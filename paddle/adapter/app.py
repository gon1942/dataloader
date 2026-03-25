from __future__ import annotations

import asyncio
import logging
import tempfile
import threading
from pathlib import Path
from typing import Any

import httpx
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from config import settings
from transform import build_placeholder_hybrid_response


logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger("odl-paddle-adapter")

app = FastAPI(
    title="OpenDataLoader Paddle Adapter",
    version="0.2.0",
    description="FastAPI adapter that runs PaddleOCRVL against a vLLM server.",
)

_pipeline_lock = threading.Lock()
_pipeline_instance: Any | None = None


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "adapter": "alive",
        "paddle_base_url": settings.paddle_base_url,
        "paddle_vllm_server_url": settings.paddle_vllm_server_url(),
    }


@app.get("/health/backend")
async def backend_health() -> JSONResponse:
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(settings.paddle_health_url())
        return JSONResponse(
            status_code=response.status_code,
            content={
                "status_code": response.status_code,
                "backend_url": settings.paddle_health_url(),
                "body": _safe_json_or_text(response),
            },
        )
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Unable to reach Paddle backend at {settings.paddle_health_url()}: {exc}",
        ) from exc


@app.post("/v1/convert/file")
async def convert_file(
    files: UploadFile = File(...),
    page_ranges: str | None = Form(None),
) -> JSONResponse:
    paddle_payload = await _run_paddle(files=files, page_ranges=page_ranges)
    content = build_placeholder_hybrid_response(paddle_payload)
    content["paddle_raw"] = {
        "pages": paddle_payload if isinstance(paddle_payload, list) else [paddle_payload],
    }
    return JSONResponse(content=content)


@app.post("/v1/convert/file/paddle-raw")
async def convert_file_paddle_raw(
    files: UploadFile = File(...),
    page_ranges: str | None = Form(None),
) -> JSONResponse:
    paddle_payload = await _run_paddle(files=files, page_ranges=page_ranges)
    return JSONResponse(
        content={
            "status": "success",
            "source": "paddle_raw",
            "pages": paddle_payload if isinstance(paddle_payload, list) else [paddle_payload],
        }
    )


async def _run_paddle(files: UploadFile, page_ranges: str | None) -> Any:
    pdf_bytes = await files.read()
    if not pdf_bytes:
        raise HTTPException(status_code=400, detail="Uploaded file is empty")

    suffix = Path(files.filename or "document.pdf").suffix or ".pdf"

    logger.info(
        "Running PaddleOCRVL for filename=%s bytes=%s page_ranges=%s via %s",
        files.filename or "document.pdf",
        len(pdf_bytes),
        page_ranges,
        settings.paddle_vllm_server_url(),
    )
    if page_ranges:
        logger.warning("page_ranges is currently ignored by the Paddle adapter: %s", page_ranges)

    tmp_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp.write(pdf_bytes)
            tmp_path = Path(tmp.name)

        paddle_payload = await asyncio.to_thread(_predict_pdf, tmp_path)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=502,
            detail=f"PaddleOCRVL execution failed: {exc}",
        ) from exc
    finally:
        if tmp_path is not None:
            try:
                tmp_path.unlink(missing_ok=True)
            except OSError:
                logger.warning("Failed to remove temporary file: %s", tmp_path)

    return paddle_payload


def _predict_pdf(pdf_path: Path) -> Any:
    pipeline = _get_pipeline()
    outputs = pipeline.predict(str(pdf_path))
    return _normalize_outputs(outputs)


def _get_pipeline() -> Any:
    global _pipeline_instance
    if _pipeline_instance is not None:
        return _pipeline_instance

    with _pipeline_lock:
        if _pipeline_instance is not None:
            return _pipeline_instance

        try:
            from paddleocr import PaddleOCRVL
        except Exception as exc:
            raise RuntimeError(
                "Unable to import PaddleOCRVL. Check adapter runtime dependencies, "
                "including paddleocr/paddlex and NumPy compatibility."
            ) from exc

        logger.info("Initializing PaddleOCRVL with vLLM server %s", settings.paddle_vllm_server_url())
        _pipeline_instance = PaddleOCRVL(
            vl_rec_backend="vllm-server",
            vl_rec_server_url=settings.paddle_vllm_server_url(),
        )
        return _pipeline_instance


def _normalize_outputs(outputs: Any) -> Any:
    if isinstance(outputs, list):
        return [_normalize_output_item(index, item) for index, item in enumerate(outputs)]
    return _normalize_output_item(0, outputs)


def _normalize_output_item(page_index: int, item: Any) -> Any:
    normalized: dict[str, Any] = {"page_index": page_index}

    raw_json = _extract_result_json(item)
    if raw_json is not None:
        normalized["pruned_result"] = _prune_result(raw_json)
        normalized["raw_result"] = _normalize_plain_data(raw_json)

    markdown = _extract_markdown(item)
    if markdown is not None:
        normalized["markdown"] = markdown

    if len(normalized) > 1:
        return normalized

    if hasattr(item, "to_dict"):
        try:
            return item.to_dict()
        except Exception:
            pass
    return _normalize_plain_data(item)


def _extract_result_json(item: Any) -> dict[str, Any] | None:
    json_value = getattr(item, "json", None)
    if isinstance(json_value, dict):
        result = json_value.get("res")
        if isinstance(result, dict):
            return result
    if isinstance(item, dict):
        result = item.get("res")
        if isinstance(result, dict):
            return result
    return None


def _extract_markdown(item: Any) -> dict[str, Any] | None:
    to_markdown = getattr(item, "_to_markdown", None)
    if callable(to_markdown):
        try:
            markdown_data = to_markdown(pretty=True, show_formula_number=False)
            if isinstance(markdown_data, dict):
                return _normalize_plain_data(markdown_data)
        except Exception as exc:
            logger.warning("Failed to extract markdown from Paddle result: %s", exc)
    return None


def _prune_result(result: dict[str, Any]) -> dict[str, Any]:
    keys_to_remove = {"input_path", "page_index"}

    def _process(obj: Any) -> Any:
        if isinstance(obj, dict):
            return {
                key: _process(value)
                for key, value in obj.items()
                if key not in keys_to_remove
            }
        if isinstance(obj, list):
            return [_process(value) for value in obj]
        return obj

    return _process(result)


def _normalize_plain_data(item: Any) -> Any:
    if isinstance(item, dict):
        return {key: _normalize_plain_data(value) for key, value in item.items()}
    if isinstance(item, (list, tuple)):
        return [_normalize_plain_data(value) for value in item]
    if isinstance(item, (str, int, float, bool)) or item is None:
        return item
    if hasattr(item, "__dict__"):
        return {key: _normalize_plain_data(value) for key, value in vars(item).items()}
    return str(item)


def _safe_json_or_text(response: httpx.Response) -> Any:
    try:
        return response.json()
    except ValueError:
        return {"raw_text": response.text}
