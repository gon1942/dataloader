# opendataloader-pdf Hybrid 모드 사용법

## 개요

Hybrid 모드는 **빠른 Java 로컬 처리**와 **AI 백엔드(Docling)**를 결합하여 복잡한 PDF(스캔 문서, 복잡한 테이블, 수식, 차트 이미지 등)를 고품질로 처리합니다.

```
┌─────────────────────────────────────────────────────────┐
│  Terminal 1: Hybrid Server (Python/FastAPI + Docling)    │
│  opendataloader-pdf-hybrid --port 5002                   │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP (JSON)
┌────────────────────▼────────────────────────────────────┐
│  Terminal 2: Client (Java CLI)                           │
│  opendataloader-pdf --hybrid docling-fast file1.pdf      │
│                                                         │
│  1. Triage: 페이지별 Java/Backend 분류                    │
│  2. Backend → Docling이 JSON 반환                         │
│  3. Java → XY-Cut++ 읽기 순서 적용                        │
│  4. Markdown/JSON/HTML 출력                               │
└─────────────────────────────────────────────────────────┘
```

---

## 1. 설치

```bash
# Hybrid 의존성 포함 설치 (docling, fastapi, uvicorn, easyocr)
pip install "opendataloader-pdf[hybrid]"
```

> **필요사항**: Java 11+, Python 3.10+

---

## 2. 서버 시작 (Terminal 1)

### 기본 실행

```bash
opendataloader-pdf-hybrid
```

> 기본 주소: `http://localhost:5002`

### 옵션

| 옵션 | 설명 | 기본값 |
|------|------|--------|
| `--host` | 바인딩 호스트 | `0.0.0.0` |
| `--port` | 포트 번호 | `5002` |
| `--log-level` | 로그 레벨 (`debug`, `info`, `warning`, `error`) | `info` |
| `--force-ocr` | 모든 페이지에 강제 OCR (스캔 PDF용) | `false` |
| `--ocr-lang` | OCR 언어 (쉼표 구분, 예: `"ko,en"`) | EasyOCR 기본 |
| `--enrich-formula` | 수식 추출 활성화 (LaTeX) | `false` |
| `--enrich-picture-description` | 이미지/차트 설명 생성 (SmolVLM) | `false` |
| `--picture-description-prompt` | 이미지 설명 커스텀 프롬프트 | 기본 프롬프트 |

### 사용 시나리오별 서버 실행

```bash
# 일반 디지털 PDF (테이블, 이미지 포함)
opendataloader-pdf-hybrid

# 스캔 PDF (한국어 OCR)
opendataloader-pdf-hybrid --force-ocr --ocr-lang "ko,en"

# 스캔 PDF (중국어 간체 + 영어)
opendataloader-pdf-hybrid --force-ocr --ocr-lang "ch_sim,en"

# 수식이 있는 논문 PDF
opendataloader-pdf-hybrid --enrich-formula

# 차트/이미지 설명이 필요한 PDF
opendataloader-pdf-hybrid --enrich-picture-description

# 모든 기능 결합
opendataloader-pdf-hybrid --force-ocr --ocr-lang "ko,en" --enrich-formula --enrich-picture-description

# 커스텀 포트
opendataloader-pdf-hybrid --port 5003
```

### OCR 지원 언어

| 코드 | 언어 |
|------|------|
| `ko` | 한국어 |
| `en` | 영어 |
| `ja` | 일본어 |
| `ch_sim` | 중국어 간체 |
| `ch_tra` | 중국어 번체 |
| `de` | 독일어 |
| `fr` | 프랑스어 |
| `ar` | 아랍어 |

> 여러 언어: `--ocr-lang "ko,en,ja"`

### 서버 상태 확인

```bash
curl http://localhost:5002/health
# {"status":"ok"}
```

---

## 3. 클라이언트 실행 (Terminal 2)

### 기본 실행

```bash
opendataloader-pdf --hybrid docling-fast file1.pdf file2.pdf folder/ -o output/ -f markdown,json
```

### 클라이언트 옵션

| 옵션 | 설명 | 기본값 |
|------|------|--------|
| `--hybrid` | 백엔드 선택 (`off`, `docling-fast`) | `off` |
| `--hybrid-mode` | Triage 모드 (`auto`, `full`) | `auto` |
| `--hybrid-url` | 백엔드 서버 URL (기본값 덮어쓰기) | `http://localhost:5002` |
| `--hybrid-timeout` | 요청 타임아웃 (ms) | `30000` |
| `--hybrid-fallback` | 백엔드 실패 시 Java 폴백 | `false` |

### Triage 모드

| 모드 | 동작 | 용도 |
|------|------|------|
| `auto` (기본) | 페이지별 동적 분류. 테이블/이미지가 있는 페이지 → Backend, 단순 텍스트 → Java | 일반 PDF |
| `full` | 모든 페이지를 Backend로 전송. Triage 생략 | 스캔 PDF, OCR 문서 |

