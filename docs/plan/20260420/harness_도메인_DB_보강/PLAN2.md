---
status: approved
created: 2026-04-20
updated: 2026-04-20
---
# 레거시 EMS/PMS → 신규 분류 체계 매핑 설계 (PLAN1 §2-5 심화)

## 목적

`PLAN1 §2-5` 에서 `legacy-mapping.md` 신설이 "레거시 테이블 → 신규 엔티티 매핑 작성 의무,
KWS-GS-* 추적표 갱신 절차, 동등성 검증 시나리오" 수준으로만 규정되어 있었다.
그러나 실제 레거시 조사 결과 EMS 95+, PMS 20+ 테이블의 suffix·역할 체계가 신규 6축
(`m / l / d / h / c / p`) 과 **기계적 매핑이 불가능**한 규모의 의미 혼재를 갖고 있음이 확인되었다.

본 PLAN2 는 §2-5 를 "어떻게 작성한다" 수준으로 승격하여 다음 네 가지를 확정한다.

1. 6축 분류의 해석 기준 (안티패턴 방어선)
2. 레거시 suffix ↔ 신규 suffix 판정 지침
3. 레거시 → 신규 매핑 seed (핵심 도메인 10~12 행)
4. 안티패턴 3종 해소 가이드 + KWS-GS-* 추적표 갱신 절차

## 배경

### 상위 계획 참조

- [PLAN1](./PLAN1.md) §2-5 / G6 항목 — "레거시(KWS-GS 산출물) → 신규 매핑/추적 절차 부재 (중간)"

### 조사 결과 요약

레거시 EMS(`legacy/ems/docs/ems_schema.md`, `ems_schema_analysis.md`)
및 PMS(`legacy/pms/docs/psm_schema.md`) 를 도메인·DB 전문가 관점으로 분석한 결과:

| 관점 | 핵심 발견 | 대표 증거 |
|------|-----------|-----------|
| DB 전문가 | 레거시 suffix 9종(`_INF / _RST / _TAG / _LOG / _VAL / _ALR / _HOUR/_DAY/_MONTH / _GRP / 없음`)이 신규 6축과 다대다(N:N). 백업·사이트 suffix(`_OLD / _BACKUP / _날짜 / _GM_250725`) 18건이 운영 스키마 잔존 | `ems_schema.md:59-66,212,291,397` |
| 도메인 전문가 | 정수장 비즈니스 4본질(설비 실체 m / 운전 명세 p / 1:N 구성 d / 시계열 이력 h) 기준 재분류 시 "`_INF` 위장 마스터" 대량 발견. 1:N 구성요소를 와이드 컬럼으로 평면화한 테이블 다수 | `ems_schema.md:215`, `psm_schema.md:25` |
| 하네스 점검 | `dev/review.md:45` 와 `wtp-domain-expert.md` / `wtp-dba-reviewer.md` 가 이미 legacy-mapping.md 를 "기준 문서"로 선언 중. 문서 미존재가 리뷰 게이트 동작을 막는 병목 | `.claude/commands/dev/review.md:45` |

### 현 신규 프로젝트 상태

- 신규 WTP 도메인 엔티티는 현재 **0건** (`RefreshToken` 1건은 인프라 성격)
- legacy-mapping.md 는 "모든 WTP 도메인 엔티티 설계의 선제 규칙 문서" 역할

## 범위

### 포함

| 대상 | 유형 | 비고 |
|------|------|------|
| `backend/.claude/rules/legacy-mapping.md` | **신설** (핵심 산출물) | 8개 섹션 |
| `backend/CLAUDE.md` | **수정** | "도메인 모델링 시 필수 참조" 표에 항목 추가 |
| `backend/.claude/rules/naming.md` | **수정** | d/l, p/c 경계 해석 주석 1~2줄 보완 (6축 자체 변경 없음) |
| `backend/.claude/agents/wtp-domain-expert.md` | **수정 검토** | §4 매핑표 미수록 도메인 점검 항목 추가 |

### 제외

