---
status: draft
created: 2026-04-25
updated: 2026-04-25
---
# 송수펌프 제어 (FR-PMP-001) — 코드 리뷰 1차

## 관련 결과
- [결과](../../../results/20260425/pumpcontrol/RESULT1.md)
- [계획안](../../../plan/20260425/pumpcontrol/PLAN1.md)
- [도메인 분석](../../../analyze/20260422/pumpcontrol/ANALYZE1.md)
- [운영 체크리스트](../../../results/20260425/pumpcontrol/OPS_CHECKLIST.md)

## 리뷰 범위

`feature-dev:code-reviewer` 서브에이전트 자동 리뷰 + ANALYZE-룰 정합성 점검 수행.

### 검토 대상

- 신규 비즈니스 도메인 패키지 3건 (`com.mo.swtp.pump`·`com.mo.swtp.ai`·`com.mo.swtp.scada.outbound`)
- 엔티티 13건 (common 모듈) — 마스터 4 + 상세 1 + 명세 2 + 시계열 파티션 3 + ID 클래스 3
- ErrorCode enum 3건 (`PumpErrorCode`·`AiErrorCode`·`ScadaErrorCode`)
- Repository 12건 (api 10 + scheduler 자체 2)
- Service 7건 (`InterlockValidator`·`AiModeService`·`AiPredictionService`·`PumpDashboardService`·`PumpMasterCacheService`·`PumpControlService`·`ScadaControlService`)
- Controller 1건 (`PumpControlController`) + DTO 9건
- 어댑터 4건 (`ScadaOutboundPort` 인터페이스 + `LoggingNoOp`/`Modbus`/`OpcUa` 어댑터 3 + `ScadaOutboundConfig`)
- AI 클라이언트 (`AiServerClient` + `AiServerClientConfig`)
- 스케줄러 4건 (`PumpPartitionScheduler`·`PumpPartitionDropScheduler`·`AiModeTransitionScheduler`·`PumpSchedulerConfig`)
- DDL 6건 (`V1`~`V5` + README)
- 단위 테스트 12 클래스 (73 케이스 중 72 통과 + 1 skip)
- 설정 변경 (`application.yml`·`build.gradle`·`resources-env/gs/`·`application-test.yml`)

### 리뷰 체크리스트 결과

| 항목 | 결과 |
|------|------|
| Lombok 사용 규약 (`@Setter` 금지) | ✅ 통과 |
| 생성자 주입 (`@RequiredArgsConstructor`) | ✅ 통과 |
| Javadoc 주석 | ✅ 통과 (설계 경계까지 명시) |
| Swagger 6 응답 코드 명세 | ✅ 통과 |
| `CommonResponseDto`·`ResponseEntity` | ✅ 통과 |
| `RestApiException`·`ErrorCode` | ✅ 통과 |
| ErrorCode enum `httpStatus(int)` 만 허용 | ✅ 통과 |
| 민감 정보 하드코딩 금지 | ✅ 통과 (환경변수 주입) |
| 네이밍 컨벤션 | ✅ 통과 |
| 패키지 구조 (feature-based) | ✅ 통과 |
| 엔티티 패턴 (`Persistable<ID>`·정적 팩토리·`YnType`) | ✅ 통과 |
| 도메인 규칙 — 인터록 검사 스킵 금지 | ✅ 통과 (테스트 7회 검증) |
| 도메인 규칙 — 파티션 마스터 FK 금지 | ✅ 통과 (DDL 주석 명시) |
| 도메인 규칙 — AI 모드 이중 체계 변경 권한 분리 | ✅ 통과 (메서드 분리) |
| OT 연동 안전성 — CircuitBreaker·Retry | ✅ 통과 |
| TASK 규모 분할 적정성 (Phase 26 / 체크박스 71) | ✅ 통과 (분할 기준 동시 초과로 분할 정당) |
| 의무 시나리오 테스트 누락 | ⚠️ 일부 (I-6 — SCADA_TIMEOUT 경로 누락) |

### ANALYZE-룰 정합성 점검

ANALYZE1 의 "## 룰 갱신 지시서" 17 체크박스 모두 `[x]` 완료. 실제 변경 파일 대조 결과:

| 룰 파일 | 지시서 체크 | 실제 변경 |
|---------|-----------|----------|
| `.claude/rules/domain-abbreviations.md` | [x] | ✅ 변경됨 |
| `.claude/rules/dict/standard-words.md` | [x] | ✅ 변경됨 |
| `.claude/rules/dict/standard-data-domains.md` | [x] | ✅ 변경됨 |
| `.claude/rules/dict/standard-terms.md` | [x] | ✅ 변경됨 |
| `.claude/rules/ot-integration.md` | [x] | ✅ 변경됨 |
| `.claude/rules/db-partitioning-and-retention.md` | [x] | ✅ 변경됨 |

**룰 갱신 누락 0건** — ANALYZE-룰 정합성 점검 통과.

## 발견 사항

| 번호 | 심각도 | 영역 | 파일·라인 | 설명 | 신뢰도 |
|------|--------|------|----------|------|--------|
| I-1 | **높음 (블로커)** | 운영 추적성 | `api/src/main/java/com/mo/swtp/ai/service/AiModeService.java:62-66` | `changeUserIntent` 이력 기록에 사용자 ID 대신 `"SYSTEM"` 하드코딩 | 92 |
| I-2 | **높음 (블로커)** | 트랜잭션 일관성 | `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java:97-103` | 다중 펌프 루프 부분 실패 시 물리 PLC 명령과 DB 이력 불일치 가능 | 88 |
| I-3 | 중간 | 스케줄러 정책 명확성 | `scheduler/src/main/java/com/mo/swtp/scheduler/pump/AiModeTransitionScheduler.java:83-85` | `lastRcvDtm == null` 시 SCADA timeout 판정이 영구 스킵되는 stub 정책이 Javadoc 미명시 | 85 |
| I-4 | 중간 | 스케줄러 복원성 | `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpPartitionDropScheduler.java:44-51` | 보존 경계 단일 파티션만 DROP — 스케줄러 누락 발생 시 초과 파티션 누적 | 85 |
| I-5 | 중간 | 트랜잭션 오버헤드 | `api/src/main/java/com/mo/swtp/scada/outbound/ScadaControlService.java:23` | DB 작업이 없는 OT 어댑터 위임 서비스에 `@Transactional(readOnly = true)` 불필요 | 82 |
| I-6 | 중간 | 의무 시나리오 누락 | `api/src/test/java/com/mo/swtp/pump/service/PumpOperationModeScenarioTest.java:159-172` | 의무 케이스 4번 "SCADA 5분 초과 강제 전환" 이 OUTBOUND_FAIL 경로로 대체 검증 — `SCADA_TIMEOUT` 타이머 판정이 의무 범위에서 누락 | 80 |

### I-1 [블로커] `changeUserIntent` 이력 기록에 사용자 ID 누락

**파일**: `api/src/main/java/com/mo/swtp/ai/service/AiModeService.java:62-66`

```java
recordHistory(dto.getPwtfId(),
        prevIntent, dto.getAiDrvnMod(),
        prevSystem, prevSystem,
        TransitionReason.USER_SELECT,
        SYSTEM_ACTOR);  // ← "SYSTEM" 하드코딩
```

`forceTransition` 은 스케줄러·장애 대응이므로 `SYSTEM_ACTOR = "SYSTEM"` 이 맞으나, `changeUserIntent` 는 사용자 API 전용 경로(`reason = USER_SELECT`) 인데 동일하게 `"SYSTEM"` 을 기록한다.

**위반 근거**: `AiDrvnModeHistory.rgstr_id` Javadoc(`common/.../AiDrvnModeHistory.java:86`) — "사용자 API 면 사용자 ID, 스케줄러면 'SYSTEM'" 계약 위반. 운영 감사 시 누가 모드를 변경했는지 추적이 불가능.

**권장 조치**: `SecurityContextHolder` 에서 principal 추출 또는 `changeUserIntent(dto, principal)` 시그니처 추가하여 `rgstrId` 에 실 사용자 ID 전달.

### I-2 [블로커] 다중 펌프 루프 부분 실패 시 트랜잭션 일관성 파괴

