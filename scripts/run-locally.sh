#!/usr/bin/env bash
#
# Runs the whole KApp backend and the admin portal on this computer, with nothing of ours: no
# Atlas, no Microsoft tenant, no Tailscale. A local MongoDB (the `local-db` profile) holds the
# data, and a fake OIDC server stands in for Microsoft's sign-in. docs/RUN-LOCALLY.md is the
# walkthrough; on Windows without Git Bash, scripts/run-locally.ps1 does the same.
#
#   scripts/run-locally.sh            # up: secrets on first run, then build and start everything
#   scripts/run-locally.sh status     # what is running
#   scripts/run-locally.sh logs       # follow the logs
#   scripts/run-locally.sh down       # stop, keeping the data
#   scripts/run-locally.sh reset      # stop and delete the data and the secrets: the next `up` starts over
#   scripts/run-locally.sh secrets    # only write app/backend/microservices/.env, if it is not there
#
# Needs Docker with Compose 2.20 or later (Docker Desktop does), and bash with openssl, which
# macOS, Linux and Git for Windows all have.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
DIR="$REPO/app/backend/microservices"
STACK=local
PROFILES=(--profile local-db --profile academic --profile map --profile dev)

compose() {
  # Always the `kapp-local` project, whatever .env says, so a stop or a reset can never touch
  # the containers of a teammate's stack on the same computer.
  (cd "$DIR" && KAPP_STACK=$STACK KAPP_PROJECT=kapp-$STACK docker compose "${PROFILES[@]}" "$@")
}

secrets() {
  if [ -f "$DIR/.env" ]; then
    if ! grep -q "^MONGO_ROOT_PASSWORD=." "$DIR/.env"; then
      echo "app/backend/microservices/.env exists and is not a local one: it points at another database." >&2
      echo "Move it aside to run locally; this script never overwrites it." >&2
      exit 1
    fi
    return 0
  fi
  echo "Writing app/backend/microservices/.env: secrets generated on this computer, for it alone."
  bash "$REPO/scripts/generate-dev-secrets.sh" --local-db "$STACK" > "$DIR/.env"
  bash "$REPO/scripts/generate-jwt-keys.sh" > /dev/null
}

need_docker() {
  if ! docker info > /dev/null 2>&1; then
    echo "Docker is not running. Start Docker Desktop (or the Docker daemon) and try again." >&2
    exit 1
  fi
  local version
  version="$(docker compose version --short 2>/dev/null || true)"
  version="${version#v}"
  case "$version" in
    "" ) echo "Docker Compose v2 is missing: install Docker Desktop, or the compose plugin." >&2; exit 1 ;;
    1.*|2.[0-9].*|2.1[0-9].* ) echo "Docker Compose $version is too old: 2.20 or later is needed." >&2; exit 1 ;;
  esac
}

value() {   # a value from .env, quotes removed
  sed -n "s/^$1=//p" "$DIR/.env" | tail -n 1 | tr -d "'\""
}

wait_for() {   # url, what, minutes
  local url=$1 what=$2 tries=$(( $3 * 12 ))
  printf 'Waiting for %s ' "$what"
  for _ in $(seq "$tries"); do
    if curl -fsS -o /dev/null "$url" 2>/dev/null; then
      echo "- up."
      return 0
    fi
    printf '.'
    sleep 5
  done
  echo
  echo "$what did not answer at $url. See: scripts/run-locally.sh logs" >&2
  return 1
}

up() {
  need_docker
  secrets
  echo "Building and starting the stack. The first time takes a while: Maven builds six services."
  compose up -d --build
  local gateway portal oidc
  gateway="$(value KAPP_GATEWAY_PORT)"; gateway="${gateway:-8080}"
  portal="$(value KAPP_PORTAL_PORT)"; portal="${portal:-4300}"
  oidc="$(value KAPP_FAKE_OIDC_PORT)"; oidc="${oidc:-8090}"
  wait_for "http://localhost:$gateway/auth/health" "the backend" 15
  wait_for "http://localhost:$portal/health" "the admin portal" 5
  cat <<EOF

KApp is running on this computer.

  API (gateway)     http://localhost:$gateway        the Android emulator reaches it at http://10.0.2.2:$gateway
  Admin portal      http://localhost:$portal
  Sign-in (fake)    http://localhost:$oidc/kapp      stands in for Microsoft

Sign in with Microsoft and type one of these as the user name; there is no password:

  estudiante        a student, with the test SINU's record, timetable and semaforo
  profesor          a professor
  administrativo    staff, and the portal's administrator

The app: docs/RUN-LOCALLY.md, "The Android app". Stop with: scripts/run-locally.sh down
EOF
}

case "${1:-up}" in
  up) up ;;
  secrets) secrets ;;
  status) need_docker; compose ps ;;
  logs) need_docker; compose logs -f --tail=60 ;;
  down) need_docker; compose down ;;
  reset)
    need_docker
    compose down -v
    # Only a local .env: the team's points at Atlas and is never this script's to delete.
    if [ -f "$DIR/.env" ] && grep -q "^MONGO_ROOT_PASSWORD=." "$DIR/.env"; then
      rm -f "$DIR/.env"
    fi
    echo "Stopped, and the local data and secrets deleted. The next 'up' starts over."
    ;;
  *) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
