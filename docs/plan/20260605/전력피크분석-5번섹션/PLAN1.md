---
status: approved
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석 5번섹션 — 전력량 추이(발생·예측) 조회 API

## 목적

전력피크분석 화면(`backend/image/전력피크분석.png`) **5번 섹션**("전력 피크 예상 시간" — 하단 추세 차트) 백엔드 조회 API 를 구현한다. 현재시각 기준 **±12시간(총 24시간)** 윈도우 단일 추세를 표출한다:

- **발생 전력량**(12h전~현재): 실측 적산전력량(PWQ) 1시간 버킷 차분 — 시스템 전역 합산 (kWh)
- **예측 전력량**(현재~12h후): 예측 적산전력량(PWQ) 1시간 버킷 차분 — 시스템 전역 합산 (kWh)
- **요금적용전력피크**(kW) · **목표피크**(kW): 차트 가로 기준선 스칼라

## 배경

- 1·2·3·4번 섹션(목표값 마스터+SSE / 5지표 집계 / 펌프 순시전력 / 시설 펌프 전력량 예측 시계열) 완료 상태.
- ANALYZE1 (`docs/analyze/20260605/전력피크분석-5번섹션/ANALYZE1.md`, status: approved) 의 5인 회의 결론을 본 PLAN 이 구현 계획으로 확정한다.
- 본 섹션은 섹션4(`PumpEnergyPredictionService` — 시설 펌프 PWQ 예측 시계열) 와 **구조 동형**이며, 차이는 (1) 시설 단위 → 시스템 전역 전체 PWQ, (2) 발생(rawdata) 12h 시계열 추가, (3) 요금/목표 피크 스칼라 동봉이다.

## 범위

- **영향 모듈**: `api` 단일. `common`·`scheduler` 변경 0건 (신규 엔티티·DDL·QClass 재생성 없음).
- **신규 파일** (2건):
  - `api/src/main/java/com/mo/swtp/opt/dto/PeakEnergyTrendDto.java`
  - `api/src/main/java/com/mo/swtp/opt/service/PeakEnergyTrendService.java`
- **수정 파일** (2건):
  - `api/src/main/java/com/mo/swtp/opt/web/PeakPowerAnalysisController.java` — GET 메서드 1개 추가
  - `api/src/main/java/com/mo/swtp/opt/repository/PumpEnergyPredcRepository.java` — Javadoc 스코프 이력 갱신 (코드 시그니처 불변)
- **신규 테스트** (1건):
  - `api/src/test/java/com/mo/swtp/opt/service/PeakEnergyTrendServiceTest.java`

## 구현 방향

### 1. 엔드포인트 (Controller 확장)

기존 `PeakPowerAnalysisController` (`@Tag "14. 전력피크 분석"`, `@RequestMapping("/api/opt/peak-power-analysis")`) 에 GET 메서드 1개 추가:

```
GET /api/opt/peak-power-analysis/energy-trend
```

- **파라미터 없음** — 시스템 전역 조회 (ANALYZE 결정: `facilityId` 불요).
- 응답: `ResponseEntity<CommonResponseDto<PeakEnergyTrendDto>>` (CommonController.getResponseEntity 래핑).
- `@Operation`/`@ApiResponses`(200/401/500) — 500 description 에 "목표값 시드 미초기화 포함" + "현재 시 버킷 예측 부분집계" 명기.
- 컨트롤러는 `peakEnergyTrendService.getEnergyTrend()` 위임만 수행 (비즈니스 로직 직접 보유 금지, `api/CLAUDE.md` Controller 규칙).

### 2. Service (신규 `PeakEnergyTrendService`)

`@Service @RequiredArgsConstructor @Transactional(readOnly = true) @Slf4j`. 의존성 4개: `TagRepository`, `RawDataRepository`, `PumpEnergyPredcRepository`, `PeakTargetService`.

상수: `UNIT_KWH = "kWh"`, `WINDOW_HOURS = 12`, `BILLING_LOOKBACK_MONTHS = 12`.

공개 메서드 흐름 (본문 ≤50줄):

