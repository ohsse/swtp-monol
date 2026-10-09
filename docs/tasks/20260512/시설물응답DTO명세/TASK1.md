---
status: completed
created: 2026-05-12
updated: 2026-05-13
---
# 시설물·계측기 자식별 응답 DTO 분리 + Pump.tagNm 폐기 + 고아 자산 백지화

## 관련 계획
- [계획안](../../../plan/20260512/시설물응답DTO명세/PLAN1.md)

## Phase

### Phase 1: Pump 엔티티 + DDL — `tag_nm` 폐기 (common 모듈)

- [x] `common/src/main/resources/db/init/V8_6__pump_m_drop_tag_nm.sql` 신규 작성 → 검증: 파일 존재 + 내용에 ALTER TABLE pump_m DROP COLUMN tag_nm 매칭 + 폐기 사유 주석 (양방향 중복 + tag_m.instrument_id FK SSOT) 포함
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` 의 `tagNm` 필드 + `@Column(name="tag_nm", length=50)` 제거 → 검증: grep tagNm Pump.java 매칭 0건
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` 의 `create(...)` 정적 팩토리 시그니처에서 `String tagNm` 인자 제거 + 본문의 `this.tagNm = tagNm` 제거 + private 생성자 인자 제거 + Javadoc 갱신 → 검증: grep "String tagNm" Pump.java 매칭 0건 + create 시그니처 인자 6개 (instrumentNm·facility·dispOrd·ratedHead·ratedFlwrt·oprtngType)
- [x] `common/src/test/java/com/mo/swtp/instrument/domain/PumpSelfColumnsTest.java` 호출처 정렬 — `Pump.create()` 호출 3건 (rated_head·rated_flwrt·oprtng_type null 검증) 의 `tagNm` 인자 제거 + `tag_nm` 전용 테스트 메서드 `create_는_tag_nm_이_null_이어도_정상_생성된다` 삭제 + 클래스 Javadoc 자식 컬럼 3건으로 갱신 → 검증: grep tagNm PumpSelfColumnsTest.java 매칭 0건. Pump.create() 시그니처 변경의 필수 부수 효과 (`coding-discipline.md §3.1` 본인 수정으로 불필요해진 코드 즉시 제거)
- [x] `./gradlew.bat :common:build` 실행 → 검증: BUILD SUCCESSFUL + QPump 재생성 시 tagNm 필드 부재 확인

