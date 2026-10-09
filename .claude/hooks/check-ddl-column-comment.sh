#!/usr/bin/env bash
# Claude Code PostToolUse 훅: DDL CREATE TABLE 컬럼 COMMENT 누락 차단
# exit 0 = 허용, exit 2 = 차단 (stderr → Claude 피드백)
#
# 규약: db/migration/ 하위 *.sql 파일의 모든 CREATE TABLE 컬럼은
#       동일 파일 내에 COMMENT ON COLUMN {table}.{column} IS '...' 가 작성되어야 한다.
#       BaseEntity 4컬럼(rgstr_dtm/updt_dtm/rgstr_id/updt_id) 도 포함.
# 우회: DDL_SKIP_COMMENT_CHECK=1
# 참조: .claude/rules/db/indexing-and-migration.md §4
# 주의: Claude Code 세션 내 Write/Edit 시에만 동작. 터미널/IDE 직접 편집에는 미적용.
# 갱신 이력: 2026-05-20 sql_관리포인트_통합 — db/init/ 디렉토리 폐지에 따른 매칭 경로 정리.

set -uo pipefail

# ── 우회 환경변수 ──
[ "${DDL_SKIP_COMMENT_CHECK:-}" = "1" ] && exit 0

input=$(cat)

# ── 파일 경로 추출 ──
# sed 의 BRE 로 추출 (grep -P 는 Windows git-bash 등 비-UTF8 locale 에서 작동 불가).
file_path=$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
[ -z "$file_path" ] && exit 0

# ── 대상: db/migration/ 하위 *.sql ──
[[ "$file_path" != *.sql ]] && exit 0
case "$file_path" in
    */db/migration/*) ;;
    *) exit 0 ;;
esac

# ── 파일이 존재하지 않으면 통과 ──
[ ! -f "$file_path" ] && exit 0

# ── 블록 주석 사전 제거 (`/* ... */`) ──
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
sed -E ':a;$!{N;ba;};s|/\*[^*]*\*+([^/*][^*]*\*+)*/||g' "$file_path" > "$tmp"

# ── 컬럼 수집 ──
declare -A all_table_columns
declare -a table_order   # 출력 순서 보존용
current_tbl=""
in_block=0
columns_buf=""

while IFS= read -r line || [ -n "$line" ]; do
    # 라인 주석 제거 (-- 이후)
    cleaned=$(printf '%s\n' "$line" | sed 's|--.*||')

    # CREATE TABLE 진입 (인라인 형식 `CREATE TABLE [IF NOT EXISTS] name (` 만 대응)
    if [ $in_block -eq 0 ] \
        && [[ "$cleaned" =~ ^[[:space:]]*CREATE[[:space:]]+TABLE[[:space:]]+(IF[[:space:]]+NOT[[:space:]]+EXISTS[[:space:]]+)?([a-z_][a-z0-9_]*)[[:space:]]*\( ]]; then
        current_tbl="${BASH_REMATCH[2]}"
        in_block=1
        columns_buf=""
        continue
    fi

    # 블록 내부 처리
    if [ $in_block -eq 1 ]; then
        # 블록 종료 (` ) ;` 또는 ` ) PARTITION BY ...`)
        if [[ "$cleaned" =~ ^[[:space:]]*\) ]]; then
            # 중복 등록 방지 (같은 테이블 재선언 케이스 — 마지막 선언만 유효)
            if [ -z "${all_table_columns[$current_tbl]+x}" ]; then
                table_order+=("$current_tbl")
            fi
            all_table_columns["$current_tbl"]="$columns_buf"
            in_block=0
            current_tbl=""
            columns_buf=""
            continue
        fi

        # 첫 토큰 추출 → 컬럼 후보
        first_word=$(printf '%s\n' "$cleaned" | awk '{print $1}' | tr -d ',')
        case "$first_word" in
            ""|CONSTRAINT|PRIMARY|FOREIGN|UNIQUE|CHECK|KEY|EXCLUDE|LIKE) continue ;;
        esac
        # 대문자 시작 토큰은 키워드로 간주, 컬럼 후보에서 제외
        if [[ "$first_word" =~ ^[A-Z] ]]; then
            continue
        fi
        # snake_case 컬럼명만 인정
        if [[ "$first_word" =~ ^[a-z][a-z0-9_]*$ ]]; then
            columns_buf+="$first_word "
        fi
    fi
done < "$tmp"

# ── COMMENT ON COLUMN 매칭 ──
declare -A missing_per_table
total_missing=0
for tbl in "${table_order[@]}"; do
    cols="${all_table_columns[$tbl]}"
    [ -z "$cols" ] && continue   # 컬럼 0개 (자식 파티션 등) 는 스킵
    for col in $cols; do
        # 동일 파일 내에 COMMENT ON COLUMN tbl.col IS ... 매칭
        if ! grep -qiE "^[[:space:]]*COMMENT[[:space:]]+ON[[:space:]]+COLUMN[[:space:]]+${tbl}\.${col}[[:space:]]+IS[[:space:]]+" "$tmp"; then
            missing_per_table["$tbl"]+="$col "
            total_missing=$((total_missing + 1))
        fi
    done
done

[ $total_missing -eq 0 ] && exit 0

# ── 차단 ──
{
    echo ""
    echo "[DDL 컬럼 COMMENT 누락] ${file_path}"
    echo "  총 ${total_missing} 건의 컬럼에 COMMENT ON COLUMN 이 누락되어 있습니다."
    echo ""
    for tbl in "${table_order[@]}"; do
        cols="${missing_per_table[$tbl]:-}"
        [ -z "${cols// }" ] && continue
        echo "  테이블 ${tbl}:"
        for c in $cols; do
            echo "    - ${tbl}.${c}"
        done
    done
    echo ""
    echo "  규약: db/migration/ 하위 SQL 의 모든 CREATE TABLE 컬럼은"
    echo "        동일 파일 내에 COMMENT ON COLUMN {table}.{column} IS '...' 가 작성되어야 한다."
    echo ""
    echo "  BaseEntity 4컬럼 표준 라벨:"
    echo "    COMMENT ON COLUMN {table}.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';"
    echo "    COMMENT ON COLUMN {table}.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';"
    echo "    COMMENT ON COLUMN {table}.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';"
    echo "    COMMENT ON COLUMN {table}.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';"
    echo ""
    echo "  도메인 컬럼 라벨 패턴: '{한글 논리명} ({단위 / DOM_* 코드 / 부연 — 선택})'"
    echo ""
    echo "  우회 (사용자 명시 요청 시에만): DDL_SKIP_COMMENT_CHECK=1"
    echo "  참조: .claude/rules/db/indexing-and-migration.md §4"
    echo ""
} >&2
exit 2
