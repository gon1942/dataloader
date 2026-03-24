# PaddleVL FastAPI Adapter

This directory contains a FastAPI adapter skeleton that sits in front of a
running `paddleocr_vl15_vllm` service and exposes the API shape expected by
OpenDataLoader hybrid mode.

Current status:

- `GET /health` is implemented
- `POST /v1/convert/file` is implemented as a skeleton
- Paddle response transformation is a placeholder and must be adapted to the
  real Paddle API response schema

## Goal

Expose a Docling-compatible surface for OpenDataLoader:

- `GET /health`
- `POST /v1/convert/file`

Expected deployment shape:

```text
OpenDataLoader CLI
  -> paddle/adapter FastAPI
    -> paddleocr_vl15_vllm
```

## Files

- `app.py`: FastAPI entrypoint
- `config.py`: environment-based settings
- `transform.py`: placeholder transformer from Paddle JSON to hybrid response
- `requirements.txt`: minimal Python dependencies
- `Dockerfile`: local container image for the adapter

## Environment variables

- `PADDLE_BASE_URL`
  Example: `http://127.0.0.1:8080`
- `PADDLE_HEALTH_PATH`
  Default: `/v1/models`
- `PADDLE_CONVERT_PATH`
  Default: `/v1/chat/completions`
- `REQUEST_TIMEOUT_SECONDS`
  Default: `120`
- `LOG_LEVEL`
  Default: `INFO`

## Local run

```bash
cd paddle/adapter
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8080 --reload
```

## Docker run

```bash
docker build -t odl-paddle-adapter paddle/adapter
docker run --rm -p 8080:8080 \
  -e PADDLE_BASE_URL=http://host.docker.internal:8080 \
  -e PADDLE_HEALTH_PATH=/v1/models \
  odl-paddle-adapter
```

## OpenDataLoader test

Point OpenDataLoader to this adapter:

```bash
opendataloader-pdf \
  --hybrid docling-fast \
  --hybrid-url http://localhost:8090 \
  -o ./tmp/odl-hybrid/input1 \
  samples/pdf/input1.pdf
```

## With `paddleocr_vll_docker`

If you use the sibling Docker setup under `paddle/paddleocr_vll_docker`, the
intended layout is:

```text
OpenDataLoader -> http://localhost:8090
adapter -> http://localhost:8080/v1/chat/completions
adapter health probe -> http://localhost:8080/v1/models
```

## Required next step

Update `transform.py` after capturing a real response payload from
`paddleocr_vl15_vllm`. Without that mapping, the adapter returns a structured
placeholder response rather than a usable document schema.
