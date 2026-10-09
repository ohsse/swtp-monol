# DB 마이그레이션 — 도메인별 V1~V9 운영본

스마트정수장 backend 의 PostgreSQL 스키마 부트스트랩 SQL 자산이다.
9개 도메인 패키지 (`com.mo.swtp.{도메인}`) 와 1:1 매핑되는 V1~V9 합본으로 구성된다.

---

## 1. 목적

신규 환경 (개발·스테이징·운영) 초기 구축 시 운영자가 본 디렉토리의 SQL 을 V1 → V9 순서로 `psql -f` 적용하여 스키마를 부트스트랩한다.

본 디렉토리는 backend 의 **유일한 SQL 운영본** 이다 (`db/init/` 디렉토리는 폐지됨, `api/src/main/resources/db/migration/` 도 폐지됨).

도메인 SSOT 사본은 `backend/docs/ddl/{도메인}.sql` 에 별도 관리되며, 본 디렉토리의 V{N}__{도메인}.sql 과 **완전 동일 내용** 을 양쪽 동시 유지한다 (`.claude/rules/db/indexing-and-migration.md §5.3`).

---

## 2. ⚠️ 기존 환경 적용 금지 정책

**기존 V6_1~V9_4 가 이미 적용된 운영 DB 에 본 V1~V9 를 재적용하지 말 것.**

본 V1~V9 는 신규 환경 부트스트랩 전용이며, 기존 환경에는 다음 차이만 존재한다:
- 파일 V 번호 재정렬 (V6_1·V6_2·...·V9_4 → V1·V2·...·V9 통합)
- 도메인별 ALTER 이력 흡수 정렬 (예: V8_2 추가 + V8_6 제거 → 최종 결과만 인라인)

운영 DB 스키마 자체는 **불변** 이다. 기존 환경에 V1~V9 를 재적용 시 다음 위험이 발생한다:
- `CREATE TABLE` 충돌 (기존 테이블 존재) → 일부 도메인은 `IF NOT EXISTS` 보호되지만 V2/V3 등은 미보호
- 시드 INSERT 중복 (`ON CONFLICT` 미적용 경로) → admin 시드는 안전하나 PUMP_CONTROL 시드 UNIQUE 충돌
- 인덱스 중복 생성 시도 → `CREATE INDEX CONCURRENTLY IF NOT EXISTS` 미적용 인덱스 충돌

**기존 환경에서 V1~V9 비교 검증이 필요하면 본 README §5 동등성 검증 절차를 사용하라 — DROP 후 V1~ 재적용은 절대 금지.**

---

## 3. 도메인-V번호 매핑 표

| V번호 | 도메인 패키지 | 운영본 파일 | 도메인 SSOT 사본 |
|------|------------|-----------|----------------|
| V1 | `com.mo.swtp.auth` | `V1__auth.sql` | `backend/docs/ddl/auth.sql` |
| V2 | `com.mo.swtp.facility` | `V2__facility.sql` | `backend/docs/ddl/facility.sql` |
| V3 | `com.mo.swtp.instrument` | `V3__instrument.sql` | `backend/docs/ddl/instrument.sql` |
| V4 | `com.mo.swtp.menu` | `V4__menu.sql` | `backend/docs/ddl/menu.sql` |
| V5 | `com.mo.swtp.opt` | `V5__opt.sql` | `backend/docs/ddl/opt.sql` |
| V6 | `com.mo.swtp.proc` | `V6__proc.sql` | `backend/docs/ddl/proc.sql` |
| V7 | `com.mo.swtp.raw` | `V7__raw.sql` | `backend/docs/ddl/raw.sql` |
| V8 | `com.mo.swtp.tag` | `V8__tag.sql` | `backend/docs/ddl/tag.sql` |
| V9 | `com.mo.swtp.user` | `V9__user.sql` | `backend/docs/ddl/user.sql` |

본 표는 `.claude/rules/db/indexing-and-migration.md §5.2` 의 SSOT 사본이다. 도메인 신설·폐기 시 양쪽 동시 갱신.

### 적용 순서 의존성

