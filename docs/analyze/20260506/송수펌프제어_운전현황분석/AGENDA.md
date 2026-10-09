---
status: draft
created: 2026-05-06
updated: 2026-05-07
---
# 운전현황 분석 (FR-PMP-002) — 5인 회의 의제 정리

> 본 문서는 `/dev:analyze` 5인 회의 진입 전 **인간 팀원 사전 검토용 의제 정리**다. 회의록(`ANALYZE1.md`) 과는 별도 산출물이며, 회의 진입 시점에 본 의제를 ANALYZE1.md 의 안건별 회의록으로 흡수한다.

---

## 0. 작업 정의

| 항목 | 값 |
|------|----|
| 슬러그 | `송수펌프제어_운전현황분석` |
| 규모 | Large |
| 메뉴 ID (명세서) | FR-PMP-002 (에너지 관리 > 송수펌프 제어 > 운전현황 분석) |
| 첨부물 | `02.운전현황 분석 요구사항 명세서_v0.2.docx` (365 라인 추출, 9 장 구성) |
| 신규 엔티티 (명세서) | 6 (DRVN_STTS_INQ · PREDC_ANLS_CMP_DATA · DRVN_ANLS_RSLT · DRVN_ANLS_DWLD_HSTRY · PMP_OPRTNG_HSTRY · BRANCH_MEAS_PREDC_DATA) |
| 외부 시스템 | Python AI 서버 (예측) + EPANET 관망해석 (분석 이력) |
| Fix Cycle | 아님 (신규 슬러그) |

### 0.1 명세서 핵심 기능 (요약)
- 운전현황 분석 조회 (시간/분 단위 조회범위 + 차트 단위 일/주/월)
- AI 운전모드 변경 (관리자만) — AI / AI추천(AI_RECOMD) / AI분석(AI_ANLS)
- 운영 현황 — 펌프 가동 조합 + 전력정보 + 전력원단위 + 펌프대수
- 실제·예측 그래프 — 성능점·저항점·회귀식 표시
- 펌프조합 선택점 자동 갱신 (성능점 또는 저항점 기준)
- 예측 조회 자료 다운로드 (Python AI 산출 결과)
- 분석 이력 비교자료 다운로드 (EPANET 산출 결과)
- 주요 분기별 예측/실측 그래프 (수요량·관압 비교, 수위는 실측만)
- 다운로드 형식: CSV / XLSX

---

## 1. 의존성·전제 조건 (회의 시작 전 사용자 확인 필요)

### 1.1 마스터도메인설계 ANALYZE1 (2026-05-02/03)
- **결정 내역**:
  - `pwtf_m`·`dwt_m` → `facility_m` (UUID PK + JOINED 다형성, `facility_type_cd` discriminator) 자식 흡수
  - `pump_m` → `instrument_m` (UUID PK + JOINED 다형성, `equip_type_cd` discriminator) 자식 흡수
  - `tag_m` 신설 — `tag_srl_no` 자연키 PK (외부 할당)
  - `rawdata_1m_h` — `tag_val` → `raw_val`·`corr_val`·`quality_cd` 분리 + BaseEntity 4 적용
- **상태**: ANALYZE1 작성 완료, **PLAN approved 상태 미확인** (이 작업 안건 A5 주제)

### 1.2 권한메뉴 ANALYZE1 (2026-05-06)
- **결정 내역**: `menu_m` (UUID PK + 시스템 전체 UNIQUE) + `menu_role_r` (N:M 매핑) + `UserRole` enum (ADMIN/USER)
- **상태**: ANALYZE1 작성 완료. PLAN approved 상태 미확인 (이 작업 안건 A9 주제)

### 1.3 pumpcontrol ANALYZE1 (2026-04-25)
- **결정 내역**: `ai_drvn_mod_p` (사용자 의도 + 시스템 상태 이중 체계) + `ai_drvn_mod_h` (전환 이력) + `pump_predc_h` (예측 결과 시계열) + `pump_ctrl_h` (제어 로그) + `pump_cmbn_m`·`pump_cmbn_d` (펌프 조합 정규화) + `pump_interlock_p` (인터록 명세)
- **상태**: ANALYZE1 작성 완료. **본 작업이 위 도메인 구조를 그대로 사용 또는 확장**

### 1.4 사용자 사전 확인 사항 ⚠️
1. **마스터도메인설계 PLAN approved 여부** — 미진행이면 본 작업 PLAN 에서 함께 처리 (스코프 확장)
2. **EPANET 의 시스템 위치** — 외부 별도 마이크로서비스인가? Python AI 서버 내부 모듈인가? 또는 백엔드 내부 라이브러리?
3. **명세서 v0.2 공식 승인 여부** — 명세서 자체가 v1.0 라고 되어 있으나 파일명은 v0.2. 본 작업 진행 중 명세 변경 가능성?

---

## 2. SSOT 자동 정렬 (안건 외 — 회의 시작 시 자동 적용)

명세서 표기는 swtp 표준 용어와 다른 기성품 컨벤션이다. 다음 9 항목은 **회의 안건이 아니라 자동 정렬** 처리한다 (각 행은 폐기·표준화 결정이 이미 존재).

