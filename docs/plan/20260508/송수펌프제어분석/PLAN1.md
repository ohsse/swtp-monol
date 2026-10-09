---
status: approved
created: 2026-05-08
updated: 2026-05-08
---
# 송수펌프제어분석 — Backend PLAN

## 목적

송수펌프 AI 플랫폼의 종합 모니터링 화면 (`송수펌프제어분석` — 7섹션) 을 backend 가 완전히 지원하도록 도메인 모델·DB·Controller 를 보강한다. 본 사이클은 화면 §1~§7 통합 1 사이클 (Large) 이며 ANALYZE1 5인 회의 + 사용자 결정 (2026-05-08, 4건) 결과를 PLAN 결정으로 변환한다.

## 배경

- 관련 ANALYZE: [ANALYZE1](../../../analyze/20260508/송수펌프제어분석/ANALYZE1.md) (status: approved, 2026-05-08)
- 사전 plan: `~\.claude\plans\image-png-1-quizzical-sunrise.md` (사용자 approved 2026-05-08)
- 기존 06 (`PumpControlController` — 제어 명령·AI 모드 변경) + 07 (`PumpDrvnStatusController` — 시계열 분석·다운로드) 가 본 화면 7섹션을 부분만 커버. 도메인 결정 미반영 (가압장 PRSF·펌프 가동상태 OPS·밸브 개도 VOI·자식 컬럼 0건 skeleton) 으로 §1·§2·§3·§4·§7 직격 블로커 발생.
- 화면 7섹션이 한 화면에 통합 표출되며 도메인 결정 (PRSF·OPS·VOI·자식 컬럼) 이 §3·§4·§2·§7 양측 동시 영향 — 통합 1 사이클이 분리 시 같은 결정 2번 반복보다 효율.

## 범위

### 포함

| 영역 | 항목 |
|------|------|
| **enum 추가** | `FacilityType.PRSF` (가압장) · `TagMeasurementType.OPS` (펌프 가동상태) · `TagMeasurementType.VOI` (밸브 개도율) |
| **신규 자식 엔티티** | `PressureBoosterStation` (`facility_type_cd='PRSF'`, skeleton — 자식 전용 컬럼 0건) |
| **자식 컬럼 도입** | `Pump` (`rated_head`·`rated_flwrt` NOT NULL + `tag_nm` NULL 허용) · `DistributionWaterTank` (`min_req_prsr` NOT NULL) |
| **마이그레이션 SQL** | V8_1~V8_4 (필수) + V8_5 (선택, `tag_m.unit_cd %` 추가) |
| **시계열 컬럼 정렬** | `pump_predc_h.pwtf_id` → `facility_id` (3단계 양방향 동기) |
| **신규 Controller** | `08. 송수펌프 제어 분석` (`PumpControlAnalysisController`) + `09. 배수지 모니터링` (`DwtStatusController`) |
| **신규 Service** | `FacilityListService` · `PumpAnalysisDashboardService` · `DwtStatusService` |
| **Repository 표준 메서드** | `FacilityCustomRepository.findFacilitiesHavingDwtChild` · `findFirstChildByParentIdAndType` · `RawDataCustomRepository.findLatestByTagSrlNos(tagSrlNos, **acqDtmFrom**)` (파티션 프루닝 강제) · `InstrumentCustomRepository.findByFacilityIdAndEquipType` |
| **DTO 7섹션 분리** | `PumpAnalysisDashboardDto` (통합 응답) + 7개 섹션 DTO (`OperationStatusDto`·`MainFactorDto`·`DwtRequirePressureDto`·`DischargePressureDto`·`PumpOperatingCountDto`·`AnalysisResultDto` + 시설 목록 응답) |
| **OT 룰 적용** | `ot-integration.md §3` 결측 대체값 `OPS`·`VOI` 행 (룰 갱신 완료) — Service 레벨 활용 |
| **frontend SPEC 전파** | `swtp/frontend/docs/api-specs/송수펌프제어분석/SPEC1.md` 자동 추출 (`/dev:spec` 후속) |

### 제외 (사용자 결정 + 도메인 룰 분리 결과)

