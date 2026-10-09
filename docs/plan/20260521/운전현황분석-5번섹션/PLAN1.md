---
status: approved
created: 2026-05-21
updated: 2026-05-21
---
# 운전현황분석-5번섹션 — 시설 단위 1분 단위 시계열 조회 API

## 목적

운전현황 분석 화면 5번 섹션 "시계열 그래프" 카드의 backend API 를 신설한다. 활성 시설(`facility_m`, `use_yn = Y`, `facility_type ∈ {PWTF, DWT, PRSF}`) 단위로 금일 (00:00 ~ 현재시간) 1분 시계열 + 비교 시계열 (`YESTERDAY` 또는 `LAST_WEEK` 중 택1, 해당 일자 00:00 ~ 23:59:59.999999) 을 합본 응답한다. 각 1분 시점의 응답값은:

- `onPumpCnt` — OPS=GOOD AND effectiveVal=1.0 펌프 개수
- `totalElpwrAmt` — On 펌프들의 PWI GOOD 합산값 (kW)
- `elpwrUnitQty` — 전력원단위 (kWh/m³, `totalElpwrAmt / 시설 FRI corrVal`, 분자/분모 무효 시 `null`)

전력원단위 산정 정책은 운전현황분석-4번섹션 (`FacilityOperatingStatusService`, 커밋 `00d8b8a`) 과 1:1 일치하며, 본 사이클은 동일 정책을 1분 시점 시계열로 확장한다.

## 배경

- 4번 섹션 = "현재 1분 시점 1건" / 5번 섹션 = "금일·비교 시계열 N건"
- 사용자 결정 (대화 합의):
  - **1분 단위 원본 `rawdata_1m_h`** 사용 (10분 슬롯 집계 정책 폐기)
  - 본 사이클은 `YESTERDAY` · `LAST_WEEK` 두 옵션만. `LAST_MONTH_AVG` 는 향후 "데이터 집계" 별도 사이클에서 처리
  - **단일 endpoint** + **today/comparison 2회 분리 호출** (Service 메모리 그룹화) — `query-tuning.md §1` 페이지네이션·파티션 프루닝 보호
- ANALYZE1.md (2026-05-21, `status: approved`) 5인 회의 결론:
  - 신규 표준 단어·데이터 도메인·표준 용어 0건 (변수명만 사용, DB 컬럼 영향 없음)
  - 4번 헬퍼 본 사이클 재구현 — 공통 추출은 "사용 사례 2건 누적" 트리거 충족 시 별도 사이클
  - 도메인 룰 4영역 모두 비해당 (조회 전용, 알람/제어/AI 모드/이력 INSERT 0건)

## 범위

### 포함
- `FacilityOperatingStatusTimeSeriesDto` + static inner `TimeSeriesPoint` 신규 작성
- `FacilityOperatingStatusCompareType` enum 신규 작성 (`YESTERDAY`, `LAST_WEEK`)
- `FacilityOperatingStatusTimeSeriesService` 신규 작성 (4번 헬퍼 재구현)
- `RawDataCustomRepository.findByTagSrlNosAndDtmRange` Querydsl 메서드 추가
- `FacilityController.findFacilityOperatingStatusTimeSeries` 엔드포인트 메서드 추가
- 단위 테스트 `FacilityOperatingStatusTimeSeriesServiceTest` 신규 작성

### 제외
- 10분 슬롯 집계 정책 (영구 폐기)
- `LAST_MONTH_AVG` 비교 옵션 (별도 "데이터 집계" 사이클로 이연)
- 4번 섹션 코드 수정 (`coding-discipline.md §3` 정밀한 수정 — 4번 헬퍼 추출 별도 사이클)
- DB 스키마 변경 (조회 전용)
- `FacilityErrorCode` 신규 추가 (4번 섹션 정의 재사용 — `FACILITY_NOT_FOUND` · `UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS`)

## 구현 방향

### 1. API 엔드포인트

