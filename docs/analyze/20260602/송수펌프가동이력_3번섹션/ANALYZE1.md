---
status: approved
created: 2026-06-02
updated: 2026-06-02
---
# 송수펌프 가동이력 3번섹션 — 전력량/주파수 시계열 차트 API 도메인 분석

## 작업 배경

- **요청 요약**: "송수펌프 가동이력" 대시보드 **3번 섹션**(좌하단) — 2개 시계열 차트 조회 **읽기 전용 API**.
  - 입력: 1번섹션 파라미터 — 시간단위 select `[시/일/월/년]` + from~to 날짜. x축 버킷이 시/일/월/년으로 변동, from~to 가 기간.
  - **차트1 송수펌프 전력량 시계열**: 전체 활성 송수펌프(`PUMP`)의 조회기간 전력량(kWh)을 **펌프별 계열**로 표출.
  - **차트2 송수펌프 주파수 시계열**: **인버터 펌프(`INVERTER_DRIVE`)만** 대상 가변 주파수(Hz)를 **펌프별 계열**로 표출. 정격 펌프는 주파수 고정이라 제외.
- **외부 산출물**:
  - `backend/image/송수펌프가동이력.png` (대시보드 와이어프레임 — 3번섹션 = 좌하단 2개 시계열 차트. 4번섹션 가로막대 가동이력 gantt 는 본 사이클 범위 외).
  - `docs/analyze/20260508/송수펌프_가동이력/03.송수펌프 가동이력 요구사항 명세서_v1.0.docx` — `ELCEG`(전력 사용량)="운전에 사용된 전기 에너지 사용량, **kWh**" 정의 확정. `PMP_ELCEG_TS_DATA`(ELCEG_VAL·BASE_DT·AGGR_UNIT), `INQ_UNIT`(시간/일/월/년), 조회 시작 ≤ 종료 검증, "데이터 없는 기간 그래프 미표시"(4.1). **주파수 차트는 요구사항 명세에 없는 사용자 추가 요건**.
- **사용자 확정 결정** (plan 승인):
  - 전력 지표 = **전력량(kWh)**, 신규 `PWQ`(적산전력량, 1분 수집 누적 미터값) 태그에서 산정.
  - 계열 = **펌프별 개별 계열** (두 차트 모두).
  - 펌프 범위 = **전체 활성 송수펌프**(`equip_type_cd=PUMP`, `use_yn=Y`) — 2번섹션 동일.
  - 집계 방식 = 온더플라이 vs 사전집계 판단 위임 → 본 ANALYZE §회의록 안건 4 에서 **온더플라이** 확정, 사전집계 테이블+스케줄러는 별도 사이클 보류.
- **직전 사이클**: 2번섹션(`PumpOperationRateController`/`Service`/`Dto`, `com.mo.swtp.instrument` 패키지, `@Tag "11. 송수펌프 가동이력"`)이 직전 커밋으로 완료.

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: PWQ(적산전력량) 측정유형 등록 + 시간단위 enum 명명 + 응답 DTO 변수명
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **PWQ 측정유형**: 신규(충돌 없음). 센서 코드는 `ot-integration.md` 1차 정의 체계 — `standard-words.md`·`domain-abbreviations.md` 중복 등록 금지 규칙 하에 운용. `PWQ`("적산전력량","kWh")를 `TagMeasurementType` 9번째 값 추가 타당. `PWI`(순시전력 kW)와 의미·단위 분리 명확, legacy EMS PWQ 선례가 컨벤션 정합 강화. `standard-terms.md` `tag_se_cd` 행 비고 8종→9종 갱신 필요 (FQI 추가 선례 동형).
  - **"적산" 단어**: 등록 불요. `elceg`(전력량 누적, kWh, `DOM_QTY_15_4`) 기등록. enum `description` 은 자유 텍스트로 표준 단어 대상 아님.
  - **시간단위 enum 명명**: `InqUnit` 권장 — `inq`(조회, 2026-05-07)+`unit`(단위, 2026-05-03) 기존 단어 조합, 요구사항 `INQ_UNIT` 정합. `TimeDivisionType` 은 `time` 표준 단어 미등록으로 컨벤션 이탈. DB 영속 아님(조회 파라미터) → `standard-terms.md` 등록 불요. 값 `HOUR/DAY/MONTH/YEAR`.
  - **응답 DTO 변수명**: `elcegVal`(전력량 — `elceg`+`val`. `amt`는 `DOM_AMT_15_2` 회계정밀도라 kWh 부적합), `freqVal`(주파수 — `freq`+`val`), `baseDtm`(버킷 시각 — `base`+`dtm`. `dt`는 `DOM_DT` DATE 전용이라 TIMESTAMP에 `dtm`, `predc_base_dtm` 선례 정합).
