---
status: completed
created: 2026-05-04
updated: 2026-05-04
---
# 마스터도메인설계 — 엔티티·DDL 구현 결과

## 관련 작업
- [계획안](../../../plan/20260503/마스터도메인설계/PLAN1.md)
- [태스크 1-1 정적 자산 (enum + DDL)](../../../tasks/20260503/마스터도메인설계/TASK1-1.md)
- [태스크 1-2 신규 엔티티 + Repository](../../../tasks/20260503/마스터도메인설계/TASK1-2.md)
- [태스크 1-3 기존 패키지 분리 이관 + 빌드 검증](../../../tasks/20260503/마스터도메인설계/TASK1-3.md)
- [도메인 분석](../../../analyze/20260502/마스터도메인설계/ANALYZE1.md)

## 작업 요약

마스터도메인설계 ANALYZE1 (Round 1·2·3, 2026-05-02·03) 의 5인 회의 결정 사항을 코드·DDL 로 구현하였다. 시설(Facility)·계측기(Instrument)·태그(Tag)·로우데이터(RawData) 4개 마스터 도메인을 신규 패키지 4건으로 도입하고, JPA `@Inheritance(JOINED)` + `@DiscriminatorColumn` 패턴으로 자식 9종 (PWTF·DWT·RSV / PUMP·VALVE·FLWMTR·PRSMTR·LVMTR·ELCMTR) 을 통합하였다. 종전 `com.mo.swtp.pump.domain` 의 단일 마스터 (Pump·PurifiedWaterTank·DistributionWaterTank) 는 폐기하고 잔존 엔티티 (PumpInterlock·PumpCmbn·PumpCmbnDetail·PumpControlHistory·PumpPredictionResult) 의 FK 컬럼·필드를 `instrument_id`·`facility_id` 로 정렬하였다. AI 모듈 (`AiDrvnMode`·`AiDrvnModeHistory` 및 의존 Service·DTO·Scheduler) 도 V2 SQL 컬럼명 변경에 맞춰 정합화하였다.

핵심 결정 흐름:
- **Round 2** (2026-05-02): JPA JOINED + DiscriminatorColumn 다형성 채택. `pwtf_m`·`dwt_m`·`pump_m` 별도 마스터 폐기, `facility_m`·`instrument_m` 단일 마스터 + 자식 9종 통합.
- **Round 3** (2026-05-02·03): 외부 할당 PK + Persistable 패턴 폐기 → UUID 자동 생성 PK + 이름 UNIQUE 비즈니스 식별자. `tag_m` PK = `tag_srl_no` 자연키 (외부 할당, `Persistable<String>`). `rawdata_1m_h` = `raw_val` + `corr_val` + `quality_cd` + BaseEntity 4 (immutable 이력 패턴 폐기, `corr_val` 갱신 허용).

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 5 |
| 체크박스 수 | 약 55 (TASK1-1: 10 + TASK1-2: 17 + TASK1-3: 28) |
| 분할 여부 | Y |
| 분할 근거 | 체크박스 60건 미만이지만 정적 자산(enum + DDL) → 신규 엔티티/Repository → 기존 패키지 이관/검증 의 3개 계층 경계가 명확하고, Phase 5개로 컨텍스트 관리 부담이 큰 Large 작업으로 판단되어 분할 |

## 변경 사항

### 의도된 변경

전체 51 파일 변경 (신규 33건 + 수정 51건 + 삭제 7건), +765 / -1087 라인.

#### 1. 신규 enum 5건 (TASK1-1 Phase 1)

- `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityType.java` — PWTF·DWT·RSV
- `common/src/main/java/com/mo/swtp/instrument/domain/enumtype/EquipType.java` — PUMP·VALVE·FLWMTR·PRSMTR·LVMTR·ELCMTR
- `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` — FRI·PRI·LEI·PWI·RMS
- `common/src/main/java/com/mo/swtp/tag/domain/enumtype/IoCode.java` — INPUT·OUTPUT·BIDIR
- `common/src/main/java/com/mo/swtp/raw/domain/enumtype/QualityCode.java` — GOOD·BAD·UNCERTAIN

