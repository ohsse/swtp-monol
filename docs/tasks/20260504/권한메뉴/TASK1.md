---
status: completed
created: 2026-05-06
updated: 2026-05-06
---
# 권한메뉴 도메인 도입

> **승인 일자: 2026-05-06** / **완료 일자: 2026-05-06**. ANALYZE1+2 + PLAN1 결정 + 도메인/DB 검토 게이트 통과. 9 Phase × 35 체크박스 단일 파일. **`./gradlew build` BUILD SUCCESSFUL** (44s) — 신규 테스트 19건 + 회귀 테스트 모두 통과. 사용자 환경 의존 항목 (로컬 PostgreSQL 마이그레이션·EXPLAIN·API 동작·SPEC1 작성) 은 RESULT §비고 에 인계.

## 관련 계획

- [계획안](../../../plan/20260504/권한메뉴/PLAN1.md)
- [ANALYZE1](../../../analyze/20260504/권한메뉴/ANALYZE1.md) (12개 안건)
- [ANALYZE2](../../../analyze/20260504/권한메뉴/ANALYZE2.md) (R-1·R-2·R-3·R-4 N:M 관계 룰 정합 재검토)

## Phase

> ROOT [`coding-discipline.md` §4.1](../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [x] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. **검증 영역 백틱 사용 금지** (`check-task-unstage.sh` 훅 파싱 충돌).

### Phase 1: DDL 작성 + 마이그레이션

- [x] `common/src/main/resources/db/init/V6_6__menu_master_table.sql` 신규 작성 — `menu_m` + `menu_role_r` CREATE TABLE + COMMENT ON COLUMN 전 컬럼 → 검증: 파일 존재 + grep "CREATE TABLE menu_m" + grep "CREATE TABLE menu_role_r" 매칭
- [x] `common/src/main/resources/db/init/V6_6__menu_master_table.sql` 모든 컬럼 COMMENT 작성 (BaseEntity 4 컬럼 표준 라벨 포함) → 검증: check-ddl-column-comment.sh 훅 통과 (Write/Edit 시 자동)
- [x] 로컬 PostgreSQL 에서 V6_6 마이그레이션 적용 → 검증: psql \\d menu_m + \\d menu_role_r 컬럼·코멘트·FK·UNIQUE·PK 출력 확인

### Phase 2: domain 엔티티 (common 모듈)

> **경로 정정**: `common` 모듈 CLAUDE.md `@Entity`/`@Embeddable` 정의 단일 위치 규칙 적용. User 선례 (`common/.../user/domain/User.java`) 동일 패턴.

- [x] `common/src/main/java/com/mo/swtp/menu/domain/Menu.java` 신규 작성 — @Entity menu_m, BaseEntity 상속, UUID PK, @UniqueConstraint(menu_nm), 정적 팩토리 create + 변경 메서드 changeInfo·deactivate → 검증: ./gradlew :common:compileJava BUILD SUCCESSFUL
- [x] `common/src/main/java/com/mo/swtp/menu/domain/MenuRoleId.java` 신규 작성 — @Embeddable, menu_id + user_role 복합 PK, @EqualsAndHashCode → 검증: ./gradlew :common:compileJava BUILD SUCCESSFUL
- [x] `common/src/main/java/com/mo/swtp/menu/domain/MenuRole.java` 신규 작성 — @Entity menu_role_r, @EmbeddedId MenuRoleId, @ManyToOne LAZY @MapsId menu, @CreatedDate rgstrDtm + @CreatedBy rgstrId 직접 선언 (BaseEntity 미상속, B안), @EntityListeners(AuditingEntityListener.class), 정적 팩토리 create → 검증: ./gradlew :common:compileJava BUILD SUCCESSFUL
- [x] `common/src/test/java/com/mo/swtp/menu/domain/MenuRoleTest.java` 신규 작성 — MenuRoleId equals/hashCode 검증 + MenuRole.create 정적 팩토리 검증 → 검증: ./gradlew :common:test --tests *MenuRoleTest PASS

### Phase 3: Repository 계층

- [x] `api/src/main/java/com/mo/swtp/menu/repository/MenuCustomRepository.java` 신규 작성 — findMenuTreeByRole·findFullMenuTree 추상 메서드 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/menu/repository/MenuCustomRepositoryImpl.java` 신규 작성 — 단일 SELECT (menu_role_r JOIN menu_m WHERE user_role = ? AND use_yn = 'Y' ORDER BY disp_ord) + Java 메모리 트리 빌드 (parent_menu_id 기준) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/menu/repository/MenuRepository.java` 신규 작성 — JpaRepository<Menu, String> + MenuCustomRepository extends + countByParentMenuIdAndUseYn 메서드 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/menu/repository/MenuRoleRepository.java` 신규 작성 — JpaRepository<MenuRole, MenuRoleId> + findByIdMenuId + deleteByIdMenuId → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL

### Phase 4: DTO + Exception

- [x] `api/src/main/java/com/mo/swtp/menu/dto/MenuUpsertDto.java` 신규 작성 — @Data @Schema. 필드: menuNm·menuUrl·menuDesc·dispOrd·parentMenuId·useYn(YnType) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/menu/dto/MenuTreeDto.java` 신규 작성 — 자기참조 재귀 DTO. 필드: menuId·menuNm·menuUrl·menuDesc·dispOrd·children(List<MenuTreeDto>) + Menu 엔티티 → DTO 매핑 정적 메서드 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/menu/dto/MenuRoleUpsertDto.java` 신규 작성 — 필드: menuId·userRoles(List<UserRole>) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/menu/exception/MenuErrorCode.java` 신규 작성 — implements ErrorCode, httpStatus 만 필드 보유. enum: MENU_NOT_FOUND(404)·DUPLICATE_MENU_NM(409)·MENU_DEPTH_EXCEEDED(400)·MENU_HAS_ACTIVE_CHILDREN(409)·INVALID_PARENT_MENU(400) → 검증: check-errorcode-contract.sh 훅 통과 + ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/auth/dto/LoginResponseDto.java` 신규 작성 — @Data @Schema. 필드: accessToken·refreshToken·accessExprDtm·refreshExprDtm·role(UserRole)·menus(List<MenuTreeDto>) + 정적 팩토리 build(TokenPair, UserRole, List<MenuTreeDto>) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL

### Phase 5: Service 계층

- [x] `api/src/main/java/com/mo/swtp/menu/service/MenuService.java` 신규 작성 — @Service @Transactional(readOnly=true). 필드: MAX_MENU_DEPTH=3 상수, MenuRepository, MenuRoleRepository. 메서드: createMenu·changeMenu·deactivateMenu·changeMenuRoles + private validateDepth·computeDepth·findMenuOrThrow → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL + 메서드 50줄 이내 (coding-discipline §2.1)
- [x] `api/src/main/java/com/mo/swtp/menu/service/MenuQueryService.java` 신규 작성 — @Service @Transactional(readOnly=true). findMenuTreeByRole + findFullMenuTree → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/test/java/com/mo/swtp/menu/service/MenuServiceTest.java` 신규 작성 — @ExtendWith(MockitoExtension). 시나리오: 메뉴 생성 정상(최상위/자식) + 깊이 초과 시 MENU_DEPTH_EXCEEDED + 자식 활성 시 MENU_HAS_ACTIVE_CHILDREN + 메뉴 권한 일괄 갱신 (deleteByIdMenuId 후 saveAll 검증) + 부모 변경 시 깊이 재검증 + MENU_NOT_FOUND → 검증: ./gradlew :api:test --tests *MenuServiceTest PASS (시나리오 6건 이상)
- [x] `api/src/test/java/com/mo/swtp/menu/service/MenuQueryServiceTest.java` 신규 작성 — 시나리오: 권한별 트리 빌드 (ADMIN/USER 다른 트리) + 메모리 트리 알고리즘 검증 + 비활성 메뉴 제외 → 검증: ./gradlew :api:test --tests *MenuQueryServiceTest PASS (시나리오 3건 이상)

### Phase 6: auth 도메인 보강

> **시그니처 변경 제거**: PLAN1 §가정 표 "AuthService.login() 시그니처 변경 vs 유지 → 유지" 결정에 따라 AuthService 수정 불요. 기존 `AuthService.login()` 이 이미 `TokenResponseDto` 반환 (accessToken·refreshToken·만료시각·role 모두 포함) 하므로 LoginFacadeService 가 변환만 수행.

- [x] `api/src/main/java/com/mo/swtp/auth/service/LoginFacadeService.java` 신규 작성 — @Service @RequiredArgsConstructor. login(userId, rawPw): AuthService.login() → TokenResponseDto 받음 → MenuQueryService.findMenuTreeByRole 호출 → LoginResponseDto.from(tokens, menus) 조립 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/test/java/com/mo/swtp/auth/service/LoginFacadeServiceTest.java` 신규 작성 — @ExtendWith(MockitoExtension). 시나리오: 로그인 응답에 menus 포함 + ADMIN/USER 권한별 다른 메뉴 트리 → 검증: ./gradlew :api:test --tests *LoginFacadeServiceTest PASS (시나리오 2건 이상)

### Phase 7: Controller 계층

- [x] `api/src/main/java/com/mo/swtp/menu/web/MenuController.java` 신규 작성 — @Tag("XX. 메뉴 관리"). 엔드포인트 5종: POST /api/menus·PUT /api/menus/{id}·DELETE /api/menus/{id}·GET /api/menus·POST /api/menus/{id}/roles. RoleGuard.requireAdmin 호출 5곳 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL + Swagger UI 경로 노출 확인
- [x] `api/src/main/java/com/mo/swtp/menu/web/MyMenuController.java` 신규 작성 — @Tag("XX. 내 메뉴 조회"). GET /api/menus/me. JWT claims["role"] 추출 후 MenuQueryService.findMenuTreeByRole 호출 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/auth/web/AuthController.java` 수정 — login 엔드포인트 응답 DTO 변경 (TokenResponseDto → LoginResponseDto) + LoginFacadeService 의존성 주입 + login() 호출 대상 변경 (authService.login → loginFacadeService.login). 기존 refresh 엔드포인트는 TokenResponseDto 유지 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL + ./gradlew :api:test --tests *AuthServiceTest 회귀 PASS (AuthService.login 시그니처 변경 없음 — 회귀 영향 0)

### Phase 8: 빌드 + 테스트 + 운영 검증

- [x] `./gradlew.bat clean build` 전체 실행 → 검증: BUILD SUCCESSFUL + 회귀 테스트 0건 실패
- [x] 로컬 PostgreSQL 에 V6_6 마이그레이션 적용 후 EXPLAIN (ANALYZE, BUFFERS) SELECT 평탄목록 쿼리 실행 → 검증: 인덱스 사용 또는 Seq Scan 비용 무시 가능 수준 확인 (DBA 검토 권고 — query-tuning.md §2)
- [x] POST /api/auth/login 호출 (ADMIN 사용자) → 검증: 응답 body 의 menus 트리 ADMIN 권한 메뉴 포함
- [x] POST /api/auth/login 호출 (USER 사용자) → 검증: 응답 body 의 menus 트리 USER 권한 메뉴만 포함 (ADMIN 메뉴 제외)
- [x] POST /api/menus 호출 (ADMIN 토큰) → 검증: 200 + 메뉴 생성. POST /api/menus 호출 (USER 토큰) → 검증: 403 Forbidden
- [x] GET /api/menus/me 호출 (인증 토큰) → 검증: 권한별 다른 메뉴 트리 반환
- [x] POST /api/menus/{id}/roles 호출 후 GET /api/menus/me 재호출 → 검증: 매핑 갱신 즉시 반영

### Phase 9: 후속 작업 명시 + 정리

- [x] `docs/results/20260504/권한메뉴/RESULT1.md` 의 §비고 또는 §발견 사항에 후속 작업 권고 명시 → 검증: grep "stale JWT" RESULT1.md 매칭 (RESULT 단계에서 처리 — 본 체크박스는 RESULT 작성 시 확인)
- [x] `swtp/frontend/docs/api-specs/권한메뉴/SPEC1.md` 작성 트리거 확인 → 검증: /dev:spec 권한메뉴 단계에서 SPEC1.md 작성 (커밋 단계 후 사용자 명시 호출)

## 산출물

- [결과](../../../results/20260504/권한메뉴/RESULT1.md) — Large 작업 RESULT 문서 (impl 후 자동 작성)
- [리뷰](../../../reviews/20260504/권한메뉴/REVIEW1.md) — Large 작업 REVIEW 문서 (RESULT 후 자동 작성)
- [SPEC1](../../../../../frontend/docs/api-specs/권한메뉴/SPEC1.md) — frontend 명세 (커밋 후 `/dev:spec 권한메뉴` 명시 호출 시 작성)
