---
status: approved
created: 2026-04-24
updated: 2026-04-24
---
# 룰·스킬 중복 정리 (Tier 1 + Tier 2) — 도메인 분석

## 작업 배경

- 사용자가 `.claude/` 하위(CLAUDE.md · 14개 룰 파일 · 8개 dev 스킬 · 4개 사용자 정의 에이전트 · 2개 훅) 의 중복·집약적 내용을 점검하고 분리 계획을 세우도록 요청
- 본 작업은 **룰·하네스·스킬 자체를 대상으로 하는 메타 작업** 이며, 비즈니스 도메인(pump·alarm·user 등) 코드나 DB 스키마는 대상 외
- 외부 산출물: `~\.claude\plans\claude-md-polymorphic-badger.md` (Plan mode 산출 계획안 — Tier 1+2 범위 확정)

### 현 자산 진단 (Plan mode Phase 1 Explore 3개 에이전트 보고)

| 항목 | 위치 | 진단 |
|------|------|------|
| Fix Cycle 알고리즘 3중 복제 | `commands/dev.md` §2.2 / `commands/dev/analyze.md` §1.3 / `commands/dev/plan.md` §1.3 | 동일 알고리즘이 3곳에 재기술. 한 곳 수정 시 나머지 어긋날 위험 |
| 자동 전이/상태 전이 규칙 9곳 분산 | `commands/dev.md` §7 표 + 각 dev:* 단계 마지막 안내 | 규모별·단계별 전이가 9곳에 흩어짐 |
| YnType / Persistable 패턴 5중 | `entity-patterns.md`(정의) + `api-patterns.md` + `naming.md` + `dict/standard-data-domains.md` + `dict/standard-words.md` | 정의는 1곳이지만 규칙 재설명이 4곳에 흩어짐 |
| "도메인 용어 구분" 경고 4중 | `CLAUDE.md` 각주 + `dict/README.md` + `dict/standard-data-domains.md` 헤더 + `domain-abbreviations.md` 헤더 | 4곳에 동일 경고 형식 반복 |
| PLAN/TASK 템플릿 2중 게시 | `doc-harness.md` §문서 템플릿(정의) + `dev/plan.md` §61~86 + `dev/task.md` §40~58 | 스킬 파일이 템플릿 전문을 복제 |
| db-patterns.md 6대 주제 집약 | `db-patterns.md` §1~§6 | 파티셔닝 · 인덱스 · 무중단 변경 · 격리 · 슬로우 쿼리 · 보존정책이 한 파일에 묶여 있음 |
| test-strategy.md 본문+부록 A 혼재 | `test-strategy.md` 본문 5섹션 + 부록 A 9소섹션(~130줄) | 현재 표준과 미도입 로드맵이 한 파일에 공존 |
| ot-integration.md 5주제 + 기술/비즈니스 혼재 | `ot-integration.md` §1~§5 | OT 미구현 상태에서 상세도 과다, 기술 계층(§4)과 비즈니스 계층(§5) 명시 부재 |
| 커밋 컨벤션 별도 룰 부재 | — | `CLAUDE.md` §커밋 규칙 8줄 외에 별도 룰 파일 없음 |
| 훅 동작 가이드 부재 | — | `check-task-unstage.sh` · `check-errorcode-contract.sh` 의 트리거·차단·우회가 비투명 |

---

## 회의 생략 사유

본 작업은 다음 4가지 이유로 5인 팀 회의를 생략한다 (선례: `docs/analyze/20260423/용어사전_3분리/ANALYZE1.md` 의 동일 패턴 채택).

