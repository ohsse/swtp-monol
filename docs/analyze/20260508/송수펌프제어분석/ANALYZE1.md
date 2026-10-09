---
status: approved
created: 2026-05-08
updated: 2026-05-08
---

> **갱신 이력 (2026-05-08, 사용자 결정 반영)**:
> - 안건 5 옵션 C → 옵션 D — `pwtf_m.reference_dwt_facility_id` FK 폐기, 화면 표시 순서 1번 DWT 채택
> - §2 자동/반자동 표시 컬럼 결정 → 차후 태스크 이관
> - 화면 §4 → DWT.min_req_prsr 만 도입, POINT 임계값은 차후 사이클 이관
> - §7 1시간 후 분석 결과 → 단일 시점 가정 유지 (PLAN 단계 최종 결정)
> - 마이그레이션 SQL: 6개 → 4개 + 선택 1개 (V8_4 PWTF FK 폐기)
> - 룰 갱신 지시서: 5건 → 4건 (`reference_dwt_facility_id` 등록 폐기)

# 송수펌프제어분석 — 도메인 분석

## 작업 배경

사용자가 `송수펌프 AI 플랫폼` 의 종합 모니터링 화면 (`송수펌프제어분석`) 을 frontend 에서 구현하려 한다. 본 화면은 시설 선택 + 7개 데이터 표출 섹션 (운영현황·주요인자·요구관압·토출관압·펌프가동대수·분석결과) 으로 구성되며, 기존 `06. 송수펌프 제어` (PumpControlController) + `07. 송수펌프 운전현황 분석` (PumpDrvnStatusController) 가 어디까지 커버하는지 검증한 결과 **다수의 도메인 결정 미반영 + API 부재** 가 발견되어 정식 ANALYZE 사이클을 진입했다.

### 외부 산출물

- `image/송수펌프제어분석.png` — 화면 와이어프레임 (사용자 첨부, 7섹션 위치 확인용)
- 사용자 명시 요구사항 (채팅) — 화면 §1~§7 의 데이터 출처와 도메인 의도 명시
- 사전 분석 plan: `~\.claude\plans\image-png-1-quizzical-sunrise.md` (사용자 approved 2026-05-08) — 본 ANALYZE 의 D1~D9 안건 입력 출처

### 화면 7섹션 요약

| 섹션 | 표출 데이터 | 도메인 매핑 |
|------|----------|------------|
| §1 시설 목록 | 생활정수지·공업정수지·**선남가압장** — 하위 배수지(DWT) 보유 시설 | facility_type_cd IN ('PWTF', **'PRSF' 신설**) + parent self-FK |
| §2 운영현황 | 활성 정수지의 토출유량계 압력/유량 + 펌프 #1~#4 on/off + AI 모드 자동/반자동 | instrument(PUMP/PRSMTR/FLWMTR) + tag(PRI/FRI/**OPS 신설**) + ai_drvn_mod_p |
| §3 주요인자 | 활성 정수지가 공급하는 배수지 목록 + 유입압력·유입유량·**밸브 개도율**·수위·유출유량 | facility(DWT) + instrument(PRSMTR/FLWMTR/VALVE/LVMTR) + tag(PRI/FRI/**VOI 신설**/LEI + io_cd) |
| §4 배수지 요구 관압 | 기준배수지의 최소요구관압 | **DWT.min_req_prsr 자식 컬럼만 신설** (사용자 결정 2026-05-08). 기준배수지=활성 정수지 자식 DWT 중 `disp_ord` ASC 1번째 (FK 신설 없음). 분기점(POINT) 임계값은 **차후 사이클**로 이관 |
| §5 송수펌프제어 | 정수지별 토출 관압 | facility(PWTF) → instrument(PRSMTR) → tag(PRI 토출측) → rawdata 최근값 |
| §6 펌프가동대수 | 정수지별 On 펌프 대수 | §2 의 펌프 가동상태 집계 (서버 집계 권장) |
| §7 분석결과 | 1시간 후 펌프 가동 상태·관압·유량 예측 | pump_predc_h (예측 결과) + pump_cmbn_d (조합 상세 매핑으로 펌프별 분해) |

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 가압장 facility_type_cd `PRSF` 자식 신설

