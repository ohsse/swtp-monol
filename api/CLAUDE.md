# api 모듈 작업 규칙

## 목적
`api` 모듈은 스마트정수장 백엔드의 REST 실행 모듈입니다.
`common` 모듈의 도메인, 공통 응답, 공통 예외, JWT 유틸리티를 사용해 HTTP 요청 처리와 인증 진입점을 제공합니다.

---

## 허용 범위
- `@RestController`, `@RequestMapping`, `@RestControllerAdvice`
- `Service`, `JpaRepository`, `CustomRepositoryImpl`, `MyBatis Mapper`
- 인증 필터, 요청 컨텍스트 적재, 인증 예외 응답 작성기
- 실행 모듈 전용 `@Configuration`, `@ConfigurationProperties`, OpenAPI 설정
- `JPAQueryFactory` 주입 기반 Querydsl 조회 구현
- API 요청/응답 DTO, 검색 DTO, 페이징 응답 모델

---

## 금지 범위
- `@Entity`, `@MappedSuperclass`, `@Embeddable` 신규 정의 → `common` 모듈에 작성
- 공통 응답 포맷, 공통 예외, 공통 JWT helper의 `api` 중복 구현
- Querydsl APT 추가 또는 QClass 직접 수정
- 컨트롤러/서비스에서 임의 문자열 에러 코드 직접 정의
- 인증 필터를 우회하는 토큰 직접 파싱 로직 분산 구현
- `api` 전용 설정이나 웹 DTO를 `common`으로 이동

---

## 모듈 경계 원칙
- 도메인 엔티티와 공통 기반 타입은 `common`에 둔다.
- `api`는 `common`의 엔티티를 대상으로 저장소, 조회 구현, 서비스 조합을 담당한다.
- 웹 요청/응답, 인증 진입점, persistence 설정은 `api`에 남긴다.

---

## 패키지 원칙
- 애플리케이션 진입점과 실행 설정: `com.mo.swtp.api.*`
- 도메인 기능: `com.mo.swtp.{도메인}` 구조, 하위 패키지 `web`, `service`, `repository`, `mapper`, `config`
- auth 런타임: `com.mo.swtp.auth.*` (신규 `api.auth.*` 구조 금지)
- MyBatis 매퍼: `@ApiMybatisMapper` 어노테이션으로 스캔 대상 명시

---

## Controller 규칙
- 모든 컨트롤러는 `CommonController`를 상속한다.
- 응답 타입은 `ResponseEntity<CommonResponseDto<T>>`를 기본으로 사용한다.
- 성공 응답은 `getResponseEntity(data)` 또는 `getResponseEntity()`로 래핑한다.
- 컨트롤러는 입력 수집과 서비스 호출에 집중하고, 비즈니스 로직을 직접 담지 않는다.

---

## Service 규칙
- 서비스는 인터페이스 없이 구체 클래스를 기본으로 사용한다.
- 클래스 레벨에 읽기 전용 트랜잭션을 기본으로 선언한다.
- 쓰기 작업 메서드에는 메서드 레벨 `@Transactional`로 오버라이드한다.
- 유효성 검사는 `Validator` 컴포넌트로 분리한다.
- 파일 처리, 외부 연동, 이벤트 발행 같은 부가 책임도 전용 컴포넌트로 분리한다.

> 코드 패턴 예시: [../.claude/rules/api-patterns.md](../.claude/rules/api-patterns.md)

---

## Repository 규칙
- 조회와 영속화 책임을 분리하는 3계층 패턴을 사용한다.
  - `JpaRepository`: 단순 CRUD
  - `CustomRepository`: 복잡 조회 인터페이스
  - `CustomRepositoryImpl`: Querydsl / MyBatis 구현
- 조회 결과가 화면/응답 중심이면 DTO 프로젝션을 우선 사용한다.
- 동일 쿼리를 Querydsl과 MyBatis로 이중 구현하지 않는다.

