import SwiftUI

/// The colours of the K and the role each one plays.
///
/// The seven brand colours are the ones in `docs/K-COLORS.md`; the greys and tints are the ones
/// `docs/design/mobile/Tokens.dc.html` derives from them. The names match
/// `ui/theme/Color.kt` in the Android client, so the two can be read side by side.
enum KColor {

    /// Pink. Actions, and nothing else: buttons, links, the active tab.
    static let action = Color(hex: 0xD51A65)
    static let onAction = Color.white

    /// Pink over blue. The brand: the login band and the band on every screen behind the bar.
    static let brand = Color(hex: 0x522567)

    /// Green. A course currently being taken. Never put white on it - 1.5:1.
    static let inProgress = Color(hex: 0xC9D329)

    /// Blue. A course, on the rail of its card.
    static let subject = Color(hex: 0x539392)

    /// Green over blue. A course already passed.
    static let passed = Color(hex: 0x3E823E)

    /// Pink over green. Errors, and a failed course.
    static let error = Color(hex: 0xB62325)

    /// All three at once. The person: the profile shortcut and the profile tab.
    static let person = Color(hex: 0x592E2A)

    static let background = Color(hex: 0xF7F4F8)
    static let surface = Color.white
    static let text = Color(hex: 0x1C1420)
    static let textSoft = Color(hex: 0x6B6472)
    static let border = Color(hex: 0xE7E2EA)

    /// A line too quiet to be a border: the edge of the floating bar, the paler skeleton bars.
    static let borderSoft = Color(hex: 0xEFEBF1)

    /// A course not taken yet. Same value as `border`; the two roles are named apart on purpose.
    static let pending = Color(hex: 0xE7E2EA)

    /// Hint text inside an empty field, and quiet secondary text.
    static let placeholder = Color(hex: 0xA29BA9)

    /// The fixed "@konradlorenz.edu.co" printed after what the student types.
    static let domainSuffix = Color(hex: 0xC4BDCA)

    /// White at 74%, for text sitting on the purple band.
    static let onBrandSoft = Color.white.opacity(0.74)

    /// The grey of an inactive tab icon.
    static let iconIdle = Color(hex: 0x8E8794)

    /// The colours a student can give a course they add to the timetable.
    ///
    /// Pink is left out on purpose: it means "you can touch this", and a course painted pink
    /// would make every class rail look like a button.
    static let courseChoices: [(name: String, hex: String)] = [
        ("Azul", "#539392"),
        ("Morado", "#522567"),
        ("Verde", "#3E823E"),
        ("Café", "#592E2A"),
        ("Lima", "#C9D329"),
        ("Rojo", "#B62325")
    ]
}

extension Color {
    /// `Color(hex: 0xD51A65)`, so the hexes in the design documents can be pasted as they are.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }

    /// A colour the server sent, such as `ClassOccurrence.color` or `PensumArea.color`.
    ///
    /// The client does not choose a course's colour: it paints the one it was given. A string that
    /// cannot be read falls back to the subject blue rather than failing, because a class with the
    /// wrong rail is better than a list that does not draw.
    init(apiHex: String?) {
        guard let value = Color.hexValue(apiHex) else {
            self = KColor.subject
            return
        }
        self.init(hex: value)
    }

    /// `"#539392"` to `0x539392`, or nil when the string is not a six-digit hex colour.
    static func hexValue(_ string: String?) -> UInt32? {
        let cleaned = (string ?? "")
            .trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: "#", with: "")
        guard cleaned.count == 6 else { return nil }
        return UInt32(cleaned, radix: 16)
    }
}
