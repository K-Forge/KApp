#!/usr/bin/env bash
# ──────────────────────────────────────────────────
# K-APP · Iniciar todos los microservicios
# Orden: discovery → auth → user → course → assignment → gateway
# ──────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MS_DIR="$ROOT/backend/microservices"
PIDS=()

cleanup() {
  echo ""
  echo "🛑 Deteniendo microservicios…"
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null
  echo "✅ Todos los servicios detenidos."
  exit 0
}
trap cleanup SIGINT SIGTERM

# ── Orden de arranque (boot order) ───────────────
SERVICES=(
  "discovery-server:8761"
  "auth-service:8081"
  "user-service:8082"
  "course-service:8083"
  "assignment-service:8084"
  "api-gateway:8080"
)

wait_for_port() {
  local port=$1 retries=30
  while ! curl -sf "http://localhost:$port/actuator/health" >/dev/null 2>&1; do
    retries=$((retries - 1))
    if [ "$retries" -le 0 ]; then
      echo "⚠️  Timeout esperando puerto $port"
      return 1
    fi
    sleep 2
  done
  return 0
}

echo "╔══════════════════════════════════════════╗"
echo "║     K-APP · Microservicios Launcher      ║"
echo "╚══════════════════════════════════════════╝"
echo ""

for entry in "${SERVICES[@]}"; do
  name="${entry%%:*}"
  port="${entry##*:}"

  echo "🔄 Iniciando $name (puerto $port)…"
  cd "$MS_DIR/$name"
  ../mvnw spring-boot:run -q &
  PIDS+=($!)

  # Eureka debe estar listo antes de arrancar los demás
  if [ "$name" = "discovery-server" ]; then
    echo "⏳ Esperando Eureka…"
    if wait_for_port "$port"; then
      echo "✅ Eureka disponible en http://localhost:$port"
    fi
  fi
done

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║  ✅ Todos los servicios iniciados         ║"
echo "╠══════════════════════════════════════════╣"
echo "║  Eureka:      http://localhost:8761       ║"
echo "║  Gateway:     http://localhost:8080       ║"
echo "║  Auth:        http://localhost:8081       ║"
echo "║  Users:       http://localhost:8082       ║"
echo "║  Courses:     http://localhost:8083       ║"
echo "║  Assignments: http://localhost:8084       ║"
echo "╚══════════════════════════════════════════╝"
echo ""
echo "Presiona Ctrl+C para detener todos los servicios."

wait