### 사용 시나리오별 클라이언트 실행

```bash
# 일반 PDF (auto triage)
opendataloader-pdf --hybrid docling-fast file1.pdf -o output/ -f markdown

# 스캔 PDF (모든 페이지를 Backend로)
opendataloader-pdf --hybrid docling-fast --hybrid-mode full scanned.pdf -o output/ -f markdown 

# 특정 페이지만 처리
opendataloader-pdf --hybrid docling-fast --hybrid-mode full doc.pdf --pages "1,3,5-10" -o output/ -f json

# 백엔드가 다른 서버에 있는 경우
opendataloader-pdf --hybrid docling-fast --hybrid-url http://192.168.0.218:8080/v1   -o ./tmp/odl-hybrid/input1  samples/pdf/input1.pdf 
# http://localhost:8080/v1
# 백엔드 실패 시 Java로 폴백
opendataloader-pdf --hybrid docling-fast --hybrid-fallback file.pdf -o output/

# 수식/이미지 enrichments는 서버에서 활성화 + 클라이언트는 full 모드 필요
opendataloader-pdf --hybrid docling-fast --hybrid-mode full paper.pdf -o output/ -f markdown,json
```

---

## 4. Python SDK 사용

```python
import opendataloader_pdf

# 일반 PDF (auto triage)
opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf"],
    output_dir="output/",
    format="markdown,json",
    hybrid="docling-fast"
)

# 스캔 PDF (full mode)
opendataloader_pdf.convert(
    input_path=["scanned.pdf"],
    output_dir="output/",
    format="markdown",
    hybrid="docling-fast",
    hybrid_mode="full"
)
```

---

## 5. Node.js SDK 사용

```typescript
import { convert } from '@opendataloader/pdf';

// 일반 PDF
await convert(['file1.pdf', 'file2.pdf'], {
  outputDir: 'output/',
  format: 'markdown,json',
  hybrid: 'docling-fast'
});

// 스캔 PDF (full mode)
await convert(['scanned.pdf'], {
  outputDir: 'output/',
  format: 'markdown',
  hybrid: 'docling-fast',
  hybridMode: 'full'
});
```

---

## 6. 처리 흐름

```
PDF 입력
  │
  ▼
┌─ HybridDocumentProcessor ─────────────────────────────┐
│                                                        │
│  [Phase 1] 콘텐츠 필터링                                │
│    → ContentFilterProcessor: 중복/미세/숨김 텍스트 제거   │
│                                                        │
│  [Phase 2] Triage (auto 모드일 때만)                    │
│    → TriageProcessor: 페이지별 JAVA/BACKEND 분류         │
│      - TableBorder 감지 → BACKEND (신뢰도 1.0)         │
│      - 벡터 그래픽 감지 → BACKEND (0.95)                │
│      - 텍스트 패턴 감지 → BACKEND (0.9)                 │
│      - 큰 이미지 → BACKEND (0.85)                       │
│      - 단순 텍스트 → JAVA (0.9)                         │
│                                                        │
│  [Phase 3] 처리                                        │
│    JAVA 페이지:                                         │
│      → 테이블/텍스트/단락/제목/리스트 감지                 │
│    BACKEND 페이지:                                      │
│      → HTTP로 PDF 전송 → Docling 처리 → JSON 수신        │
│      → DoclingSchemaTransformer: JSON → IObject 변환     │
│                                                        │
│  [Phase 4] 병합 + 후처리                                │
│    → mergeResults(): 페이지별 결과 병합                   │
│    → HeaderFooter, List, Table, Heading 교차 페이지 처리  │
│                                                        │
└────────────────────────────────────────────────────────┘
  │
  ▼
┌─ DocumentProcessor ────────────────────────────────────┐
│  sortContents() → XYCutPlusPlusSorter (모든 페이지)     │
│  ContentSanitizer → PII 마스킹                         │
│  generateOutputs() → Markdown, JSON, HTML, Text, PDF    │
└────────────────────────────────────────────────────────┘
  │
  ▼
출력 파일
```

---

## 7. API 엔드포인트 (서버)

### `GET /health`

서버 상태 확인.

```bash
curl http://localhost:5002/health
```

```json
{"status": "ok"}
```

### `POST /v1/convert/file`

PDF를 JSON으로 변환.

```bash
curl -X POST http://localhost:5002/v1/convert/file \
  -F "files=@document.pdf" \
  -F "page_ranges=1-5"
```

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `files` | File (required) | PDF 파일 (multipart/form-data) |
| `page_ranges` | String (optional) | 페이지 범위 (`"1-5"` 형식) |

**응답:**

```json
{
  "status": "success",
  "document": { "json_content": { ... } },
  "processing_time": 1.23,
  "errors": [],
  "failed_pages": []
}
```

| 상태 | 설명 |
|------|------|
| `success` | 모든 페이지 변환 성공 |
| `partial_success` | 일부 페이지 실패 (failed_pages 참조) |
| `failure` | 변환 실패 |

