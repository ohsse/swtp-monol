package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mo.swtp.facility.domain.enumtype.FacilityDownstreamDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 재귀 하위 시설 시계열 응답 추상 부모 DTO — 운전현황분석 8번 섹션의 다형성 진입점.
 *
 * <p>12번 섹션 활성 시설(루트) 1개를 상위로 바라보고, 그 재귀 하위 중 배수지(DWT) 를 자식으로 가진
 * 정수지(PWTF)·분기점(POINT) 을 표출 대상으로 삼아 금일({@code 00:00 ~ 현재시간}) 계측+예측 시계열을 응답한다.
 * 단일 엔드포인트가 {@code dataType} query parameter (수요량/관압/수위 탭) 로 분기하며, 응답 측은
 * {@code dataType} discriminator 로 자식 DTO 2종으로 직렬화된다:</p>
 * <ul>
 *   <li>{@link FacilityDownstreamMeasureDto} — {@code DEMAND}(수요량)·{@code PRESSURE}(관압). 표출대상 시설당
 *       1 시리즈 (유출 FLWMTR 의 FRI·PRI).</li>
 *   <li>{@link FacilityDownstreamLevelDto} — {@code LEVEL}(수위). 자식 DWT 의 수위계(LVMTR) 당 1 시리즈 (LEI).</li>
 * </ul>
 *
 * <p>{@link JsonTypeInfo} + {@link JsonSubTypes} 의 {@code EXISTING_PROPERTY} 다형성 — {@code dataType} 필드값으로
 * 자식 스키마가 결정된다 ({@link FacilityDto} 응답 다형성 패턴 정합). {@code DEMAND}·{@code PRESSURE} 두
 * discriminator 값이 동일 자식({@link FacilityDownstreamMeasureDto}) 으로 매핑되므로
 * {@code @JsonSubTypes.Type(names = {...})} 복수 매핑을 사용한다.</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류 ({@code api-patterns.md §BaseAuditResponseDto
 * 적용 범위} 정합). 본 추상 부모는 루트 시설 식별({@code facilityId}·{@code facilityNm}) 과 {@code dataType}
 * 만 보유하며, 시리즈 목록은 각 자식 DTO 가 종류별로 선언한다 (자식 전용 필드 부모 노출 금지 —
 * {@code api-patterns.md §상속 상한}).</p>
 */
@Getter
@Schema(
        description = "재귀 하위 시설 시계열 응답 — dataType 값에 따라 자식 스키마 결정 (운전현황분석 8번 섹션)",
        oneOf = {FacilityDownstreamMeasureDto.class, FacilityDownstreamLevelDto.class},
        discriminatorProperty = "dataType"
)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "dataType",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = FacilityDownstreamMeasureDto.class, names = {"DEMAND", "PRESSURE"}),
        @JsonSubTypes.Type(value = FacilityDownstreamLevelDto.class, name = "LEVEL")
})
public abstract class FacilityDownstreamTimeSeriesDto {

    @Schema(description = "활성 루트 시설 ID (12번 섹션 활성 시설)", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "활성 루트 시설명", example = "고령정수장")
    private String facilityNm;

    @Schema(description = "표출 데이터 종류 (DEMAND/PRESSURE/LEVEL — discriminator)",
            implementation = FacilityDownstreamDataType.class)
    private FacilityDownstreamDataType dataType;

    /**
     * 루트 시설 식별 + {@code dataType} 을 자식 DTO 에 일괄 주입한다.
     *
     * <p>자식 DTO 의 정적 팩토리 ({@code of(...)}) 내부에서 호출한다 — 자식이 부모 필드를 setter 호출이나
     * 생성자 인자로 직접 받는 방식 금지 ({@link FacilityDto#applyCommonFields} 동일 패턴).</p>
     *
     * @param facilityId 활성 루트 시설 ID
     * @param facilityNm 활성 루트 시설명
     * @param dataType   표출 데이터 종류
     */
    protected void applyRoot(String facilityId, String facilityNm, FacilityDownstreamDataType dataType) {
        this.facilityId = facilityId;
        this.facilityNm = facilityNm;
        this.dataType = dataType;
    }
}
