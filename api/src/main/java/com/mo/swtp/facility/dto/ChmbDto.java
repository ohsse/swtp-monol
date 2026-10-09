package com.mo.swtp.facility.dto;

import com.mo.swtp.facility.domain.ChemicalBuilding;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 약품동 응답 DTO — {@link ChemicalBuilding} 자식 ({@code facility_type_cd = 'CHMB'}).
 *
 * <p>자식 전용 필드 0건 (현 시점). Jackson 다형성 직렬화 시 {@code "facilityTypeCd":"CHMB"}
 * discriminator 값으로 본 자식 스키마가 결정된다. 자식 전용 필드 도입 시 본 DTO 확장.</p>
 *
 * <p>시설_도메인_확장 ANALYZE1 (2026-06-08) 도입.</p>
 */
@Getter
@Schema(description = "약품동 응답 DTO")
public class ChmbDto extends FacilityDto {

    private ChmbDto() {}

    /**
     * 약품동 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param chmb 약품동 엔티티
     * @return 부모 공통 필드만 포함하는 응답 DTO
     */
    public static ChmbDto from(ChemicalBuilding chmb) {
        ChmbDto dto = new ChmbDto();
        dto.applyCommonFields(chmb);
        return dto;
    }
}
