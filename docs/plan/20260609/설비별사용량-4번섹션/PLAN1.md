---
status: approved
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 4번섹션 — 계측기 전력량 트렌드 조회 API 계획

## 관련 분석
- [분석](../../../analyze/20260609/설비별사용량-4번섹션/ANALYZE1.md) (status: approved)

## 목적

설비별 사용량 화면 **4번섹션(설비 트렌드)** API 를 신규 추가한다. 3번섹션(`GET /api/facility/{facilityId}/power-instruments`)에서 사용자가 선택한 **단일 계측기(`instrumentId`)** 와 1번섹션 파라미터 3종(집계 버킷 `[시|일|월]`·시작일자·종료일자)을 받아, 조회기간 동안 그 계측기의 **전력량(PWQ 적산전력량, kWh) 시계열**을 **단일 시리즈**로 반환하는 **읽기 전용** 조회 API 다.

## 배경

- ANALYZE1 5인 회의 결론: 신규 엔티티·테이블·DB 컬럼·마이그레이션·인덱스 **0건**, 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 약어 **0건**, 신규 ErrorCode **0건**, 도메인 4영역(알람·인터록·운전모드·이력기록) **전부 비해당**(읽기 전용 표출), 룰 갱신 지시서 **0건**.
- 동형 선례 미러링(자산 자동 원용 없이 신규 작성 — 메모리 `feedback_no_auto_reuse_cross_cycle.md` 정합):
  - `PumpPowerTimeSeriesService`(instrument PWQ 버킷 시계열) — 단일 계측기 스코프 + 종류 무관 + **다중 PWQ 합산**으로 좁힌 변형
  - `FacilityEnergyTrendSearchDto`(YEAR 거부 + 396일 상한 검증) — `PumpTimeSeriesSearchDto`(YEAR 허용·상한 없음) 대신 동형 복제 채택(13개월 보존 정합)
  - `PumpTimeSeriesController`(컨트롤러 분리 패턴) — `InstrumentController` CRUD 혼재 회피

## 범위

### 포함

- 신규 검색 DTO 1개: `InstrumentEnergyTrendSearchDto` (`com.mo.swtp.instrument.dto`)
- 신규 응답 DTO 1개 + 중첩 DTO 1개: `InstrumentEnergyTrendDto`(+ 중첩 정적 클래스 `EnergyTrendPoint`) (`com.mo.swtp.instrument.dto`)
- 신규 Service 1개: `InstrumentEnergyTrendService` (`com.mo.swtp.instrument.service`)
- 신규 Controller 1개: `InstrumentEnergyTrendController` (`com.mo.swtp.instrument.web`) — `GET /api/instrument/{instrumentId}/energy-trend`
- 신규 단위 테스트 1개: `InstrumentEnergyTrendServiceTest`

### 제외

- PWI(순시전력) 차트 — 7번섹션 "순시 전력" 대상, 본 API 범위 외
- 5/6번섹션(통계·비율) — 별도 사이클
- 신규 인덱스·테이블·마이그레이션·엔티티·ErrorCode (ANALYZE1 결정)

## 구현 방향

### 1) 재사용하는 기존 자산 (변경 없음)

| 자산 | 용도 |
|------|------|
| `RawDataRepository.findEnergyDeltaBuckets(List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit)` | PWQ 버킷별 `MAX(raw_val)-MIN(raw_val)` 차분 (GOOD only, corr_val 미사용, 월 RANGE 파티션 프루닝) |
| `RawDataBucketDto` (record: tagSrlNo·baseDtm·aggrVal) | 버킷 집계 결과 매핑 |
| `InqUnit.getDateTruncUnit()` | 시/일/월 → `date_trunc` 단위 |
| `InstrumentRepository.findById(String)` | 계측기 단건 조회 (활성 검증) |
| `TagRepository.findByInstrumentInstrumentIdInAndUseYn(List<String>, YnType)` | 계측기의 활성 태그 IN 조회 |
| `TagMeasurementType.PWQ` | 전력량 태그 멤버십 판정 |
| `InstrumentErrorCode.INSTRUMENT_NOT_FOUND`(404)·`INVALID_INQ_PERIOD`(400) | 미존재·비활성 계측기 / 기간 검증 실패 — 신규 ErrorCode 0건 |
| `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` | 버킷 쿼리 인덱스 (이미 존재) |

### 2) `InstrumentEnergyTrendSearchDto`

`FacilityEnergyTrendSearchDto` 동형 복제(**상속 아님** — `@Schema` 4번섹션 맥락 분리, frontend SPEC 오염 회피). `@Getter @Setter @NoArgsConstructor` + `@ModelAttribute` GET 바인딩. 기간 검증 실패 시 예외 throw 는 Service 가 수행하며 본 DTO 는 boolean 판정 + 일시 변환만 제공(DTO 예외 throw 회피).

