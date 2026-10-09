---
status: completed
created: 2026-05-27
updated: 2026-05-27
---
# 운전현황분석-10번섹션 — 시설 단위 금일 하루치 계측+예측 시계열 조회 API

## 목적

운전현황 분석 화면 10번 섹션 "전력원단위(운영:계측|예측) + 펌프가동대수(예측) 시계열" 카드의 backend API 를 신설한다. 활성 시설(`facility_m`, `use_yn = Y`, `facility_type ∈ {PWTF, DWT, PRSF}`) 단위로 **금일 하루치 (00:00 ~ 23:59 최대 1440 포인트)** 1분 단위 시계열을 응답한다. 각 시점은 계측(자정~현재시간) + 예측(자정~익일자정) 데이터를 묶음 포함하며, 한쪽이라도 존재하는 분만 포함한다.

각 1분 시점 8필드:
- `dtm` — 시점 (LocalDateTime, 초 단위 SSOT)
- `actualElpwrAmt`·`actualFlwrt`·`actualUnitQty` — 계측 (자정~현재시간 분, 존재 시 채움)
- `predcElpwrAmt`·`predcFlwrt`·`predcUnitQty` — 예측 (자정~익일자정 분, 존재 시 채움)
- `predcPumpOnCnt` — 예측 펌프 가동대수

전력원단위 산정 정책은 4·5·9번 섹션과 1:1 일치하며, 본 사이클은 계측 (5번섹션) + 예측 (9번섹션) 정책을 1440 포인트 일일 시계열로 결합한다.

## 배경

- 4번 섹션 = "현재 1분 시점 계측 1건" / 5번 섹션 = "금일·비교 계측 시계열 N건" / 9번 섹션 = "현재 시점 예측 1건" / **10번 섹션 = "금일 하루치 계측+예측 시계열"**
- 사용자 결정 (ANALYZE1 토론):
  - Q1: 유출유량 = 시설의 FLWMTR 자식 FRI 태그 (9번섹션 `selectFacilityFri` 재사용)
  - Q2: 1분 간격, 자정~23:59 단일 시계열 1440 포인트
  - Q7: 원본 + 집계 모두 7필드 + `predcPumpOnCnt` = 8필드
  - Q8: 자정~현재 구간에도 계측·예측 모두 표출 (예측 정확도 시각 검증)
  - Q9: 신규 엔드포인트 `daily-time-series`
  - **신규 결정 1**: `serverNow` 메타 필드 **미포함** (actual 마지막 `dtm` 이 분기점 자연 역할)
  - **신규 결정 2**: 데이터 부재 분 처리 — **(B) 생략** ("데이터가 존재한다면" 표출)
- ANALYZE1.md (2026-05-27, `status: approved`) 5인 회의 결론:
  - 신규 표준 단어·데이터 도메인·표준 용어 0건 (DB 컬럼 영향 0건)
  - Repository 분리 (DBA·Backend 권고) — 신규 `TagPredcRangeCustomRepository` 작성, 9번섹션 `TagPredcLatestRepository` 무수정
  - 헬퍼 추출 본 사이클 외 — 5번섹션 PLAN1 §4-1 결정 그대로 (재구현 유지, 별도 ANALYZE 사이클로 이연)
  - `buildSeries` 3단계 분해 의무 (§2.5 면책 불가)
  - 도메인 4영역 모두 비해당 (조회 전용, 알람/제어/AI 모드/이력 INSERT 0건)

## 범위

### 포함

- `FacilityDailyTimeSeriesDto` + static inner `DailyTimeSeriesPoint` 신규 작성
- `FacilityDailyTimeSeriesService` 신규 작성 (4·5·9번 헬퍼 재구현)
- `TagPredcRangeDto` 신규 작성 (Repository 결과 record DTO)
- `TagPredcRangeCustomRepository` + `TagPredcRangeCustomRepositoryImpl` + `TagPredcRangeRepository` 신규 작성 (예측 시계열 범위 조회)
- `FacilityController.findFacilityDailyTimeSeries` 엔드포인트 메서드 추가
- 단위 테스트 `FacilityDailyTimeSeriesServiceTest` 신규 작성

### 제외