- 실제 엔티티·JPA·Querydsl·마이그레이션 파일 작성
- 레거시 테이블 95+ 전건 완전 매핑 (각 도메인 슬러그에서 확장)
- 백업·사이트 스냅샷(`_OLD / _BACKUP / _날짜 / _GM_ / _HP_`) 이관 정책 → 별도 슬러그
- 마이그레이션 도구(Flyway/Liquibase) 선정

## 구현 방향

핵심 산출물인 `backend/.claude/rules/legacy-mapping.md` 는 아래 8개 섹션으로 구성한다.

---

### 섹션 1 — 목적 및 적용 범위

**파일**: `backend/.claude/rules/legacy-mapping.md`

- 신규 엔티티/테이블 설계 또는 마이그레이션 작성 시 **반드시 참조**할 것을 명시
- 다음 문서·에이전트와의 인용 관계를 헤더에 기술:
  - `dev/plan.md` — PLAN 작성 전 분석 항목에서 참조
  - `dev/review.md` — "레거시 동등성" 체크리스트의 기준 문서
  - `wtp-domain-expert.md` / `wtp-dba-reviewer.md` — 검토 에이전트의 입력 근거

---

### 섹션 2 — 분류 6축 해석 기준

`naming.md` 의 m/l/d/h/c/p 에 정수장 비즈니스 의미 해석을 부여한다.

| suffix | 본질 | 변경 빈도 | 데이터 수명 | 정수장 대표 예 |
|--------|------|-----------|-------------|----------------|
| `_m` 마스터 | **물리 설비·시설·조직 실체** | 드묾(분기·년) | 영구 (논리 삭제만) | `pump_m`, `zone_m`, `wpp_m` |
| `_p` 명세/정의 | **운전 규칙·임계값·태그 매핑·조합 정의 등 설정값** | 월·분기 튜닝 | 영구 (버전 관리) | `alarm_rule_p`, `pump_comb_p`, `tag_role_p` |
| `_d` 상세 | **마스터의 1:N 구성요소** (채널·항목·상·그룹 등 수직화) | 마스터와 함께 | 마스터와 함께 | `pump_comb_d`, `equipment_channel_d` |
| `_l` 내역 | **조회·집계 리포트용 요약 저장** (일·월·기간 집계) | 스케줄러 생성 | `db-patterns.md §6` | `monthly_peak_l`, `saving_result_l` |
| `_h` 이력 | **시계열·이벤트·감사 로그** | 초~분 단위 생성 | 파티션 DROP | `rawdata_h`, `alarm_h`, `diag_h` |
| `_c` 코드 | **공통 코드·enum 정적 테이블** | 매우 드묾 | 영구 | `alarm_severity_c`, `unit_c` |

**충돌 시 판정 규칙 3가지**

```
규칙 1. 컬럼에 "설정·정의·매핑 파라미터"가 포함되면 _m 이 아니라 _p 로 분리한다.
         (변경 빈도가 다르고 이력 추적 범위도 다르기 때문)

규칙 2. "1 row = 1 시점 이벤트/측정" 이면 _h 로 분류한다.
         원시·집계·파티션 주기 차이는 테이블 이름 접두어(rawdata_1m_h, rawdata_d_h 등)로 구분한다.

규칙 3. 1:N 차원(채널, 항목, 상(R/S/T), 그룹)을 컬럼으로 가로 펼친 경우 _d 로 수직화한다.
         같은 접두어를 갖는 컬럼이 3개 이상이면 분리 대상으로 검토한다.
```

---

### 섹션 3 — 레거시 suffix ↔ 신규 suffix 판정 지침

고정 1:1 치환표가 아닌 "1차 권고 + 판정 조건" 형식.

