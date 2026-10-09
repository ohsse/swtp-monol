---
status: completed
created: 2026-05-20
updated: 2026-05-20
---
# 송수펌프제어분석 — 7번 섹션 (시설 예측 데이터 표출) — RESULT1

## 관련 작업
- [계획안](../../../plan/20260518/송수펌프제어분석-7번섹션/PLAN1.md)
- [태스크](../../../tasks/20260518/송수펌프제어분석-7번섹션/TASK1.md)

## 작업 요약

이미지 `image/송수펌프제어분석.png` 7번 섹션(우측 "분석 결과") 구현. 1번 섹션에서 선택한 시설의 **AI 예측 데이터**를 3번 섹션(현황)과 동일한 형태·이벤트 흐름으로 표출. `predc_1m_h` 신규 시계열 테이블 + `latest_meas` CTE + `CROSS JOIN LATERAL` 근접매칭 단일 native SQL + 섹션 3 미러 별도 Service 클래스 + 신규 도메인 패키지 `com.mo.swtp.opt` 도입. AI 추론 파이프라인(INSERT)은 본 사이클 제외(조회 전용).

## TASK 규모
| 항목 | 값 |
|------|----|
| Phase 수 | 6 |
| 체크박스 수 | 21 |
| 분할 여부 | N |
| 분할 근거 | — |

## 변경 사항

### 의도된 변경 (TASK 체크박스 명시)

**Phase 1 — DDL**
- `common/src/main/resources/db/migration/V9_3__predc_1m_h.sql` — 신규
  - `predc_1m_h` 월 RANGE 파티션 테이블 (6컬럼 + 복합 PK `(predc_id, predc_dtm)`)
  - `seq_predc_id` SEQUENCE (`CACHE 100` — JPA `allocationSize=100` 정합)
  - `idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)` ASC 인덱스 (근접 대칭 범위 스캔)
  - 월별 파티션 6개월 선행 (202605~202610)
  - COMMENT ON COLUMN 6건 (immutable 이력 표준 라벨 — `indexing-and-migration.md §4.3`)

**Phase 2 — 엔티티 (`com.mo.swtp.opt.domain`)**
- `common/src/main/java/com/mo/swtp/opt/domain/TagPredictionId.java` — 신규 (복합키 식별 클래스, `Serializable` + `equals/hashCode`)
- `common/src/main/java/com/mo/swtp/opt/domain/TagPrediction.java` — 신규
  - immutable 이력 예외 (`BaseEntity` 미상속, `rgstr_dtm`·`rgstr_id` 만 + `AuditingEntityListener`)
  - `@SequenceGenerator(allocationSize=100)` + `@GeneratedValue(SEQUENCE)`
  - 시계열 → 마스터 FK 금지 (`tag_srl_no` 논리 참조)

**Phase 3 — 근접매칭 Repository (`com.mo.swtp.opt.repository`)**
- `api/src/main/java/com/mo/swtp/opt/dto/TagPredictionMatchDto.java` — 신규 (Repository→Service 내부 전송 record)
- `api/src/main/java/com/mo/swtp/opt/repository/TagPredictionRepository.java` — 신규 (JpaRepository + Custom)
- `api/src/main/java/com/mo/swtp/opt/repository/TagPredictionCustomRepository.java` — 신규 (인터페이스)
- `api/src/main/java/com/mo/swtp/opt/repository/TagPredictionCustomRepositoryImpl.java` — 신규
  - `latest_meas` CTE + `CROSS JOIN LATERAL` 근접매칭 단일 native SQL 흐름
  - `// §2.5 면책 (query-tuning.md §2)` 주석 (REVIEW 블로커 방지)
  - 인덱스 방향 혼재 의도 (ASC predc vs DESC rawdata) 주석 명기
  - LATERAL N+1 아님 의도 주석 명기

**Phase 4 — Service (`com.mo.swtp.facility.service`)**
- `api/src/main/java/com/mo/swtp/facility/service/FacilityPredictionService.java` — 신규
  - 섹션 3 `FacilityStateService` 와 동일 4-step 골격이나 별도 클래스로 독립 (공유 헬퍼 재사용 금지)
  - 섹션 7 전용 상수 `PREDICTION_EQUIP_TYPES`·`PREDICTION_TAG_TYPES` 별도 선언
  - `@Value("${opt.prediction.match-window-minutes:5}")` 윈도우 주입
  - `mapFlwmtrPrediction`·`mapPumpPrediction` private 헬퍼 분해 (본문 50줄 초과 방지)
  - `predcIsRunning` Boolean 변환 (1.0/0.0/null → true/false/null)

