---
status: approved
created: 2026-05-06
updated: 2026-05-06
---
# 권한메뉴 도메인 도입

> **승인 일자: 2026-05-06**. ANALYZE1+2 결정 + 도메인/DB 검토 게이트 통과 (블로커 0건, 권고 4건 모두 본문 반영).

## 목적

스마트정수장 backend 에 신규 비즈니스 도메인 `menu` 를 도입하고, 권한별(ADMIN/USER) 메뉴 트리를 로그인 응답 body 로 전달하며, 메뉴 변경 시 frontend 가 `GET /api/menus/me` 로 재조회할 수 있는 흐름을 구현한다.

## 배경

[ANALYZE1](../../../analyze/20260504/권한메뉴/ANALYZE1.md) (12개 안건) + [ANALYZE2](../../../analyze/20260504/권한메뉴/ANALYZE2.md) (R-1·R-2·R-3·R-4 N:M 관계 룰 정합 재검토) 결과를 PLAN 단계 결정으로 변환한다. ANALYZE2 가 ANALYZE1 의 안건 6 (`menu_role_p` → `menu_role_r`) · 안건 13 (BaseEntity 상속 → B안 확정) · 부속 (`MenuRoleMappingId` → `MenuRoleId`) 을 override 하므로 본 PLAN 은 ANALYZE2 결정을 우선 반영한다.

사용자 사전 결정 (재논의 X — `happy-toasting-canyon.md` plan 승인):
- 메뉴 정보 위치: 로그인 응답 body 분리 (JWT 페이로드는 `role` 만 유지, 현행 그대로)
- 메뉴 변경 동기화: frontend `GET /api/menus/me` 재조회 (토큰 재발급 X)

## 범위

### 포함 (in scope)

**신규 패키지**: `com.mo.swtp.menu` (`com.mo.swtp.menu.domain`·`repository`·`service`·`dto`·`web`·`exception`)

**신규 엔티티 / 테이블 (2건)**
- `Menu` (`menu_m`) — 메뉴 마스터, 자기참조 self-FK, BaseEntity 상속
- `MenuRole` (`menu_role_r`) — 메뉴↔권한 N:M 매핑, `@EmbeddedId MenuRoleId`, BaseEntity 미상속 (B안 — `rgstr_*` 직접 선언)

**신규 DDL**: `common/src/main/resources/db/init/V6_6__menu_master_table.sql`

**신규 Service / Repository / DTO / Controller / Exception (16개 신규 파일)**:
- domain: `Menu.java`, `MenuRole.java`, `MenuRoleId.java`
- repository: `MenuRepository.java`, `MenuCustomRepository.java`, `MenuCustomRepositoryImpl.java`, `MenuRoleRepository.java`
- service: `MenuService.java`, `MenuQueryService.java`
- dto: `MenuUpsertDto.java`, `MenuTreeDto.java`, `MenuRoleUpsertDto.java`
- web: `MenuController.java`, `MyMenuController.java`
- exception: `MenuErrorCode.java`

**auth 도메인 보강 (2건 신규)**:
- service: `LoginFacadeService.java` — `AuthService.login()` + `MenuQueryService.findMenuTreeByRole()` 조립
- dto: `LoginResponseDto.java` — token + menus 응답 (`TokenResponseDto` 는 refresh 전용 유지)

**기존 파일 수정 (2건)**:
- `AuthController.login()` — 응답 DTO 변경 (`TokenResponseDto` → `LoginResponseDto`)
- `AuthService.login()` 또는 신규 `LoginFacadeService` — 호출 흐름 변경 (사용자 LoginFacadeService 기준 채택)

**테스트 신규 (4종)**:
- `MenuServiceTest` (단위) — CRUD ADMIN 권한 + 깊이 N=3 검증 + 비활성 시 자식 검증
- `MenuQueryServiceTest` (단위) — 권한별 트리 빌드 + 메모리 트리 알고리즘
- `LoginFacadeServiceTest` (통합) — 로그인 응답에 메뉴 트리 포함 검증
- `MenuRoleTest` (단위) — 매핑 엔티티 정적 팩토리 + `@EmbeddedId` equals/hashCode

**테스트 회귀**:
- `AuthServiceTest` — 응답 DTO 변경 영향 검증

### 제외 (out of scope)

- JWT stale token 무효화 정책 — ANALYZE1 사용자 결정 (별도 사이클)
- SSE/WebSocket 메뉴 변경 push 알림 — 별도 사이클
- frontend 화면 구현 — frontend 별도 작업 (`/dev:spec` 단계로 명세 전파)
- `user_role_c` 코드 마스터 신설 — ANALYZE2 가정 (현재 enum 직접 컬럼 유지)
- 메뉴 변경 polling 주기 / 트리거 정책 — frontend 결정

## 도메인 모델

### 엔티티 / 테이블 표

