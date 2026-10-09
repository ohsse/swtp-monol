---
status: approved
created: 2026-05-21
updated: 2026-05-21
---
# 운전현황분석-9번섹션 — 계획안

## 목적

`swtp/backend/image/운전현황분석.png` 9번 섹션 "예측 운영 현황" 카드의 backend API 신설. 활성 시설 ID 를 입력으로 받아 다음 4항목을 단발 GET 응답으로 통합 반환한다.

1. 사용된 모든 예측 태그의 동기화 시점 (`max(predc_dtm)`)
2. 활성 시설 산하 펌프 중 OPS 예측이 On 인 펌프 이름 목록
3. 예측 On 펌프들의 PWI 예측값 합산 (kW)
4. 예측 전력원단위 = 예측 PWI 합산 / 시설 FRI 예측 유출유량 (kWh/m³)

4번 섹션 "운영 현황" 카드 (`FacilityOperatingStatusService`, 데이터 소스 `rawdata_1m_h`) 와 **동일 구조**이나 데이터 소스가 `predc_1m_h` (AI 예측 시계열) 로 변경된 예측값 버전이다.

## 배경

- ANALYZE1 (`swtp/backend/docs/analyze/20260521/운전현황분석-9번섹션/ANALYZE1.md`, `status: approved`) 5인 회의 결과 6개 안건 결정 완료.
- 사용자 결정 D1·D3·D4 확정 — PWI 예측 태그 존재 / OPS On 판정 `predc_val == 1.0` / 9번 카드는 4번과 동일 구조의 별도 영역.
- **사용자 PLAN 단계 추가 결정 (D2 폐기)**: ANALYZE1 §회의 결론 + 사용자 D2 의 "시스템 전체 `max(predc_dtm)` 동기화 + 서브쿼리 패턴" 은 **풀스캔 시스템 부담** 우려로 **폐기**. **4번 섹션의 시점 정책 (`DISTINCT ON` + `NOW() - INTERVAL '1 hour'` 1시간 윈도우 파티션 프루닝 + `idx_predc_1m_h_tag_time` Index Scan Backward) 을 9번에도 동형 적용**. 시점은 사용된 태그들의 `max(predc_dtm)` 으로 산출 (태그별로 다른 시각 허용, 부분 결측 자연 처리 — 4번 `max(acq_dtm)` 동형).
- 신규 엔티티·DB 테이블·컬럼·표준 사전 변경 0건 (조회 전용 API).
- **4번 섹션의 4-SELECT 패턴 + 6단계 직렬 + private 헬퍼 분해 구조** 를 동형 재구현 (자동 원용이 아닌 4번 산출물 인용 근거). 사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합 — 4번 헬퍼 직접 재사용 금지, 9번 자체 private 메서드로 재작성.
- 4번 대비 차이 — `predc_1m_h` 는 `quality_cd`·`corr_val` 컬럼 부재 (INSERT-only immutable). 따라서 4번의 `effectiveVal(corrVal ?: rawVal)` 분기 + `quality_cd = GOOD` 분기를 모두 제거 → `predc_val` 단일값 직접 사용.
- ANALYZE1 §회의록 작성 시 다른 사이클 자산을 인용 근거로 일부 언급한 부분이 있으나 본 PLAN 에서는 **4번 섹션 자산만 인용** 으로 정렬한다 (사용자 피드백 — 9번은 4번 베이스, 그 외 사이클 자산은 본 PLAN 의 인용 근거에서 배제).

## 범위

### 포함

- 신규 응답 DTO: `com.mo.swtp.facility.dto.FacilityPredcOperatingStatusDto` (6필드 단층).
- 신규 Service: `com.mo.swtp.facility.service.FacilityPredcOperatingStatusService`.
- 신규 Repository (조회 메서드 + 내부 DTO):
  - `com.mo.swtp.opt.repository.TagPredcLatestRepository` (JpaRepository + Custom 결합)
  - `com.mo.swtp.opt.repository.TagPredcLatestCustomRepository` 인터페이스
  - `com.mo.swtp.opt.repository.TagPredcLatestCustomRepositoryImpl` (Querydsl 또는 native SQL)
  - `com.mo.swtp.opt.dto.TagPredcLatestDto` (Service 내부 전송 DTO, 3필드)
