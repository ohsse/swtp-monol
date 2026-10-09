---
status: approved
created: 2026-05-08
updated: 2026-05-08
---
# DTO @Schema(implementation) 마이그레이션

## 목적

직전 사이클 `swagger_schema_implementation` (커밋 `1b7b078`, 2026-05-07) 이 신설한 `backend/.claude/rules/api-patterns.md §DTO @Schema(implementation) 명시 패턴` 의 **첫 적용 사례** 로, DTO 위반 **19건** (Enum 16 + List 3 / 11 파일 / 5 패키지) 을 일괄 정정하여 SpringDoc/Swagger 자동 추출기가 enum 허용값·중첩 DTO 스키마·자기참조 재귀를 frontend SPEC 명세 (`/dev:spec`) 에 정확히 노출하게 한다.

## 배경

- 직전 PLAN1 §"본 사이클 범위 외" 추정치 24건 → ANALYZE1 grep 실측 18건 → PLAN 정독 실측 19건 (1건 차이는 `PumpDashboardDto` inner class enum 카운트 방식 — ANALYZE 본문 "Enum 15건" vs 표 16건). 본 PLAN 은 11파일 정독 결과 19건을 SSOT 로 사용한다.
- 전제 ANALYZE: [`docs/analyze/20260508/swagger_schema_migration/ANALYZE1.md`](../../../analyze/20260508/swagger_schema_migration/ANALYZE1.md) (status: approved). 5인 회의 4 안건 합의 — Medium / 단일 TASK / Phase 6단 / 도메인 4영역 전 비해당 / DB·신규 단어 0건.
- 변경 패턴: 어노테이션 추가/치환 1줄 단위. 도메인 모델·DB 스키마·API 시그니처·Service 로직·Controller 시그니처 변경 0건.

## 범위

- `swtp/backend/api` 모듈의 5개 패키지 11개 DTO 파일에 `@Schema(implementation = X.class)` / `@ArraySchema(schema = @Schema(implementation = E.class))` 어노테이션 추가
- `UserDto.userRole`·`UserUpsertDto.userRole` 의 `example="ADMIN"` 동시 제거 (가정 1·4 — implementation 자동 노출로 식별 손실 없음)
- `LoginResponseDto`·`PumpDashboardDto`·`MenuTreeDto` 3 파일에 `io.swagger.v3.oas.annotations.media.ArraySchema` import 추가
- `wtp-domain-expert`·`wtp-dba-reviewer` 검토 게이트 생략 (도메인 모델·DB 변경 0건 — ANALYZE 결론 적용)

## 구현 방향

### 적용 패턴

`api-patterns.md §DTO @Schema(implementation) 명시 패턴` 의 "올바른 예" 코드 블록을 인용 근거로 사용한다.

| 대상 | 패턴 |
|---|---|
| Enum 필드 | `@Schema(description = "...", implementation = X.class)` |
| `List<E>` 필드 (E = 사용자 정의) | `@ArraySchema(schema = @Schema(description = "...", implementation = E.class))` + `io.swagger.v3.oas.annotations.media.ArraySchema` import |
| `YnType` 필드 | `description` + `implementation = YnType.class` 만 (`allowableValues` / `@Pattern` / `example` 중복 금지 — `api-patterns.md §YnType 패턴과의 양립`) |

### TASK Phase 6단

