---
status: completed
created: 2026-06-01
updated: 2026-06-01
---
# 운전현황분석 7번 섹션 — 시설 유출 유량·압력 + 펌프 가동상태 시계열 API 작업 분해

## 관련 계획
- [계획안](../../../plan/20260601/운전현황분석-7번섹션/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md` §4.1](../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. 검증 영역 백틱 금지 (`check-task-unstage.sh` 훅 파싱 충돌 방지).
>
> 의존 순서: 계측 Repository → 예측 Repository → 응답 DTO → Service → Controller → 테스트 → 빌드. 하위 계층부터 작성하여 상위 컴파일 의존을 해소한다. 기존 5·10번 Repository·4·5·9·10번 Service·DTO 는 무수정 (사이클 독립성, 사용자 Q3).

### Phase 1: 계측(raw) 전용 범위 조회 Repository 신규 (기존 RawDataCustomRepository 무수정)
- [x] `api/src/main/java/com/mo/swtp/raw/dto/RawDataOutflowDto.java` 생성 — record(tagSrlNo·acqDtm·rawVal·corrVal·qualityCd), Service 내부 전송 전용(Swagger 노출 외) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataOutflowCustomRepository.java` 생성 — findByTagSrlNosAndDtmRange(tagSrlNos, startDtm, endDtm) 시그니처 선언 → 검증: 인터페이스에 List 반환 findByTagSrlNosAndDtmRange 단일 메서드 선언 확인
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataOutflowCustomRepositoryImpl.java` 생성 — QRawData Querydsl + Projections.constructor(RawDataOutflowDto) + tagSrlNo.in().and(acqDtm.goe(start)).and(acqDtm.lt(end)) + orderBy(acqDtm.asc, tagSrlNo.asc), tagSrlNos 빈 목록 가드, §2.5 면책 인용(query-tuning.md §2) Javadoc → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataOutflowRepository.java` 생성 — extends JpaRepository(RawData, RawDataId), RawDataOutflowCustomRepository, 9→10번 의도 분리 Javadoc → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 2: 예측(opt) 전용 범위 조회 Repository 신규 (기존 TagPredcRangeCustomRepository 무수정)
- [x] `api/src/main/java/com/mo/swtp/opt/dto/TagPredcOutflowDto.java` 생성 — record(tagSrlNo·predcDtm·predcVal), predc_1m_h 는 corr_val·quality_cd 컬럼 없음 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/opt/repository/TagPredcOutflowCustomRepository.java` 생성 — findByTagSrlNosAndPredcDtmRange(tagSrlNos, startDtm, endDtm) 시그니처 선언 → 검증: 인터페이스에 List 반환 findByTagSrlNosAndPredcDtmRange 단일 메서드 선언 확인
- [x] `api/src/main/java/com/mo/swtp/opt/repository/TagPredcOutflowCustomRepositoryImpl.java` 생성 — QTagPrediction Querydsl + Projections.constructor(TagPredcOutflowDto) + predcDtm 범위 조건 + orderBy(predcDtm.asc, tagSrlNo.asc), 빈 목록 가드, §2.5 면책 인용 Javadoc → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/opt/repository/TagPredcOutflowRepository.java` 생성 — extends JpaRepository(TagPrediction, TagPredictionId), TagPredcOutflowCustomRepository → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 3: 응답 DTO 신규 (2-시리즈 분리)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityOutflowTimeSeriesDto.java` 생성 — 외부 클래스(facilityId·facilityNm·linePoints·pumpSeries + 정적 of) + inner static 3종(LinePoint{dtm·actualFlwrt·actualPrsr·predcFlwrt·predcPrsr}·PumpSeries{instrumentId·instrumentNm·points}·PumpPoint{dtm·actualRunning·predcRunning}), 각 private 생성자 + 정적 of, BaseAuditResponseDto 미상속 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityOutflowTimeSeriesDto.java` Swagger 어노테이션 — LocalDateTime 2지점 @JsonFormat(STRING, yyyy-MM-dd HH:mm:ss), @ArraySchema(schema=@Schema(implementation=...)) 3지점(linePoints·pumpSeries·points), 전 필드 한국어 @Schema(description) + 계측/예측 NULL 의미 분리 → 검증: ArraySchema 3건 + JsonFormat 2건 선언 확인, actualRunning description 에 불명(null) 의미 명시 확인

### Phase 4: Service 신규 (4·5·9·10번 무수정, 헬퍼 본 서비스 재구현)
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesService.java` 생성 — @Service @RequiredArgsConstructor @Transactional(readOnly=true), 의존성 주입(FacilityRepository·InstrumentRepository·TagRepository·RawDataOutflowRepository·TagPredcOutflowRepository), 상수 SUPPORTED_TYPES=EnumSet.of(PWTF,DWT,PRSF)·TARGET_EQUIP_TYPES=List.of(PUMP,FLWMTR)·TARGET_TAG_TYPES=EnumSet.of(OPS,FRI,PRI) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesService.java` 주 메서드 findFacilityOutflowTimeSeries(facilityId) — findActiveFacilityOrThrow → instrument 조회 → 빈 경우 빈 시리즈 응답 → 태그 그룹화 → tagSrlNos 계측·예측 [today 00:00, now) 각 1회 조회 → buildLinePoints + buildPumpSeries 조립 → 검증: 메서드 본문 50줄 이내 육안 확인
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesService.java` findActiveFacilityOrThrow 재구현 — use_yn=Y + SUPPORTED_TYPES, 미존재·비활성 FACILITY_NOT_FOUND / RSV·POINT UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS (FacilityErrorCode 재사용, 신규 0건) → 검증: 미지원 종류·미존재 분기 각 throw 경로 확인
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesService.java` buildLinePoints 3단 재구현 — selectPrimaryFlwmtr(첫 FLWMTR findFirst 1회 고정) → FRI·PRI tagSrlNo 확보 → 계측 slot effectiveVal(corrVal?:rawVal) GOOD 필터·예측 slot predcVal 직접 → dtm-key TreeMap 합본 + 4값 모두 null 슬롯 생략, FLWMTR 부재 시 빈 목록 → 검증: 메서드별 본문 50줄 이내 + slot별 FLWMTR 재선택 없음(고정 1회) 확인
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesService.java` buildPumpSeries 3단 분해 재구현 — filterPumps(equip_type=PUMP 강제) → 펌프별 OPS tagSrlNo dtm-key TreeMap 병합(groupActualOpsByDtm·groupPredcOpsByDtm·toPumpPointList) → 모든 PUMP instrument 포함(OPS 부재 시 빈 points) + 양쪽 null 슬롯 생략 → 검증: 분해 메서드 3개 각 50줄 이내 + PUMP 전수 포함 로직 확인
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesService.java` OPS 3-state 헬퍼 — actualRunningTriState(null·!GOOD→null / GOOD+1.0→true / GOOD+0.0→false) + predcRunningTriState(null·predcVal null→null / 1.0→true / 0.0→false), 4·5·10번 2-state 와 의도 분리 Javadoc(ot-integration.md §3) → 검증: 3-state 분기 각 반환값(true·false·null) 경로 확인

### Phase 5: Controller 엔드포인트 추가 (기존 메서드 무수정)
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 — GET /{facilityId}/operating-status/outflow-time-series 메서드 추가, ResponseEntity<CommonResponseDto<FacilityOutflowTimeSeriesDto>> + getResponseEntity 래핑, FacilityOutflowTimeSeriesService 주입, @Operation + @ApiResponses(200/400/401/403/404/500) + OPS 3-state·라인 결측·400(RSV·POINT)·404(미존재·비활성) description → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL + 기존 4·5·9·10 메서드 diff 무변경 확인

### Phase 6: 단위 테스트 신규
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesServiceTest.java` 생성 — @ExtendWith(MockitoExtension.class) + @Mock(5 repo) + @InjectMocks, 동일 시각 픽스처, 10 시나리오: (1)계측+예측 라인 공존 (2)펌프 on·off·불명 3-state (3)예측 on/off/null (4)FRI·PRI 결측 null (5)라인 4값 양쪽 부재 슬롯 생략 (6)주 FLWMTR 첫 매치 고정 (7)RSV·POINT 400 (8)비활성·미존재 404 (9)VALVE 등 비대상 제외 (10)OPS 부재 펌프 빈 points 포함 → 검증: ./gradlew.bat :api:test --tests "FacilityOutflowTimeSeriesServiceTest" PASS

### Phase 7: 빌드 검증
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOutflowTimeSeriesService.java` 정량 기준 최종 점검 — 주 메서드·buildLinePoints·buildPumpSeries 분해 3종·헬퍼 각 50줄 이내, 추상화 3단 이내 → 검증: 신규 Service 메서드별 본문 50줄 이내(빈줄·주석 제외) 육안 확인
- [x] `./gradlew.bat build` 전체 빌드 검증 → 검증: 출력에 BUILD SUCCESSFUL 포함

## 산출물
- Medium 작업 — RESULT/REVIEW 면제 ([transitions.md](../../../../.claude/rules/process/doc-harness/transitions.md) `/dev:impl` Medium 행). `/dev:impl` 완료 후 `/dev:commit` 직행. frontend SPEC 전파는 커밋 후 선택 단계(`/dev:spec`).
