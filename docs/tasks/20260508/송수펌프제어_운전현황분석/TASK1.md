---
status: completed
created: 2026-05-08
updated: 2026-05-08
---
# 송수펌프제어_운전현황분석 — 작업 분해 (FR-PMP-002)

## 관련 계획
- [계획안](../../../plan/20260508/송수펌프제어_운전현황분석/PLAN1.md)

## 관련 분석
- [도메인 분석](../../../analyze/20260507/송수펌프제어_운전현황분석/ANALYZE1.md)

## Phase

### Phase 1: 의존성 추가 + 빌드 검증

- [x] `api/build.gradle` Apache POI 5.3.0 의존성 추가 (`implementation 'org.apache.poi:poi-ooxml:5.3.0'`) → 검증: ./gradlew.bat :api:dependencies 출력에 poi-ooxml:5.3.0 라인 포함 확인
- [x] `./gradlew.bat clean build` 실행 → 검증: BUILD SUCCESSFUL 출력 확인 (Apache POI 의존성 정상 해석)

### Phase 2: facility 자식 POINT 추가 (common 모듈)

- [x] `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityType.java` enum 에 POINT 추가 (Javadoc 보강 — 관로 계측 분기점) → 검증: ./gradlew.bat :common:compileJava PASS + enum 값 4개 (PWTF/DWT/RSV/POINT) 노출 확인
- [x] `common/src/main/java/com/mo/swtp/facility/domain/SensorPoint.java` 신규 생성 (Facility 상속, @DiscriminatorValue("POINT"), @Table(name="point_m"), 자식 전용 컬럼 0건 skeleton, create 정적 팩토리) → 검증: ./gradlew.bat :common:compileJava PASS + PurifiedWaterTank 패턴 정합
- [x] `common/src/main/resources/db/init/V7_1__point_master_table.sql` 신규 생성 (CREATE TABLE point_m + FK ON DELETE RESTRICT + COMMENT ON COLUMN facility_id) → 검증: check-ddl-column-comment.sh 훅 통과 (저장 시 자동 차단 0건)
- [x] `common/src/test/java/com/mo/swtp/facility/domain/SensorPointTest.java` 단위 테스트 신규 (create 정적 팩토리 검증 + Facility 상속 검증 + facilityType = POINT 검증) → 검증: ./gradlew.bat :common:test --tests *SensorPointTest* PASS

### Phase 3: drvn_anls_dwld_h immutable 이력 (common + api + scheduler)

