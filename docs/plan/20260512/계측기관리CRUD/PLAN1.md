---
status: approved
created: 2026-05-12
updated: 2026-05-12
---
# 계측기 관리 CRUD 구현 계획

## 목적

`instrument_m` 단일 마스터 + JPA JOINED 자식 6종 (PUMP·VALVE·FLWMTR·PRSMTR·LVMTR·ELCMTR) 의 CRUD REST API 엔드포인트를 신규 작성한다. Facility 도메인의 검증된 "JPA JOINED + Jackson 다형성 DTO + Java 21 switch 패턴 매칭 Service" 구조를 충실히 재현하여, 시설 내 계측기 등록·조회·수정·논리 삭제 운영 화면이 본 API 를 통해 작동하도록 한다.

## 배경

instrument 도메인의 구조·DB 스키마·엔티티·Repository 골격은 다음 산출물에서 이미 결정·구현되어 있다. 본 작업은 그 위에 Service/Controller/DTO 계층을 추가하는 단일 도메인 기능 확장이다.

### 선행 결정 (재사용)

| 산출물 | 위치 | 확정 사항 |
|--------|------|---------|
| 마스터도메인설계 ANALYZE1 (Round 2/3) | `docs/analyze/20260502/마스터도메인설계/ANALYZE1.md` (status: approved) | 단일 마스터 + JPA JOINED + 자식 6종 + UUID 자동 생성 PK + `(facility_id, instrument_nm)` 복합 UNIQUE |
| 마스터도메인설계 PLAN1 | `docs/plan/20260503/마스터도메인설계/PLAN1.md` (status: approved) | 도메인 모델·DB 설계 결정 |
| 펌프조작유형 ANALYZE1 (2026-05-12) | `docs/analyze/20260512/펌프조작유형/ANALYZE1.md` | `Pump.oprtng_type_cd` 자체 컬럼 (PumpOprtngType enum 매핑, NOT NULL) |
| 표준 용어 사전 | `.claude/rules/dict/standard-terms.md` | `instrument_id`·`instrument_nm`·`equip_type_cd`·`facility_id` 정식 등록 완료 |

### 이미 구현된 자산 (본 작업의 시작점)

| 자산 | 위치 |
|------|------|
| DB DDL — `instrument_m` 부모 + 자식 6종 테이블 | `common/src/main/resources/db/init/V6_2__instrument_master_tables.sql` |
| DB DDL — Pump 자식 자체 컬럼 4건 | `V8_2__pump_m_self_columns.sql` |
| 엔티티 7종 — `Instrument` 추상 부모 + 자식 6종 | `common/src/main/java/com/mo/swtp/instrument/domain/` |
| Enum — `EquipType`, `PumpOprtngType` | 동일 |
| Repository 3계층 — `InstrumentRepository`·`InstrumentCustomRepository`·`InstrumentCustomRepositoryImpl` | `api/src/main/java/com/mo/swtp/instrument/repository/` |

### Reference 자산 (패턴 충실 재현)

같은 "JPA JOINED + Jackson 다형성 DTO" 구조로 완전 구현된 facility 도메인 (`api/.../facility/`, 2026-05-11 시설물관리기능 PLAN1 approved) 의 패턴을 그대로 재현한다:

- Controller 5엔드포인트 구조 + Swagger 어노테이션
- `FacilityUpsertDto` 추상 부모 + `@JsonTypeInfo` + `@JsonSubTypes` + `@Schema(oneOf=..., discriminatorProperty=...)`
- Service 의 Java 21 switch 패턴 매칭 분기
- 검증 메서드 3종 (`validateParentFacility`·`validateDuplicateFacilityNm`·`validateTypeMatch`)
- ErrorCode enum (httpStatus int 단일 필드)

### CLAUDE.md 패키지 도입 현황 정합

`backend/CLAUDE.md` §패키지 규칙 (2026-05-12 갱신) 의 "현 도입 완료" 항목에 `com.mo.swtp.instrument` 가 명시되어 있다. pump+AI 백지화 사이클 1 (2026-05-12) 로 `com.mo.swtp.pump.*` 비즈니스 패키지는 백지화 완료된 상태이며, **`instrument.domain.Pump` 자식 엔티티 + `PumpOprtngType` enum 은 보존된다**. 본 작업은 보존된 자산을 활용하는 CRUD 확장이며 pump+AI 재설계 사이클 2 와 독립적이다.

