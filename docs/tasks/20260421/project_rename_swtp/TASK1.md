---
status: completed
created: 2026-04-21
updated: 2026-04-21
---

# 프로젝트 Rename TASK: smart-wtp-backend → swtp / com.mo.smartwtp → com.mo.swtp

## 관련 계획
- [계획안](../../../plan/20260421/project_rename_swtp/PLAN1.md)

## Phase

### Phase 0: 사전 준비
- [x] `git status` clean 상태 확인
- [x] `./gradlew.bat clean build` 기준선 Green 확인
- [x] `build/`, `.gradle/` 디렉토리 삭제 (stale Q클래스 방지)
- [x] `rg -c 'com\.mo\.smartwtp' --glob '!docs/**' --glob '!legacy/**' --glob '!reference/**'` 건수 기록 (83파일)
- [x] `rg -c 'smart-wtp' --glob '!docs/**' --glob '!legacy/**' --glob '!reference/**'` 건수 기록 (9파일)

### Phase 1: Java 패키지 이동 (커밋 #1)
> Python 스크립트로 `com.mo.smartwtp` → `com.mo.swtp` 이동 (모듈 3개 × main/test)

- [x] `common/src/main/java/com/mo/smartwtp/**` (패키지 이동, 20 파일)
- [x] `common/src/test/java/com/mo/smartwtp/**` (패키지 이동, 6 파일)
- [x] `api/src/main/java/com/mo/smartwtp/**` (패키지 이동, 31 파일)
- [x] `api/src/test/java/com/mo/smartwtp/**` (패키지 이동, 9 파일)
- [x] `scheduler/src/main/java/com/mo/smartwtp/**` (패키지 이동, 6 파일)
- [x] `scheduler/src/test/java/com/mo/smartwtp/**` (패키지 이동, 1 파일)
- [x] `./gradlew.bat clean compileJava compileTestJava` 성공 확인
- [x] `git commit` — 메시지: `chore: Java 패키지 com.mo.smartwtp → com.mo.swtp 이동`

### Phase 2: 하드코딩 FQN 문자열 교체 (커밋 #2)

- [x] `api/src/main/java/com/mo/swtp/ApiApplication.java` — Phase 1에서 이미 교체됨
- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/SchedulerApplication.java` — Phase 1에서 이미 교체됨
- [x] `api/src/main/java/com/mo/swtp/api/config/persistence/ApiMybatisConfig.java` — Phase 1에서 이미 교체됨
- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/config/persistence/SchedulerMybatisConfig.java` — Phase 1에서 이미 교체됨
- [x] `api/src/main/resources/spy.properties` — `logMessageFormat` FQN 교체
- [x] `api/src/main/resources-env/local/spy.properties` — 교체
- [x] `scheduler/src/main/resources/spy.properties` — 교체
- [x] `scheduler/src/main/resources-env/local/spy.properties` — 교체
- [x] `./gradlew.bat clean build` 성공 확인 (test 포함)
- [x] `git commit` — 메시지: `chore: FQN 하드코딩 문자열 com.mo.swtp로 교체`

### Phase 3: Gradle·application.name·JWT 기본값 + JWT 테스트 (커밋 #3)

- [x] `settings.gradle` — `rootProject.name = 'smart-wtp-backend'` → `'swtp'`
- [x] `build.gradle` — `group = 'com.mo.smartwtp'` → `'com.mo.swtp'`
- [x] `api/src/main/resources/application.yml` — `spring.application.name: smart-wtp-api` → `swtp-api`
- [x] `scheduler/src/main/resources/application.yml` — `spring.application.name: smart-wtp-scheduler` → `swtp-scheduler`
- [x] `api/src/main/resources/application-common.yml` — JWT issuer/secret 교체
- [x] `common/src/test/java/com/mo/swtp/common/jwt/JwtTokenHelperTest.java` — issuer/secret 상수 교체
- [x] `api/src/test/java/com/mo/swtp/auth/web/JwtAuthenticationFilterTest.java` — issuer/secret 상수 교체
- [x] `api/src/test/java/com/mo/swtp/auth/service/JwtTokenManagementServiceTest.java` — issuer/secret 상수 교체
- [x] `./gradlew.bat clean build` 성공 확인
- [x] `git commit` — 메시지: `chore: Gradle 루트명·application.name·JWT 기본값 swtp로 변경`

### Phase 4: 문서·하네스 갱신 (커밋 #4)

- [x] `CLAUDE.md` — `com.mo.smartwtp` 참조 교체 (패키지 규칙 섹션)
- [x] `api/CLAUDE.md` — smartwtp 참조 교체
- [x] `common/CLAUDE.md` — smartwtp 참조 교체
- [x] `scheduler/CLAUDE.md` — smartwtp 참조 교체
- [x] `.claude/commands/dev/impl.md` — smartwtp 참조 교체
- [x] `.claude/rules/doc-harness.md` — smartwtp 참조 교체
- [x] `.claude/rules/test-strategy.md` — smartwtp 참조 교체
- [x] `.claude/rules/ot-integration.md` — smartwtp 참조 교체
- [x] `.claude/settings.local.json` — `smart-wtp-codex` 경로 정리 및 FQCN 교체
- [x] `README.md` — smartwtp 참조 교체
- [x] `api/src/main/java/com/mo/swtp/api/config/OpenApiConfig.java` — Swagger 메타데이터 교체
- [x] `rg 'com\.mo\.smartwtp|smart-wtp'` 결과 0건 확인
- [x] `git commit` — 메시지: `docs: 하네스·README의 smartwtp 참조 swtp로 갱신`

## 산출물
- [결과](../../../results/20260421/project_rename_swtp/RESULT1.md)
