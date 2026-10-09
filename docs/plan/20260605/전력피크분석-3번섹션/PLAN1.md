---
status: approved
created: 2026-06-05
updated: 2026-06-05
---
# 전력피크분석-3번섹션 — 주요내역 송수펌프 순시전력 추가 구현 계획

## 목적

전력피크 분석 화면(`backend/image/전력피크분석.png`) 가운데 **3번 섹션**(`주요내역`)의 3지표를 백엔드로 표출한다. 3지표 중 2지표(총순시전력·요금적용전력피크)는 2번섹션과 동일하며, **신규 지표는 송수펌프 순시전력 1개**다. 사용자 결정에 따라 **2번섹션 엔드포인트(`GET /api/opt/peak-power-analysis`)를 확장** — 기존 `PeakPowerAnalysisDto`에 `pumpElpwr` 필드 1개를 추가(4→5필드)하고 `PeakPowerAnalysisService`를 확장한다. 신규 테이블·DDL·컬럼·인덱스는 **0건**이다.

| 3번섹션 지표 | 응답 필드 | 산출 | 2번섹션 대비 |
|------|----------|------|------------|
| 총순시전력 | `totalElpwr` | 전체 활성 PWI 태그 GOOD 최신값 합산 | 동일 (무변경) |
| **송수펌프 순시전력** | **`pumpElpwr`** | 펌프(`equip_type_cd='PUMP'`) 매핑 PWI GOOD 최신값 합산 (On/Off 무관) | **신규** |
| 요금적용전력피크 | `billingPeakElpwr` | 최근 12개월 분단위 PWI 합산값 중 MAX | 동일 (무변경) |

> 응답 DTO는 5필드 전부(`totalElpwr`·`pumpElpwr`·`targetPeakElpwr`·`billingPeakElpwr`·`predcPeakDtm`)를 반환한다. frontend는 2번섹션 패널에 4필드, 3번섹션 주요내역 패널에 3필드를 단일 응답에서 표출한다.

## 배경

- ANALYZE1(`docs/analyze/20260605/전력피크분석-3번섹션/ANALYZE1.md`, status: approved) 5인 회의 결론을 구현으로 전개한다.
- 사용자 확정(2026-06-05): ① 송수펌프 순시전력 = 전체 펌프 PWI 합산(On/Off 무관) ② 2번섹션 엔드포인트 확장 ③ 2번 Service 재사용(total·billing·predc 무변경).
- ROOT 표준단어 `pump` 등록 완료(`standard-words.md`, 2026-06-05). `pumpElpwr` = `pump`(신규) + `elpwr`(기존).
- live DB(2026-06-05): 활성 PWI 8개 전부 PUMP 매핑(현 시드 `pumpElpwr = totalElpwr`). 실 운영은 비펌프 PWI 존재 → `equip_type_cd='PUMP'` 필터로 구분.

## 범위

### 포함
- `PeakPowerAnalysisDto` — `pumpElpwr` 필드 1개 추가(`totalElpwr` 직후), 정적팩토리 `of(...)` 5인자 확장, class Javadoc·`@Schema(description)` 최소 갱신
- `PeakPowerAnalysisService` — 펌프 PWI srlNo set 조회 헬퍼 추가, `findLatestByTagSrlNos` **1회 호출 결과 공유** 리팩터, 송수펌프 부분합 산출, `of(...)` 호출 5인자 확장
- `TagRepository` — 파생쿼리 `findByTagSeCdAndUseYnAndInstrument_EquipType(...)` 1개 추가
- `PeakPowerAnalysisController` — `@Operation`·class Javadoc 의 "2번섹션 4지표" 표현 최소 갱신(§3 정밀한 수정)
- `PeakPowerAnalysisServiceTest` — 신규 케이스 2건 추가(펌프 부분집합 합산·펌프 0개 fallback)

### 제외
- 신규 엔티티·테이블·DDL·인덱스 (0건 — 읽기 전용)
- 총순시전력·요금적용전력피크·전력피크예상시간 산출 로직 변경 (무변경 재사용)
- 신규 엔드포인트·신규 Controller·신규 Service (2번섹션 확장)
- SSE 실시간 전파 (단순 GET 폴링 — 2번섹션과 동일)
- 운전현황 4번섹션 On펌프 합산 로직 변경 (별개 지표, 무접촉)

## 구현 방향

### 패키지·클래스 배치 (`com.mo.swtp.opt`, api 모듈)

