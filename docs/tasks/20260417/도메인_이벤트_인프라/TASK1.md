---
status: completed
created: 2026-04-17
updated: 2026-04-17
---
# 도메인 이벤트 인프라 구축 태스크

## 관련 계획
- [계획안](../../../plan/20260417/도메인_이벤트_인프라/PLAN1.md)

## Phase

### Phase 1: 빌드 설정
- [x] `common/build.gradle` 수정 — `compileOnly 'org.springframework.data:spring-data-jpa'` 추가
- [x] `common/build.gradle` 수정 — `compileOnly 'org.springframework:spring-context'` 추가

### Phase 2: 핵심 인프라 구현
- [x] `common/src/main/java/com/mo/smartwtp/common/event/DomainEventHolder.java` 생성
- [x] `common/src/main/java/com/mo/smartwtp/common/domain/DomainEventEntity.java` 생성
- [x] `common/src/main/java/com/mo/smartwtp/common/event/AbstractDomainEventPublisher.java` 생성

### Phase 3: BaseEntity 정리
- [x] `common/src/main/java/com/mo/smartwtp/common/domain/BaseEntity.java` 수정 — `@Getter` 추가, 수동 getter 제거

### Phase 4: 단위 테스트
- [x] `common/src/test/java/com/mo/smartwtp/common/event/DomainEventEntityTest.java` 생성
- [x] `common/src/test/java/com/mo/smartwtp/common/event/AbstractDomainEventPublisherTest.java` 생성

### Phase 5: 빌드 검증
- [x] `./gradlew.bat :common:test` 실행 성공 확인
- [x] `./gradlew.bat clean build` 실행 성공 확인

## 산출물
- [결과](../../../results/20260417/도메인_이벤트_인프라/RESULT1.md)
