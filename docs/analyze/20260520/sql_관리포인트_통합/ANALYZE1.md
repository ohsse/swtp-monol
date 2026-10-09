---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# SQL 관리포인트 통합 — 도메인 분석

## 작업 배경

backend 모듈의 SQL 자산이 3개 디렉토리에 분산되어 있어 관리 포인트를 단일화한다.

### 현 상태 (조사 결과)

| 디렉토리 | 파일 수 | 비고 |
|---------|--------|------|
| `common/src/main/resources/db/init/` | 15개 V*.sql + README.md | V6_1 ~ V9_4 (V1~V5 는 pump+AI 백지화 사이클 1, 2026-05-12 에 삭제) |
| `common/src/main/resources/db/migration/` | 3개 V*.sql | V9_1, V9_2, V9_3 — **init 의 V9_3 와 번호 충돌** (predc_1m_h vs pump_m drive_type_cd) |
| `api/src/main/resources/db/migration/` | 3개 SQL | `user_m.sql`·`refresh_token_p.sql`·`user_m_use_yn_alignment.sql` — **V prefix 부재** |

### 환경 조건

- Flyway/Liquibase **미도입** — 운영자가 `psql -f` 수동 적용 (자동 마이그레이션 도구 없음)
- `ddl-auto: none` (운영·dev·gs 프로파일) / `update` (local 프로파일)
- 자동 차단 훅 `check-ddl-column-comment.sh` 매칭 글롭: `**/db/init/*.sql` + `**/db/migration/*.sql`
- backend 도메인 패키지 9개: `com.mo.swtp.{auth, facility, instrument, menu, opt, proc, raw, tag, user}` (api·common 양 모듈 동일)

### 사용자 결정 (요청 구체화 4건 모두 응답 완료)

1. **워크플로우**: `/dev` Large 사이클로 진행
2. **docs/ddl 정리 단위**: 도메인당 단일 파일 누적 (`facility.sql`·`instrument.sql` 등 9개)
3. **resources/db ↔ docs/ddl 관계**: resources/db 통합 + V1 재번호, **docs/ddl 은 도메인 SSOT 사본** (양쪽 모두 유지)
4. **기존 운영 DB**: 스키마 무변경, 파일만 V1 재번호 — 작업 디렉토리 자산만 정리

### 외부 산출물

본 사이클은 외부 요구사항 명세서·다이어그램 등 첨부물 없음. 사용자 요청문 + 현 SQL 파일 21개 + backend 패키지 트리 + 룰 SSOT 가 입력 자료.

---

## 회의록 (5인 회의 Round 1)

### 안건 1: docs/ddl 신규 카테고리 도입 — doc-harness/README.md §디렉토리 구조 영향

- **호출 에이전트**: `wtp-backend-engineer` (계층 책임·디렉토리 구조)
- **Round 1 답변 요약**:
  - `docs/ddl/` 7번째 카테고리 신설 타당. `governance/REPORT.md` 선례와 동일 라이프사이클 (status frontmatter 미사용, 사이클 외 영구 누적 자산, 슬러그 단위 관리 아님)
  - 파일명 컨벤션: backend 도메인 패키지명 (9개) 과 **1:1 일치** (`facility.sql`·`instrument.sql` 등). 패키지 신설·폐기 시 파일도 함께 관리한다는 규칙 명시 필요
  - doc-harness 의 `/dev:result`·`/dev:review` 단계 영향: RESULT "### 의도된 변경" 섹션에 명기만 — REVIEW 블로커 대상 아님 (코드 자산 아닌 도메인 SSOT 사본)
- **결론**: `process/doc-harness/README.md §backend 작업 산출물` 섹션 하단에 `docs/ddl/` 카테고리 추가. status frontmatter 미사용 + 도메인 패키지명 1:1 매칭 규칙 명문화.

### 안건 2: V1 재번호 도메인별 그룹화 전략

