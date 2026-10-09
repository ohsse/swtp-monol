---
status: completed
created: 2026-04-21
updated: 2026-04-21
---
# application.yml 공통 설정 분리 — TASK

## 관련 계획
- [계획안](../../../plan/20260421/yml_공통설정_분리/PLAN1.md)

## Phase

### Phase 1: application-common.yml 신규 생성
- [x] `api/src/main/resources/application-common.yml` 생성 (auth.jwt.* + mybatis)
- [x] `scheduler/src/main/resources/application-common.yml` 생성 (mybatis)

### Phase 2: application.yml 수정 — 공통 섹션 제거 및 include 추가
- [x] `api/src/main/resources/application.yml` 수정 — `spring.profiles.include: common` 추가, `mybatis` 블록(19-21행) 및 `auth` 블록(26-38행) 제거
- [x] `scheduler/src/main/resources/application.yml` 수정 — `spring.profiles.include: common` 추가, `mybatis` 블록(24-26행) 제거

### Phase 3: 프로파일 yml 수정 — include 라인 보존
- [x] `api/src/main/resources-env/local/application.yml` 수정 — `spring.profiles.include: common` 최상단 추가
- [x] `scheduler/src/main/resources-env/local/application.yml` 수정 — `spring.profiles.include: common` 최상단 추가

### Phase 4: 빌드 및 테스트 검증
- [x] `./gradlew.bat :api:test` 실행 성공 확인
- [x] `./gradlew.bat :scheduler:test` 실행 성공 확인

## 산출물
- [결과](../../../results/20260421/yml_공통설정_분리/RESULT1.md)
