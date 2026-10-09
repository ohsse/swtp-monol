---
status: approved
created: 2026-06-02
updated: 2026-06-02
---
# 송수펌프 가동이력 2번섹션 — 펌프 상태 카드 API

## 목적

"송수펌프 가동이력" 대시보드 **2번 섹션** — 전체 활성 송수펌프의 현재 상태를 카드 형태로 표출하는 **읽기 전용 API** 를 신규 구현한다. 각 카드는 펌프명 · 정격양정(m) · 정격유량(m³/h) · **가동률(%)** 을 노출하며, 가동률은 구동 방식별로 다르게 산정한다.

## 배경

- ANALYZE1 ([승인됨](../../../analyze/20260602/송수펌프가동이력_2번섹션/ANALYZE1.md)) 5인 회의 결론 적용.
- 시스템에 펌프 제원(`ratedHead`·`ratedFlwrt`·`driveType`)·OPS/FQI 태그·최신값 조회 인프라는 모두 존재하나, **가동률(%) 산정 로직과 펌프 상태 카드 엔드포인트가 부재**.
- 신규 엔티티·DB 스키마·인덱스·마이그레이션 0건. 기존 enum/패턴/인덱스 재사용.

## 범위

| 구분 | 내용 |
|------|------|
| 신규 | `PumpOperationRateController` · `PumpOperationRateService` · `PumpOperationRateDto` (모두 `com.mo.swtp.instrument` 하위) |
| 신규 | `InstrumentRepository` 활성 PUMP 전용 파생 쿼리 1건 |
| 신규 | `PumpOperationRateServiceTest` (Mockito 단위 테스트 7 케이스) |
| 변경 0 | 엔티티 · DB 스키마 · 마이그레이션 · `docs/ddl` · 기존 컨트롤러/서비스 |
| 룰 갱신 완료 | `swtp/.claude/rules/dict/standard-words.md` 에 `rate`(율/비율) 등록 (ANALYZE approved 전제조건 — 완료) |

## 구현 방향

### 1. 응답 DTO — `com.mo.swtp.instrument.dto.PumpOperationRateDto`

- **읽기 전용** — `BaseAuditResponseDto` 미상속 (api-patterns.md §적용 범위: 실시간 통지·요약 응답 미적용 정합). `private` 생성자 + 정적 팩토리 `of(...)`.
- 필드:

  | 필드 | 타입 | nullable | Swagger |
  |------|------|----------|---------|
  | `pumpId` | String | N | `@Schema` |
  | `pumpNm` | String | N | `@Schema` |
  | `driveType` | `PumpDriveType` | N | `@Schema(implementation = PumpDriveType.class)` |
  | `ratedHead` | BigDecimal | N | `@Schema` (m) |
  | `ratedFlwrt` | BigDecimal | N | `@Schema` (m³/h) |
  | `oprtngRate` | BigDecimal | **Y** | `@Schema` (%, 결측·품질불량 시 null) |
  | `qualityCd` | `QualityCode` | **Y** | `@Schema(implementation = QualityCode.class)` |
  | `acqDtm` | LocalDateTime | **Y** | `@Schema` + `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` |

- `qualityCd`·`acqDtm` 는 **판정 기준 태그**(정격=OPS, 인버터=FQI)의 품질·수집시각을 **항상 동봉** (rate 가 non-null 이어도 신선도 표시 목적). 판정 태그 자체 부재 시 둘 다 null. → ANALYZE "null 반환 시 동봉" 의도의 상위호환 (항상 동봉이 더 정보적이며 분기 단순). 가정 섹션 명기.

### 2. 서비스 — `com.mo.swtp.instrument.service.PumpOperationRateService`

`@Service @RequiredArgsConstructor @Transactional(readOnly = true)`. **`FacilityOperatingStatusService` 클론 금지** (ANALYZE 안건 3 블로커) — 동형 4-SELECT 패턴을 본 패키지에 독립 재구현, 공유 부모 클래스/상속 없음.

4-SELECT 파이프라인:
1. **활성 PUMP 조회** — `instrumentRepository.findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(EquipType.PUMP, YnType.Y)` → PUMP 원소를 `Pump` 로 캐스팅 (`filterPumps` 동형).
2. **태그 배치 조회** — `tagRepository.findByInstrumentInstrumentIdInAndUseYn(pumpIds, YnType.Y)` 후 `OPS`·`FQI` 만 필터, 인스트루먼트별 그룹화.
3. **최신값 배치 조회** — `rawDataRepository.findLatestByTagSrlNos(tagSrlNos)` (DISTINCT ON + 1시간 윈도) → `Map<tagSrlNo, RawDataLatestDto>`.
4. **인메모리 산정·매핑** — 펌프별 OPS/FQI 최신값을 `pickLatest` 동형으로 추출, 가동률 산정 후 DTO 매핑. 정렬은 1단계 쿼리에서 보장된 순서 유지.