- 기존 Controller 확장: `com.mo.swtp.facility.web.FacilityController.findFacilityPredcOperatingStatus(facilityId)` 메서드 추가.
- 엔드포인트: `GET /api/facility/{facilityId}/operating-status/prediction`.
- 단위 테스트: `FacilityPredcOperatingStatusServiceTest` (Mockito 기반, 성공 기준 검증).
- 기존 Repository 메서드 재사용 (`FacilityRepository.findById` · `InstrumentRepository.findByFacilityIdAndEquipType` · `TagRepository.findByInstrumentInstrumentIdInAndUseYn`).
- 인덱스: 기존 `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)` 활용 — 신규 0건.

### 제외

- 4번 섹션 `FacilityOperatingStatusService` · `FacilityOperatingStatusDto` 코드 직접 변경 — 별도 응답 구조 + SRP 분리 (ANALYZE1 §회의 결론).
- AI 운전 모드 / 인터록 / 알람 4단계 / 제어 명령 로직 — 본 사이클 미접촉 (ANALYZE1 §도메인 룰 4영역 모두 비해당).
- 신규 엔티티·DB 테이블·컬럼·인덱스·표준 사전 — 변경 0건.
- 신규 도메인 패키지 신설 — `com.mo.swtp.facility.*` (Service·DTO·Controller) + `com.mo.swtp.opt.*` (Repository·내부 DTO) 확장으로 충분.
- AI 추론 파이프라인 (`predc_1m_h` INSERT 경로) — 본 사이클 미접촉 (조회 전용 사이클이므로 INSERT 시나리오 무영향).
- 4번 섹션의 `RawDataCustomRepository.findLatestByTagSrlNos` 동작 정의 (1시간 윈도우·DISTINCT ON) 의 직접 재사용 — 9번은 `predc_1m_h` 시스템 전체 `max(predc_dtm)` 동기화로 정책 자체 상이.

## 구현 방향

### Service 책임 분담 — `FacilityPredcOperatingStatusService.findFacilityPredcOperatingStatus(facilityId)`

ANALYZE1 안건 4 결론 (옵션 A 독립 클래스) + 4번 PLAN1 의 6단계 직렬 분해 패턴 동형. 정량 기준 §2.1 (50줄·3단·3단) 준수.

| Step | 책임 | 호출 메서드 (예상) | 4번 대비 차이 |
|------|------|----------------|-------------|
| 1 | 활성 시설 검증 + 시설 종류 필터 (PWTF·DWT·PRSF) | `findActiveFacilityOrThrow(facilityId)` | 동일 |
| 2 | 시설 직속 펌프 + 유량계 조회 | `instrumentRepository.findByFacilityIdAndEquipType(facilityId, List.of(PUMP, FLWMTR))` | 동일 |
| 3 | 펌프·유량계 활성 태그 조회 + OPS/PWI/FRI 필터링 | `tagRepository.findByInstrumentInstrumentIdInAndUseYn(...)` + stream 필터 | 동일 |
| 4 | 태그별 1시간 윈도우 최신 예측값 조회 | `tagPredcLatestRepository.findLatestByTagSrlNos(tagSrlNos)` | **변경** — `RawDataRepository` → 신규 `TagPredcLatestRepository` (1시간 윈도우 정책 동형) |
| 5 | 예측 On 펌프 식별 + 이름 목록 + PWI 합산 산출 | `collectOnPumps(...)` private 헬퍼 — `predc_val == 1.0` 조건 | `quality_cd = GOOD` 분기 제거 |
| 6 | 예측 전력원단위 산출 + `predcDtm` 산출 + DTO 조립 | `computeUnitConsumption(...)` + `selectMaxPredcDtm(...)` | `quality_cd`·`effectiveVal` 분기 제거 |

### 값 선택 정책 — `predcVal` 단일

4번의 `effectiveVal(corrVal != null ? corrVal : rawVal)` 분기 제거. `predc_1m_h` 는 `predc_val` 단일 컬럼만 보유.

```java
private BigDecimal predcVal(TagPredcLatestDto p) {
    return p == null ? null : p.predcVal();
}
```

Javadoc 에 "`predc_1m_h` 는 `corr_val` 컬럼 부재 — Hold Last Value 적용 결과 또는 운영자 보정 개념이 예측 데이터에는 적용되지 않음" 한 줄 명기.

### OPS On 판정 정책 (ANALYZE1 안건 2 + 사용자 D3)

```java
private boolean isPumpRunning(TagPredcLatestDto ops) {
    if (ops == null) return false;                 // 예측 결측 → On 제외
    BigDecimal val = predcVal(ops);
    return val != null && val.compareTo(BigDecimal.ONE) == 0;  // predc_val == 1.0 만 On
}
```

