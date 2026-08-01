# Security Audit — Prototype Review

> Static review of the repository contents and its full git history, performed on `main`. The repository is
> **already public** — it has been since it was created — so nothing here is a pre-publication gate; it is a record
> of what the review found. This document reports findings only; no application code was modified.
>
> Re-audited after the legacy monolith (`app/backend/kapp/`) was deleted from the tree. The scope of this revision is
> the microservices backend, the web client, the database scripts and the orchestration files.

## Context: nothing here is deployed

KApp is in the thesis pre-proposal phase. **No part of this system runs anywhere**: there is no server, no hosted
environment, no user data and no traffic. Everything in the repository has only ever executed on developer machines
against local or throwaway databases.

That is why this document exists in this form. The findings below are not incidents, they are a **written record of
what a review of the current prototype found**, kept so that the planned re-architecture starts from an explicit
list instead of from memory. Severity is stated as "impact if this configuration were deployed as-is" — a
counterfactual, not a description of live risk.

The architecture is expected to be revisited and restructured, with freshly generated credentials throughout. When
that happens, this document is the checklist to design against, and it should be re-run afterwards.

---

## 1. Summary

| Area | Result |
| ---- | ------ |
| Secrets in the working tree | Clean — no credential literal remains after the monolith was deleted. |
| Secrets in git history (76 commits) | **One exposure** — a local development database password and the deleted monolith's hardcoded signing key remain reachable in history (H1). |
| Microservices configuration | Clean — every credential is read from environment variables, no literal values. |
| Build artifacts and OS files tracked | Clean — no `target/`, no `.class`, no `.DS_Store` under version control. |
| Postman collections | Clean — values are `{{token}}` placeholders, no real credentials. |
| Application security posture | **Defects found** — 1 critical, 2 high, 3 moderate, 2 minor issues (section 3). |

Deleting the monolith removed the hardcoded JWT signing key from the working tree. It does **not** remove it from the
git history, and it never removed the historical database password: both stay readable by anyone who clones the
repository once it is public. Neither value ever protected a deployed system — they were local development
credentials — so the practical exposure is limited to credential reuse (H1).

The remaining findings describe the **runtime security posture the prototype would carry into a deployment**. They
are recorded here as design debt for the planned re-architecture, and they are visible to anyone reading the code.

---

## 2. What was verified as clean

Sweeps re-run over the post-deletion tree:

- No credential literal anywhere in `app/` or `scripts/`. Patterns swept: `password|passwd|secret|api_key|
  private_key|credential` followed by a quoted value, plus any hex/base64 literal of 28 characters or more. The only
  hits are Postman collection UUIDs.
- The six `application.properties` files externalize every credential: `${PGUSER:postgres}`, `${PGPASSWORD:}`,
  `${JWT_SECRET}`. No literal values, no embedded connection strings.
