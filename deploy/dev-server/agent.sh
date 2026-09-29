#!/usr/bin/env bash
# Keeps a KApp dev server on the images CI published for its branch. Run by kapp-dev-agent.timer
# every two minutes, as root, from /opt/kapp; safe to run by hand.
#
# 1. Pulls the branch's images (.github/workflows/dev-images.yml tags them with the branch).
# 2. If any changed, checks that each was built from a commit on that branch, by the label CI puts
#    on it and the repository's public history. An image tagged with the branch by any other
#    build is refused, and nothing restarts.
# 3. Restarts what changed, and drops the images it replaced - only KApp's, never another's.
#
# It never changes compose.yaml or .env: what the containers may do on the host is decided at
# install, by a person, not by whoever pushes next. A server stopped on purpose - README.md's
# `stop` leaves /opt/kapp/.stopped - stays stopped.
set -euo pipefail
cd "$(dirname "$0")"
[[ -e .stopped ]] && exit 0

setting() { sed -n "s/^$1=//p" .env | tail -1; }
REPO=$(setting KAPP_DEV_REPO); REPO=${REPO:-K-Forge/KApp}
BRANCH=$(setting KAPP_DEV_BRANCH)
[[ -n "$BRANCH" ]] || { echo "KAPP_DEV_BRANCH is not set in /opt/kapp/.env" >&2; exit 1; }

images() { docker compose config --images | sort; }
ids() { images | while read -r img; do printf '%s %s\n' "$img" "$(docker image inspect -f '{{.Id}}' "$img" 2>/dev/null || echo none)"; done; }

before=$(ids)
docker compose pull -q
after=$(ids)
if [[ "$before" == "$after" ]] && [[ -n "$(docker compose ps -q --status running)" ]]; then
  exit 0
fi

# Built from the branch? The compare API says "behind" or "identical" when the image's commit is in
# the branch's history. Unauthenticated, it allows 60 calls an hour: only a changed image costs one.
head=$(curl -fsS -H 'Accept: application/vnd.github.sha' "https://api.github.com/repos/$REPO/commits/$BRANCH")
while read -r img; do
  rev=$(docker image inspect -f '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$img")
  status=$(curl -fsS "https://api.github.com/repos/$REPO/compare/$head...$rev" \
    | python3 -c 'import json, sys; print(json.load(sys.stdin)["status"])')
  if [[ "$status" != behind && "$status" != identical ]]; then
    echo "refusing $img: built from $rev, which is not on $BRANCH (at $head): $status" >&2
    exit 1
  fi
done < <(comm -13 <(printf '%s\n' "$before") <(printf '%s\n' "$after") | cut -d' ' -f1)

docker compose up -d --remove-orphans
docker image prune -f --filter "label=org.opencontainers.image.source=https://github.com/$REPO" >/dev/null
echo "on $BRANCH at ${head::7}: $(docker compose ps --format '{{.Service}}={{.Status}}' | tr '\n' ' ')"
