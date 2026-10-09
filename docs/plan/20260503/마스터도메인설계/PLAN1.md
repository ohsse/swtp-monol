---
status: approved
created: 2026-05-03
updated: 2026-05-03
---
# 마스터도메인설계 — 엔티티·DDL 구현 계획

## 목적

[ANALYZE1.md](../../../analyze/20260502/마스터도메인설계/ANALYZE1.md) (status: approved) 의 5인 회의 Round 1·2·3 결정 사항을 코드·DDL 로 구현한다. 시설(Facility)·계측기(Instrument)·태그(Tag)·로우데이터(RawData) 4개 마스터 도메인 + 13개 신규/재구성 테이블 + 4개 신규 패키지를 작성하고, 기존 `com.mo.swtp.pump` 패키지의 마스터 엔티티 (Pump·PWTF·DWT) 를 신규 패키지로 분리 이관한다.

## 배경

ANALYZE1 의 결정 흐름 (2026-05-02·03):
- **Round 2** (2026-05-02): 시설·계측기 단일 마스터 + JPA `@Inheritance(JOINED)` + `@DiscriminatorColumn` 패턴 채택. `pwtf_m`·`dwt_m`·`pump_m` 별도 마스터 폐기, `facility_m`·`instrument_m` 단일 마스터 + 자식 9종 (PWTF·DWT·RSV / PUMP·VALVE·FLWMTR·PRSMTR·LVMTR·ELCMTR) 으로 통합.
- **Round 3** (2026-05-02·03 — 사용자 추가 요구사항): 외부 할당 PK + Persistable 패턴 폐기 → UUID 자동 생성 PK + 이름 UNIQUE 비즈니스 식별자. `tag_m` PK = `tag_srl_no` 자연키 (외부 할당, `Persistable<String>`). `rawdata_1m_h` = `raw_val` + `corr_val` + `quality_cd` + BaseEntity 4 (immutable 이력 패턴 폐기).

[ANALYZE1.md](../../../analyze/20260502/마스터도메인설계/ANALYZE1.md) 의 룰 갱신 지시서 9건은 이미 [x] 완료 + 직전 커밋 `8ff1213` 으로 반영됨 — `entity-patterns.md` §JPA JOINED + DiscriminatorColumn 다형성 패턴 신규 절, ROOT 어휘 사전 (단어 11건 + DOM_TAG_SRL_NO_50 + 비즈니스 도메인 약어 4건), backend 표준 용어 16건 등록. 본 PLAN 은 그 룰 갱신 결과를 코드·DDL 로 구현한다.

PLAN 단계 4건 미해결 질문은 사용자 응답 (2026-05-03) 으로 모두 결정 처리:
1. `instrument_nm` UNIQUE 범위 = `(facility_id, instrument_nm)` 복합 (멀티테넌트 운영 정합)
2. `tag_m` 입출력 컬럼 = `io_cd` (DOM_CODE_20) + `IoCode` enum (INPUT/OUTPUT/BIDIR)
3. 자식 9종 자식 전용 컬럼 = 본 PLAN skeleton 만 (0건). 추후 요구사항명세서 기반 차기 PLAN 결정.
4. V1 처리 = V1 폐기 + V6 신규 (ANALYZE 결정). V2~V5 는 자체 수정 (운영 데이터 0건)

## 범위

- 신규 enum 5건 (`FacilityType`·`EquipType`·`TagMeasurementType`·`IoCode`·`QualityCode`)
- 신규 엔티티 13건 (`Facility` 부모 + 자식 3 + `Instrument` 부모 + 자식 6 + `Tag` + `RawData`)
- 신규 Repository 4건 (도메인별 — 자식별 분리 vs 통합은 TASK 단계 결정)
- DDL: V1 폐기 + V6 신규 5건 + V2~V5 컬럼명 변경
- 기존 `com.mo.swtp.pump` 패키지 분리 이관 (Pump·PurifiedWaterTank·DistributionWaterTank 마스터 엔티티만 신규 패키지로 이전, `PumpInterlock`·`PumpCmbn`·`PumpControlHistory`·`PumpPredictionResult` 는 잔존 + FK 컬럼명 변경)
- 기존 Service/Controller (있다면) 의 `pump_id`·`pwtf_id` 참조 코드 변경

