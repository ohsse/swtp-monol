package com.mo.swtp.facility.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.ActivatedCarbonFilter;
import com.mo.swtp.facility.domain.ChemicalBuilding;
import com.mo.swtp.facility.domain.DewateringBuilding;
import com.mo.swtp.facility.domain.DistributionWaterTank;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.FiltrationBuilding;
import com.mo.swtp.facility.domain.PreOzonationBuilding;
import com.mo.swtp.facility.domain.PressureBoosterStation;
import com.mo.swtp.facility.domain.PurifiedWaterTank;
import com.mo.swtp.facility.domain.Reservoir;
import com.mo.swtp.facility.domain.SensorPoint;
import com.mo.swtp.facility.domain.SolarPowerFacility;
import com.mo.swtp.facility.domain.WaterTransmissionBuilding;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * {@link FacilityDto} 자식 12종 다형성 직렬화 단위 테스트.
 *
 * <p>Jackson {@code @JsonTypeInfo(EXISTING_PROPERTY) + @JsonSubTypes} 다형성 적용 검증 — 자식
 * 종류별 응답 스키마에 자식 전용 필드 노출/미노출 + {@code facilityTypeCd} discriminator 값 일치 +
 * {@code facilityGroupCd} 파생 필드 노출.</p>
 *
 * <p>시설물응답DTO명세 ANALYZE1·PLAN1 (2026-05-12) — Phase 2 §자식별 응답 DTO 직렬화 검증.
 * 시설_도메인_확장 ANALYZE1 (2026-06-08) — 운영시설 7종 + facilityGroupCd 파생 노출 검증 추가.</p>
 */
class FacilityDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void DwtDto_는_facilityTypeCd_DWT_와_minReqPrsr_minReqBranchPrsr_을_포함한다() throws Exception {
        DistributionWaterTank dwt = DistributionWaterTank.create(
                "배수지-001", null, 1, YnType.Y,
                new BigDecimal("2.5000"), new BigDecimal("0.8000"));
        setFacilityType(dwt, FacilityType.DWT);

        String json = objectMapper.writeValueAsString(DwtDto.from(dwt));

