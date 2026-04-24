
현재 hybrid 동작
현재 hybrid의 이미지 설명은 Python 서버에서 Docling 옵션으로 켜집니다.
 hybrid_server.py (line 192) 에서 do_picture_description, generate_picture_images, picture_description_options를 설정하고,
  Docling 응답의 pictures[].annotations[].kind == "description" 값을 Java에서 DoclingSchemaTransformer.java (line 376) 가 읽어 SemanticPicture로 만듭니다. 
  그 다음 출력기는 그냥 그 필드를 소비합니다: 
  JSON은 PictureSerializer.java (line 49), Markdown는 MarkdownGenerator.java (line 167), 
  HTML은 HtmlGenerator.java (line 240) 입니다. 
  
  반대로 비hybrid는 보통 ImageChunk만 남고, 이 경우 ImageSerializer.java (line 38) 는 description을 쓸 수 없습니다.

비hybrid에 넣는 권장 방식
권장 위치는 DocumentProcessor.java (line 73) 의 로컬 파이프라인 끝, generateOutputs(...) 직전입니다. 
여기서 contents를 순회하며 ImageChunk를 찾고, 각 이미지 bbox를 crop해서 VLM에 보내고, 결과를 new SemanticPicture(bbox, imageIndex, description) 로 바꿔치기하면 됩니다.
 이 방식의 장점은 출력기 수정이 거의 필요 없다는 점입니다. ImagesUtils가 이미 SemanticPicture도 파일로 저장할 수 있고 ImagesUtils.java (line 65), Markdown/JSON/HTML도 그대로 동작합니다.

구조는 이렇게 두는 게 좋습니다.

Config/CLI에 로컬용 옵션 추가
ImageDescriptionProcessor 추가: List<List<IObject>>를 받아 ImageChunk를 SemanticPicture로 변환
ImageDescriptionClient 인터페이스 추가
OllamaCompatibleImageDescriptionClient 구현 추가: https://api.hamonize.com/ollama/api/chat 호출
crop 유틸 추가: bbox -> image bytes/base64
옵션 설계
이름은 hybrid 서버 옵션과 혼동을 피하는 게 좋습니다. 추천은 아래입니다.

--image-description
--image-description-url
--image-description-model
--image-description-prompt
--image-description-timeout
--image-description-max-images 또는 --image-description-min-area
--enrich-picture-description를 로컬 CLI에도 재사용할 수도 있지만, 지금 저장소에서 그 이름은 hybrid 서버 의미가 강합니다. 사용자 혼동을 줄이려면 로컬은 --image-description 계열이 더 안전합니다.

엔진 연동 방식
제공한 엔드포인트는 Ollama 호환 /api/chat처럼 보입니다. 다만 지금 예시는 텍스트만 보내는 예시라서 “이미지를 어떤 필드로 넣는지”는 추가 확인이 필요합니다.
 보통 Ollama 계열은 messages[].images에 base64 배열을 넣거나, 다른 스펙은 content 배열 파트 방식을 씁니다. 이 부분은 실제 airun-vision:latest가 요구하는 이미지 payload 형식을 먼저 확정해야 합니다. 이게 구현의 유일한 큰 불확실성입니다.

주의할 점

지금 HTML 출력은 figcaption에는 설명을 쓰지만 alt 속성은 단순 "figureN" 입니다. 접근성을 목표로 하면 HtmlGenerator.java (line 255) 도 설명 우선으로 바꾸는 게 맞습니다.
이미지 설명은 텍스트보다 비용이 큽니다. 너무 작은 아이콘, 장식 이미지, 배경 이미지는 제외해야 합니다. 기존 CaptionProcessor의 subtle image 기준 CaptionProcessor.java (line 100) 을 재활용할 수 있습니다.
sanitize와의 순서를 신경써야 합니다. 설명문에 민감정보가 들어갈 수 있으므로, 생성된 description도 후처리 대상에 포함되게 해야 합니다.

CLI 옵션을 Java에 추가하면 CLAUDE.md (line 5) 대로 npm run sync가 필요합니다.

가장 현실적인 구현 순서는 이렇습니다.

로컬 모드에서 ImageChunk 수집 후 bbox crop 가능하게 유틸 분리
OllamaCompatibleImageDescriptionClient로 1장 설명 생성
ImageChunk -> SemanticPicture 변환 processor 추가
JSON/Markdown 검증
HTML alt 개선
작은 이미지 제외, timeout, 실패시 fallback 추가

원하면 다음 단계로 바로 넘어가서, 이 설계대로 들어갈 클래스/옵션/호출 지점까지 구체적인 변경안 형태로 정리해드리겠습니다.


## 구현 -----------

비hybrid 모드용 이미지 설명 기능을 구현했습니다.

