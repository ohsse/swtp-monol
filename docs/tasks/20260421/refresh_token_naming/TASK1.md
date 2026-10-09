---
status: completed
created: 2026-04-21
updated: 2026-04-21
---
# RefreshToken 명명규칙 정합 및 DDL 작성 — 작업 목록

## 관련 계획
- [계획안](../../../plan/20260421/refresh_token_naming/PLAN1.md)

## Phase

### Phase 1: 엔티티 정합 (common 모듈)
- [x] `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java` — `@Table(name)` 을 `"refresh_token_p"` 로 변경
- [x] `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java` — `@Id` 필드에 `@GeneratedValue(strategy = GenerationType.UUID)` 추가
- [x] `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java` — 필드 `subject`(`@Column name="subject"`, length=100) → `userId`(`@Column name="user_id"`, length=50) 변경
- [x] `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java` — 감사 필드 `rgstrId`(`@Column name="rgstr_id"`, length=50), `updtId`(`@Column name="updt_id"`, length=50) 추가
- [x] `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java` — `AllArgsConstructor` 파라미터 순서 맞추어 `create()` 정적 팩토리 시그니처 변경: `(String userId, String tokenHash, LocalDateTime exprDtm, String registrarId)`, 내부 수동 UUID 제거
- [x] `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java` — `rotate(String tokenHash, LocalDateTime exprDtm, LocalDateTime lastUsedDtm, String updaterId)` 시그니처 확장 및 `this.updtId` 갱신 추가
- [x] `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java` — `revoke(LocalDateTime revokeDtm, String updaterId)` 시그니처 확장 및 `this.updtId` 갱신 추가

### Phase 2: 리포지토리 및 서비스 변경 (api 모듈)
- [x] `api/src/main/java/com/mo/smartwtp/auth/repository/RefreshTokenRepository.java` — `findBySubject(String subject)` → `findByUserId(String userId)` 이름 변경
- [x] `api/src/main/java/com/mo/smartwtp/auth/service/JwtTokenManagementService.java` — `private static final String AUDIT_ACTOR = "system";` 상수 추가
- [x] `api/src/main/java/com/mo/smartwtp/auth/service/JwtTokenManagementService.java` — `findBySubject(subject)` 호출 3곳을 `findByUserId(subject)` 로 변경
- [x] `api/src/main/java/com/mo/smartwtp/auth/service/JwtTokenManagementService.java` — `RefreshToken.create(...)` 호출에 `AUDIT_ACTOR` 인자 추가
- [x] `api/src/main/java/com/mo/smartwtp/auth/service/JwtTokenManagementService.java` — `rotate(...)` 호출 2곳에 `AUDIT_ACTOR` 인자 추가
- [x] `api/src/main/java/com/mo/smartwtp/auth/service/JwtTokenManagementService.java` — `token.revoke(...)` 호출에 `AUDIT_ACTOR` 인자 추가

### Phase 3: 테스트 수정 (api 모듈)
- [x] `api/src/test/java/com/mo/smartwtp/auth/service/JwtTokenManagementServiceTest.java` — mock `findBySubject(any())` → `findByUserId(any())` 변경
- [x] `api/src/test/java/com/mo/smartwtp/auth/service/JwtTokenManagementServiceTest.java` — `token.getSubject().equals(subject)` → `token.getUserId().equals(subject)` 변경

### Phase 4: DDL 스크립트 신규 작성 (api 모듈)
- [x] `api/src/main/resources/db/migration/refresh_token_p.sql` — 파일 신규 생성 (`CREATE TABLE IF NOT EXISTS refresh_token_p`, 컬럼/PK/UK/FK/INDEX/COMMENT 모두 포함, `user_m.sql` 양식 준수)

### Phase 5: 빌드 및 테스트 검증
- [x] `./gradlew.bat :common:build` 성공 확인 (QClass 재생성 포함)
- [x] `./gradlew.bat :api:build` 성공 확인
- [x] `./gradlew.bat :scheduler:build` 성공 확인 (의존성 회귀 없음)
- [x] `./gradlew.bat :api:test` 성공 확인 (JwtTokenManagementServiceTest 포함)

## 산출물
- [결과](../../../results/20260421/refresh_token_naming/RESULT1.md)
