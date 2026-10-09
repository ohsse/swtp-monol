---
status: completed
created: 2026-04-30
updated: 2026-04-30
---
# 거버넌스 명확화 — ROOT CLAUDE.md 신설 + "5인 팀" 명칭 정렬

## 관련 계획

- [계획안](../../../plan/20260430/wtp_governance_clarity/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md` §4.1`](../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [x] {파일경로 백틱} 작업 → 검증: {확인 명령}`. 검증 영역에 백틱 사용 금지.

### Phase 1: ROOT `swtp/CLAUDE.md` 신설

- [x] `swtp/CLAUDE.md` 신설 — 5섹션 (모노레포 개요·분산 SSOT 모델·모듈별 진입점·5인 회의 정의·입문 가이드) + 폐기·갱신 이력 표 → 검증: ls swtp/CLAUDE.md 매칭 + grep -c "^## " swtp/CLAUDE.md 결과 5 이상
- [x] `swtp/CLAUDE.md` "데이터 도메인 vs 비즈니스 도메인" 정의 본문 복제 금지 — 라벨 인용 허용, dict/README.md 링크만 → 검증: grep -E "값의 형식.*SQL 타입|업무 영역.*Java 패키지|업무 영역.*com\.mo" swtp/CLAUDE.md 매칭 0건, dict/README.md 링크 1건 이상
- [x] `swtp/CLAUDE.md` 도메인 4영역 정책 본문 (임계값·트리거·시퀀스) 복제 금지 — 라벨 인용 허용, ot-integration.md 링크만 → 검증: grep -E "0:정상|1:주의|2:경보|3:위험|5분 초과|TRIP|Hold Last Value" swtp/CLAUDE.md 매칭 0건, ot-integration.md 링크 1건 이상
- [x] `swtp/CLAUDE.md` 5인 회의 정의 — 4 에이전트 이름 모두 명시 → 검증: grep -E "wtp-(dba-reviewer|backend-engineer|domain-expert|glossary-manager)" swtp/CLAUDE.md 매칭 4건 이상

### Phase 2: ROOT `coding-discipline.md` "5인 팀" 정렬

- [x] `swtp/.claude/rules/coding-discipline.md` §5 표·§5.1·§1 backend 적용 박스 — "5인 팀 회의" → 풀 표기 첫 등장 + "5인 회의" 약식 → 검증: grep "5인 팀" swtp/.claude/rules/coding-discipline.md 매칭 0건

### Phase 3: backend 5 파일 명칭 정렬

- [x] `swtp/backend/.claude/rules/dict/README.md` — "5인 팀 회의" → "5인 회의" → 검증: grep "5인 팀" swtp/backend/.claude/rules/dict/README.md 매칭 0건
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — 동일 치환 → 검증: grep "5인 팀" swtp/backend/.claude/rules/dict/standard-terms.md 매칭 0건
- [x] `swtp/backend/.claude/rules/process/README.md` — process 진입점이므로 풀 표기 우선 → 검증: 풀 표기 1건 이상 + grep "5인 팀" 매칭 0건
- [x] `swtp/backend/.claude/rules/process/doc-harness/templates.md` — ANALYZE 회의록 헤더 "5인 팀 토픽 주도" → "5인 회의 토픽 주도" → 검증: grep "5인 팀" swtp/backend/.claude/rules/process/doc-harness/templates.md 매칭 0건
- [x] `swtp/backend/.claude/commands/dev/analyze.md` — 회의 정의 섹션 (라인 1·8·87 등) 풀 표기 (4 에이전트 이름 명시) → 검증: grep "5인 팀" swtp/backend/.claude/commands/dev/analyze.md 매칭 0건 + 4 에이전트 이름 모두 명시

### Phase 4: 검증

- [x] 전체 grep "5인 팀" → 검증: grep -rn "5인 팀" swtp/.claude/ swtp/backend/.claude/ 결과 0건 (backend CLAUDE.md 는 풀 표기 패턴이라 본문에 "5인 팀" 단독 등장 없음 확인)
- [x] 변경 파일 수 = 7 (swtp/CLAUDE.md 신설 + 6파일 갱신) → 검증: git diff --name-only 결과 7건
- [x] ANALYZE1 의 룰 갱신 지시서 체크박스 8건 일괄 [x] 처리 → 검증: grep -c "- \[x\]" backend/docs/analyze/20260430/wtp_governance_clarity/ANALYZE1.md 결과 8 이상

### Phase 5: 커밋 (사용자 명시 승인 후)

- [x] 사용자에게 "Cycle 2-A 커밋 진행" 명시 승인 요청 → 검증: 사용자 응답 "OK" / "커밋" 등
- [x] git commit (한국어 메시지 + Co-Authored-By 푸터) → 검증: git log -1 매칭 + git status clean

## 산출물

- (medium 작업 — RESULT/REVIEW 면제, transitions.md 표 §/dev:result·§/dev:review)