**제한사항:**
- 최대 파일 크기: **100MB**
- 파일 크기 초과 시 HTTP 413 반환
- 서버 미초기화 시 HTTP 503 반환

---

## 8. 수식 추출 (LaTeX)

서버에서 `--enrich-formula` 활성화 + 클라이언트에서 `--hybrid-mode full` 필요.

**서버:**
```bash
opendataloader-pdf-hybrid --enrich-formula
```

**클라이언트:**
```bash
opendataloader-pdf --hybrid docling-fast --hybrid-mode full paper.pdf -o output/ -f json
```

**출력 (JSON):**
```json
{
  "type": "formula",
  "page number": 1,
  "bounding box": [226.2, 144.7, 377.1, 168.7],
  "content": "\\frac{f(x+h) - f(x)}{h}"
}
```

---

## 9. 이미지/차트 설명 생성

서버에서 `--enrich-picture-description` 활성화 + 클라이언트에서 `--hybrid-mode full` 필요.

> SmolVLM-256M (가벼운 비전 모델) 사용. GPU 권장.

**서버:**
```bash
opendataloader-pdf-hybrid --enrich-picture-description
```

**커스텀 프롬프트:**
```bash
opendataloader-pdf-hybrid --enrich-picture-description \
  --picture-description-prompt "Describe this chart. Include all data values, labels, and trends."
```

**클라이언트:**
```bash
opendataloader-pdf --hybrid docling-fast --hybrid-mode full report.pdf -o output/ -f json
```

**출력 (JSON):**
```json
{
  "type": "picture",
  "page number": 1,
  "bounding box": [72.0, 400.0, 540.0, 650.0],
  "description": "A bar chart showing waste generation by region from 2016 to 2030..."
}
```

---

## 10. 트러블슈팅

### 서버 연결 실패

```
Error: Hybrid server is not available at http://localhost:5002
```

→ Terminal 1에서 서버를 먼저 시작하세요:
```bash
opendataloader-pdf-hybrid
```

### 의존성 누락

```
ImportError: Missing dependencies: uvicorn, fastapi, docling
```

→ Hybrid 의존성 설치:
```bash
pip install "opendataloader-pdf[hybrid]"
```

### OCR 결과에 깨진 문자

서버가 자동으로 Unicode 서러게이트를 정리합니다. 만약 여전히 문제가 있으면:
```bash
opendataloader-pdf --hybrid docling-fast --hybrid-mode full doc.pdf -o output/ --replace-invalid-chars " "
```

### 백엔드 타임아웃

큰 PDF나 OCR 문서는 처리 시간이 깁니다. 타임아웃을 늘리세요:
```bash
opendataloader-pdf --hybrid docling-fast --hybrid-timeout 60000 large.pdf -o output/
```

### 백엔드 실패 시 처리

기본적으로 백엔드 실패 시 에러가 발생합니다. Java 폴백을 활성화하면:
```bash
opendataloader-pdf --hybrid docling-fast --hybrid-fallback file.pdf -o output/
```

### GPU 미감지

서버 시작 시 GPU 로그를 확인하세요:
```
GPU detected: NVIDIA RTX 4090 (CUDA 12.1)
```
또는:
```
No GPU detected, using CPU.
```

GPU가 없어도 동작하지만 OCR/수식/이미지 설명 속도가 느립니다.

---

## 11. 빠른 참조

### 서버 & 클라이언트 조합

| 문서 유형 | 서버 명령어 | 클라이언트 명령어 |
|-----------|-----------|-----------------|
| 일반 디지털 PDF | `opendataloader-pdf-hybrid` | `opendataloader-pdf --hybrid docling-fast file.pdf -o out/ -f markdown` |
| 복잡한 테이블 | `opendataloader-pdf-hybrid` | `opendataloader-pdf --hybrid docling-fast file.pdf -o out/ -f markdown` |
| 스캔 PDF (한국어) | `opendataloader-pdf-hybrid --force-ocr --ocr-lang "ko,en"` | `opendataloader-pdf --hybrid docling-fast --hybrid-mode full scan.pdf -o out/ -f markdown` |
| 수식 논문 | `opendataloader-pdf-hybrid --enrich-formula` | `opendataloader-pdf --hybrid docling-fast --hybrid-mode full paper.pdf -o out/ -f json` |
| 차트 설명 필요 | `opendataloader-pdf-hybrid --enrich-picture-description` | `opendataloader-pdf --hybrid docling-fast --hybrid-mode full report.pdf -o out/ -f json` |
| 전체 기능 | `opendataloader-pdf-hybrid --force-ocr --ocr-lang "ko,en" --enrich-formula --enrich-picture-description` | `opendataloader-pdf --hybrid docling-fast --hybrid-mode full doc.pdf -o out/ -f markdown,json` |

### 기본 포트

| 백엔드 | 기본 URL |
|--------|---------|
| docling-fast | `http://localhost:5002` |
| hancom | `https://dataloader.cloud.hancom.com/studio-lite/api` |
