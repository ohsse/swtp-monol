---
status: completed
created: 2026-04-23
updated: 2026-04-23
---
# TASK1-2 룰/문서 — `domain-abbreviations.md` 신규 + `doc-harness.md`/`CLAUDE.md` 갱신

## 관련 계획
- [계획안](../../../plan/20260423/analyze_phase_도입/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 커맨드/하네스](TASK1-1.md)
- [TASK1-2 룰/문서](TASK1-2.md)
- [TASK1-3 검증](TASK1-3.md)

## 작업 범위
이 묶음은 룰/패턴 문서 작성·갱신에 한정한다.
슬래시 커맨드 동작 변경은 TASK1-1 에서, 시나리오 드라이런은 TASK1-3 에서 처리한다.

## Phase

### Phase 1: 도메인명 약어 사전 신규 작성

PLAN1.md §구현 방향 §7 "domain-abbreviations.md 1차 시드" 의 본문을 그대로 작성한다.
다른 사전(naming.md / ot-integration.md / multi-tenant.md) 에 이미 정의된 영역은 본 파일에서 중복 정의하지 않는다.

- [x] `.claude/rules/domain-abbreviations.md` 신규 작성
  - 사용 규칙 (모든 신규 약어는 ANALYZE 단계에서 본 파일에 먼저 등록)
  - 약어 표 — "마스터 도메인 (현재 코드 존재)" 섹션 (user, auth)
  - 약어 표 — "도입 예정 (아직 코드 없음)" 섹션 (pump, raw, ctrl, alarm, diag, opt)
  - 다른 약어 사전과의 관계 명시 (DB suffix → naming.md, 센서 코드 → ot-integration.md, 지자체 코드 → multi-tenant.md)

### Phase 2: `doc-harness.md` 갱신

분석 단계 도입에 따른 산출물 디렉토리·템플릿·Fix Cycle 분기 규칙을 추가한다.

- [x] `.claude/rules/doc-harness.md` 의 디렉토리 트리 다이어그램에 `analyze/` 항목 추가 (plan/ 위에 위치)
- [x] `.claude/rules/doc-harness.md` 의 "디렉토리 및 파일 네이밍 규칙" 표에 `analyze` 역할 행 추가 (파일명 `ANALYZE{cycle}.md`)
- [x] `.claude/rules/doc-harness.md` 의 Fix Cycle 다이어그램에 ANALYZE 조건부 재진입 분기 주석 추가 (도메인 정합성 키워드 검출 시 ANALYZE{N+1} 추가 작성)
- [x] `.claude/rules/doc-harness.md` 에 ANALYZE{n}.md 템플릿 섹션 신규 추가 (PLAN1.md §구현 방향 §5 본문 그대로 — **회의록 섹션·PLAN 으로 전달할 결정 사항 섹션 포함**)
- [x] `.claude/rules/doc-harness.md` 의 "TASK 체크박스 파일 경로 기록 규칙" 섹션에 룰 파일 경로 표기 가이드 보강 (ANALYZE 의 "룰 갱신 지시서" 체크박스도 동일 규칙 적용)
- [x] `.claude/rules/doc-harness.md` 의 상호 참조 규칙에 ANALYZE{n} 헤더 양식 추가 (n ≥ 2 일 때 직전 REVIEW 링크)

### Phase 3: `CLAUDE.md` 갱신

- [x] `CLAUDE.md` 의 "규칙 문서 인덱스" 표에 `domain-abbreviations.md` 행 추가 (참조 시점: "신규 도메인 약어 등록 전")
- [x] `CLAUDE.md` 의 "작업 흐름" 문단에 분석 단계 언급 추가 (단계 나열을 `요청 → 분석 → 계획 → 분해 → 구현 → 결과 → 리뷰 → 커밋` 으로 갱신)

## 산출물
- [결과](../../../results/20260423/analyze_phase_도입/RESULT1.md)
