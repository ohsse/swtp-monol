---
status: approved
created: 2026-06-08
updated: 2026-06-08
---
# 시설 도메인 확장 — 시설 유형 7종 + 그룹 분류(저장/운영/계통) 도입

## 목적

`시설별 사용량` 대시보드(1~6번 섹션) API 의 전제로, facility 도메인에 다음 2개 개념을 도입한다.

1. **신규 시설 유형 7종** — 송수동(WTBLD)·약품동(CHMB)·활성탄여과지(ACFB)·전오존동(POZB)·여과지동(FLTB)·탈수기동(DEWB)·태양광(SOLAR). 기존 5종(PWTF·DWT·RSV·POINT·PRSF) JPA JOINED 다형성에 자식 7종을 추가한다.
2. **시설 그룹 분류** — STORAGE(저장시설)/OPERATION(운영시설)/NETWORK(계통시설). `FacilityType` enum 파생 속성(DB 컬럼 미신설)으로 응답 DTO 에 `facilityGroupCd` 계산 노출.

본 사이클은 **도메인 확장 + 등록 API 확장**만 수행한다. 1~6번 섹션 대시보드 API 는 후속 `시설별사용량-N번섹션` 별도 슬러그로 진행한다.

## 배경

- 관련 분석: [ANALYZE1](../../../analyze/20260608/시설_도메인_확장/ANALYZE1.md) (`status: approved`) — 5인 회의 4안건 종결, 블로커 0.
- 현 facility 도메인: `Facility`(추상 부모, `@Inheritance(JOINED)` + `@DiscriminatorColumn("facility_type_cd")`) + 자식 5종 + 응답 DTO 3단 상속(`BaseAuditResponseDto → FacilityDto → 자식Dto`) + 요청 DTO 다형성(`FacilityUpsertDto → 자식UpsertDto`, POINT 제외) + `FacilityService` switch 디스패치.
- "시설 그룹/분류" 개념은 코드·DDL 부재 — 신규 도입.
- 사용자 확정(토의): 그룹=파생 속성 / 신규 7종=JOINED skeleton / 송수동·PRSF=OPERATION / 범위=모델+등록 API.

## 범위

### 포함
- `FacilityGroup` enum 신규 (common)
- `FacilityType` enum 생성자 도입(한글명 + group) + getGroup() + 신규 7종 (common)
- 자식 엔티티 7종 skeleton (common)
- 요청 DTO 7종 + `FacilityUpsertDto` 다형성 확장 (api)
- 응답 DTO 7종 + `FacilityDto` 다형성 확장 + `facilityGroupCd` 파생 필드 (api)
- `FacilityService` saveFacility/updateFacility switch + private 메서드 14 (api)
- `FacilityController` 등록/수정 다형성 + `@ApiResponse(oneOf)` 2곳 갱신 (api)
- `V2_1__facility_patch.sql` + `docs/ddl/facility.sql` 동시 갱신
- 단위/통합 테스트

### 제외 (후속/섹션 작업)
- 1~6번 섹션 대시보드 API (별도 슬러그)
- 그룹 기준 조회 필터(`FacilitySearchDto.facilityGroupCd`) — 섹션 API 필요 시 도입 (`coding-discipline §2` 미요청 기능 배제)
- 신규 7종 자식 전용 컬럼 — 요구사항 확정 시 후속 PLAN (`V2_2__facility_patch.sql` 분리)
- 신규 7종 계측기(instrument) 부착·전력 태그(PWI/PWQ) 매핑 — 섹션 API 범위

## 구현 방향

### 1. enum (common 모듈)

#### 1-1. `FacilityGroup` 신규
- 경로: `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityGroup.java`
- `STORAGE("저장시설")`·`OPERATION("운영시설")`·`NETWORK("계통시설")` + 한글 설명 필드
- Lombok `@Getter @RequiredArgsConstructor` 패턴 (프로젝트 enum 표준)