- **호출 에이전트**: `wtp-dba-reviewer` (마이그레이션 전략·DDL 운영) + `wtp-backend-engineer` (운영 부트스트랩 절차)
- **Round 1 답변 요약**:
  - **DBA**: 도메인 단위 단일 합본 (V1__auth.sql ~ V9__proc.sql) 채택 가능. 파일 내부 순서로 적용 의존성 강제 충분 (단일 `psql -f` 트랜잭션 내 안전). **단, 향후 도메인별 변경 시 V{N} 파일은 초기 CREATE 전용 동결, ALTER 는 V{N}_{연번}__patch.sql 별도 분리 권고** (가독성·추적성). 새 도메인 도입 시 V10 부여 + 도메인-V번호 매핑 표 README 명시 의무 (없으면 V9_3 충돌 재발 구조적 위험)
  - **Backend 엔지니어**: 안건 4 결론과 연계 — `user_m_use_yn_alignment.sql` 의 ALTER 이력은 V{N}__user.sql 초기 CREATE 에 정렬된 형태로 흡수 (`coding-discipline.md §2` 단순성 우선). Flyway 미도입 + 수동 `psql -f` 환경에서는 "현재 DB 상태"를 반영한 단일 CREATE 가 SSOT 이며 ALTER 단편화 보존은 혼란 가중
- **결론**:
  - 본 사이클은 **초기 정리 시점** — 기존 V6_*~V9_* 의 모든 ALTER 를 V{N}__{도메인}.sql 초기 CREATE 에 정렬 흡수 (Backend 엔지니어 결론)
  - 향후 도메인 변경 시 정책 — V{N}__{도메인}.sql 동결, 새 변경은 V{N}_{연번}__patch.sql 분리 (DBA 권고). 단 docs/ddl/{도메인}.sql 사본은 ALTER 누적 허용 (사용자 의도) — **이중 정책 트레이드오프**는 §가정 및 미해결 질문에서 PLAN 결정으로 이관
  - 도메인-V번호 매핑 표 README 명시 의무 채택

### 안건 3: init/migration 디렉토리 분리 의미 재정의

- **호출 에이전트**: `wtp-dba-reviewer` + `wtp-backend-engineer`
- **Round 1 답변 요약**:
  - **DBA (블로커 — 높음)**: `db/init/` + `db/migration/` 분리 의미가 실질적으로 없고 V9_3 번호 충돌이 그 부작용의 직접 증거. `db/migration/` **단일 디렉토리로 일원화**가 정답. 일원화 안 하면 V1 재번호만으로는 충돌 구조 재발이 불가피
  - **Backend 엔지니어**: 동의. 훅 글롭 `**/db/init/*.sql` 항목 삭제, `**/db/migration/*.sql` 단독 잔존. 룰 정정 2건 (`hooks-guide.md §6.2`·`indexing-and-migration.md §4.1`)
- **결론**: `db/init/` 디렉토리 완전 삭제, 모든 SQL 을 `common/src/main/resources/db/migration/` 단일 디렉토리로 일원화. **(블로커 해소 의무)**

### 안건 4: api/db/migration 비-V prefix 파일 도메인 매핑 및 처리

- **호출 에이전트**: `wtp-backend-engineer` (모듈 경계) + `wtp-glossary-manager` (도메인 매핑)
- **Round 1 답변 요약**:
  - **Backend 엔지니어**: `api/src/main/resources/db/migration/` 디렉토리 **완전 삭제** (common 이관). `user_m.sql` → V{N}__user.sql 흡수, `refresh_token_p.sql` → V{N}__auth.sql 흡수, `user_m_use_yn_alignment.sql` 의 변경 결과 (`use_yn VARCHAR(1)`, DEFAULT 제거) 는 V{N}__user.sql 초기 CREATE 에 **정렬 흡수** (별도 patch 파일 미도입). `api/build.gradle` SQL 파일 특별 처리 부재 확인 — common 일원화 안전
  - **Glossary 관리자**: 비즈니스 도메인 약어 매핑 충돌 없음 (`refresh_token_p` 는 `auth` 도메인, `user_m` 은 `user` 도메인 — 양 약어 모두 마스터 도메인 등록 완료)
