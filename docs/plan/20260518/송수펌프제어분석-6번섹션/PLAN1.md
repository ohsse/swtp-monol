---
status: approved
created: 2026-05-18
updated: 2026-05-18
---
# 송수펌프제어분석 6번섹션 (시설별 토출관압 + 운전중 펌프 대수 요약)

## 목적

송수펌프제어분석 화면 6번 섹션을 backend 에서 표출한다. **1번섹션 시설목록(hasPump=true 활성 펌프 시설 전체)** 의 시설별 두 데이터 — ① 토출관압(FLWMTR 의 PRI 최신값), ② 운전중 펌프 대수(PUMP 의 OPS 가동상태 ON 대수) — 를 목록 집계형 신규 전용 엔드포인트로 반환한다. 1번섹션에서 어떤 시설을 활성화했는지와 **무관하게** 시설목록 전체의 최신값을 항상 유지한다.

## 배경

- ANALYZE: [ANALYZE1](../../../analyze/20260518/송수펌프제어분석-6번섹션/ANALYZE1.md) (status: approved) — 5인 회의 결론 반영.
- 단일 선택 시설에 종속된 3·4번섹션(`/{facilityId}/state`, `/{parentFacilityId}/dwts/states`)과 달리 path variable 없는 컬렉션 엔드포인트가 필요하다 (선택 시설 무관).
- 송수펌프제어분석 섹션별 사이클 연속 작업 (3→4→5→6번섹션). 이전 섹션 자산 자동 재사용 금지 — 공유 인프라(Repository 4종) 만 재사용하고 Service·DTO·엔드포인트는 신규 신설.
- 사용자 승인 사항: `unknownPumpCnt`(신뢰불가 펌프 대수) 응답 필드 추가 — `ot-integration.md §3` OPS 즉시 BAD 격상 정책 정합을 위한 도메인 안전 보강 (운전원 오인 방지). 사용자 승인 완료 (2026-05-18).

## 범위

### 포함

- 신규 `com.mo.swtp.facility.dto.PumpSummaryDto` (시설 1건 = row DTO)
- 신규 `com.mo.swtp.facility.service.PumpSummaryService`
- `com.mo.swtp.facility.web.FacilityController` 에 `GET /api/facility/pump-summary` 엔드포인트 메서드 1개 추가
- 신규 `PumpSummaryServiceTest` (Mockito 단위 테스트)

### 제외 (아래 §제외 사항 상세)

- DB 스키마 변경·신규 마이그레이션 SQL (0건 — `wtp-dba-reviewer` 안건 4 결론)
- 신규 Repository 메서드 (전부 기존 재사용)
- 컨테이너 래퍼 DTO (`PumpSummaryListDto`) — 미도입
- 운전 모드·인터록·알람·이력 (도메인 4영역 모두 비해당)

## 도메인 모델

신규 엔티티·DB 컬럼·테이블 **0건**. 기존 도메인 모델 재사용만 (`wtp-domain-expert` 안건 5 결론 — 도메인 4영역 모두 비해당).

| 재사용 자산 | 역할 |
|-----------|------|
| `Facility`·`Instrument`·`Pump`·`Tag`·`RawData` (엔티티) | 기존 도메인 모델 그대로 사용 |
| `FacilityRepository.findFacilities(FacilitySearchDto)` | 1번섹션 자산 — hasPump=true EXISTS 필터 (`equip_type_cd=PUMP AND use_yn=Y`), 정렬 useYn DESC → dispOrd ASC → facilityNm ASC. `List<Facility>` 엔티티 반환 |
| `InstrumentCustomRepository.findByFacilityIdInAndEquipType(facilityIds, equipTypes)` | 시설 IN → 계측기 IN 절 단일 조회 |
| `TagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y)` | 계측기 IN → 활성 태그 IN 절 단일 조회 |
| `RawDataCustomRepository.findLatestByTagSrlNos(tagSrlNos)` | rawdata_1m_h DISTINCT ON + `acq_dtm >= NOW() - INTERVAL '1 hour'` 파티션 프루닝. `Map<tagSrlNo, RawDataLatestDto>` 집계 |
| `QualityCode` enum (`com.mo.swtp.raw.domain.enumtype`) | `prsrQualityCd` 필드 타입 + OPS 품질 분기 |
| `TagMeasurementType` enum (PRI/OPS) | 태그 측정 유형 필터 |
| `EquipType` enum (PUMP/FLWMTR) | 계측기 종류 필터 |

