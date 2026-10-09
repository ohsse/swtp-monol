---
status: completed
created: 2026-04-23
updated: 2026-04-23
---
# 용어사전 3분리 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260423/용어사전_3분리/PLAN1.md)
- [도메인 분석](../../../analyze/20260423/용어사전_3분리/ANALYZE1.md)

## 규모 요약
- 총 체크박스: 16개 (Phase 1: 7 / Phase 2: 2 / Phase 3: 2 / Phase 4: 5)
- 분할 여부: **단일** (Phase 4개 < 10, 체크박스 16개 < 60 — `doc-harness.md §TASK 분할 기준` 미충족)

---

## Phase

### Phase 1: 사전 파일 신설 및 기존 룰 명확화

> 신규 4개 파일 + 기존 2개 파일 수정 + `CLAUDE.md` 인덱스 확장. 체크박스 완료 시 곧바로 Phase 2 로 진행.

- [x] `.claude/rules/dict/README.md` 신규 작성 — 3개 사전 상호참조 인덱스 + 신규 등록 진입점(`/dev:analyze`) 안내
- [x] `.claude/rules/dict/standard-words.md` 신규 작성 — ANALYZE1.md §표준 사전 카탈로그 §1 의 단어 11개 초기 등록 + "비즈니스 도메인 약어는 `domain-abbreviations.md` 참조, 중복 등록 금지" 방침 서두 명시
- [x] `.claude/rules/dict/standard-data-domains.md` 신규 작성 — ANALYZE1.md §2 의 데이터 도메인 10개 초기 등록 (각 항목에 역공학 출처 비고)
- [x] `.claude/rules/dict/standard-terms.md` 신규 작성 — ANALYZE1.md §3 의 대표 용어 4개 등록 + "모든 컬럼 등록하지 않음" 방침 명시
- [x] `.claude/rules/domain-abbreviations.md` 서두·제목을 "비즈니스 도메인 약어 사전 — 업무 영역 코드" 로 명확화 + §다른 약어 사전과의 관계 표에 `dict/*` 3개 파일 행 추가
- [x] `.claude/rules/naming.md` §Java 필드 타입 매핑 표 제거 → "데이터 도메인 사전 참조 — `.claude/rules/dict/standard-data-domains.md`" 섹션으로 대체
- [x] `CLAUDE.md` §규칙 문서 인덱스 표에 `dict/standard-words.md`·`dict/standard-data-domains.md`·`dict/standard-terms.md` 3개 행 추가

### Phase 2: 에이전트 개편

> `wtp-glossary-manager` 3층 확장 + `wtp-dba-reviewer` 책임 보강. 각 파일은 단일 체크박스로 묶어 한 번에 수정.

- [x] `.claude/agents/wtp-glossary-manager.md` description frontmatter + 관리 룰 파일 목록(+`dict/*` 3개) + 출력 템플릿(3표 분리) + 충돌 분류 4개 기준(단어=어근 / 도메인=타입·길이 / 용어=의미) 일괄 갱신
- [x] `.claude/agents/wtp-dba-reviewer.md` description 말미에 "데이터 도메인의 타입·길이·NULL 정책 2차 승인 책임" 한 줄 추가

### Phase 3: 회의 안건·템플릿 확장

> `doc-harness.md` 의 ANALYZE 템플릿 3표 구조 + `/dev:analyze` 안건·출력·룰 갱신 지시서 확장. 하위 호환 주석 누락 금지.

- [x] `.claude/rules/doc-harness.md` ANALYZE 템플릿 §신규 용어 카탈로그 → §표준 사전 카탈로그(3표: 신규 표준 단어 / 신규 표준 데이터 도메인 / 신규 표준 용어) 로 변경 + 하위 호환 주석 삽입 + §룰 갱신 지시서 예시에 `.claude/rules/dict/*` 경로 추가
- [x] `.claude/commands/dev/analyze.md` §회의 안건에 "표준 데이터 도메인(값 형식) 결정" 안건 추가(주도 `wtp-glossary-manager`, 검증 `wtp-dba-reviewer`) + §출력 카탈로그 3개 서브섹션 분리 + §룰 갱신 지시서 샘플에 `dict/*` 3개 경로 추가

### Phase 4: 검증 (파일 수정 없음, 실행 검증만)

- [x] 신규 파일 교차 참조 일관성 확인 — `data-domain` 키워드 10개 파일 67회 참조, 모두 `standard-data-domains.md` / "데이터 도메인" 으로 일관
- [x] 명명 일관성 점검 — "데이터 도메인" 과 "비즈니스 도메인" 이 두 표기 모두 명시된 문장 다수, 혼용 의심 지점 없음
- [x] `wtp-glossary-manager` 단독 드라이런 — `pump_oper_dtm` / `oper_stat_cd` 시나리오에서 3층 분류 표 정상 생성, `wtp-dba-reviewer` 2차 승인 대기건 정확히 식별, 룰 갱신 지시서 초안 5건 모두 `.claude/rules/dict/*` 경로 포함
- [x] pre-commit 훅 smoke test — `check-task-unstage.sh:62` 의 sed 정규식이 백틱 경로를 추출하고 `backend/` prefix 로 실존 확인하므로 `dict/*` 깊은 경로 파싱 정상 동작
- [x] 하위 호환 점검 — 기존 `docs/analyze/20260423/harness_개선_1차/ANALYZE1.md`(status: approved) 가 단일 "## 신규 용어 카탈로그" 섹션 기반으로 작성되어 있으나, `doc-harness.md` 의 하위 호환 주석("과거 approved 문서 그대로 유효") 이 유효성 명시

---

## 체크박스 경로 기록 규칙 준수 확인

모든 파일 경로 체크박스는 `doc-harness.md §TASK / ANALYZE 체크박스 파일 경로 기록 규칙` 을 따른다:
- 모듈 루트 기준 전체 상대 경로 + 백틱 감싸기 ✓
- 축약 경로 없음 ✓
- 글롭 패턴 없음 ✓
- 명령어는 체크박스 경로로 기록하지 않음 (Phase 4 는 실행 검증 설명만) ✓

---

## 산출물
- [결과](../../../results/20260423/용어사전_3분리/RESULT1.md) — Phase 4 완료 후 `/dev:result` 단계에서 작성
- [리뷰](../../../reviews/20260423/용어사전_3분리/REVIEW1.md) — RESULT 완료 후 `/dev:review` 단계에서 작성
