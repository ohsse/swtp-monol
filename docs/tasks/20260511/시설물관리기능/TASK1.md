---
status: completed
created: 2026-05-11
updated: 2026-05-12
---
# 시설물관리기능 CRUD API 신설 — TASK

## 관련 계획
- [계획안](../../../plan/20260511/시설물관리기능/PLAN1.md)
- [분석](../../../analyze/20260511/시설물관리기능/ANALYZE1.md)

## Phase

### Phase 1: 사전 검증 + common 모듈 1건 변경 (사용자 승인 항목)
- [x] `common/src/main/java/com/mo/swtp/facility/domain/DistributionWaterTank.java` 자식 변경 메서드 `changeMinReqPrsr(BigDecimal)` 신설 (Objects.requireNonNull 검증) → 검증: grep changeMinReqPrsr DistributionWaterTank.java 매칭 1건
- [x] `./gradlew.bat :common:build` 실행 → 검증: BUILD SUCCESSFUL 출력 확인

### Phase 2: ErrorCode 신설
- [x] `api/src/main/java/com/mo/swtp/facility/exception/FacilityErrorCode.java` enum 4 항목 (FACILITY_NOT_FOUND·DUPLICATE_FACILITY_NM·FACILITY_TYPE_MISMATCH·INVALID_PARENT_FACILITY_ID) 신설 → 검증: check-errorcode-contract.sh 훅 통과 (httpStatus int 단일 필드)
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력 확인

### Phase 3: DTO 클래스 신설 (7 파일) — 계획 외 정정: common → api 모듈 (기존 facility/dto 동일 위치 + 다른 도메인 일관성)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` 공통 응답 DTO 신설 (facilityId·facilityNm·facilityTypeCd·parentFacilityId·dispOrd·mainYn·useYn·minReqPrsr nullable·rgstrDtm·updtDtm) → 검증: grep @Schema FacilityDto.java 매칭 ≥ 8건
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityUpsertDto.java` 추상 부모 DTO 신설 (@JsonTypeInfo + @JsonSubTypes 4종 + @Schema oneOf + discriminatorProperty) → 검증: grep "oneOf" FacilityUpsertDto.java 매칭 1건 + grep "@JsonSubTypes" 매칭 1건
- [x] `api/src/main/java/com/mo/swtp/facility/dto/PwtfUpsertDto.java` extends FacilityUpsertDto 자식 DTO 신설 (공통 필드만 — 자식 전용 컬럼 없음) → 검증: grep "extends FacilityUpsertDto" PwtfUpsertDto.java 매칭 1건
- [x] `api/src/main/java/com/mo/swtp/facility/dto/DwtUpsertDto.java` extends FacilityUpsertDto + minReqPrsr @NotNull 자식 DTO 신설 → 검증: grep "@NotNull" DwtUpsertDto.java 매칭 1건
- [x] `api/src/main/java/com/mo/swtp/facility/dto/RsvUpsertDto.java` extends FacilityUpsertDto 자식 DTO 신설 → 검증: grep "extends FacilityUpsertDto" RsvUpsertDto.java 매칭 1건
- [x] `api/src/main/java/com/mo/swtp/facility/dto/PrsfUpsertDto.java` extends FacilityUpsertDto 자식 DTO 신설 → 검증: grep "extends FacilityUpsertDto" PrsfUpsertDto.java 매칭 1건
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilitySearchDto.java` 조회 필터 DTO 신설 (facilityTypeCd·useYn) → 검증: grep "FacilitySearchDto" FacilitySearchDto.java 매칭 1건
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력 확인

