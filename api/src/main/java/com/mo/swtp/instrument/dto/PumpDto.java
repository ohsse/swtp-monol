package com.mo.swtp.instrument.dto;

import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.instrument.domain.PumpOprtngType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 송수펌프 응답 DTO — {@link Pump} 자식 ({@code equip_type_cd = 'PUMP'}).
 *
 * <p>자식 전용 필드 4건 ({@code ratedHead}·{@code ratedFlwrt}·{@code oprtngType}·{@code driveType}) 을 노출한다.
 * Jackson 다형성 직렬화 시 {@code "equipTypeCd":"PUMP"} discriminator 값으로 본 자식 스키마가 결정된다.</p>
 *
 * <p>{@code tagNm} 필드는 시설물응답DTO명세 ANALYZE1 (2026-05-12) 안건 8·9 결정으로 폐기된 양방향 중복 컬럼이라
 * 응답 DTO 에도 부재한다 ({@code tag_m.instrument_id} FK SSOT — {@code entity-patterns.md §FK 보유 측 SSOT
 * — 역방향 중복 컬럼 금지}).</p>
 *
 * <p>시설물응답DTO명세 ANALYZE1·PLAN1 (2026-05-12) 도입.</p>
 */
@Getter
@Schema(description = "송수펌프 응답 DTO")
public class PumpDto extends InstrumentDto {

    @Schema(description = "정격 양정 (m)", example = "65.0")
    private BigDecimal ratedHead;

    @Schema(description = "정격 유량 (m³/h)", example = "250.0")
    private BigDecimal ratedFlwrt;

    @Schema(description = "펌프 조작유형 (AUTO_CAPABLE/SEMI_AUTO_CAPABLE)", implementation = PumpOprtngType.class)
    private PumpOprtngType oprtngType;

    @Schema(description = "펌프 구동 방식 (INVERTER_DRIVE/RATED_DRIVE — 펌프 물리적 설계값)",
            implementation = PumpDriveType.class)
    private PumpDriveType driveType;

    private PumpDto() {}

    /**
     * 송수펌프 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param pump 송수펌프 엔티티
     * @return 부모 공통 필드 + 자식 전용 4 필드 ({@code ratedHead}·{@code ratedFlwrt}·{@code oprtngType}·{@code driveType}) 를 포함하는 응답 DTO
     */
    public static PumpDto from(Pump pump) {
        PumpDto dto = new PumpDto();
        dto.applyCommonFields(pump);
        dto.ratedHead = pump.getRatedHead();
        dto.ratedFlwrt = pump.getRatedFlwrt();
        dto.oprtngType = pump.getOprtngType();
        dto.driveType = pump.getDriveType();
        return dto;
    }
}
