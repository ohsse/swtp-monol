---
status: approved
created: 2026-05-08
updated: 2026-05-08
---
# 사용자관리개선 — 사용자 전체 목록 조회 + 본인 정보 수정 API 도입

## 목적

기존 `UserController` 에 누락된 두 가지 운영 기능을 추가한다:
1. ADMIN 전용 사용자 전체 목록 조회 (활성+비활성 포함, `useYn DESC, userId ASC` 정렬)
2. 인증된 사용자 본인 정보 수정 (이름·비밀번호) — `userRole` 변경은 차단 (권한 격상 방지)

## 배경

- 현재 `GET /api/users/{userId}` 단건 조회만 존재 — ADMIN 이 등록된 사용자 현황 파악·비활성 사용자 식별 수단 부재
- 본인 정보 수정 경로 부재 — 사용자가 이름·비밀번호를 직접 갱신할 수 없으며, ADMIN 의 `PUT /api/users/{userId}` 로도 비밀번호 갱신 불가 (해당 엔드포인트는 `userNm`·`userRole` 만 변경, `User.changePw` 는 별도 메서드)
- ANALYZE1 결정 사항 (2026-05-08, [`ANALYZE1`](../../../analyze/20260508/사용자관리개선/ANALYZE1.md)):
  - 신규 어휘 등록 0건, 신규 DB 컬럼 0건, 마이그레이션 SQL 0건
  - DTO suffix 컨벤션 정합 — `MyUserProfileUpsertDto` · `MyUserPasswordUpsertDto` (`*UpdateDto` 회피)
  - `INVALID_CURRENT_PASSWORD(400)` 신규 ErrorCode 도입
  - `MyUserController` 신규 분리 (`MyMenuController` 선례 정합 + SRP)
  - **RT 폐기 정책 옵션 B 채택** (별도 보안 사이클로 분리, 본 작업 범위 외)

## 범위

### 포함
- `UserController.findAllUsers` 엔드포인트 추가 (`GET /api/users`)
- `MyUserController` 신규 작성 (`/api/users/me` 프리픽스) — 본인 조회 + 이름 변경 + 비밀번호 변경 3 엔드포인트
- `UserService` 신규 메서드 3건 (`findAllUsers`, `changeMyProfile`, `changeMyPassword`)
- `UserRepository.findAllByOrderByUseYnDescUserIdAsc` 메서드명 쿼리 추가
- `MyUserProfileUpsertDto` · `MyUserPasswordUpsertDto` 신규 DTO 2건
- `UserErrorCode.INVALID_CURRENT_PASSWORD(400)` 추가
- `UserServiceTest` 신규 테스트 4건 추가

### 제외
- 비밀번호 변경 후 RT 폐기 (`UserPasswordChangedEvent` + 핸들러) — ANALYZE1 결정으로 별도 보안 사이클 위임
- 페이징·검색 조건 (`UserSearchDto`) — 사용자 결정으로 단순 전체 조회 채택
- 기존 `INVALID_USER_PW` 데드 코드 직접 삭제 — `coding-discipline.md §3.1` 본 작업과 무관한 데드 코드는 보고만 (REVIEW 또는 RESULT 의 발견 사항으로 기록)
- ADMIN 마지막 1인 소실 방지 로직 — 본 작업은 본인 수정에서 `userRole` 차단하므로 본 시나리오 미발생. ADMIN 자기 권한 격하는 `PUT /api/users/{userId}` 자기 호출 시나리오로 별도 사이클 검토

## 구현 방향

### 계층 구조 (변경 없음 — 기존 패턴 차용)

```
UserController (ADMIN 전용)         MyUserController (인증 사용자 본인)
├ POST   /api/users                 ├ GET    /api/users/me
├ GET    /api/users        ★신규     ├ PATCH  /api/users/me/profile   ★신규
├ GET    /api/users/{userId}        └ PATCH  /api/users/me/password  ★신규
├ PUT    /api/users/{userId}                ↓
├ DELETE /api/users/{userId}        UserService (기존 + 3 메서드 추가)
└ DELETE /api/users/{userId}/permanent ↓
                                    UserRepository (기존 + 1 메서드 추가)
                                            ↓
                                    user_m (변경 없음)
```

