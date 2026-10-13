import Foundation

/// Turns what the API stores into what the screen prints.
enum DisplayText {

    private static let spanish = Locale(identifier: "es_CO")

    /// Words that stay lowercase inside a name: "Desarrollo de Aplicaciones Móviles".
    private static let connectors: Set<String> = [
        "a", "al", "con", "de", "del", "e", "el", "en", "la", "las", "lo", "los",
        "o", "para", "por", "u", "y"
    ]

    /// A course or professor name, from the report's uppercase to title case.
    ///
    /// Schedule-service stores names as the SINU report prints them - "ESTADÍSTICA DESCRIPTIVA",
    /// "CAMPOS AVENDANO GUSTAVO ANDRES" - and the mockups print "Estadística Descriptiva". Roman
    /// numerals stay uppercase ("Ingeniería de Software II"), and so does a word with no vowels,
    /// which is an acronym ("Arquitectura de SW"). A name that is already mixed case is left alone:
    /// somebody typed it that way.
    static func title(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        guard trimmed == trimmed.uppercased(with: spanish) else { return trimmed }

        let words = trimmed.split(separator: " ", omittingEmptySubsequences: true).map(String.init)
        return words.enumerated().map { index, word in
            let lower = word.lowercased(with: spanish)
            if index > 0 && connectors.contains(lower) { return lower }
            if isRomanNumeral(word) { return word }
            if isAcronym(word) { return word }
            return lower.prefix(1).uppercased(with: spanish) + lower.dropFirst()
        }
        .joined(separator: " ")
    }

    /// Weekly hours, which the contract lets carry a half: "4" or "4,5", with the comma Spanish
    /// writes decimals with.
    static func hours(_ value: Double) -> String {
        value == value.rounded()
            ? String(Int(value))
            : String(format: "%.1f", value).replacingOccurrences(of: ".", with: ",")
    }

    /// "08:00" to "8:00". The mockups drop the leading zero; the contract always sends two digits.
    static func time(_ hhmm: String) -> String {
        guard hhmm.count == 5, hhmm.hasPrefix("0") else { return hhmm }
        return String(hhmm.dropFirst())
    }

    /// "Salón 302 · Sede Principal", or only the campus while no room is assigned.
    ///
    /// A room is a bare code on the wire ("302") and the word is added here. A null room is a real
    /// state - the contract says so - and is printed as "Sin salón" rather than hidden, because
    /// "where is my class" is exactly the question the row answers.
    static func place(room: String?, campus: String?) -> String {
        let roomText: String
        if let room, !room.isEmpty {
            roomText = room.allSatisfy(\.isNumber) ? "Salón \(room)" : room
        } else {
            roomText = "Sin salón"
        }
        return [roomText, campus].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }

    /// I to XXXIX. "CIVIL" is spelled with roman letters and is not one, which is why this is a
    /// pattern and not a character set.
    private static func isRomanNumeral(_ word: String) -> Bool {
        word.range(of: "^X{0,3}(IX|IV|V?I{0,3})$", options: .regularExpression) != nil && !word.isEmpty
    }

    private static func isAcronym(_ word: String) -> Bool {
        let letters = word.folding(options: .diacriticInsensitive, locale: spanish).lowercased()
        guard letters.count >= 2, letters.allSatisfy(\.isLetter) else { return false }
        return !letters.contains { "aeiou".contains($0) }
    }
}
