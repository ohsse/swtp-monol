---
status: approved
created: 2026-05-18
updated: 2026-05-18
---
# 송수펌프제어분석 6번섹션 (시설별 토출관압 + 운전중 펌프 대수 요약) — 도메인 분석

## 작업 배경

송수펌프제어분석 화면 `swtp/backend/image/송수펌프제어분석.png` 의 **6번 섹션**(송수펌프 제어 박스)을 backend 에서 표출한다. 6번섹션은 **1번섹션 시설목록**(hasPump=true 활성 펌프 시설 전체)의 시설별 다음 2개 데이터를 표출한다.

| 표시 항목 | 데이터 출처 |
|---------|----------|
| 토출관압 | 각 시설 유량계(FLWMTR)의 PRI(압력) 태그 최신 측정값 |
| 현재 운영중인 펌프 대수 | 각 시설 펌프(PUMP)의 OPS(가동상태) 최신값이 ON 인 대수 |

핵심 제약: 1번섹션에서 **어떤 시설을 활성화했는지와 무관하게** 6번섹션은 시설목록 전체의 최신값을 항상 유지한다. 따라서 단일 선택 시설에 종속된 3·4번섹션 엔드포인트(`/{facilityId}/state`, `/{parentFacilityId}/dwts/states`)와 달리 **목록 집계형 신규 전용 엔드포인트**가 필요하다.

**외부 산출물**: `swtp/backend/image/송수펌프제어분석.png` (6번 박스 시각 명세).

**사용자 확정 결정 (Phase 1, plan 모드)**:
1. 토출관압 = **FLWMTR 유량계의 PRI** 태그 최신값 (3번섹션 PRI 처리와 동일 출처)
2. 1번섹션 시설목록 범위 = **hasPump=true 활성 펌프 시설 전체** (`GET /api/facility?hasPump=true` 와 동일 조건, 선택 시설 무관)
3. 엔드포인트 = **신규 전용 엔드포인트 + Service + DTO 신설** (이전 섹션 자산 자동 재사용 금지 정책 — 공유 인프라만 재사용)
4. 펌프 대수 = **운전중 펌프 대수만** (OPS rawVal=1.0 카운트, 전체 펌프 대수 미포함)

**선행 섹션 자산 (재사용 대상은 공유 인프라만)**:
- 1번섹션: `FacilityCustomRepository.findFacilities(hasPump=true)` — `instrument_m equip_type_cd=PUMP AND use_yn=Y` EXISTS 필터 (송수펌프_시설목록, 2026-05-13)
- 3번섹션: `FacilityStateService` 단일 시설 4-step (커밋 2026-05-13)
- 4번섹션: `DwtStateService` 부모-자식 N건 5-step IN + 메모리 그룹화 + 다중 등록 첫 매치+플래그 패턴 (커밋 2026-05-14)
- 공유 인프라: `InstrumentCustomRepository.findByFacilityIdInAndEquipType`, `TagRepository.findByInstrumentInstrumentIdInAndUseYn`, `RawDataRepository.findLatestByTagSrlNos` (rawdata_1m_h DISTINCT ON + 1시간 윈도우)

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 신규 전용 응답 DTO 구조 + Backend 패턴 정합성

- 호출 에이전트: `wtp-backend-engineer` (필수)
- Round 1 답변 요약:
  - **wtp-backend-engineer**: (1) 응답 구조는 **컨테이너 래퍼 DTO 미도입**, `CommonResponseDto<List<PumpSummaryDto>>` 직접 반환 권고 — 래퍼 추가는 `coding-discipline.md §2` "일회성 추상화 계층 금지" 위반. `BaseAuditResponseDto` 미상속 (실시간 통지성, `api-patterns.md §적용 범위` 정합). (2) 신규 `PumpSummaryService` 신설 정당 — `DwtStateService` 에 펌프 운전 현황 요약 책임 추가는 SRP 위반. hasPump 시설 목록은 기존 `findFacilities(hasPump=true)` 재사용 권고 (중복 쿼리 신설은 단순성 역행). (3) §4 `assembleDwtState`/`mapInletFlwmtr` 선례처럼 단일 시설 매핑 단위 private 헬퍼 분리, 추상화 2단 이내 유지. `List<PumpSummaryDto>` Controller 반환 시 `@ArraySchema(schema=@Schema(implementation=PumpSummaryDto.class))` 명시 의무. (4) **블로커 1건**: 집계 메서드 50줄 초과 시 `// §2.5 면책 (db/query-tuning.md §2)` 주석 인용 누락 시 REVIEW 블로커 — PLAN 예방 조치 필요. **권고 2건**: 래퍼 DTO 미도입, prsr 값 필드 평탄 vs 중첩 결정 PLAN 명시. **참고 1건**: `@ArraySchema` 명시.
