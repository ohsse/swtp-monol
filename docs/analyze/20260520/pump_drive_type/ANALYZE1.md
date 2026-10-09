---
status: approved
created: 2026-05-20
updated: 2026-05-20
---
# 펌프 구동 방식 분류 — 도메인 분석

## 작업 배경

펌프 마스터(`pump_m` — `instrument_m` 의 JPA JOINED 자식, `@DiscriminatorValue("PUMP")`) 에 펌프의 **구동 방식** 을 분류하는 신규 필드 `drive_type_cd` 를 추가한다.

- **사용자 요청**: 펌프는 두 가지 타입으로 나뉜다 — 인버터 펌프(가변속, 주파수 제어) / 정격 펌프(고정속, on-off 제어)
- **외부 산출물**: 없음 (사용자 자연어 요청 + 사전 plan 자료 `~\.claude\plans\happy-twirling-lark.md` — 본 사이클의 사전 정리 메모)
- **기존 `oprtng_type_cd` 와의 관계**: 직교축. `oprtng_type_cd`(`AUTO_CAPABLE`/`SEMI_AUTO_CAPABLE` — 펌프조작유형 ANALYZE1 2026-05-12) 는 **제어 방식(조작 유형)** 분류, 본 신규 `drive_type_cd` 는 **구동 방식(설비 물리 사양)** 분류
- **도입 배경**: pump+AI 백지화 사이클 2 (`/dev:analyze` 별도 호출 예정) 의 AI 추론 입력·인터록 룰 분기·운전 모드 진입 조건에 활용될 가능성에 대비한 펌프 정적 사양 컬럼 확보. 본 사이클은 **표출/저장 전용**

## 회의록 (5인 회의 토픽 주도)

### 안건 1: 표준 단어 `drive` 신규 등록

- **호출 에이전트**: `wtp-glossary-manager`
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: 유사 단어 충돌 검토 — `drvn`(운전됨, 동사 수동형)·`oprtng`(운전중, 상태값)·`mod`(모드) 모두 의미 계층 분리 확인. `drive`(설비 물리 사양인 구동 방식) 와 어근·의미 모두 충돌 없음. 5자 풀네임 등록 적합 (`format` 6자·`branch` 6자·`quality` 7자 선례). **신규 등록 가능**
- **Round 2**: 불필요
- **결론**: `drive` 표준 단어 신규 등록 — 한글 논리명 "구동", 풀네임 "drive", 기본 데이터 도메인 "(조합)"

### 안건 2: 표준 용어 `drive_type_cd` 신규 등록 (`type` vs `se` 의미 경계 포함)

- **호출 에이전트**: `wtp-glossary-manager`, `wtp-domain-expert` (Round 1 통합 검토)
- **Round 1 답변 요약**:
  - **wtp-glossary-manager**: `oprtng_type_cd` 선례(같은 `pump_m` 마스터 내 설비 속성 분류 + `type` 채택, 2026-05-12) 와 동일 패턴 정합. 단 `type`(Discriminator 성격) vs `se`(같은 마스터 내 내부 분류) 룰 엄격 적용 시 해석 논란 가능성 — domain-expert 의 도메인 성격 확인 의뢰
  - **wtp-domain-expert**: `type` 채택 적합. 근거 — `INVERTER_DRIVE`/`RATED_DRIVE` 는 한 펌프가 동시에 양쪽일 수 없는 **상호 배타적(mutual exclusive) 물리 분류**. `se`(세부)는 `tag_se_cd` 선례처럼 동일 레코드가 FRI/PRI/LEI 등 측정 유형으로 세분화되는 맥락에 적합. `drive_type_cd` 는 분류 성격이 `oprtng_type_cd` 와 완전 동형
- **Round 2**: 불필요 (Round 1 에서 domain-expert 통합 답변으로 해소)
- **결론**: `drive_type_cd` 표준 용어 신규 등록 — 조합 `drive`(신규) + `type`(기등록 2026-05-12) + `cd`(기등록), `DOM_CODE_20` 재사용, 사용 테이블 `pump_m`

### 안건 3: `PumpDriveType` enum + `Pump` 엔티티 변경 정합성