        assertThat(json).contains("\"facilityTypeCd\":\"DWT\"");
        assertThat(json).contains("\"minReqPrsr\":2.5");
        assertThat(json).contains("\"minReqBranchPrsr\":0.8");
        assertThat(json).contains("\"facilityNm\":\"배수지-001\"");
    }

    @Test
    void PwtfDto_는_facilityTypeCd_PWTF_만_포함하고_minReqPrsr_은_미포함한다() throws Exception {
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-001", null, 1, YnType.Y);
        setFacilityType(pwtf, FacilityType.PWTF);

        String json = objectMapper.writeValueAsString(PwtfDto.from(pwtf));

        assertThat(json).contains("\"facilityTypeCd\":\"PWTF\"");
        assertThat(json).contains("\"facilityNm\":\"정수지-001\"");
        assertThat(json).doesNotContain("minReqPrsr");
    }

    @Test
    void RsvDto_는_facilityTypeCd_RSV_를_포함하고_자식_전용_필드는_없다() throws Exception {
        Reservoir rsv = Reservoir.create("저수지-001", null, 1, YnType.Y);
        setFacilityType(rsv, FacilityType.RSV);

        String json = objectMapper.writeValueAsString(RsvDto.from(rsv));

        assertThat(json).contains("\"facilityTypeCd\":\"RSV\"");
        assertThat(json).contains("\"facilityNm\":\"저수지-001\"");
        assertThat(json).doesNotContain("minReqPrsr");
    }

    @Test
    void PrsfDto_는_facilityTypeCd_PRSF_를_포함하고_자식_전용_필드는_없다() throws Exception {
        PressureBoosterStation prsf = PressureBoosterStation.create("가압장-001", null, 1, YnType.Y);
        setFacilityType(prsf, FacilityType.PRSF);

        String json = objectMapper.writeValueAsString(PrsfDto.from(prsf));

        assertThat(json).contains("\"facilityTypeCd\":\"PRSF\"");
        assertThat(json).contains("\"facilityNm\":\"가압장-001\"");
        assertThat(json).doesNotContain("minReqPrsr");
    }

    @Test
    void PointDto_는_facilityTypeCd_POINT_를_포함하고_자식_전용_필드는_없다() throws Exception {
        SensorPoint point = SensorPoint.create("분기점-001", null, 1, YnType.N);
        setFacilityType(point, FacilityType.POINT);

        String json = objectMapper.writeValueAsString(PointDto.from(point));

        assertThat(json).contains("\"facilityTypeCd\":\"POINT\"");
        assertThat(json).contains("\"facilityNm\":\"분기점-001\"");
        assertThat(json).doesNotContain("minReqPrsr");
    }

    @Test
    void FacilityDto_from_은_자식_타입에_매칭되는_DTO_인스턴스를_반환한다() {
        DistributionWaterTank dwt = DistributionWaterTank.create(
                "배수지-002", null, 1, YnType.Y,
                new BigDecimal("3.0000"), new BigDecimal("0.8000"));
        setFacilityType(dwt, FacilityType.DWT);
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-002", null, 2, YnType.Y);
        setFacilityType(pwtf, FacilityType.PWTF);
        Reservoir rsv = Reservoir.create("저수지-002", null, 3, YnType.Y);
        setFacilityType(rsv, FacilityType.RSV);
        PressureBoosterStation prsf = PressureBoosterStation.create("가압장-002", null, 4, YnType.Y);
        setFacilityType(prsf, FacilityType.PRSF);
        SensorPoint point = SensorPoint.create("분기점-002", null, 5, YnType.N);
        setFacilityType(point, FacilityType.POINT);

        assertThat(FacilityDto.from(dwt)).isInstanceOf(DwtDto.class);
        assertThat(FacilityDto.from(pwtf)).isInstanceOf(PwtfDto.class);
        assertThat(FacilityDto.from(rsv)).isInstanceOf(RsvDto.class);
        assertThat(FacilityDto.from(prsf)).isInstanceOf(PrsfDto.class);
        assertThat(FacilityDto.from(point)).isInstanceOf(PointDto.class);
    }

    // ========== 신규 운영시설 7종 다형성 직렬화 + facilityGroupCd 파생 노출 ==========

    @Test
    void 신규_운영시설_7종은_각_discriminator_와_facilityGroupCd_OPERATION_을_포함한다() throws Exception {
        assertSerializedTypeAndGroup(
                WtbldDto.from(newWtbld("송수동-001")), "WTBLD", "OPERATION", "송수동-001");
        assertSerializedTypeAndGroup(
                ChmbDto.from(newChmb("약품동-001")), "CHMB", "OPERATION", "약품동-001");
        assertSerializedTypeAndGroup(
                AcfbDto.from(newAcfb("활성탄여과지-001")), "ACFB", "OPERATION", "활성탄여과지-001");
        assertSerializedTypeAndGroup(
                PozbDto.from(newPozb("전오존동-001")), "POZB", "OPERATION", "전오존동-001");
        assertSerializedTypeAndGroup(
                FltbDto.from(newFltb("여과지동-001")), "FLTB", "OPERATION", "여과지동-001");
        assertSerializedTypeAndGroup(
                DewbDto.from(newDewb("탈수기동-001")), "DEWB", "OPERATION", "탈수기동-001");
        assertSerializedTypeAndGroup(
                SolarDto.from(newSolar("태양광-001")), "SOLAR", "OPERATION", "태양광-001");
    }

    @Test
    void 저장시설_PWTF_는_facilityGroupCd_STORAGE_를_포함한다() throws Exception {
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-003", null, 1, YnType.Y);
        setFacilityType(pwtf, FacilityType.PWTF);

        String json = objectMapper.writeValueAsString(PwtfDto.from(pwtf));

        assertThat(json).contains("\"facilityGroupCd\":\"STORAGE\"");
    }

    @Test
    void 계통시설_POINT_는_facilityGroupCd_NETWORK_를_포함한다() throws Exception {
        SensorPoint point = SensorPoint.create("분기점-003", null, 1, YnType.N);
        setFacilityType(point, FacilityType.POINT);

        String json = objectMapper.writeValueAsString(PointDto.from(point));

        assertThat(json).contains("\"facilityGroupCd\":\"NETWORK\"");
    }

    @Test
    void FacilityDto_from_은_신규_7종_자식_타입에_매칭되는_DTO_인스턴스를_반환한다() {
        assertThat(FacilityDto.from(newWtbld("송수동-002"))).isInstanceOf(WtbldDto.class);
        assertThat(FacilityDto.from(newChmb("약품동-002"))).isInstanceOf(ChmbDto.class);
        assertThat(FacilityDto.from(newAcfb("활성탄여과지-002"))).isInstanceOf(AcfbDto.class);
        assertThat(FacilityDto.from(newPozb("전오존동-002"))).isInstanceOf(PozbDto.class);
        assertThat(FacilityDto.from(newFltb("여과지동-002"))).isInstanceOf(FltbDto.class);
        assertThat(FacilityDto.from(newDewb("탈수기동-002"))).isInstanceOf(DewbDto.class);
        assertThat(FacilityDto.from(newSolar("태양광-002"))).isInstanceOf(SolarDto.class);
    }

    private void assertSerializedTypeAndGroup(
            FacilityDto dto, String typeCd, String groupCd, String facilityNm) throws Exception {
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"facilityTypeCd\":\"" + typeCd + "\"");
        assertThat(json).contains("\"facilityGroupCd\":\"" + groupCd + "\"");
        assertThat(json).contains("\"facilityNm\":\"" + facilityNm + "\"");
        assertThat(json).doesNotContain("minReqPrsr");
    }

    private WaterTransmissionBuilding newWtbld(String nm) {
        WaterTransmissionBuilding e = WaterTransmissionBuilding.create(nm, null, 1, YnType.Y);
        setFacilityType(e, FacilityType.WTBLD);
        return e;
    }

    private ChemicalBuilding newChmb(String nm) {
        ChemicalBuilding e = ChemicalBuilding.create(nm, null, 1, YnType.Y);
        setFacilityType(e, FacilityType.CHMB);
        return e;
    }

    private ActivatedCarbonFilter newAcfb(String nm) {
        ActivatedCarbonFilter e = ActivatedCarbonFilter.create(nm, null, 1, YnType.Y);
        setFacilityType(e, FacilityType.ACFB);
        return e;
    }

    private PreOzonationBuilding newPozb(String nm) {
        PreOzonationBuilding e = PreOzonationBuilding.create(nm, null, 1, YnType.Y);
        setFacilityType(e, FacilityType.POZB);
        return e;
    }

    private FiltrationBuilding newFltb(String nm) {
        FiltrationBuilding e = FiltrationBuilding.create(nm, null, 1, YnType.Y);
        setFacilityType(e, FacilityType.FLTB);
        return e;
    }

    private DewateringBuilding newDewb(String nm) {
        DewateringBuilding e = DewateringBuilding.create(nm, null, 1, YnType.Y);
        setFacilityType(e, FacilityType.DEWB);
        return e;
    }

    private SolarPowerFacility newSolar(String nm) {
        SolarPowerFacility e = SolarPowerFacility.create(nm, null, 1, YnType.Y);
        setFacilityType(e, FacilityType.SOLAR);
        return e;
    }

    /**
     * {@code @DiscriminatorColumn(insertable=false, updatable=false)} 인 {@code facilityType} 필드를
     * 단위 테스트용으로 reflection 주입한다. 영속 컨텍스트가 자동 관리하는 컬럼이라 단위 테스트에서는
     * {@code Facility.create()} 후 null 상태이므로 직렬화 시 discriminator 가 노출되지 않는다.
     */
    private static void setFacilityType(Facility facility, FacilityType type) {
        try {
            Field field = Facility.class.getDeclaredField("facilityType");
            field.setAccessible(true);
            field.set(facility, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
