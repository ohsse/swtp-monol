package com.mo.swtp.instrument.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 송수펌프 가동이력 2번섹션 — 펌프 상태 카드 응답 DTO (읽기 전용).
 *
 * <p>전체 활성 송수펌프의 현재 상태를 카드 단위로 표출한다. 펌프 제원값({@code ratedHead}·{@code ratedFlwrt})은
 * {@link Pump} 엔티티 고정값이며, 가동률({@code oprtngRate})은 구동 방식별로 다르게 산정된다
 * (송수펌프가동이력_2번섹션 PLAN1 §구현 방향).</p>
 *
 * <p><strong>가동률 산정</strong> ({@link com.mo.swtp.instrument.service.PumpOperationRateService}):</p>
 * <ul>
 *   <li>{@code RATED_DRIVE}: OPS GOOD + rawVal 1.0 → 100, 0.0 → 0, 그 외 → null
 *       (OPS Hold Last Value 금지 — {@code .claude/rules/ot-integration.md §3}, corr_val 미사용)</li>
 *   <li>{@code INVERTER_DRIVE}: FQI GOOD → 현재 주파수값(Hz)을 그대로 % (정격주파수 정규화 미적용), 그 외 → null</li>
 * </ul>
 *
 * <p>읽기 전용 카드 응답이므로 {@code BaseAuditResponseDto} 를 상속하지 않는다
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 패턴 §적용 범위} — 요약·통지 응답 미적용 정합).</p>
 */
@Getter
@Schema(description = "송수펌프 상태 카드 응답 DTO")
public class PumpOperationRateDto {

    @Schema(description = "펌프 ID (instrument_id)", example = "I-PUMP-001")
    private String pumpId;

    @Schema(description = "펌프명", example = "송수1호기")
    private String pumpNm;

    @Schema(description = "펌프 구동 방식 (INVERTER_DRIVE/RATED_DRIVE)", implementation = PumpDriveType.class)
    private PumpDriveType driveType;

    @Schema(description = "정격 양정 (m)", example = "65.0")
    private BigDecimal ratedHead;

    @Schema(description = "정격 유량 (m³/h)", example = "250.0")
    private BigDecimal ratedFlwrt;

    @Schema(description = "가동률 (%) — 정격: OPS On→100/Off→0, 인버터: 현재 주파수값(Hz)을 % 로. "
            + "판정 태그 부재·SCADA 품질 불량·미정의 가동상태 시 null", example = "45.0")
    private BigDecimal oprtngRate;

    @Schema(description = "가동률 판정 태그(정격=OPS, 인버터=FQI)의 SCADA 품질 코드. 판정 태그 부재 시 null",
            implementation = QualityCode.class)
    private QualityCode qualityCd;

    @Schema(description = "가동률 판정 태그의 수집 일시. 판정 태그 부재 시 null", example = "2026-06-02 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime acqDtm;

    private PumpOperationRateDto() {}

    /**
     * 펌프 엔티티 + 산정된 가동률 + 판정 태그 품질·수집시각으로 응답 DTO 를 생성한다.
     *
     * @param pump        송수펌프 엔티티 (제원값 SSOT)
     * @param oprtngRate  산정된 가동률 % (결측·품질 불량 시 null)
     * @param qualityCd   가동률 판정 태그의 SCADA 품질 코드 (판정 태그 부재 시 null)
     * @param acqDtm      가동률 판정 태그의 수집 일시 (판정 태그 부재 시 null)
     * @return 펌프 상태 카드 응답 DTO
     */
    public static PumpOperationRateDto of(
            Pump pump, BigDecimal oprtngRate, QualityCode qualityCd, LocalDateTime acqDtm) {
        PumpOperationRateDto dto = new PumpOperationRateDto();
        dto.pumpId = pump.getInstrumentId();
        dto.pumpNm = pump.getInstrumentNm();
        dto.driveType = pump.getDriveType();
        dto.ratedHead = pump.getRatedHead();
        dto.ratedFlwrt = pump.getRatedFlwrt();
        dto.oprtngRate = oprtngRate;
        dto.qualityCd = qualityCd;
        dto.acqDtm = acqDtm;
        return dto;
    }
}
