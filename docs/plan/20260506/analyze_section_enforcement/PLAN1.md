---
status: approved
created: 2026-05-06
updated: 2026-05-06
---
# ANALYZE 강제 효력 강화 — 계획안

## 목적

backend `/dev:analyze` 단계의 ANALYZE 템플릿 3섹션 강제 효력을 향상하여, ROOT [`coding-discipline.md`](../../../../../.claude/rules/coding-discipline.md) §1·§4.2 의 형식적 충족·"없음" 단독 통과 차단 메커니즘을 확립한다. ANALYZE1.md "## PLAN 으로 전달할 결정 사항" 의 4 파일 변경을 IMPL 단계 체크박스로 분해한다.

## 배경

- ROOT/backend 거버넌스 강화 plan (`~/.claude/plans/quiet-hugging-bachman.md`) 의 **Cycle 1**.
- ANALYZE1.md 5인 회의 결과 (Round 1 종결): `wtp-glossary-manager`·`wtp-backend-engineer`·`wtp-domain-expert` Round 1 답변 종합. 안건 5건 중 1건 블로커 정정 (알람 4단계 인용 `§3·§5` → `§5` 단독), 3건 권고 흡수 (§2.5 면책 적용 배제 주석·정규식 8건 확장·"비해당" 차단 해제 조건).
- 분기 결론: **(a) backend 자체 결정 범위** — ROOT `coding-discipline.md` 본문 변경 0건, ROOT 어휘 사전 변경 0건. 자동 차단 훅 미신설 (`coding-discipline.md §7.1` 보류 결정 유지).

## 범위

### 변경 대상 (4 파일)

1. `backend/.claude/rules/process/doc-harness/templates.md` — ANALYZE 템플릿 3섹션 도입/강화 (성공 기준 후보 신규 + 4영역 점검 신규 + 가정 섹션 주석 강화)
2. `backend/.claude/commands/dev/plan.md` — §5b 전제조건 검증에 ANALYZE 3섹션 자율 차단 추가 (PLAN 자율 차단 vs REVIEW 자동 점검 책임 경계 주석)
3. `backend/.claude/agents/wtp-domain-expert.md` — REVIEW 점검 책임에 4영역 신규 섹션 추가, 형식적 충족 패턴 2종 블로커 등급
4. `backend/.claude/agents/wtp-backend-engineer.md` — REVIEW 자동 점검 책임 경계 명시 (PLAN 자율 차단 항목과 중복 금지)

### 영향받는 모듈

backend `.claude/` 룰·에이전트 자산 한정. Java 소스 (`api/`·`scheduler/`·`common/`) 변경 0건. DB 스키마 변경 0건. Gradle 빌드 영향 없음.

## 구현 방향

### Phase 1: ANALYZE 템플릿 강화 (`templates.md`)

ANALYZE{n}.md 블록 (라인 7~101 근처) 의 3섹션 변경:

