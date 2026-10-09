---
status: completed
created: 2026-06-08
updated: 2026-06-08
---
# 시설 도메인 확장 — 결과

## 관련 작업
- [계획안](../../../plan/20260608/시설_도메인_확장/PLAN1.md)
- [태스크](../../../tasks/20260608/시설_도메인_확장/TASK1.md)
- [분석](../../../analyze/20260608/시설_도메인_확장/ANALYZE1.md)

## 작업 요약

시설 도메인에 두 개념을 도입했다.

1. **시설 유형 7종 추가** (운영시설) — WTBLD(송수동)·CHMB(약품동)·ACFB(활성탄여과지)·POZB(전오존동)·FLTB(여과지동)·DEWB(탈수기동)·SOLAR(태양광). 기존 5종(PWTF/DWT/RSV/POINT/PRSF) + 7종 = **총 12종**.
2. **시설 그룹 분류** (`FacilityGroup`) — STORAGE(저장)/OPERATION(운영)/NETWORK(계통). `FacilityType` 파생 속성으로 1:1 불변 매핑 (DB 컬럼 미신설, 응답 DTO `facilityGroupCd` 계산 노출).

범위는 **모델 + 등록 API 확장** — 자식 엔티티 7종 skeleton, 등록/수정 요청 DTO 7종, 응답 DTO 7종, Service switch 디스패치, Controller 다형성 스키마, DDL patch. 1~6번 섹션 대시보드 API 는 차기 슬러그.

### 그룹 매핑 결과

| FacilityGroup | 소속 FacilityType | 수 |
|---------------|-------------------|----|
| STORAGE (저장) | PWTF·DWT·RSV | 3 |
| OPERATION (운영) | PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR | 8 |
| NETWORK (계통) | POINT | 1 |

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 9 |
| 체크박스 수 | 38 |
| 분할 여부 | N |
| 분할 근거 | — (Phase 9 < 10, 체크박스 38 < 60, 7종이 계층별 반복 패턴이라 분할 시 오히려 파편화) |

## 변경 사항

### 의도된 변경

**enum (common)**
- `FacilityGroup.java` 신규 — STORAGE/OPERATION/NETWORK + 한글 설명, `@Getter @RequiredArgsConstructor`
- `FacilityType.java` 수정 — 생성자 필드 2개(description·group) + 12종 + `getGroup()`

**자식 엔티티 7종 (common)** — `WaterTransmissionBuilding`·`ChemicalBuilding`·`ActivatedCarbonFilter`·`PreOzonationBuilding`·`FiltrationBuilding`·`DewateringBuilding`·`SolarPowerFacility` (PurifiedWaterTank skeleton 선례 동일, `@DiscriminatorValue` + 정적 팩토리)

**요청 DTO 7종 + 부모 (api)** — `Wtbld/Chmb/Acfb/Pozb/Fltb/Dewb/Solar UpsertDto` (자식 전용 필드 0) + `FacilityUpsertDto` 다형성 4→11 (POINT 제외)

**응답 DTO 7종 + 부모 (api)** — `Wtbld/Chmb/Acfb/Pozb/Fltb/Dewb/Solar Dto` + `FacilityDto` 다형성 5→12 + `facilityGroupCd` 파생 필드(`applyCommonFields()` 에서 `getFacilityType().getGroup()` 주입)

**Service (api)** — `FacilityService` saveFacility/updateFacility switch 4→11 + private save/update 메서드 각 7종

**Controller (api)** — `FacilityController` GET 목록·단건 `@ApiResponse(oneOf)` 5→12 (2곳) + 등록/수정 `@Operation` description + 클래스 Javadoc

**DDL (양쪽 동시 갱신, §5.3)**
- `V2_1__facility_patch.sql` 신규 — 자식 7테이블(facility_id PK + FK ON DELETE RESTRICT) + COMMENT + `facility_m.facility_type_cd` COMMENT 12종 갱신
- `docs/ddl/facility.sql` 수정 — 동일 내용 누적 (헤더·TABLE/컬럼 COMMENT 12종 정렬)

**테스트**
- `FacilityTypeTest.java` 신규 (common) — 12종 그룹 매핑 + 그룹별 카운트(3·8·1) 6건
- `FacilityServiceTest.java` 수정 — CHMB 등록(switch default 미진입) + 단건조회 facilityGroupCd=OPERATION 2건 추가
- `FacilityDtoSerializationTest.java` 수정 — 신규 7종 다형성 직렬화 + facilityGroupCd 파생 노출 + STORAGE/NETWORK 그룹 검증

