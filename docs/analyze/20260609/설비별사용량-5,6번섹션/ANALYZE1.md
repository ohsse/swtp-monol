---
status: approved
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 5·6번섹션 — 설비별 누적 전력량 + 분포율 조회 도메인 분석

## 작업 배경

`backend/image/설비별사용량.png` 대시보드의 **5번섹션**(설비별 합계 — 가로 막대)과 **6번섹션**(분포 — 도넛)을 **하나의 읽기 전용 API** 로 구현한다. 1번섹션 파라미터(시작일자·종료일자 `from~to`)와 2번섹션에서 선택한 **시설ID**(`facilityId`)를 받아, 선택 시설을 루트로 `parent_facility_id` self-FK 를 **재귀 탐색**(하위 시설 전체)한 뒤 그 시설들에 속한 활성 계측기(설비)의 **적산전력 태그(PWQ)** 로 `from~to` 기간 **설비별 누적 전력량(kWh)** 과 **분포율(%)** 을 산정해 반환한다.

- 5번섹션 = 설비별 누적 전력량.
- 6번섹션 = 설비별 분포율(%) = `[설비 전력량 / 전체 설비 전력량] × 100`. 전체 = 같은 재귀 하위의 모든 설비 합.
- 외부 산출물: `backend/image/설비별사용량.png` (5·6번섹션 영역, 시각 로드 완료).

### 사용자 사전 확정 사항 (plan 단계 AskUserQuestion, 2026-06-09)
1. **전력량 산정 = 일(DAY) 버킷 합산** — `findEnergyDeltaBuckets` 를 내부 DAY 단위로 호출, 일별 양수 차분만 합산(리셋/롤오버 강건, 신규 SQL 0건). `inqUnit` 미노출 — `from`/`to` 만 수신.
2. **응답 = 래퍼 + 전체합계** — `{ unit, totalElceg, items:[...] }`.
3. **0 설비 포함** — PWQ 태그 보유 설비는 기간 전력량 0/무데이터라도 0 kWh / 0% 로 포함.
4. **헬퍼 3번째 사본 = 동형 복제 유지** (회의 후 추가 결정, 2026-06-09) — `collectSubtree`·`validDeltaOrNull` 을 신규 서비스에 동형 복제하고 본 ANALYZE 결정을 인용 주석으로 명기. 공통 추출 보류.

> 메모리 `feedback_no_auto_reuse_cross_cycle.md` / `feedback_section_cycle_discard_policy.md` 정합 — 동형 선례(`FacilityPowerInstrumentService`·`FacilityEnergyUsageService`·`InstrumentEnergyTrendService`)의 자산을 자동 원용하지 않고 **구조만 미러링한 신규 자산**으로 작성.

### 핵심 발견 — 중복 아님 / 동형 선례
- 3번섹션 `FacilityPowerInstrumentService`(facility) — 재귀 하위 트리 + 전력태그 보유 계측기 **목록**(PWI∪PWQ). 본 작업은 그 멤버십에 **PWQ 전력량 집계 + 분포율** 을 결합한 별개 산출물.
- 2번섹션 `FacilityEnergyUsageService`(facility) — 운영시설 8종 **시설별** 전력량 4지표. 본 작업은 **설비(계측기)별** + **분포율** + **단일 시설 재귀 스코프** 로 좁힌 변형(그룹 키 root→instrument).
- 4번섹션 `InstrumentEnergyTrendService`(instrument) — 단일 계측기 PWQ **시계열**. 본 작업은 재귀 하위 다수 계측기의 **기간 단일 총합** + 분포율.

## 회의록 (5인 회의)

