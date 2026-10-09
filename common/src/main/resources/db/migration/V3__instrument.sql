-- ============================================================================
-- instrument 도메인 DDL — 계측기 단일 마스터 + JPA JOINED 자식 6종 + 인덱스
-- ----------------------------------------------------------------------------
-- 본 파일은 com.mo.swtp.instrument 도메인의 사람이 읽는 SSOT 사본이다.
-- 운영본: common/src/main/resources/db/migration/V3__instrument.sql (양쪽 동시 갱신 의무).
--
-- 흡수 이력 (sql_관리포인트_통합 ANALYZE1, 2026-05-20 — 최종 결과 정렬):
--   - common/db/init/V6_2 (instrument_m + 자식 6종 PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR skeleton)
--   - common/db/init/V8_2 (pump_m rated_head NOT NULL + rated_flwrt NOT NULL — CREATE TABLE 인라인 정렬)
--   - common/db/init/V8_6 (pump_m.tag_nm 폐기 — V8_2 의 tag_nm 컬럼 미포함, 양방향 중복 제거)
--   - common/db/init/V8_5 (pump_m.oprtng_type_cd NOT NULL — CREATE TABLE 인라인 정렬)
--   - common/db/init/V9_3 (pump_m.drive_type_cd NOT NULL — CREATE TABLE 인라인 정렬)
--   - common/db/migration/V9_2 (instrument 부분 idx_instrument_m_facility_equip)
--
-- 자식 종류 6종:
--   PUMP (펌프, self 컬럼 4건) · VALVE · FLWMTR · PRSMTR · LVMTR · ELCMTR
--
-- 참조:
--   - .claude/rules/entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴
--   - .claude/rules/db/indexing-and-migration.md §1 인덱스 원칙 + §4 컬럼 COMMENT 의무화 정책
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 계측기 단일 마스터 (부모, JPA JOINED)
-- ----------------------------------------------------------------------------
CREATE TABLE instrument_m (
    instrument_id    VARCHAR(36)  NOT NULL,
    instrument_nm    VARCHAR(100) NOT NULL,
    equip_type_cd    VARCHAR(20)  NOT NULL,
    facility_id      VARCHAR(36)  NOT NULL,
    disp_ord         INTEGER      NOT NULL,
    use_yn           VARCHAR(1)   NOT NULL,
    rgstr_dtm        TIMESTAMP    NOT NULL,
    updt_dtm         TIMESTAMP    NOT NULL,
    rgstr_id         VARCHAR(50)  NOT NULL,
    updt_id          VARCHAR(50)  NOT NULL,
    PRIMARY KEY (instrument_id),
    CONSTRAINT fk_instrument_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id)
);
COMMENT ON TABLE instrument_m IS '계측기 단일 마스터 (JPA JOINED 부모, 자식 6종 PUMP·VALVE·FLWMTR·PRSMTR·LVMTR·ELCMTR)';
COMMENT ON COLUMN instrument_m.instrument_id   IS '계측기 ID (UUID 자동 생성 PK, DOM_ID_36 — Persistable 미구현, GenerationType.UUID)';
COMMENT ON COLUMN instrument_m.instrument_nm   IS '계측기명 (DOM_NAME_100 — UNIQUE 범위 (facility_id, instrument_nm) 복합, 사용자 식별 비즈니스 키)';
COMMENT ON COLUMN instrument_m.equip_type_cd   IS '장비 유형 코드 (DOM_CODE_20 — DiscriminatorColumn, EquipType enum 매핑 PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR)';
COMMENT ON COLUMN instrument_m.facility_id     IS '소속 시설 ID (DOM_ID_36 — 마스터 참조 FK to facility_m, NOT NULL)';
COMMENT ON COLUMN instrument_m.disp_ord        IS '표시 순서 (화면 표시 정렬 순번)';
COMMENT ON COLUMN instrument_m.use_yn          IS '사용 여부 (Y/N, DOM_YN — YnType enum 매핑)';
COMMENT ON COLUMN instrument_m.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN instrument_m.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN instrument_m.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN instrument_m.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- 복합 UNIQUE 인덱스 — 멀티테넌트 운영 정합 (선두 컬럼 facility_id 가 FK 조회도 커버)
CREATE UNIQUE INDEX idx_instrument_m_facility_nm ON instrument_m (facility_id, instrument_nm);

-- 자식 종류별 부분 조회 가속 — equip_type_cd='PUMP' 필터 (인터록 평가) 빈도 반영
CREATE INDEX idx_instrument_m_equip_type_cd ON instrument_m (equip_type_cd);

-- 복합 인덱스 (facility_id, equip_type_cd) — DWT 자식 계측기 조회 hot-path (V9_2)
CREATE INDEX idx_instrument_m_facility_equip
    ON instrument_m (facility_id, equip_type_cd);

