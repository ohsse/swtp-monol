---
status: approved
created: 2026-04-21
updated: 2026-04-21
---
# 사용자 로그인 · JWT 토큰 관리 — REVIEW1 블로커·중간 해소 (Fix Cycle 2)

## 목적
[REVIEW1](../../../reviews/20260421/user_jwt_인증/REVIEW1.md)에서 식별된 블로커 3건 + 중간 3건을 해소하여 안전하게 커밋 가능한 상태로 만든다. 낮음 3건(L1·L2·L3)은 본 사이클 범위에서 제외한다.

## 배경
- 1차 사이클(`PLAN1` → `RESULT1`)에서 사용자 도메인·JWT 인증 API를 구현했고 빌드·테스트가 통과했지만, 코드 리뷰에서 다음 결함이 발견되었다.
  - **B1 보안**: `UserController.getUser`가 `User` 엔티티를 직접 반환하여 BCrypt 해시(`userPw`)가 응답 JSON에 노출된다.
  - **B2 안정성**: `AuthController.logout`에서 인증 attribute가 비정상적으로 누락된 경우 `authService.logout(null)` 호출로 NPE/DB 오동작 가능.
  - **B3 성능**: `user_m.use_yn` 인덱스 누락으로 활성 사용자 목록 조회 시 시퀀셜 스캔.
  - **M1 모듈경계**: `UserUpsertDto`(평문 `userPw` 포함)가 `common` 모듈에 위치하여 scheduler 등 모든 실행 모듈에 노출.
  - **M2 입력검증**: `LoginRequestDto`/`RefreshRequestDto`/`UserUpsertDto` 필드에 Bean Validation 어노테이션 부재, 컨트롤러에 `@Valid` 부재.
  - **M3 패키지원칙**: `RoleGuard`가 HTTP 진입점 패키지(`auth.web`)에 위치 — `naming.md` 보조 컴포넌트 패턴 위반.
- 1차 사이클이 Large 규모로 진행되었으므로 동일 규모를 이어받아 PLAN2 → TASK2 → RESULT2 → REVIEW2 흐름으로 처리한다.

## 범위

### 포함 (B + M)
| ID | 제목 | 핵심 변경 |
|----|------|-----------|
| B1 | `User` 엔티티 직접 반환 제거 | `api` 모듈에 `UserDto` 신설, `UserController.getUser` 반환 타입 교체 |
| B2 | `logout` subject null 가드 | `AuthController.logout`에서 `subject == null`이면 `JwtErrorCode.MISSING_ACCESS_TOKEN` |
| B3 | `use_yn` 인덱스 | `user_m.sql`에 `CREATE INDEX idx_user_m_use_yn ON user_m (use_yn)` 추가 |
| M1 | `UserUpsertDto` 모듈 분리 | `common.user.dto.UserUpsertDto` → `api.user.dto.UserUpsertDto`로 이동, 빈 `common.user.dto` 디렉토리 정리 |
| M2 | Bean Validation | `LoginRequestDto`/`RefreshRequestDto`/`UserUpsertDto` 필드에 `@NotBlank`·`@NotNull`, 컨트롤러 `@RequestBody`에 `@Valid` |
| M3 | `RoleGuard` 패키지 이동 | `com.mo.smartwtp.auth.web.RoleGuard` → `com.mo.smartwtp.auth.guard.RoleGuard` |

### 제외
- L1 `expr_` 접미사 네이밍 합의
- L2 `AuthService.refresh`의 role null 폴백 → 예외 강화
- L3 `user_m.sql` Flyway 네이밍 규칙
- 본 코드 리뷰 외 신규 기능 추가, 다른 도메인 수정

## 구현 방향

### B1 — UserDto 신설 및 컨트롤러 반환 타입 교체
- `api/src/main/java/com/mo/smartwtp/user/dto/UserDto.java` 신설
  - 필드: `userId`, `userNm`, `userRole`, `useYn`, `rgstrId`, `updtId`, `rgstrDtm`, `updtDtm`
  - **`userPw` 미포함** (해시 노출 차단)
  - 정적 팩토리 `UserDto.from(User)` 또는 생성자 패턴
- `UserController.getUser` 반환 타입 `CommonResponseDto<User>` → `CommonResponseDto<UserDto>`
- `UserService.findActiveUser`는 엔티티 반환을 유지 (내부 사용 — `updateUser`/`deactivateUser`에서 호출 중) → 컨트롤러 단계에서 `UserDto.from(user)` 매핑

### B2 — logout subject null 가드
```java
@PostMapping("/logout")
public ResponseEntity<CommonResponseDto<Void>> logout(HttpServletRequest request) {
    String subject = (String) request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE);
    if (subject == null) {
        throw new RestApiException(JwtErrorCode.MISSING_ACCESS_TOKEN);
    }
    authService.logout(subject);
    return getResponseEntity();
}
```
- `JwtErrorCode.MISSING_ACCESS_TOKEN`은 `common/src/main/java/com/mo/smartwtp/common/exception/JwtErrorCode.java`에 이미 존재(필터에서 사용 중) — 재사용.

