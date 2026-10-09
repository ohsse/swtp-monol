package com.mo.swtp.facility.dto;

import com.mo.swtp.instrument.domain.enumtype.EquipType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;

/**
 * 설비별 사용량 5·6번섹션 — 설비별 누적 전력량·분포율 응답 DTO (읽기 전용).
 *
 * <p>2번섹션에서 선택한 시설을 루트로 하는 재귀 하위 트리 전체(루트 inclusive)의 활성 계측기 중 PWQ(적산전력량)
 * 태그 보유 계측기 각각의 조회기간 누적 전력량(kWh, 5번섹션)과 분포율(%, 6번섹션)을 평면 목록으로 노출한다.
 * 래퍼에 전체 합계({@link #totalElceg})를 함께 담아 분포율 분모를 명시한다 (설비별사용량-5,6번섹션 PLAN1
 * §구현 방향 3).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합, {@link FacilityEnergyUsageDto}·
 * {@link FacilityPowerInstrumentDto} 선례 동형). outer + 중첩 {@link InstrumentEnergyUsageItem} + 정적 팩토리
 * 패턴은 {@link FacilityPowerInstrumentDto} 동형 미러링 (섹션5·6 전용 신규 자산 — 사이클 간 자산 자동 원용
 * 금지 정합).</p>
 */
@Getter
@Schema(description = "설비별 사용량 5·6번섹션 — 설비별 누적 전력량·분포율 응답 DTO")
public class FacilityInstrumentEnergyUsageDto {

    @Schema(description = "측정 단위 (전력량)", example = "kWh")
    private String unit;

    @Schema(description = "전체 설비 누적 전력량 합 (kWh) — 분포율(%) 분모", example = "1250.5000")
    private BigDecimal totalElceg;

    @ArraySchema(schema = @Schema(
            description = "설비별 누적 전력량·분포율 항목 목록 (PWQ 보유 계측기, 전력량 0 설비도 0kWh·0% 로 포함)",
            implementation = InstrumentEnergyUsageItem.class))
    private List<InstrumentEnergyUsageItem> items;

    private FacilityInstrumentEnergyUsageDto() {
    }

    /**
     * 설비별 누적 전력량·분포율 응답 DTO 정적 팩토리.
     *
     * @param unit       측정 단위 (kWh)
     * @param totalElceg 전체 설비 누적 전력량 합 (kWh)
     * @param items      설비별 항목 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static FacilityInstrumentEnergyUsageDto of(
            String unit, BigDecimal totalElceg, List<InstrumentEnergyUsageItem> items) {
        FacilityInstrumentEnergyUsageDto dto = new FacilityInstrumentEnergyUsageDto();
        dto.unit = unit;
        dto.totalElceg = totalElceg;
        dto.items = items;
        return dto;
    }

    /**
     * 설비(계측기) 1건의 누적 전력량(5번섹션) + 분포율(6번섹션).
     */
    @Getter
    @Schema(description = "설비(계측기)별 누적 전력량·분포율 항목")
    public static class InstrumentEnergyUsageItem {

        @Schema(description = "계측기 ID", example = "in-xxx-xxx")
        private String instrumentId;

        @Schema(description = "계측기명", example = "송수1호기 전력량계")
        private String instrumentNm;

        @Schema(description = "장비 유형 코드 (PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR)",
                implementation = EquipType.class)
        private EquipType equipTypeCd;

        @Schema(description = "소속 시설 ID", example = "fa-xxx-xxx")
        private String facilityId;

        @Schema(description = "소속 시설명", example = "생활송수동")
        private String facilityNm;

        @Schema(description = "설비 누적 전력량 (kWh) — 5번섹션. PWQ 적산값 일 버킷 MAX-MIN 차분 합산",
                example = "320.5000")
        private BigDecimal elceg;

        @Schema(description = "설비 분포율 (%) — 6번섹션. [설비 전력량 / 전체 설비 전력량] × 100, 소수 첫째자리 "
                + "반올림. 항목별 독립 반올림이라 전 항목 합이 정확히 100.0 이 아닐 수 있음", example = "25.6")
        private BigDecimal ratio;

        private InstrumentEnergyUsageItem() {
        }

        /**
         * 설비별 누적 전력량·분포율 항목 정적 팩토리.
         *
         * @param instrumentId 계측기 ID
         * @param instrumentNm 계측기명
         * @param equipTypeCd  장비 유형 코드
         * @param facilityId   소속 시설 ID
         * @param facilityNm   소속 시설명
         * @param elceg        설비 누적 전력량 (kWh)
         * @param ratio        설비 분포율 (%)
         * @return 구성된 항목 DTO
         */
        public static InstrumentEnergyUsageItem of(
                String instrumentId,
                String instrumentNm,
                EquipType equipTypeCd,
                String facilityId,
                String facilityNm,
                BigDecimal elceg,
                BigDecimal ratio) {
            InstrumentEnergyUsageItem item = new InstrumentEnergyUsageItem();
            item.instrumentId = instrumentId;
            item.instrumentNm = instrumentNm;
            item.equipTypeCd = equipTypeCd;
            item.facilityId = facilityId;
            item.facilityNm = facilityNm;
            item.elceg = elceg;
            item.ratio = ratio;
            return item;
        }
    }
}
