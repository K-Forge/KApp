import XCTest
@testable import KApp

/// What the screen prints out of what the API stores, and the calendar arithmetic that has to land
/// on the right date without a timezone ever touching it.
final class TextAndCalendarTests: XCTestCase {

    // MARK: - Names

    func testReportUppercaseBecomesTitleCaseWithLowercaseConnectors() {
        XCTAssertEqual(DisplayText.title("DESARROLLO DE APLICACIONES MÓVILES"), "Desarrollo de Aplicaciones Móviles")
        XCTAssertEqual(DisplayText.title("METODOLOGÍA DE LA INVESTIGACIÓN"), "Metodología de la Investigación")
    }

    func testRomanNumeralsStayUppercase() {
        XCTAssertEqual(DisplayText.title("INGENIERÍA DE SOFTWARE II"), "Ingeniería de Software II")
        XCTAssertEqual(DisplayText.title("CÁLCULO IV"), "Cálculo IV")
    }

    func testAWordSpelledInRomanLettersIsNotANumeral() {
        XCTAssertEqual(DisplayText.title("DERECHO CIVIL"), "Derecho Civil")
    }

    func testAWordWithNoVowelsIsAnAcronym() {
        XCTAssertEqual(DisplayText.title("ARQUITECTURA DE SW"), "Arquitectura de SW")
    }

    func testAProfessorNameInReportOrderReadsLikeAName() {
        XCTAssertEqual(DisplayText.title("CAMPOS AVENDANO GUSTAVO ANDRES"), "Campos Avendano Gustavo Andres")
    }

    func testANameAlreadyInMixedCaseIsLeftAsTyped() {
        XCTAssertEqual(DisplayText.title("Bases de Datos"), "Bases de Datos")
    }

    func testTheLeadingZeroOfAnHourIsDropped() {
        XCTAssertEqual(DisplayText.time("08:00"), "8:00")
        XCTAssertEqual(DisplayText.time("18:15"), "18:15")
    }

    func testARoomCodeGetsItsWordAndANullRoomIsSaidOutLoud() {
        XCTAssertEqual(DisplayText.place(room: "302", campus: "Sede Principal"), "Salón 302 · Sede Principal")
        XCTAssertEqual(DisplayText.place(room: "Lab 105", campus: "Bloque C"), "Lab 105 · Bloque C")
        XCTAssertEqual(DisplayText.place(room: nil, campus: "Sede Principal"), "Sin salón · Sede Principal")
    }

    // MARK: - Days

    func testReadsAndWritesTheContractDateFormat() {
        let day = CalendarDay(iso: "2026-09-21")
        XCTAssertEqual(day, CalendarDay(year: 2026, month: 9, day: 21))
        XCTAssertEqual(day?.iso, "2026-09-21")
        XCTAssertNil(CalendarDay(iso: "21/09/2026"))
    }

    func testKnowsTheWeekdayAndItsMonday() {
        let wednesday = CalendarDay(year: 2026, month: 9, day: 23)
        XCTAssertEqual(wednesday.dayOfWeek, .wednesday)
        XCTAssertEqual(wednesday.monday, CalendarDay(year: 2026, month: 9, day: 21))
        XCTAssertEqual(CalendarDay(year: 2026, month: 9, day: 27).monday, CalendarDay(year: 2026, month: 9, day: 21))
    }

    func testAddsDaysAcrossAMonthAndAYear() {
        XCTAssertEqual(CalendarDay(year: 2026, month: 9, day: 28).adding(days: 6), CalendarDay(year: 2026, month: 10, day: 4))
        XCTAssertEqual(CalendarDay(year: 2026, month: 12, day: 30).adding(days: 3), CalendarDay(year: 2027, month: 1, day: 2))
    }

    func testReadsTimesAsMinutesAndRejectsNonsense() {
        XCTAssertEqual(TimeOfDay.minutes("18:15"), 1095)
        XCTAssertNil(TimeOfDay.minutes("25:00"))
        XCTAssertEqual(TimeOfDay.string(minutes: 7 * 60), "07:00")
    }

    // MARK: - Semesters

    func testTheHalfOfTheYearNamesThePeriod() {
        XCTAssertEqual(AcademicCalendar.period(containing: CalendarDay(year: 2026, month: 3, day: 1)), "20261")
        XCTAssertEqual(AcademicCalendar.period(containing: CalendarDay(year: 2026, month: 9, day: 17)), "20262")
    }

    func testAWeeklyClassMeetsFromItsFirstToItsLastWeekdayOfTheSemester() {
        // 2026-2 runs 27 July to 30 November. The first Wednesday is 29 July and the last is 25 November:
        // the contract wants both ends of a range on the meeting's own weekday.
        let range = AcademicCalendar.meetingRange(on: .wednesday, inPeriod: "20262")
        XCTAssertEqual(range?.from.iso, "2026-07-29")
        XCTAssertEqual(range?.to.iso, "2026-11-25")
        XCTAssertEqual(range?.from.dayOfWeek, .wednesday)
        XCTAssertEqual(range?.to.dayOfWeek, .wednesday)
    }

    func testAMondayClassStartsOnTheSemestersFirstDay() {
        let range = AcademicCalendar.meetingRange(on: .monday, inPeriod: "20262")
        XCTAssertEqual(range?.from.iso, "2026-07-27")
        XCTAssertEqual(range?.to.iso, "2026-11-30")
    }
}
