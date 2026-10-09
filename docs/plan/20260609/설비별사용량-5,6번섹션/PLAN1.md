---
status: approved
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 5·6번섹션 — 설비별 누적 전력량 + 분포율 조회 API 계획

## 관련 분석
- [분석](../../../analyze/20260609/설비별사용량-5,6번섹션/ANALYZE1.md) (status: approved)

## 목적

설비별 사용량 화면 **5번섹션(설비별 합계 — 가로 막대)** 과 **6번섹션(분포 — 도넛)** 을 **하나의 읽기 전용 조회 API** 로 신규 추가한다. 1번섹션 파라미터(시작일자 `fromDt`·종료일자 `toDt`)와 2번섹션에서 선택한 **시설ID(`facilityId`)** 를 받아, 선택 시설을 루트로 `parent_facility_id` self-FK 를 **재귀 탐색**(하위 시설 전체)한 뒤 그 시설들에 속한 활성 계측기(설비)의 **적산전력 태그(PWQ)** 로 `fromDt~toDt` 기간 **설비(계측기)별 누적 전력량(kWh)** 과 **분포율(%)** 을 산정해 단일 응답으로 반환한다.

- **5번섹션** = 설비별 누적 전력량(`elceg`, kWh).
- **6번섹션** = 설비별 분포율(`ratio`, %) = `[설비 전력량 / 전체 설비 전력량] × 100`. 전체 = 같은 재귀 하위 트리의 모든 설비(PWQ 보유 계측기) 합.

## 배경

- ANALYZE1 5인 회의 결론: 신규 엔티티·테이블·DB 컬럼·마이그레이션·인덱스 **0건**, 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 약어 **0건**(`total`·`elceg`·`unit`·`rate` 기존 재사용, `ratio`/`usage`/`energy` 는 DTO·URL 전용 미등록), 신규 ErrorCode **0건**, 도메인 4영역(알람·인터록·운전모드·이력기록) **전부 비해당**(읽기 전용 표출), 룰 갱신 지시서 **0건**.
- 본 API 는 같은 화면(설비별사용량)의 2·3·4번섹션 패턴 **3종을 결합**한 단일 도메인·신규 엔드포인트다. 사이클 간 자산 자동 원용 없이 구조만 미러링한 **신규 작성**(메모리 `feedback_no_auto_reuse_cross_cycle.md` 정합):
  - `FacilityPowerInstrumentService`(3번섹션) — **재귀 하위 트리 BFS**(`collectSubtree`·`findActiveFacilityOrThrow`·`displayOrder`) + 시설/계측기/태그 IN절 일괄 조회 패턴
  - `FacilityEnergyUsageService.aggregateEnergy`(2번섹션) — **PWQ 버킷 차분 합산**(`validDeltaOrNull` 음수·null 제외), 그룹 키를 `운영루트` → `instrumentId` 로 변경
  - `PumpCtrlHistoryService.computeRate` — **분포율(%)** = `count/total×100`, scale=1, HALF_UP, total=0 → 0
  - `FacilityEnergyUsageSearchDto`(2번섹션) — 기간 검증 패턴에서 `inqUnit` 제거한 from/to 전용 버전

## 범위

### 포함

- 신규 검색 DTO 1개: `FacilityInstrumentEnergyUsageSearchDto` (`com.mo.swtp.facility.dto`)
- 신규 응답 DTO 1개 + 중첩 DTO 1개: `FacilityInstrumentEnergyUsageDto`(+ 중첩 정적 클래스 `InstrumentEnergyUsageItem`) (`com.mo.swtp.facility.dto`)
- 신규 Service 1개: `FacilityInstrumentEnergyUsageService` (`com.mo.swtp.facility.service`)
- 신규 Controller 1개: `FacilityInstrumentEnergyUsageController` (`com.mo.swtp.facility.web`) — `GET /api/facility/{facilityId}/instrument-energy-usage`
- 신규 단위 테스트 1개: `FacilityInstrumentEnergyUsageServiceTest`

### 제외

