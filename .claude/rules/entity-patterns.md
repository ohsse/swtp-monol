# 엔티티 코드 패턴

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| `swtp/.claude/rules/dict/standard-data-domains.md` | 필드 SQL 타입·Java 타입 1차 정의 — `DOM_*` 코드 (ROOT) |
| [`naming.md`](naming.md) | 테이블명·컬럼명 suffix 규칙 (`_m`·`_h` 등) |
| [`api-patterns.md`](api-patterns.md) | DTO·Swagger 와 엔티티 설계 연계 |
| `swtp/.claude/rules/dict/domain-abbreviations.md` | 비즈니스 도메인 약어 — PK·테이블명 조합 재료 (ROOT) |
| [`ot-integration.md`](ot-integration.md) | §JPA JOINED + DiscriminatorColumn 다형성 패턴 의 자식 종류별 도메인 룰 (`facility_type_cd`·`equip_type_cd` 필터 강제) 의 1차 정의처 |

---

## 기본 엔티티 패턴

```java
@Entity
@Table(name = "ds_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Dataset extends DomainEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ds_id")
    private String dsId;

    @Column(name = "ds_nm")
    private String dsNm;

    /**
     * 정적 팩토리 메서드 — 생성자 직접 호출 대신 사용
     */
    public static Dataset create(DatasetUpsertDto dto) {
        return new Dataset(null, dto.getDsNm());
    }

    /**
     * 상태 변경은 의도가 드러나는 메서드로 처리
     */
    public void changeInfo(DatasetUpsertDto dto) {
        if (dto.getDsNm() != null) {
            this.dsNm = dto.getDsNm();
        }
    }
}
```


## 외부 할당 PK 엔티티 패턴

PK를 외부에서 할당받는 엔티티(`@GeneratedValue` 미사용) — 예: `user_m.user_id`, `pump_m.pump_id` 같은 도메인 자연키 기반 마스터 — 는 반드시 `Persistable<ID>`를 구현해야 한다.

미구현 시 `save()`가 ID null-check로 신규 여부를 판정하므로 항상 `em.merge()`를 호출, 불필요한 `SELECT`가 선행된다.

```java
@Entity
@Table(name = "user_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends BaseEntity implements Persistable<String> {

    @Id
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    // ... 나머지 필드 ...

    /** {@inheritDoc} — PK를 반환한다. */
    @Override
    public String getId() {
        return userId;
    }
    // isNew()는 BaseEntity의 newEntity 플래그에 위임 — 별도 구현 불필요

    public static User create(String userId, /* ... */) {
        return new User(userId, /* ... */);
    }
}
```

- `getId()`만 override하면 된다. `isNew()`는 `BaseEntity`가 `@Transient newEntity` 플래그로 제공한다.
- `@PostLoad`와 `@PrePersist`에서 자동으로 플래그를 전환하므로 직접 조작하지 않는다.
- UUID 자동생성 엔티티는 `Persistable` 구현이 불필요하다.
- 본 패턴 적용 사례: `user_m.user_id`(사용자 도메인 자연키)·`tag_m.tag_srl_no`(SCADA 외부 할당 시리얼번호). 마스터 데이터 PK 는 기본적으로 UUID 자동 생성 (§기본 엔티티 패턴) 사용 권장 — 마스터도메인설계 ANALYZE1 Round 3 (2026-05-03) 결정으로 `facility_m`·`instrument_m` 은 UUID 자동 생성 채택, 외부 할당 PK 패턴은 `tag_m` 만 적용.

---

## 여부(Y/N) 필드 패턴

"여부"를 표현하는 필드(`*_yn` 컬럼 / `*Yn` 필드)는 반드시 `YnType` enum + `@Enumerated(EnumType.STRING)` 조합으로 선언한다.
`String` 으로 직접 보관하거나 `AttributeConverter` 로 Boolean 변환하지 않는다.

```java
import com.mo.swtp.common.enumtype.YnType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Enumerated(EnumType.STRING)
@Column(name = "use_yn", nullable = false, length = 1)
private YnType useYn;

// 생성 시
return new User(userId, userNm, encodedPw, role, YnType.Y);

// 상태 변경 시
public void deactivate() {
    this.useYn = YnType.N;
}
```

