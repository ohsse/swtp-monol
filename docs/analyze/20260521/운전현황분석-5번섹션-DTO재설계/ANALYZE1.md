---
status: approved
created: 2026-05-21
updated: 2026-06-01
---
# 운전현황분석-5번섹션-DTO재설계 — 도메인 분석

## 작업 배경

운전현황분석 5번섹션 (시설 단위 1분 단위 운영 현황 시계열 조회, 커밋 `89b4e00`) 의 **응답 DTO 만 재설계** 하는 사이클이다.

### 재설계 동기

운전현황분석 10번섹션 (예측값 기반 시계열) 사이클의 plan 단계에서 사용자가 다음을 명확화했다:

- 5번섹션도 사실 **3개 시리즈** 차트 — 금일 실측 전력원단위 + 비교일 실측 전력원단위(전일/지난주) + 금일 실측 운영 펌프대수
- 5번섹션 차트 X축은 **시간:분 (00:00~23:59)** 기준이고 날짜는 cosmetic 메타
- 현행 응답 구조(옵션 R 패턴 — `todaySeries` + `comparisonSeries` 2개 분리 List, 각 포인트는 `acqDtm: LocalDateTime`) 는 frontend 가 시간:분 키로 머지해야 차트로 그릴 수 있는 **머지 부담** 발생
- frontend 가 5번섹션 + 10번섹션 양쪽에서 같은 머지 부담을 진다는 사실은 **5번섹션 자체의 디자인 결함**

### 사용자 결정

5번섹션을 옵션 T 패턴 (행렬형 통합 + 1440 고정 시간:분 키 + baseDate/comparisonDate 메타 + 머지 완료 단일 List) 으로 **먼저 재설계** 한 뒤, 10번섹션을 같은 패턴으로 신규 구현. 사용자 메모리 `feedback_existing_decisions_reevaluation` (기존 결정 비판적 재평가) 정합.

### 전제 정정 (2026-06-01) — 10번섹션 독립 구현 완료

본 ANALYZE 작성(2026-05-21) 이후 실제 경과가 위 §재설계 동기·§사용자 결정의 "5번 먼저 재설계 → 10번 후속 구현" 선후 전제와 달라졌다. 사용자 승인(2026-06-01) 하에 본 전제만 정정하고 옵션 T 설계는 draft 그대로 유지한다.

- **10번섹션이 먼저 독립 구현·커밋됨** (`c257af1`, 2026-05-27) — 본 DTO재설계를 선행하지 않고 별도 DTO `FacilityDailyTimeSeriesDto` + 신규 엔드포인트 `GET /api/facility/{facilityId}/operating-status/daily-time-series` 로 구현됨.
- **10번 DTO 구조는 옵션 T draft 와 다름** — 시각 키 `dtm: LocalDateTime`(`yyyy-MM-dd HH:mm:ss`), 양쪽 결측 슬롯 **생략(omit)**, List 필드명 `points`, inner 클래스 `DailyTimeSeriesPoint`, baseDate/comparisonDate 메타 없음.
- **5번섹션은 10번과 완전 정합 불가 (기술 제약)** — 5번은 금일 vs 비교일(어제/지난주) **서로 다른 날짜의 같은 시:분을 한 행으로 머지**하므로, 날짜가 박힌 `dtm: LocalDateTime` 을 공통 머지 키로 쓸 수 없다. 반드시 날짜 제거된 `time: "HH:mm"` 키 + `baseDate`/`comparisonDate` 메타가 필요하다 (10번은 actual·predc 가 같은 날짜라 `dtm` 키 가능). 따라서 draft 옵션 T 의 time-only 키 선택은 5번 데이터 특성상 정당하며 10번과의 구조 차이는 불가피하다.
- **사용자 결정 (2026-06-01)**: draft 옵션 T 설계(`time` "HH:mm" + 1440 고정 null-fill + `baseDate`/`comparisonDate` 메타 + `series`/`TimeSeriesPoint` 네이밍) **그대로 유지**. 행 채움도 1440 고정 유지 (10번 omit 과 다름) — 차트 시간축 완전성·frontend 머지 부담 0 이점 우선. 사용자 메모리 `feedback_no_auto_reuse_cross_cycle` (사이클 간 자산 자동 원용 금지) 정합 — 10번 구조를 자동 차용하지 않고 5번 데이터 특성 기준 독립 결정.

