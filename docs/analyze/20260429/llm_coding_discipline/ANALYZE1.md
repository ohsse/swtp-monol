---
status: approved
created: 2026-04-29
updated: 2026-04-29
---
# LLM 코딩 디시플린 룰 도입 — 도메인 분석

## 작업 배경

외부 블로그(americanopeople.tistory.com/514) 의 LLM 코딩 가이드라인 4원칙 (구현 전 사고 / 단순성 우선 / 정밀한 수정 / 목표 중심 실행) 을 우리 `swtp/backend` 의 `/dev` 워크플로우 하네스에 적용 가능성을 검토하고, 보강 12건을 ROOT 와 backend 양쪽에 분산 도입하는 작업.

본 작업의 사전 분석은 이미 plan 파일에 상세 정리되어 있으며, ANALYZE 단계는 plan 결정의 도메인·용어 정합성 1차 검증을 담당한다.

**사전 plan 파일** (사용자 승인 완료): `~\.claude\plans\llm-virtual-lake.md`

**사전 plan 의 핵심 결정**:
- 적용 범위: 옵션 C — 보강 12건 전부
- 자동 강제 수준: REVIEW 자동 점검 (신규 자동 차단 훅은 신설 보류)
- 배치 전략: ROOT (`swtp/.claude/rules/`) 신규 1개 + backend 보강 8개

**외부 산출물**: 없음 (plan 파일이 사실상의 요구사항). docx·xlsx·다이어그램 첨부 없음.

---

## 회의록 (4인 팀 토픽 주도 1라운드)

> Round 1 만으로 종결. 블로커 1건은 회의 중단 없이 §기존 사전·패턴과의 충돌 에 기록 후 다음 안건 진행.

### 안건 1: 신규 ROOT 룰 `coding-discipline.md` 파일명·구조·용어 정합성

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **파일명 적합**: ROOT 기존 영문 케밥케이스 패턴 (`standard-words.md` 등) 과 일치. 대안 `code-quality.md`(품질 측정 뉘앙스), `coding-principles.md`(동의어) 보다 "discipline(규율)" 이 LLM 가이드라인 원전 용어를 직접 반영
  - **dict/ 와 책임 충돌 없음**: dict/ 3층 사전은 코드·DB 식별자 어휘 SSOT, coding-discipline.md 는 LLM 행동 규율 — 관장 영역 완전 분리
  - **룰 본문 한국어 표현은 표준 단어 등록 불필요**: "디시플린"·"단순성 우선"·"복잡도 과잉"·"정량 기준" 은 룰 문서 자연어이며 코드/DB 식별자가 아님. `standard-words.md` 의 등록 대상은 DB 컬럼 조합 재료에 한정 (사용 규칙 1조)
  - **단일 파일 유지 권고**: §1~§4 가 단일 책임(LLM 행동 규율) 공유. 분리 인덱스 패턴 진입 기준은 200줄 + 주제 1개 초과 시
- **결론**: 파일명·위치·책임 분리 모두 적합. 단, `swtp/backend/CLAUDE.md §규칙 문서 인덱스` 에 신규 행 추가 필수 (참조 체계 연결)

### 안건 2: 도메인 안전성 충돌 (정량 기준 vs 인터록·알람·운전 모드)

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **블로커 발견**: "단순성 우선" 정량 기준 (메서드 50줄·추상화 3단) 이 인터록 선행조건 검사·알람 4단계 평가·운전 모드 강제 전환 메서드와 구조적 충돌. `InterlockValidator.validateOrThrow()` 같은 안전 검증 메서드는 복수 규칙 순회 + 센서 종류별 분기로 50줄 자연 초과. SCADA 5분 초과 강제 전환 로직도 `last_rcv_dtm` 비교 → `ai_mode_cd` 갱신 → `ai_drvn_mod_h` INSERT → 5종 `transition_reason` 분기로 추상화 3단 자연 초과
  - **위험**: 면책 조항 없이 도입 시 REVIEW 자동 지적이 안전 코드를 분해 압박 → 인터록 검사 단계 누락 (`ot-integration.md §5 ⚠️ 절대 금지`) 위험 증가
  - **양립 조건**: coding-discipline.md 본문에 **"정수장 안전 도메인 패턴(인터록·알람 4단계·모드 강제 전환·이력 기록 의무) 은 정량 기준 예외"** 면책 조항 명시 필수
  - "## 가정 및 미해결 질문" 섹션 의무화는 도메인 가정 충돌 조기 발견에 실효적. 단, "없음"/빈 칸 방지를 위해 **최소 1건 이상 기재** 를 ANALYZE 승인 전제조건에 추가 권고
  - `wtp-domain-expert` 점검 범위 명시 필요: 알람 4단계·인터록·운전 모드·이력 기록 의무
