#!/bin/bash

# Local development flow:
# 1. Build Java and Python from the current workspace
# 2. Stage build artifacts into a local release directory
# 3. Reinstall the Python package from the staged wheel
# 4. Run a smoke test with a sample PDF

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAVA_DIR="$ROOT_DIR/java"
PYTHON_DIR="$ROOT_DIR/python/opendataloader-pdf"

VERSION="${1:-0.0.0-dev}"
SAMPLE_PDF="${SAMPLE_PDF:-$ROOT_DIR/samples/pdf/input1.pdf}"
FORMAT="${FORMAT:-markdown,json}"
TEST_OUTPUT_DIR="${TEST_OUTPUT_DIR:-$ROOT_DIR/tmp/odl-test/install-test}"
RELEASE_DIR="${RELEASE_DIR:-$ROOT_DIR/tmp/releases/$VERSION}"

usage() {
    cat <<EOF
Usage: $0 [VERSION]

Environment variables:
  SAMPLE_PDF       Sample PDF for smoke test
  FORMAT           Output formats for smoke test (default: markdown,json)
  TEST_OUTPUT_DIR  Smoke test output directory
  RELEASE_DIR      Local release directory for staged artifacts
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
fi

require_cmd() {
    command -v "$1" >/dev/null || { echo "Error: $1 not found"; exit 1; }
}

require_cmd java
require_cmd python3
require_cmd pip
require_cmd uv

if [[ -f "$JAVA_DIR/mvnw" ]]; then
    MVN_CMD="$JAVA_DIR/mvnw"
else
    require_cmd mvn
    MVN_CMD="mvn"
fi

echo "========================================"
echo " Development build/install/test"
echo " Version: $VERSION"
echo " Release: $RELEASE_DIR"
echo "========================================"

echo
echo "[1/5] Build Java from current workspace..."
(
    cd "$JAVA_DIR"
    "$MVN_CMD" -B versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
)
"$SCRIPT_DIR/build-java.sh"

echo
echo "[2/5] Build Python wheel with bundled local Java JAR..."
(
    cd "$PYTHON_DIR"
    sed -i.bak "s/^version = \"[^\"]*\"/version = \"$VERSION\"/" pyproject.toml
    rm -f pyproject.toml.bak
)
"$SCRIPT_DIR/build-python.sh"

echo
echo "[3/5] Stage release artifacts..."
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"
cp "$JAVA_DIR/opendataloader-pdf-cli/target/opendataloader-pdf-cli-$VERSION.jar" "$RELEASE_DIR/"
cp "$PYTHON_DIR/dist/opendataloader_pdf-$VERSION-py3-none-any.whl" "$RELEASE_DIR/"

echo
echo "[4/5] Reinstall Python package from staged wheel..."
if pip show opendataloader-pdf >/dev/null 2>&1; then
    pip uninstall -y opendataloader-pdf
fi
pip install "$RELEASE_DIR/opendataloader_pdf-$VERSION-py3-none-any.whl"

echo
echo "[5/5] Run smoke test..."
if [[ ! -f "$SAMPLE_PDF" ]]; then
    echo "Error: sample PDF not found: $SAMPLE_PDF"
    exit 1
fi

rm -rf "$TEST_OUTPUT_DIR"
mkdir -p "$TEST_OUTPUT_DIR"
opendataloader-pdf "$SAMPLE_PDF" -o "$TEST_OUTPUT_DIR" -f "$FORMAT"

echo
echo "Smoke test output:"
find "$TEST_OUTPUT_DIR" -maxdepth 1 -type f | sort

echo
echo "Completed successfully."
echo "Installed wheel: $RELEASE_DIR/opendataloader_pdf-$VERSION-py3-none-any.whl"
