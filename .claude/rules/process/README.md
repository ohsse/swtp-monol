# 개발 프로세스 (인덱스)

스마트정수장 백엔드의 `/dev` 워크플로우 운영 룰 모음. 문서 하네스(ANALYZE/PLAN/TASK/RESULT/REVIEW) · Git 커밋 컨벤션 · pre/post tool use 훅 동작을 함께 다룬다.

> **이동 이력**:
> - ~2026-04-25 — `commit-convention.md`, `hooks-guide.md`, `doc-harness.md` 가 root `.claude/rules/` 평면 배치.
> - 2026-04-26 — 모두 `process/` 디렉토리 하위로 묶음. `doc-harness.md` 는 4분할(README + templates + transitions + checkbox-rules) 후 `process/doc-harness/` 서브 디렉토리로. 구 root 3개 파일은 redirect 잔존 (호환성).

---

## 자식 문서

| 문서 | 역할 |
|------|------|
| [`doc-harness/README.md`](doc-harness/README.md) | **분리 인덱스** — 디렉토리·상태 흐름·Fix Cycle (자식: templates·transitions·checkbox-rules) |
| [`commit-convention.md`](commit-convention.md) | Git 커밋 메시지 구조·타입·한국어 정책·pre-commit 훅 상호작용 |
| [`hooks-guide.md`](hooks-guide.md) | `.claude/hooks/` 트리거·차단·우회 방법 (`check-task-unstage.sh` · `check-errorcode-contract.sh`) |

---

## 자식 디렉토리: `doc-harness/`

`/dev` 워크플로우의 모든 문서 정의가 이 디렉토리에 1차 정의된다. `.claude/commands/dev*.md` 와 외부 룰은 본 디렉토리를 참조한다 (복제 금지).

| 자식 파일 | 다루는 주제 |
|----------|----------|
| [`doc-harness/README.md`](doc-harness/README.md) | docs/ 디렉토리·네이밍·상태 흐름·Fix Cycle 알고리즘·ANALYZE 조건부 재진입·번호 증가·상호 참조 |
| [`doc-harness/templates.md`](doc-harness/templates.md) | ANALYZE·PLAN·TASK·RESULT·REVIEW 5종 템플릿 + TASK 분할 기준 |
| [`doc-harness/transitions.md`](doc-harness/transitions.md) | `/dev:*` 단계 상태 전이·자동 실행 표 (SSOT) |
| [`doc-harness/checkbox-rules.md`](doc-harness/checkbox-rules.md) | TASK / ANALYZE 체크박스 파일 경로 기록 규칙 (pre-commit 훅 파싱 호환) |

---

## 다른 디렉토리와의 관계

| 디렉토리 | 본 디렉토리와의 관계 |
|---------|------------------|
| [`../dict/`](../dict/README.md) | backend 표준 용어 (DB 컬럼명) — `/dev:analyze` 의 5인 회의 사전 검토 대상 |
| `swtp/.claude/rules/dict/` | ROOT 어휘 사전 (단어·데이터 도메인·비즈니스 약어) — 동일 회의 검토 대상 |
| [`../db/`](../db/README.md) | DB 운영 패턴 — `/dev:plan` 의 DB 설계 변경 검토 기준 |

---

> **참조 정책**: 본 디렉토리도 `dict/`·`db/` 와 동일한 분리 인덱스 패턴(`CLAUDE.md §룰 참조 정책 (on-demand)`) 을 따른다. 작업과 직접 관련된 자식만 Read 한다.
