---
status: approved
created: 2026-05-06
updated: 2026-05-06
---
# 마스터도메인설계 — RawData immutable 정합성 정렬 (사이클 2)

## 목적

[REVIEW1](../../../reviews/20260503/마스터도메인설계/REVIEW1.md) 블로커 1건 (`RawData.changeQualityCd` 메서드 vs PLAN1 §도메인 룰 정합성 미정렬) 해소. `quality_cd` 를 `tag_srl_no`·`acq_dtm`·`raw_val` 과 동일한 INSERT-only immutable 컬럼으로 정렬하여 PLAN-코드 정합성을 맞춘다.

## 배경

- [이전 리뷰](../../../reviews/20260503/마스터도메인설계/REVIEW1.md) 블로커 (옵션 ② 채택) 해소
- 직전 PLAN: [PLAN1](../../20260503/마스터도메인설계/PLAN1.md) §도메인 룰 134줄 ("INSERT-only 컬럼 ... `corr_val` 만 갱신 허용") 와 코드 (RawData.java 147-149 `changeQualityCd` 메서드 + 161줄 Javadoc "corr_val·quality_cd 만 갱신 허용") 의 모순 상태
- Fix Cycle 2 는 [`doc-harness/README.md` §수정 사이클](../../../../.claude/rules/process/doc-harness/README.md#수정-사이클-fix-cycle) 의 Fix Cycle 감지 알고리즘에 따라 **PLAN2 직행** (블로커 텍스트에 도메인 정합성 키워드 미포함 → ANALYZE 재진입 불요)
- 사용자 결정 (2026-05-06):
  1. **블로커 1 해소 = 옵션 ②** — quality_cd 도 INSERT-only immutable 로 강제. "수집 시 결정된 quality_cd 는 immutable, 사후 재평가·재수집에서도 변동 없음"
  2. **권고 1 (CORS) = 별도 chore 사이클 분리** — 본 PLAN2 범위 외
  3. **재수집 프로세스 = 본 사이클 범위 외** — 별도 ot_integration_inbound 작업 사이클

## 범위

- `common/src/main/java/com/mo/swtp/raw/domain/RawData.java` — `changeQualityCd` 메서드 제거 + `@PreUpdate` 에 `quality_cd` 스냅샷 비교 추가 + `qualityCdSnapshot` `@Transient` 필드 + `captureImmutableSnapshot` 갱신 + Javadoc 정렬 (클래스·메서드)
- `common/src/test/java/com/mo/swtp/raw/domain/RawDataTest.java` — **신규 작성**. immutable 검증 단위 테스트 (4개 컬럼 각각의 변경 시 `IllegalStateException` 발생, `corr_val` 갱신은 허용)

**범위 외**:
- `dev/application.yml` CORS 변경 — 별도 chore 사이클 분리 (사용자 결정)
- raw 데이터 재수집 프로세스·SCADA 인바운드 어댑터 구현 — `ot_integration_inbound` 별도 작업
- `RawData` Service·Repository 추가 메서드 — 본 사이클은 엔티티 immutable 정합 정렬만
- `PLAN1.md` §도메인 룰 134줄 본문 변경 — 옵션 ② 선택으로 PLAN1 본문은 그대로 유효 (corr_val 만 갱신 허용 정책 유지)
- `RESULT1.md` §계획 외 변경 추가 — 본 사이클이 RESULT2 별도 작성으로 흐름 분리

## 도메인 모델

변경 없음. 기존 RawData 엔티티의 immutable 검증 정렬만 수행한다.

| 엔티티/테이블 | 역할 | 변경 내용 |
|------|------|---------|
| `RawData` (`rawdata_1m_h`) | SCADA 원시 데이터 1분 시계열 | INSERT-only 컬럼 4개 (`tag_srl_no`·`acq_dtm`·`raw_val`·`quality_cd`) immutable 검증 강제. `corr_val` 만 갱신 허용. 컬럼 정의·제약 변경 없음 |

## DB 설계 변경

없음. DDL 변경 0건. `rawdata_1m_h` 테이블 스키마는 PLAN1 V6_5 그대로 유효 (`COMMENT ON COLUMN` 갱신 없음).

## 적용할 패턴

- **시계열 immutable 검증 패턴 (스냅샷 비교)** — 기존 `RawData @PrePersist`/`@PostLoad`/`@PreUpdate` 패턴에 `qualityCdSnapshot` 필드만 추가. [`entity-patterns.md`](../../../../.claude/rules/entity-patterns.md) §외부 할당 PK 엔티티 패턴의 `@PostLoad`·`@PrePersist` 운영 정합 (BaseEntity `newEntity` 플래그와 별개로 본 엔티티 전용 후처리)
- **`@Transient` 스냅샷 필드 ↔ `@PrePersist`·`@PostLoad` 캡처** — DB 영속 컬럼 아님, 메모리 비교 전용

## 도메인 룰 (PLAN 제약 — 필수 명시)

- **RawData INSERT-only 컬럼 4종 (확정)**: `tag_srl_no`·`acq_dtm`·`raw_val`·**`quality_cd`** 모두 INSERT 시점 고정값. 갱신 시도 시 `@PreUpdate` 가 `IllegalStateException` 으로 차단. PLAN1 §도메인 룰 134줄의 "`corr_val` 만 갱신 허용" 정책 유지 (`quality_cd` 는 갱신 허용 컬럼 목록에 포함되지 않음)
- **`quality_cd` immutable 의의** (사용자 결정 2026-05-06): SCADA 수집 시 결정된 GOOD/BAD/UNCERTAIN 값은 그 시점의 신뢰도 평가이며, 사후에 외부 평가로 변경하지 않는다. 사후 재평가가 필요하면 별도 행 (재수집된 RawData) 으로 표현하거나 `corr_val` 만 갱신
- **`corr_val` 만 갱신 허용** (PLAN1 §도메인 룰 인용 — 변경 없음): Hold Last Value 적용 결과 또는 운영자 사후 보정. `quality_cd` 와 무관 — `corr_val` 갱신 시 `quality_cd` 동시 갱신 금지
- **재수집 프로세스 범위 외** (사용자 결정 2026-05-06): SCADA 수신 데이터 부재 시 재수집·복구 프로세스 자체는 현재 사이클 범위 외. 본 PLAN2 는 엔티티 immutable 검증 강제만 다루며, 수집·재수집 흐름은 별도 `ot_integration_inbound` 작업 사이클
- **PLAN1 §도메인 룰 §`corr_val` 갱신과 SCADA_TIMEOUT 분리 정합 유지**: `corr_val` 갱신은 `ai_drvn_mod_p.last_rcv_dtm` 갱신·`ai_drvn_mod_h` 행 추가와 무관 (PLAN1 132줄 인용 — 변경 없음)

## 구현 방향

### Phase 1: RawData 코드 정렬 (1 파일 단일 변경)

- `RawData.java` 147-149줄 `changeQualityCd(QualityCode)` 메서드 **제거**
- `@Transient` `qualityCdSnapshot` 필드 추가 (다른 3개 스냅샷 필드와 동일 패턴)
- `captureImmutableSnapshot()` 메서드 본문에 `qualityCdSnapshot = qualityCd` 한 줄 추가
- `@PreUpdate onPreUpdateValidateImmutable()` 본문에 `quality_cd` 스냅샷 비교 분기 추가 (3개 분기와 동일 패턴, `IllegalStateException` 메시지 동일 형식)
- 클래스 Javadoc 35-39줄 INSERT-only 컬럼 목록 갱신: `tag_srl_no`·`acq_dtm`·`raw_val` → **`tag_srl_no`·`acq_dtm`·`raw_val`·`quality_cd`**
- 클래스 Javadoc "corr_val 만 갱신 허용" 문구는 그대로 유지
- `@PreUpdate` Javadoc 161줄 "corr_val·quality_cd 만 갱신 허용 (PLAN1 §도메인 룰)" → **"corr_val 만 갱신 허용 (PLAN1 §도메인 룰)"** 정렬

### Phase 2: 단위 테스트 신규 작성

`common/src/test/java/com/mo/swtp/raw/domain/RawDataTest.java` 신규 — `@ExtendWith(MockitoExtension.class)` 미사용 (entity 자기 단위 테스트). `[`test-strategy.md` §1`](../../../../.claude/rules/test-strategy.md) 단위 테스트 패턴 적용.