- **결론**: api 모듈의 `db/migration/` 디렉토리 완전 삭제 + common 의 V{N}__{도메인}.sql 로 흡수. `user_m_use_yn_alignment.sql` 의 결과 상태는 V{N}__user.sql 초기 CREATE 에 정렬 흡수.

### 안건 5: `check-ddl-column-comment.sh` 훅 매칭 글롭 갱신

- **호출 에이전트**: `wtp-backend-engineer` (훅 동작 영향) + `wtp-dba-reviewer` (DDL 정책 정합)
- **Round 1 답변 요약**:
  - **Backend 엔지니어**: 안건 3 단일화 채택 후 훅 스크립트 본문 `case "$file_path" in */db/init/*|*/db/migration/*` 에서 `*/db/init/*` 제거, `*/db/migration/*` 단독 잔존. 신규 훅 추가 없음 (1줄 정정). `hooks-guide.md §6.2 매칭 글롭` + `indexing-and-migration.md §4.1 적용 대상` 두 룰 문서도 동일 PR 정정 의무
  - **DBA**: 동의. 훅 차단 로직 자체 변경 없음 (글롭 경로 정정만)
- **결론**: 훅 스크립트 1줄 + 룰 문서 2건 정정. 신규 훅 추가 불필요 (`coding-discipline.md §3` 정밀한 수정 원칙).

### 안건 6: docs/ddl SSOT 사본과 resources/db 통합본 간 동기화 의무

- **호출 에이전트**: `wtp-dba-reviewer` (운영자 절차) + `wtp-backend-engineer` (룰 명문화)
- **Round 1 답변 요약**:
  - **DBA**: 동기화 의무는 `indexing-and-migration.md §4.1` 적용 대상 절에 단독 단락 추가. "도메인 SQL 변경 시 `docs/ddl/{도메인}.sql` 와 `common/src/main/resources/db/migration/V{N}__{도메인}.sql` 양쪽을 **동일 커밋에서 동시 갱신**. 한쪽만 변경하는 커밋은 REVIEW 단계 블로커." docs/ddl 경로는 훅 적용 대상 외 — REVIEW 자동 점검 (`wtp-backend-engineer`) 위임
  - **Backend 엔지니어**: REVIEW 자동 점검 위임 부분적으로 실현 가능. 정량 기준 — 같은 커밋 내 `V{N}__{도메인}.sql` 변경 시 `docs/ddl/{도메인}.sql` 미포함이면 **권고(중간) 등급**. **블로커(높음) 격상은 미권고** — 본 사이클의 SQL 자산은 도메인 안전·보안 직결 아님 (`coding-discipline.md §2.1` 블로커 격상 조건 미해당). TASK 체크박스에 양쪽 경로 쌍 명시 의무 (훅 파싱 호환 경로) 시 `git diff` 교차 비교로 자동 감지 가능
- **결론**: `indexing-and-migration.md §4.1` 에 양쪽 동시 갱신 의무 단락 추가 + REVIEW 권고(중간) 자동 점검 위임. 자동 차단 훅 신설 보류 (`hooks-guide.md §7.3` 도입 검토 트리거 충족 전까지).

### 안건 7: 기존 운영 DB 와 V1 재번호 파일 간 매핑 안내

- **호출 에이전트**: `wtp-dba-reviewer`
- **Round 1 답변 요약**:
  - README 명시 정책으로 충분. 내용에 "동등성 검증 절차" 추가 의무 — `pg_dump --schema-only` DDL 비교 또는 `psql \d+ {table}` 대조 절차 1단락. 환경 변수 가드 (예: `BOOTSTRAP_ONLY=1` 스크립트) 는 구현 부담 대비 효과 적음 — Flyway 미도입 상태에서 과도한 가드는 `coding-discipline.md §2` 단순성 위반 소지
