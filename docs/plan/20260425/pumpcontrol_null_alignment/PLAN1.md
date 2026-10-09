---
status: approved
created: 2026-04-25
updated: 2026-04-25
---
# pumpcontrol 도메인 DDL NULL 정책 정합성 — 계획

## 목적

직전 ANALYZE1 (`docs/analyze/20260425/pumpcontrol_null_alignment/ANALYZE1.md`, status: approved) 의 결정 사항인 5컬럼 NOT NULL 전환 + `transition_reason` enum 1값 추가를 무중단 마이그레이션 절차(`db-indexing-and-migration.md §2`) 로 적용한다. 코드 변경은 엔티티 `@Column(nullable=false)` 명시 + Java enum 값 추가에 한정한다.

## 배경

- ANALYZE1 §회의록 에서 5인 팀 검토를 거쳐 사용자 결정으로 확정된 5컬럼 NOT NULL 정책. 이를 V1/V2 SQL 의 NULL 허용 상태에서 NOT NULL 로 전환.
- **현 시점 운영 데이터 0건 가정** (커밋 `c0658c8` 도입 후 첫 통합 작업). 백필 UPDATE 는 안전 기본값으로 단일 스크립트 가능.
- **`SYSTEM_INIT` enum 값** 은 Java 와 룰 문서에 추가하되, 발행 경로(시스템 초기 등록 시드 로직) 는 본 작업 범위 외 — `AiModeService` 에 `ai_drvn_mod_p` 행 시드 메서드가 부재하므로 별도 후속 작업에서 결정.
- ANALYZE1 §본 작업 범위 경계 의 분리 작업 3건 (`use_yn_consistency` · `pump_rated_history` · `ot_integration_inbound`) 은 본 PLAN 범위 외.

## 범위

### 포함

- DDL 마이그레이션 SQL 1건 신규 작성 (백필 + `SET NOT NULL` 5건 + 코멘트 갱신)
- 엔티티 클래스 3개 파일 `@Column(nullable=false)` 추가 (5컬럼)
- enum 클래스 1개 파일 `SYSTEM_INIT` 값 추가
- 영향받는 단위 테스트 점검·수정 (NULL 의존 케이스 식별)
- 빌드 검증 (`./gradlew.bat clean build`)

### 제외

- `AiModeService` 시스템 초기 등록 시드 로직 신규 구현 — 시드 데이터 INSERT 경로 자체가 부재한 상태이므로 별도 작업 (`ai_mode_seed_strategy` 또는 운영 절차서 결정) 으로 분리
- `InterlockValidator` 의 `dwt_m.min_req_prsr` 직접 참조 로직 — 현재 코드는 `pump_interlock_p` 만 사용하며, dwt 압력 fail-safe 는 `ot_integration_inbound` 후속 작업 영역
- 마스터 등록·수정 API (`PumpUpsertDto`, `DwtUpsertDto`) — 본 시점 미구현. NOT NULL 위반 입력 검증은 향후 등록 API 도입 시 `@NotNull` 검증으로 추가
- `use_yn` 일관성 작업 — 별도 ANALYZE 슬러그 `use_yn_consistency` 로 분리

## 도메인 모델

본 PLAN 은 신규 엔티티·테이블·필드 없음. 기존 컬럼 5종의 NULL 정책 변경 + 1개 enum 값 추가만 발생. 변경 명세는 §DB 설계 변경 / §구현 방향 §3-1 참조.

| 분류 | 변경 대상 | 변경 내용 |
|------|---------|---------|
| 엔티티 필드 | `Pump.ratedHead`, `Pump.ratedFlwrt` | `@Column(nullable = false)` 추가 |
| 엔티티 필드 | `DistributionWaterTank.minReqPrsr` | `@Column(nullable = false)` 추가 |
| 엔티티 필드 | `AiDrvnModeHistory.newAiDrvnMod`, `AiDrvnModeHistory.newAiModeCd` | `@Column(nullable = false)` 추가 |
| enum 값 | `TransitionReason` | `SYSTEM_INIT` 신규 값 추가 (5종) |

