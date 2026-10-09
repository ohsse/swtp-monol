---
status: approved
created: 2026-06-02
updated: 2026-06-02
---
# 송수펌프 가동이력 2번섹션 — 펌프 상태 카드 API 도메인 분석

## 작업 배경

- **요청 요약**: "송수펌프 가동이력" 대시보드의 **2번 섹션** — 펌프별 현재 상태를 카드 형태로 표출하는 **읽기 전용 API** 작성.
  - 카드 노출 항목: 펌프명 · **가동률(%)** · 정격양정(`ratedHead`, m) · 정격유량(`ratedFlwrt`, m³/h)
  - **가동률 산정 (구동 방식별 분기)**:
    - **정격펌프(`PumpDriveType.RATED_DRIVE`)**: 현재 가동상태(OPS) On(1) → `100%`, Off(0) → `0%`
    - **인버터펌프(`PumpDriveType.INVERTER_DRIVE`)**: 현재 주파수값(FQI, Hz)을 그대로 `%`로 표현 (예: 45 → 45%, 정격주파수 정규화 미적용)
- **외부 산출물**: `backend/image/송수펌프가동이력.png` (대시보드 와이어프레임 — 2번 섹션 카드 영역 확인. 카드 예시 가동률 100% / 12.5% / 0% / 87.5%)
- **사전 결정 (plan 승인)**: 규모 Medium, 신규 엔티티·DB 스키마 변경 0건, 기존 enum/패턴 재사용. 조회 스코프 = 전체 활성 송수펌프(전체 `EquipType.PUMP` + `use_yn=Y`), 인버터 가동률 = FQI 값 그대로, 결측 처리 = `operationRate=null` + 품질코드 동봉.

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: "가동률" 응답 DTO 변수명 표준 단어 등록
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `rate`(율/비율) 신규 표준 단어 등록 권고 (후보 a). `flwrt`(flow rate 4자 복합 압축 약어)와 `rate`(독립 단어)는 복합 약어 vs 독립 단어 층위 차이로 충돌 없음 — `freq` vs `flwrt` 미충돌 선례 동형. `oprtng`(상태값)+`qty` 조합은 "율(%)" 의미 표현 불가하여 기존 조합 회피(후보 b) 부적합. `hr`·`actl`·`inq` 가 응답 DTO 변수명 용도로 등록된 선례(송수펌프제어_운전현황분석 ANALYZE1)에 따라 등록 불요(후보 c) 기각. 기본 데이터 도메인 미지정 — 계산 비율은 `DOM_QTY_15_4`(측정값)와 의미 경계 분리. 신규 비즈니스 약어·데이터 도메인·DB 컬럼(표준 용어) 0건 (DB 영속 0건).
- **결론**: 신규 표준 단어 `rate` 1건만 등록. DB 컬럼명(표준 용어)·데이터 도메인·비즈니스 도메인 약어 신규 등록 없음. 응답 DTO 가동률 필드명은 `oprtng`(기존) + `rate`(신규) 조합으로 `oprtngRate` 채택 (abbreviation 컨벤션 정합 — `elpwrUnitQty`·`predcFlwrt` 선례).

### 안건 2: 펌프 가동률 산정 도메인 규칙 정합성 + 4영역 점검
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**:
    - (Q1) 정합. FQI 가동률도 GOOD 만 인정, BAD/UNCERTAIN → null. FQI 가 Hz→% 직결이라 품질불량 값 표출 시 오표시 위험 (`ot-integration.md §3`).
    - (Q2) FQI 단독으로 충분 — OPS 병행 시 신호 불일치(기동 중 OPS 수신 지연) false negative 위험. FQI≈0 이면 자연히 0% 표출. `coding-discipline.md §2` 단순성 정합.
    - (Q3) **OPS 는 `raw_val` 단독 사용 필수** — `corr_val` 은 Hold Last Value 결과일 수 있고, OPS 는 §3 에서 Hold Last Value 를 명시 금지("통신 단절 후 정지해도 ON 표시" 오인 방지). **FQI 는 `effectiveVal(corrVal ?? rawVal)` 적용 가능** (Hold Last Value 명시 금지 아님).
    - (Q4) 태그 없음 → null 타당. RATED_DRIVE 펌프에 OPS 태그조차 없으면 설비 구성 오류이므로 근거 없는 값 표출보다 null 이 안전.
    - (Q5) 도메인 4영역(알람·인터록·AI운전모드·이력) **모두 비해당** — 표출 전용 read-only, OT 아웃바운드·테이블 INSERT/UPDATE 무접촉.
