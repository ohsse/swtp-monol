---
status: approved
created: 2026-04-17
updated: 2026-04-17
---
# 도메인 이벤트 인프라 구축

## 목적
엔티티 간 관계에서 발생하는 삭제/후처리를 JPA cascade로 직접 표현하지 않고,
도메인 이벤트 기반의 전처리(`BEFORE_COMMIT`) / 후처리(`AFTER_COMMIT`) 패턴으로 구현하기 위한
공통 인프라를 구축한다.

## 배경
- 프로젝트 규칙(`entity-patterns.md`, `naming.md`, `api-patterns.md`)이 이미 `DomainEventEntity` 사용을 전제로 작성되어 있으나, 실제 코드에는 `BaseEntity`만 존재하는 상태
- `reference` 프로젝트에서 동일 패턴이 10+ 도메인에 걸쳐 검증되어 있음
- 추가 런타임 의존성 없이 구현 가능 (`spring-data-jpa`, `spring-context`를 `compileOnly`로만 추가)

## 범위
`common` 모듈에만 변경이 집중됨. 기존 코드(`RefreshToken`, `BaseEntity`)에 대한 파괴적 변경 없음.

### 수정 파일
- `common/build.gradle` — `compileOnly` 의존성 2개 추가
- `common/src/main/java/com/mo/smartwtp/common/domain/BaseEntity.java` — `@Getter` 적용, 수동 getter 제거

### 신규 파일
- `common/src/main/java/com/mo/smartwtp/common/event/DomainEventHolder.java`
- `common/src/main/java/com/mo/smartwtp/common/domain/DomainEventEntity.java`
- `common/src/main/java/com/mo/smartwtp/common/event/AbstractDomainEventPublisher.java`
- `common/src/test/java/com/mo/smartwtp/common/event/DomainEventEntityTest.java`
- `common/src/test/java/com/mo/smartwtp/common/event/AbstractDomainEventPublisherTest.java`

## 구현 방향

### 아키텍처 흐름
```
[Entity] extends DomainEventEntity
    │ registerEvent(event)
    ▼
[XxxEventPublisher] extends AbstractDomainEventPublisher<Entity>
    │ saveAndPublish() / deleteAndPublish() / publishAndClear()
    │ → Spring ApplicationEventPublisher.publishEvent()
    ▼
[XxxEventHandler]
    @TransactionalEventListener(BEFORE_COMMIT)  ← 동기, 같은 트랜잭션 (연관 데이터 정리)
    @TransactionalEventListener(AFTER_COMMIT)   ← @Async, 별도 스레드 (파일/캐시/외부 연동)
```

### 핵심 설계 결정
1. **common 의존성**: `spring-data-jpa`, `spring-context`를 `compileOnly`로 추가 — 타입 참조만 필요, 런타임 전이 없음
2. **Lombok 미적용 (DomainEventEntity)**: `@Transient final List` 필드와 `@NoArgsConstructor` 충돌 가능 → 수동 구현
3. **AbstractDomainEventPublisher 메서드 4종**: `saveAndPublish`, `deleteAndPublish`, `publishAndClear(entity)`, `publishAndClear(entity, event)`
4. **AsyncConfig 제외**: 첫 `@Async` 핸들러 구현 시 해당 모듈(api/scheduler)에서 별도 추가

## 테스트 전략
- `DomainEventEntityTest`: Spring 컨텍스트 없이 순수 JUnit5로 이벤트 등록/조회/클리어 검증
- `AbstractDomainEventPublisherTest`: Mockito로 `JpaRepository` + `ApplicationEventPublisher` 모킹하여 발행 흐름 검증
- 빌드 검증: `./gradlew.bat clean build` 전체 통과

## 제외 사항
- AsyncConfig, 구체 EventPublisher/Handler: 각 도메인 기능 개발 시 추가
- RefreshToken의 DomainEventEntity 전환: 이벤트 불필요한 엔티티이므로 현행 유지

## 예상 산출물
- [태스크](../../../tasks/20260417/도메인_이벤트_인프라/TASK1.md)
