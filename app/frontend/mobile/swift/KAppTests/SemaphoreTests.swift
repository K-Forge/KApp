import XCTest
@testable import KApp

/// The semáforo's rules: which pending course is available and which is blocked, what a saved status
/// and a move do to the grid, and the checks made before the server makes them.
final class SemaphoreTests: XCTestCase {

    private let chain = Fixture.chain

    private func board(_ entries: [StudentProgressCourse], plan: AcademicPlan? = nil) -> SemaphoreBoard {
        SemaphoreBoard(pensum: chain.pensum, progress: Fixture.progress(entries), plan: plan)
    }

    // MARK: - Available or blocked

    func testAPendingCourseWithEveryPrerequisitePassedIsAvailable() {
        let grid = board([
            Fixture.entry(code: "46018", item: "1604", status: .passed, grade: 44),
            Fixture.entry(code: "48022", item: "1703", status: .passed, grade: 42),
            Fixture.entry(code: "59035", item: "2033", status: .pending)
        ])
        XCTAssertEqual(grid.card(id: "2033")?.state, .available)
    }

    func testAPendingCourseMissingAPrerequisiteIsBlockedAndNamesWhatIsMissing() {
        let grid = board([
            Fixture.entry(code: "46018", item: "1604", status: .passed, grade: 44),
            Fixture.entry(code: "48022", item: "1703", status: .inProgress),
            Fixture.entry(code: "59035", item: "2033", status: .pending)
        ])
        let card = grid.card(id: "2033")
        XCTAssertEqual(card?.state, .blocked)
        XCTAssertEqual(card?.missingPrerequisites, ["Ingeniería de Software II"])
    }

    func testAFailedPrerequisiteStillBlocks() {
        let grid = board([
            Fixture.entry(code: "46018", item: "1604", status: .failed, grade: 24),
            Fixture.entry(code: "48022", item: "1703", status: .pending)
        ])
        XCTAssertEqual(grid.card(id: "1703")?.state, .blocked)
    }

    func testACourseWithNoPrerequisitesIsAvailableAsSoonAsItIsPending() {
        XCTAssertEqual(board([]).card(id: "1604")?.state, .available)
    }

    func testStoredStatusesArePaintedAsTheyAre() {
        let grid = board([
            Fixture.entry(code: "46018", item: "1604", status: .passed, grade: 44),
            Fixture.entry(code: "48022", item: "1703", status: .failed, grade: 24),
            Fixture.entry(code: "59035", item: "2033", status: .inProgress)
        ])
        XCTAssertEqual(grid.cards.map(\.state), [.passed, .failed, .inProgress])
    }

    // MARK: - Changes

    func testASavedStatusRewritesTheEntryAndUnblocksWhatDependsOnIt() {
        var grid = board([
            Fixture.entry(code: "46018", item: "1604", status: .passed, grade: 44),
            Fixture.entry(code: "48022", item: "1703", status: .inProgress),
            Fixture.entry(code: "59035", item: "2033", status: .pending)
        ])
        XCTAssertEqual(grid.card(id: "2033")?.state, .blocked)

        grid.apply(CourseProgressUpdateRequest(status: .passed, grade: 40, period: "20262"), toCard: "1703")

        XCTAssertEqual(grid.card(id: "1703")?.entry?.grade, 40)
        XCTAssertEqual(grid.card(id: "2033")?.state, .available)
    }

    func testASavedStatusForACourseWithNoEntryYetCreatesOne() {
        var grid = board([])
        grid.apply(CourseProgressUpdateRequest(status: .inProgress, grade: nil, period: "20262"), toCard: "1604")
        XCTAssertEqual(grid.card(id: "1604")?.state, .inProgress)
    }

    func testAMoveTakesTheCourseToItsPlannedColumnAndMovingBackClearsIt() {
        let plan = AcademicPlan(id: "p1", name: "Mi plan", pensumCode: "1015", primary: true, placements: [])
        var grid = board([], plan: plan)

        grid.applyPlacement(ofCard: "2033", level: 9)
        XCTAssertEqual(grid.card(id: "2033")?.effectiveLevel, 9)
        XCTAssertEqual(grid.cards(atLevel: 9).map(\.id), ["2033"])
        XCTAssertTrue(grid.cards(atLevel: 8).isEmpty)

        grid.applyPlacement(ofCard: "2033", level: nil)
        XCTAssertNil(grid.card(id: "2033")?.plannedLevel)
        XCTAssertEqual(grid.plan?.placements, [])
    }

    func testAPlacementOnThePensumsOwnLevelIsNotAMove() {
        let plan = AcademicPlan(
            id: "p1", name: "Mi plan", pensumCode: "1015", primary: true,
            placements: [Placement(code: "59035", plannedLevel: 8)]
        )
        XCTAssertNil(board([], plan: plan).card(id: "2033")?.plannedLevel)
    }

    // MARK: - Checks before sending

    func testAPassedOrFailedCourseNeedsAGradeOnTheScale() {
        XCTAssertEqual(CourseUpdateForm.request(status: .passed, gradeText: "", period: "20262"), .failure(.grade))
        XCTAssertEqual(CourseUpdateForm.request(status: .failed, gradeText: "51", period: "20262"), .failure(.grade))
        XCTAssertEqual(
            CourseUpdateForm.request(status: .passed, gradeText: " 45 ", period: "20262"),
            .success(CourseProgressUpdateRequest(status: .passed, grade: 45, period: "20262"))
        )
    }

    func testThePeriodFollowsTheUniversitysFormat() {
        XCTAssertEqual(CourseUpdateForm.request(status: .inProgress, gradeText: "", period: "2026-2"), .failure(.period))
        XCTAssertEqual(CourseUpdateForm.request(status: .inProgress, gradeText: "", period: "20263"), .failure(.period))
    }

    func testPendingSendsNoGradeAndNoPeriodWhateverIsInTheFields() {
        XCTAssertEqual(
            CourseUpdateForm.request(status: .pending, gradeText: "45", period: "20262"),
            .success(CourseProgressUpdateRequest(status: .pending, grade: nil, period: nil))
        )
    }

    // MARK: - What can go on the timetable

    func testCandidatesAreInProgressFirstThenAvailableThenRetakesAndNeverPassedOrBlocked() {
        let extra = Fixture.course(code: "10011", item: "1001", name: "Precálculo", level: 1)
        let retake = Fixture.course(code: "17080", item: "1602", name: "Estadística Descriptiva", level: 6)
        let pensum = Fixture.pensum(chain.pensum.courses + [extra, retake])
        let progress = Fixture.progress([
            Fixture.entry(code: "46018", item: "1604", status: .passed, grade: 44),
            Fixture.entry(code: "48022", item: "1703", status: .inProgress),
            Fixture.entry(code: "59035", item: "2033", status: .pending),
            Fixture.entry(code: "17080", item: "1602", status: .failed, grade: 24)
        ])

        let names = CourseCandidate.from(pensum: pensum, progress: progress).map(\.course.name)

        XCTAssertEqual(names, ["Ingeniería de Software II", "Precálculo", "Estadística Descriptiva"])
    }
}