**범위 외**:
- 신규 도메인 Service/Controller (요구사항명세서 나오면 차기 작업)
- 자식 9종 자식 전용 컬럼 (사용자 명시 결정)
- 데이터 마이그레이션 SQL (운영 데이터 0건)
- frontend SPEC 전파 (Controller·DTO·ErrorCode 변경 0건)

## 도메인 모델

| 엔티티 | 테이블 | 부모/자식 | PK | UNIQUE | 주요 필드 |
|-------|------|----------|-----|--------|---------|
| `Facility` (abstract) | `facility_m` | 부모 (JOINED) | `facility_id` (UUID 자동, DOM_ID_36) | `facility_nm` 시스템 전체 | `facility_type_cd`(Discriminator), `parent_facility_id`(self-FK NULL), `disp_ord`, `main_yn`, `use_yn` + BaseEntity 4 |
| `PurifiedWaterTank` | `pwtf_m` | 자식 (PWTF) | 부모 PK 동일 (자동 상속) | — | (자식 전용 컬럼 0건 — skeleton) |
| `DistributionWaterTank` | `dwt_m` | 자식 (DWT) | 부모 PK 동일 | — | (자식 전용 컬럼 0건) |
| `Reservoir` | `rsv_m` | 자식 (RSV) | 부모 PK 동일 | — | (자식 전용 컬럼 0건) |
| `Instrument` (abstract) | `instrument_m` | 부모 (JOINED) | `instrument_id` (UUID 자동, DOM_ID_36) | `(facility_id, instrument_nm)` 복합 | `equip_type_cd`(Discriminator), `facility_id`(FK NOT NULL), `disp_ord`, `use_yn` + BaseEntity 4 |
| `Pump` | `pump_m` | 자식 (PUMP) | 부모 PK 동일 | — | (자식 전용 컬럼 0건) |
| `Valve` | `valve_m` | 자식 (VALVE) | 부모 PK 동일 | — | (자식 전용 컬럼 0건) |
| `FlowMeter` | `flwmtr_m` | 자식 (FLWMTR) | 부모 PK 동일 | — | (자식 전용 컬럼 0건) |
| `PressureMeter` | `prsmtr_m` | 자식 (PRSMTR) | 부모 PK 동일 | — | (자식 전용 컬럼 0건) |
| `LevelMeter` | `lvmtr_m` | 자식 (LVMTR) | 부모 PK 동일 | — | (자식 전용 컬럼 0건) |
| `PowerMeter` | `elcmtr_m` | 자식 (ELCMTR) | 부모 PK 동일 | — | (자식 전용 컬럼 0건) |
| `Tag` | `tag_m` | 마스터 (`Persistable<String>`) | `tag_srl_no` 자연키 (DOM_TAG_SRL_NO_50) | (`tag_srl_no` 자체 — UNIQUE 별도 불요) | `instrument_id`(FK NOT NULL), `tag_se_cd`(`TagMeasurementType`), `tag_desc`, `unit_cd`, `io_cd`(`IoCode`) + BaseEntity 4 |
| `RawData` | `rawdata_1m_h` | 시계열 (BaseEntity) | `(rawdata_id, acq_dtm)` 복합 (BIGINT SEQUENCE allocationSize=100) | — | `tag_srl_no`(논리 참조 NOT NULL), `raw_val`(NULL 허용), `corr_val`(NULL 허용), `quality_cd`(`QualityCode`) + BaseEntity 4 |

**패키지 위치 (모두 `common/src/main/java/`)**:
- `com.mo.swtp.facility.domain.{Facility, PurifiedWaterTank, DistributionWaterTank, Reservoir}`
- `com.mo.swtp.facility.domain.enumtype.FacilityType`
- `com.mo.swtp.instrument.domain.{Instrument, Pump, Valve, FlowMeter, PressureMeter, LevelMeter, PowerMeter}`
- `com.mo.swtp.instrument.domain.enumtype.EquipType`
- `com.mo.swtp.tag.domain.Tag`
- `com.mo.swtp.tag.domain.enumtype.{TagMeasurementType, IoCode}`
- `com.mo.swtp.raw.domain.RawData`
- `com.mo.swtp.raw.domain.enumtype.QualityCode`

## DB 설계 변경

### V1 폐기 + V6 신규 5건

V1 폐기: `common/src/main/resources/db/init/V1__pumpcontrol_master_tables.sql` 본문 SQL 폐기 + redirect 주석:

