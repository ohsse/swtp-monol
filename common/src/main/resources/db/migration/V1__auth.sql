-- ============================================================================
-- auth 도메인 DDL — refresh_token_p (1계정 1토큰 정책)
-- ----------------------------------------------------------------------------
-- 본 파일은 com.mo.swtp.auth 도메인의 사람이 읽는 SSOT 사본이다.
-- 운영본: common/src/main/resources/db/migration/V1__auth.sql (양쪽 동시 갱신 의무).
--
-- 흡수 이력 (sql_관리포인트_통합 ANALYZE1, 2026-05-20):
--   - api/src/main/resources/db/migration/refresh_token_p.sql (단일 흡수)
--
-- 참조:
--   - .claude/rules/db/indexing-and-migration.md §5 SQL 관리 — 도메인별 단일 파일 + 이중 정책
--   - .claude/rules/db/indexing-and-migration.md §4 컬럼 COMMENT 의무화 정책
-- ============================================================================

-- ----------------------------------------------------------------------------
-- refresh_token_p (리프레시 토큰 명세 — 1계정 1토큰)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_token_p (
    token_id      VARCHAR(36)  NOT NULL,
    user_id       VARCHAR(50)  NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    expr_dtm      TIMESTAMP    NOT NULL,
    revoke_dtm    TIMESTAMP,
    last_used_dtm TIMESTAMP,
    rgstr_id      VARCHAR(50),
    updt_id       VARCHAR(50),
    rgstr_dtm     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updt_dtm      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_refresh_token_p         PRIMARY KEY (token_id),
    CONSTRAINT uk_refresh_token_p_user_id UNIQUE (user_id),
    CONSTRAINT fk_refresh_token_p_user_id FOREIGN KEY (user_id) REFERENCES user_m (user_id)
);

COMMENT ON TABLE  refresh_token_p               IS '리프레시 토큰 명세 (1계정 1토큰)';
COMMENT ON COLUMN refresh_token_p.token_id      IS '토큰 식별자 (UUID)';
COMMENT ON COLUMN refresh_token_p.user_id       IS '사용자 ID (user_m.user_id 참조)';
COMMENT ON COLUMN refresh_token_p.token_hash    IS 'SHA-256 해시된 토큰값';
COMMENT ON COLUMN refresh_token_p.expr_dtm      IS '토큰 만료 일시';
COMMENT ON COLUMN refresh_token_p.revoke_dtm    IS '토큰 폐기 일시 (null이면 활성)';
COMMENT ON COLUMN refresh_token_p.last_used_dtm IS '마지막 사용 일시';
COMMENT ON COLUMN refresh_token_p.rgstr_id      IS '등록자 ID';
COMMENT ON COLUMN refresh_token_p.updt_id       IS '수정자 ID';
COMMENT ON COLUMN refresh_token_p.rgstr_dtm     IS '등록 일시';
COMMENT ON COLUMN refresh_token_p.updt_dtm      IS '수정 일시';

-- 만료 일시 인덱스 (만료 토큰 일괄 정리·검증용)
CREATE INDEX IF NOT EXISTS idx_refresh_token_p_expr_dtm ON refresh_token_p (expr_dtm);