- 호출 에이전트: `wtp-glossary-manager` (필수), `wtp-domain-expert` (도메인 임팩트)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `FacilityType` enum 에 `PRSF` (Pressure Booster Station Facility) 신규 추가. **비즈니스 도메인 약어 사전 등록 불필요** — `pwtf`·`dwt` 가 facility 자식으로 흡수되어 약어 폐기 예정인 선례와 동일 (마스터도메인설계 ANALYZE Round 2 결정). 자식 클래스명: `PressureBoosterStation`. 자식 전용 컬럼은 skeleton 정책. 신규 표준 단어·표준 데이터 도메인·표준 용어 0건 — Java enum 코드 변경만으로 해결.
  - **wtp-domain-expert**: PRSF 가압장도 정수지(PWTF) 와 마찬가지로 **시설 단위 `ai_drvn_mod_p` 행 분리** 의무. SCADA 5분 초과 강제 전환도 가압장 시설 단위로 별도 평가. `equip_type_cd='PUMP'` 단독 필터로 PWTF/PRSF 펌프 구별 불가 — `facility_type_cd` 필터 병행 강제 (`ot-integration.md §5`). `pump_interlock_p` 시설 종류별 룰 분리 필요 가능성을 PLAN 명시 의무.
- **결론**: `FacilityType.PRSF` enum 값 추가 + `PressureBoosterStation` 자식 클래스 (자식 전용 컬럼 0건 skeleton) 본 사이클 도입. 도메인 약어 사전 등록 없음. 인터록 평가 시 `facility_type_cd` 필터 강제 — `pump_interlock_p` 시설 종류별 룰 분리는 PLAN 단계 결정.

### 안건 2: 펌프 가동상태 측정 유형 코드 `OPS` 신설

- 호출 에이전트: `wtp-glossary-manager` (필수)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `TagMeasurementType.OPS` (Operation Status) 신규 추가. 표준 단어 등록 불필요 (`oprtng`·`drvn` 기존 등록, OT 측정 유형은 `ot-integration.md §3` SSOT). PWI(전력)>0 추론은 SCADA 의 별도 on/off DI 신호 태그 존재를 무시하므로 부적합. 분류: 신규 (Java enum 값 추가만).
- **결론**: `TagMeasurementType.OPS` enum 값 추가. 표준 단어/용어 등록 없음. `ot-integration.md §3` 결측 대체값 표 갱신 (OPS 행 추가, BAD 시 즉시 격상) 의무.

### 안건 3: 밸브 개도율 측정 유형 코드 `VOI` 신설

- 호출 에이전트: `wtp-glossary-manager` (필수)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `TagMeasurementType.VOI` (Valve Opening Indicator) 신규 추가. 5종 (FRI/PRI/LEI/PWI/RMS) 과 동일 3자 대문자 패턴. `OPN` 은 `oprtng` 어근 충돌 가능성. 단위 코드 (`%`) 는 `tag_m.unit_cd` (DOM_CODE_20) 로 추가, 별도 신규 데이터 도메인 불필요. 표준 단어 `valve`·`opening` 신규 등록은 1건 사용 시점 부담 회피 (송수펌프제어_운전현황분석 ANALYZE 의 `file` 단어 선례 적용) — 보류.
- **결론**: `TagMeasurementType.VOI` enum 값 추가. `unit_cd` 허용값에 `%` 코드 추가 검토 (PLAN 단계 결정). 표준 단어/용어 등록 없음.

### 안건 4: 자식 전용 컬럼 일괄 도입 (`pump_m`·`dwt_m`·`valve_m`·`point_m`)

- 호출 에이전트: `wtp-dba-reviewer` (필수)
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: NOT NULL 컬럼 추가 시 `indexing-and-migration.md §2` **3단계 무중단 마이그레이션** (NULL 추가 → 백필 → NOT NULL 전환) 의무. 5개 자식 마이그레이션 SQL 파일 **분리 작성** 권고 — `V8_1__pump_m_self_columns.sql`, `V8_2__dwt_m_self_columns.sql`, `V8_3__valve_m_self_columns.sql` (도입 시), `V8_4__point_m_self_columns.sql` (도입 시), `V8_5__prsf_m_skeleton.sql`. 각 파일 `COMMENT ON COLUMN` 의무 (`§4`). DOM_QTY_15_4 재사용 충분 (`rated_head`·`rated_flwrt`·`min_req_prsr` 모두 NULL 정책 강화). Valve 자식 개도율 컬럼은 RawData 만 사용 시 `valve_m` skeleton 유지 (자식 마스터 컬럼 0건 시 JOINED 자식 테이블 생성 실익 부재) — PLAN 결정 의무.
- **결론**: 5개 자식 마이그레이션 SQL 파일 분리 + 3단계 NOT NULL + COMMENT 의무. Valve 컬럼 도입 여부는 PLAN 단계 (RawData 만 사용 시 skeleton 유지). DOM_* 신규 등록 없음.

