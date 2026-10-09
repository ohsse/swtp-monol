---
status: completed
created: 2026-06-08
updated: 2026-06-08
---
# 시설 도메인 확장 — 시설 유형 7종 + 그룹 분류 도입

## 관련 계획
- [계획안](../../../plan/20260608/시설_도메인_확장/PLAN1.md)

## Phase

### Phase 1: enum (common)
- [x] `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityGroup.java` 신규 — STORAGE("저장시설")·OPERATION("운영시설")·NETWORK("계통시설") + 한글 설명 필드, @Getter @RequiredArgsConstructor → 검증: ./gradlew.bat :common:compileJava 성공
- [x] `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityType.java` 수정 — 생성자 필드 2개(description, group) + @Getter @RequiredArgsConstructor, 기존 5종 그룹 매핑(PWTF/DWT/RSV=STORAGE, POINT=NETWORK, PRSF=OPERATION) + 신규 7종(WTBLD/CHMB/ACFB/POZB/FLTB/DEWB/SOLAR=OPERATION) 추가, 각 상수 Javadoc 유지 → 검증: FacilityType.values().length == 12 확인

### Phase 2: 자식 엔티티 7종 skeleton (common)
- [x] `common/src/main/java/com/mo/swtp/facility/domain/WaterTransmissionBuilding.java` 신규 — @Table(wtbld_m) @DiscriminatorValue(WTBLD), create(facilityNm, parentFacilityId, dispOrd, mainYn), PurifiedWaterTank 선례 → 검증: extends Facility 컴파일 성공
- [x] `common/src/main/java/com/mo/swtp/facility/domain/ChemicalBuilding.java` 신규 — @Table(chmb_m) @DiscriminatorValue(CHMB), skeleton → 검증: 컴파일 성공
- [x] `common/src/main/java/com/mo/swtp/facility/domain/ActivatedCarbonFilter.java` 신규 — @Table(acfb_m) @DiscriminatorValue(ACFB), skeleton → 검증: 컴파일 성공
- [x] `common/src/main/java/com/mo/swtp/facility/domain/PreOzonationBuilding.java` 신규 — @Table(pozb_m) @DiscriminatorValue(POZB), skeleton → 검증: 컴파일 성공
- [x] `common/src/main/java/com/mo/swtp/facility/domain/FiltrationBuilding.java` 신규 — @Table(fltb_m) @DiscriminatorValue(FLTB), skeleton → 검증: 컴파일 성공
- [x] `common/src/main/java/com/mo/swtp/facility/domain/DewateringBuilding.java` 신규 — @Table(dewb_m) @DiscriminatorValue(DEWB), skeleton → 검증: 컴파일 성공
- [x] `common/src/main/java/com/mo/swtp/facility/domain/SolarPowerFacility.java` 신규 — @Table(solar_m) @DiscriminatorValue(SOLAR), skeleton → 검증: 컴파일 성공
- [x] `common` QClass 7종 재생성 확인 → 검증: ./gradlew.bat :common:build 성공, QWaterTransmissionBuilding 등 generated 확인

### Phase 3: 요청 DTO 7종 + FacilityUpsertDto 다형성 (api)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/WtbldUpsertDto.java` 신규 — extends FacilityUpsertDto (자식 전용 필드 0), PwtfUpsertDto 선례 → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/ChmbUpsertDto.java` 신규 — extends FacilityUpsertDto → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/AcfbUpsertDto.java` 신규 — extends FacilityUpsertDto → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/PozbUpsertDto.java` 신규 — extends FacilityUpsertDto → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FltbUpsertDto.java` 신규 — extends FacilityUpsertDto → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/DewbUpsertDto.java` 신규 — extends FacilityUpsertDto → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/SolarUpsertDto.java` 신규 — extends FacilityUpsertDto → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityUpsertDto.java` 수정 (동기화 지점 4) — @JsonSubTypes 에 7종 추가 (4→11, POINT 제외) → 검증: 11 Type 엔트리 확인
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityUpsertDto.java` 수정 (동기화 지점 5) — @Schema(oneOf) 에 7종 추가 (4→11) → 검증: oneOf 11 클래스 확인

### Phase 4: 응답 DTO 7종 + FacilityDto 다형성 + 파생 필드 (api)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/WtbldDto.java` 신규 — extends FacilityDto, from(WaterTransmissionBuilding) → applyCommonFields, PwtfDto 선례 → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/ChmbDto.java` 신규 — extends FacilityDto, from(ChemicalBuilding) → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/AcfbDto.java` 신규 — extends FacilityDto, from(ActivatedCarbonFilter) → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/PozbDto.java` 신규 — extends FacilityDto, from(PreOzonationBuilding) → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FltbDto.java` 신규 — extends FacilityDto, from(FiltrationBuilding) → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/DewbDto.java` 신규 — extends FacilityDto, from(DewateringBuilding) → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/SolarDto.java` 신규 — extends FacilityDto, from(SolarPowerFacility) → 검증: 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` 수정 — facilityGroupCd(FacilityGroup) 파생 필드 추가 + @Schema(implementation=FacilityGroup.class), applyCommonFields() 에서 getFacilityType().getGroup() 주입, facilityTypeCd description 12종 갱신 → 검증: facilityGroupCd getter 존재
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` 수정 (동기화 지점 1) — @JsonSubTypes 에 7종 추가 (5→12) → 검증: 12 Type 엔트리 확인
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` 수정 (동기화 지점 2) — @Schema(oneOf) 에 7종 추가 (5→12) → 검증: oneOf 12 클래스 확인
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` 수정 (동기화 지점 3) — from() switch 에 7 case 추가 (5→12), default IllegalStateException 유지 → 검증: 12 case 확인

