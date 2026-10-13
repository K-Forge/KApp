import SwiftUI

/// A section title with the link that opens the screen it summarises: "Tu semestre · Ver semáforo".
struct SectionHeading: View {
    let title: String
    var link: String?
    var onLink: () -> Void = {}

    var body: some View {
        HStack {
            Text(title)
                .font(KType.section)
                .foregroundStyle(KColor.text)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            if let link {
                Button(link, action: onLink)
                    .font(KType.link)
                    .tint(KColor.action)
                    .frame(minHeight: KMetrics.buttonHeight)
            }
        }
    }
}

/// The pink button. Full width unless told otherwise, 44 pt at least, dimmed while disabled.
struct PrimaryButton: View {
    let title: String
    var systemImage: String?
    var isLoading = false
    var fullWidth = true
    var height: CGFloat = KMetrics.buttonHeight
    let action: () -> Void

    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView().tint(KColor.onAction)
                } else if let systemImage {
                    Image(systemName: systemImage)
                }
                Text(title).font(KType.rowTitle)
            }
            .foregroundStyle(KColor.onAction)
            .padding(.horizontal, 18)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .frame(height: height)
            .background(KColor.action.opacity(isEnabled ? 1 : 0.55))
            .clipShape(RoundedRectangle(cornerRadius: KMetrics.buttonRadius, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// The outlined variant, for the secondary action next to a pink one.
struct SecondaryButton: View {
    let title: String
    var systemImage: String?
    var tint: Color = KColor.action
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title).font(KType.rowTitle)
            }
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity)
            .frame(height: KMetrics.buttonHeight)
            .overlay(
                RoundedRectangle(cornerRadius: KMetrics.buttonRadius, style: .continuous)
                    .stroke(tint, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }
}

/// A grey bar standing in for text that has not arrived yet.
struct SkeletonBar: View {
    var width: CGFloat?
    let height: CGFloat
    var color: Color = KColor.border
    var radius: CGFloat = 6

    var body: some View {
        RoundedRectangle(cornerRadius: radius, style: .continuous)
            .fill(color)
            .frame(width: width, height: height)
            .frame(maxWidth: width == nil ? .infinity : nil, alignment: .leading)
    }
}

/// The centred card used for every state that is not content: a free day, no schedule yet, a
/// failed load. An icon in a tinted circle, a title, an optional line and an optional action.
struct NoticeCard: View {
    let systemImage: String
    var tint: Color = KColor.subject
    var circle: Color?
    let title: String
    var message: String?
    var actionTitle: String?
    var actionIsLink = false
    var isLoading = false
    var action: () -> Void = {}

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.system(size: 22))
                .foregroundStyle(tint)
                .frame(width: 48, height: 48)
                .background((circle ?? tint).opacity(circle == nil ? 0.16 : 0.30))
                .clipShape(Circle())
                .accessibilityHidden(true)
            Text(title)
                .font(KType.section)
                .foregroundStyle(KColor.text)
                .multilineTextAlignment(.center)
            if let message {
                Text(message)
                    .font(.system(size: 14))
                    .foregroundStyle(KColor.textSoft)
                    .multilineTextAlignment(.center)
            }
            if let actionTitle {
                if actionIsLink {
                    Button(actionTitle, action: action)
                        .font(KType.body.weight(.medium))
                        .tint(KColor.action)
                        .frame(minHeight: KMetrics.buttonHeight)
                } else {
                    PrimaryButton(title: actionTitle, isLoading: isLoading, fullWidth: false, action: action)
                        .padding(.top, 4)
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, minHeight: 168)
        .kCard(elevated: true)
    }
}

/// A one-line message that confirms or warns, shown above a screen's content.
struct InlineMessage: View {
    enum Kind { case success, error, info }

    let kind: Kind
    let text: String
    var onDismiss: (() -> Void)?

    private var tint: Color {
        switch kind {
        case .success: KColor.passed
        case .error: KColor.error
        case .info: KColor.brand
        }
    }

    private var symbol: String {
        switch kind {
        case .success: "checkmark.circle"
        case .error: "exclamationmark.circle"
        case .info: "info.circle"
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: symbol).foregroundStyle(tint)
            Text(text)
                .font(.system(size: 14))
                .foregroundStyle(KColor.text)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let onDismiss {
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(KColor.textSoft)
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Cerrar mensaje")
            }
        }
        .padding(14)
        .background(tint.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: KMetrics.rowRadius, style: .continuous))
    }
}

/// What a load that failed says, and the one thing the student can do about it.
struct FailureCard: View {
    let error: APIError
    let retry: () -> Void

    var body: some View {
        NoticeCard(
            systemImage: error == .offline ? "wifi.slash" : "exclamationmark.triangle",
            tint: KColor.error,
            title: error == .offline ? "Sin conexión" : "No pudimos cargar esto",
            message: error.userMessage,
            actionTitle: "Reintentar",
            action: retry
        )
    }
}
