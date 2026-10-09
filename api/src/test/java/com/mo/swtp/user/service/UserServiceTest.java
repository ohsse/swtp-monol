package com.mo.swtp.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mo.swtp.auth.domain.RefreshToken;
import com.mo.swtp.auth.repository.RefreshTokenRepository;
import com.mo.swtp.auth.service.JwtTokenManagementService;
import com.mo.swtp.auth.web.ApiErrorResponseWriter;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.user.domain.User;
import com.mo.swtp.user.domain.UserRole;
import com.mo.swtp.user.dto.UserUpsertDto;
import com.mo.swtp.user.exception.UserErrorCode;
import com.mo.swtp.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserService 통합 테스트 — 실제 PostgreSQL 연결 사용.
 *
 * <p>WebEnvironment.NONE: 웹 서버 없이 서비스·JPA 레이어만 기동.
 * 각 테스트는 @Transactional로 격리되며 테스트 종료 후 자동 롤백된다.</p>
 *
 * <p>JPA Auditing이 활성화되어 있으며, HTTP 요청 컨텍스트 부재 시
 * {@code ApiAuditorAware}가 {@code "SYSTEM"}을 auditor로 반환한다.</p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    /** 서비스 테스트와 무관한 웹 레이어 빈이므로 mock 으로 대체해 컨텍스트 부담을 줄인다. */
    @MockitoBean
    private ApiErrorResponseWriter apiErrorResponseWriter;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenManagementService jwtTokenManagementService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private UserUpsertDto buildDto(String userId, String userNm, String userPw, UserRole role) {
        UserUpsertDto dto = new UserUpsertDto();
        dto.setUserId(userId);
        dto.setUserNm(userNm);
        dto.setUserPw(userPw);
        dto.setUserRole(role);
        return dto;
    }

    @Test
    void 중복_사용자ID_등록_시_예외가_발생한다() {
        userService.registerUser(buildDto("testuser", "테스트유저", "password", UserRole.USER));

        assertThatThrownBy(() -> userService.registerUser(
                buildDto("testuser", "테스트유저2", "password2", UserRole.USER)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(UserErrorCode.DUPLICATE_USER_ID);
    }

    @Test
    void 신규_사용자_등록_시_비밀번호가_인코딩된다() {
        userService.registerUser(buildDto("user01", "테스트유저", "raw-password", UserRole.USER));

        User saved = userRepository.findByUserIdAndUseYn("user01", YnType.Y).orElseThrow();
        assertThat(saved.getUserPw()).startsWith("$2a$");
        assertThat(saved.getUserPw()).isNotEqualTo("raw-password");
    }

    @Test
    void 비활성_사용자_조회_시_예외가_발생한다() {
        assertThatThrownBy(() -> userService.findActiveUser("nonexistent"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    /**
     * Persistable 구현으로 em.persist() 경로를 따르므로 신규 저장 후 rgstrDtm == updtDtm 이 보장된다.
     * JPA Auditing이 활성화되어 auditor는 "SYSTEM"(요청 컨텍스트 부재 시 fallback)으로 주입된다.
     */
    @Test
    void 신규_사용자_등록_후_감사_필드가_설정된다() {
        userService.registerUser(buildDto("user03", "감사필드유저", "password", UserRole.USER));

        User saved = userRepository.findByUserIdAndUseYn("user03", YnType.Y).orElseThrow();
        assertThat(saved.getRgstrDtm()).isNotNull();
        assertThat(saved.getUpdtDtm()).isNotNull();
        assertThat(saved.getRgstrDtm()).isEqualTo(saved.getUpdtDtm());
        assertThat(saved.getRgstrId()).isEqualTo("SYSTEM");
        assertThat(saved.getUpdtId()).isEqualTo("SYSTEM");
    }

    /**
     * admin 사용자를 실제 DB에 저장하고 커밋한다 — DB 연결 확인용.
     *
     * <p>@Commit으로 트랜잭션을 롤백하지 않아 테스트 종료 후 DB에 레코드가 영속된다.
     * 재실행 시에도 안전하도록 시작 전 기존 admin 레코드를 먼저 제거한다.</p>
     */
    @Test
    void admin_사용자를_실제_DB에_저장한다() {
        userRepository.findById("admin").ifPresent(userRepository::delete);

        userService.registerUser(buildDto("admin", "관리자", "admin", UserRole.ADMIN));

        User saved = userRepository.findByUserIdAndUseYn("admin", YnType.Y).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo("admin");
        assertThat(saved.getUserNm()).isEqualTo("관리자");
        assertThat(saved.getUserRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getUserPw()).startsWith("$2a$");
        assertThat(saved.getUserPw()).isNotEqualTo("admin");
        assertThat(saved.getUseYn()).isEqualTo(YnType.Y);
    }

    @Test
    void 사용자_비활성화_후_조회하면_예외가_발생한다() {
        userService.registerUser(buildDto("user02", "테스트유저", "password", UserRole.USER));
        userService.deactivateUser("user02");

        assertThatThrownBy(() -> userService.findActiveUser("user02"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    /**
     * @TransactionalEventListener(BEFORE_COMMIT)은 트랜잭션이 실제로 커밋될 때만 동작한다.
     * 클래스 레벨 @Transactional은 롤백되므로 NOT_SUPPORTED로 비활성화하고,
     * 각 서비스 호출이 자체 트랜잭션을 커밋하도록 한다.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 사용자_비활성화_시_리프레시_토큰이_폐기된다() {
        refreshTokenRepository.deleteByUserId("user10");
        userRepository.deleteById("user10");

        userService.registerUser(buildDto("user10", "이벤트유저", "password", UserRole.USER));
        jwtTokenManagementService.issueTokens("user10", Map.of("role", "USER"));

        userService.deactivateUser("user10");

        RefreshToken token = refreshTokenRepository.findByUserId("user10").orElseThrow();
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokeDtm()).isNotNull();

        refreshTokenRepository.deleteByUserId("user10");
        userRepository.deleteById("user10");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 사용자_물리삭제_시_리프레시_토큰도_삭제된다() {
        refreshTokenRepository.deleteByUserId("user11");
        userRepository.deleteById("user11");

        userService.registerUser(buildDto("user11", "삭제유저", "password", UserRole.USER));
        jwtTokenManagementService.issueTokens("user11", Map.of("role", "USER"));

        assertThat(refreshTokenRepository.findByUserId("user11")).isPresent();

        userService.deleteUser("user11");

        assertThat(userRepository.findById("user11")).isEmpty();
        assertThat(refreshTokenRepository.findByUserId("user11")).isEmpty();
    }

    @Test
    void 존재하지_않는_사용자_물리삭제_시_예외가_발생한다() {
        assertThatThrownBy(() -> userService.deleteUser("nonexistent"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 전체_사용자_목록을_useYn_DESC_userId_ASC_순으로_조회한다() {
        userService.registerUser(buildDto("c_user", "활성C", "pw", UserRole.USER));
        userService.registerUser(buildDto("a_user", "활성A", "pw", UserRole.ADMIN));
        userService.registerUser(buildDto("b_user", "비활성B", "pw", UserRole.USER));
        userService.deactivateUser("b_user");

        List<User> users = userService.findAllUsers();

        // 본 테스트는 admin·c_user·a_user·b_user 외에 다른 활성 사용자가 있을 수 있으므로
        // 필터링 후 본 테스트 범위 ID 만으로 정렬을 검증한다.
        List<String> orderedIds = users.stream()
                .map(User::getUserId)
                .filter(id -> id.equals("a_user") || id.equals("b_user") || id.equals("c_user"))
                .toList();

        // 활성 (a_user, c_user) 가 비활성 (b_user) 보다 먼저 — 활성 내에서는 userId ASC
        assertThat(orderedIds).containsExactly("a_user", "c_user", "b_user");
    }

    @Test
    void 본인_이름_변경_시_userNm_이_갱신되고_userRole_은_불변이다() {
        userService.registerUser(buildDto("self_nm", "이전이름", "pw", UserRole.USER));

        userService.changeMyProfile("self_nm", "새이름");

        User updated = userRepository.findByUserIdAndUseYn("self_nm", YnType.Y).orElseThrow();
        assertThat(updated.getUserNm()).isEqualTo("새이름");
        assertThat(updated.getUserRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void 본인_비밀번호_변경_시_현재_비밀번호_불일치하면_INVALID_CURRENT_PASSWORD_예외가_발생한다() {
        userService.registerUser(buildDto("self_pw_fail", "유저", "correct-pw", UserRole.USER));

        assertThatThrownBy(() -> userService.changeMyPassword("self_pw_fail", "wrong-pw", "new-pw"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(UserErrorCode.INVALID_CURRENT_PASSWORD);
    }

    @Test
    void 본인_비밀번호_변경_성공_시_새_비밀번호로_매칭된다() {
        userService.registerUser(buildDto("self_pw_ok", "유저", "raw-pw", UserRole.USER));

        userService.changeMyPassword("self_pw_ok", "raw-pw", "new-pw");

        User updated = userRepository.findByUserIdAndUseYn("self_pw_ok", YnType.Y).orElseThrow();
        assertThat(updated.getUserPw()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("new-pw", updated.getUserPw())).isTrue();
        assertThat(passwordEncoder.matches("raw-pw", updated.getUserPw())).isFalse();
    }
}
