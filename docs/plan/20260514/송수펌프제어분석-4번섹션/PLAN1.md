---
status: approved
created: 2026-05-14
updated: 2026-05-14
---
# 송수펌프제어분석 4번 섹션 (배수지 DWT 현황 표출) — 계획

## 목적

송수펌프제어분석 화면의 §4 영역을 지원하는 신규 조회 API `GET /api/facility/{parentFacilityId}/dwts/states` 를 추가한다. 1번 섹션에서 활성화한 부모 시설(예: 성주정수장)의 자식 DWT(배수지) 들의 실시간 현황(유입/유출 유량계·밸브·수위계 측정값) 을 단일 호출로 표출한다.

## 배경

§3 (시설 단건 상태) 사이클에서 단일 시설 측정값 조회 패턴 (4-step 체이닝 + DISTINCT ON 파티션 프루닝) 이 확립되었다. 본 §4 사이클은 그 패턴을 **다건(부모-자식 N건)** 으로 확장한다.

- ANALYZE: `docs/analyze/20260514/송수펌프제어분석-4번섹션/ANALYZE1.md` (status: approved, 2026-05-14)
- 5인 회의 결과:
  - 블로커 3건 (Backend Engineer) → ANALYZE 단계에서 모두 해소 (DTO 분리 + Swagger implementation 명시)
  - 권고 4건 (DBA 2건 신규 인덱스 + Backend Engineer 2건 분리 근거·헬퍼)
  - 신규 표준 단어 1건 (`opng`) → ROOT `standard-words.md` 등록 완료
  - 신규 엔티티/DB 컬럼 0건

## 범위

### 포함

- 신규 응답 DTO 6건 (`com.mo.swtp.facility.dto.*`)
- 신규 서비스 1건 (`com.mo.swtp.facility.service.DwtStateService`)
- `FacilityController` 에 `findDwtStates(parentFacilityId)` 메서드 추가
- 신규 단위 테스트 클래스 1건 (`DwtStateServiceTest`)
- DB 인덱스 2건 신규 생성 (V9_2 마이그레이션 SQL 1건)

### 제외

- 신규 엔티티·DB 컬럼·테이블 신설
- 신규 Repository 메서드 추가 (모든 조회는 기존 메서드 재사용)
- SCADA 인바운드/아웃바운드 변경
- 알람·인터록·AI 운전 모드 평가 변경
- frontend 화면 변경 (SPEC 산출물은 `/dev:spec` 단계 선택적 작성)

## 도메인 모델

신규 엔티티·테이블·필드 0건. 본 PLAN 은 응답 DTO 만 신규 작성하며 모든 도메인 엔티티는 기존 자산을 재사용한다.

| 재사용 엔티티 | 역할 (본 PLAN 관점) |
|------------|-----------------|
| `Facility` (부모) | `parentFacilityId` 로 단건 조회 — 미존재/`useYn=N` → 404 |
| `DistributionWaterTank` (DWT 자식) | `parent_facility_id + facility_type_cd='DWT' + use_yn='Y'` 다건 조회 (disp_ord 정렬) |
| `Instrument` 자식 (FlowMeter / Valve / LevelMeter) | DWT 자식들의 계측기 다건 조회 (`equip_type_cd IN ('FLWMTR','VALVE','LVMTR')`) |
| `Tag` | 계측기별 태그 다건 조회 + `Tag.ioCd` enum (INPUT/OUTPUT/BIDIR) 으로 유입/유출 구분 |
| `RawData` (시계열) | `RawDataCustomRepository.findLatestByTagSrlNos` DISTINCT ON + 1시간 파티션 프루닝 |

## DB 설계 변경

### 신규 인덱스 2건 (R1·R2 도입 결정)

ANALYZE 안건 4 의 `wtp-dba-reviewer` 권고를 채택. 본 API 가 시설 활성화 시 매번 호출되는 hot-path 이며, 두 인덱스는 Step 2·Step 3 의 등가 다조건 필터를 정확히 커버한다.

```sql
-- common/src/main/resources/db/migration/V9_2__facility_instrument_lookup_indexes.sql

-- R1: Step 2 (자식 DWT 조회) 용
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_facility_m_parent_type_yn
    ON facility_m (parent_facility_id, facility_type_cd, use_yn);

-- R2: Step 3 (DWT 자식들의 계측기 조회) 용
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_instrument_m_facility_equip
    ON instrument_m (facility_id, equip_type_cd);
```

