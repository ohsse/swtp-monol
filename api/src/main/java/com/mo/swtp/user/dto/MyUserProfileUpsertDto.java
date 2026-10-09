package com.mo.swtp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 본인 정보(이름) 수정 요청 DTO.
 *
 * <p>인증된 사용자가 자신의 이름만 변경할 때 사용한다.
 * 권한 격상을 방지하기 위해 {@code userRole} 필드는 노출하지 않는다.</p>
 */
@Data
@NoArgsConstructor
@Schema(description = "본인 정보 수정 요청 DTO")
public class MyUserProfileUpsertDto {

    @NotBlank
    @Schema(description = "사용자 이름", example = "홍길동")
    private String userNm;
}
