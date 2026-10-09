---
status: approved
created: 2026-04-29
updated: 2026-04-29
---
# ai-server LLM 코딩 디시플린 적용 — 도메인 분석

## 작업 배경

backend 직전 작업 `llm_coding_discipline` (커밋 `8240b4a`) 이 ROOT `swtp/.claude/rules/coding-discipline.md` 를 신규 작성하고 backend `wtp-domain-expert` 에 검토 항목 4번 (ANALYZE/PLAN 가정 섹션 도메인 점검) 을 추가했다. 그 작업의 [REVIEW1](../../../reviews/20260429/llm_coding_discipline/REVIEW1.md) §개선 제안 1·3 (ai-server 카피 동기화 + ai-server §5 시스템별 적용) 을 후속 작업으로 진행한다.

본 작업은 ai-server 영역의 cross-cutting 룰 변경이며, backend 의 `/dev:analyze` 5인 팀 회의로 검토한다 (ROOT 룰의 §5.1 갱신 절차 — backend ANALYZE + 사용자 승인 의무).

**외부 산출물**: 없음 (사전 plan: `~\.claude\plans\generic-forging-acorn.md` — REVIEW1 4건 개선 제안의 후속 작업 분류·우선순위가 사실상의 요구사항).

**Group 분류 (사전 plan)**:
- 본 ANALYZE = Group A (ai-server 동기화 + §5 적용)
- Group B (Glob 출력 해석 가이드) 는 별도 작업 (`analyze_glob_path_guidance` 후속)
- Group C (자동 차단 훅 도입 모니터링) 는 즉시 작업 없음

---

## 회의록 (4인 팀 토픽 주도 1라운드)

> Round 1 만으로 종결. 안건 2·4 가 룰 위치(별도 파일 vs 인용만) 결정에서 글로사리 매니저 vs 백엔드 엔지니어 결론이 표면적으로 충돌했으나, 백엔드 엔지니어가 _legacy 코드 분석 기반 구체 근거(Python 60줄 + 면책 영역 2건) 를 제시해 오케스트레이터가 종합 결정. Round 2 불요.

### 안건 1: ai-server `wtp-domain-expert` 카피에 backend 신규 검토 항목 4번 추가 — 어떤 형식으로?

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **선택지 B 권장 (의미적 변형)**: ai-server 카피의 기존 5개 항목이 모두 "추론 결과 schema·전처리·후처리" 단위. backend 항목 4번은 "ANALYZE/PLAN 가정 섹션" = 문서 프로세스 수준이라 단순 이식(A) 시 책임 분리 흐림
  - 의미적 변형 항목 6번 텍스트: "ANALYZE/PLAN 가정 섹션 도메인 4영역 점검 + 추론 schema 설계 가정의 책임 분리 원칙(`ot-integration.md §6.6`) 충돌 점검"
  - 선택지 C (양쪽 다 — 항목 6 + 7) 는 중복 — 추론 schema 가정 점검은 이미 기존 항목 1~5 (schema 필드 검토) 범위에 포함됨
- **결론**: B 채택. ai-server 카피의 항목 6번을 의미적 변형 형태로 추가 (텍스트 초안은 글로사리 답변 그대로)

### 안건 2: ai-server 에 `coding-discipline.md §5 시스템별 적용` — 어떤 형식으로?

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **Python 50줄 → 60줄 상향 조정 권고**: Python 은 줄당 정보 밀도가 Java 보다 높음 (타입 선언 없음·세미콜론 없음·중괄호 없음). docstring 본문 카운트 시 5~10줄 추가. Ruff `line-length = 120` 도 1줄 정보량 증가
  - **§2.5 면책 영역 ai-server 추가 필요**: 후보 2건
    1. **모델 추론 단일 흐름**: 전처리(NaN·shape·scaler) → `predict()` → 역변환 → 결과 검증. 분해 시 역변환 전 단위 검증 누락 위험
    2. **이상 탐지 특징 추출 파이프라인**: FFT → SVM → rule 가중치 → 임계값 비교. 분해 시 중간 상태 전파 오류
  - **선택지 A 권장 (자체 룰 파일 신규 작성)**: ROOT 본문 불변 유지 + Python 60줄·면책 영역 2건 기록 공간 확보 + 추론 패턴 룰(SRP 분리). 파일명 `swtp/ai-server/.claude/rules/coding-discipline.md`
  - 추상화 3단·Pydantic 상속 3단 = backend 와 동일 적용. Pydantic 은 composition 선호라 3단 초과 사례 드묾
  - **적용 시기 — Phase 3 신규 추론 함수 작성 시점**: Phase 2 (501 스텁) 단계에는 위반 대상 코드 없음. `_legacy/` 원본은 적용 범위 외, 이식하는 시점부터 적용