### 무중단 마이그레이션 전략

- `CREATE INDEX CONCURRENTLY` 사용 — 테이블 락 없이 인덱스 생성 ([`db/indexing-and-migration.md §1`](../../../../.claude/rules/db/indexing-and-migration.md) 정합)
- `IF NOT EXISTS` 로 idempotent 보장 (재실행 안전)
- 주 서비스 시간 외(00:00~06:00) 적용 권고
- 컬럼 추가 없음 → `COMMENT ON COLUMN` 의무 적용 외 (`indexing-and-migration.md §4`)
- 기존 인덱스 (`facility_m (facility_type_cd, parent_facility_id)`·`instrument_m UNIQUE (facility_id, instrument_nm)`) 는 **유지** — 다른 조회 경로에서 활용 가능

### 인덱스 검증 (운영)

PR 머지 후 staging 환경에서:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM facility_m
WHERE parent_facility_id = '{uuid}' AND facility_type_cd = 'DWT' AND use_yn = 'Y'
ORDER BY disp_ord ASC;
-- 기대: Index Scan using idx_facility_m_parent_type_yn

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM instrument_m
WHERE facility_id IN ('{uuid1}', '{uuid2}', '{uuid3}', '{uuid4}')
  AND equip_type_cd IN ('FLWMTR', 'VALVE', 'LVMTR');
-- 기대: Index Scan using idx_instrument_m_facility_equip
```

## 구현 방향

### API 시그니처

```java
@Tag(name = "01. 시설 관리")
@Operation(summary = "부모 시설의 자식 배수지(DWT) 실시간 현황 조회")
@GetMapping("/{parentFacilityId}/dwts/states")
public ResponseEntity<CommonResponseDto<DwtGroupStateDto>> findDwtStates(
        @PathVariable String parentFacilityId);
```

### 응답 DTO 구조 (ANALYZE 결정 그대로)

```
DwtGroupStateDto (컨테이너)
├ parentFacilityId : String
├ parentFacilityNm : String
└ dwts : List<DwtStateDto>                                @ArraySchema
    └ DwtStateDto
       ├ facilityId : String
       ├ facilityNm : String
       ├ inFlwmtr : InletFlwmtrStateDto                   @Schema(implementation=...)
       │   ├ instrumentId, instrumentNm
       │   ├ flwrtRawVal, flwrtCorrVal, flwrtAcqDtm, flwrtQualityCd  (FRI)
       │   └ prsrRawVal, prsrCorrVal, prsrAcqDtm, prsrQualityCd      (PRI)
       ├ outFlwmtr : OutletFlwmtrStateDto                 @Schema(implementation=...)
       │   ├ instrumentId, instrumentNm
       │   └ flwrtRawVal, flwrtCorrVal, flwrtAcqDtm, flwrtQualityCd  (FRI)
       ├ multipleInFlwmtrDetected : boolean
       ├ multipleOutFlwmtrDetected : boolean
       ├ valves : List<ValveStateDto>                     @ArraySchema
       │   └ instrumentId, instrumentNm,
       │     opngRawVal, opngCorrVal, opngAcqDtm, opngQualityCd      (VOI)
       └ lvmtrs : List<LvmtrStateDto>                     @ArraySchema
           └ instrumentId, instrumentNm,
             wtlvRawVal, wtlvCorrVal, wtlvAcqDtm, wtlvQualityCd      (LEI)
