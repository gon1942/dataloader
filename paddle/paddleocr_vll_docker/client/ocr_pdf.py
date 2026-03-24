import argparse
import re
import subprocess
import tempfile
from pathlib import Path
from paddleocr import PaddleOCRVL

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

TABLE_RE = re.compile(r"(?is)<table\b.*?</table>")


def md_to_html(md_path: Path, html_path: Path) -> None:
    md_text = md_path.read_text(encoding="utf-8", errors="replace")

    # md에 table HTML이 포함된 케이스(현재 당신 결과 패턴)면 그대로 렌더링
    if "<table" in md_text:
        body = md_text
    else:
        # table이 없으면 텍스트라도 볼 수 있게 pre로 래핑
        body = f"<pre>{md_text}</pre>"

    html = HTML_TEMPLATE.format(
        title=md_path.name,
        src=str(md_path),
        body=body
    )
    html_path.write_text(html, encoding="utf-8")


def _to_int(val: str | None, default: int = 1) -> int:
    try:
        n = int(str(val))
        return n if n > 0 else default
    except (TypeError, ValueError):
        return default


def _norm_text_from_cell(tag) -> str:
    text = tag.get_text(" ", strip=True)
    return " ".join(text.split())


def refine_table_html(
    table_html: str,
    head_rows: int = 4,
    left_cols: int = 3,
    min_run: int = 1,
) -> str:
    from bs4 import BeautifulSoup

    soup = BeautifulSoup(table_html, "html.parser")
    table = soup.find("table")
    if table is None:
        return table_html

    tr_tags = table.find_all("tr")
    if not tr_tags:
        return table_html

    cells = []
    grid: list[list[int | None]] = [[] for _ in tr_tags]
    max_cols = 0

    for r, tr in enumerate(tr_tags):
        c = 0
        td_tags = tr.find_all(["td", "th"], recursive=False)
        for td in td_tags:
            while c < len(grid[r]) and grid[r][c] is not None:
                c += 1

            rowspan = _to_int(td.get("rowspan"), 1)
            colspan = _to_int(td.get("colspan"), 1)
            cell = {
                "tag": td,
                "row": r,
                "col": c,
                "rowspan": rowspan,
                "colspan": colspan,
                "text": _norm_text_from_cell(td),
            }
            cells.append(cell)
            cid = len(cells) - 1

            for rr in range(r, min(r + rowspan, len(tr_tags))):
                need = c + colspan - len(grid[rr])
                if need > 0:
                    grid[rr].extend([None] * need)
                for cc in range(c, c + colspan):
                    if grid[rr][cc] is None:
                        grid[rr][cc] = cid
            c += colspan
            if c > max_cols:
                max_cols = c

    for r in range(len(grid)):
        need = max_cols - len(grid[r])
        if need > 0:
            grid[r].extend([None] * need)

    if max_cols == 0:
        return str(table)

    removed: set[int] = set()
    row_count = len(grid)
    col_count = max_cols

    # 1) 좌측 라벨 영역에서 세로 병합 복원
    target_cols = min(left_cols, col_count)
    for c in range(target_cols):
        r = 0
        while r < row_count:
            cid = grid[r][c]
            if cid is None or cid in removed:
                r += 1
                continue

            cell = cells[cid]
            if cell["row"] != r or cell["col"] != c:
                r += 1
                continue
            if cell["rowspan"] != 1 or cell["colspan"] != 1:
                r += 1
                continue
            if not cell["text"]:
                r += 1
                continue

            run: list[int] = []
            rr = r + 1
            while rr < row_count:
                nid = grid[rr][c]
                if nid is None or nid in removed:
                    break
                ncell = cells[nid]
                if ncell["row"] != rr or ncell["col"] != c:
                    break
                if ncell["rowspan"] != 1 or ncell["colspan"] != 1:
                    break
                if ncell["text"]:
                    break
                run.append(nid)
                rr += 1

            if len(run) >= min_run:
                new_rowspan = 1 + len(run)
                cell["rowspan"] = new_rowspan
                cell["tag"]["rowspan"] = str(new_rowspan)
                for nid in run:
                    removed.add(nid)
                    cells[nid]["tag"].decompose()
                r = rr
            else:
                r += 1

    # 2) 상단 헤더 영역에서 가로 병합 복원
    target_rows = min(head_rows, row_count)
    for r in range(target_rows):
        c = 0
        while c < col_count:
            cid = grid[r][c]
            if cid is None or cid in removed:
                c += 1
                continue

            cell = cells[cid]
            if cell["row"] != r or cell["col"] != c:
                c += 1
                continue
            if cell["rowspan"] != 1 or cell["colspan"] != 1:
                c += 1
                continue
            if not cell["text"]:
                c += 1
                continue

            run: list[int] = []
            cc = c + 1
            while cc < col_count:
                nid = grid[r][cc]
                if nid is None or nid in removed:
                    break
                ncell = cells[nid]
                if ncell["row"] != r or ncell["col"] != cc:
                    break
                if ncell["rowspan"] != 1 or ncell["colspan"] != 1:
                    break
                if ncell["text"]:
                    break
                run.append(nid)
                cc += 1

            if len(run) >= min_run:
                new_colspan = 1 + len(run)
                cell["colspan"] = new_colspan
                cell["tag"]["colspan"] = str(new_colspan)
                for nid in run:
                    removed.add(nid)
                    cells[nid]["tag"].decompose()
                c = cc
            else:
                c += 1

    return str(table)


