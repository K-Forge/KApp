#!/usr/bin/env bash
# ──────────────────────────────────────────────────
# K-APP · Iniciar frontend web (dev server)
# ──────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "🌐 Iniciando frontend web…"
cd "$ROOT"
bunx serve frontend/web
