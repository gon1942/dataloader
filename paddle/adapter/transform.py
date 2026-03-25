from __future__ import annotations

import html
import re
from html.parser import HTMLParser
from typing import Any


TABLE_RE = re.compile(r"(?is)<table\b.*?</table>")
TAG_RE = re.compile(r"(?is)<[^>]+>")
ALLOWED_PICTURE_LABELS = {
    "figure",
    "image",
    "chart",
    "photo",
    "diagram",
}


def build_placeholder_hybrid_response(paddle_payload: Any) -> dict[str, Any]:
    json_content = build_docling_like_document(paddle_payload)
    return {
        "status": "success",
        "document": {
            "json_content": json_content,
        },
        "processing_time": 0.0,
        "errors": [],
        "failed_pages": [],
    }


def build_docling_like_document(paddle_payload: Any) -> dict[str, Any]:
    pages_payload = paddle_payload if isinstance(paddle_payload, list) else [paddle_payload]

    document: dict[str, Any] = {
        "pages": {},
        "texts": [],
        "tables": [],
        "pictures": [],
    }

    for index, page_payload in enumerate(pages_payload):
        page_no = _resolve_page_number(page_payload, index)
        document["pages"][str(page_no)] = {"page_no": page_no}

        markdown_text = _extract_markdown_text(page_payload)
        page_bbox = _build_page_bbox(page_payload)

        if markdown_text:
            blocks = _extract_ordered_blocks(markdown_text)
            total_blocks = max(1, len(blocks))
            for block_index, block in enumerate(blocks):
                block_bbox = _offset_block_bbox(
                    page_bbox=page_bbox,
                    index=block_index,
                    total=total_blocks,
                    block_type=block["type"],
                )
                if block["type"] == "text":
                    document["texts"].append(
                        _make_text_node(
                            text=block["text"],
                            page_no=page_no,
                            bbox=block_bbox,
                        )
                    )
                    continue

                table_data = _parse_html_table(block["html"])
                if table_data is None:
                    continue
                document["tables"].append(
                    _make_table_node(
                        page_no=page_no,
                        bbox=block_bbox,
                        rows=table_data["rows"],
                        cells=table_data["cells"],
                    )
                )
        else:
            fallback_text = _extract_fallback_text(page_payload)
            if fallback_text:
                document["texts"].append(
                    _make_text_node(
                        text=fallback_text,
                        page_no=page_no,
                        bbox=page_bbox,
                    )
                )

        for picture_index, picture_bbox in enumerate(_extract_picture_bboxes(page_payload), start=1):
            document["pictures"].append(
                _make_picture_node(
                    page_no=page_no,
                    bbox=picture_bbox,
                    description=f"paddle picture {page_no}-{picture_index}",
                )
            )

    return document


def _resolve_page_number(page_payload: Any, index: int) -> int:
    if isinstance(page_payload, dict):
        value = page_payload.get("page_index")
        if isinstance(value, int):
            return value + 1
        value = page_payload.get("page_no")
        if isinstance(value, int):
            return value
    return index + 1


def _extract_markdown_text(page_payload: Any) -> str:
    if not isinstance(page_payload, dict):
        return ""
    markdown = page_payload.get("markdown")
    if isinstance(markdown, dict):
        text = markdown.get("markdown_texts") or markdown.get("text")
        if isinstance(text, str):
            return text.strip()
    return ""


def _extract_ordered_blocks(markdown_text: str) -> list[dict[str, str]]:
    blocks: list[dict[str, str]] = []
    last_end = 0

    for match in TABLE_RE.finditer(markdown_text):
        if match.start() > last_end:
            blocks.extend(_extract_text_blocks(markdown_text[last_end:match.start()]))
        table_html = match.group(0).strip()
        if table_html:
            blocks.append({"type": "table", "html": table_html})
        last_end = match.end()

    if last_end < len(markdown_text):
        blocks.extend(_extract_text_blocks(markdown_text[last_end:]))

    return blocks


def _extract_text_blocks(text: str) -> list[dict[str, str]]:
    text_without_tags = html.unescape(TAG_RE.sub(" ", text))
    blocks = []
    for chunk in re.split(r"\n\s*\n", text_without_tags):
        normalized = " ".join(chunk.split())
        if normalized:
            blocks.append({"type": "text", "text": normalized})
    return blocks