### 안건 1: 신규 어휘 정합성 (DTO 클래스·필드명·URL 세그먼트)
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: DB 컬럼 신설 0건 전제 → 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 약어 **0건**. DTO 필드 의미 단어 `total`(2026-06-05)·`elceg`(2026-04-25)·`unit`(2026-05-03)·`rate`(2026-06-02) 전부 기존 등록 재사용. DTO 필드명(`totalElceg`·`elceg`·`ratio`·`unit`)·URL 세그먼트(`instrument-energy-usage`)는 **어휘 사전 관리 대상 외**(4번섹션 `elcegVal`·`inqUnit` 종결 선례 동형). `ratio`(분포율)는 기존 `rate` 와 **동의어** 이나 DB 컬럼 신설이 없어 표준 단어 등록 사유 자체가 없음 → DTO 필드명 `ratio` 유지·미등록. `usage`·`energy` 도 URL 전용·미등록.
- **결론**: 신규 등록 **0건**(단어·데이터 도메인·표준 용어·비즈니스 약어 모두). 룰 갱신 지시서 **0건**. 직전 2·3·4번섹션과 동일하게 어휘 갱신 0건으로 종결.

### 안건 2: 계층 책임·패턴 (SearchDto·Controller·헬퍼 사본·DTO·ErrorCode)
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약 (블로커 2·권고 2·참고 1):
  - **① 헬퍼 3번째 사본 (블로커)** — `collectSubtree`(FacilityDownstreamTreeResolver→FacilityPowerInstrumentService→본 작업)·`validDeltaOrNull`(PeakEnergyTrendService→FacilityEnergyUsageService→본 작업) 가 3번째 사본. `FacilityPowerInstrumentService.java:38` 주석이 "공통 추출은 사본 3건 이상 누적 시 별도 ANALYZE" 를 자체 선언 → 본 ANALYZE 에서 추출 vs 복제를 **명시 결정** 의무. 인용 근거 없는 3번째 복제는 `coding-discipline.md §3` 위반.
  - **② `@ArraySchema`/`@Schema(implementation)` (블로커)** — `items`(List, element 사용자 정의)에 `@ArraySchema(schema=@Schema(implementation=InstrumentEnergyUsageItem.class))`, `equipTypeCd`(EquipType)에 `@Schema(implementation=EquipType.class)` 필수(`api-patterns.md §DTO @Schema(implementation)`). 누락 시 frontend SPEC 빈 스키마.
  - **③ SearchDto 신규 vs 재사용 (권고)** — from/to 전용 신규 동형 DTO 는 "요청되지 않은 유연성" 우려 있으나, `api-patterns.md` SearchDto 공통 추상화 "3건+ 누적 시 ANALYZE" 미충족 + 화면·SPEC 경계 분리(4번섹션 `InstrumentEnergyTrendSearchDto` 동형 복제 선례) → 신규 분리 허용, 사유 명기 필요.
  - **④ Controller `@Tag` (권고)** — `/{facilityId}/instrument-energy-usage` 는 `/{facilityId}` GET 과 경로 상이로 ambiguous-mapping 없음. 신규 컨트롤러(@Tag 15)는 4번섹션 분리 선례로 허용. 단 3번섹션이 FacilityController(@Tag 06)에 있어 화면 기능이 두 태그로 분산되는 기존 불일치 확인 권고.
  - **⑤ 메서드 50줄 (참고)** — BFS+집계+합산 단일 메서드 집중 시 50줄 초과 가능 → 헬퍼 분리로 §2.1 준수 의무.
- **결론**: 블로커 ①은 **동형 복제 유지**(사용자 결정 2026-06-09, 인용 주석 명기)로 해소 — 2·3·4번섹션 패턴 + 메모리 `feedback_no_auto_reuse_cross_cycle.md` 정합. 블로커 ②는 PLAN/TASK 체크박스로 `@ArraySchema`/`@Schema(implementation)` 작성 강제로 해소(본 작업 범위 포함 → 빌드·SPEC 검증). 권고 ③④는 신규 SearchDto·신규 Controller(@Tag 15) 채택, 3번섹션 @Tag 06 불일치는 기존 자산 비침습(미수정). 참고 ⑤는 헬퍼 분리 구현.

