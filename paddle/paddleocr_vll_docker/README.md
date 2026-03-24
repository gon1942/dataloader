cd paddle/paddleocr_vll_docker


docker build -t paddleocr_vl15_client ./client

-------------------------
client
-------------------------
docker run --rm --network host -v "$PWD/data:/data" \
-e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
paddleocr_vl15_client \
python /app/ocr_pdf.py --input /data/input.pdf --out_dir /data/out --server_url http://127.0.0.1:8080/v1  --make_html

# OpenDataLoader hybrid adapter
docker build -t odl-paddle-adapter ../adapter
docker run --rm --network host \
  -e PADDLE_BASE_URL=http://127.0.0.1:8080 \
  -e PADDLE_HEALTH_PATH=/v1/models \
  -e PADDLE_CONVERT_PATH=/v1/chat/completions \
  odl-paddle-adapter \
  uvicorn app:app --host 0.0.0.0 --port 8090

# 하이브리드 파이프라인(auto: 디지털 표 추출 우선, 실패 시 OCR fallback)
docker run --rm --network host -v "$PWD/data:/data" \
-e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
paddleocr_vl15_client \
python /app/hybrid_pdf_pipeline.py \
  --input /data/input.pdf \
  --out_dir /data/out_hybrid \
  --server_url http://127.0.0.1:8080/v1 \
  --mode auto \
  --dpi 300 \
  --render_mode gray \
  --temperature 0.0 \
  --top_p 1.0 \
  --repetition_penalty 1.0 \
  --make_html

# 표 병합셀(rowspan/colspan) 정확도를 위해 PDF를 고해상도 이미지로 렌더링 후 OCR
docker run --rm --network host -v "$PWD/data:/data" \
-e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
paddleocr_vl15_client \
python /app/ocr_pdf.py --input /data/input.pdf --out_dir /data/out --server_url http://127.0.0.1:8080/v1 --dpi 300 --make_html

# 병합셀 후처리(휴리스틱)까지 적용
docker run --rm --network host -v "$PWD/data:/data" \
-e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
paddleocr_vl15_client \
python /app/ocr_pdf.py --input /data/input.pdf --out_dir /data/out --server_url http://127.0.0.1:8080/v1 --dpi 300 --refine_table_spans --span_head_rows 4 --span_left_cols 3 --span_min_run 0 --make_html

# 결과 변동 최소화 + 선명도 개선(권장)
docker run --rm --network host -v "$PWD/data:/data" \
-e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
paddleocr_vl15_client \
python /app/ocr_pdf.py --input /data/input.pdf --out_dir /data/out --server_url http://127.0.0.1:8080/v1 --dpi 300 --render_mode gray --temperature 0.0 --top_p 1.0 --repetition_penalty 1.0 --refine_table_spans --span_head_rows 4 --span_left_cols 3 --span_min_run 1 --make_html

# 특정 라벨(예: 매입비하/매입비화) 행은 좌측 병합(colspan=2) 강제
docker run --rm --network host -v "$PWD/data:/data" \
-e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
paddleocr_vl15_client \
python /app/ocr_pdf.py --input /data/input.pdf --out_dir /data/out --server_url http://127.0.0.1:8080/v1 --dpi 300 --render_mode gray --temperature 0.0 --refine_table_spans --force_colspan2_labels "매입비하,매입비화" --make_html



---------------------------------------------------

docker build --no-cache -t paddleocr_vl15_client ./client

docker run --rm --network host -v "$PWD/data:/data" \
-e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
paddleocr_vl15_client \
python /app/ocr_pdf.py --input /data/input.pdf --out_dir /data/out --server_url http://127.0.0.1:8080/v1 --dpi 300 --make_html

docker run --rm paddleocr_vl15_client python /app/ocr_pdf.py -h

***************************
docker build --no-cache -t paddleocr_vl15_client ./client



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
  --span_min_run 0 \
  --make_html


pdftoppm -r 400 -png data/input.pdf /tmp/render_check/color_page
pdftoppm -r 400 -gray -png data/input.pdf /tmp/render_check/gray_page
pdftoppm -r 400 -mono -png data/input.pdf /tmp/render_check/mono_page


--refine_table_spans
OCR 결과의 <table>에서 비어있는 연속 셀 패턴을 보고 rowspan/colspan을 자동 보정합니다.
없으면 보정 없이 원본 OCR 테이블 그대로 사용합니다.

--span_head_rows 4
상단 4개 행을 “헤더 영역”으로 보고, 가로 방향 병합(colspan) 보정을 시도합니다.
값이 클수록 더 아래 행까지 헤더로 간주해 보정합니다.

--span_left_cols 3
좌측 3개 열을 “라벨 영역”으로 보고, 세로 방향 병합(rowspan) 보정을 시도합니다.
보통 분류/대분류/중분류 같은 왼쪽 계층 열 수에 맞추면 좋습니다.

--span_min_run 1
병합할 때 필요한 “연속 빈 셀” 최소 개수입니다.
1이면 빈 셀 1개만 있어도 병합(공격적), 2 이상이면 더 보수적으로 병합합니다.

-------------------------------
server
--------------------------------

docker pull ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddleocr-genai-vllm-server:latest-nvidia-gpu


docker run -d --name paddleocr_vl15_vllm --gpus all --network host \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddleocr-genai-vllm-server:latest-nvidia-gpu \
  paddleocr genai_server --model_name PaddleOCR-VL-1.5-0.9B --host 0.0.0.0 --port 8080 --backend vllm

# adapter 포함 전체 기동
docker compose up -d vlm_server odl_adapter

# adapter health check
curl -s http://127.0.0.1:8090/health
curl -s http://127.0.0.1:8090/health/backend

# OpenDataLoader hybrid test
opendataloader-pdf \
  --hybrid docling-fast \
  --hybrid-url http://127.0.0.1:8090 \
  -o ./tmp/odl-hybrid/input1 \
  samples/pdf/input1.pdf



=====================

docker run --rm --network host -v "$PWD/data:/data" \
-e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
paddleocr_vl15_client \
python /app/ocr_pdf.py --input /data/input.pdf --out_dir /data/out --server_url http://127.0.0.1:8080/v1





GPU 1을 지정해서 서버를 재실행:
docker rm -f paddleocr_vl15_vllm && \
docker run -d --name paddleocr_vl15_vllm --gpus '"device=1"' --network host \
  --shm-size 8g \
  -e PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True \
  ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlepaddle/paddleocr-genai-vllm-server:latest-nvidia-gpu \
  paddleocr genai_server --model_name PaddleOCR-VL-1.5-0.9B --host 0.0.0.0 --port 8080 --backend vllm
