---
status: approved
created: 2026-06-08
updated: 2026-06-08
---
# 시설별 사용량 2번섹션 — 운영시설 전력 사용량 조회 도메인 분석

## 작업 배경

`backend/image/시설별사용량.png` 대시보드의 **2번섹션** 을 구현한다. 1번섹션(파라미터)에서 받은 3개 파라미터 — **집계단위([시/일/월]) · 검색시작일자 · 검색종료일자** — 로, **운영시설(FacilityGroup.OPERATION) 각각** 의 전력 사용량 4지표를 조회하는 **읽기 전용 API** 다.

| 지표 | 의미 | 산출 |
|------|------|------|
| 순시전력(kW) | 조회기간 중 가장 마지막 순시전력 | 종료시점 직전 분의 시설합 PWI |
| 전력량(kWh) | 조회기간 누적 전력량 | PWQ(적산전력량) 버킷 차분(MAX-MIN) 합 |
| 최대전력(kW) | 집계단위로 집계 시 발생한 가장 큰 순시전력 | 버킷별 시설합 PWI MAX 중 최댓값 |
| 최대전력 일시 | 최대전력 발생 시각(집계단위 입도) | 최대값이 발생한 버킷 시작시각 |

- 외부 산출물: `backend/image/시설별사용량.png` (2번섹션 — 운영시설 카드, 시각 로드 완료)
- 사용자 사전 확정 사항 (계획 단계 AskUserQuestion): (1) 전력 측정 대상 = 시설 내 **모든 계측기**의 PWI/PWQ 합산, (2) **하위 시설 재귀 롤업**, (3) 데이터 부재 시 `null`.
- **최대전력 해석**: `peakElpwr = MAX over buckets( MAX over minutes in bucket( 분별 시설합 PWI ) )`. 수학적으로 전체기간 최대 분합과 동일 → 값은 집계단위 불변, **최대전력일시의 입도만 집계단위에 따라 달라진다**(시→시각, 일→일자, 월→월). 사용자 예시("시간단위로 max 순시전력 … 발생한 시간")와 정합.

## 회의록 (5인 회의)

### 안건 1: 응답 DTO 변수명 용어 확정
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 4지표 변수명 구성 단어(`elpwr`·`elceg`·`peak`·`total`·`dtm`) 전부 기존 등록 — 신규 표준 단어 0건. 최대전력 = `peakElpwr` 권고(`peak` 2026-06-04 "전력 피크(최대 수요 전력)" 정식 등록, `max` 신규 등록 회피 — `max`는 `min`의 설계치 대칭어 문맥, `peak`는 시계열 집계 최댓값 문맥). `billingPeakElpwr`(opt, 전역 12개월 분합 MAX)·`targetPeakElpwr`(목표)와 수식어 구조로 완전 분리 — 의미 충돌 없음, javadoc 경계 명시로 충분. 전력량 = `elceg` 단독 권고(`elceg` 한글 논리명 "전력량(누적)"에 누적 의미 내포 → `total` 접두는 중복 수식). DB 컬럼 아니므로 `standard-terms.md` 등록 의무 없음.
- **결론**: 변수명 확정 — 순시전력 `elpwr` / 전력량 `elceg` / 최대전력 `peakElpwr` / 최대전력일시 `peakElpwrDtm`. **신규 사전 등록 0건, 룰 갱신 불필요**.

