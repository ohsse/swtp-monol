---
status: completed
created: 2026-04-16
updated: 2026-04-16
---
# p6spy 로깅 정책 + resources-env 빌드 구조 적용 태스크

## 관련 계획
- [계획안](../../../plan/20260416/p6spy_로깅정책/PLAN1.md)

## Phase

### Phase 1: 의존성 및 공통 포매터
- [x] `common/build.gradle` — p6spy 3.9.1(`api`), hibernate-core(`compileOnly`) 추가
- [x] `common/src/main/java/com/mo/smartwtp/common/p6spy/CustomP6SpySqlFormatter.java` 생성

### Phase 2: resources-env 빌드 인프라
- [x] `api/build.gradle` — `ext.profile`, sourceSets, processResources 설정 추가
- [x] `scheduler/build.gradle` — 동일

### Phase 3: 기본 설정 파일 (resources/)
- [x] `api/src/main/resources/application.yml` — p6spy driver/url, hibernate show_sql/format_sql 비활성화
- [x] `api/src/main/resources/spy.properties` — 신규 생성
- [x] `api/src/main/resources/logback-spring.xml` — 신규 생성 (CONSOLE + ROLLING_FILE + ERR_ROLLING_FILE, p6spy→CONSOLE)
- [x] `scheduler/src/main/resources/application.yml` — 동일
- [x] `scheduler/src/main/resources/spy.properties` — 신규 생성
- [x] `scheduler/src/main/resources/logback-spring.xml` — 신규 생성

### Phase 4: resources-env/local 설정 파일
- [x] `api/src/main/resources-env/local/application.yml` — 로컬 DB 접속 정보 오버라이드
- [x] `api/src/main/resources-env/local/logback-spring.xml` — p6spy→CONSOLE+ROLLING_FILE
- [x] `api/src/main/resources-env/local/spy.properties` — executionThreshold=0
- [x] `scheduler/src/main/resources-env/local/application.yml`
- [x] `scheduler/src/main/resources-env/local/logback-spring.xml`
- [x] `scheduler/src/main/resources-env/local/spy.properties`

### Phase 5: 빌드 검증
- [x] `./gradlew.bat clean build` 성공 확인
- [x] `./gradlew.bat :api:bootJar -Pprofile=local` 성공 확인 (local 오버라이드 application.yml 포함 검증)

## 산출물
- [결과](../../../results/20260416/p6spy_로깅정책/RESULT1.md)
