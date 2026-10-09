---
status: completed
created: 2026-04-16
updated: 2026-04-16
---
# p6spy 로깅 정책 + resources-env 빌드 구조 적용 결과

## 관련 작업
- [계획안](../../../plan/20260416/p6spy_로깅정책/PLAN1.md)
- [태스크](../../../tasks/20260416/p6spy_로깅정책/TASK1.md)

---

## 작업 요약

p6spy SQL 로깅 정책을 공통 모듈(`common`)에 정의하고, `api`·`scheduler` 실행 모듈에 전파되도록 구성했다.
아울러 `resources-env/{profile}` 빌드 오버라이드 구조를 `api`·`scheduler` 두 모듈에 동시에 적용하여,
`-Pprofile=local` 빌드 플래그 하나로 환경별 설정 파일을 선택적으로 오버라이드할 수 있게 되었다.

---

## 변경 사항

### 신규 생성

| 파일 경로 | 설명 |
|-----------|------|
| `common/src/main/java/com/mo/smartwtp/common/p6spy/CustomP6SpySqlFormatter.java` | 바인딩 파라미터 치환 + Hibernate `BasicFormatterImpl` pretty-print 포매터 |
| `api/src/main/resources/spy.properties` | api 기본 p6spy 설정 (Slf4JLogger, executionThreshold=0) |
| `api/src/main/resources/logback-spring.xml` | api 기본 로그 설정 (CONSOLE + ROLLING_FILE + ERR_ROLLING_FILE, p6spy→CONSOLE) |
| `api/src/main/resources-env/local/spy.properties` | api 로컬 p6spy 오버라이드 (모든 쿼리 로깅) |
| `api/src/main/resources-env/local/logback-spring.xml` | api 로컬 로그 오버라이드 (p6spy→CONSOLE+ROLLING_FILE) |
| `scheduler/src/main/resources/spy.properties` | scheduler 기본 p6spy 설정 |
| `scheduler/src/main/resources/logback-spring.xml` | scheduler 기본 로그 설정 |
| `scheduler/src/main/resources-env/local/spy.properties` | scheduler 로컬 p6spy 오버라이드 |
| `scheduler/src/main/resources-env/local/logback-spring.xml` | scheduler 로컬 로그 오버라이드 |

### 수정

| 파일 경로 | 변경 내용 |
|-----------|-----------|
| `common/build.gradle` | `api 'p6spy:p6spy:3.9.1'`, `compileOnly 'org.hibernate.orm:hibernate-core:7.0.0.Final'` 추가 |
| `api/build.gradle` | `ext.profile`, `sourceSets` 오버라이드, `processResources duplicatesStrategy` 추가 |
| `scheduler/build.gradle` | 동일 (resources-env 빌드 인프라 추가) |
| `api/src/main/resources/application.yml` | `spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver`, `datasource.url` p6spy 래핑, `jpa.show-sql/format-sql` 비활성화 |
| `api/src/main/resources-env/local/application.yml` | 로컬 DB 접속 정보 + p6spy 드라이버 오버라이드 |
| `scheduler/src/main/resources/application.yml` | 동일 (p6spy 드라이버 설정) |
| `scheduler/src/main/resources-env/local/application.yml` | 로컬 DB 접속 정보 오버라이드 |

---

## 테스트 결과

### 전체 빌드

```
./gradlew.bat clean build
BUILD SUCCESSFUL
```

- 전체 모듈(`common`, `api`, `scheduler`) 빌드 및 테스트 통과

### 로컬 프로파일 bootJar 검증

```
./gradlew.bat :api:bootJar -Pprofile=local
BUILD SUCCESSFUL
```

- `resources-env/local/` 하위 파일(application.yml, spy.properties, logback-spring.xml)이 `resources/` 기본 파일을 올바르게 오버라이드하는 것을 확인

### 테스트 통과 현황

| 모듈 | 통과 | 실패 |
|------|------|------|
| `common` | 전체 통과 | 0 |
| `api` | 전체 통과 | 0 |
| `scheduler` | 전체 통과 | 0 |

---

## 비고

- `CustomP6SpySqlFormatter`는 JPA(Querydsl 포함)와 MyBatis 쿼리 모두 동일하게 처리한다.
- `hibernate.show_sql` / `format_sql`은 p6spy와 중복 출력을 방지하기 위해 `false`로 설정했다.
- `resources-env/{profile}` 구조는 `INCLUDE` 전략을 사용하므로, 오버라이드 파일이 없는 환경은 `resources/` 기본 파일을 그대로 사용한다.
- `spy.properties`의 `executionThreshold` 기본값은 0(전부 로깅)이며, 운영 프로파일 추가 시 임계값 조정이 필요하다.
