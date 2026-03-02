#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# K-APP · Start all microservices (local development)
# ─────────────────────────────────────────────────────────────
# Usage:
#   ./scripts/start-microservices.sh          Start all services
#   ./scripts/start-microservices.sh stop      Stop all services
#   ./scripts/start-microservices.sh status    Check running services
# ─────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MS_DIR="$ROOT/backend/microservices"
PIDS_DIR="$ROOT/.pids"

# Service definitions: name → port (order matters for startup)
declare -a SERVICES=(
  "discovery-server"
  "api-gateway"
  "auth-service"
  "user-service"
  "course-service"
  "assignment-service"
)

declare -A PORTS=(
  [discovery-server]=8761
  [api-gateway]=8080
  [auth-service]=8081
  [user-service]=8082
  [course-service]=8083
  [assignment-service]=8084
)

# ── Helpers ──────────────────────────────────────────────────

log()  { echo "$(date '+%H:%M:%S') $1"; }
ok()   { log "✅ $1"; }
fail() { log "❌ $1"; }
info() { log "ℹ️  $1"; }

wait_for_port() {
  local port=$1 name=$2 retries=60
  info "Waiting for $name on port $port..."
  while ! curl -sf "http://localhost:$port/actuator/health" &>/dev/null; do
    retries=$((retries - 1))
    if [ $retries -le 0 ]; then
      fail "$name did not start on port $port" && return 1
    fi
    sleep 2
  done
  ok "$name is ready on port $port"
}

start_service() {
  local name=$1
  local dir="$MS_DIR/$name"
  local port="${PORTS[$name]}"

  if [ ! -d "$dir" ]; then
    fail "Directory not found: $dir" && return 1
  fi

  mkdir -p "$PIDS_DIR"
  info "Starting $name..."
  cd "$dir"

  # Use parent Maven wrapper if local one doesn't exist
  local mvnw="./mvnw"
  if [ ! -f "$mvnw" ]; then
    mvnw="$MS_DIR/mvnw"
  fi
  if [ ! -f "$mvnw" ]; then
    mvnw="mvn"
  fi

  chmod +x "$mvnw" 2>/dev/null || true
  $mvnw spring-boot:run -q &>"$PIDS_DIR/$name.log" &
  echo $! > "$PIDS_DIR/$name.pid"
  info "$name started (PID: $!)"
}

stop_all() {
  info "Stopping all microservices..."
  if [ ! -d "$PIDS_DIR" ]; then
    info "No PID directory found. Nothing to stop." && exit 0
  fi
  for name in "${SERVICES[@]}"; do
    local pidfile="$PIDS_DIR/$name.pid"
    if [ -f "$pidfile" ]; then
      local pid=$(cat "$pidfile")
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" && ok "Stopped $name (PID: $pid)"
      else
        info "$name was not running"
      fi
      rm -f "$pidfile"
    fi
  done
  info "All services stopped."
}

status_all() {
  echo ""
  echo "┌──────────────────────┬────────┬──────────┐"
  echo "│ Service              │ Port   │ Status   │"
  echo "├──────────────────────┼────────┼──────────┤"
  for name in "${SERVICES[@]}"; do
    local port="${PORTS[$name]}"
    local status="⬇ DOWN"
    if curl -sf "http://localhost:$port/actuator/health" &>/dev/null; then
      status="✅ UP"
    fi
    printf "│ %-20s │ %-6s │ %-8s │\n" "$name" "$port" "$status"
  done
  echo "└──────────────────────┴────────┴──────────┘"
  echo ""
}

# ── Main ─────────────────────────────────────────────────────

case "${1:-start}" in
  stop)
    stop_all
    ;;
  status)
    status_all
    ;;
  start)
    echo ""
    echo "╔══════════════════════════════════════════╗"
    echo "║   K-APP Microservices · Local Startup    ║"
    echo "╚══════════════════════════════════════════╝"
    echo ""

    # Phase 1: Infrastructure
    info "Phase 1/3: Infrastructure"
    start_service "discovery-server"
    wait_for_port 8761 "discovery-server"

    # Phase 2: Business services (parallel)
    info "Phase 2/3: Business services"
    for svc in auth-service user-service course-service assignment-service; do
      start_service "$svc"
    done

    # Wait for business services
    for svc in auth-service user-service course-service assignment-service; do
      wait_for_port "${PORTS[$svc]}" "$svc"
    done

    # Phase 3: Gateway (needs all services registered)
    info "Phase 3/3: API Gateway"
    start_service "api-gateway"
    wait_for_port 8080 "api-gateway"

    echo ""
    ok "All microservices are running!"
    echo ""
    echo "  📊 Eureka Dashboard : http://localhost:8761"
    echo "  🌐 API Gateway     : http://localhost:8080"
    echo "  🔑 Auth Service    : http://localhost:8081"
    echo "  👤 User Service    : http://localhost:8082"
    echo "  📚 Course Service  : http://localhost:8083"
    echo "  📝 Assignment Svc  : http://localhost:8084"
    echo ""
    echo "  Stop all: ./scripts/start-microservices.sh stop"
    echo ""
    ;;
  *)
    echo "Usage: $0 {start|stop|status}"
    exit 1
    ;;
esac
