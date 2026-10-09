package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 시설 단위 운영 현황 응답 DTO — 운전현황분석 4번 섹션 "운영 현황" 카드.
 *
 * <p>활성 시설(useYn = Y) 의 PUMP 자식 인스트루먼트 중 OPS 가 On(GOOD + 1.0) 인 펌프 이름 목록,
 * 해당 펌프들의 PWI(순시전력) 합산값, 시설 자식 SensorPoint 의 FRI(유출유량) 와의 전력원단위,
 * 사용된 모든 태그 중 가장 늦은 acq_dtm 을 단건 응답으로 노출한다 (운전현황분석-4번섹션 PLAN1).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 *
 * <p>지원 시설 종류: PWTF · DWT · PRSF — RSV / POINT 는 거부
 * ({@code FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS}, 400).</p>
 */
@Getter
@Schema(description = "시설 단위 운영 현황 — 운전현황분석 4번 섹션")
public class FacilityOperatingStatusDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "정수지A")
    private String facilityNm;

    @Schema(description = "측정 시각 — 사용된 모든 태그 중 max(acq_dtm). 데이터 부재 시 null",
            example = "2026-05-21 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime measurementDtm;

    @ArraySchema(schema = @Schema(description = "On 상태 펌프명 목록 (OPS=GOOD AND effectiveVal=1.0)",
            example = "P#1"))
    private List<String> onPumpNms;

    @Schema(description = "On 펌프들의 순시전력 합산값 (PWI GOOD 만 합산, kW). On 펌프 0대 시 0.0",
            example = "80.0000")
    private BigDecimal totalElpwrAmt;

    @Schema(description = "전력원단위 (kWh/m³) — totalElpwrAmt / FRI corrVal. "
            + "분자/분모 0·NULL·BAD·부재 시 null", example = "0.2000")
    private BigDecimal elpwrUnitQty;

    private FacilityOperatingStatusDto() {
    }

    /**
     * 시설 운영 현황 응답 DTO 정적 팩토리.
     *
     * @param facilityId      시설 ID
     * @param facilityNm      시설명
     * @param measurementDtm  측정 시각 (max acq_dtm, 빈 데이터 시 null)
     * @param onPumpNms       On 펌프명 목록 (빈 List 허용)
     * @param totalElpwrAmt   On 펌프 PWI 합산값 (kW, BigDecimal.ZERO 가능)
     * @param elpwrUnitQty    전력원단위 (kWh/m³, 분모 무효 시 null)
     * @return 구성된 응답 DTO
     */
    public static FacilityOperatingStatusDto of(
            String facilityId,
            String facilityNm,
            LocalDateTime measurementDtm,
            List<String> onPumpNms,
            BigDecimal totalElpwrAmt,
            BigDecimal elpwrUnitQty) {
        FacilityOperatingStatusDto dto = new FacilityOperatingStatusDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.measurementDtm = measurementDtm;
        dto.onPumpNms = onPumpNms;
        dto.totalElpwrAmt = totalElpwrAmt;
        dto.elpwrUnitQty = elpwrUnitQty;
        return dto;
    }
}
