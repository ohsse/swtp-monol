---
status: approved
created: 2026-06-02
updated: 2026-06-02
---
# 송수펌프 가동이력 4번섹션 — 펌프 가동상태 타임라인(막대) 차트 API

## 목적

송수펌프 가동이력 대시보드 **4번 섹션**(우하단 펌프별 가로 타임라인 막대) 조회 **읽기 전용 API** 1개를 신설한다.

- 입력(1번섹션 파라미터): **from~to 날짜만** 사용 (3번섹션과 달리 `inqUnit` 미사용).
- 처리: 조회기간 동안 각 펌프의 **가동상태(OPS) 1분 시계열** 중 가동(ON) 행만 조회 → 펌프별 **연속 ON 구간을 세그먼트(`startDtm`~`endDtm`)로 런렝스 압축**.
- 표출: 프론트(**Chart.js floating bar**, `indexAxis: 'y'` + `x축 type: 'time'`)가 펌프별 1행(track)에 가동 구간 막대를 표출. 같은 펌프가 조회기간 중 여러 번 껐다 켜지면 한 행에 다수 막대.

## 배경

- ANALYZE: [ANALYZE1](../../../analyze/20260602/송수펌프가동이력_4번섹션/ANALYZE1.md) (`status: approved`, 5인 회의 — 블로커 2건 해소: domain bridge 금지 / dba 방안B).
- 응답 형식: **세그먼트(가동 구간) 압축 확정** (사용자 결정). 1분 원시 포인트 전체 전달은 배제 (한 달 조회 시 펌프당 ~4.3만 행 → 응답 비대). 백엔드는 차트 라이브러리 비종속 세그먼트 응답, 프론트가 `{ x: [startDtm, endDtm], y: pumpNm }` floating bar 포인트로 변환.
- 두 블로커의 단일 알고리즘 결합: **방안B**(쿼리 `GOOD + raw_val=1` 필터 → ON 행만 반환) + **무조건 split**(결측·BAD·OFF 구간 bridge 금지)이 수렴한다 — OFF 행을 조회하지 않으므로 연속 ON 행의 `acq_dtm` gap이 곧 OFF/BAD/결측 구간이 되어, gap 발생 시 세그먼트가 자연 분리된다.
- 직전 사이클: 2번섹션(`PumpOperationRateController`/`Service`/`Dto`) · 3번섹션(`PumpTimeSeriesController`/`PumpPowerTimeSeriesService`/`PumpFrequencyTimeSeriesService`)이 직전 커밋으로 완료 — 본 사이클은 동일 도메인(`com.mo.swtp.instrument`)·동일 `@Tag("11. 송수펌프 가동이력")` 아래 **신규 컨트롤러**로 추가.
- 룰 갱신 완료(ANALYZE 전제조건): `swtp/.claude/rules/dict/standard-words.md` 에 표준 단어 `segment`(구간) 등록 완료.

## 범위

### 포함
- from~to **공통 부모 검색 DTO** 신규(`PumpPeriodSearchDto`) — `fromDt`·`toDt` + `isValid()`·`toStartDtm()`·`toEndExclusiveDtm()` SSOT.
- 기존 3번섹션 `PumpTimeSeriesSearchDto` 를 부모 상속으로 **정렬**(중복 필드·변환 메서드 제거, `inqUnit` + override `isValid()` 만 유지).
- `RawDataCustomRepository`/`Impl` 에 ON 상태 시계열 조회 메서드 1종 추가(Querydsl, `§2.5` 면책).
- 경량 조회 전송 record 1종(`RawDataOnStateDto`) + 응답 DTO 1종(outer `PumpOperationHistoryDto` + inner `OperationSegment`).
- Service 1종 + 신규 Controller 1종(엔드포인트 1개). 런렝스 인코딩은 Service `private` 헬퍼.
- 단위 테스트 1종(Mockito).