| 엔티티 / 테이블 | 역할 | 주요 필드 |
|---------------|------|----------|
| `Menu` / `menu_m` | 메뉴 마스터 — 자기참조 트리 | `menuId`(`DOM_ID_36` UUID PK), `menuNm`(`DOM_NAME_100` UNIQUE), `menuUrl`(`VARCHAR(255)` NULL), `menuDesc`(`DOM_TEXT` NULL), `dispOrd`(`INTEGER`), `parentMenuId`(`DOM_ID_36` self-FK NULL), `useYn`(`DOM_YN` `YnType`), BaseEntity 4 (`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`) |
| `MenuRole` / `menu_role_r` | 메뉴↔권한 N:M 매핑 (INSERT/DELETE 전용) | `id` (`@EmbeddedId MenuRoleId`: `menuId`+`userRole`), `menu`(`@ManyToOne LAZY @MapsId("menuId")`), `rgstrDtm`(`@CreatedDate` NOT NULL), `rgstrId`(`@CreatedBy` NOT NULL). BaseEntity 미상속 (B안 — `updt_*` 데드 컬럼 회피) |
| `MenuRoleId` (`@Embeddable`) | `MenuRole` 복합 PK | `menuId`(`DOM_ID_36`), `userRole`(`UserRole` enum + `@Enumerated(EnumType.STRING)`) |
| `LoginResponseDto` | 로그인 응답 DTO | `accessToken`·`refreshToken`·`accessExprDtm`·`refreshExprDtm`·`role`(`UserRole`)·`menus`(`List<MenuTreeDto>`) |
| `MenuTreeDto` | 메뉴 트리 응답 DTO (자기참조 재귀) | `menuId`·`menuNm`·`menuUrl`·`menuDesc`·`dispOrd`·`children`(`List<MenuTreeDto>`) |
| `MenuUpsertDto` | 메뉴 생성/수정 요청 | `menuNm`·`menuUrl`·`menuDesc`·`dispOrd`·`parentMenuId`·`useYn`(`YnType`) |
| `MenuRoleUpsertDto` | 메뉴-권한 매핑 갱신 요청 | `menuId`·`userRoles`(`List<UserRole>`) — DELETE 후 INSERT 일괄 처리 |

### 패키지 구조 (ANALYZE2 결정 반영)

```
com.mo.swtp.menu
├── domain/
│   ├── Menu.java                          # @Entity menu_m, BaseEntity 상속, UUID PK
│   ├── MenuRole.java                      # @Entity menu_role_r, BaseEntity 미상속(B안), @EmbeddedId
│   └── MenuRoleId.java                    # @Embeddable, menu_id + user_role 복합 PK
├── repository/
│   ├── MenuRepository.java                # JpaRepository + MenuCustomRepository
│   ├── MenuCustomRepository.java          # 트리 빌드 추상 메서드
│   ├── MenuCustomRepositoryImpl.java      # 단일 SELECT + 메모리 트리 빌드 (안건 11)
│   └── MenuRoleRepository.java            # JpaRepository<MenuRole, MenuRoleId>
├── service/
│   ├── MenuService.java                   # CRUD ADMIN-only, @Transactional, 깊이 N=3 검증
│   └── MenuQueryService.java              # 권한별 트리 조회, @Transactional(readOnly=true)
├── dto/
│   ├── MenuUpsertDto.java
│   ├── MenuTreeDto.java                   # children: List<MenuTreeDto>
│   └── MenuRoleUpsertDto.java
├── web/
│   ├── MenuController.java                # @Tag("XX. 메뉴 관리"), ADMIN-only
│   └── MyMenuController.java              # @Tag("XX. 내 메뉴 조회"), /api/menus/me
└── exception/
    └── MenuErrorCode.java                 # implements ErrorCode, httpStatus 만

com.mo.swtp.auth.service (기존 패키지 보강)
└── LoginFacadeService.java                # AuthService + MenuQueryService 조립

com.mo.swtp.auth.dto (기존 패키지 보강)
└── LoginResponseDto.java                  # token + menus 응답
```

## DB 설계 변경

### 신규 테이블 2건

#### `menu_m` (메뉴 마스터)

```sql
CREATE TABLE menu_m (
    menu_id        VARCHAR(36) NOT NULL,
    menu_nm        VARCHAR(100) NOT NULL,
    menu_url       VARCHAR(255),
    menu_desc      TEXT,
    disp_ord       INTEGER NOT NULL,
    parent_menu_id VARCHAR(36),
    use_yn         VARCHAR(1) NOT NULL,
    rgstr_dtm      TIMESTAMP NOT NULL,
    updt_dtm       TIMESTAMP NOT NULL,
    rgstr_id       VARCHAR(50) NOT NULL,
    updt_id        VARCHAR(50) NOT NULL,
    CONSTRAINT pk_menu_m PRIMARY KEY (menu_id),
    CONSTRAINT uk_menu_m_menu_nm UNIQUE (menu_nm),
    CONSTRAINT fk_menu_m_parent FOREIGN KEY (parent_menu_id)
        REFERENCES menu_m (menu_id) ON DELETE RESTRICT
);
COMMENT ON COLUMN menu_m.menu_id IS '메뉴 ID (DOM_ID_36, UUID 자동 생성)';
COMMENT ON COLUMN menu_m.menu_nm IS '메뉴명 (DOM_NAME_100, 시스템 전체 UNIQUE)';
COMMENT ON COLUMN menu_m.menu_url IS '메뉴 URL — frontend 라우트 (VARCHAR(255), NULL 허용 — 부모 그룹 메뉴 URL 없음, DOM 미지정)';
COMMENT ON COLUMN menu_m.menu_desc IS '메뉴 설명 (DOM_TEXT, NULL 허용)';
COMMENT ON COLUMN menu_m.disp_ord IS '표시 순서 (INTEGER, 동일 부모 내 정렬)';
COMMENT ON COLUMN menu_m.parent_menu_id IS '상위 메뉴 ID — self-FK (DOM_ID_36, NULL 허용 — 최상위 메뉴는 NULL, 깊이 N=3 애플리케이션 검증)';
COMMENT ON COLUMN menu_m.use_yn IS '사용 여부 (DOM_YN, YnType enum 매핑)';
COMMENT ON COLUMN menu_m.rgstr_dtm IS '등록 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN menu_m.updt_dtm  IS '수정 일시 (BaseEntity, AuditingEntityListener 자동 주입)';
COMMENT ON COLUMN menu_m.rgstr_id  IS '등록자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';
COMMENT ON COLUMN menu_m.updt_id   IS '수정자 ID (BaseEntity, AuditingEntityListener 자동 주입, DOM_ID_50)';

-- 인덱스 — `idx_menu_m_parent (parent_menu_id, disp_ord)` 미생성 (단일 SELECT + 메모리 트리 빌드 패턴, 실효성 낮음 — PLAN 결정)
```

