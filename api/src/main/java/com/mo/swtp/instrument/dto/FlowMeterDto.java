package com.mo.swtp.instrument.dto;

import com.mo.swtp.instrument.domain.FlowMeter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 유량계 응답 DTO — {@link FlowMeter} 자식 ({@code equip_type_cd = 'FLWMTR'}).
 *
 * <p>자식 전용 필드 0건 (현 시점). 자식 전용 필드 도입 시 본 DTO 확장.</p>
 *
 * <p>계측기관리CRUD 사이클 (2026-05-12) 신설.</p>
 */
@Getter
@Schema(description = "유량계 응답 DTO")
public class FlowMeterDto extends InstrumentDto {

    private FlowMeterDto() {}

    /**
     * 유량계 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param flowMeter 유량계 엔티티
     * @return 부모 공통 필드만 포함하는 응답 DTO
     */
    public static FlowMeterDto from(FlowMeter flowMeter) {
        FlowMeterDto dto = new FlowMeterDto();
        dto.applyCommonFields(flowMeter);
        return dto;
    }
}
