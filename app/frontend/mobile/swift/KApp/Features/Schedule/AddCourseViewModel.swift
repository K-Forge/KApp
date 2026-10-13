import Foundation
import Observation

/// A course the student can put on the timetable, drawn from their own semáforo.
struct CourseCandidate: Identifiable, Equatable {
    let course: PensumCourse
    let status: CourseStatus

    var id: String { course.pensumItemCode }
    var displayName: String { DisplayText.title(course.name) }

    var statusLabel: String {
        switch status {
        case .inProgress: "En curso"
        case .failed: "Para repetir"
        default: "Disponible"
        }
    }

    /// The courses worth offering, in the order a student looks for them.
    ///
    /// What they are taking now comes first, then what they can take next, then what they have to
    /// retake. A passed course is left out, and so is an elective slot not resolved to a real course
    /// yet: an enrollment needs a course code, and an open slot has none.
    static func from(pensum: Pensum, progress: StudentProgress) -> [CourseCandidate] {
        let board = SemaphoreBoard(pensum: pensum, progress: progress, plan: nil)
        let ranked = board.cards.compactMap { card -> (Int, CourseCandidate)? in
            guard card.course.code != nil else { return nil }
            switch card.state {
            case .inProgress: return (0, CourseCandidate(course: card.course, status: .inProgress))
            case .available: return (1, CourseCandidate(course: card.course, status: .pending))
            case .failed: return (2, CourseCandidate(course: card.course, status: .failed))
            case .passed, .blocked: return nil
            }
        }
        return ranked
            .sorted { lhs, rhs in
                if lhs.0 != rhs.0 { return lhs.0 < rhs.0 }
                if lhs.1.course.level != rhs.1.course.level { return lhs.1.course.level < rhs.1.course.level }
                return lhs.1.displayName < rhs.1.displayName
            }
            .map(\.1)
    }
}

/// A course the server accepted onto the timetable.
struct AddedCourse: Equatable {
    let name: String
    let request: CreateEnrollmentRequest
    let enrollmentId: String
}

/// The "Agregar materia" sheet: which course, when and where.
@Observable
@MainActor
final class AddCourseViewModel: Identifiable {

    enum Candidates: Equatable {
        case loading
        case loaded([CourseCandidate])
        case failed(APIError)
    }

    private(set) var candidates: Candidates = .loading
    var selectedCourseId: String?
    var day: DayOfWeek
    var startMinutes = 7 * 60
    var endMinutes = 9 * 60
    var room = ""
    var group = "01"
    var professor = ""
    var colorHex = KColor.courseChoices[0].hex
    private(set) var isSaving = false
    private(set) var saveError: String?

    /// Every quarter hour from six in the morning to ten at night.
    static let timeSlots: [Int] = Array(stride(from: 6 * 60, through: 22 * 60, by: 15))

    private let services: Services
    private let today: CalendarDay

    init(services: Services, today: CalendarDay, suggestedDay: DayOfWeek) {
        self.services = services
        self.today = today
        day = suggestedDay == .sunday ? .monday : suggestedDay
    }

    func load() async {
        candidates = .loading
        do {
            let progress = try await services.semaphore.progress()
            let pensum = try await services.semaphore.pensum(code: progress.pensumCode)
            let list = CourseCandidate.from(pensum: pensum, progress: progress)
            candidates = .loaded(list)
            if selectedCourseId == nil { selectedCourseId = list.first?.id }
        } catch {
            candidates = .failed(error as? APIError ?? .unreadableResponse)
        }
    }

    var selectedCourse: CourseCandidate? {
        guard case .loaded(let list) = candidates else { return nil }
        return list.first { $0.id == selectedCourseId }
    }

    /// Why "Agregar" is off, in words, or nil when it is on.
    var validationMessage: String? {
        if selectedCourse == nil { return "Elige una materia." }
        if endMinutes <= startMinutes { return "La clase tiene que terminar después de empezar." }
        let trimmedRoom = room.trimmingCharacters(in: .whitespaces)
        if !trimmedRoom.isEmpty, trimmedRoom.range(of: "^[0-9]{3,4}$", options: .regularExpression) == nil {
            return "El salón va en números, como 302. Déjalo vacío si aún no lo sabes."
        }
        if group.trimmingCharacters(in: .whitespaces).isEmpty { return "Escribe el grupo." }
        return nil
    }

    /// The body of `POST /api/schedule/me/enrollments`, or nil while the form is not valid.
    ///
    /// Hand entry sends a single weekly slot covering the whole semester, which is what the
    /// contract's "MVP hand entry" example does. Names are sent uppercase because that is the form
    /// the report stores and the rest of the timetable is in.
    func makeRequest() -> CreateEnrollmentRequest? {
        // The timetable prints the code it is given, so the university's own goes where there is
        // one. Where there is not, the generated code is all there is to identify the course by -
        // worth revisiting once schedule.openapi.yaml has an opinion about it.
        guard validationMessage == nil,
              let candidate = selectedCourse,
              let code = candidate.course.sinuCode ?? candidate.course.code
        else {
            return nil
        }
        let period = AcademicCalendar.period(containing: today)
        guard let range = AcademicCalendar.meetingRange(on: day, inPeriod: period) else { return nil }

        let spanish = Locale(identifier: "es_CO")
        let trimmedRoom = room.trimmingCharacters(in: .whitespaces)
        let trimmedProfessor = professor.trimmingCharacters(in: .whitespaces)
        return CreateEnrollmentRequest(
            courseCode: code,
            pensumItemCode: candidate.course.pensumItemCode,
            courseName: candidate.course.name.uppercased(with: spanish),
            level: candidate.course.level,
            credits: candidate.course.credits,
            totalHours: candidate.course.totalHours ?? Int((candidate.course.weeklyHours * 16).rounded()),
            group: group.trimmingCharacters(in: .whitespaces),
            subgroup: nil,
            professor: trimmedProfessor.isEmpty ? "POR ASIGNAR" : trimmedProfessor.uppercased(with: spanish),
            campus: "Sede Principal",
            startDate: range.from.iso,
            endDate: range.to.iso,
            color: colorHex,
            meetings: [
                CreateMeetingRequest(
                    dayOfWeek: day,
                    startTime: TimeOfDay.string(minutes: startMinutes),
                    endTime: TimeOfDay.string(minutes: endMinutes),
                    periods: [MeetingPeriod(from: range.from.iso, to: range.to.iso, room: trimmedRoom.isEmpty ? nil : trimmedRoom)]
                )
            ]
        )
    }

    /// Sends the course. Returns what was added when the server took it.
    func save() async -> AddedCourse? {
        guard let request = makeRequest(), let name = selectedCourse?.displayName else { return nil }
        isSaving = true
        saveError = nil
        defer { isSaving = false }
        do {
            let enrollment = try await services.schedule.addEnrollment(request)
            return AddedCourse(name: name, request: request, enrollmentId: enrollment.enrollmentId)
        } catch let error as APIError {
            switch error {
            case .conflict:
                saveError = "Ese horario se cruza con otra materia que ya tienes."
            case .notFound:
                saveError = "Primero arma tu horario para este periodo."
            default:
                saveError = error.userMessage
            }
        } catch {
            saveError = APIError.unreadableResponse.userMessage
        }
        return nil
    }
}