```
GET /api/facility/{facilityId}/operating-status/timeseries?compareType={YESTERDAY|LAST_WEEK}
```

- `FacilityController` 에 메서드 추가 — 4번 섹션 `findFacilityOperatingStatus` 와 동일 파일
- `compareType` query param 필수 — `FacilityOperatingStatusCompareType` enum
- 응답 타입: `ResponseEntity<CommonResponseDto<FacilityOperatingStatusTimeSeriesDto>>`
- Swagger `@Tag` 는 4번 섹션 동일 `Tag` 재사용

### 2. DTO 설계

`api/src/main/java/com/mo/swtp/facility/dto/FacilityOperatingStatusTimeSeriesDto.java`

```java
@Getter
@Schema(description = "시설 단위 운영 현황 시계열 — 운전현황분석 5번 섹션")
public class FacilityOperatingStatusTimeSeriesDto {

    @Schema(description = "시설 ID")
    private String facilityId;

    @Schema(description = "시설명")
    private String facilityNm;

    @Schema(description = "요청한 비교 옵션", implementation = FacilityOperatingStatusCompareType.class)
    private FacilityOperatingStatusCompareType compareType;

    @ArraySchema(schema = @Schema(description = "금일 시계열 (00:00 ~ 현재시간, acq_dtm 오름차순)",
            implementation = TimeSeriesPoint.class))
    private List<TimeSeriesPoint> todaySeries;

    @ArraySchema(schema = @Schema(description = "비교 시계열 (YESTERDAY/LAST_WEEK 하루치, acq_dtm 오름차순)",
            implementation = TimeSeriesPoint.class))
    private List<TimeSeriesPoint> comparisonSeries;

    private FacilityOperatingStatusTimeSeriesDto() {}

    public static FacilityOperatingStatusTimeSeriesDto of(...) { ... }

    @Getter
    @Schema(description = "1분 시점 운영 현황")
    public static class TimeSeriesPoint {

        @Schema(description = "1분 시점 수집 시각", example = "2026-05-21 10:30:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime acqDtm;

        @Schema(description = "On 펌프 개수 (OPS=GOOD AND effectiveVal=1.0). "
                + "OPS BAD/UNCERTAIN/부재 펌프는 제외 — 즉 통신 단절 시점은 0으로 카운트")
        private Integer onPumpCnt;

        @Schema(description = "On 펌프들의 PWI 합산값 (kW, GOOD 만 합산). On 펌프 0대 시 0")
        private BigDecimal totalElpwrAmt;

        @Schema(description = "전력원단위 (kWh/m³). 분자/분모 0/NULL/BAD/부재 시 null")
        private BigDecimal elpwrUnitQty;

        private TimeSeriesPoint() {}

        public static TimeSeriesPoint of(LocalDateTime acqDtm, Integer onPumpCnt,
                BigDecimal totalElpwrAmt, BigDecimal elpwrUnitQty) { ... }
    }
}
```

- `BaseAuditResponseDto` 미상속 — 4번 섹션 `FacilityOperatingStatusDto` 와 동일 분류 (단순 조회 응답, `api-patterns.md §BaseAuditResponseDto 적용 범위` 정합)
- `TimeSeriesPoint` 는 **static inner class** — 외부 재사용 사례 없음 시 `coding-discipline.md §2` "요청되지 않은 추상화 금지"
- `@JsonFormat` 초 단위 — 4번 섹션 정렬

### 3. enum 설계

`api/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityOperatingStatusCompareType.java`

```java
@Getter
@RequiredArgsConstructor
@Schema(description = "운전현황분석 5번 섹션 비교 옵션")
public enum FacilityOperatingStatusCompareType {

    @Schema(description = "전일 하루치")
    YESTERDAY,

    @Schema(description = "지난주 같은 요일 하루치")
    LAST_WEEK;
}
```

