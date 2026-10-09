---
status: approved
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 4번섹션 — 계측기 전력량 트렌드 조회 도메인 분석

## 작업 배경

`backend/image/설비별사용량.png` 대시보드의 **4번섹션**(설비 트렌드, 우상단 영역 차트)을 구현한다. 사용자가 **3번섹션**(전력 계측기 목록, `GET /api/facility/{facilityId}/power-instruments`)에서 선택한 **단일 계측기(`instrumentId`)** 와 **1번섹션 파라미터 3종**(집계 버킷 `[시|일|월]`, 시작일자, 종료일자)을 받아, 조회기간 동안 그 계측기의 **전력량(PWQ 적산전력량, kWh) 시계열**을 단일 시리즈로 반환하는 **읽기 전용** API 다.

- 외부 산출물: `backend/image/설비별사용량.png` (4번섹션 — 설비 트렌드 영역 차트, 시각 로드 완료)
- 사용자 사전 확정 사항 (plan 단계 AskUserQuestion, 2026-06-09):
  1. **설비ID = 계측기 `instrumentId`** — 3번섹션 응답의 instrumentId. 선택한 그 계측기 1대 스코프.
  2. **단일 설비 → 단일 시리즈** — 단일 객체 응답(리스트 아님).
  3. **instrument 도메인** — `GET /api/instrument/{instrumentId}/energy-trend`.

> 메모리 `feedback_no_auto_reuse_cross_cycle.md` / `feedback_section_cycle_discard_policy.md` 정합 — 동형 선례(`PumpPowerTimeSeriesService`·`FacilityEnergyTrendService`)의 자산을 자동 원용하지 않고 **구조만 미러링한 신규 자산**으로 작성. 재사용 범위는 본 ANALYZE 5인 회의로 확정.

### 핵심 발견 — 중복 아님 / 동형 선례
- 기존 `FacilityEnergyTrendService`("시설별사용량 5번섹션")는 **운영시설 8종 전체 멀티시리즈**(BFS+rollup, 특정 ID 입력 없음) → 본 작업(단일 계측기 스코프)과 **스코프가 달라 별개**.
- 가장 가까운 동형 선례: instrument 도메인 `PumpPowerTimeSeriesService`(전체 활성 펌프의 PWQ 전력량 시계열). 본 작업은 이를 **단일 instrumentId 스코프 + 종류 무관**으로 좁힌 변형이다.

## 회의록 (5인 회의)

### 안건 1: 신규 식별자 어휘 정합성 + `energy`/`trend` 네이밍
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 DB 테이블·컬럼·데이터 도메인 0건 전제 → backend `standard-terms.md` 등록 대상 원천 없음. DTO 필드 구성 단어 `elceg`·`val`·`base`·`dtm`·`inq`·`unit`·`nm` 전부 기존 등록(7건 재사용), `instrument` 는 비즈니스 도메인 약어 기존 등록. **`energy`·`trend` 단어** — DTO 클래스명(`InstrumentEnergyTrendDto`)·URL 세그먼트(`/energy-trend`) 전용 일반 영어어휘로 **DB 컬럼 조합 재료로 사용되지 않음** → 신규 등록 불필요. `elceg`(전력량 누적, kWh)가 이미 DB 컬럼 단어이므로 `energy`가 동의어로 컬럼에 사용될 여지 없음(어근 충돌 없음). DTO Java 필드명(`elcegVal`·`baseDtm`·`inqUnit`)·URL 세그먼트는 어휘 사전 관리 대상 외(`hr`·`actl`·`inq`·`facilityGroupCd` DTO 전용 변수명 미등록 선례 동형).
- **결론**: 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 약어 **0건**, 룰 갱신 지시서 **0건**. 직전 3번섹션·2번섹션 사이클과 동일하게 어휘 갱신 0건으로 종결.

