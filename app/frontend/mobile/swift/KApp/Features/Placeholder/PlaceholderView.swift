import SwiftUI
import Observation

/// A screen that does not exist yet, with the contract that will fill it printed on it.
///
/// It borrows the band and the card of Inicio rather than inventing a look: no mockup covers it,
/// and a screen invented here would be one more thing to undo. Same as the Android placeholder.
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

/// Perfil. Not designed yet, but it holds the one action a signed-in app cannot be without: signing
/// out. With the session kept on the phone, there is no other way back to the login.
@Observable
@MainActor
final class ProfileViewModel {
    enum State: Equatable {
        case loading
        case loaded(UserProfile)
        case failed(APIError)
    }

    private(set) var state: State = .loading
    let email: String
    private let services: Services

    init(services: Services, email: String) {
        self.services = services
        self.email = email
    }

    func load() async {
        do {
            state = .loaded(try await services.profile.myProfile())
        } catch {
            if case .loaded = state { return }
            state = .failed(error as? APIError ?? .unreadableResponse)
        }
    }
}

struct ProfileView: View {
    let model: ProfileViewModel
    let onSignOut: () -> Void

    @State private var confirmingSignOut = false

    var body: some View {
        BandScreen(title: "Perfil", onRefresh: model.load) {
            VStack(alignment: .leading, spacing: 6) {
                switch model.state {
                case .loading:
                    SkeletonBar(width: 180, height: 18, radius: 7)
                    SkeletonBar(width: 220, height: 13, color: KColor.borderSoft)
                case .loaded(let profile):
                    Text(profile.fullName)
                        .font(KType.subject)
                        .foregroundStyle(KColor.text)
                    Text(profile.email)
                        .font(KType.meta)
                        .foregroundStyle(KColor.textSoft)
                    if let academic = profile.academic {
                        Text("Código \(academic.studentCode) · programa \(academic.programCode) · semestre \(academic.currentLevel)")
                            .font(KType.label)
                            .foregroundStyle(KColor.placeholder)
                            .padding(.top, 4)
                    }
                case .failed:
                    Text(model.email)
                        .font(KType.rowTitle)
                        .foregroundStyle(KColor.text)
                    Text("No pudimos cargar tu perfil.")
                        .font(KType.meta)
                        .foregroundStyle(KColor.textSoft)
                }
            }
            .padding(KMetrics.cardPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .kCard(elevated: true)

            PendingScreenCard(destination: .profile)
                .padding(.top, 14)

            SecondaryButton(title: "Cerrar sesión", systemImage: "rectangle.portrait.and.arrow.right") {
                confirmingSignOut = true
            }
            .padding(.top, 16)
        }
        .confirmationDialog("¿Cerrar sesión?", isPresented: $confirmingSignOut, titleVisibility: .visible) {
            Button("Cerrar sesión", role: .destructive, action: onSignOut)
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Tendrás que volver a ingresar tu correo y tu contraseña.")
        }
    }
}
