---
status: approved
created: 2026-04-23
updated: 2026-04-23
---
# 용어사전 3분리 — 도메인 분석

## 작업 배경

- 사용자가 일반적 DB 표준화 이론(표준 단어 / 표준 도메인 / 표준 용어 3분리) 을 프로젝트 사전 체계에 도입 요청
- "특정 엔터티 도메인에 대한 전문지식과 혼동되지 않도록" — 기존 비즈니스 도메인(pump·alarm) 과 DB 이론의 표준 도메인(값 형식) 사이의 **"도메인" 용어 충돌** 해소
- 본 작업은 `.claude/rules/*` · `.claude/agents/*` · `.claude/commands/dev/analyze.md` 를 대상으로 하는 **하네스·사전 체계 자체의 개편(메타 작업)**
- 외부 산출물: 없음 (사용자 구두 설명 및 DB 표준화 문헌 일반 지식 기반)

### 현 프로젝트 진단 (Plan mode Phase 1 Explore 결과)

| 항목 | 파일:라인 | 진단 |
|------|----------|------|
| 3분리 미구현 | `.claude/agents/wtp-glossary-manager.md:1-13` | "용어 사전" 으로 통째 관리. 출력 카탈로그가 단일 평면 |
| 표준 도메인(값 형식) 사전 부재 | `entity-patterns.md:90`, `db-patterns.md:22` | `YnType(VARCHAR 1)` / `NUMERIC(15,4)` 등 개별 사례만 산재. 중앙 표준 없음 |
| "도메인" 용어 충돌 | `domain-abbreviations.md:3` | 비즈니스 영역(pump/alarm) 과 DB 이론 표준 도메인(값 형식) 구분 부재 |
| `/dev:analyze` 안건 누락 | `commands/dev/analyze.md:135` | 표준 도메인(데이터타입·길이) 결정 안건 없음 |

---

## 회의 생략 사유

본 작업은 다음 3가지 이유로 5인 팀 회의를 생략한다:

1. **신규 비즈니스 용어·엔티티가 없다** — 회의 대상인 "새로 등장하는 도메인 용어" 가 pump·alarm 같은 업무 영역이 아님. 본 작업은 사전 구조 자체의 개편.
2. **메타-용어는 Plan 단계에서 확정** — 본 작업에서 결정된 용어(`data-domain`, `business-domain`, `DOM_*`) 는 DB 표준화 문헌의 관용이며, 사용자가 Plan mode 의 `AskUserQuestion` 에서 직접 선택 완료.
3. **회의 안건 자체가 개편 대상** — `/dev:analyze` 의 "신규 용어 카탈로그" 단일 평면 안건 구조가 이번 작업의 개편 대상. 구 안건으로 회의하면 신 안건(3분리) 의 결론 산출이 불가능.

대신 Plan mode 에서 Explore 3개 에이전트 병렬 조사 + Plan 에이전트 설계 검토 + 사용자 확인을 완료했으며, 결정 사항을 이하 §확정 메타-결정 · §표준 사전 카탈로그 섹션에 정식 기록한다.

---

## 확정 메타-결정 (Plan mode AskUserQuestion 결과)

| 결정 항목 | 확정안 | 기각된 대안 |
|----------|--------|------------|
| DB 이론 "표준 도메인(값 형식)" 명명 | **데이터 도메인 / `data-domain`** | 값 도메인(영문 대응어 부재), 필드 도메인(폼·UI 연상) |
| 3개 사전 파일 배치 | **`.claude/rules/dict/` 서브디렉토리 3파일 + `README.md`** | 루트 평면 배치, 단일 통합 파일 |
| 비즈니스 도메인 약어 vs 표준 단어 중복 | **`domain-abbreviations.md` 가 source of truth. `standard-words.md` 는 그 외 일반 단어만 등록** | 완전 통합(혼재 관리), 완전 분리(약어 중복) |
| `wtp-glossary-manager` 에이전트 | **단일 유지 + 검토 항목 3층 확장** | 3개 에이전트 분할(중계 비용·Round 2 증가) |
| 충돌 분류 4분류(신규/재사용/유사충돌/폐기·통합) | **층위별로 그대로 적용, 유사충돌 판정 기준만 층위별 구체화** | 새 분류 추가 |

---

## 표준 사전 카탈로그 (신설 사전의 초기 등록안)

### 1) 신규 표준 단어 (비즈니스 도메인 약어 외의 일반 단어)