**파일**: `api/src/main/java/com/mo/swtp/pump/service/PumpControlService.java:97-103`

```java
PumpControlHistory lastHistory = null;
for (String pumpId : pumpIds) {
    lastHistory = controlOnePump(pumpId, req, pumpCmbnCd, currentIntent);
}
return PumpControlResultDto.from(lastHistory);
```

`executeControl` 의 `@Transactional` 범위에서 펌프 ID 리스트를 순회한다. 다중 펌프 조합(2개 이상) 에서 첫 펌프 SCADA 송신 성공 후 두 번째 펌프 SCADA 실패 시:
1. 첫 펌프: PLC START 명령 송신 완료(외부 시스템 상태 변경)
2. `controlOnePump(두번째)` 의 `forceTransition` 은 별도 트랜잭션으로 즉시 커밋
3. `recordHistory(FAIL)` 후 `RestApiException` throw → 외부 트랜잭션 전체 롤백
4. 결과: 첫 펌프의 SUCCESS 이력 롤백, 첫 펌프 PLC 명령은 물리적으로 실행됨 → **물리/논리 불일치**

**위반 근거**: PLAN1 §3 흐름은 단일 펌프 catch 만 다뤘으나, 펌프 조합은 1~N 개의 펌프를 동시에 제어하므로 다중 펌프 부분 실패 정책이 명시 누락. `ot-integration.md §5` 의 안전 정지 시퀀스 범위에서 다뤄야 할 사안.

**권장 조치 (옵션 A)**: 루프 시작 전 모든 펌프의 인터록을 일괄 검사하고 1건이라도 위반 시 SCADA 송신 자체를 차단. 실패 펌프가 없으면 일괄 송신 + 일괄 이력 저장.

**권장 조치 (옵션 B)**: 다중 펌프 부분 실패 보상 트랜잭션 — 이미 송신된 펌프에 STOP 명령 발행 + FAIL 이력 동일 트랜잭션 보존. 단, 보상 STOP 자체가 또 실패할 수 있어 운영 정책 정의 필요.

**옵션 결정 사항**: 어느 옵션을 채택할지 사용자/도메인 expert 결정 필요. 현재 PLAN 단계에서 다뤄지지 않은 흐름이므로 ANALYZE 재진입 가능성 검토.

### I-3 [중간] `lastRcvDtm == null` 시 SCADA timeout 영구 스킵

**파일**: `scheduler/src/main/java/com/mo/swtp/scheduler/pump/AiModeTransitionScheduler.java:83-85`

```java
LocalDateTime lastRcv = mode.getLastRcvDtm();
if (lastRcv == null) {
    return;  // ← null 이면 판정 자체를 스킵
}
```

`last_rcv_dtm` 은 SCADA 인바운드 어댑터(별도 작업 `ot_integration_inbound`) 미구현 상태에서 NULL. 즉, 현 단계에서 `AI_AUTO` 상태인 모든 정수조는 `lastRcvDtm == null` 이라 SCADA timeout 강제 전환이 영원히 발동하지 않음.

**권장 조치**: 본 동작이 stub 기간 정책이라면 Javadoc 또는 코드 주석에 다음 문구 추가:

```java
// stub 기간 정책: ot_integration_inbound 미구현 상태에서는 last_rcv_dtm == null 이 정상.
// 인바운드 어댑터 도입 후에는 null 을 "수신 이력 없음 = SCADA 미연동" 으로 강제 전환 대상에 포함하도록
// 정책 변경 검토 필요. (TODO: ot_integration_inbound 진입 시 본 분기 재검토)
```

### I-4 [중간] 보존 경계 단일 파티션만 DROP — 누적 위험

**파일**: `scheduler/src/main/java/com/mo/swtp/scheduler/pump/PumpPartitionDropScheduler.java:44-51`

`current.minusMonths(retentionMonths)` 로 1개 파티션만 DROP. 스케줄러 N일 중단 후 복구 시 N개 초과 파티션 잔류.

**권장 조치**: 보존 경계부터 일정 개월(예: +12) 이전까지 역순 순회하며 `DROP TABLE IF EXISTS` 실행. `IF EXISTS` 가 안전 가드.

