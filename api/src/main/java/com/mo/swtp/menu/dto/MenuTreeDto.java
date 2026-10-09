package com.mo.swtp.menu.dto;

import com.mo.swtp.menu.domain.Menu;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메뉴 트리 응답 DTO (자기참조 재귀).
 *
 * <p>{@link Menu} 엔티티로부터 평탄 → 트리 변환을 거쳐 {@code MenuQueryService} 에서 조립된다.
 * children 은 빈 리스트로 초기화하여 JSON 직렬화 시 null 회피.</p>
 */
@Getter
@NoArgsConstructor
@Schema(description = "메뉴 트리 응답 DTO (자기참조 재귀)")
public class MenuTreeDto {

    @Schema(description = "메뉴 ID")
    private String menuId;

    @Schema(description = "메뉴명")
    private String menuNm;

    @Schema(description = "메뉴 URL — frontend 라우트 (NULL 가능)")
    private String menuUrl;

    @Schema(description = "메뉴 설명")
    private String menuDesc;

    @Schema(description = "표시 순서")
    private Integer dispOrd;

    @Schema(description = "상위 메뉴 ID (NULL = 최상위)")
    private String parentMenuId;

    @ArraySchema(schema = @Schema(description = "자식 메뉴 트리", implementation = MenuTreeDto.class))
    private List<MenuTreeDto> children = new ArrayList<>();

    private MenuTreeDto(Menu menu) {
        this.menuId = menu.getMenuId();
        this.menuNm = menu.getMenuNm();
        this.menuUrl = menu.getMenuUrl();
        this.menuDesc = menu.getMenuDesc();
        this.dispOrd = menu.getDispOrd();
        this.parentMenuId = menu.getParentMenuId();
    }

    /**
     * Menu 엔티티로부터 트리 노드를 생성한다 (children 은 후속 트리 빌드 시 채움).
     *
     * @param menu Menu 엔티티
     * @return children 비어있는 트리 노드 DTO
     */
    public static MenuTreeDto from(Menu menu) {
        return new MenuTreeDto(menu);
    }

    /**
     * 자식 노드를 추가한다 (트리 빌드 시 호출).
     *
     * @param child 자식 트리 노드
     */
    public void addChild(MenuTreeDto child) {
        this.children.add(child);
    }
}
