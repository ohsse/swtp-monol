---
status: approved
created: 2026-04-21
updated: 2026-04-21
---
# 사용자 로그인 · JWT 토큰 관리 코드 리뷰 — Fix Cycle 2

## 관련 결과
- [결과](../../../results/20260421/user_jwt_인증/RESULT2.md)
- [이전 리뷰](REVIEW1.md)

## 리뷰 범위

Fix Cycle 2에서 수정된 파일만 검토한다.

**신규 파일**
- `api/src/main/java/com/mo/smartwtp/user/dto/UserDto.java`
- `api/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java`
- `api/src/main/java/com/mo/smartwtp/auth/guard/RoleGuard.java`

**수정 파일**
- `api/src/main/java/com/mo/smartwtp/user/web/UserController.java`
- `api/src/main/java/com/mo/smartwtp/auth/web/AuthController.java`
- `api/src/main/java/com/mo/smartwtp/auth/dto/LoginRequestDto.java`
- `api/src/main/java/com/mo/smartwtp/auth/dto/RefreshRequestDto.java`
- `api/src/main/resources/db/migration/user_m.sql`
- `api/src/main/java/com/mo/smartwtp/api/config/web/RestApiAdvice.java`

**삭제 확인**
- `common/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java` (삭제 여부)

## 발견 사항

### 이전 블로커 해소 확인

**[B1] 해소됨 — UserController.getUser 비밀번호 해시 노출 수정**

`api/src/main/java/com/mo/smartwtp/user/dto/UserDto.java`

`userPw` 필드 없음 확인. `userId`, `userNm`, `userRole`, `useYn`, `rgstrId`, `updtId`, `rgstrDtm`, `updtDtm`만 포함하며 `UserDto.from(User)` 정적 팩토리로 변환한다.

`api/src/main/java/com/mo/smartwtp/user/web/UserController.java:68`

`getUser` 반환 타입이 `ResponseEntity<CommonResponseDto<UserDto>>`로 교체됐고, `UserDto.from(userService.findActiveUser(userId))` 반환이 확인됐다.

---

**[B2] 해소됨 — AuthController.logout subject null 가드**

`api/src/main/java/com/mo/smartwtp/auth/web/AuthController.java:74-77`

```java
String subject = (String) request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE);
if (subject == null) {
    throw new RestApiException(JwtErrorCode.MISSING_ACCESS_TOKEN);
}
```

null 가드가 올바르게 추가됐다.

---

**[B3] 해소됨 — user_m.sql use_yn 인덱스 추가**

`api/src/main/resources/db/migration/user_m.sql:37`

```sql
CREATE INDEX IF NOT EXISTS idx_user_m_use_yn ON user_m (use_yn);
```

인덱스 DDL이 추가됐다. 초기 스크립트 맥락이므로 `CONCURRENTLY` 없이 `IF NOT EXISTS`만 사용하는 것은 적절하다.

---

### 이전 중간 해소 확인

**[M1] 해소됨 — UserUpsertDto api 모듈로 분리**

`common/src/main/java/com/mo/smartwtp/user/dto/` 경로에 파일 없음 확인 (디렉토리 자체 삭제).
`api/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java`만 존재한다.
`common/CLAUDE.md`의 "API 전용 request/response DTO 금지" 원칙을 준수한다.

---

**[M2] 해소됨 — Bean Validation 및 MethodArgumentNotValidException 핸들러**

- `LoginRequestDto` — `userId`, `userPw` 모두 `@NotBlank` 적용
- `RefreshRequestDto` — `refreshToken` `@NotBlank` 적용
- `UserUpsertDto` — `userId`, `userNm`, `userPw` `@NotBlank`, `userRole` `@NotNull` 적용
- `AuthController.login`, `refresh` — `@Valid @RequestBody` 적용
- `UserController.registerUser`, `updateUser` — `@Valid @RequestBody` 적용
- `RestApiAdvice:26-31` — `MethodArgumentNotValidException` 핸들러 추가 확인

---

**[M3] 해소됨 — RoleGuard 패키지 이동**

`api/src/main/java/com/mo/smartwtp/auth/guard/RoleGuard.java:1`

`package com.mo.smartwtp.auth.guard` 확인.
`auth/web/*.java` 목록 확인 결과 `ApiErrorResponseWriter`, `JwtAuthenticationFilter`, `AuthController`만 존재하며 RoleGuard가 잔존하지 않는다.

---

### 높음 (블로커)

없음.

---

### 중간

**[N1] `UserUpsertDto.userPw @NotBlank` — 수정 API에서 불필요한 비밀번호 필수 요구**

`api/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java:26-28`
`api/src/main/java/com/mo/smartwtp/user/service/UserService.java:63-65`

`UserUpsertDto`는 등록(`POST /api/users`)과 수정(`PUT /api/users/{userId}`) 양쪽에서 공용으로 사용하는 DTO다. `userPw` 필드에 `@NotBlank`가 선언되어 있어 수정 요청 시에도 비밀번호가 필수 입력으로 강제된다. 그러나 `UserService.updateUser`는 `user.changeInfo(dto.getUserNm(), dto.getUserRole(), updaterId)`만 호출하고 `dto.getUserPw()`를 사용하지 않는다.

- 클라이언트는 이름·역할만 수정할 때도 비밀번호를 반드시 전송해야 한다.
- 사용되지 않는 평문 비밀번호가 전송 경로에 불필요하게 노출된다.
- API 계약과 서비스 구현이 불일치한다.

수정 방법 (두 가지 중 선택):

1. 등록/수정 DTO 분리: `UserRegisterDto`(userId+userNm+userPw+userRole)와 `UserUpdateDto`(userNm+userRole)로 분리
2. `userPw` 검증 완화: `@NotBlank`를 제거하고 `UserService.registerUser`에서 null 방어 추가

---

### 낮음

**[N2] `RoleGuard.requireAdmin` — subject null 반환 방어 미흡**

`api/src/main/java/com/mo/smartwtp/auth/guard/RoleGuard.java:31-34`

```java
if (claims == null || !"ADMIN".equals(claims.get("role"))) {
    throw new RestApiException(AuthErrorCode.FORBIDDEN);
}
return subject;  // subject가 null이어도 그대로 반환됨
```

`JwtAuthenticationFilter`가 정상 동작하면 claims와 subject는 항상 함께 적재된다. 그러나 exclude-paths 설정 오류 등으로 필터가 건너뛰어지면 null subject가 `registrarId`로 전달되어 DB에 null 삽입이 발생할 수 있다.

수정 방법:
```java
if (subject == null || claims == null || !"ADMIN".equals(claims.get("role"))) {
    throw new RestApiException(AuthErrorCode.FORBIDDEN);
}
```

## 개선 제안

- [N1] 수정 시 `PUT /api/users/{userId}` Swagger `summary`에 수정 대상 필드(이름·역할)를 명시한다.
- [N2] 수정이 단순하므로 [N1] 처리 시 함께 반영하는 것을 권장한다.
- REVIEW1의 낮음 항목 L1·L2·L3는 이번 사이클 범위 외이며 별도 태스크로 처리한다.

## 결론

**블로커 0건 / 중간 1건(N1) / 낮음 1건(N2)**

REVIEW1의 블로커 3건과 중간 3건이 모두 의도에 맞게 해소됐다. 신규 블로커 없으므로 커밋/PR 진행 가능. N1은 수정 API의 API 계약 불일치로 조기 해소를 권장한다.
