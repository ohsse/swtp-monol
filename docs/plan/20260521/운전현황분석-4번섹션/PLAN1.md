---
status: approved
created: 2026-05-21
updated: 2026-05-21
---
# 운전현황분석-4번섹션 — 계획안

## 목적

`swtp/backend/image/운전현황분석.png` 4번 섹션 "운영 현황" 카드의 backend API 신설. 활성 시설 ID 를 입력으로 받아 다음 4항목을 단발 GET 응답으로 통합 반환한다.

1. 사용된 모든 태그 중 `max(acq_dtm)` (카드 타이틀 옆 시간)
2. 활성 시설 산하 펌프 중 `OPS = On` 인 펌프 이름 목록
3. On 펌프들의 PWI 측정값 합산 (kW)
4. 전력원단위 = PWI 합산 / 시설 FRI 유출유량 순시값 (kWh/m³)

## 배경

- ANALYZE1 (`swtp/backend/docs/analyze/20260521/운전현황분석-4번섹션/ANALYZE1.md`, `status: approved`) 5인 회의 결과 8개 안건 결정 완료.
- 사용자 결정 D1~D4 확정 (이전 사이클 docx 미참조 / 활성 시설 frontend 전달 / 단발 HTTP GET polling / 시간은 max(acq_dtm)).
- 신규 엔티티·DB 테이블·컬럼·표준 사전 변경 0건 (조회 전용 API).
- 송수펌프제어분석-3번섹션 `FacilityStateService` 4-SELECT 패턴 (Facility 1 + Instrument 1 + Tag 1 + RawData 1) 의 구조적 유사성이 있으나, 자동 원용이 아닌 `ot-integration.md §3` 직접 인용으로 정당화 (사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합).

## 범위

### 포함

- 신규 응답 DTO: `com.mo.swtp.facility.dto.FacilityOperatingStatusDto` (6필드 단층).
- 신규 Service: `com.mo.swtp.facility.service.FacilityOperatingStatusService`.
- 기존 Controller 확장: `com.mo.swtp.facility.web.FacilityController.findFacilityOperatingStatus(facilityId)` 메서드 추가.
- 엔드포인트: `GET /api/facility/{facilityId}/operating-status`.
- 단위 테스트: `FacilityOperatingStatusServiceTest` (Mockito 기반, §성공 기준 5종 검증).
- 기존 Repository 메서드 재사용 (`FacilityRepository.findById` · `InstrumentRepository.findByFacilityIdAndEquipType` · `TagRepository.findByInstrumentInstrumentIdInAndUseYn` · `RawDataRepository.findLatestByTagSrlNos`).
- 인덱스 확인 + 미존재 시 추가 — `tag_m (instrument_id, tag_se_cd)` 또는 `idx_tag_m_instrument_id` 복합 인덱스.

### 제외

- 12번 섹션 (활성화 시설 선택) — frontend 책임 (사용자 D2).
- SSE 실시간 푸시 — frontend polling 채택 (사용자 D3).
- AI 운전 모드 / 인터록 / 알람 / 제어 명령 로직 — 본 사이클 미접촉 (ANALYZE1 §도메인 룰 4영역 비해당).
- 신규 엔티티·DB 테이블·컬럼·표준 사전 — 변경 0건 (안건 1 결론).
- 송수펌프제어분석-3번섹션의 `FacilityStateDto`·`FacilityStateService` 코드 직접 변경 — 별도 응답 구조 + SRP 분리 (안건 7 결론).

## 구현 방향

### Service 책임 분담 — `FacilityOperatingStatusService.findFacilityOperatingStatus(facilityId)`

ANALYZE1 안건 7 결론에 따라 직렬 6단계를 private 메서드로 분해 (정량 기준 §2.1 50줄 초과 방지). `FacilityStateService` 와 같은 헬퍼 분해 패턴을 따른다.