## 범위

### 포함

- **신규 작성** (Common 모듈 — DTO 7종, api 모듈 — DTO 2종 + Controller·Service·ErrorCode 4종)
- **변경**: `Pump.java` 자체 컬럼 변경 메서드 1건 추가, `InstrumentCustomRepository`/`Impl` 검색 메서드 1건 추가
- **테스트**: `InstrumentServiceTest` 통합 테스트 1종

### 제외

- ADMIN 권한 분리 (현재 인증 사용자 전체 허용 — 별도 사이클)
- 계측기 계층 구조 (instrument 의 self-FK 없음 — facility 가 self-FK 보유)
- 자식 5종 (PUMP 외) 의 자체 컬럼 도입 (skeleton 유지 — 운영 요구 발생 시 별도 사이클)
- 계측기 마스터 변경 이력 테이블 `instrument_h` — BaseEntity audit 4컬럼으로 충분 (별도 _h 미요구)
- 페이지네이션 — 마스터 테이블 행 수 < 1000 가정, 폭증 시 별도 사이클
- frontend SPEC 작성은 `/dev:commit` 후 `/dev:spec` 단계로 분리

## 구현 방향

### Phase 구성 (TASK 단계 분해 예고)

| Phase | 범위 | 검증 |
|-------|------|------|
| Phase 1 | common 모듈 DTO 7종 (`InstrumentUpsertDto` 추상 부모 + 자식 6종) | `./gradlew.bat :common:build` PASS |
| Phase 2 | api 모듈 응답 DTO·검색 DTO·ErrorCode | 컴파일 확인 |
| Phase 3 | `Pump.java` 자체 컬럼 변경 메서드 + InstrumentCustomRepository 검색 메서드 보강 | `./gradlew.bat :common:build`·`:api:build` PASS |
| Phase 4 | api 모듈 `InstrumentService` (Java 21 switch 디스패치 + 검증 메서드 3종) | `./gradlew.bat :api:build` PASS |
| Phase 5 | api 모듈 `InstrumentController` (5 엔드포인트, Swagger) | `./gradlew.bat :api:build` PASS |
| Phase 6 | `InstrumentServiceTest` 통합 테스트 (7건 시나리오) | `./gradlew.bat :api:test --tests "*InstrumentServiceTest*"` PASS |
| Phase 7 | 전체 빌드 검증 | `./gradlew.bat clean build` PASS |

### 핵심 설계 결정

1. **자식 종류 변경 금지** — PUT 시 `path.instrumentId` 조회 결과 `equipType` 과 `dto.equipTypeCd` 불일치 → `EQUIP_TYPE_MISMATCH(400)`. Facility 의 FACILITY_TYPE_MISMATCH 동일 패턴
2. **시설 FK 사전 검증** — 등록 시 `facilityRepository.existsById(dto.getFacilityId())` false → `INVALID_FACILITY_ID(400)`. DB FK 위반 (500) 회피. TagService 의 협력자 검증 선례
3. **복합 UNIQUE 사전 검증** — `existsByFacilityFacilityIdAndInstrumentNm(facilityId, instrumentNm)` 호출. 수정 시 자기 자신 제외 메서드 (`existsByFacilityFacilityIdAndInstrumentNmAndInstrumentIdNot`) 신규 작성
4. **응답 DTO 자식 종류별 컬럼 노출** — `InstrumentDto` 단일 응답 + `instanceof Pump pump` 분기로 Pump 자체 컬럼 4건만 채움. 자식 5종 (Valve 등) 은 부모 공통 컬럼만 노출. FacilityDto + DwtUpsertDto.minReqPrsr 분기 선례
5. **물리 삭제 미제공** — `tag_m.instrument_id` FK + 향후 `rawdata_1m_h.tag_srl_no` 논리 참조 + AI 운전 모드 참조 보존 정책 정합. Facility deactivate 선례
6. **Service 디스패치** — Java 21 switch 패턴 매칭 사용:
   ```java
   return switch (dto) {
       case PumpUpsertDto p -> savePump(p);
       case ValveUpsertDto v -> saveValve(v);
       case FlowMeterUpsertDto f -> saveFlowMeter(f);
       case PressureMeterUpsertDto pr -> savePressureMeter(pr);
       case LevelMeterUpsertDto l -> saveLevelMeter(l);
       case PowerMeterUpsertDto pw -> savePowerMeter(pw);
       default -> throw new RestApiException(InstrumentErrorCode.EQUIP_TYPE_MISMATCH);
   };
   ```
