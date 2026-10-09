package com.mo.swtp.menu.domain;

import com.mo.swtp.common.domain.BaseEntity;
import com.mo.swtp.common.enumtype.YnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한 메뉴 마스터 엔티티.
 *
 * <p>자기참조 self-FK ({@code parent_menu_id}) 를 통한 트리 구조를 지원한다.
 * 깊이는 애플리케이션 레벨에서 N=3 으로 제한 (ANALYZE1 안건 5).</p>
 *
 * <p>PK 는 UUID 자동 생성이며 {@link org.springframework.data.domain.Persistable} 미구현
 * (마스터도메인설계 ANALYZE1 Round 3 결정 — facility_m·instrument_m 동일 패턴).</p>
 *
 * <p>{@code menu_nm} 은 시스템 전체 UNIQUE — UUID PK 는 시스템 내부 식별자, 사용자 식별은 이름값 의존
 * (facility_nm·instrument_nm 선례 동일 패턴).</p>
 *
 * <p>등록자·수정자·등록일시·수정일시는 {@link BaseEntity} 의 JPA Auditing 으로 자동 주입된다.</p>
 */
@Entity
@Table(
        name = "menu_m",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_menu_m_menu_nm",
                columnNames = "menu_nm"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "menu_id", length = 36, nullable = false)
    private String menuId;

    /** 메뉴명 (시스템 전체 UNIQUE) */
    @Column(name = "menu_nm", length = 100, nullable = false)
    private String menuNm;

    /** 메뉴 URL — frontend 라우트 (NULL 허용 — 부모 그룹 메뉴는 URL 없음) */
    @Column(name = "menu_url", length = 255)
    private String menuUrl;

    /** 메뉴 설명 (NULL 허용) */
    @Column(name = "menu_desc", columnDefinition = "TEXT")
    private String menuDesc;

    /** 표시 순서 — 동일 부모 내 정렬 */
    @Column(name = "disp_ord", nullable = false)
    private Integer dispOrd;

    /** 상위 메뉴 ID — self-FK (NULL 허용, 최상위 메뉴는 NULL) */
    @Column(name = "parent_menu_id", length = 36)
    private String parentMenuId;

    /** 사용 여부 ({@link YnType#Y}: 활성, {@link YnType#N}: 비활성) */
    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = 1)
    private YnType useYn;

    /**
     * 신규 메뉴를 생성한다. 등록자·수정자는 JPA Auditing 이 자동 주입한다.
     *
     * @param menuNm       메뉴명
     * @param menuUrl      메뉴 URL (NULL 가능)
     * @param menuDesc     메뉴 설명 (NULL 가능)
     * @param dispOrd      표시 순서
     * @param parentMenuId 상위 메뉴 ID (NULL = 최상위)
     * @return 신규 Menu 엔티티 (menu_id 는 영속화 시 UUID 자동 생성)
     */
    public static Menu create(
            String menuNm,
            String menuUrl,
            String menuDesc,
            Integer dispOrd,
            String parentMenuId
    ) {
        return new Menu(null, menuNm, menuUrl, menuDesc, dispOrd, parentMenuId, YnType.Y);
    }

    /**
     * 메뉴 정보를 변경한다. null 필드는 유지한다.
     *
     * @param menuNm       변경할 메뉴명 (null이면 유지)
     * @param menuUrl      변경할 메뉴 URL (null이면 유지 — 명시적 NULL 변경 시 별도 메서드 필요)
     * @param menuDesc     변경할 메뉴 설명 (null이면 유지)
     * @param dispOrd      변경할 표시 순서 (null이면 유지)
     * @param parentMenuId 변경할 상위 메뉴 ID (null이면 유지)
     */
    public void changeInfo(
            String menuNm,
            String menuUrl,
            String menuDesc,
            Integer dispOrd,
            String parentMenuId
    ) {
        if (menuNm != null) {
            this.menuNm = menuNm;
        }
        if (menuUrl != null) {
            this.menuUrl = menuUrl;
        }
        if (menuDesc != null) {
            this.menuDesc = menuDesc;
        }
        if (dispOrd != null) {
            this.dispOrd = dispOrd;
        }
        if (parentMenuId != null) {
            this.parentMenuId = parentMenuId;
        }
    }

    /**
     * 메뉴를 비활성화 (논리 삭제) 한다.
     */
    public void deactivate() {
        this.useYn = YnType.N;
    }
}
