#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ADAPTER_DIR="$ROOT/adapter"

VLM_CONTAINER="${VLM_CONTAINER:-paddleocr_vl15_vllm}"
ADAPTER_CONTAINER="${ADAPTER_CONTAINER:-odl_paddle_adapter}"
ADAPTER_IMAGE="${ADAPTER_IMAGE:-odl-paddle-adapter}"
VLM_IMAGE="${VLM_IMAGE:-ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddleocr-genai-vllm-server:latest-nvidia-gpu}"
GPU_DEVICE="${GPU_DEVICE:-all}"
VLM_PORT="${VLM_PORT:-8080}"
ADAPTER_PORT="${ADAPTER_PORT:-8090}"
SHM_SIZE="${SHM_SIZE:-8g}"

resolve_gpus_arg() {
  case "$GPU_DEVICE" in
    all)
      printf '%s' "all"
      ;;
    device=*)
      printf '%s' "$GPU_DEVICE"
      ;;
    ''|*[!0-9,]*)
      printf '%s' "$GPU_DEVICE"
      ;;
    *)
      printf 'device=%s' "$GPU_DEVICE"
      ;;
  esac
}

cleanup_container() {
  local name="$1"
  if docker ps -a --format '{{.Names}}' | grep -Fxq "$name"; then
    docker rm -f "$name" >/dev/null
  fi
}

print_logs() {
  local name="$1"
  echo "----- logs: ${name} -----" >&2
  docker logs --tail 120 "$name" 2>&1 || true
  echo "-------------------------" >&2
}

echo "[1/5] Removing old containers if present"
cleanup_container "$ADAPTER_CONTAINER"
cleanup_container "$VLM_CONTAINER"

echo "[2/5] Building adapter image"
docker build -t "$ADAPTER_IMAGE" "$ADAPTER_DIR"

echo "[3/5] Starting Paddle vLLM server"
docker run -d \
  --name "$VLM_CONTAINER" \
  --gpus "$(resolve_gpus_arg)" \
  --network host \
  --shm-size "$SHM_SIZE" \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  "$VLM_IMAGE" \
  paddleocr genai_server --model_name PaddleOCR-VL-1.5-0.9B --host 0.0.0.0 --port "$VLM_PORT" --backend vllm

echo "[4/5] Waiting for Paddle backend"
backend_ready=0
for _ in {1..90}; do
  if curl -fsS "http://127.0.0.1:${VLM_PORT}/v1/models" >/dev/null 2>&1; then
    backend_ready=1
    break
  fi
  sleep 2
done

if [[ "$backend_ready" -ne 1 ]]; then
  echo "Paddle backend did not become ready at http://127.0.0.1:${VLM_PORT}/v1/models" >&2
  print_logs "$VLM_CONTAINER"
  exit 1
fi

echo "[5/5] Starting adapter"
docker run -d \
  --name "$ADAPTER_CONTAINER" \
  --network host \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  -e PADDLE_BASE_URL="http://127.0.0.1:${VLM_PORT}" \
  -e PADDLE_HEALTH_PATH=/v1/models \
  -e PADDLE_CONVERT_PATH=/v1/chat/completions \
  -e PADDLE_VLLM_SERVER_PATH=/v1 \
  -e REQUEST_TIMEOUT_SECONDS=120 \
  -e LOG_LEVEL=INFO \
  "$ADAPTER_IMAGE" \
  uvicorn app:app --host 0.0.0.0 --port "$ADAPTER_PORT"

echo "Checking adapter health"
adapter_ready=0
for _ in {1..30}; do
  if curl -fsS "http://127.0.0.1:${ADAPTER_PORT}/health" >/dev/null 2>&1; then
    adapter_ready=1
    break
  fi
  sleep 2
done

if [[ "$adapter_ready" -ne 1 ]]; then
  echo "Adapter did not become ready at http://127.0.0.1:${ADAPTER_PORT}/health" >&2
  print_logs "$ADAPTER_CONTAINER"
  exit 1
fi

curl -fsS "http://127.0.0.1:${ADAPTER_PORT}/health" | sed -n '1,120p'
echo
if ! curl -fsS "http://127.0.0.1:${ADAPTER_PORT}/health/backend" | sed -n '1,120p'; then
  echo "Adapter backend probe failed." >&2
  print_logs "$VLM_CONTAINER"
  print_logs "$ADAPTER_CONTAINER"
  exit 1
fi
echo

echo "Stack is up."