### 안건 3: 도메인 규칙 정합성 (4영역 + PWQ 차분 + 종류 무필터 + 0 설비)
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약 (블로커 0·참고 2):
  - **① 도메인 4영역** — 알람 4단계·인터록·AI 운전 모드·이력 기록 의무 전부 **비해당**. 읽기 전용 GET, 집계 결과 표출, 제어 발행/모드 전환/이력 INSERT 경로 없음, 신규 엔티티/DB 컬럼 0 → "비해당 근거 타당성" 통과 조건(구체 사유 + 신규 엔티티 없음) 동시 충족.
  - **② PWQ 차분 정책 (통과)** — GOOD only `raw_val` 차분·`corr_val` 미사용·HLV 미적용·음수 제외가 `ot-integration.md §3` PWQ 행 정합. DAY 버킷 합산은 전체기간 단일 MAX-MIN 대비 리셋/롤오버 강건성 우월 → 설계 적절.
  - **③ 종류 무필터 (통과)** — `equip_type_cd` 필터 강제 룰(`ot-integration.md §5`·`entity-patterns.md`)은 자식 종류별 도메인 룰이 다른 제어·평가 시나리오 한정. 본 API 는 전력량 표출 읽기 전용 → 적용 범위 밖.
  - **④ 0 설비 포함 (통과)** — `coding-discipline.md §2` "발생 불가능 시나리오 예외 처리 금지"는 불필요 방어 배제 룰. 0 kWh 설비는 실발생 케이스(기간 내 데이터 없음) → 충돌 없음.
  - **⑤ 음수 차분 WARN 로그 경로 (참고)** — 음수·null 제외 시 경고 기록 위치 미명시. `alarm_h` 이상치 기록 경로와 무관한 단순 애플리케이션 `WARN` 로그로 충분하다는 사유를 가정 섹션 명기 권고.
  - **⑥ 분포율 합 100 미일치 (참고)** — 독립 반올림 오차로 합계 99.x/100.x 가능. 안전 수치 아니므로 도메인 위반 아님. `@Schema(description)` 에 "반올림 오차로 합계가 정확히 100.0 이 아닐 수 있음" 명기 권고.
- **결론**: 블로커 0. 4영역 전부 비해당·PWQ 정합·종류 무필터 정합·0 설비 정합. 참고 2건은 가정 섹션 결정(WARN = 단순 앱 로그) + `@Schema` 명세로 반영.