```
api/src/main/java/com/mo/swtp/opt/dto/PeakPowerAnalysisDto.java               (필드 1 추가 + of 5인자)
api/src/main/java/com/mo/swtp/opt/service/PeakPowerAnalysisService.java        (펌프합산 + latest 1회 공유 리팩터)
api/src/main/java/com/mo/swtp/tag/repository/TagRepository.java                (파생쿼리 1 추가)
api/src/main/java/com/mo/swtp/opt/web/PeakPowerAnalysisController.java         (Javadoc·@Operation 텍스트 최소 갱신)
api/src/test/java/com/mo/swtp/opt/service/PeakPowerAnalysisServiceTest.java    (케이스 2 추가)
```

### ① `TagRepository` — 펌프 매핑 PWI 식별 파생쿼리 (ANALYZE 안건 2 옵션 A)

```java
/**
 * 측정 유형 + 사용 여부 + 계측기 종류로 활성 태그를 시스템 전역에서 조회한다.
 *
 * <p>전력피크분석-3번섹션 (2026-06-05) — 펌프(equip_type_cd='PUMP') 매핑 PWI 태그를 한 번의 파생쿼리
 * (tag_m ↔ instrument_m INNER JOIN)로 수집하여 송수펌프 순시전력 합산 입력을 구성한다. lazy 접근 N+1
 * (query-tuning.md §2) 회피 — instrument_m JPA JOINED 상속이라 부모 테이블 JOIN 1회로 equip_type_cd 필터 처리.</p>
 */
List<Tag> findByTagSeCdAndUseYnAndInstrument_EquipType(
        TagMeasurementType tagSeCd, YnType useYn, EquipType equipType);
```

- 반환 `List<Tag>` → service 가 `Tag::getTagSrlNo` 로 펌프 PWI `Set<String>` 구성.
- 기존 `findByTagSeCdAndUseYn(PWI, Y)`(전체 PWI)는 **유지** — 총순시전력·billing·predc 입력 그대로.
- 신규 인덱스 불요 (`tag_m` 소규모 Seq Scan, DBA 안건 2).

### ② `PeakPowerAnalysisService` — latest 1회 호출 공유 리팩터 (ANALYZE 안건 3 권고②)

`findLatestByTagSrlNos`를 **1회만 호출**하여 그 결과 `List<RawDataLatestDto>`를 전체합(`totalElpwr`)·펌프합(`pumpElpwr`)이 공유한다(시점 일관성 — 2회 호출 시 total↔pump 불일치 방지). 기존 `sumLatestPwi(List<String>)`(내부에서 fetch)를 `sumGoodPwi(List<RawDataLatestDto>)`(fetch는 caller)로 시그니처 변경한다(내부 private, 외부 영향 0).

