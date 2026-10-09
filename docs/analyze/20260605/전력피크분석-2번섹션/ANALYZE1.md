---
status: approved
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석-2번섹션 — 4지표 조회 API 도메인 분석

## 작업 배경

전력피크 분석 화면(`backend/image/전력피크분석.png`)의 **좌측 2번 섹션**(`전력피크 분석`)을 백엔드로 구현한다. 직전 1번섹션(`docs/analyze/20260604/전력피크분석-1번섹션/ANALYZE1.md`)이 `opt` 도메인에 목표 피크치 설정·저장(`opt_peak_target_p` + SSE)을 완성했고, 본 작업은 그 산출물(`target_peak_elpwr`)과 예측 시계열(`predc_1m_h`)을 소비하는 **읽기 전용 집계 API 1개(GET)** 다. **신규 테이블·DDL·컬럼 없음** — 4지표는 응답 DTO 필드로만 표출되는 합성 뷰다.

화면 2번 섹션 4지표:

| 지표 | 정의 | 소스 |
|------|------|------|
| 총순시전력 | 순시전력(PWI) 태그를 가진 모든 설비의 PWI 최신값 합산 (kW) | `rawdata_1m_h` 최신값 |
| 목표피크전력 | 1번섹션에서 설정한 목표 피크치 (kW) | `opt_peak_target_p` |
| 요금적용전력피크 | 최근 12개월 분(1분)단위 PWI 합산값 중 MAX (kW) | `rawdata_1m_h` 12개월 |
| 전력피크예상시간 | 예측 PWI 합이 목표피크 초과하는 최근접 미래 시각 (없으면 "없음") | `predc_1m_h` |

### 사용자 확정 사항 (요건 잠금)
| 항목 | 결정 |
|------|------|
| 결측 합산 정책 | "존재하는 GOOD만 합산"(부분합 허용) — 분단위 합산 시 그 시각 GOOD 태그값만 더함, 총순시전력과 일관 |
| 갱신 방식 | 단순 GET 폴링 (SSE 미적용 — 목표값 변경은 이미 1번섹션 SSE 구독 중) |
| 요금적용전력피크 응답 | 값(kW)만 반환 (발생 시각 미포함) |
| 도메인 배치 | 기존 `opt` 도메인(`com.mo.swtp.opt`) 확장 |

### 조사로 확정된 사실 (live DB, 2026-06-05 확인)
- 활성 PWI 태그 = **8개** (`tag_m.tag_se_cd='PWI' AND use_yn='Y'`) — 소팬아웃.
- `predc_1m_h` 의 예측 태그 = PWI와 **동일 `tag_srl_no` (8/8 매칭)** — 요구사항 "예측도 동일 순시전력태그 합산" 성립.
- `predc_1m_h` 데이터 범위 = 현재 시드 기준 `NOW()` 직후까지만 존재(미래 지평 짧음 → 현재 `expectedPeakDtm` 대개 "없음", 이미지 "없음" 표출과 정합).
- `rawdata_1m_h` 월 RANGE 파티션 + `idx_rawdata_1m_h_tag_time(tag_srl_no, acq_dtm DESC)` btree + `acq_dtm` BRIN. `predc_1m_h` 월 RANGE 파티션 + `idx_predc_1m_h_tag_time(tag_srl_no, predc_dtm)`.

