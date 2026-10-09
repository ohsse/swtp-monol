---
status: approved
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 7번섹션 — 설비별 순시전력 트렌드 조회 API

## 목적

`backend/image/설비별사용량.png` 대시보드 **7번섹션**(우측 하단 "순시 전력" 멀티시리즈 라인차트)을 **읽기 전용 API** 로 구현한다. 1번섹션 파라미터(`fromDt~toDt`)와 2번섹션에서 선택한 **시설ID**(`facilityId`)를 받아, 선택 시설을 루트로 `parent_facility_id` self-FK 를 **재귀 탐색**(하위 시설 전체, 루트 inclusive)한 뒤 그 시설들의 활성 계측기 중 **순시전력 태그(PWI, kW)** 를 보유한 설비별 `from 00:00:00 ~ to 23:59:59` 구간 **순시전력 1분 시계열**을 **설비별 멀티시리즈**로 반환한다.

## 배경

- [도메인 분석](../../../analyze/20260609/설비별사용량-7번섹션/ANALYZE1.md) — 5인 회의 결론: 신규 어휘 0·DB 스키마 변경 0·신규 ErrorCode 0. 옵션 B(신규 native 집계 쿼리) + 신규 `*SearchDto`(31일) + 신규 Controller(@Tag 15) + 헬퍼 동형 복제 채택.
- **동형 선례 미러링**(자산 자동 원용 없이 신규 작성, 메모리 `feedback_no_auto_reuse_cross_cycle`·`feedback_section_cycle_discard_policy` 정합):
  - 5·6번섹션 `FacilityInstrumentEnergyUsageService` — 시설 재귀 하위 멤버십 + 계측기/태그 IN + 설비별 집계 구조.
  - 2번섹션 `RawDataCustomRepositoryImpl.findFacilityLatestMinuteSumElpwr` — `unnest(?, ?)` read-only JOIN + `SUM(COALESCE(corr_val, raw_val))` GOOD `GROUP BY` 동시각 합산 + `Session.doReturningWork` JDBC `setArray("text", ...)` 결정론적 바인드.
  - 4번섹션 `InstrumentEnergyTrendDto` — outer + 중첩 시계열 Point + 정적 팩토리 구조.
  - 5·6번섹션 `FacilityInstrumentEnergyUsageController` — `FacilityController` 에서 분리된 @Tag 15 컨트롤러 선례.
- **본 작업과 선례의 차이**: 스코프=시설 재귀(4번섹션은 단일 계측기), 측정유형=PWI 순시전력 직접값(4·5·6번섹션은 PWQ 적산 차분), 형태=설비별 전체 분 시계열 멀티시리즈(2번섹션은 마지막 분 DISTINCT ON 단일값, 5·6번섹션은 기간 단일 총합).

## 범위

- **영향 모듈**: `api` 단일 (`common`·`scheduler` 무변경). 신규 엔티티 0 → `common` 무변경.
- **비즈니스 도메인**: `com.mo.swtp.facility`(서비스·DTO·컨트롤러) + `com.mo.swtp.raw`(Repository 메서드·내부 record).
- **기존 자산 재사용(무변경)**: `FacilityRepository.findById`/`findByParentFacilityIdInAndUseYn`·`InstrumentRepository.findByFacilityFacilityIdInAndUseYn`·`TagRepository.findByInstrumentInstrumentIdInAndUseYn`·`TagMeasurementType.PWI`·`EquipType`·`YnType`·`FacilityErrorCode.FACILITY_NOT_FOUND`/`INVALID_SEARCH_PERIOD`·`idx_rawdata_1m_h_tag_time`.

## 도메인 모델

**신규 엔티티·테이블·컬럼 없음** (읽기 전용 조회 API). 멤버십 해석 구조만 기술한다.

| 단계 | 데이터 소스 | 역할 |
|------|-----------|------|
| 루트 시설 | `facility_m` (`findById` + `use_yn='Y'`) | 미존재·비활성 시 `FACILITY_NOT_FOUND` |
| 재귀 하위 트리 | `facility_m` (`parent_facility_id` self-FK, BFS 레벨당 1쿼리, MAX_DEPTH=10) | 루트 inclusive 전체 시설 수집 |
| 활성 계측기 | `instrument_m` (`facility_id IN` + `use_yn='Y'`) | 하위 시설 전체의 설비 |
| 활성 PWI 태그 | `tag_m` (`instrument_id IN` + `use_yn='Y'`, `tag_se_cd='PWI'` 필터) | 태그→설비 평행 배열 구성 |
| 순시전력 시계열 | `rawdata_1m_h` (`findInstrumentMinuteSumElpwr`) | 태그→설비 `unnest` 매핑, GOOD `COALESCE(corr_val, raw_val)` 설비별 동일 `acq_dtm` 합산, 분 전체 시계열 |