- `docker-compose.yml` passes `PGHOST`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` and `PGSSLMODE` through from the host
  environment; nothing is baked into the file.
- No hostname or URL points anywhere outside `localhost` and the container network.
- No `.env` file and no private key was ever committed, across all 76 commits.
- `.gitignore` covers `.env`, `.env.*` (with an explicit `!.env.example` negation), `**/target/`, `.pids/`, `*.log`
  and macOS artifacts.
- The only e-mail addresses present are the club contact `kforge.dev@gmail.com` and fictional
  `@konradlorenz.edu.co` addresses used in sample data.
- The demo mode shipped for static deployments (`app/frontend/web/js/demo.js`) contains fictional people, courses
  and assignments only, and its session token is an inert placeholder string. It never reaches a backend and is
  disabled when the client is served from `localhost`.

---

## 3. Findings

Severity reflects the impact if this configuration were deployed as-is on a reachable network.

| ID | Severity | Finding |
| -- | -------- | ------- |
| H1 | High | Two credentials remain readable in the git history: the deleted monolith's signing key and a former database password. |
| S1 | Critical | Gateway can be bypassed; domain services trust an unauthenticated identity header. |
| S2 | High | No role-based authorization anywhere in the request path. |
| S3 | Moderate | Wildcard CORS configuration. |
| S4 | Moderate | Gateway discovery locator exposes every registered service, and the internal API is publicly routed. |
| S5 | Moderate | Actuator and the Eureka dashboard are open and expose internal topology. |
| S6 | Low | Debug logging and SQL echo enabled in the default configuration. |
| S7 | Low | JWT stored in `localStorage`, no refresh or revocation. |
| S8 | Informational | HS512 key length requirement is undocumented outside this audit. |
| S9 | Informational | Sample data ships a known password and its hash. |

### H1 — Credentials readable in git history (High)

**Evidence** — both values are absent from the current tree and recoverable with `git log -p`:

- A 64-hex-character JWT signing key, hardcoded in the deleted monolith at
  `app/backend/kapp/.../security/jwt/JwtTokenProvider.java`. Removed from the tree by the deletion of the monolith,
  still present in every commit that contained it.
- A plaintext database password (redacted here) that appeared as a commented `spring.datasource.password` entry in
  the monolith's `application.properties`, introduced in commit `14302c1` and removed in `dabb692`
  ("chore: remove hardcoded secrets...").

**Impact.** Deleting a secret from the tree does not delete it from the repository. The repository is public, so
both values are already readable by anyone who clones it. Neither guarded a deployed system — the signing key belonged to a
module that only ever ran locally, and the password pointed at a local database — so the real risk is **credential
reuse**: any value that also unlocks something else, on any machine or account, is what matters here.

**Suggested remediation.** Confirm neither value is in use anywhere today, and change it wherever it is reused.
Rewriting the history with `git filter-repo` is optional: both values are dead and specific to this project, so
leaving the record intact costs nothing and keeps the log honest. The re-architecture should generate fresh secrets
from scratch regardless.

### S1 — Gateway bypass through a trusted identity header (Critical)

**Evidence**

- `app/backend/microservices/docker-compose.yml:46,67,88,111` — publishes `8081:8081`, `8082:8082`, `8083:8083`
  and `8084:8084` to the host, so every domain service is directly reachable, not only the gateway.
- `api-gateway/.../filter/JwtAuthenticationFilter.java:69` — after validating the token, the gateway injects
  `X-User-Email` with the JWT subject.
- `assignment-service/.../ProfessorAssignmentController.java:27`, `.../StudentAssignmentController.java:27`,
  `course-service/.../ProfessorCourseController.java:27` — the services read that header with
  `@RequestHeader("X-User-Email")` and treat it as the authenticated identity.
- Dependency check across the six modules: only `auth-service` and `discovery-server` declare
  `spring-boot-starter-security`. `user-service` pulls `spring-security-crypto` (for `BCryptPasswordEncoder` only);
  `course-service` and `assignment-service` pull neither. No filter chain, therefore no authentication at the
  service boundary.

**Impact.** A caller who can reach a service port directly can send any `X-User-Email` value and act as any user,
without ever presenting a token. The gateway's JWT validation becomes decorative rather than enforcing.

**Suggested remediation.** Do not publish service ports outside the container network (drop the `ports` mappings and
keep the services on `kapp-network`); and make the identity claim verifiable at the service boundary — either
validate the JWT in each service, or have the gateway sign the propagated identity with a secret the services check.

### S2 — No role-based authorization (High)

**Evidence**

- `api-gateway/.../filter/JwtAuthenticationFilter.java` — the filter parses the token and checks the signature and
  expiry. The `roles` claim written by `JwtTokenProvider.generateToken` is never read, and no path-to-role mapping
  exists.
- `assignment-service/.../AssignmentAdminController.java:16` (`/api/admin/assignments`),
  `course-service/.../CourseAdminController.java:16` and `user-service/.../UserAdminController.java:17`
  (both `/api/admin`) — administrative CRUD controllers with no `@PreAuthorize` or equivalent check.
- No module declares `@EnableMethodSecurity`; across the ten controllers in the backend there is not a single
  authorization annotation.

**Impact.** Any authenticated user — a student token is enough — can call administrative endpoints: create, update
and delete assignments, courses, groups, people and employee records. This is vertical privilege escalation.

**Suggested remediation.** Enforce role checks at the edge (map `/api/admin/**` to `ROLE_ADMIN`,
`/api/professor/**` to `ROLE_PROFESSOR`, `/api/student/**` to `ROLE_STUDENT` in the gateway filter) and, defensively,
with method-level security in each service once S1 is addressed.

### S3 — Wildcard CORS (Moderate)

**Evidence**

- `api-gateway/.../config/CorsConfig.java:21,23` — `setAllowedOrigins(List.of("*"))` and
  `setAllowedHeaders(List.of("*"))`, with `GET, POST, PUT, DELETE, OPTIONS, PATCH` allowed.
- `auth-service/.../config/SecurityConfig.java:40,42` — the same wildcard configuration on the login endpoint.

**Impact.** Any origin can drive the API from a victim's browser. Combined with S7 (token in `localStorage`),
a malicious page can use a stolen token against the API from any domain.

**Suggested remediation.** Restrict `allowedOrigins` to the known client origins per environment, and list the
headers actually required instead of `*`. Note that native Android and iOS clients are not subject to CORS, so this
restriction costs nothing once the mobile clients become the primary consumers.

### S4 — Over-exposed routing surface (Moderate)

**Evidence**

- `api-gateway/src/main/resources/application.properties:12` —
  `spring.cloud.gateway.discovery.locator.enabled=true`: every service registered in Eureka becomes routable at
  `/{service-id}/**`, beyond the routes declared in `GatewayConfig`.
- `api-gateway/.../config/GatewayConfig.java:25` routes `/api/users/**` to `user-service`, which includes
  `user-service/.../UserInternalController.java:16` (`/api/users/internal`). Endpoints documented as
  "internal, used by other microservices" are therefore reachable from the public edge, and they resolve a student's
  full name, e-mail, student code and internal id from an e-mail address alone.

**Impact.** The exposed surface is whatever the services implement, not what the architecture intended to publish.
The internal identity-resolution endpoints turn any valid token into an enumeration tool over personal data.

**Suggested remediation.** Disable the discovery locator; move the internal API under a distinct prefix
(for example `/internal/**`) that the gateway does not route, and reach it only through the service network.

### S5 — Actuator and Eureka dashboard exposure (Moderate)

**Evidence**

- `api-gateway/src/main/resources/application.properties:26,27` —
  `management.endpoints.web.exposure.include=health,gateway` and `management.endpoint.health.show-details=always`.
  `/actuator` is also listed as a public path in the gateway filter.
- `discovery-server/.../config/SecurityConfig.java` — `anyRequest().permitAll()`, and
  `docker-compose.yml:14` publishes `8761:8761`. The Eureka dashboard and its registration API are open to anyone
  who can reach the host.

**Impact.** Unauthenticated callers can enumerate gateway routes, read detailed health output, list every registered
service instance and — because registration is unauthenticated — register rogue instances under an existing service
name.

**Suggested remediation.** Set `show-details=when-authorized`, remove `gateway` from the exposed endpoints outside
development, keep the discovery server off the published ports, and require authentication on the Eureka endpoints.

### S6 — Debug logging and SQL echo in default configuration (Low)

**Evidence** — the five data-backed services set `spring.jpa.show-sql=true`, and every module sets `DEBUG` level for
its application package (plus `org.springframework.cloud.gateway` at the gateway).

**Impact.** Query contents and routing decisions land in logs. Acceptable for local development, harmful if the same
defaults are promoted to a shared environment.

**Suggested remediation.** Move these settings to an `application-dev.properties` profile and keep the default
profile at `INFO` with `show-sql=false`.

### S7 — Token storage and lifecycle in the web client (Low)

**Evidence** — `app/frontend/web/js/app.js:8` reads the session from `localStorage`/`sessionStorage`; line 150
attaches it as `Authorization: Bearer`. `auth-service/src/main/resources/application.properties` sets
`jwt.expiration=86400000` (24 h).

**Impact.** Any XSS in the web client yields a token valid for up to 24 hours. There is no refresh token, no
rotation and no server-side revocation: a leaked token stays valid until it expires.

**Suggested remediation.** Shorten the access-token lifetime and add a refresh mechanism. The web client is a
testing surface for the backend, so the durable fix belongs in the Kotlin and Swift clients: store tokens in the
platform keystore (Android Keystore, iOS Keychain), never in a web storage API.

### S8 — HS512 signing key length (Informational)

**Evidence** — `auth-service/.../security/JwtTokenProvider.java` builds the key with `jwtSecret.getBytes()` and
signs with `SignatureAlgorithm.HS512`.

**Note.** JJWT requires at least a 64-byte key for HS512 and throws `WeakKeyException` at runtime otherwise. The
gateway must be configured with the exact same secret. This requirement is documented in
[`.env.example`](../.env.example).

### S9 — Sample data credentials (Informational)

**Evidence** — `app/database/test_data.sql:1-3` documents the shared password `123123` and its BCrypt hash.

**Note.** This is legitimate for a development seed. It must never be loaded into a shared or public environment,
and the file is labeled as development-only in the README.

---

## 4. Architectural notes

Observations from the same review that are not security defects but affect maintainability.

- **Duplicated entities.** `Person`, `Member`, `Student` and `Employee` are defined in both `auth-service` and
  `user-service`. Two services owning the same tables is a blurred domain boundary: a schema change has to be applied
  in two places, and neither service is the clear owner of the identity model.
- **Test coverage.** The six microservices have no tests. Deleting the monolith also removed the only test file that
  existed in the repository, so coverage is now zero. There is no automated regression barrier for the authorization
  work suggested in S1 and S2 — worth adding before touching the security path.
- **Shared database.** All services read and write the same PostgreSQL schema. Documented as a deliberate
  simplification in `DESIGN.md`; it does mean a schema migration is a cross-service event.
- **Frontend modularity.** `app/frontend/web/js/app.js` concentrates 1291 lines covering session handling, routing,
  API access and DOM rendering. Splitting it by feature is worth doing before its design is ported to the Kotlin and
  Swift clients, since that structure is what will be translated.

---

## 5. Checklists

### 5.1 Repository hygiene (the repository is already public)

Nothing here is a deployment concern. These are the housekeeping items for a public source repository. The
governance items — branch protection, secret scanning, push protection, Dependabot and test coverage — are tracked
as an explicit pending list in [PROGRESS.md](PROGRESS.md#pending--repository-governance-and-security).

- [x] Legacy monolith deleted from the tree, removing its hardcoded signing key from `HEAD`.
- [x] Post-deletion secret sweep over the working tree came back clean.
- [x] `docs/researches/` reviewed and committed: the PDF contains article summaries from academic databases
      (ScienceDirect, Taylor & Francis, Scopus) with no e-mail addresses, student IDs or other personal data.
- [x] No personal e-mail addresses exposed: only the club contact `kforge.dev@gmail.com` and fictional
      `@konradlorenz.edu.co` sample addresses.
- [ ] Confirm the two historical credentials (H1) are not reused anywhere that still matters; change them if they
      are. History rewriting is optional and not recommended for values this dead.
- [ ] Enable GitHub secret scanning and push protection, so the next accidental commit is caught at push time.
- [ ] Enable Dependabot alerts (Spring Boot 3.2.0 and Spring Cloud 2023.0.0 both have newer patch releases).
- [ ] Enable branch protection on `main`: required review, no force pushes.
- [ ] Re-run the secret sweep periodically, and before any release:

      git grep -nEi "(password|secret|api[_-]?key|private[_-]?key)[[:space:]]*=[[:space:]]*\"[^\"]{6,}\""
      git grep -nE '"[A-Za-z0-9+/=_-]{28,}"' -- app scripts

      Expected hits: none, beyond the Postman collection UUIDs.

### 5.2 Before this ever runs on a reachable network

Not applicable today — nothing is deployed. This is the gate for the planned re-architecture, in the order the work
should be done.

- [ ] Generate fresh secrets for every environment; never carry a development value forward (H1, S8).
- [ ] Close the gateway bypass: keep service ports off the host and make the propagated identity verifiable at the
      service boundary (S1).
- [ ] Enforce role-based authorization on `/api/admin/**`, `/api/professor/**` and `/api/student/**` (S2).
- [ ] Take the internal API off the public routing surface and disable the discovery locator (S4).
- [ ] Lock down actuator and the Eureka dashboard (S5).
- [ ] Replace wildcard CORS with an explicit origin list per environment (S3).
- [ ] Split configuration into profiles so `DEBUG` logging and SQL echo never reach a shared environment (S6).
- [ ] Shorten token lifetime, add refresh, and store tokens in the platform keystore on mobile (S7).
- [ ] Add tests around the authorization path before changing it — coverage is currently zero.
- [ ] Re-run this audit and update the document.

### 5.3 When the codebase starts carrying real value

Today the repository is a prototype and everything in it is meant to be read. That changes once real business logic
and real data exist. Decisions to make at that point, in order of how much they actually protect:

- **Keep the valuable logic on the server.** Anything shipped to a browser or a phone is, by definition, in the
  hands of whoever receives it. Rules, pricing, validations and access decisions belong behind the API. This is the
  only item on this list that is a real security boundary.
- **Never ship a secret to a client.** No API key, no signing secret, no database credential in web, Kotlin or Swift
  code — obfuscated or not. Extracting a string from a stripped binary is minutes of work.
- **Minify, bundle and strip as part of the build.** This is what large products actually do to their public
  clients: the Angular/Vite build for web, R8 or ProGuard for Android, symbol stripping for iOS releases. It shrinks
  the payload and raises the cost of reading the code — treat it as friction, not as protection, and never publish
  source maps for a release build.
- **Split what is published from what is not.** A public repository can hold the architecture, the interfaces, the
  documentation and the reference client while the proprietary implementation lives in a private repository or a
  private package consumed as a dependency. That is how a project stays presentable without giving away everything,
  and it is the honest version of "hiding the code" — obscurity inside a public repo is not a control.
- **Restrict by license.** Already in place: the repository is source-available under an internal-use license, which
  is what makes publication a reading right rather than a reuse right.

---

## 6. Method

- Repository contents reviewed file by file across `app/backend/microservices`, `app/frontend/web`, `app/database`
  and `scripts/`.
- Configuration reviewed in all six `application.properties` files and `docker-compose.yml`.
- Dependency review of the seven Maven modules, specifically which ones declare a Spring Security starter.
- Authorization review across the ten controllers in the backend.
- Secret sweep executed over the tracked tree and over all 76 commits in history, including a scan for long
  hex/base64 literals, which is what originally surfaced the two credentials now grouped under H1.
- `.gitignore` coverage verified with `git check-ignore -v` for build output and environment files.

> Method note: use POSIX character classes (`[[:space:]]`) in these patterns. The BSD `grep` shipped with macOS does
> not interpret `\s`, and a first pass of this audit using `\s` produced a false negative on both credentials.

Audit date: 2026-07-31. Branch: `main`. Re-audited after the monolith deletion on the same date.
Status at the time of writing: prototype, not deployed anywhere. To be re-run after the planned re-architecture.
