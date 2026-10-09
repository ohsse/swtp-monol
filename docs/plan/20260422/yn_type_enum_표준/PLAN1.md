---
status: approved
created: 2026-04-22
updated: 2026-04-22
---
# 여부(Y/N) 처리 표준 — YnType enum 도입

## 목적
신규 엔티티·DTO에 추가될 `_yn` 컬럼 / `*Yn` 필드에 대한 단일 표준을 확정한다.
타입 안전성·Swagger 자동 enum 노출·`UserRole` 기존 enum 패턴 일관성을 얻기 위해
**`YnType` enum + `@Enumerated(EnumType.STRING)`** 방식으로 통일하고,
현재 유일한 사용처인 `User` 엔티티를 새 표준으로 마이그레이션한다.

## 배경
- 현재 여부 필드를 보유한 엔티티는 `User.useYn` 1개뿐이며 `String` + `length = 1`로 선언돼 있다
  (`common/src/main/java/com/mo/swtp/user/domain/User.java:53-54`). DB에는 `"Y"`/`"N"` 문자로 저장된다.
- `UserDto.useYn`은 `@Schema(description = "사용 여부", example = "Y")` 한 줄만 붙어 있고 `@Pattern`·
  `allowableValues` 같은 검증은 없어, `"Yes"`·`"y"` 같은 오입력을 런타임까지 감지하지 못한다.
- 같은 프로젝트의 `UserRole`은 이미 `@Enumerated(EnumType.STRING)` 패턴을 사용 중이라, 동일 규약을
  여부 필드에도 적용하면 학습 비용이 0이다.
- `.claude/rules/naming.md`는 DB 컬럼 `_yn` suffix를 명시했다. 신규 도메인에도
  여부 필드가 증가할 전망이다.
- 사용자는 "`@Schema(description)` 분기만 잘 해도 되지 않을까"를 먼저 제안했으나, 선택지 비교(토의 결과
  `.claude/plans` 내 계획 파일 참조) 후 enum 표준화를 채택하기로 확정했다.

## 범위

### 포함
- `common` 모듈에 `YnType` enum 신설 (`com.mo.swtp.common.enumtype`)
- `User` 엔티티·`UserDto`·`UserRepository`·`UserService`·`AuthService` 및 관련 단위 테스트의
  `String useYn` / `"Y"` 리터럴을 `YnType`·`YnType.Y`로 교체
- 규칙 문서(`.claude/rules/entity-patterns.md`, `.claude/rules/api-patterns.md`, `.claude/rules/naming.md`)
  에 여부 필드 표준 섹션 추가

### 제외
- 다른 엔티티(현재 0개)에 대한 여부 필드 소급 적용 — 신규 엔티티 도입 시 자연스럽게 새 표준을 따르도록 규칙만 고정
- DB 스키마 변경 — 컬럼 타입·길이·값 모두 기존 그대로 유지하므로 DDL 마이그레이션 없음
- AttributeConverter·Boolean 변환 — enum name(`Y`/`N`)이 DB 값과 일치하여 불필요

## 구현 방향

### 1. YnType enum 설계
- 위치: `common/src/main/java/com/mo/swtp/common/enumtype/YnType.java`
- 상수: `Y`, `N`
- 편의 메서드:
  - `boolean isYes()` — 활성 여부 단언
  - `static YnType of(boolean flag)` — Boolean → YnType 변환 유틸
- Javadoc에 `_yn` 컬럼 매핑 원칙과 `@Enumerated(EnumType.STRING)` 기본 매핑 사용을 명시

### 2. 엔티티 적용 패턴
```java
@Enumerated(EnumType.STRING)
@Column(name = "use_yn", nullable = false, length = 1)
private YnType useYn;
```
- `User.create()`의 `"Y"` 리터럴을 `YnType.Y`로, `User.deactivate()`의 `"N"` 리터럴을 `YnType.N`으로 교체
- `AccessLevel.PRIVATE` 전체 필드 생성자의 파라미터 타입도 `YnType`으로 갱신

### 3. DTO 적용 패턴
```java
@Schema(description = "사용 여부")
private YnType useYn;
```
- Swagger OpenAPI가 enum으로 자동 문서화하므로 `allowableValues`·`example`·`@Pattern`은 작성하지 않는다
- `@Schema.description`은 **필드 의미**만 기술하고 값 나열(Y/N)은 생략 (중복 방지)

### 4. Repository / Service 호출부 교체
- `UserRepository.findByUserIdAndUseYn(String userId, String useYn)` → `(String userId, YnType useYn)` 로 시그니처 변경
- `UserService`·`AuthService`의 `"Y"` 리터럴 인자를 `YnType.Y`로 교체
- `UserServiceTest`·`AuthServiceTest`의 stub·assertion도 동일하게 교체