- 9번섹션 `TagPredcLatestRepository` 수정 (Repository 분리 채택 — `coding-discipline.md §3` 정합)
- 4·5·9번 섹션 코드 수정 — 헬퍼 추출은 본 사이클 외 (별도 ANALYZE 사이클 — `FacilityOperatingStatusSupport` 또는 abstract Service 후보)
- `actualOnPumpCnt` 응답 필드 — 사용자 Q4 결정 (펌프 가동대수는 예측만)
- 5번섹션 비교 옵션 (`YESTERDAY`/`LAST_WEEK`) — 본 사이클은 금일 하루치 전용
- `serverNow` 메타 필드 — 사용자 결정 1 (미포함)
- 데이터 부재 분 강제 포함 (옵션 A) — 사용자 결정 2 (옵션 B 채택)
- DB 스키마 변경 (조회 전용)
- `FacilityErrorCode` 신규 추가 (`FACILITY_NOT_FOUND`·`UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` 4번섹션 정의 재사용)

## 구현 방향

### 1. API 엔드포인트

```
GET /api/facility/{facilityId}/operating-status/daily-time-series
```

- `FacilityController` 에 메서드 추가 — 4·5·9번 섹션과 동일 파일
- 응답 타입: `ResponseEntity<CommonResponseDto<FacilityDailyTimeSeriesDto>>`
- Swagger `@Tag` 재사용 (`FacilityController` 클래스 레벨 Tag)
- ErrorCode 재사용: `FACILITY_NOT_FOUND` · `UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS`

### 2. DTO 설계

`api/src/main/java/com/mo/swtp/facility/dto/FacilityDailyTimeSeriesDto.java`

```java
@Getter
@Schema(description = "시설 단위 금일 하루치 계측+예측 시계열 — 운전현황분석 10번 섹션")
public class FacilityDailyTimeSeriesDto {

    @Schema(description = "시설 ID")
    private String facilityId;

    @Schema(description = "시설명")
    private String facilityNm;

    @ArraySchema(schema = @Schema(
            description = "1분 시점 시계열 (dtm 오름차순). 한쪽이라도 존재하는 분만 포함, 최대 1440 포인트",
            implementation = DailyTimeSeriesPoint.class))
    private List<DailyTimeSeriesPoint> points;

    private FacilityDailyTimeSeriesDto() {}

    public static FacilityDailyTimeSeriesDto of(
            String facilityId, String facilityNm, List<DailyTimeSeriesPoint> points) { ... }

    @Getter
    @Schema(description = "1분 시점 운영 현황 (계측·예측 합본)")
    public static class DailyTimeSeriesPoint {

        @Schema(description = "1분 시점 (계측 또는 예측 시각)", example = "2026-05-27 14:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dtm;

        @Schema(description = "계측 — On 펌프 PWI GOOD 합산 (kW). 미도래 시각 또는 BAD/UNCERTAIN/부재 시 null")
        private BigDecimal actualElpwrAmt;

        @Schema(description = "계측 — 시설 FRI GOOD effectiveVal (m³/h). 미도래 시각 또는 BAD/UNCERTAIN/부재 시 null")
        private BigDecimal actualFlwrt;

        @Schema(description = "계측 — 전력원단위 (kWh/m³, actualElpwrAmt/actualFlwrt). 분자/분모 0/NULL/BAD/부재 시 null")
        private BigDecimal actualUnitQty;

        @Schema(description = "예측 — On 펌프 PWI predc_val 합산 (kW). predc 부재 시 null")
        private BigDecimal predcElpwrAmt;

        @Schema(description = "예측 — 시설 FRI predc_val (m³/h). predc 부재 시 null")
        private BigDecimal predcFlwrt;

        @Schema(description = "예측 — 전력원단위 (kWh/m³, predcElpwrAmt/predcFlwrt). 분자/분모 0/NULL/부재 시 null")
        private BigDecimal predcUnitQty;

        @Schema(description = "예측 — On 펌프 개수 (OPS predc_val == 1.0 카운트). predc 부재 시 null")
        private Integer predcPumpOnCnt;

        private DailyTimeSeriesPoint() {}

        public static DailyTimeSeriesPoint of(LocalDateTime dtm,
                BigDecimal actualElpwrAmt, BigDecimal actualFlwrt, BigDecimal actualUnitQty,
                BigDecimal predcElpwrAmt, BigDecimal predcFlwrt, BigDecimal predcUnitQty,
                Integer predcPumpOnCnt) { ... }
    }
}
```

