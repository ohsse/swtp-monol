---
status: completed
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석-2번섹션 — 4지표 조회 API 작업 분해

## 관련 계획
- [계획안](../../../plan/20260605/전력피크분석-2번섹션/PLAN1.md)

## Phase

> 검증 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령}`. 검증 영역 백틱 금지(훅 파싱 호환). 본 작업은 `feat` — 읽기 전용 집계 API 추가.

### Phase 1: 리포지토리 계층 (조회·집계 쿼리)
- [x] `api/src/main/java/com/mo/swtp/tag/repository/TagRepository.java` findByTagSeCdAndUseYn(TagMeasurementType, YnType) 파생쿼리 + Javadoc 추가 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` findMaxMinuteSumElpwr(List, LocalDateTime, LocalDateTime) 선언 + Javadoc 추가 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` 12개월 분단위 SUM의 MAX native 구현 + GOOD 필터 + COALESCE + §2.5 면책 주석 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/repository/PeakPredcCustomRepository.java` findEarliestPredcDtmOverTarget(List, LocalDateTime, LocalDateTime, BigDecimal) 인터페이스 선언 + Javadoc → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/repository/PeakPredcCustomRepositoryImpl.java` Querydsl GROUP BY predcDtm HAVING SUM(predcVal) gt target + ASC LIMIT 1 + 빈입력 단락 + §2.5 면책 주석 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/repository/PeakPredcRepository.java` JpaRepository TagPrediction·TagPredictionId + PeakPredcCustomRepository 상속 선언 → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 2: 응답 DTO + Service
- [x] `api/src/main/java/com/mo/swtp/opt/dto/PeakPowerAnalysisDto.java` 4필드(totalElpwr·targetPeakElpwr·billingPeakElpwr·predcPeakDtm) @Getter+private생성자+정적팩토리 of + @Schema + predcPeakDtm @JsonFormat 초단위 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/java/com/mo/swtp/opt/service/PeakPowerAnalysisService.java` @Transactional(readOnly) + 헬퍼4(pwiTagSrlNos·sumLatestPwi·billingPeak·expectedPeakDtm) + effectiveVal 복제 + @Value horizon + now 단일호출 + 엣지 fallback → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 3: Controller + 설정
- [x] `api/src/main/java/com/mo/swtp/opt/web/PeakPowerAnalysisController.java` extends CommonController + @Tag 14.전력피크 분석 + GET /api/opt/peak-power-analysis + @Operation + @ApiResponses 200·401·500 → 검증: ./gradlew.bat :api:compileJava 성공
- [x] `api/src/main/resources/application-common.yml` opt 블록에 peak.predc-horizon-hours 48 + 주석 추가 → 검증: ./gradlew.bat build 시 컨텍스트 로드 YAML 파싱 오류 없음

### Phase 4: 단위 테스트
- [x] `api/src/test/java/com/mo/swtp/opt/service/PeakPowerAnalysisServiceTest.java` Mockito 5케이스(GOOD합산·target0 예측미호출·PWI0 fallback·billing null→ZERO·예측 최근접 매핑) 작성 → 검증: ./gradlew.bat :api:test --tests PeakPowerAnalysisServiceTest GREEN (5/5 통과)

### Phase 5: 빌드·기동·쿼리 검증
- [x] 전체 빌드 검증 → 검증: ./gradlew.bat build BUILD SUCCESSFUL (api/common/scheduler 전 모듈 + @SpringBootTest 컨텍스트 로드 — 신규 빈 와이어링 정상)
- [x] billing 12개월 MAX·predc horizonEnd 쿼리 파티션 프루닝 EXPLAIN → 검증: dev MCP EXPLAIN ANALYZE — billing 202605·202606 2파티션(6.0ms), predc 202606 1파티션(0.13ms) 프루닝 확인
- [x] GET 엔드포인트 4지표 산출값 + Swagger 노출 → 검증: 실데이터 4지표 SQL 대조(total 67.5954·target 0·billing 129.9404·predc null[target0 가드]) + @Tag 14 컴파일·컨텍스트 로드. 인증 HTTP 스모크는 서버 기동 후 수동 (환경 제약)

## 산출물
- [결과](../../../results/20260605/전력피크분석-2번섹션/RESULT1.md)
