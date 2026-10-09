---
status: approved
created: 2026-06-11
updated: 2026-06-11
---
# 사용량트렌드-3번섹션 — 월별 최대 순시전력 피크 조회 API

## 목적

사용량트렌드 대시보드 **3번섹션 "최대 피크 현황"** 막대그래프용 조회 API 를 신설한다.
**최근 6개월(현재 월 포함)** 동안 **전체 활성 PWI 태그(14건, 태양광 포함)** 의 **월별 최대 피크**(kW)를
6개 월 슬롯으로 반환한다. 피크 = `MAX_over_month( SUM_over_facilities(PWI per minute) )`.

## 배경

- [도메인 분석](../../../analyze/20260611/사용량트렌드-3번섹션/ANALYZE1.md) (status: approved) — 5인 회의 블로커 0건, 신규 어휘·엔티티·DDL·마이그레이션 0건
- 사용자 확정: ① 현재 월 포함 최근 6개월(서버 today 기준, 파라미터 없음), ② 빈 달 `peakVal=null` 6슬롯, ③ `com.mo.swtp.opt` 신규 클래스(기존 `EnergyUsageTrend*` 미재사용), ④ 태양광 포함 전체 14건 합산
- 동형 선례: `RawDataCustomRepositoryImpl.findMaxMinuteSumElpwr`(PWI 분별 SUM 의 전구간 단일 MAX) → 본 작업은 **월 버킷 MAX + 6개월 윈도우** 확장

## 범위

**포함** (전부 `api` 모듈):
- raw 계층: projection record `RawDataBucketPeakDto` + `RawDataCustomRepository(+Impl)` 에 월별 피크 native 쿼리 메서드
- opt 계층: `MaxPeakStatusDto`·`MaxPeakStatusService`·`MaxPeakStatusController`
- api config: `Clock` 빈 (결정적 테스트)
- 단위 테스트: `MaxPeakStatusServiceTest`

**제외**: DB 스키마 변경·마이그레이션, common 모듈·엔티티 변경, scheduler, frontend, 신규 ErrorCode, 태양광 발전/소비 구분(별도 사이클), `EXPLAIN` 실측(권고·성공 기준 외).

## 구현 방향

### 1. raw 계층 — projection record + native 쿼리

**`api/src/main/java/com/mo/swtp/raw/dto/RawDataBucketPeakDto.java`** (신규 record)
```java
/** 사용량트렌드-3번섹션 전용 — 월별 최대 순시전력 피크 프로젝션 (baseDtm=월 시작, peakVal=분별 SUM 의 월별 MAX). */
public record RawDataBucketPeakDto(LocalDateTime baseDtm, BigDecimal peakVal) {}
```

**`RawDataCustomRepository.java`** 인터페이스에 시그니처 추가:
```java
List<RawDataBucketPeakDto> findMonthlyMaxMinuteSumElpwr(
        List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);
```

**`RawDataCustomRepositoryImpl.java`** 구현 — `// §2.5 면책 (query-tuning.md §2)` 주석 의무, 빈 태그 `List.of()` early-return:
```sql
SELECT date_trunc('month', acq_dtm) AS base_dtm, MAX(minute_sum) AS peak_val
FROM (
    SELECT acq_dtm, SUM(COALESCE(corr_val, raw_val)) AS minute_sum
    FROM rawdata_1m_h
    WHERE tag_srl_no IN (:tagSrlNos)
      AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
      AND quality_cd = 'GOOD'
    GROUP BY acq_dtm
) m
GROUP BY date_trunc('month', acq_dtm)
ORDER BY base_dtm
```
- 분별 SUM(inner GROUP BY `acq_dtm`) → 월별 MAX(outer). `SUM(MAX) != MAX(SUM)` — 중첩 집계 필수
- PWI 품질 정책: GOOD only + `COALESCE(corr_val, raw_val)` (HLV 허용, `ot-integration.md §3`)
- outer GROUP BY 는 `date_trunc('month', acq_dtm)` **전체 표현식**(DBA 권고). `'month'` 리터럴이라 Hibernate 42803 무관
- `acq_dtm` 범위로 월 RANGE 파티션 프루닝 강제. 결과는 sparse → 6슬롯 채움은 Service 책임
- 결과 매핑은 기존 `mapToBucketSums` 동형 private 매퍼 1건 추가 (`JdbcTimestamps.toLocalDateTime` 사용)