### I-5 [중간] `ScadaControlService` 불필요한 `@Transactional`

**파일**: `api/src/main/java/com/mo/swtp/scada/outbound/ScadaControlService.java:23`

OT 어댑터 위임 전용 서비스에 클래스 레벨 `@Transactional(readOnly = true)` 적용. DB 커넥션 풀 불필요 점유.

**권장 조치**: 클래스 레벨 `@Transactional` 제거. `api/CLAUDE.md` Service 규칙은 DB 접근 서비스 한정.

### I-6 [중간] SCADA_TIMEOUT 의무 시나리오 누락

**파일**: `api/src/test/java/com/mo/swtp/pump/service/PumpOperationModeScenarioTest.java:159-172`

`test-strategy.md §5.2.3` 의무 케이스 4번 "SCADA 5분 초과 중단 시 AI 자동 → 반자동 강제 전환" 이 `OUTBOUND_FAIL` 경로로 대체 검증되어 `SCADA_TIMEOUT` 타이머 판정이 의무 범위에서 직접 검증되지 않음. 단, `scheduler` 모듈의 `AiModeTransitionSchedulerTest` 에서 SCADA_TIMEOUT 케이스가 검증되고 있음.

**권장 조치**: `PumpOperationModeScenarioTest` 에 명시적 SCADA_TIMEOUT 의무 케이스 추가 또는 의무 시나리오 분기 검증 책임이 `AiModeTransitionSchedulerTest` 에 있음을 시나리오 문서에 명시.

## 개선 제안

본 작업 범위 외 후속 개선 가능 항목 (블로커 아님):

1. **`PumpControlService` 의 `@Transactional` 경계 재검토** (I-2 와 연계): 외부 시스템 호출(SCADA·AI) 을 트랜잭션 안에 포함하는 것은 일반적으로 권장되지 않음. 트랜잭션은 DB 레벨로 축소하고 외부 호출은 별도 단계로 분리하는 것이 안전.
2. **`AiServerClient` fallback 캐시**: 현재 fallback 은 즉시 예외를 던지지만 PLAN §4 의 "직전 예측 반환" 옵션은 후속 작업으로 명시. 캐시 인프라 도입 시 적용.
3. **`@SequenceGenerator` allocationSize 재평가**: `pump_ctrl_id` 100, `predc_id` 100, `ai_drvn_mod_h_id` 10 — 시퀀스 gap 손실 운영 후 모니터링 필요.
4. **인터록 평가 시 SCADA query 캐시**: 현재는 매 호출마다 SCADA 태그 조회. 다중 인터록 규칙이 동일 태그를 참조하면 중복 호출 가능. 단, "검사 스킵 절대 금지" 와 별개의 이슈.

## 결론

- **블로커 (높음): 2건** (I-1, I-2)
- **중간: 4건** (I-3, I-4, I-5, I-6)
- **낮음: 0건**

블로커 2건이 발견되어 `status: draft` 유지. Fix Cycle 진입을 권장한다.

### Fix Cycle 진입 시 분기 판정 (`doc-harness.md` §수정 사이클)

블로커 텍스트 키워드 매칭:

| 키워드 | I-1 | I-2 | 매칭 |
|--------|-----|-----|------|
| 용어 | — | — | ❌ |
| 약어 | — | — | ❌ |
| 중복 정의 | — | — | ❌ |
| 네이밍 충돌 | — | — | ❌ |
| 엔티티 통합 | — | — | ❌ |

**도메인 정합성 키워드 미포함** → ANALYZE 재진입 스킵, **PLAN2 직행** 으로 Fix Cycle 진입한다.

단, I-2 의 다중 펌프 부분 실패 정책은 도메인 expert 의견이 필요할 수 있어 PLAN 단계에서 다뤄야 한다 (ANALYZE 재진입 키워드와는 별개).

### 다음 단계 안내

블로커 2건 발견. 수정 사이클을 시작하려면 `/dev pumpcontrol` 를 실행하세요. (Fix Cycle 감지 알고리즘에 따라 PLAN2 단계로 진입합니다.)
