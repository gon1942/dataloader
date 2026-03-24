# OCR 후 마크다운 읽기 순서가 틀어지던 원인과 개선 사항

## 1. 문제 현상

스캔 PDF를 OCR(Hybrid 모드)로 처리한 후, 출력된 마크다운의 **읽기 순서가 문서 원래 순서와 다름** — 특히 2단 레이아웃에서 좌/우 컬럼이 섞여서 출력됨.

---

## 2. 근본 원인: 3가지 레이어에서의 문제

### 원인 ① Hybrid 모드에서 읽기 순서 알고리즘이 누락됨

**이전 동작** (commit `3e3f2d2` 이전):

```
HybridDocumentProcessor
  → Backend(Docling)에서 Markdown/HTML을 직접 생성
  → Docling의 단순 정렬(Y→X)만 적용
  → 다중 컬럼 처리 불가 → 좌/우 컬럼 섞임
```

Docling 자체의 `sortByReadingOrder()`는 단순한 "위→아래, 왼→오" 정렬이며, **다중 컬럼 레이아웃을 인식하지 못함**:

```java
// DoclingSchemaTransformer 내부 (단순 Y→X 정렬)
contents.sort((o1, o2) -> {
    double topDiff = o2.getTopY() - o1.getTopY();
    if (Math.abs(topDiff) > 5.0) return topDiff > 0 ? 1 : -1;
    return Double.compare(o1.getLeftX(), o2.getLeftX()); // 같은 Y → 좌→우
});
```

2단 레이아웃에서 같은 Y 위치의 좌/우 컬럼 원소가 번갈아 출력됨.

### 원인 ② 좁은 원소가 컬럼 간격을 가로막음 (Issue #294)

XY-Cut++가 컬럼 간격을 감지하는 방식은 **원소의 가장자리(edge) 간격**을 기준으로 함:

```
좌측 컬럼 [50-300]    페이지 번호 "42" [302-318]    우측 컬럼 [320-560]

실제 컬럼 간격: 300 → 320 = 20pt
하지만 페이지 번호가 간격을 가로막음:
  edge gap: 300→302 = 2pt, 318→320 = 2pt
  둘 다 MIN_GAP_THRESHOLD(5pt) 미만!
  → "유효한 수직 컷 없음"으로 판단
  → sortByYThenX() 폴백 → 컬럼 섞임
```

### 원인 ③ 집계 바운딩 박스가 읽기 시작 위치와 달랐음

`SemanticTextNode`(단락)나 `PDFList`(리스트)는 **모든 자식 줄을 포함하는 집계 바운딩 박스**를 가짐:

```
단락 "Body text..."
  첫 번째 줄: Y=1650 (실제 읽기 시작 위치)
  마지막 줄: Y=300
  집계 바운딩 박스: Y=300 ~ Y=1760
  중심점(centerY): Y=1030 ← 첫 줄과 620pt 차이!
```

알고리즘이 이 집계 중심점을 사용하면, 단락이 잘못된 그룹에 배치됨.

---

## 3. 개선 사항 상세

### 개선 ① Backend → JSON만 반환, Java에서 읽기 순서 통일 적용

**Commit**: `3e3f2d2` (2026-01-03)

```java
// 변경 전: Backend에서 Markdown/HTML을 직접 생성 (읽기 순서 불일치)
determineOutputFormats(config) → JSON, MARKDOWN, HTML

// 변경 후: Backend에서 JSON만 반환 → Java에서 일괄 처리
determineOutputFormats(config) → JSON
```

**결과**: 모든 페이지(Java 처리 + Backend 처리)가 동일한 `XYCutPlusPlusSorter.sort()`를 거치게 됨.

```
HybridDocumentProcessor.processDocument()
  → Backend(Docling) JSON 결과 → DoclingSchemaTransformer 변환
  → 반환
     ↓
DocumentProcessor.sortContents()  ← 항상 실행됨
  → XYCutPlusPlusSorter.sort()   ← 모든 페이지에 적용
```

### 개선 ② 좁은 이상치 원소 필터링 (Issue #294)

**Commit**: `9f6f3c6` (2026-03-19), `8e3f74a` (2026-03-20)

`findBestVerticalCutWithProjection()`에 **폴백 메커니즘** 추가:

```java
// 1단계: 일반 edge gap 검출
CutInfo edgeCut = findVerticalCutByEdges(objects);

if (edgeCut.gap >= MIN_GAP_THRESHOLD) {
    return edgeCut;  // 정상: 컬럼 간격이 충분히 큼
}

// 2단계: 좁은 원소 제거 후 재시도
if (objects.size() >= 3) {  // 8e3f74a에서 4→3으로 수정
    double narrowThreshold = regionWidth * 0.1;  // 영역 폭의 10%
    List<IObject> filtered = /* 좁은 원소 제거 */;
    if (filtered.size() >= 2 && filtered.size() < objects.size()) {
        CutInfo filteredCut = findVerticalCutByEdges(filtered);
        if (filteredCut.gap > edgeCut.gap
            && filteredCut.gap >= MIN_GAP_THRESHOLD) {  // 8e3f74a에서 추가
            return filteredCut;  // 진짜 컬럼 간격 발견!
        }
    }
}
```

