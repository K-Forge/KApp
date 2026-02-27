#!/usr/bin/env bash
# ──────────────────────────────────────────────────
# K-APP · Iniciar backend monolito (legacy)
# ──────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/backend/kapp"

echo "🚀 Iniciando K-APP monolito (puerto 8080)…"
./mvnw spring-boot:run