### 제외 사항
- **조회 단위(`inqUnit`)·버킷 집계** — 4번섹션은 원시 ON 세그먼트만 사용 (3번섹션 집계와 무관).
- **빈 4번섹션 자식 SearchDto** — 4번섹션 추가 파라미터 0건이므로 빈 자식 클래스를 만들지 않고 부모 `PumpPeriodSearchDto` 를 직접 `@ModelAttribute` 수신 (ANALYZE 빈 자식 권고 대비 `§2` 단순성 재평가 — 가정 섹션 명기).
- **OPS 외 상태(OFF) 세그먼트 반환** — ON 구간만 (OFF·BAD·UNCERTAIN·결측은 화면상 빈 공간).
- **조회기간 백엔드 상한 강제** — dba 31일 상한은 권고였으나 본 사이클 미적용 (단순성 + 사용자 미요청). frontend UX(기간 선택 제한)로 위임. 운영 데이터 SLA 초과 누적 시 별도 사이클 도입 (가정 섹션 명기).
- **DDL·마이그레이션·docs/ddl 변경** — 0건 (신규 영속 컬럼·인덱스 없음).
- **신규 ErrorCode** — 기존 `InstrumentErrorCode.INVALID_INQ_PERIOD(400)` 재사용 (기간 결측·from>to). 신규 0건.

## 구현 방향

### 계층 흐름 (2·3번섹션 SELECT 조합 패턴 동형)

```
Controller (@ModelAttribute PumpPeriodSearchDto)
  → Service (기간 검증 → 활성 PUMP 조회 → OPS 태그 IN 일괄 조회 → ON 시계열 조회(방안B) → 펌프별 런렝스 인코딩 → DTO 매핑)
    → InstrumentRepository (활성 PUMP)
    → TagRepository (OPS 태그 IN)
    → RawDataCustomRepository (ON 상태 시계열 — GOOD + raw_val=1 Querydsl)
```

### 1. 공통 부모 검색 DTO `PumpPeriodSearchDto` (api 모듈, `com.mo.swtp.instrument.dto`, 신규)

```java
@Getter @Setter @NoArgsConstructor
@Schema(description = "송수펌프 가동이력 from~to 기간 공통 검색 조건")
public class PumpPeriodSearchDto {
    @Schema(description = "조회 시작일 (포함)", example = "2024-07-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fromDt;

    @Schema(description = "조회 종료일 (포함 — 당일 23:59:59 까지)", example = "2024-07-09")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate toDt;

    public boolean isValid() { return fromDt != null && toDt != null && !fromDt.isAfter(toDt); }
    public LocalDateTime toStartDtm() { return fromDt.atStartOfDay(); }
    public LocalDateTime toEndExclusiveDtm() { return toDt.plusDays(1).atStartOfDay(); }
}
```

- 기간 변환 정책(종료일 포함 → `[fromDt 00:00, toDt+1일 00:00)`)을 부모 1곳에 SSOT 보유 → 3·4번섹션 공유.
- `@Getter @Setter` 는 부모에 선언 — Lombok 은 선언 클래스 필드만 생성 (backend-engineer 안건 1 Q3). `@ModelAttribute` GET 쿼리 바인딩.
- 검증 throw 는 Service 가 수행 — 본 DTO 는 `isValid()` boolean 판정 + 일시 변환만 제공 (DTO 예외 throw 회피, 기존 패턴 정합).
- **상속 깊이**: `PumpPeriodSearchDto → PumpTimeSeriesSearchDto` **2단** (`coding-discipline §2.1` 3단 미달, 통과).

### 2. 3번섹션 `PumpTimeSeriesSearchDto` 정렬 (api 모듈, 기존 파일 변경)

```java
@Getter @Setter @NoArgsConstructor
@Schema(description = "송수펌프 가동이력 3번섹션 시계열 조회 검색 조건")
public class PumpTimeSeriesSearchDto extends PumpPeriodSearchDto {
    @Schema(description = "조회 단위 (시/일/월/년)", implementation = InqUnit.class)
    private InqUnit inqUnit;

    @Override public boolean isValid() { return inqUnit != null && super.isValid(); }
    // fromDt·toDt·toStartDtm()·toEndExclusiveDtm() 제거 — 부모 상속
}
```

- 변경: `fromDt`·`toDt` 필드 + `toStartDtm()`·`toEndExclusiveDtm()` 메서드 제거(부모 상속). `inqUnit` 필드 + `@Override isValid()`(= `inqUnit != null && super.isValid()`) 만 유지. `@Getter @Setter` 자체 선언(`inqUnit` 용).
- **계획 외 변경 (의도된 필수 부수 변경)** — 사용자 명시 결정("3·4번섹션 모두 부모 상속 정렬"). `coding-discipline §3` 정밀 수정 절·RESULT "계획 외 변경" 에 명기 의무.
- **3번섹션 회귀 무영향**: `PumpPowerTimeSeriesService`·`PumpFrequencyTimeSeriesService` 가 호출하는 시그니처(`isValid()`·`toStartDtm()`·`toEndExclusiveDtm()`·`getInqUnit()`·`getFromDt()`·`getToDt()`)는 상속으로 모두 유지 → 두 Service 무변경. 기존 `PumpPowerTimeSeriesServiceTest`·`PumpFrequencyTimeSeriesServiceTest` GREEN 유지.

