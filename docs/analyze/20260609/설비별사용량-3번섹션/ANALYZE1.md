---
status: approved
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 3번섹션 — 전력 설비(계측기) 목록 조회 도메인 분석

## 작업 배경

`backend/image/설비별사용량.png` 대시보드의 **3번섹션**(설비목록 패널)을 구현한다. 2번섹션(`GET /api/facility/energy-usage`)에서 운영시설 목록을 조회한 뒤, front 가 그 중 한 시설을 선택하면 **시설 ID 를 파라미터로** 보낸다. 본 API 는 그 시설을 루트로 `parent_facility_id` self-FK 재귀 하위 시설 트리 전체를 탐색해, 트리의 계측기(instrument) 중 **전력관련 태그(PWI 순시전력 / PWQ 적산전력량)를 1개 이상 보유한 설비 목록**을 반환한다. 이 목록이 5/6/7 섹션(설비별 통계·비율·순시전력 차트)의 입력이 된다. **읽기 전용** API 로 측정값(시계열)은 반환하지 않고 설비 메타(id·이름·종류·소속시설·보유 전력태그)만 반환한다.

예: `생활송수동`(WTBLD, 운영시설) ID 를 보내면 → 생활송수동 직속 설비 중 전력태그 보유 설비 + 생활송수동을 상위로 하는 모든 재귀 하위 시설의 설비 중 전력태그 보유 설비를, 더 이상 하위 시설이 없을 때까지 탐색해 모아 반환한다.

- 외부 산출물: `backend/image/설비별사용량.png` (3번섹션 — 설비목록 패널, 시각 로드 완료)
- 사용자 사전 확정 사항 (plan 단계 AskUserQuestion, 2026-06-09):
  1. **전력관련 태그 = PWI ∪ PWQ** — 둘 중 하나라도 보유한 설비 포함 (2번섹션 `TARGET_TAG_TYPES` 와 동일 멤버십).
  2. **응답 구조** — 설비 평면 목록 + 보유 전력태그 상세 (설비별 `instrumentId·instrumentNm·equipTypeCd·facilityId·facilityNm` + `tags[]`(각 `tagSrlNo·tagSeCd`)).
  3. **루트 시설 종류 제한 없음** — 활성 시설이면 종류 무관 수용, 미존재·비활성만 404.
- 사용자 네이밍 결정 (analyze 단계 AskUserQuestion, 2026-06-09):
  4. **`Instrument` 명명 채택** — `FacilityPowerInstrumentService`/`FacilityPowerInstrumentDto`, 엔드포인트 `/api/facility/{facilityId}/power-instruments` (UI 용어 'equipment' 코드 혼입 방지).
  5. **전력태그 목록 필드명 `tags`** — ROOT 표준단어 사전 변경 0건 (`power` 단어 미등록 — `elpwr`·`elceg` 유사 충돌 회피).

> 메모리 `feedback_no_auto_reuse_cross_cycle.md` / `feedback_existing_decisions_reevaluation.md` 정합 — 2번섹션 자산(BFS·태그필터 패턴) 재사용·네이밍은 모두 사용자 명시 결정으로 확정. 기존 코드 자동 원용 없이 신규 자산을 구조만 미러링.

## 회의록 (5인 회의)

### 안건 1: 신규 식별자 어휘 정합성 + `power`/`equipment` 네이밍
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: 신규 DB 테이블·컬럼·데이터 도메인 0건 전제 → backend `standard-terms.md` 등록 대상 원천 없음. 식별자 `instrumentId·instrumentNm·equipTypeCd·facilityId·facilityNm·tagSrlNo·tagSeCd` 전부 기존 등록 어휘 재사용(7건). **① `power` 단어**: `elpwr`(순시전력)·`elceg`(전력량 누적)와 어근 공유 — `nm` vs `name` 류 **유사 충돌** 소지. (a) `powerTags`+`power` DTO 전용 단어 등록(경계 명기) vs (b) `tags` 리네임(신규 단어 0) 제시. **② `equipment` vs `instrument`**: 코드베이스 엔티티·비즈니스 약어가 `instrument` SSOT 이므로 UI 용어 `equipment` 코드 혼입은 이중 소스 혼란(`pump_m.tag_nm` 양방향 중복 폐기 선례) → `Instrument`/`instruments` 권고.
- Round 2 (사용자 결정): **`Instrument` 명명 채택** + **필드명 `tags` 채택** → `power` 단어 미등록. ROOT 어휘 변경 0건.
- **결론**: 신규 표준 단어/데이터 도메인/표준 용어 **0건**, 룰 갱신 지시서 **0건**. 모든 어휘 기존 재사용. 클래스/엔드포인트는 `instrument` 일관 명명.

