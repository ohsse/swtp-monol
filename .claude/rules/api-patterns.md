# API 계층 코드 패턴

---

## 참조 문서 관계

| 문서 | 이 문서와의 관계 |
|------|----------------|
| [`entity-patterns.md`](entity-patterns.md) | 엔티티 ↔ DTO 설계 연계 — DTO `*Yn` 필드는 `YnType` 수신처 |
| [`exception-patterns.md`](exception-patterns.md) | Controller·Service 에서 던지는 `RestApiException` 규약 소비처 |
| [`naming.md`](naming.md) | Controller·Service·Repository·DTO 클래스 네이밍 기준 |
| `swtp/.claude/rules/dict/standard-data-domains.md` | DTO 필드 타입 결정 근거 — SQL·Java 타입 매핑 (ROOT) |

---

## Service 패턴

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetEventPublisher publisher;

    @Transactional
    public void saveDataset(DatasetUpsertDto dto) {
        // 쓰기 로직
    }
}
```

## Repository 패턴

```java
public interface DatasetRepository
        extends JpaRepository<Dataset, String>, DatasetCustomRepository {
}

public interface DatasetCustomRepository {
    List<DatasetDto> findList(DatasetSearchDto searchDto);
}
```

커스텀 조회 메서드명은 반환 목적이 드러나게 작성한다.
- 예: `findAllDatasets`, `findDatasetDtoById`, `groupingForDataset`

## DTO 패턴

```java
@Data
@NoArgsConstructor
@Schema(description = "데이터셋 DTO")
public class DatasetDto {
    @Schema(description = "데이터셋 ID", example = "ds-001")
    private String dsId;
}
```

- 검색 조건 DTO 의 공통 부모 추상화는 도입 보류 — 검색 조건 DTO 가 3건 이상 누적되어 공통화 이득이 명확해지면 별도 `/dev:analyze` 에서 결정한다 (`coding-discipline.md §2` "요청되지 않은 추상화 계층 금지" 적용).
- 응답 DTO 의 공통 메타(`rgstrDtm`·`updtDtm`·`rgstrId`·`updtId`) 표준화는 §BaseAuditResponseDto 패턴 참조.
- `@JsonTypeInfo` / `@JsonSubTypes`로 다형성 역직렬화가 필요한 경우에만 사용한다.
- DTO 의 `*Yn` 필드도 `YnType` enum 사용. 패턴: [entity-patterns.md §여부(Y/N) 필드 패턴](entity-patterns.md).

## DTO @Schema(implementation) 명시 패턴

DTO 필드 타입이 **사용자 정의 클래스** (enum 타입 / 참조형 DTO) 인 경우 `@Schema(... , implementation = X.class)` 를 반드시 명시한다. SpringDoc/Swagger 자동 추출기가 enum 타입 허용값·중첩 DTO 스키마를 frontend SPEC 명세 (`/dev:spec` 단계) 에 정확히 노출하기 위함이다.

### 적용 대상

| 필드 타입 | implementation 명시 |
|---------|------------------|
| 사용자 정의 enum (`UserRole`·`YnType`·`AiDrvnMode`·`TagMeasurementType` 등) | **필수** |
| 사용자 정의 참조형 DTO (`UserDto` 안의 중첩 `UserAddressDto` 등) | **필수** |
| `List<E>` · `Set<E>` element 가 사용자 정의 클래스 | **필수** (`@ArraySchema(schema = @Schema(implementation = E.class))`) |
| `Map<K, V>` value 가 사용자 정의 클래스 | **권고** (의무 미적용 — 사용 빈도 낮음, 향후 적용 사례 누적 시 의무 격상 검토) |

### 적용 제외 대상

| 필드 타입 | 사유 |
|---------|------|
| `String` · `int`/`Integer` · `long`/`Long` · `boolean`/`Boolean` 등 원시·래퍼 | Swagger 자동 인식 |
| `LocalDateTime` · `LocalDate` · `BigDecimal` · `UUID` | JSR-310 / 표준 라이브러리, Swagger 자동 인식 |
| `List<String>` · `Map<String, String>` 등 element 가 기본 타입 | 동일 사유 |

### 올바른 예

```java
// enum 타입
@Schema(description = "권한 역할", implementation = UserRole.class)
private UserRole userRole;

// enum 타입 (Y/N)
@Schema(description = "사용 여부", implementation = YnType.class)
private YnType useYn;

// 도메인 안전 enum 타입 (AI 운전 모드 — ot-integration.md §5)
@Schema(description = "AI 운전 모드 사용자 의도", implementation = AiDrvnMode.class)
private AiDrvnMode aiDrvnMode;

// 사용자 정의 참조형 DTO
@Schema(description = "주소 정보", implementation = UserAddressDto.class)
private UserAddressDto address;

