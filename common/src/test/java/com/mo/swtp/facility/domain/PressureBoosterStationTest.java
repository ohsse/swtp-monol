package com.mo.swtp.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import jakarta.persistence.DiscriminatorValue;
import org.junit.jupiter.api.Test;

/**
 * {@link PressureBoosterStation} 자식 엔티티 단위 테스트.
 *
 * <p>송수펌프제어분석 ANALYZE1 (2026-05-08) — facility 자식 'PRSF' skeleton 검증.
 * facility_type_cd 값은 영속화 시점에 JPA 가 자동 주입하므로 본 단위 테스트에서는
 * {@link DiscriminatorValue} 어노테이션 검증으로 대체한다 (부모 PK 자동 상속도 동일 — JPA JOINED 매핑 검증은
 * 통합 테스트 범위, 본 단위 테스트는 정적 팩토리 + 어노테이션 매핑까지).</p>
 */
class PressureBoosterStationTest {

    @Test
    void create_정적_팩토리는_부모_Facility_필드를_정상_반영한다() {
        PressureBoosterStation prsf = PressureBoosterStation.create("가압장-001", null, 1, YnType.Y);

        assertThat(prsf.getFacilityNm()).isEqualTo("가압장-001");
        assertThat(prsf.getParentFacilityId()).isNull();
        assertThat(prsf.getDispOrd()).isEqualTo(1);
        assertThat(prsf.getMainYn()).isEqualTo(YnType.Y);
        assertThat(prsf.getUseYn()).isEqualTo(YnType.Y);
    }

    @Test
    void PressureBoosterStation_는_Facility_를_상속한다() {
        PressureBoosterStation prsf = PressureBoosterStation.create("가압장-002", "parent-uuid", 2, YnType.Y);

        assertThat(prsf).isInstanceOf(Facility.class);
    }

    @Test
    void DiscriminatorValue_는_FacilityType_PRSF_와_일치한다() {
        DiscriminatorValue dv = PressureBoosterStation.class.getAnnotation(DiscriminatorValue.class);

        assertThat(dv).isNotNull();
        assertThat(dv.value()).isEqualTo(FacilityType.PRSF.name());
    }

    @Test
    void create_는_main_yn_Y_분기를_정상_반영한다() {
        PressureBoosterStation prsf = PressureBoosterStation.create("주요-가압장", null, 1, YnType.Y);

        assertThat(prsf.getMainYn()).isEqualTo(YnType.Y);
        assertThat(prsf.getUseYn()).isEqualTo(YnType.Y);
    }

    @Test
    void create_는_main_yn_N_분기에서도_use_yn_을_Y_로_고정한다() {
        PressureBoosterStation prsf = PressureBoosterStation.create("보조-가압장", null, 2, YnType.N);

        assertThat(prsf.getMainYn()).isEqualTo(YnType.N);
        assertThat(prsf.getUseYn()).isEqualTo(YnType.Y);
    }
}
