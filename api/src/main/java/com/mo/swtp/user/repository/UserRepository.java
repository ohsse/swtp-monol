package com.mo.swtp.user.repository;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 JPA 리포지토리.
 */
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * 사용자 ID와 사용 여부로 사용자를 조회한다.
     */
    Optional<User> findByUserIdAndUseYn(String userId, YnType useYn);

    /**
     * 전체 사용자를 사용 여부 내림차순(활성 우선) · 사용자 ID 오름차순으로 조회한다.
     *
     * <p>ADMIN 전용 사용자 관리 화면에서 활성/비활성을 함께 조회하기 위해 사용한다.</p>
     *
     * @return 정렬된 사용자 목록
     */
    List<User> findAllByOrderByUseYnDescUserIdAsc();
}
