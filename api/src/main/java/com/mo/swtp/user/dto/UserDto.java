package com.mo.swtp.user.dto;

import com.mo.swtp.common.dto.BaseAuditResponseDto;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.user.domain.User;
import com.mo.swtp.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 조회 응답 DTO.
 *
 * <p>비밀번호 해시를 포함하지 않으며, 웹 계층 응답 전용으로 사용한다.</p>
 *
 * <p>{@link BaseAuditResponseDto} 를 상속하여 공통 메타 4컬럼
 * ({@code rgstrDtm}·{@code updtDtm}·{@code rgstrId}·{@code updtId}) 을 부모로부터 일관 노출한다
 * — 패턴: {@code .claude/rules/api-patterns.md §BaseAuditResponseDto 패턴}.</p>
 */
@Getter
@NoArgsConstructor
@Schema(description = "사용자 조회 응답 DTO")
public class UserDto extends BaseAuditResponseDto {

    @Schema(description = "사용자 ID", example = "admin")
    private String userId;

    @Schema(description = "사용자 이름", example = "관리자")
    private String userNm;

    @Schema(description = "권한 역할", implementation = UserRole.class)
    private UserRole userRole;

    @Schema(description = "사용 여부", implementation = YnType.class)
    private YnType useYn;

    private UserDto(User user) {
        this.userId = user.getUserId();
        this.userNm = user.getUserNm();
        this.userRole = user.getUserRole();
        this.useYn = user.getUseYn();
        applyAuditMeta(user);
    }

    /**
     * User 엔티티로부터 조회 응답 DTO를 생성한다.
     *
     * @param user 사용자 엔티티
     * @return 비밀번호 해시가 제외된 응답 DTO
     */
    public static UserDto from(User user) {
        return new UserDto(user);
    }
}
