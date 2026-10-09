---
status: approved
created: 2026-06-08
updated: 2026-06-08
---
# 시설별 사용량 5번섹션 — 운영시설 전력량 트렌드 조회 API

## 목적

대시보드 "시설별 사용량"(`backend/image/시설별사용량.png`) **5번섹션**(우측 하단 시계열 트렌드 차트)을 구현한다. 1번섹션 파라미터 3종(집계단위·검색시작일·검색종료일)을 그대로 받아, **운영시설(FacilityGroup.OPERATION 8종) 각각의 전력량(kWh)을 버킷 시계열(트렌드)로 표출**하는 읽기 전용 조회 API `GET /api/facility/energy-trend` 를 추가한다.

## 배경

- 같은 화면 1번섹션(조회 조건)·2번섹션(운영시설 전력 4지표 단일집계, `GET /api/facility/energy-usage`)은 구현·승인 완료.
- 5번섹션은 2번섹션과 **동일 대상·동일 파라미터**를 받되, 2번섹션이 단일값으로 합쳐버린 PWQ 버킷 차분을 **버킷별로 보존**하여 시계열로 반환하는 점만 다르다.
- **핵심 설계**: 기존 `RawDataRepository.findEnergyDeltaBuckets(pwqTags, start, end, unit)` 가 이미 per-tag 버킷 차분 전체(`List<RawDataBucketDto>{tagSrlNo, baseDtm, aggrVal}`)를 반환한다. 2번섹션 `aggregateEnergy()` 는 이를 `Map<root, BigDecimal>` 로 버킷을 뭉쳐 단일 elceg 를 만들지만, 5번섹션은 `Map<root, TreeMap<baseDtm, BigDecimal>>` 로 버킷을 **보존**해 합산하면 시계열이 그대로 나온다 → **신규 native query 0건**.
- ANALYZE1([approved](../../../analyze/20260608/시설별사용량-5번섹션/ANALYZE1.md)) 5인 회의 결론: 신규 어휘 0건·룰 갱신 지시서 없음·DB 스키마 변경 0건. 검색 DTO 는 신규 동형복제, `validDeltaOrNull`·`OPERATION_TYPES` 는 동형복제 유지, 조회기간 상한 13개월 동일 유지(사용자 결정).

## 범위

| 구분 | 대상 |
|------|------|
| 신규 파일 (3) | `FacilityEnergyTrendSearchDto`(facility.dto) · `FacilityEnergyTrendDto`+중첩 `EnergyTrendPoint`(facility.dto) · `FacilityEnergyTrendService`(facility.service) |
| 수정 파일 (1) | `FacilityController` — `GET /api/facility/energy-trend` 엔드포인트 + 서비스 주입 |
| 신규 테스트 (1) | `FacilityEnergyTrendServiceTest`(Mockito 단위 테스트) |
| 재사용 (수정 0) | `FacilityOperatingRollupResolver` · `RawDataRepository.findEnergyDeltaBuckets` · `FacilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc` · `InstrumentRepository.findByFacilityFacilityIdInAndUseYn` · `TagRepository.findByInstrumentInstrumentIdInAndUseYn` · `FacilityErrorCode.INVALID_SEARCH_PERIOD` · `FacilityType`/`FacilityGroup` · `TagMeasurementType.PWQ` · `InqUnit` |
| 영향 모듈 | `api` 단일 (common·scheduler 영향 0 — 엔티티·공통타입 변경 없음) |

## 구현 방향

### 1. `FacilityEnergyTrendSearchDto` (facility.dto, 신규)

`FacilityEnergyUsageSearchDto` **동형복제**(상속·재사용 금지 — 안건 1 블로커 해소). 필드·메서드 동일, `@Schema` description 만 5번섹션 맥락으로 작성.

- 필드: `inqUnit`(InqUnit) · `fromDt`(LocalDate) · `toDt`(LocalDate)
- `@Getter @Setter @NoArgsConstructor` + `@ModelAttribute` GET 바인딩 (`FacilityEnergyUsageSearchDto` 선례 동형)
- `private static final long MAX_PERIOD_DAYS = 396L`
- `isValid()` — non-null 4조건 + fromDt≤toDt + YEAR 거부 + 396일 이내
- `toStartDtm()` = `fromDt.atStartOfDay()`, `toEndExclusiveDtm()` = `toDt.plusDays(1).atStartOfDay()`
- Javadoc 에 "`FacilityEnergyUsageSearchDto` 동형복제 — @Schema SPEC 오염 방지, 검색 DTO 공통화 2건 미만(시설별사용량-5번섹션 ANALYZE1 안건 1)" 명기

### 2. `FacilityEnergyTrendDto` (facility.dto, 신규)

`PeakEnergyTrendDto` outer + inner Point + 정적팩토리 패턴 미러링. `BaseAuditResponseDto` 미상속(집계 뷰). `@Getter` + private 생성자 + 정적팩토리 `of(...)`.

