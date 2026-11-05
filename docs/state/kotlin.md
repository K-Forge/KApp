# Kotlin State

> Owner: Kotlin. As of 2026-11-05, `develop` at `942d46a`. Checked against the code on `develop` on 2026-11-05.

---

## Built and working in `develop`

The last feature merged was #200 (2026-11-05), the first part of the floor renderer.

| Screen | What it does | Tested against |
|---|---|---|
| Sign-in | MSAL. FakeSignIn or DevOidc in debug. In the demo build, a "Modo demostración" menu (#180) | Test tenant and the dev server |
| Invitation | Redeems a visitor pass with a 4-field visual check (#173). No visitor session yet | Simulated data |
| Home | Greeting, next class, semester progress, shortcuts by role | Dev server for profile and progress. Next class only simulated |
| Semáforo | The pensum with each course's status. Plans: create, switch, and move courses, even past the last semester (#186). The demo has the real 1015 pensum (#187) | Dev server for the semáforo. Plan editing only simulated |
| Timetable | Day, two days, week and month views (#184). Tapping a class opens its room from `GET /api/map/spaces/{room}` | Dev server, as the Microsoft-signed-in test student, since #203 fixed #155 (closed 2026-11-05). The test professor not tried yet |
| Profile | Microsoft and SINU data, a profile photo (add, crop, remove; #185), and sign-out | Dev server. The photo stays on the phone, because user 1.x has no upload endpoint |
| Map | A list of the buildings with code, address and levels (#174). Tapping one opens its floors, one chip per level, drawn by the generic floor renderer with the shared style (#196, first part, #200): spaces by category, corridors, doors, labels, pinch, double-tap and +/- zoom, and the system theme on that screen. Floors read again by their `ETag`, the spaces not drawn yet listed under the levels, no label on unidentified spaces or at the screen edge (#206). No icons or selection yet | Demo floors built from map-service's seeds. Not tried on the dev server |

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
  - 157 unit tests.
- **CI:** the Android job runs `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDemo
  :app:lintDemo` (`.github/workflows/ci.yml`). The demo variant is built and linted since #191.

---

## Next

1. **Sign in on the dev server as the test professor.** #155 is closed: after #203, the test student's day, week
   and full timetable calls all answer 200 on the dev server, and the same run signed in with MSAL 8.6.0 (#189) on
   the emulator. The test professor has not been tried yet (emulator plus Tailscale on Brian's Mac; Brian types
   the passwords).
2. **#132 does not wait for the interactive map.** Its open task, "a class opens its room on the map", is already
   in the code: `ScheduleViewModel` asks `GET /api/map/spaces/{room}?buildingCode=…` and `ScheduleScreen` shows the
   room. The demo's rooms come from map-service's seeds (`EC-405`, `EC-508`); `BI 301` is not on the map, so the demo
   answers `404` for it, as the service does. What is left is checking it against the 2.0.0 mock on Brian's Mac,
   including the class with no room on 2026-09-21, and then ticking the boxes.
3. **The rest of the floor renderer (#196):** an icon per `typeCode` and selecting a space. The `ETag` cache, the
   unplaced list, the label fixes and the back arrow on the left came in #206.
4. **#204, the whole app follows the system's dark mode** (Brian chose it on 2026-11-05; no in-app toggle). #207,
   merged, has the theme, Inicio, Mapa and the bottom bar, with the floor screen's dark scheme from #200 folded into
   the app theme. #208 does Semáforo, Horario, Perfil and the login, and opens in dark mode without a white flash.
   Both were tried on the emulator in light and dark; the screenshots are in
   `app/frontend/mobile/kotlin/docs/screenshots/dark-mode/`. Until #208 merges, those four screens keep some
   light-only colours in dark mode.

---

## Small debt, no issue yet

- Home shows the initials instead of the photo chosen in Profile.
- The demo timetable has a single sample week.
- A room that is not on the map (`404`) shows "No pudimos buscar el salón en el mapa", as if the call had failed.
  It could say the room is not on the map yet, as a building that is not mapped does.
