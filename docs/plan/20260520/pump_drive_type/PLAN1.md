---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# 펌프 구동 방식 분류 — 구현 계획

## 목적

펌프 마스터(`pump_m` — `instrument_m` JPA JOINED 자식, `@DiscriminatorValue("PUMP")`) 에 펌프의 **구동 방식** 분류 필드 `drive_type_cd` 를 추가하여 인버터 펌프(가변속) / 정격 펌프(고정속) 를 구분한다. 본 사이클은 표출/저장 + 조합 유효성 검증(도메인 안전 직결) 까지 포함하며, 사이클 2 AI 재설계 시 인터록·운전 모드 진입 룰 분기 입력으로 활용 가능한 정적 사양 컬럼을 확보한다.

## 배경

- ANALYZE 결정: [ANALYZE1.md](../../../analyze/20260520/pump_drive_type/ANALYZE1.md) 의 5인 회의 결론 적용
- 신규 어휘: `drive` 표준 단어 + `drive_type_cd` 표준 용어 등록 완료 (ROOT `swtp/.claude/rules/dict/standard-words.md` + backend `.claude/rules/dict/standard-terms.md`)
- 기존 `oprtng_type_cd`(조작 유형) 와 직교축: 제어 방식 vs 구동 방식
- 도메인 안전 블로커: `RATED_DRIVE + AUTO_CAPABLE` 조합은 물리 제약상 무효 (정격 펌프 가변속 불가). 애플리케이션 레벨 차단 의무 (`ot-integration.md §5 ⚠️ 절대 금지` 직결)
- 백필 정책: `RATED_DRIVE` (fail-safe — INVERTER 잘못 분류 시 OT 안전 위협 회피)

## 범위

### 영향 모듈

- `common` — 도메인 엔티티(`Pump`), enum 신설(`PumpDriveType`), DDL 마이그레이션(V9_3)
- `api` — Controller·Service 변경 (DTO 수용), `InstrumentErrorCode` enum 추가, `PumpDto`·`PumpUpsertDto` 변경
- `scheduler` — 영향 없음

### 변경 파일 목록

| 분류 | 파일 | 변경 유형 |
|------|------|---------|
| enum 신설 | `swtp/backend/common/src/main/java/com/mo/swtp/instrument/domain/PumpDriveType.java` | 신규 |
| 엔티티 변경 | `swtp/backend/common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` | 필드·정적 팩토리·변경 메서드 시그니처 확장 |
| DTO 변경 | `swtp/backend/api/src/main/java/com/mo/swtp/instrument/dto/PumpDto.java` | `driveType` 필드 + `@Schema(implementation=)` 추가 |
| DTO 변경 | `swtp/backend/api/src/main/java/com/mo/swtp/instrument/dto/PumpUpsertDto.java` | `driveType` 필드 + `@NotNull` + `@Schema(implementation=)` 추가 |
| Service 변경 | `swtp/backend/api/src/main/java/com/mo/swtp/instrument/service/InstrumentService.java` | 호출 라인 87·113 인자 추가 |
| ErrorCode 변경 | `swtp/backend/api/src/main/java/com/mo/swtp/instrument/exception/InstrumentErrorCode.java` | `INVALID_PUMP_DRIVE_OPRTNG_COMBINATION(400)` 추가 |
| DDL 신설 | `swtp/backend/common/src/main/resources/db/init/V9_3__pump_m_drive_type_cd.sql` | 신규 (3단계 무중단 마이그레이션) |
| 테스트 변경 | `swtp/backend/api/src/test/java/com/mo/swtp/instrument/InstrumentServiceTest.java` | line 65-67·143-145·161-163 호출 갱신 |
| 테스트 변경 | `swtp/backend/api/src/test/java/com/mo/swtp/instrument/PumpDtoSerializationTest.java` | line 34·49·61 호출 갱신 + `driveType` 직렬화 검증 추가 |
| 테스트 변경 | `swtp/backend/common/src/test/java/com/mo/swtp/instrument/PumpSelfColumnsTest.java` | line 28·36·45 호출 갱신 + `driveType` null 검증 추가 |
| 테스트 신설 | `swtp/backend/common/src/test/java/com/mo/swtp/instrument/PumpDriveTypeTest.java` | 신규 — enum 2값 확인 |
| 테스트 신설 | `swtp/backend/common/src/test/java/com/mo/swtp/instrument/PumpDriveOprtngCombinationTest.java` | 신규 — 4 조합 유효성 (`RATED_DRIVE + AUTO_CAPABLE` 차단) |

