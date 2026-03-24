#!/bin/bash

# 로컬 빌드 → 기존 패키지 삭제 → 설치 (개발용)
#
# 사용법:
#   ./scripts/reinstall-local.sh              # 기본 (editable 모드)
#   ./scripts/reinstall-local.sh --install    # 일반 설치 (wheel 빌드 후 설치)
#   ./scripts/reinstall-local.sh --skip-build # Java 빌드 생략 (JAR 이미 있을 때)
#   ./scripts/reinstall-local.sh --test       # 설치 후 테스트 실행

# 기본: 삭제 → Java 빌드 → editable 모드 설치
# ./scripts/reinstall-local.sh
# 일반 설치 (wheel 빌드 후 설치)
# ./scripts/reinstall-local.sh --install
# Java 빌드 생략 (JAR이 이미 있을 때)
# ./scripts/reinstall-local.sh --skip-build
# 설치 후 샘플 PDF로 변환 테스트까지 실행
# ./scripts/reinstall-local.sh --test

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
JAVA_DIR="$ROOT_DIR/java"
PYTHON_DIR="$ROOT_DIR/python/opendataloader-pdf"

MODE="editable"
SKIP_BUILD=false
RUN_TEST=false

for arg in "$@"; do
    case "$arg" in
        --install)    MODE="install" ;;
        --skip-build) SKIP_BUILD=true ;;
        --test)       RUN_TEST=true ;;
        --help|-h)
            echo "Usage: $0 [--install] [--skip-build] [--test]"
            echo ""
            echo "  (default)      Uninstall → Build Java → pip install -e . (editable)"
            echo "  --install      Uninstall → Build Java → Build wheel → pip install"
            echo "  --skip-build   Skip Java build (use existing JAR)"
            echo "  --test         Run test after install"
            exit 0
            ;;
        *)
            echo "Unknown option: $arg"
            exit 1
            ;;
    esac
done

echo "========================================"
echo " opendataloader-pdf 로컬 재설치"
echo " 모드: $MODE"
echo "========================================"

# ── 1. 기존 패키지 삭제 ──
echo ""
echo "[1/4] 기존 opendataloader-pdf 삭제..."
if pip show opendataloader-pdf >/dev/null 2>&1; then
    pip uninstall opendataloader-pdf -y
    echo "  ✓ 삭제 완료"
else
    echo "  (설치된 패키지 없음, 건너뜀)"
fi

# ── 2. Java 빌드 ──
if [ "$SKIP_BUILD" = false ]; then
    echo ""
    echo "[2/4] Java 빌드..."

    MVN_CMD=""
    if [ -f "$JAVA_DIR/mvnw" ]; then
        MVN_CMD="$JAVA_DIR/mvnw"
    else
        command -v mvn >/dev/null || { echo "Error: mvn not found"; exit 1; }
        MVN_CMD="mvn"
    fi

    (cd "$JAVA_DIR" && $MVN_CMD -B clean package -P release -q)
    echo "  ✓ Java 빌드 완료"
else
    echo ""
    echo "[2/4] Java 빌드 생략 (--skip-build)"
fi

# ── 3. Python 패키지 설치 ──
echo ""
echo "[3/4] Python 패키지 설치..."

# README 복사 (빌드 훅에서 필요)
cp "$ROOT_DIR/README.md" "$PYTHON_DIR/README.md" 2>/dev/null || true

if [ "$MODE" = "editable" ]; then
    pip install -e "$PYTHON_DIR"
    echo "  ✓ editable 모드 설치 완료"
else
    # wheel 빌드 후 설치
    (cd "$PYTHON_DIR" && rm -rf dist/ && pip install build --quiet && python -m build --wheel --quiet)
    WHEEL=$(ls "$PYTHON_DIR/dist"/opendataloader_pdf-*.whl 2>/dev/null | head -1)
    if [ -z "$WHEEL" ]; then
        echo "Error: wheel 파일을 찾을 수 없습니다"
        exit 1
    fi
    pip install "$WHEEL"
    echo "  ✓ wheel 설치 완료: $(basename "$WHEEL")"
fi

# ── 4. 설치 확인 ──
echo ""
echo "[4/4] 설치 확인..."
INSTALLED_VERSION=$(pip show opendataloader-pdf 2>/dev/null | grep "^Version:" | awk '{print $2}')
INSTALLED_LOCATION=$(pip show opendataloader-pdf 2>/dev/null | grep "^Location:" | awk '{print $2}')

if [ -z "$INSTALLED_VERSION" ]; then
    echo "  ✗ 설치 확인 실패"
    exit 1
fi

echo "  버전:    $INSTALLED_VERSION"
echo "  위치:    $INSTALLED_LOCATION"
echo "  CLI:     $(which opendataloader-pdf)"

echo ""
echo "========================================"
echo " 설치 완료!"
echo "========================================"

# ── 5. 테스트 (옵션) ──
if [ "$RUN_TEST" = true ]; then
    echo ""
    echo "[테스트] 샘플 PDF로 변환 테스트..."
    TEST_PDF="$ROOT_DIR/samples/pdf/input1.pdf"
    TEST_OUTPUT="./tmp/opendataloader-test-$$"

    if [ -f "$TEST_PDF" ]; then
        mkdir -p "$TEST_OUTPUT"
        opendataloader-pdf "$TEST_PDF" -o "$TEST_OUTPUT" -f markdown,json
        echo ""
        echo "  출력 파일:"
        ls -la "$TEST_OUTPUT"/*.* 2>/dev/null | while read -r line; do
            echo "    $line"
        done
        echo ""
        echo "  Markdown 미리보기 (첫 10줄):"
        echo "  ────────────────────────────"
        head -10 "$TEST_OUTPUT"/*.md 2>/dev/null | sed 's/^/    /'
        echo "  ────────────────────────────"
        echo ""
        echo "  ✓ 테스트 완료 (출력: $TEST_OUTPUT)"
    else
        echo "  ⚠ 샘플 PDF 없음: $TEST_PDF"
        echo "  수동 테스트: opendataloader-pdf <pdf파일> -o <출력디렉토리> -f markdown,json"
    fi
fi
