---
status: approved
created: 2026-06-11
updated: 2026-06-11
---
# 사용량트렌드-3번섹션 — 월별 최대 순시전력 피크 도메인 분석

## 작업 배경

사용량트렌드 대시보드(`backend/image/사용량트렌드.png`)의 **3번섹션 "최대 피크 현황"** = 우상단 막대그래프.
**최근 6개월(현재 월 포함)** 동안 **전체 설비의 순시전력(PWI)** 의 **월별 최대 피크**(kW)를 6개 막대로 표출하는
읽기 전용 조회 API를 신설한다.

"피크" 의 정의(사용자 명시):
- 순시전력(PWI)은 1분 단위 데이터다.
- 매 분(`acq_dtm`)마다 **전체 활성 PWI 태그값을 합산**한다(그 분에 8개 설비가 계측되면 8개 SUM).
- 이 **분단위 합계**들을 월별로 나열했을 때 **그 달의 가장 큰 값**이 그 달의 피크다.
- 이를 **최근 6개월**(현재 월 포함, today 기준 당월~5개월 전) 에 대해 월별 1개씩, 총 6개 반환.

즉 `MAX_over_month( SUM_over_facilities(PWI per minute) )`. `SUM(MAX) != MAX(SUM)` 이므로 분별 SUM → 월별 MAX 중첩 집계가 필수다.

- 외부 산출물: `backend/image/사용량트렌드.png` (대시보드 와이어프레임 — 3번섹션 막대그래프, 이미지 라벨 2025.12~2026.05 6개 막대)
- 사용자 확정 결정 (plan 단계 AskUserQuestion):
  - **조회 기간**: 현재 월 포함 최근 6개월 (서버가 today 기준 계산, 요청 파라미터 없음)
  - **빈 달 처리**: 6개 월 슬롯 모두 반환, 데이터 없는 달 `peakVal=null`
  - **코드 배치**: `com.mo.swtp.opt` 신규 클래스, 기존 `EnergyUsageTrend*`(2번섹션) 미재사용, Swagger `@Tag "16. 사용량 트렌드"` 공유
- 동형 선례: `RawDataCustomRepositoryImpl.findMaxMinuteSumElpwr`(전력피크분석 — PWI 분별 SUM 의 전구간 단일 MAX, 요금적용전력피크)가 이미 존재. 본 작업은 그 분별 SUM 구조를 **월 버킷별 MAX + 6개월 윈도우**로 확장 — 신규 발명 없이 기존 인프라 확장이 골자

## 회의록 (5인 회의 — Round 1 종결, 블로커 0건이라 Round 2 불요)

### 안건 1: 표준 사전 정합성
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **신규 표준 단어 0건** — `peak`(2026-06-04)·`elpwr`·`val`(2026-05-18)·`base`·`dtm`·`unit` 전부 기등록. `peakVal`=`peak`+`val`, `baseDtm`=`base`+`dtm` 조합
  - **`max`·`status`·`point` 는 클래스명 토큰 → 등록 면제** — 2번섹션 ANALYZE1 의 "클래스명·엔드포인트 어휘는 DB 컬럼 조합 재료 아님 → 등록 면제" 판정과 동형. 단 `max` 를 응답/프로젝션 **필드명**에 쓰면 단어 등록 검토 대상이 되므로, 오케스트레이터가 프로젝션 record 필드를 `maxVal` → **`peakVal`**(기등록 `peak`+`val`)로 명명하여 `max` 단어 등록을 회피(§2 단순성 — 불필요한 사전 항목 회피). `max` 는 `MaxPeakStatus*` 클래스명 토큰으로만 잔존(면제)
  - **`point` 동명 혼동 주의** — 중첩 record `MaxPeakStatusPoint` 의 `Point` 는 데이터 포인트(시리즈 원소) 의미. `domain-abbreviations.md` 비즈니스 도메인 약어 `point`(관로 계측 분기점, 도입 예정)와 **층위 별개**(단어/클래스 토큰 vs 비즈니스 도메인). `EnergyUsageTrendPoint`(2번섹션) 선례 동형 — 문서 명기로 충분, 룰 갱신 불요
  - **신규 표준 데이터 도메인·표준 용어 0건** — 신규 DB 컬럼 0건. `peakVal`→`DOM_QTY_15_4`, `baseDtm`→`DOM_DTM` 재사용(응답 DTO 한정, 사전 등록 무관)
  - **2번섹션 패밀리 충돌 없음** — `RawDataBucketSumDto(totalVal)` vs `RawDataBucketPeakDto(peakVal)` 는 `sum`/`peak` 의미 분리 명확. `peakVal`(실측 월별 피크) ≠ `target_peak_elpwr`(운전원 목표값, `opt_peak_target_p`) ≠ `billingPeakElpwr`(요금적용피크) — `target`·`billing` 접두어 미사용으로 충돌 없음
