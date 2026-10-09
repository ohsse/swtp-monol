---
status: approved
created: 2026-05-27
updated: 2026-05-27
---
# 운전현황분석-10번섹션 — 도메인 분석

## 작업 배경

운전현황 분석 페이지 (`swtp/backend/image/운전현황분석.png`) 의 10번 섹션 "전력원단위(운영:계측|예측) + 펌프가동대수(예측) 시계열" 카드의 backend API 신설.

5번 섹션 (`docs/analyze/20260521/운전현황분석-5번섹션/ANALYZE1.md`, 커밋 `89b4e00` `FacilityOperatingStatusTimeSeriesService`) 이 현재 계측 + 과거 비교(YESTERDAY/LAST_WEEK) 시계열을 제공한다면, 본 10번 섹션은 같은 시설의 **금일 하루치 (00:00 ~ 23:59 1440 포인트)** 1분 단위 시계열을 **계측(자정~현재) + 예측(자정~익일자정)** 합본으로 반환한다.

9번 섹션 (`status: completed`, 커밋 `b89c144` `FacilityPredcOperatingStatusService`) 의 단일 시점 예측 패턴을 1440 포인트 시계열 범위로 확장한다.

### 카드 표출 (사용자 설명)

| 데이터 | 시간 범위 | 산출 방법 |
|--------|----------|----------|
| 전력원단위 (계측) | 00:00 ~ 현재시간, 1분 | 5번섹션 동일 — (가동 펌프 PWI GOOD 합산 / 시설 FRI GOOD effectiveVal) |
| 전력원단위 (예측) | 00:00 ~ 익일 00:00, 1분 | 9번섹션 동일 산식, predc 단일값 — (가동 펌프 PWI predc_val 합산 / 시설 FRI predc_val) |
| 펌프 가동대수 (예측) | 00:00 ~ 익일 00:00, 1분 | 시점별 OPS `predc_val == 1.0` 카운트 합산 |

자정~현재 구간은 계측과 예측 두 시리즈가 함께 표출되어 frontend 가 예측 정확도 시각 검증 가능 (Q8 결정 — 사용자 토론).

### 사용자 결정 (ANALYZE 진행 중 확정)

- Q1: **유출유량 = 시설의 FLWMTR 자식 인스트루먼트의 FRI 태그** — 9번섹션 `selectFacilityFri` 패턴 재사용
- Q2: **1분 간격, 자정~23:59 단일 시계열 1440 포인트** — 각 포인트에 계측·예측·펌프대수 묶음
- Q3: 단일 series + 각 포인트 7필드 (Q7 결정과 통합)
- Q4: 펌프 가동대수 = **예측만** 표출 (계측 펌프 가동대수 응답 외)
- Q5: 9번섹션 `computeUnitConsumption` 3-case 패턴 재사용
- Q6: 신규 엔드포인트 `GET /api/facility/{facilityId}/operating-status/daily-time-series`
- Q7: **(B) 원본 + 집계 모두 7필드** — `actualElpwrAmt`·`actualFlwrt`·`actualUnitQty`·`predcElpwrAmt`·`predcFlwrt`·`predcUnitQty`·`predcPumpOnCnt`
- Q8: **(A) 자정~현재 구간에도 두 시리즈 모두 표출** — 예측 정확도 시각 검증 (`predc_1m_h` INSERT-only immutable 이므로 자연 충족)
- Q9: **(B) `daily-time-series`** — "금일 하루치" 의미가 가장 정확

### 외부 산출물

