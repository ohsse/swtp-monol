---
status: completed
created: 2026-05-13
updated: 2026-05-13
---
# 송수펌프제어분석 — 3번섹션 시설 실시간 상태 표출 API 작업 분해

## 관련 계획
- [계획안](../../../plan/20260513/송수펌프제어분석-3번섹션/PLAN1.md)

## Phase

### Phase 1: RawData Repository 계층 — native DISTINCT ON 신설 + 2번 섹션 메서드 폐기

- [x] `backend/api/src/main/java/com/mo/swtp/raw/dto/RawDataLatestDto.java` Service 내부 전송용 DTO 신규 작성 (record, 필드: tagSrlNo·rawVal·corrVal·acqDtm·qualityCd) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `backend/api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` 기존 메서드 findLatestByTagSrlNos(List<String>, LocalDateTime) 시그니처 폐기 + 신규 시그니처 List<RawDataLatestDto> findLatestByTagSrlNos(List<String>) 단일 메서드로 재정의 → 검증: 인터페이스 메서드 1건 확인 + ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `backend/api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` Querydsl 서브쿼리 구현 폐기 + EntityManager.createNativeQuery 기반 DISTINCT ON 구현 작성. coding-discipline.md §2.5 면책 인용 주석 (query-tuning.md §2) 명시 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL

### Phase 2: 2번 섹션 자산 일괄 폐기

- [x] `backend/api/src/main/java/com/mo/swtp/facility/service/DwtStatusService.java` 파일 삭제 → 검증: 파일 미존재 확인
- [x] `backend/api/src/main/java/com/mo/swtp/facility/web/DwtStatusController.java` 파일 삭제 → 검증: 파일 미존재 확인
- [x] `backend/api/src/main/java/com/mo/swtp/facility/dto/DwtStatusDto.java` 파일 삭제 → 검증: 파일 미존재 확인
- [x] `backend/api/src/main/java/com/mo/swtp/facility/dto/section/DwtRequirePressureDto.java` 파일 삭제 → 검증: 파일 미존재 확인
- [x] `backend/api/src/main/java/com/mo/swtp/facility/dto/section/MainFactorDto.java` 파일 삭제 + section 디렉토리 비어있어 자동 삭제 → 검증: 파일·디렉토리 미존재 확인
- [x] `backend/api/src/test/java/com/mo/swtp/facility/service/DwtStatusServiceTest.java` 파일 삭제 → 검증: 파일 미존재 확인
- [x] 폐기 후 잔존 참조 검증 → 검증: Grep DwtStatus|DwtRequirePressure|MainFactor 매칭 0건 (주석 1건 제외) + Grep findLatestByTagSrlNos 매칭 3건 (RawDataCustomRepository·Impl·RawDataLatestDto Javadoc) + ./gradlew :api:build BUILD SUCCESSFUL

### Phase 3: 응답 DTO 3건 신규 작성

- [x] `backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityStateDto.java` 신규 작성 (facilityId·facilityNm·List<FlwmtrStateDto> flwmtrs·List<PumpStateDto> pumps + 정적 팩토리 of). @ArraySchema(implementation) 명시. BaseAuditResponseDto 미상속 (실시간 통지) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `backend/api/src/main/java/com/mo/swtp/facility/dto/FlwmtrStateDto.java` 신규 작성 (10 필드 — instrumentId·instrumentNm + FRI 4컬럼 + PRI 4컬럼). qualityCd 필드 @Schema(implementation = QualityCode.class) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL
- [x] `backend/api/src/main/java/com/mo/swtp/facility/dto/PumpStateDto.java` 신규 작성 (6 필드 — instrumentId·instrumentNm·oprtngType·isRunning·acqDtm·qualityCd). oprtngType @Schema(implementation = PumpOprtngType.class) + isRunning Boolean (NULL 허용) → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL

### Phase 4: Service 계층 — FacilityStateService 신규

