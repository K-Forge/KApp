import Foundation
import Observation

/// Semáforo: the student's pensum, course by course, and the two things they can change about it -
/// the status of a course and the semester they plan to take it in.
@Observable
@MainActor
final class SemaphoreViewModel {

    enum LoadState: Equatable {
        case loading
        case loaded
        case failed(APIError)
    }

    private(set) var state: LoadState = .loading
    private(set) var board: SemaphoreBoard?
    private(set) var summary: ProgressSummary?
    var notice: Notice?

    private let services: Services
    private let now: () -> Date

    init(services: Services, now: @escaping () -> Date = Date.init) {
        self.services = services
        self.now = now
    }

    /// The period a course taken now belongs to: "20262".
    var currentPeriod: String {
        AcademicCalendar.period(containing: CalendarDay(now()))
    }

    func load() async {
        do {
            let progress = try await services.semaphore.progress()
            let pensum = try await services.semaphore.pensum(code: progress.pensumCode)
            // The plan and the summary add to the grid; the grid still draws without them.
            let plan = (try? await services.semaphore.plans())?.first { $0.primary && $0.pensumCode == pensum.pensumCode }
            summary = try? await services.semaphore.summary()
            board = SemaphoreBoard(pensum: pensum, progress: progress, plan: plan)
            state = .loaded
        } catch {
            if board != nil { return }
            state = .failed(error as? APIError ?? .unreadableResponse)
        }
    }

    /// Saves a new status. Returns the reason in words when it did not save.
    func save(cardId: String, status: CourseStatus, gradeText: String, period: String) async -> String? {
        guard let card = board?.card(id: cardId) else { return nil }
        let request: CourseProgressUpdateRequest
        switch CourseUpdateForm.request(status: status, gradeText: gradeText, period: period) {
        case .success(let valid): request = valid
        case .failure(let problem): return problem.message
        }

        do {
            try await services.semaphore.updateCourse(card.course.identifier, request)
            board?.apply(request, toCard: cardId)
            notice = .success("\(card.displayName) quedó como \(Self.statusWord(status)).")
            summary = (try? await services.semaphore.summary()) ?? summary
            return nil
        } catch let error as APIError {
            return error.userMessage
        } catch {
            return APIError.unreadableResponse.userMessage
        }
    }

    /// Plans a course for another semester, creating the student's plan the first time. Moving it
    /// back to the semester the pensum gives it removes the placement instead of storing one that
    /// says nothing.
    func move(cardId: String, toLevel level: Int) async -> String? {
        guard let card = board?.card(id: cardId), let pensumCode = board?.pensum.pensumCode else { return nil }
        do {
            let plan: AcademicPlan
            if let existing = board?.plan {
                plan = existing
            } else {
                plan = try await services.semaphore.createPlan(AcademicPlanRequest(name: "Mi plan", pensumCode: pensumCode))
                board?.setPlan(plan)
            }

            if level == card.course.level {
                try await services.semaphore.resetPlacement(card.course.identifier, inPlan: plan.id)
                board?.applyPlacement(ofCard: cardId, level: nil)
                notice = .success("\(card.displayName) volvió al semestre \(level).")
            } else {
                try await services.semaphore.place(card.course.identifier, atLevel: level, inPlan: plan.id)
                board?.applyPlacement(ofCard: cardId, level: level)
                notice = .success("Planeaste \(card.displayName) para el semestre \(level).")
            }
            return nil
        } catch let error as APIError {
            return error.userMessage
        } catch {
            return APIError.unreadableResponse.userMessage
        }
    }

    static func statusWord(_ status: CourseStatus) -> String {
        switch status {
        case .passed: "aprobada"
        case .inProgress: "en curso"
        case .failed: "perdida"
        case .pending: "pendiente"
        }
    }
}