#### 1-2. `FacilityType` 수정
- 경로: `common/src/main/java/com/mo/swtp/facility/domain/enumtype/FacilityType.java`
- 무필드 상수 → 생성자 필드 2개 (`String description`, `FacilityGroup group`) + `@Getter @RequiredArgsConstructor`
- 기존 5종 그룹 매핑 + 신규 7종 추가. `@Enumerated(STRING)` 은 name 만 저장하므로 **DB 영향 0** (안건 4 결론).
- 그룹 매핑(확정):

| facility_type_cd | 한글 | FacilityGroup |
|------|------|------|
| PWTF | 정수조 | STORAGE |
| DWT | 배수지 | STORAGE |
| RSV | 저수지 | STORAGE |
| POINT | 관로 계측 분기점 | NETWORK |
| PRSF | 가압장 | OPERATION |
| WTBLD | 송수동 | OPERATION |
| CHMB | 약품동 | OPERATION |
| ACFB | 활성탄여과지 | OPERATION |
| POZB | 전오존동 | OPERATION |
| FLTB | 여과지동 | OPERATION |
| DEWB | 탈수기동 | OPERATION |
| SOLAR | 태양광 | OPERATION |

> 검증: STORAGE 3 / OPERATION 8 / NETWORK 1 = 12종.

### 2. 자식 엔티티 7종 (common 모듈) — JOINED skeleton

`PurifiedWaterTank`(자식 전용 컬럼 0건) 선례 동일 패턴 — `@Entity @Table(name="{suffix}_m") @DiscriminatorValue("{CODE}")` + 정적 팩토리 `create(facilityNm, parentFacilityId, dispOrd, mainYn)` + private 생성자 `super(..., YnType.Y)`.

| 엔티티 클래스 | @Table | @DiscriminatorValue |
|------|------|------|
| `WaterTransmissionBuilding` | `wtbld_m` | WTBLD |
| `ChemicalBuilding` | `chmb_m` | CHMB |
| `ActivatedCarbonFilter` | `acfb_m` | ACFB |
| `PreOzonationBuilding` | `pozb_m` | POZB |
| `FiltrationBuilding` | `fltb_m` | FLTB |
| `DewateringBuilding` | `dewb_m` | DEWB |
| `SolarPowerFacility` | `solar_m` | SOLAR |

경로: `common/src/main/java/com/mo/swtp/facility/domain/{클래스}.java`

### 3. 요청 DTO 7종 + 부모 확장 (api 모듈)

- 자식 요청 DTO 7: `{Wtbld,Chmb,Acfb,Pozb,Fltb,Dewb,Solar}UpsertDto extends FacilityUpsertDto {}` (자식 전용 필드 0 — `PwtfUpsertDto` 선례)
  - 경로: `api/src/main/java/com/mo/swtp/facility/dto/{클래스}.java`
- `FacilityUpsertDto` 수정 (`api/.../dto/FacilityUpsertDto.java`): `@Schema(oneOf)` + `@JsonSubTypes` 에 7종 추가 (기존 4 → 11). POINT 는 SCADA 자동 생성으로 요청 측 계속 제외.

### 4. 응답 DTO 7종 + 부모 확장 (api 모듈)

- 자식 응답 DTO 7: `{Wtbld,Chmb,Acfb,Pozb,Fltb,Dewb,Solar}Dto extends FacilityDto` + 정적 팩토리 `from(엔티티) { applyCommonFields(); }` (`PwtfDto` 선례)
  - 경로: `api/src/main/java/com/mo/swtp/facility/dto/{클래스}.java`
- `FacilityDto` 수정 (`api/.../dto/FacilityDto.java`):
  - `facilityGroupCd`(FacilityGroup) **파생 필드 신규** — `@Schema(implementation = FacilityGroup.class)`, `applyCommonFields()` 에서 `facility.getFacilityType().getGroup()` 으로 채움
  - `facilityTypeCd` `@Schema(description)` 12종으로 갱신
  - `@Schema(oneOf)` + `@JsonSubTypes` + `from()` switch 에 7종 추가 (기존 5 → 12)

### 5. Service (api 모듈)