### 안건 2: 재귀 BFS 재사용 전략 + 계층 책임/정량 기준
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: 블로커 0·권고 0·참고 4건. **① 재귀 BFS** — (a) 신규 Service 내 동형 복제 권고. 단일루트 subtree 사본은 `FacilityDownstreamTreeResolver.collectSubtree`(private) 1건뿐이며 `FacilityOperatingRollupResolver`(멀티루트 경계종단)와 의미 상이. 3번째 사본이 생겨도 후처리 로직이 상이(단일루트-하향 / 멀티루트-경계종단 / 단일루트-instrument조합)하여 공유 추출 시 분기 파라미터 과잉 위험이 큼 → `coding-discipline.md §2·§3` + `FacilityOperatingRollupResolver` 동형복제 선례 정합. **② 정량 기준** — 7단계 흐름을 단일 public 메서드에 담으면 50줄 임박. `collectSubtree`/`filterPowerInstruments`/`assembleDtos` private 헬퍼 3분리 권장(`FacilityStateService` 3헬퍼 선례). **③ `@ArraySchema`** — `List<PowerTagDto>` 필드에 `@ArraySchema(schema=@Schema(implementation=PowerTagDto.class))` 의무. **④ `findActiveFacilityOrThrow` 동형복제** — 현 사본 2건(계획 포함), 공유 임계(3건) 미달 → 복제 허용, RESULT 에 "3건 누적 시 `FacilityValidator` 추출 검토" 기록 권장.
- **결론**: BFS 동형 복제(a) + private 헬퍼 3분리 + `@ArraySchema` 명시 + BaseAuditResponseDto 미상속 패턴 승인. 블로커·권고 0건.

### 안건 3: 도메인 규칙 정합성 (4영역 + 전력태그 정의 + 필터 강제)
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 블로커 0·권고 1·참고 1건. **① 도메인 4영역** — 알람 4단계·인터록·AI 운전 모드·이력 기록 의무 모두 제어 발행/모드 전환/이력 INSERT 경로 결속 → 읽기 전용 GET 메타 조회는 **전부 비해당**(각 사유 명기). **② PWI∪PWQ** — `TagMeasurementType` 10종 중 전력 의미는 PWI(kW)·PWQ(kWh) 2종, 2번섹션 멤버십과 일치 → 타당. **권고**: FQI(주파수)는 인버터 전력 간접 지표이나 본 API 범위 외임을 가정 섹션 명기(향후 범위 혼선 방지). **③ 종류 무필터** — `facility_type_cd`/`equip_type_cd` 필터 강제 룰은 "자식 종류별 도메인 룰이 다른 시나리오(제어·평가)"에만 적용. 본 API 는 "전력 태그 보유"라는 단일 물리 사실 기준 목록 조회이므로 종류 무필터 정합(2번섹션 `findByFacilityFacilityIdInAndUseYn` 선례). 전력 계측기 없는 종류(SOLAR/WTBLD 등)는 결과 자연 제외. **④ use_yn=Y 3계층 활성 필터** — 철거·퇴역 설비·SCADA 수집 중단 태그 노출 방지로 타당. **참고**: `instrument_m.use_yn` DDL 존재 여부 PLAN 확인.
- **결론**: 도메인 4영역 전부 비해당·PWI∪PWQ 정합·종류 무필터 정합·활성 필터 타당. FQI 제외 사유 가정 섹션 명기.

