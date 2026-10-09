---
status: completed
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석 5번섹션 — 전력량 추이(발생·예측) 조회 API

## 관련 계획
- [계획안](../../../plan/20260605/전력피크분석-5번섹션/PLAN1.md)

## Phase

> ROOT [`coding-discipline.md` §4.1](../../../../../.claude/rules/coding-discipline.md) 적용. 체크박스 형식: `- [ ] {파일경로 백틱} 작업 → 검증: {확인 명령 / 테스트 / 조회}`. 검증 영역에 백틱 사용 금지 (`check-task-unstage.sh` 훅 파싱 충돌 회피).

### Phase 1: 응답 DTO 작성

- [x] `api/src/main/java/com/mo/swtp/opt/dto/PeakEnergyTrendDto.java` 생성 — plain @Getter + private 기본 생성자 + 정적 팩토리 of(unit, targetPeakElpwr, billingPeakElpwr, measuredPoints, predictedPoints). BaseAuditResponseDto 미상속(집계 뷰). 필드 5개 @Schema. measuredPoints·predictedPoints 두 List 필드에 @ArraySchema(schema=@Schema(implementation=PeakEnergyTrendPoint.class)) 명기 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/opt/dto/PeakEnergyTrendDto.java` 중첩 static class PeakEnergyTrendPoint 추가 — baseDtm(LocalDateTime, @JsonFormat shape=STRING pattern yyyy-MM-dd HH:mm:ss) + elcegVal(BigDecimal) + 정적 팩토리 of(baseDtm, elcegVal). 섹션4 PumpEnergyPredictionPoint 동형 → 검증: 컴파일 후 grep 으로 PeakEnergyTrendPoint·@ArraySchema·@JsonFormat 3토큰 매칭 확인

### Phase 2: Service 작성

- [x] `api/src/main/java/com/mo/swtp/opt/service/PeakEnergyTrendService.java` 생성 — @Service @RequiredArgsConstructor @Transactional(readOnly=true) @Slf4j. 의존성 4개(TagRepository·RawDataRepository·PumpEnergyPredcRepository·PeakTargetService). 상수 UNIT_KWH·WINDOW_HOURS=12·BILLING_LOOKBACK_MONTHS=12 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/opt/service/PeakEnergyTrendService.java` 공개 메서드 getEnergyTrend() 구현 — base=now.truncatedTo(HOURS), getPeakTarget() fail-fast 선호출, pwq/pwi 태그 조회, measured [base-12h, base) / predicted [base, base+12h), billing 산정 후 DTO.of 반환. 본문 50줄 이내 → 검증: 컴파일 성공 + 메서드 본문 빈줄·주석 제외 50줄 이내 육안 확인
- [x] `api/src/main/java/com/mo/swtp/opt/service/PeakEnergyTrendService.java` private 헬퍼 6종 구현 — pwqTagSrlNos·pwiTagSrlNos(findByTagSeCdAndUseYn), aggregateMeasured(List RawDataBucketDto)·aggregatePredicted(List PredcEnergyBucketDto)(TreeMap merge), validDeltaOrNull(공유 단일 소스, 음수 WARN 로그), billingPeak(findMaxMinuteSumElpwr null→ZERO) → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL

### Phase 3: Controller 확장 + Repository Javadoc 갱신

- [x] `api/src/main/java/com/mo/swtp/opt/web/PeakPowerAnalysisController.java` 수정 — PeakEnergyTrendService 의존성 주입 + GET /energy-trend 메서드 추가(파라미터 없음, getResponseEntity(service.getEnergyTrend()) 위임). @Operation summary + @ApiResponses(200/401/500, 500 description 에 목표값 시드 미초기화·현재 시 버킷 예측 부분집계 명기). 클래스 Javadoc 에 5번섹션 엔드포인트 행 추가 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL
- [x] `api/src/main/java/com/mo/swtp/opt/repository/PumpEnergyPredcRepository.java` 수정 — Javadoc 스코프에 "시설 PWQ 예측(4번섹션) → 전역 PWQ 추세(5번섹션) 재사용" 이력 1줄 추가. 코드 시그니처·SQL 불변 → 검증: ./gradlew.bat :api:compileJava BUILD SUCCESSFUL + git diff 로 메서드 시그니처 변경 0건 확인

### Phase 4: 단위 테스트 작성

- [x] `api/src/test/java/com/mo/swtp/opt/service/PeakEnergyTrendServiceTest.java` 생성 — @ExtendWith(MockitoExtension.class), @Mock 4종 + @InjectMocks. 윈도우 경계 검증(ArgumentCaptor 로 measured/predicted 인자 base±12h 확인) → 검증: 해당 테스트 메서드 GREEN
- [x] `api/src/test/java/com/mo/swtp/opt/service/PeakEnergyTrendServiceTest.java` 버킷 합산·음수 제외 테스트 추가 — 2태그 동일 baseDtm 버킷 SUM 일치 + 음수 aggrVal 버킷 결과 미포함 → 검증: 해당 테스트 메서드 GREEN
- [x] `api/src/test/java/com/mo/swtp/opt/service/PeakEnergyTrendServiceTest.java` 빈 입력·시드 부재 테스트 추가 — 태그 0개 / 버킷 0행 → 빈 시계열+ZERO billing+target 반환, getPeakTarget() PEAK_TARGET_NOT_INITIALIZED throw 시 동일 예외 전파 → 검증: 해당 테스트 메서드 GREEN

### Phase 5: 빌드·회귀 검증

- [x] api 모듈 전체 테스트 통과 → 검증: ./gradlew.bat :api:test BUILD SUCCESSFUL (신규 PeakEnergyTrendServiceTest GREEN + 기존 테스트 회귀 0건)

## 산출물
- [결과](../../../results/20260605/전력피크분석-5번섹션/RESULT1.md)
