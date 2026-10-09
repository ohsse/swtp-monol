---
status: completed
created: 2026-04-24
updated: 2026-04-24
---
# 룰·스킬 중복 정리 — 결과 정리

## 관련 작업
- [도메인 분석](../../../analyze/20260424/rules_dedup_정리/ANALYZE1.md)
- [계획안](../../../plan/20260424/rules_dedup_정리/PLAN1.md)
- [태스크 1-1: SSOT 강화 + 참조 정리](../../../tasks/20260424/rules_dedup_정리/TASK1-1.md)
- [태스크 1-2: 신규 파일 도입](../../../tasks/20260424/rules_dedup_정리/TASK1-2.md)
- [태스크 1-3: 분리 + 링크 갱신](../../../tasks/20260424/rules_dedup_정리/TASK1-3.md)
- [태스크 1-4: 검증](../../../tasks/20260424/rules_dedup_정리/TASK1-4.md)

## 작업 요약

스마트정수장 백엔드의 `.claude/` 자산(CLAUDE.md · 룰 14개 · dev 스킬 8개 · 사용자 정의 에이전트 4개 · 훅 2개) 에 산재하던 동일 규칙을 **단일 소스 진실(SSOT)** 원칙으로 재정렬하고, 집약 과다 파일을 주제별로 분리하여 변경 동기화 비용을 낮춘 메타 정비 작업이다.

**재정렬 대상 SSOT 5건 (Tier 1)**
- T1-A Fix Cycle 감지 알고리즘 — `dev.md`/`dev:analyze.md`/`dev:plan.md` 3곳 → `doc-harness.md §수정 사이클` 단일 정의 + 의사 코드 박스 신규
- T1-B 상태 전이·자동 실행 규칙 — 9곳(`dev.md §7` + 7개 단계 마지막 전이) → `doc-harness.md §상태 전이·자동 실행` 신규 표
- T1-C YnType / Persistable 패턴 — 5곳 재설명 → `entity-patterns.md` 단독 정의 + 4곳 참조로 단축
- T1-D "도메인" 용어 구분 — 4곳 ⚠️ 안내 → `dict/README.md` SSOT + 3곳 1줄 링크
- T1-E PLAN/TASK 템플릿 — 2중 게시 → `doc-harness.md §문서 템플릿` 단일 게시
- T1-F CLAUDE.md 축약 + 인덱스 보강 — 예외/패키지/커밋 규칙을 위임 형태로 압축

**신규/분리 파일 7건 (Tier 2)**
- T2-F `db-patterns.md` 6대 주제 → 3개 파일 분리 (수명주기 / DDL / 런타임) + 인덱스 파일 전환
- T2-G `test-strategy.md` 부록 A 130줄 → `test-strategy-e2e-roadmap.md` 로 분리
- T2-H `ot-integration.md` 헤더에 `[기술 계층]` / `[비즈니스 계층]` 태그 보강 (분리 보류)
- T2-I `commit-convention.md` 신규 — CLAUDE.md §커밋 규칙 8줄 → 95줄 상세 가이드
- T2-J `hooks-guide.md` 신규 — 훅 스크립트 상단 주석에 흩어져 있던 정책을 통합

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 5 (Phase 1~5: SSOT 강화 / 참조 정리 / 신규 파일 도입 / 분리 + 링크 갱신 / 검증) |
| 체크박스 수 | 52 (TASK1-1: 28 + TASK1-2: 6 + TASK1-3: 14 + TASK1-4: 4) |
| 분할 여부 | Y |
| 분할 근거 | doc-harness.md §TASK 분할 기준 3번째 조건 충족 — "데이터 계층·애플리케이션 계층·테스트 등 계층 경계가 명확히 구분됨". Phase 5개·체크박스 52개로 1·2번째 양적 기준(Phase 10 / 체크박스 60) 은 미달이나, PLAN1.md §"적용 순서" 의 4단계 의존성(SSOT 강화 → 참조 정리 → 신규 파일 도입 → 분리·링크 갱신 → 검증) 이 분할 단위로 명확. 단일 TASK 시 컨텍스트 부하 큼 |

## 변경 사항

### 신규 룰 파일 (6건, 총 ~602줄)

