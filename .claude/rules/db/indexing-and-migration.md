# DB 인덱싱·무중단 마이그레이션

스마트정수장 PostgreSQL 의 **DDL/스키마 관점** 운영 기준.
인덱스 종류별 사용 기준·복합 인덱스 컬럼 순서, 컬럼 추가·이름 변경·인덱스 추가 시 무중단 마이그레이션 절차를 다룬다.

> 본 문서는 과거 `db-patterns.md` 의 §2 인덱스 원칙 + §3 스키마 무중단 변경 원칙을 분리한 결과다 (2026-04-24). 2026-04-26 디렉토리화로 `db/` 하위로 이동.
> 데이터 수명주기 관점은 [`partitioning-and-retention.md`](partitioning-and-retention.md), 런타임 성능 관점은 [`query-tuning.md`](query-tuning.md) 참조.

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`README.md`](README.md) | 본 문서의 인덱스 페이지 — 진입점 (구 root `db-patterns.md` 자리) |
| [`partitioning-and-retention.md`](partitioning-and-retention.md) | 시계열 파티션 — BRIN 인덱스 적용 전제 |
| [`query-tuning.md`](query-tuning.md) | 트랜잭션 격리·슬로우 쿼리 — 인덱스 효과 검증 도구 |

---

## 1. 인덱스 원칙

### 인덱스 종류별 사용 기준

| 인덱스 | 사용 시점 | 예시 컬럼 |
|--------|----------|----------|
| B-Tree (기본) | 등가·범위 조건, 정렬이 필요한 컬럼 | `pump_id`, `alarm_tp`, `rgstr_dtm` |
| 복합 인덱스 | 두 개 이상 컬럼이 동시에 조건에 사용될 때 | `(pump_id, acq_dtm)` |
| BRIN | 삽입 순서와 물리 순서가 일치하는 대용량 시계열 | `acq_dtm` (시계열 파티션) |
| GIN | JSONB 필드 전체 키 검색 | `tag_meta`, `diag_rslt` |

### 복합 인덱스 컬럼 순서 규칙
1. **등가 조건(=) 컬럼 먼저**, 범위 조건(BETWEEN, >=, <=) 컬럼 뒤에 배치
2. **카디널리티 높은 컬럼 먼저** (pump_id > alarm_tp)
3. 정렬 컬럼은 마지막에 배치

```sql
-- 올바른 예: tag_srl_no(등가) → acq_dtm(범위·정렬) — 마스터도메인설계 ANALYZE1 Round 3 (2026-05-03)
CREATE INDEX idx_rawdata_1m_h_tag_time ON rawdata_1m_h (tag_srl_no, acq_dtm DESC);

-- 잘못된 예: 범위 조건 컬럼이 앞에 오면 나머지 컬럼 인덱스 미사용
CREATE INDEX idx_bad ON rawdata_1m_h (acq_dtm, tag_srl_no);
```

### BRIN 적용 기준
- 1분 이하 간격으로 삽입되는 시계열 파티션에 적용
- B-Tree 대비 크기 1/100, 쓰기 오버헤드 최소화

```sql
CREATE INDEX idx_rawdata_1m_h_brin ON rawdata_1m_h USING BRIN (acq_dtm);
```

### 인덱스 생성 원칙
- 무중단 생성: `CREATE INDEX CONCURRENTLY` 사용
- 주 서비스 시간 외(00:00~06:00) 실행 권장
- 불필요한 인덱스는 즉시 삭제 (`DROP INDEX CONCURRENTLY`)

---

## 2. 스키마 무중단 변경 원칙

### NOT NULL 컬럼 추가 3단계

기존 테이블에 NOT NULL 컬럼을 추가할 때는 **3단계 마이그레이션**을 반드시 따른다.

```sql
-- 1단계: NULL 허용으로 컬럼 추가 (즉시 완료, 락 없음)
ALTER TABLE pump_m ADD COLUMN alarm_lvl SMALLINT;

-- 2단계: 기존 행 백필 (배치 단위로 분할하여 실행)
UPDATE pump_m SET alarm_lvl = 0 WHERE alarm_lvl IS NULL AND id BETWEEN 1 AND 10000;
-- ... 반복

-- 3단계: NOT NULL 제약 추가 (PostgreSQL 12+: CHECK 제약으로 검증 후 NOT NULL 전환)
ALTER TABLE pump_m ALTER COLUMN alarm_lvl SET NOT NULL;
```

> **⚠️ 절대 금지**: `ALTER TABLE ... ADD COLUMN col NOT NULL DEFAULT val` — 대용량 테이블에서 전체 락 발생.