> Service 호출 라인 번호는 사전 분석 시점(2026-05-20) 의 절대 라인이다. 구현 시점에 라인이 이동될 수 있으므로 grep 으로 재확인한다.

## 도메인 모델

### 신규 enum — `PumpDriveType`

| enum 값 | 의미 | 도메인 룰 |
|--------|------|---------|
| `INVERTER_DRIVE` | 인버터 펌프 (가변속, VFD 주파수 제어) | AI 자동 제어 가능 (`AUTO_CAPABLE`·`SEMI_AUTO_CAPABLE` 모두 허용) |
| `RATED_DRIVE` | 정격 펌프 (고정속, ON/OFF 만) | `SEMI_AUTO_CAPABLE` 만 허용. `AUTO_CAPABLE` 조합 차단 의무 |

- 패키지: `com.mo.swtp.instrument.domain` (`PumpOprtngType` 동일)
- 패턴: 단순 public enum (메서드·필드 없음, Javadoc 만)
- DB 매핑: `pump_m.drive_type_cd VARCHAR(20) NOT NULL` + `@Enumerated(EnumType.STRING)`

### `Pump` 엔티티 신규 필드

| 필드 | 자료형 | DB 컬럼 | NOT NULL | 매핑 |
|------|------|--------|---------|------|
| `driveType` | `PumpDriveType` | `drive_type_cd` VARCHAR(20) | ✅ | `@Enumerated(EnumType.STRING)` + `@Column(name="drive_type_cd", length=20, nullable=false)` |

### `Pump` 정적 팩토리 시그니처 변경

```java
// 기존
public static Pump create(
    String instrumentNm, Facility facility, Integer dispOrd,
    BigDecimal ratedHead, BigDecimal ratedFlwrt, PumpOprtngType oprtngType)

// 신규 (인자 7개)
public static Pump create(
    String instrumentNm, Facility facility, Integer dispOrd,
    BigDecimal ratedHead, BigDecimal ratedFlwrt,
    PumpOprtngType oprtngType, PumpDriveType driveType)
```

- `Objects.requireNonNull(driveType, ...)` 검증 추가
- **조합 유효성 검증 추가** — `RATED_DRIVE + AUTO_CAPABLE` 입력 시 `throw new RestApiException(InstrumentErrorCode.INVALID_PUMP_DRIVE_OPRTNG_COMBINATION)`

### `Pump.changePumpSelfColumns()` 시그니처 변경

```java
// 신규 — 4번째 인자 driveType 추가, null 인자는 기존값 유지
public void changePumpSelfColumns(
    BigDecimal ratedHead, BigDecimal ratedFlwrt,
    PumpOprtngType oprtngType, PumpDriveType driveType)
```

- 갱신 결과값으로 조합 유효성 재검증 의무 — 갱신 후 `(this.driveType, this.oprtngType)` 이 `RATED_DRIVE + AUTO_CAPABLE` 이면 동일 ErrorCode
- **종료 직전 조합 검증은 입력 인자 조합과 무관하게 항상 실행** — 모든 인자 null 입력(`null·null·null·null`) 이어도 종료 직전 `(this.driveType, this.oprtngType)` 조합 검증 생략 금지. 근거: 마이그레이션 백필 직후(`RATED_DRIVE` 일괄 적용) `oprtngType = AUTO_CAPABLE` 인 기존 행 + `driveType = RATED_DRIVE`(백필) 조합이 DB에 잠재 — 운영자 수동 갱신 전 기간에 무효 조합 행 존재 가능. 이 행에 대한 null·null·null·null 호출이 검증을 우회하면 `ot-integration.md §5 ⚠️ 절대 금지` (검증 건너뛴 채 재시도) 직결. 검증 생략 분기 도입 금지 — `wtp-domain-expert` PLAN 검토 블로커 (2026-05-20)

