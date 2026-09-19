#!/usr/bin/env bash
#
# Creates the four KApp accounts the development team signs in with.
#
#   scripts/create-dev-accounts.sh              # create the missing ones
#   scripts/create-dev-accounts.sh --recreate   # delete and re-create all four
#
# The passwords are generated ON THIS MACHINE, written to
# `app/backend/microservices/.dev-accounts` (gitignored) and printed once. They are never
# sent through a chat, an issue or a commit: anything said in one of those stays in its
# history forever, and rotating the password later does not remove it from there.
#
# These are DEVELOPMENT accounts on a database that lives on one laptop. They hold
# ROLE_ADMIN because the team is still building the thing and everyone needs to reach
# everything - there is deliberately no separation between "admin" and "developer" yet.
# That has to change before KApp is reachable from outside a laptop; it is recorded in
# docs/SECURITY-AUDIT.md as a pre-production item.
#
# Requires the `core` profile (or wider) to be up, and .env to hold the Mongo root
# password that provisioned the volume.
set -euo pipefail

# Edit this list to match the team.
#
# @kforge.dev, not the university's domain. These are development identities, not people:
# keeping them off konradlorenz.edu.co means one of them can never collide with somebody's
# real institutional address once KApp authenticates against Entra ID, and it makes a
# development account recognisable as one at a glance - in the user directory, in a log line,
# in a screenshot pasted into a chat.
#
# The domain is not registered to us and does not need to be. Nothing sends mail to it: e-mail
# verification is off, and these accounts are created ACTIVE. If verification is ever turned on,
# these four have to be reissued on a domain we control, or their confirmation mail goes to
# whoever owns kforge.dev.
ACCOUNTS=(
  "brian@kforge.dev|Brian Steven|Vargas Clavijo|506900001"
  "ivan@kforge.dev|Iván Darío|Ruiz Bernal|506900002"
  "alejandro@kforge.dev|Alejandro|Parada Estupiñán|506900003"
  "santiago@kforge.dev|Santiago|Rocha Ramírez|506900004"
)

GATEWAY="${KAPP_GATEWAY:-http://localhost:8080}"
PROGRAM_CODE="506"

# This script mints its own single-purpose invitation code and deletes it on the way out,
# rather than leaning on one of the seeded ones.
#
# Two reasons. The seeded codes are deactivated on purpose - they ship in a public repository,
# and while they were active anybody who could reach the gateway could create an account (see
# S11 in docs/SECURITY-AUDIT.md). And a script that quietly depends on a code somebody may have
# revoked fails later, confusingly, for a reason unrelated to what it is doing.
INVITATION_CODE="KF-BOOTSTRAP-$$"

RECREATE=false
[ "${1:-}" = "--recreate" ] && RECREATE=true

cd "$(dirname "$0")/../app/backend/microservices"

if [ ! -f .env ]; then
  echo "No .env here. Generate one with ../../../scripts/generate-dev-secrets.sh > .env" >&2
  exit 1
fi
set -a; . ./.env; set +a

if [ -z "${MONGO_ROOT_PASSWORD:-}" ]; then
  echo "MONGO_ROOT_PASSWORD is empty in .env. Promotion to ROLE_ADMIN needs it." >&2
  exit 1
fi

if ! curl -fsS --max-time 5 "$GATEWAY/auth/health" >/dev/null 2>&1; then
  echo "The gateway is not answering at $GATEWAY." >&2
  echo "Start it with: docker compose --profile core up -d" >&2
  exit 1
fi

# 28 characters, no symbols. Length carries the entropy (~166 bits) and the alphabet
# avoids every character that would need escaping in JSON, in a shell or in a URL - which
# is where these passwords are about to travel.
password() {
  ( set +o pipefail; LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 28 )
}

# Runs a script against ONE service's database, using that service's own credentials.
#
# There is deliberately no "connect as root and touch both databases" here any more. That
# worked only against the local container, which has a root user and is reachable as
# `mongo`; against Atlas there is no local container and — by design — no account that can
# reach both kapp_auth and kapp_user. Going through each service's own credential is the
# only thing that works in both places, and it is also the arrangement the isolation is
# supposed to have.
#
# mongosh runs in a throwaway container attached to the compose network, so a local URI
# (host `mongo`) resolves and an Atlas URI reaches the internet, without needing mongosh
# installed on anybody's machine.
mongo_for() {
  local service_upper="$1" script="$2"
  local uri_var="MONGO_${service_upper}_URI"
  # Indirect expansion WITHOUT a modifier: macOS ships bash 3.2, which rejects
  # ${!var:-default} as a bad substitution.
  local uri
  eval "uri=\${$uri_var}"

  if [ -z "$uri" ]; then
    echo "  $uri_var is not set in .env - cannot reach that service's database." >&2
    return 1
  fi

  docker run --rm --network "$COMPOSE_NETWORK" mongo:7 \
    mongosh "$uri" --quiet --eval "$script"
}

