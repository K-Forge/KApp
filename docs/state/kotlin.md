# Kotlin State

> Owner: Kotlin. Refreshed by Core on 2026-11-17, `develop` at `1a08d52`. The screens table was checked against the
> code on 2026-11-05; Inicio and Mapa against `develop` on 2026-11-17.
>
> **Since 2026-11-17 Santiago owns the Kotlin client**, working with his own agent session from the issues of the
> Tercera entrega (#234). Start with "Next" below.

---

## Built and working in `develop`

The last feature merged was #200 (2026-11-05), the first part of the floor renderer.

| Screen | What it does | Tested against |
|---|---|---|
| Sign-in | MSAL. FakeSignIn or DevOidc in debug. In the demo build, a "Modo demostración" menu (#180) | Test tenant and the dev server |
| Invitation | Redeems a visitor pass with a 4-field visual check (#173). No visitor session yet | Simulated data |
| Home | Greeting, next class (from `GET /api/schedule/me/day`, in `HomeViewModel`), semester progress, shortcuts by role | Dev server for profile and progress. Next class not checked on the dev server yet (#230) |
| Semáforo | The pensum with each course's status. Plans: create, switch, and move courses, even past the last semester (#186). The demo has the real 1015 pensum (#187) | Dev server for the semáforo. Plan editing only simulated |
| Timetable | Day, two days, week and month views (#184). Tapping a class opens its room from `GET /api/map/spaces/{room}` | Dev server, as the Microsoft-signed-in test student, since #203 fixed #155 (closed 2026-11-05). The test professor not tried yet |
| Profile | Microsoft and SINU data, a profile photo (add, crop, remove; #185), and sign-out | Dev server. The photo stays on the phone, because user 1.x has no upload endpoint |
| Map | A list of the buildings with code, address and levels (#174). Tapping one opens its floors, one chip per level, drawn by the generic floor renderer with the shared style (#196, first part, #200): spaces by category, corridors, doors, labels, pinch, double-tap and +/- zoom, and the system theme on that screen. Floors read again by their `ETag`, the spaces not drawn yet listed under the levels, no label on unidentified spaces or at the screen edge (#206). An icon per `typeCode` at `labelPoint` with the label below, and demo floors with `labelPoint` (#217, merged 2026-11-17). Selecting a space is #219, open (#228) | Demo floors built from map-service's seeds. Not tried on the dev server |

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
  - 164 unit tests on `feature/floor-type-icons`.
- **CI:** the Android job runs `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDemo
  :app:lintDemo` (`.github/workflows/ci.yml`). The demo variant is built and linted since #191.

---

## Next

The Tercera entrega of Desarrollo de Aplicaciones Móviles shows this app working on the real backend: it is
uploaded on Thursday 2026-11-19 and presented on Friday 2026-11-20. Parent issue #234, milestone "Tercera entrega ·
20-11-2026". All of these are Santiago's, in this order:

1. **#228:** finish #219 (select a space on the floor). #217 was squashed into `develop`, so rebase the branch and
   keep only its own three commits.
2. **#229:** one shared debug build that signs in on the dev server, for the testers. Brian shares the dev server's
   machine on Tailscale with Santiago, Iván and Alejandro, limited to the API's port, and registers the build's
   signature hash. The APK goes to the team's WhatsApp group, never to the repository or an issue.
3. **#230:** every screen against the dev server, as the test student and the test professor (the professor has
   never been tried there), and fix what fails. Record the result in this file.
4. **#231:** a room that is not on the map says so instead of "No pudimos buscar el salón en el mapa", and Inicio
   shows the profile photo.
5. **#232:** Iván and Alejandro report on WhatsApp until Thursday 10:00. Triage, fix, freeze at 14:00, and cut the
   delivery build.

After the delivery:

- **#132:** its open task, "a class opens its room on the map", is in the code (`ScheduleViewModel` asks
  `GET /api/map/spaces/{room}?buildingCode=…`). Tick its boxes once #230 has checked it on the dev server.
- **#196:** close it when #219 is merged.
- **#226:** the Gradle 9.9.1 and AGP 9.5.1 bump waits until after the delivery, so nobody's Android Studio re-syncs
  this week.

---

## Small debt, no issue yet

- The demo timetable has a single sample week.
