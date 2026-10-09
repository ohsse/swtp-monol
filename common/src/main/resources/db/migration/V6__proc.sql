-- ============================================================================
-- proc 도메인 DDL — proc_m + ai_drvn_mod_p + ai_drvn_mod_h + 시드
-- ----------------------------------------------------------------------------
-- 본 파일은 com.mo.swtp.proc 도메인의 사람이 읽는 SSOT 사본이다.
-- 운영본: common/src/main/resources/db/migration/V6__proc.sql (양쪽 동시 갱신 의무).
--
-- 흡수 이력 (sql_관리포인트_통합 ANALYZE1, 2026-05-20):
--   - common/db/init/V9_4 (proc_m + ai_drvn_mod_p + ai_drvn_mod_h + seq + 인덱스 + seed PUMP_CONTROL)
--
-- 도메인 역할:
--   공정/제어대상 마스터 (proc_m) + AI 운전모드 현재 상태 (ai_drvn_mod_p, 1:1) +
--   AI 운전모드 변경 이력 (ai_drvn_mod_h, BaseEntity 4 상속 + end_dtm UPDATE 허용).
--
-- 참조:
--   - docs/plan/20260520/송수펌프제어분석-2번섹션/PLAN2.md §DB 설계 변경
--   - .claude/rules/db/indexing-and-migration.md §1·§4 (변경 추적 컬럼 존재 시 BaseEntity 4 상속 허용)
--   - .claude/rules/db/partitioning-and-retention.md §2 보존 정책 (5년)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. proc_m (공정/제어대상 마스터 — 외부 할당 PK, BaseEntity 4 적용)
-- ----------------------------------------------------------------------------
CREATE TABLE proc_m (
    proc_id    VARCHAR(50)  NOT NULL,
    proc_nm    VARCHAR(100) NOT NULL,
    disp_ord   INTEGER      NOT NULL,
    use_yn     VARCHAR(1)   NOT NULL,
    rgstr_dtm  TIMESTAMP    NOT NULL,
    updt_dtm   TIMESTAMP    NOT NULL,
    rgstr_id   VARCHAR(50)  NOT NULL,
    updt_id    VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_proc_m PRIMARY KEY (proc_id),
    CONSTRAINT uk_proc_m_proc_nm UNIQUE (proc_nm)
);
COMMENT ON TABLE  proc_m IS '공정/제어대상 마스터 (외부 할당 PK, 사업장 간 공통 코드값 정렬 — ANALYZE2 안건 1)';
COMMENT ON COLUMN proc_m.proc_id   IS '공정/제어대상 ID (DOM_ID_50 — 외부 할당 PK, Persistable<String> 필수, 정규식 ^[A-Z][A-Z0-9_]*$ 애플리케이션 검증, 사업장 간 공통 코드값 예 PUMP_CONTROL/WTR_TREAT)';
COMMENT ON COLUMN proc_m.proc_nm   IS '공정/제어대상명 (DOM_NAME_100 — 시스템 전체 UNIQUE, facility_nm·menu_nm 선례 동일 패턴)';
COMMENT ON COLUMN proc_m.disp_ord  IS '표시 순서 (INTEGER — 화면 메뉴 정렬, facility_m·menu_m 선례 동일)';
COMMENT ON COLUMN proc_m.use_yn    IS '사용 여부 (DOM_YN — YnType enum 매핑 Y·N, 공정/제어대상 비활성화 시나리오 대응)';
COMMENT ON COLUMN proc_m.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN proc_m.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN proc_m.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN proc_m.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- ----------------------------------------------------------------------------
-- 2. ai_drvn_mod_p (AI 운전모드 현재 상태 — 1:1 마스터, BaseEntity 4 적용)
-- ----------------------------------------------------------------------------
CREATE TABLE ai_drvn_mod_p (
    proc_id          VARCHAR(50)  NOT NULL,
    ai_drvn_mod_cd   VARCHAR(20)  NOT NULL,
    start_dtm        TIMESTAMP    NOT NULL,
    rgstr_dtm        TIMESTAMP    NOT NULL,
    updt_dtm         TIMESTAMP    NOT NULL,
    rgstr_id         VARCHAR(50)  NOT NULL,
    updt_id          VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_ai_drvn_mod_p PRIMARY KEY (proc_id),
    CONSTRAINT fk_ai_drvn_mod_p_proc FOREIGN KEY (proc_id)
        REFERENCES proc_m (proc_id) ON DELETE RESTRICT
);
COMMENT ON TABLE  ai_drvn_mod_p IS 'AI 운전모드 현재 상태 (1:1 마스터 — proc_id PK+FK 동시, AiDrvnMode @MapsId 매핑, 변경 시 UPDATE)';
COMMENT ON COLUMN ai_drvn_mod_p.proc_id        IS '공정/제어대상 ID (DOM_ID_50 — PK + FK 동시, proc_m 참조 ON DELETE RESTRICT, @MapsId 매핑)';
COMMENT ON COLUMN ai_drvn_mod_p.ai_drvn_mod_cd IS 'AI 운전모드 코드 (DOM_CODE_20 — AiDrvnModeCode enum 매핑 AI/AI_RECOMD/AI_ANLS, 사용자 의도 명세 — 시스템 상태와 별개)';
COMMENT ON COLUMN ai_drvn_mod_p.start_dtm      IS '현재 모드 시작 일시 (DOM_DTM, NOT NULL — 활성 시점 항상 존재, 기본 NULL 정책보다 더 엄격)';
COMMENT ON COLUMN ai_drvn_mod_p.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN ai_drvn_mod_p.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN ai_drvn_mod_p.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN ai_drvn_mod_p.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- ----------------------------------------------------------------------------
-- 3. 시퀀스 — ai_drvn_mod_h.ai_drvn_mod_id (DOM_SEQ_BIGINT, allocationSize=100)
-- ----------------------------------------------------------------------------
CREATE SEQUENCE seq_ai_drvn_mod_h_id
    INCREMENT BY 100
    START WITH 1
    MINVALUE 1
    NO MAXVALUE
    NO CYCLE;