def _extract_fallback_text(page_payload: Any) -> str:
    if not isinstance(page_payload, dict):
        return ""

    pruned_result = page_payload.get("pruned_result")
    if isinstance(pruned_result, dict):
        texts = []
        _collect_text_values(pruned_result, texts)
        normalized = " ".join(value for value in texts if value)
        return normalized.strip()

    raw_result = page_payload.get("raw_result")
    if isinstance(raw_result, dict):
        texts = []
        _collect_text_values(raw_result, texts)
        normalized = " ".join(value for value in texts if value)
        return normalized.strip()

    return ""


def _collect_text_values(node: Any, output: list[str]) -> None:
    if isinstance(node, dict):
        for key, value in node.items():
            if key in {"text", "content", "block_content", "markdown_texts"} and isinstance(value, str):
                normalized = " ".join(value.split())
                if normalized:
                    output.append(normalized)
            else:
                _collect_text_values(value, output)
        return
    if isinstance(node, list):
        for item in node:
            _collect_text_values(item, output)


def _build_page_bbox(page_payload: Any) -> dict[str, Any]:
    bbox = _find_bbox(page_payload.get("pruned_result") if isinstance(page_payload, dict) else None)
    if bbox is not None:
        return bbox

    page_width, page_height = _extract_page_dimensions(page_payload)
    if page_width > 0 and page_height > 0:
        return {
            "l": 0.0,
            "t": float(page_height),
            "r": float(page_width),
            "b": 0.0,
            "coord_origin": "BOTTOMLEFT",
        }

    return {
        "l": 0.0,
        "t": 1000.0,
        "r": 1000.0,
        "b": 0.0,
        "coord_origin": "BOTTOMLEFT",
    }


def _extract_page_dimensions(page_payload: Any) -> tuple[float, float]:
    if not isinstance(page_payload, dict):
        return 0.0, 0.0

    for key in ("pruned_result", "raw_result"):
        result = page_payload.get(key)
        if not isinstance(result, dict):
            continue
        width = result.get("width")
        height = result.get("height")
        if isinstance(width, (int, float)) and isinstance(height, (int, float)):
            return float(width), float(height)
    return 0.0, 0.0


def _find_bbox(node: Any) -> dict[str, Any] | None:
    if isinstance(node, dict):
        candidate = node.get("bbox")
        if isinstance(candidate, dict) and {"l", "t", "r", "b"} <= set(candidate.keys()):
            return {
                "l": float(candidate.get("l", 0.0)),
                "t": float(candidate.get("t", 0.0)),
                "r": float(candidate.get("r", 0.0)),
                "b": float(candidate.get("b", 0.0)),
                "coord_origin": candidate.get("coord_origin", "BOTTOMLEFT"),
            }
        for value in node.values():
            found = _find_bbox(value)
            if found is not None:
                return found
    elif isinstance(node, list):
        for item in node:
            found = _find_bbox(item)
            if found is not None:
                return found
    return None


def _offset_block_bbox(page_bbox: dict[str, Any], index: int, total: int, block_type: str) -> dict[str, Any]:
    height = page_bbox["t"] - page_bbox["b"]
    if height <= 0:
        return dict(page_bbox)

    band = height / max(total, 1)
    top = page_bbox["t"] - (index * band)
    block_fill_ratio = 0.9 if block_type == "table" else 0.7
    bottom = max(page_bbox["b"], top - (band * block_fill_ratio))
    return {
        "l": page_bbox["l"],
        "t": top,
        "r": page_bbox["r"],
        "b": bottom,
        "coord_origin": page_bbox.get("coord_origin", "BOTTOMLEFT"),
    }


def _extract_picture_bboxes(page_payload: Any) -> list[dict[str, Any]]:
    if not isinstance(page_payload, dict):
        return []

    raw_result = page_payload.get("raw_result")
    if not isinstance(raw_result, dict):
        return []

    layout_det_res = raw_result.get("layout_det_res")
    if not isinstance(layout_det_res, dict):
        return []

    boxes = layout_det_res.get("boxes")
    if not isinstance(boxes, list):
        return []

    picture_bboxes: list[dict[str, Any]] = []
    seen: set[tuple[float, float, float, float]] = set()
    for box in boxes:
        if not isinstance(box, dict):
            continue
        label = box.get("label")
        if not isinstance(label, str):
            continue
        normalized_label = label.strip().lower()
        if normalized_label not in ALLOWED_PICTURE_LABELS:
            continue
        coordinate = box.get("coordinate")
        if not (
            isinstance(coordinate, list)
            and len(coordinate) == 4
            and all(isinstance(value, (int, float)) for value in coordinate)
        ):
            continue

        key = tuple(float(value) for value in coordinate)
        if key in seen:
            continue
        seen.add(key)
        l, t, r, b = key[0], key[1], key[2], key[3]
        picture_bboxes.append(
            {
                "l": l,
                "t": t,
                "r": r,
                "b": b,
                "coord_origin": "TOPLEFT",
            }
        )

    return picture_bboxes


