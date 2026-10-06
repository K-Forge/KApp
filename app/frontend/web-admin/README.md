# KApp admin & dev portal

An internal Angular tool for the K-Forge team building against the KApp backend. There is no
admin UI on the backend itself, and before this existed the only way to sign in as a given role,
inspect a token, or exercise an endpoint was `curl` and `mongosh`. This is what replaces that.

It is not a product for students or staff - it never ships to a store, has no design polish beyond
"clear and fast to use", and assumes whoever is looking at it already understands the domain.

## What it's for

- **Sign in as any role and see what the token actually contains.** Decoded subject, e-mail,
  roles, issuer, issued-at and a live expiry countdown, with a clear warning once it's expired.
  The four native-client developers building Kotlin and Swift clients need this to verify their
  own token handling without guessing.
- **Fire a real request at any endpoint in seconds.** The API console reads every OpenAPI
  contracts, lets you pick a service and an operation, fills path/query parameters typed from the
  spec (enums render as dropdowns, not free text you have to get exactly right), edits the JSON
  body, and shows status, timing, response headers and a pretty-printed body. This is what lets a
  mobile developer reproduce a failing call from their app in this tool instead of rebuilding it
  by hand.
- **Manage the data the mobile clients read.** Users (search, filter, activate/deactivate),
  buildings and spaces (full CRUD, including the floor and pin-position fields the map screens
  render from), programs (read-only - see below) and curricula (create/replace as a JSON
  document).
- **Check the authorization matrix by hand.** The role inspector shows which of the four roles the
  signed-in account holds and, for every endpoint across all the specs, which role(s) the
  documentation says can reach it.

### What's deliberately not here

**Invitation codes** has a nav entry that explains, rather than a form: `docs/api/auth.openapi.yaml`
requires an `invitationCode` at registration, but defines no admin endpoint to create, list or
revoke one. Building a form against an endpoint that doesn't exist would mean guessing at a shape
the server was never built to accept - exactly the failure mode this tool exists to prevent.
**Programs** is read-only for the same reason: the semaphore contract has no admin write endpoint
for a program itself, only for its curricula.

## Running it

### Locally with pnpm (for developing the portal itself)

```bash
cd app/frontend/web-admin
pnpm install
pnpm start
```

