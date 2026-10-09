---
status: approved
created: 2026-05-18
updated: 2026-05-18
---
# 송수펌프제어분석 — 7번섹션 (시설 예측 데이터 표출) 도메인 분석

## 작업 배경

이미지 `swtp/backend/image/송수펌프제어분석.png` 의 **7번 섹션**(우측 "분석 결과") 구현. 한 HMI 화면에서 여러 섹션이 **공존**한다:

- **3번 섹션** = 1번 섹션에서 선택한 시설의 **현황(실시간 계측) 데이터** — 이미 구현됨 (`FacilityStateService`, `GET /api/facility/{facilityId}/state`, `rawdata_1m_h` DISTINCT ON, 송수펌프제어분석-3번섹션 ANALYZE1/PLAN1 status approved 2026-05-13)
- **7번 섹션** = 같은 시설의 **AI 예측 데이터** — 본 사이클

7번은 3번과 형태·이벤트 흐름이 동일하되 데이터 소스만 다르다(현황 → AI 예측). 사용자 명시:
- AI 예측 프로세스(파이썬 추론)는 **본 사이클 분석 제외** — 예측결과를 저장하는 시계열 태그 예측값 테이블이 존재한다고 보고 그 값으로 구현
- 예측결과를 rawdata 와 같은 성격의 **시계열 태그 예측값**으로 규정
- 표출 시나리오: 각 태그의 **현황 최신 계측시각(rawdata `acq_dtm`) + 1시간** 시점에 **근접한** 예측값

7번 섹션은 현재 backend 부재(예측 테이블 없음 — 과거 `pump_predc_h` 는 2026-05-12 pump+AI 백지화). 본 사이클 범위 = **예측 시계열 테이블 스키마(DDL+엔티티) 신설 + 섹션 3 미러링 조회 API**. AI 파이프라인 제외.

### 외부 산출물
- `swtp/backend/image/송수펌프제어분석.png` (HMI 화면 디자인 — 7번 섹션)

### 사용자 의논 결정 (2026-05-18, plan 모드 + ANALYZE 회의)
| 결정 항목 | 확정값 |
|----------|--------|
| 예측 테이블 시간 모델 | 단일 시각 — rawdata 미러링. 시각 컬럼 = 예측 **대상 시각** |
| 표출 기준 시각 산정 단위 | 태그별 개별 — 각 태그의 rawdata 최신 `acq_dtm` + 1h |
| (기준+1h) 매칭 정책 | 근접 시각 매칭 — 가장 가까운 예측행 1건 (윈도우 PLAN 확정) |
| 자산 전략 | 섹션 3 보존 + 섹션 7 신규 병렬 |
| 예측 태그 범위 | 섹션 3과 동일 — 유량계 FRI/PRI, 펌프 OPS |
| 예측 패키지 | `com.mo.swtp.opt` (glossary Round2 권고) |
| `quality_cd` | **제외** (domain-expert 권고 — SCADA 품질 ≠ 모델 출력) |
| 결측(윈도우 밖) 응답 | 200 + 해당 필드 null (섹션 3 철학) |
| 예측 OPS | 포함, `predc` 접두어 강제 + 별도 DTO |

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 예측 시계열 비즈니스 도메인 약어 / Java 패키지 배치

