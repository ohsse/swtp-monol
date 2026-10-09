package com.mo.swtp.instrument.dto;

import com.mo.swtp.instrument.domain.Valve;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 밸브 응답 DTO — {@link Valve} 자식 ({@code equip_type_cd = 'VALVE'}).
 *
 * <p>자식 전용 필드 0건 (현 시점). Jackson 다형성 직렬화 시 {@code "equipTypeCd":"VALVE"}
 * discriminator 값으로 본 자식 스키마가 결정된다. 자식 전용 필드 도입 시 본 DTO 확장.</p>
 *
 * <p>계측기관리CRUD 사이클 (2026-05-12) 신설 — 시설물응답DTO명세 ANALYZE1 (2026-05-12) 안건 6
 * "후속 사이클 이연" 후속.</p>
 */
@Getter
@Schema(description = "밸브 응답 DTO")
public class ValveDto extends InstrumentDto {

    private ValveDto() {}

    /**
     * 밸브 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param valve 밸브 엔티티
     * @return 부모 공통 필드만 포함하는 응답 DTO
     */
    public static ValveDto from(Valve valve) {
        ValveDto dto = new ValveDto();
        dto.applyCommonFields(valve);
        return dto;
    }
}
