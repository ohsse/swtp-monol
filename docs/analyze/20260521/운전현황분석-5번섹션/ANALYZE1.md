---
status: approved
created: 2026-05-21
updated: 2026-05-21
---
# 운전현황분석-5번섹션 — 도메인 분석

## 작업 배경

운전현황 분석 페이지 (`swtp/backend/image/운전현황분석.png`) 의 5번 섹션 "시계열 그래프" 카드의 backend API 신설.

4번 섹션 (`docs/analyze/20260521/운전현황분석-4번섹션/ANALYZE1.md`, 커밋 `00d8b8a` `FacilityOperatingStatusService`) 이 활성 시설의 **순시값 1건** 응답을 제공한다면, 본 5번 섹션은 같은 시설의 **1분 시점별 시계열** 을 금일 + 비교기간(YESTERDAY 또는 LAST_WEEK) 합본으로 반환한다. 전력원단위 산정식·시설 종류 지원범위(PWTF/DWT/PRSF)·On 판정·`effectiveVal`·`computeUnitConsumption` 정책은 모두 4번섹션과 동일.

### 카드 표출 (사용자 설명)

| 데이터 | 시점 단위 | 산출 방법 |
|--------|---------|---------|
| 금일 시계열 | 00:00 ~ 현재시간, 1분 단위 | 시점별 (가동 펌프 PWI GOOD 합산 / 시설 FRI GOOD) + 가동 펌프 대수 |
| 비교 시계열 | YESTERDAY 또는 LAST_WEEK 의 00:00~23:59, 1분 단위 | 동일 산식, 비교 기간 |

### 사용자 결정 (ANALYZE 진행 중 확정)

- D1: **시점 단위는 1분** — `rawdata_1m_h` 원본 그대로 표출. 10분 슬롯 집계 정책 폐기
- D2: **가동 펌프 대수** = 각 1분 시점에 OPS=1.0 GOOD 인 펌프 개수 (4번섹션 On 판정 동일)
- D3: **단일 endpoint** — 한 번 호출에 today + comparison 둘 다 반환
- D4: **본 사이클은 YESTERDAY · LAST_WEEK 두 옵션만** — LAST_MONTH_AVG 는 향후 별도 "데이터 집계" 사이클에서 처리
- D5: **today + comparison 은 2회 분리 호출** — Repository 두 번 호출 + Service 메모리에서 `acq_dtm` 기준 그룹화. 단일 OR 쿼리는 파티션 프루닝 손실 위험으로 거부 (안건 2 DBA 권고 채택)

### 외부 산출물

- `swtp/backend/image/운전현황분석.png` (5번 섹션) — Read 도구로 직접 시각 로드. 이미지 해상도 낮으나 사용자 설명으로 충분
- 4번 섹션 ANALYZE: `docs/analyze/20260521/운전현황분석-4번섹션/ANALYZE1.md` (`status: approved`) — 정책 재사용 근거

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 표준 사전 정합성 — 신규 어휘 후보 분류

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 후보 단어 5건 (`compare`·`series`·`today`·`comparison`·`week`) 전부 **등록 불필요** — Java 클래스명·enum 식별자·DTO 변수명 영역으로 표준 단어 사전 (DB 컬럼명 조합 재료) 적용 외. 4번 섹션 안건 1 결론 (영어 일반어 + Java 자유 명명 조합) 동일 적용. DTO 클래스명 `FacilityOperatingStatusTimeSeriesDto` 는 `naming.md {도메인명}Dto` 패턴 정합 — 4번 `FacilityOperatingStatusDto` 와 일관. enum 클래스명 `FacilityOperatingStatusCompareType` 의 `Type` suffix 는 `PumpOprtngType`·`PumpDriveType`·`EquipType` 선례 동형 (상호 배타적 분류). 본 사이클은 DB 테이블·컬럼 신규 추가 0건으로 표준 데이터 도메인·표준 용어 영향도 없음.
- **결론**: 표준 사전 갱신 0건. 룰 갱신 지시서 체크박스 0건 — PLAN 진입 전제조건 자동 충족.

### 안건 2: 시계열 Repository 쿼리 구현 방식 + 인덱스 활용

- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 본 사이클 신규 표준 데이터 도메인 0건 — 2차 승인 미발생. 발견 사항 3건:
    - **블로커(높음)**: DB→JVM 중간 결과 86,400행 (30태그 × 1440분 × 2범위) 단일 응답은 `query-tuning.md §1` "시계열 대용량 조회는 페이지네이션 또는 커서 기반 분할 조회" 위반.
    - **권고(중간) Q1**: Querydsl JPAQuery 빌더 우선 권고 — 단순 BETWEEN 범위는 DISTINCT ON 같은 PostgreSQL 전용 함수 불필요. 4번 섹션 native 와 본 5번 native 일관성보다 단순성 우선 (`coding-discipline.md §2`).
    - **권고(중간) Q2**: today + comparison 범위는 **2회 분리 호출** — 단일 OR 쿼리는 PostgreSQL 옵티마이저가 파티션별 OR 절 적용에 실패하여 전체 스캔 가능성. Service 계층 `Stream.concat` 으로 메모리 결합.
    - **참고(낮음) Q3**: 기존 인덱스 `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)` 충분 — 등가 조건 IN + 범위 acq_dtm 순서로 `indexing-and-migration.md §1` 컬럼 순서 원칙 준수. 추가 인덱스 불필요.
- **사용자 결정 (Round 2 대체)**: 블로커 해소 방향 — "2회 분리 호출 + Service 메모리 그룹화" 채택. 응답 DTO 는 그룹화 후 ~2,880 TimeSeriesPoint (약 300KB) 로 축소. DB→JVM 중간 결과 일시 메모리 점유 ~8MB 수준은 허용. 페이지네이션·시간범위 축소·10분슬롯 재도입 모두 거부.
- **결론**:
  - Repository 구현 = Querydsl JPAQuery 빌더 (`RawDataCustomRepository.findByTagSrlNosAndDtmRange`)
  - today + comparison = **2회 분리 호출**. Service 계층 `Stream.concat` 결합 + `acq_dtm` 기준 그룹화
  - 인덱스 신규 추가 없음 (기존 `idx_rawdata_1m_h_tag_time` 활용)
  - SLA: 각 호출 200ms 이내 (`EXPLAIN ANALYZE` PLAN 단계 검증 의무)

### 안건 3: Service·DTO·Controller 설계 + 공통 헬퍼 추출 정책 + 정량 기준

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 발견 사항 — 블로커 0건 / 권고(중간) 4건:
    - **Q1 권고**: 4번 헬퍼 5건 (`findActiveFacilityOrThrow`·`effectiveVal`·`isPumpRunning`·`computeUnitConsumption`·`loadTagsByInstrument`) 의 공통 컴포넌트 추출은 **본 사이클 외** — `coding-discipline.md §3` "본인이 만든 코드의 뒷정리만 수행" + "사용 사례 2건 누적 트리거" 충족 시 별도 ANALYZE. 5번 서비스에 동일 계약 재구현. 계약 일치는 TASK 체크박스에 "4번 단위테스트 동일 입력/출력 검증" 명시 권고.
    - **Q2 권고**: 1분 시점별 집계 메서드는 §2.5 면책 영역 (OT 안전 도메인·QueryDSL 빌더) 미해당 → 50줄 분해 의무. 권장 분해 — "1분 시점별 OPS/PWI 집계" + "전력원단위 계산 + DTO 조립" 2단계. 추상화 깊이 3단 이내 유지.
    - **Q3 권고**: `TimeSeriesPoint` 는 `FacilityOperatingStatusTimeSeriesDto` 의 static inner class — top-level 분리는 추측성 추상화. `@Schema` 어노테이션 inner class 정상 동작.
    - **Q4**: Service 시그니처 `findFacilityOperatingStatusTimeSeries(facilityId, compareType)` — 4번 `findFacilityOperatingStatus(facilityId)` 와 동형. 클래스 레벨 `@Transactional(readOnly = true)`.
    - **Q5 권고**: `@Schema(implementation = FacilityOperatingStatusCompareType.class)` + `@ArraySchema(schema = @Schema(implementation = TimeSeriesPoint.class))` 명시 의무. `TimeSeriesPoint.acqDtm` 는 `@JsonFormat("yyyy-MM-dd HH:mm:ss")` 초 단위 SSOT — 4번 섹션 정렬.
- **결론**:
  - Service: `FacilityOperatingStatusTimeSeriesService` 신규 분리 (4번 서비스 코드 0건 수정)
  - DTO: `FacilityOperatingStatusTimeSeriesDto` (facilityId·facilityNm·compareType·todaySeries·comparisonSeries) + static inner class `TimeSeriesPoint` (acqDtm·onPumpCnt·totalElpwrAmt·elpwrUnitQty)
  - enum: `FacilityOperatingStatusCompareType` (YESTERDAY · LAST_WEEK)
  - Controller: `FacilityController` 메서드 추가 — `GET /api/facility/{facilityId}/operating-status/timeseries?compareType={...}`
  - 정량 기준: 집계 메서드 50줄 초과 시 2단계 분해 (PLAN 단계 명시)
  - `@Schema(implementation)` + `@JsonFormat` 의무 적용