- 호출 에이전트: `wtp-glossary-manager` (Round 1·2), `wtp-backend-engineer` (Round 2)
- Round 1 (glossary): `predc` 비즈니스 도메인 약어 등록 **거부** — `domain-abbreviations.md` §등록 거부 이력에 2026-04-25 (pumpcontrol ANALYZE1 안건 3) "비즈니스 도메인 부적합 — `ai` 하위 개념" 기록 존재. 번복 논증 불충분. `com.mo.swtp.predc` 패키지 불가.
- Round 2 (glossary): 후보 A(`raw`)=의미 충돌(raw=SCADA 원시 수집 ≠ 예측, 동의어 금지 조항에 준함), B(`tag`)=책임 범위 초과(SRP), C(신규 약어)=`predc` 동의어 충돌, D(보류)=사용자 요구 충돌. **권고 = `com.mo.swtp.opt`** — `opt`(도입예정, "AI 최적화 결과 저장 테이블 prefix 전용") 설명을 예측 결과까지 확장. `predc` 거부 사유("ai 하위 개념") 번복 없이 우회 가능 (opt 는 ai 하위 결과저장 특화 도메인으로 이미 별도 등록 예정).
- Round 2 (backend): A(`com.mo.swtp.raw`) 권고 — feature-based·사용자 "로우데이터와 같이 시계열 태그 예측값" 정의·섹션3 무수정 정합(별도 클래스 병렬). C(서브패키지)=`coding-discipline §2` 과잉 추상화.
- 이견 — 오케스트레이터 중계: glossary(어휘 SSOT)는 `raw` 의미 위반 판정, backend 는 feature-based 우선. 어휘 SSOT 권한 + 사용자 결정 우선.
- **결론**: **`com.mo.swtp.opt`** 채택 (사용자 결정 2026-05-18). `opt` 비즈니스 도메인 설명 확장 룰 갱신 1건. 거부 이력 번복 없음. 예측 엔티티/Repository/내부 DTO → `com.mo.swtp.opt`, 응답 DTO 3종 → `com.mo.swtp.facility.dto` (섹션 3 선례).

### 안건 2: 예측 테이블 컬럼 표준 용어 (재등록/신규) + `val` 표준 단어 공백

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - `predc_id`·`predc_dtm` 은 2026-05-12 pump+AI 백지화로 폐기 이력 존재(폐기 이력에 "재등록 시 재사용 가능" 명시). 신규 의미로 재등록 — `predc_id`=`predc_1m_h` 시계열 BIGINT PK, `predc_dtm`=단일 예측 대상 시각(파티션 키, 구 "기준+1h 오프셋" 의미와 분리).
  - `predc_val` 신규 = `predc`(표준 단어 기등록) + `val`(신규).
  - `val`(값/value) 표준 단어 **미등록 공백** — `raw_val`·`corr_val`·`tag_val` 사용 중이나 `standard-words.md` 부재. `acq` 공백 해소(2026-05-13, 송수펌프제어분석-3번섹션 안건 11) 선례 동형으로 `predc_val` 사용 전제 등록 의무. `qty`(수량)와 의미 분리(`val`=측정값 인스턴스 단어).
- **결론**: `val` 표준 단어 신규 등록. `predc_id`·`predc_dtm` 폐기 후 재등록, `predc_val` 신규 등록. 신규 표준 데이터 도메인 0건(전부 기존 재사용).

### 안건 3: 예측 테이블 DDL·인덱스·근접매칭 쿼리 2차 승인

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약 (블로커 2 + 권고 2 + 참고 2):
  - **블로커**: 복합 인덱스는 `(tag_srl_no, predc_dtm **ASC**)` 필수 — 근접매칭은 `BETWEEN target±W` 후 `ORDER BY ABS(predc_dtm - target) LIMIT 1` 의 **대칭 범위 스캔**. rawdata 의 `(tag_srl_no, acq_dtm DESC)`("최신 1건" 단방향) 선례와 목적이 다름. DESC 시 forward scan 포기.
  - **블로커**: 근접매칭 전략은 **`CROSS JOIN LATERAL`** 채택 (Window 함수는 N×윈도우 행 정렬 비용, DISTINCT ON 은 절댓값 정렬 직접 적용 불가). `enable_partition_pruning=on` + execution-time pruning 확인 의무.
  - **권고**: 근접 윈도우 크기 — 예측 그리드 1분 가정 ±5분, 1시간 가정 ±30분. 윈도우 과대 시 파티션 경계 교차 + 잘못된 시간대 매칭 위험. 설정값 관리.
  - **권고**: SLA 200ms / `executionThreshold=100ms` (섹션 3 선례) — N=100 LATERAL 10~50ms 추정. 윈도우 과대 시 500ms 초과 위험. 실측 필수.
  - **참고**: BaseEntity4 — 예측은 INSERT-only immutable(rawdata 의 corr_val 갱신과 다름) → `indexing-and-migration.md §4.3` immutable 이력 예외(`rgstr_dtm`·`rgstr_id` 만, `updt_*` 데드 컬럼 회피)가 적합.
  - **참고**: 보존 — AI 예측 결과 선례(`opt_result_h`·`pump_predc_h`) 3년. 권고 3년, 파티션 DROP. `partitioning-and-retention.md §2` 신규 행.
  - **참고**: BRIN(`predc_dtm`)은 복합 B-Tree 후위 컬럼과 역할 중복 가능 — `predc_dtm` 단독 대용량 범위 시나리오 없으면 생략 가능. PLAN 위임.
