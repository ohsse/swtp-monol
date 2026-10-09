---
status: approved
created: 2026-05-14
updated: 2026-05-14
---
# 송수펌프제어분석 4번 섹션 (배수지 DWT 현황 표출) — 도메인 분석

## 작업 배경

- 요청 요약: 송수펌프제어분석 화면의 §4 영역 (1번 섹션에서 활성화한 시설의 자식 배수지 DWT 들의 실시간 현황 표출) 신규 API 추가
- 표출 항목 (이미지 기반):
  - 배수지명 (예: 다산면·다산산단(생활)·성주통합·개포통합)
  - 유입유량계의 유입압력값 (PRI) + 유입유량값 (FRI)
  - 유출유량계의 유출유량값 (FRI)
  - 밸브 1+건의 개도율 (VOI)
  - 저수조(=DWT 자체) 안의 수위계 1+건의 수위값 (LEI)
- 외부 산출물: `image/송수펌프제어분석.png` (모노레포 루트 기준, 화면 mockup)
- 관련 선행 작업:
  - §3 (시설 단건 상태) — `docs/analyze/20260513/송수펌프제어분석-3번섹션/ANALYZE1.md` approved (2026-05-13). `GET /api/facility/{facilityId}/state` 완료
  - 마스터도메인설계 ANALYZE1 (2026-05-02/03) — facility/instrument JPA JOINED + DiscriminatorColumn 다형성 도입
  - 펌프조작유형 ANALYZE1 (2026-05-12) — `oprtng_type_cd` enum 도입 + `type` 표준 단어 등록

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 응답 DTO 클래스명·변수명 어휘 정합성

- 호출 에이전트: `wtp-glossary-manager` (Round 1)
- Round 1 답변 요약:
  - **1-A. 컨테이너 DTO 명**: `ParentDwtStatesDto` 유사 충돌 — `Parent` 가 표준 단어 (DB 컬럼 조합 재료) 이므로 DTO 클래스명 토큰 전용은 오용. `States` 복수형은 §3 `FacilityStateDto` 단수 선례와 충돌. 권장: `DwtGroupStateDto`
  - **1-B. 자식 DWT DTO 명**: `DwtStateDto` 신규 적합 — `PumpStateDto`·`FlwmtrStateDto` 선례 `{비즈니스도메인 약어}StateDto` 패턴 완전 일관
  - **1-C. ValveStateDto / LvmtrStateDto**: 신규 적합 — `lvmtr`·`valve` 는 instrument 자식 종류 코드값이며 별도 비즈니스 도메인 약어 등록 불필요 (Pump 선례 동일)
  - **1-D. 유입/유출 변수명**: 권장 `inFlwmtr`/`outFlwmtr` — `io` 표준 단어 (2026-05-03 등록) 의 방향 접두어로 파생, 신규 단어 등록 불필요. `inlet`/`outlet`·`input`/`output` 옵션은 신규 단어 등록 부담
  - **1-E. 개도율 변수명**: PLAN 안 `voiRawVal` 유사 충돌 — `voi` 는 enum 코드, 표준 단어 아님. `flwrtRawVal`/`prsrRawVal` 선례 (표준 단어 기반) 와 층위 불일치. 권장: `opngRawVal` 계열 + **신규 표준 단어 `opng` (개도) 등록**
  - **1-F. 수위 변수명**: `wtlvRawVal` 계열 기존 재사용 — `wtlv` 표준 단어 등재됨 (2026-04-25)
- **결론**:
  - 컨테이너 DTO: `DwtGroupStateDto` 채택
  - 자식 DTO: `DwtStateDto`·`ValveStateDto`·`LvmtrStateDto` 채택
  - 변수명: `inFlwmtr`/`outFlwmtr`, `opngRawVal`/`opngCorrVal`/`opngAcqDtm`/`opngQualityCd`, `wtlvRawVal`/...
  - 신규 등록: `opng` 표준 단어 1건

### 안건 2: 도메인 정합성 (사용자 결정 4건 + ot-integration.md §3·§5)

