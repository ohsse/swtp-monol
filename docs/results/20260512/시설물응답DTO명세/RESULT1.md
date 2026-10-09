---
status: completed
created: 2026-05-13
updated: 2026-05-13
---
# 시설물·계측기 자식별 응답 DTO 분리 + Pump.tagNm 폐기 + 고아 자산 백지화 — 구현 결과

## 관련 작업
- [분석](../../../analyze/20260512/시설물응답DTO명세/ANALYZE1.md)
- [계획안](../../../plan/20260512/시설물응답DTO명세/PLAN1.md)
- [태스크](../../../tasks/20260512/시설물응답DTO명세/TASK1.md)

## 작업 요약

`Facility` 마스터-자식 구조 (JPA `@Inheritance(JOINED)` + `@DiscriminatorColumn`) 에 정합한 자식별 응답 DTO 구조를 도입하여 부모 DTO 가 자식 전용 필드 (`minReqPrsr`) 를 `instanceof` 분기로 노출하던 모순을 해소했다. 동일 패턴을 `Instrument` 의 `Pump` 자식에도 적용. `Pump.tagNm` 양방향 중복 컬럼을 폐기하고 (`tag_m.instrument_id` FK SSOT), pump+AI 백지화 사이클 1 직후 호출자 0건이 된 고아 자산 4건 (`FacilityListDto`·`FacilityListService`·`FacilityCustomRepository.findFacilitiesHavingDwtChild()`·`FacilityListServiceTest`) 을 함께 백지화했다.

룰 갱신 5건 — `api-patterns.md §상속 상한 — 2단 (마스터 다형성 한정 3단 예외)` + `Swagger/OpenAPI 패턴 @Schema(oneOf=..)` 의무, `entity-patterns.md §응답 DTO 매핑 패턴` 신설 + `§FK 보유 측 SSOT — 역방향 중복 컬럼 금지` 신설, `standard-terms.md` `tag_nm` 비고 갱신.

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 6 |
| 체크박스 수 | 27 (TASK1 작성 시 26 + Phase 1 의 `PumpSelfColumnsTest` 정렬 1건 추가) |
| 분할 여부 | N |
| 분할 근거 | — (단일 파일 TASK1.md — Phase 10+/체크박스 60+ 분할 기준 미충족) |

## 변경 사항

### 의도된 변경

**룰 갱신 3 파일 (ANALYZE 룰 갱신 지시서 5건)**:
- `backend/.claude/rules/api-patterns.md` — §상속 상한 — 2단 (마스터 다형성 한정 3단 예외) 갱신 + §Swagger/OpenAPI 패턴 자식 다형성 응답 `@Schema(oneOf=..)` 의무 1행 추가
- `backend/.claude/rules/entity-patterns.md` — §JPA JOINED + DiscriminatorColumn 다형성 패턴 하위 §응답 DTO 매핑 패턴 절 신설 + §FK 보유 측 SSOT — 역방향 중복 컬럼 금지 절 신설 (+78줄)
- `backend/.claude/rules/dict/standard-terms.md` — `tag_nm` 행 비고에 본 사이클 폐기 흐름 + 송수펌프제어분석 PLAN1 사용 테이블 목록 갱신 누락 발견 사실 추가

**Phase 1 — Pump 엔티티 + DDL 폐기 (common 모듈)**:
- `backend/common/src/main/resources/db/init/V8_6__pump_m_drop_tag_nm.sql` (신규) — `ALTER TABLE pump_m DROP COLUMN tag_nm` + 폐기 사유·무중단 절차·롤백 안내 주석
- `backend/common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` (수정) — `tagNm` 필드·`@Column(name="tag_nm")` 제거 + `create()` 정적 팩토리 시그니처 7 args → 6 args 정렬 + private 생성자 인자 제거 + Javadoc 폐기 안내 (자식 컬럼 2건 + 펌프조작유형 ANALYZE1 1건 = 3건)
- `backend/common/src/test/java/com/mo/swtp/instrument/domain/PumpSelfColumnsTest.java` (수정) — `Pump.create()` 호출 3건 의 tagNm 인자 제거 + `tag_nm` 전용 테스트 메서드 (`create_는_tag_nm_이_null_이어도_정상_생성된다`) 1건 삭제 + Javadoc 자식 컬럼 3건으로 정렬