### `InstrumentErrorCode` 신규 enum 값

| 상수 | httpStatus | 의미 |
|------|-----------|------|
| `INVALID_PUMP_DRIVE_OPRTNG_COMBINATION` | 400 | `RATED_DRIVE + AUTO_CAPABLE` 조합 무효 (정격 펌프 가변속 불가) |

Javadoc 명시: 본 검증은 `Pump.create()` / `Pump.changePumpSelfColumns()` 진입 시 발생. `ot-integration.md §5 ⚠️ 절대 금지` 직결 — 미차단 시 사이클 2 AI 제어 단계에서 OT 안전 위협.

### `PumpDto` / `PumpUpsertDto` 필드 추가

- `PumpDto`: `@Schema(description="펌프 구동 방식 (인버터/정격)", implementation = PumpDriveType.class) private PumpDriveType driveType;`
- `PumpUpsertDto`: `@Schema(...) @NotNull private PumpDriveType driveType;`
- 패턴: 기존 `oprtngType` 필드 `@Schema(implementation=PumpOprtngType.class)` 와 동일
- `applyAuditMeta()` 흐름 무변경

## DB 설계 변경

### 신규 컬럼

`pump_m.drive_type_cd VARCHAR(20) NOT NULL` — `DOM_CODE_20` 재사용

| 항목 | 결정 |
|------|------|
| SQL 타입 | `VARCHAR(20)` |
| NULL 정책 | NOT NULL |
| DDL DEFAULT | 미설정 (Java 정적 팩토리 명시 할당 SSOT — `use_yn_consistency` 선례) |
| CHECK 제약 | 미적용 (`@Enumerated(EnumType.STRING)` + enum 1차 차단) |
| 인덱스 | 단독 미적용 (카디널리티 2 — `oprtng_type_cd` 선례 동일) |
| COMMENT | 의무화 (`indexing-and-migration.md §4`) |

### 무중단 마이그레이션 전략 — V9_3 3단계

`V8_5__pump_m_oprtng_type.sql` 선례 동일 패턴 (`indexing-and-migration.md §2`):

**파일**: `swtp/backend/common/src/main/resources/db/init/V9_3__pump_m_drive_type_cd.sql`

```sql
-- pump_m.drive_type_cd 컬럼 추가 (pump_drive_type ANALYZE1 안건 2·4·5, 2026-05-20)
-- 3단계 무중단 마이그레이션 (db/indexing-and-migration.md §2, V8_5 선례)
-- DDL CHECK 미적용 — @Enumerated(EnumType.STRING) + PumpDriveType enum 단일 방어선

-- 1단계: NULL 허용 ADD COLUMN (즉시 완료, 락 없음)
ALTER TABLE pump_m ADD COLUMN drive_type_cd VARCHAR(20);
COMMENT ON COLUMN pump_m.drive_type_cd IS '구동 방식 코드 (DOM_CODE_20, INVERTER_DRIVE/RATED_DRIVE — 펌프 물리적 설계값, PumpDriveType enum 매핑)';

-- 2단계: 기존 행 백필 — fail-safe 기본값 RATED_DRIVE (pump_drive_type ANALYZE1 안건 4)
-- 근거: INVERTER 잘못 분류 시 가변속 명령이 정격 PLC 송신 → OT 안전 위협 (ot-integration.md §5 ⚠️ 절대 금지)
--      RATED 잘못 분류 시 AI 자동 제어 대상에서 제외될 뿐 — 기능 비활성화로 위험 비대칭
-- ⚠️ 운영자는 본 마이그레이션 후 인버터 펌프 식별하여 INVERTER_DRIVE 로 보정해야 함
--    (누락 시 AI 자동 운전 비활성화 — 운영 영향이나 OT 안전 사고 아님)
UPDATE pump_m SET drive_type_cd = 'RATED_DRIVE' WHERE drive_type_cd IS NULL;

-- 3단계: NOT NULL 전환
ALTER TABLE pump_m ALTER COLUMN drive_type_cd SET NOT NULL;
```

