#!/usr/bin/env bash
# Deletes the local kind cluster scripts/k8s-up.sh created, and everything in it.
#
# Nothing is lost: the data lives in Atlas, the images stay in Docker, and k8s-up.sh recreates
# the cluster in a couple of minutes.
set -euo pipefail

command -v kind >/dev/null 2>&1 || { echo "kind is not installed, so there is no cluster to delete." >&2; exit 0; }

if kind get clusters 2>/dev/null | grep -qx kapp; then
  kind delete cluster --name kapp
else
  echo "There is no kind cluster named kapp."
fi
