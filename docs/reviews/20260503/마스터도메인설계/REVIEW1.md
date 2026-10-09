---
status: draft
created: 2026-05-04
updated: 2026-05-06
---
# 마스터도메인설계 — 사이클 1 코드 리뷰

## 관련 결과
- [결과](../../../results/20260503/마스터도메인설계/RESULT1.md)

## 리뷰 범위

본 사이클의 변경 51건 (신규 33 + 수정 44 + 삭제 7) 을 대상으로 ROOT/backend 룰 정합·도메인 룰 4영역·DB 운영 룰·테스트 전략·코딩 디시플린 4원칙을 점검한다.

| 카테고리 | 건수 | 주요 파일 |
|---------|------|---------|
| 신규 enum | 5 | `FacilityType`·`EquipType`·`TagMeasurementType`·`IoCode`·`QualityCode` |
| 신규 DDL + V2~V6 갱신 | 10 | `V6_1__~V6_5__` 5건 + `V1`(폐기)·`V2`·`V4`·`V6` |
| 신규 엔티티 13 + Repository 4 | 17 | `facility/`·`instrument/`·`tag/`·`raw/` 패키지 |
| pump 잔존 FK 변경 + 폐기 6 | 13 | PumpInterlock·PumpCmbn·PumpCmbnDetail(+Id)·PumpControlHistory·PumpPredictionResult |
| AI/Scheduler 종속 변경 | 7 | AiDrvnMode·AiDrvnModeHistory·AiModeService·AiPredictionService·AiServerClient·AI DTO 3·AiModeTransitionScheduler |
| 테스트 정합 | 10 수정 + 1 폐기 | InterlockValidatorTest 외 9건 + PumpTest 삭제 |

**ANALYZE-룰 정합성 점검 수행** — `docs/analyze/20260502/마스터도메인설계/ANALYZE1.md` 라인 345~ 룰 갱신 지시서 9건 모두 `[x]` 완료 상태. 직전 커밋 `8ff1213` 으로 룰 파일이 별도 반영되었으므로 본 사이클 변경 파일에 룰 파일 미포함은 정상. 본 REVIEW 는 그 룰 갱신 결과가 코드·DDL 에 올바르게 구현되었는지 검증한다.

**재검토 (2026-05-06)** — `/dev:review` 재호출에 따른 발견 사항 상태 갱신. 권고 2 해소 확인 (오판정 정정), 블로커 1 + 권고 1·3 + 참고 1·2 미해소 유지. 추가 코드 리뷰 (인터록 재검사 경로·안전 정지 시퀀스·JPA JOINED 다형성·BaseEntity 4 immutable·ErrorCode 규약) 결과 신규 발견 0건.

## 발견 사항

> 카테고리: 복잡도 과잉 / 도메인 룰 위반 / 보안 / 성능 / 테스트 누락 / 기타. 심각도: 높음(블로커) / 중간(권고) / 낮음(참고).

### 높음 (블로커) — 1건

| 심각도 | 카테고리 | 위치 | 설명 |
|-------|---------|------|------|
| 높음 | 도메인 룰 위반 | `common/src/main/java/com/mo/swtp/raw/domain/RawData.java` `changeQualityCd` 메서드 + `@PreUpdate` 검증 | PLAN1 §도메인 룰 (PLAN 제약 — 필수 명시) 마지막 항목은 "INSERT-only 컬럼 (`tag_srl_no`·`acq_dtm`·`raw_val`)·**`corr_val` 만 갱신 허용**" 으로 명시. 본 구현은 `corr_val` 외에 `quality_cd` 갱신 메서드 (`changeQualityCd(QualityCode)`) 를 추가했고, `@PreUpdate` immutable 검증 (`onPreUpdateValidateImmutable`) 도 `tag_srl_no·acq_dtm·raw_val` 3개만 차단하여 `quality_cd` 변경을 묵인한다. `db/partitioning-and-retention.md §1` §`rawdata_1m_h` BaseEntity 4 적용 정책도 "`corr_val` 만 갱신 허용" 을 재확인. `ot-integration.md §3` QUALITY 코드 처리상 사후 이상치 재판정으로 `quality_cd` 변경이 필요한 업무 시나리오는 존재하나, 그 경우에도 PLAN 에 명시되어야 한다. 현재 상태는 PLAN 범위 이탈이거나 immutable 검증 미비 중 하나로, 도메인 안전 데이터 무결성 직결 항목이라 블로커 격상. 해소 방향 두 가지: ① `quality_cd` 갱신을 의도된 정책으로 인정 — PLAN 도메인 룰 갱신 + RESULT §계획 외 변경 §의도 추가 + `@PreUpdate` 에서 `quality_cd` 는 비교 제외 명시. ② immutable 강제 — `changeQualityCd` 메서드 제거 + `@PreUpdate` 에 `quality_cd` 스냅샷 비교 추가. |

