# ADR 0008 — Invitation codes are temporary, and this is how they go away

- **Status:** Accepted. Step 3 done on 2026-11-25 (#255): the codes are gone, and registration,
  development only, needs none.
- **Date:** 2026-09-11

## Context

Registration requires an invitation code. The question that prompted this was a good one: *"it is
a temporary feature that is not in the final design, only while we test for the first months —
how do we get rid of it afterwards? Do we just leave the code blank?"*

**No, and the reason matters more than the answer.** Leaving it blank is not even expressible:
`invitationCode` is `@NotBlank` on `RegistrationRequest`, so an empty one is a `400`. And making
it optional to achieve it would reopen exactly the finding that made us deactivate the seeded
codes — see S11 in `SECURITY-AUDIT.md`. Registration would be open to anyone who can reach the
gateway with an address on an allowed domain.

## The thing that is easy to miss

**The code does two jobs, not one.**

1. It authorises the registration.
2. It **decides the role**. `RegistrationService` calls `roleFrom(code)`, and that is the only
   thing in the system that distinguishes a student from a professor at signup.

Remove the code without a replacement and you do not get an open door — you get a system that
cannot tell who anybody is.

## Decision

**What gets retired is local registration, not the code.** The code is the lock on a door that
gets bricked up. Entra ID answers both questions the code answers today: the university's tenant
authorises, and a group or app-role claim carries the role. `EntraIdAdapter` already exists and
implements `IdentityProviderPort`; it throws `UnsupportedOperationException` because there is no
application registration yet.

### The three steps, in order

**1. Codes are generated, not chosen.** *(done)* An administrator can no longer type a code.
`InvitationCodeService.nextCode()` mints `KL-` plus two groups of four from an alphabet with no
`O`/`0` or `I`/`1`. This is worth doing on its own: `KL-20262-STUDENT` is one guess away from
`KL-20262-STAFF`, which is how a code that ships in a public repository stops being a secret.

**2. Local registration gets a switch.** *(not built)* A property —
`kapp.auth.local-registration-enabled`, default `true` — that makes `POST /auth/register` answer
`404` when it is off. A flag rather than a deletion because the switch-over day is exactly when
you want to be able to change your mind from a `.env` file, and because Entra ID will be tested
against a running system before it is trusted with everybody.

**3. The code disappears.** *(not built)* Once nothing depends on it: delete the endpoint, the
`invitation_codes` collection, `InvitationCodeService`, the portal screen, and the
`invitationCode` field on `RegistrationRequest`. One commit, and the contract's minor version
goes up.

## Consequences

**Accounts created during the trial are `Provider.LOCAL` and carry a password hash.** On the day
Entra ID lands they have to be matched to their tenant identity by e-mail, or deleted. Leaving
them is the bad option: two ways into the same person, one of them with a password KApp stores.

**If Entra ID does not arrive before November, nothing breaks.** The codes stay through the
defence. They are a trial feature, documented as one, and the university is what gates the
replacement — not this repository. The real question was never "how do we remove it" but "what
replaces it", and that answer is blocked outside our control.

**`ROLE_ADMIN` is never grantable by a code and that does not change at any step.** See
`InvitationCodeService.create`.
