---
status: completed
created: 2026-05-06
updated: 2026-05-06
---
# 마스터도메인설계 — 사이클 2 (RawData immutable 정합 정렬) 결과

## 관련 작업
- [계획안](../../../plan/20260506/마스터도메인설계/PLAN2.md)
- [태스크](../../../tasks/20260506/마스터도메인설계/TASK2.md)
- [이전 결과 (사이클 1)](../../20260503/마스터도메인설계/RESULT1.md)
- [이전 리뷰 (사이클 1)](../../../reviews/20260503/마스터도메인설계/REVIEW1.md)

## 작업 요약

REVIEW1 블로커 1건 (`RawData.changeQualityCd` 메서드 + `@PreUpdate` 검증이 PLAN1 §도메인 룰 "`corr_val` 만 갱신 허용" 과 미정렬) 을 옵션 ② (immutable 강제) 로 해소했다. `RawData` 의 `quality_cd` 컬럼을 `tag_srl_no`·`acq_dtm`·`raw_val` 과 동일한 INSERT-only 컬럼으로 정렬하여 PLAN-코드 정합성을 회복.

핵심 변경: `changeQualityCd(QualityCode)` 메서드 제거 + `qualityCdSnapshot @Transient` 필드 추가 + `captureImmutableSnapshot()` 본문 한 줄 추가 + `@PreUpdate` 본문 4번째 분기 추가 + Javadoc 2건 정렬. 검증을 위해 `RawDataTest.java` 를 신규 작성 (5 케이스 — `qualityCd` 차단 + `corrVal` 허용 + `tagSrlNo`·`acqDtm`·`rawVal` 회귀).

빌드·테스트 모두 PASS — `:common:test` 의 `RawDataTest tests=5 failures=0 errors=0`, `clean build` 54s 성공으로 common·api·scheduler 전체 회귀 없음 확인. 사이클 2 는 Small 규모 (코드 1 파일 수정 + 테스트 1 파일 신규).

## TASK 규모

| 항목 | 값 |
|------|----|
| Phase 수 | 3 |
| 체크박스 수 | 10 |
| 분할 여부 | N |
| 분할 근거 | — |

## 변경 사항

### 의도된 변경

PLAN2 §범위 정의 + TASK2 체크박스 10건 결과:

| # | 파일 | 종류 | 변경 내용 |
|---|------|------|----------|
| 1 | `common/src/main/java/com/mo/swtp/raw/domain/RawData.java` | 수정 | (1) `changeQualityCd(QualityCode)` 메서드 + Javadoc 8줄 제거 — INSERT-only 컬럼 갱신 경로 차단. (2) `qualityCdSnapshot @Transient` 필드 추가 — 다른 3개 스냅샷 필드 (`tagSrlNoSnapshot`·`acqDtmSnapshot`·`rawValSnapshot`) 와 동일 패턴. (3) `captureImmutableSnapshot()` 본문에 `this.qualityCdSnapshot = this.qualityCd` 한 줄 추가. (4) `onPreUpdateValidateImmutable()` `@PreUpdate` 본문에 `quality_cd` 스냅샷 비교 분기 추가 (`IllegalStateException` 메시지 형식 동일). (5) 클래스 Javadoc 36줄 INSERT-only 4 컬럼 정렬 (`tag_srl_no·acq_dtm·raw_val·quality_cd`). (6) `@PreUpdate` Javadoc 161줄 `"corr_val·quality_cd 만 갱신 허용"` → `"corr_val 만 갱신 허용"` 정렬 |
| 2 | `common/src/test/java/com/mo/swtp/raw/domain/RawDataTest.java` | 신규 | 5 케이스 단위 테스트 — `corrVal_갱신은_허용된다` (positive) + `qualityCd_변경은_immutable_검증으로_차단된다` (블로커 해소 검증) + `tagSrlNo`·`acqDtm`·`rawVal` 회귀. JPA 컨텍스트 미사용, `@PrePersist`·`@PreUpdate` 직접 호출 (같은 패키지 `protected` 접근). immutable 컬럼 변경은 reflection 헬퍼 `setField(...)` 사용. 한국어 메서드명 + AssertJ + `assertThatCode`/`assertThatThrownBy` 패턴 |

### 변경 메트릭

| 항목 | 수 |
|------|----|
| 신규 파일 | 1 (RawDataTest.java) |
| 수정 파일 | 1 (RawData.java) |
| 삭제 파일 | 0 |
| 신규 테스트 케이스 | 5 |
| 추가 줄 (개략) | +120 (RawDataTest 110 + RawData 5 필드/분기 + Javadoc 정렬 +1·-1) |
| 삭제 줄 (개략) | -8 (changeQualityCd 메서드 + Javadoc) |

