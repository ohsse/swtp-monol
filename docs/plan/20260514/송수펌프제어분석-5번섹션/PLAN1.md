---
status: approved
created: 2026-05-14
updated: 2026-05-14
---
# 송수펌프제어분석 5번섹션 — 최소요구관압 박스 구현 계획

## 목적

송수펌프제어분석 화면 5번 박스 (최소요구관압) 가 4번 섹션에서 선택된 배수지(DWT) 의 다음 3개 필드를 표출할 수 있도록 backend 응답을 확장한다.

| 표시 라벨 | 데이터 출처 |
|---------|----------|
| 최소요구관압 기준배수지 | `DwtStateDto.facilityNm` (기존 — 4번섹션 자산) |
| 최소요구관압(분기점) | `DwtStateDto.minReqBranchPrsr` (**신규** — `dwt_m.min_req_branch_prsr`) |
| 최소요구관압 | `DwtStateDto.minReqPrsr` (**신규 응답 필드** — `dwt_m.min_req_prsr` 기존 컬럼) |

별도 엔드포인트 미도입. 4번섹션 응답 DTO 직접 확장 (ANALYZE1 안건 6 — 1안 채택 적정성 재확인).

## 배경

- 4번섹션 자산 (커밋 `6fa2e32`, 2026-05-14) 의 `GET /api/facility/{parentFacilityId}/dwts/states` 엔드포인트가 배수지별 운전 상태를 반환 중이며, 본 사이클은 그 응답에 배수지 설계값 2필드를 합류시킨다.
- `min_req_prsr` 컬럼은 `송수펌프제어분석 PLAN1` (2026-05-08) 에서 이미 도입 완료 (`V8_3__dwt_m_self_columns.sql` + `DistributionWaterTank.minReqPrsr` 필드). 본 사이클은 응답 노출만 추가.
- `min_req_branch_prsr` 는 ANALYZE1 안건 1·2 결론으로 신규 어휘 등록 완료 (`branch` 표준 단어 + `min_req_branch_prsr` 표준 용어).
- 5인 회의 결론: 도메인 4영역 중 인터록 영역 해당 — 사이클 2 (pump+AI 재설계) 인터록 임계값 편입 가능성 사전 대비 NOT NULL 정책 채택.

## 범위

### 포함
- `common` 모듈 — `DistributionWaterTank` 엔티티 자식 필드 `minReqBranchPrsr` 추가, 정적 팩토리 + 변경 메서드 확장
- `common/src/main/resources/db/init/V8_7__dwt_m_branch_prsr.sql` 신규 DDL (무중단 3단계 + COMMENT)
- `api` 모듈 — `DwtStateDto` 응답 필드 2개 (`minReqPrsr`·`minReqBranchPrsr`) + 정적 팩토리 시그니처 8→10 확장
- `api` 모듈 — `DwtStateService.assembleDwtState(...)` 에서 DWT 자식 캐스팅 후 매핑 추가
- 신규 단위 테스트 — `DistributionWaterTankTest` 의 신규 필드 NOT NULL 검증, `DwtStateServiceTest` 의 응답 매핑 확인

### 제외
- 인터록 평가 룰 데이터 신설 (`pump_interlock_p` 행 추가) — 사이클 2 범위
- 분기점 압력 변경 이력 `_h` 테이블 — 선례 `min_req_prsr` 동일 유보 (가정 섹션 명시)
- `min_req_branch_prsr` 운영자 보정 UI — 시설물관리 사이클 별도 범위
- frontend SPEC 슬러그 결정 (4번섹션 SPEC2 누적 vs 5번섹션 별도 SPEC1) — `/dev:spec` 단계 사용자 결정

## 구현 방향

### Service 흐름 (`DwtStateService.assembleDwtState(...)`)

현재 메서드 시그니처는 `Facility dwt` 를 받아 부모 타입으로 처리한다. 자식 전용 컬럼 `minReqPrsr`·`minReqBranchPrsr` 접근을 위해 `DistributionWaterTank` 로 캐스팅한다.

```java
DistributionWaterTank dwtChild = (DistributionWaterTank) dwt;
// ... 기존 매핑 ...
return DwtStateDto.of(
        dwt.getFacilityId(), dwt.getFacilityNm(),
        dwtChild.getMinReqPrsr(), dwtChild.getMinReqBranchPrsr(),
        inDto, outDto, multipleIn, multipleOut, valves, lvmtrs);
```

캐스팅 안전성: `findByParentFacilityIdAndFacilityTypeAndUseYnOrderByDispOrdAsc(..., FacilityType.DWT, ...)` 호출이 `FacilityType.DWT` 필터로 강제하므로 반환된 모든 `Facility` 인스턴스가 `DistributionWaterTank` (JPA JOINED 자식). 캐스팅 실패 시 ClassCastException — 도메인 룰 위반이므로 fail-fast 의도.