- **결론**: 인덱스 `(tag_srl_no, predc_dtm ASC)` ASC 강제. LATERAL 채택 + `enable_partition_pruning` 확인. BaseEntity4 대신 immutable 이력 예외(`rgstr_dtm`·`rgstr_id`). 보존 3년/파티션 DROP. BRIN 생략 검토 PLAN. 윈도우 크기·SLA 실측 PLAN 변환.

### 안건 4: API 계층 설계 (섹션 3 미러 병렬) + 공유 마스터 Repository 재사용 경계

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약 (블로커 1 + 권고 2 + 참고 2):
  - 공유 마스터 Repository 재사용: `FacilityRepository.findById` = 공용, 사용자 확인 불요. `InstrumentRepository.findByFacilityIdAndEquipType`·`TagRepository.findByInstrumentInstrumentIdInAndUseYn` = 메서드 재사용 가능하되 섹션 3 전용 상수(`STATE_EQUIP_TYPES`/태그 유형) 공유 시 섹션 3 변경이 섹션 7에 전파 → **섹션 7 전용 상수 별도 선언**(메모리 `feedback_no_auto_reuse_cross_cycle` 정합).
  - DTO: `BaseAuditResponseDto` 미상속 타당(실시간/예측 통지성, 섹션 3 선례). `@Schema(implementation=)` enum 필수, `predcDtm` `@JsonFormat` 필수.
  - **블로커**: 근접매칭 native 쿼리 §2.5 면책 — Java 주석 `// §2.5 면책 (query-tuning.md §2)` 명기 의무(누락 시 REVIEW 블로커).
  - 4-step 골격: `FacilityPredictionService` **별도 클래스 독립 유지** — 섹션 3 과 공통 추상화 도입은 `coding-discipline §2` "요청되지 않은 추상화 금지" 위반. 본문 50줄 초과 시 `mapFlwmtrPrediction`·`mapPumpPrediction` private 분해(섹션 3 선례).
  - 컨트롤러: `FacilityController` 추가(단일 진입점 원칙). `/prediction` 단수형 = `/state` 선례 정합.
- **결론**: `FacilityPredictionService` 별도 클래스. 섹션 7 전용 상수(`PREDICTION_EQUIP_TYPES`·`PREDICTION_TAG_TYPES`) 별도 선언. §2.5 인용 주석 의무. `@Schema(implementation=)`·`@JsonFormat` 의무. `FacilityController` 에 `GET /api/facility/{facilityId}/prediction` 추가.