| 파일 | TASK | 분량 | 1차 정의 (SSOT) |
|------|------|------|----------------|
| `.claude/rules/commit-convention.md` | T2-I | 95줄 | 메시지 구조·타입 정의·한국어 정책·브레이킹 체인지 보류·pre-commit 훅 상호작용 |
| `.claude/rules/hooks-guide.md` | T2-J | 133줄 | 훅 인덱스·종료 코드·TASK 체크박스 파싱 규칙·우회·신규 훅 추가 절차 |
| `.claude/rules/test-strategy-e2e-roadmap.md` | T2-G | 151줄 | Testcontainers · E2E · 슬라이스 어노테이션 도입 로드맵 (현 시점 미도입) |
| `.claude/rules/db-partitioning-and-retention.md` | T2-F | 70줄 | 시계열 파티셔닝 + 데이터 보존 정책 (구 db-patterns.md §1·§6) |
| `.claude/rules/db-indexing-and-migration.md` | T2-F | 91줄 | 인덱스 원칙 + 무중단 변경 (구 db-patterns.md §2·§3) |
| `.claude/rules/db-query-tuning.md` | T2-F | 62줄 | 트랜잭션 격리 + 슬로우 쿼리 분석 (구 db-patterns.md §4·§5) |

### 수정 파일 (21건)

| 파일 | TASK | 변경 요약 |
|------|------|---------|
| `CLAUDE.md` | T1-D·T1-F·T2-F·T2-I·T2-J·T2-G | ⚠️ 도메인 1줄 단축 / 인덱스 표 db 분리 3행 + 신규 룰 4행 추가 + db-patterns "분리 인덱스" 라벨 / 커밋 규칙 2줄 위임 |
| `.claude/rules/db-patterns.md` | T2-F | 174줄 → 47줄 인덱스 파일 전환 (구 §1~§6 외부 참조 호환성용 + 자식 3개 매핑 표) |
| `.claude/rules/test-strategy.md` | T2-G·T2-F | 부록 A 130줄 제거 → §6 "향후 도입 로드맵" 1단락 위임 / db-patterns §1·§5 참조 4곳 갱신 / 462→335줄 (-127) |
| `.claude/rules/doc-harness.md` | T1-A·T1-B·T1-E | Fix Cycle 감지 알고리즘 의사 코드 박스 신규 / §상태 전이·자동 실행 표 신규 / SSOT 선언 헤더 추가 |
| `.claude/rules/ot-integration.md` | T2-H·T2-F | 5개 §헤더에 계층 태그 + 참조 표 아래 1문장 / db-patterns 참조 3곳 갱신 (참조표 (§3) 오타도 함께 수정) |
| `.claude/rules/naming.md` | T1-C | YnType 매핑 표를 entity-patterns.md 링크 형태로 단축 |
| `.claude/rules/api-patterns.md` | T1-C | DTO YnType 문단 → entity-patterns.md 참조로 단축 |
| `.claude/rules/dict/standard-data-domains.md` | T1-C·T1-D·T2-F | DOM_YN/DOM_ID_50 비고 단축 / ⚠️ 1줄 / db-patterns 참조 갱신 |
| `.claude/rules/dict/standard-words.md` | T1-C | yn 행 비고 단축 |
| `.claude/rules/domain-abbreviations.md` | T1-D | ⚠️ 도메인 안내 1줄 |
| `.claude/rules/multi-tenant.md` | T2-F | db-patterns 행을 "분리 인덱스" 라벨로 갱신 |
| `.claude/agents/wtp-dba-reviewer.md` | T2-F | §1 헤더 db-patterns.md → db-partitioning-and-retention.md |
| `.claude/commands/dev.md` | T1-A·T1-B | Fix Cycle 알고리즘 본문 → doc-harness 참조 1줄 / §7 자동 전이 표 → doc-harness 참조 |
| `.claude/commands/dev/analyze.md` | T1-A·T1-B | Fix Cycle 참조 단일화 / 마지막 전이 → doc-harness 참조 |
| `.claude/commands/dev/plan.md` | T1-A·T1-B·T1-E | Fix Cycle 참조 / PLAN 템플릿 코드블록 → doc-harness 참조 / 자동 전이 단일화 |
| `.claude/commands/dev/task.md` | T1-B·T1-E | TASK 템플릿 + Phase 분할 기준 → doc-harness 참조 / 자동 전이 단일화 |
| `.claude/commands/dev/impl.md` | T1-B | 자동 전이 → doc-harness 참조 |
| `.claude/commands/dev/result.md` | T1-B | 자동 전이 → doc-harness 참조 |
| `.claude/commands/dev/review.md` | T1-B | 자동 전이 → doc-harness 참조 |
| `.claude/commands/dev/commit.md` | T1-B·T2-J | 미완료 작업 파일 교차 검증 23줄 → hooks-guide §3 참조 8줄 / 자동 전이 → doc-harness 참조 |
| `.claude/settings.local.json` | (작업 무관) | 사전 변경 — 본 작업 스코프 외 |