| 명세서 표기 | 표준 용어 | 정렬 근거 |
|---------|--------|---------|
| `REG_ID` / `REG_DT` | `rgstr_id` / `rgstr_dtm` | `reg`·`dt` 폐기 (pumpcontrol ANALYZE1 안건 1·2) |
| `PMP_OPRTNG_CNTOM` | `pump_oprtng_cnt` | `pmp`·`cntom` 폐기 (pumpcontrol ANALYZE1 안건 1·2) |
| `PMP_ID` | `instrument_id` | 마스터도메인설계 R2 — pump_m 폐기 예정 |
| `FACIL_ID` | `facility_id` | 마스터도메인설계 R2 — facility_m UUID PK |
| `BASE_DT` (TIMESTAMP) | `base_dtm` | TIMESTAMP 는 `_dtm` (pumpcontrol ANALYZE1 안건 1) |
| `PMP_CMBN` | `pump_cmbn_cd` | 코드값은 `_cd` 접미사 |
| `AI_DRVN_MOD` | `ai_drvn_mod` | 표준 용어 등재 그대로 |
| `PRFWT_TNKF` 약어 | facility 자식 PWTF (`facility_type_cd = 'PWTF'`) | 마스터도메인설계 R2 |
| `DSTRWT_TNK` 약어 | facility 자식 DWT (`facility_type_cd = 'DWT'`) | 마스터도메인설계 R2 |
| `TAG_NM` 컬럼 사용 | `tag_srl_no` 자연키 PK + `tag_nm` 식별명 컬럼 | 마스터도메인설계 R3 |

> **명세서 작성자에게 회신 권고**: 위 정렬 결과를 명세서 v0.3 으로 반영하면 향후 SSOT 충돌이 줄어듭니다.

---

## 3. 5인 회의 안건 목록

> 표기 규칙: **호출 에이전트** = Round 1 1차 호출. **결정 의무** = 본 안건이 PLAN 진입 전 반드시 결정되어야 하는 사항. **재평가 대상** = 기존 결정도 재검토 가능한 항목 (단순 수용 금지).

### 안건 A1. 6 엔티티 정합성 — 중복 통합 검토 ⭐ 핵심

#### 배경
명세서가 6개 신규 엔티티를 제시하나 swtp 의 이미 도입된 (또는 도입 예정) 엔티티와 의미 중복 우려가 있다. 본 안건이 **본 작업 스코프를 결정하는 가장 큰 의제**.

#### 핵심 질문 별 옵션

##### Q1.1 `DRVN_STTS_INQ` (운전현황 분석 조회) — 영속화 정당성?

명세서 정의: 사용자가 조회한 파라미터(조회범위·차트단위·기준일시·AI운전모드 등) + 조회 사용자/일시 기록.

| 옵션 | 내용 | 장단 |
|------|------|------|
| **A** | 매 조회마다 INSERT (감사·재현 목적) | 단점: 조회 빈도가 높으면 폭증. 다운로드 시점만 의미 있을 가능성 |
| **B** | INSERT 하지 않음 — `DRVN_ANLS_DWLD_HSTRY` 의 컬럼으로 흡수 (다운로드 시점만 영속화) | 장점: 단순화. 단점: 일반 조회 이력 없음 (단, 명세서 7장 7.1 흐름에서 영속화 요구 명시 없음) |
| **C** | 사용자 세션 컨텍스트로만 보존 (Spring HttpSession), 영속화 없음 | 장점: 가장 단순. 단점: 다운로드 시 INQ_ID 추적 불가 |

**재평가 대상**: 명세서 5.1·5.2·5.3 모두 `INQ_ID` 를 FK 로 가지나, **별도 테이블이 정말 필요한지** 는 swtp 가 결정한다.

##### Q1.2 `PREDC_ANLS_CMP_DATA` vs `DRVN_ANLS_RSLT` — 의미 중복?

| 컬럼 | PREDC_ANLS_CMP_DATA | DRVN_ANLS_RSLT |
|------|--------------------|----------------|
| 자료 구분 | DATA_DIV (예측조회/분석이력) | — (운전 분석결과 단일) |
| 실측값 | ACTL_VAL | ACTL_FLWRT_VAL·ACTL_PPLN_PRSR_VAL·ACTL_ELPWR_UNIT |
| 예측값 | PREDC_VAL | PREDC_FLWRT_VAL·PREDC_PPLN_PRSR_VAL·PREDC_ELPWR_UNIT |
| EPANET 분석값 | EPANET_VAL | — |
| 차이값/오차율 | DIFF_VAL·ERR_RT | ERR_RT |

→ **PREDC_ANLS_CMP_DATA** 는 항목별 long format (ITEM_CD = 유량/관압/...), **DRVN_ANLS_RSLT** 는 wide format (컬럼별).

| 옵션 | 내용 | 장단 |
|------|------|------|
| **A** | 양자 모두 보유 (명세서 그대로) | 단점: 데이터 중복. EPANET 결과는 DRVN_ANLS_RSLT 에 컬럼 추가하면 충분 |
| **B** | `PREDC_ANLS_CMP_DATA` 로 통합 (long format) — DRVN_ANLS_RSLT 폐기. 분기별 데이터(BRANCH_MEAS_PREDC_DATA) 도 통합 | 장점: 통합 비교 테이블. 단점: 차트 표시 시 pivot 필요 |
| **C** | `DRVN_ANLS_RSLT` 로 통합 (wide format) — `epanet_*` 컬럼 추가, PREDC_ANLS_CMP_DATA 폐기 | 장점: 차트 매핑 단순. 단점: 항목 추가 시 컬럼 추가 부담 |
| **D** | 양자 보유하되 **저장 안 함** (매 조회마다 산출) — 캐시만 사용 | 장점: 시계열 폭증 방지. 단점: 동일 조회 반복 시 재계산 비용 |

