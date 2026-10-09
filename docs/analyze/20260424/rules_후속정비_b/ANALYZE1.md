---
status: approved
created: 2026-04-24
updated: 2026-04-24
---
# 룰 후속 정비 묶음 B — 도메인 분석

## 작업 배경

- REVIEW1 (`docs/reviews/20260424/rules_dedup_정리/REVIEW1.md`) 의 `## 개선 제안` 7건 중 묶음 B 로 이월된 2건 진행:
  - **제안 2**: `.claude/rules/` 하위 14개 룰 파일 중 "참조 문서 관계" 표 미보유인 7개 의미 있는 추가 대상에 일관 양식 적용
  - **제안 3**: `wtp-backend-engineer` · `wtp-dba-reviewer` · `wtp-glossary-manager` 3개 에이전트 정의 본문을 룰 파일 재기술에서 참조 지시문 기반으로 단순화 (`wtp-domain-expert` 는 룰 1차 정의 부재로 **대상 제외**)
- 직전 묶음 A 완료 커밋: `96649f1 chore: 룰 후속 정비 — CLAUDE.md 인덱스 2건 · naming.md 표 삭제 · dev:plan SSOT 위반 해소`
- 외부 산출물: 없음 (메타 룰 구조 정비 작업)
- 본 작업은 신규 도메인 용어·엔티티·DB 컬럼이 **0건** 이므로 표준 사전 카탈로그 3표는 "없음" 으로 처리

### 선결 조사 결과 (claude-code-guide)

- Claude Code 에이전트 frontmatter 에 자동 파일 첨부 필드 **없음** (`attachedFiles`·`includes`·`context`·`readFiles` 모두 없음)
- `skills:` 필드는 스킬만 지원, 룰 파일 지원 안 함
- `PreToolUse`·`SessionStart` 훅으로 파일 자동 주입 불가
- **최선 전략**: 본문 축소 + "다음 파일을 먼저 읽어라" 명시적 지시문 + `tools:` 에 `Read` 포함

---

## 회의록 (5인 팀 토픽 주도)

### 안건 1: "참조 문서 관계" 표 양식 표준화

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**:
    - 표 양식 — **2열 유지** (`문서` / `이 문서와의 관계`). 관계 유형(상위/하위/관련) 열 추가는 작성 비용 증가 대비 정보 이득 낮음. 관계 설명 자연어에 이미 방향 포함
    - 위치 — **최상단 고정** (제목 + 한 줄 소개 + 구분선 `---` 직후, §1 앞). 파일 성격별 차등 배치는 인지 비용 유발. `api-patterns.md` · `entity-patterns.md` 처럼 제목 직후 코드 블록 구조는 구분선으로 표 영역을 격리
    - 7개 파일별 핵심 관계 매핑 초안 제공 (안건 §PLAN 으로 전달할 결정 사항 참조)
    - 블로커 0건
- Round 2: 이견 없음 — 불실행
- **결론**: 현 6개 보유 파일의 2열 양식을 그대로 7개 추가 대상에 적용. 위치는 파일 최상단 고정.

### 안건 2: `wtp-*` 3개 에이전트 정의 본문 단순화 전략

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**:
    - 유지 항목 — (a) 역할 경계 선언(룰 파일에 없는 다른 에이전트와의 책임 분리), (b) 출력 형식 템플릿(회의록 삽입 계약)
    - 축소 대상 — §1~§N 체크리스트 상세. 기준: "룰 파일을 Read 하면 동일 정보를 얻을 수 있는가" → Yes 면 항목 제목 1줄 + 파일 링크로 대체
    - `tools:` 필드 — **명시 권장**. 기능상 미명시여도 모든 도구 상속되나, `tools: [Read, Grep]` 로 선언적 의도 표현
    - Read 지시문 양식 — 본문 상단에 "## 검토 시작 전 필수 파일 읽기" 섹션 신설. "읽지 않은 상태에서 판정하지 않는다" 강제 문구 포함
    - 목표 줄 수 — backend 81 → 30~35, dba 76 → 28~32, glossary 117 → 35~40
    - 출력 형식 — **반드시 유지** (ANALYZE 회의록 자동 판정 계약)
    - 권고 2건: `tools` 명시 권장 / "Read 전에 절대 판정 금지" 강제 문구
- Round 2: 이견 없음 — 불실행
- **결론**: 본문 축소는 (역할 + Read 지시문 + 항목명 + 출력 형식) 4개 블록으로 재구성. 체크리스트 상세는 룰 파일 링크로 대체. `tools: [Read, Grep]` 명시 추가. `wtp-domain-expert` 는 대상 제외.

---

## 표준 사전 카탈로그

### 신규 표준 단어

없음.

### 신규 표준 데이터 도메인

없음.

### 신규 표준 용어

없음.

> 본 작업은 메타 룰 구조 정비로 신규 용어·값 형식·DB 컬럼 등록이 0건이다. 3층 사전 카탈로그는 하위 호환 규칙 (`doc-harness.md §ANALYZE 표준 사전 카탈로그`) 에 따라 생략.

---

## 신규 엔티티/DB 컬럼

없음.

---

## 기존 사전·패턴과의 충돌

없음.

- 신규 단어·약어·용어·센서 코드·지자체 코드 모두 0건 → 표준 사전 충돌 없음
- 에이전트 단순화는 기존 패턴(출력 형식·역할 분리)을 유지하며 내부 본문만 축소 → 에이전트 간 계약 충돌 없음
- "참조 문서 관계" 표 양식은 기존 보유 6개 파일 양식을 그대로 확장 적용 → 스타일 충돌 없음