# The network the stack is on, asked of Compose rather than assumed: the network name comes
# from the project name, and the project name is KAPP_STACK - `back` here, `swift` in the iOS
# worktree - so no container name can be hardcoded.
AUTH_CONTAINER=$(docker compose ps -q auth-service 2>/dev/null | head -1)
COMPOSE_NETWORK=$([ -n "$AUTH_CONTAINER" ] && docker inspect "$AUTH_CONTAINER" \
  --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null || true)
if [ -z "$COMPOSE_NETWORK" ]; then
  echo "The stack does not appear to be running - start it first." >&2
  exit 1
fi

if [ "$RECREATE" = true ]; then
  emails=$(printf '%s\n' "${ACCOUNTS[@]}" | cut -d'|' -f1 | paste -sd',' -)
  echo "Deleting the four accounts so they can be created again."
  mongo_for AUTH '
    const emails = "'"$emails"'".split(",");
    const a = db.credentials.deleteMany({ email: { $in: emails } });
    print("  removed " + a.deletedCount + " credentials");
  '
  mongo_for USER '
    const emails = "'"$emails"'".split(",");
    const u = db.users.deleteMany({ email: { $in: emails } });
    print("  removed " + u.deletedCount + " profiles");
  '
  echo
fi

OUT=".dev-accounts"
: > "$OUT"
chmod 600 "$OUT"
{
  echo "# KApp development accounts, created $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "# Local development only. Gitignored on purpose. Rotate before KApp is reachable"
  echo "# from outside a laptop."
  echo
} >> "$OUT"

created=0
skipped=0

# Created directly in the database, because on a fresh cluster there is no administrator yet
# to create one through the API - which is the whole reason this script exists.
mongo_for AUTH '
  db.invitation_codes.insertOne({
    code: "'"$INVITATION_CODE"'", role: "ROLE_STUDENT",
    maxUses: '"${#ACCOUNTS[@]}"', timesUsed: 0, active: true, expiresAt: null,
    notes: "Temporary, created by create-dev-accounts.sh. Deleted when it finishes.",
    createdAt: new Date(), updatedAt: new Date()
  });
' > /dev/null

# However this script exits - success, a rate limit, a Ctrl-C - the code goes with it. A
# bootstrap code left behind and forgotten is exactly the finding this replaced.
cleanup_code() {
  mongo_for AUTH '
    db.invitation_codes.deleteOne({ code: "'"$INVITATION_CODE"'" });
  ' > /dev/null 2>&1 || true
}
trap cleanup_code EXIT

for entry in "${ACCOUNTS[@]}"; do
  IFS='|' read -r email first last student_code <<< "$entry"
  pw="$(password)"

  body=$(printf '{"email":"%s","password":"%s","firstName":"%s","lastName":"%s","invitationCode":"%s","studentCode":"%s","programCode":"%s"}' \
    "$email" "$pw" "$first" "$last" "$INVITATION_CODE" "$student_code" "$PROGRAM_CODE")

  response=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 \
    -X POST "$GATEWAY/auth/register" \
    -H 'Content-Type: application/json; charset=utf-8' \
    -d "$body" || echo "000")

  case "$response" in
    201)
      printf '  created   %s\n' "$email"
      printf '%s\n  %s\n\n' "$email" "$pw" >> "$OUT"
      created=$((created + 1))
      ;;
    409)
      printf '  exists    %s   (run with --recreate to reset its password)\n' "$email"
      printf '%s\n  (already existed; password unchanged and not known to this script)\n\n' "$email" >> "$OUT"
      skipped=$((skipped + 1))
      ;;
    429)
      echo "  RATE LIMITED after $created accounts. The gateway allows 10 credential" >&2
      echo "  attempts per minute. Wait a minute and run this again." >&2
      exit 1
      ;;
    *)
      echo "  FAILED    $email   HTTP $response" >&2
      exit 1
      ;;
  esac
done

# ROLE_ADMIN is never granted by an invitation code - the codes ship in the repository, so
# a code that granted admin would let anyone who can read the repo escalate. Promotion
# happens here instead, directly against the two collections that hold the role.
if [ "$created" -gt 0 ] || [ "$RECREATE" = true ]; then
  echo
  echo "Promoting them to ROLE_ADMIN:"
  emails=$(printf '%s\n' "${ACCOUNTS[@]}" | cut -d'|' -f1 | paste -sd',' -)
  mongo_for AUTH '
    const emails = "'"$emails"'".split(",");
    const a = db.credentials.updateMany(
      { email: { $in: emails } }, { $set: { roles: ["ROLE_ADMIN"] } });
    print("  " + a.modifiedCount + " credentials now ROLE_ADMIN");
  '
  mongo_for USER '
    const emails = "'"$emails"'".split(",");
    const u = db.users.updateMany(
      { email: { $in: emails } }, { $set: { role: "ROLE_ADMIN" } });
    print("  " + u.modifiedCount + " profiles now ROLE_ADMIN");
  '
fi

echo
echo "$created created, $skipped already existed."
echo "Passwords are in app/backend/microservices/$OUT (mode 600, gitignored)."
echo "Sign in at http://localhost:4300 or through $GATEWAY/auth/login."
if [ "$created" -gt 0 ]; then
  echo
  echo "Give each teammate their own line from that file over a private channel - not the"
  echo "group chat, and not a commit."
fi