### 컬럼 이름 변경
```sql
-- 무중단: 새 이름으로 컬럼 추가 → 양방향 동기 → 구 컬럼 제거
ALTER TABLE pump_m ADD COLUMN new_col_nm VARCHAR(100);
-- 애플리케이션 배포 후 구 컬럼 제거
ALTER TABLE pump_m DROP COLUMN old_col_nm;
```

### 인덱스 추가·변경
```sql
-- 항상 CONCURRENTLY 사용 (락 없이 생성)
CREATE INDEX CONCURRENTLY idx_pump_m_grp ON pump_m (pump_grp_id);
```

---

## 3. `DOM_YN` DDL 정책

`DOM_YN` 표준 데이터 도메인(`VARCHAR(1)` + `YnType` enum) 적용 컬럼(`*_yn`) 의 DDL 운영 정책. 1차 정의는 `swtp/.claude/rules/dict/standard-data-domains.md` 의 `DOM_YN` 행이며, 본 절은 위임받은 DDL 운영 정책의 SSOT 다.

### 3.1 SQL 타입

- 반드시 `VARCHAR(1)` 사용. **`CHAR(1)` 금지** — PostgreSQL trailing space 패딩 처리 차이로 쿼리 이식성 혼란.
- 기존 `CHAR(1)` 컬럼은 운영 도입 전 `ALTER COLUMN TYPE VARCHAR(1) USING <컬럼>::VARCHAR` 단일 문장으로 정렬 (`ACCESS EXCLUSIVE` 락이지만 운영 데이터 소량 시 무시 가능). `ALTER COLUMN TYPE` 은 `CONCURRENTLY` 불가 — 대용량 시 섀도우 컬럼 방식(신컬럼 추가 → 백필 → 구컬럼 제거) 적용.

### 3.2 CHECK 제약

- 신규 테이블에는 **추가하지 않는다** — Java `@Enumerated(EnumType.STRING)` + `YnType` enum 이 'Y'/'N' 외 값을 1차 차단하므로 단일 방어선으로 수렴.
- 기존 `user_m.ck_user_m_use_yn` 은 admin 초기 INSERT 안전망으로 작동 중 — **현상 유지**, 제거하지 않음.

### 3.3 DEFAULT 값

- DDL `DEFAULT` **미설정**.
- Java 정적 팩토리에서 `YnType.Y` 명시 할당이 의무 — 두 곳 분산은 DRY 위반, Java 패턴이 진실 소스(SSOT).
- 기존 `user_m.use_yn DEFAULT 'Y'` 는 admin INSERT 가 이미 `use_yn = 'Y'` 명시 INSERT 라 실사용 경로 없음 — 정렬 시 제거.

### 3.4 인덱스

- **단독 인덱스 미적용** — 카디널리티 2(Y/N) 단독 B-Tree 는 옵티마이저가 Seq Scan 선호.
- 복합 인덱스 구성 시 §1 카디널리티 원칙에 따라 **후위 컬럼**으로 배치.
- 기존 단독 인덱스(예: `idx_user_m_use_yn`) 는 `DROP INDEX CONCURRENTLY` 로 제거 권고.

> 본 절 도입 이력: use_yn_consistency ANALYZE1 (2026-04-25)

---

## 4. 컬럼 COMMENT 의무화 정책

DDL 마이그레이션 SQL 의 모든 `CREATE TABLE` 컬럼은 동일 파일 내에 `COMMENT ON COLUMN` 이 작성되어야 한다. DBA·운영자가 `psql \d+ {table}` 로 스키마를 조사할 때 모든 컬럼의 의미가 노출되도록 하기 위함이며, BaseEntity 자동 주입 4컬럼도 예외가 아니다.

### 4.1 적용 대상

- `common/src/main/resources/db/migration/*.sql`

해당 디렉토리 외부의 SQL (테스트 픽스처 등) 은 본 정책 적용 외다.

> 2026-05-20 sql_관리포인트_통합 — `db/init/` 디렉토리 폐지에 따른 적용 대상 정리. `db/migration/` 단일 디렉토리 일원화 (구 `db/init/` 항목 제거).

### 4.2 누락 시 자동 차단

`.claude/hooks/check-ddl-column-comment.sh` 가 `PostToolUse` / `Write`·`Edit` 시점에 동작하며, 누락 1건이라도 발견되면 `exit 2` 로 저장을 차단한다. 동작·매칭 글롭·우회 환경변수 상세는 [`../process/hooks-guide.md` §6](../process/hooks-guide.md) 참조.

