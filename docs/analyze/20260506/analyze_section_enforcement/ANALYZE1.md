---
status: approved
created: 2026-05-06
updated: 2026-05-06
---
# ANALYZE 강제 효력 강화 — 도메인 분석

## 작업 배경

ROOT/backend 거버넌스 강화 plan (`~/.claude/plans/quiet-hugging-bachman.md`) 의 **Cycle 1**. backend `/dev:analyze` 단계의 ANALYZE 템플릿에서 3가지 섹션 강제 효력 향상이 목표:

1. **"## 가정 및 미해결 질문"** 강화 (현재 ANALYZE+PLAN 모두 존재, 사람 자율 의존)
2. **"## 성공 기준 후보 (PLAN 변환 대상)"** 신규 (현재 PLAN §4.2 만 정의, ANALYZE 단계 부재)
3. **"## 도메인 룰 4영역 점검"** 신규 (현재 가정 표에 산재, 전용 섹션 부재)

ROOT `swtp/.claude/rules/coding-discipline.md §1·§4.2` 의 강제 효력을 backend `/dev:analyze` 단계에서 형식적 충족·"없음" 단독 통과 차단 메커니즘으로 향상. 자동 차단 훅 신설은 보류 (`coding-discipline.md §7.1` 결정 유지) — 메인 Claude 자율 차단 (a 안).

외부 산출물: 없음. 본 작업은 plan 문서 기반.

## 회의록 (5인 회의 — Round 1 종결)

