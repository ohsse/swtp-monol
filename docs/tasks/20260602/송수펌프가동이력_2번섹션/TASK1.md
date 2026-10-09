---
status: completed
created: 2026-06-02
updated: 2026-06-02
---
# 송수펌프 가동이력 2번섹션 — 펌프 상태 카드 API (작업 분해)

## 관련 계획
- [계획안](../../../plan/20260602/송수펌프가동이력_2번섹션/PLAN1.md)

## Phase

> 검증 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}` (검증 영역 백틱 금지 — 훅 파싱 호환).

### Phase 1: 응답 DTO

- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpOperationRateDto.java` 신규 작성 (읽기 전용, private 생성자 + 정적 팩토리 of, BaseAuditResponseDto 미상속) → 검증: 필드 8개(pumpId·pumpNm·driveType·ratedHead·ratedFlwrt·oprtngRate·qualityCd·acqDtm) 선언 + driveType/qualityCd 에 @Schema(implementation) + acqDtm 에 @JsonFormat(yyyy-MM-dd HH:mm:ss) 존재 확인
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpOperationRateDto.java` 정적 팩토리 of(Pump, oprtngRate, qualityCd, acqDtm) 작성 → 검증: oprtngRate·qualityCd·acqDtm 3개 인자가 nullable 로 수용되는 시그니처 확인

### Phase 2: 리포지토리 파생 쿼리

- [x] `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentRepository.java` 파생 쿼리 findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(EquipType, YnType) 추가 (@BatchSize(size=100)) → 검증: ./gradlew.bat :api:compileJava 성공 (메서드명 파생 쿼리 파싱 통과)

### Phase 3: 서비스

- [x] `api/src/main/java/com/mo/swtp/instrument/service/PumpOperationRateService.java` 신규 작성 (@Service @RequiredArgsConstructor @Transactional(readOnly=true), 의존성 InstrumentRepository·TagRepository·RawDataRepository) → 검증: 4-SELECT 파이프라인(활성PUMP→OPS/FQI태그→최신값→인메모리매핑) public 조회 메서드 1개 + FacilityOperatingStatusService 상속/주입 없음 확인
- [x] `api/src/main/java/com/mo/swtp/instrument/service/PumpOperationRateService.java` private 헬퍼 effectiveVal(RawDataLatestDto) 작성 (corrVal != null ? corrVal : rawVal) → 검증: FQI 산정 경로에서만 호출됨 확인
- [x] `api/src/main/java/com/mo/swtp/instrument/service/PumpOperationRateService.java` private 헬퍼 computeOprtngRate(Pump, RawDataLatestDto ops, RawDataLatestDto fqi) 작성 → 검증: RATED는 ops rawVal 단독(GOOD+1.0→100,0.0→0,그외→null), INVERTER는 fqi GOOD→effectiveVal(%),그외→null 분기 + 메서드 50줄 이하 확인

### Phase 4: 컨트롤러

- [x] `api/src/main/java/com/mo/swtp/instrument/web/PumpOperationRateController.java` 신규 작성 (@RestController, CommonController 상속, @Tag "11. 송수펌프 가동이력", @GetMapping /api/instrument/pump-operation-rate, getResponseEntity 래핑) → 검증: ResponseEntity<CommonResponseDto<List<PumpOperationRateDto>>> 반환 + @Operation/@ApiResponses(200·400·401·403·500) 존재 확인

### Phase 5: 단위 테스트 (Mockito 7 케이스)

- [x] `api/src/test/java/com/mo/swtp/instrument/service/PumpOperationRateServiceTest.java` 신규 작성 (@ExtendWith(MockitoExtension.class), 한국어 메서드명) → 검증: ./gradlew.bat :api:test --tests com.mo.swtp.instrument.service.PumpOperationRateServiceTest 실행 7건 PASS (①정격 OPS GOOD raw=1.0→100 ②정격 OPS GOOD raw=0.0→0 ③정격 OPS BAD/UNCERTAIN→null+quality동봉 ④정격 OPS 태그없음→null,quality null ⑤인버터 FQI GOOD→effectiveVal% ⑥인버터 FQI BAD/null→null+quality동봉 ⑦인버터 FQI 태그없음→null)

### Phase 6: 빌드 검증

- [x] `./gradlew.bat :api:test` 실행 → 검증: PumpOperationRateServiceTest 포함 api 모듈 전체 테스트 PASS
- [x] `./gradlew.bat build` 실행 → 검증: BUILD SUCCESSFUL 출력 (QClass 재생성 포함)

## 산출물
- [결과](../../../results/20260602/송수펌프가동이력_2번섹션/RESULT1.md)
