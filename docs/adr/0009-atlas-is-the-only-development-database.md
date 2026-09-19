# ADR 0009 — Atlas is the only development database

- **Status:** Accepted and implemented
- **Date:** 2026-09-19

## Context

The stack shipped two ways to get a database: a MongoDB container started by the `core`,
`academic`, `map` and `full` profiles, and the shared Atlas cluster reached through the `cloud`
profile. [ADR 0005](0005-per-service-database-credentials.md) applied to both — one account per
service either way.

Two databases meant two of everything else. The container needed a replica set, a keyFile, a user
provisioning script (`mongo-init/rs-init.js`) and a health probe that doubled as the replica set
initiator; the runbook needed a section on switching between them, three troubleshooting entries
that only apply locally, and a "starting fresh" procedure that wipes a volume. Six people ran six
copies of the same data, seeded differently, and a demo against one of them proved nothing about
the others.

On a laptop already running Xcode, Android Studio and six JVMs, the container was also the single
largest thing in the stack: 1.5 GB of the roughly 5 GB, and the first thing the kernel killed when
memory ran short. It was OOM-killed during the pensum verification, which is how the question came
up.

## Decision

**There is no local database.** The compose file has no `mongo` service, no volumes and no local
connection strings. Every service reads `MONGO_*_URI` from `.env`, pointed at the shared Atlas
cluster, and Compose refuses to start if one is missing rather than falling back to anything.

The `cloud` profile is gone with it: it meant "everything except the local database", which is now
simply everything.

**The tests keep their own database** — Testcontainers, one ephemeral MongoDB per run. That is not
a contradiction: a test run must be able to wipe its data, and doing that against the cluster the
team is looking at is exactly what nobody should be able to do by accident.

## Consequences

**The stack needs the network.** No connection, no backend. That is the real cost, and it is
accepted: the same laptop already needs the network for Maven, Docker Hub and Gradle, and a plane
or a bad campus wifi is a worse day for other reasons.

**A destructive experiment is everybody's.** Dropping a collection, running a migration, importing
a bad CSV — all of it lands on the data the other five are using. Two habits follow, and the
runbook says both: keep dangerous work in tests, where Testcontainers isolates it, and when the
shared data has to change, say so in the group chat first.

**Whatever runs against the cluster applies its migrations to it.** This already happened once,
before this decision: a stack started with an image another worktree had rebuilt from a different
branch ran that branch's Mongock change units against Atlas. Per-worktree image tags
([the stack naming change](../RUNBOOK.md#stacks-one-per-worktree)) make that visible instead of
silent, but the underlying rule stands — starting the stack is a write to shared data.

**The free tier's limits are now everyone's limits.** M0 allows 500 concurrent connections, which
is why every connection string keeps `maxPoolSize=10`, and it pauses after 60 days with no
connection at all, which will not happen while anyone is working.

**What was removed:** the `mongo` service, its volumes, `mongo-init/rs-init.js`, the local
passwords in `generate-dev-secrets.sh`, the `cloud` profile, and the runbook sections that only
described the local case. `scripts/verify-db-isolation.sh` now proves ADR 0005 against the cluster
itself, which is the database that actually holds the data.
