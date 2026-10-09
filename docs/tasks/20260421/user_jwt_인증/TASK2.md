---
status: completed
created: 2026-04-21
updated: 2026-04-21
---
# 사용자 로그인 · JWT 토큰 관리 — Fix Cycle 2 구현

## 관련 계획
- [계획안](../../../plan/20260421/user_jwt_인증/PLAN2.md)

## Phase

### Phase 1: B3 — user_m.sql 인덱스 추가

- [x] `api/src/main/resources/db/migration/user_m.sql` 수정 — `CREATE INDEX IF NOT EXISTS idx_user_m_use_yn ON user_m (use_yn);` 추가

### Phase 2: B2 — AuthController.logout null 가드

- [x] `api/src/main/java/com/mo/smartwtp/auth/web/AuthController.java` 수정 — subject null 검사 후 `JwtErrorCode.MISSING_ACCESS_TOKEN` 예외 추가

### Phase 3: M3 — RoleGuard 패키지 이동

- [x] `api/src/main/java/com/mo/smartwtp/auth/guard/RoleGuard.java` 신규 파일 생성 (기존 내용 이동, 패키지 선언 변경)
- [x] `api/src/main/java/com/mo/smartwtp/auth/web/RoleGuard.java` 파일 삭제
- [x] `api/src/main/java/com/mo/smartwtp/user/web/UserController.java` import 갱신 (`auth.guard.RoleGuard`)

### Phase 4: M1 — UserUpsertDto api 모듈로 이동

- [x] `api/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java` 신규 파일 생성 (기존 내용 이동, 패키지 동일)
- [x] `common/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java` 파일 삭제
- [x] 빈 디렉토리 `common/src/main/java/com/mo/smartwtp/user/dto/` 정리 (git rm 또는 삭제)

### Phase 5: B1 — UserDto 신설 및 컨트롤러 반환 타입 교체

- [x] `api/src/main/java/com/mo/smartwtp/user/dto/UserDto.java` 신규 파일 생성 (`userPw` 미포함, 정적 팩토리 `UserDto.from(User)`)
- [x] `api/src/main/java/com/mo/smartwtp/user/web/UserController.java` 수정 — `getUser` 반환 타입 `CommonResponseDto<User>` → `CommonResponseDto<UserDto>`, `UserDto.from(user)` 매핑 적용

### Phase 6: M2 — Bean Validation 적용

- [x] `api/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java` 수정 — `userId`·`userNm`·`userPw`·`userRole` 에 `@NotBlank`/`@NotNull` 추가
- [x] `api/src/main/java/com/mo/smartwtp/auth/dto/LoginRequestDto.java` 수정 — `userId`·`userPw` 에 `@NotBlank` 추가
- [x] `api/src/main/java/com/mo/smartwtp/auth/dto/RefreshRequestDto.java` 수정 — `refreshToken` 에 `@NotBlank` 추가
- [x] `api/src/main/java/com/mo/smartwtp/auth/web/AuthController.java` 수정 — `login`·`refresh` `@RequestBody` 앞에 `@Valid` 추가
- [x] `api/src/main/java/com/mo/smartwtp/user/web/UserController.java` 수정 — `registerUser`·`updateUser` `@RequestBody` 앞에 `@Valid` 추가
- [x] `api/src/main/java/com/mo/smartwtp/api/config/web/RestApiAdvice.java` 수정 — `MethodArgumentNotValidException` 핸들러 추가 (`CommonErrorCode.INVALID_REQUEST`로 매핑)

### Phase 7: 빌드 및 테스트 검증

- [x] `./gradlew.bat :common:build` 실행 성공 확인
- [x] `./gradlew.bat :api:build` 실행 성공 확인
- [x] `./gradlew.bat :scheduler:build` 실행 성공 확인
- [x] `./gradlew.bat :api:test` 실행 성공 확인

## 산출물
- [결과](../../../results/20260421/user_jwt_인증/RESULT2.md)
