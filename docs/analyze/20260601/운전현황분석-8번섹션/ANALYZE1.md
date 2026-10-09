---
status: approved
created: 2026-06-01
updated: 2026-06-01
---
# 운전현황분석-8번섹션 — 도메인 분석

## 작업 배경

운전현황 분석 화면(`backend/image/운전현황분석.png`) **8번 섹션** 카드의 backend 조회 API 신설. 프론트의
**수요량 / 관압 / 수위 탭** 에 대해, 12번 섹션에서 활성화된 시설(`facilityId`)을 루트로 그 **재귀 하위**의
정수지(PWTF)·분기점(POINT) 을 표출 대상으로, 배수지(DWT) 를 수위 소스로 삼아 **금일 00:00 ~ 현재시간**
1분 단위 **계측(rawdata_1m_h) + 예측(predc_1m_h)** 시계열과 **포인트별 대비율** 을 응답한다.

- **DEMAND**(수요량) = 각 표출대상의 **송수(유출) 유량**(FLWMTR FRI) — 표출대상 시설당 1 시리즈
- **PRESSURE**(관압) = 각 표출대상의 **송수(유출) 수압**(FLWMTR PRI) — 표출대상 시설당 1 시리즈
- **LEVEL**(수위) = 표출대상의 자식 배수지(DWT) **수위계(LVMTR LEI)** — **수위계당 1 시리즈**
- **대비율** `ratio = 예측값 / 계측값 × 100` (포인트별, 계측 0·NULL 또는 예측 NULL → null), 소수 1자리

기존 4·5·7·9·10번 섹션은 모두 단일 시설(`{facilityId}` 직속) API 다. 8번은 처음으로 **상위 시설 1개 →
다수 재귀 하위 시설 fan-out** 하는 멀티 시설 시계열이라는 점이 다르다. 기존 섹션을 폐기/재설계하지 않고
**병렬 신규 추가** 한다 (사용자 메모리 "공존 섹션은 폐기 아님" 정합).

### 외부 산출물
- `backend/image/운전현황분석.png` — Read 도구 직접 시각 로드. 8번 섹션 = 수요량/관압/수위 탭 라인 차트(계측+예측).

### 사용자 확정 결정 (2026-06-01, plan 모드)
- **Q1 표출대상 도출 = 재귀 하위 전체** — 활성 루트의 모든 재귀 하위 중 `facility_type_cd ∈ {PWTF, POINT}` = 표출대상, `DWT` = 수위 소스
- **Q2 API 형태 = 단일 엔드포인트 + `dataType=DEMAND|PRESSURE|LEVEL`** 파라미터 (탭 = dataType)
- **Q3 대비율 = 포인트별** `예측/계측×100` (계측 0·NULL 또는 예측 NULL → null), 소수 1자리
- **Q4 시계열 범위 조회 리포지토리 = 공유 재사용** (raw/opt 범용 범위조회). Service·응답DTO·다중시설 도출만 신규

