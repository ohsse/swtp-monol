---
status: completed
created: 2026-04-25
updated: 2026-04-25
---
# `use_yn` 표준 정합성 정렬 — 작업 분해

## 관련 계획

- [계획안](../../../plan/20260425/use_yn_consistency/PLAN1.md)
- [도메인 분석](../../../analyze/20260425/use_yn_consistency/ANALYZE1.md)

## Phase

### Phase 1: user_m.sql DDL 정렬

- [x] `api/src/main/resources/db/migration/user_m.sql` `use_yn` 컬럼 정의 갱신: `CHAR(1) NOT NULL DEFAULT 'Y'` → `VARCHAR(1) NOT NULL`
- [x] `api/src/main/resources/db/migration/user_m.sql` 의 `CREATE INDEX IF NOT EXISTS idx_user_m_use_yn ON user_m (use_yn);` 라인 제거
- [x] `api/src/main/resources/db/migration/user_m.sql` 의 `ck_user_m_use_yn` CHECK 제약 현상 유지 확인 (DDL 정책 §3.2 준수, 제거 금지)
- [x] `api/src/main/resources/db/migration/user_m.sql` 의 admin INSERT 문 `use_yn = 'Y'` 명시 INSERT 유지 확인 (DEFAULT 제거 후 정상 동작 검증)

### Phase 2: 기존 환경 마이그레이션 SQL 신규 작성

- [x] `api/src/main/resources/db/migration/user_m_use_yn_alignment.sql` 신규 작성 — `ALTER TABLE user_m ALTER COLUMN use_yn TYPE VARCHAR(1) USING use_yn::VARCHAR;` + `ALTER TABLE user_m ALTER COLUMN use_yn DROP DEFAULT;` + `DROP INDEX IF EXISTS idx_user_m_use_yn;` 3문장 포함
- [x] `api/src/main/resources/db/migration/user_m_use_yn_alignment.sql` 헤더 주석 작성 — 적용 사유 (use_yn_consistency ANALYZE1·PLAN1 참조 링크), `ACCESS EXCLUSIVE` 락 영향 (admin 1행 환경 무시 가능), 운영 도입 전 전제 명시

### Phase 3: 엔티티 영향 점검 (변경 없음 검증)

- [x] `common/src/main/java/com/mo/swtp/user/domain/User.java` `useYn` 필드 정의 변경 불필요 확인 (`@Column(name = "use_yn", nullable = false, length = 1)` — DDL `VARCHAR(1)` 정렬 후 동일 매칭)
- [x] `common/src/main/java/com/mo/swtp/pump/domain/Pump.java` 동일 변경 불필요 확인
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpInterlock.java` 동일 변경 불필요 확인

### Phase 4: 회귀 테스트 실행

- [x] `./gradlew.bat :common:build` 실행 성공 확인
- [x] `./gradlew.bat :api:build` 실행 성공 확인
- [x] `./gradlew.bat :common:test` 실행 성공 확인 (회귀 검증 — `PumpTest` 등)
- [x] `./gradlew.bat :api:test` 실행 성공 확인 (회귀 검증 — `UserServiceTest`, `AuthServiceTest`, `PumpInterlockScenarioTest`, `PumpMasterCacheServiceTest`, `InterlockValidatorTest`, `PumpControlIntegrationTest`)

### Phase 5: 전체 통합 빌드 검증

- [x] `./gradlew.bat clean build` 실행 성공 확인 (QClass 재생성 포함 전체 통합)

## 산출물

- [결과](../../../results/20260425/use_yn_consistency/RESULT1.md)
- [리뷰](../../../reviews/20260425/use_yn_consistency/REVIEW1.md)