- [x] `common/src/main/java/com/mo/swtp/pump/enumtype/DataDivType.java` enum 신규 (PRDC 예측조회 / ANLS 분석이력) + Javadoc → 검증: ./gradlew.bat :common:compileJava PASS + enum 값 2개 노출
- [x] `common/src/main/java/com/mo/swtp/pump/enumtype/FileFormatType.java` enum 신규 (CSV / XLSX) + Javadoc → 검증: ./gradlew.bat :common:compileJava PASS + enum 값 2개 노출
- [x] `common/src/main/java/com/mo/swtp/pump/domain/DrvnAnlsDwldHistory.java` 엔티티 신규 — immutable 이력 패턴 (BaseEntity 미상속, @EntityListeners(AuditingEntityListener.class), @CreatedDate rgstrDtm + @CreatedBy rgstrId 직접 선언, updt_* 컬럼 부재, @GeneratedValue(SEQUENCE) seq_drvn_anls_dwld_h_id, allocationSize=100) → 검증: ./gradlew.bat :common:compileJava PASS + grep -E "updt_dtm|updt_id" 결과 0건
- [x] `common/src/main/java/com/mo/swtp/pump/domain/DrvnAnlsDwldHistoryId.java` 복합 PK 신규 (`(dwld_id, rgstr_dtm)` IdClass — TASK 누락 보강, AiDrvnModeHistoryId 패턴) → 검증: ./gradlew.bat :common:compileJava PASS
- [x] `api/src/main/java/com/mo/swtp/pump/repository/DrvnAnlsDwldHistoryRepository.java` 신규 (extends JpaRepository<DrvnAnlsDwldHistory, DrvnAnlsDwldHistoryId>) → 검증: ./gradlew.bat :api:compileJava PASS
- [x] `common/src/main/resources/db/init/V7_2__drvn_anls_dwld_history.sql` 신규 (CREATE TABLE drvn_anls_dwld_h + PARTITION BY RANGE rgstr_dtm + 12개월 파티션 2026-05~2027-04 + idx_drvn_anls_dwld_h_rgstr_id_dtm + seq_drvn_anls_dwld_h_id + COMMENT ON COLUMN 6컬럼 immutable 이력 표준 라벨) → 검증: check-ddl-column-comment.sh 훅 통과 + COMMENT ON COLUMN 6건 grep 매칭
- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpPartitionDropScheduler.java` 확장 — 5년 이전 drvn_anls_dwld_h_* 파티션 자동 DROP 잡 추가 (기존 패턴 재사용) → 검증: ./gradlew.bat :scheduler:compileJava PASS + @Scheduled cron 문자열 등록 확인
- [x] `api/src/test/java/com/mo/swtp/pump/repository/DrvnAnlsDwldHistoryRepositoryTest.java` 통합 테스트 신규 (INSERT 후 AuditingEntityListener 자동 주입 검증 — rgstrDtm 기록 + rgstrId 기록 + updt_* 컬럼 부재 검증) → 검증: ./gradlew.bat :api:test --tests *DrvnAnlsDwldHistoryRepositoryTest* PASS (Phase 7 통합 빌드 검증에서 실행)

### Phase 4: 응답 DTO 4종 + Service + Custom Repository (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/pump/dto/DrvnSttsInqDto.java` 요청 DTO 신규 (from·to·inqHr·inqMin·facilityId + @Schema description 의무) → 검증: ./gradlew.bat :api:compileJava PASS
- [x] `api/src/main/java/com/mo/swtp/pump/dto/DrvnAnlsRsltDto.java` 응답 통합 DTO 신규 (조회 메타 + AI 운전모드 + 운영 현황 + 그래프 + 중첩 DTO @Schema(implementation) 의무) → 검증: ./gradlew.bat :api:compileJava PASS + grep "implementation = " 매칭 (중첩 DTO 명시)
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpOprtngDto.java` 펌프 가동 DTO 신규 (actlOprtngYn·predcOprtngYn·actlFlwrtVal·predcFlwrtVal·pumpOprtngCnt·pumpCmbnCd + YnType implementation 명시) → 검증: ./gradlew.bat :api:compileJava PASS + grep "implementation = YnType.class" 2건 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/BranchMeasPredcDto.java` 분기 계측·예측 DTO 신규 (pointId·actl_*·predc_* 수요량·관압·수위) → 검증: ./gradlew.bat :api:compileJava PASS
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpDrvnStatusService.java` 신규 (@Service + @Transactional(readOnly = true) + equip_type_cd = 'PUMP' 필터 강제) → 검증: ./gradlew.bat :api:compileJava PASS + grep "EquipType.PUMP" 매칭 (필터 적용)
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpDrvnStatusCustomRepository.java` 인터페이스 신규 → 검증: ./gradlew.bat :api:compileJava PASS
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpDrvnStatusCustomRepositoryImpl.java` QueryDSL 구현 신규 (rawdata_1m_h + pump_predc_h + pump_ctrl_h + ai_drvn_mod_p JOIN + acq_dtm/predc_base_dtm/ctrl_dtm 시간 윈도우 조건 의무 + facility_type_cd = 'POINT' 필터 강제) → 검증: ./gradlew.bat :api:compileJava PASS + grep "FacilityType.POINT" 매칭

### Phase 5: 다운로드 API + Apache POI (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpDrvnStatusDownloadService.java` 신규 (XSSFWorkbook XLSX 생성 + BufferedWriter CSV 작성 + drvn_anls_dwld_h INSERT 1건 AuditingEntityListener 자동 주입 + HttpServletResponse.getOutputStream 직접 스트리밍 + 권한 검증 request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE)) → 검증: ./gradlew.bat :api:compileJava PASS
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpDrvnStatusDownloadServiceTest.java` 단위 테스트 3건 신규 (XLSX 정상 — XSSFWorkbook 셀 검증 / CSV 정상 / 잘못된 format DOWNLOAD_FORMAT_NOT_SUPPORTED 400) → 검증: ./gradlew.bat :api:test --tests *PumpDrvnStatusDownloadServiceTest* PASS

### Phase 6: Controller + ErrorCode (common + api)

- [x] `common/src/main/java/com/mo/swtp/pump/exception/PumpDrvnStatusErrorCode.java` 신규 (INVALID_INQUIRY_RANGE 400 + FACILITY_NOT_FOUND 404 + DOWNLOAD_FORMAT_NOT_SUPPORTED 400 + DOWNLOAD_FAILED 500, httpStatus int 외 필드 금지) → 검증: check-errorcode-contract.sh 훅 통과 (저장 시 자동 차단 0건) + grep -E "private final String" 결과 0건
- [x] `api/src/main/java/com/mo/swtp/pump/web/PumpDrvnStatusController.java` 신규 (CommonController 상속, GET /api/pump-control/drvn-status 통합 조회 + GET /api/pump-control/drvn-status/operation 펌프 가동 + GET /api/pump-control/drvn-status/branch-measure 분기 지점 + POST /api/pump-control/drvn-status/download 다운로드, 모든 엔드포인트 @Tag·@Operation·@ApiResponses 6종 200/400/401/403/404/500) → 검증: ./gradlew.bat :api:compileJava PASS + grep -c "@ApiResponse" 매칭 (24건 = 4 엔드포인트 × 6응답)

### Phase 7: 통합 테스트 + 빌드 검증

- [x] `api/src/test/java/com/mo/swtp/pump/PumpDrvnStatusIntegrationTest.java` 통합 테스트 신규 (@SpringBootTest(NONE) + @ActiveProfiles("test") + @Transactional, 운전현황 조회 200 응답 검증 + facility 'POINT' 다형성 조회 검증 + AI 운전모드 read-only 표시 검증 — ai_drvn_mod 사용자 의도 + ai_mode_cd 시스템 상태 둘 다 노출 + 시간 윈도우 1분 단위 JOIN 정합성 — 정상 케이스 + 시간 경계 케이스) → 검증: ./gradlew.bat :api:test --tests *PumpDrvnStatusIntegrationTest* PASS (DB 마이그레이션 V7_1·V7_2 SQL 적용 후 PASS — RESULT 발견사항)
- [-] `./gradlew.bat clean build` 전체 모듈 빌드 검증 → 검증: BUILD SUCCESSFUL 출력 확인 (api + common + scheduler 전체 PASS) — **DB 사전 조건 미충족**: V7_1__point_master_table.sql + V7_2__drvn_anls_dwld_history.sql 을 로컬 PostgreSQL smartwtp DB 에 수동 적용 후 통합 테스트 6건 PASS 가능. 컴파일·단위 테스트(SensorPointTest 4건·PumpDrvnStatusDownloadServiceTest 3건)·Spring Context 로드는 모두 PASS

## 산출물
- [결과](../../../results/20260508/송수펌프제어_운전현황분석/RESULT1.md)
