#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import argparse
import json
from pathlib import Path
import html as htmlmod

def load_blocks_from_json(fp: Path):
    d = json.loads(fp.read_text(encoding="utf-8"))

    # PaddleOCRVL 결과 포맷이 환경/버전에 따라 달라서 복수 케이스 대응
    blocks = None
    if isinstance(d, dict):
        if "result" in d and isinstance(d["result"], dict) and "blocks" in d["result"]:
            blocks = d["result"]["blocks"]
        elif "blocks" in d:
            blocks = d["blocks"]

    if not isinstance(blocks, list):
        return []
    out = []
    for b in blocks:
        if not isinstance(b, dict):
            continue
        out.append({
            "label": b.get("block_label", ""),
            "content": b.get("block_content", ""),
            "bbox": b.get("block_bbox", None),
        })
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pdf", default="data/input.pdf", help="PDF path (relative or absolute)")
    ap.add_argument("--json_dir", default="data/out/json", help="Directory containing page JSON files")
    ap.add_argument("--out_html", default="data/out/viewer_split.html", help="Output HTML path")
    args = ap.parse_args()

    pdf_path = Path(args.pdf)
    json_dir = Path(args.json_dir)
    out_html = Path(args.out_html)
    out_html.parent.mkdir(parents=True, exist_ok=True)

    if not pdf_path.exists():
        raise SystemExit(f"PDF not found: {pdf_path}")
    if not json_dir.exists():
        raise SystemExit(f"json_dir not found: {json_dir}")

    json_files = sorted(json_dir.rglob("*.json"))
    if not json_files:
        raise SystemExit(f"No json files found under: {json_dir}")

    # 페이지별 blocks를 수집
    pages = []
    for fp in json_files:
        blocks = load_blocks_from_json(fp)
        pages.append({
            "name": fp.stem,
            "file": str(fp),
            "blocks": blocks,
        })

    # HTML에서 PDF를 상대경로로 열기 좋게: out_html 기준 상대경로 계산
    try:
        pdf_rel = pdf_path.resolve().relative_to(out_html.parent.resolve())
        pdf_src = str(pdf_rel)
    except Exception:
        # 상대경로 계산이 안 되면 절대경로 사용(file://)
        pdf_src = "file://" + str(pdf_path.resolve())

    # HTML 생성 (왼쪽 PDF, 오른쪽 추출 결과)
    data_js = json.dumps({"pdf_src": pdf_src, "pages": pages}, ensure_ascii=False)

    html_doc = f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Split Viewer (PDF + OCR)</title>
<style>
  :root {{
    --border: #ddd;
    --bg: #ffffff;
    --muted: rgba(0,0,0,.65);
    --mono: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  }}
  body {{
    margin: 0;
    font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif;
    background: var(--bg);
  }}
  .topbar {{
    display: flex;
    gap: 8px;
    align-items: center;
    padding: 10px 12px;
    border-bottom: 1px solid var(--border);
    position: sticky;
    top: 0;
    background: var(--bg);
    z-index: 10;
  }}
  .container {{
    display: grid;
    grid-template-columns: 1fr 1fr;
    height: calc(100vh - 52px);
  }}
  .pane {{
    overflow: auto;
  }}
  .pane.left {{
    border-right: 1px solid var(--border);
  }}
  iframe {{
    width: 100%;
    height: 100%;
    border: 0;
  }}
  .right-inner {{
    padding: 12px;
  }}
  .meta {{
    color: var(--muted);
    font-size: 12px;
    margin: 6px 0;
  }}
  .block {{
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px;
    margin: 10px 0;
  }}
  .block .label {{
    font-size: 12px;
    color: var(--muted);
    margin-bottom: 8px;
  }}
  pre {{
    font-family: var(--mono);
    white-space: pre-wrap;
    word-break: break-word;
    background: #f7f7f7;
    padding: 10px;
    border-radius: 10px;
    margin: 0;
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
  .controls {{
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }}
  select, input {{
    padding: 6px 8px;
    border: 1px solid var(--border);
    border-radius: 8px;
    font-size: 14px;
  }}
  .hint {{
    color: var(--muted);
    font-size: 12px;
  }}
</style>
</head>
<body>
<div class="topbar">
  <div class="controls">
    <strong>Split Viewer</strong>
    <span class="hint">왼쪽: PDF / 오른쪽: OCR 추출 결과</span>
    <label class="hint">페이지</label>
    <select id="pageSelect"></select>
    <label class="hint">필터</label>
    <input id="filterInput" type="text" placeholder="예: vision_table / footnote / title ..." />
  </div>
</div>

<div class="container">
  <div class="pane left">
    <iframe id="pdfFrame" title="PDF"></iframe>
  </div>
  <div class="pane right">
    <div class="right-inner">
      <div class="meta" id="pageMeta"></div>
      <div id="blocks"></div>
    </div>
  </div>
</div>

<script>
  const DATA = {data_js};

  const pdfFrame = document.getElementById("pdfFrame");
  const pageSelect = document.getElementById("pageSelect");
  const filterInput = document.getElementById("filterInput");
  const blocksEl = document.getElementById("blocks");
  const pageMeta = document.getElementById("pageMeta");

  function isHtmlTable(s) {{
    return typeof s === "string" && (s.includes("<table") || s.includes("<tr") || s.includes("<td"));
  }}

  function renderPage(idx) {{
    const p = DATA.pages[idx];
    pageMeta.textContent = `페이지: ${idx} / 파일: ${p.file} / blocks: ${p.blocks.length}`;

    const filter = (filterInput.value || "").trim().toLowerCase();
    blocksEl.innerHTML = "";

    for (const b of p.blocks) {{
      const label = (b.label || "");
      if (filter && !label.toLowerCase().includes(filter)) continue;

      const wrap = document.createElement("div");
      wrap.className = "block";

      const lab = document.createElement("div");
      lab.className = "label";
      lab.textContent = `label: ${label}` + (b.bbox ? ` | bbox: ${JSON.stringify(b.bbox)}` : "");
      wrap.appendChild(lab);

      const content = b.content ?? "";
      if (isHtmlTable(content)) {{
        const box = document.createElement("div");
        box.innerHTML = content; // table HTML 그대로 렌더
        wrap.appendChild(box);
      }} else {{
        const pre = document.createElement("pre");
        pre.textContent = String(content);
        wrap.appendChild(pre);
      }}

      blocksEl.appendChild(wrap);
    }}
  }}

  function init() {{
    // PDF 로드: (대부분 브라우저는 #page=1 같은 파라미터를 지원)
    pdfFrame.src = DATA.pdf_src;

    // 페이지 선택 옵션
    DATA.pages.forEach((p, i) => {{
      const opt = document.createElement("option");
      opt.value = String(i);
      opt.textContent = `${i}: ${p.name}`;
      pageSelect.appendChild(opt);
    }});

    pageSelect.addEventListener("change", () => {{
      const idx = Number(pageSelect.value);
      renderPage(idx);
    }});

    filterInput.addEventListener("input", () => {{
      const idx = Number(pageSelect.value || 0);
      renderPage(idx);
    }});

    // 초기 렌더
    pageSelect.value = "0";
    renderPage(0);
  }}

  init();
</script>
</body>
</html>
"""
    out_html.write_text(html_doc, encoding="utf-8")
    print(f"OK: wrote {out_html}")

if __name__ == "__main__":
    main()