### 안건 2: 운영시설 그룹 필터 + 하위 시설 재귀 롤업 도메인 정합성
- 호출 에이전트: `wtp-domain-expert`, `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 블로커 2건(높음) — (B1) 롤업 귀속 규칙("최근접 운영시설 조상" vs "모든 상위 조상 중복 합산")이 UI 카드 집계 결과를 바꾸는 도메인 판단이므로 ANALYZE 가정 섹션 명기 의무(`coding-discipline.md §1`), (B2) 재귀 하위 시설에 STORAGE/NETWORK가 섞일 때 포함/제외 기준 가정 미기재. `facility_type_cd` 필터 강제 룰(`entity-patterns.md §도메인 룰`)은 "도메인 룰 분기 필요 여부"가 아니라 "자식 종류 혼선 방지" 자체가 목적 → 단순 집계라도 카드 선택은 `facility_type_cd IN (OPERATION 8종)` 명시가 **룰 준수**(필터 생략 시 STORAGE/NETWORK 혼입 = 룰 위반).
  - **wtp-backend-engineer**: 6-SQL 고정 흐름 + 트리 롤업 in-memory 처리(시계열-마스터 JOIN 회피)는 `query-tuning.md §1` N+1 방지 정합. 단 Service 퍼블릭 메서드 50줄 초과 확실 → `buildFacilityTreeMap(...)`·`assembleResponse(...)` 등 private 헬퍼 분해 의무(면책 불가 영역). 호출 스택 Controller→Service→헬퍼 2단으로 3단 이내.
- **결론**: 카드 = OPERATION 8종 IN 필터. 롤업 멤버십 = 운영시설 ~ 다음 중첩 운영시설 직전까지의 **모든 하위 시설(그룹 무관)** 계측기 → 각 태그를 **최근접 운영시설 조상**에 귀속(중복 합산 방지). 두 규칙을 가정 섹션에 명기(B1·B2 해소). Service 헬퍼 분해 의무.

### 안건 3: 신규 native 집계 쿼리 2종 + unnest 매핑 + 파티션 프루닝
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 블로커 0건. `rawdata_1m_h JOIN unnest(:tags::text[], :roots::text[])` 인라인 매핑은 상수 VALUES 전개 읽기 전용 JOIN(마스터 테이블 접근 0, 물리 FK 아님) → `partitioning-and-retention.md §1` "시계열→마스터 FK 금지" 정책과 양립. `acq_dtm >= :start AND < :end` 월 RANGE 파티션 프루닝 정상 작동, `tag_srl_no`(등가)→`acq_dtm`(범위) 순서가 `idx_rawdata_1m_h_tag_time` 정합. 분별 시설합 후 버킷 MAX 순서가 `SUM(MAX)≠MAX(SUM)` 정확. `:unit` enum 4값 제약 인젝션 안전. 권고 2건(중간) — (W1) 조회기간 무제한 시 12파티션 수천만 행 집계 슬로우쿼리 → 기간 상한 + `EXPLAIN ANALYZE` PLAN 명시, (W2) Hibernate 6 native `String[]` 바인드가 `text[]` 자동 변환 안 되는 케이스 존재 → `createArrayOf` 명시 또는 VALUES 폴백 PLAN 정의. 참고 1건(낮음) — DISTINCT ON 동률 시 "가장 이른"(base_dtm ASC) vs "가장 최근" 요건 확인.
- **결론**: 쿼리 2종 설계 승인. 조회기간 상한·unnest 바인드 폴백·동률 정책을 PLAN 결정 사항으로 전달. DB 컬럼/데이터 도메인 신설 0 → DBA 2차 승인 대상 없음.

### 안건 4: Service 계층 구조 + 면책 적용
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `FacilityEnergyUsageService` facility 도메인 배치 적합(응답이 운영시설 단위, OPERATION 필터가 facility 자산; opt는 AI 산출 저장 전용). native 집계 2종은 `query-tuning.md §2` 면책 영역 → 메서드 상단 `// §2.5 면책 (query-tuning.md §2 ...)` 인용 주석 **의무**(누락 시 REVIEW 블로커). Service 일반 오케스트레이션은 면책 불가. `effectiveVal`·`validDeltaOrNull`는 동형 복제가 맞음(2건 누적, common 추출은 3건+ 시 별도 ANALYZE — "요청되지 않은 추상화 금지"). `FacilityEnergyUsageDto`는 BaseAuditResponseDto 미상속(`api-patterns.md` 요약·집계 응답 적용 외) + @Getter + private 생성자 + 정적팩토리 + @JsonFormat 정합. SearchDto는 instrument `PumpPeriodSearchDto` 상속 금지(도메인 간 의존 회피) → facility 독립 동형 작성.
- **결론**: facility 도메인 배치·동형 복제·DTO 미상속·SearchDto 독립 확정. native 2종 인용 주석 + Service 헬퍼 분해를 TASK 검증 항목으로.

### 안건 5: 데이터 부재 표현 + 집계단위 검증 + 음수 차분 + 최대전력일시 입도
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: PWI 집계 제외(null)·PWQ GOOD-only 차분(HLV 미적용)은 `ot-integration.md §3` 정합(통과). 권고 — 음수 차분(카운터 리셋) 제외 기준이 `ot-integration.md §3`에 명문화 없음 → "음수 차분 전량 제외(카운터 리셋 판정)" 기준 가정 명기. 최대전력일시 입도(버킷 시작시각 vs 정확한 분) 가정 명기. null(측정 없음) vs 0kW(정상 0) 구분은 PLAN 응답 DTO null 허용 명시 권고. 도메인 4영역(알람/인터록/운전모드/이력) 전부 비해당.
- **결론**: null 표현 = "측정 없음"(0kW와 구분). 음수 차분 = 전량 제외(WARN 로그). 최대전력일시 = 버킷 시작시각(집계단위 입도). 집계단위 = HOUR/DAY/MONTH만 허용(YEAR 거부 — 요구 3종). 모두 가정 섹션 명기.

