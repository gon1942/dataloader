# Paddle Adapter 개선 작업 문서

## 1. 목적

이 문서는 `paddle/adapter`를 현재의 골격 상태에서, OpenDataLoader hybrid mode에서 실제 사용할 수 있는 단계까지 개선하기 위한 작업 계획서입니다.

현재 상태:

- `paddleocr_vl15_vllm` 서버는 기동 가능
- `paddle/start_stack.sh`로 Paddle 서버와 adapter를 함께 실행 가능
- `GET /health`
- `GET /health/backend`
  는 정상 확인 가능
- 하지만 `POST /v1/convert/file`은 아직 실사용 불가

현재 실패 원인:

- OpenDataLoader는 adapter에 PDF를 `multipart/form-data`로 보냄
- 현재 adapter는 그 PDF를 Paddle 서버의 `/v1/chat/completions`로 그대로 전달함
- 하지만 Paddle vLLM endpoint는 `application/json`만 받음
- 따라서 현재 구현은 구조적으로 맞지 않음

결론:

- adapter는 단순 프록시가 아니라
- PDF를 직접 받아서 `PaddleOCRVL.predict()`를 수행하는 실행기로 바뀌어야 함

---

## 2. 최종 목표

최종적으로 아래 흐름이 되어야 합니다.

```text
OpenDataLoader CLI
  -> paddle/adapter /v1/convert/file
    -> PaddleOCRVL.predict(pdf)
    -> Paddle 결과를 OpenDataLoader hybrid schema로 변환
    -> json/md/html 생성 가능
```

완료 기준:

1. `./paddle/start_stack.sh` 실행 성공
2. `./paddle/run_opendataloader.sh samples/pdf/input1.pdf tmp/odl-hybrid/input1` 실행 성공
3. OpenDataLoader가 backend 502 없이 종료
4. `tmp/odl-hybrid/input1/` 아래 `json`, `md`, `html` 생성
5. 결과 품질 검토 가능

---

## 3. 현재 문제 요약

### 문제 1. adapter 요청 방식이 잘못됨

대상 파일:

- `paddle/adapter/app.py`

현재:

- `_call_paddle_backend()`가 PDF 파일을 `files=...`로 POST

문제:

- Paddle vLLM의 `/v1/chat/completions`는 파일 업로드 endpoint가 아님
- JSON body 기반 API임

조치 방향:

- `_call_paddle_backend()` 제거 또는 축소
- adapter 내부에서 `PaddleOCRVL.predict()` 직접 호출

### 문제 2. adapter 컨테이너에 Paddle 실행 환경이 없음

대상 파일:

- `paddle/adapter/Dockerfile`
- `paddle/adapter/requirements.txt`

현재:

- FastAPI, httpx 수준의 경량 환경

문제:

- `PaddleOCRVL` 실행에 필요한 Python 패키지와 런타임이 부족할 수 있음

조치 방향:

- `paddleocr`
- `paddlex`
- PDF 처리 관련 필수 의존성
를 adapter 실행 환경에 포함

### 문제 3. Paddle 결과를 OpenDataLoader schema로 변환하지 못함

대상 파일:

- `paddle/adapter/transform.py`

현재:

- placeholder 응답만 반환

문제:

- OpenDataLoader hybrid path가 기대하는 `document.json_content` 구조가 없음

조치 방향:

- Paddle page 결과 구조를 파악
- 텍스트 블록, 표 블록, bbox, 페이지 정보를 매핑

### 문제 4. end-to-end 검증 경로가 아직 미완성

대상 파일:

- `paddle/start_stack.sh`
- `paddle/run_opendataloader.sh`
- 필요 시 테스트 파일 추가

현재:

- 서버 기동은 가능
- 실제 변환은 실패

조치 방향:

- 변환 성공 기준의 확인 절차를 문서화
- 필요하면 샘플 응답 저장과 디버그 로깅 추가

---

## 4. 단계별 작업 계획

## Step 1. adapter 실행 방식을 프록시에서 로컬 실행기로 변경

목적:

- adapter가 Paddle 서버에 raw PDF를 직접 재전송하지 않도록 변경

수정 대상:

- `paddle/adapter/app.py`

해야 할 일:

1. 업로드된 PDF를 임시 파일로 저장
2. `PaddleOCRVL` 인스턴스를 생성
3. `vl_rec_backend="vllm-server"` 설정
4. `vl_rec_server_url="http://127.0.0.1:8080/v1"` 또는 환경변수 기반 주소 전달
5. `predict(temp_pdf_path)` 호출
6. Paddle raw result를 `transform.py`로 넘김