#### `menu_role_r` (메뉴-권한 N:M 매핑)

```sql
CREATE TABLE menu_role_r (
    menu_id    VARCHAR(36) NOT NULL,
    user_role  VARCHAR(20) NOT NULL,
    rgstr_dtm  TIMESTAMP NOT NULL,
    rgstr_id   VARCHAR(50) NOT NULL,
    CONSTRAINT pk_menu_role_r PRIMARY KEY (menu_id, user_role),
    CONSTRAINT fk_menu_role_r_menu FOREIGN KEY (menu_id)
        REFERENCES menu_m (menu_id) ON DELETE CASCADE
);
COMMENT ON COLUMN menu_role_r.menu_id IS '메뉴 ID — 마스터 참조 FK (DOM_ID_36, ON DELETE CASCADE)';
COMMENT ON COLUMN menu_role_r.user_role IS '사용자 권한 (DOM_CODE_20, UserRole enum 매핑 — ADMIN·USER)';
COMMENT ON COLUMN menu_role_r.rgstr_dtm IS '등록 일시 (DOM_DTM, AuditingEntityListener 자동 주입 — INSERT-only NOT NULL)';
COMMENT ON COLUMN menu_role_r.rgstr_id IS '등록자 ID (DOM_ID_50, AuditingEntityListener 자동 주입 — INSERT-only NOT NULL)';

-- 인덱스 — `idx_menu_role_r_role (user_role)` 미생성 (카디널리티 2 단독 인덱스 Seq Scan 선호, db/indexing-and-migration.md §3.4 동일 정책 — ANALYZE2 R-2 결정)
```

### 무중단 마이그레이션 전략

신규 테이블 생성이므로 무중단 영향 없음. CREATE TABLE 단일 트랜잭션으로 적용. 기존 데이터/스키마와 충돌 0건.

### CHECK 제약 정책

ANALYZE1 안건 9 결정 — V6_6 신규 테이블에 CHECK 제약 일체 미적용. Java `@Enumerated(EnumType.STRING)` + `YnType`·`UserRole` enum 단일 방어선 (`db/indexing-and-migration.md §3.2`).

### COMMENT 정책

`db/indexing-and-migration.md §4` 의무 적용. BaseEntity 4 컬럼 표준 라벨 + 도메인 컬럼 한국어 라벨 적용. `check-ddl-column-comment.sh` 훅 자동 차단 통과 의무.

### 보존 정책

- `menu_m`: 마스터 영구 보존, 논리 삭제 (`use_yn = 'N'`) 만 허용 (`db/partitioning-and-retention.md §2`)
- `menu_role_r`: 매핑 영구 보존, 권한 변경 시 DELETE 후 재INSERT (UPDATE 시나리오 없음)

### FK ON DELETE 결정

| FK | ON DELETE | 근거 |
|----|----------|------|
| `menu_m.parent_menu_id → menu_m.menu_id` | RESTRICT | 부모 비활성·삭제 시 자식 고립 방지 — `MenuService.deactivate()` 진입에서 자식 활성 메뉴 존재 검증 (PLAN 결정 — ANALYZE1 가정 §자기참조 부모 비활성 시 자식 CASCADE 정책) |
| `menu_role_r.menu_id → menu_m.menu_id` | CASCADE | 마스터 삭제 시 매핑 자동 정리 — `entity-patterns.md §N:M 매핑 엔티티 패턴` 핵심 규칙 5 (마스터 FK CASCADE 기본) |

## 구현 방향

### 1. Menu 엔티티 (`menu_m`)