### 2. opt 계층 — 응답 DTO

**`api/src/main/java/com/mo/swtp/opt/dto/MaxPeakStatusDto.java`** (신규)
```java
@Getter
@Schema(description = "사용량트렌드 3번섹션 최대 피크 현황 응답")
public class MaxPeakStatusDto {
    @Schema(description = "측정 단위", example = "kW")
    private String unit;

    @ArraySchema(schema = @Schema(implementation = MaxPeakStatusPoint.class))
    private List<MaxPeakStatusPoint> points;

    public static MaxPeakStatusDto of(String unit, List<MaxPeakStatusPoint> points) { ... }

    @Getter
    @Schema(description = "월별 최대 피크 포인트")
    public static class MaxPeakStatusPoint {
        @Schema(description = "월 시작 일시", example = "2026-06-01 00:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime baseDtm;

        @Schema(description = "월별 최대 순시전력 피크 (kW), 데이터 없는 달 null", example = "1047.4000")
        private BigDecimal peakVal;   // nullable — 측정 부재 달

        public static MaxPeakStatusPoint of(LocalDateTime baseDtm, BigDecimal peakVal) { ... }
    }
}
```
- `record` 대신 `@Getter` class 사용 가능하나 2번섹션 `EnergyUsageTrendDto` 스타일과 정합되게 작성(정적 팩토리 `of`). 중첩 `point` 는 `EnergyUsageTrendPoint` 선례 동형 — 비즈니스 도메인 약어 `point`(관로 분기점)와 **층위 별개**
- `BaseAuditResponseDto` 미상속(집계 뷰)

### 3. opt 계층 — Service

