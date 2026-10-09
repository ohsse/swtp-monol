---
status: approved
created: 2026-04-21
updated: 2026-04-21
---
# ErrorCode 계약 강화 — message 필드 재현 위반 차단

## 목적

`UserErrorCode`, `AuthErrorCode`에 추가된 `message` 필드(데드 코드)와 같은 규약 위반이 Claude 세션에서 반복 재현되지 않도록, 문서화와 훅 기반 사전 차단을 도입한다.

## 배경

프로젝트 설계는 "실패 응답에 에러 코드만 싣고, 사용자 표기 문자열은 프론트엔드가 명세 기반으로 매핑"이다.
이는 코드로 이미 강제된다:
- `ErrorCode` 인터페이스: `name()`, `getHttpStatus()`만 요구 — `getMessage()` 없음
- `CommonResponseDto`: `code`, `data` 필드만 보유 — `message` 필드 없음
- `RestApiAdvice`: 응답 빌드 시 `errorCode.name()`만 직렬화

그러나 "message 필드 금지" 규약이 어디에도 문서화되어 있지 않아 재현 위반이 발생했다.

## 범위

1. **규약 문서화** — `.claude/rules/exception-patterns.md` 신설, 상위 CLAUDE.md에 참조 추가
2. **PostToolUse 훅** — Write/Edit 시 `*ErrorCode.java` 또는 `implements ErrorCode` enum에서 message 필드/메서드 감지 → exit 2 차단
3. **리뷰 체크리스트 강화** — `.claude/commands/dev/review.md` 체크리스트에 필드 구성 점검 추가
4. **기존 위반 정리** — `UserErrorCode`, `AuthErrorCode`에서 `message` 필드·인자 제거
5. **빌드 검증** — `./gradlew.bat :api:test` 회귀 없음 확인

## 구현 방향

### 1. rule 문서 신설 — `.claude/rules/exception-patterns.md`

- 응답 계약 요약 (ErrorCode interface, CommonResponseDto, RestApiAdvice 3자 연계)
- `ErrorCode` 구현 enum 필드 규약: `httpStatus`만 허용, 사용자 표기용 문자열 필드 금지
- 올바른 예 (`CommonErrorCode`) vs 위반 예 병치

### 2. CLAUDE.md 참조 연결

- `backend/CLAUDE.md §규칙 문서 인덱스` — `exception-patterns.md` 행 추가
- `backend/api/CLAUDE.md §예외 처리 원칙` — message 필드 금지 문구 + rule 문서 링크

### 3. PostToolUse 훅 — `.claude/hooks/check-errorcode-contract.sh`

- 매처: PostToolUse + Write | Edit
- 대상: 파일명이 `*ErrorCode.java`이거나 `implements ErrorCode` 포함 enum
- 검사: `private final String message`, `String getMessage()` grep → 위반 시 exit 2

### 4. 기존 위반 정리

- `UserErrorCode.java`, `AuthErrorCode.java`: `message` 필드·생성자 인자 제거, `httpStatus`만 유지

## 테스트 전략

- `./gradlew.bat :api:test` 실행 → `RestApiAdviceTest` 포함 회귀 없음
- 훅 차단 검증: 위반 내용 저장 시도 → exit 2 확인
- 훅 정상 통과 검증: 규약 준수 파일 편집 → 통과 확인

## 제외 사항

- ArchUnit/Contract 단위 테스트 도입 없음 (2중 방어 채택)
- IDE·터미널 직접 편집 차단 없음

## 예상 산출물

- [태스크](../../../tasks/20260421/errorcode_계약_강화/TASK1.md)
