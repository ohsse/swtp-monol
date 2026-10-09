---
status: approved
created: 2026-06-08
updated: 2026-06-08
---
# 시설별 사용량 2번섹션 — 운영시설 전력 사용량 조회 API

## 목적

`backend/image/시설별사용량.png` 대시보드 2번섹션을 구현한다. 1번섹션 파라미터 3종(**집계단위[시/일/월] · 검색시작일자 · 검색종료일자**)으로 **운영시설(FacilityGroup.OPERATION) 각각**의 전력 사용량 4지표를 조회하는 **읽기 전용 API** 1개를 추가한다.

| 지표 | 응답 필드 | 산출 |
|------|----------|------|
| 순시전력(kW) | `elpwr` | 조회기간 종료시점 직전 분(分)의 시설합 PWI (마지막값) |
| 전력량(kWh) | `elceg` | PWQ(적산전력량) 버킷 차분(MAX−MIN) 합 (GOOD-only, 음수 차분 제외) |
| 최대전력(kW) | `peakElpwr` | 버킷별 시설합 PWI MAX 중 최댓값 |
| 최대전력 일시 | `peakElpwrDtm` | 최대값이 발생한 버킷 시작시각(집계단위 입도) |

> **최대전력 해석**: `peakElpwr = MAX over buckets( MAX over minutes( 분별 시설합 PWI ) )`. 값은 전체기간 최대 분합과 수학적으로 동일 → 집계단위 불변, **`peakElpwrDtm`의 입도만** 집계단위에 따라 달라진다(시→시각, 일→일자, 월→월). 사용자 예시("시간단위로 max 순시전력 … 발생한 시간")와 정합.

## 배경

- 상위 분석: [ANALYZE1](../../../analyze/20260608/시설별사용량-2번섹션/ANALYZE1.md) (status: approved). 5인 회의 5개 안건, 블로커 0건(domain-expert B1·B2는 가정 섹션 명기로 해소).
- 사용자 사전 확정(AskUserQuestion): (1) 전력 측정 대상 = 시설 내 **모든 계측기**의 PWI/PWQ 합산, (2) **하위 시설 재귀 롤업**, (3) 데이터 부재 시 `null`.
- 신규 어휘/DB 스키마 0건 — 룰 갱신 불필요(ANALYZE 룰 갱신 지시서 "없음").

## 범위

**포함**:
- `FacilityController` 조회 엔드포인트 1개 추가 (`GET /api/facility/energy-usage`)
- 검색 DTO `FacilityEnergyUsageSearchDto` + 응답 DTO `FacilityEnergyUsageDto` 신규
- `FacilityEnergyUsageService` 신규 (6-SQL 고정 흐름 + 트리 롤업 in-memory 매핑)
- `RawDataCustomRepository(+Impl)` native SQL 2종 추가 + Repo 프로젝션 DTO `RawDataFacilitySumDto`
- `FacilityRepository` 파생 메서드 1개 추가 (common 모듈)
- `FacilityErrorCode.INVALID_SEARCH_PERIOD(400)` enum 값 추가

