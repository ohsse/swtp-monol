package com.mo.swtp.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mo.swtp.common.enumtype.YnType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * {@link DistributionWaterTank} 자식 엔티티 단위 테스트.
 *
 * <p>송수펌프제어분석-5번섹션 ANALYZE1 (2026-05-14) — 자식 전용 컬럼 1→2 확장에 따른 검증:
 * {@code minReqBranchPrsr} 신규 NOT NULL 컬럼 추가 후 정적 팩토리 + 변경 메서드 동작을 검증한다.</p>
 */
class DistributionWaterTankTest {

    private static final BigDecimal MIN_REQ_PRSR = new BigDecimal("1.5000");
    private static final BigDecimal MIN_REQ_BRANCH_PRSR = new BigDecimal("0.8000");

    @Test
    void create_정적_팩토리는_자식_전용_2컬럼을_포함하여_정상_생성한다() {
        DistributionWaterTank dwt = DistributionWaterTank.create(
                "배수지A", "parent-uuid", 1, YnType.Y, MIN_REQ_PRSR, MIN_REQ_BRANCH_PRSR);

        assertThat(dwt.getFacilityNm()).isEqualTo("배수지A");
        assertThat(dwt.getParentFacilityId()).isEqualTo("parent-uuid");
        assertThat(dwt.getDispOrd()).isEqualTo(1);
        assertThat(dwt.getMainYn()).isEqualTo(YnType.Y);
        assertThat(dwt.getUseYn()).isEqualTo(YnType.Y);
        assertThat(dwt.getMinReqPrsr()).isEqualByComparingTo(MIN_REQ_PRSR);
        assertThat(dwt.getMinReqBranchPrsr()).isEqualByComparingTo(MIN_REQ_BRANCH_PRSR);
    }

    @Test
    void create_는_minReqPrsr_가_null_이면_NPE_를_던진다() {
        assertThatThrownBy(() -> DistributionWaterTank.create(
                "배수지B", null, 1, YnType.Y, null, MIN_REQ_BRANCH_PRSR))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("minReqPrsr");
    }

    @Test
    void create_는_minReqBranchPrsr_가_null_이면_NPE_를_던진다() {
        assertThatThrownBy(() -> DistributionWaterTank.create(
                "배수지C", null, 1, YnType.Y, MIN_REQ_PRSR, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("minReqBranchPrsr");
    }

    @Test
    void changeMinReqPrsr_는_새_값을_정상_반영한다() {
        DistributionWaterTank dwt = DistributionWaterTank.create(
                "배수지D", null, 1, YnType.Y, MIN_REQ_PRSR, MIN_REQ_BRANCH_PRSR);
        BigDecimal next = new BigDecimal("2.5000");

        dwt.changeMinReqPrsr(next);

        assertThat(dwt.getMinReqPrsr()).isEqualByComparingTo(next);
    }

    @Test
    void changeMinReqPrsr_는_null_입력시_NPE_를_던진다() {
        DistributionWaterTank dwt = DistributionWaterTank.create(
                "배수지E", null, 1, YnType.Y, MIN_REQ_PRSR, MIN_REQ_BRANCH_PRSR);

        assertThatThrownBy(() -> dwt.changeMinReqPrsr(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("minReqPrsr");
    }

    @Test
    void changeMinReqBranchPrsr_는_새_값을_정상_반영하고_null_입력시_NPE_를_던진다() {
        DistributionWaterTank dwt = DistributionWaterTank.create(
                "배수지F", null, 1, YnType.Y, MIN_REQ_PRSR, MIN_REQ_BRANCH_PRSR);
        BigDecimal next = new BigDecimal("1.2000");

        dwt.changeMinReqBranchPrsr(next);
        assertThat(dwt.getMinReqBranchPrsr()).isEqualByComparingTo(next);

        assertThatThrownBy(() -> dwt.changeMinReqBranchPrsr(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("minReqBranchPrsr");
    }
}
