---
status: approved
created: 2026-06-01
updated: 2026-06-01
---
# 운전현황분석 7번 섹션 — 시설 단위 유출(송수) 유량·압력 + 펌프 가동상태 계측/예측 시계열 API

## 목적

운전현황분석 화면(`backend/image/운전현황분석.png`) 7번 섹션 카드의 backend 조회 API 를 신설한다. 12번 섹션에서 활성화한 시설(`facilityId`)의 **금일 00:00 ~ 현재시간** 1분 단위 시계열을 단일 응답으로 반환한다.

- **라인** = 활성 시설 유출(송수) **유량(FRI)** · **압력(PRI)** — 계측 + 예측
- **막대** = 시설 소속 **펌프별 가동상태(OPS)** on/off 타임라인 — 계측 + 예측

## 배경

- 같은 화면의 4(현황)·5(현황 비교 시계열)·9(예측 현황)·10(계측+예측 전력원단위 시계열) 섹션과 **다른 관심사**(전력원단위가 아닌 유량·압력·펌프 가동)이므로 기존 섹션을 폐기/재설계하지 않고 **병렬 신규 추가**한다 (사용자 메모리 "공존 섹션은 폐기 아님" 정합).
- 데이터 정합성 검증 완료 — 송수펌프제어 7번 `FacilityPredictionService` 가 `predc_1m_h` 에서 예측 OPS/FRI/PRI 를 조회 중 → 계측(`rawdata_1m_h`)·예측(`predc_1m_h`) 양쪽에 OPS/FRI/PRI 존재.
- 5인 회의 결과: [ANALYZE1](../../../analyze/20260601/운전현황분석-7번섹션/ANALYZE1.md) — 블로커 0건, 표준 사전 갱신 0건, 도메인 4영역 전부 비해당, OPS 3-state 채택.

## 범위

- **영향 모듈**: `api` 단일 (신규 Service·DTO·Repository + Controller 메서드 추가). `common`·`scheduler` 무영향.
- **DB**: 변경 0건 (조회 전용 — 신규 테이블·컬럼·인덱스·마이그레이션 0건).
- **표준 사전**: 변경 0건 (`flwrt`·`prsr` 기존 재사용, 신규 명명은 Java 클래스·변수 영역).
- **기존 5·10번 Repository 무수정** (사용자 Q3 — 사이클 독립성).

## 구현 방향

### 1. 엔드포인트 (결정)

```
GET /api/facility/{facilityId}/operating-status/outflow-time-series
```

- `FacilityController` 에 메서드 추가 (`@Tag("06. 시설물 관리")`, 기존 `@RequestMapping("/api/facility")` 단수 prefix 유지).
- `operating-status/{action}` 그룹핑은 4(`/operating-status`)·9(`/operating-status/prediction`)·5(`/operating-status/timeseries`)·10(`/operating-status/daily-time-series`) 일관 규칙 → 7번 `operating-status/outflow-time-series` 정합.
- `facilityId` 는 `@PathVariable` (12번 활성화 = frontend 선택, 서버 상태 없음).
- 응답: `ResponseEntity<CommonResponseDto<FacilityOutflowTimeSeriesDto>>` (`getResponseEntity(...)` 래핑).
- Swagger `@Operation` + `@ApiResponses`(200/400/401/403/404/500) — 4·5·9·10번 description 패턴 정합, OPS 3-state·라인 결측 의미·400(RSV·POINT)·404(미존재·비활성) 명시.

### 2. 응답 DTO (2-시리즈 분리 — 결정)

신규 `api/src/main/java/com/mo/swtp/facility/dto/FacilityOutflowTimeSeriesDto.java`:

```
FacilityOutflowTimeSeriesDto {
  String facilityId;
  String facilityNm;
  List<LinePoint> linePoints;     // @ArraySchema(schema=@Schema(implementation=LinePoint.class))
  List<PumpSeries> pumpSeries;    // @ArraySchema(schema=@Schema(implementation=PumpSeries.class))
  static of(facilityId, facilityNm, linePoints, pumpSeries)

  static class LinePoint {        // 라인(유량·압력)
    LocalDateTime dtm;            // @JsonFormat(STRING, "yyyy-MM-dd HH:mm:ss")
    BigDecimal actualFlwrt;       // 계측 유출유량 (m³/h) — null=결측/BAD/UNCERTAIN
    BigDecimal actualPrsr;        // 계측 유출압력 (kgf/cm²) — null=결측/BAD/UNCERTAIN
    BigDecimal predcFlwrt;        // 예측 유출유량 — null=예측 미수행
    BigDecimal predcPrsr;         // 예측 유출압력 — null=예측 미수행
    static of(...)
  }

  static class PumpSeries {       // 막대(펌프별 on/off)
    String instrumentId;
    String instrumentNm;
    List<PumpPoint> points;       // @ArraySchema(schema=@Schema(implementation=PumpPoint.class))
    static of(...)
  }

  static class PumpPoint {
    LocalDateTime dtm;            // @JsonFormat(STRING, "yyyy-MM-dd HH:mm:ss")
    Boolean actualRunning;        // tri-state: true=on / false=off / null=불명(BAD·UNCERTAIN·결측)
    Boolean predcRunning;         // tri-state: true=on / false=off / null=예측 미수행
    static of(...)
  }
}
```

- **2-시리즈 분리** 근거: 라인/막대는 서로 다른 차트 축이며 펌프명을 슬롯마다 반복하지 않아 페이로드 경량 (`FacilityStateDto` 의 `flwmtrs[]`+`pumps[]` 2-슬롯 선례 정합).
- inner static class 3종(`LinePoint`·`PumpSeries`·`PumpPoint`)은 has-a 컨테이너 포함(상속 아님) → `coding-discipline.md §2.1` DTO 상속 3단 기준 비해당. 스타일은 10번 `FacilityDailyTimeSeriesDto.DailyTimeSeriesPoint` 정렬(private 생성자 + 정적 `of()`).
- `@ArraySchema(schema=@Schema(implementation=...))` **3지점 의무** (`linePoints`·`pumpSeries`·`PumpSeries.points`) — `api-patterns.md §@Schema(implementation)`. on/off `Boolean` 래퍼는 자동 인식.
- `BaseAuditResponseDto` 미상속 (단순 조회 응답 — 5·9·10번 분류 정합).
- 모든 필드 한국어 `@Schema(description)` + 계측/예측 NULL 의미 분리 명시.

### 3. Service (신규 — 기존 무수정)

신규 `api/src/main/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesService.java`, `@Service @RequiredArgsConstructor @Transactional(readOnly = true)`.

**상수**:
- `SUPPORTED_TYPES = EnumSet.of(PWTF, DWT, PRSF)` (RSV·POINT 400 — 4·5·9·10 동일)
- `TARGET_EQUIP_TYPES = List.of(PUMP, FLWMTR)`
- `TARGET_TAG_TYPES = EnumSet.of(OPS, FRI, PRI)` (10번의 OPS/PWI/FRI 와 달리 PWI 제외·PRI 추가)

**주 메서드** `findFacilityOutflowTimeSeries(facilityId)` (~40줄):
1. `findActiveFacilityOrThrow` — `use_yn=Y` + SUPPORTED_TYPES (10번 동형 재구현, `FacilityErrorCode` 재사용).
2. `instrumentRepository.findByFacilityIdAndEquipType(facilityId, TARGET_EQUIP_TYPES)`.
3. instruments 비면 빈 `linePoints`·`pumpSeries` 응답.
4. `loadTagsByInstrument` — OPS/FRI/PRI 필터 + instrument 별 그룹화.
5. tagSrlNos 수집 → 계측(신규 raw range repo) + 예측(신규 opt range repo) 각 1회 조회 `[today 00:00, now)`.
6. `buildLinePoints(...)` + `buildPumpSeries(...)` 조립 → DTO.

**라인 조립** `buildLinePoints` (~40줄, 10번 `buildActualSlotMap`→`overlayPredcSlots`→`toSortedPoints` 3단 동형):
- **주 유출 라인 FLWMTR 1회 고정 선택** — `selectPrimaryFlwmtr(instruments)` = 첫 FLWMTR(`equip_type=FLWMTR` findFirst). FRI·PRI 동일 라인 일관성 위해 slot별 재선택 아님(10번 per-slot 재선택과 의도 분리). FLWMTR 부재 시 linePoints 빈 목록.
- 고정 FLWMTR 의 FRI tagSrlNo·PRI tagSrlNo 확보(`pickTagSrlNo(instrument, type)`).
- 계측 slot: `actualFlwrt`/`actualPrsr` = GOOD 시 `effectiveVal(corrVal ?: rawVal)`, BAD/UNCERTAIN/결측 → null.
- 예측 slot: `predcFlwrt`/`predcPrsr` = `predcVal` 직접(quality 분기 없음).
- dtm-key TreeMap 합본(오름차순) + 4값 모두 null(계측·예측 양쪽 부재) 슬롯 생략.

