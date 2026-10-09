---
status: approved
created: 2026-05-07
updated: 2026-05-07
---
# `@Schema(implementation)` 명시 의무화 — 구현 계획

## 목적

DTO 필드 타입이 사용자 정의 클래스 (enum / 참조형 DTO / `List<E>`·`Set<E>` element) 인 경우 `@Schema(... , implementation = X.class)` 명시를 backend 룰로 의무화한다. SpringDoc/Swagger 자동 추출기가 frontend SPEC 명세 (`/dev:spec` 단계) 에 enum 허용값·중첩 DTO 스키마를 정확히 노출하도록 한다.

## 배경

- [도메인 분석](../../../analyze/20260507/swagger_schema_implementation/ANALYZE1.md) — 5인 회의 Round 1 통과 (블로커 0건)
- [Plan Mode 산출물](~/.claude/plans/schema-implementation-schema-twinkling-swing.md) — ExitPlanMode 승인됨
- 사용자 결정 (Plan Phase 3): 적용 범위 = **표준 권고**, 마이그레이션 처리 = **룰만 신설**

**현 상태**:
- backend `api`/`common` main 트리 전체에 `@Schema(implementation = ...)` 사용 0건
- 위반 추정 필드 약 24건 (enum 17건 + 중첩 DTO/List<참조형> 7건+) — 본 사이클 범위 외 (후속 사이클 분리)

## 범위

### 본 사이클 범위 (3개 파일 수정)

1. **`backend/.claude/rules/api-patterns.md`**
   - L64 직후 (§DTO 패턴 끝, §Swagger/OpenAPI 패턴 앞) 신규 절 `## DTO @Schema(implementation) 명시 패턴` 삽입
   - 본문 = 적용 대상 표 + 적용 제외 표 + 올바른 예 (5종) + 위반 예 + `YnType` 양립 관계 명시
   - 본문 어휘 = "enum 타입" 표기 사용 (5인 회의 §안건 7 결정)

2. **`backend/.claude/agents/wtp-backend-engineer.md`**
   - L33 항목 6 (`Swagger / OpenAPI`) 의 인용 근거 확장 (5인 회의 §안건 3 결정 — 항목 12 신설 아님)
   - 변경 전: `[`api-patterns.md §Swagger/OpenAPI 패턴`](../rules/api-patterns.md)`
   - 변경 후: `[`api-patterns.md §Swagger/OpenAPI 패턴 + §DTO @Schema(implementation) 명시 패턴`](../rules/api-patterns.md)`

3. **`backend/.claude/rules/entity-patterns.md`**
   - §여부(Y/N) 필드 패턴 끝에 양방향 교차 참조 1줄 추가 (5인 회의 §안건 1 결정 — 권고 등급 → 필수 격상)
   - 추가 문구: "DTO 의 `*Yn` 필드는 `@Schema(... , implementation = YnType.class)` 명시 — [`api-patterns.md §DTO @Schema(implementation) 명시 패턴`](api-patterns.md) 참조"

### 본 사이클 범위 외 (후속 사이클)

- 24건 위반 필드 일괄 수정 (enum 17건 + 참조형 7건+) — 별도 `/dev` 사이클 (`swagger_schema_migration` 가칭) 로 분리

### 자동 차단 훅 도입 보류

- ROOT `coding-discipline.md §7.2` 정합 — REVIEW 자동 점검 (`wtp-backend-engineer` 항목 6 확장) 만으로 강제. 도입 시 별도 ANALYZE 사이클

## 구현 방향

### 변경 파일 1: `backend/.claude/rules/api-patterns.md` 신규 절 본문

L64 (`Swagger 를 이용하여 API 명세를 남긴다 ...`) 와 L66 (`## Swagger/OpenAPI 패턴`) 사이에 다음 본문을 삽입한다:

```markdown
## DTO @Schema(implementation) 명시 패턴

DTO 필드 타입이 **사용자 정의 클래스** (참조형 또는 enum 타입) 인 경우 `@Schema(... , implementation = X.class)` 를 반드시 명시한다. SpringDoc/Swagger 자동 추출기가 enum 허용값·중첩 DTO 스키마를 frontend SPEC 명세에 정확히 노출하기 위함이다.

### 적용 대상

| 필드 타입 | implementation 명시 |
|---------|------------------|
| 사용자 정의 enum (`UserRole`·`YnType`·`AiDrvnMode`·`TagMeasurementType` 등) | **필수** |
| 사용자 정의 참조형 DTO (중첩 DTO·`UserAddressDto` 등) | **필수** |
| `List<E>` · `Set<E>` 의 element 가 사용자 정의 클래스 | **필수** (`@ArraySchema(schema = @Schema(implementation = E.class))`) |
| `Map<K, V>` 의 value 가 사용자 정의 클래스 | **권고** (의무 미적용 — 사용 빈도 낮음, 향후 적용 사례 누적 시 의무 격상 검토) |

### 적용 제외 대상

| 필드 타입 | 사유 |
|---------|------|
| `String` · `int`/`Integer` · `long`/`Long` · `boolean`/`Boolean` 등 원시·래퍼 | Swagger 가 자동 인식 |
| `LocalDateTime` · `LocalDate` · `BigDecimal` · `UUID` | JSR-310 / 표준 라이브러리, Swagger 자동 인식 |
| `List<String>` · `Map<String, String>` 등 element 가 기본 타입 | 동일 사유 |

### 올바른 예

```java
// 사용자 정의 enum
@Schema(description = "권한 역할", implementation = UserRole.class)
private UserRole userRole;

