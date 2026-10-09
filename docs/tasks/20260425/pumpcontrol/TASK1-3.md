---
status: completed
created: 2026-04-25
updated: 2026-04-25
---
# 송수펌프 제어 — 인프라·검증 (scheduler·멀티테넌트·테스트·빌드)

## 관련 계획
- [계획안](../../../plan/20260425/pumpcontrol/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 데이터 계층 (common 모듈)](TASK1-1.md)
- [TASK1-2 애플리케이션 계층 (api 모듈)](TASK1-2.md)
- [TASK1-3 인프라·검증 (현재 파일)](TASK1-3.md)

## Phase

### Phase 1: 의존성 추가 (build.gradle)

- [x] `api/build.gradle` 수정 — `implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'`, `implementation 'io.github.resilience4j:resilience4j-circuitbreaker:2.2.0'`, `implementation 'io.github.resilience4j:resilience4j-retry:2.2.0'` 추가 (TASK1-2 Phase 0 에서 선행 반영 — `@CircuitBreaker`/`@Retry` 컴파일 blocker 해소. Caffeine 도 함께 추가하여 TASK1-3 Phase 2 의 `expireAfterWrite=5m` 설정이 실제 TTL 로 동작하도록 준비)
- [x] `api/build.gradle` 수정 — Spring `@Cacheable` 활성화 위해 `implementation 'org.springframework.boot:spring-boot-starter-cache'` 추가 (이미 있으면 스킵) (TASK1-2 Phase 0 선행 반영)
- [x] `./gradlew.bat :api:dependencies --configuration runtimeClasspath` 실행하여 신규 의존성 정상 해결 확인 (TASK1-3 검증 시점에 실행 — resilience4j-spring-boot3 2.2.0, resilience4j-circuitbreaker 2.2.0, resilience4j-retry 2.2.0, caffeine 3.2.3, spring-boot-starter-cache 4.0.5 모두 정상 해결)

### Phase 2: 멀티테넌트 설정 (application.yml)

- [x] `api/src/main/resources/application.yml` 수정 — `ai.server.base-url`(기본값 `http://localhost:8000`) · `scada.outbound.timeout-seconds`(기본 3) · `pumpcontrol.scada.timeout-minutes`(5) · resilience4j 설정 (`circuitbreaker.instances.aiPrediction`/`scadaOutbound`, `retry.instances.aiPrediction`/`scadaOutbound`) 추가
- [x] `api/src/main/resources/application.yml` 수정 — `spring.cache.type: caffeine`, `spring.cache.cache-names: pumpMasterExists`, `spring.cache.caffeine.spec: maximumSize=1000,expireAfterWrite=5m` 추가
- [x] `api/src/main/resources-env/gs/application.yml` 수정 (예시 1건) — `ai.server.base-url: http://gs-ai:8000` · DB 접속 정보 · `spring.profiles.active: scada-modbus` 등 지자체별 오버라이드 추가 (`multi-tenant.md §2` 형식)

### Phase 3: 스케줄러 3건 (scheduler 모듈)

- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpPartitionScheduler.java` 생성 — `@Scheduled(cron="0 0 0 1 * ?")` 매월 1일 자정. 3개 파티션 테이블 다음 6개월치 파티션 자동 생성. `JdbcTemplate` 으로 `CREATE TABLE IF NOT EXISTS pump_ctrl_h_{YYYYMM} PARTITION OF pump_ctrl_h FOR VALUES FROM ... TO ...` 실행
- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpPartitionDropScheduler.java` 생성 — `@Scheduled(cron="0 0 0 * * ?")` 매일 자정. 보존 기간(`pump_ctrl_h` 2년 · `pump_predc_h` 3년 · `ai_drvn_mod_h` 5년) 초과 파티션 `DROP TABLE IF EXISTS` 실행
- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/pump/AiModeTransitionScheduler.java` 생성 — `@Scheduled(fixedDelay=60_000)` 매 1분. `SchedulerAiDrvnModeRepository.findAll()` 순회 후 SCADA 5분 초과·만료 복구 평가. 모듈 경계 보존을 위해 `AiDrvnMode.forceSystemMode()` 도메인 메서드 + `AiDrvnModeHistory.create()` 직접 호출 (api 모듈의 `AiModeService.forceTransition` 과 의미 동일). 부수적으로 `scheduler/src/main/java/com/mo/swtp/scheduler/pump/SchedulerAiDrvnModeRepository.java` · `SchedulerAiDrvnModeHistoryRepository.java` 도 추가
- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpSchedulerConfig.java` 생성 — `@Configuration` 마커 (모듈 전역 `@EnableScheduling` 은 `SchedulerApplication` 기존 활성화)

