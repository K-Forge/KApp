import Foundation
import Observation

/// A line of confirmation or warning a screen shows above its content.
struct Notice: Equatable, Identifiable {
    enum Kind: Equatable { case success, error }

    let kind: Kind
    let text: String
    let id = UUID()

    static func success(_ text: String) -> Notice { Notice(kind: .success, text: text) }
    static func error(_ text: String) -> Notice { Notice(kind: .error, text: text) }

    static func == (lhs: Notice, rhs: Notice) -> Bool {
        lhs.kind == rhs.kind && lhs.text == rhs.text
    }
}

/// Horario: one week of classes, from `GET /api/schedule/me/week`.
@Observable
@MainActor
final class ScheduleViewModel {

    enum LoadState: Equatable {
        case loading
        /// `404`: no schedule has been built for the active period.
        case noSchedule
        case loaded(WeekAgenda)
        case failed(APIError)
    }

    private(set) var state: LoadState = .loading
    /// Any day of the week being asked for. The server snaps it to that week's Monday.
    private(set) var anchor: CalendarDay
    var selectedDay: DayOfWeek
    var notice: Notice?
    private(set) var isCreatingSchedule = false
    private(set) var removingEnrollmentId: String?

    private let services: Services
    private let now: () -> Date

    init(services: Services, now: @escaping () -> Date = Date.init) {
        self.services = services
        self.now = now
        let today = CalendarDay(now())
        anchor = today
        selectedDay = today.dayOfWeek
    }

    var today: CalendarDay { CalendarDay(now()) }

    func load() async {
        do {
            let week = try await services.schedule.week(containing: anchor)
            state = .loaded(week)
        } catch APIError.notFound {
            state = .noSchedule
        } catch {
            if case .loaded = state { return }
            state = .failed(error as? APIError ?? .unreadableResponse)
        }
    }

    func showPreviousWeek() async { await move(byWeeks: -1) }
    func showNextWeek() async { await move(byWeeks: 1) }

    func showThisWeek() async {
        anchor = today
        selectedDay = today.dayOfWeek
        state = .loading
        await load()
    }

    private func move(byWeeks weeks: Int) async {
        anchor = anchor.adding(days: 7 * weeks)
        state = .loading
        await load()
    }

    // MARK: - Reading the week

    var week: WeekAgenda? {
        if case .loaded(let week) = state { return week }
        return nil
    }

    /// Whether the week on screen is not the current one, which is when "Hoy" is worth offering.
    var isAwayFromThisWeek: Bool {
        anchor.monday != today.monday
    }

    /// "21 – 27 sep", or "28 sep – 4 oct". Read off the answer rather than the request: the server
    /// decides which week it returned.
    var weekTitle: String {
        guard let week, let start = CalendarDay(iso: week.weekStart), let end = CalendarDay(iso: week.weekEnd) else {
            return "Semana del \(anchor.monday.shortLabel)"
        }
        return start.month == end.month
            ? "\(start.day) – \(end.shortLabel)"
            : "\(start.shortLabel) – \(end.shortLabel)"
    }

    func date(of day: DayOfWeek) -> CalendarDay? {
        guard let week, let monday = CalendarDay(iso: week.weekStart) else { return nil }
        return monday.adding(days: day.mondayIndex)
    }

    func isToday(_ day: DayOfWeek) -> Bool {
        date(of: day) == today
    }

    func classes(on day: DayOfWeek) -> [ClassOccurrence] {
        week?.classes(on: day) ?? []
    }

    // MARK: - Changing it

    /// `POST /api/schedule/me` for the current period, with the programme the profile carries.
    func createSchedule() async {
        isCreatingSchedule = true
        defer { isCreatingSchedule = false }
        do {
            let profile = try await services.profile.myProfile()
            guard let academic = profile.academic else {
                notice = .error("Tu cuenta no tiene un programa académico asociado.")
                return
            }
            let period = AcademicCalendar.period(containing: today)
            _ = try await services.schedule.createSchedule(CreateScheduleRequest(
                period: period,
                programCode: academic.programCode,
                pensumCode: academic.pensumCode,
                level: academic.currentLevel
            ))
            notice = .success("Listo: ya tienes horario para el periodo \(Self.periodLabel(period)). Agrega tus materias.")
            state = .loading
            await load()
        } catch let error as APIError {
            notice = .error(isConflict(error) ? "Ya tienes un horario para este periodo." : error.userMessage)
        } catch {
            notice = .error(APIError.unreadableResponse.userMessage)
        }
    }

    /// `DELETE /api/schedule/me/enrollments/{id}`. The course leaves every day it was on.
    ///
    /// A `204` means the enrollment is gone, so the week is updated on the spot rather than reloaded:
    /// the answer already says what the server now holds. The next refresh reads it back. The same
    /// goes for adding a course, and for the semáforo.
    func remove(_ occurrence: ClassOccurrence) async -> Bool {
        removingEnrollmentId = occurrence.enrollmentId
        defer { removingEnrollmentId = nil }
        do {
            try await services.schedule.removeEnrollment(id: occurrence.enrollmentId)
            notice = .success("Quitaste \(DisplayText.title(occurrence.courseName)) de tu horario.")
            if let week {
                state = .loaded(week.removingEnrollment(occurrence.enrollmentId))
            }
            return true
        } catch let error as APIError {
            notice = .error(error.userMessage)
            return false
        } catch {
            return false
        }
    }

    func makeAddCourseModel() -> AddCourseViewModel {
        AddCourseViewModel(services: services, today: today, suggestedDay: selectedDay)
    }

    func courseAdded(_ added: AddedCourse) {
        notice = .success("Agregaste \(added.name) a tu horario.")
        if let week {
            state = .loaded(week.adding(added.request, enrollmentId: added.enrollmentId))
        }
        // Land on the day the class is on, so the student sees what they just added.
        selectedDay = added.request.meetings.first?.dayOfWeek ?? selectedDay
    }

    /// "20262" to "2026-2".
    static func periodLabel(_ period: String) -> String {
        guard period.count == 5 else { return period }
        return "\(period.prefix(4))-\(period.suffix(1))"
    }

    private func isConflict(_ error: APIError) -> Bool {
        if case .conflict = error { return true }
        return false
    }
}
