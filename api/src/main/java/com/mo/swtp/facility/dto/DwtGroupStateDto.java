package com.mo.swtp.facility.dto;

import com.mo.swtp.facility.domain.Facility;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;

/**
 * 부모 시설의 자식 배수지(DWT) 그룹 실시간 상태 응답 DTO — 송수펌프제어분석 §4.
 *
 * <p>1번 섹션에서 활성화한 부모 시설(예: 성주정수장) 산하 모든 자식 DWT 의 실시간 현황을 단일 응답으로
 * 노출하는 컨테이너. {@code dwts} 리스트의 element 는 {@code disp_ord} ASC 정렬 (PLAN1 §Service 흐름 Step 2).</p>
 *
 * <p>자식 DWT 0건인 경우 {@code dwts} 는 빈 리스트로 반환되며, 부모 시설 식별자는 그대로 채워진다
 * (PLAN1 §성공 기준 #3).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 실시간 통지성 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "부모 시설의 자식 배수지(DWT) 그룹 실시간 상태 — 송수펌프제어분석 4번 섹션")
public class DwtGroupStateDto {

    @Schema(description = "부모 시설 ID", example = "fa-xxx-xxx")
    private String parentFacilityId;

    @Schema(description = "부모 시설명", example = "성주정수장")
    private String parentFacilityNm;

    @ArraySchema(schema = @Schema(description = "자식 배수지 상태 목록 (disp_ord ASC)",
            implementation = DwtStateDto.class))
    private List<DwtStateDto> dwts;

    private DwtGroupStateDto() {
    }

    /**
     * 부모 시설 entity + 자식 DWT 응답 목록으로부터 그룹 응답 DTO 를 구성한다.
     *
     * @param parent 부모 시설 entity (use_yn = Y 활성 시설)
     * @param dwts   자식 DWT 응답 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static DwtGroupStateDto of(Facility parent, List<DwtStateDto> dwts) {
        DwtGroupStateDto dto = new DwtGroupStateDto();
        dto.parentFacilityId = parent.getFacilityId();
        dto.parentFacilityNm = parent.getFacilityNm();
        dto.dwts = dwts;
        return dto;
    }
}
