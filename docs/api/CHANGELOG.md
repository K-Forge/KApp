# API contracts: changelog

What changed in each contract under `docs/api/`, newest first. A version that breaks a client also
gets its own GitHub issue, labelled `contract`, with the migration for each client.

The contracts are the source of truth: the mocks serve them as soon as they merge
(`app/backend/microservices/mock/README.md`), and the services implement them in the pull requests
that follow. Until a service has caught up, its contract describes where it is going, not where it is.

## October 2026: semaphore 2.0.1 (additive)

- **New:** `GET /api/catalog/pensums` lists every pensum without its items (`PensumSummary`),
  for pickers and listings. The service always had it, and the portal reads it; now the
  contract says so. Any authenticated role except `ROLE_GUEST`, like every catalog read.

Nothing to migrate.

## October 2026: the 2.0 set

KApp stops being an academic record the student edits and becomes a reader of SINU, the
university's academic system. Members sign in with Microsoft. Four contracts break, once and
together, before the mobile clients connect; the map grows without breaking.

| Contract | From | To | Breaks |
|---|---|---|---|
| `semaphore.openapi.yaml` | 1.0.0 | 2.0.0 | Yes |
| `schedule.openapi.yaml` | 1.0.0 | 2.0.0 | Yes |
| `auth.openapi.yaml` | 0.3.0 | 1.0.0 | Yes |
| `user.openapi.yaml` | 0.2.0 | 1.0.0 | Yes |
| `map.openapi.yaml` | 3.5.0 | 3.6.0 | No |

### semaphore 2.0.0

The semáforo is read from SINU and is read-only, except for the student's plans.

- **Removed:**
  - `PUT /api/semaphore/me/courses/{code}`: statuses come from SINU;
  - `POST` and `DELETE /api/semaphore/me/electives/{pensumItemCode}`: SINU says which course filled
    a slot already taken, and a plan says which one is meant for a slot ahead;
  - `GET /api/semaphore/{userId}`: nobody reads another student's semáforo, administrators
    included;
  - every catalog write except the import: `POST /api/catalog/programs`, `PUT` and `DELETE`
    `/api/catalog/programs/{programCode}`, `POST /api/catalog/pensums`, `PUT` and `DELETE`
    `/api/catalog/pensums/{pensumCode}`. The catalog is SINU's; the import stays as its backup.
- **Identifiers:** `code` is gone from every item. `pensumItemCode` addresses an item everywhere,
  fixed course or elective slot, and is never shown; `sinuCode` is the code a client shows, and
  is `null` where the real one is not known. `prerequisites` hold `pensumItemCode`s. The placement
  path is now `/api/semaphore/me/plans/{planId}/placements/{pensumItemCode}`.
- **Statuses:** `POSTPONED` (*aplazada*) joins `PASSED`, `IN_PROGRESS`, `PENDING` and `FAILED`.
  Every entry carries `sinuStatus`, the status as SINU writes it.
- **Grades** are optional and read-only: `null` unless SINU provides one.
- **`GET /api/semaphore/me`** returns `StudentSemaphore`:
  - with `source` (`SINU`, or `TEST` for the invented data served while SINU is not open) and
    `readAt`;
  - without `studentCode`, `updatedAt` or the `reconciliation` block.
  - Entries replace `resolvedCode` with `resolvedSinuCode`.
- **New:** `GET /api/catalog/pensums/{pensumCode}/electives?period=` lists the semester's elective
  bank.
- **Plans:** a placement may carry `electiveSinuCode` for an elective slot, and comes back with
  `electiveOffered`.
- `ROLE_STAFF` reads the catalog like `ROLE_PROFESSOR`, and nothing else here.

**Migrating a client:**
- remove marking a course's status or grade, and resolving an elective by hand;
- address items by `pensumItemCode` and show `sinuCode`, or the name when it is `null`;
- paint `POSTPONED`;
- show that the data is not real when `source` is `TEST`.

### schedule 2.0.0

The timetable is read from SINU and is read-only. Choosing courses happens in SINU.

- **Removed:**
  - `POST` and `DELETE /api/schedule/me`, and every route under `/api/schedule/me/enrollments`:
    nothing is written;
  - `GET /api/schedule/{userId}`;
  - the `409` overlap rule, with nothing written there is nothing to refuse.