- **결론**: 신규 `common/src/main/resources/db/migration/README.md` 에 (1) 기존 환경에는 V1~ 재적용 금지 정책 (2) `pg_dump --schema-only` 동등성 검증 절차 (3) 도메인-V번호 매핑 표 (4) V{N} 동결·patch 분리 정책 모두 명시.

### 도메인 전문가 (도메인 룰 4영역 점검)

- **호출 에이전트**: `wtp-domain-expert`
- **Round 1 답변 요약**:
  - 도메인 룰 4영역 (알람·인터록·운전 모드·이력 기록) **모두 비해당** — 본 사이클은 자산 재배치 작업, 운영 DB 스키마 무변경. 알람·인터록 관련 테이블 0개 접촉, `proc_m`·`ai_drvn_mod_p`·`ai_drvn_mod_h` 는 재배치 후 정의 동일
  - `coding-discipline.md §2.5` 면책 조항 인용 근거 영향 없음 — SQL 재배치, Java 코드 작성 없음
  - **권고(중간) 1건**: `proc`·`ai` 도메인 SQL 이중 SSOT 위험이 다른 도메인보다 높음 (향후 `transition_reason` 컬럼 추가 등 구조 변경 예고). PLAN 단계에서 단방향 정책 (resources 만 SSOT, docs/ddl 단순 조회용) 명시 여부 결정 권고
- **결론**: 본 사이클 도메인 안전성 영향 없음. proc/ai 도메인 단방향 정책 여부는 PLAN 단계 결정 사항 (§가정 및 미해결 질문).

---

## 표준 사전 카탈로그

### 신규 표준 단어

없음 (본 사이클은 자산 재배치 작업, 신규 단어 등장 없음).

### 신규 표준 데이터 도메인

없음 (기존 V6_*~V9_* 의 SQL 타입·길이가 그대로 재배치, 신규 컬럼·재정의 0건).

### 신규 표준 용어

없음 (기존 컬럼이 그대로 V{N}__{도메인}.sql 로 재배치, 신규 컬럼명 0건).

> ROOT 어휘 사전 (`swtp/.claude/rules/dict/standard-words.md`·`standard-data-domains.md`·`domain-abbreviations.md`) 변경 0건. ROOT 룰 갱신 (`coding-discipline.md §5.1` 5인 회의 + 사용자 승인 절차) 발동 사유 없음. `wtp-glossary-manager` 검토 결과 "어휘 사전 영향 없음" 확정.

---

## 신규 엔티티/DB 컬럼

본 사이클에서 신규 엔티티·DB 컬럼 도입 없음. 운영 DB 스키마 무변경. SQL 파일 21개를 9개 도메인별 V{N}__{도메인}.sql 단일 합본으로 재배치만 수행.

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 위치 | 해소책 |
|----------|------|--------|
| init/migration 디렉토리 번호 공간 충돌 | `common/src/main/resources/db/init/V9_3__pump_m_drive_type_cd.sql` vs `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` | `db/init/` 디렉토리 완전 삭제, `db/migration/` 단일 디렉토리로 일원화 (안건 3 블로커 해소) |
| api 모듈 db/migration 디렉토리의 V prefix 부재 | `api/src/main/resources/db/migration/user_m.sql` 등 3건 | api 모듈 디렉토리 완전 삭제, common 의 V{N}__{도메인}.sql 로 흡수 (안건 4) |
| 훅 매칭 글롭 룰 불일치 | `process/hooks-guide.md §6.2` + `indexing-and-migration.md §4.1` | 글롭 경로 `db/init/` 항목 삭제, `db/migration/` 단독 잔존으로 정정 (안건 5) |
| docs/ddl 카테고리 부재 | `process/doc-harness/README.md §디렉토리 구조` | `docs/ddl/` 7번째 카테고리 절 추가 (안건 1) |
| docs/ddl ↔ resources/db 동기화 의무 부재 | `indexing-and-migration.md §4.1` | 양쪽 동시 갱신 의무 단락 추가 + REVIEW 자동 점검 위임 (안건 6) |
| 기존 운영 DB ↔ V1 재번호 매핑 안내 부재 | (신규 작성) `common/src/main/resources/db/migration/README.md` | 신규 README 작성 — 정책·동등성 절차·매핑 표 일원 (안건 7) |

