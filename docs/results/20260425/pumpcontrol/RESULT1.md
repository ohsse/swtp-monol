---
status: completed
created: 2026-04-25
updated: 2026-04-25
---
# 송수펌프 제어 (FR-PMP-001) — 구현 결과

## 관련 작업
- [계획안](../../../plan/20260425/pumpcontrol/PLAN1.md)
- [태스크 1-1 데이터 계층](../../../tasks/20260425/pumpcontrol/TASK1-1.md)
- [태스크 1-2 애플리케이션 계층](../../../tasks/20260425/pumpcontrol/TASK1-2.md)
- [태스크 1-3 인프라·검증](../../../tasks/20260425/pumpcontrol/TASK1-3.md)
- [도메인 분석](../../../analyze/20260422/pumpcontrol/ANALYZE1.md)
- [운영 체크리스트](OPS_CHECKLIST.md)

## 작업 요약

송수펌프 제어 도메인을 최초 도입했다. 정수조(`pwtf`) → 송수펌프(`pump`) → 배수지(`dwt`) 의 물리적 흐름을 관리하는 비즈니스 도메인 3개(`com.mo.swtp.pump`·`com.mo.swtp.ai`·`com.mo.swtp.scada.outbound`) 를 신설하고, 마스터·상세·명세·시계열 파티션 엔티티 13건 + 사용자 API 3건 + 3-Service 오케스트레이터 + OT 아웃바운드 어댑터(`@Profile` 분기) + 시계열 파티션 운영 스케줄러 3건을 구현했다.

핵심 도입 사항:
- **AI 운전 모드 이중 체계**: 사용자 의도(`ai_drvn_mod`) 와 시스템 상태(`ai_mode_cd`) 를 분리하여 각각 변경 권한을 격리 (`AiDrvnMode.changeUserIntent` vs `forceSystemMode` 메서드 분리)
- **파티션 마스터 FK 금지 원칙** 준수: `pump_ctrl_h`·`pump_predc_h`·`ai_drvn_mod_h` 3개 시계열 파티션은 마스터 FK 미생성, 애플리케이션 레벨 검증으로 대체 (`PumpMasterCacheService` 5분 TTL Caffeine 캐시 + `@CacheEvict` stale 방지)
- **SCADA 5분 초과 강제 전환**: `AiModeTransitionScheduler` 매 1분 동작, 사용자 의도 보존하며 시스템 상태만 강제 전환 (`transition_reason='SCADA_TIMEOUT'`)
- **회복성 패턴**: AI 서버·SCADA 아웃바운드에 resilience4j CircuitBreaker + Retry 적용, fallback 시 `RestApiException` 으로 호출자가 안전 정지 시퀀스 트리거
- **인터록 stub**: 빈 테이블 허용 — 규칙 미등록 시 통과하되 검사 자체는 매번 호출 (장애 복구 후 재검사 절대 금지 조항 준수)

## TASK 규모

<!-- 분할 기준(Phase 10 / 체크박스 60) 대비 적정성 관찰용. 작성 시 표 그대로 유지 -->
| 항목 | 값 |
|------|----|
| Phase 수 | 7 (TASK1-1) + 8 (TASK1-2) + 9 (TASK1-3) = 26 |
| 체크박스 수 | 19 (TASK1-1) + 26 (TASK1-2) + 26 (TASK1-3) = 71 |
| 분할 여부 | Y |
| 분할 근거 | LARGE 작업 — Phase 26 / 체크박스 71 (분할 기준 Phase 10·체크박스 60 동시 초과). 데이터 계층(common 모듈 + DDL) · 애플리케이션 계층(api 모듈) · 인프라·검증(scheduler·테스트·빌드) 3계층이 명확히 구분되어 컨텍스트 분리에 적합. |

## 변경 사항

### 신규 비즈니스 도메인 패키지 3건
- `com.mo.swtp.pump` (api·common·scheduler 모듈에 분산)
- `com.mo.swtp.ai` (api·common 모듈)
- `com.mo.swtp.scada.outbound` (api 모듈)

### 신규 엔티티 13건 (common 모듈)

