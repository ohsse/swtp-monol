---
status: completed
created: 2026-04-25
updated: 2026-04-25
---
# 송수펌프 제어 — 데이터 계층 (common 모듈 + DDL)

## 관련 계획
- [계획안](../../../plan/20260425/pumpcontrol/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 데이터 계층 (현재 파일)](TASK1-1.md)
- [TASK1-2 애플리케이션 계층 (api 모듈)](TASK1-2.md)
- [TASK1-3 인프라·검증 (scheduler·테스트·빌드)](TASK1-3.md)

## Phase

### Phase 1: enum 신규 작성 (common 모듈)

- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpControlDivision.java` 생성 — `MANUAL` / `AUTO` (`@Enumerated(EnumType.STRING)` 매핑 대상)
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpControlResult.java` 생성 — `SUCCESS` / `WAITING` / `FAIL`
- [x] `common/src/main/java/com/mo/swtp/ai/domain/AiDrvnModeType.java` 생성 — `AI` / `AI_RECOMD` / `AI_ANLS` (사용자 의도 — `ai_drvn_mod` 컬럼 매핑)
- [x] `common/src/main/java/com/mo/swtp/ai/domain/AiSystemModeCode.java` 생성 — `MANUAL`(0) / `AI_AUTO`(1) / `SEMI_AUTO`(2) (시스템 상태 — `ai_mode_cd` 컬럼 매핑, name() 가 그대로 enum 값)
- [x] `common/src/main/java/com/mo/swtp/ai/domain/TransitionReason.java` 생성 — `USER_SELECT` / `SCADA_TIMEOUT` / `MANUAL_EXPIRE` / `OUTBOUND_FAIL`

### Phase 2: 마스터 엔티티 4건 (common 모듈, `Persistable<String>` 구현 필수)

- [x] `common/src/main/java/com/mo/swtp/pump/domain/Pump.java` 생성 — `pump_m` 매핑. `pump_id`(PK, 외부할당) · `pump_nm` · `pwtf_id`(FK) · `rated_head` · `rated_flwrt` · `tag_nm`(VARCHAR(50) NOT NULL `DOM_TAG_NM_50`) · `use_yn`(`YnType` enum). `BaseEntity` 상속, `Persistable<String>` 구현(`getId()` override)
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PurifiedWaterTank.java` 생성 — `pwtf_m` 매핑. `pwtf_id`(PK, 외부할당) · `pwtf_nm`. `Persistable<String>` 구현
- [x] `common/src/main/java/com/mo/swtp/pump/domain/DistributionWaterTank.java` 생성 — `dwt_m` 매핑. `dwt_id`(PK) · `dwt_nm` · `min_req_prsr`. `Persistable<String>` 구현
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpCmbn.java` 생성 — `pump_cmbn_m` 매핑. `pump_cmbn_cd`(PK) · `pwtf_id`(FK) · `pump_cmbn_nm`. `Persistable<String>` 구현

### Phase 3: 상세·명세 엔티티 3건 (common 모듈)

- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpCmbnDetailId.java` 생성 — `(pump_cmbn_cd, pump_id)` 복합 PK 클래스 (`@Embeddable`, `equals`/`hashCode`)
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpCmbnDetail.java` 생성 — `pump_cmbn_d` 매핑. `@EmbeddedId` PumpCmbnDetailId · `ord`. 마스터 FK 2건 (`pump_cmbn_cd`→`pump_cmbn_m`, `pump_id`→`pump_m`)
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpInterlock.java` 생성 — `pump_interlock_p` 매핑 (stub). `pump_interlock_id`(PK, 외부할당) · `pump_id`(FK) · `sensor_tag` · `min_val` · `max_val` · `use_yn`. `Persistable<String>` 구현
- [x] `common/src/main/java/com/mo/swtp/ai/domain/AiDrvnMode.java` 생성 — `ai_drvn_mod_p` 매핑. `pwtf_id`(PK, FK→`pwtf_m`) · `ai_drvn_mod`(`AiDrvnModeType` enum) · `ai_mode_cd`(`AiSystemModeCode` enum) · `expire_dtm` · `last_rcv_dtm`. `Persistable<String>` 구현. **변경 메서드 분리** — `changeUserIntent(AiDrvnModeType)` 와 `forceSystemMode(AiSystemModeCode, TransitionReason)` 별도 (Service 계층 권한 분리 강제)

### Phase 4: 시계열 파티션 엔티티 3건 (common 모듈, `@ManyToOne` 미사용 — `@Column` 단독)

- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpControlHistoryId.java` 생성 — `(pump_ctrl_id, ctrl_dtm)` 복합 PK 클래스 (`@IdClass` 패턴 — 시퀀스 자동생성 PK 호환)
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpControlHistory.java` 생성 — `pump_ctrl_h` 매핑. `@IdClass` · `pump_id`(컬럼 단독) · `ctrl_div`(`PumpControlDivision` enum) · `ctrl_rslt`(`PumpControlResult` enum) · `ai_drvn_mod`. `@GeneratedValue(SEQUENCE, generator="seq_pump_ctrl_id")` allocationSize=100. **`@ManyToOne` 미사용 (파티션 마스터 FK 금지 — `db-partitioning-and-retention.md §1`)**
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpPredictionResultId.java` 생성 — `(predc_id, predc_base_dtm)` 복합 PK 클래스
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpPredictionResult.java` 생성 — `pump_predc_h` 매핑. `@IdClass` · `pwtf_id`(컬럼 단독) · `predc_dtm` · `pump_cmbn_cd`(컬럼 단독) · `predc_elpwr_amt` · `predc_flwrt` · `predc_prsr` · `ai_drvn_mod`. `seq_predc_id` allocationSize=100. **`@ManyToOne` 미사용**
- [x] `common/src/main/java/com/mo/swtp/ai/domain/AiDrvnModeHistoryId.java` 생성 — `(ai_drvn_mod_h_id, rgstr_dtm)` 복합 PK 클래스
- [x] `common/src/main/java/com/mo/swtp/ai/domain/AiDrvnModeHistory.java` 생성 — `ai_drvn_mod_h` 매핑. `@IdClass` · `pwtf_id`(컬럼 단독) · `prev_ai_drvn_mod` · `new_ai_drvn_mod` · `prev_ai_mode_cd` · `new_ai_mode_cd` · `transition_reason`(`TransitionReason` enum). `seq_ai_drvn_mod_h_id` allocationSize=**10** (DBA 권고 — 단건 트랜잭션 gap 최소화). **`@ManyToOne` 미사용**

### Phase 5: 엔티티 단위 테스트 (common 모듈)

- [x] `common/src/test/java/com/mo/swtp/pump/domain/PumpTest.java` 생성 — `Pump.create()` 정적 팩토리, `Persistable<String>` `getId()` / `isNew()` 동작, `tag_nm` NOT NULL 검증
- [x] `common/src/test/java/com/mo/swtp/ai/domain/AiDrvnModeTest.java` 생성 — `changeUserIntent()` 호출 시 `ai_mode_cd` 불변, `forceSystemMode()` 호출 시 `ai_drvn_mod` 불변 검증

### Phase 6: DDL 스크립트 작성 (`common/src/main/resources/db/init/`)

- [x] `common/src/main/resources/db/init/V1__pumpcontrol_master_tables.sql` 생성 — `pump_m`·`pwtf_m`·`dwt_m`·`pump_cmbn_m`·`pump_cmbn_d`·`pump_interlock_p`·`ai_drvn_mod_p` CREATE TABLE. 마스터 FK 6건(`pump_m.pwtf_id`→`pwtf_m`, `pump_cmbn_m.pwtf_id`→`pwtf_m`, `pump_cmbn_d` 두 컬럼, `pump_interlock_p.pump_id`→`pump_m`, `ai_drvn_mod_p.pwtf_id`→`pwtf_m`) 정상 생성
- [x] `common/src/main/resources/db/init/V2__pumpcontrol_partition_tables.sql` 생성 — `pump_ctrl_h`·`pump_predc_h`·`ai_drvn_mod_h` CREATE TABLE PARTITION BY RANGE. 각 컬럼 정의 옆에 "-- 파티션 테이블의 마스터 참조 — FK 금지 (db-partitioning-and-retention.md §1)" 주석 명시
- [x] `common/src/main/resources/db/init/V3__pumpcontrol_partition_initial_6months.sql` 생성 — 3개 파티션 테이블 각각 6개월치 초기 파티션 생성 (2026-04 ~ 2026-09 = 18개)
- [x] `common/src/main/resources/db/init/V4__pumpcontrol_indexes.sql` 생성 — 인덱스 생성. `pump_m_pwtf_id_idx`, `pump_cmbn_m_pwtf_id_idx`, 파티션별 `pump_ctrl_h_{YYYYMM}_pump_dtm_idx` (B-Tree) + `pump_ctrl_h_{YYYYMM}_brin_idx` (BRIN), `pump_predc_h_{YYYYMM}_pwtf_dtm_idx`, `ai_drvn_mod_h_{YYYYMM}_pwtf_dtm_idx`. `CREATE INDEX CONCURRENTLY` 적용 권고
- [x] `common/src/main/resources/db/init/V5__pumpcontrol_sequences.sql` 생성 — `seq_pump_ctrl_id` (INCREMENT 100) · `seq_predc_id` (INCREMENT 100) · `seq_ai_drvn_mod_h_id` (INCREMENT 10) 시퀀스 3건. 시작값 1
- [x] `common/src/main/resources/db/init/README.md` 생성 — DDL 스크립트 적용 순서·운영 절차·파티션 수동 복구 SQL 예시 (PLAN §4 장애 복구 절차) 문서화

### Phase 7: QClass 재생성 검증

- [x] `./gradlew.bat :common:compileJava` 실행하여 Querydsl QClass 재생성 성공 확인
- [x] `./gradlew.bat :common:test` 실행하여 Phase 5 단위 테스트 통과 확인 (PumpTest 6 + AiDrvnModeTest 7 = 13 케이스 통과)

## 산출물
- [결과](../../../results/20260425/pumpcontrol/RESULT1.md)
