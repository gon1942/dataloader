#!/bin/bash

# CI/CD build script for Java package
# For local development, use test-java.sh instead

set -e

# Prerequisites
command -v java >/dev/null || { echo "Error: java not found"; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
PACKAGE_DIR="$ROOT_DIR/java"
cd "$PACKAGE_DIR"

# Use Maven Wrapper if available, otherwise fallback to mvn
MVN_CMD=""
if [ -f "$PACKAGE_DIR/mvnw" ]; then
    MVN_CMD="$PACKAGE_DIR/mvnw"
else
    command -v mvn >/dev/null || { echo "Error: mvn not found. Install Maven or add mvnw to $PACKAGE_DIR/"; exit 1; }
    MVN_CMD="mvn"
fi

# Build and test
$MVN_CMD -B clean package -P release