```sql
-- V1 (pumpcontrol ANALYZE1, 2026-04-25) 의 마스터 7건 (pwtf_m·dwt_m·pump_m·pump_cmbn_m·pump_cmbn_d·pump_interlock_p·pump_ctrl_rslt_m) 은
-- 마스터도메인설계 ANALYZE1 (2026-05-02·03 Round 1·2·3) 결정으로 폐기됨.
-- 신규 위치:
--   V6_1__facility_master_tables.sql (facility_m + 자식 3: pwtf_m·dwt_m·rsv_m)
--   V6_2__instrument_master_tables.sql (instrument_m + 자식 6: pump_m·valve_m·flwmtr_m·prsmtr_m·lvmtr_m·elcmtr_m)
--   V6_3__pump_secondary_table_realign.sql (pump_cmbn_*·pump_interlock_p·ai_drvn_mod_p 의 pump_id→instrument_id, pwtf_id→facility_id)
```

V6 신규 5건:

| 파일 | 내용 |
|------|------|
| `common/src/main/resources/db/init/V6_1__facility_master_tables.sql` | `facility_m` + 자식 3 (`pwtf_m`·`dwt_m`·`rsv_m`) + `facility_nm` UNIQUE 인덱스 + `parent_facility_id` self-FK |
| `common/src/main/resources/db/init/V6_2__instrument_master_tables.sql` | `instrument_m` + 자식 6 + `(facility_id, instrument_nm)` UNIQUE 복합 + `facility_id` FK to `facility_m` |
| `common/src/main/resources/db/init/V6_3__pump_secondary_table_realign.sql` | `pump_cmbn_m`·`pump_cmbn_d`·`pump_interlock_p`·`ai_drvn_mod_p` 재구성: `pump_id` → `instrument_id` (FK to `instrument_m`), `pwtf_id` → `facility_id` (FK to `facility_m`) |
| `common/src/main/resources/db/init/V6_4__tag_master_table.sql` | `tag_m` (`tag_srl_no` 자연키 PK + `instrument_id` FK to `instrument_m`) |
| `common/src/main/resources/db/init/V6_5__rawdata_1m_h.sql` | `rawdata_1m_h` 월 RANGE 파티션 + 6개월 선행 + BRIN/복합 인덱스. 시계열 → 마스터 FK 금지 (`tag_srl_no` 논리 참조) |

### V2~V5 컬럼명 변경 (자체 수정)

운영 데이터 0건 + 첫 init + `ddl-auto: none` → V2~V5 자체 수정 (V6_6 마이그레이션 SQL 별도 작성 안 함):
- `V2__pumpcontrol_history_partitions.sql` — `pump_ctrl_h.pump_id` → `instrument_id`, `pump_predc_h.{pump_id, pwtf_id}` → `{instrument_id, facility_id}`, `ai_drvn_mod_h.pwtf_id` → `facility_id`
- `V3~V5__*.sql` (인덱스·시퀀스·NULL 정책) — 위 컬럼명에 의존하는 부분 자동 동기화

### 인덱스 (DDL 통합)

| 인덱스 | 테이블·컬럼 | 종류 | 근거 |
|--------|----------|------|------|
| `idx_facility_m_facility_nm` | `facility_m(facility_nm)` | UNIQUE B-Tree | 시스템 전체 이름 UNIQUE |
| `idx_instrument_m_facility_nm` | `instrument_m(facility_id, instrument_nm)` | UNIQUE 복합 | 멀티테넌트 운영 정합 (사용자 결정). 선두 컬럼 `facility_id` 가 FK 조회도 커버하므로 `idx_instrument_m_facility_id` 단독 인덱스 별도 생성 불요 (wtp-dba-reviewer 권고 2026-05-03) |
| `idx_tag_m_instrument_id` | `tag_m(instrument_id)` | B-Tree | FK 조회 가속 |
| `idx_rawdata_1m_h_brin` | `rawdata_1m_h(acq_dtm)` | BRIN | 시계열 월 RANGE 정합 (`db/indexing-and-migration.md §1`) |
| `idx_rawdata_1m_h_tag_time` | `rawdata_1m_h(tag_srl_no, acq_dtm DESC)` | 복합 B-Tree | 등가(`tag_srl_no`) → 범위(`acq_dtm`) 순서 정합 |

### 무중단 마이그레이션 평가

