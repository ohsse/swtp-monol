---
status: approved
created: 2026-06-02
updated: 2026-06-02
---
# 송수펌프 가동이력 3번섹션 — 전력량/주파수 시계열 차트 API

## 목적

송수펌프 가동이력 대시보드 **3번 섹션**(좌하단 2개 시계열 차트) 조회 **읽기 전용 API** 2개를 신설한다.

- 입력(1번섹션 파라미터): 시간단위 `[시/일/월/년]` + from~to 날짜. x축 시계열 버킷이 시/일/월/년으로 변동.
- **차트1 전력량 시계열**: 전체 활성 송수펌프(`PUMP`)의 조회기간 전력량(kWh)을 **펌프별 계열**로 표출. PWQ(적산전력량) 태그 버킷별 차분.
- **차트2 주파수 시계열**: **인버터 펌프(`INVERTER_DRIVE`)만** 대상 가변 주파수(Hz)를 **펌프별 계열**로 표출. 정격 펌프는 주파수 고정이라 제외. FQI 태그 버킷별 평균.

## 배경

- ANALYZE: [ANALYZE1](../../../analyze/20260602/송수펌프가동이력_3번섹션/ANALYZE1.md) (`status: approved`, 5인 회의 — 블로커 4건 해소).
- 집계 방식: **온더플라이 확정** (`rawdata_1m_h` 직접 `date_trunc` + `GROUP BY`). 사전집계 테이블+스케줄러는 별도 사이클 보류 (ANALYZE 안건 2 DBA 2차 검증).
- 직전 사이클: 2번섹션(`PumpOperationRateController`/`Service`/`Dto`, `com.mo.swtp.instrument`, `@Tag "11. 송수펌프 가동이력"`)이 직전 커밋으로 완료 — 본 사이클은 동일 도메인·동일 Tag 아래 **신규 컨트롤러**로 추가.
- 룰 갱신 완료(ANALYZE 전제조건): `TagMeasurementType` PWQ 9종 확장 — `standard-terms.md` `tag_se_cd` 비고 + `ot-integration.md §3` PWQ 결측 정책 행 추가 완료.

## 범위

### 포함
- `TagMeasurementType` 에 `PWQ("적산전력량","kWh")` enum 값 추가 (DDL 무영향 — `tag_se_cd VARCHAR(20)` 문자열 저장).
- 신규 `InqUnit` enum (HOUR/DAY/MONTH/YEAR + `date_trunc` 인자 매핑).
- `RawDataCustomRepository`/`Impl` 에 버킷 집계 메서드 2종 (native SQL, `§2.5` 면책).
- 검색 DTO 1종 + 응답 DTO 2종(각 outer + inner Point) + 내부 전송 record 1종.
- Service 2종 분리 + 신규 Controller 1종(엔드포인트 2개) + `InstrumentErrorCode` 1값 추가.
- 단위 테스트 2종(Mockito).

### 제외 사항
- **다운로드(엑셀) 기능** — 이미지 다운로드 버튼은 본 사이클 범위 외 (요청은 2개 차트 API 한정).
- **사전집계 테이블 + 스케줄러** — 별도 사이클 보류 (ANALYZE 안건 2).
- **DDL·마이그레이션·docs/ddl 변경** — 0건 (PWQ는 enum 값 추가, 신규 영속 컬럼 없음).
- **적산 카운터 리셋/롤오버 정밀 처리** — 단조증가 가정. 음수 차분 버킷만 생략, 물리 상한 임계 미구현(펌프별 전력량 상한 미정의).
- **4번섹션(가로막대 가동이력 gantt)** — 별도 사이클.

## 구현 방향

### 계층 흐름 (2번섹션 4-SELECT 패턴 동형)

```
Controller (@ModelAttribute SearchDto)
  → Service (기간 검증 → 활성 PUMP 조회 → 태그 IN 일괄 조회 → 버킷 집계 쿼리 → 펌프별 DTO 매핑)
    → InstrumentRepository (활성 PUMP)
    → TagRepository (PWQ/FQI 태그 IN)
    → RawDataCustomRepository (버킷 집계 native SQL)
```

### 1. `InqUnit` enum (common 모듈, `com.mo.swtp.common.enumtype`)