> 코드 패턴 예시: [../.claude/rules/api-patterns.md](../.claude/rules/api-patterns.md)

---

## DTO 규칙
- DTO는 역할에 따라 명확히 분리한다.
  - `XxxDto`: 조회/응답
  - `XxxUpsertDto`: 생성/수정 요청
  - `XxxSearchDto`: 검색 조건
- 요청 DTO와 응답 DTO를 한 클래스로 혼용하지 않는다.
- Swagger 문서화를 위해 `@Schema`를 적극 작성한다.
- 공통 검색 조건은 부모 클래스로, 특화 조건은 자식 클래스로 상속 구조를 사용한다.

---

## 예외 처리 원칙
- 비즈니스 예외는 반드시 `RestApiException`으로 던지고 `ErrorCode` 구현 enum으로 관리한다.
- 공통 예외 매핑은 `RestApiAdvice`에서 일원화하고, 컨트롤러별 중복 예외 처리를 만들지 않는다.
- 에러 응답 바디는 `CommonResponseDto<Void>`를 사용한다.
- `ErrorCode` 구현 enum은 `httpStatus`(int) 외 필드를 두지 않는다. `message` 등 사용자 표기 문자열 필드는 금지다. 사용자 표기 문구는 프론트엔드가 `errorCode.name()`으로 명세 기반 매핑한다.

> 코드 패턴 예시: [../.claude/rules/exception-patterns.md](../.claude/rules/exception-patterns.md)

---

## 인증 원칙
- JWT access token 검증 진입점은 `JwtAuthenticationFilter` 하나로 관리한다.
- 인증 제외 경로는 코드 하드코딩 대신 `auth.jwt.exclude-paths` 설정으로 관리한다.
- 필터는 검증 성공 시 request attribute에 subject와 claims를 적재한다.
- 토큰 발급/재발급/폐기/유효성 검사는 `JwtTokenManagementService`를 통해 수행한다.
- 필터 예외 응답은 `RestApiAdvice`가 아니라 전용 응답 작성기에서 직렬화한다.

---

## 영속성 원칙
- JPA 저장소는 `JpaRepository` 기반 인터페이스로 정의하고, 엔티티 타입은 `common`에서 가져온다.
- Querydsl은 `ApiQuerydslConfig`가 제공하는 `JPAQueryFactory`를 주입받아 사용한다.
- MyBatis 매퍼는 `@ApiMybatisMapper` 어노테이션을 사용한다.

---

## Swagger/OpenAPI 규칙
- 모든 API에 `@Tag`, `@Operation`, `@ApiResponses`를 작성한다.
- 성공/실패 응답 코드는 누락 없이 문서화한다. (기본: `200`, `400`, `401`, `403`, `404`, `500`)
- 공개 API를 추가하거나 변경하면 Swagger 문서와 예외 코드 설명을 함께 갱신한다.

> 코드 패턴 예시: [../.claude/rules/api-patterns.md](../.claude/rules/api-patterns.md)

---

## 설정 원칙
- 실행 모듈 설정은 `application.yml`과 `@ConfigurationProperties`로 외부화한다.
- JWT secret, DB 접속 정보 같은 민감 설정은 환경 변수 주입을 우선한다.
- OpenAPI 메타데이터와 Swagger 노출 설정은 `api` 모듈에서 관리한다.

---

## 테스트
```bash
./gradlew.bat :api:test   # api 모듈 테스트
./gradlew.bat test        # 공통 타입/엔티티 의존 변경 포함 시 전체 테스트
```

- 응답/예외 정책 변경 시 `RestApiAdviceTest` 기준의 성공/실패 흐름을 검증한다.
- 인증 변경 시 `JwtAuthenticationFilterTest`, `JwtTokenManagementServiceTest`를 검증한다.
- 검증은 별도의 Reviewer를 spawn하여 진행한다.
