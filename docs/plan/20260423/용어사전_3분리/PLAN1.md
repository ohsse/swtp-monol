---
status: approved
created: 2026-04-23
updated: 2026-04-23
---
# 용어사전 3분리 — 사전·에이전트·하네스 개편 계획

## 관련 분석
- [도메인 분석](../../../analyze/20260423/용어사전_3분리/ANALYZE1.md)

---

## 목적

DB 표준화 이론의 3층 구조(**표준 단어 → 표준 데이터 도메인 → 표준 용어**) 를 프로젝트 사전 체계에 도입하여:

1. 신규 컬럼 설계 시 "단어 확인 → 데이터 도메인 확인 → 용어 확정" 3단계 의사결정 흐름을 명시화한다
2. 기존 "비즈니스 도메인" (pump·alarm 업무 영역) 과 DB 이론의 "표준 도메인" (값 형식) 사이의 **"도메인" 용어 충돌** 을 해소한다
3. `wtp-glossary-manager` 에이전트와 `/dev:analyze` 5인 팀 회의 안건을 3층 구조에 맞춰 재정의하여 ANALYZE 회의록의 가독성·추적성을 확보한다

---

## 배경

ANALYZE1.md §작업 배경 및 §현 프로젝트 진단 참조. 요약:

- `.claude/agents/wtp-glossary-manager.md:1-13` 이 "용어 사전" 으로 통째 관리 — 3분리 미구현
- `entity-patterns.md:90`·`db-patterns.md:22` 에 값 형식 사례가 산재 — 중앙 표준 도메인 사전 부재
- `domain-abbreviations.md:3` 의 "도메인" 이 비즈니스 영역·DB 이론의 값 형식을 함께 가리킴 — 혼동 내재
- `commands/dev/analyze.md:135` 의 "신규 용어 카탈로그" 가 단일 평면 — 3층 분리 안건 없음

---

## 범위

### 포함

- `.claude/rules/dict/` 디렉토리 신설 및 3개 사전 + README 파일 작성
- `.claude/rules/domain-abbreviations.md` · `naming.md` · `doc-harness.md` 내용 수정
- `.claude/agents/wtp-glossary-manager.md` · `wtp-dba-reviewer.md` 수정
- `.claude/commands/dev/analyze.md` 안건·출력·룰 갱신 지시서 예시 확장
- `CLAUDE.md` §규칙 문서 인덱스 표 확장
- Phase 4 검증 — 샘플 슬러그 드라이런, 체크박스 훅 통과 확인, 하위 호환 점검

### 제외 (제외 사항 섹션에 별도 기재)

- 기존 엔티티·DB 컬럼의 타입 일괄 리팩토링
- 새 에이전트 추가 또는 기존 에이전트 분할
- Gradle·Spring Boot 설정 변경

---

## 구현 방향

### Phase 1. 사전 파일 신설 및 기존 룰 명확화 (영향: 룰 파일 4종, `CLAUDE.md`)

**신규 4개 파일** (`.claude/rules/dict/`)
- `README.md` — 3개 사전 상호참조 인덱스 + 신규 등록 진입점 안내 (`/dev:analyze` 로부터 진입)
- `standard-words.md` — 표준 단어 11개 초기 등록 (ANALYZE1.md §표준 사전 카탈로그 §1)
- `standard-data-domains.md` — 표준 데이터 도메인 10개 초기 등록 (ANALYZE1.md §2), 각 항목에 역공학 출처 기록
- `standard-terms.md` — 대표 표준 용어 4개 초기 등록 (ANALYZE1.md §3), "모든 컬럼 등록하지 않음" 방침 명시

**기존 파일 수정**
- `domain-abbreviations.md` — 1-5 행 서두를 "비즈니스 도메인 약어 사전 — 업무 영역 코드" 로 명확화. 기존 §다른 약어 사전과의 관계 표에 `dict/*` 3개 파일 행 추가
- `naming.md` — §Java 필드 타입 매핑 표 제거. "데이터 도메인 사전 참조 — `.claude/rules/dict/standard-data-domains.md`" 로 대체
- `CLAUDE.md` — §규칙 문서 인덱스 표에 3개 행 추가 (`dict/standard-words.md`, `dict/standard-data-domains.md`, `dict/standard-terms.md`)