**`api/src/main/java/com/mo/swtp/opt/service/MaxPeakStatusService.java`** (신규)
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaxPeakStatusService {
    private static final String UNIT_KW = "kW";
    private static final int MONTH_SLOTS = 6;

    private final Clock clock;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    public MaxPeakStatusDto getMaxPeakStatus() {
        List<LocalDateTime> monthKeys = buildMonthKeys();   // 당월-5 .. 당월 (오름차순 6건)
        List<String> pwiTags = pwiTagSrlNos();
        Map<LocalDateTime, BigDecimal> peakByMonth = pwiTags.isEmpty()
                ? Map.of()
                : toMap(rawDataRepository.findMonthlyMaxMinuteSumElpwr(
                        pwiTags, monthKeys.get(0), endExclusive(monthKeys)));
        return MaxPeakStatusDto.of(UNIT_KW, mergeToSlots(monthKeys, peakByMonth));
    }

    private List<LocalDateTime> buildMonthKeys() { /* YearMonth.from(LocalDate.now(clock)) 기준 6건 */ }
    private List<String> pwiTagSrlNos() {
        return tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y).stream()
                .map(Tag::getTagSrlNo).toList();
    }
    // mergeToSlots: 6개 월 키 순회 → MaxPeakStatusPoint(key, map.get(key) /*null 허용*/)
}
```
- 윈도우: `YearMonth cur = YearMonth.from(LocalDate.now(clock))`; `start = cur.minusMonths(5).atDay(1).atStartOfDay()`; `endExclusive = cur.plusMonths(1).atDay(1).atStartOfDay()`
- `buildMonthKeys`·`mergeToSlots` private 분해로 50줄·가독성 유지
- 파라미터 없음 → 검증·`OptErrorCode` 변경 0건

### 4. opt 계층 — Controller

**`api/src/main/java/com/mo/swtp/opt/web/MaxPeakStatusController.java`** (신규)
- `extends CommonController`, `@Tag(name = "16. 사용량 트렌드")`, `@RequestMapping("/api/opt/max-peak-status")`, `@GetMapping`, 파라미터 없음
- `ResponseEntity<CommonResponseDto<MaxPeakStatusDto>>` → `getResponseEntity(service.getMaxPeakStatus())`
- `@Operation` + `@ApiResponses` 200/401/500 (400 없음 — 입력 파라미터 없음)

### 5. api config — Clock 빈

**`api/src/main/java/com/mo/swtp/api/config/ClockConfig.java`** (신규)
```java
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock() { return Clock.systemDefaultZone(); }
}
```
- 단위 테스트는 `MaxPeakStatusService(Clock.fixed(...), tagRepo, rawRepo)` 생성자 직접 주입으로 today 고정

## 도메인 모델

**신규 엔티티·테이블·DB 컬럼 없음.** 신규 자산은 모두 application-layer Java 클래스 + Config 빈.

| 클래스/자산 | 계층 | 역할 |
|-----------|------|------|
| `RawDataBucketPeakDto` (record) | raw/dto | 월별 피크 native 쿼리 projection (`baseDtm`, `peakVal`) |
| `MaxPeakStatusDto` (+중첩 `MaxPeakStatusPoint`) | opt/dto | 응답 DTO (`unit:"kW"`, `points`) |
| `MaxPeakStatusService` | opt/service | 6개월 윈도우 계산 + PWI 태그 수집 + 쿼리 + 6슬롯 null 병합 |
| `MaxPeakStatusController` | opt/web | `GET /api/opt/max-peak-status` |
| `ClockConfig` | api/config | `Clock` 빈 (결정적 테스트) |

## DB 설계 변경

**없음.** 기존 `rawdata_1m_h`(월 RANGE 파티션, `idx_rawdata_1m_h_tag_time` 인덱스)·`tag_m` 읽기 전용 집계만 수행. 신규 인덱스·DDL·마이그레이션 0건.

## 성공 기준 (검증 가능 형태)

| 성공 기준 | 검증 |
|---------|------|
| 6개 월 모두 데이터 → 6개 points, 각 peakVal 존재, baseDtm 오름차순 | MaxPeakStatusServiceTest 단위 테스트 GREEN — 고정 Clock + repo mock, 6슬롯·정렬 단언 |
| 일부 월 결측 → 해당 슬롯 peakVal=null, 슬롯 수 항상 6 | 단위 테스트 — sparse 버킷 입력 시 결측 월 null·size==6 단언 |
| PWI 태그 0건 → 6슬롯 전부 null, 쿼리 미호출 | 단위 테스트 — tagRepo 빈 리스트 mock, rawRepo verify(never), 6슬롯 null |
| 쿼리 결과 빈 리스트 → 6슬롯 전부 null | 단위 테스트 — rawRepo 빈 리스트 mock |
| `findByTagSeCdAndUseYn(PWI, Y)` 필터 사용 | 단위 테스트 verify 단언 |
| 빌드·컴파일 무결성 | ./gradlew.bat :api:test BUILD SUCCESSFUL |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 전체 활성 PWI 14건 합산(태양광 SOLAR 발전 포함) — 메인 총괄 미터 부재로 이중계상 없음 | 결정 | 사용자 확정(2026-06-11) — `findByTagSeCdAndUseYn(PWI, Y)` 그대로, facility 필터 불요. 발전/소비 구분 필요 시 별도 사이클 |
| 당월(2026-06) 진행 중 부분 데이터 = "현재까지 이번 달 피크" | 결정 | 사용자 "현재 월 포함" 확정 |
| 빈 달 `peakVal=null` (측정 부재 ≠ 0kW) | 결정 | 사용자 "빈 달 null" 확정. dev 는 2026-05~ 파티션만 존재 → 2026-01~04 null 예상 |
| 분별 SUM 중 일부 PWI 태그 BAD/결측 시 해당 분 과소 합계 | 가정 | 트렌드 목적 수용 (`ot-integration.md §3` GOOD 필터 범위 내) |
| `Clock` 주입으로 시각 의존 제거 | 결정 | `ClockConfig @Bean` + 테스트 `Clock.fixed` |

## 제외 사항

- DB 스키마·마이그레이션·인덱스 변경 (읽기 전용 집계)
- frontend SPEC 전파 (`/dev:spec` 별도 선택 단계)
- 태양광 발전/소비 구분 로직 (별도 사이클)
- 입력 파라미터화·기간 검증 (서버 고정 6개월 윈도우)
- `EXPLAIN (ANALYZE, BUFFERS)` 실데이터 점검 (DBA 참고·권고, 성공 기준 외)

## 예상 산출물
- [태스크](../../../tasks/20260611/사용량트렌드-3번섹션/TASK1.md)