운영 데이터 0건 + 개발 단계 → `db/indexing-and-migration.md §2` 무중단 마이그레이션 절차 (NOT NULL 컬럼 추가 3단계 등) 적용 외. 모든 DDL 은 단순 DROP/CREATE 또는 ALTER COLUMN.

### 컬럼 COMMENT 의무화

V6_1~V6_5 모든 컬럼에 `COMMENT ON COLUMN` 작성 — `db/indexing-and-migration.md §4` + `check-ddl-column-comment.sh` 훅 통과 의무. BaseEntity 4 컬럼 표준 라벨 + 도메인 컬럼 라벨 패턴 (`{한글 논리명} ({DOM_* 코드 / 부연})`) 적용.

## 적용할 패턴

- **JPA JOINED + DiscriminatorColumn 다형성** — [`entity-patterns.md` §JPA JOINED + DiscriminatorColumn 다형성 패턴](../../../../.claude/rules/entity-patterns.md) (직전 커밋 `8ff1213` 에 추가됨) 그대로 적용
- **UUID 자동 생성 PK** — `entity-patterns.md` §기본 엔티티 패턴 (`@GeneratedValue(GenerationType.UUID)`, Persistable 미구현)
- **외부 할당 PK + `Persistable<String>`** — `Tag` 만 적용 (자연키 PK = `tag_srl_no`, `entity-patterns.md` §외부 할당 PK 엔티티 패턴)
- **BaseEntity 상속** — 모든 엔티티 (`RawData` 포함). `corr_val` 갱신 시점은 `updt_dtm`·`updt_id` 자동 갱신 (`AuditingEntityListener`)
- **이름 UNIQUE 비즈니스 식별자** — `facility_nm` (시스템 전체) + `(facility_id, instrument_nm)` 복합. UUID PK = 시스템 내부 식별자, 사용자 식별은 이름값
- **시계열 → 마스터 FK 금지** — `rawdata_1m_h.tag_srl_no` 는 논리 참조 (FK 없음). 참조 무결성은 애플리케이션 레벨 + 마스터 캐시 검증 (`db/partitioning-and-retention.md §1` 정합)

## 도메인 룰 (PLAN 제약 — 필수 명시)

- **AI 운전 모드 쿼리는 `facility_type_cd='PWTF'` 필터 강제** (Service 계층) — [`ot-integration.md §5`](../../../../.claude/rules/ot-integration.md) 강제 모드 전환 정책 + `entity-patterns.md` §JPA JOINED 도메인 룰 §`facility_type_cd` 필터 강제 정합. `ai_drvn_mod_p` 에서 facility 다형성 전체 조회 시 PWTF 자식만 평가.
- **인터록 평가 (`InterlockValidator`) 는 `equip_type_cd='PUMP'` (또는 향후 `'VALVE'`) 액추에이터만 대상으로 필터** — `ot-integration.md` §Pump = Instrument 자식 정합성 정합
- **인터록 재검사 경로 보존 의무** — Phase 4 의 `pump_interlock_p.pump_id` → `instrument_id` FK 변경 후 `InterlockValidator.validateOrThrow` 가 `instrument_id` 를 정상 수신·재검사하는 경로가 끊기지 않아야 한다. `ot-integration.md §5 ⚠️ 절대 금지` (장애 복구 후에도 인터록 선행조건 재검사 강제) 직결. Phase 4 체크박스 검증 의무로 명시 (TASK 단계).
- **`RawData.corr_val` 갱신과 SCADA_TIMEOUT 판정 경계** — `corr_val` 갱신은 SCADA 품질 판정 (`ai_drvn_mod_p.last_rcv_dtm` 기반 강제 모드 전환) 에 영향을 주지 않는다. `last_rcv_dtm` 은 SCADA 인바운드 수신 시점 기록으로 `raw_val` (원본 SCADA 측정값) 의존이며, `corr_val` 은 운영자 사후 보정이므로 별개 계층. Service 구현 시 분리 보장 의무 — `corr_val` 갱신 경로에서 `ai_drvn_mod_p.last_rcv_dtm` 갱신·`ai_drvn_mod_h` 이력 행 추가 금지. `ot-integration.md §5` 강제 전환 정책 + `transition_reason` 5종 (USER_SELECT·SCADA_TIMEOUT·MANUAL_EXPIRE·OUTBOUND_FAIL·SYSTEM_INIT) 직결.
- **`parent_facility_id` self-FK 재귀 깊이 제한 미정 (무제한 허용)** — 인터록 평가 쿼리는 self-FK 미순회 (Service 계층 구현 제약). wtp-domain-expert Round 2 권고
- **`RawData` INSERT-only 컬럼 (`tag_srl_no`·`acq_dtm`·`raw_val`)** — 애플리케이션 레벨 immutable 검증 (`@PrePersist`·`@PreUpdate` 또는 변경 메서드 차단). `corr_val` 만 갱신 허용

