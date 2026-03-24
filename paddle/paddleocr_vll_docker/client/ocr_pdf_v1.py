import argparse
from pathlib import Path
from paddleocr import PaddleOCRVL

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

    print(f"\nDone. Outputs in: {out_dir}")

if __name__ == "__main__":
    main()