# KApp · Mobile (Kotlin / Android)

Native Android client, Kotlin and Jetpack Compose. This is the product, not a prototype — see
`AGENTS.md`.

## State

**Login** and **Inicio** are built, as interfaces only. They match
[`LoginAndroid.dc.html`](../../../../docs/design/mobile/LoginAndroid.dc.html) and
[`HomeAndroid.dc.html`](../../../../docs/design/mobile/HomeAndroid.dc.html), and they go nowhere:
nothing they show comes from the API yet. Pressing **Ingresar** with both fields filled navigates
to Inicio; in a debug build it first checks the connection to the API (see "Running against the
mocks").

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

The five tabs sit side by side rather than stacking. Every move pops back to Inicio first, so the
bar never builds a history of itself and Back from any tab leaves for Inicio instead of retracing
which ones were visited; `saveState` and `restoreState` keep what a tab had on it.

`InvitationScreen` is still a stub, so the login's second exit has somewhere to land. It and the
login are outside the bar: it would offer four destinations to somebody who has not signed in.

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

To check the whole path from a device, press **Ingresar** in a debug build and read Logcat under
the tag `KApp.api`. The build calls `GET /auth/health`, signs in with `POST /auth/microsoft` using
a fake ID token (the mocks accept any of 20 characters or more) and calls `GET /api/users/me` with
the token it got back:

```
auth UP, signed in as [ROLE_STUDENT], /api/users/me is Pepito Perez Gomez
```

It lets you in whatever the outcome, so the app still opens with the mocks stopped. How the mocks
answer, the `Prefer` header for other states, and troubleshooting:
[`app/backend/microservices/mock/README.md`](../../../backend/microservices/mock/README.md).

## Previews

Every `@Preview` renders at 360x800, which is the size the mockups are drawn at, so the two can be
compared side by side without a device. `HomeScreen.kt` has four — the screen and the three states
of `EstadosHome.dc.html` — and `PlaceholderScreen.kt` one.

## Layout

```
app/src/main/java/co/edu/konradlorenz/kapp/
├── MainActivity.kt              edge-to-edge, hosts the NavHost
├── data/
│   └── network/                 KAppApi (Retrofit), the contract models, the debug connection check
└── ui/
    ├── theme/                   the palette, the type scale, the Material scheme
    ├── common/                  the brand band, shared by every screen inside the bar
    ├── navigation/              seven routes: the five tabs, the login and the invitation
    ├── login/                   LoginScreen + LoginViewModel
    ├── home/                    HomeScreen + HomeViewModel + HomeUiState + HomeSampleData
    ├── placeholder/             the four tabs that are not built yet
    └── invitation/              stub
```

`KAppDestination` is the one list of the five tabs — route, icon, label and colour. The bar
iterates it and Inicio's shortcuts pick four entries out of it, so a destination cannot be added
to one and forgotten in the other.

## Colour

Every colour comes from [`docs/K-COLORS.md`](../../../../docs/K-COLORS.md) through
[`Tokens.dc.html`](../../../../docs/design/mobile/Tokens.dc.html), and `ui/theme/Color.kt` uses the
names that sheet assigns. Two rules worth not rediscovering:

- **Pink `#D51A65` means "you can touch this".** Buttons, links, the active tab. Nothing else.
- **Never white on the green `#C9D329`** — 1.5:1, it disappears in sunlight. Purple goes on green.

## What is deliberately missing

| Missing | Why |
|---|---|
| Microsoft sign-in and keeping the session | Issue #46. The debug connection check signs in with a fake ID token and keeps nothing |
| The four states in `EstadosLogin.dc.html` | Sending, 401, 403 unverified and offline. All four are answers the server gives; there is nothing to render them from yet |
| `POST /auth/verify/resend` | Reached only from the 403 state above |
| Session persistence | "Mantener la sesión iniciada" holds interface state only. auth 1.0.0 now has refresh tokens; storing and renewing them is issue #46 |
| Hilt | It earns its place when there are two implementations to swap, not before |
| A monochrome launcher icon | Themed icons need a single-colour version of the crest, which is a design asset we do not have |
| `GET /api/schedule/me/day` and `GET /api/semaphore/me/summary` | What Inicio is drawn from. `HomeViewModel` already has the two states they fill; what neither contract has a picture for is the failure case, so that is the first thing to design |
| Semáforo, Horario, Mapa and Perfil | Four routes that reach `PlaceholderScreen`. Each is replaced by editing its entry in `KAppNavHost`; nothing else has to move |
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
