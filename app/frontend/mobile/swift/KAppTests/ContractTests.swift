import XCTest
@testable import KApp

/// The places where the client has to say exactly what the contracts say: bodies the server rejects
/// when a key is missing, errors mapped from statuses, and the week resolved the server's way.
final class ContractTests: XCTestCase {

    private func json(_ value: some Encodable) throws -> [String: Any] {
        let data = try JSONEncoder().encode(value)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private let enrollment = CreateEnrollmentRequest(
        courseCode: "56201",
        pensumItemCode: "1806",
        courseName: "METODOLOGÍA DE LA INVESTIGACIÓN",
        level: 8,
        credits: 2,
        totalHours: 48,
        group: "01",
        subgroup: nil,
        professor: "POR ASIGNAR",
        campus: "Sede Principal",
        startDate: "2026-07-29",
        endDate: "2026-11-25",
        color: "#539392",
        meetings: [CreateMeetingRequest(
            dayOfWeek: .wednesday,
            startTime: "07:00",
            endTime: "09:00",
            periods: [MeetingPeriod(from: "2026-07-29", to: "2026-11-25", room: nil)]
        )]
    )

    // MARK: - Bodies

    func testAnEnrollmentSendsSubgroupAndRoomAsExplicitNulls() throws {
        // `JSONEncoder` leaves nil keys out by default, and the contract rejects a body without them.
        let body = try json(enrollment)
        XCTAssertTrue(body.keys.contains("subgroup"))
        XCTAssertTrue(body["subgroup"] is NSNull)

        let meeting = try XCTUnwrap((body["meetings"] as? [[String: Any]])?.first)
        let period = try XCTUnwrap((meeting["periods"] as? [[String: Any]])?.first)
        XCTAssertTrue(period.keys.contains("room"))
        XCTAssertTrue(period["room"] is NSNull)
        XCTAssertEqual(meeting["dayOfWeek"] as? String, "WEDNESDAY")
    }

    func testAStatusUpdateSaysNullForWhatItClears() throws {
        let body = try json(CourseProgressUpdateRequest(status: .pending, grade: nil, period: nil))
        XCTAssertEqual(body["status"] as? String, "PENDING")
        XCTAssertTrue(body["grade"] is NSNull)
        XCTAssertTrue(body["period"] is NSNull)
    }

    func testReadsTheWeekTheContractDescribes() throws {
        let data = Data("""
        {"weekStart":"2026-09-21","weekEnd":"2026-09-27","days":{
          "MONDAY":[{"enrollmentId":"a","courseCode":"17080","courseName":"ESTADÍSTICA DESCRIPTIVA",
                     "professor":"CAMPOS","startTime":"18:15","endTime":"20:30","room":null,
                     "campus":"Sede Principal","color":"#539392"}],
          "TUESDAY":[],"WEDNESDAY":[],"THURSDAY":[],"FRIDAY":[],"SATURDAY":[],"SUNDAY":[]}}
        """.utf8)
        let week = try JSONDecoder().decode(WeekAgenda.self, from: data)
        XCTAssertEqual(week.classes(on: .monday).first?.room, nil)
        XCTAssertEqual(week.classes(on: .tuesday), [])
    }

    func testAPensumItemMayBeTaughtForHalfAnHourMore() throws {
        // Four items across the published plans print 4,5 or 1,5 weekly hours. Reading the field
        // into an integer breaks on exactly those four, and on nothing else.
        let data = Data("""
        {"code":"MKT-101","sinuCode":null,"pensumItemCode":"3001","name":"PRÁCTICA PROFESIONAL",
         "level":8,"credits":2,"weeklyHours":4.5,"totalHours":72,"area":"MKT",
         "isElectiveSlot":false,"prerequisites":[]}
        """.utf8)
        let course = try JSONDecoder().decode(PensumCourse.self, from: data)
        XCTAssertEqual(course.weeklyHours, 4.5)
        XCTAssertEqual(DisplayText.hours(course.weeklyHours), "4,5")
        XCTAssertEqual(DisplayText.hours(4), "4")
    }

    func testAGeneratedCodeIsNeverTheOneShown() throws {
        // `sinuCode` is the only field that says a code is the university's. A plan that prints
        // none gets a KApp-generated `code`, which must not reach a screen.
        let data = Data("""
        {"code":"MKT-101","sinuCode":null,"pensumItemCode":"3001","name":"MERCADEO",
         "level":1,"credits":3,"weeklyHours":3,"totalHours":48,"area":"MKT",
         "isElectiveSlot":false,"prerequisites":[]}
        """.utf8)
        let course = try JSONDecoder().decode(PensumCourse.self, from: data)
        XCTAssertNil(course.sinuCode)
        // It is still what the item is addressed by.
        XCTAssertEqual(course.identifier, "MKT-101")
    }

    // MARK: - Errors

    func testStatusesMapToTheirCasesAndCarryTheServersMessage() {
        let body = Data(#"{"status":400,"message":"password: size must be between 10 and 72"}"#.utf8)
        XCTAssertEqual(APIError.from(status: 400, body: body), .badRequest("password: size must be between 10 and 72"))
        XCTAssertEqual(APIError.from(status: 401, body: Data()), .unauthorized)
        XCTAssertEqual(APIError.from(status: 403, body: Data()), .forbidden)
        XCTAssertEqual(APIError.from(status: 404, body: Data()), .notFound)
        XCTAssertEqual(APIError.from(status: 409, body: Data()), .conflict(nil))
        XCTAssertEqual(APIError.from(status: 503, body: Data()), .server(status: 503))
    }

    // MARK: - The week after a change

    func testRemovingAnEnrollmentTakesItOffEveryDay() {
        let week = WeekAgenda(weekStart: "2026-09-21", weekEnd: "2026-09-27", days: [
            "MONDAY": [Fixture.occurrence(start: "18:15", end: "20:30", enrollmentId: "a")],
            "WEDNESDAY": [
                Fixture.occurrence(start: "07:00", end: "09:00", enrollmentId: "b"),
                Fixture.occurrence(start: "18:15", end: "20:30", enrollmentId: "a")
            ]
        ])
        let after = week.removingEnrollment("a")
        XCTAssertEqual(after.classes(on: .monday), [])
        XCTAssertEqual(after.classes(on: .wednesday).map(\.enrollmentId), ["b"])
    }

    func testAnAddedCourseLandsOnItsWeekdayInTimeOrder() {
        let week = WeekAgenda(weekStart: "2026-09-21", weekEnd: "2026-09-27", days: [
            "WEDNESDAY": [Fixture.occurrence(start: "18:15", end: "21:15", enrollmentId: "late")]
        ])
        let after = week.adding(enrollment, enrollmentId: "new")
        XCTAssertEqual(after.classes(on: .wednesday).map(\.enrollmentId), ["new", "late"])
        XCTAssertNil(after.classes(on: .wednesday).first?.room)
    }

    func testAnAddedCourseOutsideItsDateRangeDoesNotShowThatWeek() {
        let december = WeekAgenda(weekStart: "2026-12-07", weekEnd: "2026-12-13", days: [:])
        XCTAssertEqual(december.adding(enrollment, enrollmentId: "new").classes(on: .wednesday), [])
    }
}
