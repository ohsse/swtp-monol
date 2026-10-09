---
status: completed
created: 2026-04-21
updated: 2026-04-21
---
# 외부할당 PK 엔티티 `Persistable` 정규화 및 harness 규칙 보강

## 관련 계획
- [계획안](../../../plan/20260421/entity_persistable_harness/PLAN1.md)

## Phase

### Phase 1: BaseEntity Persistable 기반 추가
- [x] `common/src/main/java/com/mo/smartwtp/common/domain/BaseEntity.java` — `@Transient private boolean newEntity = true` 필드 추가
- [x] `common/src/main/java/com/mo/smartwtp/common/domain/BaseEntity.java` — `public boolean isNew()` 메서드 추가
- [x] `common/src/main/java/com/mo/smartwtp/common/domain/BaseEntity.java` — `@PrePersist onCreate()`에 `this.newEntity = false` 추가
- [x] `common/src/main/java/com/mo/smartwtp/common/domain/BaseEntity.java` — `@PostLoad onLoad()` 메서드 추가

### Phase 2: User 엔티티 Persistable 구현
- [x] `common/src/main/java/com/mo/smartwtp/user/domain/User.java` — `implements Persistable<String>` 선언 추가
- [x] `common/src/main/java/com/mo/smartwtp/user/domain/User.java` — `getId()` 메서드 override 추가

### Phase 3: harness 규칙 문서 보강
- [x] `.claude/rules/entity-patterns.md` — `## 외부 할당 PK 엔티티 패턴` 섹션 신설 및 규칙 요약에 1줄 추가
- [x] `common/CLAUDE.md` — Entity 규칙에 Persistable 가이드 1줄 추가

### Phase 4: 테스트 보강
- [x] `api/src/test/java/com/mo/smartwtp/user/service/UserServiceTest.java` — `신규_사용자_등록_후_감사_필드가_설정된다` 테스트 추가

### Phase 5: 빌드 검증
- [x] `./gradlew.bat :common:build` 성공 확인
- [x] `./gradlew.bat :api:build` 성공 확인
- [x] `./gradlew.bat :scheduler:build` 성공 확인

## 산출물
- [결과](../../../results/20260421/entity_persistable_harness/RESULT1.md)
