---
status: completed
created: 2026-04-17
updated: 2026-04-17
---
# 도메인 이벤트 인프라 구축 결과

## 관련 작업
- [계획안](../../../plan/20260417/도메인_이벤트_인프라/PLAN1.md)
- [태스크](../../../tasks/20260417/도메인_이벤트_인프라/TASK1.md)

## 작업 요약
`common` 모듈에 reference 프로젝트 기반의 도메인 이벤트 인프라를 구축하였다.
JPA cascade 대신 이벤트 기반으로 엔티티 간 삭제/후처리를 처리할 수 있는 기반이 마련되었다.

## 변경 사항

### 수정 파일

**`common/build.gradle`**
- `compileOnly 'org.springframework.data:spring-data-jpa:4.0.4'` 추가 (타입 참조용)
- `compileOnly 'org.springframework:spring-context:7.0.6'` 추가 (타입 참조용)
- `testImplementation` 동일 버전 추가 (테스트 컴파일 스코프)
- `testImplementation 'org.mockito:mockito-junit-jupiter:5.20.0'` 추가

**`common/src/main/java/com/mo/smartwtp/common/domain/BaseEntity.java`**
- `@Getter` 어노테이션 추가
- 수동 작성된 `getCreatedAt()`, `getUpdatedAt()` 제거 (Lombok 위임)
- Javadoc 추가

### 신규 파일

| 파일 | 역할 |
|------|------|
| `common/.../event/DomainEventHolder.java` | 이벤트 등록/조회/클리어 인터페이스 |
| `common/.../domain/DomainEventEntity.java` | BaseEntity 확장 + `@Transient List<Object> domainEvents` 관리 |
| `common/.../event/AbstractDomainEventPublisher.java` | `saveAndPublish`, `deleteAndPublish`, `publishAndClear` 4종 메서드 제공 |
| `common/.../event/DomainEventEntityTest.java` | 이벤트 등록/조회(불변)/클리어/순서 단위 테스트 5건 |
| `common/.../event/AbstractDomainEventPublisherTest.java` | Mockito 기반 발행 흐름 단위 테스트 5건 |

### 주요 설계 결정
- common 모듈에 Spring BOM이 없으므로 의존성 버전을 명시 (api 모듈 실제 사용 버전 기준)
- `DomainEventEntity`에 Lombok 미적용 — `@Transient final` 필드와 `@NoArgsConstructor` 충돌 방지
- `AbstractDomainEventPublisher`는 abstract 클래스이므로 `@RequiredArgsConstructor` 대신 명시적 생성자 사용

## 테스트 결과

```
BUILD SUCCESSFUL in 15s
21 actionable tasks: 21 executed

common:test      ✓ (DomainEventEntityTest 5건, AbstractDomainEventPublisherTest 5건)
api:test         ✓ (기존 테스트 영향 없음)
scheduler:test   ✓ (기존 테스트 영향 없음)
```

## 비고
- `AsyncConfig` (`@EnableAsync` + `ThreadPoolTaskExecutor`)는 첫 `@Async` 핸들러 구현 시 해당 모듈(api/scheduler)에서 추가
- 구체 `EventPublisher` / `EventHandler` 는 각 도메인 기능 개발 시 `AbstractDomainEventPublisher`를 상속하여 구현
- `RefreshToken`은 이벤트가 불필요하므로 `BaseEntity` 그대로 유지