**막대 조립** `buildPumpSeries` (backend engineer 권고 3단 분해 — 각 ~30줄):
- `groupActualOpsByDtm(pump 별 actual OPS rows)` / `groupPredcOpsByDtm(pump 별 predc OPS rows)` / `toPumpPointList(merge)`.
- pumps = `filterPumps(instruments)` (`equip_type=PUMP` 강제, `entity-patterns.md §JPA JOINED 도메인 룰`).
- 펌프별 OPS tagSrlNo → dtm-key TreeMap 병합 → `actualRunning`/`predcRunning` tri-state.
- **모든 PUMP instrument 를 pumpSeries 에 포함** (OPS 데이터 부재 펌프는 빈 `points`) — 차트 펌프 트랙 로스터 보존.
- 펌프별 슬롯: `actualRunning`/`predcRunning` 양쪽 null 슬롯 생략(라인 정책 정합).

**OPS 3-state 판정 헬퍼** (도메인 전문가 승인 — 7번 막대 표출 전용):
- `actualRunningTriState(ops)`: ops==null OR qualityCd!=GOOD → null(불명); GOOD+effectiveVal==1.0 → true; GOOD+0.0 → false.
- `predcRunningTriState(ops)`: ops==null OR predcVal==null → null; predcVal==1.0 → true; ==0.0 → false.
- 4·5·10번 2-state(`isPumpRunning*` boolean, UNCERTAIN→false)와 **의도 분리** — 막대에서 BAD/불명을 off 로 표시하면 ON 불명 펌프 역방향 오인(`ot-integration.md §3` 취지).

**헬퍼 재구현 원칙**: `findActiveFacilityOrThrow`·`loadTagsByInstrument`·`filterPumps`·`effectiveVal`·`pickTagSrlNo` 등은 3·5·9·10번과 동일 정책으로 **본 서비스에 재구현**(공통 추출 금지 — `coding-discipline.md §2·§3` + 사용자 메모리 "사이클 간 자산 자동 원용 금지", 10번 Javadoc 확립).

### 4. Repository (7번 전용 신규 — 기존 5·10번 무수정, 결정)

사용자 Q3(사이클 독립성) + 9→10번 선례(인터페이스+Impl+JpaRepository+DTO 완전 분리) 정합. 기존 `RawDataCustomRepository.findByTagSrlNosAndDtmRange`·`TagPredcRangeCustomRepository.findByTagSrlNosAndPredcDtmRange` **무수정**.

**계측(raw, `com.mo.swtp.raw.repository`)**:
- `RawDataOutflowDto` (record: `tagSrlNo`·`acqDtm`·`rawVal`·`corrVal`·`qualityCd`) — Service 내부 전송, Swagger 노출 외.
- `RawDataOutflowCustomRepository` (인터페이스: `findByTagSrlNosAndDtmRange(tagSrlNos, startDtm, endDtm)`).
- `RawDataOutflowCustomRepositoryImpl` (Querydsl `QRawData` + `Projections.constructor` + `tagSrlNo.in().and(acqDtm.goe(start)).and(acqDtm.lt(end))` + `orderBy(acqDtm.asc(), tagSrlNo.asc())`). §2.5 면책 인용(`query-tuning.md §2`).
- `RawDataOutflowRepository extends JpaRepository<RawData, RawDataId>, RawDataOutflowCustomRepository`.

**예측(opt, `com.mo.swtp.opt.repository`)**:
- `TagPredcOutflowDto` (record: `tagSrlNo`·`predcDtm`·`predcVal`).
- `TagPredcOutflowCustomRepository` (인터페이스: `findByTagSrlNosAndPredcDtmRange(...)`).
- `TagPredcOutflowCustomRepositoryImpl` (Querydsl `QTagPrediction` + `predcDtm` 범위). §2.5 면책 인용.
- `TagPredcOutflowRepository extends JpaRepository<TagPrediction, TagPredictionId>, TagPredcOutflowCustomRepository`.

