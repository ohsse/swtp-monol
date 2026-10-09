---
status: completed
created: 2026-05-04
updated: 2026-05-04
---

# 마스터도메인설계 TASK1-2 — 신규 엔티티 + Repository

## 관련 계획
- [PLAN1](../../../plan/20260503/마스터도메인설계/PLAN1.md)

## 관련 분할 TASK
- [TASK1-1 정적 자산 (enum + DDL)](TASK1-1.md)
- TASK1-2 신규 엔티티 + Repository — 본 파일
- [TASK1-3 기존 패키지 분리 이관 + 빌드 검증](TASK1-3.md)

## Phase

### Phase 3: 신규 엔티티 + Repository

> 적용 패턴: `entity-patterns.md` §JPA JOINED + DiscriminatorColumn 다형성 (직전 커밋 8ff1213) + §외부 할당 PK 엔티티 패턴 + §여부(Y/N) 필드 + §기본 엔티티 패턴.
>
> 모듈 위치 결정: 엔티티 = common/src/main/java (common/CLAUDE.md §허용 범위 §도메인 엔티티), Repository = api/src/main/java (common/CLAUDE.md §금지 범위 §JpaRepository 정합 + 기존 PumpRepository·UserRepository·AiDrvnModeRepository 위치 패턴 일관). 본 결정은 PLAN1.md §Phase 3 의 "Repository 4건 (도메인별, 자식별 분리는 TASK 단계 결정)" 위임 사항을 본 TASK 에서 확정.
>
> 의무 사항 (PLAN1.md §Phase 3 정합):
> - 부모 다형성 목록 조회 메서드는 @BatchSize 또는 @EntityGraph 적용 (N+1 방지, db/query-tuning.md §2)
> - 자식 종류별 도메인 룰 분기 시 findByEquipTypeCd·findByFacilityTypeCd 등 필터 메서드 별도 노출 (entity-patterns.md §도메인 룰 정합)
> - 자식별 분리 Repository 는 본 TASK 에서 미작성 — 부모 다형성 Repository 4건만 작성. 향후 도메인 시나리오 등장 시 별도 PLAN 에서 자식 Repository 신설 결정

#### Facility 부모 + 자식 3종

- [x] `common/src/main/java/com/mo/swtp/facility/domain/Facility.java` 작성 (abstract, @Inheritance(strategy=InheritanceType.JOINED), @DiscriminatorColumn(name=facility_type_cd, length=20), @Table(uniqueConstraints=@UniqueConstraint(name=uk_facility_m_facility_nm, columnNames=facility_nm)), @GeneratedValue(strategy=GenerationType.UUID) PK, BaseEntity 상속, parent_facility_id self-FK NULL 허용, disp_ord NOT NULL, main_yn YnType, use_yn YnType) → 검증: grep "@Inheritance\|@DiscriminatorColumn\|@UniqueConstraint\|extends BaseEntity\|abstract class Facility" 모두 매칭
- [x] `common/src/main/java/com/mo/swtp/facility/domain/PurifiedWaterTank.java` 작성 (@Entity @Table(name=pwtf_m) @DiscriminatorValue("PWTF") extends Facility, 자식 전용 컬럼 0건 skeleton — 추후 요구사항명세서 기반 PLAN 에서 결정) → 검증: grep "@DiscriminatorValue.*PWTF\|extends Facility" 매칭
- [x] `common/src/main/java/com/mo/swtp/facility/domain/DistributionWaterTank.java` 작성 (@Entity @Table(name=dwt_m) @DiscriminatorValue("DWT") extends Facility, 자식 skeleton) → 검증: grep "@DiscriminatorValue.*DWT" 매칭
- [x] `common/src/main/java/com/mo/swtp/facility/domain/Reservoir.java` 작성 (@Entity @Table(name=rsv_m) @DiscriminatorValue("RSV") extends Facility, 자식 skeleton) → 검증: grep "@DiscriminatorValue.*RSV" 매칭

#### Instrument 부모 + 자식 6종

