---
status: approved
created: 2026-05-11
updated: 2026-05-11
---
# 시설물관리기능 CRUD API 신설

## 목적

운영자가 시설물 관리 화면(타입별 탭 4종 — PWTF/DWT/RSV/PRSF)에서 시설을 **등록·수정·논리 삭제·조회**할 수 있도록 backend CRUD API 를 신설한다. frontend 시설물관리 페이지의 데이터 소스 역할.

## 배경

- 시설 단일 마스터 도메인(`Facility` 부모 + 자식 5종 JPA JOINED 다형성) 의 **엔티티·DDL** 은 마스터도메인설계(2026-05-03 approved) 및 송수펌프제어분석(2026-05-08) 사이클에서 도입 완료. 코드 SSOT: `common/src/main/java/com/mo/swtp/facility/domain/`, DDL SSOT: `common/src/main/resources/db/init/V6_1*.sql` · `V8_1*.sql`.
- **CRUD API 만 미구현** — 본 사이클은 `api/` 모듈에 신설.
- ANALYZE1 Round 2 결정(2026-05-11): reference 패턴(`backend/reference/api/.../DatasetController.java`) 정합으로 **단일 Controller + 단일 Service + JSON 다형성**(`@JsonTypeInfo` + `@JsonSubTypes` + `@Schema(oneOf=..., discriminatorProperty)`) 채택. Round 1 의 자식별 컨트롤러 분리(클래스 ~15개) → Round 2 단일 구조(클래스 ~7개) 전환.

## 범위

### 포함
- `api/src/main/java/com/mo/swtp/facility/` 패키지 신설 (Controller·Service·Repository·DTO·ErrorCode 7 클래스)
- 5 엔드포인트: `GET /api/facility` (목록), `GET /api/facility/{id}` (단건), `POST /api/facility`, `PUT /api/facility/{id}`, `DELETE /api/facility/{id}`
- 추상 부모 `FacilityUpsertDto` + 자식 4종(`PwtfUpsertDto`·`DwtUpsertDto`·`RsvUpsertDto`·`PrsfUpsertDto`) — Jackson 다형성 명시 어노테이션 의무
- 공통 응답 `FacilityDto` (nullable `minReqPrsr` 필드 — DWT 만 값 보유)
- `FacilityErrorCode` enum (4 항목 — `FACILITY_NOT_FOUND` 외)
- `FacilityService` 디스패처 패턴 — `saveFacility(dto)` / `updateFacility(id, dto)` 가 `instanceof` 로 자식별 private 메서드 호출
- 검증 흐름: `parentFacilityId` 존재·자기참조 차단, `facilityNm` UNIQUE 사전 검사, PUT 시 `facility_type_cd` 일치 검증
- 단위 테스트 (자식 4종 × 등록·수정·논리삭제 + 검증 실패 3종 = 15+건) + 통합 테스트 (목록 필터·논리삭제 후 조회 제외)

### 비범위
- 엔티티 / DDL / 시퀀스 / 인덱스 변경 — `common/` 정착 코드 일체 수정 금지
- POINT(`SensorPoint`) 자식 CRUD — SCADA 도메인 자동 생성 대상, 수동 UI 비대상
- 페이지네이션 — 전체 목록 정렬 반환 (`user_m`·`tag_m` 선례)
- 트리뷰 / 부분일치 검색 / 다중 필터 — 본 사이클 type·useYn 필터만
- 물리 삭제 — 논리 삭제(`use_yn='N'`) 만
- `(facility_type_cd, use_yn)` 복합 인덱스 — 차기 사이클 검토
- `DataIntegrityViolationException → DUPLICATE_FACILITY_NM` GlobalExceptionHandler 매핑 — 본 사이클 비범위
- 비활성 시설의 `ai_drvn_mod_p` 강제 전환 평가 처리 — 차기 사이클 (도메인 4영역 점검 §AI 운전 모드 직결)
- frontend SPEC 전파(`/dev:spec`) — `/dev:commit` 후 사용자 명시 호출 시 별도 진행

## 구현 방향

### 패키지 구조 (api 모듈 신설)
```
api/src/main/java/com/mo/swtp/facility/
├── controller/
│   └── FacilityController.java          (단일 — 5 엔드포인트, Tag = "06. 시설물 관리")
├── service/
│   └── FacilityService.java             (단일 — 디스패처 + 자식별 private 메서드 4종)
├── repository/
│   ├── FacilityRepository.java          (JpaRepository<Facility,String> + FacilityCustomRepository)
│   ├── FacilityCustomRepository.java    (인터페이스 shell — 차기 Querydsl 확장 여지)
│   └── FacilityCustomRepositoryImpl.java(현재 메서드 0건 shell)
├── dto/
│   ├── FacilityDto.java                 (공통 응답)
│   ├── FacilityUpsertDto.java           (추상 부모 + Jackson 다형성 어노테이션)
│   ├── PwtfUpsertDto.java
│   ├── DwtUpsertDto.java                (minReqPrsr @NotNull)
│   ├── RsvUpsertDto.java
│   └── PrsfUpsertDto.java
└── exception/
    └── FacilityErrorCode.java
```