## DB 설계 변경

**없음** — 신규 컬럼·테이블·인덱스·마이그레이션 SQL 0건. 기존 인덱스 4종으로 조회 패턴 완전 커버:

- `idx_facility_m_parent_type_yn`·`idx_instrument_m_facility_equip` (V9_2, 기 도입)
- `idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)`·`idx_rawdata_1m_h_brin` (V6_5, 기 도입)

최신 마이그레이션은 `V9_2`. 본 사이클 신규 마이그레이션 SQL 부재 (`wtp-dba-reviewer` 안건 4 결론).

## 구현 방향

### 엔드포인트

```
GET /api/facility/pump-summary
응답: ResponseEntity<CommonResponseDto<List<PumpSummaryDto>>>
```

- path variable 없음 — hasPump=true 시설 전체 목록 집계 (선택 시설 무관)
- Swagger: `@ArraySchema(schema = @Schema(implementation = PumpSummaryDto.class))` 명시 의무 (`wtp-backend-engineer` 안건 1)
- `@Tag(name = "06. 시설물 관리")` 기존 컨트롤러에 메서드 추가, `@ApiResponses` 200/400/401/403/500 (404 없음 — 목록 집계라 단건 미존재 예외 경로 부재)

### 응답 DTO — `PumpSummaryDto` (시설 1건 = row, 평탄 구조)

미해결 질문 "prsr 값 필드 구조 (평탄 vs 중첩)" → **평탄 4필드 채택** (§3 `FlwmtrStateDto` 선례 정합 + `wtp-backend-engineer` 권고 — 중첩 `PrsrValueDto` 는 1회성 추상화로 `coding-discipline.md §2` 위반).

| 필드 | 타입 | 설명 |
|------|------|------|
| `facilityId` | String | 시설 ID |
| `facilityNm` | String | 시설명 |
| `prsrRawVal` | BigDecimal | 토출관압 원본값 (FLWMTR 첫 매치의 PRI, kgf/cm²) |
| `prsrCorrVal` | BigDecimal | 토출관압 보정값 (NULL 허용) |
| `prsrAcqDtm` | LocalDateTime | 토출관압 수집 일시 — `@JsonFormat(shape=STRING, pattern="yyyy-MM-dd HH:mm:ss")` 초 단위 SSOT |
| `prsrQualityCd` | QualityCode | 토출관압 SCADA 품질 코드 — `@Schema(implementation=QualityCode.class)` |
| `multiplePrsrDetected` | boolean | 시설 FLWMTR/PRI 다중 등록 시 첫 매치 + true + WARN 로그 |
| `oprtngPumpCnt` | Integer | 운전중 펌프 대수 (OPS rawVal=1.0 AND qualityCd=GOOD) |
| `unknownPumpCnt` | Integer | 신뢰불가 펌프 대수 (OPS qualityCd=BAD/UNCERTAIN 또는 1시간 윈도우 결측) |

- `@Getter` + private 기본 생성자 + 정적 팩토리 `of(...)` (§3·§4 DTO 패턴 정합)
- `BaseAuditResponseDto` 미상속 (실시간 통지성 — `api-patterns.md §적용 범위`)

### Service — `PumpSummaryService` (4-step IN 절 단일 쿼리 + 메모리 집계)