- `BaseAuditResponseDto` 미상속 — 4·5·9번 섹션 동일 분류 (단순 조회 응답, `api-patterns.md §BaseAuditResponseDto 적용 범위` 정합)
- `DailyTimeSeriesPoint` 는 **static inner class** — 외부 재사용 사례 없음 (`coding-discipline.md §2` "요청되지 않은 추상화 금지", 5번섹션 `TimeSeriesPoint` 선례 동형)
- `@JsonFormat` 초 단위 SSOT — 4·5·9번 정렬
- 모든 필드 `@Schema(description)` 한국어 명시 — actual NULL vs predc NULL 의미 분리 (ANALYZE 안건 4 권고)

### 3. Repository 결과 DTO

`api/src/main/java/com/mo/swtp/opt/dto/TagPredcRangeDto.java`

```java
/**
 * 예측 시계열 범위 조회 결과 record DTO.
 *
 * <p>운전현황분석-10번섹션 — 자정~익일자정 1440 슬롯 범위. 9번섹션 {@link TagPredcLatestDto}
 * 의 단일 시점 추출 패턴과 의도 분리 (Repository 분리 정합).</p>
 */
public record TagPredcRangeDto(
        String tagSrlNo,
        LocalDateTime predcDtm,
        BigDecimal predcVal
) {}
```

3필드 — 9번섹션 `TagPredcLatestDto` 와 동일 구조 (`predc_1m_h` 에 `quality_cd`·`corr_val` 컬럼 부재, `predc_val` 단일).

### 4. Repository 신규 분리

`api/src/main/java/com/mo/swtp/opt/repository/TagPredcRangeCustomRepository.java`

```java
public interface TagPredcRangeCustomRepository {
    List<TagPredcRangeDto> findByTagSrlNosAndPredcDtmRange(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);
}
```

`api/src/main/java/com/mo/swtp/opt/repository/TagPredcRangeRepository.java`

```java
public interface TagPredcRangeRepository
        extends JpaRepository<TagPrediction, TagPredictionId>, TagPredcRangeCustomRepository {
}
```

`api/src/main/java/com/mo/swtp/opt/repository/TagPredcRangeCustomRepositoryImpl.java`

```java
@Repository
@RequiredArgsConstructor
public class TagPredcRangeCustomRepositoryImpl implements TagPredcRangeCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<TagPredcRangeDto> findByTagSrlNosAndPredcDtmRange(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        QTagPrediction t = QTagPrediction.tagPrediction;
        return queryFactory
                .select(Projections.constructor(TagPredcRangeDto.class,
                        t.id.tagSrlNo, t.id.predcDtm, t.predcVal))
                .from(t)
                .where(t.id.tagSrlNo.in(tagSrlNos)
                        .and(t.id.predcDtm.goe(startDtm))
                        .and(t.id.predcDtm.lt(endDtm)))
                .orderBy(t.id.predcDtm.asc(), t.id.tagSrlNo.asc())
                .fetch();
    }
}
```

- 9번섹션 `TagPredcLatestRepository` 무수정 (의도 분리 — `DISTINCT ON + NOW() - 1h` 단일 최신값 vs `BETWEEN` 전체 범위)
- 인덱스 `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)` ASC 활용 — IN 등가 선행 + 범위·정렬 후행 (`indexing-and-migration.md §1` 정합)
- 파티션 프루닝 — `predc_dtm BETWEEN` 자동 활성 (`partitioning-and-retention.md §1`)
- Querydsl JPAQuery (5번섹션 `RawDataCustomRepository.findByTagSrlNosAndDtmRange` 패턴 동형)
- `TagPrediction` 엔티티 + `TagPredictionId` 복합 PK 재사용 (9번섹션 사이클에서 도입)

### 5. 계측 Repository 재사용