구현 메모:

- `ocr_pdf_v1.py`의 흐름을 참고
- adapter 내부에서 CLI subprocess보다 Python API 직접 호출을 우선

완료 기준:

- `/v1/convert/file` 요청 시 더 이상 `Unsupported Media Type`가 발생하지 않음

---

## Step 2. adapter Docker 환경 보강

목적:

- adapter 컨테이너 내부에서 `PaddleOCRVL`이 실제 실행되도록 보장

수정 대상:

- `paddle/adapter/Dockerfile`
- `paddle/adapter/requirements.txt`

해야 할 일:

1. `paddleocr`, `paddlex` 설치 방식 확정
2. PDF 처리에 필요한 시스템 의존성 확인
3. 현재 slim 이미지로 충분한지 확인
4. 필요 시 베이스 이미지를 변경하거나 apt 패키지 추가

검토 포인트:

- host Python에서는 이미 `paddleocr`, `paddlex` import 가능
- container에서도 동일하게 import 가능한지 확인 필요

완료 기준:

- adapter 컨테이너에서 `from paddleocr import PaddleOCRVL` import 가능
- `predict()` 호출까지 실패 없이 진입 가능

---

## Step 3. Paddle 결과 구조 확인

목적:

- `transform.py` 구현에 필요한 실제 입력 구조 확보

참고 파일:

- `paddle/paddleocr_vll_docker/client/ocr_pdf_v1.py`
- `paddle/paddleocr_vll_docker/client/ocr_pdf.py`

해야 할 일:

1. `PaddleOCRVL.predict()`의 반환 객체 샘플 저장
2. page 단위 결과 구조 확인
3. 텍스트, 테이블, 좌표, span 관련 필드 확인
4. JSON dump 가능 여부 확인

산출물:

- sample raw payload 파일
- 필드 구조 메모

완료 기준:

- `transform.py` 구현에 필요한 필드 목록이 정리됨

---

## Step 4. `transform.py` 실제 구현

목적:

- Paddle 결과를 OpenDataLoader hybrid schema로 변환

수정 대상:

- `paddle/adapter/transform.py`

해야 할 일:

1. 페이지 리스트 구성
2. 텍스트 블록 매핑
3. 표 블록 매핑
4. bbox 좌표 매핑
5. 필요한 최소 필드만 우선 구현

우선순위:

1. 페이지 정보
2. 텍스트 블록
3. 테이블 블록
4. span/merge 정보

주의:

- 처음부터 완전한 테이블 구조를 만들려 하지 말고
- OpenDataLoader가 죽지 않고 결과를 생성할 최소 스키마부터 맞출 것

완료 기준:

- OpenDataLoader backend 처리 후 예외 없이 결과 파일 생성

---

## Step 5. end-to-end 검증

목적:

- 실제 입력 PDF로 전체 경로 확인

실행 명령:

```bash
cd /home/gon/work/ttt3/opendataloader-pdf
./paddle/stop_stack.sh
GPU_DEVICE=1 ./paddle/start_stack.sh
./paddle/run_opendataloader.sh samples/pdf/input1.pdf tmp/odl-hybrid/input1
```

검증 항목:

1. `Backend processing failed`가 없어야 함
2. `tmp/odl-hybrid/input1/` 생성 확인
3. `input1.json`, `input1.md`, `input1.html` 생성 확인
4. 최소한 표/텍스트가 빈 결과가 아닌지 확인

완료 기준:

- OpenDataLoader CLI가 return code 0으로 종료

---

## Step 6. 품질 검토

목적:

- 실행 성공 후 내용 품질 확인

검토 대상:

- `tmp/odl-hybrid/input1/input1.json`
- `tmp/odl-hybrid/input1/input1.md`
- `tmp/odl-hybrid/input1/input1.html`

확인 포인트:

1. 표가 원문 구조와 비슷하게 유지되는지
2. 상단 헤더가 분리되지 않는지
3. 셀 병합 정보가 어느 정도 보존되는지
4. OCR 텍스트가 EasyOCR 때보다 나아졌는지

후속 개선 후보:

- transform에서 표 셀 span 보정
- html/md 렌더링 보정
- Paddle raw output 기반 후처리 휴리스틱 추가

---

## 5. 실제 수정 순서

가장 현실적인 작업 순서는 아래입니다.

