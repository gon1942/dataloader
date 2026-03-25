import argparse
from pathlib import Path
from paddleocr import PaddleOCRVL


def _slugify_image_name(name: str) -> str:
    return name.replace("\\", "/").split("/")[-1]


def _save_markdown_images(markdown_payload: dict, out_dir: Path, page_index: int) -> tuple[dict[str, str], Path | None]:
    markdown_images = markdown_payload.get("markdown_images")
    if not isinstance(markdown_images, dict) or not markdown_images:
        return {}, None

    image_dir = out_dir / "images" / f"page_{page_index:04d}"
    image_dir.mkdir(parents=True, exist_ok=True)

    saved_paths: dict[str, str] = {}
    for source_name, image_obj in markdown_images.items():
        if not hasattr(image_obj, "save"):
            continue
        target_name = _slugify_image_name(source_name)
        target_path = image_dir / target_name
        image_obj.save(target_path)
        saved_paths[source_name] = f"images/page_{page_index:04d}/{target_name}"

    if not saved_paths:
        return {}, None
    return saved_paths, image_dir


def _write_markdown_with_images(markdown_payload: dict, saved_paths: dict[str, str], out_dir: Path, page_index: int) -> None:
    markdown_text = markdown_payload.get("markdown_texts")
    if not isinstance(markdown_text, str) or not markdown_text.strip():
        return

    lines = [markdown_text.rstrip(), ""]
    for source_name, relative_path in saved_paths.items():
        alt_text = _slugify_image_name(source_name)
        lines.append(f"![{alt_text}]({relative_path})")
        lines.append("")

    target_path = out_dir / "md_with_images" / f"input_page_{page_index:04d}.md"
    target_path.parent.mkdir(parents=True, exist_ok=True)
    target_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="PDF path")
    ap.add_argument("--out_dir", required=True, help="Output directory")
    ap.add_argument("--server_url", required=True, help="vLLM server url, e.g. http://127.0.0.1:8080/v1")
    args = ap.parse_args()

    in_path = Path(args.input)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    if not in_path.exists():
        raise FileNotFoundError(f"Input not found: {in_path}")

    # vLLM 서버를 rec 백엔드로 사용 (공식 예시 방식)
    pipeline = PaddleOCRVL(
        vl_rec_backend="vllm-server",
        vl_rec_server_url=args.server_url,
    )

    # PDF를 그대로 넣으면 페이지 단위로 처리됨 (doc_parser 파이프라인)
    outputs = pipeline.predict(str(in_path))

    # 결과 저장: JSON / Markdown
    # (공식 예시: save_to_json / save_to_markdown)
    for i, res in enumerate(outputs):
        print(f"=== Page {i} ===")
        res.print()
        res.save_to_json(save_path=str(out_dir / "json"))
        res.save_to_markdown(save_path=str(out_dir / "md"))

        markdown_payload = getattr(res, "_to_markdown", lambda **_: None)(pretty=True, show_formula_number=False)
        if isinstance(markdown_payload, dict):
            saved_paths, _ = _save_markdown_images(markdown_payload, out_dir, i + 1)
            _write_markdown_with_images(markdown_payload, saved_paths, out_dir, i + 1)

    print(f"\nDone. Outputs in: {out_dir}")

if __name__ == "__main__":
    main()