| 항목 | 처리 |
|------|------|
| §2 자동/반자동 표시 컬럼 결정 (`ai_mode_cd` vs `ai_drvn_mod`) | **차후 태스크** — 본 사이클은 응답 DTO 에 두 컬럼 모두 임시 노출 (frontend 가 임의 선택 가능) |
| §4 분기점 임계값 (`point_m.min_req_prsr`) | **차후 사이클** — 본 사이클은 DWT.min_req_prsr 1개만 도입 |
| §7 1시간 후 시계열 (분 단위 12개 등) | **현재 가정 유지** (단일 시점 `predc_base_dtm + 1h`) — 시계열 변경 필요 시 `pump_predc_h` 스키마 변경 별도 사이클 |
| 활성 정수지 내 reference DWT 선택 메커니즘 | **차후 사이클** — 본 사이클은 `disp_ord` ASC 1번째 DWT 사용 (FK 신설 없음) |
| PRSF 가압장 `pump_interlock_p` 룰 분리 | **차후 사이클** — 본 사이클은 PWTF 와 동일 룰 평가 가정 (시설 종류별 룰 분리 미시행) |
| Valve 자식 개도율 컬럼 도입 | **skeleton 유지** — RawData 만 사용 (DBA 권장) |
| `pwtf_m.reference_dwt_facility_id` FK | **폐기** (사용자 결정 2026-05-08) |
| 06·07 Controller 변경 | **변경 없음** — 06 은 쓰기 (제어 명령·AI 모드 변경), 07 은 시계열 분석·다운로드 책임 유지. 본 사이클은 08·09 신설로 read-only 분석 책임 분리 |
| **OPS BAD 격상 알람 생성 (`alarm_h` INSERT)** | **본 사이클 외** — OPS 측정 유형은 도입하되 BAD 격상 시 알람 생성 경로는 SCADA 인바운드 어댑터 책임. 인바운드 미구현 상태이므로 알람 평가 파이프라인 연결은 SCADA 인바운드 도입 사이클에서 처리 (wtp-domain-expert 검토 결과 반영) |
| **PRSF 가압장 펌프 기동 명령 확장** | **본 사이클 외** + **선행 의무 명시**: 본 사이클은 PRSF skeleton 만 도입하며 `pump_interlock_p` 룰 분리 미시행. 따라서 **PRSF 펌프 기동 명령 확장 전 반드시 별도 사이클에서 인터록 룰 분리·등록 선행 의무**. 현재 InterlockValidator 는 "규칙 미등록 시 통과" pass-through 로직 — PRSF 펌프 룰 미등록 상태에서 기동 명령 경로 활성화 시 인터록 무결속 기동 위험 (wtp-domain-expert 블로커 해소 — 문서 명시 의무) |

## 구현 방향

### 모듈 매핑

| 변경 영역 | 모듈 | 디렉토리 |
|---------|------|--------|
| 도메인 엔티티·enum 변경 | `common` | `common/src/main/java/com/mo/swtp/{facility,instrument,tag}/domain/` · `.../domain/enumtype/` |
| 마이그레이션 SQL | `common` | `common/src/main/resources/db/init/` (V8_1~V8_5) |
| Controller·Service·Repository·DTO | `api` | `api/src/main/java/com/mo/swtp/{pump,facility}/web|service|repository|dto` |
| 단위 테스트 | `common`·`api` | 각 모듈 `src/test/java/com/mo/swtp/...` |

### 패키지 위치 결정

| 신규 클래스 | 위치 | 근거 |
|-----------|------|------|
| `PressureBoosterStation` | `com.mo.swtp.facility.domain` | facility 자식 일관 (PWTF·DWT·RSV·POINT 선례) |
| `PumpControlAnalysisController` | `com.mo.swtp.pump.web` | 06 PumpControlController 와 같은 위치 — 펌프 분석 책임 |
| `DwtStatusController` | `com.mo.swtp.facility.web` | 배수지 도메인 단위 책임 — 향후 배수지 운영 대시보드 등 재사용 |
| `FacilityListService`·`DwtStatusService` | `com.mo.swtp.facility.service` | facility 도메인 단위 |
| `PumpAnalysisDashboardService` | `com.mo.swtp.pump.service` | pump 도메인 분석 책임 |
| `RawDataCustomRepository.findLatestByTagSrlNos` | `com.mo.swtp.raw.repository` (확장) | 기존 RawData 도메인 메서드 추가 |

### 호출 흐름 (Service 오케스트레이션)

