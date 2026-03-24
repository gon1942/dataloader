#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import argparse
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
    color: rgba(0,0,0,.6);
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
<div class="hint">소스: {src}</div>
{body}
</body>
</html>
"""

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--md", default="data/out/md/input_0.md", help="input markdown path")
    ap.add_argument("--out", default="", help="output html path (default: same name with .html)")
    args = ap.parse_args()

    md_path = Path(args.md)
    if not md_path.exists():
        raise SystemExit(f"Markdown not found: {md_path}")

    out_path = Path(args.out) if args.out else md_path.with_suffix(".html")

    md_text = md_path.read_text(encoding="utf-8", errors="replace")

    # 핵심: Markdown 전체를 HTML로 '완전 변환'하기보다,
    # 표(<table>...</table>)는 HTML 그대로 렌더링되므로 안전하게 감싸기만 합니다.
    # 나머지 일반 텍스트는 <pre>로 표시하면 깨지지 않습니다.
    if "<table" in md_text:
        body = md_text
        # 단, md에 markdown 문법(### 등)이 섞이면 그대로 출력되므로
        # table 확인 목적이면 전체를 그냥 렌더링하고,
        # 텍스트가 거슬리면 아래처럼 pre로 감싸는 방식으로 바꾸셔도 됩니다.
    else:
        body = f"<pre>{md_text}</pre>"

    html = HTML_TEMPLATE.format(
        title=md_path.name,
        src=str(md_path),
        body=body
    )

    out_path.write_text(html, encoding="utf-8")
    print(f"OK: wrote {out_path}")

if __name__ == "__main__":
    main()