### 안건 4: DB 쿼리·인덱스 (스키마 변경 0)
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 블로커 0·권고 0·참고 3건. 신규 테이블·컬럼·인덱스·데이터 도메인 **0건** → DBA 2차 승인 대상 없음. **① `parent_facility_id` 인덱스** — `docs/ddl/facility.sql` 확인 결과 `idx_facility_m_parent_type_yn (parent_facility_id, facility_type_cd, use_yn)` 복합 인덱스 **이미 존재**. BFS 패턴 `parent_facility_id IN (...) AND use_yn='Y'` 가 선두 컬럼 등가 IN 으로 사용 → 인덱스 프루닝 정상. `facility_m` 소량 마스터로 추가 인덱스 불필요. **② MAX_DEPTH=10** — self-FK 는 DB 레벨 순환 미차단(ON DELETE 정책 미명시), 애플리케이션 visited-set 이 유일 방어선 → MAX_DEPTH=10 백스톱 반드시 유지(정수장 계층 도달 불가 깊이 = 순환 트립와이어). **③ N+1** — SQL = 루트 findById(1) + BFS(깊이 N) + Instrument IN(1) + Tag IN(1). 시설 수 무관, 깊이만 비례. 루프 내 개별 조회 없음 → 위반 없음.
- **결론**: 스키마 변경 0·기존 복합 인덱스 활용·신규 인덱스 불필요·MAX_DEPTH 유지·N+1 없음·DBA 2차 승인 대상 없음 확인.

## 표준 사전 카탈로그

### 신규 표준 단어
**없음** — `instrument`·`facility`·`tag`(비즈니스 도메인 약어) + `nm`·`srl`·`no`·`se`·`cd`·`equip`·`type`·`id`(표준 단어/조합) 전부 기존 등록. `power` 단어는 사용자 `tags` 필드명 채택으로 미등록(`elpwr`·`elceg` 유사 충돌 회피).

### 신규 표준 데이터 도메인
**없음** — DB 컬럼 신설 없음.

### 신규 표준 용어
**없음** — DTO Java 필드명(`tags` 등)은 DB 컬럼이 아니므로 `standard-terms.md` 등록 의무 없음(`hr`·`actl`·`inq`·`facilityGroupCd` DTO 전용 변수명 미등록 선례 동형).

## 신규 엔티티/DB 컬럼

**없음** — 읽기 전용 조회 API. 신규 엔티티·DB 테이블·DB 컬럼·인덱스 **0건**. 추가 산출물은 (1) `FacilityPowerInstrumentService` 신규 1개, (2) `FacilityPowerInstrumentDto`(+ 중첩 `PowerTagDto`) 신규 1개, (3) `FacilityController` 엔드포인트 1개 추가, (4) `FacilityPowerInstrumentServiceTest` 단위 테스트 1개. 기존 리포지토리 메서드(`FacilityRepository.findByParentFacilityIdInAndUseYn`·`InstrumentRepository.findByFacilityFacilityIdInAndUseYn`·`TagRepository.findByInstrumentInstrumentIdInAndUseYn`)·enum(`TagMeasurementType.PWI/PWQ`·`EquipType`)·`FacilityErrorCode.FACILITY_NOT_FOUND` 전부 변경 없이 재사용.

## 기존 사전·패턴과의 충돌