- Round 2: 불필요.
- **결론**: 신규 `PumpSummaryService` + row DTO `PumpSummaryDto` 신설. 컨테이너 래퍼 미도입 — `CommonResponseDto<List<PumpSummaryDto>>` 직접 반환 + `@ArraySchema` 명시. `findFacilities(hasPump=true)` 재사용. 집계 메서드 50줄 초과 시 §2.5 면책 인용 PLAN 의무화. prsr 값 필드 구조(평탄 vs 중첩) PLAN 결정 사항으로 가정 섹션 명기.

### 안건 2: 신규 엔드포인트 경로 + DTO 클래스명·변수명 어휘 정합성

- 호출 에이전트: `wtp-glossary-manager` (필수)
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 표준 단어·데이터 도메인·표준 용어·비즈니스 도메인 약어 **0건** (모든 조합 단어 기존 등재). 경로 토큰 `pump` 는 비즈니스 도메인 약어 폐기(2026-05-12) 영향이 **패키지명·DB prefix 한정** 이며 REST 경로/응답 필드는 무관 (`equip_type_cd='PUMP'` 코드값 보존과 동일 맥락). `summary` 토큰은 집계 요약 의미로 `state`(단순 상태값)보다 정합. DTO 클래스명 `PumpSummaryDto`·어휘 `Summary` 권고. 토출관압 변수명 `prsr*` 계열 (`prsrRawVal`/`prsrCorrVal`/`prsrAcqDtm`/`prsrQualityCd`) 기존 재사용 적합. 운전중 펌프 대수 변수명 `oprtngPumpCnt` (Integer) 권고 — `pump_oprtng_cnt` DB 용어 폐기 이력은 응답 DTO camelCase 필드에 구속 없음 (§4 ANALYZE1 결론 "응답 DTO 변수명은 표준 용어 사전 SSOT 적용 범위 외"). 룰 갱신 지시서 없음.
  - **오케스트레이터 정정 (사용자 확정사항 중계)**: glossary-manager 가 §3 path variable 선례를 보고 "단일 시설의 단일 평면 응답" 으로 범위를 추정했으나, 사용자 확정 결정 2·3 에 따라 6번섹션은 **hasPump=true 시설 전체 목록 집계** (선택 시설 무관). 따라서 엔드포인트는 path variable 없는 컬렉션 엔드포인트 `GET /api/facility/pump-summary`, 응답은 `List<PumpSummaryDto>` (시설 N건). glossary-manager 의 클래스명·변수명 권고(`PumpSummaryDto`·`prsr*`·`oprtngPumpCnt`)는 유효하되 "단일 평면 DTO" 전제만 정정 — `PumpSummaryDto` 는 시설 1건 = row DTO 이며 N건 List 반환.
- Round 2: 불필요 (이견은 에이전트 충돌이 아닌 범위 오해 — 오케스트레이터 중계 정정으로 종결).
- **결론**: 엔드포인트 `GET /api/facility/pump-summary` (path variable 없음, 시설 전체 목록). row DTO `PumpSummaryDto`. 토출관압 `prsr*` 재사용, 운전중 대수 `oprtngPumpCnt`. 신규 어휘 0건 — 룰 갱신 지시서 없음.

### 안건 3: 토출관압 집계 규칙 + 운전중 펌프 대수 OPS 결측/BAD 처리

