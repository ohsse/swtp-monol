---
status: approved
created: 2026-05-12
updated: 2026-05-12
---
# 시설물·계측기 자식별 응답 DTO 분리 + Pump.tagNm 폐기 + 고아 자산 백지화

## 목적

`Facility` 마스터-자식 구조 (JPA `@Inheritance(JOINED)` + `@DiscriminatorColumn`) 에 정합한 자식별 응답 DTO 구조를 도입하여 부모 DTO 가 자식 전용 필드를 노출하는 모순을 해소한다. 동일 패턴을 `Instrument` 의 `Pump` 자식에도 적용한다. 사용자 도메인 정정에 따라 `Pump.tagNm` 양방향 중복 컬럼을 폐기하고, pump+AI 백지화 사이클 1 (2026-05-12 직전 커밋) 의 즉시 부수 효과로 발생한 고아 자산 (`FacilityListDto`·`FacilityListService`·관련 Repository 메서드·테스트) 을 동일 백지화 흐름의 연장으로 함께 백지화한다.

## 배경

- ANALYZE1 (`docs/analyze/20260512/시설물응답DTO명세/ANALYZE1.md`) 5인 회의 9 안건 결론. 안건 4 는 Round 2 사용자 정정으로 재정정 (현 `FacilityListDto` 유지 → 백지화 + 향후 reference 패턴 재도입).
- 룰 갱신 5건 완료 — `api-patterns.md §상속 상한` 마스터 다형성 한정 3단 예외 + `@Schema(oneOf=..)` 의무, `entity-patterns.md` §응답 DTO 매핑 패턴 + §FK 보유 측 SSOT, `standard-terms.md` `tag_nm` 비고 갱신.
- 사실 확인 결과 (PLAN 진입 직전 grep):
  - `FacilityController.findAllFacilities()` 가 이미 `List<FacilityDto>` 다형 응답 + `FacilitySearchDto.facilityTypeCd`·`useYn` 외부 파라미터 필터링 → reference 패턴 이미 구현
  - `FacilityListService.findFacilitiesHavingDwtChild()` 호출자 0건 (pump+AI 백지화로 `PumpControlAnalysisController` 삭제됨) — 기능 중복 + 고아 상태
  - `DwtStatusService`/`DwtStatusController` 는 `pump_*`·`ai_*` 의존 0건 → 본 사이클 영향 외 (보존)
  - `FacilityCustomRepository.findFirstChildByParentIdAndType()` 는 `DwtStatusService` 가 사용 → 보존
  - `FacilityCustomRepository.findFacilities()` 는 `FacilityService.findAllFacilities()` 가 사용 → 보존
  - 백지화 대상은 정확히 `findFacilitiesHavingDwtChild` 메서드/구현 + `FacilityListService` + `FacilityListDto` + `FacilityListServiceTest` **5건 한정**

## 범위

### In Scope

1. **Facility 응답 DTO 자식별 분리 (api 모듈)**:
   - `FacilityDto` (구상) → abstract 변경 + `BaseAuditResponseDto` 상속
   - 자식 5종 DTO 신규 — `DwtDto`·`PwtfDto`·`RsvDto`·`PrsfDto`·`PointDto`
   - Jackson 다형성 + Swagger `@Schema(oneOf=..)` 적용

2. **Instrument 응답 DTO 부분 분리 (api 모듈, 신규 패키지)**:
   - `api/src/main/java/com/mo/swtp/instrument/dto/` 디렉토리 신설 (현재 부재)
   - `InstrumentDto` (abstract) + `PumpDto` 2건 신설
   - Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter 5종 skeleton 은 후속 사이클 이연 (ANALYZE 안건 6)

3. **`Pump.tagNm` 폐기 (common 모듈 + DDL)**:
   - `common/.../instrument/domain/Pump.java` 의 `tagNm` 필드 + 생성자 인자 + Javadoc 제거
   - DDL `common/src/main/resources/db/init/V8_6__pump_m_drop_tag_nm.sql` 신설
   - DBA 무중단 절차 (즉시 DROP, COMMENT 자동 정리)

