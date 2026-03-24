#!/usr/bin/env bash
set -euo pipefail

VLM_CONTAINER="${VLM_CONTAINER:-paddleocr_vl15_vllm}"
ADAPTER_CONTAINER="${ADAPTER_CONTAINER:-odl_paddle_adapter}"

stop_if_exists() {
  local name="$1"
  if docker ps -a --format '{{.Names}}' | grep -Fxq "$name"; then
    docker rm -f "$name"
  fi
}

stop_if_exists "$ADAPTER_CONTAINER"
stop_if_exists "$VLM_CONTAINER"

echo "Stack stopped."
