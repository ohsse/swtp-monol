---
status: approved
created: 2026-06-01
updated: 2026-06-01
---
# 운전현황분석-8번섹션 — 수요량/관압/수위 재귀 하위 시설 계측+예측 시계열 API

## 목적

운전현황 분석 화면 8번 섹션(`backend/image/운전현황분석.png`)의 backend 조회 API 신설. 12번 섹션에서 활성화된
시설(루트)을 기준으로 **'배수지(DWT)를 자식으로 둔' 재귀 하위 정수지(PWTF)·분기점(POINT)** 을 표출대상으로 도출하고,
프론트 **수요량/관압/수위 탭**(`dataType`) 별로 금일 00:00~현재 1분 단위 **계측(rawdata_1m_h) + 예측(predc_1m_h)**
시계열과 **포인트별 대비율(예측/계측×100)** 을 단일 엔드포인트로 응답한다.

| dataType | 탭 | 대상 시설 | 계측기 | 태그 | 시리즈 단위 |
|---|---|---|---|---|---|
| `DEMAND` | 수요량 | 표출대상(PWTF·POINT) | 유출 FLWMTR | FRI (유출유량) | 표출대상 시설당 1 |
| `PRESSURE` | 관압 | 표출대상(PWTF·POINT) | 유출 FLWMTR | PRI (유출수압) | 표출대상 시설당 1 |
| `LEVEL` | 수위 | 표출대상의 자식 배수지(DWT) | LVMTR | LEI (수위) | **수위계(LVMTR)당 1** |

## 배경

- 선행 산출물: [ANALYZE1](../../../analyze/20260601/운전현황분석-8번섹션/ANALYZE1.md) (status: approved). 표준 사전 갱신 0건·DDL 0건·도메인 4영역 비해당.
- 기존 4·5·7·9·10번 섹션은 단일 시설(`{facilityId}` 직속) API. 8번은 처음으로 **루트 1개 → 다수 재귀 하위 fan-out** 멀티 시설 시계열. 기존 섹션 무수정 **병렬 신규 추가**.
- **사용자 확정 결정 (2026-06-01)**:
  - Q1 표출대상 = 재귀 하위(루트 inclusive) 중 **'DWT 자식 보유' PWTF/POINT**. **루트 포함** 확정 (2026-06-01 추가 결정) — `dev DB` 2단(루트=정수조 PWTF, DWT 자식 보유) 및 고령 3단 모두 동작. 사업장 등 DWT 미보유 루트는 'DWT 자식 보유' 필터로 자동 제외.
  - Q2 단일 엔드포인트 + `dataType=DEMAND|PRESSURE|LEVEL`.
  - Q3 대비율 = 포인트별 `예측/계측×100`, 소수 1자리.
  - Q4 시계열 범위조회 리포지토리 = 공유 재사용.
  - **Q5 (승인 시 명시, 2026-06-01)**: 유량(수요량)·압력(관압) **모두 유량계(FLWMTR)의 유출(OUTPUT)값** 기준. 별도 압력계(PRSMTR) 미사용 — 아래 io_cd Round 2 정정을 사용자가 명시적으로 확정.
- **⚠️ 안건 4 io_cd Round 2 도메인 정정 (PLAN 단계, wtp-domain-expert 재확인 + 사용자 Q5 확정)**: ANALYZE1 안건 4 Round 1 결론(`io_cd 미필터 + 첫 FLWMTR`)을 **번복**한다. 8번은 사용자가 "송수(유출)"를 명시 강조했고 정수지(PWTF)가 유입계+유출계를 모두 보유할 수 있어 "첫 FLWMTR"이 유입계를 오선택할 도메인 위반 위험이 있다. **유출 FLWMTR = `Tag.io_cd ∈ {OUTPUT, BIDIR}` 태그 보유 계측기** (4번 섹션 `DwtStateService` 확립 패턴)로 전환한다. 상세 결론은 §부록.
- **⚠️ 리포지토리 재사용 패턴 긴장 (투명성 명기)**: 기존 5·7·9·10번은 시그니처 동일에도 각자 전용 Repository 를 신설(`RawDataOutflowRepository`·`TagPredcOutflowRepository`·`TagPredcRangeRepository` 모두 "사이클 간 자산 자동 원용 금지" Javadoc 명시)하는 강한 관례가 있다. 본 사이클은 사용자 Q4 결정으로 이 관례의 **의도적 예외**로 7번 섹션 범위조회 쌍을 재사용한다 (§가정 A-REPO).

## 범위