- **결론**: 신규 표준 단어/데이터 도메인/DB 컬럼(표준 용어) 0건. `TagMeasurementType.PWQ` enum 값 신규 + `standard-terms.md` `tag_se_cd` 비고 갱신 1건. enum 명 `InqUnit`. 변수명 `elcegVal`·`freqVal`·`baseDtm` 채택.

### 안건 2: 온더플라이 집계 vs 사전집계 판단 + 적산 차분·파티션·보존 (사용자 핵심 위임)
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약 (블로커 0 / 권고 3 / 참고 1):
  - **온더플라이 비용**: 시/일(≤1개월) — `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` Index Scan, **비용 낮음 허용**. 월/년(최대 13개월 ≈ 563만 행/태그) — 파티션 13개 순회 + GROUP BY, **비용 높음**, p6spy 500ms 초과 위험. `acq_dtm` 범위 조건으로 월 RANGE 파티션 프루닝 강제 작동 확인. 신규 인덱스·DDL 불요.
  - **사전집계 보류 타당**: 13개월 롤링 보존 한계 + 장기조회 고비용으로 별도 사이클 사전집계(10년 보존, `partitioning-and-retention.md §2`) 보류 판단 타당.
  - **적산 차분**(권고): MAX(val)-MIN(val) 채택 권고 — LAG window 는 파티션 경계 서브쿼리로 복잡도 증가. 단 **롤오버(9999→0)/리셋 시 음수·과다 위험** → 결과 < 0 또는 물리 임계 초과 시 NULL 처리 애플리케이션 가드 (단순성 우선, `coding-discipline.md §2`).
  - **corr_val 부적합**(권고): Hold Last Value 는 순간값(FRI/PRI) 보정 정책이지 **적산 미터값 보정 정책 아님**. PWQ에 corr_val(HLV) 적용 시 버킷 경계값이 직전값으로 채워져 차분 과소 산정. **PWQ는 raw_val 사용, corr_val 무조건 우선 금지**.
  - **13개월 한계**(참고): UI 조회기간 제한 또는 공백 구간 명시 응답 PLAN 결정.
- **결론**: **온더플라이 확정** (시/일 단기 기존 인덱스로 충분, 월/년 장기 = 데이터 공백+고비용으로 사전집계는 별도 사이클 보류). 적산 차분 = **MAX(raw_val)-MIN(raw_val)** per (tag, 버킷) + 음수/물리임계 NULL 가드. **PWQ는 raw_val 사용**(corr_val 미적용). 신규 인덱스·DDL 0건.

### 안건 3: API 계층 책임·패키지·컨트롤러/엔드포인트/DTO 패턴
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약 (블로커 2 / 권고 2 / 참고 1):
  - **(블로커1) 엔드포인트 2개 분리 필수**: 차트1(전체 PUMP·PWQ·차분)과 차트2(인버터 PUMP·FQI·AVG)는 펌프 스코프·태그·집계함수 3가지 모두 상이. 단일 통합 시 전 계층 `chartType` 분기로 책임 혼탁 — `coding-discipline.md §2` "요청되지 않은 유연성 금지" 역적용.
  - **(블로커2) `PumpTimeSeriesController` 신설 필수**: 2번섹션 `PumpOperationRateController` 확장은 TASK 외 파일 변경(`§3 정밀한 수정`) 해당. `@Tag("11. 송수펌프 가동이력")` 문자열 재사용은 신규 클래스 선언으로 가능(파일 변경 없음).
  - **(권고) 서비스**: 전력량·주파수 각 4단 흐름(활성PUMP→태그IN→버킷집계→DTO매핑) 독립. 단일 서비스에 두 흐름 시 50줄 초과 위험 → 2개 분리 또는 단일+`private` 헬퍼(추상화 3단 이내) 중 50줄 기준 준수 가능 안 채택.
  - **(권고) `@ArraySchema(schema=@Schema(implementation=...Point.class))`** 명시 의무 — `List<E>` element 사용자 정의 클래스.
  - **(참고) 시간단위 enum**: `common.enumtype` 배치 우선(집계 조회 공통 재사용 가능, 패키지 경계상 타 도메인 역참조 회피). 단 1건 사용이면 `instrument` 두고 재사용 시 이관도 `§2` 정합.
