---
status: approved
created: 2026-05-02
updated: 2026-05-03
---
# 마스터도메인설계 — 도메인 분석

> **본 ANALYZE 는 Round 3 (2026-05-02) 까지 누적되었다.** Round 1·2 결론 (외부 할당 PK + tag_id + tag_val 1컬럼) 은 Round 3 사용자 정정 입력으로 일부 번복되었다 — 안건 1·2 (PK 시스템 자체 생성) · 안건 5 (tag_m PK = tag_srl_no 자연키) · 안건 6 (rawdata 컬럼 분리 + BaseEntity 4) 의 정정 결론을 우선한다. Round 1·2 회의록은 결정 흐름 보존 목적으로 유지된다.

## 작업 배경

사용자 요청: 4개 마스터 도메인 엔티티 (시설·계측기·태그·로우데이터) 설계.
- 시설: 정수지·배수지·저수지 + 정수장별 추가 가능
- 계측기: 유량계·압력계·수위계·전력량계·밸브·펌프 + 정수장별 추가 가능
- 태그: 계측기에서 추출되는 단일 계측값 (1:N)
- 로우데이터: 분 단위 시계열 적재 (계측일시·태그번호·값)

핵심 안건: "단일 마스터 + 분류별 자식 (상속) vs 분류별 별도 마스터" 의 비판적 재평가 (사용자 의도: "기존 펌프 도메인설계 진행방향에 대해서는 알고 있었어. 하지만 이게 타당성이 있는 설계인지를 고려해보고 싶은거야" + "냉철하게 비교 분석").

기존 코드베이스 상태:
- `common/src/main/resources/db/init/V1__pumpcontrol_master_tables.sql` — `pwtf_m`·`dwt_m`·`pump_m` + 5개 관련 테이블 (분류별 별도 마스터 채택, pumpcontrol ANALYZE1 2026-04-22 결정)
- `common/src/main/java/com/mo/swtp/pump/domain/` — `PurifiedWaterTank.java`·`DistributionWaterTank.java`·`Pump.java` + 5개 관련 엔티티
- 운영 데이터 0건 (개발 단계) — 마이그레이션 비용 평가 시 운영 영향 없음

본 ANALYZE 의 핵심 발견 (Round 1 → Round 2 진화):
- 모노레포 reference 코드 `reference/common/src/main/java/com/hscmt/simulation/dataset/domain/Dataset.java` — JPA `@Inheritance(JOINED)` + `@DiscriminatorColumn` 패턴이 검증되어 있음. `MeasureDataset extends Dataset` 자식 엔티티 사례
- backend-engineer 의 Round 1 주장 ("`@MappedSuperclass` + `@Inheritance` 혼합 불가" + "엔티티 상속 3단이 `coding-discipline.md §2.1` 임계 충돌") 부정확 — DataSet.java 사례 + `§2.1` 비고 ("호출 스택" — 엔티티 상속 아님) 로 반증
- 운영 데이터 0건 + `pwtf`·`dwt` 가 `domain-abbreviations.md` "도입 예정" 상태 → 폐기 비용 재평가 (사전 갱신 + 코드 일괄 변경, 마이그레이션 SQL 0건)

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 시설 마스터 패턴 — 단일 마스터 + JPA JOINED + self-FK 채택
- 호출 에이전트: `wtp-domain-expert`, `wtp-backend-engineer`, `wtp-dba-reviewer`, `wtp-glossary-manager` (Round 1 + Round 2)
- Round 1 답변 요약:
  - **wtp-domain-expert**: 권고 C (혼합 — 시설 별도 + 계측기 단일 검토 가능). 시설은 운전 모드/인터록 단위
  - **wtp-backend-engineer**: 권고 A (전면 별도). JPA `@MappedSuperclass` + `@Inheritance` 혼합 불가 + `coding-discipline.md §2.1` 추상화 3단 충돌 (※ Round 2 에서 부정확 명시 철회)
  - **wtp-dba-reviewer**: 권고 A (별도). SINGLE_TABLE NULL 오염 + JOINED 매번 JOIN + V1→V6 마이그레이션 비용
  - **wtp-glossary-manager**: 권고 C (혼합). 시설 안착 → 폐기 비용 비대 + 계측기 5종은 OT 센서 코드 이중화 방지
- Round 2 컨텍스트 변화:
  - DataSet.java 사례 검증 (`@MappedSuperclass` → `@Entity @Inheritance(JOINED)` → `@Entity` 정상 작동)
  - 사용자 의도 정정: "재귀적구조 = 시설물 self-FK 정도, 강제 계층 X" + "계측기 = facility_id FK 1:N 소속"
  - 운영 데이터 0건 (개발 단계) — 마이그레이션 비용 우려 무효화
- Round 2 답변 요약:
  - **wtp-domain-expert**: 권고 변경 → **단일 facility_m + JPA JOINED + self-FK** + 블로커 2건 (펌프 통합 시 pump_m FK 무결성 파괴, ai_drvn_mod_p facility_type_cd 필터 강제 의무)
  - **wtp-backend-engineer**: Round 1 부정확 명시 철회 + 권고 변경 → **단일 + JPA JOINED + self-FK**. 외부 할당 PK + JOINED 조합 사전 검증 권고
  - **wtp-dba-reviewer**: 권고 유지 (시설 별도) — 단, 다형성 전체 조회 빈도 확인 후 재평가 가능 명시
  - **wtp-glossary-manager**: Round 1 번복 → **단일 + JPA JOINED**. 운영 데이터 0건 + `domain-abbreviations.md` 미안착 상태 재확인으로 폐기 비용 재평가
- 사용자 결정 (2026-05-02):
  - 다형성 전체 조회 빈도: "드물거나 없음" → JOINED 핫패스 우려 해소
  - 시설 마스터 구조: 단일 facility_m + JPA JOINED + parent_facility_id self-FK + DiscriminatorColumn(facility_type_cd) 채택 확정
- **결론**: 시설 = 단일 `facility_m` + `@Inheritance(JOINED)` + `@DiscriminatorColumn(name="facility_type_cd")` + `parent_facility_id` self-FK. 자식 엔티티: `PurifiedWaterTank`·`DistributionWaterTank`·`Reservoir extends Facility`. 자식 테이블: `pwtf_m`·`dwt_m`·`rsv_m` (JPA JOINED 자식 테이블). 기존 V1 DDL 폐기 + V6 재설계.

