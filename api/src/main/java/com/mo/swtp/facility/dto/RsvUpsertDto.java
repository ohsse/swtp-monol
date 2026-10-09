package com.mo.swtp.facility.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 저수지(RSV) 등록·수정 요청 DTO.
 *
 * <p>자식 전용 컬럼 없음 — {@link FacilityUpsertDto} 공통 필드만 사용한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "저수지(RSV) 등록·수정 요청 DTO — 공통 필드만 사용")
public class RsvUpsertDto extends FacilityUpsertDto {
}
