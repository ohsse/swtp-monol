---
status: approved
created: 2026-05-11
updated: 2026-05-11
---
# 시설물관리기능 — 도메인 분석

## 작업 배경

### 요청 요약
사용자(2026-05-11)는 시설물 관리 화면 backend CRUD API 신설을 요청. 시설물관리 페이지에서 시설 자식 타입별 탭(정수지·배수지·저수지·가압장)을 두고 등록·수정·삭제·조회 기능을 제공한다. 자식 타입마다 옵셔널한 명세값을 관리할 수 있어야 하며, 정수지 등록 시 시설 마스터(`facility_m`)에 등록되고 자식 명세값(예: 배수지 `min_req_prsr`)은 자식 테이블(`dwt_m` 등)에 동시에 등록되어야 한다.

### 전제 사실 (이미 정착된 결정)
- `마스터도메인설계 ANALYZE1` (Round 3, 2026-05-03 approved) 및 `송수펌프제어분석 ANALYZE1` (2026-05-08) 로 시설 단일 마스터 도메인의 엔티티·DDL 도입 완료:
  - 부모 추상 클래스 `Facility` + JPA `@Inheritance(JOINED)` + `@DiscriminatorColumn(facility_type_cd)` + UUID PK + `facility_nm` 시스템 전체 UNIQUE
  - 자식 5종 클래스 — `PurifiedWaterTank`(PWTF) / `DistributionWaterTank`(DWT, `min_req_prsr` NOT NULL) / `Reservoir`(RSV) / `PressureBoosterStation`(PRSF) / `SensorPoint`(POINT)
  - DDL V6_1 (`facility_m`/`pwtf_m`/`dwt_m`/`rsv_m`) + V8_1 (`prsf_m`)
- 본 사이클은 API 계층(Controller·Service·Repository·DTO·ErrorCode) 만 신설하며 엔티티·DDL 은 변경하지 않는다.
- POINT 자식은 SCADA 도메인에서 자동 생성·관리되므로 수동 UI 대상 외 — 본 사이클 제외(사용자 결정 2026-05-11).

### 외부 산출물
- 없음. 사용자 채팅 인용만 사용.
- plan 파일 참조: `~\.claude\plans\merry-wiggling-cloud.md` (2026-05-11 사용자 승인)

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 신규 표준 사전 등록 0건 확정 여부
- **호출 에이전트**: `wtp-glossary-manager`
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: 검토 대상 DB 컬럼(facility_id, facility_nm, facility_type_cd, parent_facility_id, disp_ord, main_yn, use_yn, min_req_prsr) 8건 모두 backend `standard-terms.md` 에 기등록. 표준 단어·표준 데이터 도메인 신규 0건. 비즈니스 도메인 약어 `prsf`·`rsv` 가 `domain-abbreviations.md` 에 독립 행으로 미등록 — 단 `com.mo.swtp.facility` 단일 패키지 하위 자식이라면 등록 대상 외 가능성. 오케스트레이터의 패키지 구조 확인 요청.
- **오케스트레이터 종결 (Round 2 생략)**: 코드 탐색으로 `PressureBoosterStation.java`·`Reservoir.java` 모두 `common/src/main/java/com/mo/swtp/facility/domain/` 단일 패키지 하위에 위치함을 확인. `domain-abbreviations.md` 의 등록 규칙("마스터 도메인은 실제 코드 `com.mo.swtp.{도메인명}` 패키지가 존재해야 등록") 에 따라 별도 패키지 없는 자식 enum 코드값은 비즈니스 도메인 약어 등록 대상 외. 단 `domain-abbreviations.md` 의 facility 도입 예정 행 본문에 `prsf` 자식이 등장(2026-05-07 송수펌프제어_운전현황분석 ANALYZE1 추가 표기)했으나 본문에 모두 명시되어 있음. ErrorCode enum 이름·API URL 경로 컴포넌트는 사전 적용 범위 외.
- **결론**: **신규 표준 사전 등록 0건**. 룰 갱신 지시서 0건.