##### Q1.3 `PMP_OPRTNG_HSTRY` — `rawdata_1m_h` + `pump_ctrl_h` 와 중복?

명세서 컬럼: PMP_ID·TAG_NM·ACTL_OPRT_YN·PREDC_OPRT_YN·FLWRT_VAL·PPLN_PRSR_VAL.

- **`rawdata_1m_h`** (마스터도메인설계 R3): tag_srl_no·acq_dtm·raw_val·corr_val·quality_cd → 펌프 가동 여부는 펌프별 RPM 또는 ON/OFF 태그 raw_val 로 추출 가능
- **`pump_ctrl_h`** (pumpcontrol ANALYZE1): pump_id(→instrument_id)·ctrl_dtm·ctrl_div·ctrl_rslt → 제어 명령 이력 (ON/OFF 명령 발행)
- **`pump_predc_h`** (pumpcontrol ANALYZE1): predc_base_dtm·predc_dtm·pump_cmbn_cd·predc_elpwr_amt·predc_flwrt·predc_prsr → 예측값

| 옵션 | 내용 | 장단 |
|------|------|------|
| **A** | `PMP_OPRTNG_HSTRY` 신규 도입 (명세서 그대로) | 단점: rawdata_1m_h + pump_predc_h 로 충분히 표현 가능 — 중복 |
| **B** | 폐기 — 화면 조회 시 rawdata_1m_h(실제) + pump_predc_h(예측) JOIN 으로 표현 | 장점: SSOT 유지. 단점: JOIN 복잡도 증가 |
| **C** | `pump_oprtng_v` view 도입 (PostgreSQL VIEW) — rawdata_1m_h + pump_predc_h 를 시간대별 결합 | 장점: 화면 조회 단순화 + 영속화 없음. 단점: view 성능 |

##### Q1.4 `BRANCH_MEAS_PREDC_DATA` — `POINT_ID` 신규 마스터?

명세서 정의: 주요 분기 지점별 (관로 분기점·계측 위치) 유량/관압/수위 실측·예측값.

| 옵션 | 내용 | 장단 |
|------|------|------|
| **A** | `point_m` 신규 마스터 도입 — POINT_ID·POINT_NM | 단점: facility_m 자식 또는 tag_m 으로 표현 가능 — 마스터 추가 부담 |
| **B** | `facility_m` 자식 종류 추가 — `facility_type_cd = 'POINT'` (관로 계측 분기점) | 장점: 마스터도메인설계 R2 패턴 일관 (자식 종류 확장) |
| **C** | `tag_m` 으로 표현 — 분기 지점은 SCADA 계측 태그 단위 | 장점: 추가 마스터 0건. 단점: tag_m 은 단일 측정값 단위, 분기 지점은 다중 측정값 (유량+관압+수위) |
| **D** | `facility_m` 신규 자식 (B) + `BRANCH_MEAS_PREDC_DATA` 시계열 보유 | 장점: 마스터 + 시계열 분리 명확 |

##### Q1.5 6 엔티티 → 최종 N 엔티티 결정 (Q1.1~Q1.4 종합)

회의 결과 후 본 작업의 **최종 신규 엔티티 목록** 확정. 예상 시나리오:

| 시나리오 | 신규 엔티티 수 | 구성 |
|---------|---------------|------|
| 명세서 그대로 | 6 | 6 entities |
| 미니멀 | 1~2 | DRVN_ANLS_DWLD_HSTRY (다운로드 감사) + 분석결과 view |
| 권장 (예상) | 3 | DRVN_ANLS_DWLD_HSTRY + DRVN_ANLS_RSLT (or PREDC_ANLS_CMP_DATA 단일) + facility 자식 'POINT' |

#### 호출 에이전트
`wtp-glossary-manager` (의미 중복 분류) · `wtp-domain-expert` (도메인 정합성) · `wtp-dba-reviewer` (시계열·집계 부담)

#### 결정 의무
✅ 본 안건 결과가 **PLAN 의 DB 설계 변경 섹션의 50% 이상을 결정**

---

### 안건 A2. 컬럼명 SSOT 정렬 — 자동 정렬 결과 검토

#### 배경
§2 (자동 정렬) 결과를 5인 회의에서 한 번 더 점검. 자동 정렬 미해결 항목 + 일부 신규 표기는 별도 결정 필요.

#### 핵심 질문

##### Q2.1 자동 정렬 적용 범위
§2 의 9 항목을 자동 정렬한 뒤 **명세서의 모든 컬럼 표기**를 표준 용어 사전에 맞춰 재기록할지?

| 옵션 | 내용 |
|------|------|
| **A** | 자동 정렬만 — 새 컬럼은 안건 A3·A4 에서 별도 결정 |
| **B** | 명세서 전체 재기록 (새 컬럼 포함) — A3·A4 결과를 본 안건에서 통합 처리 |

