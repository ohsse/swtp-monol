/**
 * ═════════════════════════════════════════════════════════════
 * 📄 FILE     : null.java
 * 📁 PACKAGE  : swtp-com.mo.swtp.user.dto
 * 👤 AUTHOR   : stz
 * 🕒 CREATED  : 26. 6. 10.
 * ═════════════════════════════════════════════════════════════
 * ═════════════════════════════════════════════════════════════
 * 📝 DESCRIPTION
 * -
 * ═════════════════════════════════════════════════════════════
 * ═════════════════════════════════════════════════════════════
 * 🔄 CHANGE LOG
 * - DATE : 2026/06/10 | Author : stz | 최초 생성
 * ═════════════════════════════════════════════════════════════
 */
package com.mo.swtp.user.dto;


import com.mo.swtp.common.enumtype.YnType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@NoArgsConstructor
@Data
@Schema(description = "본인 정보 수정 요청 DTO")
public class MyUserTtylUpsertDto {
    @NotNull
    @Schema(description = "튜토리얼 사용 여부", implementation = YnType.class)
    private YnType ttrylUseYn;
}
