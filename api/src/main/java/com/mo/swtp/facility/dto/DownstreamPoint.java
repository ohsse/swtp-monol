package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 재귀 하위 시설 시계열의 1분 단일 슬롯 — 계측·예측·대비율 — 운전현황분석 8번 섹션.
 *
 * <p>수요량(FRI)·관압(PRI)·수위(LEI) 세 종류가 모두 동일한 슬롯 구조를 공유한다. 계측값({@code actualVal})·
 * 예측값({@code predcVal}) 과 둘의 대비율({@code ratio} = {@code predcVal / actualVal × 100}) 을 담는다.</p>
 *
 * <ul>
 *   <li>{@code actualVal} — 계측값 (GOOD 측정값만; BAD/UNCERTAIN·미도래·결측 시 NULL). {@code corr_val} 우선,
 *       NULL 시 {@code raw_val}.</li>
 *   <li>{@code predcVal} — 예측값 ({@code predc_1m_h.predc_val} 직접; QUALITY 개념 없음, 예측 미수행 슬롯 NULL).</li>
 *   <li>{@code ratio} — 대비율(%). 소수 1자리(HALF_UP). 계측값이 NULL·0 이거나 예측값이 NULL 이면 NULL
 *       (0 나누기·무의미 비율 방어).</li>
 * </ul>
 *
 * <p>계측·예측 양쪽 NULL 인 슬롯은 응답에서 생략된다 (7·10번 섹션 정합). {@code BaseAuditResponseDto} 미상속 —
 * 단순 조회 응답 분류 ({@code api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "재귀 하위 시설 시계열 1분 슬롯 — 계측·예측·대비율 (운전현황분석 8번 섹션)")
public class DownstreamPoint {

    @Schema(description = "1분 슬롯 시각 (계측·예측 공통 키)", example = "2026-06-01 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dtm;

    @Schema(description = "계측값 — GOOD 측정값 (corr_val 우선). 미도래·결측·BAD/UNCERTAIN 시 NULL",
            example = "400.0000")
    private BigDecimal actualVal;

    @Schema(description = "예측값 — predc_1m_h.predc_val. 예측 미수행 슬롯 NULL", example = "390.0000")
    private BigDecimal predcVal;

    @Schema(description = "대비율(%) = 예측값 / 계측값 × 100, 소수 1자리. 계측값 NULL·0 또는 예측값 NULL 시 NULL",
            example = "97.5")
    private BigDecimal ratio;

    private DownstreamPoint() {
    }

    /**
     * 시계열 슬롯 정적 팩토리.
     *
     * @param dtm        1분 슬롯 시각
     * @param actualVal  계측값 (NULL 허용)
     * @param predcVal   예측값 (NULL 허용)
     * @param ratio      대비율 (NULL 허용 — 0 나누기·무의미 비율 방어)
     * @return 구성된 슬롯
     */
    public static DownstreamPoint of(
            LocalDateTime dtm,
            BigDecimal actualVal,
            BigDecimal predcVal,
            BigDecimal ratio) {
        DownstreamPoint point = new DownstreamPoint();
        point.dtm = dtm;
        point.actualVal = actualVal;
        point.predcVal = predcVal;
        point.ratio = ratio;
        return point;
    }
}