### 안건 5: 도메인 4영역 점검 + 예측 표출 안전성

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약 (블로커 2 + 권고 1):
  - 4영역 **전부 비해당** — 예측 테이블 조회·표출 전용. 알람: 섹션 3 "해당" 근거는 `quality_cd` 노출의 운전원 오인 방지 의무였으나 예측은 SCADA 수신 품질 판정 경로 자체가 없음. 인터록/AI모드/이력: 제어·모드·이력 경로 무접촉. `Pump.oprtngType` 은 정적 제원(운전 모드 무관).
  - **권고**: 예측 테이블 `quality_cd` **제외** — SCADA 품질(센서·통신 장애 의미)을 모델 출력에 부여하면 §3 의미 충돌·운전원 오독. 예측 신뢰도는 별개 개념 — 사이클 2 결정 대기.
  - **블로커**: 예측 OPS 표출 위험 — §3 OPS "즉시 BAD 격상, Hold Last Value 미적용"(통신 단절 시 운전원 오인 방지)과 동급 위험. 예측 on/off 를 현황으로 오인하면 동일 위험. 예측 OPS 필드 **`predc` 접두어 강제** + `@Schema` "예측 가동상태(미래 시점)" 명시 + **현황 DTO 재사용 금지**(별도 응답 DTO).
  - **블로커**: 근접매칭 윈도우·결측 정책 미결 — 가정 섹션 미해결 기재 의무.
- 이견 — 오케스트레이터 중계: `quality_cd` 제외는 사용자 원안("rawdata처럼")과 차이 → 사용자 결정 요청 → **제외 확정**(사용자 2026-05-18).
- **결론**: 4영역 전부 비해당(quality_cd 제외로 알람 "해당" 근거 소멸). `quality_cd` 컬럼 제외. 예측 OPS 포함하되 `predc` 접두어 강제·별도 DTO·`@Schema` 명시. 윈도우/결측 정책 가정 섹션 기재.

---

## 표준 사전 카탈로그