### 안건 2: `parentFacilityId` self-FK 검증 정책 (3개 결정)
- **호출 에이전트**: `wtp-domain-expert`
- **Round 1 답변 요약**:
  - **결정 1 (존재 검증 의무)**: **Y (필수)**. 부재 시 `INVALID_PARENT_FACILITY_ID` 400 반환. 근거 — `ot-integration.md §5` 의 `facility_type_cd` 필터 강제 규칙은 "시설 단위 별도 평가" 전제. 존재하지 않는 부모를 가진 시설이 `ai_drvn_mod_p.facility_id` 로 참조되면 강제 전환 판정 대상 집합 오염.
  - **결정 2 (자기 자신 참조 차단)**: **Y (필수)**. 인터록 및 AI 운전 모드 평가에서 self-FK 순회 잠재 경로의 무한 루프 위험. 방어 심층 원칙 적용.
  - **결정 3 (타입 혼합 부모 허용)**: **조건부 허용**. PWTF 가 DWT 부모를 가지는 구성을 DB 차원에서 차단할 근거 없음. `ai_drvn_mod_p` 행은 `facility_type_cd` 단위 독립 평가이므로 혼합 타입 부모 허용해도 AI 운전 모드 평가 직접 영향 없음. 단 차기 사이클에서 `pump_interlock_p.facility_type_cd` 신설 시 모호성 재검토 필요(`ot-integration.md §5` PRSF 인터록 룰 미결정 항목과 동일 맥락).
- **결론**: 3개 결정 모두 PLAN 의 "구현 방향" 에 반영. 검증 코드 위치 — `FacilityService` 또는 자식별 Service 의 사전 검증 단계.

### 안건 3: 논리 삭제 후 도메인 영향 (블로커 1건)
- **호출 에이전트**: `wtp-domain-expert`
- **Round 1 답변 요약 + 발견 사항**:
  - **영향 1 (AI 운전 모드 평가) — 블로커(높음)**: `use_yn='N'` 시설의 `ai_drvn_mod_p` 행이 잔존 시 스케줄러 `last_rcv_dtm` 5분 초과 평가 대상 포함됨. `SCADA_TIMEOUT` 전환 이력(`ai_drvn_mod_h`) 의미 없는 누적. **PLAN 단계 결정 필요** — 본 사이클 스케줄러 미변경이라면 "비활성 시설 강제 전환 평가 제외" 를 명시적 제외 사항으로 기재해야 한다(`ot-integration.md §5`).
  - **영향 2 (인터록 룰)**: 직접 영향 없음. `pump_interlock_p` 는 instrument 단위 룰이고 facility_id 미보유(`ot-integration.md §2·§5`). 비활성 시설 소속 펌프 제어 정책은 instrument 도메인 차기 사이클 검토.
  - **영향 3 (활성 조회 제외)**: 본 사이클 직접 영향 없음. 차기 시설 연관 조회 API 구현 시 `use_yn='Y'` 필터 의무 명기 권고.
- **결론**:
  - 블로커 1건 해소책: 본 사이클 **스케줄러 미변경** + PLAN "## 제외 사항" 에 "비활성 시설(`use_yn='N'`)의 `ai_drvn_mod_p` 강제 전환 평가 처리는 차기 사이클" 명시 + PLAN "## 가정 및 미해결 질문" 에 "현재 스케줄러 강제 전환 쿼리에 `facility_m.use_yn = 'Y'` 필터 부재 — 차기 사이클에서 스케줄러 또는 논리 삭제 시점 연계 처리 결정 필요" 기재.

### 안건 4: Repository 분리/통합 전략
- **호출 에이전트**: `wtp-backend-engineer`
- **Round 1 답변 요약**:
  - 부모 통합 Repository 단일 유지(`마스터도메인설계 PLAN1` 결정 정합).
  - `FacilityCustomRepository` 인터페이스 + `Impl` shell 선언 권고 — `findAllByFacilityTypeAndUseYn` 파생 메서드명 조합 한계 + 향후 Querydsl 전환 비용 회피.
  - 자식 Repository(`PwtfRepository` 등) 추가 불필요 — 본 사이클 검색 필터 type/useYn 만, DWT `minReqPrsr` 검색 요건 없음.
- **결론**: `FacilityRepository extends JpaRepository<Facility, String>, FacilityCustomRepository` 1종. `FacilityCustomRepositoryImpl` Querydsl 구현은 실제 필요 시 채움(현 시점 shell).

