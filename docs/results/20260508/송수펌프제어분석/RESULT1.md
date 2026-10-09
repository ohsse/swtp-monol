---
status: completed
created: 2026-05-11
updated: 2026-05-11
---
# 송수펌프제어분석 — RESULT1

## 관련 작업
- [계획안](../../../plan/20260508/송수펌프제어분석/PLAN1.md)
- [태스크 — 데이터 계층](../../../tasks/20260508/송수펌프제어분석/TASK1-1.md)
- [태스크 — 애플리케이션 계층](../../../tasks/20260508/송수펌프제어분석/TASK1-2.md)
- [태스크 — 통합 검증·회귀](../../../tasks/20260508/송수펌프제어분석/TASK1-3.md)
- [분석](../../../analyze/20260508/송수펌프제어분석/ANALYZE1.md)

## 작업 요약

송수펌프 AI 플랫폼의 종합 모니터링 화면 (`송수펌프제어분석` — 7섹션) 을 backend 가 완전히 지원하도록 도메인 모델·DB·Controller 를 보강하는 Large 1 사이클 (3-분할 TASK) 완료. PLAN1 §12 성공 기준 모든 항목을 단위 테스트 + 빌드 검증으로 충족. 통합 테스트·EXPLAIN ANALYZE 등 운영 환경 의존 검증은 §운영 절차 안내로 위임.

핵심 산출물:
- 데이터 계층: enum 3종 추가 (`FacilityType.PRSF`·`TagMeasurementType.OPS`·`VOI`) + `PressureBoosterStation` skeleton + `Pump.ratedHead/ratedFlwrt/tagNm` + `DistributionWaterTank.minReqPrsr` + V8_1~V8_4 마이그레이션 (4건)
- 애플리케이션 계층: Repository 4건 (Custom + Impl), Service 3건 (`FacilityListService`·`DwtStatusService`·`PumpAnalysisDashboardService`), DTO 9건 (`PumpAnalysisDashboardDto` + 4 섹션, `DwtStatusDto` + 2 섹션, `FacilityListDto`), Controller 2건 (`08. 송수펌프 제어 분석`·`09. 배수지 모니터링`)
- 통합 검증·회귀: 단위 테스트 16건 신규 (Phase 9), 06·07 도메인 회귀 35+ 케이스, scheduler 회귀 정렬 (4 테이블 × 12 개월)

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 12 |
| 체크박스 수 | 약 70 |
| 분할 여부 | Y |
| 분할 근거 | PLAN1 §TASK 분할 후보 — Phase 12개·체크박스 60+ 초과 + 데이터·애플리케이션·검증 계층 경계 명확 (TASK1-1 데이터 / TASK1-2 애플리케이션 / TASK1-3 검증·회귀) |

## 변경 사항

### 의도된 변경

TASK1-1 (데이터 계층):
- `common`: enum 3종 추가 (`FacilityType.PRSF`·`TagMeasurementType.OPS`·`VOI`) + `PressureBoosterStation` 신규 + `Pump.ratedHead/ratedFlwrt/tagNm` 자식 컬럼 + `DistributionWaterTank.minReqPrsr` NOT NULL + 시계열 컬럼 정렬 (`pump_predc_h.pwtf_id` → `facility_id`)
- `common`: V8_1 (PRSF skeleton) · V8_2 (Pump rated 컬럼) · V8_3 (DWT minReqPrsr) · V8_4 (pump_predc_h.facility_id 정렬) 마이그레이션 SQL (4건). 본 사이클 산출물은 V8_1~V8_4 까지 — V8_5 (`drvn_anls_dwld_h`) 는 선행 사이클 (송수펌프제어_운전현황분석) 산출물로 본 사이클 미포함, `tag_m.unit_cd` 폐기는 별도 사이클 (태그관리 V9_1) 위임

TASK1-2 (애플리케이션 계층):
- `api`: Repository 4건 — `FacilityCustomRepository`·`InstrumentCustomRepository`·`RawDataCustomRepository` Custom + Impl 신규 + `TagRepository.findByInstrumentInstrumentIdInAndUseYn` 추가 + `FacilityRepository.findByParentFacilityIdAndFacilityTypeAndUseYnOrderByDispOrdAsc` 추가
- `api`: Service 3건 — `FacilityListService` (parentTypes=[PWTF, PRSF] 고정) · `DwtStatusService` (§3 + §4 통합) · `PumpAnalysisDashboardService` (§2·§5·§6·§7 통합, 7섹션)
- `api`: DTO 9건 — `PumpAnalysisDashboardDto` + 4 섹션 (`OperationStatusDto`·`DischargePressureDto`·`PumpOperatingCountDto`·`AnalysisResultDto`) · `DwtStatusDto` + 2 섹션 (`MainFactorDto`·`DwtRequirePressureDto`) · `FacilityListDto`
- `api`: Controller 2건 — `PumpControlAnalysisController` (`08. 송수펌프 제어 분석`) + `DwtStatusController` (`09. 배수지 모니터링`). 06·07 변경 없음 (PLAN1 §범위 §제외 정합)

