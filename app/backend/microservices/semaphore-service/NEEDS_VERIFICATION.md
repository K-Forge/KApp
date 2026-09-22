# Needs verification

Findings from finishing `semaphore-service` that a human with access to the university's
records (or to the published `docs/api/*.yaml` contracts) needs to resolve. Nothing below was silently patched over: the code and the
seed match what is written here, discrepancies included.

## 1. The seeded pensum 1015 was a reconstruction — resolved

`V002` seeded Ingeniería de Sistemas from a drawing: 48 items, most under invented `IS-*` codes,
summing to 144 credits and 197 weekly hours against a declared 142 and 194. `V006` replaces it with
the printed 2019-1 grid — 51 items, every one with its institutional code, 143 credits and 194 weekly
hours — and loads the other 22 plans the university publishes.

What the published PDFs still cannot answer (institutional codes for most programs, per-course
credits and hours missing from several brochures, half hours, prerequisites between electives) is
listed in [`docs/pensums/README.md`](../../../../docs/pensums/README.md#what-the-pdfs-cannot-answer).

## 2. `userId` type contradicts across the two published contracts

`docs/api/semaphore.openapi.yaml` types `StudentProgress.userId` and the
`GET /api/semaphore/{userId}` path parameter as `integer`/`format: int64`. But the value
that actually flows through the system is the JWT `sub` claim, which `auth-service`
mints as `user-service`'s own profile id - a string (`docs/api/user.openapi.yaml` types
`UserProfile.id` as a UUID string). A UUID does not fit in an `int64`, and treating the
subject as a number anywhere would require parsing a value that is not numeric.

This service implements `userId` as `String` everywhere - `StudentProgress.userId`,
`StudentProgressDto.userId`, `StudentProgressWithReconciliationDto.userId`, and the
`{userId}` path variable on `GET /api/semaphore/{userId}` - matching the *user* contract
and runtime reality rather than the *semaphore* contract's declared type. This is a
deliberate deviation from "match it exactly," recorded here because fixing it properly
means editing `docs/api/semaphore.openapi.yaml` (out of scope for this worktree, and a
breaking change for any client that already reads `userId` as a number) rather than
silently working around it in code.

**Action needed:** whoever owns the OpenAPI contracts should change
`docs/api/semaphore.openapi.yaml`'s `userId` to a string type, consistent with
`docs/api/user.openapi.yaml`.

## 3. Two design decisions for an underspecified edge case

Not bugs, but worth a second pair of eyes since the spec is silent on both:

- **`ProgressSummaryDto.currentLevel` once nothing is left unfinished.** The spec defines
  it as "the lowest level that still holds an unfinished item," which has no answer once
  every item is `PASSED`. This implementation falls back to `pensum.levels()` (the
  plan's last level) rather than `0` or the stored `StudentProgress.currentLevel`. See
  `StudentProgressService.getSummary`.
- **`StudentProgress.currentLevel` vs. the derived summary field of the same name are
  two different things.** The stored field is seeded once, at lazy creation, from
  `user-service`'s `academic.currentLevel` (defaulting to `1` if absent) and is not
  touched again by anything in this service. `ProgressSummaryDto.currentLevel` is
  recomputed from the semáforo on every call. They are expected to disagree once a
  student is partway through a level without every prior item settled - that is not a
  bug in either one.