```java
public PeakPowerAnalysisDto getPeakPowerAnalysis() {
    LocalDateTime now = LocalDateTime.now();
    List<String> pwiTagSrlNos = pwiTagSrlNos();              // findByTagSeCdAndUseYn(PWI,Y) — 전체 PWI
    Set<String> pumpPwiSrlNos = pumpPwiTagSrlNos();          // findBy...Instrument_EquipType(PWI,Y,PUMP) — 펌프 PWI
    BigDecimal targetPeakElpwr = peakTargetService.getPeakTarget().getTargetPeakElpwr();

    List<RawDataLatestDto> latest = rawDataRepository.findLatestByTagSrlNos(pwiTagSrlNos);   // 1회 호출
    BigDecimal totalElpwr = sumGoodPwi(latest);                                              // 전체 GOOD 합
    BigDecimal pumpElpwr = sumGoodPwi(pumpLatest(latest, pumpPwiSrlNos));                    // 펌프 부분집합 GOOD 합

    BigDecimal billingPeakElpwr = billingPeak(pwiTagSrlNos, now);                            // 무변경
    LocalDateTime predcPeakDtm = expectedPeakDtm(pwiTagSrlNos, now, targetPeakElpwr);        // 무변경

    return PeakPowerAnalysisDto.of(totalElpwr, pumpElpwr, targetPeakElpwr, billingPeakElpwr, predcPeakDtm);
}

/** 펌프(equip_type_cd='PUMP') 매핑 활성 PWI 태그 시리얼번호 집합. */
private Set<String> pumpPwiTagSrlNos() {
    return tagRepository
            .findByTagSeCdAndUseYnAndInstrument_EquipType(TagMeasurementType.PWI, YnType.Y, EquipType.PUMP)
            .stream().map(Tag::getTagSrlNo).collect(Collectors.toSet());
}

/** 펌프 srlNo set 멤버십으로 최신값 리스트 부분집합 추출 (동일 latest 공유 — 추가 DB 조회 0회). */
private List<RawDataLatestDto> pumpLatest(List<RawDataLatestDto> latest, Set<String> pumpPwiSrlNos) {
    return latest.stream().filter(r -> pumpPwiSrlNos.contains(r.tagSrlNo())).toList();
}

/** GOOD 만 합산, null/BAD/UNCERTAIN 전액 제외 (부분합). corrVal 우선·null 시 rawVal. 빈 입력 시 ZERO. */
private BigDecimal sumGoodPwi(List<RawDataLatestDto> values) {
    return values.stream()
            .filter(r -> r.qualityCd() == QualityCode.GOOD)
            .map(this::effectiveVal)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

- 기존 `pwiTagSrlNos()`·`billingPeak(...)`·`expectedPeakDtm(...)`·`effectiveVal(...)` **무변경**.
- 단일 `sumGoodPwi(List<RawDataLatestDto>)` 헬퍼를 전체합·펌프합 양쪽이 재사용(DRY, `coding-discipline.md §2`). 펌프합은 `pumpLatest`로 사전 필터한 리스트를 동일 헬퍼에 전달.
- `getPeakPowerAnalysis()` 본문 ~13줄 — 50줄 임계(§2.1) 위반 없음.
- 신규 import: `java.util.Set`, `java.util.stream.Collectors`, `com.mo.swtp.instrument.domain.enumtype.EquipType`.
- class Javadoc `<ol>`에 송수펌프 항목 1건 추가 + "2번섹션 4지표" → "2·3번섹션 공용 5지표" 최소 갱신(§3).

### ③ `PeakPowerAnalysisDto` — `pumpElpwr` 필드 추가 (5필드)

`totalElpwr` 직후 `pumpElpwr` 배치(논리 인접 — 둘 다 순시전력 합산). 기존 4필드의 `@Schema`·`@JsonFormat` **수정 금지**(§3). class `@Schema(description)`·Javadoc만 갱신.

```java
@Schema(description = "총순시전력 — 전체 PWI 태그 최신값 GOOD 합산 (kW)", example = "812.5000")
private BigDecimal totalElpwr;

@Schema(description = "송수펌프 순시전력 — 펌프(equip_type_cd='PUMP') 매핑 PWI GOOD 최신값 합산 (kW, On/Off 무관). "
        + "운전현황 On펌프 합산과 혼동 주의 — 가동 여부 무관 전체 펌프 합산", example = "558.0000")
private BigDecimal pumpElpwr;
// ... targetPeakElpwr / billingPeakElpwr / predcPeakDtm 무변경 ...

public static PeakPowerAnalysisDto of(
        BigDecimal totalElpwr, BigDecimal pumpElpwr, BigDecimal targetPeakElpwr,
        BigDecimal billingPeakElpwr, LocalDateTime predcPeakDtm) { ... }