7. **Pump 자체 컬럼 변경 메서드 추가** — `Pump.java` 에 `changePumpSelfColumns(BigDecimal ratedHead, BigDecimal ratedFlwrt, String tagNm, PumpOprtngType oprtngType)` 1건 추가. null 인자는 기존 값 유지 (Instrument.changeInfo 동일 패턴). NOT NULL 컬럼 3건 (ratedHead·ratedFlwrt·oprtngType) 은 null 입력 시 무시 — 의도된 변경만 적용

## 도메인 모델

### 신규 엔티티

**없음**. `Instrument` 추상 부모 + 자식 6종은 이미 작성 완료. 본 작업은 신규 엔티티 정의 없음.

### 신규 DTO (Jackson 다형성 부모 + 자식 6종 + 응답·검색 DTO)

| DTO | 모듈 | 부모 | 주요 필드 |
|-----|------|------|---------|
| `InstrumentUpsertDto` | api (`com.mo.swtp.instrument.dto`) | abstract | `equipTypeCd` (discriminator, `EquipType` enum, NOT NULL), `facilityId` (UUID 36자, NOT NULL), `instrumentNm` (NOT NULL, max 100), `dispOrd` (NOT NULL) |
| `PumpUpsertDto` | api | extends `InstrumentUpsertDto` | `ratedHead` (BigDecimal, NOT NULL), `ratedFlwrt` (BigDecimal, NOT NULL), `tagNm` (max 50, NULL 허용), `oprtngType` (`PumpOprtngType` enum, NOT NULL) |
| `ValveUpsertDto` | api | extends | (자체 필드 0건) |
| `FlowMeterUpsertDto` | api | extends | (자체 필드 0건) |
| `PressureMeterUpsertDto` | api | extends | (자체 필드 0건) |
| `LevelMeterUpsertDto` | api | extends | (자체 필드 0건) |
| `PowerMeterUpsertDto` | api | extends | (자체 필드 0건) |
| `InstrumentDto` | api (`com.mo.swtp.instrument.dto`) | extends `BaseAuditResponseDto` | `instrumentId`, `instrumentNm`, `equipTypeCd`, `facilityId`, `facilityNm` (FK lazy 로 자연 join), `dispOrd`, `useYn` + Pump 분기 4건 (`ratedHead`·`ratedFlwrt`·`tagNm`·`oprtngType`) |
| `InstrumentSearchDto` | api | (없음) | `equipTypeCd`·`useYn`·`facilityId` (모두 NULL 허용) |

### 변경 엔티티

| 엔티티 | 변경 | 영향 |
|--------|------|------|
| `Pump` (`com.mo.swtp.instrument.domain.Pump`) | `changePumpSelfColumns(BigDecimal ratedHead, BigDecimal ratedFlwrt, String tagNm, PumpOprtngType oprtngType)` 메서드 1건 추가 | common 모듈 빌드 + QClass 재생성 영향 없음 |

### 검증 책임

| 검증 | 위치 | 사전 조건 |
|------|------|---------|
| `equipTypeCd` Discriminator 일치 | `InstrumentService.updateInstrument` | path.instrumentId 조회 후 `entity.getEquipType()` 와 `dto.getEquipTypeCd()` 비교 |
| `facilityId` 존재 (FK 사전 검증) | `InstrumentService.validateFacility` | 등록·수정 양쪽 |
| `(facilityId, instrumentNm)` 복합 UNIQUE | `InstrumentService.validateDuplicateInstrumentNm` | 등록: `existsByFacilityFacilityIdAndInstrumentNm` / 수정: `*AndInstrumentIdNot` 자기 제외 |
| Pump NOT NULL 자체 컬럼 (ratedHead·ratedFlwrt·oprtngType) | `PumpUpsertDto` jakarta validation + `Pump.create` `Objects.requireNonNull` 이중 방어선 | 등록 시 즉시 차단 |

## DB 설계 변경