- PWI(순시전력) 지표 — 7번섹션 "순시 전력" 대상, 본 API 범위 외(PWQ 적산만)
- 신규 인덱스·테이블·마이그레이션·엔티티·ErrorCode (ANALYZE1 결정)
- `inqUnit` 노출 — 내부 DAY 버킷 고정(사용자 미노출)
- SearchDto/helper 공통 추상화 — 동형 복제 유지(사용자 결정), 추출은 별도 ANALYZE

## 구현 방향

### 1) 재사용하는 기존 자산 (변경 없음)

| 자산 | 용도 |
|------|------|
| `RawDataRepository.findEnergyDeltaBuckets(List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit)` | PWQ 버킷별 `MAX(raw_val)-MIN(raw_val)` 차분 (GOOD only, corr_val 미사용, 월 RANGE 파티션 프루닝) |
| `RawDataBucketDto` (record: tagSrlNo·baseDtm·aggrVal) | 버킷 집계 결과 매핑 |
| `InqUnit.DAY.getDateTruncUnit()` → "day" | 내부 DAY 버킷 단위 상수 |
| `FacilityRepository.findById(String)` | 루트 시설 단건 조회 (활성 검증) |
| `FacilityRepository.findByParentFacilityIdInAndUseYn(List<String>, YnType)` | BFS 재귀 하위 레벨별 조회 |
| `InstrumentRepository.findByFacilityFacilityIdInAndUseYn(List<String>, YnType)` | 하위 트리 시설의 활성 계측기 IN 조회 |
| `TagRepository.findByInstrumentInstrumentIdInAndUseYn(List<String>, YnType)` | 계측기의 활성 태그 IN 조회 |
| `TagMeasurementType.PWQ` | 적산전력 태그 멤버십 판정 |
| `EquipType` · `YnType` | 계측기 종류 응답 / 활성 필터 |
| `FacilityErrorCode.FACILITY_NOT_FOUND`(404)·`INVALID_SEARCH_PERIOD`(400) | 미존재·비활성 시설 / 기간 검증 실패 — 신규 ErrorCode 0건 |
| `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` | 버킷 쿼리 인덱스 (이미 존재) |
| `CommonController` · `CommonResponseDto` | 컨트롤러 상속 / 응답 래핑 |

### 2) `FacilityInstrumentEnergyUsageSearchDto`

`FacilityEnergyUsageSearchDto` 에서 **`inqUnit` 제거**한 from/to 전용 동형 복제(**상속 아님** — `@Schema` 맥락 분리, frontend SPEC 오염 회피). `@Getter @Setter @NoArgsConstructor` + `@ModelAttribute` GET 바인딩. 기간 검증 실패 시 예외 throw 는 Service 가 수행하며 본 DTO 는 boolean 판정 + 일시 변환만 제공.

| 필드 | 타입 | 비고 |
|------|------|------|
| `fromDt` | `LocalDate` | `@DateTimeFormat(iso = ISO.DATE)` |
| `toDt` | `LocalDate` | `@DateTimeFormat(iso = ISO.DATE)` |

- 상수 `MAX_PERIOD_DAYS = 396L` (13개월 — `rawdata_1m_h` 롤링 보존 정합).
- `isValid()` — (1) from/to non-null, (2) `!fromDt.isAfter(toDt)`, (3) `ChronoUnit.DAYS.between(fromDt, toDt) <= MAX_PERIOD_DAYS`. **YEAR 검사 없음**(inqUnit 부재).
- `toStartDtm()` = `fromDt.atStartOfDay()` (inclusive), `toEndExclusiveDtm()` = `toDt.plusDays(1).atStartOfDay()` (exclusive 상한).

### 3) `FacilityInstrumentEnergyUsageDto`

래퍼 응답 DTO. `@Getter` + private 기본 생성자 + 정적 팩토리 `of(...)`. `BaseAuditResponseDto` 미상속(단순 조회 응답 — `FacilityEnergyUsageDto`·`InstrumentEnergyTrendDto` 선례).

