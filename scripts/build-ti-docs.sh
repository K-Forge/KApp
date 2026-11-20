#!/usr/bin/env bash
# Generates the .docx files for the university's IT office and offices from their Markdown in docs/ti/,
# with the styles of docs/ti/kapp-reference.docx. The .docx files are never committed; see docs/ti/README.md.
#
#   scripts/build-ti-docs.sh [output directory]    default: docs/ti/build
set -eu

root=$(git rev-parse --show-toplevel)
out=${1:-"$root/docs/ti/build"}

if ! command -v pandoc >/dev/null 2>&1; then
  echo "pandoc is not installed: brew install pandoc (macOS), or see https://pandoc.org/installing.html" >&2
  exit 1
fi

mkdir -p "$out"
for source in "$root"/docs/ti/peticiones-a-ti.md "$root"/docs/ti/cuestionario-oficinas.md; do
  target="$out/$(basename "$source" .md).docx"
  # --resource-path so relative links and images resolve from docs/ti, whatever the working directory.
  pandoc "$source" --reference-doc="$root/docs/ti/kapp-reference.docx" --resource-path="$root/docs/ti" -o "$target"
  echo "wrote $target"
done
