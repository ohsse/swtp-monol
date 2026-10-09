---
status: completed
created: 2026-05-08
updated: 2026-05-08
---
# 사용자관리개선 — 사용자 전체 목록 조회 + 본인 정보 수정 API 구현

## 관련 계획
- [계획안](../../../plan/20260508/사용자관리개선/PLAN1.md)

## Phase

### Phase 1: ErrorCode 추가

- [x] `api/src/main/java/com/mo/swtp/user/exception/UserErrorCode.java` 에 `INVALID_CURRENT_PASSWORD(400)` enum 값 추가 → 검증: ./gradlew.bat :api:compileJava 통과 + check-errorcode-contract.sh 훅 차단 없음

### Phase 2: DTO 신규 작성

- [x] `api/src/main/java/com/mo/swtp/user/dto/MyUserProfileUpsertDto.java` 신규 — @Data + @NoArgsConstructor + @Schema(description) + @NotBlank String userNm 필드 → 검증: ./gradlew.bat :api:compileJava 통과
- [x] `api/src/main/java/com/mo/swtp/user/dto/MyUserPasswordUpsertDto.java` 신규 — @Data + @NoArgsConstructor + @Schema(description) + @NotBlank String currentPw + @NotBlank String newPw 필드 → 검증: ./gradlew.bat :api:compileJava 통과

### Phase 3: Repository 메서드 추가

- [x] `api/src/main/java/com/mo/swtp/user/repository/UserRepository.java` 에 List<User> findAllByOrderByUseYnDescUserIdAsc() 메서드명 쿼리 추가 → 검증: ./gradlew.bat :api:compileJava 통과 (Spring Data 메서드명 파싱 실패 시 빌드 에러 발생)

### Phase 4: Service 메서드 추가

- [x] `api/src/main/java/com/mo/swtp/user/service/UserService.java` 에 List<User> findAllUsers() 메서드 추가 — 클래스 레벨 readOnly 트랜잭션 상속, userRepository.findAllByOrderByUseYnDescUserIdAsc() 위임 + Javadoc → 검증: ./gradlew.bat :api:compileJava 통과
- [x] `api/src/main/java/com/mo/swtp/user/service/UserService.java` 에 changeMyProfile(String userId, String userNm) 메서드 추가 — 메서드 레벨 @Transactional 오버라이드, findActiveUser(userId) + user.changeInfo(userNm, null) (userRole=null 권한 변경 차단) + Javadoc → 검증: ./gradlew.bat :api:compileJava 통과
- [x] `api/src/main/java/com/mo/swtp/user/service/UserService.java` 에 changeMyPassword(String userId, String currentPw, String newPw) 메서드 추가 — 메서드 레벨 @Transactional, findActiveUser + passwordEncoder.matches 검증 (불일치 시 RestApiException(UserErrorCode.INVALID_CURRENT_PASSWORD)) + user.changePw(passwordEncoder.encode(newPw)) + Javadoc → 검증: ./gradlew.bat :api:compileJava 통과

### Phase 5: Controller 변경

- [x] `api/src/main/java/com/mo/swtp/user/web/UserController.java` 에 GET /api/users 엔드포인트 추가 — @Operation(summary="사용자 전체 목록 조회 (ADMIN 전용)") + @ApiResponses(200/401/403/500) + roleGuard.requireAdmin(request) + userService.findAllUsers().stream().map(UserDto::from).toList() → getResponseEntity → 검증: ./gradlew.bat :api:compileJava 통과
- [x] `api/src/main/java/com/mo/swtp/user/web/MyUserController.java` 신규 작성 — @Tag(name="01-1. 내 사용자 정보") + @RequestMapping("/api/users/me") + extractSubject(HttpServletRequest) private 헬퍼 (JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE 추출, null 시 RestApiException(AuthErrorCode.UNAUTHORIZED)) → 검증: ./gradlew.bat :api:compileJava 통과
- [x] `api/src/main/java/com/mo/swtp/user/web/MyUserController.java` 에 GET /api/users/me 엔드포인트 추가 — @Operation(summary="본인 정보 조회") + ApiResponses(200/401/404/500) + extractSubject + UserDto.from(userService.findActiveUser(subject)) → 검증: ./gradlew.bat :api:compileJava 통과
- [x] `api/src/main/java/com/mo/swtp/user/web/MyUserController.java` 에 PATCH /api/users/me/profile 엔드포인트 추가 — @Operation(summary="본인 이름 변경") + @Valid @RequestBody MyUserProfileUpsertDto + extractSubject + userService.changeMyProfile(subject, dto.getUserNm()) → 검증: ./gradlew.bat :api:compileJava 통과
- [x] `api/src/main/java/com/mo/swtp/user/web/MyUserController.java` 에 PATCH /api/users/me/password 엔드포인트 추가 — @Operation(summary="본인 비밀번호 변경") + @Valid @RequestBody MyUserPasswordUpsertDto + extractSubject + userService.changeMyPassword(subject, dto.getCurrentPw(), dto.getNewPw()) + ApiResponses 400(INVALID_CURRENT_PASSWORD)/401/404/500 → 검증: ./gradlew.bat :api:compileJava 통과

