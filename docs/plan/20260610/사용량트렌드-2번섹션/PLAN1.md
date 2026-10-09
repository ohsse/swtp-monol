---
status: approved
created: 2026-06-10
updated: 2026-06-10
---
# 사용량트렌드-2번섹션 — 정수장 전체 전력량 추이 조회 API

## 목적

사용량트렌드 대시보드(`backend/image/사용량트렌드.png`)의 **2번섹션**(좌상단 단일 영역 차트 "전력 사용량")
백엔드 조회 API를 신설한다. 1번섹션이 제공하는 3개 파라미터(집계 버킷 `[시|일|월]`·시작일자·종료일자)를
받아, **정수장을 구성하는 모든 계측기가 보유한 전력적산태그(PWQ)** 의 버킷 단위 전력량(kWh) 시계열을
산정해 반환한다. 조회 기간은 `시작일자 00:00` ~ `종료일자 익일 00:00`(배타적 상한)으로 종료일 당일
데이터를 모두 포함한다.

> 본 PLAN 은 [ANALYZE1](../../../analyze/20260610/사용량트렌드-2번섹션/ANALYZE1.md) (`status: approved`)
> 의 결정 사항을 구현 설계로 확정한다.

## 배경

핵심 계산(전체 PWQ 차분·버킷 집계)은 동형 선례가 이미 존재한다:
- `opt/service/PeakEnergyTrendService` — 전역 활성 PWQ 태그 수집 + 버킷별 차분 합산(전력피크분석-5번섹션)
- `raw/repository/RawDataCustomRepositoryImpl#findEnergyDeltaBuckets` — 태그별·버킷별 `MAX(raw_val)-MIN(raw_val)`
  차분 native SQL (GOOD only·`raw_val` 단독·월 RANGE 파티션 프루닝)

본 작업은 **신규 발명 없이 기존 인프라 재사용 + 파라미터화**가 골자다. 다만 5번섹션이 ±12시간(24버킷)
고정 윈도우인 것과 달리 본 섹션은 사용자 지정 기간(최대 13개월)·시/일/월 가변 집계이므로,
HOUR×396일×N태그 대용량 행 materialize 를 회피하기 위해 **DB 레벨에서 버킷별 전역 합산까지 수행하는
신규 합산 쿼리**를 추가한다(ANALYZE1 안건 2, DBA 비준).

## 범위

- **모듈**: `api` (전부). `common` 신규 엔티티·`@MappedSuperclass` 변경 없음.
- **비즈니스 도메인**: `com.mo.swtp.opt`(전역 PWQ 전력량 추이 선례와 동거) + `com.mo.swtp.raw`(rawdata 집계 SQL의 SSOT).
- **신규 DB 컬럼·엔티티·테이블 0건**. 읽기 전용 집계 API 1개 신설.
- **신규 표준 어휘 0건**(ANALYZE1 룰 갱신 지시서 0건). `elcegVal`·`baseDtm`·`unit` 등 기존 표준 용어·DTO 필드명 재사용.

## 구현 방향

### 1. 신규 합산 쿼리 (DB 레벨 — `raw` 도메인 SSOT)

`raw/repository/RawDataCustomRepository.java`(인터페이스) + `RawDataCustomRepositoryImpl.java`(구현)에
메서드 추가. 태그별 선차분(`MAX-MIN`) → 버킷 단위 전역 합산을 **DB에서** 수행하여 버킷당 1행만 반환한다.

```java
List<RawDataBucketSumDto> findEnergyDeltaBucketsTotal(
        List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit);
```

```sql
SELECT base_dtm, SUM(tag_delta) AS total_val
FROM (
    SELECT tag_srl_no,
           date_trunc(:unit, acq_dtm) AS base_dtm,
           MAX(raw_val) - MIN(raw_val) AS tag_delta
    FROM rawdata_1m_h
    WHERE tag_srl_no IN (:tagSrlNos)
      AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
      AND quality_cd = 'GOOD' AND raw_val IS NOT NULL
    GROUP BY tag_srl_no, base_dtm
) t
GROUP BY base_dtm
ORDER BY base_dtm
```