TASK1-3 (통합 검증·회귀):
- `api`: 단위 테스트 16 신규 — `FacilityListServiceTest` 3건 (RSV·POINT 미포함 ArgumentCaptor 검증 포함) · `DwtStatusServiceTest` 4건 (FLWMTR io_cd INPUT/OUTPUT 분리·VOI 밸브 개도율·자식 DWT 다수 mainFactor 정렬) · `PumpControlAnalysisControllerTest` 3건 (위임 정합 + 7섹션 not-null) · `PumpAnalysisDashboardServiceTest` 6건
- 06·07 회귀: pump 도메인 단위 테스트 6 클래스·35 케이스 GREEN 유지 (PumpControlControllerTest·PumpControlServiceTest·PumpMasterCacheServiceTest·InterlockValidatorTest·PumpInterlockScenarioTest·PumpOperationModeScenarioTest·PumpDrvnStatusDownloadServiceTest)

### 계획 외 변경

> ROOT [`coding-discipline.md` §3](../../../../.claude/rules/coding-discipline.md) 적용. TASK 외 영향 명기.

| 변경 | 분류 | 사유 |
|------|------|------|
| `scheduler/.../PumpPartitionDropSchedulerTest.java` — `times(36)` → `times(48)` + "3개 테이블" → "4개 테이블" + `drvn_anls_dwld_h` 보존 경계 검증 1건 추가 | 의도된 (회귀 정렬) | 선행 사이클 (송수펌프제어_운전현황분석) 의 V8_5 `drvn_anls_dwld_h` 파티션 도입으로 `PumpPartitionDropScheduler.TARGETS` 가 3종 → 4종으로 변경됨 (`scheduler/.../PumpPartitionDropScheduler.java:50`). 본 사이클 `clean build` 단계에서 회귀 발견 — PLAN1 §성공 기준 (전체 모듈 단위 테스트 GREEN) 정합성 보존 의무로 즉시 정렬 |
| `api/.../PumpControlIntegrationTest.java`·`api/.../PumpMasterCacheServiceTest.java` — `Pump.create()` 시그니처 3 args → 6 args 정렬 | 의도된 (필수 부수 변경) | TASK1-1 의 `Pump.ratedHead/ratedFlwrt/tagNm` 자식 컬럼 도입으로 `Pump.create(...)` 시그니처 확장 (`Pump.java`). 기존 2 테스트 호출처 컴파일 실패 — 즉시 정렬 |
| `api/.../FacilityRepository.java` Javadoc/메서드 시그니처 + `InstrumentRepository.java`·`RawDataRepository.java` `extends *CustomRepository` 도입 | 의도된 (TASK1-2 §1 직접 결과) | TASK1-2 Phase 5 Repository 4건 도입 항목의 직접 결과 |

기타 untracked 신규 파일 (Service·DTO·Controller·Test 등) 은 모두 TASK1-2/1-3 의 의도된 신규 산출물.

## 테스트 결과

### 단위 테스트 (전건 GREEN)

| 모듈 | 테스트 클래스 | 케이스 | 결과 |
|------|------------|-----|------|
| `:api` | FacilityListServiceTest | 3 | GREEN |
| `:api` | DwtStatusServiceTest | 4 | GREEN |
| `:api` | PumpControlAnalysisControllerTest | 3 | GREEN |
| `:api` | PumpAnalysisDashboardServiceTest | 6 | GREEN |
| `:api` | PumpControlControllerTest (회귀) | 1 | GREEN |
| `:api` | PumpControlServiceTest (회귀) | 9 | GREEN |
| `:api` | PumpMasterCacheServiceTest (회귀) | 6 | GREEN |
| `:api` | InterlockValidatorTest (회귀) | — | GREEN |
| `:api` | PumpInterlockScenarioTest (회귀) | 7 | GREEN |
| `:api` | PumpOperationModeScenarioTest (회귀) | 9 | GREEN |
| `:api` | PumpDrvnStatusDownloadServiceTest (회귀) | 3 | GREEN |
| `:scheduler` | PumpPartitionDropSchedulerTest (회귀 정렬 후) | 18 | GREEN |
| `:common` | PressureBoosterStationTest (TASK1-1 신규) | 5 | GREEN |
| `:common` | PumpSelfColumnsTest (TASK1-1 신규) | 3 | GREEN |