| 레거시 suffix | 1차 권고 | 판정 조건 (이 경우 변경) |
|--------------|----------|--------------------------|
| `_INF` | `_m` 후보 | `*_tag`, `*_thr`, `*_rnk`, `*_typ` 컬럼이 3개 이상이면 → `_p` 로 분리 |
| `_RST` / `_DIAG_*` | `_h` | 일·월 요약 집계이면 → `_l` / 최신 1건만 유효한 상태 스냅샷이면 → `_p` 검토 |
| `_LOG` / `_ALR` | `_h` | 임계값 정의 성격이면 → `_p` / 정의와 이력이 혼재하면 분해 |
| `_TAG` | `_p` 후보 | 단순 코드·enum 이면 → `_c` / 태그 실체(단위·품질 기본값)이면 → `_m` |
| `_VAL` | `_h` | 시뮬레이션 기준값(설정)이면 → `_p` |
| `_GRP` | `_m` 또는 `_d` | 그룹 자체 실체 → `_m` / 마스터의 그룹 구성원 목록 → `_d` |
| `_HOUR / _DAY / _MONTH / _INGRT` | `_h` | 집계 리포트 전용이면 → `_l` |
| 접미 없음 (PMS) | **개별 판정** | 시계열 → `_h` / 진단 임계치 → `_p` / 설비 실체 → `_m` |
| `_OLD / _BACKUP / _날짜 / _지자체코드` | **이관 금지** | 별도 슬러그에서 아카이브 스키마 격리 |

---

### 섹션 4 — 도메인별 매핑표 seed

핵심 도메인 12행. 전수 매핑은 각 도메인 구현 PR 에서 본 표에 행을 추가하여 확장한다.

| 도메인 | 레거시 테이블 | 레거시 역할 요약 | 신규 테이블(안) | 신규 suffix | 비고 |
|--------|--------------|-----------------|----------------|-------------|------|
| 펌프 실체 | `TB_CTR_PRF_PUMPMST_INF`, `TB_PUMP_INF` | 설비 실체 + SCADA 태그 매핑(20+ 컬럼) + 제어 전략·우선순위 혼재 | `pump_m` + `pump_tag_p` + `pump_oper_p` | m + p | 안티패턴 A 해소 |
| 펌프 조합 | `TB_PUMP_COMBINATION_INF`, `TB_PUMP_COMBINATION` | 조합 규칙(헤더) + 구성 펌프(1:N) | `pump_comb_p` + `pump_comb_d` | p + d | 헤더/상세 수직화 |
| 펌프 조합 계수·전력 | `TB_PUMP_CAL`, `TB_PUMP_COMB_POWER` | 유량·압력 계수 명세 + 조합별 전력 실적 | `pump_comb_cal_p` + `pump_comb_pwr_l` | p + l | 정의 vs 실적 분리 |
| 펌프 인터록·선행조건 | `TB_CTR_PUMP_REQ_OPT` | 펌프 기동 전 만족해야 할 태그 조건·선행조건 규칙(`REQ_TAG`, `REQ_CTR_TAG`, `REQ_CTR_STOP_TAG`) | `pump_interlock_p` | p | `pump_oper_p` 와 독립 분리. 인터록 규칙 변경은 운전 전략과 다른 승인 흐름 |
| 알람 | `TB_EMS_ALR`, `TB_PMS_ALR`, `TB_AL_SETTING`, `TB_GRAPH_THRESHOLD` | 발생 이력 + 임계값 정의 + 진단 결과 판정 컬럼 혼재 | `alarm_rule_p` + `alarm_h` (진단 판정 컬럼은 `diag_h` 잔류) | p + h | 안티패턴 B 해소. `alarm_h` 파티션 키: `rgstr_dtm` (월 RANGE 필수) |
| 태그 | `TB_WPP_TAG_INF`, `TB_PEAK_TAG_INF`, `TB_HMI_*_TAG`, `TB_WPP_TAG_CODE`, `TB_TAG_UNIT_INFO` | 용도별(수집/피크/HMI) 파편화, 태그 속성 중복 보유 | `tag_m` + `tag_role_p` + `tag_kind_c` + `tag_unit_c` | m + p + c + c | `TB_WPP_TAG_CODE` → `tag_kind_c`, `TB_TAG_UNIT_INFO` → `tag_unit_c`. 안티패턴 C 해소 |
| SCADA 원시 수집 | `TB_RAWDATA`, `TB_RAWDATA_15MIN`, `TB_RAWDATA_HOUR`, `TB_RAWDATA_DAY`, `TB_RAWDATA_MONTH` | 원시 + 집계 주기별 파티션 시계열 | `rawdata_1m_h`, `rawdata_15m_h`, `rawdata_1h_h`, `rawdata_1d_h`, `rawdata_1mo_h` | h | 월 RANGE 파티션 필수(`db-patterns.md §1`) |
| SCADA 집계 리포트 | `TB_MONTHLY_PEAK` | 월별 최대 피크 요약 (조회 전용) | `monthly_peak_l` | l | 조회 리포트 |
| AI 진단 결과 | `TB_AI_DIAG_MOTOR`, `TB_AI_DIAG_PUMP`, `TB_DIAG_MOTOR_PUMP`, `TB_DIAG_MOTOR_PUMP_WINDING` | AI 진단 시계열 (10+ 알람 판정 컬럼) | `diag_motor_h`, `diag_pump_h` (알람 발생 이벤트는 `alarm_h` 로 파생) | h | `acq_dtm` 파티션 키 (월 RANGE 필수). 알람 판정 컬럼은 h 내 컬럼, 발생 이벤트 → `alarm_h` |
| 진동 원시·채널 | `TB_MOTOR`, `TB_RMS`, `TB_TIMEWAVE`, `TB_SPECTRUM`, `TB_FREQ`, `TB_CHANNEL` | 원시 시계열 + 채널 구성(1:N) 혼재 | `equipment_channel_d` + `vibration_rms_h`, `timewave_h`, `spectrum_h`, `freq_h` | d + h | 채널 구성 수직화(안티패턴 C). 시계열 4종 모두 `acq_dtm` 파티션 키 (월 RANGE 필수) |
| 요금제 | `TB_RT_RATE_INF`, `TB_RT_MSTR_INF`, `TB_RT_RATE_RST` | 요금제 정의 + 계산 결과 | `rate_plan_p` + `rate_calc_l` | p + l | 정의 vs 결과 분리 |
| 절감 목표·실적 | `TB_BASE_SAVINGS_TARGET`, `TB_RST_SAVINGS_TARGET`, `TB_BASE_SAVING_CHART` | 목표(정의) + 실적(집계) | `saving_target_p` + `saving_result_l` | p + l | |
| 관망(EPA) | `TB_FP_VAL`, `TB_FR_VAL`, `TB_EPA_PUMP_FLOW`, `TB_NODE_TAG`, `TB_LINK_GRP` | 시뮬 결과 + 관망 토폴로지 정의 | `epa_node_m` + `epa_link_m` + `epa_flow_h` | m + h | 네트워크 실체 vs 수리 해석 이력 |

