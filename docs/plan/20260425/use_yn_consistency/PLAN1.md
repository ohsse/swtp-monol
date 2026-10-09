---
status: approved
created: 2026-04-25
updated: 2026-04-25
---
# `use_yn` 표준 정합성 정렬

## 목적

`use_yn` 컬럼의 표준 정합성 정렬을 완료한다. ANALYZE1 에서 도출된 6개 안건 결론에 따라 (1) 표준 사전·룰 정책 명문화 + (2) 기존 DDL 정렬 + (3) 엔티티 패턴 적합성 검증을 수행한다.

## 배경

- [도메인 분석](../../../analyze/20260425/use_yn_consistency/ANALYZE1.md) (status: approved)
- 5인 팀 회의 결과 안건 6건 결론 도출
- 룰 갱신 5건 완료 (ANALYZE 단계에서 선행 적용):
  - `standard-words.md` — `use` 표준 단어 신규 등록
  - `standard-data-domains.md` — `DOM_YN` 비고 보강 (SQL 타입 고정 + DDL 정책 위임 주석)
  - `standard-terms.md` — `use_yn` 사용 테이블·비고 갱신
  - `entity-patterns.md` — §여부(Y/N) 필드 패턴 보강 (DEFAULT 미사용·deactivate·BaseEntity 제외)
  - `db-indexing-and-migration.md` — §3 `DOM_YN` DDL 정책 신규 절 추가

## 범위

본 PLAN 의 작업 범위는 **DDL 정렬에 한정** 한다. 마스터 테이블 `use_yn` 신규 컬럼 추가는 옵션 X (보류) 를 기본 채택.

### 포함 범위

| 항목 | 내용 | 위치 |
|------|------|------|
| **A. user_m DDL 정의 갱신** | `CREATE TABLE` 의 `use_yn` 정의를 `VARCHAR(1) NOT NULL` 로 변경 (DEFAULT/INDEX 제거) | `api/src/main/resources/db/migration/user_m.sql` |
| **B. 기존 환경 마이그레이션** | `ALTER COLUMN TYPE` + `DROP INDEX` SQL 신규 작성 | `api/src/main/resources/db/migration/user_m_use_yn_alignment.sql` (신규) |
| **C. 회귀 테스트 실행** | 기존 단위·통합 테스트 통과 확인 (User/Pump/PumpInterlock 도메인) | `:api:test`, `:common:test` |

### 제외 범위 (옵션 X 기본 채택)

| 항목 | 사유 | 후속 |
|------|------|------|
| `pwtf_m.use_yn` 추가 | AI 운전 모드 강제 전환 스케줄러·알람 평가 로직 필터링 동반 필요 | 별도 ANALYZE |
| `dwt_m.use_yn` 추가 | InterlockValidator 비활성 배수지 `min_req_prsr` 인터록 평가 제외 로직 동반 필요 | 별도 ANALYZE |
| `pump_cmbn_m.use_yn` 추가 | 부작용 최소이지만 옵션 X 기본 채택 — 작업 단위 명확성 우선 | 별도 ANALYZE 또는 옵션 Y 변경 시 본 PLAN 재범위화 |

## 사용자 결정 필요 항목

PLAN approved 의 전제. 명시 응답 없으면 default 채택.

### 결정 1: `pump_cmbn_m.use_yn` 추가 여부

| 옵션 | 내용 | default |
|------|------|--------|
| **X** (보류) | 본 작업 범위는 DDL 정렬에 한정. `pump_cmbn_m.use_yn` 은 별도 ANALYZE | ✅ |
| Y (단독 추가) | 본 PLAN 에 `pump_cmbn_m.use_yn` 컬럼 추가 + 엔티티 변경 (PumpCombination 신규 또는 기존 갱신) 포함. AI 예측 후보 필터링 로직 변경은 별도 ANALYZE | — |

### 결정 2: `pwtf_m`·`dwt_m` 별도 ANALYZE 보류 확정

종속 로직(스케줄러·InterlockValidator) 동반 필요 — 본 작업 범위 외. **별도 ANALYZE 보류 동의** default.

### 결정 3: 마이그레이션 위치

| 옵션 | 내용 | default |
|------|------|--------|
| **A** | `api/src/main/resources/db/migration/user_m_use_yn_alignment.sql` (도메인별 SQL 패턴, refresh_token_p.sql 과 동일 위치) | ✅ |
| B | `common/src/main/resources/db/init/V7__use_yn_alignment.sql` (Flyway-style V버전 — pumpcontrol V1~V6 동일 위치) | — |

> default A 사유: `user_m` 은 api 모듈 영역. api/db/migration 에는 V버전 패턴이 없고 도메인별 SQL 파일(`user_m.sql`, `refresh_token_p.sql`) 직접 배치. 모듈 경계 일관성 우선.

## 구현 방향

### DB 설계 변경

**A. user_m.sql 갱신 (신규 환경 적용)**