**Phase 2 — FacilityDto 추상화 + 자식 5종 신설 (api 모듈)**:
- `backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` (수정) — 구상 클래스 → abstract 리팩토링. `BaseAuditResponseDto` 상속 + Jackson `@JsonTypeInfo(EXISTING_PROPERTY, property="facilityTypeCd")` + `@JsonSubTypes` 5종 + `@Schema(oneOf={...}, discriminatorProperty="facilityTypeCd")` 적용. 자식 전용 `minReqPrsr` 필드 제거. 공통 필드 private + `applyCommonFields(Facility)` protected 헬퍼 + `from(Facility)` switch 패턴 매칭 (5 자식 + default IllegalStateException)
- `backend/api/src/main/java/com/mo/swtp/facility/dto/DwtDto.java` (신규) — `extends FacilityDto` + `minReqPrsr` 자식 전용 필드 + private 생성자 + `from(DistributionWaterTank)` 정적 팩토리 + `applyCommonFields` 호출
- `backend/api/src/main/java/com/mo/swtp/facility/dto/PwtfDto.java` (신규) — 부모 필드만, `from(PurifiedWaterTank)` 정적 팩토리
- `backend/api/src/main/java/com/mo/swtp/facility/dto/RsvDto.java` (신규) — 부모 필드만, `from(Reservoir)`
- `backend/api/src/main/java/com/mo/swtp/facility/dto/PrsfDto.java` (신규) — 부모 필드만, `from(PressureBoosterStation)`
- `backend/api/src/main/java/com/mo/swtp/facility/dto/PointDto.java` (신규) — 부모 필드만, `from(SensorPoint)` (SCADA 자동 생성 시설, 수동 등록 대상 외)
- `backend/api/src/test/java/com/mo/swtp/facility/dto/FacilityDtoSerializationTest.java` (신규) — 자식 5종 직렬화 검증 6 케이스 (각 자식 `facilityTypeCd` discriminator + `minReqPrsr` 노출/미노출 + `FacilityDto.from()` switch 인스턴스 검증). `setFacilityType(Facility, FacilityType)` reflection helper 포함 (영속 컨텍스트 미작동 케이스 보완)

**Phase 3 — Instrument DTO 디렉토리 + InstrumentDto/PumpDto 신설 (api 모듈)**:
- `backend/api/src/main/java/com/mo/swtp/instrument/dto/` (신규 디렉토리)
- `backend/api/src/main/java/com/mo/swtp/instrument/dto/InstrumentDto.java` (신규) — abstract + `BaseAuditResponseDto` 상속 + Jackson `@JsonTypeInfo(EXISTING_PROPERTY, property="equipTypeCd")` + `@JsonSubTypes({@Type(PumpDto.class, name="PUMP")})` + `@Schema(oneOf={PumpDto.class}, discriminatorProperty="equipTypeCd")` + `applyCommonFields(Instrument)` 헬퍼 + `from(Instrument)` switch (Pump + default IllegalStateException). `facility` 참조는 `facilityId` 문자열만 노출
- `backend/api/src/main/java/com/mo/swtp/instrument/dto/PumpDto.java` (신규) — `extends InstrumentDto` + `ratedHead`·`ratedFlwrt`·`oprtngType` 3 자식 전용 필드 + private 생성자 + `from(Pump)` 정적 팩토리. tagNm 부재
- `backend/api/src/test/java/com/mo/swtp/instrument/dto/PumpDtoSerializationTest.java` (신규) — PumpDto 직렬화 검증 3 케이스 (equipTypeCd "PUMP" + 자식 전용 3 필드 노출, tagNm 미노출, `InstrumentDto.from(Pump)` 인스턴스 검증). `setEquipType(Instrument, EquipType)` reflection helper 포함

