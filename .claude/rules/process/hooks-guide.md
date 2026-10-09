# 훅 동작 가이드

Claude Code 세션에서 발동되는 pre/post tool use 훅의 트리거·차단 동작·우회 방법을 기술한다.
훅은 `.claude/settings.local.json` 에 등록되며, Claude Code 세션 외 (터미널·IDE 직접 호출) 에는 **적용되지 않는다**.

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`commit-convention.md`](commit-convention.md) | 커밋 시 훅 발동 조건과 우회 방법 소비처 |
| [`../exception-patterns.md`](../exception-patterns.md) | `check-errorcode-contract.sh` 훅이 강제하는 규약 원천 |
| [`doc-harness/checkbox-rules.md`](doc-harness/checkbox-rules.md) | TASK·ANALYZE 체크박스 파일 경로 파싱 규칙 1차 정의 |

---

## 1. 훅 인덱스

| 스크립트 | 트리거 | 매처 | 역할 | 차단 여부 |
|----------|--------|------|------|----------|
| [`check-task-unstage.sh`](../../hooks/check-task-unstage.sh) | `PreToolUse` | `Bash` | `git commit` 시 미완료 TASK 소속 파일 자동 unstage, 남은 파일 0건이면 차단 | 조건부 차단 (`exit 2`) |
| [`check-errorcode-contract.sh`](../../hooks/check-errorcode-contract.sh) | `PostToolUse` | `Write`, `Edit` | `*ErrorCode.java` 또는 `implements ErrorCode` 파일의 `String` 필드·`getMessage()` 선언 금지 | 항상 차단 (`exit 2`) |
| [`check-ddl-column-comment.sh`](../../hooks/check-ddl-column-comment.sh) | `PostToolUse` | `Write`, `Edit` | `db/migration/` 하위 `*.sql` 의 `CREATE TABLE` 컬럼 중 `COMMENT ON COLUMN` 누락 차단 | 항상 차단 (`exit 2`) |

---

## 2. 공통 규약

### 2.1 종료 코드 의미

| Exit | 의미 | 결과 |
|------|------|------|
| `0` | 허용 | 도구 실행 계속 |
| `2` | 차단 | 도구 실행 중단 + `stderr` 메시지를 Claude 피드백으로 전달 |

### 2.2 입력 방식

- 훅은 도구 입력 JSON 을 **stdin 으로** 전달받는다
- 훅이 표준 출력으로 낸 내용은 사용자에게 표시되고, **stderr 는 Claude 에이전트에 피드백**된다

### 2.3 Windows git-bash 환경 호환성 (file_path 추출)

Windows git-bash 의 기본 locale 이 비-UTF8 (`LANG`·`LC_ALL` 미설정) 인 경우 `grep -P` (PCRE) 가 다음 메시지와 함께 작동하지 않는다:

```
grep: -P supports only unibyte and UTF-8 locales
```

이 경우 file_path 추출이 빈 문자열을 반환해 훅이 즉시 `exit 0` 으로 빠져 **검증 자체가 수행되지 않는** false negative 가 발생한다.

**권장 회피책** (신규 훅 작성 시 적용):

```bash
# ❌ 환경 의존 — 비-UTF8 locale 에서 미작동
file_path=$(echo "$input" | grep -oP '"file_path"\s*:\s*"\K[^"]+' | head -1)

# ✅ sed BRE — locale 비의존, 모든 환경에서 작동
file_path=$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
```

대안으로 `export LC_ALL=C.UTF-8` 강제도 가능하나 sed 가 더 견고하다 (시스템에 `C.UTF-8` 미존재 시 fallback 필요).

> ✅ 기존 두 훅(`check-task-unstage.sh` · `check-errorcode-contract.sh`) 은 PCRE 미사용 — 파일 경로 추출은 sed BRE, 위반 검사는 `grep -E` ERE 로 변환 완료. 신규 훅 작성 시에도 본 회피책 의무 적용.

---

## 3. `check-task-unstage.sh` (PreToolUse / Bash)

### 3.1 동작 흐름

1. stdin JSON 에서 `git commit` 명령이 포함된 Bash 호출만 처리 (그 외 모두 `exit 0`)
2. 환경변수 `GIT_SKIP_DOC_CHECK=1` 설정 시 즉시 `exit 0`
3. `docs/tasks/` 하위 `TASK*.md` 중 `status != completed` 문서를 모두 찾아:
   - YAML 프론트매터에서 `status` 파싱
   - 체크박스(`- [ ]` / `- [x]`) 에서 백틱으로 감싼 파일 경로 추출 (§4.1 파싱 규칙)
   - staged 파일과 교차 비교 → 겹치면 `git reset HEAD --` 로 자동 unstage
4. unstage 후 남은 staged 파일이 0건이면 `exit 2` 로 커밋 차단

### 3.2 경로 경고 (차단 아님)