// List<E> — element 가 사용자 정의 클래스
@ArraySchema(schema = @Schema(description = "권한 목록", implementation = UserRole.class))
private List<UserRole> roles;
```

### 위반 예 (금지)

```java
// implementation 누락 — frontend SPEC 추출 시 enum 타입 클래스 식별 불가
@Schema(description = "권한 역할", example = "ADMIN")
private UserRole userRole;
```

### `YnType` 패턴과의 양립

본 패턴은 [entity-patterns.md §여부(Y/N) 필드 패턴](entity-patterns.md) 의 `YnType` 정책 — `@Schema(allowableValues)` · `@Pattern` · `example` **중복 작성 금지** — 과 양립한다. `description` 옆에 `implementation = YnType.class` 만 추가하며, allowable values 는 `implementation` 으로 자동 노출되므로 별도 명시 불필요. `YnType` 의 DTO 어노테이션은 다음 형태가 표준이다:

```java
// 올바른 예: YnType 은 description + implementation 만 명시
@Schema(description = "사용 여부", implementation = YnType.class)
private YnType useYn;

// 잘못된 예: allowableValues / @Pattern / example 중복 — YnType 정책 위반
@Schema(description = "사용 여부", allowableValues = {"Y", "N"}, example = "Y")
private YnType useYn;
```

## BaseAuditResponseDto 패턴

`BaseEntity` 를 상속하는 마스터(`_m`) 엔티티의 응답 DTO 는 공통 메타 4컬럼(`rgstrDtm`·`updtDtm`·`rgstrId`·`updtId`) 노출을 `BaseAuditResponseDto` 추상 부모 클래스로 표준화한다.

### 적용 범위 — 선택적 옵트인

| 대상 | 적용 여부 | 사유 |
|-----|---------|------|
| 마스터(`_m`) 응답 DTO (`UserDto`·`TagDto`·향후 `FacilityDto`·`InstrumentDto`·`MenuDto` 등) | **상속 적용** | 운영자 감사 추적 표준화 |
| 도메인 룰 명세 응답 DTO (`AiModeDto` 등) | 적용 외 | `ot-integration.md §5` 모드 전환 명세 — 메타 노출 불필요 |
| 실시간 통지 응답 DTO (`PumpStateDto` 등) | 적용 외 | 통지성 — 메타 무관 |
| 요약·트리 응답 DTO (`MenuTreeDto` 등) | 적용 외 | 요약 응답 |
| 시계열(`_h`) 응답 DTO | 적용 외 | 시계열 행 자체에 4컬럼 메타가 의미 없음 |
| immutable 이력(`_h`) 응답 DTO 가 메타 필요 시 | **상속 금지** | `rgstrDtm`·`rgstrId` 2컬럼만 DTO 에 직접 선언 — `BaseEntity` 미상속 이력 테이블(`db/indexing-and-migration.md §4.3`) 정렬 |

### 표준 정의

```java
package com.mo.swtp.common.dto;

@Getter
@Schema(description = "응답 메타 공통 부모")
public abstract class BaseAuditResponseDto {