이하 회의록·결정 사항은 본 전제 정정과 무관하게 유효하다 (응답 DTO 옵션 T 재설계 범위·도메인 정책 불변).

### 변경 범위

| 항목 | 변경 여부 |
|------|---------|
| 엔드포인트 경로·HTTP·파라미터 | **변경 없음** (`GET /api/facility/{facilityId}/operating-status/timeseries?compareType=...`) |
| 데이터 조회 정책 (rawdata_1m_h · 2회 분리 호출 · 인덱스 활용) | **변경 없음** |
| 도메인 정책 (활성 시설·OPS 판정·PWI 합산·FRI 선택·전력원단위 계산) | **변경 없음** |
| 응답 DTO 구조 | **재설계** (옵션 R → 옵션 T) |
| Service `buildSeries` · `buildPoint` 헬퍼 | **재작성** (1440 머지 + 시간:분 키 변환) |
| Service 헬퍼 9종 (`findActiveFacilityOrThrow` 등) | **변경 없음** |
| Controller 응답 시그니처 | **변경 없음** (응답 DTO 클래스명 동일, 내부 구조만 변경) |
| Repository | **변경 없음** |
| 단위 테스트 5건 | **재작성** (옵션 T 응답 구조 검증으로) |
| frontend SPEC | **breaking change** — `/dev:spec` 단계에서 SPEC{N+1} 갱신 의무 |

### 외부 산출물

- 본 사이클의 직전 plan 모드 결과: `~\.claude\plans\10-backend-image-png-10-reactive-piglet.md` (2단계 작업 합의 문서)
- 5번섹션 신규 구현 ANALYZE: `docs/analyze/20260521/운전현황분석-5번섹션/ANALYZE1.md` (`status: approved`) — 도메인 정책 재인용 근거

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 표준 사전 영향 검토

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 어휘 후보 (`today`·`comparison`·`baseDate`·`comparisonDate`·`time`) 전부 **등록 불필요** — Java 식별자 영역으로 표준 사전 (DB 컬럼명 조합 재료) 적용 외. 5번섹션 ANALYZE1 안건 1 결론 그대로 재인용. `today`/`comparison` 어근은 기존 `actl`(실측)·`predc`(예측) 어근과 의미 분리 — 본 섹션은 모두 실측이고 차이는 "비교 시점" 이지 "실측/예측" 이 아니므로 `today`/`comparison` 가 의미적으로 더 명확. `base`(기준) 어근과 의미 일치하나 DTO 필드명은 표준 사전 적용 외라 충돌 없음. 비즈니스 도메인 약어 사전 영향 없음. 룰 갱신 지시서 체크박스 0건 — PLAN 진입 전제조건 자동 충족.
- **결론**: 표준 사전 갱신 0건. 룰 갱신 지시서 체크박스 0건.

### 안건 2: DB·Repository·인덱스·파티션 영향 검토

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 발견 사항 — 블로커 0건 / 권고 0건 / 참고 4건 (모두 통과):
    - **Q1 참고**: Repository 호출 패턴 그대로 유지 — `findByTagSrlNosAndDtmRange` 2회 분리 호출 정합. 단일 OR/UNION 변경 시 파티션 프루닝 손실 위험 재발생. 머지는 Service 계층 Java 코드 책임.
    - **Q2 참고**: 인덱스·파티션 활용 영향 없음 — `idx_rawdata_1m_h_tag_time` 그대로 활용, 신규 인덱스 추가·변경 0건.
    - **Q3 참고**: 응답 크기 ~30~40% 감소 (옵션 R 최대 2880 포인트 → 옵션 T 1440 고정). Service 머지 처리 부담 O(n=1440) 무시 가능. SLA 200ms 영향 없음.
    - **Q4 참고**: 표준 데이터 도메인 2차 승인 0건 — 응답 DTO 필드 (`baseDate: LocalDate`·`comparisonDate: LocalDate`·`time: String "HH:mm"`) 는 DB 컬럼이 아니라 `DOM_*` 등록 대상 외.