## DB 설계 변경

**없음** — 스키마 변경 0건(신규 테이블·컬럼·인덱스·데이터 도메인 0). `docs/ddl/` 갱신 불필요. 신규 native 쿼리는 기존 `rawdata_1m_h` + `idx_rawdata_1m_h_tag_time` 만 사용한다.

## 구현 방향

### 1. `FacilityInstrumentPowerTrendSearchDto` (신규, `api` — `com.mo.swtp.facility.dto`)

`FacilityInstrumentEnergyUsageSearchDto` 동형 복제(상속 아님). `MAX_PERIOD_DAYS` 만 **31** 로 변경, `inqUnit` 부재.

- 필드: `fromDt`·`toDt` (`LocalDate`, `@DateTimeFormat(iso = ISO.DATE)`). `@Getter @Setter @NoArgsConstructor`.
- `isValid()` — non-null + `!fromDt.isAfter(toDt)` + `ChronoUnit.DAYS.between(fromDt, toDt) <= 31`.
- `toStartDtm()` — `fromDt.atStartOfDay()` (inclusive).
- `toEndExclusiveDtm()` — `toDt.plusDays(1).atStartOfDay()` (exclusive — `to` 23:59:59 포함).
- Javadoc: 31일 상한 근거(순시 1분 raw 대용량·차트 가독성) + 동형 복제(비상속)·`§2.5` 무관 명기.

### 2. `FacilityInstrumentPowerTrendDto` (신규, `api` — `com.mo.swtp.facility.dto`)

`@Getter` + private 생성자 + 정적 팩토리 `of(...)`. `BaseAuditResponseDto` 미상속(단순 조회 응답 — `api-patterns.md` 적용 범위 정합).

- outer 필드: `unit`(String, "kW") · `series`(`List<InstrumentPowerSeries>`, **`@ArraySchema(schema = @Schema(implementation = InstrumentPowerSeries.class))`**).
- 중첩 정적 `InstrumentPowerSeries`: `instrumentId`·`instrumentNm`·`equipTypeCd`(`EquipType`, **`@Schema(implementation = EquipType.class)`**)·`facilityId`·`facilityNm`·`points`(`List<PowerTrendPoint>`, **`@ArraySchema(schema = @Schema(implementation = PowerTrendPoint.class))`**). 정적 팩토리 `of(...)`.
- 중첩 정적 `PowerTrendPoint`: `acqDtm`(`LocalDateTime`, **`@JsonFormat(shape = STRING, pattern = "yyyy-MM-dd HH:mm:ss")`**)·`elpwrVal`(`BigDecimal`). 정적 팩토리 `of(...)`.

### 3. `RawDataCustomRepository.findInstrumentMinuteSumElpwr` + `Impl` + 내부 record (신규, `api` — `com.mo.swtp.raw`)

`findFacilityLatestMinuteSumElpwr` 동형(설비 단위 + 전체 분 시계열, DISTINCT ON 제거).

- 인터페이스 시그니처: `List<RawDataInstrumentSumDto> findInstrumentMinuteSumElpwr(List<String> tags, List<String> instruments, LocalDateTime startDtm, LocalDateTime endDtm)`. 빈 `tags` → 빈 List.
- 구현(native, `Session.doReturningWork` + JDBC `setArray("text", ...)` 결정론적 바인드, `§2.5` 면책 인용 주석 — `query-tuning.md §2`):

  ```sql
  WITH tag_instrument(tag_srl_no, instrument_id) AS (
      SELECT * FROM unnest(?, ?)
  )
  SELECT ti.instrument_id, r.acq_dtm AS dtm,
         SUM(COALESCE(r.corr_val, r.raw_val)) AS val
  FROM rawdata_1m_h r
  JOIN tag_instrument ti ON ti.tag_srl_no = r.tag_srl_no
  WHERE r.acq_dtm >= ? AND r.acq_dtm < ? AND r.quality_cd = 'GOOD'
  GROUP BY ti.instrument_id, r.acq_dtm
  ORDER BY ti.instrument_id, r.acq_dtm
  ```
  - 바인드: `setArray(1, "text", tags)`·`setArray(2, "text", instruments)`·`setObject(3, startDtm)`·`setObject(4, endDtm)`.
  - 결과 매핑 헬퍼 `drainInstrumentSum(ResultSet)` — `instrument_id`(String)·`dtm`(`getObject("dtm", LocalDateTime.class)`)·`val`(BigDecimal).
