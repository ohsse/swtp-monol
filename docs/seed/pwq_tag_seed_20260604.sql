-- ============================================================================
-- 시드 — 송수펌프 PWQ(적산전력량) 태그 등록 (PWI 명명규칙 미러, 2026-06-04)
-- ----------------------------------------------------------------------------
-- 대상 DB: dev (swtp). 운영본 아님 — 화면/시계열 산정용 PWQ 태그 dev 등록.
--
-- 배경: 송수펌프 전력량(kWh) 시계열 산정을 위해 PWQ 적산전력량 태그가 필요.
--   기존 PWI(순시전력, kW) 태그가 8개 펌프 전체에 등록되어 있어, 동일 명명규칙으로
--   PWQ 8건을 미러 등록한다 (instrument_id 는 해당 펌프의 PWI 태그와 동일).
--   tag_se_cd='PWQ' 는 TagMeasurementType enum + standard-terms.md 기등록.
--
-- 명명규칙 (PWI 미러):
--   tag_srl_no : PWTF{n}-PUMP{m}-PWQ        (PWI 는 ...-PWI)
--   tag_desc   : 정수조#{n}_펌프#{m}_적산전력량  (PWI 는 ..._순시전력)
--   io_cd      : INPUT  (SCADA→백엔드 수신 측정값)
--   use_yn     : Y
--
-- 멱등성: 전 행 rgstr_id='seed' 마커, 상단 DELETE 로 재적재 안전.
--   ⚠️ tag_se_cd='PWQ' 로 스코프 — 같은 'seed' 마커의 CMD 시드(pump_ctrl_h_seed)
--      태그를 건드리지 않음.
-- ============================================================================

-- 0. 기존 PWQ 시드 정리 (재적재 멱등)
DELETE FROM tag_m WHERE tag_se_cd = 'PWQ' AND rgstr_id = 'seed';

-- 1. PWQ 적산전력량 태그 (tag_se_cd='PWQ', io_cd='INPUT', use_yn='Y') — 펌프 8개 각 1건
INSERT INTO tag_m (tag_srl_no, instrument_id, tag_se_cd, tag_desc, io_cd, use_yn,
                   rgstr_dtm, updt_dtm, rgstr_id, updt_id)
VALUES
  ('PWTF1-PUMP1-PWQ', 'cbbeb733-7f70-4794-bfaa-46514af1fbb0', 'PWQ', '정수조#1_펌프#1_적산전력량', 'INPUT', 'Y',
   now(), now(), 'seed', 'seed'),
  ('PWTF1-PUMP2-PWQ', '555c5d42-007e-4608-a60c-242ff8fa7533', 'PWQ', '정수조#1_펌프#2_적산전력량', 'INPUT', 'Y',
   now(), now(), 'seed', 'seed'),
  ('PWTF1-PUMP3-PWQ', 'c81fa0f0-8d71-430a-85ed-8596439db257', 'PWQ', '정수조#1_펌프#3_적산전력량', 'INPUT', 'Y',
   now(), now(), 'seed', 'seed'),
  ('PWTF1-PUMP4-PWQ', 'bab28c92-1818-4452-9dc0-21e784534687', 'PWQ', '정수조#1_펌프#4_적산전력량', 'INPUT', 'Y',
   now(), now(), 'seed', 'seed'),
  ('PWTF2-PUMP1-PWQ', '8a3485c1-5365-4ef2-baee-a356fb2a60ec', 'PWQ', '정수조#2_펌프#1_적산전력량', 'INPUT', 'Y',
   now(), now(), 'seed', 'seed'),
  ('PWTF2-PUMP2-PWQ', 'e3fe17f4-a442-4e20-bb2e-b3078221fb70', 'PWQ', '정수조#2_펌프#2_적산전력량', 'INPUT', 'Y',
   now(), now(), 'seed', 'seed'),
  ('PWTF2-PUMP3-PWQ', '93f77a1f-ad91-47bc-b8e0-5ba749c347eb', 'PWQ', '정수조#2_펌프#3_적산전력량', 'INPUT', 'Y',
   now(), now(), 'seed', 'seed'),
  ('PWTF2-PUMP4-PWQ', '1ffe4328-1a5c-4bbe-b0b9-4ebb2d702a5e', 'PWQ', '정수조#2_펌프#4_적산전력량', 'INPUT', 'Y',
   now(), now(), 'seed', 'seed');