- **결론** (블로커 해소 방향): coding-discipline.md §2 단순성 우선 절에 "정량 기준 면책 조항" 하위 절 신설 필수. ANALYZE 템플릿의 가정 섹션은 최소 1건 기재 강제

### 안건 3: 정량 기준 회귀 영향 + 단답형 점검 가능성 + 훅 파싱 충돌

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **회귀 영향 거의 없음**: 73개 Java 파일 직접 검토 결과, 50줄 초과 메서드 0건 (`PumpControlService.executeControl` 30줄 등), DTO 상속 0건 (`extends *Dto` 미사용), 추상화 3단 초과 0건 (Service → Repository → JPA 단일 흐름)
  - 따라서 **신규 코드부터 적용 (grandfather clause)** 으로 충분, 소급 리팩토링 불필요
  - **RESULT "### 계획 외 변경" 절 호환**: 기존 RESULT "## 변경 사항" 은 자유 형식이라 새 하위 절 추가는 구조 파괴 아님. 기존 `status: completed` 문서는 소급 재작성 근거 없음 → 신규부터 적용
  - **단답형 점검 가능**: 4개 항목 (단순성·TASK 외 변경·검증 누락·데드 코드) 표 형태 병렬 보고로 단일 호출 압축 가능. 단, 메서드 길이 점검 시 코드 read 가 선행되므로 컨텍스트 소비 증가
  - **훅 파싱 충돌 없음**: `check-task-unstage.sh` 의 `sed -n 's/^- \[.\] \`\([^\`]*\)\`.*/\1/p'` 는 첫 백틱만 추출, `→ 검증:` 텍스트는 `.*` 로 무시. 단 **검증 영역에 백틱 금지** 명시 필요 (두 번째 백틱이 첫 백틱 닫는 쌍으로 오인 가능)
- **결론**: 도입 부담 낮음. checkbox-rules.md 갱신 시 "검증 영역 백틱 금지" 명시. RESULT 변경은 신규부터 적용

### 안건 4: DB 무영향 확인 + DB 쿼리 코드 면책 검토

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **DB 직접 영향 없음 확인**: 룰 파일 9+1개 갱신, DDL·마이그레이션 SQL·인덱스 정의 변경 0건. `check-ddl-column-comment.sh` 훅 트리거 대상 (`db/init/`·`db/migration/`) 변경 없음 → 자동 차단 미발동
  - RESULT 명시: "## DB 설계 변경: 없음 (룰 파일 전용 작업, DDL·인덱스·파티션 변경 0건)" 한 줄로 충분
  - **권고 (안건 2 의 면책 조항과 보강)**: QueryDSL 복잡 집계 빌더·MyBatis 동적 SQL Mapper·`EXPLAIN ANALYZE` 분석 절차도 단일 메서드 50줄 자연 초과 → coding-discipline.md 면책 조항에 **"DB 쿼리 빌더·튜닝 코드"** 도 명시 권고
  - **db/ 룰 충돌 없음**: db/ 3개 룰 (PostgreSQL 운영 기준) 과 coding-discipline.md (Java 코드 작성 방식) 관심사 교차 없음
  - **DBA 추가 점검 안건 없음**: `standard-data-domains.md` 신규 `DOM_*` 등록 0건, 기존 13개 데이터 도메인 영향 0건
- **결론**: DB 무영향 확정. 면책 조항 범위 확장 권고 (안건 2 결론과 통합)

---

## 표준 사전 카탈로그

### 신규 표준 단어
없음

### 신규 표준 데이터 도메인
없음

### 신규 표준 용어
없음

> 본 작업은 룰/프로세스 변경이며 코드 식별자·DB 컬럼명 신규 등장 0건. 4층 사전 갱신 대상 0건 — `wtp-glossary-manager` Round 1 결론.

---

## 신규 엔티티/DB 컬럼

없음. 본 작업은 코드 변경 0건, DB 스키마 변경 0건. 엔티티·DB 컬럼 신규 도입 안건 없음 — `wtp-dba-reviewer` Round 1 확인.

---

## 기존 사전·패턴과의 충돌

### 블로커 (높음) — 1건

