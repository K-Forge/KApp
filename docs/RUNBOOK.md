# Runbook

How to start, stop and troubleshoot KApp locally. Organised by what you are trying to do.

Everything runs in Docker. You almost never need all of it at once — see [Profiles](#profiles).

> **Setting up for the first time?** Start with [`ONBOARDING.md`](ONBOARDING.md) — it walks through
> a fresh machine in ten minutes. This document is the reference you come back to.

---

## Prerequisites

| Tool           | Version        | Required?                                                              |
| -------------- | -------------- | ---------------------------------------------------------------------- |
| Docker Desktop | 4.x+           | **Yes.** Must be *running*, not just installed. Everything runs in it.  |
| pnpm           | 10+, Corepack  | **Yes.** `pnpm run microservices:*` is how the stack is controlled.     |
| Node           | 22+            | **Yes**, because pnpm needs it. Nothing else here does.                 |
| JDK            | 21             | Only to build or run the tests outside Docker.                          |
| Maven          | —              | Never install one. The repository ships `./mvnw`.                       |

**Windows and macOS both work, and with the same commands.** The `microservices:*` scripts are
single `docker compose` invocations with no shell syntax in them — no `cd`, no `&&`, no variables —
so it makes no difference that pnpm runs scripts through `cmd.exe` on Windows and `sh` everywhere
else. On Windows, run them from PowerShell or cmd with Docker Desktop started and WSL2 enabled,
which is Docker Desktop's own default.

There is **no database to install.** Compose starts MongoDB, and the tests start their own through
Testcontainers.

```bash
corepack enable                 # ships with Node; gives you the pinned pnpm
pnpm install                    # repository tooling
```

The repository pins the JDK with `.java-version` (jenv) in `app/backend/microservices/`. If `java`
is not found there, `jenv` is not picking it up:

```bash
jenv add /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

---

## Profiles

Seven JVMs is roughly 3.5 GB. You rarely need all of them, and on a laptop also running Xcode or
Android Studio you actively do not want them.

**There is no database in any of these profiles.** Every service talks to the shared Atlas
cluster; see [ADR 0009](adr/0009-atlas-is-the-only-development-database.md) and
[Pointing at Atlas](#pointing-at-atlas).

| Profile    | What starts                        | RAM     | Use it when                                       |
| ---------- | ---------------------------------- | ------- | ------------------------------------------------- |
| `mock`     | one Prism mock server per contract | ~200 MB | Building a mobile screen against the API contract |
| `core`     | discovery, gateway, auth, user     | ~1.5 GB | Working on sign-in or profiles                    |
| `academic` | core + semaphore, schedule         | ~2.5 GB | Working on pensums or timetables                  |
| `map`      | core + map                         | ~2 GB   | Working on the campus map                         |
| `full`     | everything                         | ~4 GB   | End-to-end checks before a merge                  |
| `dev`      | the admin portal                   | ~50 MB  | Any time you want the web console                 |

Profiles combine. The portal on its own is not much use, so pair it with a backend:

```bash
docker compose --profile core --profile dev up -d
```

---

## Stacks: one per worktree

Everybody works in their own worktree, and more than one stack ends up running on the same
laptop. `KAPP_STACK` is what keeps them apart: it names every container and every image tag. The
Compose project stays `kapp`, so everything still shows up under one heading in Docker Desktop.
Set it once, in `app/backend/microservices/.env` of that worktree:

```bash
echo 'KAPP_STACK=swift' >> app/backend/microservices/.env
```

`scripts/generate-dev-secrets.sh <stack> > .env` writes it for you. Without it, a stack is `back`.

| Worktree             | `KAPP_STACK` | Containers                                                    |
| -------------------- | ------------ | ------------------------------------------------------------- |
| backend              | `back`       | `back-api-gateway`, `back-auth-service`, `back-mongo`, …       |
| portal               | `front`      | `front-api-gateway`, `front-web-admin`, …                      |
| iOS (mocks only)     | `swift`      | `swift-auth-service-mock`, `swift-semaphore-service-mock`, …   |
| Android (mocks only) | `kotlin`     | `kotlin-auth-service-mock`, `kotlin-semaphore-service-mock`, … |

So the mobile team needs exactly two commands:

```bash
echo 'KAPP_STACK=kotlin' >> app/backend/microservices/.env
```

```bash
pnpm run microservices:mock
```

**Why it matters, twice over.** The names used to be fixed (`kapp-auth`, `kapp/auth-service:local`)
and shared by every checkout. Two stacks then fought over ports 8080 and 4300 until neither
answered, and — worse — a stack started with an image tag another worktree had just rebuilt from a
different branch, which ran that branch's migrations against the shared Atlas cluster. Both
failures were silent.

### Two stacks at the same time

Two stacks that run **different** services — a backend and somebody's mocks — coexist fine. Two
that run the **same** services do not, even with different container names: Compose tracks a
service by project plus service name, so starting one recreates the other's containers. If you
really need two backends at once, give one of them its own project as well:

```bash
echo 'KAPP_PROJECT=kapp-experiment' >> app/backend/microservices/.env
```

Either way they need different host ports. Set these in the same `.env`:

| Variable            | Default | Variable                   | Default |
| ------------------- | ------- | -------------------------- | ------- |
| `KAPP_GATEWAY_PORT` | 8080    | `KAPP_MOCK_AUTH_PORT`      | 4010    |
| `KAPP_PORTAL_PORT`  | 4300    | `KAPP_MOCK_USER_PORT`      | 4011    |
| `KAPP_MONGO_PORT`   | 27017   | `KAPP_MOCK_SEMAPHORE_PORT` | 4012    |
|                     |         | `KAPP_MOCK_SCHEDULE_PORT`  | 4013    |
|                     |         | `KAPP_MOCK_MAP_PORT`       | 4014    |

The portal reaches the gateway from the browser, so if you move `KAPP_GATEWAY_PORT`, set the base
URL in the portal's own **Gateway** box on the sign-in screen.

### The first time after this change

Stop what is running, so the old `kapp-*` containers go away instead of sitting there stopped, and
drop the images that carried the old shared tag:

```bash
docker compose --profile full --profile dev down
```

```bash
docker images 'kapp/*:local' -q | xargs -r docker rmi
```

Then start as usual with `--build`, which builds the images under the new tag. The project name
does not change, so the local Mongo volume and its data are untouched — and so is Atlas.

---

## Starting and stopping

All commands run from `app/backend/microservices/`.

```bash
cd app/backend/microservices
```

**Start.** The data lives in the shared Atlas cluster, so there is nothing to start besides the
services themselves:

```bash
docker compose --profile academic --profile map --profile dev up -d   # the usual combination
docker compose --profile mock up -d                                    # mobile work, no backend
docker compose --profile full --profile dev up -d                      # everything, mocks included
```

`pnpm run microservices:start` is the first of those three, from the repository root, and works
the same on Windows.

**The Atlas cluster is always on.** You do not start or stop it, and nothing has to be running on
anybody's machine for the data to be there. A free M0 cluster only pauses after **60 days with no
connection at all**, which will not happen while anyone is working.

**Stop**

```bash
docker compose --profile full --profile dev down       # stop your containers
```

`-v` no longer has anything to remove here: there are no volumes left in this stack.

**M0 has no backups.** If somebody drops a collection there is no restore: the data is gone. What
makes that survivable is that almost everything is reproducible — Mongock re-seeds the buildings,
spaces and invitation codes on startup, and the pensums reload from the CSVs in
`docs/templates/pensums/`. What is *not* reproducible is anything typed straight into the portal.
Keep the CSV as the source and import it; do not treat the cluster as the only copy.

`down` only stops what the named profiles cover, so pass the same profiles you started with, or
just pass `full` and `dev` to catch everything.

**Rebuild after changing code**

```bash
docker compose --profile core build auth-service       # one service
docker compose --profile core up -d auth-service       # restart it

docker compose --profile full build                    # everything, slow
```

**See what is running**

```bash
docker compose ps
docker compose logs -f auth-service                    # follow one service
docker compose logs --tail=50 api-gateway
```

---

## What runs where

| URL                                   | What                                              |
| ------------------------------------- | ------------------------------------------------- |
| http://localhost:4300                 | **Admin and developer portal**                    |
| http://localhost:8080                 | API gateway — the only backend entry point        |
| http://localhost:8080/swagger-ui.html | Aggregated API documentation                      |
| http://localhost:27017                | MongoDB, for `mongosh` and Compass                |
| http://localhost:4010-4014            | Prism mocks: auth, user, semaphore, schedule, map |

**The floor editor** is a self-contained HTML file with no server and no network:

```bash
open app/backend/microservices/map-service/src/main/resources/static/admin/grid-editor.html
```

It is how a floor is captured — walk it, draw the grid, export the JSON. It enforces the same two
rules the server does, so a room that will not fit or that overlaps another is refused while you
are still standing in the building rather than hours later.

**Ports 8081 to 8085 are deliberately unreachable.** The services are only addressable through the
gateway; being able to bypass it was security finding S1. If you need to reach one directly for
debugging, go through the container:

```bash
docker compose exec api-gateway wget -qO- http://auth-service:8081/auth/health
```

---

## Local accounts

The four development accounts are created by a script, which generates their passwords on your
machine and writes them to `app/backend/microservices/.dev-accounts` (mode 600, gitignored):

```bash
scripts/create-dev-accounts.sh
```

| E-mail                              | Role       |
| ----------------------------------- | ---------- |
| `brian@kforge.dev`     | ADMIN |
| `ivan@kforge.dev`      | ADMIN |
| `alejandro@kforge.dev` | ADMIN |
| `santiago@kforge.dev`  | ADMIN |

**`kforge.dev`, not the university's domain.** These are development identities, not people. One
of them can never collide with somebody's real institutional address once KApp authenticates
against Entra ID, and a development account is recognisable as one at a glance — in the
directory, in a log line, in a screenshot pasted into a chat.

The domain is not registered to us and does not need to be: nothing sends mail to it, because
e-mail verification is off and these accounts are created ACTIVE. **If verification is ever
turned on, these four have to be reissued on a domain we control**, or their confirmation mail
goes to whoever owns `kforge.dev`.

All four hold ADMIN because the team is still building KApp and everyone needs to reach
everything. There is deliberately no admin/developer split yet — and that has to change before
KApp is reachable from outside a laptop. It is recorded in `SECURITY-AUDIT.md`.

The passwords are **not** shared here, in the repository or in the group chat: anything said in
one of those stays in its history forever, and rotating the password later does not remove it.
Give each teammate their line from `.dev-accounts` privately.

`--recreate` deletes the four and issues new passwords:

```bash
scripts/create-dev-accounts.sh --recreate
```

These live in the **shared cluster**, so they are the same four accounts for everybody and they
survive every `down`. Create them once; running the script again reports them as existing.

### Visitors

There is no guest account. Open guest registration was replaced by a **day pass**: reception
issues a code, the visitor redeems it, and no account is created at all.

```bash
CODE=$(curl -sS -X POST http://localhost:8080/auth/admin/visitor-passes \
  -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
```

Then redeem it — the identity document is required, because the whole point is that reception
has a record of who was in the building:

```bash
curl -sS -X POST "http://localhost:8080/auth/visitor-passes/$CODE/redeem" \
  -H 'Content-Type: application/json' \
  -d '{"documentType":"CC","documentNumber":"1032456789","visitorName":"Visitante de prueba"}'
```

The token that comes back lasts 24 hours, carries `ROLE_GUEST`, and is refused everywhere but
`/api/map/**`. The register is at `GET /auth/admin/visitor-passes` and **deletes itself after 30
days** — MongoDB does it, not a scheduled job. See `SECURITY-AUDIT.md`, S12.

**To register any other account** you need an active invitation code, and **both seeded codes are
deactivated** — see S11 in `SECURITY-AUDIT.md`. They were the only way into the admin portal that
did not go through the four accounts above, and the portal now requires `ROLE_ADMIN` anyway.

Mint one from the portal (*Invitation codes → New code*) when the mobile app needs registration,
then:

```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@konradlorenz.edu.co","password":"KForge2026Dev!",
       "firstName":"Your","lastName":"Name","invitationCode":"KL-20262-STUDENT",
       "studentCode":"506000000","programCode":"506"}'
```

Register no more than a handful at a time — the gateway allows 10 attempts per minute on the
credential endpoints and will answer 429.

`ROLE_ADMIN` is never granted by an invitation code, on purpose: the codes ship in the repository,
so a code that granted admin would let anyone who can read the repo escalate. Promote an existing
account instead. The role lives in two databases, and no account reaches both, so it is two calls:

```bash
cd app/backend/microservices
set -a; . ./.env; set +a
EMAIL=you@konradlorenz.edu.co
docker run --rm mongo:7 mongosh "$MONGO_AUTH_URI" --quiet --eval \
  "db.credentials.updateOne({email:'$EMAIL'}, {\$set:{roles:['ROLE_ADMIN']}})"
docker run --rm mongo:7 mongosh "$MONGO_USER_URI" --quiet --eval \
  "db.users.updateOne({email:'$EMAIL'}, {\$set:{role:'ROLE_ADMIN'}})"
```

`scripts/create-dev-accounts.sh` does exactly this for the four team accounts.

---

## Tests

```bash
cd app/backend/microservices

./mvnw -B verify                              # everything, ~5 minutes
./mvnw -B -pl map-service -am verify          # one service
```

Tests use Testcontainers, so **Docker must be running**. They start their own MongoDB, which is
the only local database left anywhere in this repository: a test run has to be free to wipe its
data, and the shared cluster is the last place that should be possible.

Use `verify`, not `install`. `install` writes to the shared local Maven repository and races with
anyone else building at the same time.

---

## Databases and credentials

Each service has its own database **and its own MongoDB account**, holding `readWrite` on that one
database and nothing else. A credential that leaks out of one service opens exactly one database;
map-service cannot read `kapp_auth` even if somebody writes the query by mistake. This is what makes
the anteproyecto's claim of *"servicios independientes, con base de datos propia"* true in the engine
rather than only in the code.

| Database         | Account                 | Service           |
| ---------------- | ----------------------- | ----------------- |
| `kapp_auth`      | `kapp_auth_user`        | auth-service      |
| `kapp_user`      | `kapp_user_user`        | user-service      |
| `kapp_semaphore` | `kapp_semaphore_user`   | semaphore-service |
| `kapp_schedule`  | `kapp_schedule_user`    | schedule-service  |
| `kapp_map`       | `kapp_map_user`         | map-service       |

All five live in the shared Atlas cluster. Their connection strings go in `.env`, one per
service, and nothing generates them for you: they come from the cluster, through
[ATLAS-SETUP.md](ATLAS-SETUP.md) or from a teammate, privately.

```bash
cd app/backend/microservices
../../../scripts/generate-dev-secrets.sh back > .env   # everything else, and blanks for these five
```

**To prove the isolation actually holds:**

```bash
scripts/verify-db-isolation.sh
```

Each of the five accounts must reach its own database and be refused on the other four — 25 checks,
against the cluster itself. It needs nothing running: it connects with the strings from `.env`
through a throwaway mongosh container. Run it after any change to the cluster's database users.

---

## Inspecting the database

Each service's string opens that service's database and no other, so pick the one whose data you
want. A throwaway container saves installing mongosh:

```bash
cd app/backend/microservices
set -a; . ./.env; set +a
docker run --rm mongo:7 mongosh "$MONGO_MAP_URI" --quiet --eval \
  'db.spaces.find().limit(5).forEach(printjson)'
```

Sourcing `.env` is why every `MONGO_*_URI` in it is single-quoted: unquoted, the shell reads the
`&` as "run the rest in the background" and leaves the variable empty.

There is no account that spans the five databases, by design ([ADR 0005](adr/0005-per-service-database-credentials.md)).
A question that crosses services is two queries.

For an interactive session, paste the same string into Compass.

**Remember whose data this is.** It is the cluster the whole team is looking at; a `drop()` typed
here is everyone's afternoon.

---

## Pointing at Atlas

Every profile talks to the shared Atlas development cluster: there is no other option, and no
local database to fall back to ([ADR 0009](adr/0009-atlas-is-the-only-development-database.md)).
Compose reads the five `MONGO_*_URI` variables from `.env` and **refuses to start** if one is
missing, naming it. They look like this:

```
MONGO_MAP_URI=mongodb+srv://kapp_map_user:<password>@<cluster>.mongodb.net/kapp_map?retryWrites=true&w=majority
```

Two parameters must **not** appear in them:

- **`replicaSet`** — the SRV record already carries it.
- **`authSource`** — Atlas stores every database user in `admin` regardless of which database it
  can reach, so pinning the authSource to the service's own database makes authentication fail with
  a message that reads exactly like a wrong password. The isolation still holds; it comes from the
  privilege, not from where the user is stored.

Create the five accounts with the same one-database-each rule: *Specific Privileges* →
`readWrite` on that one database, never the "read and write to any database" built-in role.
`scripts/verify-db-isolation.sh` proves it from outside the UI.

**Full walkthrough: [ATLAS-SETUP.md](ATLAS-SETUP.md)** — creating the cluster, the five users, the
network rules and the connection strings, step by step.

**The tests do not use Atlas.** They start their own MongoDB through Testcontainers, deliberately:
pointing them at a shared cluster would make them slow and flaky, and one person's run would wipe
another's data mid-test.

---

## Troubleshooting

Every entry here is a failure we actually hit.

### "Network Error (0)" in the portal, no status code

CORS. The browser blocked the request before sending it, which is why there is no HTTP status —
if the backend were down you would see a timeout or a 502 instead.

The gateway allows only listed origins. Check yours is one of them:

```bash
curl -i -X OPTIONS http://localhost:8080/auth/login \
  -H "Origin: http://localhost:4300" \
  -H "Access-Control-Request-Method: POST"
```

A 200 with `Access-Control-Allow-Origin` is fine; a 403 means your origin is missing. Add it to
`kapp.cors.allowed-origins` in `api-gateway/src/main/resources/application.yml`, then rebuild the
gateway.

### The first request after starting returns 504

Expected, once. Services fetch the signing keys from auth-service lazily — deliberately, because
fetching them eagerly would deadlock startup — so the first authenticated request pays for that
fetch plus JVM warm-up and can exceed the gateway's 10-second timeout. Retry; it will be fast.

### 429 Too Many Requests on login or register

The rate limiter, working. Ten attempts per minute per client on the credential endpoints. Wait a
minute.

### A service shows `unhealthy` but seems to work

Read its logs before assuming the service is broken — the health probe may be failing on something
optional:

```bash
docker compose logs --tail=30 auth-service | grep -i "health\|error"
```

auth-service used to report unhealthy purely because no SMTP server exists. That specific case is
fixed, but the pattern recurs.

### Tests fail with "Could not find a valid Docker environment"

Docker Desktop is not running. Start it and wait for the whale icon to settle.

### A service 404s on endpoints you know exist

The container is running an older image than the one you just built. `docker compose up -d`
starts whatever image exists **at that moment**, so kicking off a build and bringing the stack
up before it finishes leaves containers on the previous build — and a service still carrying
only its Phase 0 skeleton answers 404 for every real endpoint.

Check what the running jar actually contains:

```bash
docker compose exec map-service sh -c 'unzip -l /app/app.jar | grep -c "kapp/map"'
```

A handful of classes means the skeleton; several dozen means the real service. Fix by running
`up -d` again once the build has finished — Compose recreates only the containers whose image
changed.

### A service starts and then dies with `MongoSecurityException`

Its `MONGODB_URI` no longer matches the cluster — usually a rotated password that `.env` did not
follow, or a string pasted with the `&` unquoted so everything after it was lost. Check what the
container is actually using, with the password redacted:

```bash
docker compose exec -T map-service printenv MONGODB_URI | sed -E 's#://([^:]+):[^@]+@#://\1:REDACTED@#'
```

If the host is right and the password is not, get the current string from Atlas
(*Database Access → Edit → connection string*) and paste it back into `.env`, single-quoted.

### A service cannot reach the cluster at all

`MongoTimeoutException` rather than a security error means the network, not the credential. Atlas
only answers addresses on its access list: check yours is there
(*Network Access → IP Access List*), which changes every time you move between campus and home.

### Everything is slow, the fan is loud

You are probably running `full` when you need `core`. Check with `docker compose ps` and restart
with a narrower profile.

### Starting fresh

```bash
docker compose --profile full --profile dev down
docker compose --profile academic --profile map --profile dev up -d --build
```

That is as fresh as it gets from here: the containers are new, the images rebuilt, and the data is
untouched because it is not on this machine. Mongock's change units have already run against the
cluster and will not run again.

**Wiping the data is a different thing entirely, and it is everybody's data.** If a collection has
to go, say so in the group chat first, do it deliberately from Atlas, and remember M0 has no
backups — the seeds come back on the next start, anything typed into the portal does not.

The passwords will be new, and `.dev-accounts` is overwritten with them.

---

## Legacy scripts

`scripts/start-microservices.sh` is **gone**. It predated the containers: it started bare JVMs, and
its service list still named `course-service` and `assignment-service` while knowing nothing about
`semaphore`, `schedule` or `map`. It also could not run at all on a Mac — it used a bash 4
associative array, and macOS ships bash 3.2, where `[discovery-server]=8761` is parsed as
arithmetic and dies with `discovery: unbound variable`.

The `pnpm run microservices:*` commands it backed now wrap `docker compose` directly, which is how
everything has actually run since the migration, and which behaves the same on Windows and macOS.

`scripts/start-frontend.sh` stays. It serves the frozen prototype in `app/frontend/web/`, it works,
and `PORT=4000 scripts/start-frontend.sh` still uses it; `pnpm run prototype:serve` is the
same thing on port 3000.