```
now  = LocalDateTime.now();  base = now.truncatedTo(ChronoUnit.HOURS)
targetPeakElpwr = peakTargetService.getPeakTarget().getTargetPeakElpwr()   # fail-fast 시드 검증
pwqSrlNos = pwqTagSrlNos()    # tag_se_cd='PWQ' AND use_yn='Y' 전역
pwiSrlNos = pwiTagSrlNos()    # tag_se_cd='PWI' AND use_yn='Y' 전역 (요금피크용)

measured  = aggregateMeasured(
    rawDataRepository.findEnergyDeltaBuckets(pwqSrlNos, base-12h, base, InqUnit.HOUR.getDateTruncUnit()))
predicted = aggregatePredicted(
    pumpEnergyPredcRepository.findEnergyDeltaBuckets(pwqSrlNos, base, base+12h))
billingPeakElpwr = billingPeak(pwiSrlNos, now)   # findMaxMinuteSumElpwr(now-12mo, now) ?? ZERO

return PeakEnergyTrendDto.of(UNIT_KWH, targetPeakElpwr, billingPeakElpwr, measured, predicted)
```

private 헬퍼:
- `pwqTagSrlNos()` / `pwiTagSrlNos()` — `tagRepository.findByTagSeCdAndUseYn(타입, YnType.Y)` → `Tag::getTagSrlNo` (섹션2 `pwiTagSrlNos()` 동형).
- `aggregateMeasured(List<RawDataBucketDto>)` / `aggregatePredicted(List<PredcEnergyBucketDto>)` — 각 버킷을 `validDeltaOrNull(...)` 통과 후 `TreeMap` merge → `PeakEnergyTrendPoint` 매핑. 두 헬퍼는 입력 record 타입만 다르고 본문 동형(섹션4 `aggregateByBucket` 깊이와 동일). 음수 차분 가드는 공유 `validDeltaOrNull` 단일 소스로 추출.
- `validDeltaOrNull(BigDecimal aggrVal, String tagSrlNo, LocalDateTime baseDtm)` — null/음수(적산 리셋·롤오버) 시 null + 음수는 WARN 로그 (섹션4 동형). **공유 단일 소스**.
- `billingPeak(List<String> pwiSrlNos, LocalDateTime now)` — `findMaxMinuteSumElpwr` 결과 null 시 ZERO (섹션2 `billingPeak` 동형).

> **2 헬퍼 분리 정당화** (`coding-discipline.md §2` 점검): `RawDataBucketDto`(raw 도메인)·`PredcEnergyBucketDto`(opt 도메인) 는 형상 동일하나 패키지가 다른 별개 record 로 공통 인터페이스 부재. 통합하려면 `common`·`opt` record 에 공유 인터페이스 신설(모듈 침범·과잉 추상화) 필요 → backend-engineer 권고대로 2 thin 헬퍼 유지 + 음수 가드만 단일 소스 공유. 추상화 깊이 Controller→Service→aggregate→validDeltaOrNull = 섹션4 승인 선례(`aggregateByBucket → validDeltaOrNull`) 와 동일.

### 3. Repository (재사용 — 신규 0건)

| 책임 | 메서드 | 비고 |
|------|--------|------|
| 발생 PWQ 버킷 차분 | `RawDataRepository.findEnergyDeltaBuckets(pwqSrlNos, base-12h, base, "hour")` | raw 공용 재사용. `GOOD`+`raw_val` 단독 = PWQ 정책(`ot-integration.md §3`) 정합 |
| 예측 PWQ 버킷 차분 | `PumpEnergyPredcRepository.findEnergyDeltaBuckets(pwqSrlNos, base, base+12h)` | 섹션4 재사용. `predc_1m_h` 임의 `tag_srl_no IN` 동작 — 도메인 중립. Javadoc 스코프만 "시설 PWQ → 전역 PWQ 추세 확장" 이력 추가 |
| 요금피크 | `RawDataRepository.findMaxMinuteSumElpwr(pwiSrlNos, now-12mo, now)` | 섹션2 재사용 |
| 목표피크 | `PeakTargetService.getPeakTarget()` | 시드 부재 시 `OptErrorCode.PEAK_TARGET_NOT_INITIALIZED`(500) 전파 |
| 태그 조회 | `TagRepository.findByTagSeCdAndUseYn(타입, YnType.Y)` | 섹션2 재사용 |

