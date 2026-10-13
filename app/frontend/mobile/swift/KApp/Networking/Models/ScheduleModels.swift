import Foundation

// Shapes from docs/api/schedule.openapi.yaml. Dates travel as "yyyy-MM-dd" strings and times as
// "HH:mm" strings, exactly as the contract writes them; `CalendarDay` converts where arithmetic
// is needed, so no timezone is ever applied to a value that has none.

enum DayOfWeek: String, Codable, CaseIterable, Identifiable {
    case monday = "MONDAY"
    case tuesday = "TUESDAY"
    case wednesday = "WEDNESDAY"
    case thursday = "THURSDAY"
    case friday = "FRIDAY"
    case saturday = "SATURDAY"
    case sunday = "SUNDAY"

    var id: String { rawValue }

    /// The single letter on the day selector.
    var initial: String {
        switch self {
        case .monday: "L"
        case .tuesday: "M"
        case .wednesday: "M"
        case .thursday: "J"
        case .friday: "V"
        case .saturday: "S"
        case .sunday: "D"
        }
    }

    var name: String {
        switch self {
        case .monday: "Lunes"
        case .tuesday: "Martes"
        case .wednesday: "Miércoles"
        case .thursday: "Jueves"
        case .friday: "Viernes"
        case .saturday: "Sábado"
        case .sunday: "Domingo"
        }
    }

    /// Position in a Monday-first week, 0 to 6. The contract's weeks run Monday to Sunday.
    var mondayIndex: Int { DayOfWeek.allCases.firstIndex(of: self)! }
}

/// One class taking place on one concrete date, already resolved by the server.
struct ClassOccurrence: Decodable, Identifiable, Equatable {
    let enrollmentId: String
    let courseCode: String
    let courseName: String
    let professor: String
    let startTime: String
    let endTime: String
    /// Null is a real, common state: no classroom assigned for that stretch of the semester.
    let room: String?
    let campus: String
    /// Painted as it arrives. The client does not choose it.
    let color: String

    var id: String { "\(enrollmentId)-\(startTime)" }
}

/// `WeekAgenda`: Monday to Sunday. All seven keys are always present.
struct WeekAgenda: Decodable, Equatable {
    let weekStart: String
    let weekEnd: String
    let days: [String: [ClassOccurrence]]

    func classes(on day: DayOfWeek) -> [ClassOccurrence] {
        days[day.rawValue] ?? []
    }

    /// The week after `DELETE /api/schedule/me/enrollments/{id}` answered `204`: the course is off
    /// every day it was on.
    func removingEnrollment(_ enrollmentId: String) -> WeekAgenda {
        WeekAgenda(
            weekStart: weekStart,
            weekEnd: weekEnd,
            days: days.mapValues { $0.filter { $0.enrollmentId != enrollmentId } }
        )
    }

    /// The week after `POST /api/schedule/me/enrollments` answered `201`.
    ///
    /// Resolves each weekly slot the way the contract says the server does: the class is on its
    /// weekday's date in this week when one of its ranges contains that date, with that range's room.
    func adding(_ request: CreateEnrollmentRequest, enrollmentId: String) -> WeekAgenda {
        guard let monday = CalendarDay(iso: weekStart) else { return self }
        var updated = days
        for meeting in request.meetings {
            let date = monday.adding(days: meeting.dayOfWeek.mondayIndex)
            let range = meeting.periods.first { period in
                guard let from = CalendarDay(iso: period.from), let to = CalendarDay(iso: period.to) else { return false }
                return from <= date && date <= to
            }
            guard let range else { continue }
            let occurrence = ClassOccurrence(
                enrollmentId: enrollmentId,
                courseCode: request.courseCode,
                courseName: request.courseName,
                professor: request.professor,
                startTime: meeting.startTime,
                endTime: meeting.endTime,
                room: range.room,
                campus: request.campus,
                color: request.color
            )
            let key = meeting.dayOfWeek.rawValue
            updated[key] = ((updated[key] ?? []) + [occurrence]).sorted { $0.startTime < $1.startTime }
        }
        return WeekAgenda(weekStart: weekStart, weekEnd: weekEnd, days: updated)
    }
}

struct CreateScheduleRequest: Encodable {
    let period: String
    let programCode: String
    let pensumCode: String
    let level: Int
}

/// The body of `POST /api/schedule/me`'s answer, the parts the app reads.
struct Schedule: Decodable, Equatable {
    let id: String
    let period: String
    let level: Int
}

/// `MeetingPeriod`. `room` must be sent even when it is null, so this encodes by hand: the default
/// encoder leaves nil keys out, and the contract rejects a range without one.
struct MeetingPeriod: Codable, Equatable {
    let from: String
    let to: String
    let room: String?

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(from, forKey: .from)
        try container.encode(to, forKey: .to)
        try container.encode(room, forKey: .room)
    }
}

struct CreateMeetingRequest: Encodable, Equatable {
    let dayOfWeek: DayOfWeek
    let startTime: String
    let endTime: String
    let periods: [MeetingPeriod]
}

/// `CreateEnrollmentRequest`. `subgroup` is sent as an explicit null for the same reason `room` is.
struct CreateEnrollmentRequest: Encodable, Equatable {
    let courseCode: String
    let pensumItemCode: String
    let courseName: String
    let level: Int
    let credits: Int
    let totalHours: Int
    let group: String
    let subgroup: String?
    let professor: String
    let campus: String
    let startDate: String
    let endDate: String
    let color: String
    let meetings: [CreateMeetingRequest]

    private enum CodingKeys: String, CodingKey {
        case courseCode, pensumItemCode, courseName, level, credits, totalHours, group, subgroup
        case professor, campus, startDate, endDate, color, meetings
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(courseCode, forKey: .courseCode)
        try container.encode(pensumItemCode, forKey: .pensumItemCode)
        try container.encode(courseName, forKey: .courseName)
        try container.encode(level, forKey: .level)
        try container.encode(credits, forKey: .credits)
        try container.encode(totalHours, forKey: .totalHours)
        try container.encode(group, forKey: .group)
        try container.encode(subgroup, forKey: .subgroup)
        try container.encode(professor, forKey: .professor)
        try container.encode(campus, forKey: .campus)
        try container.encode(startDate, forKey: .startDate)
        try container.encode(endDate, forKey: .endDate)
        try container.encode(color, forKey: .color)
        try container.encode(meetings, forKey: .meetings)
    }
}

/// The answer to `POST /api/schedule/me/enrollments`, the parts the app reads.
struct Enrollment: Decodable, Equatable {
    let enrollmentId: String
    let courseName: String
}
