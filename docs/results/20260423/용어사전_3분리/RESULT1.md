---
status: completed
created: 2026-04-23
updated: 2026-04-23
---
# 용어사전 3분리 — 작업 결과

## 관련 작업
- [도메인 분석](../../../analyze/20260423/용어사전_3분리/ANALYZE1.md)
- [계획안](../../../plan/20260423/용어사전_3분리/PLAN1.md)
- [태스크](../../../tasks/20260423/용어사전_3분리/TASK1.md)

---

## 작업 요약

DB 표준화 이론의 3층 구조(표준 단어 → 표준 데이터 도메인 → 표준 용어) 를 프로젝트 사전 체계에 도입하고, "비즈니스 도메인" 과 "데이터 도메인" 의 용어 충돌을 해소했다.

- **3개 신설 사전**: `.claude/rules/dict/` 디렉토리에 단어·데이터 도메인·용어 사전과 README 작성
- **5개 기존 파일 수정**: 역할 명확화, 데이터 도메인 참조 연결, 3층 템플릿 반영
- **2개 에이전트 개편**: `wtp-glossary-manager` 3층 확장, `wtp-dba-reviewer` 데이터 도메인 2차 승인 책임 추가
- **1개 커맨드 개편**: `/dev:analyze` 안건·출력·룰 갱신 지시서 3층 반영
- **4개 검증 통과**: 교차 참조 일관성, 명명 일관성, 에이전트 드라이런, pre-commit 훅 smoke test, 하위 호환

---

## TASK 규모
<!-- 분할 기준(Phase 10 / 체크박스 60) 대비 적정성 관찰용. 작성 시 표 그대로 유지 -->
| 항목 | 값 |
|------|----|
| Phase 수 | 4 |
| 체크박스 수 | 16 |
| 분할 여부 | N |
| 분할 근거 | — |

> 분할 기준(Phase 10 / 체크박스 60) 대비 충분히 여유. TASK 분할 불필요 판단이 적정했으며, 단일 TASK 로 관리가 용이했다.

---

## 변경 사항

### 신규 파일 (4개)

| 경로 | 용도 |
|------|------|
| `.claude/rules/dict/README.md` | 3개 사전 상호참조 인덱스 + 신규 등록 진입점 안내 + "도메인" 용어 충돌 방지 명시 + 층위별 유사 충돌 판정 기준 |
| `.claude/rules/dict/standard-words.md` | 표준 단어 11개 초기 등록 (`nm`, `dt`, `dtm`, `amt`, `qty`, `cd`, `yn`, `seq`, `ord`, `cnt`, `rgstr`) + 기본 데이터 도메인 매핑 |
| `.claude/rules/dict/standard-data-domains.md` | 데이터 도메인 10개 초기 등록 (`DOM_ID_50`·`DOM_ID_36`·`DOM_NAME_100`·`DOM_CODE_20`·`DOM_YN`·`DOM_DTM`·`DOM_DT`·`DOM_QTY_15_4`·`DOM_AMT_15_2`·`DOM_TEXT`) + 역공학 출처 + Java 타입 매핑 원칙 |
| `.claude/rules/dict/standard-terms.md` | 대표 용어 4개 등록 (`user_id`·`use_yn`·`rgstr_dtm`·`tag_val`) + 동의어·금지 패턴 섹션 |

### 수정 파일 (6개)

| 경로 | 변경 요약 |
|------|---------|
| `.claude/rules/domain-abbreviations.md` | 제목·서두를 "비즈니스 도메인 약어 사전 — 업무 영역 코드" 로 명확화. "도메인" 용어 구분 경고 박스 추가. §다른 약어 사전과의 관계 표에 `dict/*` 3개 행 추가 |
| `.claude/rules/naming.md` | §Java 필드 타입 매핑 1줄 표 제거 → "데이터 도메인 사전 참조" 섹션으로 대체. 주요 매핑 원칙 6개(외부 할당 PK, UUID, 일시, 금액, 측정값, yn) 요약 |
| `.claude/rules/doc-harness.md` | ANALYZE 템플릿 §신규 용어 카탈로그 → §표준 사전 카탈로그 3표 구조. 하위 호환 주석 추가. §룰 갱신 지시서 예시에 `dict/*` 3개 경로 추가 |
| `.claude/commands/dev/analyze.md` | 명사구·약어 후보 추출 항목을 3층 구조로 확장 (비즈니스 도메인 / 표준 단어 / 표준 데이터 도메인 / 표준 용어 / 비즈니스 용어). 기존 사전 1차 대조 목록에 `dict/*` 3개 추가. 안건 키워드 매핑에 "신규 표준 데이터 도메인" 행 추가(`wtp-dba-reviewer` 2차 승인). 작성 섹션 목록의 §3 을 "표준 사전 카탈로그" 로 전환 |
| `.claude/agents/wtp-glossary-manager.md` | 전체 재작성 — description 3층 반영, "도메인" 용어 구분 경고 박스, 검토 항목 8개(3층 + 비즈니스 도메인 + suffix + 센서 + 지자체 + 룰 갱신), 출력 템플릿 3개 표 분리 |
| `.claude/agents/wtp-dba-reviewer.md` | description 말미에 "표준 데이터 도메인의 타입·길이·NULL 정책 2차 승인 책임" 한 줄 추가 |
| `CLAUDE.md` | §규칙 문서 인덱스 표에 비즈니스 도메인 / 표준 단어 / 표준 데이터 도메인 / 표준 용어 4개 행 추가·수정. 필수 참조 문단에 `dict/standard-data-domains.md` 추가. "도메인" 용어 구분 경고 문단 추가 |