| V 순서 | 의존 V | 사유 |
|-------|-------|------|
| V2 (facility) | — | 최상위 마스터 (`facility_m` self-FK) |
| V3 (instrument) | V2 | `instrument_m.facility_id → facility_m` FK |
| V8 (tag) | V3 | `tag_m.instrument_id → instrument_m` FK |
| V9 (user) | — | 독립 (user_m) |
| V1 (auth) | V9 | `refresh_token_p.user_id → user_m` FK |
| V4 (menu) | — | 독립 (menu_m self-FK) |
| V6 (proc) | — | 독립 (proc_m 외부 할당 PK) |
| V5 (opt) | V8 | `predc_1m_h.tag_srl_no` 논리 참조 (시계열 → 마스터 FK 금지지만 의미 정합) |
| V7 (raw) | V8 | `rawdata_1m_h.tag_srl_no` 논리 참조 |

> V 번호 자체는 사전순 (auth → facility → instrument → menu → opt → proc → raw → tag → user) 이며, 적용 순서는 위 의존성 표에 따른다. V 번호 순서대로 적용해도 V9 → V1 → V4 → V6 → V2 → V3 → V8 → V5 → V7 모든 경로에서 FK 의존성이 충족된다 (V2/V3/V8 의 facility → instrument → tag 체인은 V2 → V3 → V8 순서로 진행됨).

---

## 4. 신규 환경 적용 명령

```bash
# DB 연결 환경 변수 (예시 — 실제 환경에 맞게 조정)
export PGHOST=localhost
export PGPORT=5432
export PGUSER=swtp
export PGDATABASE=smartwtp
# 비밀번호는 ~/.pgpass 또는 PGPASSWORD 환경변수로 주입

# V1 ~ V9 순차 적용 (사전순)
psql -f V1__auth.sql
psql -f V2__facility.sql
psql -f V3__instrument.sql
psql -f V4__menu.sql
psql -f V5__opt.sql
psql -f V6__proc.sql
psql -f V7__raw.sql
psql -f V8__tag.sql
psql -f V9__user.sql
```

> 또는 한 줄 일괄:
> ```bash
> for f in V*.sql; do psql -f "$f" || break; done
> ```

각 `psql -f` 호출은 단일 SQL 파일 트랜잭션 (PostgreSQL 의 기본 implicit BEGIN/COMMIT). 한 V 파일 실패 시 그 파일 내 모든 변경은 ROLLBACK 되며, 후속 V 적용 전 운영자가 원인 분석 후 재시작한다.

---

## 5. 동등성 검증 절차

기존 운영 DB 스키마와 V1~V9 적용 신환경 스키마의 동등성 검증.

### 5.1 스키마 덤프 비교 (권장)

```bash
# 기존 운영 DB (V6_1~V9_4 적용 상태) 스키마만 덤프
pg_dump --schema-only --no-owner --no-acl \
    -h prod-host -U swtp -d smartwtp \
    > /tmp/schema_existing.sql

# 신환경 (V1~V9 적용 상태) 스키마만 덤프
pg_dump --schema-only --no-owner --no-acl \
    -h new-host -U swtp -d smartwtp \
    > /tmp/schema_new.sql

# 비교 — 0 라인 차이면 동등 (CHECK 제약·인덱스·시퀀스·COMMENT 모두 포함)
diff /tmp/schema_existing.sql /tmp/schema_new.sql
```

> COMMENT ON COLUMN 차이는 ALTER 이력 흔적이 인라인 정렬되어 미세 어순 차이가 있을 수 있다. 의미 동등성은 본 README §5.2 의 `\d+` 비교로 보강.

### 5.2 테이블 단위 `\d+` 대조

```bash
# 9개 도메인의 주요 테이블에 대해 양쪽 환경에서 \d+ 출력 비교
for tbl in user_m refresh_token_p facility_m instrument_m pump_m dwt_m tag_m rawdata_1m_h predc_1m_h proc_m ai_drvn_mod_p ai_drvn_mod_h menu_m menu_role_r; do
    psql -h prod-host -d smartwtp -c "\d+ ${tbl}" > /tmp/existing_${tbl}.txt
    psql -h new-host  -d smartwtp -c "\d+ ${tbl}" > /tmp/new_${tbl}.txt
    diff /tmp/existing_${tbl}.txt /tmp/new_${tbl}.txt && echo "${tbl}: OK" || echo "${tbl}: DIFF"
done
```

