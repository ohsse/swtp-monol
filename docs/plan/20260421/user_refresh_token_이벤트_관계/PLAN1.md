---
status: approved
created: 2026-04-21
updated: 2026-04-21
---
# RefreshToken ↔ User 관계/이벤트 설계 도입

## 목적

`User` 비활성화(논리삭제) 및 물리삭제 시 연관된 `RefreshToken`이 자동으로 revoke/삭제되도록
프로젝트 표준 이벤트 파이프라인(`DomainEventEntity` + `AbstractDomainEventPublisher` + `@TransactionalEventListener`)을 적용한다.

## 배경

- `User`/`RefreshToken` 모두 `BaseEntity` 상속 → 이벤트 발행 불가
- `UserService.deactivateUser()`가 `use_yn='N'` 전환만 수행, RefreshToken은 활성 상태 잔류 (보안 이슈)
- 물리 삭제 시 FK 위반으로 DELETE 자체 실패 위험
- DB FK(`fk_refresh_token_p_user_id`)는 정합성 안전망으로만 사용 — `ON DELETE CASCADE` 미적용 (사용자 확정)

## 범위

- `common` 모듈: `User` 엔티티 계층 승격, 이벤트 record 2종 추가
- `api` 모듈: EventPublisher, EventHandler, Repository 메서드 추가, Service/Controller 수정

## 구현 방향

### 엔티티 계층 승격
- `User extends BaseEntity` → `User extends DomainEventEntity`
- `implements Persistable<String>` 유지 (외부할당 PK, `DomainEventEntity`가 `BaseEntity` 상속하므로 동작 무변)

### 이벤트 정의 (2종, common 모듈)
- `UserDeactivatedEvent(String userId)` — 논리삭제 시 발행 → RefreshToken revoke
- `UserDeletedEvent(String userId)` — 물리삭제 시 발행 → RefreshToken DELETE

### Publisher (api 모듈)
- `UserEventPublisher extends AbstractDomainEventPublisher<User>`
  - `deactivateAndPublish(user)` — `publishAndClear(user, event)` (save 없음)
  - `deleteAndPublish(user)` — `super.deleteAndPublish(user, event)` (delete + publish)

### Handler (api 모듈)
- `UserEventHandler`
  - `@TransactionalEventListener(BEFORE_COMMIT)` — `UserDeactivatedEvent` → token.revoke()
  - `@TransactionalEventListener(BEFORE_COMMIT)` — `UserDeletedEvent` → repository.deleteByUserId()

### Service 변경
- `deactivateUser()`: user.deactivate() 후 `userEventPublisher.deactivateAndPublish(user)` 호출
- `deleteUser()` (신규): 물리삭제 — `userEventPublisher.deleteAndPublish(user)`

### Controller 변경
- `DELETE /users/{userId}` 엔드포인트 추가 (ADMIN 전용, Swagger 완비)

## 테스트 전략

- `UserServiceTest`: deactivateUser/deleteUser 이벤트 발행 검증
- `UserEventHandlerTest` (신규): revoke/deleteByUserId 호출 검증, 토큰 없는 케이스

## 제외 사항

- `RefreshToken`의 `DomainEventEntity` 승격
- DB `ON DELETE CASCADE` 전환
- 다중 세션 지원

## 예상 산출물
- [태스크](../../../tasks/20260421/user_refresh_token_이벤트_관계/TASK1.md)