### 운영 데이터 처리

- **운영 데이터 0건 시**: 1단계 ADD + 3단계 SET NOT NULL 즉시 완료, 2단계 UPDATE 는 0건 영향 (안전)
- **운영 데이터 존재 시**: `RATED_DRIVE` 백필 → 운영자 인버터 펌프 식별 후 `INVERTER_DRIVE` 수동 갱신 절차 필요 (운영 안내 문서화 가정 섹션)
- 3단계 패턴은 양 경우 모두 안전

## 구현 방향

### Phase 1: 어휘 사전 갱신 (완료)

ANALYZE 단계의 룰 갱신 지시서 3건 완료 (체크박스 모두 `- [x]`).

### Phase 2: enum + 엔티티 + ErrorCode 신설/변경 (common 모듈)

1. `PumpDriveType.java` 신규 — `PumpOprtngType` 패턴 그대로 (단순 enum, Javadoc, 메서드 없음)
2. `Pump.java` 변경 — `driveType` 필드 + 정적 팩토리·변경 메서드 시그니처 확장 + 조합 유효성 검증 로직
3. `InstrumentErrorCode.java` 에 `INVALID_PUMP_DRIVE_OPRTNG_COMBINATION(400)` 추가

### Phase 3: DTO 변경 (api 모듈)

1. `PumpDto.java` — `driveType` 필드 + `@Schema(implementation=PumpDriveType.class)`
2. `PumpUpsertDto.java` — `driveType` 필드 + `@NotNull` + `@Schema(...)`

### Phase 4: Service 호출처 갱신

`InstrumentService.java` line 87·113 호출 인자에 `dto.getDriveType()` 추가

### Phase 5: 테스트 호출처 갱신 + 신규 테스트

기존 호출처 (3개 테스트 파일 9건) 갱신 + 신규 테스트 2건:
- `PumpDriveTypeTest` — 2 값 정의·`name()` 직렬화 확인
- `PumpDriveOprtngCombinationTest` — 4 조합 유효성 (3 정상 + 1 차단)

### Phase 6: DDL 마이그레이션 작성

`V9_3__pump_m_drive_type_cd.sql` 신규 — 3단계 무중단 마이그레이션 (위 §DB 설계 변경 본문 그대로)

### Phase 7: 빌드·테스트 검증

`./gradlew.bat clean build` BUILD SUCCESSFUL + 모든 테스트 PASS

## 성공 기준 (검증 가능 형태)