체크박스의 백틱 경로가 실제로 존재하지 않으면 `[경로 경고] TASK '{슬러그}' 에 존재하지 않는 경로: {경로}` 메시지를 stderr 로 출력한다. 축약 경로·글롭·오타·삭제된 파일 등 작성자 실수 조기 발견 목적이며 **커밋은 차단하지 않는다**.

### 3.3 우회

```bash
GIT_SKIP_DOC_CHECK=1 git commit -m "..."
```

사용자가 명시적으로 요청한 경우에만 사용한다. Claude 가 자동으로 우회해서는 안 된다.

---

## 4. TASK 체크박스 파일 경로 파싱 규칙

`check-task-unstage.sh` 는 TASK 문서를 `sed -n 's/^- \[.\] \`\([^\`]*\)\`.*/\1/p'` 로 파싱한다. 따라서 훅이 인식하려면 체크박스의 파일 경로를 **정확히** 기록해야 한다.

### 4.1 규칙 (doc-harness/checkbox-rules.md 1:1 대응표)

| 규칙 | `doc-harness/checkbox-rules.md` | 훅 동작 |
|------|----------------|--------|
| 전체 상대 경로 필수 | §TASK/ANALYZE 체크박스 파일 경로 기록 규칙 | 훅이 `backend/{경로}` 로 조합하여 staged 파일과 비교 |
| 축약 경로 금지 (예: `common/.../p6spy/...`) | 동일 조항 | 파싱 실패 or 파일 미존재 → `[경로 경고]` |
| 글롭 패턴 금지 (예: `resources-env/local/*`) | 동일 조항 | 파싱 실패 or 파일 미존재 → `[경로 경고]` |
| 명령어 항목은 `./` 로 시작 | 동일 조항 | `[[ "$file_path" == ./* ]]` 체크로 스킵 |
| `gradlew` 포함 항목 스킵 | — | `[[ "$file_path" == *gradlew* ]]` 체크로 스킵 |

ANALYZE 문서의 `## 룰 갱신 지시서` 체크박스도 동일 형식을 사용한다 (`/dev:plan` 의 전제조건 검증에서 파싱).

### 4.2 올바른/잘못된 예