### 안건 5: 기준배수지(Reference DWT) 정의

- 호출 에이전트: `wtp-domain-expert` (필수)
- Round 1 답변 요약:
  - **wtp-domain-expert**: 후보 3안 (옵션 A `Facility.main_yn` 재활용 — "주요시설 vs 인터록 기준" 의미 충돌 / 옵션 B 신규 `reference_yn` — 정수지별 1:1 보장 불가 / 옵션 C `pwtf_m.reference_dwt_facility_id` FK — 모호성 제거 강력) 비교. 인터록 평가 관여 여부가 핵심 분기.
- **사용자 결정 (2026-05-08)**: **옵션 D 채택** — "주요배수지" 라는 표현은 사용하지만, 본 사이클은 활성 정수지 자식 DWT 중 **화면 표시 순서(`disp_ord`) 1번** DWT 를 기준배수지로 사용. **신규 FK 컬럼 도입 없음** (V8_4 마이그레이션 폐기). 추후 활성 정수지 내 여러 배수지 중 운영자가 선택한 배수지 정보를 별도로 보는 기능이 필요해지면 그 시점에 별도 사이클로 reference 선택 메커니즘 도입.
- **결론**: PWTF 자식 `reference_dwt_facility_id` FK 컬럼 **미도입**. 화면 §4 표출 시 `Facility.findChildrenByParentIdAndType(activePwtfId, DWT) ORDER BY disp_ord ASC LIMIT 1` 로 1번째 DWT 의 `min_req_prsr` 표출. backend 표준 용어 사전에 `reference_dwt_facility_id` 등록도 함께 폐기 (1건 사용도 발생하지 않음). 인터록 평가 관여 여부는 본 사이클 영향 외 — 인터록 평가에 기준배수지 압력이 필요하면 별도 사이클에서 reference 선택 메커니즘과 동시 도입.

### 안건 6: DWT 입구/출구 FLWMTR 구별

- 호출 에이전트: `wtp-domain-expert` (필수)
- Round 1 답변 요약:
  - **wtp-domain-expert**: **옵션 A 채택** (`Tag.io_cd` 활용 — 이미 INPUT/OUTPUT/BIDIR 정의됨). 옵션 B (Instrument 단위 io_cd) 는 FLWMTR 외 자식과 일관성 깨짐. 옵션 C (instrument_nm 명명 규칙) 는 정규화 원칙 위반. 단 `AlarmEvaluator` 의 태그 필터 로직에 `io_cd` 반영 필요성 점검 (`ot-integration.md §5` 알람 전이 조건).
- **결론**: `Tag.io_cd` 활용. Instrument 단위 io_cd 신설 없음. AlarmEvaluator 영향 평가는 본 사이클 외 (read-only 화면 — 알람 평가 경로 직접 영향 없음).

### 안건 7: pump_predc_h.pwtf_id → facility_id 정렬 + 펌프별 예측 분해

- 호출 에이전트: `wtp-dba-reviewer` (필수)
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: `pump_predc_h.pwtf_id` → `facility_id` 컬럼 정렬은 **3단계 무중단 절차** + 파티션 루트 `ALTER TABLE` 의 자식 파티션 `ACCESS SHARE LOCK` 영향 — 운영 시간 외 실행 PLAN 명시 의무. 펌프별 예측 분해는 옵션 B (`pump_cmbn_d` 매핑 활용, DB 변경 0건) 가 화면 요구를 충족하는지 도메인 전문가 확인 필요 (옵션 A·C 는 파티션 스키마 변경 비용).
- **결론**: 컬럼 정렬은 PLAN 단계 운영 시간 명시. 펌프별 예측 분해는 **옵션 B (pump_cmbn_d 매핑)** 우선 — 화면이 단순 "펌프별 1시간 후 on/off" 표출이면 조합 코드 → 펌프 매핑으로 충분. DB 변경 0건이 정량 기준·시계열 정합성 면에서 우월.

### 안건 8: Controller 분리 전략

