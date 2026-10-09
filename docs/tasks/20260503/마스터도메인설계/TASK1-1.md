---
status: completed
created: 2026-05-04
updated: 2026-05-04
---
# 마스터도메인설계 TASK1-1 — 정적 자산 (enum + DDL)

## 관련 계획
- [PLAN1](../../../plan/20260503/마스터도메인설계/PLAN1.md)

## 관련 분할 TASK
- TASK1-1 정적 자산 (enum + DDL) — 본 파일
- [TASK1-2 신규 엔티티 + Repository](TASK1-2.md)
- [TASK1-3 기존 패키지 분리 이관 + 빌드 검증](TASK1-3.md)

## Phase

### Phase 1: 신규 enum 5건

> 적용 룰: `entity-patterns.md` §여부(Y/N) 필드 패턴 — `@Enumerated(EnumType.STRING)` 정합 (DB VARCHAR 와 enum name 1:1 매핑). 패키지 위치는 `naming.md` §패키지 규칙 (도메인 응집).

- [x] `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityType.java` 작성 (PWTF·DWT·RSV 3개 값, public enum 형식) → 검증: grep PWTF DWT RSV 모두 매칭
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/enumtype/EquipType.java` 작성 (PUMP·VALVE·FLWMTR·PRSMTR·LVMTR·ELCMTR 6개 값) → 검증: grep 6개 값 모두 매칭
- [x] `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` 작성 (FRI·PRI·LEI·PWI·RMS 5개 값, ot-integration.md §3 측정 유형 코드 정합) → 검증: grep 5개 값 매칭
- [x] `common/src/main/java/com/mo/swtp/tag/domain/enumtype/IoCode.java` 작성 (INPUT·OUTPUT·BIDIR 3개 값, 2026-05-03 사용자 결정) → 검증: grep 3개 값 매칭
- [x] `common/src/main/java/com/mo/swtp/raw/domain/enumtype/QualityCode.java` 작성 (GOOD·BAD·UNCERTAIN 3개 값, ot-integration.md §3 SCADA QUALITY 정합) → 검증: grep 3개 값 매칭

### Phase 2: DDL — V1 폐기 + V6 신규 5건 + V2~V5 컬럼명 변경

> ⚠️ Flyway 명명 비고: 기존 V6 (V6__pumpcontrol_null_policy.sql, pumpcontrol_null_alignment ANALYZE1 산출물, 2026-04-25) 와 신규 V6_1~V6_5 는 Flyway 6.0+ underscore=dot 규칙으로 별개 마이그레이션 인식 (적용 순서 V6 → V6_1 → V6_2 → V6_3 → V6_4 → V6_5 정상). PLAN1.md §DB 설계 변경 명명 그대로 채택.
>
> 의무 사항 (PLAN1.md §Phase 2 정합):
> - V6_1~V6_5 모든 컬럼에 COMMENT ON COLUMN 의무 (db/indexing-and-migration.md §4 + check-ddl-column-comment.sh 훅)
> - V6_5 BRIN/복합 인덱스 4단계 DDL 순서 (CREATE TABLE PARTITION BY RANGE → CREATE INDEX BRIN → CREATE INDEX 복합 → CREATE TABLE PARTITION OF 자식)
> - V2 컬럼명 변경 시 기존 COMMENT 삭제 + 신 컬럼 라벨 재작성 의무

- [x] `common/src/main/resources/db/init/V1__pumpcontrol_master_tables.sql` 본문 SQL 0건으로 갱신 + redirect 주석 작성 (V6_1·V6_2·V6_3 신 위치 안내, PLAN1.md §DB 설계 변경 §V1 폐기 본문 그대로) → 검증: grep -E "DROP|CREATE|INSERT|ALTER" V1 파일 매칭 0건 + redirect 주석 라인 매칭 1건 이상
- [x] `common/src/main/resources/db/init/V6_1__facility_master_tables.sql` 신규 작성 (facility_m + 자식 3 pwtf_m·dwt_m·rsv_m + UNIQUE idx_facility_m_facility_nm + parent_facility_id self-FK NULL 허용 + 모든 컬럼 COMMENT ON COLUMN, BaseEntity 4 표준 라벨) → 검증: check-ddl-column-comment.sh 훅 미차단 + grep "facility_type_cd\|UNIQUE.*facility_nm\|parent_facility_id" 모두 매칭
- [x] `common/src/main/resources/db/init/V6_2__instrument_master_tables.sql` 신규 작성 (instrument_m + 자식 6 pump_m·valve_m·flwmtr_m·prsmtr_m·lvmtr_m·elcmtr_m + UNIQUE idx_instrument_m_facility_nm 복합 facility_id+instrument_nm + facility_id NOT NULL FK to facility_m + 모든 컬럼 COMMENT) → 검증: check-ddl-column-comment.sh 훅 미차단 + grep "UNIQUE.*facility_id.*instrument_nm\|REFERENCES facility_m" 모두 매칭
- [x] `common/src/main/resources/db/init/V6_3__pump_secondary_table_realign.sql` 신규 작성 (pump_cmbn_m·pump_cmbn_d·pump_interlock_p·ai_drvn_mod_p 의 pump_id → instrument_id, pwtf_id → facility_id 컬럼 변경 + FK 재설정 to instrument_m·facility_m + 기존 COMMENT 삭제 후 instrument_id·facility_id 라벨 재작성) → 검증: psql 명령으로 \d+ pump_cmbn_m 출력에 instrument_id 컬럼 등장 + pump_id 부재 확인
- [x] `common/src/main/resources/db/init/V6_4__tag_master_table.sql` 신규 작성 (tag_m + tag_srl_no PK 자연키 VARCHAR(50) DOM_TAG_SRL_NO_50 + instrument_id NOT NULL FK to instrument_m + idx_tag_m_instrument_id B-Tree + 모든 컬럼 COMMENT) → 검증: check-ddl-column-comment.sh 훅 미차단 + grep "PRIMARY KEY.*tag_srl_no\|REFERENCES instrument_m" 매칭
- [x] `common/src/main/resources/db/init/V6_5__rawdata_1m_h.sql` 신규 작성 — DDL 4단계 순서 의무 준수 (1. CREATE TABLE rawdata_1m_h ... PARTITION BY RANGE acq_dtm + rawdata_id BIGINT SEQUENCE allocationSize=100 + raw_val NUMERIC(15,4) NULL + corr_val NULL + quality_cd NOT NULL + tag_srl_no NOT NULL 논리 참조 시계열 → 마스터 FK 금지 + BaseEntity 4 컬럼 / 2. CREATE INDEX idx_rawdata_1m_h_brin USING BRIN(acq_dtm) / 3. CREATE INDEX idx_rawdata_1m_h_tag_time(tag_srl_no, acq_dtm DESC) / 4. CREATE TABLE rawdata_1m_h_YYYYMM PARTITION OF 6개월 선행) + 모든 컬럼 COMMENT → 검증: check-ddl-column-comment.sh 훅 미차단 + grep "PARTITION BY RANGE\|USING BRIN\|PARTITION OF" 모두 매칭 + grep -c "CREATE TABLE rawdata_1m_h_" 결과 6 이상
- [x] `common/src/main/resources/db/init/V2__pumpcontrol_partition_tables.sql` 컬럼명 변경 (pump_ctrl_h.pump_id → instrument_id, pump_predc_h.pump_id → instrument_id, pump_predc_h.pwtf_id → facility_id, ai_drvn_mod_h.pwtf_id → facility_id) + 기존 COMMENT 삭제 후 신 컬럼 라벨 재작성 → 검증: grep -E "pump_id|pwtf_id" V2 파일 매칭 0건 + grep "instrument_id\|facility_id" 매칭
- [x] `common/src/main/resources/db/init/V3__pumpcontrol_partition_initial_6months.sql` V2 변경 컬럼 의존 구문 동기화 → 검증: grep -E "pump_id|pwtf_id" V3 파일 매칭 0건
- [x] `common/src/main/resources/db/init/V4__pumpcontrol_indexes.sql` V2 변경 컬럼 의존 인덱스 정의 동기화 (idx_pump_ctrl_h_*·idx_pump_predc_h_*·idx_ai_drvn_mod_h_* 컬럼 참조 정합) → 검증: grep -E "pump_id|pwtf_id" V4 파일 매칭 0건
- [x] `common/src/main/resources/db/init/V5__pumpcontrol_sequences.sql` V2 변경 컬럼 의존 시퀀스 정의 동기화 (있다면) → 검증: grep -E "pump_id|pwtf_id" V5 파일 매칭 0건

## 산출물
- [결과](../../../results/20260503/마스터도메인설계/RESULT1.md)
