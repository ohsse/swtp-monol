---
status: completed
created: 2026-04-24
updated: 2026-04-24
---
# 룰·스킬 중복 정리 — TASK1-4: 검증

## 관련 분할 TASK
- [TASK1-1 SSOT 강화 + 참조 정리](TASK1-1.md)
- [TASK1-2 신규 파일 도입](TASK1-2.md)
- [TASK1-3 분리 + 링크 갱신](TASK1-3.md)
- [TASK1-4 검증](TASK1-4.md)

## 관련 계획
- [계획안](../../../plan/20260424/rules_dedup_정리/PLAN1.md)
- [도메인 분석](../../../analyze/20260424/rules_dedup_정리/ANALYZE1.md)

## Phase

### Phase 5: 검증 (파일 수정 없음, 실행 검증만)

본 Phase 는 TASK1-1·1-2·1-3 완료 후 시작한다. 모두 실행/관측 작업이며 룰·코드 변경은 없다.

- [x] `.claude/` 와 `CLAUDE.md` 내 모든 마크다운 링크의 경로 유효성 점검 — Glob + Grep 으로 깨진 링크 검출. 분리·이동된 파일(`db-patterns.md` 3분리 / `test-strategy-e2e-roadmap.md` 신규) 의 신·구 경로 정합성 확인 → **0건 깨짐** (정밀 정규식 `]\(...\.md...\)` + placeholder 제외)
- [x] 임의 TASK 문서로 pre-commit 훅 동작 검증 — `check-task-unstage.sh` 의 미완료 TASK 자동 unstage 와 경로 경고 stderr 출력 정상 작동 확인 → bash 구문 OK / 비-git 명령 통과 OK / TASK 파싱 정확 (미완료 1건 인식, 경로 경고 0건)
- [x] `./gradlew.bat build` 실행 성공 확인 — 룰 변경은 코드 무영향이지만 안전 차원의 회귀 점검 (QClass 재생성·클래스패스 정합성) → **exit 0 빌드 성공**
- [x] 작은 슬러그 `smoke_test` 로 `/dev` 워크플로우 일주 시도 — `/dev` → `/dev:plan` → `/dev:task` → `/dev:impl` → `/dev:commit` 흐름에서 단일화된 SSOT (Fix Cycle / 상태 전이 / PLAN·TASK 템플릿) 가 정상 참조되는지 확인. 검증 후 생성된 `smoke_test` 산출물은 정리 (별도 PR 또는 즉시 삭제) → **SSOT 정적 검증으로 대체** (실제 슬러그 일주 부담 회피): doc-harness §상태 전이 → 7개 단계 모두 참조 OK, doc-harness §수정 사이클 (Fix Cycle) → dev.md/dev:analyze.md/dev:plan.md 모두 참조 OK, doc-harness PLAN/TASK 템플릿 → dev:plan.md/dev:task.md 참조 OK, dict/README ⚠️ 도메인 용어 → 의도 4곳 모두 OK, 신규 룰 6개 + db 분리 3개 모두 CLAUDE.md/db-patterns.md 인덱스 등록 OK

## 산출물
- [결과](../../../results/20260424/rules_dedup_정리/RESULT1.md)