def _make_text_node(text: str, page_no: int, bbox: dict[str, Any]) -> dict[str, Any]:
    return {
        "label": "text",
        "text": text,
        "orig": text,
        "prov": [
            {
                "page_no": page_no,
                "bbox": bbox,
            }
        ],
    }


def _make_picture_node(page_no: int, bbox: dict[str, Any], description: str | None) -> dict[str, Any]:
    picture: dict[str, Any] = {
        "label": "picture",
        "prov": [
            {
                "page_no": page_no,
                "bbox": bbox,
            }
        ],
    }
    if description:
        picture["annotations"] = [
            {
                "kind": "description",
                "text": description,
            }
        ]
    return picture


def _make_table_node(page_no: int, bbox: dict[str, Any], rows: list[list[str]], cells: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "label": "table",
        "prov": [
            {
                "page_no": page_no,
                "bbox": bbox,
            }
        ],
        "data": {
            "grid": rows,
            "table_cells": cells,
        },
    }


class _TableHtmlParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.rows: list[list[dict[str, Any]]] = []
        self._in_tr = False
        self._in_cell = False
        self._current_row: list[dict[str, Any]] = []
        self._current_cell: dict[str, Any] | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_dict = dict(attrs)
        if tag == "tr":
            self._in_tr = True
            self._current_row = []
            return
        if tag not in {"td", "th"} or not self._in_tr:
            return

        self._in_cell = True
        self._current_cell = {
            "text": "",
            "row_span": _safe_int(attrs_dict.get("rowspan"), 1),
            "col_span": _safe_int(attrs_dict.get("colspan"), 1),
            "column_header": tag == "th",
        }

    def handle_endtag(self, tag: str) -> None:
        if tag == "tr":
            if self._in_tr and self._current_row:
                self.rows.append(self._current_row)
            self._in_tr = False
            self._current_row = []
            return
        if tag not in {"td", "th"}:
            return
        if self._in_cell and self._current_cell is not None:
            self._current_cell["text"] = " ".join(self._current_cell["text"].split())
            self._current_row.append(self._current_cell)
        self._in_cell = False
        self._current_cell = None

    def handle_data(self, data: str) -> None:
        if self._in_cell and self._current_cell is not None:
            self._current_cell["text"] += data


def _parse_html_table(table_html: str) -> dict[str, Any] | None:
    parser = _TableHtmlParser()
    parser.feed(table_html)
    parser.close()

    if not parser.rows:
        return None

    row_count = len(parser.rows)
    col_count = _estimate_column_count(parser.rows)
    if col_count <= 0:
        return None

    grid = [["" for _ in range(col_count)] for _ in range(row_count)]
    occupied = [[False for _ in range(col_count)] for _ in range(row_count)]
    cells: list[dict[str, Any]] = []

    for row_index, row in enumerate(parser.rows):
        col_index = 0
        for cell in row:
            while col_index < col_count and occupied[row_index][col_index]:
                col_index += 1
            if col_index >= col_count:
                break

            row_span = max(1, int(cell.get("row_span", 1)))
            col_span = max(1, int(cell.get("col_span", 1)))
            text = cell.get("text", "")

            for r in range(row_index, min(row_count, row_index + row_span)):
                for c in range(col_index, min(col_count, col_index + col_span)):
                    occupied[r][c] = True
                    grid[r][c] = text

            cells.append(
                {
                    "start_row_offset_idx": row_index,
                    "end_row_offset_idx": min(row_count - 1, row_index + row_span - 1),
                    "start_col_offset_idx": col_index,
                    "end_col_offset_idx": min(col_count - 1, col_index + col_span - 1),
                    "text": text,
                    "column_header": bool(cell.get("column_header", False)),
                }
            )
            col_index += col_span

    return {"rows": grid, "cells": cells}


def _estimate_column_count(rows: list[list[dict[str, Any]]]) -> int:
    max_columns = 0
    for row in rows:
        width = 0
        for cell in row:
            width += max(1, int(cell.get("col_span", 1)))
        max_columns = max(max_columns, width)
    return max_columns


def _safe_int(value: Any, default: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default