- `swtp/backend/image/운전현황분석.png` — Read 도구로 직접 시각 로드. 화면 구조 확인용
- 5번 섹션 ANALYZE/PLAN: `docs/analyze/20260521/운전현황분석-5번섹션/ANALYZE1.md`·`docs/plan/20260521/운전현황분석-5번섹션/PLAN1.md` (`status: approved`) — 계측 시계열 정책 + Repository 패턴 재사용 근거
- 9번 섹션 산출물: 커밋 `b89c144` (`FacilityPredcOperatingStatusService`·`TagPredcLatestRepository`) — 예측 단일 시점 정책 + Repository 분리 선례

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 표준 사전 정합성 — 신규 어휘 후보 분류

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 후보 4건 (`daily`·`time`·`series`·`point`) 전부 **등록 불필요** — Java 클래스명·DTO inner class 명·DTO 변수명 영역으로 표준 사전 (DB 컬럼명 조합 재료) 적용 외. 5번섹션 안건 1 결론 (영어 일반어 + Java 자유 명명) 동형. `actual` vs `actl`(2026-05-07 등록) 충돌 — `actl` 은 DB 컬럼 조합 재료 (예: `actl_oprtng_yn`), 본 사이클 `actualElpwrAmt` 는 DTO Java 변수명 — 적용 층위가 달라 동의어 충돌 아님 (5번섹션-DTO재설계 안건 1 `today`/`comparison` vs `actl`/`predc` 선례 동형). `predc` 도 동일 — DB 조합 재료이나 DTO `predcElpwrAmt` 변수명은 충돌 외. DTO 클래스명 `FacilityDailyTimeSeriesDto` 는 `naming.md {도메인명}Dto` 패턴 정합 — `Facility` + `Daily` + `TimeSeries` + `Dto` 조합, 5번섹션 `FacilityOperatingStatusTimeSeriesDto` 와 동형. 본 사이클은 DB 테이블·컬럼 신규 0건으로 표준 데이터 도메인·표준 용어 영향 0건.
- **결론**: 표준 사전 갱신 0건. 룰 갱신 지시서 체크박스 0건 — PLAN 진입 전제조건 자동 충족.

### 안건 2: 시계열 Repository 쿼리 — 예측 시계열 범위 + 인덱스 활용 + Repository 분리

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 본 사이클 신규 표준 데이터 도메인 0건 — 2차 승인 미발생. 발견 사항:
    - **권고(중간) Q1**: 9번섹션 `TagPredcLatestCustomRepository` 의 `DISTINCT ON + NOW() - INTERVAL '1 hour'` 단일 최신값 추출 패턴과 본 사이클 `predc_dtm BETWEEN today_00 AND tomorrow_00` 전체 범위 패턴은 **의도가 다르다**. 기존 인터페이스에 `findByTagSrlNosAndPredcDtmRange` 메서드 추가 시 단일 최신값/전체 범위 두 책임 혼재로 `coding-discipline.md §3` "정밀한 수정 (본인이 만든 코드 뒷정리만)" 위반. **신규 `TagPredcRangeCustomRepository` 분리 권고**.
    - **권고(중간) Q2**: 예측 응답 크기 — `rawdata_1m_h` 자정~NOW 평균 720포인트 vs `predc_1m_h` 자정~익일자정 항상 1440포인트 전체. 태그 수 N 가정 시 1440×N 행이 JVM 메모리 적재. 5번섹션 선례 (2880포인트 ~300KB 허용) 적용 가능하나, 계측+예측 합산 응답이 2880 초과 시 `query-tuning.md §1` 페이지네이션 룰 선례 초과 — PLAN 단계 태그 수 상한 + 응답 크기 가정 명시 권고.
    - **참고(낮음) Q3**: 인덱스 `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)` ASC 정합 — IN 등가 선행 + 범위·정렬 후행 (`indexing-and-migration.md §1` 컬럼 순서 원칙 충족). `predc_dtm BETWEEN` 범위로 파티션 프루닝 자동 활성 (`partitioning-and-retention.md §1`). Querydsl `IN + BETWEEN + ORDER BY` 단순 패턴 — 5번섹션 결론 동형 적합.
- **결론**:
  - Repository 분리 채택 — 신규 `TagPredcRangeCustomRepository.findByTagSrlNosAndPredcDtmRange(tagSrlNos, startDtm, endDtm)` 작성. 9번섹션 `TagPredcLatestRepository` 무수정 (`coding-discipline.md §3` 정합 + 사용자 메모리 "사이클 간 자산 자동 원용 금지")
  - Querydsl JPAQuery 빌더 (5번섹션 `RawDataCustomRepository.findByTagSrlNosAndDtmRange` 패턴 동형)
  - 기존 인덱스 활용 (신규 인덱스 0건)
  - SLA: 각 호출 200ms 이내 (`EXPLAIN ANALYZE` PLAN 단계 검증 의무)
  - 예측 응답 크기 가정: 시설 1개의 펌프 N대 + FLWMTR 1대 × (OPS/PWI/FRI) — 평균 N×2+1 태그, 1440포인트 시 약 5,000~10,000행 DB→JVM. PLAN 단계 명시