### Phase 2. 에이전트 개편 (영향: `.claude/agents/` 2파일)

- `wtp-glossary-manager.md`
  - `description` frontmatter: "도메인 용어/약어 사전" → "표준 단어 / 데이터 도메인 / 표준 용어 3층 사전"
  - 관리 룰 파일 목록에 `dict/*` 3개 파일 추가
  - 출력 템플릿: 단일 "신규 용어" 표 → 3개 표(신규 단어 / 신규 데이터 도메인 / 신규 용어) 로 분리
  - 충돌 분류 4개(신규/재사용/유사충돌/폐기·통합) 를 층위별로 재명시:
    - 단어: 철자·어근 충돌 기준
    - 데이터 도메인: 타입·길이 중복 기준
    - 용어: 의미 중복 기준
- `wtp-dba-reviewer.md`
  - `description` 말미에 "데이터 도메인의 타입·길이·NULL 정책 2차 승인 책임" 한 줄 추가

### Phase 3. 회의 안건·템플릿 확장 (영향: `.claude/rules/doc-harness.md`, `.claude/commands/dev/analyze.md`)

- `doc-harness.md`
  - ANALYZE 템플릿 §신규 용어 카탈로그 → §표준 사전 카탈로그 로 변경
  - 내부에 3개 표(신규 표준 단어 / 신규 표준 데이터 도메인 / 신규 표준 용어) 배치
  - 템플릿 주석에 하위 호환 문구 추가: "과거 `status: approved` ANALYZE 문서는 3표 구조 없이도 유효. 해당 섹션이 비어있으면 생략 가능"
  - §룰 갱신 지시서 예시에 `.claude/rules/dict/*.md` 3개 경로 추가
- `commands/dev/analyze.md`
  - §회의 안건 섹션에 "표준 데이터 도메인(값 형식) 결정" 안건 추가 — 주도: `wtp-glossary-manager`, 검증: `wtp-dba-reviewer`
  - §출력 카탈로그 섹션을 3개 서브섹션(`### 신규 표준 단어` / `### 신규 표준 데이터 도메인` / `### 신규 표준 용어`) 으로 분리
  - §룰 갱신 지시서 샘플에 `dict/*` 3개 파일 경로 추가

### Phase 4. 검증 (영향 없음 — 실행 검증만)

- `grep -rn "data-domain" .claude/rules/` 로 신규 파일 4개 간 교차 참조 일관성 확인
- `grep -rn "비즈니스 도메인" .claude/rules/ .claude/agents/ .claude/commands/` 로 명명 일관성 확인
- 가상 슬러그 `pump_basic_master` 로 `wtp-glossary-manager` 단독 호출 → 출력이 3표 형식인지 확인
- pre-commit 훅 smoke test — 임의 TASK 문서의 체크박스가 `.claude/rules/dict/standard-words.md` 경로를 포함해도 파싱이 정상인지 (스테이징 시뮬레이션, 실제 커밋은 하지 않음)
- 기존 `docs/analyze/` 하위 approved 문서의 필수 섹션 누락 판정 여부 샘플 점검 (최소 1개)

---

## DB 설계 변경

없음. 본 작업은 룰·에이전트·하네스 개편에 한정되며, 실제 DB 스키마·엔티티·마이그레이션은 건드리지 않는다.

초기 등록될 `DOM_ID_50`, `DOM_QTY_15_4` 등 데이터 도메인은 기존 코드의 **역공학 결과를 문서화** 할 뿐이므로, 실제 DDL 변경이 없다 (ANALYZE1.md §표준 사전 카탈로그 §2 비고 열 — `entity-patterns.md:55`, `db-patterns.md:22` 출처 참조).

---

