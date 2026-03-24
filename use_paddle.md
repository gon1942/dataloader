# Paddle 사용 가이드

## 1. 개요

이 저장소의 `paddle/` 아래에는 두 가지가 있습니다.

- `paddle/paddleocr_vll_docker`
  - PaddleOCR-VL 1.5 vLLM 서버와 OCR 클라이언트를 Docker로 돌리기 위한 구성
- `paddle/adapter`
  - OpenDataLoader hybrid mode가 기대하는 API 형식으로 Paddle 서버를 감싸는 FastAPI 어댑터

현재 구조는 아래와 같습니다.

```text
OpenDataLoader CLI
  -> paddle/adapter (FastAPI)
    -> paddleocr_vll_docker 의 vLLM server
```

중요:

- `paddleocr_vll_docker`는 OpenDataLoader가 바로 호출할 수 있는 API가 아닙니다.
- OpenDataLoader에서 쓰려면 `paddle/adapter`를 중간에 둬야 합니다.
- 어댑터는 현재 골격 상태이며, Paddle 응답을 OpenDataLoader schema로 바꾸는 `transform.py`는 아직 placeholder입니다.

## 2. 디렉터리 구조

```text
paddle/
├── adapter/
│   ├── app.py
│   ├── config.py
│   ├── transform.py
│   ├── Dockerfile
│   └── requirements.txt
└── paddleocr_vll_docker/
    ├── docker-compose.yml
    ├── run.sh
    ├── client/
    │   ├── Dockerfile
    │   ├── ocr_pdf.py
    │   ├── ocr_pdf_v1.py
    │   ├── hybrid_pdf_pipeline.py
    │   └── preprocess_table_lines.py
    └── data/
```

## 3. 준비물

필수:

- Docker
- NVIDIA GPU + NVIDIA Container Toolkit
- OpenDataLoader 실행 환경

권장 확인:

```bash
docker --version
nvidia-smi
```

## 4. Paddle 서버 단독 실행

`paddleocr_vll_docker` 디렉터리로 이동합니다.

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker
```

서버 이미지를 직접 실행하려면:

```bash
docker run -d --name paddleocr_vl15_vllm --gpus all --network host \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddleocr-genai-vllm-server:latest-nvidia-gpu \
  paddleocr genai_server --model_name PaddleOCR-VL-1.5-0.9B --host 0.0.0.0 --port 8080 --backend vllm
```

GPU 1만 쓰려면:

```bash
docker rm -f paddleocr_vl15_vllm
docker run -d --name paddleocr_vl15_vllm --gpus '"device=1"' --network host \
  --shm-size 8g \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddleocr-genai-vllm-server:latest-nvidia-gpu \
  paddleocr genai_server --model_name PaddleOCR-VL-1.5-0.9B --host 0.0.0.0 --port 8080 --backend vllm
```

헬스 체크:

```bash
curl -s http://127.0.0.1:8080/v1/models
```

## 5. Docker Compose로 서버 + 어댑터 함께 실행

가장 간단한 방식입니다.

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker
docker compose up -d vlm_server odl_adapter
```

상태 확인:

```bash
docker compose ps
curl -s http://127.0.0.1:8090/health
curl -s http://127.0.0.1:8090/health/backend
```

의도된 포트:

- Paddle vLLM server: `http://127.0.0.1:8080`
- OpenDataLoader adapter: `http://127.0.0.1:8090`

## 6. Paddle OCR 클라이언트 단독 사용

OpenDataLoader를 거치지 않고 Paddle 쪽 OCR 결과만 보고 싶을 때 사용합니다.

클라이언트 이미지 빌드:

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker
docker build -t paddleocr_vl15_client ./client
```

기본 OCR 실행:

```bash
docker run --rm --network host -v "$PWD/data:/data" \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  paddleocr_vl15_client \
  python /app/ocr_pdf.py \
    --input /data/input.pdf \
    --out_dir /data/out \
    --server_url http://127.0.0.1:8080/v1 \
    --make_html