- **결론**: A 채택. ai-server 자체 룰 파일 신규 작성 (Python 60줄·면책 영역 2건 명시). Phase 3 시점 적용

### 안건 3: ROOT `coding-discipline.md §5` 표 ai-server 행 갱신

- 호출 에이전트: 없음 (안건 2 결과 종합으로 자동 결정 — 메인 Claude 단독)
- Round 1 답변 요약:
  - 안건 2 의 결과 (ai-server 자체 룰 파일 신규 작성) 를 §5 표에 반영
  - 현재 텍스트: "(현재 본 룰 미적용 — 별도 ANALYZE 후 도입 검토)"
  - 갱신 후: "ai-server 자체 룰 파일 (`swtp/ai-server/.claude/rules/coding-discipline.md`) 로 위임 — ROOT §1~§4 적용, §2.1 정량 기준은 Python 60줄 상향 조정, §2.5 면책 영역 2건 (추론 단일 흐름·이상 탐지 파이프라인) 추가"
- **결론**: §5 표 ai-server 행을 위 텍스트로 갱신

### 안건 4: ai-server CLAUDE.md §외부 참조 룰 에 ROOT 인용 추가 위치/형식

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **선택지 A 권장 (§외부 참조 룰 표에 1줄 추가)**: ai-server CLAUDE.md 의 "단일 출처 — 중복 작성 금지" 원칙 준수. 글로사리는 본래 별도 파일 불요(B X) 입장이었으나 안건 2 의 백엔드 엔지니어 결론(자체 룰 신규 작성) 과 충돌 → **오케스트레이터 종합**: 자체 룰을 만드는 만큼 §외부 참조 룰 인용 1줄 + ai-server `.claude/rules/coding-discipline.md` 인용도 함께 § 내부 룰 섹션에 추가
  - 글로사리 권고 인용 텍스트: "**LLM 코딩 행동 규율 (4원칙 + 정량 기준 + 면책 조항)**: `swtp/.claude/rules/coding-discipline.md` (ROOT). §1~§4 는 Python 환경에 그대로 적용. §2.1 정량 기준은 ai-server 자체 룰(`.claude/rules/coding-discipline.md`) 의 Python 60줄 조정 적용. §2.5 면책 조항(정수장 안전 도메인 패턴·DB 쿼리)은 ai-server 범위 외이며, ai-server 자체 면책 영역 (추론 단일 흐름·이상 탐지) 으로 대체"
- **결론**: ai-server CLAUDE.md §외부 참조 룰 에 위 텍스트 추가 + § 내부 룰 섹션의 5번째 항목으로 자체 `coding-discipline.md` 추가

---

## 표준 사전 카탈로그

> 본 작업은 룰/에이전트/CLAUDE.md 변경이며 코드 식별자·DB 컬럼명 신규 등장 0건. 4층 사전 갱신 대상 0건 — `wtp-glossary-manager` Round 1 결론.

### 신규 표준 단어
없음

### 신규 표준 데이터 도메인
없음

### 신규 표준 용어
없음

---

## 신규 엔티티/DB 컬럼

없음. 본 작업은 코드 변경 0건, DB 스키마 변경 0건. 엔티티·DB 컬럼 신규 도입 안건 없음.

---

## 기존 사전·패턴과의 충돌

### 블로커 (높음) — 0건

본 작업은 룰 위치/형식 변경이며 도메인 안전·표준 어휘에 미치는 영향 없음.

### 권고 (중간) — 1건

| 항목 | 위치 | 권고 내용 |
|------|------|---------|
| 글로사리 매니저 vs 백엔드 엔지니어 표면 충돌 | 안건 2 vs 안건 4 | 글로사리는 별도 파일 불요(단일 출처) 입장, 백엔드 엔지니어는 별도 파일 필요(60줄·면책 영역 고유) 입장 — 백엔드 엔지니어가 _legacy 코드 분석 기반 구체 근거 제시로 우세. 오케스트레이터 종합 결정으로 별도 파일 신설 + CLAUDE.md 인용 1줄 (글로사리 우려는 인용 텍스트에 "ai-server 자체 룰로 위임" 명시로 흡수) |

### 참고 (낮음) — 2건