- **호출 에이전트**: `wtp-backend-engineer`
- **Round 1 답변 요약**:
  - **wtp-backend-engineer**: 위치(`com.mo.swtp.instrument.domain`)·구조(단순 enum, 메서드·필드 없음)·매핑(`@Enumerated(EnumType.STRING)` + `DOM_CODE_20`) 모두 `PumpOprtngType` 선례 정합. 정량 기준 (메서드 50줄/추상화 3단/DTO 3단) 변동 없음. `_DRIVE` 접미사는 풀네임 어미 중복 — `INVERTER`/`RATED` 단독으로도 구분 명확하므로 접미사 필요성은 도메인 전문가 확인 권고. 확장 가능성(3종 이상) 평가 — 현 시점 2종 충분 (`coding-discipline.md §2` 과잉 설계 경계). **블로커 2건 식별**: (a) `RATED_DRIVE + AUTO_CAPABLE` 조합 유효성 미정 — 정격 펌프 가변속 불가, AI 자동 제어 가능성 도메인 검토 필요, (b) `Pump.create()` 호출처 7건 TASK 체크박스 전체 경로 명기 의무
- **Round 2**: 블로커 (a) 는 안건 5 의 domain-expert 통합 검토로 해소
- **결론**: `PumpDriveType` enum 신설 (`com.mo.swtp.instrument.domain`, 값 2종 `INVERTER_DRIVE`·`RATED_DRIVE`). `_DRIVE` 접미사 유지 (안건 5 결과로 도메인 의미 부여 — 구동 방식 명시). `Pump` 엔티티 변경 — `driveType` 필드 추가 + `create()`/`changePumpSelfColumns()` 시그니처 확장. TASK 단계에서 호출처 7건(`InstrumentServiceTest.java:143,161`·`PumpDtoSerializationTest.java:34,49,61`·`PumpSelfColumnsTest.java:28,37,46`·`InstrumentService.java:87`) 체크박스 백틱 전체 경로 명기 의무

### 안건 4: DDL 마이그레이션 V9_3 + NULL 정책 + 백필 DEFAULT

- **호출 에이전트**: `wtp-dba-reviewer`, `wtp-domain-expert` (백필 DEFAULT 결정)
- **Round 1 답변 요약**:
  - **wtp-dba-reviewer**: `DOM_CODE_20` 재사용 2차 승인 (NOT NULL 정책 기본 적합). 인덱스 단독 미적용 (카디널리티 2 — `oprtng_type_cd` 선례 동일). COMMENT 라벨 적합. **블로커 1건**: V9_3 단일 ALTER NOT NULL ADD COLUMN 위험 — 운영 데이터 존재 시 즉시 실패. `V8_5__pump_m_oprtng_type.sql` 선례 동일 3단계 무중단 마이그레이션 (ADD NULL → UPDATE 백필 → SET NOT NULL) 필수. 백필 DEFAULT 값 결정은 domain-expert 의뢰
  - **wtp-domain-expert**: 백필 DEFAULT 값 — **`RATED_DRIVE` 권고 (fail-safe 원칙)**. 근거: `INVERTER_DRIVE` 기본값 시 RATED 설비가 INVERTER 로 잘못 분류되어 사이클 2 AI 자동 제어 대상에 포함 → 가변속 명령이 정격 PLC 에 송신 → CircuitBreaker 연속 실패·OT 안전 위협 (`ot-integration.md §5 ⚠️ 절대 금지` 직결). `RATED_DRIVE` 기본값 시 INVERTER 설비가 RATED 로 잘못 분류되어도 기능 비활성화일 뿐 OT 안전 영향 없음. 비대칭 위험 — 안전 쪽으로 실패 원칙
- **Round 2**: 불필요
- **결론**: V9_3 3단계 무중단 마이그레이션, 백필 DEFAULT `'RATED_DRIVE'`. COMMENT 라벨 `'구동 방식 코드 (DOM_CODE_20, PumpDriveType enum 매핑 — INVERTER_DRIVE/RATED_DRIVE)'`. 인덱스 단독 미적용. DDL `DEFAULT` 미설정 (Java 정적 팩토리 명시 할당이 SSOT — `use_yn_consistency` 선례) — 단, 마이그레이션 백필 단계의 `UPDATE` 문에 임시 `'RATED_DRIVE'` 명시. 마이그레이션 완료 후 운영자가 인버터 설비 식별 수동 갱신

### 안건 5: 도메인 룰 4영역 점검 + RATED+AUTO 조합 유효성

