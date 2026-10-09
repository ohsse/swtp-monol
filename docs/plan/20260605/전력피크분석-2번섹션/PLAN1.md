---
status: approved
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석-2번섹션 — 4지표 조회 API 구현 계획

## 목적

전력피크 분석 화면(`backend/image/전력피크분석.png`) 좌측 **2번 섹션**의 4지표를 단일 GET 응답으로 표출하는 **읽기 전용 집계 API** 1개를 `opt` 도메인(`com.mo.swtp.opt`)에 추가한다. 신규 테이블·DDL·컬럼·인덱스는 0건이며, 기존 `tag_m`·`rawdata_1m_h`·`opt_peak_target_p`(1번섹션 산출물)·`predc_1m_h` 를 조회·집계하는 합성 뷰다.

| 지표 | 응답 필드 | 산출 |
|------|----------|------|
| 총순시전력 | `totalElpwr` (kW) | 활성 PWI 태그 최신값(GOOD) 합산 |
| 목표피크전력 | `targetPeakElpwr` (kW) | 1번섹션 `PeakTargetService.getPeakTarget()` 재사용 |
| 요금적용전력피크 | `billingPeakElpwr` (kW) | 최근 12개월 분(1분)단위 PWI 합산값 중 MAX |
| 전력피크예상시간 | `predcPeakDtm` (일시, null=없음) | 예측 PWI 합이 목표피크 초과하는 최근접 미래 시각 |

## 배경

- 직전 커밋 `d0f9898`(전력피크분석-1번섹션)이 `opt_peak_target_p`(시스템 전역 단일행, `target_peak_elpwr`) + DomainEvent/SSE 를 완성했다. 본 작업은 그 목표값과 예측 시계열(`predc_1m_h`)을 소비한다.
- ANALYZE1(`docs/analyze/20260605/전력피크분석-2번섹션/ANALYZE1.md`, status: approved) 5인 회의 결론을 구현으로 전개한다. 신규 표준단어 `total`·`billing` 등록 완료, DB 컬럼·데이터 도메인 신규 0건.
- live DB(2026-06-05): 활성 PWI 태그 8개, `predc_1m_h` 예측 태그 = PWI 와 동일 `tag_srl_no`(8/8 매칭). 소팬아웃.

## 범위

### 포함
- 신규 합성 뷰 API 1개: `GET /api/opt/peak-power-analysis`
- 신규 클래스 6: Controller / Service / 응답 DTO / `PeakPredcRepository`·`PeakPredcCustomRepository`·`PeakPredcCustomRepositoryImpl`
- 기존 파일 신규 메서드 추가 2: `TagRepository.findByTagSeCdAndUseYn`(파생쿼리), `RawDataCustomRepository`/`Impl` 12개월 MAX native
- 설정 키 1: `opt.peak.predc-horizon-hours`(기본 48) — 예측 파티션 프루닝 상한
- 단위 테스트: `PeakPowerAnalysisServiceTest`(Mockito)

### 제외
- 신규 엔티티·테이블·DDL·인덱스 (0건 — 읽기 전용)
- SSE 실시간 전파 (단순 GET 폴링 — 목표값 변경은 1번섹션 SSE 구독 중, 사용자 확정)
- 요금적용전력피크 발생 시각 (값만 반환, 사용자 확정)
- 사전집계 테이블(`rawdata_15m_l` 등) 도입 — 1년 누적 후 EXPLAIN 200ms 초과 시 별도 ANALYZE

## 구현 방향

### 패키지·클래스 배치 (`com.mo.swtp.opt`, api 모듈)
1번섹션 `PeakTarget*`(목표값 마스터)과 의미 분리한 `PeakPowerAnalysis*`(분석 집계 뷰) 네이밍.

