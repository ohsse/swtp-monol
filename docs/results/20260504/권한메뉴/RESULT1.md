---
status: completed
created: 2026-05-06
updated: 2026-05-06
---
# 권한메뉴 도메인 도입 — 구현 결과

## 관련 작업

- [계획안](../../../plan/20260504/권한메뉴/PLAN1.md)
- [태스크](../../../tasks/20260504/권한메뉴/TASK1.md)
- [ANALYZE1](../../../analyze/20260504/권한메뉴/ANALYZE1.md)
- [ANALYZE2](../../../analyze/20260504/권한메뉴/ANALYZE2.md) (N:M 관계 룰 정합 재검토 사이클)

## 작업 요약

스마트정수장 backend 에 신규 비즈니스 도메인 `menu` 를 도입했다. 자기참조 트리 (깊이 N=3) + 권한별(ADMIN/USER) N:M 매핑 + 로그인 응답 body 의 메뉴 트리 동봉 + `GET /api/menus/me` 재조회 흐름이 모두 구현되었다.

본 사이클의 N:M 매핑은 swtp 의 첫 N:M 관계 테이블 사례로, ANALYZE2 재검토를 통해 `menu_role_r` (`_r` 관계 suffix) + `MenuRole` 클래스 + `@EmbeddedId MenuRoleId` + BaseEntity 미상속 (B안) 표준이 확립되었다. 동시에 `entity-patterns.md` §N:M 매핑 엔티티 패턴 + `naming.md` §Java 클래스 네이밍 표 매핑/PK 2행이 룰에 신설되어 향후 N:M 관계의 SSOT 가 마련되었다.

`./gradlew build` BUILD SUCCESSFUL (44s) — 신규 테스트 19건 + 회귀 테스트 0건 실패.

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 9 |
| 체크박스 수 | 35 (정정 후 34 — Phase 6 첫 항목 제거) |
| 분할 여부 | N |
| 분할 근거 | — (Phase 10+ / 체크박스 60+ 모두 미달) |

## 변경 사항

### 의도된 변경

#### 신규 클래스 21개 + DDL 1개

**common 모듈 (3 + 테스트 1):**
- `common/src/main/java/com/mo/swtp/menu/domain/Menu.java` — 메뉴 마스터 엔티티 (BaseEntity 상속, UUID PK, `@UniqueConstraint(menu_nm)`)
- `common/src/main/java/com/mo/swtp/menu/domain/MenuRoleId.java` — 복합 PK (`@Embeddable`, `@EqualsAndHashCode`)
- `common/src/main/java/com/mo/swtp/menu/domain/MenuRole.java` — N:M 매핑 엔티티 (`@EmbeddedId`, `@MapsId`, `@CreatedDate`/`@CreatedBy` 직접 선언, BaseEntity 미상속 B안)
- `common/src/test/java/com/mo/swtp/menu/domain/MenuRoleTest.java` — equals/hashCode + create 정적 팩토리 (4건)