### ⚠️ dev DB 토폴로지 갭 (검증 환경 제약)
현재 dev DB 는 `정수조#1·#2(PWTF)` → `배수지#1~10(DWT)` **2단**이고 분기점(POINT)·수위계(LVMTR/LEI)
데이터가 **전무**하다. 8번은 고령정수장 모델(3단 이상) 기준이므로 dev DB 실측 검증 불가 →
**Mockito 단위 테스트(mock) 로 검증**한다. 스키마/엔티티는 이미 지원(`parent_facility_id` 재귀 self-FK,
`FacilityType.POINT`, `TagMeasurementType.LEI`, `LevelMeter`). 신규 DDL 0건.

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 표준 사전 정합성 — 신규 명명 후보 분류
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 7번 섹션 ANALYZE1 안건 1 결론 **완전 동형** — 엔드포인트 path·클래스명·DTO 변수명·enum 값은 **DB 컬럼 조합 재료가 아니므로 표준 사전 적용 외**. 신규 DB 컬럼·테이블·데이터 도메인 0건. `Section8DataType`/`DEMAND`·`PRESSURE`·`LEVEL`(enum), `ratio`·`series`·`dataType`(DTO 필드) 전부 **등록 불요**. `demand`·`ratio` 는 향후 DB 컬럼 설계 시점에 재검토(현 사이클 컬럼 미사용). 기존 재사용: `flwrt`(유량)·`prsr`(압력)·`wtlv`(수위) [DEMAND/PRESSURE/LEVEL 측정값 DB 단어 담당], `actl`·`predc`·`val`·`parent_facility_id`·`facility_nm`. **경로 단어 `downstream` 권장** — `branch` 는 표준 단어 기등록(분기점 의미 혼동), `tree` 는 비즈니스 의미 약함. **enum 명칭 `FacilityDownstreamDataType` 권장** (`{비즈니스컨텍스트}Type` — `FacilityOperatingStatusCompareType` 선례).
- **결론**: 표준 사전 갱신 **0건** (신규 단어 0 / 기존 재사용 3 [`flwrt`·`prsr`·`wtlv`] / 데이터 도메인 0 / 표준 용어 0). 경로 `downstream-time-series`, enum `FacilityDownstreamDataType`. 룰 갱신 지시서 체크박스 0건 — PLAN 진입 전제조건 자동 충족.

### 안건 2: 재귀 하위 조회 방식 + 리포지토리 재사용 선택 + 멀티 시설 SLA
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 신규 DDL·인덱스·데이터 도메인 0건. 발견 사항:
    - **블로커(높음) — 재귀 하위 조회**: **앱 레벨 BFS 채택 권장** (방식 b). `WITH RECURSIVE`(방식 a)는 `CYCLE` 절(PostgreSQL 14+) 미명시 시 `standard-terms.md` "재귀 깊이 제한 미정" 가정과 맞물려 **무한 루프 위험**. 앱 레벨 BFS 는 `parent_facility_id IN (...)` + `use_yn='Y'` + `facility_type_cd IN (PWTF,POINT,DWT)` 복합 조건으로 1레벨씩 확장하며 `idx_facility_m_parent_type_yn (parent_facility_id, facility_type_cd, use_yn)` 인덱스 활용, **Java 루프 카운터로 깊이 제한·순환 안전성 보장**. 실제 깊이 3단 초과 확인 시 `WITH RECURSIVE`+`CYCLE` 재검토 — PLAN 깊이 실측 가정 명기 의무.
    - **권고(중간) — 리포지토리 재사용**: `RawDataOutflowRepository.findByTagSrlNosAndDtmRange`(→`RawDataOutflowDto`, `qualityCd`·`corrVal` 보유) + `TagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange`(→`TagPredcRangeDto`, `qualityCd` 부재=예측 정상) 재사용 권장. FRI/PRI/LEI 혼용 시그니처 일치. "Outflow" 이름의 LEI 의미 부정합은 **DB 관점 문제 아님** — "사이클 간 자산 자동 원용 금지" 메모리 정책상 **사용자 명시 승인 전제**(이미 승인) 를 PLAN 가정 섹션 기재. 3번째 중복 회피(7번 ANALYZE1 DBA "동일 시그니처 3개+ 누적 시 common 추상화" 정합).
    - **권고(중간) — SLA**: 멀티 시설 fan-out — 표출대상 N + DWT M 이 10 초과 시 태그 IN 20+, 최대 1440행/태그 → 총 28,800행+ 단일 트랜잭션 적재. PLAN 에 시설 수 상한(예: N+M≤10) 가정 명기 + `EXPLAIN ANALYZE` 200ms 초과 시 커서(keyset) 전환 조건을 성공 기준 포함 권고.
    - **참고(낮음)**: `rawdata_1m_h` BRIN + 복합 B-Tree 공존 — `tag_srl_no IN + acq_dtm BETWEEN` 은 복합 B-Tree 선택. EXPLAIN 에서 BRIN 선택 시 비교 필요.