### 다형성 DTO 어노테이션 (필수)

`FacilityUpsertDto` 추상 부모는 다음 어노테이션 3종을 **모두** 명시:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "facilityTypeCd")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PwtfUpsertDto.class, name = "PWTF"),
    @JsonSubTypes.Type(value = DwtUpsertDto.class,  name = "DWT"),
    @JsonSubTypes.Type(value = RsvUpsertDto.class,  name = "RSV"),
    @JsonSubTypes.Type(value = PrsfUpsertDto.class, name = "PRSF")
})
@Schema(
    description = "시설 등록·수정 (자식 타입 다형성)",
    oneOf = { PwtfUpsertDto.class, DwtUpsertDto.class, RsvUpsertDto.class, PrsfUpsertDto.class },
    discriminatorProperty = "facilityTypeCd"
)
public abstract class FacilityUpsertDto { ... }
```

> `@Schema(oneOf=..., discriminatorProperty=...)` 누락 시 SpringDoc 자동 추출이 불완전 → `/dev:spec` frontend SPEC 정확성 위협 + `api-patterns.md §DTO @Schema(implementation) 명시 패턴` 위반. TASK 체크박스 검증 의무.

### Service 디스패처 패턴

`FacilityService` 의 `saveFacility` / `updateFacility` 는 공통 검증 후 `instanceof` 로 자식별 private 메서드 호출:

```java
@Transactional
public void saveFacility(FacilityUpsertDto dto) {
    validateParentFacility(dto.getParentFacilityId(), null);
    validateDuplicateFacilityNm(dto.getFacilityNm());
    if (dto instanceof PwtfUpsertDto pwtf)      savePwtf(pwtf);
    else if (dto instanceof DwtUpsertDto dwt)   saveDwt(dwt);
    else if (dto instanceof RsvUpsertDto rsv)   saveRsv(rsv);
    else if (dto instanceof PrsfUpsertDto prsf) savePrsf(prsf);
}

