package com.mo.swtp.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 메뉴 등록/수정 요청 DTO.
 *
 * <p>변경 시 null 필드는 유지된다 ({@code Menu.changeInfo} 정합).</p>
 */
@Data
@NoArgsConstructor
@Schema(description = "메뉴 등록/수정 요청 DTO")
public class MenuUpsertDto {

    @NotBlank
    @Schema(description = "메뉴명 (시스템 전체 UNIQUE)", example = "공지사항")
    private String menuNm;

    @Schema(description = "메뉴 URL — frontend 라우트 (NULL 가능 — 부모 그룹 메뉴)", example = "/notices")
    private String menuUrl;

    @Schema(description = "메뉴 설명", example = "공지사항 안내 메뉴")
    private String menuDesc;

    @NotNull
    @Schema(description = "표시 순서 (동일 부모 내 정렬)", example = "1")
    private Integer dispOrd;

    @Schema(description = "상위 메뉴 ID — self-FK (NULL = 최상위)", example = "5f8d-...")
    private String parentMenuId;
}
