---
status: approved
created: 2026-04-21
updated: 2026-04-21
---
# RefreshToken 명명규칙 정합 및 DDL 작성

## 목적
JWT 리프레시 토큰 엔티티 `RefreshToken` 과 매핑 테이블 `auth_refresh_token` 을 프로젝트 명명규칙(`naming.md`, `entity-patterns.md`)에 맞게 정렬하고, 운영 DDL 스크립트를 표준 양식(`user_m.sql` 모범)으로 신규 작성한다.

## 배경
- 현재 테이블명 `auth_refresh_token` 은 `{도메인약어}_{suffix}` 형식 위반.
- 컬럼 `subject` 는 도메인 약어 prefix 가 없으며, 의미상 `user_m.user_id` 를 그대로 담고 있음.
- PK `tokenId` 는 정적 팩토리에서 `UUID.randomUUID().toString()` 을 수동 호출 — `entity-patterns.md` "PK 는 `@GeneratedValue(strategy = UUID)` 기본" 위반.
- 감사 컬럼 `rgstr_id`/`updt_id` 가 부재 — `user_m` 표준 컬럼셋과 불일치.
- `api/src/main/resources/db/migration/` 에 RefreshToken 용 DDL 부재. 운영 배포 시 수동 생성 불가.

> 사용자 결정 4건(테이블 suffix `_p`, 컬럼 `user_id`, `@GeneratedValue` UUID, 감사 컬럼 추가)은 사전 확인 완료.

## 범위
- `common` 모듈: 엔티티 1개 (`RefreshToken`)
- `api` 모듈: 리포지토리 1개, 서비스 1개, 테스트 1개, DDL 신규 1개
- 영향 도메인: `auth` 단일 도메인

### 변경 파일 목록
**수정**
- `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java`
- `api/src/main/java/com/mo/smartwtp/auth/repository/RefreshTokenRepository.java`
- `api/src/main/java/com/mo/smartwtp/auth/service/JwtTokenManagementService.java`
- `api/src/test/java/com/mo/smartwtp/auth/service/JwtTokenManagementServiceTest.java`

**신규**
- `api/src/main/resources/db/migration/refresh_token_p.sql`

## 구현 방향

### 1. 엔티티 (`RefreshToken.java`)
- `@Table(name = "auth_refresh_token")` → `@Table(name = "refresh_token_p")`
- `@Id` 에 `@GeneratedValue(strategy = GenerationType.UUID)` 추가
- 필드 `subject`(`@Column(name = "subject", length = 100)`) → `userId`(`@Column(name = "user_id", length = 50)`)
- 신규 필드 2개 추가: `rgstrId`(`@Column(name = "rgstr_id", length = 50)`), `updtId`(`@Column(name = "updt_id", length = 50)`)
- 정적 팩토리 시그니처:
  - 변경 전: `create(String subject, String tokenHash, LocalDateTime exprDtm)`
  - 변경 후: `create(String userId, String tokenHash, LocalDateTime exprDtm, String registrarId)` — 내부에서 UUID 수동 생성 제거(JPA 가 채움), `tokenId` null 전달
- 변경 메서드 시그니처 확장:
  - `rotate(tokenHash, exprDtm, lastUsedDtm, updaterId)` — `this.updtId = updaterId` 갱신
  - `revoke(revokeDtm, updaterId)` — `this.updtId = updaterId` 갱신
- `BaseEntity` 상속 유지 (`rgstr_dtm`/`updt_dtm` 자동 처리)

### 2. 리포지토리 (`RefreshTokenRepository.java`)
- `Optional<RefreshToken> findBySubject(String subject)` → `Optional<RefreshToken> findByUserId(String userId)`