우회: `DDL_SKIP_COMMENT_CHECK=1` (Bash 환경변수). 사용자 명시 요청 시에만 사용한다.

### 4.3 BaseEntity 공통 메타 4컬럼 표준 라벨

BaseEntity 를 상속한 모든 테이블에 동일한 라벨을 사용한다. 라벨 변경 시 BaseEntity Javadoc 도 함께 갱신한다.

```sql
COMMENT ON COLUMN {table}.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN {table}.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN {table}.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN {table}.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
```

#### 이력 immutable 테이블 예외

`_h` suffix 시계열 이력 테이블 중 BaseEntity 미상속 + INSERT-only 인 경우 (백지화 이전의 `ai_drvn_mod_h` 등) 는 `rgstr_dtm`·`rgstr_id` 만 보유하며 `updt_*` 컬럼 자체를 정의하지 않는다. 이 경우 다음 라벨을 사용한다.

```sql
COMMENT ON COLUMN {table}.rgstr_dtm IS '등록 일시 (DOM_DTM, ... — immutable 이력이므로 BaseEntity 미상속, 명시 INSERT)';
COMMENT ON COLUMN {table}.rgstr_id  IS '등록자 ID (immutable 이력 INSERT-only, AuditingEntityListener 미적용 — 명시 할당, DOM_ID_50)';
```

#### 변경 추적 컬럼 존재 시 BaseEntity 4 상속 허용

`_h` suffix 이력 테이블이라도 **업무 요건상 일부 컬럼이 갱신 필요** 한 경우 BaseEntity 4 상속 허용. `_h` 의미를 "INSERT-only 기본, 업무 요건 갱신 컬럼 존재 시 BaseEntity 4 상속 허용" 으로 재해석한다.

선례:
- **`rawdata_1m_h.corr_val`** — Hold Last Value 적용 결과 또는 운영자 보정으로 갱신 필요 (마스터도메인설계 ANALYZE1 Round 3, 2026-05-03)
- **`ai_drvn_mod_h.end_dtm`** — 모드 변경 시점에 직전 행의 종료시간을 갱신 필요 (송수펌프제어분석-2번섹션 ANALYZE1 안건 3, 2026-05-20)

본 패턴 적용 시 컬럼 라벨은 §4.3 BaseEntity 4 표준 라벨을 그대로 사용한다 (`updt_dtm`·`updt_id` 자동 주입). INSERT-only 컬럼 (예: `start_dtm`·`raw_val`·`acq_dtm`) 의 immutable 보장은 **애플리케이션 레벨 검증** 으로 처리 (PLAN 단계 명시 의무).

### 4.4 도메인 컬럼 라벨 패턴

```
'{한글 논리명} ({단위 / DOM_* 코드 / 부연 — 선택})'
```

- 한글 논리명: [`../dict/standard-terms.md`](../dict/standard-terms.md) 의 한글 논리명 1차 사용. 미등재 시 [`swtp/.claude/rules/dict/standard-words.md`](../../../../.claude/rules/dict/standard-words.md) 단어 조합으로 자연어 작성
- 단위: `m³/h`, `kgf/cm²`, `kW`, `kWh`, `m` 등
- DOM_* 코드: [`swtp/.claude/rules/dict/standard-data-domains.md`](../../../../.claude/rules/dict/standard-data-domains.md) 등재 시 명시
- 부연: "외부 할당 PK", "마스터 참조 FK", "마스터 참조이지만 FK 금지", "Y·N enum 매핑" 등 제약·의도 정보

### 4.5 본 정책의 한계

- 자동 차단은 Claude Code 세션 내 `Write`·`Edit` 도구 사용 시에만 적용된다 — 터미널·IDE 직접 편집은 적용 외 (기존 훅과 동일 한계)
- 훅의 정규식은 한 줄 인라인 형식 `CREATE TABLE name (` 만 인식 — 멀티라인 형식 등장 시 별도 ANALYZE 로 보강 검토
- 컬럼 라벨 한국어 문구의 자연어 품질은 자동 검증 대상 외 — `wtp-glossary-manager` 가 PR 리뷰에서 보강

> 본 절 도입 이력: ddl_comment_harness (2026-04-29)

---

## 5. SQL 관리 — 도메인별 단일 파일 + 이중 정책

backend 의 모든 SQL 자산은 다음 2개 위치에서 양쪽 유지된다 (2026-05-20 sql_관리포인트_통합 ANALYZE1 결정).

### 5.1 SSOT 분리 — 운영본 vs 도메인 SSOT 사본