##### Q2.2 잔여 표기 충돌 (자동 정렬 외)
| 명세서 | 후보 표기 | 비고 |
|--------|----------|------|
| `EPANET_VAL` | `epanet_val` (소문자) 또는 `anls_rslt_val` | "EPANET" 자체가 비즈니스 도메인 약어로 등록 가능한지 |
| `INQ_HR` | `inq_hr` 또는 `inq_hour_cnt` | 시간 단위 (1·2·3...) — INTEGER 인지 명확화 |
| `INQ_MIN` | `inq_min_cnt` | `min` 표준 단어 (최소) 와 의미 충돌 우려 |
| `CHART_UNIT` | `chart_unit_cd` | 코드값 (DAY/WEEK/MONTH) → `_cd` 접미사 |
| `DATA_DIV` | `data_div_cd` | 코드값 (PRDC/ANLS) → `_cd` 접미사 |
| `ITEM_CD` | `item_cd` 또는 `meas_item_cd` | "항목" 의 의미 모호 — 측정 항목 한정 |

#### 호출 에이전트
`wtp-glossary-manager`

#### 결정 의무
✅ 본 안건 결과가 PLAN 의 엔티티 컬럼 SSOT 정합성 보장

---

### 안건 A3. 신규 표준 단어 등록 분류

#### 배경
명세서에 등장하는 신규 어휘 ~20개 후보. 각 후보의 신규/기존 재사용/유사 충돌/거부 분류.

#### 후보 표

| 영문 약어 | 한글 논리명 | 분류 후보 | 검토 포인트 |
|----------|-----------|---------|---------|
| `stts` | 상태 | 신규? | 기존 `oprtng`(운전중)·`drvn`(운전됨) 와 의미 경계 |
| `inq` | 조회 | 신규 | `serch`(검색) 등 충돌 가능 |
| `chart` | 차트 | 신규 | UI 어휘 → DB 컬럼 적합성? |
| `branch` | 분기 | 신규 | "분기" 의 의미 (관로 분기 vs Git branch 등 다의어) |
| `meas` | 계측 | 신규 | 기존 `qty`(측정값) 와 의미 분리 가능 |
| `point` | 지점 | 신규 | facility 자식과 결합 사용 (Q1.4) |
| `prfm` | 성능 | 신규 | "성능곡선" 의 prfm |
| `rsst` | 저항 | 신규 | "저항점" 의 rsst |
| `rgrs` | 회귀 | 신규 | "회귀식" 의 rgrs |
| `formula` | 공식 | 신규 | 합성어 |
| `err` | 오차 | 신규 | "오차율" 의 err |
| `rt` | 율 | 신규 | "오차율" 의 rt — `cnt`/`qty` 와 의미 분리 |
| `cmp` | 비교 | 신규 | 합성어 |
| `data` | 자료 | 신규? | 너무 일반적 — `_data` suffix 사용 부담 |
| `dwld` | 다운로드 | 신규 | UI 어휘 → DB 컬럼 적합성? |
| `oprt` | 운전 (단축형) | 신규? | 기존 `oprtng`(상태값)·`drvn`(동사) 와 의미 경계 — **중복 우려** |
| `actl` | 실제 | 신규 | 기존 `predc`(예측) 와 짝 |
| `hr` | 시간 (단축형) | 신규? | 기존 `dtm`(일시)·`dt`(일자) 와 의미 경계. 명세서는 단순 정수 (1~24) |
| `format` | 형식 | 신규 | 합성어 |
| `epanet` | EPANET | 거부? | 외부 도구명 — `scada` 거부 선례 (2026-04-25) |

#### 핵심 질문
##### Q3.1 `oprt` vs 기존 `oprtng`·`drvn` 정합성
명세서 `ACTL_OPRT_YN`·`PREDC_OPRT_YN` (실제/예측 가동 여부) — 새 단어 `oprt` (운전 단축형) 도입 vs 기존 `oprtng`(운전중) 재사용?

| 옵션 | 결과 |
|------|------|
| **A** | `oprt` 신규 등록 — `actl_oprt_yn`·`predc_oprt_yn` |
| **B** | `oprtng` 재사용 — `actl_oprtng_yn`·`predc_oprtng_yn` (`oprtng` 단어 본래 의미 = 상태값. yn 결합 가능) |

##### Q3.2 `epanet` 등록 거부 처리
- `swtp/.claude/rules/dict/domain-abbreviations.md` 등록 거부 이력 — `scada` (2026-04-25) 선례
- EPANET 은 외부 분석 도구. swtp 비즈니스 도메인 아님
- 컬럼명 사용 시 `epanet_*` 표기는 외부 어댑터 패키지 전용 (예: `com.mo.swtp.scada.outbound` 와 동일 위상의 `com.mo.swtp.epanet.client`)
- **DB 컬럼명에는 `anls_*` 사용 권장** (분석 결과 일반 표현)

##### Q3.3 `hr` 단어 vs `cnt` 재사용
명세서 `INQ_HR` (조회 시간 — 1·2·3 등 정수) — 새 단어 `hr` 도입 vs 기존 `cnt` (개수) 재사용?

| 옵션 | 결과 |
|------|------|
| **A** | `hr` 신규 등록 — `inq_hr`·`inq_min` |
| **B** | `cnt` 재사용 — `inq_hr_cnt`·`inq_min_cnt` (시간/분의 개수) |

