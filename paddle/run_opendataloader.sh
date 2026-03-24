#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADAPTER_URL="${ADAPTER_URL:-http://127.0.0.1:8090}"

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <input-pdf> <output-dir> [extra opendataloader args...]"
  echo "Example: $0 samples/pdf/input1.pdf tmp/odl-hybrid/input1"
  exit 1
fi

INPUT_PDF="$1"
OUTPUT_DIR="$2"
shift 2

cd "$ROOT"

echo "Running OpenDataLoader with Paddle adapter"
echo "  input : $INPUT_PDF"
echo "  output: $OUTPUT_DIR"
echo "  url   : $ADAPTER_URL"

opendataloader-pdf \
  --hybrid docling-fast \
  --hybrid-url "$ADAPTER_URL" \
  -o "$OUTPUT_DIR" \
  "$INPUT_PDF" \
  "$@"