| 분류 | 엔티티 | 테이블 | 비고 |
|------|--------|--------|------|
| 마스터 | `Pump` | `pump_m` | 외부 PK + `Persistable<String>` |
| 마스터 | `PurifiedWaterTank` | `pwtf_m` | 외부 PK |
| 마스터 | `DistributionWaterTank` | `dwt_m` | 외부 PK |
| 마스터 | `PumpCmbn` | `pump_cmbn_m` | 외부 PK |
| 상세 | `PumpCmbnDetail` (+ `PumpCmbnDetailId`) | `pump_cmbn_d` | 복합 PK `(pump_cmbn_cd, pump_id)` |
| 명세 | `PumpInterlock` | `pump_interlock_p` | 외부 PK, stub |
| 명세 | `AiDrvnMode` | `ai_drvn_mod_p` | 외부 PK, 변경 메서드 분리 |
| 시계열 | `PumpControlHistory` (+ `PumpControlHistoryId`) | `pump_ctrl_h` | `seq_pump_ctrl_id` allocSize=100 |
| 시계열 | `PumpPredictionResult` (+ `PumpPredictionResultId`) | `pump_predc_h` | `seq_predc_id` allocSize=100 |
| 시계열 | `AiDrvnModeHistory` (+ `AiDrvnModeHistoryId`) | `ai_drvn_mod_h` | `seq_ai_drvn_mod_h_id` allocSize=10 |

### 신규 enum 5건 (common 모듈)
- `PumpControlDivision` (MANUAL/AUTO)
- `PumpControlResult` (SUCCESS/WAITING/FAIL)
- `AiDrvnModeType` (AI/AI_RECOMD/AI_ANLS — 사용자 의도)
- `AiSystemModeCode` (MANUAL/AI_AUTO/SEMI_AUTO — 시스템 상태)
- `TransitionReason` (USER_SELECT/SCADA_TIMEOUT/MANUAL_EXPIRE/OUTBOUND_FAIL)

### 신규 ErrorCode enum 3건 (api 모듈)
- `PumpErrorCode` (404·400·400·400·400·400·400·404·404 — 9 코드)
- `AiErrorCode` (503·504·400·404 — 4 코드)
- `ScadaErrorCode` (503·503·504 — 3 코드)

### 신규 Repository 10건 (api 모듈) + scheduler 자체 Repository 2건
- api: `PumpRepository`·`PurifiedWaterTankRepository`·`DistributionWaterTankRepository`·`PumpCmbnRepository`·`PumpCmbnDetailRepository`·`PumpControlHistoryRepository`(+Custom·Impl)·`PumpPredictionResultRepository`·`PumpInterlockRepository`·`AiDrvnModeRepository`·`AiDrvnModeHistoryRepository`
- scheduler: `SchedulerAiDrvnModeRepository`·`SchedulerAiDrvnModeHistoryRepository` (모듈 경계 보존)

### 신규 DTO 9건 (api 모듈)
`PumpDashboardDto`·`PumpStateDto`·`PumpControlRequestDto`·`PumpControlResultDto`·`AiModeUpsertDto`·`AiModeDto`·`AiPredictionRequestDto`·`AiPredictionResponseDto`·`ControlCommandDto`

### 신규 Service·컴포넌트 (api 모듈)
- `InterlockValidator` (PLAN §6 stub + Javadoc 4개 설계 경계 명시)
- `AiPredictionService` (RestClient + 마스터 검증)
- `AiModeService` (PLAN §3 변경 메서드 분리 — `changeUserIntent`/`forceTransition`/`assertOperationAllowed`)
- `PumpDashboardService`
- `PumpMasterCacheService` (`@Cacheable("pumpMasterExists")` 5분 TTL + `@CacheEvict`)
- `PumpControlService` (오케스트레이터)
- `ScadaControlService` + `ScadaOutboundPort` 인터페이스
- `ScadaOutboundException` (런타임 예외)
- 어댑터 3건: `LoggingNoOpScadaAdapter`(default/local/dev/test/scada-noop) · `ModbusTcpScadaAdapter`(scada-modbus) · `OpcUaScadaAdapter`(scada-opcua)
- `ScadaOutboundConfig`·`AiServerClientConfig`·`PumpCacheConfig`

### 신규 Controller (api 모듈)
- `PumpControlController` (`/api/pump/dashboard` GET + `/api/pump/ai-mode` PUT + `/api/pump/auto-control` POST), Swagger 6 응답 코드 명세

### 신규 클라이언트 (api 모듈)
- `AiServerClient` (`@CircuitBreaker(name="aiPrediction", fallbackMethod="fallbackPrediction")` + `@Retry(name="aiPrediction")`, fallback 시 `AI_PREDICTION_FAILED`)

### 신규 스케줄러 4건 (scheduler 모듈)
- `PumpPartitionScheduler` — 매월 1일 자정, 3개 파티션 테이블 × 6개월 = 18 파티션 선행 생성 (`CREATE TABLE IF NOT EXISTS ... PARTITION OF`)
- `PumpPartitionDropScheduler` — 매일 자정, 보존 기간(2년/3년/5년) 초과 파티션 `DROP TABLE IF EXISTS`
- `AiModeTransitionScheduler` — 매 1분, SCADA 5분 초과 + 만료 복구 강제 전환
- `PumpSchedulerConfig` — 도메인 단위 설정 마커