#### 호출 에이전트
`wtp-glossary-manager`

#### 결정 의무
✅ ANALYZE 의 §룰 갱신 지시서 = 본 안건 결과 (체크박스로 ROOT 어휘 사전 갱신)

---

### 안건 A4. 신규 표준 데이터 도메인 등록 (DBA 2차 승인 필수)

#### 배경
명세서가 도입하는 새 값 형식 (SQL 타입·길이·Java 타입). 기존 `DOM_*` 으로 충분히 표현되는지 점검.

#### 후보 표

| 도메인 코드 후보 | 용도 | SQL 타입 | Java 타입 | NULL | 검토 |
|----------------|------|---------|---------|------|-----|
| `DOM_ELPWR_UNIT_QTY` | 전력원단위 (kWh/m³) | `NUMERIC(15,4)` | `BigDecimal` | NULL 허용 | 기존 `DOM_QTY_15_4` 와 동일 — **중복 우려, 거부 가능** |
| `DOM_ERR_RT` | 오차율 (%) | `NUMERIC(5,2)` | `BigDecimal` | NULL 허용 | 0~100 범위 백분율 — 별도 도메인 |
| `DOM_FILE_NM_200` | 다운로드 파일명 | `VARCHAR(200)` | `String` | NOT NULL | 기존 `DOM_NAME_100` 길이 부족 가능 |
| `DOM_FILE_FORMAT_10` | 파일 형식 (CSV/XLSX) | `VARCHAR(10)` | `FileFormatType` enum | NOT NULL | 기존 `DOM_CODE_20` 길이 과대 — 별도? |
| `DOM_INQ_HR` | 조회 시간 단위 정수 | `SMALLINT` | `Short` | NOT NULL | 1~24 범위 — 기존 `INTEGER` 재사용? |
| `DOM_INQ_MIN` | 조회 분 단위 정수 | `SMALLINT` | `Short` | NOT NULL | 0~59 범위 — 동일 |
| `DOM_CHART_UNIT_CD` | 차트 단위 (DAY/WEEK/MONTH) | `VARCHAR(20)` | `ChartUnitType` enum | NOT NULL | 기존 `DOM_CODE_20` 재사용 가능 |
| `DOM_DATA_DIV_CD` | 자료 구분 (예측조회/분석이력) | `VARCHAR(20)` | `DataDivType` enum | NOT NULL | 동일 — 기존 재사용 |

#### 핵심 질문
##### Q4.1 `DOM_ELPWR_UNIT_QTY` 거부 가능성
- 기존 `DOM_QTY_15_4` 와 SQL/Java 동일 — 유사 충돌 (R/d README §유사 충돌 판정 기준)
- **거부 권장**: `predc_elpwr_unit_qty` 컬럼은 `DOM_QTY_15_4` 사용

##### Q4.2 `DOM_FILE_FORMAT_10` vs `DOM_CODE_20` 재사용
- 권한메뉴 ANALYZE1 의 `DOM_URL_200` 거부 선례 (2026-05-06) — DBA 2차 승인 거부
- 본 안건도 동일 패턴 — `DOM_CODE_20` 재사용 권장

##### Q4.3 `DOM_INQ_HR`·`DOM_INQ_MIN` 별도 도메인 필요성
- SMALLINT 사용 — 기존 표준 데이터 도메인에 SMALLINT 등재 없음
- 별도 도메인 vs 컬럼별 SMALLINT 직접 선언

#### 호출 에이전트
`wtp-glossary-manager` 1차 분류 → `wtp-dba-reviewer` 2차 승인

#### 결정 의무
✅ DBA 승인 거부 시 컬럼명 변경 또는 기존 도메인 재사용 — PLAN 직전 확정

---

### 안건 A5. 마스터도메인설계 결정 적용 시점 (의존성 결정) ⭐ 핵심

#### 배경
운전현황 분석은 facility (정수지/배수지) + instrument (펌프) + tag 마스터를 모두 참조. 마스터도메인설계 ANALYZE1 의 결정이 PLAN approved 되었는지에 따라 본 작업의 스코프가 결정.

#### 핵심 질문

##### Q5.1 마스터도메인설계 PLAN 진행 상태
- **사용자 확인 필요** — `docs/plan/{20260502 또는 후속}/마스터도메인설계/PLAN1.md` 또는 후속 cycle 의 status?

##### Q5.2 본 작업과의 관계 — 옵션

| 옵션 | 내용 | 장단 |
|------|------|------|
| **A** | 마스터도메인설계 PLAN approved 상태 — 본 작업은 facility/instrument/tag 신 구조를 **사용**만 | 장점: 본 작업 스코프 명확. 단점: 의존성 위반 시 컴파일 실패 |
| **B** | 마스터도메인설계 미진행 — 본 작업이 facility/instrument/tag 마스터까지 함께 도입 | 단점: 본 작업 스코프 폭증 (Large → XL). 6 엔티티 + 마스터 4 엔티티 + 마이그레이션 |
| **C** | 마스터도메인설계 미진행 — 본 작업은 기존 `pwtf_id`·`pump_id`·`tag_nm` 표기로 작성하고, 마스터도메인설계 PLAN 후 마이그레이션 | 단점: 두 번 작업 + 마이그레이션 복잡도 |
| **D** | 마스터도메인설계 PLAN 을 본 작업 직전 선행 처리 (별도 사이클) | 권장 — 의존성 깨끗 |

