package com.mo.swtp.instrument.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 송수펌프 가동이력 4번섹션 — 펌프별 가동상태 타임라인 응답 DTO (읽기 전용).
 *
 * <p>조회기간 동안 각 펌프의 가동상태(OPS) 시계열을 가동(ON) 구간 세그먼트로 압축한 결과를 펌프별 계열로 표출한다.
 * 프론트(Chart.js floating bar)는 각 세그먼트를 {@code { x: [startDtm, endDtm], y: pumpNm }} 막대 포인트로
 * 변환한다. 백엔드는 차트 라이브러리 비종속 세그먼트 응답만 제공한다 (송수펌프가동이력_4번섹션 PLAN1 §구현 방향 5).</p>
 *
 * <p>가동(ON) 구간만 세그먼트로 생성한다 — OFF·BAD·UNCERTAIN·결측 구간은 세그먼트 미생성(화면상 빈 공간).
 * OPS 는 Hold Last Value 미적용으로 통신단절(BAD) 구간을 ON 으로 이어붙이지 않는다
 * ({@code .claude/rules/ot-integration.md §3}). OPS 태그가 없는 펌프는 {@code segments} 빈 배열.</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).
 * {@link PumpPowerTimeSeriesDto} outer+inner+정적팩토리 패턴 인용.</p>
 */
@Getter
@Schema(description = "펌프별 가동상태 타임라인 응답 DTO — 송수펌프 가동이력 4번섹션")
public class PumpOperationHistoryDto {

    @Schema(description = "펌프 ID (instrument_id)", example = "I-PUMP-001")
    private String pumpId;

    @Schema(description = "펌프명", example = "송수1호기")
    private String pumpNm;

    @ArraySchema(schema = @Schema(description = "가동(ON) 구간 세그먼트 목록 (가동 구간이 없으면 빈 배열)",
            implementation = OperationSegment.class))
    private List<OperationSegment> segments;

    private PumpOperationHistoryDto() {
    }

    /**
     * 펌프별 가동상태 타임라인 응답 DTO 정적 팩토리.
     *
     * @param pumpId   펌프 ID (instrument_id)
     * @param pumpNm   펌프명
     * @param segments 가동(ON) 구간 세그먼트 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static PumpOperationHistoryDto of(String pumpId, String pumpNm, List<OperationSegment> segments) {
        PumpOperationHistoryDto dto = new PumpOperationHistoryDto();
        dto.pumpId = pumpId;
        dto.pumpNm = pumpNm;
        dto.segments = segments;
        return dto;
    }

    /**
     * 가동(ON) 구간 단일 세그먼트 — 가동 시작 일시 ~ 종료 일시.
     *
     * <p>{@code endDtm} 은 마지막 ON 수집 시각 + 1분(수집 주기) 으로, 가동 상태가 유지된 우상한 경계를 표현한다.</p>
     */
    @Getter
    @Schema(description = "가동(ON) 구간 단일 세그먼트")
    public static class OperationSegment {

        @Schema(description = "가동 시작 일시 (구간 첫 ON 수집 시각)", example = "2024-07-01 08:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startDtm;

        @Schema(description = "가동 종료 일시 (구간 마지막 ON 수집 시각 + 1분 수집 주기)", example = "2024-07-01 12:30:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime endDtm;

        private OperationSegment() {
        }

        /**
         * 가동 구간 세그먼트 정적 팩토리.
         *
         * @param startDtm 가동 시작 일시
         * @param endDtm   가동 종료 일시 (마지막 ON 시각 + 1분)
         * @return 구성된 세그먼트 DTO
         */
        public static OperationSegment of(LocalDateTime startDtm, LocalDateTime endDtm) {
            OperationSegment segment = new OperationSegment();
            segment.startDtm = startDtm;
            segment.endDtm = endDtm;
            return segment;
        }
    }
}