```
@Service @RequiredArgsConstructor @Transactional(readOnly = true) @Slf4j
class PumpSummaryService {
    static final List<EquipType> SUMMARY_EQUIP_TYPES = List.of(PUMP, FLWMTR);
    static final Set<TagMeasurementType> SUMMARY_TAG_TYPES = EnumSet.of(PRI, OPS);

    List<PumpSummaryDto> findPumpSummaries() {
        // Step 1: hasPump=true 시설 목록 (1번섹션 list 쿼리 그대로 재사용)
        FacilitySearchDto search = new FacilitySearchDto(); search.setHasPump(true);
        List<Facility> facilities = facilityRepository.findFacilities(search);
        if (facilities.isEmpty()) return List.of();

        // Step 2: 시설 IN → PUMP·FLWMTR 계측기 IN 단일 조회
        List<Instrument> instruments = instrumentRepository
            .findByFacilityIdInAndEquipType(facilityIds, SUMMARY_EQUIP_TYPES);

        // Step 3: 계측기 IN → 활성 태그 IN 단일 조회 (PRI·OPS 필터)
        List<Tag> tags = tagRepository
            .findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y)
            .stream().filter(t -> SUMMARY_TAG_TYPES.contains(t.getTagSeCd())).toList();

        // Step 4: 태그 IN → 최신값 단일 조회 (DISTINCT ON + 1시간 프루닝)
        Map<String, RawDataLatestDto> latestByTag = rawDataRepository
            .findLatestByTagSrlNos(tagSrlNos)...toMap();

        // Step 5: 메모리 집계 — 시설 순서(findFacilities 정렬) 보존하며 시설별 매핑
        Map<String, List<Instrument>> instrumentsByFacility = ...groupingBy(facilityId);
        Map<String, List<Tag>> tagsByInstrument = ...groupingBy(instrumentId);
        return facilities.stream()
            .map(f -> mapPumpSummary(f, instrumentsByFacility, tagsByInstrument, latestByTag))
            .toList();
    }
}
```

#### Step 1 정책 — 1번섹션 list 쿼리 정확 미러링

`findFacilities(hasPump=true)` 를 **추가 useYn 필터 없이** 그대로 호출한다 (사용자 확정 결정 2 — `GET /api/facility?hasPump=true` 와 동일 조건). §3 `FacilityStateService` 가 단건에서 `facility.useYn==Y` 를 추가 검사하는 것과 달리, 6번섹션은 1번섹션 목록과 정확히 동일한 시설 집합·정렬을 표출해야 하므로 별도 필터를 추가하지 않는다. 시설 정렬(useYn DESC → dispOrd ASC → facilityNm ASC)은 응답 리스트 순서로 보존한다.

#### Step 5 매핑 — `mapPumpSummary` private 헬퍼 (시설 1건)

- **토출관압**: 해당 시설 FLWMTR 계측기들을 순회하여 PRI 최신값이 있는 **첫 매치** FLWMTR 의 PRI 4필드 채택. PRI 최신값 보유 FLWMTR 가 2개 이상이거나 한 FLWMTR 에 PRI 태그 2개 이상 → `multiplePrsrDetected=true` + `log.warn` (시설 ID·검출 건수). 정상 구성(시설당 토출관압 FLWMTR 1대 + PRI 1개)은 플래그 false. 매치 없으면 prsr 4필드 NULL + 플래그 false (§4 `multipleInFlwmtrDetected` 선례 동일).
- **펌프 대수** (PUMP 계측기별 OPS 최신값 분류, `ot-integration.md §3` 정합):
  - `oprtngPumpCnt` += 1 : `qualityCd==GOOD` AND `rawVal.compareTo(BigDecimal.ONE)==0`
  - `unknownPumpCnt` += 1 : 최신값 결측(null) OR `qualityCd ∈ {BAD, UNCERTAIN}` OR (`qualityCd==GOOD` AND rawVal 이 0/1 아님 — 판정 불가)
  - GOOD AND rawVal=0.0 (정지 확정) → 두 카운트 모두 미증가 (사용자 확정 4 "운전중 대수만" — 전체/정지 대수 미노출)
