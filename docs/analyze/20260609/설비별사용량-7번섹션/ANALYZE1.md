---
status: approved
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 7번섹션 — 설비별 순시전력 트렌드 조회 도메인 분석

## 작업 배경

`backend/image/설비별사용량.png` 대시보드의 **7번섹션**(우측 하단 "순시 전력" 멀티시리즈 영역 차트)을 **읽기 전용 API** 로 구현한다. 1번섹션 파라미터(시작일자·종료일자 `from~to`)와 2번섹션에서 선택한 **시설ID**(`facilityId`)를 받아, 선택 시설을 루트로 `parent_facility_id` self-FK 를 **재귀 탐색**(하위 시설 전체, 루트 inclusive)한 뒤 그 시설들에 속한 활성 계측기(설비) 중 **순시전력 태그(PWI, kW)** 를 보유한 설비들의 `from~to` 구간 **순시전력 1분 시계열** 을 **설비별 멀티시리즈**로 반환한다.

- 조회 구간 = `from` **00:00:00** ~ `to` **23:59:59** (= `from.atStartOfDay()` inclusive ~ `to.plusDays(1).atStartOfDay()` exclusive).
- 측정값 = PWI 태그의 분(分)별 `COALESCE(corr_val, raw_val)`(GOOD 품질만) 합산 — 한 설비가 PWI 태그를 다수 보유하면 **동일 `acq_dtm` 합산**(SUM(MAX)≠MAX(SUM) — 순시전력은 동시각 합이 의미).
- 외부 산출물: `backend/image/설비별사용량.png` (7번섹션 영역, 시각 로드 완료).

### 사용자 사전 확정 사항 (dev 진입 단계 AskUserQuestion, 2026-06-09)
1. **시리즈 단위 = 설비별, 동일시각 합산** — 설비(계측기) 1개 = 차트 라인 1개. 설비가 PWI 태그를 다수 보유하면 동일 `acq_dtm` 끼리 합산(2번섹션 시설합 PWI 선례).
2. **측정값 = `COALESCE(corr_val, raw_val)` + `quality_cd='GOOD'`** — 보정값 우선, NULL 시 원본값. BAD/UNCERTAIN·결측 제외(부분합 허용).
3. **조회기간 상한 31일** — 순시 1분 raw 데이터 대용량(설비당 1일=1440포인트). 차트 가독성·응답속도 고려. 초과 시 `INVALID_SEARCH_PERIOD`.
4. **명명 = `FacilityInstrumentPowerTrend*`** — Service/Dto/SearchDto/Controller. 엔드포인트 `/api/facility/{facilityId}/instrument-power-trend`, `@Tag "15. 설비별 사용량"`.
5. **헬퍼 4번째 사본 = 동형 복제 유지** (회의 중 AskUserQuestion, 2026-06-09) — `collectSubtree`·`findActiveFacilityOrThrow` 를 신규 서비스에 동형 복제하고 본 ANALYZE 결정을 인용 주석으로 명기. 공통 추출은 별도 리팩토링 사이클로 분리.

> 메모리 `feedback_no_auto_reuse_cross_cycle.md` / `feedback_section_cycle_discard_policy.md` 정합 — 동형 선례(`FacilityInstrumentEnergyUsageService`·`FacilityPowerInstrumentService`·`InstrumentEnergyTrendService`)의 자산을 자동 원용하지 않고 **구조만 미러링한 신규 자산**으로 작성. 재사용 범위·명명·헬퍼 복제는 모두 사용자 명시 결정으로 확정.

### 핵심 발견 — 중복 아님 / 동형 선례
- 4번섹션 `InstrumentEnergyTrendService`(instrument) — 단일 계측기 **PWQ(적산전력량) 시계열**(버킷 차분). 본 작업은 **시설 재귀 스코프** + **PWI(순시전력) 직접값** + **멀티시리즈**로 스코프·측정유형·산정방식이 모두 다른 별개 산출물.
- 5·6번섹션 `FacilityInstrumentEnergyUsageService`(facility) — 시설 재귀 하위 **PWQ 전력량 누적 + 분포율**(기간 단일 총합). 본 작업은 동일 재귀 멤버십 구조 위에 **PWI 분(分) 시계열**을 결합(총합 아님, 시계열).
- 2번섹션 `RawDataCustomRepository.findFacilityLatestMinuteSumElpwr`(`unnest(tags,roots)` + `SUM(COALESCE(corr_val,raw_val))` GOOD `GROUP BY facility_id, acq_dtm` + DISTINCT ON 마지막분) — PWI 동시각 시설합의 SQL 선례. 본 작업은 이를 **설비(instrument) 단위 + 전체 분 시계열**(DISTINCT ON 제거)로 변형한 신규 native 쿼리.