- 호출 에이전트: `wtp-backend-engineer` (필수)
- Round 1 답변 요약:
  - **wtp-backend-engineer**: **옵션 C 채택** (`08. PumpControlAnalysisController` + `09. DwtStatusController` 도메인별 2분리). 근거: (1) 계층 책임 분리 — 정수지 중심 (§1·§2·§5·§6·§7) vs 배수지 중심 (§3·§4) Service 의존성 분리 (5개 이상 팽창 방지 — SRP). (2) 재사용성 — `09` 가 향후 배수지 운영 대시보드 별도 화면에서 동일 엔드포인트 재사용 가능. (3) 기존 06 (제어 명령·AI 모드 변경, 쓰기) 와 분석 (읽기) 혼재 회피.
- **결론**: 옵션 C 채택. `08. 송수펌프 제어 분석` (`com.mo.swtp.pump.web.PumpControlAnalysisController`, base path `/api/pump-control/analysis`) + `09. 배수지 모니터링` (`com.mo.swtp.dwt.web.DwtStatusController` 또는 `com.mo.swtp.facility.web.DwtStatusController` — 패키지 위치 PLAN 결정, base path `/api/dwt-status`) 신설.

### 안건 9: 시설 목록 조회 API + Repository 표준 메서드

- 호출 에이전트: `wtp-backend-engineer` (필수)
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 시설 목록 조회 — `FacilityCustomRepository.findFacilitiesHavingDwtChild(FacilityType type)` (Querydsl exists 서브쿼리). 표준 RawData 메서드 — `RawDataCustomRepository.findLatestByTagSrlNos(List<String> tagSrlNos)` (IN 절 배치, GROUP BY tag_srl_no + MAX(acq_dtm) 서브쿼리). **3-table JOIN 단일 메서드는 추상화 오염** — Service 가 `instrumentRepository.findByFacilityIdAndEquipType()` + `rawDataRepository.findLatestByTagSrlNos()` 2단계 분리. N+1 회피: `instrument_id` IN 절 + `tag_srl_no` IN 절 배치. JOIN FETCH 는 JPA JOINED 상속에서 LEFT OUTER JOIN N개 곱연산 위험. **DTO**: 섹션별 7개 분리 (통합 DTO 는 §2.5 면책 불가, 50줄 초과 위험).
- **결론**: Repository 메서드 시그니처 + IN 절 배치 + DTO 섹션별 분리. 09 엔드포인트 파라미터 (facilityId vs 인증 컨텍스트 테넌트) 는 PLAN 결정.

---

## 표준 사전 카탈로그

> 본 ANALYZE 는 **신규 표준 단어/표준 데이터 도메인/표준 용어 0건** — 모든 신규 식별자가 Java enum 값 추가 (FacilityType.PRSF, TagMeasurementType.OPS·VOI) 로 해결되어 사전 등록 대상 외 (OT 측정 유형은 `ot-integration.md §3` SSOT).

### 신규 표준 단어

없음.

### 신규 표준 데이터 도메인

없음. 자식 컬럼 (`rated_head`·`rated_flwrt`·`min_req_prsr`) 모두 기존 `DOM_QTY_15_4` 재사용 (DBA 2차 승인). 안건 5 사용자 결정으로 `reference_dwt_facility_id` (DOM_ID_36) 도입 폐기.

### 신규 표준 용어

없음. 안건 5 사용자 결정 (2026-05-08) 으로 `reference_dwt_facility_id` 등록도 폐기 — 화면 §4 가 활성 정수지 자식 DWT 중 `disp_ord` ASC 1번째를 기준배수지로 사용하므로 신규 컬럼 0건.

---

## 신규 엔티티/DB 컬럼

### 신규 enum 값 (Java)

| 위치 | 추가 값 | 의미 |
|------|--------|------|
| `com.mo.swtp.facility.domain.enumtype.FacilityType` | `PRSF` | 가압장 (Pressure Booster Station Facility) |
| `com.mo.swtp.tag.domain.enumtype.TagMeasurementType` | `OPS` | 펌프 가동상태 (Operation Status, on/off DI 신호) |
| `com.mo.swtp.tag.domain.enumtype.TagMeasurementType` | `VOI` | 밸브 개도율 (Valve Opening Indicator, % 단위) |

### 신규 엔티티 (자식 클래스)

| 클래스 | 위치 | 부모 | DiscriminatorValue | 자식 전용 컬럼 |
|------|------|------|-------------------|------------|
| `PressureBoosterStation` | `com.mo.swtp.facility.domain.PressureBoosterStation` | `Facility` | `PRSF` | 0건 skeleton (본 사이클) — PLAN 단계 자식 컬럼 검토 가능 |