1. **신규 비즈니스 용어·엔티티가 없다** — 회의 대상인 "신규 도메인 용어·약어·DB 컬럼" 이 본 작업에는 등장하지 않는다. 본 작업은 룰·스킬·하네스 자체의 재배치 (SSOT 강화 + 분리) 이며 도메인 사전 3층(표준 단어 / 표준 데이터 도메인 / 표준 용어) 어느 층위에도 신규 항목이 없다.
2. **메타-결정은 Plan mode 에서 확정** — 본 작업의 핵심 결정(적용 범위 Tier 1+2, 분리 방향, 신규 파일 6개 명세, Phase 순서) 은 사용자가 Plan mode 의 `AskUserQuestion` 으로 직접 선택 완료했으며, 3개 Explore 에이전트가 사전 진단을 보고했다.
3. **회의 안건 자체가 개편 대상에 가깝다** — Fix Cycle 알고리즘·상태 전이 규칙·템플릿 등이 산재하는 현 상황을 정리하는 것이 본 작업의 목적이므로, 산재된 안건 정의로 회의를 진행하면 결론 산출이 비효율적이다.
4. **DB 스키마·도메인 비즈니스 규칙 변경 없음** — `db-patterns.md` 의 3분리는 **물리적 재배치만** 이며 인덱스·파티션·격리 정책의 의미는 그대로다. `ot-integration.md` 의 분리는 보류하고 헤더 태그만 보강한다. 따라서 `wtp-dba-reviewer` · `wtp-domain-expert` 의 안건이 발생하지 않는다.

대신 Plan mode 에서 Explore 3개 에이전트 병렬 조사 결과 (rules/ 매핑 · .claude 스킬 분석 · CLAUDE.md ↔ rules 계층 분석) 와 사용자 확인을 완료했으며, 결정 사항을 이하 §확정 메타-결정 · §룰 갱신 지시서 섹션에 정식 기록한다.

---

## 확정 메타-결정 (Plan mode AskUserQuestion 결과)

| 결정 항목 | 확정안 | 기각된 대안 |
|----------|--------|------------|
| 적용 범위 | **Tier 1+2 (권장)** — 상위 중복 5건 + 집약 과다 3건 + 부재 항목 2건 = 11개 작업, 신규 파일 6개 | Tier 1만 (4~5개 파일 편집), Tier 1+2+3 전체 (14개 파일) |
| Fix Cycle 알고리즘 SSOT | **`doc-harness.md` §수정 사이클 보강** | `rules/fix-cycle-rules.md` 신규 파일 (분리 비용 대비 효익 낮음 — 기존 §수정 사이클 활용) |
| 상태 전이·자동 실행 SSOT | **`doc-harness.md` 신규 §상태 전이·자동 실행** | 별도 파일 (스킬 흐름과 문서 흐름이 같은 doc-harness 우산) |
| YnType / Persistable SSOT | **`entity-patterns.md` 단독 정의 유지** (현 상태), 타 4파일은 링크로 단축 | dict/ 로 이전 (Tier 3 후속 검토) |
| "도메인 용어 구분" SSOT | **`dict/README.md` 단독 정의 유지** (가장 완전한 표 형식), 타 3파일은 링크 1줄 | CLAUDE.md 로 이전 (현 dict/README.md 가 더 적합) |
| db-patterns.md 처리 | **3개 파일로 분리 + 원본은 인덱스 파일로 전환** | 완전 삭제 (CLAUDE.md 인덱스만 갱신 — IMPL Phase 4 ANALYZE 시 옵션 재검토 가능성 명시) |
| test-strategy.md 부록 A 처리 | **별도 파일 분리** (`test-strategy-e2e-roadmap.md`) | 본문 내 `[현재]` `[미도입 로드맵]` 태그 (분리 시 검색·관리 용이성 우선) |
| ot-integration.md 처리 | **분리 보류 + 헤더 `[기술 계층]` `[비즈니스 계층]` 태그 보강** | 즉시 분리 (OT 미구현 상태에서 비용 대비 효익 낮음) |
| 커밋 컨벤션 신규 룰 | **`.claude/rules/commit-convention.md` 신규** | CLAUDE.md 본문 확장 (룰 인덱스 정합성 우선) |
| 훅 가이드 신규 룰 | **`.claude/rules/hooks-guide.md` 신규** | CLAUDE.md "Hooks 인덱스" 섹션 추가 (별도 파일이 향후 훅 추가 시 확장 용이) |

---

## 표준 사전 카탈로그

### 신규 표준 단어
**없음** — 본 작업은 룰·스킬·하네스 자체의 재배치이며 도메인 데이터 모델에 신규 단어가 등장하지 않는다.

