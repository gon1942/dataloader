import argparse
import json
import subprocess
import sys
from pathlib import Path


HTML_TEMPLATE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>{title}</title>
<style>
  body {{
    margin: 16px;
    font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif;
  }}
  h1 {{
    font-size: 18px;
    margin: 0 0 12px 0;
  }}
  .hint {{
    color: rgba(0,0,0,.65);
    font-size: 12px;
    margin-bottom: 12px;
  }}
  table {{
    border-collapse: collapse;
    width: 100%;
  }}
  td, th {{
    border: 1px solid #ccc;
    padding: 6px;
    vertical-align: top;
  }}
  pre {{
    white-space: pre-wrap;
    word-break: break-word;
    background: #f7f7f7;
    padding: 10px;
    border-radius: 10px;
  }}
</style>
</head>
<body>
<h1>{title}</h1>
<div class="hint">source: {src}</div>
{body}
</body>
</html>
"""


def _run(cmd: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, text=True, capture_output=True, check=False)


def has_text_layer(pdf_path: Path, min_chars: int) -> bool:
    cmd = ["pdftotext", "-f", "1", "-l", "3", "-nopgbrk", str(pdf_path), "-"]
    proc = _run(cmd)
    if proc.returncode != 0:
        return False
    text = proc.stdout or ""
    return len(text.strip()) >= min_chars


def normalize_rows(rows: list[list[str | None]]) -> list[list[str | None]]:
    width = 0
    for row in rows:
        if len(row) > width:
            width = len(row)
    if width == 0:
        return []
    out = []
    for row in rows:
        out.append(list(row) + [None] * (width - len(row)))
    return out


def table_matrix_to_html(rows: list[list[str | None]]) -> str:
    grid = normalize_rows(rows)
    if not grid:
        return "<table></table>"

    h = len(grid)
    w = len(grid[0])
    used = [[False] * w for _ in range(h)]
    parts = ["<table border=\"1\" style=\"margin: auto; word-wrap: break-word;\">"]

    for r in range(h):
        parts.append("<tr>")
        for c in range(w):
            if used[r][c]:
                continue

            val = grid[r][c]
            txt = "" if val is None else str(val).strip()
            if txt == "":
                used[r][c] = True
                parts.append("<td style=\"text-align: center; word-wrap: break-word;\"></td>")
                continue

            colspan = 1
            while c + colspan < w and grid[r][c + colspan] is None and not used[r][c + colspan]:
                colspan += 1

            rowspan = 1
            while r + rowspan < h:
                ok = True
                for cc in range(c, c + colspan):
                    if used[r + rowspan][cc]:
                        ok = False
                        break
                    if grid[r + rowspan][cc] is not None:
                        ok = False
                        break
                if not ok:
                    break
                rowspan += 1

            for rr in range(r, r + rowspan):
                for cc in range(c, c + colspan):
                    used[rr][cc] = True

            attrs = []
            if rowspan > 1:
                attrs.append(f"rowspan=\"{rowspan}\"")
            if colspan > 1:
                attrs.append(f"colspan=\"{colspan}\"")
            attr_txt = (" " + " ".join(attrs)) if attrs else ""
            parts.append(
                f"<td{attr_txt} style=\"text-align: center; word-wrap: break-word;\">{txt}</td>"
            )
        parts.append("</tr>")

    parts.append("</table>")
    return "".join(parts)


def write_html_from_md(md_path: Path) -> None:
    md_text = md_path.read_text(encoding="utf-8", errors="replace")
    if "<table" in md_text:
        body = md_text
    else:
        body = f"<pre>{md_text}</pre>"
    html = HTML_TEMPLATE.format(title=md_path.name, src=str(md_path), body=body)
    md_path.with_suffix(".html").write_text(html, encoding="utf-8")


def extract_with_pdfplumber(pdf_path: Path, out_dir: Path, make_html: bool) -> tuple[int, int]:
    try:
        import pdfplumber
    except Exception as exc:
        raise RuntimeError(
            "pdfplumber is required for digital extraction. Install: pip install pdfplumber"
        ) from exc

    md_dir = out_dir / "md"
    json_dir = out_dir / "json"
    md_dir.mkdir(parents=True, exist_ok=True)
    json_dir.mkdir(parents=True, exist_ok=True)

    page_count = 0
    table_count = 0

    with pdfplumber.open(str(pdf_path)) as pdf:
        for page_idx, page in enumerate(pdf.pages):
            page_count += 1
            settings = {
                "vertical_strategy": "lines",
                "horizontal_strategy": "lines",
                "intersection_x_tolerance": 3,
                "intersection_y_tolerance": 3,
            }
            tables = page.extract_tables(table_settings=settings) or []
            html_tables = []
            for t in tables:
                if not t:
                    continue
                html_tables.append(table_matrix_to_html(t))
                table_count += 1

            md_body = "\n\n".join(html_tables) if html_tables else (page.extract_text() or "")
            md_path = md_dir / f"page-{page_idx}.md"
            md_path.write_text(md_body, encoding="utf-8")

            blocks = []
            if html_tables:
                for html_table in html_tables:
                    blocks.append({"block_label": "table", "block_content": html_table})
            elif md_body.strip():
                blocks.append({"block_label": "text", "block_content": md_body})

            payload = {"result": {"blocks": blocks}}
            (json_dir / f"page-{page_idx}_res.json").write_text(
                json.dumps(payload, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

            if make_html:
                write_html_from_md(md_path)

    return page_count, table_count


def run_ocr_fallback(
    input_pdf: Path,
    out_dir: Path,
    server_url: str,
    make_html: bool,
    dpi: int,
    render_mode: str,
    temperature: float,
    top_p: float,
    repetition_penalty: float,
) -> None:
    ocr_script = Path(__file__).with_name("ocr_pdf.py")
    cmd = [
        sys.executable,
        str(ocr_script),
        "--input",
        str(input_pdf),
        "--out_dir",
        str(out_dir),
        "--server_url",
        server_url,
        "--dpi",
        str(dpi),
        "--render_mode",
        render_mode,
        "--temperature",
        str(temperature),
        "--top_p",
        str(top_p),
        "--repetition_penalty",
        str(repetition_penalty),
    ]
    if make_html:
        cmd.append("--make_html")

    proc = subprocess.run(cmd, check=False)
    if proc.returncode != 0:
        raise RuntimeError(f"OCR fallback failed with exit code {proc.returncode}")


def main() -> None:
    ap = argparse.ArgumentParser(
        description="Hybrid PDF pipeline: digital table extraction first, OCR fallback."
    )
    ap.add_argument("--input", required=True, help="Input PDF path")
    ap.add_argument("--out_dir", required=True, help="Output directory")
    ap.add_argument("--server_url", required=True, help="vLLM server URL for OCR fallback")
    ap.add_argument("--mode", choices=["auto", "digital", "ocr"], default="auto")
    ap.add_argument("--make_html", action="store_true", help="Write HTML next to markdown outputs")
    ap.add_argument(
        "--text_layer_min_chars",
        type=int,
        default=40,
        help="Auto mode threshold: if first pages text chars >= this, try digital path first",
    )
    ap.add_argument(
        "--digital_min_tables",
        type=int,
        default=1,
        help="Auto mode threshold: digital path succeeds only if extracted tables >= this",
    )
    ap.add_argument("--dpi", type=int, default=300, help="OCR fallback DPI")
    ap.add_argument(
        "--render_mode",
        choices=["color", "gray", "mono"],
        default="gray",
        help="OCR fallback render mode",
    )
    ap.add_argument("--temperature", type=float, default=0.0, help="OCR fallback temperature")
    ap.add_argument("--top_p", type=float, default=1.0, help="OCR fallback top_p")
    ap.add_argument(
        "--repetition_penalty", type=float, default=1.0, help="OCR fallback repetition penalty"
    )
    args = ap.parse_args()

    input_pdf = Path(args.input)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    if not input_pdf.exists():
        raise FileNotFoundError(f"Input not found: {input_pdf}")

    use_digital = args.mode == "digital"
    use_ocr = args.mode == "ocr"

    if args.mode == "auto":
        use_digital = has_text_layer(input_pdf, min_chars=args.text_layer_min_chars)
        print(f"[auto] text layer detected: {use_digital}")

    if use_digital:
        try:
            pages, table_count = extract_with_pdfplumber(
                pdf_path=input_pdf,
                out_dir=out_dir,
                make_html=args.make_html,
            )
            print(f"[digital] pages={pages}, extracted_tables={table_count}")
            if args.mode == "auto" and table_count < args.digital_min_tables:
                print(
                    f"[auto] extracted tables ({table_count}) < digital_min_tables ({args.digital_min_tables}), fallback to OCR"
                )
                run_ocr_fallback(
                    input_pdf=input_pdf,
                    out_dir=out_dir,
                    server_url=args.server_url,
                    make_html=args.make_html,
                    dpi=args.dpi,
                    render_mode=args.render_mode,
                    temperature=args.temperature,
                    top_p=args.top_p,
                    repetition_penalty=args.repetition_penalty,
                )
            return
        except Exception as exc:
            if args.mode == "digital":
                raise
            print(f"[auto] digital path failed: {exc}")
            print("[auto] fallback to OCR path")

    if use_ocr or args.mode == "auto":
        run_ocr_fallback(
            input_pdf=input_pdf,
            out_dir=out_dir,
            server_url=args.server_url,
            make_html=args.make_html,
            dpi=args.dpi,
            render_mode=args.render_mode,
            temperature=args.temperature,
            top_p=args.top_p,
            repetition_penalty=args.repetition_penalty,
        )


if __name__ == "__main__":
    main()
