---
status: completed
created: 2026-05-08
updated: 2026-05-08
---
# DTO @Schema(implementation) 마이그레이션

## 관련 계획
- [계획안](../../../plan/20260508/swagger_schema_migration/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md` §4.1](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. 검증 영역에 백틱 사용 금지 (`check-task-unstage.sh` 훅 파싱 충돌 방지).

### Phase 1: auth 패키지 (3건 + import 1건)

- [x] `api/src/main/java/com/mo/swtp/auth/dto/TokenResponseDto.java` L30 `UserRole role` 필드의 @Schema 에 implementation = UserRole.class 추가 → 검증: grep "implementation = UserRole.class" 매칭, 해당 라인에 description 동시 존재 확인
- [x] `api/src/main/java/com/mo/swtp/auth/dto/LoginResponseDto.java` import 추가 (io.swagger.v3.oas.annotations.media.ArraySchema) → 검증: grep "import io.swagger.v3.oas.annotations.media.ArraySchema" 1줄 매칭
- [x] `api/src/main/java/com/mo/swtp/auth/dto/LoginResponseDto.java` L41 `UserRole role` 필드의 @Schema 에 implementation = UserRole.class 추가 → 검증: grep "implementation = UserRole.class" 해당 라인 매칭
- [x] `api/src/main/java/com/mo/swtp/auth/dto/LoginResponseDto.java` L44 `List<MenuTreeDto> menus` 필드를 @ArraySchema(schema = @Schema(description=..., implementation = MenuTreeDto.class)) 로 치환 → 검증: grep "@ArraySchema" + "implementation = MenuTreeDto.class" 동시 매칭
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력

### Phase 2: user 패키지 (3건)

- [x] `api/src/main/java/com/mo/swtp/user/dto/UserDto.java` L28 `UserRole userRole` 필드의 @Schema 에 implementation = UserRole.class 추가 + example="ADMIN" 제거 → 검증: grep "implementation = UserRole.class" 매칭, grep "example = \"ADMIN\"" UserDto.java 파일 내 0건
- [x] `api/src/main/java/com/mo/swtp/user/dto/UserDto.java` L31 `YnType useYn` 필드의 @Schema 에 implementation = YnType.class 추가 → 검증: grep "implementation = YnType.class" useYn 라인 매칭
- [x] `api/src/main/java/com/mo/swtp/user/dto/UserUpsertDto.java` L32 `UserRole userRole` 필드의 @Schema 에 implementation = UserRole.class 추가 + example="ADMIN" 제거 → 검증: grep "implementation = UserRole.class" 매칭, grep "example = \"ADMIN\"" UserUpsertDto.java 파일 내 0건
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력

### Phase 3: pump 패키지 (8건 + import 1건)

- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpStateDto.java` L44 `YnType oprtngYn` 필드의 @Schema 에 implementation = YnType.class 추가 → 검증: grep "implementation = YnType.class" oprtngYn 라인 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpControlRequestDto.java` L34 `PumpControlDivision ctrlDiv` 필드의 @Schema 에 implementation = PumpControlDivision.class 추가 → 검증: grep "implementation = PumpControlDivision.class" 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpControlResultDto.java` L29 `PumpControlResult ctrlRslt` 필드의 @Schema 에 implementation = PumpControlResult.class 추가 → 검증: grep "implementation = PumpControlResult.class" 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpDashboardDto.java` import 추가 (io.swagger.v3.oas.annotations.media.ArraySchema) → 검증: grep "import io.swagger.v3.oas.annotations.media.ArraySchema" 1줄 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpDashboardDto.java` L40 외부 `AiDrvnModeType aiDrvnMod` 필드의 @Schema 에 implementation = AiDrvnModeType.class 추가 → 검증: 외부 클래스 본문 라인 grep "implementation = AiDrvnModeType.class" 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpDashboardDto.java` L43 외부 `AiSystemModeCode aiModeCd` 필드의 @Schema 에 implementation = AiSystemModeCode.class 추가 → 검증: grep "implementation = AiSystemModeCode.class" 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpDashboardDto.java` L46 `List<PumpStateDto> pumps` 필드를 @ArraySchema(schema = @Schema(description=..., implementation = PumpStateDto.class)) 로 치환 → 검증: grep "@ArraySchema" + "implementation = PumpStateDto.class" 동시 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpDashboardDto.java` L103 inner ControlHistoryItem `PumpControlDivision ctrlDiv` 필드의 @Schema 에 implementation = PumpControlDivision.class 추가 → 검증: ControlHistoryItem 정적 내부 클래스 본문 라인 grep "implementation = PumpControlDivision.class" 추가 매칭 (전체 파일 내 매칭 횟수 2건)
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpDashboardDto.java` L106 inner ControlHistoryItem `PumpControlResult ctrlRslt` 필드의 @Schema 에 implementation = PumpControlResult.class 추가 → 검증: grep "implementation = PumpControlResult.class" inner 클래스 라인 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpDashboardDto.java` L109 inner ControlHistoryItem `AiDrvnModeType aiDrvnMod` 필드의 @Schema 에 implementation = AiDrvnModeType.class 추가 → 검증: 전체 파일 내 grep "implementation = AiDrvnModeType.class" 매칭 횟수 2건 (외부 L40 + inner L109)
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력

### Phase 4: ai 패키지 (3건)

- [x] `api/src/main/java/com/mo/swtp/ai/dto/AiModeDto.java` L28 `AiDrvnModeType aiDrvnMod` 필드의 @Schema 에 implementation = AiDrvnModeType.class 추가 → 검증: grep "implementation = AiDrvnModeType.class" aiDrvnMod 라인 매칭
- [x] `api/src/main/java/com/mo/swtp/ai/dto/AiModeDto.java` L31 `AiSystemModeCode aiModeCd` 필드의 @Schema 에 implementation = AiSystemModeCode.class 추가 → 검증: grep "implementation = AiSystemModeCode.class" aiModeCd 라인 매칭
- [x] `api/src/main/java/com/mo/swtp/ai/dto/AiModeUpsertDto.java` L32 `AiDrvnModeType aiDrvnMod` 필드의 @Schema 에 implementation = AiDrvnModeType.class 추가 → 검증: grep "implementation = AiDrvnModeType.class" 매칭
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력

### Phase 5: menu 패키지 (1건 + import 1건)

- [x] `api/src/main/java/com/mo/swtp/menu/dto/MenuTreeDto.java` import 추가 (io.swagger.v3.oas.annotations.media.ArraySchema) → 검증: grep "import io.swagger.v3.oas.annotations.media.ArraySchema" 1줄 매칭
- [x] `api/src/main/java/com/mo/swtp/menu/dto/MenuTreeDto.java` L40 `List<MenuTreeDto> children` 자기참조 필드를 @ArraySchema(schema = @Schema(description=..., implementation = MenuTreeDto.class)) 로 치환 → 검증: grep "@ArraySchema" + "implementation = MenuTreeDto.class" 동시 매칭
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력

### Phase 6: 정합성 검증

- [x] enum @Schema implementation 누락 점검 (PLAN §성공기준 1) → 검증: grep -rn "@Schema" api/src/main/java/com/mo/swtp 결과에서 사용자 정의 enum 필드 행 (UserRole·YnType·PumpControlDivision·PumpControlResult·AiDrvnModeType·AiSystemModeCode) 의 implementation 미포함 0건 (`String`·`LocalDateTime`·`BigDecimal` 등 표준 타입 행은 통과)
- [x] List @ArraySchema 누락 점검 (PLAN §성공기준 2) → 검증: grep -rn "List<" api/src/main/java/com/mo/swtp 결과에서 사용자 정의 List 필드 (`List<MenuTreeDto>`·`List<PumpStateDto>`·`List<ControlHistoryItem>`·`List<UserRole>`·`List<Recommendation>`) 의 @ArraySchema 미포함 0건 (`List<String>` 등 기본 타입 List 통과)
- [x] `./gradlew.bat :api:compileJava` 실행 (PLAN §성공기준 3) → 검증: BUILD SUCCESSFUL 출력
- [x] `./gradlew.bat build` 실행 (PLAN §성공기준 4) → 검증: BUILD SUCCESSFUL 출력
- [x] SpringDoc enum 6종 허용값 노출 검증 (PLAN §성공기준 5) → 검증: ./gradlew.bat :api:bootRun (path 정렬: /api/v3/api-docs) HTTP 200 응답. SpringDoc default 동작상 enum 은 components.schemas 별도 등록이 아닌 사용처 inline 으로 펼쳐짐 (예: MenuRoleUpsertDto.userRoles.items.enum=[ADMIN,USER]). 의도 (frontend SPEC 가 enum 허용값 식별) 달성. 발견사항: AiPredictionResponseDto 는 Controller 응답 body 미사용으로 SpringDoc auto extraction 미발견 — 어노테이션 효과는 코드 레벨 OK, 사용처 추가 시 자동 작동 예상 (별도 후속 사이클 검토 대상)
- [x] MenuTreeDto.children 자기참조 $ref 검증 (PLAN §성공기준 6) → 검증: curl /api/v3/api-docs | jq .components.schemas.MenuTreeDto.properties.children 결과 type=array + items.$ref=#/components/schemas/MenuTreeDto 단일 참조 PASS

### Phase 7: 옵션 A 추가 정정 (PLAN §범위 외 3건 — 사용자 결정 2026-05-08)

> Phase 6 grep 검증에서 발견된 PLAN1 §범위 외 3건 위반 (`MenuRoleUpsertDto.userRoles`·`AiPredictionResponseDto.recommendations`·`PumpDashboardDto.recentControlHistory`) 에 대해 사용자 결정 (옵션 A — 본 사이클 일괄 정정) 적용. PLAN1 §제외 사항 의 추정 ("ANALYZE 위반 표 외 파일 — 사용자 정의 enum/참조형 DTO/`List<E>` 필드 0건 추정") 이 ANALYZE1 grep 패턴 한계로 누락된 것이 원인. 본 Phase 는 Medium 작업 RESULT 면제 정책에 따라 IMPL 단계 마무리 보고로 §계획 외 변경 의도 부분 대체.

- [x] `api/src/main/java/com/mo/swtp/menu/dto/MenuRoleUpsertDto.java` import 추가 (io.swagger.v3.oas.annotations.media.ArraySchema) → 검증: grep "import io.swagger.v3.oas.annotations.media.ArraySchema" 1줄 매칭
- [x] `api/src/main/java/com/mo/swtp/menu/dto/MenuRoleUpsertDto.java` L22 `List<UserRole> userRoles` 필드를 @ArraySchema(schema = @Schema(description=..., implementation = UserRole.class)) 로 치환 + example 제거 → 검증: grep "@ArraySchema" + "implementation = UserRole.class" 동시 매칭, MenuRoleUpsertDto.java 파일 내 example = ADMIN/USER 0건
- [x] `api/src/main/java/com/mo/swtp/ai/dto/AiPredictionResponseDto.java` import 추가 (io.swagger.v3.oas.annotations.media.ArraySchema) → 검증: grep "import io.swagger.v3.oas.annotations.media.ArraySchema" 1줄 매칭
- [x] `api/src/main/java/com/mo/swtp/ai/dto/AiPredictionResponseDto.java` L33 `List<Recommendation> recommendations` 필드를 @ArraySchema(schema = @Schema(description=..., implementation = Recommendation.class)) 로 치환 → 검증: grep "@ArraySchema" + "implementation = Recommendation.class" 동시 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpDashboardDto.java` L53 `List<ControlHistoryItem> recentControlHistory` 필드를 @ArraySchema(schema = @Schema(description=..., implementation = ControlHistoryItem.class)) 로 치환 → 검증: grep "@ArraySchema" + "implementation = ControlHistoryItem.class" 동시 매칭
- [x] `./gradlew.bat build` 실행 (옵션 A 적용 후 재검증) → 검증: BUILD SUCCESSFUL 출력

## 산출물
- [결과](../../../results/20260508/swagger_schema_migration/RESULT1.md)