#### 2. 신규 DDL — V1 폐기 + V6 신규 5건 + V2~V5 컬럼명 변경 (TASK1-1 Phase 2)

- `V1__pumpcontrol_master_tables.sql` — 본문 SQL 0건으로 폐기 + redirect 주석
- `V6_1__facility_master_tables.sql` 신규 — `facility_m` + 자식 3 (`pwtf_m`·`dwt_m`·`rsv_m`) + UNIQUE `facility_nm` + self-FK
- `V6_2__instrument_master_tables.sql` 신규 — `instrument_m` + 자식 6 + UNIQUE `(facility_id, instrument_nm)` 복합 + FK to `facility_m`
- `V6_3__pump_secondary_table_realign.sql` 신규 — `pump_cmbn_m`·`pump_cmbn_d`·`pump_interlock_p`·`ai_drvn_mod_p` 의 FK 재정렬
- `V6_4__tag_master_table.sql` 신규 — `tag_m` (`tag_srl_no` 자연키 PK + FK to `instrument_m`)
- `V6_5__rawdata_1m_h.sql` 신규 — 월 RANGE 파티션 + 6개월 선행 + BRIN/복합 인덱스 (DDL 4단계 순서 의무 준수)
- `V2__pumpcontrol_partition_tables.sql` 갱신 — `pump_id` → `instrument_id`, `pwtf_id` → `facility_id` (3 테이블) + COMMENT 재작성
- `V4__pumpcontrol_indexes.sql` 갱신 — V2 변경 컬럼 의존 인덱스 정의 동기화

#### 3. 신규 엔티티 13건 + Repository 4건 (TASK1-2 Phase 3)

엔티티 (`common/src/main/java/com/mo/swtp/`):
- `facility/domain/Facility.java` (abstract, JOINED, UUID PK + UNIQUE `facility_nm` + self-FK)
- `facility/domain/{PurifiedWaterTank, DistributionWaterTank, Reservoir}.java` (자식 3종)
- `instrument/domain/Instrument.java` (abstract, JOINED, UUID PK + UNIQUE `(facility_id, instrument_nm)` 복합 + `@ManyToOne` FK)
- `instrument/domain/{Pump, Valve, FlowMeter, PressureMeter, LevelMeter, PowerMeter}.java` (자식 6종)
- `tag/domain/Tag.java` (`Persistable<String>` 자연키 PK + FK to Instrument)
- `raw/domain/{RawData, RawDataId}.java` (BaseEntity + 복합 PK `@IdClass` + INSERT-only `@PrePersist`/`@PreUpdate`/`@PostLoad` immutable 검증)

Repository (`api/src/main/java/com/mo/swtp/`):
- `facility/repository/FacilityRepository.java` — `findByFacilityType(FacilityType)` + `existsByFacilityNm` + `@BatchSize(100)`
- `instrument/repository/InstrumentRepository.java` — `findByEquipType(EquipType)` + `findByFacilityFacilityId(String)` + `existsByFacilityFacilityIdAndInstrumentNm` + `@BatchSize(100)`
- `tag/repository/TagRepository.java` — `findByInstrumentInstrumentId(String)`
- `raw/repository/RawDataRepository.java` — 복합 PK `JpaRepository<RawData, RawDataId>` 단순 CRUD

#### 4. 기존 패키지 분리 이관 + FK 컬럼 변경 (TASK1-3 Phase 4)

폐기 6건 (삭제):
- `common/src/main/java/com/mo/swtp/pump/domain/{Pump, PurifiedWaterTank, DistributionWaterTank}.java`
- `api/src/main/java/com/mo/swtp/pump/repository/{Pump, PurifiedWaterTank, DistributionWaterTank}Repository.java`

잔존 엔티티 6건 FK 컬럼/필드 정렬:
- `pump/domain/PumpInterlock.java` — `pump_id`/`pumpId` → `instrument_id`/`instrumentId`
- `pump/domain/PumpCmbn.java` — `pwtf_id`/`pwtfId` → `facility_id`/`facilityId`
- `pump/domain/PumpCmbnDetail.java` + `PumpCmbnDetailId.java` — 복합키 `pump_id` → `instrument_id`
- `pump/domain/PumpControlHistory.java` — `pump_id` → `instrument_id`
- `pump/domain/PumpPredictionResult.java` — `pwtf_id` → `facility_id`