### 신규 자식 전용 컬럼 (자식 skeleton 후속 도입)

| 자식 테이블 | 컬럼 | 데이터 도메인 | NULL | 비고 |
|----------|------|-------------|------|------|
| `pump_m` | `rated_head` | DOM_QTY_15_4 | NOT NULL | 정격 양정 (m). standard-terms 등록됨 (pumpcontrol_null_alignment ANALYZE) |
| `pump_m` | `rated_flwrt` | DOM_QTY_15_4 | NOT NULL | 정격 유량 (m³/h). standard-terms 등록됨 |
| `pump_m` | `tag_nm` | DOM_TAG_NM_50 | NULL | SCADA 태그명 (제조사 명판값) |
| `dwt_m` | `min_req_prsr` | DOM_QTY_15_4 | NOT NULL | 배수지 최소 요구 압력 (kgf/cm²). standard-terms 등록됨 |
| `valve_m` | (자식 컬럼 0건 유지) | — | — | RawData 만 사용 시 skeleton 유지 (DBA 권장) — PLAN 결정 |

> **사용자 결정 (2026-05-08) 으로 폐기**: `pwtf_m.reference_dwt_facility_id` 컬럼 (안건 5) — 화면 표시 순서 1번 DWT 사용으로 FK 신설 불필요. `point_m.min_req_prsr` 컬럼 — 분기점 임계값은 차후 사이클로 이관, 본 사이클은 DWT 임계값만 도입.

### 신규 마이그레이션 SQL 파일

| 파일 | 내용 | 비고 |
|------|------|------|
| `V8_1__facility_m_prsf_enum.sql` | `FacilityType.PRSF` enum 값 + `prsf_m` 자식 테이블 skeleton | DDL CHECK 제약 미사용 (Java `@Enumerated` 단일 방어선) |
| `V8_2__pump_m_self_columns.sql` | `rated_head`·`rated_flwrt`·`tag_nm` 추가 (3단계 NOT NULL) + COMMENT | 운영 데이터 백필 SQL 포함 |
| `V8_3__dwt_m_self_columns.sql` | `min_req_prsr` 추가 (3단계 NOT NULL) + COMMENT | 동일 |
| `V8_4__pump_predc_h_facility_id.sql` | `pwtf_id` → `facility_id` 컬럼 정렬 (3단계 양방향 동기) + COMMENT | 운영 시간 외 실행 명시 |
| `V8_5__tag_unit_cd_percent.sql` (선택) | `tag_m.unit_cd` 허용값에 `%` 추가 | PLAN 결정 시 |

> **사용자 결정 (2026-05-08) 으로 폐기**: 구 `V8_4__pwtf_m_reference_dwt.sql` (PWTF FK 추가) — 안건 5 결과. 본 사이클은 5개 SQL 파일 (필수 4개 + 선택 1개) 로 축소.

### 신규 Controller·Service·Repository

| 클래스 | Tag | 책임 |
|-------|-----|------|
| `PumpControlAnalysisController` | `08. 송수펌프 제어 분석` | §1·§2·§5·§6·§7 |
| `DwtStatusController` | `09. 배수지 모니터링` | §3·§4 |
| `FacilityListService` | — | 시설 목록 조회 (배수지 보유 정수지/가압장) |
| `PumpAnalysisDashboardService` | — | §2·§5·§6·§7 오케스트레이션 |
| `DwtStatusService` | — | §3·§4 오케스트레이션 |
| `FacilityCustomRepository.findFacilitiesHavingDwtChild` | — | exists 서브쿼리 |
| `RawDataCustomRepository.findLatestByTagSrlNos` | — | IN 절 배치 + GROUP BY MAX |

---

## 기존 사전·패턴과의 충돌

