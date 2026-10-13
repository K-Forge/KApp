import Foundation
import Observation

/// The only domain KApp accounts use.
let institutionalDomain = "@konradlorenz.edu.co"

/// Builds the address the API expects out of whatever the student typed.
///
/// The field prints the domain in grey after the cursor, so the usual input is just the local part
/// ("pepito.perez"). A pasted address keeps its local part and takes the institutional domain, so
/// it is never doubled and never an outside one. An empty field gives an empty string, not a bare
/// domain. Same rule as `institutionalEmail` in the Android client.
func institutionalEmail(_ typed: String) -> String {
    let localPart = localPartOfEmail(typed)
    return localPart.isEmpty ? "" : localPart + institutionalDomain
}

/// Everything before the "@", trimmed.
func localPartOfEmail(_ typed: String) -> String {
    let beforeAt = typed.split(separator: "@", maxSplits: 1, omittingEmptySubsequences: false).first ?? ""
    return beforeAt.trimmingCharacters(in: .whitespacesAndNewlines)
}

/// Where the sign-in stands.
///
/// The failures are distinct on purpose, and each is an answer `POST /auth/login` gives - see
/// `docs/design/mobile/EstadosLogin.dc.html`. A `401` does not say whether the account exists, a
/// `403` is not a credentials problem but a task, and no network is not the student's fault.
enum LoginState: Equatable {
    case idle
    case sending
    /// `400` or `401`. One message for both: a password under ten characters is rejected before
    /// the account is looked up, and telling the two apart would say which one was wrong.
    case invalidCredentials
    /// `403`: the password was right, the address is not confirmed yet.
    case unverified(email: String)
    case resending(email: String)
    case verificationSent(email: String)
    case offline
    case failed(String)
}

@Observable
@MainActor
final class LoginViewModel {

    /// What the student typed, without the domain.
    private(set) var emailLocalPart = ""
    private(set) var password = ""
    var passwordVisible = false

    /// The mockup ships this on. With it on the session goes to the Keychain and survives a restart.
    var keepSignedIn = true

    private(set) var state: LoginState = .idle

    private let auth: AuthService

    init(auth: AuthService) {
        self.auth = auth
    }

    /// The address `POST /auth/login` receives.
    var email: String { institutionalEmail(emailLocalPart) }

    /// Both fields filled. The server is the one that knows whether the credentials are good.
    var canSubmit: Bool {
        !emailLocalPart.isEmpty && !password.isEmpty && state != .sending
    }

    func setEmail(_ value: String) {
        // The domain is painted after the field and cannot be typed into it. Keeping only what
        // comes before an "@" means a pasted address lands correctly instead of showing twice.
        emailLocalPart = localPartOfEmail(value)
        clearFailure()
    }

    func setPassword(_ value: String) {
        password = value
        clearFailure()
    }

    /// Signs in. Returns the token when it worked, so the caller can open the app with it.
    func submit() async -> TokenResponse? {
        guard canSubmit else { return nil }
        state = .sending
        do {
            let token = try await auth.login(email: email, password: password)
            state = .idle
            return token
        } catch let error as APIError {
            switch error {
            case .badRequest, .unauthorized: state = .invalidCredentials
            case .forbidden: state = .unverified(email: email)
            case .offline: state = .offline
            default: state = .failed(error.userMessage)
            }
        } catch {
            state = .failed(APIError.server(status: 0).userMessage)
        }
        return nil
    }

    /// `POST /auth/verify/resend`, from the unverified state.
    func resendVerification() async {
        guard case .unverified(let address) = state else { return }
        state = .resending(email: address)
        do {
            try await auth.resendVerification(email: address)
            state = .verificationSent(email: address)
        } catch let error as APIError {
            state = error == .offline ? .offline : .failed(error.userMessage)
        } catch {
            state = .failed(APIError.server(status: 0).userMessage)
        }
    }

    /// Back to an empty form, from the unverified state.
    func useAnotherAccount() {
        emailLocalPart = ""
        password = ""
        state = .idle
    }

    private func clearFailure() {
        switch state {
        case .invalidCredentials, .offline, .failed: state = .idle
        default: break
        }
    }
}