### Phase 5: Service switch + private 메서드 (api)
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityService.java` 수정 (동기화 지점 6) — saveFacility switch 에 7 case + private save{Type} 7 메서드 추가 (savePwtf 선례, skeleton) → 검증: 11 case 확인
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityService.java` 수정 (동기화 지점 7) — updateFacility switch 에 7 case + private update{Type} 7 메서드 추가 (updatePwtf 선례) → 검증: 11 case 확인

### Phase 6: Controller @ApiResponse oneOf + description (api)
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 (동기화 지점 8) — GET 목록 @ApiResponse content oneOf 5→12 → 검증: 12 클래스 확인
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 (동기화 지점 9) — GET 단건 @ApiResponse content oneOf 5→12 → 검증: 12 클래스 확인
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 — 등록/수정 @Operation description 자식 종류 목록 문구 12종(요청 11종) 갱신 → 검증: 신규 7종 문구 포함 확인

### Phase 7: DDL (common + docs 동시 갱신)
- [x] `common/src/main/resources/db/migration/V2_1__facility_patch.sql` 신규 — 자식 7테이블 CREATE (facility_id PK + FK ON DELETE RESTRICT) + COMMENT ON TABLE/COLUMN + facility_type_cd COMMENT 12종 갱신("FacilityType enum SSOT 참조" 병기) → 검증: check-ddl-column-comment.sh 통과 (COMMENT 누락 0)
- [x] `backend/docs/ddl/facility.sql` 수정 — V2_1 동일 내용 누적 (자식 7 CREATE+COMMENT, facility_type_cd COMMENT 갱신, 헤더 자식 5→12종 정렬) → 검증: V2_1 과 자식 7테이블 DDL 내용 일치

### Phase 8: 테스트
- [x] `common/src/test/java/com/mo/swtp/facility/domain/enumtype/FacilityTypeTest.java` 신규 — 12종 그룹 매핑 + 그룹별 카운트(STORAGE 3·OPERATION 8·NETWORK 1) 단언 → 검증: ./gradlew.bat :common:test FacilityTypeTest PASS
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityServiceTest.java` 수정 — 신규 유형(CHMB) 등록/조회 테스트, switch default(FACILITY_TYPE_MISMATCH) 미발생 + facilityGroupCd=OPERATION 검증 → 검증: ./gradlew.bat :api:test FacilityServiceTest PASS
- [x] `api/src/test/java/com/mo/swtp/facility/dto/FacilityDtoSerializationTest.java` 수정 — 신규 7종 다형성 직렬화 + facilityGroupCd 파생 노출 검증 → 검증: ./gradlew.bat :api:test FacilityDtoSerializationTest PASS

### Phase 9: 빌드 검증
- [x] 전체 빌드 (QClass 재생성 포함) → 검증: ./gradlew.bat clean build BUILD SUCCESSFUL
- [x] 다형성 9곳 종 수 교차 확인 → 검증: 응답측 5곳(12) + 요청측 4곳(11) case/oneOf/JsonSubTypes 카운트 일치

## 산출물
- [결과](../../../results/20260608/시설_도메인_확장/RESULT1.md)