> `AiDrvnModeHistory.prevAiDrvnMod` · `prevAiModeCd` 는 NULL 허용 유지 (시스템 초기 진입 표현, ANALYZE1 결론).

## DB 설계 변경

### 변경 대상 테이블·컬럼

| 테이블 | 컬럼 | 현행 | 변경 후 | 백필 기본값 |
|--------|------|------|--------|----------|
| `pump_m` | `rated_head` | NUMERIC(15,4) NULL | NUMERIC(15,4) **NOT NULL** | 0 (운영 절차서로 실제값 입력 의무 명시) |
| `pump_m` | `rated_flwrt` | NUMERIC(15,4) NULL | NUMERIC(15,4) **NOT NULL** | 0 |
| `dwt_m` | `min_req_prsr` | NUMERIC(15,4) NULL | NUMERIC(15,4) **NOT NULL** | 0 ("제약 없음" 의미) |
| `ai_drvn_mod_h` | `new_ai_drvn_mod` | VARCHAR(20) NULL | VARCHAR(20) **NOT NULL** | `'AI'` (이력 행 0건 가정 — 백필 미수행 가능) |
| `ai_drvn_mod_h` | `new_ai_mode_cd` | VARCHAR(20) NULL | VARCHAR(20) **NOT NULL** | `'0'` |

### 무중단 마이그레이션 전략

`db-indexing-and-migration.md §2` 3단계 중 컬럼은 이미 존재하므로 **2단계(백필) + 3단계(SET NOT NULL)** 만 적용.

> **⚠️ 파티션 테이블 안전성 (DBA 검토 블로커 해소 — 2026-04-25)**:
> `ai_drvn_mod_h` 는 V2 에서 `PARTITION BY RANGE (rgstr_dtm)` 로 생성된 파티션 부모 테이블이며 V3 에서 6개 자식 파티션(`_202604` ~ `_202609`) 이 생성된다. PostgreSQL 11+ 에서 부모 `ALTER COLUMN SET NOT NULL` 은 자식 파티션에 자동 전파되나, **자식 파티션에 NOT NULL 위반 행이 존재하면 부모 DDL 자체가 실패한다**. V6 의 백필 UPDATE 가 NULL 을 모두 채운 뒤 SET NOT NULL 을 실행하므로 동일 트랜잭션 내에서는 안전하나, 운영 적용 시 **사전 카운트 확인** 으로 영향 범위를 파악해야 한다 (Phase 1 사전 검증 단계 참조).

