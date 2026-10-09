---
status: approved
created: 2026-05-12
updated: 2026-05-12
---
# 시설물응답DTO명세 — 도메인 분석

## 작업 배경

사용자 요청: `reference` 프로젝트의 `reference/common/src/main/java/com/hscmt/simulation/dataset/dto/DatasetDto.java` 패턴을 참조하여, `Facility` 마스터-자식 구조에 맞춰 자식별 응답 DTO 를 구현. 현 `FacilityDto` (`api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java`) 가 자식 전용 필드 `minReqPrsr` (DWT 만 보유) 을 부모 DTO 에 노출하고 `instanceof DistributionWaterTank dwt` 분기로 채우는 구조 — 부모/자식 분리 (`@Inheritance(JOINED)` + `@DiscriminatorColumn`) 의도와 응답 스키마 모순. Request 측 `FacilityUpsertDto` 는 이미 Jackson `@JsonTypeInfo` 다형성으로 분리 완료된 비대칭 상태.

추가로 사용자가 본 사이클 진입 시 도메인 정정: `tag_m.instrument_id` FK 가 SSOT 이므로 `pump_m.tag_nm` (양방향 중복) 폐기. 본 사이클의 자식 DTO 설계 직전에 함께 처리.

외부 산출물: 사용자 채팅 본문 (텍스트 paste). 별도 docx/png 첨부 없음.

## 회의록 (9 안건)

### 안건 1: 응답 DTO 3단 상속 룰 갱신

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `api-patterns.md §상속 상한 — 2단` 에 "마스터 다형성 한정 3단 예외" 절을 추가하는 갱신안 채택. 허용 조건 3건 — (1) 추상 부모에 `@JsonTypeInfo`·`@JsonSubTypes`·`@Schema(oneOf=...)` 의무, (2) 자식 전용 필드 부모 DTO 노출 금지 (현 `instanceof` 분기 폐기), (3) 4단 이상은 `/dev:analyze` 재진입. 근거: `coding-discipline.md §2.1` DTO 상속 3단 초과 권고 임계, `entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴` 의 다형성 실체 정합.
- **결론**: 룰 갱신 채택. `api-patterns.md §상속 상한` 절을 위 3 조건 명문화로 교체.

### 안건 2: Jackson 다형성 어노테이션 패턴

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `EXISTING_PROPERTY` 권장. Request DTO (`FacilityUpsertDto`) 가 이미 `EXISTING_PROPERTY(property="facilityTypeCd", visible=true)` 채택 — Response 대칭으로 frontend 역직렬화 경로 단일화. `WRAPPER_OBJECT` 는 `{"PWTF": {...}}` 감싸 비대칭. `EXTERNAL_PROPERTY` 는 직렬화 미지원 (역직렬화 전용). SpringDoc `@Schema(oneOf=..., discriminatorProperty="facilityTypeCd")` 의 자동 `discriminator.propertyName` 추출이 `EXISTING_PROPERTY` 일 때 최적.
- **결론**: `@JsonTypeInfo(use=NAME, include=EXISTING_PROPERTY, property="facilityTypeCd", visible=true)` + `@JsonSubTypes` + `@Schema(oneOf=..., discriminatorProperty="facilityTypeCd")` 패턴 채택. instrument 자식 DTO 도 동일 패턴 (`equipTypeCd`).

### 안건 3: 자식별 응답 DTO 네이밍 컨벤션

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 약어 기반 채택 — `DwtDto`·`PwtfDto`·`RsvDto`·`PrsfDto`·`PointDto`·`PumpDto`·`ValveDto`·`FlwmtrDto`·`PrsmtrDto`·`LvmtrDto`·`ElcmtrDto`. 근거: `domain-abbreviations.md` 의 비즈니스 도메인 약어 (`dwt`·`pwtf`·`facility`·`instrument`) 1차 정의 + `naming.md §Java 클래스 네이밍` 의 `{도메인명}Dto` 패턴 (PascalCase 약어 변환 일관). 풀네임 (`DistributionWaterTankDto`) 은 사전과 클래스명 변환 규칙 불일치. 기존 `FacilityListDto`·`DwtUpsertDto` 선례와 일관. `naming.md` 표 구조 변경 불필요 — 비고 보충 권고만.
- **결론**: 약어 기반 PascalCase. `naming.md` 표 비고에 "자식 DTO 는 비즈니스 도메인 약어 PascalCase" 한 줄 보충 (선택).

### 안건 4: FacilityListDto 처리 방향 (Round 2 재정정)

