#!/bin/bash

# Production installation flow:
# Install from prebuilt artifacts without rebuilding from source.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ARTIFACT_PATH="${1:-}"
SMOKE_PDF="${SMOKE_PDF:-}"
SMOKE_OUTPUT_DIR="${SMOKE_OUTPUT_DIR:-$ROOT_DIR/tmp/odl-prod-test}"

usage() {
    cat <<EOF
Usage: $0 <wheel-or-release-dir>

Examples:
  $0 ./tmp/releases/1.0.0
  $0 ./tmp/releases/1.0.0/opendataloader_pdf-1.0.0-py3-none-any.whl

Environment variables:
  SMOKE_PDF         Optional PDF path for post-install smoke test
  SMOKE_OUTPUT_DIR  Output directory for smoke test
EOF
}

if [[ -z "$ARTIFACT_PATH" || "$ARTIFACT_PATH" == "--help" || "$ARTIFACT_PATH" == "-h" ]]; then
    usage
    exit 0
fi

require_cmd() {
    command -v "$1" >/dev/null || { echo "Error: $1 not found"; exit 1; }
}

require_cmd java
require_cmd python3
require_cmd pip

if [[ -d "$ARTIFACT_PATH" ]]; then
    WHEEL_PATH="$(find "$ARTIFACT_PATH" -maxdepth 1 -name 'opendataloader_pdf-*.whl' | sort | tail -n 1)"
else
    WHEEL_PATH="$ARTIFACT_PATH"
fi

if [[ -z "$WHEEL_PATH" || ! -f "$WHEEL_PATH" ]]; then
    echo "Error: wheel artifact not found from input: $ARTIFACT_PATH"
    exit 1
fi

echo "========================================"
echo " Production install"
echo " Wheel: $WHEEL_PATH"
echo "========================================"

if pip show opendataloader-pdf >/dev/null 2>&1; then
    pip uninstall -y opendataloader-pdf
fi

pip install "$WHEEL_PATH"

echo
echo "Installed package info:"
pip show opendataloader-pdf

echo
echo "CLI check:"
opendataloader-pdf --help >/dev/null
echo "opendataloader-pdf --help: OK"

if [[ -n "$SMOKE_PDF" ]]; then
    if [[ ! -f "$SMOKE_PDF" ]]; then
        echo "Error: smoke PDF not found: $SMOKE_PDF"
        exit 1
    fi
    rm -rf "$SMOKE_OUTPUT_DIR"
    mkdir -p "$SMOKE_OUTPUT_DIR"
    opendataloader-pdf "$SMOKE_PDF" -o "$SMOKE_OUTPUT_DIR" -f markdown,json
    echo
    echo "Smoke test output:"
    find "$SMOKE_OUTPUT_DIR" -maxdepth 1 -type f | sort
fi

echo
echo "Completed successfully."