```java
public enum InqUnit {
    HOUR("hour"), DAY("day"), MONTH("month"), YEAR("year");
    private final String dateTruncUnit;   // PostgreSQL date_trunc 첫 인자
    // 생성자 + getter
}
```
- 위치 `common.enumtype` (범용 조회단위, backend-engineer 안건 3 권고). `YnType` 동일 패키지 선례.
- `dateTruncUnit` 은 enum 내부 고정 매핑 — 외부 문자열 주입 차단(SQL 인젝션 표면 축소, 4값으로 제약).

### 2. `TagMeasurementType.PWQ` 추가 (common 모듈, 기존 파일 수정)

```java
/** 적산전력량 (Power Watt Quantity — 1분 수집 누적 미터값, kWh). 전력량 시계열 산정용.
 *  PWI(순시전력 kW)와 단위·물리 성격 분리. 결측 정책: ot-integration.md §3 — HLV 미적용·GOOD only raw_val 차분. */
PWQ("적산전력량", "kWh");
```
- FQI 추가(2026-05-20) 선례 동형. enum name 문자열로 `tag_se_cd` 저장 → DDL 무영향.
- **PWQ 허용 검증(`validateFqiTagAllowance` 유사) 미도입** — FQI는 인버터 전용 강제 차단이 도메인 안전(고정주파수 펌프에 가변주파수 태그 금지)이나, PWQ(적산전력량)는 모든 펌프가 보유 가능하여 계측기 종류 강제 차단 불필요 (ANALYZE 안건 4 인버터 필터 통과 의견 정합). 본 사이클은 enum 값 추가만.

### 3. 집계 쿼리 (api 모듈, `RawDataCustomRepository`/`Impl` 확장)

신규 내부 전송 record `RawDataBucketDto(String tagSrlNo, LocalDateTime baseDtm, BigDecimal aggrVal)` (`com.mo.swtp.raw.dto`, `RawDataLatestDto` 선례 — Swagger 노출 외 Service 내부 전송).

신규 메서드 2종 (native SQL, `§2.5` 면책 — `query-tuning.md §2` 인용):

```java
List<RawDataBucketDto> findEnergyDeltaBuckets(
        List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit);
List<RawDataBucketDto> findAvgValueBuckets(
        List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit);
```

**전력량(차분)** — PWQ 적산값:
```sql
SELECT tag_srl_no,
       date_trunc(:unit, acq_dtm) AS base_dtm,
       MAX(raw_val) - MIN(raw_val) AS aggr_val
FROM rawdata_1m_h
WHERE tag_srl_no IN (:tagSrlNos)
  AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
  AND quality_cd = 'GOOD' AND raw_val IS NOT NULL
GROUP BY tag_srl_no, date_trunc(:unit, acq_dtm)
ORDER BY tag_srl_no, base_dtm
```
- **`corr_val` 미사용** (ANALYZE 안건 2·4 — 적산값 HLV 차분 왜곡 방지). GOOD only.
- 음수 결과(롤오버/리셋) → **애플리케이션 레벨 생략**(Service 가드).

**주파수(평균)** — FQI:
```sql
SELECT tag_srl_no,
       date_trunc(:unit, acq_dtm) AS base_dtm,
       AVG(COALESCE(corr_val, raw_val)) AS aggr_val
FROM rawdata_1m_h
WHERE tag_srl_no IN (:tagSrlNos)
  AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
  AND quality_cd = 'GOOD'
GROUP BY tag_srl_no, date_trunc(:unit, acq_dtm)
ORDER BY tag_srl_no, base_dtm
```
- FQI는 HLV 허용 측정유형 → `COALESCE(corr_val, raw_val)` (2번섹션 `effectiveVal` 정합).

**공통**: `:unit` 은 text 바인드 파라미터(`date_trunc(text, timestamp)`) — `InqUnit.dateTruncUnit` 4값으로 제약되어 인젝션 안전. `acq_dtm` 범위 조건으로 **월 RANGE 파티션 프루닝 강제** + `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 활용. 신규 인덱스 0건. 결과 매핑 `Object[]` → record (Timestamp→LocalDateTime, NUMERIC→BigDecimal).

### 4. 검색 DTO (api 모듈, `com.mo.swtp.instrument.dto`)

```java
@Getter @Schema(...)
public class PumpTimeSeriesSearchDto {
    @Schema(implementation = InqUnit.class) private InqUnit inqUnit;   // ?inqUnit=HOUR
    private LocalDate fromDt;   // ?fromDt=2024-07-01
    private LocalDate toDt;     // ?toDt=2024-07-09