## 회의록 (5인 회의)

### 안건 1: 신규 어휘 정합성 (DTO 클래스·필드명·URL 세그먼트)
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: DB 컬럼 신설 0건 전제 → 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 약어 **0건**. DTO 시계열 포인트 값 필드 `elpwrVal` 구성 단어 `elpwr`(전력 순시 kW, 2026-04-25)·`val`(값, 2026-05-18) 기존 등록, 시각 필드 `acqDtm` 의 `acq`(2026-05-13)·`dtm`(2026-04-23) 기존 등록 재사용. **`power`·`trend`** 단어는 클래스명·URL 세그먼트 전용으로 DB 컬럼 조합 재료 아님 → 미등록 유지. `power` 는 기등록 `elpwr`(electric power) 어근과 직접 충돌(동의어 중복)이라 추가 등록 금지(3번섹션 `power` 미등록 선례 동형), `trend` 는 4번섹션 `energy`/`trend` 클래스·URL 전용 미등록 종결 선례 직접 적용. `elpwrVal`·`acqDtm` 응답 DTO 필드명은 **DB 컬럼이 아니므로** backend `standard-terms.md` 등록 의무 없음(`facilityGroupCd`·`elcegVal`·`totalElpwr` DTO 전용 변수명 미등록 선례 동형, `predc_val` DB 컬럼과 어순만 다른 DTO 전용 필드).
- **결론**: 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 약어 **0건**, 룰 갱신 지시서 **0건**. 직전 2·3·4·5,6번섹션과 동일하게 어휘 갱신 0건으로 종결.

### 안건 2: 계층 책임·패턴 (Repository A vs B·SearchDto·Controller·헬퍼 사본·DTO·정량 기준)
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약 (블로커 2·권고 2·참고 2):
  - **① Repository 옵션 A vs B (블로커)** — 옵션 A(기존 `findByTagSrlNosAndDtmRange` 태그별·전품질 raw 재사용 + Service 인메모리 GOOD 필터·effectiveVal·설비별 동일시각 합산)는 `api-patterns.md` Repository 패턴("화면·응답 중심 조회는 DTO 프로젝션 우선") 위반 — 1분×31일=44,640행(태그 수 배수)을 전품질 전송 후 Service grouping 은 계층 책임 역전. **옵션 B(신규 native `findInstrumentMinuteSumElpwr`)** 가 유일 패턴 적합. `findFacilityLatestMinuteSumElpwr` 의 `unnest(tags,roots)`+SUM+GOOD+GROUP BY 패턴을 `instrument_id` 단위로 변형 + DISTINCT ON 제거(전체 분 시계열) → 선례 정합. `coding-discipline.md §2.5`(`db/query-tuning.md §2` 인용) SQL 빌더 면책 요건 충족 — 동일 면책 인용 주석 명기.
  - **② SearchDto 재사용 불가 (블로커)** — `FacilityInstrumentEnergyUsageSearchDto`(`MAX_PERIOD_DAYS=396`) 재사용 시 31일 상한 위반 + DTO Javadoc·`@Schema` 가 396일 노출하여 SPEC 명세 오염. **신규 `FacilityInstrumentPowerTrendSearchDto` 동형 복제(비상속)** 필수 — 4번섹션·5,6번섹션 비상속 동형 복제 선례 정합.
  - **③ Controller 분리 (권고)** — `FacilityInstrumentEnergyUsageController`(@Tag 15)에 메서드 추가는 PathPattern 충돌 없으나 누적 전력량 vs 순시 트렌드 책임 단위 상이 → 신규 `FacilityInstrumentPowerTrendController`(@Tag 15) 분리 권고(`FacilityInstrumentEnergyUsageController` 가 `FacilityController` 에서 동일 이유로 분리된 선례).
  - **④ 헬퍼 4번째 사본 (권고→사용자 결정)** — `collectSubtree`·`findActiveFacilityOrThrow` 가 3·5,6번 3회 복제 → 본 사이클 4번째, "3건 이상 누적 시 별도 ANALYZE" 트리거 충족 → **사용자 결정 요청** 의무.
  - **⑤ DTO `@Schema(implementation)`/`@ArraySchema` (참고)** — `equipTypeCd`(EquipType)·중첩 시리즈 List·중첩 points List 각각 명시 의무(`api-patterns.md`).
  - **⑥ 메서드 50줄 (참고)** — BFS수집→PWI태그/매핑→쿼리→멀티시리즈 변환 단일 메서드 집중 시 50줄 임박. private 헬퍼 분리 + 내부 매핑 record(`PwiRouting` 등)로 main 30줄 이내 유지.
