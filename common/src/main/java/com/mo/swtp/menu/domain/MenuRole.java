package com.mo.swtp.menu.domain;

import com.mo.swtp.user.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 메뉴-권한 N:M 매핑 엔티티 (INSERT/DELETE 전용).
 *
 * <p>두 독립 마스터 (메뉴·사용자 권한) 사이의 순수 N:M 관계. {@code menu_role_r} 테이블은
 * suffix {@code _r}(관계) 채택 (ANALYZE2 R-1 만장일치 — naming.md L61 N:M 정의 직접 정합).</p>
 *
 * <p>BaseEntity 미상속 (B안 — ANALYZE2 R-2/R-3 만장일치): INSERT/DELETE 전용 매핑이므로
 * {@code updt_dtm}·{@code updt_id} 가 데드 컬럼이 됨. {@code rgstr_dtm}·{@code rgstr_id} 만 직접
 * 선언하고 {@link AuditingEntityListener} 가 자동 주입한다 (entity-patterns.md §N:M 매핑 엔티티 패턴).</p>
 *
 * <p>매핑 단위 갱신 (예: {@code valid_period}·{@code grant_reason} 등 컬럼 추가 시) 이 필요해지면
 * BaseEntity 4 컬럼 상속 (A안) 으로 전환을 별도 PLAN 에서 결정한다.</p>
 */
@Entity
@Table(name = "menu_role_r")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MenuRole {

    @EmbeddedId
    private MenuRoleId id;

    /**
     * 메뉴 마스터 — {@link MapsId} 로 EmbeddedId.menuId 와 동기화.
     * LAZY 페치 (집합 조회 시 N+1 회피는 메모리 트리 빌드 패턴으로 처리).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("menuId")
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    /** 등록 일시 (INSERT-only, AuditingEntityListener 자동 주입) */
    @CreatedDate
    @Column(name = "rgstr_dtm", nullable = false, updatable = false)
    private LocalDateTime rgstrDtm;

    /** 등록자 ID (INSERT-only, AuditingEntityListener 자동 주입) */
    @CreatedBy
    @Column(name = "rgstr_id", length = 50, nullable = false, updatable = false)
    private String rgstrId;

    /**
     * 신규 메뉴-권한 매핑을 생성한다. 등록자·등록일시는 JPA Auditing 이 자동 주입한다.
     *
     * @param menu     매핑할 메뉴 엔티티
     * @param userRole 매핑할 사용자 권한
     * @return 신규 MenuRole 엔티티
     */
    public static MenuRole create(Menu menu, UserRole userRole) {
        return new MenuRole(new MenuRoleId(menu.getMenuId(), userRole), menu, null, null);
    }
}
