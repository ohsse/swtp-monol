---
status: approved
created: 2026-04-23
updated: 2026-04-23
---
# 분석 단계 도입 — Fix Cycle 2 (PLAN 게이트 Small 작업 오진단 + 표현 일관성 sweep)

## 목적

REVIEW1 의 블로커 1건과 권고 2건을 한 sweep 으로 해소한다.
구체적으로 `dev/plan.md` 의 ANALYZE 게이트가 작업 규모를 판별하지 못해 발생할 수 있는 오차단 케이스를 막고,
같은 sweep 으로 doc-harness.md 의 표현 일관성 권고 2건(디렉토리 예시 누락, 플레이스홀더 혼재)을 정리한다.

## 배경

- [이전 리뷰](../../../reviews/20260423/analyze_phase_도입/REVIEW1.md) 블로커 1건 해소
- 직전 PLAN: [PLAN1](PLAN1.md)

REVIEW1 의 발견 사항 6건 중 본 사이클이 처리하는 항목:

| 심각도 | 항목 | 처리 방식 |
|--------|------|----------|
| 높음 (블로커) | PLAN 게이트의 Small 작업 오진단 가능성 (`plan.md §5`) | plan.md §5 앞에 분기 A (Small 보호) 추가 |
| 중간 (권고) | Fix Cycle 진입 시 plan.md 의 ANALYZE 키워드 분기 미수행 | plan.md §5 앞에 분기 B (Fix Cycle 키워드 미포함 시 스킵) 추가 — 블로커 1과 같은 sweep |
| 중간 (권고) | doc-harness.md 디렉토리 예시 블록에 `analyze/` 누락 | 예시 블록 맨 앞에 `docs/analyze/...` 한 줄 추가 |
| 중간 (권고) | ANALYZE 템플릿 산출물 링크 플레이스홀더 표기 불일치 | `{슬러그}` → `작업목적` 통일 |

REVIEW1 의 참고 항목 2건 (CLAUDE.md 단계 나열·약어 사전 표현 강도) 과 후속 작업 트래킹 5건은 본 사이클에서 처리하지 않는다 (REVIEW1 §개선 제안 §3 명시).

## 범위

### 포함 (4개 항목)
- `.claude/commands/dev/plan.md` §전제조건 §5 의 ANALYZE 게이트 앞에 분기 A·B 추가
- `.claude/rules/doc-harness.md` 디렉토리 예시 블록에 `docs/analyze/...` 라인 추가
- `.claude/rules/doc-harness.md` ANALYZE 템플릿의 산출물 링크 플레이스홀더 통일

### 제외
- 참고 사항 2건 (낮음) — 본 사이클 미반영
- 후속 작업 트래킹 5건 — 별도 사이클로 분리 (REVIEW1 후속 작업 트래킹 표 참조)

## 구현 방향

### 1. 신규/변경 파일 목록

| 파일 | 액션 | 목적 |
|------|------|------|
| `.claude/commands/dev/plan.md` | 수정 | §5 ANALYZE 게이트 앞에 두 분기(A: Small 보호 / B: Fix Cycle 키워드 미포함 스킵) 추가 |
| `.claude/rules/doc-harness.md` | 수정 (2항목) | (1) 디렉토리 예시 블록 맨 앞에 `docs/analyze/...` 추가 (2) ANALYZE{n}.md 템플릿 §산출물 링크 `{슬러그}` → `작업목적` 통일 |

### 2. plan.md §5 분기 설계

기존 §5 (ANALYZE 게이트 검증) 본문 시작 직전에 다음 분기를 명시한다:

```markdown
5. **ANALYZE 게이트 검증** (Medium/Large 작업의 필수 전제조건):

   **5a. 작업 규모 사전 판별 (게이트 스킵 분기):**
   - `docs/analyze/` 하위에서 슬러그 일치 디렉토리 탐색
   - 디렉토리가 없고 같은 슬러그의 ANALYZE 이력도 없으면 → Small 작업으로 간주하고 본 §5 게이트 스킵 (정상 자동 전이 흐름은 항상 `/dev:analyze` 가 선행되므로 디렉토리가 존재함. 본 분기는 사용자 수동 호출/엣지 케이스 보호용)
   - Fix Cycle 모드(직전 REVIEW status: draft + 블로커 있음)이고 블로커 텍스트에 도메인 정합성 키워드(`용어`, `약어`, `중복 정의`, `네이밍 충돌`, `엔티티 통합`) 가 하나도 포함되지 않으면 → ANALYZE 재작성 불요로 간주하고 본 §5 게이트 스킵 (dev.md §Fix Cycle 감지 의 ANALYZE 조건부 재진입 분기와 동일 로직)

   **5b. ANALYZE 게이트 본문 (5a 분기 미해당 시):**
   - (기존 §5 내용 그대로)
   - ANALYZE 문서 존재 / status: approved / 룰 갱신 지시서 모든 체크박스 완료 검증
   - 미충족 시 차단
```

### 3. doc-harness.md 변경

**변경 (1):** 디렉토리 예시 블록에 `analyze/` 추가
```markdown
docs/analyze/20260416/legacy_재개발/ANALYZE1.md   ← 신규 추가 (맨 앞)
docs/plan/20260416/legacy_재개발/PLAN1.md
docs/tasks/20260416/legacy_재개발/TASK1.md
docs/results/20260416/legacy_재개발/RESULT1.md
docs/reviews/20260416/legacy_재개발/REVIEW1.md
```

**변경 (2):** ANALYZE{n}.md 템플릿 §산출물 링크 플레이스홀더 통일
- 기존: `[계획안](../../../plan/YYYYMMDD/{슬러그}/PLAN1.md)`
- 변경: `[계획안](../../../plan/YYYYMMDD/작업목적/PLAN1.md)`

## 도메인 모델

해당 없음 (메타 워크플로우 변경, 도메인 엔티티/DTO 신규 없음).

## DB 설계 변경

해당 없음.

## 테스트 전략

본 사이클도 PLAN1 과 동일하게 메타 워크플로우 변경이므로 단위 테스트가 아닌 명세 정합성 trace 로 검증한다. 다만 변경 분량이 매우 작으므로 검증 시나리오는 3종으로 축소한다:

1. **plan.md §5a 분기 A (Small 보호) trace** — `docs/analyze/{슬러그}` 가 없는 fixture 입력에서 plan.md 가 본 게이트를 스킵하는지 명세 분석
2. **plan.md §5a 분기 B (Fix Cycle 키워드 미포함 스킵) trace** — 일반 코드 블로커만 있는 REVIEW fixture 에서 plan.md 가 본 게이트를 스킵하는지 명세 분석
3. **doc-harness.md 표현 일관성** — 디렉토리 예시 블록 5줄에 `analyze/` 포함 여부 확인 / ANALYZE 템플릿 산출물 링크가 `작업목적` 으로 통일되었는지 확인

## 제외 사항

- REVIEW1 의 참고 항목 2건 (CLAUDE.md 단계 나열의 Small/Large 표현 / domain-abbreviations.md "1차 정의 파일" 표현 강도) — 본 사이클 미반영, 차기 사이클에서 다듬기 가능
- REVIEW1 의 후속 작업 트래킹 5건 (기존 에이전트 단답형, 룰 본문 리팩터링, pre-commit 훅, lint, 마스터 약어 승격) — 모두 PLAN1 §제외 사항으로 의도적 deferred, 별도 작업 사이클에서 처리

## 예상 산출물

분량이 매우 작으므로 TASK 분할 없음. 단일 TASK2.md 로 진행.

- [태스크](../../../tasks/20260423/analyze_phase_도입/TASK2.md)