```

- `qualityCd` 필드 타입: `com.mo.swtp.raw.enumtype.QualityCode` enum 재사용 + `@Schema(implementation = QualityCode.class)` 명시
- `acqDtm` (LocalDateTime): `@JsonFormat(shape = STRING, pattern = "yyyy-MM-dd HH:mm:ss")` 초 단위 SSOT
- `BaseAuditResponseDto` 미상속 (실시간 통지 응답 DTO 적용 외, `api-patterns.md §BaseAuditResponseDto 적용 범위`)
- 모든 사용자 정의 enum/DTO 참조형 필드에 `@Schema(implementation = ...)` 명시 의무 (`api-patterns.md §DTO @Schema(implementation) 명시 패턴`)
- 모든 `List<E>` 필드에 `@ArraySchema(schema = @Schema(implementation = E.class))` 명시 의무

### Service 흐름 (`DwtStateService.findDwtStates(parentFacilityId)`)

```
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DwtStateService {

    private static final List<EquipType> DWT_EQUIP_TYPES =
            List.of(EquipType.FLWMTR, EquipType.VALVE, EquipType.LVMTR);
    private static final Set<TagMeasurementType> DWT_TAG_TYPES =
            EnumSet.of(TagMeasurementType.FRI, TagMeasurementType.PRI,
                       TagMeasurementType.VOI, TagMeasurementType.LEI);

    Step 1: facilityRepository.findByIdAndUseYn(parentFacilityId, YnType.Y)
            → Optional.empty() → throw RestApiException(FACILITY_NOT_FOUND)
    Step 2: dwts = facilityRepository.findByParentFacilityIdAndFacilityTypeCd
                AndUseYnOrderByDispOrdAsc(parentFacilityId, "DWT", YnType.Y)
            → empty → return DwtGroupStateDto.of(parent, List.of())
    Step 3: dwtIds = dwts.map(facilityId)
            instruments = instrumentCustomRepository.findByFacilityIdInAndEquipType(
                    dwtIds, DWT_EQUIP_TYPES)
    Step 4: tags = tagRepository.findByInstrumentInstrumentIdInAndUseYn(instIds, YnType.Y)
                .filter(tag.tagSeCd IN DWT_TAG_TYPES)
    Step 5: latestByTag = rawDataCustomRepository.findLatestByTagSrlNos(tagSrlNos)
                .toMap(tagSrlNo, dto)
    Step 6 (메모리 조립):
            instrumentsByDwt = instruments.groupBy(facility.facilityId)
            tagsByInstrument = tags.groupBy(instrument.instrumentId)
            foreach dwt in dwts:
              flwmtrs = instrumentsByDwt[dwt.id].filter(FLWMTR)
              inletList  = flwmtrs.filter(any tag.ioCd IN [INPUT, BIDIR])
              outletList = flwmtrs.filter(any tag.ioCd IN [OUTPUT, BIDIR])
              inletDto  = mapInletFlwmtr(inletList.firstOrNull(), tagsByInstrument, latestByTag)
              outletDto = mapOutletFlwmtr(outletList.firstOrNull(), tagsByInstrument, latestByTag)
              multipleIn  = inletList.size() > 1
              multipleOut = outletList.size() > 1
              valves = instrumentsByDwt[dwt.id].filter(VALVE).map(mapValve)
              lvmtrs = instrumentsByDwt[dwt.id].filter(LVMTR).map(mapLvmtr)
              dwtDtos.add(DwtStateDto.of(dwt, inletDto, outletDto,
                          multipleIn, multipleOut, valves, lvmtrs))
            return DwtGroupStateDto.of(parent, dwtDtos)
```

SQL 발행 4회 (Facility + Facility + Instrument + Tag + RawData = 5회 → Step 1 단건 조회 포함 시 5회, Step 1 생략 가능 시 4회). N+1 회피 메모리 그룹화.

### 매핑 헬퍼 분리 정책

`findDwtStates` 메서드는 §3 의 `FacilityStateService.findFacilityState` 와 유사한 4-step 메서드. 50줄 초과 회피를 위해 private 매핑 헬퍼 4개 분리:

- `private InletFlwmtrStateDto mapInletFlwmtr(...)` (FRI + PRI 8필드)
- `private OutletFlwmtrStateDto mapOutletFlwmtr(...)` (FRI 4필드)
- `private ValveStateDto mapValve(...)` (VOI 4필드)
- `private LvmtrStateDto mapLvmtr(...)` (LEI 4필드)

ROOT `coding-discipline.md §2.1` 메서드 50줄 임계 준수.

### 다중 등록 안전망

- 한 DWT 에 유입 FLWMTR 2건 이상 등록 시 → 첫 매치 + `multipleInFlwmtrDetected = true` + WARN 로그
- 한 DWT 에 유출 FLWMTR 2건 이상 등록 시 → 동일
- 운전원이 화면에서 다중 등록을 인지 가능 (Domain Expert 권고)
- WARN 로그 메시지: `"DWT {facilityId} 에 유입 FLWMTR 다중 등록 감지 (N건). 첫 매치 사용."`

## 성공 기준 (검증 가능 형태)

| # | 성공 기준 | 검증 명령 / 테스트 / 조회 |
|---|---------|----------------------|
| 1 | 정상 응답 — DWT 2건, 각 inlet/outlet/valve/lvmtr 보유 | `./gradlew.bat :api:test --tests DwtStateServiceTest.findDwtStates_정상응답` GREEN. 응답의 dwts.size()==2, 각 dwt 의 inFlwmtr/outFlwmtr 의 flwrtRawVal not null, valves.size()>=1, lvmtrs.size()>=1 |
| 2 | 부모 미존재 → FACILITY_NOT_FOUND | `./gradlew.bat :api:test --tests DwtStateServiceTest.findDwtStates_부모미존재` GREEN. assertThatThrownBy(() -> service.findDwtStates(...)).isInstanceOf(RestApiException.class) + errorCode == FacilityErrorCode.FACILITY_NOT_FOUND |
| 3 | 자식 DWT 0건 → 200 + 빈 리스트 | `./gradlew.bat :api:test --tests DwtStateServiceTest.findDwtStates_자식DWT0건_빈리스트` GREEN. 응답의 dwts == empty List + 컨테이너 부모 필드 채워짐 |
| 4 | 다중 유입 FLWMTR 등록 → multipleInFlwmtrDetected = true | `./gradlew.bat :api:test --tests DwtStateServiceTest.findDwtStates_유입중복등록_플래그TRUE` GREEN. fixture: DWT 1건 + 유입 FLWMTR 2건. 응답에 multipleInFlwmtrDetected==true + inFlwmtr 는 첫 매치만 채워짐 |
| 5 | Swagger UI 노출 + DTO 스키마 완전성 | `./gradlew.bat :api:bootRun` 후 브라우저에서 `http://localhost:8080/swagger-ui/index.html` 접속 → GET /api/facility/{parentFacilityId}/dwts/states 엔드포인트 노출 확인. Schemas 탭에서 DwtGroupStateDto·DwtStateDto·InletFlwmtrStateDto·OutletFlwmtrStateDto·ValveStateDto·LvmtrStateDto 6건 모두 노출 + 모든 List 가 @ArraySchema 로 element 타입 노출 |
| 6 | SQL 발행 5회 이하 (N+1 회피) | DwtStateServiceTest 의 정상응답 케이스에 Hibernate SessionFactory Statistics 또는 spring.jpa.show-sql 카운트 검증 추가. queryExecutionCount <= 5 assertion |
| 7 | 전체 빌드 통과 | `./gradlew.bat clean build` BUILD SUCCESSFUL |
| 8 | 신규 인덱스 V9_2 마이그레이션 idempotent | V9_2 SQL 을 staging 에 2회 적용 → 첫 적용 인덱스 생성, 2회 적용 IF NOT EXISTS 분기로 무동작. `\d facility_m` `\d instrument_m` 로 인덱스 존재 확인 |

## 가정 및 미해결 질문

ANALYZE1.md "## 가정 및 미해결 질문" 7건을 PLAN 단계 결정으로 변환.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `Tag.io_cd` BIDIR 태그는 유입·유출 양쪽 후보로 취급 | 가정 → 결정 | 채택. `inletList = flwmtrs.filter(any tag.ioCd IN [INPUT, BIDIR])`·`outletList = filter([OUTPUT, BIDIR])` 양쪽 후보 허용. 한 FlowMeter 가 양쪽 모두 등록되면 양쪽 응답에 채워짐 |
| "유입 압력" 의미는 단순 PRI (차압 아님) | 가정 → 결정 | 채택. PRI 태그 1건의 측정값을 그대로 노출. 차압 도입 시 별도 사이클에서 `delta_p` 등 신규 표준 단어 등록 |
| 한 DWT 에 유입 FLWMTR 2건 이상 → 첫 매치 + `multipleInFlwmtrDetected = true` 플래그 + WARN 로그 | 결정 | 위 §구현 방향 다중 등록 안전망 그대로 채택. 응답 필드 boolean 노출 + 로그 메시지 한국어 |
| LVMTR 다수 결측 시 단건 필터 금지, 전체 목록 + 각 측정값 `qualityCd` 노출 의무 | 결정 | `lvmtrs : List<LvmtrStateDto>` 전체 노출. BAD/UNCERTAIN 수위계 인지 보장. 단건 표출 정책은 frontend SPEC 책임 |
| `corr_val` NULL 시 frontend 가 `raw_val` fallback — backend 의무 영역 외 | 결정 | backend 는 raw·corr·qualityCd 3값 무조건 노출. SPEC 명기로 frontend 에 fallback 정책 전파 |
| `qualityCd` 응답 필드 타입: §3 의 `QualityCode` enum 재사용 + `@Schema(implementation = QualityCode.class)` | 가정 → 결정 | 채택. §3 의 `RawDataLatestDto.qualityCd` 가 `QualityCode` enum 이라면 그대로 정렬. 만약 §3 가 String 사용 중이면 본 사이클에서 §3 도 enum 정렬 (`/dev:impl` 단계 코드 확인 후 진행) |
| `multipleInFlwmtrDetected`·`multipleOutFlwmtrDetected` 응답 플래그 boolean 노출 | 결정 | 위 §응답 DTO 구조 채택 |

## 제외 사항

- DWT 외 다른 자식 시설(PWTF·RSV·PRSF·POINT) 의 현황은 본 PLAN 범위 외
- 시계열 트렌드 그래프 (분단위·시단위 집계) 는 본 PLAN 범위 외 — 별도 사이클
- 알람·인터록·AI 운전 모드 표출 — 본 사이클은 순수 측정값 조회만
- 위·아래 시간 범위 페이지네이션 — 본 API 는 "최신 1건만" (`findLatestByTagSrlNos` 재사용)
- DTO 매퍼 라이브러리 도입 (MapStruct 등) — 현 프로젝트 패턴은 정적 팩토리 직접 매핑

## 부록: 도메인/DB 검토 결과

ANALYZE1.md 의 5인 회의 안건 2 (Domain Expert) + 안건 4 (DBA) 에서 이미 검토 완료. 본 PLAN 의 도메인 모델·DB 설계 변경 사항은 ANALYZE 결정 그대로 반영하였으므로 추가 검토 게이트 호출 생략.

- **wtp-domain-expert** (ANALYZE 안건 2): 블로커 0건, 권고 0건, 참고 6건. 도메인 4영역 모두 비해당 확인. 본 PLAN 의 "가정 및 미해결 질문" 7건 모두 검토 권고 사항 반영
- **wtp-dba-reviewer** (ANALYZE 안건 4): 블로커 0건, 권고 인덱스 2건 (R1·R2) → 본 PLAN 에서 V9_2 마이그레이션 SQL 로 도입 채택. 신규 표준 데이터 도메인 0건

추가 검토 불필요 — ANALYZE 결정의 일관 반영 + 신규 엔티티/컬럼 0건.

## 예상 산출물

- 신규 자바 클래스 7건:
  - `api/src/main/java/com/mo/swtp/facility/dto/DwtGroupStateDto.java`
  - `api/src/main/java/com/mo/swtp/facility/dto/DwtStateDto.java`
  - `api/src/main/java/com/mo/swtp/facility/dto/InletFlwmtrStateDto.java`
  - `api/src/main/java/com/mo/swtp/facility/dto/OutletFlwmtrStateDto.java`
  - `api/src/main/java/com/mo/swtp/facility/dto/ValveStateDto.java`
  - `api/src/main/java/com/mo/swtp/facility/dto/LvmtrStateDto.java`
  - `api/src/main/java/com/mo/swtp/facility/service/DwtStateService.java`
- 신규 테스트 클래스 1건:
  - `api/src/test/java/com/mo/swtp/facility/service/DwtStateServiceTest.java`
- 신규 마이그레이션 SQL 1건:
  - `common/src/main/resources/db/migration/V9_2__facility_instrument_lookup_indexes.sql`
- 수정 자바 클래스 1건:
  - `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` (`findDwtStates` 메서드 추가)
- 산출물 문서:
  - [태스크](../../../tasks/20260514/송수펌프제어분석-4번섹션/TASK1.md) — `/dev:task` 단계 작성 예정