### 신규 표준 데이터 도메인
**없음** — 동일 사유. SQL 타입·길이·Java 타입 매핑에 신규 항목 없음.

### 신규 표준 용어
**없음** — 동일 사유. 신규 DB 컬럼명 없음.

> 본 작업은 메타 작업이므로 3층 표준 사전(`.claude/rules/dict/`) 에 등록할 신규 항목이 없다. `wtp-glossary-manager` · `wtp-dba-reviewer` 의 검토 책임 영역(데이터 도메인 2차 승인 등) 도 발동하지 않는다.

---

## 신규 룰·스킬 파일 명세 (표준 사전 대신)

본 작업으로 신설되는 6개 룰 파일의 정합성을 정리한다. 모두 `.claude/rules/` 또는 `.claude/rules/dict/` 컨벤션을 따르며, 명명은 케밥-케이스로 통일 (기존 `entity-patterns.md` · `api-patterns.md` · `db-patterns.md` 와 일관).

| 신규 파일 경로 | 분류 | 분량 (예상) | 출처 | CLAUDE.md 인덱스 행 |
|--------------|------|-----------|------|------------------|
| `.claude/rules/db-partitioning-and-retention.md` | T2-F | ~70줄 | `db-patterns.md` §1 + §6 이동 | DB 운영 패턴 (수명주기) — 시계열 파티션·보존 정책 결정 전 |
| `.claude/rules/db-indexing-and-migration.md` | T2-F | ~80줄 | `db-patterns.md` §2 + §3 이동 | DB 운영 패턴 (DDL) — 인덱스·무중단 변경 설계 전 |
| `.claude/rules/db-query-tuning.md` | T2-F | ~50줄 | `db-patterns.md` §4 + §5 이동 | DB 운영 패턴 (런타임) — 트랜잭션 격리·슬로우 쿼리 분석 전 |
| `.claude/rules/test-strategy-e2e-roadmap.md` | T2-G | ~130줄 | `test-strategy.md` 부록 A 이동 | E2E·Testcontainers 도입 로드맵 — 통합·E2E 격상 검토 전 |
| `.claude/rules/commit-convention.md` | T2-I | ~50줄 | 신규 (CLAUDE.md §커밋 규칙 기반) | 커밋 메시지 컨벤션 — 커밋 작성 전 |
| `.claude/rules/hooks-guide.md` | T2-J | ~60줄 | 신규 (훅 스크립트 실측 기반) | 훅 동작 가이드 — 신규 훅 추가·우회 검토 전 |

**기존 db-patterns.md 처리 옵션** (IMPL Phase 4 ANALYZE 직전 재확정)
- **옵션 1 (현 결정)**: 인덱스 파일로 전환 — 3개 자식 파일 링크 + 1줄 요약만 보유. 외부 참조(`db-patterns.md §X`) 호환성 확보.
- **옵션 2**: 완전 삭제 — CLAUDE.md 인덱스만 갱신. 외부 참조는 모두 새 파일로 갱신 (4개 에이전트 정의 · `test-strategy.md` · `ot-integration.md` · `multi-tenant.md` 동기화 필요).

---

## 기존 사전·패턴과의 충돌 및 해소

본 작업이 기존 룰·코드와 충돌할 수 있는 지점과 해소책을 정리한다. **의미 변경 0**(SSOT 강화·재배치만) 이므로 충돌은 모두 참조 링크 정합성 수준이다.