4. **고아 자산 백지화 (api 모듈)**:
   - `api/src/main/java/com/mo/swtp/facility/dto/FacilityListDto.java` 삭제
   - `api/src/main/java/com/mo/swtp/facility/service/FacilityListService.java` 삭제
   - `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepository.java` 의 `findFacilitiesHavingDwtChild(List<FacilityType>)` 시그니처 제거
   - `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepositoryImpl.java` 의 구현 메서드 제거 (L25-47)
   - `api/src/test/java/com/mo/swtp/facility/service/FacilityListServiceTest.java` 삭제

5. **단위 테스트 보강**:
   - `FacilityDtoSerializationTest` 신규 — Jackson 다형성 직렬화 검증 (DwtDto 에 minReqPrsr 포함, PwtfDto 에 미포함)
   - `PumpDtoSerializationTest` 신규 — equipTypeCd discriminator 검증
   - `FacilityServiceTest` 갱신 — 자식 타입별 `findFacilityDto()` 응답 검증 추가
   - `DwtStatusServiceTest` 영향 없음 확인 (PASS 유지)

### Out of Scope

- Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter 5종 instrument 자식 DTO 신설 (skeleton 자식, 후속 사이클)
- `List<FacilityDto>` 다형 응답 신규 구현 — `FacilityController.findAllFacilities()` 가 이미 동일 패턴이므로 추가 작업 없음 (응답 타입은 abstract 부모로 유지, 자식 인스턴스 다형 직렬화)
- `FacilityListService` 가 제공하던 "DWT 자식 1건 이상 보유 필터" 기능 — 사용처 0건이라 본 사이클 백지화. 향후 재등장 시 별도 ANALYZE 진입
- `DwtStatusController/Service/Dto` 변경 — 백지화 영향권 외
- `FacilityListDto` 의 reference 패턴 재구현 (`FacilitySearchDto` 확장 등) — 향후 사용처 재등장 시

## 구현 방향

### 1) Facility 자식별 응답 DTO 구조

```
common/src/main/java/com/mo/swtp/common/dto/BaseAuditResponseDto.java  (기존, 변경 없음)
    ↑ extends
api/src/main/java/com/mo/swtp/facility/dto/
  ├─ FacilityDto.java           ← abstract 로 리팩토링
  │                                @JsonTypeInfo(use=NAME, include=EXISTING_PROPERTY,
  │                                              property="facilityTypeCd", visible=true)
  │                                @JsonSubTypes({Dwt,Pwtf,Rsv,Prsf,Point})
  │                                @Schema(oneOf={...}, discriminatorProperty="facilityTypeCd")
  │                                static FacilityDto from(Facility) — switch 패턴 매칭
  ├─ DwtDto.java                 ← 신규, + minReqPrsr 자식 전용 필드
  ├─ PwtfDto.java                ← 신규, 부모 필드만
  ├─ RsvDto.java                 ← 신규, 부모 필드만
  ├─ PrsfDto.java                ← 신규, 부모 필드만
  └─ PointDto.java               ← 신규, 부모 필드만
```

**부모 추상 클래스 핵심**:

```java
@Getter
@Schema(description = "시설 응답 DTO (추상 부모)",
        oneOf = {DwtDto.class, PwtfDto.class, RsvDto.class, PrsfDto.class, PointDto.class},
        discriminatorProperty = "facilityTypeCd")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
              property = "facilityTypeCd", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DwtDto.class,   name = "DWT"),
        @JsonSubTypes.Type(value = PwtfDto.class,  name = "PWTF"),
        @JsonSubTypes.Type(value = RsvDto.class,   name = "RSV"),
        @JsonSubTypes.Type(value = PrsfDto.class,  name = "PRSF"),
        @JsonSubTypes.Type(value = PointDto.class, name = "POINT")
})
public abstract class FacilityDto extends BaseAuditResponseDto {
    // 부모 공통 필드 (facilityId·facilityNm·facilityTypeCd·parentFacilityId·dispOrd·mainYn·useYn)
    // @Setter 없음 — 자식 정적 팩토리에서 protected 헬퍼로 주입

    public static FacilityDto from(Facility facility) {
        return switch (facility) {
            case DistributionWaterTank dwt -> DwtDto.from(dwt);
            case PurifiedWaterTank pwtf -> PwtfDto.from(pwtf);
            case Reservoir rsv -> RsvDto.from(rsv);
            case PressureBoosterStation prsf -> PrsfDto.from(prsf);
            case SensorPoint point -> PointDto.from(point);
            default -> throw new IllegalStateException(
                    "Unknown facility subtype: " + facility.getClass().getName());
        };
    }

    protected void applyCommonFields(Facility facility) {
        this.facilityId = facility.getFacilityId();
        this.facilityNm = facility.getFacilityNm();
        this.facilityTypeCd = facility.getFacilityType();
        this.parentFacilityId = facility.getParentFacilityId();
        this.dispOrd = facility.getDispOrd();
        this.mainYn = facility.getMainYn();
        this.useYn = facility.getUseYn();
        applyAuditMeta(facility);   // BaseAuditResponseDto 의 메타 4컬럼 주입
    }
}
```