- 내부 record `RawDataInstrumentSumDto(String instrumentId, LocalDateTime dtm, BigDecimal value)` (`com.mo.swtp.raw.dto`) — `RawDataFacilitySumDto` 구조 미러링, Service 내부 전송 전용·Swagger 미노출.

### 4. `FacilityInstrumentPowerTrendService` (신규, `api` — `com.mo.swtp.facility.service`)

`@Service @RequiredArgsConstructor @Transactional(readOnly = true)`. 의존: `FacilityRepository`·`InstrumentRepository`·`TagRepository`·`RawDataRepository`. 상수: `POWER_TAG_TYPE = TagMeasurementType.PWI`·`UNIT_KW = "kW"`·`MAX_DEPTH = 10`.

public `findPowerTrend(String facilityId, FacilityInstrumentPowerTrendSearchDto search)` 흐름(메서드 본문 30줄 이내 — private 헬퍼 분리):

1. `search.isValid()` false → `INVALID_SEARCH_PERIOD`.
2. `findActiveFacilityOrThrow(facilityId)` — `findById` + `use_yn=='Y'`, 아니면 `FACILITY_NOT_FOUND` (동형 복제·인용 주석).
3. `collectSubtree(root)` — BFS, `facilityById` 맵 구성(동형 복제·인용 주석).
4. `instrumentRepository.findByFacilityFacilityIdInAndUseYn(...)` → 0건이면 빈 series 래퍼 200 반환.
5. PWI 태그 IN 일괄 조회(`findByInstrumentInstrumentIdInAndUseYn`) + `tag_se_cd==PWI` 필터로 **태그·설비 평행 배열**(`tags`, `instruments`) 구성 — 0건이면 빈 series 래퍼 200 반환. PWI 보유 설비 메타(`instrumentNm`·`equipType`·소속 시설) 도 이 단계에서 수집.
6. `findInstrumentMinuteSumElpwr(tags, instruments, toStartDtm, toEndExclusiveDtm)` → `instrumentId` 기준 `groupingBy` → 설비별 `points`(acqDtm 오름차순) 조립.
7. **PWI 보유 설비 전체**를 series 로(데이터 0 설비는 빈 `points` 포함). 시설 `dispOrd` → 계측기 `dispOrd` → `instrumentNm` 정렬.

Javadoc: 헬퍼 동형 복제(`collectSubtree`·`findActiveFacilityOrThrow`) + 다중 PWI 동일시각 합산(SQL GROUP BY 위임) + PWI GOOD/COALESCE 정책(`ot-integration.md §3`) 인용.

### 5. `FacilityInstrumentPowerTrendController` (신규, `api` — `com.mo.swtp.facility.web`)

`@Tag(name = "15. 설비별 사용량")`·`@RequestMapping("/api/facility")`·`CommonController` 상속.

- `@GetMapping("/{facilityId}/instrument-power-trend")` → `ResponseEntity<CommonResponseDto<FacilityInstrumentPowerTrendDto>>`. `@PathVariable facilityId` + `@ModelAttribute search` → `getResponseEntity(...)`.
- `@Operation` + `@ApiResponses`(200/400/401/403/404/500). 400=`INVALID_SEARCH_PERIOD`(기간 결측·역전·31일 초과), 404=`FACILITY_NOT_FOUND`(미존재·비활성 시설).
- PathPattern: `/{facilityId}/instrument-power-trend` 리터럴 세그먼트는 `/{facilityId}` GET 보다 특이도 높아 ambiguous-mapping 없음(`FacilityInstrumentEnergyUsageController` 선례 동형).

### 6. `FacilityInstrumentPowerTrendServiceTest` (신규, `api` test — `com.mo.swtp.facility.service`)

`@ExtendWith(MockitoExtension.class)` 단위 테스트. Repository mock.

## 성공 기준 (검증 가능 형태)