| 충돌 항목 | 현 상태 | 해소책 |
|----------|--------|-------|
| 기존 ANALYZE 문서들이 `db-patterns.md §1` 등으로 참조 | `용어사전_3분리/ANALYZE1.md`, `harness_개선_1차/ANALYZE1.md` 등 과거 문서가 `db-patterns.md` 직접 참조 | 옵션 1 채택 (인덱스 파일로 전환) — 과거 문서 링크 유지. 옵션 2 채택 시 과거 문서는 그대로 두고 신규 작업부터 새 파일 참조 |
| 4개 사용자 정의 에이전트가 룰 파일 경로 직접 명시 | `wtp-dba-reviewer.md` §1 "파티셔닝 전략 (`.claude/rules/db-patterns.md` 기준)" 등 | IMPL Phase 4 에서 4개 에이전트 정의 동기화 (db-patterns 분리 시) |
| pre-commit 훅의 TASK 체크박스 파싱 규칙 | `check-task-unstage.sh:53~58` 경로 존재성 검증 — 백틱 경로가 실제로 존재해야 함 | 본 작업의 TASK 문서 작성 시 신규 룰 파일 경로를 정확히 기록 (생성 전 단계에서 파일 부재 → 경로 경고 발생 가능, 차단 아님) |
| `wtp-glossary-manager` 가 검토 항목 §5 에서 "DB 컬럼 suffix 규칙 (`.claude/rules/naming.md` 1차 정의)" 명시 | `naming.md` §Java 필드 타입 매핑 의 위치는 본 작업에서 그대로 유지 (Tier 3 후속 검토) | 본 작업에서는 매핑 규칙 재설명만 제거하고 entity-patterns 링크로 단축. 위치 자체는 변동 없음 |
| `commands/dev/plan.md` §5b "ANALYZE 게이트" 가 룰 갱신 지시서 체크박스 검증 | 본 작업의 룰 갱신 지시서 체크박스가 IMPL 단계에서야 완료됨 (메타 작업 특성) | **⚠️ 예외 처리 (선례 동일)**: PLAN approved 전제조건에서 체크박스 완료 면제. 사용자 승인 시 본 예외 함께 승인하는 것으로 간주 |

---

## PLAN 으로 전달할 결정 사항

- **Phase 4 구성** (Plan 파일 §적용 순서 와 동일)
  - Phase 1: SSOT 강화 (T1-D · T1-A · T1-B) — 룰 의미 변경 0, 가장 안전
  - Phase 2: 참조 정리 (T1-C · T1-E · T1-F)
  - Phase 3: 신규 파일 도입 (T2-I · T2-J · T2-G)
  - Phase 4: 분리 (T2-F · T2-H) + 모든 참조 링크 갱신
- **TASK 분할 권장** — Large 작업 분할 기준 (Phase 10 / 체크박스 60) 대비 검토 필요. 현 추정 Phase 4 / 체크박스 ~50 → 단일 TASK 가능성 있으나 Phase 별 책임 분리를 위해 4분할도 후보 (`/dev:task` 단계에서 최종 확정)
- **적용 패턴**
  - SSOT 원칙 (정의 1곳 + 참조 N곳) 강제
  - 정보 손실 0 (이동·재배치만, 의미·정책 변경 없음)
  - 기존 ANALYZE 문서·에이전트 정의·훅 스크립트의 호환성 보장
- **검증 5단계** (Plan 파일 §검증 절차 와 동일)
  - 링크 무결성 → 훅 동작 → 빌드 → /dev 스모크 → 에이전트 정의 점검

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

> **⚠️ 예외 처리 (선례 패턴)**: 본 작업은 **룰·스킬 파일 자체를 대상으로 하는 메타 작업**이다. 일반적으로 ANALYZE 의 룰 갱신 지시서는 PLAN 승인 전에 체크박스를 모두 완료해야 하지만, 이번 작업에서는 체크박스가 가리키는 파일 생성·수정 자체가 IMPL 단계의 Phase 1~4 와 일치한다. 따라서 **PLAN approved 전제조건에서 체크박스 완료를 면제**하고, IMPL 단계에서 해당 파일을 생성·수정하는 것으로 체크박스 완료를 갈음한다. 사용자 승인 시 본 예외 처리를 함께 승인하는 것으로 간주한다 (선례: `docs/analyze/20260423/용어사전_3분리/ANALYZE1.md` 동일 처리).