- **결론**: 패키지 `com.mo.swtp.instrument`. **신규 `PumpTimeSeriesController`** + **엔드포인트 2개 분리**. 서비스 2개 분리(`PumpPowerTimeSeriesService`/`PumpFrequencyTimeSeriesService`). DTO outer+Point+정적팩토리(`FacilityDailyTimeSeriesDto` 선례) + `@ArraySchema(implementation)` + `BaseAuditResponseDto` 미상속. 시간단위 enum `common.enumtype`. 집계 쿼리는 `RawDataCustomRepository` 확장(raw 도메인 소유).

### 안건 4: 도메인 규칙 정합성 — PWQ 결측 정책·적산 차분·인버터 필터·4영역
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약 (블로커 2 / 권고 1 / 참고 1):
  - **(블로커1) PWQ 결측 정책 §3 미등록**: PWI(순시)는 §3 "null 저장+집계 제외"이나 PWQ(적산 누적)는 §3 표에 없는 신규 유형. 적산값에 Hold Last Value 적용 시 차분=0 왜곡 → **GOOD 품질만 차분 집계, HLV 미적용**. `ot-integration.md §3` PWQ 행 신규 등록 의무 (미등록 시 후속 사이클 HLV 적용 오류 위험).
  - **(블로커2) 적산 롤오버 가정 미명기**: 카운터 리셋(미터 교체·SCADA 재시작) 시 MAX-MIN 음수/과다 → `coding-discipline.md §1` "가정하지 않는다" 적용. "운영 기간 중 단조증가 가정, 롤오버 시 해당 버킷 집계 제외/오류 미구현" 가정 명기 의무.
  - **(권고) UNCERTAIN**: §3 기본은 "가중치 0.5"이나 GOOD only 배제는 §3 이탈 — 차트 조회는 보수적 배제가 타당하나 이탈 가정 명기 필요.
  - **인버터 필터**(통과): `drive_type_cd='INVERTER_DRIVE'` 필터 + FQI 태그(이미 인버터 전용 `validateFqiTagAllowance`)는 상호 보완 이중 안전망, 정합. 정격펌프 제외 도메인 타당(고정주파수).
  - **4영역**(통과): 읽기 전용 — 알람·인터록·AI운전모드·이력기록 4영역 모두 비해당.
  - **(참고) omit**: "데이터 없는 버킷 생략"은 정수장 HMI 관행 정합 — "정상 제로값"과 "데이터 없음" 혼동 방지. SPEC 에 "points 배열 버킷 누락 = 데이터 없음" 명시 권고.
- **결론**: PWQ 결측 = **GOOD only 차분, HLV 미적용, raw_val 사용** (DBA 안건 2 와 수렴). `ot-integration.md §3` PWQ 행 추가 의무. 적산 단조증가 가정 + UNCERTAIN 배제 가정 명기. 인버터 필터·4영역 비해당 정합. omit 처리 타당.

> **Round 2 미실시**: 모든 블로커는 (a) 룰/가정 명기 의무 또는 (b) 명확한 단일 결정이며, corr_val/적산 차분 쟁점은 DBA(안건 2)·도메인(안건 4)이 "PWQ는 raw_val·GOOD only·HLV 미적용"으로 **독립 수렴**하여 에이전트 간 이견 없음.

---

## 표준 사전 카탈로그

### 신규 표준 단어
없음 — `elceg`(전력량)·`freq`(주파수)·`val`(값)·`inq`(조회)·`unit`(단위)·`base`(기준)·`dtm`(일시) 모두 기등록. "적산"은 enum description 자유 텍스트로 단어 등록 대상 아님 (`elceg` 기존 재사용).

### 신규 표준 데이터 도메인
없음 — 조회 전용, DB 영속 신규 0건. `elcegVal`·`freqVal`은 응답 DTO `BigDecimal`(기존 `DOM_QTY_15_4` 의미 정합), DB 컬럼 아님.

