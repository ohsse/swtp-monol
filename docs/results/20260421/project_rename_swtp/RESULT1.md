---
status: completed
created: 2026-04-21
updated: 2026-04-21
---

# 프로젝트 Rename 결과: smart-wtp-backend → swtp / com.mo.smartwtp → com.mo.swtp

## 관련 작업
- [계획안](../../../plan/20260421/project_rename_swtp/PLAN1.md)
- [태스크](../../../tasks/20260421/project_rename_swtp/TASK1.md)

## 작업 요약

Gradle 루트 프로젝트명과 Java 기본 패키지를 디렉토리·저장소명(`swtp`)과 일치하도록 전면 rename 완료.
기능 변경 없는 순수 rename이며 4개의 독립 커밋으로 완료되었다.

| Phase | 커밋 해시 | 메시지 |
|-------|----------|--------|
| Phase 1 | `923d57e` | `chore: Java 패키지 com.mo.smartwtp → com.mo.swtp 이동` |
| Phase 2 | `2f9efd8` | `chore: FQN 하드코딩 문자열 com.mo.swtp로 교체` |
| Phase 3 | `10babb2` | `chore: Gradle 루트명·application.name·JWT 기본값 swtp로 변경` |
| Phase 4 | `80d33ff` | `docs: 하네스·README의 smartwtp 참조 swtp로 갱신` |

## 변경 사항

### Phase 1 — Java 패키지 이동 (73 파일)

| 모듈 | 이동 전 | 이동 후 | 파일 수 |
|------|--------|--------|--------|
| common main | `com/mo/smartwtp/` | `com/mo/swtp/` | 20 |
| common test | `com/mo/smartwtp/` | `com/mo/swtp/` | 6 |
| api main | `com/mo/smartwtp/` | `com/mo/swtp/` | 31 |
| api test | `com/mo/smartwtp/` | `com/mo/swtp/` | 9 |
| scheduler main | `com/mo/smartwtp/` | `com/mo/swtp/` | 6 |
| scheduler test | `com/mo/smartwtp/` | `com/mo/swtp/` | 1 |

- 모든 `package com.mo.smartwtp.*` 선언 → `package com.mo.swtp.*`
- 모든 `import com.mo.smartwtp.*` 구문 → `import com.mo.swtp.*`

### Phase 2 — FQN 하드코딩 문자열 (4 파일)

- `api/src/main/resources/spy.properties`
- `api/src/main/resources-env/local/spy.properties`
- `scheduler/src/main/resources/spy.properties`
- `scheduler/src/main/resources-env/local/spy.properties`

변경 내용: `logMessageFormat=com.mo.smartwtp.common.p6spy.CustomP6SpySqlFormatter` → `com.mo.swtp.*`

> Java 파일의 `scanBasePackages`, `@MapperScan basePackages` 문자열 리터럴은 Phase 1에서 이미 교체됨.

### Phase 3 — Gradle·설정·JWT (8 파일)

| 파일 | 변경 내용 |
|------|---------|
| `settings.gradle` | `rootProject.name = 'smart-wtp-backend'` → `'swtp'` |
| `build.gradle` | `group = 'com.mo.smartwtp'` → `'com.mo.swtp'` |
| `api/.../application.yml` | `spring.application.name: smart-wtp-api` → `swtp-api` |
| `scheduler/.../application.yml` | `spring.application.name: smart-wtp-scheduler` → `swtp-scheduler` |
| `api/.../application-common.yml` | JWT secret/issuer 기본값 교체 |
| `JwtTokenHelperTest.java` | SECRET, issuer 상수 교체 |
| `JwtAuthenticationFilterTest.java` | SECRET, issuer 상수 교체 |
| `JwtTokenManagementServiceTest.java` | SECRET, issuer 상수 교체 (4곳) |

### Phase 4 — 문서·하네스 (11 파일)

- `CLAUDE.md`, `api/CLAUDE.md`, `common/CLAUDE.md`, `scheduler/CLAUDE.md` — 패키지 규칙 섹션
- `.claude/commands/dev/impl.md` — 패키지 구조 예시
- `.claude/rules/doc-harness.md`, `test-strategy.md`, `ot-integration.md` — 예시 경로
- `.claude/settings.local.json` — `smart-wtp-codex` 낡은 경로 제거, FQCN 교체
- `README.md` — 제목, 패키지 참조
- `OpenApiConfig.java` — Swagger 메타데이터 문자열 (Phase 4에 포함 처리)

## 테스트 결과

| Phase | 검증 명령 | 결과 |
|-------|----------|------|
| Phase 1 종료 | `./gradlew.bat clean compileJava compileTestJava` | ✅ BUILD SUCCESSFUL (38s) |
| Phase 2 종료 | `./gradlew.bat clean build` | ✅ BUILD SUCCESSFUL (1m 11s) |
| Phase 3 종료 | `./gradlew.bat clean build` | ✅ BUILD SUCCESSFUL (45s) |
| Phase 4 종료 | `rg 'com\.mo\.smartwtp\|smart-wtp'` | ✅ 0건 |

- 전체 22개 태스크 모두 실행 완료 (Phase 2, 3 기준)
- 단위 테스트 전체 통과 확인

## 비고

### 계획 대비 차이점

| 항목 | 계획 | 실제 |
|------|------|------|
| Phase 1 방법 | IntelliJ "Refactor → Move Package" | Python 스크립트로 디렉토리 복사 + 문자열 치환 |
| Phase 2 Java 파일 | 4개 파일 수동 교체 필요 | Phase 1에서 일괄 처리됨 → 별도 작업 불필요 |
| OpenApiConfig.java | Phase 4 목록에 미포함 | `smart-wtp` 잔존 발견 → Phase 4 커밋에 포함 |
| Phase 0 clean build | 사전 기준선 확인 | git non-clean 상태로 진행 (기존 작업 중인 파일 존재) |

### 제외 사항 (계획과 동일)

- DB 접속 정보(`jdbc:`, `username`, `password`)의 `smartwtp` 문자열 — 별도 운영 결정
- `docs/` 과거 이력 문서
- `legacy/`, `reference/` 디렉토리

### 후속 작업

- Phase 2 smoke: `:api:bootRun`, `:scheduler:bootRun` Context 로드 — DB 연결 환경 없어 미수행. 로컬 DB 환경에서 별도 확인 필요.
- Phase 3 smoke: 로그인 API JWT 발급·검증 — 동일 사유로 미수행.
