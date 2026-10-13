import Foundation

// Shapes from docs/api/semaphore.openapi.yaml.

/// `CourseStatus`. Blocked is not a status: it is derived from prerequisites.
enum CourseStatus: String, Codable, CaseIterable {
    case passed = "PASSED"
    case inProgress = "IN_PROGRESS"
    case pending = "PENDING"
    case failed = "FAILED"
}

/// `ProgressSummary`.
struct ProgressSummary: Decodable, Equatable {
    let creditsPassed: Int
    let creditsInProgress: Int
    let creditsRemaining: Int
    let totalCredits: Int
    let percentComplete: Double
    let currentLevel: Int
}

/// `StudentProgressCourse`: the state of one pensum item for this student.
struct StudentProgressCourse: Decodable, Equatable {
    /// Null for an elective slot, even a resolved one.
    let code: String?
    let pensumItemCode: String
    var status: CourseStatus
    var period: String?
    var grade: Int?
    let resolvedCode: String?
    let resolvedName: String?
}

struct Reconciliation: Decodable, Equatable {
    let addedCourses: [String]
    let removedCourses: [String]
    let inSync: Bool
}

/// `StudentProgressWithReconciliation`, the answer to `GET /api/semaphore/me`.
struct StudentProgress: Decodable, Equatable {
    let userId: String
    let studentCode: String
    let programCode: String
    let pensumCode: String
    let currentLevel: Int
    var courses: [StudentProgressCourse]
    let reconciliation: Reconciliation?
}

/// `PensumArea`: a row of the grid, with the colour the pensum gives it.
struct PensumArea: Decodable, Equatable {
    let code: String
    let name: String
    let color: String?
    let credits: Int?
}

/// `PensumCourse`: one item of the plan. What the grid draws a card from.
struct PensumCourse: Decodable, Equatable, Identifiable {
    /// What the item is addressed by. Not for display: only nineteen of the twenty-three published
    /// plans print course codes, and KApp generates one for the rest.
    let code: String?
    /// The university's own code, or null where there is none. **The only code a client shows.**
    let sinuCode: String?
    let pensumItemCode: String
    let name: String
    let level: Int
    let credits: Int
    /// A number, not an integer: four items across the published plans are taught for half an hour
    /// more. Reading it as an `Int` fails to decode on exactly those four.
    let weeklyHours: Double
    /// Whole hours everywhere: half an hour a week is eight whole hours a semester.
    let totalHours: Int?
    let area: String
    let isElectiveSlot: Bool
    let prerequisites: [String]

    var id: String { pensumItemCode }

    /// What `PUT /api/semaphore/me/courses/{code}` and a placement are addressed by: the course
    /// code, or the item code for an elective slot, which has no course code.
    var identifier: String { code ?? pensumItemCode }
}

/// `Pensum`.
struct Pensum: Decodable, Equatable {
    let pensumCode: String
    let programCode: String
    let programName: String
    let levels: Int
    let totalCredits: Int
    let areas: [PensumArea]
    let courses: [PensumCourse]
}

/// `CourseProgressUpdateRequest`. The entry is replaced whole, so absent values are sent as null
/// rather than left out - it says what is meant.
struct CourseProgressUpdateRequest: Encodable, Equatable {
    let status: CourseStatus
    let grade: Int?
    let period: String?

    private enum CodingKeys: String, CodingKey { case status, grade, period }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(status, forKey: .status)
        try container.encode(grade, forKey: .grade)
        try container.encode(period, forKey: .period)
    }
}

/// `AcademicPlan`: only the courses a student moved.
struct AcademicPlan: Decodable, Equatable {
    let id: String
    let name: String
    let pensumCode: String
    let primary: Bool
    var placements: [Placement]
}

struct Placement: Decodable, Equatable {
    let code: String
    let plannedLevel: Int
}

struct AcademicPlanRequest: Encodable {
    let name: String
    let pensumCode: String
}

struct PlacementRequest: Encodable {
    let plannedLevel: Int
}
