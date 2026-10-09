package com.mo.swtp.facility.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;

/**
 * 시설 단위 AI 예측 데이터 응답 DTO — 송수펌프제어분석 7번 섹션.
 *
 * <p>1번 섹션에서 선택한 활성 시설의 유량계·펌프 AI 예측 데이터를 단건 endpoint 응답으로 노출한다.
 * 섹션 3 ({@link FacilityStateDto}) 의 단일 컨테이너 DTO 패턴 정합 — 자식 DTO 는 oneOf 다형성을 사용하지
 * 않으며, 단일 컨테이너 DTO 가 유량계/펌프 자식 목록을 각각 List 로 보유한다.</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 예측 통지성 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "시설 단위 AI 예측 데이터 — 송수펌프제어분석 7번 섹션")
public class FacilityPredictionDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "정수지A")
    private String facilityNm;

    @ArraySchema(schema = @Schema(description = "유량계 예측 목록", implementation = FlwmtrPredictionDto.class))
    private List<FlwmtrPredictionDto> flwmtrs;

    @ArraySchema(schema = @Schema(description = "펌프 예측 목록", implementation = PumpPredictionDto.class))
    private List<PumpPredictionDto> pumps;

    private FacilityPredictionDto() {
    }

    /**
     * 시설 예측 응답 DTO 정적 팩토리.
     *
     * @param facilityId  시설 ID
     * @param facilityNm  시설명
     * @param flwmtrs     유량계 예측 목록 (빈 List 허용)
     * @param pumps       펌프 예측 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static FacilityPredictionDto of(
            String facilityId,
            String facilityNm,
            List<FlwmtrPredictionDto> flwmtrs,
            List<PumpPredictionDto> pumps) {
        FacilityPredictionDto dto = new FacilityPredictionDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.flwmtrs = flwmtrs;
        dto.pumps = pumps;
        return dto;
    }
}
