# ADR 0010 — A local database for evaluation

- **Status:** Accepted and implemented
- **Date:** 2026-11-18
- **Amends:** [ADR 0009](0009-atlas-is-the-only-development-database.md), which stays the team's rule

## Context

[ADR 0009](0009-atlas-is-the-only-development-database.md) made the shared Atlas cluster the only
development database. For the team that still holds. But KApp also has to run where none of our
accounts can go. The course's final delivery asks for the backend, "the database and the scripts to
create and configure it", working with the app on the professor's computer. Neither the cluster
nor our Microsoft tenant can be handed over: their credentials would leave the team, and the data
on the cluster is the team's working copy.

The code is delivered as a ZIP made with `git archive`, so nothing untracked, no `.env`, goes with it.

## Decision

**A `local-db` Compose profile brings back a MongoDB container, for evaluation and offline runs
only.**

- **The container:** a single-node replica set, as Atlas and the tests run. map-service saves a
  floor's layout in a transaction, which a standalone server refuses. It has one database and one
  account per service, as on Atlas ([ADR 0005](0005-per-service-database-credentials.md)).
  `mongo-init/rs-init.js` creates the accounts from its health probe; the services' Mongock
  migrations create and fill the databases on their first start.
- **One command sets it up:** `scripts/run-locally.sh`, or `scripts/run-locally.ps1` from
  PowerShell. It writes an `.env` whose every secret is generated on that computer
  (`generate-dev-secrets.sh --local-db`, `generate-jwt-keys.sh`), then starts the backend, the
  database, the fake OIDC server in place of Microsoft's sign-in, and the admin portal. It always
  uses its own Compose project, `kapp-local`, and never overwrites an `.env` that points at Atlas.
- **The team's default does not change.** No other profile starts the container. The services'
  `MONGO_*_URI` stay required, and the team's `.env` points them at the cluster.

The [walkthrough](../RUN-LOCALLY.md) is written for someone with the code, Docker and nothing else.

## Consequences

- **What ADR 0009 removed comes back, scoped to evaluation:** the container, its keyFile, the
  user-provisioning probe and the local passwords in `generate-dev-secrets.sh`. It is used by people
  outside the team, so it has to keep working without anyone on the team running it daily. The
  clean-clone check in the walkthrough is how to know it still does.
- **Two databases again, but not two ways of working.** A team member never starts `local-db`. Its
  data is seeded the same way as the cluster's, by the same migrations, so what an evaluator sees is
  what the team built, not a copy that drifted.
- **The academic data does not need a database at all:** it comes from the test SINU built into the
  services, the same on the cluster and locally.
