---
status: draft
created: 2026-04-21
updated: 2026-04-21
---
# 사용자 로그인 · JWT 토큰 관리 코드 리뷰

## 관련 결과
- [결과](../../../results/20260421/user_jwt_인증/RESULT1.md)

## 리뷰 범위

- common 모듈 신규/수정 파일: `BaseEntity`, `RefreshToken`, `UserRole`, `User`, `UserUpsertDto`
- api 모듈 신규/수정 파일: `PasswordEncoderConfig`, `AuthErrorCode`, `LoginRequestDto`, `RefreshRequestDto`, `TokenResponseDto`, `AuthService`, `RoleGuard`, `AuthController`, `UserErrorCode`, `UserRepository`, `UserService`, `UserController`, `user_m.sql`
- 테스트: `AuthServiceTest`, `UserServiceTest`
- 설정: `application.yml`, `build.gradle`

## 발견 사항

### 높음 (블로커)

**[B1] `UserController.getUser` — `User` 엔티티 직접 반환으로 `userPw` 해시 노출**

`api/src/main/java/com/mo/smartwtp/user/web/UserController.java:67`

반환 타입이 `ResponseEntity<CommonResponseDto<User>>`이고, `userService.findActiveUser(userId)` 결과인 JPA 엔티티를 그대로 직렬화한다. `User.userPw` 필드에 `@JsonIgnore`가 없으므로 BCrypt 해시값이 응답 JSON에 포함된다. 엔티티를 웹 계층 DTO로 직접 반환하는 것은 `api/CLAUDE.md` "엔티티는 웹 계층 DTO를 직접 참조하지 않는다" 원칙 위반이기도 하다.

수정 방법: `api` 모듈에 `UserDto`(조회 전용 응답 DTO)를 생성하고 `userPw`를 포함하지 않도록 매핑하여 반환한다.

**[B2] `AuthController.logout` — `subject` null 방어 없음**

`api/src/main/java/com/mo/smartwtp/auth/web/AuthController.java:71-72`

`/api/auth/logout`은 `exclude-paths`에 없으므로 `JwtAuthenticationFilter`를 거치지만, 필터 비정상 경로(속성 키 오타, 예외 catch 후 체인 계속)로 인해 `subject`가 null인 채 `authService.logout(null)`이 호출될 수 있다. 이후 `revokeRefreshToken(null)`에서 NPE 또는 DB 오동작이 발생한다.

수정 방법:
```java
String subject = (String) request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE);
if (subject == null) {
    throw new RestApiException(JwtErrorCode.MISSING_ACCESS_TOKEN);
}
authService.logout(subject);
```

**[B3] `user_m.sql` — `use_yn` 인덱스 누락**

`api/src/main/resources/db/migration/user_m.sql`

`UserRepository.findByUserIdAndUseYn` 및 `AuthService.login`이 `WHERE user_id = ? AND use_yn = ?`로 조회한다. `user_id`는 PK이므로 단건 조회는 문제 없으나, 전체 활성 사용자 목록 조회(`WHERE use_yn = 'Y'`) 또는 사용자 증가 시 시퀀셜 스캔이 발생한다. `db-patterns.md §2`에 따라 빈도가 높은 조회 컬럼에는 인덱스가 필요하다.

수정 방법:
```sql
CREATE INDEX idx_user_m_use_yn ON user_m (use_yn);
```

---

### 중간

**[M1] `UserUpsertDto` — `userPw` 평문 필드가 `common` 모듈에 노출**

`common/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java:22-23`

`userPw` 필드는 평문 비밀번호를 API 요청으로 받는 API 전용 관심사다. `common/CLAUDE.md`의 허용 범위("도메인 command 성격의 XxxUpsertDto")에 형식적으로는 해당하지만, 비밀번호 필드가 포함된 DTO를 공용 모듈에 두면 scheduler 등 다른 실행 모듈에도 노출된다. `api` 모듈 전용 DTO로 분리하거나, common UpsertDto에서 `userPw`를 제거하고 api에서 별도 처리하는 것을 권장한다.

