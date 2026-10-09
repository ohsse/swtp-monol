---
status: completed
created: 2026-04-25
updated: 2026-04-25
---
# 송수펌프 제어 — 애플리케이션 계층 (api 모듈)

## 관련 계획
- [계획안](../../../plan/20260425/pumpcontrol/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 데이터 계층 (common 모듈)](TASK1-1.md)
- [TASK1-2 애플리케이션 계층 (현재 파일)](TASK1-2.md)
- [TASK1-3 인프라·검증 (scheduler·테스트·빌드)](TASK1-3.md)

## Phase

### Phase 1: ErrorCode enum 3건 (api 모듈, `httpStatus(int)` 만 허용)

- [x] `api/src/main/java/com/mo/swtp/pump/exception/PumpErrorCode.java` 생성 — `PUMP_NOT_FOUND(404)`, `PWTF_NOT_FOUND(404)`, `DWT_NOT_FOUND(404)`, `PUMP_CMBN_NOT_FOUND(404)`, `INVALID_PUMP_STATE(400)`, `INTERLOCK_VIOLATION(400)`, `MANUAL_INPUT_DENIED_IN_AUTO_MODE(400)`, `OPERATION_NOT_ALLOWED_IN_SEMI_AUTO(400)`. **`String message` 필드 절대 금지** (`check-errorcode-contract.sh` 훅 차단)
- [x] `api/src/main/java/com/mo/swtp/ai/exception/AiErrorCode.java` 생성 — `AI_PREDICTION_FAILED(503)`, `AI_SERVER_TIMEOUT(504)`, `INVALID_AI_MODE(400)`, `AI_MODE_NOT_FOUND(404)`
- [x] `api/src/main/java/com/mo/swtp/scada/exception/ScadaErrorCode.java` 생성 — `SCADA_OUTBOUND_FAILED(503)`, `CIRCUIT_BREAKER_OPEN(503)`, `SCADA_TIMEOUT(504)`

### Phase 2: Repository 9건 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpRepository.java` 생성 — `JpaRepository<Pump, String>` + `existsByPwtfId(String)` 메서드 (대시보드 조회용)
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PurifiedWaterTankRepository.java` 생성 — `JpaRepository<PurifiedWaterTank, String>`
- [x] `api/src/main/java/com/mo/swtp/pump/repository/DistributionWaterTankRepository.java` 생성 — `JpaRepository<DistributionWaterTank, String>`
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpCmbnRepository.java` 생성 — `JpaRepository<PumpCmbn, String>` + `findByPwtfId(String)` (정수조별 조합 조회)
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpControlHistoryRepository.java` 생성 — `JpaRepository<PumpControlHistory, PumpControlHistoryId>` + `PumpControlHistoryCustomRepository` 인터페이스
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpControlHistoryCustomRepository.java` + `PumpControlHistoryCustomRepositoryImpl.java` 생성 — Querydsl 기반 `findRecentByPumpId(pumpId, ctrlDtmFrom, ctrlDtmTo)` (대시보드 최근 이력)
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpPredictionResultRepository.java` 생성 — `JpaRepository<PumpPredictionResult, PumpPredictionResultId>` + `findLatestByPwtfId(String)` (대시보드 예측 조회)
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpInterlockRepository.java` 생성 — `JpaRepository<PumpInterlock, String>` + `findByPumpIdAndUseYn(String, YnType)`
- [x] `api/src/main/java/com/mo/swtp/ai/repository/AiDrvnModeRepository.java` 생성 — `JpaRepository<AiDrvnMode, String>` + `findAll()` (스케줄러용)
- [x] `api/src/main/java/com/mo/swtp/ai/repository/AiDrvnModeHistoryRepository.java` 생성 — `JpaRepository<AiDrvnModeHistory, AiDrvnModeHistoryId>`