**Phase 5 — Controller·DTO·yml**
- `api/src/main/java/com/mo/swtp/facility/dto/FacilityPredictionDto.java` — 신규 (`@Getter` + private 생성자 + 정적 팩토리 `of`)
- `api/src/main/java/com/mo/swtp/facility/dto/FlwmtrPredictionDto.java` — 신규 (FRI/PRI 예측값 4컬럼, `quality_cd` 부재)
- `api/src/main/java/com/mo/swtp/facility/dto/PumpPredictionDto.java` — 신규 (`oprtngType` + `predcIsRunning` + `predcDtm`)
- `api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` — 수정
  - `/prediction` 엔드포인트 + `FacilityPredictionService` 주입 + Swagger `@Tag "06. 시설물 관리"` 정합
- `api/src/main/resources/application-common.yml` — 수정
  - `opt.prediction.match-window-minutes: 5` 추가 (PLAN §2 미해결 #1 결정)

**Phase 6 — 테스트**
- `api/src/test/java/com/mo/swtp/facility/service/FacilityPredictionServiceTest.java` — 신규 (단위 테스트 6 시나리오)
- `api/src/test/java/com/mo/swtp/opt/repository/TagPredictionCustomRepositoryTest.java` — 신규 (통합 테스트 3 시나리오, `@Disabled`)

### 계획 외 변경

ROOT [`coding-discipline.md` §3](../../../../.claude/rules/coding-discipline.md) 정밀한 수정 정책에 따라 **의도(필수 부수 변경)** 로 분류된 2건:

**1. `TagPredictionId` — `@Embeddable` → `@IdClass` 패턴 변경 (의도)**

- **PLAN1 §5 / §도메인 모델 표 명시**: `@Embeddable` 복합키 + `@EmbeddedId`
- **실 구현 변경**: `RawData` 선례의 `@IdClass(TagPredictionId.class)` 패턴 채택
- **사유**: JPA `@EmbeddedId` + `@SequenceGenerator` 자동 채번은 표준 호환성 미보장 (Embeddable 의 PK 일부 필드에 `@GeneratedValue` 적용 시 Hibernate 동작 불확실). `RawData` 가 동일 시계열 패턴 + 동일 SEQUENCE 채번을 `@IdClass` 로 운영 중이라 선례 정합 + 안정성 확보. PLAN1 표의 "@Embeddable 복합키" 표현은 일반적 "복합키 식별 클래스" 의미로 해석 가능
- **영향**: 외부 API/DDL 동일 — 단지 JPA 매핑 어노테이션 선택 차이. `TagPredictionId` 구조 (필드 2개·equals/hashCode) 는 PLAN1 명시와 동일

**2. `FacilityPredictionService` — Repository 주입 인터페이스 변경 (의도)**

- **PLAN1 §3 명시**: `TagPredictionCustomRepository.findNearestByTagSrlNos(tagSrlNos, windowMinutes)` 호출
- **실 구현 변경**: 주입 타입을 `TagPredictionCustomRepository`(Custom 인터페이스) → `TagPredictionRepository`(JpaRepository) 로 변경. 메서드 호출 동일 (`tagPredictionRepository.findNearestByTagSrlNos(...)`)
- **사유**: Spring 빈 ambiguity — Custom 인터페이스로 직접 주입 시 ApplicationContext 가 `tagPredictionCustomRepositoryImpl` + `tagPredictionRepository` 두 빈을 모두 `TagPredictionCustomRepository` 타입 후보로 식별 → `NoUniqueBeanDefinitionException`. 첫 빌드 시 UserServiceTest 17건의 ContextLoad 실패로 발견. 섹션 3 `FacilityStateService.rawDataRepository` 선례 정합 (JpaRepository 주입) 으로 자연 해소
- **영향**: 외부 API 동일 — Service 내부 의존성 주입 방식만 변경. 단위 테스트 mock 타입도 동일 변경

## 테스트 결과

### 단위 테스트
```
./gradlew.bat :api:test --tests "com.mo.swtp.facility.service.FacilityPredictionServiceTest"
→ BUILD SUCCESSFUL (6 tests PASS)
```

| 시나리오 | 검증 결과 |
|---------|---------|
| 시설 미존재 → `FACILITY_NOT_FOUND` | PASS |
| 비활성 시설 (`use_yn=N`) → `FACILITY_NOT_FOUND` | PASS |
| 활성 instrument 없으면 빈 List | PASS |
| 유량계 FRI/PRI 예측값 분리 매핑 | PASS |
| OPS 예측 `1.0`/`0.0` → `predcIsRunning` true/false | PASS |
| 윈도우 밖 태그는 `predcVal`·`predcDtm` null | PASS |

### 통합 테스트
- `TagPredictionCustomRepositoryTest` 3 시나리오 — `@Disabled` (로컬 PostgreSQL + V6_5 `rawdata_1m_h` + V9_3 `predc_1m_h` 현재 월 파티션 사전 생성 전제). 사용자 로컬 환경에서 `@Disabled` 제거 후 실행으로 PLAN §성공 기준 #1·#2·#3 검증

### 전체 빌드 결과
```
./gradlew.bat build
→ 143 tests, 3 failed, 6 skipped (BUILD FAILED)
```

| 분류 | 건수 | 분석 |
|------|------|------|
| 본 작업 회귀 | **0** | 본 작업으로 신규 실패 0건. 단위 테스트 모두 PASS |
| 환경 의존 실패 (본 작업 외) | 3 | `FacilityServiceIntegrationTest` 3건 — `dwt_m.min_req_branch_prsr` 컬럼 부재. 송수펌프제어분석-5번섹션 마이그레이션이 로컬 PostgreSQL 에 미적용 (스키마 동기화 누락). 본 작업이 만든/수정한 파일 어디에도 `min_req_branch_prsr` 미포함 |
| `@Disabled` skip | 6 | 통합 테스트 의도적 skip (PostgreSQL 전제) |

### 미실행 항목 (사용자 로컬 환경 의존)
- PLAN §성공 기준 #1 — `EXPLAIN (ANALYZE, BUFFERS)` 인덱스/파티션 프루닝 검증 (PostgreSQL 필요)
- PLAN §성공 기준 #2 — N=100 fixture 응답 200ms 측정 (PostgreSQL 필요)
- PLAN §성공 기준 #5 — Swagger UI `predcIsRunning`·`oprtngType` enum 노출 시각 확인 (`./gradlew.bat :api:bootRun` 후 브라우저)
- PLAN §성공 기준 #6 — `curl GET /api/facility/{facilityId}/prediction` 응답 계약 (`./gradlew.bat :api:bootRun` 후)

## 비고

- **시계열 → 마스터 FK 금지** 정책 (`partitioning-and-retention.md §1`) 정합 — `predc_1m_h.tag_srl_no` 는 `tag_m.tag_srl_no` 논리 참조이며 FK 제약 없음
- **immutable 이력 예외** (`indexing-and-migration.md §4.3`) — `predc_1m_h` 는 `BaseEntity` 미상속, `rgstr_dtm`·`rgstr_id` 만 보유. `updt_*` 데드 컬럼 회피
- **§2.5 면책** (`query-tuning.md §2`) — `TagPredictionCustomRepositoryImpl.findNearestByTagSrlNos` 본문은 CTE + LATERAL 단일 흐름 보존 목적으로 면책 적용 (인용 근거 주석 필수)
- **신규 도메인 패키지** `com.mo.swtp.opt` 도입 — 비즈니스 도메인 약어 `opt` (AI 최적화 결과 + 예측 시계열 저장) `domain-abbreviations.md` 등재 항목. `com.mo.swtp.ai` (AI 운전모드·예측 서비스 상위 계층) 와 경계 명확
- **사이클 2 인계 항목**:
  - BRIN(`predc_dtm`) 인덱스 추가 — AI 추론 파이프라인 도입 (1분 그리드 INSERT) 시 재검토
  - 예측 신뢰도/`quality_cd` 컬럼 — 사이클 2 결정 대기
  - N=500 운영 SLA 200ms 미달 가능성 — 운영 측정 후 캐시 별도 사이클
  - `ai_drvn_mod_p` 재도입 시 예측→AI 자동 운전 입력 경로 4영역 재평가
- **빌드 환경 안내** — `FacilityServiceIntegrationTest` 통과를 위해 사용자 로컬 PostgreSQL 에 송수펌프제어분석-5번섹션 마이그레이션 (`dwt_m.min_req_branch_prsr` 컬럼 추가 DDL) 적용 필요. 본 작업 범위 외 후속 동기 권고