- **결론**: DB·Repository·인덱스 변경 0건. 응답 크기 감소 효과 + Service 머지 부담 무시 가능.

### 안건 3: Service·DTO 재설계 + 머지 알고리즘 + 정량 기준

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 발견 사항 — 블로커 가능성 1건 / 권고 3건 / 참고 1건:
    - **Q1 정합**: DTO outer + inner `TimeSeriesPoint` + Service `buildSeries`·`buildPoint` 재작성 범위 적절. enum·Controller·Repository·헬퍼 9종 변경 없음은 `coding-discipline.md §3` (필요한 부분만 수정) 정합.
    - **Q2 블로커 가능성**: 1440 머지 메서드는 §2.5 면책 영역 미해당 → 50줄 분해 의무. 권장 분해 — `buildOptionTSeries(todayPoints, comparisonPoints, baseDate)` (1440 슬롯 초기화 + today/comparison 머지) + `mergeIntoSlot(slot, todayPoint, comparisonPoint)` (단일 시점 행 빌드) 2단. private 헬퍼는 추상화 계층 카운팅 대상 외이므로 3단 초과 위험 없음. PLAN 단계에서 메서드 본문 줄 수 50줄 이내 분해 계획 명시 의무 — 누락 시 블로커 격상.
    - **Q3 권고 중간**: `totalElpwrAmt` 제거 시 SPEC breaking change 명시 의무 — `/dev:spec` 단계 SPEC{N+1} 변경 이력 표에 `totalElpwrAmt 제거` 기재 필요. PLAN 체크박스에도 기재.
    - **Q4 권고 중간**: `time` 타입 — `String "HH:mm"` 권장 (어노테이션 불필요 + 단순). `LocalTime` 채택 시 `@JsonFormat(shape=Shape.STRING, pattern="HH:mm")` 명시 의무, 누락 시 frontend 배열 수신 무증상 버그.
    - **Q5 권고 중간**: 1440 행 고정 검증 케이스 1건 추가 의무 — "오늘 미래 시점 null 채움 + 1440 고정" 별도 테스트. 기존 5건 (정상·빈·결측·UNCERTAIN·비활성) 재작성 + 1건 신규 = 6건.
    - **참고 낮음**: 신규 `series: List<TimeSeriesPoint>` 필드에 `@ArraySchema(schema = @Schema(implementation = TimeSeriesPoint.class))` 적용 의무 (`api-patterns.md §DTO @Schema(implementation) 명시 패턴`).
- **결론**:
  - DTO 재설계 + Service `buildSeries`/`buildPoint` 재작성 + 헬퍼 9종·enum·Controller·Repository 변경 없음
  - 머지 메서드 2단 분해 (`buildOptionTSeries` + `mergeIntoSlot`) — PLAN 에서 줄 수 명시 의무
  - `totalElpwrAmt` 제거 — SPEC breaking change 명시 의무
  - `time` 타입 = `String "HH:mm"` 채택 (단순성 우선)
  - 1440 행 고정 검증 케이스 1건 추가 (테스트 6건)
  - `@ArraySchema` 신규 `series` 필드 적용 의무