| 기준 | 검증 명령 / 테스트 / 조회 |
|------|----------------------|
| `PumpDriveType` enum 2 값 정의 + `name()` 직렬화 | `./gradlew.bat :common:test --tests "PumpDriveTypeTest"` PASS |
| `Pump.create()` `driveType=null` 전달 시 NPE 발생 | `./gradlew.bat :common:test --tests "PumpSelfColumnsTest.create_driveType_null_npe"` PASS |
| `Pump.create()` 조합 차단 — `(RATED_DRIVE, AUTO_CAPABLE)` 입력 시 `RestApiException(INVALID_PUMP_DRIVE_OPRTNG_COMBINATION)` | `./gradlew.bat :common:test --tests "PumpDriveOprtngCombinationTest.create_rated_auto_blocked"` PASS |
| `Pump.create()` 정상 조합 3종 — `(INVERTER, AUTO)`·`(INVERTER, SEMI_AUTO)`·`(RATED, SEMI_AUTO)` 모두 성공 | `./gradlew.bat :common:test --tests "PumpDriveOprtngCombinationTest.create_valid_combinations"` PASS |
| `Pump.changePumpSelfColumns()` 갱신 후 무효 조합 결과값 시 차단 | `./gradlew.bat :common:test --tests "PumpDriveOprtngCombinationTest.changePumpSelfColumns_rated_auto_blocked"` PASS |
| 빌드 전체 통과 | `./gradlew.bat clean build` BUILD SUCCESSFUL 출력 확인 |
| V9_3 마이그레이션 적용 후 컬럼 정합 | `psql -c "\d+ pump_m"` 출력에 `drive_type_cd VARCHAR(20) NOT NULL` + COMMENT 노출 확인 |
| `RATED_DRIVE` 백필 row 수 확인 | `psql -c "SELECT COUNT(*) FROM pump_m WHERE drive_type_cd = 'RATED_DRIVE'"` 출력 = 마이그레이션 전 row 수 |
| InstrumentServiceTest 기존 테스트 회귀 통과 | `./gradlew.bat :api:test --tests "InstrumentServiceTest"` PASS |
| Swagger 스키마에 `driveType` 노출 — `PumpDto`·`PumpUpsertDto` | `./gradlew.bat :api:bootRun` 후 `/v3/api-docs` 응답에 `PumpDriveType` enum + `driveType` 필드 노출 확인 |

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| ANALYZE 미해결: 사이클 2 AI 재설계 시 `INVERTER_DRIVE` 펌프만 AI 자동 모드 진입 허용 여부 | 미해결 → 사이클 2 결정 | 본 사이클은 표출/저장 + 조합 차단까지만. AI 모드 진입 룰은 사이클 2 `/dev:analyze` 안건 |
| ANALYZE 미해결: 사이클 2 인터록 룰을 `drive_type_cd` 값으로 분기할지 | 미해결 → 사이클 2 결정 | 본 사이클 미접촉. `pump_interlock_p.facility_type_cd` 같은 컬럼 신설 가능성은 별도 사이클 |
| ANALYZE 미해결: 향후 3종 이상 구동 방식 추가 가능성 | 미해결 → 별도 ANALYZE | 현 시점 2종 충분 (`coding-discipline.md §2` 과잉 설계 경계). 3종 이상 요구 발생 시 별도 사이클 |
| ANALYZE 미해결: `PumpDto` 응답 DTO `driveType` 노출 여부 | **결정** | 노출. `oprtngType` 선례 동일 패턴 (`@Schema(implementation=)`). `/dev:spec` 단계 SPEC{N+1}.md 갱신 의무 |
| ANALYZE 미해결: `INVALID_PUMP_DRIVE_OPRTNG_COMBINATION` ErrorCode 위치 | **결정 (정정 — 2026-05-20 IMPL 단계)** | `PumpErrorCode` 신설 (`common/src/main/java/com/mo/swtp/instrument/exception/PumpErrorCode.java`). 사유: 기존 PLAN 결정(`InstrumentErrorCode` 흡수)은 common→api 모듈 역의존 위반 — common 의 Pump 엔티티가 api 의 ErrorCode 를 import 할 수 없음. 책임 분리 정합: `InstrumentErrorCode(api)`=API CRUD 진입점 에러 (FK·NM 중복·equip_type 불일치) / `PumpErrorCode(common)`=도메인 규칙 위반 (조합 유효성). 선례: `JwtErrorCode`(common) — 공통 인프라가 자체 ErrorCode 보유 가능 |
| 운영 데이터 존재 여부 | 가정 → 안전 처리 | 3단계 마이그레이션은 양 경우 모두 안전. 운영 데이터 존재 시 운영자 인버터 펌프 식별 후 수동 갱신 절차 필요 — 운영 안내 별도 문서화 검토 외 |
| 운영 데이터 대용량 환경 (수십만 행 이상) 도입 시 백필 UPDATE 배치 분할 필요성 | 권고 → 향후 환경 도입 시 적용 | 본 사이클은 신규 도입 환경(운영 데이터 0건) 전제로 단일 UPDATE 안전. 대용량 환경 도입 시 `query-tuning.md §1` 장시간 트랜잭션 회피 — `UPDATE ... WHERE ... AND id BETWEEN x AND y` 배치 분할 적용 (`indexing-and-migration.md §2` 선례 동일). 본 사이클 V9_3 본문은 단일 UPDATE 유지 (`wtp-dba-reviewer` PLAN 검토 권고, 2026-05-20) |
| `Pump.changePumpSelfColumns()` null 인자 시 기존값 유지 정책 — 갱신 후 조합 검증 적용 시점 | **결정** | 갱신 메서드 종료 직전에 결과값 `(this.driveType, this.oprtngType)` 으로 검증. **모든 인자 null 입력이어도 종료 직전 조합 검증 생략 금지** — 마이그레이션 백필 직후 무효 조합 DB 행 잠재 (백필 RATED_DRIVE + 기존 AUTO_CAPABLE) → 검증 우회 시 `ot-integration.md §5 ⚠️ 절대 금지` 직결 (`wtp-domain-expert` PLAN 검토 블로커 해소, 2026-05-20) |