| 충돌 후보 | 판정 | 해소책 |
|----------|------|--------|
| `power` 단어 vs `elpwr`/`elceg` 어근 공유 | 유사 충돌 소지 | 사용자 `tags` 필드명 채택으로 `power` 미등록 — 충돌 원천 회피 |
| `equipment`(UI) vs `instrument`(코드 SSOT) | 이중 소스 혼란 소지 | `Instrument` 명명 채택 — 클래스/엔드포인트 `instrument` 일관 |
| `facility_type_cd`/`equip_type_cd` 필터 강제 룰 vs 종류 무필터 | 위반 아님 | 본 API 는 "전력 태그 보유" 단일 물리 사실 기준 목록 조회 — 자식 종류별 도메인 룰 분기 없음. 2번섹션 종류 무필터 선례 동형 |
| 재귀 BFS 3번째 동형 복제 vs 공유 추출 | 중복(참고) | `coding-discipline.md §2·§3` — 후처리 상이로 공유 추출 시 분기 과잉. 동형 복제 채택(`FacilityOperatingRollupResolver` 선례). RESULT 발견사항 기록 |
| `findActiveFacilityOrThrow` 동형 복제 (2건) | 중복(참고) | 공유 임계(3건) 미달 → 복제 허용. RESULT 에 "3건 누적 시 `FacilityValidator` 추출 검토" 기록 |

## PLAN 으로 전달할 결정 사항

**도메인 모델**: 신규 엔티티 없음. 멤버십 = (루트 시설 inclusive) + `parent_facility_id` self-FK 재귀 하위 전체 → 그 트리의 활성 계측기 중 PWI∪PWQ 태그 ≥1 보유 설비.

**API 구조** (facility 도메인, `api` 모듈):
- 신규 `FacilityPowerInstrumentService` (`@Service @RequiredArgsConstructor @Transactional(readOnly=true)`). 의존: `FacilityRepository·InstrumentRepository·TagRepository`. 상수 `POWER_TAG_TYPES = EnumSet.of(PWI, PWQ)`, `MAX_DEPTH = 10`. public `findPowerInstruments(String facilityId)` 메서드 + private 헬퍼 3분리(`findActiveFacilityOrThrow`·`collectSubtree`·조립/필터). 흐름: 활성 루트 검증 → BFS subtree 수집(레벨당 `findByParentFacilityIdInAndUseYn(parentIds, Y)`, visited-set + MAX_DEPTH) → `Map<facilityId,Facility>` (facilityNm/dispOrd lookup, N+1 회피) → `findByFacilityFacilityIdInAndUseYn(facilityIds, Y)` → `findByInstrumentInstrumentIdInAndUseYn(instrumentIds, Y)` + `EnumSet.of(PWI,PWQ)` 필터 → instrumentId groupingBy → 전력태그 보유 설비만 정렬 후 DTO 조립.
- 신규 `FacilityPowerInstrumentDto` (`@Getter` + private 생성자 + 정적 팩토리 `of(...)`, BaseAuditResponseDto 미상속 — `FacilityEnergyUsageDto`·`FacilityStateDto` 선례). 필드: `instrumentId·instrumentNm·equipTypeCd(EquipType, @Schema(implementation))·facilityId·facilityNm·tags(List<PowerTagDto>, @ArraySchema)`. 중첩 정적 `PowerTagDto{tagSrlNo, tagSeCd(TagMeasurementType, @Schema(implementation))}`.
- `FacilityController` — `@GetMapping("/{facilityId}/power-instruments")` → `ResponseEntity<CommonResponseDto<List<FacilityPowerInstrumentDto>>>`. `@Tag("06. 시설물 관리")` 유지, `@Operation`/`@ApiResponses`(200/400/401/403/404/500, 404=FACILITY_NOT_FOUND).

**DB 설계 변경**: **없음**(스키마 변경 0). `docs/ddl/` 갱신 불필요.

**적용 패턴**: 2번섹션(`FacilityEnergyUsageService` 시설→계측기→태그 IN + 인메모리 필터) + 8번섹션(`FacilityDownstreamTreeResolver.collectSubtree` BFS) + 송수펌프 3번섹션(`FacilityStateService.findActiveFacilityOrThrow`) 구조 미러링(자산 자동 원용 없이 신규 작성).

**정렬**: 시설 `disp_ord` ASC → 계측기 `disp_ord` ASC → `instrument_nm` ASC.