- 기존 인덱스 활용(신규 0건): `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` · `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)`. `acq_dtm`/`predc_dtm` 범위 조건으로 월 RANGE 파티션 프루닝 강제.

### 5. 변경/신규 파일

| 구분 | 파일 |
|------|------|
| 신규 | `api/.../facility/dto/FacilityOutflowTimeSeriesDto.java` |
| 신규 | `api/.../facility/service/FacilityOutflowTimeSeriesService.java` |
| 신규 | `api/.../raw/dto/RawDataOutflowDto.java` |
| 신규 | `api/.../raw/repository/RawDataOutflowCustomRepository.java` |
| 신규 | `api/.../raw/repository/RawDataOutflowCustomRepositoryImpl.java` |
| 신규 | `api/.../raw/repository/RawDataOutflowRepository.java` |
| 신규 | `api/.../opt/dto/TagPredcOutflowDto.java` |
| 신규 | `api/.../opt/repository/TagPredcOutflowCustomRepository.java` |
| 신규 | `api/.../opt/repository/TagPredcOutflowCustomRepositoryImpl.java` |
| 신규 | `api/.../opt/repository/TagPredcOutflowRepository.java` |
| 신규(테스트) | `api/src/test/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesServiceTest.java` |
| 수정 | `api/.../facility/web/FacilityController.java` (엔드포인트 메서드 + Swagger) |

## 도메인 모델

신규 엔티티·테이블·컬럼 **0건** — 조회 전용 API. 기존 `Facility`(JOINED, FacilityType)·`Instrument`(EquipType)·`Tag`(TagMeasurementType)·`RawData`(`rawdata_1m_h`)·`TagPrediction`(`predc_1m_h`) 엔티티를 변경 없이 조회 소비. 신규 DTO·record 는 도메인 엔티티 아님(웹 응답·Service 내부 전송).

> 신규 엔티티/테이블/필드 0건 → `/dev:plan` 도메인 검토 게이트(wtp-domain-expert) 스킵 대상. 도메인 정합성(OPS 3-state·FRI/PRI 소스·4영역 비해당)은 ANALYZE1 안건 4 wtp-domain-expert 승인 기반.

## DB 설계 변경

**없음** — 신규 테이블·컬럼·인덱스·마이그레이션·DDL 0건. 기존 파티션 테이블 + 기존 인덱스 조회만 수행.

