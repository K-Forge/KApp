# KApp · Mobile (Swift / iOS)

Native iOS client, Swift and SwiftUI, iOS 17 and later. The Android client in
[`../kotlin/`](../kotlin/) is the reference for behaviour: where this one has not caught up yet, the
Android client is what it should do.

## State

The foundation: the Xcode project, the design system, the API client and the floating bar with a
placeholder behind each tab. The screens arrive one contract at a time, in this order:

| Issue | Contract | What it brings |
|---|---|---|
| #137 | user 1.2.0 | Profile roles and the tabs each one sees, Inicio and Perfil from `GET /api/users/me` |
| #135 | auth 1.2.0 | Microsoft sign-in, and renewing the session with rotating refresh tokens |
| #131 | semaphore 2.0.2 | The read-only semáforo from SINU, and plans |
| #133 | schedule 2.0.2 | The read-only timetable from SINU |
| #197 | map 3.8.0 | Any floor from the map API, with `docs/design/map-style.json` |

## Layout

| Folder | Holds |
|---|---|
| `KApp/App/` | The destinations behind the bar |
| `KApp/DesignSystem/` | `KColor`, `KType`, `KMetrics`, the band, buttons and cards. The same values as the Android `ui/theme/`, so the two can be read side by side |
| `KApp/Networking/` | `APIClient`, `APIError` and `APIEnvironment` |
| `KApp/Features/` | One folder per screen |
| `KAppTests/` | Unit tests |

The targets use file-system synchronized groups: adding a file never touches `project.pbxproj`.

## Running it

Start the Prism mocks of every contract from the repository root (Docker is the only requirement):

```bash
pnpm microservices:mock
```

then open the project and run the `KApp` scheme on an iOS simulator:

```bash
open app/frontend/mobile/swift/KApp.xcodeproj
```

The app talks to the mocks on `http://localhost:4000` unless told otherwise. Launch arguments
(*Product > Scheme > Edit Scheme > Arguments*) point it somewhere else, with no rebuild:

| Argument | Effect |
|---|---|
| `-KAppAPI local` | The gateway of the local stack, `http://localhost:8080` |
| `-KAppBaseURL http://192.168.1.20:4000` | Another host for either one: a physical phone does not share the Mac's `localhost` |

From the command line, the tests run with:

```bash
xcodebuild test -project app/frontend/mobile/swift/KApp.xcodeproj -scheme KApp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

Requires Xcode 16 or newer, for the synchronized groups. Since Xcode 26 the iOS platform is a
separate download (*Settings > Components*): without it the scheme has no simulator to run on.
