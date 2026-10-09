package com.mo.swtp.facility.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;

/**
 * 배수지(DWT) 1건의 실시간 상태 + 최소요구관압 응답 DTO — 송수펌프제어분석 §4·§5 통합 응답.
 *
 * <p>한 배수지(DWT)에 속한 유입/유출 유량계 · 밸브 · 수위계의 최신 측정값과 함께 배수지 자체의
 * 최소요구관압 설계값 2건 ({@code minReqPrsr}·{@code minReqBranchPrsr}) 을 단일 컨테이너로 노출한다.
 * 유입/유출 유량계는 {@code Tag.io_cd} enum (INPUT/OUTPUT/BIDIR) 으로 구분 — INPUT·BIDIR 태그를 가진
 * 유량계가 유입, OUTPUT·BIDIR 태그가 유출 (PLAN1 §가정 결정 — BIDIR 양쪽 후보 허용).</p>
 *
 * <p>5번섹션 (송수펌프제어분석-5번섹션 PLAN1, 2026-05-14): 4번섹션의 배수지 목록 중 사용자가 선택한
 * 1건의 배수지 박스에 "최소요구관압 기준배수지({@code facilityNm})"·"최소요구관압(분기점)
 * ({@code minReqBranchPrsr})"·"최소요구관압({@code minReqPrsr})" 3필드를 표출한다.
 * 추가 엔드포인트 없이 4번섹션 응답에 합쳐 JOINED 자식 자동 적재로 추가 쿼리 0건 (PLAN1 §구현 방향).</p>
 *
 * <p>다중 등록 안전망: 한 DWT 에 유입 또는 유출 FLWMTR 가 2건 이상 등록될 경우 첫 매치만 응답에 채워지고
 * {@code multipleInFlwmtrDetected} / {@code multipleOutFlwmtrDetected} 플래그가 {@code true} 로 노출된다
 * (PLAN1 §다중 등록 안전망 + Service WARN 로그).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 실시간 통지성 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "배수지(DWT) 실시간 상태 + 최소요구관압 — 송수펌프제어분석 4·5번 섹션 통합 응답")
public class DwtStateDto {

    @Schema(description = "배수지 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "배수지명 — 5번섹션 \"최소요구관압 기준배수지\" 표출", example = "배수지A")
    private String facilityNm;

    @Schema(description = "최소요구관압 (kgf/cm², 5번섹션 표출) — 배수지 본체 인터록 평가 기준값",
            example = "1.5000")
    private BigDecimal minReqPrsr;

    @Schema(description = "최소요구관압(분기점) (kgf/cm², 5번섹션 표출) — 배수지로 분기되는 관로 분기점 지점값",
            example = "0.8000")
    private BigDecimal minReqBranchPrsr;

    @Schema(description = "유입 유량계 상태 (없으면 null)", implementation = InletFlwmtrStateDto.class)
    private InletFlwmtrStateDto inFlwmtr;

    @Schema(description = "유출 유량계 상태 (없으면 null)", implementation = OutletFlwmtrStateDto.class)
    private OutletFlwmtrStateDto outFlwmtr;

    @Schema(description = "유입 유량계 다중 등록 감지 — 2건 이상 등록 시 true, inFlwmtr 는 첫 매치만 사용",
            example = "false")
    private boolean multipleInFlwmtrDetected;

    @Schema(description = "유출 유량계 다중 등록 감지 — 2건 이상 등록 시 true, outFlwmtr 는 첫 매치만 사용",
            example = "false")
    private boolean multipleOutFlwmtrDetected;

    @ArraySchema(schema = @Schema(description = "밸브 상태 목록", implementation = ValveStateDto.class))
    private List<ValveStateDto> valves;

    @ArraySchema(schema = @Schema(description = "수위계 상태 목록", implementation = LvmtrStateDto.class))
    private List<LvmtrStateDto> lvmtrs;

    private DwtStateDto() {
    }

    /**
     * 배수지 단위 실시간 상태 + 최소요구관압 응답 DTO 정적 팩토리.
     *
     * @param facilityId                  배수지 ID
     * @param facilityNm                  배수지명 (5번섹션 "기준배수지" 표출)
     * @param minReqPrsr                  최소요구관압 kgf/cm² (5번섹션)
     * @param minReqBranchPrsr            최소요구관압(분기점) kgf/cm² (5번섹션)
     * @param inFlwmtr                    유입 유량계 (없으면 null)
     * @param outFlwmtr                   유출 유량계 (없으면 null)
     * @param multipleInFlwmtrDetected    유입 유량계 다중 등록 감지 플래그
     * @param multipleOutFlwmtrDetected   유출 유량계 다중 등록 감지 플래그
     * @param valves                      밸브 상태 목록 (빈 List 허용)
     * @param lvmtrs                      수위계 상태 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static DwtStateDto of(
            String facilityId,
            String facilityNm,
            BigDecimal minReqPrsr,
            BigDecimal minReqBranchPrsr,
            InletFlwmtrStateDto inFlwmtr,
            OutletFlwmtrStateDto outFlwmtr,
            boolean multipleInFlwmtrDetected,
            boolean multipleOutFlwmtrDetected,
            List<ValveStateDto> valves,
            List<LvmtrStateDto> lvmtrs) {
        DwtStateDto dto = new DwtStateDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.minReqPrsr = minReqPrsr;
        dto.minReqBranchPrsr = minReqBranchPrsr;
        dto.inFlwmtr = inFlwmtr;
        dto.outFlwmtr = outFlwmtr;
        dto.multipleInFlwmtrDetected = multipleInFlwmtrDetected;
        dto.multipleOutFlwmtrDetected = multipleOutFlwmtrDetected;
        dto.valves = valves;
        dto.lvmtrs = lvmtrs;
        return dto;
    }
}