### 안건 3: Service·DTO·Controller 설계 + 헬퍼 추출 방향 + 정량 기준

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 발견 사항 — 블로커(높음) 2건 / 권고(중간) 1건 / 참고(낮음) 1건:
    - **블로커 Q1**: 헬퍼 추출 방향 미확정 — 4·5·9·10번 = **4 사용처 누적**. `coding-discipline.md §2` "추출 트리거 2건 누적" 초과. 그러나 본 사이클 추출 시 4·5·9번 기존 코드 수정 필요 → `coding-discipline.md §3` "본인이 만든 코드 뒷정리만" 위반 위험. **본 사이클은 5번섹션 PLAN1 §4-1 결정 그대로 따라 재구현 유지, 별도 ANALYZE 사이클에서 일괄 추출** 채택 권고. PLAN 진입 전 사용자 결정 의무.
    - **블로커 Q2**: `buildSeries` 메서드 50줄 초과 — 1440 시점 LinkedHashMap 구성 + actual 합본 + predc 합본 로직. §2.5 면책 영역 (OT 안전 도메인 / DB 쿼리 빌더) 미해당 → 분해 의무. **3단계 분해 권고**: (1) actual 시계열 → `Map<dtm, ActualParts>`, (2) predc 시계열 → 동일 Map 갱신, (3) Map → List 정렬 변환. 각 메서드 50줄 이내 PLAN 보장.
    - **권고(중간) Q3**: Repository 분리 — wtp-dba-reviewer 권고와 일치. `TagPredcLatestRepository` 무수정 + 신규 `TagPredcRangeCustomRepository` 분리 채택.
    - **참고(낮음) Q4**: `predcPumpOnCnt` Integer 타입 — Swagger 자동 인식, `@Schema(implementation)` 의무 없음. `@Schema(description)` 누락 0건 확인 의무.
- **사용자 결정 (Round 2 대체)**: Block 1 헬퍼 추출 방향 — 본 사이클은 재구현 유지 + 별도 ANALYZE 추출 (5번섹션 PLAN1 §4-1 결정 그대로 적용). 사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합 — 4·5·9번 코드 자동 수정 회피.
- **결론**:
  - Service: `FacilityDailyTimeSeriesService` 신규 분리 (4·5·9번 코드 0건 수정)
  - DTO: `FacilityDailyTimeSeriesDto` (facilityId·facilityNm·points) + static inner class `DailyTimeSeriesPoint` (dtm·actualElpwrAmt·actualFlwrt·actualUnitQty·predcElpwrAmt·predcFlwrt·predcUnitQty·predcPumpOnCnt 8필드)
  - Controller: `FacilityController` 메서드 추가 — `GET /api/facility/{facilityId}/operating-status/daily-time-series`
  - `buildSeries` 3단계 분해 (PLAN 단계 각 메서드 줄 수 보장 명시)
  - 4·5·9번 헬퍼 본 사이클 재구현 (별도 ANALYZE 추출 사이클로 이연)
  - `@Schema(implementation)` + `@JsonFormat` 의무 적용
  - `ErrorCode` — `FACILITY_NOT_FOUND`·`UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS` 재사용 (신규 0건)