```
[08] PumpControlAnalysisController
  ├─ GET /api/pump-control/analysis/facilities
  │     → FacilityListService.findFacilitiesHavingDwtChild(types=[PWTF, PRSF])
  │
  └─ GET /api/pump-control/analysis/dashboard?facilityId={UUID}
        → PumpAnalysisDashboardService.buildDashboard(facilityId)
              ├─ §2 InstrumentRepository.findByFacilityIdAndEquipType(facilityId, [PUMP, FLWMTR, PRSMTR])
              │     → TagRepository.findByInstrumentIdIn(instrumentIds)
              │     → RawDataRepository.findLatestByTagSrlNos(tagSrlNos, acqDtmFrom)  ← IN 절 1회 + acq_dtm 하한 (파티션 프루닝 강제)
              │     → AiDrvnModService.findByFacilityId(facilityId)       ← 응답에 ai_mode_cd + ai_drvn_mod 모두 노출 (DTO Javadoc: ai_mode_cd 우선 표시 권고, ai_drvn_mod 참고용)
              ├─ §5 PRSMTR + tag_se_cd='PRI' 토출 압력 (위 §2 와 동일 경로)
              ├─ §6 §2 펌프 가동상태 카운트 집계 (서버 집계 — operatingPumpCount 필드)
              └─ §7 PumpPredictionService.findLatest(facilityId) + pump_cmbn_d 매핑 (펌프별 on/off 분해)

[09] DwtStatusController
  └─ GET /api/dwt-status?facilityId={UUID}
        → DwtStatusService.buildStatus(facilityId)
              ├─ §3 FacilityRepository.findChildrenByParentIdAndType(facilityId, DWT)
              │     → InstrumentRepository.findByFacilityIdInAndEquipType(dwtIds, [PRSMTR, FLWMTR, VALVE, LVMTR])
              │     → TagRepository.findByInstrumentIdIn(instrumentIds)  + io_cd 활용 (FLWMTR 입구/출구 구분)
              │     → RawDataRepository.findLatestByTagSrlNos(tagSrlNos, acqDtmFrom) ← IN 절 1회 + acq_dtm 하한 (파티션 프루닝 강제)
              └─ §4 FacilityCustomRepository.findFirstChildByParentIdAndType(facilityId, DWT, OrderBy disp_ord ASC)
                    → DistributionWaterTank.getMinReqPrsr() 응답
```

### 신규 파일 목록

**common 모듈** (12 파일):

```
common/src/main/java/com/mo/swtp/facility/domain/PressureBoosterStation.java                ← 신규
common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityType.java                ← 변경 (PRSF 추가)
common/src/main/java/com/mo/swtp/instrument/domain/Pump.java                                ← 변경 (자식 컬럼 3건)
common/src/main/java/com/mo/swtp/facility/domain/DistributionWaterTank.java                ← 변경 (자식 컬럼 1건)
common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java               ← 변경 (OPS·VOI 추가)
common/src/main/resources/db/init/V8_1__facility_m_prsf_skeleton.sql                       ← 신규
common/src/main/resources/db/init/V8_2__pump_m_self_columns.sql                            ← 신규
common/src/main/resources/db/init/V8_3__dwt_m_self_columns.sql                             ← 신규
common/src/main/resources/db/init/V8_4__pump_predc_h_facility_id.sql                       ← 신규
common/src/main/resources/db/init/V8_5__tag_unit_cd_percent.sql                            ← 신규 (선택)
common/src/test/java/com/mo/swtp/facility/domain/PressureBoosterStationTest.java           ← 신규
common/src/test/java/com/mo/swtp/instrument/domain/PumpSelfColumnsTest.java                ← 신규
```

**api 모듈** (~22 파일 — Controller 2 + Service 3 + Repository 4 + DTO 9 + Test 4):

```
# Controller·Service
api/src/main/java/com/mo/swtp/pump/web/PumpControlAnalysisController.java                  ← 신규 (08)
api/src/main/java/com/mo/swtp/facility/web/DwtStatusController.java                        ← 신규 (09)
api/src/main/java/com/mo/swtp/facility/service/FacilityListService.java                    ← 신규
api/src/main/java/com/mo/swtp/pump/service/PumpAnalysisDashboardService.java               ← 신규
api/src/main/java/com/mo/swtp/facility/service/DwtStatusService.java                       ← 신규

# Repository (확장)
api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepository.java            ← 변경 (메서드 2건 추가)
api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepositoryImpl.java        ← 변경
api/src/main/java/com/mo/swtp/instrument/repository/InstrumentCustomRepository.java        ← 변경 (메서드 1건 추가)
api/src/main/java/com/mo/swtp/instrument/repository/InstrumentCustomRepositoryImpl.java    ← 변경
api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java                  ← 변경 (메서드 1건 추가)
api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java              ← 변경

# DTO (9건)
api/src/main/java/com/mo/swtp/pump/dto/FacilityListDto.java                                ← 신규
api/src/main/java/com/mo/swtp/pump/dto/PumpAnalysisDashboardDto.java                       ← 신규 (통합 응답)
api/src/main/java/com/mo/swtp/pump/dto/section/OperationStatusDto.java                     ← 신규 (§2)
api/src/main/java/com/mo/swtp/pump/dto/section/DischargePressureDto.java                   ← 신규 (§5)
api/src/main/java/com/mo/swtp/pump/dto/section/PumpOperatingCountDto.java                  ← 신규 (§6)
api/src/main/java/com/mo/swtp/pump/dto/section/AnalysisResultDto.java                      ← 신규 (§7)
api/src/main/java/com/mo/swtp/facility/dto/DwtStatusDto.java                               ← 신규 (§3+§4 통합)
api/src/main/java/com/mo/swtp/facility/dto/section/MainFactorDto.java                      ← 신규 (§3)
api/src/main/java/com/mo/swtp/facility/dto/section/DwtRequirePressureDto.java              ← 신규 (§4)

# Test
api/src/test/java/com/mo/swtp/pump/service/PumpAnalysisDashboardServiceTest.java           ← 신규
api/src/test/java/com/mo/swtp/facility/service/DwtStatusServiceTest.java                   ← 신규
api/src/test/java/com/mo/swtp/facility/service/FacilityListServiceTest.java                ← 신규
api/src/test/java/com/mo/swtp/pump/web/PumpControlAnalysisControllerTest.java              ← 신규
```