| Step | 책임 | 호출 메서드 (예상) |
|------|------|----------------|
| 1 | 활성 시설 검증 + 시설 종류 필터 | `findActiveFacilityOrThrow(facilityId)` (시설 종류 PWTF·DWT·PRSF 검증 포함) |
| 2 | 시설 직속 펌프 + 유량계 조회 | `instrumentRepository.findByFacilityIdAndEquipType(facilityId, List.of(PUMP, FLWMTR))` |
| 3 | 펌프·유량계 활성 태그 조회 + OPS/PWI/FRI 필터링 | `tagRepository.findByInstrumentInstrumentIdInAndUseYn(...)` + stream 필터 |
| 4 | 태그별 최신 측정값 조회 | `rawDataRepository.findLatestByTagSrlNos(tagSrlNos)` |
| 5 | On 펌프 식별 + 이름 목록 + PWI 합산 산출 | `collectOnPumps(...)` private 헬퍼 — `quality_cd = GOOD` AND `effectiveVal == 1.0` 조건 |
| 6 | 전력원단위 산출 + max(acq_dtm) 산출 + DTO 조립 | `computeUnitConsumption(...)` + `latestMeasurementDtm(...)` 헬퍼 |

### 값 선택 정책 (Service private 헬퍼)

```java
private BigDecimal effectiveVal(RawDataLatestDto r) {
    if (r == null) return null;
    return r.corrVal() != null ? r.corrVal() : r.rawVal();
}
```

- ANALYZE1 안건 4 결론: `corrVal != null ? corrVal : rawVal` 단순 분기.
- DB 측 `COALESCE` 강제 금지 — `RawDataLatestDto` 가 두 값 모두 보유, Service 메모리 처리가 자연.

### OPS On 판정 정책 (ANALYZE1 안건 2 + 가정 1·2 결정)

```java
private boolean isPumpRunning(RawDataLatestDto ops) {
    if (ops == null) return false;                           // 1시간 윈도우 내 데이터 부재 → On 제외
    if (ops.qualityCd() != QualityCode.GOOD) return false;   // BAD/UNCERTAIN OPS → On 제외 (PLAN 결정 #1)
    BigDecimal val = effectiveVal(ops);
    return val != null && val.compareTo(BigDecimal.ONE) == 0;  // raw_val == 1.0 만 On
}
```

**가정 1 결정**: `UNCERTAIN` OPS 펌프 = **On 합산 제외**. 근거 — `ot-integration.md §3` 의 OPS BAD 즉시 격상 정책 (Hold Last Value 미적용, 운전원 오인 회피) 정신을 UNCERTAIN 에도 보수적으로 확장. 가중치 0.5 정책은 이진 신호에 부적합 (wtp-domain-expert Round 1).

### PWI 합산 정책 (ANALYZE1 안건 3 + 가정 2 결정)