추가 쿼리 0건: JPA JOINED 자식 상속으로 부모 SELECT 가 자식 테이블 LEFT JOIN 으로 자동 확장됨. `min_req_prsr`·`min_req_branch_prsr` 컬럼이 동일 SELECT 에 함께 적재.

### DTO 시그니처 변경 (`DwtStateDto.of(...)`)

`of(...)` 정적 팩토리 파라미터를 8→10 으로 확장한다. 추가 위치는 `facilityNm` 다음 (기준 배수지 식별 → 설계값 → 운전 상태 순서 — 응답 가독성 정렬).

```java
public static DwtStateDto of(
        String facilityId,
        String facilityNm,
        BigDecimal minReqPrsr,            // 신규
        BigDecimal minReqBranchPrsr,      // 신규
        InletFlwmtrStateDto inFlwmtr,
        OutletFlwmtrStateDto outFlwmtr,
        boolean multipleInFlwmtrDetected,
        boolean multipleOutFlwmtrDetected,
        List<ValveStateDto> valves,
        List<LvmtrStateDto> lvmtrs) { ... }
```

기존 4번섹션 `DwtStateServiceTest` 의 `of(...)` 호출 시그니처도 함께 갱신 (회귀 회피).

### DDL 무중단 3단계 (`V8_7__dwt_m_branch_prsr.sql`)

`V8_3` 선례 동일 패턴.

```sql
-- 1단계: NULL 허용 컬럼 추가 (즉시 완료, 락 없음)
ALTER TABLE dwt_m ADD COLUMN min_req_branch_prsr NUMERIC(15, 4);

-- 2단계: 기존 행 백필 (운영자 보정 전 임시값 0.0000)
UPDATE dwt_m SET min_req_branch_prsr = 0.0000 WHERE min_req_branch_prsr IS NULL;

-- 3단계: NOT NULL 제약 추가
ALTER TABLE dwt_m ALTER COLUMN min_req_branch_prsr SET NOT NULL;

-- COMMENT
COMMENT ON COLUMN dwt_m.min_req_branch_prsr IS '분기점 최소 요구 압력 (kgf/cm², DOM_QTY_15_4 NOT NULL — 인터록 평가 기준값. 사이클 2 인터록 임계값 편입 가능성 대비, min_req_prsr 선례 동일 NOT NULL 정책)';
```

### 엔티티 변경 (`DistributionWaterTank.java`)

- 신규 필드 `minReqBranchPrsr` (`BigDecimal`, precision=15·scale=4, NOT NULL)
- `create(...)` 정적 팩토리 시그니처 +1 파라미터 + `Objects.requireNonNull(minReqBranchPrsr)` 검증
- `changeMinReqPrsr(...)` 대칭으로 신규 변경 메서드 `changeMinReqBranchPrsr(BigDecimal)` 추가
- 클래스 Javadoc 갱신 (자식 전용 컬럼 1건 → 2건)

## 도메인 모델

| 엔티티/테이블 | 역할 | 주요 필드 |
|------------|------|---------|
| `DistributionWaterTank` (`dwt_m`) | 배수지 자식 엔티티 — JPA JOINED (`@DiscriminatorValue("DWT")`) | `minReqPrsr` (기존) + `minReqBranchPrsr` (신규, BigDecimal, NOT NULL) |
| `DwtStateDto` | 송수펌프제어분석 4·5번섹션 통합 응답 DTO | `facilityId`·`facilityNm`·`minReqPrsr` (신규 응답 노출)·`minReqBranchPrsr` (신규)·기존 8개 |

신규 엔티티 클래스·신규 DTO 클래스·신규 Service 클래스 모두 생성 없음 — 기존 자산 확장만.

## DB 설계 변경

| 변경 대상 | 변경 유형 | 무중단 마이그레이션 |
|---------|---------|------------------|
| `dwt_m.min_req_branch_prsr` 컬럼 추가 | 신규 컬럼 + NOT NULL 제약 | 3단계 (NULL 허용 추가 → `0.0000` 백필 → NOT NULL 전환). `V8_3` 선례 동일 패턴 |

추가 인덱스 미적용 — 단순 조회 SELECT, 조회 조건 범위 검색 부재 (ANALYZE1 안건 3 결론).

