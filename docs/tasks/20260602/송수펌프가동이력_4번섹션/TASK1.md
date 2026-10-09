---
status: completed
created: 2026-06-02
updated: 2026-06-02
---
# 송수펌프 가동이력 4번섹션 — 펌프 가동상태 타임라인 차트 API 작업 분해

## 관련 계획
- [계획안](../../../plan/20260602/송수펌프가동이력_4번섹션/PLAN1.md)

## Phase

> 계층 의존 순서대로 분해 (검색 DTO → 조회 → 응답 DTO → Service → Controller → 테스트 → 검증).
> 체크박스 형식: `- [ ] {파일경로} 작업 → 검증: {확인 명령}`. 검증 영역 백틱 미사용 (훅 파싱 호환).

### Phase 1: 검색 DTO 정렬 (공통 부모 신규 + 3번섹션 자식 상속)
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpPeriodSearchDto.java` 신규 — from~to 공통 부모 (`fromDt`·`toDt` + `isValid()`·`toStartDtm()`·`toEndExclusiveDtm()` SSOT, `@Getter @Setter @NoArgsConstructor` + `@DateTimeFormat(ISO.DATE)` + `@Schema`) → 검증: 컴파일 통과 (Phase 7 빌드)
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpTimeSeriesSearchDto.java` 변경 — `PumpPeriodSearchDto` 상속, 중복 `fromDt`·`toDt` 필드 + `toStartDtm()`·`toEndExclusiveDtm()` 메서드 제거, `inqUnit` + `@Override isValid()`(inqUnit != null && super.isValid()) 만 유지 → 검증: 3번섹션 호출부 시그니처 유지 확인 (getFromDt·getToDt·toStartDtm·toEndExclusiveDtm·getInqUnit·isValid 상속 노출)

### Phase 2: ON 상태 시계열 조회 (raw 도메인 — 방안B)
- [x] `api/src/main/java/com/mo/swtp/raw/dto/RawDataOnStateDto.java` 신규 — 경량 전송 record (`String tagSrlNo`, `LocalDateTime acqDtm`), `RawDataBucketDto` 선례 동형 (Service 내부 전송, Swagger 미노출) → 검증: 컴파일 통과 (Phase 7 빌드)
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` 변경 — `List<RawDataOnStateDto> findOnStateByTagSrlNosAndDtmRange(List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm)` 메서드 시그니처 추가 → 검증: 컴파일 통과 (Phase 7 빌드)
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` 변경 — Querydsl 구현 추가 (`§2.5` 면책 주석 — query-tuning.md §2 인용. `Projections.constructor(RawDataOnStateDto.class, tagSrlNo, acqDtm)` + WHERE `tagSrlNo.in` AND `acqDtm.goe(start)` AND `acqDtm.lt(end)` AND `qualityCd.eq(GOOD)` AND `rawVal.eq(BigDecimal.ONE)` + ORDER BY `tagSrlNo ASC, acqDtm ASC`) → 검증: 컴파일 통과 (Phase 7 빌드), WHERE 절 GOOD+raw_val=1 육안 확인

### Phase 3: 응답 DTO
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpOperationHistoryDto.java` 신규 — outer (`pumpId`·`pumpNm`·`List<OperationSegment> segments`, `@Getter` + private 생성자 + 정적팩토리 `of(...)` + `@ArraySchema(implementation=OperationSegment.class)`) + inner static `OperationSegment`(`startDtm`·`endDtm`, `@JsonFormat("yyyy-MM-dd HH:mm:ss")` + 정적팩토리 `of(...)`) → 검증: 컴파일 통과 (Phase 7 빌드)

### Phase 4: Service + 런렝스 인코딩 헬퍼
- [x] `api/src/main/java/com/mo/swtp/instrument/service/PumpOperationHistoryService.java` 신규 — `@Service @RequiredArgsConstructor @Transactional(readOnly=true)`. `findPumpOperationHistory(PumpPeriodSearchDto)` (기간검증 throw → 활성 PUMP 조회 → `loadOpsTagByInstrument` → `loadOnAcqByTag` → 펌프별 `toDto`) + `private encodeSegments(List<LocalDateTime>)` 런렝스 인코딩 (gap = OFF/BAD/결측 split, `endDtm = 마지막ON + 1분`, `COLLECTION_INTERVAL_MINUTES=1`) → 검증: 메서드 본문 50줄·추상화 3단 이내 (coding-discipline §2.1), Phase 6 테스트 GREEN

### Phase 5: Controller
- [x] `api/src/main/java/com/mo/swtp/instrument/web/PumpOperationHistoryController.java` 신규 — `@Tag("11. 송수펌프 가동이력") @RestController @RequestMapping("/api/instrument")` extends `CommonController`. `GET /pump-operation-history` (`@ModelAttribute PumpPeriodSearchDto` → `getResponseEntity(service.findPumpOperationHistory(search))`) + `@Operation`/`@ApiResponses`(200·400·401·403·404·500) → 검증: 컴파일 통과, ambiguous-mapping 미발생 (Phase 7 빌드)

### Phase 6: 단위 테스트 (Mockito)
- [x] `api/src/test/java/com/mo/swtp/instrument/service/PumpOperationHistoryServiceTest.java` 신규 — 8케이스: ①활성0대 빈리스트 ②전구간 연속ON 단일세그먼트 ③다회 껐다켜기(gap) 다수세그먼트 ④중간 결측/BAD(ON행 부재 gap) split ⑤전구간 OFF(ON행 0건) 세그먼트0건 ⑥endDtm=마지막ON acqDtm+1분 ⑦OPS태그없는펌프 segments빈배열 ⑧from>to INVALID_INQ_PERIOD 예외 → 검증: ./gradlew.bat :api:test --tests *PumpOperationHistoryServiceTest 8케이스 PASS

### Phase 7: 빌드·회귀 검증
- [x] `./gradlew.bat :api:test` 실행 — 신규 PumpOperationHistoryServiceTest GREEN + 기존 PumpPowerTimeSeriesServiceTest·PumpFrequencyTimeSeriesServiceTest 회귀 무영향 GREEN 확인
- [x] `./gradlew.bat :api:build` 실행 — BUILD SUCCESSFUL 출력 확인 (부모/자식 DTO·신규 record·응답 DTO·서비스·컨트롤러·조회 메서드 전체 무결성)

## 산출물
- [결과](../../../results/20260602/송수펌프가동이력_4번섹션/RESULT1.md) (Medium 작업 — RESULT 단계 면제, impl 후 commit 직행)
