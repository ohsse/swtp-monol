---
status: completed
created: 2026-04-25
updated: 2026-04-25
---
# 송수펌프 제어 (FR-PMP-001) — 코드 리뷰 1차 블로커 해소 (RESULT2)

## 관련 작업
- [계획안 PLAN2](../../../plan/20260425/pumpcontrol/PLAN2.md)
- [태스크 TASK2](../../../tasks/20260425/pumpcontrol/TASK2.md) (단일 — 분할 미사용)
- [이전 사이클 PLAN1](../../../plan/20260425/pumpcontrol/PLAN1.md)
- [이전 사이클 RESULT1](RESULT1.md)
- [이전 리뷰 REVIEW1](../../../reviews/20260425/pumpcontrol/REVIEW1.md)
- [도메인 분석 ANALYZE1](../../../analyze/20260422/pumpcontrol/ANALYZE1.md)
- [운영 체크리스트](OPS_CHECKLIST.md) (PLAN1 사이클에서 작성, 본 사이클에서 변경 없음)

## 작업 요약

REVIEW1 의 블로커 (높음) 2건과 중간 4건을 해소했다.

핵심 도입:
- **사용자 ID 추적성** (블로커 I-1): `AiModeService.changeUserIntent` 시그니처를 `(dto, rgstrId)` 로 변경, 컨트롤러는 `request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE)` 로 사용자 ID 추출 후 전달. `ai_drvn_mod_h.rgstr_id` 가 더 이상 `"SYSTEM"` 하드코딩되지 않으며 운영 감사 추적이 가능해졌다. 시스템 강제 전환 경로(`forceTransition`·`AiModeTransitionScheduler`) 의 `"SYSTEM"` 사용은 회귀 방지 케이스로 검증.
- **다중 펌프 부분 실패 정책** (블로커 I-2): `PumpControlService.executeControl` 을 2-Phase 구조로 재구성. Phase 1 (Preflight) 에서 모든 펌프의 마스터 존재·인터록을 일괄 검사하여 1건이라도 위반 시 SCADA 송신 자체를 시작하지 않는다 (옵션 A — 1차 차단점). Phase 2 (Execute) 의 부분 실패 시 보상 STOP 시퀀스(`scadaControlService.sendStop`) + `forceTransitionInNewTransaction(MANUAL, OUTBOUND_FAIL)` + SUCCESS·FAIL 이력 모두 `PumpControlHistoryWriter.saveNew` (REQUIRES_NEW) 로 보존 후 `RestApiException(SCADA_OUTBOUND_FAILED)` 던짐. 외부 트랜잭션이 롤백되어도 시스템 상태(`ai_drvn_mod_p`/`ai_drvn_mod_h`) 와 제어 이력은 보존된다.
- **REQUIRES_NEW 자가 호출 한계 회피**: Spring AOP 의 self-invocation 한계로 동일 클래스 내 `@Transactional(REQUIRES_NEW)` 메서드 호출이 동작하지 않는 점을 별도 컴포넌트로 해결 — `PumpControlHistoryWriter` 신규 컴포넌트 + `AiModeService.forceTransitionInNewTransaction` 메서드 추가.
- **중간 사항 4건 (I-3·I-4·I-5·I-6)**: stub 정책 Javadoc · 12개월 LOOKBACK 다중 파티션 DROP · 불필요 `@Transactional` 제거 · 시나리오 테스트 책임 분담 명시 — 모두 함께 처리.

## TASK 규모

<!-- 분할 기준(Phase 10 / 체크박스 60) 대비 적정성 관찰용. 작성 시 표 그대로 유지 -->
| 항목 | 값 |
|------|----|
| Phase 수 | 5 |
| 체크박스 수 | 35 |
| 분할 여부 | N |
| 분할 근거 | — (단일 TASK 적정. PLAN2 가 블로커 해소 위주이고 변경 범위가 PLAN1 의 1/8 수준이라 분할 기준 Phase 10·체크박스 60 모두 미달) |

## 변경 사항

### 신규 파일 (3건)

