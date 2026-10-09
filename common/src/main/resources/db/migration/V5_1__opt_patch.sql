-- ============================================================================
-- opt 도메인 patch V5_1 — opt_peak_target_p (전력피크 목표값 단일 전역 행)
-- ----------------------------------------------------------------------------
-- V5__opt.sql 동결 → 신규 변경은 본 patch 로 분리 (indexing-and-migration.md §5.4).
-- SSOT 사본: backend/docs/ddl/opt.sql (양쪽 동시 갱신 의무 §5.3).
--
-- 도메인 역할 (전력피크분석-1번섹션 ANALYZE1 + PLAN1, 2026-06-04):
--   전력피크 분석 화면 1번 섹션 "피크치 설정" — 운전원이 목표 피크 전력값(kW) 하나를
--   입력·저장하면 같은 화면 모든 구독자에게 SSE 전파. 정수장(테넌트) 전역 단일 값.
--
-- 단일 전역 행 강제:
--   고정 코드값 PK 'PEAK_TARGET' + CHECK (peak_cd = 'PEAK_TARGET') → DB 레벨 2행 차단.
--   시드 1행 사전 INSERT → 저장은 항상 UPDATE (SELECT FOR UPDATE 직렬화).
--
-- 시드 초기값 target_peak_elpwr = 0 ("미설정" sentinel):
--   이미지의 운영값(예: 900)은 사업장별 상이 → 스키마 baking 시 멀티테넌트 위반.
--   운전원이 PUT 으로 실제값(양수) 저장 전까지 0 유지. @Positive 가 0 저장 차단.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. opt_peak_target_p (전력피크 목표값 — 단일 전역 행, 설정값 명세 _p)
-- ----------------------------------------------------------------------------
CREATE TABLE opt_peak_target_p (
    peak_cd           VARCHAR(20)   NOT NULL,
    target_peak_elpwr NUMERIC(15,4) NOT NULL,
    rgstr_dtm         TIMESTAMP     NOT NULL,
    updt_dtm          TIMESTAMP     NOT NULL,
    rgstr_id          VARCHAR(50)   NOT NULL,
    updt_id           VARCHAR(50)   NOT NULL,
    CONSTRAINT pk_opt_peak_target_p PRIMARY KEY (peak_cd),
    CONSTRAINT ck_opt_peak_target_p_peak_cd CHECK (peak_cd = 'PEAK_TARGET')
);

COMMENT ON TABLE opt_peak_target_p IS '전력피크 목표값 (단일 전역 행 — 고정 코드값 PK + CHECK, 전력피크분석-1번섹션)';

COMMENT ON COLUMN opt_peak_target_p.peak_cd           IS '피크 코드 (DOM_CODE_20, 외부 할당 고정 코드값 PK ''PEAK_TARGET'' — CHECK 제약으로 단일 행 강제)';
COMMENT ON COLUMN opt_peak_target_p.target_peak_elpwr IS '목표 피크 전력값 (kW, DOM_QTY_15_4, 운전원 설정값 — 0 = 미설정 sentinel, 저장 시 @Positive 양수 강제)';
COMMENT ON COLUMN opt_peak_target_p.rgstr_dtm         IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN opt_peak_target_p.updt_dtm          IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN opt_peak_target_p.rgstr_id          IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN opt_peak_target_p.updt_id           IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- ----------------------------------------------------------------------------
-- 2. 시드 1행 — 저장 UPDATE-only 보장 ("미설정" sentinel 0)
-- ----------------------------------------------------------------------------
INSERT INTO opt_peak_target_p (peak_cd, target_peak_elpwr, rgstr_dtm, updt_dtm, rgstr_id, updt_id)
VALUES ('PEAK_TARGET', 0, now(), now(), 'system', 'system');