def refine_tables_in_md(md_path: Path, head_rows: int, left_cols: int, min_run: int) -> bool:
    md_text = md_path.read_text(encoding="utf-8", errors="replace")

    def _replace(match: re.Match) -> str:
        table_html = match.group(0)
        return refine_table_html(
            table_html=table_html,
            head_rows=head_rows,
            left_cols=left_cols,
            min_run=min_run,
        )

    refined = TABLE_RE.sub(_replace, md_text)
    if refined == md_text:
        return False
    md_path.write_text(refined, encoding="utf-8")
    return True


def render_pdf_to_images(
    pdf_path: Path,
    out_dir: Path,
    dpi: int,
    image_format: str,
    render_mode: str,
) -> list[Path]:
    if dpi <= 0:
        raise ValueError(f"--dpi must be > 0, got {dpi}")

    prefix = out_dir / "page"
    cmd = [
        "pdftoppm",
        "-r",
        str(dpi),
    ]
    if render_mode == "gray":
        cmd.append("-gray")
    elif render_mode == "mono":
        cmd.append("-mono")
    cmd += [
        f"-{image_format}",
        str(pdf_path),
        str(prefix),
    ]
    subprocess.run(cmd, check=True)

    ext = ".png" if image_format == "png" else ".jpg"
    image_files = sorted(out_dir.glob(f"page-*{ext}"))
    if not image_files:
        raise RuntimeError(f"No rendered images found in {out_dir}")
    return image_files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="PDF path")
    ap.add_argument("--out_dir", required=True, help="Output directory")
    ap.add_argument("--server_url", required=True, help="vLLM server url, e.g. http://127.0.0.1:8080/v1")
    ap.add_argument("--make_html", action="store_true", help="Also write HTML files next to markdown outputs")
    ap.add_argument(
        "--dpi",
        type=int,
        default=0,
        help="If > 0 and input is PDF, render PDF pages to images with this DPI before OCR (e.g. 300)",
    )
    ap.add_argument(
        "--image_format",
        choices=["png", "jpeg"],
        default="png",
        help="Rendered image format when --dpi is used",
    )
    ap.add_argument(
        "--render_mode",
        choices=["color", "gray", "mono"],
        default="gray",
        help="PDF rendering mode when --dpi is used",
    )
    ap.add_argument(
        "--temperature",
        type=float,
        default=0.0,
        help="Decoding temperature for OCR-VLM predict (0.0 is most deterministic)",
    )
    ap.add_argument(
        "--top_p",
        type=float,
        default=1.0,
        help="Top-p for OCR-VLM predict",
    )
    ap.add_argument(
        "--repetition_penalty",
        type=float,
        default=1.0,
        help="Repetition penalty for OCR-VLM predict",
    )
    ap.add_argument(
        "--min_pixels",
        type=int,
        default=None,
        help="Minimum pixels constraint passed to OCR-VLM predict",
    )
    ap.add_argument(
        "--max_pixels",
        type=int,
        default=None,
        help="Maximum pixels constraint passed to OCR-VLM predict",
    )
    ap.add_argument(
        "--refine_table_spans",
        action="store_true",
        help="Heuristically refine rowspan/colspan in markdown tables after OCR",
    )
    ap.add_argument(
        "--span_head_rows",
        type=int,
        default=4,
        help="Top N rows treated as header area for horizontal span refinement",
    )
    ap.add_argument(
        "--span_left_cols",
        type=int,
        default=3,
        help="Left N columns treated as label area for vertical span refinement",
    )
    ap.add_argument(
        "--span_min_run",
        type=int,
        default=1,
        help="Minimum consecutive empty-cell run to merge",
    )
    args = ap.parse_args()

    in_path = Path(args.input)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    if not in_path.exists():
        raise FileNotFoundError(f"Input not found: {in_path}")

    pipeline = PaddleOCRVL(
        vl_rec_backend="vllm-server",
        vl_rec_server_url=args.server_url,
    )

    md_dir = out_dir / "md"
    json_dir = out_dir / "json"
    md_dir.mkdir(parents=True, exist_ok=True)
    json_dir.mkdir(parents=True, exist_ok=True)

    predict_kwargs = {
        "temperature": args.temperature,
        "top_p": args.top_p,
        "repetition_penalty": args.repetition_penalty,
        "min_pixels": args.min_pixels,
        "max_pixels": args.max_pixels,
    }

    if in_path.suffix.lower() == ".pdf" and args.dpi > 0:
        with tempfile.TemporaryDirectory(prefix="ocr_pages_", dir=str(out_dir)) as tmp_dir:
            page_images = render_pdf_to_images(
                pdf_path=in_path,
                out_dir=Path(tmp_dir),
                dpi=args.dpi,
                image_format=args.image_format,
                render_mode=args.render_mode,
            )
            print(f"Rendered {len(page_images)} pages at {args.dpi} DPI")

            for i, page_path in enumerate(page_images):
                outputs = pipeline.predict(str(page_path), **predict_kwargs)
                for res in outputs:
                    print(f"=== Page {i} ({page_path.name}) ===")
                    res.print()
                    res.save_to_json(save_path=str(json_dir))
                    res.save_to_markdown(save_path=str(md_dir))
    else:
        outputs = pipeline.predict(str(in_path), **predict_kwargs)

        for i, res in enumerate(outputs):
            print(f"=== Page {i} ===")
            res.print()
            res.save_to_json(save_path=str(json_dir))
            res.save_to_markdown(save_path=str(md_dir))

    md_files = sorted(md_dir.rglob("*.md"))
    if args.refine_table_spans:
        try:
            import bs4  # noqa: F401
        except Exception as exc:
            raise RuntimeError(
                "Missing dependency for --refine_table_spans. Install beautifulsoup4."
            ) from exc

        changed = 0
        for md_path in md_files:
            if refine_tables_in_md(
                md_path=md_path,
                head_rows=max(args.span_head_rows, 0),
                left_cols=max(args.span_left_cols, 0),
                min_run=max(args.span_min_run, 1),
            ):
                changed += 1
        print(f"Refined table spans in {changed} markdown files")

    if args.make_html:
        for md_path in md_files:
            html_path = md_path.with_suffix(".html")
            md_to_html(md_path, html_path)

    print(f"\nDone. Outputs in: {out_dir}")
    if args.make_html:
        print(f"HTML outputs in: {md_dir} (*.html)")

if __name__ == "__main__":
    main()