- **호출 에이전트**: `wtp-domain-expert`
- **Round 1 답변 요약**:
  - **wtp-domain-expert**: 4영역 점검 결과 (상세 표는 아래 §도메인 룰 4영역 점검 참조). **블로커 1건**: `RATED_DRIVE + AUTO_CAPABLE` 조합은 물리 제약상 성립 불가 (정격 펌프 = 고정속 ON/OFF 만, AI 자동 = 가변 주파수 제어 필수). 4 가능 조합 중 1건 무효 — 애플리케이션 레벨 차단 의무. `Pump.create()`/`changePumpSelfColumns()` 내부 또는 별도 `PumpValidator` 에서 입력 시 `RestApiException(INVALID_PUMP_DRIVE_OPRTNG_COMBINATION)` 발생 의무. 미차단 시 사이클 2 AI 제어 단계에서 OT 안전 위협 (`ot-integration.md §5 ⚠️ 절대 금지`)
- **Round 2**: 불필요
- **결론**: 본 사이클은 표출/저장 전용이나 **조합 유효성 검증 로직은 본 사이클에 포함** (도메인 안전 직결). 4 조합 유효성:
  - INVERTER_DRIVE + AUTO_CAPABLE — 유효
  - INVERTER_DRIVE + SEMI_AUTO_CAPABLE — 유효
  - **RATED_DRIVE + AUTO_CAPABLE — 무효 (차단 의무)**
  - RATED_DRIVE + SEMI_AUTO_CAPABLE — 유효

## 표준 사전 카탈로그

### 신규 표준 단어

| 영문 약어 | 한글 논리명 | 풀네임 | 기본 데이터 도메인 | 분류 | 결정 근거 |
|----------|-----------|--------|-----------------|------|----------|
| `drive` | 구동 | drive | (조합) | **신규** | `swtp/.claude/rules/dict/standard-words.md` 미등록. `drvn`(운전됨, 수동형)·`oprtng`(운전중, 상태값)·`mod`(모드) 의미 계층 분리 확인 — 어근·의미 충돌 없음. 5자 풀네임 swtp 4-5자 약어 컨벤션 정합 (`format`·`branch`·`quality` 선례) |

### 신규 표준 데이터 도메인

없음 (`DOM_CODE_20` 재사용 — DBA 2차 승인 완료)

### 신규 표준 용어

| 물리명 | 조합 | 데이터 도메인 | 분류 | 결정 근거 |
|--------|------|-------------|------|----------|
| `drive_type_cd` | `drive`(신규) + `type`(기등록 2026-05-12) + `cd`(기등록) | `DOM_CODE_20` (재사용) | **신규** | `swtp/backend/.claude/rules/dict/standard-terms.md` 미등록. `oprtng_type_cd` 선례 (같은 `pump_m` 마스터 내 설비 속성 분류 + `type` 채택, 2026-05-12) 와 동일 패턴 정합. domain-expert 확인: `INVERTER_DRIVE`/`RATED_DRIVE` 상호 배타적 물리 분류 → `type` 채택 적합 (`se` 는 동일 레코드 세분화 — `tag_se_cd` 맥락) |

## 신규 엔티티/DB 컬럼

### 엔티티 변경

- **위치**: `swtp/backend/common/src/main/java/com/mo/swtp/instrument/domain/Pump.java`
- **신규 필드**: `private PumpDriveType driveType` + `@Enumerated(EnumType.STRING)` + `@Column(name="drive_type_cd", length=20, nullable=false)`
- **정적 팩토리**: `Pump.create(...)` 인자 4번째에 `PumpDriveType driveType` 추가 + `Objects.requireNonNull` 검증. **조합 유효성 검증 추가** — `RATED_DRIVE + AUTO_CAPABLE` 입력 시 `RestApiException(INVALID_PUMP_DRIVE_OPRTNG_COMBINATION)` (안건 5 결정)
- **변경 메서드**: `changePumpSelfColumns()` 시그니처에 `PumpDriveType driveType` 추가 — null 인자는 기존값 유지. 변경 후 결과값으로 조합 유효성 재검증 의무

### 신규 enum

- **위치**: `swtp/backend/common/src/main/java/com/mo/swtp/instrument/domain/PumpDriveType.java`
- **값**:
  - `INVERTER_DRIVE` — 인버터 펌프 (가변속, VFD 주파수 제어, AI 자동 제어 가능)
  - `RATED_DRIVE` — 정격 펌프 (고정속, ON/OFF 만 가능, AI 자동 제어 불가)
- **패턴**: `PumpOprtngType` 선례 동일 — 단순 public enum, javadoc 만, 메서드 없음

### 신규 DB 컬럼