| 필드 | 타입 | 비고 |
|------|------|------|
| `unit` | String | "kWh" |
| `totalElceg` | BigDecimal | 전체 설비 누적 전력량 합 (kWh) |
| `items` | `List<InstrumentEnergyUsageItem>` | `@ArraySchema(schema = @Schema(implementation = InstrumentEnergyUsageItem.class))` |

중첩 정적 클래스 `InstrumentEnergyUsageItem` — `@Getter` + private 생성자 + 정적 팩토리 `of(...)`:

| 필드 | 타입 | 비고 |
|------|------|------|
| `instrumentId` | String | 계측기 ID |
| `instrumentNm` | String | 계측기명 |
| `equipTypeCd` | `EquipType` | `@Schema(implementation = EquipType.class)` — 계측기 종류 |
| `facilityId` | String | 소속 시설 ID |
| `facilityNm` | String | 소속 시설명 |
| `elceg` | BigDecimal | 설비 누적 전력량 (kWh) — 5번섹션 |
| `ratio` | BigDecimal | 설비 분포율 (%) — 6번섹션. `@Schema` 에 "독립 반올림으로 합이 정확히 100.0 이 아닐 수 있음" 명기 |

### 4) `FacilityInstrumentEnergyUsageService` 흐름

`@Service @RequiredArgsConstructor @Transactional(readOnly = true) @Slf4j`. 의존: `FacilityRepository`·`InstrumentRepository`·`TagRepository`·`RawDataRepository`. 상수: `POWER_ENERGY_TYPE = TagMeasurementType.PWQ`, `UNIT_KWH = "kWh"`, `BUCKET_UNIT = InqUnit.DAY.getDateTruncUnit()`, `MAX_DEPTH = 10`, `HUNDRED = new BigDecimal("100")`, `RATE_SCALE = 1`.

public `findInstrumentEnergyUsage(String facilityId, FacilityInstrumentEnergyUsageSearchDto search)` → `FacilityInstrumentEnergyUsageDto`:

1. `search.isValid()` false → `RestApiException(FacilityErrorCode.INVALID_SEARCH_PERIOD)`.
2. `findActiveFacilityOrThrow(facilityId)` — `findById` + `useYn == Y` 검사, 아니면 `FACILITY_NOT_FOUND` *(`FacilityPowerInstrumentService` 동형 복제)*. (private 헬퍼)
3. `collectSubtree(root)` — 루트 inclusive 재귀 하위 BFS, visited-set 순환 방어 + `MAX_DEPTH` 백스톱 *(`FacilityPowerInstrumentService.collectSubtree` 동형 복제)*. → `Map<facilityId, Facility>`.
4. `findByFacilityFacilityIdInAndUseYn(subtreeIds, Y)`; 계측기 0건 → 빈 래퍼(`of("kWh", ZERO, List.of())`)로 즉시 응답.
5. `collectPwqByInstrument(instruments)` — `findByInstrumentInstrumentIdInAndUseYn(instrumentIds, Y)` → **PWQ 필터** → `tagSrlNo` 리스트 + `tagSrlNo→instrumentId` 맵 + **PWQ 보유 계측기 집합**. PWQ 태그 0건 → 빈 래퍼로 즉시 응답.
6. `aggregateEnergyByInstrument(pwqTags, tagToInstrument, search)` — `findEnergyDeltaBuckets(pwqTags, toStartDtm, toEndExclusiveDtm, BUCKET_UNIT)` 순회 → `validDeltaOrNull`(null·음수 제외 + WARN) → `tagSrlNo` 의 instrumentId 로 `merge(BigDecimal::add)` *(`FacilityEnergyUsageService.aggregateEnergy` 미러링, 그룹 키만 root→instrument)*. → `Map<instrumentId, BigDecimal>`.
7. **PWQ 보유 계측기 전체**(5단계 집합)를 대상으로, 전력량 맵 lookup 부재 시 `BigDecimal.ZERO` (0 포함 결정).
8. `totalElceg` = 전 계측기 `elceg` 합. `ratio` = `computeRate(elceg, totalElceg)` — `scale=1, HALF_UP`, `total.signum()==0` → `ZERO.setScale(1, HALF_UP)` *(`PumpCtrlHistoryService.computeRate` 미러링)*.
9. 정렬: 시설 `dispOrd` → 계측기 `dispOrd` → `instrumentNm` *(`FacilityPowerInstrumentService.displayOrder` 미러링)* → `InstrumentEnergyUsageItem` 조립 → 래퍼 `of(...)` 반환.