| 항목 | 위치 | 해소책 |
|------|------|--------|
| `tag_m.unit_cd` 허용값 `%` 부재 | `tag_m` (`unit_cd` DOM_CODE_20) | PLAN 단계에서 `%` 추가 결정. `DOM_CODE_20` 재사용 (신규 데이터 도메인 등록 불필요) |
| `pump_predc_h.pwtf_id` 마이그레이션 미완료 | `pump_predc_h` | 본 사이클의 V8_4 마이그레이션 SQL 로 정렬 (3단계 절차 + 운영 시간 외 실행) |
| Valve 자식 개도율 컬럼 도입 미결정 | `valve_m` | RawData 만 사용 시 skeleton 유지 (DBA 권장). PLAN 결정 의무 |
| POINT 자식 `min_req_prsr` 도입 (분기점 임계값) | `point_m` | **본 사이클 외** — 사용자 결정 (2026-05-08) 으로 차후 사이클로 이관. 본 사이클 화면 §4 는 DWT.min_req_prsr 1개만 표출 |
| 화면 §2 "자동/반자동" 표시 기준 모호 | `ai_drvn_mod_p` | **본 사이클 외** — 사용자 결정 (2026-05-08) 으로 차후 태스크로 이관 ("아직 설계의도가 명확하지 않음"). 본 사이클은 §2 모드 표시 컬럼을 응답 DTO 에 포함하되 어느 컬럼 (`ai_mode_cd` vs `ai_drvn_mod`) 을 표출할지는 후속 ANALYZE 에서 결정 |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안

1. `FacilityType` enum 에 `PRSF` 추가 + `PressureBoosterStation` 자식 클래스 신설 (skeleton). Java 패키지: `com.mo.swtp.facility.domain.PressureBoosterStation`. DiscriminatorValue: `PRSF`.
2. `TagMeasurementType` enum 에 `OPS`·`VOI` 추가.
3. `Pump` 자식에 `rated_head`·`rated_flwrt`·`tag_nm` 컬럼 (정격값 NOT NULL, tag_nm NULL 허용).
4. `DistributionWaterTank` 자식에 `min_req_prsr` 컬럼 (NOT NULL).
5. `Valve` 자식 — skeleton 유지 (RawData 만 사용).
6. **`PurifiedWaterTank` 자식 변경 없음** — 사용자 결정 (2026-05-08) 으로 기준배수지 FK 도입 폐기, 화면 §4 가 활성 정수지 자식 DWT 중 `disp_ord` ASC 1번째를 기준배수지로 사용.

### DB 설계 변경 초안

- 마이그레이션 SQL 4개 + 선택 1개 (V8_1 ~ V8_5) — 안건 4 결정 + 사용자 결정 (2026-05-08, V8_4 PWTF FK 폐기). 각 파일 3단계 NOT NULL + COMMENT 의무.
- `pump_predc_h.pwtf_id` → `facility_id` 정렬 (V8_4) — 운영 시간 외 실행, 사용자 결정 시점 명시.
- 기존 운영 데이터 백필 — 정격값·min_req_prsr 은 운영자 입력 필수 데이터, 기본값 임시 적용 후 운영자 보정 절차 PLAN 명시.

### 적용할 패턴

- **Controller 분리**: 옵션 C — `08. 송수펌프 제어 분석` + `09. 배수지 모니터링`.
- **Service 분리**: `FacilityListService` + `PumpAnalysisDashboardService` (§2·§5·§6·§7) + `DwtStatusService` (§3·§4). 단일 Service 50줄 + 추상화 3단 정량 기준 준수.
- **Repository 표준 메서드**: 2단계 분리 (`instrumentRepository` + `rawDataRepository`). Querydsl exists + IN 절 배치. **신규 메서드**: `FacilityCustomRepository.findFirstChildByParentIdAndType(parentId, FacilityType, OrderBy disp_ord)` — 화면 §4 의 표시 순서 1번 DWT 조회 (사용자 결정 2026-05-08 반영).
- **DTO 섹션별 분리**: 7개 섹션 DTO + Controller 통합 응답 (`PumpAnalysisDashboardDto` 가 7개 섹션 DTO 보유). 각 DTO `@Schema(implementation)` 명시.
- **펌프별 예측 분해**: 옵션 B (`pump_cmbn_d` 매핑) — DB 변경 0건.
- **§2 자동/반자동 표시 컬럼**: 응답 DTO 에 모드 필드를 포함은 하되 어느 컬럼 (`ai_mode_cd` vs `ai_drvn_mod`) 을 표출할지는 **차후 ANALYZE 사이클** 에서 결정 (사용자 결정 2026-05-08, "아직 설계의도가 명확하지 않음"). 본 사이클은 두 컬럼 모두 응답에 포함하는 임시 방안 채택 가능 — PLAN 결정.
- **인터록 시설 종류별 룰 분리**: PRSF 가압장과 PWTF 정수지의 인터록 룰 분리 필요 시 `pump_interlock_p` 의 `facility_type_cd` 컬럼 신설 또는 룰 ID 명명 규칙으로 분리 — PLAN 단계 결정.