@Transactional
public void updateFacility(String facilityId, FacilityUpsertDto dto) {
    Facility found = facilityRepository.findById(facilityId)
        .orElseThrow(() -> new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND));
    validateTypeMatch(found, dto);
    validateParentFacility(dto.getParentFacilityId(), facilityId);
    validateDuplicateFacilityNmOnUpdate(dto.getFacilityNm(), facilityId);
    if (dto instanceof PwtfUpsertDto pwtf)      updatePwtf((PurifiedWaterTank) found, pwtf);
    else if (dto instanceof DwtUpsertDto dwt)   updateDwt((DistributionWaterTank) found, dwt);
    else if (dto instanceof RsvUpsertDto rsv)   updateRsv((Reservoir) found, rsv);
    else if (dto instanceof PrsfUpsertDto prsf) updatePrsf((PressureBoosterStation) found, prsf);
}
```

추상화 계층: Controller → Service → Repository = 3단 유지(§2.1 정량 기준 적합). 같은 Service Bean 내 private 메서드 호출은 추상화 계층 산정 외(ANALYZE Round 2 (C) 결론).

### 검증 흐름

| 단계 | 검증 | 실패 시 |
|-----|------|-------|
| 1 | `parentFacilityId` NULL 아니면 (a) `facilityRepository.existsById(...)` (b) PUT 시 `facilityId != parentFacilityId` (자기참조 차단) | `INVALID_PARENT_FACILITY_ID` (400) |
| 2 | 등록: `existsByFacilityNm(nm)`, 수정: `existsByFacilityNmAndFacilityIdNot(nm, currentId)` | `DUPLICATE_FACILITY_NM` (409) |
| 3 | PUT 시 `found.getFacilityType().name() == dto.getDiscriminator()` 검증 | `FACILITY_TYPE_MISMATCH` (400) |
| 4 | (수정·삭제 공통) `findById` 결과 부재 | `FACILITY_NOT_FOUND` (404) |

### ErrorCode (신설)

| name | httpStatus |
|------|-----------|
| `FACILITY_NOT_FOUND` | 404 |
| `DUPLICATE_FACILITY_NM` | 409 |
| `FACILITY_TYPE_MISMATCH` | 400 |
| `INVALID_PARENT_FACILITY_ID` | 400 |

본 enum 은 `exception-patterns.md §2 ErrorCode 구현 enum 필드 규약` 준수 — `httpStatus(int)` 단일 필드. `String message` 금지 (자동 차단 훅 `check-errorcode-contract.sh` 적용).

### 영속 경로

| 작업 | 메서드 |
|------|------|
| 등록 | 자식 정적 팩토리(`PurifiedWaterTank.create(...)` / `DistributionWaterTank.create(..., minReqPrsr)` 등) → `facilityRepository.save(...)` |
| 수정(공통 필드 — facilityNm·parentFacilityId·dispOrd·mainYn) | `Facility.changeInfo(...)` JPA dirty checking |
| 수정(DWT 자식 전용 — `minReqPrsr`) | `DistributionWaterTank` 자식 변경 메서드 신설 (예: `changeMinReqPrsr(BigDecimal)`) — common 모듈 1메서드 추가는 PLAN 단계 사용자 사전 승인 필요 ⚠️ |
| 논리 삭제 | `Facility.deactivate()` → `use_yn='N'` 전환 |

> **⚠️ common 모듈 변경 사전 승인 항목**: DWT `minReqPrsr` 변경 메서드 신설 1건. `swtp/CLAUDE.md` "## ⚠️ 최상위 경고 — backend 경로 수정 금지" 는 ROOT CLAUDE.md 정책으로 backend 변경 시 사용자 승인 의무 — 단, 본 사이클은 사용자가 `/dev` 워크플로우를 명시 호출하여 backend 작업 자체를 승인한 상태이며, 이 1건의 common 메서드 추가는 작업 범위 내 필수 부수 변경에 해당. TASK 체크박스로 명시.

### 조회 정렬

`findAllFacilities(type, useYn)`: Spring Data JPA `Sort` 또는 `@Query` 활용:
- 정렬 기준: `useYn DESC, dispOrd ASC, facilityNm ASC`
- `type` NULL → 자식 종류 무관 전체. `useYn` NULL → Y/N 모두

## 성공 기준 (검증 가능 형태)

> ROOT `coding-discipline.md §4.2` 적용. 모호 목표 금지.

| 기준 | 검증 명령 / 테스트 |
|------|----------------|
| `common` + `api` 모듈 빌드 | `./gradlew.bat :common:build :api:build` BUILD SUCCESSFUL 출력 |
| 단위 테스트 통과 — 자식 4종 × 등록·수정·논리삭제 시나리오 | `./gradlew.bat :api:test --tests "com.mo.swtp.facility.service.FacilityServiceTest"` GREEN ≥ 12건 |
| 검증 실패 단위 테스트 — `DUPLICATE_FACILITY_NM` / `FACILITY_NOT_FOUND` / `INVALID_PARENT_FACILITY_ID` / `FACILITY_TYPE_MISMATCH` | `./gradlew.bat :api:test --tests "*FacilityServiceTest*"` GREEN ≥ 4건 (각 ErrorCode 1건) |
| 통합 테스트 — PWTF 1건 + DWT 1건 등록 후 `GET /api/facility?type=PWTF` 반환에 PWTF 만 포함 | `./gradlew.bat :api:test --tests "*FacilityServiceIntegrationTest*"` GREEN (로컬 PostgreSQL 전제 — `application-test.yml`) |
| 통합 테스트 — `DELETE /api/facility/{pwtfId}` 후 `GET /api/facility?type=PWTF&useYn=Y` 응답에서 해당 시설 제외 | 동일 통합 테스트 클래스 내 1건 GREEN |
| `POST /api/facility` body `{"facilityTypeCd":"DWT", "facilityNm":"...", ...}` 의 `minReqPrsr` 누락 시 400 응답 | MockMvc 통합 테스트 또는 수동 curl 검증 — 400 응답 + `code` 필드에 `INVALID_REQUEST` 또는 검증 오류 |
| Swagger UI `oneOf` 다형성 노출 | `./gradlew.bat :api:bootRun` 후 `http://localhost:8080/swagger-ui/index.html` 의 `/api/facility` POST request body 가 `oneOf` 4종(PWTF/DWT/RSV/PRSF) 표시 + 디스크리미네이터 `facilityTypeCd` 표시 |
| `check-errorcode-contract.sh` 훅 통과 | `FacilityErrorCode.java` Write 시 차단 없음 (httpStatus 외 String 필드 부재) |

## 가정 및 미해결 질문

