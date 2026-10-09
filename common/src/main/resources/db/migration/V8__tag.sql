-- ============================================================================
-- tag 도메인 DDL — 태그 마스터 (SCADA 계측값 식별 단위)
-- ----------------------------------------------------------------------------
-- 본 파일은 com.mo.swtp.tag 도메인의 사람이 읽는 SSOT 사본이다.
-- 운영본: common/src/main/resources/db/migration/V8__tag.sql (양쪽 동시 갱신 의무).
--
-- 흡수 이력 (sql_관리포인트_통합 ANALYZE1, 2026-05-20 — 최종 결과 정렬):
--   - common/db/init/V6_4 (tag_m + 인덱스 — tag_srl_no 자연키 PK + io_cd + unit_cd)
--   - common/db/migration/V9_1 (use_yn 추가 + unit_cd 제거 — CREATE TABLE 인라인 정렬)
--     → tag_m CREATE TABLE 에 use_yn 포함, unit_cd 미포함 (TagMeasurementType enum 단위 매핑 흡수)
--
-- 도메인 역할:
--   SCADA 계측값 식별 단위. instrument_m 1:N 종속.
--   자연키 PK (tag_srl_no, 706-FRI-xxx-xxx 형식) — Persistable<String> 구현 필수.
--   use_yn 논리 삭제 지원 (rawdata_1m_h.tag_srl_no 시계열 논리 참조 보존).
--
-- 참조:
--   - docs/plan/20260503/마스터도메인설계/PLAN1.md §DB 설계 변경
--   - docs/plan/20260508/태그관리/PLAN1.md §DB 설계 변경 (use_yn + unit_cd drop)
--   - .claude/rules/entity-patterns.md §외부 할당 PK 엔티티 패턴
--   - .claude/rules/db/indexing-and-migration.md §3 DOM_YN DDL 정책
-- ============================================================================

CREATE TABLE tag_m (
    tag_srl_no     VARCHAR(50)   NOT NULL,
    instrument_id  VARCHAR(36)   NOT NULL,
    tag_se_cd      VARCHAR(20)   NOT NULL,
    tag_desc       TEXT,
    io_cd          VARCHAR(20)   NOT NULL,
    use_yn         VARCHAR(1)    NOT NULL,
    rgstr_dtm      TIMESTAMP     NOT NULL,
    updt_dtm       TIMESTAMP     NOT NULL,
    rgstr_id       VARCHAR(50)   NOT NULL,
    updt_id        VARCHAR(50)   NOT NULL,
    PRIMARY KEY (tag_srl_no),
    CONSTRAINT fk_tag_m__instrument
        FOREIGN KEY (instrument_id) REFERENCES instrument_m (instrument_id)
);
COMMENT ON TABLE tag_m IS '태그 마스터 (SCADA 계측값 식별 단위, instrument_m 1:N 종속, use_yn 논리 삭제 지원)';
COMMENT ON COLUMN tag_m.tag_srl_no    IS '태그 시리얼번호 (DOM_TAG_SRL_NO_50, 자연키 PK — 외부 할당 706-FRI-xxx-xxx 형식, Persistable<String> 매핑)';
COMMENT ON COLUMN tag_m.instrument_id IS '소속 계측기 ID (마스터 참조 FK to instrument_m, DOM_ID_36, NOT NULL)';
COMMENT ON COLUMN tag_m.tag_se_cd     IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수)';
COMMENT ON COLUMN tag_m.tag_desc      IS '태그 설명 (DOM_TEXT — 자유형 설명, NULL 허용)';
COMMENT ON COLUMN tag_m.io_cd         IS '입출력 구분 코드 (DOM_CODE_20 — IoCode enum 매핑 INPUT/OUTPUT/BIDIR)';
COMMENT ON COLUMN tag_m.use_yn        IS '사용 여부 (DOM_YN — Y·N enum 매핑, 논리 삭제 지원, rawdata_1m_h.tag_srl_no 논리 참조 보존)';
COMMENT ON COLUMN tag_m.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN tag_m.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN tag_m.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN tag_m.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- FK 조회 가속
CREATE INDEX idx_tag_m_instrument_id ON tag_m (instrument_id);
