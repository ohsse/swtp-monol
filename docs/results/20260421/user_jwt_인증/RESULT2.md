---
status: draft
created: 2026-04-21
updated: 2026-04-21
---
# 사용자 로그인 · JWT 토큰 관리 — Fix Cycle 2 결과

## 관련 작업
- [계획안](../../../plan/20260421/user_jwt_인증/PLAN2.md)
- [태스크](../../../tasks/20260421/user_jwt_인증/TASK2.md)
- [이전 리뷰](../../../reviews/20260421/user_jwt_인증/REVIEW1.md)

## 작업 요약
REVIEW1의 블로커 3건(B1·B2·B3)과 중간 3건(M1·M2·M3)을 해소했다.

## 변경 사항

### B1 — UserController.getUser 보안 수정
- `api/src/main/java/com/mo/smartwtp/user/dto/UserDto.java` 신설
  - `userPw` 제외, `UserDto.from(User)` 정적 팩토리
- `api/src/main/java/com/mo/smartwtp/user/web/UserController.java` 수정
  - `getUser` 반환 타입 `CommonResponseDto<User>` → `CommonResponseDto<UserDto>`

### B2 — AuthController.logout null 가드
- `api/src/main/java/com/mo/smartwtp/auth/web/AuthController.java` 수정
  - `subject == null`이면 `JwtErrorCode.MISSING_ACCESS_TOKEN` 예외 발생

### B3 — user_m.sql 인덱스 추가
- `api/src/main/resources/db/migration/user_m.sql` 수정
  - `CREATE INDEX IF NOT EXISTS idx_user_m_use_yn ON user_m (use_yn)` 추가

### M1 — UserUpsertDto 모듈 분리
- `common/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java` 삭제
- `api/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java` 신설 (패키지명 동일 유지)
- 빈 `common/src/main/java/com/mo/smartwtp/user/dto/` 디렉토리 제거

### M2 — Bean Validation 적용
- `api/src/main/java/com/mo/smartwtp/auth/dto/LoginRequestDto.java`: `@NotBlank`
- `api/src/main/java/com/mo/smartwtp/auth/dto/RefreshRequestDto.java`: `@NotBlank`
- `api/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java`: `@NotBlank`·`@NotNull`
- `api/src/main/java/com/mo/smartwtp/auth/web/AuthController.java`: `@Valid`
- `api/src/main/java/com/mo/smartwtp/user/web/UserController.java`: `@Valid`
- `api/src/main/java/com/mo/smartwtp/api/config/web/RestApiAdvice.java`: `MethodArgumentNotValidException` 핸들러 추가

### M3 — RoleGuard 패키지 이동
- `api/src/main/java/com/mo/smartwtp/auth/web/RoleGuard.java` 삭제
- `api/src/main/java/com/mo/smartwtp/auth/guard/RoleGuard.java` 신설
- `UserController` import 갱신

## 테스트 결과
```
./gradlew.bat :common:build   → BUILD SUCCESSFUL
./gradlew.bat :api:build      → BUILD SUCCESSFUL
./gradlew.bat :scheduler:build → BUILD SUCCESSFUL
./gradlew.bat :api:test       → BUILD SUCCESSFUL (테스트 캐시 히트, 기존 통과 유지)
```

## 비고
- 낮음 항목(L1·L2·L3)은 이번 사이클 범위 외. 후속 사이클이나 별도 태스크로 처리 가능.
- `updateUser`에 `@Valid`를 적용했으나 `userPw`는 수정 시 null 허용 정책이 요구될 수 있음. 현재는 `@NotBlank`로 필수 처리 중 — 추후 등록/수정 DTO 분리 검토 필요.
