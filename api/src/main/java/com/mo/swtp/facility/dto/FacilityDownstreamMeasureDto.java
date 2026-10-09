package com.mo.swtp.facility.dto;

import com.mo.swtp.facility.domain.enumtype.FacilityDownstreamDataType;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;

/**
 * 수요량/관압 재귀 하위 시계열 응답 DTO — 운전현황분석 8번 섹션 ({@code dataType = DEMAND | PRESSURE}).
 *
 * <p>표출 대상(재귀 하위 중 DWT 자식을 가진 PWTF·POINT) 시설마다 1 시리즈를 구성한다. 각 시리즈는 그 시설의
 * 유출(송수) FLWMTR({@code io_cd ∈ {OUTPUT, BIDIR}} 첫 매치 고정) 의 FRI(수요량) 또는 PRI(관압) 계측+예측
 * 시계열이다. {@code dataType} discriminator 값이 {@code DEMAND} 또는 {@code PRESSURE} 일 때 본 자식 스키마로
 * 직렬화된다 (두 값 모두 동일 구조 — 유량계 유출값 기준, 사용자 Q5 확정 2026-06-01).</p>
 *
 * <p>자식 전용 필드({@code series}) 는 부모 {@link FacilityDownstreamTimeSeriesDto} 가 아닌 본 DTO 에만
 * 선언한다 ({@code api-patterns.md §상속 상한}).</p>
 */
@Getter
@Schema(description = "수요량/관압 재귀 하위 시계열 응답 (운전현황분석 8번 섹션, dataType=DEMAND|PRESSURE)")
public class FacilityDownstreamMeasureDto extends FacilityDownstreamTimeSeriesDto {

    @ArraySchema(schema = @Schema(
            description = "표출대상(PWTF·POINT) 시설별 유출 FRI/PRI 계측+예측 시리즈 목록",
            implementation = MeasureSeries.class))
    private List<MeasureSeries> series;

    private FacilityDownstreamMeasureDto() {
    }

    /**
     * 수요량/관압 응답 DTO 정적 팩토리.
     *
     * @param facilityId 활성 루트 시설 ID
     * @param facilityNm 활성 루트 시설명
     * @param dataType   표출 데이터 종류 ({@code DEMAND} 또는 {@code PRESSURE})
     * @param series     표출대상 시설별 시리즈 목록 (빈 List 허용 — 표출대상 0건)
     * @return 구성된 응답 DTO
     */
    public static FacilityDownstreamMeasureDto of(
            String facilityId,
            String facilityNm,
            FacilityDownstreamDataType dataType,
            List<MeasureSeries> series) {
        FacilityDownstreamMeasureDto dto = new FacilityDownstreamMeasureDto();
        dto.applyRoot(facilityId, facilityNm, dataType);
        dto.series = series;
        return dto;
    }

    /**
     * 표출 대상 1 시설의 유출 FRI/PRI 계측+예측 시리즈 — 시설 + 유출 FLWMTR 식별 + 1분 슬롯 목록.
     *
     * <p>표출 대상은 PWTF(정수지) 또는 POINT(분기점) 이다. 유출 FLWMTR 가 2건 이상이면 {@code disp_ord} 첫 매치를
     * 사용하고 {@code multipleOutletFlwmtrDetected = true} 로 신호한다 (4번 섹션 {@code multipleOutFlwmtrDetected}
     * 동일 안전망). 유출 FLWMTR 또는 해당 측정 태그가 없으면 {@code instrumentId}·{@code instrumentNm} 은 NULL,
     * {@code points} 는 빈 목록이다.</p>
     */
    @Getter
    @Schema(description = "표출대상 시설 1건의 유출 FRI/PRI 계측+예측 시리즈")
    public static class MeasureSeries {

        @Schema(description = "표출대상 시설 ID (PWTF 또는 POINT)", example = "fa-pwtf-1")
        private String facilityId;

        @Schema(description = "표출대상 시설명", example = "1정수지")
        private String facilityNm;

        @Schema(description = "표출대상 시설 유형 (PWTF 또는 POINT)", implementation = FacilityType.class)
        private FacilityType facilityTypeCd;

        @Schema(description = "유출(송수) 유량계 ID (io_cd ∈ {OUTPUT, BIDIR} 첫 매치). 유출 FLWMTR 부재 시 NULL",
                example = "in-fm-out-1")
        private String instrumentId;

        @Schema(description = "유출(송수) 유량계명. 유출 FLWMTR 부재 시 NULL", example = "송수유량계1")
        private String instrumentNm;

        @Schema(description = "유출 유량계 다중 등록 감지 — 2건 이상 시 true (첫 매치 사용)", example = "false")
        private boolean multipleOutletFlwmtrDetected;

        @ArraySchema(schema = @Schema(
                description = "FRI(수요량) 또는 PRI(관압) 1분 슬롯 목록 (00:00 ~ 현재시간). 계측·예측 양쪽 부재 슬롯 생략",
                implementation = DownstreamPoint.class))
        private List<DownstreamPoint> points;

        private MeasureSeries() {
        }

        /**
         * 수요량/관압 시리즈 정적 팩토리.
         *
         * @param facilityId                  표출대상 시설 ID
         * @param facilityNm                  표출대상 시설명
         * @param facilityTypeCd              표출대상 시설 유형 (PWTF/POINT)
         * @param instrumentId                유출 유량계 ID (NULL 허용)
         * @param instrumentNm                유출 유량계명 (NULL 허용)
         * @param multipleOutletFlwmtrDetected 유출 유량계 다중 등록 여부
         * @param points                      FRI/PRI 시계열 슬롯 목록 (빈 List 허용)
         * @return 구성된 시리즈
         */
        public static MeasureSeries of(
                String facilityId,
                String facilityNm,
                FacilityType facilityTypeCd,
                String instrumentId,
                String instrumentNm,
                boolean multipleOutletFlwmtrDetected,
                List<DownstreamPoint> points) {
            MeasureSeries series = new MeasureSeries();
            series.facilityId = facilityId;
            series.facilityNm = facilityNm;
            series.facilityTypeCd = facilityTypeCd;
            series.instrumentId = instrumentId;
            series.instrumentNm = instrumentNm;
            series.multipleOutletFlwmtrDetected = multipleOutletFlwmtrDetected;
            series.points = points;
            return series;
        }
    }
}
