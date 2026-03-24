#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

SERVER_URL="${SERVER_URL:-http://127.0.0.1:8080/v1}"
DATA_DIR="$ROOT/data"
OUT_DIR="$DATA_DIR/out"

# 캐시/임시 디렉토리(권한 문제 방지)
mkdir -p "$DATA_DIR/.paddlex" "$DATA_DIR/.cache" "$DATA_DIR/.tmp" "$OUT_DIR"
chmod -R u+rwX "$DATA_DIR/.paddlex" "$DATA_DIR/.cache" "$DATA_DIR/.tmp" "$OUT_DIR"

# vLLM 서버 헬스 체크
if ! curl -fsS "${SERVER_URL}/models" >/dev/null; then
  echo "[ERROR] vLLM server not reachable: ${SERVER_URL}"
  echo "        먼저 vLLM 서버를 실행하거나 SERVER_URL을 올바르게 지정하세요."
  exit 1
fi

shopt -s nullglob

for f in "$DATA_DIR"/*.pdf; do
  base="$(basename "$f" .pdf)"
  echo "=== OCR: $f -> $OUT_DIR/$base ==="

  docker run --rm --network host -v "$DATA_DIR:/data" \
    --user "$(id -u)":"$(id -g)" \
    -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
    -e PADDLE_PDX_HOME=/data/.paddlex \
    -e XDG_CACHE_HOME=/data/.cache \
    -e TMPDIR=/data/.tmp \
    paddleocr_vl15_client \
    python /app/ocr_pdf.py \
      --input "/data/$(basename "$f")" \
      --out_dir "/data/out/$base" \
      --server_url "$SERVER_URL" \
      --make_html
done

echo "Done. Outputs under: $OUT_DIR"
echo "Tip: 브라우저로 보기 -> python3 -m http.server 8000 -d data/out"