package com.mo.swtp.menu.dto;

import com.mo.swtp.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 메뉴-권한 매핑 갱신 요청 DTO.
 *
 * <p>전달된 {@code userRoles} 로 매핑을 일괄 교체한다 (DELETE 후 INSERT). 빈 리스트 전달은 매핑 전체 삭제 의미.</p>
 */
@Data
@NoArgsConstructor
@Schema(description = "메뉴-권한 매핑 갱신 요청 DTO")
public class MenuRoleUpsertDto {

    @NotEmpty
    @ArraySchema(schema = @Schema(description = "매핑할 사용자 권한 목록 (빈 리스트 비허용)", implementation = UserRole.class))
    private List<UserRole> userRoles;
}