- **private 헬퍼 분리**: `rawVal`/`corrVal`/`acqDtm`/`qualityCd`/`pickFirstPriFlwmtr`/`classifyOps` 등 단일 책임 헬퍼로 분리하여 메서드 50줄 이내 유지. 50줄 초과가 불가피한 집계 메서드에는 `// §2.5 면책 (db/query-tuning.md §2)` 주석 인용 의무 (`wtp-backend-engineer` 블로커 — 인용 누락 시 REVIEW 블로커). 추상화 2단 이내.
- §3 `FacilityStateService` 의 동명 private 헬퍼는 **추출·공유하지 않는다** — 이전 섹션 자산 자동 재사용 금지 정책 + `coding-discipline.md §2`(요청되지 않은 추상화 계층 금지). PumpSummaryService 전용 private 헬퍼로 독립 보유 (4건 이상 누적 시 별도 사이클에서 공통화 검토).

### Controller

```
@Operation(summary = "송수펌프 시설별 토출관압·운전중 펌프 대수 요약 조회", description = "송수펌프제어분석 6번 섹션 — ...")
@ApiResponses({200, 400, 401, 403, 500})
@GetMapping("/pump-summary")
public ResponseEntity<CommonResponseDto<List<PumpSummaryDto>>> findPumpSummaries() {
    return getResponseEntity(pumpSummaryService.findPumpSummaries());
}
```

- 경로 충돌 검토: 기존 `GET /{facilityId}` 와 `GET /pump-summary` — Spring MVC 는 정적 경로 세그먼트(`pump-summary`)를 path variable(`{facilityId}`)보다 우선 매칭하므로 충돌 없음. (구현 시 매핑 우선순위 단위 테스트로 확인)

## 성공 기준 (검증 가능 형태)