##### Q5.3 instrument 자식 종류별 도메인 룰 (`equip_type_cd` 필터 강제)
- 본 작업의 모든 펌프 조회는 `equip_type_cd = 'PUMP'` 필터 강제 (entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴 §도메인 룰)
- 부모 다형성 전체 조회 금지 — REVIEW 시 `wtp-domain-expert` 점검

##### Q5.4 facility 자식 종류별 (`facility_type_cd` 필터)
- PWTF·DWT·POINT(Q1.4 도입 시) 자식 종류별 시나리오 분리
- 운전현황 분석 화면이 **단일 facility 단위 조회**인가, **복수 facility 통합 조회**인가? 명세서 7장 7.1 흐름은 단일 facility 가정 (단, 명시 부재)

#### 호출 에이전트
`wtp-domain-expert` · `wtp-backend-engineer`

#### 결정 의무
✅ 본 안건 결과가 본 작업의 **PLAN 진행 자체를 결정** (스코프·일정·의존)

---

### 안건 A6. 시계열 파티션·집계 정책

#### 배경
6 엔티티 중 시계열 후보 (`PMP_OPRTNG_HSTRY`·`PREDC_ANLS_CMP_DATA`·`DRVN_ANLS_RSLT`·`BRANCH_MEAS_PREDC_DATA`) 가 다수. swtp 의 시계열 파티션 정책 (월 RANGE) + 보존 기간 + 매 조회 산출 vs 사전 집계 결정 필요.

#### 핵심 질문

##### Q6.1 시계열 vs 마스터 분류

| 엔티티 | 분류 후보 | 보존 기간 후보 |
|------|---------|--------------|
| DRVN_STTS_INQ | 마스터 (`_m`) 또는 폐기 (Q1.1) | — |
| PREDC_ANLS_CMP_DATA | 시계열 (`_h`) 또는 캐시 (Q1.2) | 3년 (예측 결과) |
| DRVN_ANLS_RSLT | 시계열 (`_h`) 또는 캐시 (Q1.2) | 3년 |
| DRVN_ANLS_DWLD_HSTRY | 시계열 (`_h`) — 다운로드 감사 | 5년 (감사 로그) |
| PMP_OPRTNG_HSTRY | 시계열 (`_h`) 또는 폐기 (Q1.3) | rawdata_1m_h 와 동일 13개월 |
| BRANCH_MEAS_PREDC_DATA | 시계열 (`_h`) | 3년 (예측+실측) |

##### Q6.2 시계열 파티션 키
- 모두 `acq_dtm` 또는 `base_dtm` (월 RANGE)
- `db/partitioning-and-retention.md §1` 정책 적용
- 시계열 → 마스터 FK 금지 정책 (1분 INSERT 잠금) 적용 — 본 작업의 시계열 행은 `tag_srl_no`·`facility_id`·`instrument_id` 논리 참조 (FK 미생성)

##### Q6.3 매 조회 산출 vs 사전 집계
- 운전현황 분석은 **사용자 트리거 조회** (조회범위 버튼 클릭)
- 1분 raw → 1시간 집계 → 화면 표시
- 옵션:

| 옵션 | 내용 | 장단 |
|------|------|------|
| **A** | 매 조회마다 rawdata_1m_h 집계 (GROUP BY 1시간 윈도우) | 단점: 13개월 raw 1분 데이터 약 6.7억 행 — 페이지네이션 필수 |
| **B** | 1시간 집계 사전 저장 (스케줄러 잡, `rawdata_1h_h` 신설) | 장점: 조회 성능. 단점: 신규 테이블 + 스케줄러 잡 |
| **C** | A + 메모리 캐시 (Caffeine) — 같은 조회범위 반복 시 캐시 히트 | 장점: 인프라 없이 성능. 단점: 캐시 키 설계 |

##### Q6.4 다운로드 시 대용량 처리
- CSV/XLSX 다운로드 — 13개월 1분 데이터 = 약 56만 행 (단일 시설 × 단일 펌프 기준)
- streaming 처리 + apache poi SXSSFWorkbook (XLSX) 또는 OpenCSV
- 메모리 안정성 — `query-tuning.md §1` 페이지네이션 정책 적용

##### Q6.5 펌프조합 선택점 자동 갱신 (명세서 4.3·6.6)
- 성능점·저항점 기준 자동 갱신 — 백엔드 스케줄링? 또는 매 조회마다 산출?
- 산출 결과 영속화 — `pump_cmbn_sel_pt_h` 신설? 또는 `pump_predc_h` 컬럼 추가?

#### 호출 에이전트
`wtp-dba-reviewer` · `wtp-backend-engineer`

#### 결정 의무
✅ 본 안건이 운영 성능 기준 + 인프라 부담 결정

---

### 안건 A7. 도메인 4영역 점검 (알람·인터록·운전모드·이력)

#### 배경
ANALYZE 템플릿 §도메인 룰 4영역 점검 의무 (form 효력 강화 — Cycle 1, 2026-05-06).