> 외부 산출물: `backend/image/전력피크분석.png` (2번 섹션 = 4지표 게이지·수치 표출).

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 응답 DTO 4필드 어휘 + 신규 표준단어 (DB 컬럼 미생성)
- 호출 에이전트: `wtp-glossary-manager` (Round 1)
- **Round 1 결론**:
  - `total`(합계/총합) **신규 등록** — `standard-words.md` 미등록 확인. `totalElpwrAmt`(`FacilityOperatingStatusDto`) 실사용 공백 해소(`acq`·`val` 공백 해소 선례 동형). `sum`(동사)·`amt`(금액)·`cnt`(개수)와 의미 분리. 풀네임 5자(`drive`·`start`·`peak`·`rate` 선례).
  - `billing`(요금적용) **신규 등록 — `billing` 풀네임(7자) 확정**(사용자 결정 2026-06-05). `standard-words.md`·`domain-abbreviations.md` 미등록. `segment`·`quality`·`subscr` 7자 풀네임 선례 정합. 대안 `chrg`(4자)·`tariff`(6자) 미채택(의미 명확성·실무 체감 우선).
  - `expected` **불요** — `predc`(예측, OT 예측 도메인 SSOT) 재사용으로 "예상시간" 표현. `expected`(일반 어감)는 `predc` 동의어 충돌 위험. → 필드 `predcPeakDtm`.
  - `instant` **불요** — `elpwr`(전력 순시) 이미 "순시" 의미 보유. → 필드 `totalElpwr`.
  - **`standard-terms.md`(DB 컬럼 SSOT) 등록 불요 확정** — DB 테이블·컬럼 미생성. 응답 DTO 필드명일 뿐.
  - 확정 필드명: `totalElpwr`(`total`+`elpwr`) · `targetPeakElpwr`(기존 재사용) · `billingPeakElpwr`(`billing`+`peak`+`elpwr`) · `predcPeakDtm`(`predc`+`peak`+`dtm`).

### 안건 2: 요금적용전력피크 — 12개월 분단위 SUM 후 MAX (신규 native)
- 호출 에이전트: `wtp-dba-reviewer` (Round 1)
- **Round 1 결론**:
  - `acq_dtm` BETWEEN 으로 월 RANGE 파티션 프루닝 정상(12개월 한정). `idx_rawdata_1m_h_tag_time(tag_srl_no, acq_dtm)` 등가 선행 원칙 부합 — 8태그 Index Range Scan, 신규 인덱스 불요.
  - `COALESCE(corr_val, raw_val)` = effectiveVal SQL 동치 적정(`findAvgValueBuckets` 선례).
  - **권고(중간)**: 1년 누적 시 12개월×8태그 ≈ 4.2M행 GROUP BY HashAggregate → 기본 work_mem(4MB) disk spill 우려, 200ms SLA 미충족 가능. 완화: `SET LOCAL work_mem` 또는 EXPLAIN ANALYZE 실측 후 사전집계(`rawdata_15m_l` 등) 별도 ANALYZE 도입. **현재 단일 native 적정**(화면 진입 단발 조회·소팬아웃, `coding-discipline.md §2` 추측 추상화 금지). ※ 현 시드 데이터는 1일치라 경량.

### 안건 3: 전력피크예상시간 — 예측 합 GROUP BY+HAVING (신규 native)
- 호출 에이전트: `wtp-dba-reviewer` (Round 1) · `wtp-backend-engineer` (리포지토리 배치)
- **Round 1 결론 (DBA)**:
  - **블로커(높음) → 해소**: `predc_dtm >= :now` 하한만으로는 파티션 프루닝 상한 부재 → predc 3년 보존 시 최대 36파티션 스캔 위험. **`horizonEnd` 상한 바인딩 파라미터 필수**. → 설정 프로퍼티 `opt.peak.predc-horizon-hours`(기본 48h)로 `horizonEnd = now + N시간` 상한 강제. 현재월~익월 1~2 파티션만 스캔.
  - `(tag_srl_no, predc_dtm)` 오름차순 인덱스 → `ORDER BY predc_dtm ASC` Sort 생략 가능(EXPLAIN 확인 — 참고/낮음).
  - 예측 쿼리 리포지토리는 **opt 도메인 배치 적정**(`predc_1m_h`=opt 자산, `TagPredcRangeCustomRepositoryImpl` 선례).