---

## PLAN 으로 전달할 결정 사항

### 1. "참조 문서 관계" 표 적용 범위 — 7개 룰 파일

| 파일 | 핵심 관계 (초안, PLAN 에서 확정) |
|------|-----------------------------|
| `.claude/rules/api-patterns.md` | `entity-patterns.md` / `exception-patterns.md` / `naming.md` / `dict/standard-data-domains.md` |
| `.claude/rules/entity-patterns.md` | `dict/standard-data-domains.md` / `naming.md` / `api-patterns.md` / `domain-abbreviations.md` |
| `.claude/rules/naming.md` | `dict/standard-words.md` / `dict/standard-data-domains.md` / `domain-abbreviations.md` / `entity-patterns.md` |
| `.claude/rules/exception-patterns.md` | `hooks-guide.md` / `api-patterns.md` / `naming.md` |
| `.claude/rules/commit-convention.md` | `hooks-guide.md` / `doc-harness.md` |
| `.claude/rules/hooks-guide.md` | `commit-convention.md` / `exception-patterns.md` / `doc-harness.md` |
| `.claude/rules/test-strategy-e2e-roadmap.md` | `test-strategy.md` / `db-partitioning-and-retention.md` / `ot-integration.md` |

- **표 양식 (고정)**:
  ```markdown
  ## 참조 문서 관계

  | 문서 | 이 문서와의 관계 |
  |------|----------------|
  | [`xxx.md`](xxx.md) | 관계 설명 한 줄 |
  ```
- **위치 (고정)**: 파일 최상단 제목 + 한 줄 소개 + 구분선 `---` 직후, §1 앞
- **금지**: 관계 유형 열 추가, 파일 성격별 차등 위치

### 2. `wtp-*` 3개 에이전트 정의 단순화 — 대상 3개, 제외 1개

| 에이전트 | 현재 | 목표 | 주 참조 룰 |
|---------|------|------|-----------|
| `wtp-backend-engineer.md` | 81행 | 30~35행 | `api-patterns.md` · `entity-patterns.md` · `exception-patterns.md` |
| `wtp-dba-reviewer.md` | 76행 | 28~32행 | `db-partitioning-and-retention.md` · `db-indexing-and-migration.md` · `db-query-tuning.md` · `dict/standard-data-domains.md` |
| `wtp-glossary-manager.md` | 117행 | 35~40행 | `dict/README.md` · `dict/standard-words.md` · `dict/standard-data-domains.md` · `dict/standard-terms.md` · `domain-abbreviations.md` · `naming.md` · `ot-integration.md` · `multi-tenant.md` |
| `wtp-domain-expert.md` | 56행 | **유지** | — (룰 1차 정의 부재 — 제외) |

- **축소 후 본문 4블록 구조**:
  1. `## 역할` — 역할 경계 선언 (룰 파일에 없는 책임 분리 기준)
  2. `## 검토 시작 전 필수 파일 읽기` — Read 지시문 + 파일 목록 + "읽지 않은 상태에서 판정하지 않는다" 강제 문구
  3. `## 검토 항목` — 항목 제목 1줄 + 해당 룰 파일 섹션 링크. 상세 체크리스트는 링크로 대체
  4. `## 출력 형식` — 현행 그대로 유지 (심각도 표·결론 카운트 — ANALYZE 회의록 자동 판정 계약)
- **frontmatter 변경**: `tools: [Read, Grep]` 명시 추가 (현재 3개 모두 미명시)
- **금지**: 출력 형식 임의 변경, `wtp-domain-expert` 축소 시도

### 3. 작업 순서·검증

- PLAN 에서 위 10개 파일 (룰 7 + 에이전트 3) 수정 범위·순서 확정
- TASK 에서 Phase 단위로 분할 (제안 2 Phase + 제안 3 Phase)
- IMPL 단계 검증:
  - 각 룰 파일의 새 "## 참조 문서 관계" 표 링크 유효성 수동 클릭 또는 grep
  - 각 에이전트 frontmatter 의 `tools: [Read, Grep]` 추가 확인
  - `./gradlew.bat build` 회귀 검증 (코드 변경 0건이므로 참고용)
  - pre-commit 훅 영향 없음 확인

---

## 룰 갱신 지시서

본 작업은 신규 용어·엔티티 등록이 0건이므로 **표준 사전·분산 사전의 1차 정의 갱신 대상 없음**.

수정 대상 10개 파일은 본 작업 자체의 **구현 대상** 이며 `/dev:impl` 단계에서 처리한다. 룰 갱신 지시서의 도메인 정합성 블로커 성격 체크박스 (3층 사전 등록 / 비즈니스 도메인 약어 추가 등) 는 해당 없음.

- [x] 3층 사전 (표준 단어·데이터 도메인·표준 용어) 갱신 해당 없음 — 신규 용어 0건
- [x] 비즈니스 도메인 약어 (`domain-abbreviations.md`) 갱신 해당 없음 — 신규 약어 0건
- [x] DB suffix 규칙 (`naming.md`) 갱신 해당 없음 — 신규 suffix 0건
- [x] 센서 코드 (`ot-integration.md`) 갱신 해당 없음 — 신규 센서 0건
- [x] 지자체 코드 (`multi-tenant.md`) 갱신 해당 없음 — 신규 프로파일 0건

> 실제 10개 파일 수정 체크박스는 TASK1.md 에 기록한다.

---

## 산출물

- [계획안](../../../plan/20260424/rules_후속정비_b/PLAN1.md)