### 신규 표준 용어 (DB 컬럼명)
없음 — 신규 DB 컬럼 0건. `tag_se_cd` 행은 **비고 갱신만**(코드값 PWQ 추가, 컬럼·데이터도메인 불변).

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `tag_se_cd` (비고 갱신) | `tag`(비즈니스)+`se`+`cd` | `DOM_CODE_20` (재사용) | 기존 재사용 (코드값 추가) | `TagMeasurementType` 9종으로 확장 — `PWQ`(적산전력량, kWh) 추가. FQI 추가(2026-05-20) 선례 동형. enum name 문자열 저장으로 DDL 무영향 |

---

## 신규 엔티티/DB 컬럼

**신규 엔티티·DB 컬럼·인덱스·마이그레이션 0건.** (읽기 전용 조회 API + 온더플라이 집계)

- **코드 변경(비-DDL)**: `TagMeasurementType` enum 에 `PWQ("적산전력량","kWh")` 값 추가 — `tag_se_cd VARCHAR(20)` 문자열 저장이라 DDL/마이그레이션 무영향.
- 사용 엔티티 (모두 기존): `Instrument`/`Pump`(`instrument_m`/`pump_m`), `Tag`(`tag_m`), `RawData`(`rawdata_1m_h`).
- 사용 enum: `PumpDriveType`(INVERTER_DRIVE), `TagMeasurementType`(PWQ 신규 값·FQI), `QualityCode`(GOOD), `EquipType`(PUMP) + 신규 `InqUnit`(HOUR/DAY/MONTH/YEAR).
- 사용 인덱스 (모두 기존): `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)`, `idx_tag_m_instrument_id_tag_se_cd`.

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 판정 | 해소책 (회의 결론 일치) |
|----------|------|----------------------|
| PWQ vs PWI 측정유형 의미 충돌 의심 | **충돌 없음** | PWI=순시전력(kW), PWQ=적산전력량(kWh) 단위·물리 성격 분리. legacy EMS PWQ 선례 (안건 1) |
| PWQ 결측 시 corr_val(HLV) 적용 가능 여부 | **PWQ는 raw_val 단독 (블로커→가정·룰 명기로 해소)** | 적산 누적값에 HLV 적용 시 차분=0 왜곡. DBA·도메인 수렴 — GOOD only + raw_val. `ot-integration.md §3` PWQ 행 추가 (안건 2·4) |
| 적산 차분 음수/롤오버 | **단조증가 가정 + NULL 가드 (블로커→가정 명기로 해소)** | MAX-MIN per 버킷, 결과<0/물리임계초과 → NULL. 리셋 정밀처리 미구현 가정 (안건 2·4) |
| 단일 통합 엔드포인트 vs 2개 분리 | **2개 분리 (블로커)** | 펌프스코프·태그·집계함수 3중 상이. `chartType` 분기 = 복잡도 과잉 (안건 3) |
| 2번섹션 컨트롤러 확장 vs 신규 | **신규 `PumpTimeSeriesController` (블로커)** | 기존 컨트롤러 확장 = TASK 외 파일 변경(`§3`). `@Tag` 문자열만 재사용 (안건 3) |
| UNCERTAIN 집계 (§3 가중치 0.5 vs GOOD only) | **GOOD only (가정 명기)** | 차트 보수적 배제. §3 이탈 가정 명기 (안건 4) |

---

## PLAN 으로 전달할 결정 사항

- **패키지**: `com.mo.swtp.instrument` (`web`/`service`/`dto`). 시간단위 enum 은 `com.mo.swtp.common.enumtype` (PLAN 확정).
- **컨트롤러**: 신규 `PumpTimeSeriesController extends CommonController`, `@Tag("11. 송수펌프 가동이력")` 재사용. 엔드포인트 **2개 분리**:
  - `GET /api/instrument/pump-power-timeseries` → `ResponseEntity<CommonResponseDto<List<PumpPowerTimeSeriesDto>>>`
  - `GET /api/instrument/pump-frequency-timeseries` → `ResponseEntity<CommonResponseDto<List<PumpFrequencyTimeSeriesDto>>>`
  - `@Operation`/`@ApiResponses`(200·400·401·403·404·500). 경로 PLAN 확정.