```sql
-- common/src/main/resources/db/init/V6__pumpcontrol_null_policy.sql
-- ============================================================================
-- pumpcontrol NULL 정책 정합성 (pumpcontrol_null_alignment ANALYZE1, 2026-04-25)
-- ----------------------------------------------------------------------------
-- 5컬럼 NOT NULL 전환 — db-indexing-and-migration.md §2 3단계 절차 (2~3단계만)
-- 운영 데이터 0건 가정 (V1/V2 도입 후 첫 정합성 작업) — 백필 단일 스크립트 가능
-- 파티션 테이블 ai_drvn_mod_h 는 부모 SET NOT NULL 이 자식 6개 파티션에 자동 전파됨
-- ============================================================================

-- 2단계: 백필 (기존 NULL 값을 안전 기본값으로 설정)
UPDATE pump_m
   SET rated_head  = COALESCE(rated_head, 0),
       rated_flwrt = COALESCE(rated_flwrt, 0)
 WHERE rated_head IS NULL OR rated_flwrt IS NULL;

UPDATE dwt_m
   SET min_req_prsr = COALESCE(min_req_prsr, 0)
 WHERE min_req_prsr IS NULL;

UPDATE ai_drvn_mod_h
   SET new_ai_drvn_mod = COALESCE(new_ai_drvn_mod, 'AI'),
       new_ai_mode_cd  = COALESCE(new_ai_mode_cd, '0')
 WHERE new_ai_drvn_mod IS NULL OR new_ai_mode_cd IS NULL;

-- 3단계: NOT NULL 제약 추가
ALTER TABLE pump_m         ALTER COLUMN rated_head      SET NOT NULL;
ALTER TABLE pump_m         ALTER COLUMN rated_flwrt     SET NOT NULL;
ALTER TABLE dwt_m          ALTER COLUMN min_req_prsr    SET NOT NULL;
ALTER TABLE ai_drvn_mod_h  ALTER COLUMN new_ai_drvn_mod SET NOT NULL;
ALTER TABLE ai_drvn_mod_h  ALTER COLUMN new_ai_mode_cd  SET NOT NULL;

-- 코멘트 갱신 (신규 정책 명시)
COMMENT ON COLUMN pump_m.rated_head    IS '정격 양정 m (NOT NULL — 명판값 항상 존재, AI 예측 정규화 인자)';
COMMENT ON COLUMN pump_m.rated_flwrt   IS '정격 유량 m³/h (NOT NULL — 동일 사유)';
COMMENT ON COLUMN dwt_m.min_req_prsr   IS '최소 요구 압력 kgf/cm² (NOT NULL — 인터록 평가 NULL/미입력 구별 보장)';
```

> **⚠️ 절대 금지** (룰 §2): `ALTER TABLE ... ADD COLUMN col NOT NULL DEFAULT val` — 본 케이스는 컬럼 추가 아닌 기존 컬럼의 NULL 정책 변경이므로 해당 없음.

> **DDL 파일 명명**: 기존 V1~V5 와 동일 컨벤션. `V6__pumpcontrol_null_policy.sql` (Flyway 미도입이지만 명명 일관성 유지).

> **기존 V1/V2 SQL 직접 수정 금지**: 배포 불변 원칙. NULL 정책 변경은 V6 마이그레이션으로만 적용.

## 구현 방향

### Phase 1: DDL 마이그레이션 (common 모듈)

1. **사전 카운트 검증** (DBA 검토 블로커 해소 — 2026-04-25): V6 실행 전 다음 SQL 로 영향 범위 파악. 결과 모두 0 이면 운영 데이터 없음 가정 성립 → V6 안전 진행. 0 이 아니면 백필 UPDATE 가 적용될 행 수가 사전 보고됨 (V6 자체는 동일하게 동작):
   ```sql
   SELECT count(*) AS pump_m_null_count
     FROM pump_m WHERE rated_head IS NULL OR rated_flwrt IS NULL;
   SELECT count(*) AS dwt_m_null_count
     FROM dwt_m WHERE min_req_prsr IS NULL;
   SELECT count(*) AS ai_drvn_mod_h_null_count
     FROM ai_drvn_mod_h WHERE new_ai_drvn_mod IS NULL OR new_ai_mode_cd IS NULL;
   ```
2. `common/src/main/resources/db/init/V6__pumpcontrol_null_policy.sql` 신규 작성 — §DB 설계 변경 의 SQL 그대로
3. 로컬 DB 에서 실행하여 정합성 확인 — 기존 V1~V5 가 적용된 상태에서 V6 추가 실행. `ai_drvn_mod_h` 파티션 부모 SET NOT NULL 이 자식 6개에 자동 전파되었는지 확인:
   ```sql
   \d+ ai_drvn_mod_h_202604
   -- new_ai_drvn_mod / new_ai_mode_cd 가 NOT NULL 로 표시되는지 확인
   ```

### Phase 2: 엔티티 코드 변경 (common 모듈)

3-1. **`Pump.java`** — `rated_head`, `rated_flwrt` 의 `@Column` 에 `nullable = false` 추가
- 정적 팩토리 `Pump.create()` 시그니처 변경 없음 (이미 인자 필수)
- `changeInfo()` 시그니처 변경 없음 (null 인자는 "기존값 유지" 의미로 유지)

