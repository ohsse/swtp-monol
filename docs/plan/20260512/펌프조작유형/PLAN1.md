---
status: approved
created: 2026-05-12
updated: 2026-05-12
---
# 펌프조작유형 — PLAN1

## 목적

펌프의 물리적 설계값인 조작유형 (자동 조작 가능 ⊕ 반자동 조작 가능, 상호 배타) 을 `pump_m` 마스터에 도입한다. 본 사이클은 마스터 컬럼 추가까지 한정하며, `ai_mode_cd` × `oprtng_type_cd` 인터록 제약 검증 로직과 화면 §2 표시 매핑은 별도 사이클로 분리한다.

## 배경

- [ANALYZE1](../../../analyze/20260512/펌프조작유형/ANALYZE1.md) — 5인 회의 6 안건 결론. 룰 갱신 지시서 3건 모두 완료, `status: approved`.
- 미결 안건 연결: [송수펌프제어분석 ANALYZE1 안건 7](../../../analyze/20260508/송수펌프제어분석/ANALYZE1.md) — 화면 §2 "자동/반자동" 표시 기준 모호. 본 PLAN 은 마스터 도메인 측 상위 결정만 담당.

## 범위

### 포함

- `PumpOprtngType` enum 신설 (`com.mo.swtp.pump.domain`) — `AUTO_CAPABLE` / `SEMI_AUTO_CAPABLE`.
- `Pump` 자식 엔티티에 `oprtng_type_cd` 컬럼 매핑 필드 추가.
- `Pump.create()` 정적 팩토리 시그니처에 `PumpOprtngType` 인자 추가 + 호출처 일괄 갱신.
- `V8_5__pump_m_oprtng_type.sql` 마이그레이션 — 3단계 무중단 + 백필 `AUTO_CAPABLE` + COMMENT.
- 단위 테스트 추가 (`PumpTest` — 정적 팩토리·매핑 검증).

### 제외 (별도 사이클 분리)

- `ai_mode_cd` × `oprtng_type_cd` 인터록 제약 (`AI_AUTO` + `SEMI_AUTO_CAPABLE` 펌프 AI 제어 제외 룰) — 인터록 강제 로직.
- 화면 §2 "자동/반자동" 표출 매핑 (`ai_mode_cd` vs `ai_drvn_mod` vs `oprtng_type_cd`) — 송수펌프제어분석 차후 사이클.
- 응답 DTO / Controller 신설 — 본 사이클은 마스터 컬럼만, DTO 노출은 송수펌프제어분석 후속 PLAN 의 책임.
- valve/flwmtr 등 instrument 타 자식에 동일 패턴 적용 — 자식 3건 이상 도입 시 instrument 부모 이관 별도 ANALYZE.

## 구현 방향

### 1. enum 신설 — `PumpOprtngType`

- 위치: `common/src/main/java/com/mo/swtp/pump/domain/PumpOprtngType.java`
- 값: `AUTO_CAPABLE` / `SEMI_AUTO_CAPABLE` 2종. 상호 배타.
- Javadoc: 펌프의 물리적 설계값 (제조사 PLC 회로 결선) 임을 명시. `AiSystemModeCode` 시스템 상태와의 의미 차이 명기.

### 2. `Pump` 엔티티 변경

- 위치: `common/src/main/java/com/mo/swtp/pump/domain/Pump.java` (현재 패키지 — 마스터도메인설계 이관 시 동행)
- 추가 필드:
  ```java
  @Enumerated(EnumType.STRING)
  @Column(name = "oprtng_type_cd", nullable = false, length = 20)
  private PumpOprtngType oprtngType;
  ```
- `Pump.create()` 정적 팩토리 시그니처에 `PumpOprtngType oprtngType` 인자 추가.
- 호출처 (`PumpService` 등) 일괄 갱신 — `git grep "Pump\.create\("` 로 식별 후 인자 추가.

### 3. DB 마이그레이션 — `V8_5__pump_m_oprtng_type.sql`

- 위치: `common/src/main/resources/db/init/V8_5__pump_m_oprtng_type.sql`
- 3단계 무중단 (`db/indexing-and-migration.md §2`):
  ```sql
  -- 1단계: NULL 허용 ADD COLUMN
  ALTER TABLE pump_m ADD COLUMN oprtng_type_cd VARCHAR(20);
  COMMENT ON COLUMN pump_m.oprtng_type_cd IS '펌프 조작유형 (DOM_CODE_20, AUTO_CAPABLE/SEMI_AUTO_CAPABLE — 펌프의 물리적 설계값, PumpOprtngType enum 매핑)';

  -- 2단계: 백필 (기본값 AUTO_CAPABLE — 한국 지자체 송수펌프 실무 표준 회로)
  UPDATE pump_m SET oprtng_type_cd = 'AUTO_CAPABLE' WHERE oprtng_type_cd IS NULL;

  -- 3단계: NOT NULL 전환
  ALTER TABLE pump_m ALTER COLUMN oprtng_type_cd SET NOT NULL;
  ```
- DDL CHECK 제약 미적용 — `@Enumerated(EnumType.STRING)` + enum 단일 방어선 (`db/indexing-and-migration.md §3.2` DOM_YN 정책 유추).
- 단독 인덱스 미적용 — 카디널리티 2.