```
api/src/main/java/com/mo/swtp/opt/web/PeakPowerAnalysisController.java          (신규)
api/src/main/java/com/mo/swtp/opt/service/PeakPowerAnalysisService.java         (신규)
api/src/main/java/com/mo/swtp/opt/dto/PeakPowerAnalysisDto.java                 (신규)
api/src/main/java/com/mo/swtp/opt/repository/PeakPredcRepository.java           (신규)
api/src/main/java/com/mo/swtp/opt/repository/PeakPredcCustomRepository.java     (신규)
api/src/main/java/com/mo/swtp/opt/repository/PeakPredcCustomRepositoryImpl.java (신규)
api/src/main/java/com/mo/swtp/tag/repository/TagRepository.java                 (메서드 1 추가)
api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java       (메서드 1 추가)
api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java   (메서드 1 추가)
api/src/main/resources/application-common.yml                                  (opt.peak.predc-horizon-hours 추가)
api/src/test/java/com/mo/swtp/opt/service/PeakPowerAnalysisServiceTest.java    (신규)
```

### ① 총순시전력 `totalElpwr` — 기존 최신값 조회 재사용
- PWI 태그 식별: `TagRepository.findByTagSeCdAndUseYn(TagMeasurementType.PWI, YnType.Y)` (신규 파생쿼리) → `Tag::getTagSrlNo` 추출. 스코프가 전 PWI 태그(설비·On 무관)이므로 instrument 경유 불요 — `FacilityOperatingStatusService`(On펌프·시설 한정)와 차이.
- `RawDataRepository.findLatestByTagSrlNos(tagSrlNos)` **재사용**(native DISTINCT ON, 1시간 윈도우 — 1분 그리드에서 항상 최신 1건 확보).
- Service 합산: `qualityCd == GOOD` 필터 → `effectiveVal`(corrVal 우선, null 시 rawVal) → `null` 제외 → `reduce(BigDecimal.ZERO, BigDecimal::add)`. `ot-integration.md §3` PWI 결측 정책 정합.

### ② 목표피크전력 `targetPeakElpwr` — 1번섹션 서비스 재사용
- `peakTargetService.getPeakTarget().getTargetPeakElpwr()`. 시드 미초기화 시 `OptErrorCode.PEAK_TARGET_NOT_INITIALIZED`(500) 전파(1번섹션과 일관). 이 값은 ④ 임계값으로도 재사용.

### ③ 요금적용전력피크 `billingPeakElpwr` — 12개월 분단위 SUM 후 MAX (신규 native, raw 도메인)
중첩집계(분단위 SUM 의 MAX)는 JPQL/Querydsl 의 FROM-서브쿼리 미지원으로 **native 필수**. `RawDataCustomRepositoryImpl`(EntityManager) 에 추가 — `findLatestByTagSrlNos` native 선례 동형.

```sql
SELECT MAX(minute_sum) FROM (
  SELECT acq_dtm, SUM(COALESCE(corr_val, raw_val)) AS minute_sum
  FROM rawdata_1m_h
  WHERE tag_srl_no IN (:tagSrlNos)
    AND acq_dtm >= :startDtm AND acq_dtm < :endDtm   -- now-12개월 .. now
    AND quality_cd = 'GOOD'
  GROUP BY acq_dtm                                    -- 결측: 존재하는 GOOD만 합산(부분합)
) m
```

- 시그니처: `BigDecimal findMaxMinuteSumElpwr(List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm)`. `startDtm = now.minusMonths(12)`, `endDtm = now`.
- `acq_dtm` BETWEEN 으로 월 RANGE 파티션 프루닝(12개월 한정) + `idx_rawdata_1m_h_tag_time(tag_srl_no, acq_dtm)` Index Range Scan. `COALESCE(corr_val, raw_val)` = effectiveVal SQL 동치(`findAvgValueBuckets` 선례).
- 빈 입력(`tagSrlNos` empty) 단락 → `null` 반환. `getSingleResult()` MAX 결과 NULL(데이터 부재) → Service 가 `ZERO` fallback. **값만 반환**(발생 시각 미포함).
- `§2.5` 면책 주석 의무(`query-tuning.md §2`).

