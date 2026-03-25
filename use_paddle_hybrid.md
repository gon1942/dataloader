# Paddle Hybrid 사용 가이드

## 1. 개요

이 문서는 이 프로젝트에서 PaddleOCR-VL을 OpenDataLoader `hybrid` 모드로 사용하는 현재 실행 구조를 설명합니다.

중요:

- 현재는 `--hybrid paddle` 정식 backend가 아닙니다.
- OpenDataLoader는 기존 `docling-fast` hybrid 경로를 재사용합니다.
- 따라서 실제 사용 형태는 아래입니다.

```bash
opendataloader-pdf \
  --hybrid docling-fast \
  --hybrid-url http://127.0.0.1:8090 \
  --format json,html,markdown,markdown-with-images \
  -o <output-dir> \
  <input.pdf>
```

즉 OpenDataLoader는 `Paddle 원서버`가 아니라 `adapter`에 붙습니다.

---

## 2. 전체 구조

현재 hybrid 실행 구조는 아래와 같습니다.

```text
OpenDataLoader CLI
  -> odl_paddle_adapter (FastAPI, port 8090)
    -> paddleocr_vl15_vllm (PaddleOCR-VL vLLM server, port 8080)
```

컨테이너 역할:

- `odl_paddle_adapter`
  - OpenDataLoader가 직접 호출하는 hybrid backend
  - `/health`
  - `/health/backend`
  - `/v1/convert/file`
  를 제공
- `paddleocr_vl15_vllm`
  - 실제 PaddleOCR-VL 추론 서버
  - adapter 내부에서 `PaddleOCRVL.predict()` 호출 시 사용

정리:

- OpenDataLoader가 직접 사용하는 컨테이너: `odl_paddle_adapter`
- 실제 추론에 필요한 컨테이너: `odl_paddle_adapter` + `paddleocr_vl15_vllm`

---

## 3. 관련 파일

- [start_stack.sh](/home/gon/work/ttt3/opendataloader-pdf/paddle/start_stack.sh)
- [stop_stack.sh](/home/gon/work/ttt3/opendataloader-pdf/paddle/stop_stack.sh)
- [run_opendataloader.sh](/home/gon/work/ttt3/opendataloader-pdf/paddle/run_opendataloader.sh)
- [Dockerfile](/home/gon/work/ttt3/opendataloader-pdf/paddle/adapter/Dockerfile)
- [app.py](/home/gon/work/ttt3/opendataloader-pdf/paddle/adapter/app.py)

---

## 4. start_stack.sh 동작

[start_stack.sh](/home/gon/work/ttt3/opendataloader-pdf/paddle/start_stack.sh)는 아래 순서로 동작합니다.

1. 기존 컨테이너 정리
   - `odl_paddle_adapter`
   - `paddleocr_vl15_vllm`
2. `paddle/adapter` 이미지 빌드
   - 이미지명 기본값: `odl-paddle-adapter`
3. Paddle vLLM 서버 실행
4. `http://127.0.0.1:8080/v1/models` 응답 대기
5. adapter 실행
6. 아래 health check 수행
   - `http://127.0.0.1:8090/health`
   - `http://127.0.0.1:8090/health/backend`

기본 포트:

- Paddle vLLM: `8080`
- Adapter: `8090`

기본 컨테이너 이름:

- `paddleocr_vl15_vllm`
- `odl_paddle_adapter`

---

## 5. stop_stack.sh 동작

[stop_stack.sh](/home/gon/work/ttt3/opendataloader-pdf/paddle/stop_stack.sh)는 아래 컨테이너를 제거합니다.

- `odl_paddle_adapter`
- `paddleocr_vl15_vllm`

즉 현재 hybrid 실행 스택 전체를 내립니다.

---

## 6. run_opendataloader.sh 동작

[run_opendataloader.sh](/home/gon/work/ttt3/opendataloader-pdf/paddle/run_opendataloader.sh)는 아래 명령을 감싼 래퍼입니다.

```bash
opendataloader-pdf \
  --hybrid docling-fast \
  --hybrid-url http://127.0.0.1:8090 \
  --format json,html,markdown,markdown-with-images \
  -o <output-dir> \
  <input.pdf>
```

기본 adapter URL:

- `http://127.0.0.1:8090`

환경변수로 변경 가능:

```bash
ADAPTER_URL=http://127.0.0.1:8090 ./paddle/run_opendataloader.sh ...
```

---

## 7. Docker 이미지와 런타임

### Paddle 추론 서버

이미지:

- `ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddleocr-genai-vllm-server:latest-nvidia-gpu`

실행 시 주요 옵션:

- `--network host`
- `--gpus ...`
- `--shm-size 8g`
- `PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True`

실행 커맨드:

```bash
paddleocr genai_server --model_name PaddleOCR-VL-1.5-0.9B --host 0.0.0.0 --port 8080 --backend vllm
```

### Adapter 이미지

베이스 이미지:

- `python:3.11-slim`

주요 시스템 패키지:

- `libglib2.0-0`
- `libgl1`
- `libgomp1`