---

## 테스트 결과

본 작업은 코드 변경이 아닌 문서·설정 개편이므로 Gradle 빌드·JUnit 테스트는 수행하지 않았다. 대신 Phase 4 에서 문서·설정 검증 5건을 수행하여 모두 통과.

| 검증 항목 | 결과 | 근거 |
|----------|------|------|
| 교차 참조 일관성 (`data-domain` 키워드) | ✅ 통과 | `.claude` 하위 10개 파일에서 67회 참조, 모두 `standard-data-domains.md` / "데이터 도메인" 사용 (Grep 결과) |
| 명명 일관성 ("데이터 도메인" vs "비즈니스 도메인") | ✅ 통과 | 두 표기 모두 병기된 문장 다수 확인, 혼용 의심 지점 없음 |
| `wtp-glossary-manager` 단독 드라이런 | ✅ 통과 | `pump_oper_dtm` / `oper_stat_cd` 시나리오에서 3층 분류 표(단어 4건 / 데이터 도메인 2건 / 용어 2건) 정상 생성. `DOM_OPER_STAT_20` 신규 등록 건에 `wtp-dba-reviewer` 2차 승인 대상 명시. 룰 갱신 지시서 5건 모두 `.claude/rules/dict/*` 경로 포함 |
| pre-commit 훅 smoke test | ✅ 통과 | `check-task-unstage.sh:62` 의 sed 정규식(``- \[.\] \`(경로)\` ``) 이 `dict/*` 깊은 경로 파싱 정상. 파일 실존 확인도 정상 |
| 하위 호환 점검 | ✅ 통과 | `docs/analyze/20260423/harness_개선_1차/ANALYZE1.md`(status: approved) 가 구 템플릿 기반이나 `doc-harness.md` 하위 호환 주석으로 유효성 보장 |

---

## 비고

### 기대 효과

1. **신규 컬럼 설계 흐름 명시화** — 향후 엔티티·DB 설계 시 `/dev:analyze` 에서 3층 사전 확인 순서(단어 → 데이터 도메인 → 용어) 가 강제됨
2. **"도메인" 용어 혼동 제거** — "데이터 도메인" · "비즈니스 도메인" 표기 분리로 ANALYZE 회의록 가독성 향상
3. **데이터 도메인 2차 승인 게이트** — `wtp-dba-reviewer` 가 타입·길이·NULL 정책을 공식 검토 → DB 표준화 품질 향상

### 향후 관찰 사항

- `wtp-glossary-manager` 드라이런 결과 출력이 400단어 제한을 일부 초과했다. 장기적으로 출력이 지속 초과되면 에이전트 분리(단어 / 데이터 도메인 / 용어 별도 에이전트) 재검토 필요 — ANALYZE1.md §확정 메타-결정 에 기록된 트리거
- `standard-terms.md` 는 "모든 컬럼 등록하지 않음" 방침으로 선별 등록만 허용 — 실제 운영에서 "언제 등록할지" 의 기준이 모호해지면 별도 ANALYZE 필요
- 기존 엔티티의 데이터 도메인 소급 정렬은 범위 밖 — 신규 엔티티부터 데이터 도메인 강제 적용, 기존은 리팩토링 시 점진 정렬 (ANALYZE1.md §PLAN 으로 전달할 결정 사항)

### 하위 호환성

- 과거 `status: approved` ANALYZE 문서는 단일 "## 신규 용어 카탈로그" 구조로 작성되어 있으나 `doc-harness.md` 의 하위 호환 주석으로 유효성이 보장된다
- `harness_개선_1차` 등 과거 작업의 룰 갱신 지시서 체크박스 역시 파싱 규칙 변경 없음 — pre-commit 훅 영향 없음

### Gradle 빌드·JUnit 테스트 미수행 사유

본 작업은 `.claude/*` 디렉토리의 하네스·룰·에이전트·커맨드 및 `CLAUDE.md` 문서만 수정했다. Java 소스 코드·`build.gradle`·`application.yml`·마이그레이션 스크립트는 전혀 건드리지 않았으므로 빌드·테스트로 검증할 대상이 없다 (`test-strategy.md` 의 "테스트는 코드 변경을 검증" 원칙).