**없음**. `V6_2__instrument_master_tables.sql` (부모 + 자식 6종) + `V8_2__pump_m_self_columns.sql` (Pump 자체 컬럼 4건) 이 이미 작성·적용된 상태다. 본 작업은 마이그레이션 SQL 0건.

## 성공 기준 (검증 가능 형태)

1. **빌드 통과** — `./gradlew.bat clean build` PASS (QClass 재생성 포함, 컴파일·전체 단위 테스트 모두 GREEN)
2. **신규 통합 테스트 PASS** — `./gradlew.bat :api:test --tests "*InstrumentServiceTest*"` PASS (7건 시나리오 GREEN)
   - 시나리오 ①: PUMP 등록 성공 → `InstrumentRepository.findById(returnedId)` 결과 `instanceof Pump` + `ratedHead`·`ratedFlwrt`·`oprtngType` 값 일치
   - 시나리오 ②: VALVE 등록 성공 → `instanceof Valve`
   - 시나리오 ③: 동일 시설·동일 이름 등록 시 `RestApiException(DUPLICATE_INSTRUMENT_NM)`
   - 시나리오 ④: 존재하지 않는 facilityId 로 등록 시 `RestApiException(INVALID_FACILITY_ID)` (DB FK 위반 500 이 아님)
   - 시나리오 ⑤: PUMP 엔티티에 `equipTypeCd = VALVE` PUT 시 `RestApiException(EQUIP_TYPE_MISMATCH)`
   - 시나리오 ⑥: 논리 삭제 → `entity.getUseYn() == YnType.N`
   - 시나리오 ⑦: 다른 시설에는 동일 이름 등록 허용 (시스템 전체 UNIQUE 가 아닌 `(facility_id, instrument_nm)` 복합 UNIQUE 검증)
3. **Swagger UI 노출** — `./gradlew.bat :api:bootRun` 후 `http://localhost:8080/swagger-ui/index.html` 에서 `POST /api/instrument` 의 Request Body 스키마가 `oneOf` (PumpUpsertDto·ValveUpsertDto·FlowMeterUpsertDto·PressureMeterUpsertDto·LevelMeterUpsertDto·PowerMeterUpsertDto) 로 노출, discriminator = `equipTypeCd`
4. **PostgreSQL DDL 정합** — 로컬 PostgreSQL 에서 자식 6종 각 1건 등록 후 `SELECT equip_type_cd, COUNT(*) FROM instrument_m GROUP BY equip_type_cd ORDER BY equip_type_cd` 6행 반환 (PUMP·VALVE·FLWMTR·PRSMTR·LVMTR·ELCMTR 각 1)
5. **자식 테이블 INSERT 검증** — `SELECT COUNT(*) FROM pump_m`·`valve_m`·`flwmtr_m`·`prsmtr_m`·`lvmtr_m`·`elcmtr_m` 각 1 (JPA JOINED 자식 테이블 INSERT 자동 발생 확인)

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `Pump.java` 에 자체 컬럼 4건 변경 메서드가 부재함을 확인 (조사 시점 부재) — `changePumpSelfColumns` 메서드 신규 추가 필요 | 결정 | Phase 3 에 추가. 1개 메서드, 10줄 미만 (ROOT coding-discipline §2.1 정량 기준 부합) |
| 자식 5종 (Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter) 의 자체 컬럼은 본 시점 0건 — Upsert·수정 시 자체 컬럼 분기 코드 미생성 | 결정 | 자식 6종 모두 부모 공통 4필드 (instrumentNm·facilityId·dispOrd + equipTypeCd discriminator) 만 처리. 향후 자체 컬럼 도입은 별도 사이클 |
| `InstrumentSearchDto` 검색 조건 NULL 처리 정책 — 모두 NULL 시 전체 반환 vs 페이지네이션 | 결정 | FacilitySearchDto 패턴 정합 — 모두 NULL 시 전체 반환, 페이지네이션 없음. 행 수 < 1000 가정 |
| `useYn` 단독 인덱스 부재 — `(facility_id, instrument_nm)` UNIQUE 와 `equip_type_cd` 단독 인덱스만 존재 | 결정 | 현 시점 검색 성능 충분 (DOM_YN 단독 인덱스 미적용 정책 `db/indexing-and-migration.md §3.4` 정합). 행 수 폭증 시 별도 사이클 |
| frontend SPEC 슬러그 — `계측기관리CRUD` (신규) vs `시설물관리기능` SPEC2 (갱신) | 미해결 | `/dev:spec` 단계 호출 시점 사용자 확인. 권고: 신규 슬러그 `계측기관리` (시설과 분리) |
| 권한 정책 — 현재 인증 사용자 전체 허용 | 결정 | Facility 와 동일, ADMIN 권한 분리는 별도 사이클 (사용자 명시 결정 — 2026-05-12 plan 모드) |
| `InstrumentDto.facilityNm` 노출 — `facility.getFacilityNm()` LAZY 로딩 호출 시 N+1 위험 | 가정 | 목록 조회는 InstrumentCustomRepositoryImpl 의 Querydsl 가 `JOIN FETCH` 또는 DTO 프로젝션 적용. 단건 조회는 1건 LAZY 로딩 1회 발생 (수용) |