`FacilityService` 수정 (`api/.../service/FacilityService.java`):
- `saveFacility` switch + private `save{Type}` 메서드 7 추가
- `updateFacility` switch + private `update{Type}` 메서드 7 추가
- 모든 신규 자식은 skeleton 이므로 `savePwtf`/`updatePwtf` 와 동일 형태 (자식 전용 필드 set 없음)

### 6. Controller (api 모듈)

`FacilityController` 수정 (`api/.../web/FacilityController.java`):
- GET `/api/facility` (목록) `@ApiResponse content oneOf` 5 → 12
- GET `/api/facility/{facilityId}` (단건) `@ApiResponse content oneOf` 5 → 12
- 등록/수정 `@Operation description` 의 자식 종류 목록 문구 갱신 (PWTF/DWT/RSV/PRSF → +7종)

### 7. ⚠️ 다형성 동기화 지점 — 총 9곳 (ANALYZE 정정)

> ANALYZE1 은 "다형성 4곳 + switch 3곳"으로 기재했으나, 실제 `FacilityController` 의 `@ApiResponse(oneOf)` 2곳(목록·단건)을 누락했다. PLAN 에서 **9곳**으로 정정한다 (안건 4 "다형성 동시 갱신 누락 방지" 강화). TASK 에서 9곳을 **개별 체크박스**로 분해한다.

| # | 위치 | 종류 | 기존 → 후 |
|---|------|------|------|
| 1 | `FacilityDto` `@JsonSubTypes` | Jackson 직렬화 | 5 → 12 |
| 2 | `FacilityDto` `@Schema(oneOf)` | Swagger | 5 → 12 |
| 3 | `FacilityDto.from()` switch | 런타임 분기 | 5 → 12 |
| 4 | `FacilityUpsertDto` `@JsonSubTypes` | Jackson 역직렬화 | 4 → 11 |
| 5 | `FacilityUpsertDto` `@Schema(oneOf)` | Swagger | 4 → 11 |
| 6 | `FacilityService.saveFacility` switch | 런타임 분기 | 4 → 11 |
| 7 | `FacilityService.updateFacility` switch | 런타임 분기 | 4 → 11 |
| 8 | `FacilityController` GET 목록 `@ApiResponse oneOf` | Swagger | 5 → 12 |
| 9 | `FacilityController` GET 단건 `@ApiResponse oneOf` | Swagger | 5 → 12 |

> 요청 측(4·5·6·7)은 POINT 제외이므로 11종, 응답 측(1·2·3·8·9)은 POINT 포함이므로 12종. 누락 시 switch default → `FacilityErrorCode.FACILITY_TYPE_MISMATCH`(런타임) 또는 SPEC 자식 스키마 누락(Swagger).

### 8. DDL (common + docs 동시 갱신 — `db/indexing-and-migration.md §5.3`)

- **신규** `common/src/main/resources/db/migration/V2_1__facility_patch.sql`:
  - 자식 테이블 7: `CREATE TABLE {suffix}_m (facility_id VARCHAR(36) NOT NULL, PRIMARY KEY (facility_id), CONSTRAINT fk_{suffix}_m__facility FOREIGN KEY (facility_id) REFERENCES facility_m (facility_id) ON DELETE RESTRICT)` — point_m/prsf_m 선례 동일 (FK **ON DELETE RESTRICT** 명시, DBA 블로커 해소)
  - 각 테이블 `COMMENT ON TABLE` + `COMMENT ON COLUMN {suffix}_m.facility_id` (훅 통과 의무)
  - `COMMENT ON COLUMN facility_m.facility_type_cd` 갱신 — 12종 나열 + "FacilityType enum SSOT 참조" 병기
- **수정** `backend/docs/ddl/facility.sql`: 동일 내용 누적 (자식 7 CREATE+COMMENT, facility_type_cd COMMENT 갱신, 헤더 "자식 5종 → 12종" 정렬)
- `V2__facility.sql` 본문은 **수정 금지** (V{N} 동결, `§5.4`). facility_m 구조 무변경.

