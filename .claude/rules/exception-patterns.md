# 예외·에러 코드 패턴

`ErrorCode` 인터페이스 구현 규칙, `RestApiException` 던지기 패턴, 응답 계약을 기술한다.

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`process/hooks-guide.md`](process/hooks-guide.md) | `check-errorcode-contract.sh` 자동 차단 훅 상세 |
| [`api-patterns.md`](api-patterns.md) | Swagger·Controller 계층 응답 계약 소비처 |
| [`naming.md`](naming.md) | `{도메인명}ErrorCode` 클래스 네이밍 규칙 |

---

## 1. 응답 계약

실패 응답은 에러 **코드**만 전달하고, 사용자 표기 문자열은 프론트엔드가 코드를 기반으로 명세에서 매핑한다.

| 계층 | 역할 |
|------|------|
| `ErrorCode.name()` | 응답 body의 `code` 값으로 직렬화 |
| `CommonResponseDto<Void>` | 실패 응답 구조 — `code` + `data(null)` |
| `RestApiAdvice` | `errorCode.name()`만 읽어 응답 빌드 |
| 프론트엔드 명세 | `code` 값을 키로 화면 표기 문자열 결정 |

> **`CommonResponseDto`에는 `message` 필드가 없다.** `RestApiAdvice`는 `errorCode.name()`만 직렬화하므로, 백엔드 enum의 String 필드는 **어디서도 읽히지 않는 데드 코드**가 된다.

---

## 2. ErrorCode 구현 enum 필드 규약

### 허용 필드

```java
private final int httpStatus;   // HTTP 상태 코드 — 유일하게 허용
```

### 금지 필드 (이유 포함)

| 필드 | 이유 |
|------|------|
| `String message` | `RestApiAdvice`와 `CommonResponseDto`가 읽지 않는 데드 코드. 프론트엔드 명세와 이중 소스 발생. |
| `String description` | 동일 |
| `String defaultMessage` | 동일 |
| `String getMessage()` 오버라이드 | `ErrorCode` 인터페이스에 정의되지 않은 계약. `@Getter`를 통한 간접 노출 포함. |

---

## 3. 올바른 예 / 위반 예

### 올바른 예

```java
@Getter
@RequiredArgsConstructor
public enum PumpErrorCode implements ErrorCode {

    PUMP_NOT_FOUND(404),
    DUPLICATE_PUMP_ID(409),
    INVALID_PUMP_STATE(400);

    private final int httpStatus;
}
```

### 위반 예 (금지)

```java
// ❌ message 필드는 RestApiAdvice/CommonResponseDto가 절대 읽지 않는다.
@Getter
@RequiredArgsConstructor
public enum PumpErrorCode implements ErrorCode {

    PUMP_NOT_FOUND(404, "존재하지 않는 펌프입니다."),   // ← 위반
    DUPLICATE_PUMP_ID(409, "이미 등록된 펌프 ID입니다."); // ← 위반

    private final int httpStatus;
    private final String message;   // ← 금지
}
```

---

## 4. RestApiException 던지기 패턴

```java
// 서비스에서 비즈니스 예외 발생
throw new RestApiException(PumpErrorCode.PUMP_NOT_FOUND);
```

`RestApiException`은 `super(errorCode.name())`으로 예외 메시지를 설정한다.
응답 바디는 `RestApiAdvice`가 `CommonResponseDto<Void>`로 래핑한다.

---

## 5. 도메인별 ErrorCode enum 네이밍

네이밍 컨벤션은 `naming.md` §Java 클래스 네이밍 참조.

```
{도메인명}ErrorCode    예: UserErrorCode, PumpErrorCode, AlarmErrorCode
```

도메인별로 하나의 enum에 모으되, 규모가 커지면 기능군으로 분리한다.

---

## 6. 자동 차단 (Claude Code 훅)

`.claude/hooks/check-errorcode-contract.sh`가 PostToolUse(Write/Edit) 시점에 동작한다.
`*ErrorCode.java` 또는 `implements ErrorCode` 파일에서 `String` 타입 필드가 발견되면 `exit 2`로 저장을 차단한다.
