---
status: completed
created: 2026-05-06
updated: 2026-05-06
---
# ANALYZE 강제 효력 강화 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260506/analyze_section_enforcement/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md` §4.1](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: 첫 백틱 쌍에 파일 경로, `→ 검증:` 다음에 확인 방법 (검증 영역 백틱 사용 금지 — `check-task-unstage.sh` 훅 파싱 충돌 방지).

### Phase 1: ANALYZE 템플릿 강화 (`templates.md`)

- [x] `backend/.claude/rules/process/doc-harness/templates.md` 의 ANALYZE{n}.md 블록에 "## 성공 기준 후보 (PLAN 변환 대상)" 신규 섹션 추가 (표 컬럼: 후보 기준 / 검증 명령 초안 (PLAN 에서 확정), §2.5 면책 적용 배제 주석 명기, "없음" 단독 금지) → 검증: grep -c "## 성공 기준 후보 (PLAN 변환 대상)" backend/.claude/rules/process/doc-harness/templates.md 매칭 결과 1 이상
- [x] `backend/.claude/rules/process/doc-harness/templates.md` 의 ANALYZE{n}.md 블록에 "## 도메인 룰 4영역 점검" 신규 섹션 추가 (4행 표 영역 / 해당·비해당 / 근거 또는 영향. 알람 4단계 → ot-integration.md §5 단독, 인터록 → §2·§5, AI 운전 모드 → §5, 이력 기록 → §5. "비해당" 단독 4건 차단 해제 조건 — 사유 병기 + "## 신규 엔티티/DB 컬럼" "없음" 동시 충족 명기) → 검증: grep -c "## 도메인 룰 4영역 점검" backend/.claude/rules/process/doc-harness/templates.md 매칭 결과 1 이상
- [x] `backend/.claude/rules/process/doc-harness/templates.md` 의 기존 ANALYZE "## 가정 및 미해결 질문" 섹션 주석에 "wtp-domain-expert 가 본 섹션 + 신규 4영역 섹션 교차 검토 (가정의 4영역 충돌 ↔ 4영역 표 '해당' 일관성)" 문자열 포함하도록 강화 → 검증: grep "wtp-domain-expert" backend/.claude/rules/process/doc-harness/templates.md | grep "4영역 섹션 교차 검토" 매칭 결과 1 이상

### Phase 2: PLAN §5b 자율 차단 강화 (`dev/plan.md`)

- [x] `backend/.claude/commands/dev/plan.md` 의 §5b ANALYZE 게이트 본문에 "## 가정 및 미해결 질문" 보유 + 1건 이상 + "없음" 단독 차단 검증 항목 추가 → 검증: grep "가정 및 미해결 질문" backend/.claude/commands/dev/plan.md | grep "없음 단독" 매칭 결과 1 이상
- [x] `backend/.claude/commands/dev/plan.md` 의 §5b 에 "## 성공 기준 후보" 모호 표현 정규식 8건 (^성능 개선$ ^안정성 향상$ ^개선$ ^향상$ ^기능 추가$ ^코드 개선$ ^리팩토링$ ^문서화$) 단독 행 차단 검증 항목 추가 → 검증: grep -cE "(성능 개선|안정성 향상|개선|향상|기능 추가|코드 개선|리팩토링|문서화)\\\$" backend/.claude/commands/dev/plan.md 매칭 결과 8 이상
- [x] `backend/.claude/commands/dev/plan.md` 의 §5b 에 "## 도메인 룰 4영역 점검" 4행 채움 + "비해당" 단독 4건 차단 + 사유 병기 + "## 신규 엔티티/DB 컬럼" "없음" 동시 충족 시 차단 해제 분기 추가 → 검증: grep "비해당" backend/.claude/commands/dev/plan.md | grep "차단 해제" 매칭 결과 1 이상
- [x] `backend/.claude/commands/dev/plan.md` 의 §5b 에 PLAN 자율 차단 (3섹션 존재 + 모호 단독 행) vs REVIEW 자동 점검 (TASK 체크박스 → 검증 누락 + §2.1 정량 기준 + §2.5 인용 근거) 책임 경계 주석 추가 → 검증: grep "PLAN 자율 차단" backend/.claude/commands/dev/plan.md | grep "REVIEW 자동 점검" 매칭 결과 1 이상

### Phase 3: 에이전트 정의 강화

- [x] `backend/.claude/agents/wtp-domain-expert.md` 의 검토 항목에 "## 도메인 룰 4영역 점검" 신규 섹션 자체 점검 항목 추가 (4영역 인용 근거 — 알람 §5, 인터록 §2·§5, AI 운전 모드 §5, 이력 기록 §5) → 검증: grep "도메인 룰 4영역 점검" backend/.claude/agents/wtp-domain-expert.md 매칭 결과 1 이상
- [x] `backend/.claude/agents/wtp-domain-expert.md` 에 형식적 충족 패턴 2종 — (a) "비해당" 단독 4건 + 사유 부재, (b) "해당" 표기 + "근거 또는 영향" 컬럼 공란/확인 필요 수준 — 모두 블로커 등급 명시 → 검증: grep "형식적 충족" backend/.claude/agents/wtp-domain-expert.md 매칭 결과 1 이상
- [x] `backend/.claude/agents/wtp-domain-expert.md` 에 "비해당 근거 타당성" 판정 기준 (구체 사유 명기 + "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시) + 가정 섹션 교차 검증 (가정의 4영역 충돌 기재 ↔ 4영역 표 "해당" 일관성) 명시 → 검증: grep "비해당 근거 타당성" backend/.claude/agents/wtp-domain-expert.md 매칭 + grep "가정 섹션 교차 검증" backend/.claude/agents/wtp-domain-expert.md 매칭 모두 1 이상
- [x] `backend/.claude/agents/wtp-backend-engineer.md` 에 REVIEW 자동 점검 책임 경계 (TASK 체크박스 → 검증 누락 + coding-discipline §2.1 정량 기준 + §2.5 면책 인용 근거 의무) + PLAN 자율 차단 항목 (3섹션 존재 + 모호 표현 정규식) 과 중복 검증 금지 명시 → 검증: grep "PLAN 자율 차단" backend/.claude/agents/wtp-backend-engineer.md | grep "중복" 매칭 결과 1 이상

### Phase 4: dogfood 검증 (RESULT 부록 기록)

- [x] `backend/docs/results/20260506/analyze_section_enforcement/RESULT1.md` 의 "## 부록: 의도적 위반 시뮬레이션 결과" 섹션에 본 PLAN1.md 가 status approved 도달한 dogfood 통과 사실 기록 → 검증: RESULT1.md 의 부록 섹션에 PLAN1 통과 사실 기록 1건 확인
- [x] `backend/docs/results/20260506/analyze_section_enforcement/RESULT1.md` 부록에 의도적 위반 시뮬레이션 케이스 1건 이상 기록 (예: "## 성공 기준 후보" 에 "성능 개선" 단독 행 ANALYZE 작성 → §5b 자율 차단 시뮬레이션 → 차단 사유 기록) → 검증: RESULT1.md 부록의 시뮬레이션 케이스 행 1건 이상 확인

### Phase 5: 빌드 검증

- [x] `./gradlew.bat build` 실행 성공 확인 (룰 자산만 변경하므로 빌드 영향 없음 사후 검증)

## 산출물
- [결과](../../../results/20260506/analyze_section_enforcement/RESULT1.md)
