# 테스트 전략

스마트정수장 백엔드의 테스트 계층 원칙과 도메인 시나리오 기반 테스트 가이드. 현 스택(Spring Boot 4.0.5 / Java 21 / JUnit 5 / Mockito / AssertJ) 실사용 패턴을 1급으로 기술한다.

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| `api/src/test/java/` | 현재 단위·통합 테스트 스타일 원천 (§1 · §2) |
| [`db/partitioning-and-retention.md §1`](db/partitioning-and-retention.md) | 시계열 파티션 규칙 — 통합·E2E 격상 시 픽스처 구성에 필요 ([`test-strategy-e2e-roadmap.md §5`](test-strategy-e2e-roadmap.md)) |
| [`ot-integration.md §5`](ot-integration.md) | 알람 4단계·인터록·운전 모드 도메인 규칙 — 시나리오 테스트의 근거 (§3) |
| [`test-strategy-e2e-roadmap.md`](test-strategy-e2e-roadmap.md) | Testcontainers · E2E · 슬라이스 테스트 도입 로드맵 (현 시점 미도입, 도입 시 ANALYZE 필요) |

---

## 테스트 계층 원칙

```
단위 테스트 (Unit)          ← 현재 표준, 빠름, Mockito 격리 (§1)
통합 테스트 (Integration)   ← 제한적 사용, @SpringBootTest(NONE) + @Transactional (§2)
E2E · Testcontainers 격상   ← 현 시점 미도입. 도입 시 별도 ANALYZE 필수 (test-strategy-e2e-roadmap.md)
```

> **현 스택 제약**: 프로젝트에는 `org.testcontainers:*` 의존성이 선언되어 있지 않다. 슬라이스 어노테이션(`@DataJpaTest`·`@WebMvcTest`·`@AutoConfigureTestDatabase`) 은 Spring Boot 4 에서도 제공되나 현재 프로젝트에서는 사용 0건이다. 파티션·BRIN·컨트롤러 슬라이스 테스트가 필요해질 경우 [`test-strategy-e2e-roadmap.md`](test-strategy-e2e-roadmap.md) 로드맵에 따라 별도 ANALYZE 를 거쳐 도입한다.

---

## 1. 단위 테스트

현 프로젝트의 표준 스타일. 외부 의존성은 Mockito 로 격리한다. 16개 테스트 중 15건이 이 방식으로 작성되어 있다.

### 기본 구성

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenManagementService jwtTokenManagementService;

    @InjectMocks
    private AuthService authService;

    @Test
    void 존재하지_않는_사용자_로그인_시_LOGIN_FAILED_예외가_발생한다() {
        given(userRepository.findByUserIdAndUseYn("unknown", YnType.Y))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("unknown", "anyPw"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }
}
```

### 적용 대상

| 대상 | 핵심 검증 항목 |
|------|--------------|
| Service 로직 | 비즈니스 규칙, 예외 발생 조건, 상태 전이 |
| 도메인 엔티티 | `create()`·`changeInfo()` 정적 팩토리 및 변경 메서드 |
| 유효성 검사 컴포넌트 | Validator, 인터록 검사 로직 |
| 값 계산 로직 | 알람 임계값 비교, 절감량 계산, 운전 모드 판정 |
| 이벤트 발행기 | `ApplicationEventPublisher.publishEvent(...)` 호출 `verify()` |

### 의존성 (현 표준 — 루트 `build.gradle`)

```groovy
testImplementation platform('org.junit:junit-bom:5.13.4')
testImplementation 'org.junit.jupiter:junit-jupiter'
testImplementation 'org.mockito:mockito-core:5.20.0'
testImplementation 'org.assertj:assertj-core:3.27.4'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

`common` 모듈은 `mockito-junit-jupiter:5.20.0` 추가. `api` · `scheduler` 모듈은 `spring-boot-starter-test` 추가.

### 작성 원칙

- `@ExtendWith(MockitoExtension.class)` 기본. `@Mock` / `@InjectMocks` 조합으로 의존성 격리
- `given(...)` · `then(...).should()` · `verify(...)` (BDDMockito) 문체 사용
- 검증은 `assertThat(...)` · `assertThatThrownBy(...)` (AssertJ) 사용
- `RestApiException` 검증 시 `errorCode` 까지 확인 (`.extracting(e -> ((RestApiException) e).getErrorCode())`)
- `@DisplayName` 대신 한국어 메서드명(`_`) 사용 — 현 프로젝트 관행 유지

### 버그 수정 작업의 첫 체크박스 의무

ROOT [`coding-discipline.md` §4.3](../../../.claude/rules/coding-discipline.md) 적용. 커밋 타입 `fix:` 작업의 TASK 첫 번째 체크박스는 다음과 같이 작성한다:

