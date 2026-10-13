import SwiftUI

/// Sign-in, against `POST /auth/login`.
///
/// Laid out after `docs/design/mobile/Main.dc.html`, with the states of `EstadosLogin.dc.html`. The
/// whole screen scrolls, so with the keyboard open the sheet lifts and the Ingresar button is never
/// covered - the design note says in so many words that this is solved in code.
struct LoginView: View {
    @Bindable var model: LoginViewModel
    /// Why the student is here when it was not their choice, such as an expired session.
    let notice: String?
    let onSignedIn: (TokenResponse) -> Void
    let onDismissNotice: () -> Void
    let onUseInvitationCode: () -> Void

    @FocusState private var focus: Field?

    private enum Field { case email, password }

    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top
            let fullHeight = proxy.size.height + top + proxy.safeAreaInsets.bottom
            let sheetTop = top + 290

            ScrollView {
                ZStack(alignment: .top) {
                    // The band runs to the very top of the display; the system draws the status bar
                    // over it.
                    KColor.brand.frame(height: sheetTop + 32)

                    VStack(spacing: 0) {
                        brandMark
                            .padding(.top, top + 34)
                        Spacer(minLength: 0)
                    }
                    .frame(height: sheetTop)

                    sheet
                        .frame(minHeight: fullHeight - sheetTop, alignment: .top)
                        .padding(.top, sheetTop)
                }
                .frame(minHeight: fullHeight, alignment: .top)
            }
            .ignoresSafeArea(edges: .top)
            .scrollDismissesKeyboard(.interactively)
            .background(KColor.surface)
        }
    }

    // MARK: - Brand

    private var brandMark: some View {
        VStack(spacing: 16) {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(KColor.surface)
                .frame(width: 84, height: 84)
                .overlay(
                    Image("KonradLogo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 60, height: 60)
                )
                .shadow(color: .black.opacity(0.18), radius: 12, x: 0, y: 8)
                .accessibilityLabel("Escudo de la Fundación Universitaria Konrad Lorenz")

            VStack(spacing: 4) {
                Text("KApp")
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(.white)
                Text("Fundación Universitaria Konrad Lorenz")
                    .font(KType.body)
                    .foregroundStyle(KColor.onBrandSoft)
            }
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Sheet

    private var sheet: some View {
        VStack(spacing: 0) {
            KTricolorEdge()

            VStack(alignment: .leading, spacing: 20) {
                if let notice {
                    InlineMessage(kind: .info, text: notice, onDismiss: onDismissNotice)
                }

                switch model.state {
                case .unverified(let email), .resending(let email):
                    verification(email: email, isResending: model.state == .resending(email: email))
                case .verificationSent(let email):
                    InlineMessage(
                        kind: .success,
                        text: "Te reenviamos el enlace a \(email). Ábrelo y vuelve a entrar."
                    )
                    anotherAccountLink
                default:
                    form
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 32)

            Spacer(minLength: 32)

            Text("© 2026 Fundación Universitaria Konrad Lorenz")
                .font(.system(size: 11))
                .foregroundStyle(KColor.placeholder)
                .frame(maxWidth: .infinity)
                .padding(.bottom, 28)
        }
        .background(KColor.surface)
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28, style: .continuous))
        .shadow(color: KColor.brand.opacity(0.22), radius: 16, x: 0, y: -8)
    }

    @ViewBuilder
    private var form: some View {
        let sending = model.state == .sending
        let invalid = model.state == .invalidCredentials

        Group {
            emailField(invalid: invalid)
            passwordField(invalid: invalid)
        }
        .disabled(sending)
        .opacity(sending ? 0.55 : 1)

        switch model.state {
        case .invalidCredentials:
            errorRow("Revisa tu correo o tu contraseña e inténtalo de nuevo.")
        case .failed(let message):
            errorRow(message)
        case .offline:
            offlineNotice
        default:
            EmptyView()
        }

        Toggle(isOn: $model.keepSignedIn) {
            Text("Mantener la sesión iniciada")
                .font(.system(size: 16))
                .foregroundStyle(KColor.text)
        }
        .tint(KColor.action)
        .frame(minHeight: KMetrics.buttonHeight)
        .disabled(sending)

        PrimaryButton(
            title: sending ? "Ingresando…" : (model.state == .offline ? "Reintentar" : "Ingresar"),
            isLoading: sending,
            height: 52,
            action: submit
        )
        .disabled(!model.canSubmit)

        HStack(spacing: 5) {
            Text("¿Primera vez?")
                .font(KType.body)
                .foregroundStyle(KColor.textSoft)
            Button("Usa tu código de invitación", action: onUseInvitationCode)
                .font(.system(size: 15, weight: .semibold))
                .tint(KColor.action)
        }
        .frame(maxWidth: .infinity, minHeight: KMetrics.buttonHeight)
    }

    private func emailField(invalid: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Correo institucional")
                .font(KType.fieldLabel)
                .foregroundStyle(KColor.textSoft)

            HStack(spacing: 0) {
                TextField(
                    "",
                    text: Binding(get: { model.emailLocalPart }, set: model.setEmail),
                    prompt: Text("pepito.perez").foregroundStyle(KColor.placeholder)
                )
                .textContentType(.username)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.next)
                .focused($focus, equals: .email)
                .onSubmit { focus = .password }
                .font(.system(size: 16))
                .foregroundStyle(KColor.text)

                // Without a fixed size the text field takes the whole row and the domain wraps
                // mid-word: "@konradlorenz.edu.c / o".
                Text(institutionalDomain)
                    .font(.system(size: 16))
                    .foregroundStyle(KColor.domainSuffix)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .layoutPriority(1)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 16)
            .frame(height: KMetrics.fieldHeight)
            .background(fieldBackground(invalid: invalid))
            .contentShape(Rectangle())
            .onTapGesture { focus = .email }
        }
    }

    private func passwordField(invalid: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Contraseña")
                .font(KType.fieldLabel)
                .foregroundStyle(KColor.textSoft)

            HStack(spacing: 0) {
                let binding = Binding(get: { model.password }, set: model.setPassword)
                Group {
                    if model.passwordVisible {
                        TextField("", text: binding)
                    } else {
                        SecureField("", text: binding)
                    }
                }
                .textContentType(.password)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.go)
                .focused($focus, equals: .password)
                .onSubmit(submit)
                .font(.system(size: 16))
                .foregroundStyle(KColor.text)

                Button {
                    model.passwordVisible.toggle()
                } label: {
                    Image(systemName: model.passwordVisible ? "eye.slash" : "eye")
                        .foregroundStyle(KColor.textSoft)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(model.passwordVisible ? "Ocultar la contraseña" : "Mostrar la contraseña")
            }
            .padding(.leading, 16)
            .padding(.trailing, 4)
            .frame(height: KMetrics.fieldHeight)
            .background(fieldBackground(invalid: invalid))
        }
    }

    private func fieldBackground(invalid: Bool) -> some View {
        RoundedRectangle(cornerRadius: KMetrics.fieldRadius, style: .continuous)
            .fill(invalid ? KColor.surface : KColor.background)
            .overlay(
                RoundedRectangle(cornerRadius: KMetrics.fieldRadius, style: .continuous)
                    .stroke(invalid ? KColor.error : KColor.border, lineWidth: invalid ? 1.5 : 1)
            )
    }

    private func errorRow(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.circle")
                .foregroundStyle(KColor.error)
            Text(text)
                .font(.system(size: 14))
                .foregroundStyle(KColor.error)
        }
    }

    private var offlineNotice: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "wifi.slash").foregroundStyle(KColor.error)
            Text(APIError.offline.userMessage)
                .font(.system(size: 14))
                .foregroundStyle(Color(hex: 0x8C1B1D))
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(KColor.error.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: KMetrics.rowRadius, style: .continuous))
    }

    // MARK: - 403

    private func verification(email: String, isResending: Bool) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 8) {
                Label {
                    Text("Confirma tu correo").font(.system(size: 16, weight: .semibold))
                } icon: {
                    Image(systemName: "envelope")
                }
                .foregroundStyle(KColor.brand)
                Text("Te enviamos un enlace a \(email). Ábrelo y vuelve a entrar.")
                    .font(.system(size: 14))
                    .foregroundStyle(KColor.text.opacity(0.8))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(KColor.brand.opacity(0.07))
            .clipShape(RoundedRectangle(cornerRadius: KMetrics.rowRadius, style: .continuous))

            PrimaryButton(title: "Reenviar correo", isLoading: isResending, height: 52) {
                Task { await model.resendVerification() }
            }
            .disabled(isResending)

            anotherAccountLink
        }
    }

    private var anotherAccountLink: some View {
        Button("Usar otra cuenta", action: model.useAnotherAccount)
            .font(.system(size: 15, weight: .medium))
            .tint(KColor.action)
            .frame(maxWidth: .infinity, minHeight: KMetrics.buttonHeight)
    }

    private func submit() {
        guard model.canSubmit else { return }
        focus = nil
        onDismissNotice()
        Task {
            if let token = await model.submit() {
                onSignedIn(token)
            }
        }
    }
}
