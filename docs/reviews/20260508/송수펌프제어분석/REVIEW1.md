---
status: approved
created: 2026-05-11
updated: 2026-05-11
---
# 송수펌프제어분석 — REVIEW1

## 관련 결과
- [RESULT1](../../../results/20260508/송수펌프제어분석/RESULT1.md) (status: completed)
- [PLAN1](../../../plan/20260508/송수펌프제어분석/PLAN1.md) (status: approved)
- [ANALYZE1](../../../analyze/20260508/송수펌프제어분석/ANALYZE1.md) (status: approved)
- 분할 TASK: [TASK1-1](../../../tasks/20260508/송수펌프제어분석/TASK1-1.md) · [TASK1-2](../../../tasks/20260508/송수펌프제어분석/TASK1-2.md) · [TASK1-3](../../../tasks/20260508/송수펌프제어분석/TASK1-3.md)

## 리뷰 범위

### 5인 회의 (메인 Claude 오케스트레이터 + 4 wtp-* 리뷰어)
- **Round 1**: 4 에이전트 병렬 spawn — `wtp-dba-reviewer` · `wtp-backend-engineer` · `wtp-domain-expert` · `wtp-glossary-manager`
- **Round 2**: `wtp-domain-expert` 1회 재호출 (Round 1 prompt too long 사유 — 단축 prompt 로 재진입). 도메인 4영역 점검은 차단 등급 직결로 재호출 의무 충족
- 각 에이전트 단답형 200~400단어, 블로커/권고/참고 분류

### ANALYZE-룰 정합성 점검 (자동)
ANALYZE1 의 "## 룰 갱신 지시서" 4건 모두 `[x]` 완료 + 실제 변경 반영 확인:
- `swtp/backend/.claude/rules/ot-integration.md` §3 OPS 행 추가 (즉시 BAD 격상) — git diff modified ✅
- `swtp/backend/.claude/rules/ot-integration.md` §3 VOI 행 추가 (Hold Last Value 5분 한계) — git diff modified ✅
- `swtp/backend/.claude/rules/ot-integration.md` §5 PRSF 가압장 시설 단위 별도 평가 명시 — git diff modified ✅
- `swtp/backend/.claude/rules/dict/standard-terms.md` 동의어/금지 패턴 `pwtf_id` (deprecated, `facility_id` 사용) 행 — 선행 사이클 또는 본 사이클 작업 중 별도 커밋 반영 (HEAD 컨텍스트 검증 완료) ✅

룰 갱신 누락 0건.

### 검토 코드 영역
- 데이터 계층: `FacilityType.PRSF`·`TagMeasurementType.OPS/VOI` enum / `PressureBoosterStation` skeleton / `Pump.ratedHead·ratedFlwrt·tagNm` / `DistributionWaterTank.minReqPrsr` / V8_1~V8_4 마이그레이션
- 애플리케이션 계층: Repository 4건 (Custom+Impl) / Service 3건 / DTO 9건 / Controller 2건 (08·09)
- 통합 검증·회귀: 단위 테스트 16건 신규 + 06·07 회귀 35+ 케이스 + scheduler 회귀 정렬

## 발견 사항

### 블로커 (높음) — 0건

본 REVIEW 결론: **블로커 0건**. Fix Cycle 진입 불필요. `status: approved` 전환.

### 권고 (중간) — 6건

