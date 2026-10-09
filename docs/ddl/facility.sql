-- ============================================================================
-- facility 도메인 DDL — 시설 단일 마스터 + JPA JOINED 자식 12종 + 인덱스
-- ----------------------------------------------------------------------------
-- 본 파일은 com.mo.swtp.facility 도메인의 사람이 읽는 SSOT 사본이다.
-- 운영본: common/src/main/resources/db/migration/V2__facility.sql + V2_1__facility_patch.sql
--         (양쪽 동시 갱신 의무).
--
-- 흡수 이력 (sql_관리포인트_통합 ANALYZE1, 2026-05-20 — 최종 결과 정렬):
--   - common/db/init/V6_1 (facility_m + pwtf_m + dwt_m + rsv_m skeleton + 인덱스 2건)
--   - common/db/init/V7_1 (point_m FK ON DELETE RESTRICT)
--   - common/db/init/V8_1 (prsf_m FK ON DELETE RESTRICT + idx_facility_m_type_parent)
--   - common/db/init/V8_3 (dwt_m.min_req_prsr NOT NULL → CREATE TABLE 인라인 정렬)
--   - common/db/init/V8_7 (dwt_m.min_req_branch_prsr NOT NULL → CREATE TABLE 인라인 정렬)
--   - common/db/migration/V9_2 (facility 부분 idx_facility_m_parent_type_yn)
--   - common/db/migration/V2_1 (운영시설 자식 7종 + facility_type_cd COMMENT 12종 갱신 — 시설_도메인_확장, 2026-06-08)
--
-- 자식 종류 12종:
--   [저장] PWTF (정수조) · DWT (배수지, self 컬럼 2건) · RSV (저수지)
--   [계통] POINT (관로 계측 분기점)
--   [운영] PRSF (가압장) · WTBLD (송수동) · CHMB (약품동) · ACFB (활성탄여과지)
--          · POZB (전오존동) · FLTB (여과지동) · DEWB (탈수기동) · SOLAR (태양광)
--   ※ 그룹(저장/운영/계통)은 FacilityType 파생 enum (FacilityGroup) — DB 컬럼 미신설.
--
-- 참조:
--   - .claude/rules/entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴
--   - .claude/rules/db/indexing-and-migration.md §1 인덱스 원칙 + §4 컬럼 COMMENT 의무화 정책
--   - .claude/rules/ot-integration.md §5 PRSF 가압장 시설 단위 별도 평가
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 시설 단일 마스터 (부모, JPA JOINED)
-- ----------------------------------------------------------------------------
CREATE TABLE facility_m (
    facility_id         VARCHAR(36)  NOT NULL,
    facility_nm         VARCHAR(100) NOT NULL,
    facility_type_cd    VARCHAR(20)  NOT NULL,
    parent_facility_id  VARCHAR(36),
    disp_ord            INTEGER      NOT NULL,
    main_yn             VARCHAR(1)   NOT NULL,
    use_yn              VARCHAR(1)   NOT NULL,
    rgstr_dtm           TIMESTAMP    NOT NULL,
    updt_dtm            TIMESTAMP    NOT NULL,
    rgstr_id            VARCHAR(50)  NOT NULL,
    updt_id             VARCHAR(50)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_facility_m__parent
        FOREIGN KEY (parent_facility_id) REFERENCES facility_m (facility_id)
);
COMMENT ON TABLE facility_m IS '시설 단일 마스터 (JPA JOINED 부모, 자식 12종 PWTF·DWT·RSV·POINT·PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR)';
COMMENT ON COLUMN facility_m.facility_id        IS '시설 ID (UUID 자동 생성 PK, DOM_ID_36 — Persistable 미구현, GenerationType.UUID)';
COMMENT ON COLUMN facility_m.facility_nm        IS '시설명 (DOM_NAME_100 — UNIQUE 시스템 전체, 사용자 식별 비즈니스 키)';
COMMENT ON COLUMN facility_m.facility_type_cd   IS '시설 유형 코드 (DOM_CODE_20 — DiscriminatorColumn, FacilityType enum SSOT 매핑 PWTF/DWT/RSV/POINT/PRSF/WTBLD/CHMB/ACFB/POZB/FLTB/DEWB/SOLAR)';
COMMENT ON COLUMN facility_m.parent_facility_id IS '상위 시설 ID (DOM_ID_36 — self-FK NULL 허용, 재귀 깊이 무제한)';
COMMENT ON COLUMN facility_m.disp_ord           IS '표시 순서 (화면 표시 정렬 순번)';
COMMENT ON COLUMN facility_m.main_yn            IS '주요 시설 여부 (Y/N, DOM_YN — YnType enum 매핑)';
COMMENT ON COLUMN facility_m.use_yn             IS '사용 여부 (Y/N, DOM_YN — YnType enum 매핑)';
COMMENT ON COLUMN facility_m.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN facility_m.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN facility_m.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN facility_m.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- 시스템 전체 UNIQUE 인덱스 (사용자 결정 핵심 — 마스터도메인설계 PLAN1)
CREATE UNIQUE INDEX idx_facility_m_facility_nm ON facility_m (facility_nm);