```java
private BigDecimal sumOnPumpPwr(List<Pump> onPumps, Map<String, RawDataLatestDto> pwiByPumpId) {
    return onPumps.stream()
            .map(pump -> pwiByPumpId.get(pump.getInstrumentId()))
            .filter(pwi -> pwi != null && pwi.qualityCd() == QualityCode.GOOD)  // GOOD 만
            .map(this::effectiveVal)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

**가정 2 결정**: `UNCERTAIN` PWI = **전액 제외** (가중치 0.5 미적용). 근거 — 순시전력 합산에 가중치 0.5 적용 시 실제 전력 소비와 오차 발생, frontend 표출 정확도 우선. 단 ANALYZE1 안건 3 `wtp-domain-expert` Round 1 권고와 정합.

### 전력원단위 산출 (ANALYZE1 안건 3 결론)

```java
private BigDecimal computeUnitConsumption(BigDecimal totalElpwrAmt, RawDataLatestDto fri) {
    if (totalElpwrAmt == null || totalElpwrAmt.compareTo(BigDecimal.ZERO) == 0) return null;
    if (fri == null || fri.qualityCd() != QualityCode.GOOD) return null;
    BigDecimal friVal = effectiveVal(fri);
    if (friVal == null || friVal.compareTo(BigDecimal.ZERO) == 0) return null;
    return totalElpwrAmt.divide(friVal, 4, RoundingMode.HALF_UP);  // kWh/m³ (정밀도 4자리)
}
```

- 분모 0/NULL/BAD/부재 → `elpwrUnitQty = null` 반환 (0 나누기 방어).
- 분자 (PWI 합산) 0 또는 null → `elpwrUnitQty = null` 반환 (의미 혼동 회피).
- 단위: kW / (m³/h) = kW·h/m³ = kWh/m³ — `@Schema(description="전력원단위 (kWh/m³)")` 명시 (PLAN 결정 #6).
- 정밀도: `RoundingMode.HALF_UP` + scale 4 (BigDecimal `DOM_QTY_15_4` 정합).

### FRI 매핑 정책 (ANALYZE1 안건 3 + 가정 3 결정)

**가정 3 결정 유지**: 시설 직접 자식 `equip_type_cd = FLWMTR` 인스트루먼트의 FRI 태그를 시설 유출유량으로 가정. `instrument_m.facility` 직접 자식만 조회 (parent_facility_id 재귀 미적용). 시설별 FRI 가 복수 존재할 경우 첫 번째 GOOD 태그 사용 — 단일 FRI 운영 전제 (현장 배선 확인 필요 항목으로 운영자 보정 가능, PLAN 단계 frontend·운영자 의견 미수렴 시 향후 사이클 보완).

### 시설 종류 범위 (가정 4 결정)

**가정 4 결정**: 본 사이클 지원 시설 종류 = **PWTF · DWT · PRSF 3종**. RSV (저수조 — 펌프 미보유) · POINT (관로 분기점 — 자체 펌프·유량계 없음) 는 거부.

```java
private static final EnumSet<FacilityType> SUPPORTED_TYPES =
        EnumSet.of(FacilityType.PWTF, FacilityType.DWT, FacilityType.PRSF);

