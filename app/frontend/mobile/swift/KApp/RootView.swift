import SwiftUI

/// The app's first screen. Until the sign-in exists (#135), the shell opens directly.
struct RootView: View {
    @State private var selection: KAppDestination = .home

    var body: some View {
        MainShell(
            destinations: KAppDestination.allCases,
            selection: $selection
        ) { destination in
            PlaceholderView(destination: destination)
        }
    }
}

/// A signed-in screen with the floating bar over it.
///
/// The bar is drawn after the content and in the same stack, so it sits on top of whatever scrolls
/// under it. Which tabs it offers is the caller's decision: it depends on the profile role.
struct MainShell<Screen: View>: View {
    let destinations: [KAppDestination]
    @Binding var selection: KAppDestination
    @ViewBuilder let screen: (KAppDestination) -> Screen

    var body: some View {
        ZStack(alignment: .bottom) {
            KColor.background.ignoresSafeArea()

            screen(selection)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            if destinations.count > 1 {
                KTabBar(destinations: destinations, selection: selection) { selection = $0 }
                    .padding(.horizontal, KMetrics.tabBarInset)
                    .padding(.bottom, KMetrics.tabBarBottom)
            }
        }
    }
}

/// The floating bar: Perfil, Semáforo, Inicio, Mapa, Horario, or the part of them a role sees.
struct KTabBar: View {
    let destinations: [KAppDestination]
    let selection: KAppDestination
    let onSelect: (KAppDestination) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(destinations) { tab in
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
