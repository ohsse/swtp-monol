---
status: approved
created: 2026-04-25
updated: 2026-04-25
---
# `use_yn` 표준 정합성 — 도메인 분석

## 작업 배경

스마트정수장 백엔드의 `use_yn` (사용 여부 활성 플래그) 컬럼이 현재 3개 엔티티/테이블에서 비일관적으로 정의되어 있다. 표준 데이터 도메인 `DOM_YN`이 이미 사전에 등록되어 있음에도 DDL 작성 시 SQL 타입·CHECK·DEFAULT·INDEX 정책이 테이블마다 다르게 적용되어, 향후 신규 마스터 테이블 도입 시 일관성을 강제할 사전 기준이 부재하다.

본 ANALYZE 는 `DOM_YN` 표준 데이터 도메인 정책 보강 + `use_yn` 표준 용어 보유 정책 명문화 + 기존 DDL 정렬을 통합 검토한다.

### 비일관성 현황

| 테이블 | DDL 정의 | CHECK | DEFAULT | INDEX | 위치 |
|------|--------|------|--------|-------|------|
| `user_m` | `CHAR(1) NOT NULL DEFAULT 'Y'` | `IN ('Y','N')` | `'Y'` | `idx_user_m_use_yn` | `api/src/main/resources/db/migration/user_m.sql` |
| `pump_m` | `VARCHAR(1) NOT NULL` | 없음 | 없음 | 없음 | `common/src/main/resources/db/init/V1__pumpcontrol_master_tables.sql` |
| `pump_interlock_p` | `VARCHAR(1) NOT NULL` | 없음 | 없음 | 없음 | 동일 |
| `pwtf_m`, `dwt_m`, `pump_cmbn_m` | (컬럼 없음) | — | — | — | — |

표준 데이터 도메인 `DOM_YN` = `VARCHAR(1)` / `YnType` enum / NOT NULL.
Java 엔티티는 모두 `@Enumerated(EnumType.STRING)` + `private YnType useYn` 일관 패턴.

### 외부 산출물

본 작업은 외부 요구사항·다이어그램 산출물 없음. 코드·DDL·표준 사전 자체가 분석 입력.

---

## 회의록 (5인 팀 토픽 주도)

### 안건 1: `DOM_YN` SQL 타입 — `CHAR(1)` vs `VARCHAR(1)` 통일

- 호출 에이전트: `wtp-dba-reviewer`, `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: PostgreSQL 에서 `CHAR(1)`·`VARCHAR(1)` 저장 크기 동일하나 `CHAR` 는 trailing space 패딩 처리 차이로 쿼리 이식성 혼란 위험. `DOM_YN = VARCHAR(1)` 사전 확정 상태에서 `user_m.use_yn` `CHAR(1)` 유지는 표준 위반. **`VARCHAR(1)` 정렬 권장**, 데이터 1행이므로 정렬 비용 무시 가능.
  - **wtp-glossary-manager**: `DOM_YN` 비고에 SQL 타입 고정 명문화 필요 (현재 비고에 "CHAR(1) 금지" 근거 없음). DDL 운영 정책(CHECK·DEFAULT·인덱스)은 DBA 영역 — 데이터 도메인 사전은 위임 주석으로 분리.
- **결론**: `user_m.use_yn` `CHAR(1)` → `VARCHAR(1)` 무중단 변경. `standard-data-domains.md` `DOM_YN` 비고에 "SQL 타입 `VARCHAR(1)` 고정, `CHAR(1)` 금지(PostgreSQL trailing space 패딩 차이)" 명문화.

### 안건 2: DB CHECK 제약 정책

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: Java `@Enumerated(EnumType.STRING)` + `YnType` enum 이 'Y'/'N' 외 값을 1차 차단. DB CHECK 는 ORM 우회 직접 INSERT 2차 방어선이지만 추가 유지 비용 발생. 기존 `user_m.ck_user_m_use_yn` 은 admin 초기 INSERT 안전망으로 작동 중이므로 제거 이득 없음. 신규 테이블 일괄 적용은 향후 enum 확장 시 결합 비용. **(c) 기존 CHECK 현상 유지, 신규 테이블 미추가** — 단일 방어선(Java enum) 으로 수렴.
- **결론**: `user_m.ck_user_m_use_yn` 현상 유지. `pump_m`·`pump_interlock_p` 및 향후 신규 테이블에는 CHECK 미추가.

### 안건 3: DDL `DEFAULT 'Y'` 정책

- 호출 에이전트: `wtp-dba-reviewer`, `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: (c) — 외부 직접 INSERT 가 명확한 `user_m` 만 유지, 신규 테이블은 미추가. 마이그레이션 SQL 에서 `use_yn` 미명시 INSERT 시 NOT NULL 위반 방지 안전망.
  - **wtp-backend-engineer**: (c) — DDL DEFAULT **일괄 제거**, Java 정적 팩토리에서 `YnType.Y` 명시 할당 의무화. 두 곳 분산은 DRY 위반. `entity-patterns.md §여부(Y/N) 필드 패턴` 코드 예제가 이미 명시 할당 정책. 컴파일 타임 안전성(`@AllArgsConstructor(PRIVATE)` 생성자 강제) 우월.