### 안건 2: 검증 DTO 신규 vs 재사용 + 컨트롤러 배치 + 계층 책임
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약 (블로커 2·권고 2·참고 1):
  - **① 검증 정책 (블로커)** — `PumpTimeSeriesSearchDto`(YEAR 허용·기간 상한 없음) 재사용은 `rawdata_1m_h` 13개월 보존 정합을 깨므로(396일 초과 조회 허용) **신규 `InstrumentEnergyTrendSearchDto` 필수**. `FacilityEnergyTrendSearchDto`(YEAR 거부+396일 상한)가 같은 이유로 동형 복제한 선례 존재. **상속 관계 만들지 말고 동형 복제**(`MAX_PERIOD_DAYS=396L`·YEAR 거부 동일 유지).
  - **② 컨트롤러 배치 (블로커)** — `InstrumentController`(CRUD)에 시계열 메서드 혼재 시 SRP 위반 + `@Tag("계측기 관리")` 에 설비별사용량 목적 혼입. `PumpTimeSeriesController` 확장도 "전체 펌프" vs "단일 계측기" 책임 혼재. **신규 `InstrumentEnergyTrendController`(@Tag 설비별사용량) 분리**가 올바름. `/{instrumentId}/energy-trend` 리터럴 세그먼트는 `/{instrumentId}` 보다 PathPattern 특이도 우선이라 ambiguous-mapping 미발생.
  - **③ 다중 PWQ 태그 합산 (권고)** — 단일 계측기 PWQ 다건 시 `baseDtm` 기준 `BigDecimal::add` 합산은 타당하나, `PumpPowerTimeSeriesService`(펌프당 첫 태그 가정)와 의도적 차이이므로 **Javadoc에 합산 정책 명기**(`coding-discipline.md §1` 가정 명시).
  - **④ Swagger 어노테이션 (권고)** — `inqUnit` 에 `@Schema(implementation=InqUnit.class)`, `points` 에 `@ArraySchema(schema=@Schema(implementation=...))` PLAN 체크박스 명기 의무.
  - **⑤ ErrorCode (참고)** — `INSTRUMENT_NOT_FOUND`(404)·`INVALID_INQ_PERIOD`(400) 재사용 의미 일치 → 신규 0건 적정.
- **결론**: 신규 `InstrumentEnergyTrendSearchDto`(동형 복제·비상속) + 신규 `InstrumentEnergyTrendController`(@Tag 설비별사용량) 채택. 두 블로커는 "계획된 신규 자산을 실제 작성해야 함"의 확인이며 본 작업 범위에 포함되므로 빌드·테스트 검증으로 해소. 다중 PWQ 합산·`@Schema`/`@ArraySchema` 는 PLAN/TASK 명기.

### 안건 3: 도메인 규칙 정합성 (4영역 + PWQ 정의 + 종류 무필터 + 음수 차분)
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약 (블로커 1·참고 1):
  - **① 도메인 4영역** — 알람 4단계·인터록·AI 운전 모드·이력 기록 의무 전부 **비해당**. 단일 instrumentId + 집계 파라미터로 `rawdata_1m_h` PWQ 버킷 차분만 읽는 읽기 전용 경로 — 제어 발행/모드 전환/이력 INSERT 경로 없음(3번섹션 비해당 종결 선례 동형).
  - **② 종류 무필터 (통과)** — `equip_type_cd` 필터 강제 룰은 "자식 종류별 도메인 룰이 다른 시나리오(제어·평가)"에만 적용. 본 API 는 instrumentId 단일 물리 식별자 직접 지정이라 종류 분기 자체가 성립하지 않음 → **종류 무필터 정합**. PUMP뿐 아니라 ELCMTR(전력계) 등 PWQ 보유 계측기 모두 수용(3번섹션 종류 무필터 선례 동형).
  - **③ PWQ 무보유 빈 시리즈 200 (통과)** — `coding-discipline.md §2` "발생 불가능 시나리오 예외 처리 금지" 정합. 3번섹션이 전력태그 보유 계측기만 노출하므로 4번섹션 PWQ 무보유는 빈 시리즈로 자연 처리.
  - **④ 음수 차분 처리 (블로커)** — `ot-integration.md §3` PWQ 행은 "음수 차분(적산 리셋·롤오버) 제외"를 명시하나 그 제외의 구체 처리(null·스킵·0 클램핑)가 미명세. PLAN/ANALYZE "가정" 섹션에 처리 방침 1건 기재 의무(`coding-discipline.md §1`).