- 호출 에이전트: `wtp-domain-expert` (Round 1) + 오케스트레이터 직접 사실 확인 (Round 2)
- Round 1 답변 요약:
  - **wtp-domain-expert**: 현 `FacilityListDto` 유지. 사용처 단일 (`findFacilitiesHavingDwtChild()`) + 5컬럼 요약 정합.
- Round 2 — 사용자 지적 + reference 패턴 + 백지화 영향 확인:
  - **reference 패턴 (Explore 결과)**: `List<DatasetDto>` 단일 다형 응답 — 요약 DTO 부재. 외부 파라미터 (`dsTypeCd`) 로 자식 타입 필터링 + 자식별 `projectionFields()` Querydsl QBean 프로젝션. swtp 의 `FacilityListDto` 같은 요약 전용 DTO 패턴 reference 에 없음
  - **사용처 0건 확인 (grep)**: `api/src/main/java/com/mo/swtp/` 에서 `FacilityListService.findFacilitiesHavingDwtChild()` 호출하는 Controller 0건. `com.mo.swtp.pump` 참조 파일도 `FacilityListDto.java` 의 주석 1건뿐 — 즉 `PumpControlAnalysisController` (송수펌프제어분석 사이클 도입) 가 **pump+AI 백지화 사이클 1 (2026-05-12, 직전 커밋 `6419887`)** 으로 삭제되면서 호출자가 사라짐
  - **고아 자산 식별**: `FacilityListDto` + `FacilityListService` + `FacilityCustomRepository.findFacilitiesHavingDwtChild()` + `FacilityCustomRepositoryImpl.findFacilitiesHavingDwtChild()` + `FacilityListServiceTest` 5건 모두 사용처 0건 데드 코드
- **결론 재정정 (사용자 명시 결정 — 2026-05-12)**:
  - 위 5건 고아 자산을 **본 사이클에서 백지화**. pump+AI 백지화 사이클 1 의 즉시 부수 효과로 발생한 고아 상태이므로 동일 백지화 흐름의 연장으로 처리 (`coding-discipline.md §3.1` 데드 코드 정책 위반 아님 — 사용자 명시 결정 + 사이클 1 직후 연속 처리)
  - 향후 시설 목록 조회 사용처 재등장 시 reference 패턴 채택 — `List<FacilityDto>` 다형 응답 (자식별 인스턴스 혼재) + `FacilitySearchDto.facilityTypeCd` 외부 파라미터 필터링 + `FacilityDto.from(Facility)` 정적 팩토리의 자식 타입 매칭. 본 사이클 신규 구현 외 — 별도 ANALYZE 진입.
  - Round 1 의 wtp-domain-expert 결론은 사용처 단일 가정이 백지화 영향 확인 전 상태였음을 명시. 본 Round 2 결과로 재정정.

### 안건 5: abstract 응답 타입 Controller 동작

- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 런타임 직렬화는 안전 (Jackson 이 `@JsonTypeInfo` 런타임 타입으로 실제 인스턴스 `DwtDto`·`PwtfDto` 직렬화). 그러나 SpringDoc 자동 추출은 `ResponseEntity<CommonResponseDto<FacilityDto>>` 제네릭의 추상 클래스 스키마만 노출 → `@ApiResponse(content = @Content(schema = @Schema(oneOf={DwtDto.class, ...}, discriminatorProperty="facilityTypeCd")))` 명시 **의무**. 부재 시 `/dev:spec` SPEC 산출물에 자식 전용 필드 누락.
- **결론**: Controller `@Operation` 의 `@ApiResponse(content = @Content(schema = @Schema(oneOf={...}, discriminatorProperty="...")))` 명시 의무화. `api-patterns.md §Swagger/OpenAPI 패턴` 의 자식 다형성 응답 적용 시 1줄 권고 추가.

### 안건 6: instrument 도메인 동시 적용 범위

