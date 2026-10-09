---
status: approved
created: 2026-06-08
updated: 2026-06-08
---
# 시설별 사용량 5번섹션 — 운영시설 전력량 트렌드 조회 도메인 분석

## 작업 배경

`backend/image/시설별사용량.png` 대시보드의 **5번섹션**(우측 하단 시계열 트렌드 차트)을 구현한다. 1번섹션 파라미터 3종 — **집계단위([시/일/월]) · 검색시작일자 · 검색종료일자** — 을 그대로 받아, **운영시설(FacilityGroup.OPERATION 8종) 각각의 전력량(kWh)을 버킷 시계열(트렌드)로 표출**하는 **읽기 전용 API** 다.

같은 화면의 1번섹션(조회 조건)·2번섹션(운영시설 전력 4지표 단일집계, `GET /api/facility/energy-usage`)은 이미 구현·승인 완료([2번섹션 ANALYZE1](../시설별사용량-2번섹션/ANALYZE1.md)). 5번섹션은 2번섹션과 **동일 대상·동일 파라미터**를 받되, 2번섹션이 단일값으로 합쳐버린 PWQ 버킷 차분을 **버킷별로 보존**하여 시계열로 반환하는 점만 다르다.

- 외부 산출물: `backend/image/시설별사용량.png` (5번섹션 — 운영시설별 시계열 영역 차트, 시각 로드 완료)
- 사용자 사전 확정 사항 (plan 단계 AskUserQuestion): (1) 트렌드 값 = **전력량(elceg, kWh)만** (PWI 순시전력 미사용), (2) 응답 구조 = **시설별 시리즈 묶음** `[{facilityId, facilityNm, points:[{baseDtm, elcegVal}]}]`, (3) 빈 버킷 = **있는 버킷만 반환**(생략, 연속 시간축은 frontend 구성).
- **핵심 설계 발견**: 기존 `RawDataRepository.findEnergyDeltaBuckets(pwqTags, start, end, unit)` 가 이미 per-tag 버킷 차분 전체(`List<RawDataBucketDto>{tagSrlNo, baseDtm, aggrVal}`)를 반환한다. 2번섹션 `aggregateEnergy()` 는 이를 `Map<root, BigDecimal>` 로 버킷을 뭉쳐 단일 elceg 를 만들지만, 5번섹션은 `Map<root, TreeMap<baseDtm, BigDecimal>>` 로 버킷을 보존해 합산하면 시계열이 그대로 나온다 → **신규 native query 0건**.

## 회의록 (5인 회의)