### 3. 서비스 (`JwtTokenManagementService.java`)
- 클래스 상수 `private static final String AUDIT_ACTOR = "system";` 추가 (시스템 자동 발급 표기)
- `findBySubject(subject)` 호출 3곳을 `findByUserId(subject)` 로 변경 (JWT subject 변수명은 유지)
- `RefreshToken.create(subject, hash, expiresAt)` → `RefreshToken.create(subject, hash, expiresAt, AUDIT_ACTOR)`
- `existing.rotate(hash, expiresAt, null)` → `existing.rotate(hash, expiresAt, null, AUDIT_ACTOR)`
- `storedToken.rotate(hash, expiresAt, now)` → `storedToken.rotate(hash, expiresAt, now, AUDIT_ACTOR)`
- `token.revoke(now)` → `token.revoke(now, AUDIT_ACTOR)`

### 4. 테스트 (`JwtTokenManagementServiceTest.java`)
- mock 설정: `repository.findBySubject(any())` → `repository.findByUserId(any())`
- 비교: `token.getSubject().equals(subject)` → `token.getUserId().equals(subject)`
- 시나리오·assertion 변경 없음

### 5. DDL (`refresh_token_p.sql`)
`user_m.sql` 양식 그대로 차용:
- `CREATE TABLE IF NOT EXISTS refresh_token_p (...)` — 컬럼 정렬 패딩
- 컬럼:
  - `token_id      VARCHAR(36)  NOT NULL`
  - `user_id       VARCHAR(50)  NOT NULL`
  - `token_hash    VARCHAR(64)  NOT NULL`
  - `expr_dtm      TIMESTAMP    NOT NULL`
  - `revoke_dtm    TIMESTAMP`
  - `last_used_dtm TIMESTAMP`
  - `rgstr_id      VARCHAR(50)`
  - `updt_id       VARCHAR(50)`
  - `rgstr_dtm     TIMESTAMP    NOT NULL DEFAULT NOW()`
  - `updt_dtm      TIMESTAMP    NOT NULL DEFAULT NOW()`
- 제약:
  - `CONSTRAINT pk_refresh_token_p PRIMARY KEY (token_id)`
  - `CONSTRAINT uk_refresh_token_p_user_id UNIQUE (user_id)` — 1계정 1토큰 정책 강제
  - `CONSTRAINT fk_refresh_token_p_user_id FOREIGN KEY (user_id) REFERENCES user_m (user_id)` — 사용자 무결성 보장
- 인덱스: `CREATE INDEX IF NOT EXISTS idx_refresh_token_p_expr_dtm ON refresh_token_p (expr_dtm)` — 만료 토큰 정리 배치용
- `COMMENT ON TABLE` / `COMMENT ON COLUMN` 한국어 작성

## 테스트 전략
1. **단위 테스트** (`test-strategy.md §1` 표준)
   - `JwtTokenManagementServiceTest` 4개 시나리오 모두 GREEN: 토큰 발급, 회전, 만료 거부, 폐기 토큰 거부
   - `AuthServiceTest`, `JwtAuthenticationFilterTest` 회귀 GREEN
2. **빌드 회귀**: `./gradlew.bat :common:build`, `./gradlew.bat :api:build`, `./gradlew.bat :scheduler:build` 모두 성공
3. **DDL 수동 검증**: 로컬 PostgreSQL 에 `psql -f refresh_token_p.sql` 실행 후 `\d refresh_token_p` 로 제약·인덱스·코멘트 확인. UK 위반 동작 확인.

## 제외 사항
- `BaseEntity` 에 `rgstr_id`/`updt_id` 를 끌어올리는 리팩토링 — User 만 보유 중이어서 영향 범위 한정. 별도 PLAN 분리.
- 토큰 회전 이력 누적(`_h`) 정책 전환 — 본 PLAN 은 `_p` 상태 스냅샷 정책 유지.
- 지자체별 `resources-env/` DDL 분기 — 공통 `resources/db/migration/` 위치만 사용.
- Flyway/Liquibase 도입 — 현행 수동 SQL 보관 방식 유지.

## 예상 산출물
- [태스크](../../../tasks/20260421/refresh_token_naming/TASK1.md)
- [결과](../../../results/20260421/refresh_token_naming/RESULT1.md)