테스트 케이스 (한국어 메서드명 관행 유지):
- `corrVal_갱신은_허용된다` — `RawData.create(...)` → `captureImmutableSnapshot()` → `changeCorrVal(...)` → `onPreUpdateValidateImmutable()` 호출 시 예외 없음
- `qualityCd_변경은_immutable_검증으로_차단된다` — `quality_cd` 만 변경 후 `onPreUpdateValidateImmutable()` 호출 시 `IllegalStateException` 발생, 메시지에 "quality_cd 는 INSERT-only" 포함
- `tagSrlNo_변경은_immutable_검증으로_차단된다` — 기존 도메인 룰 회귀 검증
- `acqDtm_변경은_immutable_검증으로_차단된다` — 기존 도메인 룰 회귀 검증
- `rawVal_변경은_immutable_검증으로_차단된다` — 기존 도메인 룰 회귀 검증

> 본 테스트는 `IllegalStateException` 메시지 검증을 포함한다 (테스트 강도 = 메시지 일치). `JpaRepository`·EntityManager 미사용 — 도메인 엔티티 단위 검증.
>
> **재현 테스트 의무 (ROOT [`coding-discipline.md §4.3`](../../../../../.claude/rules/coding-discipline.md))**: 본 PLAN 은 `fix:` 타입은 아니지만 (`refactor:` 또는 `chore:` 범주) Fix Cycle 의 블로커 해소 작업이므로 첫 번째 체크박스를 "qualityCd_변경은_immutable_검증으로_차단된다 RED 확인" 으로 시작 후 코드 수정으로 GREEN 전환 흐름을 권장 (TDD 강제는 아님).

