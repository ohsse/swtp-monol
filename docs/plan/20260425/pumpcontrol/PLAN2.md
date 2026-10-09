---
status: approved
created: 2026-04-25
updated: 2026-04-25
---
# 송수펌프 제어 (FR-PMP-001) — 코드 리뷰 1차 블로커 해소 (PLAN2)

## 목적

REVIEW1 (status: draft) 의 블로커 (높음) 2건과 중간 4건을 해소한다. 본 사이클은 PLAN1 (status: approved) 이 도입한 송수펌프 제어 도메인의 동작을 보존하면서, **사용자 ID 추적성**과 **다중 펌프 부분 실패 시 트랜잭션 일관성**의 두 결함을 보강한다.

핵심 산출물:
- `AiModeService.changeUserIntent(...)` 시그니처에 `rgstrId` 매개변수 도입 — `ai_drvn_mod_h.rgstr_id` 에 실제 사용자 ID 기록 (블로커 I-1)
- `PumpControlService.executeControl(...)` 의 다중 펌프 흐름을 **사전 일괄 인터록 검사 → 펌프별 순차 SCADA 송신 → 부분 실패 시 보상 STOP + REQUIRES_NEW 이력 보존** 구조로 재구성 (블로커 I-2)
- `ScadaOutboundPort.sendStop(equipmentId)` 메서드 추가 — 보상 STOP 명령 인터페이스
- 중간 4건 (I-3·I-4·I-5·I-6) 동시 해소

## 배경

- [이전 리뷰](../../../reviews/20260425/pumpcontrol/REVIEW1.md) 블로커 2건 + 중간 4건 해소
- [직전 사이클 PLAN1](PLAN1.md) (status: approved) 의 도메인 설계·DB 스키마는 그대로 유지
- ANALYZE 재진입 스킵 — 직전 REVIEW 블로커 텍스트에 도메인 정합성 키워드(`용어`/`약어`/`중복 정의`/`네이밍 충돌`/`엔티티 통합`) 미포함 (`doc-harness.md` §ANALYZE 조건부 재진입)
- ANALYZE1 (`docs/analyze/20260422/pumpcontrol/ANALYZE1.md`, status: approved) 의 룰 갱신 지시서 17건은 PLAN1 사이클에서 모두 완료. 본 사이클에서 추가 룰 변경 없음

## 범위

### 포함

- **블로커 해소 2건**:
  - I-1 — `AiModeService.changeUserIntent` 가 `rgstrId` 매개변수를 받아 `ai_drvn_mod_h.rgstr_id` 에 기록. 컨트롤러는 `request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE)` 로 사용자 ID 추출하여 전달
  - I-2 — `PumpControlService.executeControl` 흐름 재구성: 사전 일괄 인터록 검사 + 펌프별 순차 SCADA 송신 + 부분 실패 시 보상 STOP 시퀀스 + `REQUIRES_NEW` 이력 보존
- **중간 해소 4건** (블로커 해소 코드 영역과 인접해 함께 처리):
  - I-3 — `AiModeTransitionScheduler` 의 `lastRcvDtm == null` 분기에 stub 정책 Javadoc·인라인 주석 추가
  - I-4 — `PumpPartitionDropScheduler` 가 보존 경계부터 12개월 이전까지 역순 순회하며 `DROP TABLE IF EXISTS` 실행 (스케줄러 누락 시 누적 위험 해소)
  - I-5 — `ScadaControlService` 클래스 레벨 `@Transactional(readOnly = true)` 제거
  - I-6 — `PumpOperationModeScenarioTest` 의 SCADA_TIMEOUT 의무 케이스를 `AiModeTransitionSchedulerTest` 로 위임함을 시나리오 테스트 클래스 Javadoc 에 명시
- 신규 단위 테스트 — `PumpControlServiceTest` 보강 (다중 펌프 흐름 4 케이스 추가) · `AiModeServiceTest` 시그니처 변경 반영 (기존 11 케이스 모두 갱신) · `PumpControllerTest` 신규 (사용자 ID 전달 검증, 본 사이클 추가) · `PumpPartitionDropSchedulerTest` 다중 파티션 DROP 케이스 보강

### 제외 (별도 작업)