- 호출 에이전트: `wtp-domain-expert` (필수)
- Round 1 답변 요약:
  - **wtp-domain-expert**: (3-1 토출관압) §4 선례 (첫 매치 + 다중검출 플래그 + WARN 로그) 적용 권고 — 대표 지정 테이블은 DB 변경 0건 제약 위반, 전체 목록 표출은 요구 범위 초과. "한 시설 활성 FLWMTR 1대 + PRI 태그 1개" 정상 구성 가정 + 다중 시 첫 매치 + 플래그 + WARN. **가정 섹션 기재 필수**. (3-2 운전중 대수) **블로커**: OPS qualityCd=BAD/UNCERTAIN 또는 1시간 윈도우 결측 펌프를 운전중 카운트에 포함하면 `ot-integration.md §3` OPS 즉시 BAD 격상 정책(통신 단절 시 정지 펌프가 ON 표시 운전원 오인 방지) 을 우회한다. `oprtngPumpCnt` = OPS rawVal=1.0 **AND qualityCd=GOOD** 만 카운트 + `unknownPumpCnt` (BAD/UNCERTAIN/결측 펌프 수) 별도 응답 필드 의무. UNCERTAIN 을 운전중 포함은 §3 가중치 0.5 정책과 혼동되므로 미채택.
- Round 2: 불필요.
- **결론**: 토출관압은 §4 선례 (시설별 첫 매치 FLWMTR 의 PRI + `multiplePrsrDetected` 플래그 + WARN 로그). 운전중 대수는 `oprtngPumpCnt` (OPS rawVal=1.0 AND qualityCd=GOOD) + `unknownPumpCnt` (OPS BAD/UNCERTAIN/결측 펌프 수) 2필드 — `ot-integration.md §3` 정합 유일 구현. **사용자 확정 4(운전중 대수만)에 대한 도메인 안전 보강** — `unknownPumpCnt` 는 "전체 펌프 대수"가 아닌 "신뢰불가 펌프 대수"로 운전원 오인 방지 필수. 사용자 승인 시점에 명시 제시.

### 안건 4: 쿼리 성능·인덱스 정합성

- 호출 에이전트: `wtp-dba-reviewer` (필수)
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: §4 ANALYZE1 권고 인덱스 R1 `idx_facility_m_parent_type_yn`·R2 `idx_instrument_m_facility_equip` 모두 `common/src/main/resources/db/migration/V9_2__facility_instrument_lookup_indexes.sql` 에 `CREATE INDEX CONCURRENTLY IF NOT EXISTS` 로 **기 도입 완료**. `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)`·`idx_rawdata_1m_h_brin` 은 `V6_5__rawdata_1m_h.sql` 존재. rawdata_1m_h DISTINCT ON + `ANY(?)` + `acq_dtm >= NOW() - INTERVAL '1 hour'` 기존 인덱스 완전 커버 + 파티션 프루닝 정상. 최신 마이그레이션 `V9_2` — 신규 컬럼·테이블·인덱스 0건이므로 **본 사이클 신규 마이그레이션 SQL 없음**. **블로커 0·권고 0·참고 2건**: (참고1) Step 1 hasPump EXISTS 의 `use_yn=Y` 가 R2 인덱스 미커버 — facility_m 소량(수십~수백) 시 Seq Scan 유리, 실측 없이 인덱스 추가 금지, PLAN 성공 기준에 `EXPLAIN (ANALYZE, BUFFERS)` 검증 명시로 충분. (참고2) 시설 수 향후 수백 초과 시 IN 절 청크 분할 검토 — PLAN 가정 "시설 수 상한 미정" 명기 권고.
- Round 2: 불필요.
- **결론**: DB 스키마 변경 0건, 신규 마이그레이션 SQL 없음. 기존 인덱스 4종(R1·R2·tag_time·brin) 전원 도입 완료로 §4 검증 패턴 그대로 재사용 안전. PLAN 성공 기준에 EXPLAIN 검증 + 시설 수 상한 미정 가정 명기.

### 안건 5: 도메인 룰 4영역 점검