COMMENT ON SEQUENCE seq_ai_drvn_mod_h_id IS 'ai_drvn_mod_h.ai_drvn_mod_id 시퀀스 (DOM_SEQ_BIGINT — GenerationType.SEQUENCE allocationSize=100)';

-- ----------------------------------------------------------------------------
-- 4. ai_drvn_mod_h (AI 운전모드 변경 이력 — BaseEntity 4 + end_dtm UPDATE 허용)
-- ----------------------------------------------------------------------------
CREATE TABLE ai_drvn_mod_h (
    ai_drvn_mod_id   BIGINT       NOT NULL,
    proc_id          VARCHAR(50)  NOT NULL,
    ai_drvn_mod_cd   VARCHAR(20)  NOT NULL,
    start_dtm        TIMESTAMP    NOT NULL,
    end_dtm          TIMESTAMP,
    rgstr_dtm        TIMESTAMP    NOT NULL,
    updt_dtm         TIMESTAMP    NOT NULL,
    rgstr_id         VARCHAR(50)  NOT NULL,
    updt_id          VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_ai_drvn_mod_h PRIMARY KEY (ai_drvn_mod_id),
    CONSTRAINT fk_ai_drvn_mod_h_proc FOREIGN KEY (proc_id)
        REFERENCES proc_m (proc_id) ON DELETE RESTRICT
);
COMMENT ON TABLE  ai_drvn_mod_h IS 'AI 운전모드 변경 이력 (BaseEntity 4 상속 — end_dtm UPDATE 허용, INSERT-only 컬럼은 ai_drvn_mod_id·proc_id·ai_drvn_mod_cd·start_dtm 애플리케이션 검증, 보존 5년)';
COMMENT ON COLUMN ai_drvn_mod_h.ai_drvn_mod_id IS 'AI 운전모드 이력 ID (DOM_SEQ_BIGINT — GenerationType.SEQUENCE allocationSize=100, INSERT-only immutable)';
COMMENT ON COLUMN ai_drvn_mod_h.proc_id        IS '공정/제어대상 ID (DOM_ID_50 — proc_m 참조 FK ON DELETE RESTRICT, INSERT-only immutable)';
COMMENT ON COLUMN ai_drvn_mod_h.ai_drvn_mod_cd IS 'AI 운전모드 코드 (DOM_CODE_20 — AiDrvnModeCode enum 매핑 AI/AI_RECOMD/AI_ANLS, INSERT-only immutable)';
COMMENT ON COLUMN ai_drvn_mod_h.start_dtm      IS '모드 시작 일시 (DOM_DTM NOT NULL — INSERT-only immutable, 활성 시점 항상 존재)';
COMMENT ON COLUMN ai_drvn_mod_h.end_dtm        IS '모드 종료 일시 (DOM_DTM NULL 허용 — 현재 활성 행 NULL, 다음 모드 변경 시점에 UPDATE, 부분 UNIQUE INDEX 대상)';
COMMENT ON COLUMN ai_drvn_mod_h.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN ai_drvn_mod_h.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입 — end_dtm 갱신 시점 추적)';
COMMENT ON COLUMN ai_drvn_mod_h.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN ai_drvn_mod_h.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50 — end_dtm 갱신자)';

-- ----------------------------------------------------------------------------
-- 5. 인덱스
--    - 복합 (proc_id, start_dtm DESC): 이력 조회 (proc_id 별 최신순)
--    - 부분 UNIQUE (proc_id) WHERE end_dtm IS NULL: 활성 행 1건 강제 (동시성 안전망)
-- ----------------------------------------------------------------------------
CREATE INDEX idx_ai_drvn_mod_h_proc_start
    ON ai_drvn_mod_h (proc_id, start_dtm DESC);

CREATE UNIQUE INDEX uk_ai_drvn_mod_h_proc_active
    ON ai_drvn_mod_h (proc_id) WHERE end_dtm IS NULL;

-- ----------------------------------------------------------------------------
-- 6. 시드 데이터 — 'PUMP_CONTROL' 1행 (송수펌프제어 화면 진입점)
--    BaseEntity 4 컬럼은 AuditingEntityListener 미적용 (raw INSERT) 이므로 명시 할당
-- ----------------------------------------------------------------------------
INSERT INTO proc_m (proc_id, proc_nm, disp_ord, use_yn, rgstr_dtm, updt_dtm, rgstr_id, updt_id)
VALUES ('PUMP_CONTROL', '송수펌프제어', 1, 'Y', NOW(), NOW(), 'system', 'system');