- `YnType.Y` · `YnType.N` enum name 이 DB VARCHAR(1) 값과 1:1 로 일치하므로 컨버터가 필요 없다.
- 분기는 `YnType#isYes()` 로 처리한다. `"Y".equals(...)` 같은 문자열 비교는 금지.
- DTO 에서도 동일하게 `YnType` 을 쓰고 `@Schema(allowableValues)` · `@Pattern` · `example` 은 작성하지 않는다
  (상세: [api-patterns.md](api-patterns.md) DTO 섹션).
- **Java 정적 팩토리(`User.create(...)` 등) 에서 `YnType.Y` 명시 할당 의무** — 본 패턴이 진실 소스(SSOT). DDL `DEFAULT` 정책(미설정·기존 컬럼 정렬)은 [db/indexing-and-migration.md §3 `DOM_YN` DDL 정책](db/indexing-and-migration.md) 위임 (use_yn_consistency ANALYZE1, 2026-04-25).
- `deactivate()` 메서드는 비활성화 의도를 드러내는 표준 변경 메서드로 사용한다 (위 코드 예제). 4건 이상 누적 시 `@MappedSuperclass ActivatableEntity` 추상화 재검토.
- `use_yn` 은 `BaseEntity` 추상화 대상 외다 — 감사 메타(`rgstr_dtm`·`updt_dtm` 등) 와 활성 상태는 의미 범주가 다르므로 SRP 관점에서 분리한다.
- DDL CHECK·DEFAULT·인덱스 운영 정책은 [db/indexing-and-migration.md §3 `DOM_YN` DDL 정책](db/indexing-and-migration.md) 참조.
- DTO 의 `*Yn` 필드는 `@Schema(description=..., implementation = YnType.class)` 명시 — [`api-patterns.md §DTO @Schema(implementation) 명시 패턴`](api-patterns.md) 참조 (양방향 교차 참조).

---

## JPA JOINED + DiscriminatorColumn 다형성 패턴

단일 마스터에 종류별 자식 테이블이 다수 존재하는 도메인 (예: `facility_m` + 자식 9종 `pwtf_m`·`dwt_m`·`rsv_m`·`pump_m`·`valve_m`·`flwmtr_m`·`prsmtr_m`·`lvmtr_m`·`elcmtr_m`) 은 JPA `@Inheritance(strategy = InheritanceType.JOINED)` + `@DiscriminatorColumn` 패턴을 사용한다. 마스터도메인설계 ANALYZE1 Round 2 (2026-05-02) 결정.

### 부모 (단일 마스터)

```java
@Entity
@Table(
    name = "facility_m",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_facility_m_facility_nm",
        columnNames = "facility_nm"
    )
)
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "facility_type_cd", discriminatorType = DiscriminatorType.STRING, length = 20)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Facility extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "facility_id", length = 36, nullable = false)
    private String facilityId;

    @Column(name = "facility_nm", length = 100, nullable = false)
    private String facilityNm;

    @Enumerated(EnumType.STRING)
    @Column(name = "facility_type_cd", length = 20, nullable = false, insertable = false, updatable = false)
    private FacilityType facilityType;

    @Column(name = "parent_facility_id", length = 36)
    private String parentFacilityId;

    @Column(name = "disp_ord", nullable = false)
    private Integer dispOrd;

    @Enumerated(EnumType.STRING)
    @Column(name = "main_yn", nullable = false, length = 1)
    private YnType mainYn;

    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = 1)
    private YnType useYn;
}
```

### 자식 (자식 PK 자동 상속)

```java
@Entity
@Table(name = "pwtf_m")
@DiscriminatorValue("PWTF")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurifiedWaterTankFacility extends Facility {
    // 자식 PK = 부모 facility_id 동일 — JPA JOINED 표준 동작으로 자동 상속.
    // 별도 @Id·@Column(name="facility_id") 선언 불필요.
    // 자식 전용 컬럼은 추후 요구사항명세서 기반 PLAN 단계에서 결정 (마스터도메인설계 ANALYZE1 Round 3).
}
```

### 핵심 규칙

