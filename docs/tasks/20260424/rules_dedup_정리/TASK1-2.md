---
status: completed
created: 2026-04-24
updated: 2026-04-24
---
# 룰·스킬 중복 정리 — TASK1-2: 신규 파일 도입 (Tier 2)

## 관련 분할 TASK
- [TASK1-1 SSOT 강화 + 참조 정리](TASK1-1.md)
- [TASK1-2 신규 파일 도입](TASK1-2.md)
- [TASK1-3 분리 + 링크 갱신](TASK1-3.md)
- [TASK1-4 검증](TASK1-4.md)

## 관련 계획
- [계획안](../../../plan/20260424/rules_dedup_정리/PLAN1.md)
- [도메인 분석](../../../analyze/20260424/rules_dedup_정리/ANALYZE1.md)

## Phase

### Phase 3: 신규 파일 도입

순서: T2-I → T2-J → T2-G (단순 신규 → 훅 분석 → 분량 큰 이동)

#### T2-I — 커밋 컨벤션 룰 신규 (2건)
- [x] `.claude/rules/commit-convention.md` 신규 작성 — 타입(feat/fix/refactor/docs/chore/test) 정의·사용 예, 한국어 메시지 정책, 본문/제목/푸터 구조(Co-Authored-By 정책 포함), 브레이킹 체인지 도입 보류 명시, pre-commit 훅(`check-task-unstage.sh`) 상호작용 + `hooks-guide.md` 링크
- [x] `CLAUDE.md` §커밋 규칙 을 2줄 + "타입: feat/fix/refactor/docs/chore/test, 한국어. 상세: `.claude/rules/commit-convention.md`" 위임으로 축약

#### T2-J — 훅 동작 가이드 룰 신규 (2건)
- [x] `.claude/rules/hooks-guide.md` 신규 작성 — 훅 인덱스(`check-task-unstage.sh` PreToolUse·Bash matcher / `check-errorcode-contract.sh` PostToolUse·Write/Edit matcher), 트리거·매처·차단 동작(exit 0/2 의미), TASK 체크박스 경로 파싱 규칙(`doc-harness.md` 1:1 대응표), 우회 방법(`GIT_SKIP_DOC_CHECK=1`), 신규 훅 추가 절차(`.claude/settings.local.json` 등록)
- [x] `.claude/commands/dev/commit.md` 의 훅 언급을 hooks-guide 참조로 단순화

#### T2-G — test-strategy 부록 A 분리 (2건)
- [x] `.claude/rules/test-strategy-e2e-roadmap.md` 신규 작성 — `test-strategy.md` 부록 A (A.1 ~ A.8) 9소섹션 전체 이동, 헤더에 "현 시점 미도입 — 도입 시 별도 ANALYZE 필요" 명시
- [x] `.claude/rules/test-strategy.md` 본문 §2 의 "Testcontainers 미도입 상태" 안내 박스를 신규 파일 링크로 갱신 + 부록 A 섹션 제거

## 산출물
- [결과](../../../results/20260424/rules_dedup_정리/RESULT1.md)
