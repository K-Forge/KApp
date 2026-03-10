#!/usr/bin/env bash
# ─────────────────────────────────────────────
# KApp · Start monolith backend (legacy)
# ─────────────────────────────────────────────
# Usage:
#   ./scripts/start-backend.sh
#   PROFILE=dev ./scripts/start-backend.sh
# ─────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="$ROOT/app/backend/kapp"
PROFILE="${PROFILE:-default}"

# ── Helpers ──────────────────────────────────

log()  { echo "$(date '+%H:%M:%S') $1"; }
ok()   { log "✅ $1"; }
fail() { log "❌ $1"; exit 1; }
info() { log "ℹ️  $1"; }

# ── Checks ───────────────────────────────────

[ -d "$BACKEND" ]          || fail "Backend directory not found: $BACKEND"
[ -f "$BACKEND/mvnw" ]     || fail "Maven wrapper not found in $BACKEND"
command -v java &>/dev/null || fail "Java is not installed. Required: Java 21+"

JAVA_VER=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
if [ "${JAVA_VER:-0}" -lt 21 ]; then
  fail "Java 21+ required, found Java $JAVA_VER"
fi

# ── Start ────────────────────────────────────

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║     KApp Monolith Backend (Legacy)       ║"
echo "╚══════════════════════════════════════════╝"
echo ""
info "Java $JAVA_VER detected"
info "Profile: $PROFILE"
info "Directory: $BACKEND"
echo ""

cd "$BACKEND"
chmod +x mvnw
./mvnw spring-boot:run -Dspring-boot.run.profiles="$PROFILE"