| 성공 기준 | 검증 방법 |
|---------|---------|
| 기간 무효(null·역전·>31일) → INVALID_SEARCH_PERIOD | `FacilityInstrumentPowerTrendServiceTest` — 3 케이스 각각 RestApiException(`INVALID_SEARCH_PERIOD`) GREEN |
| 미존재·비활성 시설 → FACILITY_NOT_FOUND | 동 테스트 — `findById` empty / `use_yn=N` 각각 RestApiException(`FACILITY_NOT_FOUND`) GREEN |
| 재귀 하위 BFS 다단계 수집 → 설비별 분 시계열 조립(acqDtm 오름차순) | 동 테스트 — 다단계 children mock + `findInstrumentMinuteSumElpwr` 결과 mock → series·points 매핑·정렬 GREEN |
| 다중 PWI 태그 동일시각 합산(SQL 위임) → 설비별 단일 시리즈 | 동 테스트 — 설비 1개 PWI 2태그 mock → 평행 배열 2 길이·단일 series·합산 points GREEN (SQL 합산 자체는 EXPLAIN/수동 검증) |
| PWI 보유·데이터 0 설비 → 빈 points 시리즈 포함 / 계측기·PWI 0건 → 빈 series 래퍼(200) | 동 테스트 — 0 데이터 설비 series 포함 / instruments 0건·PWI 0건 빈 series·200 GREEN |
| 시리즈 정렬(시설 dispOrd → 계측기 dispOrd → instrumentNm) | 동 테스트 — 정렬 역순 mock → 출력 순서 GREEN |
| `api` 모듈 테스트 통과 | `./gradlew.bat :api:test` PASS |
| 전체 빌드 통과 | `./gradlew.bat clean build` BUILD SUCCESSFUL |
| 신규 native 쿼리 실행 계획 검증(ANALYZE dba 블로커 ① 해소) | dev/스테이징 EXPLAIN (ANALYZE, BUFFERS) — `findInstrumentMinuteSumElpwr` Index Scan(`idx_rawdata_1m_h_tag_time`) + 월 RANGE 파티션 프루닝(최대 2파티션) + 500ms 이내 확인 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 표출 측정값 = PWI(순시전력 kW) 한정. PWQ는 4·5·6번섹션 대상 → 본 API 범위 외 | 결정 | 사용자 "순시전력" 명시 |
| 시리즈 단위 = 설비별. 다중 PWI 태그 → 동일 `acq_dtm` SQL GROUP BY 합산(SUM(MAX)≠MAX(SUM)) | 결정 | 사용자 확정 + 2번섹션 시설합 선례 |
| 분당 측정값 = `COALESCE(corr_val, raw_val)` + `quality_cd='GOOD'`(부분합) | 결정 | 사용자 확정. `ot-integration.md §3` PWI "집계 제외" 정합, HLV 미적용 |
| 분 단위 전량 반환(버킷 집계 아님) — 31일 상한으로 볼륨 제한 | 결정 | 사용자 "순시데이터" 명시. dba 버킷 집계 권고 미채택 |
| 조회기간 상한 31일 초과 → INVALID_SEARCH_PERIOD | 결정 | 신규 SearchDto `MAX_PERIOD_DAYS=31` |
| PWI 보유·데이터 0 설비 → 빈 points 시리즈 포함(예외 아님) | 결정 | 5·6번섹션 "0 설비 포함" 선례 + 차트 범례 일관. `coding-discipline.md §2` 정합 |
| 계측기 0건·PWI 0건 → 빈 IN/unnest 방어(즉시 빈 series 래퍼 200) | 결정 | DB 호출 전 사전 검사. 빈 unnest → CTE 0행 |
| `equip_type_cd` 무필터 — 제어·모드 평가 경로 아닌 물리 사실(PWI 태그 보유) 기준 조회 | 결정 | `ot-integration.md §5` 필터 강제 룰 적용 범위 밖. 3·5·6번섹션 선례 |
| 격리 READ_COMMITTED 유지(REPEATABLE_READ 미적용) | 결정 | 동형 선례 readOnly 기본. 과거 표시용 순시 시계열 팬텀 무시 |
| 헬퍼 `collectSubtree`·`findActiveFacilityOrThrow` 4번째 사본 → 동형 복제 유지 | 결정 | 사용자 결정 2026-06-09, 인용 주석 명기. 공통 추출 별도 사이클 |
| EXPLAIN (ANALYZE, BUFFERS) 검증 = 성공 기준 의무(미검증 배포 금지) | 결정 | 신규 native 쿼리 실행 계획 기준선. 31일 최대 2파티션 프루닝 |

## 제외 사항

- PWQ(적산전력량) 트렌드·누적·분포 — 4·5·6번섹션 별개 산출물.
- 버킷 집계(시/일 단위 다운샘플링) — 본 사이클 분 단위 전량 반환 확정, dba 권고는 향후 별도 사이클.
- 헬퍼 공통 추출 리팩토링(`collectSubtree`·`findActiveFacilityOrThrow` 4사본 통합) — 별도 ANALYZE 사이클.
- 기존 3개 서비스(`FacilityPowerInstrumentService`·`FacilityInstrumentEnergyUsageService`·`InstrumentEnergyTrendService`) 침습 — 무변경.
- 신규 어휘·DB 스키마·ErrorCode 등록 — 0건(전부 기존 재사용).

## 예상 산출물

- [태스크](../../../tasks/20260609/설비별사용량-7번섹션/TASK1.md) (다음 단계 `/dev:task` 에서 작성)