**4번 대비 차이**: `quality_cd != GOOD → false` 분기 제거. `predc_1m_h` 에 `quality_cd` 컬럼 부재 — `ot-integration.md §3` OPS BAD 즉시 격상 정책은 **실측 전용** (SCADA QUALITY 코드 부재로 예측에는 구조적 적용 불가). 도메인 위반 아닌 설계 범위 차이.

Javadoc 에 "`predc_1m_h` 는 `quality_cd` 컬럼 부재 — SCADA QUALITY 분기 미적용, `ot-integration.md §3` OPS BAD 즉시 격상은 실측 전용" 명기.

### PWI 합산 정책 (ANALYZE1 안건 6)

```java
private BigDecimal sumOnPumpPwr(
        List<Pump> onPumps,
        Map<String, List<Tag>> tagsByInstrument,
        Map<String, TagPredcLatestDto> latestByTag) {
    return onPumps.stream()
            .map(p -> pickLatest(tagsByInstrument, p.getInstrumentId(),
                    TagMeasurementType.PWI, latestByTag))
            .filter(pwi -> pwi != null)               // 예측 결측 펌프는 합산 제외
            .map(this::predcVal)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

**결정 #1**: PWI 예측 결측 (`predc_val IS NULL`) 펌프 = 해당 펌프만 합산 제외, 다른 예측 On 펌프 합산 유지. 4번 stream filter 패턴 동형 — `quality_cd` 분기 제거.

### 예측 전력원단위 산출 — `computeUnitConsumption`

```java
private BigDecimal computeUnitConsumption(BigDecimal totalElpwrAmt, TagPredcLatestDto fri) {
    if (totalElpwrAmt == null || totalElpwrAmt.compareTo(BigDecimal.ZERO) == 0) {
        return null;
    }
    if (fri == null) {
        return null;
    }
    BigDecimal friVal = predcVal(fri);
    if (friVal == null || friVal.compareTo(BigDecimal.ZERO) == 0) {
        return null;
    }
    return totalElpwrAmt.divide(friVal, 4, RoundingMode.HALF_UP);
}
```

**4번 대비 차이**: `fri.qualityCd() != QualityCode.GOOD → null` 분기 제거. 3 케이스 분기 (분자 0/null · FRI 부재 · 분모 null/0) 로 단순화 — 4번의 5 케이스 대비 `quality_cd` 분기 2건 (분자 PWI BAD·분모 FRI BAD) 제거.

단위 표기: kW / (m³/h) = kWh/m³. `@Schema(description="예측 전력원단위 (kWh/m³)")` 명시.
정밀도: `RoundingMode.HALF_UP` + scale=4 (BigDecimal `DOM_QTY_15_4` 정합).

### FRI 매핑 정책 (ANALYZE1 4번 가정 3 동형)

시설 직접 자식 `equip_type_cd = FLWMTR` 인스트루먼트의 FRI 예측 태그를 시설 유출유량으로 매핑. `parent_facility_id` 재귀 미적용. 시설별 복수 FRI 존재 시 첫 번째 비-null 태그 사용 (`quality_cd` 분기 없음 — 컬럼 부재).

```java
private TagPredcLatestDto selectFacilityFri(
        List<Instrument> instruments,
        Map<String, List<Tag>> tagsByInstrument,
        Map<String, TagPredcLatestDto> latestByTag) {
    return instruments.stream()
            .filter(i -> i.getEquipType() == EquipType.FLWMTR)
            .map(i -> pickLatest(tagsByInstrument, i.getInstrumentId(),
                    TagMeasurementType.FRI, latestByTag))
            .filter(r -> r != null)
            .findFirst()
            .orElse(null);
}
```

### 시설 종류 범위 (ANALYZE1 안건 5)

**결정**: 본 사이클 지원 시설 종류 = **PWTF · DWT · PRSF 3종** (4번 동일). RSV (저수조 — 펌프 미보유) · POINT (관로 분기점) 거부.

```java
private static final Set<FacilityType> SUPPORTED_TYPES =
        EnumSet.of(FacilityType.PWTF, FacilityType.DWT, FacilityType.PRSF);

