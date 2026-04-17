# OpenDataLoader PDF 사용 가이드

## 1. 프로젝트 분석

이 프로젝트는 `PDF -> 구조화 데이터` 변환 엔진을 중심으로 한 모노레포입니다.

- Java: 실제 PDF 분석 엔진과 CLI
- Python: Java CLI JAR을 번들해서 실행하는 래퍼 패키지
- Node.js: Java CLI JAR을 번들해서 실행하는 래퍼 패키지
- scripts: 빌드, 테스트, 설치 자동화 스크립트

핵심 포인트는 다음입니다.

- 실제 추출 로직은 Java에 있습니다.
- `java/opendataloader-pdf-cli/target/...jar`가 직접 실행 가능한 CLI입니다.
- `opendataloader-pdf` 명령은 Python 패키지 안에 포함된 `opendataloader-pdf-cli.jar`를 실행합니다.
- 따라서 Java 코드를 수정한 뒤 설치형 CLI로 테스트하려면 Java만 빌드하는 것으로 끝나지 않고 Python wheel 재빌드/재설치가 필요합니다.

구조 요약:

```text
opendataloader-pdf/
├── java/
│   ├── opendataloader-pdf-core/   # 실제 PDF 처리 엔진
│   └── opendataloader-pdf-cli/    # java -jar 로 실행하는 CLI
├── python/opendataloader-pdf/     # Python 패키지, 내부에 Java JAR 번들
├── node/opendataloader-pdf/       # Node 패키지, 내부에 Java JAR 번들
└── scripts/                       # 빌드/설치/테스트 스크립트
```

## 2. 주요 실행 방식

### 2.1 Java JAR 직접 실행 

가장 정확하게 "Java 코드"를 검증하는 방법입니다.

```bash
빌드 
./java/mvnw -q -f java/pom.xml -pl opendataloader-pdf-cli -am package -DskipTests 

java -jar java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0.jar \
  -f json,markdown \
  -o ./tmp/odl-test/input8-xycut \
  --reading-order xycut \
  testfile/input8.pdf
```

### 2.2 Python 설치형 CLI 실행

아래 명령은 현재 설치된 Python 패키지 안의 번들 JAR을 실행합니다.

```bash
opendataloader-pdf \
  -f markdown,json,html,pdf \
  -o ./tmp/odl-test/xycut-input9 \
  --reading-order xycut \
  testfile/input9.pdf
```

주의:

- 설치형 CLI는 `java/opendataloader-pdf-cli/target/...jar`를 직접 쓰지 않습니다.
- Java 수정 후 `opendataloader-pdf`로 테스트하려면 wheel 재빌드와 재설치가 필요합니다.

## 3. 요구사항

개발 및 설치에 필요한 기본 도구:

- Java 11+
- Maven 또는 `java/mvnw`
- Python 3.10+
- `pip`
- `uv`
- Node.js, `pnpm`:
  - 전체 빌드나 Node 패키지까지 다룰 때 필요

확인 예시:

```bash
java -version
python3 --version
pip --version
uv --version
```

## 4. 빌드 스크립트 정리

### 4.1 전체 빌드

```bash
./scripts/build-all.sh
./scripts/build-all.sh 0.0.0
 -> ./tmp/releases/<0.0.0> 
```

동작:

- Java 빌드/테스트
- Python wheel 빌드/테스트
- Node 패키지 빌드/테스트

중요:

- 이 스크립트는 "빌드"이지 "설치"가 아닙니다.
- 로컬 수정본으로 산출물을 만들지만, 현재 환경에 자동 설치하지는 않습니다.

### 4.2 Java만 빌드

```bash
./scripts/build-java.sh
```

또는:

```bash
cd java
./mvnw -B clean package -P release
```

산출물:

- `java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-<version>.jar`

### 4.3 Python만 빌드

```bash
./scripts/build-python.sh
```

산출물:

- `python/opendataloader-pdf/dist/opendataloader_pdf-<version>-py3-none-any.whl`

이 wheel 안에 Java CLI JAR이 포함됩니다.

## 5. 개발 테스트용 빌드 > 배포 > 설치 > 테스트

