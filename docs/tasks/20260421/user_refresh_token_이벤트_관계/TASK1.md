---
status: completed
created: 2026-04-21
updated: 2026-04-21
---
# RefreshToken ↔ User 관계/이벤트 설계 도입

## 관련 계획
- [계획안](../../../plan/20260421/user_refresh_token_이벤트_관계/PLAN1.md)

## Phase

### Phase 1: common 모듈 — 엔티티 승격 + 이벤트 정의

- [x] `common/src/main/java/com/mo/smartwtp/user/domain/User.java` 수정 — `extends BaseEntity` → `extends DomainEventEntity` (import 교체, Persistable<String> 유지)
- [x] `common/src/main/java/com/mo/smartwtp/user/event/UserDeactivatedEvent.java` 생성 — `record UserDeactivatedEvent(String userId)`
- [x] `common/src/main/java/com/mo/smartwtp/user/event/UserDeletedEvent.java` 생성 — `record UserDeletedEvent(String userId)`

### Phase 2: api 모듈 — Publisher / Handler / Repository

- [x] `api/src/main/java/com/mo/smartwtp/user/event/UserEventPublisher.java` 생성 — `AbstractDomainEventPublisher<User>` 상속, `deactivateAndPublish` / `deleteAndPublish` 구현
- [x] `api/src/main/java/com/mo/smartwtp/user/event/UserEventHandler.java` 생성 — `@TransactionalEventListener(BEFORE_COMMIT)` 핸들러 2종 (`UserDeactivatedEvent` → revoke, `UserDeletedEvent` → deleteByUserId)
- [x] `api/src/main/java/com/mo/smartwtp/auth/repository/RefreshTokenRepository.java` 수정 — `void deleteByUserId(String userId)` 파생 쿼리 추가

### Phase 3: api 모듈 — Service / Controller

- [x] `api/src/main/java/com/mo/smartwtp/user/service/UserService.java` 수정 — `UserEventPublisher` 주입, `deactivateUser()` 수정(publishAndClear 호출), `deleteUser()` 신규 추가
- [x] `api/src/main/java/com/mo/smartwtp/user/web/UserController.java` 수정 — `DELETE /users/{userId}` 엔드포인트 추가 (ADMIN 권한, Swagger `@Tag`/`@Operation`/`@ApiResponses` 완비)

### Phase 4: 테스트

- [x] `api/src/test/java/com/mo/smartwtp/user/service/UserServiceTest.java` 수정 — `deactivateUser` Publisher 호출 검증, `deleteUser` 성공/USER_NOT_FOUND 케이스 추가
- [x] `api/src/test/java/com/mo/smartwtp/user/event/UserEventHandlerTest.java` 생성 — `UserDeactivatedEvent` revoke 검증, `UserDeletedEvent` deleteByUserId 검증, 토큰 없는 케이스

### Phase 5: 빌드 검증

- [x] `./gradlew.bat :common:build` 성공 확인
- [x] `./gradlew.bat :api:build` 성공 확인

## 산출물
- [결과](../../../results/20260421/user_refresh_token_이벤트_관계/RESULT1.md)
