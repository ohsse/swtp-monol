---
status: approved
created: 2026-04-25
updated: 2026-04-25
---
# 송수펌프 제어 (FR-PMP-001) — 코드 리뷰 2차 (REVIEW1 블로커 해소 검증)

## 관련 결과
- [결과 RESULT2](../../../results/20260425/pumpcontrol/RESULT2.md)
- [계획안 PLAN2](../../../plan/20260425/pumpcontrol/PLAN2.md)
- [태스크 TASK2](../../../tasks/20260425/pumpcontrol/TASK2.md)
- [이전 리뷰 REVIEW1](REVIEW1.md) (해소 대상 블로커 6건)
- [이전 사이클 RESULT1](../../../results/20260425/pumpcontrol/RESULT1.md)
- [도메인 분석 ANALYZE1](../../../analyze/20260422/pumpcontrol/ANALYZE1.md)
- [운영 체크리스트](../../../results/20260425/pumpcontrol/OPS_CHECKLIST.md)

## 리뷰 범위

`feature-dev:code-reviewer` 서브에이전트 자동 리뷰 + ANALYZE-룰 정합성 점검 + REVIEW1 6건(I-1·I-2·I-3·I-4·I-5·I-6) 해소 검증 수행.

### 검토 대상 (PLAN2 사이클 변경 영역만)

- 신규 파일 3건: `PumpControlHistoryWriter`(REQUIRES_NEW 헬퍼) · `PumpControlControllerTest`(사용자 ID 추출 검증) · `PumpPartitionDropSchedulerTest`(12개월 LOOKBACK 검증)
- 운영 코드 수정 10건: `AuthErrorCode`(UNAUTHORIZED 신규) · `AiModeService`(`changeUserIntent` 시그니처 + `forceTransitionInNewTransaction` 신규) · `PumpControlController`(AUTH_SUBJECT_ATTRIBUTE) · `PumpControlService`(2-Phase 흐름 재작성) · `ScadaOutboundPort`(sendStop) + 어댑터 3건 · `ScadaControlService`(@Transactional 제거 + sendStop 위임) · `AiModeTransitionScheduler`(Javadoc·인라인 주석) · `PumpPartitionDropScheduler`(LOOKBACK 12개월)
- 테스트 수정 5건: `AiModeServiceTest`(시그니처 + 신규 2건) · `PumpControlServiceTest`(다중 펌프 신규 3건 + 단일 펌프 갱신 1건) · `PumpInterlockScenarioTest`(클래스 Javadoc 책임 분담) · `PumpOperationModeScenarioTest`(생성자 회귀 + Javadoc) · `PumpControlIntegrationTest`(시그니처 회귀)

### 리뷰 체크리스트 결과

