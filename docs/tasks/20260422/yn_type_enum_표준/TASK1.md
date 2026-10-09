---
status: completed
created: 2026-04-22
updated: 2026-04-22
---
# 여부(Y/N) 처리 표준 — YnType enum 도입

## 관련 계획
- [계획안](../../../plan/20260422/yn_type_enum_표준/PLAN1.md)

## Phase

### Phase 1: YnType enum 신설
- [x] `common/src/main/java/com/mo/swtp/common/enumtype/YnType.java` 생성 — `Y`/`N` 상수, `isYes()`, `of(boolean)` 편의 메서드, `_yn` 컬럼 매핑 원칙 Javadoc

### Phase 2: User 엔티티 마이그레이션
- [x] `common/src/main/java/com/mo/swtp/user/domain/User.java` 수정 — `String useYn` 필드 타입을 `YnType`으로 교체, `@Enumerated(EnumType.STRING)` 추가, `create()`의 `"Y"`를 `YnType.Y`로, `deactivate()`의 `"N"`을 `YnType.N`으로 교체, `YnType` import 추가

### Phase 3: API 계층 마이그레이션
- [x] `api/src/main/java/com/mo/swtp/user/repository/UserRepository.java` 수정 — `findByUserIdAndUseYn` 두 번째 파라미터 타입 `String` → `YnType`, `YnType` import 추가
- [x] `api/src/main/java/com/mo/swtp/user/service/UserService.java` 수정 — `findByUserIdAndUseYn(userId, "Y")` 호출의 `"Y"`를 `YnType.Y`로 교체, `YnType` import 추가
- [x] `api/src/main/java/com/mo/swtp/auth/service/AuthService.java` 수정 — `findByUserIdAndUseYn(userId, "Y")` 호출의 `"Y"`를 `YnType.Y`로 교체, `YnType` import 추가
- [x] `api/src/main/java/com/mo/swtp/user/dto/UserDto.java` 수정 — `String useYn` → `YnType useYn`, `@Schema(example = "Y")` 제거하고 `description`만 유지, `YnType` import 추가

### Phase 4: 테스트 마이그레이션
- [x] `api/src/test/java/com/mo/swtp/user/service/UserServiceTest.java` 수정 — 4개 위치의 `"Y"` 리터럴을 `YnType.Y`로 교체, assertion의 `isEqualTo("Y")`도 `YnType.Y`로, `YnType` import 추가
- [x] `api/src/test/java/com/mo/swtp/auth/service/AuthServiceTest.java` 수정 — 4개 위치의 `findByUserIdAndUseYn(..., "Y")` stub을 `YnType.Y`로 교체, `YnType` import 추가

### Phase 5: 규칙 문서 보강
- [x] `.claude/rules/entity-patterns.md` 수정 — "여부(Y/N) 필드 패턴" 섹션 신설, `YnType` + `@Enumerated(EnumType.STRING)` 샘플 코드와 적용 기준 명시, "규칙 요약"에 한 줄 추가
- [x] `.claude/rules/api-patterns.md` 수정 — DTO 섹션에 "여부 필드는 `YnType` 타입만 사용하며 `allowableValues`·`@Pattern`·`example` 중복 작성을 금지" 규칙 추가
- [x] `.claude/rules/naming.md` 수정 — "Java 필드 타입 매핑" 섹션 신설 (`_yn` → `YnType` 매핑 기재)

### Phase 6: 빌드·테스트 검증
- [x] `./gradlew.bat :common:build` 실행 성공 확인 (YnType 컴파일 + QUser.useYn이 `EnumPath<YnType>`으로 재생성) — BUILD SUCCESSFUL in 36s
- [x] `./gradlew.bat :api:test` 실행 성공 확인 (UserServiceTest·AuthServiceTest 전 케이스 통과) — BUILD SUCCESSFUL in 29s
- [x] Swagger UI 수동 확인 절차 정리 — 커밋 전 `./gradlew.bat :api:bootRun` 기동 후 `/swagger-ui/index.html`에서 `UserDto.useYn`이 `enum: [Y, N]`으로 노출되는지 사용자 검증 (비회귀 체크리스트)

## 산출물
- [결과](../../../results/20260422/yn_type_enum_표준/RESULT1.md)
- [리뷰](../../../reviews/20260422/yn_type_enum_표준/REVIEW1.md)