- **`tag_delta >= 0` 필터 미포함** — `MAX-MIN` 은 그룹 내 수학적으로 항상 ≥0이므로 死코드(ANALYZE1 안건 2,
  DBA 권고로 제거). `delta=0` 버킷(측정된 0 kWh)은 포함, GOOD 데이터 없는 버킷은 inner 결과 부재로 자연 sparse.
- **PWQ 적산 차분 정책**(`.claude/rules/ot-integration.md §3`): `quality_cd='GOOD'` + `raw_val` 단독
  `MAX-MIN`. `corr_val` 미사용(적산값 HLV 차분 왜곡 방지) — 기존 `findEnergyDeltaBuckets` SQL과 동일.
- **`SUM(MAX-MIN) ≠ MAX(SUM)-MIN(SUM)`** — inner 에서 태그별 선차분 후 outer 에서 버킷 합산하는 중첩 구조 의무.
- **42803 회피**: `date_trunc(:unit)` 는 inner SELECT 1회만 등장하고 inner GROUP BY 는 alias(`base_dtm`),
  outer 는 서브쿼리 컬럼 `base_dtm` 을 참조 — `:unit` named param 이 SQL 전체에 1회만 전개되어 Hibernate
  위치 파라미터 중복 전개 문제 없음(기존 `findEnergyDeltaBuckets` 선례보다 단순).
- **파티션 프루닝**: `acq_dtm >= :startDtm AND acq_dtm < :endDtm` 범위로 월 RANGE 파티션 프루닝 강제 +
  `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 인덱스 활용.
- **`:unit` 인젝션 안전**: `InqUnit.getDateTruncUnit()`(hour/day/month/year 4값 제약) text 바인드.
- **빈 태그 가드**: `tagSrlNos` null/빈 리스트 시 `List.of()` 즉시 반환(기존 메서드 동일 패턴).
- **§2.5 면책**: 메서드 상단에 `// §2.5 면책 (query-tuning.md §2)` 주석 + 중첩 집계·파티션 프루닝 단일 흐름
  보존 사유 명기(기존 `findEnergyDeltaBuckets` 주석 양식 동형). 인용 근거 누락은 REVIEW 블로커.
- **매핑**: `em.createNativeQuery` + `List<Object[]>` → `JdbcTimestamps.toLocalDateTime(r[0])`,
  `(BigDecimal) r[1]` 로 `RawDataBucketSumDto` 변환(기존 `mapToBuckets` 양식 동형, 단 컬럼 2개).

### 2. 신규 프로젝션 DTO — `raw/dto/RawDataBucketSumDto.java`

```java
public record RawDataBucketSumDto(LocalDateTime baseDtm, BigDecimal totalVal) {}
```

버킷별 전역 합산 결과(태그 차원 소거됨). 기존 `RawDataBucketDto`(태그별·`aggrVal`)와 별개 — 합산 후 단일 시계열용.

### 3. 신규 검색 DTO — `opt/dto/EnergyUsageTrendSearchDto.java`

`facility/dto/FacilityEnergyTrendSearchDto` 를 **동형 복제**(상속·공유 금지 — 사이클 간 자산 자동 원용 금지 정합).
- 필드: `inqUnit`(InqUnit)·`fromDt`(LocalDate)·`toDt`(LocalDate). `@Getter @Setter @NoArgsConstructor`(GET `@ModelAttribute` 바인딩).
- `MAX_PERIOD_DAYS = 396L`(13개월 — `rawdata_1m_h` 롤링 보존 정합).
- `isValid()`: (1) 3필드 non-null, (2) `fromDt ≤ toDt`, (3) `inqUnit != YEAR`(시/일/월만 허용), (4) `≤396일`.
- `toStartDtm()` = `fromDt.atStartOfDay()`.
- `toEndExclusiveDtm()` = `toDt.plusDays(1).atStartOfDay()` — **"종료일 익일 00시 이전" 요건 정확 충족**.
- Javadoc: 동형 복제 사유(사이클 간 자산 자동 원용 금지·SPEC 경계 분리) 명기.

### 4. 신규 응답 DTO — `opt/dto/EnergyUsageTrendDto.java`