- **테이블**: `pump_m`
- **컬럼**: `drive_type_cd VARCHAR(20) NOT NULL`
- **데이터 도메인**: `DOM_CODE_20` 재사용
- **인덱스**: 단독 미적용 (카디널리티 2 — `oprtng_type_cd` 선례 동일)
- **DDL DEFAULT**: 미설정 (Java 정적 팩토리 명시 할당이 SSOT — `use_yn_consistency` 선례)
- **CHECK 제약**: 미적용 (Java `@Enumerated(EnumType.STRING)` + enum 1차 차단 — `oprtng_type_cd` 선례)
- **COMMENT 라벨**: `'구동 방식 코드 (DOM_CODE_20, PumpDriveType enum 매핑 — INVERTER_DRIVE/RATED_DRIVE)'`

### 신규 마이그레이션

- **파일**: `swtp/backend/common/src/main/resources/db/migration/V9_3__pump_m_drive_type_cd.sql`
- **3단계 무중단 마이그레이션** (`indexing-and-migration.md §2`, `V8_5` 선례):
  1. `ALTER TABLE pump_m ADD COLUMN drive_type_cd VARCHAR(20);` — NULL 허용으로 즉시 추가
  2. `UPDATE pump_m SET drive_type_cd = 'RATED_DRIVE' WHERE drive_type_cd IS NULL;` — fail-safe 백필 (domain-expert 결정)
  3. `ALTER TABLE pump_m ALTER COLUMN drive_type_cd SET NOT NULL;` — NOT NULL 전환
  4. `COMMENT ON COLUMN pump_m.drive_type_cd IS '...';` — COMMENT 의무화 정책

### 신규 ErrorCode

- **위치**: 기존 `com.mo.swtp.instrument.exception.InstrumentErrorCode` 에 추가 (또는 PumpErrorCode 가 있다면 그곳)
- **상수**: `INVALID_PUMP_DRIVE_OPRTNG_COMBINATION(400)` — `RATED_DRIVE + AUTO_CAPABLE` 조합 차단 시 발생
- **PLAN 단계 확정**: 정확한 enum 위치는 PLAN 에서 결정

## 기존 사전·패턴과의 충돌

| 충돌 항목 | 권장 해소책 (회의 결론 일치) |
|---------|-------------------------|
| `drive` vs `drvn`/`oprtng`/`mod` 의미 중복 의심 | 의미 계층 분리 — `drive`(설비 정적 사양)·`drvn`(동사 수동형)·`oprtng`(실시간 상태값)·`mod`(시스템 상태 코드). 동일 어근·동의어 없음 (Round 1 wtp-glossary-manager 확인) |
| `drive_type_cd` 의 `type` vs `se` 의미 경계 | `type` 채택 — 상호 배타적 분류 (`oprtng_type_cd` 선례 동일, `tag_se_cd` 맥락 다름). Round 1 wtp-domain-expert 통합 검토 결론 |
| `pump_` prefix 사용 불가 (비즈니스 도메인 약어 폐기 2026-05-12) | `drive_type_cd` 단독 — `pump_m` 테이블 컨텍스트로 의미 자명 (`rated_head`·`oprtng_type_cd` 선례 동일) |
| 단일 ALTER NOT NULL ADD COLUMN — 운영 데이터 존재 시 실패 위험 | 3단계 무중단 마이그레이션 (`V8_5` 선례) — Round 1 wtp-dba-reviewer 결론 |
| `RATED_DRIVE + AUTO_CAPABLE` 조합 도메인 무효 (물리 제약) | 애플리케이션 레벨 차단 — `Pump.create()`/`changePumpSelfColumns()` 검증 의무. `RestApiException` 발생 (`ot-integration.md §5 ⚠️ 절대 금지` 직결) — Round 1 wtp-domain-expert 결론 |

## PLAN 으로 전달할 결정 사항

### 도메인 모델 초안

- `PumpDriveType` enum (`com.mo.swtp.instrument.domain`) — 단순 enum 2 값
- `Pump` 엔티티 (`com.mo.swtp.instrument.domain.Pump`) — `driveType` 필드 추가 + 조합 유효성 검증 로직
- 신규 `InvalidPumpDriveOprtngCombinationException` 또는 `InstrumentErrorCode.INVALID_PUMP_DRIVE_OPRTNG_COMBINATION` ErrorCode (PLAN 에서 위치 결정)

### DB 설계 변경 초안

