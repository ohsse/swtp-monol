package com.mo.swtp.facility.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;

/**
 * 시설 단위 실시간 상태 응답 DTO — 송수펌프제어분석 3번 섹션.
 *
 * <p>활성 시설(시설물 목록 조회 응답의 hasPump=true 결과) 의 유량계·펌프 실시간 상태를 단건 endpoint
 * 응답으로 노출한다. 자식 DTO 는 oneOf 다형성을 사용하지 않으며, 단일 컨테이너 DTO 가 유량계/펌프
 * 자식 목록을 각각 List 로 보유한다 (송수펌프제어분석-3번섹션 PLAN1 §구현 방향 §Controller).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 실시간 통지성 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "시설 단위 실시간 상태 — 송수펌프제어분석 3번 섹션")
public class FacilityStateDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "정수지A")
    private String facilityNm;

    @ArraySchema(schema = @Schema(description = "유량계 상태 목록", implementation = FlwmtrStateDto.class))
    private List<FlwmtrStateDto> flwmtrs;

    @ArraySchema(schema = @Schema(description = "펌프 상태 목록", implementation = PumpStateDto.class))
    private List<PumpStateDto> pumps;

    private FacilityStateDto() {
    }

    /**
     * 시설 실시간 상태 응답 DTO 정적 팩토리.
     *
     * @param facilityId  시설 ID
     * @param facilityNm  시설명
     * @param flwmtrs     유량계 상태 목록 (빈 List 허용)
     * @param pumps       펌프 상태 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static FacilityStateDto of(
            String facilityId,
            String facilityNm,
            List<FlwmtrStateDto> flwmtrs,
            List<PumpStateDto> pumps) {
        FacilityStateDto dto = new FacilityStateDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.flwmtrs = flwmtrs;
        dto.pumps = pumps;
        return dto;
    }
}