    public boolean isValid();              // inqUnit·from·to non-null && !from.isAfter(to)
    public LocalDateTime toStartDtm();     // fromDt.atStartOfDay()
    public LocalDateTime toEndExclusiveDtm(); // toDt.plusDays(1).atStartOfDay()  (종료일 포함→익일 0시 미만)
}
```
- `@ModelAttribute` 수신(GET 쿼리). 검증 throw 는 Service 가 수행(DTO는 boolean·변환만 — DTO 예외 throw 회피).
- 기간 변환 정책(종료일 포함 → `[from 00:00, to+1일 00:00)`)을 SearchDto 1곳에 SSOT 보유 → 2 서비스 공유.

### 5. 응답 DTO (api 모듈, `com.mo.swtp.instrument.dto`, `BaseAuditResponseDto` 미상속)

`FacilityDailyTimeSeriesDto` outer+inner Point+정적팩토리 패턴 인용.

- `PumpPowerTimeSeriesDto` { `pumpId`, `pumpNm`, `unit`("kWh"), `points`:`List<PumpPowerTimeSeriesPoint>` }
  - `PumpPowerTimeSeriesPoint` { `baseDtm`(LocalDateTime, `@JsonFormat yyyy-MM-dd HH:mm:ss`), `elcegVal`(BigDecimal) }
- `PumpFrequencyTimeSeriesDto` { `pumpId`, `pumpNm`, `unit`("Hz"), `points`:`List<PumpFrequencyTimeSeriesPoint>` }
  - `PumpFrequencyTimeSeriesPoint` { `baseDtm`, `freqVal`(BigDecimal) }
- `points` 필드 `@ArraySchema(schema=@Schema(implementation=...Point.class))` 명시(안건 3 권고, api-patterns `List<E>` 사용자정의 element 의무).
- 정적 팩토리 `of(...)` (private 생성자). 데이터 없는 버킷은 `points` 에서 omit.

### 6. Service 2종 (api 모듈, `com.mo.swtp.instrument.service`, `@Transactional(readOnly=true)`)

공통 4단 흐름 — 차이점만 분기:

| | `PumpPowerTimeSeriesService` | `PumpFrequencyTimeSeriesService` |
|--|------------------------------|----------------------------------|
| 펌프 스코프 | 전체 활성 PUMP | 활성 PUMP 중 `INVERTER_DRIVE` (인메모리 필터) |
| 태그 유형 | PWQ | FQI |
| 집계 메서드 | `findEnergyDeltaBuckets` | `findAvgValueBuckets` |
| 버킷 가드 | aggrVal `signum() < 0` → 생략 | 없음 |
| 응답 단위 | "kWh" | "Hz" |

```java
public List<PumpPowerTimeSeriesDto> findPumpPowerTimeSeries(PumpTimeSeriesSearchDto search) {
    if (!search.isValid()) throw new RestApiException(InstrumentErrorCode.INVALID_INQ_PERIOD);
    List<Instrument> pumps = instrumentRepository
            .findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(EquipType.PUMP, YnType.Y);
    if (pumps.isEmpty()) return List.of();
    // PWQ 태그 인스트루먼트별 그룹화 → tagSrlNo IN 버킷 집계 → 펌프별 points 매핑 (private 헬퍼)
    ...
}
```
- 인버터 필터: `findByEquipTypeAndUseYnOrderBy...(PUMP, Y)` 재사용 후 `(Pump)` 캐스팅 → `getDriveType()==INVERTER_DRIVE` 인메모리 필터 (활성 펌프 수십 대 — 자식 discriminator 필드 파생쿼리 추가 회피, `§2` 단순성).
- 메서드 50줄·추상화 3단 이내 — `loadTagsByMeasurementType`·`toDto` private 헬퍼 분리(2번섹션 동형).
- 펌프당 대상 태그(PWQ/FQI) 1건 가정 — `findFirst`(2번섹션 `pickLatest` 동형). 다건 시 첫 태그.

### 7. Controller (api 모듈, `com.mo.swtp.instrument.web`, 신규)

```java
@Tag(name = "11. 송수펌프 가동이력") @RestController
@RequestMapping("/api/instrument") @RequiredArgsConstructor
public class PumpTimeSeriesController extends CommonController {
    private final PumpPowerTimeSeriesService powerService;
    private final PumpFrequencyTimeSeriesService frequencyService;