- 신규 컬럼 `pump_m.drive_type_cd VARCHAR(20) NOT NULL`
- V9_3 3단계 무중단 마이그레이션 (fail-safe 백필 `'RATED_DRIVE'`)
- 인덱스 미적용

### 적용할 패턴

- JPA JOINED + DiscriminatorColumn 자식 엔티티 신규 필드 추가 (`Pump` 자식 전용 컬럼)
- `@Enumerated(EnumType.STRING)` enum 매핑 (`PumpOprtngType` 선례 동일)
- 외부 할당 PK 아님 (UUID 자동 생성 부모 `instrument_m`)
- 조합 유효성 검증 — Service 가 아닌 도메인(엔티티 정적 팩토리) 책임 (`entity-patterns.md` 의도 일치)
- DDL COMMENT 의무화 (`indexing-and-migration.md §4`)
- 3단계 무중단 마이그레이션 (`indexing-and-migration.md §2`)

## 가정 및 미해결 질문

| 가정 / 질문 | 분류 | 비고 |
|-----------|------|------|
| 운영 데이터 존재 여부 불확정 — `V8_5` 선례 동일 3단계 무중단 마이그레이션으로 양 경우 모두 안전 처리 | 결정 | PLAN 에서 마이그레이션 SQL 확정 |
| 백필 기본값 `RATED_DRIVE` fail-safe 채택 — 운영자가 인버터 설비 수동 갱신 절차 필요 | 결정 | PLAN 에서 운영 안내 문서화 검토 |
| 사이클 2 AI 재설계 시 `INVERTER_DRIVE` 펌프만 AI 자동 모드 진입 허용 여부 — 본 사이클 결정 외 | 미해결 | 사이클 2 `/dev:analyze` 안건. `oprtng_type_cd`(AUTO_CAPABLE)+`drive_type_cd`(INVERTER_DRIVE) 교차 조건이 AI 진입 룰에 미치는 영향 |
| 사이클 2 인터록 룰을 `drive_type_cd` 값으로 분기할지 (예: RATED_DRIVE 가변속 명령 차단 인터록) | 미해결 | 사이클 2 안건. `pump_interlock_p.facility_type_cd` 같은 컬럼 신설 가능성 |
| 향후 3종 이상 구동 방식 (`MAGNETIC_COUPLING_DRIVE`·`HYDRAULIC_DRIVE` 등) 추가 가능성 | 미해결 | 현 시점 2종 충분 (`coding-discipline.md §2` 과잉 설계 경계). 3종 이상 요구 발생 시 별도 ANALYZE |
| `PumpDto` 응답 DTO 가 `driveType` 노출 여부 (시설물응답DTO명세 ANALYZE1 3단 상속 패턴 정합) | 미해결 | PLAN 에서 결정 — 노출 시 `/dev:spec` SPEC{N+1}.md 갱신 의무 |
| `INVALID_PUMP_DRIVE_OPRTNG_COMBINATION` ErrorCode 위치 — `InstrumentErrorCode` 흡수 vs 신규 `PumpErrorCode` enum | 미해결 | PLAN 에서 결정 (현 코드 베이스 ErrorCode 디렉토리 구조 확인 필요) |

## 성공 기준 후보 (PLAN 변환 대상)

| 후보 기준 | 검증 명령 초안 (PLAN 에서 확정) |
|---------|--------------------------|
| `PumpDriveType` enum 단위 테스트 — 2 값 정의·`name()` 직렬화 확인 | `./gradlew.bat :common:test --tests "PumpDriveTypeTest"` PASS |
| `Pump.create()` 정적 팩토리 — `driveType=null` 전달 시 `NullPointerException` 확인 | `./gradlew.bat :common:test --tests "PumpTest.create_driveType_null_npe"` PASS |
| `Pump.create()` 조합 유효성 — `(RATED_DRIVE, AUTO_CAPABLE)` 전달 시 `RestApiException(INVALID_PUMP_DRIVE_OPRTNG_COMBINATION)` 확인 | `./gradlew.bat :common:test --tests "PumpTest.create_rated_auto_blocked"` PASS |
| `Pump.create()` 정상 조합 3종 — `(INVERTER, AUTO)`·`(INVERTER, SEMI_AUTO)`·`(RATED, SEMI_AUTO)` 모두 성공 | `./gradlew.bat :common:test --tests "PumpTest.create_valid_combinations"` PASS |
| V9_3 마이그레이션 적용 후 `pump_m.drive_type_cd` NOT NULL VARCHAR(20) + COMMENT 정합 | `psql -c "\d+ pump_m"` 출력에 `drive_type_cd` 컬럼 NOT NULL + COMMENT 노출 확인 |
| 기존 호출처 7건 (`Pump.create(`) + 1건 (`changePumpSelfColumns(`) 컴파일 통과 | `./gradlew.bat :api:compileTestJava :common:compileTestJava` BUILD SUCCESSFUL |
| `RATED_DRIVE` 백필 후 row 수 확인 | `psql -c "SELECT COUNT(*) FROM pump_m WHERE drive_type_cd = 'RATED_DRIVE'"` = 마이그레이션 전 row 수 |

