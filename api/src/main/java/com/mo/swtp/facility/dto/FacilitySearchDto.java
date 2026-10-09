package com.mo.swtp.facility.dto;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.enumtype.FacilityGroup;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시설 목록 조회 필터 DTO.
 *
 * <p>모든 필터 NULL 허용 — NULL 인 경우 해당 조건 미적용 (전체 반환). 시설물관리기능 ANALYZE1
 * 사용자 결정 (2026-05-11) — 페이지네이션 없음·시설명 검색 없음.</p>
 *
 * <p>{@code hasPump} 는 송수펌프제어 화면 1번 섹션 (시설물 목록) 요구로 도입된 필터다 —
 * 송수펌프_시설목록 작업 (2026-05-13). {@code Boolean.TRUE.equals(hasPump)} 일 때만
 * "활성 펌프 1대 이상 보유 시설" 로 EXISTS 필터 적용. NULL·{@code false} 는 미적용.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "시설 목록 조회 필터")
public class FacilitySearchDto {

    @Schema(description = "시설 유형 코드 필터 (NULL 시 전체)", implementation = FacilityType.class)
    private FacilityType facilityTypeCd;

    @Schema(description = "시설 그룹 코드 필터 (NULL 시 전체). OPERATION=운영시설 8종 등 그룹 단위 IN 조회. "
            + "facilityTypeCd 와 동시 지정 시 AND 교집합.",
            implementation = FacilityGroup.class)
    private FacilityGroup facilityGroupCd;

    @Schema(description = "사용 여부 필터 (NULL 시 전체)", implementation = YnType.class)
    private YnType useYn;

    @Schema(description = "활성 펌프 보유 여부 필터 — true 일 때만 적용 (NULL·false 시 미적용). "
            + "true 면 instrument_m 에 equip_type_cd=PUMP AND use_yn=Y 행이 1건 이상 EXISTS 인 시설만 응답.",
            example = "true")
    private Boolean hasPump;
}
