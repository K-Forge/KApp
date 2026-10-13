import SwiftUI

/// The login, or the signed-in app. Nothing else decides which.
struct RootView: View {
    let app: AppModel

    var body: some View {
        if let workspace = app.workspace {
            MainShell(workspace: workspace, app: app)
                .transition(.opacity)
        } else {
            LoginFlow(app: app)
                .transition(.opacity)
        }
    }
}

/// The two screens outside the bar: the login and the invitation code.
///
/// The bar is not shown here. It would offer four destinations to somebody who has not signed in.
private struct LoginFlow: View {
    let app: AppModel
    @State private var path: [Route] = []
    // Held here rather than built in `body`, which runs again on every change: a view model made
    // there would be a new one each time, and would take what the student typed with it.
    @State private var model: LoginViewModel

    enum Route: Hashable { case invitation }

    init(app: AppModel) {
        self.app = app
        _model = State(initialValue: LoginViewModel(auth: app.services.auth))
    }

    var body: some View {
        NavigationStack(path: $path) {
            LoginView(
                model: model,
                notice: app.signedOutNotice,
                onSignedIn: { token in
                    withAnimation {
                        app.signIn(token, email: model.email, keepSignedIn: model.keepSignedIn)
                    }
                },
                onDismissNotice: app.clearSignedOutNotice,
                onUseInvitationCode: { path.append(.invitation) }
            )
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Route.self) { _ in
                InvitationView(onBack: { path.removeLast() })
                    .toolbar(.hidden, for: .navigationBar)
            }
        }
    }
}

/// A signed-in screen with the floating bar over it.
///
/// The bar is drawn after the content and in the same stack, so it sits on top of whatever scrolls
/// under it. Every tab refreshes when it is selected, which is how a course marked as passed on the
/// semáforo reaches the progress card on Inicio without the two screens knowing about each other.
struct MainShell: View {
    let workspace: Workspace
    let app: AppModel

    var body: some View {
        ZStack(alignment: .bottom) {
            KColor.background.ignoresSafeArea()

            Group {
                switch workspace.selectedTab {
                case .home:
                    HomeView(model: workspace.home, open: workspace.open)
                case .schedule:
                    ScheduleView(model: workspace.schedule, open: workspace.open)
                case .semaphore:
                    SemaphoreView(model: workspace.semaphore)
                case .map:
                    PlaceholderView(destination: .map)
                case .profile:
                    ProfileView(model: workspace.profile, onSignOut: {
                        withAnimation { app.signOut() }
                    })
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            KTabBar(selection: workspace.selectedTab, onSelect: workspace.open)
                .padding(.horizontal, KMetrics.tabBarInset)
                .padding(.bottom, KMetrics.tabBarBottom)
        }
        .task(id: workspace.selectedTab) {
            await refresh(workspace.selectedTab)
        }
    }

    private func refresh(_ tab: KAppDestination) async {
        switch tab {
        case .home: await workspace.home.load()
        case .schedule: await workspace.schedule.load()
        case .semaphore: await workspace.semaphore.load()
        case .profile: await workspace.profile.load()
        case .map: break
        }
    }
}

/// The floating bar: Perfil, Semáforo, Inicio, Mapa, Horario.
struct KTabBar: View {
    let selection: KAppDestination
    let onSelect: (KAppDestination) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(KAppDestination.allCases) { tab in
                let active = tab == selection
                Button {
                    onSelect(tab)
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: active ? tab.activeSymbol : tab.symbol)
                            .font(.system(size: 20, weight: active ? .semibold : .regular))
                            .frame(height: 24)
                        Text(tab.title)
                            .font(KType.tab.weight(active ? .semibold : .regular))
                    }
                    .foregroundStyle(active ? KColor.action : KColor.iconIdle)
                    .frame(maxWidth: .infinity, minHeight: KMetrics.buttonHeight)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(active ? [.isSelected] : [])
            }
        }
        .padding(.horizontal, 4)
        .frame(height: KMetrics.tabBarHeight)
        .background(KColor.surface)
        .clipShape(RoundedRectangle(cornerRadius: KMetrics.tabBarRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: KMetrics.tabBarRadius, style: .continuous)
                .stroke(KColor.borderSoft, lineWidth: 1)
        )
        .shadow(color: KColor.brand.opacity(0.20), radius: 13, x: 0, y: 8)
    }
}