### 안건 4: DB 쿼리·인덱스·격리 (스키마 변경 0)
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약 (블로커 0·권고 2·참고 1):
  - **통과** — 스키마·인덱스·데이터 도메인 변경 0건, DBA 2차 승인 대상 0건. `findEnergyDeltaBuckets` 의 `tag_srl_no IN`(등가) 선행 + `acq_dtm` 범위(후위)가 `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 정합(`indexing-and-migration.md §1`). `acq_dtm` 범위로 월 RANGE 파티션 프루닝 동작. BFS 레벨당 1쿼리 + IN 집계 구조 N+1 없음, `instrument.getFacility().getFacilityId()` 프록시 식별자 접근(추가 쿼리 0).
  - **① 격리 수준 (권고)** — `query-tuning.md §1` 집계 팬텀 대응. 과거 확정 구간은 READ_COMMITTED 안전하나, `to` 가 오늘(2026-06-09 등)을 포함하면 진행 분(分) 버킷 동시 INSERT 팬텀 가능 → PLAN 에서 `end < now()` 강제 또는 REPEATABLE_READ 적용 여부 명시 결정 의무.
  - **② 슬로우 쿼리 (권고)** — 다수 PWQ 태그 × 396일 조회 시 IN 카디널리티 확대로 스캔량이 4번섹션(단일 태그)의 N배. `query-tuning.md §2` EXPLAIN (ANALYZE, BUFFERS) 검증을 PLAN 성공 기준 편입 권고.
  - **③ 빈 IN 절 방어 (참고)** — PWQ 인메모리 필터 후 태그 0건이면 `findEnergyDeltaBuckets` IN 빈 배열 → 일부 드라이버 문법 오류. 빈 태그 목록 사전 검사 후 즉시 빈 결과 반환 권고.
- **결론**: 블로커 0. 스키마 변경 0·신규 인덱스 불필요·DBA 2차 승인 0. 격리 수준은 **동형 선례(4번섹션·2번섹션 readOnly 기본 READ_COMMITTED) 유지** — 과거 구간 집계의 진행 분(分) 1버킷 미세 팬텀은 표시용 집계상 무시 가능, `end < now()` 미강제(현재일 부분 버킷 허용)를 가정 섹션 결정으로 기재. 슬로우 쿼리 EXPLAIN 검증은 선택적 수동 검증(성공 기준 후보). 빈 IN 절 방어는 설계 반영(PWQ 0건·계측기 0건 → 빈 래퍼 즉시 반환).

## 표준 사전 카탈로그

### 신규 표준 단어
**없음** — `total`·`elceg`·`unit`·`rate`(표준 단어) 전부 기존 등록 재사용. `ratio`·`usage`·`energy` 는 DTO 필드명·URL 세그먼트 전용으로 DB 컬럼 조합 재료 아님 → 미등록(`ratio` vs `rate` 동의어는 DB 컬럼 미신설로 충돌 판정 대상 외).

### 신규 표준 데이터 도메인
**없음** — DB 컬럼 신설 0건. DBA 2차 승인 대상 0건.

### 신규 표준 용어
**없음** — DTO Java 필드명(`totalElceg`·`elceg`·`ratio`·`unit` 등)은 DB 컬럼이 아니므로 `standard-terms.md` 등록 의무 없음(4번섹션 선례 동형).

## 신규 엔티티/DB 컬럼

**없음** — 읽기 전용 조회 API. 신규 엔티티·DB 테이블·DB 컬럼·인덱스·마이그레이션 **0건**. 추가 산출물은 (1) `FacilityInstrumentEnergyUsageSearchDto` 신규 1개, (2) `FacilityInstrumentEnergyUsageDto`(+ 중첩 `InstrumentEnergyUsageItem`) 신규 1개, (3) `FacilityInstrumentEnergyUsageService` 신규 1개, (4) `FacilityInstrumentEnergyUsageController` 신규 1개, (5) `FacilityInstrumentEnergyUsageServiceTest` 단위 테스트 1개. 기존 리포지토리(`FacilityRepository.findById`·`findByParentFacilityIdInAndUseYn`·`InstrumentRepository.findByFacilityFacilityIdInAndUseYn`·`TagRepository.findByInstrumentInstrumentIdInAndUseYn`·`RawDataRepository.findEnergyDeltaBuckets`)·enum(`TagMeasurementType.PWQ`·`EquipType`·`YnType`)·`FacilityErrorCode.FACILITY_NOT_FOUND`/`INVALID_SEARCH_PERIOD`·`idx_rawdata_1m_h_tag_time` 전부 변경 없이 재사용.

## 기존 사전·패턴과의 충돌

| 충돌 후보 | 판정 | 해소책 |
|----------|------|--------|
| `ratio` vs `rate`(표준 단어) 동의어 | 충돌 없음 | DB 컬럼 미신설 → DTO 필드명 전용, 사전 등록 대상 외 (glossary 안건 1) |
| `collectSubtree`·`validDeltaOrNull` 3번째 사본 | 미결(블로커→결정) | **동형 복제 유지** (사용자 결정 2026-06-09) — 본 ANALYZE 인용 주석 명기. working code 비침습, 2·3·4번섹션 패턴 정합 (backend 안건 2-①) |
| `@ArraySchema`/`@Schema(implementation)` 누락 | 블로커→TASK 강제 | `items`/`equipTypeCd` 어노테이션 PLAN/TASK 체크박스 명기 (backend 안건 2-②) |
| SearchDto 재사용(inqUnit 보유) vs 신규 | 권고→결정 | 신규 `FacilityInstrumentEnergyUsageSearchDto`(from/to 전용, 비상속) — 화면·SPEC 분리, 4번섹션 동형 복제 선례 (backend 안건 2-③) |
| 컨트롤러 @Tag 06(3번섹션) vs @Tag 15(신규) | 기존 불일치(비침습) | 신규 컨트롤러 @Tag 15 채택, 3번섹션 FacilityController 미수정 (backend 안건 2-④) |
| `equip_type_cd` 필터 강제 룰 vs 종류 무필터 | 위반 아님 | 전력량 표출 읽기 전용 — 자식 종류별 도메인 룰 분기 없음 (domain 안건 3-③) |
| 음수 차분(적산 리셋·롤오버) 처리 | 결정 | 음수·null 버킷 제외(생략)+WARN 단순 앱 로그. `FacilityEnergyUsageService.validDeltaOrNull` 동형, `ot-integration.md §3` 정합 (domain 안건 3-②·⑤) |

## PLAN 으로 전달할 결정 사항

**도메인 모델**: 신규 엔티티 없음. 멤버십 = 선택 시설(`facilityId`) 재귀 하위 트리(루트 inclusive) → 활성 계측기 → 활성 PWQ 태그 → `findEnergyDeltaBuckets("day")` 일 버킷 차분(음수·null 제외) → instrumentId 합산 → totalElceg 합 → 분포율 → 단일 래퍼.

**API 구조** (facility 도메인, api 모듈):
- 신규 `FacilityInstrumentEnergyUsageSearchDto` — 필드 `fromDt`·`toDt`(LocalDate, `@DateTimeFormat ISO.DATE`). `@Getter @Setter @NoArgsConstructor` + `@ModelAttribute`. `MAX_PERIOD_DAYS=396L`. `isValid()`(non-null + `!fromDt.isAfter(toDt)` + 간격 ≤ 396일, **inqUnit/YEAR 검사 없음**)·`toStartDtm()`·`toEndExclusiveDtm()`. `FacilityEnergyUsageSearchDto` 에서 inqUnit 제거한 동형(비상속).
- 신규 `FacilityInstrumentEnergyUsageDto` — `@Getter` + private 생성자 + 정적 팩토리 `of(...)`, `BaseAuditResponseDto` 미상속. 필드 `unit`("kWh")·`totalElceg`(BigDecimal)·`items`(`List<InstrumentEnergyUsageItem>`, **`@ArraySchema(schema=@Schema(implementation=InstrumentEnergyUsageItem.class))`**). 중첩 정적 `InstrumentEnergyUsageItem{instrumentId·instrumentNm·equipTypeCd(EquipType, **`@Schema(implementation=EquipType.class)`**)·facilityId·facilityNm·elceg(BigDecimal)·ratio(BigDecimal, **`@Schema(description` 에 "반올림 오차로 합계가 정확히 100.0 이 아닐 수 있음" 명기**)}.
- 신규 `FacilityInstrumentEnergyUsageService`(`@Service @RequiredArgsConstructor @Transactional(readOnly=true) @Slf4j`). 의존 `FacilityRepository`·`InstrumentRepository`·`TagRepository`·`RawDataRepository`. 상수 `POWER_ENERGY_TYPE=PWQ`·`UNIT_KWH="kWh"`·`BUCKET_UNIT=InqUnit.DAY.getDateTruncUnit()`·`MAX_DEPTH=10`·`HUNDRED`·`RATE_SCALE=1`. public `findInstrumentEnergyUsage(String facilityId, search)` + private 헬퍼(검증·활성시설·재귀수집·PWQ수집·버킷합산·validDeltaOrNull·computeRate·정렬·조립) 분리. 흐름: `isValid()` false → `INVALID_SEARCH_PERIOD` / `findActiveFacilityOrThrow`(findById + useYn==Y, 아니면 `FACILITY_NOT_FOUND`) / `collectSubtree` BFS / 계측기 IN(0건 → 빈 래퍼) / PWQ 태그 IN+필터(0건 → 빈 래퍼) / `findEnergyDeltaBuckets("day")` → `validDeltaOrNull` → `Map<instrumentId, BigDecimal>` 합산 / **PWQ 보유 계측기 전체** 대상 elceg lookup 부재 시 `ZERO`(0 설비 포함) / `totalElceg`=합, `ratio=computeRate(elceg, total)`(scale1 HALF_UP, total=0→`ZERO.setScale(1)`) / 시설 dispOrd→계측기 dispOrd→instrumentNm 정렬 조립. **헬퍼 동형 복제(`collectSubtree`·`validDeltaOrNull`·`findActiveFacilityOrThrow`·`displayOrder`)에 본 ANALYZE 결정 인용 주석 + 다중 PWQ 합산 정책 Javadoc 명기**.
- 신규 `FacilityInstrumentEnergyUsageController`(`@Tag` "15. 설비별 사용량", `/api/facility`, `CommonController` 상속). `@GetMapping("/{facilityId}/instrument-energy-usage")` → `ResponseEntity<CommonResponseDto<FacilityInstrumentEnergyUsageDto>>`. `@PathVariable facilityId` + `@ModelAttribute search`. `@Operation`+`@ApiResponses`(200/400/401/403/404/500, 400=INVALID_SEARCH_PERIOD, 404=FACILITY_NOT_FOUND).

