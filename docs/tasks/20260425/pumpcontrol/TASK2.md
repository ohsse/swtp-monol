---
status: completed
created: 2026-04-25
updated: 2026-04-25
---

> **진행 상태 (2026-04-25 완료)**: Phase 1·2·3·4·5 모두 ✅ 완료. 35/35 체크박스 통과.
# 송수펌프 제어 (FR-PMP-001) — 코드 리뷰 1차 블로커 해소 (TASK2)

## 관련 계획
- [계획안 PLAN2](../../../plan/20260425/pumpcontrol/PLAN2.md)
- [이전 리뷰 REVIEW1](../../../reviews/20260425/pumpcontrol/REVIEW1.md)
- [PLAN1 (직전 사이클)](../../../plan/20260425/pumpcontrol/PLAN1.md)

## Phase

### Phase 1: I-1 해소 — 사용자 ID 전파 (`changeUserIntent`)

- [x] `api/src/main/java/com/mo/swtp/ai/service/AiModeService.java` 의 `changeUserIntent(AiModeUpsertDto dto)` 시그니처를 `changeUserIntent(AiModeUpsertDto dto, String rgstrId)` 로 변경하고 `recordHistory` 호출 시 `SYSTEM_ACTOR` 대신 `rgstrId` 전달
- [x] `api/src/main/java/com/mo/swtp/ai/service/AiModeService.java` 의 `changeUserIntent` Javadoc 에 `rgstrId` 매개변수 설명 추가 — "사용자 API 호출 주체의 사용자 ID. JWT 인증 필터가 적재한 `AUTH_SUBJECT_ATTRIBUTE` 값" 명시
- [x] `api/src/main/java/com/mo/swtp/pump/web/PumpControlController.java` 의 `changeAiMode` 핸들러에 `HttpServletRequest request` 매개변수 추가 + `request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE)` 로 `rgstrId` 추출 후 `aiModeService.changeUserIntent(dto, rgstrId)` 호출
- [x] `api/src/main/java/com/mo/swtp/pump/web/PumpControlController.java` 에 `rgstrId == null || rgstrId.isBlank()` 방어 — 위반 시 `RestApiException(AuthErrorCode.UNAUTHORIZED)` 던짐 (`api/src/main/java/com/mo/swtp/auth/exception/AuthErrorCode.java` 에 `UNAUTHORIZED(401)` 신규 추가 — PLAN2 가 재사용 가정한 것이 미존재여서 신규 도입)
- [x] `api/src/test/java/com/mo/swtp/ai/service/AiModeServiceTest.java` 기존 11 케이스 모두 시그니처 변경 반영 — 호출부에 `"test-user"` 등 명시적 `rgstrId` 전달
- [x] `api/src/test/java/com/mo/swtp/ai/service/AiModeServiceTest.java` 의 `changeUserIntent` 케이스 1건에서 `ArgumentCaptor<AiDrvnModeHistory>` 로 저장된 이력의 `rgstrId` 가 `"test-user"` 인지 검증 (신규 케이스 2건 추가 — `changeUserIntent_호출_시_history_의_rgstr_id_가_사용자_id_로_기록된다` + `forceTransition_호출_시_history_의_rgstr_id_는_SYSTEM_으로_기록된다` 회귀 방지)
- [x] `api/src/test/java/com/mo/swtp/pump/web/PumpControlControllerTest.java` 신규 작성 — `MockHttpServletRequest.setAttribute(AUTH_SUBJECT_ATTRIBUTE, "user001")` 후 `changeAiMode` 호출 시 service 에 `"user001"` 전달 검증
- [x] `api/src/test/java/com/mo/swtp/pump/web/PumpControlControllerTest.java` 에 `AUTH_SUBJECT_ATTRIBUTE` 미적재 시 `AuthErrorCode.UNAUTHORIZED` 던지는 케이스 추가 (NULL + 빈 문자열 2 케이스)

### Phase 2: I-2 해소 — 다중 펌프 부분 실패 정책 (옵션 A + 보상 STOP)