### 안건 2: 계측기 마스터 패턴 — 단일 마스터 + JPA JOINED + facility_id FK 채택
- 호출 에이전트: 안건 1 과 동일 4 에이전트 (Round 1 + Round 2)
- Round 2 답변 요약:
  - **wtp-domain-expert**: 센서 5종 (유량계·압력계·수위계·전력량계·밸브) 단일 instrument_m 통합 가능. **펌프 분리 필수** (블로커: pump_ctrl_h·pump_interlock_p·pump_predc_h FK 무결성 파괴)
  - **wtp-backend-engineer**: 단일 + JOINED + facility_id FK (펌프 포함 통합)
  - **wtp-dba-reviewer**: 단일 + JOINED 권고 (tag_m.instrument_id FK 단순화)
  - **wtp-glossary-manager**: 단일 + JOINED + equip_type_cd. tag_se_cd (FRI/PRI/LEI/PWI/RMS) 와 의미 계층 다름 (이중화 우려 무효화). 단, pump_* 표준 용어 처리 별도 안건 권고
- 사용자 결정 (2026-05-02):
  - 펌프 처리: **통합 (instrument_m 의 자식)** — pump_ctrl_h·pump_interlock_p·pump_predc_h 의 pump_id FK 다수 재설계 수용. 운영 데이터 0건 활용
  - facility_type_cd 필터 강제: PLAN 단계 제약 명시
- **결론**: 계측기 = 단일 `instrument_m` + `@Inheritance(JOINED)` + `@DiscriminatorColumn(name="equip_type_cd")` + `facility_id` FK to facility_m. 자식 엔티티 6종: `Pump`·`Valve`·`FlowMeter`·`PressureMeter`·`LevelMeter`·`PowerMeter extends Instrument`. 자식 테이블: `pump_m`(자식)·`valve_m`·`flwmtr_m`·`prsmtr_m`·`lvmtr_m`·`elcmtr_m` (JPA JOINED 자식 테이블).
- **PLAN 제약 (필수 명시)**: `ai_drvn_mod_p` 등 AI 운전 모드 관련 쿼리는 `facility_type_cd='PWTF'` 필터를 Service 계층에서 일관 적용. wtp-domain-expert 블로커 2 해소 조건.

### 안건 3: 저수지 (Reservoir) 마스터 설계
- 호출 에이전트: `wtp-domain-expert`
- 사용자 정의 (2026-05-02): "배수지의 물탱크 역할을 하는 것" — 배수지의 보조/확장 저장소
- 사용자 추가 정의 (2026-05-02): "재귀적구조는 시설물이 상위 시설물을 바라볼 수 있다는 정도지" — `parent_facility_id` self-FK 로 표현
- 인터록 연관: **미정 — 향후 결정 가능** (ANALYZE 가정 섹션 명시)
- 배수지 ↔ 저수지 관계: 사용자 발언 "재귀적구조" 의 본의는 self-FK 자유 표현 (1:1·1:N·N:1 강제 X)
- **결론**: `Reservoir extends Facility` (`@DiscriminatorValue("RSV")`) + `parent_facility_id` self-FK 로 배수지 (또는 다른 시설) 자식 가능. 저수지 전용 컬럼은 자식 테이블 `rsv_m` 에 정의 (현 시점 추가 컬럼 없음, 향후 인터록·계측기 부착 결정 시 자식 테이블 컬럼 확장).

### 안건 4: 계측기 6종 자식 엔티티 설계 (Pump 포함)
- 호출 에이전트: `wtp-backend-engineer`, `wtp-glossary-manager`
- 안건 2 결정 (단일 instrument_m + JPA JOINED) 에 종속
- 6종 자식 엔티티 + 자식 테이블 구조:

| 자식 엔티티 | 자식 테이블 | DiscriminatorValue | 자식 전용 컬럼 (예시) |
|----------|----------|-------------------|---------------------|
| `Pump` | `pump_m` | `PUMP` | `rated_head` (정격 양정), `rated_flwrt` (정격 유량), `tag_nm` (SCADA 태그) |
| `Valve` | `valve_m` | `VALVE` | TBD (밸브 종류·정격 압력 등) |
| `FlowMeter` | `flwmtr_m` | `FLWMTR` | `range_min`·`range_max`·`unit_cd` (m³/h) |
| `PressureMeter` | `prsmtr_m` | `PRSMTR` | `range_min`·`range_max`·`unit_cd` (kgf/cm²) |
| `LevelMeter` | `lvmtr_m` | `LVMTR` | `range_min`·`range_max`·`unit_cd` (m) |
| `PowerMeter` | `elcmtr_m` | `ELCMTR` | `unit_cd` (kW or kWh — 순시/누적 구분) |

- 부모 instrument_m 공통 컬럼: `instrument_id` (PK, DOM_ID_50), `instrument_nm` (DOM_NAME_100), `facility_id` (FK to facility_m, DOM_ID_50), `equip_type_cd` (DiscriminatorColumn, DOM_CODE_20), `use_yn` (DOM_YN), BaseEntity 4
- 자식 테이블 PK = 부모 instrument_id (JPA JOINED 자동)
- **결론**: 6종 자식 엔티티 + 6개 자식 테이블 + 부모 1개 = 7개 테이블. 신규 계측기 종류 추가 시 자식 엔티티 + 자식 테이블 1개씩 추가 (부모 변경 0건).

### 안건 5: tag_m 신규 설계
- 호출 에이전트: `wtp-backend-engineer`, `wtp-glossary-manager`
- 사용자 명시: 계측기-태그 1:N 구조
- PK 결정 후보:
  - (a) `tag_id` (DOM_ID_50) — 외부 할당 PK 패턴 일관성
  - (b) `tag_nm` (DOM_TAG_NM_50) — 자연키 (`706-FRI-xxx-xxx` 형식 SCADA 태그명 자체) — 기존 `pump_m.tag_nm`·`rawdata_1m_h.tag_nm` 과 직접 조인 가능
- **결론 (잠정)**: PK = `tag_id` (DOM_ID_50, 외부 할당) + UNIQUE 제약 on `tag_nm` (조회용). 일관성 + 향후 SCADA 태그명 변경 가능성 (자연키 회피). `tag_nm` 은 SCADA 수신 시 키로 사용되므로 UNIQUE 인덱스 필수
- tag_m 컬럼:

