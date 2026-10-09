---
status: completed
created: 2026-06-09
updated: 2026-06-10
---
# 설비별사용량 7번섹션 — 설비별 순시전력 트렌드 조회 API 작업 분해

## 관련 계획
- [계획안](../../../plan/20260609/설비별사용량-7번섹션/PLAN1.md)

## Phase

> 계층 의존 순서: Repository(record→interface→impl) → DTO → Service → Controller → Test → 빌드·검증. 각 체크박스는 `coding-discipline.md §4.1` 검증 기준 동반(검증 영역 백틱 미사용).

### Phase 1: Repository 계층 (raw 도메인 — native 집계)

- [x] `api/src/main/java/com/mo/swtp/raw/dto/RawDataInstrumentSumDto.java` 생성 — record(instrumentId, dtm, value), `RawDataFacilitySumDto` 구조 미러링·Service 내부 전송 전용·Swagger 미노출 Javadoc → 검증: ./gradlew.bat :api:compileJava 부분 성공 (해당 파일 컴파일 에러 없음)
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` 수정 — findInstrumentMinuteSumElpwr(tags, instruments, startDtm, endDtm) 메서드 시그니처 추가 + Javadoc(unnest 설비 매핑·GOOD COALESCE 동시각 합산·전체 분 시계열·파티션 프루닝) → 검증: 인터페이스 메서드 선언 존재 확인
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` 수정 — findInstrumentMinuteSumElpwr native 구현(WITH tag_instrument unnest + SUM COALESCE GOOD GROUP BY instrument_id,acq_dtm ORDER BY, DISTINCT ON 없음) + Session.doReturningWork setArray text 바인드 + drainInstrumentSum 헬퍼 + §2.5 면책 인용 주석(query-tuning.md §2) + 빈 tags 가드 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 2: DTO 계층 (facility 도메인)

- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityInstrumentPowerTrendSearchDto.java` 생성 — fromDt/toDt(LocalDate, @DateTimeFormat ISO.DATE), MAX_PERIOD_DAYS=31, isValid()/toStartDtm()/toEndExclusiveDtm(), @Getter @Setter @NoArgsConstructor, 동형 복제(비상속) Javadoc → 검증: 31일 상한·isValid 3조건 메서드 존재 확인
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityInstrumentPowerTrendDto.java` 생성 — outer(unit, series @ArraySchema) + 중첩 InstrumentPowerSeries(instrumentId·instrumentNm·equipTypeCd @Schema(implementation=EquipType)·facilityId·facilityNm·points @ArraySchema) + 중첩 PowerTrendPoint(acqDtm @JsonFormat·elpwrVal) + 각 정적 팩토리 of() + BaseAuditResponseDto 미상속 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 3: Service 계층

- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityInstrumentPowerTrendService.java` 생성 — @Service @RequiredArgsConstructor @Transactional(readOnly=true), 상수 POWER_TAG_TYPE=PWI/UNIT_KW/MAX_DEPTH=10, findPowerTrend public + private 헬퍼(검증·findActiveFacilityOrThrow·collectSubtree·PWI 평행배열 수집·쿼리·설비별 시리즈 조립), 헬퍼 동형 복제·다중 PWI 동시각 합산·PWI GOOD/COALESCE 인용 Javadoc → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 4: Controller 계층

- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityInstrumentPowerTrendController.java` 생성 — @Tag "15. 설비별 사용량", @RequestMapping /api/facility, CommonController 상속, @GetMapping /{facilityId}/instrument-power-trend, @PathVariable+@ModelAttribute, @Operation+@ApiResponses(200/400/401/403/404/500) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 5: 테스트

- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityInstrumentPowerTrendServiceTest.java` 생성 — @ExtendWith(MockitoExtension), 기간무효 3종→INVALID_SEARCH_PERIOD / 미존재·비활성→FACILITY_NOT_FOUND / 재귀 BFS 다단계→설비별 시계열 정렬 / 다중 PWI 평행배열·단일 series / 데이터0 설비 빈 points 포함 / 계측기·PWI 0건 빈 series 200 / 정렬(시설 dispOrd→계측기 dispOrd→instrumentNm) → 검증: ./gradlew.bat :api:test 신규 테스트 PASS

### Phase 6: 빌드·검증

- [x] `./gradlew.bat :api:test` 실행 → 검증: 신규 테스트 포함 전체 GREEN
- [x] `./gradlew.bat clean build` 실행 → 검증: BUILD SUCCESSFUL (QClass 재생성 포함 전 모듈)
- [x] 신규 native 쿼리 EXPLAIN 검증 → 검증: dev/스테이징 EXPLAIN (ANALYZE, BUFFERS) — findInstrumentMinuteSumElpwr Index Scan(idx_rawdata_1m_h_tag_time) + 월 RANGE 파티션 프루닝 최대 2파티션 + 500ms 이내 확인

## 산출물
- [결과](../../../results/20260609/설비별사용량-7번섹션/RESULT1.md)