**Phase 1 — SSOT 강화 (룰 의미 변경 0)**
- [ ] `.claude/rules/dict/README.md` "도메인" 용어 충돌 방지 섹션을 SSOT 로 확정 (변경 없음, 검수만)
- [ ] `CLAUDE.md` "⚠️ '도메인' 용어 구분" 각주를 1줄 링크로 축약 (T1-D)
- [ ] `.claude/rules/dict/standard-data-domains.md` 헤더 ⚠️ 박스를 1줄 링크로 축약 (T1-D)
- [ ] `.claude/rules/domain-abbreviations.md` 헤더 ⚠️ 박스를 1줄 링크로 축약 (T1-D)
- [ ] `.claude/rules/doc-harness.md` §수정 사이클 에 "Fix Cycle 감지 알고리즘 (의사 코드)" 박스 추가 (T1-A)
- [ ] `.claude/commands/dev.md` §2.2 Fix Cycle 감지 로직을 doc-harness 참조로 단축 (T1-A)
- [ ] `.claude/commands/dev/analyze.md` §1.3 Fix Cycle 분기를 doc-harness 참조로 단축 (T1-A)
- [ ] `.claude/commands/dev/plan.md` §1.3 Fix Cycle 분기를 doc-harness 참조로 단축 (§5a 사전 판별은 유지, T1-A)
- [ ] `.claude/rules/doc-harness.md` 말미에 §상태 전이·자동 실행 신규 섹션 추가 (T1-B)
- [ ] `.claude/commands/dev.md` §7 자동 전이 표를 doc-harness 참조로 단축 (T1-B)
- [ ] `.claude/commands/dev/analyze.md` 마지막 전이 안내를 doc-harness 참조로 단축 (T1-B)
- [ ] `.claude/commands/dev/plan.md` 마지막 전이 안내를 doc-harness 참조로 단축 (T1-B)
- [ ] `.claude/commands/dev/task.md` 마지막 전이 안내를 doc-harness 참조로 단축 (T1-B)
- [ ] `.claude/commands/dev/impl.md` 마지막 전이 안내를 doc-harness 참조로 단축 (T1-B)
- [ ] `.claude/commands/dev/result.md` 마지막 전이 안내를 doc-harness 참조로 단축 (T1-B)
- [ ] `.claude/commands/dev/review.md` 마지막 전이 안내를 doc-harness 참조로 단축 (T1-B)
- [ ] `.claude/commands/dev/commit.md` 마지막 전이 안내를 doc-harness 참조로 단축 (T1-B)

**Phase 2 — 참조 정리 (의미 변경 0)**
- [ ] `.claude/rules/naming.md` §Java 필드 타입 매핑 의 YnType·Persistable 줄을 entity-patterns 링크로 단축 (T1-C)
- [ ] `.claude/rules/api-patterns.md` DTO 섹션의 YnType 문단을 entity-patterns 링크로 단축 (T1-C)
- [ ] `.claude/rules/dict/standard-data-domains.md` `DOM_YN` · `DOM_ID_50` 행 비고를 entity-patterns 링크로 단축 (T1-C)
- [ ] `.claude/rules/dict/standard-words.md` `yn` 행 비고를 entity-patterns 링크로 단축 (T1-C)
- [ ] `.claude/commands/dev/plan.md` §계획 문서 작성 의 PLAN 템플릿 코드블록을 doc-harness 참조로 대체 (T1-E)
- [ ] `.claude/commands/dev/task.md` §템플릿 의 TASK 템플릿 코드블록 + TASK 분할 기준을 doc-harness 참조로 대체 (T1-E)
- [ ] `.claude/rules/doc-harness.md` 상단에 "본 파일이 모든 문서 템플릿·상태 전이·Fix Cycle 의 SSOT 임을 명시" 1문단 추가 (T1-E)
- [ ] `CLAUDE.md` §예외 및 응답 규칙 (4줄) 을 2줄 + exception-patterns 위임으로 축약 (T1-F)
- [ ] `CLAUDE.md` §패키지 규칙 (5줄) 을 2줄 + naming/domain-abbreviations 위임으로 축약 (T1-F)
- [ ] `CLAUDE.md` §코드 작성 규칙 에 entity-patterns/api-patterns 링크 추가 (T1-F)
- [ ] `CLAUDE.md` §규칙 문서 인덱스 표에 신규 6행 추가 (commit-convention · hooks-guide · db 분리 3행 · test-strategy-e2e-roadmap, T1-F)