**자식 DTO 표준 (DwtDto 사례)**:

```java
@Getter
@Schema(description = "배수지 응답 DTO")
public class DwtDto extends FacilityDto {

    @Schema(description = "최소 요구 압력 (kgf/cm²)", example = "2.5")
    private BigDecimal minReqPrsr;

    private DwtDto() {}

    public static DwtDto from(DistributionWaterTank dwt) {
        DwtDto dto = new DwtDto();
        dto.applyCommonFields(dwt);
        dto.minReqPrsr = dwt.getMinReqPrsr();
        return dto;
    }
}
```

부모 공통 필드 setter 부재로 자식 DTO 가 부모 필드를 직접 할당할 수 없는 문제는 `applyCommonFields(Facility)` protected 헬퍼로 해결 — `BaseAuditResponseDto.applyAuditMeta(BaseEntity)` 와 동일 패턴.

### 2) Instrument 자식별 응답 DTO (Pump 만)

```
api/src/main/java/com/mo/swtp/instrument/dto/   ← 신규 디렉토리
  ├─ InstrumentDto.java          ← abstract, equipTypeCd discriminator
  │                                @JsonSubTypes({@Type(PumpDto.class, name="PUMP")})
  │                                static InstrumentDto from(Instrument) — switch
  └─ PumpDto.java                ← 신규, + ratedHead/ratedFlwrt/oprtngType
```

`InstrumentDto.from(Instrument)` 의 switch 패턴은 `case Pump p -> PumpDto.from(p)` + `default -> throw` 로 처리. Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter 자식은 본 사이클 미신설이므로 `default` 분기에서 `IllegalStateException` 발생. **현재 instrument 단건 조회 호출자 부재** (`InstrumentController` 자체 부재) 라서 런타임 영향 없음. 후속 사이클에서 5종 DTO 추가 시 분기 확장.

### 3) Pump.tagNm 폐기

**common 모듈 — Pump 엔티티 변경**:

```java
// 기존
public class Pump extends Instrument {
    @Column(name = "rated_head", nullable = false, precision = 15, scale = 4)
    private BigDecimal ratedHead;

    @Column(name = "rated_flwrt", nullable = false, precision = 15, scale = 4)
    private BigDecimal ratedFlwrt;

    @Column(name = "tag_nm", length = 50)   // ← 제거
    private String tagNm;                    // ← 제거

    @Enumerated(EnumType.STRING)
    @Column(name = "oprtng_type_cd", nullable = false, length = 20)
    private PumpOprtngType oprtngType;

    public static Pump create(... String tagNm, ...) {   // ← 인자 제거
        ...
    }
}
```

`create(...)` 정적 팩토리의 `tagNm` 인자 + 생성자 인자 + `this.tagNm = tagNm` 모두 제거. Javadoc 도 갱신.

**DDL — V8_6__pump_m_drop_tag_nm.sql 신설** (DBA 답변 기반):

```sql
-- pump_m.tag_nm 컬럼 폐기 — 양방향 중복 제거 (시설물응답DTO명세 ANALYZE1, 2026-05-12)
-- 사유: tag_m.instrument_id FK 가 (계측기 ↔ 태그) 연관의 SSOT. pump_m.tag_nm 역방향 컬럼은
-- entity-patterns.md §FK 보유 측 SSOT — 역방향 중복 컬럼 금지 위반. api 사용처 0건 확인.
-- pump_m 마스터 행수 50건 미만 + ACCESS EXCLUSIVE 락은 메타데이터 변경만 → 즉시 DROP 안전.
-- COMMENT 자동 정리 (PostgreSQL DROP COLUMN 동작).

ALTER TABLE pump_m DROP COLUMN tag_nm;
```