- **보상 STOP 명령 자체 실패 시 알람 발행** — 본 사이클은 STOP 실패를 `log.error` + 운영 체크리스트 메모로 기록만 한다. 알람 4단계 도입은 후속 작업 `ot_integration_inbound` 범위
- **`scheduler` 모듈의 `AiDrvnModeHistory` 사용자 ID 일원화** — `AiModeTransitionScheduler` 는 모든 강제 전환이 시스템 발생이므로 `SYSTEM_ACTOR` 유지 (변경 없음). I-1 은 사용자 API 경로(`changeUserIntent`) 만 해당
- **`PumpControlService` 의 외부 호출 전체 트랜잭션 분리 리팩토링** — REVIEW1 의 개선 제안 1번. 본 작업은 다중 펌프 부분 실패 정책에 한정. 외부 호출 전체 트랜잭션 분리는 후속 ANALYZE 필요
- PLAN1 의 §제외 사항 모두 그대로 유지 (`ot_integration_inbound`·`dwt_pressure_history`·`tag_m`·Flyway·Testcontainers)

## 도메인 모델

**신규 엔티티·테이블·컬럼 없음.** 본 사이클은 메서드 시그니처와 흐름 변경에 한정한다.

### 시그니처 변경 1건

| 대상 | Before | After | 근거 |
|------|--------|-------|------|
| `AiModeService.changeUserIntent` | `changeUserIntent(AiModeUpsertDto dto)` | `changeUserIntent(AiModeUpsertDto dto, String rgstrId)` | 블로커 I-1 — `ai_drvn_mod_h.rgstr_id` 에 `"SYSTEM"` 하드코딩 대신 실제 사용자 ID 기록 |

### 신규 인터페이스 메서드 1건

| 대상 | 메서드 | 근거 |
|------|--------|------|
| `ScadaOutboundPort` | `void sendStop(String equipmentId)` | 블로커 I-2 보상 STOP 시퀀스용. 모든 어댑터(`LoggingNoOp`·`ModbusTcp`·`OpcUa`) 가 구현 |

## DB 설계 변경

**없음.** PLAN1 의 DDL 6건(`V1`~`V5` + README) 과 인덱스·파티션·시퀀스·FK 정책은 그대로 유지한다. 본 사이클은 애플리케이션 계층 변경에만 한정한다.

## 구현 방향

### 1. I-1 해소 — `changeUserIntent` 사용자 ID 전파

**현재 결함** (`AiModeService.java:62-66`):
```java
recordHistory(dto.getPwtfId(),
        prevIntent, dto.getAiDrvnMod(),
        prevSystem, prevSystem,
        TransitionReason.USER_SELECT,
        SYSTEM_ACTOR);  // ← "SYSTEM" 하드코딩
```

**수정 방향**:

```java
// AiModeService.java
@Transactional
public void changeUserIntent(AiModeUpsertDto dto, String rgstrId) {
    AiDrvnMode mode = findModeOrThrow(dto.getPwtfId());
    AiDrvnModeType prevIntent = mode.getAiDrvnMod();
    AiSystemModeCode prevSystem = mode.getAiModeCd();

    mode.changeUserIntent(dto.getAiDrvnMod(), dto.getExpireDtm());

    recordHistory(dto.getPwtfId(),
            prevIntent, dto.getAiDrvnMod(),
            prevSystem, prevSystem,
            TransitionReason.USER_SELECT,
            rgstrId);  // ← 매개변수로 전달받음
}
```

```java
// PumpControlController.java
@PutMapping("/ai-mode")
public ResponseEntity<CommonResponseDto<Void>> changeAiMode(
        @Valid @RequestBody AiModeUpsertDto dto,
        HttpServletRequest request) {
    String rgstrId = (String) request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE);
    if (rgstrId == null || rgstrId.isBlank()) {
        throw new RestApiException(AuthErrorCode.UNAUTHORIZED);
    }
    aiModeService.changeUserIntent(dto, rgstrId);
    return getResponseEntity();
}
```

**근거**:
- `JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE` 패턴은 `AuthController.java:74`·`ApiAuditorAware.java:37`·`RoleGuard.java:30` 에서 이미 사용 중인 표준 패턴
- `SecurityContextHolder` 미도입 프로젝트 — request attribute 패턴 일관성 유지
- `rgstrId` null 또는 빈 문자열 방어 — JWT 필터가 정상 동작하면 발생 불가하나 방어 코드로 `AuthErrorCode.UNAUTHORIZED` 던짐 (이미 정의되어 있는 ErrorCode 재사용)
- `forceTransition`·`AiModeTransitionScheduler` 의 `SYSTEM_ACTOR` 유지 — 시스템 주체 명령은 모두 `"SYSTEM"`

