---
status: approved
created: 2026-05-08
updated: 2026-05-08
---
# DTO @Schema(implementation) 마이그레이션 — 도메인 분석

## 작업 배경

직전 사이클 `swagger_schema_implementation` (커밋 `1b7b078`, 2026-05-07) 이 `backend/.claude/rules/api-patterns.md` 에 **DTO `@Schema(implementation = X.class)` 명시 의무 룰** 을 신설했다. 당시 ANALYZE1 §"PLAN 으로 전달할 결정 사항" 5항·PLAN1 §"본 사이클 범위 외"·TASK1 §"후속 사이클" 에서 코드 마이그레이션을 명시적으로 분리했다.

본 사이클은 그 인계를 받아 **위반 18건 (9파일 / 5패키지) 을 일괄 정정** 한다.

- 직전 PLAN1 §"본 사이클 범위 외" 추정치 24건 → grep 실측 18건 (정정 기록)
- 변경 패턴: 어노테이션 추가/치환 1줄 단위. 도메인 모델·DB 스키마·API 시그니처 변경 0건.

### 위반 목록 (실측 18건)

| 분류 | 파일 | 위치 | 필드 | 타입 |
|---|---|---|---|---|
| Enum | `auth/dto/TokenResponseDto.java` | L30 | `role` | `UserRole` |
| Enum | `auth/dto/LoginResponseDto.java` | L41 | `role` | `UserRole` |
| Enum | `user/dto/UserDto.java` | L28, L31 | `userRole`, `useYn` | `UserRole`, `YnType` |
| Enum | `user/dto/UserUpsertDto.java` | L32 | `userRole` | `UserRole` (`example="ADMIN"` 동시 제거) |
| Enum | `pump/dto/PumpStateDto.java` | L44 | `oprtngYn` | `YnType` |
| Enum | `pump/dto/PumpControlRequestDto.java` | L34 | `ctrlDiv` | `PumpControlDivision` |
| Enum | `pump/dto/PumpControlResultDto.java` | L29 | `ctrlRslt` | `PumpControlResult` |
| Enum (×5) | `pump/dto/PumpDashboardDto.java` | L40, L43, L103, L106, L109 | 외부 enum 2건 + inner class enum 3건 | `AiDrvnModeType`·`AiSystemModeCode`·`PumpControlDivision`·`PumpControlResult` |
| Enum | `ai/dto/AiModeDto.java` | L28, L31 | `aiDrvnMod`, `aiModeCd` | `AiDrvnModeType`, `AiSystemModeCode` |
| Enum | `ai/dto/AiModeUpsertDto.java` | L32 | `aiDrvnMod` | `AiDrvnModeType` |
| List | `auth/dto/LoginResponseDto.java` | L44 | `menus` | `List<MenuTreeDto>` |
| List | `menu/dto/MenuTreeDto.java` | L40 | `children` | `List<MenuTreeDto>` (자기참조 재귀) |
| List | `pump/dto/PumpDashboardDto.java` | L46 | `pumps` | `List<PumpStateDto>` |

## 회의록 (5인 회의)

### 안건 1 — 규모 분류 및 TASK 구성 타당성
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: Medium + 단일 TASK1.md (미분할) 타당. 변경 패턴이 어노테이션 추가 1줄 단위로 고정되며 판단 분기 없음. Phase 6 / 체크박스 약 33개 — 분할 임계(Phase 10 / 체크박스 60) 명확히 하회. 블로커 없음.
- **결론**: Medium 규모, 단일 TASK1.md, 도메인 패키지별 Phase 6단 분해 확정.

### 안건 2 — 위반 목록 인계 명문화
- 호출 에이전트: (오케스트레이터 자체 정리)
- Round 1 답변 요약:
  - 직전 PLAN1 §"본 사이클 범위 외" 추정치 24건 → grep 실측 18건 (Enum 15건 + List 3건 / 9파일 / 5패키지).
- **결론**: 18건 실측치를 본 ANALYZE §작업 배경 위반 목록 표로 명문화 완료.