| # | 영역 | 항목 | 위치 | 내용 |
|---|------|------|------|------|
| 1 | DB | V8_1 복합 인덱스 카디널리티 순서 역전 | `common/.../V8_1__facility_m_prsf_skeleton.sql:45-46` + 주석 19줄 | `idx_facility_m_type_parent` 가 `(facility_type_cd, parent_facility_id)` 순서. `db/indexing-and-migration.md §1` 카디널리티 높은 컬럼 선행 원칙상 `(parent_facility_id, facility_type_cd)` 가 정합 (UUID 고카디널리티 + 시설 종류 코드 저카디널리티). `findFacilitiesHavingDwtChild` exists 서브쿼리는 등가 조건 2건이므로 선택성 우선 컬럼이 선행되어야 함. 운영 시 인덱스 효율 저하 가능성 — 차기 배포 사이클에서 `DROP INDEX CONCURRENTLY` + `CREATE INDEX CONCURRENTLY` 재정렬 권고 |
| 2 | Backend | `instanceof` 다운캐스팅 3회 반복 — JOINED 다형성 추상화 깨짐 | `api/.../PumpAnalysisDashboardService.java:224-226` (`mapPumpState()`) | `pump instanceof com.mo.swtp.instrument.domain.Pump pp` 패턴 3회 반복. `Pump.ratedHead/ratedFlwrt/tagNm` 자식 컬럼 도입 완료 상태이므로 `findByFacilityIdAndEquipType()` 반환 타입을 `List<Pump>` 로 좁히거나 `Pump.toPumpStatePartial()` 내부 메서드로 캐스팅을 엔티티 계층에 위임 권고 (SRP) |
| 3 | Backend | `PumpStateDto` Javadoc/`fromMaster()` 오래된 주석 잔존 | `api/.../PumpStateDto.java:18-21` | Javadoc 본문에 "자식 전용 컬럼 미구현 — 차기 PLAN" 잔존하나 본 사이클 TASK1-1 에서 도입 완료 + `mapPumpState()` 가 실값 채움. `fromMaster(Instrument)` 정적 팩토리도 3컬럼 null 고정으로 실사용 경로와 불일치 — 현행 구현 기준 갱신 권고 |
| 4 | Backend | `PumpAnalysisDashboardService` 의존성 7개 + §2.5 면책 인용 근거 미명기 | `api/.../PumpAnalysisDashboardService.java:77-83` | `FacilityRepository`·`InstrumentRepository`·`TagRepository`·`RawDataRepository`·`AiDrvnModeRepository`·`PumpPredictionResultRepository`·`PumpCmbnDetailRepository` 7개 의존. ROOT [`coding-discipline.md §2.5`](../../../../.claude/rules/coding-discipline.md) 면책 영역 (`ot-integration.md §5` 단일 흐름) 해당 가능성 — 코드 주석에 `// §2.5 면책 (ot-integration.md §5)` 인용 근거 명기 의무. **본 건은 의존성 수 관찰 항목 (메서드 줄 수 위반 아님)** 이라 권고로 처리하되 향후 유사 Service 작성 시 선행 명기 강력 권고 |
| 5 | 도메인 | `ai_drvn_mod_p` 조회 시 `facility_type_cd` 필터 적용 방식 확인 | `api/.../PumpAnalysisDashboardService.java` (구현 확인 필요) | `ot-integration.md §5` "facility_type_cd 필터 강제" 룰에 따라 facilityId 단독 조회 시 PWTF/PRSF 구별 불가. 본 사이클 호출 형태가 단건 facilityId 1:1 조회면 자동 해소, 다건/리스트 조회면 필터 추가 필요. **단건 조회 형태로 확인되면 자동 해소** — 차기 사이클 PRSF 가압장 ai_drvn_mod_p 행 추가 시점에 재검증 |
| 6 | 어휘 | `ot-integration.md §3` SSOT 주석 측정 유형 코드 목록 갱신 | `backend/.claude/rules/ot-integration.md` §3 상단 SSOT 안내 블록 | 주석 `(FRI·PRI·LEI·PWI·RMS)` 5종 열거가 OPS·VOI 도입 후에도 미갱신. 현재 7종 (`FRI·PRI·LEI·PWI·RMS·OPS·VOI`) 으로 표기 일관성 보완 권고 |

### 참고 (낮음) — 6건

| # | 영역 | 항목 | 위치 | 내용 |
|---|------|------|------|------|
| 1 | DB | V8_4 COMMENT 훅 인식 범위 외 | `common/.../V8_4__pump_predc_h_facility_id.sql:88` | `check-ddl-column-comment.sh` 가 `CREATE TABLE name (` 인라인 형식만 인식. V8_4 는 `DO $$ ... $$` 내부 `ALTER TABLE ADD COLUMN` 패턴이라 훅 평가 대상 외. COMMENT 는 실제 존재 (88줄) — 데이터 무결성 영향 없음. 훅 정규식 보강은 별도 ANALYZE |
| 2 | DB | `InstrumentCustomRepositoryImpl` 묵시적 INNER JOIN 발생 가능성 | `api/.../InstrumentCustomRepositoryImpl.java:30` | `i.facility.facilityId.eq(facilityId)` 경로 탐색이 Querydsl 의 INNER JOIN 생성. `i.facilityId.eq(facilityId)` 로 직접 컬럼 조건 시 JOIN 회피 가능 — 현 행 수에서는 동작 영향 없으나 차후 검토 |
| 3 | Backend | `FacilityListDto` 패키지 위치 — 도메인 경계 모호 | `api/src/main/java/com/mo/swtp/pump/dto/FacilityListDto.java` | `FacilityListService` 가 `com.mo.swtp.facility.service` 에 있고 응답 대상이 `Facility` 엔티티임에도 DTO 가 `pump.dto` 위치. PLAN1 §패키지 위치 결정의 의도적 배치 — 향후 시설 도메인 재사용 시 `facility.dto` 이동 권고 |
| 4 | Backend | `DwtStatusService.buildDwtRequirePressure()` Repository 2회 호출 | `api/.../DwtStatusService.java:63-68, 167-168` | 자식 DWT 리스트를 이미 조회한 뒤 `findFirstChildByParentIdAndType()` 재호출. 메모리에서 `dispOrd` ASC 1번째 선택 시 SQL 1회 절감 가능 |
| 5 | 어휘 | `oprtng` 표준 단어 vs `OPS` 측정 코드 어근 유사 | `swtp/.claude/rules/dict/standard-words.md` + `ot-integration.md §3` | `oprtng` 은 DB 컬럼 조합 재료 단어, `OPS` 는 OT 측정 유형 SSOT 코드 — 계층 다르므로 충돌 판정 외. 후속 `ops_*` 컬럼 명명 시 재검토 트리거 |
| 6 | 도메인 | `FacilityListService` PRSF 포함 전제 단위 테스트 커버리지 | `api/test/.../FacilityListServiceTest.java` | `findFacilitiesHavingDwtChild(types=[PWTF, PRSF])` 의 PRSF 자식 (DWT 보유) 시나리오가 단위 테스트에 커버되는지 확인 — PRSF skeleton 단계라 DWT 자식 0건이 기본. 차기 사이클 PRSF 가압장 데이터 도입 시점에 통합 테스트 추가 |