```java
@Entity
@Table(
    name = "menu_m",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_menu_m_menu_nm",
        columnNames = "menu_nm"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "menu_id", length = 36, nullable = false)
    private String menuId;

    @Column(name = "menu_nm", length = 100, nullable = false)
    private String menuNm;

    @Column(name = "menu_url", length = 255)
    private String menuUrl;

    @Column(name = "menu_desc", columnDefinition = "TEXT")
    private String menuDesc;

    @Column(name = "disp_ord", nullable = false)
    private Integer dispOrd;

    @Column(name = "parent_menu_id", length = 36)
    private String parentMenuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = 1)
    private YnType useYn;

    public static Menu create(MenuUpsertDto dto, String parentMenuId) {
        return new Menu(null, dto.getMenuNm(), dto.getMenuUrl(), dto.getMenuDesc(),
                dto.getDispOrd(), parentMenuId, YnType.Y);
    }

    public void changeInfo(MenuUpsertDto dto) { ... }
    public void deactivate() { this.useYn = YnType.N; }
}
```

- `entity-patterns.md §기본 엔티티 패턴` UUID 자동 생성 PK + `BaseEntity` 상속
- `entity-patterns.md §여부(Y/N) 필드 패턴` `use_yn YnType` + `@Enumerated(EnumType.STRING)`
- 자기참조: `parent_menu_id` 단방향 컬럼만 보유 (`@ManyToOne` 미선언) — `Menu.children` 양방향 매핑 미선언 (안건 11). 메모리 트리 빌드 시 `parent_menu_id` 기준
- 정적 팩토리 `create(...)` + 변경 메서드 `changeInfo(...)`·`deactivate()`

### 2. MenuRole 매핑 엔티티 (`menu_role_r`) — `entity-patterns.md §N:M 매핑 엔티티 패턴` 적용

```java
@Entity
@Table(name = "menu_role_r")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MenuRole {

    @EmbeddedId
    private MenuRoleId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("menuId")
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @CreatedDate
    @Column(name = "rgstr_dtm", nullable = false, updatable = false)
    private LocalDateTime rgstrDtm;

    @CreatedBy
    @Column(name = "rgstr_id", length = 50, nullable = false, updatable = false)
    private String rgstrId;

    public static MenuRole create(Menu menu, UserRole userRole) {
        return new MenuRole(new MenuRoleId(menu.getMenuId(), userRole), menu, null, null);
    }
}

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class MenuRoleId implements Serializable {

    @Column(name = "menu_id", length = 36, nullable = false)
    private String menuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", length = 20, nullable = false)
    private UserRole userRole;
}
```

- `entity-patterns.md §N:M 매핑 엔티티 패턴` 의 8개 핵심 규칙 모두 적용
- BaseEntity **미상속** (B안 — `rgstr_*` 직접 선언, `updt_*` 데드 컬럼 회피)
- `rgstr_dtm`·`rgstr_id` `nullable = false`, `updatable = false` (INSERT-only, AuditingEntityListener 자동 주입)
- `@MapsId("menuId")` 로 EmbeddedId 의 `menuId` 필드와 `Menu` 엔티티 동기

### 3. MenuRepository / MenuCustomRepository

```java
public interface MenuRepository extends JpaRepository<Menu, String>, MenuCustomRepository {
}

public interface MenuCustomRepository {
    /** 활성 메뉴 전체 단일 SELECT 후 메모리 트리 빌드 */
    List<MenuTreeDto> findMenuTreeByRole(UserRole userRole);
    List<MenuTreeDto> findFullMenuTree();  // ADMIN 관리 화면
}

public class MenuCustomRepositoryImpl implements MenuCustomRepository {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<MenuTreeDto> findMenuTreeByRole(UserRole userRole) {
        // 1. menu_role_r JOIN menu_m WHERE user_role = ? AND use_yn = 'Y' ORDER BY disp_ord
        //    단일 SELECT 로 권한별 활성 메뉴 평탄 목록 조회
        // 2. Java 메모리에서 parent_menu_id 기준 트리 빌드
        // 3. List<MenuTreeDto> (최상위) 반환
    }
}
```

- `api-patterns.md §Repository 패턴` 준수 — Custom + Impl 분리
- 단일 SELECT + 메모리 트리 빌드 (안건 11 — N+1 회피, JOIN FETCH 재귀보다 단순)
- `@JoinFetch` 또는 양방향 매핑 미사용

### 4. MenuRoleRepository

```java
public interface MenuRoleRepository extends JpaRepository<MenuRole, MenuRoleId> {
    List<MenuRole> findByIdMenuId(String menuId);   // 메뉴별 권한 조회
    void deleteByIdMenuId(String menuId);            // 권한 갱신 시 일괄 삭제
}
```

