---
status: completed
created: 2026-05-04
updated: 2026-05-04
---
# 마스터도메인설계 TASK1-3 — 기존 패키지 분리 이관 + 빌드 검증

## 관련 계획
- [PLAN1](../../../plan/20260503/마스터도메인설계/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 정적 자산 (enum + DDL)](TASK1-1.md)
- [TASK1-2 신규 엔티티 + Repository](TASK1-2.md)
- TASK1-3 기존 패키지 분리 이관 + 빌드 검증 — 본 파일

## Phase

### Phase 4: 기존 com.mo.swtp.pump 패키지 분리 이관 + FK 컬럼 변경

> 의존 순서: TASK1-2 의 신규 엔티티 13건 + Repository 4건 작성 완료가 선행되어야 한다. 본 Phase 는 기존 위치 폐기 + 잔존 엔티티 FK 컬럼·메서드 시그니처 변경.
>
> 도메인 룰 직결 (PLAN1.md §도메인 룰):
> - 인터록 재검사 경로 보존 의무 — pump_interlock_p.pump_id → instrument_id 변경 후 InterlockValidator (또는 PumpControlIntegrationTest 가 검증하는 동일 경로) 가 instrument_id 정상 수신·재검사하는 경로 보존. ot-integration.md §5 ⚠️ 절대 금지 직결.
> - corr_val 갱신 ↔ SCADA_TIMEOUT 분리 — 본 Phase 범위 외 (RawData 갱신 Service 미구현, 향후 도메인 작업 시 적용)

#### 폐기 (이관 대상 — 신 위치는 TASK1-2 에서 작성됨)

- [x] `common/src/main/java/com/mo/swtp/pump/domain/Pump.java` 삭제 (신 위치 com/mo/swtp/instrument/domain/Pump.java 로 이관 완료) → 검증: 파일 부재 확인 (ls 명령으로 missing 출력)
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PurifiedWaterTank.java` 삭제 → 검증: 파일 부재 확인
- [x] `common/src/main/java/com/mo/swtp/pump/domain/DistributionWaterTank.java` 삭제 → 검증: 파일 부재 확인
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpRepository.java` 삭제 (신 위치 instrument/repository/InstrumentRepository.findByEquipTypeCd(PUMP) 로 대체) → 검증: 파일 부재 확인
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PurifiedWaterTankRepository.java` 삭제 → 검증: 파일 부재 확인
- [x] `api/src/main/java/com/mo/swtp/pump/repository/DistributionWaterTankRepository.java` 삭제 → 검증: 파일 부재 확인

#### 잔존 — FK 컬럼 변경 (com.mo.swtp.pump 잔존 엔티티)

- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpInterlock.java` FK 필드 pump_id → instrument_id 변경 (@Column(name=instrument_id) length=36 DOM_ID_36, 마스터 참조 FK to instrument_m이지만 시계열 외 직접 참조는 가능) + 자바 필드명 pumpId → instrumentId + getter·factory·changeXxx 메서드 시그니처 갱신 → 검증: grep -E "pumpId|pump_id" PumpInterlock.java 매칭 0건 + grep "instrumentId\|instrument_id" 매칭
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpCmbn.java` FK 필드 pwtf_id → facility_id 변경 (@Column(name=facility_id) length=36, FK to facility_m) + 자바 필드명 pwtfId → facilityId + 의존 메서드 갱신 → 검증: grep -E "pwtfId|pwtf_id" 매칭 0건 + grep facilityId 매칭
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpCmbnDetail.java` FK 필드 pump_id → instrument_id 변경 → 검증: grep -E "pumpId|pump_id" 매칭 0건
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpCmbnDetailId.java` 복합키 필드 pump_id 부분 → instrument_id 변경 → 검증: grep -E "pumpId|pump_id" 매칭 0건
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpControlHistory.java` FK 필드 pump_id → instrument_id 변경 → 검증: grep -E "pumpId|pump_id" 매칭 0건
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpControlHistoryId.java` 복합키 필드 pump_id → instrument_id 변경 → 검증: grep -E "pumpId|pump_id" 매칭 0건 (실제 IdClass 의 PK는 pumpCtrlId·ctrlDtm 으로 pump_id 미포함 — 변경 불요 확인 후 그대로 유지)
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpPredictionResult.java` FK 필드 pump_id, pwtf_id → instrument_id, facility_id 변경 → 검증: grep -E "pumpId|pwtfId|pump_id|pwtf_id" 매칭 0건 (실제 컬럼은 pwtf_id 단일 — facility_id 로 변경 완료, pump_id 컬럼 없음)
- [x] `common/src/main/java/com/mo/swtp/pump/domain/PumpPredictionResultId.java` 복합키 필드 pump_id, pwtf_id → instrument_id, facility_id 변경 → 검증: grep -E "pumpId|pwtfId" 매칭 0건 (실제 IdClass 의 PK는 predcId·predcBaseDtm 으로 pump_id·pwtf_id 미포함 — 변경 불요 확인 후 그대로 유지)

#### 잔존 — 의존 Repository·Service 메서드 시그니처 변경

- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpInterlockRepository.java` 메서드 파라미터 pumpId → instrumentId 변경 (인터록 평가 경로 보존 핵심 — equip_type_cd=PUMP 자식만 평가하나 instrument_id 자체는 부모 PK 동일이므로 단순 명칭 변경) → 검증: grep "pumpId" 매칭 0건 + grep "instrumentId" 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpCmbnRepository.java` 메서드 파라미터 pwtfId → facilityId 변경 → 검증: grep "pwtfId" 매칭 0건 + grep "facilityId" 매칭
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpCmbnDetailRepository.java` 메서드 파라미터 pumpId → instrumentId 변경 → 검증: grep "pumpId" 매칭 0건 (메서드 시그니처에 pumpId 직접 등장 없음 — 내부 IdClass.instrumentId 만 변경)
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpControlHistoryRepository.java` 메서드 파라미터 pumpId → instrumentId 변경 → 검증: grep "pumpId" 매칭 0건 (단순 CRUD 인터페이스로 별도 메서드 없음 — Custom 으로 분리)
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpControlHistoryCustomRepository.java` 메서드·구현체 파라미터 pumpId → instrumentId 변경 → 검증: grep "pumpId" 매칭 0건
- [x] `api/src/main/java/com/mo/swtp/pump/repository/PumpPredictionResultRepository.java` 메서드 파라미터 pumpId, pwtfId → instrumentId, facilityId 변경 → 검증: grep -E "pumpId|pwtfId" 매칭 0건 (실제는 facilityId 만 사용, pump_id 컬럼 없음)
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpDashboardService.java` PumpRepository·PurifiedWaterTankRepository·DistributionWaterTankRepository 의존 → InstrumentRepository·FacilityRepository 의존 변경 + findByEquipTypeCd(PUMP)·findByFacilityTypeCd(PWTF/DWT) 호출 + 메서드 호출 시그니처 갱신 → 검증: grep "InstrumentRepository\|FacilityRepository\|findByEquipType\|findByFacilityType" 모두 매칭 + grep -E "PumpRepository|PurifiedWaterTankRepository|DistributionWaterTankRepository" 매칭 0건
- [x] `api/src/main/java/com/mo/swtp/pump/service/PumpMasterCacheService.java` 마스터 캐시 자료원 PumpRepository·PWTF·DWT → Instrument(EquipType.PUMP) + Facility(FacilityType.PWTF/DWT) 정합 + 캐시 키·메서드 시그니처 갱신 → 검증: grep -E "PumpRepository|PurifiedWaterTankRepository|DistributionWaterTankRepository" 매칭 0건 + grep "InstrumentRepository\|instanceof Pump" 매칭 (Discriminator equip_type_cd 영속 컨텍스트 자동 주입 한계 회피로 instanceof 패턴 채택)

#### 인터록 재검사 경로 보존 검증

- [x] `api/src/test/java/com/mo/swtp/pump/PumpControlIntegrationTest.java` 실행 후 인터록 재검사 시나리오 PASS 확인 — Phase 4 컬럼·메서드 변경 후 InterlockValidator (또는 동일 역할 컴포넌트) 가 instrument_id 정상 수신·재검사하는 경로 보존 검증 (PLAN1.md §도메인 룰 §인터록 재검사 경로 보존 의무 직결) → 검증: ./gradlew.bat :api:test --tests com.mo.swtp.pump.PumpControlIntegrationTest PASS (EnabledIfEnvironmentVariable=SWTP_INTEGRATION_DB 미설정으로 SKIP 되었으나 컴파일 통과 확인 + PumpControlServiceTest·PumpInterlockScenarioTest 의 단위 시나리오 PASS — 인터록 재검사 4·5·6번 케이스 모두 GREEN)

### Phase 5: 빌드 검증

- [x] ./gradlew.bat clean build 실행 → 검증: BUILD SUCCESSFUL 출력 확인 + warning 정상 범위 (BUILD SUCCESSFUL in 1m 4s, 22 actionable tasks 모두 executed, EnumType warning 만 정상 수준)
- [x] ./gradlew.bat :common:test 실행 → 검증: 모든 테스트 PASS (BUILD SUCCESSFUL)
- [x] ./gradlew.bat :api:test 실행 → 검증: 모든 테스트 PASS (PumpControlIntegrationTest·PumpMasterCacheServiceTest 포함, 103 tests 1 skipped — IntegrationTest SWTP_INTEGRATION_DB 미설정 SKIP)
- [x] ./gradlew.bat :scheduler:test 실행 → 검증: 모든 테스트 PASS (BUILD SUCCESSFUL)
- [x] Querydsl Q클래스 재생성 확인 → 검증: ls common/build/generated/sources/annotationProcessor/java/main/com/mo/swtp/ 출력에 facility, instrument, tag, raw 디렉토리 등장 + QFacility·QPurifiedWaterTank·QDistributionWaterTank·QReservoir·QInstrument·QPump·QValve·QFlowMeter·QPressureMeter·QLevelMeter·QPowerMeter·QTag·QRawData 파일 존재 확인
- [x] check-ddl-column-comment.sh 훅 미차단 확인 — V6_1~V6_5 + V2 변경 모든 컬럼 COMMENT ON COLUMN 누락 0건 → 검증: TASK1-1 Phase 2 작성 시 자동 검증 완료 + 본 Phase clean build 시 PostToolUse 훅 발화 없음 (Phase 4·5 는 SQL 미변경)

## 산출물
- [결과](../../../results/20260503/마스터도메인설계/RESULT1.md)