`PumpEnergyPredcRepository` Javadoc 갱신은 코드 시그니처·SQL 불변 — 스코프 서술만 보강 (재사용 이력 추적).

### 4. DTO (신규 `PeakEnergyTrendDto`)

`PumpEnergyPredictionDto` 패턴 미러링 (plain `@Getter` + private 기본 생성자 + 정적 팩토리 `of(...)`, `BaseAuditResponseDto` 미상속 — 집계 뷰 분류, `api-patterns.md` 정합).

| 필드 | 타입 | 설명 |
|------|------|------|
| `unit` | `String` | 시계열 단위 "kWh" (스칼라는 kW — 단위 혼재는 Swagger description 명기) |
| `targetPeakElpwr` | `BigDecimal` | 목표 피크 전력값 (kW, 기준선) |
| `billingPeakElpwr` | `BigDecimal` | 요금적용전력피크 (kW, 기준선) |
| `measuredPoints` | `List<PeakEnergyTrendPoint>` | 발생 전력량 시계열 (kWh, 최대 12버킷) |
| `predictedPoints` | `List<PeakEnergyTrendPoint>` | 예측 전력량 시계열 (kWh, 최대 12버킷, 현재 시 부분집계) |

중첩 `PeakEnergyTrendPoint` (static class, 섹션4 Point 동형):

| 필드 | 타입 | 설명 |
|------|------|------|
| `baseDtm` | `LocalDateTime` | 버킷 시작 일시 (시 단위 절삭). `@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")` |
| `elcegVal` | `BigDecimal` | 버킷 전역 합산 전력량 (kWh) |

- `measuredPoints`·`predictedPoints` 두 `List<사용자정의>` 필드에 **`@ArraySchema(schema=@Schema(implementation=PeakEnergyTrendPoint.class))` 명기 의무** (backend-engineer 권고, `api-patterns.md §DTO @Schema(implementation)`).

## 도메인 모델

신규 엔티티·테이블·필드 **0건**. 기존 3종 재사용:

| 엔티티/테이블 | 역할 | 사용 컬럼 |
|--------------|------|----------|
| `RawData` / `rawdata_1m_h` | 발생 PWQ 실측 적산값 | `tag_srl_no`·`acq_dtm`·`raw_val`·`quality_cd` (조회 전용) |
| `TagPrediction` / `predc_1m_h` | 예측 PWQ 적산값 | `tag_srl_no`·`predc_dtm`·`predc_val` (조회 전용) |
| `PeakTarget` / `opt_peak_target_p` | 목표 피크 마스터 | `peak_cd`·`target_peak_elpwr` (조회 전용) |
| `Tag` / `tag_m` | PWQ·PWI 태그 식별 | `tag_se_cd`·`use_yn`·`tag_srl_no` (조회 전용) |

> 신규 도메인 모델·DB 설계 변경이 없으므로 **도메인·DB 검토 게이트 생략** (PLAN 스킬 §도메인·DB 검토 게이트 — "도메인 모델과 DB 변경이 모두 없는 경우 생략"). ANALYZE1 안건 3(dba)·안건 4(domain-expert) 에서 조회 경로·전역 합산 정책은 이미 검토 완료.

## DB 설계 변경