5번섹션의 `RawDataCustomRepository.findByTagSrlNosAndDtmRange` **재사용** — 신규 메서드 0건. 호출 시점:
- `startDtm` = `LocalDate.now().atStartOfDay()` (자정)
- `endDtm` = `LocalDateTime.now()` (현재시간)

### 6. Service 책임 분담

`api/src/main/java/com/mo/swtp/facility/service/FacilityDailyTimeSeriesService.java`

- 6-SELECT 패턴 (Facility 1 + Instrument 1 + Tag 1 + RawData range 1 + Predc range 1 + facility 검증 통합 1)
- 클래스 레벨 `@Transactional(readOnly = true)` — `query-tuning.md §1` 정합
- 메서드 50줄 이내 + 추상화 호출 스택 3단 이하 의무 (`coding-discipline.md §2.1`)

핵심 메서드 구조:

```
findFacilityDailyTimeSeries(facilityId)
  ├─ findActiveFacilityOrThrow(facilityId)          // 4·5·9번 재구현
  ├─ findByFacilityIdAndEquipType(PUMP, FLWMTR)     // instrument
  ├─ loadTagsByInstrument(instruments)              // OPS/PWI/FRI 필터 + 인스트루먼트별 그룹화
  ├─ resolveActualRange() / resolvePredcRange()     // [today 00:00, NOW] / [today 00:00, tomorrow 00:00)
  ├─ rawDataRepository.findByTagSrlNosAndDtmRange(tagSrlNos, today00, now)
  ├─ tagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange(tagSrlNos, today00, tomorrow00)
  ├─ buildSeries(actuals, predcs, tagsByInstrument, instruments)
  └─ FacilityDailyTimeSeriesDto.of(facilityId, facilityNm, points)
```

#### 6-1. `buildSeries` 3단계 분해 (§2.5 면책 불가 → 분해 의무)

ANALYZE 안건 3 블로커 Q2 결정 — 각 메서드 50줄 이내 보장.

```
buildSeries(actuals, predcs, tagsByInstrument, instruments)
  ├─ Step 1: buildActualSlotMap(actuals, tagsByInstrument, instruments)
  │     // 입력: List<RawDataLatestDto>
  │     // 처리: dtm 기준 그룹화 → 각 슬롯에 actualElpwrAmt·actualFlwrt·actualUnitQty 계산
  │     // 출력: TreeMap<LocalDateTime, PointParts> — actual 채움, predc 필드는 null
  ├─ Step 2: overlayPredcSlots(combinedMap, predcs, tagsByInstrument, instruments)
  │     // 입력: Step 1 결과 Map + List<TagPredcRangeDto>
  │     // 처리: predc dtm 기준 그룹화 → 슬롯에 predcElpwrAmt·predcFlwrt·predcUnitQty·predcPumpOnCnt 갱신
  │     //       Map 키가 없으면 신규 생성, 있으면 predc 필드만 채움
  │     // 출력: TreeMap (양쪽 합본)
  └─ Step 3: toSortedPoints(combinedMap)
        // 입력: TreeMap<LocalDateTime, PointParts>
        // 처리: TreeMap 자체가 dtm 오름차순 → entrySet 순회로 DailyTimeSeriesPoint 변환
        // 출력: List<DailyTimeSeriesPoint>
```

내부 record (Service private):
```java
private record PointParts(
        BigDecimal actualElpwrAmt, BigDecimal actualFlwrt, BigDecimal actualUnitQty,
        BigDecimal predcElpwrAmt, BigDecimal predcFlwrt, BigDecimal predcUnitQty,
        Integer predcPumpOnCnt) {}
```

`TreeMap` 사용 — `dtm` 자연 정렬 보장 + Step 3 의 별도 `Stream.sorted` 불필요.

#### 6-2. 4·5·9번 헬퍼 재구현 (본 사이클 추출 외)

다음 헬퍼는 4·5·9번 섹션과 동일 시그니처·정책으로 본 Service 내부에 재구현 (private 메서드):