- **결론**: 4층 사전 **갱신 0건**. `max` 단어 등록은 프로젝션 필드 `peakVal` 명명으로 회피. `point` 층위 구분은 본 문서·PLAN 명기로 처리

### 안건 2: DB 집계 쿼리·성능 (DBA 2차 승인)
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **신규 중첩 native 쿼리 채택** — 분별 SUM(inner GROUP BY `acq_dtm`) → 월별 MAX(outer GROUP BY month). `findMaxMinuteSumElpwr`(전구간 단일 MAX, 12개월) 의 월 버킷 확장형
  - **파티션 프루닝 정상** — `acq_dtm >= :startDtm AND acq_dtm < :endDtm` 리터럴 범위가 월 RANGE 프루닝 작동(`date_trunc` 무관, WHERE 절 미사용)
  - **권고(낮음) → 반영**: outer `GROUP BY base_dtm`(alias) 대신 **`GROUP BY date_trunc('month', acq_dtm)`** 전체 표현식 사용 권고(실행 오류 방지 안전판). `'month'` 는 **리터럴**(named `:unit` 아님)이라 표현식 2회 등장해도 Hibernate 위치 파라미터 중복 전개(42803) 무관
  - **인덱스·성능 적정** — `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 가 inner 등가(tag_srl_no)+범위(acq_dtm) 커버. 6개월 구간은 12개월 `findMaxMinuteSumElpwr` 대비 절반이라 선례 대비 열위 없음
  - **빈 과거 월 무행 반환** — RANGE 파티션 부재 범위는 SELECT 오류 없이 무행 반환 → 서비스 6슬롯 null 병합과 정합
  - 참고(낮음): 실데이터 `EXPLAIN (ANALYZE, BUFFERS)` 500ms 점검 권고 / 태그 100건 초과 시 `unnest(text[])` 전환(현재 14건이라 `IN (:tagSrlNos)` 유지)
- **결론**: 신규 DB 레벨 중첩 쿼리 채택. outer GROUP BY 는 `date_trunc('month', acq_dtm)` 전체 표현식. 블로커 0건

### 안건 3: 계층 책임·패턴 정합
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **`RawDataBucketPeakDto` 신규 생성 권고(중간)** — `RawDataBucketSumDto`(2번섹션 전용, `totalVal`="PWQ 버킷 합산 kWh") 재사용 시 `totalVal` 이 "최대 피크" 를 표현 못해 가독성 위반(§2 시니어 자문). 신규 record + Javadoc "사용량트렌드-3번섹션 전용 월별 최대 피크 프로젝션" 명기
  - **`Clock` 빈 등록 권고(중간)** — `LocalDate.now(clock)` 으로 시각 의존 제거(결정적 단위 테스트). `final Clock` 필드 + `@RequiredArgsConstructor` 양립 위해 `api` Config 에 `@Bean Clock clock(){ return Clock.systemDefaultZone(); }` 신규 등록. TASK 체크박스에 Config 파일 경로 명시 의무. 테스트는 `Clock.fixed(...)` 오버라이드
  - **참고(낮음)**: `points` 에 `@ArraySchema(schema=@Schema(implementation=MaxPeakStatusPoint.class))` 의무 / Service 6슬롯 병합·윈도우 생성 private 분해(`buildMonthKeys`·`mergeToSlots`)로 50줄 예방 / 신규 native 메서드 빈 태그 `List.of()` early-return / 신규 native SQL `// §2.5 면책 (query-tuning.md §2)` 주석 의무
  - **파라미터 없는 GET → ErrorCode 생략 타당** — 서버가 6개월 윈도우 내부 결정, 사용자 입력 유효성 대상 없음. 빈 태그·빈 결과는 6슬롯 null 정상 응답. 2번섹션 `INVALID_SEARCH_PERIOD` 미재사용이 §3(불필요 자산 미추가) 정합
