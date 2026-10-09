-- ============================================================================
-- dev 더미 측정데이터 생성 프로시저 + pgAgent 매분 스케줄 (2026-06-04)
-- ----------------------------------------------------------------------------
-- 대상 DB: dev (swtp). 운영본 아님 — 화면/시계열 차트 검증용 더미데이터 생성기.
--   ⚠️ 운영 마이그레이션 V* 에 미포함 (dev 전용). docs/seed/ 보관.
--
-- 배경: rawdata_1m_h(실시간 계측) / predc_1m_h(예측) 시계열이 dev 에 0건이라
--   시계열 차트·운전현황 화면을 검증할 수 없다. tag_m 에 등록된 태그를 동적으로
--   읽어 매분 더미 측정값을 두 테이블에 적재한다 (신규 태그 자동 반영).
--
-- 생성 규칙 (raw·predc 공통, CMD 제어 태그 제외):
--   - OPS (가동상태)     : 고정 1 (전부 가동 가정)
--   - PWQ (적산전력량)   : 직전 동일 태그 값 + random()*10  → 시간 흐름 우상향 누적
--   - 그 외 순시값(FRI/PRI/PWI/FQI/LEI/VOI/RMS …) : random()*20  (0~20)
--
-- 시각 기준:
--   - raw   acq_dtm   = date_trunc('minute', now())            (해당 분 계측값)
--   - predc predc_dtm = date_trunc('minute', now()) + 1 minute (1분 앞선 예측값)
--   실행 시점(초)과 무관 — date_trunc 로 분 정규화.
--
-- 멱등성: INSERT 전 (tag_srl_no, 분) 존재 가드 → 같은 분 재호출/중복 스케줄 시 중복 없음.
--   메타 컬럼은 순수 SQL 이라 AuditingEntityListener 미동작 → 프로시저가 직접 채움
--   (rgstr_id/updt_id = 'dummy_job', rgstr_dtm/updt_dtm = now(), quality_cd='GOOD').
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 더미 측정데이터 생성 프로시저
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE generate_dummy_measurements()
LANGUAGE plpgsql
AS $$
DECLARE
    v_acq_dtm   TIMESTAMP   := date_trunc('minute', now());                       -- raw 계측 시각 (분 절삭)
    v_predc_dtm TIMESTAMP   := date_trunc('minute', now()) + INTERVAL '1 minute'; -- 예측 시각 (1분 앞)
    v_actor     VARCHAR(50) := 'dummy_job';
    rec         RECORD;
    v_raw_val   NUMERIC(15, 4);
    v_predc_val NUMERIC(15, 4);
    v_prev      NUMERIC(15, 4);
BEGIN
    -- tag_m 에서 사용중(use_yn='Y') + 제어(CMD) 제외 태그 동적 순회
    FOR rec IN
        SELECT tag_srl_no, tag_se_cd
        FROM tag_m
        WHERE use_yn = 'Y'
          AND tag_se_cd <> 'CMD'
    LOOP
        -- ===== 실시간(raw) — 해당 분 계측값 =====
        IF NOT EXISTS (
            SELECT 1 FROM rawdata_1m_h
            WHERE tag_srl_no = rec.tag_srl_no AND acq_dtm = v_acq_dtm
        ) THEN
            IF rec.tag_se_cd = 'OPS' THEN
                v_raw_val := 1;                                            -- 가동상태 고정 1
            ELSIF rec.tag_se_cd = 'PWQ' THEN
                SELECT raw_val INTO v_prev                                 -- 직전 적산값 조회
                FROM rawdata_1m_h
                WHERE tag_srl_no = rec.tag_srl_no
                ORDER BY acq_dtm DESC
                LIMIT 1;
                v_raw_val := COALESCE(v_prev, 0) + (random() * 10)::NUMERIC(15, 4); -- 우상향 누적
            ELSE
                v_raw_val := (random() * 20)::NUMERIC(15, 4);             -- 순시값 0~20
            END IF;

            INSERT INTO rawdata_1m_h
                (rawdata_id, acq_dtm, tag_srl_no, raw_val, corr_val, quality_cd,
                 rgstr_dtm, updt_dtm, rgstr_id, updt_id)
            VALUES
                (nextval('seq_rawdata_id'), v_acq_dtm, rec.tag_srl_no, v_raw_val, NULL, 'GOOD',
                 now(), now(), v_actor, v_actor);
        END IF;

        -- ===== 예측(predc) — 1분 앞선 예측값 (raw 와 동일 규칙) =====
        IF NOT EXISTS (
            SELECT 1 FROM predc_1m_h
            WHERE tag_srl_no = rec.tag_srl_no AND predc_dtm = v_predc_dtm
        ) THEN
            IF rec.tag_se_cd = 'OPS' THEN
                v_predc_val := 1;
            ELSIF rec.tag_se_cd = 'PWQ' THEN
                SELECT predc_val INTO v_prev                               -- 예측 테이블 직전 적산값
                FROM predc_1m_h
                WHERE tag_srl_no = rec.tag_srl_no
                ORDER BY predc_dtm DESC
                LIMIT 1;
                v_predc_val := COALESCE(v_prev, 0) + (random() * 10)::NUMERIC(15, 4);
            ELSE
                v_predc_val := (random() * 20)::NUMERIC(15, 4);
            END IF;

            INSERT INTO predc_1m_h
                (predc_id, predc_dtm, tag_srl_no, predc_val, rgstr_dtm, rgstr_id)
            VALUES
                (nextval('seq_predc_id'), v_predc_dtm, rec.tag_srl_no, v_predc_val,
                 now(), v_actor);
        END IF;
    END LOOP;