## 구현 방향

### Phase 1: 신규 enum 5건

- `FacilityType` (PWTF·DWT·RSV) — `com.mo.swtp.facility.domain.enumtype`
- `EquipType` (PUMP·VALVE·FLWMTR·PRSMTR·LVMTR·ELCMTR) — `com.mo.swtp.instrument.domain.enumtype`
- `TagMeasurementType` (FRI·PRI·LEI·PWI·RMS) — `com.mo.swtp.tag.domain.enumtype` (`ot-integration.md §3` 매핑)
- `IoCode` (INPUT·OUTPUT·BIDIR) — `com.mo.swtp.tag.domain.enumtype` (사용자 결정 2026-05-03)
- `QualityCode` (GOOD·BAD·UNCERTAIN) — `com.mo.swtp.raw.domain.enumtype` (`ot-integration.md §3`)

### Phase 2: DDL — V1 폐기 + V6 신규 5건 + V2~V5 컬럼명 변경

- V1 redirect 주석 (위 §DB 설계 변경 참조)
- V6_1~V6_5 신규 SQL 작성 — 모든 컬럼 `COMMENT ON COLUMN` 의무, BaseEntity 4 컬럼 표준 라벨 적용
- **V6_5 BRIN/복합 인덱스 DDL 순서 의무** — wtp-dba-reviewer 권고 (2026-05-03) 반영. PostgreSQL 파티션 인덱스 자동 상속을 위해 다음 순서 준수:
  1. `CREATE TABLE rawdata_1m_h ... PARTITION BY RANGE (acq_dtm)` (부모 테이블 + 파티션 키)
  2. `CREATE INDEX idx_rawdata_1m_h_brin ... USING BRIN (acq_dtm)` (부모 인덱스 — 파티션 자식에 자동 상속)
  3. `CREATE INDEX idx_rawdata_1m_h_tag_time ... (tag_srl_no, acq_dtm DESC)` (부모 복합 인덱스)
  4. `CREATE TABLE rawdata_1m_h_YYYYMM PARTITION OF rawdata_1m_h FOR VALUES FROM ... TO ...` (자식 파티션 6개월치 — 부모 인덱스 자동 상속)
- V2 컬럼명 변경 + V3~V5 의존 컬럼 동기화
- **V2 컬럼명 변경 시 `COMMENT ON COLUMN` 갱신 의무** — wtp-dba-reviewer 권고 (2026-05-03) 반영. `pump_ctrl_h.pump_id`·`pump_predc_h.pump_id`·`pump_predc_h.pwtf_id`·`ai_drvn_mod_h.pwtf_id` 의 기존 COMMENT 삭제 후 `instrument_id`·`facility_id` 라벨로 재작성 (`db/indexing-and-migration.md §4` 의무)

### Phase 3: 신규 엔티티 + Repository 13건

- `Facility` 부모 (`abstract`, `@Inheritance(InheritanceType.JOINED)`, `@DiscriminatorColumn`, `@UniqueConstraint`) + 자식 3 (`@DiscriminatorValue("PWTF")` 등)
- `Instrument` 부모 + 자식 6
- `Tag` (`Persistable<String>` 구현, `getId()` 만 override)
- `RawData` (BaseEntity 상속, `@SequenceGenerator(allocationSize=100)`)
- Repository 4건 (도메인별, 자식별 분리는 TASK 단계 결정)
- **N+1 방지 — JPA JOINED 다형성 부모 목록 조회 메서드 의무** — wtp-dba-reviewer 권고 (2026-05-03) 반영. `FacilityRepository.findAll()`·`InstrumentRepository.findAll()` 등 부모 다형성 전체 조회 메서드는 `@BatchSize(size = N)` 또는 `@EntityGraph(attributePaths = {...})` 적용 (`db/query-tuning.md §2` N+1 방지 원칙). 도메인 시나리오상 다형성 전체 조회 빈도 = "드물거나 없음" (ANALYZE Round 2 가정) 이지만 안전을 위해 첫 작성부터 적용. TASK 체크박스에 명시.

