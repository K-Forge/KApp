#!/usr/bin/env bash
#
# Proves that the separation between the services' databases is enforced by MongoDB and
# not merely respected by the code.
#
#   scripts/verify-db-isolation.sh
#
# For each service account it checks two things: that the account can read
# and write its OWN database, and that it is refused on the other four. A credential that
# leaks out of one service must not open another service's data.
#
# Needs nothing running: it connects to the Atlas cluster with each service's own string
# from `.env`, through a throwaway mongosh container. Run it after any change to the
# cluster's database users.
set -euo pipefail

cd "$(dirname "$0")/../app/backend/microservices"

if [ ! -f .env ]; then
  echo "No .env here. Generate one with ../../../scripts/generate-dev-secrets.sh > .env" >&2
  exit 1
fi
set -a; . ./.env; set +a

ALL_DBS="kapp_auth kapp_user kapp_semaphore kapp_schedule kapp_map"

# Every try/catch below is at the TOP LEVEL of the mongosh program, including the ones
# inside the loop. mongosh does not rewrite the body of an ordinary function to await the
# driver's promises, so a `catch` written inside a function never fires and the error
# escapes instead. See the header of mongo-init/rs-init.js.
read -r -d '' PROBE <<'JS' || true
const OWN = process.env.OWN_DB;
const ALL = process.env.ALL_DBS.split(" ");
let failures = 0;

// No auth() call: the connection string carries the credential and its authSource, which
// is the service's own database. If it were wrong we would not have a connection at all.
for (const name of ALL) {
  let allowed = false;
  let detail = "";
  try {
    db.getSiblingDB(name).isolation_probe.findOne();
    allowed = true;
  } catch (e) {
    detail = e.codeName || e.message;
  }

  if (name === OWN) {
    // readWrite, not read: the service has to be able to write its own data.
    let wrote = false;
    try {
      db.getSiblingDB(name).isolation_probe.insertOne({ at: new Date() });
      db.getSiblingDB(name).isolation_probe.drop();
      wrote = true;
    } catch (e) {
      detail = e.codeName || e.message;
    }
    if (allowed && wrote) {
      print("  allowed  " + name + "   (its own database, read and write)");
    } else {
      print("  FAILURE  " + name + "   should be readable and writable: " + detail);
      failures++;
    }
  } else {
    if (allowed) {
      print("  FAILURE  " + name + "   READ IT. The databases are not isolated.");
      failures++;
    } else {
      print("  refused  " + name + "   (" + detail + ")");
    }
  }
}

quit(failures === 0 ? 0 : 1);
JS

status=0
for svc in auth user semaphore schedule map; do
  own="kapp_${svc}"
  uri_var="MONGO_$(printf '%s' "$svc" | tr '[:lower:]' '[:upper:]')_URI"
  # Indirect expansion WITHOUT a modifier: macOS ships bash 3.2, which rejects
  # ${!var:-default} as a bad substitution.
  eval "uri=\${$uri_var}"

  if [ -z "${uri:-}" ]; then
    echo "kapp_${svc}: $uri_var is not set in .env - see docs/ATLAS-SETUP.md" >&2
    status=1
    continue
  fi

  echo "kapp_${svc}_user:"
  # The connection string goes in as an argument to a throwaway container that nothing else
  # shares, and the probe reads only the two database names from the environment.
  if ! docker run --rm -e OWN_DB="$own" -e ALL_DBS="$ALL_DBS" mongo:7 \
      mongosh "$uri" --quiet --eval "$PROBE"; then
    status=1
  fi
  echo
done

if [ "$status" -eq 0 ]; then
  echo "Every service reaches its own database and no other."
else
  echo "Isolation is NOT holding. See the failures above." >&2
fi
exit "$status"
