#!/usr/bin/env bash
# CONTRIBUTING.md is a verbatim copy of the K-Forge organization's canonical guide. Organization-level
# files are not included when a repository is cloned, so without this copy neither new members nor AI
# agents working in the checkout ever see the rules. Never edit the copy: change the canonical file in
# K-Forge/.github, merge it, then run this script and commit the result.
#
# raw.githubusercontent.com caches for about five minutes, so run it a few minutes after that merge.
set -eu

URL=https://raw.githubusercontent.com/K-Forge/.github/main/CONTRIBUTING.md

cd "$(git rev-parse --show-toplevel)"
curl -fsSL "$URL" -o CONTRIBUTING.md.tmp
mv CONTRIBUTING.md.tmp CONTRIBUTING.md

if git diff --quiet -- CONTRIBUTING.md; then
  echo "CONTRIBUTING.md already matches K-Forge/.github."
else
  git --no-pager diff --stat -- CONTRIBUTING.md
  echo "Updated. Commit it as: docs: sync contributing guide with the organization"
fi