없음 — read-only 조회. 신규 테이블·컬럼·인덱스·DDL·마이그레이션 0건. 기존 인덱스 `idx_rawdata_1m_h_tag_time`·`idx_predc_1m_h_tag_time` 활용 + `acq_dtm`/`predc_dtm` 범위로 월 RANGE 파티션 프루닝 (ANALYZE1 안건 3 dba 확인).

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 방법 |
|------|----------|
| 윈도우 분할 경계 정확성 | `PeakEnergyTrendServiceTest` — `now=10:30` mock 시 measured 인자가 `findEnergyDeltaBuckets(pwq, 2026-06-04 22:00, 2026-06-05 10:00, "hour")`, predicted 가 `(pwq, 10:00, 22:00)` 임을 ArgumentCaptor 로 검증 GREEN |
| 버킷별 전역 합산 + 음수 차분 제외 | 2태그 서로 다른 baseline mock → 동일 baseDtm 버킷에서 `SUM(태그별 MAX-MIN)` 일치, 음수 aggrVal 버킷은 결과 미포함 단위 테스트 GREEN |
| PWQ/PWI 태그 0개 | 태그 빈 리스트 mock → measuredPoints=[], predictedPoints=[], billingPeakElpwr=ZERO, targetPeakElpwr=목표값 반환 GREEN |
| 예측·실측 0행 | 버킷 빈 리스트 mock → 빈 시계열 + 스칼라 정상 GREEN |
| 목표 시드 부재 전파 | `peakTargetService.getPeakTarget()` 가 `PEAK_TARGET_NOT_INITIALIZED` throw mock → 동일 예외 전파 + ErrorCode 일치 GREEN |
| 빌드·회귀 통과 | ./gradlew.bat :api:test BUILD SUCCESSFUL, 기존 테스트 회귀 0건 |
| Swagger 노출 | 빌드 후 신규 엔드포인트 @Operation/@ApiResponses(200/401/500) + 두 List 필드 @ArraySchema 컴파일 확인 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 전역 PWQ 합산 = 전체 활성 PWQ 태그(`tag_se_cd='PWQ' AND use_yn='Y'`). 현재 펌프 8개·메인미터 0개라 이중계상 없음 | 결정 | ANALYZE AskUserQuestion 재확정. 향후 ELCMTR 메인 적산미터 추가 시 이중계상 위험 → 그 시점 별도 사이클 재검토 (코드 주석·Javadoc 명기) |
| 윈도우 = `base=date_trunc('hour',now)`, 발생 `[base-12h, base)` / 예측 `[base, base+12h)`. 현재 시 버킷은 예측 포함(부분 집계 — 과소 가능) | 결정 | 섹션4 동형. Swagger description 에 "현재 시 버킷 예측 부분집계" 명기 |
| 빈 버킷(데이터 0)은 응답 생략 (24슬롯 null 고정 채움 아님). 태그/데이터 0건은 빈 시계열(200) | 결정 | 섹션4 동형. repo 빈 입력 시 `List.of()` 반환으로 자연 처리 (명시 빈 분기 불요) |
| 요금/목표 스칼라(kW) 섹션5 응답 포함 + 독립 재계산. 12개월 스캔 캐싱 미도입 | 결정 | backend-engineer (a) 채택. dba 캐싱 권고는 섹션2·5 횡단 최적화로 향후 별도 검토 |
| kWh(발생·예측) / kW(요금·목표) 단위 혼재 — 백엔드 변환 금지, 프론트 이중축 | 결정 | `unit`="kWh" 는 시계열 단위. 스칼라 kW 는 Swagger description 명기 |
| `validDeltaOrNull`·`aggregate*` 헬퍼는 섹션5 신규 작성 (섹션4 동형 미러링, 자동 원용 아님) | 결정 | 사이클 간 자산 자동 원용 금지 정합 — 섹션4 메서드 직접 호출 아닌 동형 신규 작성 |

## 제외 사항

- 요금피크 캐싱(`@Cacheable` 또는 섹션2 재사용) — 향후 횡단 최적화 별도 사이클.
- ELCMTR 메인 적산미터 이중계상 방어 로직 — 현 시점 메인미터 부재로 불요, 도입 시점 재검토.
- 24슬롯 고정 채움(빈 버킷 0 fill) — 프론트 표출 책임.
- frontend SPEC 전파 — `/dev:spec` 선택 단계 (커밋 후).

## 예상 산출물
- [태스크](../../../tasks/20260605/전력피크분석-5번섹션/TASK1.md)