---

## 6. 향후 변경 정책 — V{N} 동결 + patch 분리

**V{N}__{도메인}.sql 파일은 초기 CREATE 후 동결된다.** 도메인 신규 변경은 별도 `V{N}_{연번}__patch.sql` 파일로 분리 작성한다.

### 6.1 patch 파일 네이밍

```
V{N}__{도메인}.sql          ← 초기 CREATE (동결, 수정 금지)
V{N}_1__{도메인}_patch.sql  ← 첫 번째 patch (예: V3_1__instrument_patch.sql)
V{N}_2__{도메인}_patch.sql  ← 두 번째 patch
V{N}_3__{도메인}_patch.sql  ← ...
```

### 6.2 patch 적용 순서

신환경 부트스트랩 시 `V{N}__{도메인}.sql` + 그 도메인의 모든 `V{N}_*__patch.sql` 을 V 번호 + 연번 오름차순으로 적용한다.

```bash
# 예: instrument 도메인 신환경 부트스트랩
psql -f V3__instrument.sql
psql -f V3_1__instrument_patch.sql
psql -f V3_2__instrument_patch.sql
# ...
```

### 6.3 docs/ddl 사본과의 관계

V{N} 동결 후 patch 가 추가되면 `backend/docs/ddl/{도메인}.sql` 은 patch 내용을 **ALTER 누적** 형태로 추가한다 (사람이 읽는 도메인 통합본 가독성 우선).

상세는 본 README §7 참조.

상세 정책: `.claude/rules/db/indexing-and-migration.md §5.4 V{N} 파일에 ALTER 누적 금지`.

---

## 7. docs/ddl 사본 관계

backend SQL 자산은 **이중 정책** 으로 관리된다:

| 위치 | 역할 | 변경 방식 |
|------|------|---------|
| `common/src/main/resources/db/migration/V{N}__{도메인}.sql` (본 디렉토리) | **운영본** — `psql -f` 적용 대상 | V{N} 동결 + `V{N}_{연번}__patch.sql` 분리 |
| `backend/docs/ddl/{도메인}.sql` | **도메인 SSOT 사본** — 사람이 읽는 도메인 통합본 | 단일 파일에 ALTER 결과 누적 (가독성 우선) |

### 7.1 양쪽 동시 갱신 의무

본 디렉토리의 V{N}__{도메인}.sql 또는 V{N}_{연번}__patch.sql 가 변경된 커밋은 반드시 `backend/docs/ddl/{도메인}.sql` 도 동일 커밋에 포함되어야 한다.

REVIEW 단계 `wtp-backend-engineer` 가 `git diff --name-only` 결과를 교차 비교하여 미동기화 발견 시 권고(중간) 등급으로 지적한다 (블로커 격상 미적용 — 도메인 안전·보안 직결 아님).

### 7.2 SSOT 관계 요약

- 운영본 = 신환경 부트스트랩 시 적용되는 단일 파일 집합 (V{N} 동결 + patch 누적)
- 도메인 SSOT 사본 = 도메인 단위 스키마를 사람이 한 번에 통합 조회할 때 사용 (CREATE + ALTER 누적 형태)
- 두 위치의 내용은 의미적으로 동등하나 표현 방식이 다름 (V{N} + patch 합집합 vs 단일 파일 ALTER 누적)

본 정책의 상세 SSOT: `.claude/rules/db/indexing-and-migration.md §5 SQL 관리 — 도메인별 단일 파일 + 이중 정책`.

---

## 폐기·갱신 이력

| 일자 | 변경 | 배경 |
|------|------|------|
| 2026-05-20 | 신규 작성 — sql_관리포인트_통합 ANALYZE1 + PLAN1 결정 | `db/init/` 폐지 + `api/db/migration/` 폐지 + V6_1~V9_4 통합 → V1~V9 사전순 도메인 합본 + docs/ddl 신규 카테고리 도입 |