---

## PLAN 으로 전달할 결정 사항

### 도메인-V번호 매핑 표 (Backend 패키지 사전순)

| V번호 | 도메인 | 흡수 대상 (현 파일 → V{N}__{도메인}.sql) |
|------|-------|--------------------------------------|
| V1 | auth | `api/db/migration/refresh_token_p.sql` |
| V2 | facility | `common/db/init/V6_1__facility_master_tables.sql` + `V7_1__point_master_table.sql` + `V8_1__facility_m_prsf_skeleton.sql` + `common/db/migration/V9_2__facility_instrument_lookup_indexes.sql` (facility 측 인덱스만) |
| V3 | instrument | `common/db/init/V6_2__instrument_master_tables.sql` + `V8_2__pump_m_self_columns.sql` + `V8_3__dwt_m_self_columns.sql` + `V8_5__pump_m_oprtng_type.sql` + `V8_6__pump_m_drop_tag_nm.sql` (drop 결과 반영) + `V8_7__dwt_m_branch_prsr.sql` + `common/db/init/V9_3__pump_m_drive_type_cd.sql` + `common/db/migration/V9_2__facility_instrument_lookup_indexes.sql` (instrument 측 인덱스만) |
| V4 | menu | `common/db/init/V6_6__menu_master_table.sql` |
| V5 | opt | `common/db/migration/V9_3__predc_1m_h.sql` |
| V6 | proc | `common/db/init/V9_4__proc_ai_drvn_mod_도입.sql` |
| V7 | raw | `common/db/init/V6_5__rawdata_1m_h.sql` |
| V8 | tag | `common/db/init/V6_4__tag_master_table.sql` + `common/db/migration/V9_1__tag_m_use_yn_and_drop_unit_cd.sql` (drop 결과 반영) |
| V9 | user | `api/db/migration/user_m.sql` + `api/db/migration/user_m_use_yn_alignment.sql` (정렬 결과 반영) |

> **흡수 정렬 규칙**: ALTER 이력 (V8_2 의 rated_head/rated_flwrt/tag_nm 추가, V8_6 의 tag_nm 제거 등) 은 최종 결과 상태로 초기 CREATE 에 정렬. 예: `pump_m` 의 `tag_nm` 컬럼은 V8_2 에서 추가되고 V8_6 에서 제거됐으므로 V3__instrument.sql 의 `pump_m CREATE TABLE` 에 포함되지 않는다. `dwt_m.min_req_prsr` 은 V8_3 추가, V8_7 의 `min_req_branch_prsr` 추가가 모두 반영된 최종 상태로 초기 CREATE 에 포함.

### docs/ddl 디렉토리 구조

```
backend/docs/ddl/
├── auth.sql          ← refresh_token_p
├── facility.sql      ← facility_m + pwtf_m + dwt_m + rsv_m + point_m + prsf_m + 인덱스
├── instrument.sql    ← instrument_m + pump_m + valve_m + flwmtr_m + prsmtr_m + lvmtr_m + elcmtr_m + 인덱스
├── menu.sql          ← menu_m + menu_role_r
├── opt.sql           ← predc_1m_h
├── proc.sql          ← proc_m + ai_drvn_mod_p + ai_drvn_mod_h
├── raw.sql           ← rawdata_1m_h + sequence + 파티션
├── tag.sql           ← tag_m
└── user.sql          ← user_m
```