| 헬퍼 | 정책 | 재사용 출처 |
|------|------|-----------|
| `findActiveFacilityOrThrow(facilityId)` | `use_yn = Y` + `facility_type ∈ {PWTF, DWT, PRSF}` 검증 | 4·5·9번 |
| `loadTagsByInstrument(instruments)` | OPS/PWI/FRI 필터 + 인스트루먼트별 그룹화 | 4·5·9번 |
| `filterPumps(instruments)` | `equip_type_cd = 'PUMP'` 필터 강제 | 4·5·9번 |
| `effectiveVal(r)` | `corrVal != null ? corrVal : rawVal` | 4·5번 (actual 전용) |
| `isPumpRunningActual(ops)` | `qualityCd == GOOD && effectiveVal == 1.0` | 4·5번 |
| `isPumpRunningPredc(ops)` | `predcVal == 1.0` (단일값, quality 없음) | 9번 |
| `sumOnPumpPwrActual(onPumps, tags, latestByTag)` | PWI GOOD 합산 | 4·5번 |
| `sumOnPumpPwrPredc(onPumps, tags, latestByTag)` | PWI predc_val 합산 | 9번 |
| `selectFacilityFriActual(instruments, tags, latestByTag)` | 첫 GOOD effectiveVal | 4·5번 |
| `selectFacilityFriPredc(instruments, tags, latestByTag)` | 첫 predc_val | 9번 |
| `computeUnitConsumption(totalPwr, fri)` | 분자/분모 0·NULL·BAD·부재 → null. 정상 시 `divide(scale=4, HALF_UP)` | 4·5·9번 동일 |

추출 트리거 — "사용 사례 4건 누적 (4·5·9·10번)" 초과. 그러나 본 사이클 추출 시 4·5·9번 기존 코드 수정 필요 → `coding-discipline.md §3` "본인이 만든 코드 뒷정리만" 위반 위험. **본 사이클은 재구현 유지**. 추출은 별도 ANALYZE 사이클 (`FacilityOperatingStatusSupport` 또는 abstract Service 후보). 사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합.

#### 6-3. 시간 범위 결정

```java
private record TimeRange(LocalDateTime start, LocalDateTime end) {}

private TimeRange resolveActualRange() {
    LocalDateTime now = LocalDateTime.now();
    return new TimeRange(now.toLocalDate().atStartOfDay(), now);
}

private TimeRange resolvePredcRange() {
    LocalDate today = LocalDate.now();
    return new TimeRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
}
```

- `TimeRange` 는 Service 내부 record — 외부 노출 없음 (`coding-discipline.md §2` 추상화 금지)
- actual end = `LocalDateTime.now()` 사용 — Repository 가 `predc_dtm.lt(endDtm)` (exclusive) 적용
- predc end = `today.plusDays(1).atStartOfDay()` exclusive — 자정~익일자정 정확 1440 슬롯

#### 6-4. 데이터 부재 시점 처리 (사용자 결정 2 — 옵션 B)

- 어떤 분에 actual·predc 모두 부재 → 응답 `points` 리스트에서 자동 생략 (TreeMap 키 자체가 없음)
- 한쪽만 존재 → 슬롯 포함, 부재 측 필드는 NULL
- frontend 가 그래프 X축 연속 보장 필요 시 결측 슬롯 보간 (gap fill) 직접 처리

#### 6-5. 트랜잭션 정책

- 클래스 레벨 `@Transactional(readOnly = true)` — 조회 전용, 4·5·9번 동일 패턴

## 도메인 모델

본 사이클은 신규 엔티티·테이블·필드 0건. 도메인 모델 변경 없음 — `wtp-domain-expert` 검토 게이트 생략.

| 엔티티/테이블 | 변경 | 비고 |
|------------|------|------|
| `Facility` (`facility_m`) | 변경 없음 | 활성 검증 + 시설 종류 필터 재구현만 |
| `Instrument` (`instrument_m`) | 변경 없음 | PUMP/FLWMTR 자식 조회 재사용 |
| `Tag` (`tag_m`) | 변경 없음 | OPS/PWI/FRI 필터 재사용 |
| `RawData` (`rawdata_1m_h`) | 변경 없음 | 5번섹션 `findByTagSrlNosAndDtmRange` 재사용 |
| `TagPrediction` (`predc_1m_h`) | 변경 없음 | 9번섹션 엔티티 재사용, 신규 Repository 분리 |