| 컬럼 | 타입/도메인 | 비고 |
|------|----------|------|
| `tag_id` | DOM_ID_50 | 외부 할당 PK, Persistable<String> |
| `instrument_id` | DOM_ID_50 | FK to instrument_m (계측기 1:N 소속) |
| `tag_nm` | DOM_TAG_NM_50 | SCADA 태그명, UNIQUE 인덱스 |
| `tag_se_cd` | DOM_CODE_20 | TagMeasurementType enum (FRI·PRI·LEI·PWI·RMS) |
| `tag_desc` | DOM_TEXT | 자유형 설명 |
| `unit_cd` | DOM_CODE_20 | 측정 단위 (m³/h·kgf/cm²·m·kW·kWh 등) |
| `use_yn` | DOM_YN | YnType enum |
| BaseEntity 4 | — | rgstr_dtm·updt_dtm·rgstr_id·updt_id |

### 안건 6: 로우데이터 SSOT 결정 + DDL 신규 설계
- 호출 에이전트: `wtp-dba-reviewer`, `wtp-glossary-manager`
- 사용자 명시 (2026-05-02): "로우데이터는 마스터엔터티라고 표현했지만 데이터베이스 관점에서의 역할은 **이력테이블**이 맞아. 1분마다 태그의 계측값이 쌓이는 구조"
- 룰 SSOT 충돌:
  - `db/partitioning-and-retention.md §1` — `rawdata_m` 명시
  - `db/indexing-and-migration.md §1` — `rawdata_m` 사용
  - `ot-integration.md` — `rawdata_1m_h` 사용
  - `domain-abbreviations.md` — `raw` 약어 도입 예정 + `rawdata_1m_h` 예시
- **결론**: SSOT = **`rawdata_1m_h`** (`_h` suffix 이력 테이블 + `1m` 분 단위 명시). 향후 5분/15분 집계 (`rawdata_5m_h`·`rawdata_15m_h`) 와 일관 패턴. 룰 갱신 지시서로 SSOT 정렬 의무.
- DDL 컬럼:

| 컬럼 | 타입/도메인 | 비고 |
|------|----------|------|
| `rawdata_id` | DOM_SEQ_BIGINT | 시계열 BIGINT PK + GenerationType.SEQUENCE + allocationSize=100 |
| `acq_dtm` | DOM_DTM | 파티션 키 (월 RANGE) + NOT NULL |
| `tag_id` | DOM_ID_50 | 논리 참조 (시계열 → 마스터 FK 금지 정책 적용, 애플리케이션 레벨 검증) |
| `tag_val` | DOM_QTY_15_4 | SCADA 측정값 |
| `quality_cd` | DOM_CODE_20 | 품질 코드 (GOOD·BAD·UNCERTAIN, ot-integration.md §3) |
| `rgstr_dtm` | DOM_DTM | 등록 일시 (immutable 이력 BaseEntity 미상속, 명시 INSERT) |
| `rgstr_id` | DOM_ID_50 | 등록자 ID (immutable 이력 INSERT-only) |

- 파티션: 월 RANGE on `acq_dtm` + V3 패턴 (6개월 선행 생성)
- 인덱스: BRIN on `acq_dtm` + 복합 인덱스 (`tag_id`, `acq_dtm DESC`)
- 보존 기간: 13개월 (db/partitioning-and-retention.md §2 SCADA 원시 데이터 정책 적용)

### 안건 7: ROOT 어휘 사전 신규 등록 + 기존 약어 폐기
- 호출 에이전트: `wtp-glossary-manager`
- 폐기 (비즈니스 도메인 약어 → discriminator 코드값으로 강등):
  - `pwtf` (정수조) → `facility_type_cd='PWTF'`
  - `dwt` (배수지) → `facility_type_cd='DWT'`
  - `rsv` (저수지) → `facility_type_cd='RSV'` (도입 예정 약어 폐기)
  - `pump` (펌프) → `equip_type_cd='PUMP'` (마스터 도메인 → 자식 디스크리미네이터)
- 신규 등록:
  - `facility` — 시설 비즈니스 도메인 약어 (`com.mo.swtp.facility` 패키지)
  - `instrument` — 계측기 비즈니스 도메인 약어 (`com.mo.swtp.instrument` 패키지)
  - `tag` (도입 예정 → 마스터 도메인 승격, `com.mo.swtp.tag` 패키지)
  - `raw` (도입 예정 → 마스터 도메인 승격, `com.mo.swtp.raw` 패키지) — 또는 `rawdata` 풀네임 검토
- 표준 단어 신규 등록:
  - `quality` (품질) — `quality_cd` 컬럼 조합 재료
  - `range` (범위) — `range_min`·`range_max` 컬럼 조합 재료
  - `unit` (단위) — `unit_cd` 컬럼 조합 재료
  - `valve` (밸브) — 단어인지 약어인지 분류 필요 (안건 4 결정 후 확정)
- 표준 데이터 도메인 신규 등록:
  - 없음 (기존 DOM_CODE_20·DOM_ID_50·DOM_TAG_NM_50·DOM_QTY_15_4·DOM_DTM·DOM_TEXT·DOM_YN·DOM_SEQ_BIGINT 재사용)
- 표준 용어 (DB 컬럼) 신규 등록 (다수 — 카탈로그 §신규 표준 용어 참조)

### Round 3 — 사용자 새 요구사항 반영 (2026-05-02)

- 호출 에이전트: 메인 Claude (오케스트레이터) — 사용자 직접 요구사항 입력 + 4 에이전트 사후 점검 항목
- 입력: 사용자가 simplified entity skeleton 4종 직접 제시 (시설·계측기·태그·로우데이터 컬럼 명세)

