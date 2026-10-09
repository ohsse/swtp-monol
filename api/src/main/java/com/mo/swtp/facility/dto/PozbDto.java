package com.mo.swtp.facility.dto;

import com.mo.swtp.facility.domain.PreOzonationBuilding;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 전오존동 응답 DTO — {@link PreOzonationBuilding} 자식 ({@code facility_type_cd = 'POZB'}).
 *
 * <p>자식 전용 필드 0건 (현 시점). Jackson 다형성 직렬화 시 {@code "facilityTypeCd":"POZB"}
 * discriminator 값으로 본 자식 스키마가 결정된다. 자식 전용 필드 도입 시 본 DTO 확장.</p>
 *
 * <p>시설_도메인_확장 ANALYZE1 (2026-06-08) 도입.</p>
 */
@Getter
@Schema(description = "전오존동 응답 DTO")
public class PozbDto extends FacilityDto {

    private PozbDto() {}

    /**
     * 전오존동 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param pozb 전오존동 엔티티
     * @return 부모 공통 필드만 포함하는 응답 DTO
     */
    public static PozbDto from(PreOzonationBuilding pozb) {
        PozbDto dto = new PozbDto();
        dto.applyCommonFields(pozb);
        return dto;
    }
}