---

### 섹션 5 — 안티패턴 해소 가이드

레거시 조사에서 발견된 3가지 반복 패턴과 신규 프로젝트에서의 해소 방법.

#### A. `_INF` 위장 마스터

**증상**: 한 테이블에 설비 실체 컬럼 + SCADA 태그 매핑 컬럼 + 운전 전략·우선순위 컬럼 공존.
대표 사례: `TB_CTR_PRF_PUMPMST_INF`(`ems_schema.md:215`) 의 `DUTY_H / DUTY_Q` (실체)
+ `PWI_TAG / PMB_TAG` 20여 개 (태그 매핑) + `PRRT_TYP / PRRT_RNK / USER_RNK` (운전 전략).

**검사 기준**: 테이블 컬럼 중 `*_tag`, `*_thr`, `*_rnk`, `*_typ` 패턴이 3개 이상이면 분리 대상.

**해소 안**:
```
{domain}_m        — pump_id, pump_nm, duty_h, duty_q (물리 실체, 거의 불변)
{domain}_tag_p    — pump_id + 각 SCADA 태그 매핑 컬럼 (현장 튜닝 가능)
{domain}_oper_p   — pump_id + 우선순위, 제어 전략 코드, AI 태그 등 (운전 정책)
```

> 이유: 설비 교체·SCADA 리네이밍·운전 전략 변경은 각자 다른 변경 주기와 감사 이력을 가진다.

---

#### B. "알람 3중 의미" 혼재

