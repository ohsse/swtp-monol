---
status: completed
created: 2026-05-12
updated: 2026-05-13
---
# 계측기 관리 CRUD 구현 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260512/계측기관리CRUD/PLAN1.md)

## Phase

### Phase 1: common 모듈 — Jackson 다형성 DTO 7종

- [x] `common/src/main/java/com/mo/swtp/instrument/dto/InstrumentUpsertDto.java` 추상 부모 작성 → 검증: 컴파일 OK, @JsonTypeInfo(use=NAME, property="equipTypeCd", include=EXISTING_PROPERTY) + @JsonSubTypes 6종 + @Schema(oneOf={...}, discriminatorProperty="equipTypeCd") 모두 포함
- [x] `common/src/main/java/com/mo/swtp/instrument/dto/PumpUpsertDto.java` 자식 DTO 작성 → 검증: ratedHead·ratedFlwrt·oprtngType 3건에 @NotNull, tagNm @Size(max=50)
- [x] `common/src/main/java/com/mo/swtp/instrument/dto/ValveUpsertDto.java` skeleton 자식 DTO 작성 → 검증: 자체 필드 0건, @JsonTypeName("VALVE") 매칭
- [x] `common/src/main/java/com/mo/swtp/instrument/dto/FlowMeterUpsertDto.java` skeleton 자식 DTO 작성 → 검증: @JsonTypeName("FLWMTR")
- [x] `common/src/main/java/com/mo/swtp/instrument/dto/PressureMeterUpsertDto.java` skeleton 자식 DTO 작성 → 검증: @JsonTypeName("PRSMTR")
- [x] `common/src/main/java/com/mo/swtp/instrument/dto/LevelMeterUpsertDto.java` skeleton 자식 DTO 작성 → 검증: @JsonTypeName("LVMTR")
- [x] `common/src/main/java/com/mo/swtp/instrument/dto/PowerMeterUpsertDto.java` skeleton 자식 DTO 작성 → 검증: @JsonTypeName("ELCMTR")
- [x] `./gradlew.bat :common:build` 실행 → 검증: BUILD SUCCESSFUL 출력 + QClass 재생성 완료

### Phase 2: api 모듈 — 응답 DTO·검색 DTO·ErrorCode

- [x] `api/src/main/java/com/mo/swtp/instrument/exception/InstrumentErrorCode.java` 작성 → 검증: implements ErrorCode + httpStatus(int) 단일 필드 (message 필드 금지 — check-errorcode-contract.sh 훅 통과). 4건 항목 (INSTRUMENT_NOT_FOUND=404 · DUPLICATE_INSTRUMENT_NM=409 · INVALID_FACILITY_ID=400 · EQUIP_TYPE_MISMATCH=400)
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/InstrumentDto.java` 응답 DTO 작성 → 검증: extends BaseAuditResponseDto, from(Instrument) 정적 팩토리 + instanceof Pump pump 분기로 Pump 자체 컬럼 4건 채움
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/InstrumentSearchDto.java` 검색 조건 DTO 작성 → 검증: equipTypeCd·useYn·facilityId 3필드 모두 NULL 허용 + @Schema
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력

### Phase 3: 엔티티·Repository 보강

