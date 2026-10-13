# KApp · Mobile (Swift / iOS)

Native iOS client, Swift and SwiftUI. This is the product, not a prototype — see `AGENTS.md`.

## State

**Login**, **Inicio**, **Horario** and **Semáforo** are built and talk to the API. They follow the
same mockups as the Android client —
[`docs/design/mobile/`](../../../../docs/design/mobile/) — and the same rules, which are written
down in `app/frontend/mobile/kotlin/README.md`. Where the two clients differ, the difference is
listed below.

| Screen | Reads | Writes |
|---|---|---|
| Login | — | `POST /auth/login`, `POST /auth/verify/resend` |
| Inicio | `GET /api/users/me`, `GET /api/schedule/me/day`, `GET /api/semaphore/me/summary`, `GET /api/semaphore/me` | — |
| Horario | `GET /api/schedule/me/week`, `GET /api/semaphore/me`, `GET /api/catalog/pensums/{code}` | `POST /api/schedule/me`, `POST /api/schedule/me/enrollments`, `DELETE /api/schedule/me/enrollments/{id}` |
| Semáforo | `GET /api/semaphore/me`, `GET /api/catalog/pensums/{code}`, `GET /api/semaphore/me/plans`, `GET /api/semaphore/me/summary` | `PUT /api/semaphore/me/courses/{code}`, `POST /api/semaphore/me/plans`, `PUT` and `DELETE /api/semaphore/me/plans/{id}/placements/{code}` |
| Mapa, Perfil | — | Placeholders naming their contract. Perfil also signs out. |

Every control on those screens does something:

- **Login** — the four states of `EstadosLogin.dc.html`. A `400` (a password under ten characters)
  reads the same as a `401`, so neither says which field was wrong. A `403` offers to resend the
  verification e-mail. "Usa tu código de invitación" opens a stub, as on Android.
- **Inicio** — the day and the semester load separately, because two services answer them. Every
  link opens its tab: the avatar, the four shortcuts, "Ver semáforo", "Ver horario", "Cómo llegar"
  and the button on each empty state. A class that has started is still the next class, without
  the "Empieza en" badge.
- **Horario** — the week from the server, one day at a time, with previous and next week. A class
  opens a sheet with "Cómo llegar" and "Quitar del horario". "Agregar materia" offers the courses of
  the student's own semáforo — in progress, then available, then retakes — with a weekly slot,
  room and colour. A student with no schedule gets "Armar mi horario", which creates it from the
  programme on their profile.
- **Semáforo** — the pensum as columns of semesters, each course coloured by status. *Blocked* is
  derived, as the contract defines it: pending with a prerequisite not passed. A course opens a
  sheet to change its status (grade and period checked before sending) or plan it for another
  semester, creating the student's plan the first time.

## Running it

```bash
cd app/backend/microservices
docker compose --profile mock up -d          # the Prism mocks, ports 4010-4014

open app/frontend/mobile/swift/KApp.xcodeproj
```

The app talks to the mocks unless told otherwise. To reach the real stack through the gateway, add
launch arguments to the scheme (*Product > Scheme > Edit Scheme > Arguments*):

```
-KAppAPI gateway
-KAppGatewayURL http://192.168.1.20:8080      # only on a physical phone; the simulator uses localhost
```

`-KAppResetSession YES` starts at the login instead of restoring the stored session.

Requires Xcode 16 or newer (the project uses file-system synchronized groups) and an iOS 17
simulator or device. `⌘U` runs the unit tests in `KAppTests/`.

Three things that cost time on the first run:

- **The Xcode licence.** `xcodebuild` refuses to do anything until it is accepted, and
  `xcode-select` has to point at Xcode rather than the Command Line Tools first:
  `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`, then
  `sudo xcodebuild -license accept`.
- **The iOS platform is a separate download** since Xcode 26 (*Settings > Components*). Without it
  the scheme has no simulator to run on.
- **The session needs a signed build.** It is kept in the Keychain, which an unsigned simulator
  build cannot write to. Running from Xcode signs it; a command-line build with
  `CODE_SIGNING_ALLOWED=NO` compiles and runs, but forgets the session on every launch.