**증상**: "알람"이라는 단어가 세 층위에 모두 사용됨.
1. 임계값 정의 — `TB_AL_SETTING`, `TB_GRAPH_THRESHOLD`
2. 진단 결과 판정 컬럼 — `TB_AI_DIAG_MOTOR.UNBALANCE_ALARM`, `DE_RMS_ALARM` 등 10+ 컬럼
3. 발생 이벤트 이력 — `TB_EMS_ALR`, `TB_PMS_ALR`, `TB_HMI_ALR_TAG`

**해소 안**:
```
alarm_rule_p   — (equipment_tp, alarm_item_cd, caution_thr, alert_thr, danger_thr,
                   hysteresis): 설비유형·항목별 알람 4단계(1=주의/2=경보/3=위험) 임계값 각각 독립 컬럼.
                   severity_cd 는 alarm_h(발생 이벤트)에서 관리
diag_h         — AI 진단 시계열 + 판정 결과 컬럼(*_alarm) 잔류
alarm_h        — (equipment_id, alarm_item_cd, severity_cd, occurred_at,
                   acknowledged_at, value): 발생 이벤트 로그 (diag_h 에서 파생 삽입)
```

> 알람 정의·진단 결과·발생 이벤트는 화면 권한·조회 패턴·보존 기간이 모두 다르다.

---

#### C. "초광역 와이드 테이블"

**증상**: 1:N 차원(채널, 권선 상, 그룹 구분)을 한 행에 가로 컬럼으로 평면화.
대표 사례: `TB_PUMP_INF`(`psm_schema.md:25`) 의
`MOTOR_NDE_AMP_TAG / MOTOR_DE_AMP_TAG / R_TEMP_TAG / S_TEMP_TAG / T_TEMP_TAG /
BRG_MOTOR_DE_TEMP_TAG / BRG_MOTOR_NDE_TEMP_TAG` 등 20+ 컬럼.

**검사 기준**: 같은 접두어를 갖는 컬럼 3개 이상 OR 항목·채널별 컬럼 10개 이상.

**해소 안**:
```
equipment_channel_d — (equipment_id, channel_role=DE|NDE|R|S|T,
                        channel_kind=AMP|TEMP, tag_id)
```
> 신규 모델·센서 추가 시 DDL ALTER TABLE 없이 행 삽입만으로 확장 가능.

---

### 섹션 6 — KWS-GS-*-AN04 추적표 갱신 절차

**원칙**: 레거시 요구사항추적표 xlsx 는 원본 보존, 코드베이스 내 추적은 이 문서 §4 매핑표로 관리.

**의무 사항**:
1. 신규 엔티티·API 를 PLAN 문서에 기재할 때, §4 매핑표에 해당 레거시 테이블이 없으면
   **매핑표 추가 행을 PLAN 문서 내 "도메인 모델" 섹션에 함께 기재** 후 별도 PR 에서 이 문서에 반영
2. `wtp-domain-expert` 가 PLAN 리뷰 시 §4 미수록 레거시 테이블 → 매핑표 추가 요구를 체크
3. 기능 누락 검증은 §7 동등성 체크리스트 참조

---

### 섹션 7 — 동등성 검증 체크리스트

신규 도메인 엔티티 PR 에서 레거시 대비 기능 누락을 방지하기 위한 항목:

```
[ ] 레거시 PK 보존: 자연키는 신규에서도 UNIQUE 제약으로 유지 또는 surrogate + UNIQUE
[ ] 일시 컬럼 정규화: 레거시 *_TIME / *_DTM / *_DATE → rgstr_dtm / updt_dtm / acq_dtm 중 하나
    (db-patterns.md §1 파티션 키 규칙 준수)
[ ] 알람 4단계: domain-glossary.md §5 의 0~3 값이 severity_cd / *_lvl 컬럼으로 보존
[ ] 보존 기간: db-patterns.md §6 정책이 신규 파티션 계획에 반영됨
    (SCADA 원시 13개월 / 알람 5년 / 제어 2년 / 마스터 영구 등)
[ ] 화면 전용 조회: 레거시 MyBatis 쿼리가 반환하던 컬럼 셋을 신규에서도 제공 가능
[ ] 인터록 선행조건: `TB_CTR_PUMP_REQ_OPT` 인터록 규칙이 `pump_interlock_p` 로 보존 (§4 매핑표 참조)
[ ] HMI 제어 로그: `TB_HMI_CTR_LOG` 제어 이력이 신규 `ctrl_log_h` 또는 상응 `_h` 테이블로 대응됨 (`db-patterns.md §6` 제어 로그 2년 보존)
```

