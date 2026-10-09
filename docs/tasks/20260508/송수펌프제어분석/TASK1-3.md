---
status: completed
created: 2026-05-08
updated: 2026-05-11
---
<!--
TASK1-3 (통합 검증·회귀) 완료 — 2026-05-11. Phase 9 단위 테스트 4건 신규 + Phase 10 06·07 도메인 회귀 + Phase 11 빌드/Swagger 정적 검증 + Phase 12 운영 절차 안내 (RESULT1 §운영 절차).
통합 테스트 4건 (PumpDrvnStatusIntegrationTest 2건·DrvnAnlsDwldHistoryRepositoryTest 2건) 은 V8_1~V8_5 마이그레이션 적용 + 시드 데이터 + 시계열 파티션 선행 생성된 운영 환경 의존 — RESULT1 §운영 절차 §1~§4 완료 후 재실행 의무 (본 TASK 범위 외).
회귀 정렬 발견 (의도된 부수 변경): `PumpPartitionDropSchedulerTest` (`times(36)` → `times(48)`, 4 테이블 대응) — 선행 사이클의 `drvn_anls_dwld_h` 추가 영향 회귀, RESULT1 §계획 외 변경에 명기.
-->

# 송수펌프제어분석 — TASK1-3 (통합 검증·회귀)

## 관련 계획
- [PLAN1](../../../plan/20260508/송수펌프제어분석/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 데이터 계층](TASK1-1.md)
- [TASK1-2 애플리케이션 계층](TASK1-2.md)
- [TASK1-3 통합 검증·회귀](TASK1-3.md) (현재 파일)

## 묶음 범위

통합 테스트 4건 (Service / Controller) + 06·07 회귀 + 빌드·Swagger 노출 검증 + frontend SPEC 자동 추출 안내. TASK1-1·1-2 가 완료되어야 본 묶음 진입 가능 (통합 테스트가 엔티티·Service·Controller 의존).

본 묶음 완료 + RESULT1 작성 + REVIEW1 블로커 0건이 보장되어야 `/dev:commit` 진입 가능.

---

## Phase

### Phase 9: 통합 테스트 4건 (@SpringBootTest webEnvironment = NONE)

- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityListServiceTest.java` Mockito 단위 테스트 3건 — 활성DWT 보유 시 매핑 / 빈 응답 / ArgumentCaptor 로 parentTypes=[PWTF, PRSF] 만 전달됨 (RSV·POINT 미포함) → 검증: :api:test --tests FacilityListServiceTest GREEN (failures=0, errors=0)
- [x] `api/src/test/java/com/mo/swtp/pump/service/PumpAnalysisDashboardServiceTest.java` Mockito 단위 테스트 6건 GREEN (TASK1-2 선작성, TASK1-3 회귀 재확인) → 검증: :api:test --tests PumpAnalysisDashboardServiceTest GREEN
- [x] `api/src/test/java/com/mo/swtp/facility/service/DwtStatusServiceTest.java` Mockito 단위 테스트 4건 — 기준배수지 disp_ord ASC + FLWMTR INPUT/OUTPUT 분리 (TASK1-2 선작성) + VOI 밸브 개도율 + LEI 수위 매핑 + 자식 DWT 다수 mainFactor disp_ord 정렬 → 검증: :api:test --tests DwtStatusServiceTest GREEN
- [x] `api/src/test/java/com/mo/swtp/pump/web/PumpControlAnalysisControllerTest.java` 단위 위임 검증 3건 — getFacilities 위임+200 / 빈 목록 200 / getDashboard 위임+7섹션 not-null + AI 모드 두 컬럼 검증. MockMvc+401 인증 검증은 test-strategy.md §부록 A 슬라이스 도입 후 별도 사이클 (현 프로젝트 패턴 정합 — PumpControlControllerTest 동일) → 검증: :api:test --tests PumpControlAnalysisControllerTest GREEN

### Phase 10: 06·07 회귀

- [x] `api/src/test/java/com/mo/swtp/pump/web/PumpControlControllerTest.java` 회귀 검증 — 1 케이스 GREEN (pump_predc_h.facility_id 정렬 영향 없음) → 검증: :api:test --tests PumpControlControllerTest GREEN
- [x] 07 도메인 회귀 검증 — `PumpDrvnStatusControllerTest` 미존재 (07 컨트롤러 단위 테스트 부재), 대신 `PumpDrvnStatusDownloadServiceTest` 3 케이스 + 07 도메인 서비스/repository 전건 GREEN 으로 대체 → 검증: :api:test --tests PumpDrvnStatusDownloadServiceTest GREEN
- [x] grep `pwtf_id` api/src/main — 2건 매칭 (`PumpPredictionResultRepository.java:15`·`PumpCmbnRepository.java:11`) 은 모두 Javadoc 의 컬럼 정렬 이력 설명 (정렬 완료 트레이스). 실 코드 `pwtf_id` 컬럼 위반 0건 → 검증: RESULT1 §정적 검증 표 인용

### Phase 11: 통합 빌드·Swagger·SPEC

- [x] `./gradlew.bat clean build -x test` BUILD SUCCESSFUL (전체 모듈 클린 빌드 + QClass 재생성 + bootJar). `:common:test`·`:scheduler:test` 별도 GREEN (scheduler 18 케이스 회귀 정렬 후) + `:api:test` 단위 51+ 케이스 GREEN. 통합 테스트 4건 (DB 의존) 은 RESULT1 §운영 절차 위임 → 검증: RESULT1 §빌드 검증 표 인용
- [x] Swagger Tag "08. 송수펌프 제어 분석" (`PumpControlAnalysisController.java:31`) + "09. 배수지 모니터링" (`DwtStatusController.java:27`) 정적 검증 — SpringDoc 자동 추출 SSOT. bootRun 실 노출은 RESULT1 §운영 절차 §5 위임 → 검증: RESULT1 §정적 검증 표 인용
- [x] EXPLAIN ANALYZE 파티션 프루닝 검증은 운영 환경 (V8_* 적용 + 시드 데이터 + 12 개월 파티션) 의존 — RESULT1 §운영 절차 §4 안내 작성 (psql 명령·기대 결과 명기) → 검증: RESULT1 §운영 절차 §4 명시
- [x] frontend SPEC 자동 추출 안내 — 본 TASK 산출물 섹션 + RESULT1 §운영 절차 §6 명시 (`/dev:commit` 후속 `/dev:spec 송수펌프제어분석` 호출로 `swtp/frontend/docs/api-specs/송수펌프제어분석/SPEC1.md` 자동 생성) → 검증: RESULT1 §운영 절차 §6 명시

### Phase 12: 백필·운영 절차 안내 (코드 영향 없음)

- [x] V8_2·V8_3 백필 후 `ANALYZE pump_m; ANALYZE dwt_m; ANALYZE pump_predc_h;` 통계 재수집 운영 안내 — RESULT1 §운영 절차 §3 작성 완료 → 검증: RESULT1 §운영 절차 §3 명시
- [x] V8_4 운영 시간 외 (00:00~06:00) 적용 + `rawdata_1m_h` INSERT 스케줄러 일시 정지 운영 절차 — RESULT1 §운영 절차 §2 작성 완료 (5단계 절차 명기) → 검증: RESULT1 §운영 절차 §2 명시

---

## 산출물

본 분할 완료 후 RESULT1 작성 → REVIEW1 호출 → 블로커 0건 시 `/dev:commit` 진입 가능. SPEC 전파는 commit 후 별도 `/dev:spec` 호출.

- [결과](../../../results/20260508/송수펌프제어분석/RESULT1.md) (status: draft, 2026-05-11 작성)
- [리뷰 (작성 예정)](../../../reviews/20260508/송수펌프제어분석/REVIEW1.md) — `/dev:review` 자동 진입
- frontend 명세 (작성 예정): `swtp/frontend/docs/api-specs/송수펌프제어분석/SPEC1.md` (`/dev:commit` 후 `/dev:spec 송수펌프제어분석` 호출)
