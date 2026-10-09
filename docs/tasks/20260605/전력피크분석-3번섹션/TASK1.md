---
status: completed
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석-3번섹션 — 송수펌프 순시전력 추가 작업 분해

## 관련 계획
- [계획안](../../../plan/20260605/전력피크분석-3번섹션/PLAN1.md)

## Phase

> 검증 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령}`. 검증 영역 백틱 금지(훅 파싱 호환). 본 작업은 `feat` — 2번섹션 엔드포인트 확장(읽기 전용 집계 1지표 추가). 신규 DDL·테이블·인덱스 0건.

### Phase 1: 리포지토리 계층 (펌프 매핑 PWI 식별 파생쿼리)
- [x] `api/src/main/java/com/mo/swtp/tag/repository/TagRepository.java` findByTagSeCdAndUseYnAndInstrument_EquipType(TagMeasurementType, YnType, EquipType) 파생쿼리 + Javadoc(INNER JOIN N+1 회피 근거) 추가 → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 2: 응답 DTO (pumpElpwr 필드 추가)
- [x] `api/src/main/java/com/mo/swtp/opt/dto/PeakPowerAnalysisDto.java` totalElpwr 직후 pumpElpwr 필드 추가 + @Schema(description On/Off무관·혼동주의·example) + 정적팩토리 of 5인자 확장 + class Javadoc 갱신. 기존 4필드 @Schema·@JsonFormat 무수정 → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 3: Service (latest 1회 호출 공유 리팩터 + 펌프 부분합)
- [x] `api/src/main/java/com/mo/swtp/opt/service/PeakPowerAnalysisService.java` getPeakPowerAnalysis 본문을 latest 단일 fetch 공유로 리팩터 + pumpPwiTagSrlNos·pumpLatest 헬퍼 추가 + sumLatestPwi(List String)→sumGoodPwi(List RawDataLatestDto) 시그니처 변경 + of 5인자 호출 + import 3종(Set·Collectors·EquipType) + class Javadoc 송수펌프 항목 추가 → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 4: Controller (텍스트 최소 갱신)
- [x] `api/src/main/java/com/mo/swtp/opt/web/PeakPowerAnalysisController.java` class Javadoc·@Operation summary/description 의 2번섹션 4지표 표현을 2·3번섹션 5지표로 갱신 + 송수펌프 순시전력 1줄 추가. 경로·시그니처·@ApiResponses 무변경 → 검증: ./gradlew.bat :api:compileJava 성공

### Phase 5: 단위 테스트 (신규 케이스 2건)
- [x] `api/src/test/java/com/mo/swtp/opt/service/PeakPowerAnalysisServiceTest.java` 펌프 부분집합 합산 케이스(펌프 PWI GOOD 100·50 + 비펌프 PWI GOOD 200 → pumpElpwr 150·totalElpwr 350 + findLatestByTagSrlNos times1 검증) + 펌프 0개 fallback 케이스(펌프 쿼리 빈 리스트 → pumpElpwr ZERO·totalElpwr 양수) 추가. 기존 5케이스 stub 무변경 → 검증: ./gradlew.bat :api:test --tests PeakPowerAnalysisServiceTest GREEN (7/7 통과)

### Phase 6: 빌드·기동·쿼리 검증
- [x] 전체 빌드 검증 → 검증: ./gradlew.bat build BUILD SUCCESSFUL (api/common/scheduler 전 모듈 + @SpringBootTest 컨텍스트 로드 — 시그니처 변경 빈 와이어링 정상)
- [x] GET 엔드포인트 5지표 산출값 + 펌프합 SQL 대조 → 검증: dev MCP SQL 대조 — pumpElpwr = 펌프(equip_type_cd=PUMP) 매핑 PWI GOOD 합, 현 시드 8/8 펌프 매핑이므로 pumpElpwr == totalElpwr 일치 확인 (total 85.3208 == pump 85.3208, total_pwi_cnt 8 == pump_pwi_cnt 8)

## 산출물
- [결과](../../../results/20260605/전력피크분석-3번섹션/RESULT1.md)