---

### 섹션 8 — 매핑표 갱신 룰

1. 신규 엔티티/테이블 PR 은 §4 매핑표에 해당 행이 **존재하거나 추가** 되어야 merge 가능
   (`dev/review.md` "레거시 동등성" 체크리스트 항목으로 반영)
2. 매핑표 행 수정·추가는 단일 섹션 변경 PR 로 독립 처리 가능
3. 백업·사이트 스냅샷 테이블(`_OLD / _BACKUP / _날짜` 등)은 이 문서에 기재하지 않으며
   별도 슬러그에서 아카이브 스키마 정책으로 처리

---

## 도메인 모델

> 본 섹션은 `legacy-mapping.md §4` 매핑표의 신규 테이블명(안)을 참조용으로 발췌한 것이다.
> 신규 테이블명은 각 도메인 구현 PR 에서 확정되며, 본 PLAN 은 **분류 규칙**을 고정한다.

| 신규 테이블(안) | suffix | 도메인 | 레거시 근거 |
|----------------|--------|--------|------------|
| `pump_m` | m | 펌프 설비 실체 | `TB_CTR_PRF_PUMPMST_INF`, `TB_PUMP_INF` |
| `pump_tag_p` | p | 펌프 SCADA 태그 매핑 명세 | 위 테이블의 `*_TAG` 컬럼군 |
| `pump_oper_p` | p | 펌프 운전 전략·우선순위 | 위 테이블의 `PRRT_*`, `USER_RNK` 컬럼군 |
| `pump_comb_p` | p | 펌프 조합 규칙 정의 | `TB_PUMP_COMBINATION_INF` |
| `pump_comb_d` | d | 조합별 구성 펌프 목록 (1:N) | `TB_PUMP_COMBINATION` |
| `alarm_rule_p` | p | 알람 임계값·심각도 정의 | `TB_AL_SETTING`, `TB_GRAPH_THRESHOLD` |
| `alarm_h` | h | 알람 발생 이벤트 이력 | `TB_EMS_ALR`, `TB_PMS_ALR` |
| `tag_m` | m | SCADA 태그 실체 | `TB_WPP_TAG_INF` |
| `tag_kind_c` | c | 태그 분류 코드 | `TB_WPP_TAG_CODE` |
| `tag_unit_c` | c | 태그 단위 코드 | `TB_TAG_UNIT_INFO` |
| `pump_interlock_p` | p | 펌프 기동 인터록·선행조건 규칙 | `TB_CTR_PUMP_REQ_OPT` |
| `tag_role_p` | p | 태그별 용도 매핑 (수집·피크·HMI 등) | `TB_PEAK_TAG_INF`, `TB_HMI_*_TAG` |
| `rawdata_1m_h` | h | SCADA 1분 원시 시계열 (월 파티션) | `TB_RAWDATA` |
| `diag_motor_h` | h | 모터 AI 진단 결과 시계열 | `TB_AI_DIAG_MOTOR` |
| `equipment_channel_d` | d | 설비별 채널·센서 구성 (1:N 수직화) | `TB_CHANNEL`, `TB_PUMP_INF.*_TAG` 컬럼군 |

## DB 설계 변경

- **신규 DDL/마이그레이션 없음** (본 PLAN 산출물은 `.md` 문서만)
- 변경 불필요: 기존 `db-patterns.md` 의 파티션·인덱스·보존 정책은 그대로 참조
- 향후 영향: 본 `legacy-mapping.md` 가 merge 되면, 이후 모든 WTP 도메인 엔티티 PR 은
  §2 분류 기준·§4 매핑표 갱신·§7 동등성 체크리스트를 필수로 통과해야 한다.

