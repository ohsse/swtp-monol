-- ============================================================================
-- 화면 확인용 시드 — 송수펌프 제어이력 2·3번 섹션 (제어이력 재도입, 2026-06-04)
-- ----------------------------------------------------------------------------
-- 대상 DB: dev (swtp). 운영본 아님 — 화면/Swagger 실조회 + EXPLAIN 파티션 프루닝 검증용 더미.
--
-- 멱등성: 모든 시드 행은 rgstr_id='seed' 마커를 가지며, 상단 DELETE 로 재적재 안전.
--
-- 분포 (2026-06 조회 기준, 이미지 정합):
--   섹션2 전체 102 = AI 68 + AI추천(AI_RECOMD) 34 + AI분석(AI_ANLS) 0
--   + 수동(ai_drvn_mod NULL) 8건 → 섹션2 집계 제외, 섹션3 목록에만 노출
--   섹션3 목록 총 110건 (ctrl_dtm DESC), 펌프#1~#4 순환, 제어구분 START/STOP 교대,
--   제어결과 COMPLETED 다수 + CANCELLED 일부(10건당 1건)
--
-- 데이터 위치: 전 행 2026-06 범위 → 단일 파티션 pump_ctrl_h_202606 (프루닝 검증 가능)
-- ============================================================================

-- 0. 기존 시드 정리 (재적재 멱등)
DELETE FROM pump_ctrl_h WHERE rgstr_id = 'seed';
DELETE FROM tag_m WHERE tag_se_cd = 'CMD' AND rgstr_id = 'seed';

-- 1. 제어 태그 (tag_se_cd='CMD', io_cd='OUTPUT', use_yn='Y') — 펌프#1~#4 각 1건
INSERT INTO tag_m (tag_srl_no, instrument_id, tag_se_cd, tag_desc, io_cd, use_yn,
                   rgstr_dtm, updt_dtm, rgstr_id, updt_id)
VALUES
  ('706-CMD-001-001', 'cbbeb733-7f70-4794-bfaa-46514af1fbb0', 'CMD', '펌프#1 운전제어 태그', 'OUTPUT', 'Y',
   now(), now(), 'seed', 'seed'),
  ('706-CMD-002-001', 'e3fe17f4-a442-4e20-bb2e-b3078221fb70', 'CMD', '펌프#2 운전제어 태그', 'OUTPUT', 'Y',
   now(), now(), 'seed', 'seed'),
  ('706-CMD-003-001', 'c81fa0f0-8d71-430a-85ed-8596439db257', 'CMD', '펌프#3 운전제어 태그', 'OUTPUT', 'Y',
   now(), now(), 'seed', 'seed'),
  ('706-CMD-004-001', 'bab28c92-1818-4452-9dc0-21e784534687', 'CMD', '펌프#4 운전제어 태그', 'OUTPUT', 'Y',
   now(), now(), 'seed', 'seed');

-- 2. pump_ctrl_h 더미 제어 이력 110건 (generate_series 0~109)
INSERT INTO pump_ctrl_h (pump_ctrl_id, ctrl_dtm, instrument_id, ctrl_div, ctrl_rslt, ai_drvn_mod,
                         rgstr_dtm, updt_dtm, rgstr_id, updt_id)
SELECT
    nextval('seq_pump_ctrl_id'),
    TIMESTAMP '2026-06-01 08:00:00' + (n * INTERVAL '90 minutes'),
    (ARRAY['cbbeb733-7f70-4794-bfaa-46514af1fbb0',
           'e3fe17f4-a442-4e20-bb2e-b3078221fb70',
           'c81fa0f0-8d71-430a-85ed-8596439db257',
           'bab28c92-1818-4452-9dc0-21e784534687'])[1 + (n % 4)],
    (ARRAY['START', 'STOP'])[1 + (n % 2)],
    CASE WHEN n % 10 = 0 THEN 'CANCELLED' ELSE 'COMPLETED' END,
    CASE WHEN n < 68  THEN 'AI'
         WHEN n < 102 THEN 'AI_RECOMD'
         ELSE NULL END,
    TIMESTAMP '2026-06-01 08:00:00' + (n * INTERVAL '90 minutes'),
    TIMESTAMP '2026-06-01 08:00:05' + (n * INTERVAL '90 minutes'),
    'seed', 'seed'
FROM generate_series(0, 109) AS n;