| 항목 | 결과 |
|------|------|
| Lombok 사용 규약 (`@Setter` 금지, 생성자 주입) | ✅ 통과 |
| Javadoc 주석 (신규/수정 클래스·메서드 모두) | ✅ 통과 (PLAN2 흐름·도메인 규칙 근거 명시) |
| Swagger `@ApiResponses` (변경된 `changeAiMode`) | ✅ 통과 (UNAUTHORIZED 는 401 범위 포함, 추가 갱신 불요) |
| `CommonResponseDto`·`ResponseEntity` | ✅ 통과 |
| `RestApiException`·`ErrorCode` | ✅ 통과 |
| ErrorCode enum `httpStatus(int)` 만 허용 — `AuthErrorCode.UNAUTHORIZED` 신규 | ✅ 통과 (`exception-patterns.md §2` 단일 필드 규약 준수) |
| 민감 정보 하드코딩 금지 | ✅ 통과 |
| 네이밍 컨벤션 (메서드·필드 camelCase, 패키지 도메인 중심) | ✅ 통과 |
| 패키지 구조 (feature-based) | ✅ 통과 (`com.mo.swtp.pump.service.PumpControlHistoryWriter` 기존 구조 정합) |
| 엔티티 패턴 (변경 없음 — 본 사이클 메서드 시그니처만) | ✅ 통과 |
| 도메인 규칙 — 인터록 검사 스킵 절대 금지 | ✅ 통과 (Phase 1 매번 재평가) |
| 도메인 규칙 — 안전 정지 시퀀스 후 수동 모드 전환 | ✅ 통과 (`performSafeStop` → `forceTransitionInNewTransaction`) |
| 도메인 규칙 — AI 모드 이중 체계 (`ai_drvn_mod` vs `ai_mode_cd`) | ✅ 통과 (변경 권한 격리 메서드 분리) |
| 도메인 규칙 — `ctrl_rslt='FAIL'` 기록 보존 | ✅ 통과 (REQUIRES_NEW 외부 롤백과 분리) |
| OT 연동 안전성 — CircuitBreaker·Retry (변경 없음) | ✅ 통과 |
| 트랜잭션 경계 의도 (REQUIRES_NEW 자가 호출 한계 회피) | ✅ 통과 (별도 컴포넌트 + 신규 메서드 분리) |
| 테스트 충실성 (4 흐름 + ArgumentCaptor) | ✅ 통과 |
| 회귀 수정 완전성 (`PumpOperationModeScenarioTest`·`PumpControlIntegrationTest`) | ✅ 통과 |
| TASK 규모 적정성 (Phase 5 / 체크박스 35) | ✅ 통과 (분할 기준 Phase 10·체크박스 60 미달 → 단일 적정) |

### REVIEW1 6건 해소 판정

| 번호 | 영역 | 해소 위치 | 판정 |
|------|------|----------|------|
| I-1 | 사용자 ID 누락 | `AiModeService.changeUserIntent(dto, rgstrId)` + `PumpControlController` `AUTH_SUBJECT_ATTRIBUTE` 추출 + `AiModeServiceTest` ArgumentCaptor 검증 | ✅ 해소 |
| I-2 | 다중 펌프 부분 실패 | `PumpControlService` 2-Phase 흐름 + `PumpControlHistoryWriter` REQUIRES_NEW + `forceTransitionInNewTransaction` + 보상 STOP + `PumpControlServiceTest` 4 흐름 검증 | ✅ 해소 |
| I-3 | `lastRcvDtm == null` stub 정책 | `AiModeTransitionScheduler.applyScadaTimeoutTransition` Javadoc 4줄 + 인라인 TODO 주석 | ✅ 해소 |
| I-4 | 보존 경계 단일 파티션 누적 위험 | `PumpPartitionDropScheduler` `LOOKBACK_MONTHS=12` 역순 루프 + 신규 테스트 5 케이스 | ✅ 해소 |
| I-5 | `ScadaControlService` 불필요 `@Transactional` | 클래스 레벨 어노테이션 제거 + Javadoc 사유 명시 | ✅ 해소 |
| I-6 | SCADA_TIMEOUT 의무 시나리오 | `PumpOperationModeScenarioTest` 클래스 Javadoc 에 책임 분담 명시 (`AiModeTransitionSchedulerTest` 가 직접 검증 책임) | ✅ 해소 |

**6/6 모두 해소 — Fix Cycle 목표 달성.**

### ANALYZE-룰 정합성 점검

본 사이클은 **룰 갱신 0건**. REVIEW1 시점의 ANALYZE-룰 정합성 점검 (17개 룰 갱신 지시서 모두 완료) 이 그대로 유효하며 본 사이클에서 추가 누락은 발생하지 않았다. **점검 통과.**

## 발견 사항