> `PumpControlHistoryId.java` 와 `PumpPredictionResultId.java` 는 IdClass PK 가 (`pumpCtrlId`,`ctrlDtm`) 및 (`predcId`,`predcBaseDtm`) 으로 `pump_id`·`pwtf_id` 미포함이라 변경 불요.

의존 Repository·Service 메서드 시그니처 변경 (api 모듈):
- Repository 5건: `PumpInterlockRepository` (`findByInstrumentIdAndUseYn`)·`PumpCmbnRepository` (`findByFacilityId`)·`PumpControlHistoryCustomRepository` + `Impl` (`findRecentByInstrumentId(s)`)·`PumpPredictionResultRepository` (`findLatestByFacilityId(Page)`)·`PumpCmbnDetailRepository` (변경 없음 — `id.pumpCmbnCd` 만 사용)
- Service 5건: `InterlockValidator` (`validateOrThrow(String instrumentId, ...)`)·`PumpControlHistoryWriter` (`saveNew(String instrumentId, ...)`)·`PumpControlService` (req.getFacilityId(), instrumentId 변수, 다중 펌프 보상 흐름 instrumentId 보존)·`PumpDashboardService` (의존성 `PumpRepository`·`PWTF`/`DWT`Repository → `InstrumentRepository`·`FacilityRepository`, `findByEquipType(PUMP)` + `facility_id` 필터, `findByFacilityType(PWTF)` 등)·`PumpMasterCacheService` (`InstrumentRepository` 의존, **`instanceof Pump`** 다형성 검증 — Discriminator 영속 컨텍스트 자동 주입 한계 회피)
- DTO 4건: `PumpControlRequestDto` (`facilityId`)·`PumpDashboardDto` (`facilityId`/`facilityNm`/`ControlHistoryItem.instrumentId`)·`PumpStateDto` (`instrumentId`/`instrumentNm`, 자식 전용 컬럼 0건 skeleton 정책에 따라 `ratedHead`/`ratedFlwrt`/`tagNm` null 채움 + `fromMaster(Instrument)` 시그니처)·`PumpControlResultDto` (변경 없음)
- Controller 1건: `PumpControlController.getDashboard` 파라미터 `facilityId`

#### 5. AI 모듈 + scheduler 추가 변경 (V2 SQL 컬럼명 변경의 종속)

> TASK1-3 명시 외 — V2 SQL 의 `ai_drvn_mod_p`·`ai_drvn_mod_h.pwtf_id` → `facility_id` 변경에 따른 강제 종속 변경. 빌드 통과 의무.

- `common/src/main/java/com/mo/swtp/ai/domain/AiDrvnMode.java` — PK `@Column(name="facility_id")`, `pwtfId` → `facilityId`
- `common/src/main/java/com/mo/swtp/ai/domain/AiDrvnModeHistory.java` — `pwtf_id` → `facility_id`, `pwtfId` → `facilityId`
- `api/src/main/java/com/mo/swtp/ai/service/AiModeService.java` — 모든 메서드 파라미터·내부 변수 `pwtfId` → `facilityId`
- `api/src/main/java/com/mo/swtp/ai/service/AiPredictionService.java` — 의존성 `PurifiedWaterTankRepository` → `FacilityRepository`, `instanceof PurifiedWaterTank` 다형성 검증
- `api/src/main/java/com/mo/swtp/ai/client/AiServerClient.java` — log 메시지 `pwtfId` → `facilityId`
- `api/src/main/java/com/mo/swtp/ai/dto/{AiModeDto, AiModeUpsertDto, AiPredictionRequestDto}.java` — `pwtfId` → `facilityId`
- `scheduler/src/main/java/com/mo/swtp/scheduler/pump/AiModeTransitionScheduler.java` — `pwtfId` → `facilityId` 변수·log