- Round 2: 미수행. 오케스트레이터 종합 판단으로 처리.
- **오케스트레이터 종합**: 두 답변 모두 (c) 라벨이지만 적용 범위가 다름. 결정 근거:
  - `api/src/main/resources/db/migration/user_m.sql` 의 admin INSERT 문은 이미 `use_yn` 을 `'Y'` 로 명시 INSERT 하고 있어 `DEFAULT 'Y'` 가 실제 사용되는 경로가 없다.
  - DBA 가 우려한 "외부 직접 INSERT" 시나리오는 운영 절차 문서화 영역이며 현 시점 그러한 절차 미정의.
  - Java 패턴 일관성 + DRY 우월. **Backend 옵션 채택**.
- **결론**: 모든 use_yn 컬럼에서 DDL `DEFAULT 'Y'` 제거. Java 정적 팩토리에서 `YnType.Y` 명시 할당 정책을 `entity-patterns.md` 에 명문화. `user_m.sql` 의 `DEFAULT 'Y'` 도 제거 (admin INSERT 영향 없음).

### 안건 4: `use_yn` 인덱스 정책

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: (c) — 카디널리티 2(Y/N) 단독 인덱스는 옵티마이저가 Seq Scan 선호 → 실효성 낮음. 활성 비율 높은 마스터 테이블은 부분 인덱스(`WHERE use_yn = 'Y'`) 도 미적용 권장. 단독 인덱스 미적용, 복합 인덱스의 **후위 컬럼**으로만 포함. **`user_m.idx_user_m_use_yn` `DROP INDEX CONCURRENTLY` 권고** (`db-indexing-and-migration.md §1` 카디널리티 원칙 위반).
- **결론**: `idx_user_m_use_yn` 제거. 신규 테이블 단독 인덱스 미적용. 복합 인덱스 후위 컬럼으로만 포함.

### 안건 5: 마스터 테이블 `use_yn` 보유 정책