```

- `pumpElpwr` 는 `BigDecimal` → `@Schema(implementation)` 생략 적법(`api-patterns.md §적용 제외`). `description`(On/Off 무관 + 혼동 주의 주석 — domain-expert 참고③)·`example` 작성.

### ④ `PeakPowerAnalysisController` — 텍스트 최소 갱신 (§3)

- class Javadoc·`@Operation(summary/description)`의 "2번섹션 4지표"/"4지표" → "2·3번섹션 5지표" 및 송수펌프 순시전력 1줄 추가. 엔드포인트 경로·시그니처·`@ApiResponses` **무변경**.

### 엣지 케이스 fallback

| 케이스 | 처리 |
|--------|------|
| 펌프 PWI 0개 | `pumpPwiSrlNos` 빈 set → `pumpLatest` 빈 리스트 → `pumpElpwr` = ZERO |
| 비펌프 PWI 만 존재 | `totalElpwr` > 0, `pumpElpwr` = ZERO (펌프 set 멤버십 0건) |
| 전체 PWI 0개 | 기존과 동일 — total/billing ZERO, predc null. pump set 도 빈 set → pumpElpwr ZERO |
| 펌프 PWI BAD/UNCERTAIN/null | `sumGoodPwi` GOOD 필터 제외 — pumpElpwr 부분합 |

## 도메인 모델

신규 엔티티·테이블·DB 컬럼 **없음**. 응답 DTO 필드 1개(`pumpElpwr`, `DOM_QTY_15_4` 형식, DB 컬럼 아님) 추가만. 따라서 `wtp-domain-expert` PLAN 검토 게이트 비대상(ANALYZE 5인 회의에서 도메인 4영역 비해당·지표 정의 검증 완료).

## DB 설계 변경

**없음** — 읽기 전용. 신규 DDL·인덱스·파티션 0건. 기존 `idx_rawdata_1m_h_tag_time` 적중, `tag_m` Seq Scan 허용. `wtp-dba-reviewer` PLAN 검토 게이트 비대상(ANALYZE 안건 2에서 쿼리·인덱스·프루닝 검증 완료).

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| 송수펌프순시전력 = 펌프 PWI 부분집합 GOOD 합 (비펌프 PWI 제외) | `PeakPowerAnalysisServiceTest` 신규 — 펌프 PWI 2건(GOOD 100·50) + 비펌프 PWI 1건(GOOD 200) stub 시 `pumpElpwr`=150, `totalElpwr`=350 검증 (Mockito) |
| 펌프 PWI BAD/UNCERTAIN 제외 | 위 케이스 확장 또는 별도 — 펌프 PWI BAD 1건 추가 시 `pumpElpwr` 합산 제외 검증 |
| 펌프 PWI 0개 시 `pumpElpwr`=ZERO, `totalElpwr` 정상 | 신규 — `findByTagSeCdAndUseYnAndInstrument_EquipType` 빈 리스트 stub 시 `pumpElpwr`=ZERO, `totalElpwr`>0 |
| `findLatestByTagSrlNos` 1회 호출 (total·pump 공유) | 신규 — `verify(rawDataRepository, times(1)).findLatestByTagSrlNos(anyList())` |
| 기존 4지표 회귀 무변경 | 기존 `PeakPowerAnalysisServiceTest` 5케이스 GREEN 유지 (stub 미변경) |
| 빌드·기동 | `./gradlew.bat :api:test` GREEN, `./gradlew.bat build` BUILD SUCCESSFUL |
| GET 응답 5필드 + Swagger 노출 | dev MCP 실데이터 SQL 대조 — `pumpElpwr` = 펌프 PWI GOOD 합 == 현 시드 `totalElpwr`(8/8 펌프 매핑) 일치 확인. @Tag 14 컨텍스트 로드 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 송수펌프순시전력 = 전체 펌프 PWI 합산(On/Off 무관) | 결정 (사용자 2026-06-05) | 운전현황 4번섹션 On펌프 합산과 목적 다른 별개 지표 — domain-expert 정합 판정 |
| 2번섹션 엔드포인트 확장 (DTO 5필드) | 결정 (사용자 2026-06-05) | `pumpElpwr` 필드 추가, 신규 엔드포인트 미생성 |
| 펌프 srlNo 식별 = 파생쿼리 1개(옵션 A) | 결정 | DBA·backend 합치 — lazy N+1 회피 |
| `findLatestByTagSrlNos` 1회 호출 공유 (`sumLatestPwi`→`sumGoodPwi` 시그니처 변경) | 결정 | backend 권고② — total↔pump 시점 일관성. 내부 private 변경 |
| Off 상태 펌프 PWI GOOD 값이 0/극소(대기 전력) 가정 — 양수 잔존 시 과대계상 | 가정 (사용자 확정) | domain-expert 참고① — `@Schema` description 에 "On/Off 무관, 대기 전력 포함 가능" 명기로 완화 |
| 태그 미등록 펌프는 합산 자동 제외 (부분합 반환을 의도된 동작) | 가정 | domain-expert 참고② — 오류 없이 부분합 반환 |
| 현 시드 PWI 8개 전부 펌프 매핑 → `pumpElpwr = totalElpwr` | 가정 | live DB 확인. 검증 단계 SQL 대조로 일치 확인 |

분류값: 가정 / 미해결 → 결정

## 제외 사항

- 신규 엔티티·테이블·DDL·인덱스 (읽기 전용, 0건)
- 총순시전력·요금적용전력피크·전력피크예상시간 산출 로직 변경
- SSE 실시간 전파 (단순 GET 폴링)
- 운전현황 4번섹션 `FacilityOperatingStatusService` On펌프 합산 로직 (별개 지표, 무접촉)
- 리포지토리 통합 테스트 — Testcontainers 미도입(`test-strategy.md §2`). 펌프 식별 쿼리 검증은 Mockito 서비스 단위 테스트 + dev MCP 실데이터 SQL 대조

## 예상 산출물
- [태스크](../../../tasks/20260605/전력피크분석-3번섹션/TASK1.md)
