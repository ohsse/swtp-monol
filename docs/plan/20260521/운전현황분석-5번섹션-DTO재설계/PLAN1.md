---
status: approved
created: 2026-05-21
updated: 2026-06-01
---
# 운전현황분석-5번섹션-DTO재설계 — 구현 계획

## 목적

운전현황분석 5번섹션 (시설 단위 1분 단위 운영 현황 시계열 조회) 의 **응답 DTO 만** 옵션 T 패턴 (1440 고정 시간:분 키 + baseDate/comparisonDate 메타 + 머지 완료 단일 List) 으로 재설계해 frontend 머지 부담을 0 으로 만든다.

## 배경

운전현황분석 10번섹션 사이클의 plan 단계에서 사용자가 5번섹션 차트 구조 (3 시리즈 — 금일 실측 / 비교일 실측 / 금일 펌프대수) 와 X축 시간:분 정렬 의도를 명확화했고, 현행 옵션 R 패턴이 frontend 가 3 List 를 시간:분 키로 머지해야 하는 디자인 결함임이 드러났다. 본 사이클은 그 결함을 해소한다.

> **전제 정정 (2026-06-01)**: 당초 배경 전제였던 "후속 10번섹션이 동일 패턴 채택" 은 어긋났다 — 10번섹션이 먼저 독립 구현·커밋됨 (`c257af1`, 별도 DTO `FacilityDailyTimeSeriesDto`, `dtm` LocalDateTime 키 + 결측 omit). 5번은 금일 vs 비교일 cross-date 머지 특성상 `time` "HH:mm" 키가 불가피하여 10번과 구조가 다르며 이는 정당하다 (상세: [ANALYZE1 §전제 정정](../../../analyze/20260521/운전현황분석-5번섹션-DTO재설계/ANALYZE1.md)). 사용자 승인 하에 옵션 T 설계는 draft 그대로 유지.

상세 결정 사항은 [ANALYZE1](../../../analyze/20260521/운전현황분석-5번섹션-DTO재설계/ANALYZE1.md) 참조.

## 범위

### 포함

- `FacilityOperatingStatusTimeSeriesDto` outer 필드 재설계 (`todaySeries`/`comparisonSeries` 제거 + `series`·`baseDate`·`comparisonDate` 추가)
- inner `TimeSeriesPoint` 재설계 (`acqDtm` 제거 + `time: String "HH:mm"` 추가, `totalElpwrAmt` 제거, 시리즈 컬럼 `todayElpwrUnitQty`·`comparisonElpwrUnitQty`·`todayOnPumpCnt` 로 변경)
- `FacilityOperatingStatusTimeSeriesService.buildSeries`·`buildPoint` 재작성 + 신규 private 헬퍼 `buildOptionTSeries`·`mergeIntoSlot` 추가
- 단위 테스트 6건 재작성 (기존 5건 옵션 T 검증 변환 + 1440 행 고정 검증 1건 신규)
- Swagger description·`@ArraySchema` 명세 갱신

### 제외 (변경 없음)

- 엔드포인트 경로·HTTP·파라미터 (`GET /api/facility/{facilityId}/operating-status/timeseries?compareType=...`)
- 데이터 조회 정책 (rawdata_1m_h · `findByTagSrlNosAndDtmRange` 2회 분리 호출 · 인덱스 `idx_rawdata_1m_h_tag_time`)
- 도메인 정책 (활성 시설 검증·OPS 판정·PWI 합산·FRI 선택·전력원단위 계산)
- Service 헬퍼 9종 (`findActiveFacilityOrThrow`·`effectiveVal`·`isPumpRunning`·`pickLatest`·`sumOnPumpPwr`·`selectFacilityFri`·`computeUnitConsumption`·`loadTagsByInstrument`·`filterPumps`)
- enum `FacilityOperatingStatusCompareType` (YESTERDAY/LAST_WEEK 그대로)
- Controller (응답 클래스명 동일, 내부 구조만 변경 — 응답 시그니처 변경 없음)
- Repository (변경 없음)
- DB 스키마·인덱스·DDL (변경 없음)

