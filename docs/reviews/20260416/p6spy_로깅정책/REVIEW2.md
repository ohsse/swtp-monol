---
status: approved
created: 2026-04-16
updated: 2026-04-16
---
# p6spy 로깅 정책 블로커 수정 재리뷰

## 관련 결과
- [결과](../../../results/20260416/p6spy_로깅정책/RESULT1.md)
- [1차 리뷰](REVIEW1.md)

---

## 리뷰 범위

REVIEW1.md의 블로커 2건 및 중간 항목 수정 여부 검증.

| 파일 | 검증 내용 |
|------|-----------|
| `common/src/main/java/com/mo/smartwtp/common/p6spy/CustomP6SpySqlFormatter.java` | BLOCK-2 try-catch 추가 |
| `api/src/main/resources/application.yml` | BLOCK-1 환경 변수 주입 |
| `scheduler/src/main/resources/application.yml` | BLOCK-1 환경 변수 주입 |
| `api/src/main/resources-env/local/application.yml` | BLOCK-1 + MID-4 |
| `scheduler/src/main/resources-env/local/application.yml` | BLOCK-1 + MID-4 |
| `common/build.gradle` | MID-3 주석 추가 |
| `api/build.gradle` | MID-1 주석 추가 |
| `scheduler/build.gradle` | MID-1 주석 추가 |

---

## 발견 사항

### 높음 (블로커)

없음

### 중간

없음

### 낮음

- `api/src/main/resources/application.yml:28` — JWT secret 기본값이 코드에 노출되어 있으나, 로컬 개발 편의 패턴으로 기존 코드와 일관성이 있어 블로커로 판단하지 않음. 운영 배포 시 환경 변수로 반드시 덮어써야 함.

---

## 개선 제안

없음

---

## 결론

**블로커 없음 — 커밋 진행 가능**

| 항목 | 이전 상태 | 현재 상태 |
|------|-----------|-----------|
| BLOCK-1: DB 자격 증명 하드코딩 | 블로커 | **해소** — 4개 파일 모두 `${DB_URL:...}` 등 환경 변수 주입 적용 |
| BLOCK-2: `Long.parseLong(now)` 예외 미처리 | 블로커 | **해소** — try-catch + `LocalDateTime.now()` 폴백 적용 |
| MID-1: `DuplicatesStrategy.INCLUDE` 미주석 | 중간 | **개선** — 오버라이드 의도 주석 추가 |
| MID-3: `hibernate-core` compileOnly 이유 미명시 | 중간 | **개선** — 런타임 제공 경로 및 주의 사항 주석 추가 |
| MID-4: `ddl-auto: update` 이유 미명시 | 중간 | **개선** — 로컬 개발 목적 주석 추가 |