**Phase 3 — 신규 파일 도입 (Tier 2)**
- [ ] `.claude/rules/commit-convention.md` 신규 작성 — feat/fix/refactor/docs/chore/test 정의, 한국어 메시지 정책, 본문/제목/푸터 구조, pre-commit 훅 상호작용 (T2-I)
- [ ] `CLAUDE.md` §커밋 규칙 을 2줄 + commit-convention 위임으로 축약 (T2-I)
- [ ] `.claude/rules/hooks-guide.md` 신규 작성 — 훅 인덱스, 트리거·차단·우회, TASK 체크박스 파싱 1:1 대응표, 신규 훅 추가 절차 (T2-J)
- [ ] `.claude/commands/dev/commit.md` 의 훅 언급을 hooks-guide 참조로 단축 (T2-J)
- [ ] `.claude/rules/test-strategy-e2e-roadmap.md` 신규 작성 — `test-strategy.md` 부록 A (A.1~A.8) 9소섹션 전체 이동 (T2-G)
- [ ] `.claude/rules/test-strategy.md` 본문 §2 의 "Testcontainers 미도입 상태" 안내를 신규 파일 링크로 갱신 + 부록 A 제거 (T2-G)

**Phase 4 — 분리 + 링크 갱신 (Tier 2)**
- [ ] `.claude/rules/db-partitioning-and-retention.md` 신규 작성 — `db-patterns.md` §1 + §6 이동 (T2-F)
- [ ] `.claude/rules/db-indexing-and-migration.md` 신규 작성 — `db-patterns.md` §2 + §3 이동 (T2-F)
- [ ] `.claude/rules/db-query-tuning.md` 신규 작성 — `db-patterns.md` §4 + §5 이동 (T2-F)
- [ ] `.claude/rules/db-patterns.md` 를 인덱스 파일로 전환 — 3개 자식 파일 링크 + 1줄 요약만 보유 (옵션 1 채택, T2-F)
- [ ] `.claude/rules/test-strategy.md` 의 `db-patterns.md §X` 참조 5곳을 새 파일·앵커로 갱신 (T2-F)
- [ ] `.claude/rules/ot-integration.md` 의 `db-patterns.md §X` 참조 2곳을 새 파일·앵커로 갱신 (T2-F)
- [ ] `.claude/rules/multi-tenant.md` 참조 표의 `db-patterns.md` 행을 갱신 (T2-F)
- [ ] `CLAUDE.md` §규칙 문서 인덱스 표의 `db-patterns.md` 행을 3행으로 분리 (T2-F)
- [ ] `.claude/agents/wtp-dba-reviewer.md` §1 의 `db-patterns.md` 참조를 새 파일로 갱신 (T2-F)
- [ ] `.claude/agents/wtp-glossary-manager.md` 검토 항목 §5 의 `naming.md` 참조 검수 (변경 없음 예상, T2-F)
- [ ] `.claude/agents/wtp-backend-engineer.md` 검토 항목의 `entity-patterns.md` · `api-patterns.md` · `exception-patterns.md` 참조 검수 (변경 없음 예상, T2-F)
- [ ] `.claude/agents/wtp-domain-expert.md` 참조 검수 (변경 없음 예상, T2-F)
- [ ] `.claude/rules/ot-integration.md` §1·§2·§3·§4·§5 헤더에 `[기술 계층]` `[데이터 처리 정책]` `[비즈니스 계층]` 태그 추가 + 상단 1문장 추가 (T2-H)

**Phase 5 — 검증** (파일 수정 없음, 실행 검증만)
- [ ] `.claude/` 와 `CLAUDE.md` 내 모든 마크다운 링크 무결성 점검 (Glob + Grep)
- [ ] 임의 TASK 문서로 pre-commit 훅 동작 검증
- [ ] `./gradlew.bat build` 통과 확인 (룰 변경은 코드 무영향이지만 안전 차원)
- [ ] 작은 슬러그(`smoke_test`) 로 /dev 워크플로우 일주 검증 — SSOT 가 정상 참조되는지 확인

---

## 산출물

- [계획안](../../../plan/20260424/rules_dedup_정리/PLAN1.md) — 다음 단계 `/dev:plan rules_dedup_정리` 에서 작성 예정
- 외부 plan 파일: `~\.claude\plans\claude-md-polymorphic-badger.md` (Plan mode 산출, 본 ANALYZE 의 결정 근거)
