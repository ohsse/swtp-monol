package com.mo.swtp.facility.domain;

import com.mo.swtp.common.enumtype.YnType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배수지 자식 엔티티 — JPA JOINED 자식 ({@code facility_type_cd = 'DWT'}).
 *
 * <p>자식 PK는 부모 {@link Facility#getFacilityId()} 와 동일하며 JPA JOINED 표준 동작으로 자동 상속된다.</p>
 *
 * <p>자식 전용 컬럼 2건:</p>
 * <ul>
 *   <li>{@link #minReqPrsr} ({@code min_req_prsr}, DOM_QTY_15_4 NOT NULL) — 최소 요구 압력 kgf/cm²
 *       (송수펌프제어분석 ANALYZE1, 2026-05-08 도입)</li>
 *   <li>{@link #minReqBranchPrsr} ({@code min_req_branch_prsr}, DOM_QTY_15_4 NOT NULL) — 분기점 최소 요구 압력 kgf/cm²
 *       (송수펌프제어분석-5번섹션 ANALYZE1, 2026-05-14 도입)</li>
 * </ul>
 *
 * <p>NOT NULL 정책 ({@code pumpcontrol_null_alignment ANALYZE1, 2026-04-25}): DOM_QTY_15_4 기본 NULL 정책에서
 * 더 엄격하게 적용 — 인터록 평가 NULL/미입력 구별 불가 방지. {@code minReqBranchPrsr} 도 본 사이클은 표출 전용이나
 * 인터록 평가 편입 가능성 대비 NOT NULL 동일 적용 (송수펌프제어분석-5번섹션 ANALYZE1).</p>
 */
@Entity
@Table(name = "dwt_m")
@DiscriminatorValue("DWT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DistributionWaterTank extends Facility {

    /** 최소 요구 압력 (kgf/cm², DOM_QTY_15_4) — 인터록 평가 기준값. */
    @Column(name = "min_req_prsr", nullable = false, precision = 15, scale = 4)
    private BigDecimal minReqPrsr;

    /** 분기점 최소 요구 압력 (kgf/cm², DOM_QTY_15_4) — 배수지로 분기되는 관로 분기점 지점의 최소 요구 압력. */
    @Column(name = "min_req_branch_prsr", nullable = false, precision = 15, scale = 4)
    private BigDecimal minReqBranchPrsr;

    /**
     * 신규 배수지를 생성한다. NOT NULL 컬럼 2건 ({@code minReqPrsr}·{@code minReqBranchPrsr}) 은
     * {@link Objects#requireNonNull} 로 사전 검증한다.
     *
     * @param facilityNm        시설명
     * @param parentFacilityId  상위 시설 ID (NULL 허용)
     * @param dispOrd           표시 순서
     * @param mainYn            주요 시설 여부
     * @param minReqPrsr        최소 요구 압력 kgf/cm² (NOT NULL)
     * @param minReqBranchPrsr  분기점 최소 요구 압력 kgf/cm² (NOT NULL)
     * @return 생성된 배수지 ({@code use_yn = Y})
     */
    public static DistributionWaterTank create(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn,
            BigDecimal minReqPrsr,
            BigDecimal minReqBranchPrsr) {
        Objects.requireNonNull(minReqPrsr, "minReqPrsr must not be null");
        Objects.requireNonNull(minReqBranchPrsr, "minReqBranchPrsr must not be null");
        return new DistributionWaterTank(
                facilityNm, parentFacilityId, dispOrd, mainYn, minReqPrsr, minReqBranchPrsr);
    }

    private DistributionWaterTank(
            String facilityNm,
            String parentFacilityId,
            Integer dispOrd,
            YnType mainYn,
            BigDecimal minReqPrsr,
            BigDecimal minReqBranchPrsr) {
        super(facilityNm, parentFacilityId, dispOrd, mainYn, YnType.Y);
        this.minReqPrsr = minReqPrsr;
        this.minReqBranchPrsr = minReqBranchPrsr;
    }

    /**
     * 최소 요구 압력을 변경한다.
     *
     * <p>NOT NULL 컬럼 ({@code pumpcontrol_null_alignment ANALYZE1, 2026-04-25}) 이므로 null 입력은
     * {@link Objects#requireNonNull} 로 사전 차단한다. {@link Facility#changeInfo} 가 부모 공통 4필드
     * (facilityNm·parentFacilityId·dispOrd·mainYn) 를 처리하는 것과 대칭으로 자식 전용 컬럼 변경 책임을
     * 자식 엔티티에 위임한다 (시설물관리기능 ANALYZE1·PLAN1, 2026-05-11 사용자 승인).</p>
     *
     * @param minReqPrsr 변경할 최소 요구 압력 kgf/cm² (NOT NULL)
     */
    public void changeMinReqPrsr(BigDecimal minReqPrsr) {
        Objects.requireNonNull(minReqPrsr, "minReqPrsr must not be null");
        this.minReqPrsr = minReqPrsr;
    }

    /**
     * 분기점 최소 요구 압력을 변경한다.
     *
     * <p>NOT NULL 컬럼 (송수펌프제어분석-5번섹션 ANALYZE1, 2026-05-14) 이므로 null 입력은
     * {@link Objects#requireNonNull} 로 사전 차단한다. {@link #changeMinReqPrsr} 와 동일 책임 패턴 —
     * 자식 전용 컬럼 변경 책임을 자식 엔티티에 위임한다.</p>
     *
     * @param minReqBranchPrsr 변경할 분기점 최소 요구 압력 kgf/cm² (NOT NULL)
     */
    public void changeMinReqBranchPrsr(BigDecimal minReqBranchPrsr) {
        Objects.requireNonNull(minReqBranchPrsr, "minReqBranchPrsr must not be null");
        this.minReqBranchPrsr = minReqBranchPrsr;
    }
}