### 신규 문서 (6건)

| 파일 | 역할 |
|------|------|
| `docs/analyze/20260424/rules_dedup_정리/ANALYZE1.md` | 5인 팀 회의록 + 신규 파일 명세 + 룰 갱신 지시서 |
| `docs/plan/20260424/rules_dedup_정리/PLAN1.md` | Tier 1·2 작업 항목 + 4단계 의존성 + 영향 평가 |
| `docs/tasks/20260424/rules_dedup_정리/TASK1-1.md` | Phase 1·2 SSOT + 참조 정리 (28 체크박스) |
| `docs/tasks/20260424/rules_dedup_정리/TASK1-2.md` | Phase 3 신규 파일 도입 (6 체크박스) |
| `docs/tasks/20260424/rules_dedup_정리/TASK1-3.md` | Phase 4 분리 + 링크 갱신 (14 체크박스) |
| `docs/tasks/20260424/rules_dedup_정리/TASK1-4.md` | Phase 5 검증 (4 체크박스) |

### 통계

- **수정**: 21 파일, 196 insertions / 494 deletions (룰 본문 줄수 순감 ≈ −298)
- **신규 룰**: 6 파일, ~602줄 (의도된 증가 — SSOT 분산 감소 후 단일 게시)
- **신규 문서**: 6 파일 (ANALYZE/PLAN/TASK 4개)
- **코드(.java/.yml/.gradle) 변경**: 0 — 본 작업은 메타 룰 재정렬에 한정

## 테스트 결과

| 검증 | 결과 |
|------|------|
| `./gradlew.bat build` 회귀 점검 | **exit 0 빌드 성공** (룰 변경 코드 무영향 확인, QClass 재생성·클래스패스 정합성 OK) |
| 링크 무결성 점검 (.claude/ + CLAUDE.md) | **0건 깨짐** — 정밀 정규식 `]\(...\.md...\)` + placeholder 제외 검증 |
| pre-commit 훅 동작 (`check-task-unstage.sh`) | bash 구문 OK / 비-git 명령 stdin → exit 0 정상 통과 / TASK 파싱 정확 (미완료 1건 인식·경로 경고 0건) |
| pre-commit 훅 (`check-errorcode-contract.sh`) | bash 구문 OK |
| SSOT #1 — doc-harness §상태 전이 → 7개 단계 (analyze/plan/task/impl/result/review/commit) | **모두 OK** |
| SSOT #2 — doc-harness §수정 사이클 (Fix Cycle) → dev.md / dev:analyze.md / dev:plan.md | **모두 OK** |
| SSOT #3 — doc-harness PLAN/TASK 템플릿 단일 게시 → dev:plan.md / dev:task.md | **모두 OK** |
| SSOT #4 — dict/README ⚠️ 도메인 용어 → 의도 4곳 (CLAUDE.md / dict/standard-data-domains.md / domain-abbreviations.md / dict/README.md SSOT) | **모두 OK** |
| SSOT #5 — 신규 룰 6개 (commit-convention/hooks-guide/db 분리 3개/test-strategy-e2e-roadmap) → CLAUDE.md 인덱스 등록 | **모두 OK** |
| SSOT #6 — db-patterns.md 인덱스 → 자식 3개 모두 링크 + 구 §1~§6 매핑 표 | **모두 OK** |