- [x] `common/src/main/java/com/mo/swtp/instrument/domain/Instrument.java` 작성 (abstract, @Inheritance(JOINED), @DiscriminatorColumn(equip_type_cd), @Table(uniqueConstraints=@UniqueConstraint(name=uk_instrument_m_facility_instrument, columnNames={facility_id, instrument_nm}) 복합), @GeneratedValue(UUID) PK, facility_id NOT NULL @ManyToOne FK to Facility, BaseEntity 상속, disp_ord NOT NULL, use_yn YnType) → 검증: grep "@UniqueConstraint.*facility_id.*instrument_nm\|@ManyToOne.*Facility" 매칭
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/Pump.java` 작성 (@Entity @Table(name=pump_m) @DiscriminatorValue("PUMP") extends Instrument, 자식 skeleton) → 검증: grep "@DiscriminatorValue.*PUMP\|extends Instrument" 매칭
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/Valve.java` 작성 (@DiscriminatorValue("VALVE") extends Instrument, skeleton) → 검증: grep "@DiscriminatorValue.*VALVE" 매칭
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/FlowMeter.java` 작성 (@DiscriminatorValue("FLWMTR") extends Instrument, skeleton) → 검증: grep "@DiscriminatorValue.*FLWMTR" 매칭
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/PressureMeter.java` 작성 (@DiscriminatorValue("PRSMTR") extends Instrument, skeleton) → 검증: grep "@DiscriminatorValue.*PRSMTR" 매칭
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/LevelMeter.java` 작성 (@DiscriminatorValue("LVMTR") extends Instrument, skeleton) → 검증: grep "@DiscriminatorValue.*LVMTR" 매칭
- [x] `common/src/main/java/com/mo/swtp/instrument/domain/PowerMeter.java` 작성 (@DiscriminatorValue("ELCMTR") extends Instrument, skeleton) → 검증: grep "@DiscriminatorValue.*ELCMTR" 매칭

#### Tag 자연키 PK + RawData 시계열

- [x] `common/src/main/java/com/mo/swtp/tag/domain/Tag.java` 작성 (implements Persistable<String> — getId() 만 override, isNew() BaseEntity 위임, @Id tag_srl_no PK 자연키 VARCHAR(50) DOM_TAG_SRL_NO_50, instrument_id NOT NULL @ManyToOne FK to Instrument, tag_se_cd @Enumerated(EnumType.STRING) TagMeasurementType, tag_desc, unit_cd, io_cd @Enumerated(EnumType.STRING) IoCode, BaseEntity 상속) → 검증: grep "implements Persistable<String>\|public String getId()\|@Enumerated.*TagMeasurementType\|@Enumerated.*IoCode" 모두 매칭
- [x] `common/src/main/java/com/mo/swtp/raw/domain/RawData.java` 작성 (BaseEntity 상속, @SequenceGenerator(name=rawdata_seq, allocationSize=100) BIGINT rawdata_id + acq_dtm 복합 PK @IdClass 또는 @EmbeddedId, tag_srl_no NOT NULL VARCHAR(50) 논리 참조 — FK 설정 안 함 시계열→마스터 FK 금지 db/partitioning-and-retention.md §1, raw_val NUMERIC(15,4) NULL, corr_val NULL, quality_cd @Enumerated(STRING) QualityCode NOT NULL, INSERT-only 컬럼 immutable @PreUpdate 검증 — tag_srl_no·acq_dtm·raw_val 변경 시 IllegalStateException, corr_val 만 갱신 허용) → 검증: grep "@SequenceGenerator.*allocationSize.*100\|@PreUpdate\|IllegalStateException" 모두 매칭

#### Repository 4건 (api 모듈, 부모 다형성만)

- [x] `api/src/main/java/com/mo/swtp/facility/repository/FacilityRepository.java` 작성 (extends JpaRepository<Facility, String>, findByFacilityTypeCd(FacilityType) 자식 종류 필터 메서드, 부모 다형성 목록 조회 시 @EntityGraph(attributePaths={...}) 또는 @BatchSize(N+1 방지)) → 검증: grep "JpaRepository<Facility, String>\|findByFacilityTypeCd\|@EntityGraph\|@BatchSize" 모두 매칭
- [x] `api/src/main/java/com/mo/swtp/instrument/repository/InstrumentRepository.java` 작성 (extends JpaRepository<Instrument, String>, findByEquipTypeCd(EquipType) — entity-patterns.md §도메인 룰 §equip_type_cd 필터 강제 정합, findByFacilityFacilityId(String) FK 조회, N+1 방지 적용) → 검증: grep "findByEquipTypeCd\|findByFacility\|@EntityGraph\|@BatchSize" 매칭
- [x] `api/src/main/java/com/mo/swtp/tag/repository/TagRepository.java` 작성 (extends JpaRepository<Tag, String>, findByInstrumentInstrumentId(String) FK 조회) → 검증: grep "JpaRepository<Tag, String>\|findByInstrument" 매칭
- [x] `api/src/main/java/com/mo/swtp/raw/repository/RawDataRepository.java` 작성 (extends JpaRepository<RawData, RawDataId> — RawData 의 복합키 ID 클래스 RawDataId 결정 후 작성, 시계열 → 마스터 FK 미설정으로 instrument 조회 메서드는 tag_srl_no 논리 매핑만 노출) → 검증: grep "JpaRepository<RawData" 매칭

## 산출물
- [결과](../../../results/20260503/마스터도메인설계/RESULT1.md)
