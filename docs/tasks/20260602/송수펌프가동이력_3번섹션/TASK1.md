---
status: completed
created: 2026-06-02
updated: 2026-06-02
---
# 송수펌프 가동이력 3번섹션 — 전력량/주파수 시계열 차트 API

## 관련 계획
- [계획안](../../../plan/20260602/송수펌프가동이력_3번섹션/PLAN1.md)

## Phase

> 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. 검증 영역 백틱 미사용 (훅 파싱 호환). 계층 의존 순서대로 Phase 구성.

### Phase 1: common 모듈 — enum 기반 (InqUnit 신규 + TagMeasurementType PWQ)

- [x] `common/src/main/java/com/mo/swtp/common/enumtype/InqUnit.java` 신규 — HOUR/DAY/MONTH/YEAR + 필드 dateTruncUnit("hour"/"day"/"month"/"year") + getter, Javadoc → 검증: ./gradlew.bat :common:build 컴파일 통과
- [x] `common/src/main/java/com/mo/swtp/tag/domain/enumtype/TagMeasurementType.java` PWQ("적산전력량","kWh") enum 값 추가 + Javadoc (PWI 단위 분리·HLV 미적용 명기) → 검증: ./gradlew.bat :common:build BUILD SUCCESSFUL 출력 확인

### Phase 2: api 모듈 — 집계 데이터 계층 (record + repository 2종)

- [x] `api/src/main/java/com/mo/swtp/raw/dto/RawDataBucketDto.java` 신규 — record(String tagSrlNo, LocalDateTime baseDtm, BigDecimal aggrVal), RawDataLatestDto 선례 동형 (Service 내부 전송, Swagger 미노출) → 검증: ./gradlew.bat :api:build 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` findEnergyDeltaBuckets·findAvgValueBuckets 시그니처 2종 추가 (List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit) → 검증: 인터페이스 메서드 2건 선언 확인
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` native SQL 구현 2종 — 전력량 MAX(raw_val)-MIN(raw_val) GOOD·raw_val NOT NULL·corr_val 미사용, 주파수 AVG(COALESCE(corr_val,raw_val)) GOOD, 공통 date_trunc(:unit) GROUP BY·acq_dtm 범위 파티션 프루닝, Object[]→record 매핑, §2.5 면책 주석(query-tuning.md §2 인용) → 검증: ./gradlew.bat :api:build BUILD SUCCESSFUL 출력 확인

### Phase 3: api 모듈 — DTO + ErrorCode

- [x] `api/src/main/java/com/mo/swtp/instrument/exception/InstrumentErrorCode.java` INVALID_INQ_PERIOD(400) enum 값 추가 → 검증: ./gradlew.bat :api:build 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpTimeSeriesSearchDto.java` 신규 — inqUnit(InqUnit)·fromDt·toDt(LocalDate) + isValid()·toStartDtm()·toEndExclusiveDtm() 변환 메서드, @ModelAttribute 수신, DTO 예외 throw 회피 (boolean·변환만) → 검증: 기간 변환 [from 00:00, to+1일 00:00) 메서드 본문 확인
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpPowerTimeSeriesDto.java` 신규 — outer{pumpId,pumpNm,unit="kWh",points} + inner static PumpPowerTimeSeriesPoint{baseDtm(@JsonFormat yyyy-MM-dd HH:mm:ss),elcegVal} + private 생성자 + 정적팩토리 of(), points @ArraySchema(implementation=Point.class), BaseAuditResponseDto 미상속 → 검증: ./gradlew.bat :api:build 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/instrument/dto/PumpFrequencyTimeSeriesDto.java` 신규 — outer{pumpId,pumpNm,unit="Hz",points} + inner static PumpFrequencyTimeSeriesPoint{baseDtm,freqVal} + 정적팩토리 of(), FacilityDailyTimeSeriesDto 패턴 동형 → 검증: ./gradlew.bat :api:build BUILD SUCCESSFUL 출력 확인

### Phase 4: api 모듈 — Service 2종

- [x] `api/src/main/java/com/mo/swtp/instrument/service/PumpPowerTimeSeriesService.java` 신규 — @Transactional(readOnly=true), 기간검증 throw(INVALID_INQ_PERIOD)→활성PUMP조회→PWQ태그 IN 일괄→findEnergyDeltaBuckets→펌프별 DTO 매핑, 음수 aggrVal signum()<0 버킷 생략 가드, loadTagsByMeasurementType·toDto private 헬퍼 분리(50줄·3단 이내) → 검증: ./gradlew.bat :api:build 컴파일 통과
- [x] `api/src/main/java/com/mo/swtp/instrument/service/PumpFrequencyTimeSeriesService.java` 신규 — 활성PUMP중 (Pump)캐스팅 getDriveType()==INVERTER_DRIVE 인메모리 필터→FQI태그 IN→findAvgValueBuckets→펌프별 DTO 매핑, 음수가드 없음, 단위 "Hz" → 검증: ./gradlew.bat :api:build BUILD SUCCESSFUL 출력 확인

### Phase 5: api 모듈 — Controller (신규)

- [x] `api/src/main/java/com/mo/swtp/instrument/web/PumpTimeSeriesController.java` 신규 — @Tag("11. 송수펌프 가동이력") extends CommonController @RequestMapping("/api/instrument"), GET /pump-power-timeseries·/pump-frequency-timeseries @ModelAttribute 수신, getResponseEntity 래핑, @Operation/@ApiResponses(200·400·401·403·404·500) → 검증: ./gradlew.bat :api:build BUILD SUCCESSFUL 출력 확인

### Phase 6: 단위 테스트 (Mockito)

- [x] `api/src/test/java/com/mo/swtp/instrument/service/PumpPowerTimeSeriesServiceTest.java` 신규 — 6케이스: 활성0대 빈리스트 / 2펌프 계열분리 / 음수버킷 생략 / from>to 예외(INVALID_INQ_PERIOD) / PWQ태그없는펌프 points빈배열 / inqUnit=MONTH→repo dateTruncUnit "month" 전달(ArgumentCaptor) → 검증: ./gradlew.bat :api:test PumpPowerTimeSeriesServiceTest 6케이스 PASS
- [x] `api/src/test/java/com/mo/swtp/instrument/service/PumpFrequencyTimeSeriesServiceTest.java` 신규 — 4케이스: 인버터만포함(정격제외) / AVG버킷 펌프별매핑 / from>to 예외 / 인버터0대 빈리스트 → 검증: ./gradlew.bat :api:test PumpFrequencyTimeSeriesServiceTest 4케이스 PASS

### Phase 7: 빌드·통합 검증

- [x] common→api 순차 빌드 무결성 → 검증: ./gradlew.bat :common:build 후 ./gradlew.bat :api:build 각각 BUILD SUCCESSFUL 출력 확인
- [x] 전체 단위 테스트 GREEN → 검증: ./gradlew.bat :api:test 신규 2개 테스트 클래스 포함 전체 PASS
- [x] 엔드포인트 2개 Spring 컨텍스트 등록 확인 → 검증: ./gradlew.bat :api:build 통합테스트 Spring 컨텍스트 정상 기동 (ambiguous-mapping 미발생 — /pump-power-timeseries·/pump-frequency-timeseries 매핑 등록). Swagger UI 시각 확인은 사용자 수동 단계

## 산출물
- [결과](../../../results/20260602/송수펌프가동이력_3번섹션/RESULT1.md)
