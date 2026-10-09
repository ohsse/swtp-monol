# 7단계: 커밋 (최종 승인 및 Git 반영)

목적 슬러그: $ARGUMENTS

---

## 전제조건 검증
1. `$ARGUMENTS`가 비어 있으면 "목적 슬러그를 인자로 전달하세요. 예: `/dev:commit jwt_인증_추가`" 안내 후 중단

2. **문서 완전성 검증** — 작업 규모에 따라 필수 문서가 모두 존재하는지 확인한다:

   먼저 `docs/` 하위에서 슬러그 `$ARGUMENTS`와 일치하는 문서들을 탐색한다:
   - `docs/plan/` → PLAN 문서 존재 여부
   - `docs/tasks/` → TASK 문서 존재 여부
   - `docs/results/` → RESULT 문서 존재 여부
   - `docs/reviews/` → REVIEW 문서 존재 여부

   **규모별 필수 문서:**

   | 규모 | PLAN | TASK | RESULT | REVIEW |
   |------|------|------|--------|--------|
   | Small | - | - | - | - |
   | Medium | 필수 | 필수 | - | - |
   | Large | 필수 | 필수 | 필수 | 필수 |

   **규모 판단 기준:**
   - PLAN/TASK 문서가 모두 없으면 → Small
   - PLAN/TASK 문서가 있고 RESULT/REVIEW가 없으면 → Medium (RESULT/REVIEW 불필요)
   - RESULT 또는 REVIEW 문서가 있으면 → Large

   **차단 조건 (커밋 진행 불가):**
   - Medium인데 PLAN 문서가 없으면 → **"PLAN 문서가 누락되었습니다. `/dev:plan $ARGUMENTS`를 실행하세요." 경고 후 중단**
   - Medium인데 TASK 문서가 없으면 → **"TASK 문서가 누락되었습니다. `/dev:task $ARGUMENTS`를 실행하세요." 경고 후 중단**
   - Large인데 RESULT 문서가 없으면 → **"RESULT 문서가 누락되었습니다. `/dev:result $ARGUMENTS`를 실행하세요." 경고 후 중단**
   - Large인데 REVIEW 문서가 없거나 `status: approved`가 아니면 → **"REVIEW가 완료되지 않았습니다. `/dev:review $ARGUMENTS`를 실행하세요." 경고 후 중단**

   누락된 문서가 있으면 사용자에게 누락 목록을 표로 보여주고, 해당 단계 실행을 안내한다.

3. REVIEW 문서가 존재하는 경우 추가 검증:
   - `status: approved`가 아닌 경우 → "리뷰에 블로커가 있습니다. `/dev:review $ARGUMENTS`를 확인하세요." 경고

## 미완료 작업 파일 교차 검증

현재 커밋 대상 슬러그(`$ARGUMENTS`) 이외의 `status != completed` TASK 에 속한 파일이 staging 에 포함되면 pre-commit 훅이 자동 unstage 한다. 본 스킬이 수동으로 같은 작업을 중복 수행하지 않는다.

- 훅 동작 흐름·매처·체크박스 경로 파싱·우회(`GIT_SKIP_DOC_CHECK=1`) 상세: [`.claude/rules/process/hooks-guide.md`](../../rules/process/hooks-guide.md) §3 참조
- 훅이 발동되지 않는 환경(터미널·IDE 직접 커밋, 세션 외)에서는 수동 확인 필요 — 미완료 작업 파일이 staging 에 있으면 `git reset HEAD -- <파일>` 로 unstage

훅 실행 결과:
- 남은 staged 파일 0건 → `exit 2` 로 커밋 차단. 미완료 작업을 완료하거나 사용자 명시 요청 시 `GIT_SKIP_DOC_CHECK=1 git commit -m "..."` 로 우회
- 남은 staged 파일이 있으면 변경된 커밋 범위를 사용자에게 제시하고 계속 진행

## 최종 테스트 실행

커밋 전 최종 테스트를 실행한다:
```bash
./gradlew.bat test
```
테스트 실패 시 → "테스트가 실패했습니다. 구현을 수정한 뒤 다시 시도하세요." 안내 후 중단

## 커밋 준비

다음 정보를 수집하여 커밋 내용을 사용자에게 제시한다:

**변경 사항 확인:**
```bash
git status
git diff --stat HEAD
```

**스테이징 파일 결정:**
- 코드 파일: 구현한 Java 소스, 리소스 파일
- 문서 파일: `docs/{plan,tasks,results,reviews}/{날짜}/$ARGUMENTS/` 하위 파일
- 제외: `.env`, 민감 정보 파일, IDE 설정 파일 (`.idea/`, `*.iml` 등)

**커밋 메시지 초안 작성** (CLAUDE.md 커밋 규칙):
```
{prefix}: {한국어 설명}

예시:
feat: JWT 인증 필터 추가
fix: 펌프 스케줄러 NPE 버그 수정
refactor: 에너지 소비 도메인 패키지 구조 개선
```

접두사 선택 기준:
- `feat`: 새 기능
- `fix`: 버그 수정
- `refactor`: 코드 구조 개선
- `docs`: 문서 변경
- `chore`: 빌드/설정 변경
- `test`: 테스트 추가/수정

## ⚠️ 사용자 명시적 승인 필수

다음 내용을 사용자에게 표시하고 **반드시 명시적 승인을 기다린다**:

```
=== 커밋 준비 완료 ===

[스테이징할 파일]
{git diff --stat 출력}

[커밋 메시지]
{초안 메시지}

커밋하려면 "커밋" 또는 "commit"으로 응답하세요.
커밋 메시지를 수정하려면 원하는 메시지를 직접 입력하세요.
```

사용자가 승인하면 커밋을 실행한다. 승인 없이는 절대 커밋하지 않는다.

## 커밋 실행

사용자 승인 후:
```bash
git add {스테이징 파일 목록}
git commit -m "$(cat <<'EOF'
{커밋 메시지}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
git status
```

## PR 생성 여부 확인

커밋 완료 후 PR 생성 여부를 확인한다:
- 필요하면 `gh pr create` 로 PR 생성
- PR 제목: 커밋 메시지와 동일 (한국어)
- PR 본문: PLAN/RESULT 문서 링크 포함

자동 전이 규칙은 [`.claude/rules/process/doc-harness/transitions.md`](../../rules/process/doc-harness/transitions.md) 표의 `/dev:commit` 행을 따른다 — `/dev:commit` 은 자동 전이 대상이 아니다. 커밋/PR 완료 후 사용자에게 다음을 안내한다:

```
작업이 완료되었습니다.

[선택] frontend 로 API 명세를 전파하려면 `/dev:spec $ARGUMENTS` 를 실행하세요.
- Controller·DTO·ErrorCode 가 변경되어 frontend 인터페이스에 영향을 줄 때만 작성합니다.
- 산출물 위치: `swtp/frontend/docs/api-specs/$ARGUMENTS/SPEC{N}.md`
- 라이프사이클: backend cycle 과 독립적으로 SPEC 번호가 누적됩니다.
```

`/dev:spec` 은 사용자가 명시적으로 호출하는 경우에만 실행한다. 자동 실행 금지.