- **서비스**: `PumpPowerTimeSeriesService` / `PumpFrequencyTimeSeriesService` 2개 분리 `@Transactional(readOnly=true)`. 패턴: 활성 PUMP(주파수는 `driveType=INVERTER_DRIVE` 필터) 조회 → 태그(PWQ/FQI) `tag_srl_no` IN 일괄 조회 → 버킷 집계 쿼리 → 펌프별 DTO 매핑. 메서드 50줄·추상화 3단 이내 (`private` 헬퍼 분리).
- **검색 DTO**: `PumpTimeSeriesSearchDto` { `inqUnit`(InqUnit enum), `fromDt`(LocalDate), `toDt`(LocalDate) }. `@ModelAttribute` 수신. 검증: `fromDt <= toDt` (위반 시 `RestApiException`). `[from.atStartOfDay, to.plusDays(1).atStartOfDay)` 범위로 변환(파티션 프루닝).
- **시간단위 enum**: `InqUnit` (HOUR/DAY/MONTH/YEAR), 각 값에 PostgreSQL `date_trunc` 인자(`"hour"/"day"/"month"/"year"`) 매핑 보유.
- **응답 DTO** (BaseAuditResponseDto 미상속, 정적팩토리 `of()`):
  - `PumpPowerTimeSeriesDto` { `pumpId`, `pumpNm`, `unit`("kWh"), `points`: `List<PumpPowerTimeSeriesPoint>` } / Point { `baseDtm`(버킷시작 LocalDateTime), `elcegVal`(BigDecimal) }. `@ArraySchema(schema=@Schema(implementation=PumpPowerTimeSeriesPoint.class))`.
  - `PumpFrequencyTimeSeriesDto` { `pumpId`, `pumpNm`, `unit`("Hz"), `points`: `List<PumpFrequencyTimeSeriesPoint>` } / Point { `baseDtm`, `freqVal`(BigDecimal) }.
- **집계 쿼리** (`RawDataCustomRepository`/`Impl`, native SQL — `§2.5` 면책, `query-tuning.md §2` 인용):
  - 전력량: `date_trunc(:unit, acq_dtm)` 버킷별 `MAX(raw_val) - MIN(raw_val)` per (`tag_srl_no`, 버킷), `tag_srl_no IN(...)`, `acq_dtm ∈ [from,to)`, `quality_cd='GOOD'`. **corr_val 미사용**. 결과 < 0 또는 물리임계 초과 → 애플리케이션 레벨 NULL/생략 가드.
  - 주파수: `date_trunc(:unit, acq_dtm)` 버킷별 `AVG(COALESCE(corr_val, raw_val))`, FQI 태그, `quality_cd='GOOD'`. (FQI는 HLV 허용 — 2번섹션 `effectiveVal` 정합)
  - `acq_dtm` 범위로 월 RANGE 파티션 프루닝 강제, `idx_rawdata_1m_h_tag_time` 활용. 신규 인덱스 0건.
