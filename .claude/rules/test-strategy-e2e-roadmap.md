# 테스트 E2E · Testcontainers 도입 로드맵

현 시점 **프로젝트에 도입되어 있지 않은** 통합 격상·E2E·Testcontainers 전략을 향후 도입할 때 참고하는 로드맵이다.
본 문서의 내용은 [`test-strategy.md`](test-strategy.md) 본문(§1~§5) 규칙과 독립적으로 관리되며,
**실제 도입 시 별도 `/dev:analyze` ANALYZE 단계를 반드시 거쳐야 한다**.

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`test-strategy.md`](test-strategy.md) | 현행 단위·통합 테스트 표준 (본 문서는 미도입 격상 로드맵) |
| [`db/partitioning-and-retention.md`](db/partitioning-and-retention.md) | 시계열 파티션 픽스처 구성 전제 (월 RANGE 규칙) |
| [`ot-integration.md`](ot-integration.md) | 알람·인터록 도메인 시나리오 격상 근거 |

> 본 문서가 필요해지는 시점은 §2 도입 트리거 조건 중 하나가 충족될 때다. 트리거 전까지는
> [`test-strategy.md`](test-strategy.md) 의 단위 테스트(§1) · 통합 테스트(§2 — `@SpringBootTest(NONE)` 방식) 만 사용한다.

---

## 1. 현 시점 미도입 기술

| 기술 | 현 상태 | 도입 전제 |
|------|--------|---------|
| `org.testcontainers:*` | **의존성 미선언** | `build.gradle` 에 추가 필요 |
| `@DataJpaTest` · `@WebMvcTest` 슬라이스 | 사용 0건 (의존성은 `spring-boot-starter-test` 에 포함됨) | 현 통합 방식(`@SpringBootTest(NONE)`) 한계에 도달 시 |
| `@AutoConfigureTestDatabase(replace = Replace.NONE)` | 사용 0건 | `@DataJpaTest` + 실제 PostgreSQL 조합 시 |
| `@ServiceConnection` (Boot 3.1+) | 사용 0건 | Testcontainers 도입 시 권장 1순위 |
| E2E (`@SpringBootTest(RANDOM_PORT)`) | 사용 0건 | API 골든 패스 검증 필요 시 |

---

## 2. 도입 트리거

다음 조건 중 하나가 충족되면 도입 ANALYZE 를 소집한다:

- 시계열 테이블(`rawdata_1m_h`, `alarm_h`, `ctrl_log_h` 등) 에 대한 Repository 슬라이스 검증이 **5건 이상** 누적
- 파티션 프루닝 · BRIN 인덱스 실효성 검증이 단위 테스트로 불가
- Controller 슬라이스(`@WebMvcTest`) + SecurityFilter 조합 테스트 필요
- 실서버 환경 골든 패스(API 인증 → 서비스 → DB 저장) E2E 재현 필요
- 로컬 PostgreSQL 환경 의존으로 CI 파이프라인 구성이 제약받기 시작

---

## 3. 필요 의존성 (도입 시 추가)

```groovy
// common 또는 해당 테스트가 속한 모듈의 build.gradle
testImplementation 'org.testcontainers:postgresql:1.20.4'
testImplementation 'org.testcontainers:junit-jupiter:1.20.4'
// Boot 4 슬라이스 어노테이션은 spring-boot-starter-test 에 이미 포함
// (단, 패키지 경로는 Boot 3 → 4 에서 이동됨 — §6 참조)
```

---

## 4. Testcontainers 기본 구성 (Boot 4 권장 — `@ServiceConnection`)

```java
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
class RawDataRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired RawDataRepository rawDataRepository;

    @Test
    void 유량_범위로_원시데이터를_조회한다() { ... }
}
```

> Spring Boot 3.1+ 에서 도입된 `@ServiceConnection` 이 Boot 4 에서도 권장 1순위. 커스텀 프로퍼티가 필요하면 `@DynamicPropertySource` 를 보조로 사용한다.

---

## 5. 시계열 픽스처 전략 (도입 시)

[`db/partitioning-and-retention.md §1`](db/partitioning-and-retention.md) 의 월 RANGE 파티션 규칙을 픽스처에도 적용한다. 파티션 없이 INSERT 시 PostgreSQL 이 즉시 오류를 반환한다.

```sql
-- 테스트용 파티션 선행 생성 (2개월치)
CREATE TABLE IF NOT EXISTS rawdata_1m_h_202601
    PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE IF NOT EXISTS rawdata_1m_h_202602
    PARTITION OF rawdata_1m_h
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
```

**데이터 분량 기준**:

| 목적 | 파티션 수 | 행 수 |
|------|----------|------|
| 단순 CRUD 검증 | 1개 | 10~50 |
| 파티션 프루닝 검증 | 2~3개 | 각 50~100 |
| 성능 기준선 | 1개 | 최대 1,000 |

**픽스처 상수 규칙**:

- `acq_dtm` / `rgstr_dtm` 픽스처 날짜는 **상수로 고정** (예: `LocalDateTime.of(2026, 1, 15, 0, 0)`)
- `LocalDateTime.now()` 사용 금지 — 생성된 파티션 범위를 벗어날 수 있음

---

## 6. Spring Boot 4 슬라이스 어노테이션 패키지 경로

Boot 3 → 4 에서 슬라이스 어노테이션의 **패키지 경로가 이동**되었다. 슬라이스 테스트 도입 시 다음 경로를 사용한다:

```java
// Spring Boot 4 패키지 경로
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
```

Boot 3 경로(`org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` 등) 는 더 이상 사용하지 않는다. `@MockBean` 은 Boot 4 에서 **제거**되었으므로 `@MockitoBean` 으로 교체한다 ([`test-strategy.md §2`](test-strategy.md) 참조).

---

## 7. 통합 테스트 소스셋 분리 기준

Testcontainers 기반 통합 테스트가 **10개 초과** 누적되면 별도 소스셋으로 분리하여 CI 단계를 구분한다.

```groovy
// build.gradle — 도입 시 추가
sourceSets {
    integrationTest {
        java.srcDir 'src/integration-test/java'
        resources.srcDir 'src/integration-test/resources'
        compileClasspath += sourceSets.main.output + configurations.testRuntimeClasspath
        runtimeClasspath += output + compileClasspath
    }
}

task integrationTest(type: Test) {
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    useJUnitPlatform()
}
```

E2E 테스트는 별도 태스크(`./gradlew.bat :api:test -Pe2e`) 로 분리하여 실행 시간을 관리한다.

---

## 8. 도입 시 검증 사항

- [ ] `build.gradle` 에 Testcontainers 의존성 추가 후 빌드 통과 확인
- [ ] `@ServiceConnection` 기반 첫 테스트 실행 성공 (PostgreSQL 16 컨테이너 기동)
- [ ] 파티션 선행 생성 SQL 이 `@Sql` 또는 픽스처 초기화 코드로 실행되는지 확인
- [ ] CI 환경의 Docker-in-Docker 또는 Testcontainers Cloud 연결 검증
- [ ] [`test-strategy.md §5.2`](test-strategy.md) 필수 3종 시나리오 중 통합 격상 대상이 있다면 별도 ANALYZE 로 의사 결정 기록
- [ ] 통합 테스트 10개 초과 시 §7 소스셋 분리 검토