- [x] `api/src/main/java/com/mo/swtp/scada/outbound/ScadaOutboundPort.java` 에 `void sendStop(String equipmentId)` 메서드 추가 + Javadoc 작성 (보상 STOP 용도, `ScadaOutboundException` 던짐 명시)
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/LoggingNoOpScadaAdapter.java` 에 `sendStop` 구현 — `log.info` 로 STOP 명령 기록
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/ModbusTcpScadaAdapter.java` 에 `sendStop` stub 구현 — `log.warn` + `UnsupportedOperationException` 던짐, Javadoc 에 stub 명시
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/OpcUaScadaAdapter.java` 에 `sendStop` stub 구현 — `log.warn` + `UnsupportedOperationException` 던짐, Javadoc 에 stub 명시
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java` 에 사전 일괄 검증 메서드 `preflightAllPumps(pumpIds, req, pumpCmbnCd)` 추가 — 모든 펌프 마스터 존재 검증 + 모든 펌프 인터록 검사 (1건이라도 위반 시 즉시 throw, ControlCommandDto 미리 생성하여 LinkedHashMap 으로 반환)
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java` 에 보상 STOP 메서드 `performSafeStop(List<String> succeeded)` 추가 — 각 펌프별 try/catch + STOP 실패는 `log.error` 만
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java` 에 `recordHistoryNew(...)` 메서드 추가 — 별도 컴포넌트 옵션 채택. `api/src/main/java/com/mo/swtp/pump/service/PumpControlHistoryWriter.java` 신규 생성 — `@Transactional(propagation = REQUIRES_NEW)` 로 자가 호출 한계 회피
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java` 의 `executeControl` 흐름을 2-Phase 구조로 재구성 — Phase 0(검증·예측) → Phase 1(preflight) → Phase 2(executeAllPumps — 펌프별 순차 송신 + 부분 실패 보상). `api/src/main/java/com/mo/swtp/ai/service/AiModeService.java` 에 `forceTransitionInNewTransaction` 메서드 신규 추가 (REQUIRES_NEW) — 보상 흐름 전용
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java` 의 클래스 Javadoc 에 2-Phase 흐름 그림 갱신 (PLAN2 §2 흐름 그대로 반영) + 도메인 규칙 (`ot-integration.md §5`) 준수 항목 명시
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java` 의 기존 `controlOnePump` 메서드 제거 — `executeAllPumps` 헬퍼로 대체. `api/src/main/java/com/mo/swtp/scada/outbound/ScadaControlService.java` 에 `sendStop(equipmentId)` 위임 메서드 추가
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpControlServiceTest.java` 에 케이스 추가 — 단일 펌프 SCADA 실패 시 보상 STOP 호출 0회 + `forceTransitionInNewTransaction(MANUAL, OUTBOUND_FAIL)` + FAIL 이력 (`historyWriter.saveNew`) 보존 검증 (기존 케이스 갱신)
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpControlServiceTest.java` 에 케이스 추가 — 다중 펌프 사전 인터록 위반 (3개 중 2번째 펌프 위반) 시 SCADA 송신 자체 시작 안 됨 + `INTERLOCK_VIOLATION` 던지기 검증 (`scadaControlService.send(...)` 호출 0회 verify)
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpControlServiceTest.java` 에 케이스 추가 — 다중 펌프 송신 도중 두 번째 펌프 실패 시 첫 펌프에 `sendStop` 1회 호출 + SUCCESS·FAIL 이력 모두 `historyWriter.saveNew` 로 보존 + `forceTransitionInNewTransaction(MANUAL, OUTBOUND_FAIL)` 1회 + `SCADA_OUTBOUND_FAILED` 던지기 검증
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpControlServiceTest.java` 에 케이스 추가 — 다중 펌프 모두 성공 시 일괄 SUCCESS 이력 저장 + `sendStop` 호출 0회 + `forceTransitionInNewTransaction` 호출 0회 verify
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpInterlockScenarioTest.java` 에 케이스 추가 — 옵션 A 정책의 직접 검증은 `PumpControlServiceTest#다중펌프_사전인터록_위반_시_SCADA_송신_자체가_시작되지_않는다` 가 책임진다는 책임 분담 명시를 클래스 Javadoc 에 추가 (시나리오 테스트는 InterlockValidator 단독 검증, 호출자 흐름 모방 회피)

### Phase 3: 중간 사항 해소 (I-3·I-4·I-5)

- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/pump/AiModeTransitionScheduler.java` 의 `applyScadaTimeoutTransition` 메서드 Javadoc 에 stub 정책 4줄 명시 (`ot_integration_inbound` 미구현 상태에서 NULL 정상, 인바운드 도입 후 정책 변경 검토 필요)
- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/pump/AiModeTransitionScheduler.java` 의 `lastRcv == null` 분기에 인라인 주석 2줄 추가 (TODO + Javadoc 참조)
- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpPartitionDropScheduler.java` 에 `LOOKBACK_MONTHS = 12` 상수 추가
- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpPartitionDropScheduler.java` 의 `dropExpiredPartitions` 에 보존 경계부터 12개월 이전까지 역순 순회하는 내부 루프 추가 (`for (int i = 0; i < LOOKBACK_MONTHS; i++)`)
- [x] `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpPartitionDropScheduler.java` 의 클래스 Javadoc 에 LOOKBACK 정책 명시 (스케줄러 누락 시 누적 위험 해소)
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/ScadaControlService.java` 의 클래스 레벨 `@Transactional(readOnly = true)` 어노테이션 제거
- [x] `api/src/main/java/com/mo/swtp/scada/outbound/ScadaControlService.java` 의 클래스 Javadoc 에 "DB 작업이 없는 OT 어댑터 위임 서비스이므로 트랜잭션 미사용" 한 줄 추가
- [x] `scheduler/src/test/java/com/mo/swtp/scheduler/pump/PumpPartitionDropSchedulerTest.java` 에 케이스 추가 — 12개월 누적 시뮬레이션 시 `JdbcTemplate.execute` 호출 횟수가 `3 테이블 × 12개월 = 36회` 인지 검증 (PLAN2 가 "기존 5" 로 가정했으나 실제는 신규 클래스 — 5 케이스 신설)

### Phase 4: I-6 해소 — 테스트 책임 분담 Javadoc

- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpOperationModeScenarioTest.java` 의 클래스 Javadoc 에 SCADA_TIMEOUT 책임 분담 명시 — "본 클래스는 OUTBOUND_FAIL 경로의 강제 전환만 검증, SCADA_TIMEOUT 직접 검증은 `AiModeTransitionSchedulerTest` 책임"

### Phase 5: 빌드·검증

- [x] `./gradlew.bat :common:test` 실행 성공 확인 (변경 없음 — regression, UP-TO-DATE)
- [x] `./gradlew.bat :api:test` 실행 성공 확인 (시그니처 변경 반영 + 신규 케이스 통과 — BUILD SUCCESSFUL 23s)
- [x] `./gradlew.bat :scheduler:test` 실행 성공 확인 (다중 파티션 DROP 케이스 통과 — BUILD SUCCESSFUL 8s)
- [x] `./gradlew.bat clean build` 실행 성공 확인 (전체 23 task 실행 성공 — BUILD SUCCESSFUL 35s, QClass 재생성 무영향)

## 산출물
- [결과 RESULT2](../../../results/20260425/pumpcontrol/RESULT2.md)
- [리뷰 REVIEW2](../../../reviews/20260425/pumpcontrol/REVIEW2.md)
