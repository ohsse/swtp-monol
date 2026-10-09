---
status: completed
created: 2026-04-23
updated: 2026-04-23
---
# 하네스 개선 1차 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260423/harness_개선_1차/PLAN1.md)

## 작업 개요

PLAN1.md §구현 방향의 4개 파일 수정을 체크박스 단위로 분해한다. Phase 5개 / 체크박스 13개. 분할 기준(Phase 10 / 체크박스 60) 미달 → **단일 TASK 파일 유지**.

## Phase

### Phase 1: 훅 강화 (§5.5)

- [x] `.claude/hooks/check-task-unstage.sh` — 라인 49(`full="backend/$file_path"`) 직후에 경로 존재성 검사 블록 추가: `$REPO_ROOT/$full` 이 파일도 디렉토리도 아닐 때 stderr 에 `[경로 경고] TASK '{slug}' 에 존재하지 않는 경로: {file_path}` 출력 후 `continue`
- [x] `.claude/hooks/check-task-unstage.sh` — 훅 상단 설명 주석(라인 2~10)에 "경로 존재성 경고" 동작 1~2줄 추가

### Phase 2: 테스트 전략 §7 신설 (§5.6)

- [x] `.claude/rules/test-strategy.md` — 문서 말미(§6 뒤)에 `## 7. 커버리지 최소 기준` 섹션 신설. PLAN1.md §구현 방향 §2 의 7.1~7.6 구조 그대로 작성 (계층별 권고 표 / 필수 3종 + 최소 케이스 목록 / 권장 추가 2종 / DBA 검증 병기 / 파티션 픽스처 주의 / JaCoCo 선택 예시)

### Phase 3: RESULT 템플릿 변경 (§5.7 템플릿)

- [x] `.claude/rules/doc-harness.md` — "## 문서 템플릿" 의 `RESULT{n}.md` 블록에서 `## 작업 요약` 과 `## 변경 사항` 사이에 `## TASK 규모` 섹션 삽입 (항목 4행 표: Phase 수 / 체크박스 수 / 분할 여부 / 분할 근거)

### Phase 4: 리뷰 체크리스트 업데이트 (§5.7 체크리스트)

- [x] `.claude/commands/dev/review.md` — 리뷰 체크리스트 섹션 중 "프로세스 검증" 또는 "문서 하네스" 계열 블록 근처에 `- [ ] TASK 규모(Phase 수/체크박스 수)가 분할 기준(Phase 10 / 체크박스 60) 대비 적절했는가` 1줄 추가

### Phase 5: 검증

- [x] 훅 동작 수동 검증 — 임시 TASK 문서에 존재하지 않는 경로 체크박스 포함 → 가짜 staged 파일로 `git commit` 시도 → stderr 경고 출력 확인 → 임시 파일 즉시 삭제 (커밋하지 않음)
- [x] 문서 링크 무결성 — `.claude/rules/` 및 `.claude/commands/` 내 `.md` 링크가 깨지지 않았는지 `grep -rn '\.md)'` 로 확인
- [x] `./gradlew.bat build` 실행 — 본 변경은 Java 소스에 영향 없으므로 빌드 성공 확인 (회귀 없음)
- [x] `docs/analyze/20260423/harness_개선_1차/ANALYZE1.md` — "## 룰 갱신 지시서" 체크박스 4개를 `- [x]` 로 완료 처리 (self-reform 케이스 마감 표시)

## 산출물
- [결과](../../../results/20260423/harness_개선_1차/RESULT1.md)
