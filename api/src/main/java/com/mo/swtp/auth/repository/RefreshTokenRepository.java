package com.mo.swtp.auth.repository;

import com.mo.swtp.auth.domain.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰 리포지토리.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * 사용자 ID 로 리프레시 토큰을 조회한다.
     *
     * @param userId 사용자 ID
     */
    Optional<RefreshToken> findByUserId(String userId);

    /**
     * 사용자 ID 에 해당하는 리프레시 토큰을 삭제한다.
     *
     * <p>JPQL 직접 DELETE를 사용하여 SQL이 즉시 실행된다.
     * 사용자 물리 삭제 시 FK 제약 위반을 방지하기 위해 user DELETE 보다 먼저 실행되어야 한다.
     * {@code UserEventHandler}의 {@code BEFORE_COMMIT} 리스너에서 호출된다.</p>
     *
     * @param userId 사용자 ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