| 항목 | 위치 | 참고 내용 |
|------|------|---------|
| Python 60줄 상향 조정 정합성 검증 | ai-server `coding-discipline.md` (예정) | 60줄 임계가 _legacy `Predict()` 80줄 사례 기반. 운영 1~2 사이클 후 재검토 필요 (실제 신규 추론 함수 평균 줄 수 측정) |
| Phase 3 적용 시점 운용성 | ai-server `coding-discipline.md` §적용 시기 절 | Phase 2 (501 스텁) 시점에는 룰 위반 대상 코드 없음. Phase 3 첫 추론 함수 이식 시점에 첫 시연 — 그 시점의 작업이 본 룰 적용 1차 검증 사례가 됨 |

---

## PLAN 으로 전달할 결정 사항

### A. ai-server `wtp-domain-expert` 카피 갱신

`swtp/ai-server/.claude/agents/wtp-domain-expert.md` 의 `## 검토 항목` 섹션 끝에 **6번** 추가 (5개 항목 그대로 보존, 의미적 변형):

```markdown
6. **ANALYZE/PLAN 가정 섹션 도메인 점검 + 책임 분리 원칙** — ANALYZE/PLAN 의 "## 가정 및 미해결 질문" 섹션이 도메인 4영역 (알람 4단계·인터록·운전 모드·이력 기록 의무) 과 충돌하는지, 그리고 추론 schema 설계 가정이 책임 분리 원칙(`ot-integration.md §6.6` — DB I/O 금지·스케줄링 Java 측 전담) 과 충돌하는지 점검. 가정 섹션이 비었거나 "없음" 으로 형식 충족된 경우, **블로커** 로 지적 (`coding-discipline.md §1` 적용)
```

### B. ai-server 자체 룰 파일 신규 작성

`swtp/ai-server/.claude/rules/coding-discipline.md` 신규 작성. 구조:

| 절 | 내용 |
|---|------|
| 머리말 | ROOT `swtp/.claude/rules/coding-discipline.md` 의 ai-server 적용 — §1~§4 적용 + §2.1 Python 60줄 상향 + §2.5 면책 영역 ai-server 정의 |
| §1 정량 기준 Python 적용 | ROOT §2.1 인용 + Python 60줄 조정 근거 (정보 밀도·docstring·Ruff line-length 120) + 추상화 3단·Pydantic 상속 3단 backend 동일 적용 |
| §2 ai-server §2.5 면책 영역 | 후보 2건 명시 — ① 모델 추론 단일 흐름 (전처리→predict→역변환→결과 검증), ② 이상 탐지 특징 추출 파이프라인 (FFT→SVM→rule→임계값). 인용 근거 명기 의무 (주석 `# §2.5 면책 (추론 단일 흐름)` 형태) |
| §3 적용 시기 | Phase 3 신규 추론 함수 작성 시점부터. Phase 2 (501 스텁) 적용 X. `_legacy/` 원본 적용 범위 외 |
| §4 ROOT 룰과의 관계 | ROOT §1·§3·§4 전체 적용. ROOT §2.5 의 정수장 안전 도메인·DB 쿼리 면책은 ai-server 범위 외 |

### C. ai-server CLAUDE.md 갱신

`swtp/ai-server/CLAUDE.md` 의 §외부 참조 룰 에 5번째 항목 추가:

```markdown
- **LLM 코딩 행동 규율 (4원칙 + 정량 기준 + 면책 조항)**: `swtp/.claude/rules/coding-discipline.md` (ROOT). §1~§4 는 Python 환경에 그대로 적용. §2.1 정량 기준은 ai-server 자체 룰(`.claude/rules/coding-discipline.md`) 의 Python 60줄 조정 적용. §2.5 면책 조항(정수장 안전 도메인 패턴·DB 쿼리)은 ai-server 범위 외이며, ai-server 자체 면책 영역 (추론 단일 흐름·이상 탐지) 으로 대체.
```

§ 내부 룰 (`.claude/rules/`) 섹션에 5번째 항목 추가:

```markdown
- [`coding-discipline.md`](.claude/rules/coding-discipline.md) — ROOT 룰의 ai-server 적용 (Python 60줄·면책 영역 2건)
```

### D. ROOT `coding-discipline.md §5` 표 갱신

`swtp/.claude/rules/coding-discipline.md` §5 표의 ai-server 행:

- 현재: `(현재 본 룰 미적용 — 별도 ANALYZE 후 도입 검토)` / `Python 환경의 50줄·3단 기준 적용 가능성은 별도 검증 필요`
- 갱신 후 적용 룰: `swtp/ai-server/.claude/rules/coding-discipline.md` (ai-server 자체 룰)
- 갱신 후 핵심 적용 지점: `ROOT §1~§4 적용, §2.1 정량 기준 Python 60줄 상향 조정, §2.5 면책 영역 2건 추가 (추론 단일 흐름·이상 탐지 파이프라인)`

§폐기·갱신 이력 표에 1행 추가:
```
| 2026-04-29 | §5 표 ai-server 행 갱신 (적용 위임) | ai-server 자체 룰 파일 신설 (`docs/analyze/20260429/ai_server_discipline_apply/ANALYZE1.md`). REVIEW1 §개선 제안 1·3 후속 작업 |
```

### E. 적용 패턴

- **ai-server 자체 룰 파일 신설로 단일 출처 원칙 보존**: ROOT 본문 불변 유지, Python 고유 차이는 ai-server 룰에 집중
- **6번 검토 항목은 의미적 변형으로 추가**: backend 카피의 "ANALYZE/PLAN 가정" 점검을 ai-server 의 "추론 schema 설계 가정 + 책임 분리" 로 변형 (단순 이식 X)
- **Phase 3 시점 적용 시연**: 첫 추론 함수 이식 작업이 본 룰의 첫 검증 사례가 됨

---

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md §1`](../../../../../.claude/rules/coding-discipline.md) 적용. dogfooding — 본 ANALYZE 자체에 본 작업으로 강화하는 가정 섹션 패턴 적용.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| ai-server `wtp-domain-expert` 카피의 항목 6번 의미적 변형 텍스트가 ai-server 자체 `/dev:analyze` 워크플로우 첫 사이클에서 작동 — 검증되지 않음 | 가정 | Phase 3 첫 추론 함수 이식 작업의 ai-server ANALYZE 단계에서 첫 시연 |
| Python 60줄 상향 조정의 적정성 — _legacy `Predict()` 80줄 사례 기반이지만 신규 추론 함수가 동일 길이 패턴인지 미검증 | 가정 | 운영 1~2 사이클 후 재검토. 60줄 도달 빈도가 비정상적이면 재조정 안건 (ai-server 자체 ANALYZE) |
| §2.5 면책 영역 후보 2건 (추론 단일 흐름·이상 탐지 파이프라인) 의 실제 적용 사례 — Phase 2 (501 스텁) 시점에 시연 불가 | 가정 | Phase 3 이식 시점에 첫 인용 사례 발생 시 패턴 정착 |
| backend ANALYZE 가 ai-server 룰 변경을 결정하는 것의 거버넌스 정합성 — ROOT 룰 §5.1 갱신 절차는 backend ANALYZE 의무이지만, ai-server 자체 룰 파일 신설은 ai-server `/dev:analyze` 가 결정해야 한다는 반론 가능 | 미해결 | 본 작업 한정으로 backend ANALYZE 가 결정 (ROOT §5.1 의무 + ai-server 적용은 ROOT 결정의 종속). 향후 ai-server 자체 룰 추가 변경은 ai-server `/dev:analyze` 가 1차 책임 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

> 본 체크박스는 PLAN 의 전제조건 검증용. 실제 작성은 IMPL 단계.

### 어휘 사전 갱신 (없음)

- 신규 표준 단어·표준 데이터 도메인·비즈니스 도메인 약어·표준 용어 등록 안건 0건. 어휘 사전 4개 파일 갱신 불필요.

### 룰 파일 갱신 (PLAN 단계에서 진행)

- [x] `swtp/ai-server/.claude/agents/wtp-domain-expert.md` — `## 검토 항목` 섹션 끝에 6번 항목 추가 (의미적 변형 — ANALYZE/PLAN 가정 섹션 점검 + 책임 분리 원칙)
- [x] `swtp/ai-server/.claude/rules/coding-discipline.md` — 신규 작성 (ROOT 인용 + Python 60줄 조정 근거 + §2.5 면책 영역 2건)
- [x] `swtp/ai-server/CLAUDE.md` — §외부 참조 룰 표에 1줄 추가 + § 내부 룰 섹션에 1줄 추가
- [x] `swtp/.claude/rules/coding-discipline.md` — §5 표 ai-server 행 갱신 (적용 위임) + §폐기·갱신 이력 표에 1행 추가

> 본 4건 체크박스가 모두 `[x]` 완료 상태로 사용자 승인 시 status: approved 전환.

---

## 산출물

- [계획안](../../../plan/20260429/ai_server_discipline_apply/PLAN1.md) — 본 ANALYZE 승인 후 작성 예정
