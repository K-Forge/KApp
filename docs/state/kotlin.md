# Kotlin State

> Owner: Kotlin. As of 2026-11-04, `develop` at `a1e4b17`. Built from Kotlin's report of 2026-10-30, plus the
> merges of 2026-11-04.

---

## Built and working in `develop`

The last feature merged was #187 (2026-10-22). Kotlin has no open pull requests and no unmerged branches.

| Screen | What it does | Tested against |
|---|---|---|
| Sign-in | MSAL. FakeSignIn or DevOidc in debug. In the demo build, a "Modo demostración" menu (#180) | Test tenant and the dev server |
| Invitation | Redeems a visitor pass with a 4-field visual check (#173). No visitor session yet | Simulated data |
| Home | Greeting, next class, semester progress, shortcuts by role | Dev server for profile and progress. Next class only simulated |
| Semáforo | The pensum with each course's status. Plans: create, switch, and move courses, even past the last semester (#186). The demo has the real 1015 pensum (#187) | Dev server for the semáforo. Plan editing only simulated |
| Timetable | Day, two days, week and month views (#184), with each class's room | Simulated data. The dev server test is #155 |
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
- **CI:** the Android job runs `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
  (`.github/workflows/ci.yml`). The demo variant is not built or linted in CI.

---

## Next

1. **#155:** prove the timetable on the dev server, signed in as the test student and the test professor. This needs
   Brian's Mac (emulator plus Tailscale), and Brian types the passwords. Once it passes, the timetable moves from
   "simulated" to "tested against the dev server" in the thesis proposal.
2. **The demo variant in CI:** add `:app:assembleDemo :app:lintDemo` to the Android job. **Waiting for Brian's
   approval.** A cloud container has no Android SDK, so CI on the pull request is the verification.
3. **Sign in once on a device** to check MSAL 8.6.0, which came in with #189.
4. **The interactive map**, after the EC floors (#183) and the map mockup. It closes the last item of #132.
   Meanwhile, tick the boxes of #132 that can be checked.

---

## Small debt, no issue yet

- Home shows the initials instead of the photo chosen in Profile.
- The demo timetable has a single sample week.