## 테스트 전략

본 작업은 코드 변경이 아니므로 JUnit·Spring 테스트는 추가하지 않는다. 대신 **문서·설정 검증** 을 수행한다.

| 검증 항목 | 방법 | 성공 기준 |
|----------|------|---------|
| 사전 파일 간 교차 참조 | `grep` + 수동 검사 | 모든 참조 링크가 유효 |
| 명명 일관성 ("데이터 도메인" vs "비즈니스 도메인") | `grep` 전수 검사 | 두 용어가 혼용되는 문장 없음 |
| `wtp-glossary-manager` 출력 형식 | 에이전트 단독 호출 드라이런 | 3표 형식으로 응답 |
| pre-commit 훅 호환 | 임의 TASK 문서 스테이징 시뮬레이션 | `dict/*` 경로 체크박스가 파싱 정상 |
| 하위 호환 | 기존 `approved` ANALYZE 샘플 점검 | 필수 섹션 누락 오판정 없음 |

`./gradlew.bat build` · `./gradlew.bat test` 같은 전체 빌드는 **실행하지 않는다** — 본 작업이 Java 소스 코드에 영향을 주지 않기 때문. 이는 `test-strategy.md` 의 "테스트는 코드 변경을 검증" 원칙과 일치.

---

## 제외 사항

| 제외 항목 | 이유 |
|----------|------|
| 기존 엔티티의 컬럼 타입 일괄 리팩토링 | 리스크 과다 (DB 마이그레이션·하위 호환). 신규 엔티티부터 데이터 도메인 적용, 기존은 점진 정렬 (ANALYZE1.md §PLAN 으로 전달할 결정 사항) |
| 새 에이전트 추가 (`wtp-word-manager` 등) | 단어·도메인·용어 강결합 — 분할 시 중계 비용·Round 2 증가 (ANALYZE1.md §확정 메타-결정) |
| `AttributeConverter` / `@Enumerated` 등 JPA 매핑 변경 | 데이터 도메인 사전은 문서 표준일 뿐 — JPA 어노테이션은 `entity-patterns.md §여부(Y/N) 필드 패턴` 로 별도 관리 |
| Gradle·Spring Boot·PostgreSQL 설정 변경 | 본 작업 범위 밖 |
| 표준 용어 사전에 기존 모든 컬럼 일괄 등록 | 유지보수 부담. 신규·충돌·재사용 판단이 필요한 용어만 등록 (ANALYZE1.md §3 방침) |

---

## 하위 호환 및 리스크

| 리스크 | 완화책 |
|--------|-------|
| 기존 `approved` ANALYZE 문서가 템플릿 변경으로 "필수 섹션 누락" 으로 판정될 가능성 | `doc-harness.md` 템플릿 주석에 "과거 문서 그대로 유효, 빈 섹션 생략 가능" 명시. Phase 4 샘플 점검 |
| pre-commit 훅이 `.claude/rules/dict/` 깊은 경로를 인식하지 못할 가능성 | `doc-harness.md` 의 체크박스 규칙상 깊이 무관 — Phase 4 smoke test 로 확증 |
| `wtp-glossary-manager` 출력이 3표로 길어져 200~400단어 제한 초과 | 각 표에 "신규 항목 없음 시 섹션 생략" 허용. 장기적 에이전트 분리는 Phase 이후 재검토 (ANALYZE1.md §확정 메타-결정) |
| 신규 `DOM_*` 코드가 기존 코드 상수·상수풀과 충돌 | `DOM_*` 은 문서 코드일 뿐이므로 Java 코드에 상수 선언하지 않음. 충돌 가능성 없음 |

---

## 예상 산출물

- [태스크](../../../tasks/20260423/용어사전_3분리/TASK1.md) — 다음 단계 `/dev:task` 에서 작성 예정
- 최종 구현 결과는 `docs/results/20260423/용어사전_3분리/RESULT1.md` · `docs/reviews/20260423/용어사전_3분리/REVIEW1.md` 에 기록