## 테스트 전략

Java 코드 변경 없음 → 회귀 확인 목적으로만 `./gradlew.bat build` 실행.

검증 항목:

```
1. legacy-mapping.md 의 상대경로(../../legacy/docs/...) 가 실제 파일을 가리키는지 확인
2. 문서 내 도메인 용어가 domain-glossary.md 와 1:1 일치 (수작업 diff)
3. dev/review.md:45 의 "legacy-mapping.md 기준" 체크리스트가 본 문서를 참조 가능
4. wtp-domain-expert.md / wtp-dba-reviewer.md 가 본 문서 경로를 참조하는지 확인
5. check-task-unstage.sh 훅이 TASK2 의 파일 경로를 정상 파싱 (전체 상대경로 기재 필수)
6. ./gradlew.bat build 통과
```

## 제외 사항

- 실제 엔티티·JPA·Querydsl·마이그레이션 코드 — 각 도메인 슬러그에서 작성
- 레거시 95+ 테이블 전수 매핑 — seed 만 기록, 각 PR 에서 확장
- 백업·사이트 스냅샷(`_OLD / _BACKUP / _날짜 / _GM_250725 / _HP_250725`) 아카이브 정책 — 별도 슬러그
- 마이그레이션 도구(Flyway/Liquibase) 선정 — 별도 슬러그
- `naming.md` 6축 자체 개정 — 경미 주석 수준 보완만

## 예상 산출물

- [태스크](../../../tasks/20260420/harness_도메인_DB_보강/TASK2.md)

## 부록: 도메인/DB 검토 결과

### wtp-domain-expert 검토 (2026-04-20)

- **블로커(높음) 3건 → 모두 해소**
  - B1 `TB_CTR_PUMP_REQ_OPT` 매핑 누락 → §4 `pump_interlock_p` 행 추가, §7 체크리스트 추가
  - B2 `alarm_rule_p` 4단계 임계값 부족 → §5-B 컬럼 구조를 `caution_thr / alert_thr / danger_thr` 3컬럼으로 개정
  - B3 `tag_c` 레거시 근거 부재 → `tag_kind_c(TB_WPP_TAG_CODE)` + `tag_unit_c(TB_TAG_UNIT_INFO)` 로 분리 명시
- **권고(중간) 3건**
  - R1 `pump_comb_cal_p` 의 `cal` 약어 → 구현 PR 에서 `domain-glossary.md` 등재 후 확정
  - R2 `equipment_channel_d.eq_id` (약어) → 구현 PR 에서 `naming.md §PK 패턴` 준수 (`equipment_id` 풀네임 금지)
  - R3 `epa_flow_h` 물리량 범위 모호 → 각 도메인 PR 에서 유압/유속/유량 별도 행으로 분화
- **참고(낮음) 3건** — 구현 PR 에서 개별 처리

### wtp-dba-reviewer 검토 (2026-04-20)

- **블로커(높음) 3건 → 모두 해소**
  - B1 `alarm_h` 파티션 키 미확정 → §4 비고에 `rgstr_dtm` 월 RANGE 필수 명시
  - B2 `diag_motor_h / diag_pump_h` 파티셔닝 누락 → §4 비고에 `acq_dtm` 월 RANGE 필수 명시
  - B3 `vibration_rms_h` 등 4종 파티셔닝 누락 → §4 비고에 `acq_dtm` 월 RANGE 필수 명시
- **권고(중간) 4건**
  - G-1 `_l` 집계 전략 — 구현 PR 에서 스케줄러 배치 설계 시 반영
  - G-2 `alarm_h` 복합 인덱스: `(equipment_id, rgstr_dtm DESC)` 기준 — 구현 PR 에서 설계
  - G-3 `equipment_channel_d → tag_m` FK 참조 방향 — 구현 PR 에서 명시
  - G-4 `_p` 버전 관리 전략(`valid_from / valid_to` 또는 논리 삭제) — 구현 PR 에서 결정
- **참고(낮음) 2건** — 구현 PR 에서 개별 처리