> public 메서드 50줄 이내 — 검증·활성시설·재귀수집·PWQ수집·버킷합산·비율·조립을 private 헬퍼로 분리(`coding-discipline.md §2.1`). `validDeltaOrNull`·`collectSubtree`·`findActiveFacilityOrThrow` 는 **3번째 동형 복제**이며 Javadoc 에 "공통 추출은 사본 3건 이상 누적 시 별도 ANALYZE — ANALYZE1 wtp-backend-engineer 결론" 인용(`coding-discipline.md §3`). `validDeltaOrNull` Javadoc 에 PWQ 차분 정책(`ot-integration.md §3`) 인용. `findEnergyDeltaBuckets` 호출 흐름은 `§2.5` 면책 영역(이미 인용 주석 보유 — 본 서비스 추가 인용 불필요). 다중 PWQ 태그/설비 합산 정책 Javadoc 명기.

### 5) `FacilityInstrumentEnergyUsageController`

신규 컨트롤러(`FacilityController` 미확장 — SRP·@Tag 목적 분리). `CommonController` 상속, `@RestController @RequestMapping("/api/facility")`. `@Tag(name = "15. 설비별 사용량")` *(4번섹션 컨트롤러와 동일 태그 — 화면 그룹 정렬)*.

`@GetMapping("/{facilityId}/instrument-energy-usage")` → `ResponseEntity<CommonResponseDto<FacilityInstrumentEnergyUsageDto>>`. `@PathVariable facilityId` + `@ModelAttribute FacilityInstrumentEnergyUsageSearchDto search` → `getResponseEntity(service.findInstrumentEnergyUsage(...))`. `@Operation` + `@ApiResponses`(200/400/401/403/404/500, 400 = `INVALID_SEARCH_PERIOD`, 404 = `FACILITY_NOT_FOUND`).

> 리터럴 세그먼트 `/{facilityId}/instrument-energy-usage` 는 PathPattern 특이도 우선이라 `FacilityController` 의 `/{facilityId}` GET 과 ambiguous-mapping 미발생(3·4번섹션 선례).

## 성공 기준 (검증 가능 형태)