- **결론**: 산정 규칙 도메인 정합 확인. **값 소스 비대칭 확정** — OPS=`raw_val` 단독, FQI=`effectiveVal(corrVal ?? rawVal)`. 양쪽 모두 `qualityCd == GOOD` 필수, 그 외 → null. 블로커: 본 비대칭 정책을 "## 가정 및 미해결 질문"에 명기 의무 (아래 반영 완료).

### 안건 3: API 계층 구조·DTO 패턴·패키지 배치
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**:
    - (Q1, **블로커**) `com.mo.swtp.instrument` 패키지 배치 권고. PUMP 제원(`ratedHead`·`ratedFlwrt`·`driveType`)의 도메인 소유자가 instrument 이고, 본 API 는 시설 스코프가 없으므로 `FacilityOperatingStatusService` 클론·`facility` 배치는 도메인 경계 오염. "사이클 간 자산 자동 원용 금지" 메모리 정합 — 동형 패턴은 참고만.
    - (Q2, 권고) 신규 전용 컨트롤러 생성. CRUD(`InstrumentController`)·시설관리(`FacilityController`) 와 책임이 다른 대시보드 집계 조회.
    - (Q3, 통과) `BaseAuditResponseDto` 미적용이 `api-patterns.md §적용 범위`(실시간 통지·요약 응답 미적용) 정합.
    - (Q4, 권고) 전용 조회 메서드(`findActiveByEquipType(EquipType)` 동형) 신규 추가가 범용 `findInstruments(SearchDto)` 재사용보다 의도 명확·단순.
    - (Q5, 참고) 가동률 산정은 별도 `private` 헬퍼로 분리하여 메서드 50줄·추상화 3단·DTO 상속 1단 정량 기준 유지.
- **결론**: 패키지 = `com.mo.swtp.instrument`. 전용 컨트롤러·서비스·DTO 신규. DTO 는 `BaseAuditResponseDto` 미상속 + 정적 팩토리. 활성 PUMP 조회는 전용 메서드 신규 추가. 가동률 산정 헬퍼 분리.
  > 메모: 백엔드 엔지니어 Q5 답변이 가동률을 "On 시간/전체 시간 비율"로 오해했으나, 본 작업 가동률은 **순시값**(정격 On/Off→100/0, 인버터 현재 FQI→%)이라 산정 로직이 더 단순함. 헬퍼 분리·정량 기준 결론은 그대로 유효.

### 안건 4: 조회 성능·인덱스 재사용·N+1
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 전 항목 통과. (1) "전체 활성 PUMP" 마스터 풀스캔 허용 — 수십~수백 행 규모에서 Seq Scan 정상, JPA JOINED 단일 자식(`instrument_m ⨝ pump_m`) 조인 비용 무시 가능, 신규 인덱스 불요. (2) 기존 인덱스 2개(`idx_tag_m_instrument_id_tag_se_cd`·`idx_rawdata_1m_h_tag_time`) 재사용 충분, DDL 0건 타당. (3) `findLatestByTagSrlNos` 1시간 윈도+DISTINCT ON 이 파티션 프루닝·N+1 회피 정합. (4) PUMP 단일 자식 필터라 다형성 LEFT OUTER JOIN 핫패스 무관. (5) DDL·마이그레이션·docs/ddl 영향 0건 확인.
  - 참고(낮음) 2건: instrument_m 규모 수천 행 성장 또는 p6spy 500ms 초과 시 `(equip_type_cd) WHERE use_yn='Y'` 인덱스 권고 / `findLatestByTagSrlNos` IN 절 상한 미정 (현 수십~백 개 수준 무문제).
- **결론**: 신규 인덱스·DDL·마이그레이션 0건. 기존 인덱스 2개 재사용. 참고 2건은 향후 성장 대비 관찰 사항으로만 기록 (본 사이클 미적용).

---

## 표준 사전 카탈로그