private Facility findActiveFacilityOrThrow(String facilityId) {
    Facility facility = facilityRepository.findById(facilityId)
            .orElseThrow(() -> new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND));
    if (facility.getUseYn() != YnType.Y) {
        throw new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND);
    }
    if (!SUPPORTED_TYPES.contains(facility.getFacilityType())) {
        throw new RestApiException(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
    }
    return facility;
}
```

**ErrorCode 재사용** — 4번 사이클에서 추가된 `FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` (HTTP 400) 재사용. 신규 enum 값 추가 0건. `entity-patterns.md` §JPA JOINED 다형성 §도메인 룰 — `facility_type_cd` 필터 강제 정합.

### 시점 산출 — `selectMaxPredcDtm` (4번 동형)

4번의 `latestMeasurementDtm(max acq_dtm)` 와 동형 — 사용된 모든 예측 태그 (OPS+PWI+FRI) 의 `predc_dtm` 중 `max()` 로 산출. 태그별로 다른 `predc_dtm` 허용 — Repository 가 태그별 1시간 윈도우 최신 1건씩 반환하므로 각 태그의 시각이 다를 수 있음.

```java
private LocalDateTime selectMaxPredcDtm(Collection<TagPredcLatestDto> usedTags) {
    return usedTags.stream()
            .filter(r -> r != null)
            .map(TagPredcLatestDto::predcDtm)
            .filter(d -> d != null)
            .max(Comparator.naturalOrder())
            .orElse(null);
}
```

사용된 태그 = OPS + PWI + FRI 의 모든 `TagPredcLatestDto`. 1시간 윈도우 내 예측 행이 없는 태그는 Repository 응답에 미포함 → Map 결측 → 시점 산출 대상 외. 빈 데이터 시 `null` 반환.

### Repository 신규 — `TagPredcLatestRepository.findLatestByTagSrlNos`

**옵션 A** (ANALYZE1 안건 4 결론) — 9번 사이클 자체 Repository 신설. `opt` 도메인 패키지에 신규 Repository 인터페이스·구현·내부 DTO 작성. 다른 사이클 자산 (동일 테이블 `predc_1m_h` 에 대한 기존 Repository 가 있다면) 의 메서드 직접 추가는 본 사이클 책임 범위 외 — `coding-discipline.md §3` 정밀한 수정 (본인이 만든 코드의 뒷정리만) 정합.

#### 시그니처

```java
public interface TagPredcLatestCustomRepository {
    /**
     * 태그 시리얼번호 목록의 각 태그별 1시간 윈도우 내 최신 예측값을 단일 native SQL 로 조회한다.
     * <p>구현은 PostgreSQL DISTINCT ON 절 + {@code idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)} 인덱스
     * 활용을 강제한다. {@code predc_dtm >= NOW() - INTERVAL '1 hour'} 하한으로 월 RANGE 파티션 프루닝 강제
     * (4번 섹션 {@code RawDataCustomRepository.findLatestByTagSrlNos} 동형 정책).</p>
     * <p>1시간 윈도우 내 예측 행이 없는 태그는 응답 목록에 포함되지 않는다.</p>
     */
    List<TagPredcLatestDto> findLatestByTagSrlNos(List<String> tagSrlNos);
}
```

#### Repository 인터페이스 결합

```java
public interface TagPredcLatestRepository
        extends JpaRepository<PredcData1Min, Long>, TagPredcLatestCustomRepository {
}
```

> JpaRepository 타입 파라미터 `PredcData1Min` 는 `predc_1m_h` 엔티티 클래스. **본 클래스가 기 존재한다면 본 사이클은 재사용** (`com.mo.swtp.common.opt.domain.PredcData1Min` 또는 유사 명칭). 미존재 시 TASK Phase 1 에서 별도 사용자 결정 — 본 사이클 신설 vs 기 존재 엔티티 활용. 가정 섹션 명기.

#### SQL — DISTINCT ON + 1시간 윈도우 (4번 동형)

4번 섹션 `RawDataCustomRepository.findLatestByTagSrlNos` 의 native SQL 패턴 동형 — 데이터 소스만 `rawdata_1m_h` → `predc_1m_h`, 파티션 키만 `acq_dtm` → `predc_dtm`. 단일 메서드 본문 50줄 자연 초과 가능 — `coding-discipline.md §2.5` 면책 영역 (DB 쿼리 빌더·튜닝 코드, 인용 근거 `db/query-tuning.md §2`) 적용.

```sql
SELECT DISTINCT ON (tag_srl_no) tag_srl_no, predc_dtm, predc_val
FROM predc_1m_h
WHERE tag_srl_no IN (:tagSrlNos)
  AND predc_dtm >= NOW() - INTERVAL '1 hour'