```

고해상도 렌더링:

```bash
docker run --rm --network host -v "$PWD/data:/data" \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  paddleocr_vl15_client \
  python /app/ocr_pdf.py \
    --input /data/input.pdf \
    --out_dir /data/out \
    --server_url http://127.0.0.1:8080/v1 \
    --dpi 300 \
    --make_html
```

표 병합셀 후처리 포함:

```bash
docker run --rm --network host -v "$PWD/data:/data" \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  paddleocr_vl15_client \
  python /app/ocr_pdf.py \
    --input /data/input.pdf \
    --out_dir /data/out \
    --server_url http://127.0.0.1:8080/v1 \
    --dpi 300 \
    --render_mode gray \
    --temperature 0.0 \
    --top_p 1.0 \
    --repetition_penalty 1.0 \
    --refine_table_spans \
    --span_head_rows 4 \
    --span_left_cols 3 \
    --span_min_run 1 \
    --make_html
```

## 7. run.sh 사용

`data/` 아래 PDF를 순회해서 일괄 OCR을 돌립니다.

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker
./run.sh
```

다른 서버 주소를 쓰려면:

```bash
SERVER_URL=http://127.0.0.1:8080/v1 ./run.sh
```

출력 위치:

```text
paddle/paddleocr_vll_docker/data/out/
```

## 8. OpenDataLoader와 연결

어댑터가 떠 있다는 전제에서 OpenDataLoader는 아래처럼 붙입니다.

```bash
cd /home/gon/work/ttt3/opendataloader-pdf

opendataloader-pdf \
  --hybrid docling-fast \
  --hybrid-url http://127.0.0.1:8090 \
  -o ./tmp/odl-hybrid/input1 \
  samples/pdf/input1.pdf
```

현재 의미:

- `--hybrid docling-fast`
  - 실제 Docling 서버를 쓰는 뜻이 아니라, OpenDataLoader의 기존 hybrid client 경로를 재사용한다는 뜻입니다.
- `--hybrid-url http://127.0.0.1:8090`
  - 이 URL은 `paddle/adapter`를 가리켜야 합니다.

## 9. Adapter 환경변수

`paddle/adapter` 기본값:

- `PADDLE_BASE_URL=http://localhost:8080`
- `PADDLE_HEALTH_PATH=/v1/models`
- `PADDLE_CONVERT_PATH=/v1/chat/completions`
- `REQUEST_TIMEOUT_SECONDS=120`
- `LOG_LEVEL=INFO`

직접 실행 예시:

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/adapter
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

PADDLE_BASE_URL=http://127.0.0.1:8080 \
PADDLE_HEALTH_PATH=/v1/models \
PADDLE_CONVERT_PATH=/v1/chat/completions \
uvicorn app:app --host 0.0.0.0 --port 8090 --reload
```

## 10. 주요 옵션 설명

`ocr_pdf.py` 쪽 주요 옵션:

- `--dpi`
  - PDF를 이미지로 렌더링할 해상도
- `--render_mode color|gray|mono`
  - 렌더링 색상 모드
- `--refine_table_spans`
  - OCR 결과의 빈 셀 패턴을 이용해 `rowspan/colspan` 휴리스틱 보정
- `--span_head_rows`
  - 상단 몇 개 행을 헤더로 보고 가로 병합 보정할지
- `--span_left_cols`
  - 좌측 몇 개 열을 라벨 영역으로 보고 세로 병합 보정할지
- `--span_min_run`
  - 빈 셀 연속 개수 기준. 낮을수록 공격적으로 병합

## 11. 현재 한계

중요:

- `paddle/adapter/transform.py`는 아직 placeholder입니다.
- 즉 서버 연결은 되더라도 Paddle 응답을 OpenDataLoader hybrid schema로 완전히 바꾸는 작업은 아직 안 되어 있습니다.
- 따라서 OpenDataLoader hybrid에 붙여도 최종 `json/md/html`이 제대로 나오려면 추가 구현이 필요합니다.

현 시점에서 가능한 것:

- Paddle 서버를 Docker로 띄우기
- Paddle 클라이언트로 OCR 실험하기
- adapter를 통해 OpenDataLoader가 붙을 수 있는 API 골격 만들기

현 시점에서 미완성인 것:

- Paddle 응답 -> OpenDataLoader 문서 schema 변환

## 12. 문제 해결

`curl http://127.0.0.1:8080/v1/models` 실패:

- Paddle vLLM 서버가 안 떠 있음
- GPU/runtime 설정 문제

`curl http://127.0.0.1:8090/health` 성공, `/health/backend` 실패:

- adapter는 떠 있지만 Paddle 서버 연결 실패
- `PADDLE_BASE_URL`, `PADDLE_HEALTH_PATH` 확인

OpenDataLoader에서 hybrid 404:

- `--hybrid-url`가 adapter가 아닌 Paddle 원서버를 가리키는 경우
- adapter 포트를 `8090`으로 맞췄는지 확인

## 13. 권장 순서

처음에는 아래 순서로 확인하는 것이 안전합니다.

1. `vlm_server`만 기동
2. `curl http://127.0.0.1:8080/v1/models`
3. `paddleocr_vl15_client`로 `ocr_pdf.py` 단독 실행
4. `odl_adapter` 기동
5. `curl http://127.0.0.1:8090/health/backend`
6. 마지막에 `opendataloader-pdf --hybrid docling-fast --hybrid-url http://127.0.0.1:8090 ...`

## 14. 바로 실행용 명령 모음

아래 명령은 그대로 복사해서 실행하는 용도입니다.

### 14.1 Paddle vLLM 서버만 실행

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker
docker rm -f paddleocr_vl15_vllm && \
docker run -d --name paddleocr_vl15_vllm --gpus all --network host \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddleocr-genai-vllm-server:latest-nvidia-gpu \
  paddleocr genai_server --model_name PaddleOCR-VL-1.5-0.9B --host 0.0.0.0 --port 8080 --backend vllm

# docker rm -f paddleocr_vl15_vllm && \
# docker run -d --name paddleocr_vl15_vllm --gpus '"device=1"' --network host \
#   --shm-size 8g \
#   -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
#   ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddleocr-genai-vllm-server:latest-nvidia-gpu \
#   paddleocr genai_server --model_name PaddleOCR-VL-1.5-0.9B --host 0.0.0.0 --port 8080 --backend vllm


```

헬스 체크:

```bash
curl -s http://127.0.0.1:8080/v1/models
```

### 14.2 GPU 1만 사용해서 Paddle vLLM 서버 실행

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker

docker rm -f paddleocr_vl15_vllm

docker run -d --name paddleocr_vl15_vllm --gpus '"device=1"' --network host \
  --shm-size 8g \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddleocr-genai-vllm-server:latest-nvidia-gpu \
  paddleocr genai_server --model_name PaddleOCR-VL-1.5-0.9B --host 0.0.0.0 --port 8080 --backend vllm
```

### 14.3 Paddle OCR 클라이언트 이미지 빌드

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker
docker build -t paddleocr_vl15_client ./client
```

### 14.4 Paddle OCR 단독 실행

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker

docker run --rm --network host -v "$PWD/data:/data" \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  paddleocr_vl15_client \
  python /app/ocr_pdf.py \
    --input /data/input.pdf \
    --out_dir /data/out \
    --server_url http://127.0.0.1:8080/v1 \
    --dpi 300 \
    --render_mode gray \
    --temperature 0.0 \
    --top_p 1.0 \
    --repetition_penalty 1.0 \
    --refine_table_spans \
    --span_head_rows 4 \
    --span_left_cols 3 \
    --span_min_run 1 \
    --make_html
```