주요 Python 패키지:

- `fastapi`
- `httpx`
- `uvicorn`
- `numpy<2`
- `paddlepaddle`
- `paddleocr[doc-parser]`

---

## 8. Adapter 역할

[app.py](/home/gon/work/ttt3/opendataloader-pdf/paddle/adapter/app.py)의 현재 역할:

1. OpenDataLoader가 보낸 PDF를 `/v1/convert/file`로 받음
2. 임시 PDF 파일로 저장
3. `PaddleOCRVL(vl_rec_backend="vllm-server", vl_rec_server_url="http://127.0.0.1:8080/v1")` 초기화
4. `predict(pdf_path)` 실행
5. Paddle 결과를 최소 Docling-like 구조로 변환
6. OpenDataLoader가 읽을 수 있는 `document.json_content`로 반환

지원 health API:

- `GET /health`
- `GET /health/backend`

---

## 9. 현재 사용 방법

### 9.1 스택 시작

```bash
cd /home/gon/work/ttt3/opendataloader-pdf
GPU_DEVICE=1 ./paddle/start_stack.sh
```

### 9.2 변환 실행

```bash
./paddle/run_opendataloader.sh \
  samples/pdf/input1.pdf \
  tmp/odl-hybrid/input1_paddle_formats
```

### 9.3 결과 확인

- [input1.json](/home/gon/work/ttt3/opendataloader-pdf/tmp/odl-hybrid/input1_paddle_formats/input1.json)
- [input1.md](/home/gon/work/ttt3/opendataloader-pdf/tmp/odl-hybrid/input1_paddle_formats/input1.md)
- [input1.html](/home/gon/work/ttt3/opendataloader-pdf/tmp/odl-hybrid/input1_paddle_formats/input1.html)

### 9.4 스택 종료

```bash
./paddle/stop_stack.sh
```

---

## 10. 직접 CLI로 실행할 때

스크립트를 쓰지 않고 직접 실행하려면:

```bash
opendataloader-pdf \
  --hybrid docling-fast \
  --hybrid-url http://127.0.0.1:8090 \
  --format json,html,markdown,markdown-with-images \
  -o tmp/odl-hybrid/input1_paddle_formats \
  samples/pdf/input1.pdf
```

주의:

- `--hybrid-url`는 `paddleocr_vl15_vllm`이 아니라 `odl_paddle_adapter`를 가리켜야 합니다.

---

## 11. 자주 헷갈리는 점

### Q1. hybrid 모드에서 사용하는 컨테이너는 하나인가?

아니요.

- 직접 붙는 컨테이너는 `odl_paddle_adapter` 하나입니다.
- 하지만 실제 처리에는 `paddleocr_vl15_vllm`도 필요합니다.

### Q2. `--hybrid paddle`인가?

아니요.

현재는:

- `--hybrid docling-fast`
- `--hybrid-url http://127.0.0.1:8090`

형태를 사용합니다.

### Q3. `odl-paddle-adapter:latest`만 있으면 되나?

아니요.

adapter는 내부에서 Paddle vLLM 서버를 사용하므로 `paddleocr_vl15_vllm`도 필요합니다.

---

## 12. 확인 명령

컨테이너 상태:

```bash
docker ps
```

adapter health:

```bash
curl -s http://127.0.0.1:8090/health
curl -s http://127.0.0.1:8090/health/backend
```

Paddle backend health:

```bash
curl -s http://127.0.0.1:8080/v1/models
```

---

## 13. 장애 시 점검 포인트

### Paddle backend가 안 뜨는 경우

확인:

```bash
nvidia-smi
docker logs paddleocr_vl15_vllm
```

대표 원인:

- GPU 메모리 부족
- 잘못된 GPU 지정

GPU 1 사용 예:

```bash
GPU_DEVICE=1 ./paddle/start_stack.sh
```

### adapter는 떴지만 backend 502인 경우

확인:

```bash
docker logs odl_paddle_adapter
curl -s http://127.0.0.1:8090/health/backend
```

대표 원인:

- Paddle backend 미기동
- Python 의존성 문제
- `PaddleOCRVL` import 실패

### fresh output에 json만 있고 md/html이 없는 경우

현재는 [run_opendataloader.sh](/home/gon/work/ttt3/opendataloader-pdf/paddle/run_opendataloader.sh)에서 이미 아래 포맷을 명시합니다.

```bash
--format json,html,markdown,markdown-with-images
```

직접 CLI를 칠 때 이 옵션을 빠뜨리면 `json`만 생성될 수 있습니다.

---

## 14. 현재 상태 요약

현재 기준으로는:

- hybrid adapter stack 기동 가능
- OpenDataLoader end-to-end 실행 가능
- `json/md/html` 생성 가능

하지만 여전히 품질 이슈는 남아 있습니다.

- adapter의 `transform.py`는 최소 Docling-like 변환기입니다.
- 즉 연결과 산출물 생성은 되지만, Paddle raw 구조를 정밀하게 좌표 기반 매핑한 상태는 아닙니다.
- 품질 개선은 다음 단계 작업입니다.