### Phase 4: Repository 확장 — 계획 외 정정: 신설 → 기존 3계층 확장 (이미 송수펌프제어분석 사이클에서 도입됨)
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityRepository.java` existsByFacilityNmAndFacilityIdNot 메서드 추가 (자기 자신 제외 UNIQUE 검사) → 검증: grep "existsByFacilityNm" FacilityRepository.java 매칭 ≥ 2건
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepository.java` findFacilities(FacilitySearchDto) 시그니처 추가 → 검증: grep "interface FacilityCustomRepository" FacilityCustomRepository.java 매칭 1건
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepositoryImpl.java` findFacilities Querydsl 구현 추가 (BooleanBuilder + Sort 정렬) → 검증: grep "class FacilityCustomRepositoryImpl" FacilityCustomRepositoryImpl.java 매칭 1건
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력 확인

### Phase 5: Service 신설 — 디스패처 + 자식별 private 메서드 (Java 21 switch 패턴 매칭 — instanceof 대신 채택)
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityService.java` 신설 — 클래스 골격 + 의존성 주입 (@Service @Transactional readOnly + FacilityRepository) → 검증: grep "@Service" FacilityService.java 매칭 1건
- [x] FacilityService 에 findAllFacilities(FacilitySearchDto) 공통 조회 메서드 추가 — type/useYn 필터 + Sort (useYn DESC, dispOrd ASC, facilityNm ASC) → 검증: grep "findAllFacilities" FacilityService.java 매칭 1건
- [x] FacilityService 에 findFacilityDto(facilityId) 단건 조회 메서드 추가 — orElseThrow FACILITY_NOT_FOUND → 검증: grep "FACILITY_NOT_FOUND" FacilityService.java 매칭 ≥ 1건
- [x] FacilityService 에 saveFacility(FacilityUpsertDto) 디스패처 메서드 추가 — validateParentFacility + validateDuplicateFacilityNm + switch 패턴 매칭 4분기 → 검증: grep "switch" FacilityService.java 매칭 ≥ 1건
- [x] FacilityService 에 updateFacility(facilityId, FacilityUpsertDto) 디스패처 메서드 추가 — findById + validateTypeMatch + validateParentFacility(facilityId) + validateDuplicateFacilityNmOnUpdate + switch 패턴 매칭 4분기 → 검증: grep "validateTypeMatch" FacilityService.java 매칭 ≥ 1건
- [x] FacilityService 에 deactivateFacility(facilityId) 논리 삭제 메서드 추가 — Facility.deactivate() 호출 → 검증: grep "deactivate" FacilityService.java 매칭 ≥ 1건
- [x] FacilityService 에 자식별 private savePwtf/saveDwt/saveRsv/savePrsf 메서드 4건 추가 (각 자식 정적 팩토리 호출 + save) → 검증: grep "private String save" FacilityService.java 매칭 4건
- [x] FacilityService 에 자식별 private updatePwtf/updateDwt/updateRsv/updatePrsf 메서드 4건 추가 (DWT 는 changeMinReqPrsr 추가 호출) → 검증: grep "private void update" FacilityService.java 매칭 4건
- [x] FacilityService 에 validateParentFacility/validateDuplicateFacilityNm/validateDuplicateFacilityNmOnUpdate/validateTypeMatch private 검증 메서드 4건 추가 → 검증: grep "private void validate" FacilityService.java 매칭 4건
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력 확인

### Phase 6: Controller 신설 — 계획 외 정정: controller/ → web/ 패키지 (api CLAUDE.md 룰 + 기존 DwtStatusController 동일 패턴)
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 신설 — 클래스 골격 + Swagger @Tag "06. 시설물 관리" + RequestMapping "/api/facility" + CommonController 상속 → 검증: grep "@Tag" FacilityController.java 매칭 1건
- [x] FacilityController 에 GET / 목록 조회 엔드포인트 추가 (FacilitySearchDto 쿼리 파라미터) → 검증: grep "@GetMapping" FacilityController.java 매칭 ≥ 1건
- [x] FacilityController 에 GET /{facilityId} 단건 조회 엔드포인트 추가 → 검증: grep "@GetMapping" FacilityController.java 매칭 ≥ 2건
- [x] FacilityController 에 POST / 등록 엔드포인트 추가 (@RequestBody @Valid FacilityUpsertDto + Operation summary) → 검증: grep "@PostMapping" FacilityController.java 매칭 1건
- [x] FacilityController 에 PUT /{facilityId} 수정 엔드포인트 추가 (@RequestBody @Valid FacilityUpsertDto + @PathVariable) → 검증: grep "@PutMapping" FacilityController.java 매칭 1건
- [x] FacilityController 에 DELETE /{facilityId} 논리 삭제 엔드포인트 추가 → 검증: grep "@DeleteMapping" FacilityController.java 매칭 1건
- [x] FacilityController 에 @ApiResponses (200/400/401/403/404/500) 표준 응답 명세 추가 (api-patterns.md 패턴) → 검증: grep "@ApiResponses" FacilityController.java 매칭 ≥ 5건
- [x] `./gradlew.bat :api:compileJava` 실행 → 검증: BUILD SUCCESSFUL 출력 확인

