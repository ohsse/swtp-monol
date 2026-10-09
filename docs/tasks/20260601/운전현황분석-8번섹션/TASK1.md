---
status: completed
created: 2026-06-01
updated: 2026-06-01
---
# 운전현황분석-8번섹션 — 수요량/관압/수위 재귀 하위 시설 계측+예측 시계열 API 구현

## 관련 계획
- [계획안](../../../plan/20260601/운전현황분석-8번섹션/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md` §4.1](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. 검증 영역 백틱 금지 (`check-task-unstage.sh` 훅 파싱 충돌). 계층 의존 순서(enum → DTO → Resolver → Service → Controller → 테스트 → 빌드)로 배치.

### Phase 1: 공통 dataType enum (common 모듈)
- [x] `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityDownstreamDataType.java` 생성 — DEMAND/PRESSURE/LEVEL 3값 enum, 값별 @Schema description(수요량/관압/수위), 5번 FacilityOperatingStatusCompareType 위치·패턴 선례 → 검증: ./gradlew.bat :common:build 출력 BUILD SUCCESSFUL

### Phase 2: 다형성 응답 DTO (api 모듈)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/DownstreamPoint.java` 생성 — 공유 포인트 {dtm(@JsonFormat yyyy-MM-dd HH:mm:ss), actualVal, predcVal, ratio} + 정적 팩토리 of, BaseAuditResponseDto 미상속 → 검증: 필드 4개 + 계측·예측·대비율 null 의미 한국어 @Schema 조회 확인
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityDownstreamTimeSeriesDto.java` 생성 — abstract {facilityId(루트), facilityNm, dataType} + @JsonTypeInfo(use=NAME, include=EXISTING_PROPERTY, property=dataType, visible=true) + @JsonSubTypes(Measure names DEMAND·PRESSURE, Level name LEVEL) + @Schema(oneOf Measure·Level, discriminatorProperty=dataType) → 검증: @JsonSubTypes 매핑이 3 dataType 모두 커버하는지 조회 확인
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityDownstreamMeasureDto.java` 생성 — extends 부모, {List MeasureSeries series}, 중첩 static MeasureSeries{facilityId, facilityNm, facilityTypeCd(@Schema implementation=FacilityType), instrumentId(유출 FLWMTR), instrumentNm, multipleOutletFlwmtrDetected, List DownstreamPoint points} + @ArraySchema + 정적 팩토리 of → 검증: series·points @ArraySchema(schema=@Schema(implementation)) 명시 조회 확인
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityDownstreamLevelDto.java` 생성 — extends 부모, {List LevelSeries series}, 중첩 static LevelSeries{facilityId(DWT), facilityNm, facilityTypeCd, instrumentId(LVMTR), instrumentNm, parentFacilityId, parentFacilityNm, List DownstreamPoint points} + @ArraySchema + 정적 팩토리 of → 검증: parent 그룹핑 필드 2개 + @ArraySchema 명시 조회 확인

### Phase 3: 재귀 하위 도출 (Repository 메서드 + Resolver)
- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityRepository.java` 수정 — findByParentFacilityIdInAndUseYn(List parentIds, YnType useYn) Spring Data 파생 쿼리 메서드 1개 추가, 기존 메서드 무수정 → 검증: ./gradlew.bat :api:build 컴파일 PASS (파생 쿼리 시그니처 부트스트랩 검증)
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityDownstreamTreeResolver.java` 생성 — @Component, 앱 레벨 BFS(depth<MAX_DEPTH 상수 + visited-set 순환 방어), 인메모리 분류(dwtsByParent 그룹핑 → displayTargets = subtree 중 PWTF·POINT AND DWT 자식 보유, 루트 inclusive), 반환 record DownstreamTopology(List displayTargets, Map dwtsByTargetId), 추가 쿼리 0 → 검증: Phase 6 FacilityDownstreamTreeResolverTest 로 분류·방어 검증

### Phase 4: Service — dataType 분기 + 병합 + 대비율 (api 모듈)
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityDownstreamTimeSeriesService.java` 생성 — @Service @Transactional(readOnly=true), findDownstreamTimeSeries(facilityId, dataType): 루트 404(FACILITY_NOT_FOUND) → Resolver 호출 → DEMAND·PRESSURE 분기(유출 FLWMTR io_cd ∈ OUTPUT·BIDIR 필터 + dispOrd 첫 매치 + multipleOutletFlwmtrDetected 플래그 + dataType별 FRI/PRI 태그) → 검증: Phase 6 ServiceTest DEMAND·PRESSURE·유입계 미선택 케이스 PASS
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityDownstreamTimeSeriesService.java` LEVEL 분기 추가 — dwtIds → LVMTR 조회 → LEI 태그(io_cd 무필터) → 수위계당 1 시리즈, parentFacilityId/Nm = DWT 직속 parent 표출대상 그룹핑 → 검증: Phase 6 ServiceTest LEVEL 수위계 M→M·parent 그룹 케이스 PASS
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityDownstreamTimeSeriesService.java` 병합 헬퍼 추가 — effectiveVal(corrVal 우선)·effectiveValGood(GOOD 만)·TreeMap fillActual→overlayPredc→toPoints(양쪽 null 슬롯 생략) + computeRatio(actual null·0 또는 predc null → null; 그 외 predc/actual×100 setScale(1,HALF_UP)) → 검증: Phase 6 ServiceTest ratio 경계(0·null·BAD→null·소수1자리) 케이스 PASS

### Phase 5: Controller 엔드포인트 (api 모듈)
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 — @GetMapping("/{facilityId}/operating-status/downstream-time-series") 메서드 1개 추가, @RequestParam FacilityDownstreamDataType dataType(@Schema implementation), @Tag 06. 시설물 관리, @ApiResponses 200/400/401/403/404/500, @ApiResponse content oneOf(Measure·Level) discriminatorProperty=dataType, getResponseEntity(service.findDownstreamTimeSeries(...)) → 검증: ./gradlew.bat :api:build 컴파일 PASS

### Phase 6: 단위 테스트 (Mockito, api 모듈)
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityDownstreamTreeResolverTest.java` 생성 — @ExtendWith(MockitoExtension): 다단계 트리(루트→PWTF→DWT / 루트→중간RSV→POINT→DWT) 분류 / 루트 inclusive 표출대상 / DWT 자식 보유 필터(미보유 PWTF 제외) / DWT parent 비-PWTF/POINT 제외 / 순환·MAX_DEPTH 방어 → 검증: ./gradlew.bat :api:test --tests *FacilityDownstreamTreeResolverTest PASS
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityDownstreamTimeSeriesServiceTest.java` 생성 — @ExtendWith(MockitoExtension): DEMAND N→N 시리즈(유출 FLWMTR FRI 계측+예측+ratio) / PRESSURE PRI / LEVEL 수위계 M→M(LEI+parent 그룹) / 유입(INPUT)계만 보유 시설 유출 시리즈 제외 verify / 다중 유출계 multipleOutletFlwmtrDetected=true / ratio 경계(계측 0·null·BAD 또는 예측 null→null) / 루트 미존재·비활성 404 / 표출대상 0건 빈 series(200) / 태그 부재 빈 points → 검증: ./gradlew.bat :api:test --tests *FacilityDownstreamTimeSeriesServiceTest PASS

### Phase 7: 전체 빌드 검증
- [x] `./gradlew.bat :common:build` 실행 → 검증: BUILD SUCCESSFUL 출력 (신규 enum 컴파일)
- [x] `./gradlew.bat :api:test` 실행 → 검증: 신규 2 테스트 클래스 포함 전체 PASS
- [x] `./gradlew.bat build` 실행 → 검증: 전체 BUILD SUCCESSFUL (QClass 재생성 포함, 회귀 없음)

## 산출물
- 규모 **Medium** — RESULT/REVIEW 면제 (transitions.md). `/dev:impl 운전현황분석-8번섹션` 구현 완료 후 `/dev:commit 운전현황분석-8번섹션` 진행.