### 2. I-2 해소 — 다중 펌프 부분 실패 정책 (옵션 A + 보상 STOP)

**현재 결함** (`PumpControlService.java:97-103`):
```java
PumpControlHistory lastHistory = null;
for (String pumpId : pumpIds) {
    lastHistory = controlOnePump(pumpId, req, pumpCmbnCd, currentIntent);
}
return PumpControlResultDto.from(lastHistory);
```

다중 펌프 조합에서 첫 펌프 SCADA 송신 성공 후 두 번째 펌프 실패 시:
- 외부 트랜잭션 전체 롤백 → 첫 펌프 SUCCESS 이력 사라짐
- 첫 펌프 PLC 명령은 물리적으로 실행 → **물리/논리 불일치**
- `forceTransition` 도 같이 롤백되어 시스템 상태 보존 실패

**수정 방향** — 2-Phase 구조 도입:

```
executeControl(req):
    [Phase 0: 검증]
    1. assertOperationAllowed(pwtfId, op)
    2. determinePumpCmbnCd → pumpIds 도출 (비어 있으면 PUMP_CMBN_NOT_FOUND)
    3. AUTO 모드 시 AI 예측

    [Phase 1: Preflight — 사전 일괄 인터록 검사 (옵션 A)]
    4. 모든 pumpId 에 대해 마스터 존재 검증 (캐시 활용)
       1건이라도 미존재 → PUMP_NOT_FOUND 즉시 throw (SCADA 송신 자체 시작 안 함)
    5. 모든 pumpId 에 대해 InterlockValidator.validateOrThrow 호출
       1건이라도 위반 → INTERLOCK_VIOLATION 즉시 throw (SCADA 송신 자체 시작 안 함)

    [Phase 2: Execute — 펌프별 순차 SCADA 송신]
    6. List<String> succeeded = []
       try {
           for (pumpId : pumpIds) {
               scadaControlService.send(buildStartCommand(pumpId, ...))
               succeeded.add(pumpId)
           }
       } catch (ScadaOutboundException | UnsupportedOperationException e) {
           // 부분 실패 — 안전 정지 시퀀스
           failedPumpId = currentPumpId
           performSafeStop(succeeded)         // 보상 STOP, 각각 try/catch + log.error
           aiModeService.forceTransition(pwtfId, MANUAL, OUTBOUND_FAIL)
           recordHistoryNew(succeeded, SUCCESS, currentIntent)   // REQUIRES_NEW
           recordHistoryNew(failedPumpId, FAIL, currentIntent)   // REQUIRES_NEW
           throw new RestApiException(SCADA_OUTBOUND_FAILED)
       }

    [Phase 3: 성공 이력 일괄 저장]
    7. recordHistoryNew(succeeded, SUCCESS, currentIntent)        // REQUIRES_NEW
    8. return PumpControlResultDto.from(lastSuccessHistory)
```

**핵심 결정 사항**:

1. **옵션 A 채택 — 사전 일괄 인터록 검사**
   - 펌프 마스터 존재·인터록 위반은 SCADA 송신 시작 *전* 일괄 검증
   - 1건이라도 위반 시 SCADA 송신 자체를 시작하지 않음 → 부분 송신 가능성 원천 차단의 1차 방어선
   - 인터록 검사는 `validateOrThrow` 가 멱등이라 중복 호출 안전 (장애 복구 후 재검사 절대 금지 조항과 별개 — 본 흐름은 단일 호출 내 사전 일괄)

2. **보상 STOP 시퀀스 (옵션 B 일부 결합)** — `performSafeStop(List<String> succeeded)`
   - 이미 송신 완료된 펌프에 대해 `scadaOutboundPort.sendStop(pumpId)` 호출
   - 각 STOP 호출은 개별 try/catch — STOP 자체가 실패해도 다음 펌프로 계속 진행
   - STOP 실패는 `log.error` 로 기록 (현 단계는 알람 발행 미구현 — 알람 4단계는 `ot_integration_inbound` 후속 작업)
   - `ot-integration.md §5` 의 "진행 중 AI 자동 운전 세션은 안전 정지(Safe Stop) 시퀀스 실행 후 수동 모드 전환" 정책 직접 구현