주요 변경은 ImageDescriptionProcessor.java (line 52) 에서 로컬 파이프라인의 ImageChunk를 순회하며 PDF crop 이미지를 base64로 인코딩하고, OllamaImageDescriptionClient.java (line 49) 로 https://api.hamonize.com/ollama/api/chat에 messages[].images 형태로 전송해 설명을 받아 SemanticPicture로 승격하도록 넣었습니다. 이 단계는 DocumentProcessor.java (line 90) 에서 비hybrid 경로에만 연결했고, 설명 텍스트는 sanitizer 전에 생성되도록 배치했습니다.

CLI/설정도 추가했습니다. 새 옵션은 CLIOptions.java (line 105) 에 있고, 기본값은 Config.java (line 86) 에 반영했습니다.

--image-description
--image-description-url
--image-description-model
--image-description-prompt
--image-description-timeout
출력 쪽도 SemanticPicture.description을 alt/caption에 더 잘 쓰도록 조정했습니다. 옵션 생성물과 문서도 재생성해서 options.json, Python/Node generated 파일, CLI 옵션 문서까지 반영됐습니다.

검증은 ./java/mvnw -f java/pom.xml -DskipTests package 성공으로 확인했습니다. npm run sync 자체는 현재 환경의 테스트/X11 제약 때문에 실패해서, 대신 패키징 후 옵션/스키마 생성만 별도로 완료했습니다. 실제 API 연동 E2E는 네트워크 제약 때문에 여기서는 못 돌렸습니다.

사용 예시는 이렇게 하면 됩니다.

opendataloader-pdf input.pdf -o out -f json,markdown --image-description
기본 엔진은 airun-vision:latest, 기본 URL은 https://api.hamonize.com/ollama/api/chat 입니다.





./scripts/build-all.sh 0.0.0
opendataloader-pdf --help | rg 'image-description'



## command -----------

opendataloader-pdf -f markdown,json,html,pdf --image-description  --reading-order xycut  -o ./tmp/odl-xycut2/input1  testfile/input1.pdf 

opendataloader-pdf -f json,markdown,html --image-description -o ./tmp/odl-img/t1 testfile/ㅑㅞㅕㅅ1.pdf 

opendataloader-pdf testfile/img1.pdf -o ./tmp/odl-img/img1 -f json,markdown,html --image-description --image-description-language en




opendataloader-pdf -f pdf,json,markdown,html \
  --image-description \
  --image-description-url https://app.hamonize.com/gemma4/v1/chat/completions \
  --image-description-model hamonize:latest \
  /home/gon/Documents/계측_4p.pdf \
  -o ./tmp/odl-a1----12


opendataloader-pdf -f pdf,json,markdown,html \
  --image-description \
  --image-description-url https://app.hamonize.com/gemma4/api/chat \
  --image-description-model airun-vision:latest \
  '/home/gon/Documents/한수원테스트자료/001/rag참고자료/교재/1. 교육 과정소개 및 생활안내.pdf' \
  --image-description-model hamonize:latest \
  -o ./tmp/odl-a1----1




opendataloader-pdf -f pdf,json,markdown,html \
  --image-description \
  --image-description-url http://192.168.0.203:11435/api/chat \
  --image-description-model hamonize:latest \
  --image-description-timeout 120000 \
  /home/gon/Documents/계측_4p.pdf \
  -o ./tmp/odl-a1----12




opendataloader-pdf -f pdf,json,markdown,html \
  --image-description \
  --image-description-url https://app.hamonize.com/gemma4/v1/chat/completions \
  --image-description-model hamonize-v2 \
  --image-description-timeout 120000 \
  '/home/gon/work/airun_proj/airun_rag_docs/admin/지진행동요령v1.pdf' \
  -o ./tmp/pdf-a1----123

 cp  python/opendataloader-pdf/dist/opendataloader_pdf-0.0.0-py3-none-any.whl  /home/gon/work/airun_proj/gemma2/airun/install_files/python-wheels   



opendataloader-pdf -f pdf,json,markdown,html \
  --image-description \
  --image-description-url https://app.hamonize.com/gemma4/v1/chat/completions \
  --image-description-model hamonize-v2 \
  --image-description-timeout 120000 \
  '/home/gon/Documents/0000/doc/호랑이서식지에대해서.doc' \
  -o ./tmp/doc-a1----123


mkdir -p ./tmp/office-pdf
soffice --headless --convert-to pdf --outdir ./tmp/office-pdf \
  '/home/gon/Documents/0000/doc/호랑이서식지에대해서.doc'

opendataloader-pdf -f pdf,json,markdown,html \
  --image-description \
  --image-description-url http://211.115.68.5:11407/v1/chat/completions \
  --image-description-model hamonize-v2 \
  --image-description-timeout 120000 \
  './tmp/office-pdf/호랑이서식지에대해서.pdf' \
  -o ./tmp/doc-a1----123








java -Djava.awt.headless=true -jar /home/gon/work/ttt4/dataloader/tmp/releases/1.1.0/opendataloader-pdf-cli-1.1.0.jar \
  -f pdf,json,markdown,html \
  --image-description \
  --image-description-url https://app.hamonize.com/gemma4/v1/chat/completions \
  --image-description-model hamonize-v2 \
  --image-description-timeout 120000 \
  '/home/gon/Documents/0000/hwp/어린이날.hwpx' \
  -o ./tmp/hwp-a1----1