- 호출 에이전트: `wtp-domain-expert` (Round 1)
- Round 1 답변 요약:
  - 본 API 는 순수 조회 — SCADA 아웃바운드·인터록 평가·AI 모드 변경에 미관여
  - `facility_type_cd = 'DWT'` 필터 경로로 자식 종류 혼선 없음 (`ot-integration.md §5` + `entity-patterns.md` JPA JOINED 도메인 룰 정합)
  - `rawdata_1m_h.quality_cd` 노출 — `ot-integration.md §3` GOOD/BAD/UNCERTAIN 3단계 정책과 일치
  - `corr_val` 별도 노출 — Hold Last Value 보정 결과를 frontend 가 선택 가능하도록 raw_val 과 분리 전달 (`api-patterns.md §직렬화 정책` 정합)
  - BIDIR io_cd: 한국 정수장 실무에서 진정한 양방향 유량계는 드물어 블로커 아님. 운영 등록 정책으로 단방향 우선 유도 권고
  - FLWMTR + PRI 복합 센서 모델링: 한국 정수장 표준은 별도 PRSMTR 이나 일부 복합 센서 제품 존재. `tag_m.instrument_id` FK 모델과 정합. "유입 압력" 의미 (단순 PRI vs 차압) 가정 명기 권고
  - LVMTR 다수 결측 시: 단건 필터 금지, 전체 목록 + quality_cd 노출 의무 (운전원 BAD 인지 보장)
  - raw/corr 이중 노출: backend 의무는 raw·corr·quality_cd 3값 노출까지. corr_val NULL 시 frontend raw_val fallback 정책은 SPEC 명기
- **결론**: 블로커 0건, 권고 0건, 참고 6건. PLAN 진입 가능. 도메인 4영역 모두 비해당. PLAN "## 가정 및 미해결 질문" 4건 명기 의무

### 안건 3: 응답 DTO 구조 + Backend 패턴 정합성

- 호출 에이전트: `wtp-backend-engineer` (Round 1)
- Round 1 답변 요약:
  - **블로커 3건**:
    - **B1. FlwmtrStateDto 재사용 시 PRI NULL 4필드 구조적 결함**: `entity-patterns.md §응답 DTO 매핑 패턴` 의 "자식 전용 필드를 부모 DTO 에 instanceof 분기로 채우는 방식 금지" 동일 안티패턴. PRI 4필드 NULL 채우는 방식은 부적합. 해결책: `InletFlwmtrStateDto` (FRI+PRI) / `OutletFlwmtrStateDto` (FRI) 분리
    - **B2. List<E> 의 @ArraySchema 누락**: `api-patterns.md §DTO @Schema(implementation) 명시 패턴` 의무. `valves`·`lvmtrs`·`dwts` 3건 모두 `@ArraySchema(schema = @Schema(implementation = ...))` 명시 필수
    - **B3. 참조형 DTO 필드의 @Schema(implementation) 누락**: `inFlwmtr`·`outFlwmtr` 사용자 정의 참조형 DTO 필드는 `@Schema(description = "...", implementation = ...)` 명시 의무
  - **권고 2건**: Service 분리 근거 정량화 (PLAN 에 §3 메서드 수·줄 수 명기), `findDwtStates` 50줄 초과 시 헬퍼 분리 TASK 체크박스 의무
  - 참고 2건: `qualityCd` 필드 타입 확정 (String vs `QualityCode` enum), `acqDtm` `@JsonFormat` 명시 확인
  - `FacilityErrorCode.FACILITY_NOT_FOUND(404)` 재사용 — exception-patterns.md `httpStatus(int)` 규약 준수
- **결론**: B1·B2·B3 블로커 3건 해소 후 PLAN 진입. DTO 구조 재설계 — InletFlwmtrStateDto / OutletFlwmtrStateDto 분리 + 모든 사용자 정의 타입 필드에 implementation 명시

### 안건 4: 쿼리 성능·인덱스 정합성

- 호출 에이전트: `wtp-dba-reviewer` (Round 1)
- Round 1 답변 요약:
  - 5단계 IN 절 단일 쿼리 + 메모리 그룹화 — N+1 회피 (`query-tuning.md §2` 정합)
  - `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` — Step 5 DISTINCT ON 정렬 비용 Index Scan 으로 흡수 (§3 검증 패턴 재사용)
  - **권고 2건 (신규 복합 인덱스)**:
    - **R1. Step 2 용**: `CREATE INDEX CONCURRENTLY idx_facility_m_parent_type_yn ON facility_m (parent_facility_id, facility_type_cd, use_yn);` — 현 `(facility_type_cd, parent_facility_id)` 는 선두 저카디널리티. Step 2 등가 3조건 모두 커버하는 인덱스 추가
    - **R2. Step 3 용**: `CREATE INDEX CONCURRENTLY idx_instrument_m_facility_equip ON instrument_m (facility_id, equip_type_cd);` — 현 UNIQUE `(facility_id, instrument_nm)` 은 `equip_type_cd IN (...)` 필터 비커버
  - 참고: Step 4 `(instrument_id, use_yn)` 복합 인덱스 (`use_yn` 카디널리티 2 라 후위 배치, 행 수 소량이면 실익 낮음), 파티션 프루닝 운영 검증 (EXPLAIN ANALYZE 권고)
  - 신규 DB 컬럼·표준 데이터 도메인 0건
