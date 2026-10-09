package com.mo.swtp.user.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.user.domain.User;
import com.mo.swtp.user.dto.UserUpsertDto;
import com.mo.swtp.user.event.UserEventPublisher;
import com.mo.swtp.user.exception.UserErrorCode;
import com.mo.swtp.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 CRUD 서비스.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserEventPublisher userEventPublisher;

    /**
     * 활성 사용자를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 활성 사용자 엔티티
     * @throws RestApiException USER_NOT_FOUND — 존재하지 않거나 비활성인 경우
     */
    public User findActiveUser(String userId) {
        return userRepository.findByUserIdAndUseYn(userId, YnType.Y)
                .orElseThrow(() -> new RestApiException(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * 전체 사용자를 활성/비활성 모두 포함하여 조회한다.
     *
     * <p>정렬은 {@code use_yn} 내림차순(활성 우선) 후 {@code user_id} 오름차순.
     * ADMIN 전용 사용자 관리 화면에서 호출한다.</p>
     *
     * @return 정렬된 사용자 목록
     */
    public List<User> findAllUsers() {
        return userRepository.findAllByOrderByUseYnDescUserIdAsc();
    }

    /**
     * 인증된 사용자 본인의 이름을 변경한다.
     *
     * <p>{@code userRole} 은 변경하지 않는다 (권한 격상 방지).
     * 비활성 사용자는 {@link #findActiveUser(String)} 가 USER_NOT_FOUND 로 자동 차단한다.</p>
     *
     * @param userId 본인 사용자 ID (JWT subject)
     * @param userNm 변경할 이름
     */
    @Transactional
    public void changeMyProfile(String userId, String userNm) {
        User user = findActiveUser(userId);
        user.changeInfo(userNm, null);
    }

    /**
     * 인증된 사용자 본인의 비밀번호를 변경한다.
     *
     * <p>현재 비밀번호를 검증한 뒤 새 비밀번호를 BCrypt 인코딩하여 저장한다.
     * 현재 비밀번호 불일치 시 {@link UserErrorCode#INVALID_CURRENT_PASSWORD} 예외를 던진다.</p>
     *
     * @param userId    본인 사용자 ID (JWT subject)
     * @param currentPw 현재 비밀번호 (평문)
     * @param newPw     새 비밀번호 (평문)
     * @throws RestApiException INVALID_CURRENT_PASSWORD — 현재 비밀번호 불일치
     */
    @Transactional
    public void changeMyPassword(String userId, String currentPw, String newPw) {
        User user = findActiveUser(userId);
        if (!passwordEncoder.matches(currentPw, user.getUserPw())) {
            throw new RestApiException(UserErrorCode.INVALID_CURRENT_PASSWORD);
        }
        user.changePw(passwordEncoder.encode(newPw));
    }

    /**
     * 신규 사용자를 등록한다. 등록자·수정자는 JPA Auditing 이 자동 주입한다.
     *
     * @param dto 등록 요청 DTO
     * @throws RestApiException DUPLICATE_USER_ID — 동일 ID가 이미 존재하는 경우
     */
    @Transactional
    public void registerUser(UserUpsertDto dto) {
        if (userRepository.existsById(dto.getUserId())) {
            throw new RestApiException(UserErrorCode.DUPLICATE_USER_ID);
        }
        String encodedPw = passwordEncoder.encode(dto.getUserPw());
        User user = User.create(dto.getUserId(), dto.getUserNm(), encodedPw, dto.getUserRole(), YnType.Y);
        userRepository.save(user);
    }

    /**
     * 사용자 정보를 수정한다 (비밀번호 제외). 수정자는 JPA Auditing 이 자동 주입한다.
     *
     * @param userId 수정 대상 사용자 ID
     * @param dto    수정 요청 DTO (null 필드는 유지)
     */
    @Transactional
    public void updateUser(String userId, UserUpsertDto dto) {
        User user = findActiveUser(userId);
        user.changeInfo(dto.getUserNm(), dto.getUserRole());
    }

    /**
     * 사용자를 비활성화(논리 삭제)한다. 수정자는 JPA Auditing 이 자동 주입한다.
     *
     * <p>{@code UserDeactivatedEvent}를 발행하며, {@code UserEventHandler}가
     * {@code BEFORE_COMMIT} 시점에 연관 리프레시 토큰을 폐기한다.</p>
     *
     * @param userId 비활성화 대상 사용자 ID
     */
    @Transactional
    public void deactivateUser(String userId) {
        User user = findActiveUser(userId);
        user.deactivate();
        userEventPublisher.deactivateAndPublish(user);
    }

    /**
     * 사용자를 물리 삭제한다.
     *
     * <p>{@code UserDeletedEvent}를 발행하며, {@code UserEventHandler}가
     * {@code BEFORE_COMMIT} 시점에 연관 리프레시 토큰을 먼저 삭제하여 FK 제약 위반을 방지한다.</p>
     *
     * @param userId 삭제 대상 사용자 ID
     * @throws RestApiException USER_NOT_FOUND — 존재하지 않는 사용자 ID
     */
    @Transactional
    public void deleteUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RestApiException(UserErrorCode.USER_NOT_FOUND));
        userEventPublisher.deleteAndPublish(user);
    }
    
    
    @Transactional
    public void changeTtrylUseYn(String userId, YnType ttrylUseYn) {
        User user = findActiveUser(userId);
        user.changeTtrylUseYn(ttrylUseYn);
    }
}