### ④ 전력피크예상시간 `predcPeakDtm` — 예측 합 GROUP BY+HAVING (Querydsl, opt 도메인)
단일 레벨 `GROUP BY predc_dtm HAVING SUM(predc_val) > target` 이므로 기존 `TagPredcRangeCustomRepositoryImpl`(Querydsl, 동일 `predc_1m_h`) 선례대로 **Querydsl 채택** — ANALYZE안건3 "native" 의 PLAN 정련(아래 §정련 근거). 신규 `PeakPredcCustomRepository`(섹션별 책임 분리 선례 — 7/9/10번 섹션 전용 리포지토리 분리).

```java
// PeakPredcCustomRepositoryImpl — §2.5 면책 (query-tuning.md §2)
QTagPrediction p = QTagPrediction.tagPrediction;
return queryFactory
        .select(p.predcDtm)
        .from(p)
        .where(p.tagSrlNo.in(tagSrlNos)
                .and(p.predcDtm.goe(now))
                .and(p.predcDtm.lt(horizonEnd)))   // 파티션 프루닝 상한 (1~2 파티션)
        .groupBy(p.predcDtm)
        .having(p.predcVal.sum().gt(targetPeakElpwr))
        .orderBy(p.predcDtm.asc())                  // (tag_srl_no, predc_dtm) 인덱스 → Sort 생략
        .limit(1)
        .fetchFirst();                              // 0행 → null
```

- 시그니처: `LocalDateTime findEarliestPredcDtmOverTarget(List<String> tagSrlNos, LocalDateTime now, LocalDateTime horizonEnd, BigDecimal targetPeakElpwr)`.
- "가장 가까운" = 가장 이른 미래(`ORDER BY predc_dtm ASC LIMIT 1`). 0행(초과 시각 없음) → `null` → 화면 "없음".
- `horizonEnd = now.plusHours(opt.peak.predc-horizon-hours)`(기본 48h) — `predc_dtm` 상한으로 predc 3년 보존 36파티션 풀스캔 방지(DBA 블로커 해소). 현재월~익월 1~2 파티션만 스캔.
- **`targetPeakElpwr <= 0`(미설정) 또는 `tagSrlNos` empty → 쿼리 생략하고 `null` 반환**(무의미 매칭 방지).
- 예측 태그 = PWI `tagSrlNos` 그대로 입력(논리 참조, 8/8 매칭 확인).
- `§2.5` 면책 주석 의무.

#### 정련 근거 (ANALYZE안건3 "native" → Querydsl)
ANALYZE안건3 은 native 로 설계했으나, 본 PLAN 에서 Querydsl 로 정련한다: (1) `predc_1m_h` 집계의 코드베이스 선례(`TagPredcRangeCustomRepositoryImpl`)가 Querydsl, (2) 단일 레벨 GROUP BY+HAVING+ORDER BY+LIMIT 은 Querydsl 이 타입 안전하게 표현, (3) 파티션 프루닝(`predc_dtm` 범위 바인딩)·인덱스 활용 동일, (4) raw SQL 표면 축소. 중첩집계인 ③(billing)만 native 유지. ⚠️ 이는 approved ANALYZE 의 표현 수단 변경이므로 PLAN 검토에서 사용자 확인 대상.

### 응답 DTO `PeakPowerAnalysisDto`
복수 소스 집계 합성 뷰(영속 마스터 1:1 부재) → `BaseAuditResponseDto` **미상속**(`FacilityOperatingStatusDto`·`PumpStateDto` 선례, `api-patterns.md §적용 범위` 통지/집계 적용 외). `record` 비채택 → `@Getter` + private 생성자 + 정적팩토리(프로젝트 일관성).