// YnType (Y/N enum)
@Schema(description = "사용 여부", implementation = YnType.class)
private YnType useYn;

// 도메인 안전 enum (AI 운전 모드)
@Schema(description = "AI 운전 모드", implementation = AiDrvnMode.class)
private AiDrvnMode aiDrvnMod;

// 사용자 정의 참조형 DTO
@Schema(description = "주소 정보", implementation = UserAddressDto.class)
private UserAddressDto address;

// List<E> — element 가 사용자 정의 클래스
@ArraySchema(schema = @Schema(description = "권한 목록", implementation = UserRole.class))
private List<UserRole> roles;
```

### 위반 예 (금지)

```java
// ❌ implementation 누락 — frontend SPEC 추출 시 enum 클래스 식별 불가
@Schema(description = "권한 역할", example = "ADMIN")
private UserRole userRole;
```

### `YnType` 패턴과의 양립

`YnType` 의 `@Schema(allowableValues)` · `@Pattern` · `example` 중복 작성 금지 정책 ([`entity-patterns.md §여부(Y/N) 필드 패턴`](entity-patterns.md)) 과 본 패턴은 양립한다. `YnType` 은 본 패턴의 의무 적용 대상이며, `description` 옆에 `implementation = YnType.class` 만 추가한다 — `allowableValues` 는 implementation 으로 자동 노출되므로 별도 명시 불필요. `@Pattern`·`example` 도 기술 금지 (해당 정책 그대로 유지).
```

### 변경 파일 2: `backend/.claude/agents/wtp-backend-engineer.md` 항목 6 인용 근거 확장

L33 의 한 줄을 다음과 같이 수정한다:

- 변경 전 (L33): `6. **Swagger / OpenAPI** — [`api-patterns.md §Swagger/OpenAPI 패턴`](../rules/api-patterns.md)`
- 변경 후 (L33): `6. **Swagger / OpenAPI** — [`api-patterns.md §Swagger/OpenAPI 패턴`·`§DTO @Schema(implementation) 명시 패턴`](../rules/api-patterns.md). DTO 필드 타입이 사용자 정의 클래스 (enum / 참조형 DTO / `List<E>`/`Set<E>` element) 인 경우 `@Schema(... , implementation = X.class)` 명시 여부 점검 — 누락 시 권고 등급 (도메인 안전·보안 미해당)`

### 변경 파일 3: `backend/.claude/rules/entity-patterns.md` 양방향 교차 참조

§여부(Y/N) 필드 패턴 끝의 마지막 bullet 직후에 다음 1줄 bullet 을 추가한다 (현재 마지막 bullet: `- DDL CHECK·DEFAULT·인덱스 운영 정책은 ...`):

```markdown
- DTO 의 `*Yn` 필드는 `@Schema(description=..., implementation = YnType.class)` 명시 — [`api-patterns.md §DTO @Schema(implementation) 명시 패턴`](api-patterns.md) 참조 (양방향 교차 참조).
```

## 성공 기준 (검증 가능 형태)

ROOT [`coding-discipline.md §4.2`](../../../../.claude/rules/coding-discipline.md) 적용. 모든 기준에 검증 명령·grep·빌드 결과 명시.

