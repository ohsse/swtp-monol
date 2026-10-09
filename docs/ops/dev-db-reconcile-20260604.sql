-- ============================================================================
-- dev-db 현행 정합화 스크립트 (2026-06-04)
-- ----------------------------------------------------------------------------
-- 목적:
--   기존 dev-db(과거 마이그레이션 누적 환경)를 현행 코드/마이그레이션
--   (V1~V9 + V8_1 + V8_2) 기준으로 정합화한다. 기존 데이터는 보존한다.
--
-- 적용 대상: dev-db (기존 환경)
-- 적용 방식: psql -f (사용자 직접 적용 — dev MCP 는 read-only)
--
-- ⚠️ 트랜잭션 주의:
--   본 스크립트는 CREATE INDEX CONCURRENTLY 를 포함한다.
--   CONCURRENTLY 는 트랜잭션 블록 내부에서 실행 불가하므로,
--   psql 기본 autocommit 으로 실행한다 (BEGIN/COMMIT 으로 감싸지 말 것,
--   psql -1 / --single-transaction 옵션 사용 금지).
--
-- 멱등성: 모든 문장이 IF NOT EXISTS — 2회 이상 재실행해도 안전(0 변경).
--
-- 범위 결정 (사용자 승인 2026-06-04):
--   - 현행 정합화만 수행 (전체 재적용 금지 — db/migration/README §2)
--   - 백지화 잔재 고아 테이블 6종(drvn_anls_dwld_h*, pump_cmbn_m/d,
--     pump_ctrl_h*, pump_interlock_p, pump_predc_h*)은 그대로 둠 (DROP 안 함)
--
-- 참조:
--   - common/src/main/resources/db/migration/V8_2__tag_patch.sql
--   - common/src/main/resources/db/migration/V5__opt.sql / V7__raw.sql (파티션)
--   - .claude/rules/db/partitioning-and-retention.md §1 (6개월 선행 파티션)
--   - .claude/rules/db/indexing-and-migration.md §1 (CONCURRENTLY 인덱스)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- [필수] (1) V8_2 누락 인덱스 — repo↔dev-db 유일 스키마 드리프트 해소
--   FacilityOperatingStatusService 의 'PUMP 자식 instrument_id 목록 →
--   OPS/PWI 태그 조회' 핫패스 가속. use_yn='Y' 부분 인덱스로 비활성 태그 제외.
--   common/.../db/migration/V8_2__tag_patch.sql 와 동일 문장.
-- ----------------------------------------------------------------------------
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tag_m_instrument_id_tag_se_cd
    ON tag_m (instrument_id, tag_se_cd)
    WHERE use_yn = 'Y';

-- ----------------------------------------------------------------------------
-- [권장] (2) 시계열 파티션 6개월 선행 보충 (202611·202612)
--   현재 dev-db 파티션은 202605~202610(10월)까지만 존재.
--   2026-06 기준 "운영 기간보다 최소 6개월 선행" 규칙 충족을 위해
--   202611·202612 를 추가한다 (rawdata_1m_h, predc_1m_h 각각).
--   파티션 미생성 시 해당 월 INSERT 실패 (partitioning-and-retention.md §1).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rawdata_1m_h_202611 PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS rawdata_1m_h_202612 PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

CREATE TABLE IF NOT EXISTS predc_1m_h_202611 PARTITION OF predc_1m_h
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS predc_1m_h_202612 PARTITION OF predc_1m_h
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

-- ----------------------------------------------------------------------------
-- [필수] (3) V8_3 tag_se_cd 코멘트 PWQ 반영 (8종 → 9종)
--   TagMeasurementType enum 에 PWQ(적산전력량, kWh) 추가(2026-06-02)되었으나
--   dev-db 코멘트는 V8_1 시점 8종(FQI까지)으로 stale. 9종으로 동기화.
--   common/.../db/migration/V8_3__tag_patch.sql 와 동일 문장 (스키마 변경 없음, 멱등 덮어쓰기).
--   ※ CMD(운전제어)는 enum 미구현(20260604 ANALYZE만 존재)이라 제외.
-- ----------------------------------------------------------------------------
COMMENT ON COLUMN tag_m.tag_se_cd IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI/PWQ 9종, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수. PWQ=적산전력량 kWh, PWI(순시전력 kW)와 단위·물리 성격 분리, 송수펌프가동이력_3번섹션 ANALYZE1 2026-06-02)';

-- ============================================================================
-- 끝. 적용 후 검증은 dev-db-reconcile-20260604.md §검증 참조.
-- ============================================================================