> ROOT `coding-discipline.md §1` 적용. ANALYZE 의 가정·미해결 질문을 PLAN 단계 결정으로 변환.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 비활성 시설(`use_yn='N'`)의 `ai_drvn_mod_p` 강제 전환 평가 처리 (안건 3 블로커) | 미해결 → **차기 사이클 위임** | 본 사이클은 스케줄러 변경 없음. 본 PLAN §비범위 명시. 차기 사이클에서 `ot-integration.md §5` 본문에 비활성 시설 처리 정책 절 추가 + 스케줄러 강제 전환 쿼리에 `facility_m.use_yn='Y'` 필터 추가 별도 ANALYZE |
| `parentFacilityId` 타입 혼합 부모 허용 — PWTF 가 DWT 부모를 가지는 구성을 DB·애플리케이션에서 차단하지 않음 (안건 2 결정) | 가정 → **수용** | 자기참조만 차단. 다른 타입 부모 허용. 향후 `pump_interlock_p.facility_type_cd` 신설 시 재검토 |
| `DataIntegrityViolationException → DUPLICATE_FACILITY_NM` 변환 핸들러 부재 (안건 6 권고) | 가정 → **사전 `existsBy` 단일 방어선 채택** | race condition 발생 시 500 응답 가능성 존재. 운영자 single-user POST 동시성 발생 확률 매우 낮음. 차기 사이클 또는 다른 도메인과 함께 GlobalExceptionHandler 도입 시 매핑 일괄 추가 |
| `(facility_type_cd, use_yn)` 복합 인덱스 (안건 6 권고) | 가정 → **현 단일 `facility_type_cd` 인덱스로 충분** | 시설 수 수천 건 규모 도달 시 차기 사이클 도입. `db/indexing-and-migration.md §1` 복합 인덱스 컬럼 순서 규칙 차기 사이클 적용 |
| 활성 시설만 노출하는 다른 도메인 API 의 `use_yn='Y'` 필터 의무 | 가정 → **본 사이클 직접 영향 없음** | 향후 시설 트리 조회·펌프 대시보드 등 구현 시 가이드라인 적용 |
| `@Schema(oneOf=..., discriminatorProperty="facilityTypeCd")` 명시 의무 (Backend Engineer Round 2 권고) | **결정** | TASK 체크박스 1개 항목으로 명시 검증. Swagger UI 수동 확인까지 성공 기준에 포함 |
| 자식 DTO 중첩 객체 도입 시 `@Valid` 전파 의무 (Backend Engineer Round 2 (A) 결론) | 가정 → **본 사이클 직접 영향 없음** | 본 사이클 자식 DTO 의 중첩 객체 없음 (모두 primitive·String·BigDecimal·YnType enum). 차기 자식 DTO 확장 시 점검 |
| common 모듈 `DistributionWaterTank` 자식 변경 메서드 신설 1건 | 가정 → **승인 요청 항목** | `/dev:plan` 사용자 승인 시 작업 범위로 확정. TASK 첫 Phase 에 명시. 본 사이클 외 common 변경 0건 유지 |
| `FacilityService` 가 `Facility.changeInfo(...)` 시그니처에 의존 — 기존 메서드 사용 가능 여부 | 가정 → **재사용** | `common/.../Facility.java:126` 의 `changeInfo` 가 `facilityNm`·`parentFacilityId`·`dispOrd`·`mainYn` 변경을 모두 지원하는지 TASK Phase 1 에서 검증. 미지원 컬럼 발견 시 PLAN2 또는 별도 TASK 분할 |

## 제외 사항

위 §범위 의 "비범위" 절 참조. 핵심 7건:
1. 엔티티/DDL 변경
2. POINT 자식 CRUD
3. 페이지네이션·트리뷰·부분일치 검색
4. 물리 삭제
5. 복합 인덱스 추가
6. GlobalExceptionHandler `DataIntegrityViolationException` 매핑
7. 비활성 시설 AI 운전 모드 평가 처리

## 도메인 4영역 점검 (PLAN 단계 재확인)

| 영역 | 해당/비해당 | PLAN 단계 영향 |
|------|----------|------------|
| 알람 4단계 | 비해당 | 영향 없음 |
| 인터록 선행조건 | 비해당 | `pump_interlock_p` 는 instrument 단위, 시설 단위 룰 없음 |
| AI 운전 모드 | 해당 (간접) | 논리 삭제 후 강제 전환 평가 영향 = 차기 사이클 위임 (위 §가정 미해결 #1) |
| 이력 기록 의무 | 비해당 | BaseEntity 4컬럼 audit 으로 충분, `facility_h` 불필요 |

## 예상 산출물

- [태스크](../../../tasks/20260511/시설물관리기능/TASK1.md) (작성 예정)