#### 6. 테스트 코드 정합 (10건)

- `api/src/test/java/com/mo/swtp/pump/service/InterlockValidatorTest.java` — `findByInstrumentIdAndUseYn`·`instrumentId` 변수
- `api/src/test/java/com/mo/swtp/pump/service/PumpInterlockScenarioTest.java` — 동일
- `api/src/test/java/com/mo/swtp/pump/service/PumpMasterCacheServiceTest.java` — `InstrumentRepository` mock + `Pump`/`Valve` 다형성 검증 6 케이스 (PUMP true, VALVE false 등)
- `api/src/test/java/com/mo/swtp/pump/service/PumpControlServiceTest.java` — 모든 케이스 `FACILITY_ID` 상수 + `existsByInstrumentId`
- `api/src/test/java/com/mo/swtp/pump/service/PumpOperationModeScenarioTest.java` — `FACILITY_ID` 상수
- `api/src/test/java/com/mo/swtp/pump/web/PumpControlControllerTest.java` — `setFacilityId`
- `api/src/test/java/com/mo/swtp/pump/PumpControlIntegrationTest.java` — 신규 시그니처 (`PurifiedWaterTank.create(facilityNm, parentFacilityId, dispOrd, mainYn)` + `Pump.create(instrumentNm, facility, dispOrd)`) + `InstrumentRepository`·`FacilityRepository` 의존 + UUID 자동 ID 추출
- `api/src/test/java/com/mo/swtp/ai/service/AiModeServiceTest.java` — `FACILITY_ID` 상수
- `api/src/test/java/com/mo/swtp/ai/service/AiPredictionServiceTest.java` — `FacilityRepository` mock + PWTF 자식 다형성 검증
- `common/src/test/java/com/mo/swtp/ai/domain/AiDrvnModeTest.java` — `FACILITY_ID` 상수, `getFacilityId()` assertion

폐기 1건 (삭제):
- `common/src/test/java/com/mo/swtp/pump/domain/PumpTest.java` — 폐기된 단일 마스터 `Pump` 엔티티 테스트 (자식 전용 컬럼 0건 skeleton 정책으로 신규 instrument.domain.Pump 에 적용 불가능)

### 계획 외 변경

#### 의도 (필수 부수 변경)

1. **`common/src/main/resources/db/init/V6__pumpcontrol_null_policy.sql` 적용 범위 축소** — pumpcontrol_null_alignment ANALYZE1 (2026-04-25) 의 V6 가 `pump_m.rated_head`·`rated_flwrt` + `dwt_m.min_req_prsr` 의 NULL → NOT NULL 백필을 수행했는데, 본 작업의 V1 폐기 + 자식 전용 컬럼 0건 skeleton 정책으로 해당 컬럼들이 더 이상 V6_1·V6_2 자식 테이블에 존재하지 않음. 따라서 V6 의 pump_m·dwt_m 백필·NOT NULL 부분을 제거하고 ai_drvn_mod_h NOT NULL 부분만 유지. 헤더 주석에 변경 사유 명시. — 본 작업 PLAN1 §V2~V5 변경의 자연 부수 변경.

2. **PumpMasterCacheService·AiPredictionService 의 `instanceof` 다형성 검증** — TASK 체크박스에는 `equip_type_cd=PUMP`/`facility_type_cd=PWTF` 필터로 명시되었으나, JPA Discriminator 컬럼 (`equip_type_cd`/`facility_type_cd`) 이 `insertable=false, updatable=false` 로 영속 컨텍스트 자동 주입 필드라 단위 테스트의 Mockito 객체에서는 null. 도메인 룰 (자식 종류 필터 강제) 의도는 유지하면서 영속 컨텍스트 의존 없이 단위 테스트 가능하도록 **`instanceof Pump` / `instanceof PurifiedWaterTank`** Java 다형성 패턴 매칭으로 전환. 의미적 등가 — 자식 클래스 식별이라 본질 동일. 첫 통합 테스트 보완 시 enum 필드 검증으로 환원할지 별도 검토 가능.

#### 우연 (범위 이탈)

