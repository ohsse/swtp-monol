---
status: approved
created: 2026-06-10
updated: 2026-06-10
---
# 사용량트렌드-2번섹션 — 정수장 전체 전력량 추이 도메인 분석

## 작업 배경

사용량트렌드 대시보드(`backend/image/사용량트렌드.png`)의 **2번섹션** = 좌상단 단일 영역 차트("전력 사용량").
1번섹션이 제공하는 3개 파라미터(집계 버킷 `[시|일|월]`, 시작일자, 종료일자)를 받아 **정수장을 구성하는 모든
계측기가 보유한 전력적산태그(PWQ)** 를 이용해 **버킷 단위 전력량(kWh) 단일 전역 합산 시계열**을 산정해 반환하는
읽기 전용 조회 API를 신설한다. 조회 기간은 `시작일자 00:00` ~ `종료일자 익일 00:00`(배타적 상한)으로 종료일
당일 데이터를 모두 포함한다.

- 외부 산출물: `backend/image/사용량트렌드.png` (대시보드 와이어프레임 — 2번섹션 단일 영역 차트)
- 사용자 확정 결정 (plan 단계 AskUserQuestion): 패키지 `com.mo.swtp.opt`, **단일 전체 합산 시계열**, **sparse**(데이터 있는 버킷만)
- 핵심 계산(전체 PWQ 차분·버킷 집계)은 동형 선례 `PeakEnergyTrendService`(opt, 전역 PWQ ±12h 윈도우) + `RawDataRepository#findEnergyDeltaBuckets`(태그별 MAX-MIN 버킷 차분 native SQL)가 이미 존재 — **신규 발명 없이 기존 인프라 재사용 + 파라미터화**가 골자

## 회의록 (5인 회의 — Round 1 종결, 이견 없어 Round 2 불요)

### 안건 1: 패키지 배치·컨트롤러 분리·DTO 구조·계층 책임
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **패키지 `com.mo.swtp.opt`** 적정 — `PeakEnergyTrendService`가 이미 전역 PWQ 합산 시계열을 opt에서 처리하는 선례 성립
  - **신규 `EnergyUsageTrendController` 분리** 적정 — `PeakPowerAnalysisController`(`@Tag "14. 전력피크 분석"`)와 대시보드가 다름. 기존 컨트롤러 삽입 시 summary·description 맥락 혼선 (api-patterns.md §Swagger 단일 의미 원칙)
  - **SearchDto 동형 복제**(FacilityEnergyTrendSearchDto 복사) 적정 — api-patterns.md가 검색 DTO 공통 추상화를 "3건 이상 누적 후 별도 ANALYZE"로 보류. 사이클 간 자산 자동 원용 금지 정합
  - **Validator 별도 컴포넌트 불요** — 파라미터 3개 범위 검증은 단순. `isValid()` + Service throw로 충분 (FacilityEnergyTrendService 선례). 별도 컴포넌트화 시 §2 일회성 추상화 금지 저촉
  - **§2.5 면책 주석 의무** — 신규 native 합산 SQL 메서드에 `// §2.5 면책 (db/query-tuning.md §2)` 인용 근거 주석 미기재 시 REVIEW 블로커
  - 참고(낮음): `EnergyUsageTrendDto.points` 는 `@ArraySchema(schema = @Schema(implementation = Point.class))` 의무
- **결론**: opt 패키지 + 신규 컨트롤러 분리 + SearchDto 동형 복제 + isValid()+throw 패턴 확정. §2.5 면책 주석·`@ArraySchema` 적용 의무. Service는 `pwqTagSrlNos()`/`aggregate()`/`toPoints()` 수준 private 분해로 50줄 기준 유지

