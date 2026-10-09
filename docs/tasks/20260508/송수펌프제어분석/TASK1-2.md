---
status: completed
created: 2026-05-08
updated: 2026-05-11
---
<!-- Phase 5~9 (Repository 4건·Service 3건·DTO 9건·Controller 2건·BUILD) 완료 — 2026-05-11. :api:compileJava BUILD SUCCESSFUL + 단위 테스트 10건 GREEN. 통합 테스트 6건 회귀 검증은 TASK1-3 범위 (DB 마이그레이션 적용 후). -->

# 송수펌프제어분석 — TASK1-2 (애플리케이션 계층)

## 관련 계획
- [PLAN1](../../../plan/20260508/송수펌프제어분석/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 데이터 계층](TASK1-1.md)
- [TASK1-2 애플리케이션 계층](TASK1-2.md) (현재 파일)
- [TASK1-3 통합 검증·회귀](TASK1-3.md)

## 묶음 범위

Repository 표준 메서드 4건 + Service 3건 + DTO 9건 + Controller 2건 + Swagger 어노테이션. TASK1-1 이 enum·엔티티·마이그레이션 SQL 까지 마쳐야 본 묶음 진입 가능 (`Pump.ratedHead`·`DistributionWaterTank.minReqPrsr`·`PressureBoosterStation`·`FacilityType.PRSF`·`TagMeasurementType.OPS·VOI` 의존).

본 묶음 완료 후 `./gradlew.bat :api:build` BUILD SUCCESSFUL + Swagger `08·09 Tag` 노출이 보장되어야 TASK1-3 진입 가능.

---

## Phase

### Phase 5: Repository 표준 메서드 4건

- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepository.java` 신규 작성 — 메서드 시그니처 2건 (`findFacilitiesHavingDwtChild` · `findFirstChildByParentIdAndType`) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL (2026-05-11)
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepositoryImpl.java` 신규 작성 — `findFacilitiesHavingDwtChild` Querydsl 구현 (JPAExpressions exists 서브쿼리 1회, 자식 facility_type_cd=DWT + use_yn=Y 필터, 부모 facility_type_cd IN parentTypes + use_yn=Y) → 검증: 단위 테스트 mock 처리 — Phase 6 Service 테스트에서 통합 검증 (Repository 단독 테스트는 Querydsl exists 시그니처 검증만 의미 — 실 SQL 은 통합 테스트로 검증, TASK1-3 회귀)
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepositoryImpl.java` `findFirstChildByParentIdAndType` Querydsl 구현 (where parent_facility_id = ? AND facility_type_cd = ? AND use_yn=Y, ORDER BY disp_ord ASC LIMIT 1, fetchFirst()) → 검증: TASK1-3 통합 테스트에서 disp_ord=1 행 1건 응답 확인
- [x] `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentCustomRepository.java` 신규 작성 — `findByFacilityIdAndEquipType(facilityId, List<EquipType>)` 시그니처 추가 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL (2026-05-11)
- [x] `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentCustomRepositoryImpl.java` 신규 작성 — Querydsl 구현 (where facility_id = ? AND equip_type_cd IN (?...) AND use_yn=Y, ORDER BY disp_ord ASC) — entity-patterns.md §JPA JOINED §도메인 룰 정합 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` 신규 작성 — `Map<String, RawData> findLatestByTagSrlNos(List<String> tagSrlNos, LocalDateTime acqDtmFrom)` 시그니처 추가 (acqDtmFrom 파티션 프루닝 강제 파라미터) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` 신규 작성 — Querydsl 구현 (`tagSrlNo IN (?...) AND acqDtm >= acqDtmFrom AND acqDtm = (SELECT MAX(rSub.acqDtm) FROM RawData rSub WHERE rSub.tagSrlNo = r.tagSrlNo AND rSub.acqDtm >= acqDtmFrom)`) — 단일 SQL + 파티션 프루닝 보장 + Map<tag_srl_no, RawData> 응답 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL. EXPLAIN ANALYZE 파티션 프루닝 검증은 TASK1-3 통합 테스트 범위

### Phase 6: Service 3건

- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityListService.java` 신규 작성 — `findFacilitiesHavingDwtChild()` parentTypes=[PWTF, PRSF] 고정 + `@Transactional(readOnly = true)` → 검증: BUILD SUCCESSFUL + `api/src/test/java/com/mo/swtp/facility/service/FacilityListServiceTest.java` 2건 GREEN (2026-05-11)
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpAnalysisDashboardService.java` 신규 작성 — `buildDashboard(facilityId)` 7섹션 오케스트레이션 (시설 검증 → §2 운영현황 → §5 토출 압력 → §6 가동 대수 → §7 분석 결과) + `@Transactional(readOnly = true)` + 시설 유형 PWTF/PRSF 필터 → 검증: BUILD SUCCESSFUL
- [x] `acqDtmFrom = LocalDateTime.now().minusMinutes(15)` — 15분 이내 최신 측정값만 조회 (LATEST_WINDOW_MINUTES 상수) → 검증: PumpAnalysisDashboardServiceTest.acqDtmFrom_파라미터는_now_minus_15분이다 ArgumentCaptor GREEN
- [x] §6 펌프 가동대수 집계 — pumps OPS 측정값 > 0 카운트 (서버 집계) → 검증: PumpAnalysisDashboardServiceTest.운전중_펌프_대수_집계 4건 mock (3건 OPS=1, 1건 OPS=0) → operatingPumpCount=3 GREEN
- [x] §7 펌프별 예측 분해 — pump_cmbn_d 매핑으로 ON/OFF 분해 → 검증: PumpAnalysisDashboardServiceTest.펌프별_예측_분해 pump_cmbn_cd="CMBN-1" 매핑 ins-1·ins-3 ON, ins-2·ins-4 OFF GREEN
- [x] `api/src/main/java/com/mo/swtp/facility/service/DwtStatusService.java` 신규 작성 — `buildStatus(facilityId)` §3 자식 DWT 목록 + IN 절 N+1 회피 + §4 기준배수지 응답 + `@Transactional(readOnly = true)` → 검증: BUILD SUCCESSFUL
- [x] §4 기준배수지 — `findFirstChildByParentIdAndType(facilityId, DWT)` + DistributionWaterTank 캐스트 + `getMinReqPrsr()` 응답 → 검증: DwtStatusServiceTest.기준배수지는_disp_ord_ASC_1번째 DWT 2건 (disp_ord 1·2) 중 1번째의 minReqPrsr 2.5 응답 GREEN
- [x] §3 FLWMTR io_cd 입구/출구 분리 — INPUT → inflowFlwrt, OUTPUT → outflowFlwrt → 검증: DwtStatusServiceTest.FLWMTR_io_cd_INPUT_과_OUTPUT_을_분리 FLWMTR 2건 mock (TAG-IN io_cd=INPUT, TAG-OUT io_cd=OUTPUT) → inflowFlwrt=100.0, outflowFlwrt=95.0 GREEN

### Phase 7: DTO 9건 (Swagger @Schema 명시 의무)

- [x] `api/src/main/java/com/mo/swtp/pump/dto/FacilityListDto.java` 신규 작성 — `facilityId` · `facilityNm` · `facilityType` (FacilityType implementation) · `dispOrd` · `mainYn` (YnType implementation) + `from(Facility)` 정적 팩토리 → 검증: BUILD SUCCESSFUL + grep "implementation = FacilityType.class" 매칭 (2026-05-11)
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpAnalysisDashboardDto.java` 신규 작성 — 7섹션 통합 응답 + 4 섹션 DTO @Schema(implementation) 명시 → 검증: grep "implementation = OperationStatusDto.class" 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/section/OperationStatusDto.java` 신규 작성 — §2 운영현황 (`dischargeFlwrt` · `dischargePrsr` · `pumps` List<PumpStateDto> 기존 재사용 · `aiModeCd` AiSystemModeCode · `aiDrvnMod` AiDrvnModeType) + Javadoc "ai_mode_cd 우선 표시 권고, ai_drvn_mod 참고용" 명시 → 검증: grep "ai_mode_cd 우선 표시 권고" OperationStatusDto.java 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/dto/section/DischargePressureDto.java` 신규 작성 — §5 (`facilityId` · `facilityNm` · `dischargePrsr` BigDecimal) → 검증: BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/pump/dto/section/PumpOperatingCountDto.java` 신규 작성 — §6 (`facilityId` · `operatingPumpCount` · `totalPumpCount`) → 검증: BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/pump/dto/section/AnalysisResultDto.java` 신규 작성 — §7 (`predcBaseDtm` · `predcDtm` · `pumpStates` List<PumpPredcState> 내부 클래스 · `predcDischargePrsr` · `predcDischargeFlwrt`). PumpPredcState 내부 클래스에 `instrumentId`·`instrumentNm`·`predcOprtngYn`(YnType) 정의 → 검증: BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/facility/dto/DwtStatusDto.java` 신규 작성 — §3+§4 통합 (`mainFactor` List<MainFactorDto> · `dwtRequirePressure` DwtRequirePressureDto) → 검증: grep "implementation = MainFactorDto.class" 매칭
- [x] `api/src/main/java/com/mo/swtp/facility/dto/section/MainFactorDto.java` 신규 작성 — §3 (`dwtId` · `dwtNm` · `inflowPrsr` · `inflowFlwrt` · `valveOpening` · `wtlv` · `outflowFlwrt`) → 검증: BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/facility/dto/section/DwtRequirePressureDto.java` 신규 작성 — §4 기준배수지 1건 (`referenceDwtId` · `referenceDwtNm` · `minReqPrsr` · `dispOrd`) + Javadoc "disp_ord ASC 1번째" 명시 → 검증: grep "disp_ord ASC 1번째" DwtRequirePressureDto.java 매칭

