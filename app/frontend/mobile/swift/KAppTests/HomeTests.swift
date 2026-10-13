import XCTest
@testable import KApp

/// The parts of Inicio that are arithmetic rather than layout: the greeting, the progress bar, and
/// which class goes on the card. The first two are the cases `HomeUiStateTest.kt` pins on Android.
final class HomeTests: XCTestCase {

    // MARK: - The greeting

    func testGreetsByFirstNameAndAbbreviatesToTwoInitials() {
        let student = Student(fullName: "Pepe Pérez")
        XCTAssertEqual(student.firstName, "Pepe")
        XCTAssertEqual(student.initials, "PP")
    }

    func testAThirdSurnameDoesNotAddAThirdInitial() {
        XCTAssertEqual(Student(fullName: "Pepe Pérez Gómez").initials, "PP")
    }

    func testOneWordGivesOneInitialRatherThanADoubledOne() {
        let student = Student(fullName: "Pepe")
        XCTAssertEqual(student.firstName, "Pepe")
        XCTAssertEqual(student.initials, "P")
    }

    func testALowercaseNameStillGivesUppercaseInitials() {
        XCTAssertEqual(Student(fullName: "pepe pérez").initials, "PP")
    }

    func testExtraWhitespaceDoesNotBecomeAnInitial() {
        XCTAssertEqual(Student(fullName: "  Pepe   Pérez  ").initials, "PP")
    }

    func testAnEmptyNameProducesAnEmptyGreetingInsteadOfFailing() {
        let student = Student(fullName: "   ")
        XCTAssertEqual(student.firstName, "")
        XCTAssertEqual(student.initials, "")
    }

    func testTheAddressStandsInForTheNameUntilTheProfileArrives() {
        let student = Student(email: "pepito.perez@konradlorenz.edu.co")
        XCTAssertEqual(student.firstName, "Pepito")
        XCTAssertEqual(student.initials, "PP")
    }

    // MARK: - The bar

    private func progress(passed: Int, inProgress: Int, total: Int) -> SemesterProgress {
        SemesterProgress(
            summary: ProgressSummary(
                creditsPassed: passed,
                creditsInProgress: inProgress,
                creditsRemaining: total - passed - inProgress,
                totalCredits: total,
                percentComplete: 78.9,
                currentLevel: 8
            ),
            coursesInProgress: 5
        )
    }

    func testTheBarSegmentsAreSharesOfTheWholePensumNotOfEachOther() {
        let semester = progress(passed: 112, inProgress: 15, total: 142)
        // The widths HomeIOS.dc.html draws: 78.9% green, 10.6% lime.
        XCTAssertEqual(semester.passedFraction, 0.789, accuracy: 0.001)
        XCTAssertEqual(semester.inProgressFraction, 0.106, accuracy: 0.001)
    }

    func testAnEmptyPensumLeavesTheBarEmptyInsteadOfDividingByZero() {
        let semester = progress(passed: 0, inProgress: 0, total: 0)
        XCTAssertEqual(semester.passedFraction, 0)
        XCTAssertEqual(semester.inProgressFraction, 0)
    }

    // MARK: - The card

    private let morning = [
        Fixture.occurrence("BASES DE DATOS", start: "08:00", end: "10:00"),
        Fixture.occurrence("ARQUITECTURA DE SW", start: "11:00", end: "13:00"),
        Fixture.occurrence("REDES II", start: "14:00", end: "16:00")
    ]

    func testAnEmptyDayIsAFreeDay() {
        XCTAssertEqual(DayState.from([], nowMinutes: 9 * 60), .noClassesToday)
    }

    func testBeforeTheFirstClassItIsNextAndSaysWhenItStarts() {
        guard case .classes(let next, let later) = DayState.from(morning, nowMinutes: 7 * 60 + 35) else {
            return XCTFail("expected classes")
        }
        XCTAssertEqual(next.courseName, "Bases de Datos")
        XCTAssertEqual(next.startTime, "8:00")
        XCTAssertEqual(next.startsInMinutes, 25)
        XCTAssertEqual(later.map(\.courseName), ["Arquitectura de SW", "Redes II"])
    }

    func testAClassUnderWayIsStillTheNextOneButCarriesNoBadge() {
        guard case .classes(let next, let later) = DayState.from(morning, nowMinutes: 9 * 60) else {
            return XCTFail("expected classes")
        }
        XCTAssertEqual(next.courseName, "Bases de Datos")
        XCTAssertNil(next.startsInMinutes)
        XCTAssertEqual(later.count, 2)
    }

    func testAClassThatHasEndedLeavesTheCard() {
        guard case .classes(let next, let later) = DayState.from(morning, nowMinutes: 10 * 60) else {
            return XCTFail("expected classes")
        }
        XCTAssertEqual(next.courseName, "Arquitectura de SW")
        XCTAssertEqual(later.map(\.courseName), ["Redes II"])
    }

    func testOnceEveryClassHasEndedTheDayIsDone() {
        XCTAssertEqual(DayState.from(morning, nowMinutes: 16 * 60), .doneForToday)
    }

    func testTheBadgeReadsInHoursPastAnHour() {
        XCTAssertEqual(ClassSummary.startsInLabel(25), "Empieza en 25 min")
        XCTAssertEqual(ClassSummary.startsInLabel(60), "Empieza en 1 h")
        XCTAssertEqual(ClassSummary.startsInLabel(345), "Empieza en 5 h 45 min")
    }
}