**제외**: [§제외 사항](#제외-사항) 참조.

## 도메인 모델

> **신규 도메인 엔티티·DB 테이블·DB 컬럼 0건.** 아래는 API 계층 산출물(DTO·Service·Repository 메서드)이며 도메인 엔티티가 아니다. 운영시설 종류 SSOT는 기존 `FacilityType.getGroup()` enum 파생으로 도출한다(하드코딩 회피).

| 산출물 | 계층 | 패키지 | 주요 필드/시그니처 |
|--------|------|--------|------------------|
| `FacilityEnergyUsageSearchDto` | 검색 DTO | `com.mo.swtp.facility.dto` | `fromDt:LocalDate` · `toDt:LocalDate` · `inqUnit:InqUnit` + `toStartDtm()` · `toEndExclusiveDtm()` · `isValid()` |
| `FacilityEnergyUsageDto` | 응답 DTO | `com.mo.swtp.facility.dto` | `facilityId` · `facilityNm` · `elpwr:BigDecimal` · `elceg:BigDecimal` · `peakElpwr:BigDecimal` · `peakElpwrDtm:LocalDateTime` (전부 null 허용) + 정적팩토리 `of(...)` |
| `RawDataFacilitySumDto` | Repo 프로젝션 | `com.mo.swtp.raw.dto` | record `(facilityId:String, dtm:LocalDateTime, value:BigDecimal)` — 마지막값·피크 공용 |
| `FacilityEnergyUsageService` | Service | `com.mo.swtp.facility.service` | `@Transactional(readOnly=true)` `findEnergyUsage(FacilityEnergyUsageSearchDto): List<FacilityEnergyUsageDto>` |
| 운영시설 종류 SSOT | enum 파생 | `com.mo.swtp.common.facility.domain.enumtype.FacilityType` | `getGroup()==OPERATION` 필터로 `OPERATION_TYPES` 도출 (PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR 8종) |

**DTO 설계 규칙**(ANALYZE 안건 4 확정):
- `FacilityEnergyUsageDto`는 `BaseAuditResponseDto` **미상속**(요약·집계 응답 — `api-patterns.md §BaseAuditResponseDto 패턴` 적용 외). `@Getter` + private 생성자 + 정적팩토리 `of(...)` + `peakElpwrDtm`에 `@JsonFormat(shape=STRING, pattern="yyyy-MM-dd HH:mm:ss")`. `FacilityOperatingStatusDto` 패턴 동형.
- `FacilityEnergyUsageSearchDto`는 instrument `PumpPeriodSearchDto` **상속 금지**(도메인 간 의존 회피) → facility 독립 동형 작성. `@ModelAttribute` 바인딩.
- `@Schema` 작성. enum 필드 `inqUnit`은 `@Schema(implementation = InqUnit.class)`(`api-patterns.md §DTO @Schema(implementation)` 의무).

## DB 설계 변경

**없음** — 읽기 전용 조회. 신규 테이블·컬럼·인덱스·파티션 0건. `docs/ddl/` 및 `db/migration/` 갱신 불필요.

기존 인덱스 활용: `rawdata_1m_h`의 `(tag_srl_no, acq_dtm)` 복합 인덱스(`idx_rawdata_1m_h_tag_time`) + 월 RANGE 파티션 프루닝으로 native 쿼리 2종이 정합(DBA 안건 3 승인).

## 구현 방향

### 1. Service 흐름 (`FacilityEnergyUsageService.findEnergyUsage`)

SQL 호출은 **시설 수와 무관하게 고정 횟수**(N+1 없음). 트리 롤업은 전부 Service 메모리 매핑.

1. `searchDto.isValid()` 검증 — 실패 시 `RestApiException(FacilityErrorCode.INVALID_SEARCH_PERIOD)`.
2. 기간 변환: `start=toStartDtm()`, `end=toEndExclusiveDtm()`, `unit=inqUnit.getDateTruncUnit()`.
3. **운영시설 카드 목록** 조회 — `findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(OPERATION_TYPES, YnType.Y)`. (카드 = OPERATION 8종 IN 필터 명시 → `facility_type_cd` 필터 강제 룰 준수)
4. **전체 활성 시설** 로드 → `parent_facility_id` 기준 parent→children 맵 구성 → 각 운영시설 BFS로 **하위 모든 시설(그룹 무관) 집합 + `facility_id → 최근접 운영루트` 맵** 산출. 운영시설 중첩 시 하위 운영시설에서 BFS 종단(중복 합산 방지, 가정 B1).
5. 위 시설 집합의 **모든 계측기** 조회 (`findByFacilityFacilityIdIn`, `use_yn=Y`, equip 종류 무필터).
6. 계측기의 **활성 PWI/PWQ 태그** 조회 (`tag_se_cd ∈ {PWI, PWQ}`, `use_yn=Y`). PWI·PWQ 분리 보관.
7. `태그 srl_no → 최근접 운영루트 facility_id` 매핑 2개 구성(이미 로드된 엔티티 — 추가 쿼리 0).
8. 집계 3회:
   - 순시전력: `findFacilityLatestMinuteSumElpwr(pwiTags, pwiRoots, start, end)` → `Map<facilityId, elpwr>`
   - 최대전력: `findFacilityBucketPeakElpwr(pwiTags, pwiRoots, start, end, unit)` → `Map<facilityId, (peakElpwr, peakElpwrDtm)>`
   - 전력량: `findEnergyDeltaBuckets(pwqTags, start, end, unit)` 재사용 → `validDeltaOrNull`(음수 제외) 후 `pwqTag→운영루트` 그룹 합산 → `Map<facilityId, elceg>`
9. 운영시설 순회 → 맵 lookup → `FacilityEnergyUsageDto.of(...)`(부재 시 null). 정렬(dispOrd→facilityNm) 보존.

**메서드 분해**(ANALYZE 안건 2·4 — 면책 불가 영역): 퍼블릭 `findEnergyUsage`는 `buildOperatingRootMap(...)`·`aggregate(...)`·`assemble(...)` private 헬퍼로 분해해 50줄 이내 + 호출 스택 Controller→Service→헬퍼 2단(3단 이내) 유지.

### 2. 신규 native SQL 2종 — `§2.5 면책 (query-tuning.md §2)`

분별 시설합은 **반드시 SQL의 같은 `acq_dtm`에서 SUM** 후 버킷 MAX (`SUM(MAX)≠MAX(SUM)` 함정 회피). 각 메서드 본문 상단에 인용 주석 의무(누락 시 REVIEW 블로커).

**A. 최대전력 + 발생시각** (`findFacilityBucketPeakElpwr` — 시설별 ARGMAX):
```sql
-- §2.5 면책 (query-tuning.md §2 — 복잡 집계 빌더, 분별 시설합→버킷 MAX→ARGMAX 단일 흐름)
WITH tag_facility(tag_srl_no, facility_id) AS (SELECT * FROM unnest(:tags::text[], :roots::text[])),
minute_sum AS (
  SELECT tf.facility_id, r.acq_dtm, SUM(COALESCE(r.corr_val, r.raw_val)) AS minute_sum
  FROM rawdata_1m_h r JOIN tag_facility tf ON tf.tag_srl_no = r.tag_srl_no
  WHERE r.acq_dtm >= :startDtm AND r.acq_dtm < :endDtm AND r.quality_cd = 'GOOD'
  GROUP BY tf.facility_id, r.acq_dtm),
bucket_max AS (
  SELECT facility_id, date_trunc(:unit, acq_dtm) AS base_dtm, MAX(minute_sum) AS bucket_max
  FROM minute_sum GROUP BY facility_id, date_trunc(:unit, acq_dtm))
SELECT DISTINCT ON (facility_id) facility_id, base_dtm, bucket_max
FROM bucket_max ORDER BY facility_id, bucket_max DESC, base_dtm ASC;
```
**B. 순시전력 마지막값** (`findFacilityLatestMinuteSumElpwr` — 시설별 최종 분합):
```sql
-- §2.5 면책 (query-tuning.md §2 — 분별 시설합 후 시설별 최신 1행 추출)
WITH tag_facility(tag_srl_no, facility_id) AS (SELECT * FROM unnest(:tags::text[], :roots::text[])),
minute_sum AS ( ...A와 동일... )
SELECT DISTINCT ON (facility_id) facility_id, acq_dtm, minute_sum
FROM minute_sum ORDER BY facility_id, acq_dtm DESC;
```
- `acq_dtm >= :start AND < :end` 월 RANGE 파티션 프루닝 강제. `quality_cd='GOOD'` + `COALESCE(corr_val, raw_val)` = effectiveVal.
- 기존 `findLatestByTagSrlNos`(NOW()−1h 윈도우)는 과거기간 조회 부적합 → B로 신규(동형 복제, 사이클 간 자동 원용 금지 정합).

### 3. unnest 바인드 전략 (DBA W2 결정)

- **1차**: Hibernate native query에 `String[]` 바인드 + `::text[]` 캐스트(위 SQL).
- **폴백**(1차가 `text[]` 자동 변환 실패 시 — IMPL PoC에서 확정): `RawDataCustomRepositoryImpl`에서 `EntityManager.unwrap(Session.class).doReturningWork(conn -> conn.createArrayOf("text", arr))`로 명시 배열 생성, 또는 `unnest`를 다중 행 `VALUES (:t0,:r0),(:t1,:r1),...` 동적 생성으로 대체. IMPL 첫 단계에서 1차 동작을 통합 테스트로 검증 후 분기.

### 4. Controller 엔드포인트

- `GET /api/facility/energy-usage`, `@ModelAttribute FacilityEnergyUsageSearchDto`, 응답 `ResponseEntity<CommonResponseDto<List<FacilityEnergyUsageDto>>>`.
- `CommonController` 상속, `getResponseEntity(data)` 래핑. `@Tag("06. 시설물 관리")`(기존 FacilityController Tag 정합) + `@Operation` + `@ApiResponses` 200/400/401/403/500.

### 5. 재사용/복제 자산

| 자산 | 처리 | 근거 |
|------|------|------|
| `InqUnit`(HOUR/DAY/MONTH/YEAR, `getDateTruncUnit()`) | 재사용 | common 공용 enum |
| `RawDataCustomRepositoryImpl.findEnergyDeltaBuckets` | 재사용 | 전력량(PWQ) 산출 동일 |
| `FacilityOperatingStatusDto` 응답 패턴 | 동형 | BaseAuditResponseDto 미상속 + 정적팩토리 |
| `PeakEnergyTrendService.validDeltaOrNull`(음수차분 제외) | 동형 복제 | 사이클 간 자동 원용 금지(2건 누적 — common 추출은 3건+ 시 별도 ANALYZE) |
| `PumpPeriodSearchDto.toStartDtm()/toEndExclusiveDtm()` | 동형 복제 | 도메인 간 의존 회피 |
| `PumpSummaryService`/`FacilityOperatingStatusService` in-memory 집계 구조 | 미러링 | N+1 회피 패턴 |

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| 한 시설 PWI 다태그 분별 SUM 후 버킷 MAX 정확(`SUM(MAX)≠MAX(SUM)` 회피) | 단위 테스트 — 2태그 같은 분 합산값이 버킷 max 로 산출, GREEN |
| 음수 차분(PWQ 카운터 리셋) 버킷 전력량 합산 제외 | 단위 테스트 — 음수 delta 버킷 미합산 + WARN 로그, GREEN |
| 전구간 BAD 품질 시 4지표 null | 단위 테스트 — GOOD 0행 → DTO 4필드 null, GREEN |
| PWI만/PWQ만 보유 시 해당 지표만 산출(나머지 null) | 단위 테스트 — 지표별 독립 맵 lookup, GREEN |
| 하위 시설 롤업 — 자식 시설 계측기 전력이 최근접 운영조상 카드에 합산 | 단위 테스트 — 트리 매핑(중첩 운영시설 BFS 종단 포함), GREEN |
| 운영시설 카드 = OPERATION 8종만(STORAGE/NETWORK 미출현) | 단위 테스트 — 결과 facilityType 전부 OPERATION, GREEN |
| 동률 최대전력 시 가장 이른 버킷 선택 | 단위 테스트 — 동일 bucket_max 2버킷 중 base_dtm 작은 값, GREEN |
| 검색기간 상한 초과 시 INVALID_SEARCH_PERIOD | 단위 테스트 — 13개월 초과 입력 → RestApiException, GREEN |
| 집계단위 YEAR 거부 | 단위 테스트 — inqUnit=YEAR → isValid() false → INVALID_SEARCH_PERIOD, GREEN |
| native 쿼리 파티션 프루닝 작동 | 통합 테스트 — `EXPLAIN (ANALYZE, BUFFERS)` 파티션 제한 확인(로컬 PostgreSQL, 월 파티션 선행 생성) |
| 빌드 통과 | `./gradlew.bat :common:build` 후 `./gradlew.bat :api:test` BUILD SUCCESSFUL |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 롤업 귀속 = 각 태그를 **최근접 운영시설 조상**에 귀속(중첩 시 중복 합산 방지) | 가정 → 결정 | 채택. BFS가 하위 운영시설에서 종단 |
| 롤업 멤버십 = 운영시설 + 하위 **모든 시설(그룹 무관)** 계측기. 카드만 OPERATION 8종 | 가정 → 결정 | 채택. 하위 STORAGE/NETWORK 전력도 상위 운영시설 카드에 합산 |
| 집계단위 = HOUR/DAY/MONTH만, YEAR 거부 | 결정 | `isValid()`에서 YEAR 거부 |
| 음수 차분(PWQ 카운터 리셋) = 전량 제외 + WARN 로그 | 결정 | `validDeltaOrNull` 동형 |
| 최대전력일시 입도 = 버킷 시작시각 | 결정 | `date_trunc(:unit, ...)` 버킷 시작 |
| 동률 최대전력 = 가장 이른 버킷(base_dtm ASC) | 가정 → 결정 | 채택(결정론적). "가장 최근" 요건이면 승인 게이트에서 DESC 변경 |
| 데이터 부재 = 4지표 null("측정 없음", 0kW와 구분) | 결정 | DTO 4필드 null 허용 |
| **조회기간 상한** | 미해결 → 결정 | **`fromDt`~`toDt` 간격 > 13개월(396일) 시 INVALID_SEARCH_PERIOD**. 근거: `rawdata_1m_h` 13개월 롤링 보존 — 보존 초과 구간은 데이터 부재이며 12+ 파티션 풀스캔 슬로우쿼리 방지(DBA W1). 더 엄격한 1년 상한 필요 시 승인 게이트에서 조정 |
| **unnest 바인드** | 미해결 → 결정 | 1차 `String[]`+`::text[]` 캐스트, IMPL PoC 통합 테스트 검증 후 실패 시 `createArrayOf`/VALUES 폴백(§구현 방향 3) |

## 제외 사항

- 1번섹션(파라미터 UI) — frontend 책임.
- 시설별 사용량 **트렌드/차트**(시계열 추이) — 별도 섹션.
- 전력 외 지표(유량·압력·수위) — 본 섹션 범위 외.
- 응답 캐싱·비동기 — 6-SQL 고정으로 현 시점 불요.
- 조회기간 상한 외 페이지네이션 — 운영시설 카드 수 제한적(수십 건)이라 불요.

## 부록: 도메인/DB 검토 결과

PLAN 도메인·DB 검토 게이트는 **생략**한다. 사유:
1. `## 도메인 모델`에 신규 도메인 엔티티·DB 테이블·DB 필드 **0건**(API 계층 DTO·Service·Repository 메서드만).
2. `## DB 설계 변경` **없음**(스키마 변경 0).
3. ANALYZE1 5인 회의에서 `wtp-domain-expert`(안건 2·5)·`wtp-dba-reviewer`(안건 3)가 이미 도메인·DB를 검토하여 **블로커 0건**(B1·B2는 본 PLAN 가정 섹션 명기로 해소). PLAN/REVIEW 중복 검증 회피 경계(skill §5b) 정합.

- wtp-domain-expert(ANALYZE 안건 2·5): 블로커 0건(가정 명기로 해소), 권고 — 음수차분/최대전력일시 입도 가정 명기(반영 완료).
- wtp-dba-reviewer(ANALYZE 안건 3): 블로커 0건, 권고 2건(조회기간 상한·unnest 폴백 — 본 PLAN 결정으로 반영), 참고 1건(동률 정책 — 가장 이른 버킷 채택).

## 예상 산출물
- [태스크](../../../tasks/20260608/시설별사용량-2번섹션/TASK1.md)