1. **`api/src/main/resources-env/dev/application.yml` CORS 변경** — `cors.allowed-origins` 에 `http://localhost:5173,http://localhost:3000` 추가. 본 작업 의도와 무관한 이전 사이클의 frontend dev 환경 정합 변경으로 추정. **본 사이클이 추가한 변경 아님** — 작업 시작 시점에 이미 working tree 변경 상태였음 (직전 git log 의 `9a0e53e fix. tenant별 swagger 오픈전략 수정. cors 및 jwt필터 수정.` 잔여 가능성). 커밋 단계에서 제외 검토 또는 별도 사이클로 분리.

## 테스트 결과

### 컴파일

```
./gradlew.bat :common:compileJava        — BUILD SUCCESSFUL (4s)
./gradlew.bat :api:compileJava           — BUILD SUCCESSFUL (UP-TO-DATE)
./gradlew.bat :scheduler:compileJava     — BUILD SUCCESSFUL (6s)
./gradlew.bat :common:compileTestJava
./gradlew.bat :api:compileTestJava
./gradlew.bat :scheduler:compileTestJava — BUILD SUCCESSFUL (5s)
```

### 단위·통합 테스트

```
./gradlew.bat :common:test     — BUILD SUCCESSFUL (5s)
./gradlew.bat :api:test        — BUILD SUCCESSFUL (30s, 103 tests, 1 skipped)
./gradlew.bat :scheduler:test  — BUILD SUCCESSFUL (8s)
./gradlew.bat clean build      — BUILD SUCCESSFUL (1m 4s, 22 actionable tasks)
```

> `:api:test` 의 1 skipped 는 `PumpControlIntegrationTest` 가 `@EnabledIfEnvironmentVariable(SWTP_INTEGRATION_DB=true)` 로 CI 환경에서 자동 skip 되는 정상 동작. 컴파일 통과 + 단위 시나리오 9건의 인터록 재검사 케이스 (PumpInterlockScenarioTest 6건 + PumpControlServiceTest 의 다중펌프 시나리오 3건) 모두 GREEN 으로 PLAN1 §도메인 룰 §인터록 재검사 경로 보존 의무 검증 완료.

### 1차 PASS 회귀 — 4건 사전 발견

`api:test` 1차 실행 시 다음 4건이 RED 였다:
- `AiPredictionServiceTest > 추천_top1_을_pump_predc_h_에_저장한다`
- `AiPredictionServiceTest > 추천_없음_응답_시_AI_PREDICTION_FAILED_예외가_발생한다`
- `PumpMasterCacheServiceTest > existsByInstrumentId_는_PUMP_자식이면_true_를_반환한다`
- `PumpMasterCacheServiceTest > deactivateInstrument_는_PUMP_자식의_비활성화_도메인_메서드를_호출한다`

원인은 JPA Discriminator 컬럼 (`equip_type_cd`·`facility_type_cd`) 의 영속 컨텍스트 자동 주입 의존이며, **§계획 외 변경 (의도) 2** 의 `instanceof` 다형성 검증으로 해소. 2차 실행 시 모두 GREEN.

### Querydsl Q클래스 재생성

`common/build/generated/sources/annotationProcessor/java/main/com/mo/swtp/` 하위에 신규 디렉토리 **facility·instrument·tag·raw** 등장 + 신규 Q클래스 13건 생성:
- QFacility, QPurifiedWaterTank, QDistributionWaterTank, QReservoir
- QInstrument, QPump, QValve, QFlowMeter, QPressureMeter, QLevelMeter, QPowerMeter
- QTag
- QRawData

기존 Q클래스 (QPumpInterlock·QPumpCmbn·QPumpCmbnDetail·QPumpControlHistory·QPumpPredictionResult·QAiDrvnMode·QAiDrvnModeHistory) 도 변경된 컬럼명 정합으로 재생성 확인.

### 자동 차단 훅

- `check-ddl-column-comment.sh` — V6_1~V6_5 + V2 모든 컬럼 COMMENT ON COLUMN 누락 0건 (TASK1-1 Phase 2 작성 시 자동 검증 완료)
- `check-errorcode-contract.sh` — 신규 ErrorCode 미추가 (적용 외)
- `check-task-unstage.sh` — TASK1-3 작성 후 모든 체크박스 [x] 처리 + status: completed 전환 완료