    @Schema(description = "등록 일시", example = "2026-05-11 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rgstrDtm;

    @Schema(description = "수정 일시", example = "2026-05-11 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updtDtm;

    @Schema(description = "등록자 ID", example = "system")
    private String rgstrId;

    @Schema(description = "수정자 ID", example = "system")
    private String updtId;

    /** BaseEntity 의 메타 4컬럼을 응답 DTO 에 적용한다. 자식 DTO 의 정적 팩토리에서 호출. */
    protected void applyAuditMeta(BaseEntity entity) {
        this.rgstrDtm = entity.getRgstrDtm();
        this.updtDtm = entity.getUpdtDtm();
        this.rgstrId = entity.getRgstrId();
        this.updtId = entity.getUpdtId();
    }
}
```

### 자식 DTO 사용 패턴

`@SuperBuilder` 는 사용하지 않는다 — 제네릭 빌더 체인 컴파일 오류·Jackson 역직렬화 충돌 등 알려진 엣지 케이스가 있고, 응답 DTO 는 출력 전용이라 빌더 패턴의 가독성 이득이 작다. 자식 DTO 의 정적 팩토리 `from(Entity)` 에서 부모의 `protected void applyAuditMeta(BaseEntity)` 를 호출하는 패턴이 표준이다.

```java
@Getter
@Schema(description = "사용자 DTO")
public class UserDto extends BaseAuditResponseDto {

    @Schema(description = "사용자 ID")
    private String userId;

    @Schema(description = "사용자 이름")
    private String userNm;

    private UserDto() {}

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.userId = user.getUserId();
        dto.userNm = user.getUserNm();
        dto.applyAuditMeta(user);   // 부모 4컬럼 일괄 주입
        return dto;
    }
}
```

### 상속 상한 — 2단 (마스터 다형성 한정 3단 예외)

응답 DTO 의 상속은 **2단**(`BaseAuditResponseDto → 도메인 DTO`) 이 기본이다. 단 `@Inheritance(JOINED)` + `@DiscriminatorColumn` 마스터 다형성 도메인 (`facility_m`·`instrument_m` 등) 에 한해 **3단** (`BaseAuditResponseDto → {도메인}Dto(abstract) → {자식}Dto`) 을 허용한다 (ROOT [`coding-discipline.md §2.1`](../../../.claude/rules/coding-discipline.md) DTO 상속 깊이 3단 초과 자동 지적 룰의 응답 DTO 특화 정렬).

#### 3단 허용 조건 (3 건 모두 충족)

1. 추상 부모 `{도메인}Dto` 에 `@JsonTypeInfo(use=NAME, include=EXISTING_PROPERTY, property="{discriminator}", visible=true)` + `@JsonSubTypes` + `@Schema(oneOf={자식DTO.class, ...}, discriminatorProperty="{discriminator}")` 명시 의무
2. 자식 전용 필드는 자식 DTO 에만 선언 — 부모 DTO 에 `instanceof` 분기로 채우는 방식 **금지** (자식 종류별 응답 스키마 분기 의무)
3. 4단 이상은 별도 `/dev:analyze` 재진입

3단 적용 사례: `BaseAuditResponseDto → FacilityDto(abstract) → DwtDto/PwtfDto/RsvDto/PrsfDto/PointDto` · `BaseAuditResponseDto → InstrumentDto(abstract) → PumpDto/...` (시설물응답DTO명세 ANALYZE1, 2026-05-12).

### 직렬화 정책

- `LocalDateTime` 필드에는 `@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")` 초 단위 SSOT 적용
- 근거: `BaseEntity.rgstrDtm`·`updtDtm` 은 `DOM_DTM` 표준 데이터 도메인 (`LocalDateTime`, 초 단위 정밀도). `ot-integration.md §5` 의 모드 전환·제어 로그 등 OT 감사 추적은 초 단위 식별이 필요. 분 단위 잘라내기는 정밀도 손실
- 분 단위 표시가 필요한 경우 frontend 가 표시 시점에 잘라낸다 (backend 가 직렬화 정밀도를 낮추지 않는다)

### Swagger 노출

- 부모의 `@Schema` 4건이 자식 DTO 의 응답 스키마에 자동 노출된다
- 자식 DTO 의 `@Schema(description=...)` 은 자식 전용 필드에만 작성하며, 부모 필드를 재선언하지 않는다
- frontend SPEC(`/dev:spec`) 자동 추출은 부모·자식 필드를 합쳐 SPEC 의 "DTO 스키마" 표에 노출한다

### 적용 시 변경 이력 (frontend SPEC 영향)

기존 응답 DTO 를 `BaseAuditResponseDto` 옵트인으로 마이그레이션하면 응답 필드 추가 또는 직렬화 포맷 변경이 발생한다. 영향이 있는 슬러그의 `swtp/frontend/docs/api-specs/{슬러그}/SPEC{N+1}.md` 를 `/dev:spec` 단계에서 갱신한다. 본 패턴 자체를 위한 별도 SPEC 슬러그는 만들지 않는다 (응답 DTO 표준화는 공통 인프라).

## Swagger/OpenAPI 패턴

```java
@Tag(name = "03. 데이터셋 관리")
@Operation(summary = "데이터셋 목록 조회")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "401", description = "인증 실패"),
    @ApiResponse(responseCode = "403", description = "권한 없음"),
    @ApiResponse(responseCode = "404", description = "리소스 없음"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
})
```

- `@Tag` 이름은 정렬 순서가 필요하면 번호 접두사를 사용한다.
- `summary`는 한 줄 목적 중심으로 작성한다.
- `description`은 조건/제약/예외가 필요한 경우에만 짧게 작성한다.
- 동일 의미의 필드는 문서 전반에서 같은 이름을 사용한다. (예: `userId`와 `memberId` 혼용 금지)
- 자식 다형성 응답 DTO (3단 상속 패턴 — §BaseAuditResponseDto 패턴 §상속 상한) 는 `@ApiResponse(content = @Content(schema = @Schema(oneOf={자식DTO.class, ...}, discriminatorProperty="{discriminator}")))` 명시 의무 — `ResponseEntity<CommonResponseDto<{추상부모}>>` 시그니처가 SpringDoc 정적 분석에서 추상 클래스 스키마만 노출하므로 `oneOf` 명시 부재 시 `/dev:spec` SPEC 산출물에 자식 전용 필드 누락 (시설물응답DTO명세 ANALYZE1, 2026-05-12).