- 호출 에이전트: `wtp-glossary-manager`, `wtp-domain-expert`, `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: (b) — 비활성 운영 시나리오가 있는 테이블만 선별 보유. 표준 사전은 "선별 기준" 만 명문화하고 강제 아님. 더불어 `use_yn` 의 조합 재료 `use` 가 `standard-words.md` 에 미등록 — **신규 표준 단어 등록 필요**.
  - **wtp-domain-expert**: 옵션 B — 3개 모두 비활성 시나리오 실재 (정수조 격리/배수지 리모델링/펌프 조합 폐기). 단:
    - `pwtf_m` 추가 시 AI 운전 모드 강제 전환 스케줄러·알람 평가 로직이 `use_yn=N` 행 명시 필터링 필요 — **블로커성 종속 작업**
    - `dwt_m` 추가 시 `InterlockValidator` 가 비활성 배수지의 `min_req_prsr` 인터록 평가 제외 로직 필요 — **블로커성 종속 작업**
    - `pump_cmbn_m` — AI 예측 후보 필터링만 추가하면 부작용 최소, 알람·강제 전환 직접 결합 없음
  - **wtp-backend-engineer**: BaseEntity 추상화 **배제** (감사 메타와 활성 상태 의미 범주 다름 — SRP 위반). 엔티티별 명시 반복 + 정적 팩토리 내부 `YnType.Y` 고정. 4건 누적 시 `@MappedSuperclass ActivatableEntity` 재검토.
- Round 2: 미수행. 작업 범위 결정 이슈로 PLAN 단계에 옵션 전달.
- **오케스트레이터 종합**: Glossary "선별" + Domain "3개 모두 (단 종속 로직 동반)" 의 본질은 일치 — 정책은 선별, 다만 3개 모두 비활성 시나리오 존재 평가. 종속 로직(스케줄러·InterlockValidator) 까지 다루면 본 작업 규모 Medium → Large 격상.
- **결론**:
  - 표준 정책 명문화: `standard-terms.md` `use_yn` 비고를 "비활성 운영 시나리오가 존재하는 `_m` 테이블에 선별 보유 — 전체 `_m` 의무 아님" 으로 갱신.
  - 본 작업 범위 컬럼 추가 결정은 **PLAN 단계에서 사용자 선택**:
    - 옵션 X: 컬럼 추가 보류 — 본 작업은 정책 명문화 + 기존 DDL 정렬에 한정. `pwtf_m`/`dwt_m`/`pump_cmbn_m` 추가는 각각 별도 ANALYZE.
    - 옵션 Y: `pump_cmbn_m` 만 단독 추가 — 부작용 최소, 종속 로직 불필요. `pwtf_m`/`dwt_m` 은 별도 ANALYZE 보류.
  - BaseEntity 추상화 배제 — 명시 반복 패턴 유지.

### 안건 6: `user_m.use_yn` `CHAR(1)` → `VARCHAR(1)` 무중단 마이그레이션

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 단일 `ALTER TABLE user_m ALTER COLUMN use_yn TYPE VARCHAR(1) USING use_yn::VARCHAR` 적용 가능. NOT NULL 재설정 불필요. `db-indexing-and-migration.md §2` NOT NULL 추가 3단계와 무관(이미 NOT NULL). `ACCESS EXCLUSIVE` 락이지만 admin 1행 환경에서 수 밀리초 무시 가능. 운영 도입 후 적용 시 점검 시간대(00:00~06:00) 실행. `ALTER COLUMN TYPE` 은 `CONCURRENTLY` 불가 — 대용량 시 섀도우 컬럼 방식 적용 필요.
- **결론**: 운영 도입 전이므로 단일 ALTER 문으로 처리. V7 마이그레이션 SQL 신규 작성 (`common/src/main/resources/db/init/V7__use_yn_alignment.sql`).

---

## 표준 사전 카탈로그

### 신규 표준 단어

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `use` | 사용(동사) | 신규 | `standard-words.md` 미등록. `user`(비즈니스 도메인 약어, `domain-abbreviations.md`) 와 다른 단어 — 의미·어근 충돌 없음. `use_yn` 조합의 1차 재료 (`wtp-glossary-manager` 검출) |

### 신규 표준 데이터 도메인

| 도메인 코드 | SQL 타입 | Java 타입 | NULL | 분류 | 결정 근거 |
|-----------|---------|---------|------|------|----------|
| (없음) | — | — | — | — | `DOM_YN` 기존 정의 비고 보강만 발생 (분류: 기존 재사용) |

### 신규 표준 용어

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| (없음) | — | — | — | `use_yn` 기존 정의 사용 테이블·비고 갱신만 발생 (분류: 기존 재사용) |

분류값 (3층 공통): **신규 / 기존 재사용 / 유사 충돌 / 폐기·통합**

---

## 신규 엔티티/DB 컬럼

본 작업은 **신규 엔티티 미생성**. DDL 정렬·정책 명문화·옵션 Y 선택 시 `pump_cmbn_m`(기존 엔티티 `PumpCombination` — 미정) 에 컬럼 1건 추가.

### DDL 변경 항목

| 대상 | 변경 내용 | 사용 데이터 도메인 |
|------|---------|------------------|
| `user_m.use_yn` | `CHAR(1)` → `VARCHAR(1)`, `DEFAULT 'Y'` 제거 | `DOM_YN` |
| `idx_user_m_use_yn` | `DROP INDEX CONCURRENTLY` (운영 도입 시) / `DROP INDEX` (현 단계) | — |
| `pump_m.use_yn`, `pump_interlock_p.use_yn` | DDL 변경 없음 (이미 `VARCHAR(1) NOT NULL`) | `DOM_YN` |
| `pump_cmbn_m.use_yn` (옵션 Y) | 신규 컬럼 `VARCHAR(1) NOT NULL` (DEFAULT·CHECK 미설정) | `DOM_YN` |

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 권장 해소책 |
|----------|----------|
| `standard-data-domains.md` `DOM_YN` 의 SQL 타입은 `VARCHAR(1)` 이지만 `user_m.sql` 이 `CHAR(1)` 사용 | 데이터 도메인 사전 비고에 "SQL 타입 `VARCHAR(1)` 고정, `CHAR(1)` 금지" 명문화 + `user_m.sql` 마이그레이션 |
| `standard-terms.md` `use_yn` 비고 "활성 플래그 공통 패턴" 의미 모호 | 비고를 "비활성 운영 시나리오가 존재하는 `_m` 테이블에 선별 보유 — 전체 `_m` 의무 아님" 로 갱신 |
| `entity-patterns.md §여부(Y/N) 필드 패턴` 코드 예제는 정적 팩토리 명시 할당이지만 DDL DEFAULT 와 분산 | `entity-patterns.md` 에 "DDL `DEFAULT` 미설정, Java 정적 팩토리에서 `YnType.Y` 명시 할당 의무" 한 줄 추가 |
| `db-indexing-and-migration.md §1` 카디널리티 원칙 위반 — `idx_user_m_use_yn` 단독 인덱스 | DROP. DDL 정책 (단독 인덱스 미적용·복합 인덱스 후위 컬럼만 허용) 을 룰에 추가 |
| `standard-words.md` 에 `use` 미등록 — `use_yn` 조합 재료 미정의 | `use` 표준 단어 신규 등록 |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안

- 신규 엔티티 없음
- 옵션 Y 선택 시 `PumpCombination` 엔티티 (현 미구현) 또는 신규 도입 엔티티에 `useYn` 필드 추가 — PLAN 단계 결정

### DB 설계 변경 초안

| Phase | 변경 |
|-------|------|
| V7 신규 마이그레이션 | `user_m.use_yn` 타입 변경 + `DEFAULT 'Y'` 제거 + `idx_user_m_use_yn` DROP |
| 옵션 Y (선택 시) | `pump_cmbn_m.use_yn` 신규 컬럼 추가 (V7 또는 V8 분리) |
| `user_m.sql` admin INSERT | 영향 없음 (이미 `use_yn = 'Y'` 명시) |

### 적용할 패턴

- `entity-patterns.md §여부(Y/N) 필드 패턴` 보강:
  - DDL `DEFAULT` 미설정 명시
  - 정적 팩토리 `YnType.Y` 명시 할당 의무
  - `deactivate()` 메서드 정식 코드 예제 포함
  - "BaseEntity 추상화 대상 외" 명기
- `db-indexing-and-migration.md` 보강:
  - `DOM_YN` DDL 정책 (CHECK 신규 미추가, 단독 인덱스 미적용, 복합 인덱스 후위 컬럼만 허용)

### 사용자 결정 필요 항목 (PLAN 으로 전달)

1. **옵션 X / Y 선택**: `pump_cmbn_m` 에 `use_yn` 컬럼을 본 작업에서 단독 추가할지, 별도 ANALYZE 로 보류할지
2. **`pwtf_m`·`dwt_m` 컬럼 추가 보류 확정**: 종속 로직(AI 운전 모드 스케줄러 필터링, InterlockValidator 비활성 배수지 제외) 이 본 작업 범위 외이므로 별도 ANALYZE — 사용자 동의 확인

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `.claude/rules/dict/standard-words.md` — `use` 표준 단어 신규 등록 (한글 논리명 "사용(동사)", 기본 데이터 도메인 "(조합)", 비고 "`use_yn` 조합 재료 — `user` 비즈니스 도메인 약어와 구분")
- [x] `.claude/rules/dict/standard-data-domains.md` — `DOM_YN` 행 비고에 "SQL 타입 `VARCHAR(1)` 고정, `CHAR(1)` 금지(PostgreSQL trailing space 패딩 차이)" 추가, "DDL CHECK·DEFAULT·인덱스 정책은 `db-indexing-and-migration.md` §`DOM_YN` DDL 정책 참조" 위임 주석 추가
- [x] `.claude/rules/dict/standard-terms.md` — `use_yn` 행 사용 테이블 칸을 `user_m 외 공통` → `user_m, pump_m, pump_interlock_p` 로 명시 갱신; 비고를 "비활성 운영 시나리오가 존재하는 `_m` 테이블에 선별 보유 — 전체 `_m` 의무 아님. 신규 마스터 도입 시 ANALYZE 에서 판단" 으로 갱신
- [x] `.claude/rules/entity-patterns.md` — §여부(Y/N) 필드 패턴 에 다음 추가: (1) "DDL `DEFAULT` 미설정, Java 정적 팩토리에서 `YnType.Y` 명시 할당 의무" 한 문장, (2) `deactivate()` 메서드 정식 코드 예제 포함, (3) "`use_yn` 은 `BaseEntity` 추상화 대상 외 — 감사 메타와 활성 상태 의미 범주 다름" 명기
- [x] `.claude/rules/db-indexing-and-migration.md` — `DOM_YN` DDL 정책 신규 §추가: (1) DB CHECK 제약 — 신규 테이블 미추가, 기존 `user_m.ck_user_m_use_yn` 만 현상 유지, (2) DDL `DEFAULT` 미설정, (3) 단독 인덱스 미적용·복합 인덱스 후위 컬럼만 허용

---

## 산출물

- [계획안](../../../plan/20260425/use_yn_consistency/PLAN1.md) (다음 단계 — `/dev:plan use_yn_consistency` 자동 전이)