```markdown
# 올바른 예
- [ ] `common/src/main/java/com/mo/swtp/common/p6spy/CustomP6SpySqlFormatter.java` 생성
- [ ] `.claude/rules/process/commit-convention.md` 신규 작성
- [ ] `./gradlew.bat clean build` 실행 성공 확인

# 잘못된 예
- [ ] `common/.../p6spy/CustomP6SpySqlFormatter.java` 생성   ← 축약 금지
- [ ] `api/src/main/resources-env/local/*` 설정 추가         ← 글롭 금지
```

---

## 5. `check-errorcode-contract.sh` (PostToolUse / Write·Edit)

### 5.1 동작 흐름

1. stdin JSON 에서 `file_path` 추출
2. 대상 조건: `*.java` 이면서 (파일명이 `*ErrorCode.java` 이거나 내부에 `implements ErrorCode` 포함)
3. 위반 검사:
   - `private final String \w+` 선언 발견 → 차단
   - `String getMessage()` 메서드 선언 발견 → 차단
4. 위반 시 `exit 2` 로 저장 차단 + stderr 로 규약·올바른 예·참조 룰 경로 안내

### 5.2 규약

- `ErrorCode` 구현 enum 은 `httpStatus(int)` 만 허용
- `CommonResponseDto` 에 `message` 필드가 없으며, `RestApiAdvice` 는 `errorCode.name()` 만 직렬화
- 사용자 표기 문구는 프론트엔드가 `errorCode.name()` 으로 명세 기반 매핑

상세 및 올바른 예는 [`../exception-patterns.md`](../exception-patterns.md) 참조.

### 5.3 우회

없음. 본 훅은 규약 위반을 항상 차단한다. 규약을 재논의하려면 `/dev:analyze` 단계에서 다뤄야 한다.

---

## 6. `check-ddl-column-comment.sh` (PostToolUse / Write·Edit)

### 6.1 동작 흐름

1. 환경변수 `DDL_SKIP_COMMENT_CHECK=1` 설정 시 즉시 `exit 0`
2. stdin JSON 에서 `file_path` 추출
3. 대상 조건: `*.sql` 이면서 경로에 `/db/migration/` 포함
4. 블록 주석 `/* ... */` 사전 제거 후 임시 파일에 저장
5. 라인 단위 순회로 `CREATE TABLE [IF NOT EXISTS] {name} (` 인라인 형식 진입 감지 → 블록 본문에서 첫 토큰을 컬럼 후보로 수집 (`CONSTRAINT`·`PRIMARY`·`FOREIGN`·`UNIQUE`·`CHECK`·`KEY`·`EXCLUDE`·`LIKE` 등 키워드 제외, snake_case `[a-z][a-z0-9_]*` 만 인정)
6. 블록 종료 (`)` 시작 라인) 시 테이블별 컬럼 목록 확정
7. 각 (`테이블`, `컬럼`) 쌍에 대해 동일 파일 내 `COMMENT ON COLUMN {tbl}.{col} IS ...` 매칭 검사
8. 누락 1건 이상이면 `exit 2` 로 저장 차단 + stderr 로 누락 표 + BaseEntity 4컬럼 표준 라벨 + 도메인 컬럼 라벨 패턴 + 우회 환경변수 안내

### 6.2 매칭 글롭

```
**/db/migration/*.sql
```

이 외 경로의 SQL 파일 (테스트 픽스처 등) 은 즉시 `exit 0` 처리.

> 2026-05-20 sql_관리포인트_통합 사이클에서 `**/db/init/*.sql` 글롭 항목 폐기 (디렉토리 자체 삭제 — `db/migration/` 단일화).

### 6.3 False positive 완화

- 라인 주석 `--` 이후 텍스트는 sed 로 사전 제거
- 블록 주석 `/* ... */` 도 사전 제거
- 대문자 시작 토큰은 SQL 키워드로 간주, 컬럼 후보에서 제외
- snake_case 정규식 `^[a-z][a-z0-9_]*$` 매칭만 인정
- 멀티라인 `CREATE TABLE name\n(` 형식은 미인식 (현 V1·V2 인라인 형식 전제). 해당 형식 도입 시 별도 보강 ANALYZE

### 6.4 우회

```bash
DDL_SKIP_COMMENT_CHECK=1
```

사용자가 명시적으로 요청한 경우에만 사용한다. Claude 가 자동으로 우회해서는 안 된다.

규약 본문은 [`../db/indexing-and-migration.md` §4](../db/indexing-and-migration.md) 참조.

---

## 7. 본 시점 자동 차단 훅 신설 보류

ROOT [`coding-discipline.md`](../../../../.claude/rules/coding-discipline.md) 도입 (2026-04-29) 시점의 결정.

### 7.1 결정 배경

LLM 코딩 디시플린 4원칙 적용 작업 (`docs/analyze/20260429/llm_coding_discipline/ANALYZE1.md`) 에서 자동 강제 수준을 다음 3단계 중 선택:

| 강도 | 메커니즘 |
|------|---------|
| 권고 룰만 | 룰 파일·템플릿에 명시, 자동 점검 없음 |
| **REVIEW 자동 점검** ✅ | `wtp-backend-engineer` / `wtp-domain-expert` 가 REVIEW 단계에서 자동 점검, 블로커 시 Fix Cycle |
| 훅 자동 차단 | PostToolUse 훅으로 위반 시 즉시 저장 차단 |

사용자 결정: **REVIEW 자동 점검까지만** (옵션 C 의 룰 본문 12건은 모두 명문화하되 자동 차단 훅은 신설 보류).

### 7.2 훅 신설 보류 항목

다음 항목들은 룰로 명문화되었으나 본 시점에 자동 차단 훅을 신설하지 않는다:

- 메서드 50줄 / 추상화 3단 / DTO 상속 3단 초과 자동 차단
- TASK 체크박스 외 파일 변경 자동 차단
- 체크박스 검증 기준 (`→ 검증:`) 누락 자동 차단
- 데드 코드 직접 삭제 자동 차단

위 항목들은 §1 인덱스의 기존 3개 훅 외에 **추가 훅 없이 REVIEW 단계 에이전트 자동 점검만으로 강제** 한다.

### 7.3 향후 도입 검토 트리거

운영 사례 누적 후 다음 조건 충족 시 별도 ANALYZE 로 자동 차단 훅 도입을 검토한다:

- REVIEW 자동 점검에서 **반복적으로 누락된 위반 사례** 가 3건 이상 누적
- 누락 사례가 도메인 안전·보안에 영향을 준 경우
- `wtp-backend-engineer` / `wtp-domain-expert` 점검 항목 응답 시간이 단답형 200~400단어 제약을 자주 초과

본 절은 결정 정합성 추적 목적으로 유지된다. 자동 차단 훅 도입 시 본 절을 갱신·이동한다.

---

## 8. 신규 훅 추가 절차

1. `.claude/hooks/{이름}.sh` 스크립트 작성
   - 상단에 주석으로 **트리거·매처·exit 코드 의미·우회 방법** 기재 (본 문서 인덱스와 일치해야 함)
   - `set -uo pipefail` 로 시작, stdin 입력 파싱 명시
2. `.claude/settings.local.json` 의 `hooks` 섹션에 등록
   ```json
   "PreToolUse": [
     {
       "matcher": "Bash",
       "hooks": [ { "type": "command", "command": "bash .claude/hooks/{이름}.sh" } ]
     }
   ]
   ```
3. 본 `hooks-guide.md` §1 인덱스 표에 신규 행 추가 + 동작 섹션 신규 절 추가
4. 차단 동작이 있는 훅이면 `commit-convention.md` / `exception-patterns.md` 등 관련 룰에도 교차 링크 추가
5. `./gradlew.bat build` 로 기존 빌드에 부작용 없음 확인