### 신규 DDL 스크립트 6건 (common 모듈)
- `V1__pumpcontrol_master_tables.sql` — 마스터 7건 + FK 6건
- `V2__pumpcontrol_partition_tables.sql` — 파티션 3건 + FK 금지 주석 명시
- `V3__pumpcontrol_partition_initial_6months.sql` — 18개 초기 파티션
- `V4__pumpcontrol_indexes.sql` — B-Tree + BRIN 인덱스
- `V5__pumpcontrol_sequences.sql` — 3개 시퀀스 (allocSize 100/100/10)
- `README.md` — 적용 순서 + 운영 절차

### 설정 변경 (api 모듈)
- `application.yml` — `ai.server.base-url`·`scada.outbound.timeout-seconds`·`pumpcontrol.scada.timeout-minutes`·resilience4j 4 인스턴스(circuitbreaker × 2 + retry × 2)·Caffeine 캐시(`pumpMasterExists` 5분 TTL) 추가
- `resources-env/gs/application.yml` 신규 — 지자체 빌드 프로파일 예시 (`scada-modbus` 활성화)
- `application-test.yml` 보강 — `LoggingNoOpScadaAdapter` 자동 활성화 의도 주석
- `build.gradle` — resilience4j 3개 + Caffeine + spring-boot-starter-cache (TASK1-2 단계에서 선반영)

### 룰 갱신 (TASK1-1·1-2·1-3 전반)
- `dict/standard-words.md` — 27 단어 신규 등록 + 2 단어 폐기 이력
- `dict/standard-data-domains.md` — `DOM_TAG_NM_50`·`DOM_SEQ_BIGINT` 신규 등록
- `dict/standard-terms.md` — 26 용어 신규 등록 + 8 동의어 금지 패턴
- `domain-abbreviations.md` — `pump`/`ctrl` 마스터 승격, `pwtf`/`dwt`/`ai`/`tag`/`raw`/`alarm`/`diag`/`opt` 도입 예정 + `pmp`/`reg` 폐기 + 등록 거부 3건
- `db-partitioning-and-retention.md` — `pump_ctrl_h` 2년·`pump_predc_h`/`opt_result_h`/`pump_prdct_h` 3년·`ai_drvn_mod_h` 5년 보존 정책 추가
- `ot-integration.md` — AI 운전 모드 이중 체계·강제 전환 정책·`ai_drvn_mod_h` 기록 의무·아웃바운드 어댑터 도입 상태 갱신

## 테스트 결과

### 신규 테스트 (TASK1-3 Phase 4·5·6·7)

| 모듈 | 테스트 클래스 | 케이스 수 | 결과 |
|------|------------|---------|------|
| api | `AiModeServiceTest` | 11 | 통과 |
| api | `AiPredictionServiceTest` | 3 | 통과 |
| api | `InterlockValidatorTest` | 7 | 통과 |
| api | `PumpControlServiceTest` | 6 | 통과 |
| api | `PumpInterlockScenarioTest` (의무 시나리오) | 7 | 통과 |
| api | `PumpMasterCacheServiceTest` | 4 | 통과 |
| api | `PumpOperationModeScenarioTest` (의무 시나리오) | 9 | 통과 |
| api | `PumpControlIntegrationTest` | 1 | skip (`SWTP_INTEGRATION_DB=true` 가드) |
| common | `PumpTest` (TASK1-1) | 6 | 통과 |
| common | `AiDrvnModeTest` (TASK1-1) | 7 | 통과 |
| scheduler | `AiModeTransitionSchedulerTest` | 7 | 통과 |
| scheduler | `PumpPartitionSchedulerTest` | 5 | 통과 |
| **합계** | **12 클래스** | **73** | **72 통과 + 1 skip** |

### 빌드 검증 (TASK1-3 Phase 8)

| 명령 | 결과 |
|------|------|
| `./gradlew.bat :common:test` | BUILD SUCCESSFUL |
| `./gradlew.bat :api:test` | BUILD SUCCESSFUL (89 tests, 1 skipped) |
| `./gradlew.bat :scheduler:test` | BUILD SUCCESSFUL |
| `./gradlew.bat clean build` | BUILD SUCCESSFUL (전체 23 task 실행) |
| `./gradlew.bat :api:bootRun` | 환경 의존 — 운영 검증 (CI 제외) |