-- 자식 종류별 부분 조회 가속
CREATE INDEX idx_facility_m_facility_type_cd ON facility_m (facility_type_cd);

-- 복합 인덱스 (facility_type_cd, parent_facility_id) — findFacilitiesHavingDwtChild exists 서브쿼리 최적화 (V8_1)
CREATE INDEX idx_facility_m_type_parent
    ON facility_m (facility_type_cd, parent_facility_id);

-- 복합 인덱스 (parent_facility_id, facility_type_cd, use_yn) — DWT 자식 조회 hot-path (V9_2)
CREATE INDEX idx_facility_m_parent_type_yn
    ON facility_m (parent_facility_id, facility_type_cd, use_yn);

-- ----------------------------------------------------------------------------
-- 2. 정수조 자식 (PWTF, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE pwtf_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_pwtf_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id)
);
COMMENT ON TABLE pwtf_m IS '정수조 자식 (facility_type_cd=PWTF, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton)';
COMMENT ON COLUMN pwtf_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준)';

-- ----------------------------------------------------------------------------
-- 3. 배수지 자식 (DWT, JPA JOINED 자식) — 자식 컬럼 2건 인라인 정렬 (V8_3 + V8_7 최종)
-- ----------------------------------------------------------------------------
CREATE TABLE dwt_m (
    facility_id         VARCHAR(36)     NOT NULL,
    min_req_prsr        NUMERIC(15, 4)  NOT NULL,
    min_req_branch_prsr NUMERIC(15, 4)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_dwt_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id)
);
COMMENT ON TABLE dwt_m IS '배수지 자식 (facility_type_cd=DWT, JPA JOINED 자식 — 자식 컬럼 2건 min_req_prsr·min_req_branch_prsr)';
COMMENT ON COLUMN dwt_m.facility_id         IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준)';
COMMENT ON COLUMN dwt_m.min_req_prsr        IS '최소 요구 압력 (kgf/cm², DOM_QTY_15_4 NOT NULL — 인터록 평가 기준값, pumpcontrol_null_alignment ANALYZE1 더 엄격 NULL 정책)';
COMMENT ON COLUMN dwt_m.min_req_branch_prsr IS '분기점 최소 요구 압력 (kgf/cm², DOM_QTY_15_4 NOT NULL — 배수지로 분기되는 관로 분기점 지점의 최소 요구 압력. 송수펌프제어분석-5번섹션 ANALYZE1)';

-- ----------------------------------------------------------------------------
-- 4. 저수지 자식 (RSV, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE rsv_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_rsv_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id)
);
COMMENT ON TABLE rsv_m IS '저수지 자식 (facility_type_cd=RSV, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton)';
COMMENT ON COLUMN rsv_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준)';