### 안건 1: 검색 DTO 재사용 vs 신규 동형복제
- 호출 에이전트: `wtp-backend-engineer`, `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-backend-engineer (블로커 높음)**: 5번섹션은 2번섹션 `FacilityEnergyUsageSearchDto` 와 100% 동일 파라미터·검증(inqUnit/fromDt/toDt, isValid 13개월·YEAR거부, toStartDtm/toEndExclusiveDtm)이나, 해당 DTO 는 `@Schema(description = "시설별 사용량 2번섹션 ...")` 로 섹션 소속이 박혀 있어 5번섹션이 재사용하면 description 이 허위가 되고 `/dev:spec` SPEC 추출 시 2번·5번이 동일 타입으로 노출되어 frontend SPEC 신뢰성이 깨진다. `api-patterns.md §DTO 패턴`("검색 조건 DTO 공통 부모 추상화는 3건 이상 누적 시 별도 ANALYZE") — 현재 2건이므로 추상화 생성 금지, **동형복제(`FacilityEnergyTrendSearchDto`)가 룰 정합**. 사용자 메모리 "사이클 간 자산 자동 원용 금지"와도 정합(원용이 아닌 신규 복제).
  - **wtp-glossary-manager**: 검색 DTO 는 신규 필드 없음 — 어휘 영향 0.
- **결론**: **`FacilityEnergyTrendSearchDto` 신규 동형복제**. inqUnit/fromDt/toDt + isValid(13개월·YEAR거부) + toStartDtm/toEndExclusiveDtm. `@Schema` description 은 5번섹션 맥락으로 작성. `FacilityEnergyUsageSearchDto` 상속·재사용 금지.

### 안건 2: `validDeltaOrNull` · `OPERATION_TYPES` 동형복제 3번째 — 공통 추출 시점
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer (권고 중간)**: `validDeltaOrNull`(음수 차분 제외 3라인 순수함수)·`OPERATION_TYPES`(FacilityType→OPERATION 파생 1라인 static)가 2번섹션·`PeakEnergyTrendService` 에 이어 3번째 동형복제가 된다. 그러나 `coding-discipline.md §2`("일회성 코드를 위해 추상화 계층을 만들지 않는다")·`api-patterns.md §DTO 패턴`("3건 이상 누적되어 공통화 이득이 명확해지면 별도 `/dev:analyze`") 관점 — 지금 Helper/Util 클래스를 신설해 추출하면 오히려 §2 "일회성 추상화 계층 금지" 위반이다. **동형복제 유지 + Javadoc 에 동형복제 카운트 명기**가 정합.
- **결론**: `validDeltaOrNull`·`OPERATION_TYPES` 동형복제 유지. Javadoc 에 "N번째 동형복제 — 공통 추출은 별도 ANALYZE" 명기. 공통 추출은 본 작업 범위 외(별도 ANALYZE 안건).

### 안건 3: 응답 DTO 명명 + 표준 용어
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager (블로커 0)**: 신규 등장 식별자(`FacilityEnergyTrendDto`·`EnergyTrendPoint`·`FacilityEnergyTrendService`·`points`·`baseDtm`·`elcegVal`)는 전부 클래스/변수명 — DB 컬럼 아님. `elceg`(2026-04-25)+`val`(2026-05-18)→`elcegVal` 기존 조합(`PeakEnergyTrendPoint.elcegVal` 선례), `base`+`dtm`→`baseDtm` 기존 조합. `trend`(추세)는 표준 단어 등록 불필요(DB 컬럼 조합 재료 아님, `PeakEnergyTrend*` 미등록 선례). `points` 변수명은 비즈니스 도메인 약어 `point`(분기점 시설)와 층위 다름(변수 vs 비즈니스 도메인) — 충돌 없음(`raw`/`pump` 층위 공존 선례). **신규 표준 단어/데이터 도메인/비즈니스 약어/표준 용어 전부 0건, 룰 갱신 지시서 불필요**.
- **결론**: `FacilityEnergyTrendDto`/`EnergyTrendPoint`/`elcegVal` 확정. 신규 사전 등록 0건, 룰 갱신 불필요, PLAN 직행 가능.

### 안건 4: 도메인 4영역 + PWQ 차분 정책 + 음수차분 생략 + 운영시설 필터
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert (블로커 0, 참고 1)**: 도메인 4영역(알람·인터록·운전모드·이력) 전부 비해당(사유 각각 명기 — 읽기 전용 SELECT 경로, 제어/쓰기 없음). PWQ GOOD-only raw_val 차분(corr_val·HLV 미사용)은 `ot-integration.md §3` PWQ 정책 완전 정합 — 2번섹션 합산의 버킷별 중간 단계를 그대로 노출하는 구조라 추가 도메인 리스크 없음. 음수차분 제외로 빈 버킷이 생기는 것은 "데이터 부재(리셋/장애 구간)"의 정직한 표현으로 수용 가능. 운영시설 `facility_type_cd IN (OPERATION 8종)` 필터는 `entity-patterns.md §facility_type_cd 필터 강제` 정확한 적용. **참고**: 빈 버킷을 frontend 차트가 "0kWh 보간"하면 과소표출 오인 → SPEC 명세에 "응답 부재 버킷 = null, 0 보간 금지" 명시 권고(도메인 정책 아닌 명세 기술).
- **결론**: 도메인 4영역 전부 비해당. PWQ 차분·운영시설 필터 정합. SPEC 권고("0 보간 금지")는 `/dev:spec` 단계 반영 사항으로 기록.

### 안건 5: HOUR 기간 상한 × 집계단위 + 재사용 쿼리 정합 + DB 스키마 0
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer (블로커 높음)**: 재사용 쿼리(`findEnergyDeltaBuckets`)는 `idx_rawdata_1m_h_tag_time(tag_srl_no, acq_dtm DESC)` 정합 + `acq_dtm` 범위로 파티션 프루닝 정상, 2번섹션에서 운영 검증된 쿼리를 동일 입력으로 재호출하므로 **DB 부하 추가 리스크 없음**. DB 스키마 변경 0건 — DBA 2차 승인 대상(`DOM_*`) 없음 확인. **블로커**: 5번섹션은 버킷 전체를 반환하므로 inqUnit=HOUR + 396일 = 태그당 ~9504버킷 × 운영시설 8종 = 수만 행이 단일 응답에 실린다 → `query-tuning.md §1`("시계열 대용량 조회는 페이지네이션/커서") 관점 응답 크기 우려. HOUR 단위 별도 상한(권고 90일) 또는 응답 행수 guard 권고.
- Round 2 (블로커 → 사용자 결정): 오케스트레이터가 HOUR 기간 상한 정책을 사용자에게 질의.
  - **사용자 결정**: **13개월 동일 유지**. 1번섹션 파라미터를 2번·5번이 공유하므로 검증 일관성 우선(5번만 더 빡센 상한 시 "2번은 1년 되는데 5번은 거부" UX 혼란). HOUR+장기간 응답 크기는 frontend 가 HOUR 를 짧게 쓰는 관행으로 자연 완화, DB 부하는 2번섹션과 동일(이미 검증).
- **결론**: 조회기간 상한 = **2번섹션과 동일 13개월(396일)** (`FacilityEnergyTrendSearchDto.isValid` 동형). 별도 HOUR 상한·응답 행수 guard 미도입(over-engineering 회피, `coding-discipline.md §2`). HOUR+장기간 응답 크기는 가정 섹션에 명시(향후 모니터링 — frontend 사용 패턴 관찰 후 별도 사이클 재검토 가능).

## 표준 사전 카탈로그

### 신규 표준 단어
**없음** — 식별자 구성 단어 전부 기존 등록(`elceg` 2026-04-25 · `val` 2026-05-18 · `base` 2026-04-25 · `dtm` 2026-04-23 · `facility`/`nm`/`id`). `trend`·`point`(변수명)는 DB 컬럼 조합 재료 아님 → 등록 불필요(`PeakEnergyTrend*` 미등록 선례).

### 신규 표준 데이터 도메인
**없음** — DB 컬럼 신설 0건. 응답 DTO 필드 타입은 `BigDecimal`(DOM_QTY_15_4 대응)·`LocalDateTime`(DOM_DTM 대응)·`String`(DOM_ID_36/DOM_NAME_100 대응) 재사용.

### 신규 표준 용어
**없음** — `elcegVal`·`baseDtm`·`facilityId`·`facilityNm`·`points` 는 응답 DTO 필드(DB 컬럼 아님) → `standard-terms.md` 등록 의무 없음(`PeakEnergyTrendPoint.elcegVal` 등 DTO 전용 필드 선례 동형).

## 신규 엔티티/DB 컬럼

**없음** — 읽기 전용 조회 API. 신규 엔티티·DB 테이블·컬럼·인덱스 0건. 추가 산출물은 응답 DTO 1종(`FacilityEnergyTrendDto`+중첩 `EnergyTrendPoint`), 검색 DTO 1종(`FacilityEnergyTrendSearchDto`), Service 1종(`FacilityEnergyTrendService`), Controller 엔드포인트 1개(`GET /api/facility/energy-trend`). **신규 ErrorCode 0건**(`FacilityErrorCode.INVALID_SEARCH_PERIOD` 재사용). **신규 Repository 메서드 0건**(`findEnergyDeltaBuckets`·`findByFacilityTypeIn...`·`findByFacilityFacilityIdIn...`·`findByInstrumentInstrumentIdIn...` 전부 기존 재사용).

## 기존 사전·패턴과의 충돌

| 충돌 후보 | 판정 | 해소책 |
|----------|------|--------|
| `FacilityEnergyUsageSearchDto` 재사용 | 회피(블로커 해소) | 신규 `FacilityEnergyTrendSearchDto` 동형복제 — @Schema description SPEC 오염 방지, 검색 DTO 공통화 3건 미만 |
| `validDeltaOrNull`·`OPERATION_TYPES` 3번째 동형복제 | 수용 | 동형복제 유지 + Javadoc 카운트 명기. 지금 추출 시 §2 일회성 추상화 금지 위반. 공통 추출은 별도 ANALYZE |
| `trend`/`points` vs 비즈니스 도메인 약어 `point` | 충돌 없음 | 층위 다름(클래스/변수 vs 비즈니스 도메인). `trend` DB 컬럼 아님 → 등록 불필요 |
| HOUR+장기간 응답 크기 (DBA 블로커) | 수용(사용자 결정) | 13개월 동일 유지. 별도 guard 미도입(단순성·일관성). 가정 섹션 명시 |
| `facility_type_cd` 필터 강제 | 위반 아님 | 시리즈 대상 OPERATION 8종 IN 필터 명시가 룰 준수 |
| 시계열→마스터 FK 금지 | 충돌 없음 | 신규 쿼리 0, 기존 `findEnergyDeltaBuckets` 재사용(이미 정합 검증) |

## PLAN 으로 전달할 결정 사항

**도메인 모델**: 신규 엔티티 없음. `FacilityType.getGroup()==OPERATION` enum 필터로 `OPERATION_TYPES`(PRSF/WTBLD/CHMB/ACFB/POZB/FLTB/DEWB/SOLAR) 도출(2번섹션 동형 동형복제, 하드코딩 회피 SSOT).

**API 구조** (facility 도메인):
- `FacilityController` 엔드포인트 1개 추가 — `GET /api/facility/energy-trend`, `@ModelAttribute FacilityEnergyTrendSearchDto`, `@Tag("06. 시설물 관리")`, 응답 `ResponseEntity<CommonResponseDto<List<FacilityEnergyTrendDto>>>`, `@ApiResponses` 200/400/401/403/500, `@ArraySchema(schema = @Schema(implementation = FacilityEnergyTrendDto.class))`. `energy-usage`(L434) 바로 다음 배치.
- `FacilityEnergyTrendSearchDto` (facility.dto) — inqUnit(InqUnit)·fromDt·toDt(LocalDate) + `toStartDtm()`/`toEndExclusiveDtm()`/`isValid()`(13개월·YEAR거부). `FacilityEnergyUsageSearchDto` 동형복제(상속·재사용 금지). `@Schema` description 5번섹션 맥락.
- `FacilityEnergyTrendDto` (facility.dto) — outer: facilityId·facilityNm·`List<EnergyTrendPoint> points`. inner `static class EnergyTrendPoint`: baseDtm(LocalDateTime, `@JsonFormat yyyy-MM-dd HH:mm:ss`)·elcegVal(BigDecimal). BaseAuditResponseDto 미상속, @Getter + private 생성자 + 정적팩토리 `of(...)`. outer `points` 에 `@ArraySchema(schema = @Schema(implementation = EnergyTrendPoint.class))` 필수. `PeakEnergyTrendDto` outer+inner Point 패턴 미러링.
- `FacilityEnergyTrendService` (facility.service) — `@Transactional(readOnly=true)`. 흐름: isValid 검증(INVALID_SEARCH_PERIOD) → operatingRoots 조회(empty→[]) → rollupResolver.resolveRootByFacility → instruments 조회 → **PWQ 태그만** 수집 + pwqTagToRoot 맵 → `findEnergyDeltaBuckets(pwqTags, start, end, unit)` → `Map<root, TreeMap<baseDtm, BigDecimal>>` 음수차분(validDeltaOrNull) 제외 후 merge → operatingRoots 순서 조립(데이터0→points=[]). 퍼블릭 메서드 50줄 이내 위해 `collectPwqTags(...)`·`aggregateTrendByRoot(...)`·`assembleSeriesList(...)` private 헬퍼 분해. `validDeltaOrNull`·`OPERATION_TYPES` 동형복제 + Javadoc 카운트 명기.

**재사용 (수정 없음)**: `FacilityOperatingRollupResolver.resolveRootByFacility()`, `RawDataRepository.findEnergyDeltaBuckets`, `FacilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc`, `InstrumentRepository.findByFacilityFacilityIdInAndUseYn`, `TagRepository.findByInstrumentInstrumentIdInAndUseYn`, `FacilityErrorCode.INVALID_SEARCH_PERIOD`, `FacilityType`/`FacilityGroup`, `TagMeasurementType.PWQ`, `InqUnit`.

**DB 설계 변경**: 없음(스키마 변경 0). `docs/ddl/` 갱신 불필요.

**적용 패턴**: 2번섹션 `FacilityEnergyUsageService`(시설 N × 태그 IN절 in-memory 집계) 미러링하되 **PWQ 단일 갈래 + 버킷 보존 시계열**. `PeakEnergyTrendService`(TreeMap 버킷 합산 + validDeltaOrNull)·`PeakEnergyTrendDto`(outer+inner Point+정적팩토리) 응답 패턴 미러링.

**SPEC 권고(`/dev:spec` 단계 반영)**: 응답 부재 버킷 = null/데이터 없음, frontend 0 보간 금지 명시(domain-expert 권고).

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 트렌드 값 = 전력량(elceg, kWh)만, PWI 순시전력 미사용 | 결정 | 사용자 확정(plan AskUserQuestion) |
| 응답 구조 = 시설별 시리즈 묶음 `[{facilityId, facilityNm, points:[...]}]` | 결정 | 사용자 확정(plan AskUserQuestion) |
| 빈 버킷 = 있는 버킷만 반환(생략), 연속 시간축은 frontend 구성 | 결정 | 사용자 확정. SPEC 에 "0 보간 금지" 명시(domain-expert 권고) |
| 조회기간 상한 = 13개월(396일), HOUR 단위도 동일(별도 상한 미도입) | 결정 | 사용자 확정(ANALYZE AskUserQuestion). 1번섹션 파라미터 2번·5번 공유 일관성 |
| HOUR+장기간 응답 크기(시설당 ~9504버킷 × 8종) | 미해결(모니터링) | DBA 블로커 수용 결정. frontend 사용 패턴 관찰 후 응답 크기 문제 발생 시 별도 사이클에서 guard/페이지네이션 재검토 |
| 검색 DTO = `FacilityEnergyTrendSearchDto` 신규 동형복제(재사용 금지) | 결정 | 안건 1 — SPEC 오염 방지 |
| 롤업 귀속 = 각 태그를 최근접 운영시설 조상에 귀속(중복 합산 방지), 멤버십 = 운영시설+하위 모든 시설 계측기 | 가정(상속) | 2번섹션 approved 동작 그대로 상속(`FacilityOperatingRollupResolver`) |
| 음수 차분(PWQ 카운터 리셋) = 전량 제외 + WARN 로그 | 결정 | 2번섹션·`PeakEnergyTrendService` 동형 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 운영시설별 PWQ 버킷 차분이 운영루트로 버킷별 합산되어 시계열(points) 반환 | 신규 단위 테스트 — 2태그 같은 버킷 합산값이 해당 baseDtm point 로 산출 GREEN |
| 음수 차분(카운터 리셋) 버킷 제외 | 신규 단위 테스트 — 음수 delta 버킷이 points 에서 제외 GREEN |
| 데이터 0 시설 → points 빈 리스트 | 신규 단위 테스트 — GOOD 0행 시설 → points=[] GREEN |
| 재귀 하위 시설 태그가 최근접 운영조상 시리즈에 귀속 | 신규 단위 테스트 — 트리 매핑 검증 GREEN |
| points TreeMap baseDtm 오름차순 정렬 | 신규 단위 테스트 — 반환 points 가 baseDtm ASC GREEN |
| isValid 실패(YEAR·기간 역전·13개월 초과) → INVALID_SEARCH_PERIOD | 신규 단위 테스트 — 검증 실패 시 RestApiException(INVALID_SEARCH_PERIOD) GREEN |
| 시리즈 대상 = OPERATION 8종만(STORAGE/NETWORK 미출현) | 신규 단위 테스트 — 결과 facility 전부 OPERATION GREEN |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | `rawdata_1m_h` 기수집 데이터를 SELECT·버킷 집계·반환하는 읽기 전용 경로. 알람 임계값 평가·전이 조건·`alarm_h` 기록 경로 무접촉 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령(아웃바운드) 발행 경로 없음. 읽기 전용 조회 — 기동 선행조건 검사 대상 아님 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 컬럼·`ai_drvn_mod_p`/`_h` 테이블 무접근. 모드 전환 트리거·강제 전환 판정 없는 순수 집계 조회 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h.transition_reason`·`pump_ctrl_h` 무접촉. DB 쓰기 경로 전무(`rawdata_1m_h` 조회 전용) |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

**없음** — 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 도메인 약어 등록 0건, DB suffix·엔티티 패턴 변경 0건, DB 스키마 변경 0건. 모든 어휘가 기존 등록 항목 재사용이므로 룰 파일 갱신이 불필요하다. (`wtp-glossary-manager` Round 1 확정 — 신규 등록 항목 없음, PLAN 직행 가능.)

## 산출물
- [계획안](../../../plan/20260608/시설별사용량-5번섹션/PLAN1.md) (다음 단계 `/dev:plan` 에서 작성)