### 신규 표준 단어
(1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `val` | 값 | 신규 | `standard-words.md` 미등록 공백. `raw_val`·`corr_val`·`tag_val`·`predc_val` 조합 재료로 기/신규 사용. `acq` 공백 해소(2026-05-13) 선례 동형. `qty`(수량)와 의미 분리 — `val`=측정값 인스턴스 단어 |

### 신규 표준 데이터 도메인

없음 (전부 기존 재사용 — `predc_val`=`DOM_QTY_15_4`, `predc_dtm`=`DOM_DTM`, `predc_id`=`DOM_SEQ_BIGINT`. DBA 2차 승인 대기 0건).

### 신규 표준 용어
(1차 정의: `.claude/rules/dict/standard-terms.md` — backend DB 컬럼)

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `predc_id` | `predc`(표준 단어) + `id` | `DOM_SEQ_BIGINT` | 폐기 후 재등록 | 구 `pump_predc_h` PK 의미 소멸(2026-05-12). 신규 의미: `predc_1m_h` 시계열 BIGINT PK (`GenerationType.SEQUENCE` allocationSize=100) |
| `predc_dtm` | `predc`(표준 단어) + `dtm` | `DOM_DTM` | 폐기 후 재등록 | 구 의미(기준+1h 오프셋 예측 대상 일시) 소멸. 신규 의미: `predc_1m_h` 단일 예측 대상 시각, 월 RANGE 파티션 키 |
| `predc_val` | `predc`(표준 단어) + `val`(신규) | `DOM_QTY_15_4` | 신규 | 예측 측정값. `raw_val`·`corr_val` 패턴 동형. NULL 허용. 사용 테이블 `predc_1m_h`. 의미 중복 없음 |

> `quality_cd` 는 예측 테이블 **제외**(안건 5) — 표준 용어 신규 등록 없음(rawdata 기등록 컬럼, 예측 미사용).

분류값 (3층 공통): 신규 / 기존 재사용 / 유사 충돌 / 폐기·통합

---

## 신규 엔티티/DB 컬럼

**신규 테이블 1건** — `predc_1m_h` (예측 시계열, `com.mo.swtp.opt` 비즈니스 도메인)

| 컬럼 | 타입 | NULL | 데이터 도메인 | 비고 |
|------|------|------|-------------|------|
| `predc_id` | BIGINT | NOT NULL | `DOM_SEQ_BIGINT` | PK 1/2. `seq_predc_id` SEQUENCE allocationSize=100 |
| `predc_dtm` | TIMESTAMP | NOT NULL | `DOM_DTM` | PK 2/2. 예측 대상 시각. 월 RANGE 파티션 키 |
| `tag_srl_no` | VARCHAR(50) | NOT NULL | `DOM_TAG_SRL_NO_50` | `tag_m` 논리 참조 (시계열 → 마스터 FK 금지) |
| `predc_val` | NUMERIC(15,4) | NULL 허용 | `DOM_QTY_15_4` | 예측값 |
| `rgstr_dtm` | TIMESTAMP | NOT NULL | `DOM_DTM` | immutable 이력 예외 (`indexing-and-migration.md §4.3`) — `updt_*` 미정의 |
| `rgstr_id` | VARCHAR(50) | NOT NULL | `DOM_ID_50` | immutable 이력 예외 — INSERT-only |

- 비즈니스 도메인: `com.mo.swtp.opt` (안건 1). 엔티티/복합키/CustomRepository/내부 전송 DTO 배치. 응답 DTO 3종은 `com.mo.swtp.facility.dto` (섹션 3 선례)
- 인덱스: `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm ASC)` — 근접 대칭 범위 스캔 (안건 3 블로커). BRIN(`predc_dtm`) 생략 검토 PLAN
- 파티션: 월 RANGE (`predc_dtm`), 6개월 선행. DDL: `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` (현 최신 V9_2 다음)
- BaseEntity4 미적용 — INSERT-only immutable 이력 예외(`rgstr_dtm`·`rgstr_id` 만, `updt_*` 데드 컬럼 회피)
- `quality_cd` 미포함 (안건 5)
- COMMENT ON COLUMN 의무 (`check-ddl-column-comment.sh` 훅) — immutable 이력 표준 라벨(`indexing-and-migration.md §4.3`)

신규 응답 DTO 3건 (`com.mo.swtp.facility.dto`):
- `FacilityPredictionDto` — `facilityId`·`facilityNm`·`List<FlwmtrPredictionDto>`·`List<PumpPredictionDto>`
- `FlwmtrPredictionDto` — FRI/PRI 예측 분리 필드 (`flwrtPredcVal`·`flwrtPredcDtm`·`prsrPredcVal`·`prsrPredcDtm`). `quality*` 없음
- `PumpPredictionDto` — `instrumentId`·`instrumentNm`·`oprtngType`(정적, "AI 운전 모드와 무관")·`predcIsRunning`(예측 OPS, `predc` 접두어)·`predcDtm`

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 해소책 (회의 결론과 일치) |
|---------|----------------------|
| `predc` 비즈니스 도메인 약어 거부 이력(2026-04-25) — `com.mo.swtp.predc` 불가 | `com.mo.swtp.opt` 배치 + `opt` 설명 확장(거부 번복 없음, 안건 1·사용자 결정) |
| `val` 표준 단어 ROOT 사전 미등록 공백 — `raw_val`·`corr_val`·`tag_val` 어휘 근거 없이 사용 중 | 본 사이클 룰 갱신으로 `val` 단어 등록 (`acq` 선례 동형, 안건 2) |
| `predc_id`·`predc_dtm` 폐기 이력 존재 — 재등록 시 의미 명확화 필요 | 신규 의미(시계열 PK / 단일 예측 대상 시각)로 비고 갱신 후 재등록 (안건 2) |
| 예측 `quality_cd` 부여 시 §3 SCADA 품질 의미 충돌·운전원 오독 | `quality_cd` 컬럼 제외 — 예측 신뢰도는 사이클 2 결정 (안건 5·사용자 결정) |
| 예측 OPS on/off 를 현황으로 오인 위험 (§3 OPS 정책 동급) | 예측 OPS 필드 `predc` 접두어 강제 + `@Schema` "예측 가동상태(미래 시점)" + 현황 DTO 재사용 금지 (안건 5) |
| 섹션 3 전용 상수 공유 시 크로스사이클 자동 원용 | 섹션 7 전용 `PREDICTION_EQUIP_TYPES`·`PREDICTION_TAG_TYPES` 별도 선언 (안건 4) |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안
- 신규 엔티티 1건: 예측 시계열 엔티티 (`com.mo.swtp.opt.domain`, 테이블 `predc_1m_h`, 클래스명·복합키명 PLAN 확정 — 후보 `TagPrediction`/`TagPredictionId`) — BaseEntity 미상속, immutable 이력 예외(`rgstr_dtm`·`rgstr_id` `@CreatedDate`·`@CreatedBy`)
- 신규 응답 DTO 3건: `FacilityPredictionDto`·`FlwmtrPredictionDto`·`PumpPredictionDto` (`com.mo.swtp.facility.dto`, `BaseAuditResponseDto` 미적용)
- 섹션 3 전용 자산(`FacilityStateService`·`RawDataCustomRepository`·State DTO 3종) 무수정 — 신규 병렬

### DB 설계 변경 초안
- 신규 테이블 `predc_1m_h` (월 RANGE 파티션, `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm ASC)`, 6개월 선행, COMMENT 의무). DDL `V9_3__predc_1m_h.sql`
- `partitioning-and-retention.md §2` 보존 기간 표 신규 행: 예측 시계열(`predc_1m_h`) 3년 / 파티션 DROP
- `quality_cd` 제외, BaseEntity4 미적용(immutable 이력 예외)

### 적용할 패턴
- 근접매칭 native 쿼리: `latest_meas` CTE(rawdata 태그별 최신 `acq_dtm` DISTINCT ON) + `CROSS JOIN LATERAL`(`predc_dtm BETWEEN (acq_dtm+1h)±윈도우`, `ORDER BY ABS(...) LIMIT 1`). `EntityManager.createNativeQuery` + `@Transactional(readOnly=true)` + `// §2.5 면책 (query-tuning.md §2)` 주석 의무
- `FacilityPredictionService` 별도 클래스 4-step (Facility→Instrument[PUMP,FLWMTR]→Tag[FRI,PRI,OPS]→예측 근접매칭). 50줄 초과 시 `mapFlwmtrPrediction`·`mapPumpPrediction` 분해
- `FacilityController` 에 `GET /api/facility/{facilityId}/prediction` (단수형, `@Operation` 에 "송수펌프제어분석 7번 섹션" 명기). `@ApiResponses` 200/400/401/403/404/500
- 응답 DTO: `@Getter`+private 생성자+정적 팩토리, `oprtngType` `@Schema(implementation=PumpOprtngType.class)`, `predcDtm` `@JsonFormat(shape=STRING, pattern="yyyy-MM-dd HH:mm:ss")`, 예측 OPS `predcIsRunning` `@Schema(description="예측 가동상태(미래 시점)")`
- 결측(윈도우 밖) → 200 + 해당 필드 null (섹션 3 철학)
- 섹션 7 전용 상수 `PREDICTION_EQUIP_TYPES = [PUMP, FLWMTR]` · `PREDICTION_TAG_TYPES = [FRI, PRI, OPS]` 별도 선언

---

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. 최소 1건 이상 기재 의무.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 근접매칭 윈도우 크기 — 예측 그리드 단위(1분/시간) 미상(AI 프로세스 본 사이클 제외) | 미해결 | PLAN 결정: dba 권고 기본값(시간 그리드 가정 ±30분, 1분 가정 ±5분), 설정값 관리(하드코딩 금지). 윈도우 과대 시 잘못된 시간대 매칭 + 파티션 경계 교차 위험 |
| 윈도우 내 예측행 결측 시 응답 | 결정 | 200 + 해당 필드 null (섹션 3 철학, 사용자 결정 2026-05-18). frontend 가 null 보고 표시 분기 |
| 예측 신뢰도/유효성 컬럼 도입 | 미해결 | 본 사이클 미포함 — `quality_cd` 제외 결정(안건 5). 예측 신뢰도는 별개 개념, 사이클 2(AI 추론 재설계) 결정 대기 |
| 예측 OPS 필드명 — `predcIsRunning` vs `predcOprtngYn` | 가정 | 가정: `predcIsRunning`(섹션 3 `isRunning` 미러 + `predc` 접두어 정합). PLAN 확정. `predc` 접두어·별도 DTO·`@Schema` "예측 가동상태(미래 시점)"는 결정(안건 5 블로커 해소) |
| 예측 엔티티 클래스명·복합키명 | 가정 | 가정: `TagPrediction`·`TagPredictionId` (`com.mo.swtp.opt.domain`). PLAN 확정 |
| BRIN(`predc_dtm`) 인덱스 도입 여부 | 미해결 | 복합 B-Tree 후위 컬럼과 역할 중복 가능. `predc_dtm` 단독 대용량 범위 시나리오(파티션 스케줄러 외) 없으면 생략. PLAN 결정 |
| 활성 태그 N=500 시 SLA 200ms 미달 가능성 | 미해결 | 섹션 3 동일 미해결. 본 사이클 N=100 fixture 측정 한정. 운영 측정 후 캐시 별도 사이클 |
| 신규 엔티티 존재하나 4영역 무접촉 | 가정 | `predc_1m_h` 신규 테이블이나 표출 전용·SCADA 품질 경로 없음·제어/모드/이력 무접촉(domain-expert Round1 판정). "비해당 단독 4건"이 형식적 충족이 아님을 §도메인 룰 4영역 점검 각 행 구체 사유로 명시 |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 근접매칭 쿼리가 인덱스 활용 + 파티션 프루닝 정상 | 로컬 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` 결과 (1) `predc_1m_h` 에서 `Index Scan using idx_predc_1m_h_tag_time`, (2) `latest_meas` CTE 가 `idx_rawdata_1m_h_tag_time` 활용, (3) `Append` 하위 예측 파티션 1~2개(`Subplans Removed` 확인), (4) `enable_partition_pruning=on` 확인 |
| N=100 태그 fixture 응답 200ms 이내 | `./gradlew.bat :api:test` 통합 테스트 예측+rawdata fixture INSERT 후 `FacilityPredictionService.findFacilityPrediction(facilityId)` 응답 시간 측정 |
| 근접매칭 — 정확/근접/윈도우 밖(null) 케이스 | 단위 테스트 — 근접 Repository mock 으로 정확 일치·근접·윈도우 밖(null) 행 주입 시 응답 DTO 매핑 검증 |
| 예측 OPS `predcIsRunning` + `@Schema` "예측 가동상태(미래 시점)" 노출 | Swagger UI 에서 `PumpPredictionDto.predcIsRunning` description + `oprtngType` enum 노출 확인 |
| 신규 엔드포인트 `CommonResponseDto<FacilityPredictionDto>` + `code:"SUCCESS"` | `curl GET /api/facility/{facilityId}/prediction` 응답 `code=="SUCCESS"` + `data.facilityId` 일치 |

---

## 도메인 룰 4영역 점검

> 인용 근거: [`backend/.claude/rules/ot-integration.md`](../../../../.claude/rules/ot-integration.md). domain-expert Round1 판정 — 각 행 구체 사유 명기 ("비해당 단독 4건"이 형식적 충족 아님: `quality_cd` 제외 결정으로 섹션 3 "알람 해당" 근거 소멸, 신규 테이블이나 표출 전용·4영역 무접촉).

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5·§3`) | **비해당** | 섹션 3 "해당" 근거는 `quality_cd`(GOOD/BAD/UNCERTAIN) 노출의 운전원 오인 방지 의무(§3)였다. 본 사이클은 `quality_cd` 컬럼 **제외**(안건 5·사용자 결정)로 SCADA 품질 노출 경로 자체가 없음 → 알람 해당 근거 소멸. 예측 표출은 SCADA 수신 품질 판정 파이프라인 무접촉. 알람 임계값·전이·`alarm_h` 저장 경로 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | **비해당** | 표출 전용 — 제어 명령(`sendControlCommand`) 발행 없음. `pump_interlock_p` 평가·기동 차단·복구 후 재검사 무접촉 |
| AI 운전 모드 (`ot-integration.md §5`) | **비해당** | 예측 시계열 단순 SELECT. `ai_drvn_mod_p`/`ai_mode_cd` 참조·변경·강제 전환 트리거 없음(§5 보류 마커 상태). `Pump.oprtngType`(`oprtng_type_cd`)은 정적 물리 제원으로 운전 모드 전환과 별개 — `@Schema` "AI 운전 모드와 무관" 명시 |
| 이력 기록 의무 (`ot-integration.md §5`) | **비해당** | 표출 전용 SELECT. 모드 전환 이력(`ai_drvn_mod_h.transition_reason`)·제어 로그(`pump_ctrl_h`) 생성 경로 없음. `predc_1m_h` 는 신규 테이블이나 본 사이클은 조회만(INSERT 는 AI 파이프라인 — 본 사이클 제외) |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `val`(값/value) 표준 단어 신규 등록. 기본 데이터 도메인 (조합), 등록일 2026-05-18. `raw_val`·`corr_val`·`tag_val`·`predc_val` 조합 재료, `acq` 공백 해소(2026-05-13) 선례 동형, `qty`(수량)와 의미 분리 비고 — 표 행 추가
- [x] `swtp/.claude/rules/dict/domain-abbreviations.md` — `opt` 비즈니스 도메인 약어 설명을 "AI 최적화 결과 저장 테이블 prefix 전용" 에서 "AI 최적화 결과 및 예측 결과 저장 — `com.mo.swtp.opt` 패키지(예측 시계열 `predc_1m_h` 포함)" 로 확장. `ai` 와 경계 비고 갱신 (2026-05-18 송수펌프제어분석-7번섹션 ANALYZE1 안건 1)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `predc_id` 폐기 후 재등록: `predc`(표준 단어) + `id`, `DOM_SEQ_BIGINT`, 사용 테이블 `predc_1m_h`, 신규 의미(시계열 BIGINT PK) 비고
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `predc_dtm` 폐기 후 재등록: `predc` + `dtm`, `DOM_DTM`, `predc_1m_h` 파티션 키, 신규 의미(단일 예측 대상 시각) 비고
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `predc_val` 신규 등록: `predc` + `val`(신규), `DOM_QTY_15_4`, NULL 허용, 사용 테이블 `predc_1m_h`
- [x] `swtp/backend/.claude/rules/db/partitioning-and-retention.md` — §2 데이터 보존 기간 표에 예측 시계열(`predc_1m_h`) 행 추가: 보존 3년, 삭제 방식 파티션 DROP (dba 권고, AI 예측 결과 선례 정합)

> 신규 표준 데이터 도메인 0건 — `standard-data-domains.md` 갱신 의무 없음. 신규 비즈니스 도메인 약어 신규 등록 0건(`opt` 는 기등록 도입예정 항목의 설명 확장 — 거부 이력 번복 아님). DB 컬럼 신규는 `predc_val` 1건 + 폐기 후 재등록 2건(`predc_id`·`predc_dtm`).

## 산출물
- [계획안](../../../plan/20260518/송수펌프제어분석-7번섹션/PLAN1.md) (생성 예정)