### 계획 외 변경

> ROOT [`coding-discipline.md §3`](../../../../../.claude/rules/coding-discipline.md) 적용. TASK 체크박스 외 파일 변경 점검.

**없음** — TASK2 체크박스 10건 외 변경 사항 없음. PLAN2 §제외 사항에 명시된 항목 (CORS 변경·재수집 프로세스·Service/Repository 추가·PLAN1 본문 변경·RESULT1 보강·ai-server Pydantic 동기화) 모두 본 사이클 범위 외로 유지.

> 부수적 frontmatter·체크박스 갱신 (`docs/tasks/20260506/마스터도메인설계/TASK2.md` 의 `status: review` → `approved` → `completed` + 체크박스 10건 `[ ]` → `[x]`) 은 doc-harness 정상 흐름의 일부로 계획 외 변경 대상 아님.

## 테스트 결과

### Phase 3 빌드 검증 — 3/3 PASS

| # | 명령 | 결과 | 소요 |
|---|------|------|------|
| 1 | `./gradlew.bat :common:compileJava` | BUILD SUCCESSFUL | 3s |
| 2 | `./gradlew.bat :common:test` | BUILD SUCCESSFUL | 6s |
| 3 | `./gradlew.bat clean build` | BUILD SUCCESSFUL | 54s |

### RawDataTest 단위 테스트 — 5/5 PASS

`common/build/test-results/test/TEST-com.mo.swtp.raw.domain.RawDataTest.xml` 결과 (`tests=5 skipped=0 failures=0 errors=0 time=0.014s`):

| 케이스 | 시간 | 결과 |
|-------|------|------|
| `qualityCd_변경은_immutable_검증으로_차단된다()` | 0.004s | PASS |
| `tagSrlNo_변경은_immutable_검증으로_차단된다()` | 0.001s | PASS |
| `rawVal_변경은_immutable_검증으로_차단된다()` | 0.002s | PASS |
| `acqDtm_변경은_immutable_검증으로_차단된다()` | 0.001s | PASS |
| `corrVal_갱신은_허용된다()` | 0.001s | PASS |

### 회귀 테스트 — 사이클 1 정합 유지

`clean build` 가 common·api·scheduler 모듈 전체 테스트를 재실행했고 모두 PASS — 사이클 1 의 103건 + RawDataTest 5건 = 통합 무회귀.

### PLAN2 §성공 기준 8건 GREEN 검증

| # | 검증 | 명령 | 결과 |
|---|------|------|------|
| 1 | `RawData.changeQualityCd` 메서드 부재 | `grep changeQualityCd RawData.java` | 0건 ✓ |
| 2 | `qualityCdSnapshot @Transient` 필드 존재 | `grep qualityCdSnapshot RawData.java` | 3건 (필드·캡처·검증 분기) ✓ |
| 3 | `@PreUpdate` quality_cd 분기 존재 | `grep "quality_cd 는 INSERT-only"` | 1건 ✓ |
| 4 | 클래스 Javadoc INSERT-only 4컬럼 명시 | `grep "tag_srl_no.*acq_dtm.*raw_val.*quality_cd"` | 1건 ✓ |
| 5 | 구 `corr_val·quality_cd 만 갱신 허용` 문구 부재 | `grep "corr_val·quality_cd 만 갱신 허용"` | 0건 ✓ |
| 6 | 신 `corr_val 만 갱신 허용` 문구 존재 | `grep "corr_val} 만 갱신 허용"` | 2건 (≥1건 충족) ✓ |
| 7 | RawDataTest.java 신규 + 5 케이스 (qualityCd 차단 포함) | 파일 존재 + 메서드 컴파일 | ✓ |
| 8 | `:common:test` PASS + `clean build` PASS | gradle 출력 BUILD SUCCESSFUL | ✓ |

## 비고

### 사이클 2 흐름 종결 후 후속 작업

본 RESULT2 는 사이클 2 의 구현 결과만 다룬다. 다음 단계는 다음과 같이 진행된다:

