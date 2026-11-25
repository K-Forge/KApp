import SwiftUI

/// The type scale of the mockups.
///
/// The same on iOS and Android; only the family changes (SF Pro here, Roboto there). Sizes are
/// fixed rather than `Font.title`/`.body` because the mockups are drawn to the point and both
/// clients have to land in the same place. Dynamic Type is not handled yet - see the README.
enum KType {
    /// 26 / 700 - the title on the band: "Hola, Pepe", "Horario".
    static let bandTitle = Font.system(size: 26, weight: .bold)
    /// 20 / 600 - a course name at the top of a card.
    static let subject = Font.system(size: 20, weight: .semibold)
    /// 17 / 600 - a section heading.
    static let section = Font.system(size: 17, weight: .semibold)
    /// 15 / 600 - a row title, a field label's value, a button.
    static let rowTitle = Font.system(size: 15, weight: .semibold)
    /// 15 / 400 - body.
    static let body = Font.system(size: 15)
    /// 14 / 500 - links and secondary data.
    static let link = Font.system(size: 14, weight: .medium)
    /// 13 / 400 - metadata.
    static let meta = Font.system(size: 13)
    /// 13 / 600 - a field label.
    static let fieldLabel = Font.system(size: 13, weight: .semibold)
    /// 12 / 400 - labels.
    static let label = Font.system(size: 12)
    /// 11 / 600, tracked - the one small-caps line in the app.
    static let eyebrow = Font.system(size: 11, weight: .semibold)
    /// 10 / 600 - the tab labels.
    static let tab = Font.system(size: 10, weight: .semibold)
}

/// Radii, heights and spacing. Also shared with Android.
enum KMetrics {
    static let cardRadius: CGFloat = 18
    static let rowRadius: CGFloat = 14
    static let buttonRadius: CGFloat = 14
    static let fieldRadius: CGFloat = 12
    static let tileRadius: CGFloat = 16

    static let fieldHeight: CGFloat = 52
    /// 44 is the iOS minimum touch target. No control goes below it.
    static let buttonHeight: CGFloat = 44
    static let rowHeight: CGFloat = 58

    /// The side margin every screen uses.
    static let screenPadding: CGFloat = 16
    static let cardPadding: CGFloat = 16

    /// How far the first card climbs back into the band.
    static let cardOverlap: CGFloat = 24

    /// The floating bar: height, side margin, gap to the bottom edge and corner radius.
    static let tabBarHeight: CGFloat = 62
    static let tabBarInset: CGFloat = 16
    static let tabBarBottom: CGFloat = 8
    static let tabBarRadius: CGFloat = 22

    /// What a screen leaves free at its bottom so the bar does not cover its last row.
    static let barContentPadding: CGFloat = tabBarHeight + tabBarBottom + 24
}

extension View {
    /// The white card of the mockups: fill, radius and the faint purple shadow.
    func kCard(radius: CGFloat = KMetrics.cardRadius, elevated: Bool = false) -> some View {
        background(KColor.surface)
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .shadow(
                color: KColor.brand.opacity(elevated ? 0.16 : 0.07),
                radius: elevated ? 14 : 5,
                x: 0,
                y: elevated ? 10 : 2
            )
    }
}

/// The tricolour edge of the K: pink, green and blue in equal parts.
///
/// It sits where the white sheet rides over the purple. It is the brand device of the app, and
/// replaces the gradient the frozen web login had.
struct KTricolorEdge: View {
    var height: CGFloat = 4

    var body: some View {
        HStack(spacing: 0) {
            KColor.action
            KColor.inProgress
            KColor.subject
        }
        .frame(height: height)
    }
}
