package com.mo.swtp.instrument.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 전력량계(ELCMTR) 등록·수정 요청 DTO.
 *
 * <p>자식 전용 컬럼 없음 — {@link InstrumentUpsertDto} 공통 필드만 사용한다. 자식 전용 컬럼 도입은
 * 운영 요구 발생 시 별도 사이클 (PLAN1 §제외 사항, 2026-05-12).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "전력량계 등록·수정 요청 DTO — 공통 필드만 사용")
public class PowerMeterUpsertDto extends InstrumentUpsertDto {
}