- 호출 에이전트: `wtp-domain-expert` + `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-domain-expert**: **Pump 만 본 사이클**. Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter 5종은 자식 전용 필드 0건 skeleton 이므로 현 시점 DTO 신설 시 부모 `InstrumentDto` 와 내용 동일 — `coding-discipline.md §2` "일회성 코드를 위해 추상화 계층 금지" 위반. `ot-integration.md §5` 인터록·운전 모드 평가에서 `equip_type_cd` 필터 강제가 요구되더라도 자식 전용 필드 추가 시점에 자식 DTO 신설이 적절.
- **결론**: 본 사이클 instrument 범위 = `InstrumentDto`(abstract) + `PumpDto` 2건만. 나머지 5종은 후속 사이클 이연. **Plan 단계 결정 변경 — plan 의 7건 동시 신설 → 2건으로 축소**.

### 안건 7: domain-abbreviations.md 신규 등록 필요 여부

- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 등록 0건. 자식 DTO 신설은 기존 비즈니스 도메인 (`facility`·`instrument`) 내 응답 DTO 추가이며 `com.mo.swtp.{도메인명}` 패키지 신설 요건 미충족. `point` 비즈니스 도메인 약어는 `domain-abbreviations.md` 도입 예정 표의 `facility` 비고에 이미 언급 — POINT 자식 테이블 (`point_m`) 신설 결정이 PLAN 단계 미결이므로 **본 사이클은 보류**.
- **결론**: 비즈니스 도메인 약어 신규 등록 0건. `point` 약어 도입은 별도 ANALYZE.

### 안건 8: `pump_m.tag_nm` 폐기 마이그레이션 + 어휘 정합성

- 호출 에이전트: `wtp-dba-reviewer` + `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: DDL 파일 권고 `common/src/main/resources/db/init/V8_6__pump_m_drop_tag_nm.sql` — 본 사이클의 DTO 신설 작업과 별도 파일 (롤백 단위 명확화). 무중단 절차 = `ALTER TABLE pump_m DROP COLUMN tag_nm` 단일 구문 즉시 실행 안전 (`pump_m` 마스터 행수 50건 미만 + api 사용처 0건 + `ACCESS EXCLUSIVE` 락이지만 메타데이터 변경만). 섀도우 컬럼 방식 불필요. COMMENT 자동 정리 (PostgreSQL `DROP COLUMN` 동작). 롤백은 `V8_7__pump_m_restore_tag_nm.sql` 별도 파일 패턴 권고 (git revert 시 DB 실행 상태와 파일 집합 불일치 위험). 근거: `indexing-and-migration.md §2 스키마 무중단 변경 원칙`·§4 COMMENT 의무화 정책.
  - **wtp-glossary-manager**: 표준 용어 `tag_nm` 자체 유지. `standard-terms.md §용어 표` 의 `tag_nm` 행 "사용 테이블" 컬럼에서 `pump_m` 제거 + 비고에 "pump_m 컬럼 폐기 (시설물응답DTO명세 ANALYZE1, 2026-05-12 — 운영 사용 전, tag_m.instrument_id FK SSOT 로 중복)" 기재. `DOM_TAG_NM_50` 데이터 도메인 유지 (`rawdata_m`·`tag_m` 계속 사용). 별도 폐기 이력 섹션 등록 불필요 — 용어 단위 폐기가 아닌 사용처 축소.
- **결론**: DDL `V8_6__pump_m_drop_tag_nm.sql` 신설. `standard-terms.md` 의 `tag_nm` 사용 테이블 목록에서 `pump_m` 제거. 엔티티 `Pump.java` 의 `tagNm` 필드 + `create()` 정적 팩토리 인자 + Javadoc 제거. 롤백 파일 (V8_7) 미리 작성하지 않음 (필요 시 작성).

### 안건 9: "FK 보유 측 SSOT — 역방향 중복 컬럼 금지" 원칙 명문화

