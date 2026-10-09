package com.mo.swtp.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "공통 응답 DTO")
public class CommonResponseDto<T> {

    @Schema(description = "성공 또는 에러 코드", example = "SUCCESS")
    private String code;

    @Schema(description = "응답 데이터", nullable = true)
    private T data;
}