## 도메인 모델

### 신규 엔티티

| 엔티티/테이블 | 역할 | 주요 필드 |
|------------|------|---------|
| `PressureBoosterStation` / `prsf_m` | 가압장 (Pressure Booster Station) — `Facility` JPA JOINED 자식 (`facility_type_cd='PRSF'`) | (skeleton — 자식 전용 컬럼 0건. 부모 PK `facility_id` 자동 상속, `facility_nm` UNIQUE, `parent_facility_id` self-FK) |

### 변경 엔티티

| 엔티티/테이블 | 변경 내용 | 주요 필드 |
|------------|--------|---------|
| `Pump` / `pump_m` | 자식 전용 컬럼 3건 추가 | `rated_head` (DOM_QTY_15_4, NOT NULL — 정격 양정 m) · `rated_flwrt` (DOM_QTY_15_4, NOT NULL — 정격 유량 m³/h) · `tag_nm` (DOM_TAG_NM_50, NULL 허용 — 제조사 명판값) |
| `DistributionWaterTank` / `dwt_m` | 자식 전용 컬럼 1건 추가 | `min_req_prsr` (DOM_QTY_15_4, NOT NULL — 최소 요구 압력 kgf/cm²) |

### 신규 enum 값

| 위치 | 추가 값 | 의미 | 비고 |
|------|--------|------|------|
| `com.mo.swtp.facility.domain.enumtype.FacilityType` | `PRSF` | 가압장 (Pressure Booster Station Facility) | DDL CHECK 제약 미사용 (Java `@Enumerated` 단일 방어선) |
| `com.mo.swtp.tag.domain.enumtype.TagMeasurementType` | `OPS` | 펌프 가동상태 (Operation Status, on/off DI 신호) | `ot-integration.md §3` 결측 대체값 표 행 추가 완료 — 즉시 BAD 격상 |
| `com.mo.swtp.tag.domain.enumtype.TagMeasurementType` | `VOI` | 밸브 개도율 (Valve Opening Indicator, % 단위) | `ot-integration.md §3` 결측 대체값 표 행 추가 완료 — Hold Last Value |

### 정적 팩토리 메서드 변경 (entity-patterns.md §외부 할당 PK 엔티티 패턴 정합)

```java
// Pump.create — 컬럼 3건 추가
public static Pump create(
        String instrumentNm,
        Facility facility,
        Integer dispOrd,
        BigDecimal ratedHead,      // NEW
        BigDecimal ratedFlwrt,     // NEW
        String tagNm                // NEW (NULL 허용)
) { ... }

// DistributionWaterTank.create — 컬럼 1건 추가
public static DistributionWaterTank create(
        String facilityNm,
        String parentFacilityId,
        Integer dispOrd,
        YnType mainYn,
        BigDecimal minReqPrsr      // NEW
) { ... }

// PressureBoosterStation.create — 신규 (PWTF.create 시그니처 동일)
public static PressureBoosterStation create(
        String facilityNm,
        String parentFacilityId,
        Integer dispOrd,
        YnType mainYn
) { ... }
```

> 변경 메서드 (`changeRatedHead`, `changeRatedFlwrt`, `changeTagNm`, `changeMinReqPrsr` 등) 는 본 사이클 외 — 운영자 보정 화면이 별도 사이클에서 도입될 때 추가.

## DB 설계 변경

### 마이그레이션 SQL 4 + 선택 1

각 파일 `db/indexing-and-migration.md §2` **3단계 무중단 마이그레이션** + `§4` **컬럼 COMMENT 의무화** 정책 준수.

