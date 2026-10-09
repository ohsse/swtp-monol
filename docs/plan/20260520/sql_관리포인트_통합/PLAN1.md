---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# SQL 관리포인트 통합 — 계획

## 목적

backend 모듈의 SQL 자산이 3개 디렉토리 (`common/db/init/`·`common/db/migration/`·`api/db/migration/`) 에 분산되어 관리 포인트가 혼재. 본 사이클은 모든 SQL 을 `common/src/main/resources/db/migration/` 단일 디렉토리로 통합하고 9개 도메인별 V1~V9 단일 합본으로 재번호하며, 사람이 읽는 도메인 SSOT 사본을 `backend/docs/ddl/` 에 신설한다.

## 배경

- ANALYZE1 (2026-05-20, approved) 의 결정 사항 6건 + 사용자 결정 2건 반영
- 도메인 4영역 (알람·인터록·운전 모드·이력 기록) **모두 비해당** — 운영 DB 스키마 무변경, 자산 재배치 작업
- ROOT 어휘 사전 영향 0건 (Glossary 매니저 검토 완료)
- 룰 갱신 6건 이미 ANALYZE 단계에서 실행 완료 (status: approved 의 전제조건 충족)
- 관련 문서: [ANALYZE1](../../../analyze/20260520/sql_관리포인트_통합/ANALYZE1.md)

## 범위

### 포함

1. **resources/db/migration 단일화** — `common/src/main/resources/db/migration/` 에 V1~V9 도메인별 합본 9개 + README.md 1개 작성
2. **docs/ddl 신규 카테고리** — `backend/docs/ddl/` 디렉토리 신설 + 9개 도메인 SSOT 사본 작성 (`auth.sql`·`facility.sql`·`instrument.sql`·`menu.sql`·`opt.sql`·`proc.sql`·`raw.sql`·`tag.sql`·`user.sql`)
3. **구 디렉토리 삭제**:
   - `common/src/main/resources/db/init/` 전체 (15개 V*.sql + README.md)
   - `common/src/main/resources/db/migration/` 의 구 V9_1·V9_2·V9_3 (3개)
   - `api/src/main/resources/db/migration/` 전체 (3개 SQL)
4. **빌드 검증** — `./gradlew.bat clean build` 통과 확인

### 제외

- **운영 DB 스키마 변경** — DROP·CREATE·ALTER 모두 없음 (사용자 결정 4번 — 스키마는 그대로, 파일만 V1 재번호)
- **자동 동기화 스크립트 도입** — REVIEW 권고(중간) 자동 점검만으로 충분 (단순성 우선)
- **Flyway/Liquibase 도입** — 별도 ANALYZE 사이클 대상
- **본 사이클 외 V{N}_{연번}__patch.sql 신설** — 본 사이클은 V{N}__{도메인}.sql 초기 정리만, 패치 정책은 향후 변경 발생 시 적용
- **신규 자동 차단 훅** — `check-ddl-column-comment.sh` 글롭 정정만 (룰 갱신 ANALYZE 단계 완료)

## 도메인 모델

본 사이클은 **자산 정리 작업** — 신규 엔티티·DTO·컬럼·테이블 도입 없음. ANALYZE1 §신규 엔티티/DB 컬럼 = "없음" 확정. 따라서 도메인 모델 표 생략.

## DB 설계 변경

본 사이클은 **운영 DB 무변경** — DROP·CREATE·ALTER 모두 발생 없음. 새로 작성되는 V1~V9 SQL 의 내용은 기존 V6_1~V9_4 + api/db 의 모든 ALTER 결과를 최종 상태로 합본한 것이며, 신규 환경 부트스트랩 시에만 적용된다 (기존 DB 에 적용 금지 — README 명시 정책).

### 흡수 정렬 규칙 (ANALYZE1 §흡수 정렬 규칙 SSOT)

| V번호 | 도메인 | 흡수 대상 |
|------|-------|---------|
| V1 | auth | `api/db/migration/refresh_token_p.sql` |
| V2 | facility | `common/db/init/V6_1` + `V7_1` + `V8_1` + `common/db/migration/V9_2` (facility 인덱스만) |
| V3 | instrument | `common/db/init/V6_2` + `V8_2` + `V8_3` + `V8_5` + `V8_6` (drop 반영) + `V8_7` + `V9_3-init` + `common/db/migration/V9_2` (instrument 인덱스만) |
| V4 | menu | `common/db/init/V6_6` |
| V5 | opt | `common/db/migration/V9_3-migration` (predc_1m_h) |
| V6 | proc | `common/db/init/V9_4` |
| V7 | raw | `common/db/init/V6_5` |
| V8 | tag | `common/db/init/V6_4` + `common/db/migration/V9_1` (drop 반영) |
| V9 | user | `api/db/migration/user_m.sql` + `user_m_use_yn_alignment.sql` (정렬 결과 반영) |

