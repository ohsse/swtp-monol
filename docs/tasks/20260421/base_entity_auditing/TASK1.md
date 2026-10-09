---
status: completed
created: 2026-04-21
updated: 2026-04-21
---

# BaseEntity JPA Auditing 도입 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260421/base_entity_auditing/PLAN1.md)

## Phase

### Phase 1: common — BaseEntity Auditing 전환

- [x] `common/src/main/java/com/mo/smartwtp/common/domain/BaseEntity.java` 수정
  - `@EntityListeners(AuditingEntityListener.class)` 추가
  - `@CreatedDate` + `updatable = false` 로 `rgstrDtm` 어노테이션 전환
  - `@LastModifiedDate` 로 `updtDtm` 어노테이션 전환
  - `@CreatedBy` + `updatable = false` + `nullable = false` 로 `rgstrId` 신규 필드 추가 (컬럼명 `rgstr_id`)
  - `@LastModifiedBy` + `nullable = false` 로 `updtId` 신규 필드 추가 (컬럼명 `updt_id`)
  - `@PrePersist onCreate()` → 날짜 세팅 제거, `newEntity = false` 만 유지 (메서드명 `onPrePersistMarkLoaded`)
  - `@PostLoad onLoad()` → `newEntity = false` 만 유지 (메서드명 `onPostLoadMarkLoaded`)
  - `@PreUpdate onUpdate()` 메서드 완전 제거

### Phase 2: common — 엔티티 중복 컬럼 제거

- [x] `common/src/main/java/com/mo/smartwtp/user/domain/User.java` 수정
  - `rgstrId` 필드 및 `@Column(rgstr_id)` 제거 (BaseEntity 상속으로 확보)
  - `updtId` 필드 및 `@Column(updt_id)` 제거
  - `@AllArgsConstructor` 파라미터 목록에서 `rgstrId`, `updtId` 제거
  - `create(userId, userNm, encodedPw, role, registrarId)` → `create(userId, userNm, encodedPw, role)` 로 축소
  - `changePw(encodedPw, updaterId)` → `changePw(encodedPw)` 로 축소
  - `changeInfo(userNm, userRole, updaterId)` → `changeInfo(userNm, userRole)` 로 축소
  - `deactivate(updaterId)` → `deactivate()` 로 축소

- [x] `common/src/main/java/com/mo/smartwtp/auth/domain/RefreshToken.java` 수정
  - `rgstrId` 필드 및 `@Column(rgstr_id)` 제거
  - `updtId` 필드 및 `@Column(updt_id)` 제거
  - `@AllArgsConstructor` 파라미터 목록에서 `rgstrId`, `updtId` 제거
  - `create(userId, tokenHash, exprDtm, registrarId)` → `create(userId, tokenHash, exprDtm)` 로 축소
  - `rotate(tokenHash, exprDtm, lastUsedDtm, updaterId)` → `rotate(tokenHash, exprDtm, lastUsedDtm)` 로 축소
  - `revoke(revokeDtm, updaterId)` → `revoke(revokeDtm)` 로 축소

### Phase 3: api — Auditing 설정 + AuditorAware 신규 생성

- [x] `api/src/main/java/com/mo/smartwtp/api/config/persistence/JpaAuditingConfig.java` 생성
  - `@Configuration` + `@EnableJpaAuditing(auditorAwareRef = "apiAuditorAware")`

- [x] `api/src/main/java/com/mo/smartwtp/api/config/persistence/ApiAuditorAware.java` 생성
  - `AuditorAware<String>` 구현, Bean 이름 `"apiAuditorAware"`
  - `RequestContextHolder.getRequestAttributes()` 로 컨텍스트 확인
  - `!(attrs instanceof ServletRequestAttributes)` → `Optional.of("SYSTEM")`
  - `request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE)` 읽기
  - subject null/blank → `Optional.of("SYSTEM")`

### Phase 4: api — 서비스 auditor 파라미터 제거

