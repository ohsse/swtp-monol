---
status: completed
created: 2026-04-29
updated: 2026-04-29
---
# ai-server LLM 코딩 디시플린 적용 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260429/ai_server_discipline_apply/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md §4.1`](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [ ] {백틱 경로} 작업 → 검증: {확인 명령}`. **검증 영역에 백틱 사용 금지** (훅 파싱 충돌 방지).

### Phase 1: ai-server 신규 룰 파일 작성 (가장 긴 작업)

- [x] `swtp/ai-server/.claude/rules/coding-discipline.md` 신규 작성 — 머리말 (ROOT 인용 + 본 룰 신설 추적성: backend ANALYZE 결정 명시) / §1 정량 기준 Python 적용 (50줄 → 60줄 상향 조정 근거: 정보 밀도·docstring·Ruff line-length 120, 추상화 3단·Pydantic 상속 3단 backend 동일) / §2 ai-server §2.5 면책 영역 (① 모델 추론 단일 흐름 ② 이상 탐지 특징 추출 파이프라인, 인용 근거 명기 의무) / §3 적용 시기 (Phase 3 신규 추론 함수 작성 시점부터, Phase 2 501 스텁 적용 X, _legacy 적용 범위 외) / §4 ROOT 룰과의 관계 (§1·§3·§4 적용, §2.5 면책 영역만 ai-server 식 재정의) → 검증: 파일 존재 + grep "60줄" 매칭 1건 이상 + grep "추론 단일 흐름" 매칭 1건 이상 + grep "이상 탐지" 매칭 1건 이상 + ROOT 룰 인용 swtp/.claude/rules/coding-discipline.md 매칭 1건 이상

### Phase 2: ai-server 에이전트 검토 항목 6번 추가

- [x] `swtp/ai-server/.claude/agents/wtp-domain-expert.md` 의 "## 검토 항목" 섹션 끝에 6번 항목 추가 — 의미적 변형 텍스트 (ANALYZE/PLAN 가정 섹션 도메인 4영역 점검 + 추론 schema 설계 가정의 책임 분리 원칙 ot-integration.md §6.6 충돌 점검, 가정 섹션 비었거나 "없음" 시 블로커 — coding-discipline.md §1 적용). 기존 5개 항목 (1.알람 매핑·2.인터록 메타데이터·3.운전 모드 신호 분리·4.물리적 타당성·5.결측 대체값) 텍스트 그대로 보존 → 검증: grep "ANALYZE/PLAN 가정" wtp-domain-expert.md 매칭 + grep "책임 분리 원칙" 매칭 + 기존 5개 항목 텍스트 보존 (변경 없음 확인)

### Phase 3: ai-server CLAUDE.md 인용 추가

- [x] `swtp/ai-server/CLAUDE.md` 의 "## 외부 참조 룰" 섹션에 5번째 항목 추가 — "**LLM 코딩 행동 규율 (4원칙 + 정량 기준 + 면책 조항)**: swtp/.claude/rules/coding-discipline.md (ROOT). §1~§4 는 Python 환경에 그대로 적용. §2.1 정량 기준은 ai-server 자체 룰의 Python 60줄 조정 적용. §2.5 면책 조항은 ai-server 범위 외이며 자체 면책 영역 (추론 단일 흐름·이상 탐지) 으로 대체" → 검증: grep "coding-discipline" CLAUDE.md 매칭 1건 이상 (외부 참조 섹션 내)
- [x] `swtp/ai-server/CLAUDE.md` 의 "## 내부 룰 (ai-server 자체 — `.claude/rules/`)" 섹션에 5번째 항목 추가 — coding-discipline.md (ROOT 룰의 ai-server 적용 — Python 60줄·면책 영역 2건) → 검증: grep -c "coding-discipline" CLAUDE.md 결과 2 이상 (외부 참조 + 내부 룰 양쪽 매칭)

### Phase 4: ROOT coding-discipline.md 갱신 + backend 빌드 무영향 확인

- [x] `swtp/.claude/rules/coding-discipline.md` §5 표 ai-server 행 갱신 — 적용 룰 컬럼: "swtp/ai-server/.claude/rules/coding-discipline.md (ai-server 자체 룰)" / 핵심 적용 지점 컬럼: "ROOT §1~§4 적용, §2.1 정량 기준 Python 60줄 상향 조정, §2.5 면책 영역 2건 추가 (추론 단일 흐름·이상 탐지 파이프라인)" → 검증: grep "ai-server 자체 룰" coding-discipline.md 매칭 1건 이상 + grep "현재 본 룰 미적용" coding-discipline.md 매칭 0건 (제거 확인)
- [x] `swtp/.claude/rules/coding-discipline.md` 의 "## 폐기·갱신 이력" 표에 2026-04-29 행 추가 — 변경 컬럼: "§5 표 ai-server 행 갱신 (적용 위임)" / 배경 컬럼: "ai-server 자체 룰 파일 신설 (`docs/analyze/20260429/ai_server_discipline_apply/ANALYZE1.md`). REVIEW1 §개선 제안 1·3 후속 작업" → 검증: grep "ai_server_discipline_apply" coding-discipline.md 매칭 1건 이상
- [x] `./gradlew.bat build` 실행 → 검증: BUILD SUCCESSFUL 출력 확인, 기존 16개 backend 테스트 모두 PASS (ai-server 영역은 Java 빌드 대상 외이므로 영향 없음 확인)

## 산출물
- [결과](../../../results/20260429/ai_server_discipline_apply/RESULT1.md)
