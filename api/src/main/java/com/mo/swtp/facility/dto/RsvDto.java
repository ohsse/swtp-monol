package com.mo.swtp.facility.dto;

import com.mo.swtp.facility.domain.Reservoir;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 저수지 응답 DTO — {@link Reservoir} 자식 ({@code facility_type_cd = 'RSV'}).
 *
 * <p>자식 전용 필드 0건 (현 시점). Jackson 다형성 직렬화 시 {@code "facilityTypeCd":"RSV"}
 * discriminator 값으로 본 자식 스키마가 결정된다. 자식 전용 필드 도입 시 본 DTO 확장.</p>
 *
 * <p>시설물응답DTO명세 ANALYZE1·PLAN1 (2026-05-12) 도입.</p>
 */
@Getter
@Schema(description = "저수지 응답 DTO")
public class RsvDto extends FacilityDto {

    private RsvDto() {}

    /**
     * 저수지 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param rsv 저수지 엔티티
     * @return 부모 공통 필드만 포함하는 응답 DTO
     */
    public static RsvDto from(Reservoir rsv) {
        RsvDto dto = new RsvDto();
        dto.applyCommonFields(rsv);
        return dto;
    }
}