### LAST_MONTH_AVG 추가 — 본 사이클 외

5번 ANALYZE1 D4 결정 ("YESTERDAY · LAST_WEEK 두 옵션만, LAST_MONTH_AVG 는 별도 데이터 집계 사이클") 유지.

## 구현 방향

### A. DTO 재설계

```java
@Getter
@Schema(description = "시설 단위 운영 현황 시계열 — 운전현황분석 5번 섹션")
public class FacilityOperatingStatusTimeSeriesDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "정수지A")
    private String facilityNm;

    @Schema(description = "기준 일자 (금일)", example = "2026-05-21")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate baseDate;

    @Schema(description = "비교 기간 선택값", implementation = FacilityOperatingStatusCompareType.class)
    private FacilityOperatingStatusCompareType compareType;

    @Schema(description = "비교 일자 (compareType 에 따른 실제 날짜 — YESTERDAY: -1d, LAST_WEEK: -7d)",
            example = "2026-05-20")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate comparisonDate;

    @ArraySchema(schema = @Schema(
            description = "시간:분 키 1440 슬롯 시계열 (00:00 ~ 23:59, 1분 간격, 결측 시점은 null 채움)",
            implementation = TimeSeriesPoint.class))
    private List<TimeSeriesPoint> series;

    private FacilityOperatingStatusTimeSeriesDto() { }

    public static FacilityOperatingStatusTimeSeriesDto of(
            String facilityId, String facilityNm, LocalDate baseDate,
            FacilityOperatingStatusCompareType compareType, LocalDate comparisonDate,
            List<TimeSeriesPoint> series) { /* ... */ }

    @Getter
    @Schema(description = "시간:분 시점 (1분 슬롯) — 3 시리즈 머지된 행")
    public static class TimeSeriesPoint {

        @Schema(description = "시간:분 (HH:mm)", example = "14:30")
        private String time;

        @Schema(description = "시리즈 1 — 금일 실측 전력원단위 (kWh/m³). "
                + "분자/분모 0·NULL·BAD·부재 시 또는 해당 시점 실측 부재 시 null", example = "0.4200")
        private BigDecimal todayElpwrUnitQty;

        @Schema(description = "시리즈 2 — 비교일 실측 전력원단위 (kWh/m³). "
                + "분자/분모 0·NULL·BAD·부재 시 또는 해당 시점 비교 데이터 부재 시 null", example = "0.4100")
        private BigDecimal comparisonElpwrUnitQty;

        @Schema(description = "시리즈 3 — 금일 실측 운영 펌프 대수. quality_cd=GOOD 이고 가동상태값 1.0인 펌프만 카운트. "
                + "UNCERTAIN/BAD OPS 태그 제외 (ot-integration.md §3 OPS 즉시 BAD 격상 정책). "
                + "해당 시점 데이터 부재 시 null", example = "2")
        private Integer todayOnPumpCnt;

        private TimeSeriesPoint() { }

        public static TimeSeriesPoint of(String time, BigDecimal todayElpwrUnitQty,
                BigDecimal comparisonElpwrUnitQty, Integer todayOnPumpCnt) { /* ... */ }
    }
}
```

### B. Service 메서드 재작성 + 분해

기존 `buildSeries`·`buildPoint` 재작성. 신규 private 헬퍼 2건 추가:

```java
public FacilityOperatingStatusTimeSeriesDto findFacilityOperatingStatusTimeSeries(
        String facilityId, FacilityOperatingStatusCompareType compareType) {
    Facility facility = findActiveFacilityOrThrow(facilityId);
    LocalDate baseDate = LocalDate.now();
    LocalDate comparisonDate = baseDate.minusDays(daysAgoOf(compareType));

    List<Instrument> instruments = instrumentRepository.findByFacilityIdAndEquipType(
            facilityId, TARGET_EQUIP_TYPES);
    if (instruments.isEmpty()) {
        return FacilityOperatingStatusTimeSeriesDto.of(
                facility.getFacilityId(), facility.getFacilityNm(),
                baseDate, compareType, comparisonDate,
                buildEmptySeries());
    }

    Map<String, List<Tag>> tagsByInstrument = loadTagsByInstrument(instruments);
    TimeRange todayRange = resolveTodayRange();
    TimeRange comparisonRange = resolveComparisonRange(comparisonDate);

    // today/comparison 각각 시점별 운영 지표 Map 으로 변환 (HH:mm → 지표)
    Map<String, TodayMetrics> todayByTime = collectTodayMetrics(
            tagsByInstrument, instruments, todayRange);
    Map<String, BigDecimal> comparisonByTime = collectComparisonElpwrUnitQty(
            tagsByInstrument, instruments, comparisonRange);

    List<TimeSeriesPoint> series = buildOptionTSeries(todayByTime, comparisonByTime);

    return FacilityOperatingStatusTimeSeriesDto.of(
            facility.getFacilityId(), facility.getFacilityNm(),
            baseDate, compareType, comparisonDate, series);
}

/** 1440 슬롯 초기화 + today/comparison 머지. 50줄 이내. */
private List<TimeSeriesPoint> buildOptionTSeries(
        Map<String, TodayMetrics> todayByTime,
        Map<String, BigDecimal> comparisonByTime) {
    List<TimeSeriesPoint> result = new ArrayList<>(1440);
    for (int min = 0; min < 1440; min++) {
        String time = formatHhmm(min);  // "00:00" ~ "23:59"
        result.add(mergeIntoSlot(time, todayByTime.get(time), comparisonByTime.get(time)));
    }
    return result;
}

/** 단일 시점 행 빌드. 10줄 이내. */
private TimeSeriesPoint mergeIntoSlot(
        String time, TodayMetrics today, BigDecimal comparisonElpwrUnitQty) {
    BigDecimal todayElpwrUnitQty = today != null ? today.elpwrUnitQty() : null;
    Integer todayOnPumpCnt = today != null ? today.onPumpCnt() : null;
    return TimeSeriesPoint.of(time, todayElpwrUnitQty, comparisonElpwrUnitQty, todayOnPumpCnt);
}

private record TodayMetrics(Integer onPumpCnt, BigDecimal elpwrUnitQty) { }
```

- `collectTodayMetrics(...)` 와 `collectComparisonElpwrUnitQty(...)` 는 기존 `buildSeries`/`buildPoint` 의 1분별 그룹화·On 펌프 계산·전력원단위 계산 로직을 시간:분 키 Map 형태로 변환한 결과를 반환. 도메인 정책 (헬퍼 9종) 모두 그대로 호출.
- `formatHhmm(min)` = `LocalTime.of(min / 60, min % 60).format("HH:mm")` — 1440 슬롯 키 생성.
- `daysAgoOf(compareType)` = YESTERDAY → 1, LAST_WEEK → 7 (기존 resolveComparisonRange 의 switch 동일).

### C. SLA·인덱스·파티션

- Repository 호출 패턴 변경 없음 (2회 분리 호출 + `idx_rawdata_1m_h_tag_time` 활용)
- 응답 크기 ~30~40% 감소 효과
- Service 머지 부담 O(n=1440) — SLA 200ms 영향 없음

### D. SPEC breaking change

- `/dev:spec` 단계 SPEC{N+1} 의 "변경 이력" 표에 다음 명시:
  - outer 필드 추가: `baseDate` · `comparisonDate` · `series`
  - outer 필드 제거: `todaySeries` · `comparisonSeries`
  - inner `TimeSeriesPoint` 필드 추가: `time` · `todayElpwrUnitQty` · `comparisonElpwrUnitQty` · `todayOnPumpCnt`
  - inner `TimeSeriesPoint` 필드 제거: `acqDtm` · `onPumpCnt` · `totalElpwrAmt` · `elpwrUnitQty`

## 성공 기준 (검증 가능 형태)