## 비고

### 계획 대비 차이점 (실측 기반 보완)

1. **파생 참조 동시 갱신** — PLAN/TASK 가 명시한 5건의 외부 참조(test-strategy.md / ot-integration.md / multi-tenant.md / CLAUDE.md / wtp-dba-reviewer.md) 외에, grep 으로 발견된 2곳을 함께 갱신:
   - `.claude/rules/dict/standard-data-domains.md:30` (DOM_QTY_15_4 비고의 db-patterns §1 링크)
   - `.claude/rules/test-strategy-e2e-roadmap.md:74` (시계열 픽스처 §5 의 db-patterns §1 링크)
   → 분리 정합성 완전 유지를 위해 스코프 보강.

2. **ot-integration.md 참조 표 (§3) 오타 동시 수정** — 본래 "수집 데이터 보존 기간·파티션 정책 (§3)" 으로 적혀 있던 db-patterns 참조 행에서 `(§3)` 은 db-patterns.md §3 (스키마 무중단 변경) 을 가리켜 의미 불일치였음. 분리 작업 중 자연 정정 → `db-partitioning-and-retention.md (§1·§2)` 로 갱신.

3. **multi-tenant.md / CLAUDE.md 의 db-patterns 행 처리 방식** — TASK 가 "새 파일로 갱신" 명시했으나 멀티테넌트 컨텍스트에서 db 자식 3개 모두 균등 적용되므로 인덱스 파일 유지 + "분리 인덱스" 라벨 + 자식 3개 안내 표기로 정리. CLAUDE.md 의 db-patterns.md 행은 "분리 인덱스 — 아래 3개 자식 파일 진입점" 으로 설명 갱신.

4. **에이전트 정의 검수 결과 변경 0건** — wtp-glossary-manager.md / wtp-backend-engineer.md / wtp-domain-expert.md 모두 db-patterns 미참조 → 갱신 불요. ANALYZE 의 "4개 사용자 정의 에이전트가 룰 파일 경로 직접 명시" 우려 항목은 wtp-dba-reviewer.md §1 만 해당하여 정확히 1건 갱신.

### 후속 작업 (Tier 3 — 별도 PLAN 으로 미룸)

본 작업의 명시된 비목표 그대로 후속 PLAN 으로 이월 (REVIEW 권고 반영분 2건은 본 사이클에서 즉시 보완 — 후속 작업에서 제거):

- `exception-patterns.md` 가 CLAUDE.md 인덱스 표에 미등록 — 추가 검토
- 모든 룰 파일에 "참조 문서 관계" 표 일관 추가 (현재 일부 파일만 보유)
- `wtp-*` 4개 에이전트의 검토 항목 텍스트를 룰 파일 자동 첨부로 대체 검토
- `multi-tenant.md §4` (프로파일 분기/Bean/별도 모듈 판정) 의 별도 파일 분리 가능성
- `dict/standard-words.md` vs `domain-abbreviations.md` 경계 재정리
- `naming.md §Java 필드 타입 매핑` 의 `dict/` 로 이전 가능성 재평가
- `dev:analyze.md §1.3` 의 Fix Cycle 분기 외 "사전 판별 분기 §5a" 추가 단순화

### 핵심 성과

- **단일 소스 진실(SSOT) 6건 정착** — Fix Cycle / 자동 전이 / PLAN·TASK 템플릿 / 도메인 용어 / db-patterns 인덱스 / dict 3층 구조
- **변경 동기화 비용 감소** — 동일 규칙 1곳 정의 + N곳 참조 패턴으로 통일 → 한 곳만 고치면 자동 정합
- **집약 과다 파일 해소** — db-patterns.md (174→47, 자식 3개 분리) / test-strategy.md (462→335, 부록 A 분리)
- **부재 룰 보완** — 커밋 컨벤션 / 훅 동작 가이드 단일 룰 파일 신설 (그동안 CLAUDE.md 8줄 + 훅 스크립트 상단 주석에만 흩어짐)
- **외부 참조 호환성 유지** — db-patterns.md 인덱스 파일 옵션 채택으로 과거 ANALYZE/PLAN/REVIEW 의 `db-patterns.md §X` 호출 호환