#### 4영역 표 (예상 결과)

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 운전현황 분석은 조회 화면. 알람 임계값 변경 없음 (단, 화면에 알람 상태 표시는 가능) |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | AI 운전모드 변경은 인터록 비대상 (제어 명령 발행 아님) |
| AI 운전모드 (`ot-integration.md §5`) | **해당** ⚠️ | "시스템 관리자만 AI 운전모드 변경" — `ai_drvn_mod_p` UPDATE 권한 분리. 사용자 의도 변경 시 `ai_drvn_mod_h` 자동 INSERT 의무 (이미 도입) |
| 이력 기록 의무 (`ot-integration.md §5`) | **해당** ⚠️ | AI 운전모드 변경 → `ai_drvn_mod_h` `transition_reason = 'USER_SELECT'` 행 INSERT 의무. 본 작업이 새 transition_reason 추가하지 않음 (기존 5종 재사용) |

#### 핵심 질문

##### Q7.1 AI 운전모드 변경 권한 강제
- ADMIN 만 변경 — Spring Security `@PreAuthorize("hasRole('ADMIN')")` 적용
- 사용자 정보는 `BaseEntity` `rgstr_id`·`updt_id` 자동 주입 — `AuditingEntityListener`
- `ai_drvn_mod_h` 의 `rgstr_id` 가 변경 사용자 ID 로 기록됨 (기존 패턴)

##### Q7.2 AI 운전모드 표시 vs 변경 분리
- 모든 사용자: 현재 운전모드 조회 가능 (명세서 6.2)
- ADMIN: 변경 가능 (명세서 2.2)
- API 분리: `GET /api/pump/drvn-status/ai-mode` (모두 허용) + `PUT /api/pump/drvn-status/ai-mode` (ADMIN 만)

##### Q7.3 SCADA 5분 초과 강제 전환 표시 의무
- `ai_mode_cd` (시스템 상태) 가 강제 전환된 경우 화면에 표시 의무 (운영자 인지)
- 명세서에 명시 부재 — 추가 요구사항?

#### 호출 에이전트
`wtp-domain-expert`

#### 결정 의무
✅ ANALYZE §도메인 룰 4영역 점검 표 작성 (REVIEW 자동 점검 대상)

---

### 안건 A8. AI 추론 서버 호출 정합성 (`ot-integration.md §6`)

#### 배경
명세서가 두 외부 추론 결과를 활용:
1. **Python 기반 예측결과** (`pump_predc_h` 등) — 이미 도입 (pumpcontrol ANALYZE1)
2. **EPANET 기반 분석 이력** — 신규

#### 핵심 질문

##### Q8.1 EPANET 시스템 위치
- 사용자 확인 필요 (§1.4 항목 2) — 외부 별도 마이크로서비스? Python AI 서버 내부 모듈? 백엔드 내부 라이브러리?

| 옵션 | 내용 | 영향 |
|------|------|------|
| **A** | 외부 별도 마이크로서비스 (`epanet-server:8001`) | `ot-integration.md §6` 패턴 동일. `EpanetClient` 신규 |
| **B** | Python AI 서버 내부 모듈 (`/predict/epanet/analyze` 라우터) | `AiServerClient` 의 새 메서드 |
| **C** | 백엔드 내부 라이브러리 (Java EPANET 바인딩) | 외부 호출 없음 — 회복성 패턴 미적용 |
| **D** | EPANET 분석은 백엔드 직접 실행 안 함 — 외부 시스템 산출 결과 파일을 swtp 가 import | 가장 단순 — 정기 import 잡 |

##### Q8.2 예측 조회 vs 분석 이력 라이프사이클
- 예측 조회: 사용자 트리거 → `pump_predc_h` 조회 또는 ai-server 동기 호출
- 분석 이력: EPANET 산출 결과 사전 저장 → 다운로드 시 조회만

##### Q8.3 `ai_server_client` 회복성 패턴 적용
- 본 작업이 ai-server 호출 추가 시 `aiPrediction` 인스턴스 재사용 vs 새 회복성 인스턴스
- EPANET 옵션 A 시 `epanetAnalysis` 회복성 인스턴스 신설

#### 호출 에이전트
`wtp-backend-engineer` · `wtp-domain-expert`

#### 결정 의무
✅ 본 안건이 외부 시스템 호출 패키지 구조 결정

---

### 안건 A9. 권한·메뉴 정합성 (권한메뉴 ANALYZE1 연계)

#### 배경
- 권한메뉴 ANALYZE1 (2026-05-06) 의 `menu_m` + `menu_role_r` + `UserRole` enum (ADMIN/USER) 정합
- 명세서 메뉴 ID `FR-PMP-002` (에너지 관리 > 송수펌프 제어 > 운전현황 분석) — `menu_m` 등록 필요

#### 핵심 질문

##### Q9.1 메뉴 등록 시점
- 명세서 메뉴 → `menu_m` INSERT — 본 작업에서?
- 권한메뉴 PLAN 진행 상태 의존 (사용자 확인 필요 — §1.4 항목 2 와 동일 의존)

| 옵션 | 내용 |
|------|------|
| **A** | 권한메뉴 PLAN approved 상태 — 본 작업이 menu_m 직접 INSERT (운영 데이터) |
| **B** | 권한메뉴 PLAN 미진행 — 본 작업이 menu_m·menu_role_r 까지 도입 |
| **C** | 권한메뉴 PLAN 본 작업 직전 선행 (별도 사이클) — 의존성 깨끗 (권장) |