    @GetMapping("/pump-power-timeseries")     // → CommonResponseDto<List<PumpPowerTimeSeriesDto>>
    @GetMapping("/pump-frequency-timeseries") // → CommonResponseDto<List<PumpFrequencyTimeSeriesDto>>
}
```
- 신규 클래스 — 2번섹션 컨트롤러 확장 금지(안건 3 블로커, `§3` TASK 외 파일 변경 회피). `@Tag` 문자열만 재사용.
- 리터럴 세그먼트 `/pump-power-timeseries`·`/pump-frequency-timeseries` 가 `InstrumentController`의 `/{instrumentId}` path-variable 보다 PathPattern 특이도 높아 우선 매칭 (2번섹션 `/pump-operation-rate` 선례 — ambiguous-mapping 미발생).
- `@Operation`/`@ApiResponses`(200·400·401·403·404·500). `getResponseEntity(...)` 래핑.

## 도메인 모델

**신규 엔티티·테이블·필드 0건** — 읽기 전용 조회 + 온더플라이 집계. `PWQ` 는 `TagMeasurementType` enum 값 추가(영속 스키마 무영향). 따라서 **wtp-domain-expert PLAN 검토 게이트 미해당** (ANALYZE 5인 회의에서 PWQ 결측 정책·인버터 필터·4영역 이미 검토 완료).

| 사용 엔티티 (모두 기존) | 역할 | 비고 |
|---------------------|------|------|
| `Instrument`/`Pump` (`instrument_m`/`pump_m`) | 활성 PUMP·구동방식 | `equip_type_cd=PUMP` 필터 강제 |
| `Tag` (`tag_m`) | PWQ/FQI 태그 → `tag_srl_no` | `use_yn=Y` 필터 |
| `RawData` (`rawdata_1m_h`) | 버킷 집계 소스 | `acq_dtm` 파티션 키 |

## DB 설계 변경

**변경 없음** — DDL·마이그레이션·`docs/ddl`·인덱스 신규 0건. 기존 `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` + 월 RANGE 파티션 프루닝 활용. 따라서 **wtp-dba-reviewer PLAN 검토 게이트 미해당** (집계 비용·파티션 프루닝·적산 차분 쿼리는 ANALYZE 안건 2 에서 DBA 2차 검증 완료).

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| 전체 빌드 무결성 (PWQ enum·InqUnit·신규 DTO·record) | ./gradlew.bat :common:build 후 :api:build BUILD SUCCESSFUL 출력 확인 |
| 전력량 서비스 단위 테스트 GREEN | ./gradlew.bat :api:test 에서 PumpPowerTimeSeriesServiceTest 6케이스 PASS — 활성0대 빈리스트 / 2펌프 계열분리 / 음수버킷 생략 / from>to 예외 / PWQ태그없는펌프 points빈배열 / inqUnit=MONTH→repo dateTruncUnit "month" 전달 |
| 주파수 서비스 단위 테스트 GREEN | ./gradlew.bat :api:test 에서 PumpFrequencyTimeSeriesServiceTest 4케이스 PASS — 인버터만포함(정격제외) / AVG버킷 펌프별매핑 / from>to 예외 / 인버터0대 빈리스트 |
| 엔드포인트 2개 노출 | :api:bootRun 후 Swagger UI 에서 GET /api/instrument/pump-power-timeseries · /pump-frequency-timeseries + CommonResponseDto<List<Pump*TimeSeriesDto>> 스키마 + Point 배열 스키마 확인 |
| 집계 SQL 의미 정합 (커버리지 비보증 — test-strategy §5.4) | REVIEW 단계 코드 검토 — GOOD only·MAX-MIN 차분·corr_val 미사용(전력량)·AVG COALESCE(주파수)·date_trunc 버킷·acq_dtm 파티션 프루닝 WHERE 절 육안 검증 (Mockito 단위테스트는 repo 모킹으로 SQL 미검증, 선택적 수동 EXPLAIN) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| PWQ 전력량 = `MAX(raw_val)-MIN(raw_val)` 버킷 차분, GOOD only, corr_val 미사용 | 결정 | ANALYZE 안건 2·4 수렴. native SQL §3 확정 |
| 적산 단조증가 가정 — 음수 차분 버킷만 Service 생략, 물리 상한 임계 미구현(펌프별 상한 미정의) | 결정 | `coding-discipline §1` 가정 명기. 정밀 리셋 처리는 별도 사이클 |
| FQI 주파수 = `AVG(COALESCE(corr_val,raw_val))`, GOOD only | 결정 | FQI HLV 허용(2번섹션 정합). native SQL §3 확정 |
| UNCERTAIN 배제 (§3 가중치 0.5 이탈, GOOD only) | 결정 | 차트 보수적 배제. WHERE quality_cd='GOOD' |
| 펌프당 PWQ/FQI 태그 1건 가정 (다건 시 first) | 가정 | 2번섹션 `pickLatest` findFirst 동형. 다건 합산은 미구현 |
| `fromDt/toDt`=LocalDate, 기간 `[from 00:00, to+1일 00:00)` (종료일 포함) | 결정 | 이미지 `2024-07-09` 날짜형식. 시 단위는 일 범위 내 시간 버킷 |
| 13개월 보존 초과 월/년 구간 = 버킷 omit(에러 아님), UI 조회기간 제한/안내는 frontend·SPEC | 가정 | DBA 안건 2 참고. 백엔드는 공백 응답 |
| 기간 검증 실패 ErrorCode = `InstrumentErrorCode.INVALID_INQ_PERIOD(400)` (가법 추가) | 결정 | 프로젝트 Validator 컴포넌트 0건 → Service 인라인 throw (기존 스타일 `§3`) |

분류값: 가정 / 미해결 → 결정

## 예상 산출물

- [태스크](../../../tasks/20260602/송수펌프가동이력_3번섹션/TASK1.md) (TASK 단계 생성 예정)

### 신규/변경 파일

**common 모듈:**
- `common/src/main/java/com/mo/swtp/common/enumtype/InqUnit.java` (신규)
- `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` (PWQ 값 추가)

**api 모듈:**
- `api/src/main/java/com/mo/swtp/raw/dto/RawDataBucketDto.java` (신규)
- `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` (집계 메서드 2종 추가)
- `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` (native SQL 구현 2종)
- `api/src/main/java/com/mo/swtp/instrument/exception/InstrumentErrorCode.java` (INVALID_INQ_PERIOD 추가)
- `api/src/main/java/com/mo/swtp/instrument/dto/PumpTimeSeriesSearchDto.java` (신규)
- `api/src/main/java/com/mo/swtp/instrument/dto/PumpPowerTimeSeriesDto.java` (신규)
- `api/src/main/java/com/mo/swtp/instrument/dto/PumpFrequencyTimeSeriesDto.java` (신규)
- `api/src/main/java/com/mo/swtp/instrument/service/PumpPowerTimeSeriesService.java` (신규)
- `api/src/main/java/com/mo/swtp/instrument/service/PumpFrequencyTimeSeriesService.java` (신규)
- `api/src/main/java/com/mo/swtp/instrument/web/PumpTimeSeriesController.java` (신규)
- `api/src/test/java/com/mo/swtp/instrument/service/PumpPowerTimeSeriesServiceTest.java` (신규)
- `api/src/test/java/com/mo/swtp/instrument/service/PumpFrequencyTimeSeriesServiceTest.java` (신규)

## 테스트 전략

- 단위 테스트 범위: `./gradlew.bat :api:test` (Mockito 기반, 2번섹션 `PumpOperationRateServiceTest` 동형 — `Mockito.mock(Pump.class)` lenient stubbing).
- 집계 SQL 의미(GOOD only·MAX-MIN·AVG·date_trunc·파티션 프루닝)는 repo 모킹으로 단위테스트 비검증 → REVIEW 코드 검토 + 선택적 수동 EXPLAIN (`test-strategy §5.4` 쿼리 실행계획 커버리지 비보증 정합).
- 빌드 검증: `./gradlew.bat :common:build` → `./gradlew.bat :api:build`.
