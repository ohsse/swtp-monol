---
status: approved
created: 2026-04-30
updated: 2026-04-30
---
# §1 5번째 항목 점검 항목 도입 — 4 에이전트 갱신

## 목적

ROOT `coding-discipline.md` §1 5번째 항목 (도구 출력 경로 해석) 을 backend 4 에이전트의 §검토 항목에 블로커 강도로 명문화하여, 회의 단계에서 cwd 상대 경로 표기를 자동 검출 가능하게 한다.

## 배경

- 관련 ANALYZE: [`ANALYZE1`](../../../analyze/20260430/wtp_path_resolution_check/ANALYZE1.md)
- 관련 plan: `~\.claude\plans\sunny-zooming-goblet.md` (Cycle 2-B)
- 5인 회의 결론: 4 에이전트 모두에 §검토 항목 신설, 블로커 강도, 본문 통일 형식

## 범위

### 포함

- backend `agents/` 4 파일 §검토 항목 끝에 신규 항목 추가:
  - `wtp-backend-engineer.md` §11
  - `wtp-dba-reviewer.md` §7
  - `wtp-domain-expert.md` §5
  - `wtp-glossary-manager.md` §9
- 신규 항목 본문 통일 (ANALYZE1 §"PLAN 으로 전달할 결정 사항" 의 코드블록 참조)

### 제외

- 4 에이전트의 기존 §검토 항목 본문·번호·우선순위 (마지막 위치 추가만)
- 4 에이전트의 §출력 형식·§역할·§frontmatter (변경 없음)
- 자동 차단 훅 신설 (점검은 REVIEW 에이전트만 — 훅 강제는 별도 ANALYZE 트리거)
- false positive 빈도 모니터링 메커니즘 자동화 (수동 모니터링)

## 구현 방향

### Phase 1: 4 에이전트 §검토 항목 추가

각 에이전트 파일을 Edit 도구로 수정. 마지막 §검토 항목 본문 끝에 신규 항목 추가. 본문은 ANALYZE1 결정 사항의 통일 형식 적용.

### Phase 2: 검증

grep 으로 4 에이전트 모두에서 신규 항목 키워드("5번째 항목" 또는 "경로 표기 정합성") 매칭 확인.

### Phase 3: 커밋 (사용자 명시 승인 후)

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령 / 조회 |
|------|---------------|
| 4 에이전트 모두 신규 §검토 항목 보유 | `grep -l "경로 표기 정합성" backend/.claude/agents/wtp-*.md` 매칭 4 파일 |
| 각 에이전트의 §검토 항목 본문이 통일 형식 | `grep -c "ANALYZE/PLAN/REVIEW 의 경로 표기 정합성" backend/.claude/agents/wtp-*.md` 합계 4 |
| 기존 §검토 항목 본문 변경 없음 | `git diff backend/.claude/agents/wtp-*.md` 결과 — 새 항목 추가 영역만 변경, 기존 본문 라인 변경 0건 |
| 변경 파일 4개 (4 에이전트만) | `git diff --name-only` 결과 backend/.claude/agents/ 하위 4 파일 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 4 에이전트의 §검토 항목 마지막 위치에 신규 항목 추가만 — 기존 항목의 번호·순서·우선순위 불변 | 가정 | 결정 — Phase 1 의 Edit 시 기존 §검토 항목 끝에만 삽입, 다른 영역 변경 금지 (`coding-discipline.md` §3 정밀한 수정 적용) |
| 도입 후 1주 false positive 빈도 모니터링 — 잦은 의도적 cwd 상대 경로 사례 발견 시 점검 항목 강도 재검토 | 미해결 → 결정 | 결정 — 본 cycle 종료 후 1주 (2026-05-07) 시점에 운영 사례 점검. false positive 3건 이상 누적 시 별도 ANALYZE 안건으로 강도 재검토 (권고로 강등 가능). 본 결정은 ROOT CLAUDE.md "폐기·갱신 이력" 표에 기록하지 않으며 별도 plan 으로 추적 |
| 본 항목의 점검 영역이 wtp-backend-engineer §7~§9 + wtp-domain-expert §4 와 의미 분리됨 | 가정 | 결정 — 회의 결론 그대로 적용. 분리 모호 발견 시 §검토 항목 본문 갱신 (별도 ANALYZE) |

## 제외 사항

- 4 에이전트의 §역할·§frontmatter·§출력 형식 변경
- 자동 차단 훅 신설 (REVIEW 에이전트 자동 점검만)
- false positive 모니터링 자동화 (수동)
- ai-server `coding-discipline.md` 의 §1 5번째 항목 적용 (사용자 지시로 점검 대상 외)

## 예상 산출물

- [태스크](../../../tasks/20260430/wtp_path_resolution_check/TASK1.md)