| 파일 | 내용 | 운영 영향 | 정책 인용 |
|------|------|---------|---------|
| `V8_1__facility_m_prsf_skeleton.sql` | `prsf_m` 자식 테이블 skeleton (`facility_id` PK FK to `facility_m`) + COMMENT + `idx_facility_m_type_parent` 복합 인덱스 (`facility_type_cd, parent_facility_id`) `CREATE INDEX CONCURRENTLY` 추가 — `findFacilitiesHavingDwtChild` exists 서브쿼리 최적화 | 신규 테이블 — 락 없음, 인덱스 CONCURRENTLY | `entity-patterns.md §JPA JOINED` + `db/indexing-and-migration.md §1` 복합 인덱스 카디널리티 순서 |
| `V8_2__pump_m_self_columns.sql` | `pump_m.rated_head`·`rated_flwrt`·`tag_nm` 추가 (3단계: NULL 추가 → 백필 → NOT NULL 전환) + COMMENT | 운영 데이터 백필 시 임시값 적용 후 운영자 보정 절차 | `db/indexing-and-migration.md §2` |
| `V8_3__dwt_m_self_columns.sql` | `dwt_m.min_req_prsr` 추가 (3단계 NOT NULL) + COMMENT | 동일 | 동일 |
| `V8_4__pump_predc_h_facility_id.sql` | `pwtf_id` → `facility_id` 컬럼 정렬 (3단계 양방향 동기 → 구 컬럼 제거) + COMMENT | **운영 시간 외 (00:00~06:00) 실행** — 파티션 루트 `ALTER TABLE` 의 자식 파티션 `ACCESS SHARE LOCK` 영향. **추가 안전책**: (1) 사전 검증 SQL `SELECT count(*) FROM pump_predc_h WHERE pwtf_id IS NOT NULL` 백필 완료 확인, (2) 세션 `SET lock_timeout = '5s'` + 충돌 시 즉시 롤백 후 재시도, (3) `rawdata_1m_h` INSERT 스케줄러 일시 정지 (TASK 체크박스로 명시) | `db/indexing-and-migration.md §2` |
| `V8_5__tag_unit_cd_percent.sql` (선택) | `tag_m.unit_cd` 허용값에 `%` 추가 (애플리케이션 검증 — DDL CHECK 미사용) + COMMENT 갱신 (허용값 목록 명시) | 무시할 수준 — 검증 코드만 변경. COMMENT 포함으로 `check-ddl-column-comment.sh` 훅 통과 | `db/indexing-and-migration.md §3·§4` |

> **인덱스 추가 정책**: `(parent_facility_id, facility_type_cd, disp_ord)` 복합 인덱스 (`findFirstChildByParentIdAndType` 정렬 최적화) 는 본 사이클 운영 데이터 규모 (시설 수십 건 미만) 에서 Seq Scan 이 옵티마이저 선택일 가능성이 높아 **본 사이클 미생성**. 시설 데이터 100건 초과 시 별도 사이클에서 `CREATE INDEX CONCURRENTLY` 추가. 본 결정 근거는 `db/indexing-and-migration.md §1` "카디널리티 작은 컬럼 단독 인덱스 미적용" 정책 확장 적용.

### 백필 정책 (V8_2·V8_3)

운영자 입력 필수 데이터 (`rated_head`·`rated_flwrt`·`min_req_prsr`) 의 백필 임시값:

| 컬럼 | 임시값 | 사유 |
|------|------|------|
| `pump_m.rated_head` | `0.00` (NUMERIC(15,4)) | 운영 후 운영자 명판값 보정 — 0 은 명백한 미입력 표지 |
| `pump_m.rated_flwrt` | `0.00` | 동일 |
| `pump_m.tag_nm` | `NULL` | NULL 허용 컬럼이므로 백필 불요 — V8_2 는 NOT NULL 컬럼 2건 + NULL 허용 1건 |
| `dwt_m.min_req_prsr` | `0.0000` | 동일. 인터록 평가 영향이 본 사이클 외이므로 임시 0 허용 |

PLAN approved 후 운영자에게 보정 안내 (별도 운영 채널) — 코드 영향 없음.

### 시계열 → 마스터 FK 정책 정합