### 3. ON 상태 시계열 조회 (api 모듈, `RawDataCustomRepository`/`Impl` 확장 — 방안B)

신규 경량 전송 record `RawDataOnStateDto(String tagSrlNo, LocalDateTime acqDtm)` (`com.mo.swtp.raw.dto`, `RawDataBucketDto` 선례 동형 — Swagger 노출 외 Service 내부 전송). ON 행은 쿼리에서 `GOOD + raw_val=1` 로 이미 필터되어 `rawVal`·`corrVal`·`qualityCd` 가 고정값이므로 런렝스 인코딩에 필요한 2컬럼(`tagSrlNo`·`acqDtm`)만 투영 (방안B 메모리·전송량 감소 취지 정합).

신규 메서드 1종 (Querydsl, `§2.5` 면책 — `query-tuning.md §2` 인용):

```java
List<RawDataOnStateDto> findOnStateByTagSrlNosAndDtmRange(
        List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);
```

```java
// §2.5 면책 (query-tuning.md §2) — ON 상태 시계열 (GOOD + raw_val=1). acq_dtm 범위로 월 RANGE 파티션 프루닝 강제.
QRawData rawData = QRawData.rawData;
return queryFactory
        .select(Projections.constructor(RawDataOnStateDto.class, rawData.tagSrlNo, rawData.acqDtm))
        .from(rawData)
        .where(rawData.tagSrlNo.in(tagSrlNos)
                .and(rawData.acqDtm.goe(startDtm))
                .and(rawData.acqDtm.lt(endDtm))
                .and(rawData.qualityCd.eq(QualityCode.GOOD))
                .and(rawData.rawVal.eq(BigDecimal.ONE)))
        .orderBy(rawData.tagSrlNo.asc(), rawData.acqDtm.asc())
        .fetch();
```

- **방안B**(dba 안건 4 블로커) — 쿼리 단 `quality_cd='GOOD' AND raw_val=1` 필터로 ON 행만 반환. 방안A(전체 로딩 + Service 필터)는 OFF·BAD 행 전량 힙 상주(가동률 30~50% 시 절반 폐기) → 배제.
- **`corr_val` 미참조** — OPS 는 Hold Last Value 미적용 (`ot-integration.md §3`). `raw_val` 단독 판정 (2번섹션 `computeOprtngRate` 정합).
- `raw_val = 1` 비교: NUMERIC(15,4) 1.0000 = 1 동등 (PostgreSQL 스케일 무관). `BigDecimal.ONE` 바인드.
- 정렬 `ORDER BY tag_srl_no ASC, acq_dtm ASC` (dba 안건 4 권고) — 런렝스는 태그별 시간순 순회. 인덱스 `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 역방향 스캔 활용. `acq_dtm` 범위 조건으로 월 RANGE 파티션 프루닝 강제. 신규 인덱스 0건. `EXPLAIN ANALYZE` Sort 노드 회피 확인은 impl 단계 (가정 섹션 미해결).

### 4. 응답 DTO (api 모듈, `com.mo.swtp.instrument.dto`, `BaseAuditResponseDto` 미상속)

`PumpPowerTimeSeriesDto` outer + inner + 정적팩토리 패턴 인용.

```java
@Getter
@Schema(description = "펌프별 가동상태 타임라인 응답 DTO — 송수펌프 가동이력 4번섹션")
public class PumpOperationHistoryDto {
    @Schema(description = "펌프 ID (instrument_id)", example = "I-PUMP-001") private String pumpId;
    @Schema(description = "펌프명", example = "송수1호기") private String pumpNm;
    @ArraySchema(schema = @Schema(description = "가동(ON) 구간 세그먼트 목록 (OFF·결측·BAD 구간은 분리되어 미포함)",
            implementation = OperationSegment.class))
    private List<OperationSegment> segments;

    private PumpOperationHistoryDto() {}
    public static PumpOperationHistoryDto of(String pumpId, String pumpNm, List<OperationSegment> segments) { ... }

