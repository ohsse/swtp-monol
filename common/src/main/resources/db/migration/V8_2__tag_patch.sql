-- ============================================================================
-- V8_2 패치 — 운전현황분석-4번섹션 ANALYZE1 (2026-05-21)
-- ----------------------------------------------------------------------------
-- 운영본: common/src/main/resources/db/migration/V8_2__tag_patch.sql
-- SSOT 사본: backend/docs/ddl/tag.sql (동시 갱신 의무 — indexing-and-migration.md §5)
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