### Phase 4: 기존 `com.mo.swtp.pump` 패키지 분리 이관 + FK 컬럼 변경

- `com.mo.swtp.pump.domain.Pump` → `com.mo.swtp.instrument.domain.Pump` (JPA JOINED 자식 재구성, Persistable 미구현)
- `com.mo.swtp.pump.domain.PurifiedWaterTank` → `com.mo.swtp.facility.domain.PurifiedWaterTank`
- `com.mo.swtp.pump.domain.DistributionWaterTank` → `com.mo.swtp.facility.domain.DistributionWaterTank`
- 잔존: `PumpInterlock`·`PumpCmbn`·`PumpCmbnDetail`·`PumpControlHistory`·`PumpPredictionResult` (com.mo.swtp.pump 잔존, FK 컬럼 변경만)
  - `pump_id` 필드 → `instrument_id`
  - `pwtf_id` 필드 → `facility_id`
- 기존 Service/Repository (있다면) 의 컬럼 참조 코드 변경

### Phase 5: 빌드 검증

- `./gradlew.bat clean build` BUILD SUCCESSFUL
- `./gradlew.bat :common:test` PASS
- `./gradlew.bat :api:test` PASS
- `./gradlew.bat :scheduler:test` PASS
- Querydsl Q클래스 재생성 확인 (`common/build/generated/sources/annotationProcessor/java/main`)

## 성공 기준 (검증 가능 형태)

> ROOT [`coding-discipline.md` §4.2](../../../../../.claude/rules/coding-discipline.md) 적용. 각 기준에 검증 명령·테스트·조회 명시.

- [ ] enum 5건 생성 → 검증: 각 .java 파일 존재 + grep ENUM 값 일치
- [ ] V6_1~V6_5 SQL 작성 → 검증: psql \d+ {table} 출력에 모든 컬럼 + COMMENT ON COLUMN 표시
- [ ] V2~V5 컬럼명 변경 → 검증: psql \d+ pump_ctrl_h 등에 instrument_id·facility_id 등장 (pump_id·pwtf_id 부재)
- [ ] V1 redirect 주석 → 검증: V1 SQL 파일 본문 SQL 0건 + redirect 주석 존재 (grep "DROP\|CREATE\|INSERT" V1__*.sql 매칭 0건)
- [ ] 신규 엔티티 13건 작성 → 검증: 각 .java 파일 존재 + JPA 어노테이션 (@Inheritance·@DiscriminatorColumn·@DiscriminatorValue) 일치
- [ ] Repository 4건 작성 → 검증: 각 인터페이스 존재
- [ ] 기존 pump 패키지 분리 이관 → 검증: instrument/facility 패키지에 마스터 엔티티 존재 + pump 잔존 엔티티의 FK 필드 (instrument_id·facility_id) 적용
- [ ] ./gradlew.bat clean build BUILD SUCCESSFUL 출력 확인
- [ ] ./gradlew.bat test 모든 모듈 PASS
- [ ] check-ddl-column-comment.sh 훅 미차단 → V6_1~V6_5 모든 컬럼 COMMENT ON COLUMN 존재
- [ ] check-errorcode-contract.sh 훅 미차단 (해당 없음 — 신규 ErrorCode 미추가)

## 가정 및 미해결 질문

> ROOT [`coding-discipline.md` §1](../../../../../.claude/rules/coding-discipline.md) 적용. ANALYZE 의 가정·미해결 질문을 PLAN 단계 결정으로 변환. 최소 1건 이상 기재 의무.