산정 헬퍼 `private` 분리 (메서드 ≤50줄, 호출 깊이 ≤3 — coding-discipline.md §2.1):
- `effectiveVal(RawDataLatestDto)` = `corrVal != null ? corrVal : rawVal` (FQI 전용 — Hold Last Value 허용 측정유형).
- `computeOprtngRate(Pump, RawDataLatestDto ops, RawDataLatestDto fqi)` → nullable `BigDecimal`:
  - **RATED_DRIVE**: OPS `qualityCd == GOOD` 아니거나 OPS==null → `null`. **OPS 는 `rawVal` 단독 사용** (Hold Last Value 금지 — ot-integration.md §3, `corr_val` 미사용). `rawVal == 1.0` → `100`, `== 0.0` → `0`, 그 외 값 → `null`.
  - **INVERTER_DRIVE**: FQI `qualityCd == GOOD` 아니거나 FQI==null → `null`. GOOD → `effectiveVal(fqi)` 를 그대로 % 로 (정격주파수 정규화 미적용).

### 3. 컨트롤러 — `com.mo.swtp.instrument.web.PumpOperationRateController`

- `@RestController @RequiredArgsConstructor`, `CommonController` 상속.
- `@Tag(name = "11. 송수펌프 가동이력")` — 기존 00·01·01-1·02·02-1·06·07·08·10 미사용 번호 11 채택 (확인 완료).
- `@GetMapping("/api/instrument/pump-operation-rate")` → `ResponseEntity<CommonResponseDto<List<PumpOperationRateDto>>>`, `getResponseEntity(...)` 래핑.
  - **경로 충돌 없음 (확인 완료)**: 기존 `InstrumentController` 의 `@GetMapping("/{instrumentId}")` 와 같은 base 이나, 리터럴 세그먼트 `pump-operation-rate` 가 path-variable `{instrumentId}` 보다 Spring PathPattern 특이도가 높아 우선 매칭 (ambiguous-mapping 예외 미발생).
- `@Operation`(데이터 소스·구동방식별 산정 규칙·null 의미 명시) + `@ApiResponses`(200·400·401·403·500). 인증 필요.

### 4. 리포지토리 — `InstrumentRepository` (api 모듈)

신규 파생 쿼리 1건 추가 (엔티티·DB 무변경):

```java
@BatchSize(size = 100)
List<Instrument> findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(EquipType equipType, YnType useYn);
```

- 기존 `findByEquipType(EquipType)` 가 discriminator(`equip_type_cd`) 파생 쿼리 동작을 입증 — 동형 확장.
- `use_yn = Y` 필터 + `disp_ord ASC, instrument_nm ASC` 정렬. 기존 인덱스로 충분 (DBA 통과 — 마스터 풀스캔 허용, DDL 0건).

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| `PumpOperationRateService` 단위 테스트 7 케이스 GREEN | `./gradlew.bat :api:test` 실행 → PumpOperationRateServiceTest 7건 PASS (①정격 OPS GOOD raw=1.0→100 ②정격 OPS GOOD raw=0.0→0 ③정격 OPS BAD/UNCERTAIN→null+quality 동봉 ④정격 OPS 태그없음→null,quality null ⑤인버터 FQI GOOD→effectiveVal(%) ⑥인버터 FQI BAD/null→null+quality 동봉 ⑦인버터 FQI 태그없음→null) |
| 엔드포인트가 활성 PUMP 전체를 dispOrd 순 반환 | 로컬 구동 후 Swagger 에서 GET /api/instrument/pump-operation-rate 호출 → CommonResponseDto<List<PumpOperationRateDto>> 형태 + dispOrd 정렬 + null 동봉 확인 |
| 전체 빌드 무결성 | ./gradlew.bat build → BUILD SUCCESSFUL (QClass 재생성 포함) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| OPS 값 소스 = `rawVal` 단독, FQI 값 소스 = `effectiveVal(corrVal ?? rawVal)` | 결정 | ot-integration.md §3 — OPS Hold Last Value 금지. ANALYZE 안건 2 블로커 해소 정책 확정 적용 |
| `qualityCd`·`acqDtm` 를 판정 태그 기준으로 **항상 동봉** (rate non-null 시에도) | 결정 | ANALYZE "null 시 동봉" 의 상위호환 — 분기 단순 + 신선도 표시. 판정 태그 부재 시 둘 다 null |
| `@Tag` = "11. 송수펌프 가동이력" | 결정 | 기존 번호 미충돌 확인 완료 (00·01·01-1·02·02-1·06·07·08·10) |
| 엔드포인트 = `GET /api/instrument/pump-operation-rate` | 결정 | PathPattern 특이도로 `{instrumentId}` 와 충돌 없음 확인 완료 |
| 정렬 = `disp_ord ASC`, 동률 시 `instrument_nm ASC` | 결정 | 파생 쿼리 `OrderByDispOrdAscInstrumentNmAsc` 로 보장 |
| RATED_DRIVE 펌프의 OPS `rawVal` 이 0/1 외 값일 때 | 결정 | `null` 반환 (정의되지 않은 가동상태 — 근거 없는 값 표출 회피) |

분류값: 가정 / 미해결 → 결정

## 제외 사항

- 1번섹션 등 다른 섹션 · 기간 시계열 · 펌프조합 엑셀 다운로드 (요구사항 historical 범위).
- 엔티티 · DB 스키마 변경, 신규 측정유형, AI/인터록/제어 로직.
- 송수/전송 펌프 카테고리 구분 (현재 도메인 부재 — 전체 PUMP 대상).
- ADMIN 권한 분리 (현재 인증 사용자 전체 허용 — 기존 컨벤션 유지).

## 예상 산출물

- [태스크](../../../tasks/20260602/송수펌프가동이력_2번섹션/TASK1.md)