**테스트**: `FacilityPowerInstrumentServiceTest` Mockito 단위 테스트 — 미존재/비활성 루트→FACILITY_NOT_FOUND / 재귀 하위 설비 포함(BFS) / 전력태그 무보유 설비 제외 / PWI+PWQ 동시 보유→1행 2태그 / 비전력 태그(FRI 등) 제외 / 설비 0건→빈 목록 / facilityNm 매핑 정확성 / (선택) visited-set 순환 방어.

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 전력관련 태그 = PWI ∪ PWQ 2종. **FQI(주파수)는 인버터 전력 간접 지표이나 본 API 범위 외로 결정** | 결정 | 2번섹션 멤버십 일치. 향후 주파수 보유 설비 목록 요청 시 별도 범위 논의(domain-expert 권고) |
| 루트 시설 inclusive — 루트 자신의 설비도 포함 | 결정 | 사용자 명시("그 시설을 바라보는 설비들 + 하위 시설 설비들") |
| 시설·계측기·태그 3계층 모두 `use_yn=Y` 활성만 | 결정 | 철거·퇴역 설비·수집 중단 태그 노출 방지. `instrument_m.use_yn` 존재 확인 — 기존 `InstrumentRepository.findByFacilityFacilityIdInAndUseYn(List, YnType)` 컴파일 사용 중이므로 존재 확정(domain-expert 참고 해소) |
| 루트 시설 종류 무제한 — 활성이면 수용, 미존재·비활성만 404 | 결정 | 사용자 선택. 2번섹션에서 운영시설을 선택하나 API 는 재귀 하위 탐색 일반화 |
| 계측기 종류 무필터 — 전력태그 보유 여부로만 필터 | 결정 | "전력 태그 보유" 단일 물리 사실 기준. 종류별 도메인 룰 분기 없음(domain-expert 정합 판정) |
| 재귀 BFS 는 신규 Service 내 동형 복제(기존 resolver 미수정) | 결정 | `coding-discipline.md §3` — 기존 코드 직접 수정 금지. 사본 누적은 RESULT 발견사항 보고 |
| MAX_DEPTH=10 백스톱 유지 (visited-set 1차 방어) | 결정 | DB self-FK 순환 미차단 → 앱 레벨 방어 필수(DBA) |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| 미존재/비활성 루트 → FACILITY_NOT_FOUND | 신규 단위 테스트 — `findById` empty / `useYn=N` 케이스 각각 RestApiException(FACILITY_NOT_FOUND) GREEN |
| 재귀 하위(자식·손자) 설비 중 전력태그 보유분 포함 | 신규 단위 테스트 — 루트+2단 트리 mock, 하위 계측기 PWI 보유분 응답 포함 GREEN |
| 전력태그 무보유 설비 제외 + PWI+PWQ 동시 보유 1행 2태그 | 신규 단위 테스트 — FRI만 보유 계측기 제외 / PWI+PWQ 계측기 tags size 2 GREEN |
| 설비/태그 0건 → 빈 목록(예외 아님) | 신규 단위 테스트 — instruments empty 시 `List.of()` 반환 GREEN |
| 빌드 성공 | `./gradlew.bat :api:test` + `./gradlew.bat clean build` BUILD SUCCESSFUL |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 설비 메타 읽기 전용 조회 — 알람 임계값·전이·복귀 무접촉, `alarm_h` 기록 없음 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | GET 조회만 — 기동 명령 발생 경로 없음, 선행조건 검사·제어 명령 발행 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 변경 없음, 강제 전환 트리거 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h`·`pump_ctrl_h` 무접촉. facility/instrument/tag 마스터 조회 전용(쓰기 없음) |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

**없음** — 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 도메인 약어 등록 0건, DB suffix·엔티티 패턴 변경 0건, DB 스키마 변경 0건. 모든 어휘가 기존 등록 항목 재사용이며, 사용자 `tags` 필드명·`Instrument` 명명 채택으로 `power`/`equipment` 신규 어휘 등록이 모두 회피되었다(`wtp-glossary-manager` 안건 1 확정 — 신규 등록 항목 0건).

## 산출물
- [계획안](../../../plan/20260609/설비별사용량-3번섹션/PLAN1.md) (다음 단계 `/dev:plan` 에서 작성)
