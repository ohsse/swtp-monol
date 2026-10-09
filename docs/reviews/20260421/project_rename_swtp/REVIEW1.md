---
status: approved
created: 2026-04-21
updated: 2026-04-21
---

# 프로젝트 Rename 리뷰: smart-wtp-backend → swtp

## 관련 결과
- [결과](../../../results/20260421/project_rename_swtp/RESULT1.md)

## 리뷰 범위

| 항목 | 확인 방법 |
|------|---------|
| rename 완전성 | `rg 'com\.mo\.smartwtp\|smart-wtp'` 전수 검색 (docs/legacy/reference 제외) |
| 패키지 선언 일관성 | 이동된 Java 파일 `package` 선언 vs 디렉토리 경로 |
| JWT 테스트 상수 | 3개 JWT 테스트 파일 SECRET·issuer 값 |
| spy.properties FQN | api/scheduler 4개 파일 `logMessageFormat` |
| settings.local.json | 낡은 경로·FQCN |
| 보안 | JWT·DB 민감값 환경변수 주입 패턴 유지 여부 |

## 발견 사항

### 높음 (블로커)

없음

### 중간

**[1] JwtAuthenticationFilter request attribute 키 상수값 미교체** — ✅ 리뷰 중 수정 완료

- 파일: `api/src/main/java/com/mo/swtp/auth/web/JwtAuthenticationFilter.java` L24-25
- 내용: `AUTH_SUBJECT_ATTRIBUTE = "smartwtp.auth.subject"`, `AUTH_CLAIMS_ATTRIBUTE = "smartwtp.auth.claims"` 값이 잔존
- 원인: Python 치환 스크립트가 `com.mo.smartwtp` 패턴만 교체하여 네임스페이스 형식의 단순 `smartwtp` 문자열을 누락
- 조치: `swtp.auth.subject` / `swtp.auth.claims`로 교체 후 api 테스트 통과 확인
- 커밋: `0ce3ebd` (`fix: JwtAuthenticationFilter request attribute 키값 swtp로 교체`)

### 낮음

**[2] DB 접속 기본값·README 예시의 `smartwtp` 잔존** — 의도된 제외

- 파일: `api/scheduler/src/main/resources/application.yml`, `application-test.yml`, `README.md`
- 내용: `jdbc:...smartwtp`, `username: smartwtp` 등 DB 스키마·계정명에 `smartwtp` 잔존
- 판단: PLAN 명시 제외 사항 — "DB 스키마·계정명 변경: 별도 운영 결정". `application*.yml`의 DB 관련 라인은 touch 금지로 계획에 명시되어 있으므로 누락이 아님.
- 후속: DB 마이그레이션 결정 시 별도 슬러그(`db_rename_swtp`)로 처리 권장.

## 개선 제안

- Python 기반 rename 스크립트를 재사용 시 `com.mo.smartwtp` 외 `smartwtp`(단독 네임스페이스) 패턴도 포함하도록 교체 범위를 확장할 것을 권장.

## 결론

블로커 없음. 중간 이슈 [1]은 리뷰 과정에서 즉시 수정·커밋 완료되었다.
Java 패키지 선언·import·Gradle 설정·JWT 상수·spy.properties FQN 교체가 모두 정상 완료되었으며,
전체 테스트(22 tasks) BUILD SUCCESSFUL 확인. 커밋을 진행해도 무방하다.