로컬 Java 수정본을 포함한 wheel을 만들고, 다시 설치한 뒤, 바로 테스트하려면 아래 스크립트를 사용합니다.

```bash
./scripts/dev-package-install-test.sh
./scripts/dev-package-install-test.sh 0.0.0
./scripts/dev-package-install-test.sh 0.0.1-dev
```

동작 순서:

1. Java 버전 설정
2. Java 빌드
3. Python wheel 빌드
4. `tmp/releases/<version>`에 JAR/wheel 보관
5. 기존 `opendataloader-pdf` 제거 후 새 wheel 설치
6. 샘플 PDF로 스모크 테스트

환경변수로 테스트 파일을 바꿀 수 있습니다.

```bash
SAMPLE_PDF=./testfile/input8.pdf \
FORMAT=markdown,json,html,pdf \
TEST_OUTPUT_DIR=./tmp/odl-test/install-test \
./scripts/dev-package-install-test.sh 0.0.1-dev
```

release 디렉터리 예시:

```text
tmp/releases/0.0.1-dev/
├── opendataloader-pdf-cli-0.0.1-dev.jar
└── opendataloader_pdf-0.0.1-dev-py3-none-any.whl
```

## 6. 운영 설치용 스크립트

소스 재빌드 없이, 이미 만들어진 wheel 또는 release 디렉터리로 설치할 때 사용합니다.

```bash
./scripts/install-production.sh ./tmp/releases/0.0.1-dev
./scripts/install-production.sh ./tmp/releases/0.0.1-dev/opendataloader_pdf-0.0.1-dev-py3-none-any.whl
```

설치 후 smoke test도 가능:

```bash
SMOKE_PDF=./testfile/input8.pdf \
./scripts/install-production.sh ./tmp/releases/0.0.1-dev
```

동작:

- 기존 `opendataloader-pdf` 제거
- 전달받은 wheel 설치
- `opendataloader-pdf --help` 점검
- 선택적으로 샘플 PDF 실행

## 7. 설치

### 7.1 로컬 개발 산출물 설치

wheel 직접 설치:

```bash
pip install python/opendataloader-pdf/dist/opendataloader_pdf-0.0.0-py3-none-any.whl
```

release 디렉터리에서 설치:

```bash
pip install ./tmp/releases/0.0.1-dev/opendataloader_pdf-0.0.1-dev-py3-none-any.whl
```

### 7.2 editable 설치

패키지 소스 자체를 설치할 때:

```bash
pip install -e ./python/opendataloader-pdf
```

주의:

- editable 설치라도 실제 실행 JAR은 패키지 내부 번들 파일을 사용합니다.
- Java를 다시 빌드했다면 wheel 재설치 또는 적절한 재번들 단계가 필요합니다.

## 8. 삭제

Python 패키지 삭제:

```bash
pip uninstall opendataloader-pdf -y
```

빌드 산출물 정리 예시:

```bash
rm -rf python/opendataloader-pdf/dist
rm -rf tmp/releases
rm -rf tmp/odl-test
```

Java target 정리:

```bash
cd java
./mvnw clean
```

## 9. 기본 사용법

### 9.1 단일 PDF 변환

```bash
opendataloader-pdf \
  -f markdown,json \
  -o ./tmp/odl-test/input8 \
  testfile/input8.pdf
```

### 9.2 XYCut 읽기 순서 사용

```bash
opendataloader-pdf \
  -f markdown,json,html,pdf \
  -o ./tmp/odl-test/xycut-input8 \
  --reading-order xycut \
  testfile/input8.pdf
```

### 9.3 Java JAR 직접 사용

```bash
java -jar java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0.jar \
  -f markdown,json,html,pdf \
  -o ./tmp/odl-test/xycut-input8 \
  --reading-order xycut \
  testfile/input8.pdf
```

### 9.4 여러 포맷 생성

```bash
opendataloader-pdf \
  -f markdown,json,html,pdf \
  -o ./tmp/odl-test/full-output \
  testfile/input9.pdf
```

생성 가능한 대표 포맷:

- `json`
- `text`
- `html`
- `pdf`
- `markdown`
- `markdown-with-html`
- `markdown-with-images`