```markdown
- [ ] {버그 위치 백틱 경로} 버그 재현 테스트 작성 → 검증: 신규 테스트 RED 확인 (실패 메시지에 버그 증상 포함)
```

- TDD 강제는 아님 — **재현 가능성** 만 보장
- 두 번째 체크박스부터 실제 수정 작업 진행 (수정 후 같은 테스트가 GREEN 으로 전환)
- REVIEW 단계에서 `wtp-backend-engineer` 가 `fix:` 타입 작업의 첫 체크박스에 재현 테스트가 누락되면 **블로커** 로 지적

---

## 2. 통합 테스트

Spring 컨텍스트 + 실제 PostgreSQL 로 서비스·JPA·이벤트 경로를 검증한다. `api` 모듈의 `UserServiceTest` 가 현재 유일한 사례이자 표준 패턴.

### 기본 구성

```java
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    /** 서비스 테스트와 무관한 웹 레이어 빈이므로 mock 으로 대체한다. */
    @MockitoBean
    private ApiErrorResponseWriter apiErrorResponseWriter;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 중복_사용자ID_등록_시_예외가_발생한다() {
        userService.registerUser(buildDto("testuser", "테스트유저", "pw", UserRole.USER));

        assertThatThrownBy(() -> userService.registerUser(
                buildDto("testuser", "유저2", "pw2", UserRole.USER)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(UserErrorCode.DUPLICATE_USER_ID);
    }
}
```

### 현 스택에서의 규칙

| 요소 | 규칙 |
|------|------|
| 웹 환경 | `@SpringBootTest(webEnvironment = WebEnvironment.NONE)` 기본. MVC·필터 레이어를 기동하지 않는다 |
| 프로파일 | `@ActiveProfiles("test")` — `application-test.yml` 이 로컬 PostgreSQL 로 접속. `-Pprofile=gs` 등 멀티테넌트 빌드 프로파일(`multi-tenant.md`)과는 별개 |
| 트랜잭션 | 클래스 레벨 `@Transactional` → 각 테스트 종료 후 자동 롤백 |
| 롤백 불가 케이스 | 이벤트 `BEFORE_COMMIT` 리스너 검증 등은 메서드 레벨 `@Transactional(propagation = NOT_SUPPORTED)` 로 오버라이드하고 전후 정리 쿼리 수동 실행 |
| 영속 확인 | DB 커밋이 필요한 경우 `@Commit` 사용 + 테스트 전 기존 레코드 정리 코드 포함 |
| Bean 오버라이드 | `@MockitoBean` (패키지: `org.springframework.test.context.bean.override.mockito.MockitoBean`). **`@MockBean` 은 Spring Boot 4 에서 제거됨** |
| 웹 레이어 mock | 서비스 테스트와 무관한 웹 빈(`ApiErrorResponseWriter` 등)은 `@MockitoBean` 으로 대체 |

### 테스트 DB 전제조건

현 프로젝트의 `application-test.yml` 은 **로컬 PostgreSQL 에 직접 연결**된다(`jdbc:postgresql://localhost:5432/smartwtp`). 따라서 테스트 실행 전 다음이 선행되어야 한다:

- 로컬 PostgreSQL 기동 + `smartwtp` DB 생성
- JPA 스키마가 적용된 상태 (또는 마이그레이션 완료)
- 시계열 파티션이 필요한 테스트 시 해당 월 파티션 선행 생성 (`db/partitioning-and-retention.md §1`)

> **⚠️ Testcontainers 미도입 상태**. PostgreSQL 전용 DDL(파티션·BRIN) 이 필요한 Repository 테스트가 누적되면 [`test-strategy-e2e-roadmap.md`](test-strategy-e2e-roadmap.md) 로드맵에 따라 Testcontainers 도입을 검토한다 (§2 도입 트리거 조건).

---

## 3. 도메인 시나리오 테스트

정수장 핵심 비즈니스 규칙(`ot-integration.md §5` 기반) 을 시나리오 단위로 검증한다. 현 스택에서는 **Mockito 기반 단위 테스트** 로 작성하며, DB 쓰기는 Repository 호출 `verify()` 로 검증한다. 실제 DB · 파티션 의존은 금지한다.

### 3-1. 알람 4단계 전이

알람 4단계 `0(정상) → 1(주의) → 2(경보) → 3(위험/TRIP)` 전이를 검증.