- **결론**: opt 신규 클래스 + `RawDataBucketPeakDto` 신규 + `Clock` 빈 등록 + §2.5 주석·`@ArraySchema`·private 분해·early-return. 검증/ErrorCode 생략. 블로커 0건

### 안건 4: 도메인 4영역·scope·이중계상
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **도메인 4영역 전부 비해당** — read-only 집계. 알람 임계값·인터록·운전 모드·이력 기록 무접촉, 신규 엔티티·DB 컬럼 0건
  - **PWI 품질 정책 정합** — `quality_cd='GOOD'` + `SUM(COALESCE(corr_val, raw_val))` 은 `ot-integration.md §3` PWI 정책(BAD 시 null 저장+집계 제외, HLV 미적용은 결측 처리 의미) + `findMaxMinuteSumElpwr` 선례 동형
  - **빈 달 `peakVal=null` 타당** — 측정 부재(파티션 없음/GOOD 0건)를 0으로 채우면 "피크 없음"과 "진짜 0kW"가 구분 불가. null 명시가 정확
  - **당월 부분 데이터 포함 타당** — "현재 시점까지 이번 달 피크" 는 추세·목표 피크(`target_peak_elpwr`) 대비 모니터링에 유효
  - **권고(중간) → 반영**: 이중계상 가정 명기 의무. PWI 전체 합산 scope 에서 메인 인입 PWI 태그 공존 시 이중계상 가능 — 현재 시딩 확인 후 가정 섹션 명기
  - **참고(낮음)**: 분별 SUM 중 일부 태그 BAD/결측 시 해당 분 과소 합계 한계 — 트렌드 목적 수용 사유 명기
- **결론**: 도메인 4영역 비해당 확정. 이중계상·당월 부분·분별 결측 가정 명기. 블로커 0건

### 안건 5: PWI 시딩 실측 검증 (오케스트레이터 단독 — dev DB read-only)
- 안건 4 도메인 권고(이중계상 확인)를 해소하기 위해 dev DB 의 활성 PWI 태그를 실측 조회
- **결과 (14건)**:
  - 펌프 PWI 8건 — `정수조#1·#2`(PWTF) 각 펌프#1~#4
  - 건물 전력계측기(ELCMTR) PWI 6건 — `활성탄여과지(ACFB)`·`약품동(CHMB)`·`탈수기동(DEWB)`·`여과지동(FLTB)`·`전오존동(POZB)`·`태양광(SOLAR)` 각 1건
- **판정**:
  - **이중계상 없음** — 펌프 PWI(정수조)와 건물 ELCMTR PWI(여과지/약품동 등)는 **서로 다른 물리 부하/시설**을 계측하며, 전체를 합산하는 **메인 인입(main incomer) 총괄 미터가 부재**. 2번섹션 PWQ("ELCMTR 부재") 와 시딩 구성은 다르나(PWI 는 ELCMTR 포함) 합산 중복은 동일하게 없음
  - **태양광(SOLAR) 발전 전력 포함 유의** — `SOLAR-ELCMTR-PWI` 는 소비가 아닌 **발전(생산) 전력** 성격일 수 있다. "전체 설비" scope 확정에 따라 전체 PWI 합산에 포함되나, "최대 피크 수요" 의미와 발전 전력 합산이 혼재될 수 있음 — 발전/소비 구분 필요 시 별도 사이클 재검토 (가정 명기 + 사용자 확인 대상)
- **결론**: 이중계상 가정은 "메인 총괄 미터 부재 → 중복 없음" 으로 확정. 태양광 발전 전력 합산 포함은 **사용자 확정(2026-06-11 AskUserQuestion — 전체 14건 포함, 태양광 발전 포함)**. 발전/소비 구분 필요 시 별도 사이클

## 표준 사전 카탈로그

### 신규 표준 단어
없음 (기존 재사용: `peak`+`val`, `base`+`dtm`, `unit`). `max`·`status`·`point` 는 클래스명 토큰 등록 면제.

### 신규 표준 데이터 도메인
없음 (신규 DB 컬럼 0건).

### 신규 표준 용어
없음 (신규 DB 컬럼·엔티티 0건). `RawDataBucketPeakDto`·`MaxPeakStatusDto` 는 application-layer 클래스로 표준 용어 사전 대상 외.

## 신규 엔티티/DB 컬럼

