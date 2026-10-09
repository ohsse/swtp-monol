---
status: approved
created: 2026-05-07
updated: 2026-05-08
---
# 송수펌프제어_운전현황분석 — 도메인 분석 (FR-PMP-002)

## 작업 배경

명세서 `02.운전현황 분석 요구사항 명세서_v0.2.docx` (FR-PMP-002 — 에너지 관리 > 송수펌프 제어 > 운전현황 분석) 도메인 분석. 핵심 기능:

- 운전현황 분석 조회 (시간/분 단위 조회범위 + 차트 단위 일/주/월)
- AI 운전모드 변경 (관리자만) — AI / AI추천(AI_RECOMD) / AI분석(AI_ANLS)
- 운영 현황 — 펌프 가동 조합 + 전력정보 + 전력원단위 + 펌프대수
- 실제·예측 그래프 — 성능점·저항점·회귀식 표시
- 펌프조합 선택점 자동 갱신 (성능점 또는 저항점 기준)
- 예측 조회 자료 다운로드 (Python AI 산출 결과)
- 분석 이력 비교자료 다운로드 (EPANET 산출 결과)
- 주요 분기별 예측/실측 그래프 (수요량·관압 비교, 수위는 실측만)
- 다운로드 형식: CSV / XLSX

### 외부 산출물

- [`02.운전현황 분석 요구사항 명세서_v0.2.docx`](../../20260506/송수펌프제어_운전현황분석/02.운전현황%20분석%20요구사항%20명세서_v0.2.docx) — 명세서 v0.2
- [`AGENDA.md`](../../20260506/송수펌프제어_운전현황분석/AGENDA.md) — 5인 회의 사전 의제 (562줄, 9 안건 A1~A9 + Q1.1~Q9.4 + 사용자 결정 D1~D7)

### 의존 작업

| 작업 | 상태 | 본 작업과의 관계 |
|------|-----|------------------|
| [마스터도메인설계 PLAN2](../../../plan/20260506/마스터도메인설계/PLAN2.md) | **approved** ✅ | facility_m·instrument_m·tag_m 신 구조를 본 작업이 사용 (Q5.1·Q5.2 D 답변) |
| [pumpcontrol ANALYZE1](../../20260422/pumpcontrol/ANALYZE1.md) | approved | `ai_drvn_mod_p`·`ai_drvn_mod_h`·`pump_predc_h`·`pump_ctrl_h` 사용 전제 |
| 권한메뉴 PLAN | **미확인** ⚠️ | A9 안건 — 사용자 답변 부재. PLAN 직전 검증 의무 (가정 섹션 처리) |

---

## 회의록 (5인 회의 토픽 주도)

> **진행 방식**: 사용자가 9 안건 (A1~A9) 의 모든 핵심 질문 (Q1.1~Q9.4) 에 사전 답변을 제공한 상태이므로 4 에이전트 병렬 호출은 생략한다. 사용자 답변 → 결론 변환 형태로 회의록을 작성하며, 답변 부재 안건 (A8·A9) 은 본 ANALYZE 범위 외 처리 결론 명기.

### 안건 A1. 6 엔티티 정합성 — 중복 통합 검토 ⭐ 핵심

#### 호출 에이전트
`wtp-glossary-manager` · `wtp-domain-expert` · `wtp-dba-reviewer` (사용자 사전 답변으로 형식적 진행)

#### Round 1 결론

| Q | 사용자 답변 | 회의 결론 |
|---|-----------|---------|
| Q1.1 (DRVN_STTS_INQ 영속화) | 조회 파라미터이므로 엔티티화하지 않음 | **영속화 안 함**. 응답 DTO `DrvnSttsInqDto` 만 운영. Spring HttpSession 의존 없는 stateless API |
| Q1.2 (PREDC_ANLS_CMP_DATA + DRVN_ANLS_RSLT) | 둘 다 뷰 또는 응답 DTO | **영속화 안 함**. 매 조회 시 산출. 뷰 vs 응답 DTO 선택은 PLAN 단계 결정 |
| Q1.3 (PMP_OPRTNG_HSTRY) | 응답 DTO (뷰 vs 조인 추후) | **영속화 안 함**. `rawdata_1m_h` (실제) + `pump_predc_h` (예측) JOIN 또는 PostgreSQL VIEW. 결정 PLAN 단계 |
| Q1.4 (BRANCH_MEAS_PREDC_DATA) | B 옵션 | **facility 자식 'POINT' 추가** — `facility_type_cd = 'POINT'` (관로 계측 분기점). BRANCH_MEAS_PREDC_DATA 시계열은 영속화하지 않고 응답 DTO 처리 (Q6 보류와 정합) |

#### 안건 A1 종합 결론

