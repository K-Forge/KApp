import SwiftUI

/// Registration with an invitation code, not built yet.
///
/// Its contract is `POST /auth/register` in `docs/api/auth.openapi.yaml`, and the code is what
/// decides whether the account comes out as a student or a professor. The screen exists so the
/// login's second exit lands somewhere and can come back. Same stub as the Android client's.
struct InvitationView: View {
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "ticket")
                .font(.system(size: 28))
                .foregroundStyle(KColor.brand)
                .frame(width: 56, height: 56)
                .background(KColor.brand.opacity(0.10))
                .clipShape(Circle())
            Text("Código de invitación")
                .font(KType.section)
                .foregroundStyle(KColor.text)
            Text("El registro con código de invitación todavía no existe. Su contrato es POST /auth/register en docs/api/auth.openapi.yaml.")
                .font(.system(size: 14))
                .foregroundStyle(KColor.textSoft)
                .multilineTextAlignment(.center)
            Button("Volver", action: onBack)
                .font(KType.body.weight(.medium))
                .tint(KColor.action)
                .frame(minHeight: KMetrics.buttonHeight)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(KColor.background)
    }
}