ORDER BY tag_srl_no, predc_dtm DESC;
```

- PostgreSQL DISTINCT ON — 태그별 최신 1건 단방향 추출.
- `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)` ASC 인덱스 + Index Scan Backward (Sort 노드 부재).
- `predc_dtm >= NOW() - INTERVAL '1 hour'` 하한 → 월 RANGE 파티션 프루닝 강제 (현재 시점 1개 파티션만 스캔).
- 추가 인덱스 0건 — 기존 인덱스 활용. 슬로우 발생 시 `EXPLAIN ANALYZE` 보고 재검토.
- **풀스캔 방지 핵심**: 1시간 윈도우 하한 + `idx_predc_1m_h_tag_time` 활용으로 옵티마이저가 단일 파티션 + 인덱스 스캔 강제.

#### 내부 DTO

```java
public record TagPredcLatestDto(
        String tagSrlNo,
        LocalDateTime predcDtm,
        BigDecimal predcVal
) {
}
```

3필드 — 4번 `RawDataLatestDto` (5필드: tagSrlNo·rawVal·corrVal·acqDtm·qualityCd) 대비 `rawVal·corrVal·qualityCd` 3필드 제거 + `acqDtm` → `predcDtm` 명칭 변경.

### Controller 시그니처

```java
@Operation(summary = "활성 시설의 예측 운영 현황 조회 (운전현황분석 9번 섹션)",
        description = "활성 시설의 PUMP 자식 인스트루먼트 중 OPS 예측이 On (predc_val = 1.0) 인 펌프 "
                + "이름 목록, 예측 PWI 합산(kW), 시설 직속 FLWMTR 의 FRI 예측값과의 예측 전력원단위(kWh/m³), "
                + "사용된 모든 예측 태그의 max(predc_dtm) 을 단일 응답으로 반환한다. "
                + "지원 시설 종류: PWTF · DWT · PRSF (RSV·POINT 는 400 거부). "
                + "predc_1m_h 는 quality_cd·corr_val 컬럼 부재 — SCADA QUALITY 분기 + Hold Last Value 분기 모두 미적용. "
                + "예측 결측(predc_val IS NULL) 펌프는 onPumpNms 에서 제외, "
                + "FRI 예측 결측 또는 0 시 elpwrUnitQty = null 반환 (0 나누기 방어).")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공"),
        @ApiResponse(responseCode = "400", description =
                "잘못된 요청 (UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류 거부)"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "권한 없음"),
        @ApiResponse(responseCode = "404", description = "시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성)"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
})
@GetMapping("/{facilityId}/operating-status/prediction")
public ResponseEntity<CommonResponseDto<FacilityPredcOperatingStatusDto>> findFacilityPredcOperatingStatus(
        @PathVariable String facilityId) {
    return getResponseEntity(
            facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus(facilityId));
}
```

`@Tag(name = "06. 시설물 관리")` 동일 — 기존 `FacilityController` 메서드 추가.

### DTO 구조 — `FacilityPredcOperatingStatusDto`

| 필드 | 타입 | 4번 대응 | 설명 | 빈 데이터 시 |
|------|------|---------|------|------------|
| `facilityId` | `String` | 동일 | 활성 시설 ID | — (항상 채움) |
| `facilityNm` | `String` | 동일 | 시설명 | — (항상 채움) |
| `predcDtm` | `LocalDateTime` | `measurementDtm` 대응 | 동기화된 예측 대상 시점 (`max(predc_dtm)`) | `null` |
| `onPumpNms` | `List<String>` | 동일 | 예측 On 펌프 이름 목록 (`predc_val = 1.0`) | `[]` (빈 리스트) |
| `totalElpwrAmt` | `BigDecimal` | 동일 | 예측 On 펌프 PWI 예측값 합산 (kW) | `BigDecimal.ZERO` |
| `elpwrUnitQty` | `BigDecimal` | 동일 | 예측 전력원단위 (kWh/m³) | `null` |

- 단층 (`BaseAuditResponseDto` 미상속 — 실시간 통지성, `api-patterns.md` §BaseAuditResponseDto 적용 외 정합, 4번 동일).
- `@JsonFormat(shape = STRING, pattern = "yyyy-MM-dd HH:mm:ss")` 적용 (4번 동일).
- 정적 팩토리 `FacilityPredcOperatingStatusDto.of(...)` 단일 진입점.
- `@Schema` 어노테이션 전 필드 명시 + `predc_val IS NULL` (결측·신뢰도 불명) vs `predc_val == 0.0` (예측 Off 확신) 구별 정책을 `onPumpNms` 의 `@Schema(description)` 에 명기 (ANALYZE1 안건 2 권고).

### 인덱스 — 신규 0건

기존 `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)` 활용 — ANALYZE1 안건 3 결론. 추가 인덱스 미신설.

## 성공 기준 (검증 가능 형태)

| # | 기준 | 검증 명령·테스트·조회 |
|---|------|------------------|
| 1 | `FacilityPredcOperatingStatusService.findFacilityPredcOperatingStatus(facilityId)` 가 활성 시설의 예측 On 펌프 이름·예측 PWI 합산·예측 전력원단위·`predcDtm` 을 단일 호출로 반환 (정상 시나리오 — PUMP 3대 중 2대 예측 On, FRI/PWI 예측 정상) | 신규 단위 테스트 PASS — `FacilityPredcOperatingStatusServiceTest.예측운영현황_정상_조회_시_예측On펌프이름_예측PWI합산_예측전력원단위_predcDtm_반환` |
| 2 | Repository 가 태그별 1시간 윈도우 (`predc_dtm >= NOW() - INTERVAL '1 hour'`) 내 `DISTINCT ON` 최신 1건만 반환, 응답 `predcDtm` 은 사용된 태그들의 `max(predc_dtm)` 으로 산출 (태그별로 다른 시각 허용) | 신규 단위 테스트 PASS — `predcDtm_은_사용된_태그들의_max_predc_dtm_으로_산출된다` + Repository native SQL grep 검증 (`NOW() - INTERVAL '1 hour'` 포함) |
| 3 | OPS 예측 On 판정 `predc_val == 1.0` 만 — null/0.0/0.7/1.5 모두 On 제외 | 파라미터화 테스트 PASS — `OPS_predc_val_4_케이스` (null·0.0·0.7·1.5 모두 onPumpNms 미포함) |
| 4 | `quality_cd` 분기 0건 — Service 메서드 grep 시 `qualityCd`·`QualityCode` 키워드 부재 | grep 검증 — `grep -E "qualityCd\|QualityCode" FacilityPredcOperatingStatusService.java` 매칭 0 (REVIEW 자동 점검) |
| 5 | `effectiveVal`·`corrVal`·`rawVal` 분기 0건 — Service 메서드 grep 시 해당 키워드 부재 | grep 검증 — `grep -E "effectiveVal\|corrVal\|rawVal" FacilityPredcOperatingStatusService.java` 매칭 0 (REVIEW 자동 점검) |
| 6 | 시설 종류 PWTF·DWT·PRSF 외 거부 (RSV/POINT) — 4번 ErrorCode 재사용 | 신규 단위 테스트 PASS — `RSV_시설은_UNSUPPORTED_FACILITY_TYPE_거부` + `POINT_시설은_UNSUPPORTED_FACILITY_TYPE_거부` |
| 7 | 빈 데이터 (`predc_1m_h` 0건) → `predcDtm=null` / `onPumpNms=[]` / `totalElpwrAmt=BigDecimal.ZERO` / `elpwrUnitQty=null` | 신규 단위 테스트 PASS — `예측데이터_부재_시_빈_응답_반환` |
| 8 | PWI 부분 결측 (`predc_val IS NULL`) 시 해당 펌프만 합산 제외, 다른 예측 On 펌프 합산 유지 | 신규 단위 테스트 PASS — `PWI_부분_결측_시_다른_펌프_합산_유지` |
| 9 | FRI 예측 결측 (`predc_val IS NULL` 또는 `0`) 시 `elpwrUnitQty = null` 반환 (0 나누기 방어) | 파라미터화 테스트 PASS — `elpwrUnitQty_분모_무효_시_null_반환` (FRI null·0·부재 3 케이스) |
| 10 | `equip_type_cd = 'PUMP'` 필터 강제 — VALVE/FLWMTR/PRSMTR 등 다른 인스트루먼트가 `onPumpNms` 에 미포함 | 신규 단위 테스트 PASS — `다른_자식_종류_인스트루먼트는_onPumpNms_에_포함되지_않는다` |
| 11 | FACILITY_NOT_FOUND (비활성·미존재) 거부 | 신규 단위 테스트 PASS — `비활성_시설은_FACILITY_NOT_FOUND_거부` + `미존재_시설은_FACILITY_NOT_FOUND_거부` |
| 12 | Service 메서드 본문 50줄 이내 + 추상화 깊이 3단 이내 (`coding-discipline.md §2.1`) | `wtp-backend-engineer` REVIEW 자동 점검 |
| 13 | `GET /api/facility/{facilityId}/operating-status/prediction` Swagger UI 호출 시 `CommonResponseDto<FacilityPredcOperatingStatusDto>` 응답 200 OK + 6필드 정상 노출 + 한국어 description | `./gradlew.bat :api:bootRun` 후 `http://localhost:8080/swagger-ui.html` 에서 본 엔드포인트 호출 → 응답 구조 확인 |
| 14 | 빌드 + 전체 테스트 BUILD SUCCESSFUL | `./gradlew.bat build` 종료 코드 0 + BUILD SUCCESSFUL 출력 |