### 안건 4: 도메인 룰 4영역 점검 + SCADA 품질 정책 적용 + 동기 수집 가정

- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 발견 사항 — 블로커 0건 / 권고(중간) 1건 + 참고(낮음) 2건:
    - **Q1 참고**: 4영역 (알람·인터록·AI 운전 모드·이력 기록) 모두 "비해당" 판정 타당. "비해당" 단독 4건 차단 해제 조건 (각 행 구체 사유 + 신규 엔티티 "없음") 두 가지 모두 충족 가능 — 본 사이클 신규 엔티티·DB 컬럼 0건. 각 행에 구체 사유 명기 의무 ("알람 생성·전이 없음·alarm_h INSERT 미수행" 수준).
    - **Q2 권고**: `ot-integration.md §3` Hold Last Value 정책은 SCADA 수신 파이프라인 (`ScadaMessageProcessor`) 이 `corr_val` 을 채울 때 적용 — 5번 섹션 Service 는 재적용 의무 없음. **OPS BAD 시점은 `onPumpCnt` 에서 자동 제외됨** (`quality_cd = GOOD AND raw_val = 1.0` 조건) — 운전원 오인 방지를 위해 응답 DTO `@Schema(description)` 또는 PLAN "## 성공 기준" 에 명시 의무.
    - **Q3 참고**: `rawdata_1m_h.acq_dtm` 의 다중 태그 동기 수집은 §3 명시 보장 없음 — SCADA 미들웨어·폴링 지연으로 동일 분 내 수십 초 편차 가능. 본 사이클은 **동일 `acq_dtm` 행 끼리만 한 시점으로 묶음** 가정 — "## 가정 및 미해결 질문" 명시 의무.
    - **Q4 참고**: LAST_MONTH_AVG 누락 운영 의미 결손 없음 — 전일·지난주 비교는 단기 이상 감지에 충분.
- **결론**:
  - 4영역 비해당 — 각 행 구체 사유 명기 (본 ANALYZE "## 도메인 룰 4영역 점검" 섹션 적용)
  - OPS BAD 시점 onPumpCnt 제외 동작 — DTO 또는 PLAN 명시 의무
  - 다중 태그 동기 수집 가정 — "## 가정 및 미해결 질문" 명시 의무

---

## 표준 사전 카탈로그

본 사이클은 표준 사전 3층 모두에 신규 등록 항목이 없음 (안건 1 wtp-glossary-manager Round 1 결론). 표 생략.

---

## 신규 엔티티/DB 컬럼

**없음** — 본 사이클은 조회 전용 API 신설로 신규 엔티티·DB 컬럼·DDL·인덱스 추가 0건.

신규 Java 클래스 (DB 영향 없음):
- `api/src/main/java/com/mo/swtp/facility/dto/FacilityOperatingStatusTimeSeriesDto.java` (응답 DTO + static inner `TimeSeriesPoint`)
- `api/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityOperatingStatusCompareType.java` (enum)
- `api/src/main/java/com/mo/swtp/facility/service/FacilityOperatingStatusTimeSeriesService.java` (Service)

수정 클래스:
- `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` (메서드 추가)
- `api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` + Impl (Querydsl 메서드 추가)

---

## 기존 사전·패턴과의 충돌

**없음** — 표준 사전 영향 0건, 도메인 룰 4영역 모두 비해당, 4번 섹션 정책 그대로 1분 시점 시계열로 확장.

---

## PLAN 으로 전달할 결정 사항