명세서 6 엔티티 → 본 작업 신규 영속화 엔티티 **2개**:
- `drvn_anls_dwld_h` — 다운로드 감사 시계열 (다운로드 시점만 영속화) ※ 명세서의 DRVN_ANLS_DWLD_HSTRY 항목에 해당. 사용자 Q1.1~Q1.3 답변에서 명시적으로 "영속화 안 함" 결정한 5종 외 잔여 1건이라 본 ANALYZE 가 채택
- facility 자식 'POINT' — `facility_m` 의 새 자식 종류 (마스터도메인설계 JOINED 다형성 패턴 흡수)

나머지 5종 (DRVN_STTS_INQ·PREDC_ANLS_CMP_DATA·DRVN_ANLS_RSLT·PMP_OPRTNG_HSTRY·BRANCH_MEAS_PREDC_DATA) 은 응답 DTO + 뷰 (또는 매 조회 산출) 로 처리.

---

### 안건 A2. 컬럼명 SSOT 정렬 — 자동 정렬 결과 검토

#### 호출 에이전트
`wtp-glossary-manager` (사용자 사전 답변으로 형식적 진행)

#### Round 1 결론

| Q | 사용자 답변 | 회의 결론 |
|---|-----------|---------|
| Q2.1 (자동 정렬 적용 범위) | A 옵션 | **자동 정렬만**. AGENDA §2 의 9 항목 (REG_ID→rgstr_id, PMP_ID→instrument_id, BASE_DT→base_dtm 등) 자동 정렬 적용. 새 컬럼 표기는 안건 A3·A4 에서 별도 결정 |

#### 자동 정렬 결과 요약

명세서 표기 → 표준 용어 정렬 (AGENDA §2 와 동일):

| 명세서 | 표준 용어 |
|-------|---------|
| `REG_ID` / `REG_DT` | `rgstr_id` / `rgstr_dtm` |
| `PMP_OPRTNG_CNTOM` | `pump_oprtng_cnt` |
| `PMP_ID` | `instrument_id` |
| `FACIL_ID` | `facility_id` |
| `BASE_DT` (TIMESTAMP) | `base_dtm` |
| `PMP_CMBN` | `pump_cmbn_cd` |
| `AI_DRVN_MOD` | `ai_drvn_mod` |
| `PRFWT_TNKF` 약어 | facility 자식 PWTF (`facility_type_cd = 'PWTF'`) |
| `DSTRWT_TNK` 약어 | facility 자식 DWT (`facility_type_cd = 'DWT'`) |
| `TAG_NM` 컬럼 사용 | `tag_srl_no` 자연키 PK + `tag_nm` 식별명 컬럼 |

---

### 안건 A3. 신규 표준 단어 등록 분류

#### 호출 에이전트
`wtp-glossary-manager`

#### Round 1 결론

| Q | 사용자 답변 | 회의 결론 |
|---|-----------|---------|
| Q3.1 (oprt vs oprtng) | B 옵션 | **`oprtng` 재사용** — `actl_oprtng_yn`·`predc_oprtng_yn`. `oprt` 신규 등록 거부 |
| Q3.2 (epanet 등록 거부) | "외부 도구이긴 하나 outbound 포함 아님. 발제 추후로 미룸" | **본 ANALYZE 범위 외**. `epanet` 비즈니스 도메인 약어 등록 미진행. 본 ANALYZE 에서는 EPANET 분석 이력 처리 미정의 (A8 안건 보류와 정합) |
| Q3.3 (hr 단어) | A 옵션 | **`hr` 신규 등록** — 시간(단축형). 응답 DTO `inq_hr` 필드 사용 |

#### 안건 A3 종합 결론

본 ANALYZE 가 등록할 신규 표준 단어:

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `hr` | 시간(단축형) | 신규 | Q3.3 A 답변. 명세서 INQ_HR 컬럼 — 정수형 시간 단위. `dtm`(일시)·`dt`(일자) 와 의미 분리 |
| `dwld` | 다운로드 | 신규 | drvn_anls_dwld_h 컬럼 (`dwld_id`·`dwld_file_nm`·`dwld_format_cd`) 사용. UI 어휘이나 DB 컬럼 사용 발생 — 등록 의무 |
| `format` | 형식 | 신규 | `dwld_format_cd` 컬럼 사용. 합성어이나 표준 단어로 등록하면 향후 재사용 가능 |
| `actl` | 실제 | 신규 | 명세서 ACTL_*·PREDC_* 짝 표현. 응답 DTO 변수명 일관성 위해 등록 |
| `inq` | 조회 | 신규 | 응답 DTO `inq_hr`·`inq_min` 변수명 사용. 향후 `inq_*` 컬럼 도입 시 재료 |

본 ANALYZE 가 등록 거부한 후보 단어 (AGENDA A3 후보 표 참고):

