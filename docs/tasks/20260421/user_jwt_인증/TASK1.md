---
status: completed
created: 2026-04-21
updated: 2026-04-21
---
# 사용자 로그인 · JWT 토큰 관리 구현

## 관련 계획
- [계획안](../../../plan/20260421/user_jwt_인증/PLAN1.md)

## Phase

### Phase 1: 네이밍 규칙 기반 엔티티 컬럼명 정렬

- [x] `.claude/rules/naming.md` 접미사 규칙에 `_pw` 반영 (Password/비밀번호/user_pw)
- [x] `common/src/main/java/com/mo/smartwtp/common/domain/BaseEntity.java` 컬럼명 변경
  - `created_at` → `rgstr_dtm`, `updated_at` → `updt_dtm`
- [x] `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java` 컬럼명 변경
  - `expires_at` → `expr_dtm`, `revoked_at` → `revoke_dtm`, `last_used_at` → `last_used_dtm`

### Phase 2: User 도메인 (common 모듈)

- [x] `common/src/main/java/com/mo/smartwtp/user/domain/UserRole.java` 생성 (enum ADMIN/USER)
- [x] `common/src/main/java/com/mo/smartwtp/user/domain/User.java` 생성
  - `@Entity @Table(name = "user_m")`, `BaseEntity` 상속
  - 필드: `userId(user_id)`, `userNm(user_nm)`, `userPw(user_pw)`, `userRole(user_role)`, `useYn(use_yn)`, `rgstrId(rgstr_id)`, `updtId(updt_id)`
  - 정적 팩토리: `User.create(userId, userNm, encodedPw, role, registrarId)`
  - 변경 메서드: `changePw`, `changeInfo`, `deactivate`
- [x] `common/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java` 생성

### Phase 3: 비밀번호 인코더 Bean 및 의존성

- [x] `api/build.gradle` 에 `implementation 'org.springframework.security:spring-security-crypto'` 추가
- [x] `api/src/main/java/com/mo/smartwtp/auth/config/PasswordEncoderConfig.java` 생성
  - `@Bean BCryptPasswordEncoder passwordEncoder()`

### Phase 4: User CRUD (api 모듈)

- [x] `api/src/main/java/com/mo/smartwtp/user/exception/UserErrorCode.java` 생성
  - `USER_NOT_FOUND(404)`, `DUPLICATE_USER_ID(409)`, `INVALID_USER_PW(400)`, `FORBIDDEN_ROLE(403)`
- [x] `api/src/main/java/com/mo/smartwtp/user/repository/UserRepository.java` 생성
  - `JpaRepository<User, String>`, `findByUserIdAndUseYn`
- [x] `api/src/main/java/com/mo/smartwtp/user/service/UserService.java` 생성
  - 등록/수정/조회/비활성화 (BCrypt 인코딩 포함)
- [x] `api/src/main/java/com/mo/smartwtp/user/web/UserController.java` 생성
  - Swagger 태그 `"01. 사용자 관리"`
  - `POST /api/users` (ADMIN only), `GET /api/users/{userId}`, `PUT /api/users/{userId}`, `DELETE /api/users/{userId}` (논리 삭제)

### Phase 5: 인증 API (auth 런타임 확장)

- [x] `api/src/main/java/com/mo/smartwtp/auth/exception/AuthErrorCode.java` 생성
  - `LOGIN_FAILED(401)`, `FORBIDDEN(403)`
- [x] `api/src/main/java/com/mo/smartwtp/auth/dto/LoginRequestDto.java` 생성 (`userId`, `userPw`)
- [x] `api/src/main/java/com/mo/smartwtp/auth/dto/RefreshRequestDto.java` 생성 (`refreshToken`)
- [x] `api/src/main/java/com/mo/smartwtp/auth/dto/TokenResponseDto.java` 생성
  - (`accessToken`, `refreshToken`, `accessExprDtm`, `refreshExprDtm`, `role`)
- [x] `api/src/main/java/com/mo/smartwtp/auth/service/AuthService.java` 생성
  - `login(userId, rawPw)`, `logout(subject)`, `refresh(refreshToken)`
- [x] `api/src/main/java/com/mo/smartwtp/auth/web/RoleGuard.java` 생성
  - `requireAdmin(HttpServletRequest)` — claims 에서 role 추출 후 ADMIN 아니면 FORBIDDEN
- [x] `api/src/main/java/com/mo/smartwtp/auth/web/AuthController.java` 생성
  - Swagger 태그 `"00. 인증"`
  - `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`

### Phase 6: 설정 변경 및 DB 스키마

- [x] `api/src/main/resources/application.yml` 수정
  - `access-token-expiration-minutes` 기본값 30 → 60
  - `exclude-paths` 에 `/api/auth/login`, `/api/auth/refresh` 추가
- [x] `api/src/main/resources/db/migration/user_m.sql` 생성
  - `CREATE TABLE user_m` DDL
  - `INSERT INTO user_m` 초기 관리자(admin/admin BCrypt 해시) 시드 SQL

### Phase 7: 테스트 작성

- [x] `api/src/test/java/com/mo/smartwtp/user/service/UserServiceTest.java` 생성
  - 중복 ID 예외, 비밀번호 인코딩, 비활성화 후 조회 불가 검증
- [x] `api/src/test/java/com/mo/smartwtp/auth/service/AuthServiceTest.java` 생성
  - 로그인 성공, BCrypt 불일치, 비활성 계정 차단 검증

### Phase 8: 빌드 검증

- [x] `./gradlew.bat :common:build` 실행 성공 확인
- [x] `./gradlew.bat :api:build` 실행 성공 확인
- [x] `./gradlew.bat :scheduler:build` 실행 성공 확인
- [x] `./gradlew.bat :api:test` 실행 성공 확인

## 산출물
- [결과](../../../results/20260421/user_jwt_인증/RESULT1.md)
