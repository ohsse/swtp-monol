---
status: completed
created: 2026-04-16
updated: 2026-04-16
---
# p6spy 로깅 정책 + resources-env 빌드 구조 적용 계획

## 목적

JPA/Querydsl과 MyBatis를 혼용하는 환경에서 모든 SQL을 통합 로깅하고, 지자체별 배포를 위한 resources-env 빌드 구조를 구축한다.

## 배경

- 현재 Hibernate `format_sql: true`만 설정되어 있어 JPA 쿼리만 포매팅 출력, 바인딩 파라미터는 `?`로 표시, MyBatis 쿼리는 미포착
- 사업 대상이 지자체(지역)별로 나뉘어 환경별 설정 분리 및 선택 빌드 인프라 필요

## 범위

### 타당성 검토: p6spy vs log4jdbc

| 기준 | p6spy | log4jdbc |
|------|-------|----------|
| JPA+MyBatis 통합 로깅 | JDBC 드라이버 래핑 → 모든 SQL 포착 | DataSource 래핑 → MyBatis 별도 SqlSessionFactory 시 누락 가능 |
| 바인딩 파라미터 | `?`를 실제 값으로 치환한 완전한 SQL 출력 | 동일 |
| Spring Boot 호환 | JDBC 레벨이므로 Boot 버전 무관 | Boot 자동 구성과 충돌 가능 |
| 유지보수 | 활발한 업데이트 | 2016년 이후 업데이트 없음 |

**결론**: JPA+MyBatis 혼용 환경에서 p6spy가 적합하다.

### 라이브러리 선택

| 방식 | Spring Boot 4.0.5 호환 | 비고 |
|------|----------------------|------|
| `gavlyukovskiy:p6spy-spring-boot-starter:1.11.0` | 미보장 (Boot 3.x 대상) | 자동 구성이 Boot 4 내부 API 변경에 깨질 위험 |
| **`p6spy:p6spy:3.9.1` (순수)** | 호환 (JDBC 레벨 래핑) | driver-class-name + URL 접두사 수동 변경 |

**선택: 순수 `p6spy:p6spy:3.9.1`**

### 변경 대상 파일

**수정**

| 파일 | 변경 내용 |
|------|----------|
| `common/build.gradle` | p6spy + hibernate-core(compileOnly) 의존성 추가 |
| `api/build.gradle` | resources-env sourceSets + processResources 설정 추가 |
| `scheduler/build.gradle` | 동일 |
| `api/src/main/resources/application.yml` | p6spy 드라이버 적용, Hibernate SQL 비활성화 |
| `scheduler/src/main/resources/application.yml` | 동일 |

**신규 생성**

| 파일 | 역할 |
|------|------|
| `common/.../p6spy/CustomP6SpySqlFormatter.java` | SQL pretty-print 포매터 |
| `api/src/main/resources/spy.properties` | p6spy 공통 설정 |
| `api/src/main/resources/logback-spring.xml` | 기본 logback (콘솔+롤링파일+에러롤링파일) |
| `scheduler/src/main/resources/spy.properties` | 동일 |
| `scheduler/src/main/resources/logback-spring.xml` | 동일 |
| `api/src/main/resources-env/local/*` | 로컬 환경 설정 (application.yml, logback-spring.xml, spy.properties) |
| `scheduler/src/main/resources-env/local/*` | 동일 |

## 구현 방향

### 1. 의존성 구조

- `common` 모듈에 `api 'p6spy:p6spy:3.9.1'`로 추가하여 api/scheduler로 전이
- `hibernate-core`는 `compileOnly` — 런타임에는 spring-boot-starter-data-jpa가 제공

### 2. CustomP6SpySqlFormatter

- `MessageFormattingStrategy` 구현
- Hibernate `BasicFormatterImpl`로 SQL pretty-print
- 타임스탬프, 실행시간(ms), 카테고리, 커넥션ID 포함

### 3. resources-env 빌드 구조

```
{모듈}/src/main/
├── resources/                    # 공통 기본 설정
│   ├── application.yml
│   ├── logback-spring.xml
│   └── spy.properties
└── resources-env/
    └── local/                    # 로컬 환경 오버라이드
        ├── application.yml
        ├── logback-spring.xml
        └── spy.properties
    └── {지자체코드}/              # 향후 지자체별 추가
```

- Gradle: `ext.profile` + `sourceSets.main.resources.srcDirs` + `DuplicatesStrategy.INCLUDE`
- 빌드: `./gradlew.bat :{모듈}:bootJar -Pprofile={환경}`

### 4. 로깅 전략

- 모든 환경에서 p6spy 활성화
- Hibernate SQL 로거(`org.hibernate.SQL`) 비활성화 → p6spy가 대체
- logback: CONSOLE + ROLLING_FILE(100MB/30일/1GB) + ERR_ROLLING_FILE(ERROR 전용)
- 환경별 `spy.properties`로 `executionThreshold` 등 제어

### 5. application.yml 변경

- `driver-class-name: com.p6spy.engine.spy.P6SpyDriver`
- `url: jdbc:p6spy:postgresql://...`
- `hibernate.format_sql: false`, `show_sql: false`

## 테스트 전략

```bash
# 전체 빌드 검증
./gradlew.bat clean build

# profile 지정 빌드 — local 오버라이드 포함 확인
./gradlew.bat :api:bootJar -Pprofile=local
```

## 제외 사항

- 지자체별 resources-env 디렉토리 생성 (향후 지자체 확정 시 추가)
- 운영 환경 logback 설정 (배포 대상 확정 후 작성)

## 예상 산출물

- [태스크](../../../tasks/20260416/p6spy_로깅정책/TASK1.md)