### 9. 표준 사전 (ANALYZE 룰 갱신 지시서 — 이미 완료)
- ✅ `swtp/.claude/rules/dict/standard-words.md` `group` 등록 (ANALYZE 단계 반영 완료)
- ✅ `backend/.claude/rules/dict/standard-terms.md` `facility_type_cd` 비고 갱신 (완료)
- ROOT `standard-data-domains.md`·`domain-abbreviations.md`·`naming.md`·`entity-patterns.md` 변경 없음

### 10. 테스트
- `FacilityTypeTest`(common) — 12종 그룹 매핑 + 그룹별 카운트(STORAGE 3·OPERATION 8·NETWORK 1) 검증
- `FacilityServiceTest`/`FacilityServiceIntegrationTest`(api) — 신규 유형(예: CHMB) 등록/조회 → switch default 미진입 + `facilityGroupCd=OPERATION` 노출
- `FacilityDtoSerializationTest`(api) — 신규 7종 다형성 직렬화 + `facilityGroupCd` 파생 노출

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 |
|------|------|
| `FacilityType` 12종 전부 그룹 매핑 보유 | FacilityTypeTest 단위 테스트 GREEN — STORAGE 3·OPERATION 8·NETWORK 1 카운트 단언 |
| 신규 유형(CHMB) 등록·조회 성공 + facilityGroupCd=OPERATION | FacilityServiceTest GREEN, switch default(FACILITY_TYPE_MISMATCH) 미발생 |
| 12-way 응답 / 11-way 요청 다형성 직렬화 정상 | FacilityDtoSerializationTest GREEN (신규 7종 각 case) |
| 자식 테이블 7종 COMMENT 누락 0 | check-ddl-column-comment.sh 통과 + ./gradlew.bat build BUILD SUCCESSFUL (QClass 재생성 포함) |
| V2_1 patch ↔ docs/ddl/facility.sql 동기화 | REVIEW git diff 교차 점검 일치 (자식 7 CREATE+COMMENT, facility_type_cd COMMENT) |
| 다형성 9곳 전부 12/11종 동기화 | grep 으로 9 위치 각 case 수 확인 + 빌드 GREEN |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 멀티테넌트 배포 내 동일 facility_type_cd 의 그룹 1:1 불변 → 파생 enum 속성으로 충분 | 가정 → 결정 | 컬럼 미저장. 향후 type 무관 그룹 재분류 요건 발생 시 별도 ANALYZE 로 컬럼 materialize 재검토 |
| 신규 7종 skeleton 에 계측기·전력 태그 미연결 동안 도메인 4영역 비해당 | 가정 → 결정 | 본 사이클 4영역 비접촉. 계측기 부착 사이클 착수 시 재분류 |
| 그룹 조회는 전력집계·화면표출·목록조회만 허용, 인터록/AI운전모드/제어 평가는 `facility_type_cd` 명시 필터 강제 | 가정 → 결정 | 본 사이클 그룹 조회 코드 미도입(제외). 후속 섹션 PLAN 에서 도메인 룰 평가 서비스 제약 명기 의무 |
| 송수동·PRSF 운영시설 분류는 전력 소비 관점. 향후 AI운전모드 편입 시 별도 슬러그 재설계 | 미해결 | PRSF 는 ot-integration.md §5 별도평가 기등재. 송수동 편입 여부 후속 결정 (본 사이클 미결) |
| FacilityGroup 파생 필드명 `facilityGroupCd` (cd suffix) | 가정 → 결정 | 코드값 파생이므로 cd suffix. standard-words `group` 등록 정합 |

## 제외 사항

- 1~6번 섹션 대시보드 API
- 그룹 기준 조회 필터(`FacilitySearchDto.facilityGroupCd`)
- 신규 7종 자식 전용 컬럼 / 계측기 부착 / 전력 태그 매핑
- ADMIN 권한 분리 (기존 미적용 — 현행 유지)

## 예상 산출물
- [태스크](../../../tasks/20260608/시설_도메인_확장/TASK1.md)