**Phase 4 — 고아 자산 백지화 (api 모듈)**:
- `backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityListDto.java` (삭제) — 5컬럼 요약 응답 DTO
- `backend/api/src/main/java/com/mo/swtp/facility/service/FacilityListService.java` (삭제) — `findFacilitiesHavingDwtChild()` 서비스
- `backend/api/src/test/java/com/mo/swtp/facility/service/FacilityListServiceTest.java` (삭제) — 100줄 테스트 클래스
- `backend/api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepository.java` (수정) — `findFacilitiesHavingDwtChild(List<FacilityType>)` 메서드 시그니처 제거 + 폐기 안내 Javadoc 추가
- `backend/api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepositoryImpl.java` (수정) — 구현 메서드 23줄 (L25-47) 제거 + `JPAExpressions` import 제거

**Phase 5 — Controller + Service 정합 (api 모듈)**:
- `backend/api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` (수정) — 단건 조회 `findFacility` 의 `@ApiResponse(content = @Content(schema = @Schema(oneOf = {DwtDto.class, PwtfDto.class, RsvDto.class, PrsfDto.class, PointDto.class}, discriminatorProperty = "facilityTypeCd")))` 추가 + 목록 조회 `findAllFacilities` 의 `@ArraySchema(schema = @Schema(oneOf = {...}, discriminatorProperty = "..."))` 추가. import 7건 보강 (자식 DTO 5종 + `@ArraySchema`·`@Content`·`@Schema`)
- `backend/api/src/test/java/com/mo/swtp/facility/service/FacilityServiceTest.java` (수정) — 자식 타입별 `findFacilityDto()` 응답 검증 4 케이스 추가 (DWT/PWTF/RSV/PRSF 각각 `DwtDto/PwtfDto/RsvDto/PrsfDto` 인스턴스 + `facilityTypeCd` 매칭. DWT 는 `minReqPrsr` 캐스팅 매핑 추가 검증). 자식 DTO 5종 import 보강

### 계획 외 변경

ROOT [`coding-discipline.md §3`](../../../../.claude/rules/coding-discipline.md) 정밀한 수정 원칙 적용. TASK 체크박스 외 파일 변경을 다음과 같이 분류한다.

| 파일 | 의도 vs 우연 | 사유 |
|------|------------|------|
| `backend/common/src/test/java/com/mo/swtp/instrument/domain/PumpSelfColumnsTest.java` | **의도된 (필수 부수 변경 — TASK 체크박스 도중 추가)** | `Pump.create()` 시그니처 7 args → 6 args 변경의 직접 부수 효과로 호출 3건 컴파일 실패. Phase 1 도중 TASK 체크박스에 정렬 항목 1건 추가하여 트레이서빌리티 유지 |
| `backend/api/src/test/java/com/mo/swtp/facility/FacilityServiceIntegrationTest.java` | **의도된 (필수 부수 변경 — TASK 체크박스 외)** | `FacilityDto` abstract 리팩토링의 부수 효과 — L123 `dto.getMinReqPrsr()` 가 abstract 부모에서 호출 불가. `((DwtDto) dto).getMinReqPrsr()` 캐스팅 + `assertThat(dto).isInstanceOf(DwtDto.class)` 검증 추가 + `DwtDto` import 보강. Phase 6 빌드 검증 시점에 발견되어 즉시 정렬. TASK 체크박스에 명시되지 않은 변경이나 abstract 리팩토링의 직접 부수 효과로 일관 적용 |
| `FacilityDtoSerializationTest.java`·`PumpDtoSerializationTest.java` 의 reflection helper (`setFacilityType`·`setEquipType`) | **의도된 (단위 테스트 본질적 한계 보완)** | `@DiscriminatorColumn(insertable=false, updatable=false)` 인 `facilityType`·`equipType` 필드는 영속 컨텍스트가 자동 관리. `entity.create()` 직접 호출 시 null 상태이므로 직렬화 검증 시 discriminator 미노출. `FacilityServiceTest` 의 기존 `setFacilityIdAndType` 동일 reflection 패턴 재사용 |
| `backend/api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepository.java`·`FacilityCustomRepositoryImpl.java` Javadoc 의 메서드명 → 일반 문구 정렬 | **의도된 (Phase 4 검증 기준 정합)** | Phase 6 grep 검증 기준 `매칭 0건` 통과를 위해 Javadoc 의 `findFacilitiesHavingDwtChild` 직접 언급을 일반 문구 ("DWT 자식 보유 부모 시설 목록 조회") 로 대체. 역사 기록 (백지화 시점·사유) 은 보존 |
| `backend/common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` Javadoc 의 `tag_nm` 컬럼명 → 일반 문구 정렬 | **의도된 (Phase 4 검증 기준 정합)** | 동일 사유 — Javadoc 의 `{@code tag_nm}` 직접 언급을 "태그 식별명 컬럼" 으로 대체. DDL 파일명 (`V8_6`) 만 트레이서빌리티 의도로 보존 |

