---
status: completed
created: 2026-04-21
updated: 2026-04-21
---
# 사용자 로그인 · JWT 토큰 관리 구현 결과

## 관련 작업
- [계획안](../../../plan/20260421/user_jwt_인증/PLAN1.md)
- [태스크](../../../tasks/20260421/user_jwt_인증/TASK1.md)

## 작업 요약

스마트정수장 백엔드에 User 도메인과 JWT 기반 로그인·로그아웃·토큰 갱신 API를 추가했다.
기존 `JwtTokenManagementService` 인프라를 재사용하고 `User` 엔티티, `AuthService`, `AuthController`,
`UserService`, `UserController`, `RoleGuard`, `BCryptPasswordEncoder`를 신규 구현했다.
아울러 `BaseEntity`·`RefreshToken`의 컬럼명을 네이밍 규칙 기준(`rgstr_dtm`, `updt_dtm` 등)으로 정렬했다.

## 변경 사항

### 수정 파일

| 파일 | 성격 | 내용 |
|------|------|------|
| `.claude/rules/naming.md` | 수정 | 접미사 규칙에 `_pw` 반영 |
| `common/src/main/java/com/mo/smartwtp/common/domain/BaseEntity.java` | 수정 | 컬럼명 `created_at→rgstr_dtm`, `updated_at→updt_dtm`, 필드명 동기화 |
| `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java` | 수정 | 컬럼명 `expires_at→expr_dtm`, `revoked_at→revoke_dtm`, `last_used_at→last_used_dtm`, 필드명 동기화 |
| `api/build.gradle` | 수정 | `spring-security-crypto` 의존성 추가 |
| `api/src/main/resources/application.yml` | 수정 | AT 유효기간 30→60분, exclude-paths에 `/api/auth/login`, `/api/auth/refresh` 추가 |

### 신규 파일 (common 모듈)

| 파일 | 내용 |
|------|------|
| `common/src/main/java/com/mo/smartwtp/user/domain/UserRole.java` | ADMIN/USER enum |
| `common/src/main/java/com/mo/smartwtp/user/domain/User.java` | `user_m` 테이블 엔티티, 정적 팩토리·변경 메서드 포함 |
| `common/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java` | 사용자 등록/수정 요청 DTO |

### 신규 파일 (api 모듈)

| 파일 | 내용 |
|------|------|
| `api/src/main/java/com/mo/smartwtp/auth/config/PasswordEncoderConfig.java` | `BCryptPasswordEncoder` 빈 등록 |
| `api/src/main/java/com/mo/smartwtp/auth/exception/AuthErrorCode.java` | `LOGIN_FAILED(401)`, `FORBIDDEN(403)` |
| `api/src/main/java/com/mo/smartwtp/auth/dto/LoginRequestDto.java` | 로그인 요청 DTO |
| `api/src/main/java/com/mo/smartwtp/auth/dto/RefreshRequestDto.java` | 토큰 갱신 요청 DTO |
| `api/src/main/java/com/mo/smartwtp/auth/dto/TokenResponseDto.java` | 토큰 응답 DTO (AT/RT/만료일시/role) |
| `api/src/main/java/com/mo/smartwtp/auth/service/AuthService.java` | `login`, `logout`, `refresh` 구현 |
| `api/src/main/java/com/mo/smartwtp/auth/web/RoleGuard.java` | request attribute에서 role 추출, ADMIN 검증 |
| `api/src/main/java/com/mo/smartwtp/auth/web/AuthController.java` | `POST /api/auth/login`, `/refresh`, `/logout` |
| `api/src/main/java/com/mo/smartwtp/user/exception/UserErrorCode.java` | `USER_NOT_FOUND(404)`, `DUPLICATE_USER_ID(409)`, `INVALID_USER_PW(400)`, `FORBIDDEN_ROLE(403)` |
| `api/src/main/java/com/mo/smartwtp/user/repository/UserRepository.java` | `JpaRepository<User, String>`, `findByUserIdAndUseYn` |
| `api/src/main/java/com/mo/smartwtp/user/service/UserService.java` | 등록/조회/수정/비활성화 (BCrypt 인코딩 포함) |
| `api/src/main/java/com/mo/smartwtp/user/web/UserController.java` | `POST /api/users`, `GET/PUT/DELETE /api/users/{userId}` |
| `api/src/main/resources/db/migration/user_m.sql` | `user_m` DDL + 초기 admin 시드 |
| `api/src/test/java/com/mo/smartwtp/user/service/UserServiceTest.java` | 단위 테스트 4건 |
| `api/src/test/java/com/mo/smartwtp/auth/service/AuthServiceTest.java` | 단위 테스트 5건 |

## 테스트 결과

`./gradlew.bat :api:test` 전체 통과 (신규 포함 6개 클래스, 24개 테스트, 실패 0)

| 테스트 클래스 | 건수 | 결과 |
|-------------|------|------|
| `ApiPersistenceConfigTest` | 1 | 전부 통과 |
| `RestApiAdviceTest` | 6 | 전부 통과 |
| `AuthServiceTest` (신규) | 5 | 전부 통과 |
| `JwtTokenManagementServiceTest` | 4 | 전부 통과 |
| `JwtAuthenticationFilterTest` | 4 | 전부 통과 |
| `UserServiceTest` (신규) | 4 | 전부 통과 |

## 비고

- **Spring Security 전체 미도입**: `spring-security-crypto`만 추가하여 BCrypt 해싱에만 사용. Security 필터체인은 도입하지 않아 기존 `JwtAuthenticationFilter` 구조 유지.
- **ADMIN 권한 검사 위치**: `RoleGuard.requireAdmin()`이 컨트롤러 레이어에서 request attribute claims를 직접 확인하는 구조. Spring Security의 `@PreAuthorize` 없이 동일한 보호 효과를 낸다.
- **초기 admin 비밀번호**: `user_m.sql`의 BCrypt 해시는 `admin` 원문 기준이다. 운영 배포 전 반드시 변경해야 한다.
- **DB 마이그레이션 도구 미연동**: `user_m.sql`은 Flyway/Liquibase 없이 수동 적용 방식이다. 향후 마이그레이션 도구 연동 시 파일 구조를 재구성해야 한다.
