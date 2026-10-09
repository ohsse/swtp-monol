---
status: approved
created: 2026-06-09
updated: 2026-06-09
---
# 설비별사용량 2번섹션 — 운영시설 목록 조회 도메인 분석

## 작업 배경

`backend/image/설비별사용량.png` 대시보드의 **2번섹션**(좌측 "시설 현황" 패널)을 구현한다. 시설 중 **운영시설(`FacilityGroup.OPERATION` 8종: PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR)** 목록을 조회하는 **읽기 전용 API** 다. 현재 `GET /api/facility`(`FacilitySearchDto`)는 단일 `facilityTypeCd` 만 필터하고 **그룹 단위 필터가 없어** 운영시설 묶음 조회가 불가하다. 본 작업은 그룹 필터를 추가해 이 공백을 메운다.

- 외부 산출물: `backend/image/설비별사용량.png` (2번섹션 — 시설 현황 좌측 nav 목록, 시각 로드 완료)
- 사용자 사전 확정 사항 (계획 단계 AskUserQuestion, 2026-06-09):
  1. **범위** — 본 섹션은 운영시설 목록 조회만. 기존 `시설별사용량` 대시보드(2번섹션=전력 사용량, 구현 완료 `GET /api/facility/energy-usage`)와의 관계는 **이후 섹션에서 재논의**(현 섹션 미고려).
  2. **구현 방식** — 기존 엔드포인트 필터 확장(`hasPump` 선례 동형). 전용 엔드포인트 신설 안 함.
  3. **응답 DTO** — 기존 다형성 `FacilityDto` 재사용.

> 메모리 `feedback_no_auto_reuse_cross_cycle.md` 정합 — 재사용(필터 확장·`FacilityDto`)은 사용자 명시 결정으로 확정.

## 회의록 (5인 회의)

### 안건 1: `facilityGroupCd` 필터 어휘 정합성
- 호출 에이전트: `wtp-glossary-manager`
- Round 1 답변 요약:
  - **wtp-glossary-manager**: `facilityGroupCd` 구성 단어 `facility`(비즈니스 도메인 약어) + `group`(`standard-words.md` 98행, 2026-06-08 시설_도메인_확장 등록, 비고에 "응답 DTO `facilityGroupCd` 조합 재료, DB 컬럼 미신설" 명기) + `cd`(26행, 2026-04-23) 전부 기존 등록 → 신규 표준 단어 0건. `facilityGroupCd` 는 DB 컬럼이 아닌 검색 DTO 필드 → `standard-terms.md` 등록 의무 없음(`hr`·`actl`·`inq` DTO 전용 단어 선례 동형). `typesOf` Java 메서드명은 어휘 사전 관리 대상 외. 분산 사전 충돌 0건.
- **결론**: 신규 표준 단어/데이터 도메인/표준 용어 **0건**, 룰 갱신 지시서 **0건**. 모든 어휘 기존 재사용.

### 안건 2: 필터 확장 구현 패턴 + `typesOf` SSOT 배치
- 호출 에이전트: `wtp-backend-engineer`
- Round 1 답변 요약:
  - **wtp-backend-engineer**: `FacilitySearchDto` 신규 필드 `@Schema(implementation = FacilityGroup.class)` 명시는 `api-patterns.md §DTO @Schema(implementation) 명시 패턴` 준수. `findFacilities()` BooleanBuilder 분기 추가는 기존 `facilityTypeCd`·`useYn`·`hasPump` 분기와 완전 동형(`hasPump` 선례 2026-05-13 정합). `FacilityService` 변경 없음 — Repository 레벨 필터 확장이므로 계층 책임 분리 정합. `typesOf`(FacilityType, common 모듈)는 도메인 enum 내 순수 정적 헬퍼 → `common/CLAUDE.md` 허용 범위, api 호출 가능, 하드코딩 방지 효과 명확. 정량 기준(메서드 50줄/추상화 3단/DTO 상속 3단) 위반 없음. **블로커(높음) 1건** — 현재 `FacilityType.java` 에 `typesOf` 미존재 → 미구현 시 `FacilityCustomRepositoryImpl` 신규 분기 컴파일 오류. **권고(중간) 1건** — `FacilityEnergyUsageService` 의 OPERATION 인라인 도출(`Arrays.stream(...).filter(t -> t.getGroup()==OPERATION)`)과 `typesOf` 중복은 `coding-discipline.md §3` 정밀 수정 원칙상 직접 수정 금지, RESULT 발견사항 보고만. **참고(낮음) 1건** — `typesOf` 순수 함수 단위 테스트 1건 권고.
- **결론**: 필터 확장 패턴·`typesOf` common 배치 승인. 블로커는 "계획된 신규 자산(`typesOf`)을 실제 구현해야 함" 의 확인이며, 본 작업 범위에 `typesOf` 신설이 포함되므로 **빌드 검증(`:common:build`·`:api:build` BUILD SUCCESSFUL)을 TASK 검증 항목으로 강제**하여 해소. OPERATION 중복은 발견사항 보고만(리팩토링 강제 금지).