- **결론**: 재귀 하위 = **앱 레벨 BFS + 깊이 카운터**(블로커 해소). 리포지토리 = `RawDataOutflowRepository`(계측) + `TagPredcRangeRepository`(예측) 재사용(사용자 승인 전제, 정확한 pair PLAN 확정). 신규 DDL 0건, 기존 인덱스 파티션 프루닝 활용. SLA 시설 수 상한 가정 + 커서 전환 조건 PLAN 명시.

### 안건 3: 응답 DTO 구조 + Service 분해 + 명명 + 재귀 도출 컴포넌트
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 발견 사항:
    - **블로커(높음) — DTO 구조**: 제안 통합 `DownstreamSeries`(LEVEL 전용 `instrumentId`/`parentFacilityId` 가 DEMAND·PRESSURE 에서 null) optional-field 패턴은 `api-patterns.md §BaseAuditResponseDto 3단 허용 조건 2항`("자식 전용 필드를 부모에 instanceof 분기로 채우는 방식 금지") 동일 맥락 위반 + `coding-discipline.md §2` 복잡도. **dataType별 전용 DTO 분리 권장** (`...Demand`/`...Pressure`/`...Level` + `DemandPoint`/`PressurePoint`/`LevelPoint`). 단일 엔드포인트 유지 시 응답 다형성(wrapper `{dataType, data}` 또는 oneOf discriminator) PLAN 확정.
    - **권고(중간) — 재귀 도출 컴포넌트**: `FacilityDownstreamTreeResolver` `@Component` 분리 권고 — Service 단일 메서드가 (트리탐색+dataType분기+시계열조회+ratio) 50줄 초과 위험(`§2.1`). Resolver 분리 시 추상화 2단(`Service→Resolver`) 유지.
    - ratio: 단일 private `computeRatio(actual, predc)` 10줄 이내, `scale(1, HALF_UP)` + null 가드 충분(별도 컴포넌트 불요). `effectiveValGood`(BAD 가드) 본 서비스 재구현.
    - 빈 시리즈: 태그 부재 표출대상/LVMTR 부재 DWT 도 **빈 points 시리즈 포함**(7번 OPS 부재 펌프 빈 트랙 정합, 프론트 렌더 순서 의존). 표출대상 0건 → 빈 List `[]`(404 아님). "빈 List vs 404" PLAN 명시.
    - 명명: `FacilityDownstreamTimeSeriesService`, 'Section8' 금지. `@ArraySchema(implementation)` 의무 — series List·points List(+ `facilityTypeCd` enum 시 `@Schema(implementation=FacilityType.class)`).
    - 헬퍼 재구현(추출 금지) — `coding-discipline.md §2·§3` + 사용자 메모리(7번 Javadoc 명문화). 7번 서비스 무수정.
- **결론**: **dataType별 전용 DTO 분리**(블로커 해소). 단일 엔드포인트 유지(사용자 Q2) + 응답 다형성(oneOf discriminator `dataType` / abstract base+3 subtype) PLAN 확정. `FacilityDownstreamTreeResolver` 컴포넌트 분리. `computeRatio`·`effectiveValGood` 본 서비스 재구현. `@ArraySchema` 의무. 빈 시리즈 포함 + 표출대상 0건 빈 List. 비즈니스 의미 명명('Section8' 금지).

