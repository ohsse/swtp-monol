---
status: completed
created: 2026-04-23
updated: 2026-04-23
---
# TASK2 — 분석 단계 도입 Fix Cycle 2 (PLAN 게이트 분기 + doc-harness 표현 일관성)

## 관련 계획
- [계획안](../../../plan/20260423/analyze_phase_도입/PLAN2.md)

## 작업 범위

REVIEW1 의 블로커 1건과 권고 2건을 한 sweep 으로 해소한다. 분량이 작아 분할 없이 단일 TASK 로 진행.
PLAN2 §구현 방향 §1·§2·§3 의 변경 항목을 그대로 반영한다.

## Phase

### Phase 1: `dev/plan.md` §5 ANALYZE 게이트에 사전 판별 분기 추가

PLAN2 §2 plan.md §5 분기 설계 그대로 반영. 기존 §5 본문을 §5b 로 두고 그 앞에 §5a (사전 판별) 두 분기를 추가.

- [x] `.claude/commands/dev/plan.md` §전제조건 §5 의 ANALYZE 게이트 본문 앞에 §5a 사전 판별 블록 신규 추가
  - 분기 A: `docs/analyze/{슬러그}` 디렉토리 미존재 + 같은 슬러그 ANALYZE 이력 없음 → Small 작업으로 간주, §5 게이트 스킵
  - 분기 B: Fix Cycle 모드 + 직전 REVIEW 블로커에 도메인 정합성 키워드(`용어`/`약어`/`중복 정의`/`네이밍 충돌`/`엔티티 통합`) 미포함 → ANALYZE 재작성 불요로 간주, §5 게이트 스킵
- [x] 기존 §5 본문을 §5b 로 라벨 변경 (내용 변경 없음, "5a 분기 미해당 시" 단서 추가)

### Phase 2: `doc-harness.md` 표현 일관성 sweep

- [x] `.claude/rules/doc-harness.md` 디렉토리 예시 블록(현재 plan/tasks/results/reviews 4줄) 맨 앞에 `docs/analyze/20260416/legacy_재개발/ANALYZE1.md` 1줄 추가
- [x] `.claude/rules/doc-harness.md` ANALYZE{n}.md 템플릿 §산출물 링크의 `{슬러그}` 플레이스홀더를 `작업목적` 으로 통일 (다른 템플릿과 일치)

## 산출물
- [결과](../../../results/20260423/analyze_phase_도입/RESULT2.md)