## Against the mocks

Prism answers every request with the example in the contract, and stores nothing. The app is built
for the real server, so a few things look odd against the mocks and are not bugs:

- **Changes last until the next reload.** A `200` or `204` means the server now holds exactly what
  was sent, so the screen applies the change on the spot instead of reloading. Pull to refresh, or
  come back to the tab, and the mock's canned answer returns.
- **The week is always 21–27 September.** The arrows send a different date; the mock ignores it.
  The title is read from the answer, so it says so.
- **The summary does not move.** Marking a course as passed updates its card; the 83% comes from
  `GET /api/semaphore/me/summary`, which the mock does not recompute.
- **Prism can hang.** Left running while the Mac sleeps, its process under Rosetta can turn into a
  zombie that Docker cannot stop. Every screen then shows "Sin conexión". Restarting Docker Desktop
  clears it.

## Layout

```
KApp/
├── KAppApp.swift            @main
├── RootView.swift           login or signed in; the shell and the floating bar
├── App/                     AppModel (session), Workspace (tabs), KAppDestination
├── DesignSystem/            colour, type, the band, shared components, display formatting
├── Networking/              environment, client, errors, Keychain session, services, models
├── Support/                 calendar dates without timezones, the academic calendar
└── Features/
    ├── Login/  Invitation/
    ├── Home/                HomeViewModel + HomeView
    ├── Schedule/            ScheduleViewModel, AddCourseViewModel and their views
    ├── Semaphore/           SemaphoreBoard (the rules), the view model, the grid, the course sheet
    └── Placeholder/         Mapa, and Perfil with sign-out
KAppTests/                   the rules above, no network
```

Adding a file needs no change to the `.pbxproj`: both targets use a synchronized group, so two
people can add files in parallel without fighting over the project on every merge. The shared
scheme is under `xcshareddata/` and versioned on purpose — the root `.gitignore` excludes every
`*.xcscheme`, and `app/frontend/mobile/swift/.gitignore` brings this one back.

View models are `@Observable` and live in `Workspace`, not in the views, so switching tabs keeps
what each one had loaded. Each tab reloads when it is selected, which is how a course marked as
passed reaches the card on Inicio without the two screens knowing about each other.

## Colour

Every colour comes from [`docs/K-COLORS.md`](../../../../docs/K-COLORS.md) through
[`Tokens.dc.html`](../../../../docs/design/mobile/Tokens.dc.html), with the names the Android
client uses. The same two rules:

- **Pink `#D51A65` means "you can touch this".** A course colour picked in "Agregar materia" is
  never pink. The server can still send one — the contract's own week example does — and the client
  paints what it is sent.
- **Never white on the green `#C9D329`.** A course in progress is purple on green.

## Differences from the Android client

| | iOS | Android |
|---|---|---|
| Network layer | Built | Next task |
| Semáforo, Horario | Built | Placeholders |
| Session | Keychain, restored on launch | Interface state only |
| "Resto del día" after the last class | "Terminaste las clases de hoy" | Not drawn |

## What is deliberately missing

| Missing | Why |
|---|---|
| Session expiry | The student stays signed in until the server answers `401`, then returns to the login. `auth.openapi.yaml` has no refresh token and `expiresIn` is an hour for everybody — a backend decision, not something the client can fix |
| Registration with an invitation code | `POST /auth/register`. The stub is there so the login's second exit lands somewhere |
| Mapa and Perfil | Not designed yet |
| Dynamic Type | The type scale is fixed to match the mockups and Android. Needs a decision on how it grows |
| Editing an enrollment | `PATCH /api/schedule/me/enrollments/{id}` and the meeting sub-resource. Remove and add again for now |
| Resolving an elective slot | `POST /api/semaphore/me/electives/{code}`. An unresolved slot cannot go on the timetable, since an enrollment needs a course code |
| The academic calendar | No contract publishes it. A hand-entered class spans 27 July to 30 November (second semester) or 26 January to 30 May (first), taken from the contract's examples |
