# 커밋 컨벤션

스마트정수장 백엔드의 Git 커밋 메시지 작성 규칙. CLAUDE.md §커밋 규칙의 요약을 보강한 상세 가이드다.

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`hooks-guide.md`](hooks-guide.md) | `check-task-unstage.sh` 훅과 커밋 차단 상호작용 |
| [`doc-harness/checkbox-rules.md`](doc-harness/checkbox-rules.md) | TASK 체크박스 경로 기록 규칙과 커밋 훅 연동 |

---

## 1. 메시지 구조

```
{타입}: {한국어 설명}

[본문 — 선택]

Co-Authored-By: Claude {모델명} <noreply@anthropic.com>
```

- **제목** (필수): 한 줄. 70자 이내 권장. 접두사 `타입:` 뒤 한국어로 작성
- **본문** (선택): 변경 사유·영향 범위가 제목 한 줄로 부족할 때만 작성. 제목과 **빈 줄 한 칸**으로 구분
- **푸터** (Claude 세션 필수): `Co-Authored-By` 라인으로 Claude 세션 커밋을 기록. 본문과 빈 줄로 구분

{모델명} 부분은 커밋을 수행한 Claude 모델명을 그대로 기록한다 (예: `Claude Sonnet 4.6`, `Claude Opus 4.7 (1M context)`).

### 제목 예시

현재 리포지토리에서 관찰된 실사용 패턴:

```
feat: YnType enum 도입으로 여부(Y/N) 필드 표준화
fix: JwtAuthenticationFilter request attribute 키값 swtp로 교체
refactor: 에너지 소비 도메인 패키지 구조 개선
chore: DB 표준화 3분리 사전 도입 및 하네스 개편
docs: project_rename_swtp 작업 산출물 문서 추가 (PLAN·TASK·RESULT·REVIEW)
```

부연 설명이 필요하면 제목 내부에 ` — ` 또는 `()` 를 사용한다 (본문 추가보다 가독성이 높음):

```
chore: /dev 워크플로우에 분석(analyze) 단계 도입
chore: 하네스 1차 개선 — 훅 경로 경고, 커버리지 기준, RESULT TASK 규모
```

---

## 2. 타입 정의

| 타입 | 의미 | 대표 변경 대상 |
|------|------|-------------|
| `feat` | 새 기능 | 신규 API·엔티티·서비스·스케줄러 잡 |
| `fix` | 버그 수정 | 기존 동작 이상 복원 |
| `refactor` | 코드 구조 개선 | 동작 불변, 가독성·SOLID 정리, 패키지 이동 |
| `docs` | 문서 변경 | 소스 주석·Javadoc·`docs/` 하위 산출물 |
| `chore` | 빌드/설정 변경 | Gradle·CI·훅·`.claude/` 룰·하네스 재정렬 |
| `test` | 테스트 추가/수정 | 단위·통합·시나리오 테스트 |

> `.claude/rules/` 하위 룰 재정렬은 동작 영향이 없는 설정 변경이므로 `chore` 가 기본이다. 신규 룰 파일 추가도 `chore` 범주로 관리한다.

---

## 3. 한국어 메시지 정책

- **제목·본문 모두 한국어로 작성한다** (CLAUDE.md 커밋 규칙 + 사용자 전역 CLAUDE.md 공통 정책)
- 변수명·함수명·클래스명·파일 경로 등 **코드 식별자**는 원문(영문) 유지 — 예: `"JwtTokenHelper 분리"`
- 기술 약어(JWT·DTO·PK·NPE 등)는 원문 유지 — 한글 음역 금지

---

## 4. 브레이킹 체인지 표기

현재 도입 **보류** — `feat!:` / `BREAKING CHANGE:` 표기는 사용하지 않는다.

호환성 파괴 변경이 필요해지면 별도 ANALYZE 단계에서 도입 여부를 논의한다. 그 전까지는 제목·본문에 자연어로 **"호환성 주의"** 또는 **"마이그레이션 필요"** 문구를 포함하여 리뷰어에게 신호한다.

---

## 5. pre-commit 훅과의 상호작용

Claude Code 세션에서 커밋 시 `check-task-unstage.sh` 훅이 PreToolUse(Bash matcher) 로 실행된다.
이 훅은 다음 동작을 수행한다:

- `docs/tasks/` 하위 `status != completed` TASK 문서의 체크박스 파일 경로를 파싱
- 해당 파일이 staging 에 있으면 **자동 unstage** 후 stderr 로 목록 출력
- 남은 staged 파일이 0건이면 `exit 2` 로 커밋 차단
- 차단 시 `GIT_SKIP_DOC_CHECK=1 git commit -m "..."` 으로 우회 가능 (사용자 명시 요청 시에만)

훅의 트리거·매처·매개변수 파싱 상세와 체크박스 경로 기록 규칙은 [`hooks-guide.md`](hooks-guide.md) 를 참조한다.

> 터미널·IDE 직접 커밋 (Claude 세션 외) 에는 훅이 적용되지 않는다. 따라서 TASK 체크박스 경로는 훅이 파싱할 수 있도록 [`doc-harness/checkbox-rules.md`](doc-harness/checkbox-rules.md) 을 반드시 지켜야 한다.

---

## 6. PR · 릴리스 태그

- PR 제목 = 커밋 제목과 동일 (한국어)
- PR 본문 = PLAN/RESULT 문서 링크 + 요약. GitHub `gh pr create` 사용 시 한국어 본문을 HEREDOC 으로 전달
- 릴리스 태그 규칙은 현 시점 미정의 — 도입 시 본 룰에 절 추가
