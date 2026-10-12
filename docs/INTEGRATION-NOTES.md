# Phase 1 rebuild brief

Phase 1 was built once and lost before it was committed. This file carries forward everything that
was learned, so the rebuild is a transcription rather than a rediscovery. Delete it once Phase 1 is
merged and the items below are resolved.

## What happened, so it does not happen again

The four tracks ran in git worktrees placed under a session scratchpad in `/private/tmp`, and the
agents were instructed not to commit. macOS purged the directory days later. The branches survived
but were empty: they still pointed at the commit they were created from.

**Rule for every future parallel run:** worktrees go somewhere persistent, and each track commits to
its own branch as it goes. A branch is the durable artifact; an uncommitted worktree is not. "Do not
commit" is only safe advice for work inside the main checkout.

Nothing from Phase 0 was affected — it was committed.

---

## 1. The shared security chain backs off too easily

`KappSecurityAutoConfiguration` declares its default `SecurityFilterChain` with
`@ConditionalOnMissingBean`. **Any** chain a service declares silently disables the shared one,
including a chain meant to cover a single path.

This was found the expensive way in `user-service`: adding a chain for `/internal/**` disabled the
default, and every `/api/users/**` route would have been left unauthenticated with nothing failing
loudly.

**Fix this in `common` first, before the tracks restart** — it is the one change that is cheaper to
make now than to work around four times:

- Give the shared chain `@Order(Ordered.LOWEST_PRECEDENCE)` and drop `@ConditionalOnMissingBean`, so
  it acts as a catch-all.
- Services add narrower chains with a `securityMatcher` and higher precedence for only the paths
  they own.
- Add a test that a service declaring its own narrow chain still rejects an anonymous request to an
  unrelated path. That failure mode is invisible otherwise.

**Done, with one correction worth keeping.** `CatchAllSecurityChainTest` in `user-service` is that
guard. Its first version declared its synthetic chain on `/internal/**`, which later collided with
the service's real `InternalApiSecurityConfig` and stopped the context from starting at all. A
regression guard must not claim a path the service might legitimately want, so it now uses
`/test-scoped/**` and stays independent of whatever concrete chains a service adds.

---

## 2. auth-service — decisions worth reproducing exactly

**`IdentityProviderPort`.** This shape was validated as Entra-compatible; reproduce it.

```java
String providerId();
boolean supports(IdentityAssertion assertion);
AuthenticatedIdentity authenticate(IdentityAssertion assertion);
```

`IdentityAssertion` sealed over `Password` and `AuthorizationCode` (the latter unimplemented, present
to prove the shape holds). `AuthenticatedIdentity` is `(subject, email, roles, emailVerified)` —
every field has an Entra equivalent (`oid`, `preferred_username`, app-role assignment, implicit).
No password hash, no Mongo type, no create/update method in the signature. `AuthService` must not
know that BCrypt or MongoDB exist.

**`emailVerified` and `status` are different facts.** Registration always sets
`emailVerified = false`, because nothing was proven. `status` decides whether the account can sign
in: `ACTIVE` when verification is not required, `PENDING_VERIFICATION` when it is. Login gates on
`status`, never on `emailVerified`. Conflating them makes an account claim a verification that never
happened, and once the flag is switched on those accounts are indistinguishable from genuinely
verified ones.

**Invitation codes.** Redemption is a single `findAndModify` with an
`$expr: {$lt: ["$timesUsed", "$maxUses"]}` guard, plus a guarded `release()` returning a use when the
registration it was claimed for fails. Proven with a 10-thread stampede on a single-use code:
exactly one success. Never read-then-write.

**Verification tokens.** 32 random bytes from `SecureRandom`, URL-safe base64. Store only a SHA-256
**hash**, so the collection holds nothing replayable if it leaks. Single use. `/verify/resend`
always returns 202 regardless of whether the account exists.

**Registration ordering.** Create the profile in user-service *before* writing the credential. A
failure then leaves a retryable orphan profile; the reverse leaves a credential that authenticates
into a void. 409 comes from the unique index, not from the pre-check.

**Seeded invitation codes are public.** `KL-20262-STUDENT` (200 uses) and `KL-20262-STAFF` (20 uses)
ship in a change unit so the MVP demos without an admin UI. Anyone who reads the repository can
create an account. Acceptable on a laptop, not acceptable once reachable — **revoke before any
public deployment** and put it on the pre-deployment checklist.

