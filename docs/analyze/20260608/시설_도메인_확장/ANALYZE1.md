---
status: approved
created: 2026-06-08
updated: 2026-06-08
---
# 시설 도메인 확장 — 시설 유형 7종 + 그룹 분류(저장/운영/계통) 도입 — 도메인 분석

## 작업 배경

- **요청 요약**: `시설별 사용량` 대시보드 API(1~6번 섹션)를 개발하기 전, 시설(facility) 도메인에 (1) 신규 시설 유형 7종, (2) 시설을 묶는 그룹 분류(저장시설/운영시설/계통시설) 개념을 도입한다. 섹션별 API 는 본 확장 위에서 `시설별사용량-1번섹션` 등 별도 슬러그로 진행한다.
- **외부 산출물**: `backend/image/시설별사용량.png` — 시설별 전력 사용량(송수동·약품동 등 카드) + 그룹 단위 집계 차트(분포도·합계) 대시보드 와이어프레임. 1~6번 섹션 표기.
- **현 상태**: facility 도메인은 `FacilityType` 5종(PWTF·DWT·RSV·POINT·PRSF) JPA JOINED 다형성으로 구현 완료. "시설 그룹/분류" 개념은 코드·DDL 어디에도 부재 — 신규 도입.
- **사용자 사전 토의 확정 (4건)**: (1) 그룹 = `FacilityType` enum 파생 속성(DB 컬럼 미신설), (2) 신규 7종 = 유형별 JOINED skeleton, (3) 송수동·가압장(PRSF) = 운영시설, (4) 범위 = 모델 + 등록 API 확장.

---

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 신규 7종 facility_type_cd 코드값 + FacilityGroup enum + 어휘 등재
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 7종 코드값 제안 — WTBLD(송수동)·CHMB(약품동)·ACFB(활성탄여과지)·POZB(전오존동)·FLTB(여과지동)·DEWB(탈수기동, DWBLD 는 DWT 와 DW 2자 충돌 회피)·SOLAR(태양광). 기존 5종과 철자 충돌 없음. `FacilityGroup` = STORAGE/OPERATION/NETWORK 3종 적합(영문 표준, 충돌 없음). `group` 표준 단어 신규 등록 권고(DTO `facilityGroupCd` 조합 재료, `role`·`mod` 선례). 신규 7종 코드값은 enum 상수로만 관리 — 표준 단어/비즈니스 약어 미등재(기존 PWTF/DWT enum 코드값 관리 선례). `facilityGroupCd` 는 DB 컬럼 아닌 DTO 계산 필드 → standard-terms.md 미등재.
- 사용자 확정: 7종 코드값 glossary 권장안 채택 (AskUserQuestion 2026-06-08).
- **결론**: 12종 코드값 확정(기존 5 + 신규 7). `group` 단어 신규 등록. 7종 코드·`facilityGroupCd`·`FacilityGroup` 은 사전 미등재(enum/DTO 전용). `facility_type_cd` 행 비고만 갱신.

### 안건 2: V2_1 patch DDL — 자식 테이블 7종 skeleton + FK 정책 + COMMENT
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: **블로커** — 신규 7종 자식 테이블 FK 는 `ON DELETE RESTRICT` 명시 통일 필요. 근거: instrument_m·rawdata_1m_h 가 facility_id 논리 참조 + 마스터 영구 보존(논리 삭제만) 원칙 → CASCADE 발동 시나리오 없음, RESTRICT 가 논리 삭제 정책의 DB 이중 안전망. point_m·prsf_m 선례 일치. 자식 skeleton 은 PK=FK 단일 인덱스로 충분(추가 인덱스 불요). facility_type_cd COMMENT 는 `COMMENT ON COLUMN`(락 없음)으로 patch 단독 선언 가능 — 단 12종 나열 시 enum 과 이중 관리 드리프트 우려 → "FacilityType enum SSOT 참조" 병기 권고(중간). 파생 그룹 IN(...) 쿼리는 idx_facility_m_facility_type_cd 활용 가능, 그룹 전체 IN 시 Seq Scan 가능성은 PLAN EXPLAIN 검증(참고). facility_group_cd 파생 결정 타당 — materialize 트리거는 멀티테넌트 수만 건 + 그룹필터 500ms 초과 슬로우쿼리 시점뿐.
- **결론**: 신규 7종 FK `ON DELETE RESTRICT` 명시(블로커 해소). COMMENT 12종 나열 + "FacilityType enum SSOT" 병기. 자식 추가 인덱스 없음. 파생 그룹 유지, 향후 트리거 가정 기재.