END;
$$;

COMMENT ON PROCEDURE generate_dummy_measurements() IS
    'dev 전용 더미 측정데이터 생성기 — tag_m(CMD 제외) 기반 rawdata_1m_h(해당 분)·predc_1m_h(1분 앞) 매분 적재. OPS=1, PWQ=우상향 누적, 그 외=0~20 랜덤';

-- ----------------------------------------------------------------------------
-- 2. pgAgent 매분 스케줄 등록 (dev 적용·실행 확인 완료 2026-06-04)
-- ----------------------------------------------------------------------------
-- ⚠️ pgAgent 컨트롤 평면 vs 실행 평면 분리 (핵심):
--   - 컨트롤 평면 = 데몬이 "접속한 DB" 의 pgagent 스키마. 데몬은 이 DB 의 pga_job/schedule 만 폴링.
--   - 실행 평면   = 각 step 의 jstdbname 이 가리키는 DB. 데몬이 그 DB 에 접속해 jstcode 를 실행.
--   ⇒ job 정의는 "데몬 접속 DB" 에 등록하고, step jstdbname 으로 실제 실행 DB(swtp) 를 지정한다.
--
-- dev 실제 배포 구조 (2026-06-04 확인):
--   - 데몬: postgres 계정 + postgres DB 에 연결·실행 중 (jagstation=DESKTOP-U17GM2C).
--   - 따라서 본 등록 DO 블록은 ★ postgres DB ★ 에 대해 실행한다 (swtp 아님).
--   - step jstdbname='swtp' → 데몬이 swtp 에 접속해 CALL generate_dummy_measurements() 실행.
--   - 프로시저 자체(§1)는 swtp DB 에 존재 (실행 평면).
--   ※ 만약 데몬을 swtp DB 에 직접 붙인다면, 본 블록을 swtp 에 등록하면 된다 (동일 구조, 접속 DB만 다름).
--
-- 데몬 연결 확인 (데몬 접속 DB 에서): SELECT jagpid, jagstation FROM pgagent.pga_jobagent;  (행 있으면 연결됨)
--
-- [등록] ★ postgres DB 에 실행 ★ — 매분 swtp 에서 CALL. 멱등(기존 동일 job 삭제 후 재생성).
DO $$
DECLARE
    v_jobid integer;
    v_jclid integer;
BEGIN
    -- jobclass 확보 (기본 'Routine Maintenance', 없으면 첫 클래스, 그래도 없으면 생성)
    SELECT jclid INTO v_jclid FROM pgagent.pga_jobclass WHERE jclname = 'Routine Maintenance';
    IF v_jclid IS NULL THEN
        SELECT jclid INTO v_jclid FROM pgagent.pga_jobclass ORDER BY jclid LIMIT 1;
    END IF;
    IF v_jclid IS NULL THEN
        INSERT INTO pgagent.pga_jobclass (jclname) VALUES ('Routine Maintenance') RETURNING jclid INTO v_jclid;
    END IF;

    DELETE FROM pgagent.pga_job WHERE jobname = 'dummy_measurement_every_minute'; -- FK CASCADE 로 step·schedule 동시 삭제

    INSERT INTO pgagent.pga_job (jobjclid, jobname, jobdesc, jobenabled)
    VALUES (
        v_jclid,
        'dummy_measurement_every_minute',
        'dev 더미 측정데이터 매분 생성 (rawdata_1m_h + predc_1m_h, CMD 제외, 대상 swtp)',
        true
    ) RETURNING jobid INTO v_jobid;

    INSERT INTO pgagent.pga_jobstep
        (jstjobid, jstname, jstenabled, jstkind, jstonerror, jstdbname, jstcode)
    VALUES (
        v_jobid, 'call_generate_dummy', true, 's', 'f', 'swtp',  -- jstdbname='swtp' = 실행 평면
        'CALL generate_dummy_measurements();'
    );

    INSERT INTO pgagent.pga_schedule
        (jscjobid, jscname, jscenabled, jscstart,
         jscminutes, jschours, jscweekdays, jscmonthdays, jscmonths)
    VALUES (
        v_jobid, 'every_minute', true, now(),
        array_fill(true, ARRAY[60]),   -- 매분 (0~59 전부)
        array_fill(true, ARRAY[24]),   -- 매시
        array_fill(true, ARRAY[7]),    -- 매 요일
        array_fill(true, ARRAY[32]),   -- 매 일 (31 + last-day)
        array_fill(true, ARRAY[12])    -- 매 월
    );
END $$;

-- [비활성] (postgres DB)  UPDATE pgagent.pga_job SET jobenabled = false WHERE jobname = 'dummy_measurement_every_minute';
-- [해제]   (postgres DB)  DELETE FROM pgagent.pga_job WHERE jobname = 'dummy_measurement_every_minute';
-- [확인]   (postgres DB)  SELECT jobid, jobname, jobenabled, joblastrun, jobnextrun FROM pgagent.pga_job;
--          (postgres DB)  SELECT jslstatus, jslresult, jslstart
--                           FROM pgagent.pga_jobsteplog ORDER BY jslstart DESC LIMIT 5;  -- 's'=success 'f'=fail 'r'=running
--          (swtp DB)      SELECT count(*), max(acq_dtm) FROM rawdata_1m_h;               -- 분당 32건 누적 확인
-- ============================================================================
