---
status: completed
created: 2026-04-24
updated: 2026-04-24
---
# 룰 후속 정비 묶음 B — 작업

## 관련 계획
- [계획안](../../../plan/20260424/rules_후속정비_b/PLAN1.md)

## 규모 판단
- Phase 3개 / 체크박스 13개 → `doc-harness.md §TASK 분할 기준` 의 분할 조건(Phase 10 이상 / 체크박스 60 이상 / 계층 경계 명확) **모두 미달**
- **분할 없음** — 단일 TASK1.md 로 관리

## Phase

### Phase 1: 룰 7개에 "참조 문서 관계" 표 추가

각 파일 최상단 (제목 + 한 줄 소개 + `---` 직후, §1 앞) 에 2열 양식 표를 삽입한다. 관계 설명 문장은 각 파일 본문에 이미 명시된 참조 맥락을 재사용하여 작성한다.

- [x] `.claude/rules/api-patterns.md` "참조 문서 관계" 표 신규 추가 (entity-patterns / exception-patterns / naming / dict/standard-data-domains)
- [x] `.claude/rules/entity-patterns.md` "참조 문서 관계" 표 신규 추가 (dict/standard-data-domains / naming / api-patterns / domain-abbreviations)
- [x] `.claude/rules/naming.md` "참조 문서 관계" 표 신규 추가 (dict/standard-words / dict/standard-data-domains / domain-abbreviations / entity-patterns)
- [x] `.claude/rules/exception-patterns.md` "참조 문서 관계" 표 신규 추가 (hooks-guide / api-patterns / naming)
- [x] `.claude/rules/commit-convention.md` "참조 문서 관계" 표 신규 추가 (hooks-guide / doc-harness)
- [x] `.claude/rules/hooks-guide.md` "참조 문서 관계" 표 신규 추가 (commit-convention / exception-patterns / doc-harness)
- [x] `.claude/rules/test-strategy-e2e-roadmap.md` "참조 문서 관계" 표 신규 추가 (test-strategy / db-partitioning-and-retention / ot-integration)

### Phase 2: 에이전트 3개 본문 단순화

각 에이전트를 4블록 구조(역할 + 필수 파일 읽기 + 검토 항목(링크만) + 출력 형식(유지))로 재작성한다. frontmatter 에 `tools: [Read, Grep]` 명시 추가.

- [x] `.claude/agents/wtp-backend-engineer.md` 4블록 재작성 + Read 지시문 (api-patterns / entity-patterns / exception-patterns / naming) + tools 필드 추가 (목표 30~35행)
- [x] `.claude/agents/wtp-dba-reviewer.md` 4블록 재작성 + Read 지시문 (db-partitioning-and-retention / db-indexing-and-migration / db-query-tuning / dict/standard-data-domains) + tools 필드 추가 (목표 28~32행)
- [x] `.claude/agents/wtp-glossary-manager.md` 4블록 재작성 + Read 지시문 (dict/README / dict/standard-words / dict/standard-data-domains / dict/standard-terms / domain-abbreviations / naming / ot-integration / multi-tenant) + tools 필드 추가 (목표 35~40행)

### Phase 3: 검증

- [x] Phase 1 수정 파일 7개의 "참조 문서 관계" 표 링크 유효성 grep 확인 (총 약 25개 링크)
- [x] Phase 2 수정 에이전트 3개 frontmatter 의 Read/Grep 도구 필드 포함 확인
- [x] `./gradlew.bat build` 실행 성공 확인 (회귀 검증, 참고용)

## 산출물
- 결과 문서: Medium 규모 면제 (RESULT/REVIEW 미작성)
