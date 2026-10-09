---
status: completed
created: 2026-04-24
updated: 2026-04-24
---
# 룰·스킬 중복 정리 — TASK1-1: SSOT 강화 + 참조 정리 (Tier 1)

## 관련 분할 TASK
- [TASK1-1 SSOT 강화 + 참조 정리](TASK1-1.md)
- [TASK1-2 신규 파일 도입](TASK1-2.md)
- [TASK1-3 분리 + 링크 갱신](TASK1-3.md)
- [TASK1-4 검증](TASK1-4.md)

## 관련 계획
- [계획안](../../../plan/20260424/rules_dedup_정리/PLAN1.md)
- [도메인 분석](../../../analyze/20260424/rules_dedup_정리/ANALYZE1.md)

## Phase

### Phase 1: SSOT 강화 (의미 변경 0)

#### T1-D — "도메인 용어 구분" 경고 SSOT 화 (4건)
- [x] `.claude/rules/dict/README.md` §⚠️ "도메인" 용어 충돌 방지 섹션을 SSOT 로 확정 (변경 없음, 검수만)
- [x] `CLAUDE.md` "⚠️ '도메인' 용어 구분" 각주를 1줄 링크로 축약 — "데이터 도메인 vs 비즈니스 도메인 구분: `.claude/rules/dict/README.md` 참조"
- [x] `.claude/rules/dict/standard-data-domains.md` 헤더 ⚠️ 박스를 1줄 링크로 축약
- [x] `.claude/rules/domain-abbreviations.md` 헤더 ⚠️ 박스를 1줄 링크로 축약

#### T1-A — Fix Cycle 알고리즘 단일화 (4건)
- [x] `.claude/rules/doc-harness.md` §수정 사이클 — 기존 §ANALYZE 조건부 재진입 표 위에 "Fix Cycle 감지 알고리즘 (의사 코드)" 박스 신규 추가 (입력 슬러그·출력 진입단계·REVIEW status·블로커 키워드 분기 기술)
- [x] `.claude/commands/dev.md` §2.2 Fix Cycle 감지 로직을 "doc-harness.md §수정 사이클 참조" 1줄로 단축 + 자동 전이 핸드오프만 남김
- [x] `.claude/commands/dev/analyze.md` §1.3 Fix Cycle 분기를 doc-harness 참조로 단축
- [x] `.claude/commands/dev/plan.md` §1.3 Fix Cycle 분기를 doc-harness 참조로 단축 (§5a 사전 판별은 PLAN 단계 고유 책임이므로 유지)

#### T1-B — 상태 전이·자동 실행 규칙 단일화 (9건)
- [x] `.claude/rules/doc-harness.md` 말미에 §상태 전이·자동 실행 신규 섹션 추가 (표 컬럼: 현재 단계 / 승인 필요 / 다음 단계 / 자동 실행 / Small·Medium·Large 차이)
- [x] `.claude/commands/dev.md` §7 자동 전이 표를 doc-harness 참조 1줄로 단축
- [x] `.claude/commands/dev/analyze.md` 마지막 전이 안내를 doc-harness 참조로 단축
- [x] `.claude/commands/dev/plan.md` 마지막 전이 안내를 doc-harness 참조로 단축
- [x] `.claude/commands/dev/task.md` 마지막 전이 안내를 doc-harness 참조로 단축
- [x] `.claude/commands/dev/impl.md` 마지막 전이 안내를 doc-harness 참조로 단축
- [x] `.claude/commands/dev/result.md` 마지막 전이 안내를 doc-harness 참조로 단축
- [x] `.claude/commands/dev/review.md` 마지막 전이 안내를 doc-harness 참조로 단축
- [x] `.claude/commands/dev/commit.md` 마지막 전이 안내를 doc-harness 참조로 단축

### Phase 2: 참조 정리 (의미 변경 0)

#### T1-C — YnType / Persistable 패턴 참조 단순화 (4건)
- [x] `.claude/rules/naming.md` §Java 필드 타입 매핑 의 YnType·Persistable 줄을 한 표로 통합하고 entity-patterns 링크만 유지 (재설명 제거)
- [x] `.claude/rules/api-patterns.md` DTO 섹션의 YnType 문단을 "DTO 의 `*Yn` 필드도 YnType 사용. 패턴: entity-patterns.md §여부(Y/N)" 1줄로 단축
- [x] `.claude/rules/dict/standard-data-domains.md` `DOM_YN` · `DOM_ID_50` 행 비고를 entity-patterns 링크 1줄로 단축
- [x] `.claude/rules/dict/standard-words.md` `yn` 행 비고를 entity-patterns 링크 1줄로 단축

#### T1-E — PLAN / TASK 템플릿 단일 게시 (3건)
- [x] `.claude/commands/dev/plan.md` §계획 문서 작성 의 PLAN 템플릿 코드 블록을 "PLAN 템플릿: doc-harness.md §PLAN{n}.md" 참조로 대체
- [x] `.claude/commands/dev/task.md` §템플릿 의 TASK 템플릿 코드 블록 + TASK 분할 기준을 doc-harness 참조로 단축
- [x] `.claude/rules/doc-harness.md` 상단에 "본 파일이 모든 문서 템플릿·상태 전이·Fix Cycle 의 SSOT 임을 명시" 1문단 추가

#### T1-F — CLAUDE.md 축약 + 인덱스 보강 (4건)
- [x] `CLAUDE.md` §예외 및 응답 규칙 (4줄) 을 2줄 + "상세: `.claude/rules/exception-patterns.md`" 위임으로 축약
- [x] `CLAUDE.md` §패키지 규칙 (5줄) 을 2줄 + "클래스/필드/DB 컬럼 명명 상세: `.claude/rules/naming.md`, 비즈니스 도메인 약어: `.claude/rules/domain-abbreviations.md`" 위임으로 축약
- [x] `CLAUDE.md` §코드 작성 규칙 의 Lombok·Javadoc·Swagger 줄에 "패턴 상세: entity-patterns.md / api-patterns.md" 링크 추가
- [x] `CLAUDE.md` §규칙 문서 인덱스 표에 신규 6행 추가 (commit-convention · hooks-guide · db 분리 3행 · test-strategy-e2e-roadmap)

## 산출물
- [결과](../../../results/20260424/rules_dedup_정리/RESULT1.md)