- **Round 1 결론 (backend)**: 기존 `TagPredcRangeCustomRepository`(범용 범위 조회) 확장 대신 **신규 `PeakPredcCustomRepository` 신설** — "예측 합 목표초과 최근접" 집계 특화 책임 분리(섹션 7/9/10 전용 리포지토리 분리 선례).

### 안건 4: 클래스 배치·계층 구조·응답 DTO
- 호출 에이전트: `wtp-backend-engineer` (Round 1)
- **Round 1 결론**:
  - 클래스명 `PeakPowerAnalysisController`/`Service`/`Dto` 적정(`naming.md` `{도메인}Controller/...` 정합). 1번 `PeakTarget*`(목표값 마스터)과 2번 `PeakPowerAnalysis*`(분석 집계 뷰) 의미 분리 명확.
  - 응답 DTO `PeakPowerAnalysisDto` **`BaseAuditResponseDto` 미상속** 적정 — 복수 소스 집계 합성 뷰(영속 마스터 1:1 부재), `FacilityOperatingStatusDto`·`PumpStateDto` 선례 동형(`api-patterns.md §적용 범위` 통지/집계 적용 외). **record 비권고 → `@Getter` + private 기본생성자 + 정적팩토리**(프로젝트 일관성, Jackson/Swagger 부담 회피). `expectedPeakDtm` `@JsonFormat(shape=STRING, "yyyy-MM-dd HH:mm:ss")` 의무.
  - PWI 태그 식별: `TagRepository.findByTagSeCdAndUseYn(PWI, Y)` 신규 파생쿼리 — 전 PWI 직접 조회(instrument 경유 불요, 스코프가 On펌프·시설 한정이 아님).
  - `effectiveVal`(corrVal 우선·GOOD만) **private 헬퍼 복제** 채택 — 메모리 "사이클 간 자산 자동 원용 금지" 정합. `FacilityOperatingStatusService`·`RawDataCustomRepositoryImpl` 가 이미 각자 복제 중. 공유 유틸 추출은 Service 계층 3건 이상 누적 시 별도 ANALYZE.
  - `@Transactional(readOnly=true)` 클래스 레벨 + 헬퍼 4개 위임 50줄 이내. **native 메서드(`billingPeakElpwr`·`predcPeakDtm`)에 `§2.5` 면책 주석 필수**(`query-tuning.md §2`, 누락 시 REVIEW 블로커).

### 안건 5: 도메인 룰 4영역 점검 + 결측/MAX 정책 도메인 검증
- 호출 에이전트: `wtp-domain-expert` (Round 1)
- **Round 1 결론**:
  - 도메인 4영역(알람 4단계·인터록·AI 운전모드·이력 기록) **모두 비해당**(조회 전용·제어 무접촉). 각 구체 사유 아래 "## 도메인 룰 4영역 점검" 표.
  - PWI 전 태그 합산(On/Off 무관)이 "총 순시전력" 정의에 **부합**(현재 설비 전체 순간 전력 합계). 1번섹션 On펌프 합산과는 집계 목적이 다른 별개 지표, 충돌 없음.
  - `effectiveVal`(corrVal 우선·GOOD만) + GOOD 필터는 `ot-integration.md §3` PWI 결측 정책("GOOD만 집계, BAD/UNCERTAIN null 저장+제외")과 완전 정합.
  - **참고(낮음) 3건** → 가정 섹션 기재: ① 부분합 허용 시 결측 분에서 요금적용전력 과소추정 가능(한전 청구 피크와 차이) ② 1분 MAX vs 한전 실무 15분 평균 수요전력 불일치(1분 스파이크 포함) ③ GOOD 필터 누락 방지 PLAN 검증 기준 명시. 모두 사용자 확정 결정·블로커 아님, 내부 모니터링 근사값·UI 주석 권고 수준.

---

## 표준 사전 카탈로그

