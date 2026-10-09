---
status: completed
created: 2026-04-29
updated: 2026-04-29
---
# LLM 코딩 디시플린 룰 도입 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260429/llm_coding_discipline/PLAN1.md)

## Phase

> dogfooding: 본 작업으로 도입할 `→ 검증: ...` 체크박스 형식을 본 TASK 자체에 적용. 검증 영역에 백틱 금지 (훅 파싱 충돌 방지).

### Phase 1: ROOT 신규 룰 파일 작성

- [x] `swtp/.claude/rules/coding-discipline.md` (ROOT) 신규 작성 — §1 구현 전 사고 / §2 단순성 우선 / §2.5 정량 기준 면책 조항 / §3 정밀한 수정 (데드 코드 정책 포함) / §4 목표 중심 실행 / §5 시스템별 적용 → 검증: 파일 존재 확인 + grep "면책" 매칭 1건 이상 + ot-integration.md / db/query-tuning.md 인용 근거 명시 확인

### Phase 2: 5종 템플릿 보강

- [x] `.claude/rules/process/doc-harness/templates.md` ANALYZE 템플릿에 "## 가정 및 미해결 질문" 섹션 추가 (최소 1건 기재 의무 명시) → 검증: grep "가정 및 미해결 질문" templates.md 의 ANALYZE 블록 매칭
- [x] `.claude/rules/process/doc-harness/templates.md` PLAN 템플릿에 "## 가정 및 미해결 질문" 섹션 추가 + "## 테스트 전략" 을 "## 성공 기준 (검증 가능 형태)" 로 명칭 확장 → 검증: PLAN 블록에 두 변경 모두 반영 확인
- [x] `.claude/rules/process/doc-harness/templates.md` TASK 템플릿 "## Phase" 섹션 설명에 "체크박스 형식: `- [ ] 작업 → 검증: 명령` 권장" 명시 + 검증 영역 백틱 금지 안내 → 검증: TASK 블록에 검증 형식 안내 매칭
- [x] `.claude/rules/process/doc-harness/templates.md` RESULT 템플릿 "## 변경 사항" 에 "### 계획 외 변경" 하위 절 추가 (없으면 "없음" 명시 의무) → 검증: RESULT 블록에 "계획 외 변경" 매칭
- [x] `.claude/rules/process/doc-harness/templates.md` REVIEW 템플릿 "## 발견 사항" 분류 안내에 "복잡도 과잉(Overengineering)" 카테고리 추가 → 검증: REVIEW 블록에 "복잡도 과잉" 매칭

### Phase 3: 보조 룰 갱신

- [x] `.claude/rules/process/doc-harness/checkbox-rules.md` "## 검증 기준 표기 형식" 절 신설 — `→ 검증: ...` 패턴 명시 + 검증 영역 백틱 금지 (훅 파싱 두 번째 백틱 오인 방지) → 검증: grep "검증 기준" checkbox-rules.md 매칭 + grep "백틱 금지" 매칭
- [x] `.claude/rules/process/hooks-guide.md` "## 본 시점 자동 차단 훅 신설 보류" 절 신설 — 옵션 C 결정 정합성 추적 (REVIEW 자동 점검까지만, 신규 차단 훅은 별도 ANALYZE) → 검증: grep "자동 차단 훅 신설 보류" hooks-guide.md 매칭
- [x] `.claude/rules/test-strategy.md` §1 단위 테스트 절에 "버그 수정 TASK 의 첫 체크박스 = 재현 테스트 RED 확인 의무" 명시 → 검증: grep "버그 수정.*첫 체크박스" test-strategy.md 매칭

### Phase 4: 에이전트 점검 항목 보강

- [x] `.claude/agents/wtp-backend-engineer.md` (backend, plan 분석 오류 정정 — ROOT 가 아닌 backend 위치) 에 점검 항목 4개 추가 — ① 단순성 위반 (메서드 50줄 / 추상화 3단 / DTO 상속 3단 초과, §2.5 면책 인용 시 인용 근거 누락 = 블로커) / ② TASK 체크박스 외 파일 변경 감지 (git diff 와 백틱 경로 교차) / ③ 체크박스 검증 기준 누락 (`→ 검증:` 부재) / ④ 데드 코드 직접 삭제 → 검증: grep "단순성 위반" wtp-backend-engineer.md 매칭 + grep "체크박스 외 파일" 매칭
- [x] `.claude/agents/wtp-domain-expert.md` (backend, plan 분석 오류 정정 — ROOT 가 아닌 backend 위치) 에 점검 항목 1개 추가 — ANALYZE/PLAN 의 "## 가정 및 미해결 질문" 섹션 도메인 가정 위반 여부 점검, 점검 대상 4영역 명시 (알람 4단계 · 인터록 · 운전 모드 · 이력 기록 의무) → 검증: grep "알람 4단계" wtp-domain-expert.md 매칭 + grep "이력 기록 의무" 매칭

### Phase 5: backend CLAUDE.md 인덱스 갱신 + 빌드 검증

- [x] `CLAUDE.md` §규칙 문서 인덱스 표에 ROOT `swtp/.claude/rules/coding-discipline.md` 신규 행 추가 — 참조 시점 "신규 코드 작성 / 룰 변경 작업 전" 명시 → 검증: grep coding-discipline CLAUDE.md 매칭 1건 이상
- [x] `./gradlew.bat build` 실행 → 검증: BUILD SUCCESSFUL 출력 확인, 기존 16개 테스트 모두 PASS

## 산출물
- [결과](../../../results/20260429/llm_coding_discipline/RESULT1.md)
