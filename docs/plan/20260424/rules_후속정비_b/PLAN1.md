---
status: approved
created: 2026-04-24
updated: 2026-04-24
---
# 룰 후속 정비 묶음 B — 계획

## 목적

- `.claude/rules/` 하위 7개 룰 파일에 "## 참조 문서 관계" 표를 일관 양식으로 추가하여 룰 간 의존도·관계를 명시적으로 표현한다 (제안 2)
- `wtp-*` 3개 에이전트 정의 본문을 룰 파일 재기술에서 참조 지시문 기반으로 단순화하여 룰 변경 시 에이전트 동기화 유지보수 부담을 제거한다 (제안 3)

## 배경

- [이전 결과](../../../results/20260424/rules_dedup_정리/RESULT1.md) REVIEW1 (`docs/reviews/20260424/rules_dedup_정리/REVIEW1.md`) 개선 제안 7건 중 묶음 B 로 이월된 2건
- 직전 묶음 A 커밋: `96649f1 chore: 룰 후속 정비 — CLAUDE.md 인덱스 2건 · naming.md 표 삭제 · dev:plan SSOT 위반 해소`
- [분석](../../../analyze/20260424/rules_후속정비_b/ANALYZE1.md) 에서 결정된 양식·대상·단순화 전략을 본 PLAN 이 구현 계획으로 변환

## 범위

### 수정 대상 (10개 파일)

**룰 파일 7개** — "## 참조 문서 관계" 표 신규 추가:
- `.claude/rules/api-patterns.md`
- `.claude/rules/entity-patterns.md`
- `.claude/rules/naming.md`
- `.claude/rules/exception-patterns.md`
- `.claude/rules/commit-convention.md`
- `.claude/rules/hooks-guide.md`
- `.claude/rules/test-strategy-e2e-roadmap.md`

**에이전트 정의 3개** — 본문 단순화:
- `.claude/agents/wtp-backend-engineer.md` (81행 → 30~35행)
- `.claude/agents/wtp-dba-reviewer.md` (76행 → 28~32행)
- `.claude/agents/wtp-glossary-manager.md` (117행 → 35~40행)

### 제외 대상 (명시)

- `.claude/agents/wtp-domain-expert.md` (56행) — 알람 4단계·인터록·운전 모드는 룰 파일에 1차 정의가 없어 에이전트 본문이 유일한 출처. 단순화 시 정보 손실 발생 → **제외**
- `.claude/rules/db-patterns.md` — 이미 인덱스 페이지 (분리 후 자식 3개 진입점). "참조 문서 관계" 표 대신 §자식 문서 표가 그 역할 → **제외**
- `.claude/rules/doc-harness.md` — SSOT (모든 룰이 참조). "참조 문서 관계" 표를 추가하면 거의 모든 룰 파일을 열거하게 되어 정보 이득 낮음 → **제외**
- `.claude/rules/domain-abbreviations.md` · `dict/README.md` · `dict/standard-*.md` 3개 — 자체가 사전/인덱스이며 "다른 사전과의 관계" 표를 이미 보유 (또는 README 가 그 역할) → **제외**

## 구현 방향

### Phase 1 — 룰 7개에 "참조 문서 관계" 표 추가

각 파일 상단에 다음 양식을 **동일하게** 삽입한다 (표 양식·위치는 ANALYZE1 §PLAN 전달사항 §1 결정 고정):

```markdown
# {현행 제목}

{현행 한 줄 소개}

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`xxx.md`](xxx.md) | 관계 설명 한 줄 |
| ... | ... |

---

## {기존 §1 제목}
```

파일별 초안 관계는 ANALYZE1 §PLAN 전달사항 §1 표 참조. 실제 관계 설명 문장은 **구현 시 작성**하되, 이미 본문에 명시된 참조 맥락을 재사용한다 (예: api-patterns.md 본문에 "entity-patterns.md §여부(Y/N) 필드 패턴" 이라는 링크가 있으면 관계 설명을 "DTO `*Yn` 필드 타입 매핑 — `YnType` enum 규약 수신처" 로 축약).