3-2. **`DistributionWaterTank.java`** — `min_req_prsr` 의 `@Column` 에 `nullable = false` 추가
- 정적 팩토리·`changeInfo()` 동일

3-3. **`AiDrvnModeHistory.java`** — `new_ai_drvn_mod`, `new_ai_mode_cd` 의 `@Column` 에 `nullable = false` 추가
- `prev_*` 2컬럼은 변경 없음 (NULL 허용 유지)
- 정적 팩토리 `create()` 시그니처 변경 없음 (인자는 이미 필수, prev/new 모두 받음)

3-4. **`TransitionReason.java`** — `SYSTEM_INIT` enum 값 추가 + Javadoc 한 줄 추가
```java
/** 시스템 최초 등록 — `ai_drvn_mod_p` 행 시드 시 첫 이력 행 표현. `prev_*` NULL 허용 (이후 전환은 항상 채워짐) */
SYSTEM_INIT
```

### Phase 3: 영향 테스트 점검 (common, api 모듈)

4-1. **단위 테스트 영향 점검** — 다음 테스트가 NULL 데이터로 객체 생성하는지 확인:
- `common/src/test/java/com/mo/swtp/pump/domain/PumpTest.java`
- `common/src/test/java/com/mo/swtp/ai/domain/AiDrvnModeTest.java`
- `api/src/test/java/com/mo/swtp/ai/service/AiModeServiceTest.java`
- `api/src/test/java/com/mo/swtp/pump/service/PumpInterlockScenarioTest.java`
- `api/src/test/java/com/mo/swtp/pump/service/PumpOperationModeScenarioTest.java`
- `api/src/test/java/com/mo/swtp/pump/service/PumpControlServiceTest.java`
- `api/src/test/java/com/mo/swtp/pump/web/PumpControlControllerTest.java`
- `api/src/test/java/com/mo/swtp/pump/PumpControlIntegrationTest.java`

4-2. NULL 데이터 의존 케이스 발견 시 **테스트 픽스처를 안전 기본값(0 / 'AI' / '0') 으로 수정**. 단순 fixture 수정에 한정 — 새 테스트 추가는 본 작업 범위 외.

### Phase 4: 빌드·통합 검증

5-1. `./gradlew.bat :common:build` 성공 — 엔티티 변경이 common 모듈에 한정되므로 우선 검증
5-2. `./gradlew.bat :api:build` 성공 — common 의 변경이 api 의 컴파일·테스트에 영향 주는지 확인
5-3. `./gradlew.bat clean build` 전체 통과
5-4. `PumpControlIntegrationTest` 가 NULL 데이터를 사용하지 않으면 기존 시나리오 그대로 통과 예상

## 테스트 전략

| 계층 | 검증 항목 | 도구 |
|------|---------|------|
| 단위 (common) | `Pump.create()` / `DistributionWaterTank.create()` 호출이 NOT NULL 인자로 정상 작동 | JUnit 5 + Mockito (`PumpTest`, `AiDrvnModeTest`) |
| 단위 (api) | `AiModeService.changeUserIntent` / `forceTransitionInternal` 가 `new_*` NOT NULL 채움 검증 (이미 동작) | JUnit 5 + Mockito (`AiModeServiceTest`) |
| 통합 (api) | `PumpControlIntegrationTest` 의 데이터 셋업이 신규 NOT NULL 컬럼을 모두 채움 | `@SpringBootTest(NONE)` + `@Transactional` |
| 빌드 | `./gradlew.bat :api:test`, `:common:test`, 전체 `build` | Gradle |

> 신규 테스트 추가 없음. 기존 fixture 의 NULL 의존 케이스만 수정.

## 제외 사항

