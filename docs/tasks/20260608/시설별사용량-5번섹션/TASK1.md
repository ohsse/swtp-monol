---
status: completed
created: 2026-06-08
updated: 2026-06-08
---
# 시설별 사용량 5번섹션 — 운영시설 전력량 트렌드 조회 API

## 관련 계획
- [계획안](../../../plan/20260608/시설별사용량-5번섹션/PLAN1.md)

## Phase

### Phase 1: 검색·응답 DTO (신규 2종)
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityEnergyTrendSearchDto.java` 생성 — `FacilityEnergyUsageSearchDto` 동형복제(inqUnit·fromDt·toDt + isValid 13개월·YEAR거부 + toStartDtm/toEndExclusiveDtm), @Schema 5번섹션 맥락, Javadoc 동형복제 사유 명기 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityEnergyTrendDto.java` 생성 — outer(facilityId·facilityNm·points) + 중첩 EnergyTrendPoint(baseDtm @JsonFormat·elcegVal), @Getter+private 생성자+정적팩토리 of, points 에 @ArraySchema(implementation=EnergyTrendPoint) → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 2: Service (신규)
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityEnergyTrendService.java` 생성 — @Service @Transactional(readOnly=true), findEnergyTrend 퍼블릭 + buildInstrumentToRoot/collectPwqTagToRoot/aggregateTrendByRoot/assembleSeriesList/validDeltaOrNull 헬퍼, OPERATION_TYPES static, findEnergyDeltaBuckets 재사용, Map root→TreeMap baseDtm 버킷 보존 합산, 동형복제 카운트 Javadoc → 검증: ./gradlew.bat :api:compileJava 성공 + 퍼블릭 메서드 본문 50줄 이내

### Phase 3: Controller 엔드포인트 (수정)
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 수정 — FacilityEnergyTrendService 주입 필드 + import, GET /api/facility/energy-trend 추가(energy-usage L434 다음), @ModelAttribute 바인딩, @Operation/@ApiResponses 200/400/401/403/500, @ArraySchema(implementation=FacilityEnergyTrendDto), description 에 0 보간 금지 명시 → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 4: 단위 테스트 (신규)
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityEnergyTrendServiceTest.java` 생성 — Mockito 8케이스(버킷 합산·baseDtm ASC·음수차분 제외·points 빈리스트·하위 귀속·OPERATION 8종 한정·operatingRoots 부재 후속 미호출 verify·isValid 실패 INVALID_SEARCH_PERIOD), FacilityEnergyUsageServiceTest 패턴 미러링 → 검증: ./gradlew.bat :api:test 실행 시 신규 테스트 8건 GREEN

### Phase 5: 빌드 검증
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityEnergyTrendService.java` 전체 빌드 무결성 확인 → 검증: ./gradlew.bat :common:build 성공 후 ./gradlew.bat :api:build 성공(BUILD SUCCESSFUL 출력)

## 산출물
- 구현 완료 후 `/dev:commit 시설별사용량-5번섹션` 진행 (Medium — RESULT/REVIEW 면제)