## DB 설계 변경

**없음** — 조회 전용 신규 API. 인덱스·파티션·컬럼 변경 0건. `wtp-dba-reviewer` 검토 게이트 생략.

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 방법 |
|------|---------|
| `FacilityDailyTimeSeriesService` 단위 테스트 9건 이상 GREEN | `./gradlew.bat :api:test --tests "FacilityDailyTimeSeriesServiceTest"` PASS — 시나리오: (1) 정상 시계열 (actual+predc 공존 + predc 단독), (2) 자정~현재 actual+predc 공존, (3) 현재~익일자정 predc 단독, (4) 분 결측 양쪽 모두 부재 시 슬롯 생략, (5) OPS predc_val 경계값 (null/0.0/1.0/1.5) parametrized, (6) FRI predc 분모 무효 parametrized (null/0), (7) VALVE 자식 제외 (equip_type_cd=PUMP 필터), (8) RSV/POINT 거부 (UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS), (9) 비활성/존재 X 시설 거부 (FACILITY_NOT_FOUND) |
| 빌드 BUILD SUCCESSFUL | `./gradlew.bat build` 출력에 `BUILD SUCCESSFUL` 포함 |
| Swagger UI 에 신규 엔드포인트 등록 + DTO 8필드 모두 노출 | `:api:bootRun` 로컬 기동 후 Swagger UI 에서 `GET /api/facility/{facilityId}/operating-status/daily-time-series` 노출 + `DailyTimeSeriesPoint` 8필드 `@Schema(description)` 한국어 명시 + actual NULL vs predc NULL 의미 분리 description 확인 |
| 단일 시설 조회 응답 시간 200ms 이내 | 로컬 PostgreSQL 픽스처 (PUMP 3대 + FLWMTR 1대, actual 720분 + predc 1440분) 투입 후 Swagger 수동 호출 — p6spy 로그 합계 200ms 이내 |
| 파티션 프루닝 활성화 (actual + predc 양쪽) | `EXPLAIN ANALYZE` 출력에 `Partitions: rawdata_1m_h_YYYYMM` + `predc_1m_h_YYYYMM` 만 노출 + `Index Scan using idx_*_tag_time` 노드 확인 |
| `buildSeries` 3단계 분해 — 각 메서드 50줄 이내 | 신규 Service 파일 — `buildActualSlotMap`·`overlayPredcSlots`·`toSortedPoints` 각 메서드 본문 50줄 이내 (빈 줄·주석 제외) |
| 응답 직렬화 검증 | 단위 테스트에서 `objectMapper.writeValueAsString(dto)` 시 `dtm` 이 `yyyy-MM-dd HH:mm:ss` 포맷 + `points` 리스트 8필드 모두 노출 + `actualXxx` / `predcXxx` 누락 시 `null` 직렬화 |
| 응답 정렬 — `points` 가 `dtm` 오름차순 | 단위 테스트에서 `points.stream().map(p -> p.getDtm()).reduce((a, b) -> a.isAfter(b) ? null : b)` 가 null 아님 검증 |
| 부재 분 슬롯 생략 — 양쪽 모두 부재 시 응답 미포함 | 단위 테스트 — actual·predc 모두 부재인 09:00 슬롯 픽스처 → `points` 리스트에 09:00 키 없음 검증 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 다중 태그 actual 동기 수집 가정 — 같은 1분 시점의 OPS/PWI/FRI 가 동일 `acq_dtm` 값을 갖는다 (5번섹션 안건 4 Q3 정합) | 가정 → 결정 | **유지** — SCADA 수집 파이프라인이 1분 슬롯 단위로 동기 수집. 동기 미보장 시 미일치 태그 누락 허용 (시점 결측 자동 생략). TASK 단계 단위 테스트에서 동일 `acq_dtm` 픽스처로 검증 |
| 다중 태그 predc 동기 작성 가정 — 같은 1분 시점의 OPS/PWI/FRI 가 AI 추론 파이프라인에 의해 동일 `predc_dtm` 슬롯에 일괄 작성된다 | 가정 → 결정 | **유지** — predc 1440 슬롯 일괄 작성 가정. 미보장 시 시점 결측 자동 생략 (옵션 B 정책 정합) |
| `serverNow` 메타 필드 포함 여부 | 결정 (ANALYZE 1) | **미포함** — actual 시리즈 마지막 `dtm` 이 분기점 자연 역할. `FacilityDailyTimeSeriesDto` 3필드 유지 |
| 데이터 부재 분 처리 | 결정 (ANALYZE 2) | **옵션 B (생략)** — TreeMap 에 키가 없으면 응답 미포함. 양쪽 모두 부재 시만 슬롯 자체 생략, 한쪽이라도 존재하면 포함 (부재 측 NULL) |
| 헬퍼 추출 방향 | 결정 (ANALYZE 안건 3) | **본 사이클 외** — 재구현 유지. 별도 ANALYZE 사이클로 이연 (`FacilityOperatingStatusSupport` 또는 abstract Service 후보) |
| 시간 범위 정의 | 결정 | actual = `today.atStartOfDay()` ~ `LocalDateTime.now()` (exclusive 종점). predc = `today.atStartOfDay()` ~ `today.plusDays(1).atStartOfDay()` (exclusive). 서버 시간대 (Asia/Seoul) |
| 응답 크기 SLA | 결정 | 호출당 200ms · 응답 약 ~400KB. actual 평균 720 + predc 1440 = 합본 후 1440 슬롯 (predc 가 모두 채우므로 actual 슬롯이 predc 에 흡수). DB→JVM 중간 결과 추정 1440 × N태그 (N=PUMP×2+FLWMTR×1) ≈ 10,000행. `EXPLAIN ANALYZE` + p6spy TASK 단계 검증 |
| 응답 정렬 | 결정 | `dtm` 오름차순 — `TreeMap` 자연 정렬 + 단위 테스트 검증 |
| OPS predc_val 판정 — null vs 0.0 vs 1.0 vs 비정상 (예: 1.5) | 결정 | `predc_val == 1.0` (정확 일치) 만 On 판정. null·0.0·기타 모두 Off. 9번섹션 `isPumpRunning(TagPredcLatestDto)` 동일 패턴 — `compareTo(BigDecimal.ONE) == 0` |
| `predc_1m_h.rgstr_dtm`(수집 메타) vs `predc_dtm`(예측 대상 시각) 표출 기준 | 결정 | **`predc_dtm` 기준 표출** — 의미 충돌 없음 (ANALYZE 안건 4 Q3 참고) |
| 트랜잭션 격리 | 결정 | `@Transactional(readOnly = true)` 기본 (`READ_COMMITTED`) — `query-tuning.md §1` 정합 |

