---
status: approved
created: 2026-04-21
updated: 2026-04-21
---

# 프로젝트 Rename: smart-wtp-backend → swtp / com.mo.smartwtp → com.mo.swtp

## 목적

Gradle 루트 프로젝트명과 Java 기본 패키지를 디렉토리·저장소명(`swtp`)과 일치하도록 전면 rename한다.
기능 변경 없는 순수 rename 작업이다.

## 배경

현재 작업 디렉토리는 `C:\dev\workspace\swtp\backend`로 `swtp` 기반이지만,
내부 식별자는 레거시 네이밍이 그대로 남아 있다.

- `rootProject.name = 'smart-wtp-backend'` → JAR 파일명, Gradle 산출물명에 노출
- `group = 'com.mo.smartwtp'` → Maven group ID 및 패키지 루트에 노출
- `@SpringBootApplication(scanBasePackages = "com.mo.smartwtp")` 등 문자열 하드코딩
- `spring.application.name: smart-wtp-api` → 모니터링·로그 레이블에 노출

## 범위

| 카테고리 | 현재 | 변경 후 |
|---------|------|---------|
| Gradle 루트명 | `smart-wtp-backend` | `swtp` |
| Gradle group | `com.mo.smartwtp` | `com.mo.swtp` |
| Java 패키지 | `com.mo.smartwtp.*` | `com.mo.swtp.*` |
| spring.application.name | `smart-wtp-api` / `smart-wtp-scheduler` | `swtp-api` / `swtp-scheduler` |
| JWT issuer 개발 기본값 | `smart-wtp-api` | `swtp-api` |
| JWT secret 개발 기본값 | `local-dev-only-secret-change-me-0123456789` | `local-dev-only-secret-change-me-0123456789` |
| DB 스키마·계정명 `smartwtp` | — | **변경하지 않음** (운영 결정 별도) |
| `docs/` 과거 이력 문서 | — | **변경하지 않음** (이력 보존) |
| `legacy/`, `reference/` | — | **변경하지 않음** (스코프 밖) |

**영향 파일 규모**: Java 소스 73 파일 + 설정·하네스 20여 파일

## 구현 방향

### 4-Phase 커밋 전략

각 Phase 는 독립 커밋. Phase 1 단독은 런타임 기동이 깨지므로 **Phase 1+2 를 반드시 한 push 에 묶는다**.

#### Phase 0: 사전 준비 (커밋 없음)
- `git status` clean 확인, `./gradlew.bat clean build` 기준선 Green 확인
- IntelliJ 종료 후 `build/`, `.gradle/` 디렉토리 삭제 (QueryDSL Q클래스 stale 방지)
- 기준 grep 건수 기록
  - `rg -c 'com\.mo\.smartwtp' --glob '!docs/**' --glob '!legacy/**' --glob '!reference/**'`
  - `rg -c 'smart-wtp' --glob '!docs/**' --glob '!legacy/**' --glob '!reference/**'`

#### Phase 1: Java 패키지 이동 (IntelliJ 필수, 커밋 #1)
- **방법**: IntelliJ "Refactor → Move Package" — `com.mo.smartwtp` 루트를 `com.mo.swtp`로 이동
  (모듈 3개 × main/test 각각 수행)
- IDE 가 `package` 선언·`import` 문·FQN 레퍼런스 자동 갱신
- **문자열 리터럴은 이 커밋에 포함하지 않음** (Phase 2에서 처리)
- 검증: `./gradlew.bat clean compileJava compileTestJava` 성공
  (Phase 2 이전이라 bootRun/전체 test 는 실패 예상 — 의도된 상태)
- 커밋 메시지: `chore: Java 패키지 com.mo.smartwtp → com.mo.swtp 이동`

**영향 파일:**

| 경로 | 파일 수 |
|------|--------|
| `common/src/{main,test}/java/com/mo/smartwtp/**` | 26 파일 |
| `api/src/{main,test}/java/com/mo/smartwtp/**` | 40 파일 |
| `scheduler/src/{main,test}/java/com/mo/smartwtp/**` | 7 파일 |

#### Phase 2: 하드코딩 FQN 문자열 교체 (커밋 #2)
- 4개 Java 파일의 `scanBasePackages` / `@MapperScan basePackages` 문자열
- 4개 `spy.properties`의 `logMessageFormat` FQN
- 검증: `./gradlew.bat clean build` 통과, `:api:bootRun` / `:scheduler:bootRun` 부팅 smoke

**대상 파일 (Phase 1 이동 후 새 경로):**

| 파일 | 변경 위치 |
|------|---------|
| `api/src/main/java/com/mo/swtp/ApiApplication.java` | `scanBasePackages = "com.mo.smartwtp"` |
| `scheduler/src/main/java/com/mo/swtp/scheduler/SchedulerApplication.java` | `scanBasePackages = "com.mo.smartwtp"` |
| `api/src/main/java/com/mo/swtp/api/config/persistence/ApiMybatisConfig.java` | `@MapperScan(basePackages = "com.mo.smartwtp", ...)` |
| `scheduler/src/main/java/com/mo/swtp/scheduler/config/persistence/SchedulerMybatisConfig.java` | `@MapperScan(basePackages = "com.mo.smartwtp", ...)` |
| `api/src/main/resources/spy.properties` | `logMessageFormat` FQN |
| `api/src/main/resources-env/local/spy.properties` | 동일 |
| `scheduler/src/main/resources/spy.properties` | 동일 |
| `scheduler/src/main/resources-env/local/spy.properties` | 동일 |