3. **이력은 모두 `@Transactional(propagation = REQUIRES_NEW)` 별도 트랜잭션**
   - 외부 트랜잭션이 롤백되어도 `pump_ctrl_h` 이력은 보존
   - `AiDrvnModeHistory` 도 `forceTransition` 의 `@Transactional` 자체가 새 트랜잭션 경계가 되도록 `forceTransition` 메서드 시그니처는 그대로 두고 호출만 `try` 블록 외부에 배치
   - 부분 실패 시: succeeded 펌프 = SUCCESS 이력 + failedPumpId = FAIL 이력. 양쪽 모두 `REQUIRES_NEW` 로 보존

4. **사용자 응답** — 부분 실패 시 `RestApiException(SCADA_OUTBOUND_FAILED)` 던짐. 호출 측은 명령 자체가 부분적으로 실행되었다는 신호로 처리

**ScadaOutboundPort 인터페이스 확장**:

```java
public interface ScadaOutboundPort {
    void send(ControlCommandDto command);
    
    /**
     * 보상 STOP 명령. 다중 펌프 흐름에서 부분 실패 발생 시 이미 송신된 펌프를
     * 정지시키기 위한 안전 정지 시퀀스에서 호출된다 ({@code ot-integration.md §5}).
     *
     * <p>실패 시 {@link ScadaOutboundException} 을 던지며, 호출자는 각 펌프별로
     * try/catch 처리하여 STOP 실패가 다음 펌프 STOP 을 방해하지 않도록 한다.</p>
     *
     * @param equipmentId 정지 대상 펌프 ID
     * @throws ScadaOutboundException 송신 실패 시
     */
    void sendStop(String equipmentId);
    
    Optional<String> query(String tag);
}
```

**구현체 책임**:
- `LoggingNoOpScadaAdapter` — `log.info` 로 STOP 명령 기록 (테스트·로컬 동작)
- `ModbusTcpScadaAdapter` · `OpcUaScadaAdapter` — 현 단계는 stub 으로 `log.warn` + `UnsupportedOperationException` 던짐. 실제 PLC STOP 명령 매핑은 후속 작업 (`ot_integration_inbound` 또는 별도 작업) — Javadoc 에 stub 명시

### 3. I-3 해소 — `lastRcvDtm == null` 분기 stub 정책 주석

**파일**: `scheduler/src/main/java/com/mo/swtp/scheduler/pump/AiModeTransitionScheduler.java:78-98`

`applyScadaTimeoutTransition` 메서드 Javadoc 에 stub 정책 명시 + 인라인 주석 추가:

```java
/**
 * SCADA 5분 초과 중단 시 AI_AUTO → SEMI_AUTO 강제 전환.
 *
 * <p><b>stub 기간 정책 (블로커 I-3 해소 — 2026-04-25)</b>: 본 작업 시점은 SCADA 인바운드 어댑터
 * (별도 작업 {@code ot_integration_inbound}) 가 미구현 상태이므로 {@code last_rcv_dtm} 이
 * NULL 인 정수조가 정상 케이스다. 인바운드 어댑터 도입 후에는 NULL 을 "수신 이력 없음 = SCADA
 * 미연동" 으로 강제 전환 대상에 포함하도록 정책 변경 검토 필요. 현 구현은 NULL 시 판정 자체를
 * 스킵한다.</p>
 */
private void applyScadaTimeoutTransition(...) {
    ...
    LocalDateTime lastRcv = mode.getLastRcvDtm();
    if (lastRcv == null) {
        // stub 기간 정책: ot_integration_inbound 미구현 상태에서 NULL 정상 (Javadoc 참조).
        // TODO: ot_integration_inbound 진입 시 본 분기 재검토.
        return;
    }
    ...
}
```

코드 동작 변경 없음 — 문서화만 추가.

### 4. I-4 해소 — 다중 파티션 DROP 루프

**파일**: `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpPartitionDropScheduler.java:44-66`

**현재 결함**: `current.minusMonths(retentionMonths)` 로 1개 파티션만 DROP. 스케줄러 N일 중단 후 복구 시 N개 초과 파티션이 누적된다.

**수정 방향** — 보존 경계부터 12개월 이전까지 역순 순회:

```java
private static final int LOOKBACK_MONTHS = 12;

@Scheduled(cron = "${pumpcontrol.scheduler.partition-drop.cron:0 0 0 * * ?}")
public void dropExpiredPartitions() {
    YearMonth current = YearMonth.from(LocalDate.now());
    for (RetentionTarget t : TARGETS) {
        YearMonth boundary = current.minusMonths(t.retentionMonths);
        // 보존 경계부터 LOOKBACK_MONTHS 이전까지 역순 순회
        // 누락 발생해도 IF EXISTS 가 안전 가드
        for (int i = 0; i < LOOKBACK_MONTHS; i++) {
            dropIfExists(t, boundary.minusMonths(i));
        }
    }
}
```

