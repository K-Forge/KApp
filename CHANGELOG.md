# Changelog

What each release of KApp brought, newest first. A release is a merge of `release/<version>` into
`main`, tagged `v<version>` (see [CONTRIBUTING.md](CONTRIBUTING.md), "Releases"). The changes to
each API contract are listed apart, in [docs/api/CHANGELOG.md](docs/api/CHANGELOG.md).

## 0.3.0 (November 2026)

The Tercera entrega of the mobile applications course (DAM), uploaded on 19 November 2026 and presented on
the 20th. The Android app works end to end against the real backend on the dev server. The whole stack
runs on one computer with one command, so an evaluator needs no account of the team's.

### Backend

- **Microsoft sign-in, for the app and the portal.** ID tokens are verified against the tenant's keys
  (#102, #104). The dev server signs people in with Microsoft (#148). The portal signs in with Microsoft
  and renews its session with the refresh token (#141, #142).
- **Roles and permissions.** One profile role plus any number of permissions, set by an administrator
  (#103, #172), with every service tested against them (#119).
  - An account the directory does not classify gets `ROLE_UNASSIGNED`, "Sin rol": the map and its
    profile, nothing academic (#252).
  - A visitor pass, `ROLE_GUEST`, stays a separate type.
- **Plans follow the rules.** A pending course cannot be planned before the student's current level, and
  a failed course goes only to the next period (#253). Plans can go past the last semester (#186).
- **Map.**
  - Floors are cached by ETag and list the spaces not drawn yet (#206).
  - Every drawn space carries a label point (#211, #212).
  - The Edificio Central's first floor is drawn from Brian's sketch and the surveyed plans, then
    corrected (#183, #193). Its auditorium, the floor's outline and the Carrera 9A exit are redrawn
    from the plans; the exit stays provisional until it is measured (#221). The survey sheet asks
    for that exit (#235).
- **The timetable runs on the dev server** without waiting on its neighbours (#156, #203).
- **The admin portal** opens to every permission with the menu each one needs (#169), sends a content
  security policy (#179), and hides the invitation codes page, which goes for good after this release
  (#257).
- **Run it locally.** `scripts/run-locally.sh` and `scripts/run-locally.ps1` start the whole stack with a
  local MongoDB (the `local-db` profile), a fake Microsoft sign-in and secrets generated on the computer
  (#244, ADR 0010). Development itself stays on Atlas and the tenant.

### Android

- **Seven screens:** login, inicio, semáforo, horario (day, week and month, #184), mapa, perfil and the
  visitor pass.
  - Inicio shows the next class and the semester in credits. Without network, it shows the last data
    it loaded and its time (#238, #258).
  - Semáforo has plans, the elective bank even without a plan, failed courses marked "Debe repetirse"
    in the next period, and only the levels a course may go to (#186, #254, #258).
  - Mapa lists the buildings (#174) and draws each floor with the shared map style (#199, #200). Each
    space has an icon (#217) and can be tapped for its name, type and door code (#219).
  - Perfil can change the profile photo (#185).
  - A redeemed visitor pass opens the campus map for 24 hours (#173, #258).
  - An account with no role is told to contact TI (#254).
- **Sign-in.** Microsoft through MSAL, the fake OIDC server in debug builds (#139), a clear message
  when there is no network, and a single sign-in at a time (#254). Sign-out forgets the last person's
  tabs and data (#239).
- **A demo build** that runs with no backend and no account, from the contracts' examples (#171, #180).
- **Dark mode** that follows the system (#207, #208). Targets API 37 (#124, #147).

### Testing

Iván and Alejandro ran 89 cases on the dev server: 76 passed, 4 failed and 9 were blocked. The four
failures were fixed in #254. The blocked cases either need data the test SINU does not have, or rooms
linked to the map (#251).

### Contracts

| Contract | Version |
|---|---|
| `auth.openapi.yaml` | 1.2.0 |
| `user.openapi.yaml` | 1.2.0 |
| `semaphore.openapi.yaml` | 2.0.2 |
| `schedule.openapi.yaml` | 2.0.2 |
| `map.openapi.yaml` | 3.8.0 |

### Repository

- A shared state folder that every agent session reads first (#190, #209, #210), and the rule that
  agents open ready pull requests and report them to Core (#214).
- One pull request per purpose, a squash-only merge queue, and local branches deleted once merged
  (#129, #138, #128).

## 0.2.0 (October 2026)

The state of the code that the thesis proposal cites. KApp stops copying academic data and reads
it from SINU, members sign in with Microsoft, the campus map follows the surveyed plans, and the
Android app reaches its first four screens.

### Backend

- **SINU, read only.** The semáforo, the timetable and a student's program are read from SINU
  and never stored (#78, #79, #80). Until the university opens SINU, a test SINU stands in for
  it. Only the student's own plans are kept.
- **Sign-in.** Microsoft for every member, KApp's own tokens, and refresh tokens that rotate on
  each renewal (#82). Password sign-in is closed unless local sign-in is enabled for development
  (#83).
- **Profiles** no longer hold identity documents (#80).
- **Map.** The campus is drawn from its evacuation plans and the city's map (#35 to #38), every
  building carries its SINU sedes, and clients can cache map reads (#77).
- **Fewer moving parts.** The Eureka registry is gone and services call each other by name (#64,
  #68). The frozen course and assignment services are deleted (#65), and so is the local database:
  Atlas is the only one (#27, ADR 0009).
- **Pensums.** The 23 published pensums are loaded, with the university's course codes (#25, #29).

### Contracts

| Contract | Version |
|---|---|
| `auth.openapi.yaml` | 1.0.0 |
| `user.openapi.yaml` | 1.0.0 |
| `semaphore.openapi.yaml` | 2.0.1 |
| `schedule.openapi.yaml` | 2.0.1 |
| `map.openapi.yaml` | 3.6.0 |

The mocks serve all five on one port, 4000 (#40).

### Android

- Microsoft sign-in, kept across launches (#70).
- Profile, semáforo with plans, and the timetable with the room of each class (#71 to #73).
- Builds against the mocks (#69) and builds the same on every clone (#32).

### Admin portal

- A floor editor for the campus map that works from an iPad (#36).

### Repository

- The contributing rules live in the repository and are checked locally and in CI (#31).
- Stacked pull requests are rebased by a bot after a squash merge (#86, #90, #91).
- Dependabot keeps dependencies patched (#93), and the dev server rebuilds only what changed
  since the last published image (#95).
- The 2025 web prototype, its Vercel demo and the legacy SQL schema are removed (#97).
- SRS 2.0, the roadmap of new services, and every main document brought up to the October
  decisions (#94, #96, #98, #99).

## 0.1.0 (August 2026)

The architectural reference prototype: Spring Boot microservices behind a gateway, the admin
portal, and the 2025 web client. See the
[v0.1.0 release](https://github.com/K-Forge/KApp/releases/tag/v0.1.0).