### 안건 4: 도메인 4영역 + 유출 io_cd + DWT 연결 + LEI 품질 + 루트 제한 + 대비율
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 블로커 0:
    - **도메인 4영역 전부 비해당** — 7번 동형. 알람(임계값 평가·`alarm_h` INSERT 없음, GOOD 필터는 신뢰도 표시) / 인터록(제어 명령 0) / AI 운전모드(`ai_drvn_mod_*` 무접촉, 읽기 전용) / 이력 기록(INSERT/UPDATE 0).
    - **io_cd 미필터, 첫 FLWMTR 유지 권장** — 정수장 토폴로지상 PWTF·POINT 의 FLWMTR 는 구조적으로 유출(하류) 측정. `io_cd` 필터는 태그 마스터 데이터 품질 의존 fragile. 7번 "첫 매치"(구조적 위치 기반) 가 견고·일관. 단 다중 FLWMTR 혼재 시 `main_yn`/`disp_ord` 정렬 신뢰도 ↑ — PLAN 가정 명기.
    - **DWT→표출대상 = 직속 parent(`parent_facility_id`) 권장** — "PWTF/POINT → DWT" 단방향 송수 흐름을 `parent_facility_id` 가 명시. 최근접 조상 재귀는 parent 가 RSV 등일 때 의도치 않은 결합 → 1단계 직접 참조 단순·정합. DWT.parent 가 PWTF/POINT 아니면 수위 그룹핑 제외 — PLAN 명기.
    - **LEI GOOD-only(`effectiveValGood`) 적절** — `§3` Hold Last Value 는 인바운드 `corr_val` 단계 처리, 조회는 `quality_cd` GOOD 만. UNCERTAIN/BAD → null(측정불명). LEI 연속값이므로 null=측정불명 @Schema 명시(OPS tri-state 와 구별).
    - **루트 시설 종류 제한 없음 권장** — 4/5/7번(단일 시설 직속) 의 PWTF·DWT·PRSF 제한은 8번(재귀 트리) 에 부적합(상위 PWTF 루트 가능). 임의 활성 시설 허용, 탐색 결과 PWTF/POINT 0건 → 빈 응답 200.
    - **대비율 도메인 제약 없음** — 조회 전용, 이상치 기각(`§3`) 미적용. 분모 0/null → null 방어(도메인 안전 아닌 단순 방어).
    - 루트 자신이 PWTF/POINT 일 때 표출대상 포함 여부 PLAN 명시.
- **결론**: 4영역 **전부 비해당**(구체 사유 명기). io_cd 미필터(첫 FLWMTR). DWT→직속 parent(PWTF/POINT). LEI GOOD-only + null=측정불명. 루트 종류 제한 없음(탐색 0건 → 빈 응답 200). 대비율 도메인 제약 없음(분모 0/null → null). 루트 포함 여부·DWT parent 종류·분모 제로 처리 PLAN 가정 명기.

---

## 표준 사전 카탈로그

본 사이클은 표준 사전 3층 모두 신규 등록 0건 (안건 1 결론). 표 생략.
- 신규 표준 단어: 없음 (`flwrt`·`prsr`·`wtlv`·`actl`·`predc`·`val` 기존 재사용)
- 신규 표준 데이터 도메인: 없음
- 신규 표준 용어(DB 컬럼): 없음
- 명명 확정: 경로 `downstream-time-series`, enum `FacilityDownstreamDataType` (전부 Java 식별자 — 사전 적용 외)

---

## 신규 엔티티/DB 컬럼

**없음** — 조회 전용 API 신설. 신규 엔티티·DB 컬럼·DDL·인덱스·데이터 도메인 추가 0건.

신규 Java 클래스 (DB 영향 없음):
- `api/src/main/java/com/mo/swtp/facility/dto/` — dataType별 전용 응답 DTO (Demand/Pressure/Level, 명명·다형성 구조 PLAN 확정) + 중첩 Point
- `api/src/main/java/com/mo/swtp/facility/service/FacilityDownstreamTimeSeriesService.java` — Service
- `api/src/main/java/com/mo/swtp/facility/service/FacilityDownstreamTreeResolver.java` — 재귀 하위 BFS 도출 컴포넌트
- `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityDownstreamDataType.java` — enum DEMAND/PRESSURE/LEVEL

