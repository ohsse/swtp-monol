---
status: completed
created: 2026-06-08
updated: 2026-06-08
---
# 시설별 사용량 2번섹션 — 운영시설 전력 사용량 조회 API 구현

## 관련 계획
- [계획안](../../../plan/20260608/시설별사용량-2번섹션/PLAN1.md)

## 구현 메모 (코드 확인 결과 — PLAN 정련)

- **`FacilityRepository`·`InstrumentRepository` 는 api 모듈** 소재 → 빌드 검증은 `:api:test` (PLAN §성공기준 `:common:build` 표기는 common 무변경으로 불요). `FacilityType.getGroup()` 기존 제공 → `OPERATION_TYPES` 는 Service 상수에서 `FacilityType.values()` 필터로 파생(common 무변경).
- **롤업은 독립 컴포넌트 `FacilityOperatingRollupResolver` 로 추출** — 동일 패키지 `FacilityDownstreamTreeResolver`(BFS via `findByParentFacilityIdInAndUseYn` + visited-set + depth 백스톱) 선례 미러링. PLAN §구현방향 1 의 `buildOperatingRootMap` private 헬퍼 의도를, 50줄 제한 + 트리 매핑 단위 테스트 격리(성공기준 5번)를 위해 컴포넌트로 정련. "운영시설 경계 종단" 로직이 선례와 달라 동형 복제(자동 원용 아님).
- **native 집계 스타일**: `em.createNativeQuery` + `IN (:tagSrlNos)` 미사용(매핑 필요) → `unnest(:tags, :roots)` 배열 바인드. `JdbcTimestamps.toLocalDateTime(r[i])` 시각 매핑, `@SuppressWarnings("unchecked")`. 메서드 상단 `// §2.5 면책 (query-tuning.md §2 ...)` 주석 의무.
- **전력량은 기존 `findEnergyDeltaBuckets`(per-tag 버킷 차분) 재사용** — 시설합 SUM-at-minute 불요(태그별 에너지 가산적). PWI 마지막값·최대전력만 분별 시설합 native 신규.

## Phase

### Phase 1: 검색/응답 DTO + 에러 코드 (api 입출력 골격)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityEnergyUsageSearchDto.java` 생성 — `fromDt`·`toDt`(LocalDate, `@DateTimeFormat ISO.DATE`) + `inqUnit`(InqUnit, `@Schema(implementation=InqUnit.class)`) + `@Getter @Setter @NoArgsConstructor`. `toStartDtm()`·`toEndExclusiveDtm()`(PumpPeriodSearchDto 동형 복제) + `isValid()`(null·fromDt≤toDt·**inqUnit≠YEAR**·**간격 13개월(396일) 이내** 4조건) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityEnergyUsageDto.java` 생성 — `facilityId`·`facilityNm`·`elpwr`(BigDecimal)·`elceg`(BigDecimal)·`peakElpwr`(BigDecimal)·`peakElpwrDtm`(LocalDateTime, `@JsonFormat "yyyy-MM-dd HH:mm:ss"`) 전부 null 허용. `@Getter` + private 생성자 + 정적팩토리 `of(...)`. BaseAuditResponseDto 미상속(FacilityOperatingStatusDto 동형) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/facility/exception/FacilityErrorCode.java` 수정 — `INVALID_SEARCH_PERIOD(400)` enum 상수 추가(기간 검증 실패) → 검증: ./gradlew.bat :api:compileJava 후 INVALID_SEARCH_PERIOD 참조 컴파일 통과