### Phase 7: 단위 테스트 — Service 시나리오 (17 GREEN — 12+ 초과 충족)
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityServiceTest.java` 신설 — @ExtendWith(MockitoExtension.class) + @Mock FacilityRepository + @InjectMocks → 검증: grep "@InjectMocks" FacilityServiceTest.java 매칭 1건
- [x] FacilityServiceTest 에 PWTF 등록 정상 시나리오 + RSV 등록 정상 시나리오 + PRSF 등록 정상 시나리오 추가 → 검증: ./gradlew.bat :api:test --tests com.mo.swtp.facility.service.FacilityServiceTest PASS 3건 이상
- [x] FacilityServiceTest 에 DWT 등록 정상 시나리오 (minReqPrsr 포함) 추가 → 검증: grep "minReqPrsr" FacilityServiceTest.java 매칭 ≥ 1건
- [x] FacilityServiceTest 에 자식 4종 수정 시나리오 4건 + DWT 수정 시 changeMinReqPrsr 호출 검증 추가 → 검증: ./gradlew.bat :api:test --tests com.mo.swtp.facility.service.FacilityServiceTest 통과
- [x] FacilityServiceTest 에 논리 삭제 시나리오 + deactivate 호출 verify 추가 → 검증: grep "deactivate" FacilityServiceTest.java 매칭 ≥ 1건
- [x] FacilityServiceTest 에 DUPLICATE_FACILITY_NM 예외 시나리오 (등록 + 수정) 추가 → 검증: grep "DUPLICATE_FACILITY_NM" FacilityServiceTest.java 매칭 ≥ 2건
- [x] FacilityServiceTest 에 FACILITY_NOT_FOUND 예외 시나리오 (수정·삭제·단건조회) 추가 → 검증: grep "FACILITY_NOT_FOUND" FacilityServiceTest.java 매칭 ≥ 3건
- [x] FacilityServiceTest 에 INVALID_PARENT_FACILITY_ID 예외 시나리오 (존재하지 않는 부모 + 자기참조) 추가 → 검증: grep "INVALID_PARENT_FACILITY_ID" FacilityServiceTest.java 매칭 ≥ 2건
- [x] FacilityServiceTest 에 FACILITY_TYPE_MISMATCH 예외 시나리오 (PUT 시 path 시설 type ≠ body discriminator) 추가 → 검증: grep "FACILITY_TYPE_MISMATCH" FacilityServiceTest.java 매칭 ≥ 1건
- [x] `./gradlew.bat :api:test --tests "com.mo.swtp.facility.service.FacilityServiceTest"` 실행 → 검증: 17 GREEN + 모든 ErrorCode 분기 커버

### Phase 8: 통합 테스트 — Controller·Repository 결합 (4 GREEN — flush/clear 회피 추가, DDL V6_1·V8_1·V8_3 사용자 적용)
- [x] `api/src/test/java/com/mo/swtp/facility/FacilityServiceIntegrationTest.java` 신설 — @SpringBootTest(NONE) + @ActiveProfiles("test") + @Transactional + @MockitoBean(ApiErrorResponseWriter) → 검증: grep "@SpringBootTest" FacilityServiceIntegrationTest.java 매칭 1건
- [x] FacilityServiceIntegrationTest 에 PWTF·DWT 각 1건 등록 + GET 목록 type=PWTF 조회 시 PWTF 만 반환 시나리오 추가 → 검증: ./gradlew.bat :api:test --tests com.mo.swtp.facility.FacilityServiceIntegrationTest 해당 테스트 PASS
- [x] FacilityServiceIntegrationTest 에 DELETE 후 GET 목록 useYn=Y 응답에서 해당 시설 제외 시나리오 추가 → 검증: ./gradlew.bat :api:test --tests com.mo.swtp.facility.FacilityServiceIntegrationTest 해당 테스트 PASS
- [x] FacilityServiceIntegrationTest 에 facility_nm UNIQUE DB 제약 위반 시 RestApiException DUPLICATE_FACILITY_NM (existsBy 사전 차단) 시나리오 추가 → 검증: 통합 테스트 GREEN

### Phase 9: 빌드 + 수동 검증
- [x] `./gradlew.bat :common:build :api:build` 실행 → 검증: BUILD SUCCESSFUL 양 모듈 출력 확인
- [x] `./gradlew.bat :api:test --tests "com.mo.swtp.facility.*"` 실행 → 검증: BUILD SUCCESSFUL + 신규 facility 테스트 21 GREEN (단위 17 + 통합 4)
- [ ] Swagger UI 수동 확인 — bootRun 후 /swagger-ui/index.html 의 POST /api/facility request body 에 oneOf 4종 표시 + discriminator facilityTypeCd 표시 → 검증: 브라우저 스크린샷 또는 swagger-ui 의 oneOf 키워드 노출 육안 확인 (사용자 수동 검증 영역 — /dev:commit 이전 또는 이후)

## 산출물
- [결과](../../../results/20260511/시설물관리기능/RESULT1.md) (작성 예정)
