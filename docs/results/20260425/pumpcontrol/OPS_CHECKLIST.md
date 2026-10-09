---
status: draft
created: 2026-04-25
updated: 2026-04-25
---
# 송수펌프 제어 도메인 — 운영 체크리스트

본 체크리스트는 [`PLAN1.md §4 파티션 운영`](../../../plan/20260425/pumpcontrol/PLAN1.md) 의 장애 복구 절차와 [`ot-integration.md §4` 회복성 패턴](../../../../.claude/rules/ot-integration.md) 의 모니터링 항목을 운영 환경에서 점검하기 위한 산출물이다.

---

## 1. 파티션 스케줄러 장애 시 수동 복구 SQL

`PumpPartitionScheduler` (`@Scheduled(cron="0 0 0 1 * ?")`) 가 장애로 다음 6개월치 파티션을 생성하지 못한 경우, 운영자가 다음 SQL 을 직접 실행한다.

### 1.1 다음 달 파티션 수동 생성 (예시 — 2026-07)

```sql
-- pump_ctrl_h
CREATE TABLE IF NOT EXISTS pump_ctrl_h_202607
    PARTITION OF pump_ctrl_h
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pump_ctrl_h_202607_pump_dtm
    ON pump_ctrl_h_202607 (pump_id, ctrl_dtm DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pump_ctrl_h_202607_brin
    ON pump_ctrl_h_202607 USING BRIN (ctrl_dtm);

-- pump_predc_h
CREATE TABLE IF NOT EXISTS pump_predc_h_202607
    PARTITION OF pump_predc_h
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pump_predc_h_202607_pwtf_dtm
    ON pump_predc_h_202607 (pwtf_id, predc_base_dtm DESC);

-- ai_drvn_mod_h
CREATE TABLE IF NOT EXISTS ai_drvn_mod_h_202607
    PARTITION OF ai_drvn_mod_h
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ai_drvn_mod_h_202607_pwtf_dtm
    ON ai_drvn_mod_h_202607 (pwtf_id, rgstr_dtm DESC);
```

### 1.2 보존 기간 초과 파티션 수동 DROP

```sql
-- pump_ctrl_h 2년 초과 (2024-04 = 2026-04 - 24개월)
DROP TABLE IF EXISTS pump_ctrl_h_202404;

-- pump_predc_h 3년 초과 (2023-04 = 2026-04 - 36개월)
DROP TABLE IF EXISTS pump_predc_h_202304;

-- ai_drvn_mod_h 5년 초과 (2021-04 = 2026-04 - 60개월)
DROP TABLE IF EXISTS ai_drvn_mod_h_202104;
```

### 1.3 파티션 누락 감지 쿼리

다음 쿼리로 향후 1개월 내 파티션 누락 여부를 점검한다.

```sql
SELECT 'pump_ctrl_h' AS table_name,
       to_char(date_trunc('month', now()) + interval '1 month', 'YYYYMM') AS expected_partition,
       EXISTS (
           SELECT 1 FROM pg_tables WHERE tablename =
               'pump_ctrl_h_' || to_char(date_trunc('month', now()) + interval '1 month', 'YYYYMM')
       ) AS exists
UNION ALL
SELECT 'pump_predc_h',
       to_char(date_trunc('month', now()) + interval '1 month', 'YYYYMM'),
       EXISTS (
           SELECT 1 FROM pg_tables WHERE tablename =
               'pump_predc_h_' || to_char(date_trunc('month', now()) + interval '1 month', 'YYYYMM')
       )
UNION ALL
SELECT 'ai_drvn_mod_h',
       to_char(date_trunc('month', now()) + interval '1 month', 'YYYYMM'),
       EXISTS (
           SELECT 1 FROM pg_tables WHERE tablename =
               'ai_drvn_mod_h_' || to_char(date_trunc('month', now()) + interval '1 month', 'YYYYMM')
       );
```

`exists = false` 행이 발견되면 §1.1 의 수동 생성 SQL 을 즉시 실행한다.

---

## 2. p6spy 슬로우 쿼리 모니터링

### 2.1 임계값
- 기본 `executionThreshold` = 500ms (`common/src/main/resources-env/{프로파일}/spy.properties`)
- 펌프 도메인 신규 쿼리 중 다음 항목이 임계값 초과 시 우선 점검:
  - `PumpControlHistoryRepository.findRecentByPumpId(...)` — `(pump_id, ctrl_dtm DESC)` B-Tree 인덱스 활용
  - `PumpPredictionResultRepository.findLatestByPwtfId(...)` — 정수조별 최신 1건
  - `AiDrvnModeTransitionScheduler` 의 `findAll()` — 정수조 수가 적어 캐시 불필요

### 2.2 분석 절차

```sql
-- 슬로우 쿼리 실행 계획 확인
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM pump_ctrl_h
WHERE pump_id = 'PUMP-001'
  AND ctrl_dtm BETWEEN '2026-04-01' AND '2026-05-01'
ORDER BY ctrl_dtm DESC;
```

- `Seq Scan` 이 발견되면 `(pump_id, ctrl_dtm)` 인덱스 적용 확인
- `Nested Loop` 과다 발생 시 조인 조건 재검토
- 파티션 프루닝이 작동하는지 `Partitions` 라인 확인

상세는 [`db-query-tuning.md §2`](../../../../.claude/rules/db-query-tuning.md) 참조.

---

## 3. AI 서버 다운 시 fallback 동작 확인

### 3.1 정상 동작 확인 절차