| 가정 / 질문 | 분류 | PLAN 단계 결정 |
|-----------|------|--------------|
| `instrument_m.instrument_nm` UNIQUE 범위 | **결정** | `(facility_id, instrument_nm)` 복합 — 멀티테넌트 운영 환경 (`multi-tenant.md`) 정합. 정수장별로 같은 이름 허용 (사용자 결정 2026-05-03) |
| `tag_m.io_*` 컬럼 의미 | **결정** | `io_cd` (DOM_CODE_20) + `IoCode` enum (INPUT·OUTPUT·BIDIR) — 양방향 태그 표현 가능, 향후 확장 여지 (사용자 결정 2026-05-03) |
| 자식 9종 자식 전용 컬럼 (`pump_m.rated_head` 등) | **결정** | 본 PLAN skeleton 만 (자식 전용 컬럼 0건). 추후 요구사항명세서 기반 차기 PLAN 결정 (사용자 결정 2026-05-03 + ANALYZE Round 3 결정) |
| V1 처리 전략 | **결정** | V1 폐기 + V6 신규 5건 (V1 파일에 redirect 주석). V2~V5 자체 수정 (운영 데이터 0건) (사용자 결정 2026-05-03) |
| `parent_facility_id` self-FK 재귀 깊이 제한 | 가정 | 무제한 허용. 인터록 평가 쿼리는 self-FK 미순회 (Service 계층 구현 제약) — wtp-domain-expert Round 2 권고 |
| `RawData` INSERT-only 컬럼 (`tag_srl_no`·`acq_dtm`·`raw_val`) immutable 검증 방식 | 가정 | 애플리케이션 레벨 — `@PrePersist`·`@PreUpdate` 또는 변경 메서드 차단. Phase 3 구현 시 결정 |
| Tag enum 위치 (`com.mo.swtp.tag.domain.enumtype` vs `com.mo.swtp.common.enumtype`) | 가정 | tag 도메인 전용 enum (`TagMeasurementType`·`IoCode`) 은 `com.mo.swtp.tag.domain.enumtype` (도메인 응집), 공용 (`YnType`) 은 common 위치 — `naming.md` §패키지 규칙 정합 |
| 기존 `com.mo.swtp.pump` 패키지 잔존 엔티티 (`PumpInterlock`·`PumpCmbn` 등) 의 패키지 이전 여부 | 가정 | 패키지 이전 안 함. 펌프 운영 데이터 (조합·인터록·제어이력·예측) 는 펌프 비즈니스 도메인 의미 유지. `instrument_id` FK 만 변경. 향후 `com.mo.swtp.pump` 비즈니스 도메인 약어 재정의 (펌프 운영 데이터) 검토는 차기 ANALYZE |
| 인터록 재검사 경로 보존 — `pump_interlock_p.pump_id` → `instrument_id` FK 변경 후 `InterlockValidator.validateOrThrow` 가 `instrument_id` 를 정상 수신·재검사하는 경로 보존 | 가정 | wtp-domain-expert 블로커 1 (2026-05-03) — `ot-integration.md §5 ⚠️ 절대 금지` 직결. Phase 4 체크박스 검증 의무 (TASK 단계 명시). 위 §도메인 룰 (PLAN 제약) 에 추가 |
| `RawData.corr_val` 갱신과 `ai_drvn_mod_p.last_rcv_dtm` 분리 | 가정 | wtp-domain-expert 블로커 2 (2026-05-03) — `corr_val` 갱신은 SCADA 인바운드 수신 경로와 무관 (운영자 사후 보정). Service 구현 시 `corr_val` 갱신 경로에서 `last_rcv_dtm`·`ai_drvn_mod_h` 행 추가 금지. `ot-integration.md §5` 강제 전환 정책 + `transition_reason` 5종 직결. 위 §도메인 룰 (PLAN 제약) 에 추가 |
| AI 추론 입력 경로 영향 — `pump_predc_h.pwtf_id` → `facility_id` 변경이 `PumpPredictionRequest` 의 시설 식별자 필드에 미치는 영향 | 가정 | wtp-domain-expert 권고 1 (2026-05-03) — `ot-integration.md §6.6` 데이터 I/O 책임 분담. Java 백엔드가 SELECT 시 `facility_id` 로 조회하여 HTTP body 매핑하므로 ai-server Pydantic 스키마 (`PumpPredictionRequest`) 변경 동반 필수 — 본 PLAN 범위 내 (Phase 4 잔존 엔티티 FK 변경 시 함께 진행) 또는 차기 작업 결정. monorepo 단일 PR 동기화 원칙 준수 |
| `ai_drvn_mod_h` immutable 이력 예외 적용 (`updt_*` 컬럼 미정의) | 가정 | wtp-domain-expert 참고 1 (2026-05-03) — `db/indexing-and-migration.md §4.3` 이력 immutable 테이블 예외. V2 컬럼 변경 시 `ai_drvn_mod_h` 의 `updt_*` 컬럼 부재 + `rgstr_dtm`·`rgstr_id` 만 보유 패턴 보존 확인 (Phase 2 체크박스 검증) |

