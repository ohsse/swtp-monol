package com.mo.swtp.instrument.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.PurifiedWaterTank;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.instrument.domain.PumpOprtngType;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * {@link PumpDto} 다형성 직렬화 단위 테스트.
 *
 * <p>Jackson {@code @JsonTypeInfo(EXISTING_PROPERTY) + @JsonSubTypes} 다형성 적용 검증 —
 * {@code equipTypeCd} discriminator + 자식 전용 3 필드 노출 + 폐기된 {@code tagNm} 미노출.</p>
 *
 * <p>시설물응답DTO명세 ANALYZE1·PLAN1 (2026-05-12) — Phase 3 §PumpDto 직렬화 검증.</p>
 */
class PumpDtoSerializationTest {

    private static final BigDecimal RATED_HEAD = new BigDecimal("65.0000");
    private static final BigDecimal RATED_FLWRT = new BigDecimal("250.0000");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void PumpDto_는_equipTypeCd_PUMP_와_자식_전용_4필드를_포함한다() throws Exception {
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-001", null, 1, YnType.Y);
        Pump pump = Pump.create("송수펌프-001", pwtf, 1,
                RATED_HEAD, RATED_FLWRT, PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);
        setEquipType(pump, EquipType.PUMP);

        String json = objectMapper.writeValueAsString(PumpDto.from(pump));

        assertThat(json).contains("\"equipTypeCd\":\"PUMP\"");
        assertThat(json).contains("\"ratedHead\":65.0");
        assertThat(json).contains("\"ratedFlwrt\":250.0");
        assertThat(json).contains("\"oprtngType\":\"AUTO_CAPABLE\"");
        assertThat(json).contains("\"driveType\":\"INVERTER_DRIVE\"");
        assertThat(json).contains("\"instrumentNm\":\"송수펌프-001\"");
    }

    @Test
    void PumpDto_응답에_폐기된_tagNm_필드는_미포함한다() throws Exception {
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-002", null, 1, YnType.Y);
        Pump pump = Pump.create("송수펌프-002", pwtf, 2,
                RATED_HEAD, RATED_FLWRT, PumpOprtngType.SEMI_AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);
        setEquipType(pump, EquipType.PUMP);

        String json = objectMapper.writeValueAsString(PumpDto.from(pump));

        assertThat(json).doesNotContain("tagNm");
        assertThat(json).doesNotContain("tag_nm");
    }

    @Test
    void InstrumentDto_from_은_Pump_자식을_PumpDto_인스턴스로_반환한다() {
        PurifiedWaterTank pwtf = PurifiedWaterTank.create("정수지-003", null, 1, YnType.Y);
        Pump pump = Pump.create("송수펌프-003", pwtf, 3,
                RATED_HEAD, RATED_FLWRT, PumpOprtngType.AUTO_CAPABLE, PumpDriveType.INVERTER_DRIVE);
        setEquipType(pump, EquipType.PUMP);

        assertThat(InstrumentDto.from(pump)).isInstanceOf(PumpDto.class);
    }

    /**
     * {@code @DiscriminatorColumn(insertable=false, updatable=false)} 인 {@code equipType} 필드를
     * 단위 테스트용으로 reflection 주입한다. 영속 컨텍스트가 자동 관리하는 컬럼이라 단위 테스트에서는
     * {@code Pump.create()} 후 null 상태이므로 직렬화 시 discriminator 가 노출되지 않는다.
     */
    private static void setEquipType(Instrument instrument, EquipType type) {
        try {
            Field field = Instrument.class.getDeclaredField("equipType");
            field.setAccessible(true);
            field.set(instrument, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
