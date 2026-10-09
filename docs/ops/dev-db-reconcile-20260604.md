# dev-db 현행 정합화 절차 (2026-06-04)

`dev-db` 를 현행 코드/마이그레이션(V1~V9 + V8_1 + V8_2) 기준으로 맞추는 일회성 운영 작업의 적용·검증·롤백 절차다.

- **적용 스크립트**: [`dev-db-reconcile-20260604.sql`](dev-db-reconcile-20260604.sql)
- **적용 주체**: 운영자/사용자 (psql 직접 — dev-db MCP 는 read-only)
- **데이터 영향**: 없음 (기존 데이터 보존, 스키마/파티션 추가만)

---

## 1. 배경 — 왜 정합화가 필요한가

dev-db 는 신규 환경이 아니라 과거 마이그레이션이 누적된 기존 환경이다. 현행 코드의 도메인 23개 테이블은 dev-db 와 구조적으로 일치하나, 다음 차이가 발견되었다.

| # | 항목 | 처리 |
|---|------|------|
| 1 | `tag_m` 의 V8_2 부분 복합 인덱스(`idx_tag_m_instrument_id_tag_se_cd`)가 dev-db 에 미적용 | **본 스크립트로 적용** |
| 2 | `rawdata_1m_h`·`predc_1m_h` 파티션이 202610(10월)까지만 존재 → 6개월 선행 규칙 미달 | **본 스크립트로 202611·202612 보충** |
| 3 | `tag_se_cd` 코멘트가 PWQ 미열거(V8_1 시점 8종 stale) | **본 스크립트로 9종 반영** (repo V8_3 동기화) |
| 4 | 백지화 잔재 고아 테이블 6종(모두 0행) 잔존 | **유지 (사용자 결정, DROP 안 함)** |

> 전체 재적용(V1~V9)은 기존 환경에 **금지**된다(`common/src/main/resources/db/migration/README.md` §2). 본 정합화는 누락분만 추가하는 최소 변경이다.

---

## 2. 사전 확인 (적용 전)

```sql
-- (a) V8_2 인덱스 미존재 확인 (0행이면 적용 필요)
SELECT indexname FROM pg_indexes
WHERE tablename = 'tag_m' AND indexname = 'idx_tag_m_instrument_id_tag_se_cd';

-- (b) 현재 파티션 범위 확인 (202610 까지만 있는지)
SELECT inhrelid::regclass AS partition
FROM pg_inherits
WHERE inhparent IN ('rawdata_1m_h'::regclass, 'predc_1m_h'::regclass)
ORDER BY 1;

-- (c) 보존 데이터 건수 스냅샷 (적용 후 비교용)
SELECT 'user_m' t, count(*) c FROM user_m
UNION ALL SELECT 'facility_m', count(*) FROM facility_m
UNION ALL SELECT 'instrument_m', count(*) FROM instrument_m
UNION ALL SELECT 'tag_m', count(*) FROM tag_m
UNION ALL SELECT 'proc_m', count(*) FROM proc_m;
```

기준 스냅샷(2026-06-04 dev-db): `user_m`=1001, `facility_m`=12, `instrument_m`=10, `tag_m`=24, `proc_m`=1.

---

## 3. 적용

```bash
# 접속 정보는 환경에 맞게 조정. ⚠️ -1 / --single-transaction 옵션 사용 금지
#   (CREATE INDEX CONCURRENTLY 는 트랜잭션 블록 내부 실행 불가)
psql -h <dev-host> -p <port> -U <user> -d <db> \
     -f backend/docs/ops/dev-db-reconcile-20260604.sql
```

- 본 스크립트는 멱등하다(`IF NOT EXISTS`). 2회 이상 재실행해도 안전.
- `CREATE INDEX CONCURRENTLY` 는 운영 중 `ACCESS EXCLUSIVE` 락을 회피하므로 서비스 중단 없이 적용된다.

---

## 4. 검증 (적용 후)