```java
@Getter
@Schema(description = "전력피크분석 2번섹션 4지표")
public class PeakPowerAnalysisDto {
    @Schema(description = "총순시전력 — 활성 PWI 태그 GOOD 최신값 합산 (kW)", example = "820.5000")
    private BigDecimal totalElpwr;

    @Schema(description = "목표피크전력 — 1번섹션 설정 목표 피크치 (kW, 0=미설정)", example = "900.0000")
    private BigDecimal targetPeakElpwr;

    @Schema(description = "요금적용전력피크 — 최근 12개월 분단위 PWI 합산 MAX (kW)", example = "875.0000")
    private BigDecimal billingPeakElpwr;

    @Schema(description = "전력피크예상시간 — 예측 PWI 합이 목표 초과하는 최근접 미래 시각 (null=없음)",
            example = "2026-06-05 14:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime predcPeakDtm;

    private PeakPowerAnalysisDto() {}
    public static PeakPowerAnalysisDto of(BigDecimal totalElpwr, BigDecimal targetPeakElpwr,
                                          BigDecimal billingPeakElpwr, LocalDateTime predcPeakDtm) { ... }
}
```

### Service `PeakPowerAnalysisService`
- `@Service @RequiredArgsConstructor @Transactional(readOnly = true)`. 주입: `TagRepository`, `RawDataRepository`, `PeakPredcRepository`, `PeakTargetService`. `@Value("${opt.peak.predc-horizon-hours:48}") long predcHorizonHours`.
- 공개 메서드 `getPeakPowerAnalysis()` — 헬퍼 4개 위임으로 50줄 이내:
  1. `tagSrlNos = pwiTagSrlNos()` (PWI 활성 태그)
  2. `total = sumLatestPwi(tagSrlNos)`
  3. `target = peakTargetService.getPeakTarget().getTargetPeakElpwr()`
  4. `billing = billingPeak(tagSrlNos)` (now-12mo..now, null→ZERO)
  5. `predcPeakDtm = expectedPeakDtm(tagSrlNos, target)` (target<=0 또는 빈 태그 → null)
  6. `PeakPowerAnalysisDto.of(total, target, billing, predcPeakDtm)`
- `effectiveVal(RawDataLatestDto)` private 헬퍼 **복제**(corrVal 우선·null 시 rawVal) — 메모리 "사이클 간 자산 자동 원용 금지" 정합. 공유 유틸 추출은 Service 3건 누적 시 별도 ANALYZE.
- `LocalDateTime.now()` 단일 호출로 4지표 시점 일관성 확보(billing endDtm·predc now 동일 기준).

### Controller `PeakPowerAnalysisController`
- `extends CommonController`, `@Tag(name = "14. 전력피크 분석")`, `@RequestMapping("/api/opt/peak-power-analysis")`.
- `@GetMapping` → `getResponseEntity(service.getPeakPowerAnalysis())`. 반환 `ResponseEntity<CommonResponseDto<PeakPowerAnalysisDto>>`. 인증 필요.
- `@Operation` + `@ApiResponses`(200/401/500 — 500 은 시드 미초기화 포함).

### 설정 `application-common.yml`
기존 `opt:` 블록에 추가:
```yaml
opt:
  prediction:
    match-window-minutes: 5
  peak:
    predc-horizon-hours: 48   # 전력피크예상시간 예측 파티션 프루닝 상한 (now+Nh). 1~2 파티션 한정.
```