### 안건 4: 도메인 룰 4영역 점검 + 결측 정책 변경 영향

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 발견 사항 — 블로커 0건 / 권고 0건 / 참고 2건:
    - **Q1 통과**: 4영역 (알람·인터록·AI 운전 모드·이력 기록) 모두 비해당 — 5번섹션 ANALYZE1 비해당 판정 그대로 재인용 가능. PLAN 의 "## 신규 엔티티/DB 컬럼" 섹션 "없음" 명시 시 비해당 4건 차단 해제 조건 충족.
    - **Q2 통과**: LAST_MONTH_AVG 추가 보류 정당 — 5번 ANALYZE1 D4 결정 유지, 별도 데이터 집계 사이클에서 처리. `coding-discipline.md §2` "요청되지 않은 기능 추가 금지" 정합.
    - **Q3 참고 낮음**: `comparisonDate` 메타 추가 적절 — 운영자가 차트의 "비교" 시리즈 기준 날짜를 명확히 인지 가능. 단 **서버 JVM 시간대 = Asia/Seoul 가정 명시** 권고 — 자정 전후 1시간 구간에서 baseDate/comparisonDate 가 프론트엔드 사용자 체감 날짜와 다를 위험. PLAN "## 가정 및 미해결 질문" 1건 기재 의무.
    - **Q4 통과**: 1440 고정 행 + 결측 null 채움 정책 변경 통과 — `ot-integration.md §3` OPS 즉시 BAD 격상 정책과 정합성 오히려 개선. 결측 omit (옵션 R) 시 frontend 가 BAD 를 GOOD 으로 오판할 여지 있었으나, 명시적 null 전달로 "데이터 없음" 확정 표현. 운전원 안전 측면 개선.
    - **Q5 참고 낮음**: `todayOnPumpCnt` Swagger description 에 "quality_cd=GOOD 이고 가동상태값이 1.0인 펌프 대수. UNCERTAIN/BAD 품질의 OPS 태그 제외 (ot-integration.md §3)" 명시 권고. `todayElpwrUnitQty`·`comparisonElpwrUnitQty` 의 null 4케이스 규칙도 description 명기 의무.
- **결론**:
  - 도메인 룰 4영역 모두 비해당 — 본 ANALYZE "## 도메인 룰 4영역 점검" 표에 구체 사유 명기
  - 서버 JVM 시간대 Asia/Seoul 가정 명시 — PLAN "## 가정 및 미해결 질문" 1건 추가
  - Swagger description 명시 — PLAN 의무 항목

---

## 표준 사전 카탈로그

본 사이클은 표준 사전 3층 모두에 신규 등록 항목이 없음 (안건 1 wtp-glossary-manager Round 1 결론). 표 생략.

---

## 신규 엔티티/DB 컬럼

**없음** — 본 사이클은 응답 DTO 구조 재설계로 신규 엔티티·DB 컬럼·DDL·인덱스 추가 0건.

재작성 Java 클래스 (DB 영향 없음):
- `api/src/main/java/com/mo/swtp/facility/dto/FacilityOperatingStatusTimeSeriesDto.java` — outer DTO 필드 변경 + inner `TimeSeriesPoint` 재작성
- `api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesService.java` — `buildSeries`·`buildPoint` 재작성, 신규 `buildOptionTSeries`·`mergeIntoSlot` private 헬퍼 추가, 헬퍼 9종 변경 없음

재작성 테스트:
- `api/src/test/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesServiceTest.java` — 기존 5건 재작성 + 1440 행 고정 검증 1건 추가 = 6건

변경 없음:
- `api/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityOperatingStatusCompareType.java`
- `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` (응답 클래스명 동일, 내부 구조 변경만)
- `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` + Impl

---

## 기존 사전·패턴과의 충돌

**없음** — 표준 사전 영향 0건, 도메인 룰 4영역 모두 비해당, 5번섹션 신규 구현 사이클의 모든 도메인 정책 (활성 시설·OPS 판정·PWI 합산·FRI 선택·전력원단위 계산) 그대로 유지.

---

## PLAN 으로 전달할 결정 사항

### A. 응답 DTO 구조 (옵션 T 패턴)

