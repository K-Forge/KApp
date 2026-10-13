import Foundation

/// How a course reads on the grid. Five colours, the traffic light the screen is named for.
enum CardState: Equatable {
    case passed
    case inProgress
    case failed
    /// Pending, with every prerequisite passed: it can be taken next.
    case available
    /// Pending, with a prerequisite still missing.
    case blocked

    var label: String {
        switch self {
        case .passed: "Aprobada"
        case .inProgress: "En curso"
        case .failed: "Perdida"
        case .available: "Disponible"
        case .blocked: "Bloqueada"
        }
    }
}

/// One course on the grid: the pensum item, the student's entry for it, and what that adds up to.
struct BoardCard: Identifiable, Equatable {
    let course: PensumCourse
    let entry: StudentProgressCourse?
    let state: CardState
    /// Set only when the student's plan moved the course off the level the pensum gives it.
    let plannedLevel: Int?
    /// Names of the prerequisites not passed yet. Empty unless the card is blocked.
    let missingPrerequisites: [String]
    /// The course is no longer in the pensum; its grade is kept but does not count.
    let isRemovedFromPensum: Bool

    var id: String { course.pensumItemCode }
    var effectiveLevel: Int { plannedLevel ?? course.level }

    /// A resolved elective prints the course it became.
    var displayName: String {
        DisplayText.title(entry?.resolvedName ?? course.name)
    }
}

/// The semáforo grid, put together from three answers.
///
/// `GET /api/semaphore/me` says what state each item is in but not what it is called or where it
/// sits; `GET /api/catalog/pensums/{code}` says what and where; the primary plan from
/// `GET /api/semaphore/me/plans` says which ones the student moved. This joins them, and derives
/// blocked versus available, which the contract defines and does not store: a pending item is
/// available when every prerequisite is passed.
struct SemaphoreBoard: Equatable {
    let pensum: Pensum
    private(set) var progress: StudentProgress
    private(set) var plan: AcademicPlan?

    init(pensum: Pensum, progress: StudentProgress, plan: AcademicPlan?) {
        self.pensum = pensum
        self.progress = progress
        self.plan = plan
    }

    var levels: [Int] {
        Array(1...max(pensum.levels, pensum.courses.map(\.level).max() ?? 1))
    }

    var cards: [BoardCard] {
        let entries = Dictionary(progress.courses.map { ($0.pensumItemCode, $0) }, uniquingKeysWith: { first, _ in first })
        let statusByCode = Dictionary(
            progress.courses.compactMap { entry in entry.code.map { ($0, entry.status) } },
            uniquingKeysWith: { first, _ in first }
        )
        let names = Dictionary(
            pensum.courses.compactMap { course in course.code.map { ($0, DisplayText.title(course.name)) } },
            uniquingKeysWith: { first, _ in first }
        )
        let placements = Dictionary(
            (plan?.placements ?? []).map { ($0.code, $0.plannedLevel) },
            uniquingKeysWith: { _, last in last }
        )
        let removed = Set(progress.reconciliation?.removedCourses ?? [])

        return pensum.courses.map { course in
            let entry = entries[course.pensumItemCode]
            let state = Self.state(of: course, entry: entry, statusByCode: statusByCode)
            let missing = state == .blocked
                ? course.prerequisites.filter { statusByCode[$0] != .passed }.map { names[$0] ?? $0 }
                : []
            let planned = placements[course.identifier].flatMap { $0 == course.level ? nil : $0 }
            return BoardCard(
                course: course,
                entry: entry,
                state: state,
                plannedLevel: planned,
                missingPrerequisites: missing,
                isRemovedFromPensum: removed.contains(course.identifier)
            )
        }
    }

    /// The contract's rule, as `GET /api/semaphore/me/eligible` states it.
    static func state(
        of course: PensumCourse,
        entry: StudentProgressCourse?,
        statusByCode: [String: CourseStatus]
    ) -> CardState {
        switch entry?.status ?? .pending {
        case .passed: return .passed
        case .inProgress: return .inProgress
        case .failed: return .failed
        case .pending:
            let ready = course.prerequisites.allSatisfy { statusByCode[$0] == .passed }
            return ready ? .available : .blocked
        }
    }

