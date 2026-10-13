import SwiftUI

/// The five screens behind the floating bar.
///
/// Declared in the order the bar draws them, left to right: Inicio in the middle, not at the left
/// edge. The shortcuts on Inicio use four of the same entries, so a destination cannot be added to
/// one and forgotten in the other. Mirrors `ui/navigation/KAppDestination.kt`.
///
/// `colour` is the colour of the K each destination answers to. It is what tells the four apart on
/// the shortcut tiles, where the icon is the only other difference. Pink is absent on purpose: the
/// active tab is the one thing in the bar that is pink.
enum KAppDestination: String, CaseIterable, Identifiable {
    case profile, semaphore, home, map, schedule

    var id: String { rawValue }

    var title: String {
        switch self {
        case .profile: "Perfil"
        case .semaphore: "Semáforo"
        case .home: "Inicio"
        case .map: "Mapa"
        case .schedule: "Horario"
        }
    }

    var symbol: String {
        switch self {
        case .profile: "person"
        case .semaphore: "square.grid.2x2"
        case .home: "house"
        case .map: "mappin.and.ellipse"
        case .schedule: "calendar"
        }
    }

    /// The icon of the active tab. Named one by one because a filled variant is not a rule of SF
    /// Symbols: "calendar.fill" and "mappin.and.ellipse.fill" do not exist, and an unknown name
    /// draws nothing at all.
    var activeSymbol: String {
        switch self {
        case .profile: "person.fill"
        case .semaphore: "square.grid.2x2.fill"
        case .home: "house.fill"
        case .map: "mappin.and.ellipse"
        case .schedule: "calendar"
        }
    }

    var colour: Color {
        switch self {
        case .profile: KColor.person
        case .semaphore: KColor.passed
        case .home, .map: KColor.brand
        case .schedule: KColor.subject
        }
    }
}
