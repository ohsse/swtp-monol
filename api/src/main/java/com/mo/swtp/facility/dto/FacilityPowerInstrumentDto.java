package com.mo.swtp.facility.dto;

import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;

/**
 * 시설별 사용량 3번섹션 — 전력 계측기 목록 응답 DTO.
 *
 * <p>2번섹션에서 선택한 운영시설을 루트로 하는 재귀 하위 트리 전체(루트 inclusive)의 활성 계측기 중
 * 전력관련 태그(PWI 순시전력 ∪ PWQ 적산전력량)를 1건 이상 보유한 계측기를 평면 목록으로 노출한다.
 * 본 목록이 5/6/7 섹션(설비별 통계·비율·순시전력 차트)의 입력이 된다 (시설별사용량-3번섹션 PLAN1 §목적).</p>
 *
 * <p>계측기 1건당 소속 시설({@link #facilityId}·{@link #facilityNm}) 과 보유 전력태그 상세({@link #tags}) 를
 * 함께 담는다 — 같은 계측기가 PWI·PWQ 를 동시에 보유하면 {@link #tags} 에 2건이 노출된다. 정렬은 소속 시설
 * {@code disp_ord} → 계측기 {@code disp_ord} → 계측기명 순 (PLAN1 §구현 방향).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합, {@link FacilityEnergyUsageDto}·
 * {@link FacilityEnergyTrendDto} 선례 동형). outer + 중첩 {@link PowerTagDto} + 정적팩토리 패턴은
 * {@link FacilityEnergyTrendDto} 동형 미러링 (섹션3 전용 신규 자산 — 사이클 간 자산 자동 원용 금지 정합).</p>
 */
@Getter
@Schema(description = "시설별 사용량 3번섹션 — 전력관련 태그 보유 계측기")
public class FacilityPowerInstrumentDto {

    @Schema(description = "계측기 ID", example = "in-xxx-xxx")
    private String instrumentId;

    @Schema(description = "계측기명", example = "송수1호기")
    private String instrumentNm;

    @Schema(description = "장비 유형 코드 (PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR)",
            implementation = EquipType.class)
    private EquipType equipTypeCd;

    @Schema(description = "소속 시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "소속 시설명", example = "생활송수동")
    private String facilityNm;

    @ArraySchema(schema = @Schema(description = "보유 전력관련 태그 목록 (PWI 순시전력 ∪ PWQ 적산전력량, 1건 이상)",
            implementation = PowerTagDto.class))
    private List<PowerTagDto> tags;

    private FacilityPowerInstrumentDto() {
    }

    /**
     * 전력 계측기 응답 DTO 정적 팩토리.
     *
     * @param instrumentId 계측기 ID
     * @param instrumentNm 계측기명
     * @param equipTypeCd  장비 유형 코드
     * @param facilityId   소속 시설 ID
     * @param facilityNm   소속 시설명
     * @param tags         보유 전력관련 태그 목록 (1건 이상)
     * @return 구성된 응답 DTO
     */
    public static FacilityPowerInstrumentDto of(
            String instrumentId,
            String instrumentNm,
            EquipType equipTypeCd,
            String facilityId,
            String facilityNm,
            List<PowerTagDto> tags) {
        FacilityPowerInstrumentDto dto = new FacilityPowerInstrumentDto();
        dto.instrumentId = instrumentId;
        dto.instrumentNm = instrumentNm;
        dto.equipTypeCd = equipTypeCd;
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.tags = tags;
        return dto;
    }

    /**
     * 계측기가 보유한 전력관련 태그 단건 — 태그 시리얼번호 + 측정 유형.
     */
    @Getter
    @Schema(description = "전력관련 태그 (계측기 보유)")
    public static class PowerTagDto {

        @Schema(description = "태그 시리얼번호", example = "706-PWI-001-001")
        private String tagSrlNo;

        @Schema(description = "태그 측정 유형 코드 (PWI 순시전력 / PWQ 적산전력량)",
                implementation = TagMeasurementType.class)
        private TagMeasurementType tagSeCd;

        private PowerTagDto() {
        }

        /**
         * 전력관련 태그 정적 팩토리.
         *
         * @param tagSrlNo 태그 시리얼번호
         * @param tagSeCd  태그 측정 유형 (PWI / PWQ)
         * @return 구성된 태그 DTO
         */
        public static PowerTagDto of(String tagSrlNo, TagMeasurementType tagSeCd) {
            PowerTagDto dto = new PowerTagDto();
            dto.tagSrlNo = tagSrlNo;
            dto.tagSeCd = tagSeCd;
            return dto;
        }
    }
}