- [x] `common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` `changePumpSelfColumns(BigDecimal ratedHead, BigDecimal ratedFlwrt, String tagNm, PumpOprtngType oprtngType)` 메서드 추가 → 검증: null 인자는 기존 값 유지 패턴 (Instrument.changeInfo 와 동일), Javadoc 작성
- [x] `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentRepository.java` `existsByFacilityFacilityIdAndInstrumentNmAndInstrumentIdNot(String facilityId, String instrumentNm, String instrumentId)` 메서드 시그니처 추가 → 검증: 컴파일 OK
- [x] `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentCustomRepository.java` `findInstruments(InstrumentSearchDto)` 메서드 시그니처 추가 → 검증: 컴파일 OK
- [x] `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentCustomRepositoryImpl.java` Querydsl 구현 추가 → 검증: 정렬 use_yn DESC → disp_ord ASC → instrument_nm ASC (FacilityCustomRepositoryImpl.findFacilities 선례 정합)
- [x] `./gradlew.bat :common:build :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL

### Phase 4: api 모듈 — InstrumentService

- [x] `api/src/main/java/com/mo/swtp/instrument/service/InstrumentService.java` 작성 → 검증: 클래스 레벨 @Transactional(readOnly=true), @RequiredArgsConstructor, 협력자 (InstrumentRepository, InstrumentCustomRepository, FacilityRepository) 주입
- [x] saveInstrument(InstrumentUpsertDto) — Java 21 switch 패턴 매칭 6 자식 분기 + validateFacility + validateDuplicateInstrumentNm 호출 후 자식별 정적 팩토리 호출 → 검증: 메서드 50줄 이하 (코딩 디시플린 §2.1)
- [x] updateInstrument(String instrumentId, InstrumentUpsertDto) — path 조회 + validateEquipTypeMatch + validateFacility + 자기 제외 UNIQUE 검증 + super.changeInfo + Pump 분기 시 changePumpSelfColumns 호출 → 검증: 메서드 50줄 이하
- [x] deactivateInstrument(String instrumentId) — 논리 삭제 (useYn = N) → 검증: Instrument 엔티티의 deactivate() 또는 super.changeInfo 활용
- [x] findInstruments(InstrumentSearchDto) / findInstrument(String instrumentId) 조회 메서드 2건 → 검증: 단건 조회 시 INSTRUMENT_NOT_FOUND 예외, 목록 조회는 InstrumentCustomRepository.findInstruments 위임
- [x] 검증 메서드 3종 (private) — validateFacility / validateDuplicateInstrumentNm / validateEquipTypeMatch → 검증: 각 검증 실패 시 RestApiException(InstrumentErrorCode.XXX) throw
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL

### Phase 5: api 모듈 — InstrumentController

- [x] `api/src/main/java/com/mo/swtp/instrument/web/InstrumentController.java` 작성 → 검증: extends CommonController, @Tag(name="07. 계측기 관리"), @RequestMapping("/api/instrument")
- [x] GET /api/instrument (목록 조회) — `findInstruments(@ModelAttribute InstrumentSearchDto)` → 검증: @Operation(summary="계측기 목록 조회"), @ApiResponses 6종 (200·400·401·403·404·500)
- [x] GET /api/instrument/{instrumentId} (단건 조회) → 검증: @Parameter 어노테이션, ResponseEntity<CommonResponseDto<InstrumentDto>>
- [x] POST /api/instrument (등록) — `@Valid @RequestBody InstrumentUpsertDto` → 검증: Jackson 다형성 역직렬화 자동, @Operation summary 작성
- [x] PUT /api/instrument/{instrumentId} (수정) → 검증: path + body 매개변수 모두 @Valid
- [x] DELETE /api/instrument/{instrumentId} (논리 삭제) → 검증: @Operation summary="계측기 비활성화", ResponseEntity<CommonResponseDto<Void>>
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL

### Phase 6: 통합 테스트 작성

- [x] `api/src/test/java/com/mo/swtp/instrument/service/InstrumentServiceTest.java` 작성 → 검증: @SpringBootTest(NONE) + @ActiveProfiles("test") + @Transactional + 7건 시나리오 모두 GREEN
- [x] 시나리오 ①: PUMP 등록 성공 → 저장 결과 instanceof Pump + 자체 컬럼 4건 값 일치
- [x] 시나리오 ②: VALVE 등록 성공 → 저장 결과 instanceof Valve
- [x] 시나리오 ③: 동일 시설·동일 이름 등록 시 RestApiException(DUPLICATE_INSTRUMENT_NM)
- [x] 시나리오 ④: 존재하지 않는 facilityId 등록 시 RestApiException(INVALID_FACILITY_ID)
- [x] 시나리오 ⑤: PUMP 엔티티 PUT 에 equipTypeCd=VALVE 전송 시 RestApiException(EQUIP_TYPE_MISMATCH)
- [x] 시나리오 ⑥: 논리 삭제 → useYn = N
- [x] 시나리오 ⑦: 다른 시설에 동일 이름 등록 허용 — (facility_id, instrument_nm) 복합 UNIQUE 검증
- [x] `./gradlew.bat :api:test --tests "*InstrumentServiceTest*"` 실행 → 검증: 7건 모두 PASS

### Phase 7: 전체 빌드 검증

- [x] `./gradlew.bat clean build` 실행 → 검증: BUILD SUCCESSFUL 출력, 전체 단위 + 통합 테스트 GREEN
- [x] (선택) `./gradlew.bat :api:bootRun` 실행 후 Swagger UI 에서 POST /api/instrument 의 Request Body 스키마가 oneOf (자식 6종) discriminator=equipTypeCd 로 노출 확인

## 산출물
- [결과](../../../results/20260512/계측기관리CRUD/RESULT1.md) (Medium 작업이므로 RESULT 단계 면제 — `/dev:impl` 완료 후 `/dev:commit` 안내로 직행)
