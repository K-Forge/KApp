# ADR 0001 — MongoDB rather than PostgreSQL

- **Status:** Accepted and implemented
- **Date:** 2026-08-26

## Context

KApp ran on PostgreSQL with a 552-line `init.sql` — 33 tables, 13 enum types and roughly 43 audit
triggers — applied by hand, with **no migration tool at all**. Around nineteen of those tables were
modelled for features no service implemented.

The MVP was then narrowed to four domains: users, campus map, a customisable preloaded schedule, and
a customisable preloaded career progress tracker. The team proposed moving to MongoDB, reasoning
that it would make switching to the university's official data easier later.

## Decision

MongoDB, one logical database per service on a single instance, with **Mongock** for versioned
migrations.

## Rationale

The reason originally given — that it eases a future switch to official data — **is not correct**,
and it is worth recording that it was examined rather than accepted. What makes an integration
cheap is a ports-and-adapters boundary, not the storage engine; that would hold equally with
PostgreSQL. The decision stands on different grounds:

**The domain is still moving.** Hand-maintaining SQL DDL with no migration tool, while the model
changes weekly, is a standing source of drift between what the schema says and what the code
expects.

**The aggregates are document-shaped.** A student's timetable is one document, read whole by its
owner: enrolments, weekly meetings and the date ranges each classroom applies to. A student's
progress is one document of roughly 51 course entries with a single writer. Both cost one query
where a normalised model costs several joins, and neither has contention to relieve.

**Migrations become a real tool.** Mongock gives versioned, ordered, audited change units with a
distributed lock, so several developers and CI can point at one database without racing. That is
strictly more than the project had.

**The team already knows it.** A sibling K-Forge project, Roastory, runs on MongoDB.

## Alternatives considered

**Stay on PostgreSQL and add Flyway.** Defensible, and the honest competitor. Rejected because it
keeps the relational modelling cost for aggregates that are read and written whole, and because the
existing schema was built for a scope that no longer exists — most of the migration effort would
have gone into deleting it.

## Consequences

- Referential integrity moves into the application. Cross-collection references — a schedule
  entry's `courseCode`, a space's `buildingId` — are not enforced by the database.
- Auditing by database trigger is gone. If an audit trail is needed it must be built explicitly.
- Transactions require a **replica set**, even single-node. A standalone `mongod` throws
  *"Transaction numbers are only allowed on a replica set member or mongos"* at runtime rather than
  at startup. Compose runs `--replSet rs0`, and Testcontainers' `MongoDBContainer` does the same, so
  tests and local runs match.
- Images must be MongoDB 7.0 or newer: earlier lines publish no arm64 build and would run emulated
  on the Apple Silicon machines the team uses.
- `app/database/init.sql` was retained for reference only, as the record of the previous model. It was deleted in
  October 2026 (#51), since nothing read it; git keeps it.