- 커밋 메시지: `chore: FQN 하드코딩 문자열 com.mo.swtp로 교체`

#### Phase 3: Gradle 루트·app name·JWT 기본값 + JWT 테스트 (커밋 #3)
- `settings.gradle:1` `rootProject.name`
- `build.gradle:12` `group`
- `api/src/main/resources/application.yml:3` `spring.application.name`
- `scheduler/src/main/resources/application.yml:3` `spring.application.name`
- `api/src/main/resources/application-common.yml:10-11` JWT secret/issuer 기본값
- JWT 관련 테스트 상수 3 파일 12 라인 (issuer `smart-wtp` / secret `smart-wtp-jwt-*` 교체)
  - `common/src/test/java/com/mo/swtp/common/jwt/JwtTokenHelperTest.java` L13, 17, 35, 47
  - `api/src/test/java/com/mo/swtp/auth/web/JwtAuthenticationFilterTest.java` L119-120
  - `api/src/test/java/com/mo/swtp/auth/service/JwtTokenManagementServiceTest.java` L24, 31, 46, 66, 84
- **주의**: `application*.yml` 의 DB 관련 라인(`jdbc:`, `username:`, `password:`)은 **touch 금지**
- 검증: `./gradlew.bat clean build` 통과, 부팅 로그에 `swtp-api`/`swtp-scheduler` 확인, 로그인 API 1회 JWT 발급·검증 smoke
- 커밋 메시지: `chore: Gradle 루트명·application.name·JWT 기본값 swtp로 변경`

#### Phase 4: 문서·하네스 (커밋 #4)
- `CLAUDE.md` (루트) L4, 84-85
- `api/CLAUDE.md`, `common/CLAUDE.md`, `scheduler/CLAUDE.md`
- `.claude/commands/dev/impl.md:36-37`
- `.claude/rules/doc-harness.md:145`
- `.claude/rules/test-strategy.md:272`
- `.claude/rules/ot-integration.md:33, 77`
- `.claude/settings.local.json:8-10` (`smart-wtp-codex` 낡은 경로 정리 포함), `17-18` (FQCN 2건)
- `README.md:1, 9`
- 검증: `rg 'com\.mo\.smartwtp|smart-wtp' --glob '!docs/**' --glob '!legacy/**' --glob '!reference/**'` 결과 0건
- 커밋 메시지: `docs: 하네스·README의 smartwtp 참조 swtp로 갱신`

## 도메인 모델

해당 없음 (신규 엔티티·DTO 없음)

## DB 설계 변경

해당 없음 (DB 스키마 변경 없음)

## 테스트 전략

| Phase | 검증 명령 | 기대 결과 |
|-------|----------|---------|
| Phase 0 기준선 | `./gradlew.bat clean build` | Green |
| Phase 1 종료 | `./gradlew.bat clean compileJava compileTestJava` | 성공 (test 실행 실패는 예상된 상태) |
| Phase 2 종료 | `./gradlew.bat clean build` | Green (test 포함) |
| Phase 2 smoke | `./gradlew.bat :api:bootRun` / `:scheduler:bootRun` | Context 로드 성공 |
| Phase 3 종료 | `./gradlew.bat clean build` | Green |
| Phase 3 smoke | bootRun + 로그인 API 1회 | `swtp-api` 로그명, JWT 발급·검증 성공 |
| Phase 4 종료 | `rg 'com\.mo\.smartwtp\|smart-wtp' --glob '!docs/**' --glob '!legacy/**' --glob '!reference/**'` | 0건 |

## 제외 사항

- **DB 스키마·계정명 변경**: `application*.yml`의 `jdbc:...smartwtp`, `username/password: smartwtp` — 별도 운영 결정
- **`legacy/`, `reference/` 디렉토리** — 과거 레퍼런스 코드
- **`docs/` 과거 PLAN/TASK/RESULT/REVIEW 문서 189건** — 이력 보존
- **`build/`, `.gradle/`** — gitignore 대상, rename 이전 clean 후 제거

## 리스크 및 롤백

- **IntelliJ 필수** (Phase 1): `sed`/스크립트 방식은 Lombok·QueryDSL annotation processor 감지 누락 위험
- **Q클래스 캐시**: 각 Phase 전 `./gradlew.bat clean` 필수. IDE Phase 1 후 "Invalidate Caches" 1회
- **Phase 1+2 한 push 묶음**: Phase 1 단독 push 시 CI 가 런타임 기동 실패 상태를 main에서 봄
- **역순 revert 원칙**: Phase 2/3 단독 revert 시 일관성 파괴 → 항상 역순 일괄 revert

## 예상 산출물

- [태스크](../../../tasks/20260421/project_rename_swtp/TASK1.md)