| 파일 | 역할 |
|------|------|
| `api/src/main/java/com/mo/swtp/pump/service/PumpControlHistoryWriter.java` | `@Transactional(REQUIRES_NEW)` 로 `pump_ctrl_h` 이력을 별도 트랜잭션 보존하는 헬퍼 컴포넌트 — `PumpControlService` 의 self-invocation 한계 회피 |
| `api/src/test/java/com/mo/swtp/pump/web/PumpControlControllerTest.java` | 컨트롤러 단위 테스트 신규 — `AUTH_SUBJECT_ATTRIBUTE` 추출 흐름 + null/blank 방어 검증 (3 케이스) |
| `scheduler/src/test/java/com/mo/swtp/scheduler/pump/PumpPartitionDropSchedulerTest.java` | 스케줄러 단위 테스트 신규 — 3 테이블 × 12개월 = 36회 DROP 호출 검증 + 각 테이블 보존 경계 + LOOKBACK 누적 시뮬레이션 + 일부 실패 시 진행 (5 케이스) |

### 수정 파일 — 운영 코드 (10건)

| 파일 | 변경 내용 |
|------|---------|
| `api/src/main/java/com/mo/swtp/auth/exception/AuthErrorCode.java` | `UNAUTHORIZED(401)` 신규 enum 값 추가 — 컨트롤러 방어 코드용. PLAN2 가 재사용 가정했으나 실제 미존재여서 신규 도입 (계획 대비 차이점 — §비고 참조) |
| `api/src/main/java/com/mo/swtp/ai/service/AiModeService.java` | (1) `changeUserIntent(AiModeUpsertDto dto)` → `changeUserIntent(AiModeUpsertDto dto, String rgstrId)` 시그니처 변경, `recordHistory` 의 `SYSTEM_ACTOR` 자리에 `rgstrId` 전달. (2) `forceTransitionInNewTransaction(pwtfId, newSystem, reason)` 메서드 신규 추가 — `@Transactional(propagation = REQUIRES_NEW)`, 보상 흐름 전용. 기존 `forceTransition` 은 그대로 유지 (스케줄러용). 두 메서드 모두 내부 공통 로직 `forceTransitionInternal` 위임 |
| `api/src/main/java/com/mo/swtp/pump/web/PumpControlController.java` | `changeAiMode(@Valid @RequestBody AiModeUpsertDto dto)` → `(dto, HttpServletRequest request)` 시그니처 변경. `request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE)` 로 `rgstrId` 추출, null/blank 시 `RestApiException(AuthErrorCode.UNAUTHORIZED)` 던짐 |
| `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java` | 2-Phase 구조 전면 재작성 — Phase 0(검증·예측) → Phase 1(`preflightAllPumps` 일괄 마스터·인터록 검사 + `LinkedHashMap<String, ControlCommandDto>` 반환) → Phase 2(`executeAllPumps` 펌프별 순차 송신, 부분 실패 시 `performSafeStop` + `forceTransitionInNewTransaction` + `historyWriter.saveNew` SUCCESS·FAIL 양쪽 보존 + `SCADA_OUTBOUND_FAILED` throw) → 정상 시 `saveAllSuccessHistory` 외부 트랜잭션 일괄 저장. 클래스 Javadoc 에 새 흐름과 `ot-integration.md §5` 도메인 규칙 준수 항목 명시. 기존 `controlOnePump` 메서드 제거. `PumpControlHistoryWriter` 의존성 추가 |
| `api/src/main/java/com/mo/swtp/scada/outbound/ScadaOutboundPort.java` | `void sendStop(String equipmentId)` 인터페이스 메서드 추가 — Javadoc 에 보상 STOP 용도·stub 정책·예외 계약 명시 |
| `api/src/main/java/com/mo/swtp/scada/outbound/LoggingNoOpScadaAdapter.java` | `sendStop` 구현 — `log.info` 시뮬레이션 |
| `api/src/main/java/com/mo/swtp/scada/outbound/ModbusTcpScadaAdapter.java` | `sendStop` stub 구현 — `log.warn` + `UnsupportedOperationException` 던짐, Javadoc 에 stub 명시 |
| `api/src/main/java/com/mo/swtp/scada/outbound/OpcUaScadaAdapter.java` | `sendStop` stub 구현 — `log.warn` + `UnsupportedOperationException` 던짐, Javadoc 에 stub 명시 |
| `api/src/main/java/com/mo/swtp/scada/outbound/ScadaControlService.java` | (1) 클래스 레벨 `@Transactional(readOnly = true)` 어노테이션 제거 (I-5), `import` 정리. (2) `sendStop(String equipmentId)` 위임 메서드 신규 추가 — `PumpControlService` 의 보상 STOP 호출용. 클래스 Javadoc 에 트랜잭션 미사용 사유 1단락 추가 |
| `scheduler/src/main/java/com/mo/swtp/scheduler/pump/AiModeTransitionScheduler.java` | `applyScadaTimeoutTransition` 메서드 Javadoc 에 stub 정책 4줄 명시 (`ot_integration_inbound` 미구현 상태 / 인바운드 도입 후 정책 변경 검토). `lastRcv == null` 분기에 인라인 주석 + TODO 추가 (I-3) |
| `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpPartitionDropScheduler.java` | `LOOKBACK_MONTHS = 12` 상수 추가, `dropExpiredPartitions` 에 보존 경계부터 12개월 이전까지 역순 순회 내부 루프 추가 — `IF EXISTS` 멱등 가드. 클래스 Javadoc 에 LOOKBACK 정책 1단락 명시 (I-4) |