### 안건 3: 도메인 4영역 점검 + 그룹↔필터강제 룰 경계
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 4영역(알람·인터록·AI운전모드·이력) 모두 **본 사이클 비해당** — skeleton 모델 + 등록 API 만, 계측기/SCADA/제어 경로 없음. 단 송수동(송수펌프 보유)·PRSF(이미 AI운전모드 별도평가 대상)는 향후 펌프/제어 편입 가능성 → 가정 기재. 그룹↔필터강제 충돌은 "그룹 자체"가 아니라 "그룹 조회 결과를 도메인 룰 평가에 투입하는 코드"에서 발생: **전력 집계·화면 표출·목록 조회는 그룹 조회 허용 / 인터록·AI운전모드·제어 평가는 `facility_type_cd` 명시 필터 강제**. UUID 자동생성 facility_id 는 향후 인터록 평가 단위(facility_id FK) 와 구조 충돌 없음. 송수동 운영시설 분류는 전력 소비 관점 타당, 단 계통 흐름 모델 필요 시 후속 재설계(미해결).
- **결론**: 4영역 비해당(사유 명기). 가정 섹션에 그룹조회 경계 + 송수동/PRSF 향후 편입 가능성 기재. 블로커 0.

### 안건 4: 백엔드 패턴 — enum 생성자·12-way 다형성·파생 DTO 필드·skeleton
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: enum 생성자 필드(`FacilityGroup group`) 추가는 `@Enumerated(STRING)`(name 만 저장)이라 DB 영향 없음 — getGroup() 패턴. 파생 facilityGroupCd 는 applyCommonFields() 에서 `getFacilityType().getGroup()` 으로 채우고 `@Schema(implementation=FacilityGroup.class)` 명시. 3단 상속(BaseAuditResponseDto→FacilityDto→자식) 은 마스터 다형성 3단 허용 충족. **블로커(검증)** — `from()`/save·update switch `default` 분기는 sealed 미사용으로 exhaustiveness 미보장 → 7종 case 누락 시 런타임 IllegalStateException. TASK 체크박스에 "신규 유형 직접 테스트로 예외 미발생 확인" 검증기준 명시 의무(§4.1). 다형성 4곳(@JsonSubTypes×2 + @Schema(oneOf)×2) 동시 갱신 누락 방지(개별 체크박스). 엔티티/요청DTO=common, 응답DTO=api 배치 정합. FacilityService 50줄 초과 시 — 도메인 안전 직결 아니라 §2.5 면책 비해당, 초과 시 일반 권고.
- **결론**: enum 생성자 도입 안전. 12-way 다형성 4곳 + switch 3곳(from/save/update) 갱신. TASK 에 switch case 검증기준·QClass 빌드검증·4곳 동시갱신 개별 체크박스 의무. skeleton 보일러플레이트는 JOINED 구조상 불가피(권고).

---

## 표준 사전 카탈로그

### 신규 표준 단어
(1차 정의: `swtp/.claude/rules/dict/standard-words.md` — ROOT)

| 영문 약어 | 한글 논리명 | 분류 | 결정 근거 |
|----------|-----------|------|----------|
| `group` | 그룹 | 신규 | `standard-words.md` 미등재. DTO `facilityGroupCd` 조합 재료(FacilityGroup 파생 노출). `role`·`mod`·`div` 코드 조합 단어 선례 동형. 풀네임 5자 — `drive`·`start`·`event` 선례 정합. 기본 데이터 도메인 `DOM_CODE_20` |

### 신규 표준 데이터 도메인
없음 — `FacilityGroup` 은 Java enum, DB 컬럼 미신설(파생). 신규 SQL 타입·길이 조합 없음.

### 신규 표준 용어 (DB 컬럼명)
없음 — `facilityGroupCd` 는 DB 컬럼이 아닌 DTO 계산 필드이므로 `.claude/rules/dict/standard-terms.md` 등재 대상 외. 신규 7종 facility_type_cd 코드값은 `FacilityType` enum 상수로만 관리(기존 PWTF/DWT 선례).

> 기존 `facility_type_cd` 행은 **비고 갱신**(12종 + 그룹 개념). 신규 컬럼 아님.

분류값 적용: 신규 단어 1 / 기존 재사용 0 / 유사 충돌 0(DWBLD→DEWB 회피) / 폐기·통합 0.

---

## 신규 엔티티/DB 컬럼

### enum (common 모듈)
- **신규** `FacilityGroup` (`com.mo.swtp.facility.domain.enumtype`) — STORAGE(저장시설)/OPERATION(운영시설)/NETWORK(계통시설) + 한글 설명 필드
- **수정** `FacilityType` — 생성자 필드 `FacilityGroup group`(+한글 설명) 추가 + `getGroup()`. 기존 5종 그룹 매핑 + 신규 7종 추가