### 4. 운영자 백필 검토 체크리스트 (RESULT 명시 의무)

본 사이클 RESULT1.md 의 "## 비고" 섹션에 다음 체크리스트 명시:
- [ ] 운영자가 V8_5 마이그레이션 후 반자동 회로 펌프 식별 — 멀티테넌트 별 (`gs`/`gm2`/`hy` 등).
- [ ] 식별 펌프에 대해 `UPDATE pump_m SET oprtng_type_cd = 'SEMI_AUTO_CAPABLE' WHERE pump_id IN (...);` 실행.
- [ ] 백필 검증: `SELECT pump_id, oprtng_type_cd FROM pump_m ORDER BY oprtng_type_cd, pump_id;` 결과 운영자 재확인.

> 누락 시 도메인 룰 평가 오작동 위험 (예: AI 자동 모드 평가 시 반자동 펌프를 자동으로 오인) — `wtp-dba-reviewer` 안건 4 권고.

### 5. 단위 테스트

- 위치: `common/src/test/java/com/mo/swtp/pump/domain/PumpTest.java` (신규 또는 기존 확장)
- 테스트 케이스:
  - `Pump.create()` 정적 팩토리 — `AUTO_CAPABLE` / `SEMI_AUTO_CAPABLE` 각각 생성 검증.
  - 매핑 검증 — `@Enumerated(EnumType.STRING)` 으로 DB VARCHAR(20) 값과 일치.

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령·테스트·조회 |
|------|---------------------|
| `PumpOprtngType` enum 매핑 + `Pump.create()` 정적 팩토리 호환 | 단위 테스트 GREEN — `./gradlew.bat :common:test --tests "*PumpTest*"` 통과 (신규 2건 + 기존 회귀 0건) |
| V8_5 마이그레이션 3단계 무중단 적용 + 백필 | 로컬 PostgreSQL 마이그레이션 순차 실행 후 `SELECT COUNT(*) FROM pump_m WHERE oprtng_type_cd IS NULL` 결과 0 확인 + `SELECT DISTINCT oprtng_type_cd FROM pump_m` 결과 `AUTO_CAPABLE` 만 노출 (운영자 보정 전 상태) |
| 전체 빌드 통과 | `./gradlew.bat clean build` BUILD SUCCESSFUL 출력 확인 (호출처 일괄 갱신 검증 포함) |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| 시설 내 `AUTO_CAPABLE` + `SEMI_AUTO_CAPABLE` 펌프 혼재가 정수지 실무상 빈번 | **결정** | 혼재 허용. 인터록 제약 검증 로직은 본 사이클 범위 외 — 별도 ANALYZE 사이클 분리 (안건 5 결론) |
| 기존 V8_2 적용 운영 환경의 펌프 마스터 행 수 (멀티테넌트별) | **결정** | 백필 SQL 은 V8_3 단일 UPDATE 배치 (멀티테넌트 별 행 수 소수 가정 — 수십~수백 건). 대용량 시 배치 분할 (`db/indexing-and-migration.md §2`) PLAN 보강 검토 |
| valve/flwmtr 등 instrument 타 자식 자동/반자동 구분 필요성 | **결정** | 현 사이클은 펌프 한정. 자식 종류 3건 이상 도입 시 instrument 부모 이관 별도 ANALYZE (안건 2 `wtp-dba-reviewer` 권고) |
| 호출처 (`Pump.create()` 호출 코드) 누락 시 컴파일 오류 처리 | 가정 | Java 컴파일러가 누락 검출. PLAN 단계 git grep 으로 사전 식별 + TASK 체크박스에 호출처 파일 경로 모두 명시 |

## 제외 사항

- 응답 DTO / Controller / Swagger 노출 — 송수펌프제어분석 후속 PLAN 의 책임 (본 사이클 ANALYZE1 안건 6 결론).
- `ai_mode_cd` × `oprtng_type_cd` 인터록 강제 로직 — pump+AI 도메인 백지화 사이클 2 에서 시스템 상태 enum 신규 정의 후 별도 ANALYZE 사이클 (본 사이클 ANALYZE1 안건 5 결론 + IMPL 단계 발견 백지화 컨텍스트).
- 운영자 백필 보정 SQL 자동화 — 멀티테넌트 운영 환경 의존, 운영자 수동 실행.
- 마스터도메인설계 이관 (`com.mo.swtp.pump` → `com.mo.swtp.instrument`) — 본 사이클 범위 외 (마스터도메인설계 PLAN approved 후 별도 사이클).

## 예상 산출물

- [태스크](../../../tasks/20260512/펌프조작유형/TASK1.md) — PLAN approved 후 작성. 5개 Phase 예상 (enum 신설 / Pump 엔티티 변경 / 호출처 일괄 갱신 / V8_5 마이그레이션 / 단위 테스트). 체크박스 ~15건. 분할 불필요 (TASK 분할 기준 Phase 10 이상 미해당).

## 규모 분류

본 사이클은 **Medium** (마스터 컬럼 1건 + enum 1건 + 마이그레이션 1건 + 호출처 일괄 갱신). Large 분기 (RESULT/REVIEW 의무) 미적용 — 사용자 분류 확인 필요.