| 번호 | 심각도 | 영역 | 파일·라인 | 설명 | 신뢰도 |
|------|--------|------|----------|------|--------|
| F-1 | 중간 | 트랜잭션 경계 문서화 | `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java:170-183` (`executeAllPumps` 부분 실패 블록) | `forceTransitionInNewTransaction` 커밋 후 `historyWriter.saveNew` 루프에서 DB 장애 시 `ai_drvn_mod_p` 는 MANUAL 로 남고 `pump_ctrl_h` 이력이 누락될 수 있는 경계 조건이 Javadoc/인라인 주석에 미명시 | 82 |
| F-2 | 중간 | 응답 계약 동기화 | `api/src/main/java/com/mo/swtp/auth/exception/AuthErrorCode.java:20` + RESULT2 §비고 | 신규 `UNAUTHORIZED(401)` enum 추가 시 프론트엔드 명세에 해당 코드의 사용자 표기 문자열 등록 필요성이 코드 Javadoc·RESULT2 비고 어디에도 명시되지 않음 | 80 |

### F-1 [중간] REQUIRES_NEW 부분 커밋 시 이력 누락 경계 조건 Javadoc 누락

**파일**: `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java:170-183` (`executeAllPumps` 의 부분 실패 분기)

```java
// 부분 실패 — 안전 정지 시퀀스
log.warn(...);
performSafeStop(succeeded);
aiModeService.forceTransitionInNewTransaction(                          // (커밋 1)
        req.getPwtfId(), AiSystemModeCode.MANUAL, TransitionReason.OUTBOUND_FAIL);
// ↑ REQUIRES_NEW 즉시 커밋 후 ↓ 루프에서 DB 장애 발생 시
//    ai_drvn_mod_p 는 MANUAL 로 남고 pump_ctrl_h 이력 누락 가능
for (String pumpId : succeeded) {
    historyWriter.saveNew(pumpId, ctrlDtm, req.getCtrlDiv(),             // (커밋 2..N)
            PumpControlResult.SUCCESS, currentIntent);
}
historyWriter.saveNew(failedPumpId, ctrlDtm, req.getCtrlDiv(),           // (커밋 N+1)
        PumpControlResult.FAIL, currentIntent);
throw new RestApiException(ScadaErrorCode.SCADA_OUTBOUND_FAILED);
```

**위반 근거**: `ot-integration.md §5` 의 "제어 실패 시 `pump_ctrl_h.ctrl_rslt='FAIL'` 기록" 이 일부 DB 장애 시 누락될 수 있다. PLAN2 §2 가 인지하는 REQUIRES_NEW 패턴의 근본 한계이지만 코드에 인라인 주석이 없어 향후 유지보수자가 오해할 수 있다.

**권장 조치**: `executeAllPumps` 메서드 또는 `historyWriter.saveNew` 루프 직전에 다음 인라인 주석 추가 (변경 범위 작음):

```java
// 트랜잭션 경계 (블로커 I-2 — 2026-04-25):
// forceTransitionInNewTransaction 은 이미 REQUIRES_NEW 로 커밋됨.
// 이후 saveNew 루프에서 DB 장애 발생 시 ai_drvn_mod_p 는 MANUAL 로 남고
// pump_ctrl_h 이력이 누락될 수 있다 — REQUIRES_NEW 패턴의 근본 한계.
// 운영 체크리스트의 수동 복구 지침 참조.
```

본 항목은 "도메인 정합성 키워드" (용어/약어/중복 정의/네이밍 충돌/엔티티 통합) 미포함으로 ANALYZE 재진입 대상 아님.

### F-2 [중간] `AuthErrorCode.UNAUTHORIZED` 프론트엔드 명세 동기화 필요성 미명시

**파일**: `api/src/main/java/com/mo/swtp/auth/exception/AuthErrorCode.java:20` + RESULT2 §비고

```java
LOGIN_FAILED(401),
/**
 * 인증 컨텍스트가 없는 상황. JWT 필터가 적재한
 * {@code request.getAttribute(AUTH_SUBJECT_ATTRIBUTE)} 가 NULL 인 경우 등에 사용된다.
 * 정상 흐름에서는 JWT 필터가 항상 채우므로 방어 코드 용도.
 */
UNAUTHORIZED(401),
```