### 5. MenuService (CRUD, ADMIN-only)

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private static final int MAX_MENU_DEPTH = 3;

    private final MenuRepository menuRepository;
    private final MenuRoleRepository menuRoleRepository;

    @Transactional
    public Menu createMenu(MenuUpsertDto dto) {
        validateDepth(dto.getParentMenuId());      // 깊이 N=3 검증 (안건 5)
        Menu menu = Menu.create(dto, dto.getParentMenuId());
        return menuRepository.save(menu);
    }

    @Transactional
    public void changeMenu(String menuId, MenuUpsertDto dto) {
        Menu menu = findMenuOrThrow(menuId);
        if (!Objects.equals(menu.getParentMenuId(), dto.getParentMenuId())) {
            validateDepth(dto.getParentMenuId());
        }
        menu.changeInfo(dto);
    }

    @Transactional
    public void deactivateMenu(String menuId) {
        Menu menu = findMenuOrThrow(menuId);
        // 자식 활성 메뉴 존재 시 차단 (PLAN 결정 — 부모 활성 사전 검증)
        long activeChildren = menuRepository.countByParentMenuIdAndUseYn(menuId, YnType.Y);
        if (activeChildren > 0) {
            throw new RestApiException(MenuErrorCode.MENU_HAS_ACTIVE_CHILDREN);
        }
        menu.deactivate();
    }

    @Transactional
    public void changeMenuRoles(String menuId, List<UserRole> userRoles) {
        Menu menu = findMenuOrThrow(menuId);
        menuRoleRepository.deleteByIdMenuId(menuId);   // 일괄 삭제
        // 배치 INSERT — 루프 내 개별 save() 회피 (`db/query-tuning.md §N+1 방지 원칙` 대칭)
        List<MenuRole> mappings = userRoles.stream()
                .map(role -> MenuRole.create(menu, role))
                .toList();
        menuRoleRepository.saveAll(mappings);
    }

    private void validateDepth(String parentMenuId) {
        if (parentMenuId == null) return;   // 최상위
        int depth = computeDepth(parentMenuId) + 1;
        if (depth > MAX_MENU_DEPTH) {
            throw new RestApiException(MenuErrorCode.MENU_DEPTH_EXCEEDED);
        }
    }

    private int computeDepth(String menuId) { ... }   // 재귀 또는 반복으로 부모 체인 깊이 산정
    private Menu findMenuOrThrow(String menuId) { ... }
}
```

- `coding-discipline.md §2.1` — 메서드 50줄 이내 (`createMenu`·`changeMenu`·`deactivateMenu`·`changeMenuRoles` 모두 단순)
- 깊이 검증은 별도 private 메서드로 분리 (재사용 + SRP)
- 일괄 삭제 후 재INSERT 패턴 — `menu_role_r` INSERT/DELETE 전용 정합

### 6. MenuQueryService (권한별 트리 조회)

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuQueryService {

    private final MenuRepository menuRepository;

    /** 본인 권한에 해당하는 활성 메뉴 트리 — 로그인 응답 + GET /menus/me 공용 */
    public List<MenuTreeDto> findMenuTreeByRole(UserRole userRole) {
        return menuRepository.findMenuTreeByRole(userRole);
    }

    /** 전체 메뉴 트리 (ADMIN 관리 화면용) */
    public List<MenuTreeDto> findFullMenuTree() {
        return menuRepository.findFullMenuTree();
    }
}
```

### 7. LoginFacadeService + LoginResponseDto (auth 도메인 보강)

```java
// com.mo.swtp.auth.service.LoginFacadeService (안건 12 — 도메인 간 직접 의존 회피)
@Service
@RequiredArgsConstructor
public class LoginFacadeService {

    private final AuthService authService;
    private final MenuQueryService menuQueryService;

    @Transactional(readOnly = true)
    public LoginResponseDto login(String userId, String rawPw) {
        TokenPair tokens = authService.issueLoginTokens(userId, rawPw);   // 신규 메서드 — User 조회 + claims 발급
        UserRole role = authService.getRoleFromTokens(tokens);             // 또는 AuthService 가 role 도 반환
        List<MenuTreeDto> menus = menuQueryService.findMenuTreeByRole(role);
        return LoginResponseDto.build(tokens, role, menus);
    }
}
```

- `AuthService.login()` 시그니처 변경 — `TokenResponseDto` 반환을 `TokenPair` + `UserRole` 반환으로 변경 (또는 `AuthService.login()` 그대로 유지하고 LoginFacadeService 가 menus 만 추가하는 패턴)
- 본 PLAN 은 후자 (최소 변경) 채택 — `AuthService.login()` 은 `TokenPair + UserRole` 반환, `LoginFacadeService` 가 menus 추가 + DTO 변환
- `@Transactional(readOnly = true)` 단일 경계 — AuthService 의 트랜잭션을 그대로 사용 (Propagation REQUIRED)

```java
// com.mo.swtp.auth.dto.LoginResponseDto
@Data
@Schema(description = "로그인 응답 DTO")
public class LoginResponseDto {
    @Schema(description = "AccessToken")
    private String accessToken;
    @Schema(description = "RefreshToken")
    private String refreshToken;
    @Schema(description = "AccessToken 만료일시")
    private LocalDateTime accessExprDtm;
    @Schema(description = "RefreshToken 만료일시")
    private LocalDateTime refreshExprDtm;
    @Schema(description = "사용자 권한")
    private UserRole role;
    @Schema(description = "권한별 메뉴 트리")
    private List<MenuTreeDto> menus;

    public static LoginResponseDto build(TokenPair tokens, UserRole role, List<MenuTreeDto> menus) { ... }
}
```

### 8. AuthController.login 수정

기존 `TokenResponseDto` 반환을 `LoginResponseDto` 로 변경. `AuthService.login()` 직접 호출을 `LoginFacadeService.login()` 으로 변경.