## 가정 및 미해결 질문

ANALYZE1 의 7건 가정·미해결을 본 PLAN 에서 결정으로 변환한다.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| OPS 예측 결측 (`predc_val IS NULL`) 과 예측 Off (`predc_val == 0.0`) 의 응답 표현 구별 정책 | 결정 | **본 사이클 `onPumpNms` 리스트는 둘 다 On 제외 합쳐짐**. Swagger `@Schema(description)` 에 두 케이스 구별 부재 명시 (운전원에게는 둘 다 "On 아님" 으로 표시). 향후 사이클에서 별도 필드 (`uncertainPumpNms` 등) 도입 검토 가능 |
| PWI 부분 결측 (특정 펌프 `predc_val IS NULL`) 시 `totalElpwrAmt` 처리 | 결정 | **해당 펌프만 합산 제외, 다른 예측 On 펌프 합산 유지** (4번 stream filter 패턴 동형) |
| FRI 예측 결측 (`predc_val IS NULL` 또는 `0`) 시 `elpwrUnitQty` 처리 | 결정 | **`elpwrUnitQty = null`** (분모 무효 방어) — 4번 `computeUnitConsumption` 가정 3 동형 |
| 시점 정책 — 4번 섹션 동형 (`DISTINCT ON` + 1시간 윈도우) 채택, ANALYZE1 D2 (시스템 전체 max 동기화) 폐기 | 결정 | **사용자 PLAN 단계 결정 (풀스캔 부담 회피)** — Repository 가 `predc_dtm >= NOW() - INTERVAL '1 hour'` 하한으로 파티션 프루닝 강제 + 태그별 DISTINCT ON 최신 1건 반환. 시점은 사용된 태그들의 `max(predc_dtm)` 으로 산출 (4번 `max(acq_dtm)` 동형). 태그별로 다른 `predc_dtm` 허용 — 부분 결측 자연 처리 |
| 1시간 윈도우 내 예측 행이 없는 태그의 처리 | 결정 | Repository 응답 List 에 미포함 → Map 결측 → OPS 결측 시 On 제외 / PWI 결측 시 PWI 합산 제외 / FRI 결측 시 `elpwrUnitQty = null`. 부분 결측 허용 (4번 1시간 윈도우 정책 동형) |
| `predc_1m_h` 데이터 자체가 0건 (시스템 전체) 시점에서의 응답 | 결정 | **`predcDtm=null` + `onPumpNms=[]` + `totalElpwrAmt=BigDecimal.ZERO` + `elpwrUnitQty=null`** (4번 빈 데이터 패턴 동형) |
| `predcDtm` 직렬화 정책 | 결정 | **`@JsonFormat(shape = STRING, pattern = "yyyy-MM-dd HH:mm:ss")`** (4번 `measurementDtm` 동형) |
| AI 추론 파이프라인 (`predc_1m_h` INSERT 경로) 본 사이클 미접촉 | 결정 | 조회 전용 사이클이므로 INSERT 시나리오 무영향. 추론 파이프라인 신설은 별도 사이클 책임 |
| `com.mo.swtp.opt.domain.PredcData1Min` (또는 유사 명칭) 엔티티 클래스 존재 여부 | 가정 | TASK Phase 1 에서 `Glob api/src/main/java/com/mo/swtp/opt/domain/*.java` 로 확인. **기 존재 시 본 사이클은 엔티티 재사용** (재정의 금지 — common 모듈 책임). **미존재 시 본 사이클 신설** — `com.mo.swtp.common.opt.domain.PredcData1Min` (common 모듈) 신규 엔티티 작성. 마이그레이션 SQL 재확인 — `common/src/main/resources/db/migration/` 의 `predc_1m_h` 테이블 정의 (테이블은 기 존재 가정 — DDL 신설 0건, 엔티티 매핑만 신설) |
| Repository Querydsl 우선 vs native SQL | 결정 | **native SQL 채택** — 4번 섹션 `RawDataCustomRepositoryImpl` 의 DISTINCT ON + NOW()-1h 패턴 동형 구현 시 PostgreSQL 전용 절 (DISTINCT ON) 이 Querydsl 표현 불가. `coding-discipline.md §2.5` 면책 영역 (DB 쿼리 빌더·튜닝 코드) 적용 + 인용 근거 (`db/query-tuning.md §2`) Javadoc 명기 |