- **부모 PK**: `@GeneratedValue(strategy = GenerationType.UUID)` — UUID 자동 생성. `Persistable<String>` 구현 **불필요** (마스터도메인설계 ANALYZE1 Round 3 정정 — Round 1·2 의 외부 할당 PK 결정 폐기).
- **자식 PK**: 부모 PK 와 동일 컬럼명 사용. JPA JOINED 표준 동작으로 자동 상속되므로 자식 클래스에 `@Id` 재선언 금지.
- **DiscriminatorColumn**: 부모 테이블에 `facility_type_cd`·`equip_type_cd` 같은 종류 코드 컬럼으로 매핑. `insertable = false, updatable = false` 로 영속 컨텍스트가 자동 관리.
- **이름 UNIQUE 비즈니스 식별자**: 부모 마스터의 `*_nm` 컬럼은 `@UniqueConstraint` 로 시스템 전체 UNIQUE 보장 (마스터도메인설계 ANALYZE1 Round 3 사용자 결정 핵심) — UUID PK 는 시스템 내부 식별자, 사용자 식별은 이름값 의존.
- **`use_yn`·`main_yn` 등 `*_yn` 필드**: 위 §여부(Y/N) 필드 패턴 준수 (`YnType` + `@Enumerated(EnumType.STRING)`).
- **추상 부모 클래스**: `abstract` 키워드 명시 — 부모는 직접 인스턴스화 금지, 자식 클래스 통해서만 영속.
- **Repository 분리 vs 통합**: 부모·자식 각각의 Repository 는 도메인 요구에 따라 분리 또는 통합. JOINED 다형성 LEFT OUTER JOIN N개 핫패스 우려는 도메인 시나리오상 시설 종류별 화면 분리 (다형성 전체 조회 빈도 = 드물거나 없음) 가정으로 해소 (마스터도메인설계 ANALYZE1 Round 2 wtp-dba-reviewer 권고).

### 도메인 룰 — `facility_type_cd` 필터 강제

AI 운전 모드 평가·제어 명령 발행 등 자식 종류별 도메인 룰이 다른 시나리오에서는 부모 다형성 조회 시 반드시 `facility_type_cd` (또는 `equip_type_cd`) 필터를 명시한다 — `ot-integration.md §5` 참조.

```java
// 올바른 예: PUMP 자식만 평가
List<Pump> pumps = instrumentRepository.findByEquipTypeCd(EquipType.PUMP);

// 잘못된 예: 부모 다형성 전체 조회 (제어 평가 시 인터록 룰 종류 혼선)
List<Instrument> instruments = instrumentRepository.findAll();  // 금지 - 도메인 룰 위반 위험
```

### 자식 전용 컬럼 도입 시점

본 ANALYZE 단계에서는 자식 테이블 skeleton 만 정의하며, 자식 전용 컬럼 (예: `dwt_m.min_req_prsr`·`pump_m.rated_head`·`pump_m.rated_flwrt`·`flwmtr_m.range_min` 등) 은 추후 요구사항명세서 기반 PLAN 단계에서 결정 (마스터도메인설계 ANALYZE1 Round 3 사용자 명시 결정).

### 응답 DTO 매핑 패턴 (3단 상속)

JPA JOINED + DiscriminatorColumn 다형성 도메인의 응답 DTO 는 부모/자식 분리 의도를 응답 스키마에 정렬한다. 자식 전용 필드를 부모 DTO 에 노출하고 `instanceof` 분기로 채우는 방식은 **금지**. 시설물응답DTO명세 ANALYZE1 (2026-05-12) 결정.

#### 표준 구조

`BaseAuditResponseDto → {도메인}Dto(abstract) → {자식}Dto` 3단 상속. 추상 부모 DTO 에 Jackson `@JsonTypeInfo(use=NAME, include=EXISTING_PROPERTY, property="{discriminator}", visible=true)` + `@JsonSubTypes` 다형성 어노테이션 적용. 자식 전용 필드는 자식 DTO 에만 선언. 상세 룰·허용 조건·Swagger 매핑은 [`api-patterns.md §BaseAuditResponseDto 패턴 §상속 상한`](api-patterns.md) 1차 정의.

#### 정적 팩토리 패턴