- 신규 "## 성공 기준 후보 (PLAN 변환 대상)" 섹션 — `## PLAN 으로 전달할 결정 사항` 직전 또는 직후 위치. 표 컬럼: `후보 기준 | 검증 명령 초안 (PLAN 에서 확정)`. 주석: ROOT `coding-discipline.md §4.2` 적용. **§2.5 면책 적용 배제** 명기. "없음" 단독 금지.
- 신규 "## 도메인 룰 4영역 점검" 섹션 — 4행 표 `영역 | 해당/비해당 | 근거 또는 영향`. 주석: 4영역 인용 근거 (알람 4단계 → `ot-integration.md §5` 단독, 인터록 → `§2·§5`, AI 운전 모드 → `§5`, 이력 기록 → `§5`). "비해당" 단독 4건 차단 해제 조건 명기 (사유 병기 + "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족).
- 기존 "## 가정 및 미해결 질문" 섹션 주석 강화 — "wtp-domain-expert 가 본 섹션 + 신규 4영역 섹션 교차 검토 (가정의 4영역 충돌 ↔ 4영역 표 '해당' 일관성)" 명시.

### Phase 2: PLAN §5b 자율 차단 강화 (`dev/plan.md`)

§5b "ANALYZE 게이트 본문" 에 ANALYZE 3섹션 검증 항목 추가:

- "## 가정 및 미해결 질문" 보유 + 1건 이상 + "없음" 단독 차단
- "## 성공 기준 후보" 보유 + 1건 이상 + 모호 표현 정규식 8건 단독 행 차단:
  `^성능 개선$` · `^안정성 향상$` · `^개선$` · `^향상$` · `^기능 추가$` · `^코드 개선$` · `^리팩토링$` · `^문서화$`
- "## 도메인 룰 4영역 점검" 보유 + 4행 채움 + "비해당" 단독 4건 차단 + 사유 병기 + "## 신규 엔티티/DB 컬럼" "없음" 동시 충족 시 차단 해제 분기

위반 시 PLAN 작성 중단 + ANALYZE 보강 안내. 자동 차단 훅 미신설 (메인 Claude 자율 차단). PLAN 자율 차단 (섹션 존재 + 모호 단독 행) vs REVIEW 자동 점검 (TASK 체크박스 `→ 검증:` 누락 + 정량 기준 + §2.5 인용 근거) 책임 경계 주석 추가.

### Phase 3: 에이전트 정의 강화

- `wtp-domain-expert.md`: REVIEW 점검 책임에 "## 도메인 룰 4영역 점검" 신규 섹션 자체 점검 추가. 형식적 충족 패턴 2종 블로커 등급 — (a) "비해당" 단독 4건 + 사유 부재, (b) "해당" 표기 + "근거 또는 영향" 컬럼 공란/`확인 필요` 수준. "비해당 근거 타당성" 판정 기준 (구체 사유 + "## 신규 엔티티/DB 컬럼" "없음") 명기. 가정 섹션 교차 검증 추가.
- `wtp-backend-engineer.md`: REVIEW 자동 점검 책임 경계 명시 — TASK 체크박스 `→ 검증:` 누락 + `coding-discipline §2.1` 정량 기준 + `§2.5` 인용 근거 의무. PLAN 자율 차단 항목 (3섹션 존재 + 모호 표현 정규식) 과 중복 검증 금지.

### Phase 4: dogfood 검증 시뮬레이션

본 사이클 자체가 새 템플릿 첫 적용 사례. `ANALYZE1.md` 의 3섹션 (가정 5건 / 성공 기준 후보 5건 / 4영역 점검 4행 비해당 + 차단 해제 조건 충족) 이 정상 ANALYZE 통과해 PLAN 진입에 성공했음을 RESULT 단계에서 확인. 의도적 위반 시뮬레이션은 별도 케이스 (RESULT 단계 부록).

### Phase 5: 빌드 검증

`./gradlew.bat build` 성공 확인 — 본 작업은 룰 자산만 변경하므로 빌드 영향 없음을 사후 검증.

## 성공 기준 (검증 가능 형태)

> ROOT [`coding-discipline.md` §4.2](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE1.md "## 성공 기준 후보" 5건을 검증 명령 확정 형태로 변환.

| # | 기준 | 검증 명령·테스트·조회 |
|---|------|---------------------|
| 1 | `templates.md` 의 ANALYZE 템플릿에 3섹션 (`## 성공 기준 후보` · `## 도메인 룰 4영역 점검` · 강화된 `## 가정 및 미해결 질문` 주석) 모두 존재 | `grep -c "## 성공 기준 후보"·"## 도메인 룰 4영역 점검"` 매칭 결과 각각 1 이상. `sed -n` 으로 가정 섹션 주석에 "wtp-domain-expert 가 본 섹션 + 신규 4영역 섹션 교차 검토" 문자열 포함 확인 |
| 2 | `dev/plan.md` §5b 에 모호 표현 정규식 8건 + "비해당" 차단 해제 분기 + 책임 경계 주석 모두 존재 | `grep -E "성능 개선\\$\|안정성 향상\\$\|개선\\$\|향상\\$\|기능 추가\\$\|코드 개선\\$\|리팩토링\\$\|문서화\\$"` 매칭 결과 8건. `grep "비해당.*차단 해제\|사유 병기"` 매칭 결과 1 이상. `grep "PLAN 자율 차단.*REVIEW 자동 점검\|책임 경계"` 매칭 결과 1 이상 |
| 3 | `wtp-domain-expert.md` 가 4영역 신규 섹션 + 형식적 충족 2종 블로커 + 가정 섹션 교차 검증 모두 명시 | `grep "도메인 룰 4영역 점검\|형식적 충족\|비해당 근거 타당성\|가정 섹션 교차 검증"` 매칭 결과 모두 1 이상 |
| 4 | `wtp-backend-engineer.md` 가 REVIEW 자동 점검 책임 경계 + PLAN 자율 차단과 중복 금지 명시 | `grep "→ 검증:\|§2.1 정량\|§2.5 인용 근거\|PLAN 자율 차단.*중복"` 매칭 결과 모두 1 이상 |
| 5 | 본 작업의 ANALYZE1.md 가 새 템플릿 첫 적용 사례로 정상 PLAN 진입 (dogfood) | 본 PLAN1.md 가 `status: approved` 도달. `dev:plan §5b` 자율 차단 시뮬레이션 (의도적 위반 ANALYZE 작성 → 차단 메시지 확인) 은 RESULT 단계 부록에 기록 |

> **§2.5 면책 적용 배제** — 본 5건은 정량 기준 면책 영역이 아니다. 룰 본문 변경 검증이며 메서드 줄 수·추상화 깊이 무관.

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE 의 가정·미해결 질문을 PLAN 단계 결정으로 변환.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 본 변경이 ROOT `coding-discipline.md §4.2` 와 의미 충돌 없음 | 결정 | ANALYZE1 안건 5 결론 유지 — backend 위임 자산 변경 범위 (`coding-discipline.md §5` 표). PLAN 단계 추가 결정 없음 |
| 모호 표현 정규식 8건이 정수장 도메인 작업의 모호 표현 패턴을 충분히 포괄 | 가정 | **PLAN 결정**: 본 8건으로 1차 도입. 운영 사례 누적 후 보강 (별도 ANALYZE 트리거 — 누락 사례 3건 누적). 본 PLAN 범위에서 추가 후보 도출 시도 없음 |
| "비해당" 단독 차단 해제 조건 (사유 병기 + 신규 엔티티/DB 컬럼 "없음") 이 도메인 안전 작업의 형식적 우회 방지 충분 | 가정 | **PLAN 결정**: wtp-domain-expert REVIEW 단계의 "비해당 근거 타당성" 판정으로 보완 (Phase 3). 운영 사례 누적 후 강화 검토 — 우회 사례 발생 시 §7.1 트리거 누적과 함께 별도 ANALYZE |
| PLAN 자율 차단 vs REVIEW 자동 점검 책임 경계 명시화 | 결정 | **PLAN 결정**: Phase 2 (dev/plan.md §5b) 와 Phase 3 (wtp-backend-engineer.md) 양쪽에 동일 책임 경계 주석 작성. PLAN = 3섹션 존재 + 모호 단독 행, REVIEW = TASK `→ 검증:` 누락 + §2.1 정량 기준 + §2.5 인용 근거 |
| 자동 차단 훅 미신설 (a 안) 유지 적절 | 결정 | **PLAN 결정**: `coding-discipline.md §7.1` 보류 결정 유지. 본 작업에서 신규 훅 0건. 우회 발생 사례 모니터링은 Cycle 2 (`/governance` slash command) 의 정합 진단 항목으로 위임 |
| dogfood 검증 시 의도적 위반 시뮬레이션의 결과는 RESULT 단계에서 분리 기록 | 결정 | **PLAN 결정**: RESULT1.md "## 부록: 의도적 위반 시뮬레이션 결과" 섹션에 기록. PLAN 진입 차단 확인은 본 PLAN1.md 가 status: approved 도달함으로 충분 |

## 도메인 모델

신규 엔티티·DTO·컬럼 0건 — 본 작업은 룰/하네스 변경.

## DB 설계 변경

DB 스키마 변경 0건 — 본 작업은 Java 소스·DB 무접촉.

## 제외 사항

다음은 본 PLAN 범위 외다. 모두 별도 사이클 권장.

- **ROOT `coding-discipline.md` 본문 변경** — ANALYZE1 안건 5 결론. ROOT §1~§5 본문 수정 0건
- **ROOT 어휘 사전 (`standard-words.md`·`standard-data-domains.md`·`domain-abbreviations.md`) 변경** — 신규 표준 단어·데이터 도메인·비즈니스 도메인 약어 0건
- **자동 차단 훅 신설** — `coding-discipline.md §7.1` 보류 결정 유지. 본 PLAN 에서 신규 훅 0건
- **300줄+ 룰 파일 분리** — `templates.md` 376줄, `test-strategy.md` 343줄, `entity-patterns.md` 318줄. Cycle 2 의 `/governance` 가 발견사항으로 출력 후 사용자 검토
- **§7.1 훅 신설 보류 결정 재검토** — 거버넌스 진단 (3) 깊이 운영 후 트리거 누적 시 별도 ANALYZE
- **redirect stub 제거** — `db-patterns.md`·`doc-harness.md` 외부 참조 0건 확인됨. 폐기 정합성 별도 분석
- **backend CLAUDE.md "작업 흐름" 정책 본문 분리** — 라인 42~51 의 부분 복제 갭. Cycle 1 종료 후 별도 검토

## 부록: 도메인/DB 검토 결과

도메인 모델·DB 설계 변경 모두 없음 — `dev/plan.md §도메인·DB 검토 게이트` 적용 대상 외. wtp-domain-expert / wtp-dba-reviewer 검토 게이트 생략. 도메인 룰 4영역 점검은 ANALYZE1 단계에서 wtp-domain-expert 가 이미 점검 완료 (4영역 모두 비해당, 차단 해제 조건 충족).

## 예상 산출물

- [태스크](../../../tasks/20260506/analyze_section_enforcement/TASK1.md)