- **도메인 모델 초안**: 4번 섹션 정책 (effectiveVal·isPumpRunning·computeUnitConsumption) 을 1분 시점 단위로 동일 적용. 4번 헬퍼 5건은 본 사이클에서 5번 서비스에 재구현 (4번 코드 수정 0건).
- **API 설계**: 단일 endpoint `GET /api/facility/{facilityId}/operating-status/timeseries?compareType={YESTERDAY|LAST_WEEK}`. 응답 `CommonResponseDto<FacilityOperatingStatusTimeSeriesDto>`.
- **DB 설계 변경**: 없음. 기존 인덱스 `idx_rawdata_1m_h_tag_time` 활용.
- **Repository 패턴**: `RawDataCustomRepository.findByTagSrlNosAndDtmRange(tagSrlNos, startDtm, endDtm)` Querydsl 빌더. Service 가 today/comparison 2회 호출 후 `acq_dtm` 기준 그룹화.
- **DTO 구조**: `FacilityOperatingStatusTimeSeriesDto` (5필드) + static inner `TimeSeriesPoint` (4필드). `@Schema(implementation)` + `@JsonFormat` 의무 적용.
- **정량 기준**: 1분 시점별 집계 메서드 §2.5 면책 불가 → 2단계 private 헬퍼 분해 (PLAN 단계 명시).
- **응답 명세 의무**: OPS BAD 시점 `onPumpCnt` 자동 제외 동작 `@Schema(description)` 명시.

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 다중 태그 동기 수집 — 같은 1분에 OPS·PWI·FRI 가 동일 `acq_dtm` 으로 수집된다는 가정. SCADA 미들웨어·폴링 지연으로 수십 초 편차 가능 (안건 4 Q3) | 가정 | PLAN 단계에서 동일 `acq_dtm` 그룹화 기준 명시. 향후 편차 관측 시 `date_trunc('minute', acq_dtm)` 기준으로 보강 검토 |
| 1분 시점에 일부 태그만 결측 → `onPumpCnt = 0` 또는 `elpwrUnitQty = null` (4번 정책 1분 단위 분단위 확장) | 가정 | PLAN 단계 명시 — 빈 1분 시점 (OPS·PWI·FRI 전부 부재) 은 응답 리스트에서 생략 |
| YESTERDAY/LAST_WEEK 시간 범위 = `LocalDate.now().minusDays(1|7)` 의 00:00:00 ~ 23:59:59 (시간대 일관성, 시스템 LocalDateTime 기준) | 가정 | PLAN 단계 확정 |
| 응답 크기 SLA — 각 시리즈 최대 1440 포인트 × 2 = 2,880 포인트, 약 300KB | 미해결 | PLAN 단계 EXPLAIN ANALYZE 결과로 검증 (단일 호출 200ms 이내) |
| 응답 정렬 — todaySeries / comparisonSeries 각각 `acq_dtm` 오름차순 | 결정 | PLAN 단계 명시 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 |
|---------|--------------------------|
| `FacilityOperatingStatusTimeSeriesService` 의 단위 테스트 — 정상 시계열·빈 시계열·1분 시점 결측·UNCERTAIN 제외·비활성 시설 거부 5건 GREEN | `./gradlew.bat :api:test` PASS, 신규 테스트 클래스 5케이스 |
| `compareType=YESTERDAY` 와 `LAST_WEEK` 두 옵션 모두 응답 직렬화 정상 | Swagger UI 수동 호출 또는 통합 테스트 |
| Repository 두 시간 범위 호출 각각 200ms 이내 + 파티션 프루닝 적중 | `EXPLAIN (ANALYZE, BUFFERS)` 출력에 `Subplans Removed` 또는 `Partitions Selected` 1~2 표시 |
| 1분 시점별 acq_dtm 그룹화 후 응답 ordering 오름차순 | 통합 테스트 또는 Swagger 수동 검증 — `todaySeries[i].acqDtm < todaySeries[i+1].acqDtm` |
| OPS BAD 시점 `onPumpCnt` 제외 동작 — Swagger description 명시 + 단위 테스트로 GOOD/BAD 혼재 시 GOOD 만 카운트 검증 | 단위 테스트 + Swagger UI 한국어 description 명시 확인 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 알람 생성·전이 없음, `alarm_h` INSERT 미수행. 조회 전용 API 로 임계값·전이 조건·복귀 조건 변경 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 제어 명령 미발행, `ot-integration.md §2` 아웃바운드 경로 (`ScadaOutboundPort`) 미진입. 인터록 룰 `pump_interlock_p` 미참조 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p`·`ai_drvn_mod_h` 미참조. SCADA 5분 강제 전환 판정 로직 미포함. 사용자 의도·시스템 상태 변경 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 새 이력 INSERT 없음, `pump_ctrl_h`·`ai_drvn_mod_h` 무접촉. `transition_reason` 컬럼 영향 없음 |

"비해당" 단독 4건 차단 해제 조건 충족: (1) 각 행 구체 사유 명기 (위 표), (2) "## 신규 엔티티/DB 컬럼" 섹션 "없음" 동시 충족.

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

본 사이클은 표준 사전 갱신 0건 + DB 영향 0건 + 도메인 룰 4영역 비해당으로 룰 갱신 지시서 체크박스 0건. PLAN 진입 전제조건 자동 충족.

---

## 산출물

- [계획안](../../../plan/20260521/운전현황분석-5번섹션/PLAN1.md) (status: draft 예정)
