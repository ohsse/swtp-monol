package com.mo.swtp.menu.domain;

import com.mo.swtp.user.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link MenuRole} 매핑 엔티티의 복합 PK.
 *
 * <p>{@code (menu_id, user_role)} 자연키 복합 PK — 중복 INSERT 자동 차단, forward 인덱스 자동
 * (menu_id 카디널리티 > user_role 카디널리티 순서 정합 — db/indexing-and-migration.md §1).</p>
 *
 * <p>{@link Embeddable} 클래스는 equals·hashCode 가 의무이므로 Lombok {@link EqualsAndHashCode}
 * 로 구현한다 (entity-patterns.md §N:M 매핑 엔티티 패턴).</p>
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class MenuRoleId implements Serializable {

    /** 메뉴 ID — {@link Menu#menuId} 참조 */
    @Column(name = "menu_id", length = 36, nullable = false)
    private String menuId;

    /** 사용자 권한 — {@link UserRole} enum 매핑 (ADMIN/USER) */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", length = 20, nullable = false)
    private UserRole userRole;
}