### Phase 3: DTO (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpDashboardDto.java` 생성 — `pwtfId`·`pwtfNm`·`pumps[]`(펌프 운전 상태)·`latestPrediction`(최신 예측)·`recentControlHistory[]`. Swagger `@Schema` 명시
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpStateDto.java` 생성 — `pumpId`·`pumpNm`·`oprtngYn`·`currentFlwrt`·`currentElpwr` 등 대시보드 펌프 행
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpControlRequestDto.java` 생성 — `pwtfId`·`pumpCmbnCd`·`ctrlDiv`(`PumpControlDivision`)·`requestedFlwrt` 등. 검증 어노테이션
- [x] `api/src/main/java/com/mo/swtp/pump/dto/PumpControlResultDto.java` 생성 — `pumpCtrlId`·`ctrlDtm`·`ctrlRslt`(`PumpControlResult`)·`message`(엔티티 message 아닌 응답 보조)
- [x] `api/src/main/java/com/mo/swtp/ai/dto/AiModeUpsertDto.java` 생성 — `pwtfId`·`aiDrvnMod`(`AiDrvnModeType`)·`expireDtm`(NULL 허용). Swagger 명세
- [x] `api/src/main/java/com/mo/swtp/ai/dto/AiModeDto.java` 생성 — 조회 응답. `pwtfId`·`aiDrvnMod`·`aiModeCd`(`AiSystemModeCode`)·`expireDtm`·`lastRcvDtm`
- [x] `api/src/main/java/com/mo/swtp/ai/dto/AiPredictionRequestDto.java` 생성 — Python AI 서버 요청. `pwtfId`·`predcBaseDtm`
- [x] `api/src/main/java/com/mo/swtp/ai/dto/AiPredictionResponseDto.java` 생성 — Python AI 서버 응답. `recommendations[]`(조합·예측 전력·예측 유량 등)
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/dto/ControlCommandDto.java` 생성 — SCADA 송신 명령. `equipmentId`·`commandType`·`payload`

### Phase 4: SCADA Outbound 어댑터 (api 모듈, `@Profile` 분기)

- [x] `api/src/main/java/com/mo/swtp/scada/outbound/ScadaOutboundPort.java` 생성 — 인터페이스. `void send(ControlCommandDto)`, `Optional<String> query(String tag)` 등
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/ModbusTcpScadaAdapter.java` 생성 — `@Profile("scada-modbus")` 또는 지자체별 프로파일. Modbus TCP 송신 구현 (라이브러리 의존성은 본 작업 외 — stub 으로 IOException 던짐)
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/OpcUaScadaAdapter.java` 생성 — `@Profile("scada-opcua")`. OPC-UA Write 구현 stub
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/LoggingNoOpScadaAdapter.java` 생성 — `@Profile({"local","dev","test"})`. 실제 송신 없이 SLF4J 로그만 출력 (로컬 개발용 default)
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/ScadaOutboundConfig.java` 생성 — `@Configuration` + 회복성 설정. `@CircuitBreaker(name="scadaOutbound")` 등록 인터셉터

### Phase 5: AI Server Client + 회복성 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/ai/client/AiServerClient.java` 생성 — `RestClient` 주입. `requestPrediction(AiPredictionRequestDto)` 메서드. `@CircuitBreaker(name="aiPrediction", fallbackMethod="fallbackPrediction")` + `@Retry(name="aiPrediction")` 어노테이션. fallback 시 직전 예측 반환 또는 예외 던짐
- [x] `api/src/main/java/com/mo/swtp/ai/client/AiServerClientConfig.java` 생성 — `RestClient.Builder` 빈 등록. `baseUrl` 은 `@Value("${ai.server.base-url}")` 주입

