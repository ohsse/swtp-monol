package com.mo.swtp.proc.dto;

import com.mo.swtp.common.dto.BaseAuditResponseDto;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.proc.domain.Process;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공정/제어대상 조회 응답 DTO.
 *
 * <p>{@link BaseAuditResponseDto} 상속으로 메타 4컬럼 자동 노출.</p>
 */
@Getter
@NoArgsConstructor
@Schema(description = "공정/제어대상 조회 응답 DTO")
public class ProcDto extends BaseAuditResponseDto {

    @Schema(description = "공정/제어대상 ID (외부 할당 PK)", example = "PUMP_CONTROL")
    private String procId;

    @Schema(description = "공정/제어대상명 (시스템 전체 UNIQUE)", example = "송수펌프제어")
    private String procNm;

    @Schema(description = "표시 순서", example = "1")
    private Integer dispOrd;

    @Schema(description = "사용 여부", implementation = YnType.class)
    private YnType useYn;

    private ProcDto(Process process) {
        this.procId = process.getProcId();
        this.procNm = process.getProcNm();
        this.dispOrd = process.getDispOrd();
        this.useYn = process.getUseYn();
        applyAuditMeta(process);
    }

    /**
     * Process 엔티티로부터 응답 DTO 를 생성한다.
     */
    public static ProcDto from(Process process) {
        return new ProcDto(process);
    }
}