DDL 적용 후 운영자 절차:
1. 운영 환경에서 `V8_7` 마이그레이션 실행
2. 운영자가 각 배수지 행의 `min_req_branch_prsr` 실제 분기점 압력값으로 보정 (임시값 `0.0000` 으로는 인터록 평가 시 정상 동작 불가)
3. `ANALYZE dwt_m` 으로 통계 재수집 (선택 — 데이터 분포 변화 미미하지만 일관성)

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령 / 테스트 / 조회 |
|------|----------------------|
| `DistributionWaterTank` 엔티티가 `minReqBranchPrsr` NOT NULL 필드 보유 + `create()`·`changeMinReqBranchPrsr()` 에서 null 입력 시 NPE | `./gradlew :common:test` PASS + `DistributionWaterTankTest` 신규 케이스 GREEN |
| `V8_7__dwt_m_branch_prsr.sql` 적용 후 `dwt_m.min_req_branch_prsr` NUMERIC(15,4) NOT NULL + COMMENT 존재 | psql `\d+ dwt_m` 출력 매칭 — `min_req_branch_prsr` 행에 `numeric(15,4)`·`not null` + COMMENT 라벨 한국어 노출 |
| `GET /api/facility/{parentFacilityId}/dwts/states` 응답 JSON 에 `minReqPrsr`·`minReqBranchPrsr` 2필드 포함 | `DwtStateServiceTest` 신규 검증 — `assertThat(dto.getMinReqPrsr())` · `assertThat(dto.getMinReqBranchPrsr())` 매칭 GREEN |
| `DwtStateDto.of(...)` 시그니처 변경에 기존 4번섹션 매핑 회귀 없음 | `./gradlew :api:test` PASS — 기존 `DwtStateServiceTest` 의 모든 케이스 GREEN |
| 추가 SQL 쿼리 0건 (JPA JOINED 자식 자동 적재) | `DwtStateServiceTest` 의 p6spy 로그 확인 — 기존 5건 SQL 유지 (Facility 부모 1 + 자식 1 + Instrument 1 + Tag 1 + RawData 1) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `min_req_branch_prsr` 는 본 사이클 표출 전용, 사이클 2 인터록 임계값 편입 가능성 — NOT NULL 정책 사전 채택은 본 가능성 대비 (ANALYZE1 안건 5 + 도메인 4영역 점검 표 "인터록" 행 해당) | 결정 | 본 사이클 인터록 평가 코드 변경 없음, 사이클 2 별도 ANALYZE 에서 룰 데이터 신설 |
| `min_req_branch_prsr` 변경 이력 `_h` 테이블 — 선례 `min_req_prsr` 동일 유보 적용 | 결정 | 본 사이클 범위 외. 분기별 이력 요구사항 발생 시 별도 작업 |
| `dwt_m` 운영 데이터 백필 — 임시값 `0.0000` 적용 + 운영자 보정 안내 의무 | 결정 | DDL 적용 직후 운영자가 실제 분기점 압력값 갱신. 미보정 시 사이클 2 인터록 평가에서 부정확 — 운영 매뉴얼 별도 안내 |
| frontend SPEC 슬러그 — 4번섹션 SPEC2 누적 vs 5번섹션 별도 SPEC1 | 미해결 → 결정 보류 | `/dev:spec` 단계 사용자 결정 — `DwtStateDto` 가 4번섹션 슬러그 자산이므로 SPEC2 누적이 자연스러우나 화면 분리 명세 가독성 위해 SPEC1 별도도 가능 |
| 본 사이클은 `instanceof` 패턴 매칭 vs C-style 캐스팅 선택 — Java 21 sealed types 미사용 환경에서 캐스팅이 더 명료 | 결정 | C-style 캐스팅 `(DistributionWaterTank) dwt` 사용 — `findByParentFacilityIdAndFacilityTypeAndUseYnOrderByDispOrdAsc` 의 `FacilityType.DWT` 필터로 자식 타입 보장됨, 캐스팅 실패는 도메인 룰 위반 fail-fast 의도 |

## 제외 사항

- `pump_interlock_p` 룰 데이터 신설 — 사이클 2 (pump+AI 재설계) 별도 ANALYZE
- 분기점 압력 변경 이력 `_h` 테이블 — 본 사이클 범위 외
- 시설물관리 화면의 분기점 압력 입력 UI — 별도 사이클 (시설물관리기능 자산 확장)
- 4번섹션 응답 DTO 의 BaseAuditResponseDto 옵트인 — `DwtStateDto` Javadoc 명시 "실시간 통지성 응답" 분류 유지 (ANALYZE1 안건 4 결론)

## 예상 산출물

- [태스크](../../../tasks/20260514/송수펌프제어분석-5번섹션/TASK1.md) (TASK 단계 작성 예정)

## 부록: 도메인/DB 검토 결과

본 PLAN 의 도메인 모델·DB 설계 변경은 ANALYZE1 5인 회의 결론을 그대로 반영하며, 추가적 도메인/DB 결정 사항 없음.

- ANALYZE1 안건 3 (DB) — wtp-dba-reviewer 결론: NOT NULL + 무중단 3단계 + 단독 인덱스 미적용 + COMMENT 의무
- ANALYZE1 안건 5 (도메인) — wtp-domain-expert 결론: 본 사이클 4영역 비해당 (인터록 영역은 사이클 2 임계값 편입 가능성 사전 대비로 4영역 점검 표 "해당" 격상)

PLAN 단계 추가 검토 게이트 호출 불필요 — 5인 회의 결론을 직접 인용한 단순 컬럼 추가 + DTO 확장.
