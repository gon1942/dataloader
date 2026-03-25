
## java 수정후 배포.
./scripts/build-all.sh 0.0.0


## hybrid 
./paddle/start_stack.sh
./paddle/stop_stack.sh
GPU_DEVICE=1 ./paddle/start_stack.sh




----



  ## hybrid 기본(docling-fast + EasyOCR)
  ```
  opendataloader-pdf \
  --hybrid docling-fast \
  --format json,html,markdown,markdown-with-images \
  -o tmp/odl-hybrid-base/input1_paddle_formats \
  testfile/input7.pdf
  ```

  ## ling-fast  + paddlevl
  ```
  opendataloader-pdf \
  --hybrid docling-fast \
  --hybrid-url http://127.0.0.1:8090 \
  --format json,html,markdown,markdown-with-images \
  -o tmp/odl-hybrid-paddle/input7 \
  testfile/input7.pdf
  ```

  ## none hybrid
  opendataloader-pdf -f markdown,json,html,pdf --reading-order xycut  -o ./tmp/odl-xycut/input7  samples/pdf/input7.pdf 



----







## hybrid 기본(docling-fast + EasyOCR)
```
PDF
-> hybrid backend (docling-fast + EasyOCR)
-> OCR/레이아웃/표 구조화 수행
-> json_content 반환
-> OpenDataLoader
-> json_content를 읽어서 내부 객체로 변환
-> json/md/html 생성

```

## docling-fast  + paddlevl
```
PDF
-> Paddle adapter
-> PaddleOCRVL 실행
-> Paddle 결과(raw markdown/json) 획득
-> adapter가 OpenDataLoader용 json_content로 변환
-> OpenDataLoader
-> 그 json_content를 다시 json/md/html로 출력
```










도커 자체 테스트
### 1 
docker exec -it odl_paddle_adapter /bin/bash -lc '
cd /app &&
python - << "PY"
from pathlib import Path
from paddleocr import PaddleOCRVL

def slugify_image_name(name: str) -> str:
    return name.replace("\\\\", "/").split("/")[-1]

def save_markdown_images(markdown_payload: dict, out_dir: Path, page_index: int):
    markdown_images = markdown_payload.get("markdown_images")
    if not isinstance(markdown_images, dict) or not markdown_images:
        return {}

    image_dir = out_dir / "images" / f"page_{page_index:04d}"
    image_dir.mkdir(parents=True, exist_ok=True)

    saved_paths = {}
    for source_name, image_obj in markdown_images.items():
        if not hasattr(image_obj, "save"):
            continue
        target_name = slugify_image_name(source_name)
        target_path = image_dir / target_name
        image_obj.save(target_path)
        saved_paths[source_name] = f"images/page_{page_index:04d}/{target_name}"
    return saved_paths

def write_markdown_with_images(markdown_payload: dict, saved_paths: dict, out_dir: Path, page_index: int):
    markdown_text = markdown_payload.get("markdown_texts")
    if not isinstance(markdown_text, str) or not markdown_text.strip():
        return

    lines = [markdown_text.rstrip(), ""]
    for source_name, relative_path in saved_paths.items():
        alt_text = slugify_image_name(source_name)
        lines.append(f"![{alt_text}]({relative_path})")
        lines.append("")

    target_path = out_dir / "md_with_images" / f"input_page_{page_index:04d}.md"
    target_path.parent.mkdir(parents=True, exist_ok=True)
    target_path.write_text("\\n".join(lines).rstrip() + "\\n", encoding="utf-8")

out_dir = Path("/tmp/paddle_only_input7")
out_dir.mkdir(parents=True, exist_ok=True)

pipeline = PaddleOCRVL(
    vl_rec_backend="vllm-server",
    vl_rec_server_url="http://127.0.0.1:8080/v1",
)

outputs = pipeline.predict("/tmp/input7.pdf")

for i, res in enumerate(outputs):
    res.save_to_json(save_path=str(out_dir / "json"))
    res.save_to_markdown(save_path=str(out_dir / "md"))
    markdown_payload = res._to_markdown(pretty=True, show_formula_number=False)
    if isinstance(markdown_payload, dict):
        saved_paths = save_markdown_images(markdown_payload, out_dir, i + 1)
        write_markdown_with_images(markdown_payload, saved_paths, out_dir, i + 1)

print(out_dir)
PY
'

### 2. 
docker cp odl_paddle_adapter:/tmp/paddle_only_input7 /home/gon/work/ttt3/opendataloader-pdf/tmp/paddle_only_input7



