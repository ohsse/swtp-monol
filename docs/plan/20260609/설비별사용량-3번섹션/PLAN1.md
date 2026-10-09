---
status: approved
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 3번섹션 — 전력 설비(계측기) 목록 조회 API 계획

## 관련 분석
- [분석](../../../analyze/20260609/설비별사용량-3번섹션/ANALYZE1.md) (status: approved)

## 목적

설비별 사용량 화면 3번 섹션(설비목록) API 를 신규 추가한다. 2번 섹션(`GET /api/facility/energy-usage`)에서 운영시설 목록을 조회한 뒤 front 가 그 중 하나의 시설을 선택하면, 그 시설 ID 를 파라미터로 받아 **해당 시설을 루트로 하는 재귀 하위 트리 전체(루트 inclusive)** 의 활성 계측기 중 **전력관련 태그(PWI 순시전력 ∪ PWQ 적산전력량)** 를 보유한 설비 목록을 반환한다. 이 목록이 5/6/7 섹션(설비별 통계·비율·순시전력 차트)의 입력이 된다.

## 배경

- 2번 섹션(`FacilityEnergyUsageService`)·송수펌프 상태(`FacilityStateService`)·8번 섹션(`FacilityDownstreamTreeResolver`)이 이미 확립한 두 가지 패턴을 그대로 따른다:
  - **시설 → 계측기 → 태그 IN 절 일괄 조회 + 인메모리 멤버십 필터** (N+1 회피)
  - **app-level BFS 재귀 하위 수집** (`parent_facility_id` self-FK, visited-set + depth 백스톱)
- ANALYZE1 5인 회의 결론: 신규 엔티티·테이블·DB 컬럼·마이그레이션·인덱스 **0건**, 신규 표준 단어/데이터 도메인/표준 용어 **0건**, 도메인 4영역(알람·인터록·운전모드·이력기록) **전부 비해당**(읽기 전용 표출).

## 범위

### 포함

- 신규 Service 1개: `FacilityPowerInstrumentService` (`com.mo.swtp.facility.service`)
- 신규 응답 DTO 1개 + 중첩 DTO 1개: `FacilityPowerInstrumentDto`(+ 중첩 정적 클래스 `PowerTagDto`) (`com.mo.swtp.facility.dto`)
- `FacilityController` 에 엔드포인트 1개 추가: `GET /api/facility/{facilityId}/power-instruments`
- 신규 단위 테스트 1개: `FacilityPowerInstrumentServiceTest`

### 제외

- 5/6/7 섹션(설비별 통계·비율·순시전력 차트) — 본 목록 API 가 선행 조건이며 별도 사이클
- 시계열 전력값 집계·차트 데이터 — 본 API 는 "전력태그 보유 설비 목록"까지만 책임
- 신규 인덱스·테이블·마이그레이션·엔티티 (ANALYZE1 결정)

## 구현 방향

### 1) 재사용하는 기존 자산 (변경 없음)

| 자산 | 용도 |
|------|------|
| `FacilityRepository.findByParentFacilityIdInAndUseYn(List<String>, YnType)` | BFS 레벨당 활성 하위 시설 조회 |
| `InstrumentRepository.findByFacilityFacilityIdInAndUseYn(List<String>, YnType)` | 시설 집합의 활성 계측기 일괄 조회(`@BatchSize(100)`) |
| `TagRepository.findByInstrumentInstrumentIdInAndUseYn(List<String>, YnType)` | 계측기 집합의 활성 태그 일괄 조회 |
| `TagMeasurementType.PWI` · `PWQ` | 전력태그 멤버십 판정 |
| `EquipType` · `TagMeasurementType` | DTO enum 필드 |
| `FacilityErrorCode.FACILITY_NOT_FOUND` (404) | 미존재·비활성 루트 처리 |
| `idx_facility_m_parent_type_yn (parent_facility_id, facility_type_cd, use_yn)` | BFS 레벨 조회 인덱스 (이미 존재) |

### 2) `FacilityPowerInstrumentService` 흐름

`@Service @RequiredArgsConstructor @Transactional(readOnly = true)`. 의존: `FacilityRepository` · `InstrumentRepository` · `TagRepository`. 상수: `POWER_TAG_TYPES = EnumSet.of(PWI, PWQ)`, `MAX_DEPTH = 10`.

public `findPowerInstruments(String facilityId)` → `List<FacilityPowerInstrumentDto>`:

