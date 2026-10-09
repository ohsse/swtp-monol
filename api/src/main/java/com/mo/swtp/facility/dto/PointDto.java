package com.mo.swtp.facility.dto;

import com.mo.swtp.facility.domain.SensorPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 센서포인트 응답 DTO — {@link SensorPoint} 자식 ({@code facility_type_cd = 'POINT'}).
 *
 * <p>자식 전용 필드 0건 (현 시점). Jackson 다형성 직렬화 시 {@code "facilityTypeCd":"POINT"}
 * discriminator 값으로 본 자식 스키마가 결정된다. 자식 전용 필드 도입 시 본 DTO 확장.</p>
 *
 * <p>SCADA 자동 생성 시설로 수동 등록 API 대상 외 ({@link FacilityUpsertDto} {@code @JsonSubTypes} 에
 * POINT 부재). 단건 조회·목록 응답은 본 DTO 로 다형성 직렬화한다.</p>
 *
 * <p>시설물응답DTO명세 ANALYZE1·PLAN1 (2026-05-12) 도입.</p>
 */
@Getter
@Schema(description = "센서포인트 응답 DTO")
public class PointDto extends FacilityDto {

    private PointDto() {}

    /**
     * 센서포인트 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param point 센서포인트 엔티티
     * @return 부모 공통 필드만 포함하는 응답 DTO
     */
    public static PointDto from(SensorPoint point) {
        PointDto dto = new PointDto();
        dto.applyCommonFields(point);
        return dto;
    }
}
