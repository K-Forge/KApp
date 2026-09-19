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

Seven JVMs plus MongoDB is roughly 5 GB. You rarely need that, and on a laptop also running Xcode
or Android Studio you actively do not want it.

| Profile    | What starts                           | RAM     | Use it when                                       |
| ---------- | ------------------------------------- | ------- | ------------------------------------------------- |
| `mock`     | one Prism mock server per contract                  | ~200 MB | Building a mobile screen against the API contract |
| `core`     | mongo, discovery, gateway, auth, user | ~2.5 GB | Working on sign-in or profiles                    |
| `academic` | core + semaphore, schedule            | ~3.5 GB | Working on pensums or timetables                |
| `map`      | core + map                            | ~3 GB   | Working on the campus map                         |
| `full`     | everything                            | ~5 GB   | End-to-end checks before a merge                  |
| `dev`      | the admin portal                      | ~50 MB  | Any time you want the web console                 |
| `cloud`    | everything **except** MongoDB         | ~4.5 GB | Pointing the services at the Atlas dev cluster    |

Profiles combine. The portal on its own is not much use, so pair it with a backend:

```bash
docker compose --profile core --profile dev up -d
```

---

## Stacks: one per worktree

Everybody works in their own worktree, and more than one stack ends up running on the same
laptop. `KAPP_STACK` is what keeps them apart: it names the Compose project, every container and
every image tag. Set it once, in `app/backend/microservices/.env` of that worktree:

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

They still need different host ports. Set these in the same `.env`:

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

The old containers, images and local volume belong to a project name nothing uses any more:

```bash
docker compose -p kapp --profile full --profile dev down -v
```

```bash
docker images 'kapp/*:local' -q | xargs -r docker rmi
```

Then start as usual. The images are rebuilt under the new tag on the first `--build`, and a local
Mongo starts empty and re-seeds itself. Atlas is untouched by any of this.

---

## Starting and stopping

All commands run from `app/backend/microservices/`.

```bash
cd app/backend/microservices
```

**Start** — `cloud` is the normal one now. The databases live in the shared Atlas cluster, so
there is nothing local to start and nothing to seed:

```bash
docker compose --profile cloud --profile dev up -d     # the usual combination
docker compose --profile mock up -d                    # mobile work, no backend at all
docker compose --profile full --profile dev up -d      # everything, with a LOCAL database
```

**The Atlas cluster is always on.** You do not start or stop it, and nothing has to be running on
anybody's machine for the data to be there. A free M0 cluster only pauses after **60 days with no
connection at all**, which will not happen while anyone is working.

**Stop**

```bash
docker compose --profile cloud --profile dev down      # stop your containers
```

**Never `down -v` against the shared cluster.** It does not delete anything in Atlas — the `-v`
removes local volumes — but it is a habit worth not having, because the same reflex against
`--profile full` wipes a local database, and one day it will be the wrong one.

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

### Switching between the local database and Atlas

```bash
scripts/set-atlas-uris.sh          # point the services at the shared cluster
scripts/set-atlas-uris.sh --local  # and back, for a plane or bad Wi-Fi
```

It rewrites only the five `MONGO_*_URI` lines in `.env` and saves the previous one as `.env.bak`.
The local passwords are left alone, so switching back is one command and not a regeneration.

With Atlas, start the stack with `--profile cloud` instead of `--profile full`: it runs every
service and no local database.

`--recreate` deletes the four and issues new passwords:

```bash
scripts/create-dev-accounts.sh --recreate
```

These live in **your** MongoDB. They are not shared between machines, and they disappear with
`down -v`; run the script again afterwards.

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
account instead — note that this now needs the Mongo root credentials, which live in `.env`:

```bash
cd app/backend/microservices
docker compose exec -T -e P="$(grep '^MONGO_ROOT_PASSWORD=' .env | cut -d= -f2-)" \
  mongo mongosh --quiet --eval '
  db.getSiblingDB("admin").auth("kapp_root", process.env.P);
  const email = "you@konradlorenz.edu.co";
  db.getSiblingDB("kapp_auth").credentials.updateOne({email}, {$set:{roles:["ROLE_ADMIN"]}});
  db.getSiblingDB("kapp_user").users.updateOne({email}, {$set:{role:"ROLE_ADMIN"}});'
```

---

## Tests

```bash
cd app/backend/microservices

./mvnw -B verify                              # everything, ~5 minutes
./mvnw -B -pl map-service -am verify          # one service
```

Tests use Testcontainers, so **Docker must be running** — they start their own MongoDB and do not
touch the one from compose.

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

Plus `kapp_root`, which exists only to provision the other five and to run the admin commands in
this runbook. No service uses it.

The passwords live in `.env` and are generated per machine:

```bash
cd app/backend/microservices
../../../scripts/generate-dev-secrets.sh > .env
```

Re-running produces a **different** set. If the volume already holds accounts created with the old
passwords, mongo will refuse to start healthy and say so — see the troubleshooting entry below.

