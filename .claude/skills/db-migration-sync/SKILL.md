---
name: db-migration-sync
description: "운영본 DB 마이그레이션(common/src/main/resources/db/migration/*.sql)과 타겟 DB(dev/local) 실제 스키마를 대조해 ① 미적용 patch ② 잔존 고아 테이블 ③ 구조·COMMENT 차이를 정리하고, 사용자 승인 후 누락 patch·시드를 타겟 DB에 트랜잭션으로 적용한다. '마이그레이션 누락 확인', 'dev DB에 patch 적용', 'DB 스키마 동기화/드리프트 점검', '운영본과 타겟 DB 비교', '시드 데이터 등록 전 스키마 확인' 같은 요청에 사용한다. 스키마 변경(DDL) 자체를 새로 **설계**할 때는 이 스킬이 아니라 `/dev:analyze` 워크플로우를 사용한다."
---

# db-migration-sync

스마트정수장 backend 의 **운영본 DB 마이그레이션 ↔ 타겟 DB(dev/local) 드리프트 점검 & 적용** 전용 스킬.

운영본(`common/src/main/resources/db/migration/*.sql`)과 실제 DB 스키마를 대조하여 미적용 patch·잔존 고아 테이블·구조 차이를 3분류로 정리하고, **사용자 승인 후에만** 누락 patch 를 트랜잭션으로 적용한다. dev DB 는 Flyway 이력 테이블이 없어 버전 메타가 아니라 **실제 스키마 구조 기반** 으로 비교한다.

---

## 1. 사용 시점

- "dev DB 와 운영본 마이그레이션 누락 목록 뽑아줘"
- "이 patch 가 dev 에 적용됐는지 확인해줘" · "dev DB 에 V{N}_{n} patch 적용해줘"
- 시드 데이터 등록 전 "대상 테이블이 타겟 DB 에 있는지 / 스키마가 운영본과 일치하는지" 확인이 필요할 때
- 신규 환경 부트스트랩 후 운영본과 동등성 검증이 필요할 때

**사용하지 않아야 하는 경우**:

- 스키마 변경(DDL)을 **새로 설계**할 때 (신규 테이블·컬럼·인덱스 결정) → `/dev:analyze` 5인 회의 워크플로우
- 운영본 마이그레이션 파일 자체를 작성·수정할 때 → `/dev` 워크플로우 (V{N} 동결 + patch 분리 정책, `db/indexing-and-migration.md §5`)
- 애플리케이션 데이터(시설·태그 등) 등록만 하고 스키마 비교가 불필요할 때 → 직접 `pg_execute_sql`

---

## 2. 전제 — MCP 도구 & 접속

| 용도 | 도구 | 비고 |
|------|------|------|
| 타겟 DB 읽기 (dev) | `mcp__swtp-postgres-dev__query` | read-only. dev 전용 |
| 타겟 DB 읽기 (local) | `mcp__swtp-postgres-local__pg_execute_sql` (expectRows=true) | 또는 위 dev 도구로 통일 불가 시 |
| 타겟 DB 쓰기 (모든 환경) | `mcp__swtp-postgres-local__pg_execute_sql` + `connectionString` 오버라이드 + `transactional=true` | dev 쓰기는 반드시 connectionString 오버라이드로 수행 |

- **접속 문자열은 `backend/.mcp.json` 에서 읽는다** (스킬 문서·산출물에 비밀번호를 하드코딩하지 않는다). dev = `swtp-postgres-dev` 서버의 connection string, local = `swtp-postgres-local`.
- 쓰기는 `swtp-postgres-dev` 도구가 read-only 이므로, **local 도구의 `connectionString` 파라미터에 dev 접속 문자열을 넘겨** 실행한다 (메모리 `reference_dev_db_write_path` 정합).

---

## 3. 절차

### 3.1 운영본 마이그레이션 스캔

1. `common/src/main/resources/db/migration/*.sql` 전체 목록 확보 (Glob).
2. `common/src/main/resources/db/migration/README.md` 의 §3 도메인-V번호 매핑 + §6 V{N} 동결 정책 확인.
3. 각 파일을 **메인 V**(V1~V9, 초기 CREATE) 와 **patch**(`V{N}_{연번}__{도메인}_patch.sql`) 로 분류.
4. 각 patch 가 만드는/바꾸는 객체를 추출 — 테이블·컬럼·인덱스·제약·`COMMENT ON`·시드 `INSERT`. (patch SQL 을 Read 하여 직접 파싱. SQL 구조가 다양하므로 스크립트보다 LLM 직접 판독이 정확)

### 3.2 타겟 DB 실제 상태 조회

read-only 도구로 다음을 조회한다:

- 전체 테이블 목록 — `information_schema.tables WHERE table_schema='public'`
- Flyway 이력 유무 — `flyway_schema_history` 존재 여부 (보통 **없음** → 구조 기반 비교 확정)
- patch 대상별 정밀 조회: 컬럼(`information_schema.columns`), 인덱스(`pg_indexes`), 제약(`pg_constraint`), 시퀀스(`information_schema.sequences`), 컬럼 COMMENT(`col_description(c.oid, a.attnum)`), 시드/잔존 행 수(`count(*)`).

### 3.3 대조 — 3분류

3.1 의 운영본 객체와 3.2 의 실제 객체를 대조하여 분류한다 (§4 기준 표).

