---
status: completed
created: 2026-06-01
updated: 2026-06-01
---
# 운전현황분석-5번섹션-DTO재설계 — 작업 분해

## 관련 계획
- [계획안](../../../plan/20260521/운전현황분석-5번섹션-DTO재설계/PLAN1.md)
- [도메인 분석](../../../analyze/20260521/운전현황분석-5번섹션-DTO재설계/ANALYZE1.md)

## 규모
Medium (단일 도메인·응답 DTO 재설계). RESULT/REVIEW 면제 — `/dev:impl` 후 `/dev:commit` 안내.

## 변경 대상 4파일 (DB·인덱스·DDL·엔드포인트·도메인 정책 무변경)

| 파일 | 성격 |
|------|------|
| `FacilityOperatingStatusTimeSeriesDto.java` | outer + inner 옵션 T 재설계 |
| `FacilityOperatingStatusTimeSeriesService.java` | `buildSeries`/`buildPoint` 제거 + 머지 헬퍼 재작성, 헬퍼 9종 불변 |
| `FacilityController.java` | 5번섹션 `@Operation` description 옵션 T 정합 (시그니처·로직 불변) |
| `FacilityOperatingStatusTimeSeriesServiceTest.java` | series 4건 재작성 + 1440 고정 1건 신규 |

## Phase