    @Getter
    @Schema(description = "가동(ON) 단일 구간 — Chart.js floating bar [startDtm, endDtm] 매핑")
    public static class OperationSegment {
        @Schema(description = "가동 시작 일시 (구간 첫 ON 수집시각)", example = "2024-07-01 08:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime startDtm;

        @Schema(description = "가동 종료 일시 (구간 마지막 ON 수집시각 + 1분)", example = "2024-07-01 12:30:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime endDtm;

        private OperationSegment() {}
        public static OperationSegment of(LocalDateTime startDtm, LocalDateTime endDtm) { ... }
    }
}
```

- 시간 직렬화 SSOT `"yyyy-MM-dd HH:mm:ss"` (3번섹션 `baseDtm` 정합, Chart.js date adapter parser 처리).
- OPS 태그 부재·ON 데이터 없는 펌프는 `segments` 빈 배열 (펌프 행 자체는 유지 — 막대 없는 빈 track).

### 5. Service `PumpOperationHistoryService` (api 모듈, `com.mo.swtp.instrument.service`, `@Transactional(readOnly=true)`)

```java
public List<PumpOperationHistoryDto> findPumpOperationHistory(PumpPeriodSearchDto search) {
    if (!search.isValid()) throw new RestApiException(InstrumentErrorCode.INVALID_INQ_PERIOD);
    List<Instrument> pumps = instrumentRepository
            .findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(EquipType.PUMP, YnType.Y);
    if (pumps.isEmpty()) return List.of();

    Map<String, String> tagByPump = loadOpsTagByInstrument(pumps);          // 펌프 → OPS tagSrlNo (1건, first)
    Map<String, List<LocalDateTime>> onAcqByTag = loadOnAcqByTag(tagByPump, search); // tagSrlNo → ON acqDtm 오름차순

    return pumps.stream().map(i -> (Pump) i)   // equip_type_cd='PUMP' 파생쿼리 보장 — 안전 캐스팅
            .map(p -> toDto(p, tagByPump, onAcqByTag)).toList();
}
```

- `loadOpsTagByInstrument`: `tagRepository.findByInstrumentInstrumentIdInAndUseYn(ids, Y)` → `TagMeasurementType.OPS` 필터 → `toMap(instrumentId, tagSrlNo, (a,b)->a)` (펌프당 OPS 1건 가정, 2번섹션 동형).
- `loadOnAcqByTag`: `rawDataRepository.findOnStateByTagSrlNosAndDtmRange(tagSrlNos, search.toStartDtm(), search.toEndExclusiveDtm())` → `groupingBy(tagSrlNo, mapping(acqDtm, toList))`. 쿼리가 `(tag_srl_no, acq_dtm ASC)` 정렬 보장 → 그룹 내 시간순 유지.
- `toDto`: tagSrlNo 로 ON acqDtm 목록 조회 → `encodeSegments(...)` → `PumpOperationHistoryDto.of(...)`. OPS 태그 없으면 `segments` 빈 배열.
- **런렝스 인코딩 헬퍼** `encodeSegments(List<LocalDateTime> onAcqDtms)` (`private`):
  - 빈 목록 → 빈 세그먼트.
  - 첫 ON → `segStart`. 다음 ON `cur` 가 `prev + 1분`(수집주기) 이면 연속, 아니면 split (직전 세그먼트 `endDtm = prev + 1분`, 새 `segStart = cur`). 마지막 → `endDtm = prev + 1분`.
  - 연속성 판정 `cur.equals(prev.plusMinutes(1))`. gap 발생 = OFF/BAD/결측(ON 행 부재) → split (bridge 금지, domain 안건 3 블로커 정합).
- 메서드 50줄·추상화 3단 이내 (`coding-discipline §2.1`). `COLLECTION_INTERVAL_MINUTES = 1` 상수.

### 6. Controller `PumpOperationHistoryController` (api 모듈, `com.mo.swtp.instrument.web`, 신규)

```java
@Tag(name = "11. 송수펌프 가동이력") @RestController
@RequestMapping("/api/instrument") @RequiredArgsConstructor
public class PumpOperationHistoryController extends CommonController {
    private final PumpOperationHistoryService service;