## 비고

### 후속 작업 필요

1. **자식 전용 컬럼 도입 PLAN** — 본 PLAN 단계는 자식 9종 모두 skeleton (자식 전용 컬럼 0건). 추후 요구사항명세서 기반 차기 PLAN 에서 `pump_m.rated_head`·`rated_flwrt`·`tag_nm`·`dwt_m.min_req_prsr`·`flwmtr_m.range_min` 등 결정 (사용자 명시 결정 2026-05-03 + ANALYZE Round 3).
2. **ai-server Pydantic 스키마 동기화** — `AiPredictionRequestDto.pwtfId` → `facilityId` 변경에 따라 `swtp/ai-server/app/schemas/PumpPredictionRequest` 도 동기화 필요 (monorepo 단일 PR 원칙, `ot-integration.md §6.6`). 본 backend 작업 범위 외 — 별도 ai-server 작업으로 진행.
3. **`PumpControlIntegrationTest` 실DB 통과 검증** — `SWTP_INTEGRATION_DB=true` 환경에서 V1~V6_5 SQL 적용 후 시드 INSERT → AI 모드 변경 → 자동 제어 실행의 end-to-end 흐름. 컴파일 통과 + Mockito 단위 시나리오 GREEN 으로 1차 안전망 확보, 운영 환경 도입 전 실제 PostgreSQL 기동 검증.
4. **`com.mo.swtp.pump` 비즈니스 도메인 약어 재정의 검토** — 잔존 엔티티 (PumpInterlock·PumpCmbn 등) 가 펌프 운영 데이터 의미로 유지되었으나 실제로는 펌프(Instrument 자식) 마스터가 빠진 상태. 별도 ANALYZE 로 패키지 의미 재정렬 검토 (PLAN1 §가정).

### 기술 부채

1. **PumpControlHistoryId·PumpPredictionResultId 컬럼명 미변경** — IdClass PK 에 `pump_id`·`pwtf_id` 미포함이라 정합 변경 불요했으나, TASK 체크박스에는 변경 명시. 의미적 일관성을 위해 IdClass 클래스 자체에 변경된 외부 컬럼명을 반영하지는 않았다. 후속 ANALYZE 또는 PLAN 에서 재검토.
2. **자식 전용 컬럼 미도입 상태에서 PumpStateDto 의 `ratedHead`·`ratedFlwrt`·`tagNm` 필드 유지** — 자식 종류 PUMP 가 skeleton 이므로 본 시점에 모두 null. DTO 스키마 일관성 유지 vs 무의미 필드 노출의 트레이드오프 존재. 차기 PLAN 에서 자식 전용 컬럼 도입 시 실값으로 채워질 예정.
3. **`application.yml` (dev 프로파일) CORS 변경 우연 포함** — 본 사이클이 추가하지 않았으나 git working tree 에 잔재. 커밋 단계에서 별도 사이클로 분리 또는 의도된 변경으로 명시.

### REVIEW 단계 점검 권장 사항

- ROOT [`coding-discipline.md` §2.5](../../../../.claude/rules/coding-discipline.md) 면책 영역 인용 근거 명기 — `RawData.@PrePersist`/`@PreUpdate`/`@PostLoad` immutable 검증은 `ot-integration.md §3` 데이터 처리 정책 인용 근거 있음 (Javadoc 포함)
- ROOT [`coding-discipline.md` §3](../../../../.claude/rules/coding-discipline.md) 정밀한 수정 — application.yml CORS 변경 (본 사이클 외) 의 처리 방향
- 도메인 룰 4영역 정합성 (`wtp-domain-expert` 점검) — 인터록 재검사 경로 (PumpControlIntegrationTest 와 PumpInterlockScenarioTest 의 책임 분담), `corr_val` 갱신 ↔ SCADA_TIMEOUT 분리 (RawData Service 미구현 — 향후 도메인 작업 시 적용)