1. **REVIEW2 작성** (`/dev:review 마스터도메인설계` 자동 전이) — 사이클 2 결과의 코드 리뷰. 블로커 1 (changeQualityCd 불일치) 해소 확인 + 사이클 2 신규 코드 (RawDataTest reflection·메시지 검증 패턴) 점검 + REVIEW1 의 미해소 권고/참고 (CORS, §2.5 면책 인용 주석, RawDataId 접근 수준, PumpControlIntegrationTest SKIP) 의 사이클 2 처리 여부 확인.
2. **REVIEW2 approved 시** REVIEW1.md `status: draft` → 사이클 1 의 블로커 해소 확인 차원에서 별도 갱신 검토 (사이클 1 fix cycle 의 doc-harness 트레이서빌리티).
3. **사이클 2 종결 시** `/dev:commit 마스터도메인설계` — 사이클 1+2 누적 변경 (51 + 2 ≈ 53 파일) 을 단일 커밋으로 묶거나, 사이클 1·2 분리 커밋. 분리 권고 — `commit-convention.md` §1 메시지 구조 + `chore` 본 사이클은 PLAN-코드 정합 정렬이므로 `chore: 마스터도메인설계 RawData immutable 정합 정렬 (사이클 2)` 또는 `refactor:` 적합.

### REVIEW1 미해소 항목과 본 사이클의 관계

| REVIEW1 항목 | 사이클 2 처리 |
|-------------|------------|
| 블로커 1 (`changeQualityCd` vs PLAN 도메인 룰) | **본 사이클에서 옵션 ② 채택으로 해소 완료** ✓ |
| 권고 1 (`dev/application.yml` CORS 변경) | PLAN2 §제외 사항 — 별도 chore 사이클 분리 (사용자 결정 2026-05-06) |
| 권고 2 (SCADA_TIMEOUT 의무 케이스 GREEN 명시) | REVIEW1 재검토 (2026-05-06) 에서 해소 확인 — 본 사이클 영향 없음 |
| 권고 3 (§2.5 면책 인용 주석) | PLAN2 §가정 — 본 사이클의 `@PreUpdate` 본문 (4 분기 18줄 미만) 50줄 미만 유지로 면책 인용 주석 미추가. 향후 메서드 50줄 임계 도달 시 별도 작업 |
| 참고 1 (`RawDataId @AllArgsConstructor`) | 본 사이클 영향 없음 — RawDataId 변경 없음 |
| 참고 2 (`PumpControlIntegrationTest` SKIP) | 본 사이클 영향 없음 — 별도 사이클 (실 PostgreSQL + V6_1~V6_5 적용 환경 E2E) |

### 기술 부채·후속 작업 식별

본 사이클에서 새로 식별된 후속 작업:

1. **`quality_cd` 사후 재평가 시나리오 정책** — `ot-integration.md §3` BAD → UNCERTAIN 격상 등 운영 시나리오 발생 시 처리 방안. 사용자 결정 (2026-05-06) 으로 본 사이클 범위 외, 사후 재평가 필요 시 별도 행 (재수집된 RawData) 또는 `corr_val` 만 갱신. `ot_integration_inbound` 별도 작업 사이클 검토 대상.
2. **시계열 immutable 검증 패턴 재사용성** — `RawData` 의 `@Transient` 스냅샷 + `@PrePersist`/`@PostLoad` 캡처 + `@PreUpdate` 검증 패턴을 `pump_ctrl_h`·`pump_predc_h`·`ai_drvn_mod_h` 등 시계열 이력 테이블에 동일 적용 검토 (필요 시 별도 ANALYZE 단계).

### 룰 갱신 영향 없음

본 사이클은 `coding-discipline.md`·`entity-patterns.md`·`db/partitioning-and-retention.md`·`ot-integration.md` 등 룰 본문의 변경 0건. PLAN-코드 정합 정렬 작업으로 룰의 의미는 그대로 유지하되 코드만 룰에 맞춤.

### 도메인 룰 정합

| 룰 항목 | 본 사이클 정합 여부 |
|--------|------------------|
| PLAN1 §도메인 룰 마지막 항목 (`corr_val` 만 갱신 허용) | ✓ 본 사이클에서 정렬 완료 |
| `db/partitioning-and-retention.md §1` `rawdata_1m_h` BaseEntity 4 적용 정책 (`corr_val` 만 갱신 허용) | ✓ |
| `entity-patterns.md` §외부 할당 PK 패턴의 `@PostLoad`·`@PrePersist` 운영 정합 (`BaseEntity newEntity` 플래그와 별개 후처리) | ✓ |
| ROOT `coding-discipline.md §2.1` 정량 기준 (메서드 50줄·추상화 3단·DTO 상속 3단) | ✓ `@PreUpdate` 4 분기 18줄 — 50줄 미만 유지 |
| ROOT `coding-discipline.md §3` 정밀한 수정 (TASK 체크박스 외 변경 금지) | ✓ TASK2 체크박스 10건 외 변경 0건 |
| `test-strategy.md §1` 단위 테스트 패턴 (한국어 메서드명·AssertJ·BDD 주석) | ✓ RawDataTest 5 케이스 일관 적용 |