| 기준 | 검증 명령 / 조회 |
|------|--------------|
| 1. `api-patterns.md` 신규 절 헤더가 §DTO 패턴 (`L50`) 과 §Swagger/OpenAPI 패턴 (구 L66) 사이에 배치 | grep -n "## DTO @Schema(implementation) 명시 패턴" backend/.claude/rules/api-patterns.md → 라인 번호가 `## DTO 패턴` 라인 번호와 `## Swagger/OpenAPI 패턴` 라인 번호 사이임을 확인 |
| 2. 신설 절 본문에 적용 대상 표·적용 제외 표·올바른 예 5종·위반 예·`YnType` 양립 관계 모두 포함 | grep -E "사용자 정의 enum|사용자 정의 참조형 DTO|@ArraySchema|YnType 의 \`@Schema\(allowableValues\)\`|AiDrvnMode" backend/.claude/rules/api-patterns.md → 5건 모두 매칭 PASS |
| 3. 본문 어휘 "enum 타입" 사용 ("열거형" 단독 표기 0건) | grep -c "enum 타입" backend/.claude/rules/api-patterns.md → 1건 이상 + grep -c "열거형" 신규 절 본문 → 0건 (혹은 "enum 타입 (열거형)" 같은 병기 표현만 허용) |
| 4. `wtp-backend-engineer.md` L33 항목 6 인용 근거에 `§DTO @Schema(implementation) 명시 패턴` 포함 | grep "§DTO @Schema(implementation) 명시 패턴" backend/.claude/agents/wtp-backend-engineer.md → 매칭 PASS |
| 5. `entity-patterns.md` §여부(Y/N) 필드 패턴 끝에 양방향 교차 참조 1줄 추가 | grep "DTO 의 \`\*Yn\` 필드는 \`@Schema(description=..., implementation = YnType.class)\` 명시" backend/.claude/rules/entity-patterns.md → 매칭 PASS |
| 6. 빌드 영향 없음 (rule 변경만이므로 컴파일·테스트 영향 0) | ./gradlew.bat build → BUILD SUCCESSFUL 출력 확인 |
| 7. ANALYZE1.md 의 "## 룰 갱신 지시서" 체크박스 3건 모두 [x] 전환 (메타 케이스 — 본 사이클 IMPL 적용 결과) | grep -c "^- \[x\]" backend/docs/analyze/20260507/swagger_schema_implementation/ANALYZE1.md → 3건 이상 (룰 갱신 지시서 절 한정) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 본 사이클은 룰/하네스 변경만 다루며 backend 소스 코드 변경 없음 | 결정 | ANALYZE 단계 사용자 결정 (마이그레이션 처리 = 룰만 신설) 으로 변환됨 |
| 24건 위반 필드는 후속 별도 `/dev` 사이클 (`swagger_schema_migration` 가칭) 로 분리 | 결정 | 동일 — 본 사이클 RESULT/비고 섹션에 위반 목록 표 보고 |
| `Map<K, V>` value 가 사용자 정의 클래스인 경우 권고 등급 (의무 미적용) | 결정 | 룰 본문에 명시. 향후 적용 사례 누적 시 의무 격상 별도 ANALYZE |
| `@ArraySchema(schema = @Schema(implementation = E.class))` 사용법 정합성 | 결정 | SpringDoc 표준 동작 — 5인 회의 Round 1 wtp-backend-engineer 검토 통과 |
| 본 작업은 메타 케이스 — IMPL 단계의 산출물 자체가 룰 변경 | 결정 | ANALYZE 의 "## 룰 갱신 지시서" 체크박스는 본 사이클 IMPL 적용 후 [x] 전환 (일반 사이클의 ANALYZE→PLAN 경계 적용 패턴과 다름) |

## 제외 사항

- backend 소스 (24건 위반 필드) 일괄 마이그레이션 — 후속 사이클 (`swagger_schema_migration`) 분리
- 자동 차단 훅 신설 — 본 시점 보류 (ROOT `coding-discipline.md §7.2` 정합)
- frontend SPEC (`swtp/frontend/docs/api-specs/`) 갱신 — 본 작업은 backend 룰 변경만 다룸. frontend 영향은 후속 마이그레이션 사이클 후 `/dev:spec` 호출 시 자연 반영
- ROOT 어휘 사전 (`swtp/.claude/rules/dict/`) 갱신 — 5인 회의 Round 1 `wtp-glossary-manager` 판정으로 신규 등록·폐기 0건

## 비고

### 24건 위반 필드 보고 (후속 사이클 대상)

본 ANALYZE 단계의 추정치. 정확한 파일·라인 목록은 본 작업 IMPL 완료 후 별도 grep 으로 확정하여 RESULT 또는 후속 사이클 ANALYZE 의 "## 작업 배경" 에 기록.

| 분류 | 추정 건수 | 대표 위반 |
|------|---------|----------|
| 사용자 정의 enum 누락 | 17건 | `UserDto.userRole` (UserRole), `UserDto.useYn` (YnType) |
| 중첩 DTO/`List<참조형>` 누락 | 7건+ | (후속 사이클 ANALYZE 단계에서 정확한 파일·라인 보충) |

### Medium 작업 워크플로우 적용

- ANALYZE 통과 (5인 회의 Round 1, 블로커 0건) ✅
- PLAN 작성 (본 문서) — 사용자 승인 대기
- TASK 작성 (PLAN approved 후 자동 전이)
- IMPL — 3개 파일 수정 + ANALYZE1.md 룰 갱신 지시서 체크박스 [x] 전환
- COMMIT — `chore: DTO @Schema(implementation) 명시 의무화 룰 신설` (한국어)
- RESULT/REVIEW 단계는 Medium 규모이므로 면제 (Large 만 의무)

## 예상 산출물

- [태스크](../../../tasks/20260507/swagger_schema_implementation/TASK1.md) (PLAN approved 후 자동 전이로 작성)