수정 클래스:
- `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` — 엔드포인트 메서드 1개 추가 (기존 섹션 무수정)
- `api/src/main/java/com/mo/swtp/facility/repository/FacilityRepository.java` (또는 Custom) — BFS 용 `findByParentFacilityIdInAndUseYn` 류 조회 메서드 추가 (기존 메서드 무수정)

재사용 (무수정): `RawDataOutflowRepository`·`TagPredcRangeRepository`·`InstrumentCustomRepository.findByFacilityIdInAndEquipType`·`TagRepository.findByInstrumentInstrumentIdInAndUseYn`·`RawDataOutflowDto`·`TagPredcRangeDto`·`QualityCode`·`EquipType`·`TagMeasurementType`·`FacilityType`·`FacilityErrorCode`

테스트 클래스:
- `api/src/test/java/com/mo/swtp/facility/service/FacilityDownstreamTimeSeriesServiceTest.java`
- `api/src/test/java/com/mo/swtp/facility/service/FacilityDownstreamTreeResolverTest.java` (BFS 다단계/순환/깊이)

---

## 기존 사전·패턴과의 충돌

**없음** — 표준 사전 영향 0건, 도메인 4영역 비해당, 기존 섹션(4·5·7·9·10) 무수정 병렬 추가. Round 1 블로커 2건
(재귀 조회 순환 안전성 / 통합 DTO optional 필드) 은 회의 내 해소(BFS 채택 / dataType별 DTO 분리)되어
PLAN 진입 잔여 블로커 0건. 리포지토리 재사용은 사용자 명시 승인 전제(메모리 정책 정합).

---

## PLAN 으로 전달할 결정 사항