## 표준 사전 카탈로그

### 신규 표준 단어
**없음** — 변수명 구성 단어 전부 기존 등록(`elpwr` 2026-04-25 · `elceg` 2026-04-25 · `peak` 2026-06-04 · `total` 2026-06-05 · `dtm` 2026-04-23). `max` 신규 등록 회피(`peak` 재사용).

### 신규 표준 데이터 도메인
**없음** — DB 컬럼 신설 없음. 응답 DTO 필드 타입은 `BigDecimal`(DOM_QTY_15_4 대응)·`LocalDateTime`(DOM_DTM 대응) 재사용.

### 신규 표준 용어
**없음** — `peakElpwr`·`peakElpwrDtm`·`elpwr`·`elceg`는 응답 DTO 필드(DB 컬럼 아님) → `standard-terms.md` 등록 의무 없음(`hr`·`actl`·`inq`·`total`·`billing`·`pump` DTO 전용 단어 선례 동형, 단 본 건은 모든 단어 기존 등록이라 단어 등록도 0건).

## 신규 엔티티/DB 컬럼

**없음** — 읽기 전용 조회 API. 신규 엔티티·DB 테이블·DB 컬럼·인덱스 0건. 추가 산출물은 응답/검색 DTO 2종, Service 1종, Repository 메서드(native SQL) 2종, Controller 엔드포인트 1개, FacilityRepository 파생 메서드 1개, FacilityErrorCode enum 값 1개(`INVALID_SEARCH_PERIOD`).

## 기존 사전·패턴과의 충돌

| 충돌 후보 | 판정 | 해소책 |
|----------|------|--------|
| `peakElpwr` vs `billingPeakElpwr`·`targetPeakElpwr` (opt) | 충돌 없음 | 수식어 구조·집계 범위·소속 도메인 상이. javadoc 경계 명시 (facility 단위 조회기간 버킷 MAX) |
| `facility_type_cd` 필터 강제 룰 | 위반 아님 | 카드 선택에 OPERATION 8종 IN 필터 **명시**가 룰 준수. 생략 시 STORAGE/NETWORK 혼입 = 위반 |
| 시계열→마스터 FK 금지 vs unnest JOIN | 충돌 없음 | unnest는 상수 인라인 매핑(마스터 JOIN 0, 물리 FK 아님) |
| instrument `PumpPeriodSearchDto` 상속 | 회피 | facility 독립 SearchDto 동형 작성 (도메인 간 의존 회피) |

## PLAN 으로 전달할 결정 사항

**도메인 모델**: 신규 엔티티 없음. `FacilityType.getGroup()==OPERATION` enum 필터로 `OPERATION_TYPES`(PRSF/WTBLD/CHMB/ACFB/POZB/FLTB/DEWB/SOLAR) 도출(하드코딩 회피 SSOT).

**API 구조** (facility 도메인):
- `FacilityController` 엔드포인트 1개 추가 — `GET /api/facility/energy-usage`, `@ModelAttribute FacilityEnergyUsageSearchDto`, `@Tag("06. 시설물 관리")`, 응답 `ResponseEntity<CommonResponseDto<List<FacilityEnergyUsageDto>>>`, `@ApiResponses` 200/400/401/403/500.
- `FacilityEnergyUsageSearchDto` (facility.dto) — fromDt·toDt(LocalDate)·inqUnit(InqUnit) + `toStartDtm()`/`toEndExclusiveDtm()`/`isValid()`. `PumpPeriodSearchDto` 비상속 동형.
- `FacilityEnergyUsageDto` (facility.dto) — facilityId·facilityNm·elpwr·elceg·peakElpwr·peakElpwrDtm(전부 null 허용). BaseAuditResponseDto 미상속, @Getter + private 생성자 + 정적팩토리 `of(...)` + @JsonFormat("yyyy-MM-dd HH:mm:ss").
- `FacilityEnergyUsageService` (facility.service) — `@Transactional(readOnly=true)`. 6-SQL 흐름 + 트리 롤업 매핑 + 음수차분/부재 헬퍼. 퍼블릭 메서드 50줄 이내 위해 `buildOperatingRootMap`·`aggregate`·`assemble` private 헬퍼 분해.
- `FacilityRepository.findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(Collection<FacilityType>, YnType)` 파생 메서드 추가.
- `RawDataCustomRepository(+Impl)` native SQL 2종 — `findFacilityLatestMinuteSumElpwr`(순시전력 마지막값), `findFacilityBucketPeakElpwr`(최대전력+시각 ARGMAX). 전력량은 기존 `findEnergyDeltaBuckets` 재사용. 각 신규 메서드 상단 `// §2.5 면책 (query-tuning.md §2)` 인용 주석 의무.
- `FacilityErrorCode.INVALID_SEARCH_PERIOD(400)` 추가(기간 검증 실패).

