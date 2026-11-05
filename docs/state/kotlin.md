# Kotlin State

> Owner: Kotlin. As of 2026-11-04, `develop` at `ba7e8ca`. Checked against the code on `develop` on 2026-11-04.

---

## Built and working in `develop`

The last feature merged was #187 (2026-10-22). Kotlin has no open pull requests and no unmerged branches.

| Screen | What it does | Tested against |
|---|---|---|
| Sign-in | MSAL. FakeSignIn or DevOidc in debug. In the demo build, a "Modo demostración" menu (#180) | Test tenant and the dev server |
| Invitation | Redeems a visitor pass with a 4-field visual check (#173). No visitor session yet | Simulated data |
| Home | Greeting, next class, semester progress, shortcuts by role | Dev server for profile and progress. Next class only simulated |
| Semáforo | The pensum with each course's status. Plans: create, switch, and move courses, even past the last semester (#186). The demo has the real 1015 pensum (#187) | Dev server for the semáforo. Plan editing only simulated |
| Timetable | Day, two days, week and month views (#184). Tapping a class opens its room from `GET /api/map/spaces/{room}` | Simulated data. On the dev server it fails today: #155 |
| Profile | Microsoft and SINU data, a profile photo (add, crop, remove; #185), and sign-out | Dev server. The photo stays on the phone, because user 1.x has no upload endpoint |
| Map | A list of the buildings with code, address and levels (#174). Tapping one opens its floors, one chip per level, drawn by the generic floor renderer with the shared style (#196, first part): spaces by category, corridors, doors, labels, pan and zoom, light and dark. No icons, selection, `ETag` cache or list of unplaced spaces yet | Demo floors built from map-service's seeds. Not tried on the dev server |

- **Build variants:**
  - `demo`, the default: no network, `DemoBackend` serves the demo assets by API path, and `DemoClock` is fixed on a
    Monday. It was used for the 2026-10-23 delivery;
  - `debug`: can point at the mocks, at the local backend with fake OIDC, at MSAL with `-Pkapp.msal.*`, or at the
    dev server (`:8080`; the emulator needs `-dns-server 100.100.100.100` for Tailscale);
  - `release`.
- **Stack:**
  - Kotlin 2.4.21, JDK 25, minSdk 26, targetSdk 37;
  - since #189: AGP 9.5.0, Gradle 9.8.2, okhttp 5.5.1, kotlinx-serialization 1.12.0, MSAL 8.6.0;
  - MVVM with Compose (Material 3), Retrofit 3 and OkHttp 5;
  - the session is kept with AES-GCM in the Android Keystore;
  - 118 unit tests.
- **CI:** the Android job runs `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDemo
  :app:lintDemo` (`.github/workflows/ci.yml`). The demo variant is built and linted since #191.

---

## Next

1. **#155 is an open backend defect, not a pending Kotlin test.** On the dev server, `GET /api/schedule/me/day` and
   the week call fail for the Microsoft-signed-in test student with an error that is not the contract's 404, while
   Perfil and Semáforo work. The likely cause is how schedule-service maps that user to a SINU student. Backend
   investigates first. Kotlin's part comes after the fix: sign in on Brian's Mac (emulator plus Tailscale; Brian
   types the passwords) as the test student and the test professor. The app already logs each failed call's HTTP
   status to logcat (`KAppApi`), so that run shows the code. Once it passes, the timetable moves from "simulated"
   to "tested against the dev server" in the thesis proposal.
2. **Sign in once on a device** to check MSAL 8.6.0, which came in with #189.
3. **#132 does not wait for the interactive map.** Its open task, "a class opens its room on the map", is already
   in the code: `ScheduleViewModel` asks `GET /api/map/spaces/{room}?buildingCode=…` and `ScheduleScreen` shows the
   room, and the demo has `BI-301` and `EC-405`. What is left is checking it against the 2.0.0 mock on Brian's Mac,
   including the class with no room on 2026-09-21, and then ticking the boxes.
4. **The interactive map**, after the EC floors (#183) and the map reference Brian is collecting. Keep the Map
   screen design open until Core sends that reference.

---

## Small debt, no issue yet

- Home shows the initials instead of the photo chosen in Profile.
- The demo timetable has a single sample week.
- Two comments in `gradle/libs.versions.toml` still say OkHttp is on the 4.x line; it is 5.5.1 since #189. Fix them
  in the next Gradle or dependency pull request, not on their own.