1. `findActiveFacilityOrThrow(facilityId)` — `findById` + `useYn == Y` 검사, 아니면 `FACILITY_NOT_FOUND`. (`FacilityStateService` 동형 복제)
2. `collectSubtree(root)` — 루트 + 모든 활성 재귀 하위를 레벨당 `findByParentFacilityIdInAndUseYn` 1회로 수집. visited-set 순환 방어 + `MAX_DEPTH` 백스톱. (`FacilityDownstreamTreeResolver.collectSubtree` 동형 복제)
3. 수집 시설로 `Map<facilityId, Facility>` 구성 → 응답 `facilityNm` + 정렬 `dispOrd` lookup (계측기 `getFacility().getFacilityId()` 는 프록시 id 접근으로 추가 쿼리 0, `facilityNm` 은 이 맵에서 조회해 N+1 회피).
4. `instrumentRepository.findByFacilityFacilityIdInAndUseYn(facilityIds, Y)` — 종류 무필터.
5. `tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, Y)` → `POWER_TAG_TYPES` 인메모리 필터 → `instrumentId` 로 `groupingBy`.
6. 전력태그 ≥1 보유 계측기만 추출하여 `FacilityPowerInstrumentDto.of(...)` 조립.
7. 정렬: 시설 `dispOrd` ASC → 계측기 `dispOrd` ASC → `instrumentNm` ASC.

> SQL 호출 = 루트 Facility 1 + BFS(깊이 N) + Instrument IN 1 + Tag IN 1 — 시설 수 무관 고정. public 메서드는 50줄 이내, BFS·검증·조립은 private 헬퍼로 분리(coding-discipline §2.1).

### 3) `FacilityPowerInstrumentDto`

`@Getter` + private 기본 생성자 + 정적 팩토리 `of(...)`. `BaseAuditResponseDto` 미상속(단순 조회 응답 — `FacilityEnergyUsageDto` 선례).

| 필드 | 타입 | 비고 |
|------|------|------|
| `instrumentId` | String | 계측기 ID |
| `instrumentNm` | String | 계측기명 |
| `equipTypeCd` | `EquipType` | `@Schema(implementation = EquipType.class)` |
| `facilityId` | String | 소속 시설 ID |
| `facilityNm` | String | 소속 시설명 (Map lookup) |
| `tags` | `List<PowerTagDto>` | `@ArraySchema(schema = @Schema(implementation = PowerTagDto.class))` |

중첩 정적 클래스 `PowerTagDto`: `tagSrlNo`(String), `tagSeCd`(`TagMeasurementType`, `@Schema(implementation = TagMeasurementType.class)`).

### 4) `FacilityController` 엔드포인트

`GET /api/facility/{facilityId}/power-instruments` → `ResponseEntity<CommonResponseDto<List<FacilityPowerInstrumentDto>>>`. `getResponseEntity(data)` 래핑. `@Tag("06. 시설물 관리")` 유지, `@Operation` + `@ApiResponses`(200/400/401/403/404/500, 404 = `FACILITY_NOT_FOUND`).

## 성공 기준 (검증 가능 형태)

| 성공 기준 | 검증 |
|---------|------|
| 미존재·비활성 루트 시설 ID → `FACILITY_NOT_FOUND` | 신규 단위 테스트 2건 GREEN — findById empty / useYn=N |
| 루트 inclusive + 재귀 하위 전체 BFS 수집 | 신규 단위 테스트 GREEN — 루트 직속 설비 + 2단 하위 설비 동시 포함 검증 |
| PWI∪PWQ 보유 설비만 포함, 비전력 태그(FRI 등) 보유 설비 제외 | 신규 단위 테스트 GREEN — FRI-only 설비 미포함, PWI-only·PWQ-only 각각 포함 |
| PWI+PWQ 동시 보유 설비 → 1행 + tags 2건 | 신규 단위 테스트 GREEN — tags 크기 2 검증 |
| 전력태그 설비 0건 → 빈 목록 반환(예외 아님) | 신규 단위 테스트 GREEN — 빈 List |
| `facilityNm` 매핑 정확성 (Map lookup) | 신규 단위 테스트 GREEN — 하위 시설 설비의 facilityNm 일치 |
| 빌드·전체 테스트 통과 | `./gradlew.bat :api:test` PASS + `./gradlew.bat clean build` BUILD SUCCESSFUL |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 활성만 집계 — 시설·계측기·태그 모두 `use_yn = Y` (2번 섹션 정합) | 가정 → 결정 | 채택. BFS 하위 탐색도 `...AndUseYn(..., Y)` 로 활성 하위만 |
| 루트 inclusive — 루트 시설 자신의 설비도 포함 | 가정 → 결정 | 채택 (사용자 명시 요구) |
| 재귀 깊이 백스톱 `MAX_DEPTH = 10` + visited-set 순환 방어 | 가정 → 결정 | 채택 (`FacilityDownstreamTreeResolver` 동형) |
| 정렬 키 = 시설 `dispOrd` → 계측기 `dispOrd` → `instrumentNm` | 가정 → 결정 | 채택 |
| BFS 공유 컴포넌트 추출 vs 동형 복제 | 미해결 → 결정 | 동형 복제 채택 (working code 비침습, coding-discipline §3 + ANALYZE1 wtp-backend-engineer 결론) |

## 제외 사항

- 5/6/7 섹션 차트·통계 데이터 API (별도 사이클)
- 신규 인덱스·테이블·마이그레이션·엔티티
- BFS 공유 컴포넌트 추출 리팩토링 (동형 복제 사본 3종 누적 시 별도 ANALYZE 재검토)

## 예상 산출물
- [태스크](../../../tasks/20260609/설비별사용량-3번섹션/TASK1.md)
