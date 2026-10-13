import Foundation
import Observation

/// Whether somebody is signed in, and everything that only exists while they are.
@Observable
@MainActor
final class AppModel {

    /// Set while signed in. A new one per sign-in, so a second account never sees the first
    /// account's timetable on its way in.
    private(set) var workspace: Workspace?

    /// Why the student is looking at the login, when it was not their choice: "Tu sesión expiró".
    private(set) var signedOutNotice: String?

    let services: Services
    let environment: APIEnvironment
    private let client: APIClient?
    private let store: SessionStore

    init(client: APIClient?, services: Services, store: SessionStore, environment: APIEnvironment) {
        self.client = client
        self.services = services
        self.store = store
        self.environment = environment

        client?.onUnauthorized = { [weak self] in
            self?.signOut(notice: APIError.unauthorized.userMessage)
        }
        if let session = store.load() {
            start(session)
        }
    }

    /// The app as it runs on a phone: the real client, the Keychain.
    static func live() -> AppModel {
        let environment = APIEnvironment.fromLaunchConfiguration()
        let client = APIClient(environment: environment)
        let services = Services(
            auth: RemoteAuthService(client: client),
            profile: RemoteProfileService(client: client),
            schedule: RemoteScheduleService(client: client),
            semaphore: RemoteSemaphoreService(client: client)
        )
        let store = KeychainSessionStore()
        // `-KAppResetSession YES` in the scheme's arguments starts at the login.
        if UserDefaults.standard.bool(forKey: "KAppResetSession") {
            store.clear()
        }
        return AppModel(client: client, services: services, store: store, environment: environment)
    }

    func signIn(_ token: TokenResponse, email: String, keepSignedIn: Bool) {
        let session = Session(
            accessToken: token.accessToken,
            userId: token.userId,
            roles: token.roles,
            email: email
        )
        // With the switch off the session lives in memory and ends with the process.
        if keepSignedIn {
            store.save(session)
        } else {
            store.clear()
        }
        start(session)
    }

    func signOut(notice: String? = nil) {
        guard workspace != nil else { return }
        store.clear()
        client?.accessToken = nil
        signedOutNotice = notice
        workspace = nil
    }

    func clearSignedOutNotice() {
        signedOutNotice = nil
    }

    private func start(_ session: Session) {
        client?.accessToken = session.accessToken
        signedOutNotice = nil
        workspace = Workspace(session: session, services: services)
    }
}

/// The signed-in app: which tab is showing, and the state of each one.
///
/// The view models live here and not in the views, so moving between tabs keeps what each one had
/// loaded - the same job `saveState` and `restoreState` do in the Android client.
@Observable
@MainActor
final class Workspace {
    let session: Session
    var selectedTab: KAppDestination = .home

    let home: HomeViewModel
    let schedule: ScheduleViewModel
    let semaphore: SemaphoreViewModel
    let profile: ProfileViewModel

    init(session: Session, services: Services, today: @escaping () -> Date = Date.init) {
        self.session = session
        home = HomeViewModel(services: services, email: session.email, now: today)
        schedule = ScheduleViewModel(services: services, now: today)
        semaphore = SemaphoreViewModel(services: services, now: today)
        profile = ProfileViewModel(services: services, email: session.email)
    }

    func open(_ destination: KAppDestination) {
        selectedTab = destination
    }
}
