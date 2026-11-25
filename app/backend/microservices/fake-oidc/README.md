# Fake OIDC server

A stand-in for Microsoft's sign-in, **for evaluation only**: running KApp with the `local-db` profile on a computer
with none of the team's accounts ([ADR 0010](../../../../docs/adr/0010-a-local-database-for-evaluation.md),
[RUN-LOCALLY.md](../../../../docs/RUN-LOCALLY.md)). A client signs in through it as an invented person, and
auth-service checks the ID token it gets back exactly as it checks a Microsoft one: signature, issuer, audience and
expiry. Nothing here is real, and nothing here may reach Atlas, the dev server or production.

It is [mock-oauth2-server](https://github.com/navikt/mock-oauth2-server), started by `docker-compose.yml` with the
`local-db` profile only, at `http://localhost:${KAPP_FAKE_OIDC_PORT:-8090}/kapp`.

**The team does not use it** (#246). Development runs on Atlas with Microsoft sign-in against the test tenant: see
[Against the real tenant](#against-the-real-tenant). auth-service refuses to start when its Microsoft issuer or key
set is not Microsoft's while its database is on Atlas, so the invented `@kforge.dev` people cannot land there again.
auth-service's migration V006 and user-service's V004 deleted the ones that had.

## Who you can be

At its login page, type one of these user names. Leave the claims box empty.

| User name | Profile | Address |
|---|---|---|
| `estudiante` | student | `estudiante@kforge.dev` |
| `profesor` | professor | `profesor@kforge.dev` |
| `administrativo` | administrative staff | `administrativo@kforge.dev` |
| anything else | none: KApp gives `ROLE_UNASSIGNED` ("Sin rol") | `<name>@kforge.dev` |

`config.json` holds them. The profile travels in the `roles` claim, as the university's app roles will.

`administrativo` is also one of the first administrators (`KAPP_FIRST_ADMINS` in `docker-compose.yml`), so it opens
the admin portal. Grant other permissions from the portal.

## What a client needs

| Setting | Value |
|---|---|
| Discovery document | `http://localhost:8090/kapp/.well-known/openid-configuration` |
| Client id, apps | `kapp-app-dev` |
| Client id, portal | `kapp-portal-dev` |
| Flow | authorization code with PKCE; no client secret |

The ID token it returns goes to `POST /auth/microsoft`, as Microsoft's will.

## Which address to use

The token's issuer is the address the client used to reach the server, and auth-service accepts only the issuers in
`KAPP_MICROSOFT_ISSUERS`:

- **a browser, or the iOS simulator:** `http://localhost:8090/kapp`;
- **the Android emulator:** `http://10.0.2.2:8090/kapp`;
- **a phone on the same network:** `http://<the Mac's address>:8090/kapp`. Add it to `KAPP_MICROSOFT_ISSUERS` in
  `.env`, after the two above, and restart auth-service.

## Against the real tenant

This is how the team runs the stack on Atlas. In `.env`, set all six. An empty value would fall back to this fake
server, since that is the compose default, and auth-service would then refuse to start on Atlas:

```
KAPP_MICROSOFT_TENANT_ID=<tenant id>
KAPP_MICROSOFT_ISSUERS=https://login.microsoftonline.com/<tenant id>/v2.0
KAPP_MICROSOFT_JWKS_URI=https://login.microsoftonline.com/<tenant id>/discovery/v2.0/keys
KAPP_MICROSOFT_CLIENT_IDS=<application (client) id>
KAPP_MICROSOFT_PORTAL_CLIENT_ID=<application (client) id>
KAPP_FIRST_ADMINS=<your address in the tenant>
```

Then `docker compose up -d auth-service`. The portal at `http://localhost:4300` sends you to Microsoft,
which needs `http://localhost:4300/auth/callback` registered as a single-page application redirect.

None of them is a secret. Issue #62 lists the steps to create the test tenant.
