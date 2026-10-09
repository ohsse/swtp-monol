-- ============================================================================
-- raw 도메인 DDL — rawdata_1m_h SCADA 원시 데이터 시계열 (월 RANGE 파티션)
-- ----------------------------------------------------------------------------
-- 본 파일은 com.mo.swtp.raw 도메인의 사람이 읽는 SSOT 사본이다.
-- 운영본: common/src/main/resources/db/migration/V7__raw.sql (양쪽 동시 갱신 의무).
--
-- 흡수 이력 (sql_관리포인트_통합 ANALYZE1, 2026-05-20):
--   - common/db/init/V6_5 (seq_rawdata_id + rawdata_1m_h + BRIN + 복합 인덱스 + 6 파티션)
--
-- 도메인 역할:
--   SCADA 원시 측정 데이터 1분 시계열. raw_val (원본) + corr_val (보정값 갱신 허용) 분리.
--   시계열 → 마스터 FK 금지 (tag_srl_no 논리 참조만).
--   BaseEntity 4 적용 (corr_val 갱신 시점 추적).
--
-- DDL 4단계 순서 의무 (wtp-dba-reviewer 권고 2026-05-03):
--   1. CREATE SEQUENCE seq_rawdata_id (allocationSize=100)
--   2. CREATE TABLE rawdata_1m_h ... PARTITION BY RANGE (acq_dtm)
--   3. CREATE INDEX (BRIN + 복합 B-Tree)
--   4. CREATE TABLE rawdata_1m_h_YYYYMM PARTITION OF 6개월 선행
--
-- 참조:
--   - docs/plan/20260503/마스터도메인설계/PLAN1.md §DB 설계 변경
--   - .claude/rules/db/partitioning-and-retention.md §1 시계열 파티셔닝 + §2 보존 (13개월 롤링)
--   - .claude/rules/db/indexing-and-migration.md §1 인덱스 원칙
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1단계: 시퀀스 (allocationSize=100, JPA @SequenceGenerator 와 INCREMENT BY 일치)
-- ----------------------------------------------------------------------------
CREATE SEQUENCE seq_rawdata_id
    INCREMENT BY 100
    START WITH 1
    MINVALUE 1
    NO MAXVALUE
    NO CYCLE;
COMMENT ON SEQUENCE seq_rawdata_id IS 'rawdata_1m_h.rawdata_id 시퀀스 (allocationSize=100 — 분당 다수 INSERT 배치 효율)';

-- ----------------------------------------------------------------------------
-- 2단계: 부모 테이블 + 파티션 키 (BaseEntity 4 적용 — corr_val 갱신 시점 추적)
-- ----------------------------------------------------------------------------
CREATE TABLE rawdata_1m_h (
    rawdata_id  BIGINT          NOT NULL,
    acq_dtm     TIMESTAMP       NOT NULL,
    tag_srl_no  VARCHAR(50)     NOT NULL,
    raw_val     NUMERIC(15, 4),
    corr_val    NUMERIC(15, 4),
    quality_cd  VARCHAR(20)     NOT NULL,
    rgstr_dtm   TIMESTAMP       NOT NULL,
    updt_dtm    TIMESTAMP       NOT NULL,
    rgstr_id    VARCHAR(50)     NOT NULL,
    updt_id     VARCHAR(50)     NOT NULL,
    PRIMARY KEY (rawdata_id, acq_dtm)
) PARTITION BY RANGE (acq_dtm);
COMMENT ON TABLE rawdata_1m_h IS 'SCADA 원시 데이터 1분 시계열 (월 RANGE 파티션, 보존 13개월 롤링, immutable INSERT-only는 tag_srl_no·acq_dtm·raw_val, corr_val 만 갱신 허용)';
COMMENT ON COLUMN rawdata_1m_h.rawdata_id IS '로우데이터 ID (DOM_SEQ_BIGINT — GenerationType.SEQUENCE allocationSize=100, 복합 PK 1/2)';
COMMENT ON COLUMN rawdata_1m_h.acq_dtm    IS '수집 일시 (DOM_DTM, 파티션 키 + 복합 PK 2/2 — INSERT-only immutable, SCADA 수집 시점 고정)';
COMMENT ON COLUMN rawdata_1m_h.tag_srl_no IS '태그 시리얼번호 (DOM_TAG_SRL_NO_50 — tag_m.tag_srl_no 논리 참조, 시계열→마스터 FK 금지 db/partitioning-and-retention.md §1, INSERT-only immutable)';
COMMENT ON COLUMN rawdata_1m_h.raw_val    IS 'SCADA 원본 측정값 (DOM_QTY_15_4 — INSERT-only immutable, BAD QUALITY 시 NULL)';
COMMENT ON COLUMN rawdata_1m_h.corr_val   IS '보정/수정 측정값 (DOM_QTY_15_4 — Hold Last Value 적용 결과 또는 운영자 보정, NULL 허용, 갱신 허용 corr_val 만)';
COMMENT ON COLUMN rawdata_1m_h.quality_cd IS 'SCADA QUALITY 코드 (DOM_CODE_20 — QualityCode enum 매핑 GOOD/BAD/UNCERTAIN, ot-integration.md §3 정합)';
COMMENT ON COLUMN rawdata_1m_h.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN rawdata_1m_h.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입 — corr_val 갱신 시점 추적)';
COMMENT ON COLUMN rawdata_1m_h.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN rawdata_1m_h.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- ----------------------------------------------------------------------------
-- 3단계: 부모 인덱스 (PostgreSQL 11+ 자식 파티션 자동 상속)
-- ----------------------------------------------------------------------------

-- BRIN — 1분 주기 대용량 INSERT (크기 1/100, 쓰기 오버헤드 최소)
CREATE INDEX idx_rawdata_1m_h_brin ON rawdata_1m_h USING BRIN (acq_dtm);

-- 복합 B-Tree — 등가(tag_srl_no) → 범위·정렬(acq_dtm DESC) 순서 정합
CREATE INDEX idx_rawdata_1m_h_tag_time ON rawdata_1m_h (tag_srl_no, acq_dtm DESC);

-- ----------------------------------------------------------------------------
-- 4단계: 6개월 선행 파티션 (2026-05 ~ 2026-10)
--    자동 추가는 scheduler 모듈 (PumpPartitionScheduler 와 동일 패턴, 별도 작업)
-- ----------------------------------------------------------------------------
CREATE TABLE rawdata_1m_h_202605 PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE rawdata_1m_h_202606 PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE rawdata_1m_h_202607 PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE rawdata_1m_h_202608 PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE rawdata_1m_h_202609 PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE rawdata_1m_h_202610 PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
