from __future__ import annotations

import logging
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
    version="0.1.0",
    description="FastAPI adapter skeleton for paddleocr_vl15_vllm.",
)


@app.get("/health")
async def health() -> dict[str, Any]:
    """Adapter health check.

    This only verifies that the adapter process is alive. The backend probe is
    exposed separately via `/health/backend`.
    """
    return {
        "status": "ok",
        "adapter": "alive",
        "paddle_base_url": settings.paddle_base_url,
    }


@app.get("/health/backend")
async def backend_health() -> JSONResponse:
    """Proxy a lightweight health check to the Paddle backend."""
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
    """Accept a PDF like docling-fast and forward it to the Paddle backend.

    This is only the transport skeleton. The backend request shape here is a
    placeholder and should be aligned with the real PaddleVL server contract.
    """
    pdf_bytes = await files.read()
    if not pdf_bytes:
        raise HTTPException(status_code=400, detail="Uploaded file is empty")

    logger.info(
        "Forwarding %s bytes to Paddle backend. filename=%s page_ranges=%s",
        len(pdf_bytes),
        files.filename or "document.pdf",
        page_ranges,
    )

    try:
        paddle_payload = await _call_paddle_backend(
            pdf_bytes=pdf_bytes,
            filename=files.filename or "document.pdf",
            page_ranges=page_ranges,
        )
    except httpx.HTTPStatusError as exc:
        body = exc.response.text if exc.response is not None else ""
        raise HTTPException(
            status_code=502,
            detail=(
                f"Paddle backend returned HTTP {exc.response.status_code if exc.response else 'unknown'} "
                f"for {settings.paddle_convert_url()}: {body}"
            ),
        ) from exc
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Failed to call Paddle backend at {settings.paddle_convert_url()}: {exc}",
        ) from exc

    content = build_placeholder_hybrid_response(paddle_payload)
    return JSONResponse(content=content)


async def _call_paddle_backend(
    pdf_bytes: bytes,
    filename: str,
    page_ranges: str | None,
) -> Any:
    """Placeholder request to the Paddle backend.

    Replace this request shape once the actual `paddleocr_vl15_vllm` API
    contract is confirmed.
    """
    files = {
        "file": (filename, pdf_bytes, "application/pdf"),
    }
    data = {}
    if page_ranges:
        data["page_ranges"] = page_ranges

    async with httpx.AsyncClient(timeout=settings.request_timeout_seconds) as client:
        response = await client.post(
            settings.paddle_convert_url(),
            files=files,
            data=data,
        )
        response.raise_for_status()
        return _safe_json_or_text(response)


def _safe_json_or_text(response: httpx.Response) -> Any:
    try:
        return response.json()
    except ValueError:
        return {"raw_text": response.text}
