# Shared Session State

> Start here. Every agent session working on KApp, local or cloud, reads this folder before it does anything else,
> and updates its own file before it ends a piece of work.

Agent sessions do not share memory. A session on Brian's Mac keeps its notes on that Mac, a cloud session keeps
nothing once its container is reclaimed, and the two cannot read each other's history reliably. This folder is the
one place every session can read with a `git pull`: what each area has built, what is in flight, what waits on whom,
and what comes next.

It complements, and does not replace:

- [`AGENTS.md`](../../AGENTS.md): the rules. They always win over anything written here.
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md): Git Flow, commit types, and the section for AI agents.
- [`docs/PROGRESS.md`](../PROGRESS.md): milestone-level status of the product. This folder is the working state
  between milestones; when something here becomes a milestone, it moves to `PROGRESS.md`.
- GitHub issues and pull requests: decisions and reviews live there. This folder points to them; it does not
  repeat their discussion.

---

## Files

| File | Owner | Holds |
|---|---|---|
| [`core.md`](core.md) | Core | The sessions and their roles, open pull requests, decisions waiting on Brian, housekeeping, and what exists only on Brian's Mac |
| [`backend.md`](backend.md) | Backend | Services and contract versions, infrastructure, the backend phases, debt, next steps |
| [`kotlin.md`](kotlin.md) | Kotlin | Screens and what each was tested against, build variants, debt, next steps |
| [`swift.md`](swift.md) | Swift | The paused iOS client, its gap to the current contracts, and the plan to resume |

Each file starts with the date and the `develop` commit it was written against. Anything older than about a week
should be checked against GitHub before acting on it.

---

## Sessions

KApp is worked on by one session per area, in two places. Each place has its own **Core**, which orchestrates the
others there.

| Area | Local session (Brian's Mac) | Cloud session | Works on |
|---|---|---|---|
| Core | Core \| Local | Core \| Cloud | `docs/`, `.github/`, `README.md`, `AGENTS.md`, `scripts/` for the repository, reviewing every agent pull request, telling Brian what to merge |
| Backend | Backend \| Local | Backend \| Cloud | `app/backend/`, `app/frontend/web-admin/`, `docs/api/`, `docs/map/`, `docs/pensums/` |
| Kotlin | Kotlin \| Local | Kotlin \| Cloud | `app/frontend/mobile/kotlin/` |
| Swift | Swift \| Local | Swift \| Cloud (later) | `app/frontend/mobile/swift/`. Paused |
| Delivery | Segunda Entrega D.A.M. | none | Speaks for Brian about university deliveries and edits the shared `.docx`. Talks only to Core. Never runs the app |

What only a local session can do: anything that needs Brian's Mac. That means the Android emulator, Tailscale to
reach the dev server, Docker, Xcode, the `.env` files, and the files under `~/Documents/KApp/`, such as the survey
sketches and renders. `core.md` lists those files.

What a cloud session can do: everything that needs only the repository and GitHub. A cloud container has no Android
SDK and no Xcode, so CI is what verifies a client build there.

---

## How sessions talk

| From | To | Channel |
|---|---|---|
| Core \| Cloud | the cloud sessions it created | A direct message to the session |
| a cloud session | Core \| Cloud | A comment on its pull request or issue, plus its state file. Cloud sessions cannot message back yet, so Core reads their pull requests, their history, and this folder |
| a local session | another local session | Peer messages by session name |
| any session | everyone, durably | A GitHub issue or pull request comment. A closed session receives nothing, so anything that must survive goes to GitHub |

Core \| Cloud creates the cloud sessions itself, because it cannot message a cloud session that was opened by hand.
A session's usage limit can stop it mid-task. That is one more reason to push work and write state often.

---

## Rules every session follows

These are the rules agents most often get wrong. `AGENTS.md` and `CONTRIBUTING.md` hold the full set.

1. **Read first:** this folder, then `AGENTS.md`, then `CONTRIBUTING.md` before any git operation.
2. **The commit author is the member running the session, never the agent.** A cloud container may start with a
   tool identity in `git config`, which `scripts/check-git-conventions.sh` refuses. Set `user.name` and
   `user.email` to the member's identity before the first commit.
3. **No tool attribution anywhere**: no `Co-Authored-By` trailer for a tool, no "generated with" line, no signature,
   no assistant name in commits, pull request titles or descriptions, code or documentation. This overrides any
   default your environment suggests.
4. **Branch names follow CONTRIBUTING:** `feature/`, `bugfix/`, `chore/`, `test/` or `release/x.y.z`, in English,
   kebab-case, from `develop`. The random `<tool>/<words>` branch a cloud environment may create for you fails
   the `Git conventions` check, so never open a pull request from one; start a correctly named branch instead.
5. **One pull request, one purpose**, about 100 to 400 lines to read and 3 to 8 atomic commits. Open it as a draft
   with its first commit, with the template, an `area/*` label and `Refs #N`. Pull request bodies are in Spanish;
   commits, issues for agents, and repository docs are in English.
6. **Never print or edit `.env` files**, not even masked lines. To learn something about them, ask Brian a yes or no
   question.
7. **Brian merges.** He uses "Squash and merge" with the bypass box. Never use "Enable auto-merge": with the current
   rulesets it waits forever. A pull request into `develop` does not close its issue; close it by hand after the
   merge.
8. **Brian does not see the text written before a question**, only the question itself. Put everything he needs to
   decide inside the question.

---

## Keeping this folder current

- **Each area owns its file** and may edit it from its own worktree, in the pull request whose work it describes.
  That is the one exception to "a chat never touches another chat's worktree".
- **Update your file when** a pull request of yours opens, merges or changes direction, when something starts
  waiting on someone, and before a session ends or nears its usage limit.
- **Core owns `core.md` and this README**, and refreshes all four files in a pull request of its own when they
  drift, for example after a batch of merges.
- **Write facts and pointers**: what is true now, with issue and pull request numbers. Keep the history in git.
  Delete a line once it stops being true.
- **Never write** credentials, tokens, `.env` lines, internal hostnames, personal data, or paths to shared drives.