private Facility findActiveFacilityOrThrow(String facilityId) {
    Facility f = facilityRepository.findById(facilityId)
            .orElseThrow(() -> new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND));
    if (f.getUseYn() != YnType.Y) throw new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND);
    if (!SUPPORTED_TYPES.contains(f.getFacilityType())) {
        throw new RestApiException(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
    }
    return f;
}
```

- `FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` (HTTP 400) 신규 추가 (안건 7 결론 외 PLAN 단계 보강).
- `entity-patterns.md` §JPA JOINED 다형성 §도메인 룰 — `facility_type_cd` 필터 강제 의무 정합.

### 측정시간 산출 (ANALYZE1 안건 6 결론)

```java
private LocalDateTime latestMeasurementDtm(Collection<RawDataLatestDto> usedTags) {
    return usedTags.stream()
            .filter(r -> r != null)
            .map(RawDataLatestDto::acqDtm)
            .filter(d -> d != null)
            .max(Comparator.naturalOrder())
            .orElse(null);
}
```

- 사용된 태그 = OPS + PWI + FRI 의 모든 RawDataLatestDto.
- 빈 데이터 시 `null` 반환 (Swagger 명세 명시).

### Controller 시그니처

```java
@GetMapping("/{facilityId}/operating-status")
@Operation(summary = "활성 시설의 운영 현황 조회 (운전현황분석 4번 섹션)")
public ResponseEntity<CommonResponseDto<FacilityOperatingStatusDto>> findFacilityOperatingStatus(
        @PathVariable String facilityId) {
    return getResponseEntity(facilityOperatingStatusService.findFacilityOperatingStatus(facilityId));
}
```

- 기존 `FacilityController` 의 `@Tag(name = "06. 시설물 관리")` Tag 동일.
- `@ApiResponses` 6종 (200/400/401/403/404/500) 명시.

### DTO 구조 — `FacilityOperatingStatusDto`

| 필드 | 타입 | 설명 | 빈 데이터 시 |
|------|------|------|------------|
| `facilityId` | `String` | 활성 시설 ID | — (항상 채움) |
| `facilityNm` | `String` | 시설명 | — (항상 채움) |
| `measurementDtm` | `LocalDateTime` | 사용된 태그 중 `max(acq_dtm)` | `null` |
| `onPumpNms` | `List<String>` | On 펌프 이름 목록 | `[]` (빈 리스트) |
| `totalElpwrAmt` | `BigDecimal` | On 펌프 PWI 합산 (kW) | `null` (또는 `BigDecimal.ZERO` — §성공 기준에서 결정) |
| `elpwrUnitQty` | `BigDecimal` | 전력원단위 (kWh/m³) | `null` |

- 단층 (`BaseAuditResponseDto` 미상속 — 실시간 통지성, `api-patterns.md` §BaseAuditResponseDto 적용 외 정합).
- `@JsonFormat(shape = STRING, pattern = "yyyy-MM-dd HH:mm:ss")` 적용 (`api-patterns.md` 직렬화 정책 정합).
- 정적 팩토리 `FacilityOperatingStatusDto.of(...)` 단일 진입점.
- `@Schema` 어노테이션 전 필드 명시 (Swagger UI 노출).

### 인덱스 확인 + 추가 (가정 5 결정)

PLAN 단계에서 `psql \d+ tag_m` 또는 `pg_indexes` 조회로 다음 인덱스 존재 여부 확인:

```sql
SELECT indexname FROM pg_indexes
WHERE tablename = 'tag_m' AND indexdef ILIKE '%instrument_id%';
```

미존재 시 TASK Phase 1 에 다음 추가:

```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tag_m_instrument_id_tag_se_cd
    ON tag_m (instrument_id, tag_se_cd) WHERE use_yn = 'Y';