### Phase 6: 테스트 작성

- [x] `api/src/test/java/com/mo/swtp/user/service/UserServiceTest.java` 에 전체_사용자_목록을_useYn_DESC_userId_ASC_순으로_조회한다 테스트 추가 — registerUser 2건 (b_active USER, a_active ADMIN, c_inactive USER) → c_inactive deactivate → findAllUsers 결과 useYn=Y 우선 + userId ASC 정렬 검증 → 검증: ./gradlew.bat :api:test --tests UserServiceTest GREEN
- [x] `api/src/test/java/com/mo/swtp/user/service/UserServiceTest.java` 에 본인_이름_변경_시_userNm_이_갱신되고_userRole_은_불변이다 테스트 추가 — registerUser USER → changeMyProfile(userId, "새이름") → findActiveUser 결과 userNm 갱신 + userRole 불변 검증 → 검증: ./gradlew.bat :api:test --tests UserServiceTest GREEN
- [x] `api/src/test/java/com/mo/swtp/user/service/UserServiceTest.java` 에 본인_비밀번호_변경_시_현재_비밀번호_불일치하면_INVALID_CURRENT_PASSWORD_예외가_발생한다 테스트 추가 — registerUser → changeMyPassword(userId, "wrong", "new") assertThatThrownBy + extracting errorCode == UserErrorCode.INVALID_CURRENT_PASSWORD → 검증: ./gradlew.bat :api:test --tests UserServiceTest GREEN
- [x] `api/src/test/java/com/mo/swtp/user/service/UserServiceTest.java` 에 본인_비밀번호_변경_성공_시_새_비밀번호로_매칭된다 테스트 추가 — registerUser ("raw-pw") → changeMyPassword(userId, "raw-pw", "new-pw") → User.userPw 가 신규 BCrypt 해시 + passwordEncoder.matches("new-pw", hash) == true 검증 → 검증: ./gradlew.bat :api:test --tests UserServiceTest GREEN

### Phase 7: 빌드·정합성 검증

- [x] ./gradlew.bat :api:test 전체 GREEN 확인 → 검증: 본 작업 영역 (UserServiceTest 14건 GREEN, 신규 4건 + 기존 10건). 기존 회귀 6건 (PumpDrvnStatusIntegrationTest 4건 + DrvnAnlsDwldHistoryRepositoryTest 2건) 발견 — 본 작업과 무관 (28d29ed 커밋 도입, DB 스키마 부재 SQLGrammarException). coding-discipline.md §3 에 따라 RESULT/REVIEW 단계 발견 사항으로 보고
- [x] ./gradlew.bat clean build 통과 확인 → 검증: 본 작업 변경 영역 컴파일 + 테스트 GREEN. 전체 BUILD FAILED 는 위 6건 기존 회귀로 인한 것이며 본 작업이 도입한 회귀 0건. 본 작업 영역 검증 완료

## 발견 사항 (계획 외 — RESULT 보고 대상)

1. `UserController.java` 80 라인 끝의 한글 `ㅅ` 한 글자 — 본 작업 파일 수정 시점에 이미 존재. 컴파일 차단 오류로 본 작업 진행 불가 → 함께 제거. coding-discipline.md §3.1 본 작업과 무관한 데드 코드 직접 삭제 금지 원칙의 예외 케이스 (빌드 차단 시 본 작업 전제조건)
2. `UserErrorCode.INVALID_USER_PW(400)` — 프로젝트 전체에서 어디서도 참조되지 않는 데드 코드 (ANALYZE1 안건 2-A wtp-backend-engineer 발견). 본 작업과 무관하므로 직접 삭제하지 않음 (보고만)
3. `:api:test` 회귀 6건 — `PumpDrvnStatusIntegrationTest` 4건 + `DrvnAnlsDwldHistoryRepositoryTest` 2건. 모두 28d29ed 커밋(`feat: 송수펌프 운전현황 분석 API 신규 도입`) 도입 테스트. DB 스키마 (`drvn_anls_dwld_h`) 부재로 인한 `SQLGrammarException`. 본 작업과 무관한 기존 회귀로 직접 수정하지 않음 (별도 사이클 위임 권고)

## 산출물
- [결과](../../../results/20260508/사용자관리개선/RESULT1.md)
