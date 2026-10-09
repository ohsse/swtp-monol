package com.mo.swtp.instrument.dto;

import com.mo.swtp.instrument.domain.PowerMeter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 전력량계 응답 DTO — {@link PowerMeter} 자식 ({@code equip_type_cd = 'ELCMTR'}).
 *
 * <p>자식 전용 필드 0건 (현 시점). 자식 전용 필드 도입 시 본 DTO 확장.</p>
 *
 * <p>계측기관리CRUD 사이클 (2026-05-12) 신설.</p>
 */
@Getter
@Schema(description = "전력량계 응답 DTO")
public class PowerMeterDto extends InstrumentDto {

    private PowerMeterDto() {}

    /**
     * 전력량계 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param powerMeter 전력량계 엔티티
     * @return 부모 공통 필드만 포함하는 응답 DTO
     */
    public static PowerMeterDto from(PowerMeter powerMeter) {
        PowerMeterDto dto = new PowerMeterDto();
        dto.applyCommonFields(powerMeter);
        return dto;
    }
}
