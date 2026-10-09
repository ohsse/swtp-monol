-- ============================================================================
-- menu 도메인 DDL — 권한 메뉴 마스터 + 메뉴-권한 N:M 매핑
-- ----------------------------------------------------------------------------
-- 본 파일은 com.mo.swtp.menu 도메인의 사람이 읽는 SSOT 사본이다.
-- 운영본: common/src/main/resources/db/migration/V4__menu.sql (양쪽 동시 갱신 의무).
--
-- 흡수 이력 (sql_관리포인트_통합 ANALYZE1, 2026-05-20):
--   - common/db/init/V6_6 (menu_m + menu_role_r — 권한메뉴 PLAN1, 2026-05-06 동일)
--
-- 참조:
--   - .claude/rules/entity-patterns.md §N:M 매핑 엔티티 패턴
--   - .claude/rules/db/indexing-and-migration.md §3·§4
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. menu_m (메뉴 마스터 — 자기참조 트리, BaseEntity 4 적용)
-- ----------------------------------------------------------------------------
CREATE TABLE menu_m (
    menu_id        VARCHAR(36)  NOT NULL,
    menu_nm        VARCHAR(100) NOT NULL,
    menu_url       VARCHAR(255),
    menu_desc      TEXT,
    disp_ord       INTEGER      NOT NULL,
    parent_menu_id VARCHAR(36),
    use_yn         VARCHAR(1)   NOT NULL,
    rgstr_dtm      TIMESTAMP    NOT NULL,
    updt_dtm       TIMESTAMP    NOT NULL,
    rgstr_id       VARCHAR(50)  NOT NULL,
    updt_id        VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_menu_m PRIMARY KEY (menu_id),
    CONSTRAINT uk_menu_m_menu_nm UNIQUE (menu_nm),
    CONSTRAINT fk_menu_m_parent FOREIGN KEY (parent_menu_id)
        REFERENCES menu_m (menu_id) ON DELETE RESTRICT
);
COMMENT ON TABLE  menu_m IS '권한 메뉴 마스터 (자기참조 트리, 깊이 N=3 애플리케이션 검증)';
COMMENT ON COLUMN menu_m.menu_id        IS '메뉴 ID (DOM_ID_36 — UUID 자동 생성, @GeneratedValue(GenerationType.UUID), Persistable 미구현)';
COMMENT ON COLUMN menu_m.menu_nm        IS '메뉴명 (DOM_NAME_100 — 시스템 전체 UNIQUE, facility_nm·instrument_nm 선례 동일 패턴)';
COMMENT ON COLUMN menu_m.menu_url       IS '메뉴 URL — frontend 라우트 (VARCHAR(255), NULL 허용 — 부모 그룹 메뉴 URL 없음, DOM_URL_200 등록 거부 결과 도메인 미지정)';
COMMENT ON COLUMN menu_m.menu_desc      IS '메뉴 설명 (DOM_TEXT, NULL 허용 — 자유형 텍스트)';
COMMENT ON COLUMN menu_m.disp_ord       IS '표시 순서 (INTEGER — 동일 부모 내 정렬)';
COMMENT ON COLUMN menu_m.parent_menu_id IS '상위 메뉴 ID — self-FK (DOM_ID_36, NULL 허용 — 최상위 메뉴는 NULL, 깊이 N=3 애플리케이션 검증, ON DELETE RESTRICT)';
COMMENT ON COLUMN menu_m.use_yn         IS '사용 여부 (DOM_YN — YnType enum 매핑, Y·N)';
COMMENT ON COLUMN menu_m.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN menu_m.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN menu_m.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN menu_m.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- ----------------------------------------------------------------------------
-- 2. menu_role_r (메뉴-권한 N:M 매핑 — INSERT/DELETE 전용, BaseEntity 미상속 B안)
-- ----------------------------------------------------------------------------
CREATE TABLE menu_role_r (
    menu_id    VARCHAR(36)  NOT NULL,
    user_role  VARCHAR(20)  NOT NULL,
    rgstr_dtm  TIMESTAMP    NOT NULL,
    rgstr_id   VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_menu_role_r PRIMARY KEY (menu_id, user_role),
    CONSTRAINT fk_menu_role_r_menu FOREIGN KEY (menu_id)
        REFERENCES menu_m (menu_id) ON DELETE CASCADE
);
COMMENT ON TABLE  menu_role_r IS '메뉴-권한 N:M 매핑 (INSERT/DELETE 전용, ANALYZE2 R-1 _r 채택, ANALYZE2 R-3 복합 PK + B안 BaseEntity 미상속)';
COMMENT ON COLUMN menu_role_r.menu_id   IS '메뉴 ID — 마스터 참조 FK (DOM_ID_36, ON DELETE CASCADE — 마스터 삭제 시 매핑 자동 정리, 복합 PK 1/2)';
COMMENT ON COLUMN menu_role_r.user_role IS '사용자 권한 (DOM_CODE_20 — UserRole enum 매핑 ADMIN·USER, 복합 PK 2/2 — 카디널리티 낮음 단독 인덱스 미적용)';
COMMENT ON COLUMN menu_role_r.rgstr_dtm IS '등록 일시 (DOM_DTM, AuditingEntityListener 자동 주입 — INSERT-only NOT NULL, DOM_DTM 기본 NULL 정책보다 더 엄격 적용)';
COMMENT ON COLUMN menu_role_r.rgstr_id  IS '등록자 ID (DOM_ID_50, AuditingEntityListener 자동 주입 — INSERT-only NOT NULL)';

-- 인덱스 0건 — PLAN1 결정 (idx_menu_m_parent 단일 SELECT 패턴 + idx_menu_role_r_role 카디널리티 2 보류)