**정렬 원칙** (ANALYZE1 §흡수 정렬 규칙):
- ALTER 이력 (V8_2 추가, V8_6 제거 등) 은 **최종 결과 상태** 만 반영 → 초기 CREATE 에 정렬
- 예: `pump_m.tag_nm` 은 V8_2 추가, V8_6 에서 제거됐으므로 V3__instrument.sql 에 포함되지 않음
- `dwt_m.min_req_prsr` (V8_3) + `min_req_branch_prsr` (V8_7) 둘 다 V2__facility.sql 의 `dwt_m CREATE TABLE` 에 포함
- `user_m.use_yn` 은 `VARCHAR(1) NOT NULL` (DEFAULT 미설정) — `user_m_use_yn_alignment.sql` 정렬 결과 반영
- `tag_m` 의 `use_yn` 컬럼 추가 + `unit_cd` 제거 (V9_1) 결과 반영 — V8__tag.sql 의 `tag_m CREATE TABLE` 에 `use_yn` 포함, `unit_cd` 미포함

## 구현 방향

### Phase 1: docs/ddl 디렉토리 신설 + 9개 도메인 SSOT 사본 작성

- `backend/docs/ddl/` 디렉토리 신설
- 9개 SQL 파일 작성 — 도메인 패키지명 1:1 매칭 (`auth.sql`·`facility.sql`·`instrument.sql`·`menu.sql`·`opt.sql`·`proc.sql`·`raw.sql`·`tag.sql`·`user.sql`)
- 내용: ANALYZE1 §흡수 정렬 규칙에 따라 기존 V6_1~V9_4 + api/db 의 최종 결과 상태 + COMMENT ON COLUMN 전부 포함
- 컬럼 COMMENT 의무: `db/indexing-and-migration.md §4` 정책 준수 — BaseEntity 4컬럼 표준 라벨 + 도메인 컬럼 라벨 패턴

### Phase 2: resources/db/migration V1~V9 운영본 작성

- `common/src/main/resources/db/migration/V{1..9}__{도메인}.sql` 9개 파일 작성
- 내용은 `docs/ddl/{도메인}.sql` 과 **완전 동일** (단방향 복제 — docs/ddl 작성 후 resources 복제)
- 훅 `check-ddl-column-comment.sh` 가 매칭 글롭 `**/db/migration/*.sql` 로 자동 차단 — 컬럼 COMMENT 누락 0건 확보

### Phase 3: README.md 작성 (운영자 안내 + 매핑 표)

- `common/src/main/resources/db/migration/README.md` 신규 작성
- 포함 내용:
  1. **목적** — 신규 환경 부트스트랩 시 도메인별 SQL 적용 안내
  2. **⚠️ 기존 환경 적용 금지 정책** — 기존 V6~V9 가 적용된 DB 에 V1~ 재적용 금지 (DROP 후 V1~ 적용 절대 금지)
  3. **도메인-V번호 매핑 표** (9건) — `indexing-and-migration.md §5.2` SSOT
  4. **신규 환경 적용 명령** — `psql -h ... -U swtp -d smartwtp -f V1__auth.sql` 등 9개 순서 명시
  5. **동등성 검증 절차** — `pg_dump --schema-only` 비교 (기존 DB vs V1~ 적용 신환경) 또는 `psql \d+ {table}` 대조
  6. **향후 변경 정책** — V{N} 동결 + `V{N}_{연번}__patch.sql` 분리 (이중 정책 — `indexing-and-migration.md §5.4`)
  7. **docs/ddl 사본 관계** — 양쪽 동시 갱신 의무 (`indexing-and-migration.md §5.3`)

### Phase 4: 구 디렉토리·파일 삭제

- **삭제 1**: `common/src/main/resources/db/init/` 디렉토리 전체 (15개 V*.sql + README.md)
- **삭제 2**: `common/src/main/resources/db/migration/V9_1__tag_m_use_yn_and_drop_unit_cd.sql` · `V9_2__facility_instrument_lookup_indexes.sql` · `V9_3__predc_1m_h.sql` (구 3개)
- **삭제 3**: `api/src/main/resources/db/migration/` 디렉토리 전체 (3개 SQL)
- 삭제 후 디렉토리 검증 — `test -d` 결과 부재 확인