- LAST_MONTH_AVG 등 향후 옵션은 별도 사이클 (해당 사이클의 ANALYZE 단계에서 enum 확장 결정)

### 4. Service 책임 분담

`api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesService.java`

- 4-SELECT 패턴 + 시계열 2회 호출 = **6-SELECT 패턴** (Facility 1 + Instrument 1 + Tag 1 + RawData today 1 + RawData comparison 1)
- 메서드 50줄 이내 + 추상화 호출 스택 3단 이하 의무 (`coding-discipline.md §2.1`)

핵심 메서드 구조:

```
findFacilityOperatingStatusTimeSeries(facilityId, compareType)
  ├─ findActiveFacilityOrThrow(facilityId)         // 4번 재구현 (FACILITY_NOT_FOUND / UNSUPPORTED_FACILITY_TYPE 체크)
  ├─ findByFacilityIdAndEquipType(PUMP, FLWMTR)
  ├─ loadTagsByInstrument(instruments)              // 4번 재구현 — OPS/PWI/FRI 필터 + 인스트루먼트별 그룹화
  ├─ resolveTodayRange()                            // [LocalDate.now().atStartOfDay(), LocalDateTime.now()]
  ├─ resolveComparisonRange(compareType)            // [yesterday 00:00, yesterday 23:59:59.999999] or [-7일 ...]
  ├─ buildSeries(tagsByInstrument, instruments, todayRange)        // 호출 1: today 1분 시계열
  ├─ buildSeries(tagsByInstrument, instruments, comparisonRange)   // 호출 2: comparison 1분 시계열
  └─ FacilityOperatingStatusTimeSeriesDto.of(...)
```

`buildSeries(tags, instruments, range)` 내부:
```
rawList = rawDataRepository.findByTagSrlNosAndDtmRange(tagSrlNos, range.start, range.end)
groupedByDtm = rawList.stream().collect(Collectors.groupingBy(RawDataLatestDto::acqDtm))
groupedByDtm.entrySet().stream()
    .sorted(Comparator.comparing(Map.Entry::getKey))
    .map(e -> buildPoint(e.getKey(), e.getValue(), tagsByInstrument, instruments))
    .toList()
```

`buildPoint(acqDtm, rawsAtThisMinute, tagsByInstrument, instruments)` 내부:
```
latestByTag = rawsAtThisMinute.stream().collect(toMap(RawDataLatestDto::tagSrlNo, identity()))  // 시점별 lookup
pumps = filterPumps(instruments)
onPumps = pumps.stream().filter(p -> isPumpRunning(pickLatest(tags, p.id, OPS, latestByTag))).toList()
totalElpwrAmt = sumOnPumpPwr(onPumps, tagsByInstrument, latestByTag)
fri = selectFacilityFri(instruments, tagsByInstrument, latestByTag)
elpwrUnitQty = computeUnitConsumption(totalElpwrAmt, fri)
return TimeSeriesPoint.of(acqDtm, onPumps.size(), totalElpwrAmt, elpwrUnitQty)
```

#### 4-1. 4번 헬퍼 재구현 (본 사이클 추출 제외)

다음 헬퍼는 4번 섹션과 동일 시그니처·동일 정책으로 본 Service 내부에 재구현 (private 메서드):

| 헬퍼 | 정책 |
|------|------|
| `findActiveFacilityOrThrow(facilityId)` | `use_yn = Y` + `facility_type ∈ {PWTF, DWT, PRSF}` 검증 |
| `loadTagsByInstrument(instruments)` | OPS/PWI/FRI 필터 + 인스트루먼트별 그룹화 |
| `filterPumps(instruments)` | `equip_type_cd = 'PUMP'` 필터 강제 |
| `pickLatest(tags, instrumentId, type, latestByTag)` | 시점별 lookup map 적용 |
| `effectiveVal(r)` | `corrVal != null ? corrVal : rawVal` |
| `isPumpRunning(ops)` | `qualityCd == GOOD && effectiveVal == 1.0` |
| `sumOnPumpPwr(onPumps, tags, latestByTag)` | PWI GOOD 합산 |
| `selectFacilityFri(instruments, tags, latestByTag)` | 첫 GOOD FRI |
| `computeUnitConsumption(totalPwr, fri)` | 분자/분모 0·NULL·BAD·부재 → null. 정상 시 `divide(scale=4, HALF_UP)` |