**To prove the isolation actually holds:**

```bash
scripts/verify-db-isolation.sh
```

Each of the five accounts must reach its own database and be refused on the other four — 25 checks.
Run it after any change to `mongo-init/rs-init.js`.

---

## Inspecting the database

mongosh now needs credentials. For a single service's data, use that service's account:

```bash
cd app/backend/microservices
docker compose exec -T -e U=kapp_map_user -e P="$(grep '^MONGO_MAP_PASSWORD=' .env | cut -d= -f2-)" \
  mongo mongosh --quiet --eval '
  db.getSiblingDB("kapp_map").auth(process.env.U, process.env.P);
  db.getSiblingDB("kapp_map").spaces.find().limit(5).forEach(printjson);'
```

For anything spanning services, use root:

```bash
docker compose exec -T -e P="$(grep '^MONGO_ROOT_PASSWORD=' .env | cut -d= -f2-)" \
  mongo mongosh --quiet --eval '
  db.getSiblingDB("admin").auth("kapp_root", process.env.P);
  db.getSiblingDB("kapp_user").users.countDocuments({role: "ROLE_STUDENT"});'
```

The password goes in as an environment variable rather than on the command line, because anything
in `argv` is readable by every process in the container through `/proc`.

For an interactive session, Compass or `mongosh` from the host connect with
`mongodb://kapp_root:<password>@localhost:27017/?authSource=admin&directConnection=true`. The
`directConnection=true` matters: the replica set advertises itself as `mongo:27017`, which does not
resolve from your Mac.

---

## Pointing at Atlas

The `cloud` profile starts everything **except** the local MongoDB, so the services talk to the
shared Atlas development cluster instead:

```bash
docker compose --profile cloud --profile dev up -d
```

It reads the same five `MONGO_*_URI` variables from `.env`; replace their values with the cluster's
strings:

```
MONGO_MAP_URI=mongodb+srv://kapp_map_user:<password>@<cluster>.mongodb.net/kapp_map?retryWrites=true&w=majority
```

Two parameters that belong in the local URI must **not** appear in the Atlas one:

- **`replicaSet`** — the SRV record already carries it.
- **`authSource`** — Atlas stores every database user in `admin` regardless of which database it
  can reach, so pinning the authSource to the service's own database makes authentication fail with
  a message that reads exactly like a wrong password. The isolation still holds; it comes from the
  privilege, not from where the user is stored.

Create the five accounts in Atlas with the same one-database-each rule: *Specific Privileges* →
`readWrite` on that one database, never the "read and write to any database" built-in role.
`scripts/verify-db-isolation.sh` assumes the local container, so check Atlas isolation from the
Atlas UI instead.

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

### The mongo container never becomes healthy

Read what the probe actually said:

```bash
docker inspect -f '{{range .State.Health.Log}}exit={{.ExitCode}} {{.Output}}{{end}}' \
  "$(docker compose ps -q mongo)" | tail -5
```

One `exit=1` at the very start is normal — the probe runs before the replica set has elected itself
and correctly refuses to report healthy until the node can accept writes.

A repeated `FATAL: cannot authenticate as kapp_root and cannot create it` means your `.env` no
longer matches the volume: the accounts were provisioned with a different set of passwords, most
likely because `generate-dev-secrets.sh` was run again. The passwords in `.env` are the only copy,
so the fix is to discard the local data:

```bash
docker compose --profile full --profile dev down -v
docker compose --profile core --profile dev up -d
```

### A service starts and then dies with `MongoSecurityException`

Its `MONGODB_URI` and the volume disagree. Same cause and same fix as the entry above. Check what
the container is actually using, with the password redacted:

```bash
docker compose exec -T map-service printenv MONGODB_URI | sed -E 's#://([^:]+):[^@]+@#://\1:REDACTED@#'
```

### Editing `mongo-init/rs-init.js` and errors vanish without a message

mongosh rewrites the **top-level** program to await the driver's promises, but it does not rewrite
the body of an ordinary function declaration. Inside a function, a failing call rejects a promise
that a synchronous `catch` never sees, so the error escapes the `try` entirely and kills the
process — the container goes unhealthy with a bare `MongoServerError` and no clue which line
produced it.

Keep every `try`/`catch` at the top level of that script, including the ones inside the loop. A
top-level `try` **around** a call to a function does work; one written **inside** the function does
not. This cost an afternoon; the script's header says so too.

### Everything is slow, the fan is loud

You are probably running `full` when you need `core`. Check with `docker compose ps` and restart
with a narrower profile.

### Starting fresh

```bash
docker compose --profile full --profile dev down -v    # wipes the database
docker compose --profile core --profile dev up -d
```

The seed data — pensums, buildings, spaces, invitation codes — is reloaded automatically by
Mongock on startup, and the MongoDB accounts are re-provisioned by the healthcheck. User accounts
are not; recreate the four development ones:

```bash
scripts/create-dev-accounts.sh
```

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
and `pnpm run web:start:script` still uses it.