**없음.** 신규 엔티티·DB 테이블·DB 컬럼·마이그레이션 0건. 기존 `rawdata_1m_h`·`tag_m` 읽기 전용 집계만 수행한다.
신규 자산은 모두 `api` 모듈 조회 전용 Java 클래스(projection record·DTO·Service·Controller)와 `api` Config 의 `Clock` 빈 1건뿐이다.

## 기존 사전·패턴과의 충돌

충돌(블로커 높음) 0건. 5인 회의 4개 안건 모두 블로커 0건. 권고(중간) 3건 — `RawDataBucketPeakDto` 신규 생성(backend), `Clock` 빈 등록(backend), 이중계상 가정 명기(domain) — 은 아래 PLAN 전달 결정·가정 섹션으로 해소. 참고(낮음) — DBA outer GROUP BY 전체 표현식·`EXPLAIN` 점검, backend `@ArraySchema`·private 분해·early-return, domain 분별 결측 한계 — 도 동일 반영.

## PLAN 으로 전달할 결정 사항

- **패키지·계층** (`com.mo.swtp.opt`, 기존 `EnergyUsageTrend*` 미재사용):
  - `opt/dto/MaxPeakStatusDto` 신규 — `{unit:"kW", points:List<MaxPeakStatusPoint>}`, 중첩 record `MaxPeakStatusPoint(LocalDateTime baseDtm, BigDecimal peakVal)`. `peakVal` nullable. `baseDtm` 월 시작 일시 `@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")`(2번섹션 직렬화 정합). `points` 에 `@ArraySchema(schema=@Schema(implementation=MaxPeakStatusPoint.class))` 의무. BaseAuditResponseDto 미상속(집계 뷰)
  - `opt/service/MaxPeakStatusService` 신규 — `@Transactional(readOnly=true)`, `final Clock` 주입
  - `opt/web/MaxPeakStatusController` 신규 — `extends CommonController`, `@Tag "16. 사용량 트렌드"`, `@RequestMapping("/api/opt/max-peak-status")`, `@GetMapping`, 파라미터 없음, `ResponseEntity<CommonResponseDto<MaxPeakStatusDto>>`, `@ApiResponses` 200/401/500
  - `raw/dto/RawDataBucketPeakDto(LocalDateTime baseDtm, BigDecimal peakVal)` 신규 record + Javadoc "3번섹션 전용 월별 최대 피크 프로젝션"
  - `raw/repository/RawDataCustomRepository`(+Impl) 에 `findMonthlyMaxMinuteSumElpwr(List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm): List<RawDataBucketPeakDto>` 추가
  - `api` Config(예: `ApiClockConfig` 또는 기존 config 클래스)에 `@Bean Clock clock(){ return Clock.systemDefaultZone(); }` — 파일 경로 PLAN/TASK 명시
  - **신규 ErrorCode 0건** (`OptErrorCode` 무변경)
- **합산 쿼리** (DB 레벨, `// §2.5 면책 (query-tuning.md §2)` 주석 의무):
  ```sql
  SELECT date_trunc('month', acq_dtm) AS base_dtm, MAX(minute_sum) AS peak_val
  FROM (
      SELECT acq_dtm, SUM(COALESCE(corr_val, raw_val)) AS minute_sum
      FROM rawdata_1m_h
      WHERE tag_srl_no IN (:tagSrlNos)
        AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
        AND quality_cd = 'GOOD'
      GROUP BY acq_dtm
  ) m
  GROUP BY date_trunc('month', acq_dtm)
  ORDER BY base_dtm
  ```
  - outer GROUP BY 는 `date_trunc('month', acq_dtm)` 전체 표현식(DBA 권고). `'month'` 리터럴이라 42803 무관. GOOD only + `COALESCE(corr_val, raw_val)`(PWI HLV 허용). 빈 태그 `List.of()` early-return