### Phase 8: Controller 2건 + Swagger

- [x] `api/src/main/java/com/mo/swtp/pump/web/PumpControlAnalysisController.java` 신규 작성 — `@Tag(name = "08. 송수펌프 제어 분석")` + 2 GET 엔드포인트 (`/facilities`·`/dashboard?facilityId={UUID}`) + `@Operation summary` 명시 + `@ApiResponses` 6 코드 (200/400/401/403/404/500) + `CommonController` 상속 + `getResponseEntity(data)` 헬퍼 사용 → 검증: BUILD SUCCESSFUL + grep "08. 송수펌프 제어 분석" 매칭 (2026-05-11)
- [x] PumpControlAnalysisController 응답 타입 — `ResponseEntity<CommonResponseDto<List<FacilityListDto>>>` (facilities) + `ResponseEntity<CommonResponseDto<PumpAnalysisDashboardDto>>` (dashboard) → 검증: grep "CommonResponseDto<PumpAnalysisDashboardDto>" 매칭
- [x] `api/src/main/java/com/mo/swtp/facility/web/DwtStatusController.java` 신규 작성 — `@Tag(name = "09. 배수지 모니터링")` + 1 GET 엔드포인트 (`/api/dwt-status?facilityId={UUID}`) + `@Operation summary` 명시 + `@ApiResponses` 6 코드 + CommonController 상속 → 검증: BUILD SUCCESSFUL + grep "09. 배수지 모니터링" 매칭
- [x] DwtStatusController 응답 타입 — `ResponseEntity<CommonResponseDto<DwtStatusDto>>` → 검증: grep "CommonResponseDto<DwtStatusDto>" 매칭

### Phase 9: BUILD 검증

- [x] `./gradlew.bat :api:compileJava` BUILD SUCCESSFUL (2026-05-11) + TASK1-2 범위 단위 테스트 10건 GREEN — FacilityListServiceTest 2건 · DwtStatusServiceTest 2건 · PumpAnalysisDashboardServiceTest 6건 (XML tests=2/2/6 failures=0/0/0 확인). **회귀 정렬 1건** — TASK1-1 의 `Pump.create(...)` 시그니처 확장 (ratedHead/ratedFlwrt/tagNm 추가) 으로 기존 테스트 2건 (PumpControlIntegrationTest L121, PumpMasterCacheServiceTest L50) 시그니처 정렬 완료. **통합 테스트 6건 실패** (PumpDrvnStatusIntegrationTest 2건 + DrvnAnlsDwldHistoryRepositoryTest 2건 + 추가 2건) 는 DB 환경 의존 (V8_2 `pump_m.rated_head/rated_flwrt/tag_nm` NOT NULL + V8_4 `pump_predc_h.facility_id` 정렬 미적용) — **TASK1-3 운영 절차 범위** (로컬 PostgreSQL V8_1~V8_4 마이그레이션 적용 + `ANALYZE pump_m; ANALYZE dwt_m;` 통계 재수집 후 통합 테스트 재실행 + 06·07 회귀 검증)

---

## 산출물

본 분할 완료 후 TASK1-3 (통합 검증·회귀) 진입.

- [태스크 1-3](TASK1-3.md)