## 제외 사항

- 4번 섹션 `FacilityOperatingStatusService` · `FacilityOperatingStatusDto` 코드 변경 — 별도 응답 구조 + SRP 분리.
- AI 운전 모드 / 인터록 / 알람 4단계 / 제어 명령 — ANALYZE1 §4영역 모두 비해당.
- AI 추론 파이프라인 (`predc_1m_h` INSERT 경로) — 본 사이클 미접촉.
- 표준 사전·룰 파일 변경 — ANALYZE1 §룰 갱신 지시서 0건.
- 신규 도메인 패키지 신설 — `com.mo.swtp.facility.*` + `com.mo.swtp.opt.*` 확장으로 충분 (`coding-discipline.md §2` 정합).
- ANALYZE1 §회의 결론 안건 3 의 "서브쿼리 패턴 A (시스템 전체 `max(predc_dtm)` 등가)" + 사용자 D2 (전 태그 동기화) — 풀스캔 부담으로 폐기. PLAN 단계에서 4번 섹션 동형 (`DISTINCT ON` + 1시간 윈도우) 으로 정렬.
- SensorPoint·재귀 자식 시설 FRI 매핑 — 본 사이클 미적용 (4번 가정 3 동형 유지).

## 예상 산출물

- [태스크](../../../tasks/20260521/운전현황분석-9번섹션/TASK1.md) — `/dev:task` 단계에서 작성. Phase 5~6개 예상:
  - Phase 1: 엔티티 매핑 확인 (PredcData1Min) + Repository 신설
  - Phase 2: 내부 DTO 신설 (TagPredcLatestDto)
  - Phase 3: 응답 DTO 신설 (FacilityPredcOperatingStatusDto)
  - Phase 4: Service 신설 (FacilityPredcOperatingStatusService)
  - Phase 5: Controller 메서드 추가
  - Phase 6: 단위 테스트 작성 + 통합 빌드