java -Djava.awt.headless=true -jar /home/gon/work/ttt4/dataloader/tmp/releases/1.1.0/opendataloader-pdf-cli-1.1.0.jar \
  -f pdf,json,markdown,html \
  --image-description \
  --image-description-url https://app.hamonize.com/gemma4/v1/chat/completions \
  --image-description-model hamonize-v2 \
  --image-description-timeout 120000 \
  '/home/gon/Documents/0000/doc/호랑이서식지에대해서.doc' \
  -o ./tmp/doc-a1----1



## -----------


기본 사용법은 아래입니다.

opendataloader-pdf INPUT.pdf -o OUTPUT_DIR -f json,markdown,html
예:

opendataloader-pdf ./testfile/계측_4p.pdf -o ./tmp/odl/계측_4p.pdf -f json,markdown,html
자주 쓰는 옵션:

-o, --output-folder: 출력 폴더
-f, --format: 출력 포맷
json
markdown
html
text
쉼표로 여러 개 지정 가능
--image-description: 비hybrid 모드 이미지 설명 생성
--image-description-language ko|en: 설명 언어 지정
--pages 1,3,5 또는 --pages 1-3: 특정 페이지만 처리
--include-header-footer: 헤더/푸터 포함
--embed-images: md/html에 이미지 파일 대신 base64 포함
예시:

opendataloader-pdf sample.pdf -o out -f json
opendataloader-pdf sample.pdf -o out -f markdown,html --image-description
opendataloader-pdf sample.pdf -o out -f json --pages 1-3
도움말 확인:

opendataloader-pdf --help
현재 환경에서 이미지 설명까지 포함한 대표 예시는:

JAVA_TOOL_OPTIONS=-Djava.awt.headless=true \
opendataloader-pdf ./testfile/계측_4p.pdf \
  -o ./tmp/odl/계측_4p.pdf \
  -f json,markdown,html \
  --image-description
원하면 내가 이 저장소 기준으로 use.md나 README에 실행 예시까지 정리해서 문서로 넣겠습니다.


## --------------------------------------------------------------------#
## ----------운영환경 설치 ----------------------
## --------------------------------------------------------------------#
source ~/.airun_venv/bin/activate
./scripts/build-all.sh 0.0.0


또는 빌드된 wheel을 운영 venv에 직접 설치
~/.airun_venv/bin/pip install /home/gon/work/ttt4/dataloader/python/opendataloader-pdf/dist/opendataloader_pdf-0.0.0-py3-none-any.whl
<!-- ~/.airun_venv/bin/pip show opendataloader-pdf -->



## 반영 문제.
해결 방법은 utils.py (line 6841)에서 JSON이 dict일 경우 kids를 재귀 순회해서 type과 page number를 집계하도록 바꾸는 것입니다.

원하면 내가 /home/gon/work/airun_proj/git/airun/utils.py를 바로 패치해서 이 로그가 실제 값으로 나오게 수정하겠습니다.


문서를 업로드하면 업로드한 경로에  업로드한 파일이 존재하고 문서 임베딩을 수행하면 .extracts/<업로드파일>이 생겨 이안에 이미지가 저장된다. 



 ls 계측_4p.pdf  계측_5p.pdf
ls .extracts/계측_4p
 ls .extracts/계측_4p                                   ✔  at 15:56:10  
3ba461fc_page_1_1.png  3ba461fc_page_1_3.jpg  3ba461fc_page_1_5.jpg
3ba461fc_page_1_2.png  3ba461fc_page_1_4.jpg



opendataloader-pdf를 파서로 사용하는 경우 동일하게 .extracts/문서/ md, json을 저장하도록 할 수 있나?















# ------------------------




java -Djava.awt.headless=true -jar /home/gon/work/opendataloader_proj/dataloader_airun/tmp/releases/1.1.0/opendataloader-pdf-cli-1.1.0.jar \
  -f pdf,json,markdown,html \
  --image-description \
  --image-description-url https://app.hamonize.com/gemma4/v1/chat/completions \
  --image-description-model hamonize-v2 \
  --image-description-timeout 120000 \
  '/home/gon/work/opendataloader_proj/testfile/img/a1.pdf' \
  -o ./tmp/img-1



java -Djava.awt.headless=true -jar /home/gon/work/opendataloader_proj/dataloader_airun/tmp/releases/1.1.0/opendataloader-pdf-cli-1.1.0.jar \
  -f pdf,json,markdown,html \
  --image-description \
  --image-description-url https://app.hamonize.com/gemma4/v1/chat/completions \
  --image-description-model hamonize-v2 \
  --image-description-timeout 120000 \
  './testfile/input/input4.pdf' \
  -o ./tmp/input-4