| 거부 후보 | 거부 사유 |
|---------|----------|
| `oprt` | `oprtng`(기존, 운전중) 재사용으로 충분 — Q3.1 B 결정 |
| `epanet` | 외부 도구명, swtp 비즈니스 도메인 아님 — Q3.2 발제 추후 |
| `stts` | 본 ANALYZE 의 DRVN_STTS_INQ 영속화 안 함 결정으로 컬럼 사용 발생 0 — 등록 불필요 |
| `chart`·`branch`·`meas`·`point`·`prfm`·`rsst`·`rgrs`·`formula`·`err`·`rt`·`cmp`·`data` | 응답 DTO 변수명에서 사용되나 영속화 컬럼 사용 0 — 단어 등록 불필요. 향후 사용 발생 시 별도 ANALYZE |

---

### 안건 A4. 신규 표준 데이터 도메인 등록 (DBA 2차 승인 필수)

#### 호출 에이전트
`wtp-glossary-manager` 1차 분류 → `wtp-dba-reviewer` 2차 승인 (사용자 사전 답변으로 형식적 진행)

#### Round 1 결론

| Q | 사용자 답변 | 회의 결론 |
|---|-----------|---------|
| Q4.1 (DOM_ELPWR_UNIT_QTY) | 거부 권장 | **거부** — `DOM_QTY_15_4` 재사용. 전력원단위는 일반 정량 측정값과 SQL/Java 동일 (NUMERIC(15,4) + BigDecimal) |
| Q4.2 (DOM_FILE_FORMAT_10) | code 재사용 | **DOM_CODE_20 재사용** — 권한메뉴 ANALYZE1 의 DOM_URL_200 거부 선례 동일 패턴. CSV/XLSX 등 enum 코드값은 DOM_CODE_20 충분 |
| Q4.3 (DOM_INQ_HR·DOM_INQ_MIN) | 등록 필요 없음 | **별도 도메인 미등록** — 컬럼별 `INTEGER` 직접 선언. SMALLINT 별도 도메인 신설 부담 회피 |

#### 안건 A4 종합 결론

본 ANALYZE 가 등록할 신규 표준 데이터 도메인: **없음** (3종 후보 모두 거부 또는 기존 재사용).

DOM_ERR_RT (오차율 백분율) 후보는 AGENDA A4 후보 표에 등재되어 있으나 사용자 답변 부재. 회의 결론으로 **거부 권장** — `DOM_QTY_15_4` 재사용 가능 (백분율 0~100 범위는 애플리케이션 검증). 사용자 확인 가정 섹션 명기.

---

### 안건 A5. 마스터도메인설계 결정 적용 시점 (의존성 결정) ⭐ 핵심

#### 호출 에이전트
`wtp-domain-expert` · `wtp-backend-engineer`

#### Round 1 결론

| Q | 사용자 답변 | 회의 결론 |
|---|-----------|---------|
| Q5.1 (마스터도메인설계 PLAN 진행 상태) | 마스터도메인설계는 완료상태 | [PLAN2](../../../plan/20260506/마스터도메인설계/PLAN2.md) status: approved 검증 완료 ✅. facility_m·instrument_m·tag_m 신 구조 사용 가능 |
| Q5.2 (본 작업과의 관계) | D 옵션 | **마스터도메인설계 별도 사이클로 선행 처리됨**. 본 작업은 신 구조를 사용만 하며 마이그레이션·자식 추가는 본 작업 범위 외 (POINT 자식 추가는 본 작업 PLAN 에서 처리하되 패턴 변경 없음) |
| Q5.3 (instrument 자식 종류 필터) | 필터 강제 | **`equip_type_cd = 'PUMP'` 필터 강제** — 본 작업의 모든 펌프 조회는 instrument_m 부모 다형성 전체 조회 금지. `entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴 §도메인 룰` 정합 |
| Q5.4 (단일 vs 복수 facility 조회) | 보류 | **PLAN 단계 결정 위임**. 명세서 7장 7.1 흐름은 단일 facility 가정이나 명시 부재. 가정 섹션 명기 |

#### 안건 A5 종합 결론

마스터도메인설계 PLAN2 approved 상태이므로 본 작업 스코프는 **신규 facility 자식 'POINT' 추가** 1건 외에는 마스터 변경 없음. 펌프 조회는 `equip_type_cd = 'PUMP'` 필터 의무. facility 단일/복수 조회 정책은 PLAN 단계 결정.

---

### 안건 A6. 시계열 파티션·집계 정책

#### 호출 에이전트
`wtp-dba-reviewer` · `wtp-backend-engineer` (사용자 사전 답변으로 형식적 진행)

#### Round 1 결론

| Q | 사용자 답변 | 회의 결론 |
|---|-----------|---------|
| Q6 (시계열 파티션·집계 5종) | 전부 보류 필요없음 | Q1.1~Q1.3 결정으로 시계열 영속화 5종 모두 미진행 — Q6 안건 자체가 적용 대상 0건. 본 ANALYZE 는 시계열 파티션 정책을 결정하지 않음 |

#### 안건 A6 종합 결론

본 작업의 시계열 영속화 후보는 `drvn_anls_dwld_h` (다운로드 감사) 1건만 잔존. 파티션 키·보존 기간·집계 전략은 PLAN 단계 결정 (가정 섹션 명기). 명세서 6장 다운로드 빈도 (1일 N회 ~ 사용자 트리거) 가 1분 raw 시계열과 빈도 차이 크므로, 월 RANGE 파티션 적용 의무는 PLAN 단계에서 재검토.