## 개선 제안

### 본 사이클 후속 조치 (선택)
1. **권고 #2·#3 즉시 반영**: `PumpAnalysisDashboardService` `instanceof` 다운캐스팅 제거 + `PumpStateDto` Javadoc 갱신은 **본 사이클 커밋 전 보완 가능** — 별도 사이클 회피
2. **권고 #4 (`§2.5` 면책 인용)**: `PumpAnalysisDashboardService` 클래스 Javadoc 또는 클래스 상단에 `// §2.5 면책 (ot-integration.md §5 단일 흐름 — 7섹션 통합 오케스트레이션)` 1줄 보강
3. **권고 #6 (`ot-integration.md §3` SSOT 주석)**: 1줄 갱신 — 즉시 가능

### 차기 사이클 트리거
1. **권고 #1 (V8_1 인덱스 재정렬)**: `DROP INDEX CONCURRENTLY idx_facility_m_type_parent; CREATE INDEX CONCURRENTLY idx_facility_m_type_parent ON facility_m (parent_facility_id, facility_type_cd);` — 차기 배포 사이클에서 V9_* 등록
2. **권고 #5 (도메인 필터)**: PRSF 가압장 데이터 실제 도입 시점에 `ai_drvn_mod_p` 조회 필터 재검증 + 통합 테스트 보강
3. **참고 #1 (V8_4 COMMENT 훅 보강)**: `check-ddl-column-comment.sh` 의 `DO $$ ALTER TABLE ADD COLUMN` 패턴 인식 별도 ANALYZE

### 도메인 4영역 점검 결론
| 영역 | 해당/비해당 | 결과 |
|------|----------|------|
| 알람 4단계 | 비해당 (read-only) | 정합 ✅ |
| 인터록 선행조건 | **해당** (PRSF skeleton) | PLAN §제외 사항 마지막 행 "PRSF 펌프 기동 명령 확장 전 인터록 룰 분리·등록 선행 의무" 명시 ✅ + `PressureBoosterStation.java` Javadoc 재기술 ✅ |
| AI 운전 모드 | **해당** (PRSF 시설 단위 + §2 두 컬럼) | `ot-integration.md §5` PRSF 행 추가 ✅ + `OperationStatusDto.java` Javadoc "ai_mode_cd 우선 표시" 안내 + `ot-integration.md §5` 인용 근거 명기 ✅ |
| 이력 기록 의무 | 비해당 (read-only) | 정합 ✅ |

## 결론

- **블로커 0건** — `status: approved` 전환 완료
- **권고 6건 + 참고 6건** — 차기 사이클 또는 본 사이클 commit 전 선택적 반영
- PLAN1 §사용자 결정 4건 모두 코드 + 룰 + 문서에 반영 완료
- ANALYZE1 §룰 갱신 지시서 4건 모두 적용 완료
- 도메인 4영역 점검 통과 (인터록 pass-through 위험 + AI 모드 이중 체계 모두 문서·Javadoc 가시화)
- TASK 규모 적정성: Phase 12 / 체크박스 ~70 / 3-분할 (분할 기준 Phase 10 / 60 초과 + 계층 경계 명확)

### 다음 단계
사용자 명시적 승인 후 `/dev:commit 송수펌프제어분석` 호출 (transitions.md §`/dev:review` → `/dev:commit` 자동 실행 금지 — 사용자 승인 의무).

commit 후 frontend SPEC 전파: `/dev:spec 송수펌프제어분석` → `swtp/frontend/docs/api-specs/송수펌프제어분석/SPEC1.md` 자동 추출.
