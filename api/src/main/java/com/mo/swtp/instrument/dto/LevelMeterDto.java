package com.mo.swtp.instrument.dto;

import com.mo.swtp.instrument.domain.LevelMeter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 수위계 응답 DTO — {@link LevelMeter} 자식 ({@code equip_type_cd = 'LVMTR'}).
 *
 * <p>자식 전용 필드 0건 (현 시점). 자식 전용 필드 도입 시 본 DTO 확장.</p>
 *
 * <p>계측기관리CRUD 사이클 (2026-05-12) 신설.</p>
 */
@Getter
@Schema(description = "수위계 응답 DTO")
public class LevelMeterDto extends InstrumentDto {

    private LevelMeterDto() {}

    /**
     * 수위계 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param levelMeter 수위계 엔티티
     * @return 부모 공통 필드만 포함하는 응답 DTO
     */
    public static LevelMeterDto from(LevelMeter levelMeter) {
        LevelMeterDto dto = new LevelMeterDto();
        dto.applyCommonFields(levelMeter);
        return dto;
    }
}