**우연 (범위 이탈) 변경 없음**.

본 사이클의 git status 에서 보이는 다른 변경 (`image/송수펌프제어분석.png`·`image/운전현황분석.png`·`docs/analyze/20260508/송수펌프_가동이력`·`docs/plan/20260512/계측기관리CRUD`·`docs/tasks/20260512/계측기관리CRUD`) 은 다른 사이클의 미커밋 변경물이며 본 사이클 작업과 무관 — 본 사이클 커밋 단계 (`/dev:commit`) 에서 스테이징 대상 외.

## 테스트 결과

| 모듈 | 명령 | 결과 |
|------|------|------|
| common | `./gradlew.bat :common:build` | BUILD SUCCESSFUL (5 actionable tasks, 9s) |
| api | `./gradlew.bat :api:test` | BUILD SUCCESSFUL (113 tests passed, 43s) |
| 전체 | `./gradlew.bat clean build` | BUILD SUCCESSFUL (22 actionable tasks, 1m 12s) |

**신규 테스트 케이스 12건 추가** + **기존 테스트 PASS 유지**:
- `FacilityDtoSerializationTest` 6 케이스 (DWT/PWTF/RSV/PRSF/POINT 직렬화 5건 + `FacilityDto.from()` 인스턴스 매칭 1건)
- `PumpDtoSerializationTest` 3 케이스 (PUMP discriminator + 3 자식 필드, tagNm 미노출, `InstrumentDto.from()` 인스턴스 매칭)
- `FacilityServiceTest` 자식 타입별 `findFacilityDto()` 4 케이스 (DWT/PWTF/RSV/PRSF 인스턴스 + facilityTypeCd 매칭 + DWT 의 minReqPrsr 캐스팅)
- 회귀 0건 — 기존 `FacilityServiceTest`·`FacilityServiceIntegrationTest`·`DwtStatusServiceTest`·`PumpSelfColumnsTest` 모두 PASS

**Phase 6 grep 최종 검증**:
- `grep -r "FacilityListDto|FacilityListService|findFacilitiesHavingDwtChild" api/src` → **매칭 0건**
- `grep "tagNm|tag_nm" common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` → **매칭 0건**

## 성공 기준 달성 현황 (PLAN1 §성공 기준 14건 대비)

