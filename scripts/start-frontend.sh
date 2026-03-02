#!/usr/bin/env bash
# ─────────────────────────────────────────────
# K-APP · Start frontend web (dev server)
# ─────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if ! command -v bun &>/dev/null; then
  echo "❌ bun is not installed. Install it: https://bun.sh" && exit 1
fi

echo "🌐 Starting K-APP web frontend on http://localhost:3000 ..."
cd "$ROOT"
bun run dev:web
