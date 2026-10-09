package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.instrument.domain.PumpOprtngType;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 펌프(PUMP) 1대의 OPS 가동상태 최신값 + 정적 조작유형 응답 DTO.
 *
 * <p>{@code oprtngType} 은 펌프의 물리적 설계값 (AI 운전 모드와 무관) — 마스터 데이터로 정적이므로
 * 실시간 측정값과 별도로 노출한다 (송수펌프제어분석-3번섹션 ANALYZE1 안건 2).</p>
 *
 * <p>{@code isRunning} 은 OPS 태그의 {@code raw_val} (1.0=ON / 0.0=OFF) 을 Boolean 변환한 값으로,
 * BAD QUALITY 또는 1시간 윈도우 내 측정값 부재 시 NULL 로 반환된다. qualityCd 가 BAD/UNCERTAIN 인 경우도
 * raw 값은 그대로 응답 (ANALYZE1 안건 10 결정 — frontend 가 qualityCd 보고 표시 분기).</p>
 */
@Getter
@Schema(description = "펌프(PUMP) 실시간 상태 — OPS 가동상태 + 정적 조작유형")
public class PumpStateDto {

    @Schema(description = "계측기 ID", example = "in-xxx-xxx")
    private String instrumentId;

    @Schema(description = "계측기명", example = "송수펌프1")
    private String instrumentNm;

    @Schema(description = "펌프 물리 조작 가능 유형 (AI 운전 모드와 무관)", implementation = PumpOprtngType.class)
    private PumpOprtngType oprtngType;

    @Schema(description = "가동상태 (1.0=ON / 0.0=OFF 변환, BAD/결측 시 NULL)", example = "true")
    private Boolean isRunning;

    @Schema(description = "OPS 수집 일시 (결측 시 NULL)", example = "2026-05-13 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime acqDtm;

    @Schema(description = "OPS SCADA 품질 코드 (결측 시 NULL)", implementation = QualityCode.class)
    private QualityCode qualityCd;

    private PumpStateDto() {
    }

    /**
     * 펌프 상태 응답 DTO 정적 팩토리.
     */
    public static PumpStateDto of(
            String instrumentId,
            String instrumentNm,
            PumpOprtngType oprtngType,
            Boolean isRunning,
            LocalDateTime acqDtm,
            QualityCode qualityCd) {
        PumpStateDto dto = new PumpStateDto();
        dto.instrumentId = instrumentId;
        dto.instrumentNm = instrumentNm;
        dto.oprtngType = oprtngType;
        dto.isRunning = isRunning;
        dto.acqDtm = acqDtm;
        dto.qualityCd = qualityCd;
        return dto;
    }
}