```java
@PostMapping("/login")
@Operation(summary = "로그인 — JWT 발급 + 권한별 메뉴 트리 응답")
public CommonResponseDto<LoginResponseDto> login(@RequestBody LoginRequestDto request) {
    return CommonResponseDto.success(loginFacadeService.login(request.getUserId(), request.getUserPw()));
}
```

> ⚠️ 응답 DTO 변경은 frontend SPEC 영향 — `/dev:spec 권한메뉴` 단계에서 SPEC1.md 작성으로 전파.

### 9. MenuController + MyMenuController

```java
// com.mo.swtp.menu.web.MenuController (ADMIN-only)
@Tag(name = "XX. 메뉴 관리")
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final MenuQueryService menuQueryService;
    private final RoleGuard roleGuard;

    @PostMapping
    @Operation(summary = "메뉴 생성 (ADMIN-only)")
    public CommonResponseDto<MenuDto> create(HttpServletRequest request, @RequestBody MenuUpsertDto dto) {
        roleGuard.requireAdmin(request);
        return CommonResponseDto.success(MenuDto.from(menuService.createMenu(dto)));
    }

    @PutMapping("/{menuId}")
    @Operation(summary = "메뉴 수정 (ADMIN-only)")
    public CommonResponseDto<Void> update(HttpServletRequest request, @PathVariable String menuId, @RequestBody MenuUpsertDto dto) {
        roleGuard.requireAdmin(request);
        menuService.changeMenu(menuId, dto);
        return CommonResponseDto.success();
    }

    @DeleteMapping("/{menuId}")
    @Operation(summary = "메뉴 비활성화 (ADMIN-only)")
    public CommonResponseDto<Void> deactivate(HttpServletRequest request, @PathVariable String menuId) {
        roleGuard.requireAdmin(request);
        menuService.deactivateMenu(menuId);
        return CommonResponseDto.success();
    }

    @GetMapping
    @Operation(summary = "전체 메뉴 트리 조회 (ADMIN-only)")
    public CommonResponseDto<List<MenuTreeDto>> findAll(HttpServletRequest request) {
        roleGuard.requireAdmin(request);
        return CommonResponseDto.success(menuQueryService.findFullMenuTree());
    }

    @PostMapping("/{menuId}/roles")
    @Operation(summary = "메뉴-권한 매핑 갱신 (ADMIN-only)")
    public CommonResponseDto<Void> changeRoles(HttpServletRequest request, @PathVariable String menuId, @RequestBody MenuRoleUpsertDto dto) {
        roleGuard.requireAdmin(request);
        menuService.changeMenuRoles(menuId, dto.getUserRoles());
        return CommonResponseDto.success();
    }
}

// com.mo.swtp.menu.web.MyMenuController (인증 사용자)
@Tag(name = "XX. 내 메뉴 조회")
@RestController
@RequestMapping("/api/menus/me")
@RequiredArgsConstructor
public class MyMenuController {

    private final MenuQueryService menuQueryService;

    @GetMapping
    @Operation(summary = "본인 권한에 해당하는 메뉴 트리")
    public CommonResponseDto<List<MenuTreeDto>> findMine(HttpServletRequest request) {
        UserRole role = extractRole(request);   // claims["role"] 추출
        return CommonResponseDto.success(menuQueryService.findMenuTreeByRole(role));
    }

    private UserRole extractRole(HttpServletRequest request) { ... }
}
```

- `api-patterns.md §Swagger/OpenAPI 패턴` — `@Tag`·`@Operation`·`@ApiResponses` 적용 (간략화)
- `RoleGuard.requireAdmin()` 재사용 (UserController 패턴 동일)

### 10. MenuErrorCode (httpStatus 만, exception-patterns.md 정합)

```java
@Getter
@RequiredArgsConstructor
public enum MenuErrorCode implements ErrorCode {

    MENU_NOT_FOUND(404),
    DUPLICATE_MENU_NM(409),
    MENU_DEPTH_EXCEEDED(400),
    MENU_HAS_ACTIVE_CHILDREN(409),
    INVALID_PARENT_MENU(400);

    private final int httpStatus;
}
```

- `exception-patterns.md` §2 — `httpStatus(int)` 만 허용. `String message`/`getMessage()` 금지
- `check-errorcode-contract.sh` 훅 자동 차단 통과 의무

### 11. 시드 데이터 (V6_6 SQL 또는 별도 V6_7)

ANALYZE1 가정 §AI 운전 모드 메뉴 role 정책 → **PLAN 결정**: ADMIN-only 매핑 (`ot-integration.md §5` 인터록 우회 방지). 다만 본 PLAN 에서는 시드 데이터 적용을 **TASK 단계 옵션** 으로 둔다 — DDL 만 적용하고 시드는 운영 환경 수동 INSERT 또는 별도 시드 SQL.

본 PLAN 결정: **TASK 단계에서 시드 SQL 추가 여부 결정** (운영자 화면 운영 정책에 따라).

## 성공 기준 (검증 가능 형태)

### 빌드 / 컴파일

- [ ] `./gradlew.bat clean build` BUILD SUCCESSFUL — 전체 모듈 빌드 + 테스트 통과
- [ ] `./gradlew.bat :common:test` PASS — Menu·MenuRole·MenuRoleId 단위 테스트
- [ ] `./gradlew.bat :api:test` PASS — Service·Controller·LoginFacade 테스트

