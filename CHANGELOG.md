# Changelog

What each release of KApp brought, newest first. A release is a merge of `release/<version>` into
`main`, tagged `v<version>` (see [CONTRIBUTING.md](CONTRIBUTING.md), "Releases"). The changes to
each API contract are listed apart, in [docs/api/CHANGELOG.md](docs/api/CHANGELOG.md).

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