### 안건 3 — REVIEW 단계 자동 점검 항목 6 (Swagger/OpenAPI) 적용
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 정정 완료 후 grep 2종으로 잔존 위반 0건 검증 가능. ① `grep -rn "@Schema" api/src/main/java/com/mo/swtp --include="*.java" | grep -v "implementation"` 에서 enum 필드 행 0건. ② `grep -rn "List<" api/src/main/java/com/mo/swtp --include="*.java" | grep -v "@ArraySchema"` 에서 사용자 정의 List 필드 행 0건. 런타임: `/v3/api-docs` 에서 enum 6종이 `enum` 키 포함 스키마로 노출 확인. TASK 체크박스 검증 명령 사전 명기 권고(낮음). 블로커 없음.
- **결론**: 본 사이클이 직전 신설 룰의 첫 적용 사례임을 확인. REVIEW 항목 6 점검 방법 확정. TASK Phase 마지막 체크박스에 grep 검증 명기 의무.

### 안건 4 — MenuTreeDto.children 자기참조 재귀 검증 절차
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `@ArraySchema(schema = @Schema(implementation = MenuTreeDto.class))` 추가 안전. SpringDoc 3.x는 이미 처리 중인 타입을 `$ref: '#/components/schemas/MenuTreeDto'`로 즉시 치환해 무한 루프 없이 종료. 별도 설정(`springdoc.override-with-generic-response` 등) 불필요. Phase 6 검증 시 `curl -s http://localhost:8080/v3/api-docs | jq '.components.schemas.MenuTreeDto.properties.children'` 결과가 `{"type":"array","items":{"$ref":"#/components/schemas/MenuTreeDto"}}` 이면 정상. 블로커 없음.
- **결론**: 자기참조 재귀 코너 케이스 안전 확인. Phase 6 검증 명령 1건 추가 (TASK에 명기 의무).

`wtp-domain-expert`: 도메인 4영역 전 비해당 (런타임 도메인 로직과 교점 없음). 블로커 없음.

`wtp-dba-reviewer`: DB/쿼리/마이그레이션 영향 없음. 신규 데이터 도메인 등록 불필요. 블로커 없음.

`wtp-glossary-manager`: 신규 단어·데이터 도메인·비즈니스 약어·표준 용어 등록 불필요 (0건). 블로커 없음.

## 표준 사전 카탈로그

> 신규 항목이 없는 층위는 "없음" 으로 표기.

### 신규 표준 단어
없음 — @Schema 어노테이션 추가는 DB 컬럼명 재료 단어 도입 없음 (wtp-glossary-manager Round 1)

### 신규 표준 데이터 도메인
없음 — 신규 SQL 타입·Java 타입 도입 없음. UserRole 등 enum 6종은 기존 `DOM_CODE_20`·`DOM_YN` 에 이미 매핑 (wtp-dba-reviewer·wtp-glossary-manager Round 1)

### 신규 표준 용어
없음 — 신규 DB 컬럼 도입 없음 (wtp-glossary-manager Round 1)

## 신규 엔티티/DB 컬럼

없음. 본 사이클은 DTO Swagger 어노테이션 정정만 수행한다.

## 기존 사전·패턴과의 충돌

없음.

## PLAN 으로 전달할 결정 사항

1. **규모**: Medium / 단일 TASK1.md (분할 미적용)
2. **TASK Phase 구성** (6단 / 체크박스 약 33개):
   - Phase 1 (`auth`): TokenResponseDto.role, LoginResponseDto.role, LoginResponseDto.menus (`@ArraySchema`)
   - Phase 2 (`user`): UserDto.userRole, UserDto.useYn, UserUpsertDto.userRole + `example="ADMIN"` 동시 제거
   - Phase 3 (`pump`): PumpStateDto.oprtngYn, PumpControlRequestDto.ctrlDiv, PumpControlResultDto.ctrlRslt, PumpDashboardDto (외부 enum 2 + inner class enum 3 + pumps `@ArraySchema`)
   - Phase 4 (`ai`): AiModeDto.aiDrvnMod·aiModeCd, AiModeUpsertDto.aiDrvnMod
   - Phase 5 (`menu`): MenuTreeDto.children 자기참조 `@ArraySchema`
   - Phase 6 (검증): grep 2종 + `/v3/api-docs` curl 검증 + `./gradlew.bat build`