| 필드 | 타입 | 비고 |
|------|------|------|
| `inqUnit` | `InqUnit` | `@Schema(implementation = InqUnit.class)` — 시/일/월. YEAR 미지원 |
| `fromDt` | `LocalDate` | `@DateTimeFormat(iso = ISO.DATE)` |
| `toDt` | `LocalDate` | `@DateTimeFormat(iso = ISO.DATE)` |

- 상수 `MAX_PERIOD_DAYS = 396L` (13개월 — `rawdata_1m_h` 롤링 보존 정합).
- `isValid()` — (1) 3필드 non-null, (2) `!fromDt.isAfter(toDt)`, (3) `inqUnit != InqUnit.YEAR`, (4) `ChronoUnit.DAYS.between(fromDt, toDt) <= MAX_PERIOD_DAYS`.
- `toStartDtm()` = `fromDt.atStartOfDay()` (inclusive), `toEndExclusiveDtm()` = `toDt.plusDays(1).atStartOfDay()` (exclusive 상한).

### 3) `InstrumentEnergyTrendDto`

`@Getter` + private 기본 생성자 + 정적 팩토리 `of(...)`. `BaseAuditResponseDto` 미상속(단순 조회 응답 — `PumpPowerTimeSeriesDto` 선례). `PumpPowerTimeSeriesDto` outer + 중첩 Point + 정적 팩토리 패턴 인용.

| 필드 | 타입 | 비고 |
|------|------|------|
| `instrumentId` | String | 계측기 ID |
| `instrumentNm` | String | 계측기명 |
| `unit` | String | "kWh" |
| `points` | `List<EnergyTrendPoint>` | `@ArraySchema(schema = @Schema(implementation = EnergyTrendPoint.class))` — 데이터 없는 버킷 생략 |

중첩 정적 클래스 `EnergyTrendPoint` — `baseDtm`(LocalDateTime, `@JsonFormat(shape=STRING, pattern="yyyy-MM-dd HH:mm:ss")`)·`elcegVal`(BigDecimal). `@Getter` + private 생성자 + 정적 팩토리 `of(baseDtm, elcegVal)`.

### 4) `InstrumentEnergyTrendService` 흐름

`@Service @RequiredArgsConstructor @Transactional(readOnly = true)`. 의존: `InstrumentRepository`·`TagRepository`·`RawDataRepository`. 상수: `POWER_ENERGY_TYPE = TagMeasurementType.PWQ`, `UNIT_KWH = "kWh"`.

public `findEnergyTrend(String instrumentId, InstrumentEnergyTrendSearchDto search)` → `InstrumentEnergyTrendDto`:

1. `search.isValid()` false → `RestApiException(INVALID_INQ_PERIOD)`.
2. `findActiveInstrumentOrThrow(instrumentId)` — `findById` + `useYn == Y` 검사, 아니면 `INSTRUMENT_NOT_FOUND`. (private 헬퍼)
3. `collectPwqTags(instrumentId)` — `findByInstrumentInstrumentIdInAndUseYn(List.of(instrumentId), Y)` → `PWQ` 필터 → `tagSrlNo` 리스트. **종류 무필터**(instrumentId 단일 물리 식별자).
4. PWQ 태그 0건 → 빈 `points`(200, 예외 아님)로 즉시 응답.
5. `aggregateBuckets(pwqTags, search)` — `findEnergyDeltaBuckets(pwqTags, toStartDtm, toEndExclusiveDtm, getDateTruncUnit)` → `TreeMap<LocalDateTime, BigDecimal>` 에 `merge(baseDtm, aggrVal, BigDecimal::add)` 로 **다중 PWQ 태그 버킷 합산**. `aggrVal == null || signum() < 0` 버킷은 **제외**(적산 리셋·롤오버 방어).
6. `TreeMap` 자연 순서(`baseDtm` 오름차순)로 `EnergyTrendPoint` 리스트 조립 → 단일 DTO 반환.

> public 메서드 50줄 이내, 검증·활성계측기·PWQ태그수집·버킷합산·조립은 private 헬퍼로 분리(`coding-discipline.md §2.1`). SQL 호출 = `findById` 1 + 태그 IN 1 + 버킷 1 (고정). **다중 PWQ 합산 정책은 Javadoc 명기**(ELCMTR 다채널 대비, `PumpPowerTimeSeriesService` 첫-태그 가정과 의도적 차이 — `coding-discipline.md §1` 가정 명시). `findEnergyDeltaBuckets` 호출 흐름은 `§2.5` 면책 영역(이미 인용 주석 보유 — 본 서비스 추가 인용 불필요).

### 5) `InstrumentEnergyTrendController`

