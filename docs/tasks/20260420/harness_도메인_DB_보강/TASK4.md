---
status: completed
created: 2026-04-20
updated: 2026-04-20
---
# Fix Cycle 4 — 외부 문서 섹션 번호 오기재 수정 및 REVIEW2 권고 처리

## 관련 계획
- [계획안](../../../plan/20260420/harness_도메인_DB_보강/PLAN4.md)

## Phase

### Phase 1: 참조 표 수정 (B1 + P1)

- [x] `.claude/rules/legacy-mapping.md` 참조 표 `domain-glossary` 행 — display name `rules/domain-glossary.md` → `domain-glossary.md`, 설명 `§4 도메인 용어·약어의 원천` → `도메인 용어·약어의 원천 (전 섹션)` 수정
- [x] `.claude/rules/legacy-mapping.md` 참조 표 `db-patterns` 행 — display name `rules/db-patterns.md` → `db-patterns.md`, 설명 `§4 파티션·인덱스·보존 기간 정책 참조 원천` → `파티션(§1)·인덱스(§2)·보존 기간(§6) 정책 참조 원천` 수정

### Phase 2: §3 헤더 seed 행 수 표기 (R1)

- [x] `.claude/rules/legacy-mapping.md` §3 헤더 아래 설명 `핵심 도메인 seed.` → `핵심 도메인 seed (초기 13행).` 수정

### Phase 3: 서문 섹션 번호 비고 추가 (R2)

- [x] `.claude/rules/legacy-mapping.md` 서문 끝에 섹션 번호 off-by-one 비고 한 줄 추가

### Phase 4: 빌드 검증

- [x] `./gradlew.bat build` 실행 성공 확인

## 산출물
- [결과](../../../results/20260420/harness_도메인_DB_보강/RESULT4.md)