`opt/dto/PeakEnergyTrendDto` 의 outer+inner Point+정적 팩토리 구조 미러링(동형 패턴만, 섹션 전용 신규 자산).
`BaseAuditResponseDto` 미상속(집계 뷰 — `api-patterns.md §BaseAuditResponseDto 적용 범위` 정합).

```java
@Getter @Schema(...)
public class EnergyUsageTrendDto {
    @Schema(description = "시계열 측정 단위 (전력량)", example = "kWh") private String unit;
    @ArraySchema(schema = @Schema(implementation = EnergyUsageTrendPoint.class)) private List<EnergyUsageTrendPoint> points;
    private EnergyUsageTrendDto() {}
    public static EnergyUsageTrendDto of(String unit, List<EnergyUsageTrendPoint> points) { ... }

    @Getter @Schema(...)
    public static class EnergyUsageTrendPoint {
        @JsonFormat(shape = STRING, pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime baseDtm;
        private BigDecimal elcegVal;  // 버킷 전역 합산 전력량 (kWh)
        ...
        public static EnergyUsageTrendPoint of(LocalDateTime baseDtm, BigDecimal elcegVal) { ... }
    }
}
```

- 5번섹션과 달리 `targetPeakElpwr`·`billingPeakElpwr` 스칼라 없음(사용량 추이 단일 차트). `unit = "kWh"`.
- `points` 필드에 `@ArraySchema(schema = @Schema(implementation = ...))` 의무(`api-patterns.md §@Schema(implementation)`).

### 5. 신규 서비스 — `opt/service/EnergyUsageTrendService.java`

읽기 전용(`@Transactional(readOnly = true)`). 흐름:
1. `searchDto.isValid()` false → `throw new RestApiException(OptErrorCode.INVALID_SEARCH_PERIOD)`.
2. 전역 활성 PWQ 태그 수집 — `tagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWQ, YnType.Y)` → `tagSrlNo` 매핑(`PeakEnergyTrendService#pwqTagSrlNos` 동형).
3. `rawDataRepository.findEnergyDeltaBucketsTotal(pwqSrlNos, searchDto.toStartDtm(), searchDto.toEndExclusiveDtm(), searchDto.getInqUnit().getDateTruncUnit())` 호출.
4. 결과(SQL `ORDER BY base_dtm` 로 이미 오름차순·sparse)를 `EnergyUsageTrendPoint.of(baseDtm, totalVal)` 매핑 → `EnergyUsageTrendDto.of("kWh", points)`.
5. PWQ 태그 0개 / 데이터 0건 → `points` 빈 리스트(빈 태그 가드로 SQL 미실행, 빈 List 반환).
- Javadoc: 이중계상 주의(현재 펌프 서브미터만 존재, ELCMTR 메인미터 부재라 안전 — 향후 추가 시 별도 사이클, `PeakEnergyTrendService` 기록 동형) + `SUM(MAX-MIN)≠MAX(SUM)-MIN(SUM)` 명기.
- 합산은 DB가 수행하므로 5번섹션의 `validDeltaOrNull`(Java 음수 가드) 불필요 — `MAX-MIN≥0` 보장.

### 6. 신규 컨트롤러 — `opt/web/EnergyUsageTrendController.java`

- `@Tag(name = "16. 사용량 트렌드")`(현 최대 15 다음 — 사용량트렌드 대시보드 전용, 향후 동 대시보드 섹션 누적).
- `@RestController @RequestMapping("/api/opt/energy-usage-trend") @RequiredArgsConstructor extends CommonController`.
- `@GetMapping` + `@ModelAttribute EnergyUsageTrendSearchDto searchDto` → `getResponseEntity(service.getEnergyUsageTrend(searchDto))`.
- `@Operation` + `@ApiResponses(200/400/401/500)`. `description` 에 PWQ 차분 정책·종료일 익일 00시 계약·sparse 명기.

### 7. 수정 — `opt/exception/OptErrorCode.java`

`INVALID_SEARCH_PERIOD(400)` 추가(현 `PEAK_TARGET_NOT_INITIALIZED(500)` 만 존재). `httpStatus`(int) 외 필드 금지(훅 `check-errorcode-contract.sh` 강제).