### 테스트 신규 (4종)

- [ ] `MenuServiceTest` — 신규 9건 이상:
  - 메뉴 생성 정상 (최상위·자식)
  - 메뉴 깊이 N=3 초과 시 `MENU_DEPTH_EXCEEDED`
  - 자식 활성 메뉴 존재 시 부모 비활성화 차단 (`MENU_HAS_ACTIVE_CHILDREN`)
  - 메뉴 권한 매핑 일괄 갱신 (DELETE 후 INSERT)
  - 메뉴 수정 시 부모 변경 시 깊이 재검증
  - 존재하지 않는 메뉴 조회 시 `MENU_NOT_FOUND`
- [ ] `MenuQueryServiceTest` — 신규 3건 이상:
  - 권한별 트리 빌드 정상 (ADMIN/USER 다른 트리)
  - 메모리 트리 알고리즘 — 평탄 목록 → 트리 변환 검증
  - 비활성 메뉴 제외 검증
- [ ] `LoginFacadeServiceTest` (통합 또는 단위) — 신규 2건 이상:
  - 로그인 응답에 메뉴 트리 포함
  - ADMIN/USER 권한별 다른 메뉴 트리 반환
- [ ] `MenuRoleTest` — 신규 2건 이상:
  - `MenuRoleId` equals/hashCode 검증
  - `MenuRole.create()` 정적 팩토리 검증

### 테스트 회귀

- [ ] `AuthServiceTest` 회귀 PASS — 기존 로그인 흐름 영향 없음 (응답 DTO 변경 영향 검증)
- [ ] `JwtAuthenticationFilterTest` 회귀 PASS — JWT 페이로드 변경 없음 검증

### DDL / 운영 검증

- [ ] 로컬 PostgreSQL 에서 V6_6 마이그레이션 실행 후 `\d menu_m`·`\d menu_role_r` 컬럼 + 코멘트 존재 검증
- [ ] `check-ddl-column-comment.sh` 훅 V6_6 SQL 저장 시 통과 (모든 컬럼 COMMENT)
- [ ] `check-errorcode-contract.sh` 훅 `MenuErrorCode.java` 저장 시 통과
- [ ] `MenuCustomRepositoryImpl.findMenuTreeByRole()` JOIN 쿼리에 대해 `EXPLAIN (ANALYZE, BUFFERS) SELECT ... FROM menu_role_r JOIN menu_m ...` 실행 → 인덱스 사용 + Seq Scan 비용 검증 (DBA 검토 권고 — `query-tuning.md §2` 분석 절차)

### API 동작 검증 (수동 또는 통합 테스트)

- [ ] `POST /api/auth/login` 응답에 `menus: [...]` 트리 포함 — Postman/curl
- [ ] ADMIN 토큰으로 로그인 시 ADMIN 메뉴 트리, USER 토큰은 USER 메뉴 트리
- [ ] `POST /api/menus` ADMIN 토큰 200, USER 토큰 403
- [ ] `GET /api/menus/me` 인증 토큰으로 권한별 다른 트리 반환
- [ ] `GET /api/menus` ADMIN 만 전체 트리 조회 가능
- [ ] `POST /api/menus/{id}/roles` 권한 매핑 갱신 → `GET /api/menus/me` 즉시 반영

### 룰 통과 자동 점검

- [ ] `check-task-unstage.sh` 훅 — 미완료 TASK 파일 staged 차단 검증
- [ ] `wtp-backend-engineer` REVIEW 자동 점검 — 메서드 50줄 / 추상화 3단 / DTO 상속 3단 초과 0건
- [ ] `wtp-domain-expert` REVIEW 자동 점검 — 도메인 4영역 충돌 0건 (메뉴는 OT 운전 흐름 외)

## 가정 및 미해결 질문

