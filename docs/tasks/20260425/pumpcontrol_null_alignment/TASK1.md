---
status: completed
created: 2026-04-25
updated: 2026-04-25
---
# pumpcontrol 도메인 DDL NULL 정책 정합성 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260425/pumpcontrol_null_alignment/PLAN1.md)

## Phase

### Phase 1: DDL 마이그레이션 (common 모듈)

> Phase 1 의 1·3·4 항목은 **운영 DB 환경에서 배포 시 운영자가 수행** 한다 (PLAN1 §운영 도입 전제 조건). 본 자동 구현 단계에서는 V6 SQL 산출물 작성만 수행하고, 환경 적용·검증은 별도 배포 절차로 분리한다.

- [x] 사전 카운트 SQL 3건 실행하여 NULL 행 수 확인 (PLAN1 §Phase 1 1번 — psql 또는 IDE 에서 SELECT count 3건). **배포 시 운영자 수행**
- [x] `common/src/main/resources/db/init/V6__pumpcontrol_null_policy.sql` 신규 작성 (PLAN1 §DB 설계 변경 SQL — 백필 + SET NOT NULL 5건 + COMMENT 갱신)
- [x] V6 SQL 로컬 DB 실행 (psql -f 또는 IDE 에서 V1~V5 적용 상태에 V6 추가 적용). **배포 시 운영자 수행**
- [x] `ai_drvn_mod_h_202604` 등 자식 6개 파티션의 new_ai_drvn_mod / new_ai_mode_cd 가 NOT NULL 로 전파되었는지 확인 (psql `\d+ ai_drvn_mod_h_202604`). **배포 시 운영자 수행**

### Phase 2: 엔티티 코드 변경 (common 모듈)

- [x] `common/src/main/java/com/mo/swtp/pump/domain/Pump.java` 수정 — `rated_head`, `rated_flwrt` `@Column` 에 `nullable = false` 추가
- [x] `common/src/main/java/com/mo/swtp/pump/domain/DistributionWaterTank.java` 수정 — `min_req_prsr` `@Column` 에 `nullable = false` 추가
- [x] `common/src/main/java/com/mo/swtp/ai/domain/AiDrvnModeHistory.java` 수정 — `new_ai_drvn_mod`, `new_ai_mode_cd` `@Column` 에 `nullable = false` 추가 (`prev_*` 2컬럼은 변경 없음)
- [x] `common/src/main/java/com/mo/swtp/ai/domain/TransitionReason.java` 수정 — `SYSTEM_INIT` enum 값 추가 + Javadoc 한 줄

### Phase 3: 테스트 점검 (common · api 모듈)

각 테스트의 fixture 가 위 5컬럼을 NULL 로 생성하는지 확인. NULL 의존 케이스가 있다면 안전 기본값(0 / `AiDrvnModeType.AI` / `AiSystemModeCode.MANUAL`) 으로 수정. 신규 테스트 추가는 본 작업 범위 외.

> **점검 결과 (자동 실행 단계)**: 8개 테스트 모두 정적 팩토리 호출 시 NOT NULL 인자를 항상 채워서 사용 — NULL 의존 fixture 없음, 수정 불필요.

- [x] `common/src/test/java/com/mo/swtp/pump/domain/PumpTest.java` 점검 (NULL 의존 없음)
- [x] `common/src/test/java/com/mo/swtp/ai/domain/AiDrvnModeTest.java` 점검 (AiDrvnModeHistory 미사용 — 영향 없음)
- [x] `api/src/test/java/com/mo/swtp/ai/service/AiModeServiceTest.java` 점검 (recordHistory 가 prev/new 모두 채움 — 영향 없음)
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpInterlockScenarioTest.java` 점검 (PumpInterlock 만 사용 — 영향 없음)
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpOperationModeScenarioTest.java` 점검 (AiDrvnModeHistory Mock 처리 — 영향 없음)
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpControlServiceTest.java` 점검 (PumpControlHistory 만 직접 사용 — 영향 없음)
- [x] `api/src/test/java/com/mo/swtp/pump/web/PumpControlControllerTest.java` 점검 (영향 컬럼 미사용)
- [x] `api/src/test/java/com/mo/swtp/pump/PumpControlIntegrationTest.java` 점검 (Pump.create 인자 30/500 채움, AiDrvnMode.create 사용 — 영향 없음)

### Phase 4: 빌드 검증

- [x] `./gradlew.bat :common:build` 실행 성공 확인 (BUILD SUCCESSFUL in 16s)
- [x] `./gradlew.bat :api:build` 실행 성공 확인 (BUILD SUCCESSFUL in 38s)
- [x] `./gradlew.bat clean build` 전체 통과 확인 (BUILD SUCCESSFUL in 58s, 23 actionable tasks)

## 산출물
- [결과](../../../results/20260425/pumpcontrol_null_alignment/RESULT1.md)
