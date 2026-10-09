# common 모듈 작업 규칙

## 목적
`common` 모듈은 스마트정수장 백엔드의 공유 도메인 플랫폼 모듈입니다.
전체 도메인 엔티티와 Querydsl QClass 생성의 단일 책임을 가집니다.
`api`, `scheduler`는 `common`의 도메인 모델을 소비하는 실행 모듈이며, 실행 모듈 전용 기술 요소를 `common`으로 이동하지 않습니다.

---

## 허용 범위
- `@Entity`, `@MappedSuperclass`, `@Embeddable`
- 도메인 enum, value object, composite key
- Base Entity, auditing base, domain event base
- 도메인 생성/수정 command 성격의 `XxxUpsertDto`
- 공통 DTO, 공통 예외, 공통 상수, 공통 유틸리티
- Querydsl QClass 생성 설정과 generated source 관리

---

## 금지 범위
- `Controller`, `Service`, `Scheduler`, `Job`, `Step`
- `JpaRepository`, `CustomRepositoryImpl`, `MyBatis Mapper`, `@Mapper`
- `@RestControllerAdvice`, API 전용 request/response DTO
- 실행 모듈 전용 설정 클래스
- `api`, `scheduler` 모듈 타입에 대한 역의존

---

## 패키지 원칙
- 공통 인프라: `com.mo.swtp.common.*`
- 도메인 엔티티: `com.mo.swtp.{도메인}.domain`
- 도메인 command DTO: `com.mo.swtp.{도메인}.dto`
- 역할이 명확한 경우 역할명을 패키지명으로 부여 (예: `response`, `jwt`)

---

## Querydsl 규칙
- QClass는 `common`에서만 생성한다.
- 생성 경로: `build/generated/sources/annotationProcessor/java/main`
- 생성된 QClass는 직접 수정하지 않는다.
- `api`, `scheduler`에서 `querydsl-apt`를 추가하거나 QClass를 재생성하지 않는다.

---

## Entity 규칙
- `@Getter`만 사용하고 `@Setter`는 사용하지 않는다.
- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 제한한다.
- 전체 필드 생성자는 필요 시 `PRIVATE` 접근 수준을 사용한다.
- PK는 `@GeneratedValue(strategy = GenerationType.UUID)`를 기본으로 사용한다.
- 외부에서 PK를 할당받는 엔티티(`@GeneratedValue` 미사용)는 `Persistable<ID>`를 구현한다. `isNew()` 판정은 `BaseEntity`의 `@Transient newEntity` 플래그에 위임하고, `getId()`만 override한다.
- 공통 메타 정보가 필요하면 `BaseEntity` 상속, 이벤트 기능이 필요하면 `DomainEventEntity` 상속.
- 상태 변경은 setter 대신 의도가 드러나는 메서드로 처리한다. (예: `changeInfo(...)`)
- 엔티티 생성은 정적 팩토리 메서드를 우선 사용한다.
- 엔티티는 웹 계층 DTO를 직접 참조하지 않는다.

> 코드 패턴 예시: [../.claude/rules/entity-patterns.md](../.claude/rules/entity-patterns.md)

---

## 검증 원칙
엔티티 변경 시 다음 순서로 검증한다:

```bash
./gradlew.bat :common:build
./gradlew.bat :api:build
./gradlew.bat :scheduler:build
```