**효과**: 페이지 번호/각주 표시가 컬럼 간격에 있어도, 이를 제거한 후 진짜 컬럼 간격(20pt)을 감지.

### 개선 ③ 읽기 순서 앵커(Reading Order Box) 도입 (미커밋, 작업 중)

집계 바운딩 박스 대신 **첫 번째 줄의 바운딩 박스**를 읽기 순서 기준점으로 사용:

```java
private static BoundingBox getReadingOrderBox(IObject object) {
    // 단락: 첫 번째 TextLine의 바운딩 박스 사용
    if (object instanceof SemanticTextNode) {
        TextLine firstLine = ((SemanticTextNode) object).getFirstLine();
        if (firstLine != null && firstLine.getBoundingBox() != null) {
            return firstLine.getBoundingBox();  // 집계가 아닌 첫 줄!
        }
    }
    // 리스트: 첫 번째 아이템의 첫 번째 줄 사용
    if (object instanceof PDFList) {
        ListItem firstItem = ((PDFList) object).getFirstListItem();
        if (firstItem != null) {
            TextLine firstLine = firstItem.getFirstLine();
            if (firstLine != null && firstLine.getBoundingBox() != null) {
                return firstLine.getBoundingBox();
            }
        }
    }
    return object.getBoundingBox();  // 기타: 기존대로
}
```

이 메서드가 알고리즘 전체에서 사용됨:

- `findVerticalCutByEdges()` — 수직 갭 감지
- `findBestHorizontalCutWithProjection()` — 수평 갭 감지
- `splitByHorizontalCut()` / `splitByVerticalCut()` — 분할 기준
- `sortByYThenX()` — 폴백 정렬
- cross-layout 병합

### 개선 ④ 교차 레이아웃 감지 활성화 (미커밋, 작업 중)

```java
// 변경 전: DEFAULT_BETA=2.0 → maxWidth 기준 → 사실상 비활성화
// (어떤 원소도 자기 자신의 2배 너비가 될 수 없음)
static final double DEFAULT_BETA = 2.0;
double threshold = beta * maxWidth;

// 변경 후: DEFAULT_BETA=0.7 → regionWidth 기준 → 정상 동작
static final double DEFAULT_BETA = 0.7;
double threshold = beta * regionWidth;
```

전체 폭의 70% 이상이고 2개 이상 원소와 수평 겹치는 원소(제목, 요약 테이블 등)를 **분할 전에 추출**하여 컬럼 감지 방해 요소 제거.

### 개선 ⑤ 기타 읽기 순서 관련 수정