### 안건 2: DB 집계 쿼리·성능 (DBA 2차 승인)
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **신규 중첩 합산 SQL 채택 권고** — 대안(기존 `findEnergyDeltaBuckets` 재사용 + 메모리 합산)은 HOUR×396일×N태그 = 최대 9,504버킷×N행을 JVM 힙 적재 후 루프 합산 → 수십만 행 materialize·GC 압박·장시간 트랜잭션 우려. 신규 쿼리는 outer GROUP BY로 버킷당 1행만 전송
  - **파티션 프루닝 정상** — `acq_dtm >= :startDtm AND acq_dtm < :endDtm` 리터럴 범위 비교가 월 RANGE 프루닝 작동(date_trunc 표현식 무관). GROUP BY alias(`base_dtm`) 패턴은 기존 42803 회피 선례 동일
  - **인덱스 효과적** — `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)`가 inner 등가(tag_srl_no)+범위(acq_dtm) 집계를 Index Scan 커버
  - **권고(중간)**: `tag_delta >= 0` 방어 조건은 `MAX-MIN`이 그룹 내 수학적으로 항상 ≥0이라 **死코드**(§2 "발생 불가 시나리오 예외 처리 금지" 저촉). 제거 + PLAN 단계에서 delta=0 버킷 포함 여부 명시 결정
  - 참고(낮음): 실데이터 `EXPLAIN (ANALYZE, BUFFERS)` 500ms 임계 검증 권고 / 태그 100건 초과 시 `unnest(?, ?)` text[] 바인드 전환 검토(현재 수십 건 수준이라 `IN` 유지)
- **결론**: 신규 DB 레벨 합산 쿼리 채택. `tag_delta >= 0` 死코드 **제거**(delta=0 버킷은 "측정된 0 kWh 사용"으로 정당 → 포함, no-GOOD-data 버킷은 inner 행 부재로 자연 sparse). 태그 리스트는 `IN (:tagSrlNos)` 유지

### 안건 3: 도메인 4영역 점검·이중계상·scope
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **도메인 4영역 모두 비해당** — 읽기 전용 집계, 제어 명령·운전 모드·이력 기록 무접촉. `quality_cd='GOOD'` 필터는 §3 품질 정책 소비일 뿐 알람 임계값 신설 아님
  - **이중계상 가정 유효** — `PeakEnergyTrendService` javadoc("펌프 서브미터만 존재·ELCMTR 부재라 이중계상 없음, 향후 메인 적산미터 추가 시 별도 사이클 재검토") 가정이 동일 조건(`findByTagSeCdAndUseYn(PWQ,Y)`)으로 본 작업에 그대로 유효. 가정 섹션 명기 의무
  - **scope 해석 타당** — "정수장을 구성하는 모든 계측기" = 전역 활성 PWQ 태그 전체. facility 스코핑 의도적 제외
  - **롤오버/리셋 한계 수용** — 버킷 내 리셋 시 MAX-MIN 과대계상 가능(음수 가드로 탐지 불가). `ot-integration.md §3` GOOD raw_val 차분 정책 범위 내 알려진 트레이드오프, 기존 전력피크분석-5번섹션 동일. 별도 처리 의무 없음
  - **종료일 배타 상한 충족** — `acq_dtm < toDt.plusDays(1).atStartOfDay()` = toDt 당일 23:59 수집값까지 포함, 요건 정확 충족
  - **권고(중간)**: 가정 섹션 3건 명기 (`coding-discipline.md §1`)
- **결론**: 도메인 4영역 비해당 확정. 가정 3건(ELCMTR 이중계상·단일 정수장 스코프·버킷 내 리셋 과대계상 수용) 기재 의무

### 안건 4: 표준 사전 정합성
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **신규 표준 단어 0건** — `elcegVal`(elceg+val), `baseDtm`(base+dtm), `unit`(기존) 전부 기등록 단어 조합. `points`는 복수형 Java 필드명(사전 범위 외)
  - **"usage"·"trend" 등록 의무 없음** — standard-words.md는 "DB 컬럼명·테이블명 조합 재료" 대상. 신규 DB 컬럼 0건이라 클래스명·엔드포인트 경로 어휘는 등록 의무 미발생
  - **신규 표준 데이터 도메인·표준 용어 0건** — 신규 DB 컬럼 0건
  - **`opt` 기존 약어 충분** — "usage" 신규 비즈니스 도메인 약어 신설 불요
  - **기존 패밀리 충돌 없음** — `시설별사용량`(facility)·`설비별사용량`(instrument/facility)은 시설·설비 단위 분류 집계, 본 `사용량트렌드`(opt)는 전역 단일 합산 시계열. 집계 축·반환 형태 상이로 의미 중복 없음
