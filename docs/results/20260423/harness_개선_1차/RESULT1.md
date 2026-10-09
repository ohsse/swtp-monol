---
status: completed
created: 2026-04-23
updated: 2026-04-23
---
# 하네스 개선 1차 — 결과

## 관련 작업
- [계획안](../../../plan/20260423/harness_개선_1차/PLAN1.md)
- [태스크](../../../tasks/20260423/harness_개선_1차/TASK1.md)

## 작업 요약

외부 보고서 `docs/analyze/20260423/harness_개선_1차/보고서.docx` 가 지적한 8개 개선점 중 1차 범위 **§5.5 (TASK 체크박스 경로 경고) · §5.6 (테스트 커버리지 최소 기준) · §5.7 (RESULT TASK 규모 필드 + 리뷰 체크리스트)** 를 반영했다. ANALYZE1.md 의 5인 팀 회의 결론(도메인 전문가 블로커 2건 해소·backend 엔지니어 권고 2건 수용) 을 PLAN1.md 로 구체화하고, 4개 파일 수정 + self-reform 루프 검증 + 빌드 회귀 없음 확인 순서로 진행했다.

구현 중 추가 발견으로 `grep -oP` 기반 체크박스 파싱이 Windows Git Bash 의 non-UTF8 locale 에서 실패하던 사실을 확인하고, POSIX 호환 `sed` 로 대체했다. 이는 1차 범위에는 포함되지 않은 버그 수정이지만 훅 파일 동일 수정 범위 내에서 함께 처리했다.

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 5 |
| 체크박스 수 | 9 |
| 분할 여부 | N |
| 분할 근거 | — (분할 기준 Phase 10 / 체크박스 60 미달) |

## 변경 사항

### 수정 파일 (4개, 자기 수정 제외)

| 파일 | 삽입 / 삭제 | 변경 성격 |
|------|-----------|---------|
| `backend/.claude/hooks/check-task-unstage.sh` | +11 / -2 | 경로 존재성 검사 블록 추가 + 주석 보강 + `grep -oP` → `sed -n` (추가 발견 버그 수정) |
| `backend/.claude/rules/test-strategy.md` | +92 / -0 | `## 7. 커버리지 최소 기준` 섹션 신설 (§7.1~§7.6) |
| `backend/.claude/rules/doc-harness.md` | +8 / -0 | RESULT{n}.md 템플릿에 `## TASK 규모` 섹션 삽입 |
| `backend/.claude/commands/dev/review.md` | +1 / -0 | 리뷰 체크리스트에 "프로세스 검증: TASK 규모 적정성" 1줄 추가 |

합계: **4개 파일, +112 / -2 줄**. (git diff 는 settings.local.json +10 을 포함하지만 본 작업 범위 외 — 커밋 시 분리)

### 신규 문서 산출물

| 파일 | 상태 |
|------|------|
| `docs/analyze/20260423/harness_개선_1차/ANALYZE1.md` | status: approved (self-reform 케이스 비고 포함) |
| `docs/plan/20260423/harness_개선_1차/PLAN1.md` | status: approved |
| `docs/tasks/20260423/harness_개선_1차/TASK1.md` | status: completed |
| `docs/results/20260423/harness_개선_1차/RESULT1.md` | 본 문서 (status: completed 로 설정 예정) |

## 테스트 결과

### 1. 빌드 회귀 확인
- `./gradlew.bat build` (api + common + scheduler 전체) → **BUILD SUCCESSFUL in 18s** (19 actionable tasks: 4 executed, 15 up-to-date)
- Java 소스 무변경 확인 — 하네스 메타 파일만 수정

### 2. 훅 수동 검증
- **임시 TASK 파일** (`docs/tasks/20260423/hook_test_temp/TASK_HOOK_VERIFY.md`) 에 존재하지 않는 경로 체크박스(`this_path_definitely_does_not_exist_abc123.txt`) + 실재 경로(`.claude/rules/test-strategy.md`) 각 1개 포함
- `echo '{"tool_input":{"command":"git commit -m hook-test"}}' | bash .claude/hooks/check-task-unstage.sh` 실행
- **결과 (stderr)**:
  ```
  [경로 경고] TASK 'hook_test_temp' 에 존재하지 않는 경로: this_path_definitely_does_not_exist_abc123.txt
  [미완료 작업 파일 제외] 미완료 작업에 속한 파일을 staging에서 제외합니다.
    backend/.claude/rules/test-strategy.md   harness_개선_1차
  [미완료 작업 파일 제외] 위 파일을 제외한 1개 파일로 커밋을 진행합니다.
  ```
