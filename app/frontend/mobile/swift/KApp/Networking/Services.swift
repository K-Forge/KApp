import Foundation

// What each screen needs from the backend, one protocol per service. The screens depend on these
// and not on `APIClient`, so previews and tests hand them canned answers instead of a network.

@MainActor
protocol AuthService {
    func login(email: String, password: String) async throws -> TokenResponse
    func resendVerification(email: String) async throws
}

@MainActor
protocol ProfileService {
    func myProfile() async throws -> UserProfile
}

@MainActor
protocol ScheduleService {
    /// `404` when the student has never built a schedule; `[]` when there is nothing that day.
    func day(_ day: CalendarDay) async throws -> [ClassOccurrence]
    func week(containing day: CalendarDay) async throws -> WeekAgenda
    func createSchedule(_ request: CreateScheduleRequest) async throws -> Schedule
    func addEnrollment(_ request: CreateEnrollmentRequest) async throws -> Enrollment
    func removeEnrollment(id: String) async throws
}

@MainActor
protocol SemaphoreService {
    func progress() async throws -> StudentProgress
    func summary() async throws -> ProgressSummary
    func pensum(code: String) async throws -> Pensum
    func updateCourse(_ identifier: String, _ request: CourseProgressUpdateRequest) async throws
    func plans() async throws -> [AcademicPlan]
    func createPlan(_ request: AcademicPlanRequest) async throws -> AcademicPlan
    func place(_ identifier: String, atLevel level: Int, inPlan planId: String) async throws
    func resetPlacement(_ identifier: String, inPlan planId: String) async throws
}

/// The four services, as the app hands them to its screens.
@MainActor
struct Services {
    let auth: AuthService
    let profile: ProfileService
    let schedule: ScheduleService
    let semaphore: SemaphoreService
}

// MARK: - Over the network

@MainActor
struct RemoteAuthService: AuthService {
    let client: APIClient

    func login(email: String, password: String) async throws -> TokenResponse {
        try await client.request(
            .post, .auth, "/auth/login",
            body: LoginRequest(email: email, password: password),
            authenticated: false
        )
    }

    func resendVerification(email: String) async throws {
        try await client.requestWithoutContent(
            .post, .auth, "/auth/verify/resend",
            body: ResendVerificationRequest(email: email),
            authenticated: false
        )
    }
}

@MainActor
struct RemoteProfileService: ProfileService {
    let client: APIClient

    func myProfile() async throws -> UserProfile {
        try await client.request(.get, .user, "/api/users/me")
    }
}

@MainActor
struct RemoteScheduleService: ScheduleService {
    let client: APIClient

    func day(_ day: CalendarDay) async throws -> [ClassOccurrence] {
        try await client.request(
            .get, .schedule, "/api/schedule/me/day",
            query: [URLQueryItem(name: "date", value: day.iso)]
        )
    }

    func week(containing day: CalendarDay) async throws -> WeekAgenda {
        try await client.request(
            .get, .schedule, "/api/schedule/me/week",
            query: [URLQueryItem(name: "date", value: day.iso)]
        )
    }

    func createSchedule(_ request: CreateScheduleRequest) async throws -> Schedule {
        try await client.request(.post, .schedule, "/api/schedule/me", body: request)
    }

    func addEnrollment(_ request: CreateEnrollmentRequest) async throws -> Enrollment {
        try await client.request(.post, .schedule, "/api/schedule/me/enrollments", body: request)
    }

    func removeEnrollment(id: String) async throws {
        try await client.requestWithoutContent(.delete, .schedule, "/api/schedule/me/enrollments/\(id)")
    }
}

@MainActor
struct RemoteSemaphoreService: SemaphoreService {
    let client: APIClient

    func progress() async throws -> StudentProgress {
        try await client.request(.get, .semaphore, "/api/semaphore/me")
    }

    func summary() async throws -> ProgressSummary {
        try await client.request(.get, .semaphore, "/api/semaphore/me/summary")
    }

    func pensum(code: String) async throws -> Pensum {
        try await client.request(.get, .semaphore, "/api/catalog/pensums/\(code)")
    }

    func updateCourse(_ identifier: String, _ request: CourseProgressUpdateRequest) async throws {
        // The answer is the updated entry. The screen does not read it: the request already says
        // what the entry now is, and the contract replaces the entry with exactly that.
        let _: StudentProgressCourse = try await client.request(
            .put, .semaphore, "/api/semaphore/me/courses/\(identifier)", body: request
        )
    }

    func plans() async throws -> [AcademicPlan] {
        try await client.request(.get, .semaphore, "/api/semaphore/me/plans")
    }

    func createPlan(_ request: AcademicPlanRequest) async throws -> AcademicPlan {
        try await client.request(.post, .semaphore, "/api/semaphore/me/plans", body: request)
    }

    func place(_ identifier: String, atLevel level: Int, inPlan planId: String) async throws {
        let _: AcademicPlan = try await client.request(
            .put, .semaphore, "/api/semaphore/me/plans/\(planId)/placements/\(identifier)",
            body: PlacementRequest(plannedLevel: level)
        )
    }

    func resetPlacement(_ identifier: String, inPlan planId: String) async throws {
        try await client.requestWithoutContent(
            .delete, .semaphore, "/api/semaphore/me/plans/\(planId)/placements/\(identifier)"
        )
    }
}