### 안건 5: Controller·Service·UpsertDto 구조 (Round 1 + Round 2)
- **호출 에이전트**: `wtp-backend-engineer`
- **Round 1 답변 요약**:
  - **응답 DTO = 옵션 C (현재 단일, 추후 분리)**. 자식 전용 컬럼이 DWT `minReqPrsr` 1건 + BigDecimal 표준 라이브러리 타입이라 Swagger 자동 인식. 단순성 우선(`coding-discipline.md §2`).
  - **Controller·Service·UpsertDto = 자식별 분리 권고 (당시)**. Controller 5개·Service 5개·자식별 UpsertDto 4개 + 부모 1단 상속(`FacilityUpsertDto`). API URL 로 자식 명시 (`/api/facility/pwtf` 등).
- **Round 2 트리거**: 사용자(2026-05-11)가 `backend/reference/.../DatasetController.java`·`DatasetService.java`·`DatasetUpsertDto.java` 의 단일 엔드포인트 + Jackson 다형성(`@JsonTypeInfo` + `@JsonSubTypes`) + Service 내부 `instanceof` 패턴 매칭으로 자식별 메서드 호출하는 검증된 패턴을 인용. JOINED 엔티티 상속 + JSON DTO 다형성 의미론적 일치 + 클래스 수 1/4 축소를 근거로 구조 변경 요청.
- **Round 2 답변 요약**:
  - **(A) Bean Validation**: 채택 가능. `@RequestBody @Valid FacilityUpsertDto dto` Jackson 다형성 역직렬화 후 자식 인스턴스에 `@NotNull min_req_prsr` 등 정상 검증. 단 자식 DTO 중첩 객체 도입 시 `@Valid` 계단식 전파 명시 의무.
  - **(B) Swagger/SpringDoc `oneOf`**: 조건부 채택. `@JsonTypeInfo`/`@JsonSubTypes` 만으로는 SpringDoc 자동 `oneOf` 추출이 불완전. **부모 DTO 에 `@Schema(oneOf = {PwtfUpsertDto.class, DwtUpsertDto.class, RsvUpsertDto.class, PrsfUpsertDto.class}, discriminatorProperty = "facilityTypeCd")` 명시 의무**. `api-patterns.md` "## DTO @Schema(implementation) 명시 패턴" 의 사용자 정의 참조형 DTO 필수 조항 준수.
  - **(C) §2.1 정량 기준**: 임계 미초과 통과. 디스패처 `saveFacility(...)` 약 10줄, 자식별 `savePwtf`/`saveDwt`/`saveRsv`/`savePrsf` 각 25-30줄(검증 + 정적 팩토리 + save). 추상화 계층 3단(Controller → Service → Repository) 유지 — 같은 Service Bean 내부 private 메서드 분리는 추상화 계층으로 산정하지 않음.
  - **(D) 종합 권고**: 사용자 제안 채택. Round 1 클래스 약 15개(Controller 5 + Service 5 + DTO 5) → Round 2 약 6개(Controller 1 + Service 1 + DTO 부모 1 + 자식 4) 축소. ROOT `coding-discipline.md §2` 단순성 원칙 정합.
  - **권고 1건 (중간)**: 부모 DTO `@Schema(oneOf=..., discriminatorProperty=...)` 명시 의무를 PLAN 체크박스에 반드시 포함.
