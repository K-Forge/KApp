# The API mocks, on one port

Every contract in `docs/api/` served by Prism, behind one gateway with the routes of the real one.
A client builds against `http://localhost:4000` and later points at a real gateway by changing only
its base URL. Nothing here needs a JVM, a database or an account.

## Run them

Docker is the only requirement: Docker Desktop on Windows (with WSL 2) and macOS, or Docker Engine
on Linux. From the repository root:

```bash
pnpm microservices:mock
```

or, without pnpm, from `app/backend/microservices`:

```bash
docker compose --profile mock up -d
```

The first run builds the Prism image from this folder, which takes a minute and needs the internet.
After that it starts in seconds. Stop them with:

```bash
docker compose --profile mock down
```

## Where they answer

| Client | Base URL |
|---|---|
| A browser or `curl` on the same machine | `http://localhost:4000` |
| The Android emulator | `http://10.0.2.2:4000`, the emulator's name for the host |
| A physical phone on the same Wi-Fi | `http://<the computer's LAN IP>:4000` |
| The iOS simulator | `http://localhost:4000` |

The routes are the gateway's:

| Path | Contract |
|---|---|
| `/auth/**`, `/.well-known/jwks.json` | `auth.openapi.yaml` |
| `/api/users/**` | `user.openapi.yaml` |
| `/api/semaphore/**`, `/api/catalog/**` | `semaphore.openapi.yaml` |
| `/api/schedule/**` | `schedule.openapi.yaml` |
| `/api/map/**` | `map.openapi.yaml` |

Each Prism also keeps its own port, 4010 to 4014 in that order, for a client that still points at
one service. New work should use 4000.

## Plain HTTP on Android

The mocks speak plain HTTP, and Android refuses it since API 28 unless the app allows it. Allow it
only in the debug build, and only for the hosts above, with a
`src/debug/res/xml/network_security_config.xml`:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

referenced from the debug manifest with `android:networkSecurityConfig="@xml/network_security_config"`.
A release build keeps refusing plain HTTP: a real server is always HTTPS.

## What they answer

Prism answers every request with an example from the contract, so the shapes are exactly the ones
the services return. It also checks the request against the contract: a missing required
parameter or body field gets `400`, and a call to a secured route without `Authorization` gets
`401`. Any bearer token is accepted; take the one `POST /auth/login` returns.

It stores nothing. A `POST` or `PUT` answers as if it had worked, and the next `GET` returns the
same example as before.

To see a screen in another state, ask for it with a `Prefer` header:

```bash
# The 404 the contract documents, e.g. a student with no timetable
curl -H 'Authorization: Bearer x' -H 'Prefer: code=404' \
  'http://localhost:4000/api/schedule/me/day?date=2026-10-05'

# A specific named example, when a response documents several: a day with no classes
curl -H 'Authorization: Bearer x' -H 'Prefer: example=noClasses' \
  'http://localhost:4000/api/schedule/me/day?date=2026-10-05'
```

`Prefer: code=` takes any status the operation documents, and `Prefer: example=` takes the name of
one of its examples.

## Two stacks at once

The containers are named after `KAPP_STACK`, and the port is `KAPP_MOCK_PORT`. A second worktree
running its own mocks sets both in its `app/backend/microservices/.env`:

```bash
KAPP_STACK=kotlin
KAPP_MOCK_PORT=4100
```

## When something fails

- **`502 Bad Gateway`:** that one mock is still starting or has stopped. Wait a few seconds, or
  look at it with `docker compose --profile mock logs mock-schedule`.
- **`port is already allocated`:** something else listens on 4000 (or 4010 to 4014). Set
  `KAPP_MOCK_PORT`, or stop the other stack.
- **A contract changed and the mock did not:** the contracts are mounted, not copied, so a restart
  is enough: `docker compose --profile mock restart`.
- **A mock stops right after starting:** it could not read its contract. Its log says why:
  `docker compose --profile mock logs mock-auth`. On Windows, clone the repository somewhere Docker
  Desktop can mount, such as your user folder or the WSL file system.