### 포함
- 단일 엔드포인트 `GET /api/facility/{facilityId}/operating-status/downstream-time-series?dataType=DEMAND|PRESSURE|LEVEL` (FacilityController 메서드 1개 추가).
- 재귀 하위 BFS 도출 컴포넌트(`FacilityDownstreamTreeResolver`) + dataType별 다형성 응답 DTO + Service.
- 계측 GOOD-only + 예측 직접 + 포인트별 대비율.
- Mockito 단위 테스트 (Resolver + Service).

### 제외 (제외 사항 절 참조)
- DDL·인덱스·엔티티·표준사전 변경 (0건).
- dev DB 실측 검증 (POINT/LVMTR/LEI/3단 데이터 부재 — Mockito 로만 검증).
- frontend SPEC 전파(`/dev:spec`) 는 commit 후 선택 단계.

## 구현 방향

### 1. 엔드포인트 (FacilityController)
- `@GetMapping("/{facilityId}/operating-status/downstream-time-series")`, `@Tag("06. 시설물 관리")`, `@ApiResponses` 200/400/401/403/404/500.
- `@RequestParam FacilityDownstreamDataType dataType` (`@Schema(implementation = FacilityDownstreamDataType.class)` — 5번 섹션 `compareType` 선례).
- 응답 `ResponseEntity<CommonResponseDto<FacilityDownstreamTimeSeriesDto>>` (추상 부모) + `@ApiResponse(content = @Content(schema = @Schema(oneOf = {FacilityDownstreamMeasureDto.class, FacilityDownstreamLevelDto.class}, discriminatorProperty = "dataType")))` (findAllFacilities oneOf 선례).
- `getResponseEntity(service.findDownstreamTimeSeries(facilityId, dataType))`.

### 2. 재귀 하위 도출 — `FacilityDownstreamTreeResolver` (@Component, 앱 레벨 BFS)
- 신규 Repository 메서드 1개: `FacilityRepository.findByParentFacilityIdInAndUseYn(List<String> parentIds, YnType useYn)` (Spring Data 파생 쿼리, 활성 자식 전수 — 종류 무필터). 기존 메서드 무수정. `idx_facility_m_parent_type_yn (parent_facility_id, ...)` 선행 컬럼 활용.
- BFS 알고리즘 (깊이 카운터 + visited-set 순환 방어):
  1. 루트 `facilityRepository.findById(facilityId)` → 미존재·`use_yn≠Y` → `FACILITY_NOT_FOUND`. 루트 종류 제한 없음.
  2. `subtree = {루트}`, `frontier = [루트id]`, `depth = 0`.
  3. `depth < MAX_DEPTH`(상수, 예: 10) 동안: `children = findByParentFacilityIdInAndUseYn(frontier, Y)` → visited 미포함만 subtree·frontier 갱신. 중간 종류(RSV 등) 도 통과시켜 더 깊은 PWTF/POINT 누락 방지.
  4. 분류(인메모리): `dwtsByParent = subtree 의 DWT 를 parent_facility_id 로 그룹`. `displayTargets = subtree 중 type ∈ {PWTF, POINT} AND dwtsByParent.containsKey(자신 id)` (= 'DWT 자식 보유' 필터, 루트 inclusive). `levelSourcesByTarget = displayTargets 각각의 직속 자식 DWT 목록`.
- 반환 record `DownstreamTopology(List<Facility> displayTargets, Map<String,List<Facility>> dwtsByTargetId)`. 'DWT 자식 보유' 판정에 **추가 쿼리 불필요** (BFS 적재분 인메모리 분류).
- 표출대상 0건 → 빈 `displayTargets` (404 아님 — 빈 series 응답).

### 3. dataType 분기 — `FacilityDownstreamTimeSeriesService`
공통: `startDtm = LocalDate.now().atStartOfDay()`, `endDtm = LocalDateTime.now()`. 계측·예측 동일 구간.

**DEMAND / PRESSURE** (표출대상 시설당 1 시리즈):
1. `displayTargetIds` → `instrumentRepository.findByFacilityIdInAndEquipType(ids, [FLWMTR])` (dispOrd ASC) → 시설별 그룹.
2. `tagRepository.findByInstrumentInstrumentIdInAndUseYn(flwmtrIds, Y)` → 계측기별 그룹 (Tag 는 `getIoCd()`·`getTagSeCd()` 보유).
3. 시설별 **유출 FLWMTR 선택**: 산하 태그 io_cd ∈ {OUTPUT, BIDIR} 1건이라도 보유한 FLWMTR (4번 `filterByIoCode` 패턴 재구현). dispOrd ASC 첫 매치 + `multipleOutletFlwmtrDetected`(2건+) 플래그.
4. 선택 FLWMTR 의 `dataType==DEMAND ? FRI : PRI` 태그 → tagSrlNo.
5. 전 시설 tagSrlNo 수집 → `rawDataOutflowRepository.findByTagSrlNosAndDtmRange`(계측) + `tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange`(예측) **각 1회**.
6. 시설별 시리즈 빌드 (§4 병합).

