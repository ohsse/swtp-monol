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
COMMENT ON COLUMN tag_m.tag_se_cd     IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수. FQI 는 INVERTER_DRIVE 펌프에만 허용 — TagService.validateFqiTagAllowance 차단, tag_frequency_추가 ANALYZE1 2026-05-20)';
COMMENT ON COLUMN tag_m.tag_desc      IS '태그 설명 (DOM_TEXT — 자유형 설명, NULL 허용)';
COMMENT ON COLUMN tag_m.io_cd         IS '입출력 구분 코드 (DOM_CODE_20 — IoCode enum 매핑 INPUT/OUTPUT/BIDIR)';
COMMENT ON COLUMN tag_m.use_yn        IS '사용 여부 (DOM_YN — Y·N enum 매핑, 논리 삭제 지원, rawdata_1m_h.tag_srl_no 논리 참조 보존)';
COMMENT ON COLUMN tag_m.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN tag_m.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN tag_m.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN tag_m.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- FK 조회 가속
CREATE INDEX idx_tag_m_instrument_id ON tag_m (instrument_id);

-- ============================================================================
-- V8_1 패치 — 주파수측정유형등록 ANALYZE1 (2026-05-20)
-- ----------------------------------------------------------------------------
-- 운영본: common/src/main/resources/db/migration/V8_1__tag_patch.sql
--   1) 시드 데이터 정정: tag_se_cd='SPI' → 'FQI' 4건 (인버터 펌프 주파수 태그 정식 코드값)
--   2) tag_m.tag_se_cd 컬럼 COMMENT 본문 — 7종 → 8종 (FQI 포함) 열거 갱신
-- 무중단: 12행 미만 ROW EXCLUSIVE 락 밀리초.
-- 멱등성: WHERE 조건 + COMMENT 덮어쓰기 보장.
-- 롤백: UPDATE tag_m SET tag_se_cd='SPI' WHERE tag_se_cd='FQI'; + COMMENT 원복.
-- ============================================================================

UPDATE tag_m SET tag_se_cd = 'FQI' WHERE tag_se_cd = 'SPI';

COMMENT ON COLUMN tag_m.tag_se_cd IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI 8종, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수)';

-- ============================================================================
-- V8_2 패치 — 운전현황분석-4번섹션 ANALYZE1 (2026-05-21)
-- ----------------------------------------------------------------------------
-- 운영본: common/src/main/resources/db/migration/V8_2__tag_patch.sql
--
-- 변경:
--   tag_m (instrument_id, tag_se_cd) WHERE use_yn = 'Y' 부분 복합 인덱스 추가
--   → FacilityOperatingStatusService 의 'PUMP 자식 instrument_id 목록 → OPS/PWI 태그 조회' 핫패스 가속
--   → 기존 idx_tag_m_instrument_id (instrument_id 단독) 와 별도 — tag_se_cd 필터까지 인덱스 커버
--   → use_yn = 'Y' 부분 인덱스로 비활성 태그 제외 (논리 삭제 정합)
--
-- 무중단: CONCURRENTLY (운영 중 ACCESS EXCLUSIVE 락 회피, db/indexing-and-migration.md §1).
-- 멱등성: IF NOT EXISTS (반복 적용 안전).
-- 롤백: DROP INDEX CONCURRENTLY IF EXISTS idx_tag_m_instrument_id_tag_se_cd;
-- ============================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tag_m_instrument_id_tag_se_cd
    ON tag_m (instrument_id, tag_se_cd)
    WHERE use_yn = 'Y';