### 그룹 매핑 (확정)
| FacilityGroup | facility_type_cd |
|---------------|------------------|
| STORAGE | PWTF, DWT, RSV |
| OPERATION | PRSF, WTBLD, CHMB, ACFB, POZB, FLTB, DEWB |
| NETWORK | POINT |

### 신규 자식 시설 7종 (JPA JOINED skeleton — 자식 전용 컬럼 0건)
| 한글 | facility_type_cd | 자식 테이블 | 엔티티 클래스(common, 제안) | 응답 DTO(api) | 요청 DTO(common) |
|------|------|------|------|------|------|
| 송수동 | WTBLD | `wtbld_m` | WaterTransmissionBuilding | WtbldDto | WtbldUpsertDto |
| 약품동 | CHMB | `chmb_m` | ChemicalBuilding | ChmbDto | ChmbUpsertDto |
| 활성탄여과지 | ACFB | `acfb_m` | ActivatedCarbonFilter | AcfbDto | AcfbUpsertDto |
| 전오존동 | POZB | `pozb_m` | PreOzonationBuilding | PozbDto | PozbUpsertDto |
| 여과지동 | FLTB | `fltb_m` | FiltrationBuilding | FltbDto | FltbUpsertDto |
| 탈수기동 | DEWB | `dewb_m` | DewateringBuilding | DewbDto | DewbUpsertDto |
| 태양광 | SOLAR | `solar_m` | SolarPowerFacility | SolarDto | SolarUpsertDto |

- 자식 테이블 DDL: `CREATE TABLE {suffix}_m (facility_id VARCHAR(36) NOT NULL, PK, FK → facility_m ON DELETE RESTRICT)` + `COMMENT ON COLUMN`. 인덱스 추가 없음(PK=FK 단일).
- `facility_m` 구조 변화 없음 — `facility_type_cd` COMMENT 만 12종 + 그룹 개념으로 갱신.
- 응답 DTO 파생 필드 `facilityGroupCd`(FacilityGroup) — `FacilityDto`(부모) 공통 필드로 추가, `applyCommonFields()` 에서 계산.

### DDL 파일 (§5.3 양쪽 동시 갱신)
- **신규** `common/src/main/resources/db/migration/V2_1__facility_patch.sql` — 7 자식 테이블 + COMMENT + facility_type_cd COMMENT 갱신
- **수정** `backend/docs/ddl/facility.sql` — 동일 내용 누적

---

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 해소책 (회의 결론) |
|----------|------------------|
| DWBLD(탈수기동 후보) ↔ DWT(배수지) DW 2자 공유 | `DEWB` 채택 (glossary 권고, 사용자 확정) |
| facility_type_cd COMMENT 12종 나열 ↔ enum 이중 관리 드리프트(DBA 중간) | COMMENT 에 12종 나열 + "FacilityType enum SSOT 참조" 병기 |
| 그룹 조회 ↔ `facility_type_cd` 필터 강제 룰(domain) | 그룹 조회는 전력집계·표출·목록만 허용 / 인터록·AI운전모드·제어 평가는 type 필터 강제 (가정 섹션 명기) |
| FK ON DELETE 정책 혼재(DBA 블로커) | 신규 7종 `ON DELETE RESTRICT` 명시 통일 |

---

## PLAN 으로 전달할 결정 사항

- **도메인 모델 초안**: `FacilityGroup` enum 신규 + `FacilityType` 생성자(group) 도입. 신규 7종 = Facility 추상 부모 상속 JOINED skeleton 엔티티 7 + 응답 DTO 7 + 요청 DTO 7.
- **DB 설계 변경 초안**: `V2_1__facility_patch.sql` (7 자식 테이블 skeleton, FK ON DELETE RESTRICT, COMMENT 의무) + facility_type_cd COMMENT 갱신. `docs/ddl/facility.sql` 동시 갱신. facility_m 구조 무변경.
- **적용 패턴**: JPA JOINED + DiscriminatorColumn(entity-patterns.md), 3단 상속 응답 DTO + Jackson 다형성(api-patterns.md §BaseAuditResponseDto), 파생 DTO 필드 `@Schema(implementation)`, 등록 API 다형성 switch 확장.
- **다형성 4곳 + switch 3곳 동시 갱신**: `FacilityDto`(@JsonSubTypes·@Schema oneOf·from() switch), `FacilityUpsertDto`(@JsonSubTypes·@Schema oneOf), `FacilityService`(saveFacility·updateFacility switch).
- **TASK 검증기준 의무**: 신규 유형 직접 등록/조회 테스트로 switch default IllegalStateException/FACILITY_TYPE_MISMATCH 미발생 확인, `FacilityType` 12종 그룹 매핑 단위 테스트, QClass 재생성 빌드(`:common:build :api:build`).
- **제외(후속/섹션)**: 그룹 기준 조회 필터(FacilitySearchDto.facilityGroupCd), 자식 전용 컬럼, 계측기·전력 태그 매핑, 1~6번 섹션 API.

