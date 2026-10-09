package com.mo.swtp.facility.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 가압장(PRSF) 등록·수정 요청 DTO.
 *
 * <p>자식 전용 컬럼 없음 — {@link FacilityUpsertDto} 공통 필드만 사용한다 (송수펌프제어분석 ANALYZE1,
 * 2026-05-08 — PRSF skeleton 도입).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "가압장(PRSF) 등록·수정 요청 DTO — 공통 필드만 사용")
public class PrsfUpsertDto extends FacilityUpsertDto {
}