- 호출 에이전트: `wtp-domain-expert` (필수, 안건 3 통합 답변)
- Round 1 답변 요약:
  - **wtp-domain-expert**: 4영역 모두 비해당 — 순수 조회 API, DB INSERT/UPDATE 0건. "비해당" 단독 4건 차단 해제 2조건(각 행 구체 사유 + "## 신규 엔티티/DB 컬럼" 없음) 충족. REVIEW 블로커 없음.
- Round 2: 불필요.
- **결론**: 도메인 4영역 모두 비해당 (아래 §도메인 룰 4영역 점검 표 참조).

---

## 표준 사전 카탈로그

### 신규 표준 단어

없음 — 모든 조합 단어 (`prsr`·`raw`·`corr`·`acq`·`quality`·`oprtng`·`cnt`·`pump`) 기존 등재. `wtp-glossary-manager` 확인 완료.

### 신규 표준 데이터 도메인

없음 — 신규 DB 컬럼·테이블 0건.

### 신규 표준 용어

없음 — 응답 DTO·엔드포인트 전용, DB 컬럼 신규 0건. 응답 DTO camelCase 필드명은 backend 표준 용어 사전 SSOT 적용 범위 외 (§4 ANALYZE1 결론).

분류값 요약: 신규 0건 / 기존 재사용 8건 / 유사 충돌 0건 / 폐기·통합 0건

---

## 신규 엔티티/DB 컬럼

없음 — 본 작업은 신규 엔티티·DB 컬럼·테이블·마이그레이션 0건. DB 스키마 변경 없음 (`wtp-dba-reviewer` 안건 4 결론).

- 재사용 엔티티: `Facility`·`Instrument`·`Pump`·`FlowMeter`·`Tag`·`RawData` (모두 기존)
- 재사용 Repository:
  - `FacilityRepository.findFacilities(FacilitySearchDto)` — hasPump=true EXISTS 필터 (1번섹션 자산)
  - `InstrumentCustomRepository.findByFacilityIdInAndEquipType(facilityIds, equipTypes)`
  - `TagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y)`
  - `RawDataCustomRepository.findLatestByTagSrlNos(tagSrlNos)`
- 재사용 인덱스 (기 도입 완료, 신규 0건): `idx_facility_m_parent_type_yn`·`idx_instrument_m_facility_equip` (V9_2)·`idx_rawdata_1m_h_tag_time`·`idx_rawdata_1m_h_brin` (V6_5)

---

## 기존 사전·패턴과의 충돌

| 충돌/이견 항목 | 해소책 (회의 결론) |
|---------|-----------------|
| `wtp-glossary-manager` 가 6번섹션을 "단일 시설 path variable + 단일 평면 DTO" 로 범위 오해 | 사용자 확정 결정 2·3 으로 정정 — hasPump=true 시설 전체 목록 집계. 엔드포인트 path variable 없는 컬렉션 `GET /api/facility/pump-summary`, 응답 `List<PumpSummaryDto>`. 클래스명·변수명 권고는 유효 (오케스트레이터 중계 정정) |
| 사용자 확정 4 (운전중 펌프 대수만) ↔ `wtp-domain-expert` 블로커 (`unknownPumpCnt` 추가 의무) | `unknownPumpCnt` 는 "전체 펌프 대수"가 아닌 "신뢰불가(BAD/UNCERTAIN/결측) 펌프 대수" — `ot-integration.md §3` OPS 즉시 BAD 격상 정책 정합 유일 구현. 사용자 확정 4 와 모순 아님 (운전원 오인 방지 안전 보강). 사용자 승인 시점 명시 제시 |
| `PumpSummaryListDto` 컨테이너 래퍼 도입 안 | `wtp-backend-engineer` 권고 — 미도입, `CommonResponseDto<List<PumpSummaryDto>>` 직접 반환 (`coding-discipline.md §2` 일회성 추상화 금지) |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안
- 신규 엔티티·DB 컬럼 0건. 기존 도메인 모델 재사용만.

### 응답 DTO 구조 (회의 종합)