- [x] `backend/api/src/main/java/com/mo/swtp/facility/service/FacilityStateService.java` 신규 작성. 클래스 레벨 @Transactional(readOnly = true) + @RequiredArgsConstructor + @Service. 단일 public 메서드 findFacilityState(String facilityId) — Step 1 FacilityRepository.findById + use_yn 검증 (FACILITY_NOT_FOUND), Step 2 InstrumentRepository.findByFacilityIdAndEquipType (NoUniqueBean 회피 위해 InstrumentRepository 주입), Step 3 TagRepository.findByInstrumentInstrumentIdInAndUseYn + tagSeCd IN [FRI, PRI, OPS] Stream 필터, Step 4 RawDataRepository.findLatestByTagSrlNos (NoUniqueBean 회피 위해 RawDataRepository 주입), Step 5~6 instrument 단위 그룹핑 + DTO 빌드 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL

### Phase 5: Controller 계층 — FacilityController endpoint 추가

- [x] `backend/api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` GET /{facilityId}/state endpoint 추가. @Operation summary + @ApiResponses (200/400/401/403/404/500) + ResponseEntity<CommonResponseDto<FacilityStateDto>>. getResponseEntity(data) 래핑 + CommonController 상속 패턴 정합 → 검증: ./gradlew :api:compileJava BUILD SUCCESSFUL

### Phase 6: 단위 테스트 — FacilityStateServiceTest

- [x] `backend/api/src/test/java/com/mo/swtp/facility/service/FacilityStateServiceTest.java` 신규 작성. @ExtendWith(MockitoExtension.class) + @Mock 4건 (FacilityRepository / InstrumentRepository / TagRepository / RawDataRepository) + @InjectMocks FacilityStateService. 6개 시나리오 — (1) 시설 미존재 FACILITY_NOT_FOUND, (2) 비활성 시설 FACILITY_NOT_FOUND, (3) BAD QUALITY 태그도 그대로 반환, (4) 활성 instrument 없으면 빈 List 반환, (5) FRI/PRI 분리 매핑 검증, (6) OPS 1.0/0.0 isRunning true/false 변환 → 검증: ./gradlew :api:test --tests FacilityStateServiceTest 6 PASS BUILD SUCCESSFUL

### Phase 7: 통합 테스트 — RawDataCustomRepositoryImplIntegrationTest

- [x] `backend/api/src/test/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImplIntegrationTest.java` 신규 작성. @SpringBootTest(webEnvironment = NONE) + @ActiveProfiles("test") + @Transactional. 시나리오 3건 — 태그별 최신 측정값 DISTINCT ON / 빈 태그 목록 빈 결과 / 1시간 초과 과거 측정값 제외. native INSERT 픽스처 (BaseEntity AuditingEntityListener 우회). 본 워크트리는 PostgreSQL 미기동 + DDL 미실행 상태로 @Disabled 마커 추가 → 검증: 파일 작성 완료. 실행은 사용자 로컬 환경에서 @Disabled 제거 후 ./gradlew :api:test --tests RawDataCustomRepositoryImplIntegrationTest PASS (rawdata_1m_h 테이블 사전 생성 필요 — V6_5__rawdata_1m_h.sql 의 2026-05 파티션)

### Phase 8: 빌드 + EXPLAIN ANALYZE + Swagger 수동 검증

- [x] ./gradlew :common:build 실행 → 검증: BUILD SUCCESSFUL 출력 확인
- [x] ./gradlew :api:build 실행 → 검증: BUILD SUCCESSFUL 출력 확인 (10 tasks 통과 — 통합 테스트는 @Disabled 스킵)
- [ ] 로컬 PostgreSQL 에서 EXPLAIN (ANALYZE, BUFFERS) 수동 실행 → 검증: Index Scan Backward using idx_rawdata_1m_h_tag_time + Append 하위 파티션 1~2개 + Sort 노드 부재 확인 (본 사이클은 PostgreSQL 미기동 — 사용자 환경에서 별도 검증)
- [ ] ./gradlew :api:bootRun -Pprofile=local 기동 + Swagger UI 노출 확인 (본 사이클은 PostgreSQL 미기동 — 사용자 환경에서 별도 검증)

## 산출물
- [결과](../../../results/20260513/송수펌프제어분석-3번섹션/RESULT1.md) (작성 예정)