```java
@ExtendWith(MockitoExtension.class)
class AlarmEscalationScenarioTest {

    @Mock AlarmHistoryRepository alarmHistoryRepository;
    @InjectMocks AlarmEvaluator alarmEvaluator;

    @Test
    void 진동값이_주의_임계값을_초과하면_알람_1단계가_발생한다() {
        AlarmRule rule = AlarmRuleFixture.create(5.0, 8.0, 12.0);

        AlarmResult result = alarmEvaluator.evaluate(rule, 6.5);

        assertThat(result.getSeverityCd()).isEqualTo(1);
        then(alarmHistoryRepository).should().save(any(AlarmHistory.class));
    }
}
```

### 3-2. 인터록 선행조건 미충족 시 기동 차단

```java
@Test
void 흡입압력이_최저값_미만이면_기동_명령이_차단된다() {
    // given: 인터록 규칙 — 흡입압력 >= 0.3m 필요
    // when: 현재 흡입압력 = 0.1m 상태에서 기동 명령
    // then: RestApiException(INTERLOCK_VIOLATION)
}

@Test
void 모든_선행조건을_만족하면_기동_명령이_허용된다() { ... }

@Test
void CircuitBreaker_CLOSED_복구_후_인터록_재검사가_실행된다() { ... }
```

### 3-3. 운전 모드 전환

운전 모드 `AI_MODE=0(수동)`, `AI_MODE=1(AI자동)`, `AI_MODE=2(반자동)` 전환 검증.

```java
@Test
void AI자동_모드에서는_수동_주파수_입력이_거부된다() {
    // given: AI_MODE = 1 (AI 자동)
    // when: 운전원이 직접 주파수 입력
    // then: RestApiException(MANUAL_INPUT_DENIED_IN_AUTO_MODE)
}

@Test
void SCADA_5분_초과_중단_시_AI자동_모드가_반자동으로_강제_전환된다() { ... }
```

### 구현 범위

- **구현 방식**: 모두 `@ExtendWith(MockitoExtension.class)` 기반 단위 테스트
- **DB 쓰기 검증**: Repository `save(...)` / `update(...)` 호출을 `then(...).should()` · `verify(...)` 로 확인
- **통합 격상 조건**: 실 DB 파티션 · 이벤트 전파가 필요해지면 [`test-strategy-e2e-roadmap.md`](test-strategy-e2e-roadmap.md) 로드맵에 따라 별도 ANALYZE 후 격상

---

## 4. 테스트 디렉토리·네이밍

### 디렉토리 구조 (현 유지)

```
{모듈}/src/test/java/
└── com/mo/swtp/
    └── {도메인}/
        └── {대상클래스}Test.java
```

### 테스트 클래스 네이밍

| 분류 | 패턴 | 예시 |
|------|------|------|
| 단위 테스트 | `{대상클래스}Test` | `AuthServiceTest`, `JwtTokenHelperTest` |
| 통합 테스트 | `{대상클래스}Test` (파일명 동일) | `UserServiceTest` |
| 도메인 시나리오 | `{도메인}{시나리오}ScenarioTest` | `AlarmEscalationScenarioTest` |
| E2E (미도입) | `{기능}E2ETest` | [`test-strategy-e2e-roadmap.md`](test-strategy-e2e-roadmap.md) 로드맵 참조 |

> 단위 / 통합을 파일명으로 구분하지 않는다. **클래스 어노테이션**(`@ExtendWith(MockitoExtension.class)` vs `@SpringBootTest`) 으로 구분한다.

---

## 5. 커버리지 최소 기준

테스트의 "충분함" 을 객관화하기 위한 최소 기준. 수치는 **권고** 이며 CI 강제(violationRules) 로 두지 않는다. 단, §5.2 의 도메인 시나리오 필수 3종과 각 클래스의 최소 필수 케이스는 **의무** 다.

### 5.1 계층별 권고 기준

| 계층 | 최소 시나리오 수 | 라인 커버리지 (권고) | Mock 허용 |
|------|---------------|------------------|---------|
| 단위 (Service · Entity · Validator) | 도메인당 3개 이상 | 50% → 70% (로드맵) | 허용 |
| 통합 (`@SpringBootTest(NONE)` 기반) | 주요 서비스 경로당 1개 | — | 웹 레이어 `@MockitoBean` 에 한정 |
| E2E · Repository 슬라이스 | [`test-strategy-e2e-roadmap.md`](test-strategy-e2e-roadmap.md) 로드맵 | — | 로드맵 §4 참조 |

- 라인 커버리지 수치는 JaCoCo 기준의 권고이며, CI 실패 조건으로 쓰지 않는다. JaCoCo 도입 자체도 선택 사항.
- 현재 `api` · `common` 모듈 main/test 클래스 비율 ≈ **27.6%** (main 58개, test 16개 기준) — 50% 까지는 도메인 시나리오 보강으로 자연 달성 예상. 급격한 상향 금지.

