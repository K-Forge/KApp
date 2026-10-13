import Foundation
import Observation

/// Who is being greeted: the first name for the band, two initials for the avatar.
struct Student: Equatable {
    let firstName: String
    let initials: String

    /// "Pepe Pérez Gómez" is "Pepe" and "PP": two initials at most, because the avatar is a 34 pt
    /// circle. A single word gives a single letter. Same rule as `student()` on Android.
    init(fullName: String) {
        let words = fullName.split(whereSeparator: \.isWhitespace).map(String.init)
        firstName = words.first ?? ""
        initials = words.prefix(2).compactMap { $0.first.map { String($0).uppercased() } }.joined()
    }

    /// A stand-in until `GET /api/users/me` answers. The token carries the address and not the
    /// name, and "pepito.perez" is close enough to greet with for the second it takes.
    init(email: String) {
        let localPart = localPartOfEmail(email)
        let words = localPart.split(separator: ".").map { $0.prefix(1).uppercased() + $0.dropFirst() }
        self.init(fullName: words.joined(separator: " "))
    }
}

/// One class as a row prints it.
struct ClassSummary: Equatable {
    let courseName: String
    let startTime: String
    let endTime: String
    let place: String
    let colorHex: String
    /// Null once the class has begun. The mockup draws no badge for that, so none is invented.
    let startsInMinutes: Int?

    init(_ occurrence: ClassOccurrence, startsInMinutes: Int?) {
        courseName = DisplayText.title(occurrence.courseName)
        startTime = DisplayText.time(occurrence.startTime)
        endTime = DisplayText.time(occurrence.endTime)
        place = DisplayText.place(room: occurrence.room, campus: occurrence.campus)
        colorHex = occurrence.color
        self.startsInMinutes = startsInMinutes
    }

    /// "Empieza en 25 min", "Empieza en 2 h 10 min".
    static func startsInLabel(_ minutes: Int) -> String {
        guard minutes >= 60 else { return "Empieza en \(minutes) min" }
        let hours = minutes / 60, rest = minutes % 60
        return rest == 0 ? "Empieza en \(hours) h" : "Empieza en \(hours) h \(rest) min"
    }
}

/// The student's classes today, as `GET /api/schedule/me/day` answers.
enum DayState: Equatable {
    case loading
    /// `404`: this student has never built a schedule. A task, not an error.
    case noSchedule
    /// `[]`: there is a schedule, and nothing on it today.
    case noClassesToday
    /// There were classes today and every one of them has ended.
    case doneForToday
    /// `next` is the first class that has not ended; `later` is the rest, in the server's order.
    case classes(next: ClassSummary, later: [ClassSummary])
    case failed(APIError)

    /// Picks the class for the card out of the day's list, given the time now.
    ///
    /// A class still in progress is the next class: the student is in it, or late for it.
    static func from(_ classes: [ClassOccurrence], nowMinutes: Int) -> DayState {
        guard !classes.isEmpty else { return .noClassesToday }
        let upcoming = classes.filter { (TimeOfDay.minutes($0.endTime) ?? 0) > nowMinutes }
        guard let first = upcoming.first else { return .doneForToday }
        let start = TimeOfDay.minutes(first.startTime) ?? 0
        return .classes(
            next: ClassSummary(first, startsInMinutes: start > nowMinutes ? start - nowMinutes : nil),
            later: upcoming.dropFirst().map { ClassSummary($0, startsInMinutes: nil) }
        )
    }
}

/// Progress through the pensum, as `GET /api/semaphore/me/summary` answers.
struct SemesterProgress: Equatable {
    let level: Int
    /// Courses, not credits - the one count the summary does not carry. It is counted off
    /// `GET /api/semaphore/me`, and left out of the card when that call fails.
    let coursesInProgress: Int?
    let creditsPassed: Int
    let creditsInProgress: Int
    let creditsRemaining: Int
    let totalCredits: Int
    let percentComplete: Double

    init(summary: ProgressSummary, coursesInProgress: Int?) {
        level = summary.currentLevel
        self.coursesInProgress = coursesInProgress
        creditsPassed = summary.creditsPassed
        creditsInProgress = summary.creditsInProgress
        creditsRemaining = summary.creditsRemaining
        totalCredits = summary.totalCredits
        percentComplete = summary.percentComplete
    }

    /// Share of the bar painted green. Zero rather than a crash if the pensum has no credits.
    var passedFraction: Double {
        totalCredits == 0 ? 0 : Double(creditsPassed) / Double(totalCredits)
    }

    /// Share painted lime, straight after the green.
    var inProgressFraction: Double {
        totalCredits == 0 ? 0 : Double(creditsInProgress) / Double(totalCredits)
    }
}

enum SemesterState: Equatable {
    case loading
    case ready(SemesterProgress)
    case failed(APIError)
}

/// What Inicio has on it.
///
/// Three calls answer it - profile, day and semester - and each writes its own part of the state
/// as soon as it returns, so the screen never waits on the slowest. A reload keeps what is showing
/// until the new answer arrives, and a reload that fails keeps it too rather than trading content
/// for an error card.
@Observable
@MainActor
final class HomeViewModel {

    private(set) var student: Student
    private(set) var day: DayState = .loading
    private(set) var semester: SemesterState = .loading

    private let services: Services
    private let now: () -> Date

    init(services: Services, email: String, now: @escaping () -> Date = Date.init) {
        self.services = services
        self.now = now
        student = Student(email: email)
    }

    func load() async {
        async let profile: Void = loadProfile()
        async let day: Void = loadDay()
        async let semester: Void = loadSemester()
        _ = await (profile, day, semester)
    }

    func loadProfile() async {
        guard let profile = try? await services.profile.myProfile() else { return }
        student = Student(fullName: profile.fullName)
    }

    func loadDay() async {
        let date = now()
        do {
            let classes = try await services.schedule.day(CalendarDay(date))
            day = DayState.from(classes, nowMinutes: TimeOfDay.minutes(of: date))
        } catch APIError.notFound {
            day = .noSchedule
        } catch {
            if day == .loading || isFailure(day) {
                day = .failed(error as? APIError ?? .unreadableResponse)
            }
        }
    }

    func loadSemester() async {
        do {
            let summary = try await services.semaphore.summary()
            let progress = try? await services.semaphore.progress()
            let inProgress = progress?.courses.filter { $0.status == .inProgress }.count
            semester = .ready(SemesterProgress(summary: summary, coursesInProgress: inProgress))
        } catch {
            if semester == .loading || isFailure(semester) {
                semester = .failed(error as? APIError ?? .unreadableResponse)
            }
        }
    }

    private func isFailure(_ state: DayState) -> Bool {
        if case .failed = state { return true }
        return false
    }

    private func isFailure(_ state: SemesterState) -> Bool {
        if case .failed = state { return true }
        return false
    }
}