### 신규 표준 단어
| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `total` | 합계/총합 | 신규 | `standard-words.md` 미등록. `totalElpwr` 조합 재료. `totalElpwrAmt`(FacilityOperatingStatusDto) 실사용 공백 해소(`acq`·`val` 선례). `sum`·`amt`·`cnt` 와 의미 분리. 풀네임 5자(`drive`·`peak` 선례) |
| `billing` | 요금적용 | 신규 (풀네임 7자 확정) | `standard-words.md`·`domain-abbreviations.md` 미등록. `billingPeakElpwr` 조합 재료. `billing`(7자 풀네임, `segment`·`quality`·`subscr` 선례) 사용자 확정. `opt` 비즈니스 도메인과 층위 다름 |

### 신규 표준 데이터 도메인
없음 — `DOM_QTY_15_4`(kW, `totalElpwr`·`billingPeakElpwr`) · `DOM_DTM`(`predcPeakDtm`) 모두 기존 재사용. DBA 2차 승인 불요.

### 신규 표준 용어 (DB 컬럼)
없음 — **DB 테이블·컬럼 미생성 확정**. `standard-terms.md` 등록 불요. (아래는 응답 DTO 필드명 — 사전 등록 대상 아님, 참고용)

| DTO 필드명 | 조합 | 데이터 도메인 | 비고 |
|-----------|------|-------------|------|
| `totalElpwr` | `total`(신규) + `elpwr`(기존) | (DTO, `DOM_QTY_15_4` 형식) | 총순시전력 합산 |
| `targetPeakElpwr` | `target`+`peak`+`elpwr` (모두 기존) | (DTO, `DOM_QTY_15_4` 형식) | 1번섹션 컬럼 camelCase 1:1 |
| `billingPeakElpwr` | `billing`(신규) + `peak`+`elpwr`(기존) | (DTO, `DOM_QTY_15_4` 형식) | 요금적용전력피크 |
| `predcPeakDtm` | `predc`+`peak`+`dtm` (모두 기존) | (DTO, `DOM_DTM` 형식) | 예측 피크 초과 최근접 시각, NULL 허용 |

분류값: **신규 / 기존 재사용 / 유사 충돌 / 폐기·통합**

---

## 신규 엔티티/DB 컬럼

없음 — 본 작업은 신규 엔티티·테이블·컬럼·DDL·인덱스를 생성하지 않는다. 기존 `tag_m`·`rawdata_1m_h`·`opt_peak_target_p`·`predc_1m_h` 를 조회·집계하는 합성 뷰 API 만 추가한다.

추가되는 코드 자산(전부 `api` 모듈, 엔티티 무변경):
- `PeakPowerAnalysisController`/`Service`/`Dto` (`com.mo.swtp.opt.web`/`service`/`dto`)
- `PeakPredcRepository`/`PeakPredcCustomRepository`/`PeakPredcCustomRepositoryImpl` (`com.mo.swtp.opt.repository`)
- `TagRepository.findByTagSeCdAndUseYn` (신규 파생쿼리 1개)
- `RawDataCustomRepository`/`Impl` 12개월 MAX native 메서드 1개 추가
- 설정 프로퍼티 `opt.peak.predc-horizon-hours`(기본 48) — 예측 파티션 프루닝 상한

---

## 기존 사전·패턴과의 충돌

| 충돌 후보 | 판정 | 해소 |
|----------|------|------|
| `total`·`billing` 신규 단어 vs 기존 어휘 | 유사 충돌 0건 | `sum`·`amt`·`cnt`(total) / `opt`·`chrg`·`tariff`(billing) 와 의미·어근 분리. `billing` 약어 형태만 사용자 확정 |
| `expected`/`instant` 신규 등록 | 불요 | `predc`(예측)·`elpwr`(순시전력) 재사용으로 회피 — 동의어 중복 방지 |
| 예측 쿼리 — 기존 `TagPredcRangeCustomRepository` 확장 vs 신설 | 섹션별 책임 분리 선례 | **신규 `PeakPredcCustomRepository`** — 범위 조회 vs 집계 계산 책임 분리 |
| `effectiveVal` 헬퍼 — 공유 유틸 vs 복제 | 메모리 "사이클 간 자산 자동 원용 금지" | private 헬퍼 **복제**(Service 2건 시점, 추상화 강제 금지 §2) |
| `predc_dtm >= now` 하한만 — 파티션 프루닝 상한 부재 | DBA 블로커(높음) | `opt.peak.predc-horizon-hours`(기본 48h) 상한 바인딩 — 1~2 파티션 한정 |

