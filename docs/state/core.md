# Core State

> Owner: Core. As of 2026-11-18, `develop` after #239.

Core orchestrates. It asks the other sessions for their state, orders the work, reviews every agent pull request
(as a comment titled "Revisión de Docs/Chore"), and tells Brian which ones are ready to merge. Brian merges and
decides. Decisions are recorded in issues or pull requests.

From 2026-11-05 all sessions are local (see the README). Kotlin goes first, and Swift resumes when the Android
app is finished. Core rotates the worker sessions when a purpose is done or they get heavy, and stays the
long-lived session itself (README, "One session, one purpose").

---

## Where things stand (2026-11-19, v0.3.0)

**The Tercera entrega is v0.3.0** (parent #234, milestone 2). It is uploaded on Thursday 2026-11-19 and presented
on Friday 2026-11-20. The course asks for the final document, the frontend and backend code, the database and its
scripts, a working integration, evidence and a README. The professor accepted Kotlin for the frontend and Java
Spring Boot for the backend.

**Testing.** Iván and Alejandro ran 89 cases on the dev server: 76 passed, 4 failed and 9 were blocked. The 4
failures are fixed. The blocked ones either need data the test SINU does not have (H-07 to H-09, V-12) or need
rooms linked to the map (H-06, M-03, #251), and M-05 no longer applies. Brian reviewed the fixes on 2026-11-19.

Merged on 2026-11-19:

- **#221:** EC P1 redrawn from the plans, with the floor's outline and the Carrera 9A exit. The exit is
  provisional until Brian's measures 84–94. #218 can close.
- **#252 (#249):** an account the directory does not classify gets `ROLE_UNASSIGNED` ("Sin rol"): the map and its
  profile, nothing academic. The portal shows it and asks for a role. Guest (`ROLE_GUEST`, a visitor pass) stays a
  separate type.
- **#253 (#250):** a plan cannot put a pending course before the student's current level, or a failed course
  anywhere but the next period.
- **#254 and #258 (#232):** the Kotlin fixes from testing:
  - sign-in offline and after a cancel;
  - electives without a plan;
  - "Créditos" on the semester card;
  - plan levels;
  - the visitor code field and its wording;
  - the no-role screens;
  - failed courses placed in the next period on their own;
  - a redeemed pass opening the visitor map;
  - Inicio's last data shown offline.
- **#257 (#255):** the portal's invitation codes page is hidden. The codes are removed for good after the delivery.

**The code goes as one ZIP** of the whole repository, made with `git archive` from the tag `v0.3.0`, with a Spanish
README at its root that walks an evaluator through running everything locally (`local-db`, fake OIDC,
`scripts/run-locally.sh` or `.ps1`, the Android client in Android Studio). The repository stays public and in
English.

Open pull requests: **#226** (Gradle 9.9.1 and AGP 9.5.1), held until after the delivery.

**After the delivery:**

- #242 lists everything Brian approved on 2026-11-18: architecture, CI cost, secrets, gateway resilience,
  observability, the profile photo on the server, a public dev API with a demo database, ways of working,
  releases and housekeeping.
- #241: `KAPP_FIRST_ADMINS` only bootstraps ADMIN.
- #245: the Users page redesign. #246: the fake accounts out of Atlas.
- #248: the gate. The Carrera 9A measures.
- #251: the building in Horario, which needs the rooms linked to the map.
- #255: remove invitation codes. #256: an outline on every floor.
- Then #220 (EC P2) and #213.

The Vercel project `kapp` has its builds turned off (Ignored Build Step `exit 0`) since 2026-11-05: nothing in the
repo deploys there since the prototype was removed (#97). Turn it back on for the public prototype (#92).

---

## Waiting on Brian

- **Developer accounts:** created on 2026-11-18. Brian grants ADMIN to the others by hand in the portal, and takes
  it away from `administrativo.prueba`.
- **Reading the final document** and exporting the PDF for the upload of 2026-11-19.
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