- [x] `api/src/main/java/com/mo/smartwtp/user/service/UserService.java` 수정
  - `registerUser(dto, registrarId)` → `registerUser(dto)` 로 축소, `User.create` 신규 시그니처 호출
  - `updateUser(userId, dto, updaterId)` → `updateUser(userId, dto)` 로 축소, `user.changeInfo` 신규 시그니처 호출
  - `deactivateUser(userId, updaterId)` → `deactivateUser(userId)` 로 축소, `user.deactivate()` 신규 시그니처 호출

- [x] `api/src/main/java/com/mo/smartwtp/auth/service/JwtTokenManagementService.java` 수정
  - `AUDIT_ACTOR` 상수 제거
  - `RefreshToken.create` 신규 3인자 시그니처 호출 (registrarId 인자 제거)
  - `existing.rotate` 신규 3인자 시그니처 호출 (updaterId 인자 제거)
  - `token.revoke` 신규 1인자 시그니처 호출 (updaterId 인자 제거)

- [x] `api/src/main/java/com/mo/smartwtp/user/web/UserController.java` 확인 및 수정
  - request attribute 에서 `registrarId`/`updaterId` 추출 후 서비스에 전달하는 코드 제거
  - `UserService` 신규 시그니처 (`registrarId` 없는 버전) 으로 호출부 갱신

- [x] `api/src/main/java/com/mo/smartwtp/auth/service/AuthService.java` 확인
  - `JwtTokenManagementService` 호출 시 auditor 전달 코드 없음 — 변경 불필요

### Phase 5: scheduler — Auditing 설정 신규 생성

- [x] `scheduler/src/main/java/com/mo/smartwtp/scheduler/config/persistence/JpaAuditingConfig.java` 생성
  - `@Configuration` + `@EnableJpaAuditing(auditorAwareRef = "schedulerAuditorAware")`

- [x] `scheduler/src/main/java/com/mo/smartwtp/scheduler/config/persistence/SchedulerAuditorAware.java` 생성
  - `AuditorAware<String>` 구현, Bean 이름 `"schedulerAuditorAware"`
  - `getCurrentAuditor()` → `Optional.of("SYSTEM")` 고정 반환

### Phase 6: 테스트 수정 및 신규 작성

- [x] `api/src/test/java/com/mo/smartwtp/user/service/UserServiceTest.java` 수정
  - `registerUser(dto, registrarId)` 호출부를 `registerUser(dto)` 로 변경
  - `deactivateUser(userId, updaterId)` 호출부를 `deactivateUser(userId)` 로 변경
  - `신규_사용자_등록_후_감사_필드가_설정된다` 에 `rgstrId="SYSTEM"`, `updtId="SYSTEM"` 검증 추가

- [x] `api/src/test/java/com/mo/smartwtp/auth/service/JwtTokenManagementServiceTest.java` 확인
  - `AUDIT_ACTOR`, `registrarId`, `updaterId` 직접 사용 없음 — 변경 불필요

- [x] `api/src/test/java/com/mo/smartwtp/auth/service/AuthServiceTest.java` 수정
  - `User.create(..., "system")` → `User.create(...)` 로 변경 (3곳)

- [x] `api/src/test/java/com/mo/smartwtp/api/config/persistence/ApiAuditorAwareTest.java` 신규 생성
  - 케이스 1: `ServletRequestAttributes` 에 subject 있음 → `Optional.of(subject)` 반환
  - 케이스 2: request attribute 에 subject null → `Optional.of("SYSTEM")` 반환
  - 케이스 3: request attribute 에 subject blank → `Optional.of("SYSTEM")` 반환
  - 케이스 4: 요청 컨텍스트 자체 없음 → `Optional.of("SYSTEM")` 반환

### Phase 7: 빌드 검증

- [x] `./gradlew.bat :common:build` 실행 성공 확인 (QClass 재생성 포함)
- [x] `./gradlew.bat :api:build` 실행 성공 확인
- [x] `./gradlew.bat :scheduler:build` 실행 성공 확인
- [x] `./gradlew.bat :api:test` 실행 성공 확인
- [x] `./gradlew.bat build` 전체 빌드 성공 확인

## 산출물
- [결과](../../../results/20260421/base_entity_auditing/RESULT1.md)