    @GetMapping("/pump-operation-history")   // → CommonResponseDto<List<PumpOperationHistoryDto>>
    public ResponseEntity<CommonResponseDto<List<PumpOperationHistoryDto>>> findPumpOperationHistory(
            @ModelAttribute PumpPeriodSearchDto search) {
        return getResponseEntity(service.findPumpOperationHistory(search));
    }
}
```

- 신규 클래스 — 2·3번섹션 컨트롤러 확장 금지(`§3` TASK 외 파일 변경 회피). `@Tag` 문자열만 재사용.
- 리터럴 세그먼트 `/pump-operation-history` 가 `InstrumentController` 의 `/{instrumentId}` path-variable 보다 PathPattern 특이도 높아 우선 매칭 (2·3번섹션 선례 — ambiguous-mapping 미발생).
- `@Operation`/`@ApiResponses`(200·400·401·403·404·500). `getResponseEntity(...)` 래핑.
- **4번섹션 자식 SearchDto 미생성** — 부모 `PumpPeriodSearchDto` 직접 수신 (추가 파라미터 0건, `§2` 단순성).

## 도메인 모델

**신규 엔티티·테이블·필드 0건** — 읽기 전용 조회 + 인메모리 런렝스 인코딩. 따라서 **wtp-domain-expert PLAN 검토 게이트 미해당** (ANALYZE 5인 회의에서 OPS ON 판정·HLV 미적용·bridge 금지·4영역 검토 완료).

| 사용 엔티티 (모두 기존) | 역할 | 비고 |
|---------------------|------|------|
| `Instrument`/`Pump` (`instrument_m`/`pump_m`) | 활성 PUMP 목록 | `equip_type_cd=PUMP` 필터 강제 |
| `Tag` (`tag_m`) | OPS 태그 → `tag_srl_no` | `use_yn=Y` + `TagMeasurementType.OPS` 필터 |
| `RawData` (`rawdata_1m_h`) | ON 상태 시계열 소스 | `acq_dtm` 파티션 키, `quality_cd='GOOD' AND raw_val=1` |

## DB 설계 변경

**변경 없음** — DDL·마이그레이션·`docs/ddl`·인덱스 신규 0건. 기존 `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` + 월 RANGE 파티션 프루닝 활용. 따라서 **wtp-dba-reviewer PLAN 검토 게이트 미해당** (방안B 쿼리·파티션 프루닝·응답량은 ANALYZE 안건 4 에서 DBA 검토 완료).

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| 전체 빌드 무결성 (부모/자식 DTO·신규 record·DTO·서비스·컨트롤러·조회 메서드) | ./gradlew.bat :api:build BUILD SUCCESSFUL 출력 확인 |
| 가동상태 서비스 단위 테스트 GREEN | ./gradlew.bat :api:test 에서 PumpOperationHistoryServiceTest 8케이스 PASS — ①활성0대 빈리스트 ②전구간 연속ON 단일세그먼트 ③다회 껐다켜기(gap) 다수세그먼트 ④중간 결측/BAD(ON행 부재 gap) split ⑤전구간 OFF(ON행 0건) 세그먼트0건 ⑥endDtm=마지막ON acqDtm+1분 ⑦OPS태그없는펌프 segments빈배열 ⑧from>to → INVALID_INQ_PERIOD 예외 |
| 3번섹션 회귀 무영향 (부모 상속 정렬 후) | ./gradlew.bat :api:test 에서 기존 PumpPowerTimeSeriesServiceTest·PumpFrequencyTimeSeriesServiceTest GREEN 유지 |
| 엔드포인트 1개 노출 | :api:bootRun 후 Swagger UI 에서 GET /api/instrument/pump-operation-history + CommonResponseDto<List<PumpOperationHistoryDto>> 스키마 + OperationSegment 배열 스키마 확인 |
| ON 조회 SQL 의미 정합 (커버리지 비보증 — test-strategy §5.4) | REVIEW 단계 코드 검토 — quality_cd='GOOD' AND raw_val=1 필터·corr_val 미참조·(tag_srl_no, acq_dtm) 정렬·acq_dtm 파티션 프루닝 WHERE 절 육안 검증 (Mockito 단위테스트는 repo 모킹으로 SQL 미검증, 선택적 수동 EXPLAIN ANALYZE) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| ON 판정 = 쿼리 `quality_cd='GOOD' AND raw_val=1`, corr_val 미참조 | 결정 | OPS HLV 미적용 (`ot-integration.md §3`). 2번섹션 `computeOprtngRate` 정합 |
| 결측·BAD·OFF 구간 무조건 split (gap bridge 미적용) | 결정 | domain 안건 3 블로커. 방안B(OFF 행 미조회)로 gap=split 자연 구현 |
| `endDtm = 마지막 ON acqDtm + 1분` (수집 시작 시각 + 수집주기) | 가정 | 막대 빈틈없는 연결. `acq_dtm`=수집 시작 시각 해석 (domain 안건 3 참고) |
| 연속성 판정 = 다음 ON `acqDtm` == 직전 ON `acqDtm + 1분`, `COLLECTION_INTERVAL_MINUTES=1` | 가정 | 수집주기 1분(`rawdata_1m_h`) 분 단위 정렬 전제. 초 단위 지터 존재 시 impl 에서 정규화 검토 |
| 신규 메서드 정렬 `(tag_srl_no ASC, acq_dtm ASC)` EXPLAIN ANALYZE Sort 노드 회피 확인 | 미해결 | 인덱스 `(tag_srl_no, acq_dtm DESC)` 역방향 스캔 실측 → impl 단계 확인 |
| **4번섹션 자식 SearchDto 미생성 — 부모 `PumpPeriodSearchDto` 직접 수신** | 결정 | ANALYZE 빈 자식 권고 대비 `§2` 단순성 재평가. 4번 추가 파라미터 0건 → 빈 클래스는 불필요한 추상화. 사용자 "추가 필드 시 상속" 의도 정합. **사용자 검토 시 빈 자식 선호 시 조정** |
| 3번섹션 `PumpTimeSeriesSearchDto` 부모 상속 정렬 = 기존 코드 변경 | 결정 | 사용자 명시 결정. RESULT "계획 외 변경 — 의도된 필수 부수 변경" 명기 (`§3`) |
| 조회기간 백엔드 상한 31일 미적용 | 결정 | dba 권고였으나 `§2` 단순성 + 사용자 미요청 → frontend UX 위임. 운영 SLA 초과 누적 시 별도 사이클. **사용자 검토 시 강제 원하면 조정** |
| 조회 전송 record = 경량 `RawDataOnStateDto`(tagSrlNo·acqDtm 2컬럼) 신규 vs `RawDataLatestDto` 재사용 | 결정 | 방안B 메모리 취지 정합 — ON 행은 rawVal/corrVal/qualityCd 고정값이라 2컬럼만 투영. `RawDataBucketDto` 내부 전송 record 선례 동형 |
| 펌프당 OPS 태그 1건 가정 (다건 시 first) | 가정 | 2번·3번섹션 `findFirst`/`(a,b)->a` 동형. 다건 합산 미구현 |
| 13개월 보존 초과 구간 = ON 행 부재 → 세그먼트 omit(에러 아님) | 가정 | 백엔드는 공백 응답. UI 안내는 frontend |

분류값: 가정 / 미해결 → 결정

## 예상 산출물

- [태스크](../../../tasks/20260602/송수펌프가동이력_4번섹션/TASK1.md) (TASK 단계 생성 예정)

### 신규/변경 파일

**api 모듈 (신규):**
- `api/src/main/java/com/mo/swtp/instrument/dto/PumpPeriodSearchDto.java` (from~to 공통 부모 SearchDto)
- `api/src/main/java/com/mo/swtp/instrument/dto/PumpOperationHistoryDto.java` (응답 DTO + 중첩 `OperationSegment`)
- `api/src/main/java/com/mo/swtp/instrument/service/PumpOperationHistoryService.java`
- `api/src/main/java/com/mo/swtp/instrument/web/PumpOperationHistoryController.java`
- `api/src/main/java/com/mo/swtp/raw/dto/RawDataOnStateDto.java` (경량 조회 전송 record)
- `api/src/test/java/com/mo/swtp/instrument/service/PumpOperationHistoryServiceTest.java` (신규)

**api 모듈 (변경):**
- `api/src/main/java/com/mo/swtp/instrument/dto/PumpTimeSeriesSearchDto.java` (부모 상속 정렬 — 중복 필드·변환 메서드 제거)
- `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` (`findOnStateByTagSrlNosAndDtmRange` 추가)
- `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` (Querydsl 구현 추가)

## 테스트 전략

- 단위 테스트 범위: `./gradlew.bat :api:test` (Mockito 기반, 2·3번섹션 Service 테스트 동형 — `Mockito.mock(Pump.class)` lenient stubbing).
- 런렝스 인코딩 로직은 repo 모킹(ON acqDtm 목록 주입)으로 검증 — 결측·BAD 구간은 "ON 행 부재(gap)" 로 모킹(repo 가 해당 시각 행 미반환). SQL 의미(GOOD·raw_val=1·정렬·파티션 프루닝)는 repo 모킹으로 비검증 → REVIEW 코드 검토 + 선택적 수동 EXPLAIN ANALYZE (`test-strategy §5.4` 쿼리 실행계획 커버리지 비보증 정합).
- 빌드 검증: `./gradlew.bat :api:build` (common 모듈 변경 0건 → :api 범위로 충분).