**api 모듈 (13 + 테스트 4):**
- `api/src/main/java/com/mo/swtp/menu/repository/MenuCustomRepository.java` + `MenuCustomRepositoryImpl.java` — Querydsl JOIN 단일 SELECT
- `api/src/main/java/com/mo/swtp/menu/repository/MenuRepository.java` — JpaRepository + Custom + 사전 검사 메서드 3종
- `api/src/main/java/com/mo/swtp/menu/repository/MenuRoleRepository.java` — JpaRepository<MenuRole, MenuRoleId>
- `api/src/main/java/com/mo/swtp/menu/dto/MenuUpsertDto.java` — 등록/수정 요청 DTO (`@NotBlank`/`@NotNull` validation)
- `api/src/main/java/com/mo/swtp/menu/dto/MenuTreeDto.java` — 자기참조 재귀 응답 DTO (`children` 빈 리스트 초기화)
- `api/src/main/java/com/mo/swtp/menu/dto/MenuRoleUpsertDto.java` — 권한 매핑 갱신 요청 DTO (`@NotEmpty`)
- `api/src/main/java/com/mo/swtp/menu/exception/MenuErrorCode.java` — 5개 enum (`httpStatus(int)` 만, 자동 차단 훅 통과)
- `api/src/main/java/com/mo/swtp/menu/service/MenuService.java` — CRUD ADMIN-only + 깊이 N=3 검증 + 자식 활성 시 비활성화 차단 + 매핑 갱신 (DELETE 후 saveAll)
- `api/src/main/java/com/mo/swtp/menu/service/MenuQueryService.java` — 단일 SELECT + 메모리 트리 빌드 (parent_menu_id 기준)
- `api/src/main/java/com/mo/swtp/menu/web/MenuController.java` — `@Tag("02. 메뉴 관리")` ADMIN-only 5종 엔드포인트
- `api/src/main/java/com/mo/swtp/menu/web/MyMenuController.java` — `@Tag("02-1. 내 메뉴 조회")` `GET /api/menus/me`
- `api/src/main/java/com/mo/swtp/auth/dto/LoginResponseDto.java` — token + menus 응답 DTO (`@Builder`, 정적 팩토리 `from(tokens, menus)`)
- `api/src/main/java/com/mo/swtp/auth/service/LoginFacadeService.java` — `AuthService.login` + `MenuQueryService.findMenuTreeByRole` 조립
- `api/src/test/java/com/mo/swtp/menu/service/MenuServiceTest.java` — 시나리오 9건 (생성/수정/비활성/매핑/깊이/자식활성/중복명/미존재/비활성 부모)
- `api/src/test/java/com/mo/swtp/menu/service/MenuQueryServiceTest.java` — 시나리오 4건 (트리 빌드/권한별/고립 노드/전체 트리)
- `api/src/test/java/com/mo/swtp/auth/service/LoginFacadeServiceTest.java` — 시나리오 2건 (메뉴 포함/권한별 분리)

**DDL:**
- `common/src/main/resources/db/init/V6_6__menu_master_table.sql` — `menu_m` + `menu_role_r` (CHECK 제약 미적용, COMMENT 전면 의무, 인덱스 0건)

#### 기존 클래스 수정 1건

- `api/src/main/java/com/mo/swtp/auth/web/AuthController.java` — login 엔드포인트 응답 DTO `TokenResponseDto` → `LoginResponseDto`, `LoginFacadeService` 의존성 주입. `refresh`·`logout` 엔드포인트는 변경 없음 (`AuthService` 시그니처 유지)

#### 룰 갱신 6건

- `swtp/.claude/rules/dict/standard-words.md` — `url` (한글 URL, 풀네임 uniform resource locator) + `role` (한글 역할, 기본 도메인 `DOM_CODE_20`) 신규 등록
- `swtp/.claude/rules/dict/standard-data-domains.md` — `## 폐기 이력` 하위 §등록 거부 이력 섹션 신설 + `DOM_URL_200` REJECT 기록 (DBA 2차 승인 — `DOM_TEXT` 유사 충돌, 200자 신뢰 근거 부족)
- `swtp/.claude/rules/dict/domain-abbreviations.md` — `menu` 도입 예정 등록 (PLAN approved 후 마스터 도메인 승격 예정)
- `backend/.claude/rules/dict/standard-terms.md` — 6건 신규 (`menu_id`·`menu_nm`·`menu_url`·`menu_desc`·`parent_menu_id`·`user_role`) + `disp_ord` 사용 테이블 갱신
- `backend/.claude/rules/entity-patterns.md` — §N:M 매핑 엔티티 패턴 신설 (8개 핵심 규칙 + 코드 예시) + §규칙 요약 한 줄 추가
- `backend/.claude/rules/naming.md` — §Java 클래스 네이밍 표에 N:M 매핑 엔티티 행 + 복합 PK 행 2건 추가

### 계획 외 변경

> ROOT [`coding-discipline.md` §3](../../../../.claude/rules/coding-discipline.md) 적용. TASK 체크박스 외 변경의 의도(필수 부수 변경) vs 우연(범위 이탈) 구분 명시.

#### 의도된 부수 변경 4건

1. **TASK1 모듈 정정** — Phase 2 의 엔티티 3개 + `MenuRoleTest` 경로를 `api` → `common` 으로 이동. 근거: `common/CLAUDE.md` "도메인 엔티티 단일 책임" + User 선례 (`common/.../user/domain/User.java`) 정합. ROOT [`coding-discipline.md` §1 5번째 항목](../../../../.claude/rules/coding-discipline.md) (도구 출력 경로 모듈 경계 절대 경로 확인) 적용. PLAN1 §패키지 구조에서 모듈 분리가 명시되지 않은 부분 보강.