```
FacilityOperatingStatusTimeSeriesDto {
  facilityId: String,
  facilityNm: String,
  baseDate: LocalDate,                     // 신규 — 금일
  compareType: FacilityOperatingStatusCompareType,
  comparisonDate: LocalDate,               // 신규 — 비교일 실제 날짜
  series: List<TimeSeriesPoint>            // 1440 고정 행
}

TimeSeriesPoint {
  time: String,                            // "HH:mm" — Q4 결정 (String 채택)
  todayElpwrUnitQty: BigDecimal,           // 시리즈 1: 금일 실측 전력원단위 (null 허용)
  comparisonElpwrUnitQty: BigDecimal,      // 시리즈 2: 비교일 실측 전력원단위 (null 허용)
  todayOnPumpCnt: Integer                  // 시리즈 3: 금일 실측 운영 펌프대수 (null 허용)
  // totalElpwrAmt 는 제거 (Q3 결정)
}
```

### B. Service 메서드 분해 (PLAN 명시 의무)

- `buildOptionTSeries(todayPoints, comparisonPoints, baseDate)` — 1440 슬롯 초기화 + today/comparison 머지 (50줄 이내)
- `mergeIntoSlot(slot, todayPoint, comparisonPoint)` — 단일 시점 행 빌드 (10줄 이내)
- 호출 스택: Controller → Service 공개 진입점 → buildOptionTSeries(private) → mergeIntoSlot(private) — 추상화 깊이 3단 (private 헬퍼는 계층 카운팅 외)

### C. SPEC breaking change

- 응답 DTO 의 outer 필드 (`todaySeries`·`comparisonSeries` 제거 + `series`·`baseDate`·`comparisonDate` 추가) breaking change
- inner `TimeSeriesPoint` 의 필드 (`acqDtm` 제거 + `time` 추가, `totalElpwrAmt` 제거, 시리즈 컬럼명 변경) breaking change
- `/dev:spec` 단계에서 SPEC{N+1} 의 "변경 이력" 표에 명시 의무

### D. Repository·도메인 정책

- `RawDataCustomRepository.findByTagSrlNosAndDtmRange()` 2회 분리 호출 패턴 그대로 유지
- 인덱스 `idx_rawdata_1m_h_tag_time` 그대로 활용
- 5번섹션 ANALYZE1 의 모든 도메인 정책 (활성 시설·OPS·PWI·FRI·전력원단위) 그대로 유지

### E. Swagger 명세 의무

- `series` 필드: `@ArraySchema(schema = @Schema(implementation = TimeSeriesPoint.class))`
- `baseDate`·`comparisonDate`: `@Schema(description=..., implementation = LocalDate.class)` 또는 example 형식 지정
- `time`: `@Schema(description="시간:분 (HH:mm)", example="14:30")`
- `todayOnPumpCnt`: `@Schema(description="quality_cd=GOOD 이고 가동상태값 1.0인 펌프 대수. UNCERTAIN/BAD OPS 태그 제외 (ot-integration.md §3)")`
- `todayElpwrUnitQty` · `comparisonElpwrUnitQty`: `@Schema(description="...분자/분모 0·NULL·BAD·부재 시 null...")`

### F. 테스트 6건