추상 부모의 `from(부모엔티티)` 정적 팩토리가 자식 타입 매칭으로 자식 DTO 인스턴스를 반환한다. Service 계층 분기 책임 회피.

```java
// 추상 부모 — 자식 타입 매칭 정적 팩토리
public abstract class FacilityDto extends BaseAuditResponseDto {
    public static FacilityDto from(Facility facility) {
        return switch (facility) {
            case DistributionWaterTank dwt -> DwtDto.from(dwt);
            case PurifiedWaterTankFacility pwtf -> PwtfDto.from(pwtf);
            case Reservoir rsv -> RsvDto.from(rsv);
            case PressureBoosterStation prsf -> PrsfDto.from(prsf);
            case SensorPoint point -> PointDto.from(point);
            default -> throw new IllegalStateException("Unknown facility subtype: " + facility.getClass());
        };
    }
}

// 자식 DTO — applyAuditMeta(부모) 호출
public class DwtDto extends FacilityDto {
    private BigDecimal minReqPrsr;

    private DwtDto(DistributionWaterTank dwt) {
        // 부모 필드 + 자식 전용 필드 할당
        this.minReqPrsr = dwt.getMinReqPrsr();
        applyAuditMeta(dwt);
    }

    public static DwtDto from(DistributionWaterTank dwt) {
        return new DwtDto(dwt);
    }
}
```

#### Controller 응답 시그니처

Controller 응답 타입은 추상 부모 `ResponseEntity<CommonResponseDto<{도메인}Dto>>` 사용. 런타임 직렬화는 Jackson 다형성으로 안전. SpringDoc 자동 추출은 `@ApiResponse(content = @Content(schema = @Schema(oneOf={...}, discriminatorProperty="...")))` 명시 의무 — 상세는 [`api-patterns.md §Swagger/OpenAPI 패턴`](api-patterns.md) 참조.

---

## N:M 매핑 엔티티 패턴

두 독립 마스터(또는 마스터·코드값) 사이의 진정한 N:M 관계를 매핑 엔티티로 분리한다. 단일 마스터의 1:N 구성요소 정규화 (`pump_cmbn_d` 같은 `_d`(상세) 패턴) 는 본 패턴 대상이 아니다. 권한메뉴 ANALYZE2 (2026-05-06) 결정.

### 사용 시점

- 두 독립 마스터·코드값 사이의 진정한 N:M 관계 (예: `menu_role_r` — 메뉴 ↔ 권한)
- 매핑 자체에 감사 메타 (`rgstr_*`) 또는 도메인 컬럼 추가 가능성이 1% 라도 존재하는 경우