---

### 안건 A7. 도메인 4영역 점검 (알람·인터록·운전모드·이력)

#### 호출 에이전트
`wtp-domain-expert`

#### Round 1 결론

| Q | 사용자 답변 | 회의 결론 |
|---|-----------|---------|
| Q7 (도메인 4영역 점검) | 차후 태스크로 분리 | **AI 운전모드 변경 기능 (명세서 6.2) 은 별도 사이클 분리**. 본 ANALYZE 의 4영역 표는 작성 의무 (form 효력 강화) — "비해당" + 사유 명기 또는 "별도 사이클 분리" 명기 |

#### 안건 A7 종합 결론

명세서 6.2 (AI 운전모드 변경 — ADMIN 전용) 와 그에 따른 `ai_drvn_mod_p` UPDATE + `ai_drvn_mod_h` INSERT 는 본 사이클 범위 외. 운전현황 분석 화면의 AI 운전모드 표시 (read-only) 만 본 작업 범위에 포함. 4영역 표는 §도메인 룰 4영역 점검 섹션에 작성.

---

### 안건 A8. AI 추론 서버 호출 정합성 (`ot-integration.md §6`)

#### 호출 에이전트
`wtp-backend-engineer` · `wtp-domain-expert`

#### Round 1 결론

| Q | 사용자 답변 | 회의 결론 |
|---|-----------|---------|
| Q3.2 (epanet 약어 등록 — A8 의존) | 발제 추후로 미룸 | **본 ANALYZE 범위 외**. EPANET 시스템 위치 (외부 마이크로서비스/AI 서버 내부/백엔드 라이브러리/결과 import) 결정 별도 사이클 |
| Q8.1·Q8.2·Q8.3 | 사용자 명시 답변 부재 (Q3.2 의존 — 발제 추후) | EPANET 관련 결정 모두 별도 사이클로 분리 |

#### 안건 A8 종합 결론

본 ANALYZE 는 EPANET 분석 이력 처리를 정의하지 않는다. 명세서 6.7 (분석 이력 다운로드) 의 EPANET 산출 결과 활용은 향후 별도 사이클에서 결정. 본 작업 PLAN 의 다운로드 API 는 Python 예측 자료 다운로드만 다루며 EPANET 분석 이력 다운로드는 미구현 (응답에 "EPANET 데이터 미연동" 안내). Python 예측 자료는 [pumpcontrol ANALYZE1](../../20260422/pumpcontrol/ANALYZE1.md) 의 `pump_predc_h` 조회로 제공.

---

### 안건 A9. 권한·메뉴 정합성 (권한메뉴 ANALYZE1 연계)

#### 호출 에이전트
`wtp-backend-engineer` · `wtp-glossary-manager`

#### Round 1 결론

| Q | 사용자 답변 | 회의 결론 |
|---|-----------|---------|
| Q9.1·Q9.2·Q9.3·Q9.4 | 사용자 명시 답변 부재 | **본 ANALYZE 범위 외 — PLAN 직전 별도 검증 의무**. 권한메뉴 PLAN approved 여부 미확인. 가정 섹션 명기 |

#### 안건 A9 종합 결론

본 ANALYZE 는 권한·메뉴 등록 처리를 정의하지 않는다. 본 작업 PLAN 진입 전 권한메뉴 PLAN approved 상태 검증 필요. ADMIN/USER 역할 분리는 [권한메뉴 ANALYZE1](../../20260504/권한메뉴/ANALYZE1.md)·[ANALYZE2](../../20260504/권한메뉴/ANALYZE2.md) 의 `UserRole` enum (ADMIN/USER) + `menu_role_r` 매핑 패턴 재사용 가정. 권한메뉴 PLAN 미진행 시 본 작업 PLAN 에서 menu_m·menu_role_r INSERT 운영 데이터 추가 또는 권한메뉴 PLAN 선행 처리 (별도 사이클).

---

## 표준 사전 카탈로그

> **⚠️ 하위 호환**: 신규 항목이 없는 층위는 "없음" 으로 표기하고 해당 표 생략 가능.

### 신규 표준 단어

