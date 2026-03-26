
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