### Phase 1: 응답 DTO 옵션 T 재설계
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityOperatingStatusTimeSeriesDto.java` outer 필드 재설계 — todaySeries·comparisonSeries 제거, baseDate(LocalDate)·comparisonDate(LocalDate)·series(List) 추가, of() 정적 팩토리 6인자로 변경, baseDate/comparisonDate 에 yyyy-MM-dd JsonFormat + series 에 ArraySchema → 검증: Phase 5 빌드에서 DTO 컴파일 성공
- [x] `api/src/main/java/com/mo/swtp/facility/dto/FacilityOperatingStatusTimeSeriesDto.java` inner TimeSeriesPoint 재설계 — acqDtm·onPumpCnt·totalElpwrAmt·elpwrUnitQty 제거, time(String)·todayElpwrUnitQty·comparisonElpwrUnitQty·todayOnPumpCnt 추가, of() 4인자로 변경, Schema description 갱신 (time HH:mm 예시·null 4케이스·todayOnPumpCnt OPS GOOD 전용 ot-integration.md §3) → 검증: Phase 5 빌드에서 컴파일 성공 + description 문구 시각 확인

### Phase 2: Service 재작성 (헬퍼 9종 불변)
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesService.java` 메인 메서드 findFacilityOperatingStatusTimeSeries 재작성 — baseDate=LocalDate.now()·comparisonDate=baseDate.minusDays(daysAgoOf) 산출, today/comparison 각각 collectMinuteMetrics 호출, buildOptionTSeries 머지, of() 6인자 호출. instruments empty 시 buildOptionTSeries(Map.of(), Map.of()) 1440 null 반환 → 검증: Phase 5 정상_시계열·빈_시계열 테스트 GREEN
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesService.java` 머지 헬퍼 신규 추가 — collectMinuteMetrics(범위→Map HH:mm:MinuteMetrics)·computeMinuteMetrics(분 단위 지표)·buildOptionTSeries(1440 슬롯)·mergeIntoSlot(단일 행)·daysAgoOf(YESTERDAY 1/LAST_WEEK 7)·hhmm(시·분 포맷)·MinuteMetrics record. buildSeries·buildPoint·TimeRange.acqDtm 기반 로직 제거 → 검증: Phase 5 :api:test PASS + buildOptionTSeries/mergeIntoSlot 본문 각 50줄 이내 시각 점검
- [x] `api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesService.java` 헬퍼 9종 (findActiveFacilityOrThrow·loadTagsByInstrument·filterPumps·pickLatest·effectiveVal·isPumpRunning·sumOnPumpPwr·selectFacilityFri·computeUnitConsumption) 본문 무변경 + resolveComparisonRange 는 daysAgoOf 재사용으로만 정리 → 검증: git diff 로 9종 헬퍼 본문 무변경 확인

### Phase 3: Swagger 명세 갱신 (Controller — 시그니처 불변)
- [x] `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` 5번섹션 @Operation description 옵션 T 정합 갱신 — "비교 기간 시계열 동시 응답·onPumpCnt/totalElpwrAmt/elpwrUnitQty·결측 생략" 문구를 "시간:분 1440 고정 단일 series·todayElpwrUnitQty/comparisonElpwrUnitQty/todayOnPumpCnt·결측 null 채움·baseDate/comparisonDate 메타" 로 교체 → 검증: Phase 5 빌드에서 컴파일 성공 + description 문구 시각 확인

### Phase 4: 단위 테스트 재작성
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesServiceTest.java` series 4건 (정상·빈·결측·UNCERTAIN) 옵션 T 검증으로 재작성 — getTodaySeries/getComparisonSeries/getAcqDtm/getTotalElpwrAmt assertion 제거, series.size()==1440 + time 키 인덱싱(HH:mm = hour*60+min) + todayElpwrUnitQty/comparisonElpwrUnitQty/todayOnPumpCnt + 비데이터 슬롯 null 검증 → 검증: Phase 5 4건 GREEN
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesServiceTest.java` 1440 행 고정 검증 신규 1건 추가 — today 단일 시점 데이터 + series.size()==1440·series.get(0).time=="00:00"·series.get(1439).time=="23:59"·데이터 슬롯 값·미래/결측 슬롯 3컬럼 null → 검증: Phase 5 신규 1건 GREEN
- [x] `api/src/test/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesServiceTest.java` 예외 테스트 2건 (비활성 FACILITY_NOT_FOUND·RSV UNSUPPORTED) 무변경 유지 확인 → 검증: Phase 5 2건 GREEN

### Phase 5: 빌드·전체 검증
- [x] `./gradlew.bat :api:test --tests com.mo.swtp.facility.service.FacilityOperatingStatusTimeSeriesServiceTest` 실행 → 검증: 7 tests passed (4 재작성 + 1 신규 1440 + 2 예외) — 결과 XML tests="7" failures="0" errors="0" 확인 완료
- [x] `./gradlew.bat build` 전체 빌드 → 검증: `:api:compileJava` PASS + 시계열 단위 7건 PASS. 통합테스트 23건은 로컬 PostgreSQL(localhost:5432) 부재로 Connection refused — 본 변경 무관 환경 제약 (test-strategy.md §2 DB 전제조건 미충족), BUILD SUCCESSFUL 미달성은 환경 사유

## 구현 결과 (Medium — RESULT 면제, 본 절로 경량 기록)

- **변경 4파일**: DTO(옵션 T outer 6필드 + inner 4필드) · Service(머지 헬퍼 7종 신규, 헬퍼 9종·도메인 정책 불변) · Controller(@Operation description 1건) · Test(7건).
- **검증**: `FacilityOperatingStatusTimeSeriesServiceTest` 7/7 GREEN (`failures=0 errors=0`). `:api:compileJava`·`:api:compileTestJava` PASS.
- **메서드 줄 수**: buildOptionTSeries(~9줄)·mergeIntoSlot(~5줄)·collectMinuteMetrics(~20줄)·computeMinuteMetrics(~16줄)·메인(~25줄) 모두 coding-discipline §2.1 50줄 이내.
- **환경 제약**: 전체 `build` 의 23건 통합테스트 실패는 모두 `@SpringBootTest` 컨텍스트 로드 — `localhost:5432 Connection refused`. 본 사이클 변경(facility 시계열 DTO/Service)과 인과 없음. 로컬 PostgreSQL 기동 환경에서는 영향 없음.
- **계획 외 변경**: 없음. TASK 체크박스 4파일 외 변경 0건.

## 산출물
- Medium 규모 — RESULT/REVIEW 면제. `/dev:commit` 으로 진행 (사용자 명시 승인 필요).
- frontend 영향: 응답 DTO breaking change → 커밋 후 `/dev:spec 운전현황분석-5번섹션-DTO재설계` (선택) 으로 SPEC 갱신.