### 안건 3: 도메인 규칙 정합성 (필터 강제 룰 + 4영역)
- 호출 에이전트: `wtp-domain-expert`
- Round 1 답변 요약:
  - **wtp-domain-expert**: 블로커 0건·권고 0건·참고 1건. 그룹 필터를 `facility_type_cd IN (OPERATION 8종)` 으로 전개하는 구현은 `entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 §도메인 룰 — facility_type_cd 필터 강제` **준수**(STORAGE/NETWORK 혼입 차단). 기존 시설별사용량-2번섹션 ANALYZE1 안건 2 선례(OPERATION 8종 IN 명시 = 룰 준수)와 일관. 도메인 4영역(알람 4단계·인터록·AI 운전 모드·이력 기록)은 모두 제어 발행/모드 전환 경로 결속 → 읽기 전용 `GET` 목록 조회는 **전부 비해당**. `FacilityGroup.OPERATION` 8종 매핑은 `standard-terms.md` `facility_type_cd` 행 정의와 정합(POINT=NETWORK·PWTF/DWT/RSV=STORAGE 제외 올바름). 참고 1건 — `facilityGroupCd` 미전달 시 전체 반환 여부를 가정 섹션 명기 권고(`coding-discipline.md §1`).
- **결론**: 필터 강제 룰 준수·도메인 4영역 전부 비해당·OPERATION 8종 정합 확인. 미전달 시 동작을 가정 섹션 명기.

### 안건 4: DB 쿼리·인덱스 (스키마 변경 0)
- 호출 에이전트: `wtp-dba-reviewer`
- Round 1 답변 요약:
  - **wtp-dba-reviewer**: 블로커 0건·권고 0건·참고 1건. DB 스키마 변경 0건 → 신규 데이터 도메인 0건, **DBA 2차 승인 대상 없음**. `facility_m` 은 마스터 소량 테이블(수십~수백 행)이라 옵티마이저가 Seq Scan 선택이 일반적(`indexing-and-migration.md §1`), `facility_type_cd IN (8종)` + `use_yn` 조건은 기존 `idx_facility_m_facility_type_cd` 활용 여부와 무관하게 성능 영향 무시 수준. IN 절 8개 enum 전개도 PostgreSQL 배열 최적화로 우려 없음. `use_yn` 카디널리티 2값(`indexing-and-migration.md §3.4`) 단독 인덱스 불필요. **신규 인덱스 필요 없음**. 참고 1건 — 운영 후 p6spy 500ms 초과 시 `EXPLAIN (ANALYZE, BUFFERS)` 확인 권고(`query-tuning.md §2`), 현 조건상 발생 가능성 낮음.
- **결론**: 스키마 변경 0·신규 인덱스 불필요·DBA 2차 승인 대상 없음 확인.

## 표준 사전 카탈로그

### 신규 표준 단어
**없음** — `facility`(비즈니스 도메인 약어)·`group`(2026-06-08)·`cd`(2026-04-23) 전부 기존 등록.

### 신규 표준 데이터 도메인
**없음** — DB 컬럼 신설 없음.

### 신규 표준 용어
**없음** — `facilityGroupCd` 는 검색 DTO Java 필드(DB 컬럼 아님) → `standard-terms.md` 등록 의무 없음(`hr`·`actl`·`inq` DTO 전용 단어 선례 동형). `typesOf` 는 Java 메서드명으로 어휘 사전 관리 대상 외.

## 신규 엔티티/DB 컬럼

**없음** — 읽기 전용 조회 API. 신규 엔티티·DB 테이블·DB 컬럼·인덱스 **0건**. 추가 산출물은 (1) `FacilityType.typesOf(FacilityGroup)` 정적 헬퍼 1개, (2) `FacilitySearchDto.facilityGroupCd` 필드 1개, (3) `FacilityCustomRepositoryImpl.findFacilities()` 그룹 필터 분기 1개, (4) `FacilityController.findAllFacilities` Swagger description 보강, (5) `FacilityType` 단위 테스트 1건.

## 기존 사전·패턴과의 충돌

| 충돌 후보 | 판정 | 해소책 |
|----------|------|--------|
| `facility_type_cd` 필터 강제 룰 vs 그룹 필터 | 위반 아님 | 그룹 필터를 `facility_type_cd IN (OPERATION 8종)` 으로 전개 = 룰 준수(STORAGE/NETWORK 혼입 차단). 기존 시설별사용량-2번섹션 안건 2 선례 동형 |
| `typesOf` SSOT vs `FacilityEnergyUsageService` 인라인 OPERATION 도출 | 중복(권고) | `coding-discipline.md §3` 정밀 수정 — 기존 서비스 코드 직접 수정 금지. RESULT "발견 사항"으로만 보고. 본 사이클 리팩토링 강제 금지 |
| `facilityTypeCd` 와 `facilityGroupCd` 동시 지정 | 충돌 없음 | BooleanBuilder AND 교집합(`eq` AND `in`). 별도 충돌 처리 불필요 — 단순성 우선(`coding-discipline.md §2`) |

## PLAN 으로 전달할 결정 사항