#### 사용자 정정 입력 (요약)
- **시설/계측기 PK**: 외부 할당 → 시스템 자체 생성. PK 자체보다 **이름값(`facility_nm`·`instrument_nm`) UNIQUE** 가 비즈니스 식별자
- **시설 마스터 추가 컬럼**: `disp_ord`(표시순서), `main_yn`(주요시설여부)
- **계측기 마스터 추가 컬럼**: `disp_ord`(표시순서)
- **태그 PK 변경**: `tag_id`(외부 할당 50자) → `tag_srl_no`(태그시리얼번호 자연키, 외부 할당 — `srl` 약어는 swtp 4-5자 컨벤션 정합)
- **태그 컬럼 위치 이동**: `unit_cd` 는 tag_m 컬럼 (Round 2 의 계측기 자식 테이블 → tag_m 으로 이동)
- **태그 신규 컬럼**: `io_yn` 또는 `io_cd` (입출력여부 — 의미 결정 미해결)
- **로우데이터 컬럼**: `tag_val` 1개 → `raw_val`(원본값) + `corr_val`(수정값) 2개 분리 + `quality_cd` 유지 + **BaseEntity 4** 적용 (immutable 이력 패턴 폐기)
- **자식 엔터티/테이블 9종**: Round 2 결정 (PWTF·DWT·RSV·PUMP·VALVE·FLWMTR·PRSMTR·LVMTR·ELCMTR) 유지. 단, **자식 전용 컬럼** (예: `pump_m.rated_head`·`dwt_m.min_req_prsr`) 은 추후 요구사항명세서 기반 PLAN 단계에서 결정 — 본 ANALYZE 시점에는 자식 테이블 skeleton 만 정의

#### 결론 정정 (안건별)
- **안건 1·2 (시설·계측기 마스터)**: PK = UUID 시스템 자체 생성 (`@GeneratedValue(GenerationType.UUID)`) + `Persistable<String>` 미구현. 자식 테이블 PK 는 부모 UUID 동일 (JPA JOINED 표준 동작). `facility_nm`·`instrument_nm` UNIQUE 인덱스 필수. `disp_ord`·`main_yn`(시설만) 추가 컬럼.
- **안건 5 (tag_m)**: PK = `tag_srl_no` (태그시리얼번호 자연키, 외부 할당, `Persistable<String>` 구현). `tag_id` (Round 1·2 결정) 폐기. `unit_cd` 는 tag_m 컬럼. `io_yn`/`io_cd` 신규 (의미 PLAN 결정).
- **안건 6 (rawdata_1m_h)**: 컬럼 = `raw_val` + `corr_val` + `quality_cd` + BaseEntity 4 (immutable 이력 패턴 폐기). `corr_val` 갱신 시 `updt_dtm`·`updt_id` 자동 갱신으로 보정 시점 추적. tag 참조 컬럼명 = `tag_srl_no` (tag_m PK 와 동일 컬럼명, 논리 참조).

## 표준 사전 카탈로그

### 신규 표준 단어
(DB 컬럼 조합 재료 — ROOT `swtp/.claude/rules/dict/standard-words.md`)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `quality` | 품질 | 신규 | `quality_cd` 컬럼 조합 재료. ot-integration.md §3 SCADA QUALITY 코드 |
| `range` | 범위 | 신규 (보류) | `range_min`·`range_max` 컬럼 조합 재료 — Round 3 자식 전용 컬럼 보류로 PLAN 단계 도입 시 등록 |
| `unit` | 단위 | 신규 | `unit_cd` 컬럼 조합 재료 (tag_m 위치 — Round 3 정정) |
| `equip` | 장비 | 신규 | `equip_type_cd` 컬럼 조합 재료. instrument 자식 종류 코드 |
| `instr` | 계측기 (약어) | 폐기·통합 | `instrument` 풀네임 권장 (8자 미만 의무 X) |
| `valve` | 밸브 | 신규 (보류) | 자식 엔티티 종류 명 — Round 3 자식 전용 컬럼 보류로 자식 테이블명 (`valve_m`) 에만 사용. PLAN 단계 자식 컬럼 도입 시 등록 |
| `meter` | 계기 | 신규 (보류) | `flwmtr` 등 합성어 재료 — 단, 단일 instrument 채택으로 자식 테이블명에만 사용. Round 3 자식 전용 컬럼 보류로 등록 보류 |
| `corr` | 보정/수정 | 신규 | `corr_val` 컬럼 조합 재료 (correction). Round 3 추가 — rawdata_1m_h 수정값 컬럼 |
| `raw` | 원본 (단어로) | 신규 | `raw_val` 컬럼 조합 재료. 비즈니스 도메인 약어 `raw` ([`domain-abbreviations.md`](../../../../../.claude/rules/dict/domain-abbreviations.md)) 와 다른 층위 (단어 vs 비즈니스 도메인). Round 3 추가 |
| `srl` | 시리얼 | 신규 | `tag_srl_no` 컬럼 조합 재료. swtp 4-5자 약어 컨벤션(`prsr`·`flwrt`·`elpwr`) 정합 — 풀네임 `serial` 대신 약어 채택 (사용자 결정, Round 3) |
| `no` | 번호 | 신규 | `tag_srl_no` 컬럼 조합 재료 (number 약어). `seq`(순번 BIGINT) 와 의미 분리 — `no` 는 자연키, `seq` 는 자동 증가. Round 3 추가 |
| `main` | 주요/주된 | 신규 | `main_yn` 컬럼 조합 재료. Round 3 추가 — 시설 주요시설여부 컬럼 |
| `io` | 입출력 | 신규 | `io_yn`·`io_cd` 컬럼 조합 재료 (input/output 약어). Round 3 추가 |
| `parent` | 상위/부모 | 신규 | `parent_facility_id` 컬럼 조합 재료. self-FK 패턴 |
| `disp` | 표시 | 신규 | `disp_ord` 컬럼 조합 재료 (display 약어). Round 3 사용자 결정 (2026-05-03) — `sort` 신규 등록 vs `ord` 단독 vs `disp_ord` 중 `disp_ord` 채택. 화면 표시 순서 의미 |

### 신규 표준 데이터 도메인
(값 형식 — ROOT `swtp/.claude/rules/dict/standard-data-domains.md`. **wtp-dba-reviewer 2차 승인 필수**)

| 도메인 코드 | SQL 타입 | Java 타입 | NULL | 분류 | 결정 근거 |
|-----------|---------|---------|------|------|----------|
| `DOM_TAG_SRL_NO_50` | VARCHAR(50) | String | NOT NULL | 신규 | Round 3 사용자 결정 (2026-05-03) — `tag_srl_no` PK 자연키 전용 도메인. 706-FRI-xxx-xxx 형식이 SCADA 태그명과 동일 패턴이나 시리얼번호 의미 분리를 위해 신규 등록. DBA 2차 승인 ANALYZE 단계에서 결정 처리 |

