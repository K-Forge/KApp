# Core State

> Owner: Core. As of 2026-11-04, `develop` at `a1e4b17`.

Core orchestrates. It asks the other sessions for their state, orders the work, reviews every agent pull request
(as a comment titled "Revisión de Docs/Chore"), and tells Brian which ones are ready to merge. Brian merges and
decides. Decisions are recorded in issues or pull requests.

Since 2026-11-04 there are two Cores. Core \| Local orchestrates the sessions on Brian's Mac, and Core \| Cloud
orchestrates the cloud sessions (Backend \| Cloud and Kotlin \| Cloud, plus Swift \| Cloud later). Kotlin goes first,
and Swift resumes when the Android app is finished.

---

## Open pull requests

| Pull request | Area | State | What it needs |
|---|---|---|---|
| #183 EC floor 1 from Brian's sketch, plus V016 | backend | CI green and mergeable, but **do not merge yet** | Brian's review on 2026-11-04: the "after" render places the patio and many sizes well, but the spaces have irregular shapes, worst next to Carrera 9 Bis. They must be rectilinear ("squared") and still fit reality, following the pencil lines on the sketches. Backend regularizes the shapes and renders them again. See `backend.md` |
| #153 vitest 4 to 5 (Dependabot) | portal | A rebase was requested on 2026-11-04 after #152 merged | Merge once the portal job passes on the rebased branch. `@angular/build` accepts vitest `^4 \|\| ^5` |

Merged on 2026-11-04: #150 (springdoc 2.10.0), #151 (create-github-app-token 3.3.0), #152 (Angular 22.2.4/22.2.5
and other npm patches), and #189 (AGP 9.5.0, Gradle 9.8.2, okhttp 5.5.1, kotlinx-serialization 1.12.0, MSAL 8.6.0).
**After #189, someone should sign in on a device once**, because MSAL is the Microsoft sign-in library.

---

## Waiting on Brian

- **#183:** check the regularized floor 1 when Backend renders it again. After the merge, decide whether
  `docs/entrega/segunda-entrega/mapa-base/ec-p1.png` is regenerated with the new floor.
- **Sketches for EC floors 2 to 8**, in `Sketchs/`, keeping each original plan's file name (phase 5, #58).
- **#155:** sign in on the Android app against the dev server as the test student and the test professor, to prove
  the timetable there. Only Brian types those passwords.
- **Kotlin:** approve adding `:app:assembleDemo :app:lintDemo` to the Android CI job, so that a change cannot break
  the demo build without anyone noticing.
- **Swift git debt:** two old commits on `feature/mobile-swift` use the type `ios:` and carry a tool trailer. Either
  rewrite them, or resume Swift from a clean branch off `develop`.
- **Outside the repository:**
  - with Gabriel Cruz Parra (IT): access to the real SINU, the institutional Entra ID tenant, and the production
    server;
  - the Firebase project and the Apple Developer account, needed by phases 7 and 8;
  - the student code that visitors use, from the reception questionnaire.

---

## Housekeeping for Core

- **Second delivery of 2026-10-23:** Brian confirmed on 2026-11-04 that it was handed in. #158 and its
  sub-issues #163, #164 and #165 can be closed by hand. #163 holds Santiago's requirements review, which is worth
  keeping linked.
- **#43:** the mocks already run on one port (#40), so it is done in practice. Close it.
- **#132:** tick the boxes that can be checked. Its last item waits for the interactive map.
- **Swift sub-issues:** `[Swift]` sub-issues equivalent to the Kotlin delivery issues #176 to #178 do not exist
  yet.
- **Stale docs:**
  - `docs/PROGRESS.md` was last updated on 2026-10-11;
  - the "Current State" section of `AGENTS.md` still describes phase 3 as in progress (it is done; #56 is closed).
  - Refresh both from these files in a docs pull request.
- **Thesis proposal (anteproyecto):** the "current state of the project" draft is on Brian's Mac (see below). Its
  figures date from 2026-10-29: 331 commits, 104 merged pull requests, 5 contracts, about 900 backend, 234 portal
  and 118 Android tests. Refresh them right before it is handed in. Keep separating what was tested against real
  services from what only runs on simulated data.

---

## Only on Brian's Mac

Cloud sessions cannot read these. Ask a local session, or ask Brian to attach the file to an issue.

- **Agent memory of the local sessions**, under the local project's memory folder. The state it held on
  2026-10-29 is now in this folder.
- **`~/Documents/KApp/`:**
  - `anteproyecto/estado-actual-2026-10-29.md`: the draft above;
  - `levantamiento edificios/`: the building survey (`KAPP_SURVEY`). For the Edificio Central, `Originales/` holds
    the plans and `Sketchs/` the pencil sketches (P1 is drawn; P2 to P8 are untouched copies).
    `renders-p1-2026-10-22/` holds the before and after renders of floor 1;
  - `entrega-dam/`: the source of the delivery `.docx`, its diagrams and review captures;
  - `documentos-ti/`: the sources of the IT office documents. The neutral part is in `docs/ti/`.
- **The `.env` files** of the backend worktrees, and the dev server's configuration. Never read or print them.
- **The worktrees** in `../KApp-worktrees/`: `docs`, `backend`, `mobile-kotlin` and `mobile-swift`.

---

## Next for Core

1. Review the pull requests that Backend and Kotlin open from the cloud, and tell Brian which are ready.
2. Merge path for #153 once its rebase is green.
3. The housekeeping above: close #43, #158 and #163–#165, then refresh `PROGRESS.md` and `AGENTS.md`.
4. Keep this folder current after each batch of merges.