2. **TASK1 Phase 6 첫 체크박스 제거** — `AuthService.login()` 시그니처 변경 항목 제거. 근거: PLAN1 §가정 표 "AuthService.login() 시그니처 변경 vs 유지 → 유지" 결정과 정합. 기존 `AuthService.login()` 이 이미 `TokenResponseDto` (accessToken·refreshToken·만료시각·role) 반환하므로 `LoginFacadeService` 가 변환만 수행.

3. **Repository 책임 분리 정정** — PLAN1 §3 의 `findMenuTreeByRole(UserRole) -> List<MenuTreeDto>` 시그니처 대신 `MenuCustomRepository.findActiveMenusByRole(UserRole) -> List<Menu>` 채택. 트리 빌드는 `MenuQueryService.buildTree()` 가 담당. 근거: 단일 책임 분리 (Repository=DB 평탄 조회, Service=메모리 변환). PLAN1 의 의사 코드 "// 1. ... 2. ... 3. ..." 였으므로 시그니처 미세 조정.

4. **`MenuRoleUpsertDto.userRoles` `@NotEmpty` 추가** — 빈 리스트 전달 시 매핑 전체 삭제 의미가 되어 의도치 않은 권한 박탈 위험. PLAN 미명시 부분 보강. 매핑 전체 삭제 의도가 있다면 별도 엔드포인트로 분리 (본 작업 범위 외).

#### 우연한 범위 이탈

없음. 본 작업과 무관한 working tree 변경 (`pump/`·`ai/`·V6_1~V6_5 SQL·docs/20260503 등) 은 직전 마스터도메인설계 작업 결과물로 본 사이클과 분리되어 있다.

## 테스트 결과

### 신규 테스트 19건 (모두 PASS)

| 테스트 파일 | 시나리오 수 | 결과 |
|-----------|----------|-----|
| `MenuRoleTest` (common) | 4 | PASS |
| `MenuServiceTest` | 9 | PASS |
| `MenuQueryServiceTest` | 4 | PASS |
| `LoginFacadeServiceTest` | 2 | PASS |
| **합계** | **19** | **PASS** |

### 회귀 테스트 0건 실패

- `AuthServiceTest` PASS — `AuthService.login()` 시그니처 변경 없음 (영향 0)
- `JwtAuthenticationFilterTest`·`JwtTokenManagementServiceTest`·`RoleGuardTest` PASS
- `UserServiceTest`·`UserEventHandlerTest` PASS
- 전체 모듈 (common·api·scheduler) 빌드 성공

### 빌드 / 검증

- `./gradlew.bat build` BUILD SUCCESSFUL (44s)
- `check-ddl-column-comment.sh` 훅 통과 — V6_6 SQL 모든 컬럼 COMMENT
- `check-errorcode-contract.sh` 훅 통과 — `MenuErrorCode` `httpStatus(int)` 만 보유

### 사용자 환경 의존 잔여 항목 (비고 인계)

본 자동화 단계에서 처리 불가한 운영 검증 항목 — Phase 8·9 일부:

- 로컬 PostgreSQL 에 V6_6 마이그레이션 적용 + `\d menu_m`·`\d menu_role_r` 컬럼·코멘트·FK·UNIQUE·PK 검증
- `EXPLAIN (ANALYZE, BUFFERS) SELECT ... FROM menu_role_r JOIN menu_m ...` 실행 → 인덱스 사용 + Seq Scan 비용 무시 가능 수준 검증 (DBA 검토 권고)
- API 동작 검증 (Postman/curl):
  - `POST /api/auth/login` 응답 body 의 `menus` 트리 ADMIN/USER 권한별 다른 트리 확인
  - `POST /api/menus` ADMIN 토큰 200 / USER 토큰 403 확인
  - `GET /api/menus/me` 권한별 다른 트리 반환 확인
  - `POST /api/menus/{id}/roles` 갱신 후 `GET /api/menus/me` 즉시 반영 확인

## 비고

### 후속 작업 권고

#### 1. JWT stale token 무효화 정책 (별도 사이클 필수)