```

- 컬럼 순서: `instrument_id` (높은 카디널리티) → `tag_se_cd` (낮은 카디널리티) — `db/indexing-and-migration.md §1` 정합.
- 부분 인덱스 (`WHERE use_yn = 'Y'`) — 인덱스 크기 최소화 (use_yn = Y 가 대부분이지만 비활성 행 제외).
- 운영본 DDL + `docs/ddl/tag.sql` 양쪽 동시 갱신 (`db/indexing-and-migration.md §5.3`).

### 8번섹션 ANALYZE1 룰 갱신 0건 정합 — 코드 외 변경 없음

본 사이클은 코드·테스트·DDL (조건부) 외 룰 파일·표준 사전·도메인 약어 변경 0건.

## 성공 기준 (검증 가능 형태)

| # | 기준 | 검증 명령·테스트·조회 |
|---|------|------------------|
| 1 | `FacilityOperatingStatusService.findFacilityOperatingStatus(facilityId)` 가 활성 시설의 On 펌프 이름·PWI 합산·전력원단위·max(acq_dtm) 을 단일 호출로 반환 (정상 시나리오 — PUMP 3대 중 2대 On, FRI 측정 정상) | 신규 단위 테스트 PASS: `FacilityOperatingStatusServiceTest.운영현황_정상_조회_시_On펌프이름_PWI합산_전력원단위_측정시간_반환` |
| 2 | `quality_cd = BAD` OPS 펌프가 On 합산에서 제외 (`ot-integration.md §3` OPS BAD 즉시 격상 정합) | 신규 단위 테스트 PASS: `BAD_OPS_펌프는_onPumpNms_에서_제외된다` |
| 3 | `quality_cd = UNCERTAIN` OPS 펌프가 On 합산에서 제외 (PLAN 결정 #1 보수적 처리) | 신규 단위 테스트 PASS: `UNCERTAIN_OPS_펌프는_onPumpNms_에서_제외된다` |
| 4 | `quality_cd = BAD/UNCERTAIN` PWI 가 합산에서 전액 제외 (PLAN 결정 #2 가중치 0.5 미적용) | 신규 단위 테스트 PASS: `UNCERTAIN_PWI_는_totalElpwrAmt_합산에서_전액_제외된다` |
| 5 | 분모 FRI = 0 / NULL / BAD / 부재 4 케이스에서 `elpwrUnitQty = null` 반환 (0 나누기 방어) | 신규 단위 테스트 PASS: 파라미터화 테스트 `elpwrUnitQty_분모_무효_시_null_반환` (FRI 0, NULL, BAD, 부재 4 케이스) |
| 6 | `equip_type_cd = 'PUMP'` 필터 강제 — VALVE/FLWMTR/PRSMTR 등 다른 인스트루먼트가 `onPumpNms` 에 포함되지 않음 | 신규 단위 테스트 PASS: `다른_자식_종류_인스트루먼트는_onPumpNms_에_포함되지_않는다` |
| 7 | RSV/POINT 시설 종류는 `UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` (HTTP 400) 거부 | 신규 단위 테스트 PASS: `RSV_시설은_UNSUPPORTED_FACILITY_TYPE_거부` + `POINT_시설은_UNSUPPORTED_FACILITY_TYPE_거부` |
| 8 | `measurementDtm` 이 사용된 태그(OPS+PWI+FRI) 중 `max(acq_dtm)` 으로 산출 | 신규 단위 테스트 PASS: `measurementDtm_은_사용된_태그들의_max_acq_dtm_으로_산출된다` |
| 9 | 빈 데이터 (OPS 태그 0건) → `onPumpNms = []`, `totalElpwrAmt = null` (또는 ZERO), `elpwrUnitQty = null`, `measurementDtm = null` 반환 | 신규 단위 테스트 PASS: `OPS_태그_부재_시_빈_응답_반환` |
| 10 | `GET /api/facility/{facilityId}/operating-status` Swagger UI 호출 시 `CommonResponseDto<FacilityOperatingStatusDto>` 응답 200 OK + 6필드 정상 노출 | `./gradlew.bat :api:bootRun` 후 `http://localhost:8080/swagger-ui.html` 에서 본 엔드포인트 호출 → 응답 구조 확인 |
| 11 | 빌드 + 전체 테스트 통과 | `./gradlew.bat build` 종료 코드 0 + BUILD SUCCESSFUL 출력 |
| 12 | `tag_m` 의 `(instrument_id, tag_se_cd)` 인덱스 존재 확인 (없으면 추가 + `docs/ddl/tag.sql` 동시 갱신) | `pg_indexes` 조회 결과 `idx_tag_m_instrument_id_tag_se_cd` 존재 + DDL 사본 양쪽 일치 |

## 가정 및 미해결 질문

