---
status: completed
created: 2026-05-13
updated: 2026-05-13
---
# 송수펌프제어분석 — 3번섹션 시설 실시간 상태 표출 API 결과

## 관련 작업
- [계획안](../../../plan/20260513/송수펌프제어분석-3번섹션/PLAN1.md)
- [태스크](../../../tasks/20260513/송수펌프제어분석-3번섹션/TASK1.md)

## 작업 요약

활성 시설의 유량계(FLWMTR) FRI/PRI + 펌프(PUMP) OPS + 정적 oprtngType 을 단건 endpoint 로 표출하는 송수펌프제어 화면 3번 섹션 백엔드 API 신설 + 2번 섹션 (배수지 상태 표출) 자산 일괄 폐기.

신규 endpoint: `GET /api/facility/{facilityId}/state` → `ResponseEntity<CommonResponseDto<FacilityStateDto>>`. 4-step 체이닝 (Facility → Instrument → Tag → RawData) 모두 IN 절 기반 단일 SQL 4회로 N+1 회피. RawData 는 PostgreSQL DISTINCT ON + 1시간 윈도우 파티션 프루닝 native SQL.

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 8 |
| 체크박스 수 | 23 (완료 21 / 환경 의존 보류 2) |
| 분할 여부 | N |
| 분할 근거 | — (Phase 10 + 체크박스 60 미만, Medium 작업 단일 TASK) |

## 변경 사항

### 의도된 변경

**3번 섹션 신규 자산 9건 추가**:

| 파일 | 분류 |
|------|------|
| `backend/api/src/main/java/com/mo/swtp/raw/dto/RawDataLatestDto.java` | Service 내부 전송용 record |
| `backend/api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepository.java` | 인터페이스 재정의 (단일 메서드 `findLatestByTagSrlNos(List<String>)`) |
| `backend/api/src/main/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImpl.java` | native DISTINCT ON 구현 |
| `backend/api/src/main/java/com/mo/swtp/facility/dto/FacilityStateDto.java` | 응답 DTO 컨테이너 |
| `backend/api/src/main/java/com/mo/swtp/facility/dto/FlwmtrStateDto.java` | 유량계 응답 DTO (FRI+PRI 8 필드 + instrumentId/Nm 2 = 10 필드) |
| `backend/api/src/main/java/com/mo/swtp/facility/dto/PumpStateDto.java` | 펌프 응답 DTO (6 필드 — oprtngType + isRunning + acqDtm + qualityCd + instrumentId/Nm) |
| `backend/api/src/main/java/com/mo/swtp/facility/service/FacilityStateService.java` | 4-step 체이닝 Service |
| `backend/api/src/main/java/com/mo/swtp/facility/web/FacilityController.java` (수정) | GET /{facilityId}/state endpoint 추가 |
| `backend/api/src/test/java/com/mo/swtp/facility/service/FacilityStateServiceTest.java` | 단위 테스트 6 시나리오 |
| `backend/api/src/test/java/com/mo/swtp/raw/repository/RawDataCustomRepositoryImplIntegrationTest.java` | 통합 테스트 3 시나리오 (@Disabled — PostgreSQL 의존) |

**2번 섹션 자산 6건 일괄 폐기** (사용자 결정 2026-05-13 "DwtStatus 부분도 전부 폐기 다음 섹션 진행할 때 다시설계"):

| 파일 | 처리 |
|------|------|
| `backend/api/src/main/java/com/mo/swtp/facility/service/DwtStatusService.java` | 삭제 |
| `backend/api/src/main/java/com/mo/swtp/facility/web/DwtStatusController.java` | 삭제 |
| `backend/api/src/main/java/com/mo/swtp/facility/dto/DwtStatusDto.java` | 삭제 |
| `backend/api/src/main/java/com/mo/swtp/facility/dto/section/DwtRequirePressureDto.java` | 삭제 |
| `backend/api/src/main/java/com/mo/swtp/facility/dto/section/MainFactorDto.java` | 삭제 (+ section 디렉토리 자동 삭제) |
| `backend/api/src/test/java/com/mo/swtp/facility/service/DwtStatusServiceTest.java` | 삭제 |

`RawDataCustomRepository.findLatestByTagSrlNos(List<String>, LocalDateTime)` Querydsl 구현은 2번 섹션 (`DwtStatusService:99`) 의 유일한 호출처였으므로 일괄 폐기 후 native DISTINCT ON 신규 메서드로 대체.

### 계획 외 변경

> ROOT [`coding-discipline.md` §3](../../../../../.claude/rules/coding-discipline.md) 적용. PLAN1 결정 외 부수 변경 명시.

