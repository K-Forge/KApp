import SwiftUI

/// A screen that is not built yet, with the contract that will fill it printed on it.
///
/// It borrows the band and the card of Inicio rather than inventing a look. Same as the Android
/// placeholder.
struct PlaceholderView: View {
    let destination: KAppDestination

    var body: some View {
        BandScreen(title: destination.title, onRefresh: {}) {
            PendingScreenCard(destination: destination)
        }
    }
}

struct PendingScreenCard: View {
    let destination: KAppDestination

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: destination.symbol)
                .font(.system(size: 22))
                .foregroundStyle(destination.colour)
                .frame(width: 48, height: 48)
                .background(destination.colour.opacity(0.14))
                .clipShape(Circle())
                .accessibilityHidden(true)
            Text("Esta pantalla todavía no existe")
                .font(KType.section)
                .foregroundStyle(KColor.text)
            Text(contract)
                .font(.system(size: 14))
                .foregroundStyle(KColor.textSoft)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 28)
        .frame(maxWidth: .infinity)
        .kCard(elevated: true)
    }

    private var contract: String {
        switch destination {
        case .profile: "Se alimentará de GET /api/users/me."
        case .map: "Se alimentará de GET /api/map/buildings."
        case .semaphore: "Se alimentará de GET /api/semaphore/me."
        case .schedule: "Se alimentará de GET /api/schedule/me/week."
        case .home: "Se alimentará de GET /api/schedule/me/day."
        }
    }
}
