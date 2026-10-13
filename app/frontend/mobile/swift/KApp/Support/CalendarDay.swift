import Foundation

/// A calendar date with no time and no timezone - what the contract means by `format: date`.
///
/// Converting "2026-09-21" to a `Date` puts it at midnight in some timezone, and the day it shows
/// can then slide by one. This type does arithmetic on the calendar instead, and only touches a
/// `Date` to find out what today is.
struct CalendarDay: Hashable, Comparable {
    let year: Int
    let month: Int
    let day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// The day `date` falls on for the person holding the phone.
    init(_ date: Date, calendar: Calendar = .current) {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year!, month: parts.month!, day: parts.day!)
    }

    /// "2026-09-21".
    init?(iso: String) {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        self.init(year: parts[0], month: parts[1], day: parts[2])
    }

    var iso: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    static func < (lhs: CalendarDay, rhs: CalendarDay) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    /// Arithmetic runs in UTC with a Gregorian calendar, where every day is 24 hours long, so a
    /// daylight-saving change cannot skip or repeat a date.
    private static let utc: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    private var utcDate: Date {
        Self.utc.date(from: DateComponents(year: year, month: month, day: day))!
    }

    func adding(days: Int) -> CalendarDay {
        CalendarDay(Self.utc.date(byAdding: .day, value: days, to: utcDate)!, calendar: Self.utc)
    }

    var dayOfWeek: DayOfWeek {
        // Gregorian weekday: 1 is Sunday.
        let weekday = Self.utc.component(.weekday, from: utcDate)
        return DayOfWeek.allCases[(weekday + 5) % 7]
    }

    /// The Monday of this day's week. Weeks run Monday to Sunday, as the contract says.
    var monday: CalendarDay {
        adding(days: -dayOfWeek.mondayIndex)
    }

    /// "21 sep".
    var shortLabel: String {
        "\(day) \(Self.monthAbbreviations[month - 1])"
    }

    private static let monthAbbreviations = [
        "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"
    ]
}

/// A time of day as the contract writes it, "18:15", counted in minutes from midnight.
enum TimeOfDay {
    static func minutes(_ hhmm: String) -> Int? {
        let parts = hhmm.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2, (0..<24).contains(parts[0]), (0..<60).contains(parts[1]) else { return nil }
        return parts[0] * 60 + parts[1]
    }

    static func minutes(of date: Date, calendar: Calendar = .current) -> Int {
        let parts = calendar.dateComponents([.hour, .minute], from: date)
        return parts.hour! * 60 + parts.minute!
    }

    static func string(minutes: Int) -> String {
        String(format: "%02d:%02d", minutes / 60, minutes % 60)
    }
}

/// The university's calendar, as far as the client needs it.
enum AcademicCalendar {

    /// "20262" for any date in the second half of 2026. The contract's `AcademicPeriod`: four digits
    /// for the year, then `1` or `2`.
    static func period(containing day: CalendarDay) -> String {
        "\(day.year)\(day.month <= 6 ? 1 : 2)"
    }

    /// The first and last day of a semester.
    ///
    /// An assumption, not data: no contract publishes the academic calendar. Second semester runs
    /// from the last week of July to the end of November - the span the examples in
    /// `schedule.openapi.yaml` use (2026-07-27 to 2026-11-30) - and the first semester mirrors it
    /// from late January to the end of May. A hand-entered class covers the whole of it.
    static func bounds(ofPeriod period: String) -> (start: CalendarDay, end: CalendarDay)? {
        guard period.count == 5, let year = Int(period.prefix(4)), let half = Int(period.suffix(1)) else {
            return nil
        }
        switch half {
        case 1: return (CalendarDay(year: year, month: 1, day: 26), CalendarDay(year: year, month: 5, day: 30))
        case 2: return (CalendarDay(year: year, month: 7, day: 27), CalendarDay(year: year, month: 11, day: 30))
        default: return nil
        }
    }

    /// The first and last dates a weekly class on `dayOfWeek` actually meets in `period`.
    ///
    /// The contract requires both ends of a `MeetingPeriod` to fall on the meeting's own weekday,
    /// so the semester's bounds are walked inwards to the nearest matching day.
    static func meetingRange(on dayOfWeek: DayOfWeek, inPeriod period: String) -> (from: CalendarDay, to: CalendarDay)? {
        guard let bounds = bounds(ofPeriod: period) else { return nil }
        let forward = (dayOfWeek.mondayIndex - bounds.start.dayOfWeek.mondayIndex + 7) % 7
        let backward = (bounds.end.dayOfWeek.mondayIndex - dayOfWeek.mondayIndex + 7) % 7
        let from = bounds.start.adding(days: forward)
        let to = bounds.end.adding(days: -backward)
        return from <= to ? (from, to) : nil
    }
}
