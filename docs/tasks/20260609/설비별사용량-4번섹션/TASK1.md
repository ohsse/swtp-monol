---
status: completed
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 4번섹션 — 계측기 전력량 트렌드 조회 API 태스크

## 관련 계획
- [계획안](../../../plan/20260609/설비별사용량-4번섹션/PLAN1.md) (status: approved)

## Phase

> 신규 산출물 5건(검색 DTO·응답 DTO·Service·Controller·단위 테스트). 신규 엔티티·DB·마이그레이션·ErrorCode 0건(PLAN1 §배경). 모두 `api` 모듈 `com.mo.swtp.instrument` 패키지.

### Phase 1: 검색 DTO

- [x] `api/src/main/java/com/mo/swtp/instrument/dto/InstrumentEnergyTrendSearchDto.java` 생성 — `inqUnit`(InqUnit)·`fromDt`(LocalDate)·`toDt`(LocalDate) 필드, `@Getter @Setter @NoArgsConstructor`, `@DateTimeFormat(iso=ISO.DATE)`, 상수 MAX_PERIOD_DAYS=396L → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/InstrumentEnergyTrendSearchDto.java` isValid()/변환 메서드 작성 — isValid()는 3필드 non-null + fromDt 미역전 + inqUnit YEAR 거부 + 간격 396일 이하, toStartDtm()=fromDt.atStartOfDay(), toEndExclusiveDtm()=toDt.plusDays(1).atStartOfDay() → 검증: FacilityEnergyTrendSearchDto 동형 복제 여부 확인 (상속 아님, DTO 예외 throw 없음)

### Phase 2: 응답 DTO

- [x] `api/src/main/java/com/mo/swtp/instrument/dto/InstrumentEnergyTrendDto.java` 생성 — outer(instrumentId·instrumentNm·unit·points) + 중첩 정적 EnergyTrendPoint(baseDtm @JsonFormat yyyy-MM-dd HH:mm:ss·elcegVal BigDecimal), @Getter + private 생성자 + 정적 팩토리 of(), points는 @ArraySchema(@Schema implementation=EnergyTrendPoint.class), BaseAuditResponseDto 미상속 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 3: Service

- [x] `api/src/main/java/com/mo/swtp/instrument/service/InstrumentEnergyTrendService.java` 생성 — @Service @RequiredArgsConstructor @Transactional(readOnly=true), 의존 InstrumentRepository·TagRepository·RawDataRepository, 상수 POWER_ENERGY_TYPE=PWQ·UNIT_KWH="kWh" → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/instrument/service/InstrumentEnergyTrendService.java` findEnergyTrend 흐름 작성 — isValid false→INVALID_INQ_PERIOD, findActiveInstrumentOrThrow(findById+useYn=Y else INSTRUMENT_NOT_FOUND), collectPwqTags(PWQ 필터), 0건→빈 points, aggregateBuckets(findEnergyDeltaBuckets→TreeMap merge 합산, null·음수 차분 제외), baseDtm 오름차순 조립 → 검증: 다중 PWQ 합산 정책 Javadoc 명기 확인 (PumpPowerTimeSeriesService 첫-태그 가정과 의도적 차이)

### Phase 4: Controller

- [x] `api/src/main/java/com/mo/swtp/instrument/web/InstrumentEnergyTrendController.java` 생성 — CommonController 상속, @RestController @RequestMapping("/api/instrument"), @Tag("15. 설비별 사용량"), @GetMapping("/{instrumentId}/energy-trend"), @PathVariable instrumentId + @ModelAttribute search → getResponseEntity, @Operation + @ApiResponses(200/400/401/403/404/500) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 5: 단위 테스트

- [x] `api/src/test/java/com/mo/swtp/instrument/service/InstrumentEnergyTrendServiceTest.java` 생성 — @ExtendWith(MockitoExtension.class), @Mock 3종 + @InjectMocks, 기간 무효 4종(null·역전·YEAR·397일)→INVALID_INQ_PERIOD / 미존재·비활성 계측기 2종→INSTRUMENT_NOT_FOUND / PWQ 버킷 baseDtm 오름차순 매핑 / 음수 차분 제외 / 다중 PWQ 동일 baseDtm 합산 / PWQ 무보유·데이터 0건→빈 points / MONTH 경계 전달 → 검증: ./gradlew.bat :api:test 해당 클래스 전체 GREEN

### Phase 6: 빌드 검증

- [x] `./gradlew.bat :api:test` 실행 → 검증: InstrumentEnergyTrendServiceTest 전체 GREEN, 기존 테스트 회귀 없음
- [x] `./gradlew.bat clean build` 실행 → 검증: BUILD SUCCESSFUL 출력 확인

## 산출물
- [결과](../../../results/20260609/설비별사용량-4번섹션/RESULT1.md)
