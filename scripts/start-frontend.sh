#!/usr/bin/env bash
# ─────────────────────────────────────────────
# KApp · Start frontend web (dev server)
# ─────────────────────────────────────────────
# Usage:
#   ./scripts/start-frontend.sh              Start dev server
#   PORT=4000 ./scripts/start-frontend.sh    Custom port
# ─────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT/app/frontend/web"
PORT="${PORT:-3000}"

# ── Helpers ──────────────────────────────────

log()  { echo "$(date '+%H:%M:%S') $1"; }
ok()   { log "✅ $1"; }
fail() { log "❌ $1"; exit 1; }
info() { log "ℹ️  $1"; }

# ── Checks ───────────────────────────────────

command -v bun &>/dev/null || fail "bun is not installed. Install it: https://bun.sh"
[ -d "$WEB_DIR" ]             || fail "Web directory not found: $WEB_DIR"
[ -f "$WEB_DIR/index.html" ]  || fail "No index.html found in $WEB_DIR"

# ── Start ────────────────────────────────────

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║     KApp Frontend · Dev Server           ║"
echo "╚══════════════════════════════════════════╝"
echo ""
info "Serving $WEB_DIR"
info "URL: http://localhost:$PORT"
echo ""

cd "$ROOT"
bunx serve "$WEB_DIR" -l "$PORT"
