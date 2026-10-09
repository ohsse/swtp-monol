---
status: approved
created: 2026-04-21
updated: 2026-04-21
---

# BaseEntity JPA Auditing 도입

## 목적

`BaseEntity` 에 `@EntityListeners(AuditingEntityListener.class)` 를 적용하여 등록자·수정자(`rgstr_id`/`updt_id`) 및 등록·수정 일시(`rgstr_dtm`/`updt_dtm`) 를 JPA Auditing 으로 자동 관리한다. 현재 서비스·팩토리가 auditor 값을 명시적으로 전달하는 구조를 제거하고, JWT 필터가 request attribute 에 세팅한 subject(= `userId`) 를 auditor 소스로 활용한다.

## 배경

현재 `BaseEntity` 는 `@PrePersist`/`@PreUpdate` 로 날짜만 수동 관리하며, 등록자·수정자 컬럼은 `User`, `RefreshToken` 이 각자 보유하고 호출부가 `registrarId`/`updaterId` 파라미터를 명시적으로 전달한다. reference 프로젝트(`backend/reference/.../AuditingComp.java`)의 JWT subject 활용 방향은 채택하되, 헤더 재파싱·@PrePersist 날짜 중복 세팅 등 안티패턴은 제거한다.

### reference 설계 타당성 평가 요약

| 항목 | 판정 |
|------|------|
| JWT subject(=userId)를 auditor로 사용 | ✅ 채택 |
| `Authorization` 헤더 재파싱 (서명 검증 없이 decode) | ❌ 금지 → request attribute 사용 |
| `@CreatedBy`/`@LastModifiedBy` 동일 컬럼 이중 적용 | ❌ → 별개 컬럼 적용 |
| `@PrePersist` 날짜 수동 세팅과 `@CreatedDate` 중복 | ❌ → 수동 세팅 제거 |
| `IllegalStateException` silent catch | ❌ → 명시적 instanceof 판정으로 교체 |

## 범위

| 모듈 | 변경 내용 |
|------|----------|
| `common` | `BaseEntity` Auditing 전환, `User`/`RefreshToken` 중복 컬럼·파라미터 제거 |
| `api` | `JpaAuditingConfig`, `ApiAuditorAware` 신규, 서비스·테스트 시그니처 갱신 |
| `scheduler` | `JpaAuditingConfig`, `SchedulerAuditorAware` 신규 (`"SYSTEM"` 고정) |

## 구현 방향

### 결정된 설계 방침

| 항목 | 결정 |
|------|------|
| 비인증 상황 auditor fallback (API) | `"SYSTEM"` 고정값 |
| 명시적 registrarId/updaterId 파라미터 | 전부 제거, Auditing에 위임 |
| scheduler 모듈 포함 여부 | 이번 범위 포함 (`"SYSTEM"` 고정) |

### 1. `common` — BaseEntity 개편

```java
@MappedSuperclass
@Getter
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "rgstr_dtm", nullable = false, updatable = false)
    private LocalDateTime rgstrDtm;

    @LastModifiedDate
    @Column(name = "updt_dtm", nullable = false)
    private LocalDateTime updtDtm;

    @CreatedBy
    @Column(name = "rgstr_id", nullable = false, updatable = false, length = 50)
    private String rgstrId;

    @LastModifiedBy
    @Column(name = "updt_id", nullable = false, length = 50)
    private String updtId;

    @Transient
    private boolean newEntity = true;

    public boolean isNew() { return newEntity; }

    @PrePersist
    protected void onPrePersistMarkLoaded() { this.newEntity = false; }

    @PostLoad
    protected void onPostLoadMarkLoaded() { this.newEntity = false; }
}
```

- `@PreUpdate` 제거 (날짜는 `@LastModifiedDate` 가 처리)
- `newEntity` 플래그 전환 훅은 유지 (Persistable 계약 보호)

### 2. `common` — 엔티티 중복 컬럼 제거

**User**: `rgstrId`/`updtId` 필드 제거, `create()/changePw()/changeInfo()/deactivate()` 시그니처에서 auditor 파라미터 제거  
**RefreshToken**: 동일하게 `rgstr_id`/`updt_id` 필드·`AUDIT_ACTOR` 하드코딩 제거

### 3. `api` — Auditing 설정 + AuditorAware

```java
// JpaAuditingConfig.java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "apiAuditorAware")
public class JpaAuditingConfig {}

// ApiAuditorAware.java
@Component("apiAuditorAware")
public class ApiAuditorAware implements AuditorAware<String> {
    private static final String SYSTEM_AUDITOR = "SYSTEM";

    @Override
    public Optional<String> getCurrentAuditor() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return Optional.of(SYSTEM_AUDITOR);
        }
        Object subject = sra.getRequest().getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE);
        if (subject instanceof String s && !s.isBlank()) {
            return Optional.of(s);
        }
        return Optional.of(SYSTEM_AUDITOR);
    }
}
```

- `Authorization` 헤더 재파싱 금지. `JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE` request attribute 만 사용.
- `UserService`, `AuthService`, `JwtTokenManagementService` 에서 auditor 파라미터 추출·전달 코드 제거

### 4. `scheduler` — 별도 AuditorAware

```java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "schedulerAuditorAware")
public class JpaAuditingConfig {}

@Component("schedulerAuditorAware")
public class SchedulerAuditorAware implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() { return Optional.of("SYSTEM"); }
}
```

### 5. DDL / 마이그레이션

- `user_m`, `refresh_token_m` 의 `rgstr_id`/`updt_id` 컬럼 이미 존재 → **스키마 변경 불필요**
- `BaseEntity` 에서 `nullable=false` 선언 → 운영 DB NULL 행 점검 필요:
  ```sql
  SELECT COUNT(*) FROM user_m WHERE rgstr_id IS NULL OR updt_id IS NULL;
  SELECT COUNT(*) FROM refresh_token_m WHERE rgstr_id IS NULL OR updt_id IS NULL;
  ```
  NULL 존재 시 `UPDATE ... SET rgstr_id='SYSTEM', updt_id='SYSTEM' WHERE ... IS NULL` 백필 후 진행 (`db-patterns.md §3` 무중단 변경 원칙 준수)

## 테스트 전략

- `UserServiceTest` — 신규 시그니처 반영, `rgstr_id` 검증은 AuditorAware 주입 또는 Mock 으로 교체
- `RefreshToken` 관련 토큰 테스트 — 팩토리 시그니처 변경 반영
- (신규) `ApiAuditorAwareTest` — request attribute 있음/없음/컨텍스트 부재 3케이스
- (신규) `BaseEntityAuditingTest` (`@DataJpaTest` + `@Import(JpaAuditingConfig.class)`) — persist 시 4개 감사 컬럼 자동 주입 검증

## 제외 사항

- `BaseEntity<ID>` 제네릭화 (QClass 파급 이유로 제외)
- SecurityContextHolder 도입 (현재 request attribute 기반 인가 유지)
- 레거시 reference 프로젝트 코드 수정

## 예상 산출물

- [태스크](../../../tasks/20260421/base_entity_auditing/TASK1.md)