---

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 멀티테넌트 배포 내에서 동일 facility_type_cd 의 그룹은 1:1 불변 → 파생(enum 속성) 으로 충분, 컬럼 미저장 | 가정 | 향후 type 무관 그룹 재분류 요건 발생 시 컬럼 materialize 재검토(DBA 트리거: 수만 건 + 그룹필터 500ms 초과) |
| 신규 7종 skeleton 에 계측기·전력 태그 미연결 동안 알람·인터록·AI운전모드·이력 4영역 비해당 | 가정 | 계측기 부착 사이클 착수 시 4영역 재분류 필요 |
| 그룹 조회(`group=OPERATION`)는 전력집계·화면표출·목록조회만 허용, 인터록/AI운전모드/제어 평가는 `facility_type_cd` 명시 필터 강제 | 가정 | 후속 사이클 PLAN 에서 도메인 룰 평가 서비스 제약 명기 의무(domain-expert 권고) |
| 송수동·가압장(PRSF) 운영시설 분류는 전력 소비 관점. 향후 AI운전모드 평가 대상 편입 또는 계통 흐름 모델 필요 시 별도 슬러그 재설계 | 미해결 | PRSF 는 ot-integration.md §5 AI운전모드 별도평가 기등재 — 송수동 편입 여부 후속 결정 |
| 신규 7종 자식 테이블 자식 전용 컬럼 0건 — 향후 컬럼 추가 시 `V2_{연번}__facility_patch.sql` 분리(V2_1 동결) | 가정 | db/indexing-and-migration.md §5.4 정합 |

---

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `FacilityType` 12종 전부 그룹 매핑 보유 | 단위 테스트 — 저장 3·운영 8·계통 1 매핑 검증 GREEN |
| 신규 유형(예: 약품동 CHMB) 등록·조회 성공 + 응답 facilityGroupCd=OPERATION | FacilityService 통합/단위 테스트 GREEN, switch default 미진입 확인 |
| 12-way 다형성 직렬화/역직렬화 정상 | FacilityDto/FacilityUpsertDto 다형성 테스트 GREEN (7종 각 case) |
| 자식 테이블 7종 COMMENT 누락 0 | check-ddl-column-comment.sh 통과 + ./gradlew.bat build BUILD SUCCESSFUL |
| V2_1 patch ↔ docs/ddl/facility.sql 동기화 | REVIEW git diff 교차 점검 일치 |

---

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 본 사이클은 skeleton 모델 + 등록 API 만. 계측기 부착·SCADA 수신·임계값 설정 경로 없음. 향후 전력 태그(PWI/PWQ) 매핑 시 적용 대상 전환(가정 기재) |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 아웃바운드(PLC 제어) 미포함. 송수동 소속 Pump 기동은 후속 범위. UUID 자동생성 facility_id 는 향후 인터록 평가 단위(facility_id FK) 와 구조 충돌 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_p` 행 생성 없음. PRSF 는 §5 별도평가 기등재이나 본 사이클 신규 7종은 연결 없이 skeleton 만 도입(향후 편입 가능성 가정 기재) |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | 운전 명령 발행 경로 없음. `ai_drvn_mod_h`·`pump_ctrl_h` 비접촉 |

> "비해당" 단독 차단 미해당 — 각 행 구체 사유 명기 + "## 신규 엔티티/DB 컬럼" 섹션에 신규 자산(enum·자식 7종·DDL) 내용 존재.

---

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `group`(그룹, group, `DOM_CODE_20`) 신규 등록. DTO `facilityGroupCd` 조합 재료, `FacilityGroup` 파생 노출, 풀네임 5자 (시설_도메인_확장 ANALYZE1 안건 1·3, 2026-06-08)
- [x] `.claude/rules/dict/standard-terms.md` — `facility_type_cd` 행 비고 갱신: 12종(PWTF·DWT·RSV·POINT·PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR) 열거 + FacilityGroup 3종 그룹 매핑 + DB 컬럼 미신설(파생) 사유 + FacilityType enum SSOT 병기

> ROOT 어휘(`standard-data-domains.md`·`domain-abbreviations.md`)·`naming.md`·`entity-patterns.md` 변경 없음 (신규 데이터 도메인·비즈니스 약어·suffix·패턴 도입 없음 — 기존 JOINED 패턴 재사용).

## 산출물
- [계획안](../../../plan/20260608/시설_도메인_확장/PLAN1.md)