ANALYZE1 안건 8 결정에 따라 본 작업 범위에서 제외되었다. 도메인 검토 (wtp-domain-expert) 에서도 "권한 변경 시 활성 JWT `role` 클레임이 stale → privilege escalation 위험" 으로 식별됨. 인증 흐름 전반 보안 명세로 별도 ANALYZE 사이클에서 다음 사항을 검토해야 한다.

- ADMIN 이 타 사용자 role 변경 시 활성 JWT 무효화 메커니즘
- refresh token 블랙리스트 또는 토큰 버전 관리 도입
- 로그인 시 token version claim 추가 + 사용자 마스터 token version 컬럼 도입 검토

#### 2. frontend SPEC1 작성 (`/dev:spec` 명시 호출)

본 작업의 인증 응답 변경 (`TokenResponseDto` → `LoginResponseDto`) + 신규 메뉴 API 6종 (POST/PUT/DELETE /api/menus, GET /api/menus, POST /api/menus/{id}/roles, GET /api/menus/me) 은 frontend 영향이 크다. 커밋 후 `/dev:spec 권한메뉴` 명시 호출하여 `swtp/frontend/docs/api-specs/권한메뉴/SPEC1.md` 작성 필수.

#### 3. 메뉴 변경 알림 채널 (선택)

frontend 가 폴링 또는 라우팅 진입 시 `GET /api/menus/me` 재조회로 메뉴 갱신 인식. 향후 SSE/WebSocket 도입 시:

- `MenuChangedEvent` 발행 (`Menu` 가 `DomainEventEntity` 로 전환)
- 활성 사용자에게 broadcast → frontend 즉시 재조회

별도 ANALYZE 사이클에서 도입 시점·트리거·연관 인프라 결정.

#### 4. `user_role_c` 코드 마스터 도입 검토

현재 `user_role` 은 enum 직접 컬럼 (`UserRole.ADMIN/USER`). 향후 권한 종류 확장 (예: VIEWER·OPERATOR·MAINTAINER) 시 `user_role_c` 코드 마스터 + `menu_role_r.user_role` FK 도입 검토. 도입 시 ON DELETE RESTRICT 정책 (`entity-patterns.md` §N:M 코드 마스터 FK).

#### 5. 매핑 단위 갱신 컬럼 추가 시 BaseEntity 전환

현재 `menu_role_r` 은 INSERT/DELETE 전용 (B안 — `rgstr_*` 직접 선언). `valid_period`·`grant_reason` 등 변경 가능 컬럼 추가 시 BaseEntity 4 컬럼 상속 (A안) 으로 전환. 별도 PLAN 단계에서 결정.

### 도메인/DB 검토 게이트 (PLAN 단계) 결과

PLAN1 §부록 참조. 블로커 0건, 권고 4건 모두 본 구현에 반영 완료:

- AI 운전 모드 메뉴 role 인용 근거 정밀도 (도메인 권고) → §가정 본문 정정
- stale JWT 후속 작업 RESULT 명시 (도메인 참고) → 본 §비고 §1 명시 (위)
- `EXPLAIN ANALYZE` 검증 항목 (DBA 권고) → §성공 기준 §DDL/운영 검증 추가, 본 §사용자 환경 의존 잔여 항목 인계
- `changeMenuRoles` 배치 INSERT (DBA 권고) → `MenuService.changeMenuRoles` 코드 반영 (`saveAll` 사용)

### N:M 매핑 표준 선례 확립 가치

`menu_role_r` 은 swtp 의 첫 순수 N:M 매핑 테이블이다. 본 작업의 결정 (`_r` suffix + `MenuRole` 클래스 + `MenuRoleId` 복합 PK + `@ManyToMany` 직접 매핑 금지 + INSERT/DELETE 전용 시 BaseEntity 미상속 B안 + 카디널리티 작은 컬럼 단독 인덱스 미적용) 은 `entity-patterns.md` §N:M 매핑 엔티티 패턴 + `naming.md` 표 2행으로 룰화되어 향후 N:M 관계 (예: `user_facility_r`·`role_instrument_r` 등) 의 SSOT 가 되었다.

ANALYZE1 의 1차 회의가 `_p`(명세) 채택을 결정했으나 사용자 재검토 요청 → ANALYZE2 5인 회의 만장일치로 `_r`(관계) 변경. 룰 본문 직접 정합을 우선한 사례로, 도메인 정합성 키워드 (`네이밍 충돌`) Fix Cycle 패턴이 적시에 작동한 결과.
