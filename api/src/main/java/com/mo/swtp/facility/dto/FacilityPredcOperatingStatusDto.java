package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 시설 단위 예측 운영 현황 응답 DTO — 운전현황분석 9번 섹션 "예측 운영 현황" 카드.
 *
 * <p>활성 시설(useYn = Y) 의 PUMP 자식 인스트루먼트 중 OPS 예측이 On ({@code predc_val = 1.0}) 인
 * 펌프 이름 목록, 해당 펌프들의 PWI(순시전력) 예측값 합산, 시설 직속 FLWMTR 의 FRI(유출유량) 예측값과의
 * 예측 전력원단위, 사용된 모든 예측 태그 중 가장 늦은 {@code predc_dtm} 을 단건 응답으로 노출한다
 * (운전현황분석-9번섹션 PLAN1).</p>
 *
 * <p>4번 섹션 {@link FacilityOperatingStatusDto} 와 6필드 단층 구조 동형이나, 데이터 소스가
 * {@code predc_1m_h} (AI 예측 시계열) 로 변경된 예측값 버전이다. {@code predc_1m_h} 는
 * {@code quality_cd}·{@code corr_val} 컬럼 부재이므로 SCADA QUALITY 분기 + Hold Last Value 분기 모두
 * 미적용 ({@code ot-integration.md §3} OPS BAD 즉시 격상은 실측 전용).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 *
 * <p>지원 시설 종류: PWTF · DWT · PRSF — RSV / POINT 는 거부
 * ({@code FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS}, 400, 4번 사이클 enum 재사용).</p>
 */
@Getter
@Schema(description = "시설 단위 예측 운영 현황 — 운전현황분석 9번 섹션")
public class FacilityPredcOperatingStatusDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "정수지A")
    private String facilityNm;

    @Schema(description = "예측 시점 — 사용된 모든 예측 태그 중 max(predc_dtm). 데이터 부재 시 null",
            example = "2026-05-21 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime predcDtm;

    @ArraySchema(schema = @Schema(
            description = "예측 On 상태 펌프명 목록 (predc_val = 1.0). "
                    + "predc_val IS NULL (결측·신뢰도 불명) 과 predc_val == 0.0 (예측 Off 확신) 두 케이스 모두 본 목록에서 제외",
            example = "P#1"))
    private List<String> onPumpNms;

    @Schema(description = "예측 On 펌프들의 순시전력 예측값 합산 (kW). 예측 결측(predc_val IS NULL) 펌프는 합산 제외. 예측 On 펌프 0대 시 0.0",
            example = "80.0000")
    private BigDecimal totalElpwrAmt;

    @Schema(description = "예측 전력원단위 (kWh/m³) — totalElpwrAmt / FRI predcVal. "
            + "분자/분모 0·NULL·부재 시 null", example = "0.2000")
    private BigDecimal elpwrUnitQty;

    private FacilityPredcOperatingStatusDto() {
    }

    /**
     * 시설 예측 운영 현황 응답 DTO 정적 팩토리.
     *
     * @param facilityId     시설 ID
     * @param facilityNm     시설명
     * @param predcDtm       예측 시점 (max predc_dtm, 빈 데이터 시 null)
     * @param onPumpNms      예측 On 펌프명 목록 (빈 List 허용)
     * @param totalElpwrAmt  예측 On 펌프 PWI 예측 합산값 (kW, BigDecimal.ZERO 가능)
     * @param elpwrUnitQty   예측 전력원단위 (kWh/m³, 분모 무효 시 null)
     * @return 구성된 응답 DTO
     */
    public static FacilityPredcOperatingStatusDto of(
            String facilityId,
            String facilityNm,
            LocalDateTime predcDtm,
            List<String> onPumpNms,
            BigDecimal totalElpwrAmt,
            BigDecimal elpwrUnitQty) {
        FacilityPredcOperatingStatusDto dto = new FacilityPredcOperatingStatusDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.predcDtm = predcDtm;
        dto.onPumpNms = onPumpNms;
        dto.totalElpwrAmt = totalElpwrAmt;
        dto.elpwrUnitQty = elpwrUnitQty;
        return dto;
    }
}