### Phase 4: 단위 테스트 — Service 계층 (api 모듈)

- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpControlServiceTest.java` 생성 — Mockito 단위 테스트. 6 케이스: 정상 흐름 / 인터록 위반 / SCADA 송신 실패(forceTransition + SCADA_OUTBOUND_FAILED 재던지기) / 모드 검증 위반(MANUAL_INPUT_DENIED_IN_AUTO_MODE) / 펌프 조합 미존재(PUMP_CMBN_NOT_FOUND) / 펌프 마스터 미존재(PUMP_NOT_FOUND)
- [x] `api/src/test/java/com/mo/swtp/ai/service/AiModeServiceTest.java` 생성 — Mockito. 11 케이스: `changeUserIntent` 호출 시 `ai_mode_cd` 불변 + `ai_drvn_mod_h` USER_SELECT 기록 / `forceTransition` 호출 시 `ai_drvn_mod` 불변 + `OUTBOUND_FAIL` 기록 / 동일 상태는 no-op / `assertOperationAllowed` 모드별 5 케이스 / AI_MODE_NOT_FOUND
- [x] `api/src/test/java/com/mo/swtp/ai/service/AiPredictionServiceTest.java` 생성 — 3 케이스: 정수조 미존재(PWTF_NOT_FOUND) / 추천 없음 응답(AI_PREDICTION_FAILED) / 정상 top-1 저장 흐름. CircuitBreaker fallback 동작은 `AiServerClient` 단위 책임
- [x] `api/src/test/java/com/mo/swtp/pump/service/InterlockValidatorTest.java` 생성 — 7 케이스: 규칙 미등록 통과 / 최소값 미만 위반 / 최대값 초과 위반 / 범위 내 통과 / 현재값 미수신 stub 통과 / 다중 규칙 위반 / 캐시 없이 매번 재조회
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpMasterCacheServiceTest.java` 생성 — 4 케이스: `existsByPumpId` 위임 / 미등록 false / `deactivatePump` 정상 / 없는 펌프 PUMP_NOT_FOUND

### Phase 5: 단위 테스트 — Scheduler (scheduler 모듈)

- [x] `scheduler/src/test/java/com/mo/swtp/scheduler/pump/AiModeTransitionSchedulerTest.java` 생성 — Mockito 7 케이스: SCADA 5분 초과 + AI_AUTO → SEMI_AUTO 전환(SCADA_TIMEOUT) / 5분 초과 + 비-AI_AUTO 미전환 / 5분 이내 미전환 / 만료 + AI 의도 → AI_AUTO 복구(MANUAL_EXPIRE) / 만료 + AI_ANLS 의도 → SEMI_AUTO 복구 / 만료 미경과 미복구 / 빈 데이터 무동작
- [x] `scheduler/src/test/java/com/mo/swtp/scheduler/pump/PumpPartitionSchedulerTest.java` 생성 — `JdbcTemplate` mock 5 케이스: 18회 SQL 실행 / SQL 형식(CREATE TABLE IF NOT EXISTS PARTITION OF) / yyyyMM 접미사 / 일부 실패 시 계속 실행 / sanity

### Phase 6: 도메인 시나리오 테스트 의무 2종 (`test-strategy.md §5.2`)

- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpInterlockScenarioTest.java` 생성 — 7 케이스(5 의무 + 2 보강): PRI 흡입압력 미달 차단 / FRI 유량 초과 차단 / LEI 수위 미달 차단 / 모든 조건 만족 통과 / 장애 복구 후 재검사 통과 / 장애 복구 후 위반 유지 차단 / 검사 스킵 절대 금지(매번 Repository 재호출 verify)
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpOperationModeScenarioTest.java` 생성 — 9 케이스(4 의무 + 보강): AI_AUTO 수동 주파수 거부 / AI_AUTO 수동 펌프 제어 거부 / MANUAL 강제 전환 시 의도 보존·이력 기록 / SEMI_AUTO 조합 선택 허용 / SEMI_AUTO 주파수 차단 / SEMI_AUTO 단일 펌프 차단 / SCADA 아웃바운드 장애 시 MANUAL 강제 전환(PumpControlService wiring) / MANUAL 모드에서 AI 모드 변경 허용 / AI_AUTO 모드에서 AI 모드 변경 허용

### Phase 7: 통합 테스트 1건 (api 모듈, `@SpringBootTest(NONE)`)

- [x] `api/src/test/java/com/mo/swtp/pump/PumpControlIntegrationTest.java` 생성 — `@SpringBootTest(webEnvironment=NONE) @ActiveProfiles("test") @Transactional`. 핵심 시나리오 1건: 펌프·정수조·조합 시드 INSERT → AI 모드 USER_SELECT 변경 → 자동 제어 실행 → SUCCESS 결과 검증. `@MockitoBean AiServerClient` 로 외부 HTTP 격리. CI 회피용 `@EnabledIfEnvironmentVariable("SWTP_INTEGRATION_DB"="true")` 가드 — 로컬 PostgreSQL + 파티션 사전 생성 환경에서만 활성화
- [x] `api/src/test/resources/application-test.yml` 수정 — `spring.profiles.active: test,scada-noop` 추가하여 `LoggingNoOpScadaAdapter` 자동 활성화 의도 명시

### Phase 8: 빌드·검증

- [x] `./gradlew.bat :common:test` 실행 성공 확인 — PumpTest 6 + AiDrvnModeTest 7 = 13 케이스 모두 통과 (TASK1-1 산출물)
- [x] `./gradlew.bat :api:test` 실행 성공 확인 — 신규 47 케이스(AiModeServiceTest 11 + AiPredictionServiceTest 3 + InterlockValidatorTest 7 + PumpControlServiceTest 6 + PumpInterlockScenarioTest 7 + PumpMasterCacheServiceTest 4 + PumpOperationModeScenarioTest 9) 통과 + PumpControlIntegrationTest 1 skip(환경 변수 가드)
- [x] `./gradlew.bat :scheduler:test` 실행 성공 확인 — AiModeTransitionSchedulerTest 7 + PumpPartitionSchedulerTest 5 = 12 케이스 모두 통과
- [x] `./gradlew.bat clean build` 실행 성공 확인 — QClass 재생성 + 전체 빌드 BUILD SUCCESSFUL
- [ ] `./gradlew.bat :api:bootRun` 로컬 실행 성공 확인 — 운영 환경에서 수동 검증 (CI 자동 검증 외 — 환경 의존)

### Phase 9: 운영 체크리스트 (TASK 산출물)

- [x] `docs/results/20260425/pumpcontrol/OPS_CHECKLIST.md` 생성 — 파티션 스케줄러 장애 시 수동 복구 SQL (PLAN §4) · 파티션 누락 감지 쿼리 · p6spy 슬로우 쿼리 모니터링 · AI 서버 fallback 동작 확인 · SCADA 5분 초과 강제 전환 검증 · 캐시 stale 방지 검증 · 운영 체크 주기 표 등 7개 절

## 산출물
- [결과](../../../results/20260425/pumpcontrol/RESULT1.md)
