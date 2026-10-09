package com.mo.swtp.instrument.dto;

import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.instrument.domain.PumpOprtngType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 송수펌프(PUMP) 등록·수정 요청 DTO.
 *
 * <p>자식 전용 컬럼 4건 — 모두 NOT NULL (DOM_QTY_15_4·DOM_CODE_20 기본 NULL 정책에서 더 엄격하게 적용):</p>
 * <ul>
 *   <li>{@link #ratedHead} (m) — 정격 양정. {@code pumpcontrol_null_alignment ANALYZE1, 2026-04-25} 결정</li>
 *   <li>{@link #ratedFlwrt} (m³/h) — 정격 유량. 동일 결정</li>
 *   <li>{@link #oprtngType} — 펌프 조작유형 (AUTO_CAPABLE/SEMI_AUTO_CAPABLE).
 *       {@code 펌프조작유형 ANALYZE1 안건 4, 2026-05-12} 결정</li>
 *   <li>{@link #driveType} — 펌프 구동 방식 (INVERTER_DRIVE/RATED_DRIVE).
 *       {@code pump_drive_type ANALYZE1 안건 2·5, 2026-05-20} 결정. {@code RATED_DRIVE + AUTO_CAPABLE}
 *       조합은 도메인 무효 — {@code Pump.create()}/{@code changePumpSelfColumns()} 진입 시
 *       {@link com.mo.swtp.instrument.exception.PumpErrorCode#INVALID_PUMP_DRIVE_OPRTNG_COMBINATION}
 *       으로 차단</li>
 * </ul>
 *
 * <p>{@code tagNm} 필드는 시설물응답DTO명세 ANALYZE1 (2026-05-12) 안건 8·9 결정으로 폐기된 양방향
 * 중복 컬럼이라 본 요청 DTO 에도 부재한다 ({@code tag_m.instrument_id} FK SSOT —
 * {@code entity-patterns.md §FK 보유 측 SSOT}).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "송수펌프 등록·수정 요청 DTO — 자식 전용 3 NOT NULL 컬럼 보유")
public class PumpUpsertDto extends InstrumentUpsertDto {

    @Schema(description = "정격 양정 (m) — 제조사 명판값", example = "65.0",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "ratedHead 는 필수입니다.")
    @Positive(message = "ratedHead 는 양수여야 합니다.")
    private BigDecimal ratedHead;

    @Schema(description = "정격 유량 (m³/h) — 제조사 명판값", example = "250.0",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "ratedFlwrt 는 필수입니다.")
    @Positive(message = "ratedFlwrt 는 양수여야 합니다.")
    private BigDecimal ratedFlwrt;

    @Schema(description = "펌프 조작유형 (AUTO_CAPABLE/SEMI_AUTO_CAPABLE — 펌프의 물리적 설계값)",
            implementation = PumpOprtngType.class, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "oprtngType 은 필수입니다.")
    private PumpOprtngType oprtngType;

    @Schema(description = "펌프 구동 방식 (INVERTER_DRIVE/RATED_DRIVE — 펌프의 물리적 설계값)",
            implementation = PumpDriveType.class, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "driveType 은 필수입니다.")
    private PumpDriveType driveType;
}
