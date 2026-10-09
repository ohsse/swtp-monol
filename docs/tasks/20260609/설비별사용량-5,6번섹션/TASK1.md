---
status: completed
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 5·6번섹션 — 설비별 누적 전력량 + 분포율 조회 API 태스크

## 관련 계획
- [계획안](../../../plan/20260609/설비별사용량-5,6번섹션/PLAN1.md) (status: approved)

## Phase

> 신규 산출물 5건(검색 DTO·응답 DTO·Service·Controller·단위 테스트). 신규 엔티티·DB·마이그레이션·ErrorCode·표준 어휘 0건(PLAN1 §배경, ANALYZE1 5인 회의 결론). 모두 `api` 모듈 `com.mo.swtp.facility` 패키지. 사이클 간 자산 자동 원용 없이 구조만 미러링한 신규 작성(`feedback_no_auto_reuse_cross_cycle.md`).

### Phase 1: 검색 DTO

- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityInstrumentEnergyUsageSearchDto.java` 생성 — `fromDt`(LocalDate)·`toDt`(LocalDate) 필드, `@Getter @Setter @NoArgsConstructor`, `@DateTimeFormat(iso=ISO.DATE)`, 상수 MAX_PERIOD_DAYS=396L → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityInstrumentEnergyUsageSearchDto.java` isValid()/변환 메서드 작성 — isValid()는 from/to non-null + fromDt 미역전 + 간격 396일 이하(YEAR 검사 없음·inqUnit 부재), toStartDtm()=fromDt.atStartOfDay(), toEndExclusiveDtm()=toDt.plusDays(1).atStartOfDay() → 검증: FacilityEnergyUsageSearchDto 에서 inqUnit 제거한 동형 복제 여부 확인 (상속 아님, DTO 예외 throw 없음)

### Phase 2: 응답 DTO

- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityInstrumentEnergyUsageDto.java` 생성 — 래퍼(unit String·totalElceg BigDecimal·items List) + 중첩 정적 InstrumentEnergyUsageItem(instrumentId·instrumentNm·equipTypeCd EquipType·facilityId·facilityNm·elceg BigDecimal·ratio BigDecimal), @Getter + private 생성자 + 정적 팩토리 of(), items는 @ArraySchema(@Schema implementation=InstrumentEnergyUsageItem.class), equipTypeCd는 @Schema(implementation=EquipType.class), ratio @Schema에 합 100.0 불일치 가능 명기, BaseAuditResponseDto 미상속 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 3: Service

- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityInstrumentEnergyUsageService.java` 생성 — @Service @RequiredArgsConstructor @Transactional(readOnly=true) @Slf4j, 의존 FacilityRepository·InstrumentRepository·TagRepository·RawDataRepository, 상수 POWER_ENERGY_TYPE=PWQ·UNIT_KWH="kWh"·BUCKET_UNIT=InqUnit.DAY.getDateTruncUnit()·MAX_DEPTH=10·HUNDRED=new BigDecimal("100")·RATE_SCALE=1 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityInstrumentEnergyUsageService.java` findInstrumentEnergyUsage 흐름 작성 — isValid false→INVALID_SEARCH_PERIOD, findActiveFacilityOrThrow(findById+useYn=Y else FACILITY_NOT_FOUND), collectSubtree(루트 inclusive BFS·visited·MAX_DEPTH), 계측기 IN 0건→빈 래퍼, collectPwqByInstrument(PWQ 필터+tagSrlNo→instrumentId 맵+보유 집합)·0건→빈 래퍼, aggregateEnergyByInstrument(findEnergyDeltaBuckets day→validDeltaOrNull 음수·null 제외+WARN→instrumentId merge), PWQ 보유 전체 0 default, totalElceg 합산·computeRate(scale1 HALF_UP·total0→0), 시설dispOrd→계측기dispOrd→계측기명 정렬 → 검증: validDeltaOrNull·collectSubtree·findActiveFacilityOrThrow 3번째 동형복제 Javadoc 인용(ANALYZE1 §3 결론) + 다중 PWQ 합산 정책 Javadoc 명기 확인

### Phase 4: Controller

- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityInstrumentEnergyUsageController.java` 생성 — CommonController 상속, @RestController @RequestMapping("/api/facility"), @Tag("15. 설비별 사용량"), @GetMapping("/{facilityId}/instrument-energy-usage"), @PathVariable facilityId + @ModelAttribute search → getResponseEntity, @Operation + @ApiResponses(200/400/401/403/404/500) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 5: 단위 테스트

- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityInstrumentEnergyUsageServiceTest.java` 생성 — @ExtendWith(MockitoExtension.class), @Mock 4종 + @InjectMocks, 기간 무효 3종(null·역전·397일)→INVALID_SEARCH_PERIOD / 미존재·비활성 시설 2종→FACILITY_NOT_FOUND / 재귀 하위 BFS 2단계 손자 계측기 집계 / 2설비×다PWQ태그 instrumentId 합산 / 음수·null 차분 제외 / 분포율 elceg/total×100 scale1 HALF_UP / total0→전 ratio 0.0 / PWQ보유·전력량0 설비 elceg0·ratio0 포함 / 계측기·PWQ 0건→빈 래퍼(예외 아님) / 시설dispOrd→계측기dispOrd→계측기명 정렬 → 검증: ./gradlew.bat :api:test 해당 클래스 전체 GREEN

### Phase 6: 빌드 검증

- [x] `./gradlew.bat :api:test` 실행 → 검증: FacilityInstrumentEnergyUsageServiceTest 전체 GREEN, 기존 테스트 회귀 없음
- [x] `./gradlew.bat clean build` 실행 → 검증: BUILD SUCCESSFUL 출력 확인

## 산출물
- [결과](../../../results/20260609/설비별사용량-5,6번섹션/RESULT1.md)
