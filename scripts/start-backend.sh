#!/usr/bin/env bash
# ─────────────────────────────────────────────
# KApp · Start monolith backend (legacy)
# ─────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="$ROOT/backend/kapp"

if [ ! -f "$BACKEND/mvnw" ]; then
  echo "❌ Maven wrapper not found in $BACKEND" && exit 1
fi

echo "🚀 Starting KApp monolith backend..."
cd "$BACKEND"
chmod +x mvnw
./mvnw spring-boot:run