1. `app.py`를 `PaddleOCRVL.predict()` 기반으로 교체
2. `Dockerfile`, `requirements.txt` 보강
3. raw payload 샘플 저장
4. `transform.py` 최소 구현
5. `input1.pdf` end-to-end 테스트
6. 결과 품질 검토

---

## 6. 작업 우선순위

우선순위 High:

- `app.py` 실행 방식 변경
- adapter 실행 환경 보강
- `transform.py` 최소 구현

우선순위 Medium:

- raw payload 저장 기능
- 디버그 로그 강화

우선순위 Low:

- 품질 개선용 후처리
- span/merge 고도화
- 문서 정리 보강

---

## 7. 체크리스트

### A. 실행 환경

- [ ] `GPU_DEVICE=1 ./paddle/start_stack.sh` 성공
- [ ] `curl http://127.0.0.1:8090/health` 성공
- [ ] `curl http://127.0.0.1:8090/health/backend` 성공

### B. adapter 실행

- [ ] `/v1/convert/file`에서 502가 아닌 정상 응답 반환
- [ ] `Unsupported Media Type` 에러 제거
- [ ] `PaddleOCRVL.predict()` 호출 성공

### C. schema 변환

- [ ] placeholder 제거
- [ ] 최소 `document.json_content` 구조 반환
- [ ] OpenDataLoader가 응답을 파싱 가능

### D. 산출물

- [ ] `input1.json` 생성
- [ ] `input1.md` 생성
- [ ] `input1.html` 생성

### E. 품질

- [ ] 결과가 빈 문서가 아님
- [ ] 표가 최소한 문단으로 붕괴되지 않음
- [ ] 후속 품질 개선 포인트 식별

---

## 8. 관련 파일 목록

- `paddle/adapter/app.py`
- `paddle/adapter/config.py`
- `paddle/adapter/transform.py`
- `paddle/adapter/Dockerfile`
- `paddle/adapter/requirements.txt`
- `paddle/start_stack.sh`
- `paddle/run_opendataloader.sh`
- `paddle/paddleocr_vll_docker/client/ocr_pdf_v1.py`
- `paddle/paddleocr_vll_docker/client/ocr_pdf.py`

---

## 9. 바로 다음 작업

다음 작업은 이 문서 기준으로 Step 1입니다.

즉,

- `paddle/adapter/app.py`

를 수정해서

- PDF 업로드 수신
- 임시 파일 저장
- `PaddleOCRVL.predict()`
- 결과 반환

구조로 바꾸는 것이 첫 번째 실제 구현 작업입니다.

---

## 10. AI 작업 지시사항

이 섹션은 AI 에이전트에게 바로 전달할 수 있는 작업 지시문입니다.

### 공통 지시

- 이 저장소 안에서만 작업할 것
- 기존 동작을 무조건 지우지 말고, 필요한 범위만 수정할 것
- 임시 우회 하드코딩은 넣지 말 것
- 특정 파일명, 특정 키워드, 특정 문서 내용에 의존하는 분기 처리는 금지
- `input1.pdf`는 검증용 샘플로만 사용할 것
- 목표는 일반적인 PDF 입력에 대해 동작 가능한 adapter 구조를 만드는 것


### 작업 범위

수정 가능 파일:

- `paddle/adapter/app.py`
- `paddle/adapter/config.py`
- `paddle/adapter/transform.py`
- `paddle/adapter/Dockerfile`
- `paddle/adapter/requirements.txt`
- `paddle/start_stack.sh`
- `paddle/run_opendataloader.sh`
- 필요 시 관련 문서 파일

가능하면 수정하지 말아야 할 파일:

- Java core 처리 로직
- 기존 `docling-fast` 구현
- `samples/pdf/` 아래 샘플 원본 파일

### 1차 작업 지시

목표:

- `paddle/adapter/app.py`를 단순 HTTP 프록시에서 실제 Paddle 실행기로 변경할 것

구체 지시:

1. `/v1/convert/file`로 들어온 PDF를 임시 파일로 저장할 것
2. adapter 내부에서 `PaddleOCRVL`을 import할 것
3. `PaddleOCRVL(vl_rec_backend="vllm-server", vl_rec_server_url=...)` 형태로 초기화할 것
4. `predict(pdf_path)`를 호출할 것
5. Paddle raw result를 `transform.py`로 넘길 것
6. 더 이상 `/v1/chat/completions`에 PDF 파일을 직접 POST하지 말 것

금지:

- PDF를 다시 `multipart/form-data`로 Paddle backend에 전달하는 방식 유지
- 특정 입력 파일명 기준 분기
- 테스트용 mock 응답을 실제 코드 경로에 남겨두는 것

