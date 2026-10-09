#!/usr/bin/env bash
# Claude Code PreToolUse 훅: git commit 시 미완료 TASK 파일 자동 unstage
# exit 0 = 허용, exit 2 = 차단 (stderr → Claude 피드백)
#
# 동작: docs/tasks/ 하위 미완료(status != completed) TASK 문서를 파싱하여,
#       해당 작업에 속한 파일이 staging에 있으면 자동 unstage한다.
#       남은 staged 파일이 없으면 커밋을 차단한다.
# 부가 경고: TASK 체크박스의 백틱 경로가 실제로 존재하지 않으면 stderr 에
#            "[경로 경고]" 로 알린다. 작성자 실수(축약 경로·글롭·오타·삭제된 파일)
#            를 조기 발견하는 용도이며 커밋은 차단하지 않는다.
# 우회: GIT_SKIP_DOC_CHECK=1 git commit -m "..."
# 주의: Claude Code 세션 내에서만 동작. 터미널/IDE 직접 커밋에는 미적용.
# 경로 기록 규칙: .claude/rules/process/doc-harness/checkbox-rules.md (TASK 체크박스 전체 경로 필수)

set -uo pipefail

# ── stdin 읽기 (PreToolUse는 도구 입력 JSON을 stdin으로 전달) ──
input=$(cat)

# ── git commit 명령이 아니면 즉시 통과 ──
echo "$input" | grep -q 'git commit' || exit 0

# ── 우회 환경변수 ──
[ "${GIT_SKIP_DOC_CHECK:-}" = "1" ] && exit 0

# ── 경로 설정 ──
REPO_ROOT="$(git rev-parse --show-toplevel)"
TASKS_DIR="$REPO_ROOT/backend/docs/tasks"
[ ! -d "$TASKS_DIR" ] && exit 0

# ── staged 파일 목록 수집 ──
staged_files=()
while IFS= read -r line; do
    [ -n "$line" ] && staged_files+=("$line")
done < <(git diff --cached --name-only)
[ ${#staged_files[@]} -eq 0 ] && exit 0

# ── 미완료 TASK 문서 파싱 → 매칭 파일 수집 ──
matches=()
while IFS= read -r -d '' task_file; do
    # YAML 프론트매터에서 status 추출
    status=$(awk 'BEGIN{n=0} /^---/{n++; if(n==2)exit} n==1 && /^status:/{print $2}' "$task_file")
    [ "$status" = "completed" ] && continue

    # 슬러그 추출: .../docs/tasks/{date}/{slug}/TASK*.md → {slug}
    task_slug=$(echo "$task_file" | sed 's|.*/docs/tasks/[^/]*/\([^/]*\)/.*|\1|')

    # 체크박스 파일 경로 추출 (명령어 제외)
    while IFS= read -r file_path; do
        [[ "$file_path" == ./* ]] && continue
        [[ "$file_path" == *gradlew* ]] && continue
        full="backend/$file_path"
        # 경로 존재성 검증 — 백틱 경로가 실제로 존재하지 않으면 작성자 실수 가능성
        # (축약 경로, 글롭, 삭제된 파일 등). 차단은 하지 않고 stderr 경고만 출력한다.
        if [ ! -e "$REPO_ROOT/$full" ]; then
            echo "[경로 경고] TASK '$task_slug' 에 존재하지 않는 경로: $file_path" >&2
            continue
        fi
        for s in "${staged_files[@]}"; do
            [ "$s" = "$full" ] && { matches+=("$full|$task_slug"); break; }
        done
    done < <(sed -n 's/^- \[.\] `\([^`]*\)`.*/\1/p' "$task_file" 2>/dev/null || true)
done < <(find "$TASKS_DIR" -name "TASK*.md" -print0 2>/dev/null)

[ ${#matches[@]} -eq 0 ] && exit 0

# ── 중복 제거 (한 파일이 여러 작업에 속하는 경우) ──
declare -A seen_files
unique_matches=()
for m in "${matches[@]}"; do
    file="${m%%|*}"
    [ -z "${seen_files[$file]+x}" ] && { seen_files["$file"]=1; unique_matches+=("$m"); }
done

# ── 자동 unstage + stderr 피드백 ──
{
    echo ""
    echo "[미완료 작업 파일 제외] 미완료 작업에 속한 파일을 staging에서 제외합니다."
    echo ""
    printf "  %-60s %s\n" "파일" "미완료 작업"
    printf "  %-60s %s\n" "------------------------------------------------------" "----------"
    for m in "${unique_matches[@]}"; do
        file="${m%%|*}"; task="${m##*|}"
        git reset HEAD -- "$file" 2>/dev/null
        printf "  %-60s %s\n" "$file" "$task"
    done
    echo ""
} >&2

# ── 남은 staged 파일 확인 ──
remaining=$(git diff --cached --name-only | wc -l | tr -d '[:space:]')
if [ "$remaining" -eq 0 ]; then
    echo "커밋 차단: 모든 staged 파일이 미완료 작업에 속합니다. 미완료 작업을 완료하거나 아래 방법으로 우회하세요:" >&2
    echo "  GIT_SKIP_DOC_CHECK=1 git commit -m '...'" >&2
    exit 2
fi

echo "[미완료 작업 파일 제외] 위 파일을 제외한 ${remaining}개 파일로 커밋을 진행합니다." >&2
exit 0