### 안건 1: ANALYZE "## 성공 기준 후보" 섹션 신규 도입의 ROOT §4.2 의미 분리 정합성
- 호출 에이전트: `wtp-glossary-manager`, `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: ROOT §4.2 는 PLAN 단계만 규율. ANALYZE 의 "후보" 신설은 의미 충돌 없음, backend 위임 자산(`templates.md`) 변경 범위 내. ROOT 갱신 불필요.
  - **wtp-backend-engineer**: 정합 확정. 단 §2.5 면책 우회 차단을 위해 "§2.5 면책은 정량 기준에만 적용, 본 섹션 검증 가능성 요건 면제 대상 외" 주석 명기 의무. 컬럼명을 "검증 명령 초안 (PLAN 에서 확정)" 으로 명시하면 PLAN §4.2 와 관계가 더 명확.
- **결론**: ANALYZE 에 "## 성공 기준 후보 (PLAN 변환 대상)" 섹션 신설. 표 컬럼: `후보 기준 | 검증 명령 초안 (PLAN 에서 확정)`. §2.5 면책 적용 배제 주석 명기 (블로커 회피).

### 안건 2: ANALYZE "## 도메인 룰 4영역 점검" 신규 섹션 + 4영역 정의 정확성
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 4영역 인용 근거 검증 결과:
    - 알람 4단계: plan 의 `§3·§5` 인용은 **오류** (블로커). `§3` 은 QUALITY 코드 SSOT, 알람 4단계 1차 정의는 `§5` 단독 (인바운드 중단 표 부기 노트)
    - 인터록 선행조건: `§2·§5` 정합
    - AI 운전 모드 이중 체계 + 강제 전환: `§5` 단독 정합
    - 이력 기록 의무: `§5` 단독 정합
  - "비해당" 단독 4건 차단 정책: (a)안 (모든 ANALYZE 4행 의무) → 룰/하네스 작업 false positive 빈발. (b)안 변형 권고 — 사유 병기 + 신규 엔티티/DB 컬럼 "없음" 두 조건 동시 충족 시 블로커 해제.
- **결론**: 4영역 인용 근거 정정 (`알람 4단계 → ot-integration.md §5` 단독). "비해당" 단독 4건 블로커 해제 조건 = 구체 사유 명기 + "## 신규 엔티티/DB 컬럼" 섹션 "없음".

### 안건 3: `dev/plan.md` §5b 전제조건 검증 강화 (모호 표현 정규식)
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: plan 의 정규식 4건 (`성능 개선`·`안정성 향상`·`개선`·`향상`) 외에 추가 보강 후보 4건 (`기능 추가`·`코드 개선`·`리팩토링`·`문서화` — 단독 행 시). 단독 행 완전 매칭 (`^...$`) 유지 시 검증 명령 포함 행은 통과하므로 false positive 위험 낮음.
  - PLAN 단계 자율 차단(섹션 존재 + 모호 단독 행) 과 REVIEW 자동 점검 (TASK 체크박스 `→ 검증:` 누락) 의 책임 경계 명시 필요. 같은 문서 같은 항목 중복 검증 시 Fix Cycle 불필요 유발 위험.
- **결론**: 모호 표현 정규식 8건으로 확장. PLAN 자율 차단/REVIEW 자동 점검 책임 경계를 `dev/plan.md` §5b 와 `wtp-backend-engineer.md` 양쪽에 명시.

### 안건 4: `wtp-domain-expert` 책임 강화 (자기 정의 변경)
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 4행 점검 + 가정 섹션 교차 검증 = 응답 50~80단어 추가, 단답형 200~400단어 제약 내 수용 가능. "비해당 근거 타당성" 자동 판정 기준: 구체 사유 명기 + "## 신규 엔티티/DB 컬럼" 섹션 "없음" 두 조건 동시. "해당" 표기 후 "근거 또는 영향" 컬럼 공란/`확인 필요` 수준은 형식적 충족 패턴이므로 블로커 추가.
- **결론**: REVIEW 점검 책임에 4영역 신규 섹션 추가. 형식적 충족 패턴 2종 ("비해당" 단독 / "해당" + 영향 공란) 모두 블로커 등급. 가정 섹션 교차 검증 명시.

### 안건 5: 본 변경의 ROOT vs backend 결정 범위 판정
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: ROOT `coding-discipline.md §5` 표가 "5종 산출물 템플릿" 을 backend 위임 자산으로 명시. §5.1 갱신 진입 조건 (§1~§5 본문 변경 또는 ROOT 어휘 사전 신규 등록) 미해당. 본 변경은 §4.2 본문 수정 없이 ANALYZE 전단계 섹션 추가이므로 backend 자체 결정 범위.
- **결론**: **(a) backend 자체 결정 범위** 확정. ROOT `coding-discipline.md` 갱신 사이클 선행 불필요. ROOT 어휘 사전 (`standard-words.md` 등) 갱신 0건.

## 표준 사전 카탈로그

### 신규 표준 단어
없음 — 본 작업은 룰/하네스 변경. "후보"·"점검" 등 신규 한국어 서술 단어는 DB 컬럼 조합 재료 아니므로 ROOT `standard-words.md` 등재 대상 외.

### 신규 표준 데이터 도메인
없음.

### 신규 표준 용어
없음.

## 신규 엔티티/DB 컬럼

없음 — 본 작업은 룰 본문(`templates.md`) + slash command 절차서(`dev/plan.md`) + 에이전트 정의 (`wtp-domain-expert.md`·`wtp-backend-engineer.md`) 변경만. 엔티티·DB 컬럼·테이블·인덱스 변경 0건.

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 해소책 (회의 결론과 일치) |
|---------|----------------------|
| plan 의 4영역 인용 근거 표에서 "알람 4단계 → `ot-integration.md §3·§5`" 표기 (블로커) | `§5` 단독으로 정정. `§3` 은 QUALITY 코드 SSOT 이며 알람 단계 정의 근거 아님 (wtp-domain-expert 검토). 본 ANALYZE 의 신규 4영역 점검 섹션·룰 갱신 지시서 모두 정정 적용 |

## PLAN 으로 전달할 결정 사항

### 변경 대상 파일 (확정 — plan §Cycle 1 + Round 1 보강)

1. **`backend/.claude/rules/process/doc-harness/templates.md`** (현재 376줄, ANALYZE 템플릿 라인 7~101)
   - 신규 섹션 "## 성공 기준 후보 (PLAN 변환 대상)" — 표 (`후보 기준 | 검증 명령 초안 (PLAN 에서 확정)`). `§2.5 면책 적용 배제` 주석 명기. 최소 1건 + "없음" 단독 금지.
   - 신규 섹션 "## 도메인 룰 4영역 점검" — 4행 표 (`영역 | 해당/비해당 | 근거 또는 영향`). 인용 근거: 알람 4단계 → `ot-integration.md §5` 단독 / 인터록 선행조건 → `§2·§5` / AI 운전 모드 → `§5` / 이력 기록 의무 → `§5`.
   - 기존 "## 가정 및 미해결 질문" 강화 — 주석에 "wtp-domain-expert 가 본 섹션 + 신규 4영역 섹션 교차 검토 (가정의 4영역 충돌 ↔ 4영역 표 '해당' 일관성)" 명시.

2. **`backend/.claude/commands/dev/plan.md`** §5b 전제조건 검증 강화 (메인 Claude 자율 차단)
   - "## 가정 및 미해결 질문" 보유 + 1건 이상 + "없음" 단독 차단
   - "## 성공 기준 후보" 보유 + 1건 이상 + 모호 표현 정규식 8건 단독 행 차단:
     `^성능 개선$` · `^안정성 향상$` · `^개선$` · `^향상$` · `^기능 추가$` · `^코드 개선$` · `^리팩토링$` · `^문서화$`
   - "## 도메인 룰 4영역 점검" 보유 + 4행 채움 + "비해당" 단독 4건 차단. **단 사유 병기 + "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족 시 차단 해제** (false positive 방지)
   - 위반 시 PLAN 작성 중단 + ANALYZE 보강 요청. 자동 차단 훅 미신설 (`coding-discipline.md §7.1` 보류 유지)
   - PLAN 자율 차단 vs REVIEW 자동 점검 책임 경계 주석 추가 (PLAN = 섹션 존재 + 모호 단독 행, REVIEW = TASK 체크박스 `→ 검증:` 누락)

3. **`backend/.claude/agents/wtp-domain-expert.md`** REVIEW 점검 책임 강화
   - "## 도메인 룰 4영역 점검" 신규 섹션 자체 점검 추가
   - 형식적 충족 패턴 2종 블로커 등급:
     - (a) "비해당" 단독 4건 + 사유 부재
     - (b) "해당" 표기 + "근거 또는 영향" 컬럼 공란 또는 `확인 필요` 수준
   - "비해당 근거 타당성" 판정 기준: 구체 사유 명기 + "## 신규 엔티티/DB 컬럼" 섹션 "없음"
   - 가정 섹션 교차 검증: 가정에 4영역 충돌 기재 ↔ 4영역 표 "해당" 일관성

4. **`backend/.claude/agents/wtp-backend-engineer.md`** REVIEW 자동 점검 책임 경계 명시 (안건 3 보강)
   - PLAN 자율 차단 항목 (3섹션 존재 검증 + 모호 표현 정규식) 과 중복 검증 금지 명시
   - REVIEW 자동 점검 책임 = TASK 체크박스 `→ 검증:` 누락 + `coding-discipline §2.1` 정량 기준 + `§2.5` 면책 인용 근거

### 적용 패턴

- 자동 차단 훅 미신설 (a 안 — `coding-discipline.md §7.1` 보류 결정 유지)
- ROOT `coding-discipline.md` 본문 변경 0건 (안건 5 결론)
- ROOT 어휘 사전 변경 0건 (안건 1 보조 결론)

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. wtp-domain-expert 가 본 섹션 + 신규 "## 도메인 룰 4영역 점검" 교차 검토.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 본 변경이 ROOT `coding-discipline.md §4.2` 와 의미 충돌 없음 | 결정 | wtp-glossary-manager 안건 5 결론 — backend 위임 자산 변경 범위 |
| 모호 표현 정규식 8건이 정수장 도메인 작업의 모호 표현 패턴을 충분히 포괄 | 가정 | 운영 사례 누적 후 보강 필요. 본 plan 범위 외 (별도 ANALYZE 트리거: 누락 사례 3건 누적) |
| "비해당" 단독 차단 해제 조건 (사유 병기 + 신규 엔티티/DB 컬럼 "없음") 이 도메인 안전 작업의 형식적 우회 방지 충분 | 가정 | wtp-domain-expert REVIEW 점검에서 "비해당 근거 타당성" 판정으로 보완. 운영 사례 누적 후 강화 검토 |
| PLAN 자율 차단 vs REVIEW 자동 점검 책임 경계 명시화 → 미해결 → 결정 변환 | 결정 | PLAN = 3섹션 존재 + 모호 단독 행, REVIEW = TASK `→ 검증:` 누락 + 정량 기준 + §2.5 인용 근거 |
| 자동 차단 훅 미신설 (a 안) 유지 적절 → 미해결 → 결정 변환 | 결정 | wtp-backend-engineer 결론. 우회 발생 사례 모니터링 후 §7.1 트리거 누적 시 별도 ANALYZE |

## 성공 기준 후보 (PLAN 변환 대상)

> 본 ANALYZE 자체가 도입하는 신규 섹션. dogfood 적용 — 본 사이클의 PLAN1.md 가 본 섹션을 검증 가능 형태로 변환하여 §4.2 의 PLAN "## 성공 기준 (검증 가능 형태)" 로 확정한다. **§2.5 면책 적용 배제** — 본 섹션의 검증 가능성 요건은 정량 기준 면책 영역이 아니다.

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 새 ANALYZE 템플릿 3섹션 도입 후 의도 위반 ANALYZE 작성 시 PLAN 진입 차단 | 의도적 위반 ANALYZE 작성 (예: "## 성공 기준 후보" 에 "성능 개선" 단독) → `/dev:plan` 시뮬레이션 → 차단 메시지 확인. 정상 ANALYZE 로 보강 후 PLAN 진입 성공 확인 |
| 모호 표현 정규식 8건이 단독 행 매칭 시 차단 | 정규식 8건 각각에 대해 단독 행 ANALYZE 작성 → 모두 차단. 검증 명령이 포함된 행 (예: "성능 개선 — `./gradlew test` 응답 시간 < 5분") → 통과 |
| "비해당" 단독 4건 차단 + 사유 병기 + 신규 엔티티/DB 컬럼 "없음" 시 해제 | 사유 부재 4행 → 차단. 사유 병기 + 신규 엔티티/DB 컬럼 "없음" → 통과. 사유 병기 + 신규 엔티티 1건 → 차단 |
| wtp-domain-expert REVIEW 단계에서 형식적 충족 2종 블로커 처리 | REVIEW dogfood — "해당" + 영향 공란 ANALYZE 작성 → REVIEW 블로커 등급 확인. 가정 섹션 4영역 충돌 기재와 4영역 표 "해당" 일관성 검증 |
| PLAN 자율 차단/REVIEW 자동 점검 중복 검증 0건 | 본 작업의 RESULT 단계에서 PLAN1.md 와 REVIEW1.md 의 검증 항목 비교 → 같은 문서 같은 항목 중복 검증 사례 0건 확인 |

## 도메인 룰 4영역 점검

> 본 ANALYZE 자체가 도입하는 신규 섹션. dogfood 적용. 4영역 인용 근거: [`backend/.claude/rules/ot-integration.md`](../../../.claude/rules/ot-integration.md) §2·§3·§5.

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 본 작업은 룰·하네스 변경 (`templates.md`·`dev/plan.md`·`wtp-domain-expert.md`·`wtp-backend-engineer.md`). 알람 임계값·전이 조건·복귀 조건 무접촉 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | OT 인터록 룰 변경과 무관. `pump_interlock_p` 테이블·`InterlockValidator` 클래스·PLC 송신 경로 모두 변경 없음 |
| AI 운전 모드 이중 체계 + 강제 전환 (`ot-integration.md §5`) | 비해당 | ANALYZE 템플릿·검증 절차 변경. AI 운전 모드 (`ai_drvn_mod`/`ai_mode_cd`) 또는 SCADA 수신 경로 무접촉 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 룰/하네스 변경. `pump_ctrl_h`·`ai_drvn_mod_h.transition_reason` 등 이력 기록 테이블·트리거 무접촉 |

> **"비해당" 단독 4건이지만 차단 해제 조건 충족**: (1) 각 행에 구체 사유 명기 (어떤 영역도 "해당 없음" 한 줄이 아님), (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음". wtp-domain-expert 안건 2 결론 — (b)안 변형 적용.

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

체크박스 형식 — 모두 backend 자체 결정 범위. ROOT 어휘 사전·ROOT `coding-discipline.md` 변경 0건.

- [x] `backend/.claude/rules/process/doc-harness/templates.md` — ANALYZE 템플릿에 "## 성공 기준 후보 (PLAN 변환 대상)" 섹션 신규 추가 (표 컬럼: `후보 기준 | 검증 명령 초안 (PLAN 에서 확정)`. §2.5 면책 적용 배제 주석 명기. 최소 1건 + "없음" 단독 금지)
- [x] `backend/.claude/rules/process/doc-harness/templates.md` — ANALYZE 템플릿에 "## 도메인 룰 4영역 점검" 섹션 신규 추가 (4행 표. 알람 4단계 인용 `§5` 단독, 인터록 `§2·§5`, AI 운전 모드 `§5`, 이력 기록 `§5`. "비해당" 단독 4건 차단 해제 조건 명기)
- [x] `backend/.claude/rules/process/doc-harness/templates.md` — 기존 "## 가정 및 미해결 질문" 섹션 주석에 "wtp-domain-expert 가 본 섹션 + 신규 4영역 섹션 교차 검토" 명시 강화
- [x] `backend/.claude/commands/dev/plan.md` — §5b 전제조건 검증에 ANALYZE 3섹션 자율 차단 추가 (가정 "없음" 단독 / 성공 기준 후보 모호 표현 정규식 8건 / 4영역 "비해당" 단독 4건 + 사유 병기 + 신규 엔티티/DB 컬럼 "없음" 해제 분기). PLAN 자율 차단 vs REVIEW 자동 점검 책임 경계 주석 추가
- [x] `backend/.claude/agents/wtp-domain-expert.md` — REVIEW 점검 책임에 "## 도메인 룰 4영역 점검" 섹션 추가. 형식적 충족 패턴 2종 ("비해당" 단독, "해당" + 영향 공란) 블로커 등급. "비해당 근거 타당성" 판정 기준 (구체 사유 + 신규 엔티티/DB 컬럼 "없음") 명기. 가정 섹션 교차 검증 추가
- [x] `backend/.claude/agents/wtp-backend-engineer.md` — REVIEW 자동 점검 책임 경계 명시 (TASK 체크박스 `→ 검증:` 누락 + §2.1 정량 기준 + §2.5 인용 근거 의무. PLAN 자율 차단 항목과 중복 금지)

## 산출물

- [계획안](../../../plan/20260506/analyze_section_enforcement/PLAN1.md) (룰 갱신 지시서 모두 `- [x]` 완료 + 사용자 승인 후 `/dev:plan` 자동 전이로 작성)