### 4) 고아 자산 백지화

`FacilityListDto`/`FacilityListService`/`FacilityListServiceTest` 3 파일 삭제. `FacilityCustomRepository`/`FacilityCustomRepositoryImpl` 의 `findFacilitiesHavingDwtChild` 메서드만 제거 (다른 메서드 보존). import 정리.

### 5) Controller·Service 영향

- `FacilityController` 시그니처 변경 없음 — `ResponseEntity<CommonResponseDto<FacilityDto>>` 유지. abstract 부모 응답 시 Jackson 다형성으로 자식 인스턴스 직렬화. SpringDoc 정적 분석 보강을 위해 `@ApiResponse(content = @Content(schema = @Schema(oneOf={DwtDto.class, ...}, discriminatorProperty="facilityTypeCd")))` 명시 추가
- `FacilityService.findFacilityDto(facilityId)` + `findAllFacilities(searchDto)` 변경 없음 — `FacilityDto.from(facility)` 호출 그대로. switch 분기는 `FacilityDto.from()` 내부로 이동
- `DwtStatusService` 변경 없음 — `instrument`·`tag`·`raw` 도메인만 의존

## 도메인 모델

| 클래스 / 테이블 | 역할 | 주요 필드 | 변경 유형 |
|---------------|------|---------|---------|
| `api.facility.dto.FacilityDto` | 시설 응답 추상 부모 DTO | facilityId·facilityNm·facilityTypeCd·parentFacilityId·dispOrd·mainYn·useYn + 메타 4 | 구상 → abstract 리팩토링, BaseAuditResponseDto 상속 추가, 자식 전용 minReqPrsr 제거, Jackson 다형성 어노테이션 추가 |
| `api.facility.dto.DwtDto` | 배수지 응답 DTO | (부모 상속) + minReqPrsr | 신규 |
| `api.facility.dto.PwtfDto` | 정수지 응답 DTO | (부모 상속) | 신규 |
| `api.facility.dto.RsvDto` | 저수지 응답 DTO | (부모 상속) | 신규 |
| `api.facility.dto.PrsfDto` | 가압장 응답 DTO | (부모 상속) | 신규 |
| `api.facility.dto.PointDto` | 센서포인트 응답 DTO | (부모 상속) | 신규 |
| `api.instrument.dto.InstrumentDto` | 계측기 응답 추상 부모 DTO | instrumentId·instrumentNm·facility(또는 facilityId)·equipTypeCd·dispOrd·useYn + 메타 4 | 신규 (디렉토리 자체 신설) |
| `api.instrument.dto.PumpDto` | 펌프 응답 DTO | (부모 상속) + ratedHead·ratedFlwrt·oprtngType | 신규 |
| `common.instrument.domain.Pump` | 펌프 엔티티 | tagNm 필드 제거 (양방향 중복) | 필드·생성자·정적 팩토리 시그니처 변경 |
| `pump_m.tag_nm` (DB 컬럼) | 폐기 | — | DROP COLUMN |

## DB 설계 변경