추출 트리거 — "사용 사례 2건 누적" 충족. 별도 ANALYZE 사이클에서 `FacilityOperatingStatusSupport` (Spring `@Component`) 또는 부모 abstract Service 로 추출. **본 사이클은 추출 금지** (`coding-discipline.md §2` "요청되지 않은 추상화 금지").

#### 4-2. 시간 범위 결정

```java
private TimeRange resolveTodayRange() {
    LocalDateTime now = LocalDateTime.now();
    return new TimeRange(now.toLocalDate().atStartOfDay(), now);
}

private TimeRange resolveComparisonRange(FacilityOperatingStatusCompareType type) {
    long minusDays = switch (type) {
        case YESTERDAY -> 1L;
        case LAST_WEEK -> 7L;
    };
    LocalDate target = LocalDate.now().minusDays(minusDays);
    return new TimeRange(target.atStartOfDay(), target.atTime(LocalTime.MAX));
}
```

- `TimeRange` 는 Service 내부 record (`record TimeRange(LocalDateTime start, LocalDateTime end) {}`) — 외부 노출 없음
- `LocalTime.MAX` = `23:59:59.999999999` — 하루 종일 포함

#### 4-3. 데이터 부재 시점 처리

- 어떤 1분에 사용된 태그가 **전혀 없으면** → `groupedByDtm` map 에 키 자체가 없으므로 응답 리스트에서 자동 생략
- 시점에 일부 태그만 결측 → 4번 정책 적용 (`onPumpCnt` 부분 카운트, `elpwrUnitQty` 분모 BAD 시 `null`)

#### 4-4. 트랜잭션 정책

- 클래스 레벨 `@Transactional(readOnly = true)` — 조회 전용, 4번 섹션 동일 패턴

### 5. Repository 쿼리

`api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` 메서드 추가:

```java
/**
 * 태그 시리얼번호 목록의 지정 시간 범위 1분 단위 측정값을 조회한다.
 *
 * <p>Querydsl 빌더 — IN 절 + acq_dtm 반개 구간 [startDtm, endDtm). 인덱스
 * {@code idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)} 활용 + acq_dtm 범위로 파티션 프루닝 강제.</p>
 *
 * <p>운전현황분석-5번섹션 PLAN1 — 시점별 그룹화·시계열 응답용. 시계열 → 마스터 FK 금지 정책 정합
 * ({@code db/partitioning-and-retention.md §1}).</p>
 *
 * @param tagSrlNos 태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
 * @param startDtm  포함 시작 시각 (inclusive)
 * @param endDtm    제외 종료 시각 (exclusive)
 * @return acq_dtm 오름차순 측정값 List
 */
List<RawDataLatestDto> findByTagSrlNosAndDtmRange(
        List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);
```

#### 5-1. 구현 방식 — Querydsl

`RawDataCustomRepositoryImpl` 에 Querydsl `JPAQueryFactory` 주입 + `QRawData` 빌더 사용.

```java
@PersistenceContext private EntityManager em;
// 별도 JPAQueryFactory 주입은 ApiQuerydslConfig.queryFactory 사용

@Override
public List<RawDataLatestDto> findByTagSrlNosAndDtmRange(
        List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm) {
    if (tagSrlNos == null || tagSrlNos.isEmpty()) {
        return List.of();
    }
    QRawData r = QRawData.rawData;
    return queryFactory
            .select(Projections.constructor(RawDataLatestDto.class,
                    r.id.tagSrlNo, r.rawVal, r.corrVal, r.id.acqDtm, r.qualityCd))
            .from(r)
            .where(r.id.tagSrlNo.in(tagSrlNos)
                    .and(r.id.acqDtm.goe(startDtm))
                    .and(r.id.acqDtm.lt(endDtm)))
            .orderBy(r.id.acqDtm.asc(), r.id.tagSrlNo.asc())
            .fetch();
}
```

