# Plan

**생성일시**: 2026-04-24 11:15
**요청**: 밑줄(underline)이 있는 각주 텍스트가 테이블로 잘못 인식되는 버그 수정 — AND 결합 필터로 안전하게 제거

## Plan

- **Task Summary**: 페이지 하단의 밑줄 각주(footnote) 텍스트가 표로 오인되는 버그 수정. 새 `UnderlineFootnoteTableFilterProcessor` 필터를 추가하여 안전하게 제거
- **Objective**: 밑줄로 인해 line-based table detection이 각주 텍스트를 테이블로 오탐지하는 것을 방지
- **Scope**:
  - `UnderlineFootnoteTableFilterProcessor.java` 신규 생성
  - `DocumentProcessor.java`에 필터 호출 추가
  - 단위 테스트 `UnderlineFootnoteTableFilterProcessorTest.java` 신규 생성
- **Non-Goals**:
  - 기존 테이블 감지 알고리즘(veraPDF `LinesPreprocessingConsumer`) 수정 불가
  - 기존 `DominantImageTableFilterProcessor` 로직 변경 불가
  - 다른 종류의 FP(Chart/Figure 오탐지)는 범위 외
- **Constraints**:
  - 단일 필터 기준(셀 겹침, 빈 셀 비율)은 정상 테이블 회귀 위험으로 단독 사용 불가
  - AND 결합 조건으로만 필터 적용: 상단 위치 + 전체 빨간색 + 높은 빈 셀 비율
  - 기존 정상 테이블 0개가 필터링되지 않아야 함 (사전 영향 분석 기준)
- **Assumptions**:
  - 각주 텍스트는 `SemanticTextNode.getTextColor()`가 `[1.0, 0.0, 0.0]` (빨간색)인 특징을 가짐
  - 각주 테이블은 PDF 좌표계 상단 5% 영역에 위치함 (읽기 순서상 마지막)
  - 정상 테이블 중 빨간 텍스트 전체 + 상단 5% + 빈 셀 25%+ 조건을 모두 만족하는 것은 없음
- **Affected Areas**:
  - `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/DocumentProcessor.java` (2줄 추가)
  - `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/UnderlineTableFilterProcessor.java` (신규)
  - `java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/UnderlineTableFilterProcessorTest.java` (신규)
- **Implementation Strategy**:

  신규 `UnderlineFootnoteTableFilterProcessor` 클래스 생성:
  - 기존 필터들과 동일한 패턴: `final` class, private constructor, public static 메서드
  - 시그니처: `filterFootnoteTables(List<IObject> contents, BoundingBox pageBoundingBox)`
  - 필터 조건 (모두 AND로 결합):
    1. **페이지 상단 위치**: 테이블 bounding box의 topY가 페이지 상단 5% 이내 (PDF 좌표계 상단 = 읽기 순서 마지막)
    2. **모든 텍스트가 빨간색**: 모든 비어있지 않은 셀의 `SemanticTextNode.getTextColor()`이 `[1.0, 0.0, 0.0]`
    3. **빈 셀 비율 ≥ 25%**: `emptyCells / totalCells >= 0.25`
  - 조건 충족 시 해당 `TableBorder`를 리스트에서 제거

  DocumentProcessor.java 수정:
  - line 214 (DominantImageTextFilterProcessor 호출) 직후에 새 필터 호출 추가

- **Risks**:
  - **회귀 리스크 (낮음)**: AND 결합으로 인해 정상 테이블 영향 최소화. 사전 분석에서 0개 영향 확인
  - **색상 기반 리스크 (낮음)**: 빨간색 전체 + 상단 5% + 빈 셀 높은 비율 = 매우 좁은 조건
  - **페이지 상단 기준 리스크 (낮음)**: 이 조건은 매우 좁은 영역 (페이지 top 5% = 75.6pt)만 해당

- **Verification Results**:
  1. 신규 단위 테스트 5개 모두 통과:
     - `removesFootnoteTable_atBottom_allRed_highEmptyRatio` ✔ (각주 테이블 제거)
     - `keepsTable_withPartialRedText_inMiddleOfPage` ✔ (부분 빨간색 정상 테이블 유지)
     - `keepsTable_inTop5Percent_withLowEmptyRatio` ✔ (상단 위치지만 빈 셀 낮음 → 유지)
     - `keepsTable_inTop5Percent_allRedButLowEmptyRatio` ✔ (상단 + 빨간색 전체지만 빈 셀 낮음 → 유지)
     - `keepsTable_atBottom_allRedButNotInTop5Percent` ✔ (빨간색이지만 하단 위치 → 유지)
  2. 기존 filter processor 테스트 6개 모두 통과 (회귀 없음)
  3. 전체 350개 테스트 중 기존 실패 46개 (본 변경과 무관, veraPDF StaticContainers NPE)

- **Definition of Done**:
  - 단위 테스트 전체 통과 ✔
  - 기존 테스트 회귀 없음 ✔
  - input3.pdf 재실행하여 각주 테이블 제거 확인 ✔
  - 정상 테이블 3개 회귀 없음 (Page 1: 18×14, 13×15, 28×14 모두 동일) ✔

## Task Breakdown

- [x] `UnderlineFootnoteTableFilterProcessor.java` 신규 생성
- [x] `DocumentProcessor.java`에 필터 호출 추가
- [x] `UnderlineFootnoteTableFilterProcessorTest.java` 단위 테스트 작성
- [x] 기존 테스트 회귀 없음 확인
- [x] 검증: input3.pdf 재실행하여 각주 테이블 제거 확인