- **결론**: 도메인 4영역 전부 비해당·종류 무필터 정합·빈 시리즈 200 정합. 음수 차분 처리 방침은 동형 선례(`PumpPowerTimeSeriesService`: `signum() >= 0` 필터로 음수·null 버킷 **제외/생략** · `FacilityEnergyTrendService`: 음수면 null 반환 후 merge 스킵 + WARN 로그)가 이미 확립 → **음수·null 버킷 제외(생략) 정책 채택**, 가정 섹션 결정으로 기재해 블로커 해소.

### 안건 4: DB 쿼리·인덱스·격리 (스키마 변경 0)
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약 (블로커 0·권고 1·참고 2):
  - **통과** — 스키마 변경 0건(신규 테이블·컬럼·인덱스·데이터 도메인 0, DBA 2차 승인 대상 0). `findEnergyDeltaBuckets` 의 `tag_srl_no IN` 등가 선행 + `acq_dtm` 범위 후위는 `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 정합(`indexing-and-migration.md §1`). `acq_dtm` 범위로 월 RANGE 파티션 프루닝 동작. N+1 없음(`findById` 1 + 태그 IN 1 + 버킷 1, 루프 내 개별 조회 없음).
  - **① REPEATABLE_READ (권고)** — HOUR 버킷 396일 조회는 단일 집계 쿼리가 ~57만행 스캔 가능, 동시 수집 INSERT 팬텀으로 버킷 차분 왜곡 우려 → `query-tuning.md §1` 기준 격리 수준 PLAN 명시.
  - **② 슬로우 쿼리 (참고)** — HOUR 버킷 장기 조회 p6spy 500ms 초과 가능, PLAN 성공 기준에 `EXPLAIN (ANALYZE, BUFFERS)` 포함 권고.
  - **③ 음수 차분 방어 (참고)** — 안건 3-④와 동일 사안(PLAN 가정 명기).
- **결론**: 블로커 0. 스키마 변경 0·신규 인덱스 불필요·DBA 2차 승인 대상 없음. 격리 수준은 **동형 선례(`PumpPowerTimeSeriesService`·`FacilityEnergyTrendService` 모두 `@Transactional(readOnly=true)` 기본 READ_COMMITTED) 유지** — 과거 구간 시계열 조회로 진행 중 분(分) 1버킷의 미세 팬텀 영향은 무시 가능, REPEATABLE_READ 미적용을 가정 섹션 결정으로 기재. 슬로우 쿼리 검증은 단일 계측기 1태그 기준 부담 낮아 선택적 수동 검증(성공 기준 후보).

## 표준 사전 카탈로그

### 신규 표준 단어
**없음** — `elceg`·`val`·`base`·`dtm`·`inq`·`unit`·`nm`(표준 단어) + `instrument`(비즈니스 도메인 약어) 전부 기존 등록. `energy`·`trend` 는 DTO 클래스명·URL 세그먼트 전용으로 DB 컬럼 조합 재료 아님 → 미등록(`elceg` 어근 충돌 회피).

### 신규 표준 데이터 도메인
**없음** — DB 컬럼 신설 없음. DBA 2차 승인 대상 0건.

### 신규 표준 용어
**없음** — DTO Java 필드명(`elcegVal`·`baseDtm`·`inqUnit` 등)은 DB 컬럼이 아니므로 `standard-terms.md` 등록 의무 없음(`hr`·`actl`·`inq`·`facilityGroupCd` DTO 전용 변수명 미등록 선례 동형).

## 신규 엔티티/DB 컬럼

**없음** — 읽기 전용 조회 API. 신규 엔티티·DB 테이블·DB 컬럼·인덱스 **0건**. 추가 산출물은 (1) `InstrumentEnergyTrendSearchDto` 신규 1개, (2) `InstrumentEnergyTrendDto`(+ 중첩 `EnergyTrendPoint`) 신규 1개, (3) `InstrumentEnergyTrendService` 신규 1개, (4) `InstrumentEnergyTrendController` 신규 1개, (5) `InstrumentEnergyTrendServiceTest` 단위 테스트 1개. 기존 리포지토리 메서드(`InstrumentRepository.findById`·`TagRepository.findByInstrumentInstrumentIdInAndUseYn`·`RawDataRepository.findEnergyDeltaBuckets`)·enum(`TagMeasurementType.PWQ`·`InqUnit`)·`InstrumentErrorCode.INSTRUMENT_NOT_FOUND`/`INVALID_INQ_PERIOD`·`idx_rawdata_1m_h_tag_time` 전부 변경 없이 재사용.

## 기존 사전·패턴과의 충돌

| 충돌 후보 | 판정 | 해소책 |
|----------|------|--------|
| `energy`/`trend` 단어 vs `elceg`/`elpwr` 어근 | 충돌 없음 | DTO 클래스명·URL 세그먼트 전용, DB 컬럼 조합 재료 아님 → 미등록 (glossary 안건 1) |
| `PumpTimeSeriesSearchDto` 재사용 vs 신규 SearchDto | 정책 충돌(블로커) | 396일 상한·YEAR 거부 정책 차이 → 신규 `InstrumentEnergyTrendSearchDto` 동형 복제(비상속). 본 작업 범위 포함 → 빌드·테스트 검증으로 해소 (backend 안건 2-①) |
| `InstrumentController`/`PumpTimeSeriesController` 확장 vs 신규 컨트롤러 | SRP/책임 혼재(블로커) | 신규 `InstrumentEnergyTrendController`(@Tag 설비별사용량) 분리. PathPattern 충돌 없음 (backend 안건 2-②) |
| `equip_type_cd` 필터 강제 룰 vs 종류 무필터 | 위반 아님 | instrumentId 단일 물리 식별자 직접 지정 — 종류별 도메인 룰 분기 없음. 3번섹션 종류 무필터 선례 동형 (domain 안건 3-②) |
| 음수 차분(적산 리셋·롤오버) 처리 미명세 | 미결(블로커→결정) | 음수·null 버킷 제외(생략) — `PumpPowerTimeSeriesService`·`FacilityEnergyTrendService` 선례 동형. 가정 섹션 결정 기재 (domain 안건 3-④, dba 안건 4-③) |
| 다중 PWQ 태그 처리 — 첫 태그 vs 합산 | 중복(권고) | 단일 계측기 다중 PWQ 시 `baseDtm` 버킷 합산. `PumpPowerTimeSeriesService`(첫 태그)와 의도적 차이 → Javadoc 명기 (backend 안건 2-③) |

## PLAN 으로 전달할 결정 사항

**도메인 모델**: 신규 엔티티 없음. 멤버십 = 선택 단일 계측기(`instrumentId`)의 활성 PWQ 태그 → `findEnergyDeltaBuckets` 버킷 차분 → `baseDtm` 버킷 합산(음수·null 제외) → 단일 시리즈.

**API 구조** (instrument 도메인, `api` 모듈):
- 신규 `InstrumentEnergyTrendSearchDto` — 필드 `inqUnit`(InqUnit, `@Schema(implementation=InqUnit.class)`)·`fromDt`·`toDt`(LocalDate, `@DateTimeFormat ISO.DATE`). `@Getter @Setter @NoArgsConstructor` + `@ModelAttribute`. `isValid()`(non-null + `fromDt<=toDt` + YEAR 거부 + 간격 ≤ `MAX_PERIOD_DAYS=396L`)·`toStartDtm()`·`toEndExclusiveDtm()`. `FacilityEnergyTrendSearchDto` 동형 복제(비상속).
- 신규 `InstrumentEnergyTrendDto` — `@Getter` + private 생성자 + 정적 팩토리 `of(...)`, `BaseAuditResponseDto` 미상속. 필드 `instrumentId`·`instrumentNm`·`unit`("kWh")·`points`(`List<EnergyTrendPoint>`, `@ArraySchema`). 중첩 정적 `EnergyTrendPoint{baseDtm(LocalDateTime, @JsonFormat yyyy-MM-dd HH:mm:ss), elcegVal(BigDecimal)}`.
- 신규 `InstrumentEnergyTrendService`(`@Service @RequiredArgsConstructor @Transactional(readOnly=true)`). 의존 `InstrumentRepository`·`TagRepository`·`RawDataRepository`. 상수 `POWER_ENERGY_TYPE=TagMeasurementType.PWQ`·`UNIT_KWH="kWh"`·`MAX 헬퍼`. public `findEnergyTrend(String instrumentId, InstrumentEnergyTrendSearchDto search)` + private 헬퍼(검증·활성계측기·PWQ태그수집·버킷합산·조립) 분리. 흐름: `isValid()` false → `INVALID_INQ_PERIOD` / `findActiveInstrumentOrThrow`(findById + useYn==Y, 아니면 `INSTRUMENT_NOT_FOUND`) / PWQ 태그 수집(없으면 빈 points 200) / `findEnergyDeltaBuckets` → `TreeMap<baseDtm, elcegVal>` 합산(`merge(baseDtm, delta, BigDecimal::add)`, 음수·null 제외) / `baseDtm` 오름차순 조립. **다중 PWQ 합산 정책 Javadoc 명기**.
- 신규 `InstrumentEnergyTrendController`(`@Tag` 설비별사용량 화면, `/api/instrument`). `@GetMapping("/{instrumentId}/energy-trend")` → `ResponseEntity<CommonResponseDto<InstrumentEnergyTrendDto>>`. `@Operation`+`@ApiResponses`(200/400/401/403/404/500, 400=INVALID_INQ_PERIOD, 404=INSTRUMENT_NOT_FOUND).

**DB 설계 변경**: **없음**(스키마 변경 0). `docs/ddl/` 갱신 불필요.

**적용 패턴**: `PumpPowerTimeSeriesService`(instrument PWQ 버킷 시계열) + `FacilityEnergyTrendSearchDto`(YEAR 거부+396일 검증) + `PumpTimeSeriesController`(컨트롤러 분리) 구조 미러링(자산 자동 원용 없이 신규 작성).

**격리 수준**: 동형 선례 유지 — `@Transactional(readOnly=true)` 기본 READ_COMMITTED. REPEATABLE_READ 미적용(과거 구간 조회 팬텀 무시 가능).

**테스트**: `InstrumentEnergyTrendServiceTest` Mockito 단위 — 기간 무효(null·역전·YEAR·>396일)→INVALID_INQ_PERIOD / 미존재·비활성 계측기→INSTRUMENT_NOT_FOUND / PWQ 보유→버킷 points 매핑·baseDtm 오름차순 / 음수 차분 버킷 제외 / 다중 PWQ 태그 버킷 합산 / PWQ 무보유(PWI만)→빈 points(200) / 데이터 0건→빈 points.

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 표출 측정값 = PWQ(적산전력량, kWh) 한정. PWI(순시전력)는 7번섹션 "순시 전력" 차트 대상 → 본 API 범위 외 | 결정 | 사용자 "전력량" 명시. PWI 는 별도 섹션(domain 안건 3) |
| **음수 차분(적산 리셋·롤오버) 버킷은 제외(생략)** — null 차분도 동일 | 결정 | `PumpPowerTimeSeriesService`(`signum()>=0` 필터)·`FacilityEnergyTrendService`(음수→null→merge 스킵+WARN) 선례 동형. `ot-integration.md §3` PWQ 정책 정합 (domain 블로커 해소) |
| 단일 계측기 다중 PWQ 태그 → `baseDtm` 버킷 합산 | 결정 | ELCMTR 다채널 대비. `PumpPowerTimeSeriesService`(펌프당 첫 태그)와 의도적 차이 → Javadoc 명기 (backend 안건 2-③) |
| PWQ 무보유 계측기(PWI만) → 빈 시리즈(200, 예외 아님) | 결정 | `coding-discipline.md §2` 정합. 3번섹션이 전력태그 보유 계측기만 노출 (domain 안건 3-③) |
| 데이터 없는 버킷은 생략 — 연속 시간축은 frontend 구성 | 결정 | `PumpPowerTimeSeriesDto`·`FacilityEnergyTrendDto` 선례 동형 |
| 격리 수준 READ_COMMITTED 유지(REPEATABLE_READ 미적용) | 결정 | 동형 선례 readOnly 기본 격리. 과거 구간 조회 팬텀 무시 (dba 안건 4-①) |
| 조회 단위 `[시|일|월]` (YEAR 거부) + 간격 ≤ 396일(13개월) | 결정 | 1번섹션 버킷 [시\|일\|월] + rawdata 13개월 보존. `FacilityEnergyTrendSearchDto` 동형 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 기간 무효 → INVALID_INQ_PERIOD / 미존재·비활성 계측기 → INSTRUMENT_NOT_FOUND | 신규 단위 테스트 — null·역전·YEAR·>396일 / findById empty·useYn=N 각각 RestApiException GREEN |
| PWQ 버킷 → baseDtm 오름차순 시계열 매핑 | 신규 단위 테스트 — RawDataBucketDto mock → points baseDtm asc·elcegVal 일치 GREEN |
| 음수 차분 버킷 제외 + 다중 PWQ 태그 버킷 합산 | 신규 단위 테스트 — 음수 버킷 미포함 / 동일 baseDtm 2태그 합산값 GREEN |
| PWQ 무보유·데이터 0건 → 빈 points(200) | 신규 단위 테스트 — PWI-only / 버킷 empty 시 빈 List GREEN |
| 빌드·전체 테스트 통과 | `./gradlew.bat :api:test` PASS + `./gradlew.bat clean build` BUILD SUCCESSFUL |
| (선택) dev DB 수동 검증 | `GET /api/instrument/{PWQ 보유 계측기 ID}/energy-trend?inqUnit=DAY&fromDt=...&toDt=...` 일 버킷 시계열 응답·baseDtm 오름차순 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | PWQ 측정값 읽기 전용 시계열 조회 — 알람 임계값·전이·복귀 무접촉, `alarm_h` 기록 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | GET 조회만 — 기동 명령 발생 경로 없음, 선행조건 검사·제어 명령 발행 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 변경 없음, 강제 전환 트리거 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h`·`pump_ctrl_h` 무접촉. instrument/tag 마스터 + rawdata 조회 전용(쓰기 없음) |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

**없음** — 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 도메인 약어 등록 0건, DB suffix·엔티티 패턴 변경 0건, DB 스키마 변경 0건. 모든 어휘가 기존 등록 항목 재사용이며 신규 ErrorCode 0건이다(`wtp-glossary-manager` 안건 1 확정 — 신규 등록 항목 0건, 직전 3번섹션·2번섹션 사이클과 동일).

## 산출물
- [계획안](../../../plan/20260609/설비별사용량-4번섹션/PLAN1.md) (다음 단계 `/dev:plan` 에서 작성)