---

## PLAN 으로 전달할 결정 사항

- **도메인 모델**: 신규 엔티티 없음. 코드 자산 — `PeakPowerAnalysisController`(GET `/api/opt/peak-power-analysis`) + `PeakPowerAnalysisService`(`@Transactional(readOnly=true)`, 헬퍼 4개) + `PeakPowerAnalysisDto`(`@Getter`+정적팩토리, BaseAuditResponseDto 미상속) + `PeakPredcRepository`/`PeakPredcCustomRepository`/`Impl`(api, opt). 기존 `PeakTargetService.getPeakTarget()` 재사용.
- **쿼리 설계**:
  - 총순시전력: `TagRepository.findByTagSeCdAndUseYn(PWI,Y)` → `RawDataCustomRepository.findLatestByTagSrlNos` 재사용 → Service GOOD 필터 + `effectiveVal` + `reduce(ZERO,add)`.
  - 요금적용전력피크: 신규 native `RawDataCustomRepository.findBillingPeak...(tagSrlNos, start, end)` → `SELECT MAX(minute_sum) FROM (SELECT acq_dtm, SUM(COALESCE(corr_val,raw_val)) ... WHERE quality_cd='GOOD' GROUP BY acq_dtm)`. `start = now-12개월`. 값만 반환, NULL→ZERO.
  - 전력피크예상시간: 신규 native `PeakPredcCustomRepository.findEarliestPredcDtmOverTarget(tagSrlNos, now, horizonEnd, target)` → `SELECT predc_dtm ... WHERE predc_dtm>=:now AND predc_dtm<:horizonEnd GROUP BY predc_dtm HAVING SUM(predc_val)>:target ORDER BY predc_dtm ASC LIMIT 1`. `target<=0`(미설정)이면 쿼리 생략 후 null.