기존 데이터 도메인 재사용: DOM_CODE_20·DOM_ID_36·DOM_QTY_15_4·DOM_DTM·DOM_TEXT·DOM_YN·DOM_SEQ_BIGINT·DOM_NAME_100·DOM_TAG_NM_50.

> Round 3 정정: 시설·계측기 PK 도메인 = `DOM_ID_36` (UUID, `entity-patterns.md` 기본 엔티티 패턴 — `@GeneratedValue(GenerationType.UUID)` 자동 생성). Round 1·2 의 `DOM_ID_50` (외부 할당) 폐기.

### 신규 표준 용어
(DB 컬럼명 — backend `.claude/rules/dict/standard-terms.md`)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `facility_id` | `facility`(비즈니스 도메인) + `id` | DOM_ID_36 | 신규 | 시설 단일 마스터 PK — UUID 자동 생성 (Round 3 정정) |
| `facility_nm` | `facility` + `nm` | DOM_NAME_100 | 신규 | 시설 명칭 — **UNIQUE 인덱스** (Round 3 사용자 결정, 시스템 전체 unique) |
| `facility_type_cd` | `facility` + `type` + `cd` | DOM_CODE_20 | 신규 | DiscriminatorColumn (PWTF/DWT/RSV/...) |
| `parent_facility_id` | `parent` + `facility` + `id` | DOM_ID_36 | 신규 | self-FK (재귀 부모 참조), NULL 허용 |
| `disp_ord` | `disp` + `ord` | INTEGER | 신규 | 표시 순서 — 시설·계측기 공통. `disp` 신규 단어 등록 + `ord` 기존 표준 단어 조합 (Round 3 사용자 결정, 2026-05-03) |
| `main_yn` | `main` + `yn` | DOM_YN | 신규 | 주요시설여부 (Round 3 추가) |
| `instrument_id` | `instrument`(비즈니스 도메인) + `id` | DOM_ID_36 | 신규 | 계측기 단일 마스터 PK — UUID 자동 생성 |
| `instrument_nm` | `instrument` + `nm` | DOM_NAME_100 | 신규 | 계측기 명칭 — **UNIQUE 인덱스** (범위 = 시스템 전체 vs `(facility_id, instrument_nm)` 복합, 가정 섹션) |
| `equip_type_cd` | `equip` + `type` + `cd` | DOM_CODE_20 | 신규 | DiscriminatorColumn (PUMP/VALVE/FLWMTR/...) |
| `range_min` | `range` + `min` | DOM_QTY_15_4 | 신규 (보류) | 측정 범위 최소값 — Round 3 자식 전용 컬럼 보류로 PLAN 단계 도입 시 등록 |
| `range_max` | `range` + `max` | DOM_QTY_15_4 | 신규 (보류) | 측정 범위 최대값 — 동일 |
| `unit_cd` | `unit` + `cd` | DOM_CODE_20 | 신규 | 측정 단위 코드 — **tag_m 위치** (Round 3 정정 — 계측기 자식 → tag_m) |
| `io_yn` 또는 `io_cd` | `io` + `yn` 또는 `io` + `cd` | DOM_YN 또는 DOM_CODE_20 | 신규 | tag_m 입출력여부 — 의미 결정 미해결 (가정 섹션) |
| `tag_srl_no` | `tag`(비즈니스 도메인) + `srl` + `no` | DOM_TAG_SRL_NO_50 (신규 등록) | 신규 | 태그 마스터 PK (자연키, 외부 할당, `Persistable<String>`). Round 3 — `tag_id` 폐기. 데이터 도메인 = DOM_TAG_SRL_NO_50 신규 (Round 3 사용자 결정, 2026-05-03) |
| `raw_val` | `raw`(단어) + `val` | DOM_QTY_15_4 | 신규 | rawdata SCADA 원본 측정값. Round 3 — `tag_val` 분리 |
| `corr_val` | `corr` + `val` | DOM_QTY_15_4 | 신규 | rawdata 보정/수정값, NULL 허용. Round 3 추가 |
| `rawdata_id` | `raw`(또는 `rawdata` 풀네임) + `id` | DOM_SEQ_BIGINT | 신규 | 시계열 BIGINT PK |
| `quality_cd` | `quality` + `cd` | DOM_CODE_20 | 신규 | SCADA QUALITY 코드 (GOOD/BAD/UNCERTAIN) |

폐기 표준 용어 (V1 폐기 + V6 재설계로 마이그레이션):
- `pwtf_id`·`pwtf_nm` → `facility_id`·`facility_nm` (type=PWTF 자식 테이블 컬럼명은 부모 PK 동일)
- `dwt_id`·`dwt_nm`·`min_req_prsr` → `facility_id`·`facility_nm` (PK 부모 동일) + `min_req_prsr` 는 `dwt_m` 자식 테이블 컬럼 (Round 3 — 자식 전용 컬럼 보류로 PLAN 단계 결정)
- `pump_id`·`pump_nm` → `instrument_id`·`instrument_nm` (PK 부모 동일) + `rated_head`·`rated_flwrt`·`tag_nm` 은 `pump_m` 자식 테이블 컬럼 (Round 3 — 동일 보류)
- `pump_cmbn_*`·`pump_interlock_*`·`pump_ctrl_*`·`pump_predc_*` 등 — `pump_id` FK 컬럼은 `instrument_id` 로 변경 (자식 테이블 PK 참조)
- **`tag_id` (Round 1·2 결정) → `tag_srl_no` 자연키 PK 변경** (Round 3)
- **`tag_val` (Round 1·2 결정) → `raw_val` + `corr_val` 분리** (Round 3)

## 신규 엔티티/DB 컬럼

### 엔티티 (Java) — Round 3 정정

