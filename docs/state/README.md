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

Since 2026-11-05 KApp is worked on from **local sessions on Brian's Mac only**. The cloud sessions were a trial
(2026-11-04 to 11-05): almost every change needs the Android emulator, Tailscale to the dev server or Docker, which
only the Mac has, and running both sets doubled the token cost. A cloud session is the exception, for a long task
while the Mac is off.

| Session | Role | Works on |
|---|---|---|
| Core \| Local | **Coordinates.** Plans, keeps this folder current, reviews every agent pull request, tells Brian what to merge, writes issues. Does not write feature code | `docs/`, `.github/`, `README.md`, `AGENTS.md`, `scripts/` for the repository |
| Backend \| Local | **Works** | `app/backend/`, `app/frontend/web-admin/`, `docs/api/`, `docs/map/`, `docs/pensums/` |
| Kotlin \| Local | **Works.** The only one that runs the emulator, and turns it off when done | `app/frontend/mobile/kotlin/` |
| Swift \| Local | **Works** (paused until Android is finished) | `app/frontend/mobile/swift/` |
| Segunda Entrega D.A.M. | Speaks for Brian about university deliveries and edits the shared `.docx`. Talks only to Core. Never runs the app | Outside the repo |

The Mac-only files (survey sketches, renders, the thesis proposal draft, `.env` files) are listed in `core.md`.

---

## One session, one purpose

Every message a session receives re-reads its whole history, so a long session spends more tokens on each turn than
a fresh one with the same knowledge. The knowledge does not live in the history: it lives in this folder, in the
issues and in the pull requests. So sessions are kept short, one purpose each.

**When to recommend a new session.** A session tells Brian, in one short line at the end of its reply, that it is
time to start a new one when any of these is true:

- the purpose it was opened for is done: its pull request is merged or ready, or its question is answered;
- the next task is a different purpose, even in the same area (a new issue, a new pull request);
- the conversation is long: it has been compacted, or it is past roughly 50 turns or a few hours of work.

**How it hands over.** Before recommending, the session:

1. updates its area's file in this folder: what is done, what is in flight with branch and PR numbers, and the exact
   next step;
2. pushes everything: work that exists only in a session is lost when it ends;
3. writes the **opening message for the new session** in its reply, in a code block Brian can copy: its name, its one
   purpose (an issue or PR number), and "read `docs/state/README.md` and `docs/state/<area>.md` first".

**What happens when Brian confirms.** The old session is archived, not left idle: in the Claude desktop app, Archive;
from the command line, `/exit`. A new session starts **with the same name** (Core \| Local, Backend \| Local, Kotlin
\| Local, Swift \| Local) and, where the app groups sessions, in the same group, with Remote Control on so Core and
Brian's phone can reach it. If the session has a tool that archives or starts sessions, it may do it itself after
Brian's yes; otherwise Brian does it. A session never archives itself without that yes.

Core recommends the same for itself. Its coordination does not depend on its history either: it reads this folder and
the open pull requests at the start of each session.

---

## How sessions talk

| From | To | Channel |
|---|---|---|
| a local session | another local session, Core included | `ListAgents`, then `SendMessage` to the session's name |
| a worker session | Core | `SendMessage` to "Core \| Local" when a task Core gave it is done: the result, links, what Brian must do |
| any session | everyone, durably | A GitHub issue or pull request comment. A closed session receives nothing, so anything that must survive goes to GitHub |

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