- `DROP TABLE IF EXISTS` 는 멱등이라 N회 호출 안전
- LOOKBACK 기본 12개월 — 1년치 누적까지 한 번의 스케줄로 정리 가능
- 단위 테스트 — 12개월 누적 시뮬레이션 케이스 추가

### 5. I-5 해소 — `ScadaControlService` 클래스 레벨 `@Transactional` 제거

**파일**: `api/src/main/java/com/mo/swtp/scada/outbound/ScadaControlService.java:23`

**수정 방향**:

```java
@Slf4j
@Service
@RequiredArgsConstructor
// @Transactional(readOnly = true) 제거 — DB 작업이 없는 OT 어댑터 위임 서비스이므로
// DB 커넥션 풀 불필요 점유 방지
public class ScadaControlService {
    ...
}
```

`ScadaControlService` 는 `ScadaOutboundPort` 위임만 수행하며 DB 접근이 없다. `api/CLAUDE.md` 의 "Service 규칙 — 클래스 레벨에 읽기 전용 트랜잭션을 기본으로 선언" 은 DB 접근 서비스 한정이라는 본래 의도에 부합.

### 6. I-6 해소 — SCADA_TIMEOUT 의무 시나리오 책임 명확화

**파일**: `api/src/test/java/com/mo/swtp/pump/service/PumpOperationModeScenarioTest.java:159-172`

**수정 방향** — 클래스 Javadoc 에 책임 분담 명시:

```java
/**
 * 펌프 운전 모드 전환 의무 시나리오 테스트 ({@code test-strategy.md §5.2.3}).
 *
 * <p><b>SCADA_TIMEOUT 의무 케이스 책임 분담 (블로커 I-6 해소 — 2026-04-25)</b>:
 * {@code test-strategy.md §5.2.3} 의무 케이스 4번 "SCADA 5분 초과 중단 시 AI 자동 → 반자동 강제
 * 전환" 의 직접 검증 책임은 본 클래스가 아닌 {@code AiModeTransitionSchedulerTest} 가 가진다.
 * 이는 SCADA timeout 판정이 스케줄러의 시간 경계 평가 책임이고, 본 클래스는 사용자 API 흐름의
 * 모드 전이 시나리오에 집중하기 때문이다. 본 클래스에서는 {@code OUTBOUND_FAIL} 경로의
 * 강제 전환만 검증한다.</p>
 *
 * ...
 */
class PumpOperationModeScenarioTest { ... }
```

추가로 `test-strategy.md §5.2.3` 의무 케이스 4번에 책임 분담 메모 — 본 사이클은 룰 갱신 변경 없음으로 결정되었으므로 (배경 §3 참조), 메모는 시나리오 테스트 클래스 Javadoc 만 갱신한다. 룰 파일 변경 시 추가 ANALYZE 진입이 발생하므로 보수적 처리.

### 7. PLAN1 §3 흐름 그림 갱신 (코드 주석 only)

`PumpControlService` 클래스 Javadoc 의 §3 흐름 설명을 새 2-Phase 구조로 갱신. 외부 룰 갱신은 없음 — 본 코드 단독 변경.

## 테스트 전략

### 단위 테스트 보강

| 테스트 클래스 | 추가·갱신 케이스 |
|-------------|----------------|
| `AiModeServiceTest` (기존 11) | 시그니처 변경 반영 — 모든 `changeUserIntent` 호출에 `rgstrId` 매개변수 전달. `recordHistory` 호출 시 `rgstrId` 가 그대로 `AiDrvnModeHistory.create(...)` 에 전달되는지 `argument captor` 로 검증. 기존 11 케이스 모두 갱신. |
| `PumpControlServiceTest` (기존 6) | 신규 케이스 4건 — ① 단일 펌프 SCADA 실패 (기존 흐름 보존), ② 다중 펌프 사전 인터록 위반 시 SCADA 송신 자체 차단 검증, ③ 다중 펌프 송신 도중 두 번째 펌프 실패 시 첫 펌프에 STOP 호출 검증 + FAIL/SUCCESS 이력 모두 보존, ④ 다중 펌프 모두 성공 시 일괄 SUCCESS 이력 저장. |
| `PumpControllerTest` (신규) | 사용자 ID 추출 흐름 — `request.setAttribute(AUTH_SUBJECT_ATTRIBUTE, "user001")` 후 `changeAiMode` 호출 시 service 에 `"user001"` 전달 검증. AUTH_SUBJECT_ATTRIBUTE 미적재 시 `AuthErrorCode.UNAUTHORIZED` 검증. |
| `PumpPartitionDropSchedulerTest` (기존 5) | 신규 케이스 1건 — 12개월 누적 시뮬레이션 시 모든 파티션 `DROP TABLE IF EXISTS` 호출 검증 (JdbcTemplate mock + 호출 횟수 검증). |
| `AiModeTransitionSchedulerTest` (기존 7) | 변경 없음 — Javadoc·인라인 주석만 추가. |