`pump_predc_h.facility_id` 정렬 후에도 시계열 → 마스터 FK 금지 정책 (`db/partitioning-and-retention.md §1`) 유지 — 논리 참조만 적용. 새 `facility_id` 컬럼은 NOT NULL + 백필 검증 후 NOT NULL 전환.

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령 |
|------|---------|
| 1. enum 추가 후 빌드 통과 (FacilityType.PRSF · TagMeasurementType.OPS·VOI) | `./gradlew.bat :common:build` BUILD SUCCESSFUL |
| 2. PressureBoosterStation JPA JOINED 매핑 검증 — DiscriminatorValue 매핑 + facility_nm UNIQUE 제약 | `./gradlew.bat :common:test --tests PressureBoosterStationTest` GREEN (Mockito 단위 5건 — 정적 팩토리 + 부모 PK 자동 상속 + main_yn=Y/N 분기) |
| 3. Pump 자식 컬럼 + DistributionWaterTank 자식 컬럼 정적 팩토리 변경 후 단위 테스트 통과 | `./gradlew.bat :common:test --tests PumpSelfColumnsTest` GREEN (3건 — rated_head·rated_flwrt NOT NULL + tag_nm NULL 허용) |
| 4. 마이그레이션 SQL 4건 (V8_1~V8_4) 무중단 적용 + COMMENT 검증 | 로컬 PostgreSQL 에서 V8_1~V8_4 순차 적용 후 `psql \d+ pump_m`·`\d+ dwt_m`·`\d+ prsf_m`·`\d+ pump_predc_h` 출력에 신규 컬럼 + COMMENT 모두 표시 |
| 5. `findFacilitiesHavingDwtChild(types=[PWTF, PRSF])` Querydsl 메서드 — exists 서브쿼리 1회 SQL (N+1 회피) | p6spy 슬로우 쿼리 로그에서 단일 쿼리 1회 + EXPLAIN ANALYZE 시 시퀀셜 스캔 부재 |
| 6. `findFirstChildByParentIdAndType(facilityId, DWT, OrderBy disp_ord ASC LIMIT 1)` Querydsl 메서드 | 통합 테스트 `DwtStatusServiceTest.기준배수지_표시순서_1번` GREEN — 자식 DWT 3건 중 disp_ord=1 행 1개만 응답 |
| 7. `findLatestByTagSrlNos(tagSrlNos, acqDtmFrom)` Repository 메서드 — IN 절 배치 (N+1 회피) **+ 파티션 프루닝 보장** | 단위 테스트에서 태그 100개 입력 시 SQL 1회 실행 (Mockito verify). **추가 검증**: 통합 테스트에서 `EXPLAIN (ANALYZE, BUFFERS)` 결과의 모든 자식 파티션 노드에서 Seq Scan 부재 확인 — 최근 13개월 파티션만 스캔하고 그 이전 파티션은 Pruned by Postgres 처리. `partitioning-and-retention.md §1` 정합 |
| 8. Controller 08·09 신설 + Swagger 노출 | `./gradlew.bat :api:bootRun` 후 `curl /v3/api-docs | jq '.tags'` 에 "08. 송수펌프 제어 분석"·"09. 배수지 모니터링" 표시 |
| 9. §1~§7 7섹션 통합 dashboard 응답 200 OK + 모든 섹션 DTO 채워짐 | 통합 테스트 `PumpAnalysisDashboardServiceTest.7섹션_통합조회` GREEN — 정수지 1건·펌프 4건·DWT 2건·예측 1건 mock + 응답 JSON 의 7개 섹션 모두 not-null |
| 10. 펌프별 예측 분해 (`pump_cmbn_d` 매핑) — 펌프 #1~#4 의 1시간 후 on/off 모두 응답 | 통합 테스트 `PumpAnalysisDashboardServiceTest.펌프별_예측_분해` GREEN |
| 11. `pump_predc_h.pwtf_id → facility_id` 정렬 후 06·07 회귀 테스트 통과 | 기존 `PumpControlControllerTest`·`PumpDrvnStatusControllerTest` 전건 GREEN (새 컬럼명 적용) |
| 12. ANALYZE1 룰 갱신 4건 적용 검증 | `grep "OPS" backend/.claude/rules/ot-integration.md` 매칭 + `grep "VOI" ot-integration.md` 매칭 + `grep "PRSF 가압장 시설 단위 별도 평가" ot-integration.md` 매칭 + `grep "facility_id.*pwtf_id.*deprecated" backend/.claude/rules/dict/standard-terms.md` 매칭 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 화면 §2 "자동/반자동" 표시는 `ai_mode_cd` (시스템 상태) + `ai_drvn_mod` (사용자 의도) 두 컬럼 모두 응답에 노출 — frontend 가 임의 선택 | **결정** | ANALYZE 의 차후 태스크 이관 결정 반영. 본 사이클은 두 컬럼 모두 노출하는 임시 방안 채택 |
| `pump_predc_h.facility_id` 정렬 후 시계열 → 마스터 FK 금지 정책 유지 (`db/partitioning-and-retention.md §1`) | **결정** | 본 정렬은 컬럼명 정렬일 뿐, 신규 FK 추가 아님 — 정책 위반 없음 |
| `09. 배수지 모니터링` 엔드포인트 시그니처 — `GET /api/dwt-status?facilityId={UUID}` (정수지 ID 1개 받아 자식 DWT 목록 + 측정값 응답) | **결정** | 인증 컨텍스트 테넌트 필터는 별도 (모든 API 공통). facilityId 는 화면이 §1 에서 선택한 활성 정수지 ID |
| Valve 자식 — RawData 만 사용 (skeleton 유지). VOI tag_se_cd 만 신설 | **결정** | DBA 권장. 자식 마스터 컬럼 0건 시 JOINED 자식 테이블 생성 실익 부재 |
| §7 펌프별 예측 분해는 `pump_cmbn_d` 매핑 사용 (DB 변경 0건) | **결정** | wtp-dba-reviewer 권장. 화면 단순 on/off 표출이면 충분 |
| §4 기준배수지는 `findFirstChildByParentIdAndType(facilityId, DWT, OrderBy disp_ord ASC)` 적용 (FK 신설 없음) | **결정** | 사용자 결정 (2026-05-08) 반영 |
| §7 1시간 후 분석결과 — 단일 시점 (`predc_base_dtm + 1h`) 표출 가정 유지 | **결정** | 사용자 결정 (2026-05-08) — 시계열 변경 필요 시 별도 사이클 |
| `tag_m.unit_cd` 에 `%` 추가 (V8_5 선택 마이그레이션) | 가정 | TASK 단계에서 V8_5 도입 여부 최종 결정 — 화면이 % 단위 응답 시점에 필요 |
| PRSF 가압장 인터록 룰 분리 미시행 — 본 사이클은 read-only 모니터링이라 기동 명령 경로 없음. PRSF 룰 미등록 상태 pass-through 위험은 §제외 사항 명시 의무 처리 | **결정** | wtp-domain-expert 블로커 해소 결과. 본 사이클 코드 변경 없음, 문서 1행 (제외 사항) 추가로 risk 비가시화 회피 |
| 자식 컬럼 백필 임시값 (`rated_head=0`·`rated_flwrt=0`·`min_req_prsr=0`) 운영자 보정 + `ANALYZE pump_m; ANALYZE dwt_m;` 통계 재수집 | 가정 | 별도 운영 채널로 보정 안내. TASK 운영 절차에 `ANALYZE` 명령 한 줄 추가 (DBA 참고 권고) — 코드 영향 없음 |
| §2 두 컬럼 임시 노출 시 DTO Javadoc 에 "ai_mode_cd 우선 표시 권고, ai_drvn_mod 참고용" 안내 명시 의무 | **결정** | wtp-domain-expert 권고 반영. 강제 전환 중 화면 모드 vs 시스템 상태 불일치 운전원 혼동 방지. DTO 클래스 Javadoc 에 명시 (TASK 체크박스) |
| `송수펌프_가동이력` docx 별개 작업 자료 가정 | 가정 | ANALYZE1 동일 가정 유지 — 본 사이클 미참고 |

