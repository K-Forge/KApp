# KApp · Mobile (Kotlin / Android)

Native Android client, Kotlin and Jetpack Compose. This is the product, not a prototype — see
`AGENTS.md`.

## State

**Login** and **Inicio** are built, as interfaces only. They match
[`LoginAndroid.dc.html`](../../../../docs/design/mobile/LoginAndroid.dc.html) and
[`HomeAndroid.dc.html`](../../../../docs/design/mobile/HomeAndroid.dc.html), and they go nowhere:
there is no network layer, no `INTERNET` permission and no call to anything. Pressing **Ingresar**
with both fields filled navigates to Inicio.

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

Requires JDK 17 or newer and the Android SDK with platform 36. Open the `kotlin/` folder in Android
Studio, or from the command line:

```bash
cd app/frontend/mobile/kotlin

./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:installDebug         # onto a connected device or a running emulator
```

Every `@Preview` renders at 360x800, which is the size the mockups are drawn at, so the two can be
compared side by side without a device. `HomeScreen.kt` has four — the screen and the three states
of `EstadosHome.dc.html` — and `PlaceholderScreen.kt` one.

## Layout

```
app/src/main/java/co/edu/konradlorenz/kapp/
├── MainActivity.kt              edge-to-edge, hosts the NavHost
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
| Retrofit and `POST /auth/login` | Next task. Against the Prism mock on `10.0.2.2:4010` (`docker compose --profile mock up -d`) before the real gateway |
| The four states in `EstadosLogin.dc.html` | Sending, 401, 403 unverified and offline. All four are answers the server gives; there is nothing to render them from yet |
| `POST /auth/verify/resend` | Reached only from the 403 state above |
| Session persistence | "Mantener la sesión iniciada" holds interface state only. `auth.openapi.yaml` has no refresh token and `expiresIn` is one hour for everybody, so there is a backend decision to make first |
| Hilt | It earns its place when there are two implementations to swap, not before |
| A monochrome launcher icon | Themed icons need a single-colour version of the crest, which is a design asset we do not have |
| `GET /api/schedule/me/day` and `GET /api/semaphore/me/summary` | What Inicio is drawn from. `HomeViewModel` already has the two states they fill; what neither contract has a picture for is the failure case, so that is the first thing to design |
| Semáforo, Horario, Mapa and Perfil | Four routes that reach `PlaceholderScreen`. Each is replaced by editing its entry in `KAppNavHost`; nothing else has to move |
| The block a class is in | `ClassOccurrence` carries `room` and `campus`; the mockup prints "Salón 401 · Bloque B". The block comes from map-service or it is a field `schedule.openapi.yaml` grows |
| The number of courses in progress | `ProgressSummary` counts credits, not courses, so "5 materias en curso" needs a second call to `GET /api/semaphore/me` or a new field |

## Versions

Pinned in `gradle/libs.versions.toml`. Android Studio will offer newer ones — take them through the
AGP Upgrade Assistant rather than by hand, so Gradle and the Kotlin compiler move together.