내용은 `common/src/main/resources/db/migration/V{N}__{도메인}.sql` 와 동일 (도메인 SSOT 사본).

### resources/db 재구성 후 디렉토리 구조

```
backend/common/src/main/resources/db/
└── migration/                ← db/init/ 폐지, migration 단일화
    ├── README.md             ← 신규 작성 — 정책·동등성·매핑·patch 정책
    ├── V1__auth.sql
    ├── V2__facility.sql
    ├── V3__instrument.sql
    ├── V4__menu.sql
    ├── V5__opt.sql
    ├── V6__proc.sql
    ├── V7__raw.sql
    ├── V8__tag.sql
    └── V9__user.sql
```

### 적용할 패턴

- **`coding-discipline.md §3` 정밀한 수정**: 훅 글롭 1줄 정정, ALTER 단편화 흡수 시 의미·구조 변경 0건
- **`coding-discipline.md §2` 단순성**: 자동 동기화 스크립트·환경 변수 가드 미도입 — REVIEW 권고 + README 명시로 충분. 도메인별 예외 정책 없음 (proc/ai 단방향 정책 미채택)
- **`process/doc-harness/README.md §디렉토리 구조`**: docs/ddl/ 7번째 카테고리 패턴 (status frontmatter 미사용, governance/REPORT.md 와 동일 라이프사이클)
- **`indexing-and-migration.md §4.1` + §4.x 신설**: 매칭 글롭 정정 + 양쪽 동시 갱신 의무 단락 추가 + **이중 정책 명문화** (docs/ddl 누적 vs resources patch 분리)

### 룰 변경 영향 요약

| 룰 파일 | 변경 종류 |
|--------|---------|
| `process/doc-harness/README.md` | docs/ddl/ 카테고리 신설 절 추가 |
| `process/hooks-guide.md §6.2` | 매칭 글롭에서 `**/db/init/*.sql` 삭제 |
| `db/indexing-and-migration.md §4.1` | 적용 대상 표기 정정 + 양쪽 동시 갱신 의무 단락 추가 + V{N} 동결·patch 분리 정책 |
| `.claude/hooks/check-ddl-column-comment.sh` | `case` 분기 1줄 정정 (`*/db/init/*|` 제거) |