### 수정 파일 — 테스트 (5건)

| 파일 | 변경 내용 |
|------|---------|
| `api/src/test/java/com/mo/swtp/ai/service/AiModeServiceTest.java` | `changeUserIntent` 호출 3건 모두 시그니처 변경 반영 (`"test-user"` 전달). 신규 케이스 2건 추가 — `changeUserIntent_호출_시_history_의_rgstr_id_가_사용자_id_로_기록된다` (블로커 I-1 직접 검증) + `forceTransition_호출_시_history_의_rgstr_id_는_SYSTEM_으로_기록된다` (시스템 경로 회귀 방지). 기존 11 → 13 케이스 |
| `api/src/test/java/com/mo/swtp/pump/service/PumpControlServiceTest.java` | (1) `@Mock PumpControlHistoryWriter historyWriter` 추가, `@InjectMocks` 자동 주입. (2) `자동_제어_정상_흐름` 케이스에 보상 흐름 0회 호출 검증 추가. (3) 기존 `SCADA_송신_실패` 케이스를 단일 펌프 케이스로 명확화하고 `forceTransitionInNewTransaction` + `historyWriter.saveNew(FAIL)` 검증으로 갱신. (4) 신규 케이스 3건 — 다중펌프 사전인터록 위반 / 다중펌프 송신중 부분실패 + 보상 STOP + 양쪽 이력 REQUIRES_NEW / 다중펌프 모두 성공. 기존 6 → 9 케이스 |
| `api/src/test/java/com/mo/swtp/pump/service/PumpInterlockScenarioTest.java` | 클래스 Javadoc 에 옵션 A 다중 펌프 정책 검증 책임 분담 1단락 추가 — `PumpControlService` 흐름 직접 검증은 `PumpControlServiceTest#다중펌프_사전인터록_위반_시_SCADA_송신_자체가_시작되지_않는다` 가 책임지고, 본 클래스는 InterlockValidator 단독 검증에 집중 (시나리오 테스트의 책임 분리) |
| `api/src/test/java/com/mo/swtp/pump/service/PumpOperationModeScenarioTest.java` | (1) 클래스 Javadoc 에 SCADA_TIMEOUT 책임 분담 명시 — `AiModeTransitionSchedulerTest` 가 직접 검증 책임 (블로커 I-6). (2) `@Mock PumpControlHistoryWriter pumpControlHistoryWriter` 추가, `SCADA_아웃바운드_장애` 케이스의 `PumpControlService` 생성자 호출에 `pumpControlHistoryWriter` 매개변수 추가 (회귀 수정). (3) 동 케이스의 사용되지 않는 `pumpControlHistoryRepository.save` stub 제거 — 부분 실패 흐름은 `historyWriter` 만 사용 |
| `api/src/test/java/com/mo/swtp/pump/PumpControlIntegrationTest.java` | `aiModeService.changeUserIntent(modeDto)` → `aiModeService.changeUserIntent(modeDto, "integration-test-user")` 시그니처 갱신 (회귀 수정). 통합 테스트는 `SWTP_INTEGRATION_DB=true` 가드로 CI 자동화에서 skip 유지 |

### 변경 없음 — 룰 / DDL / 멀티테넌트 / 의존성

