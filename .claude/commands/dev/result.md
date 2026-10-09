# 5단계: 결과 정리 (RESULT 문서 작성)

목적 슬러그: $ARGUMENTS

---

## 전제조건 검증
1. `$ARGUMENTS`가 비어 있으면 "목적 슬러그를 인자로 전달하세요. 예: `/dev:result jwt_인증_추가`" 안내 후 중단
2. TASK 문서 탐색 (`docs/tasks/` 하위에서 슬러그 일치):
   - 같은 날짜 디렉토리 내에서 **가장 큰 cycle 번호**의 TASK 문서 그룹을 찾는다 (단일이면 `TASK{N}.md`, 분할이면 `TASK{N}-1.md`, `TASK{N}-2.md`, ...).
   - TASK 그룹 내 **모든** 파일이 `status: completed`여야 한다. 하나라도 미완료이면 → "구현이 완료되지 않았습니다. `/dev:impl $ARGUMENTS`를 먼저 실행하세요." 경고
3. 기준 날짜: PLAN/TASK 문서의 날짜를 재사용
4. 현재 TASK cycle 번호가 N이면 RESULT{N}을 생성한다 (TASK1(또는 TASK1-k)→RESULT1, TASK2(또는 TASK2-k)→RESULT2)
5. `docs/results/{날짜}/$ARGUMENTS/` 탐색:
   - RESULT{N} 문서가 이미 있으면 이어서 업데이트할지 확인

## 결과 문서 작성

다음 정보를 수집하여 RESULT 문서를 작성한다:

**변경 사항 수집:**
```bash
git diff --stat HEAD          # 변경된 파일 목록
git diff --name-only HEAD     # 파일 경로 목록
```

**작성 내용:**
- 변경된 파일 목록과 각 파일의 변경 성격 (신규/수정/삭제)
- 추가/변경된 클래스, 메서드, 엔티티 목록
- 실행한 테스트와 결과 (통과/실패 건수)
- 계획 대비 실제 구현의 차이점 (있을 경우)
- 특이 사항, 기술 부채, 후속 작업 필요 항목

**RESULT 문서 템플릿**: [`.claude/rules/process/doc-harness/templates.md`](../../rules/process/doc-harness/templates.md) 의 `RESULT{n}.md` 블록(SSOT)을 그대로 사용한다. 본 커맨드에서는 다음 치환만 적용:

- `작업목적` → `$ARGUMENTS`
- `YYYYMMDD` → 기준 날짜 (PLAN/TASK 재사용)
- `{N}` → 현재 fix cycle 번호 (최초 사이클 1, 첫 fix cycle 2 ...)
- TASK 분할 시 `## 관련 작업` 의 태스크 링크는 `TASK{N}-1.md`, `TASK{N}-2.md` 등 분할 파일을 각각 추가
- `## TASK 규모` 표는 doc-harness 템플릿 그대로 채워 작성

## 완료 후 자동 전이

문서 작성 완료 후 절차는 [`.claude/rules/process/doc-harness/transitions.md`](../../rules/process/doc-harness/transitions.md) 표의 `/dev:result` 행을 따른다.

요약: `status: completed` 설정 → `/dev:review $ARGUMENTS` **자동 실행**.
