---
status: completed
created: 2026-05-08
updated: 2026-05-11
---
# 송수펌프제어분석 — TASK1-1 (데이터 계층)

## 관련 계획
- [PLAN1](../../../plan/20260508/송수펌프제어분석/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 데이터 계층](TASK1-1.md) (현재 파일)
- [TASK1-2 애플리케이션 계층](TASK1-2.md)
- [TASK1-3 통합 검증·회귀](TASK1-3.md)

## 묶음 범위

enum 추가 (PRSF·OPS·VOI) + PressureBoosterStation 신규 자식 엔티티 + Pump/DistributionWaterTank 자식 컬럼 도입 + 마이그레이션 SQL V8_1~V8_4 + 정적 팩토리 변경 + common 모듈 단위 테스트.

> `tag_m.unit_cd` 단위 표시는 `TagMeasurementType` enum 속성으로 관리한다 (사용자 결정 — 2026-05-08). 본 사이클은 V8_5 (`tag_m.unit_cd` % COMMENT 갱신) 를 미실행하며, `tag_m.unit_cd` 컬럼 자체 폐기는 별도 사이클 범위 (마이그레이션 + JPA + standard-terms.md 폐기 이력 등록).

본 묶음 완료 후 `./gradlew.bat :common:build` BUILD SUCCESSFUL 이 보장되어야 TASK1-2 진입 가능 (Repository·Service 가 본 엔티티·enum 의존).

---

## Phase

### Phase 1: enum 추가

- [x] `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityType.java` 에 `PRSF` 값 추가 (Javadoc: "가압장 (Pressure Booster Station Facility)") → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL + grep PRSF FacilityType.java 매칭
- [x] `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` 에 `OPS` 값 추가 (Javadoc: "펌프 가동상태 — Operation Status, on/off DI 신호. 결측 시 즉시 BAD 격상, ot-integration.md §3 참조") → 검증: grep OPS TagMeasurementType.java 매칭 + Javadoc 인용 근거 ot-integration.md §3 포함 (태그관리 ANALYZE1 안건 6 사이클에서 흡수 — 2026-05-08)
- [x] `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` 에 `VOI` 값 추가 (Javadoc: "밸브 개도율 — Valve Opening Indicator, % 단위. 결측 시 Hold Last Value 적용, ot-integration.md §3 참조") → 검증: grep VOI TagMeasurementType.java 매칭 + Javadoc 인용 근거 포함 (태그관리 ANALYZE1 안건 6 사이클에서 흡수 — 2026-05-08)

### Phase 2: 신규 자식 엔티티 (PressureBoosterStation)

- [x] `common/src/main/java/com/mo/swtp/facility/domain/PressureBoosterStation.java` 신규 생성 — `@Entity @Table(name="prsf_m") @DiscriminatorValue("PRSF")` + Facility 상속 (skeleton) + 정적 팩토리 `create(facilityNm, parentFacilityId, dispOrd, mainYn)` → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL + grep "@DiscriminatorValue\(\"PRSF\"\)" 매칭
- [x] `common/src/test/java/com/mo/swtp/facility/domain/PressureBoosterStationTest.java` 신규 작성 — Mockito 단위 테스트 5건 (정적 팩토리 호출 / 부모 PK 자동 상속 검증 / facility_type_cd 자동 매핑 / main_yn=Y 분기 / main_yn=N 분기) → 검증: ./gradlew.bat :common:test --tests PressureBoosterStationTest 5건 GREEN

### Phase 3: 자식 엔티티 컬럼 도입 (Pump·DistributionWaterTank)

