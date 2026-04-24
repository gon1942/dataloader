#!/bin/bash

# Build all packages and install the local Python CLI by default.
# Usage:
#   ./scripts/build-all.sh [VERSION] [--no-install] [--editable] [--release] [--release-dir DIR]
#
# Examples:
#   ./scripts/build-all.sh
#   ./scripts/build-all.sh 1.0.0
#   ./scripts/build-all.sh 1.0.0 --editable
#   ./scripts/build-all.sh 1.0.0 --no-install
#   ./scripts/build-all.sh 1.0.0 --release
#   ./scripts/build-all.sh 1.0.0 --release-dir ./tmp/releases/1.0.0

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
RELEASE_DIR=""
USE_DEFAULT_RELEASE_DIR="false"

resolve_release_dir() {
    local dir="$1"
    if [[ "$dir" = /* ]]; then
        printf '%s\n' "$dir"
    else
        printf '%s\n' "$ROOT_DIR/$dir"
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --editable)
            INSTALL_MODE="editable"
            shift
            ;;
        --no-install)
            SKIP_INSTALL="true"
            shift
            ;;
        --release)
            USE_DEFAULT_RELEASE_DIR="true"
            shift
            ;;
        --release-dir)
            if [[ $# -lt 2 ]]; then
                echo "Error: $1 requires a directory path"
                exit 1
            fi
            RELEASE_DIR="$(resolve_release_dir "$2")"
            shift 2
            ;;
        --help|-h)
            cat <<EOF
Usage: $0 [VERSION] [--no-install] [--editable] [--release] [--release-dir DIR]

Arguments:
  VERSION       Package version to stamp into build outputs. Default: 0.0.0

Options:
  --editable    Install Python package with pip install -e
  --no-install  Build/test only; do not replace local opendataloader-pdf install
  --release     Copy build outputs into ./tmp/releases/<VERSION>
  --release-dir Copy build outputs into DIR after a successful build
EOF
            exit 0
            ;;
        -*)
            echo "Unknown option: $1"
            exit 1
            ;;
        *)
            if [ "$VERSION_SET" = "true" ]; then
                echo "Error: multiple version arguments provided"
                exit 1
            fi
            VERSION="$1"
            VERSION_SET="true"
            shift
            ;;
    esac
done

if [ "$USE_DEFAULT_RELEASE_DIR" = "true" ] && [ -z "$RELEASE_DIR" ]; then
    RELEASE_DIR="$ROOT_DIR/tmp/releases/$VERSION"
fi

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
if [ -n "$RELEASE_DIR" ]; then
    echo "Release dir: $RELEASE_DIR"
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
# Stage Release Artifacts
# =================================================================
if [ -n "$RELEASE_DIR" ]; then
    echo ""
    echo "[post] Staging build artifacts..."
    echo "----------------------------------------"

    mkdir -p "$RELEASE_DIR"

    JAVA_CLI_JAR="$ROOT_DIR/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-$VERSION.jar"
    PYTHON_WHEEL="$ROOT_DIR/python/opendataloader-pdf/dist/opendataloader_pdf-$VERSION-py3-none-any.whl"
    NODE_DIST_DIR="$ROOT_DIR/node/opendataloader-pdf/dist"

    if [ ! -f "$JAVA_CLI_JAR" ]; then
        echo "Error: Java CLI jar not found: $JAVA_CLI_JAR"
        exit 1
    fi
    if [ ! -f "$PYTHON_WHEEL" ]; then
        echo "Error: Python wheel not found: $PYTHON_WHEEL"
        exit 1
    fi
    if [ ! -d "$NODE_DIST_DIR" ]; then
        echo "Error: Node dist directory not found: $NODE_DIST_DIR"
        exit 1
    fi

    cp "$JAVA_CLI_JAR" "$RELEASE_DIR/"
    cp "$PYTHON_WHEEL" "$RELEASE_DIR/"
    rm -rf "$RELEASE_DIR/node-dist"
    cp -R "$NODE_DIST_DIR" "$RELEASE_DIR/node-dist"

    echo "Staged artifacts:"
    find "$RELEASE_DIR" -maxdepth 2 -type f | sort
fi

# =================================================================
# Summary
# =================================================================
echo ""
echo "========================================"
echo "All builds completed successfully!"
echo "Version: $VERSION"
if [ -n "$RELEASE_DIR" ]; then
    echo "Artifacts: $RELEASE_DIR"
fi
echo "========================================"