### Controller 책임 (`api/src/main/java/com/mo/swtp/user/web/`)

- **수정** `UserController.java`
  - `findAllUsers` 메서드 추가: `roleGuard.requireAdmin(request)` → `userService.findAllUsers().stream().map(UserDto::from).toList()` → `getResponseEntity(...)`
  - Swagger 어노테이션: `@Operation(summary = "사용자 전체 목록 조회 (ADMIN 전용)")`, `@ApiResponses` 200/401/403/500

- **신규** `MyUserController.java` — `MyMenuController` 패턴 차용
  - 기본 경로: `@RequestMapping("/api/users/me")`
  - Swagger: `@Tag(name = "01-1. 내 사용자 정보")`
  - `extractSubject(HttpServletRequest)` private 헬퍼 — `JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE` 추출, null 시 `RestApiException(AuthErrorCode.UNAUTHORIZED)`
  - `GET /api/users/me` → `getMyInfo` — 본인 정보 조회 (`UserDto`)
  - `PATCH /api/users/me/profile` → `changeMyProfile` (`MyUserProfileUpsertDto` body)
  - `PATCH /api/users/me/password` → `changeMyPassword` (`MyUserPasswordUpsertDto` body)

### Service 책임 (`api/src/main/java/com/mo/swtp/user/service/UserService.java`)

신규 메서드 3건 — 기존 패턴 (`@Transactional` 클래스 레벨 + 쓰기 메서드 오버라이드) 정합:

- `List<User> findAllUsers()` — readOnly, `userRepository.findAllByOrderByUseYnDescUserIdAsc()` 위임
- `void changeMyProfile(String userId, String userNm)` — `@Transactional` 오버라이드. `findActiveUser` + `user.changeInfo(userNm, null)` (`userRole=null` 로 권한 변경 차단)
- `void changeMyPassword(String userId, String currentPw, String newPw)` — `@Transactional` 오버라이드. `findActiveUser` + `passwordEncoder.matches(currentPw, user.getUserPw())` 검증 → 불일치 시 `RestApiException(UserErrorCode.INVALID_CURRENT_PASSWORD)` → 일치 시 `user.changePw(passwordEncoder.encode(newPw))`

### Repository 책임 (`api/src/main/java/com/mo/swtp/user/repository/UserRepository.java`)

- `List<User> findAllByOrderByUseYnDescUserIdAsc()` Spring Data 메서드명 쿼리 추가
  - 정렬: `use_yn DESC, user_id ASC` (활성 사용자 우선)
  - 인덱스: 신규 추가 0건 (DBA 검토 — 100명 미만 규모는 Seq Scan 적정)

### DTO (`api/src/main/java/com/mo/swtp/user/dto/`)

- **신규** `MyUserProfileUpsertDto.java`
  ```
  @Data @NoArgsConstructor @Schema(description = "본인 정보 수정 요청 DTO")
  - @NotBlank @Schema String userNm
  ```

- **신규** `MyUserPasswordUpsertDto.java`
  ```
  @Data @NoArgsConstructor @Schema(description = "본인 비밀번호 변경 요청 DTO")
  - @NotBlank @Schema String currentPw — 현재 비밀번호 (평문, 서버 검증)
  - @NotBlank @Schema String newPw — 새 비밀번호 (평문, 서버 BCrypt 인코딩)
  ```

- DTO 명명 근거: `naming.md §Java 클래스 네이밍` 의 `{도메인명}UpsertDto` 컨벤션 (ANALYZE1 안건 1-A 결론). 도메인명 = `MyUserProfile` / `MyUserPassword` 로 해석

### ErrorCode 변경 (`api/src/main/java/com/mo/swtp/user/exception/UserErrorCode.java`)

