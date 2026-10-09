package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 송수펌프제어분석 §6 — 시설 1건의 토출관압 + 운전중 펌프 대수 요약 응답 DTO.
 *
 * <p>1번 섹션 시설목록(hasPump=true 활성 펌프 시설 전체)의 각 시설을 1 row 로 표현한다. 토출관압은
 * 시설 FLWMTR 의 PRI 최신값(첫 매치), 운전중 펌프 대수는 시설 PUMP 들의 OPS 가동상태 ON 대수다
 * (송수펌프제어분석-6번섹션 PLAN1 §응답 DTO).</p>
 *
 * <p>{@code prsr} 4필드는 §3 {@link FlwmtrStateDto} 선례 정합으로 평탄 구조다 (중첩 PrsrValueDto 는
 * 1회성 추상화로 미도입). 토출관압 PRI 최신값이 1시간 윈도우 내 없으면 4필드 모두 NULL.</p>
 *
 * <p>{@code unknownPumpCnt} 는 {@code ot-integration.md §3} OPS 즉시 BAD 격상 정책 정합을 위한
 * 도메인 안전 보강 필드다 — 운전원이 통신 단절 펌프를 운전중으로 오인하지 않도록 신뢰불가 대수를 별도
 * 노출한다 (사용자 승인 2026-05-18).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 실시간 통지성 응답 ({@code api-patterns.md §적용 범위}).</p>
 */
@Getter
@Schema(description = "송수펌프 시설별 토출관압 + 운전중 펌프 대수 요약")
public class PumpSummaryDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "성주정수장")
    private String facilityNm;

    @Schema(description = "토출관압 원본 측정값 (FLWMTR 첫 매치의 PRI, kgf/cm²)", example = "2.45")
    private BigDecimal prsrRawVal;

    @Schema(description = "토출관압 보정 측정값 (kgf/cm², NULL 허용)", example = "2.46")
    private BigDecimal prsrCorrVal;

    @Schema(description = "토출관압 수집 일시", example = "2026-05-18 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime prsrAcqDtm;

    @Schema(description = "토출관압 SCADA 품질 코드", implementation = QualityCode.class)
    private QualityCode prsrQualityCd;

    @Schema(description = "시설 FLWMTR/PRI 다중 등록 감지 여부 — true 면 첫 매치 값 사용 + 서버 WARN 로그",
            example = "false")
    private boolean multiplePrsrDetected;

    @Schema(description = "운전중 펌프 대수 (OPS qualityCd=GOOD AND rawVal=1.0)", example = "2")
    private Integer oprtngPumpCnt;

    @Schema(description = "신뢰불가 펌프 대수 (OPS qualityCd=BAD/UNCERTAIN·결측·판정불가 — 운전원 오인 방지)",
            example = "1")
    private Integer unknownPumpCnt;

    private PumpSummaryDto() {
    }

    /**
     * 시설 요약 응답 DTO 정적 팩토리.
     */
    public static PumpSummaryDto of(
            String facilityId,
            String facilityNm,
            BigDecimal prsrRawVal,
            BigDecimal prsrCorrVal,
            LocalDateTime prsrAcqDtm,
            QualityCode prsrQualityCd,
            boolean multiplePrsrDetected,
            Integer oprtngPumpCnt,
            Integer unknownPumpCnt) {
        PumpSummaryDto dto = new PumpSummaryDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.prsrRawVal = prsrRawVal;
        dto.prsrCorrVal = prsrCorrVal;
        dto.prsrAcqDtm = prsrAcqDtm;
        dto.prsrQualityCd = prsrQualityCd;
        dto.multiplePrsrDetected = multiplePrsrDetected;
        dto.oprtngPumpCnt = oprtngPumpCnt;
        dto.unknownPumpCnt = unknownPumpCnt;
        return dto;
    }
}
