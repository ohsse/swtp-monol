---
status: completed
created: 2026-04-20
updated: 2026-04-20
---
# Fix Cycle 3 — 섹션 번호 자기참조 오류 및 CLAUDE.md 표현 불일치 수정

## 관련 계획
- [계획안](../../../plan/20260420/harness_도메인_DB_보강/PLAN3.md)

## Phase

### Phase 1: legacy-mapping.md §4 → §3 일괄 수정 (B1)

- [x] `.claude/rules/legacy-mapping.md` 서문 `§4 매핑표에 해당 행을 추가 또는 확인한다` → `§3 매핑표에 해당 행을 추가 또는 확인한다` 수정
- [x] `.claude/rules/legacy-mapping.md` 참조 표 wtp-domain-expert 행 `§4 매핑표 미수록` → `§3 매핑표 미수록` 수정
- [x] `.claude/rules/legacy-mapping.md` 참조 표 wtp-dba-reviewer 행 `§4 신규 테이블명` → `§3 신규 테이블명` 수정

### Phase 2: CLAUDE.md "두 문서" 표현 수정 (B2)

- [x] `CLAUDE.md` line 111 `아래 두 문서를 먼저 확인한다` → `아래 세 문서를 먼저 확인한다` 수정
- [x] `CLAUDE.md` line 120 `두 문서를 참조한다` → `위 문서들을 참조한다` 수정

### Phase 3: 빌드 검증

- [x] `./gradlew.bat build` 실행 성공 확인

## 산출물
- [결과](../../../results/20260420/harness_도메인_DB_보강/RESULT3.md)