### Phase 2: FacilityDto 추상화 + 자식 5종 신설 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` abstract 리팩토링 → 검증: grep "^public abstract class FacilityDto extends BaseAuditResponseDto" 매칭 1건. Jackson `@JsonTypeInfo(use=NAME, include=EXISTING_PROPERTY, property="facilityTypeCd", visible=true)` + `@JsonSubTypes` 5종 + `@Schema(oneOf=..., discriminatorProperty="facilityTypeCd")` 적용. 자식 전용 `minReqPrsr` 필드 제거. 공통 필드는 private + protected applyCommonFields(Facility) 헬퍼 + 정적 팩토리 from(Facility) switch 패턴 매칭 (5 자식 + default throw IllegalStateException)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/DwtDto.java` 신규 작성 → 검증: 파일 존재 + extends FacilityDto + minReqPrsr 필드 보유 + private 생성자 + static from(DistributionWaterTank) 정적 팩토리 + applyCommonFields 호출
- [x] `api/src/main/java/com/mo/swtp/facility/dto/PwtfDto.java` 신규 작성 → 검증: 파일 존재 + extends FacilityDto + 부모 필드만 (자식 전용 0건) + static from(PurifiedWaterTank)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/RsvDto.java` 신규 작성 → 검증: 파일 존재 + extends FacilityDto + 부모 필드만 + static from(Reservoir)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/PrsfDto.java` 신규 작성 → 검증: 파일 존재 + extends FacilityDto + 부모 필드만 + static from(PressureBoosterStation)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/PointDto.java` 신규 작성 → 검증: 파일 존재 + extends FacilityDto + 부모 필드만 + static from(SensorPoint)
- [x] `api/src/test/java/com/mo/swtp/facility/dto/FacilityDtoSerializationTest.java` 신규 작성 → 검증: 5 자식 직렬화 검증 케이스 (DwtDto 결과에 "facilityTypeCd":"DWT" + "minReqPrsr" 포함 / PwtfDto 결과에 "minReqPrsr" 미포함 / 5종 모두 응답 스키마 검증). ./gradlew.bat :api:test --tests FacilityDtoSerializationTest PASS

### Phase 3: Instrument DTO 디렉토리 + InstrumentDto/PumpDto 신설 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/instrument/dto/InstrumentDto.java` 신규 작성 → 검증: 파일 존재 + abstract + extends BaseAuditResponseDto + Jackson `@JsonTypeInfo(property="equipTypeCd")` + `@JsonSubTypes({@Type(PumpDto.class, name="PUMP")})` + `@Schema(oneOf={PumpDto.class}, discriminatorProperty="equipTypeCd")` + protected applyCommonFields(Instrument) 헬퍼 + static from(Instrument) switch 패턴 (case Pump + default throw IllegalStateException)
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpDto.java` 신규 작성 → 검증: 파일 존재 + extends InstrumentDto + ratedHead·ratedFlwrt·oprtngType 3 필드 + private 생성자 + static from(Pump) + applyCommonFields 호출. tagNm 필드 부재
- [x] `api/src/test/java/com/mo/swtp/instrument/dto/PumpDtoSerializationTest.java` 신규 작성 → 검증: PumpDto 직렬화 결과에 "equipTypeCd":"PUMP" + "ratedHead" + "ratedFlwrt" + "oprtngType" 포함, tagNm 미포함. ./gradlew.bat :api:test --tests PumpDtoSerializationTest PASS

### Phase 4: 고아 자산 백지화 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityListDto.java` 파일 삭제 → 검증: 파일 부재
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityListService.java` 파일 삭제 → 검증: 파일 부재
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepository.java` 의 findFacilitiesHavingDwtChild 메서드 시그니처 제거 (FacilityType import 미사용 시 import 정리) → 검증: grep findFacilitiesHavingDwtChild api/src/main 매칭 0건 + 잔존 메서드 (findFirstChildByParentIdAndType·findFacilities) 시그니처 유지
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepositoryImpl.java` 의 findFacilitiesHavingDwtChild 구현 (L25-47) 제거 (QFacility child·JPAExpressions import 미사용 시 정리) → 검증: grep findFacilitiesHavingDwtChild api/src/main 매칭 0건 + 잔존 메서드 2건 (findFirstChildByParentIdAndType·findFacilities) 구현 유지
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityListServiceTest.java` 파일 삭제 → 검증: 파일 부재 + ./gradlew.bat :api:test 컴파일 오류 0건

### Phase 5: Controller + Service 정합 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 의 findFacility (단건 조회) `@Operation` 에 `@ApiResponse(content = @Content(schema = @Schema(oneOf = {DwtDto.class, PwtfDto.class, RsvDto.class, PrsfDto.class, PointDto.class}, discriminatorProperty = "facilityTypeCd")))` 추가 → 검증: grep "oneOf.*DwtDto.class" FacilityController.java 매칭 + 단건 조회 엔드포인트
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 의 findAllFacilities (목록 조회) `@Operation` 에 동일 `@ArraySchema(schema = @Schema(oneOf = {...}, discriminatorProperty = "facilityTypeCd"))` 추가 → 검증: grep "ArraySchema.*oneOf" FacilityController.java 매칭 + 목록 조회 엔드포인트
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityServiceTest.java` 자식 타입별 findFacilityDto 응답 검증 케이스 추가 (DWT 조회 → DwtDto 인스턴스 + minReqPrsr 매칭 / PWTF 조회 → PwtfDto 인스턴스) → 검증: ./gradlew.bat :api:test --tests FacilityServiceTest PASS

### Phase 6: 전체 빌드·테스트 검증

- [x] `./gradlew.bat :common:build` 실행 → 검증: BUILD SUCCESSFUL
- [x] `./gradlew.bat :api:test` 실행 → 검증: BUILD SUCCESSFUL + DwtStatusServiceTest PASS 유지 + FacilityServiceTest PASS 유지 + FacilityDtoSerializationTest·PumpDtoSerializationTest PASS
- [x] `./gradlew.bat clean build` 실행 → 검증: BUILD SUCCESSFUL (전체 모듈)
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepository.java` 최종 grep 검증 → 검증: grep -r FacilityListDto api/src 매칭 0건 + grep -r FacilityListService api/src 매칭 0건 + grep -r findFacilitiesHavingDwtChild api/src 매칭 0건 + grep -r tagNm common/src/main/java/com/mo/swtp/instrument/domain/Pump.java 매칭 0건

## 산출물
- [결과](../../../results/20260512/시설물응답DTO명세/RESULT1.md)