### 안건 4: 도메인 룰 4영역 점검 + actual/predc NULL 의미 분리 + serverNow 메타

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 발견 사항 — 블로커 0건 / 권고(중간) 2건 / 참고(낮음) 2건:
    - **권고(중간) Q1**: actual NULL vs predc NULL 의미 분리 — 5번섹션은 actual 단일 시리즈로 NULL 의미가 "BAD 제외" 단일. 본 사이클은 두 시리즈 공존 — `actualXxx == null` 의미 = "아직 미도래 시각" 또는 "BAD 제외 또는 결측", `predcXxx == null` 의미 = "`predc_val` 부재 또는 결측". DTO `@Schema(description)` 에 두 의미 구분 명기 + PLAN "## 성공 기준" 명시 의무.
    - **권고(중간) Q2**: `serverNow` 메타 필드 필요성 미결 — 자정~현재(actual+predc 공존) vs 현재~익일자정(predc 전용) 분기 기준 "현재 시각" 을 frontend 자체 시계로 판단 시 backend·frontend 시계 미세 불일치 (수 초) 로 경계 시점 렌더링 오류 가능. 응답 DTO 에 `serverNow` 필드 포함 여부 결정 필요. "## 가정 및 미해결 질문" 별도 행 기재 의무 (`coding-discipline.md §1`).
    - **참고(낮음) Q3**: 과거 `predcDtm` 표출 도메인 룰 충돌 없음 — `ot-integration.md` 어디에도 "예측은 미래 시점이어야 한다" 제약 없음. `predc_1m_h.rgstr_dtm` (수집 메타) 과 `predc_dtm` (예측 대상 시각) 기준 표출 의미 충돌 없음.
    - **참고(낮음) Q4**: predc 다중 태그 동기 작성 가정 별도 명기 의무 — 5번섹션 안건 4 Q3 의 actual 다중 태그 동기 수집 가정과 대칭. "같은 `predcDtm` 슬롯에 OPS·PWI·FRI 예측값이 AI 추론 파이프라인에 의해 일괄 작성된다" 가정 "## 가정 및 미해결 질문" 별도 행 기재 의무.
- **결론**:
  - 4영역 모두 비해당 — 각 행 구체 사유 명기 (본 ANALYZE "## 도메인 룰 4영역 점검" 섹션 적용)
  - actual NULL vs predc NULL 의미 분리 — DTO `@Schema(description)` + PLAN "## 성공 기준" 명시
  - `serverNow` 메타 필드 필요성 미결 — 가정 섹션 기재 + PLAN 단계 결정 (사용자 결정 필요)
  - predc 다중 태그 동기 작성 가정 — 가정 섹션 별도 행 기재

---

## 표준 사전 카탈로그

본 사이클은 표준 사전 3층 모두에 신규 등록 항목이 없음 (안건 1 wtp-glossary-manager Round 1 결론). 표 생략.

---

## 신규 엔티티/DB 컬럼

**없음** — 본 사이클은 조회 전용 API 신설로 신규 엔티티·DB 컬럼·DDL·인덱스 추가 0건.

신규 Java 클래스 (DB 영향 없음):
- `api/src/main/java/com/mo/swtp/facility/dto/FacilityDailyTimeSeriesDto.java` (응답 DTO + static inner `DailyTimeSeriesPoint`)
- `api/src/main/java/com/mo/swtp/facility/service/FacilityDailyTimeSeriesService.java` (Service)
- `api/src/main/java/com/mo/swtp/opt/repository/TagPredcRangeCustomRepository.java` (예측 시계열 범위 Repository 인터페이스)
- `api/src/main/java/com/mo/swtp/opt/repository/TagPredcRangeCustomRepositoryImpl.java` (구현체)
- `api/src/main/java/com/mo/swtp/opt/repository/TagPredcRangeRepository.java` (Spring Data JPA Repository)
- `api/src/main/java/com/mo/swtp/opt/dto/TagPredcRangeDto.java` (Repository 결과 DTO)

수정 클래스:
- `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` (메서드 추가)

테스트 클래스:
- `api/src/test/java/com/mo/swtp/facility/service/FacilityDailyTimeSeriesServiceTest.java`

---

## 기존 사전·패턴과의 충돌

**없음** — 표준 사전 영향 0건, 도메인 룰 4영역 모두 비해당, 4·5·9번 섹션 정책 그대로 1440포인트 일일 시계열로 결합.

---

## PLAN 으로 전달할 결정 사항

- **도메인 모델 초안**: 5번섹션 계측 정책 (`effectiveVal`·`isPumpRunning`·`computeUnitConsumption`) + 9번섹션 예측 정책 (`predcVal`·`predc isPumpRunning`·동일 `computeUnitConsumption`) 1:1 재사용. 4·5·9번 헬퍼는 본 사이클에서 Service 내부 재구현 (코드 0건 수정).
- **API 설계**: 단일 endpoint `GET /api/facility/{facilityId}/operating-status/daily-time-series`. 응답 `CommonResponseDto<FacilityDailyTimeSeriesDto>`.
- **DB 설계 변경**: 없음. 기존 인덱스 `idx_predc_1m_h_tag_time` + `idx_rawdata_1m_h_tag_time` 활용.
- **Repository 패턴**:
  - 계측: `RawDataCustomRepository.findByTagSrlNosAndDtmRange` (5번섹션 재사용, 신규 메서드 0건)
  - 예측: 신규 `TagPredcRangeCustomRepository.findByTagSrlNosAndPredcDtmRange` (9번섹션 `TagPredcLatestRepository` 무수정)
