-- ============================================================================
-- opt 도메인 DDL — predc_1m_h 예측 시계열 (월 RANGE 파티션)
-- ----------------------------------------------------------------------------
-- 본 파일은 com.mo.swtp.opt 도메인의 사람이 읽는 SSOT 사본이다.
-- 운영본: common/src/main/resources/db/migration/V5__opt.sql (양쪽 동시 갱신 의무).
--
-- 흡수 이력 (sql_관리포인트_통합 ANALYZE1, 2026-05-20):
--   - common/db/migration/V9_3__predc_1m_h.sql (predc_1m_h + seq_predc_id + 인덱스 + 6 파티션)
--
-- 도메인 역할:
--   AI 최적화 결과 + 예측 시계열 저장 도메인. 본 파일은 1분 그리드 예측값.
--   조회 전용 (INSERT 경로 = AI 추론 파이프라인은 사이클 2 결정 대기).
--
-- 참조:
--   - docs/plan/20260518/송수펌프제어분석-7번섹션/PLAN1.md
--   - .claude/rules/db/partitioning-and-retention.md §1 시계열 파티셔닝 + §2 보존 정책 (3년)
--   - .claude/rules/db/indexing-and-migration.md §4.3 immutable 이력 표준 라벨
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. predc_1m_h (예측 시계열 — 월 RANGE 파티션, immutable INSERT-only)
-- ----------------------------------------------------------------------------
CREATE TABLE predc_1m_h (
    predc_id    BIGINT       NOT NULL,
    predc_dtm   TIMESTAMP    NOT NULL,
    tag_srl_no  VARCHAR(50)  NOT NULL,
    predc_val   NUMERIC(15,4),
    rgstr_dtm   TIMESTAMP    NOT NULL,
    rgstr_id    VARCHAR(50)  NOT NULL,
    PRIMARY KEY (predc_id, predc_dtm)
) PARTITION BY RANGE (predc_dtm);

COMMENT ON TABLE predc_1m_h IS '예측 시계열 태그 예측값 (1분 그리드, immutable 이력 — INSERT-only, 송수펌프제어분석 7번 섹션)';

COMMENT ON COLUMN predc_1m_h.predc_id   IS '예측 결과 ID (DOM_SEQ_BIGINT, seq_predc_id SEQUENCE 채번, PK 1/2)';
COMMENT ON COLUMN predc_1m_h.predc_dtm  IS '예측 대상 일시 (DOM_DTM, PK 2/2, 월 RANGE 파티션 키)';
COMMENT ON COLUMN predc_1m_h.tag_srl_no IS '태그 시리얼번호 (DOM_TAG_SRL_NO_50, tag_m 논리 참조 — 시계열→마스터 FK 금지)';
COMMENT ON COLUMN predc_1m_h.predc_val  IS '예측 측정값 (DOM_QTY_15_4, NULL 허용 — 예측 결측 표현)';
COMMENT ON COLUMN predc_1m_h.rgstr_dtm  IS '등록 일시 (DOM_DTM, immutable 이력이므로 BaseEntity 미상속, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN predc_1m_h.rgstr_id   IS '등록자 ID (immutable 이력 INSERT-only, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- ----------------------------------------------------------------------------
-- 2. SEQUENCE (PK predc_id 채번 — @SequenceGenerator(allocationSize=100) 정합)
-- ----------------------------------------------------------------------------
CREATE SEQUENCE seq_predc_id
    START WITH 1
    INCREMENT BY 1
    CACHE 100;

COMMENT ON SEQUENCE seq_predc_id IS '예측 결과 ID 채번 SEQUENCE — TagPrediction @SequenceGenerator(allocationSize=100) 정합';

-- ----------------------------------------------------------------------------
-- 3. 복합 B-Tree 인덱스 — ASC 강제 (근접 대칭 범위 스캔 BETWEEN target±W)
--    카디널리티 순서: tag_srl_no(높음, 등가) → predc_dtm(범위·정렬)
-- ----------------------------------------------------------------------------
CREATE INDEX idx_predc_1m_h_tag_time
    ON predc_1m_h (tag_srl_no, predc_dtm);

-- ----------------------------------------------------------------------------
-- 4. 월별 파티션 — 6개월 선행 (2026-05 ~ 2026-10)
--    파티션 미생성 시 INSERT 실패 (partitioning-and-retention.md §1)
-- ----------------------------------------------------------------------------
CREATE TABLE predc_1m_h_202605 PARTITION OF predc_1m_h
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE predc_1m_h_202606 PARTITION OF predc_1m_h
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE predc_1m_h_202607 PARTITION OF predc_1m_h
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE TABLE predc_1m_h_202608 PARTITION OF predc_1m_h
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');

CREATE TABLE predc_1m_h_202609 PARTITION OF predc_1m_h
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');

CREATE TABLE predc_1m_h_202610 PARTITION OF predc_1m_h
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