**Feign to user-service.** `InternalTokenInterceptor` wired in a non-`@Configuration` client config,
so the secret reaches exactly one downstream endpoint. 3s connect, 5s read, no retries, every Feign
failure surfaced as a 503 with a retry-safe message.

---

## 3. user-service — decisions worth reproducing exactly

**Accent-insensitive search that actually uses an index.** Store a derived `searchTokens` array:
every word of first name, last name and e-mail, NFD-decomposed with combining marks stripped, then
lowercased (`Muñoz` → `munoz`). Fold the query the same way and query with **anchored, flagless**
regexes (`/^munoz/`) against a multikey index.

Anchored and flagless is load-bearing: MongoDB can only turn a regex into an index range when it is
anchored at the start and carries no `i` flag. Both sides are already folded at write time, so no
flag is needed. `/muñoz/i` would use no index *and* still fail to match "munoz".

Recompute `searchTokens` in the document's compact constructor so the indexed field cannot drift from
the fields it indexes, including on documents read back from Mongo.

Prove it rather than asserting it: seed ~60 profiles, run MongoDB's `explain` over the exact filter
the service builds, assert the winning plan is an `IXSCAN` with few documents examined.

**Consequence to keep:** this matches word prefixes, not infixes. `varg` finds Vargas; `unoz` does
not. Correct for a type-ahead; infix matching cannot be served from an index. Tell the mobile team so
the UI does not promise otherwise.

**`IXSCAN` in the plan does NOT prove the query is efficient — MongoDB 7 will mislead you here.**
An unanchored, case-insensitive regex reports a winning plan of `IXSCAN`, not `COLLSCAN`: the server
walks the index over its **full unbounded key range**, which costs the same as a collection scan but
carries a reassuring stage name. A test asserting only "the plan contains IXSCAN" therefore passes
on exactly the query it was written to catch.

Assert the two things that actually distinguish them:

- the index bounds are **narrowed** rather than spanning `MinKey` to `MaxKey`
- `totalDocsExamined` is close to `nReturned` — for a search matching one profile out of sixty, both
  should be 1

`DirectorySearchIndexTest` in `user-service` does this and is the model to copy. Any future
performance assertion — `map-service`'s text search especially — should follow it rather than
checking the stage name.

**PATCH binds a raw `JsonNode`, not a record.** A record collapses "field absent" and "field sent as
null" into the same value, and the contract needs those to mean *leave alone* versus *clear*.

**Internal token comparison** hashes both sides to SHA-256 before `MessageDigest.isEqual`, so the
fixed-length digest hides the length of the supplied value as well as its content. An unset
`KAPP_INTERNAL_TOKEN` fails closed with a startup warning.

**E-mail is lowercased on the internal upsert**, and auth-service must agree on both sides. Without
it, a retry differing only in capitalisation slips past the unique index and creates the duplicate
the upsert exists to prevent.

**Test gotcha:** BSON stores instants at millisecond precision, so a seeded object never equals the
one read back. Truncate at the seam.

---

## 4. Contract divergences already settled

- `page`/`size` above the cap: the spec **rejects with 400**; do not clamp. Clamping tells a client
  asking for 5000 that it got everything when it got 100.
- `currentLevel` is 1–12 per the spec, not 1–20.
- PATCH `/me` also rejects `active` and `id`, and rejects unknown fields, per `additionalProperties: false`.
- Login returns 403 for a valid password against an unverified address. A narrow account oracle, but
  it is in the contract and knowing the password is already the harder half.
- *Historical:* `identification.number` (5–20 characters) and `phone` (E.164, `maxLength: 16`) were settled here
  too. Both fields left the profile with user 1.0 (#80): KApp stores no identity document or phone for members.

---

## 5. Track status at the time of loss

| Track | Service | State |
|---|---|---|
| A | auth-service | Complete, 30 tests green, minus the `emailVerified`/`status` split in section 2 |
| B | user-service | Complete, 38 tests green |
| C | semaphore-service | Partial — 7 seed tests green, integration tests not written |
| D | map-service | Partial — service built, tests and the pin editor not written |

Phase 0 is untouched and verified: full reactor builds, 27 integration tests, and the end-to-end
flow through the gateway (login → RS256 token → four services validating it independently against
the JWKS) was confirmed against the running stack.