**LEVEL** (수위계 LVMTR당 1 시리즈):
1. `dwtIds = dwtsByTargetId.values()` → `findByFacilityIdInAndEquipType(dwtIds, [LVMTR])` → DWT별 그룹.
2. LVMTR 산하 LEI 태그 (io_cd 무필터 — 수위는 입출력 개념 없음).
3. **수위계당 1 시리즈** (LEI 태그 1개당), `parentFacilityId/Nm` = DWT 의 직속 parent 표출대상으로 그룹.
4. 전 LEI tagSrlNo 수집 → 계측·예측 각 1회 → LVMTR별 시리즈 빌드.

쿼리 횟수: BFS(깊이 ~1–3) + Instrument 1 + Tag 1 + Raw 1 + Predc 1 ≈ 5–7회. N+1 안전.

### 4. 시리즈 병합 + 대비율 (7번 헬퍼 재구현, 7번 무수정)
- 시리즈별 `TreeMap<LocalDateTime, Parts>`: `fillActualSlots`(actual, GOOD-only) → `overlayPredcSlots`(predc) → `toPoints`(양쪽 null 슬롯 생략).
- `effectiveVal`(corrVal 우선) · `effectiveValGood`(GOOD 만) 본 서비스 재구현 (FRI/PRI/LEI 동형, OPS tri-state 불요 — 연속값).
- 신규 `computeRatio(BigDecimal actual, BigDecimal predc)`: actual null·0 또는 predc null → null; 그 외 `predc.divide(actual, ...).multiply(100).setScale(1, HALF_UP)` (10줄 이내 private).
- `DownstreamPoint = {dtm, actualVal, predcVal, ratio}`.

### 5. 응답 DTO 다형성 (dataType별 전용 — optional-field 스멜 회피)
- 추상 부모 `FacilityDownstreamTimeSeriesDto` { `facilityId`(루트), `facilityNm`, `dataType` } + `@JsonTypeInfo(use=NAME, include=EXISTING_PROPERTY, property="dataType", visible=true)` + `@JsonSubTypes({@Type(value=FacilityDownstreamMeasureDto.class, names={"DEMAND","PRESSURE"}), @Type(value=FacilityDownstreamLevelDto.class, name="LEVEL")})` + `@Schema(oneOf=..., discriminatorProperty="dataType")`.
- `FacilityDownstreamMeasureDto`(DEMAND·PRESSURE) { `List<MeasureSeries> series` }, `MeasureSeries` { facilityId(PWTF/POINT), facilityNm, facilityTypeCd, instrumentId(유출 FLWMTR), instrumentNm, multipleOutletFlwmtrDetected, `List<DownstreamPoint> points` }.
- `FacilityDownstreamLevelDto`(LEVEL) { `List<LevelSeries> series` }, `LevelSeries` { facilityId(DWT), facilityNm, facilityTypeCd, instrumentId(LVMTR), instrumentNm, parentFacilityId, parentFacilityNm, `List<DownstreamPoint> points` }.
- 공유 `DownstreamPoint` { dtm(`@JsonFormat "yyyy-MM-dd HH:mm:ss"`), actualVal, predcVal, ratio }.
- `@ArraySchema(schema=@Schema(implementation=...))` 의무 (series·points). `facilityTypeCd` → `@Schema(implementation=FacilityType.class)`. `BaseAuditResponseDto` 미상속(조회 응답). 정적 팩토리 `of(...)`. **'Section8' 명명 금지**.
- DEMAND·PRESSURE 가 동일 `MeasureSeries` shape 를 공유(단위만 유량 m³/h vs 수압 kgf/cm²) — `@Schema` description 으로 dataType 의미 구분.

## 도메인 모델

신규 엔티티·테이블·컬럼 **0건**. 신규 Java 자산만 추가.