##### Q9.2 메뉴 트리 부모 ID
- 명세서: "에너지 관리 > 송수펌프 제어 > 운전현황 분석" — 깊이 3
- `menu_m.parent_menu_id` self-FK 깊이 N=3 (권한메뉴 ANALYZE1 결정)
- 부모 메뉴 (`에너지 관리`·`송수펌프 제어`) 도 본 작업이 INSERT? 또는 권한메뉴 작업이 처리?

##### Q9.3 menu_role_r 매핑
- ADMIN: `에너지 관리 > 송수펌프 제어 > 운전현황 분석` 메뉴 접근 + AI 운전모드 변경 버튼 활성
- USER: 동일 메뉴 접근 + AI 운전모드 변경 버튼 비활성 (조회만)
- → `menu_role_r` 에 (menu_id, ADMIN) + (menu_id, USER) 2 행 INSERT
- 버튼 단위 권한은 `menu_m` 의 자식 메뉴? 또는 프론트엔드 분기?

##### Q9.4 ADMIN/USER 외 역할 추가 필요?
- 명세서 2.1 "시스템 관리자" / "시스템 사용자" — 2 역할
- 권한메뉴 ANALYZE1 의 ADMIN/USER 와 1:1 대응

#### 호출 에이전트
`wtp-backend-engineer` · `wtp-glossary-manager`

#### 결정 의무
✅ 메뉴 + 권한 매핑 결과 PLAN 의 운영 데이터 INSERT 스크립트에 반영

---

## 4. 사용자 결정 필요 사항 (회의 시작 전)

회의 시작 전 사용자(인간 팀원) 가 다음을 결정해야 회의가 효율적으로 진행됩니다.

| # | 결정 사항 | 영향 |
|---|---------|------|
| **D1** | 마스터도메인설계 PLAN approved 여부 (안건 A5) | 본 작업 스코프 결정 |
| **D2** | 권한메뉴 PLAN approved 여부 (안건 A9) | 본 작업 스코프 결정 |
| **D3** | EPANET 시스템 위치 (외부 마이크로서비스 / AI 서버 내부 / 백엔드 내부 / 결과 import 만) (안건 A8) | 외부 호출 패키지 구조 결정 |
| **D4** | 운전현황 분석 화면이 단일 facility 조회 vs 복수 facility 통합 (안건 A1·A5) | 쿼리·인덱스 설계 |
| **D5** | 6 엔티티 통합 방향 — 명세서 그대로 / 미니멀 / 권장 (안건 A1) | 본 작업 스코프 결정 |
| **D6** | 시계열 집계 정책 — 매 조회 산출 / 사전 집계 / 캐시 (안건 A6) | 인프라 부담 |
| **D7** | 명세서 v0.2 공식 승인 — 본 작업 진행 중 명세 변경 가능성 | 일정 영향 |

---

## 5. 회의 진행 절차

### Round 1 — 4 에이전트 병렬 호출 (사용자 동의 후 시작)
- **wtp-glossary-manager**: A1·A2·A3 응답
- **wtp-domain-expert**: A1·A5·A7·A8 응답
- **wtp-dba-reviewer**: A1·A4·A6 응답
- **wtp-backend-engineer**: A5·A6·A8·A9 응답

### Round 2 — 이견 시 추가 호출
- 안건 A1 (6 엔티티 통합) 의 wtp-glossary-manager vs wtp-dba-reviewer vs wtp-domain-expert 결론 차이 시 Round 2 진행
- 안건 A4 (DBA 2차 승인) 의 거부 발생 시 Round 2 — 컬럼명 재설계

### 회의록 → ANALYZE1.md 통합
- 9 안건 회의 결과를 `ANALYZE1.md` 의 §회의록 섹션에 통합
- §표준 사전 카탈로그 3표 (단어·데이터 도메인·표준 용어) 작성
- §룰 갱신 지시서 체크박스 작성 (PLAN approved 전 모두 `[x]` 완료 의무)
- §도메인 룰 4영역 점검 표 작성 (안건 A7 결과)
- §성공 기준 후보 작성 (PLAN 단계 변환 대상)
- §가정 및 미해결 질문 작성 (사용자 결정 D1~D7 잔여)

### 사용자 승인 단계
- ANALYZE1 `status: review` → 사용자 검토
- 승인 시 `status: approved` → `/dev:plan` 자동 전이

---

## 6. 회의 시작 전 진행 동의 요청

본 의제 9 안건 + §4 사용자 결정 사항 D1~D7 검토를 인간 팀원과 함께 진행하신 후, 다음과 같이 회신해 주십시오.

```
1. 의제 안건 추가/조정 사항: (없음 / 안건 A_ 추가 / 안건 A_ 조정 / 우선순위 조정)
2. 사용자 결정 D1~D7: (각 항목 답변 또는 "회의에서 결정 위임")
3. 5인 회의 시작 동의: (예 / 아니오 — 사유)
```

회신 후 `/dev:analyze` 5인 회의를 시작하고 `ANALYZE1.md` 산출물을 작성합니다.

---

## 폐기·갱신 이력

| 일자 | 변경 | 배경 |
|------|------|------|
| 2026-05-06 | 신규 작성 | `/dev` 진입 후 ANALYZE 단계 진입 전 의제 사전 검토용. 인간 팀원 검토 후 5인 회의 시작 |