### Phase 2 — 에이전트 3개 본문 단순화

각 에이전트를 4블록 구조로 재작성 (ANALYZE1 §PLAN 전달사항 §2 결정 고정):

```markdown
---
name: {현행 name}
description: {현행 description}
model: {현행 model}
tools: [Read, Grep]
---

# {현행 제목}

## 역할

{현행 역할 경계 선언 유지 — 다른 에이전트와의 책임 분리}

## 검토 시작 전 필수 파일 읽기

검토 요청을 받으면 답변 전에 다음 파일을 Read 도구로 순서대로 읽는다. **읽지 않은 상태에서 판정하지 않는다.**

1. `.claude/rules/xxx.md`
2. `.claude/rules/yyy.md`
...

## 검토 항목

### 1. {항목 제목 — 1줄 요약}
- 기준: [`.claude/rules/xxx.md §섹션`](../rules/xxx.md)

### 2. {...}
- 기준: [`.claude/rules/yyy.md §섹션`](../rules/yyy.md)

...

## 출력 형식

{현행 출력 형식 템플릿 — 심각도 표·결론 카운트 그대로 유지}
```

에이전트별 필수 Read 파일 목록:
- `wtp-backend-engineer.md`: api-patterns.md · entity-patterns.md · exception-patterns.md · naming.md
- `wtp-dba-reviewer.md`: db-partitioning-and-retention.md · db-indexing-and-migration.md · db-query-tuning.md · dict/standard-data-domains.md
- `wtp-glossary-manager.md`: dict/README.md · dict/standard-words.md · dict/standard-data-domains.md · dict/standard-terms.md · domain-abbreviations.md · naming.md · ot-integration.md · multi-tenant.md

### Phase 3 — 검증

- 각 룰 파일의 "참조 문서 관계" 표 링크 유효성 grep 으로 확인 (7개 파일 × 평균 3~4개 링크)
- 각 에이전트 frontmatter `tools: [Read, Grep]` 추가 확인
- `./gradlew.bat build` 실행 — 회귀 검증 (코드 변경 0건이므로 참고용)
- pre-commit 훅 (`check-task-unstage.sh` · `check-errorcode-contract.sh`) 영향 없음 확인 — 룰/에이전트 정의 파일은 훅 대상 아님

## 테스트 전략

- **코드 변경 0건** — JUnit 테스트 신규 작성·수정 없음
- `./gradlew.bat build` 회귀 검증만 수행 (참고용)
- 영향받는 모듈: 없음 (공통/api/scheduler 모두 영향 없음)

## 제외 사항

- 제안 4 (multi-tenant.md §4 분리) — REVIEW1 검토 결과 "보류" 권장
- 제안 5 (standard-words vs domain-abbreviations 경계 재정리) — 이미 경계 명확
- `wtp-domain-expert` 에이전트 단순화 — 상기 §범위 §제외 대상 참조
- 신규 룰 파일 생성·삭제 — 본 작업은 기존 파일 내부 구조 정비에 한정
- 에이전트 출력 형식(심각도 표·결론 카운트) 변경 — ANALYZE 회의록 자동 판정 계약이므로 유지

## 도메인·DB 검토 게이트 생략

본 작업은 다음 조건 모두 해당:
- `## 도메인 모델` 섹션 내용 없음 (신규 엔티티·테이블·필드 0건)
- `## DB 설계 변경` 섹션 내용 없음 (스키마 변경 0건)

`dev:plan.md §도메인·DB 검토 게이트` 규칙에 따라 **검토 게이트 생략**.

## 예상 산출물

- [태스크](../../../tasks/20260424/rules_후속정비_b/TASK1.md) — Phase 2개 + 검증 Phase 1개로 분할, 분할 여부는 TASK 단계에서 최종 결정
