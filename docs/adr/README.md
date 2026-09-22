# Architecture Decision Records

One file per decision that would be expensive to reverse or that someone will otherwise ask about
again in six months. Each records the context, what was decided, what else was considered, and the
consequences — including the bad ones.

They are append-only. A decision that turns out wrong gets a new record superseding the old one, and
the old one stays: the reasoning that led somewhere wrong is worth as much as the reasoning that led
somewhere right.

| # | Decision | Status |
|---|---|---|
| [0001](0001-mongodb-over-postgresql.md) | MongoDB rather than PostgreSQL | Implemented |
| [0002](0002-rs256-with-a-published-jwks.md) | RS256 with a published JWKS, verified by every service | Implemented |
| [0003](0003-oidc-over-saml-for-mobile-authentication.md) | OIDC rather than SAML for the mobile clients | Accepted, pending university configuration |
| [0004](0004-one-database-engine-not-polyglot.md) | One database engine, not polyglot persistence | Implemented |
| [0005](0005-per-service-database-credentials.md) | One MongoDB account per service | Implemented |
| [0006](0006-schematic-map-not-floor-plan-images.md) | A schematic map drawn from data, not floor plan images | Implemented |
| [0007](0007-visitor-day-pass-instead-of-guest-accounts.md) | A visitor day pass instead of guest accounts | Implemented |
| [0008](0008-invitation-codes-are-temporary.md) | Invitation codes are temporary, and this is how they go away | Step 1 implemented |
| [0009](0009-atlas-is-the-only-development-database.md) | Atlas is the only development database | Implemented |

## Still to record

Decisions already taken whose reasoning currently lives only in commit messages and
`docs/INTEGRATION-NOTES.md`:

- Seven services, with `auth` and `user` kept separate because authentication is the seam an
  external identity provider replaces
- Contract-first OpenAPI with Prism mocks, so client and server work proceed in parallel
- `semaphore-service` owning the academic catalogue, because the pensum is what the progress
  view displays
- Freezing `course-service` and `assignment-service` rather than deleting or migrating them
- Personal academic plans stored as deltas over an immutable pensum, rather than as copies of it