- 호출 에이전트: `wtp-backend-engineer` + `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `entity-patterns.md` 신규 절 추가 권고. 초안: "두 엔티티 간 연관에서 FK 를 보유하는 쪽이 SSOT다. 연관 대상 엔티티의 식별 정보를 반대쪽 테이블에 중복 컬럼으로 보유는 정규화 위반이며 금지. 예외: FK 의미와 중복 컬럼 의미가 실질적으로 다른 경우 (이력 스냅샷·비정규화 집계 캐시) 는 PLAN 단계 사유 명시 + `/dev:analyze` 가정 섹션 기재." 근거: `entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴` 의 자식 PK 자동 상속 SSOT 원칙과 동일 맥락.
  - **wtp-domain-expert**: OT 안전 도메인 예외 조항 불필요. `ot-integration.md §5` 인터록 평가는 `instrument_id → pump_interlock_p → tag_srl_no` 논리 참조 경로로 완결 — 펌프에서 흡입센서 역참조 캐시 시나리오 부재. 양방향 중복 보유 시 이중 소스 불일치 위험이 안전 도메인 패턴 위협 (`coding-discipline.md §2.5 ⚠️ 절대 금지` 직결).
- **결론**: `entity-patterns.md` 에 §FK 보유 측 SSOT — 역방향 중복 컬럼 금지 절 신설. OT 안전 도메인 예외 조항 없이 명문화. PLAN 단계 사유 명시 예외만 허용 (이력 스냅샷·비정규화 집계).

## 표준 사전 카탈로그

### 신규 표준 단어

없음.

### 신규 표준 데이터 도메인

없음.

### 신규 표준 용어

없음. (기존 `tag_nm` 의 사용 테이블 목록만 갱신 — 신규 등록 아님)

## 신규 엔티티/DB 컬럼

본 사이클은 응답 DTO 신설 + 기존 컬럼 1건 폐기만 — 신규 엔티티·신규 DB 컬럼 없음.

| 변경 유형 | 항목 | 데이터 도메인 | NULL 정책 | 비고 |
|---------|------|-------------|----------|------|
| 컬럼 폐기 | `pump_m.tag_nm` | `DOM_TAG_NM_50` (데이터 도메인 자체는 유지) | NULL 허용 (폐기 전) | 양방향 중복 — `tag_m.instrument_id` FK SSOT. api 사용처 0건. DDL `V8_6__pump_m_drop_tag_nm.sql` |

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 해소책 |
|---------|------|
| `api-patterns.md §상속 상한 — 2단` 룰이 3단 상속 도입 차단 | 룰 갱신 — "마스터 다형성(@Inheritance JOINED + @DiscriminatorColumn) 한정 3단 예외" 절 추가. 허용 조건 3건 명문화 (안건 1 결론) |
| 현 `FacilityDto` 가 `BaseAuditResponseDto` 미상속 + `instanceof` 분기로 자식 필드 노출 | 본 사이클에서 `FacilityDto` abstract 로 리팩토링 + `BaseAuditResponseDto` 상속 + 자식별 DTO 분리 (`DwtDto`·`PwtfDto`·`RsvDto`·`PrsfDto`·`PointDto`). 부모 DTO 자식 전용 필드 노출 금지 룰 적용 (안건 1 조건 2) |
| `pump_m.tag_nm` 컬럼이 `tag_m.instrument_id` FK 와 양방향 중복 | DDL DROP COLUMN + 엔티티 필드 제거. 일반 원칙 `entity-patterns.md` 신규 절 §FK 보유 측 SSOT — 역방향 중복 컬럼 금지 명문화 (안건 9 결론) |

## PLAN 으로 전달할 결정 사항

### 도메인 모델 변경

1. **Facility 응답 DTO 분리 (api 모듈)**:
   - `FacilityDto` (api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java) → abstract 로 변경
   - `BaseAuditResponseDto → FacilityDto(abstract) → DwtDto/PwtfDto/RsvDto/PrsfDto/PointDto` 3단 상속
   - Jackson `@JsonTypeInfo(use=NAME, include=EXISTING_PROPERTY, property="facilityTypeCd", visible=true)` + `@JsonSubTypes`
   - `@Schema(oneOf={...}, discriminatorProperty="facilityTypeCd")` 명시
   - 각 자식 DTO 는 정적 팩토리 `from(자식엔티티)` 패턴 — `applyAuditMeta(facility)` 호출
   - `DwtDto` 만 자식 전용 `minReqPrsr` 보유 — 나머지 4종은 부모 필드만 노출

2. **Instrument 응답 DTO 부분 분리 (api 모듈)**:
   - `InstrumentDto`(abstract) + `PumpDto` 2건 신설 (안건 6 — 범위 축소)
   - 동일 Jackson 다형성 패턴 (`equipTypeCd` discriminator)
   - `PumpDto` 자식 전용 필드 = `ratedHead`·`ratedFlwrt`·`oprtngType` 3건 (tagNm 폐기 후)
   - Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter 5종은 본 사이클 신설 외 — 후속 사이클 이연

3. **Pump.tagNm 폐기 (common 모듈)**:
   - `Pump.java` 의 `tagNm` 필드 + `@Column(name="tag_nm")` + `create()` 정적 팩토리 인자 + 생성자 인자 + Javadoc 제거
   - DDL `V8_6__pump_m_drop_tag_nm.sql` 신설 — `ALTER TABLE pump_m DROP COLUMN tag_nm` 단일 구문 (안건 8 — DBA 승인 무중단 절차)

4. **고아 자산 백지화 (api 모듈, 안건 4 Round 2 재정정 결과)**:
   - `api/src/main/java/com/mo/swtp/facility/dto/FacilityListDto.java` 삭제
   - `api/src/main/java/com/mo/swtp/facility/service/FacilityListService.java` 삭제
   - `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepository.java` 의 `findFacilitiesHavingDwtChild(List<FacilityType>)` 메서드 시그니처 제거
   - `api/src/main/java/com/mo/swtp/facility/repository/FacilityCustomRepositoryImpl.java` 의 `findFacilitiesHavingDwtChild(...)` 구현 메서드 제거
   - `api/src/test/java/com/mo/swtp/facility/service/FacilityListServiceTest.java` 삭제
   - 본 4건은 pump+AI 백지화 사이클 1 (2026-05-12 직전 커밋) 직후 고아화된 데드 코드 — `coding-discipline.md §3.1` 위반 아님 (사용자 명시 결정)

### DB 설계 변경

- 신규 테이블/컬럼: 없음
- 폐기 컬럼: `pump_m.tag_nm` (DDL V8_6)
- 신규 인덱스/파티션: 없음

### 적용할 패턴

- `entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴` 의 도메인 룰 (자식 종류별 도메인 룰)
- `entity-patterns.md` 신규 §BaseEntity ↔ BaseAuditResponseDto 매핑 패턴 (3단 상속 사례 보강)
- `api-patterns.md §BaseAuditResponseDto 패턴` 갱신 (마스터 다형성 한정 3단 예외)
- `api-patterns.md §Swagger/OpenAPI 패턴` (자식 다형성 응답에 `@Schema(oneOf=..)` 의무)

### Controller 응답 타입

- `ResponseEntity<CommonResponseDto<FacilityDto>>` 유지 (abstract 응답)
- `@Operation` 의 `@ApiResponse(content = @Content(schema = @Schema(oneOf={DwtDto.class, PwtfDto.class, RsvDto.class, PrsfDto.class, PointDto.class}, discriminatorProperty="facilityTypeCd")))` 명시 의무

### Service `from()` 호출 변경

- `FacilityService` · `InstrumentService` 의 `FacilityDto.from(entity)` 호출은 변경 없음 — 정적 팩토리 시그니처 유지 (abstract `from(Facility)` → 자식 타입 매칭으로 자식 DTO 반환). 자식별 분기 로직은 정적 팩토리 내부로 이동

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| `FacilityDto.from(Facility)` 가 자식 타입 매칭 (switch / instanceof) 으로 자식 DTO 반환하는 정적 팩토리 패턴이 `coding-discipline.md §2` 단순성 우선과 정합 — `Facility.toDto()` 추상 메서드 패턴 대신 채택 | 결정 | PLAN 단계 적용 |
| `InstrumentDto`(abstract) 가 현재 존재하지 않으면 본 사이클에서 신규 작성 (`api/src/main/java/com/mo/swtp/instrument/dto/InstrumentDto.java`) | 가정 | PLAN 단계 사전 확인 |
| `PointDto` (SensorPoint 자식 응답 DTO) 는 SCADA 자동 생성 시설이라 수동 등록 API 대상 외 — Request 측 (`FacilityUpsertDto` Jackson subTypes 에 POINT 부재) 와 정합하게 응답 측에서도 POINT 처리 시점 결정 (단건 조회 응답에는 포함, 목록 응답 분기 대상 외) | 미해결 | PLAN 단계 결정 |
| Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter 5종 자식 DTO 후속 사이클 이연 — `equip_type_cd = 'VALVE'` 등 자식 종류로 instrument 단건 조회 시 `InstrumentDto` 부모 그대로 응답 (현재 0건이라 모순 없음) | 결정 | PLAN 단계 적용 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 본 사이클은 응답 DTO 분리 + Pump.tagNm 폐기만 수행. 알람 임계값·전이 조건·복귀 조건 컬럼 무접촉 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 인터록 평가 로직·선행조건 검사·기동 차단 코드 무접촉. 안건 9 의 양방향 중복 금지 룰 명문화는 평가 로직 변경 아님 (룰 추가만) |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 컬럼 및 강제 전환 로직 무접촉. 본 절 자체가 pump+AI 백지화 사이클 1 보류 상태 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h.transition_reason`·`pump_ctrl_h` 모두 백지화 상태이며 본 사이클 DTO 분리·tagNm 폐기 작업과 무관 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 자식별 응답 DTO 가 abstract 부모 상속 + Jackson 다형성 정상 직렬화 | `./gradlew.bat :api:test` PASS + 단위 테스트 (`FacilityDtoSerializationTest` 신규) — `DwtDto.from(dwt)` 의 `objectMapper.writeValueAsString()` 결과에 `"facilityTypeCd":"DWT"` 와 `"minReqPrsr"` 포함, `PwtfDto.from(pwtf)` 결과에 `minReqPrsr` 미포함 |
| `FacilityDto` 가 자식 전용 필드를 보유하지 않음 | `grep -E "minReqPrsr\|ratedHead\|ratedFlwrt" api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` 매칭 0건 |
| `Pump.tagNm` 폐기 완료 | `grep -E "tagNm\|tag_nm" common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` 매칭 0건 + `psql \d+ pump_m` 컬럼 목록에 `tag_nm` 부재 |
| `BaseAuditResponseDto` 상속 적용 | `grep "extends BaseAuditResponseDto" api/src/main/java/com/mo/swtp/facility/dto/FacilityDto.java` 매칭 1건 + 자식 DTO 5건 `extends FacilityDto` 매칭 |
| `@JsonTypeInfo`+`@JsonSubTypes`+`@Schema(oneOf=..)` 어노테이션 적용 | grep `EXISTING_PROPERTY` + `discriminatorProperty="facilityTypeCd"` 매칭 `FacilityDto.java` |
| Controller `@ApiResponse(content=@Content(schema=@Schema(oneOf=..)))` 명시 | 단건 조회 Controller 의 `@Operation` Annotation 에 매칭 |
| `api-patterns.md` 룰 갱신 반영 | `grep "마스터 다형성" backend/.claude/rules/api-patterns.md` 매칭 |
| `entity-patterns.md` "FK 보유 측 SSOT — 역방향 중복 컬럼 금지" 절 추가 | `grep "FK 보유 측 SSOT" backend/.claude/rules/entity-patterns.md` 매칭 |
| `standard-terms.md` `tag_nm` 사용 테이블 갱신 | `grep "tag_nm.*pump_m" backend/.claude/rules/dict/standard-terms.md` 매칭 0건 (사용 테이블 목록에서 pump_m 제거 확인) |
| 고아 자산 백지화 완료 | `FacilityListDto.java`·`FacilityListService.java`·`FacilityListServiceTest.java` 파일 부재 + `grep "findFacilitiesHavingDwtChild" api/src` 매칭 0건 |
| `./gradlew.bat clean build` BUILD SUCCESSFUL | 전체 빌드 통과 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `backend/.claude/rules/api-patterns.md` — §BaseAuditResponseDto 패턴 §상속 상한 — 2단 절을 "마스터 다형성 한정 3단 예외" 갱신. 허용 조건 3건 명문화 (안건 1·5 결론) — 2026-05-12 완료
- [x] `backend/.claude/rules/api-patterns.md` — §Swagger/OpenAPI 패턴에 "자식 다형성 응답 DTO 는 `@ApiResponse(content = @Content(schema = @Schema(oneOf={...}, discriminatorProperty=\"...\")))` 의무" 한 줄 추가 (안건 5 결론) — 2026-05-12 완료
- [x] `backend/.claude/rules/entity-patterns.md` — §JPA JOINED + DiscriminatorColumn 다형성 패턴 하위에 "§응답 DTO 매핑 패턴" 절 신설 (자식별 DTO + `from(Child)` 정적 팩토리 + `applyAuditMeta(parent)` 호출 표준화. 부모 DTO 자식 전용 필드 노출 금지 명문화) (안건 1 결론) — 2026-05-12 완료
- [x] `backend/.claude/rules/entity-patterns.md` — 신규 절 "§FK 보유 측 SSOT — 역방향 중복 컬럼 금지" 추가 (예외 조항 = PLAN 단계 사유 명시 시 이력 스냅샷·비정규화 집계 캐시) (안건 9 결론) — 2026-05-12 완료
- [x] `backend/.claude/rules/dict/standard-terms.md` — `tag_nm` 행 비고에 본 사이클 폐기 흐름 + 송수펌프제어분석 PLAN1 의 사용 테이블 목록 갱신 누락 발견 사실 기재 (안건 8 결론 — 사용 테이블 목록은 본 사이클 이전부터 `pump_m` 미등록 상태였으므로 컬럼값 변경 없이 비고 갱신만 진행) — 2026-05-12 완료

## 산출물

- [계획안](../../../plan/20260512/시설물응답DTO명세/PLAN1.md) (작성 예정 — `/dev:plan` 자동 전이 후)
