---
status: draft
created: 2026-04-16
updated: 2026-04-16
---
# p6spy 로깅 정책 + resources-env 빌드 구조 적용 리뷰

## 관련 결과
- [결과](../../../results/20260416/p6spy_로깅정책/RESULT1.md)

---

## 리뷰 범위

| Phase | 대상 파일 |
|-------|-----------|
| Phase 1 | `common/build.gradle`, `common/.../p6spy/CustomP6SpySqlFormatter.java` |
| Phase 2 | `api/build.gradle`, `scheduler/build.gradle` |
| Phase 3 | `api/scheduler` `resources/` 하위 `application.yml`, `spy.properties`, `logback-spring.xml` |
| Phase 4 | `api/scheduler` `resources-env/local/` 하위 동일 3종 설정 파일 |

---

## 발견 사항

### 높음 (블로커)

#### [BLOCK-1] DB 자격 증명 하드코딩 — 보안 규칙 위반

- **파일**: `api/src/main/resources/application.yml`, `scheduler/src/main/resources/application.yml`, `api/src/main/resources-env/local/application.yml`, `scheduler/src/main/resources-env/local/application.yml`
- **근거**: `CLAUDE.md` — "JWT secret, DB 계정, 민감 설정은 코드에 하드코딩하지 않는다", "실제 자격 증명은 커밋하지 않는다"
- **내용**: `datasource.username`, `datasource.password`가 평문으로 커밋되어 있다. 기본 파일은 운영 빌드에도 포함되므로 영향 범위가 넓다.

수정 방향:
```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:p6spy:postgresql://localhost:5432/smartwtp}
    username: ${DB_USERNAME:smartwtp}
    password: ${DB_PASSWORD:smartwtp}
```

#### [BLOCK-2] `Long.parseLong(now)` 예외 미처리 — 런타임 오류 유발

- **파일**: `common/src/main/java/com/mo/smartwtp/common/p6spy/CustomP6SpySqlFormatter.java:59`
- **내용**: p6spy의 `commit`, `rollback` 카테고리 호출 시 `now` 파라미터에 빈 문자열이 전달되는 케이스가 실제 존재한다. 현재 코드는 `Long.parseLong(now)`를 try-catch 없이 호출하므로, 이 경우 `NumberFormatException`이 발생하여 SQL 로그가 누락되고 스택 트레이스가 콘솔에 출력된다.

수정 방향:
```java
LocalDateTime timestamp;
try {
    timestamp = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(Long.parseLong(now)), ZoneId.systemDefault());
} catch (NumberFormatException e) {
    timestamp = LocalDateTime.now();
}
```

---

### 중간

#### [MID-1] `DuplicatesStrategy.INCLUDE` 의도 미주석

- **파일**: `api/build.gradle:22`, `scheduler/build.gradle:22`
- **내용**: `INCLUDE` 전략으로 환경 파일이 기본 파일을 묵시적으로 덮어쓴다. 처리 순서 및 오버라이드 의도가 코드에서 명확히 드러나지 않아 유지보수 혼동 가능성이 있다.

#### [MID-2] `resources/application.yml` 기본 파일에 실행 가능한 DB URL 포함

- **파일**: `api/src/main/resources/application.yml:5`, `scheduler/src/main/resources/application.yml:5`
- **내용**: 기본 파일에 `localhost:5432/smartwtp`가 실제 URL로 기입되어 있어, 운영 프로파일이 DB URL을 오버라이드하지 않으면 로컬 URL이 그대로 운영 환경에 적용된다. `${DB_URL:...}` 형태로 외부화 필요. (BLOCK-1 수정 시 함께 해결됨)

#### [MID-3] `hibernate-core` compileOnly — 런타임 누락 위험

- **파일**: `common/build.gradle:17`
- **내용**: `BasicFormatterImpl`은 런타임에 실제 호출된다. 현재는 `api`/`scheduler`의 `spring-boot-starter-data-jpa`를 통해 우연히 런타임에 포함되지만, 의존성 그래프 변경 시 `ClassNotFoundException`이 발생할 수 있다. `compileOnly` 선택 이유를 주석으로 명시하거나 `implementation`으로 전환 필요.

#### [MID-4] `resources-env/local/application.yml` — `ddl-auto: update` 이유 미명시

- **파일**: `api/src/main/resources-env/local/application.yml:9`, `scheduler/src/main/resources-env/local/application.yml:9`
- **내용**: 기본 파일은 `none`인데 로컬만 `update`이다. 의도적이라면 이유를 주석으로 남겨야 한다. 의도가 불명확하면 `validate` 또는 `none` 통일이 안전하다.

---

### 낮음

#### [LOW-1] `ext.profile` 기본값 `'common'` — 존재하지 않는 경로 무검증

- **파일**: `api/build.gradle:11`, `scheduler/build.gradle:11`
- **내용**: `resources-env/common/`이 없어도 Gradle이 경고 없이 빈 srcDir로 처리한다. 빌드 타임 검증 구문 또는 README 안내 추가를 권장한다.

#### [LOW-2] `resources-env/local/spy.properties`가 기본 파일과 내용 동일

- **파일**: `api/src/main/resources-env/local/spy.properties`, `scheduler/src/main/resources-env/local/spy.properties`
- **내용**: 주석 첫 줄 외 기본 파일과 완전히 동일하다. 기본 파일 수정 시 로컬 파일도 수정해야 하는 중복 유지 비용이 발생한다. 로컬 파일 삭제를 검토할 수 있다.

#### [LOW-3] `logback-spring.xml` 파일명 선택 이유 미명시

- **파일**: `api/src/main/resources/logback-spring.xml`, `scheduler/src/main/resources/logback-spring.xml`
- **내용**: `logback-spring.xml`을 사용하면서 `<springProfile>` 태그를 활용하지 않는다. `resources-env` 교체 방식으로 환경 분리를 완전히 처리하므로 문제는 없으나, 파일명 선택 이유를 주석으로 남겨두면 혼동을 방지할 수 있다.

---

## 개선 제안

1. `CustomP6SpySqlFormatter`에 `category`가 `commit`/`rollback`인 경우 조기 반환 가드 추가를 권장한다.
2. Gradle 빌드 파일에 `profile` 디렉토리 존재 여부를 빌드 타임에 검증하는 구문 추가를 고려할 수 있다.
3. `resources/spy.properties`와 `resources-env/local/spy.properties`가 동일하다면 로컬 파일 삭제로 중복을 제거할 수 있다.

---

## 결론

**블로커 2건 발견 — 수정 후 재리뷰 필요**

| 번호 | 항목 | 심각도 |
|------|------|--------|
| BLOCK-1 | DB 자격 증명 하드코딩 | 높음 |
| BLOCK-2 | `Long.parseLong(now)` 예외 미처리 | 높음 |

블로커 2건을 제외한 전반적인 구조는 프로젝트 규칙에 부합한다.
- p6spy + Hibernate 이중 출력 방지 구성 (`show_sql: false`, Hibernate 로거 OFF) 적절
- `resources-env` 빌드 분리 구조 의도에 맞게 구성
- `CustomP6SpySqlFormatter` 패키지 위치(`common.p6spy`) 규칙 준수
- Javadoc 주석 충실히 작성됨

**블로커 해소 후 `/dev:review p6spy_로깅정책` 재실행 필요**
