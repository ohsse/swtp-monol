package com.mo.swtp.user.domain;

import com.mo.swtp.common.domain.DomainEventEntity;
import com.mo.swtp.common.enumtype.YnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 사용자 마스터 엔티티.
 *
 * <p>비밀번호는 BCrypt 인코딩된 값만 저장하며 원본은 저장하지 않는다.
 * 삭제는 {@link #deactivate()}를 통한 논리 삭제만 허용한다.</p>
 *
 * <p>PK({@code user_id})는 외부에서 할당되므로 {@link Persistable}을 구현한다.
 * {@code isNew()} 판정은 {@link BaseEntity}의 {@code newEntity} 플래그에 위임한다.</p>
 *
 * <p>등록자({@code rgstr_id})·수정자({@code updt_id})·등록일시·수정일시는
 * {@link BaseEntity} 의 JPA Auditing 으로 자동 주입된다.</p>
 */
@Entity
@Table(name = "user_m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends DomainEventEntity implements Persistable<String> {

    @Id
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /** 사용자 이름 */
    @Column(name = "user_nm", nullable = false, length = 100)
    private String userNm;

    /** BCrypt 해시 비밀번호 */
    @Column(name = "user_pw", nullable = false, length = 255)
    private String userPw;

    /** 사용자 권한 역할 */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 20)
    private UserRole userRole;

    /** 사용 여부 ({@link YnType#Y}: 활성, {@link YnType#N}: 비활성) */
    @Enumerated(EnumType.STRING)
    @Column(name = "use_yn", nullable = false, length = 1)
    private YnType useYn;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "ttryl_use_yn" , nullable = false, length = 1)
    private YnType ttrylUseYn;

    /**
     * {@inheritDoc} — PK를 반환한다.
     */
    @Override
    public String getId() {
        return userId;
    }

    /**
     * 신규 사용자를 생성한다. 등록자·수정자는 JPA Auditing 이 자동 주입한다.
     *
     * @param userId    사용자 ID
     * @param userNm    사용자 이름
     * @param encodedPw BCrypt 인코딩된 비밀번호
     * @param role      권한 역할
     */
    public static User create(String userId, String userNm, String encodedPw, UserRole role, YnType ttrylUseYn) {
        return new User(userId, userNm, encodedPw, role, YnType.Y, ttrylUseYn);
    }

    /**
     * 비밀번호를 변경한다.
     *
     * @param encodedPw 새 BCrypt 인코딩 비밀번호
     */
    public void changePw(String encodedPw) {
        this.userPw = encodedPw;
    }

    /**
     * 사용자 기본 정보를 변경한다.
     *
     * @param userNm   변경할 이름 (null이면 유지)
     * @param userRole 변경할 권한 (null이면 유지)
     */
    public void changeInfo(String userNm, UserRole userRole) {
        if (userNm != null) {
            this.userNm = userNm;
        }
        if (userRole != null) {
            this.userRole = userRole;
        }
    }
    
    public void changeTtrylUseYn(YnType ttrylUseYn) {
        this.ttrylUseYn = ttrylUseYn;
    }

    /**
     * 사용자를 비활성화(논리 삭제)한다.
     */
    public void deactivate() {
        this.useYn = YnType.N;
    }
}