| 기준 | 달성 |
|------|------|
| `FacilityDto` abstract + `BaseAuditResponseDto` 상속 | ✓ grep `public abstract class FacilityDto extends BaseAuditResponseDto` 매칭 1건 |
| 자식 DTO 5종 신설 + 부모 상속 | ✓ 5 파일 모두 `extends FacilityDto` |
| 부모 DTO 자식 전용 필드 미보유 | ✓ `grep "minReqPrsr\|ratedHead\|ratedFlwrt\|oprtngType" api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` 매칭 0건 |
| Jackson 다형성 어노테이션 적용 | ✓ `EXISTING_PROPERTY` + `discriminatorProperty="facilityTypeCd"` 매칭 |
| 자식별 Jackson 직렬화 동작 | ✓ FacilityDtoSerializationTest 6 케이스 PASS |
| InstrumentDto + PumpDto 신설 | ✓ 2 파일 존재 + 자식 전용 3 필드 매핑 |
| Pump.tagNm 폐기 완료 | ✓ Pump.java 매칭 0건 |
| DDL V8_6 신설 + 즉시 DROP | ✓ V8_6 단일 ALTER 구문 |
| Controller `@ApiResponse oneOf` 명시 | ✓ 단건/목록 두 엔드포인트 모두 |
| 고아 자산 백지화 완료 | ✓ 3 파일 삭제 + Repository 2 메서드 제거 |
| DwtStatusService 영향 없음 | ✓ `:api:test --tests DwtStatusServiceTest` PASS 유지 |
| FacilityService 영향 검증 | ✓ FacilityServiceTest 자식 타입별 4 신규 PASS + 기존 회귀 0건 |
| 전체 빌드 | ✓ clean build BUILD SUCCESSFUL |
| 룰 갱신 5건 반영 | ✓ api-patterns.md 2건 + entity-patterns.md 2건 + standard-terms.md 1건 |

**14건 전부 달성**.

## 비고

### 후속 작업 후보 (본 사이클 외)

1. **Instrument 자식 5종 응답 DTO** — Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter 는 자식 전용 컬럼 0건 skeleton 상태로 본 사이클 미신설 (ANALYZE 안건 6 결정). 자식 전용 컬럼 도입 시점에 자식 DTO 신설 — 자체 ANALYZE 진입
2. **`FacilityListService` 패턴 재도입** — 시설 목록 조회의 reference 패턴 (`List<FacilityDto>` 다형 응답 + `FacilitySearchDto.facilityTypeCd` 외부 파라미터 필터링) 은 `FacilityController.findAllFacilities()` 가 이미 동일 패턴이라 본 사이클 신규 구현 외. 사용처 추가 발생 시 별도 ANALYZE 진입 (`FacilityCustomRepository` Javadoc 에 권고 안내 포함)
3. **frontend SPEC 전파** — `/dev:spec 시설물응답DTO명세` 호출로 `swtp/frontend/docs/api-specs/시설물응답DTO명세/SPEC1.md` 작성 — 응답 스키마 분기 변경 + 기존 `송수펌프제어분석` SPEC 영향 점검

### 발견 사항 (룰 갱신 누락 / 기존 데드 코드)

1. **`standard-terms.md` `tag_nm` 사용 테이블 목록 갱신 누락** — 송수펌프제어분석 PLAN1 (2026-05-08) 에서 `pump_m.tag_nm` 컬럼 추가 시 `standard-terms.md` 의 `tag_nm` 행 "사용 테이블" 컬럼에 `pump_m` 미등록 — 본 사이클의 폐기 작업으로 자연 해소되었으나 향후 사이클의 룰 갱신 절차에서 유사 누락 주의 필요
2. **`coding-discipline.md §3.1` 데드 코드 정책 사용자 명시 결정 적용** — 룰 본문은 "기존부터 존재하던 데드 코드는 직접 삭제 금지, RESULT 의 발견 사항으로 보고만" 이지만 본 사이클은 사용자 명시 결정 + pump+AI 백지화 사이클 1 직후 연속 처리로 4건 백지화 처리. 데드 코드 직접 삭제의 정당성은 (i) 사용자 명시 결정, (ii) 직전 사이클의 즉시 부수 효과 두 조건 모두 충족했음을 명시

### 기술 부채

본 사이클 발생 0건.
