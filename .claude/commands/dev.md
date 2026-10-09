# 개발 워크플로우 진입점 (1단계: 요청 구체화 + 규모 분류 + 자동 전이)

사용자의 작업 요청: $ARGUMENTS

---

## 수행 절차

### 1. 요청 구체화
`$ARGUMENTS`가 비어 있거나 모호하다면 다음을 확인하기 위해 질문한다:
- 변경하려는 기능/도메인이 무엇인가?
- 기대하는 결과물은 무엇인가?
- 영향받는 모듈(common/api/scheduler)은 무엇인가?

### 2. 기존 진행 중인 작업 확인
`docs/` 디렉토리를 스캔하여 $ARGUMENTS와 관련된 진행 중인 작업이 있는지 확인한다.
- `docs/plan/`, `docs/tasks/`, `docs/results/`, `docs/reviews/` 탐색

**Fix Cycle 감지 (최우선):**

Fix Cycle 감지 로직(REVIEW status·블로커 키워드 분기)의 단일 정의는 [`.claude/rules/process/doc-harness/README.md` §수정 사이클](../rules/process/doc-harness/README.md#수정-사이클-fix-cycle) 의 "Fix Cycle 감지 알고리즘 (의사 코드)" 박스를 따른다. 본 단계는 알고리즘 결과를 받아 자동 전이한다:

- Fix Cycle 진입 시 사용자에게 "Fix Cycle N+1: {슬러그}의 블로커를 해소하기 위한 수정 사이클을 시작합니다." 안내
- 규모 분류·슬러그 재확정 단계(아래 §3·§4)를 건너뛰고 즉시 알고리즘이 반환한 단계(`/dev:analyze` 또는 `/dev:plan`)로 자동 전이
- 이번 사이클에서 생성할 PLAN·RESULT·REVIEW 문서는 번호를 +1 증가한다 (PLAN2, RESULT2, REVIEW2). TASK 는 PLAN{N} 과 대응하며 분할 여부를 새로 결정한다 (단일이면 TASK2, 분할이면 TASK2-1·TASK2-2)

**일반 진행 중인 작업 감지:**
- Fix Cycle 조건이 아니면서 동일한 목적의 작업이 발견되면 사용자에게 알리고 이어서 진행할지 확인

### 3. 작업 규모 분류
CLAUDE.md의 "작업 규모 분류" 기준을 참고하여 분류한다:

| 규모 | 기준 |
|------|------|
| **Small** | 단일 파일 수정, 버그 픽스, 간단한 리팩토링 |
| **Medium** | 단일 도메인 기능 추가/수정 |
| **Large** | 다중 도메인, 아키텍처 변경, 대규모 재개발 |

분류 근거를 명확히 제시한다. 사용자가 이견이 있으면 조정한다.

### 4. 목적 슬러그 생성
작업 목적을 snake_case로 표현한 슬러그를 생성한다.
- 한국어 허용: `jwt_인증_추가`, `pump_도메인_리팩토링`
- 영문도 가능: `user_auth_filter`, `batch_job_setup`
- 짧고 의미가 명확해야 한다 (20자 이내 권장)
- 사용자에게 제안하고 확인받는다

### 5. 워크플로우 안내 출력
규모에 따라 실행할 커맨드 순서를 안내한다. 오늘 날짜(YYYYMMDD)와 슬러그를 함께 표시한다.

**Small 작업 흐름:**
```
/dev:impl {슬러그}    → 구현 및 테스트
/dev:commit {슬러그}  → 커밋
```

**Medium 작업 흐름:**
```
/dev:analyze {슬러그} → 도메인 분석 (5인 회의, 승인 필요)
/dev:plan {슬러그}    → 계획 수립 (승인 필요)
/dev:task {슬러그}    → 작업 분해 (확인 필요)
/dev:impl {슬러그}    → 구현 및 테스트
/dev:commit {슬러그}  → 커밋
```

**Large 작업 흐름:**
```
/dev:analyze {슬러그} → 도메인 분석 (5인 회의, 승인 필요)
/dev:plan {슬러그}    → 계획 수립 (승인 필요)
/dev:task {슬러그}    → 작업 분해 (확인 필요)
/dev:impl {슬러그}    → 구현 및 테스트
/dev:result {슬러그}  → 결과 정리
/dev:review {슬러그}  → 코드 리뷰 (블로커 없어야 진행)
/dev:commit {슬러그}  → 커밋
```

> 각 커맨드는 전제조건을 자체 검증한다. 단계를 건너뛰면 경고가 표시된다.

### 6. 자동 전이 — 첫 번째 단계 즉시 실행

워크플로우 안내 후, 규모에 따라 **첫 번째 단계를 자동으로 실행**한다.
단순히 안내만 하고 멈추지 않는다.

| 규모 | 자동 전이 대상 | 동작 |
|------|--------------|------|
| **Small** | `/dev:impl` | 즉시 구현 단계로 진입 |
| **Medium** | `/dev:analyze` | 5인 회의 (메인 Claude 오케스트레이터 + 4 에이전트: wtp-dba-reviewer · wtp-backend-engineer · wtp-domain-expert · wtp-glossary-manager) 오케스트레이션 시작 → ANALYZE 문서 작성. 승인 후 `/dev:plan` 자동 전이. **반드시 `docs/analyze/{날짜}/{슬러그}/ANALYZE1.md` 와 `docs/plan/{날짜}/{슬러그}/PLAN1.md` 파일을 생성**해야 한다. |
| **Large** | `/dev:analyze` | 동일 |

**⚠️ 중요: plan 모드와 PLAN 문서의 관계**

plan 모드(Claude의 내장 plan 파일)는 대화 내 작업 계획 도구이다.
`docs/plan/` 경로의 PLAN 문서는 프로젝트에 영구 보존되는 산출물이다.
**두 가지는 별개이며, PLAN 문서 생성은 생략할 수 없다.**

plan 모드에서 계획을 수립하더라도, 해당 내용을 반드시 `docs/plan/{날짜}/{슬러그}/PLAN1.md`로 작성해야 한다.
PLAN 문서가 `docs/plan/`에 존재하지 않으면 후속 단계(`/dev:task`, `/dev:impl`)에서 **차단**된다.

### 7. 단계 완료 시 자동 전이

각 단계의 승인 요건·다음 단계·자동 실행 규칙은 [`.claude/rules/process/doc-harness/transitions.md`](../rules/process/doc-harness/transitions.md) 표 단일 정의를 따른다.

**핵심 원칙**: `/dev:commit` 은 항상 사용자 명시적 승인이 필요하며 자동 실행하지 않는다.
