package com.mo.swtp.instrument.domain;

import com.mo.swtp.common.domain.BaseEntity;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계측기 단일 마스터 엔티티 (JPA JOINED 다형성 부모).
 *
 * <p>{@link Pump}·{@link Valve}·{@link FlowMeter}·{@link PressureMeter}·{@link LevelMeter}·{@link PowerMeter}
 * 6종 자식을 {@code equip_type_cd} DiscriminatorColumn 으로 식별한다.
 * 마스터도메인설계 ANALYZE1 Round 2 (2026-05-02) 결정으로 종전 {@code pump_m} 별도 마스터를
 * 본 단일 마스터로 통합하였다.</p>
 *
 * <p>PK는 {@code @GeneratedValue(GenerationType.UUID)} 로 자동 생성하며 {@code Persistable} 미구현
 * (Round 3 결정, 2026-05-03). 사용자 식별은 {@code (facility_id, instrument_nm)} 복합 UNIQUE
 * 비즈니스 키에 의존한다 — 멀티테넌트 운영 정합 (정수장별로 같은 이름 허용, 사용자 결정 2026-05-03).</p>
 *
 * <p>도메인 룰: 인터록 평가·제어 명령 발행 시 자식 종류별 도메인 룰이 다른 시나리오에서는
 * 부모 다형성 조회 시 반드시 {@code equip_type_cd} 필터를 명시한다.
 * 1차 정의: {@code .claude/rules/entity-patterns.md} §JPA JOINED + DiscriminatorColumn 다형성 패턴 §도메인 룰 —
 * {@code equip_type_cd} 필터 강제.</p>
 *
 * <p>참조: {@code docs/plan/20260503/마스터도메인설계/PLAN1.md} §도메인 모델, §도메인 룰.</p>
 */
@Entity
@Table(
        name = "instrument_m",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_instrument_m_facility_instrument",
                columnNames = {"facility_id", "instrument_nm"}
        )
)
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(
        name = "equip_type_cd",
        discriminatorType = DiscriminatorType.STRING,
        length = 20
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Instrument extends BaseEntity {

    /** 계측기 ID — UUID 자동 생성 PK (DOM_ID_36). */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "instrument_id", nullable = false, length = 36)
    private String instrumentId;

    /** 계측기명 — {@code (facility_id, instrument_nm)} 복합 UNIQUE (DOM_NAME_100). */
    @Column(name = "instrument_nm", nullable = false, length = 100)
    private String instrumentNm;

    /**
     * 장비 유형 코드 — DiscriminatorColumn (PUMP·VALVE·FLWMTR·PRSMTR·LVMTR·ELCMTR).
     * 영속 컨텍스트가 자동 관리하므로 {@code insertable=false, updatable=false}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "equip_type_cd", nullable = false, length = 20, insertable = false, updatable = false)
    private EquipType equipType;

    /** 소속 시설 — 마스터 참조 FK to {@code facility_m} (NOT NULL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    /** 표시 순서 — 화면 표시 정렬 순번. */
    @Column(name = "disp_ord", nullable = false)
    private Integer dispOrd;

    /** 사용 여부 (Y/N, DOM_YN). */
    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = 1)
    private YnType useYn;

    /**
     * 자식 클래스가 신규 계측기를 생성할 때 호출하는 protected 생성자.
     *
     * @param instrumentNm  계측기명
     * @param facility      소속 시설
     * @param dispOrd       표시 순서
     * @param useYn         사용 여부
     */
    protected Instrument(
            String instrumentNm,
            Facility facility,
            Integer dispOrd,
            YnType useYn) {
        this.instrumentNm = instrumentNm;
        this.facility = facility;
        this.dispOrd = dispOrd;
        this.useYn = useYn;
    }

    /**
     * 계측기 기본 정보를 변경한다. null 인자는 기존 값을 유지한다.
     *
     * @param instrumentNm  계측기명
     * @param facility      소속 시설
     * @param dispOrd       표시 순서
     */
    public void changeInfo(String instrumentNm, Facility facility, Integer dispOrd) {
        if (instrumentNm != null) {
            this.instrumentNm = instrumentNm;
        }
        if (facility != null) {
            this.facility = facility;
        }
        if (dispOrd != null) {
            this.dispOrd = dispOrd;
        }
    }

    /** 계측기를 비활성화(논리 삭제) 한다. */
    public void deactivate() {
        this.useYn = YnType.N;
    }
}