### Phase 6: Service 계층 (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/pump/service/InterlockValidator.java` 생성 — PLAN §6 코드 그대로. Javadoc 에 "규칙 미등록 통과 vs 검사 스킵 절대 금지" 경계 명시 (PLAN §6 블로커 2 해소)
- [x] `api/src/main/java/com/mo/swtp/ai/service/AiPredictionService.java` 생성 — `AiServerClient.requestPrediction()` 호출 + 결과 매핑 + `pump_predc_h` 저장 (saveAll 배치 진입 시 `pwtf_id` 존재 검증 1회)
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/ScadaControlService.java` 생성 — `ScadaOutboundPort` 주입 (현재 active profile 의 어댑터 1개 자동 주입). `send(ControlCommandDto)` 위임. 인터록 검사는 호출자 책임
- [x] `api/src/main/java/com/mo/swtp/ai/service/AiModeService.java` 생성 — **변경 메서드 분리** (PLAN §3 명시):
  - `changeUserIntent(pwtfId, AiDrvnModeType)` — 사용자 API 만 호출. `ai_drvn_mod` 만 변경 + `ai_drvn_mod_h` 기록 (`reason=USER_SELECT`)
  - `forceTransition(pwtfId, AiSystemModeCode, TransitionReason)` — 스케줄러·SCADA 실패 catch 만 호출. `ai_mode_cd` 만 변경 + `ai_drvn_mod_h` 기록
  - `assertOperationAllowed(pwtfId, requestedOperation)` — PLAN §3.5 모드 별 허용 오퍼레이션 강제. 위반 시 `PumpErrorCode.MANUAL_INPUT_DENIED_IN_AUTO_MODE` 또는 `OPERATION_NOT_ALLOWED_IN_SEMI_AUTO`
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpDashboardService.java` 생성 — `getDashboard(pwtfId)` 메서드. 펌프 마스터 + 최근 제어 이력 + 최신 예측 + AI 모드 조회 후 `PumpDashboardDto` 조합
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpMasterCacheService.java` 생성 — `@Cacheable("pumpMasterExists")` 5분 TTL 의 `existsByPumpId(String)` 메서드. `deactivatePump(String)` 메서드에 `@CacheEvict(value="pumpMasterExists", key="#pumpId")` (DBA 권고 — PLAN §파티션 마스터 FK 금지 원칙)
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java` 생성 — **오케스트레이터** (PLAN §3 흐름 정확히 구현):
  1. `AiModeService.assertOperationAllowed()` 호출 (모드 검증)
  2. `InterlockValidator.validateOrThrow()` 호출
  3. `ai_mode_cd` 가 AUTO 면 `AiPredictionService.requestPrediction()` 호출
  4. `ScadaControlService.send()` 호출. **try/catch 로 `ScadaOutboundException` 캐치 → `AiModeService.forceTransition(MANUAL, OUTBOUND_FAIL)` 호출 + `ScadaErrorCode.SCADA_OUTBOUND_FAILED` 재던지기** (도메인 expert 블로커 1 해소)
  5. `PumpControlHistoryRepository.save()` (성공·실패 모두)

### Phase 7: Controller (api 모듈)

- [x] `api/src/main/java/com/mo/swtp/pump/web/PumpControlController.java` 생성 — Swagger `@Tag(name="06. 송수펌프 제어")` (정렬 번호는 추후 결정). 3 endpoints:
  - `GET /api/pump/dashboard?pwtfId={id}` → `PumpDashboardService.getDashboard()`
  - `PUT /api/pump/ai-mode` → `AiModeService.changeUserIntent()`
  - `POST /api/pump/auto-control` → `PumpControlService.executeControl()`
- [x] 각 endpoint 에 `@Operation` summary + `@ApiResponses` 6종 (200·400·401·403·404·500) 명시 (`api-patterns.md` Swagger 패턴)

### Phase 8: 빌드 검증 (Phase 단위)

- [x] `./gradlew.bat :api:compileJava` 실행하여 컴파일 통과 확인
- [x] `./gradlew.bat :api:test --tests "com.mo.swtp.pump.web.*Test"` 실행 (테스트는 TASK1-3 에서 작성)

## 산출물
- [결과](../../../results/20260425/pumpcontrol/RESULT1.md)