- `Facility` (`@Entity`, `@Inheritance(JOINED)`, `@DiscriminatorColumn(facility_type_cd)`, `BaseEntity` 상속, **UUID 자동 생성 PK** — Persistable 미구현)
  - 컬럼: `facility_id`(PK, DOM_ID_36), `facility_nm`(DOM_NAME_100, **UNIQUE**), `facility_type_cd`(DOM_CODE_20), `parent_facility_id`(self-FK, NULL), `disp_ord`(INTEGER), `main_yn`(DOM_YN), `use_yn`(DOM_YN), BaseEntity 4
  - `PurifiedWaterTank extends Facility` (`@DiscriminatorValue("PWTF")`, `@Table(name="pwtf_m")`) — **자식 전용 컬럼 보류** (Round 3)
  - `DistributionWaterTank extends Facility` (`@DiscriminatorValue("DWT")`, `@Table(name="dwt_m")`) — 동일 보류
  - `Reservoir extends Facility` (`@DiscriminatorValue("RSV")`, `@Table(name="rsv_m")`) — 동일 보류
- `Instrument` (`@Entity`, `@Inheritance(JOINED)`, `@DiscriminatorColumn(equip_type_cd)`, `BaseEntity` 상속, **UUID 자동 생성 PK**)
  - 컬럼: `instrument_id`(PK, DOM_ID_36), `instrument_nm`(DOM_NAME_100, **UNIQUE 범위 PLAN 결정**), `equip_type_cd`(DOM_CODE_20), `facility_id`(FK to facility_m, DOM_ID_36, NOT NULL), `disp_ord`(INTEGER), `use_yn`(DOM_YN), BaseEntity 4
  - `Pump`·`Valve`·`FlowMeter`·`PressureMeter`·`LevelMeter`·`PowerMeter extends Instrument` (각 자식 테이블 6개) — **자식 전용 컬럼 보류** (Round 3)
- `Tag` (`@Entity`, `@Table(name="tag_m")`, `BaseEntity` 상속, **`Persistable<String>` 구현** — 외부 할당 PK 자연키)
  - 컬럼: `tag_srl_no`(PK 자연키, DOM_TAG_NM_50 또는 DOM_TAG_SRL_NO_50), `instrument_id`(FK to instrument_m, NOT NULL — Round 3 사용자 결정), `tag_se_cd`(DOM_CODE_20), `tag_desc`(DOM_TEXT), `unit_cd`(DOM_CODE_20), `io_yn`/`io_cd`(DOM_YN/DOM_CODE_20), BaseEntity 4
- `RawData` (`@Entity`, `@Table(name="rawdata_1m_h")`, **`BaseEntity` 상속** — Round 3 정정으로 immutable 이력 패턴 폐기, AuditingEntityListener 자동 주입)
  - 컬럼: `rawdata_id`(PK, DOM_SEQ_BIGINT, SEQUENCE allocationSize=100), `acq_dtm`(파티션 키, DOM_DTM, NOT NULL), `tag_srl_no`(논리 참조, DOM_TAG_NM_50), `raw_val`(DOM_QTY_15_4), `corr_val`(DOM_QTY_15_4, NULL 허용), `quality_cd`(DOM_CODE_20), BaseEntity 4

### DB 테이블
- 부모 테이블: `facility_m`, `instrument_m` (2건)
- 시설 자식 테이블: `pwtf_m`, `dwt_m`, `rsv_m` (3건, JOINED 자식)
- 계측기 자식 테이블: `pump_m`, `valve_m`, `flwmtr_m`, `prsmtr_m`, `lvmtr_m`, `elcmtr_m` (6건, JOINED 자식)
- 태그 마스터: `tag_m` (1건)
- 시계열 이력: `rawdata_1m_h` (1건, 월 RANGE 파티션)
- 합계: **13개 신규/재구성 테이블**

### 패키지 구조 (com.mo.swtp.*)
- `com.mo.swtp.facility.domain.{Facility, PurifiedWaterTank, DistributionWaterTank, Reservoir}`
- `com.mo.swtp.instrument.domain.{Instrument, Pump, Valve, FlowMeter, PressureMeter, LevelMeter, PowerMeter}`
- `com.mo.swtp.tag.domain.Tag`
- `com.mo.swtp.raw.domain.RawData` (또는 `com.mo.swtp.rawdata.*`)

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 권장 해소책 |
|---------|----------|
| `pwtf`·`dwt`·`rsv` 비즈니스 도메인 약어 (도입 예정) | `facility` 비즈니스 도메인 약어로 통합. 폐기 이력 등록 |
| `pump` 비즈니스 도메인 약어 (마스터 도메인 등록) | `instrument` 로 통합 + `equip_type_cd='PUMP'` 자식. 폐기 이력 등록 |
| `pump_id`·`pump_nm`·`pwtf_id`·`pwtf_nm`·`dwt_id`·`dwt_nm` 표준 용어 | 폐기 (PK 는 부모 테이블 단일) |
| `pump_*` FK 컬럼 (`pump_cmbn_d.pump_id`·`pump_interlock_p.pump_id` 등) | `instrument_id` 로 변경 (Pump 자식 테이블 PK = 부모 instrument_id) |
| `rawdata_m` vs `rawdata_1m_h` 룰 SSOT | `rawdata_1m_h` 채택. partitioning-and-retention.md·indexing-and-migration.md·ot-integration.md 모두 정렬 |
| `com.mo.swtp.pump` 패키지 (정수지·배수지·펌프 잠정 귀속) | `com.mo.swtp.facility`·`com.mo.swtp.instrument` 로 분리 이관 |
| **외부 할당 PK + `Persistable<String>`** (Round 1·2 결정) | **UUID 자동 생성 PK** (Round 3 정정) — `Persistable` 미구현, `entity-patterns.md` 기본 엔티티 패턴 적용. tag_m 만 외부 할당 PK 유지 (자연키 PK) |
| `tag_id`·`tag_val` 표준 용어 (Round 1·2 결정) | `tag_srl_no` 자연키 PK + `raw_val`·`corr_val` 분리 (Round 3) |
| RawData immutable 이력 패턴 (Round 1·2 결정) | **BaseEntity 4 상속** (Round 3 정정) — `corr_val` 갱신 시점 추적 위해 mutable 정책. 단, `tag_srl_no`·`acq_dtm`·`raw_val` 은 INSERT 후 변경 금지 (애플리케이션 레벨) |

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안 (Round 3 정정)
- 시설 단일 마스터 (Facility) + JPA JOINED + self-FK + 자식 3종 (PWTF/DWT/RSV) — **UUID 자동 생성 PK** + `facility_nm` UNIQUE + `disp_ord`·`main_yn` 컬럼 추가
- 계측기 단일 마스터 (Instrument) + JPA JOINED + facility_id FK + 자식 6종 (PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR) — **UUID 자동 생성 PK** + `instrument_nm` UNIQUE + `disp_ord` 컬럼 추가
- Tag 마스터 (instrument_id FK 1:N) — PK = **`tag_srl_no` 자연키** (외부 할당, Persistable<String>) + `unit_cd`·`io_yn`/`io_cd` tag_m 컬럼
- RawData 시계열 (월 RANGE 파티션 + BRIN 인덱스, 시계열 → 마스터 FK 금지 정책) — `raw_val`·`corr_val`·`quality_cd` + **BaseEntity 4 상속** (immutable 이력 패턴 폐기)
- **자식 9종 자식 전용 컬럼** (예: `dwt_m.min_req_prsr`·`pump_m.rated_head`) 은 **본 ANALYZE 시점 보류** — PLAN 단계 또는 추후 ANALYZE 에서 요구사항명세서 기반으로 결정