- **Service 구조**: 6-SELECT 패턴 (Facility 1 + Instrument 1 + Tag 1 + RawData range 1 + Predc range 1 + facility/instrument 자식 조회 통합 1). `buildSeries` 메서드 3단계 분해 의무.
- **DTO 구조**: `FacilityDailyTimeSeriesDto` 3필드 + static inner `DailyTimeSeriesPoint` 8필드. `@Schema(implementation)` + `@JsonFormat("yyyy-MM-dd HH:mm:ss")` 의무 적용. `BaseAuditResponseDto` 미상속 (조회 응답).
- **정량 기준**: `buildSeries` §2.5 면책 불가 → 3단계 private 헬퍼 분해 (PLAN 단계 각 메서드 줄 수 보장 명시).
- **응답 명세 의무**: 한쪽 시리즈 부재 시 해당 분의 필드 NULL 동작 `@Schema(description)` 명시.
- **`serverNow` 메타 필드**: **미포함 결정** (사용자 2026-05-27) — actual 시리즈 마지막 `dtm` 이 분기점 역할. `FacilityDailyTimeSeriesDto` 는 3필드 유지 (`facilityId`·`facilityNm`·`points`).
- **부재 시점 정책**: **옵션 B (생략) 결정** (사용자 2026-05-27) — 한쪽이라도 존재하는 분만 응답. 둘 다 부재 시 생략. `points` 리스트 크기는 가변 (최대 1440).

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 다중 태그 actual 동기 수집 가정 (5번섹션 안건 4 Q3 동일) — 같은 1분 시점의 OPS/PWI/FRI 가 동일 `acq_dtm` 값을 갖는다 | 가정 | PLAN 단계 단위 테스트에서 동일 `acq_dtm` 픽스처로 검증 |
| 다중 태그 predc 동기 작성 가정 (신규 — 안건 4 Q4) — 같은 1분 시점의 OPS/PWI/FRI 가 AI 추론 파이프라인에 의해 동일 `predc_dtm` 슬롯에 일괄 작성된다 | 가정 | PLAN 단계 명시 — 동기 미보장 시 시점 결측 자동 생략 (`acq_dtm`/`predc_dtm` 미일치 태그값 누락 허용) |
| `serverNow` 메타 필드 포함 여부 (안건 4 Q2) — backend·frontend 시계 미세 불일치 시 경계 시점 렌더링 오류 가능 | 결정 | **미포함** (사용자 결정, 2026-05-27) — (B) 부재 시점 생략 정책과 결합하면 actual 시리즈가 현재시간 부근에서 자연 단절. frontend 는 actual 마지막 포인트의 `dtm` 으로 분기점 자연 인식 가능 → 자체 시계 의존 불필요 → 시계 불일치 오류 회피 |
| 헬퍼 추출 방향 (안건 3 블로커 Q1) — 4·5·9·10번 = 4 사용처 누적, 추출 트리거 초과 | 결정 | **본 사이클 외** — 5번섹션 PLAN1 §4-1 결정 그대로 적용 (재구현 유지). 헬퍼 추출은 별도 ANALYZE 사이클 (`FacilityOperatingStatusSupport` 또는 abstract Service 후보). 사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합 |
| 시간 범위 정의 — today = `LocalDate.now().atStartOfDay()` ~ `LocalDateTime.now()` 의 actual, predc = `LocalDate.now().atStartOfDay()` ~ `LocalDate.now().plusDays(1).atStartOfDay()` (자정~익일자정 exclusive) | 결정 | PLAN 단계 명시 — 서버 시간대 (Asia/Seoul) |
| 데이터 부재 1분 시점 처리 — actual/predc 둘 다 부재 시 응답에 포함 vs 생략 | 결정 | **옵션 B 채택** (사용자 결정, 2026-05-27) — "데이터가 존재한다면" 표출. 한쪽이라도 존재하는 분만 응답 포함, 둘 다 부재 시 생략. 사용자 시나리오 정합: 14시 현재 → actual 자정~14시 존재 분만 + predc 자정~23:59 존재 분만 (AI 추론 파이프라인이 1440 슬롯 일괄 작성 가정상 사실상 1440 모두 존재) |
| 응답 크기 SLA (안건 2 권고 Q2) — 예측 1440 × N 태그, 계측 평균 720 × N 태그 | 결정 | PLAN 단계 단일 호출 200ms 이내. 시설별 태그 수 N 상한 가정 명시 (PUMP 평균 3대 + FLWMTR 1대 → 약 10태그) |
| OPS BAD 시점 actual `actualOnPumpCnt` 자동 제외 동작 — 본 사이클은 actual 펌프 가동대수 응답 외 (사용자 Q4) | 결정 | `predcPumpOnCnt` 만 응답, `actualOnPumpCnt` 응답 제외 — 5번섹션의 `onPumpCnt` 동작과 응답 구조 차이 |
| `predc_1m_h` 의 `rgstr_dtm`(수집 메타) 이 아닌 `predc_dtm`(예측 대상 시각) 기준 표출 — 의미 충돌 없음 (안건 4 Q3 참고) | 결정 | PLAN 단계 명시 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 |
|---------|--------------------------|
| `FacilityDailyTimeSeriesService` 단위 테스트 6건 이상 GREEN — 정상 시계열 / 자정~현재 actual+predc 공존 / 현재~익일자정 predc 단독 / 분 결측 처리 / 비활성 시설 거부 / OPS predc_val 경계값 (0/1/null) | `./gradlew.bat :api:test --tests "FacilityDailyTimeSeriesServiceTest"` PASS |
| 빌드 BUILD SUCCESSFUL | `./gradlew.bat build` 출력에 `BUILD SUCCESSFUL` 포함 |
| Swagger UI 에 신규 엔드포인트 등록 + DTO 8필드 모두 노출 | `:api:bootRun` 로컬 기동 후 Swagger UI 에서 `GET /api/facility/{facilityId}/operating-status/daily-time-series` 노출 + `DailyTimeSeriesPoint` 8필드 `@Schema(description)` 한국어 명시 |
| 단일 시설 조회 응답 시간 200ms 이내 | 로컬 PostgreSQL 픽스처 (PUMP 3대 + FLWMTR 1대, 자정~현재 720분 + 자정~익일자정 1440분 predc) 투입 후 Swagger 수동 호출 — p6spy 로그 합계 200ms 이내 |
| 파티션 프루닝 활성화 (actual + predc 양쪽) | `EXPLAIN ANALYZE` 출력에 `Partitions: rawdata_1m_h_YYYYMM` 및 `predc_1m_h_YYYYMM` 만 노출 + `Index Scan using idx_*_tag_time` 노드 확인 |
| actual NULL vs predc NULL 의미 분리 DTO 명시 | Swagger UI 의 `actualElpwrAmt`·`predcElpwrAmt` `@Schema(description)` 에 NULL 발생 사유 한국어 명시 확인 |
| `buildSeries` 3단계 분해 각 메서드 50줄 이내 | 신규 Service 파일 `wc -l` + 메서드별 코드 검토 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 알람 생성·전이 없음, `alarm_h` INSERT 미수행. 조회 전용 API 로 임계값·전이 조건·복귀 조건 변경 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 미발행, `ot-integration.md §2` 아웃바운드 경로 (`ScadaOutboundPort`) 미진입. 인터록 룰 `pump_interlock_p` 미참조 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`·`ai_drvn_mod_h` 미참조. SCADA 5분 강제 전환 판정 로직 미포함. 사용자 의도·시스템 상태 변경 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 새 이력 INSERT 없음, `pump_ctrl_h`·`ai_drvn_mod_h` 무접촉. `transition_reason` 컬럼 영향 없음 |

"비해당" 단독 4건 차단 해제 조건 충족: (1) 각 행 구체 사유 명기 (위 표), (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족.

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 사이클은 표준 사전 갱신 0건 + DB 영향 0건 + 도메인 룰 4영역 비해당으로 룰 갱신 지시서 체크박스 0건. PLAN 진입 전제조건 자동 충족.

---

## 산출물

- [계획안](../../../plan/20260527/운전현황분석-10번섹션/PLAN1.md) (status: draft 예정)