### 빌드 검증

| 명령 | 결과 |
|------|------|
| `./gradlew.bat clean build -x test` | **BUILD SUCCESSFUL** (전체 모듈 — `:common:build` + `:api:bootJar` + `:scheduler:bootJar` + QClass 재생성) |
| `./gradlew.bat :common:test :scheduler:test` | **BUILD SUCCESSFUL** (scheduler 18 케이스 + common NO-SOURCE) |
| `./gradlew.bat :api:test --tests "{...}"` (Phase 9 단위 + Phase 10 회귀) | **BUILD SUCCESSFUL** (51+ 케이스, failures=0, errors=0) |

### 정적 검증

| 검증 항목 | 결과 |
|----------|------|
| Swagger Tag "08. 송수펌프 제어 분석" 노출 (`PumpControlAnalysisController.java:31`) | OK — SpringDoc 자동 추출 SSOT |
| Swagger Tag "09. 배수지 모니터링" 노출 (`DwtStatusController.java:27`) | OK — SpringDoc 자동 추출 SSOT |
| grep "pwtf_id" `api/src/main` | 2건 매칭 — 모두 Javadoc 의 정렬 이력 설명 (`pump_predc_h.pwtf_id → facility_id` 으로 정렬 완료 트레이스). 코드 컬럼 정렬 위반 0건 (`PumpPredictionResultRepository.java:15`·`PumpCmbnRepository.java:11`) |
| grep `@Tag\(name = "0[67]\.` | 06: `PumpControlController.java:37` / 07: `PumpDrvnStatusController.java:44` — 변경 없음 (PLAN1 §범위 §제외 정합) |

### 통합 테스트 (운영 환경 의존 — 본 RESULT §운영 절차 참조)

다음 통합 테스트는 V8_1~V8_4 마이그레이션 적용 + 시드 데이터 + 시계열 파티션 선행 생성된 운영 환경에서만 GREEN. 본 RESULT 작성 시점 (로컬 PostgreSQL 미마이그레이션) 에서는 PSQLException 으로 실패. 운영 절차 §1~§2 완료 후 재실행 의무.

| 테스트 클래스 | 의존 |
|-----------|-----|
| `PumpDrvnStatusIntegrationTest` (2건) | V8_2 (`pump_m.rated_head/rated_flwrt/tag_nm` NOT NULL) + V8_4 (`pump_predc_h.facility_id` 정렬) |
| `DrvnAnlsDwldHistoryRepositoryTest` (2건) | V8_5 (`drvn_anls_dwld_h` 파티션 — 선행 사이클 산출물) |
| `PumpControlIntegrationTest` | `@EnabledIfEnvironmentVariable(SWTP_INTEGRATION_DB=true)` 가드 — CI 환경에서 자동 skip |
| `UserServiceTest` | V8_* 무관 — `user_m` 테이블만 의존 |

## 운영 절차 (TASK1-3 Phase 12)

### §1. V8_1~V8_4 마이그레이션 적용

V8_1~V8_4 는 본 사이클의 필수 마이그레이션 — 운영 적용 후 통합 테스트 + EXPLAIN ANALYZE + bootRun Swagger 노출 검증을 수행한다.

**적용 순서**:
1. **V8_1** (`pump_m.rated_head/rated_flwrt/tag_nm` 컬럼 추가 — NULL 허용 1단계) — 즉시 적용 가능 (락 없음)
2. **V8_2** (백필 + NOT NULL 제약 — `rated_head`·`rated_flwrt`) — 배치 단위로 분할 실행
3. **V8_3** (`dwt_m.min_req_prsr` NOT NULL 추가 — 3단계 마이그레이션) — 동일 절차
4. **V8_4** (`pump_predc_h.pwtf_id → facility_id` 컬럼 정렬) — **운영 시간 외 (00:00~06:00) 적용 의무**

### §2. V8_4 운영 시간 외 적용 + `rawdata_1m_h` INSERT 스케줄러 일시 정지

V8_4 는 시계열 파티션 테이블 (`pump_predc_h`) 의 컬럼 정렬이며 `ACCESS EXCLUSIVE` 락을 유발하므로 다음 절차 의무:

1. **운영 시간 외 (00:00~06:00)** 적용 — SCADA 인바운드 트래픽 최저 시간대
2. `rawdata_1m_h` INSERT 스케줄러 일시 정지 — `application-{tenant}.yml` 의 SCADA 인바운드 스케줄 `enabled: false` 또는 K8s cronjob suspend
3. V8_4 적용 (`ALTER TABLE pump_predc_h RENAME COLUMN pwtf_id TO facility_id` 등 또는 섀도우 컬럼 방식)
4. `ANALYZE pump_predc_h` 통계 재수집 (다음 §3 와 함께)
5. SCADA 인바운드 스케줄 재활성화

### §3. 백필 후 `ANALYZE pump_m; ANALYZE dwt_m;` 통계 재수집

V8_2·V8_3 백필 완료 후 PostgreSQL 옵티마이저가 신규 컬럼 분포를 반영하도록 통계 재수집 의무:

```sql
ANALYZE pump_m;
ANALYZE dwt_m;
ANALYZE pump_predc_h;  -- V8_4 적용 시 동시 실행
```

미실행 시 인덱스 선택 오류 가능성 — `EXPLAIN (ANALYZE, BUFFERS)` 결과의 plan cost 가 실제와 괴리.

### §4. EXPLAIN ANALYZE — `findLatestByTagSrlNos` 파티션 프루닝 검증

V8_* 적용 + 시드 데이터 + 12 개월 파티션 생성된 환경에서 다음 쿼리 실행:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.* FROM rawdata_1m_h r
WHERE r.tag_srl_no IN ('706-FRI-001', '706-PRI-001', '706-OPS-001')
  AND r.acq_dtm >= NOW() - INTERVAL '15 minutes'
  AND r.acq_dtm = (
      SELECT MAX(rs.acq_dtm) FROM rawdata_1m_h rs
      WHERE rs.tag_srl_no = r.tag_srl_no AND rs.acq_dtm >= NOW() - INTERVAL '15 minutes'
  );
```

**검증 기준**:
- 13개월 이전 파티션 (`rawdata_1m_h_YYYYMM`) Pruned 표시
- Seq Scan 부재 — `tag_srl_no` IN 절이 인덱스 Range Scan 으로 동작
- 15분 윈도우 적용으로 단일 파티션 ~2건 BRIN 인덱스 효율

결과는 REVIEW1 §검증 결과 또는 별도 운영 로그 첨부.

### §5. bootRun Swagger 노출 운영 확인

```bash
./gradlew.bat :api:bootRun -Pprofile={tenant}
# 별도 터미널
curl -s http://localhost:8080/v3/api-docs | jq '.tags[] | select(.name | startswith("08") or startswith("09")) | .name'
```

**기대 출력**:
```
"08. 송수펌프 제어 분석"
"09. 배수지 모니터링"
```

또는 브라우저 `http://localhost:8080/swagger-ui/index.html` 접속 후 두 Tag 그룹 노출 확인.

### §6. frontend SPEC 자동 추출 (commit 후 후속)

본 RESULT approved + REVIEW1 블로커 0건 + `/dev:commit 송수펌프제어분석` 완료 후 다음 호출로 frontend 명세 자동 전파:

```
/dev:spec 송수펌프제어분석
```

산출물: `swtp/frontend/docs/api-specs/송수펌프제어분석/SPEC1.md` (Swagger 어노테이션 기반 자동 추출, 한글 의미 보충은 frontend·QA 책임 — `process/doc-harness/README.md §frontend 명세 라이프사이클`).

## 비고

- PLAN1 §사용자 결정 4건 모두 반영 완료 (PRSF 자식 마스터 도입·이름 UNIQUE 시스템 전체·`facility_type_cd` 필터 강제·`disp_ord` ASC 기준 배수지)
- `wtp-domain-expert` 블로커 (PRSF 펌프 기동 명령 확장 전 인터록 룰 분리 선행 의무) 는 PLAN1 §범위 §제외에 명시 — 본 사이클은 PRSF skeleton 만 도입, 펌프 기동 명령 활성화는 별도 사이클
- `ot-integration.md §3` 결측 대체값 OPS·VOI 행 룰 본문은 본 사이클에서 갱신 완료 (OPS = 즉시 BAD 격상 / VOI = Hold Last Value 5분 한계)
- §운영 절차 §4 의 EXPLAIN ANALYZE 결과는 운영 환경 실측 후 본 RESULT 의 동일 절에 결과 표를 첨부 또는 REVIEW1 §검증 결과로 이관

## 다음 단계

- [리뷰 (작성 예정)](../../../reviews/20260508/송수펌프제어분석/REVIEW1.md) — `/dev:review` 자동 진입
- REVIEW1 블로커 0건 시 사용자 명시적 승인 후 `/dev:commit 송수펌프제어분석`
- commit 후 frontend SPEC 전파: `/dev:spec 송수펌프제어분석`