- **Service 흐름**: (1) `YearMonth cur = YearMonth.from(LocalDate.now(clock))` → `start = cur.minusMonths(5).atDay(1).atStartOfDay()`, `endExclusive = cur.plusMonths(1).atDay(1).atStartOfDay()`, 6개 월 키 = `cur.minusMonths(5)..cur`, (2) `tagRepository.findByTagSeCdAndUseYn(PWI, Y)` 태그 수집(빈 태그 → 6슬롯 전부 null), (3) `findMonthlyMaxMinuteSumElpwr` → `Map<LocalDateTime,BigDecimal>`, (4) 6개 월 키 순회 `MaxPeakStatusPoint(monthStart, map.getOrDefault(monthStart,null))`. `buildMonthKeys`·`mergeToSlots` private 분해
- **검증 권고(낮음, 성공 기준 외)**: 실데이터 `EXPLAIN (ANALYZE, BUFFERS)` 500ms 점검 / 태그 100건 초과 시 `unnest` 전환 재검토

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 현재 활성 PWI 14건 = 펌프 8(정수조#1·#2) + 건물 ELCMTR 6(활성탄여과지·약품동·탈수기동·여과지동·전오존동·태양광). 메인 인입 총괄 PWI 미터 부재 → 전체 합산 시 **이중계상 없음** (서로 다른 물리 부하). 향후 메인 인입 PWI 태그 시딩 시 이중계상 위험 별도 사이클 재검토 | 가정 | dev DB 실측 확인 (안건 5, domain-expert 권고 해소) |
| **태양광(SOLAR) PWI 는 발전(생산) 전력 성격이나, 사용자 확정으로 전체 14건 합산에 포함** (전체 설비 문자 그대로). `findByTagSeCdAndUseYn(PWI, Y)` 그대로 사용 — facility 필터 불요. 발전/소비 구분 필요 시 별도 사이클 | 결정 | 안건 5 + AskUserQuestion 2026-06-11 사용자 확정 (전체 14건 포함) |
| 당월(2026-06)은 진행 중 부분 데이터 — "현재 시점까지 이번 달 피크" 로 표출 | 결정 | 사용자 "현재 월 포함" 확정 (domain-expert 타당 판정) |
| 분별 SUM 중 일부 PWI 태그 BAD/결측이면 해당 분 합계가 과소 — 진짜 피크 시점 결측 시 피크 과소 산정 가능. 트렌드 파악 목적에서 수용 | 가정 | `ot-integration.md §3` GOOD 필터 범위 내 (domain-expert) |
| 빈 달 `peakVal=null` — 측정 부재(파티션 없음/GOOD 0건)와 진짜 0kW 피크를 구분. dev 는 2026-05~ 파티션만 존재 → 2026-01~04 null 예상 | 결정 | 사용자 "빈 달 null" 확정 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `MaxPeakStatusService` 가 6개 월 모두 데이터 → 6개 points 각 peakVal 존재·baseDtm 오름차순 | Mockito 단위 테스트 — tagRepository·rawDataRepository(+고정 Clock) mock, 6슬롯 매핑 GREEN |
| 일부 월 결측 → 해당 슬롯 peakVal=null, 슬롯 수 항상 6 | 단위 테스트 — sparse 결과 입력 시 결측 월 null·6슬롯 단언 |
| PWI 태그 0건 → 6슬롯 전부 null (repo 미호출 또는 빈 결과) | 단위 테스트 — 빈 태그 입력 시 6슬롯 null |
| 쿼리 결과 빈 리스트 → 6슬롯 전부 null | 단위 테스트 — empty 버킷 입력 |
| `findByTagSeCdAndUseYn(PWI, Y)` 필터 사용 | verify 단언 |
| 전체 빌드·QClass 재생성 무결성 | ./gradlew.bat :api:test BUILD SUCCESSFUL |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | rawdata_1m_h read-only 집계. `quality_cd='GOOD'` 필터는 §3 품질 정책 소비이며 알람 임계값·전이·복귀·`alarm_h` 무접촉 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 조회 전용 API. 제어 명령 발행·기동 차단·재검사 어느 것도 미해당, `pump_interlock_p` 무접촉 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd`·`ai_drvn_mod_p`·`ai_drvn_mod_h` 무접촉. 모드 전환·강제 전환 트리거 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 신규 DB 컬럼·엔티티 0건. `pump_ctrl_h`·`ai_drvn_mod_h` 무접촉 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

**갱신 0건.** 4층 사전(ROOT 표준 단어·표준 데이터 도메인·비즈니스 도메인 약어 + backend 표준 용어) 모두 신규 등록·폐기 항목 없음 (`wtp-glossary-manager` 결론 — 신규 DB 컬럼 0건, `peak`·`val`·`base`·`dtm`·`unit` 전부 기등록, `max`·`status`·`point` 는 클래스명 토큰 등록 면제, 프로젝션 필드는 `peakVal` 명명으로 `max` 등록 회피). 사전 관련 PLAN 진입 전제조건 충족.

## 산출물
- [계획안](../../../plan/20260611/사용량트렌드-3번섹션/PLAN1.md)
