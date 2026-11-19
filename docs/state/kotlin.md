# Kotlin State

> Owner: Kotlin. Refreshed by Core on 2026-11-17, `develop` at `1a08d52`. The screens table was checked against the
> code on 2026-11-05; Inicio and Mapa against `develop` on 2026-11-17. Every screen checked on the dev server on
> 2026-11-17 as the test student and the test professor (#230), with #238's branch on the emulator. The fixes from
> testing (#232) checked on the emulator on 2026-11-19 against the dev server and in the demo.
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
| Home | Greeting with the photo picked in Perfil, or the initials without one (#231), next class (from `GET /api/schedule/me/day`, in `HomeViewModel`), semester progress, shortcuts by role | Dev server, student and professor (#230) |
| Semáforo | The pensum with each course's status. Plans: create, switch, and move courses, even past the last semester (#186). The demo has the real 1015 pensum (#187) | Dev server, including creating a plan and moving a course in it (#230) |
| Timetable | Day, two days, week and month views (#184). Tapping a class opens its room from `GET /api/map/spaces/{room}`; a `404` says the room is not on the map yet, and only a network or server failure says the search failed (#231) | Dev server, student and professor (#230). Switching between them on one phone showed the previous person's week until `bugfix/forget-tabs-on-sign-out` |
| Profile | Microsoft and SINU data, a profile photo (add, crop, remove; #185), and sign-out | Dev server. The photo stays on the phone, because user 1.x has no upload endpoint |
| Map | A list of the buildings with code, address and levels (#174). Tapping one opens its floors, one chip per level, drawn by the generic floor renderer with the shared style (#196, first part, #200): spaces by category, corridors, doors, labels, pinch, double-tap and +/- zoom, and the system theme on that screen. Floors read again by their `ETag`, the spaces not drawn yet listed under the levels, no label on unidentified spaces or at the screen edge (#206). An icon per `typeCode` at `labelPoint` with the label below, and demo floors with `labelPoint` (#217, merged 2026-11-17). Tapping a space selects it: outlined in the selection colour, with a card for its name, type and door code; Back or a tap on nothing clears it (#219, rebased for #228) | Dev server: the buildings and the floors of BI, CPC1, EA and EC (#230) |

- **Build variants:**
  - `demo`, the default: no network, `DemoBackend` serves the demo assets by API path, and `DemoClock` is fixed on a
    Monday. It was used for the 2026-10-23 delivery;
  - `debug`: can point at the mocks, at the local backend with fake OIDC, at MSAL with `-Pkapp.msal.*`, or at the
    dev server (`:8080`; the emulator needs `-dns-server 100.100.100.100` for Tailscale, and on Windows the
    tester's tailnet needs a global nameserver with "Override DNS servers" on, or Microsoft's sign-in does not
    resolve);
  - `release`.
- **Stack:**
  - Kotlin 2.4.21, JDK 25, minSdk 26, targetSdk 37;
  - since #189: AGP 9.5.0, Gradle 9.8.2, okhttp 5.5.1, kotlinx-serialization 1.12.0, MSAL 8.6.0;
  - MVVM with Compose (Material 3), Retrofit 3 and OkHttp 5;
  - the session is kept with AES-GCM in the Android Keystore;
  - 178 unit tests on `bugfix/delivery-fixes-232`.
- **CI:** the Android job runs `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDemo
  :app:lintDemo` (`.github/workflows/ci.yml`). The demo variant is built and linted since #191.

---

## Next

The Tercera entrega of Desarrollo de Aplicaciones Móviles shows this app working on the real backend: it is
uploaded on Thursday 2026-11-19 and presented on Friday 2026-11-20. Parent issue #234, milestone "Tercera entrega ·
20-11-2026". All of these are Santiago's, in this order:

1. **#228:** finish #219 (select a space on the floor). #217 was squashed into `develop`, so rebase the branch and
   keep only its own three commits.
2. **#229:** a Spanish guide, posted on WhatsApp today, Tuesday 2026-11-17, so Iván and Alejandro run the debug build from
   `develop` against the dev server on the emulator of their own computers (they have iPhones). Brian shares the
   dev server's machine on Tailscale, limited to the API's port, and registers each tester's signature hash. No
   shared APK: one is built only if the course asks for it (#232).
3. **#230:** every screen against the dev server, as the test student and the test professor (the professor has
   never been tried there), and fix what fails. Record the result in this file. **Checked 2026-11-17**: every
   screen answers on the dev server for both, recorded in the table above. One fix, `bugfix/forget-tabs-on-sign-out`.
4. **#231:** a room that is not on the map says so instead of "No pudimos buscar el salón en el mapa", and Inicio
   shows the profile photo.
5. **#232:** Iván and Alejandro report on WhatsApp until Thursday 10:00. Triage, fix, freeze at 14:00, and cut the
   delivery build. **Fixes on `bugfix/delivery-fixes-232` (2026-11-19)**, each checked on the emulator unless noted:
   - L-05: no network, no Microsoft page: the sign-in says "No pudimos conectar con el servidor" first.
   - L-02: one Microsoft sign-in at a time, and MSAL's `user_cancelled` and `duplicate_command` read as a cancel.
     Not reproduced before the fix; eight fast cancel-and-retry rounds after it showed no error.
   - S-12: an elective slot with no plan shows this semester's bank, read-only.
   - The semester card says "créditos" in each figure, on two lines when they do not fit.
   - A plan offers a pending course only the current semester and later ones, and a failed course only the next
     one (#250 adds the same rules to the API).
   - The visitor code field shows `KL-V-` fixed and puts in the hyphen after the first four characters.
   - The demo assets captured again for #221's EC P1.
   - #249, client side: `ROLE_UNASSIGNED` gets Inicio, Mapa and Perfil, a card on Inicio saying to contact TI,
     and "Sin rol" on Perfil. Unit tests only until the backend sends the role.
   - #250, client side: a failed course reads "Debe repetirse". Not seen on the emulator: the test student has no
     failed course.

After the delivery:

- **#132:** its open task, "a class opens its room on the map", is in the code (`ScheduleViewModel` asks
  `GET /api/map/spaces/{room}?buildingCode=…`). Tick its boxes once #230 has checked it on the dev server.
- **#196:** close it when #219 is merged.
- **#226:** the Gradle 9.9.1 and AGP 9.5.1 bump waits until after the delivery, so nobody's Android Studio re-syncs
  this week.

---

## Small debt, no issue yet

- The demo timetable has a single sample week.
