import Foundation
@testable import KApp

/// Small builders for the shapes the contracts return, so each test states only what it is about.
enum Fixture {

    static func occurrence(
        _ name: String = "BASES DE DATOS",
        start: String,
        end: String,
        enrollmentId: String = UUID().uuidString,
        room: String? = "401",
        color: String = "#539392"
    ) -> ClassOccurrence {
        ClassOccurrence(
            enrollmentId: enrollmentId,
            courseCode: "17080",
            courseName: name,
            professor: "CAMPOS AVENDANO GUSTAVO ANDRES",
            startTime: start,
            endTime: end,
            room: room,
            campus: "Sede Principal",
            color: color
        )
    }

    static func course(
        code: String?,
        item: String,
        name: String,
        level: Int,
        credits: Int = 3,
        weeklyHours: Double = 4,
        sinuCode: String? = nil,
        prerequisites: [String] = []
    ) -> PensumCourse {
        PensumCourse(
            code: code,
            sinuCode: sinuCode ?? code,
            pensumItemCode: item,
            name: name,
            level: level,
            credits: credits,
            weeklyHours: weeklyHours,
            totalHours: 64,
            area: "ISA",
            isElectiveSlot: code == nil,
            prerequisites: prerequisites
        )
    }

    static func entry(code: String?, item: String, status: CourseStatus, grade: Int? = nil) -> StudentProgressCourse {
        StudentProgressCourse(
            code: code,
            pensumItemCode: item,
            status: status,
            period: status == .pending ? nil : "20261",
            grade: grade,
            resolvedCode: nil,
            resolvedName: nil
        )
    }

    static func pensum(_ courses: [PensumCourse], levels: Int = 9) -> Pensum {
        Pensum(
            pensumCode: "1015",
            programCode: "506",
            programName: "Ingeniería de Sistemas",
            levels: levels,
            totalCredits: 142,
            areas: [PensumArea(code: "ISA", name: "Ingeniería de Sistemas Aplicada", color: "#C9D329", credits: 66)],
            courses: courses
        )
    }

    static func progress(_ entries: [StudentProgressCourse], added: [String] = []) -> StudentProgress {
        StudentProgress(
            userId: "507f1f77bcf86cd799439011",
            studentCode: "506999999",
            programCode: "506",
            pensumCode: "1015",
            currentLevel: 8,
            courses: entries,
            reconciliation: Reconciliation(addedCourses: added, removedCourses: [], inSync: added.isEmpty)
        )
    }

    /// The prerequisite chain from the contract's example: Software I, then II, then Móviles.
    static let chain: (pensum: Pensum, softwareI: PensumCourse, softwareII: PensumCourse, mobile: PensumCourse) = {
        let one = course(code: "46018", item: "1604", name: "Ingeniería de Software I", level: 6)
        let two = course(code: "48022", item: "1703", name: "Ingeniería de Software II", level: 7, prerequisites: ["46018"])
        let mobile = course(code: "59035", item: "2033", name: "Desarrollo de Aplicaciones Móviles", level: 8, prerequisites: ["48022"])
        return (pensum([one, two, mobile]), one, two, mobile)
    }()
}

/// An auth service that answers whatever the test tells it to.
@MainActor
final class FakeAuthService: AuthService {
    var loginResult: Result<TokenResponse, APIError> = .failure(.offline)
    var resendResult: Result<Void, APIError> = .success(())
    private(set) var loginCalls: [(email: String, password: String)] = []

    func login(email: String, password: String) async throws -> TokenResponse {
        loginCalls.append((email, password))
        return try loginResult.get()
    }

    func resendVerification(email: String) async throws {
        try resendResult.get()
    }

    static let token = TokenResponse(
        accessToken: "token",
        tokenType: "Bearer",
        expiresIn: 3600,
        userId: "3f8a1c2e-7b4d-4e5a-9c6f-2d1b8e0a4c73",
        roles: ["ROLE_STUDENT"]
    )
}