**DB 설계 변경**: 없음(스키마 변경 0). `docs/ddl/` 갱신 불필요.

**적용 패턴**: `PumpSummaryService`/`FacilityOperatingStatusService` 미러링(시설 N건 × 태그 IN절 in-memory 집계), `PeakEnergyTrendService` 음수차분 제외 동형 복제, `FacilityOperatingStatusDto` 응답 패턴.

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 롤업 귀속 = 각 태그를 **최근접 운영시설 조상**에 귀속(운영시설 중첩 시 중복 합산 방지) | 가정 | B1 해소. 사용자 확정 "재귀 포함"의 귀속 규칙 구체화 — 승인 게이트에서 확인 |
| 롤업 멤버십 = 운영시설 + 그 하위 **모든 시설(STORAGE/NETWORK 그룹 무관)** 계측기 포함. 카드 선택만 OPERATION 8종 IN 필터 | 가정 | B2 해소. 하위 STORAGE/NETWORK 시설 전력도 상위 운영시설 카드에 합산 |
| 집계단위 = HOUR/DAY/MONTH만 허용, YEAR 거부 (`isValid()`) | 결정 | 요구 [시/일/월] 3종 |
| 음수 차분(PWQ 카운터 리셋) = 전량 제외(0 미합산) + WARN 로그 | 결정 | `PeakEnergyTrendService.validDeltaOrNull` 동형 |
| 최대전력일시 입도 = 버킷 시작시각(시→시각, 일→일자, 월→월) | 결정 | 사용자 예시 "집계 단위에 맞게" |
| 동률 최대전력 = 가장 이른 버킷(base_dtm ASC) | 가정 | DBA 참고 — "가장 최근" 요건이면 DESC 변경. 승인 게이트 확인 |
| 데이터 부재(전구간 BAD/태그 없음) = 4지표 `null`("측정 없음", 0kW와 구분) | 결정 | 사용자 확정 |
| 조회기간 상한 제약 도입 여부·임계(권고: 13개월 보존 또는 1년) | 미해결 | PLAN 결정 — DBA W1 권고 |
| `unnest(:arr::text[])` Hibernate 6 native 배열 바인드 PoC + 폴백(`createArrayOf`/VALUES) | 미해결 | PLAN/IMPL 검증 — DBA W2 권고 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 한 시설 PWI 다태그 분별 SUM 후 버킷 MAX 정확(`SUM(MAX)≠MAX(SUM)` 회피) | 신규 단위 테스트 — 2태그 같은 분 합산값이 버킷 max 로 산출 GREEN |
| 음수 차분(카운터 리셋) 버킷 전력량 합산 제외 | 신규 단위 테스트 — 음수 delta 버킷 미합산 GREEN |
| 전구간 BAD 품질 시 4지표 null | 신규 단위 테스트 — GOOD 0행 → DTO 필드 null GREEN |
| PWI만/PWQ만 보유 시 해당 지표만 산출 | 신규 단위 테스트 — 독립 맵 lookup GREEN |
| 하위 시설 롤업 — 자식 시설 계측기 전력이 최근접 운영조상 카드에 합산 | 신규 단위 테스트 — 트리 매핑 검증 GREEN |
| 운영시설 카드 = OPERATION 8종만(STORAGE/NETWORK 카드 미출현) | 신규 단위 테스트 — 결과 facilityTypeCd 전부 OPERATION GREEN |
| native 쿼리 파티션 프루닝 작동 | 통합 테스트 — `EXPLAIN (ANALYZE, BUFFERS)` 파티션 제한 확인 (로컬 PostgreSQL, 월 파티션 선행 생성) |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 전력 집계 조회 API — 알람 임계값·전이 조건·복귀 조건 무접촉 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 읽기 전용 조회 — 기동 선행조건 검사·제어 명령 발행 경로 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 변경 없음, 모드 전환 트리거 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h`·`pump_ctrl_h` 무접촉. `rawdata_1m_h` 조회 전용(쓰기 없음) |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

**없음** — 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 도메인 약어 등록 0건, DB suffix·엔티티 패턴 변경 0건. 모든 어휘가 기존 등록 항목 재사용이며 DB 스키마 변경이 없으므로 룰 파일 갱신이 불필요하다. (`wtp-glossary-manager` Round 1 확정 — 신규 등록 항목 없음.)

## 산출물
- [계획안](../../../plan/20260608/시설별사용량-2번섹션/PLAN1.md) (다음 단계 `/dev:plan` 에서 작성)