| Phase | 패키지 | 건수 | 핵심 |
|---|---|---|---|
| 1 | `auth` | 3 | `TokenResponseDto.role`, `LoginResponseDto.role` + `LoginResponseDto.menus` (`@ArraySchema` + import) |
| 2 | `user` | 3 | `UserDto.userRole`/`useYn` + `example="ADMIN"` 제거, `UserUpsertDto.userRole` + `example="ADMIN"` 제거 |
| 3 | `pump` | 8 | `PumpStateDto.oprtngYn`, `PumpControlRequestDto.ctrlDiv`, `PumpControlResultDto.ctrlRslt` 각 1건 + `PumpDashboardDto` 외부 enum 2 + `pumps` `List` + inner ControlHistoryItem enum 3 |
| 4 | `ai` | 3 | `AiModeDto.aiDrvnMod`/`aiModeCd`, `AiModeUpsertDto.aiDrvnMod` |
| 5 | `menu` | 1 | `MenuTreeDto.children` 자기참조 (`@ArraySchema` + import) |
| 6 | 검증 | — | grep 2종 + `./gradlew.bat :api:compileJava` + `./gradlew.bat build` + `/v3/api-docs` 런타임 검증 |

각 Phase 마지막 체크박스에 `./gradlew.bat :api:compileJava` BUILD SUCCESSFUL 검증 명기 의무 (ANALYZE 안건 3 권고 반영).

### Critical Files (11)

| # | 파일 | 변경 라인 | 변경 유형 |
|---|---|---|---|
| 1 | `api/src/main/java/com/mo/swtp/auth/dto/TokenResponseDto.java` | L30 | Enum (`UserRole`) |
| 2 | `api/src/main/java/com/mo/swtp/auth/dto/LoginResponseDto.java` | L41, L44 | Enum (`UserRole`) + List (`MenuTreeDto`) + import |
| 3 | `api/src/main/java/com/mo/swtp/user/dto/UserDto.java` | L28, L31 | Enum (`UserRole` + example 제거, `YnType`) |
| 4 | `api/src/main/java/com/mo/swtp/user/dto/UserUpsertDto.java` | L32 | Enum (`UserRole` + example 제거) |
| 5 | `api/src/main/java/com/mo/swtp/pump/dto/PumpStateDto.java` | L44 | Enum (`YnType`) |
| 6 | `api/src/main/java/com/mo/swtp/pump/dto/PumpControlRequestDto.java` | L34 | Enum (`PumpControlDivision`) |
| 7 | `api/src/main/java/com/mo/swtp/pump/dto/PumpControlResultDto.java` | L29 | Enum (`PumpControlResult`) |
| 8 | `api/src/main/java/com/mo/swtp/pump/dto/PumpDashboardDto.java` | L40, L43, L46, L103, L106, L109 | Enum 2 외부 + List (`PumpStateDto`) + inner enum 3 + import |
| 9 | `api/src/main/java/com/mo/swtp/ai/dto/AiModeDto.java` | L28, L31 | Enum (`AiDrvnModeType`, `AiSystemModeCode`) |
| 10 | `api/src/main/java/com/mo/swtp/ai/dto/AiModeUpsertDto.java` | L32 | Enum (`AiDrvnModeType`) |
| 11 | `api/src/main/java/com/mo/swtp/menu/dto/MenuTreeDto.java` | L40 | List (`MenuTreeDto`) 자기참조 + import |

### 재사용 (Reuse)

본 사이클은 어노테이션 추가만이라 신규 함수·유틸리티 도입 없음. 패턴은 `api-patterns.md §DTO @Schema(implementation) 명시 패턴` 의 "올바른 예" 코드 블록 그대로 적용한다.

## 성공 기준 (검증 가능 형태)

ROOT [`coding-discipline.md §4.2`](../../../../../.claude/rules/coding-discipline.md) 적용. 모호 표현 금지, 각 기준에 검증 명령·테스트·조회 명시.