본 사이클은 **신규 엔티티·테이블·컬럼 0건**, **DB 설계 변경 0건**, **룰 갱신 0건**, **build.gradle 변경 0건**, **application.yml 변경 0건** 이다. PLAN1 사이클에서 도입한 DDL 6건·인덱스·시퀀스·FK 정책·resilience4j·Caffeine 캐시·`resources-env/gs/`·표준 사전 (단어·데이터 도메인·용어)·도메인 약어·`ot-integration.md §5` 등은 모두 그대로 유효하다.

## 테스트 결과

### 신규·갱신 테스트 (Phase 1·2·3·4)

| 모듈 | 테스트 클래스 | 케이스 수 변화 | 결과 |
|------|------------|--------------|------|
| api | `AiModeServiceTest` | 11 → 13 (+2) | 통과 |
| api | `PumpControlServiceTest` | 6 → 9 (+3, 단일 펌프 케이스 1건 갱신) | 통과 |
| api | `PumpControllerTest` | 0 → 3 (+3 신규) | 통과 |
| api | `PumpInterlockScenarioTest` | 7 → 7 (Javadoc 만 변경) | 통과 |
| api | `PumpOperationModeScenarioTest` | 9 → 9 (생성자 + Javadoc) | 통과 |
| api | `PumpControlIntegrationTest` | 1 → 1 (skip 유지) | skip (`SWTP_INTEGRATION_DB=true` 가드) |
| scheduler | `PumpPartitionDropSchedulerTest` | 0 → 5 (+5 신규) | 통과 |
| scheduler | `AiModeTransitionSchedulerTest` | 7 → 7 (변경 없음) | 통과 |
| **합계 (PLAN2 영향 영역)** | **신규/갱신 5 클래스** | **총 +13 케이스** | **통과** |

### 빌드 검증 (Phase 5)

| 명령 | 결과 | 시간 |
|------|------|------|
| `./gradlew.bat :common:test` | BUILD SUCCESSFUL (UP-TO-DATE — 변경 없음 회귀 검증) | 1s |
| `./gradlew.bat :api:test` | BUILD SUCCESSFUL | 23s |
| `./gradlew.bat :scheduler:test` | BUILD SUCCESSFUL | 8s |
| `./gradlew.bat clean build` | BUILD SUCCESSFUL (전체 23 task 실행, QClass 재생성 무영향) | 35s |

`common` 모듈에서 `EnumType.STRING` 관련 컴파일 경고 3건은 PLAN1 사이클부터 존재하던 알려진 경고이며 본 사이클과 무관하다.

## 비고

### 계획 대비 차이점

1. **`AuthErrorCode.UNAUTHORIZED(401)` 신규 도입**: PLAN2 §1 본문에서 "이미 정의되어 있는 ErrorCode 재사용" 으로 가정했으나 실제 `AuthErrorCode` enum 에는 `LOGIN_FAILED(401)` 와 `FORBIDDEN(403)` 만 있었다. `LOGIN_FAILED` 는 로그인 자격 증명 실패 의미라 부적합 → `UNAUTHORIZED(401)` 신규 도입. ErrorCode 신규 추가는 본 사이클의 핵심 변경(사용자 ID 방어 코드) 의 직접 결과이며 응답 계약 일관성 유지를 위한 필요 변경이다. 룰 갱신 영향 없음 (`exception-patterns.md` 의 `httpStatus(int)` 만 허용 정책 준수).

2. **REQUIRES_NEW 자가 호출 회피 — 별도 컴포넌트 옵션 채택**: PLAN2 §2 에서 "별도 컴포넌트 또는 AOP 가 동작하는 호출 경로 사용" 두 옵션 중 별도 컴포넌트 옵션을 채택하여 `PumpControlHistoryWriter` 신규 생성. `AiModeService` 에는 `forceTransitionInNewTransaction` 메서드를 신규 추가하여 외부 호출 경로(self-invocation 회피) 가 자연스럽게 보장되도록 했다. 두 가지 위치 모두 책임이 명확한 곳에 배치 — 이력 저장은 별도 컴포넌트, AiMode 강제 전환은 AiModeService 내부 메서드 분리.

