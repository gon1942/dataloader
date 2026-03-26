#!/bin/bash

# Build/test the Python package and optionally install it locally.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
PACKAGE_DIR="$ROOT_DIR/python/opendataloader-pdf"
INSTALL_MODE="${INSTALL_MODE:-wheel}"
SKIP_INSTALL="${SKIP_INSTALL:-false}"
cd "$PACKAGE_DIR"

# Check uv is available
command -v uv >/dev/null || { echo "Error: uv not found. Install with: curl -LsSf https://astral.sh/uv/install.sh | sh"; exit 1; }
command -v python3 >/dev/null || { echo "Error: python3 not found"; exit 1; }
command -v pip >/dev/null || { echo "Error: pip not found"; exit 1; }

# Clean previous build
rm -rf dist/

# Copy README.md from root (gitignored in package dir)
cp "$ROOT_DIR/README.md" "$PACKAGE_DIR/README.md"

# Build wheel package
uv build --wheel

# Install and run tests
uv sync
uv run pytest tests -v -s

if [ "$SKIP_INSTALL" = "true" ]; then
    echo "Build completed successfully. Installation skipped."
    exit 0
fi

if pip show opendataloader-pdf >/dev/null 2>&1; then
    pip uninstall -y opendataloader-pdf
fi

if [ "$INSTALL_MODE" = "editable" ]; then
    pip install -e "$PACKAGE_DIR"
    echo "Build completed successfully. Installed in editable mode."
    exit 0
fi

WHEEL="$(find "$PACKAGE_DIR/dist" -maxdepth 1 -name 'opendataloader_pdf-*.whl' | sort | tail -n 1)"
if [ -z "$WHEEL" ]; then
    echo "Error: wheel file not found in $PACKAGE_DIR/dist"
    exit 1
fi

pip install "$WHEEL"

echo "Build completed successfully. Installed wheel: $(basename "$WHEEL")"