### Phase 2: Repository 계층 (조회 메서드 + native SQL 2종)
- [x] `api/src/main/java/com/mo/swtp/raw/dto/RawDataFacilitySumDto.java` 생성 — record `(String facilityId, LocalDateTime dtm, BigDecimal value)`. 마지막값·피크 공용 프로젝션 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityRepository.java` 수정 — 파생 메서드 `findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(Collection<FacilityType>, YnType)` + `@BatchSize(100)` + Javadoc(OPERATION 8종 카드 조회, facility_type_cd 필터 강제 정합) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentRepository.java` 수정 — 파생 메서드 `findByFacilityFacilityIdInAndUseYn(Collection<String>, YnType)` + `@BatchSize(100)` + Javadoc(롤업 시설 집합의 전체 계측기 일괄 조회) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` 수정 — 시그니처 2종 추가: `findFacilityLatestMinuteSumElpwr(List<String> tags, List<String> roots, LocalDateTime start, LocalDateTime end)` · `findFacilityBucketPeakElpwr(List<String> tags, List<String> roots, LocalDateTime start, LocalDateTime end, String dateTruncUnit)` 둘 다 `List<RawDataFacilitySumDto>` 반환 + Javadoc(파티션 프루닝·SUM(MAX)≠MAX(SUM)·DISTINCT ON argmax 명시) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` 수정 — native 2종 구현. 공통 `minute_sum` CTE(`unnest(:tags, :roots)` JOIN + `quality_cd='GOOD'` + `SUM(COALESCE(corr_val, raw_val))` GROUP BY facility_id, acq_dtm). 마지막값=`DISTINCT ON(facility_id) ORDER BY facility_id, acq_dtm DESC`. 최대전력=버킷 `date_trunc(:unit)` MAX 후 `DISTINCT ON(facility_id) ORDER BY facility_id, bucket_max DESC, base_dtm ASC`(동률 최이른). `acq_dtm >= :start AND < :end` 파티션 프루닝. 각 메서드 상단 §2.5 면책 주석 + 빈 입력 가드 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 3: 롤업 리졸버 + Service
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingRollupResolver.java` 생성 — `@Component`. 운영시설 루트 목록 입력 → `findByParentFacilityIdInAndUseYn` 레벨별 BFS(visited-set + MAX_DEPTH 백스톱). 자식이 운영시설이면 그 가지는 자기 루트로 종단(최근접 운영조상 귀속). 산출 record: `(Set<String> facilityIds, Map<String,String> facilityToRoot)` → 검증: ./gradlew.bat :api:test --tests *FacilityOperatingRollupResolverTest PASS
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityEnergyUsageService.java` 생성 — `@Service @Transactional(readOnly=true)`. 퍼블릭 `findEnergyUsage(FacilityEnergyUsageSearchDto): List<FacilityEnergyUsageDto>` ≤50줄. 흐름: isValid 검증(실패 시 RestApiException INVALID_SEARCH_PERIOD) → 기간 변환 → OPERATION 카드 조회 → 롤업 리졸버 호출 → 계측기/PWI·PWQ 태그 IN절 조회 → 태그→루트 매핑 2개 → 집계 3회(순시/최대/전력량) → DTO 조립. private 헬퍼 `aggregate`·`assemble`·`validDeltaOrNull`(음수 차분 제외, PeakEnergyTrendService 동형 복제)·`effectiveVal` 분해 → 검증: ./gradlew.bat :api:test --tests *FacilityEnergyUsageServiceTest PASS

### Phase 4: Controller 엔드포인트
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 — `FacilityEnergyUsageService` 생성자 주입 + `@GetMapping("/energy-usage")` 메서드. `@ModelAttribute FacilityEnergyUsageSearchDto`, 응답 `ResponseEntity<CommonResponseDto<List<FacilityEnergyUsageDto>>>`, `getResponseEntity(data)` 래핑. `@Operation` + `@ApiResponses` 200/400/401/403/500 + `@ArraySchema(schema=@Schema(implementation=FacilityEnergyUsageDto.class))` → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 5: 테스트
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityOperatingRollupResolverTest.java` 생성 — Mockito 단위. 케이스: 단일 운영시설+하위 STORAGE/NETWORK 전부 귀속 / 운영시설 중첩 시 최근접 조상 귀속(상위 중복 합산 없음) / 순환 self-FK 방어 / depth 백스톱 → 검증: ./gradlew.bat :api:test --tests *FacilityOperatingRollupResolverTest PASS
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityEnergyUsageServiceTest.java` 생성 — Mockito 단위 8케이스: 2태그 동분 SUM 후 버킷 MAX(SUM(MAX)≠MAX(SUM) 회피) / 음수차분 제외 / 전구간 GOOD 0행 4지표 null / PWI만·PWQ만 독립 / 하위시설 롤업 합산 / OPERATION 8종만 카드 / 동률 최대전력 최이른 버킷 / 13개월 초과·YEAR 거부 INVALID_SEARCH_PERIOD → 검증: ./gradlew.bat :api:test --tests *FacilityEnergyUsageServiceTest PASS
- [x] `api/src/test/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImplIntegrationTest.java` 수정 — native 2종 통합 테스트 추가(로컬 PostgreSQL, 월 파티션 선행 생성). unnest 바인드 동작 검증 + EXPLAIN (ANALYZE, BUFFERS) 파티션 프루닝 확인 → 검증: ./gradlew.bat :api:test --tests *RawDataCustomRepositoryImplIntegrationTest PASS (로컬 DB 미가용 시 @Disabled 사유 명시)

### Phase 6: unnest 바인드 PoC + 빌드 검증
- [x] unnest 배열 바인드 1차(`String[]`+`::text[]` 캐스트) PoC — Phase 5 통합 테스트로 동작 확인. 실패 시 `EntityManager.unwrap(Session).doReturningWork(createArrayOf("text", arr))` 또는 동적 VALUES 폴백 적용(PLAN §구현방향 3) → 검증: 통합 테스트 GREEN 또는 폴백 적용 후 GREEN
- [x] 전체 빌드 — DTO·Repository·Service·Controller 변경 통합 → 검증: ./gradlew.bat :api:test BUILD SUCCESSFUL

## 산출물
- [결과](../../../results/20260608/시설별사용량-2번섹션/RESULT1.md)
