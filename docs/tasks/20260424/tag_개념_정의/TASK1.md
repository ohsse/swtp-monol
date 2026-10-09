---
status: completed
created: 2026-04-24
updated: 2026-04-24
---
# TAG 도메인 개념 정립 — 룰 갱신 검증 작업 분해

## 관련 계획
- [계획안](../../../plan/20260424/tag_개념_정의/PLAN1.md)
- [도메인 분석](../../../analyze/20260424/tag_개념_정의/ANALYZE1.md)

## 작업 개요

본 작업의 IMPL 단계는 **이미 ANALYZE 단계에서 갱신된 룰 파일 6개의 검증** 으로 한정된다. 신규 코드/엔티티/DB 변경 없음 (사용자 §질문2 답변 — "사전 등록 + 룰 정합만").

## Phase

### Phase 1: 갱신된 룰 파일 시각·정합 검토

ANALYZE1 의 룰 갱신 지시서 10건이 실제 파일에 정확히 반영되었는지 시각 검토.

- [x] `.claude/rules/domain-abbreviations.md` — `tag` 도입 예정 행 1건 추가 시각 확인 (풀네임 `tag`, 설명 "SCADA 계측값 식별 단위", 비고 "도입 예정 (예: `tag_m`)")
- [x] `.claude/rules/dict/standard-words.md` — `se` (DOM_CODE_20) + `desc` (DOM_TEXT) 신규 단어 2건 시각 확인 (등록일 2026-04-24)
- [x] `.claude/rules/dict/standard-data-domains.md` — `DOM_TAG_NM_50` (VARCHAR(50), String, NOT NULL) 신규 데이터 도메인 1건 시각 확인 (`DOM_ID_36` 다음 행)
- [x] `.claude/rules/dict/standard-terms.md` — `tag_nm` / `tag_se_cd` / `tag_desc` 신규 용어 3건 시각 확인 (`tag_val` 다음 3행)
- [x] `.claude/rules/dict/standard-terms.md` — 동의어·금지 패턴 표에 `tag_nm` ↔ tagname/tag_name/tagNm / `tag_se_cd` ↔ tag_type/tag_se/tagType / `tag_desc` ↔ tag_description/tagDesc 3건 추가 시각 확인
- [x] `.claude/rules/ot-integration.md` — §3 센서 품질 관리 헤더 직후 측정 유형 코드 SSOT 안내 인용 블록 추가 시각 확인 (표준 용어 사전 + Java enum 위치 명시)
- [x] `.claude/rules/db-partitioning-and-retention.md` — §1 운영 원칙 절에 "시계열 파티션 테이블(`_h` suffix) 은 마스터 테이블 FK 추가 금지" 글머리 추가 시각 확인

### Phase 2: 빌드 회귀 검증

- [x] `./gradlew.bat build` 실행 → BUILD SUCCESSFUL 확인 (룰 파일은 코드 외 산출물이므로 본질적 영향 없으나 회귀 차원)

### Phase 3: 사전 cross-link 정합 검증

- [x] `.claude/rules/dict/standard-terms.md` 의 `tag_nm` 행이 참조하는 `DOM_TAG_NM_50` 이 `.claude/rules/dict/standard-data-domains.md` 에 실재함을 Grep 으로 확인
- [x] `.claude/rules/dict/standard-terms.md` 의 `tag_se_cd` 행이 `DOM_CODE_20` 재사용을 명시한 표기가 정확한지 시각 확인 + `DOM_CODE_20` 이 `standard-data-domains.md` 에 실재함을 Grep 으로 확인
- [x] `.claude/rules/ot-integration.md` §3 인용 블록의 `[`dict/standard-terms.md`](dict/standard-terms.md)` 상대 경로 링크 정합성 시각 확인 (ot-integration.md 와 dict/ 의 상대 경로 관계)
- [x] `.claude/rules/domain-abbreviations.md` 의 `tag` 약어가 `dict/standard-terms.md` 의 `tag_nm`/`tag_se_cd`/`tag_desc` 조합 칸에서 "비즈니스 도메인" 으로 일관 표기됨을 시각 확인

## 산출물

- 본 작업은 Medium 규모 — RESULT/REVIEW 단계 면제 (`.claude/rules/doc-harness.md` §상태 전이·자동 실행 표)
- IMPL 단계 완료 후 `/dev:commit tag_개념_정의` 로 직행