| 항목 | 위치 | 충돌 내용 | 해소책 |
|------|------|---------|--------|
| 정량 기준 vs 도메인 안전 패턴 | coding-discipline.md (예정) §2 단순성 우선 ↔ `ot-integration.md §3·§4·§5` | 메서드 50줄·추상화 3단 정량 기준이 인터록 검사·알람 4단계 평가·강제 모드 전환 같은 안전 검증 메서드와 구조적으로 충돌. 면책 조항 없이 도입 시 REVIEW 자동 지적이 안전 코드를 분해 압박 → `ot-integration.md §5 ⚠️ 절대 금지` 와 정면 충돌 | coding-discipline.md §2 에 **"정량 기준 면책 조항"** 하위 절 신설. 면책 대상: ① 정수장 안전 도메인 패턴 (인터록·알람·강제 전환·이력 기록), ② DB 쿼리 빌더·튜닝 코드 (QueryDSL·MyBatis·EXPLAIN 분석) |

### 권고 (중간) — 1건

| 항목 | 위치 | 권고 내용 |
|------|------|---------|
| ANALYZE 가정 섹션 형식 전락 방지 | `process/doc-harness/templates.md` ANALYZE 템플릿 | "## 가정 및 미해결 질문" 의무화만으로는 "없음" 기재 방지 불가. **최소 1건 이상 기재 + wtp-domain-expert 확인** 을 ANALYZE 승인 전제조건에 추가 |

### 참고 (낮음) — 3건

| 항목 | 위치 | 참고 내용 |
|------|------|---------|
| 검증 영역 백틱 금지 | `checkbox-rules.md` 신규 절 | TASK 체크박스 `→ 검증: ...` 확장 시, 검증 텍스트에 백틱 사용하면 훅 파싱 오작동 가능 → 명시 금지 |
| DTO 상속 미사용 | `api-patterns.md` 예시 vs 실제 코드 | 실제 코드에 DTO 상속 0건 — "3단 초과 제한" 기준은 미래 대비용 |
| `wtp-domain-expert` 점검 범위 미세화 | coding-discipline.md REVIEW 항목 정의 | "가정 항목 도메인 충돌 여부" 점검 시 알람 4단계·인터록·운전 모드·이력 기록 의무 4개 영역을 명시 열거 |

---

## PLAN 으로 전달할 결정 사항

### A. ROOT 신규 파일 도입

`swtp/.claude/rules/coding-discipline.md` 신규 작성. §1~§5 구성:

| 절 | 내용 | 비고 |
|---|------|------|
| §1 구현 전 사고 | 가정 명시·모호함 노출·트레이드오프 표기·불분명 시 중단 후 질문 | plan B-1·B-2 |
| §2 단순성 우선 | 메서드 50줄 / 추상화 3단 / DTO 상속 3단 정량 기준, 시니어 자문 체크 | plan B-3·B-4·B-5 |
| **§2.5 정량 기준 면책 조항** | **(블로커 해소)** ① 정수장 안전 도메인 패턴 (인터록·알람·강제 전환·이력 기록), ② DB 쿼리 빌더·튜닝 코드 (QueryDSL·MyBatis·EXPLAIN 분석) — 면책. 면책 코드도 시니어 자문 체크는 적용 | **신규 추가** (안건 2·4 결론) |
| §3 정밀한 수정 | 본인 변경 부수물만 정리. 기존 데드 코드는 보고만 (직접 삭제 금지) | plan B-8 |
| §4 목표 중심 실행 | TASK 체크박스 `→ 검증: ...` 패턴, 버그 재현 테스트 first | plan B-9·B-10·B-11 |
| §5 본 룰의 시스템별 적용 | backend `/dev` 워크플로우 매핑은 backend 룰 참조 (분리 인덱스 패턴 안내) | — |

### B. backend 보강 (plan 그대로)

| 파일 | 변경 |
|------|------|
| `process/doc-harness/templates.md` | ANALYZE/PLAN 에 "## 가정 및 미해결 질문" 섹션 (최소 1건 기재 의무 명시), TASK 체크박스 `→ 검증:` 확장, PLAN 의 "## 테스트 전략" → "## 성공 기준 (검증 가능 형태)", RESULT 의 "### 계획 외 변경" 신설, REVIEW 분류에 "복잡도 과잉" |
| `process/doc-harness/checkbox-rules.md` | "검증 기준 표기 형식" 절 신설 + **검증 영역 백틱 금지 명시** (안건 3) |
| `process/hooks-guide.md` | "## 본 시점 자동 차단 훅 신설 보류" 절 신설 (정합성 추적) |
| `test-strategy.md` | §1 단위 테스트 절에 "버그 수정 첫 체크박스 = 재현 테스트 RED 확인" 의무 |
| `swtp/.claude/agents/wtp-backend-engineer.md` (ROOT) | 점검 항목 4개 추가 (단순성·TASK 외 변경·검증 누락·데드 코드 직접 삭제) |
| `swtp/.claude/agents/wtp-domain-expert.md` (ROOT) | 점검 항목 1개 추가 — **알람 4단계·인터록·운전 모드·이력 기록 4영역 명시** (안건 2) |
| `swtp/backend/CLAUDE.md` | §규칙 문서 인덱스 에 ROOT `coding-discipline.md` 신규 행 추가 (안건 1) |

