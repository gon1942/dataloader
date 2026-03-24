1) Java JAR = 실제 추출 엔진

java/opendataloader-pdf-cli/...jar는 PDF를 읽고,

읽기 순서 정리
표 추출
heading/paragraph/list 구조화
markdown/json/html 생성

--reading-order <arg>    off, xycut.  arg를 변경 테스트 > 결과물 차이가 없다.


mkdir -p /home/gon/work/ttt3/opendataloader-pdf/tmp/odl-test/input9-order-debug && java -jar /home/gon/work/ttt3/opendataloader-pdf/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-0.0.0.jar -f json,markdown -o /home/gon/work/ttt3/opendataloader-pdf/tmp/odl-test/input9-order-debug --reading-order xycut /home/gon/work/ttt3/opendataloader-pdf/samples/pdf/input9.pdf > /home/gon/work/ttt3/opendataloader-pdf/tmp/odl-test/input9-order-debug/stdout.log 2> /home/gon/work/ttt3/opendataloader-pdf/tmp/odl-test/input9-order-debug/run.log







개발용과 운영용 스크립트를 추가했습니다.

개발 테스트용은 scripts/dev-package-install-test.sh 입니다. 현재 워크스페이스 기준으로 자바와 Python을 다시 빌드하고, wheel/JAR를 tmp/releases/<version>에 스테이징한 뒤, 그 wheel을 재설치하고 샘플 PDF로 스모크 테스트까지 수행합니다.

./scripts/dev-package-install-test.sh
./scripts/dev-package-install-test.sh 0.0.1-dev
SAMPLE_PDF=/home/gon/work/ttt3/opendataloader-pdf/samples/pdf/input8.pdf ./scripts/dev-package-install-test.sh 0.0.1-dev
운영 설치용은 scripts/install-production.sh 입니다. 이미 빌드된 wheel 또는 release 디렉터리를 받아서 소스 재빌드 없이 설치합니다. 필요하면 설치 직후 smoke test도 돌릴 수 있습니다.

./scripts/install-production.sh ./tmp/releases/0.0.1-dev
SMOKE_PDF=/home/gon/work/ttt3/opendataloader-pdf/samples/pdf/input8.pdf ./scripts/install-production.sh ./tmp/releases/0.0.1-dev
./scripts/install-production.sh ./tmp/releases/0.0.1-dev/opendataloader_pdf-0.0.1-dev-py3-none-any.whl
검증은 bash -n으로 두 스크립트 문법 확인까지 끝냈습니다.
주의할 점은 이 두 스크립트가 현재 기준으로 Python 설치 흐름을 중심으로 잡고 있다는 점입니다. 지금 문제도 "설치 후 수정된 자바가 반영되지 않음"이어서, 자바 JAR이 번들되는 Python wheel 재설치 경로를 가장 안전한 기본값으로 잡았습니다. 원하시면 다음 단계로 Node 배포/설치까지 같은 방식으로 확장해드리겠습니다.