### 14.5 FastAPI adapter 이미지 빌드

```bash
cd /home/gon/work/ttt3/opendataloader-pdf
docker build -t odl-paddle-adapter ./paddle/adapter
```

### 14.6 FastAPI adapter 실행

```bash
docker run --rm --network host \
  -e PADDLE_BASE_URL=http://127.0.0.1:8080 \
  -e PADDLE_HEALTH_PATH=/v1/models \
  -e PADDLE_CONVERT_PATH=/v1/chat/completions \
  -e REQUEST_TIMEOUT_SECONDS=120 \
  -e LOG_LEVEL=INFO \
  odl-paddle-adapter \
  uvicorn app:app --host 0.0.0.0 --port 8090
```

adapter 확인:

```bash
curl -s http://127.0.0.1:8090/health
curl -s http://127.0.0.1:8090/health/backend
```

### 14.7 Docker Compose로 Paddle 서버 + adapter 동시 실행

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker
docker compose up -d vlm_server odl_adapter
```

상태 확인:

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker
docker compose ps
curl -s http://127.0.0.1:8090/health
curl -s http://127.0.0.1:8090/health/backend
```

### 14.8 OpenDataLoader hybrid 연결

```bash
cd /home/gon/work/ttt3/opendataloader-pdf

opendataloader-pdf \
  --hybrid docling-fast \
  --hybrid-url http://127.0.0.1:8090 \
  -o ./tmp/odl-hybrid/input1 \
  samples/pdf/input1.pdf
```

### 14.9 종료

단독 실행 서버 종료:

```bash
docker rm -f paddleocr_vl15_vllm
```

compose 종료:

```bash
cd /home/gon/work/ttt3/opendataloader-pdf/paddle/paddleocr_vll_docker
docker compose down
```



## 15. 저장소 실행 스크립트로 한 번에 처리
```
cd /home/gon/work/ttt3/opendataloader-pdf

./paddle/start_stack.sh

./paddle/run_opendataloader.sh samples/pdf/input1.pdf tmp/odl-hybrid/input1

./paddle/stop_stack.sh
```

위 명령을 직접 치지 않고, 저장소에 추가한 스크립트로 바로 실행할 수도 있습니다.




스크립트:

- `./paddle/start_stack.sh`
  - `vlm_server + odl_adapter` 기동
- `./paddle/run_opendataloader.sh <input-pdf> <output-dir>`
  - OpenDataLoader 실행
- `./paddle/stop_stack.sh`
  - stack 종료

가장 간단한 실행 예:

```bash
cd /home/gon/work/ttt3/opendataloader-pdf

./paddle/start_stack.sh

./paddle/run_opendataloader.sh \
  samples/pdf/input1.pdf \
  tmp/odl-hybrid/input1

./paddle/stop_stack.sh
```

다른 PDF로 실행:

```bash
cd /home/gon/work/ttt3/opendataloader-pdf

./paddle/start_stack.sh

./paddle/run_opendataloader.sh \
  /absolute/path/to/your.pdf \
  /absolute/path/to/output-dir
```

추가 OpenDataLoader 옵션 전달:

```bash
./paddle/run_opendataloader.sh \
  samples/pdf/input1.pdf \
  tmp/odl-hybrid/input1 \
  --overwrite
```

adapter URL 변경:

```bash
ADAPTER_URL=http://127.0.0.1:8090 \
./paddle/run_opendataloader.sh \
  samples/pdf/input1.pdf \
  tmp/odl-hybrid/input1
```

스크립트 기동 후 확인:

```bash
curl -s http://127.0.0.1:8090/health
curl -s http://127.0.0.1:8090/health/backend
```

cd /home/gon/work/ttt3/opendataloader-pdf
GPU_DEVICE=1 ./paddle/start_stack.sh
./paddle/start_stack.s

