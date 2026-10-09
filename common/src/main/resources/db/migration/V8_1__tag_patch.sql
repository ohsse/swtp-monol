-- ============================================================================
-- V8_1 tag 도메인 패치 — TagMeasurementType.FQI 신규 enum 값 등재 정합화
-- ----------------------------------------------------------------------------
-- 본 패치는 주파수측정유형등록 ANALYZE1 (2026-05-20) 의 후속 마이그레이션이다.
--
-- 변경:
--   1) tag_m 의 tag_se_cd='SPI' (시드 데이터 임시 채택값) 4건을 'FQI' 로 UPDATE
--      (인버터 펌프 운전 주파수 측정 태그 정식 코드값 정정)
--   2) tag_m.tag_se_cd 컬럼 COMMENT 본문 — 7종 → 8종 (FQI 포함) 열거 갱신
--
-- 무중단:
--   - 12행 미만 소량 UPDATE, ROW EXCLUSIVE 락, 밀리초 단위, CONCURRENTLY 불필요
--   - COMMENT ON COLUMN 은 카탈로그 메타만 갱신, 운영 영향 없음
--
-- 멱등성:
--   - WHERE tag_se_cd='SPI' 조건 — SPI 잔재 0건 환경에서 재실행 시 0행 갱신 (오류 없음)
--   - COMMENT 는 마지막 실행값으로 단일 정의 (덮어쓰기 멱등)
--
-- 롤백:
--   UPDATE tag_m SET tag_se_cd = 'SPI' WHERE tag_se_cd = 'FQI';
--   COMMENT ON COLUMN tag_m.tag_se_cd IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수)';
--
-- 참조:
--   - docs/analyze/20260520/주파수측정유형등록/ANALYZE1.md (안건 1·4)
--   - docs/plan/20260520/주파수측정유형등록/PLAN1.md §DB 설계 변경
--   - .claude/rules/db/indexing-and-migration.md §5.4 V{N} 동결 정책
-- ============================================================================

-- 1. 시드 데이터 정정: SPI → FQI
UPDATE tag_m SET tag_se_cd = 'FQI' WHERE tag_se_cd = 'SPI';

-- 2. 컬럼 COMMENT 갱신 (7종 → 8종 열거)
COMMENT ON COLUMN tag_m.tag_se_cd IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI 8종, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수)';
