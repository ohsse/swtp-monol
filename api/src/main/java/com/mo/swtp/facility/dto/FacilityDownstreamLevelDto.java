package com.mo.swtp.facility.dto;

import com.mo.swtp.facility.domain.enumtype.FacilityDownstreamDataType;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;

/**
 * 수위 재귀 하위 시계열 응답 DTO — 운전현황분석 8번 섹션 ({@code dataType = LEVEL}).
 *
 * <p>표출 대상(재귀 하위 중 DWT 자식을 가진 PWTF·POINT) 의 자식 배수지(DWT) 들의 수위계(LVMTR) 마다 1 시리즈를
 * 구성한다. 각 시리즈는 그 수위계의 LEI(수위) 계측+예측 시계열이다. 수요량/관압이 표출대상 시설당 1 시리즈인 것과
 * 달리, 수위는 자식 배수지의 수위계 개수만큼 시리즈가 생성된다 (사용자 요청 명시). {@code dataType}
 * discriminator 값이 {@code LEVEL} 일 때 본 자식 스키마로 직렬화된다.</p>
 *
 * <p>자식 전용 필드({@code series}) 는 부모 {@link FacilityDownstreamTimeSeriesDto} 가 아닌 본 DTO 에만
 * 선언한다 ({@code api-patterns.md §상속 상한}).</p>
 */
@Getter
@Schema(description = "수위 재귀 하위 시계열 응답 (운전현황분석 8번 섹션, dataType=LEVEL)")
public class FacilityDownstreamLevelDto extends FacilityDownstreamTimeSeriesDto {

    @ArraySchema(schema = @Schema(
            description = "자식 배수지(DWT) 수위계(LVMTR) 별 LEI 계측+예측 시리즈 목록 (수위계당 1)",
            implementation = LevelSeries.class))
    private List<LevelSeries> series;

    private FacilityDownstreamLevelDto() {
    }

    /**
     * 수위 응답 DTO 정적 팩토리.
     *
     * @param facilityId 활성 루트 시설 ID
     * @param facilityNm 활성 루트 시설명
     * @param series     수위계별 시리즈 목록 (빈 List 허용 — 자식 DWT·수위계 0건)
     * @return 구성된 응답 DTO ({@code dataType} 은 {@link FacilityDownstreamDataType#LEVEL} 고정)
     */
    public static FacilityDownstreamLevelDto of(
            String facilityId,
            String facilityNm,
            List<LevelSeries> series) {
        FacilityDownstreamLevelDto dto = new FacilityDownstreamLevelDto();
        dto.applyRoot(facilityId, facilityNm, FacilityDownstreamDataType.LEVEL);
        dto.series = series;
        return dto;
    }

    /**
     * 수위계 1대의 LEI 계측+예측 시리즈 — 배수지 + 수위계 식별 + 상위 표출대상 그룹핑 + 1분 슬롯 목록.
     *
     * <p>{@code facilityId}·{@code facilityNm}·{@code facilityTypeCd} 는 수위계가 속한 배수지(DWT), {@code parentFacilityId}·
     * {@code parentFacilityNm} 은 그 배수지의 상위 표출대상(PWTF·POINT) — 프론트가 표출대상별로 수위 시리즈를
     * 묶을 수 있도록 동봉한다. LEI 태그가 없는 수위계도 빈 {@code points} 로 포함된다 (트랙 누락 방지).</p>
     */
    @Getter
    @Schema(description = "수위계 1대의 LEI 계측+예측 시리즈")
    public static class LevelSeries {

        @Schema(description = "배수지(DWT) ID", example = "fa-dwt-1")
        private String facilityId;

        @Schema(description = "배수지명", example = "1배수지")
        private String facilityNm;

        @Schema(description = "배수지 시설 유형 (DWT 고정)", implementation = FacilityType.class)
        private FacilityType facilityTypeCd;

        @Schema(description = "수위계(LVMTR) ID", example = "in-lv-1")
        private String instrumentId;

        @Schema(description = "수위계명", example = "1배수지 수위계")
        private String instrumentNm;

        @Schema(description = "상위 표출대상(PWTF·POINT) 시설 ID — 배수지의 직속 상위", example = "fa-pwtf-1")
        private String parentFacilityId;

        @Schema(description = "상위 표출대상 시설명", example = "1정수지")
        private String parentFacilityNm;

        @ArraySchema(schema = @Schema(
                description = "LEI(수위) 1분 슬롯 목록 (00:00 ~ 현재시간). 계측·예측 양쪽 부재 슬롯 생략",
                implementation = DownstreamPoint.class))
        private List<DownstreamPoint> points;

        private LevelSeries() {
        }

        /**
         * 수위 시리즈 정적 팩토리.
         *
         * @param facilityId       배수지(DWT) ID
         * @param facilityNm       배수지명
         * @param facilityTypeCd   배수지 시설 유형 (DWT)
         * @param instrumentId     수위계 ID
         * @param instrumentNm     수위계명
         * @param parentFacilityId 상위 표출대상 시설 ID
         * @param parentFacilityNm 상위 표출대상 시설명
         * @param points           LEI 시계열 슬롯 목록 (빈 List 허용)
         * @return 구성된 시리즈
         */
        public static LevelSeries of(
                String facilityId,
                String facilityNm,
                FacilityType facilityTypeCd,
                String instrumentId,
                String instrumentNm,
                String parentFacilityId,
                String parentFacilityNm,
                List<DownstreamPoint> points) {
            LevelSeries series = new LevelSeries();
            series.facilityId = facilityId;
            series.facilityNm = facilityNm;
            series.facilityTypeCd = facilityTypeCd;
            series.instrumentId = instrumentId;
            series.instrumentNm = instrumentNm;
            series.parentFacilityId = parentFacilityId;
            series.parentFacilityNm = parentFacilityNm;
            series.points = points;
            return series;
        }
    }
}
