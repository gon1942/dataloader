## 10. AI 작업 지시사항

이 섹션은 AI 에이전트에게 바로 전달할 수 있는 작업 지시문입니다.

### 공통 지시

- 이 저장소 안에서만 작업할 것
- 기존 동작을 무조건 지우지 말고, 필요한 범위만 수정할 것
- 임시 우회 하드코딩은 넣지 말 것
- 특정 파일명, 특정 키워드, 특정 문서 내용에 의존하는 분기 처리는 금지
- `input1.pdf`는 검증용 샘플로만 사용할 것
- 목표는 일반적인 PDF 입력에 대해 동작 가능한 adapter 구조를 만드는 것


### 작업 범위

수정 가능 파일:

- `paddle/adapter/app.py`
- `paddle/adapter/config.py`
- `paddle/adapter/transform.py`
- `paddle/adapter/Dockerfile`
- `paddle/adapter/requirements.txt`
- `paddle/start_stack.sh`
- `paddle/run_opendataloader.sh`
- 필요 시 관련 문서 파일

가능하면 수정하지 말아야 할 파일:

- Java core 처리 로직
- 기존 `docling-fast` 구현
- `samples/pdf/` 아래 샘플 원본 파일

### 1차 작업 지시

목표:

- `paddle/adapter/app.py`를 단순 HTTP 프록시에서 실제 Paddle 실행기로 변경할 것

구체 지시:

1. `/v1/convert/file`로 들어온 PDF를 임시 파일로 저장할 것
2. adapter 내부에서 `PaddleOCRVL`을 import할 것
3. `PaddleOCRVL(vl_rec_backend="vllm-server", vl_rec_server_url=...)` 형태로 초기화할 것
4. `predict(pdf_path)`를 호출할 것
5. Paddle raw result를 `transform.py`로 넘길 것
6. 더 이상 `/v1/chat/completions`에 PDF 파일을 직접 POST하지 말 것

금지:

- PDF를 다시 `multipart/form-data`로 Paddle backend에 전달하는 방식 유지
- 특정 입력 파일명 기준 분기
- 테스트용 mock 응답을 실제 코드 경로에 남겨두는 것

완료 기준:

- `Unsupported Media Type` 에러가 제거될 것
- adapter가 Paddle raw result를 실제로 받을 것

### 2차 작업 지시

목표:

- adapter 컨테이너에서도 `PaddleOCRVL.predict()`가 동작하게 만들 것

구체 지시:

1. `paddle/adapter/Dockerfile`에 필요한 Python 패키지 설치를 반영할 것
2. `requirements.txt`를 실제 실행 기준으로 정리할 것
3. 컨테이너 안에서 `from paddleocr import PaddleOCRVL` import 가능해야 함
4. 필요 시 시스템 패키지를 추가할 것

금지:

- host 환경에만 의존하는 설명으로 끝내는 것
- container와 host가 서로 다르게 동작하는 상태를 방치하는 것

완료 기준:

- adapter 컨테이너 내부에서 Paddle 실행 경로가 정상 동작할 것

### 3차 작업 지시

목표:

- Paddle raw output 구조를 확인하고 샘플을 저장할 것

구체 지시:

1. `predict()` 결과 구조를 확인할 것
2. page 단위 필드, text 블록, table 블록, bbox 관련 필드를 정리할 것
3. 필요하면 디버그용 raw payload 저장 기능을 넣을 것
4. 단, 디버그 출력은 끄거나 제어 가능해야 함

금지:

- 결과 구조를 추측만 하고 구현하는 것
- 샘플 payload 없이 `transform.py`를 크게 작성하는 것

완료 기준:

- `transform.py` 구현에 필요한 입력 구조가 확보될 것

### 4차 작업 지시

목표:

- `transform.py`를 placeholder에서 실제 변환기로 바꿀 것

구체 지시:

1. OpenDataLoader hybrid path가 읽을 수 있는 최소 스키마를 먼저 맞출 것
2. page 정보부터 구현할 것
3. text block 매핑을 넣을 것
4. table block 매핑을 넣을 것
5. span 정보는 가능하면 넣되, 처음부터 완전하게 하지 않아도 됨

금지:

- raw payload를 그대로 `backend_payload`에만 넣고 끝내는 것
- placeholder 응답을 유지한 채 "성공"으로 처리하는 것

완료 기준:

- OpenDataLoader가 backend 결과를 받아 `json/md/html`을 생성할 것

### 5차 작업 지시

목표:

- `input1.pdf` 기준 end-to-end 실행 검증

구체 지시:

실행 순서:

```bash
cd /home/gon/work/ttt3/opendataloader-pdf
./paddle/stop_stack.sh
GPU_DEVICE=1 ./paddle/start_stack.sh
./paddle/run_opendataloader.sh samples/pdf/input1.pdf tmp/odl-hybrid/input1
```

검증 항목:

1. adapter 502가 없어야 함
2. OpenDataLoader CLI가 종료 코드 0으로 끝나야 함
3. `tmp/odl-hybrid/input1/input1.json`
4. `tmp/odl-hybrid/input1/input1.md`
5. `tmp/odl-hybrid/input1/input1.html`
   가 생성되어야 함

금지:

- 문서만 수정하고 실행 검증을 생략하는 것
- 실패했는데 원인 확인 없이 다음 단계로 넘어가는 것

완료 기준:

- end-to-end 결과 파일 생성 확인

### 6차 작업 지시

목표:

- 품질 점검과 후속 과제 분리

구체 지시:

1. 생성된 `json/md/html`을 열어 결과 품질을 확인할 것
2. 실행 성공과 품질 문제를 분리해서 기록할 것
3. OCR 문제와 schema 변환 문제를 구분해서 적을 것
4. 다음 개선 작업이 무엇인지 남길 것

금지:

- 실행 성공만으로 품질도 정상이라고 결론 내리는 것

완료 기준:

- 후속 개선 포인트가 문서로 남을 것

### AI 최종 산출물

AI는 작업 완료 후 아래를 남겨야 합니다.

1. 수정한 파일 목록
2. 어떤 단계까지 완료했는지
3. 실제 실행한 검증 명령
4. 성공한 항목
5. 아직 남은 문제
6. 다음으로 이어서 할 작업

### AI가 지켜야 할 구현 원칙

- 하드코딩 금지
- 특정 샘플 전용 분기 금지
- 실패 시 원인 로그를 남길 것
- placeholder를 실제 구현으로 교체할 것
- “연결만 된다” 수준에서 멈추지 말고, 실제 산출물 생성까지 검증할 것