- **결론**: 옵션 B + 신규 SearchDto(31일) + 신규 Controller(@Tag 15) + `@Schema`/`@ArraySchema` 명시 + 헬퍼 분리 채택. 블로커 ①②는 "계획된 신규 자산 작성"의 확인이며 본 작업 범위 포함 → 빌드·테스트 검증으로 해소. ④ 헬퍼 4번째 사본은 **사용자 결정 = 동형 복제 유지**(AskUserQuestion 2026-06-09) — 인용 주석 명기, 기존 3개 서비스 미침습.

### 안건 3: 도메인 규칙 정합성 (4영역 + PWI 측정값 정책 + 동일시각 합산 + 종류 무필터 + 빈 시리즈)
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약 (블로커 0·권고 1):
  - **① 도메인 4영역** — 알람 4단계·인터록·AI 운전 모드·이력 기록 의무 전부 **비해당**. 읽기 전용 GET, `rawdata_1m_h` PWI SELECT+집계만, 제어 발행/모드 전환/이력 INSERT 경로 없음, 신규 엔티티/DB 컬럼 0 → "비해당 근거 타당성" 통과 조건(구체 사유 + 신규 엔티티 없음) 동시 충족.
  - **② PWI 측정값 정책 (통과)** — `ot-integration.md §3` PWI 결측 정책은 "null 저장 + 집계 제외"·HLV 미적용. `quality_cd='GOOD'` 필터가 BAD/UNCERTAIN 완전 제외 → §3 정합. `COALESCE(corr_val, raw_val)` 도 타당(corr_val 은 PWI 경로에서 HLV 보정 아닌 순수 운영자 보정 한정) — 2번섹션 `findFacilityLatestMinuteSumElpwr`·전력피크분석 `findMaxMinuteSumElpwr` 선례 동일 패턴.
  - **③ 동일시각 합산 (통과)** — 설비 단위 PWI 합산은 그 시각 GOOD 품질 태그만 포함하는 부분합. 전력은 HLV 미적용이라 BAD 태그를 0으로 채워 합산하는 것보다 부분합이 오염 방지상 §3 정합. 2번섹션 시설합 선례 동일 결론.
  - **④ 종류 무필터 (통과→권고)** — `equip_type_cd` 필터 강제 룰(`ot-integration.md §5`·`entity-patterns.md`)은 "자식 종류별 도메인 룰이 다른 제어·평가 시나리오" 한정. 본 "PWI 태그 보유" 물리 사실 기준 읽기 전용 조회는 적용 대상 아님 → 무필터 정합(3·5,6번섹션 선례). **권고**: PLAN/ANALYZE "가정" 섹션에 무필터 근거 1건 명기(`coding-discipline.md §1`).
  - **⑤ 빈 시리즈 200 (통과)** — PWI 무보유·데이터 0건은 정상 업무 경로 → 빈 items 200. 4xx 는 도달 가능 정상 상태의 예외 처리이므로 부적절(`coding-discipline.md §2`).
- **결론**: 블로커 0. 4영역 전부 비해당·PWI GOOD+COALESCE 정합·동일시각 부분합 정합·종류 무필터 정합·빈 시리즈 200 정합. 권고 1건은 가정 섹션 `equip_type_cd` 무필터 근거 명기로 반영.