**도메인 모델**: 신규 엔티티 없음. `FacilityType.typesOf(FacilityGroup)` 정적 헬퍼로 group→소속 type 목록을 파생(하드코딩 회피 SSOT). `OPERATION` → `[PRSF, WTBLD, CHMB, ACFB, POZB, FLTB, DEWB, SOLAR]`.

**API 구조** (facility 도메인, 엔드포인트 신설 없음):
- `common` — `FacilityType.typesOf(FacilityGroup group)` 정적 메서드 추가: `Arrays.stream(values()).filter(t -> t.group == group).toList()`.
- `api` `FacilitySearchDto` — `private FacilityGroup facilityGroupCd;` 필드 추가(NULL 허용, `@Schema(description=..., implementation = FacilityGroup.class)`).
- `api` `FacilityCustomRepositoryImpl.findFacilities()` — BooleanBuilder 분기 추가: `if (searchDto.getFacilityGroupCd() != null) where.and(f.facilityType.in(FacilityType.typesOf(searchDto.getFacilityGroupCd())));`. 정렬 `useYn DESC → dispOrd ASC → facilityNm ASC` 유지.
- `api` `FacilityController.findAllFacilities` — `@Operation(description)` 에 `facilityGroupCd` 필터 + 설비별사용량 2번섹션 운영시설 목록 용도 한 줄 보강. 엔드포인트/시그니처/응답 타입 변경 없음.
- `FacilityService` 변경 없음(필터는 SearchDto 경유 자동 반영).

**DB 설계 변경**: **없음**(스키마 변경 0). `docs/ddl/` 갱신 불필요.

**적용 패턴**: `hasPump` 필터 도입(송수펌프_시설목록 2026-05-13) 동형. 호출 예: `GET /api/facility?facilityGroupCd=OPERATION&useYn=Y`.

**테스트**: `FacilityTypeTest.typesOf(OPERATION)` = 8종 / `typesOf(STORAGE)` = 3종 / `typesOf(NETWORK)` = 1종 순수 함수 단위 테스트. Querydsl IN 은 DB 의존이라 빌드 검증 범위.

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| `facilityGroupCd` 미전달(NULL) 시 그룹 조건 미적용 = 전체 시설 반환 | 결정 | 기존 `facilityTypeCd`·`useYn` NULL=전체 시맨틱 동형(`FacilitySearchDto` 정책). 2번섹션 화면은 `facilityGroupCd=OPERATION` 명시 호출 |
| `facilityTypeCd` 와 `facilityGroupCd` 동시 지정 시 AND 교집합(별도 검증/배타 처리 없음) | 결정 | 단순성 우선. 그룹 밖 type 지정 시 빈 결과 — 정상 동작으로 허용 |
| 활성 필터(useYn=Y) 는 클라이언트가 파라미터로 제어(서버 강제 안 함) | 결정 | 기존 `useYn` 필터 재사용. 좌측 nav 는 frontend 가 `useYn=Y` 전달 |
| 운영시설 목록은 평면(flat) 목록 — 트리/계층 구조 아님 | 가정 | 사용자 "목록 조회" 표현 기준. 트리 필요 시 별도 섹션 |
| `typesOf` SSOT 헬퍼는 신규 작성(기존 `FacilityEnergyUsageService` 인라인 도출 미수정) | 결정 | `coding-discipline.md §3` — 기존 코드 직접 수정 금지, 중복은 RESULT 발견사항 보고 |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `FacilityType.typesOf(OPERATION)` = 8종 정확 반환 | 신규 단위 테스트 — `typesOf(OPERATION)` size 8 + PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR 포함 GREEN |
| `typesOf(STORAGE)` = 3종 / `typesOf(NETWORK)` = 1종 | 신규 단위 테스트 — 그룹별 분류 정확 GREEN |
| 그룹 필터 분기 컴파일·빌드 성공 | `./gradlew.bat :common:build` 후 `./gradlew.bat :api:build` BUILD SUCCESSFUL |
| 그룹 필터 적용 시 OPERATION 8종만 반환 | (선택) 통합/수동 — dev DB `GET /api/facility?facilityGroupCd=OPERATION` 응답 facilityTypeCd 전부 OPERATION 그룹 |

## 도메인 룰 4영역 점검

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | 시설 목록 읽기 전용 조회 — 알람 임계값·전이·복귀 무접촉 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 비해당 | 읽기 전용 조회 — 기동 선행조건 검사·제어 명령 발행 경로 없음 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod`·`ai_mode_cd` 변경 없음, 모드 전환 트리거 없음 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `ai_drvn_mod_h`·`pump_ctrl_h` 무접촉. `facility_m` 조회 전용(쓰기 없음) |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

**없음** — 신규 표준 단어/데이터 도메인/표준 용어/비즈니스 도메인 약어 등록 0건, DB suffix·엔티티 패턴 변경 0건, DB 스키마 변경 0건. 모든 어휘가 기존 등록 항목 재사용이므로 룰 파일 갱신이 불필요하다(`wtp-glossary-manager` Round 1 확정 — 신규 등록 항목 0건).

## 산출물
- [계획안](../../../plan/20260609/설비별사용량-2번섹션/PLAN1.md) (다음 단계 `/dev:plan` 에서 작성)