### 5.2 도메인 시나리오 필수 3종

다음 3개 시나리오 클래스는 도메인 규칙(알람 4단계 · 인터록 · 운전 모드) 과 직결되므로 **반드시 작성** 한다. 각 클래스의 **최소 필수 케이스** 까지 의무다 (누락 시 REVIEW 에서 블로커로 지적). 모두 **Mockito 기반 단위 테스트** 범위에서 작성한다.

#### 5.2.1 `AlarmEscalationScenarioTest` — 알람 4단계 전이 (§3-1 격상)

- [ ] 정상(0) → 주의(1) 전이 — 주의 임계값 초과
- [ ] 주의(1) → 경보(2) 순차 전이 — 경보 임계값 초과 (단계를 건너뛰지 않고 1→2 로만 전이됨을 검증)
- [ ] 경보(2) → 위험/TRIP(3) 전이 — 위험 임계값 초과
- [ ] 위험(3) → 정상(0) 복귀 — 복귀 조건 만족
- [ ] 이상치 기각 시 `AlarmHistoryRepository.save(...)` 호출 검증 (`verify`) — UNCERTAIN 등급 저장 (`ot-integration.md §3`)

#### 5.2.2 `PumpInterlockScenarioTest` — 인터록 선행조건 (§3-2 격상)

- [ ] 흡입압력(PRI) 미충족 시 기동 명령 차단
- [ ] 다중 센서 인터록 — 유량(FRI) · 수위(LEI) 각각의 위반 케이스
- [ ] 모든 선행조건 만족 시 기동 명령 허용
- [ ] **장애 복구 후 재검사 통과** — CircuitBreaker CLOSED 복구 후 인터록 재검사 실행
- [ ] **장애 복구 후 재검사 실패** — 인터록 위반 상태 유지 시 차단 유지 (`ot-integration.md §5` *⚠️ 절대 금지* 규정 직결)

#### 5.2.3 `PumpOperationModeScenarioTest` — 운전 모드 전환 (§3-3 격상)

- [ ] AI 자동 모드에서 수동 주파수 입력 거부
- [ ] 수동 모드 전환 시 진행 중이던 AI 제어 명령 취소
- [ ] 반자동 모드에서 펌프 조합 선택만 허용
- [ ] SCADA 5분 초과 중단 시 AI 자동 → 반자동 **강제 전환** (`ot-integration.md §5`)

### 5.3 권장 추가 시나리오 (필수 아님)

향후 별도 ANALYZE 를 거쳐 필수로 승격 검토 대상:

- `ScadaQualityDegradationScenarioTest` — SCADA 품질 저하 지속 시간(1분 미만 / 1~5분 / 5분 초과) 별 알람 격상 및 강제 모드 전환 통합 흐름
- `InterlockRecoveryScenarioTest` — 아웃바운드 장애 복구 후 인터록 재검사 흐름 (§5.2.2 를 별도 클래스로 분리 시)

### 5.4 라인 커버리지로 보증되지 않는 검증

라인 커버리지는 JVM 바이트코드 실행 여부만 집계하며, 아래 품질 지표는 커버리지 수치로 보증되지 않는다. 별도 수단으로 관리한다.

| 관심 영역 | 검증 수단 |
|----------|---------|
| 쿼리 실행 계획 · 파티션 프루닝 | `EXPLAIN (ANALYZE, BUFFERS)` (`db/query-tuning.md §2`) |
| 슬로우 쿼리 | p6spy 로그 + `executionThreshold` (`db/query-tuning.md §2`) |
| N+1 발생 | `JOIN FETCH` · `@EntityGraph` · `@BatchSize` 적용 여부 점검 |

### 5.5 JaCoCo 도입 예시 (선택 사항)

JaCoCo 는 측정 도구로만 사용하며 `violationRules` 로 CI 실패 조건을 두지 않는다.

```groovy
// build.gradle (모듈별, 선택)
plugins { id 'jacoco' }

jacoco { toolVersion = '0.8.12' }

test { finalizedBy jacocoTestReport }

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}
```

> 수치 기반 CI 강제는 본 룰의 "권고 기준" 방침과 충돌하므로 도입하지 않는다. 커버리지 리포트는 리뷰 참고용.

---

## 6. 향후 도입 로드맵

통합 격상·E2E·Testcontainers·슬라이스 어노테이션 도입 시 참고할 로드맵은 [`test-strategy-e2e-roadmap.md`](test-strategy-e2e-roadmap.md) 단일 문서로 분리되어 있다. 도입 트리거 조건이 충족되면 별도 `/dev:analyze` 단계를 거친 후 해당 로드맵을 따른다.