### 매핑 엔티티 분리 패턴 (권장)

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

    // user_role 은 @EmbeddedId 내 @Enumerated 컬럼으로 관리 — 별도 @ManyToOne 불필요

    @CreatedDate
    @Column(name = "rgstr_dtm", nullable = false, updatable = false)
    private LocalDateTime rgstrDtm;

    @CreatedBy
    @Column(name = "rgstr_id", length = 50, nullable = false, updatable = false)
    private String rgstrId;

    public static MenuRole create(Menu menu, UserRole userRole) {
        return new MenuRole(new MenuRoleId(menu.getMenuId(), userRole), menu);
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

### 핵심 규칙

1. **테이블 suffix `_r`** — `naming.md` L61 N:M 관계 정의 직접 정합. `_p`(명세) 의 "태그 매핑" 인용 금지 (설정값 맥락 한정 — `tag_m` 의 메타데이터 매핑 등). `pump_interlock_p`·`ai_drvn_mod_p` 선례는 운전 규칙·시스템 상태 명세이므로 N:M 매핑 비교 부적절.
2. **클래스 네이밍** — `{도메인1}{도메인2}` 단순 결합 (`MenuRole`·`UserPump`·`RoleFacility` 등). **`Mapping`·`Relation`·`Map` 접미사 금지** (의미 중복). 복합 PK 클래스명 = `{엔티티명}Id` (`MenuRoleId`).
3. **`@ManyToMany` 직접 매핑 금지** — 매핑 엔티티 분리가 기본. `@ManyToMany` 는 BaseEntity 4 컬럼 / `AuditingEntityListener` / 매핑 단위 삭제 / 도메인 컬럼 추가 모두 불가능 → 본 프로젝트 패턴과 충돌.
4. **복합 PK — `@EmbeddedId` 기본** — 자연키 의미 명확 + 중복 INSERT 자동 차단 + forward 인덱스 자동. `@IdClass` 는 레거시 호환 또는 JPA 프레임워크가 `@EmbeddedId` 미지원 시 예외적 케이스. 단일 `BIGINT seq` PK + UNIQUE `(매핑컬럼1, 매핑컬럼2)` 는 매핑 행에 외부 FK 참조 또는 매핑 단위 갱신 이력 필요 시에만 도입 (`DOM_SEQ_BIGINT` + `@GeneratedValue(SEQUENCE)`).
5. **FK ON DELETE 정책**:
   - 마스터 FK (`menu_id` → `menu_m`): **CASCADE 기본** — 마스터 삭제 시 매핑 자동 정리. 도메인 안전 영역 (인터록·운전 모드 등 [`ot-integration.md §5`](ot-integration.md) 직결) 은 RESTRICT 검토를 PLAN 명시
   - 코드 마스터 FK (예: `user_role` → 향후 `user_role_c`): **RESTRICT 기본** — 코드값 삭제는 업무 규칙 위반이므로 CASCADE 금지
6. **BaseEntity 상속 정책**:
   - **INSERT/DELETE 전용 매핑** (UPDATE 시나리오 없음 — `menu_role_r` 사례) → **B안: BaseEntity 미상속**, `rgstr_dtm`·`rgstr_id` 만 직접 선언 + `@CreatedDate`·`@CreatedBy` + `@EntityListeners(AuditingEntityListener.class)`. `updt_*` 2컬럼은 데드 컬럼이므로 보유 금지
   - **매핑 단위 갱신 가능** (예: `valid_period`·`grant_reason` 등 변경 가능 컬럼 추가 시) → **A안: BaseEntity 4 컬럼 상속**
   - 두 후보 중 본 사례 시점의 PLAN 에서 명시 결정 의무
7. **`rgstr_*` NOT NULL 의무** — INSERT-only 구조에서 결측 경로 없음. `DOM_DTM` 기본 NULL 정책보다 더 엄격 적용. INSERT 시 `AuditingEntityListener` 자동 주입 보장.
8. **인덱스 정책**:
   - 복합 PK `(매핑컬럼1, 매핑컬럼2)` 자체가 forward 방향 (선행 컬럼) 인덱스 자동 제공
   - 카디널리티 순서 정합 — 복합 PK 컬럼 순서는 카디널리티 높은 컬럼 선행 ([`db/indexing-and-migration.md §1`](db/indexing-and-migration.md) 정책)
   - 역방향 단독 조회 빈번 + 행 수 수백 이상 → `CREATE INDEX CONCURRENTLY idx_{테이블}_{컬럼}` 추가
   - **카디널리티 작은 컬럼 단독 인덱스 미적용** — [`db/indexing-and-migration.md §3.4 DOM_YN`](db/indexing-and-migration.md) 정책 동일. 옵티마이저 Seq Scan 선호 시 도입 보류

### 도메인 룰 — N:M 매핑 vs 1:N 정규화 구분

- `_r` (관계): 두 독립 마스터·코드값 사이의 진정한 N:M (예: `menu_role_r`·향후 `user_facility_r`·`role_instrument_r`)
- `_d` (상세): 단일 마스터의 1:N 구성요소 정규화 (예: `pump_cmbn_d` — 펌프 조합 마스터의 상세 정규화). §외부 할당 PK 엔티티 패턴 의 `Persistable<ID>` 적용 등 별도 패턴

---

## BaseEntity ↔ BaseAuditResponseDto 매핑 패턴

`BaseEntity` 의 공통 메타 4컬럼(`rgstrDtm`·`updtDtm`·`rgstrId`·`updtId`) 을 응답 DTO 에 일관 노출하기 위해 `BaseAuditResponseDto` (응답 메타 전용 추상 부모, 위치: `common.dto.BaseAuditResponseDto`) 를 사용한다. 본 절은 엔티티 → DTO 매핑 책임 분담을 정의한다. DTO 측 정의·옵트인 정책·직렬화 정책은 [`api-patterns.md §BaseAuditResponseDto 패턴`](api-patterns.md) 가 SSOT.

### 책임 분담

| 책임 | 위치 |
|------|------|
| 메타 4컬럼 SSOT (DB 컬럼·Java 필드·AuditingEntityListener 자동 주입) | `BaseEntity` (`common.domain.BaseEntity`) |
| 응답 DTO 메타 4컬럼 노출·Swagger 스키마·`@JsonFormat` 직렬화 | `BaseAuditResponseDto` (`common.dto.BaseAuditResponseDto`) |
| 엔티티 → DTO 메타 매핑 헬퍼 | `BaseAuditResponseDto.applyAuditMeta(BaseEntity)` protected 메서드 |
| 자식 DTO 의 도메인 필드 매핑 + 메타 헬퍼 호출 | 자식 DTO 의 정적 팩토리 `from(Entity)` |

### 자식 엔티티 - DTO 매핑 표준

```java
// 엔티티 측 — 변경 없음 (BaseEntity 기존 상속 유지)
@Entity
@Table(name = "user_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity implements Persistable<String> {
    // ... 도메인 필드 ...
}

// 응답 DTO 측 — BaseAuditResponseDto 상속 + from() 에서 applyAuditMeta() 호출
@Getter
@Schema(description = "사용자 DTO")
public class UserDto extends BaseAuditResponseDto {

    @Schema(description = "사용자 ID")
    private String userId;
    // ... 도메인 필드 ...

    private UserDto() {}

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.userId = user.getUserId();
        // ... 도메인 필드 할당 ...
        dto.applyAuditMeta(user);   // 부모 메타 4컬럼 일괄 주입
        return dto;
    }
}
```

### 옵트인 vs 미적용 결정

- **옵트인 적용**: 마스터(`_m`) 응답 DTO — `UserDto`·`TagDto`·향후 `FacilityDto`·`InstrumentDto`·`MenuDto` 등 ([`api-patterns.md §BaseAuditResponseDto 패턴`](api-patterns.md) 의 적용 범위 표 참조)
- **미적용**: 도메인 룰 명세(`AiModeDto`)·실시간 통지(`PumpStateDto`)·요약(`MenuTreeDto`)·시계열(`_h`) 응답 DTO
- **immutable 이력(`_h`) 예외**: `BaseEntity` 미상속 이력 테이블(예: `ai_drvn_mod_h`) 의 응답 DTO 가 메타 노출이 필요하면 `BaseAuditResponseDto` 를 상속하지 않고 `rgstrDtm`·`rgstrId` 2컬럼만 DTO 에 직접 선언한다 (`db/indexing-and-migration.md §4.3` immutable 이력 테이블 예외 정렬)

### 빌더 패턴 정책

- `@SuperBuilder` 사용 금지 — 제네릭 빌더 체인 컴파일 오류·Jackson 역직렬화 충돌 등 알려진 엣지 케이스가 있고, 응답 DTO 는 출력 전용이라 빌더의 가독성 이득이 작다 (`coding-discipline.md §2` "일회성 코드를 위해 추상화 계층을 만들지 않는다" 적용)
- 자식 DTO 는 `private` 기본 생성자 + 정적 팩토리 `from(Entity)` 패턴 사용
- 메타 4컬럼 할당은 `applyAuditMeta(entity)` 단일 호출로 일관화 — 자식 DTO 가 4컬럼을 직접 setter 호출하거나 생성자 인자로 받는 방식 금지 (변경 시 모든 자식 DTO 가 영향)

---

## FK 보유 측 SSOT — 역방향 중복 컬럼 금지

두 엔티티 간 연관에서 **FK 를 보유하는 쪽이 SSOT** 다. 연관 대상 엔티티의 식별 정보를 반대쪽 테이블에 중복 컬럼으로 보유하는 것은 정규화 위반이며 **금지** 한다. 시설물응답DTO명세 ANALYZE1 안건 9 (2026-05-12) 결정.

### 원칙

- 한 엔티티 A 가 다른 엔티티 B 의 식별자 (PK·자연키) 를 FK 로 보유하면, A 가 (A↔B 연관의) SSOT
- A 의 식별자 (또는 B 가 알고 있는 A 의 속성) 를 B 에 역방향 중복 컬럼으로 등록 금지
- 응용 — 양방향 조회 필요 시 JPA `@OneToMany(mappedBy=...)` / 명시적 JOIN 쿼리로 해결하며 컬럼 중복 보유는 금지

### 적용 사례 (양방향 중복 금지 결정)

| FK 보유 측 (SSOT) | 폐기된 역방향 중복 컬럼 | 사이클 |
|----------|---------------|--------|
| `tag_m.instrument_id` (FK → `instrument_m`) | `pump_m.tag_nm` (역방향 — 폐기) | 시설물응답DTO명세 ANALYZE1, 2026-05-12 |

### 예외 조건

다음 두 사유에 한해 PLAN 단계에서 명시 + ANALYZE "## 가정 및 미해결 질문" 섹션에 기재 후 도입 허용:

1. **이력 스냅샷 컬럼** — FK 보유 측 (마스터) 가 변경 가능하고, 변경 시점 값 보존이 도메인 요구일 때 (예: 송수펌프 제어 시점의 `pump_nm` 스냅샷 이력)
2. **비정규화된 집계 캐시** — 1:N 집계가 빈번하고 조회 성능이 SLA 임계 (예: `EXPLAIN ANALYZE` 결과 200ms 초과) 인 경우. 일치 보장을 위한 트리거·이벤트 동기화 절차도 함께 명시

위 두 예외 외 모든 역방향 중복 보유는 [`ot-integration.md §5 ⚠️ 절대 금지`](ot-integration.md) 의 이중 소스 불일치 위험과 동격으로 처리 — 도메인 안전 위협.

### 자동 점검

REVIEW 단계에서 `wtp-backend-engineer` 가 신규 엔티티의 `*_id`·`*_nm`·`*_cd` 등 컬럼이 다른 테이블의 FK 와 양방향 중복 가능성을 보유하는지 점검. 의심 케이스 발견 시 ANALYZE 의 "## 가정 및 미해결 질문" 에 예외 사유 기재 여부 확인.

---

## 규칙 요약

- `@Getter`만 사용, `@Setter` 사용 금지
- 기본 생성자: `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
- 전체 필드 생성자: 필요 시 `PRIVATE` 접근 수준
- PK: `@GeneratedValue(strategy = GenerationType.UUID)` 기본
- **`@GeneratedValue`가 없는 외부할당 PK 엔티티는 `Persistable<ID>` 구현 필수** (`getId()` override, `isNew()`는 `BaseEntity` 위임)
- 공통 메타 필요 시 `BaseEntity` 상속, 이벤트 기능 필요 시 `DomainEventEntity` 상속
- 상태 변경은 `changeInfo(...)`, `changeGrpInfo(...)` 형태의 의도 드러나는 메서드로 처리
- null 또는 빈 문자열 처리 기준이 필요한 경우 변경 메서드 내부에서 일관되게 처리
- **여부(Y/N) 필드는 `YnType` enum + `@Enumerated(EnumType.STRING)` 으로 선언** (상세: §여부(Y/N) 필드 패턴)
- **단일 마스터 + 자식 종류 다수 패턴** (`facility_m`·`instrument_m` 등) 은 `@Inheritance(JOINED)` + `@DiscriminatorColumn` 사용. UUID 자동 생성 PK + `@UniqueConstraint` 로 이름값 비즈니스 식별자 보장 + 자식 PK 자동 상속 (상세: §JPA JOINED + DiscriminatorColumn 다형성 패턴)
- **N:M 매핑 엔티티 패턴** (`menu_role_r` 등) 은 매핑 엔티티 분리 + `@EmbeddedId` 복합 PK + `_r`(관계) suffix + `@ManyToMany` 직접 매핑 금지. INSERT/DELETE 전용 매핑은 `rgstr_*` 직접 선언 (BaseEntity 미상속) — `updt_*` 데드 컬럼 회피 (상세: §N:M 매핑 엔티티 패턴)