| 영문 약어 | 한글 논리명 | 풀네임 | 분류 | 비고 |
|----------|-----------|--------|------|------|
| `nm` | 이름 | name | 신규 | 빈번 사용 — `user_nm` 등 |
| `dt` | 일자 | date | 신규 | `DOM_DT` 매핑 |
| `dtm` | 일시 | datetime | 신규 | `DOM_DTM` 매핑 — `rgstr_dtm` 등 |
| `amt` | 금액 | amount | 신규 | `DOM_AMT_15_2` 매핑 |
| `qty` | 수량 | quantity | 신규 | `DOM_QTY_15_4` 매핑 |
| `cd` | 코드 | code | 신규 | `DOM_CODE_20` 매핑 |
| `yn` | 여부 | yes/no | 기존 재사용 | `YnType` enum 존재 — `entity-patterns.md:79-105` 참조 |
| `seq` | 순번 | sequence | 신규 | 일련번호 |
| `ord` | 순서 | order | 신규 | 정렬 순서 |
| `cnt` | 개수 | count | 신규 | 집계용 |
| `rgstr` | 등록(동사) | register | 신규 | `rgstr_dtm` 등 공통 메타 필드 |

### 2) 신규 표준 데이터 도메인 (값 형식)

| 도메인 코드 | 한글명 | SQL 타입 | Java 타입 | NULL | 비고 (역공학 출처) |
|-----------|--------|---------|---------|------|------------------|
| `DOM_ID_50` | 식별자 50자 | VARCHAR(50) | String | NOT NULL | `user_m.user_id` — `entity-patterns.md:55` |
| `DOM_ID_36` | UUID 식별자 | VARCHAR(36) | String | NOT NULL | 기본 UUID PK 패턴 — `entity-patterns.md` |
| `DOM_NAME_100` | 일반 이름 | VARCHAR(100) | String | NULL 허용 | 빈번한 이름 필드 |
| `DOM_CODE_20` | 코드값 | VARCHAR(20) | String | NOT NULL | 공통 코드 필드 |
| `DOM_YN` | 여부 | VARCHAR(1) | YnType enum | NOT NULL | 기존 재사용 — `entity-patterns.md §여부(Y/N) 필드 패턴` |
| `DOM_DTM` | 일시 | TIMESTAMP | LocalDateTime | NULL 허용 | `rgstr_dtm`, `updt_dtm` 공통 메타 |
| `DOM_DT` | 일자 | DATE | LocalDate | NULL 허용 | 일 단위 |
| `DOM_QTY_15_4` | 수량·측정값 | NUMERIC(15,4) | BigDecimal | NULL 허용 | `rawdata_m.tag_val` — `db-patterns.md:22` |
| `DOM_AMT_15_2` | 금액 | NUMERIC(15,2) | BigDecimal | NULL 허용 | 표준 회계 정밀도 |
| `DOM_TEXT` | 긴 설명 | TEXT | String | NULL 허용 | 설명·메모 |

### 3) 신규 표준 용어 (역공학된 대표 사례만)

| 물리명 | 한글 논리명 | 조합 | 데이터 도메인 | 사용 테이블 | 분류 |
|--------|-----------|------|-------------|-----------|------|
| `user_id` | 사용자 ID | user(비즈니스) + id | `DOM_ID_50` | `user_m` | 기존 재사용 |
| `use_yn` | 사용 여부 | use + yn | `DOM_YN` | `user_m` 외 공통 | 기존 재사용 |
| `rgstr_dtm` | 등록 일시 | rgstr + dtm | `DOM_DTM` | 공통 메타 | 기존 재사용 |
| `tag_val` | 태그 측정값 | tag + val | `DOM_QTY_15_4` | `rawdata_m` | 기존 재사용 |

> 표준 용어 사전은 **모든 컬럼을 등록하지 않는다**. 신규 설계·충돌·재사용 판단이 필요한 용어만 등록하여 유지보수 부담을 최소화한다.

---

## 기존 사전·패턴과의 충돌 및 해소

| 충돌 항목 | 현 상태 | 해소책 |
|----------|--------|-------|
| "도메인" 용어 이중 사용 | `domain-abbreviations.md:3` 의 "도메인" = 비즈니스 영역. DB 이론 = 값 형식 | `domain-abbreviations.md` 제목을 "비즈니스 도메인 약어 사전" 으로 명확화. 신설 `standard-data-domains.md` 는 "데이터 도메인" 으로 명명 통일 |
| `YnType` 정의 분산 | `entity-patterns.md:79-105` + `naming.md:48-54` + `api-patterns.md` 3곳 | `DOM_YN` 으로 승격. 세 파일 모두 `standard-data-domains.md#DOM_YN` 참조로 단순화 |
| `naming.md` Java 필드 타입 매핑 표가 1줄 (`_yn → YnType` 만) | 실질적 표준 역할을 못함 | 해당 섹션을 제거하고 "데이터 도메인 사전 참조" 로 대체. 데이터 도메인 사전이 Java 타입 매핑의 1차 소스 |
| 기존 `domain-abbreviations.md` 의 등록 약어(user·pump·raw·ctrl·alarm 등) | "도메인명 약어 + 표준 단어" 의 이중 역할 | 현 위치 유지. `standard-words.md` 는 서두에 "본 사전은 `domain-abbreviations.md` 에 등록된 비즈니스 도메인 약어를 참조만 하며 중복 등록하지 않는다" 명시 |