기존 5건 (정상 시계열·빈 시계열·1분 결측·UNCERTAIN·비활성 시설 거부) 옵션 T 응답 구조 검증으로 재작성 + 1440 행 고정 검증 1건 신규.

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 서버 JVM 시간대 = Asia/Seoul 가정. `LocalDate.now()`·`LocalDateTime.now()` 의 베이스가 Asia/Seoul 인 환경에서 baseDate/comparisonDate 가 프론트엔드 사용자 체감 날짜와 일치. 자정 전후 1시간 구간에서 환경 시간대가 다르면 날짜 불일치 위험 (안건 4 Q3) | 가정 | PLAN 단계 명시 — 배포 환경의 명시적 시간대 설정 권고. 향후 시간대 미설정 운영 환경 발견 시 별도 보강 ANALYZE |
| 1440 고정 행 — 옵션 T 의 `series` 는 00:00·00:01·…·23:59 의 1440 슬롯 전부 포함. today/comparison 둘 다 결측인 시점도 행은 존재하되 3 컬럼 모두 null. 단위 테스트로 검증 의무 | 결정 | PLAN 체크박스 1440 행 검증 신규 케이스 명시 |
| comparisonDate 계산 — `LocalDate.now().minusDays(1 또는 7)` 의 결과를 응답에 그대로 노출. compareType=YESTERDAY → -1d, LAST_WEEK → -7d | 결정 | PLAN 단계 확정 |
| `totalElpwrAmt` 제거 후 frontend 가 디버깅·검증 용도로 필요해질 가능성 | 미해결 | 본 사이클은 제거. 향후 frontend 요구 시 별도 사이클로 재추가 검토 |
| `time: String "HH:mm"` 직렬화 시 정렬 — 단순 lexicographic 정렬이 시간 순서 정렬과 일치 (00:00 < 00:01 < … < 23:59) | 결정 | PLAN 단계 명시 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 |
|---------|--------------------------|
| `FacilityOperatingStatusTimeSeriesService` 의 단위 테스트 6건 GREEN — 5건 재작성 + 1440 행 고정 1건 신규 | ./gradlew.bat :api:test PASS, 신규 클래스 6케이스 |
| `compareType=YESTERDAY` 와 `LAST_WEEK` 두 옵션 모두 옵션 T 응답 구조 직렬화 정상 | 통합 테스트 또는 Swagger UI 수동 호출 — `series.size() == 1440` 확인 |
| Repository 두 시간 범위 호출 각각 200ms 이내 + 파티션 프루닝 적중 (변경 없음 재확인) | EXPLAIN (ANALYZE, BUFFERS) 출력에 `Subplans Removed` 또는 `Partitions Selected` 1~2 표시 |
| 시간:분 1440 행 오름차순 정렬 + 결측 시점 null 채움 동작 | 통합 테스트 — `series[0].time == "00:00"` · `series[1439].time == "23:59"` · 미래 시각 todayElpwrUnitQty == null |
| Service `buildOptionTSeries` · `mergeIntoSlot` 메서드 본문 줄 수 50줄 이내 | 코드 리뷰 단계 시각 점검 + 신규 단위 테스트로 머지 로직 검증 |
| Swagger description 명시 검증 — todayOnPumpCnt OPS GOOD 전용 판정 + null 4케이스 규칙 + 시간 형식 "HH:mm" | Swagger UI 한국어 description 명시 확인 + `/v3/api-docs` 응답 jq 검증 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 응답 DTO 구조 재설계로 알람 생성·전이 없음, `alarm_h` INSERT 미수행. 임계값·전이 조건·복귀 조건 변경 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 조회 전용 API, 제어 명령 미발행, `ot-integration.md §2` 아웃바운드 경로 (`ScadaOutboundPort`) 미진입. 인터록 룰 `pump_interlock_p` 미참조 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`·`ai_drvn_mod_h` 미참조. SCADA 5분 강제 전환 판정 로직 미포함. 사용자 의도·시스템 상태 변경 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 새 이력 INSERT 없음, `pump_ctrl_h`·`ai_drvn_mod_h` 무접촉. `transition_reason` 컬럼 영향 없음 |

"비해당" 단독 4건 차단 해제 조건 충족: (1) 각 행 구체 사유 명기 (위 표), (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족.

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 사이클은 표준 사전 갱신 0건 + DB 영향 0건 + 도메인 룰 4영역 비해당으로 룰 갱신 지시서 체크박스 0건. PLAN 진입 전제조건 자동 충족.

---

## 산출물

- [계획안](../../../plan/20260521/운전현황분석-5번섹션-DTO재설계/PLAN1.md) (status: draft 예정)