### Phase 3: 빌드 검증

- `./gradlew.bat :common:compileJava` BUILD SUCCESSFUL
- `./gradlew.bat :common:test` PASS (RawDataTest 5케이스 + 기존 테스트 회귀 없음)
- `./gradlew.bat clean build` BUILD SUCCESSFUL — `common`·`api`·`scheduler` 전체 회귀 검증

## 성공 기준 (검증 가능 형태)

> ROOT [`coding-discipline.md §4.2`](../../../../../.claude/rules/coding-discipline.md) 적용. 각 기준에 검증 명령·테스트·조회 명시.

- [ ] `RawData.changeQualityCd` 메서드 부재 → 검증: grep changeQualityCd common/src/main/java/com/mo/swtp/raw/domain/RawData.java 매칭 0건
- [ ] `RawData.qualityCdSnapshot` `@Transient` 필드 존재 → 검증: grep qualityCdSnapshot common/src/main/java/com/mo/swtp/raw/domain/RawData.java 매칭 3건 이상 (필드 선언·캡처·검증)
- [ ] `@PreUpdate` 에 quality_cd 스냅샷 비교 분기 존재 → 검증: grep "quality_cd 는 INSERT-only" RawData.java 매칭 1건
- [ ] 클래스 Javadoc INSERT-only 4개 컬럼 명시 → 검증: grep "tag_srl_no.*acq_dtm.*raw_val.*quality_cd" RawData.java 매칭 1건 이상
- [ ] @PreUpdate Javadoc "corr_val 만 갱신 허용" 정렬 → 검증: grep "corr_val·quality_cd 만 갱신 허용" RawData.java 매칭 0건 + grep "corr_val 만 갱신 허용" RawData.java 매칭 1건 이상
- [ ] `RawDataTest.java` 신규 작성 → 검증: 파일 존재 + 테스트 메서드 5개 (qualityCd 차단 케이스 포함)
- [ ] `./gradlew.bat :common:test` BUILD SUCCESSFUL 출력 확인 (RawDataTest 5 PASS)
- [ ] `./gradlew.bat clean build` BUILD SUCCESSFUL 출력 확인

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md §1`](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE 의 가정·미해결 질문을 PLAN 단계 결정으로 변환. 최소 1건 이상 기재 의무.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `quality_cd` immutable 강제 시 사후 재평가 시나리오 (BAD → UNCERTAIN 격상) 발생 시 처리 방안 | 결정 | 사용자 결정 (2026-05-06) — 본 사이클 범위 외. 사후 재평가 필요 시 별도 행 (재수집된 RawData) 으로 표현하거나 `corr_val` 만 갱신. `quality_cd` 직접 갱신 경로는 도입하지 않음 |
| `dev/application.yml` CORS 변경 처리 | 결정 | 사용자 결정 (2026-05-06) — 별도 chore 사이클 분리. 본 PLAN2 §범위 외에 명시. RESULT1 §계획 외 변경 §우연 1 그대로 유지 |
| raw 데이터 재수집 프로세스 (SCADA 부재 시 복구) | 결정 | 사용자 결정 (2026-05-06) — 본 사이클 범위 외. 별도 `ot_integration_inbound` 작업 사이클로 분리 |
| §2.5 면책 인용 주석 (REVIEW1 권고 3) | 가정 | 본 PLAN2 의 `@PreUpdate` 본문 확장 (50줄 미만 유지 — 1개 분기 추가) 으로 정량 기준 위반 없음. 면책 인용 주석은 미추가 (메서드 길이 임계 도달 시 별도 작업) |
| `RawDataTest` 위치 | 가정 | `common/src/test/java/com/mo/swtp/raw/domain/RawDataTest.java` — 도메인 엔티티 단위 테스트는 common 모듈 (`common/CLAUDE.md` §허용 범위 §도메인 enum·VO·composite key 정합) |
| `RawDataTest` 메서드 네이밍 | 가정 | 한국어 `_` 구분 (현 프로젝트 관행 — `test-strategy.md §1` "한국어 메서드명(`_`) 사용") |

## 제외 사항

- `dev/application.yml` CORS 변경 (별도 chore 사이클 — 사용자 결정 2026-05-06)
- raw 데이터 재수집 프로세스 (별도 `ot_integration_inbound` 작업 — 사용자 결정 2026-05-06)
- `RawData` Service·Repository 추가 메서드 (`api` 모듈 — 본 사이클은 엔티티 정합 정렬만)
- `PLAN1.md` §도메인 룰 134줄 본문 변경 (옵션 ② 선택으로 PLAN1 본문 그대로 유효)
- `RESULT1.md` §계획 외 변경 §의도 추가 (본 사이클이 별도 RESULT2 작성으로 흐름 분리)
- `ai-server` Pydantic 스키마 동기화 (`pump_predc_h.facility_id` 변경 — RESULT1 §후속 작업 2 인용, 별도 ai-server 작업 사이클)

## 예상 산출물

- [태스크](../../../tasks/20260506/마스터도메인설계/TASK2.md) (단일 — 분할 기준 미만 — Phase 3 / 체크박스 ~10건)

---

## 부록: 도메인/DB 검토 결과 (2026-05-06)

본 PLAN2 는 도메인 모델·DB 설계 변경 모두 부재 (엔티티 immutable 검증 정합 정렬만 수행) — `/dev:plan §검토 게이트` 생략 조건 충족.

- `## 도메인 모델` 신규 엔티티·테이블·필드 0건 → `wtp-domain-expert` 검토 생략
- `## DB 설계 변경` 내용 부재 → `wtp-dba-reviewer` 검토 생략
- 본 PLAN2 의 작업 본질은 PLAN1 §도메인 룰 134줄 ("`corr_val` 만 갱신 허용") 의 **코드 정렬** — 도메인 룰 자체의 신규 결정 0건 → 5인 회의 재소집 불필요

도메인 룰 변경 0건 + 코드/테스트 변경 1+1 파일 → Small 규모. PLAN approve 비차단.
