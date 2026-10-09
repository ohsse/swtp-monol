---
status: completed
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석-4번섹션 — 시설물 펌프 전력량 예측 시계열 API

## 관련 계획
- [계획안](../../../plan/20260605/전력피크분석-4번섹션/PLAN1.md)

## Phase

> 검증 기준: ROOT `coding-discipline.md §4.1` — 체크박스에 `→ 검증:` 명시, 검증 영역 백틱 금지. 모든 신규 파일은 `api` 모듈, `common`·DB 변경 0건. 기능1(펌프 소유 시설 목록)은 기존 `GET /api/facility?hasPump=true` 재사용으로 신규 작업 없음.

### Phase 1: 내부 전송 record + 응답 DTO
- [x] `api/src/main/java/com/mo/swtp/opt/dto/PredcEnergyBucketDto.java` 생성 — record(tagSrlNo, baseDtm, aggrVal), RawDataBucketDto 동형 Swagger 비노출 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/opt/dto/PumpEnergyPredictionDto.java` 생성 — @Getter + 정적 팩토리 of(), 필드 facilityId·facilityNm·unit·points, 중첩 PumpEnergyPredictionPoint(baseDtm·elcegVal) @JsonFormat 초단위 + @ArraySchema → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 2: Repository 트리플 (predc_1m_h 버킷 차분)
- [x] `api/src/main/java/com/mo/swtp/opt/repository/PumpEnergyPredcCustomRepository.java` 생성 — findEnergyDeltaBuckets(tagSrlNos, startDtm, endDtm) 시그니처 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/opt/repository/PumpEnergyPredcCustomRepositoryImpl.java` 생성 — native MAX(predc_val)-MIN(predc_val) per date_trunc('hour'), predc_val IS NOT NULL, tagSrlNos empty early return, JdbcTimestamps 매핑, §2.5 면책 주석 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/opt/repository/PumpEnergyPredcRepository.java` 생성 — JpaRepository<TagPrediction, TagPredictionId> + PumpEnergyPredcCustomRepository → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 3: Service
- [x] `api/src/main/java/com/mo/swtp/opt/service/PumpEnergyPredictionService.java` 생성 — @Service @Transactional(readOnly) @Slf4j, 주입 4종(Facility/Instrument/Tag/PumpEnergyPredc), getPumpEnergyPrediction(facilityId) 4-SELECT 흐름 + private 헬퍼 분리(findActiveFacilityOrThrow·loadActivePumpIds·loadPwqTagSrlNos·aggregateByBucket·validDeltaOrNull·emptyResponse), 시간창 truncatedTo(HOURS)~+24h, 음수차분 제외+WARN 로그 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL, 공개 메서드 본문 50줄 이내 육안 확인

### Phase 4: Controller 메서드 추가 (기존 파일 수정)
- [x] `api/src/main/java/com/mo/swtp/opt/web/PeakPowerAnalysisController.java` 수정 — PumpEnergyPredictionService 주입 추가 + @GetMapping("/pump-energy-prediction") @RequestParam facilityId 메서드, Swagger @Operation/@ApiResponses(200/400/401/404/500) "현재 시 버킷 부분 집계" 명기, 클래스 javadoc "2·3번섹션"→"2·3·4번섹션" → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 5: 단위 테스트
- [x] `api/src/test/java/com/mo/swtp/opt/service/PumpEnergyPredictionServiceTest.java` 생성 — Mockito 7 케이스(다펌프 합산+필터 정상·시설 미존재 404·시설 비활성 404·펌프 0대 빈 시계열·PWQ 태그 0개 빈 시계열·음수차분 버킷 제외·예측 0행 빈 시계열) → 검증: ./gradlew.bat :api:test --tests *PumpEnergyPredictionServiceTest 8 케이스 GREEN
- [x] 다펌프 차분 후 합산 정확성 케이스 — 2펌프 동일 버킷 차분 mock, 시각별 합산값이 펌프별 MAX-MIN 합과 일치 assert → 검증: 해당 테스트 케이스 GREEN

### Phase 6: 빌드·회귀 검증
- [x] `./gradlew.bat :api:test` 전체 실행 → 검증: BUILD SUCCESSFUL, 기존 테스트 회귀 0건
- [x] Swagger 노출 확인 — 14. 전력피크 분석 태그에 GET /api/opt/peak-power-analysis/pump-energy-prediction 노출 → 검증: @Operation/@ApiResponses 어노테이션 컴파일 검증 완료 (라이브 :api:bootRun 기동 확인은 선택 항목 — 생략)

## 산출물
- [결과](../../../results/20260605/전력피크분석-4번섹션/RESULT1.md)