ANALYZE1+2 의 미해결 항목을 PLAN 단계에서 결정으로 변환.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 자기참조 메뉴 깊이 N=3 (애플리케이션 검증) | **결정** | 적용. `MAX_MENU_DEPTH = 3` 상수. 운영 검증 후 조정 가능 |
| `menu_role_r` BaseEntity 상속 | **결정 (ANALYZE2)** | B안 확정. `rgstr_*` 직접 선언, BaseEntity 미상속, `rgstr_*` NOT NULL |
| `MenuRoleMappingId` 클래스 네이밍 | **결정 (ANALYZE2)** | `MenuRoleId` 확정 |
| `idx_menu_m_parent` 인덱스 도입 | **결정 (PLAN)** | **미생성**. 단일 SELECT + 메모리 트리 빌드 패턴이라 실효성 낮음. 트리 빌드 성능 이슈 발견 시 추가 |
| `idx_menu_role_r_role` 역방향 인덱스 | **결정 (ANALYZE2)** | 보류. 카디널리티 2 단독 Seq Scan 선호. 행 수 수백 이상 + 역방향 조회 빈번 시 추가 |
| 자기참조 부모 비활성 시 자식 CASCADE | **결정 (PLAN)** | 부모 활성 사전 검증. `MenuService.deactivateMenu()` 진입에서 자식 활성 메뉴 존재 시 `MENU_HAS_ACTIVE_CHILDREN` 차단 |
| AI 운전 모드 메뉴 role 정책 | **결정 (PLAN)** | ADMIN-only 매핑 — 운전 모드·인터록 관련 기능에 대한 **오조작 방지 접근 제한** (사전 가드). 실제 인터록 차단은 `InterlockValidator.validateOrThrow()` 경로에서 수행되며, `ot-integration.md §5 ⚠️ 절대 금지` 조항의 직접 적용 대상은 아님 (도메인 검토 권고 반영 — 부록 참조). 시드 SQL 적용 여부는 TASK 단계 결정 |
| 권한 변경 시 stale JWT 토큰 무효화 | **별도 사이클 (ANALYZE1 결정 유지)** | 본 작업 범위 외. **TASK 체크박스에 "RESULT §비고에 stale JWT 후속 사이클 권고 명시" 항목 포함 의무** (도메인 검토 참고 반영 — 누락 방지) |
| `user_role_c` 코드 마스터 도입 | **미해결 (가정 유지)** | 향후 권한 종류 확장 시 검토. 현재는 `menu_role_r.user_role` 단순 enum 유지 |
| 향후 매핑 단위 갱신 가능 컬럼 추가 시 BaseEntity 전환 | **가정 유지** | `valid_period`·`grant_reason` 등 추가 시 PLAN 에서 A안 (BaseEntity 4) 전환 결정 |
| 시드 SQL 적용 시점 | **TASK 단계 결정** | V6_6 SQL 에 시드 INSERT 포함 vs 별도 V6_7 vs 운영 환경 수동 INSERT 중 TASK 단계에서 결정 |
| `AuthService.login()` 시그니처 변경 vs 유지 | **결정 (PLAN)** | 유지 — `AuthService` 는 TokenPair + UserRole 반환으로 최소 변경. `LoginFacadeService` 가 menus 추가 + DTO 변환 |

## 제외 사항

- JWT stale token 무효화 정책 (별도 사이클)
- SSE/WebSocket 메뉴 변경 push 알림 (별도 사이클)
- frontend 화면 구현 (frontend 별도, `/dev:spec` 단계에서 SPEC1.md 전파)
- `user_role_c` 코드 마스터 신설 (현재 enum 직접)
- 메뉴 변경 polling 주기 정책 (frontend 결정)
- 메뉴 트리 캐싱 (Redis 등) — 본 작업은 매 요청 단일 SELECT, 캐시 도입은 별도 ANALYZE 후

## 예상 산출물

- [태스크](../../../tasks/20260504/권한메뉴/TASK1.md) — 분할 여부는 TASK 단계에서 결정 (체크박스 60건 초과 시 분할 검토 — Phase 6~8 추정으로 분할 가능성)
- 신규 클래스 16개 (Menu·MenuRole·MenuRoleId·Repository 4종·Service 2종·DTO 3종·Controller 2종·MenuErrorCode·LoginFacadeService·LoginResponseDto)
- 기존 클래스 수정 2종 (`AuthController`·`AuthService`)
- DDL 1개 (`V6_6__menu_master_table.sql`)
- 테스트 4종 신규 + 2종 회귀

---

## 부록: 도메인/DB 검토 결과

### wtp-domain-expert (도메인 정합성)

- **결론**: 블로커 0건 / 권고(중간) 1건 / 참고(낮음) 1건
- **점검 항목 6건 모두 통과** — 메뉴 도메인은 OT 운전 흐름과 직접 접점 없음, 자기참조 부모 비활성 정책·CASCADE·로그인 응답 분리 모두 정합
- **권고 1건 (반영됨)**: AI 운전 모드 메뉴 role 정책의 `ot-integration.md §5 ⚠️ 절대 금지` 인용 정밀도 — 메뉴 ADMIN-only 는 오조작 방지 접근 제한이며, 실제 인터록 차단은 `InterlockValidator` 경로 → §가정 표 본문 수정 반영
- **참고 1건 (반영됨)**: stale JWT 후속 작업 RESULT 명시 누락 방지 → TASK 체크박스 명시 의무로 §가정 표 본문 강화

### wtp-dba-reviewer (DB 설계)

- **결론**: 블로커 0건 / 권고(중간) 2건 / 참고(낮음) 1건
- **점검 항목 8건 모두 통과** — DDL·복합 PK·FK CASCADE·인덱스 보류·무중단·N+1 회피·표준 도메인·VARCHAR(255) 운영 영향 모두 정합
- **표준 데이터 도메인 2차 승인 통과**: `DOM_ID_36`·`DOM_NAME_100`·`DOM_TEXT`·`DOM_YN`·`DOM_CODE_20`·`DOM_DTM` NOT NULL·`DOM_ID_50` NOT NULL 적용
- **권고 1건 (반영됨)**: `EXPLAIN (ANALYZE, BUFFERS)` 검증 시점 명시 → §성공 기준 §DDL/운영 검증에 추가
- **권고 1건 (반영됨)**: `changeMenuRoles` 루프 내 개별 `save()` → `saveAll()` 배치 INSERT 패턴 → §구현 방향 §5 MenuService 코드 예시 수정