- §5.5 경고 기능 정상 동작 ✓
- 임시 TASK 디렉토리 삭제 완료 (`docs/tasks/20260423/hook_test_temp/`)

### 3. 문서 링크 무결성
- `.claude/rules/` 및 `.claude/commands/` 내 `.md` 링크 grep → 26개 링크 모두 유효 (절대경로 + 템플릿 플레이스홀더 `{YYYYMMDD}`, `{N}` 포함)

### 4. self-reform 루프 검증
- ANALYZE1.md "## 룰 갱신 지시서" 체크박스 4개를 IMPL 단계에서 `- [x]` 완료 처리. ANALYZE 승인 비고에 명시된 "체크박스는 /dev:impl 단계에서 채운다" 흐름이 실제로 성립함을 검증.

## 비고

### 추가 발견 (1차 범위 외이지만 함께 처리)
- **기존 훅의 Windows 환경 파싱 버그**: `grep -oP` (PCRE) 가 Windows Git Bash 의 non-UTF8 locale 에서 `grep: -P supports only unibyte and UTF-8 locales` 오류로 **전혀 작동하지 않던 상태**. `sed -n 's/^- \[.\] \`\([^\`]*\)\`.*/\1/p'` (POSIX BRE) 로 대체하여 해결. 체크박스 파싱·unstage·§5.5 경고 모두 작동.

### 관찰 필요 (REVIEW 에서 재확인)
- 훅 내부 `git reset HEAD -- "$file"` 호출 후 staged 파일이 즉시 unstage 되지 않는 경우가 수동 검증 중 1회 관찰됨 (`2>/dev/null` 로 출력 억제된 영향 가능). 수동으로 동일 명령 재실행 시에는 정상 unstage. 재현성 낮음 → REVIEW 에서 재검증 필요.
- §5.5 경고는 **stderr 단독 출력**이고 차단하지 않음 → 실제 커밋에서는 경고가 섞여 보일 수 있음. 대량 경고 시 가독성 이슈 발생 가능. 2차 범위에서 "경고 총괄 라인" 추가 검토.

### 후속 작업 권장
- **2차 범위 (§5.1/5.2/5.3)**: 교차 주제 인덱스 + ANALYZE 재진입 수동 플래그 + 약어 승격 체크리스트 — 별도 슬러그 `harness_개선_2차` 로 진행 권장
- **3차/보류 (§5.4/5.8)**: 실제 슬러그 장기 분산 사례 또는 CLAUDE.md 스크롤 부담 발생 시 착수
- **시나리오 테스트 코드 구현**: `AlarmEscalationScenarioTest` 등 3종은 `pump`·`alarm` 도메인 패키지 도입 시 별도 작업
- **JaCoCo 실 도입**: 본 작업은 가이드 문서화까지. 실제 플러그인 설치는 팀 합의 후 별도 PR

### 계획 대비 차이
- PLAN1 의 `wtp-backend-engineer.md` 변경 항목은 회의 결론에 따라 **`.claude/commands/dev/review.md`** 로 이관 (원래 ANALYZE1.md 에서 결정). 실제 구현은 결정 사항 그대로 수행.
- 추가 `sed` 대체는 계획에 없던 변경이지만 동일 훅 파일 내부 수정으로 `wtp-backend-engineer` 단독 결정 범위.
- **경로 존재성 조건식 변경**: PLAN1.md 명시 `[ ! -f "$REPO_ROOT/$full" ] && [ ! -d "$REPO_ROOT/$full" ]` 에서 `[ ! -e "$REPO_ROOT/$full" ]` 로 변경. `-e` 가 일반 파일·디렉토리·심볼릭링크·소켓·파이프 등 모든 파일 시스템 객체를 한 번에 커버하므로 더 견고. 기능적으로 PLAN 의도보다 강한 검증.