- 신규 테이블/컬럼: 없음
- 폐기 컬럼: `pump_m.tag_nm` (DOM_TAG_NM_50, NULL 허용)
- 신규 인덱스/파티션: 없음
- DDL 파일: `common/src/main/resources/db/init/V8_6__pump_m_drop_tag_nm.sql`
- 무중단 마이그레이션 전략: `indexing-and-migration.md §2` 적용. `pump_m` 마스터 행수 50건 미만 + api 사용처 0건이라 섀도우 컬럼 방식 불필요 — 단일 `ALTER TABLE pump_m DROP COLUMN tag_nm` 즉시 실행 안전 (`ACCESS EXCLUSIVE` 락 메타데이터 변경만). COMMENT 자동 정리 (PostgreSQL `DROP COLUMN` 동작). 롤백 시 `V8_7__pump_m_restore_tag_nm.sql` 별도 파일 패턴 (본 사이클은 미작성).

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령 / 테스트 / 조회 |
|------|----------------------|
| `FacilityDto` abstract + `BaseAuditResponseDto` 상속 | grep `^public abstract class FacilityDto extends BaseAuditResponseDto` api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java 매칭 1건 |
| 자식 DTO 5종 신설 + 부모 상속 | `ls api/src/main/java/com/mo/swtp/facility/dto/DwtDto.java PwtfDto.java RsvDto.java PrsfDto.java PointDto.java` 5건 + grep `extends FacilityDto` 매칭 5건 |
| 부모 DTO 자식 전용 필드 미보유 | grep `minReqPrsr\|ratedHead\|ratedFlwrt\|oprtngType` api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java 매칭 0건 |
| Jackson 다형성 어노테이션 적용 | grep `EXISTING_PROPERTY.*facilityTypeCd` FacilityDto.java 매칭 1건 + grep `discriminatorProperty.*facilityTypeCd` 매칭 1건 |
| 자식별 Jackson 직렬화 동작 | `FacilityDtoSerializationTest` 신규 단위 테스트 — DwtDto 직렬화 결과에 `"facilityTypeCd":"DWT"` + `"minReqPrsr":2.5` 포함, PwtfDto 직렬화 결과에 `minReqPrsr` 미포함, 5종 자식 모두 검증 |
| InstrumentDto + PumpDto 신설 | `ls api/src/main/java/com/mo/swtp/instrument/dto/InstrumentDto.java PumpDto.java` 2건 + PumpDto 의 ratedHead·ratedFlwrt·oprtngType 필드 grep 매칭 |
| Pump.tagNm 폐기 완료 | grep `tagNm\|tag_nm` common/src/main/java/com/mo/swtp/instrument/domain/Pump.java 매칭 0건 + 같은 파일의 `create` 정적 팩토리 인자에 tagNm 부재 |
| DDL V8_6 신설 + 즉시 DROP 안전 | `ls common/src/main/resources/db/init/V8_6__pump_m_drop_tag_nm.sql` 존재 + 내용에 `ALTER TABLE pump_m DROP COLUMN tag_nm` 매칭 |
| Controller `@ApiResponse(content=@Content(schema=@Schema(oneOf={...})))` 명시 | grep `oneOf.*DwtDto.class` api/src/main/java/com/mo/swtp/facility/web/FacilityController.java 매칭 + 단건/목록 두 엔드포인트 |
| 고아 자산 백지화 완료 | `ls api/src/main/java/com/mo/swtp/facility/dto/FacilityListDto.java` 부재 + `ls api/src/main/java/com/mo/swtp/facility/service/FacilityListService.java` 부재 + `ls api/src/test/java/com/mo/swtp/facility/service/FacilityListServiceTest.java` 부재 + grep `findFacilitiesHavingDwtChild` api/src 매칭 0건 |
| DwtStatusService 영향 없음 | `./gradlew.bat :api:test --tests DwtStatusServiceTest` PASS |
| FacilityService 영향 검증 | `./gradlew.bat :api:test --tests FacilityServiceTest` PASS 유지. 자식 타입별 `findFacilityDto()` 응답이 적합한 자식 DTO 반환하는 신규 테스트 케이스 추가 PASS |
| common·api·scheduler 전체 빌드 | `./gradlew.bat :common:build :api:build :scheduler:build` BUILD SUCCESSFUL |
| `./gradlew.bat clean build` 전체 PASS | BUILD SUCCESSFUL |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `FacilityDto.from(Facility)` 의 switch 패턴 매칭이 `coding-discipline.md §2` 단순성 우선과 정합 — `Facility.toDto()` 추상 메서드 패턴보다 권장 | 결정 | switch 패턴 채택. `default → throw IllegalStateException` 으로 모든 자식 매칭 강제 (컴파일러 exhaustiveness 미보장 — sealed class 미사용이므로 runtime 검증) |
| `InstrumentDto` 가 부모 `Facility` 엔티티 참조를 어떻게 직렬화할지 — `facilityId` 만 노출 vs `FacilityDto` 중첩 | 결정 | `facilityId` 문자열만 노출. 중첩 시 응답 페이로드 비대 + frontend 가 시설 정보 별도 조회 가능. `Instrument.getFacility().getFacilityId()` 호출로 단순화 |
| `PointDto` 의 SCADA 자동 생성 시설 특성상 단건 조회 API 응답 대상에 포함되는가 vs `default` 분기로 처리 | 결정 | `FacilityDto.from()` switch 분기에 포함. 등록 API 는 대상 외 (`FacilityUpsertDto` 의 `@JsonSubTypes` 에 POINT 부재) 이나 조회 응답은 모든 자식 종류 다형성 처리 |
| `useYn` 필드 응답 노출 정책 — 모든 자식 DTO 일관 노출 (부모 추상 필드) | 결정 | 모든 자식 DTO 일관 노출 (부모 abstract 클래스 `useYn` 필드) |
| `InstrumentDto.from(Instrument)` 의 `default` 분기 동작 — 본 사이클은 PUMP 외 자식 (Valve 등) 매칭 시 IllegalStateException. 후속 사이클까지 임시 상태 | 가정 | `InstrumentController` 부재로 런타임 호출 없음 — 후속 사이클 5종 추가 시 분기 확장. PLAN 시점 안전 |
| `FacilityDto` 부모 필드들이 자식 DTO 에서 보이려면 부모에서 `@Getter` + private 필드 + `applyCommonFields(Facility)` protected 헬퍼 필요. 그러나 `BaseAuditResponseDto` 와 동일한 패턴이라 일관성 확보 | 결정 | 부모 abstract 클래스에 private 필드 + protected `applyCommonFields(Facility)` 메서드 도입 (`BaseAuditResponseDto.applyAuditMeta(BaseEntity)` 와 동일 패턴) |
| `FacilityListServiceTest` 삭제 시 테스트 커버리지 감소 — `FacilityServiceTest` 의 기존 케이스로 흡수 가능한가 | 결정 | `FacilityListService.findFacilitiesHavingDwtChild()` 자체가 백지화되므로 테스트도 백지화. 본 메서드의 비즈니스 기능 (DWT 자식 보유 필터링) 은 향후 reference 패턴 재구현 시 별도 테스트 작성 |