| 위치 | 역할 | 변경 정책 |
|------|------|---------|
| `common/src/main/resources/db/migration/V{N}__{도메인}.sql` | 운영본 (운영자가 `psql -f` 로 신환경 부트스트랩 시 적용) | **V{N} 동결** — 초기 CREATE 전용. 향후 도메인 변경은 `V{N}_{연번}__patch.sql` 별도 분리 |
| `backend/docs/ddl/{도메인}.sql` | 도메인 SSOT 사본 (사람이 읽는 도메인 단위 통합본) | **ALTER 누적** — 도메인 변경 시 같은 파일 하단에 ALTER 문 추가. CREATE + ALTER 통합 형태로 사람이 읽는 가독성 우선 |

> **이중 정책 결정 배경**: DBA 권고 (운영 적용 안전성·추적성) + 사용자 의도 (도메인별 변경점 누적 가독성) 양립. 운영자 적용 절차는 patch 파일 분리로 안전, 사람이 도메인 단위 스키마를 통합 조회하는 용도는 docs/ddl 사본으로 충족.

### 5.2 도메인-V번호 매핑

backend 도메인 패키지 (`com.mo.swtp.{도메인}`) 9개와 1:1 일치 (사전순 V 번호 부여).

| V번호 | 도메인 | 파일명 |
|------|-------|--------|
| V1 | auth | `V1__auth.sql` · `docs/ddl/auth.sql` |
| V2 | facility | `V2__facility.sql` · `docs/ddl/facility.sql` |
| V3 | instrument | `V3__instrument.sql` · `docs/ddl/instrument.sql` |
| V4 | menu | `V4__menu.sql` · `docs/ddl/menu.sql` |
| V5 | opt | `V5__opt.sql` · `docs/ddl/opt.sql` |
| V6 | proc | `V6__proc.sql` · `docs/ddl/proc.sql` |
| V7 | raw | `V7__raw.sql` · `docs/ddl/raw.sql` |
| V8 | tag | `V8__tag.sql` · `docs/ddl/tag.sql` |
| V9 | user | `V9__user.sql` · `docs/ddl/user.sql` |

신규 도메인 도입 시 V10~ 부여. 도메인-V번호 매핑은 `common/src/main/resources/db/migration/README.md` 가 SSOT — 본 표는 사본 (마이그레이션 README 갱신 시 본 §5.2 도 동시 갱신).

### 5.3 양쪽 동시 갱신 의무

`common/src/main/resources/db/migration/V{N}__{도메인}.sql` 또는 `V{N}_{연번}__patch.sql` 가 변경된 커밋은 반드시 `backend/docs/ddl/{도메인}.sql` 도 동일 커밋에 포함되어야 한다.

- 한쪽만 변경하는 커밋은 **REVIEW 단계 권고(중간) 등급** — `wtp-backend-engineer` 가 `/dev:review` 단계에서 `git diff --name-only` 결과를 교차 비교하여 미동기화 발견 시 권고 지적
- 블로커(높음) 격상 미적용 — 본 자산은 도메인 안전·보안 직결 아님 ([`coding-discipline.md §2.1`](../../../../.claude/rules/coding-discipline.md) 블로커 격상 조건 미해당)
- 도메인별 예외 없음 — 9개 도메인 모두 동일 정책 적용 (proc/ai 단방향 정책 미채택, 2026-05-20 사용자 결정)
- TASK 체크박스 작성 시 두 파일 경로를 쌍으로 명시 의무 ([`process/doc-harness/checkbox-rules.md`](../process/doc-harness/checkbox-rules.md) 호환)

### 5.4 V{N} 파일에 ALTER 누적 금지

`common/src/main/resources/db/migration/V{N}__{도메인}.sql` 파일에 도메인 신설 이후 ALTER 문 직접 추가 금지. 새 변경은 반드시 `V{N}_{연번}__patch.sql` 별도 파일로 분리한다.

```
common/src/main/resources/db/migration/
├── V3__instrument.sql          ← 초기 CREATE (동결, 수정 금지)
├── V3_1__instrument_patch.sql  ← 첫 번째 patch (예: pump_m.new_col 추가)
├── V3_2__instrument_patch.sql  ← 두 번째 patch
```

운영자 신환경 부트스트랩 시 `V{N}__{도메인}.sql` + 모든 `V{N}_*__patch.sql` 순서대로 적용.

> 본 절 도입 이력: sql_관리포인트_통합 ANALYZE1 (2026-05-20). 자세한 배경: `docs/analyze/20260520/sql_관리포인트_통합/ANALYZE1.md`