## 제외 사항

- 9번섹션 `TagPredcLatestRepository` 메서드 추가 — Repository 분리 채택
- 4·5·9번 섹션 코드 수정 — 헬퍼 추출은 별도 ANALYZE 사이클로 이연
- `actualOnPumpCnt` 응답 필드 — 사용자 Q4 결정 (펌프 가동대수는 예측만)
- `LAST_MONTH_AVG` 등 비교 옵션 — 본 사이클은 금일 하루치 전용
- DB 스키마 변경 — 없음
- frontend SPEC 전파 — `/dev:spec 운전현황분석-10번섹션` 별도 선택 단계

## 예상 산출물

- [태스크](../../../tasks/20260527/운전현황분석-10번섹션/TASK1.md)
- (Medium 작업 — RESULT·REVIEW 면제, `process/doc-harness/transitions.md` 정합)

## 부록: 도메인/DB 검토 결과

- **wtp-domain-expert**: 검토 게이트 미적용 — 신규 도메인 모델·DB 변경 0건. ANALYZE1 4영역 점검 결과 (모두 비해당, 구체 사유 명기) 가 SSOT. actual/predc NULL 의미 분리 + predc 동기 작성 가정 PLAN 반영 완료
- **wtp-dba-reviewer**: 검토 게이트 미적용 — DB 설계 변경 0건. ANALYZE1 안건 2 결론 (Querydsl + Repository 분리 + 기존 인덱스 활용) 이 SSOT. 응답 크기 추정 + 파티션 프루닝 SLA TASK 단계 검증 위임
