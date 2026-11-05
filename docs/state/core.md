# Core State

> Owner: Core. As of 2026-11-05, `develop` at `4d94141`, after #207, #208, #211, #214 and #215 merged.

Core orchestrates. It asks the other sessions for their state, orders the work, reviews every agent pull request
(as a comment titled "Revisión de Docs/Chore"), and tells Brian which ones are ready to merge. Brian merges and
decides. Decisions are recorded in issues or pull requests.

From 2026-11-05 all sessions are local (see the README). Kotlin goes first, and Swift resumes when the Android
app is finished. Core rotates the worker sessions when a purpose is done or they get heavy, and stays the
long-lived session itself (README, "One session, one purpose").

---

## Where things stand (2026-11-05)

Merged on 2026-11-04 and 11-05: #150, #151, #152, #153, #189 (dependencies); #190 (this folder); #191 (demo variant
in CI); #192; #199 (shared map style); #200 and #206 (floor renderer, first parts); #202 (AGP moves only with
Android Studio; the team needs Android Studio 2026.2.2 Rabbit 2); #203 (#155 fix); #205 (demo rooms from the map
seeds); #183 and #193 (EC floor 1, squared). **#155 is closed:** Horario works on the dev server.

Merged later on 2026-11-05: #210 and #214 (session rules: Core is the long-lived session and rotates the
workers; agents open pull requests ready for review, never as drafts, and report to Core); #207 and #208 (the whole
app follows the system's dark mode; **#204 is closed**); #211 (map 3.7.0, `Space.labelPoint`, and EC P1 as the
floor example); #215 (`MAP-STYLE.md` anchors labels at `labelPoint`).

Open work:

- **#195, `labelPoint` in map-service:** #212, approved by Core. It merges once its full backend CI, with the
  Testcontainers tests, is green on `develop`. Backend \| Local reports it, with the opening message for its next
  session (#157). Close #195 by hand after the merge.
- **#196, the rest of Kotlin's floor renderer:** type icons, selecting a space, labels at `labelPoint`. A new
  Kotlin \| Local started on it on 2026-11-05.
- **#213, `verticalCode`:** stairs, lifts and ramps tied across floors, in four pull requests (contract, service,
  data, portal). Backend, after #157.
- **#194, the generic map renderer:** Swift (#197) and the portal (#198) come later.
- **Route animation decided:** a fluorescent line that pulses in the direction of travel (comment on #194).

The Vercel project `kapp` has its builds turned off (Ignored Build Step `exit 0`) since 2026-11-05: nothing in the
repo deploys there since the prototype was removed (#97). Turn it back on for the public prototype (#92).

---

## Waiting on Brian

- **`docs/entrega/segunda-entrega/mapa-base/ec-p1.png`:** decide whether it is regenerated with the new floor 1.
- **Sketches for EC floors 2 to 8**, in `Sketchs/`, keeping each original plan's file name (phase 5, #58).
- **Housekeeping to confirm:** close #158 and #163 to #165 (the delivery of 2026-10-23 is done) and #43 (done by
  #40).
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
  services from what only runs on simulated data. Since 2026-11-05 the timetable is **tested against the dev
  server** (#155), no longer simulated only. Core \| Local added the Mappedin and IMDF references to the draft.
- **References for the thesis proposal's future work** (Brian, 2026-11-04):
  - **Mappedin** (https://www.mappedin.com): the reference for the indoor map, for its flat, clean 2D style and its
    point-to-point wayfinding. MapsIndoors was considered and set aside, because its 3D style is not the goal;
  - **IMDF**, the Indoor Mapping Data Format (OGC Community Standard 20-094, https://docs.ogc.org/cs/20-094/): the
    reference for the map's data model.
  - Cite both where the proposal describes the map and its routes.

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

1. Review the pull requests Backend and Kotlin open, and tell Brian which are ready.
2. With Brian's yes, the housekeeping above: close #43, #158 and #163–#165, then refresh `PROGRESS.md` and the
   "Current State" of `AGENTS.md` from this folder.
3. Keep this folder current after each batch of merges.
4. Rotate a worker session, with Brian's yes, whenever its purpose is done or it gets heavy, as the README says.
   Recommend Core's own rotation only when this session is compacted or very long.