분류값: 가정 / 미해결 → 결정

## 제외 사항

- 알람 4단계 평가 로직 변경 — read-only 화면이므로 알람 임계값·전이·복귀 조건 미변경
- 인터록 평가 로직 변경 — `pump_interlock_p` 룰 평가는 본 사이클 외 (PRSF 룰 분리 차후 사이클)
- AI 운전 모드 변경 API — 06 PumpControlController 의 `/ai-mode` 변경 책임 유지
- SCADA 어댑터 (`com.mo.swtp.scada.inbound|outbound`) 변경 — 본 사이클은 read-only 모니터링
- 운영자 보정 화면 (`changeRatedHead`·`changeMinReqPrsr` 메서드) — 별도 사이클
- 알람 이력 (`alarm_h`) 조회 — 본 사이클 외
- E2E 테스트 도입 — 단위·통합 테스트만 (`test-strategy-e2e-roadmap.md` 미트리거)

## TASK 분할 후보

본 사이클은 Large 분류 + Phase 10 이상 / 체크박스 60 이상 가능성 높음 (V8_1~V8_5 + Pump/DWT/PRSF 자식 + Controller 2 + Service 3 + Repository 4 + DTO 9 + Test 4). 다음 3분할 권장 — TASK 단계에서 사용자 확인.

