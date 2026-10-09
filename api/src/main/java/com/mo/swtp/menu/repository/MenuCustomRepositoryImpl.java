package com.mo.swtp.menu.repository;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.menu.domain.Menu;
import com.mo.swtp.menu.domain.QMenu;
import com.mo.swtp.menu.domain.QMenuRole;
import com.mo.swtp.user.domain.UserRole;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * {@link MenuCustomRepository} Querydsl 구현.
 *
 * <p>{@code JPAQueryFactory} 빈은 {@code ApiQuerydslConfig} 에서 제공된다.</p>
 *
 * <p>단일 SELECT (menu_m JOIN menu_role_r) + 메모리 트리 빌드 패턴 (ANALYZE1 안건 11) — 트리 빌드는
 * {@code MenuQueryService} 책임. Repository 는 평탄 활성 메뉴 목록만 반환한다.</p>
 */
@RequiredArgsConstructor
public class MenuCustomRepositoryImpl implements MenuCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Menu> findActiveMenusByRole(UserRole userRole) {
        QMenu m = QMenu.menu;
        QMenuRole mr = QMenuRole.menuRole;
        return queryFactory
                .selectFrom(m)
                .join(mr).on(mr.id.menuId.eq(m.menuId))
                .where(
                        mr.id.userRole.eq(userRole),
                        m.useYn.eq(YnType.Y)
                )
                .orderBy(m.dispOrd.asc())
                .fetch();
    }
}