- outer 필드: `facilityId`(String) · `facilityNm`(String) · `List<EnergyTrendPoint> points`
  - `points` 에 `@ArraySchema(schema = @Schema(implementation = EnergyTrendPoint.class))` 필수(`api-patterns.md §DTO @Schema(implementation)` — List element 사용자 정의 클래스)
  - `of(facilityId, facilityNm, points)` 정적팩토리
- inner `static class EnergyTrendPoint`:
  - `baseDtm`(LocalDateTime, `@JsonFormat(shape = STRING, pattern = "yyyy-MM-dd HH:mm:ss")`)
  - `elcegVal`(BigDecimal) — `PeakEnergyTrendPoint.elcegVal` 선례 동일 필드명
  - `of(baseDtm, elcegVal)` 정적팩토리

### 3. `FacilityEnergyTrendService` (facility.service, 신규)

`@Service @RequiredArgsConstructor @Transactional(readOnly = true) @Slf4j`. 2번섹션 `FacilityEnergyUsageService` 흐름 미러링하되 **PWQ 단일 갈래 + 버킷 보존 시계열**. 퍼블릭 메서드 50줄 이내 위해 private 헬퍼 분해.

주입: `FacilityRepository` · `FacilityOperatingRollupResolver` · `InstrumentRepository` · `TagRepository` · `RawDataRepository`

`static`: `OPERATION_TYPES`(FacilityType→OPERATION 파생, 동형복제 + Javadoc 카운트)

퍼블릭 `findEnergyTrend(FacilityEnergyTrendSearchDto)`:
```
1. isValid 검증 실패 → throw RestApiException(INVALID_SEARCH_PERIOD)
2. start/end/unit 추출
3. operatingRoots = findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(OPERATION_TYPES, Y); empty → return List.of()
4. facilityToRoot = rollupResolver.resolveRootByFacility(operatingRoots)
5. instruments = findByFacilityFacilityIdInAndUseYn(facilityToRoot.keySet(), Y)
6. instrumentToRoot = buildInstrumentToRoot(instruments, facilityToRoot)
7. pwqTagToRoot = collectPwqTagToRoot(instruments, instrumentToRoot)   // PWQ만
8. trendByRoot = aggregateTrendByRoot(pwqTagToRoot, start, end, unit)  // Map<root, TreeMap<baseDtm, BigDecimal>>
9. return assembleSeriesList(operatingRoots, trendByRoot)
```

private 헬퍼:
- `buildInstrumentToRoot(instruments, facilityToRoot)` — 2번섹션 동형(`instrument.getFacility().getFacilityId()` 프록시 식별자 접근, 추가 쿼리 0)
- `collectPwqTagToRoot(instruments, instrumentToRoot)` — 태그 IN 1회 로드 후 `tagSeCd == PWQ` 만 필터, `Map<tagSrlNo, root>` 반환(2번섹션 loadActiveTags+splitTagsByType 를 PWQ 단일 갈래로 축약)
- `aggregateTrendByRoot(pwqTagToRoot, start, end, unit)` — `findEnergyDeltaBuckets(pwqTagToRoot.keySet, ...)` 결과를 `validDeltaOrNull` 통과분만 `trendByRoot.computeIfAbsent(root, new TreeMap).merge(baseDtm, delta, add)` 로 버킷별 합산
- `assembleSeriesList(operatingRoots, trendByRoot)` — operatingRoots 순서대로 TreeMap entrySet → `EnergyTrendPoint.of` 매핑(TreeMap 으로 baseDtm ASC 보장), 부재 root → `points = List.of()`
- `validDeltaOrNull(aggrVal, tagSrlNo, baseDtm)` — null/음수 시 null + 음수 WARN 로그. Javadoc 에 "3번째 동형복제(`PeakEnergyTrendService`·`FacilityEnergyUsageService` 이어) — 공통 추출은 별도 ANALYZE(시설별사용량-5번섹션 ANALYZE1 안건 2)" 명기

### 4. `FacilityController` 수정

`energy-usage`(L434) 바로 다음 `GET /api/facility/energy-trend` 추가. `FacilityEnergyTrendService` 주입 필드 + import 추가.

```java
@Operation(summary = "운영시설 전력량 트렌드 조회 (시설별 사용량 5번 섹션)",
        description = "...운영시설별 전력량(elceg, kWh) 버킷 시계열... 부재 버킷 생략(0 보간 금지)...")
@ApiResponses({200 배열, 400 INVALID_SEARCH_PERIOD, 401, 403, 500})
@GetMapping("/energy-trend")
public ResponseEntity<CommonResponseDto<List<FacilityEnergyTrendDto>>> findFacilityEnergyTrend(
        @ModelAttribute FacilityEnergyTrendSearchDto searchDto) {
    return getResponseEntity(facilityEnergyTrendService.findEnergyTrend(searchDto));
}
```

- 200 응답: `@Content(array = @ArraySchema(schema = @Schema(implementation = FacilityEnergyTrendDto.class)))`
- description 에 도메인-expert 권고("응답 부재 버킷 = 데이터 없음, frontend 0 보간 금지") 명시 → `/dev:spec` 단계 SPEC 자동 추출 시 전파

### 5. 테스트 `FacilityEnergyTrendServiceTest` (Mockito 단위)