## 제외 사항

- 신규 도메인의 Service/Controller (요구사항명세서 나오면 차기 작업)
- 자식 9종 자식 전용 컬럼 (`pump_m.rated_head`·`dwt_m.min_req_prsr`·`flwmtr_m.range_min` 등) — 사용자 명시 결정
- 데이터 마이그레이션 SQL — 운영 데이터 0건이라 불요
- frontend SPEC 전파 — 본 PLAN 은 마스터 도메인 설계, Controller·DTO·ErrorCode 변경 0건이므로 SPEC 전파 불요

## 예상 산출물

- [태스크](../../../tasks/20260503/마스터도메인설계/TASK1.md) (또는 분할 시 TASK1-1·1-2·1-3)

---

## 부록: 도메인/DB 검토 결과 (2026-05-03)

### wtp-domain-expert (도메인 정합성)

- **블로커**: 2건 — 본 PLAN 갱신으로 모두 해소 (도메인 룰 위반이 아닌 PLAN 명시 부족)
  - 블로커 1: 인터록 재검사 경로 가정 누락 → 위 §도메인 룰 (PLAN 제약) + §가정 및 미해결 질문 에 인터록 재검사 경로 보존 의무 추가
  - 블로커 2: `corr_val` 갱신과 SCADA_TIMEOUT 판정 경계 미정의 → 위 §도메인 룰 + §가정 에 분리 보장 의무 추가
- **권고(중간)**: 1건 — `pump_predc_h.pwtf_id` → `facility_id` 변경의 AI 추론 입력 경로 영향 → §가정 에 추가 (`ot-integration.md §6.6` 정합)
- **참고(낮음)**: 1건 — `ai_drvn_mod_h` immutable 이력 예외 적용 확인 → §가정 에 추가

### wtp-dba-reviewer (DB 스키마·쿼리 성능)

- **블로커**: 0건. PLAN approve 비차단
- **DOM_TAG_SRL_NO_50 2차 승인**: **승인 완료** (VARCHAR(50)·String·NOT NULL 정합. `DOM_TAG_NM_50` 과 의미 분리 합리성 확인 — `tag_srl_no` 는 외부 할당 자연키 PK용, `tag_nm` 은 시계열 참조 컬럼용)
- **권고(중간)**: 2건 — 본 PLAN 갱신으로 모두 반영
  - JPA JOINED N+1 시점 명시 → §Phase 3 에 부모 다형성 목록 조회 메서드 `@BatchSize`/`@EntityGraph` 적용 의무 추가
  - V6_5 BRIN 인덱스 파티션 DDL 순서 → §Phase 2 에 4단계 순서 명시 (부모 테이블 → 인덱스 → 자식 파티션)
- **참고(낮음)**: 2건 — 본 PLAN 갱신으로 모두 반영
  - `idx_instrument_m_facility_id` 단독 인덱스 중복 → §인덱스 표 에서 제거 (UNIQUE 복합이 선두 `facility_id` 커버)
  - V2 컬럼명 변경 후 COMMENT 갱신 의무 → §Phase 2 에 명시

### 통과 항목 요약

- 시계열 파티셔닝·BRIN·복합 인덱스 컬럼 순서 모두 룰 정합 (`db/indexing-and-migration.md §1`·`db/partitioning-and-retention.md §1`)
- 시계열 → 마스터 FK 금지 + BaseEntity 4 audit 메타 양립 정합
- 무중단 마이그레이션 면제 근거 (운영 데이터 0건 + 첫 init + ddl-auto: none) 타당
- DOM_YN DDL 정책 준수 (`main_yn`·`use_yn` VARCHAR(1) + CHECK 미추가 + DEFAULT 미설정)
- 도메인 4영역 (알람·인터록·운전 모드·이력 기록) 룰 정합
- AI 운전 모드 강제 전환 정책 (`facility_type_cd` 필터·이중 체계 보존) 정합

### 종합 결론

블로커 2건 (도메인) 은 PLAN 명시 보강으로 해소. DBA 블로커 0건 + 신규 데이터 도메인 2차 승인 완료. 권고·참고 6건 모두 PLAN 본문에 반영. **PLAN approve 비차단**.