| # | 기준 | 검증 명령 |
|---|---|---|
| 1 | enum @Schema implementation 누락 0건 | grep -rn "@Schema" api/src/main/java/com/mo/swtp --include="*.java" \| grep -v "implementation" 결과에서 사용자 정의 enum 필드 행 0건 (`String`·`LocalDateTime` 등 표준 타입 행은 통과) |
| 2 | List @ArraySchema 누락 0건 | grep -rn "List<" api/src/main/java/com/mo/swtp --include="*.java" \| grep -v "@ArraySchema" 결과에서 사용자 정의 List 필드 행 0건 (`List<String>` 등 기본 타입 List 통과) |
| 3 | api 모듈 컴파일 PASS | ./gradlew.bat :api:compileJava BUILD SUCCESSFUL |
| 4 | 전체 빌드 PASS | ./gradlew.bat build BUILD SUCCESSFUL |
| 5 | SpringDoc enum 6종 스키마 노출 | ./gradlew.bat :api:bootRun 후 curl -s http://localhost:8080/v3/api-docs 응답에 UserRole / YnType / PumpControlDivision / PumpControlResult / AiDrvnModeType / AiSystemModeCode 스키마가 enum 키 포함으로 노출 |
| 6 | MenuTreeDto.children 자기참조 $ref | curl -s http://localhost:8080/v3/api-docs \| jq .components.schemas.MenuTreeDto.properties.children 결과가 type=array + items.$ref=#/components/schemas/MenuTreeDto 단일 참조 |

## 가정 및 미해결 질문

ROOT [`coding-discipline.md §1`](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE 가정 3건 + PLAN 단계 신규 1건.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `UserDto.userRole` 의 `example="ADMIN"` 도 제거 (ANALYZE 결정 4 는 `UserUpsertDto` 만 명시) | 미해결 → 결정 | **제거 적용** — `implementation = UserRole.class` 자동 노출, `UserUpsertDto` 와 일관 + `api-patterns.md §위반 예` 정합 |
| IMPL 단계에서 어노테이션 추가 외 enum 클래스 값 변경·Service 로직 수정 0건 | 가정 | **유지** — ROOT [`coding-discipline.md §3`](../../../../../.claude/rules/coding-discipline.md) 정밀한 수정으로 강제. 전제 깨지면 AI 운전 모드 4영역 즉시 재판정 + Fix Cycle 진입 |
| SpringDoc 3.0.2 의 순환 `$ref` 처리가 `MenuTreeDto.children` 에 대해 무한 루프 없이 종료 | 가정 | **유지** — Phase 6 §성공 기준 6 으로 런타임 검증 |
| `UserUpsertDto.userRole` `example="ADMIN"` 제거 후 frontend 가 enum 허용값 식별 가능 | 가정 | **유지** — `implementation = UserRole.class` 자동 노출로 식별 손실 없음 |

## 도메인 모델

없음. 본 사이클은 DTO Swagger 어노테이션 정정만 수행한다 — 신규 엔티티·DTO 클래스·컬럼·필드 도입 0건. 따라서 `wtp-domain-expert` 검토 게이트 생략.

## DB 설계 변경

없음. DB 스키마·테이블·컬럼·인덱스·파티션·마이그레이션 영향 0건. 따라서 `wtp-dba-reviewer` 검토 게이트 생략.

## 제외 사항

- 어노테이션 추가 외 enum 클래스 값 / Service 로직 / DTO 필드 / Controller 시그니처 변경 — 본 사이클 범위 외
- `Map<K, V>` value 사용자 정의 케이스 — `api-patterns.md` 권고 의무 미적용, 향후 적용 사례 누적 시 별도 ANALYZE
- ANALYZE 위반 표 외 파일 (`LoginRequestDto`·`RefreshRequestDto`·`MenuUpsertDto`·`MenuRoleUpsertDto`·`AiPredictionRequestDto`·`AiPredictionResponseDto`) — 사용자 정의 enum / 참조형 DTO / `List<E>` 필드 0건 추정, Phase 6 grep 으로 재확인만
- frontend SPEC 갱신 — `/dev:spec swagger_schema_migration` 별도 단계 (templates.md §SPEC 라이프사이클)
- `api-patterns.md` 룰 본문 변경 — 직전 사이클 (`1b7b078`) 에서 완료, 본 사이클은 룰 적용만

## 예상 산출물

- [태스크](../../../tasks/20260508/swagger_schema_migration/TASK1.md)