| 분할 | 묶음명 | 범위 | 추정 체크박스 수 |
|------|------|------|------------|
| TASK1-1 | 데이터 계층 | enum (PRSF·OPS·VOI) + PressureBoosterStation 엔티티 + Pump/DWT 자식 컬럼 + 마이그레이션 SQL V8_1~V8_5 + 정적 팩토리 변경 + 단위 테스트 | ~25 |
| TASK1-2 | 애플리케이션 계층 | Repository 표준 메서드 4건 + Service 3건 + DTO 9건 + Controller 2건 + Swagger 어노테이션 | ~25 |
| TASK1-3 | 통합 검증 + 회귀 | 통합 테스트 4건 (Service/Controller) + 06/07 회귀 + frontend SPEC 자동 추출 + 빌드 검증 | ~15 |

## 예상 산출물

- [태스크 (작성 예정)](../../../tasks/20260508/송수펌프제어분석/TASK1.md) — 분할 시 TASK1-1·TASK1-2·TASK1-3
- [결과 (작성 예정)](../../../results/20260508/송수펌프제어분석/RESULT1.md)
- [리뷰 (작성 예정)](../../../reviews/20260508/송수펌프제어분석/REVIEW1.md)
- frontend 명세 (작성 예정): `swtp/frontend/docs/api-specs/송수펌프제어분석/SPEC1.md`

---

## 부록: 도메인/DB 검토 결과

### wtp-domain-expert (도메인 4영역 정합성)

- **블로커 1건**: PRSF 인터록 통과(pass-through) 상태 비가시화 위험 — 본 사이클이 read-only 모니터링이라 즉시 운전 안전 위협은 없으나, PRSF 도입이 향후 기동 명령 확장의 전제이므로 인터록 룰 미등록 상태가 비가시화될 위험. → **해소**: PLAN §제외 사항 표에 "PRSF 가압장 펌프 기동 명령 확장 전 인터록 룰 분리·등록 선행 의무" 행 추가 (문서 1행).
- **권고 1건**: §2 두 컬럼 임시 노출 시 강제 전환 중 화면 모드 vs 시스템 상태 불일치 운전원 혼동. → **해소**: PLAN §가정 표에 DTO Javadoc 안내 명시 의무 결정 추가 (TASK 체크박스로 변환).
- **참고 1건**: OPS BAD 격상 알람 생성 경로 미명시. → **해소**: PLAN §제외 사항 표에 "OPS BAD 격상 알람 생성은 SCADA 인바운드 어댑터 책임, 본 사이클 외" 행 추가.

### wtp-dba-reviewer (DB 스키마·쿼리 성능)

- **블로커 1건**: `findLatestByTagSrlNos` 파티션 프루닝 미보장 — `acq_dtm` 범위 조건 누락 시 전체 파티션 스캔. → **해소**: Repository 메서드 시그니처에 `acqDtmFrom: LocalDateTime` 파라미터 추가 (최근 13개월 내부 고정 가능). 성공 기준 7번에 `EXPLAIN (ANALYZE, BUFFERS)` 검증 의무 추가 (Seq Scan 부재 + Pruned 파티션 확인).
- **권고 3건**: V8_4 스케줄러 충돌 안전책 누락 / `findFacilitiesHavingDwtChild` 복합 인덱스 후보 / `findFirstChildByParentIdAndType` 정렬 인덱스 후보. → **해소**: V8_1 에 `idx_facility_m_type_parent` 복합 인덱스 (`facility_type_cd, parent_facility_id`) `CREATE INDEX CONCURRENTLY` 추가. V8_4 에 사전 검증 SQL + `lock_timeout = '5s'` + `rawdata_1m_h` 스케줄러 일시 정지 안전책 명시. `(parent_facility_id, facility_type_cd, disp_ord)` 복합 인덱스는 시설 수십 건 미만 기준 미생성 결정 (운영 데이터 100건 초과 시 별도 사이클).
- **참고 2건**: 백필 후 `ANALYZE pump_m; ANALYZE dwt_m;` 운영 절차 / V8_5 COMMENT 포함. → **해소**: PLAN §가정 표에 ANALYZE 통계 재수집 권고 추가. V8_5 표 행 내용에 "COMMENT 갱신 (허용값 목록 명시)" 명시.

### 표준 데이터 도메인 2차 승인

- **신규 `DOM_*` 등록 0건** — 기존 `DOM_QTY_15_4`·`DOM_ID_36`·`DOM_TAG_NM_50`·`DOM_CODE_20` 재사용. 추가 승인 사항 없음.

### 종합

- 블로커 2건 모두 PLAN 수정으로 해소 완료 (코드 변경 없음, 문서·시그니처 정렬만)
- 권고 4건 중 핵심 3건 PLAN 반영, 인덱스 미생성 결정은 근거 명시
- 참고 3건 모두 반영

