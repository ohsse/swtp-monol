---
status: approved
created: 2026-04-21
updated: 2026-04-21
---
# application.yml 공통 설정 분리 — `application-common.yml` 도입

## 목적

`auth`와 `mybatis` 설정이 지자체 프로파일 yml에 의해 유실되는 리스크를 제거하고,
변경이 필요 없는 공통 설정을 한 파일(`application-common.yml`)에서 관리한다.

## 배경

`{api,scheduler}/build.gradle`은 `srcDirs` + `DuplicatesStrategy.INCLUDE`로 **파일 단위 덮어쓰기** 방식을 사용한다.
`resources-env/{profile}/application.yml`이 기본 `resources/application.yml`을 통째로 교체하므로,
지자체 프로파일 yml이 `auth` / `mybatis` 섹션을 생략하면 해당 블록 전체가 사라진다.
현재 `local` 프로파일 yml이 이미 이 상태이므로 `auth.jwt.exclude-paths`가 비어 JWT 필터 화이트리스트가 유실될 수 있다.

레거시 `reference` 프로젝트가 `application-common.yml` + `spring.profiles.include: common` 패턴으로 동일 문제를 해결한 선례를 따른다.

## 범위

- `api`, `scheduler` 두 모듈의 yml 파일만 수정 (common 모듈, Java 코드 변경 없음)
- 공통 대상: `mybatis.configuration.map-underscore-to-camel-case` (양 모듈), `auth.jwt.*` (api 전용)

## 구현 방향

모듈당 `application-common.yml` 1개를 신규 생성하고,
기존 `application.yml`에서 공통 섹션을 제거 후 `spring.profiles.include: common`으로 활성화한다.
`resources-env/{profile}/application.yml`(현재 `local`)에도 동일 include 라인을 추가하여 파일 단위 교체 이후에도 common 프로파일이 유지되도록 한다.

## 테스트 전략

- `./gradlew.bat :api:test` — AuthJwtProperties 바인딩 및 JwtAuthenticationFilter 테스트 통과 확인
- `./gradlew.bat :scheduler:test` — MyBatis 매핑 정상 동작 확인
- `./gradlew.bat :api:bootJar -Pprofile=local` 후 jar 내부에 `application-common.yml` 존재 및 `spring.profiles.include: common` 유지 확인

## 제외 사항

- JPA, server.port 등 다른 공통 설정의 이동 (이번 범위 아님)
- Gradle 빌드 로직 수정
- common 모듈 리소스 도입
- 신규 지자체 프로파일 추가

## 예상 산출물

- [태스크](../../../tasks/20260421/yml_공통설정_분리/TASK1.md)