- 신규 행 추가: `INVALID_CURRENT_PASSWORD(400)`
- 기존 enum 값 4건 (`USER_NOT_FOUND`·`DUPLICATE_USER_ID`·`INVALID_USER_PW`·`FORBIDDEN_ROLE`) 변경 없음

### 적용 패턴 정합성 (자율 점검)

| 룰 | 적용 |
|----|-----|
| `swtp/backend/.claude/rules/api-patterns.md` Service·Controller 패턴 | `@Transactional(readOnly=true)` 클래스 레벨 + 쓰기 메서드 오버라이드. Controller `CommonController` 상속 + `getResponseEntity(...)`. DTO `@Schema` + `@NotBlank` |
| `swtp/backend/.claude/rules/exception-patterns.md` ErrorCode 규약 | `httpStatus(int)` 만 보유. `String` 필드·`getMessage()` 미선언 — `check-errorcode-contract.sh` 훅 자동 검증 |
| `swtp/backend/.claude/rules/entity-patterns.md` § 여부(Y/N) 필드 | `User.useYn` 기존 `YnType` enum 그대로 사용 |
| `coding-discipline.md §2.1` 정량 기준 | 신규 메서드 모두 50줄 미만, Controller→Service→Repository 2단 추상화 (3단 미만) |
| `coding-discipline.md §4.1` TASK 검증 형식 | 모든 체크박스에 `→ 검증:` 동반, 검증 영역 백틱 미사용 |
| `coding-discipline.md §4.3` `fix:` 첫 체크박스 RED 의무 | 본 작업 `feat:` 타입이므로 미적용 |

## 도메인 모델

본 작업은 신규 엔티티·테이블·컬럼 0건. 기존 `User` 엔티티 (common 모듈) 의 메서드만 그대로 호출.

| 엔티티/테이블 | 역할 | 주요 필드 (변경 사항) |
|------------|------|------------------|
| `User` (`user_m`) | 사용자 마스터 | 변경 없음. 기존 `changeInfo(userNm, userRole)` · `changePw(encodedPw)` 메서드 그대로 활용 |

## DB 설계 변경

본 작업은 DB 스키마 변경 0건. `common/src/main/resources/db/init/`·`db/migration/` 신규 SQL 파일 작성 0건.