## 제외 사항

- Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter 5종 instrument 자식 응답 DTO 신설 — 자식 전용 필드 0건 skeleton 상태라 분리 실익 부재 (ANALYZE 안건 6 결론)
- `FacilityListDto` 기능 재구현 (reference 패턴 `FacilitySearchDto` 확장 등) — 향후 사용처 재등장 시 별도 ANALYZE
- `PumpController` 신설 — `Pump` 단건/목록 조회 API 자체가 부재한 상태라 본 사이클 범위 외 (향후 도입 시 자체 ANALYZE)
- frontend SPEC 전파 — `/dev:spec` 단계가 본 사이클 commit 후 별도 호출
- `ai_drvn_mod_p`/`pump_predc_h`/`pump_ctrl_h` 등 백지화 자산의 사용처 잔존 점검 — pump+AI 백지화 사이클 1 의 책임 (본 사이클 외)

## 부록: 도메인/DB 검토 결과

ANALYZE1.md 의 5인 회의에서 9 안건 모두 검토 완료. 본 PLAN 의 도메인 모델·DB 변경 결정은 ANALYZE 결론을 그대로 인용.

- **wtp-domain-expert (ANALYZE 안건 4·6·9)**: 블로커 0건. 인터록·운전 모드·이력 기록·알람 4단계 모두 비해당 — 응답 DTO 분리 + 양방향 중복 폐기만 수행
- **wtp-dba-reviewer (ANALYZE 안건 8)**: 블로커 0건, 참고 1건 (V 번호 채번 주의 — V8_6 채택). DDL 즉시 DROP 안전, COMMENT 자동 정리, 롤백 절차 V8_7 별도 파일 권고
- **wtp-backend-engineer (ANALYZE 안건 1·2·5·9)**: 블로커 0건. Jackson `EXISTING_PROPERTY` 채택, abstract 응답 Controller 안전 + `@ApiResponse(oneOf=..)` 명시 의무
- **wtp-glossary-manager (ANALYZE 안건 3·7·8)**: 블로커 0건. 자식 DTO 약어 기반 네이밍, `tag_nm` 표준 용어 자체 유지

PLAN 단계에서 추가 검토 게이트 호출 불요 (ANALYZE 5인 회의가 도메인·DB 검토를 완료).

## 예상 산출물

- [태스크](../../../tasks/20260512/시설물응답DTO명세/TASK1.md)