- 4번 섹션은 native `DISTINCT ON` 패턴 → 5번 섹션은 **단순 IN + 범위 + 정렬** 이므로 Querydsl 적합 (ANALYZE 2번 안건 `wtp-dba-reviewer` 결론)
- `RawData` 복합 PK 가 `@EmbeddedId RawDataId` 인 경우 `r.id.tagSrlNo`·`r.id.acqDtm` 접근 — 실제 도메인 구조 확인 후 TASK 단계에서 정렬
- `QRawData` 가 없으면 `:common` 모듈 QClass 생성 의존 — `build.gradle` Querydsl APT 정합 확인 (TASK 단계)

#### 5-2. 인덱스·파티션 프루닝

- 활용 인덱스: `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` — 기존 정의 (별도 마이그레이션 불요)
- 파티션 프루닝 — `acq_dtm` 범위 조건 직접 명시로 보장
- TASK 단계 `EXPLAIN ANALYZE` 검증 의무

### 6. 도메인 모델

| 엔티티/테이블 | 변경 | 비고 |
|------------|------|------|
| `Facility` (`facility_m`) | 변경 없음 | 활성 검증 + 시설 종류 필터 재구현만 |
| `Instrument` (`instrument_m`) | 변경 없음 | PUMP/FLWMTR 자식 조회 재사용 |
| `Tag` (`tag_m`) | 변경 없음 | OPS/PWI/FRI 필터 재사용 |
| `RawData` (`rawdata_1m_h`) | 변경 없음 | 신규 쿼리만 추가 |

### 7. DB 설계 변경