- **도메인 모델 초안**: 신규 엔티티 0건. 계측 `effectiveVal`/`effectiveValGood`·`computeRatio`(예측/계측×100, scale 1 HALF_UP, 분모 0·null·예측 null → null) 는 본 Service 내부 **재구현**(추출 금지). 7번 서비스 무수정.
- **API 설계**: 단일 endpoint `GET /api/facility/{facilityId}/operating-status/downstream-time-series?dataType=DEMAND|PRESSURE|LEVEL` (사용자 Q2 단일 엔드포인트 유지). 응답 `CommonResponseDto<{dataType별 다형성 DTO}>` — oneOf discriminator(`dataType`) 또는 wrapper `{dataType, data}` 중 PLAN 확정 (기존 `FacilityController.findAllFacilities` oneOf 선례 참조).
- **DTO 구조 (블로커 해소)**: dataType별 전용 — DEMAND/PRESSURE = 표출대상 facility 시리즈 `{facilityId, facilityNm, facilityTypeCd, points[]}`, LEVEL = 수위계 시리즈 `{facilityId(DWT), facilityNm, facilityTypeCd, instrumentId, instrumentNm, parentFacilityId, parentFacilityNm, points[]}`. `points[] = {dtm, actualVal, predcVal, ratio}`. (Demand·Pressure 동일 shape 를 2 DTO 로 명세 분리 vs 공유 1 shape 는 PLAN 확정.) `@ArraySchema(implementation)` + `@JsonFormat("yyyy-MM-dd HH:mm:ss")` + `BaseAuditResponseDto` 미상속.
- **재귀 하위 도출**: `FacilityDownstreamTreeResolver` `@Component` — **앱 레벨 BFS**(`parent_facility_id IN` 1레벨씩 확장, `use_yn='Y'`, 깊이 카운터 + visited-set 순환 방어). 표출대상 = 재귀 하위 중 PWTF·POINT, 수위 소스 = DWT(직속 parent 가 표출대상인 것).
- **dataType 분기**: DEMAND/PRESSURE → `findByFacilityIdInAndEquipType(displayTargetIds, [FLWMTR])` → 태그 FRI/PRI(첫 매치, io_cd 미필터). LEVEL → `findByFacilityIdInAndEquipType(dwtIds, [LVMTR])` → 태그 LEI(수위계당 1 시리즈, parent 표출대상 그룹).
- **시계열 조회**: `RawDataOutflowRepository.findByTagSrlNosAndDtmRange`(계측) + `TagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange`(예측), `[today 00:00, now)` 동일 구간. 기존 인덱스 파티션 프루닝. 시리즈별 `TreeMap<LocalDateTime, parts>` 병합 + 양쪽 부재 슬롯 생략.
- **품질·NULL 정책**: 계측 GOOD-only(`effectiveValGood`, corrVal 우선) — FRI/PRI/LEI 동형. 예측 `predc_val` 직접(QUALITY 부재). NULL 의미 분리 @Schema.
- **명명**: 'Section8' 금지 — `FacilityDownstreamTimeSeriesService`/`...TreeResolver`/`FacilityDownstreamDataType`. DTO 명 PLAN 확정.
- **ErrorCode**: `FACILITY_NOT_FOUND` 재사용. (루트 종류 제한 없음 → `UNSUPPORTED_FACILITY_TYPE_*` 미사용 — 탐색 0건 시 빈 List.)
- **정량 기준**: Resolver 분리 + `computeRatio`/`buildSeries`/`mergeActualPredc` 헬퍼 분해로 메서드 50줄 이내(§2.5 면책 비해당).

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 / PLAN 결정 |
|-----------|------|--------------|
| 재귀 하위 구현 = 앱 레벨 BFS + 깊이 카운터 + visited-set 순환 방어 (재귀 CTE 대신) | 결정 → PLAN | DBA 블로커 해소. 실제 깊이 3단 초과 확인 시 `WITH RECURSIVE`+`CYCLE` 재검토. PLAN 깊이 실측 가정·상한 명기 |
| 시계열 리포지토리 재사용 pair = `RawDataOutflowRepository`(계측) + `TagPredcRangeRepository`(예측) | 미해결 → PLAN | 사용자 재사용 승인 전제. 정확한 pair·projection(qualityCd 보유[계측]/부재[예측]) PLAN 확정 |
| 응답 DTO = dataType별 전용 + 단일 엔드포인트 다형성(oneOf discriminator vs wrapper) | 미해결 → PLAN | 백엔드 블로커 해소. 사용자 Q2(단일 엔드포인트) 유지. 다형성 구조 PLAN 확정·사용자 확인 |
| **표출대상에 루트 자신 포함 여부** — 기본 descendants-only(루트 제외, "하위 시설" 문구 충실) | 미해결 → PLAN | ⚠️ 2단 토폴로지(루트=유일 PWTF, dev DB)에서는 표출대상 0 → 빈 응답. 루트 포함이 대안(2·3단 모두 동작). PLAN 사용자 확인 |
| io_cd 미필터(첫 FLWMTR, 구조적 위치). 태그 마스터 io_cd·main_yn 품질 가정 | 가정 | 다중 FLWMTR 시 `main_yn`/`disp_ord` 정렬 검토 — PLAN 명기 |
| DWT→표출대상 = 직속 parent(`parent_facility_id`). parent 가 PWTF/POINT 아니면(RSV 등) 수위 그룹핑 제외 | 가정 → PLAN | 도메인 전문가 권고 — PLAN 명기 |
| ratio 분모 0·null 또는 예측 null → null. scale 1 HALF_UP | 결정 | 도메인 안전 아닌 단순 방어. PLAN 명기 |
| 루트 종류 제한 없음(임의 활성 시설). 미존재·비활성 → 404. 탐색 결과 PWTF/POINT 0건 → 빈 List `[]`(200) | 결정 | 도메인 전문가 권고 — 4/5/7번 제한 미적용 |
| LEI GOOD-only(`effectiveValGood`). null=측정불명 @Schema | 결정 | FRI/PRI 동형 |
| 빈 시리즈(태그 부재 표출대상/LVMTR 부재 DWT) = 빈 points 포함 (7번 정합) | 결정 | 프론트 렌더 순서 의존 |
| `FacilityDownstreamTreeResolver` 컴포넌트 분리 (Service 50줄 방지) | 결정 | 추상화 2단 유지 |
| 헬퍼 재구현(`effectiveVal`·`effectiveValGood`·`computeRatio`) 본 서비스. 7번 무수정 | 결정 | `coding-discipline.md §2·§3` + 메모리 |
| SLA — 표출대상 N + DWT M 수 상한 가정(예: N+M≤10) + 태그 IN 20+ 시 커서/LIMIT 전환 조건 | 가정 → PLAN | DBA 권고. 본 사이클 가정 내 단일 조회. 초과 시 분할 조회 성공 기준 |
| 다중 태그 동기 수집 — 동일 1분 OPS/FRI/PRI/LEI 가 동일 `acq_dtm`/`predc_dtm` 슬롯 일괄 존재 | 가정 | 7번 동형. PLAN 픽스처 동일 시각 검증 |
| BRIN vs 복합 B-Tree EXPLAIN 확인 | 참고 | `tag_srl_no IN + dtm BETWEEN` 복합 B-Tree 선택 확인 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `FacilityDownstreamTreeResolver` 단위 테스트 GREEN — 다단계 트리(루트→PWTF→DWT, 루트→POINT→DWT) 표출대상/수위소스 분류 / 순환·깊이 제한 / DWT parent 비-PWTF/POINT 제외 | `./gradlew.bat :api:test --tests "FacilityDownstreamTreeResolverTest"` PASS |
| `FacilityDownstreamTimeSeriesService` 단위 테스트 GREEN — DEMAND 표출대상 N→N 시리즈 FRI 계측+예측+ratio / PRESSURE PRI / LEVEL 수위계 M→M 시리즈 LEI+parent 그룹핑 / ratio 계측 0·null·BAD 또는 예측 null → null / 루트 미존재·비활성 404 / 표출대상 0건 빈 List / 태그 부재 시설 빈 points | `./gradlew.bat :api:test --tests "FacilityDownstreamTimeSeriesServiceTest"` PASS |
| 빌드 성공 | `./gradlew.bat build` 출력 BUILD SUCCESSFUL |
| Swagger 신규 엔드포인트 + dataType별 DTO 노출 | `:api:bootRun` 후 Swagger UI 에서 엔드포인트 + dataType discriminator + 계측/예측/ratio NULL 의미 한국어 @Schema 노출 |
| 파티션 프루닝(계측·예측) | `EXPLAIN ANALYZE` 출력에 `rawdata_1m_h_YYYYMM`·`predc_1m_h_YYYYMM` 만 + `Index Scan using idx_*_tag_time` |
| 메서드 50줄 이내 | 신규 Service/Resolver 메서드별 본문 50줄 이내(빈줄·주석 제외) |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 알람 임계값 평가·전이·`alarm_h` INSERT 없음. `quality_cd` 를 읽어 GOOD 필터·표출 신뢰도로 분류하는 것은 알람 발생이 아닌 데이터 신뢰도 표시 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 미발행, 아웃바운드 경로 미진입, `pump_interlock_p` 미참조 (조회 전용) |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`·`ai_drvn_mod_h` 무접촉, 모드 전환·강제 전환 판정 없음, 읽기 전용 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 새 이력 INSERT/UPDATE 0건, `pump_ctrl_h`·`ai_drvn_mod_h`·`transition_reason` 무접촉 |

"비해당" 단독 4건 차단 해제 조건 충족: (1) 각 행 구체 사유 명기, (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족.

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 사이클은 표준 사전 갱신 0건 + DB 영향 0건 + 도메인 4영역 비해당으로 룰 갱신 지시서 체크박스 **0건**.
PLAN 진입 전제조건 자동 충족.

---

## 산출물
- [계획안](../../../plan/20260601/운전현황분석-8번섹션/PLAN1.md) (status: draft 예정)