- [ ] `FacilityOperatingStatusTimeSeriesService` 단위 테스트 6건 GREEN — 정상 시계열·빈 시계열·1분 시점 결측·UNCERTAIN OPS 제외·비활성 시설 거부 5건 옵션 T 검증으로 재작성 + 1440 행 고정·미래 null 채움 1건 신규 → 검증: ./gradlew.bat :api:test PASS
- [ ] compareType=YESTERDAY · LAST_WEEK 두 옵션 모두 옵션 T 응답 구조 정상 직렬화 → 검증: 통합 테스트 또는 Swagger UI 호출, `series.length == 1440` 및 `series[0].time == "00:00"` 및 `series[1439].time == "23:59"` 확인
- [ ] Repository 호출 패턴·인덱스·파티션 활용 변경 없음 검증 → 검증: 통합 테스트 또는 EXPLAIN ANALYZE 출력에 `Subplans Removed` 또는 `Partitions Selected` 1~2 표시
- [ ] Service 머지 메서드 본문 줄 수 50줄 이내 → 검증: 코드 리뷰 단계 시각 점검 + 머지 단위 테스트 GREEN
- [ ] Swagger description·`@ArraySchema` 명세 검증 — todayOnPumpCnt OPS GOOD 전용 판정 + null 4케이스 규칙 + 시간 형식 "HH:mm" → 검증: Swagger UI 한국어 description 확인 또는 `/v3/api-docs` 응답 jq 검증
- [ ] 빌드 전체 통과 → 검증: ./gradlew.bat build BUILD SUCCESSFUL 출력 확인

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 서버 JVM 시간대 = Asia/Seoul. `LocalDate.now()` 의 베이스가 Asia/Seoul 인 환경에서 baseDate/comparisonDate 가 프론트엔드 사용자 체감 날짜와 일치. 배포 환경의 명시적 시간대 설정 권고 | 결정 | 배포 매니페스트 (`application.yml` 또는 컨테이너 환경 변수 `TZ=Asia/Seoul`) 에 명시 권고 메모만, 본 사이클은 시간대 변경 코드 없음 |
| 1440 고정 행 — `series` 는 00:00~23:59 의 1440 슬롯 전부 포함. today/comparison 둘 다 결측인 시점도 행 존재하되 3 컬럼 모두 null | 결정 | 단위 테스트로 검증 의무. `series.size() == 1440` assertion 의무 |
| comparisonDate 계산 — `LocalDate.now().minusDays(1 또는 7)` | 결정 | 본 PLAN §B Service `daysAgoOf` 헬퍼로 구현 |
| `totalElpwrAmt` 제거 후 frontend 디버깅·검증 용도 필요해질 가능성 | 미해결 | 본 사이클은 제거. 향후 frontend 요구 시 별도 사이클로 재추가 검토 |
| `time: String "HH:mm"` 정렬 — lexicographic 정렬이 시간 순서 정렬과 일치 (00:00 < 00:01 < … < 23:59) | 결정 | `buildOptionTSeries` for-loop 가 0~1439 분 순서로 슬롯 생성하므로 정렬 자명 |
| 응답 DTO 클래스명 (`FacilityOperatingStatusTimeSeriesDto`) 동일 유지 vs 옵션 T 명시 신규 명 (`...Daily*` 등) | 결정 | **동일 유지** — Controller 응답 시그니처 변경 없음, frontend SPEC 영향 최소화. 클래스명은 의미가 변하지 않음 (시설 단위 운영 현황 시계열) |

## 제외 사항

- LAST_MONTH_AVG enum 추가 (5번 ANALYZE1 D4 결정 유지)
- 헬퍼 9종 공통 컴포넌트 추출 (10번 사이클 또는 사용 사례 2건 누적 시점 별도 ANALYZE)
- 10번섹션 신규 구현 — **이미 독립 구현·커밋 완료** (`c257af1`, 2026-05-27, 별도 DTO `FacilityDailyTimeSeriesDto`). 본 사이클과 무관
- DB 컬럼·인덱스·DDL 변경

## 예상 산출물

- [태스크](../../../tasks/20260521/운전현황분석-5번섹션-DTO재설계/TASK1.md)