3. **Phase 마지막 체크박스**: `./gradlew.bat :api:compileJava` 검증 (Phase 1~5) + REVIEW grep 검증 명령 명기 의무 (안건 3 권고)
4. **`example="ADMIN"` 제거**: UserUpsertDto.userRole 에서 동시 제거. `implementation = UserRole.class` 으로 ADMIN·USER 자동 노출 — frontend 식별 손실 없음.
5. **MenuTreeDto.children 자기참조**: `@ArraySchema(schema = @Schema(description = "...", implementation = MenuTreeDto.class))`. Phase 6 에서 `$ref` 단일 참조 확인.
6. **패턴 적용 기준** (`api-patterns.md §DTO @Schema(implementation) 명시 패턴`):
   - enum 필드: `@Schema(description = "...", implementation = X.class)`
   - `List<E>` 필드: `@ArraySchema(schema = @Schema(description = "...", implementation = E.class))`
   - YnType: `description` + `implementation` 만 명시 (`allowableValues`/`@Pattern`/`example` 중복 금지)

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| IMPL 단계에서 어노테이션 추가 외 enum 클래스 값 변경·Service 로직 수정이 없다 | 가정 | wtp-domain-expert 경고 — 이 전제가 깨지면 AI 운전 모드 4영역 즉시 재판정 |
| SpringDoc 3.0.2 의 순환 $ref 처리가 MenuTreeDto.children 에 대해 무한 루프 없이 종료된다 | 가정 | wtp-backend-engineer Round 1 확인. Phase 6 런타임 검증으로 최종 확인 |
| UserUpsertDto.userRole 의 `example="ADMIN"` 제거 후 frontend 가 enum 허용값을 식별 가능하다 | 가정 | `implementation = UserRole.class` 자동 노출 → frontend 손실 없음 확인 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 |
|---------|------------|
| 18건 정정 후 enum @Schema implementation 누락 0건 | grep -rn "@Schema" api/src/main/java/com/mo/swtp --include="*.java" | grep -v "implementation" → enum 필드 행 0건 |
| List @ArraySchema 누락 0건 | grep -rn "List<" api/src/main/java/com/mo/swtp --include="*.java" | grep -v "@ArraySchema" → 사용자 정의 List 필드 행 0건 |
| 컴파일 PASS | ./gradlew.bat :api:compileJava BUILD SUCCESSFUL |
| 전체 빌드 PASS | ./gradlew.bat build BUILD SUCCESSFUL |
| SpringDoc 직렬화 정상 | curl /v3/api-docs 응답에서 UserRole 등 enum 6종 스키마 확인, MenuTreeDto.children.items 가 $ref 단일 참조 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | @Schema는 Swagger 메타데이터 레이어. 알람 임계값·전이 조건·복귀 조건·alarm_h 기록 경로 변경 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | InterlockValidator 런타임 호출 경로와 교점 없음. 기동 차단·복구 후 재검사 의무 영향 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | AiDrvnModeType·AiSystemModeCode enum 을 @Schema 노출에만 사용. ai_drvn_mod·ai_mode_cd 컬럼 값 변경 주체(사용자 API·스케줄러) 및 강제 전환 로직 미변경. 단, IMPL에서 enum 클래스 내부 변경 수반 시 즉시 재판정 의무 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | ai_drvn_mod_h.transition_reason·pump_ctrl_h 기록 경로는 Service/Repository 런타임. 어노테이션 추가와 교점 없음 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 사이클은 신규 표준 단어·표준 데이터 도메인·비즈니스 도메인 약어·표준 용어 등록이 필요하지 않으며, `api-patterns.md` 룰 본문도 변경 없다.

- [x] (등록 대상 없음 — 신규 표준 단어 0건 / 표준 데이터 도메인 0건 / 비즈니스 도메인 약어 0건 / 표준 용어 0건)

## 산출물
- [계획안](../../../plan/20260508/swagger_schema_migration/PLAN1.md)