**위반 근거**: `exception-patterns.md §1` "프론트엔드 명세 — `code` 값을 키로 화면 표기 문자열 결정" — 신규 enum 값 추가 시 프론트엔드 명세도 매핑해야 한다. 본 사이클에서 추가된 `UNAUTHORIZED` 가 프론트엔드 명세에 등록되지 않으면 사용자에게 빈 에러 문구가 노출될 수 있다. RESULT2 §비고는 "룰 갱신 영향 없음" 으로 처리했으나 프론트엔드 명세 동기화 알림은 별도 사안이다.

**권장 조치**: 다음 둘 중 하나:

1. `AuthErrorCode.UNAUTHORIZED` Javadoc 에 한 줄 추가 — "프론트엔드 명세에 본 코드의 사용자 표기 문자열 등록 필요"
2. RESULT2 §비고 또는 OPS_CHECKLIST.md 에 "프론트엔드 ErrorCode 명세 동기화 — `UNAUTHORIZED`" 항목 추가

본 항목도 "도메인 정합성 키워드" 미포함이라 ANALYZE 재진입 대상 아님.

## 개선 제안

본 작업 범위 외 후속 개선 가능 항목 (블로커 아님):

1. **`PumpControlHistoryWriter` 의 saveNew 호출에 운영 알람 hook 도입**: 부분 실패 흐름에서 saveNew 자체가 DB 장애로 실패하면 현재는 외부로 예외 전파만 한다. 알람 4단계 도입 시 (`ot_integration_inbound`) 본 메서드의 catch 분기에 알람 발행을 연결하면 운영 가시성이 크게 개선됨.
2. **`AiModeService` 의 두 강제 전환 메서드 — 호출 위치 명확화**: `forceTransition` (스케줄러용) vs `forceTransitionInNewTransaction` (보상 흐름용) 의 호출 경로 매트릭스를 클래스 Javadoc 표로 추가하면 향후 유지보수자가 어느 메서드를 사용해야 할지 명확. 현재는 본문 설명만 있음.
3. **`PumpPartitionDropScheduler.LOOKBACK_MONTHS = 12` 의 운영 임계값 모니터링**: 12개월 초과 누적 시 운영자 수동 개입 필요 — 운영 체크리스트의 모니터링 절기 추가 검토 (기존 OPS_CHECKLIST.md 갱신 시점).
4. **`PumpControlServiceTest` 의 `argMatchesPump` 헬퍼 — 공유 가능성**: 본 헬퍼가 다른 PumpControlService 관련 테스트에서도 재사용 가능. 현재는 단일 클래스 내 private static 으로 유지되었으나 향후 추가 테스트 시 공통 유틸로 리팩토링 검토.
5. **PLAN2 가 명시한 후속 ANALYZE 대상 — `PumpControlService` 외부 호출 전체 트랜잭션 분리**: 본 사이클은 부분 실패 정책에 한정. 외부 호출(SCADA·AI) 을 트랜잭션 경계 외부로 분리하는 리팩토링은 후속 ANALYZE 단계에서 다룸.

## 결론

- **블로커 (높음): 0건**
- **중간: 2건** (F-1, F-2 — 모두 문서화·명세 동기화 항목, 도메인 규칙 위반 아님)
- **낮음: 0건**

**REVIEW1 의 6건(I-1·I-2·I-3·I-4·I-5·I-6) 모두 해소 확인.** Fix Cycle 목표 달성.

블로커 0건이므로 `status: approved` 로 전환한다. 발견된 중간 2건은 문서화·운영 명세 동기화 사안으로 본 사이클 커밋 이후 별도 작은 PR 또는 운영 체크리스트 갱신으로 처리 가능 — Fix Cycle 진입 불필요.

### 다음 단계 안내

블로커 0건. 사용자가 `/dev:commit pumpcontrol` 를 실행하여 본 사이클의 변경을 커밋하면 작업이 완료된다. (`/dev:commit` 은 사용자 명시적 승인이 필요하며 자동 실행되지 않는다.)