- **결론 (Round 2 채택)**:
  - **Controller**: 단일 `FacilityController` (5 엔드포인트 — `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `DELETE /{id}`)
  - **Service**: 단일 `FacilityService` — `saveFacility(FacilityUpsertDto)` 가 `instanceof` 패턴 매칭으로 자식별 private 메서드(`savePwtf`/`saveDwt`/`saveRsv`/`savePrsf`) 분기 호출. 수정도 동일 패턴(`updateFacility` 디스패처 + 자식별 private 메서드)
  - **UpsertDto**: 추상 부모 `FacilityUpsertDto` (`@JsonTypeInfo(use=Id.NAME, include=As.PROPERTY, property="facilityTypeCd")` + `@JsonSubTypes` 4종 + `@Schema(oneOf=..., discriminatorProperty="facilityTypeCd")`) + 자식 4(`PwtfUpsertDto`/`DwtUpsertDto`(`minReqPrsr` `@NotNull`)/`RsvUpsertDto`/`PrsfUpsertDto`)
  - **응답 DTO**: 단일 `FacilityDto` (nullable `minReqPrsr` + `@Schema(description="DWT 전용, 그 외 자식 타입은 null")` 명시) — Round 1 결정 유지

### 안건 6: DB UNIQUE race condition + 인덱스 정합성
- **호출 에이전트**: `wtp-dba-reviewer`
- **Round 1 답변 요약 + 오케스트레이터 코드 확인 후 정정**:
  - **등록(POST) race**: 사전 `existsByFacilityNm` + DB UNIQUE 양 단계 방어 (`user_m`/`tag_m` 선례 동일). race 발생 시 `DataIntegrityViolationException` → GlobalExceptionHandler 변환 위임 가정.
  - **수정(PUT) race**: `existsByFacilityNmAndFacilityIdNot(newNm, currentId)` 단일 EXISTS 권장.
  - **인덱스 검토** — **오케스트레이터 정정**: V6_1 DDL 실제 인덱스는 `idx_facility_m_facility_nm`(UNIQUE) + `idx_facility_m_facility_type_cd` 만 정의됨. **`idx_facility_m_use_yn` 단독 인덱스는 존재하지 않음** → §3.4 위반 없음, DROP 권고 자동 해소. `(facility_type_cd, use_yn)` 복합 인덱스 추가는 본 사이클 비범위(엔티티/DDL 변경 금지)와 충돌 — 차기 사이클 검토 항목으로 분리. `facility_type_cd` 단독 인덱스로 `?type=PWTF&useYn=Y` 쿼리 시 type 필터 후 결과 집합에서 useYn 조건이 충분히 효율 처리됨(카디널리티 type=4·useYn=2).
  - **GlobalExceptionHandler 매핑 확인** — `DataIntegrityViolationException` → 도메인별 ErrorCode 매핑이 현재 backend 코드에 등록되지 않음(grep 0건). race 발생 시 500 응답 위험은 존재하나 운영자 single-user POST 동시성 시나리오 발생 확률 매우 낮음. PLAN "## 가정 및 미해결 질문" 에 기재.
- **결론**:
  - 사전 `existsBy` 1차 방어선 사용, race 발생 시 500 응답 가능성은 가정 섹션 기재
  - 본 사이클 DDL 변경 없음, 인덱스 정합성(복합 인덱스 통합)은 차기 사이클 항목으로 식별

---

## 표준 사전 카탈로그

### 신규 표준 단어
없음 (모든 단어 ROOT `standard-words.md` 기등록 — `nm`·`cd`·`yn`·`ord`·`disp`·`main`·`parent`·`min`·`req`·`prsr`).

### 신규 표준 데이터 도메인
없음 (모든 데이터 도메인 ROOT `standard-data-domains.md` 기등록 — `DOM_ID_36`·`DOM_NAME_100`·`DOM_CODE_20`·`DOM_YN`·`DOM_QTY_15_4`).

### 신규 표준 용어
없음 (모든 DB 컬럼 backend `standard-terms.md` 기등록 — `facility_id`·`facility_nm`·`facility_type_cd`·`parent_facility_id`·`disp_ord`·`main_yn`·`use_yn`·`min_req_prsr`).

### 비즈니스 도메인 약어
신규 등록 0건. `prsf`·`rsv` 는 `com.mo.swtp.facility` 단일 패키지 하위 자식 enum 코드값이므로 `domain-abbreviations.md` 의 등록 규칙(독립 패키지 존재 요건) 미충족 — 등록 대상 외.

---

## 신규 엔티티/DB 컬럼

없음. 본 사이클은 API 계층만 신설.

엔티티/DDL 은 다음 사이클 결과로 이미 정착되어 변경 대상 외:
- 마스터도메인설계 사이클 (2026-05-03): `Facility`·`PurifiedWaterTank`·`DistributionWaterTank`·`Reservoir` + V6_1 DDL
- 송수펌프제어분석 사이클 (2026-05-08): `PressureBoosterStation` + V8_1 DDL + `DistributionWaterTank.min_req_prsr` 자식 컬럼

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 회의 결론 |
|----------|---------|
| `idx_facility_m_use_yn` 단독 인덱스 `DOM_YN` DDL 정책 §3.4 위반 가능성 (DBA Round 1 권고) | **해소** — V6_1 DDL 실제 정의 없음 확인. 위반 없음 |
| 비활성 시설 `ai_drvn_mod_p` 강제 전환 평가 누적 (Domain Expert 블로커) | 본 사이클 스케줄러 미변경. PLAN "## 제외 사항" + "## 가정 및 미해결 질문" 에 차기 사이클 위임 명시 |
| `(facility_type_cd, use_yn)` 복합 인덱스 미적용 (DBA 권고) | 본 사이클 DDL 변경 금지(plan 비범위). 차기 사이클 검토 항목으로 식별 |
| `DataIntegrityViolationException` → ErrorCode 변환 핸들러 부재 (DBA 권고) | 사전 `existsBy` 1차 방어선 사용. 본 사이클 핸들러 미신설, PLAN 가정 섹션 기재 |
| `prsf`·`rsv` 비즈니스 도메인 약어 미등록 (Glossary Round 1) | **해소** — `com.mo.swtp.facility` 단일 패키지 귀속 자식 enum 으로 등록 대상 외 |
| 다형성 부모 DTO `@Schema(oneOf=..., discriminatorProperty=...)` 명시 누락 위험 (Backend Engineer Round 2 권고) | **권고 (중간)** — PLAN TASK 체크박스에 `FacilityUpsertDto.java` 작성 시 `@Schema(oneOf={...}, discriminatorProperty="facilityTypeCd")` 명시를 검증 항목으로 포함. `api-patterns.md` "## DTO @Schema(implementation) 명시 패턴" 사용자 정의 참조형 DTO 필수 조항 준수 의무 |

---

## PLAN 으로 전달할 결정 사항

### 도메인 모델
변경 없음. 기존 `Facility` 부모 + 자식 5종(PWTF/DWT/RSV/PRSF/POINT) 그대로 사용. 본 사이클은 자식 4종(POINT 제외) CRUD API 만 신설.

### DB 설계 변경
**없음**. 본 사이클 비범위.

### 적용할 패턴

#### 패키지 구조 (api 모듈 신설 — Round 2 결정: 단일 Controller·Service)
```
api/src/main/java/com/mo/swtp/facility/
├── controller/
│   └── FacilityController.java          (단일 — 5 엔드포인트)
├── service/
│   └── FacilityService.java             (단일 — saveFacility/updateFacility 디스패처 + 자식별 private 메서드 4종)
├── repository/
│   ├── FacilityRepository.java          (JpaRepository<Facility,String> + FacilityCustomRepository)
│   ├── FacilityCustomRepository.java    (인터페이스 shell — Querydsl 확장 여지)
│   └── FacilityCustomRepositoryImpl.java(현재 메서드 0건 shell)
├── dto/
│   ├── FacilityDto.java                 (공통 응답 — nullable minReqPrsr + @Schema description 명시)
│   ├── FacilityUpsertDto.java           (추상 부모 — @JsonTypeInfo + @JsonSubTypes + @Schema(oneOf=..., discriminatorProperty="facilityTypeCd"))
│   ├── PwtfUpsertDto.java               (extends FacilityUpsertDto)
│   ├── DwtUpsertDto.java                (extends FacilityUpsertDto — minReqPrsr @NotNull)
│   ├── RsvUpsertDto.java                (extends FacilityUpsertDto)
│   └── PrsfUpsertDto.java               (extends FacilityUpsertDto)
└── exception/
    └── FacilityErrorCode.java
```

> common 모듈 변경 없음 — 엔티티는 이미 `common/com/mo/swtp/facility/domain/` 에 존재.

#### CRUD 엔드포인트 (단일 컨트롤러 + Jackson 다형성)
- `GET    /api/facility?type={enum}&useYn={Y|N}` — 전체 목록 (정렬: useYn DESC, dispOrd ASC, facilityNm ASC)
- `GET    /api/facility/{facilityId}` — 단건
- `POST   /api/facility` (`FacilityUpsertDto` — body 의 `facilityTypeCd` discriminator 로 자식 자동 역직렬화) → 자식 종류별 등록
- `PUT    /api/facility/{facilityId}` (`FacilityUpsertDto` — body 의 `facilityTypeCd` 와 path 의 대상 시설 `facility_type_cd` 일치 검증) → 자식 종류별 수정
- `DELETE /api/facility/{facilityId}` — 논리 삭제 (`use_yn='N'`, 자식 종류 무관 부모 메서드 호출로 충분)

> Reference 패턴 정합 (`backend/reference/api/.../DatasetController.java`): 단일 `POST /dataset` 엔드포인트가 `DatasetUpsertDto` 다형성 부모로 모든 자식 등록 수용 + Service 내부 `instanceof` 분기로 자식별 메서드 호출.

#### FacilityUpsertDto 어노테이션 명세 (필수)
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

> `@Schema(oneOf=..., discriminatorProperty=...)` 누락 시 `api-patterns.md` "## DTO @Schema(implementation) 명시 패턴" 사용자 정의 참조형 DTO 필수 조항 위반 — Backend Engineer Round 2 권고 (중간) 직결. PLAN TASK 체크박스에서 명시 검증.

#### FacilityService 디스패처 패턴 (Round 2 채택)
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
    validateTypeMatch(found, dto);  // facility_type_cd ↔ dto 자식 타입 일치 검증
    validateParentFacility(dto.getParentFacilityId(), facilityId);
    validateDuplicateFacilityNmOnUpdate(dto.getFacilityNm(), facilityId);
    if (dto instanceof PwtfUpsertDto pwtf)      updatePwtf((PurifiedWaterTank) found, pwtf);
    else if (dto instanceof DwtUpsertDto dwt)   updateDwt((DistributionWaterTank) found, dwt);
    // ... 나머지 자식 분기 ...
}

// 자식별 private 메서드 — 각 25-30줄
private void savePwtf(PwtfUpsertDto dto) { ... }
private void saveDwt(DwtUpsertDto dto)   { ... }
// ...
```

> 추상화 계층: Controller → Service → Repository = 3단 유지. `saveFacility` 디스패처 → `savePwtf` 호출은 같은 Service Bean 내 private 메서드 분리로 추상화 계층 산정 외 (Round 2 (C) 결론).

#### ErrorCode (신설)
| name | httpStatus | 발생 시점 |
|------|-----------|---------|
| `FACILITY_NOT_FOUND` | 404 | 단건 조회/수정/삭제 시 facilityId 미존재 |
| `DUPLICATE_FACILITY_NM` | 409 | 등록·수정 시 facility_nm UNIQUE 충돌 (사전 `existsBy` 검출) |
| `FACILITY_TYPE_MISMATCH` | 400 | PUT 시 path 대상 시설의 `facility_type_cd` 가 body 의 `facilityTypeCd` discriminator 와 불일치 |
| `INVALID_PARENT_FACILITY_ID` | 400 | parentFacilityId 미존재 또는 자기 자신 참조 |

#### 검증 흐름 (등록/수정 공통)
1. `parentFacilityId` NULL 아니면 (1) `facilityRepository.existsById(parentFacilityId)` (2) `facilityId != parentFacilityId` (수정 시) 검증 → `INVALID_PARENT_FACILITY_ID`
2. `facilityNm` 등록 시 `existsByFacilityNm`, 수정 시 `existsByFacilityNmAndFacilityIdNot(newNm, currentId)` → `DUPLICATE_FACILITY_NM`
3. PUT 시 `validateTypeMatch(found, dto)` — 대상 시설의 `facility_type_cd` 와 body 의 `facilityTypeCd` 비교 → `FACILITY_TYPE_MISMATCH`
4. 자식 정적 팩토리(예: `DistributionWaterTank.create(...)`) → `facilityRepository.save(...)` 영속
5. 수정은 `Facility.changeInfo(...)` JPA dirty checking, DWT 의 `minReqPrsr` 변경은 자식 클래스 변경 메서드(필요 시 신설) 사용

#### 논리 삭제
- `DELETE /api/facility/{facilityId}` → `Facility.deactivate()` 호출 → `use_yn='N'` 전환
- 자식 종류 무관 부모 메서드 단일 호출로 충분 (자식별 분기 불필요)
- 물리 삭제 미지원 (`instrument_m`·`ai_drvn_mod_p`·`rawdata_1m_h` 논리 참조 보존)

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 비활성 시설(`use_yn='N'`)의 `ai_drvn_mod_p` 강제 전환 평가 처리는 본 사이클 비범위 — 차기 사이클에서 스케줄러 강제 전환 쿼리에 `facility_m.use_yn='Y'` 필터 추가 또는 논리 삭제 시점 연계 처리 결정 필요 | 미해결 | `wtp-domain-expert` 안건 3 블로커 해소책. `ot-integration.md §5` 강제 전환 평가 의미 없는 이력 누적 위험 |
| `parentFacilityId` 타입 혼합 부모 허용 — PWTF 가 DWT 부모를 가지는 구성을 DB·애플리케이션 차원에서 차단하지 않음. 차기 사이클 `pump_interlock_p.facility_type_cd` 신설 시 부모 타입 혼합 모호성 재검토 필요 | 가정 | `wtp-domain-expert` 안건 2 결정 3. `ot-integration.md §5` PRSF 인터록 룰 미결정 항목과 동일 맥락 |
| `DataIntegrityViolationException` → `DUPLICATE_FACILITY_NM` 변환 GlobalExceptionHandler 매핑이 backend 코드에 미존재 — 본 사이클은 사전 `existsBy` 1차 방어선만 사용. race 발생 시 500 응답 가능성 존재 (운영자 single-user POST 동시성 발생 확률 매우 낮음) | 가정 | `wtp-dba-reviewer` 안건 6 권고. 본 사이클 핸들러 미신설, 차기 사이클 또는 다른 도메인과 함께 일괄 도입 |
| `(facility_type_cd, use_yn)` 복합 인덱스 추가는 본 사이클 비범위(엔티티/DDL 변경 금지) — 단일 `facility_type_cd` 인덱스로 카디널리티 4 필터 후 useYn 필터링 효율 충분. 향후 시설 수 수천 건 그모 도달 시 차기 사이클 검토 | 가정 | `wtp-dba-reviewer` 안건 6 권고. `db/indexing-and-migration.md §1` 복합 인덱스 컬럼 순서 규칙 적용은 차기 사이클 |
| 활성 시설만 노출하는 다른 도메인 API(차기 시설 트리 조회·펌프 대시보드 등) 구현 시 `use_yn='Y'` 필터 의무 명기 — 본 사이클 직접 영향 없음 | 가정 | `wtp-domain-expert` 안건 3 영향 3 |
| `FacilityUpsertDto` 다형성 부모 DTO 의 `@Schema(oneOf=..., discriminatorProperty="facilityTypeCd")` 명시는 PLAN TASK 체크박스에서 검증 항목으로 포함 — SpringDoc 자동 `oneOf` 추출이 불완전한 케이스 대응 + `/dev:spec` frontend SPEC 정확성 보장 | 결정 | `wtp-backend-engineer` Round 2 권고 (중간). `api-patterns.md` "## DTO @Schema(implementation) 명시 패턴" 사용자 정의 참조형 DTO 필수 조항 직결 |
| `@RequestBody @Valid FacilityUpsertDto dto` 자식 DTO 에 중첩 객체 도입 시 `@Valid` 계단식 전파 명시 의무 — 본 사이클 자식 DTO 의 중첩 객체 없음 (모두 primitive·String·BigDecimal·YnType enum), 직접 영향 없음. 차기 자식 DTO 확장 시 점검 의무 | 가정 | `wtp-backend-engineer` Round 2 (A) 결론. Spring Boot 4 + Hibernate Validator 기본 동작 |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 시설 마스터 CRUD 만 추가. 알람 임계값·전이 조건·복귀 조건 변경 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | `pump_interlock_p` 는 instrument 단위 룰. 본 사이클은 시설 마스터 CRUD 만 — 인터록 룰 평가 직접 영향 없음(`wtp-domain-expert` 안건 3 영향 2 확인) |
| AI 운전 모드 (`ot-integration.md §5`) | 해당 (간접) | `ai_drvn_mod_p.facility_id` 가 시설 마스터를 논리 참조. **논리 삭제 후 강제 전환 평가 영향 = 블로커 (안건 3 영향 1)** — 본 사이클 스케줄러 미변경으로 회피, PLAN 가정 섹션 및 제외 사항 명시 의무 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 시설 마스터 변경은 BaseEntity 4컬럼(`rgstr_dtm`·`updt_dtm`·`rgstr_id`·`updt_id`) audit 으로 충분. 별도 `facility_h` history 테이블 불필요 |

---

## 룰 갱신 지시서

본 사이클은 신규 사전 등록 0건이며 모든 결정 사항이 기존 룰과 정합하므로 룰 갱신 지시서 0건이다.

(참고: 차기 사이클에서 비활성 시설 `ai_drvn_mod_p` 강제 전환 평가 처리를 결정할 때 `ot-integration.md §5` 본문에 비활성 시설 처리 정책 절을 추가하는 룰 갱신이 발생할 수 있다. 본 사이클 범위 외.)

---

## 산출물

- [계획안](../../../plan/20260511/시설물관리기능/PLAN1.md) (작성 예정)
