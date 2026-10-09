-- ============================================================================
-- user 도메인 DDL — 사용자 마스터 + 초기 admin 계정
-- ----------------------------------------------------------------------------
-- 본 파일은 com.mo.swtp.user 도메인의 사람이 읽는 SSOT 사본이다.
-- 운영본: common/src/main/resources/db/migration/V9__user.sql (양쪽 동시 갱신 의무).
--
-- 흡수 이력 (sql_관리포인트_통합 ANALYZE1, 2026-05-20 — 최종 결과 정렬):
--   - api/src/main/resources/db/migration/user_m.sql (user_m + admin seed)
--   - api/src/main/resources/db/migration/user_m_use_yn_alignment.sql (정렬 결과 인라인)
--     → use_yn VARCHAR(1) (구 CHAR(1) → VARCHAR(1) 정렬)
--     → DEFAULT 'Y' 미설정 (Java 정적 팩토리 YnType.Y SSOT)
--     → idx_user_m_use_yn 단독 인덱스 미포함 (카디널리티 2 — Seq Scan 선호, §3.4 정책)
--     → CHECK 보존 (ck_user_m_use_yn 기존 운영 안전망)
--
-- 도메인 역할:
--   사용자 마스터 (외부 할당 PK user_id, Persistable<String>).
--   초기 admin 계정 시드 INSERT 포함 (비밀번호: admin, BCrypt 해시).
--
-- 참조:
--   - docs/analyze/20260425/use_yn_consistency/ANALYZE1.md (안건 1·2·3·4·6)
--   - .claude/rules/db/indexing-and-migration.md §3 DOM_YN DDL 정책
--   - swtp/.claude/rules/dict/standard-data-domains.md DOM_YN
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_m (
    user_id   VARCHAR(50)  NOT NULL,
    user_nm   VARCHAR(100) NOT NULL,
    user_pw   VARCHAR(255) NOT NULL,
    user_role VARCHAR(20)  NOT NULL,
    use_yn    VARCHAR(1)   NOT NULL,
    rgstr_id  VARCHAR(50),
    updt_id   VARCHAR(50),
    rgstr_dtm TIMESTAMP    NOT NULL DEFAULT NOW(),
    updt_dtm  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_m PRIMARY KEY (user_id),
    CONSTRAINT ck_user_m_use_yn CHECK (use_yn IN ('Y', 'N')),
    CONSTRAINT ck_user_m_role   CHECK (user_role IN ('ADMIN', 'USER'))
);

COMMENT ON TABLE  user_m           IS '사용자 마스터 (외부 할당 PK user_id, Persistable<String>)';
COMMENT ON COLUMN user_m.user_id   IS '사용자 ID (DOM_ID_50 — 외부 할당 PK)';
COMMENT ON COLUMN user_m.user_nm   IS '사용자 이름 (DOM_NAME_100)';
COMMENT ON COLUMN user_m.user_pw   IS 'BCrypt 해시 비밀번호';
COMMENT ON COLUMN user_m.user_role IS '권한 역할 (ADMIN/USER, DOM_CODE_20 — UserRole enum 매핑, CHECK ck_user_m_role)';
COMMENT ON COLUMN user_m.use_yn    IS '사용 여부 (Y: 활성, N: 비활성) - DDL DEFAULT 미설정, Java 정적 팩토리에서 YnType.Y 명시 할당 의무. CHECK ck_user_m_use_yn 기존 운영 안전망 보존';
COMMENT ON COLUMN user_m.rgstr_id  IS '등록자 ID (DOM_ID_50, NULL 허용)';
COMMENT ON COLUMN user_m.updt_id   IS '수정자 ID (DOM_ID_50, NULL 허용)';
COMMENT ON COLUMN user_m.rgstr_dtm IS '등록 일시 (DOM_DTM, DEFAULT NOW())';
COMMENT ON COLUMN user_m.updt_dtm  IS '수정 일시 (DOM_DTM, DEFAULT NOW())';

-- ----------------------------------------------------------------------------
-- 초기 관리자 계정 시드 (비밀번호: admin — BCrypt 해시)
-- ----------------------------------------------------------------------------
INSERT INTO user_m (user_id, user_nm, user_pw, user_role, use_yn, rgstr_id, updt_id)
VALUES (
    'admin',
    '관리자',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Jm3.',
    'ADMIN',
    'Y',
    'system',
    'system'
)
ON CONFLICT (user_id) DO NOTHING;