**[M2] 요청 DTO 유효성 검사 어노테이션 누락**

`api/src/main/java/com/mo/smartwtp/auth/dto/LoginRequestDto.java`
`api/src/main/java/com/mo/smartwtp/auth/web/UserController.java`

`LoginRequestDto.userId`, `LoginRequestDto.userPw`, `UserUpsertDto` 필드에 `@NotBlank` 등 Bean Validation 어노테이션이 없고, 컨트롤러 메서드에도 `@Valid`가 없다. `userId`/`userPw`가 null이면 `findByUserIdAndUseYn(null, "Y")`가 실행되어 예측 불가능한 동작이 발생한다. `api/build.gradle`에 `spring-boot-starter-validation`이 이미 선언되어 있어 추가 의존성 없이 적용 가능하다.

**[M3] `RoleGuard` 패키지 위치 — `auth.web`은 HTTP 진입점 패키지**

`api/src/main/java/com/mo/smartwtp/auth/web/RoleGuard.java`

`naming.md`에서 인증 보조 컴포넌트는 `{도메인명}Comp` 패턴으로 명명하고, `web` 패키지는 Controller·Filter 등 HTTP 진입점만 위치한다. `RoleGuard`는 `com.mo.smartwtp.auth` 패키지 또는 별도 `auth.guard` 패키지로 이동하는 것이 구조 원칙에 부합한다.

---

### 낮음

**[L1] `TokenResponseDto` 필드명 — `accessExprDtm` 접미사 미등록**

`api/src/main/java/com/mo/smartwtp/auth/dto/TokenResponseDto.java:24,27`

`naming.md` 접미사 규칙에 `_expr`(만료 일시) 항목이 없다. `RefreshToken` 엔티티의 `expr_dtm`과 일관성은 맞지만, 접미사 규칙에 명시적으로 등록이 필요하다. `naming.md`에 `expr_` (Expiration, 만료) 행을 추가하거나 표준 패턴을 확정한다.

**[L2] `AuthService.refresh` — role null 폴백이 ADMIN 사용자에게 혼란 유발 가능**

`api/src/main/java/com/mo/smartwtp/auth/service/AuthService.java:81`

AT claims에서 role을 꺼내지 못하면 `UserRole.USER`로 폴백한다. 정상 경로에서는 발생하지 않지만, 토큰 조작으로 role이 없는 AT가 유입될 경우 ADMIN 계정이 USER 권한으로 응답받는 혼란이 발생한다. null 또는 알 수 없는 role 값이면 예외를 던지는 것이 더 안전하다.

**[L3] `user_m.sql` — Flyway 네이밍 규칙 미준수**

`api/src/main/resources/db/migration/user_m.sql`

파일명이 Flyway 표준(`V{버전}__{설명}.sql`)을 따르지 않는다. Flyway를 사용하면 이 파일이 자동 실행되지 않는다. 수동 적용 방식으로 관리할 경우 디렉토리 경로(`db/migration/`)와 파일명 의도를 주석으로 명시하거나, 별도 `db/ddl/` 경로로 이동하여 의도를 구분해야 한다.

## 개선 제안

- 블로커 [B1] 수정 시 `UserDto`를 생성하면서 `GET /api/users/{userId}` Swagger 응답 스키마도 함께 업데이트한다.
- 블로커 [B2] 수정 시 `JwtTokenManagementService`의 null 처리 여부도 확인한다.
- 중간 [M2] 수정 시 `LoginRequestDto`, `RefreshRequestDto`, `UserUpsertDto` 세 곳에 동시에 적용한다.
- 낮음 [L1] 수정과 함께 `naming.md` 접미사 규칙에 `expr_` 행을 추가하여 이후 만료 일시 컬럼 네이밍을 통일한다.

## 결론

**블로커 3건 / 중간 3건 / 낮음 3건**

블로커 [B1](비밀번호 해시 노출)은 보안 취약점으로 커밋 전 반드시 수정이 필요하다. [B2](logout null 방어), [B3](인덱스 누락)도 운영 환경에서 실제로 발생할 수 있는 문제다.