- **결론**: 블로커 0건. 권고 인덱스 2건은 PLAN 단계에서 추가 여부 결정 (구현 시 V9_x 마이그레이션 SQL 1건 추가). 안건 4 와 무관하게 §3 의 4-step 패턴 재사용 안전

## 표준 사전 카탈로그

### 신규 표준 단어

(DB 컬럼 조합의 재료 — 의미의 최소 단위. 1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `opng` | 개도 | 신규 | `standard-words.md` 미등록. `voi`(enum 코드)·`open`·`rate` 대안 대비 swtp 4-5자 약어 컨벤션(`prsr`·`flwrt`·`elpwr`) 정합. 응답 DTO 변수명 `opngRawVal` 계열에 사용. 향후 `valve_m` 개도율 컬럼 도입 시 컬럼 조합 재료 재사용 |

### 신규 표준 데이터 도메인

없음 (DB 컬럼 신설 0건, 본 작업은 응답 DTO 변수명만 신규)

### 신규 표준 용어

없음 (응답 DTO 변수명은 backend 표준 용어 사전 SSOT 적용 범위 외 — Java camelCase 필드)

## 신규 엔티티/DB 컬럼

없음 (본 작업은 신규 엔티티·DB 컬럼·테이블·마이그레이션 0건)

- 재사용 엔티티: `Facility`·`DistributionWaterTank`·`Instrument`·`FlowMeter`·`Valve`·`LevelMeter`·`Tag`·`RawData` (모두 기존)
- 재사용 Repository:
  - `FacilityRepository.findByParentFacilityIdAndFacilityTypeAndUseYnOrderByDispOrdAsc(parentId, DWT, Y)`
  - `InstrumentCustomRepository.findByFacilityIdInAndEquipType(facilityIds, equipTypes)`
  - `TagRepository.findByInstrumentInstrumentIdInAndUseYn(instIds, Y)`
  - `RawDataCustomRepository.findLatestByTagSrlNos(tagSrlNos)`
- 권고 신규 인덱스 2건 (R1·R2, PLAN 단계 도입 결정):
  - `idx_facility_m_parent_type_yn (parent_facility_id, facility_type_cd, use_yn)`
  - `idx_instrument_m_facility_equip (facility_id, equip_type_cd)`

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 해소책 (회의 결론) |
|---------|-----------------|
| PLAN 안 컨테이너 DTO `ParentDwtStatesDto` 명명 | `DwtGroupStateDto` 로 정정 — `Parent` 표준 단어 클래스명 전용 회피, §3 `FacilityStateDto` 단수 선례 정렬 |
| PLAN 안 유입/유출 변수명 `inletFlwmtr`/`outletFlwmtr` | `inFlwmtr`/`outFlwmtr` 로 정정 — `io` 표준 단어 방향 접두어 파생, 신규 단어 등록 회피 |
| PLAN 안 개도율 변수명 `voiRawVal` 계열 | `opngRawVal` 계열로 정정 — `voi` (enum 코드) vs `opng` (표준 단어) 층위 정렬. `flwrt`/`prsr`/`wtlv` 선례 정합 |
| PLAN 안 FlwmtrStateDto 재사용 (유출 시 PRI NULL) | `InletFlwmtrStateDto` (FRI+PRI) / `OutletFlwmtrStateDto` (FRI) 분리 — `entity-patterns.md §응답 DTO 매핑 패턴` 의 NULL 분기 안티패턴 회피 |
| PLAN 안 사용자 정의 DTO 참조형·List 필드의 Swagger 어노테이션 부재 | 모든 List<E> 에 `@ArraySchema(schema = @Schema(implementation = E.class))` 명시, 참조형 DTO 필드에 `@Schema(description = "...", implementation = ...)` 명시 — `api-patterns.md §DTO @Schema(implementation) 명시 패턴` 의무 |

## PLAN 으로 전달할 결정 사항

### 응답 DTO 구조 (회의 종합 후 최종)

```
DwtGroupStateDto (컨테이너)
├ parentFacilityId : String
├ parentFacilityNm : String
└ dwts : List<DwtStateDto>  -- @ArraySchema
    └ DwtStateDto
       ├ facilityId : String
       ├ facilityNm : String
       ├ inFlwmtr : InletFlwmtrStateDto  -- @Schema(implementation=InletFlwmtrStateDto)
       │   └ instrumentId, instrumentNm
       │     flwrtRawVal/flwrtCorrVal/flwrtAcqDtm/flwrtQualityCd (FRI)
       │     prsrRawVal/prsrCorrVal/prsrAcqDtm/prsrQualityCd (PRI)
       ├ outFlwmtr : OutletFlwmtrStateDto  -- @Schema(implementation=OutletFlwmtrStateDto)
       │   └ instrumentId, instrumentNm
       │     flwrtRawVal/flwrtCorrVal/flwrtAcqDtm/flwrtQualityCd (FRI)
       ├ multipleInFlwmtrDetected : boolean
       ├ multipleOutFlwmtrDetected : boolean
       ├ valves : List<ValveStateDto>  -- @ArraySchema
       │   └ instrumentId, instrumentNm
       │     opngRawVal/opngCorrVal/opngAcqDtm/opngQualityCd (VOI)
       └ lvmtrs : List<LvmtrStateDto>  -- @ArraySchema
           └ instrumentId, instrumentNm
             wtlvRawVal/wtlvCorrVal/wtlvAcqDtm/wtlvQualityCd (LEI)
```

- `BaseAuditResponseDto` 미상속 (api-patterns.md 적용 범위 표 "실시간 통지 응답 DTO" 적용 외)
- `acqDtm` (LocalDateTime) 필드 `@JsonFormat(shape = STRING, pattern = "yyyy-MM-dd HH:mm:ss")` 초 단위 SSOT
- `qualityCd` 필드 타입은 PLAN 단계 확정 (§3 의 `QualityCode` enum 재사용 권장 + `@Schema(implementation = QualityCode.class)`)

### 신규 자바 클래스 (7건)

- `com.mo.swtp.facility.dto.DwtGroupStateDto` (컨테이너)
- `com.mo.swtp.facility.dto.DwtStateDto`
- `com.mo.swtp.facility.dto.InletFlwmtrStateDto`
- `com.mo.swtp.facility.dto.OutletFlwmtrStateDto`
- `com.mo.swtp.facility.dto.ValveStateDto`
- `com.mo.swtp.facility.dto.LvmtrStateDto`
- `com.mo.swtp.facility.service.DwtStateService`

### 수정 자바 클래스 (1건)

- `com.mo.swtp.facility.web.FacilityController` — `findDwtStates(parentFacilityId)` 메서드 추가

### 적용할 패턴

- §3 의 4-step 체이닝 + 메모리 그룹화 (N+1 회피)
- PostgreSQL DISTINCT ON + 1시간 파티션 프루닝 (`RawDataCustomRepository.findLatestByTagSrlNos` 재사용)
- 클래스 레벨 `@Transactional(readOnly = true)` + `@RequiredArgsConstructor`
- 상수: `DWT_EQUIP_TYPES = List.of(FLWMTR, VALVE, LVMTR)`, `DWT_TAG_TYPES = EnumSet.of(FRI, PRI, VOI, LEI)`
- 매핑 헬퍼 private 메서드 분리 (50줄 초과 회피): `mapInletFlwmtr`·`mapOutletFlwmtr`·`mapValve`·`mapLvmtr`

### 권고 인덱스 도입 (PLAN 단계 최종 결정)

- R1: `idx_facility_m_parent_type_yn (parent_facility_id, facility_type_cd, use_yn)` — V9_x 마이그레이션 SQL 신규 1건
- R2: `idx_instrument_m_facility_equip (facility_id, equip_type_cd)` — 동일 SQL 에 통합

도입 결정 시 `CREATE INDEX CONCURRENTLY` 사용 + 컬럼 COMMENT 정책 (`indexing-and-migration.md §4`) 적용

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| `Tag.io_cd` BIDIR 태그는 유입 후보로 취급 (INPUT 또는 BIDIR → 유입 후보, OUTPUT 또는 BIDIR → 유출 후보) | 가정 | 한국 정수장 실무에서 진정한 양방향 유량계는 드물어 운영 등록 정책으로 단방향 우선 유도. PLAN 단계 재검토 |
| "유입 압력" 의 의미는 단순 PRI(파이프 내압) — 차압 (델타 P) 아님 | 가정 | 유량계 원리상 차압이 사용될 수 있으나 표출 라벨은 단순 압력으로 가정. PLAN 단계 frontend SPEC 협의 시 재확정 |
| 한 DWT 에 유입 FLWMTR 2건 이상 등록 시 첫 매치만 사용 + `multipleInFlwmtrDetected = true` 플래그 응답에 노출 (유출 동일) | 결정 | wtp-domain-expert 권고 — 운전원이 화면에서 다중 등록 인지 가능. WARN 로그 + 응답 플래그 이중 안전망 |
| 수위계 (LVMTR) 는 단건 필터 금지, 전체 목록 + 각 측정값 `qualityCd` 노출 의무 | 결정 | wtp-domain-expert 권고 — BAD/UNCERTAIN 수위계 인지 보장. 단건 표출 정책은 frontend 책임 |
| `corr_val` NULL 시 frontend 가 `raw_val` fallback — backend 의무 영역 외, SPEC 명기로 전파 | 결정 | api-patterns.md §직렬화 정책 — backend 가 정밀도를 낮추지 않는다 |
| `qualityCd` 응답 필드 타입: §3 의 `QualityCode` enum 재사용 (`@Schema(implementation = QualityCode.class)`) | 가정 | PLAN 단계에서 §3 의 RawDataLatestDto 의 qualityCd 필드 타입 그대로 정렬 |
| `multipleInFlwmtrDetected` / `multipleOutFlwmtrDetected` 응답 플래그 boolean 노출 (운전원 인지 보장) | 결정 | wtp-domain-expert 권고 + Backend Engineer Service 분리 권고 절충 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 임계값·전이 조건·복귀 조건 변경 없음. 알람 이력 INSERT 없음. 순수 조회 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 발행 경로 미진입. 인터록 선행조건 검사 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 변경 없음. SCADA 5분 초과 강제 전환 평가 미수행. 본 API 는 모드 정보를 조회·표출하지 않음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h` / `pump_ctrl_h` INSERT 없음. 본 API 는 신규 이력 행 추가 없음 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `DwtStateServiceTest.findDwtStates_정상응답` 통과 | `./gradlew.bat :api:test --tests DwtStateServiceTest` 정상 시나리오 (DWT 2건 + 각 inlet/outlet/valve/lvmtr 보유) GREEN |
| `DwtStateServiceTest.findDwtStates_부모미존재_FACILITY_NOT_FOUND` 통과 | 위 명령 의 예외 시나리오 GREEN + RestApiException errorCode == FACILITY_NOT_FOUND assertion |
| `DwtStateServiceTest.findDwtStates_자식DWT0건_빈리스트` 통과 | 응답 dwts == empty List + 200 OK |
| Swagger UI 노출 + DTO 스키마 정합성 | `./gradlew.bat :api:bootRun` 후 `/swagger-ui/index.html` 에서 GET /api/facility/{parentFacilityId}/dwts/states 노출 + DwtGroupStateDto 스키마 + 모든 List/참조형 필드의 implementation 명시 확인 |
| SQL 발행 4회 이하 (Facility + Instrument + Tag + RawData) | 단위 테스트 Hibernate Statistics 또는 spring.jpa.show-sql 카운트 |
| `DwtStateServiceTest.findDwtStates_유입유출중복등록_플래그TRUE` 통과 | DWT 1건에 유입 FLWMTR 2건 등록 fixture → 응답에 multipleInFlwmtrDetected = true + 첫 매치만 inFlwmtr 채워짐 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `opng` (개도, opening) 신규 등록. 등록 형식: 본 사이클 결정 (송수펌프제어분석-4번섹션 ANALYZE1, 2026-05-14) 명시 + 기본 데이터 도메인 `(조합)` + 비고 "응답 DTO 변수명 `opngRawVal` 계열 사용. 향후 `valve_m` 개도율 컬럼 도입 시 컬럼 조합 재료 재사용. `voi`(TagMeasurementType enum 코드) 와 층위 분리"

## 산출물

- [계획안](../../../plan/20260514/송수펌프제어분석-4번섹션/PLAN1.md) — 작성 예정 (`/dev:plan` 단계)
