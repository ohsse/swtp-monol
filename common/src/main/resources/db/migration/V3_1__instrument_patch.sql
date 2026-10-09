-- ============================================================================
-- V3_1 instrument 도메인 패치 — pump_ctrl_h 펌프 제어 이력 (월 RANGE 파티션) 재도입
-- ----------------------------------------------------------------------------
-- 본 패치는 제어이력 재도입 ANALYZE1·PLAN1 (2026-06-04) 의 마이그레이션이다.
--
-- 배경:
--   pump_ctrl_h 는 dev DB 에 월 파티션과 함께 물리 존재하나 2026-05-12 백지화로 엔티티·마이그레이션이
--   삭제된 고아 테이블(0행)이다. 기존 컬럼 구조·물리명·인덱스명·파티션명을 그대로 코드 자산화한다.
--   비즈니스 도메인 약어 ctrl 은 폐기 유지 — 물리명·컬럼명에 역사 흔적으로만 잔존.
--
-- 무중단/멱등성:
--   - 전 구문 IF NOT EXISTS — 기존 dev DB(테이블·시퀀스·인덱스·202604~202609 파티션 존재)에는 no-op.
--   - 신환경 부트스트랩 시 V3__instrument.sql 적용 후 본 패치로 pump_ctrl_h 전체 생성.
--   - 인덱스·파티션명은 dev DB 실측명과 동일 — 중복 생성 회피 (pump_ctrl_h_pkey/_instrument_dtm_idx/_brin_idx).
--
-- 정책:
--   - 시계열(_h) → 마스터 FK 금지 (db/partitioning-and-retention.md §1) — instrument_id 논리 참조.
--   - ai_drvn_mod NULL 허용 = 수동 제어 (DBA 2차 승인, ANALYZE1 안건 3).
--   - BaseEntity 4 상속 — updt_dtm(갱신시간/제어완료시간) 갱신 컬럼 존재 (indexing-and-migration.md §4.3).
--   - 보존 2년 (db/partitioning-and-retention.md §2 제어 로그).
--
-- 참조:
--   - docs/analyze/20260604/송수펌프제어이력_2_3번섹션/ANALYZE1.md
--   - docs/plan/20260604/송수펌프제어이력_2_3번섹션/PLAN1.md §DB 설계 변경
--   - .claude/rules/db/indexing-and-migration.md §5.4 V{N} 동결 정책
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. pump_ctrl_h (펌프 제어 이력 — 월 RANGE 파티션, BaseEntity 4)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pump_ctrl_h (
    pump_ctrl_id   BIGINT       NOT NULL,
    ctrl_dtm       TIMESTAMP    NOT NULL,
    instrument_id  VARCHAR(36)  NOT NULL,
    ctrl_div       VARCHAR(20)  NOT NULL,
    ctrl_rslt      VARCHAR(20)  NOT NULL,
    ai_drvn_mod    VARCHAR(20),
    rgstr_dtm      TIMESTAMP    NOT NULL,
    updt_dtm       TIMESTAMP    NOT NULL,
    rgstr_id       VARCHAR(50)  NOT NULL,
    updt_id        VARCHAR(50)  NOT NULL,
    PRIMARY KEY (pump_ctrl_id, ctrl_dtm)
) PARTITION BY RANGE (ctrl_dtm);

COMMENT ON TABLE pump_ctrl_h IS '펌프 제어 이력 (월 RANGE 파티션, 제어이력 재도입 — 조회 전용, 보존 2년)';

COMMENT ON COLUMN pump_ctrl_h.pump_ctrl_id  IS '펌프 제어 이력 ID (DOM_SEQ_BIGINT, seq_pump_ctrl_id SEQUENCE 채번, PK 1/2)';
COMMENT ON COLUMN pump_ctrl_h.ctrl_dtm      IS '제어 일시 (제어요청시간, DOM_DTM, PK 2/2, 월 RANGE 파티션 키)';
COMMENT ON COLUMN pump_ctrl_h.instrument_id IS '제어대상 계측기 ID (DOM_ID_36, instrument_m 논리 참조 — 시계열→마스터 FK 금지)';
COMMENT ON COLUMN pump_ctrl_h.ctrl_div      IS '제어요청구분 (DOM_CODE_20, ControlCommand enum 매핑 START 가동/STOP 중지)';
COMMENT ON COLUMN pump_ctrl_h.ctrl_rslt     IS '제어결과 (DOM_CODE_20, ControlResult enum 매핑 COMPLETED 제어완료/CANCELLED 제어취소)';
COMMENT ON COLUMN pump_ctrl_h.ai_drvn_mod   IS '운전모드 (DOM_CODE_20, AiDrvnModeCode enum 매핑 AI/AI_RECOMD/AI_ANLS — NULL 허용=수동 제어, 섹션2 집계는 WHERE IS NOT NULL)';
COMMENT ON COLUMN pump_ctrl_h.rgstr_dtm     IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN pump_ctrl_h.updt_dtm      IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입 — 갱신시간/제어완료시간)';
COMMENT ON COLUMN pump_ctrl_h.rgstr_id      IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN pump_ctrl_h.updt_id       IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- ----------------------------------------------------------------------------
-- 2. SEQUENCE (PK pump_ctrl_id 채번 — @SequenceGenerator(allocationSize=100) 정합)
-- ----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS seq_pump_ctrl_id
    START WITH 1
    INCREMENT BY 1
    CACHE 100;

COMMENT ON SEQUENCE seq_pump_ctrl_id IS '펌프 제어 이력 ID 채번 SEQUENCE — PumpCtrlHistory @SequenceGenerator(allocationSize=100) 정합';

-- ----------------------------------------------------------------------------
-- 3. 인덱스 — dev DB 실측명 정합 (중복 생성 회피)
--    PK(pump_ctrl_h_pkey)는 PRIMARY KEY 제약이 자동 생성 — 별도 CREATE 불요.
--    카디널리티 순서: instrument_id(높음, 등가) → ctrl_dtm(범위·정렬 DESC)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS pump_ctrl_h_instrument_dtm_idx
    ON pump_ctrl_h (instrument_id, ctrl_dtm DESC);

CREATE INDEX IF NOT EXISTS pump_ctrl_h_brin_idx
    ON pump_ctrl_h USING BRIN (ctrl_dtm);

-- ----------------------------------------------------------------------------
-- 4. 월별 파티션 — 6개월 선행 (2026-04 ~ 2027-03)
--    dev DB 기존: 202604~202609 (no-op) / 신규 선행: 202610~202703
--    파티션 미생성 시 INSERT 실패 (partitioning-and-retention.md §1)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pump_ctrl_h_202604 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202605 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202606 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202607 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202608 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202609 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202610 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202611 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202612 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202701 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202702 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');

CREATE TABLE IF NOT EXISTS pump_ctrl_h_202703 PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