| 분류 | 정의 | 조치 |
|------|------|------|
| **누락** | 운영본 O · DB X (또는 DB 에 구버전만 존재) | 적용 후보 |
| **잔존** | 운영본 X · DB O | 정보성 보고 (백지화 미반영 고아 등). 삭제는 **별도 승인** |
| **차이** | 양쪽 O 이나 컬럼·코드값·COMMENT 불일치 | 갱신 후보 (예: COMMENT N종→M종) |

### 3.4 리포트 + 적용 SQL 미리보기

사용자에게 다음을 제시한다:

- 3분류 표 (누락/잔존/차이) + 각 항목의 근거(행 수·컬럼·COMMENT 실측값)
- **적용 SQL 미리보기** — 누락/차이 항목별 실제 실행할 SQL. patch 파일을 그대로 쓸지, 멱등 보강(`IF NOT EXISTS`)이 필요한지 명시
- 잔존 항목은 데이터 유무(행 수)와 함께 "삭제 시 영향" 정보 제공

### 3.5 사용자 승인

`AskUserQuestion` 으로 항목별 적용 여부를 확정한다. 최소 분리 질문:

- 누락/차이 patch 적용 여부 (개별 또는 묶음)
- 잔존 테이블 처리 (보존 / DROP) — **기본은 보존**, DROP 은 명시적 선택 시에만

### 3.6 적용 (트랜잭션)

- `mcp__swtp-postgres-local__pg_execute_sql` + `connectionString`(타겟) + `transactional=true` 로 실행.
- DDL/COMMENT 만이면 `expectRows=false`.
- 한 patch = 한 트랜잭션 단위 권장 (부분 실패 시 롤백 추적 용이).
- patch 가 부모-자식(JOINED 상속) INSERT 면 동일 `facility_id` 보장을 위해 `WITH ins AS (INSERT ... RETURNING ...) INSERT INTO child SELECT ... FROM ins` CTE 패턴 사용.

### 3.7 재검증

적용 직후 read-only 도구로 다시 조회하여 의도한 객체·행이 생성/갱신됐는지 확인하고, 결과 표를 사용자에게 보고한다.

---

## 4. 분류 기준 상세

### 4.1 메인 V1~V9 (초기 CREATE)

- 기존 DB 는 이미 부트스트랩 완료 상태이므로 메인 V 는 보통 전부 반영됨.
- **⚠️ 기존 환경에 메인 V1~V9 재적용 금지** (README §2). 테이블·시드·인덱스 충돌 위험. 메인 V 는 "테이블 존재 여부"로만 빠르게 확인하고, 차이 발견 시 사용자에게 보고만 한다 (자동 재적용 금지).

### 4.2 patch (V{N}_{연번})

- patch 가 적용 대상. 대부분 `IF NOT EXISTS` / COMMENT 덮어쓰기 / 멱등 UPDATE 라 재실행 안전.
- 멱등 보강이 없는 patch(예: `CREATE TABLE` without `IF NOT EXISTS`, `INSERT` without `ON CONFLICT`)는 적용 전 대상 객체 존재 여부를 반드시 확인 후 실행.

### 4.3 구버전 잔존 판정

- 같은 이름의 테이블이 있어도 컬럼·코드값이 구버전일 수 있다 (예: 백지화 전 `pump_ctrl_h.pump_id` vs 재도입 `instrument_id`). 컬럼명·COMMENT·코드값까지 대조해야 "차이" 를 놓치지 않는다.

---

## 5. 안전 정책

- **적용은 항상 사용자 승인 후** (`AskUserQuestion`). 무승인 자동 적용 금지.
- **잔존 테이블 DROP 은 기본 보존**, 명시적 선택 시에만 실행. 0행이어도 되돌릴 수 없음을 고지.
- **메인 V1~V9 기존 환경 재적용 금지** (README §2).
- **비밀번호 하드코딩 금지** — connection string 은 `backend/.mcp.json` 참조.
- dev 쓰기는 local 도구 + dev `connectionString` 오버라이드 경로만 사용 (dev 도구는 read-only).
- 운영본 마이그레이션 **파일 자체를 수정**해야 하는 경우(스키마 설계 변경)는 본 스킬 범위 밖 → `/dev` 워크플로우로 분리.

---

## 6. 참조

- `common/src/main/resources/db/migration/README.md` — 도메인-V번호 매핑 SSOT, §2 기존 환경 적용 금지, §5 동등성 검증, §6 V{N} 동결 정책
- `.claude/rules/db/indexing-and-migration.md §5` — SQL 관리 이중 정책(운영본 + docs/ddl 사본), §5.4 V{N} 동결
- `backend/docs/ddl/{도메인}.sql` — 사람이 읽는 도메인 SSOT 사본 (운영본과 동시 갱신 의무)
- 메모리 `reference_dev_db_write_path` — dev DB 쓰기 경로(local MCP + dev connectionString 오버라이드)

---

## 7. 적용 사례 (2026-06-10 시드데이터등록 세션)

최초 도출 작업. 시설 운영시설 6종 시드 등록 중 자식 테이블 부재를 발견 → 본 절차로 드리프트 점검:

- **누락**: V2_1(facility 운영시설 7종 테이블) → 적용 / V8_4(tag_se_cd COMMENT 10종 CMD) → 적용
- **잔존(0행, 보존)**: pump_predc_h·pump_cmbn_m·pump_cmbn_d·pump_interlock_p·drvn_anls_dwld_h (2026-05-12 백지화 고아)
- **반영 확인(no-op)**: V3_1(pump_ctrl_h 신 스키마)·V5_1(opt_peak_target_p)·V8_1~V8_3
