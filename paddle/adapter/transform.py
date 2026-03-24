from __future__ import annotations

from typing import Any


def build_placeholder_hybrid_response(paddle_payload: Any) -> dict[str, Any]:
    """Wrap the raw Paddle payload in an OpenDataLoader-like response envelope.

    This is intentionally a placeholder. OpenDataLoader hybrid mode expects a
    Docling-like `document.json_content` payload. Once the real Paddle response
    schema is known, replace this function with a true mapping.
    """

    return {
        "status": "success",
        "document": {
            "json_content": {
                "pages": {},
                "texts": [],
                "tables": [],
                "backend_payload": paddle_payload,
            }
        },
        "processing_time": 0.0,
        "errors": [
            "Adapter skeleton in use: Paddle response has not been mapped to OpenDataLoader schema yet."
        ],
        "failed_pages": [],
    }
