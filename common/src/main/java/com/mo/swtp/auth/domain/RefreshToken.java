package com.mo.swtp.auth.domain;

import com.mo.swtp.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JWT 리프레시 토큰 명세 엔티티.
 *
 * <p>토큰 원본은 저장하지 않고 SHA-256 해시값만 보관한다.
 * 1계정 1토큰 정책 — {@code user_id} 가 유니크 키 역할을 한다.</p>
 *
 * <p>등록자({@code rgstr_id})·수정자({@code updt_id})·등록일시·수정일시는
 * {@link BaseEntity} 의 JPA Auditing 으로 자동 주입된다.</p>
 *
 * <p>{@code User} 엔티티와의 1:1 관계는 의도적으로 JPA 연관관계로 매핑하지 않는다.
 * 생명주기는 {@code UserEventHandler} 의 {@code UserDeactivatedEvent} /
 * {@code UserDeletedEvent} {@code @TransactionalEventListener(BEFORE_COMMIT)} 리스너가 관리하며,
 * DB 무결성은 {@code uk_refresh_token_p_user_id} UNIQUE 와 {@code fk_refresh_token_p_user_id}
 * FK 로 강제한다.</p>
 */
@Entity
@Table(name = "refresh_token_p")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshToken extends BaseEntity {

    /** 토큰 식별자 (UUID 자동 생성) */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id", nullable = false, length = 36)
    private String tokenId;

    /** 토큰 주체 (사용자 ID, user_m.user_id 참조) */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /** SHA-256 해시된 토큰값 */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** 토큰 만료 일시 */
    @Column(name = "expr_dtm", nullable = false)
    private LocalDateTime exprDtm;

    /** 토큰 폐기 일시 (null이면 활성) */
    @Column(name = "revoke_dtm")
    private LocalDateTime revokeDtm;

    /** 마지막 사용 일시 */
    @Column(name = "last_used_dtm")
    private LocalDateTime lastUsedDtm;

    /**
     * 새 리프레시 토큰 레코드를 생성한다. 등록자·수정자는 JPA Auditing 이 자동 주입한다.
     *
     * @param userId    사용자 ID
     * @param tokenHash SHA-256 해시된 토큰값
     * @param exprDtm   만료 일시
     */
    public static RefreshToken create(String userId, String tokenHash, LocalDateTime exprDtm) {
        return new RefreshToken(null, userId, tokenHash, exprDtm, null, null);
    }

    /**
     * 폐기 여부를 확인한다.
     */
    public boolean isRevoked() {
        return revokeDtm != null;
    }

    /**
     * 만료 여부를 확인한다.
     *
     * @param now 비교 기준 일시
     */
    public boolean isExpired(LocalDateTime now) {
        return exprDtm.isBefore(now);
    }

    /**
     * 토큰을 갱신한다 (리프레시 토큰 로테이션). 수정자는 JPA Auditing 이 자동 주입한다.
     *
     * @param tokenHash   새 SHA-256 해시된 토큰값
     * @param exprDtm     새 만료 일시
     * @param lastUsedDtm 마지막 사용 일시
     */
    public void rotate(String tokenHash, LocalDateTime exprDtm, LocalDateTime lastUsedDtm) {
        this.tokenHash = tokenHash;
        this.exprDtm = exprDtm;
        this.lastUsedDtm = lastUsedDtm;
        this.revokeDtm = null;
    }

    /**
     * 토큰을 폐기한다 (로그아웃). 수정자는 JPA Auditing 이 자동 주입한다.
     *
     * @param revokeDtm 폐기 일시
     */
    public void revoke(LocalDateTime revokeDtm) {
        this.revokeDtm = revokeDtm;
        this.lastUsedDtm = revokeDtm;
    }
}