ANALYZE1 의 6건 미해결을 본 PLAN 에서 결정으로 변환한다.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `UNCERTAIN` 품질 OPS 펌프를 On 합산 대상에 포함할지 | 결정 | **제외**. `ot-integration.md §3` OPS BAD 즉시 격상 정책의 보수적 확장. 가중치 0.5 정책은 이진 신호에 부적합 |
| `UNCERTAIN` 품질 PWI 측정값 합산 대상 포함 여부 (가중치 0.5 vs 전액 제외) | 결정 | **전액 제외**. 가중치 0.5 미적용 — 순시전력 합산 정확도 우선. frontend 표출 신뢰도 확보 |
| FRI 매핑 위치는 시설 직접 자식 `FLWMTR` 의 FRI 태그 | 결정 → 가정 유지 | 본 사이클 가정 유지 — `instrument_m.facility` 직접 자식 (`parent_facility_id` 재귀 미적용). 시설 종류별 복수 FLWMTR 운영 시 첫 GOOD 태그 사용. 운영자 보정은 향후 사이클 보완 |
| 본 카드 지원 시설 종류 (PWTF/DWT/PRSF) | 결정 | **PWTF · DWT · PRSF 3종** 허용. RSV (저수조, 펌프 미보유) · POINT (관로 분기점) 거부 — `UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` HTTP 400 |
| `tag_m` 의 `(instrument_id, tag_se_cd)` 복합 인덱스 존재 여부 | 결정 | **TASK Phase 1 에서 `pg_indexes` 확인 후 미존재 시 `CREATE INDEX CONCURRENTLY idx_tag_m_instrument_id_tag_se_cd ON tag_m (instrument_id, tag_se_cd) WHERE use_yn = 'Y'` 추가** + `docs/ddl/tag.sql` 동시 갱신 |
| 전력원단위 단위 표기 (kWh/m³) Swagger 명세 표기 | 결정 | **`@Schema(description="전력원단위 (kWh/m³)")`** — kW / (m³/h) = kW·h/m³ = kWh/m³ 차원 변환 결과. BigDecimal scale=4, `RoundingMode.HALF_UP` |
| `totalElpwrAmt` 빈 데이터 시 `null` vs `BigDecimal.ZERO` | 미해결 | TASK 단계 결정 — On 펌프 0대 시 `BigDecimal.ZERO` 권장 (합산 자연 결과). On 펌프 존재하나 모든 PWI 가 BAD 시 `null` 권장 (의미 구분 — 합산 불가능) |

## 제외 사항

- 12번 섹션 (활성화 시설 선택 UI 로직) — frontend 책임.
- AI 운전 모드 / 인터록 / 알람 4단계 / 제어 명령 — 본 사이클 미접촉 (ANALYZE1 §4영역 모두 비해당).
- SSE 실시간 푸시 — frontend polling 채택.
- 송수펌프제어분석-3번섹션 `FacilityStateDto`·`FacilityStateService` 의 기존 코드 변경 — 별도 응답 구조 + SRP 분리.
- 표준 사전·룰 파일 변경 — 안건 1 결론 0건.
- 신규 도메인 패키지 (`com.mo.swtp.operating`) 신설 — `coding-discipline.md §2` "요청되지 않은 추상화 계층 금지" 정합, `com.mo.swtp.facility.*` 확장으로 충분.
- SensorPoint·재귀 자식 시설 FRI 매핑 — 본 사이클 미적용 (가정 3 유지).

## 예상 산출물

- [태스크](../../../tasks/20260521/운전현황분석-4번섹션/TASK1.md) — `/dev:task` 단계에서 작성. Phase 5개 예상 (인덱스 확인·DTO·Service·Controller·테스트).

---

## 참조 문서

- ANALYZE1: `swtp/backend/docs/analyze/20260521/운전현황분석-4번섹션/ANALYZE1.md` (`status: approved`)
- 도메인 룰 인용: `swtp/backend/.claude/rules/ot-integration.md §3` (센서 품질 관리 — OPS BAD 즉시 격상)
- 다형성 룰 인용: `swtp/backend/.claude/rules/entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴 §도메인 룰` (`equip_type_cd` 필터 강제)
- DDL 정책 인용: `swtp/backend/.claude/rules/db/indexing-and-migration.md §1` (인덱스 컬럼 순서) · `§5` (SQL 관리 이중 정책)
- DTO 패턴 인용: `swtp/backend/.claude/rules/api-patterns.md §BaseAuditResponseDto 패턴` (실시간 통지성 미적용)
- 코딩 디시플린: `swtp/.claude/rules/coding-discipline.md §2` (단순성) · `§4.2` (성공 기준 검증 가능 형태)
- 참조 패턴: `swtp/backend/api/src/main/java/com/mo/swtp/facility/service/FacilityStateService.java` (4-SELECT 패턴 — 인용 근거로만)
