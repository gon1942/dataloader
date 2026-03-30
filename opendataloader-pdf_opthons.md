# opendataloader-pdf 옵션 사용 현황

기준 파일:

- AIRUN: [`/home/gon/work/airun_proj/git/airun/utils.py`](/home/gon/work/airun_proj/git/airun/utils.py)
- Python wrapper: [`/home/gon/work/ttt4/dataloader/python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py`](/home/gon/work/ttt4/dataloader/python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py)

이 문서는 현재 AIRUN이 `opendataloader-pdf`를 호출할 때:

- 실제로 읽고 사용하는 옵션
- 기본값으로 동작하므로 `airun.conf`에 굳이 적지 않아도 되는 옵션
- 아직 AIRUN에서 연결하지 않은 옵션

을 구분해서 정리한 것이다.

## 진입 조건

AIRUN에서 PDF 파서를 `opendataloader-pdf`로 선택하면 [`utils.py`](/home/gon/work/airun_proj/git/airun/utils.py#L6239)의 [`_extract_with_opendataloader_pdf()`](/home/gon/work/airun_proj/git/airun/utils.py#L6774)가 실행된다.

## 현재 실제로 사용하는 옵션

### AIRUN 함수 인자

함수 시그니처는 아래와 같다.

- `pdf_path`
- `use_ocr`
- `lang`
- `add_table`
- `return_documents`
- `disable_header_footer_detection`

이 중 `convert()`에 직접 반영되는 것은 일부만 있다.

### convert()에 직접 전달되는 옵션

현재 [`utils.py`](/home/gon/work/airun_proj/git/airun/utils.py#L6858) 기준으로 `convert_kwargs`에 들어가는 항목은 아래와 같다.

| 옵션 | 현재 값 | 설명 |
|---|---|---|
| `input_path` | `pdf_path` | 입력 PDF 경로 |
| `output_dir` | `.extracts/<문서명>` | Markdown/JSON 출력 디렉터리 |
| `format` | `markdown-with-images,json` | Markdown과 JSON 생성 |
| `quiet` | `not show_opendataloader_logs` | JVM 로그 출력 여부 |
| `image_output` | `external` | 이미지 외부 파일 저장 |
| `image_dir` | `.extracts/<문서명>` | 이미지 저장 디렉터리 |
| `include_header_footer` | `not disable_header_footer_detection` | 머릿말/꼬릿말 포함 여부 |
| `image_description` | 조건부 | 이미지 설명 생성 여부 |
| `image_description_url` | 조건부 | 이미지 설명 API URL |
| `image_description_model` | 조건부 | 이미지 설명 모델 |
| `image_description_prompt` | 조건부 | 이미지 설명 프롬프트 |
| `image_description_language` | 조건부 | 이미지 설명 언어 |
| `image_description_timeout` | 조건부 | 이미지 설명 타임아웃 |
| `image_format` | 조건부 | 이미지 포맷 |
| `keep_line_breaks` | 조건부 | 줄바꿈 유지 |
| `sanitize` | 조건부 | 민감정보 마스킹 |
| `use_struct_tree` | 조건부 | 구조 트리 사용 |
| `detect_strikethrough` | 조건부 | 취소선 감지 |
| `hybrid_fallback` | 조건부 | hybrid 실패 시 fallback |
| `table_method` | 조건부 | 표 검출 방식 |
| `reading_order` | 조건부 | 읽기 순서 방식 |
| `replace_invalid_chars` | 조건부 | 잘못된 문자 대체 |
| `pages` | 조건부 | 일부 페이지 선택 |
| `markdown_page_separator` | 조건부 | Markdown 페이지 구분자 |
| `text_page_separator` | 조건부 | Text 페이지 구분자 |
| `html_page_separator` | 조건부 | HTML 페이지 구분자 |
| `hybrid` | 조건부 | hybrid 백엔드 |
| `hybrid_mode` | 조건부 | hybrid triage 모드 |
| `hybrid_url` | 조건부 | hybrid 서버 URL |
| `hybrid_timeout` | 조건부 | hybrid 타임아웃 |
| `content_safety_off` | 조건부 | content safety 제어 |

여기서 `조건부`는 환경변수/설정값이 비어 있지 않거나 true일 때만 `convert()`에 추가되는 항목을 뜻한다.

## 현재 AIRUN이 읽는 환경변수

현재 [`utils.py`](/home/gon/work/airun_proj/git/airun/utils.py#L6813) 는 아래 `OPENDATALOADER_PDF_*` 환경변수를 읽는다.

### 운영에서 보통 유지할 최소 옵션

이 항목들은 `airun.conf`에 명시해 둘 가치가 있다.

| 환경변수 | convert 옵션 | 기본값 | 운영 권장 |
|---|---|---|---|
| `OPENDATALOADER_PDF_SHOW_LOGS` | `quiet`에 반영 | `false` | 유지 |
| `OPENDATALOADER_PDF_IMAGE_DESCRIPTION` | `image_description` | `true` | 유지 |
| `OPENDATALOADER_PDF_IMAGE_DESCRIPTION_MODEL` | `image_description_model` | `airun-vision:latest` | 유지 |
| `OPENDATALOADER_PDF_IMAGE_DESCRIPTION_LANGUAGE` | `image_description_language` | `ko` | 유지 |

### 필요할 때만 명시할 옵션

이 항목들은 특정 운영 요구가 있을 때만 `airun.conf`에 넣으면 된다.

| 환경변수 | convert 옵션 | 기본값 | 사용 시점 |
|---|---|---|---|
| `OPENDATALOADER_PDF_IMAGE_DESCRIPTION_URL` | `image_description_url` | `""` | 기본 API 대신 별도 URL 사용 시 |
| `OPENDATALOADER_PDF_IMAGE_DESCRIPTION_PROMPT` | `image_description_prompt` | `""` | 프롬프트 커스텀 필요 시 |
| `OPENDATALOADER_PDF_IMAGE_DESCRIPTION_TIMEOUT` | `image_description_timeout` | `""` | 타임아웃 조정 필요 시 |
| `OPENDATALOADER_PDF_FORMAT` | `format` | `markdown-with-images,json` | 출력 포맷을 바꿀 때만 |
| `OPENDATALOADER_PDF_IMAGE_OUTPUT` | `image_output` | `external` | embedded/off로 바꿀 때만 |
| `OPENDATALOADER_PDF_IMAGE_FORMAT` | `image_format` | 패키지 기본값 | png/jpeg 제어 필요 시 |
| `OPENDATALOADER_PDF_KEEP_LINE_BREAKS` | `keep_line_breaks` | `false` | 원문 줄바꿈 보존 필요 시 |
| `OPENDATALOADER_PDF_SANITIZE` | `sanitize` | `false` | 민감정보 마스킹 필요 시 |
| `OPENDATALOADER_PDF_USE_STRUCT_TREE` | `use_struct_tree` | `false` | tagged PDF 구조 사용 시 |
| `OPENDATALOADER_PDF_DETECT_STRIKETHROUGH` | `detect_strikethrough` | `false` | 취소선 감지 필요 시 |
| `OPENDATALOADER_PDF_TABLE_METHOD` | `table_method` | `""` | 표 검출 방식 조정 시 |
| `OPENDATALOADER_PDF_READING_ORDER` | `reading_order` | `""` | 읽기 순서 조정 시 |
| `OPENDATALOADER_PDF_REPLACE_INVALID_CHARS` | `replace_invalid_chars` | `""` | 잘못된 문자 대체 필요 시 |
| `OPENDATALOADER_PDF_PAGES` | `pages` | `""` | 일부 페이지만 처리할 때 |
| `OPENDATALOADER_PDF_MARKDOWN_PAGE_SEPARATOR` | `markdown_page_separator` | `""` | Markdown 페이지 구분 필요 시 |
| `OPENDATALOADER_PDF_TEXT_PAGE_SEPARATOR` | `text_page_separator` | `""` | Text 페이지 구분 필요 시 |
| `OPENDATALOADER_PDF_HTML_PAGE_SEPARATOR` | `html_page_separator` | `""` | HTML 페이지 구분 필요 시 |
| `OPENDATALOADER_PDF_CONTENT_SAFETY_OFF` | `content_safety_off` | `""` | hidden-text 등 필터 제어 시 |
| `OPENDATALOADER_PDF_HYBRID` | `hybrid` | `""` | hybrid 모드 사용 시 |
| `OPENDATALOADER_PDF_HYBRID_MODE` | `hybrid_mode` | `""` | hybrid triage 모드 조정 시 |
| `OPENDATALOADER_PDF_HYBRID_URL` | `hybrid_url` | `""` | hybrid 서버 별도 지정 시 |
| `OPENDATALOADER_PDF_HYBRID_TIMEOUT` | `hybrid_timeout` | `""` | hybrid 타임아웃 조정 시 |
| `OPENDATALOADER_PDF_HYBRID_FALLBACK` | `hybrid_fallback` | `false` | hybrid 실패 fallback 허용 시 |

## airun.conf에 권장하는 최소 설정

운영 기준으로는 아래 정도만 `airun.conf`에 명시하는 것을 권장한다.

```bash
# OpenDataLoader PDF: JVM 로그 출력 여부
# true면 Java 로그를 직접 출력, false면 quiet 모드로 실행
export OPENDATALOADER_PDF_SHOW_LOGS="false"

# OpenDataLoader PDF: 이미지 설명 생성 여부
# true면 이미지 설명 API를 호출해서 JSON/Markdown alt text에 반영
export OPENDATALOADER_PDF_IMAGE_DESCRIPTION="true"

# OpenDataLoader PDF: 이미지 설명 모델
# 기본 운영 모델. 허용 모델 예: airun-vision:latest
export OPENDATALOADER_PDF_IMAGE_DESCRIPTION_MODEL="airun-vision:latest"

# OpenDataLoader PDF: 이미지 설명 언어
# ko, en 등. 현재 운영 기본은 ko
export OPENDATALOADER_PDF_IMAGE_DESCRIPTION_LANGUAGE="ko"
```

## 필요 시 추가하는 옵션 예시

```bash
# OpenDataLoader PDF: 이미지 설명 API URL
# 기본 URL 대신 별도 vision endpoint를 사용할 때만 지정
export OPENDATALOADER_PDF_IMAGE_DESCRIPTION_URL=""

# OpenDataLoader PDF: 이미지 설명 프롬프트
# 기본 설명 스타일 대신 커스텀 프롬프트가 필요할 때만 지정
export OPENDATALOADER_PDF_IMAGE_DESCRIPTION_PROMPT=""

# OpenDataLoader PDF: 이미지 설명 타임아웃(ms)
# 느린 API 환경에서만 조정
export OPENDATALOADER_PDF_IMAGE_DESCRIPTION_TIMEOUT=""
```

## airun.conf에 굳이 쓰지 않아도 되는 옵션

아래는 현재 AIRUN이 읽기는 하지만, 기본값으로도 충분히 동작하므로 운영 `airun.conf`에는 보통 넣지 않는 편이 낫다.

- `OPENDATALOADER_PDF_FORMAT`
- `OPENDATALOADER_PDF_IMAGE_OUTPUT`
- `OPENDATALOADER_PDF_IMAGE_FORMAT`
- `OPENDATALOADER_PDF_KEEP_LINE_BREAKS`
- `OPENDATALOADER_PDF_SANITIZE`
- `OPENDATALOADER_PDF_USE_STRUCT_TREE`
- `OPENDATALOADER_PDF_DETECT_STRIKETHROUGH`
- `OPENDATALOADER_PDF_TABLE_METHOD`
- `OPENDATALOADER_PDF_READING_ORDER`
- `OPENDATALOADER_PDF_REPLACE_INVALID_CHARS`
- `OPENDATALOADER_PDF_PAGES`
- `OPENDATALOADER_PDF_MARKDOWN_PAGE_SEPARATOR`
- `OPENDATALOADER_PDF_TEXT_PAGE_SEPARATOR`
- `OPENDATALOADER_PDF_HTML_PAGE_SEPARATOR`
- `OPENDATALOADER_PDF_CONTENT_SAFETY_OFF`
- `OPENDATALOADER_PDF_HYBRID`
- `OPENDATALOADER_PDF_HYBRID_MODE`
- `OPENDATALOADER_PDF_HYBRID_URL`
- `OPENDATALOADER_PDF_HYBRID_TIMEOUT`
- `OPENDATALOADER_PDF_HYBRID_FALLBACK`

## 아직 AIRUN에서 직접 쓰지 않는 함수 인자

아래 인자는 `_extract_with_opendataloader_pdf()` 시그니처에는 있으나 `convert()`에 직접 연결되지 않는다.

| 인자 | 현재 상태 | 비고 |
|---|---|---|
| `use_ocr` | 직접 미사용 | local 모드 기준 |
| `lang` | 직접 미사용 | OCR 언어 전달 안 함 |
| `add_table` | 직접 미사용 | 파서 기본 동작 사용 |
| `return_documents` | AIRUN 후처리에만 사용 | `Document` 생성 여부 판단용 |

## 패키지가 지원하지만 AIRUN에서 아직 연결하지 않은 옵션

현재 wrapper가 지원하지만 AIRUN에서 읽지도 않고 전달하지도 않는 대표 옵션은 아래다.

| 옵션 | 설명 |
|---|---|
| `password` | 암호화 PDF 비밀번호 |

## 결론

현재 AIRUN은 `opendataloader-pdf`의 옵션을 꽤 많이 읽도록 되어 있다. 다만 운영 `airun.conf`에는 그 전체를 다 적는 것이 아니라, 실제로 운영자가 조정할 항목만 남기는 것이 맞다.

운영 권장 최소 설정은 아래 4개다.

- `OPENDATALOADER_PDF_SHOW_LOGS`
- `OPENDATALOADER_PDF_IMAGE_DESCRIPTION`
- `OPENDATALOADER_PDF_IMAGE_DESCRIPTION_MODEL`
- `OPENDATALOADER_PDF_IMAGE_DESCRIPTION_LANGUAGE`

추가 항목은 필요할 때만 명시하는 방식이 가장 관리하기 쉽다.
