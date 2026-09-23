#!/usr/bin/env bash
# Runs KApp on a local Kubernetes cluster (kind): the same images as the Compose stack, as
# Deployments and Services described in deploy/k8s/.
#
#   scripts/k8s-up.sh            # uses the images of your Compose stack (KAPP_STACK, default back)
#   scripts/k8s-up.sh --build    # builds them first
#
# Then the gateway is at http://localhost:8080 and the portal at http://localhost:4300, as under
# Compose. scripts/k8s-down.sh deletes the cluster.
#
# What it does, in order: refuses to run next to the Compose stack, creates the cluster if it
# does not exist, loads the images into it, creates the kapp-secrets Secret from .env, applies
# the manifests and waits until every Deployment is available.
#
# The Secret never exists as a file: the values go from .env to kubectl through a pipe, and
# nothing here prints them. Point KAPP_ENV_FILE at another file to use other credentials.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MICRO="$ROOT/app/backend/microservices"
K8S="$ROOT/deploy/k8s"
CLUSTER=kapp
NAMESPACE=kapp
ENV_FILE="${KAPP_ENV_FILE:-$MICRO/.env}"

SERVICES=(discovery-server api-gateway auth-service user-service semaphore-service schedule-service map-service)
IMAGES=("${SERVICES[@]}" web-admin)

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "$1 is not installed. $2" >&2; exit 1; }
}
need docker "Docker Desktop has to be running."
need kind "Install it with: brew install kind"
need kubectl "Install it with: brew install kubectl"

[ -f "$ENV_FILE" ] || { echo "No $ENV_FILE - the services need their MONGO_*_URI. See docs/ATLAS-SETUP.md." >&2; exit 1; }

# The stack name decides which images are used, exactly as it does for Compose.
STACK="$(set -a; . "$ENV_FILE"; set +a; echo "${KAPP_STACK:-back}")"

# Both publish localhost:8080 and localhost:4300, and a laptop has memory for one of them.
if docker ps --format '{{.Names}}' | grep -Eq "^${STACK}-(api-gateway|web-admin)$"; then
  echo "The Compose stack '$STACK' is running. Stop it first:" >&2
  echo "  cd app/backend/microservices && docker compose --profile full --profile dev stop" >&2
  exit 1
fi

if [ "${1:-}" = "--build" ]; then
  echo "Building the images (stack $STACK)..."
  (cd "$MICRO" && KAPP_STACK="$STACK" docker compose --env-file "$ENV_FILE" \
    --profile full --profile dev build "${IMAGES[@]}")
fi

for image in "${IMAGES[@]}"; do
  docker image inspect "kapp/$image:$STACK" >/dev/null 2>&1 || {
    echo "Missing image kapp/$image:$STACK. Run scripts/k8s-up.sh --build." >&2
    exit 1
  }
done

if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  echo "Creating the kind cluster '$CLUSTER'..."
  kind create cluster --config "$K8S/kind-config.yaml"
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null

echo "Loading the images into the cluster..."
for image in "${IMAGES[@]}"; do
  docker tag "kapp/$image:$STACK" "kapp/$image:kind"
  kind load docker-image "kapp/$image:kind" --name "$CLUSTER" >/dev/null
  echo "  kapp/$image"
done

kubectl apply -f "$K8S/namespace.yaml" >/dev/null

echo "Creating the kapp-secrets Secret from $(basename "$ENV_FILE")..."
# Sourced, not read as an env file: .env single-quotes its values (they contain '&'), and
# kubectl's --from-env-file would keep the quotes as part of the value.
secret_lines() {
  (
    set -a; . "$ENV_FILE"; set +a
    for key in MONGO_AUTH_URI MONGO_USER_URI MONGO_SEMAPHORE_URI MONGO_SCHEDULE_URI MONGO_MAP_URI KAPP_INTERNAL_TOKEN; do
      [ -n "${!key:-}" ] || { echo "$key is empty in $ENV_FILE" >&2; exit 1; }
      printf '%s=%s\n' "$key" "${!key}"
    done
    for key in KAPP_JWT_PRIVATE_KEY KAPP_JWT_PUBLIC_KEY SPRING_MAIL_HOST SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD; do
      [ -n "${!key:-}" ] && printf '%s=%s\n' "$key" "${!key}"
    done
    true
  )
}
# Checked first, with the values thrown away: a failure inside the pipe below would not stop
# kubectl from creating a Secret with keys missing.
secret_lines >/dev/null
kubectl create secret generic kapp-secrets --namespace "$NAMESPACE" \
  --from-env-file=<(secret_lines) --dry-run=client -o yaml | kubectl apply -f - >/dev/null

echo "Applying deploy/k8s..."
kubectl apply -k "$K8S" >/dev/null
# A re-run with rebuilt images keeps the tag "kind", so Kubernetes would not notice on its own.
kubectl rollout restart deployment --namespace "$NAMESPACE" >/dev/null

echo "Waiting for every Deployment to be available (the JVMs take a minute or two)..."
for deployment in "${IMAGES[@]}"; do
  kubectl rollout status "deployment/$deployment" --namespace "$NAMESPACE" --timeout=6m
done

echo
kubectl get pods --namespace "$NAMESPACE"
echo
echo "Gateway: http://localhost:8080    Portal: http://localhost:4300"
echo "Stop with: scripts/k8s-down.sh"