### 안건 4: DB 쿼리·인덱스·격리·성능 (스키마 변경 0)
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약 (블로커 1·권고 2·참고 1):
  - **통과** — 스키마 변경 0건(신규 테이블·컬럼·인덱스·데이터 도메인 0, DBA 2차 승인 대상 0). `DOM_QTY_15_4`·`DOM_DTM`·`DOM_TAG_SRL_NO_50` 재사용. `unnest(tags,instruments)` read-only JOIN 은 시계열→마스터 FK 금지 정책(`partitioning-and-retention.md §1`, DDL FK 물리 등록 금지)과 무관 — native CTE unnest JOIN 은 애플리케이션 레벨 논리 참조(2번섹션 `unnest(tags,roots)` 선례 동형). 빈 `unnest` → CTE 0행 → 빈 컬렉션(DB 오류 없음). 격리 READ_COMMITTED readOnly 유지 타당(과거 표시용 순시 읽기, 진행 분 일관성보다 표시 최신성 우선).
  - **① EXPLAIN 검증 (블로커)** — `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 등가 선두 + `acq_dtm` 범위 후위는 컬럼 순서 원칙(`indexing-and-migration.md §1`) 부합하나, 태그 집합 크기·selectivity 에 따라 Seq Scan 선택 가능. 31일 상한은 최대 2개 파티션(월말~익월초) 스캔이라 프루닝은 동작. **PLAN 전 `EXPLAIN (ANALYZE, BUFFERS)` 로 실행 계획 검증 의무**(`query-tuning.md §2` 슬로우 쿼리 500ms 기준선), 미검증 운영 배포 금지.
  - **② 31일×N설비 행 볼륨 (권고)** — 설비당 최대 44,640행, N설비 선형 증가. `query-tuning.md §1` 대용량 시계열 페이지네이션/커서 권고. 트렌드 차트 목적상 분 전량 vs 버킷 집계 PLAN 검토 권고.
  - **③ 옵션 B 채택 (권고)** — 옵션 A 대비 DB 레벨 집계·필터 후 반환으로 전송량·메모리 유리. EXPLAIN 전제로 채택 확정.
  - **④ 인덱스 정렬 방향 (참고)** — 기존 인덱스 `acq_dtm DESC` vs 본 쿼리 `ORDER BY instrument_id, acq_dtm` ASC. PostgreSQL Backward Scan 지원으로 기능 문제 없음, 현 단계 인덱스 추가 불필요.
- **결론**: 블로커 1(EXPLAIN 검증). 스키마 변경 0·신규 인덱스 불필요·DBA 2차 승인 0. 옵션 B 채택. **분 단위 전량 반환 유지**(사용자 "순시데이터" 명시 + 31일 상한 볼륨 인지·선택) — 버킷 집계 미적용을 가정 섹션 결정 기재. EXPLAIN 검증은 PLAN 성공 기준 체크박스로 강제(블로커 해소). 격리 READ_COMMITTED 유지.

## 표준 사전 카탈로그

### 신규 표준 단어
**없음** — `elpwr`(전력 순시, 2026-04-25)·`val`(값, 2026-05-18)·`acq`(수집, 2026-05-13)·`dtm`(일시, 2026-04-23) + `instrument`·`facility`(비즈니스 도메인 약어) 전부 기존 등록. `power`·`trend` 는 DTO 클래스명·URL 세그먼트 전용으로 DB 컬럼 조합 재료 아님 → 미등록 유지(`power` 는 `elpwr` 어근 충돌 회피 — 3번섹션 선례, `trend` 는 4번섹션 선례).

### 신규 표준 데이터 도메인
**없음** — DB 컬럼 신설 0건. DBA 2차 승인 대상 0건.

### 신규 표준 용어
**없음** — DTO Java 필드명(`elpwrVal`·`acqDtm` 등)은 DB 컬럼이 아니므로 `standard-terms.md` 등록 의무 없음(`elcegVal`·`facilityGroupCd`·`totalElpwr` DTO 전용 변수명 미등록 선례 동형).

## 신규 엔티티/DB 컬럼

**없음** — 읽기 전용 조회 API. 신규 엔티티·DB 테이블·DB 컬럼·인덱스·마이그레이션 **0건**. 추가 산출물은 (1) `FacilityInstrumentPowerTrendSearchDto` 신규 1개, (2) `FacilityInstrumentPowerTrendDto`(+ 중첩 `InstrumentPowerSeries` + `PowerTrendPoint`) 신규 1개, (3) `FacilityInstrumentPowerTrendService` 신규 1개, (4) `FacilityInstrumentPowerTrendController` 신규 1개, (5) `RawDataCustomRepository.findInstrumentMinuteSumElpwr` 신규 메서드 + `RawDataCustomRepositoryImpl` 구현 1개 + 내부 결과 record `RawDataInstrumentSumDto` 신규 1개, (6) `FacilityInstrumentPowerTrendServiceTest` 단위 테스트 1개. 기존 리포지토리(`FacilityRepository.findById`·`findByParentFacilityIdInAndUseYn`·`InstrumentRepository.findByFacilityFacilityIdInAndUseYn`·`TagRepository.findByInstrumentInstrumentIdInAndUseYn`)·enum(`TagMeasurementType.PWI`·`EquipType`·`YnType`·`QualityCode.GOOD`)·`FacilityErrorCode.FACILITY_NOT_FOUND`/`INVALID_SEARCH_PERIOD`·`idx_rawdata_1m_h_tag_time` 전부 변경 없이 재사용.

## 기존 사전·패턴과의 충돌

| 충돌 후보 | 판정 | 해소책 |
|----------|------|--------|
| `power`/`trend` 단어 vs `elpwr`/`elceg` 어근 | 충돌 없음 | DTO 클래스명·URL 세그먼트 전용, DB 컬럼 조합 재료 아님 → 미등록(3·4번섹션 선례) (glossary 안건 1) |
| 옵션 A(기존 Repository 재사용) vs 옵션 B(신규 native) | 계층 역전(블로커) | 옵션 B 채택 — `findFacilityLatestMinuteSumElpwr` unnest+SUM+GOOD+GROUP BY 패턴 설비 단위 변형. `§2.5`(`query-tuning.md §2`) 면책 인용. 본 작업 범위 포함 → 빌드 검증 (backend 안건 2-①, dba 안건 4-③) |
| `FacilityInstrumentEnergyUsageSearchDto`(396일) 재사용 vs 신규 | 검증 느슨·SPEC 오염(블로커) | 신규 `FacilityInstrumentPowerTrendSearchDto`(31일 동형 복제·비상속). 4·5,6번섹션 선례 (backend 안건 2-②) |
| `FacilityInstrumentEnergyUsageController`(@Tag 15) 확장 vs 신규 | SRP 책임 혼재(권고) | 신규 `FacilityInstrumentPowerTrendController`(@Tag 15) 분리. PathPattern 충돌 없음 (backend 안건 2-③) |
| `collectSubtree`·`findActiveFacilityOrThrow` 4번째 사본 | 중복(권고→결정) | **동형 복제 유지**(사용자 결정 2026-06-09) — 인용 주석 명기, 기존 3서비스 미침습, 공통 추출 별도 사이클 (backend 안건 2-④) |
| `equip_type_cd` 필터 강제 룰 vs 종류 무필터 | 위반 아님 | PWI 태그 보유 물리 사실 기준 읽기 전용 — 자식 종류별 도메인 룰 분기 없음. 가정 섹션 근거 명기 (domain 안건 3-④) |
| PWI COALESCE+GOOD vs `ot-integration.md §3` 결측 정책 | 정합 | GOOD 필터로 BAD/UNCERTAIN 제외 = "집계 제외" 정합, HLV 미적용. 2번섹션·전력피크 선례 (domain 안건 3-②) |

## PLAN 으로 전달할 결정 사항

**도메인 모델**: 신규 엔티티 없음. 멤버십 = 선택 시설(`facilityId`) 재귀 하위 트리(루트 inclusive) → 활성 계측기 → 활성 PWI 태그 → `findInstrumentMinuteSumElpwr`(태그→설비 unnest 매핑, GOOD `COALESCE(corr_val,raw_val)` 설비별 동일 `acq_dtm` 합산, 분 전체 시계열) → 설비별 시리즈 조립.

**API 구조** (facility 도메인, api 모듈):
- 신규 `FacilityInstrumentPowerTrendSearchDto` — 필드 `fromDt`·`toDt`(LocalDate, `@DateTimeFormat ISO.DATE`). `@Getter @Setter @NoArgsConstructor` + `@ModelAttribute`. `MAX_PERIOD_DAYS=31`. `isValid()`(non-null + `!fromDt.isAfter(toDt)` + 간격 ≤ 31일)·`toStartDtm()`(`fromDt.atStartOfDay()`)·`toEndExclusiveDtm()`(`toDt.plusDays(1).atStartOfDay()`). `FacilityInstrumentEnergyUsageSearchDto` 에서 상한만 31로 바꾼 동형(비상속).
- 신규 `FacilityInstrumentPowerTrendDto` — `@Getter` + private 생성자 + 정적 팩토리 `of(...)`, `BaseAuditResponseDto` 미상속. 필드 `unit`("kW")·`series`(`List<InstrumentPowerSeries>`, **`@ArraySchema(schema=@Schema(implementation=InstrumentPowerSeries.class))`**). 중첩 정적 `InstrumentPowerSeries{instrumentId·instrumentNm·equipTypeCd(EquipType, **`@Schema(implementation=EquipType.class)`**)·facilityId·facilityNm·points(`List<PowerTrendPoint>`, **`@ArraySchema(schema=@Schema(implementation=PowerTrendPoint.class))`**)}`. 중첩 정적 `PowerTrendPoint{acqDtm(LocalDateTime, **`@JsonFormat yyyy-MM-dd HH:mm:ss`**)·elpwrVal(BigDecimal)}`.
- 신규 `FacilityInstrumentPowerTrendService`(`@Service @RequiredArgsConstructor @Transactional(readOnly=true)`). 의존 `FacilityRepository`·`InstrumentRepository`·`TagRepository`·`RawDataRepository`. 상수 `POWER_TAG_TYPE=TagMeasurementType.PWI`·`UNIT_KW="kW"`·`MAX_DEPTH=10`. public `findPowerTrend(String facilityId, search)` + private 헬퍼 분리(검증·`findActiveFacilityOrThrow`·`collectSubtree`·PWI 태그→설비 매핑 수집·쿼리·설비별 시리즈 조립). 흐름: `isValid()` false → `INVALID_SEARCH_PERIOD` / `findActiveFacilityOrThrow`(findById + useYn==Y, 아니면 `FACILITY_NOT_FOUND`) / `collectSubtree` BFS(facilityById 맵) / 계측기 IN(0건 → 빈 series 래퍼 200) / PWI 태그 IN+필터로 태그·설비 평행 배열 구성(0건 → 빈 series 래퍼 200) / `findInstrumentMinuteSumElpwr(tags, instruments, toStartDtm, toEndExclusiveDtm)` → instrumentId groupingBy → 설비별 points(acqDtm asc) 조립 / PWI 보유 설비 전체를 series 로(데이터 0 설비는 빈 points 포함) / 시설 dispOrd→계측기 dispOrd→instrumentNm 정렬. **헬퍼 동형 복제(`collectSubtree`·`findActiveFacilityOrThrow`)·다중 PWI 동일시각 합산 정책 인용 주석 Javadoc 명기**.
- 신규 `FacilityInstrumentPowerTrendController`(`@Tag "15. 설비별 사용량"`, `/api/facility`, `CommonController` 상속). `@GetMapping("/{facilityId}/instrument-power-trend")` → `ResponseEntity<CommonResponseDto<FacilityInstrumentPowerTrendDto>>`. `@PathVariable facilityId` + `@ModelAttribute search`. `@Operation`+`@ApiResponses`(200/400/401/403/404/500, 400=INVALID_SEARCH_PERIOD, 404=FACILITY_NOT_FOUND).

**Repository (api 모듈)**:
- `RawDataCustomRepository.findInstrumentMinuteSumElpwr(List<String> tags, List<String> instruments, LocalDateTime startDtm, LocalDateTime endDtm)` 신규 + `RawDataCustomRepositoryImpl` native 구현. `findFacilityLatestMinuteSumElpwr` 의 `Session.doReturningWork` + JDBC `setArray("text", ...)` 결정론적 바인드 패턴 동형. SQL: `WITH tag_instrument(tag_srl_no, instrument_id) AS (SELECT * FROM unnest(?, ?)) SELECT ti.instrument_id, r.acq_dtm AS dtm, SUM(COALESCE(r.corr_val, r.raw_val)) AS val FROM rawdata_1m_h r JOIN tag_instrument ti ON ti.tag_srl_no = r.tag_srl_no WHERE r.acq_dtm >= ? AND r.acq_dtm < ? AND r.quality_cd = 'GOOD' GROUP BY ti.instrument_id, r.acq_dtm ORDER BY ti.instrument_id, r.acq_dtm` (DISTINCT ON 없음 — 전체 분 시계열). 빈 tags → 빈 List. `§2.5`(`query-tuning.md §2`) 면책 인용 주석. 결과 record `RawDataInstrumentSumDto(String instrumentId, LocalDateTime dtm, BigDecimal value)` 신규(`RawDataFacilitySumDto` 구조 미러링, Service 내부 전송 전용·Swagger 미노출).

**DB 설계 변경**: **없음**(스키마 변경 0). `docs/ddl/` 갱신 불필요.

**적용 패턴**: `FacilityInstrumentEnergyUsageService`(재귀 하위 + 계측기/태그 IN + 설비별 집계) + `RawDataCustomRepositoryImpl.findFacilityLatestMinuteSumElpwr`(unnest+SUM+GOOD+GROUP BY, 설비 단위 변형) + `InstrumentEnergyTrendDto`(시계열 중첩 Point) + `FacilityInstrumentEnergyUsageController`(컨트롤러 분리) 구조 미러링(자산 자동 원용 없이 신규 작성).

**격리 수준**: 동형 선례 유지 — `@Transactional(readOnly=true)` 기본 READ_COMMITTED. 과거 표시용 순시 시계열, 진행 분 팬텀 무시 가능.

**테스트**: `FacilityInstrumentPowerTrendServiceTest` Mockito 단위 — 기간 무효(null·역전·>31일)→INVALID_SEARCH_PERIOD / 미존재·비활성 시설→FACILITY_NOT_FOUND / 재귀 하위 BFS 다단계 수집 / 설비별 분 시계열 조립(acqDtm asc) / 다중 PWI 태그 동일시각 합산(SQL 결과 mock 기준 매핑 검증) / PWI 보유·데이터 0 설비 → 빈 points 시리즈 포함 / 계측기 0건·PWI 0건 → 빈 series 래퍼(200) / 시리즈 정렬(시설 dispOrd→계측기 dispOrd→instrumentNm).

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 표출 측정값 = PWI(순시전력, kW) 한정. PWQ(적산전력량)는 4·5,6번섹션 대상 → 본 API 범위 외 | 결정 | 사용자 "순시전력" 명시 |
| 시리즈 단위 = 설비별. 다중 PWI 태그 → 동일 `acq_dtm` 합산(SQL GROUP BY instrument_id, acq_dtm) | 결정 | 사용자 확정. 2번섹션 시설합 PWI 선례, SUM(MAX)≠MAX(SUM) (domain 안건 3-③) |
| 분당 측정값 = `COALESCE(corr_val, raw_val)`, `quality_cd='GOOD'` 행만(부분합) | 결정 | 사용자 확정. `ot-integration.md §3` PWI "집계 제외" 정합, HLV 미적용 (domain 안건 3-②) |
| **분 단위 전량 반환(버킷 집계 아님)** — 31일 상한으로 볼륨 제한 | 결정 | 사용자 "순시데이터" 명시 + 기간 상한 31일 인지·선택. dba 버킷 집계 권고 미채택 (dba 안건 4-②) |
| 조회기간 상한 31일 초과 → `INVALID_SEARCH_PERIOD` | 결정 | 순시 1분 raw 대용량(설비당 1일 1440포인트). `FacilityInstrumentEnergyUsageSearchDto`(396일) 동형이나 상한만 31 |
| PWI 보유·데이터 0/무데이터 설비 → 빈 points 시리즈로 포함(예외 아님) | 결정 | 5,6번섹션 "0 설비 포함" 선례 동형 + 차트 범례 일관. `coding-discipline.md §2` 정합 (domain 안건 3-⑤) |
| 계측기 0건·PWI 태그 0건 → 빈 IN/unnest 방어(즉시 빈 series 래퍼 200) | 결정 | DB 호출 전 사전 검사. 빈 unnest → CTE 0행 (dba 통과) |
| `equip_type_cd` 무필터 — 제어·모드 평가 경로 아닌 물리 사실 기준 조회 | 결정 | `ot-integration.md §5` 필터 강제 룰 적용 범위 밖. 3·5,6번섹션 선례 (domain 안건 3-④) |
| 격리 수준 READ_COMMITTED 유지(REPEATABLE_READ 미적용) | 결정 | 동형 선례 readOnly 기본. 과거 표시용 순시 시계열 팬텀 무시 (dba 통과) |
| 헬퍼 `collectSubtree`·`findActiveFacilityOrThrow` 4번째 사본 → 동형 복제 유지 | 결정 | 사용자 결정 2026-06-09, 인용 주석 명기 (backend 안건 2-④) |
| EXPLAIN (ANALYZE, BUFFERS) 검증 = PLAN 성공 기준 의무(미검증 배포 금지) | 결정 | 신규 native 쿼리 실행 계획 기준선. 31일 최대 2파티션 프루닝 (dba 블로커 ①) |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 기간 무효 → INVALID_SEARCH_PERIOD / 미존재·비활성 시설 → FACILITY_NOT_FOUND | 신규 단위 테스트 — null·역전·>31일 / findById empty·useYn=N 각각 RestApiException GREEN |
| 재귀 하위 BFS → 설비별 분 시계열 조립(acqDtm 오름차순) | 신규 단위 테스트 — 다단계 children mock + `findInstrumentMinuteSumElpwr` 결과 mock → series·points 매핑·정렬 GREEN |
| 다중 PWI 태그 동일시각 합산(SQL 위임) → 설비별 단일 시리즈 | 신규 단위 테스트 — 설비 1개 PWI 2태그 mock → 단일 series·합산값 points GREEN (SQL 합산은 통합/수동 검증) |
| PWI 보유·데이터 0 설비 → 빈 points 시리즈 포함 / 계측기·PWI 0건 → 빈 series 래퍼(200) | 신규 단위 테스트 — 0 데이터 설비 series 포함 / 빈 series·200 GREEN |
| 빌드·전체 테스트 통과 | `./gradlew.bat :api:test` PASS + `./gradlew.bat clean build` BUILD SUCCESSFUL |
| 신규 native 쿼리 EXPLAIN 검증(블로커 해소) | dev/스테이징 `EXPLAIN (ANALYZE, BUFFERS)` — `findInstrumentMinuteSumElpwr` Index Scan(`idx_rawdata_1m_h_tag_time`)·월 RANGE 파티션 프루닝(최대 2파티션)·500ms 이내 확인 |
| (선택) dev DB 수동 검증 | `GET /api/facility/{PWI 하위 설비 보유 시설ID}/instrument-power-trend?fromDt=...&toDt=...` → series 설비별·points acqDtm 오름차순·다태그 합산 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | PWI 측정값 읽기 전용 시계열 조회 — 알람 임계값·전이·복귀 무접촉, `alarm_h` 기록 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | GET 조회만 — 기동 명령 발생 경로 없음, 선행조건 검사·제어 명령 발행 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 변경 없음, 강제 전환 트리거 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h`·`pump_ctrl_h` 무접촉. facility/instrument/tag 마스터 + rawdata 조회 전용(쓰기 없음) |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

**없음** — 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 도메인 약어 등록 0건, DB suffix·엔티티 패턴 변경 0건, DB 스키마 변경 0건, 신규 ErrorCode 0건. 모든 어휘가 기존 등록 항목 재사용이다(`wtp-glossary-manager` 안건 1 확정 — 직전 2·3·4·5,6번섹션 사이클과 동일).

## 산출물
- [계획안](../../../plan/20260609/설비별사용량-7번섹션/PLAN1.md) (다음 단계 `/dev:plan` 에서 작성)