---

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md §1`](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE 가정·미해결 질문 명시. 사용자 검토 시점에 2건 결정 반영 (2026-05-20).

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| **V{N}__{도메인}.sql 향후 변경 정책 — 이중 정책 채택.** `docs/ddl/{도메인}.sql` 사본은 ALTER 누적 (사용자 의도). `common/src/main/resources/db/migration/V{N}__{도메인}.sql` 운영본은 V{N} 동결 + `V{N}_{연번}__patch.sql` 별도 분리 (DBA 권고). 운영자 적용 안전성 + 도메인 SSOT 가독성 양립 | **결정** | 2026-05-20 사용자 결정. `indexing-and-migration.md §4.x` 본문에 이중 정책 명문화 |
| **proc/ai 도메인 SQL 단방향 정책 미채택 — 모든 도메인 동일 정책 적용.** 9개 도메인 모두 `docs/ddl/` + `resources/db/migration/` 양쪽 유지 + REVIEW 권고(중간) 자동 점검 공통 적용. 도메인별 예외 행 없음 — 정책 단순화 우선. 향후 `transition_reason` 추가 등 구조 변경 시에도 양쪽 동시 갱신 의무로 분기 위험 완화 | **결정** | 2026-05-20 사용자 결정. 도메인별 예외 행 없음 |
| `docs/ddl/{도메인}.sql` 의 내용 형식은 V{N}__{도메인}.sql 과 **완전 동일** 가정 (CREATE + INDEX + SEQUENCE + COMMENT 포함). 단방향 복제 방향 — resources/db/migration → docs/ddl (수동) 가정 | 가정 | 향후 자동 복제 스크립트 도입 검토 시 별도 ANALYZE |
| 본 사이클에서 작성될 V{N}__{도메인}.sql 의 ALTER 이력 흡수 시 V8_2 의 `tag_nm` 컬럼 (V8_6 제거됨) 등은 **최종 결과 상태** 만 반영. 흡수 정렬에 의한 컬럼·인덱스 순서 결정은 V6_*~V9_* 의 최신 적용 결과 기준 | 가정 | 흡수 시 정렬 기준 명시 (위 PLAN 으로 전달 §흡수 정렬 규칙) |

---

## 성공 기준 후보 (PLAN 변환 대상)

> ROOT [`coding-discipline.md §4.2`](../../../../../.claude/rules/coding-discipline.md) 적용. 후보 기준은 PLAN 단계에서 검증 명령·테스트·조회로 확정.

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `common/src/main/resources/db/init/` 디렉토리가 존재하지 않음 (완전 삭제) | `test -d common/src/main/resources/db/init && echo FAIL || echo OK` → OK 반환 |
| `api/src/main/resources/db/migration/` 디렉토리가 존재하지 않음 (완전 삭제) | `test -d api/src/main/resources/db/migration && echo FAIL || echo OK` → OK 반환 |
| `common/src/main/resources/db/migration/` 하위 V{1..9}__{도메인}.sql 파일 9개 + README.md 1개 모두 존재 | `ls common/src/main/resources/db/migration/V*.sql | wc -l` → 9 반환 |
| `backend/docs/ddl/` 하위 {도메인}.sql 9개 (auth/facility/instrument/menu/opt/proc/raw/tag/user) 존재 | `ls backend/docs/ddl/*.sql | wc -l` → 9 반환 |
| 각 V{N}__{도메인}.sql 의 CREATE TABLE 컬럼 수가 기존 V6_*~V9_* 합산 결과와 일치 (스키마 동등성) | 도메인별 컬럼 목록 수기 대조 또는 `pg_dump --schema-only` 비교 결과 동등 |
| `check-ddl-column-comment.sh` 훅 매칭 글롭이 `*/db/migration/*` 단독 (`*/db/init/*` 제거) | `grep "db/init" .claude/hooks/check-ddl-column-comment.sh` → 매칭 0건 |
| `process/hooks-guide.md §6.2` 매칭 글롭 표기에서 `**/db/init/*.sql` 행 제거 | `grep "db/init" .claude/rules/process/hooks-guide.md` → §6.2 영역 매칭 0건 |
| `db/indexing-and-migration.md §4.1` 적용 대상 표기에서 `db/init/` 제거 + 양쪽 동시 갱신 의무 단락 추가 | `grep "db/init" .claude/rules/db/indexing-and-migration.md` → §4.1 매칭 0건 + "docs/ddl" 매칭 1건 이상 |
| `process/doc-harness/README.md` 의 디렉토리 구조 섹션에 `docs/ddl/` 절 추가 | `grep "docs/ddl" .claude/rules/process/doc-harness/README.md` → 매칭 1건 이상 |
| `./gradlew.bat clean build` 빌드 통과 (SQL 파일 자체는 컴파일 대상 아니지만 자원 검증) | BUILD SUCCESSFUL 출력 확인 |

---

## 도메인 룰 4영역 점검

> 인용 근거: [`backend/.claude/rules/ot-integration.md`](../../../.claude/rules/ot-integration.md)

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 본 사이클은 SQL 파일 21개를 9개 도메인별 합본으로 재번호하는 자산 정리 작업. `alarm_h` 등 알람 관련 테이블 0개 접촉. 임계값·전이 조건·복귀 조건 변경 없음. SQL 컬럼 정의를 그대로 재배치하므로 알람 4단계 정책 영향 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | `pump_interlock_p` 는 pump+AI 백지화 사이클 1 (2026-05-12) 에서 삭제 완료. 대상 21개 SQL 중 인터록 관련 테이블 0개. `ot-integration.md §2` 아웃바운드 절 보류 상태. 인터록 선행조건 검사·기동 차단·복구 후 재검사 의무 영향 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `proc_m`·`ai_drvn_mod_p`·`ai_drvn_mod_h` 는 V9_4 (2026-05-20 신설) 에서 정의되며, 본 사이클에서 V6__proc.sql 로 재배치. 테이블 정의·컬럼·인덱스 내용이 그대로 이동하므로 사용자 의도 (`ai_drvn_mod_cd`)·SCADA 5분 초과 강제 전환 정책 변경 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h` 테이블 구조 (BaseEntity 4 상속, `end_dtm` UPDATE 허용, 부분 UNIQUE 인덱스) 재배치 후에도 동일. `transition_reason` 5종은 현 시점 미도입 상태 — 재배치 대상 컬럼 자체 존재 없음. `pump_ctrl_h` 는 백지화 완료, 재배치 대상 아님 |

> **"비해당" 단독 4건 차단 해제 조건 충족**: (1) 각 행에 구체 사유 명기 (단순 "해당 없음" 한 줄 아님), (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족. `wtp-domain-expert` Round 1 확정.

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

> ROOT [`coding-discipline.md §5.1`](../../../../../.claude/rules/coding-discipline.md) — 본 사이클은 ROOT 어휘 사전 변경 없음 (Glossary 매니저 검토 완료). backend 모듈 룰만 갱신.

- [x] `.claude/rules/process/doc-harness/README.md` — `docs/ddl/` 7번째 카테고리 절 추가 (status frontmatter 미사용 + 도메인 패키지명 1:1 매칭 + governance/REPORT.md 와 동일 라이프사이클)
- [x] `.claude/rules/process/hooks-guide.md` — §1 인덱스 표 + §6.1 대상 조건 + §6.2 매칭 글롭 표기에서 `**/db/init/*.sql` 행 제거
- [x] `.claude/rules/db/indexing-and-migration.md` — §4.1 적용 대상 표기에서 `db/init/` 제거 + §5 (신규) SQL 관리 — 도메인별 단일 파일 + 이중 정책 절 추가 (양쪽 동시 갱신 의무 + V{N} 동결 + patch 분리 + 도메인-V번호 매핑)
- [x] `.claude/hooks/check-ddl-column-comment.sh` — `case "$file_path" in */db/init/*|*/db/migration/*` 의 `*/db/init/*|` 제거 + 헤더 주석·차단 메시지 동기화
- [x] `.claude/agents/wtp-dba-reviewer.md` — §REVIEW 자동 점검 책임의 SQL 트리거 경로 `db/migration/` 단독 정리 (의존 갱신 — 본 검증 단계 발견)
- [x] `.claude/commands/dev/review.md` — §호출 매핑 표의 DB SQL 트리거 경로 `db/init/*.sql` 제거 + `docs/ddl/*.sql` 추가 (의존 갱신 — 본 검증 단계 발견)

> **체크박스 경로 기록 규칙** ([`process/doc-harness/checkbox-rules.md`](../../../.claude/rules/process/doc-harness/checkbox-rules.md)): 모든 경로는 backend 모듈 루트 기준 전체 상대 경로. 축약·글롭 금지.
>
> **승인 절차** ([`process/doc-harness/transitions.md`](../../../.claude/rules/process/doc-harness/transitions.md)): 본 ANALYZE 의 `status: approved` 전환 + `/dev:plan` 자동 전이 요건은 모든 체크박스가 `- [x]` 완료 + 사용자 승인. **단 §가정 및 미해결 질문의 2건이 룰 본문 형식에 직접 영향**하므로 사용자 결정을 본 ANALYZE 검토 시점에 함께 요청하고 그 결정을 룰 갱신에 반영한다.

---

## 산출물

- [계획안](../../../plan/20260520/sql_관리포인트_통합/PLAN1.md) (작성 예정)