### 도메인 시나리오 테스트

| 클래스 | 변경 |
|-------|------|
| `PumpInterlockScenarioTest` (기존 7) | 신규 케이스 1건 — 다중 펌프 조합 시 인터록 위반 펌프 1건 포함되면 SCADA 송신 자체 차단 검증 (옵션 A 정책 직접 검증) |
| `PumpOperationModeScenarioTest` (기존 9) | 변경 없음 — Javadoc 만 갱신 |

### 통합 테스트

- `PumpControlIntegrationTest` (기존 1, `SWTP_INTEGRATION_DB=true` 가드) — 변경 없음. 본 사이클은 단위 테스트 중심으로 보강

### 검증 명령

```bash
./gradlew.bat :common:test       # 변경 없음 확인 (regression)
./gradlew.bat :api:test          # 단위·시나리오 테스트 추가 반영
./gradlew.bat :scheduler:test    # 다중 파티션 DROP 케이스 추가
./gradlew.bat clean build        # QClass 재생성 + 전체 빌드
```

테스트 카운트 변화 (예상): 기존 73 케이스 → 78~80 케이스 (`PumpControllerTest` 2 + `PumpControlServiceTest` 4 + `PumpInterlockScenarioTest` 1 + `PumpPartitionDropSchedulerTest` 1).

## 제외 사항

- **보상 STOP 명령 자체 실패 시 알람 발행** — 본 사이클은 `log.error` 로 기록만. 알람 4단계 도입은 `ot_integration_inbound` 후속 작업
- **`PumpControlService` 외부 호출 전체 트랜잭션 분리 리팩토링** — REVIEW1 개선 제안 1번. 본 작업은 다중 펌프 부분 실패 정책에 한정
- **PLAN1 의 §제외 사항 모두 그대로 유지** (`ot_integration_inbound`·`dwt_pressure_history`·`tag_m`·Flyway·Testcontainers 등)

## 예상 산출물

- [태스크 2](../../../tasks/20260425/pumpcontrol/TASK2.md) — TASK 분할 여부는 `/dev:task` 단계에서 재결정. 본 사이클은 변경 범위가 작아 단일 TASK 가능성 높음 (Phase 5~7 / 체크박스 25~30 예상)
- [결과 2](../../../results/20260425/pumpcontrol/RESULT2.md) — 구현 완료 후 작성
- [리뷰 2](../../../reviews/20260425/pumpcontrol/REVIEW2.md) — 코드 리뷰 후 작성

## 부록: 도메인/DB 검토 결과

본 사이클은 **신규 엔티티·테이블·컬럼 변경 없음**, **DB 설계 변경 없음**, **룰 갱신 없음** 으로 도메인/DB 검토 게이트를 생략한다 (`/dev:plan` §검토 게이트 — "도메인 모델과 DB 변경이 모두 없는 경우 검토 게이트 생략").

PLAN1 의 wtp-domain-expert·wtp-dba-reviewer 검토 결과(블로커 0건, 권고 5건 모두 PLAN1 본문에 반영) 가 본 사이클에서도 그대로 유효하다.

본 사이클의 핵심 정책 결정 (옵션 A + 보상 STOP + REQUIRES_NEW 이력) 은 `ot-integration.md §5` 의 안전 정지 시퀀스 명시 정책 (CircuitBreaker OPEN 시 안전 정지 후 수동 모드 전환 + `pump_ctrl_h` 에 `ctrl_rslt='FAIL'` 기록) 의 다중 펌프 흐름 직접 적용이며, 도메인 expert 가 PLAN1 단계에서 이미 승인한 정책의 흐름 구현이다.