신규 컨트롤러(`InstrumentController`·`PumpTimeSeriesController` 미확장 — SRP·@Tag 목적 분리). `CommonController` 상속, `@RequestMapping("/api/instrument")`. `@Tag` = 설비별사용량 화면 그룹.

`@GetMapping("/{instrumentId}/energy-trend")` → `ResponseEntity<CommonResponseDto<InstrumentEnergyTrendDto>>`. `@PathVariable instrumentId` + `@ModelAttribute InstrumentEnergyTrendSearchDto search` → `getResponseEntity(service.findEnergyTrend(...))`. `@Operation` + `@ApiResponses`(200/400/401/403/404/500, 400 = `INVALID_INQ_PERIOD`, 404 = `INSTRUMENT_NOT_FOUND`). `/{instrumentId}/energy-trend` 리터럴 세그먼트는 PathPattern 특이도 우선이라 `InstrumentController`의 `/{instrumentId}` GET 과 ambiguous-mapping 미발생.

## 성공 기준 (검증 가능 형태)

| 성공 기준 | 검증 |
|---------|------|
| 기간 무효(null·역전·YEAR·>396일) → INVALID_INQ_PERIOD | 신규 단위 테스트 4건 GREEN — 각 케이스 RestApiException + errorCode 일치 |
| 미존재·비활성 계측기 → INSTRUMENT_NOT_FOUND | 신규 단위 테스트 2건 GREEN — findById empty / useYn=N |
| PWQ 버킷 → baseDtm 오름차순 시계열 매핑 | 신규 단위 테스트 GREEN — RawDataBucketDto mock → points baseDtm asc·elcegVal 일치 |
| 음수 차분 버킷 제외 | 신규 단위 테스트 GREEN — 음수 aggrVal 버킷 points 미포함 |
| 다중 PWQ 태그 동일 baseDtm 버킷 합산 | 신규 단위 테스트 GREEN — 2태그 동일 baseDtm → 합산 elcegVal 1행 |
| PWQ 무보유(PWI만)·데이터 0건 → 빈 points(200) | 신규 단위 테스트 2건 GREEN — 빈 List, 예외 아님 |
| 빌드·전체 테스트 통과 | `./gradlew.bat :api:test` PASS + `./gradlew.bat clean build` BUILD SUCCESSFUL |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 표출 측정값 = PWQ(적산전력량, kWh) 한정. PWI 는 7번섹션 대상 | 가정 → 결정 | 채택 (사용자 "전력량" 명시) |
| 음수 차분(적산 리셋·롤오버)·null 차분 버킷 제외(생략) | 미해결 → 결정 | 채택 — `aggrVal == null || signum() < 0` 필터. `PumpPowerTimeSeriesService`(`signum()>=0`)·`FacilityEnergyTrendService`(음수→null→merge 스킵+WARN) 선례 동형, `ot-integration.md §3` PWQ 정합 |
| 단일 계측기 다중 PWQ 태그 → `baseDtm` 버킷 합산 | 미해결 → 결정 | 채택 (ELCMTR 다채널 대비). `PumpPowerTimeSeriesService`(첫 태그)와 의도적 차이 → Javadoc 명기 |
| PWQ 무보유 계측기(PWI만) → 빈 시리즈(200, 예외 아님) | 가정 → 결정 | 채택 (`coding-discipline.md §2` 정합. 3번섹션이 전력태그 보유 계측기만 노출) |
| 데이터 없는 버킷 생략 — 연속 시간축은 frontend 구성 | 가정 → 결정 | 채택 (`PumpPowerTimeSeriesDto` 선례 동형) |
| 격리 수준 READ_COMMITTED 유지(REPEATABLE_READ 미적용) | 미해결 → 결정 | 채택 — `@Transactional(readOnly=true)` 기본. 과거 구간 조회 진행 분(分) 1버킷 미세 팬텀 무시 가능 (dba 안건 4-①) |
| 조회 단위 `[시|일|월]`(YEAR 거부) + 간격 ≤ 396일(13개월) | 가정 → 결정 | 채택 (`FacilityEnergyTrendSearchDto` 동형 복제) |
| SearchDto 재사용 vs 신규 / 컨트롤러 확장 vs 신규 | 미해결 → 결정 | 신규 `InstrumentEnergyTrendSearchDto`(비상속) + 신규 `InstrumentEnergyTrendController` (ANALYZE1 backend 블로커 2건 결론) |

## 제외 사항

- PWI 순시전력 차트(7번섹션)·통계/비율(5/6번섹션) — 별도 사이클
- 신규 인덱스·테이블·마이그레이션·엔티티·ErrorCode
- SearchDto 공통 부모 추상화 (동형 복제 채택 — `api-patterns.md §DTO` 검색 조건 3건 누적 시 별도 ANALYZE)

## 예상 산출물
- [태스크](../../../tasks/20260609/설비별사용량-4번섹션/TASK1.md)
