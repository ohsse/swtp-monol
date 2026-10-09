---
status: completed
created: 2026-04-20
updated: 2026-04-20
---
# 레거시 EMS/PMS → 신규 분류 체계 매핑 설계 TASK

## 관련 계획
- [계획안](../../../plan/20260420/harness_도메인_DB_보강/PLAN2.md)

## Phase

### Phase 1: legacy-mapping.md 신설

- [x] `.claude/rules/legacy-mapping.md` 생성 — 섹션 1 (목적 및 적용 범위, 참조 문서 인용 관계)
- [x] `.claude/rules/legacy-mapping.md` — 섹션 2 (분류 6축 해석 기준 표 + 충돌 판정 규칙 3가지)
- [x] `.claude/rules/legacy-mapping.md` — 섹션 3 (레거시 suffix ↔ 신규 suffix 판정 지침 표)
- [x] `.claude/rules/legacy-mapping.md` — 섹션 4 (도메인별 매핑표 seed 12행)
- [x] `.claude/rules/legacy-mapping.md` — 섹션 5 (안티패턴 A·B·C 해소 가이드)
- [x] `.claude/rules/legacy-mapping.md` — 섹션 6 (KWS-GS-*-AN04 추적표 갱신 절차)
- [x] `.claude/rules/legacy-mapping.md` — 섹션 7 (동등성 검증 체크리스트)
- [x] `.claude/rules/legacy-mapping.md` — 섹션 8 (매핑표 갱신 룰)

### Phase 2: 참조 문서 수정

- [x] `CLAUDE.md` — "도메인 모델링 시 필수 참조" 표에 `legacy-mapping.md` 항목 및 참조 시점 추가
- [x] `.claude/rules/naming.md` — suffix 표 하단에 d/l, p/c 경계 해석 주석 1~2줄 보완
- [x] `.claude/agents/wtp-domain-expert.md` — PLAN 리뷰 체크리스트에 "§4 매핑표 미수록 레거시 테이블 → 매핑표 추가 요구" 항목 추가

### Phase 3: 검증

- [x] `legacy-mapping.md` 내 상대 경로(`../../legacy/docs/...`) 가 실제 파일을 가리키는지 수작업 확인
- [x] `legacy-mapping.md` 도메인 용어가 `.claude/rules/domain-glossary.md` 와 상충 없는지 수작업 diff
- [x] `.claude/commands/dev/review.md:45` 의 `legacy-mapping.md` 경로 참조가 신설 파일과 일치하는지 확인
- [x] `.claude/agents/wtp-domain-expert.md` 와 `.claude/agents/wtp-dba-reviewer.md` 가 `legacy-mapping.md` 경로를 올바르게 참조하는지 확인
- [x] `./gradlew.bat build` 실행 성공 확인

## 산출물
- [결과](../../../results/20260420/harness_도메인_DB_보강/RESULT2.md)