Opens on **http://localhost:4200**. `pnpm start` (and `pnpm build`, `pnpm test`) first regenerate
`src/app/core/openapi/generated/*.json` from `docs/api/*.openapi.yaml` - see
[How the API console reads the specs](#how-the-api-console-reads-the-specs) below - so the OpenAPI
contracts are always current with whatever is checked in, with no separate step to remember.

### As a container (for everyone else)

```bash
cd app/backend/microservices
docker compose --profile dev up -d
```

Opens on **http://localhost:4300**. No Node, no pnpm, nothing to install - a multi-stage build
compiles the Angular app and serves the static output with nginx. The published port is 4300, not
4200: `pnpm start` above already claims 4200 for local development of the portal itself, and 8080,
27017, 4000 and 4010-4014 are already spoken for by the rest of `docker-compose.yml`. A distinct
port means you can run the container and a local `pnpm start` side by side if you ever need to.

```bash
docker compose --profile dev down       # stop it
docker compose --profile dev up -d --build   # rebuild after pulling changes
```

## Pointing it at a different backend

The gateway URL defaults to `http://localhost:8080` (set in `src/environments/environment.ts`),
but the whole point is that this is a **runtime** setting, not a build-time one - someone on the
team may need to point their portal at a teammate's machine or a tunnel without rebuilding
anything:

- On the **login screen**, expand "Gateway" and enter a new base URL before signing in.
- Once signed in, the **header** shows the current gateway and a button to change it.

The value is saved in `localStorage` (per browser, not synced anywhere) and used by every request
the portal makes, including the API console. To go back to the default, clear it or set it back to
`http://localhost:8080`.

If nothing is running at that URL yet, the backend team's own compose profiles cover the range you
need - from `docker compose --profile mock up` (Prism mocks only, no JVM, no Mongo) up to
`--profile full` (everything). See the root `AGENTS.md` for the full list.

## How the API console reads the specs

`docs/api/*.openapi.yaml` are the authoritative contracts - every path, payload shape and status
code the portal shows comes from them, not from anything hand-maintained here. `pnpm start` /
`build` / `test` run `scripts/generate-openapi.mjs` first, which parses the five YAML files and
writes plain JSON into `src/app/core/openapi/generated/` (gitignored - it's derived, and a
checked-in copy would go stale the moment a spec changed without anyone noticing). Those JSON
files are then `import`ed like ordinary TypeScript modules, so they're bundled at build time with
no runtime fetch and no risk of the portal loading before its own contracts are ready.

From there, `OpenApiCatalogService` flattens every `paths` entry across the five documents into a
single list of operations - resolving `$ref`s (including a parameter's own schema, which is easy
to miss: `SpaceType` on the `type` query parameter is a `$ref`, not an inline enum, and skipping
that resolution is the difference between a dropdown of the four real values and a free-text box
that 400s on a typo), merging path-level and operation-level parameters, and picking a body example
from the spec's own `example`/`examples` where one exists, or synthesizing a plausible skeleton
from the schema (`core/openapi/example.util.ts`) where it doesn't. The console and the role
inspector both read from this one flattened list; neither re-implements spec parsing.

The role inspector adds one more piece: none of the specs carries a machine-readable role
annotation, so `core/openapi/role-requirement.ts` parses the *prose* each operation states its
required role(s) in - three different phrasings show up across the five files ("Allowed roles:",
"**Access:**", "Roles:"), plus an "any authenticated role" form. Where an operation states nothing
usable (only the semaphore catalog endpoints do this - the rule lives once in that spec's top-of-file
table instead), it falls back to that documented rule rather than guessing, and reports "not
documented" rather than inventing an answer when even that doesn't apply.

## Architecture, briefly

- **Standalone components everywhere**, lazy-loaded per route, modern control flow (`@if`/`@for`),
  signals for state. No NgModules, no state-management library, no zone.js (this is a zoneless
  Angular 22 app - `ApplicationRef` change detection reacts to signal writes directly).
- **One HTTP client, one error path.** `ApiClientService` resolves every request against the
  currently configured base URL and normalizes every failure - including a request that never
  reached a server at all - into `ApiError`, the envelope shared byte-for-byte across every
  backend services. Every screen renders it through the same `ApiErrorBannerComponent`.
- **One token store.** `TokenStore` decodes the JWT for display (it never verifies the signature -
  that's the gateway's job; this is a "what does this token actually say" tool, not an
  authorization boundary) and is the single source of truth for "is anyone logged in". The
  `authInterceptor` attaches the bearer token to every request and clears the session on a 401.
- **One table shell, `DataTableComponent`.** Loading/empty/pagination chrome is identical across
  Users, Spaces and Programs; each page still owns its own `<thead>`/`<tbody>` markup (projected
  in) because column shape genuinely differs per entity.
- **A native `<dialog>`** (`ModalComponent`) backs every create/edit form - free focus trap,
  Escape-to-close and a backdrop, no hand-rolled accessibility bugs.

## Tests

```bash
pnpm test
```

Runs via Angular's Vitest-based unit-test builder. Coverage is deliberately narrow rather than
broad: the three pieces whose failure mode is *confusing* rather than *obvious* -

- **JWT decoding** (`core/auth/jwt.util.spec.ts`) - malformed tokens, non-ASCII claims, expiry math.
- **The auth interceptor** (`core/auth/auth.interceptor.spec.ts`) - the token gets attached, a 401
  clears the session and redirects, and the error still reaches the caller so a screen (the login
  form especially) can show its own message.
- **`ApiError` parsing** (`core/http/api-error.util.spec.ts`) - the well-formed envelope, and the
  cases that *aren't* one: a status-0 network failure, a non-JSON error body, a differently-shaped
  JSON body.

Presentational components aren't unit tested; their correctness is easiest to see by running the
app.

## Project layout

```
src/app/
  core/           # config, auth, http, openapi parsing, theming - no UI
  shared/ui/      # DataTableComponent, ModalComponent, JsonViewComponent, badges, banners
  layout/shell/   # the authenticated nav + header wrapping every page below
  features/
    login/, identity/, console/, roles/
    data/users/, data/buildings/, data/spaces/, data/programs/, data/curricula/,
    data/invitation-codes/
```