### 의존성 검증
`./gradlew.bat :api:dependencies --configuration runtimeClasspath` 실행하여 다음 의존성 정상 해결 확인:
- `resilience4j-spring-boot3` 2.2.0
- `resilience4j-circuitbreaker` 2.2.0
- `resilience4j-retry` 2.2.0
- `caffeine` 3.2.3
- `spring-boot-starter-cache` 4.0.5

## 비고

### 계획 대비 차이점

1. **scheduler 모듈의 `AiModeService.forceTransition` 호출 방식**: PLAN §3 흐름은 `AiModeService.forceTransition` 직접 호출을 명시했으나, scheduler 모듈은 `common` 모듈에만 의존하므로(`api` 모듈 미의존) 모듈 경계 보존을 위해 다음 방식으로 구현:
   - scheduler 자체 Repository 2건 정의 (`SchedulerAiDrvnModeRepository`·`SchedulerAiDrvnModeHistoryRepository`)
   - 도메인 메서드 `AiDrvnMode.forceSystemMode()` 직접 호출 + `AiDrvnModeHistory.create()` 직접 저장
   - 의미적으로 `AiModeService.forceTransition` 과 동일한 행위, 테스트도 동일 시나리오 검증
2. **`PumpControlIntegrationTest` CI 가드**: PLAN 은 "최소 1건만" 으로 통합 테스트 도입을 명시했고 환경 의존(로컬 PostgreSQL + 파티션 사전 생성) 을 인정했다. CI 자동화 회피를 위해 `@EnabledIfEnvironmentVariable(named="SWTP_INTEGRATION_DB", matches="true")` 가드를 추가했다. 로컬에서 환경 변수 설정 시 활성화된다.
3. **`application-test.yml` `spring.profiles.active` 미사용**: Spring Boot 4 의 `InvalidConfigDataPropertyException` 정책에 따라 외부 설정 파일에서 `spring.profiles.active` 사용 금지. `@ActiveProfiles("test")` 만 사용하며 `LoggingNoOpScadaAdapter` 의 `@Profile({"test", ...})` 매칭으로 자동 활성화된다.

### 후속 작업 (PLAN 제외 사항)

- **`ot_integration_inbound`**: SCADA 인바운드 어댑터·센서 품질 관리(GOOD/BAD/UNCERTAIN)·결측 대체값(Hold Last Value)·알람 4단계 체계(`alarm_h`)·실제 인터록 규칙 데이터 투입·`AlarmEscalationScenarioTest` 의 통합 격상
- **`dwt_pressure_history`**: 배수지 분기별 요구 압력 이력(`dwt_prsr_setn_h`)
- **`tag_m` 마스터 도입**: `pump_m.tag_se_cd` 컬럼 추가, `TagMeasurementType` Java enum 위치 결정 (`com.mo.swtp.tag` vs `common`)
- **Flyway 마이그레이션 도구 도입**: 본 작업은 JPA `ddl-auto=none/update` + 수동 SQL 스크립트 사용. 향후 ANALYZE 단계에서 도입 검토
- **Testcontainers·E2E 도입**: `test-strategy-e2e-roadmap.md` 별도 ANALYZE 필요. 현재 통합 테스트 1건은 로컬 PostgreSQL 의존
- **`ai_drvn_mod_h` BRIN 인덱스**: 5년 누적 후 파티션당 행 증가 시 `rgstr_dtm` 단독 BRIN 추가 검토 (DBA 후속 판단 메모)

### 운영 자료
[운영 체크리스트](OPS_CHECKLIST.md) 작성 완료 — 파티션 스케줄러 장애 시 수동 복구 SQL · 슬로우 쿼리 모니터링 · AI 서버 fallback 동작 확인 · SCADA 5분 초과 강제 전환 검증 · 캐시 stale 방지 검증 · 운영 체크 주기 표 (7개 절).

### 아키텍처 의의

- **3-Service 오케스트레이터 패턴** 도입으로 `PumpControlService` 가 `InterlockValidator → AiPredictionService → ScadaControlService → PumpControlHistoryRepository` 순으로 명확한 단계 호출. 각 단계의 단위 테스트가 독립적으로 작성 가능
- **`@Profile` 분기 어댑터 패턴** (`multi-tenant.md §4` 전략 2) 의 OT 영역 최초 적용 — 한국 지자체 PLC 환경(LS Electric Modbus / Siemens S7 OPC-UA) 혼재 대응
- **사용자 의도 vs 시스템 상태 분리** — 변경 권한을 도메인 메서드 수준에서 격리(`changeUserIntent` vs `forceSystemMode`) 하여 침범 방지. SCADA 장애 복구 시 자동 환원 가능한 설계
- **시계열 파티션 + 마스터 FK 금지** 원칙을 신규 도메인에 일관 적용. 캐시 + `@CacheEvict` 로 stale 방지 보강