**1건 (의도)** — Service DI 컨벤션 정렬:
- PLAN1 §구현 방향 §Service 표는 `InstrumentCustomRepository` / `RawDataCustomRepository` 직접 주입을 제안. 본 프로젝트의 `*CustomRepositoryImpl` 이 Spring 빈으로 별도 등록되어 `extends *CustomRepository` 한 `*Repository` (JPA + Custom 통합 인터페이스) 와 함께 둘 다 `*CustomRepository` 타입 빈으로 노출 → `NoUniqueBeanDefinitionException`
- `FacilityStateService` 의 의존성 주입을 `InstrumentRepository` · `RawDataRepository` (JPA + Custom 통합) 로 변경하여 회피. 2번 섹션 `DwtStatusService` 의 기존 컨벤션과 정렬 — 직전 사이클 자산을 자동 원용하지 않되, DI 컨벤션 자체는 본 사이클에서 독립적으로 발견·채택
- 영향: 호출 방식 동일 (`*Repository.findLatestByTagSrlNos` / `.findByFacilityIdAndEquipType`), 응답 동일

**1건 (의도)** — PLAN1 §Repository SQL 본문 미세 변경:
- PLAN1 §Repository 본문 골자 `tag_srl_no = ANY(?)` 명시 → 실 구현에서는 `tag_srl_no IN (:tagSrlNos)` (Hibernate native query 표준 IN-list 펼침 패턴) 채택
- 근거: JDBC Array 직접 변환의 복잡도 회피 + Hibernate 6 의 native query IN-list 자동 펼침 지원. 결과 동일 — DISTINCT ON·인덱스 정합성·파티션 프루닝 의도 보존

## 테스트 결과

| 테스트 분류 | 결과 | 비고 |
|----------|------|------|
| 단위 테스트 `FacilityStateServiceTest` (6 시나리오) | **PASS** | 시설 미존재/비활성 FACILITY_NOT_FOUND, BAD QUALITY 그대로 반환, 빈 instrument 빈 List, FRI/PRI 분리, OPS Boolean 변환 |
| 통합 테스트 `RawDataCustomRepositoryImplIntegrationTest` (3 시나리오) | **@Disabled** | PostgreSQL 미기동 + DDL 미실행 (rawdata_1m_h 테이블 부재). 사용자 환경에서 @Disabled 제거 후 실행 의무 |
| `./gradlew :common:build` | **BUILD SUCCESSFUL** | 9s |
| `./gradlew :api:build` | **BUILD SUCCESSFUL** | 55s (10 tasks 통과) |

## 비고

### PLAN1 §성공 기준 검증 매트릭스

| # | 기준 | 본 사이클 상태 | 사용자 환경 검증 |
|---|------|----------|--------------|
| 1 | DISTINCT ON 인덱스 활용 + 파티션 프루닝 | **보류** | 로컬 PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` 수동 실행 — Index Scan Backward + 파티션 1~2개 + Sort 노드 부재 확인 |
| 2 | N=100 활성 태그 fixture 응답 시간 200ms 이내 | **보류** | `RawDataCustomRepositoryImplIntegrationTest` @Disabled 제거 + 100건 fixture 보강 후 실행 |
| 3 | `quality_cd` BAD/UNCERTAIN 도 그대로 반환 | **PASS** (단위 테스트) | 통합 테스트 추가 검증 권장 |
| 4 | `oprtngType` Swagger 스키마 노출 | **보류** | `./gradlew :api:bootRun -Pprofile=local` 기동 후 Swagger UI 수동 확인 |
| 5 | 응답 `CommonResponseDto<FacilityStateDto>` + `code: "SUCCESS"` | **PASS** (구조 검증) | curl 수동 확인 권장 |

### 메모리 보강 2건

본 사이클에서 사용자 정책 명시 결정에 따라 글로벌 메모리 신규 등록:

| 메모리 | 정책 |
|------|-----|
| `feedback-no-auto-reuse-cross-cycle` | 이전 사이클의 자산 (메서드·DTO·테이블) 자동 원용 금지 — 항상 사용자 명시 결정 요청 |
| `feedback-section-cycle-discard-policy` | 송수펌프제어분석 N번섹션 사이클에서 이전 섹션 자산은 폐기 대상, 재설계는 별도 사이클 |

### 본 사이클 범위 외

- 2번 섹션 (배수지 상태 표출) 재설계 — 별도 사이클 (예: `송수펌프제어분석-2번섹션_재설계`) 에서 신규 ANALYZE/PLAN 진행
- N=500 SLA 미달 대응 캐시 도입 — PLAN1 §제외 사항 정합, 별도 사이클
- OPS 결측 처리 정책 (Hold Last Value 미적용 영향, ANALYZE1 미해결 6) — 별도 사이클
- 5분 초과 결측 표시 정책 — frontend 자체 분기 위임
- 6번 섹션 펌프 가동대수·관압 endpoint — 6번 ANALYZE 사이클에서 별도 결정