## 제외 사항

- Pump 운영 기능 (제어·인터록·운전 모드·예측) — pump+AI 백지화 사이클 2 (별도 ANALYZE) 범위. 본 작업은 마스터 CRUD 만
- 자식 5종 (Valve 등) 의 도메인 룰 정의 — 자체 컬럼 도입 시점에 별도 사이클
- 멀티테넌트 시설별 권한 분리
- 계측기 일괄 등록 (Bulk Insert) API
- 계측기 검색의 keyword 풀텍스트 검색

## 예상 산출물

### 신규 파일

| 파일 | 모듈 | 역할 |
|------|------|------|
| `common/src/main/java/com/mo/swtp/instrument/dto/InstrumentUpsertDto.java` | common | Jackson 다형성 추상 부모 |
| `common/src/main/java/com/mo/swtp/instrument/dto/PumpUpsertDto.java` | common | Pump 자체 컬럼 4건 자식 DTO |
| `common/src/main/java/com/mo/swtp/instrument/dto/ValveUpsertDto.java` | common | skeleton 자식 DTO |
| `common/src/main/java/com/mo/swtp/instrument/dto/FlowMeterUpsertDto.java` | common | skeleton 자식 DTO |
| `common/src/main/java/com/mo/swtp/instrument/dto/PressureMeterUpsertDto.java` | common | skeleton 자식 DTO |
| `common/src/main/java/com/mo/swtp/instrument/dto/LevelMeterUpsertDto.java` | common | skeleton 자식 DTO |
| `common/src/main/java/com/mo/swtp/instrument/dto/PowerMeterUpsertDto.java` | common | skeleton 자식 DTO |
| `api/src/main/java/com/mo/swtp/instrument/dto/InstrumentDto.java` | api | 응답 DTO (BaseAuditResponseDto 상속, Pump 분기) |
| `api/src/main/java/com/mo/swtp/instrument/dto/InstrumentSearchDto.java` | api | 검색 조건 DTO |
| `api/src/main/java/com/mo/swtp/instrument/exception/InstrumentErrorCode.java` | api | ErrorCode enum (httpStatus int 단일 필드) |
| `api/src/main/java/com/mo/swtp/instrument/service/InstrumentService.java` | api | Service (Java 21 switch 디스패치 + 검증 3종) |
| `api/src/main/java/com/mo/swtp/instrument/web/InstrumentController.java` | api | Controller (5 엔드포인트) |
| `api/src/test/java/com/mo/swtp/instrument/service/InstrumentServiceTest.java` | api (test) | 통합 테스트 7건 시나리오 |

### 변경 파일

| 파일 | 변경 |
|------|------|
| `common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` | `changePumpSelfColumns(...)` 메서드 1건 추가 (10줄 이내) |
| `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentCustomRepository.java` | `findInstruments(InstrumentSearchDto)` 메서드 1건 추가 |
| `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentCustomRepositoryImpl.java` | 위 구현 추가 (Querydsl) |
| `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentRepository.java` | `existsByFacilityFacilityIdAndInstrumentNmAndInstrumentIdNot(facilityId, instrumentNm, instrumentId)` 메서드 1건 추가 (수정 시 자기 제외 검증) |

### 후속 산출물

- [태스크](../../../tasks/20260512/계측기관리CRUD/TASK1.md) — `/dev:task` 단계 자동 작성
- frontend SPEC — `/dev:spec` 단계 호출 시 작성 (선택, 슬러그 사용자 확인 필요)
