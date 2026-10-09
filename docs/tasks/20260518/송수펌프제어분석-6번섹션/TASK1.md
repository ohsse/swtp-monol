---
status: completed
created: 2026-05-18
updated: 2026-05-18
---
# 송수펌프제어분석 6번섹션 (시설별 토출관압 + 운전중 펌프 대수 요약)

## 관련 계획
- [계획안](../../../plan/20260518/송수펌프제어분석-6번섹션/PLAN1.md)

## Phase

> ROOT `coding-discipline.md §4.1` 적용. 체크박스 형식: 파일경로 백틱 + 작업 → 검증: 확인 명령. 검증 영역 백틱 금지 (훅 파싱 충돌).

### Phase 1: 응답 DTO 신설 (PumpSummaryDto)

- [x] `api/src/main/java/com/mo/swtp/facility/dto/PumpSummaryDto.java` 생성 — 평탄 9필드(facilityId·facilityNm·prsrRawVal·prsrCorrVal·prsrAcqDtm·prsrQualityCd·multiplePrsrDetected·oprtngPumpCnt·unknownPumpCnt), @Getter + private 기본 생성자 + 정적 팩토리 of(...) → 검증: 클래스 컴파일 + 필드 9개 존재 grep 매칭
- [x] `api/src/main/java/com/mo/swtp/facility/dto/PumpSummaryDto.java` 어노테이션 — prsrQualityCd 에 @Schema(implementation=QualityCode.class), prsrAcqDtm 에 @JsonFormat(shape=STRING, pattern yyyy-MM-dd HH:mm:ss), 클래스 @Schema(description) 작성. BaseAuditResponseDto 미상속 → 검증: api-patterns.md §DTO @Schema 규약 대조 + grep 으로 implementation/JsonFormat 존재 확인
- [x] `api/src/main/java/com/mo/swtp/facility/dto/PumpSummaryDto.java` Javadoc — 클래스·정적 팩토리 한국어 Javadoc 작성 (FlwmtrStateDto 선례 정합) → 검증: Javadoc 블록 존재 확인

### Phase 2: 집계 서비스 신설 (PumpSummaryService)

- [x] `api/src/main/java/com/mo/swtp/facility/service/PumpSummaryService.java` 생성 — @Service @RequiredArgsConstructor @Transactional(readOnly=true) @Slf4j, SUMMARY_EQUIP_TYPES=List.of(PUMP,FLWMTR), SUMMARY_TAG_TYPES=EnumSet.of(PRI,OPS), 의존성 4종(FacilityRepository·InstrumentRepository·TagRepository·RawDataRepository) 주입 → 검증: 클래스 컴파일 + 상수·의존성 grep 매칭
- [x] `api/src/main/java/com/mo/swtp/facility/service/PumpSummaryService.java` findPumpSummaries() — Step 1 findFacilities(hasPump=true) 추가 useYn 필터 없이 호출, 빈 목록 시 List.of() 반환, Step 2~4 IN 절 단일 조회, Step 5 시설 순서 보존 메모리 집계 → 검증: PLAN §구현 방향 Step 1~5 와 1:1 대조, 시설 정렬 보존 로직 확인
- [x] `api/src/main/java/com/mo/swtp/facility/service/PumpSummaryService.java` mapPumpSummary 외 private 헬퍼 — 토출관압 첫 매치 PRI FLWMTR 선정 + multiplePrsrDetected 플래그/log.warn, OPS 분류(classifyOps: GOOD&1.0→oprtng / null·BAD·UNCERTAIN·판정불가→unknown / GOOD&0.0→미증가), rawVal/corrVal/acqDtm/qualityCd null-safe 헬퍼 → 검증: PLAN §Step 5 매핑 규칙과 분기 1:1 대조
- [x] `api/src/main/java/com/mo/swtp/facility/service/PumpSummaryService.java` 정량 기준 — 각 메서드 50줄 이내 유지, 불가피 초과 집계 메서드에 §2.5 면책 (db/query-tuning.md §2) 주석 인용, §3 FacilityStateService 동명 헬퍼 추출·공유 안 함(독립 보유) → 검증: 메서드별 본문 줄 수 확인, 50줄 초과 시 면책 주석 grep 존재 확인

### Phase 3: 컨트롤러 엔드포인트 추가

- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 — pumpSummaryService 필드 추가, GET /pump-summary 메서드 추가, ResponseEntity<CommonResponseDto<List<PumpSummaryDto>>> 반환, getResponseEntity(...) 사용 → 검증: 메서드·필드 추가 후 컴파일, 기존 7개 엔드포인트 시그니처 무변경 확인
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` Swagger — @Operation(summary/description 6번섹션 명시), @ApiResponses 200/400/401/403/500(404 없음), 200 응답 @Content(array=@ArraySchema(schema=@Schema(implementation=PumpSummaryDto.class))) 명시 → 검증: api-patterns.md §DTO @Schema(implementation) 규약 대조 + ArraySchema grep 확인

### Phase 4: 단위 테스트 신설

- [x] `api/src/test/java/com/mo/swtp/facility/service/PumpSummaryServiceTest.java` 생성 — @ExtendWith(MockitoExtension.class), @Mock 4종 Repository + @InjectMocks PumpSummaryService, 시설 N건 fixture 정상 응답 케이스 → 검증: ./gradlew.bat :api:test --tests com.mo.swtp.facility.service.PumpSummaryServiceTest GREEN
- [x] `api/src/test/java/com/mo/swtp/facility/service/PumpSummaryServiceTest.java` OPS 분류 케이스 — GOOD&1.0(oprtng)·GOOD&0.0(미증가)·BAD·UNCERTAIN·null(unknown)·GOOD&판정불가(unknown) 각 카운트 정확성 검증 → 검증: 해당 테스트 메서드 GREEN, oprtngPumpCnt/unknownPumpCnt assertion 통과
- [x] `api/src/test/java/com/mo/swtp/facility/service/PumpSummaryServiceTest.java` 토출관압 다중 검출 케이스 — PRI 보유 FLWMTR 2건 fixture 시 첫 매치 값 + multiplePrsrDetected=true 검증 → 검증: 해당 테스트 메서드 GREEN
- [x] `api/src/test/java/com/mo/swtp/facility/service/PumpSummaryServiceTest.java` 경계 케이스 — 빈 시설 목록(List.of()), 시설 정렬 순서 보존, Repository mock 호출 각 1회 verify(...) → 검증: 해당 테스트 메서드 GREEN + verify 호출 횟수 4회 단언 통과

### Phase 5: 빌드·회귀 검증

- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 경로 매핑 우선순위 — GET /pump-summary 정적 세그먼트가 GET /{facilityId} path variable 보다 우선 매칭 확인 → 검증: PumpSummaryServiceTest 또는 컨트롤러 슬라이스 검증 없이 매핑 규칙 수동 확인 (Spring MVC 정적 경로 우선 원칙)
- [x] `common/src/main/resources/db/migration` 신규 SQL 파일 부재 확인 — DB 스키마 변경 0건 (최신 V9_2 유지) → 검증: 디렉토리 신규 V9_3+ 파일 부재 확인
- [ ] 전체 회귀 — 3·4·5번섹션 + facility CRUD 무영향 → 검증: ./gradlew.bat :common:build 후 ./gradlew.bat :api:test 각 BUILD SUCCESSFUL

## 산출물
- [결과](../../../results/20260518/송수펌프제어분석-6번섹션/RESULT1.md)
