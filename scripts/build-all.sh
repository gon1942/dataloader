#!/bin/bash

# Build all packages and install the local Python CLI by default.
# Usage:
#   ./scripts/build-all.sh [VERSION] [--no-install] [--editable]
#
# Examples:
#   ./scripts/build-all.sh
#   ./scripts/build-all.sh 1.0.0
#   ./scripts/build-all.sh 1.0.0 --editable
#   ./scripts/build-all.sh 1.0.0 --no-install

set -euo pipefail

# =================================================================
# Configuration
# =================================================================
VERSION="0.0.0"
VERSION_SET="false"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
INSTALL_MODE="wheel"
SKIP_INSTALL="false"

for arg in "$@"; do
    case "$arg" in
        --editable)
            INSTALL_MODE="editable"
            ;;
        --no-install)
            SKIP_INSTALL="true"
            ;;
        --help|-h)
            cat <<EOF
Usage: $0 [VERSION] [--no-install] [--editable]

Arguments:
  VERSION       Package version to stamp into build outputs. Default: 0.0.0

Options:
  --editable    Install Python package with pip install -e
  --no-install  Build/test only; do not replace local opendataloader-pdf install
EOF
            exit 0
            ;;
        -*)
            echo "Unknown option: $arg"
            exit 1
            ;;
        *)
            if [ "$VERSION_SET" = "true" ]; then
                echo "Error: multiple version arguments provided"
                exit 1
            fi
            VERSION="$arg"
            VERSION_SET="true"
            ;;
    esac
done

# =================================================================
# Prerequisites Check
# =================================================================
echo "Checking prerequisites..."

command -v java >/dev/null || { echo "Error: java not found"; exit 1; }
# Maven: check for mvnw (Maven Wrapper) or system mvn
if [ -f "$ROOT_DIR/java/mvnw" ]; then
    echo "Maven: using Maven Wrapper (mvnw)"
else
    command -v mvn >/dev/null || { echo "Error: mvn not found. Install Maven or add mvnw to java/"; exit 1; }
fi
command -v uv >/dev/null || { echo "Error: uv not found. Install with: curl -LsSf https://astral.sh/uv/install.sh | sh"; exit 1; }
command -v pip >/dev/null || { echo "Error: pip not found"; exit 1; }
command -v node >/dev/null || { echo "Error: node not found"; exit 1; }
command -v pnpm >/dev/null || { echo "Error: pnpm not found"; exit 1; }

echo "All prerequisites found."

echo ""
echo "========================================"
echo "Building all packages (version: $VERSION)"
if [ "$SKIP_INSTALL" = "true" ]; then
    echo "Python install: skipped"
else
    echo "Python install mode: $INSTALL_MODE"
fi
echo "========================================"

# =================================================================
# Java Build & Test
# =================================================================
echo ""
echo "[1/3] Java: Building and testing..."
echo "----------------------------------------"

cd "$ROOT_DIR/java"
if [ -f "mvnw" ]; then
    ./mvnw versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
else
    mvn versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
fi
"$SCRIPT_DIR/build-java.sh"

echo "[1/3] Java: Done"

# =================================================================
# Python Build & Test
# =================================================================
echo ""
echo "[2/3] Python: Building and testing..."
echo "----------------------------------------"

cd "$ROOT_DIR/python/opendataloader-pdf"
sed -i.bak "s/^version = \"[^\"]*\"/version = \"$VERSION\"/" pyproject.toml && rm -f pyproject.toml.bak
INSTALL_MODE="$INSTALL_MODE" SKIP_INSTALL="$SKIP_INSTALL" "$SCRIPT_DIR/build-python.sh"

echo "[2/3] Python: Done"

# =================================================================
# Node.js Build & Test
# =================================================================
echo ""
echo "[3/3] Node.js: Building and testing..."
echo "----------------------------------------"

cd "$ROOT_DIR/node/opendataloader-pdf"
pnpm version "$VERSION" --no-git-tag-version --allow-same-version
"$SCRIPT_DIR/build-node.sh"

echo "[3/3] Node.js: Done"

# =================================================================
# Summary
# =================================================================
echo ""
echo "========================================"
echo "All builds completed successfully!"
echo "Version: $VERSION"
echo "========================================"