- **결론**: 4층 사전 **갱신 0건**. PLAN 진입 전제조건 중 사전 관련 항목 전부 충족

## 표준 사전 카탈로그

### 신규 표준 단어
없음 (기존 재사용 2건: `elceg`+`val`, `base`+`dtm`)

### 신규 표준 데이터 도메인
없음 (신규 DB 컬럼 0건)

### 신규 표준 용어
없음 (신규 DB 컬럼 0건)

## 신규 엔티티/DB 컬럼

**없음.** 신규 엔티티·DB 테이블·DB 컬럼 0건. 기존 `rawdata_1m_h`·`tag_m` 읽기 전용 집계만 수행한다.
신규 자산은 모두 `api` 모듈의 조회 전용 Java 클래스(DTO·Service·Controller·record)와 기존 `OptErrorCode` enum 상수 1건 추가뿐이다.

## 기존 사전·패턴과의 충돌

충돌 없음. 5인 회의 4개 안건 모두 블로커(높음) 0건. DBA 권고(중간) 1건(`tag_delta >= 0` 死코드)·도메인 권고(중간) 1건(가정 3건 명기)·backend 참고(낮음) 1건(`@ArraySchema`)은 아래 PLAN 전달 결정으로 해소.

## PLAN 으로 전달할 결정 사항

- **패키지·계층**: `com.mo.swtp.opt` — `dto/EnergyUsageTrendSearchDto`·`dto/EnergyUsageTrendDto`·`service/EnergyUsageTrendService`·`web/EnergyUsageTrendController` 신규. `raw/dto/RawDataBucketSumDto`(record) 신규. `opt/exception/OptErrorCode`에 `INVALID_SEARCH_PERIOD(400)` 추가. `raw/repository/RawDataCustomRepository`(+Impl)에 합산 메서드 추가
- **엔드포인트**: `GET /api/opt/energy-usage-trend` (@ModelAttribute SearchDto), 신규 `@Tag`, `CommonController` 상속, `@ApiResponses` 200/400/401/500
- **SearchDto**: `FacilityEnergyTrendSearchDto` 동형 복제 — `inqUnit`/`fromDt`/`toDt` + `isValid()`(HOUR/DAY/MONTH·YEAR거부·≤396일) + `toStartDtm()` + `toEndExclusiveDtm()`(=`toDt.plusDays(1).atStartOfDay()`)
- **응답 DTO**: `PeakEnergyTrendDto` 미러링 — `{unit:"kWh", points:List<Point>}`, `Point{baseDtm(yyyy-MM-dd HH:mm:ss), elcegVal}`. BaseAuditResponseDto 미상속(집계 뷰). `points` 에 `@ArraySchema(schema=@Schema(implementation=Point.class))` 의무
- **합산 쿼리** (DB 레벨, §2.5 면책 `db/query-tuning.md §2` 주석 의무):
  ```sql
  SELECT base_dtm, SUM(tag_delta) AS total_val
  FROM (
      SELECT tag_srl_no, date_trunc(:unit, acq_dtm) AS base_dtm,
             MAX(raw_val) - MIN(raw_val) AS tag_delta
      FROM rawdata_1m_h
      WHERE tag_srl_no IN (:tagSrlNos)
        AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
        AND quality_cd = 'GOOD' AND raw_val IS NOT NULL
      GROUP BY tag_srl_no, base_dtm
  ) t
  GROUP BY base_dtm
  ORDER BY base_dtm
  ```
  - `tag_delta >= 0` 死코드 제거 (DBA 권고). delta=0 버킷은 "측정된 0 kWh"로 포함, no-GOOD 버킷은 자연 sparse
  - `:unit`은 `InqUnit.getDateTruncUnit()` text 바인드(4값 제약·인젝션 안전), `date_trunc(:unit)`는 SELECT 1회+GROUP BY alias(42803 회피)
