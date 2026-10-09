package com.mo.swtp.instrument.dto;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 계측기 목록 조회 필터 DTO.
 *
 * <p>모든 필터 NULL 허용 — NULL 인 경우 해당 조건 미적용 (전체 반환). 계측기관리CRUD PLAN1 결정 —
 * FacilitySearchDto 패턴 정합. 페이지네이션 없음·계측기명 검색 없음 (행 수 < 1000 가정).</p>
 *
 * <p>{@code facilityId} 필터는 시설별 계측기 화면 진입 시 사용된다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "계측기 목록 조회 필터")
public class InstrumentSearchDto {

    @Schema(description = "장비 유형 코드 필터 (NULL 시 전체)", implementation = EquipType.class)
    private EquipType equipTypeCd;

    @Schema(description = "사용 여부 필터 (NULL 시 전체)", implementation = YnType.class)
    private YnType useYn;

    @Schema(description = "소속 시설 ID 필터 (NULL 시 전체)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String facilityId;
}