-- ============================================================================
-- V8_3 패치 — tag_se_cd 컬럼 COMMENT 에 PWQ 반영 (8종 → 9종)
-- ----------------------------------------------------------------------------
-- 운영본: common/src/main/resources/db/migration/V8_3__tag_patch.sql
--
-- 변경:
--   TagMeasurementType enum 의 PWQ(적산전력량, kWh — 송수펌프가동이력_3번섹션
--   ANALYZE1 안건 1, 2026-06-02) 가 tag_se_cd 컬럼 COMMENT 에 누락(8종 stale)되어
--   있던 것을 9종(PWQ 포함) 으로 동기화.
-- 스키마 변경 없음 (코드값 VARCHAR(20), COMMENT 카탈로그 메타만 갱신).
-- 무중단: COMMENT ON COLUMN — 락 없음. 멱등: 덮어쓰기.
-- 비고: CMD(운전제어)는 제어이력 재도입 미구현 상태라 본 패치 제외 (구현 시 별도 10종 갱신).
-- ============================================================================

COMMENT ON COLUMN tag_m.tag_se_cd IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI/PWQ 9종, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수. PWQ=적산전력량 kWh, PWI(순시전력 kW)와 단위·물리 성격 분리, 송수펌프가동이력_3번섹션 ANALYZE1 2026-06-02)';

-- ============================================================================
-- V8_4 패치 — tag_se_cd 컬럼 COMMENT 에 CMD 반영 (9종 → 10종)
-- ----------------------------------------------------------------------------
-- 운영본: common/src/main/resources/db/migration/V8_4__tag_patch.sql
--
-- 변경:
--   제어이력 재도입(제어이력 재도입 ANALYZE1 안건 5, 2026-06-04)으로 추가된
--   TagMeasurementType enum 의 CMD(운전제어) 를 tag_se_cd 컬럼 COMMENT 에 반영.
--   V8_3 가 보류했던 "구현 사이클 별도 10종 갱신" 을 본 패치로 이행.
-- 스키마 변경 없음 (코드값 VARCHAR(20), COMMENT 카탈로그 메타만 갱신).
-- 무중단: COMMENT ON COLUMN — 락 없음. 멱등: 덮어쓰기.
-- 비고: CMD=운전제어 (io_cd='OUTPUT' 제어 태그 식별용, 단위 없음). 가동상태 OPS(INPUT)와 별개 태그.
-- ============================================================================

COMMENT ON COLUMN tag_m.tag_se_cd IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI/PWQ/CMD 10종, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수. CMD=운전제어 io_cd=OUTPUT 제어 태그, OPS(가동상태 INPUT)와 별개. 제어이력 재도입 ANALYZE1 2026-06-04)';

-- ============================================================================
-- V8_5 패치 — tag_se_cd 컬럼 COMMENT 에 FRQ 반영 (10종 → 11종)
-- ----------------------------------------------------------------------------
-- 운영본: common/src/main/resources/db/migration/V8_5__tag_patch.sql
--
-- 변경:
--   계측기 태그 시딩(2026-06-10)으로 TagMeasurementType enum 에 FRQ(유량적산, m³) 추가 +
--   FRI 한글 설명 '유량' → '유량순시' 변경. tag_se_cd 컬럼 COMMENT 를 11종(FRQ 포함) 으로 동기화.
-- 스키마 변경 없음 (코드값 VARCHAR(20), COMMENT 카탈로그 메타만 갱신).
-- 무중단: COMMENT ON COLUMN — 락 없음. 멱등: 덮어쓰기.
-- 비고: FRQ=유량적산 (누적 적산 유량 m³). FRI(유량순시 m³/h)와 단위·물리 성격 분리 (PWI/PWQ 쌍 동형).
--       ※ 사용자 직접 결정 도입 — /dev:analyze 미경유 (FQI/PWQ/CMD 선례와 차이). standard-terms.md 병행 등재.
-- ============================================================================

COMMENT ON COLUMN tag_m.tag_se_cd IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI/PWQ/CMD/FRQ 11종, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수. FRQ=유량적산 m³, FRI(유량순시 m³/h)와 단위·물리 성격 분리(PWI/PWQ 쌍 동형). 계측기 태그 시딩 2026-06-10)';