- 시스템 초기 등록 시드 로직(`AiModeService` 에 `seedInitialMode` 메서드 신설 + `SYSTEM_INIT` 사유 발행) → 별도 작업 `ai_mode_seed_strategy` (또는 운영 절차서 결정)
- `dwt_m.min_req_prsr` 의 InterlockValidator fail-safe 차단 로직 → 별도 작업 `ot_integration_inbound`
- 마스터 등록·수정 API 도입·`@NotNull` Validator 강화 → 별도 작업 (마스터 CRUD API 도입 시점)
- `pump_rated_history` 정격값 변경 이력 → 별도 작업
- `use_yn` 일관성 (CHAR→VARCHAR · CHECK · DEFAULT) → 별도 ANALYZE 슬러그 `use_yn_consistency`

## 운영 도입 전제 조건 (DBA 권고 반영 — 2026-04-25)

본 작업 적용 후 운영 환경에서 다음 순서를 지켜야 부작용을 회피할 수 있다:

1. **AI 예측 잡 활성화 전 정격값 입력 완료**: V6 백필이 `pump_m.rated_head=0`, `rated_flwrt=0` 으로 채우므로, 이 상태에서 AI 예측 모델(`AiServerClient` → Python AI 서비스) 이 기동되면 정규화 인자 0 입력으로 divide-by-zero 또는 극단 예측값을 반환할 수 있다. 운영 절차서에 "**명판값 UPDATE 완료 후 AI 예측 스케줄러 활성화**" 순서를 명시한다.
2. **Python AI 서비스 0값 방어 로직 검토**: 본 PLAN 범위 외이지만, AI 서버 측 입력 검증으로 0값 거부 또는 기본 정상 펌프 정격값 fallback 추가 권고 (별도 협의).
3. **운영 데이터 첫 도입 후 `ANALYZE` 실행**: PostgreSQL 통계는 SET NOT NULL 만으로 자동 갱신되지 않는다. 마스터 데이터 첫 입력 직후 `ANALYZE pump_m; ANALYZE dwt_m; ANALYZE ai_drvn_mod_h;` 실행을 운영 절차서에 명시.

## 예상 산출물

| 파일 | 변경 유형 | 비고 |
|------|---------|------|
| `common/src/main/resources/db/init/V6__pumpcontrol_null_policy.sql` | 신규 | 백필 + SET NOT NULL 5건 + COMMENT 갱신 |
| `common/src/main/java/com/mo/swtp/pump/domain/Pump.java` | 수정 | `rated_head`, `rated_flwrt` `@Column(nullable=false)` 추가 |
| `common/src/main/java/com/mo/swtp/pump/domain/DistributionWaterTank.java` | 수정 | `min_req_prsr` `@Column(nullable=false)` 추가 |
| `common/src/main/java/com/mo/swtp/ai/domain/AiDrvnModeHistory.java` | 수정 | `new_ai_drvn_mod`, `new_ai_mode_cd` `@Column(nullable=false)` 추가 |
| `common/src/main/java/com/mo/swtp/ai/domain/TransitionReason.java` | 수정 | `SYSTEM_INIT` 값 추가 + Javadoc |
| 영향 단위 테스트 (common·api) | 점검·필요 시 fixture 수정 | NULL 데이터 의존 케이스만 — 신규 테스트 추가 없음 |

- [태스크](../../../tasks/20260425/pumpcontrol_null_alignment/TASK1.md)

## 부록: 도메인/DB 검토 결과

- **wtp-domain-expert**: 검토 면제 — §도메인 모델 신규 엔티티·테이블·필드 0건 (NULL 정책·enum 값 변경만)
- **wtp-dba-reviewer**: 블로커 0건 (원래 1건 → 본 PLAN 본문 §Phase 1 사전 카운트 검증 + §운영 도입 전제 조건 으로 해소), 권고 1건(rated=0 백필 운영 전제), 참고 2건 (transition_reason DB CHECK 재검토 시점·운영 첫 ANALYZE 실행)
