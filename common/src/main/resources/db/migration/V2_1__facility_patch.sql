-- ============================================================================
-- facility 도메인 patch — 운영시설 자식 7종 추가 + facility_type_cd COMMENT 12종 갱신
-- ----------------------------------------------------------------------------
-- 시설_도메인_확장 ANALYZE1·PLAN1 (2026-06-08).
-- V2__facility.sql (초기 CREATE) 은 동결 — 본 patch 로 자식 7종을 추가한다
-- (db/indexing-and-migration.md §5.4 V{N} 파일 ALTER 누적 금지).
--
-- 신규 자식 종류 7종 (모두 FacilityGroup.OPERATION 운영시설):
--   WTBLD (송수동) · CHMB (약품동) · ACFB (활성탄여과지) · POZB (전오존동)
--   · FLTB (여과지동) · DEWB (탈수기동) · SOLAR (태양광)
--
-- FacilityGroup (저장/운영/계통) 은 FacilityType 파생 enum — DB 컬럼 미신설.
-- 자식 테이블은 모두 자식 전용 컬럼 0건 skeleton (PWTF/RSV/POINT/PRSF 선례 동일).
-- FK ON DELETE RESTRICT — 시설은 논리 삭제만 허용 (instrument_m·rawdata_1m_h 논리 참조).
--
-- 참조:
--   - .claude/rules/entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴
--   - .claude/rules/db/indexing-and-migration.md §4 컬럼 COMMENT 의무화 정책 + §5 SQL 관리
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 부모 마스터 COMMENT 갱신 (자식 5종 → 12종, facility_type_cd 코드값 갱신)
-- ----------------------------------------------------------------------------
COMMENT ON TABLE facility_m IS '시설 단일 마스터 (JPA JOINED 부모, 자식 12종 PWTF·DWT·RSV·POINT·PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR)';
COMMENT ON COLUMN facility_m.facility_type_cd IS '시설 유형 코드 (DOM_CODE_20 — DiscriminatorColumn, FacilityType enum SSOT 매핑 PWTF/DWT/RSV/POINT/PRSF/WTBLD/CHMB/ACFB/POZB/FLTB/DEWB/SOLAR)';

-- ----------------------------------------------------------------------------
-- 2. 송수동 자식 (WTBLD, JPA JOINED 자식, skeleton)
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
-- 3. 약품동 자식 (CHMB, JPA JOINED 자식, skeleton)
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
-- 4. 활성탄여과지 자식 (ACFB, JPA JOINED 자식, skeleton)
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
-- 5. 전오존동 자식 (POZB, JPA JOINED 자식, skeleton)
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
-- 6. 여과지동 자식 (FLTB, JPA JOINED 자식, skeleton)
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
-- 7. 탈수기동 자식 (DEWB, JPA JOINED 자식, skeleton)
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
-- 8. 태양광 자식 (SOLAR, JPA JOINED 자식, skeleton)
-- ----------------------------------------------------------------------------
CREATE TABLE solar_m (
    facility_id  VARCHAR(36)  NOT NULL,
    PRIMARY KEY (facility_id),
    CONSTRAINT fk_solar_m__facility
        FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT
);
COMMENT ON TABLE solar_m IS '태양광 자식 (facility_type_cd=SOLAR, JPA JOINED 자식 — 자식 전용 컬럼 0건 skeleton, 운영시설, FK ON DELETE RESTRICT)';
COMMENT ON COLUMN solar_m.facility_id IS '시설 ID (DOM_ID_36 — facility_m PK 참조 자식 PK 자동 상속, JPA JOINED 표준, FK ON DELETE RESTRICT)';