### C. 적용 패턴

- **신규 코드부터 적용 (grandfather clause)**: 기존 코드 회귀 영향 거의 없음 (50줄 초과 0건·DTO 상속 0건·추상화 3단 초과 0건). 소급 리팩토링 불필요
- **RESULT 신규부터 적용**: 기존 `status: completed` RESULT 문서 소급 재작성 안 함

---

## 가정 및 미해결 질문

> 본 ANALYZE 자체에도 본 작업으로 도입할 "가정 섹션" 패턴을 적용하여 자기참조 검증 (dogfooding).

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| coding-discipline.md §2.5 면책 조항이 향후 면책 남용으로 이어지지 않을 것 — REVIEW 단계에서 면책 인용 시 인용 근거 (`ot-integration.md §X` 등) 명시를 강제 | 가정 | PLAN 단계에서 §2.5 면책 인용 형식을 구체화 |
| `wtp-domain-expert` 점검 항목 추가 시 단답형 200~400단어 제약 내 4영역 모두 점검 가능 | 가정 | 실제 운영 1~2 사이클 후 검증 (REVIEW 호출 응답 길이 측정) |
| ai-server 의 `wtp-domain-expert` 카피와 ROOT 본체 갱신 시점 차이 — 본 작업은 ROOT 만 갱신 | 미해결 | 별도 동기화 안건 (`docs/analyze/.../ai_server_agent_sync` 등) 으로 후속 |
| TASK 체크박스 `→ 검증:` 확장이 기존 작성 중인 TASK 문서와 호환 — 신규부터 적용 | 가정 | PLAN 단계에서 명시 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

### 어휘 사전 갱신 (없음)

- 신규 표준 단어·표준 데이터 도메인·비즈니스 도메인 약어·표준 용어 등록 안건 0건. 어휘 사전 4개 파일 갱신 불필요.

### 룰 파일 갱신 (PLAN 단계에서 진행)

> 본 체크박스는 PLAN 의 전제조건 검증용. 본 ANALYZE 의 status: approved 전제조건은 아래 항목들이 PLAN 작업으로 처리 가능함을 사용자가 확인하는 것 (실제 작성은 IMPL 단계).

- [x] `swtp/.claude/rules/coding-discipline.md` — 신규 파일 작성 (§1~§5, **§2.5 면책 조항 포함**)
- [x] `swtp/backend/CLAUDE.md` — §규칙 문서 인덱스 에 ROOT `coding-discipline.md` 신규 행 추가
- [x] `swtp/backend/.claude/rules/process/doc-harness/templates.md` — ANALYZE/PLAN/TASK/RESULT/REVIEW 5종 템플릿 보강 (가정 섹션·검증 형식·계획 외 변경·복잡도 과잉)
- [x] `swtp/backend/.claude/rules/process/doc-harness/checkbox-rules.md` — "검증 기준 표기 형식" 절 신설 (백틱 금지 명시)
- [x] `swtp/backend/.claude/rules/process/hooks-guide.md` — "본 시점 자동 차단 훅 신설 보류" 절 신설
- [x] `swtp/backend/.claude/rules/test-strategy.md` — §1 단위 테스트 절에 "버그 수정 첫 체크박스 = 재현 테스트 RED 확인" 의무 추가
- [x] `swtp/.claude/agents/wtp-backend-engineer.md` — 점검 항목 4개 추가
- [x] `swtp/.claude/agents/wtp-domain-expert.md` — 점검 항목 1개 추가 (4영역 명시)

> ai-server 의 `wtp-domain-expert` 카피 동기화는 본 작업 범위 외 — 별도 안건으로 후속.

---

## 산출물

- [계획안](../../../plan/20260429/llm_coding_discipline/PLAN1.md) — 본 ANALYZE 승인 후 작성 예정