### 5. 규칙 문서 보강
- `.claude/rules/entity-patterns.md` — "여부 필드는 `YnType` enum + `@Enumerated(EnumType.STRING)`" 섹션 추가,
  샘플 코드는 `User.useYn` 구현 결과를 인용
- `.claude/rules/api-patterns.md` — "DTO 여부 필드는 `YnType` 타입만 사용하고 `allowableValues`·`@Pattern`
  중복 작성을 금지" 규칙 추가
- `.claude/rules/naming.md` — DB 컬럼 `_yn` suffix 표에 "Java 필드: `YnType`" 주석 추가

### 6. 참조 구현
- `common/src/main/java/com/mo/swtp/user/domain/UserRole.java` — 동일한 `@Enumerated(EnumType.STRING)` 패턴
- 모든 변경은 기존 저장 데이터와 호환되므로 DB 마이그레이션 없이 런타임 교체만으로 완료된다

## 도메인 모델

신규 엔티티·테이블은 없다. 공통 enum 타입 1개를 추가한다.

| 구분 | 이름 | 역할 | 주요 상수 |
|------|------|------|----------|
| 공통 enum | `YnType` (`com.mo.swtp.common.enumtype`) | `_yn` 컬럼 / 여부 필드 표준 타입 | `Y`(활성), `N`(비활성) |

- 여부(Y/N) 도메인 어휘를 Java 타입으로 형식화한 것
- 엔티티·테이블·관계 변경은 없고, `User.useYn` 필드의 Java 타입만 `String` → `YnType`으로 교체됨
- 향후 신규 엔티티(`pump_m`, `alarm_rule_p`, `tag_m` 등)에서 여부 필드는 모두 `YnType`으로 선언

## DB 설계 변경

변경 없음.

- `user_m.use_yn`은 `VARCHAR(1) NOT NULL` 그대로 유지
- `@Enumerated(EnumType.STRING)` 기본 매핑이 enum name(`Y`/`N`)을 컬럼에 저장하므로 기존 값과 완전 호환
- DDL·인덱스·파티션·보존 기간 정책에 영향 없음
- 다른 지자체 프로파일(`resources-env/*`)에도 영향 없음

## 테스트 전략

### 빌드·단위 테스트
1. `./gradlew.bat :common:build`
   - `YnType.java` 컴파일 성공, `QUser.useYn` 이 `EnumPath<YnType>` 타입으로 재생성되는지 확인
2. `./gradlew.bat :api:test`
   - `UserServiceTest`: 사용자 등록·조회·논리 삭제 시 `YnType.Y`/`YnType.N` 전이 검증
   - `AuthServiceTest`: `findByUserIdAndUseYn(..., YnType.Y)` stub 기반 로그인 성공·실패·비활성 계정 분기 검증

### 회귀 검증
3. `./gradlew.bat :api:bootRun` 기동 후 Swagger UI(`/swagger-ui/index.html`)에서
   `UserDto.useYn`이 OpenAPI 스펙상 `type: string, enum: [Y, N]`로 노출되는지 확인
4. 기존 DB 데이터(`use_yn = 'Y'` 행)에 대해 `findByUserIdAndUseYn`이 `Optional.of`를 반환하는지 수동 확인

### 테스트 도메인 시나리오
- **활성 사용자 로그인**: `useYn = YnType.Y` 계정은 정상 인증 통과
- **비활성 사용자 로그인**: `useYn = YnType.N` 계정은 `findByUserIdAndUseYn(..., Y)` 에서 `Optional.empty`로 거부
- **오입력 방어**: `YnType.valueOf("Yes")` 호출 시 `IllegalArgumentException` — 컴파일 타임에 차단되므로 별도 테스트 작성은 생략

## 제외 사항
- 다른 도메인 엔티티 여부 필드 소급 적용 (현재 대상 없음)
- `AttributeConverter<Boolean, String>` 기반 Boolean 매핑 전략 (불채택)
- `@Schema.allowableValues`·`@Pattern`·`example` 수동 문서화 (OpenAPI 자동 enum 노출로 대체)
- DB 스키마 변경, 파티션·인덱스 조정
- 지자체별 `resources-env` 설정 변경

## 예상 산출물
- [태스크](../../../tasks/20260422/yn_type_enum_표준/TASK1.md)
- [결과](../../../results/20260422/yn_type_enum_표준/RESULT1.md)
- [리뷰](../../../reviews/20260422/yn_type_enum_표준/REVIEW1.md)
