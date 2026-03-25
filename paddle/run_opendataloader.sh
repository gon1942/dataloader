#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADAPTER_URL="${ADAPTER_URL:-http://127.0.0.1:8090}"
SAVE_PADDLE_RAW="${SAVE_PADDLE_RAW:-1}"

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
  --format json,html,markdown,markdown-with-images \
  -o "$OUTPUT_DIR" \
  "$INPUT_PDF" \
  "$@"

if [[ "$SAVE_PADDLE_RAW" == "1" ]]; then
  RAW_DIR="$OUTPUT_DIR/_paddle_raw"
  RAW_JSON="$RAW_DIR/paddle_raw.json"

  mkdir -p "$RAW_DIR"

  echo
  echo "Saving raw Paddle outputs"
  echo "  raw   : $RAW_DIR"

  curl -fsS \
    -X POST \
    -F "files=@$INPUT_PDF;type=application/pdf" \
    "$ADAPTER_URL/v1/convert/file/paddle-raw" \
    -o "$RAW_JSON"

  python3 - "$RAW_JSON" "$RAW_DIR" <<'PY'
import json
import sys
from pathlib import Path

raw_json = Path(sys.argv[1])
raw_dir = Path(sys.argv[2])

payload = json.loads(raw_json.read_text(encoding="utf-8"))
pages = payload.get("pages") or []

for index, page in enumerate(pages, start=1):
    markdown = page.get("markdown") if isinstance(page, dict) else None
    if not isinstance(markdown, dict):
        continue
    text = markdown.get("markdown_texts") or markdown.get("text")
    if not isinstance(text, str) or not text.strip():
        continue
    out_path = raw_dir / f"page_{index:04d}.md"
    out_path.write_text(text, encoding="utf-8")
PY
fi
