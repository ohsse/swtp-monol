package com.mo.swtp.facility.dto;

import com.mo.swtp.facility.domain.PurifiedWaterTank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 정수지 응답 DTO — {@link PurifiedWaterTank} 자식 ({@code facility_type_cd = 'PWTF'}).
 *
 * <p>자식 전용 필드 0건 (현 시점). Jackson 다형성 직렬화 시 {@code "facilityTypeCd":"PWTF"}
 * discriminator 값으로 본 자식 스키마가 결정된다. 자식 전용 필드 도입 시 본 DTO 확장.</p>
 *
 * <p>시설물응답DTO명세 ANALYZE1·PLAN1 (2026-05-12) 도입.</p>
 */
@Getter
@Schema(description = "정수지 응답 DTO")
public class PwtfDto extends FacilityDto {

    private PwtfDto() {}

    /**
     * 정수지 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param pwtf 정수지 엔티티
     * @return 부모 공통 필드만 포함하는 응답 DTO
     */
    public static PwtfDto from(PurifiedWaterTank pwtf) {
        PwtfDto dto = new PwtfDto();
        dto.applyCommonFields(pwtf);
        return dto;
    }
}