## 도메인 룰 4영역 점검

본 ANALYZE 의 도메인 4영역 (알람 4단계 / 인터록 / AI 운전 모드 / 이력 기록) 해당 여부.

| 영역 | 해당/비해당 | 근거 또는 영향 |
|------|----------|------------|
| 알람 4단계 (`ot-integration.md §5`) | 비해당 | `drive_type_cd` 는 펌프의 물리적 설비 속성(인버터/정격) 분류 컬럼. 알람 임계값·전이 조건·복귀 조건 어디에도 구동 방식 분류가 직접 개입하지 않음. 알람 룰은 SCADA 측정값 기반이며 설비 속성 코드 참조 없음. 본 사이클은 표출/저장 전용이므로 알람 평가 로직 미접촉 |
| 인터록 선행조건 (`ot-integration.md §2·§5`) | 해당 (간접 — 가정 섹션 기재) | 본 사이클에서 `pump_interlock_p` 룰 테이블에 직접 편입 없음. 그러나 사이클 2 재설계 시 `drive_type_cd` 값으로 인터록 룰 분기(예: RATED_DRIVE 가변속 명령 차단 인터록)할 수 있어 "완전 비해당" 위험. `§5 ⚠️ 절대 금지` — 인터록 검사 건너뛴 채 재시도 금지 — 직결 맥락. 현 사이클은 조합 유효성 검증 로직(RATED+AUTO 차단)만 도입, 인터록 룰 자체는 사이클 2 미해결 |
| AI 운전 모드 (`ot-integration.md §5`) | 비해당 (현 사이클 한정) | `ai_drvn_mod_p`·`ai_drvn_mod_h` 백지화 완료 상태이며 본 사이클은 표출/저장 전용. 사용자 의도(`ai_drvn_mod`)·시스템 상태(`ai_mode_cd`) 컬럼 변경 없음. 단 가정 섹션에 "사이클 2 AI 재설계 시 `oprtng_type_cd`(AUTO_CAPABLE)+`drive_type_cd`(INVERTER_DRIVE) 교차 조건이 AI 진입 룰 영향" 미해결 기재 |
| 이력 기록 의무 (`ot-integration.md §5`) | 비해당 | `drive_type_cd` 는 설비 마스터 속성 컬럼(정적 분류값) — 운영 중 변경 빈도 매우 낮음. `ai_drvn_mod_h.transition_reason` 5종·`pump_ctrl_h` 제어 로그는 운전 모드 변경 시점 이벤트 기록이라 무관. 본 사이클에서 `_h` 이력 테이블 접촉 없음. 마스터의 갱신 추적은 `BaseEntity.updt_dtm`·`updt_id` 자동 주입으로 충분 |

## 룰 갱신 지시서 (PLAN approved 의 전제조건)

- [x] `swtp/.claude/rules/dict/standard-words.md` — `drive` 표준 단어 신규 등록 (한글 논리명: 구동, 풀네임: drive, 기본 데이터 도메인: (조합), 등록일: 2026-05-20, 비고: `drvn`/`oprtng`/`mod` 의미 분리 확인)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `drive_type_cd` 표준 용어 신규 등록 (조합 `drive`(신규) + `type` + `cd`, `DOM_CODE_20`, 사용 테이블 `pump_m`, 비고: `oprtng_type_cd` 선례 동일 패턴 — `type` 채택 근거)
- [x] `swtp/backend/.claude/rules/dict/standard-terms.md` — `oprtng_type_cd` 행의 비고에 `drive_type_cd` 와 직교축 관계 명시 추가 (선택 — 두 컬럼 의미 분리 가이드)

## 산출물

- [계획안](../../../plan/20260520/pump_drive_type/PLAN1.md) (다음 단계 `/dev:plan pump_drive_type` 실행 시 작성)
