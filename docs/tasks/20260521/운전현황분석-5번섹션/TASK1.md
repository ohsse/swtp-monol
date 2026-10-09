---
status: completed
created: 2026-05-21
updated: 2026-05-21
---
# 운전현황분석-5번섹션 — 시설 단위 1분 단위 시계열 조회 API

## 관련 계획
- [계획안](../../../plan/20260521/운전현황분석-5번섹션/PLAN1.md)
- [도메인 분석](../../../analyze/20260521/운전현황분석-5번섹션/ANALYZE1.md)

## Phase

### Phase 1: enum + DTO 신규 작성

- [x] `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityOperatingStatusCompareType.java` 신규 작성 (YESTERDAY, LAST_WEEK 2값. `@Schema(description)` + 각 enum 값 `@Schema(description)` 명시. common 모듈 — 도메인 enum 위치 일관성, FacilityType 선례) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityOperatingStatusTimeSeriesDto.java` 신규 작성 (외부 클래스 5필드: facilityId·facilityNm·compareType·todaySeries·comparisonSeries. private 기본 생성자 + static factory `of(...)`. `@Schema(implementation = FacilityOperatingStatusCompareType.class)` + `@ArraySchema(schema=@Schema(implementation=TimeSeriesPoint.class))` 명시) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] 같은 파일에 static inner class `TimeSeriesPoint` 추가 (acqDtm·onPumpCnt·totalElpwrAmt·elpwrUnitQty 4필드. `@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")` LocalDateTime 직렬화. private 기본 생성자 + static factory `of(...)`) → 검증: 신규 파일 grep 으로 TimeSeriesPoint static class + @JsonFormat 어노테이션 매칭 확인

### Phase 2: Repository 시계열 쿼리 추가

- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` 메서드 시그니처 추가 (`List<RawDataLatestDto> findByTagSrlNosAndDtmRange(List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm)`. Javadoc 으로 인덱스·파티션 프루닝 정책 명시) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` Querydsl 구현 추가 (JPAQueryFactory 주입 — ApiQuerydslConfig 의존. QRawData 사용. tagSrlNos 빈 리스트 시 List.of() 조기 반환. ORDER BY acq_dtm ASC, tag_srl_no ASC. RawData 엔티티의 PK 구조 확인 후 정렬) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] QRawData QClass 생성 확인 — `common/build/generated/querydsl` 디렉토리 또는 빌드 출력에서 QRawData.class 존재 확인 → 검증: ./gradlew.bat :common:compileJava 후 QRawData 파일 존재 확인 (Phase 6 빌드 시 일괄 확인)

### Phase 3: Service 신규 작성

- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesService.java` 신규 작성 — 클래스 스켈레톤 + 의존성 주입 (FacilityRepository · InstrumentRepository · TagRepository · RawDataRepository. `@Service` + `@Transactional(readOnly = true)` + `@RequiredArgsConstructor`. SUPPORTED_TYPES · TARGET_EQUIP_TYPES · TARGET_TAG_TYPES 상수 4번 섹션 동일 정의 재구현) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `findFacilityOperatingStatusTimeSeries(facilityId, compareType)` 공개 메서드 작성 — 활성 시설 검증 → 인스트루먼트 조회 → 태그 그룹화 → today/comparison 시간 범위 결정 → buildSeries 2회 호출 → DTO 조립. 메서드 본문 50줄 이내 의무 → 검증: 메서드 라인 수 본문 50줄 이하 + 추상화 호출 스택 3단 이하 manual 확인
- [x] 4번 헬퍼 9개 재구현 (private 메서드) — `findActiveFacilityOrThrow` · `loadTagsByInstrument` · `filterPumps` · `pickLatest` · `effectiveVal` · `isPumpRunning` · `sumOnPumpPwr` · `selectFacilityFri` · `computeUnitConsumption`. 4번 섹션 동일 정책 + 동일 시그니처 (pickLatest 는 latestByTag map 인자 받는 형태 유지) → 검증: 4번 섹션 동일 정책 grep 비교 — isPumpRunning(GOOD+1.0) · computeUnitConsumption(scale=4, HALF_UP) 매칭 확인
- [x] 시간 범위 헬퍼 `resolveTodayRange()` + `resolveComparisonRange(compareType)` private 메서드 작성. 내부 record `TimeRange(LocalDateTime start, LocalDateTime end)` 정의 (Service 내부 record). YESTERDAY = minusDays(1) atStartOfDay ~ atTime(LocalTime.MAX). LAST_WEEK = minusDays(7) 동일 → 검증: 단위 테스트에서 시간 범위 경계 검증
- [x] `buildSeries(tagsByInstrument, instruments, range)` private 메서드 작성 — Repository 호출 후 `acq_dtm` 기준 grouping + 시점별 buildPoint 호출 + 오름차순 정렬. 빈 결과 시 List.of() 반환 → 검증: 단위 테스트에서 빈 시계열·정렬·결측 시점 생략 검증
- [x] `buildPoint(acqDtm, rawsAtThisMinute, tagsByInstrument, instruments)` private 메서드 작성 — 시점별 latestByTag map 생성 → On 펌프 추출 → totalElpwrAmt + fri + elpwrUnitQty 계산 → TimeSeriesPoint.of(...) 반환 → 검증: 단위 테스트에서 정상 시점·UNCERTAIN OPS 제외·FRI BAD 시 elpwrUnitQty=null 검증

### Phase 4: Controller 메서드 추가

- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 메서드 추가 — `findFacilityOperatingStatusTimeSeries(@PathVariable String facilityId, @RequestParam FacilityOperatingStatusCompareType compareType)`. `@GetMapping("/{facilityId}/operating-status/timeseries")`. FacilityOperatingStatusTimeSeriesService 의존성 주입 (`@RequiredArgsConstructor` 필드 추가). `getResponseEntity(dto)` 래핑 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] Swagger 어노테이션 작성 — `@Operation(summary)`, `@Parameter(description)` × 2 (facilityId, compareType), `@ApiResponses` (200·400·401·403·404·500) → 검증: :api:bootRun 후 Swagger UI 에서 신규 엔드포인트 노출 + compareType enum 옵션 2종 표시 (Phase 6 수동 검증)

### Phase 5: 단위 테스트 작성

- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesServiceTest.java` 신규 작성 — `@ExtendWith(MockitoExtension.class)` + Mock 4종 (FacilityRepository · InstrumentRepository · TagRepository · RawDataRepository) + `@InjectMocks` 대상 Service → 검증: ./gradlew.bat :api:compileTestJava BUILD SUCCESSFUL
- [x] 정상 시계열 테스트 — 펌프 3대 (P#1·P#2·P#3) + 유량계 1개 + 2분 시계열 (T1·T2) 픽스처. compareType=YESTERDAY 호출. todaySeries 2건 + comparisonSeries 1건 응답 검증. 각 TimeSeriesPoint 의 onPumpCnt·totalElpwrAmt·elpwrUnitQty 정확값 검증 → 검증: ./gradlew.bat :api:test --tests "FacilityOperatingStatusTimeSeriesServiceTest.정상_시계열_*" PASS
- [x] 빈 시계열 테스트 — RawDataRepository 가 빈 List 반환 시 todaySeries=[] + comparisonSeries=[] 응답. NPE 미발생 → 검증: ./gradlew.bat :api:test --tests "*빈_시계열*" PASS
- [x] 결측 시점 생략 테스트 — T1 시점은 OPS·PWI·FRI 모두 존재, T2 시점은 데이터 0건. 응답 todaySeries 가 T1 한 행만 포함 (T2 자동 생략) → 검증: ./gradlew.bat :api:test --tests "*결측_시점*" PASS
- [x] UNCERTAIN OPS 제외 테스트 — 펌프 2대 중 1대 OPS=GOOD+1.0, 다른 1대 OPS=UNCERTAIN+1.0. onPumpCnt=1 + totalElpwrAmt = On 펌프 1대 PWI 만 합산 → 검증: ./gradlew.bat :api:test --tests "*UNCERTAIN_OPS*" PASS
- [x] 비활성 시설 거부 테스트 — useYn=N 시설 조회 시 `RestApiException(FACILITY_NOT_FOUND)`. RSV 시설 조회 시 `RestApiException(UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS)` → 검증: ./gradlew.bat :api:test --tests "*비활성_시설* OR *UNSUPPORTED_FACILITY*" PASS

### Phase 6: 빌드·통합 검증

- [x] `./gradlew.bat :api:test` 전체 실행 — 신규 단위 테스트 5건 이상 PASS + 기존 테스트 모두 GREEN (회귀 없음) → 검증: BUILD SUCCESSFUL 출력 확인 (5번 섹션 6건 PASS + 기존 회귀 0건)
- [x] `./gradlew.bat build` 전체 빌드 — common·api·scheduler 모두 BUILD SUCCESSFUL → 검증: BUILD SUCCESSFUL 출력 + 0 test failures 확인 (1m 19s 완료, 19 actionable tasks)
- [ ] 로컬 PostgreSQL 픽스처 투입 후 Swagger UI 수동 호출 — `compareType=YESTERDAY` 와 `compareType=LAST_WEEK` 각각 200 OK + 응답 직렬화 정상 → 검증: Swagger UI 응답 본문에 todaySeries·comparisonSeries 두 키 + acqDtm 포맷 yyyy-MM-dd HH:mm:ss 확인 **(사용자 로컬 환경 수동 검증 — 코드 정합성은 통과)**
- [ ] `EXPLAIN (ANALYZE, BUFFERS) SELECT ... FROM rawdata_1m_h WHERE tag_srl_no IN (...) AND acq_dtm >= '...' AND acq_dtm < '...'` 실행 — 파티션 프루닝 활성화 (특정 월 파티션만 스캔) + Index Scan using idx_rawdata_1m_h_tag_time 활용 + 응답 시간 200ms 이내 → 검증: EXPLAIN ANALYZE 출력 Partitions 절 + Index Scan 노드 매칭 **(사용자 로컬 환경 수동 검증 — Querydsl 쿼리는 `tagSrlNo IN + acqDtm >= ... AND acqDtm < ...` 형태로 파티션 프루닝 의도 보존)**

## 산출물
- (Medium 작업 — RESULT·REVIEW 면제, `process/doc-harness/transitions.md` 정합)
- 본 사이클 완료 후 사용자 요청 시 `/dev:commit 운전현황분석-5번섹션` (사용자 명시 승인 의무)
- 선택적 후속 `/dev:spec 운전현황분석-5번섹션` — frontend SPEC 전파