- **Renamed:** `Enrollment` is now `Section`, under `sections`, and `enrollmentId` is `sectionCode`,
  SINU's own identifier of a course and group.
- **Identifiers:** `courseCode` is now `sinuCode`, and `pensumItemCode` may be `null`.
- **Meetings:** they lose `meetingId` and gain `blocks`, the number of 45-minute blocks. SINU's
  hours are those blocks.
- **Buildings:** `campus` is now `sede`, as SINU writes it, plus `buildingCode`, the KApp building
  it maps to. A class opens its room with `GET /api/map/spaces/{room}?buildingCode=…`; the same
  room number exists in more than one building.
- **The schedule:** it carries `source` and `readAt`. It loses its `id`, and `programCode`,
  `pensumCode` and `level` are `null` on a professor's timetable.
- **Who:** `ROLE_STUDENT` sees the sections they take and `ROLE_PROFESSOR` the ones they teach.
  `ROLE_STAFF` and `ROLE_ADMIN` alone get `403`. `SchedulePeriodSummary.enrollmentCount` is
  `sectionCount`.

**Migrating a client:**
- remove building a timetable, adding and removing courses, and the overlap error;
- read `sections`;
- open a room with `buildingCode` and `room`.

### auth 1.0.0

Members sign in with Microsoft; KApp keeps issuing its own tokens.

- **New:**
  - `POST /auth/microsoft` takes the ID token Microsoft returns at the end of its sign-in (OIDC,
    authorization code with PKCE), and `client`: `APP` or `PORTAL`;
  - `POST /auth/refresh` renews with a refresh token that rotates; reusing one revokes its family;
  - `POST /auth/logout` revokes it;
  - `PUT /auth/admin/accounts/{userId}/roles` sets an account's profile role and permissions.
- **Tokens:** `TokenResponse` gains `refreshToken` and `refreshExpiresIn`, both `null` for a
  visitor. The access token still lasts an hour, and the client renews it before then. A session
  lasts 30 days from its last use in the apps, a working day in the portal.
- **Roles** are a profile role, `ROLE_STUDENT`, `ROLE_PROFESSOR` or `ROLE_STAFF` (new), plus any
  permissions: `ROLE_ADMIN`, `ROLE_RECEPTION`, `ROLE_MAINTENANCE`, `ROLE_MODERATION` and
  `ROLE_WELLBEING`. A token may carry several, for example `[ROLE_STAFF, ROLE_ADMIN]`.
- **Development only:** password sign-in, registration, verification, invitation codes and
  temporary passwords answer `404` unless local sign-in is enabled. Their shapes change only
  where roles did: `AccountRequest.role` is `profileRole`, `TemporaryPassword.role` is `roles`,
  and registration and accounts take no `studentCode` or `programCode`.
- **Visitor passes:** issued and read with `ROLE_RECEPTION` as well as `ROLE_ADMIN`.

**Migrating a client:**
- sign in with Microsoft (MSAL or AppAuth) and send the ID token to `/auth/microsoft`;
- keep the refresh token in the Keychain on iOS, or in encrypted storage on Android, and call
  `/auth/refresh` before `expiresIn` runs out;
- call `/auth/logout` on sign-out;
- decide the tabs by the profile role in `roles`. Against the mocks, any ID token signs in.

### user 1.0.0

- **Removed:** `identification`, `phone` and `studentCode`. KApp stores no identity document, phone
  number or student code.
- **Roles:** `role` is now `roles`, a list.
- **Academic block:** `academic` carries `programCode`, `programName`, `pensumCode` and
  `currentLevel`, read from SINU, for a student only.
- **Editing:** `PATCH /api/users/me` takes `avatarUrl` and nothing else. Names come from Microsoft
  at sign-in.
- **The directory** (`GET /api/users`, `GET /api/users/{userId}`, the status change) returns
  `DirectoryEntry`, with nothing academic.
- **Internal:** `POST /internal/users` takes `roles` and no `academic`.

**Migrating a client:**
- drop the document and the phone;
- read `roles` as a list;
- edit only the avatar.

### map 3.6.0 (additive)

- `Building.sinuSedes`: the names SINU gives the building as a sede.
- `ETag` on the reads of buildings, a building, a floor and the ground. Send it back in
  `If-None-Match` to get `304` while nothing changed.
- `ROLE_STAFF` reads the map like every other member.

Nothing to migrate. Caching with `ETag` is optional and saves data.