**없음** — 조회 전용 신규 API. 인덱스·파티션·컬럼 변경 0건.

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 방법 |
|------|---------|
| `FacilityOperatingStatusTimeSeriesService` 단위 테스트 5건 이상 GREEN | `./gradlew.bat :api:test --tests "FacilityOperatingStatusTimeSeriesServiceTest"` PASS — 정상 시계열 / 빈 시계열 / 결측 시점 / UNCERTAIN OPS 제외 / 비활성 시설 거부 |
| 빌드 BUILD SUCCESSFUL | `./gradlew.bat build` 출력에 `BUILD SUCCESSFUL` 포함 |
| Swagger UI 에 신규 엔드포인트 등록 | `:api:bootRun` 로컬 기동 후 `http://localhost:{port}/swagger-ui.html` 에서 `GET /api/facility/{facilityId}/operating-status/timeseries` 노출 + `compareType` enum 2 옵션 (YESTERDAY/LAST_WEEK) 표시 |
| 단일 시설 조회 응답 시간 200ms 이내 | 로컬 PostgreSQL 에 펌프 3대 + 유량계 1개 + 시계열 데이터 60분 픽스처 투입 후 Swagger 수동 호출 — `p6spy` 로그에서 두 호출 합계 200ms 이내 확인 |
| 파티션 프루닝 활성화 | `EXPLAIN ANALYZE SELECT ... FROM rawdata_1m_h WHERE tag_srl_no IN (...) AND acq_dtm >= '...' AND acq_dtm < '...'` 출력에 `Partitions: rawdata_1m_h_YYYYMM` 만 노출 + `Index Scan using idx_rawdata_1m_h_tag_time` 노드 활용 확인 |
| 응답 직렬화 검증 | 단위 테스트에서 `objectMapper.writeValueAsString(dto)` 시 `acqDtm` 이 `yyyy-MM-dd HH:mm:ss` 포맷 + `compareType` 이 enum name 문자열 + `todaySeries`·`comparisonSeries` 두 키 노출 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 다중 태그 동기 수집 가정 — 같은 1분 시점의 OPS·PWI·FRI 가 같은 `acq_dtm` 값을 갖는다 | 가정 → 결정 | **유지** — SCADA 수집 파이프라인이 1분 슬롯 단위로 동기 수집. 동기 미보장 시 `acq_dtm` 미일치 펌프의 PWI 가 시점 합산에서 누락될 수 있으나 SCADA 운영 환경상 발생 빈도 낮음. TASK 단계 단위 테스트에서 동일 `acq_dtm` 픽스처로 검증 |
| 데이터 부재 1분 시점 응답 처리 | 가정 → 결정 | **응답에 미포함** — `groupedByDtm` map 에 키 자체가 없으면 해당 분 시점 자동 생략. frontend 가 그래프 X축을 분 단위 연속으로 표시할 필요가 있으면 frontend 측에서 결측 슬롯 보간 (gap fill) 처리 |
| YESTERDAY/LAST_WEEK 시간 범위 정의 | 가정 → 결정 | **YESTERDAY** = `LocalDate.now().minusDays(1)` 의 `00:00:00` ~ `23:59:59.999999999`. **LAST_WEEK** = `LocalDate.now().minusDays(7)` 의 `00:00:00` ~ `23:59:59.999999999`. 서버 시간대 기준 (Asia/Seoul) |
| 응답 크기 SLA | 가정 → 결정 | **호출당 200ms · 응답 ~300KB**. today 최대 1440 + comparison 1440 = 2880 `TimeSeriesPoint`. 결측 시점 생략으로 평균 ~2000 포인트 예상. `EXPLAIN ANALYZE` 결과 + p6spy 로그로 TASK 단계 검증 |
| 응답 정렬 | 가정 → 결정 | **`acq_dtm` 오름차순** — Repository ORDER BY + Service Map.Entry sort 양쪽에서 보장 (frontend 그래프 시간축 정합) |
| 시점에 일부 태그만 결측 — `elpwrUnitQty` 정책 | 가정 → 결정 | **4번 섹션 정책 그대로 분단위 적용**. 분자(`totalElpwrAmt`) 0/NULL 시 `null`, 분모(FRI) BAD/UNCERTAIN/부재 시 `null`. `computeUnitConsumption` 재구현 4 케이스 동일 |
| 트랜잭션 격리 | 가정 → 결정 | `@Transactional(readOnly = true)` 기본 (`READ_COMMITTED`) — `query-tuning.md §1` 정합 |

## 제외 사항

- 4번 섹션 코드 수정 — 헬퍼 추출은 본 사이클 외 (사용 사례 2건 누적 후 별도 ANALYZE)
- `LAST_MONTH_AVG` 비교 옵션 — 별도 "데이터 집계" 사이클 (사전 집계 테이블 또는 캐시 도입 별도 ANALYZE 필요)
- 10분 슬롯 집계 — 영구 폐기 (1분 원본 표출 정책 채택)
- DB 스키마 변경 — 없음 (조회 전용)
- frontend SPEC 전파 — `/dev:spec 운전현황분석-5번섹션` 별도 선택 단계

## 예상 산출물

- [태스크](../../../tasks/20260521/운전현황분석-5번섹션/TASK1.md)
- (Medium 작업 — RESULT·REVIEW 면제, `process/doc-harness/transitions.md` 정합)

## 부록: 도메인/DB 검토 결과

- **wtp-domain-expert**: 검토 게이트 미적용 — 신규 도메인 모델·DB 변경 0건. ANALYZE 4영역 점검 결과 (모두 비해당, 구체 사유 명기) 가 SSOT
- **wtp-dba-reviewer**: 검토 게이트 미적용 — DB 설계 변경 0건. ANALYZE 2번 안건 결론 (Querydsl + 2회 분리 호출 + 기존 인덱스 활용) 이 SSOT