**표준 사전 (ANALYZE 룰 갱신 지시서 산출물)**
- `swtp/.claude/rules/dict/standard-words.md` — `group` 단어 등록
- `.claude/rules/dict/standard-terms.md` — `facility_type_cd` 비고 12종/그룹 반영

### 다형성 동기화 9곳 (교차 확인 결과)

| # | 위치 | 변경 | 종 수 |
|---|------|------|------|
| 1 | FacilityDto `@JsonSubTypes` | 5→12 | 응답 12 |
| 2 | FacilityDto `@Schema(oneOf)` | 5→12 | 응답 12 |
| 3 | FacilityDto `from()` switch | 5→12 | 응답 12 |
| 4 | FacilityUpsertDto `@JsonSubTypes` | 4→11 | 요청 11 |
| 5 | FacilityUpsertDto `@Schema(oneOf)` | 4→11 | 요청 11 |
| 6 | FacilityService `saveFacility` switch | 4→11 | 요청 11 |
| 7 | FacilityService `updateFacility` switch | 4→11 | 요청 11 |
| 8 | FacilityController GET 목록 `@ApiResponse(oneOf)` | 5→12 | 응답 12 |
| 9 | FacilityController GET 단건 `@ApiResponse(oneOf)` | 5→12 | 응답 12 |

> 응답측 12종(POINT 포함)·요청측 11종(POINT 제외 — SCADA 자동 생성) 비대칭 의도 유지.

### 계획 외 변경

- `FacilityDtoSerializationTest.java` 의 기존 테스트 `FacilityDto_from_은_자식_타입에_매칭되는_DTO_인스턴스를_반환한다` 에 `setFacilityType()` 5건 주입 추가 (의도된 필수 부수 변경). 사유: 새 `applyCommonFields()` 가 `getFacilityType().getGroup()` 을 역참조하므로 discriminator 미설정 시 NPE. 운영 경로(`from()` 은 항상 DB 로드 영속 엔티티에만 호출, discriminator 자동 채움)는 NPE 불가이나, 단위 테스트의 transient 엔티티는 형제 테스트 패턴대로 discriminator 주입 필요. §2 단순성 원칙상 운영 불가 시나리오 방어 코드 대신 테스트 정정 선택.

## 테스트 결과

- `./gradlew.bat clean build` → **BUILD SUCCESSFUL** (GRADLE_EXIT=0, 1m 40s, QClass 7종 재생성 포함)
- 신규/수정 테스트:
  - `FacilityTypeTest` (common) — 6건 PASS (실패 0)
  - `FacilityServiceTest` (api) — 23건 PASS (실패 0)
  - `FacilityDtoSerializationTest` (api) — 10건 PASS (실패 0)
- 전체 회귀: common·api·scheduler 3모듈 테스트 통과 (Fix 전 1건 실패 → 기존 테스트 discriminator 주입 누락, 정정 후 GREEN)
- DDL COMMENT 훅(`check-ddl-column-comment.sh`) — V2_1 patch 작성 시 차단 0 (자식 7테이블 facility_id COMMENT 누락 0)

## 비고

- **발견 사항 (TASK 외, 직접 수정 안 함)**: `common/.../facility/domain/Facility.java` 의 `facilityType` 필드 Javadoc(`// 시설 유형 코드 — DiscriminatorColumn (PWTF·DWT·RSV)`)·클래스 Javadoc 이 기존부터 stale (POINT/PRSF 도 누락 상태였음). 본 사이클로 12종이 되어 더 부정확해졌으나 `coding-discipline §3` 정밀한 수정 원칙상 TASK 범위 밖이라 직접 변경하지 않고 보고만 한다. 별도 docs 정합 사이클 또는 차기 facility 작업 시 정정 권고.
- 신규 7종은 자식 전용 컬럼 0건 skeleton — 자식 전용 컬럼(용량·면적 등) 도입은 요구사항 확정 시 차기 PLAN.
- 그룹 기준 조회 필터(`FacilitySearchDto.facilityGroupCd`)는 미도입 — 섹션 API 에서 필요 시 도입 (§2 미요청 기능 배제).