1. AI 서버 정상 응답 시: `POST /api/pump/auto-control` 응답에서 `ctrlRslt='SUCCESS'` 반환
2. AI 서버 다운 시: `AiServerClient` CircuitBreaker 가 30초 동안 OPEN 상태 진입
3. CircuitBreaker OPEN 상태에서 호출 시 `fallbackPrediction()` 메서드가 즉시 `RestApiException(AI_PREDICTION_FAILED)` 던짐
4. `PumpControlService` 는 예외를 사용자에게 전달 (HTTP 503)
5. 30초 후 half-open 상태에서 3건의 시험 호출. 50% 이상 성공 시 CLOSED 복구

### 3.2 모니터링 메트릭

resilience4j 메트릭 (`actuator/metrics` 활성화 시):
- `resilience4j.circuitbreaker.calls{name=aiPrediction}`
- `resilience4j.circuitbreaker.state{name=aiPrediction}` — `closed`/`open`/`half_open`
- `resilience4j.retry.calls{name=aiPrediction,kind=successful_with_retry}`

### 3.3 점검 SQL

```sql
-- 최근 10분간 AI 예측 실패로 SCADA 송신이 차단된 건수
SELECT count(*) AS recent_ai_failures
FROM pump_ctrl_h
WHERE ctrl_rslt = 'FAIL'
  AND ctrl_div = 'AUTO'
  AND ctrl_dtm > now() - interval '10 minutes';

-- 최근 강제 모드 전환 (transition_reason 별)
SELECT transition_reason, count(*)
FROM ai_drvn_mod_h
WHERE rgstr_dtm > now() - interval '1 hour'
GROUP BY transition_reason;
```

---

## 4. SCADA 5분 초과 강제 전환 검증

### 4.1 검증 절차

1. `AiModeTransitionScheduler` 가 `@Scheduled(fixedDelay=60_000)` 매 1분 동작
2. 정수조의 `last_rcv_dtm` 이 5분 초과 + `ai_mode_cd=AI_AUTO` 이면 `SEMI_AUTO` 강제 전환
3. `ai_drvn_mod_h.transition_reason='SCADA_TIMEOUT'` 행 추가 확인
4. SCADA 수신 복구 시 `ai_drvn_mod` (사용자 의도) 가 보존되어 있어 자동 환원 가능

### 4.2 점검 SQL

```sql
-- SCADA 수신 5분 초과인데 아직 강제 전환되지 않은 정수조 (스케줄러 정상이면 0건)
SELECT pwtf_id, ai_drvn_mod, ai_mode_cd, last_rcv_dtm,
       extract(epoch from (now() - last_rcv_dtm))/60 AS minutes_since_last_rcv
FROM ai_drvn_mod_p
WHERE last_rcv_dtm < now() - interval '5 minutes'
  AND ai_mode_cd = 'AI_AUTO';

-- 최근 24시간 SCADA 타임아웃 강제 전환 이력
SELECT pwtf_id, prev_ai_mode_cd, new_ai_mode_cd, rgstr_dtm
FROM ai_drvn_mod_h
WHERE transition_reason = 'SCADA_TIMEOUT'
  AND rgstr_dtm > now() - interval '24 hours'
ORDER BY rgstr_dtm DESC;
```

---

## 5. 캐시 stale 방지 검증

`PumpMasterCacheService.existsByPumpId(...)` 5분 TTL Caffeine 캐시. `deactivatePump()` 호출 시 동일 키 즉시 evict.

### 5.1 검증 시나리오

1. `pump_id='PUMP-X'` 활성 상태에서 캐시 적재 (`existsByPumpId('PUMP-X') == true`)
2. `PumpMasterCacheService.deactivatePump('PUMP-X')` 호출
3. 즉시 `existsByPumpId('PUMP-X')` 다시 호출 → DB 재조회되어 비활성 상태 반영
4. (캐시 evict 미동작 시) 5분간 stale 데이터로 인해 `pump_ctrl_h` 에 비활성 펌프 INSERT 발생 — **장애 신호**

### 5.2 캐시 메트릭 (Caffeine)

`actuator/metrics` 활성화 시:
- `cache.gets{cache=pumpMasterExists,result=hit}` — 캐시 히트 카운트
- `cache.gets{cache=pumpMasterExists,result=miss}` — 캐시 미스 카운트
- `cache.evictions{cache=pumpMasterExists}` — evict 카운트 (5분 TTL 만료 + 명시적 evict 합산)

---

## 6. 운영 체크 주기

| 항목 | 주기 | 책임자 |
|------|------|--------|
| 다음 달 파티션 존재 확인 (§1.3) | 매월 1일 자정 직후 + 월 중 1회 | 운영팀 |
| 슬로우 쿼리 로그 점검 (§2) | 매주 1회 | 백엔드 개발팀 |
| AI 서버 회복성 메트릭 (§3.2) | 실시간 모니터링 + 일 1회 리뷰 | 운영팀 |
| SCADA 강제 전환 이력 (§4.2) | 일 1회 리뷰 | 운영팀 + 도메인 전문가 |
| 캐시 메트릭 (§5.2) | 주 1회 | 백엔드 개발팀 |

---

## 7. 관련 산출물

- [PLAN1](../../../plan/20260425/pumpcontrol/PLAN1.md)
- [TASK1-1 데이터 계층](../../../tasks/20260425/pumpcontrol/TASK1-1.md)
- [TASK1-2 애플리케이션 계층](../../../tasks/20260425/pumpcontrol/TASK1-2.md)
- [TASK1-3 인프라·검증](../../../tasks/20260425/pumpcontrol/TASK1-3.md)
- [`db-partitioning-and-retention.md`](../../../../.claude/rules/db-partitioning-and-retention.md)
- [`ot-integration.md`](../../../../.claude/rules/ot-integration.md)