## 10. 자주 쓰는 옵션

- `-f, --format`: 출력 포맷 목록
- `-o, --output-dir`: 출력 디렉터리
- `-p, --password`: 암호화 PDF 비밀번호
- `-q, --quiet`: 로그 최소화
- `--reading-order xycut|off`: 읽기 순서 알고리즘
- `--table-method default|cluster`: 표 추출 방식
- `--use-struct-tree`: tagged PDF 구조 트리 활용
- `--pages "1,3,5-7"`: 특정 페이지 선택
- `--image-output off|embedded|external`: 이미지 출력 방식
- `--image-format png|jpeg`: 이미지 형식
- `--include-header-footer`: 헤더/푸터 포함
- `--detect-strikethrough`: 취소선 감지
- `--hybrid docling-fast`: 하이브리드 백엔드 사용

# 개별 파일
opendataloader-pdf file1.pdf file2.pdf
# 폴더 (폴더 내 모든 PDF 처리)
opendataloader-pdf folder/
# 혼합
opendataloader-pdf file1.pdf folder/ file2.pdf
# 특정 페이지만 (1, 3, 5~7페이지)
opendataloader-pdf file1.pdf --pages "1,3,5-7"
-o로 출력 디렉토리를 지정하면 결과가 해당 폴더에 생성됩니다:
opendataloader-pdf testfile/ -o /tmp/output -f markdown


예시:

```bash
opendataloader-pdf \
  -f markdown,json \
  --pages "1-3" \
  --table-method cluster \
  --reading-order xycut \
  -o ./tmp/odl-test/input10 \
  testfile/input10.pdf
```

## 11. Python 코드에서 사용

```python
import opendataloader_pdf

opendataloader_pdf.convert(
    input_path=["testfile/input8.pdf"],
    output_dir="./tmp/odl-test/python-api",
    format="markdown,json",
    reading_order="xycut",
)
```


## 12. 추천 작업 흐름

### Java 코드 수정 후 빠른 확인

```bash
./scripts/build-java.sh
java -jar java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0.jar \
  -f markdown,json \
  -o ./tmp/odl-test/debug \
  --reading-order xycut \
  testfile/input8.pdf
```

### 설치형 CLI까지 포함해서 확인

```bash
./scripts/dev-package-install-test.sh 0.0.1-dev
opendataloader-pdf \
  -f markdown,json \
  -o ./tmp/odl-test/debug-install \
  --reading-order xycut \
  testfile/input8.pdf
```

### 운영 반영

```bash
./scripts/dev-package-install-test.sh 0.0.1
./scripts/install-production.sh ./tmp/releases/0.0.1
```

## 13. 참고 스크립트

- `scripts/build-all.sh`: Java/Python/Node 전체 빌드
- `scripts/build-java.sh`: Java 빌드
- `scripts/build-python.sh`: Python wheel 빌드
- `scripts/test-java.sh`: Java 테스트
- `scripts/test-python.sh`: Python 테스트
- `scripts/run-cli.sh`: Java CLI JAR 직접 실행
- `scripts/dev-package-install-test.sh`: 개발용 빌드/설치/테스트
- `scripts/install-production.sh`: 운영 설치
- `scripts/ryan_reinstall-local.sh`: Python 패키지 재설치 보조 스크립트



##  귀찮이즘.


./scripts/dev-package-install-test.sh 0.0.0

./scripts/build-all.sh 0.0.0
pip uninstall opendataloader-pdf -y
pip install python/opendataloader-pdf/dist/opendataloader_pdf-0.0.0-py3-none-any.whl



cd /home/gon/work/ttt3/opendataloader-pdf/java
./mvnw -pl opendataloader-pdf-cli -am -DskipTests package

cd /home/gon/work/ttt3/opendataloader-pdf
java -Djava.awt.headless=true -jar java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.1.jar \
  -f html,json \
  -o ./tmp/odl-xycut/input10-fix2 \
  testfile/input10.pdf