---

## PLAN 으로 전달할 결정 사항

- **사전 파일 4개 신설**: `.claude/rules/dict/{README,standard-words,standard-data-domains,standard-terms}.md`
- **기존 룰 파일 수정**: `domain-abbreviations.md`(제목·서두 명확화), `naming.md`(Java 필드 타입 매핑 → 데이터 도메인 참조), `doc-harness.md`(ANALYZE 템플릿 3표 구조 + 하위 호환 주석)
- **에이전트 개편**: `wtp-glossary-manager.md`(description·검토 항목·출력 템플릿·관리 파일 목록 확장), `wtp-dba-reviewer.md`(description 에 "데이터 도메인 2차 승인 책임" 한 줄 추가)
- **커맨드 개편**: `commands/dev/analyze.md`(회의 안건 + 출력 섹션 3분리 + 룰 갱신 지시서 예시 갱신)
- **CLAUDE.md 인덱스 표**: `dict/*` 3개 파일 추가
- **Phase 구성**: 4 Phase (사전 신설 → 에이전트 → 커맨드·템플릿 → 검증). TASK 분할 불필요 (Phase 수 < 10, 체크박스 수 < 60 예상)
- **적용 패턴**: 
  - 하위 호환 유지 — 기존 `status: approved` ANALYZE 문서는 3표 구조 없이도 유효. 템플릿 주석에 "해당 섹션이 비어있으면 생략 가능" 명시
  - pre-commit 훅 영향 없음 — 체크박스 파싱은 디렉토리 깊이 무관

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

> **⚠️ 예외 처리**: 본 작업은 **룰 파일 자체를 대상으로 하는 메타 작업**이다. 일반적으로 ANALYZE 의 룰 갱신 지시서는 PLAN 승인 전에 체크박스를 모두 완료해야 하지만, 이번 작업에서는 체크박스가 가리키는 파일 생성·수정 자체가 IMPL 단계의 Phase 1~3 과 일치한다. 따라서 **PLAN approved 전제조건에서 체크박스 완료를 면제**하고, IMPL 단계에서 해당 파일을 생성·수정하는 것으로 체크박스 완료를 갈음한다. 사용자 승인 시 본 예외 처리를 함께 승인하는 것으로 간주한다.

**Phase 1 — 사전 파일 신설 및 기존 룰 명확화**
- [ ] `.claude/rules/dict/README.md` 신규 작성 — 3개 사전 상호참조 인덱스
- [ ] `.claude/rules/dict/standard-words.md` 신규 작성 — 표준 단어 11개 초기 등록
- [ ] `.claude/rules/dict/standard-data-domains.md` 신규 작성 — 데이터 도메인 10개 초기 등록
- [ ] `.claude/rules/dict/standard-terms.md` 신규 작성 — 대표 용어 4개 초기 등록
- [ ] `.claude/rules/domain-abbreviations.md` 제목·서두를 "비즈니스 도메인 약어 사전" 으로 명확화
- [ ] `.claude/rules/naming.md` "Java 필드 타입 매핑" 표 → 데이터 도메인 참조로 재구성
- [ ] `CLAUDE.md` §규칙 문서 인덱스 표에 `dict/*` 3개 파일 추가

**Phase 2 — 에이전트 개편**
- [ ] `.claude/agents/wtp-glossary-manager.md` description · 검토 항목 · 출력 템플릿 · 관리 파일 목록 3층 확장
- [ ] `.claude/agents/wtp-dba-reviewer.md` description 에 데이터 도메인 2차 승인 책임 한 줄 추가

**Phase 3 — 회의 안건·템플릿 확장**
- [ ] `.claude/rules/doc-harness.md` ANALYZE 템플릿 `## 신규 용어 카탈로그` → `## 표준 사전 카탈로그` 3표 구조 + 하위 호환 주석
- [ ] `.claude/commands/dev/analyze.md` 회의 안건에 "표준 데이터 도메인 결정" 추가 + 출력 섹션 3분리 + 룰 갱신 지시서 예시 갱신

**Phase 4 — 검증** (파일 수정 없음, 실행 검증만)

---

## 산출물

- [계획안](../../../plan/20260423/용어사전_3분리/PLAN1.md) — 다음 단계 `/dev:plan` 에서 작성 예정