### DB 설계 변경 초안
- V1 DDL **폐기** (`V1__pumpcontrol_master_tables.sql`)
- V6 신규 마이그레이션:
  - V6_1: `facility_m` + 자식 3개 (`pwtf_m`·`dwt_m`·`rsv_m`)
  - V6_2: `instrument_m` + 자식 6개
  - V6_3: 기존 V1 의 `pump_cmbn_m`·`pump_cmbn_d`·`pump_interlock_p`·`ai_drvn_mod_p` 재구성 (FK `pump_id` → `instrument_id`, `pwtf_id` → `facility_id`)
  - V6_4: `tag_m`
  - V6_5: `rawdata_1m_h` (월 RANGE 파티션)
- V2~V5 영향:
  - `pump_ctrl_h.pump_id` → `instrument_id` (논리 참조 + 컬럼 재명명)
  - `pump_predc_h.pwtf_id`·`pump_id` → `facility_id`·`instrument_id`
  - `ai_drvn_mod_h.pwtf_id` → `facility_id`
  - 시퀀스 (V5) 영향 없음

### 적용할 패턴 (Round 3 정정)
- **JPA JOINED + DiscriminatorColumn 패턴** — `reference/common/.../dataset/domain/Dataset.java` 사례 적용. `entity-patterns.md` 에 신규 패턴 추가
- **UUID 자동 생성 PK 패턴** (시설·계측기 부모 + 자식 9종) — `entity-patterns.md` "기본 엔티티 패턴" 적용 + JOINED 자식 PK 자동 상속. Persistable 미구현
- **외부 할당 PK + `Persistable<String>` 패턴** — `Tag` 만 적용 (자연키 PK = `tag_srl_no`)
- **BaseEntity 상속 전체 적용** (Round 3 정정) — `RawData` 도 BaseEntity 4 상속 (mutable, `corr_val` 갱신 시점 추적). `tag_srl_no`·`acq_dtm`·`raw_val` INSERT 후 변경 금지는 애플리케이션 레벨 검증
- **이름 UNIQUE 비즈니스 식별자 패턴** (Round 3 신규) — `facility_nm`·`instrument_nm` UNIQUE 인덱스 + UUID PK 조합. 비즈니스 식별자(이름) 와 기술 식별자(UUID) 분리