| 자산 | 위치(모듈) | 역할 |
|------|-----------|------|
| `FacilityDownstreamDataType` (enum DEMAND/PRESSURE/LEVEL) | common `com.mo.swtp.facility.domain.enumtype` | dataType 파라미터 (5번 `FacilityOperatingStatusCompareType` 위치 선례) |
| `FacilityDownstreamTimeSeriesDto`(abstract)+`...MeasureDto`+`...LevelDto`+`DownstreamPoint` | api `com.mo.swtp.facility.dto` | 다형성 응답 DTO |
| `FacilityDownstreamTimeSeriesService` | api `com.mo.swtp.facility.service` | dataType 분기·시계열 조회·병합·대비율 |
| `FacilityDownstreamTreeResolver` | api `com.mo.swtp.facility.service` | 재귀 하위 BFS 도출 + 'DWT 자식 보유' 분류 (@Component, 추상화 2단) |
| `FacilityRepository.findByParentFacilityIdInAndUseYn` | api `com.mo.swtp.facility.repository` | BFS 1레벨 확장 (파생 쿼리 추가, 기존 무수정) |

**재사용(무수정)**: `RawDataOutflowRepository`·`TagPredcOutflowRepository`·`InstrumentCustomRepository.findByFacilityIdInAndEquipType`·`TagRepository.findByInstrumentInstrumentIdInAndUseYn`·`RawDataOutflowDto`·`TagPredcOutflowDto`·`QualityCode`·`IoCode`·`EquipType`·`TagMeasurementType`·`FacilityType`·`FacilityErrorCode`.

## DB 설계 변경

**없음.** 신규 DDL·인덱스·마이그레이션 0건. BFS 는 기존 `idx_facility_m_parent_type_yn (parent_facility_id, facility_type_cd, use_yn)`(V9_2) 선행 컬럼 활용. 시계열 조회는 기존 `idx_rawdata_1m_h_tag_time`·`idx_predc_1m_h_tag_time` + 월 RANGE 파티션 프루닝 활용 (재사용 Repository).

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| `FacilityDownstreamTreeResolver` 단위 테스트 GREEN — 다단계 트리(루트→PWTF→DWT / 루트→중간RSV→POINT→DWT) 분류 / 루트 inclusive 표출대상 / 'DWT 자식 보유' 필터(DWT 미보유 PWTF 제외) / DWT parent 비-PWTF/POINT 제외 / 순환·MAX_DEPTH 방어 | ./gradlew.bat :api:test --tests FacilityDownstreamTreeResolverTest PASS |
| `FacilityDownstreamTimeSeriesService` 단위 테스트 GREEN — DEMAND 표출대상 N→N 시리즈(유출 FLWMTR FRI 계측+예측+ratio) / PRESSURE PRI / LEVEL 수위계 M→M 시리즈(LEI+parent 그룹핑) | ./gradlew.bat :api:test --tests FacilityDownstreamTimeSeriesServiceTest PASS |
| io_cd 정합성 — 유입(INPUT)계만 보유 시설은 유출 시리즈 제외 / OUTPUT·BIDIR 계측기만 선택 / 다중 유출계 시 dispOrd 첫 매치 + multipleOutletFlwmtrDetected=true | 위 ServiceTest 케이스 PASS (유입 FLWMTR 미선택 verify) |
| 대비율 경계 — 계측 0·null·BAD 또는 예측 null → ratio=null / 정상 슬롯 소수1자리 HALF_UP | 위 ServiceTest 케이스 PASS |
| 예외·빈 응답 — 루트 미존재·비활성 404 / 표출대상 0건 빈 series(200) / 태그 부재 표출대상 빈 points 포함 | 위 ServiceTest 케이스 PASS |
| 전체 빌드 | ./gradlew.bat :common:build 후 ./gradlew.bat build 출력 BUILD SUCCESSFUL |
| Swagger 노출 | :api:bootRun 후 Swagger UI 에서 엔드포인트 + dataType discriminator oneOf(Measure/Level) + 계측·예측·ratio null 의미 한국어 @Schema 확인 |
| 메서드 50줄 이내 | 신규 Service/Resolver 메서드별 본문 50줄 이내(빈줄·주석 제외) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| **A-ROOT** 표출대상에 루트 포함 (루트 inclusive 서브트리 + 'DWT 자식 보유' 필터) | 결정 | 사용자 2026-06-01 확정. dev DB 2단·고령 3단 모두 동작 |
| **A-IO1** PWTF 는 유입(INPUT)·유출(OUTPUT) FLWMTR 를 모두 보유할 수 있다 → io_cd ∈ {OUTPUT,BIDIR} 필터 적용 (첫 FLWMTR 번복) | 결정 | wtp-domain-expert Round 2 (§부록) **+ 사용자 Q5 확정 (2026-06-01 승인 시 '유량·압력=유량계 유출값 기준' 명시)**. 4번 섹션 패턴 |
| **A-IO2** POINT 의 FLWMTR io_cd 는 OUTPUT 또는 BIDIR 로 등록된다 → `IN (OUTPUT, BIDIR)` 통일 필터 | 가정 | 현장 태그 등록 정책 의존. BIDIR 포함으로 안전 |
| **A-IO3** 시설당 유출 FLWMTR 2건+ 시 dispOrd ASC 첫 매치 사용 + multipleOutletFlwmtrDetected 플래그 | 결정 | 4번 섹션 선례 |
| **A-PRI** 유출 FLWMTR 가 FRI·PRI 태그를 모두 보유 정상 (3·6번 FLWMTR PRI 선례) | 결정 | wtp-domain-expert 확인 |
| **A-REPO** 시계열 범위조회 = 7번 섹션 쌍(`RawDataOutflowRepository`+`TagPredcOutflowRepository`) 재사용 | 가정 | 사용자 Q4 재사용 결정. **단 5·7·9·10번 per-section 격리 관례의 의도적 예외** — 전용 신설 대안 존재. PLAN 검토 시 재확인 가능 |
| **A-DTO** 응답 = dataType별 전용 DTO + 단일 엔드포인트 oneOf discriminator(dataType, 2 subtype: DEMAND·PRESSURE→Measure, LEVEL→Level) | 가정 | 백엔드 블로커 해소(optional-field 스멜 회피). 사용자 Q2 단일 엔드포인트 유지. wrapper `{dataType,data}` 대안 존재 |
| **A-DWT** DWT→표출대상 = 직속 parent(`parent_facility_id`). parent 가 PWTF/POINT 아니면(RSV 등) 수위 그룹핑 제외 | 결정 | wtp-domain-expert 권고 |
| **A-LEI** LEI 계측 GOOD-only(`effectiveValGood`), null=측정불명 @Schema | 결정 | FRI/PRI 동형 |
| **A-DEPTH** BFS MAX_DEPTH 상수(예: 10) + visited-set. 실제 깊이 3단 초과 확인 시 재검토 | 가정 | facility_m 마스터(소규모). 순환 안전 |
| **A-SLA** 표출대상 N + DWT/LVMTR M 시설 수 상한(예: N+M≤10) 가정, 태그 IN 20+ 시 EXPLAIN 200ms 초과면 분할/커서 | 가정 | DBA 권고. 본 사이클 단일 조회 |
| **A-EMPTY** 빈 시리즈(태그 부재 표출대상/LVMTR 부재 DWT) 빈 points 포함, 표출대상 0건 빈 List(200) | 결정 | 7번 정합 + 프론트 렌더 순서 |
| **A-FIX** 다중 태그 동기 — 동일 1분 FRI/PRI/LEI 가 동일 acq_dtm/predc_dtm 슬롯 존재 | 가정 | 7번 동형. 픽스처 동일 시각 검증 |

