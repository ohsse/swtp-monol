package com.mo.swtp.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import jakarta.persistence.DiscriminatorValue;
import org.junit.jupiter.api.Test;

/**
 * {@link SensorPoint} 자식 엔티티 단위 테스트.
 *
 * <p>FR-PMP-002 (운전현황 분석) BRANCH_MEAS_PREDC_DATA 매핑을 위한 facility 자식 'POINT' 검증.
 * facility_type_cd 값은 영속화 시점에 JPA 가 자동 주입하므로 본 단위 테스트에서는
 * {@link DiscriminatorValue} 어노테이션 검증으로 대체한다.</p>
 */
class SensorPointTest {

    @Test
    void create_정적_팩토리는_부모_Facility_필드를_정상_반영한다() {
        SensorPoint sp = SensorPoint.create("분기점-001", null, 1, YnType.Y);

        assertThat(sp.getFacilityNm()).isEqualTo("분기점-001");
        assertThat(sp.getParentFacilityId()).isNull();
        assertThat(sp.getDispOrd()).isEqualTo(1);
        assertThat(sp.getMainYn()).isEqualTo(YnType.Y);
        assertThat(sp.getUseYn()).isEqualTo(YnType.Y);
    }

    @Test
    void create_정적_팩토리는_use_yn_을_Y_로_고정한다() {
        SensorPoint sp = SensorPoint.create("분기점-002", "parent-uuid", 2, YnType.N);

        assertThat(sp.getUseYn()).isEqualTo(YnType.Y);
        assertThat(sp.getMainYn()).isEqualTo(YnType.N);
    }

    @Test
    void SensorPoint_는_Facility_를_상속한다() {
        SensorPoint sp = SensorPoint.create("분기점-003", null, 3, YnType.Y);

        assertThat(sp).isInstanceOf(Facility.class);
    }

    @Test
    void DiscriminatorValue_는_FacilityType_POINT_와_일치한다() {
        DiscriminatorValue dv = SensorPoint.class.getAnnotation(DiscriminatorValue.class);

        assertThat(dv).isNotNull();
        assertThat(dv.value()).isEqualTo(FacilityType.POINT.name());
    }
}
