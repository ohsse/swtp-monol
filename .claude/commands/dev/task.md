# 3단계: 작업 분해 (TASK 문서 작성)

목적 슬러그: $ARGUMENTS

---

## 전제조건 검증
1. `$ARGUMENTS`가 비어 있으면 "목적 슬러그를 인자로 전달하세요. 예: `/dev:task jwt_인증_추가`" 안내 후 중단
2. PLAN 문서 탐색:
   - `docs/plan/` 하위에서 슬러그 `$ARGUMENTS`와 일치하는 디렉토리 탐색 (날짜 불문)
   - 같은 날짜 디렉토리 내에서 **가장 큰 번호**의 PLAN 문서(예: PLAN2.md > PLAN1.md)를 찾아 `status` 확인
   - `status: approved`가 아니면 → "계획이 아직 승인되지 않았습니다. `/dev:plan $ARGUMENTS`를 먼저 실행하고 승인받으세요." 경고 후 중단
3. PLAN 문서의 날짜를 기준 날짜로 사용 (TASK도 동일 날짜 디렉토리에 생성)
4. 현재 PLAN 번호가 N이면 TASK{N}을 생성한다 (PLAN1→TASK1, PLAN2→TASK2)
4-1. **LARGE 작업 분할 판정**: 분할 기준은 [`.claude/rules/process/doc-harness/templates.md` §TASK 분할 기준](../../rules/process/doc-harness/templates.md#task-분할-기준-large-전용) 단일 정의를 따른다. 사용자가 분할에 동의하면 TASK{N}-1, TASK{N}-2, ... 복수 파일로 생성하고, 미동의 시 TASK{N}.md 단일 파일로 생성한다.
5. `docs/tasks/{날짜}/$ARGUMENTS/` 디렉토리 탐색:
   - TASK{N} 또는 TASK{N}-1 문서가 이미 있으면 이어서 수정할지 확인

## TASK 문서 작성

PLAN 문서 내용을 읽고 구체적인 Phase/Task 목록으로 분해한다.
- 경로: `docs/tasks/{PLAN날짜}/$ARGUMENTS/TASK1.md`

**분해 기준:**
- Phase는 독립적으로 완료 가능한 작업 단위로 구성
- 각 Task는 단일 파일 또는 단일 메서드 수준의 구체적 작업
- 체크박스 형식으로 작성 (`- [ ] Task 설명`)
- 의존 관계가 있는 Task는 같은 Phase에 묶기

**TASK 분할 시 추가 작업:**
- 각 분할 파일 상단에 "## 관련 분할 TASK" 섹션으로 형제 TASK를 상호 링크한다
- 분할 경계는 계층 의존 순서대로 정하며, 사용자와 합의한 Phase 묶음을 기준으로 한다
- 분할된 각 파일은 독립적인 프론트매터(`status: draft`)와 "## 관련 계획" 링크를 포함한다

**TASK 문서 템플릿**: 단일 게시 위치는 [`.claude/rules/process/doc-harness/templates.md`](../../rules/process/doc-harness/templates.md) 의 `TASK{n}.md` 블록(SSOT) 이다. 본 스킬은 그 템플릿을 그대로 사용하며 내용을 복제하지 않는다.

## 확인 요청

문서 작성 완료 후 절차는 [`.claude/rules/process/doc-harness/transitions.md`](../../rules/process/doc-harness/transitions.md) 표의 `/dev:task` 행을 따른다.

요약: `status: draft → review` 전환 → 사용자에게 Phase/Task 목록 검토 요청 → 확인 시 `status: approved` 전환 후 `/dev:impl $ARGUMENTS` **자동 실행**.
