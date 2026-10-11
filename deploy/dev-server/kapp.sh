#!/usr/bin/env bash
# kapp: the dev server by hand, from the host itself - an SSH session from a phone is enough.
# install.sh puts it in /usr/local/bin/kapp.
#
#   kapp status          what runs, how much each takes, and how much the host has left
#   kapp stop            stop the whole server, the map's four and the extras; it stays stopped
#   kapp start           start the map's four again, and the extras if they were on
#   kapp extras on       start users and the semaphore (users, programs, pensums), if there is room
#   kapp extras off      stop them again
#   kapp extras          whether they are on
#
# The extras are off unless switched on. They ask the host for up to ~550 MB more, so `on` checks
# it has that and a margin before starting them, and the agent switches them off on its own if the
# host later runs short: whatever else the host runs comes first.
set -euo pipefail
cd /opt/kapp 2>/dev/null || { echo "KApp is not installed here (/opt/kapp)" >&2; exit 1; }
[[ $EUID -eq 0 ]] || exec sudo "$0" "$@"

EXTRAS=(user-service semaphore-service)
# What the two may take (their mem_limit) and what the host keeps besides: below this, no.
NEED_MB=$((256 + 288 + 400))

available_mb() { awk '/^MemAvailable:/ {print int($2 / 1024)}' /proc/meminfo; }
is_on() { [[ -e .extras ]]; }

status() {
  COMPOSE_PROFILES=extras docker compose ps --format 'table {{.Name}}\t{{.Status}}'
  echo
  docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}' | grep -E 'NAME|kapp-' || true
  echo
  echo "extras: $(is_on && echo on || echo off)   available on the host: $(available_mb) MB"
  [[ -e .extras-auto-off ]] && echo "($(cat .extras-auto-off))"
  [[ -e .stopped ]] && echo "(the whole server is stopped on purpose)"
  return 0
}

extras_on() {
  [[ -e .stopped ]] && { echo "The server is stopped: 'kapp start' first." >&2; exit 1; }
  for key in MONGO_USER_URI MONGO_SEMAPHORE_URI; do
    grep -q "^$key=." .env || { echo "$key is missing in /opt/kapp/.env: run kapp-dev.sh install from the Mac" >&2; exit 1; }
  done
  local free
  free=$(available_mb)
  if (( free < NEED_MB )); then
    echo "Not now: the host has $free MB available and the extras need $NEED_MB MB with its margin." >&2
    echo "Whatever else runs here comes first. Try again when it has more room." >&2
    exit 1
  fi
  echo "Pulling users and the semaphore..."
  if ! COMPOSE_PROFILES=extras docker compose pull -q "${EXTRAS[@]}"; then
    echo "Their images are not published for this branch yet: GitHub Actions builds them on the next push." >&2
    exit 1
  fi
  touch .extras
  rm -f .extras-auto-off
  COMPOSE_PROFILES=extras docker compose up -d "${EXTRAS[@]}"
  echo -n "Starting (about a minute)"
  for _ in $(seq 1 60); do
    healthy=$(COMPOSE_PROFILES=extras docker compose ps --format '{{.Service}} {{.Health}}' "${EXTRAS[@]}" | grep -c ' healthy$' || true)
    [[ "$healthy" == 2 ]] && { echo; echo "On: users, programs and pensums answer through the gateway."; return 0; }
    echo -n "."
    sleep 5
  done
  echo
  echo "Still starting; 'kapp status' shows how they are doing."
}

stop_all() {
  # The agent leaves a stopped server alone, so it does not start it again two minutes later.
  touch .stopped
  COMPOSE_PROFILES=extras docker compose stop
  echo "Stopped: the whole server is down until 'kapp start'. The host's other work is untouched."
}

start_all() {
  rm -f .stopped
  local profiles=''
  is_on && profiles=extras
  COMPOSE_PROFILES=$profiles docker compose up -d
  echo "Started: the map's four$(is_on && echo ' and the extras'). The gateway answers in about a minute."
}

extras_off() {
  rm -f .extras .extras-auto-off
  COMPOSE_PROFILES=extras docker compose rm -sf "${EXTRAS[@]}" >/dev/null 2>&1 || true
  echo "Off: users and the semaphore are stopped. The map, the portal and signing in keep running."
}

case "${1:-status} ${2:-}" in
  "status "*) status ;;
  "stop "*) stop_all ;;
  "start "*) start_all ;;
  "extras on") extras_on ;;
  "extras off") extras_off ;;
  "extras "|"extras status") echo "extras: $(is_on && echo on || echo off)   available on the host: $(available_mb) MB" ;;
  *)
    sed -n '2p;4,11p' "$0" | sed 's/^# \{0,1\}//'
    exit 2 ;;
esac
