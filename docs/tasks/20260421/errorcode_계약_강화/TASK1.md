---
status: completed
created: 2026-04-21
updated: 2026-04-21
---
# ErrorCode 계약 강화 — message 필드 재현 위반 차단

## 관련 계획
- [계획안](../../../plan/20260421/errorcode_계약_강화/PLAN1.md)

## Phase

### Phase 1: 규약 문서화

- [x] `.claude/rules/exception-patterns.md` 신설 (응답 계약 요약 + ErrorCode 필드 규약 + 올바른/위반 예시)
- [x] `CLAUDE.md` 규칙 문서 인덱스 표에 `exception-patterns.md` 행 추가
- [x] `api/CLAUDE.md` §예외 처리 원칙에 "message 필드 금지" 문구 + rule 문서 링크 추가
- [x] `.claude/commands/dev/review.md` 체크리스트에 "ErrorCode enum 필드 구성 점검 (httpStatus만 허용)" 한 줄 추가

### Phase 2: PostToolUse 훅

- [x] `.claude/hooks/check-errorcode-contract.sh` 신설 (대상 조건 / message 필드 grep / exit 2 차단)
- [x] `.claude/settings.local.json`에 PostToolUse(Write | Edit) 매처 + 훅 명령 추가

### Phase 3: 기존 위반 정리

- [x] `api/src/main/java/com/mo/smartwtp/user/exception/UserErrorCode.java` — message 필드·생성자 인자 제거, httpStatus만 유지
- [x] `api/src/main/java/com/mo/smartwtp/auth/exception/AuthErrorCode.java` — 동일

### Phase 4: 빌드 검증

- [x] `./gradlew.bat :api:test` 실행 성공 확인

## 산출물
- [결과](../../../results/20260421/errorcode_계약_강화/RESULT1.md)