> DB 변경 0건 → `/dev:plan` DB 검토 게이트(wtp-dba-reviewer) 스킵 대상. 인덱스·파티션 프루닝·SLA 정합성은 ANALYZE1 안건 2 wtp-dba-reviewer 승인 기반.

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| `FacilityOutflowTimeSeriesService` 단위 테스트 GREEN — (1) 계측+예측 라인 공존 (2) 펌프별 on·off·불명(BAD·UNCERTAIN·결측) 3-state (3) 예측 펌프 on/off/null (4) FRI·PRI 라인 결측 null (5) 라인 4값 양쪽 부재 슬롯 생략 (6) 주 유출 FLWMTR 첫 매치 고정 (7) RSV·POINT 400 (8) 비활성·미존재 404 (9) VALVE 등 비대상 instrument 제외 (10) OPS 태그 없는 펌프는 빈 points 트랙 포함 | ./gradlew.bat :api:test --tests "FacilityOutflowTimeSeriesServiceTest" PASS |
| 전체 빌드 성공 | ./gradlew.bat build 출력 BUILD SUCCESSFUL |
| Swagger 신규 엔드포인트 + DTO 노출 | :api:bootRun 후 Swagger UI 에서 outflow-time-series 엔드포인트 + linePoints·pumpSeries·PumpPoint inner 한국어 @Schema + 계측/예측 NULL 의미 분리 노출 확인 |
| 파티션 프루닝(계측·예측) | EXPLAIN ANALYZE 출력에 rawdata_1m_h_YYYYMM·predc_1m_h_YYYYMM 만 스캔 + Index Scan using idx_*_tag_time |
| 정량 기준 — buildPumpSeries 3단·각 메서드 50줄 이내 | 신규 Service 메서드별 본문 50줄 이내(빈줄·주석 제외) 육안 확인 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 다중 FLWMTR 시설의 "주 유출 라인" 선택 | 미해결 → **결정** | **첫 FLWMTR 1회 고정 선택**(instruments findFirst, FRI+PRI 동일 라인 일관성 위해 slot별 재선택 아님). `disp_ord`/`main_yn` 우선 선택은 본 사이클 제외 — 단일 주 유출 라인 가정. 다중 유출 라인 요구 시 별도 사이클 |
| 라인·펌프 필드명 명명 | 미해결 → **결정** | **`actualFlwrt`/`actualPrsr`/`predcFlwrt`/`predcPrsr` + `actualRunning`/`predcRunning`** — 10번 `actual*`/`predc*` 일관 채택(glossary `actl` 대칭 권고 대비 직전 sibling 10번 일관성 우선) |
| 엔드포인트 path | 미해결 → **결정** | **`/api/facility/{facilityId}/operating-status/outflow-time-series`** — 4·5·9·10 operating-status 그룹핑 일관 |
| 최대 PUMP 대수 SLA | 미해결 → **결정** | 시설당 PUMP **≤ 약 10대 가정**, 단일 범위 조회(커서 미도입). 초과 시에도 본 사이클 단일 조회 유지, 커서/LIMIT 전환은 별도 사이클 트리거(DBA 권고). 단일 호출 200ms SLA |
| 신규 Repository 명명 | 미해결 → **결정** | raw `RawDataOutflow*`(Dto·CustomRepository·Impl·Repository) / opt `TagPredcOutflow*` 4종씩 — 9→10번 완전 분리 선례 + 'Section7'(UI 번호) 금지 |
| 다중 태그 동기 수집 가정 | 가정 | 같은 1분 시점 OPS/FRI/PRI 가 계측 동일 `acq_dtm`·예측 동일 `predc_dtm` 슬롯 일괄 존재(4·5·10번 동형). 미동기 시 미일치 태그 누락 허용 — 단위 테스트 동일 시각 픽스처 검증 |
| 신규 Repository 중복 누적 | 가정 | 본 사이클로 raw·opt range 조회 구현이 각 **2개**(기존 + 신규)로 증가 — DBA "동일 시그니처 3개+ 누적 시 `common` 추상화 재검토" 트리거의 2/3 도달. 본 사이클은 사용자 사이클 독립성 결정 유지, 3번째 누적 시 별도 ANALYZE |
| 예측 PRI 가용성 | 가정 | `predc_1m_h` 의 PRI 적재는 AI 파이프라인 데이터 운영 영역(API 설계 무관). 미적재 시 `predcPrsr` 전체 null — 정상 동작 |

## 제외 사항

- 다중 유출 라인(다중 FLWMTR 동시 표출)·`disp_ord`/`main_yn` 기반 주 라인 선택 — 별도 사이클.
- PUMP 10대 초과 시설 커서/페이지네이션 — 별도 사이클(DBA 권고 트리거).
- raw/opt range 조회 `common` 공통 추상화 — DBA 3개+ 누적 트리거 도달 시 별도 ANALYZE.
- 알람·인터록·운전 모드·이력 기록 — 도메인 4영역 전부 비해당(조회 전용).
- frontend SPEC 전파(`/dev:spec`) — 커밋 후 선택 단계.

## 예상 산출물
- [태스크](../../../tasks/20260601/운전현황분석-7번섹션/TASK1.md)

## 부록: 도메인/DB 검토 결과

신규 엔티티/테이블/필드 0건 + DB 설계 변경 0건 → `/dev:plan` 도메인·DB 검토 게이트 **스킵**(스킬 절차상 둘 다 없으면 생략). 도메인·DB 정합성은 [ANALYZE1](../../../analyze/20260601/운전현황분석-7번섹션/ANALYZE1.md) 5인 회의 결과로 갈음:
- wtp-domain-expert(안건 4): 블로커 0 — 4영역 비해당, OPS 3-state 정합, FRI/PRI=FLWMTR 타당.
- wtp-dba-reviewer(안건 2): 블로커 0 — 신규 DDL 0, 기존 인덱스·파티션 프루닝 정합, 신규 Repository 중복 권고(낮음).
- wtp-backend-engineer(안건 3): 블로커 0 — 2-시리즈 DTO·@ArraySchema 3지점·buildPumpSeries 3단 분해·비즈니스 의미 명명 권고.