### Phase 5: 빌드 검증 + 룰 정합성 재확인

- `./gradlew.bat clean build` 통과 (SQL 파일 자체는 컴파일 대상 아니지만 자원 검증)
- 룰 갱신 6건 (ANALYZE 단계 완료) 재확인 — `db/init` grep 결과 룰 영역에 잔존 없음
- 훅 동작 확인 — `check-ddl-column-comment.sh` 가 `db/migration/*.sql` 만 매칭하는지 grep 검증

## 성공 기준 (검증 가능 형태)

> ROOT [`coding-discipline.md §4.2`](../../../../../.claude/rules/coding-discipline.md) 적용. 모호 목표 금지, 각 기준에 검증 명령·테스트·조회 명시.

| 기준 | 검증 명령 / 테스트 / 조회 |
|------|----------------------|
| `common/src/main/resources/db/init/` 디렉토리 완전 삭제 | `test -d common/src/main/resources/db/init` 결과 비-zero (디렉토리 부재) |
| `api/src/main/resources/db/migration/` 디렉토리 완전 삭제 | `test -d api/src/main/resources/db/migration` 결과 비-zero |
| `common/src/main/resources/db/migration/` 하위 V{1..9}__{도메인}.sql 9개 + README.md 1개 정확히 존재 | `ls common/src/main/resources/db/migration/*.sql | wc -l` → 9 반환 + `test -f common/src/main/resources/db/migration/README.md` → 0 반환 |
| `backend/docs/ddl/` 하위 9개 도메인 SQL 정확히 존재 (auth/facility/instrument/menu/opt/proc/raw/tag/user) | `ls backend/docs/ddl/*.sql` 결과 9개 + 파일명이 도메인 패키지 9개와 1:1 일치 |
| 각 V{N}__{도메인}.sql 와 docs/ddl/{도메인}.sql 내용 완전 동일 | `diff common/src/main/resources/db/migration/V{N}__{도메인}.sql backend/docs/ddl/{도메인}.sql` 결과 0 라인 차이 (9 도메인 모두) |
| 룰 영역에 `db/init` 잔존 없음 (정정 누락 검증) | `grep -rn "db/init" .claude/rules/ .claude/hooks/ .claude/agents/ .claude/commands/` 결과 → "갱신 이력·폐지·2026-05-20·이력" 컨텍스트 외 잔존 0건 |
| `check-ddl-column-comment.sh` 매칭 글롭이 `*/db/migration/*` 단독 | `grep '"\*/db/init\*"\|"\*/db/migration\*"' .claude/hooks/check-ddl-column-comment.sh` → `db/init` 매칭 0건 |
| `process/doc-harness/README.md` 에 docs/ddl 카테고리 절 존재 | `grep "DDL 도메인 SSOT 사본" .claude/rules/process/doc-harness/README.md` → 매칭 1건 이상 |
| `db/indexing-and-migration.md §5` SQL 관리 절 존재 | `grep "## 5. SQL 관리" .claude/rules/db/indexing-and-migration.md` → 매칭 1건 |
| 컬럼 COMMENT 의무 충족 (`check-ddl-column-comment.sh` Write/Edit 시 자동 차단 안 됨) | 각 V{N}__{도메인}.sql 작성 시 훅 통과 확인 (Write 시점 자동 검증) |
| `./gradlew.bat clean build` BUILD SUCCESSFUL | `BUILD SUCCESSFUL` 출력 확인 |

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md §1`](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE 의 가정·미해결 질문을 PLAN 결정으로 변환.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| V{N}__{도메인}.sql 향후 변경 정책 — 이중 정책 (docs/ddl 누적 / resources patch 분리) | 결정 | ANALYZE1 단계 사용자 결정 반영 완료. `indexing-and-migration.md §5.4` 명문화 완료. 본 사이클은 patch 파일 생성 없음 (초기 V1~V9 만 작성) |
| proc/ai 도메인 SQL 단방향 정책 미채택 — 9개 도메인 모두 동일 정책 | 결정 | ANALYZE1 단계 사용자 결정 반영 완료. `indexing-and-migration.md §5.3` 단일 정책 명문화 완료 |
| docs/ddl ↔ resources/db/migration 복제 방향 — docs/ddl 작성 후 resources 복제 (수동) | 결정 | Phase 1 → Phase 2 순서로 작성 (docs/ddl 먼저, resources 가 사본). 양쪽 동시 갱신 의무는 향후 변경 사이클에서 유지 |
| `user_m.use_yn DEFAULT 'Y'` 정렬 — V9__user.sql 의 CREATE 에 DEFAULT 미설정 (`indexing-and-migration.md §3.3` 정책 정합) | 결정 | V9__user.sql 의 `use_yn VARCHAR(1) NOT NULL` 형태로 작성, DEFAULT 'Y' 제거 |
| `user_m.ck_user_m_use_yn` CHECK 제약 — 현상 유지 (`indexing-and-migration.md §3.2`) | 결정 | V9__user.sql 의 CREATE TABLE 에 CHECK 제약 포함 (기존 CHECK 보존) |
| `idx_user_m_use_yn` 단독 인덱스 — `indexing-and-migration.md §3.4` DROP CONCURRENTLY 권고 | 가정 | 본 사이클은 운영 DB 무변경이므로 V9__user.sql 에 본 단독 인덱스 CREATE 문 포함 여부 결정 필요. 결정: **포함하지 않음** (단독 인덱스 옵티마이저 Seq Scan 선호 + §3.4 권고 정합). 기존 DB 의 인덱스는 별도 운영 사이클에서 DROP CONCURRENTLY 적용 |
| `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` 의 BRIN 인덱스 누락 — 기존 `idx_predc_1m_h_tag_time` 만 있고 BRIN 인덱스 없음 | 가정 | V5__opt.sql 작성 시 기존 V9_3 의 인덱스만 정확히 반영 (BRIN 인덱스 추가 금지 — 본 사이클은 기존 자산 재배치, 신규 인덱스 도입 금지) |

## 도메인 룰 4영역 점검 (ANALYZE1 §도메인 룰 4영역 점검 SSOT 참조)

| 영역 | 해당/비해당 | 근거 (ANALYZE1 동일) |
|------|----------|---------------------|
| 알람 4단계 | 비해당 | alarm_h 등 0개 접촉 |
| 인터록 선행조건 | 비해당 | pump_interlock_p 백지화 완료 |
| AI 운전 모드 | 비해당 | proc_m·ai_drvn_mod_p·ai_drvn_mod_h 정의 그대로 재배치 |
| 이력 기록 의무 | 비해당 | ai_drvn_mod_h 구조 동일 |

## 제외 사항

- **운영 DB 변경**: DROP·CREATE·ALTER 발생 0건
- **자동 동기화 스크립트**: 도입 없음 — REVIEW 권고(중간) 자동 점검 위임으로 충분
- **Flyway/Liquibase 도입**: 별도 ANALYZE 사이클 대상
- **신규 컬럼·테이블·인덱스 도입**: 본 사이클 전체 0건 (기존 자산 재배치 한정)
- **이력 patch 파일 (V{N}_{연번}__patch.sql) 생성**: 본 사이클은 V1~V9 초기 정리만, patch 정책은 향후 변경 시 적용
- **삭제 파일 (예: pump_m.tag_nm) 의 흔적 보존**: ALTER 이력 V8_2/V8_6 의 시간순 변경 흔적은 흡수 정렬 후 최종 결과만 반영 — 변경 이력은 git log 가 SSOT

## 영향받는 모듈

| 모듈 | 영향 종류 |
|-----|---------|
| `common` | SQL 자산 재구성 (init 폐지·migration 단일화·V1~V9 신규 작성·README 신설). Java 소스 0건 |
| `api` | `src/main/resources/db/migration/` 디렉토리 삭제만. Java 소스 0건 |
| `scheduler` | 영향 없음 |

## 테스트 전략

- **단위 테스트 변경 없음** — Java 소스 코드 영향 0건
- **통합 테스트 검증** — `application-test.yml` 의 로컬 PostgreSQL 연결로 기존 테스트 (`UserServiceTest` 등) 가 그대로 통과해야 함 — 운영 DB 무변경 정책 정합
- **빌드 검증** — `./gradlew.bat clean build` 통과 (전 모듈)
- 도메인 시나리오 테스트 (`AlarmEscalationScenarioTest` 등 5종) 영향 없음 — 본 사이클 도메인 4영역 비해당

## 예상 산출물

- [태스크](../../../tasks/20260520/sql_관리포인트_통합/TASK1.md)
- TASK 분할 평가: Phase 5개 + 예상 체크박스 25~30개 → 분할 기준 (Phase 10+ 또는 체크박스 60+) 미달 → 단일 TASK1.md 작성