java -Djava.awt.headless=true -jar /home/gon/.airun_venv/lib/python3.12/site-packages/opendataloader_pdf/jar/opendataloader-pdf-cli.jar \
  -f html,json \
  -o ./tmp/odl-xycut/input10-fix2 \
  testfile/input10.pdf


❌ OpenDataLoader-PDF 처리 실패, PyPDF2 방식으로 전환: Command '['java', '-Djava.awt.headless=true', '-jar', '/home/gon/.airun_venv/lib/python3.12/site-packages/opendataloader_pdf/jar/opendataloader-pdf-cli.jar', '/home/gon/work/airun_proj/airun_rag_docs/admin/a1.pdf', '--output-dir', '/home/gon/work/airun_proj/airun_rag_docs/admin/.extracts/a1', '--format', 'markdown-with-images,json,html', '--quiet', '--image-output', '.external', '--image-dir', '/home/gon/work/airun_proj/airun_rag_docs/admin/.extracts/a1', '--image-description', '--image-description-url', 'https://app.hamonize.com/gemma4/v1/chat/completions', '--image-description-model', 'gemma-4', '--image-description-language', 'ko', '--image-description-timeout', '120000', '--include-header-footer']' returned non-zero exit status 2.
📄 PyPDF2를 사용하여 PDF 처리 시작: /home/gon/work/airun_proj/airun_rag_docs/admin/a1.pdf, useOcr: False




opendataloader-pdf -f markdown,json,html,pdf --reading-order xycut  -o ./tmp/odl-xycut/input10  testfile/input10.pdf












opendataloader-pdf-hybrid는 opendataloader-pdf가 --hybrid docling-fast 모드에서 붙는 외부 Python backend 서버입니다.

역할:

PDF를 받아서
Docling 기반 문서 파싱 수행
OCR 수행
표/문단/레이아웃 구조를 추출
그 결과를 OpenDataLoader가 읽는 json_content 형태로 반환
즉 구조는 이렇습니다.

opendataloader-pdf (Java CLI)
  -> opendataloader-pdf-hybrid (Python server)
    -> Docling + OCR backend
기본 docling-fast 경로에서는 이 서버가 보통 localhost:5002에서 뜹니다.

이 프로젝트 안의 구현 위치:

hybrid_server.py
정리하면:

opendataloader-pdf: 메인 CLI/라이브러리
opendataloader-pdf-hybrid: hybrid 모드용 외부 서버
즉 --hybrid docling-fast를 쓸 때 실제 OCR/파싱을 대신 수행하는 서버가 바로 이겁니다.









---- 이미지 설명 
이 옵션은 opendataloader-pdf 클라이언트 옵션이 아니라 opendataloader-pdf-hybrid 서버 옵션입니다.

이미지 설명 관련 옵션:

--enrich-picture-description: 이미지/차트 설명 생성 활성화. 기본값 false. 구현은 hybrid_server.py (line 471) 에 있고, 도움말은 “alt text generation using SmolVLM”으로 되어 있습니다.
--no-enrich-picture-description: 위 기능 비활성화. hybrid_server.py (line 477)
--picture-description-prompt: 이미지 설명용 커스텀 프롬프트 지정. hybrid_server.py (line 482)
중요한 점:

서버에서 --enrich-picture-description를 켜도, 클라이언트는 반드시 --hybrid-mode full로 호출해야 합니다. 아니면 설명 생성이 건너뛰어집니다. 이 제약은 CLAUDE.md (line 7), README.md (line 246), use_hybird.md (line 333) 에 명시돼 있습니다.
예시는 이렇게 쓰면 됩니다:

opendataloader-pdf-hybrid --enrich-picture-description
opendataloader-pdf --hybrid docling-fast --hybrid-mode full testfile/img1.pdf -o ./tmp/odl-img/ -f json,markdown,html
문서 기준으로는 설명 결과가 JSON의 "description" 필드와 Markdown caption으로 나옵니다. 관련 설명은 README.md (line 254), content/docs/faq.mdx (line 283), use_hybird.md (line 339) 에 있습니다.

원하면 제가 지금 이 저장소 기준으로 opendataloader-pdf --help / opendataloader-pdf-hybrid --help까지 직접 실행해서 실제 노출 옵션도 확인해드리겠습니다.