```
CommonResponseDto<List<PumpSummaryDto>>
└ List<PumpSummaryDto>  -- @ArraySchema(schema=@Schema(implementation=PumpSummaryDto.class))
    └ PumpSummaryDto  (시설 1건 = row, hasPump=true 시설 N건)
       ├ facilityId : String
       ├ facilityNm : String
       ├ 토출관압 (FLWMTR 첫 매치의 PRI 최신값)
       │   prsrRawVal / prsrCorrVal / prsrAcqDtm / prsrQualityCd
       │   (평탄 4필드 vs 중첩 PrsrValueDto 는 PLAN 결정 — 가정 섹션)
       ├ multiplePrsrDetected : boolean  (시설 FLWMTR/PRI 다중 등록 시 첫 매치 + true + WARN 로그)
       ├ oprtngPumpCnt : Integer  (OPS rawVal=1.0 AND qualityCd=GOOD 카운트)
       └ unknownPumpCnt : Integer  (OPS qualityCd=BAD/UNCERTAIN 또는 결측 펌프 수)
```

- `BaseAuditResponseDto` 미상속 (실시간 통지성)
- `acqDtm` (LocalDateTime) `@JsonFormat(shape=STRING, pattern="yyyy-MM-dd HH:mm:ss")` 초 단위 SSOT
- `prsrQualityCd` 타입은 §3 `QualityCode` enum 재사용 + `@Schema(implementation=QualityCode.class)`

### 신규 자바 클래스 (2건)
- `com.mo.swtp.facility.dto.PumpSummaryDto` (신규)
- `com.mo.swtp.facility.service.PumpSummaryService` (신규)

### 수정 자바 클래스 (1건)
- `com.mo.swtp.facility.web.FacilityController` — `GET /api/facility/pump-summary` 엔드포인트 메서드 1개 추가

### 적용할 패턴
- §4 4-step IN 절 단일 쿼리 + 메모리 집계 (N+1 회피). 기점만 `findFacilities(hasPump=true)` 로 변경
- `RawDataCustomRepository.findLatestByTagSrlNos` + 1시간 파티션 프루닝 재사용
- 클래스 레벨 `@Transactional(readOnly=true)` + `@RequiredArgsConstructor`
- 상수: `SUMMARY_EQUIP_TYPES = List.of(PUMP, FLWMTR)`, `SUMMARY_TAG_TYPES = EnumSet.of(PRI, OPS)`
- 시설별 매핑 private 헬퍼 분리 (50줄 초과 회피). 50줄 초과 시 `// §2.5 면책 (db/query-tuning.md §2)` 주석 인용 의무
- `List<PumpSummaryDto>` Controller 반환 `@ArraySchema(schema=@Schema(implementation=PumpSummaryDto.class))` 명시

### Service 분리/통합
- `PumpSummaryService` 신규 신설 (SRP — `DwtStateService`/`FacilityStateService` 와 책임 분리). 신규 Repository 메서드 0건 (전부 기존 재사용).

### frontend SPEC 전파
- `/dev:spec` 단계에서 슬러그 결정 (송수펌프제어분석-6번섹션 신규 SPEC1 vs 기존 슬러그 누적) — 사용자 확인 사항

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 한 시설에 활성 FLWMTR 다수 또는 한 FLWMTR 에 PRI 태그 다수 등록 시 첫 매치 사용 + `multiplePrsrDetected=true` + WARN 로그 — 대표 태그 지정 테이블 미도입. 정상 구성은 시설당 토출관압 FLWMTR 1대 + PRI 1개 | 가정 | `wtp-domain-expert` 안건 3-1 결론. PLAN 단계 frontend 경고 UI 책임 SPEC 명기 |
| `unknownPumpCnt` 응답 필드 추가 — 사용자 확정 4(운전중 대수만)에 대한 도메인 안전 보강. OPS BAD/UNCERTAIN/결측 펌프를 운전중에 포함하면 `ot-integration.md §3` OPS 즉시 BAD 격상 정책 우회 | 결정 | `wtp-domain-expert` 블로커 흡수. **사용자 승인 시점 명시 제시 의무** — 거부 시 PLAN 재진입 |
| 토출관압 prsr 값 필드 구조 — 평탄 4필드(`prsrRawVal`/...) vs 중첩 `PrsrValueDto` | 미해결 | PLAN 단계 결정. `wtp-backend-engineer` 권고 — §3 평탄 선례 우선 검토 |
| 집계 메서드 50줄 초과 시 `// §2.5 면책 (db/query-tuning.md §2)` 주석 인용 의무 | 결정 | `wtp-backend-engineer` 블로커 — PLAN/구현 예방. 인용 누락 시 REVIEW 블로커 |
| hasPump 시설 수 상한 미정 — 수십~수백 전제. 수백 초과 시 IN 절 청크 분할 별도 검토 | 가정 | `wtp-dba-reviewer` 참고2. 본 사이클 범위 외 |
| frontend SPEC 슬러그 결정 | 미해결 | `/dev:spec` 단계 사용자 결정 |