### 중간 (권고) — 3건

| 심각도 | 카테고리 | 위치 | 설명 |
|-------|---------|------|------|
| 중간 | 기타 (정밀한 수정) | `api/src/main/resources-env/dev/application.yml` | RESULT1 §계획 외 변경 §우연 1 로 식별된 CORS `allowed-origins` 변경 (`http://localhost:5173,http://localhost:3000` 추가). 본 사이클 외 잔재로 `coding-discipline.md §3` 정밀한 수정 + §3.1 데드 코드 정책 (계획 외 변경 직접 포함 금지) 위반. 해소: `git reset HEAD api/src/main/resources-env/dev/application.yml` 으로 unstage 후 별도 `chore: dev 프로파일 CORS allowed-origins 정합` 커밋으로 분리. 본 사이클 커밋에 포함하려면 RESULT §계획 외 변경 §의도로 이동 + 커밋 메시지에 사유 명기 필수. |
| 중간 | 테스트 누락 | `test-strategy.md §5.2.3` 의무 케이스 4번 (SCADA_TIMEOUT) | RESULT1 §테스트 결과는 `PumpOperationModeScenarioTest` 통과를 명시하고, `PumpOperationModeScenarioTest` Javadoc 은 SCADA_TIMEOUT 의무 케이스를 `AiModeTransitionSchedulerTest` 에 책임 분담했다고 기술. 그러나 `AiModeTransitionSchedulerTest` 가 실제로 SCADA_TIMEOUT 경로의 GREEN 통과를 검증하는지 RESULT 가 명시하지 않음. 본 사이클 기준 `AiModeTransitionScheduler` 의 `pwtfId → facilityId` 변경은 적용되었으나 단위 테스트 신규 작성·갱신은 RESULT §변경 사항에 보고된 바 없다 — 즉 `AiModeTransitionSchedulerTest` 자체가 미존재할 가능성. `test-strategy.md §5.2.3` 의무 케이스 4번이 GREEN 으로 검증된 적이 없으면 본 사이클의 운전 모드 시나리오 의무 3종 중 1건 누락 상태. **재검토 (2026-05-06) — 해소 확인**: `scheduler/src/test/java/com/mo/swtp/scheduler/pump/AiModeTransitionSchedulerTest.java` 가 8케이스로 존재하며, 73-89줄 `SCADA_5분_초과_AI_AUTO_상태에서_SEMI_AUTO_로_강제_전환된다()` 가 `TransitionReason.SCADA_TIMEOUT` GREEN 검증. RESULT1 §테스트 결과 한 줄 보강 권고는 유효하나 의무 케이스 누락은 사실이 아님 — 본 권고 항목은 해소 처리. |
| 중간 | 도메인 룰 (인용 근거) | `common/src/main/java/com/mo/swtp/raw/domain/RawData.java` `@PrePersist`/`@PreUpdate`/`@PostLoad` 메서드 | ROOT `coding-discipline.md §2.5` 면책 영역 인용 근거 명기 의무. 본 메서드는 SCADA 원본값 immutable 단일 흐름으로 `ot-integration.md §3` 데이터 처리 정책에 해당하는 면책 영역 후보다. 현 시점 메서드 본문은 50줄 미만으로 §2.1 정량 기준 위반은 아니나, RESULT1 §REVIEW 단계 점검 권장사항 1번이 본 항목을 별도 점검 대상으로 식별했다. 블로커 항목 (changeQualityCd) 해소 시 `@PreUpdate` 본문이 확장될 수 있으므로 면책 여부 판단 기준 주석을 미리 명기하면 향후 운영 안전. 코드 (163행 부근) 메서드 Javadoc 에 `// §2.5 면책 (ot-integration.md §3 SCADA 원본값 immutable 단일 흐름) — 현 50줄 미만이라 정량 기준 면책 불필요, 향후 quality_cd·corr_val 보정 단계 추가 시 면책 적용` 형식의 한 줄 추가 권고. |

### 낮음 (참고) — 2건

