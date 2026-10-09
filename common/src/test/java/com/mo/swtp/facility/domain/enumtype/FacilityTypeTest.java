package com.mo.swtp.facility.domain.enumtype;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * {@link FacilityType} 그룹 매핑 단위 테스트.
 *
 * <p>시설_도메인_확장 ANALYZE1 (2026-06-08) — 12종 시설 유형의 {@link FacilityGroup} 파생 매핑 검증.
 * type → group 1:1 불변 매핑 + 그룹별 소속 카운트 (저장 3·운영 8·계통 1).</p>
 */
class FacilityTypeTest {

    @Test
    void 시설_유형은_12종이다() {
        assertThat(FacilityType.values()).hasSize(12);
    }

    @Test
    void 저장시설_그룹은_PWTF_DWT_RSV_3종이다() {
        assertThat(FacilityType.PWTF.getGroup()).isEqualTo(FacilityGroup.STORAGE);
        assertThat(FacilityType.DWT.getGroup()).isEqualTo(FacilityGroup.STORAGE);
        assertThat(FacilityType.RSV.getGroup()).isEqualTo(FacilityGroup.STORAGE);

        long storageCnt = Arrays.stream(FacilityType.values())
                .filter(t -> t.getGroup() == FacilityGroup.STORAGE)
                .count();
        assertThat(storageCnt).isEqualTo(3);
    }

    @Test
    void 계통시설_그룹은_POINT_1종이다() {
        assertThat(FacilityType.POINT.getGroup()).isEqualTo(FacilityGroup.NETWORK);

        long networkCnt = Arrays.stream(FacilityType.values())
                .filter(t -> t.getGroup() == FacilityGroup.NETWORK)
                .count();
        assertThat(networkCnt).isEqualTo(1);
    }

    @Test
    void 운영시설_그룹은_PRSF_와_신규_7종_합_8종이다() {
        assertThat(FacilityType.PRSF.getGroup()).isEqualTo(FacilityGroup.OPERATION);
        assertThat(FacilityType.WTBLD.getGroup()).isEqualTo(FacilityGroup.OPERATION);
        assertThat(FacilityType.CHMB.getGroup()).isEqualTo(FacilityGroup.OPERATION);
        assertThat(FacilityType.ACFB.getGroup()).isEqualTo(FacilityGroup.OPERATION);
        assertThat(FacilityType.POZB.getGroup()).isEqualTo(FacilityGroup.OPERATION);
        assertThat(FacilityType.FLTB.getGroup()).isEqualTo(FacilityGroup.OPERATION);
        assertThat(FacilityType.DEWB.getGroup()).isEqualTo(FacilityGroup.OPERATION);
        assertThat(FacilityType.SOLAR.getGroup()).isEqualTo(FacilityGroup.OPERATION);

        long operationCnt = Arrays.stream(FacilityType.values())
                .filter(t -> t.getGroup() == FacilityGroup.OPERATION)
                .count();
        assertThat(operationCnt).isEqualTo(8);
    }

    @Test
    void 모든_시설_유형은_그룹과_한글_설명을_보유한다() {
        for (FacilityType type : FacilityType.values()) {
            assertThat(type.getGroup()).as("%s 의 그룹", type).isNotNull();
            assertThat(type.getDescription()).as("%s 의 설명", type).isNotBlank();
        }
    }

    @Test
    void 그룹별_카운트_합은_전체_12종과_일치한다() {
        long total = Arrays.stream(FacilityGroup.values())
                .mapToLong(g -> Arrays.stream(FacilityType.values())
                        .filter(t -> t.getGroup() == g)
                        .count())
                .sum();
        assertThat(total).isEqualTo(FacilityType.values().length).isEqualTo(12);
    }

    // --- typesOf(FacilityGroup) group→type 파생 SSOT (설비별사용량-2번섹션 ANALYZE1, 2026-06-09) ---

    @Test
    void typesOf_OPERATION_은_운영시설_8종을_반환한다() {
        assertThat(FacilityType.typesOf(FacilityGroup.OPERATION))
                .containsExactlyInAnyOrder(
                        FacilityType.PRSF, FacilityType.WTBLD, FacilityType.CHMB,
                        FacilityType.ACFB, FacilityType.POZB, FacilityType.FLTB,
                        FacilityType.DEWB, FacilityType.SOLAR);
    }

    @Test
    void typesOf_STORAGE_는_저장시설_3종을_반환한다() {
        assertThat(FacilityType.typesOf(FacilityGroup.STORAGE))
                .containsExactlyInAnyOrder(
                        FacilityType.PWTF, FacilityType.DWT, FacilityType.RSV);
    }

    @Test
    void typesOf_NETWORK_는_계통시설_1종을_반환한다() {
        assertThat(FacilityType.typesOf(FacilityGroup.NETWORK))
                .containsExactly(FacilityType.POINT);
    }

    @Test
    void typesOf_그룹별_결과_합은_전체_12종과_일치한다() {
        int total = FacilityType.typesOf(FacilityGroup.STORAGE).size()
                + FacilityType.typesOf(FacilityGroup.OPERATION).size()
                + FacilityType.typesOf(FacilityGroup.NETWORK).size();

        assertThat(total).isEqualTo(FacilityType.values().length);
    }
}