분류값: 가정 / 미해결 → 결정

## 제외 사항
- DDL·인덱스·엔티티·표준사전 변경 (0건 — 조회 전용).
- dev DB 라이브 검증 (POINT/LVMTR/LEI/3단 데이터 부재). Mockito 단위 테스트로 1차 검증, 추후 픽스처 투입 시 Swagger 라이브 검증(선택).
- LVMTR io_cd 필터 (수위는 입출력 개념 없음 — 미적용).
- 도메인 4영역(알람·인터록·운전모드·이력) 무접촉 (전부 비해당 — ANALYZE1 §도메인 룰 4영역 점검).
- frontend SPEC 전파 (`/dev:spec` commit 후 선택 단계).

## 예상 산출물
- [태스크](../../../tasks/20260601/운전현황분석-8번섹션/TASK1.md)

## 부록: 도메인/DB 검토 결과
- **wtp-domain-expert (안건 4 io_cd Round 2, 2026-06-01)**: 블로커성 정정 1건 반영 완료.
  - 결론: 7번 "첫 FLWMTR by dispOrd" 를 8번에 적용하면 도메인 위반. **io_cd ∈ {OUTPUT, BIDIR} 필터로 전환** 권고 (전환 반영 → §구현 방향 3, §가정 A-IO1~3).
  - PWTF 는 원수 유입계 + 송수 유출계를 물리적으로 모두 보유(정수장 표준 토폴로지). 유출 FLWMTR 의 FRI·PRI 동시 보유 정상(3·6번 선례). POINT 는 OUTPUT/BIDIR 가정. 다중 유출계는 dispOrd 첫 매치 + 플래그.
- **wtp-dba-reviewer**: 본 PLAN `## DB 설계 변경` = 없음(신규 DDL·인덱스 0건, 기존 인덱스 재사용) → DB 검토 게이트 생략. ANALYZE1 안건 2 DBA 결론(BFS·재사용·SLA) 이미 반영.