```sql
-- (1) V8_2 인덱스 생성 확인 — 1행 반환되어야 함
SELECT indexname, indexdef FROM pg_indexes
WHERE tablename = 'tag_m' AND indexname = 'idx_tag_m_instrument_id_tag_se_cd';

-- (2) 파티션 4건 추가 확인 — rawdata/predc × 202611/202612 = 4행
SELECT inhrelid::regclass AS partition
FROM pg_inherits
WHERE inhparent IN ('rawdata_1m_h'::regclass, 'predc_1m_h'::regclass)
  AND inhrelid::regclass::text ~ '_(202611|202612)$'
ORDER BY 1;

-- (3) 인덱스 유효성 확인 — indisvalid = true 여야 함 (CONCURRENTLY 실패 시 false)
SELECT c.relname, i.indisvalid
FROM pg_class c
JOIN pg_index i ON i.indexrelid = c.oid
WHERE c.relname = 'idx_tag_m_instrument_id_tag_se_cd';

-- (4) 데이터 무변경 확인 — §2(c) 스냅샷과 동일해야 함
SELECT 'user_m' t, count(*) c FROM user_m
UNION ALL SELECT 'facility_m', count(*) FROM facility_m
UNION ALL SELECT 'instrument_m', count(*) FROM instrument_m
UNION ALL SELECT 'tag_m', count(*) FROM tag_m
UNION ALL SELECT 'proc_m', count(*) FROM proc_m;

-- (5) tag_se_cd 코멘트 PWQ 반영 확인 — true 여야 함
SELECT col_description('tag_m'::regclass, a.attnum) ~ 'PWQ' AS pwq_in_comment
FROM pg_attribute a
WHERE a.attrelid = 'tag_m'::regclass AND a.attname = 'tag_se_cd';
```

성공 기준:
- (1) 1행 반환, `WHERE (use_yn = 'Y'::...)` 부분 인덱스
- (2) 4행 (`predc_1m_h_202611`, `predc_1m_h_202612`, `rawdata_1m_h_202611`, `rawdata_1m_h_202612`)
- (3) `indisvalid = true`
- (4) §2(c) 스냅샷과 동일
- (5) `pwq_in_comment = true` (코멘트가 9종 PWQ 포함)

> ⚠️ (3) 에서 `indisvalid = false` 면 `CONCURRENTLY` 생성이 중도 실패한 것이다. `DROP INDEX CONCURRENTLY IF EXISTS idx_tag_m_instrument_id_tag_se_cd;` 후 재적용한다.

---

## 5. 롤백

```sql
-- 인덱스 제거 (CONCURRENTLY — 트랜잭션 블록 밖에서 실행)
DROP INDEX CONCURRENTLY IF EXISTS idx_tag_m_instrument_id_tag_se_cd;

-- 추가 파티션 제거 (빈 파티션이면 즉시 완료)
DROP TABLE IF EXISTS rawdata_1m_h_202611;
DROP TABLE IF EXISTS rawdata_1m_h_202612;
DROP TABLE IF EXISTS predc_1m_h_202611;
DROP TABLE IF EXISTS predc_1m_h_202612;

-- tag_se_cd 코멘트 원복 (V8_1 시점 8종)
COMMENT ON COLUMN tag_m.tag_se_cd IS '태그 측정 유형 코드 (DOM_CODE_20 — TagMeasurementType enum 매핑 FRI/PRI/LEI/PWI/RMS/OPS/VOI/FQI 8종, ot-integration.md §3 정합 — 단위 매핑은 enum 의 unit 필드로 흡수)';
```

> 파티션에 이미 데이터가 INSERT 된 경우 DROP 시 해당 데이터가 소실된다. 적용 직후 롤백이 아니라면 데이터 유무를 먼저 확인한다.

---

## 6. 범위 밖 (후속 별도 처리)

| 항목 | 사유 / 후속 |
|------|-----------|
| 고아 테이블 6종 DROP | 사용자 결정=유지. 전부 0행이라 무해. `pump_ctrl_h` 재도입 작업 시 함께 정리 권장 |
| `pump_ctrl_h` 재도입 / `CMD` enum | 코드 미구현(20260604 ANALYZE만 존재). 별도 `/dev` 사이클 — 구현 시 `tag_se_cd` 코멘트 10종 갱신(V8_4) 동반 |
| 파티션 자동 추가 스케줄러 | V5/V7 주석의 "별도 작업"(`PumpPartitionScheduler` 패턴) 미구현. 수동 보충 반복 제거용 |

---

> 본 문서는 dev-db 일회성 정합화 운영 기록이다. 운영본 마이그레이션(`db/migration/`)·도메인 SSOT(`docs/ddl/`)는 본 작업으로 변경되지 않는다.