| 성공 기준 | 검증 |
|---------|------|
| 기간 무효(null·역전·>396일) → INVALID_SEARCH_PERIOD | 신규 단위 테스트 3건 GREEN — 각 케이스 RestApiException + errorCode 일치 |
| 미존재·비활성 시설 → FACILITY_NOT_FOUND | 신규 단위 테스트 2건 GREEN — findById empty / useYn=N |
| 재귀 하위 BFS 다단계 수집 | 신규 단위 테스트 GREEN — 2단계 자식 mock → 손자 계측기까지 집계 포함 |
| 설비별 버킷 합산(다중 PWQ 태그·다설비) | 신규 단위 테스트 GREEN — 2설비×다태그 mock → instrumentId 별 elceg 합산 일치 |
| 음수·null 차분 버킷 제외 | 신규 단위 테스트 GREEN — 음수/null aggrVal 버킷 elceg 미반영 |
| 분포율 = elceg/total×100 scale1 HALF_UP | 신규 단위 테스트 GREEN — 알려진 elceg·total → ratio 기대값 일치 |
| total=0 → 전 설비 ratio 0.0 | 신규 단위 테스트 GREEN — 전 설비 전력량 0 → ratio 모두 0.0 |
| PWQ 보유·전력량 0 설비 포함 | 신규 단위 테스트 GREEN — 데이터 0 설비도 items 에 elceg=0·ratio=0 포함 |
| 계측기·PWQ 0건 → 빈 래퍼(200) | 신규 단위 테스트 2건 GREEN — totalElceg=0·items 빈 배열, 예외 아님 |
| 정렬: 시설 dispOrd → 계측기 dispOrd → 계측기명 | 신규 단위 테스트 GREEN — 역순 mock → 정렬 순서 검증 |
| 빌드·전체 테스트 통과 | `./gradlew.bat :api:test` PASS + `./gradlew.bat clean build` BUILD SUCCESSFUL |
| (선택) 실 DB 응답 정합 | dev DB GET `/api/facility/{PWQ 설비 보유 시설ID}/instrument-energy-usage?fromDt=2024-07-01&toDt=2024-07-09` → totalElceg = Σelceg, ratio 합 ≈ 100, dispOrd 정렬 확인 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 표출 측정값 = PWQ(적산전력량, kWh) 한정. PWI 는 7번섹션 대상 | 가정 → 결정 | 채택 (사용자 "누적 전력량" 명시) |
| 전력량 산정 = 내부 DAY 버킷 차분 합산(`inqUnit` 미노출) | 미해결 → 결정 | 채택 — 일별 양수 차분만 합산(리셋·롤오버 강건, 신규 SQL 0건). 사용자 AskUserQuestion "일 버킷 합산" 결정 |
| 음수·null 차분(적산 리셋·롤오버) 버킷 제외(생략) | 미해결 → 결정 | 채택 — `validDeltaOrNull` 음수→null→merge 스킵 + WARN. `FacilityEnergyUsageService`·`PeakEnergyTrendService` 선례 동형, `ot-integration.md §3` PWQ 정합 |
| 단일 계측기 다중 PWQ 태그 → instrumentId 버킷 합산 | 미해결 → 결정 | 채택 (ELCMTR 다채널 대비). Javadoc 명기 |
| PWQ 보유·전력량 0 설비 → items 포함(elceg=0·ratio=0) | 가정 → 결정 | 채택 — 사용자 AskUserQuestion "0 으로 포함" 결정 |
| 응답 형태 = 래퍼 `{unit, totalElceg, items[]}` | 미해결 → 결정 | 채택 — 사용자 AskUserQuestion "래퍼 + 전체합계" 결정 |
| 독립 반올림으로 분포율 합이 정확히 100.0 이 아닐 수 있음 | 가정 → 결정 | 채택(표시 허용) — `ratio` `@Schema` 에 명기 |
| 격리 수준 READ_COMMITTED 유지(REPEATABLE_READ 미적용) | 미해결 → 결정 | 채택 — `@Transactional(readOnly=true)` 기본. 과거 구간 조회 진행 분(分) 1버킷 미세 팬텀 무시 가능 (ANALYZE1 dba 결론) |
| 조회기간 간격 ≤ 396일(13개월), YEAR 검사 없음 | 가정 → 결정 | 채택 (`FacilityEnergyUsageSearchDto` 동형 복제, inqUnit 제거) |
| `validDeltaOrNull`·`collectSubtree`·`findActiveFacilityOrThrow` 3번째 사본 | 미해결 → 결정 | **동형 복제 유지**(사용자 결정) — Javadoc 인용. 공통 추출은 별도 ANALYZE (`coding-discipline.md §3`) |
| SearchDto 신규 분리 + 컨트롤러 신규 분리 | 미해결 → 결정 | 신규 `FacilityInstrumentEnergyUsageSearchDto`(비상속) + 신규 `FacilityInstrumentEnergyUsageController` (ANALYZE1 backend 블로커 2건 결론) |

## 제외 사항

- PWI 순시전력(7번섹션)·통계 외 지표 — 별도 사이클
- 신규 인덱스·테이블·마이그레이션·엔티티·ErrorCode
- SearchDto/helper 공통 부모 추상화 (동형 복제 채택 — 누적 3건 시 별도 ANALYZE)

## 예상 산출물
- [태스크](../../../tasks/20260609/설비별사용량-5,6번섹션/TASK1.md)