`@ExtendWith(MockitoExtension.class)` + `@Mock` 5종 + `@InjectMocks`. `FacilityEnergyUsageServiceTest` 패턴 미러링(엔티티는 정적 팩토리/빌더 또는 mock). 성공 기준 표와 1:1 대응.

## 성공 기준 (검증 가능 형태)

| 성공 기준 | 검증 방법 |
|---------|---------|
| 운영시설별 PWQ 버킷 차분이 운영루트로 버킷별 합산되어 시계열(points) 반환 | 단위 테스트 — 동일 root 2태그 같은 baseDtm delta 합이 해당 point.elcegVal 로 산출 → GREEN |
| points baseDtm 오름차순 정렬 | 단위 테스트 — 역순 입력 버킷이 반환 points 에서 baseDtm ASC → GREEN |
| 음수 차분(적산 카운터 리셋) 버킷 제외 | 단위 테스트 — 음수 delta 버킷이 points 에서 제외 + WARN 로그 → GREEN |
| 데이터 0 시설 → points 빈 리스트 | 단위 테스트 — GOOD 버킷 0건 운영시설의 points = 빈 List → GREEN |
| 재귀 하위 시설 태그가 최근접 운영조상 시리즈에 귀속 | 단위 테스트 — rollupResolver 매핑대로 하위 태그 delta 가 조상 root point 에 합산 → GREEN |
| 시리즈 대상 = OPERATION 8종만(STORAGE/NETWORK 미출현) | 단위 테스트 — findByFacilityTypeIn 인자가 OPERATION_TYPES, 결과 facility 전부 OPERATION → GREEN |
| operatingRoots 부재 → 빈 결과 + 후속 조회 미호출 | 단위 테스트 — operatingRoots empty 시 List.of() 반환 + rollup/instrument/raw 미호출 verify → GREEN |
| isValid 실패(YEAR·기간 역전·13개월 초과·null) → INVALID_SEARCH_PERIOD | 단위 테스트 — 각 케이스 RestApiException(INVALID_SEARCH_PERIOD) → GREEN |
| common·api 빌드 무결성 | ./gradlew.bat :common:build 성공 후 ./gradlew.bat :api:build 성공(BUILD SUCCESSFUL) |
| 수동 검증(로컬 PostgreSQL) | GET /api/facility/energy-trend?inqUnit=HOUR&fromDt=2024-07-09&toDt=2024-07-09 호출 → 운영시설별 시리즈 응답·points baseDtm ASC 확인 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 트렌드 값 = 전력량(elceg, kWh)만, PWI 미사용 | 결정 | PWQ 태그만 수집, PWI 집계 로직 미도입 |
| 응답 구조 = 시설별 시리즈 묶음 | 결정 | `List<FacilityEnergyTrendDto>` (outer=시설, points=시계열) |
| 빈 버킷 = 있는 버킷만 반환(생략) | 결정 | 연속 시간축 미생성, TreeMap 존재 버킷만 point. SPEC 에 "0 보간 금지" 명시 |
| 조회기간 상한 13개월(396일), HOUR 동일 | 결정 | `FacilityEnergyTrendSearchDto.MAX_PERIOD_DAYS = 396` 동형. 별도 HOUR guard 미도입 |
| HOUR+장기간 응답 크기(시설당 ~9504버킷 × 8종) | 미해결(모니터링) | 본 사이클 guard 미도입. frontend 사용 패턴 관찰 후 별도 사이클 재검토 |
| 검색 DTO = 신규 동형복제(재사용 금지) | 결정 | `FacilityEnergyTrendSearchDto` 신규 작성 |
| 롤업 귀속 = 최근접 운영조상(중복 합산 방지) | 가정(상속) | `FacilityOperatingRollupResolver` 동작 그대로 상속 |
| 음수 차분 = 전량 제외 + WARN 로그 | 결정 | `validDeltaOrNull` 동형복제(3번째) |
| `validDeltaOrNull`·`OPERATION_TYPES` 공통 추출 | 미해결 | 본 사이클 동형복제 유지. 공통 추출은 별도 ANALYZE |

## 제외 사항

- PWI(순시전력)·최대전력·최대전력 일시 등 2번섹션 4지표 — 5번섹션은 전력량 트렌드만(전력량 단일 갈래)
- 신규 native query / Repository 메서드 — 기존 `findEnergyDeltaBuckets` 재사용으로 0건
- 신규 엔티티·DB 테이블·컬럼·인덱스·ErrorCode — 0건(`docs/ddl/`·마이그레이션 SQL 갱신 불필요)
- `validDeltaOrNull`·`OPERATION_TYPES` 공통 Helper/Util 추출 — over-engineering 회피(별도 ANALYZE)
- 연속 시간축 빈 버킷 0 채움 — frontend 책임
- 통합 테스트(`@SpringBootTest`)·Repository 슬라이스 — 신규 쿼리 0이므로 단위 테스트로 충분(`test-strategy.md` 현행 표준)

## 예상 산출물

- [태스크](../../../tasks/20260608/시설별사용량-5번섹션/TASK1.md)
