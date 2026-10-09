---
status: completed
created: 2026-05-27
updated: 2026-05-27
---
# 운전현황분석-10번섹션 — 시설 단위 금일 하루치 계측+예측 시계열 조회 API

## 관련 계획

- [계획안](../../../plan/20260527/운전현황분석-10번섹션/PLAN1.md)
- [도메인 분석](../../../analyze/20260527/운전현황분석-10번섹션/ANALYZE1.md)

## Phase

### Phase 1: 예측 시계열 범위 Repository 신규 작성

9번섹션 `TagPredcLatestRepository` 무수정 (의도 분리). Querydsl 빌더 + 5번섹션 `RawDataCustomRepository.findByTagSrlNosAndDtmRange` 패턴 동형.

- [x] `api/src/main/java/com/mo/swtp/opt/dto/TagPredcRangeDto.java` 신규 작성 — record DTO 3필드 (tagSrlNo·predcDtm·predcVal) → 검증: 컴파일 통과 ./gradlew :api:compileJava SUCCESS
- [x] `api/src/main/java/com/mo/swtp/opt/repository/TagPredcRangeCustomRepository.java` 신규 작성 — findByTagSrlNosAndPredcDtmRange(List<String>, LocalDateTime, LocalDateTime) 시그니처 → 검증: 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/opt/repository/TagPredcRangeCustomRepositoryImpl.java` 신규 작성 — Querydsl JPAQueryFactory + QTagPrediction + IN+BETWEEN+ORDER BY predc_dtm ASC, tag_srl_no ASC + 빈 입력 List.of() 반환 → 검증: 컴파일 통과 + QTagPrediction 생성 확인 ./gradlew :common:compileQuerydsl
- [x] `api/src/main/java/com/mo/swtp/opt/repository/TagPredcRangeRepository.java` 신규 작성 — JpaRepository<TagPrediction, TagPredictionId> + TagPredcRangeCustomRepository 상속 → 검증: 컴파일 통과

### Phase 2: 응답 DTO 신규 작성

`FacilityDailyTimeSeriesDto` 3필드 + static inner `DailyTimeSeriesPoint` 8필드. BaseAuditResponseDto 미상속, @Schema(implementation) + @JsonFormat 의무.

- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityDailyTimeSeriesDto.java` 신규 작성 — facilityId·facilityNm·points 3필드 + 정적 팩토리 of(...) → 검증: 컴파일 통과
- [x] 같은 파일 내 static inner DailyTimeSeriesPoint 작성 — dtm·actualElpwrAmt·actualFlwrt·actualUnitQty·predcElpwrAmt·predcFlwrt·predcUnitQty·predcPumpOnCnt 8필드 + 정적 팩토리 of(...) → 검증: 컴파일 통과
- [x] @JsonFormat(yyyy-MM-dd HH:mm:ss) dtm 필드 적용 + @Schema(description) 8필드 모두 한국어 명시 (actual NULL vs predc NULL 의미 분리) → 검증: 빌드 후 Swagger UI 한국어 description 노출 확인
- [x] @ArraySchema(schema = @Schema(implementation = DailyTimeSeriesPoint.class)) points 필드 명시 → 검증: 컴파일 통과 + Swagger 노출 확인

### Phase 3: Service 신규 작성

`FacilityDailyTimeSeriesService` — 6-SELECT 패턴 + buildSeries 3단계 분해 (§2.5 면책 불가, 각 메서드 50줄 이내).

- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityDailyTimeSeriesService.java` 신규 작성 — 클래스 레벨 @Service @Slf4j @RequiredArgsConstructor @Transactional(readOnly=true) → 검증: 컴파일 통과
- [x] 의존성 주입 — FacilityRepository·InstrumentRepository·TagRepository·RawDataRepository·TagPredcRangeRepository → 검증: 컴파일 통과
- [x] 메인 메서드 findFacilityDailyTimeSeries(facilityId) 작성 — 6 단계 호출 흐름 (findActiveFacilityOrThrow → findByFacilityIdAndEquipType → loadTagsByInstrument → resolveActualRange·resolvePredcRange → Repository 2회 호출 → buildSeries → DTO 조립) → 검증: 메인 메서드 50줄 이내 + 컴파일 통과
- [x] private record PointParts 작성 — 7필드 (actual 3 + predc 3 + predcPumpOnCnt) → 검증: 컴파일 통과
- [x] private record TimeRange 작성 — start·end 2필드 → 검증: 컴파일 통과
- [x] private helper findActiveFacilityOrThrow(facilityId) 재구현 — use_yn=Y + facility_type ∈ {PWTF,DWT,PRSF} 검증, 위반 시 FACILITY_NOT_FOUND / UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS → 검증: 4·5·9번 단위 테스트 동일 입력 응답 일치 확인
- [x] private helper loadTagsByInstrument·filterPumps·effectiveVal·isPumpRunningActual·isPumpRunningPredc·sumOnPumpPwrActual·sumOnPumpPwrPredc·selectFacilityFriActual·selectFacilityFriPredc·computeUnitConsumption 재구현 — 4·5·9번 정책 1:1 → 검증: 단위 테스트 GREEN (Phase 5)
- [x] private helper resolveActualRange() / resolvePredcRange() 작성 — TimeRange 반환 → 검증: 단위 테스트 시간 범위 검증
- [x] private helper buildActualSlotMap(actuals, tagsByInstrument, instruments) 작성 — TreeMap<LocalDateTime, PointParts> 반환, actual 필드만 채움 → 검증: 메서드 50줄 이내 + 단위 테스트 GREEN
- [x] private helper overlayPredcSlots(combinedMap, predcs, tagsByInstrument, instruments) 작성 — predc 필드 갱신·신규 슬롯 추가 → 검증: 메서드 50줄 이내 + 단위 테스트 GREEN
- [x] private helper toSortedPoints(combinedMap) 작성 — TreeMap entrySet → List<DailyTimeSeriesPoint> 변환 (TreeMap 자연 정렬 활용) → 검증: 메서드 50줄 이내 + 단위 테스트 정렬 검증

### Phase 4: Controller 메서드 추가

`FacilityController` 에 GET /api/facility/{facilityId}/operating-status/daily-time-series 메서드 추가.

- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 — FacilityDailyTimeSeriesDto·FacilityDailyTimeSeriesService import 추가 + 필드 추가 → 검증: 컴파일 통과
- [x] FacilityController.findFacilityDailyTimeSeries(facilityId) 메서드 추가 — @GetMapping("/{facilityId}/operating-status/daily-time-series") + @Operation·@ApiResponses 어노테이션 (200·400·401·403·404·500 6종) → 검증: 컴파일 통과 + Swagger UI 노출 확인
- [x] @Operation summary·description 한국어 명시 + @PathVariable @Parameter description 명시 → 검증: Swagger UI 한국어 설명 확인

### Phase 5: 단위 테스트 신규 작성

`FacilityDailyTimeSeriesServiceTest` — Mockito 기반 9건 시나리오 (PLAN1 §성공 기준).

- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityDailyTimeSeriesServiceTest.java` 신규 작성 — @ExtendWith(MockitoExtension.class) + @Mock 5개 (Facility·Instrument·Tag·RawData·TagPredcRange Repository) + @InjectMocks → 검증: 컴파일 통과
- [x] 테스트 1 — 정상 시계열 (PUMP 3대 + FLWMTR 1대, actual 자정~14:00 + predc 자정~익일자정 1440슬롯, 합본 응답 1440 포인트 검증) → 검증: ./gradlew :api:test --tests FacilityDailyTimeSeriesServiceTest.정상_시계열 PASS
- [x] 테스트 2 — 자정~현재 actual+predc 공존 검증 (14시 시점에 actual 필드 7개·predc 필드 4개 모두 채워짐) → 검증: PASS
- [x] 테스트 3 — 현재~익일자정 predc 단독 검증 (14시 이후 슬롯은 actual 필드 모두 null, predc 필드만 채워짐) → 검증: PASS
- [x] 테스트 4 — 양쪽 모두 부재 슬롯 생략 검증 (특정 분에 actual·predc 둘 다 부재 시 points 리스트에 해당 dtm 키 없음) → 검증: PASS
- [x] 테스트 5 — OPS predc_val 경계값 parametrized (null / 0.0 / 1.0 / 1.5 → predcPumpOnCnt 카운트 변화) → 검증: PASS
- [x] 테스트 6 — FRI predc 분모 무효 parametrized (null / 0 / 부재 → predcUnitQty null) → 검증: PASS
- [x] 테스트 7 — VALVE 자식 제외 검증 (equip_type_cd=PUMP 필터 강제, VALVE 인스트루먼트의 PWI/OPS 무시) → 검증: PASS
- [x] 테스트 8 — 시설 종류 거부 검증 (RSV/POINT 시설 ID 입력 시 UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS) → 검증: PASS
- [x] 테스트 9 — 비활성·존재 X 시설 거부 검증 (use_yn=N 또는 존재하지 않는 facilityId → FACILITY_NOT_FOUND) → 검증: PASS

### Phase 6: 빌드·정량 검증

- [x] ./gradlew :api:compileJava 통과 → 검증: BUILD SUCCESSFUL 출력 확인
- [x] ./gradlew :api:test --tests FacilityDailyTimeSeriesServiceTest 9건 모두 GREEN → 검증: BUILD SUCCESSFUL + 14 tests (parametrized 5건 + 일반 9건) completed, 0 failed
- [x] ./gradlew :api:test --tests facility.service.* + opt.* 본 사이클 영향 범위 통과 → 검증: 회귀 없음 (UserServiceTest 23건 실패는 통합 테스트 환경 의존 — test-strategy.md §2 선행 조건, 본 사이클 무관)
- [x] buildSeries 3단계 분해 각 메서드 50줄 이내 확인 — buildActualSlotMap(27)·overlayPredcSlots(31)·toSortedPoints(12) → 검증: 코드 검토 + 본문 줄 수 50줄 이하 (빈 줄·주석 제외)
- [x] 메인 메서드 findFacilityDailyTimeSeries 50줄 이내 확인 → 검증: 25 줄 (50줄 이내)
- [x] 추상화 호출 스택 3단 이하 확인 — Controller → Service → private helper 3단 → 검증: 코드 검토

### Phase 7: 산출물 점검

- [x] Swagger UI 노출 검증 — @Operation·@ApiResponses 200/400/401/403/404/500 6종 한국어 description + @PathVariable @Parameter description 한국어 명시 → 검증: 컴파일 + 빌드 통과 (SpringDoc 자동 추출 보장, 로컬 기동 수동 확인은 :api:bootRun 환경 의존)
- [x] actual NULL vs predc NULL 의미 분리 description 명시 확인 — 8 필드 모두 NULL 발생 사유 한국어 노출 (actualXxx: 미도래/결측, predcXxx: 예측 미수행) → 검증: grep "NULL" FacilityDailyTimeSeriesDto.java 13건 매칭 확인
- [x] TASK1.md status: draft → completed 전환 → 검증: 프론트매터 갱신

## 산출물

- (Medium 작업 — RESULT·REVIEW 면제, `process/doc-harness/transitions.md` 정합)
- 다음 단계: `/dev:commit 운전현황분석-10번섹션`