분할 미적용 예상 (Phase 6 / 체크박스 30 내외 — 분할 기준 미달).

---

## 참조 문서

- ANALYZE1: `swtp/backend/docs/analyze/20260521/운전현황분석-9번섹션/ANALYZE1.md` (`status: approved`)
- 4번 섹션 산출물 (인용 근거 — 동형 재구현):
  - ANALYZE1: `swtp/backend/docs/analyze/20260521/운전현황분석-4번섹션/ANALYZE1.md`
  - PLAN1: `swtp/backend/docs/plan/20260521/운전현황분석-4번섹션/PLAN1.md`
  - TASK1: `swtp/backend/docs/tasks/20260521/운전현황분석-4번섹션/TASK1.md`
- 4번 섹션 코드 (동형 재구현 근거 — 직접 재사용 금지):
  - `swtp/backend/api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusService.java`
  - `swtp/backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityOperatingStatusDto.java`
- 도메인 룰: `swtp/backend/.claude/rules/ot-integration.md §3·§5` (OPS BAD 즉시 격상은 실측 전용 — 예측 미적용 정합)
- 다형성 룰: `swtp/backend/.claude/rules/entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴 §도메인 룰` (`equip_type_cd` 필터 강제)
- DB 정책:
  - `swtp/backend/.claude/rules/db/partitioning-and-retention.md §1` (`predc_1m_h` 월 RANGE 파티션)
  - `swtp/backend/.claude/rules/db/indexing-and-migration.md §1·§5` (인덱스 컬럼 순서 · SQL 관리 이중 정책)
  - `swtp/backend/.claude/rules/db/query-tuning.md §2` (옵티마이저 분석 — native SQL 면책 근거)
- DTO 패턴: `swtp/backend/.claude/rules/api-patterns.md §BaseAuditResponseDto 패턴` (실시간 통지성 미적용)
- 코딩 디시플린: `swtp/.claude/rules/coding-discipline.md §2`·`§2.1`·`§2.5`·`§3`·`§4.2`