-- ----------------------------------------------------------------------------
-- 2. 펌프 자식 (PUMP, JPA JOINED 자식) — 자식 컬럼 4건 인라인 정렬
--    (V8_2 rated_head/rated_flwrt + V8_5 oprtng_type_cd + V9_3 drive_type_cd, V8_6 tag_nm 제거 반영)
-- ----------------------------------------------------------------------------
CREATE TABLE pump_m (
    instrument_id   VARCHAR(36)     NOT NULL,
    rated_head      NUMERIC(15, 4)  NOT NULL,
    rated_flwrt     NUMERIC(15, 4)  NOT NULL,
    oprtng_type_cd  VARCHAR(20)     NOT NULL,
    drive_type_cd   VARCHAR(20)     NOT NULL,
    PRIMARY KEY (instrument_id),
    CONSTRAINT fk_pump_m__instrument
        FOREIGN KEY (instrument_id) REFERENCES instrument_m (instrument_id)
);
COMMENT ON TABLE pump_m IS '펌프 자식 (equip_type_cd=PUMP, JPA JOINED 자식 — 자식 컬럼 4건 rated_head·rated_flwrt·oprtng_type_cd·drive_type_cd)';
COMMENT ON COLUMN pump_m.instrument_id  IS '계측기 ID (DOM_ID_36 — instrument_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준)';
COMMENT ON COLUMN pump_m.rated_head     IS '정격 양정 (m, DOM_QTY_15_4 NOT NULL — 제조사 명판값, AI 예측 모델 정규화 인자)';
COMMENT ON COLUMN pump_m.rated_flwrt    IS '정격 유량 (m³/h, DOM_QTY_15_4 NOT NULL — 제조사 명판값, AI 예측 모델 정규화 인자)';
COMMENT ON COLUMN pump_m.oprtng_type_cd IS '펌프 조작유형 (DOM_CODE_20, AUTO_CAPABLE/SEMI_AUTO_CAPABLE — 펌프의 물리적 설계값, PumpOprtngType enum 매핑)';
COMMENT ON COLUMN pump_m.drive_type_cd  IS '구동 방식 코드 (DOM_CODE_20, INVERTER_DRIVE/RATED_DRIVE — 펌프 물리적 설계값, PumpDriveType enum 매핑)';

-- ----------------------------------------------------------------------------
-- 3. 밸브 자식 (VALVE, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE valve_m (
    instrument_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (instrument_id),
    CONSTRAINT fk_valve_m__instrument
        FOREIGN KEY (instrument_id) REFERENCES instrument_m (instrument_id)
);
COMMENT ON TABLE valve_m IS '밸브 자식 (equip_type_cd=VALVE, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton)';
COMMENT ON COLUMN valve_m.instrument_id IS '계측기 ID (DOM_ID_36 — instrument_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준)';

-- ----------------------------------------------------------------------------
-- 4. 유량계 자식 (FLWMTR, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE flwmtr_m (
    instrument_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (instrument_id),
    CONSTRAINT fk_flwmtr_m__instrument
        FOREIGN KEY (instrument_id) REFERENCES instrument_m (instrument_id)
);
COMMENT ON TABLE flwmtr_m IS '유량계 자식 (equip_type_cd=FLWMTR, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton)';
COMMENT ON COLUMN flwmtr_m.instrument_id IS '계측기 ID (DOM_ID_36 — instrument_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준)';

-- ----------------------------------------------------------------------------
-- 5. 압력계 자식 (PRSMTR, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE prsmtr_m (
    instrument_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (instrument_id),
    CONSTRAINT fk_prsmtr_m__instrument
        FOREIGN KEY (instrument_id) REFERENCES instrument_m (instrument_id)
);
COMMENT ON TABLE prsmtr_m IS '압력계 자식 (equip_type_cd=PRSMTR, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton)';
COMMENT ON COLUMN prsmtr_m.instrument_id IS '계측기 ID (DOM_ID_36 — instrument_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준)';

-- ----------------------------------------------------------------------------
-- 6. 수위계 자식 (LVMTR, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE lvmtr_m (
    instrument_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (instrument_id),
    CONSTRAINT fk_lvmtr_m__instrument
        FOREIGN KEY (instrument_id) REFERENCES instrument_m (instrument_id)
);
COMMENT ON TABLE lvmtr_m IS '수위계 자식 (equip_type_cd=LVMTR, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton)';
COMMENT ON COLUMN lvmtr_m.instrument_id IS '계측기 ID (DOM_ID_36 — instrument_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준)';

-- ----------------------------------------------------------------------------
-- 7. 전력량계 자식 (ELCMTR, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE elcmtr_m (
    instrument_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (instrument_id),
    CONSTRAINT fk_elcmtr_m__instrument
        FOREIGN KEY (instrument_id) REFERENCES instrument_m (instrument_id)
);
COMMENT ON TABLE elcmtr_m IS '전력량계 자식 (equip_type_cd=ELCMTR, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton)';
COMMENT ON COLUMN elcmtr_m.instrument_id IS '계측기 ID (DOM_ID_36 — instrument_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준)';