### 엣지 케이스 fallback
| 케이스 | 처리 |
|--------|------|
| PWI 태그 0개 | 모든 쿼리 빈 입력 단락 → `total`/`billing` = ZERO, `predcPeakDtm` = null |
| 목표피크 0(미설정) | total/billing 정상, ④ 쿼리 생략 → null |
| 예측/12개월 데이터 부재 | MAX NULL → ZERO, HAVING 0행 → null |

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| 총순시전력 = GOOD PWI 합(BAD/UNCERTAIN/null 제외, corrVal 우선) | PeakPowerAnalysisServiceTest 단위 — GOOD 2건+BAD 1건+null corrVal·rawVal 1건 stub 시 GOOD 2건만 합산 검증 (Mockito) |
| 목표피크 미설정(target=0) 시 predcPeakDtm=null + 예측 쿼리 미호출 | 단위 — getPeakTarget stub target=0, PeakPredcRepository 호출 0회 verify + null 반환 |
| PWI 태그 0개 시 total=ZERO·billing=ZERO·predcPeakDtm=null | 단위 — findByTagSeCdAndUseYn 빈 리스트 stub 시 fallback + 하위 쿼리 미호출 verify |
| billing MAX null → ZERO 매핑 | 단위 — findMaxMinuteSumElpwr null 반환 stub 시 billingPeakElpwr=ZERO |
| 예측 합 목표 초과 최근접 시각 매핑 | 단위 — findEarliestPredcDtmOverTarget stub 결과가 predcPeakDtm 으로 매핑 |
| billing 12개월 MAX 파티션 프루닝 | 수동 EXPLAIN (local MCP) — acq_dtm BETWEEN 월 파티션 프루닝 + 결과값 대조 |
| 예측 horizonEnd 상한 프루닝 | 수동 EXPLAIN (local MCP) — predc_dtm < horizonEnd 1~2 파티션 한정 |
| 빌드·기동 | `./gradlew.bat :api:test` GREEN, `./gradlew.bat build` BUILD SUCCESSFUL, GET 응답 4필드 + Swagger 노출 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `opt.peak.predc-horizon-hours` 기본값 48h | 미해결 → 결정 | 48h 채택 — 현재 시드 predc 지평 짧음(now 직후). 운영 24~48h 가정, application-common.yml 외부화로 무중단 조정 가능 |
| ④ 예측 쿼리 표현 수단 — ANALYZE "native" → Querydsl 정련 | 결정 | 코드베이스 선례(TagPredcRangeCustomRepositoryImpl Querydsl) + 타입 안전 + 동일 프루닝. PLAN 검토 시 사용자 확인 대상 |
| 요금적용전력피크 = "존재하는 GOOD만 합산(부분합)" 내부 모니터링 근사값 (결측 분 과소추정 가능) | 가정 (사용자 확정) | domain-expert 참고(낮음) ① — UI 주석 권고, blocker 아님 |
| 요금적용전력피크 = 1분 MAX (한전 실무 15분 평균 수요전력과 상이) | 가정 (사용자 확정) | domain-expert 참고(낮음) ② — 내부 운전 참고 지표 |
| `quality_cd='GOOD'` 필터 누락 방지 | 결정 → 성공 기준 | "GOOD 필터 단위 테스트 GREEN" 명시 (③ native + ① service 양쪽) |
| 12개월 MAX 단일 native 1차 채택 | 가정 | DBA 권고(중간) — 1년 누적 후 EXPLAIN 200ms 초과 시 사전집계 별도 ANALYZE. 현재 시드 경량 |
| 예측 태그 = PWI 동일 tag_srl_no (8/8) | 가정 | live DB 확인. 신규 PWI 태그 추가 시 예측 생성 동반 가정 |
| `effectiveVal` private 헬퍼 복제 | 결정 | 메모리 "사이클 간 자산 자동 원용 금지" 정합 |

## 제외 사항
- 신규 엔티티·테이블·DDL·인덱스 (읽기 전용, 0건)
- SSE 실시간 전파 (단순 GET 폴링)
- 요금적용전력피크 발생 시각 응답
- 사전집계 테이블 도입 (성능 트리거 시 후속 사이클)
- 리포지토리 통합 테스트 클래스 — Testcontainers 미도입(`test-strategy.md §2`), 파티션 프루닝은 수동 EXPLAIN(MCP)으로 대체. Mockito 서비스 단위 테스트가 자동 검증 SSOT

## 예상 산출물
- [태스크](../../../tasks/20260605/전력피크분석-2번섹션/TASK1.md)