**DB 설계 변경**: **없음**(스키마 변경 0). `docs/ddl/` 갱신 불필요.

**적용 패턴**: `FacilityPowerInstrumentService`(재귀 하위 + 계측기/태그 IN) + `FacilityEnergyUsageService.aggregateEnergy`(PWQ 버킷 차분 합산, 그룹 키 root→instrument) + `PumpCtrlHistoryService.computeRate`(분포율 scale1 HALF_UP) + `InstrumentEnergyTrendController`(컨트롤러 분리) 구조 미러링(자산 자동 원용 없이 신규 작성).

**격리 수준**: 동형 선례 유지 — `@Transactional(readOnly=true)` 기본 READ_COMMITTED. `end < now()` 미강제(현재일 부분 버킷 허용), 진행 분(分) 1버킷 미세 팬텀은 표시용 집계상 무시 가능.

**테스트**: `FacilityInstrumentEnergyUsageServiceTest` Mockito 단위 — 기간 무효(null·역전·>396일)→INVALID_SEARCH_PERIOD / 미존재·비활성 시설→FACILITY_NOT_FOUND / 재귀 하위 BFS 다단계 수집 / 설비별 버킷 합산(다중 PWQ 태그·다설비) / 음수·null 차분 제외 / 비율 = elceg/total×100 scale1 HALF_UP / total=0 → 전 비율 0 / PWQ 보유·전력량 0 설비 포함(0kWh·0%) / 계측기 0건·PWQ 0건 → 빈 래퍼(total 0, items []).

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 표출 측정값 = PWQ(적산전력량, kWh) 한정. PWI(순시전력)는 7번섹션 대상 → 본 API 범위 외 | 결정 | 사용자 "적산전력태그" 명시 |
| 전력량 = 내부 DAY 버킷 차분 합산. `inqUnit` 미노출(from/to 만 수신) | 결정 | 사용자 plan 단계 확정. 리셋/롤오버 강건, `FacilityEnergyUsageService` 선례 |
| **음수 차분(적산 리셋·롤오버)·null 차분 버킷은 제외(생략)** — 제외 시 단순 애플리케이션 WARN 로그(`alarm_h` 미기록) | 결정 | `FacilityEnergyUsageService.validDeltaOrNull` 동형, `ot-integration.md §3` PWQ 정합 (domain 참고 ⑤ 해소) |
| 단일 계측기 다중 PWQ 태그 → instrumentId 버킷 합산 | 결정 | ELCMTR 다채널 대비, 4번섹션 합산 정책 정합 → Javadoc 명기 |
| PWQ 보유·전력량 0/무데이터 설비 → 0 kWh·0% 포함 | 결정 | 사용자 확정. `coding-discipline.md §2` 정합 (domain 안건 3-④) |
| 분포율 독립 반올림(scale1 HALF_UP)으로 합 100.0 미일치 가능 — `@Schema(description)` 명기 | 결정 | 표시 허용, 안전 수치 아님 (domain 참고 ⑥) |
| 격리 수준 READ_COMMITTED 유지(`end < now()` 미강제) | 결정 | 동형 선례 readOnly 기본, 현재일 부분 버킷 미세 팬텀 무시 (dba 권고 ①) |
| PWQ 태그 0건·계측기 0건 → 빈 IN 절 방어(즉시 빈 래퍼) | 결정 | DB 호출 전 사전 검사 (dba 참고 ③) |
| 헬퍼 `collectSubtree`·`validDeltaOrNull` 3번째 사본 → 동형 복제 유지(공통 추출 보류) | 결정 | 사용자 결정 2026-06-09, 인용 주석 명기 (backend 블로커 ①) |
| 슬로우 쿼리 EXPLAIN 검증 = 선택적 수동(성공 기준 후보) | 미해결 → 결정 | 다수 태그 396일 조회 시 스테이징 EXPLAIN 권고 (dba 권고 ②) |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 기간 무효 → INVALID_SEARCH_PERIOD / 미존재·비활성 시설 → FACILITY_NOT_FOUND | 신규 단위 테스트 — null·역전·>396일 / findById empty·useYn=N 각각 RestApiException GREEN |
| 재귀 하위 BFS → 설비별 버킷 합산(다중 PWQ·다설비) | 신규 단위 테스트 — 다단계 children mock → instrumentId 합산값·다중 PWQ 합산 GREEN |
| 음수·null 차분 버킷 제외 | 신규 단위 테스트 — 음수 버킷 elceg 미반영 GREEN |
| 분포율 = elceg/total×100 scale1 HALF_UP / total=0 → 전 비율 0 | 신규 단위 테스트 — ratio 값 일치·total 0 시 0.0 GREEN |
| PWQ 보유·전력량 0 설비 포함 / 계측기·PWQ 0건 → 빈 래퍼 | 신규 단위 테스트 — 0 elceg 0% item 포함 / 빈 items·total 0 GREEN |
| 빌드·전체 테스트 통과 | `./gradlew.bat :api:test` PASS + `./gradlew.bat clean build` BUILD SUCCESSFUL |
| (선택) dev DB 수동 + 슬로우 쿼리 검증 | `GET /api/facility/{PWQ 하위 설비 보유 시설ID}/instrument-energy-usage?fromDt=...&toDt=...` → items elceg·ratio, totalElceg=Σelceg, ratio 합 ≈ 100, dispOrd 정렬 / `EXPLAIN (ANALYZE, BUFFERS)` Index Scan·파티션 프루닝 확인 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | PWQ 측정값 집계 표출 — 알람 임계값·전이·복귀 무접촉, `alarm_h` 기록 없음(음수 차분 제외는 단순 WARN 로그) |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | GET 조회만 — 기동 명령 발생 경로 없음, 선행조건 검사·제어 명령 발행 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 변경 없음, 강제 전환 트리거 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h`·`pump_ctrl_h` 무접촉. facility/instrument/tag 마스터 + rawdata 조회 전용(쓰기 없음) |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

**없음** — 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 도메인 약어 등록 0건, DB suffix·엔티티 패턴 변경 0건, DB 스키마 변경 0건, 신규 ErrorCode 0건. 모든 어휘가 기존 등록 항목 재사용이다(`wtp-glossary-manager` 안건 1 확정 — 직전 2·3·4번섹션 사이클과 동일).

## 산출물
- [계획안](../../../plan/20260609/설비별사용량-5,6번섹션/PLAN1.md) (다음 단계 `/dev:plan` 에서 작성)