| 수정 | Commit | 내용 |
|------|--------|------|
| XY-Cut++ 알고리즘 도입 | `4dd3c1c` | 단순 bbox 정렬 → XY-Cut++ 재귀 분할 (다중 컬럼 지원) |
| 무의미한 갭 분할 방지 | `fd59ae4` | `MIN_GAP_THRESHOLD=5.0` — 5pt 미만 갭은 컷하지 않음 (OCR 노이즈 방지) |
| 무한 재귀 방지 | `d98f723`/`3e144b4` | 분할 후 그룹이 1개뿐이면 폴백 정렬로 전환 (StackOverflow 방지, #179) |
| 코드 리팩토링 | `b1d8823` | 수동 좌표 계산 → `BoundingBox.getWidth()`, `getCenterX()` 등 유틸 메서드 사용 |

---

## 4. 수정 전/후 비교

```
[수정 전] 스캔 PDF → Hybrid → Docling이 Markdown 직접 생성
  → 단순 Y→X 정렬
  → 2단 문서에서 컬럼 섞임
  → 페이지 번호가 컬럼 간격을 가로막아 감지 실패
  → 단락 집계 바운딩 박스로 인해 잘못된 위치 배치

[수정 후] 스캔 PDF → Hybrid → Docling이 JSON만 반환
  → DoclingSchemaTransformer가 IObject로 변환
  → DocumentProcessor.sortContents()에서 XY-Cut++ 일괄 적용
  → 좁은 이상치 필터링으로 컬럼 간격 정확 감지
  → 읽기 순서 앵커(첫 줄 기준)로 정확한 위치 배치
  → 교차 레이아웃 분리로 컬럼 감지 방해 방지
  → 올바른 읽기 순서의 Markdown 출력
```

---

## 5. 수정 파일 요약

| 파일 | 상태 | 변경 내용 |
|------|------|----------|
| `XYCutPlusPlusSorter.java` | 커밋됨 + 미커밋 | 좁은 이상치 필터링, 읽기 순서 앵커, 교차 레이아웃 감지 활성화 |
| `XYCutPlusPlusSorterTest.java` | 커밋됨 + 미커밋 | 좁은 이상치 필터링 테스트, 집계 객체 읽기 앵커 테스트 |
| `HybridDocumentProcessor.java` | 커밋됨 | Backend → JSON만 요청, Java에서 읽기 순서 적용 |
| `DoclingSchemaTransformer.java` | 커밋됨 | Docling JSON → IObject 변환 (읽기 순서는 Java가 담당) |
| `DocumentProcessor.java` | 커밋됨 + 미커밋 | `sortContents()`가 Hybrid 결과에도 항상 실행, 디버그 로깅 추가 |

---

## 6. XY-Cut++ 알고리즘 개요

XY-Cut++는 논문 arXiv:2504.10258을 기반으로 한 4단계 읽기 순서 알고리즘:

### Phase 1: 교차 레이아웃 사전 마스킹
- 폭이 영역 너비의 70% 이상이고, 2개 이상 원소와 수평으로 겹치는 원소 추출
- 전체 폭 제목, 요약 테이블 등이 여기에 해당
- 분할 전에 제거 → 컬럼 감지 방해 방지 → 최종 결과에 Y 위치 기준으로 병합

### Phase 2: 밀도 비율 계산
- `콘텐츠 면적 / 바운딩 영역 면적`
- 높은 밀도(>0.9) → 신문 스타일 밀집 레이아웃 → 수평 컷 선호

### Phase 3: 재귀 분할 (핵심)
1. X축과 Y축 각각에서 **가장 큰 갭**을 투영법으로 탐색
2. `MIN_GAP_THRESHOLD = 5.0pt` 미만 갭은 무시 (OCR 노이즈 방지)
3. 더 큰 갭의 방향으로 컷 수행
4. 컷 위치 기준으로 원소를 두 그룹으로 분할
5. 각 그룹에 대해 재귀적으로 반복
6. 유효한 컷이 없으면 `sortByYThenX()` 폴백

### Phase 4: 교차 레이아웃 병합
- Phase 1에서 추출한 원소를 Y 위치 기준으로 정렬된 메인 콘텐츠에 재삽입

---

## 7. Hybrid 모드 전체 읽기 순서 흐름

```
1. DocumentProcessor.processFile()
   |
   ├── preprocessing() — PDF 파싱, 청크 추출, 테이블 보더 감지
   ├── calculateDocumentInfo()
   ├── getValidPageNumbers()
   |
   ├── HybridDocumentProcessor.processDocument()  [hybrid-mode=full]
   |   |
   |   ├── client.checkAvailability()
   |   ├── filterAllPages() — ContentFilterProcessor on each page
   |   ├── SKIP triage (all pages → BACKEND)
   |   ├── processBackendPath()
   |   |   |
   |   |   ├── Send entire PDF to Docling backend via HTTP
   |   |   ├── Receive JSON response (texts, tables, pictures)
   |   |   ├── DoclingSchemaTransformer.transform()
   |   |   |   ├── Transform texts → SemanticParagraph/SemanticHeading
   |   |   |   ├── Transform tables → TableBorder
   |   |   |   ├── Transform pictures → SemanticPicture
   |   |   |   └── sortByReadingOrder() [단순 T→B, L→R — 임시]
   |   |   └── Set IDs on all objects
   |   |
   |   ├── mergeResults() — backend 결과를 페이지 순서대로 배치
   |   └── postProcess() — header/footer, list/table/heading 교차 페이지 처리
   |
   ├── sortContents()  ← 핵심: 모든 페이지에 XY-Cut++ 적용
   |   |
   |   └── XYCutPlusPlusSorter.sort() on EACH page
   |       |
   |       ├── Phase 1: Extract cross-layout elements (full-width headers/tables)
   |       ├── Phase 2: Compute density ratio for axis preference
   |       ├── Phase 3: Recursive XY-Cut segmentation
   |       |   ├── findBestHorizontalCutWithProjection() — Y-axis gaps
   |       |   ├── findBestVerticalCutWithProjection()   — X-axis gaps
   |       |   |   ├── findVerticalCutByEdges()          — 일반 edge gap
   |       |   |   └── narrow outlier fallback           — <10% width 원소 제거 후 재시도
   |       |   ├── Choose direction (larger valid gap wins)
   |       |   ├── Split at cut position
   |       |   └── Recurse on each group
   |       └── Phase 4: Merge cross-layout elements back by Y position
   |
   ├── ContentSanitizer.sanitizeContents()
   └── generateOutputs() — JSON, Markdown, HTML, Text
```