- **DB 설계 변경**: 없음(읽기 전용). 신규 DDL·인덱스 0건.
- **설정**: `opt.peak.predc-horizon-hours`(기본 48) `application-common.yml` + `@ConfigurationProperties` 또는 `@Value`. PLAN 에서 실제 예측 생성 지평 대조 후 기본값 확정.
- **적용 패턴**: api-patterns Service/Repository 3계층, native §2.5 면책 주석, `@JsonFormat` 초단위, Swagger `@Tag`/`@Operation`/`@ApiResponses`.
- **엣지 fallback**: PWI 0개 → total/billing=ZERO, expected=null. 목표피크 0 → expected 쿼리 생략 null. 데이터 부재 → ZERO/null.

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 요금적용전력피크 = "존재하는 GOOD만 합산(부분합)" → 한전 청구 피크가 아닌 **내부 모니터링 근사값**. 결측 분 과소추정 가능 | 가정 (사용자 확정) | domain-expert 참고(낮음) ① — UI 주석 권고 |
| 요금적용전력피크 = **1분 단위 MAX**(한전 실무 15분 평균 수요전력과 상이, 1분 스파이크 포함 가능) | 가정 (사용자 확정) | domain-expert 참고(낮음) ② — 내부 운전 참고 지표, UI 주석 권고 |
| `quality_cd='GOOD'` 필터 누락 방지 — BAD/UNCERTAIN 합산 포함 시 `ot-integration.md §3` 위반 | 결정 → PLAN 성공 기준 | domain-expert 참고(낮음) ③ — "GOOD 필터 단위 테스트 GREEN" 명시 |
| `billing` 표준단어 약어 형태 → `billing` 풀네임(7자) 확정 | 결정 (사용자 2026-06-05) | glossary 안건 1 해소 |
| `opt.peak.predc-horizon-hours` 기본값(48h) — 실제 예측 생성 지평 대조 | 미해결 → PLAN 결정 | 현재 시드 predc 지평 짧음(now 직후), 운영 시 24~48h 가정 |
| 12개월 MAX 단일 native 1차 채택 — 1년 누적 후 EXPLAIN 200ms 초과 시 사전집계 별도 ANALYZE | 가정 | DBA 권고(중간). 현재 시드 1일치 경량 |
| 단일 backend 인스턴스·폴링 갱신(SSE 미적용) | 가정 (사용자 확정) | 목표값 변경은 1번섹션 SSE 구독으로 처리 |
| 예측 태그 = PWI 태그 동일 `tag_srl_no`(8/8 매칭 확인) | 가정 | live DB 확인. 신규 PWI 태그 추가 시 예측 생성도 동반 가정 |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 총순시전력 = GOOD PWI 합(BAD/UNCERTAIN/null 제외, corrVal 우선) | `PeakPowerAnalysisServiceTest` 단위 — GOOD 2건+BAD 1건+null 1건 입력 시 GOOD 2건만 합산 검증 (Mockito) |
| 목표피크 미설정(0) 시 expectedPeakDtm=null | 단위 테스트 — target=0 stub 시 예측 쿼리 미호출 + null 반환 verify |
| PWI 태그 0개 시 total/billing=0, expected=null | 단위 테스트 — findByTagSeCdAndUseYn 빈 리스트 stub 시 fallback 검증 |
| 예측 합 목표 초과하는 최근접 미래 시각 반환 | 단위 테스트 — repository stub 으로 ASC LIMIT 1 결과 매핑 검증 |
| 요금적용전력피크 12개월 MAX 파티션 프루닝 | 통합/EXPLAIN — `acq_dtm` BETWEEN 월 파티션 프루닝 + 200ms SLA 관찰 |
| 예측 쿼리 horizonEnd 상한 파티션 프루닝 | 통합/EXPLAIN — `predc_dtm < horizonEnd` 1~2 파티션 한정 확인 |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | `rawdata_1m_h`·`predc_1m_h`·`opt_peak_target_p` 조회 전용 집계. 알람 임계값·전이·복귀 조건 무접촉, `alarm_h` 기록 없음. 단 `quality_cd='GOOD'` 필터는 §3 품질 정책 직접 구현 경로 — 누락 시 BAD/UNCERTAIN 오염, PLAN 검증 기준 명시 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 경로 없음. `pump_interlock_p` 평가·기동 차단·복구 재검사 무접촉 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`/`ai_drvn_mod_h` 미접촉. 사용자 의도·시스템 상태 변경 없음. SCADA 5분 강제 전환 트리거 무관 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h.transition_reason`·`pump_ctrl_h` 기록 경로 없음. 읽기 전용 응답 DTO 반환만 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `total`(합계/총합) 신규 등록 (조합 재료, `totalElpwr`·`totalElpwrAmt` 실사용 공백 해소)
- [x] `swtp/.claude/rules/dict/standard-words.md` — `billing`(요금적용) 신규 등록 (조합 재료, `billingPeakElpwr` — 풀네임 7자 확정)

> 표준 데이터 도메인 신규 0건(`DOM_QTY_15_4`·`DOM_DTM` 재사용). 표준 용어(DB 컬럼) 신규 0건(DB 미생성 — `standard-terms.md` 미갱신). 비즈니스 도메인 약어 신규 0건(`opt` 재사용). `peak`·`target`·`elpwr`·`predc`·`dtm`·`cd` 기등록.

## 산출물
- [계획안](../../../plan/20260605/전력피크분석-2번섹션/PLAN1.md)