| 성공 기준 | 검증 방법 |
|---------|---------|
| `findPumpSummaries()` 가 hasPump=true 활성 펌프 시설 전체를 반환, 각 시설에 prsr 4필드 + `oprtngPumpCnt` + `unknownPumpCnt` + `multiplePrsrDetected` 포함 | 신규 `PumpSummaryServiceTest` — 시설 N건 fixture 정상 응답 GREEN |
| 선택 시설과 무관하게 전체 목록 반환 (단일 시설 종속 없음, path variable 없음) | `PumpSummaryServiceTest` — `findFacilities(hasPump=true)` mock 결과 시설만 응답·순서 보존 검증 GREEN |
| OPS qualityCd=BAD/UNCERTAIN/결측 펌프는 `oprtngPumpCnt` 제외 + `unknownPumpCnt` 카운트, GOOD rawVal=0.0 은 양쪽 미증가 | `PumpSummaryServiceTest` — OPS BAD/UNCERTAIN/null/0.0/1.0 fixture 각 카운트 정확성 GREEN |
| 한 시설 FLWMTR/PRI 다중 등록 시 첫 매치 + `multiplePrsrDetected=true` + WARN 로그 | `PumpSummaryServiceTest` — PRI 보유 FLWMTR 2건 fixture 시 플래그 true + 첫 매치 값 GREEN |
| SQL 발행 4회 이하 (Facility 1 + Instrument 1 + Tag 1 + RawData 1) | `PumpSummaryServiceTest` — Repository mock 호출 횟수 `verify(...)` 각 1회 확인 |
| 기존 3·4·5번섹션 + facility CRUD 회귀 없음 | ./gradlew.bat :api:test 실행 결과 BUILD SUCCESSFUL |
| 전체 모듈 빌드 통과 | ./gradlew.bat :common:build 후 ./gradlew.bat :api:build 각 BUILD SUCCESSFUL |
| DB 스키마 변경 0건 | common/src/main/resources/db/migration 신규 SQL 파일 부재 확인 (최신 V9_2 유지) |
| Step 1 hasPump EXISTS 쿼리 실행 계획 정상 (Seq Scan 허용 — 실측 없이 인덱스 추가 금지) | bootRun 후 p6spy 로그의 hasPump 쿼리에 EXPLAIN (ANALYZE, BUFFERS) 수동 1회 실행하여 파티션/인덱스 동작 확인 (wtp-dba-reviewer 참고1) |
| `GET /api/facility/pump-summary` Swagger 노출 + `@ArraySchema` 응답 스키마 정상 | bootRun 후 /swagger-ui/index.html 에서 엔드포인트 + PumpSummaryDto 배열 스키마 확인 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| prsr 값 필드 구조 — 평탄 4필드 vs 중첩 PrsrValueDto | 미해결 → 결정 | **평탄 4필드 채택** (§3 FlwmtrStateDto 선례 + wtp-backend-engineer 권고, 1회성 추상화 회피) |
| `unknownPumpCnt` 응답 필드 추가 (도메인 안전 보강) | 결정 | 사용자 승인 완료 (2026-05-18) — 응답 DTO·성공 기준 반영 |
| 집계 메서드 50줄 초과 시 `// §2.5 면책 (db/query-tuning.md §2)` 주석 인용 의무 | 결정 | 구현 단계 예방 — private 헬퍼 분리 우선, 불가피 초과 시 인용 주석 필수 (인용 누락 REVIEW 블로커) |
| 한 시설 FLWMTR 다수 또는 PRI 태그 다수 시 첫 매치 + `multiplePrsrDetected=true` + WARN | 가정 | §4 선례 적용 결정. frontend 경고 UI 책임은 `/dev:spec` SPEC 명기 |
| hasPump 시설 수 상한 미정 — 수십~수백 전제 | 가정 | 수백 초과 시 IN 절 청크 분할은 본 사이클 범위 외 (별도 사이클) |
| Step 1 에서 1번섹션 list 쿼리에 추가 useYn 필터 없이 그대로 재사용 | 결정 | 사용자 확정 결정 2 — `GET /api/facility?hasPump=true` 와 동일 시설 집합·정렬 보존 |
| frontend SPEC 슬러그 (신규 송수펌프제어분석-6번섹션 vs 기존 누적) | 미해결 | `/dev:spec` 단계 사용자 결정 |

분류값: 가정 / 미해결 → 결정

## 제외 사항

- DB 스키마 변경·신규 마이그레이션 SQL — 0건 (조회 전용, 기존 인덱스 완전 커버)
- 신규 Repository 메서드·쿼리 — 0건 (공유 인프라 4종 그대로 재사용)
- 컨테이너 래퍼 DTO (`PumpSummaryListDto`) — 미도입 (`CommonResponseDto<List<PumpSummaryDto>>` 직접 반환)
- §3/§4 private 헬퍼의 공통 유틸 추출 — 본 사이클 범위 외 (자동 재사용 금지 정책)
- 운전 모드(`oprtngType`)·인터록·알람·이력 — 6번섹션은 토출관압 + 운전 대수만 (도메인 4영역 비해당)
- 통합 테스트(@SpringBootTest) — Mockito 단위 테스트 범위로 충분 (§3·§4 동일 방침, test-strategy.md §3)

## 예상 산출물

- [태스크](../../../tasks/20260518/송수펌프제어분석-6번섹션/TASK1.md)

## 부록: 도메인/DB 검토 결과

신규 엔티티·테이블·필드 0건 + DB 설계 변경 0건 → `/dev:plan` §도메인·DB 검토 게이트 **생략 조건 충족** (도메인 모델·DB 변경 모두 없음). ANALYZE1 5인 회의에서 `wtp-domain-expert`(안건 3·5)·`wtp-dba-reviewer`(안건 4) 검토 완료 — 블로커 0건 (도메인 안전 보강 `unknownPumpCnt` 는 사용자 승인으로 해소).