### 8. 단위 테스트 — `api/src/test/java/com/mo/swtp/opt/service/EnergyUsageTrendServiceTest.java`

Mockito(`@ExtendWith(MockitoExtension.class)`, `@Mock` `TagRepository`·`RawDataRepository`, `@InjectMocks`). 한국어 메서드명.

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| 유효 파라미터 → PWQ 태그 수집 + 합산 쿼리 호출 + 버킷별 시계열 매핑 | 단위 테스트 — given PWQ 2태그·합산쿼리 3버킷 stub, then points 3건·`elcegVal` 일치·`unit="kWh"` 검증 (`./gradlew.bat :api:test --tests *EnergyUsageTrendServiceTest`) |
| `isValid()` false(YEAR) → INVALID_SEARCH_PERIOD | 단위 테스트 — inqUnit=YEAR 입력 시 RestApiException 의 errorCode == OptErrorCode.INVALID_SEARCH_PERIOD 검증 (assertThatThrownBy + extracting) |
| `isValid()` false(기간역전·396일초과·null) → INVALID_SEARCH_PERIOD | 단위 테스트 — fromDt>toDt / 397일 / null 각 케이스 동일 예외 검증, 합산쿼리 미호출 verify(then(...).shouldHaveNoInteractions 또는 never) |
| PWQ 태그 0개 → points 빈 리스트 | 단위 테스트 — findByTagSeCdAndUseYn 빈 List stub 시 응답 points isEmpty + 합산쿼리 호출되더라도 빈 결과 매핑 검증 |
| 종료일 익일 00시 계약 | 단위 테스트 — searchDto.toEndExclusiveDtm() == toDt.plusDays(1).atStartOfDay() 단언 + 서비스가 해당 endDtm 으로 쿼리 호출함을 ArgumentCaptor 검증 |
| 전체 빌드(QClass 재생성 포함) | `./gradlew.bat clean build` BUILD SUCCESSFUL 출력 확인 |
| (선택) dev DB 수동 대조 | dev MCP read-only 로 위 SQL 직접 실행 결과와 `GET /api/opt/energy-usage-trend?inqUnit=DAY&fromDt=..&toDt=..` 응답 버킷별 kWh 일치 확인 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 전체 PWQ 합산 = 정수장 전체 전력량(이중계상 없음) | 가정 → 결정 | 현재 펌프 서브미터만 존재·ELCMTR 메인 적산미터 부재로 안전. 향후 메인미터 시딩 시 이중계상은 **별도 사이클** 재검토(`PeakEnergyTrendService` 기록 동형). 서비스 Javadoc 명기 |
| 단일 정수장 스코프(시설/사업장 필터 없음) | 가정 → 결정 | 1번섹션 파라미터 3종만 사용, facility 스코핑 없음. 전역 활성 PWQ 전체 합산. 다정수장 분리는 범위 외 |
| 버킷 내 적산 카운터 리셋 시 `MAX-MIN` 과대계상 | 가정 → 결정 | 과대계상 수용(기존 `findEnergyDeltaBuckets`·5번섹션과 동일 방식). 본 사이클 보정 미적용 |
| `@Tag` 번호 16 | 미해결 → 결정 | 현 최대 15("설비별 사용량") 다음 16 채택. impl 시 grep 으로 중복 없음 재확인 |
| `delta=0` 버킷 포함, no-GOOD 버킷 sparse 제외 | 결정 | DB SQL 의 자연 결과(inner 가 GOOD 행만 그룹핑). `tag_delta>=0` 死코드 제거 |

## 제외 사항

- 사용량트렌드 대시보드 1·3·4번섹션(별도 사이클).
- 시설/사업장/계측기 종류별 필터·드릴다운(전역 합산만).
- 예측 시계열·요금/목표 스칼라(5번섹션 전용, 본 섹션은 실측 사용량 추이만).
- 신규 표준 어휘·DB 컬럼·엔티티·마이그레이션 SQL(0건).
- frontend SPEC 전파(`/dev:spec` 선택적 후속 단계).

## 예상 산출물

- [태스크](../../../tasks/20260610/사용량트렌드-2번섹션/TASK1.md)