---

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. 5인 회의에서 발견된 블로커 + 사용자 검토 필요 사항을 명시. 사용자 결정 (2026-05-08) 으로 4건이 **결정** 으로 확정되었다.

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 기준배수지는 활성 정수지 자식 DWT 중 `disp_ord` ASC 1번째 DWT — FK 신설 없음 | **결정** | 사용자 결정 (2026-05-08). 안건 5 옵션 C 폐기, 옵션 D 채택. 활성 정수지 내 reference 선택 메커니즘은 차후 사이클로 이관 |
| 화면 §2 "자동/반자동" 표시 컬럼 (`ai_mode_cd` vs `ai_drvn_mod`) 결정은 차후 태스크로 이관 | **결정** | 사용자 결정 (2026-05-08, "아직 설계의도가 명확하지 않음"). 본 사이클은 응답 DTO 에 두 컬럼 모두 임시 포함 또는 모드 필드 자체를 PLAN 단계에서 결정 |
| 화면 §4 "분기점 최소요구관압" 은 본 사이클 외 — DWT.min_req_prsr 1개만 도입 | **결정** | 사용자 결정 (2026-05-08). POINT 자식 임계값 (`point_m.min_req_prsr`) 컬럼 도입 + V8_3a 마이그레이션 폐기. 분기점 임계값은 차후 사이클 |
| §7 "1시간 후" 분석결과 — 단일 시점 (`predc_base_dtm + 1h`) 표출 가정 유지 | **결정** | 사용자 결정 (2026-05-08, "PLAN 단계에서 결정 — 현재 가정 (단일 시점) 으로 유지"). PLAN 단계에서 시계열 vs 단일 시점 최종 확정 |
| PRSF 가압장의 `pump_interlock_p` 룰이 PWTF 정수지 룰과 동일한지 미결정 — 본 사이클은 시설 종류별 룰 분리 없이 진행, 향후 분리 필요 시 별도 사이클 | 미해결 | wtp-domain-expert 권고 |
| `tag_m.unit_cd` 허용값에 `%` 추가 여부 — 본 사이클에서 V8_5 (선택) 으로 추가 가정 | 가정 | wtp-glossary-manager 발견. PLAN 단계에서 도입 여부 확정 |
| `09. 배수지 모니터링` 엔드포인트 파라미터 — `facilityId` (정수지 ID) 1개 받아 자식 DWT 목록 + 측정값 조회로 가정 | 가정 | wtp-backend-engineer 발견. 인증 컨텍스트 테넌트 필터만 적용 시 시그니처 다름 |
| Valve 자식 컬럼 — RawData 만 사용 (skeleton 유지) 로 가정 | 가정 | DBA 권장. RawData 만으로 화면 §3 밸브 개도율 표출 충분 |
| `docs/analyze/20260508/송수펌프_가동이력/` 의 docx 는 별개 작업 자료로 가정 — 본 사이클 미참고 | 가정 | 사용자 미답변. 동반 검토 필요 시 사용자 명시 후 별도 사이클 |
| 펌프별 예측 분해는 `pump_cmbn_d` 매핑 (옵션 B) — DB 변경 0건 | 가정 | wtp-dba-reviewer 권장. 화면이 단순 "펌프별 1시간 후 on/off" 표출이면 충분 |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `FacilityType.PRSF`·`TagMeasurementType.OPS·VOI` enum 추가 후 빌드 통과 | `./gradlew :common:build` BUILD SUCCESSFUL |
| `PressureBoosterStation` 자식 엔티티 + JPA JOINED 통합 테스트 통과 | 신규 테스트 `PressureBoosterStationTest` 1건 GREEN — DiscriminatorValue 매핑 검증 |
| 자식 컬럼 마이그레이션 SQL 4건 (V8_1~V8_4) 무중단 적용 + COMMENT 검증 | `psql \d+ pump_m`·`\d+ dwt_m`·`\d+ prsf_m`·`\d+ pump_predc_h` 출력에 신규 컬럼 + COMMENT 모두 표시 |
| `findFacilitiesHavingDwtChild` Querydsl 메서드 — exists 서브쿼리 1회 SQL 생성 (N+1 없음) | p6spy 슬로우 쿼리 로그에서 단일 쿼리 확인 |
| `findFirstChildByParentIdAndType` Querydsl 메서드 — `disp_ord` ASC LIMIT 1 SQL 생성 | 통합 테스트에서 활성 정수지 자식 DWT 3건 중 disp_ord=1 행 1개만 응답 |
| `findLatestByTagSrlNos` Repository 메서드 — IN 절 배치 (N+1 회피) | 통합 테스트에서 태그 100개 입력 시 SQL 1회 실행 확인 |
| Controller `08`·`09` 신설 + Swagger 노출 | `/api/v3/api-docs` 에 `08. 송수펌프 제어 분석`·`09. 배수지 모니터링` Tag 표시 |
| §1~§7 7섹션 통합 dashboard 응답 200 OK + 모든 섹션 DTO 채워짐 (모킹 데이터) | 통합 테스트 `PumpAnalysisDashboardServiceTest` 시나리오 1건 GREEN — 정수지 1건·펌프 4건·DWT 2건·예측 1건 mock |
| 펌프별 예측 분해 (`pump_cmbn_d` 매핑) — 펌프 #1~#4 의 1시간 후 on/off 모두 응답 | 통합 테스트 `PumpPredictionMappingTest` 시나리오 1건 GREEN |
| 화면 §4 기준배수지 표출 — 활성 정수지 자식 DWT `disp_ord` ASC 1번째 DWT 의 `min_req_prsr` 1건만 응답 | 통합 테스트 `DwtStatusServiceTest.기준배수지_표시순서_1번` GREEN |
| `pump_predc_h.pwtf_id → facility_id` 정렬 후 06 dashboard·07 시계열 회귀 테스트 통과 | 기존 `PumpControlControllerTest`·`PumpDrvnStatusControllerTest` 전건 GREEN |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | read-only 모니터링 화면. 알람 임계값·전이 조건·복귀 조건 변경 없음. 단 안건 6 (Tag.io_cd) 가 `AlarmEvaluator` 의 태그 필터 로직과 연동되면 향후 영향 — 본 사이클은 영향 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | **해당** | 안건 1 (PRSF 가압장) 의 `pump_interlock_p` 룰이 시설 종류별로 분리 필요한지가 인터록 선행조건과 직결 — PLAN 단계 결정. 안건 5 (사용자 결정 2026-05-08 로 기준배수지 FK 폐기) 는 본 사이클 외 — 인터록 평가에 기준배수지 압력 사용 여부는 향후 reference 선택 메커니즘 도입 시 별도 사이클에서 점검 |
| AI 운전 모드 (`ot-integration.md §5`) | **해당** | 안건 1 (PRSF) 의 `ai_drvn_mod_p` 시설 단위 별도 행 분리 + 강제 전환 절차 (PWTF 와 동일) 가 운전 모드 정합. 화면 §2 표시 기준 (`ai_mode_cd` vs `ai_drvn_mod`) 결정은 사용자 결정 (2026-05-08) 으로 차후 태스크 이관 — 본 사이클은 두 컬럼 임시 노출 가정. 강제 전환 결과 표출 정확성은 차후 사이클 책임 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 본 화면이 read-only 라 `ai_drvn_mod_h`·`pump_ctrl_h` 행 추가 경로 없음. 자식 컬럼 마이그레이션 (V8_2~V8_4) 도 마스터 데이터 영역으로 이력 기록 의무 영역 외 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

> 본 ANALYZE 는 신규 표준 단어/표준 데이터 도메인/표준 용어 등록 0건 (Java enum 값만 추가). ROOT 어휘 사전 갱신 없음. backend 표준 용어 사전 갱신도 0건 (사용자 결정 2026-05-08 로 `reference_dwt_facility_id` 등록 폐기).

- [x] `swtp/backend/.claude/rules/ot-integration.md` — §3 결측 대체값 표에 `OPS` (펌프 가동상태 DI 신호, BAD QUALITY 시 즉시 격상) 행 추가
- [x] `swtp/backend/.claude/rules/ot-integration.md` — §3 결측 대체값 표에 `VOI` (밸브 개도율, Hold Last Value) 행 추가
- [x] `swtp/backend/.claude/rules/ot-integration.md` — §5 AI 운전 모드 강제 전환 정책에 PRSF 가압장 시설 단위 별도 평가 명시 (PWTF 와 동일 절차)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — 동의어/금지 패턴 표에 `pwtf_id` (deprecated, `facility_id` 사용) 폐기 이력 보강 (V8_4 정렬 후 명시)

---

## 산출물

- [계획안 (작성 예정)](../../../plan/20260508/송수펌프제어분석/PLAN1.md)
