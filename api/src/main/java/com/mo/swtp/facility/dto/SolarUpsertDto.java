package com.mo.swtp.facility.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 태양광(SOLAR) 등록·수정 요청 DTO.
 *
 * <p>자식 전용 컬럼 없음 — {@link FacilityUpsertDto} 공통 필드만 사용한다 (시설_도메인_확장 ANALYZE1,
 * 2026-06-08). 향후 자식 전용 컬럼 도입 시 본 클래스에 추가.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "태양광(SOLAR) 등록·수정 요청 DTO — 공통 필드만 사용")
public class SolarUpsertDto extends FacilityUpsertDto {
}