(DB 컬럼 조합의 재료 — 의미의 최소 단위. 1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `hr` | 시간(단축형) | 신규 | Q3.3 A 답변. 응답 DTO `inq_hr` 필드. `dtm`(일시)·`dt`(일자) 와 의미 분리 — 정수형 시간 단위 (1~24) |
| `dwld` | 다운로드 | 신규 | `drvn_anls_dwld_h` 컬럼 사용 (`dwld_id`·`dwld_file_nm`·`dwld_format_cd`). 등록 의무 |
| `format` | 형식 | 신규 | `dwld_format_cd` 컬럼 사용 (CSV/XLSX). 합성어이나 향후 재사용 가능 |
| `actl` | 실제 | 신규 | 명세서 ACTL_*·PREDC_* 짝 표현. 응답 DTO 변수명 일관성 위해 등록 |
| `inq` | 조회 | 신규 | 응답 DTO `inq_hr`·`inq_min` 변수명 사용. 향후 `inq_*` 컬럼 도입 시 재료 |

### 신규 표준 데이터 도메인

(값 형식 — SQL 타입·길이·Java 타입. 1차 정의: `swtp/.claude/rules/dict/standard-data-domains.md` — ROOT. **`wtp-dba-reviewer` 2차 승인 필수**)

**없음** (Q4.1·4.2·4.3 답변에 따라 모든 후보 거부 또는 기존 재사용):
- DOM_ELPWR_UNIT_QTY 거부 → `DOM_QTY_15_4` 재사용
- DOM_FILE_FORMAT_10 거부 → `DOM_CODE_20` 재사용
- DOM_INQ_HR·DOM_INQ_MIN 거부 → 컬럼별 `INTEGER` 직접 선언

### 신규 표준 용어

(단어 + 데이터 도메인 → DB 컬럼명. 1차 정의: `.claude/rules/dict/standard-terms.md`)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `dwld_id` | `dwld`(신규) + `id` | `DOM_SEQ_BIGINT` | 신규 | drvn_anls_dwld_h 시계열 PK. `pump_ctrl_id` 패턴 재사용 |
| `dwld_file_nm` | `dwld` + `file`(일반어) + `nm`(기존) | `DOM_NAME_100` | 신규 | 다운로드 파일명. `file` 은 표준 단어 미등록 — 일반 영단어 |
| `dwld_format_cd` | `dwld` + `format`(신규) + `cd`(기존) | `DOM_CODE_20` | 신규 | 파일 형식 코드 (CSV/XLSX) — `FileFormatType` enum 매핑 후보 |
| `data_div_cd` | `data`(일반어) + `div`(기존) + `cd`(기존) | `DOM_CODE_20` | 신규 | 자료 구분 코드 (PRDC/ANLS — 예측조회/분석이력) |
| `dwld_dtm` | `dwld` + `dtm`(기존) | `DOM_DTM` | 신규 | 다운로드 일시. BaseEntity `rgstr_dtm` 흡수 가능 (PLAN 단계 결정) |

---

## 신규 엔티티/DB 컬럼

### 1. `drvn_anls_dwld_h` — 다운로드 감사 시계열

| 항목 | 값 |
|------|-----|
| 비즈니스 도메인 | **PLAN 단계 결정 위임** — `pump`(현행) / `instrument`(마스터도메인설계 폐기 후) / 신규 등 후보 다수. 본 작업의 패키지 위치는 PLAN 에서 결정 |
| 테이블 suffix | `_h` (이력) |
| PK | `dwld_id BIGINT` (`DOM_SEQ_BIGINT`) — `GenerationType.SEQUENCE` + `allocationSize` |
| 파티션 정책 | **PLAN 단계 결정** — 다운로드 빈도 1분 raw 대비 낮음. 월 RANGE 파티션 적용 의무 재검토. 보존 5년 (`partitioning-and-retention.md §2` 다운로드 감사 일반 기준) |
| 인덱스 후보 | `(rgstr_id, rgstr_dtm DESC)` — 사용자별 다운로드 이력 조회 |
| FK | 없음 — 시계열 → 마스터 FK 금지 (`partitioning-and-retention.md §1`). `rgstr_id` 는 BaseEntity 자동 주입 |
| BaseEntity 적용 | 4 컬럼 (`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`) — INSERT-only 이므로 `updt_*` 데드 컬럼 검토 (PLAN 단계) |
| 사용 데이터 도메인 | `DOM_SEQ_BIGINT`·`DOM_NAME_100`·`DOM_CODE_20`·`DOM_DTM`·`DOM_ID_50` |

#### 컬럼 후보

| 컬럼 | 데이터 도메인 | NULL | 설명 |
|------|-------------|------|------|
| `dwld_id` | `DOM_SEQ_BIGINT` | NOT NULL | PK |
| `data_div_cd` | `DOM_CODE_20` | NOT NULL | 자료 구분 (PRDC/ANLS) |
| `dwld_file_nm` | `DOM_NAME_100` | NOT NULL | 다운로드 파일명 |
| `dwld_format_cd` | `DOM_CODE_20` | NOT NULL | 파일 형식 (CSV/XLSX) |
| `rgstr_dtm` | `DOM_DTM` | NOT NULL | 다운로드 일시 (BaseEntity 자동 주입) |
| `rgstr_id` | `DOM_ID_50` | NOT NULL | 다운로드 사용자 ID (BaseEntity 자동 주입) |

### 2. facility 자식 'POINT' — 관로 계측 분기점

| 항목 | 값 |
|------|-----|
| 부모 마스터 | `facility_m` (마스터도메인설계 PLAN2 approved) |
| 자식 종류 코드 | `facility_type_cd = 'POINT'` (신규 추가) |
| 자식 테이블 | `point_m` (또는 facility_m 단일 테이블 운용 — 자식 전용 컬럼 부재 시) — PLAN 단계 결정 |
| JPA 패턴 | `@Inheritance(JOINED)` + `@DiscriminatorValue("POINT")` ([`entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴`](../../../../.claude/rules/entity-patterns.md)) |
| 자식 전용 컬럼 | **PLAN 단계 결정** — 명세서에 명시 부재. 분기 위치·관경 등 후보 |
| PK | `facility_id` 부모 PK 자동 상속 (UUID 자동 생성) |
| 도메인 룰 | facility 다형성 조회 시 `facility_type_cd = 'POINT'` 필터 강제 ([`ot-integration.md §5`](../../../../.claude/rules/ot-integration.md)) |

---

## 기존 사전·패턴과의 충돌

### `oprt` vs `oprtng` 충돌 (Q3.1 결정)

- **충돌 항목**: 명세서 ACTL_OPRT_YN·PREDC_OPRT_YN 의 `oprt` 신규 단어 후보
- **해소책**: `oprtng`(기존 — `swtp/.claude/rules/dict/standard-words.md` 등록) 재사용. 컬럼명 → `actl_oprtng_yn`·`predc_oprtng_yn`
- **근거**: `oprtng` 은 본래 의미 = 상태값. `_yn` 결합으로 "운전중 여부" 의미 표현 가능. 동의어/유사 의미 단어 동시 등록 금지 룰 (standard-words.md §사용 규칙) 정합

### `epanet` 비즈니스 도메인 약어 등록 거부 (Q3.2 결정)

- **충돌 항목**: 명세서 EPANET_VAL 컬럼의 `epanet` 비즈니스 도메인 약어 후보
- **해소책**: 등록 거부. `scada` 거부 선례 (2026-04-25) 동일 패턴 — 외부 도구명은 swtp 비즈니스 도메인 아님
- **본 ANALYZE 범위 외**: EPANET 분석 이력 처리는 별도 사이클 (Q3.2 발제 추후)

### `actl` 단어 vs 기존 `predc` 의 짝 (안건 A3 보완)

- **충돌 항목**: `actl` 신규 등록 시 짝 단어 `predc` (기존, pumpcontrol ANALYZE1) 와 의미 짝 형성. 의미 충돌 없음
- **해소책**: `actl` 등록. `actl_*`·`predc_*` 짝으로 실제·예측 컬럼 일관 표현

### `dwld_file_nm` 의 `file` 단어 미등록 (실용적 결정)

- **충돌 항목**: `file` 일반 영단어 — 표준 단어 사전 등록 가치
- **해소책**: 본 ANALYZE 는 등록하지 않음. 1건 사용 시점에 등록 부담 회피. 향후 `file_*` 컬럼 누적 시 (3건 이상) 별도 ANALYZE 로 등록 검토

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안

1. **신규 영속화 엔티티 2개**:
   - `drvn_anls_dwld_h` (시계열 — 다운로드 감사)
   - facility 자식 'POINT' (마스터 다형성 자식 — `facility_type_cd = 'POINT'`)

2. **신규 응답 DTO 4종** (영속화 0):
   - `DrvnSttsInqDto` — 조회 파라미터 DTO (DRVN_STTS_INQ 매핑)
   - `DrvnAnlsRsltDto` — 운전 분석 결과 통합 응답 (DRVN_ANLS_RSLT + PREDC_ANLS_CMP_DATA 통합)
   - `PumpOprtngDto` — 펌프 가동 상태 응답 (PMP_OPRTNG_HSTRY 매핑, rawdata_1m_h + pump_predc_h 조인 결과)
   - `BranchMeasPredcDto` — 분기 지점 계측·예측 응답 (BRANCH_MEAS_PREDC_DATA 매핑, facility 'POINT' 자식별 시계열)

### DB 설계 변경 초안

- **`drvn_anls_dwld_h` 테이블 신설** — 컬럼 6 (위 §신규 엔티티/DB 컬럼 표 참조). 파티션 정책 PLAN 결정
- **`facility_m` 자식 'POINT' 추가** — `facility_type_cd` enum 확장 (PWTF/DWT/RSV/POINT 등). `point_m` 테이블 신설 vs facility_m 단일 운용 — PLAN 결정
- **rawdata_1m_h + pump_predc_h JOIN 조회 패턴** — `PumpOprtngDto` 응답을 위한 QueryDSL 쿼리 또는 PostgreSQL VIEW (`pump_oprtng_v`) — PLAN 결정

### 적용 패턴

- **JPA JOINED 다형성** — facility 자식 'POINT' 추가 ([`entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴`](../../../../.claude/rules/entity-patterns.md))
- **`equip_type_cd = 'PUMP'` 필터 강제** — 본 작업의 모든 펌프 조회 (Q5.3 결정, [`ot-integration.md §5`](../../../../.claude/rules/ot-integration.md))
- **시계열 → 마스터 FK 금지** — `drvn_anls_dwld_h` 는 마스터 FK 미생성. `rgstr_id` 는 논리 참조만
- **응답 DTO 4종 + Swagger `@Schema(implementation)`** — 사용자 정의 enum/DTO 필드는 implementation 명시 ([`api-patterns.md §DTO @Schema(implementation) 명시 패턴`](../../../../.claude/rules/api-patterns.md))

---

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. 최소 1건 의무 — 본 ANALYZE 6건 작성.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 마스터도메인설계 PLAN2 approved 검증 완료 (`docs/plan/20260506/마스터도메인설계/PLAN2.md`) — facility_m·instrument_m·tag_m 신 구조 사용 가능 | 결정 | Q5.1·Q5.2 D 답변. 사용자 검증 완료 |
| 권한메뉴 PLAN approved 여부 미확인 — 본 작업 PLAN 직전 검증 의무 | 미해결 | A9 사용자 답변 부재. PLAN 진입 전 권한메뉴 PLAN approved 상태 확인 필요. 미진행 시 본 작업 PLAN 에서 menu_m·menu_role_r INSERT 추가 vs 별도 사이클 선행 결정 |
| 운전현황 분석 화면이 단일 facility 조회 vs 복수 facility 통합 조회 | 미해결 | Q5.4 보류. 명세서 7장 7.1 흐름은 단일 facility 가정이나 명시 부재. PLAN 단계 결정 (쿼리·인덱스 설계 영향) |
| `drvn_anls_dwld_h` 파티션 키·보존 기간·집계 전략 | 미해결 | Q6 답변에서 5종 시계열 후보 모두 영속화 안 함 결정으로 잔존 신규 시계열 1건만 — 파티션 정책 PLAN 단계 결정 |
| EPANET 시스템 위치 (외부 마이크로서비스 / AI 서버 내부 / 백엔드 내부 / 결과 import 만) | 미해결 | A8 안건 — Q3.2 답변 "발제 추후로 미룸". 본 ANALYZE 범위 외, 별도 사이클 |
| DOM_ERR_RT 처리 — 회의 결론 거부 권장 (`DOM_QTY_15_4` 재사용 + 백분율 0~100 애플리케이션 검증) | 미해결 | AGENDA A4 후보 표 등재되어 있으나 사용자 답변 부재. 사용자 ANALYZE 검토 시 확인 |

---

## 성공 기준 후보 (PLAN 변환 대상)

> 본 섹션의 후보 기준은 PLAN 단계에서 ROOT [`coding-discipline.md` §4.2](../../../../../.claude/rules/coding-discipline.md) 의 "성공 기준 (검증 가능 형태)" 로 변환된다. 검증 명령 초안은 PLAN 에서 명령·테스트·조회로 확정한다. 모호 표현 정규식 8건 차단 패턴 회피.

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 운전현황 조회 API (`GET /api/pump/drvn-status`) — 200 응답 + DrvnAnlsRsltDto 정상 직렬화 + `equip_type_cd = 'PUMP'` 필터 적용 | `./gradlew.bat :api:test` PASS 시 통합 테스트 1건 GREEN |
| 다운로드 API (`POST /api/pump/drvn-status/download`) — 200 응답 + `drvn_anls_dwld_h` INSERT 1건 검증 + DOM_FILE_FORMAT 검증 (CSV/XLSX 외 거부) | 단위 테스트 3건 (정상 INSERT·DOM_CODE_20 위반 거부·`AuditingEntityListener` 자동 주입) GREEN |
| facility 자식 'POINT' 다형성 조회 — `facility_type_cd = 'POINT'` 필터 강제 검증 | 단위 테스트 1건 + REVIEW 단계 `wtp-domain-expert` 점검 (필터 누락 시 블로커) |
| `rawdata_1m_h` + `pump_predc_h` JOIN 응답 (`PumpOprtngDto`) — 시계열 시간 윈도우 1분 단위 정합성 검증 | 단위 테스트 2건 (정상 JOIN 결과·시간 윈도우 경계 케이스) GREEN |
| AI 운전모드 표시 (read-only) — `ai_drvn_mod_p` 의 `ai_drvn_mod` 사용자 의도 + `ai_mode_cd` 시스템 상태 둘 다 응답 노출 | 단위 테스트 1건 GREEN. ※ AI 운전모드 변경 기능 (명세서 6.2) 은 Q7 결정에 따라 별도 사이클 분리, 본 작업 범위 외 |

---

## 도메인 룰 4영역 점검

> 본 ANALYZE 의 도메인 4영역 (알람 4단계 / 인터록 / AI 운전 모드 / 이력 기록) 해당 여부를 표시한다. 인용 근거: [`backend/.claude/rules/ot-integration.md`](../../../../.claude/rules/ot-integration.md). form 효력 강화 — "비해당" 단독 사유 부재 차단 + "해당" 표기 시 근거/영향 컬럼 공란 차단.

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 운전현황 분석은 조회 화면. 알람 임계값·전이 조건·복귀 조건 변경 없음. 단, 화면에 알람 상태 표시 가능 — 해당 표시는 기존 알람 도메인 (별도 작업) 의 응답 재사용, 본 ANALYZE 범위 외 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 본 작업은 조회·다운로드 API 만 도입. 제어 명령 발행 없음 — 인터록 선행조건 검사 적용 대상 0건. AI 운전모드 변경 기능 (명세서 6.2) 도 Q7 결정에 따라 별도 사이클 분리되었으므로 본 작업 범위에서는 인터록 평가 미발생 |
| AI 운전 모드 (`ot-integration.md §5`) | **해당** ⚠️ — 단, 본 ANALYZE 는 별도 사이클로 분리 (Q7 결정) | 명세서 6.2 의 AI 운전모드 변경 기능은 `ai_drvn_mod_p` UPDATE + `ai_drvn_mod_h` INSERT 패턴 (pumpcontrol ANALYZE1 도입 완료) 재사용 — 본 ANALYZE 신규 결정 없음. 본 작업 범위에서는 AI 운전모드 표시 (read-only) 만 포함, 변경 기능은 별도 사이클. 사용자 의도 (`ai_drvn_mod`) / 시스템 상태 (`ai_mode_cd`) 강제 전환 (SCADA 5분 초과) 은 본 작업 영향 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | **해당** ⚠️ | `drvn_anls_dwld_h` 다운로드 감사 INSERT 의무 — 다운로드 API 호출 시마다 1행 INSERT. 다운로드 실패 시 INSERT 미수행 (예외 시 정상 응답 0). `rgstr_id`·`rgstr_dtm` 자동 주입 (`AuditingEntityListener`). AI 운전모드 변경 이력 (`ai_drvn_mod_h`) 은 별도 사이클 분리 (Q7) 로 본 ANALYZE 신규 결정 없음 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

> [`backend/.claude/rules/process/doc-harness/checkbox-rules.md`](../../../../.claude/rules/process/doc-harness/checkbox-rules.md) 준수 — 전체 상대 경로 + 백틱.

- [x] `swtp/.claude/rules/dict/standard-words.md` — `hr` 표준 단어 신규 등록 (Q3.3 A 답변)
- [x] `swtp/.claude/rules/dict/standard-words.md` — `dwld` 표준 단어 신규 등록 (drvn_anls_dwld_h 컬럼 사용)
- [x] `swtp/.claude/rules/dict/standard-words.md` — `format` 표준 단어 신규 등록 (dwld_format_cd 컬럼 사용)
- [x] `swtp/.claude/rules/dict/standard-words.md` — `actl` 표준 단어 신규 등록 (실제·예측 짝 표현)
- [x] `swtp/.claude/rules/dict/standard-words.md` — `inq` 표준 단어 신규 등록 (응답 DTO 변수명 inq_hr·inq_min)
- [x] `swtp/.claude/rules/dict/domain-abbreviations.md` — facility 자식 종류 'POINT' 도입 명기 (facility 행 비고 갱신 — POINT 자식 추가)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `dwld_id` 표준 용어 신규 등록 (drvn_anls_dwld_h PK)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `dwld_file_nm` 표준 용어 신규 등록 (다운로드 파일명)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `dwld_format_cd` 표준 용어 신규 등록 (파일 형식 코드)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `data_div_cd` 표준 용어 신규 등록 (자료 구분 코드)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `dwld_dtm` 표준 용어 신규 등록 (다운로드 일시 — BaseEntity rgstr_dtm 흡수 후보 명기)

---

## 산출물

- [계획안](../../../plan/20260508/송수펌프제어_운전현황분석/PLAN1.md) — `/dev:plan` 단계에서 작성 (PLAN1 작성일 2026-05-08, ANALYZE 작성일 2026-05-07 — 날짜 차이는 사용자 승인 사이클 진행)

---

## 폐기·갱신 이력

| 일자 | 변경 | 배경 |
|------|------|------|
| 2026-05-07 | 신규 작성 (사이클 1) | FR-PMP-002 운전현황 분석 도메인 분석. 사용자가 AGENDA.md 의 9 안건 (A1~A9) 모든 핵심 질문 (Q1.1~Q9.4) 에 사전 답변 제공 → 5인 회의 형식적 진행 (4 에이전트 병렬 호출 생략, 답변 기반 결론 채택). 신규 영속화 엔티티 2개 (`drvn_anls_dwld_h` + facility 자식 'POINT') + 응답 DTO 4종. 시계열 영속화 5종 후보 모두 영속화 안 함 결정 (Q1.1~Q1.3). A8 EPANET·A9 권한메뉴 본 ANALYZE 범위 외 (별도 사이클). Q7 도메인 4영역 분리 결정 — AI 운전모드 변경 기능 (명세서 6.2) 별도 사이클. |
