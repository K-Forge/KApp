import SwiftUI

/// The purple band at the top of every screen behind the bottom bar.
///
/// Inicio puts the greeting and the student's initials in it; the other screens put their name. It
/// runs to the very top of the display and the system draws the status bar over it, which is why
/// `topInset` is passed in: the purple has to reach the edge while the row stays below the status
/// bar. Mirrors `ui/common/BrandBand.kt`.
struct BrandBand<Trailing: View>: View {
    let title: String
    let topInset: CGFloat
    @ViewBuilder var trailing: () -> Trailing

    /// The band below the status bar. The first card climbs `KMetrics.cardOverlap` back into it.
    static var contentHeight: CGFloat { 80 }

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(KColor.surface)
                .frame(width: 40, height: 40)
                .overlay(
                    Image("KonradLogo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 30, height: 30)
                )
                .accessibilityLabel("Escudo de la Fundación Universitaria Konrad Lorenz")

            Text(title)
                .font(KType.bandTitle)
                .tracking(-0.4)
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)

            trailing()
        }
        .frame(height: 44)
        .padding(.horizontal, KMetrics.screenPadding)
        .padding(.top, topInset)
        .frame(maxWidth: .infinity, minHeight: topInset + Self.contentHeight, alignment: .top)
        .background(KColor.brand)
    }
}

extension BrandBand where Trailing == EmptyView {
    init(title: String, topInset: CGFloat) {
        self.init(title: title, topInset: topInset, trailing: { EmptyView() })
    }
}

/// A screen behind the bar: the band, the content climbing into it, pull to refresh, and room at
/// the bottom so the floating bar never covers the last row.
///
/// The scroll view ignores the top safe area so the band can run under the status bar. A band
/// placed inside a scroll view that respects the safe area cannot do that - the scroll view clips
/// its content to its own frame, which starts below the status bar - and that was the pale strip
/// the first build of Inicio showed.
struct BandScreen<Trailing: View, Content: View>: View {
    let title: String
    let onRefresh: () async -> Void
    @ViewBuilder var trailing: () -> Trailing
    @ViewBuilder var content: () -> Content

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    BrandBand(title: title, topInset: proxy.safeAreaInsets.top, trailing: trailing)
                    VStack(alignment: .leading, spacing: 0) {
                        content()
                    }
                    .padding(.horizontal, KMetrics.screenPadding)
                    .padding(.top, -KMetrics.cardOverlap)
                    .padding(.bottom, KMetrics.barContentPadding)
                }
            }
            .ignoresSafeArea(edges: .top)
            .refreshable { await onRefresh() }
            .background(KColor.background)
        }
    }
}

extension BandScreen where Trailing == EmptyView {
    init(
        title: String,
        onRefresh: @escaping () async -> Void,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.init(title: title, onRefresh: onRefresh, trailing: { EmptyView() }, content: content)
    }
}