3. **`PumpControlServiceTest` 단일 펌프 SCADA 실패 케이스 갱신 처리**: PLAN2 본문에서는 "+4 케이스 신규" 로 명시했으나 실제로는 기존 `SCADA_송신_실패_시_forceTransition_MANUAL_OUTBOUND_FAIL_호출_후_예외_재던지기` 케이스가 단일 펌프 흐름 검증의 의미를 그대로 가지므로 신규 케이스 분리보다 이름·검증 항목 갱신이 정합적이라 판단. 결과적으로 +3 신규 + 1 갱신 = 총 9 케이스. PLAN2 의 "+4" 의도는 모두 달성됨.

4. **`PumpInterlockScenarioTest` 다중 펌프 케이스 책임 분담**: PLAN2 §테스트 전략에서 "신규 케이스 1건 — 다중 펌프 조합 시 SCADA 송신 자체 차단" 으로 명시했으나, 다중 펌프 정책의 직접 검증은 `PumpControlService` 흐름 검증이라 시나리오 테스트(InterlockValidator 단독 검증) 책임 경계와 부합하지 않는다. → 책임 분담을 클래스 Javadoc 에 명시하고, 실제 검증은 `PumpControlServiceTest#다중펌프_사전인터록_위반_시_SCADA_송신_자체가_시작되지_않는다` (Phase 2 신규 케이스) 에 위임. 결과적으로 옵션 A 정책의 직접 검증은 1건 추가됨 (`PumpControlServiceTest`).

5. **`PumpPartitionDropSchedulerTest` 신규 작성**: PLAN2 가 "기존 5" 라고 표현했으나 실제로는 클래스 자체가 미존재 (`PumpPartitionSchedulerTest` 와 혼동). 본 사이클에서 5 케이스로 신규 작성하여 PLAN2 의 의도(기존 + 12개월 누적 케이스 검증) 를 실질적으로 더 충실히 충족했다.

### 후속 작업 (제외 사항 그대로 유지)

PLAN2 §제외 사항이 본 사이클에서도 유효하다:

- **보상 STOP 명령 자체 실패 시 알람 발행** — 본 사이클은 `log.error` 로 기록만. 알람 4단계 도입은 후속 작업 `ot_integration_inbound` 범위
- **`PumpControlService` 외부 호출 전체 트랜잭션 분리 리팩토링** — REVIEW1 개선 제안 1번. 본 작업은 다중 펌프 부분 실패 정책에 한정, 외부 호출 전체 트랜잭션 분리는 후속 ANALYZE 필요
- **PLAN1 의 §제외 사항 모두 그대로 유지** (`ot_integration_inbound`·`dwt_pressure_history`·`tag_m`·Flyway·Testcontainers 등)

### 운영·문서 자료

- 운영 체크리스트는 PLAN1 사이클의 `OPS_CHECKLIST.md` 가 그대로 유효하다 (본 사이클은 운영 절차 변경 없음 — 보상 STOP 흐름은 자동 동작)
- 룰 갱신 0건이라 별도 룰 변경 보고 없음
- ANALYZE-룰 정합성도 변동 없음 — REVIEW1 시점의 17개 룰 갱신 지시서 항목은 PLAN1 사이클에서 모두 완료되었고 본 사이클에서 추가 룰 변경이 없다

### 아키텍처 의의 (PLAN2 추가)

- **2-Phase 흐름 도입 — 사전 일괄 검사 + 실행 단계 분리**: 옵션 A 의 사전 일괄 인터록 검사가 1차 차단점으로 동작하여 부분 송신 가능성을 원천 차단. 실행 단계의 부분 실패는 보상 STOP + REQUIRES_NEW 이력 보존으로 외부 트랜잭션 롤백과 무관하게 운영 추적성 유지.
- **변경 권한 격리 강화**: 기존 PLAN1 의 `changeUserIntent` vs `forceSystemMode` 메서드 분리에 더해, 이번 사이클은 `forceTransition` (스케줄러) vs `forceTransitionInNewTransaction` (보상 흐름) 으로 한 단계 더 분리하여 트랜잭션 경계 의도가 코드에 명시적으로 드러난다.
- **시나리오 테스트 책임 경계 정립**: `PumpInterlockScenarioTest` 와 `PumpOperationModeScenarioTest` 의 클래스 Javadoc 에 책임 분담을 명시함으로써 향후 시나리오 테스트 추가 시 InterlockValidator 단독 검증인지 PumpControlService 흐름 통합 검증인지 명확히 구분 가능. 호출자 흐름 모방으로 인한 테스트 중복·취약성을 회피.