- 신규 컬럼 / 신규 인덱스 / 신규 테이블 / 신규 파티션 모두 없음
- 기존 `user_m` 테이블 PK B-Tree 인덱스만 사용 (정렬 쿼리는 100명 미만 규모 Seq Scan 적정 — DBA 검토 결과)

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령 / 테스트 / 조회 |
|------|------------------------|
| `UserService.findAllUsers` 가 `useYn DESC, userId ASC` 순 반환 | UserServiceTest 신규 메서드 `전체_사용자_목록을_useYn_DESC_userId_ASC_순으로_조회한다` GREEN — registerUser 2건 (USER + ADMIN) 후 1건 deactivate, 결과 List 의 첫 번째가 활성, useYn 차원 정렬 검증 |
| `UserController.findAllUsers` ADMIN 토큰으로 200 + List<UserDto> | 수동 검증 (Swagger) — `POST /api/auth/login` ADMIN → `GET /api/users` 200 응답 + `userPw` 미포함 확인 |
| `UserController.findAllUsers` USER 토큰으로 403 FORBIDDEN | 수동 검증 (Swagger) — USER 토큰 → `GET /api/users` 403 + `code = "FORBIDDEN"` 확인 |
| `UserService.changeMyProfile` 후 `user_nm` 갱신 + `updt_dtm` JPA Auditing 자동 갱신 | UserServiceTest 신규 메서드 `본인_이름_변경_시_userNm_이_갱신된다` GREEN — registerUser → changeMyProfile → DB 행 검증 (userNm 갱신, userRole·useYn 불변) |
| `UserService.changeMyPassword` 현재 비밀번호 불일치 시 `INVALID_CURRENT_PASSWORD(400)` 예외 | UserServiceTest 신규 메서드 `본인_비밀번호_변경_시_현재_비밀번호_불일치하면_INVALID_CURRENT_PASSWORD_예외가_발생한다` GREEN — assertThatThrownBy + errorCode == INVALID_CURRENT_PASSWORD |
| `UserService.changeMyPassword` 성공 시 새 비밀번호로 `passwordEncoder.matches` 통과 | UserServiceTest 신규 메서드 `본인_비밀번호_변경_성공_시_새_비밀번호로_매칭된다` GREEN — changeMyPassword → User.userPw 가 신규 BCrypt 해시 + matches(newPw, hash) == true |
| `MyUserController` 인증 attribute 누락 시 401 UNAUTHORIZED | 수동 검증 (Swagger) — `Authorization` 헤더 없이 `GET /api/users/me` 호출 → 401 (`JwtAuthenticationFilter` 가 차단) |
| `./gradlew.bat :api:test` 전체 GREEN (회귀 0건) | 명령 실행 후 BUILD SUCCESSFUL 출력 확인 |
| `./gradlew.bat clean build` BUILD SUCCESSFUL | 명령 실행 후 출력 확인 (QClass 재생성 시 User 변경 없음 검증) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| RT 폐기 정책 — 본 작업 범위 외로 분리 | 결정 | 옵션 B 채택 (사용자 결정, 2026-05-08). `UserPasswordChangedEvent` 미도입. 향후 별도 보안 강화 ANALYZE 사이클에서 명시 결정 |
| 사용자 규모 100명 미만 가정 — 신규 인덱스 미추가 | 결정 | DBA 검토 결과 Seq Scan 적정. 향후 사용자 규모 확장 시 `(use_yn, user_id ASC)` 복합 인덱스 검토 (`use_yn = 'Y'` 등가 필터 쿼리 도입 시점) |
| ADMIN 자기 권한 격하 시 마지막 ADMIN 소실 위험 | 결정 | 본 작업 범위 외 — 본 작업은 본인 수정 API 가 `userRole` 자체를 받지 않음. `PUT /api/users/{userId}` 자기 호출 시나리오는 별도 사이클 검토 |
| `MyUserController` 의 PATCH HTTP 메서드 선택 | 결정 | 부분 갱신 의미 강조 — 이름·비밀번호는 독립 자원. PUT (전체 교체) 대신 PATCH 채택 |

분류값: 가정 / 미해결 / 결정

## 제외 사항

- DTO 클래스 레벨 그룹 검증 (`@AssertTrue` 등) — 단순 필드 검증 (`@NotBlank`) 만으로 충분
- 본인 비밀번호 변경 시 정책 검증 (최소 길이·복잡도) — 기존 `UserUpsertDto` 와 동일하게 `@NotBlank` 만 적용. 비밀번호 정책 강화는 별도 ANALYZE
- 본인 정보 수정 감사 로그 별도 테이블 — `BaseEntity` 의 `updt_dtm`·`updt_id` 자동 주입으로 충분
- ADMIN 의 본인 비밀번호 변경 분리 엔드포인트 — `MyUserController` 가 ADMIN·USER 모두 처리 (역할별 분리 불요)

## 예상 산출물

- [태스크](../../../tasks/20260508/사용자관리개선/TASK1.md) (`/dev:task` 단계에서 작성)

## 부록: 도메인/DB 검토 게이트

본 PLAN 의 "## 도메인 모델" · "## DB 설계 변경" 섹션 모두 신규 엔티티·테이블·필드·스키마 변경이 **없음** 이므로 `/dev:plan` §도메인·DB 검토 게이트 의 검토 필요 조건에 해당하지 않는다. `wtp-domain-expert` · `wtp-dba-reviewer` 추가 검토는 ANALYZE1 단계에서 이미 완료되었으므로 (안건 3·4) 본 단계 추가 호출 생략.