### 신규 표준 단어
(DB 컬럼 조합 + 응답 DTO 변수명 재료. 1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `rate` | 율/비율 | 신규 | `standard-words.md` 미등록. `flwrt`(flow rate 복합 압축 4자 약어)와 독립 단어 vs 복합 약어 층위 차이로 충돌 없음 (`freq` vs `flwrt` 선례 동형). `oprtng`·`qty` 조합으로 "율(%)" 표현 불가. `hr`·`actl`·`inq` DTO 변수명 등록 선례 동형. 응답 DTO `oprtngRate` 변수명 조합 재료. 기본 데이터 도메인 미지정 — 계산 비율은 `DOM_QTY_15_4`(측정값)와 의미 경계 분리, 비율 전용 도메인 3건 집적 시 재검토 |

### 신규 표준 데이터 도메인
없음 — `oprtngRate` 는 응답 DTO 계산값(DB 미영속). DTO 필드 Java 타입은 `BigDecimal` (기존 매핑 원칙 재사용, 신규 도메인 불필요).

### 신규 표준 용어
없음 — 신규 DB 컬럼 0건. `oprtngRate` 는 영속 컬럼이 아니므로 `.claude/rules/dict/standard-terms.md` 등록 대상 외. `ratedHead`·`ratedFlwrt`·`drive_type_cd`·`tag_se_cd`(OPS·FQI) 등 사용 컬럼은 모두 기존 표준 용어 재사용.

---

## 신규 엔티티/DB 컬럼

**없음.** 본 작업은 읽기 전용 API 로 신규 엔티티·DB 컬럼·인덱스·마이그레이션 0건.
- 사용 엔티티 (모두 기존): `Instrument`/`Pump`(`instrument_m`/`pump_m`), `Tag`(`tag_m`), `RawData`(`rawdata_1m_h`)
- 사용 enum (모두 기존): `PumpDriveType`(INVERTER_DRIVE/RATED_DRIVE), `TagMeasurementType`(OPS·FQI), `QualityCode`(GOOD/BAD/UNCERTAIN), `EquipType`(PUMP)
- 사용 인덱스 (모두 기존): `idx_tag_m_instrument_id_tag_se_cd`, `idx_rawdata_1m_h_tag_time`

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 판정 | 해소책 (회의 결론 일치) |
|----------|------|----------------------|
| `rate` vs `flwrt` 어근 충돌 의심 | **충돌 없음** | 복합 압축 약어(`flwrt`) vs 독립 단어(`rate`) 층위 차이 — `freq` vs `flwrt` 미충돌 선례 동형 (안건 1) |
| 패키지 배치 — `facility` vs `instrument` | **`facility` 배치/클론 금지 (블로커)** | `com.mo.swtp.instrument` 신규 배치. PUMP 제원 도메인 소유자 + 시설 스코프 부재. `FacilityOperatingStatusService` 는 동형 패턴 참고만 (안건 3) |
| OPS 값 소스 `corr_val` 사용 가능 여부 | **OPS 는 `raw_val` 단독 (블로커→가정 명기로 해소)** | OPS 는 `ot-integration.md §3` Hold Last Value 명시 금지. FQI 는 `effectiveVal(corr??raw)` 적용 가능 (안건 2). "## 가정 및 미해결 질문" 명기 완료 |

---

## PLAN 으로 전달할 결정 사항

- **패키지**: `com.mo.swtp.instrument` (`web`/`service`/`dto` 하위)
- **컨트롤러**: 신규 `PumpOperationRateController` — `GET /api/instrument/pump-operation-rate` (경로·`@Tag` 번호는 PLAN 확정, 후보 `@Tag(name = "11. 송수펌프 가동이력")`). `CommonController` 상속, `ResponseEntity<CommonResponseDto<List<PumpOperationRateDto>>>` 반환, `@Operation`/`@ApiResponses`.
- **서비스**: 신규 `PumpOperationRateService` `@Transactional(readOnly=true)`. 4-SELECT 패턴 (활성 PUMP → OPS·FQI 태그 배치 → 최신 raw 값 배치 → 인메모리 가동률 산정). 가동률 산정 `private BigDecimal computeOprtngRate(Pump, RawDataLatestDto ops, RawDataLatestDto fqi)` 헬퍼 분리.
- **응답 DTO**: 신규 `PumpOperationRateDto` (읽기 전용 — `BaseAuditResponseDto` 미상속, `private` 생성자 + 정적 팩토리 `of(...)`). 필드: `pumpId`·`pumpNm`·`driveType`(`@Schema(implementation=PumpDriveType.class)`)·`ratedHead`·`ratedFlwrt`·`oprtngRate`(BigDecimal nullable)·`qualityCd`(`@Schema(implementation=QualityCode.class)` nullable)·`acqDtm`(nullable).
- **조회 메서드**: `InstrumentRepository`/`CustomRepository` 에 활성 PUMP 전용 조회 메서드 신규 추가 (예: `findActiveByEquipTypeOrderByDispOrd(EquipType)` 또는 파생 쿼리 `findByEquipTypeAndUseYnOrderByDispOrdAsc`). 정렬 `dispOrd ASC`.
- **가동률 산정 규칙 (확정)**:
  - 정격(RATED_DRIVE): OPS `qualityCd==GOOD` && `raw_val==1.0` → `100`, `==0.0` → `0`, 그 외 → `null`
  - 인버터(INVERTER_DRIVE): FQI `qualityCd==GOOD` → `effectiveVal(corrVal??rawVal)` (%), 그 외 → `null`
  - 태그 없음/최신값 부재(1시간 윈도 밖) → `null`
  - null 반환 시 해당 펌프의 판정 기준 태그(정격=OPS, 인버터=FQI) 의 `qualityCd`·`acqDtm` 동봉 (태그 자체 부재 시 둘 다 null)
- **DB**: 변경 0건 (DDL·마이그레이션·docs/ddl 사본 무영향).

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| **OPS 값 소스 = `raw_val` 단독, FQI 값 소스 = `effectiveVal(corrVal ?? rawVal)`** | 결정 | `ot-integration.md §3` — OPS 는 Hold Last Value 명시 금지(통신단절 후 ON 오인 방지)이므로 `corr_val` 미사용. FQI 는 Hold Last Value 금지 아님. 안건 2 도메인 블로커 해소 |
| "송수펌프" = 시스템 전체 `EquipType.PUMP` 활성 계측기 | 가정 | 송수/전송 구분 카테고리가 현재 도메인에 없음. 사용자 plan 답변(전체 송수펌프 목록) 기반. 향후 카테고리 도입 시 별도 사이클 |
| 인버터 가동률 = FQI 값 그대로 % (정격주파수 정규화 미적용) | 결정 | 사용자 plan 답변. FQI raw 값이 곧 % (예: 45→45%). 60Hz 초과 값도 그대로 표출 |
| 응답 정렬 = `dispOrd ASC` | 가정 | 카드 표시 순서. 동률 시 `instrumentNm ASC` 보조 정렬 (PLAN 확정) |
| `@Tag` 번호 = "11. 송수펌프 가동이력" | 미해결 | PLAN 단계에서 기존 `@Tag` 번호(03·06·08·09·10) 충돌 확인 후 확정 |
| 엔드포인트 경로 = `/api/instrument/pump-operation-rate` | 미해결 | PLAN 단계에서 기존 `/api/instrument/**` 경로 충돌 확인 후 확정 |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 가동률 산정 7 케이스 단위 테스트 GREEN (Mockito) | 신규 `PumpOperationRateServiceTest`: ①정격 OPS GOOD raw_val=1.0→100 ②정격 OPS GOOD raw_val=0.0→0 ③정격 OPS BAD/UNCERTAIN→null+quality ④정격 OPS 태그없음→null,quality null ⑤인버터 FQI GOOD val→effectiveVal(%) ⑥인버터 FQI BAD/null→null+quality ⑦인버터 FQI 태그없음→null |
| 엔드포인트가 활성 PUMP 전체를 `dispOrd` 순으로 반환 | `./gradlew.bat :api:test` PASS + Swagger 호출 시 `CommonResponseDto<List<PumpOperationRateDto>>` 형태 확인 |
| 전체 빌드 무결성 | `./gradlew.bat build` BUILD SUCCESSFUL (QClass 재생성 포함) |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | `rawdata_1m_h` 최신값 SELECT + 가동률 % 계산 후 응답만 수행. `alarm_h` INSERT/UPDATE 없음. 임계값·전이·복귀 조건 무접촉 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령(아웃바운드) 미발행. PLC 방향 통신 없음. `pump_interlock_p` 무접촉. 카드 표출 조회 전용 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`·`ai_drvn_mod_h` 무접촉. 사용자 의도/시스템 상태 변경 없음. SCADA 5분 초과 강제 전환 로직 미포함 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `transition_reason` 기록 트리거 없음. `pump_ctrl_h` 제어 로그 생성 없음. 신규 엔티티/DB 컬럼 없는 조회 전용 |

> 비해당 단독 4건 차단 해제 조건 자가 점검: (1) 각 행 구체 사유 명기 완료, (2) "## 신규 엔티티/DB 컬럼" 없음 — 두 조건 동시 충족.

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `rate`(율/비율) 신규 표준 단어 등록 (풀네임 rate, 기본 데이터 도메인 미지정, 응답 DTO `oprtngRate` 변수명 조합 재료, `flwrt` 복합약어 vs 독립단어 충돌 없음 근거 명기) ✅ 2026-06-02 적용 완료

> 본 작업은 신규 비즈니스 도메인 약어·표준 데이터 도메인·DB 컬럼(표준 용어)·DDL·엔티티 패턴 변경 0건이므로 위 1건 외 룰 갱신 없음.

## 산출물
- [계획안](../../../plan/20260602/송수펌프가동이력_2번섹션/PLAN1.md) (PLAN 단계 생성 예정)