### B3 — `use_yn` 인덱스 추가
- `api/src/main/resources/db/migration/user_m.sql` 끝에 추가:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_user_m_use_yn ON user_m (use_yn);
  ```
- `db-patterns.md §2` 인덱스 원칙 준수 (단일 컬럼, B-Tree).

### M1 — `UserUpsertDto`를 api 모듈로 이동
- 신규 위치: `api/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java`
- 패키지명 동일 유지(`com.mo.smartwtp.user.dto`) — import 변경 없음
- 기존 파일 삭제: `common/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java`
- 빈 디렉토리 정리: `common/src/main/java/com/mo/smartwtp/user/dto/`
- 의존: `UserUpsertDto` 내부에서 `com.mo.smartwtp.user.domain.UserRole` (common) 참조는 그대로 유지 (api → common 정상 의존)
- 영향 범위: `UserService`, `UserController`는 import 경로가 그대로이므로 코드 수정 불필요(파일 위치만 이동)

### M2 — Bean Validation 적용
- `UserUpsertDto`(이동 후)
  - `userId`: `@NotBlank`
  - `userNm`: `@NotBlank`
  - `userPw`: `@NotBlank` (등록 시 필수, 수정 시 null 허용 정책은 별도 — 본 사이클은 등록 기준만 적용하고 수정 분리는 L 범주 후속 처리)
  - `userRole`: `@NotNull`
- `LoginRequestDto`: `userId`, `userPw` 모두 `@NotBlank`
- `RefreshRequestDto`: `refreshToken` `@NotBlank`
- 컨트롤러 `@RequestBody` 앞에 `@Valid` 추가
  - `AuthController.login`, `AuthController.refresh`
  - `UserController.registerUser`, `UserController.updateUser`
- 의존성: `spring-boot-starter-validation`은 이미 `api/build.gradle`에 존재 → 추가 불필요
- 검증 실패는 기존 `RestApiAdvice`의 `MethodArgumentNotValidException` 핸들러에서 처리 (없으면 다음 사이클 후속 — 본 사이클에서는 핸들러 존재 여부만 확인)

### M3 — RoleGuard 패키지 이동
- `api/src/main/java/com/mo/smartwtp/auth/web/RoleGuard.java`
  → `api/src/main/java/com/mo/smartwtp/auth/guard/RoleGuard.java`
- import만 패키지 변경 (`com.mo.smartwtp.auth.guard.RoleGuard`)
- 영향: `UserController` import 경로 갱신
- `JwtAuthenticationFilter` 상수(`AUTH_SUBJECT_ATTRIBUTE`, `AUTH_CLAIMS_ATTRIBUTE`) 참조는 그대로 유지

## 테스트 전략
- 신규/수정 단위 테스트
  - `UserServiceTest`는 영향 없음 (시그니처 불변)
  - `AuthServiceTest`는 영향 없음
  - 필요 시 `UserDto.from(...)` 매핑 단위 검증을 `UserDtoTest`로 추가(선택)
- 빌드·테스트 명령
  ```
  ./gradlew.bat :common:build
  ./gradlew.bat :api:build
  ./gradlew.bat :scheduler:build
  ./gradlew.bat :api:test
  ```
- 회귀 검증 포인트
  - `JwtAuthenticationFilterTest`가 통과하는지 확인 (B2 변경이 필터를 건드리지 않음)
  - 컴파일 단계에서 `UserUpsertDto` 이동에 따른 import 누락 즉시 검출

## 제외 사항
- 신규 사용자 도메인 기능 추가
- L 범주 항목(L1·L2·L3) — 후속 PLAN3에서 별도 진행 가능
- `RestApiAdvice`의 `MethodArgumentNotValidException` 핸들러 신규 추가 (없을 경우 RESULT2에 발견 사항으로 기록)

## 예상 산출물
- [태스크](../../../tasks/20260421/user_jwt_인증/TASK2.md)

## 변경 파일 요약
**신규**
- `api/src/main/java/com/mo/smartwtp/user/dto/UserDto.java` (B1)
- `api/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java` (M1 이동 결과)
- `api/src/main/java/com/mo/smartwtp/auth/guard/RoleGuard.java` (M3 이동 결과)

**수정**
- `api/src/main/java/com/mo/smartwtp/user/web/UserController.java` (B1·M2·M3 import)
- `api/src/main/java/com/mo/smartwtp/auth/web/AuthController.java` (B2·M2)
- `api/src/main/java/com/mo/smartwtp/auth/dto/LoginRequestDto.java` (M2)
- `api/src/main/java/com/mo/smartwtp/auth/dto/RefreshRequestDto.java` (M2)
- `api/src/main/resources/db/migration/user_m.sql` (B3)

**삭제**
- `common/src/main/java/com/mo/smartwtp/user/dto/UserUpsertDto.java` (M1)
- `api/src/main/java/com/mo/smartwtp/auth/web/RoleGuard.java` (M3)