| 심각도 | 카테고리 | 위치 | 설명 |
|-------|---------|------|------|
| 낮음 | 기타 | `common/src/main/java/com/mo/swtp/raw/domain/RawDataId.java` | `@AllArgsConstructor` 접근 수준 미명시로 기본 `PUBLIC` 적용. JPA IdClass 스펙은 public no-arg constructor 만 요구하므로 기능 영향 없음. `entity-patterns.md` §기본 엔티티 패턴은 전체 필드 생성자를 필요 시 `PRIVATE` 권고하나 IdClass 의 사용처 (JPA 내부 + 정적 팩토리 호출) 가 `PRIVATE` 으로 충분한지 별도 검토. 본 사이클 정합성에 영향 없음 — 참고. |
| 낮음 | 기타 (테스트 환경) | `api/src/test/java/com/mo/swtp/pump/PumpControlIntegrationTest.java` + RESULT1 §테스트 결과 1 skipped | `PumpControlIntegrationTest` 가 `@EnabledIfEnvironmentVariable(SWTP_INTEGRATION_DB=true)` 로 CI 환경 SKIP. PLAN1 §도메인 룰 §인터록 재검사 경로 보존 의무는 단위 시나리오 (`PumpInterlockScenarioTest` 6건 + `PumpControlServiceTest` 다중펌프 시나리오) 로 1차 안전망 확보되었으나, 실 PostgreSQL + V6_1~V6_5 적용 환경의 end-to-end 검증은 운영 도입 전 별도 사이클 필요. 본 사이클 approve 차단 사유 아니며 RESULT §후속 작업 3번이 명시. |

## 개선 제안

1. **블로커 (`changeQualityCd` vs PLAN 도메인 룰 불일치) 해소** — 두 방향 중 하나로 의사 결정. ① 인정 시 PLAN1 §도메인 룰 (PLAN 제약) 마지막 항목을 "INSERT-only 컬럼·`corr_val` 및 `quality_cd` 만 갱신 허용 — `quality_cd` 는 `ot-integration.md §3` 사후 이상치 재판정 (예: BAD → UNCERTAIN 격상) 으로 갱신" 으로 갱신 + `@PreUpdate` 주석에 명시. ② 강제 시 `RawData.changeQualityCd` 제거 + `@PreUpdate` 에 `quality_cd` 스냅샷 비교 추가. 현 시점 도메인 expert 권고는 미정 — wtp-domain-expert + wtp-dba-reviewer 재소집 검토 권고.

2. **`application.yml` CORS 변경 별도 사이클 분리** — 본 PR 에서 unstage 처리 후 별도 `chore:` 커밋으로 분리. 분리 불가 시 RESULT §계획 외 변경 §우연 → §의도 이동 + 커밋 메시지 명기.

3. **`AiModeTransitionSchedulerTest` SCADA_TIMEOUT 케이스 추가/검증** — 신규 작성 또는 기존 케이스 GREEN 통과 확인 후 RESULT §테스트 결과 한 줄 추가. `test-strategy.md §5.2.3` 의무 4건 모두 GREEN 보장.

4. **ai-server Pydantic 스키마 동기화 트리거** — `swtp/ai-server/app/schemas/PumpPredictionRequest` 의 `pwtf_id` → `facility_id` 변경을 본 PR 또는 즉시 후속 PR 로 진행. monorepo 단일 PR 동기화 원칙 (`ot-integration.md §6.6`) 준수. RESULT §후속 작업 2번이 식별했으나 실행 시점 미정.

5. **§2.5 면책 인용 근거 주석 추가** — `RawData` immutable 검증 메서드 Javadoc 에 면책 영역 판단 기준 한 줄 명기. 향후 메서드 확장 시 운영 안전성 확보.

## 결론

- **블로커 1건**: `changeQualityCd` vs PLAN 도메인 룰 (`corr_val` 만 갱신 허용) 불일치 — 도메인 안전 데이터 무결성 직결로 격상.
- **권고 3건**: application.yml CORS 분리 / SCADA_TIMEOUT 의무 케이스 GREEN 명시 / §2.5 면책 인용 주석.
- **참고 2건**: RawDataId @AllArgsConstructor 접근 수준 / PumpControlIntegrationTest SKIP.

본 REVIEW 는 `status: draft` 유지. 블로커 해소를 위한 **Fix Cycle (사이클 2) 진입 필요**. 블로커 텍스트에 도메인 정합성 키워드 (`용어`/`약어`/`중복 정의`/`네이밍 충돌`/`엔티티 통합`) 미포함이라 ANALYZE 재진입 불요 — `doc-harness/README.md` §수정 사이클 §Fix Cycle 감지 알고리즘에 따라 **PLAN2 직행 (ANALYZE 스킵)**.