- **데이터 없는 버킷**: 응답 `points` 배열에서 **생략(omit)** (요구사항 4.1). 13개월 보존 초과 구간도 자연 omit (에러 아님).
- **DB**: DDL·마이그레이션·docs/ddl 영향 0건.

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| **PWQ 전력량 차분 = `MAX(raw_val)-MIN(raw_val)` 버킷, GOOD 품질만, corr_val(HLV) 미사용** | 결정 | 적산 누적값에 HLV 적용 시 차분=0 왜곡. DBA·도메인 수렴 (안건 2·4). `ot-integration.md §3` PWQ 행 추가 |
| **적산 카운터 단조증가 가정** — 리셋/롤오버 시 결과<0/물리임계초과 버킷은 NULL/생략. 정밀 리셋 처리 미구현 | 가정 | `coding-discipline.md §1`. MAX-MIN 단순성 우선 (안건 2·4) |
| FQI 주파수 = `AVG(effectiveVal(corrVal??rawVal))`, GOOD 품질만 | 결정 | FQI는 HLV 허용 측정유형(VOI 동형). 2번섹션 FQI 정합 |
| UNCERTAIN 배제 (§3 가중치 0.5 이탈, GOOD only) | 가정 | 차트 보수적 배제 (안건 4) |
| `fromDt/toDt` = LocalDate, 시 단위도 일 경계 확장 (`[from00:00, to+1일00:00)`) | 가정 | 이미지 `2024-07-09` 날짜 형식. 시 단위는 일 범위 내 시간 버킷 |
| 13개월 보존 초과 월/년 구간 = 데이터 없는 버킷 omit (에러 아님), UI 조회기간 제한/안내는 frontend·SPEC | 가정 | DBA 참고 (안건 2) |
| `InqUnit` 위치 = `common.enumtype` | 가정 | 범용 조회단위 개념, backend 권고 (안건 3). PLAN 확정 |
| 엔드포인트 경로 = `/api/instrument/pump-power-timeseries`·`/pump-frequency-timeseries` | 미해결 | PLAN 에서 기존 `/api/instrument/**` 경로 충돌 확인 후 확정 |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 전력량 집계 단위 테스트 GREEN (Mockito) | 신규 `PumpPowerTimeSeriesServiceTest`: ①시/일/월/년 각 버킷 MAX-MIN 차분 정확 ②펌프별 계열 분리 ③전체 활성 PUMP 대상 ④GOOD only(BAD/UNCERTAIN 제외) ⑤음수/롤오버 버킷 NULL 가드 ⑥데이터 없는 버킷 omit |
| 주파수 집계 단위 테스트 GREEN (Mockito) | 신규 `PumpFrequencyTimeSeriesServiceTest`: ①인버터 펌프만 포함(정격 제외) ②버킷 AVG(effectiveVal) ③GOOD only ④펌프별 계열 |
| 엔드포인트 2개가 펌프별 시계열 반환 | `./gradlew.bat :api:test` PASS + Swagger `CommonResponseDto<List<Pump*TimeSeriesDto>>` 형태 확인 |
| 전체 빌드 무결성 (PWQ enum·신규 DTO·InqUnit) | `./gradlew.bat :common:build` + `./gradlew.bat :api:build` BUILD SUCCESSFUL |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | `rawdata_1m_h` 버킷 집계 SELECT + 시계열 응답만. `alarm_h` INSERT/UPDATE 없음. 임계값·전이·복귀 무접촉 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령(아웃바운드) 미발행. PLC 방향 통신 없음. `pump_interlock_p` 무접촉. 차트 조회 전용 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`·`ai_drvn_mod_h` 무접촉. 모드 변경·SCADA 5분 강제전환 로직 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `transition_reason`·`pump_ctrl_h` 생성 없음. 신규 엔티티/DB 컬럼 없는 조회 전용 |

> 비해당 단독 4건 차단 해제 조건 자가 점검: (1) 각 행 구체 사유 명기 완료, (2) "## 신규 엔티티/DB 컬럼" 신규 0건 — 두 조건 동시 충족. **단, `ot-integration.md §3` 센서 품질 관리(결측 대체값) 표에 PWQ 행 추가는 해당** (데이터 처리 정책 영역, 도메인 4영역과 별개 — 도메인 전문가 안건 4 블로커).

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `.claude/rules/dict/standard-terms.md` — `tag_se_cd` 행 비고 갱신: `FRI·PRI·LEI·PWI·RMS·OPS·VOI·FQI 8종` → `FRI·PRI·LEI·PWI·RMS·OPS·VOI·FQI·PWQ 9종` (PWQ=적산전력량, kWh, `TagMeasurementType` enum 매핑, 송수펌프가동이력_3번섹션 ANALYZE1)
- [x] `.claude/rules/ot-integration.md` — §3 센서 품질 관리 결측 대체값 표에 PWQ(적산전력량) 행 추가: 결측 대체값 = "null(집계 제외)", 근거 = "적산 누적값 — Hold Last Value 적용 시 버킷 차분=0 왜곡, GOOD 품질만 raw_val 차분 집계" (송수펌프가동이력_3번섹션 ANALYZE1 안건 4 도메인 블로커 해소)

> 본 작업은 신규 표준 단어·비즈니스 도메인 약어·표준 데이터 도메인·DB 컬럼(표준 용어)·DDL·엔티티 패턴 변경 0건. ROOT 어휘 사전(`swtp/.claude/rules/dict/`) 갱신 없음. 위 2건은 모두 backend 모듈 룰 (DB 컬럼 사전 비고 + 도메인 결측 정책).

## 산출물
- [계획안](../../../plan/20260602/송수펌프가동이력_3번섹션/PLAN1.md) (PLAN 단계 생성 예정)
