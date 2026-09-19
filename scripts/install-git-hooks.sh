#!/usr/bin/env bash
# Points git at the versioned hooks in .githooks/. Run it once per clone: the setting lives in the
# clone's config, so every worktree of that clone picks it up.
set -eu

cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
chmod +x .githooks/* scripts/check-git-conventions.sh

echo "Hooks enabled: every commit is now checked against CONTRIBUTING.md."
echo "The same check runs in CI on every pull request, so skipping it locally only moves the failure."