완료 기준:

- `Unsupported Media Type` 에러가 제거될 것
- adapter가 Paddle raw result를 실제로 받을 것

### 2차 작업 지시

목표:

- adapter 컨테이너에서도 `PaddleOCRVL.predict()`가 동작하게 만들 것

구체 지시:

1. `paddle/adapter/Dockerfile`에 필요한 Python 패키지 설치를 반영할 것
2. `requirements.txt`를 실제 실행 기준으로 정리할 것
3. 컨테이너 안에서 `from paddleocr import PaddleOCRVL` import 가능해야 함
4. 필요 시 시스템 패키지를 추가할 것

금지:

- host 환경에만 의존하는 설명으로 끝내는 것
- container와 host가 서로 다르게 동작하는 상태를 방치하는 것

완료 기준:

- adapter 컨테이너 내부에서 Paddle 실행 경로가 정상 동작할 것

### 3차 작업 지시

목표:

- Paddle raw output 구조를 확인하고 샘플을 저장할 것

구체 지시:

1. `predict()` 결과 구조를 확인할 것
2. page 단위 필드, text 블록, table 블록, bbox 관련 필드를 정리할 것
3. 필요하면 디버그용 raw payload 저장 기능을 넣을 것
4. 단, 디버그 출력은 끄거나 제어 가능해야 함

금지:

- 결과 구조를 추측만 하고 구현하는 것
- 샘플 payload 없이 `transform.py`를 크게 작성하는 것

완료 기준:

- `transform.py` 구현에 필요한 입력 구조가 확보될 것

### 4차 작업 지시

목표:

- `transform.py`를 placeholder에서 실제 변환기로 바꿀 것

구체 지시:

1. OpenDataLoader hybrid path가 읽을 수 있는 최소 스키마를 먼저 맞출 것
2. page 정보부터 구현할 것
3. text block 매핑을 넣을 것
4. table block 매핑을 넣을 것
5. span 정보는 가능하면 넣되, 처음부터 완전하게 하지 않아도 됨

금지:

- raw payload를 그대로 `backend_payload`에만 넣고 끝내는 것
- placeholder 응답을 유지한 채 "성공"으로 처리하는 것

완료 기준:

- OpenDataLoader가 backend 결과를 받아 `json/md/html`을 생성할 것

### 5차 작업 지시

목표:

- `input1.pdf` 기준 end-to-end 실행 검증

구체 지시:

실행 순서:

```bash
cd /home/gon/work/ttt3/opendataloader-pdf
./paddle/stop_stack.sh
GPU_DEVICE=1 ./paddle/start_stack.sh
./paddle/run_opendataloader.sh samples/pdf/input1.pdf tmp/odl-hybrid/input1
```

검증 항목:

1. adapter 502가 없어야 함
2. OpenDataLoader CLI가 종료 코드 0으로 끝나야 함
3. `tmp/odl-hybrid/input1/input1.json`
4. `tmp/odl-hybrid/input1/input1.md`
5. `tmp/odl-hybrid/input1/input1.html`
   가 생성되어야 함

금지:

- 문서만 수정하고 실행 검증을 생략하는 것
- 실패했는데 원인 확인 없이 다음 단계로 넘어가는 것

완료 기준:

- end-to-end 결과 파일 생성 확인

### 6차 작업 지시

목표:

- 품질 점검과 후속 과제 분리

구체 지시:

1. 생성된 `json/md/html`을 열어 결과 품질을 확인할 것
2. 실행 성공과 품질 문제를 분리해서 기록할 것
3. OCR 문제와 schema 변환 문제를 구분해서 적을 것
4. 다음 개선 작업이 무엇인지 남길 것

금지:

- 실행 성공만으로 품질도 정상이라고 결론 내리는 것

완료 기준:

- 후속 개선 포인트가 문서로 남을 것

### AI 최종 산출물

AI는 작업 완료 후 아래를 남겨야 합니다.

1. 수정한 파일 목록
2. 어떤 단계까지 완료했는지
3. 실제 실행한 검증 명령
4. 성공한 항목
5. 아직 남은 문제
6. 다음으로 이어서 할 작업

### AI가 지켜야 할 구현 원칙

- 하드코딩 금지
- 특정 샘플 전용 분기 금지
- 실패 시 원인 로그를 남길 것
- placeholder를 실제 구현으로 교체할 것
- “연결만 된다” 수준에서 멈추지 말고, 실제 산출물 생성까지 검증할 것
