# KApp · Mobile (Kotlin / Android)

Native Android client, Kotlin and Jetpack Compose. This is the product, not a prototype — see
`AGENTS.md`.

## State

**Sign-in and the session** are built (issue #46): **Ingresar con Microsoft** runs Microsoft's
sign-in, sends its ID token to `POST /auth/microsoft`, reads `GET /api/users/me` and opens Inicio
with the tabs of the account's profile role. The session is kept encrypted, renewed before it
expires and on a `401`, and ended with **Cerrar sesión** on Perfil. A saved session opens straight
on Inicio. See "Microsoft sign-in" below.

**Inicio** is built as an interface only: it matches
[`HomeAndroid.dc.html`](../../../../docs/design/mobile/HomeAndroid.dc.html), and nothing it shows
comes from the API yet.

The login keeps the band, crest, stripe and footer of
[`LoginAndroid.dc.html`](../../../../docs/design/mobile/LoginAndroid.dc.html), but not its fields:
the address and password are typed into Microsoft's page, never into KApp's, and "Mantener la
sesión iniciada" is gone because every session now lasts 30 days from its last use. The mockups
still draw the fields; updating them is pending in `docs/design/mobile/`.

Inicio is drawn from a state, not from constants, so the three cases in
[`EstadosHome.dc.html`](../../../../docs/design/mobile/EstadosHome.dc.html) are already there:
loading, a day with no classes on it (`day` → `[]`) and a student who has never built a schedule
(`day` → 404). The day and the semester load separately, because schedule-service and
semaphore-service answer separately — a student with no timetable still has a semáforo. Until
there is a repository, `HomeViewModel` serves `HomeSampleData.kt`, which is the student every
artboard is drawn with.

The bar at the bottom navigates, and so does everything on Inicio that names another screen: the
four shortcuts, the two section links, **Cómo llegar** and the button on each empty state. What
they open is `PlaceholderScreen` — the same band and card as Inicio, with the contract that will
fill that screen printed on it. One placeholder for the four of them: there is nothing to tell
apart yet, and four identical files would only be four files to delete.

The bar shows the tabs of the profile role, as issue #46's table has it: a student sees all five,
a professor has no Semáforo, staff have neither Semáforo nor Horario. Inicio leaves out the cards
of the screens the role does not have. The rule is `destinationsFor` in `KAppDestination.kt`.

The tabs sit side by side rather than stacking. Every move pops back to Inicio first, so the
bar never builds a history of itself and Back from any tab leaves for Inicio instead of retracing
which ones were visited; `saveState` and `restoreState` keep what a tab had on it.

`InvitationScreen` is still a stub, and its link is drawn in debug builds only: invitation codes
are development-only in auth 1.0.0. It and the login are outside the bar: it would offer four
destinations to somebody who has not signed in.

## Running it

Requires the Android SDK with platform 36 and a JDK 25: `gradle/gradle-daemon-jvm.properties`
asks for one and Gradle downloads it when the machine has none. Android Studio's bundled JBR
already is one. Open the `kotlin/` folder in Android Studio - not `kotlin/app/`, which is a
module and not the project - or from the command line:

```bash
cd app/frontend/mobile/kotlin

./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:installDebug         # onto a connected device or a running emulator
```

## Running against the mocks

The app talks to the API through one base URL with the gateway's routes, `BuildConfig.API_BASE_URL`,
set per build type in `app/build.gradle.kts`. Debug points at the Prism mocks on your computer;
release can never point at a mock. Start the mocks from the repository root (Docker only, no JVM,
no database):

```bash
pnpm microservices:mock    # or: docker compose --profile mock up -d, in app/backend/microservices
curl http://localhost:4000/auth/health    # {"status":"UP"}
```

| Where the app runs | Base URL |
|---|---|
| Android emulator | `http://10.0.2.2:4000/`, the default in debug |
| Physical phone on the same Wi-Fi | `http://<your computer's LAN IP>:4000/`: change the debug field and add the IP to the network security config, without committing either |

The mocks speak plain HTTP. Only the debug build allows it, and only for `10.0.2.2` and
`localhost`: `src/debug/res/xml/network_security_config.xml`, merged by `src/debug/AndroidManifest.xml`.

A debug build with no Microsoft tenant configured (see below) signs in with a fake ID token, which
the mocks accept, so pressing **Ingresar con Microsoft** goes straight in. Under the button it
offers **Perfil de prueba en los mocks**: Estudiante, Profesor or Administrativo picks the named
example `GET /api/users/me` answers (`Prefer: example=student`, `professor` or `staffAdmin`), and
with it the tabs. The token is a student's whatever is picked; only the profile changes.

How the mocks answer, the `Prefer` header for other states, and troubleshooting:
[`app/backend/microservices/mock/README.md`](../../../backend/microservices/mock/README.md).

## Microsoft sign-in

Through MSAL, in single-account mode ([ADR 0003](../../../../docs/adr/0003-oidc-over-saml-for-mobile-authentication.md)).
It needs three values from the university's app registration (issue #62), read from Gradle
properties so none of them is committed. Put them in `~/.gradle/gradle.properties`:

```properties
kapp.msal.clientId=<application (client) id>
kapp.msal.tenantId=<directory (tenant) id>
kapp.msal.signatureHash=<base64 SHA-1 of the signing certificate>
```

The signature hash is per signing key, so every developer's debug keystore needs its own entry in
the registration's Android redirect URIs, `msauth://co.edu.konradlorenz.kapp/<hash>`:

```bash
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android \
  | openssl sha1 -binary | openssl base64
```

| Build | No client id | With a client id |
|---|---|---|
| Debug | Fake ID token, accepted by the mocks only | Microsoft's sign-in |
| Release | "todavía no tiene configurado el ingreso con Microsoft" | Microsoft's sign-in |

The session, in `data/session/`:

- **Stored** encrypted with AES-256-GCM under a key that never leaves the Android Keystore
  (`KeystoreSessionStore`), and excluded from backups: a copy restored elsewhere could not be
  decrypted anyway.
- **Renewed** two minutes before the access token expires, and once on a `401`. The refresh token
  rotates and reusing one revokes its whole family, so renewals are serialised, and a request that
  waited behind one uses the token it produced instead of renewing again (`SessionManager`).
- **Ended** when a renewal answers `401` or `403`: the app goes back to the login. With no network
  the session survives and the old token is tried.
- **Signed out** with `POST /auth/logout`, then both tokens are forgotten even if the server could
  not be reached.

## Previews

Every `@Preview` renders at 360x800, which is the size the mockups are drawn at, so the two can be
compared side by side without a device. `HomeScreen.kt` has four — the screen and the three states
of `EstadosHome.dc.html` — `LoginScreen.kt` two, as a release build and as a debug build with an
error showing, and `PlaceholderScreen.kt` one.

## Layout

```
app/src/main/java/co/edu/konradlorenz/kapp/
├── MainActivity.kt              edge-to-edge, hosts the NavHost
├── KAppApplication.kt           AppContainer: the one session, the one API client, the sign-in
├── data/
│   ├── network/                 KAppApi (Retrofit), the contract models
│   ├── auth/                    Microsoft's sign-in: MSAL, or the fake in debug
│   └── session/                 SessionManager, its encrypted store, the profile role
└── ui/
    ├── theme/                   the palette, the type scale, the Material scheme
    ├── common/                  the brand band, shared by every screen inside the bar
    ├── navigation/              seven routes: the five tabs, the login and the invitation
    ├── login/                   LoginScreen + LoginViewModel
    ├── home/                    HomeScreen + HomeViewModel + HomeUiState + HomeSampleData
    ├── placeholder/             the four tabs that are not built yet
    └── invitation/              stub
```

`KAppDestination` is the one list of the five tabs — route, icon, label and colour — and
`destinationsFor` picks a role's tabs out of it. The bar and Inicio's shortcuts both draw that
pick, so a destination cannot be added to one and forgotten in the other.

## Colour

Every colour comes from [`docs/K-COLORS.md`](../../../../docs/K-COLORS.md) through
[`Tokens.dc.html`](../../../../docs/design/mobile/Tokens.dc.html), and `ui/theme/Color.kt` uses the
names that sheet assigns. Two rules worth not rediscovering:

- **Pink `#D51A65` means "you can touch this".** Buttons, links, the active tab. Nothing else.
- **Never white on the green `#C9D329`** — 1.5:1, it disappears in sunlight. Purple goes on green.

## What is deliberately missing

| Missing | Why |
|---|---|
| A tenant to sign in against | The university's app registration is issue #62. Until it exists a debug build signs in with the fake |
| The login's mockup with Microsoft | The mockups still draw the address and password. The button and its error line follow the mockup's sizes and colours, and the copy of `EstadosLogin.dc.html` where it still applies |
| `POST /auth/verify/resend` | E-mail verification is development-only in auth 1.0.0; Microsoft accounts need none |
| Visitor passes | `ROLE_GUEST` and `POST /auth/visitor-passes/{code}/redeem`. No issue asks for them in the app yet |
| Hilt | It earns its place when there are two implementations to swap, not before |
| A monochrome launcher icon | Themed icons need a single-colour version of the crest, which is a design asset we do not have |
| `GET /api/schedule/me/day` and `GET /api/semaphore/me/summary` | What Inicio is drawn from. `HomeViewModel` already has the two states they fill; what neither contract has a picture for is the failure case, so that is the first thing to design |
| Semáforo, Horario, Mapa and Perfil | Four routes that reach `PlaceholderScreen`; Perfil's carries **Cerrar sesión** until #47 builds it. Each is replaced by editing its entry in `KAppNavHost`; nothing else has to move |
| The block a class is in | `ClassOccurrence` carries `room` and `campus`; the mockup prints "Salón 401 · Bloque B". The block comes from map-service or it is a field `schedule.openapi.yaml` grows |
| The number of courses in progress | `ProgressSummary` counts credits, not courses, so "5 materias en curso" needs a second call to `GET /api/semaphore/me` or a new field |

## Versions

Pinned in `gradle/libs.versions.toml`, and Gradle itself in `gradle/wrapper/`, with the checksum of
the distribution next to it so every clone runs the same build.

Android Studio will offer newer versions — take them through the AGP Upgrade Assistant rather than
by hand, so Gradle and the Kotlin compiler move together. Then read the diff: the assistant also
writes a dozen `android.*` flags into `gradle.properties` that pin the behaviour of the previous
AGP. They are deprecated the moment they are written and AGP 10 removes them, so take them out and
fix what actually breaks. That is the whole point of upgrading.

There is no Kotlin Android plugin in the build: AGP 9 compiles Kotlin itself.