-- ----------------------------------------------------------------------------
-- 5. 관로 계측 분기점 자식 (POINT, JPA JOINED 자식, skeleton) — V7_1 도입
-- ----------------------------------------------------------------------------
CREATE TABLE point_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_point_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT
);
COMMENT ON TABLE point_m IS '관로 계측 분기점 자식 (facility_type_cd=POINT, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton, FK ON DELETE RESTRICT)';
COMMENT ON COLUMN point_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준, FK ON DELETE RESTRICT)';

-- ----------------------------------------------------------------------------
-- 6. 가압장 자식 (PRSF, JPA JOINED 자식, skeleton) — V8_1 도입
-- ----------------------------------------------------------------------------
CREATE TABLE prsf_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_prsf_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT
);
COMMENT ON TABLE prsf_m IS '가압장 자식 (facility_type_cd=PRSF, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton, PWTF 와 동일한 정수지 단위 모니터링 대상)';
COMMENT ON COLUMN prsf_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준, FK ON DELETE RESTRICT)';

-- ============================================================================
-- V2_1 patch — 운영시설 자식 7종 (시설_도메인_확장 ANALYZE1·PLAN1, 2026-06-08)
-- ----------------------------------------------------------------------------
-- 운영본: common/src/main/resources/db/migration/V2_1__facility_patch.sql
-- 모두 자식 전용 컬럼 0건 skeleton, FacilityGroup.OPERATION 운영시설, FK ON DELETE RESTRICT.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 7. 송수동 자식 (WTBLD, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE wtbld_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_wtbld_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT
);
COMMENT ON TABLE wtbld_m IS '송수동 자식 (facility_type_cd=WTBLD, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton, 운영시설, FK ON DELETE RESTRICT)';
COMMENT ON COLUMN wtbld_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준, FK ON DELETE RESTRICT)';

-- ----------------------------------------------------------------------------
-- 8. 약품동 자식 (CHMB, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE chmb_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_chmb_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT
);
COMMENT ON TABLE chmb_m IS '약품동 자식 (facility_type_cd=CHMB, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton, 운영시설, FK ON DELETE RESTRICT)';
COMMENT ON COLUMN chmb_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준, FK ON DELETE RESTRICT)';

-- ----------------------------------------------------------------------------
-- 9. 활성탄여과지 자식 (ACFB, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE acfb_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_acfb_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT
);
COMMENT ON TABLE acfb_m IS '활성탄여과지 자식 (facility_type_cd=ACFB, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton, 운영시설, FK ON DELETE RESTRICT)';
COMMENT ON COLUMN acfb_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준, FK ON DELETE RESTRICT)';

-- ----------------------------------------------------------------------------
-- 10. 전오존동 자식 (POZB, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE pozb_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_pozb_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT
);
COMMENT ON TABLE pozb_m IS '전오존동 자식 (facility_type_cd=POZB, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton, 운영시설, FK ON DELETE RESTRICT)';
COMMENT ON COLUMN pozb_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준, FK ON DELETE RESTRICT)';

-- ----------------------------------------------------------------------------
-- 11. 여과지동 자식 (FLTB, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE fltb_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_fltb_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT
);
COMMENT ON TABLE fltb_m IS '여과지동 자식 (facility_type_cd=FLTB, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton, 운영시설, FK ON DELETE RESTRICT)';
COMMENT ON COLUMN fltb_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준, FK ON DELETE RESTRICT)';

-- ----------------------------------------------------------------------------
-- 12. 탈수기동 자식 (DEWB, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE dewb_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_dewb_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT
);
COMMENT ON TABLE dewb_m IS '탈수기동 자식 (facility_type_cd=DEWB, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton, 운영시설, FK ON DELETE RESTRICT)';
COMMENT ON COLUMN dewb_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준, FK ON DELETE RESTRICT)';

-- ----------------------------------------------------------------------------
-- 13. 태양광 자식 (SOLAR, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE solar_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_solar_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT
);
COMMENT ON TABLE solar_m IS '태양광 자식 (facility_type_cd=SOLAR, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton, 운영시설, FK ON DELETE RESTRICT)';
COMMENT ON COLUMN solar_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준, FK ON DELETE RESTRICT)';