분류값: 가정 / 미해결 / 결정

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `GET /api/facility/pump-summary` 가 hasPump=true 활성 펌프 시설 전체를 반환, 각 시설에 토출관압 prsr 필드 + `oprtngPumpCnt` + `unknownPumpCnt` 포함 | 신규 `PumpSummaryServiceTest` — 시설 N건 fixture 정상 응답 GREEN |
| 선택 시설과 무관하게 전체 목록 반환 (단일 시설 종속 없음) | `PumpSummaryServiceTest` — path variable 없음, hasPump 필터 시설만 응답 검증 |
| OPS qualityCd=BAD/UNCERTAIN/결측 펌프는 `oprtngPumpCnt` 제외 + `unknownPumpCnt` 카운트 | `PumpSummaryServiceTest` — OPS BAD fixture 시 oprtngPumpCnt 미증가 + unknownPumpCnt 증가 GREEN |
| 한 시설 FLWMTR/PRI 다중 등록 시 첫 매치 + `multiplePrsrDetected=true` + WARN 로그 | `PumpSummaryServiceTest` — FLWMTR 2건 fixture 시 플래그 true GREEN |
| SQL 발행 4회 이하 (Facility + Instrument + Tag + RawData) | 단위 테스트 SQL 카운트 / p6spy 로그 |
| 기존 3·4·5번섹션 엔드포인트 회귀 없음 | `./gradlew.bat :api:test` PASS |
| DB 스키마 변경 0건 | 신규 마이그레이션 SQL 부재 확인 |
| Step 1 hasPump EXISTS 쿼리 실행 계획 검증 | PLAN 성공 기준에 `EXPLAIN (ANALYZE, BUFFERS)` 항목 명시 (`wtp-dba-reviewer` 참고1) |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | `rawdata_1m_h` 최신값 조회 + 메모리 집계만 수행. 알람 임계값·전이·복귀 조건 무접촉, `alarm_h` INSERT 없음. OPS BAD 펌프 카운트 제외는 §3 품질 관리 정책 귀속이며 §5 알람 4단계와 별개 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 순수 조회 API. 제어 명령 발행 경로 미진입, `pump_interlock_p` 참조 0건. §5 ⚠️ 절대 금지(인터록 우회) 해당 경로 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 무접촉. SCADA 5분 초과 강제 전환 평가 미수행. 본 API 는 운전 모드 조회·표출하지 않음 (`oprtngType` 도 미노출 — 6번섹션은 토출관압 + 운전 대수만) |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 조회 전용 — `ai_drvn_mod_h`·`pump_ctrl_h` INSERT 0건. DB INSERT/UPDATE 0건 |

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

없음 — 신규 표준 단어·데이터 도메인·표준 용어·비즈니스 도메인 약어 4층 사전 모두 신규 등록 0건 (`wtp-glossary-manager` 안건 2 결론). 룰 갱신 체크박스 없음 → PLAN approved 전제조건 자동 충족.

---

## 산출물

- [계획안](../../../plan/20260518/송수펌프제어분석-6번섹션/PLAN1.md) (PLAN 단계 작성 예정)