## 제외 사항

- AI 운전 모드 진입 조건 룰 변경 — 사이클 2 (`pump+AI 백지화 사이클 2`) 범위
- `pump_interlock_p` 룰 테이블 `drive_type_cd` 기반 분기 — 사이클 2 범위
- `MAGNETIC_COUPLING_DRIVE`·`HYDRAULIC_DRIVE` 등 3종 이상 구동 방식 enum 확장 — 별도 ANALYZE
- 운영자 인버터 펌프 식별·수동 갱신 운영 매뉴얼 작성 — 별도 운영 문서화 작업
- frontend SPEC 갱신 (`/dev:spec`) — 본 사이클 COMMIT 후 별도 단계

## 예상 산출물

- [태스크](../../../tasks/20260520/pump_drive_type/TASK1.md) — `/dev:task pump_drive_type` 실행 시 작성

---

## 부록: 도메인/DB 검토 결과 (PLAN 검토 게이트)

### wtp-domain-expert (도메인 모델 검토)
- 블로커(높음) 1건 — `changePumpSelfColumns()` "null + null 통과" 표현 모호성. 마이그레이션 백필 직후 무효 조합 DB 행 존재 시 검증 우회 위험 (`ot-integration.md §5 ⚠️ 절대 금지` 직결). **PLAN 본문 수정으로 해소** — §도메인 모델 `Pump.changePumpSelfColumns()` 시그니처 변경 항목 마지막 행 + §가정 마지막 행에 "종료 직전 조합 검증은 입력 인자 조합과 무관하게 항상 실행" 명시
- 권고(중간) 1건 — ANALYZE 인터록 인용 근거 `§2·§5` 중 `§2` 백지화 상태. `§5` 단독 인용이 더 정확하나 templates.md 형식 위반은 아니므로 본 사이클 정정 없음 (다음 사이클 ANALYZE 작성 시 참고)
- 참고(낮음) 1건 — DDL 파일 경로 `migration/` vs `init/` 불일치. PLAN 의 `db/init/` 경로가 V8_5 선례 정합으로 정답 (dba 검토에서 추가 확인)

### wtp-dba-reviewer (DB 설계 변경 검토)
- 블로커(높음) 0건
- 권고(중간) 0건
- 참고(낮음) 1건 — 백필 UPDATE 배치 분할 — 현 사이클은 운영 데이터 0건 전제로 단일 UPDATE 안전. 대용량 환경 도입 시 배치 분할 필요. **PLAN 가정 섹션에 권고 사항 추가** (위 §가정 마지막 행)
- 종합 판정: V9_3 마이그레이션 구조·경로·라벨·정책 전항목 정합 (`V8_5` 선례 대비). PLAN 게이트 통과

### 종합
- 블로커 0건 (1건 발생 → PLAN 본문 수정으로 해소 완료)
- 권고 0건
- 참고 2건 (모두 PLAN 본문에 반영)