- **Service 흐름**: `isValid()` 실패 → `RestApiException(OptErrorCode.INVALID_SEARCH_PERIOD)` → `tagRepository.findByTagSeCdAndUseYn(PWQ, Y)`로 전역 PWQ 태그 수집 → 합산 쿼리 → 시계열 매핑. 태그/데이터 0건 → 빈 `points`. private 분해로 50줄 유지
- **검증 권고(낮음, PLAN 성공 기준 외)**: 실데이터 `EXPLAIN (ANALYZE, BUFFERS)` 500ms 점검 / 태그 100건 초과 시 `unnest` 전환 재검토

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 현재 ELCMTR 메인 적산미터 PWQ 태그 미존재 — 펌프 서브미터만 합산하므로 이중계상 없음. 향후 메인 적산미터 `tag_se_cd='PWQ'` 시딩 시 이중계상 위험, 별도 사이클 재검토 | 가정 | `PeakEnergyTrendService` javadoc 기존 결정 정합 (domain-expert) |
| 단일 정수장 단위 운용 — 멀티테넌트 지자체별 분리는 DB 인스턴스 분리로 충족, facility 스코핑 불요 (전역 PWQ 합산) | 가정 | 사용자 요청 "정수장을 구성하는 모든 계측기" 정합 (domain-expert) |
| 버킷 내 적산미터 리셋·롤오버 발생 시 MAX-MIN 과대계상 가능 — 음수 가드는 리셋 직후 버킷만 탐지(리셋 전후 공존 버킷 탐지 불가). 전력피크분석-5번섹션 동일 방식, 수용 가정 | 가정 | `ot-integration.md §3` GOOD raw_val 차분 정책 범위 내 (domain-expert) |
| delta=0 버킷(측정값 존재·소비 0 kWh) 응답 포함 — no-GOOD-data 버킷(inner 행 부재)만 sparse 제외 | 결정 | DBA 권고 반영 (死코드 `tag_delta>=0` 제거) |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `EnergyUsageTrendService`가 유효 파라미터 → PWQ 태그 수집 + 합산 쿼리 호출 + 버킷별 시계열 매핑 | Mockito 단위 테스트 — tagRepository·rawDataRepository mock, 버킷 합산 결과 매핑 검증 GREEN |
| `isValid()` false(YEAR·기간역전·396일초과·null) → `RestApiException(INVALID_SEARCH_PERIOD)` | 단위 테스트 4케이스 GREEN — errorCode 단언 |
| PWQ 태그 0개 / 데이터 0건 → `points` 빈 리스트 | 단위 테스트 — 빈 태그·빈 버킷 입력 시 empty points 반환 |
| 종료일 익일 00시 배타 상한 계약 | `toEndExclusiveDtm() == toDt.plusDays(1).atStartOfDay()` 단언 |
| 전체 빌드·QClass 재생성 무결성 | ./gradlew.bat clean build BUILD SUCCESSFUL |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 조회 전용. `quality_cd='GOOD'` 필터는 §3 품질 정책 소비이며 알람 임계값·전이·복귀 조건 신설·변경 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 발행 경로 없음. 인터록 검사·기동 차단·복구 후 재검사 어느 것도 미해당 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 무접촉. 사용자 의도·시스템 상태 변경 또는 SCADA 5분 초과 강제 전환 경로 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h.transition_reason`·`pump_ctrl_h` 무접촉. 신규 DB 컬럼·엔티티 없음 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

**갱신 0건.** 4층 사전(ROOT 표준 단어·표준 데이터 도메인·비즈니스 도메인 약어 + backend 표준 용어) 모두 신규 등록·폐기 항목 없음 (`wtp-glossary-manager` 결론 — 신규 DB 컬럼 0건, `elceg`·`val`·`base`·`dtm`·`unit` 전부 기등록). 사전 관련 PLAN 진입 전제조건 충족.

## 산출물
- [계획안](../../../plan/20260610/사용량트렌드-2번섹션/PLAN1.md)
