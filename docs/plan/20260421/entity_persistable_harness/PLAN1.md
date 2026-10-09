---
status: approved
created: 2026-04-21
updated: 2026-04-21
---
# 외부할당 PK 엔티티 `Persistable` 정규화 및 harness 규칙 보강

## 목적

외부에서 PK를 할당받는 엔티티(`@GeneratedValue` 부재)가 `Persistable<ID>`를 구현하지 않으면 Spring Data JPA `save()`가 신규 엔티티를 `em.merge()`로 처리해 불필요한 SELECT가 발생한다. 이를 수정하고 harness 규칙을 보강하여 향후 legacy 이관 엔티티에서 동일 패턴이 반복되지 않도록 한다.

## 배경

- `SimpleJpaRepository.save()`는 `isNew()` 판정 → `@GeneratedValue` 없고 `Persistable` 미구현 시 ID null-check → 신규임에도 `merge()` 호출 → 불필요한 SELECT + detached 참조 위험
- 현재 영향: `User`(PK=`userId`, String 외부할당) 1건
- 향후 영향: `pump_m`, `tag_m`, `epa_node_m` 등 legacy 자연키 기반 마스터 대량 이관 예정

## 범위

- `BaseEntity`에 `@Transient newEntity` 플래그 + `isNew()` + `@PostLoad` 추가
- `User` 엔티티 `Persistable<String>` 구현
- `entity-patterns.md`, `common/CLAUDE.md` harness 보강
- `UserServiceTest` 회귀 테스트 보강

## 구현 방향

### BaseEntity 변경
```java
@Transient
private boolean newEntity = true;

public boolean isNew() { return newEntity; }

@PrePersist
protected void onCreate() {
    ...
    this.newEntity = false;   // 추가
}

@PostLoad
protected void onLoad() {
    this.newEntity = false;
}
```

### User 변경
```java
public class User extends BaseEntity implements Persistable<String> {
    @Override
    public String getId() { return userId; }
    // isNew()는 BaseEntity 제공
}
```

### harness 규칙 보강
- `entity-patterns.md`: `## 외부 할당 PK 엔티티 패턴` 섹션 신설
- `common/CLAUDE.md`: Entity 규칙에 Persistable 가이드 1줄 추가

## 테스트 전략

- `UserServiceTest` 기존 회귀 통과 확인
- `./gradlew.bat :common:build` → `:api:build` → `:scheduler:build` 순 검증

## 제외 사항

- `RefreshToken`(UUID 자동생성) 무변경
- `BaseEntity<ID>` 제네릭 파라미터화 (QClass 재생성 파급 큼)
- legacy 이관 엔티티 선제 도입

## 예상 산출물

- [태스크](../../../tasks/20260421/entity_persistable_harness/TASK1.md)