전반 구현 품질 정상. 인터록 재검사 경로 (`InterlockValidator.validateOrThrow` + `PumpControlService.preflightAllPumps`) · V6_5 BRIN/복합 인덱스 4단계 DDL 순서 · `instanceof` 기반 자식 종류 필터 강제 (`Pump`/`PurifiedWaterTank`) · 컬럼 COMMENT 의무 (V6_1~V6_5 + V2) · ANALYZE 룰 갱신 9건 반영 모두 정합. PLAN1 의 도메인 룰 마지막 항목 (`corr_val` 만 갱신) 단일 항목의 코드/PLAN 정합 정렬만 해소되면 approve 가능.

### 재검토 결과 (2026-05-06)

- **권고 2 (SCADA_TIMEOUT 의무 케이스) 해소 확인** — `AiModeTransitionSchedulerTest` 8케이스 존재 + 73-89줄 `SCADA_TIMEOUT` 강제 전환 GREEN 검증. 초기 REVIEW1 의 "테스트 미존재 가능성" 추정은 오판정 (정정).
- **블로커 1 (`changeQualityCd`) 미해소** — 코드 Javadoc (`RawData.java` 161줄) 은 옵션 ① (quality_cd 갱신 인정) 방향이나 PLAN1 §도메인 룰 (134줄) + RESULT1 §계획 외 변경 §의도 미반영으로 PLAN-코드 정합성 미정렬. PLAN2 진입 시 다음 두 옵션 중 사용자 결정 필요:
  - 옵션 ①: PLAN1.md §도메인 룰 마지막 항목 갱신 ("`corr_val` 및 `quality_cd` 만 갱신 허용 — `quality_cd` 는 `ot-integration.md §3` 사후 이상치 재판정 (BAD → UNCERTAIN 격상) 으로 갱신") + RESULT1 §계획 외 변경 §의도 추가 + `@PreUpdate` 본문에 `quality_cd` 비교 제외 의도 주석
  - 옵션 ②: `RawData.changeQualityCd` 메서드 제거 + `@PreUpdate` 에 `quality_cd` 스냅샷 비교 추가 + Javadoc 161줄 "corr_val·quality_cd 만 갱신 허용" → "corr_val 만 갱신 허용" 으로 정렬
- **권고 1 (CORS) 미해소** — `dev/application.yml` 27줄 `localhost:5173,localhost:3000` 그대로. 별도 chore 사이클 분리 또는 RESULT1 §의도 이동 결정 필요.
- **권고 3 (§2.5 면책 주석) 미해소** — `RawData @PreUpdate` Javadoc 미보강. 메서드 50줄 미만이라 약한 권고, 향후 확장 대비.
- **참고 1·2 변경 없음**.

### 추가 코드 리뷰 결과 (REVIEW1 외 — 신규 발견 0건)

- **인터록 재검사 경로 보존** (`ot-integration.md §5 ⚠️ 절대 금지` 직결): ✓ 정상. `PumpControlService.preflightAllPumps` 가 매 호출마다 `interlockValidator.validateOrThrow` 실행, CircuitBreaker 복구 후에도 우회 경로 없음
- **안전 정지(Safe Stop) 시퀀스**: ✓ 정상. `performSafeStop` + `forceTransition(MANUAL, OUTBOUND_FAIL)` + REQUIRES_NEW 이력 보존
- **JPA JOINED 다형성 패턴** (`entity-patterns.md` §JPA JOINED 패턴): ✓ Facility·Instrument 부모 abstract + UUID PK + `@UniqueConstraint` + 자식 PK 자동 상속 정합
- **외부 할당 PK + Persistable**: ✓ Tag 만 적용 (자연키 `tag_srl_no`)
- **시계열 → 마스터 FK 금지**: ✓ `RawData.tag_srl_no` 논리 참조 (`@Column` 만, FK 없음)
- **BaseEntity 4 immutable 이력 폐기**: ✓ RawData `extends BaseEntity` + 스냅샷 비교 immutable 검증
- **ErrorCode 인터페이스 규약**: ✓ `PumpErrorCode.INTERLOCK_VIOLATION`·`PUMP_NOT_FOUND` 등 사용, `String message` 필드 미추가

### 다음 단계

`status: draft` 유지. 블로커 1 해소를 위해 **Fix Cycle (사이클 2) 진입 필요** — 블로커 텍스트에 도메인 정합성 키워드 (`용어`/`약어`/`중복 정의`/`네이밍 충돌`/`엔티티 통합`) 미포함이라 ANALYZE 재진입 불요, **PLAN2 직행** (`doc-harness/README.md` §수정 사이클 §Fix Cycle 감지 알고리즘).