- [x] `common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` 변경 — `rated_head` (`@Column(name="rated_head", nullable=false, precision=15, scale=4)` BigDecimal) · `rated_flwrt` (동일 스펙) · `tag_nm` (`@Column(name="tag_nm", length=50)` String, NULL 허용) 3건 추가 + Javadoc → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL + grep rated_head Pump.java 매칭
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` 정적 팩토리 `create` 시그니처 확장 (ratedHead·ratedFlwrt·tagNm 3 파라미터 추가, NOT NULL 2건은 Objects.requireNonNull 검증) → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL
- [x] `common/src/test/java/com/mo/swtp/instrument/domain/PumpSelfColumnsTest.java` 신규 작성 — Mockito 단위 테스트 3건 (rated_head NOT NULL 검증 / rated_flwrt NOT NULL 검증 / tag_nm NULL 허용 검증) → 검증: ./gradlew.bat :common:test --tests PumpSelfColumnsTest 3건 GREEN
- [x] `common/src/main/java/com/mo/swtp/facility/domain/DistributionWaterTank.java` 변경 — `min_req_prsr` (`@Column(name="min_req_prsr", nullable=false, precision=15, scale=4)` BigDecimal) 1건 추가 + Javadoc (단위 kgf/cm²) → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL + grep min_req_prsr DistributionWaterTank.java 매칭
- [x] `common/src/main/java/com/mo/swtp/facility/domain/DistributionWaterTank.java` 정적 팩토리 `create` 시그니처 확장 (minReqPrsr NOT NULL 파라미터 추가, Objects.requireNonNull 검증) → 검증: ./gradlew.bat :common:compileJava BUILD SUCCESSFUL

### Phase 4: 마이그레이션 SQL — V8_1 (PRSF skeleton + 복합 인덱스)

- [x] `common/src/main/resources/db/init/V8_1__facility_m_prsf_skeleton.sql` 신규 작성 — `CREATE TABLE prsf_m (facility_id VARCHAR(36) PRIMARY KEY REFERENCES facility_m(facility_id))` + 컬럼 COMMENT 1건 + `CREATE INDEX CONCURRENTLY idx_facility_m_type_parent ON facility_m (facility_type_cd, parent_facility_id)` → 검증: psql 로컬 적용 후 \d+ prsf_m 출력에 facility_id 컬럼 + COMMENT 표시 + \di idx_facility_m_type_parent 인덱스 확인
- [x] `common/src/main/resources/db/init/V8_1__facility_m_prsf_skeleton.sql` 의 모든 컬럼에 COMMENT 작성 (check-ddl-column-comment.sh 훅 차단 회피) → 검증: ./gradlew.bat :common:processResources 후 hooks 실행 PASS

### Phase 5: 마이그레이션 SQL — V8_2 (Pump 자식 컬럼 3단계 무중단)

- [x] `common/src/main/resources/db/init/V8_2__pump_m_self_columns.sql` 신규 작성 — 1단계 (NULL 허용 컬럼 3건 추가 `ALTER TABLE pump_m ADD COLUMN rated_head NUMERIC(15,4); ...`) + 2단계 (배치 백필 `UPDATE pump_m SET rated_head=0.00, rated_flwrt=0.00 WHERE rated_head IS NULL`) + 3단계 (NOT NULL 전환 `ALTER TABLE pump_m ALTER COLUMN rated_head SET NOT NULL; ALTER COLUMN rated_flwrt SET NOT NULL`) + 모든 신규 컬럼 COMMENT → 검증: psql 로컬 적용 후 \d+ pump_m 출력에 rated_head·rated_flwrt NOT NULL + tag_nm NULL + COMMENT 모두 표시
- [x] `common/src/main/resources/db/init/V8_2__pump_m_self_columns.sql` 의 db/indexing-and-migration.md §2 3단계 무중단 마이그레이션 정책 인용 주석 1건 포함 → 검증: grep "indexing-and-migration.md §2" V8_2__pump_m_self_columns.sql 매칭

### Phase 6: 마이그레이션 SQL — V8_3 (DWT 자식 컬럼 3단계 무중단)

- [x] `common/src/main/resources/db/init/V8_3__dwt_m_self_columns.sql` 신규 작성 — 1단계 (NULL 허용 ADD COLUMN `min_req_prsr NUMERIC(15,4)`) + 2단계 (배치 백필 `UPDATE dwt_m SET min_req_prsr=0.0000 WHERE min_req_prsr IS NULL`) + 3단계 (NOT NULL 전환) + COMMENT (단위 kgf/cm²) → 검증: psql 로컬 적용 후 \d+ dwt_m 출력에 min_req_prsr NOT NULL + COMMENT 표시

### Phase 7: 마이그레이션 SQL — V8_4 (pump_predc_h.pwtf_id → facility_id)

- [x] `common/src/main/resources/db/init/V8_4__pump_predc_h_facility_id.sql` 신규 작성 — 사전 검증 SQL `SELECT count(*) FROM pump_predc_h WHERE pwtf_id IS NOT NULL` (백필 완료 확인) + `SET lock_timeout = '5s'` + 1단계 (`ALTER TABLE pump_predc_h ADD COLUMN facility_id VARCHAR(36)` + COMMENT) + 2단계 (`UPDATE pump_predc_h SET facility_id = pwtf_id WHERE facility_id IS NULL`) + 3단계 (`ALTER TABLE pump_predc_h ALTER COLUMN facility_id SET NOT NULL`) + 4단계 (`ALTER TABLE pump_predc_h DROP COLUMN pwtf_id`) → 검증: psql 로컬 적용 후 \d+ pump_predc_h 출력에 facility_id NOT NULL + COMMENT 표시 + pwtf_id 컬럼 부재 (※ idempotent — V2 가 이미 facility_id 로 정렬된 신규 설치 환경에서는 RAISE NOTICE 후 SKIP)
- [x] `common/src/main/resources/db/init/V8_4__pump_predc_h_facility_id.sql` 의 운영 시간 외 (00:00~06:00) 실행 안내 주석 + `rawdata_1m_h` INSERT 스케줄러 일시 정지 안내 주석 1건 포함 → 검증: grep "00:00~06:00" V8_4__pump_predc_h_facility_id.sql 매칭 + grep "스케줄러 일시 정지" V8_4__pump_predc_h_facility_id.sql 매칭

### Phase 8: BUILD 검증

- [x] `./gradlew.bat :common:build` 실행 → 검증: BUILD SUCCESSFUL + 단위 테스트 (PressureBoosterStationTest 5건 + PumpSelfColumnsTest 3건) 모두 GREEN (2026-05-11 검증 — BUILD SUCCESSFUL in 14s, PressureBoosterStationTest tests=5 failures=0, PumpSelfColumnsTest tests=3 failures=0)

---

## 산출물

본 분할 완료 후 TASK1-2 (애플리케이션 계층) 진입. RESULT 작성은 TASK1-3 완료 후 통합으로 1건 작성:

- [결과 (작성 예정)](../../../results/20260508/송수펌프제어분석/RESULT1.md)