```sql
-- BEFORE
use_yn    CHAR(1)      NOT NULL DEFAULT 'Y',
...
CONSTRAINT pk_user_m PRIMARY KEY (user_id),
CONSTRAINT ck_user_m_use_yn CHECK (use_yn IN ('Y', 'N')),
CONSTRAINT ck_user_m_role   CHECK (user_role IN ('ADMIN', 'USER'))
...
CREATE INDEX IF NOT EXISTS idx_user_m_use_yn ON user_m (use_yn);

-- AFTER
use_yn    VARCHAR(1)   NOT NULL,
...
CONSTRAINT pk_user_m PRIMARY KEY (user_id),
CONSTRAINT ck_user_m_use_yn CHECK (use_yn IN ('Y', 'N')),  -- 현상 유지 (DDL 정책 §3.2)
CONSTRAINT ck_user_m_role   CHECK (user_role IN ('ADMIN', 'USER'))
...
-- idx_user_m_use_yn 삭제
```

- admin INSERT 문은 이미 `use_yn = 'Y'` 명시 → DEFAULT 제거 영향 없음
- `ck_user_m_use_yn` CHECK 는 §3.2 정책에 따라 **현상 유지** (admin 초기 INSERT 안전망)

**B. user_m_use_yn_alignment.sql (기존 환경 마이그레이션)**

```sql
-- 기존 환경에서 적용 (user_m 이미 생성된 환경)
ALTER TABLE user_m ALTER COLUMN use_yn TYPE VARCHAR(1) USING use_yn::VARCHAR;
ALTER TABLE user_m ALTER COLUMN use_yn DROP DEFAULT;
DROP INDEX IF EXISTS idx_user_m_use_yn;
```

- `ALTER COLUMN TYPE` 은 `ACCESS EXCLUSIVE` 락 발생 — admin 1행 환경에서 수 밀리초 무시 가능
- `DROP INDEX` 는 운영 환경에서 `CONCURRENTLY` 적용 권장 (현 단계는 운영 도입 전이므로 단순 DROP)

### 엔티티 패턴 영향

User/Pump/PumpInterlock 엔티티는 **변경 없음**. 이미 `entity-patterns.md §여부(Y/N) 필드 패턴` 에 부합:

```java
@Enumerated(EnumType.STRING)
@Column(name = "use_yn", nullable = false, length = 1)
private YnType useYn;
```

DDL `length = 1` 과 Java `length = 1` 매칭 확인 — VARCHAR(1) 정렬 후에도 동일.

### 테스트 영향

- `UserServiceTest` (통합 테스트, `@SpringBootTest`, `@Transactional`) — DB 스키마 변경 후 회귀 검증
- `PumpInterlockScenarioTest`, `PumpMasterCacheServiceTest`, `InterlockValidatorTest` (단위 테스트) — `useYn` 필드 변경 없음, 영향 없음 예상
- `AuthServiceTest` (단위 테스트) — `findByUserIdAndUseYn` 호출, mock 기반이라 DDL 영향 없음

## 테스트 전략

본 PLAN 은 신규 기능 추가 없음 — **회귀 테스트 중심**.

| 테스트 유형 | 대상 | 검증 항목 |
|----------|------|----------|
| 단위 (회귀) | `AuthServiceTest`, `UserServiceTest`(단위 부분 없음 — 통합), 펌프 도메인 5건 | 기존 통과 유지 |
| 통합 (회귀) | `UserServiceTest`, `PumpControlIntegrationTest` | DDL 변경 후 JPA 매핑 정상 동작 |
| 빌드 | `:common:build`, `:api:build`, `:scheduler:build` | 컴파일·QClass 재생성 정상 |

> Testcontainers 미도입 (`test-strategy-e2e-roadmap.md` 참조) — DDL 검증은 로컬 PostgreSQL `application-test.yml` 환경에서 수행

### 신규 테스트 작성 여부

신규 기능 추가 없음 — **신규 테스트 작성 불필요**. ANALYZE 결론에 따른 룰 정책 정렬만 수행.

## 제외 사항

- `pwtf_m`·`dwt_m`·`pump_cmbn_m` 의 `use_yn` 컬럼 추가 (별도 ANALYZE)
- AI 운전 모드 스케줄러 필터링 로직 변경
- InterlockValidator 비활성 배수지 제외 로직 변경
- BaseEntity `use_yn` 추상화 (4건 누적 시 재검토)
- `user_m.ck_user_m_use_yn` CHECK 제약 제거 (현상 유지 정책)
- 운영 도입 후 대용량 마이그레이션 전략 (현 시점 admin 1행, 무중단 변경 불필요)

## 예상 산출물

- [태스크](../../../tasks/20260425/use_yn_consistency/TASK1.md) (다음 단계 — `/dev:task use_yn_consistency` 자동 전이)
- [결과](../../../results/20260425/use_yn_consistency/RESULT1.md)
- [리뷰](../../../reviews/20260425/use_yn_consistency/REVIEW1.md)