### PLAN 제약 (필수 명시)
- **AI 운전 모드 관련 쿼리는 `facility_type_cd='PWTF'` 필터 강제** — Service 계층 (`AiDrivenModeService` 등) 에서 일관 적용. wtp-domain-expert 블로커 2 해소 조건
- 인터록 평가 (`InterlockValidator`) 는 `equip_type_cd='PUMP'` 또는 `'VALVE'` 액추에이터만 대상으로 필터 (현재는 펌프만)
- 시계열 → 마스터 FK 금지 정책 (`rawdata_1m_h.tag_id` 는 논리 참조)

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../.claude/rules/coding-discipline.md) 적용. **최소 1건 이상 기재 의무**.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| ~~외부 할당 PK + JPA JOINED 조합에서 `BaseEntity` 의 `@Transient newEntity` 플래그가 부모·자식 모두에서 정상 작동한다~~ | ~~가정~~ | **Round 3 무효화** — UUID 자동 생성 PK 채택으로 Persistable 미구현. tag_m 만 외부 할당 PK 유지하나 JOINED 미사용 (단일 테이블) 으로 검증 단순화 |
| 저수지 (`Reservoir`) 수위가 인터록 선행조건으로 사용될 가능성이 향후 발생할 수 있으나 현 시점 미정 | 가정 | 사용자 응답 (2026-05-02). 현 시점 자식 테이블 `rsv_m` 컬럼 없음, 향후 도입 시 자식 테이블 컬럼 확장 |
| 정수장 HMI 대시보드는 시설 종류별 화면 분리 (다형성 전체 조회 빈도 = 드물거나 없음) | 가정 | 사용자 응답 (2026-05-02). wtp-dba-reviewer 권고 — JOINED LEFT OUTER JOIN N개 핫패스 우려 해소 |
| ~~`tag_m.tag_id` PK 는 외부 할당, `tag_nm` 은 SCADA 태그명 (UNIQUE 제약)~~ | ~~결정~~ | **Round 3 변경** — `tag_id` 폐기, PK = `tag_srl_no` (태그시리얼번호 자연키). UNIQUE 제약 별도 불필요 (PK 자체가 자연키) |
| 시설·계측기 신규 종류 추가 빈도가 정수장별로 다를 수 있으며, 단일 마스터 + JPA JOINED 가 빈번 추가에 적합 | 결정 | 5인 회의 합의 (Round 2) |
| `parent_facility_id` self-FK 의 재귀 깊이 제한은 도메인 정책 미정 — 무제한 허용하되 인터록 평가 쿼리에서 self-FK 를 따라가지 않는다 | 미해결 | wtp-domain-expert Round 2 권고. PLAN 단계에서 Service 계층 구현 제약으로 명시 |
| `valve_m`·`flwmtr_m` 등 계측기 자식 테이블의 자식 전용 컬럼 (정격 압력·범위·단위 등) 의 정확한 명세는 PLAN 단계에서 결정 | **결정** (Round 3 사용자 명시) | 본 ANALYZE 는 부모 컬럼 + 자식 skeleton 만 정의. 자식 테이블별 자식 전용 컬럼은 추후 요구사항명세서 기반 PLAN 단계에서 결정 |
| `rawdata_1m_h` 의 `quality_cd` 는 ot-integration.md §3 의 GOOD/BAD/UNCERTAIN enum 매핑 (`QualityCode` enum 신규) | 가정 | PLAN 단계에서 `TagMeasurementType` enum 과 함께 정의 |
| `raw` 비즈니스 도메인 약어 vs `rawdata` 풀네임 — 본 ANALYZE 는 `raw` 채택 (도입 예정 약어 승격), 패키지명은 `com.mo.swtp.raw` | 결정 | 4자 약어 일관성 (pump·user·tag 와 동일 수준) |
| `instrument_m.instrument_nm` UNIQUE 범위: 시스템 전체 vs `(facility_id, instrument_nm)` 복합 | **미해결** (Round 3 추가) | PLAN 단계 결정 — 정수장이 여러 개일 때 "유량계1" 이름 중복 가능성 vs prefix 운영 부담 트레이드오프. wtp-domain-expert·wtp-dba-reviewer Round 4 검토 |
| 태그 `io_yn` (DOM_YN — 단순 송수신 boolean) vs `io_cd` (DOM_CODE_20 — INPUT/OUTPUT/BIDIR enum) 의미 결정 | **미해결** (Round 3 추가) | PLAN 단계 결정 — SCADA 태그가 단방향(input/output) 만 있는지 양방향(bidir) 도 있는지 도메인 검토 |
| `tag_srl_no` 도메인 = `DOM_TAG_SRL_NO_50` 신규 등록 (사용자 결정) | **결정** (Round 3, 2026-05-03) | 사용자 응답 — 시리얼번호 의미 분리 명확화. DBA 2차 승인은 ANALYZE 단계에서 결정 처리. standard-data-domains.md 에 VARCHAR(50)·String·NOT NULL 신규 등록 |
| `rawdata_1m_h` 의 BaseEntity 4 적용 시 `corr_val` 갱신 → `updt_dtm` 자동 갱신 (AuditingEntityListener) | **가정** (Round 3 추가) | partitioning-and-retention.md 시계열 → 마스터 FK 금지 정책과 충돌 없음 (FK 아닌 audit 메타 주입). 단, INSERT-only 컬럼(`tag_srl_no`·`acq_dtm`·`raw_val`) 은 애플리케이션 레벨 immutable 검증 필요 — PLAN 단계 명시 |
| `disp_ord` 컬럼 조합 = `disp` + `ord` (사용자 결정) | **결정** (Round 3, 2026-05-03) | 사용자 응답 — `disp` 신규 단어 등록 + `ord` 기존 표준 단어 조합. 표시 순서 의미 채택. standard-words.md 에 `disp` 신규 등록 + standard-terms.md 에 `disp_ord` 등록 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/domain-abbreviations.md` — `pwtf`·`dwt`·`pump` 폐기 이력 등록 (단, 본 작업 PLAN approved 후 실행) + `facility`·`instrument` 신규 비즈니스 도메인 약어 마스터 도메인 등록 + `tag`·`raw` 도입 예정 → 마스터 도메인 승격
- [x] `swtp/.claude/rules/dict/standard-words.md` — `quality`·`unit`·`equip`·`parent`·`corr`·`raw`(단어)·`srl`·`no`·`main`·`io`·`disp` 신규 표준 단어 등록. `range`·`valve`·`meter`·`sort` 는 등록 제외 (자식 전용 컬럼 보류 또는 `disp_ord` 채택, Round 3)
- [x] `swtp/.claude/rules/dict/standard-data-domains.md` — `DOM_TAG_SRL_NO_50` 신규 등록 (VARCHAR(50)·String·NOT NULL, Round 3 사용자 결정 2026-05-03)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — 폐기 컬럼 (`pwtf_*`·`dwt_*`·`pump_*` PK·NM + `tag_id`·`tag_val`) 폐기 이력 등록 + 신규 컬럼 (`facility_*`·`instrument_*`·`tag_srl_no`·`raw_val`·`corr_val`·`rawdata_id`·`equip_type_cd`·`unit_cd`·`quality_cd`·`disp_ord`·`main_yn`·`io_yn`/`io_cd`·`parent_facility_id`) 등록. `disp_ord` 조합 = `disp` + `ord` (Round 3 사용자 결정)
- [x] `swtp/backend/.claude/rules/ot-integration.md` — `rawdata_m` → `rawdata_1m_h` SSOT 정렬 (모든 등장 라인) + AI 운전 모드 facility_type_cd 필터 강제 명시 + Pump = Instrument 자식 정합성 보강 (sensor_tag 참조 패턴 영향 평가) + `tag_srl_no` 컬럼명 정렬 (rawdata 참조 컬럼)
- [x] `swtp/backend/.claude/rules/db/partitioning-and-retention.md` — `rawdata_m` → `rawdata_1m_h` SSOT 정렬 (예시 DDL 포함) + RawData BaseEntity 4 컬럼 주입 정책 명시 (Round 3 추가)
- [x] `swtp/backend/.claude/rules/db/indexing-and-migration.md` — `rawdata_m` → `rawdata_1m_h` SSOT 정렬 (BRIN/복합 인덱스 예시 포함)
- [x] `swtp/backend/.claude/rules/entity-patterns.md` — JPA JOINED + DiscriminatorColumn 패턴 신규 절 추가 (DataSet.java 사례 인용 + **UUID 자동 생성 PK + Persistable 미구현 조합** 가이드 + 자식 PK 자동 상속 + 이름 UNIQUE 비즈니스 식별자 패턴 + AI 운전 모드 facility_type_cd 필터 강제 패턴) — Round 3 정정으로 외부 할당 PK 가이드는 tag_m 만 적용
- [x] `swtp/backend/CLAUDE.md` — 패키지 규칙 절 갱신 (`com.mo.swtp.pump` 잠정 귀속 → `com.mo.swtp.facility`·`com.mo.swtp.instrument` 분리 이관 명시)

## 산출물

- [계획안](../../../plan/20260502/마스터도메인설계/PLAN1.md) (PLAN 단계 작성 예정)