    func cards(atLevel level: Int) -> [BoardCard] {
        cards.filter { $0.effectiveLevel == level }
    }

    func card(id: String) -> BoardCard? {
        cards.first { $0.id == id }
    }

    func areaName(_ code: String) -> String {
        pensum.areas.first { $0.code == code }?.name ?? code
    }

    func areaColor(_ code: String) -> String? {
        pensum.areas.first { $0.code == code }?.color
    }

    /// Items the pensum gained after this student's document was created.
    var addedCourseCount: Int {
        progress.reconciliation?.addedCourses.count ?? 0
    }

    /// Credits of the courses shown in one column.
    func credits(atLevel level: Int) -> Int {
        cards(atLevel: level).reduce(0) { $0 + $1.course.credits }
    }

    // MARK: - Changes the server accepted

    /// Writes what `PUT /api/semaphore/me/courses/{code}` was sent into the document.
    ///
    /// The contract replaces the entry with exactly the request, so a `200` means the entry now is
    /// the request - there is no need to read it back, and the grid does not wait on a reload.
    mutating func apply(_ request: CourseProgressUpdateRequest, toCard id: String) {
        guard let course = pensum.courses.first(where: { $0.pensumItemCode == id }) else { return }
        if let index = progress.courses.firstIndex(where: { $0.pensumItemCode == id }) {
            progress.courses[index].status = request.status
            progress.courses[index].grade = request.grade
            progress.courses[index].period = request.period
        } else {
            progress.courses.append(StudentProgressCourse(
                code: course.code,
                pensumItemCode: course.pensumItemCode,
                status: request.status,
                period: request.period,
                grade: request.grade,
                resolvedCode: nil,
                resolvedName: nil
            ))
        }
    }

    mutating func setPlan(_ plan: AcademicPlan) {
        self.plan = plan
    }

    /// Moves a course in the plan, or back to its pensum level when `level` is nil.
    mutating func applyPlacement(ofCard id: String, level: Int?) {
        guard var plan, let course = pensum.courses.first(where: { $0.pensumItemCode == id }) else { return }
        plan.placements.removeAll { $0.code == course.identifier }
        if let level, level != course.level {
            plan.placements.append(Placement(code: course.identifier, plannedLevel: level))
        }
        self.plan = plan
    }
}

/// The checks `PUT /api/semaphore/me/courses/{code}` makes, made before sending so the student
/// reads the reason in Spanish instead of a 400.
enum CourseUpdateForm {
    static func request(status: CourseStatus, gradeText: String, period: String) -> Result<CourseProgressUpdateRequest, FormError> {
        let trimmedPeriod = period.trimmingCharacters(in: .whitespaces)

        switch status {
        case .pending:
            // A course not taken has no mark and no period.
            return .success(CourseProgressUpdateRequest(status: .pending, grade: nil, period: nil))

        case .inProgress:
            guard isValidPeriod(trimmedPeriod) else { return .failure(.period) }
            return .success(CourseProgressUpdateRequest(status: .inProgress, grade: nil, period: trimmedPeriod.nilIfEmpty))

        case .passed, .failed:
            let text = gradeText.trimmingCharacters(in: .whitespaces)
            guard let grade = Int(text), (0...50).contains(grade) else { return .failure(.grade) }
            guard isValidPeriod(trimmedPeriod) else { return .failure(.period) }
            return .success(CourseProgressUpdateRequest(status: status, grade: grade, period: trimmedPeriod.nilIfEmpty))
        }
    }

    /// Empty is allowed - the contract clears the period - otherwise `^\d{4}[12]$`.
    static func isValidPeriod(_ period: String) -> Bool {
        period.isEmpty || period.range(of: "^\\d{4}[12]$", options: .regularExpression) != nil
    }

    enum FormError: Error, Equatable {
        case grade, period

        var message: String {
            switch self {
            case .grade: "Escribe la nota final, un número de 0 a 50."
            case .period: "El periodo va como 20262: el año y luego 1 o 2."
            }
        }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
