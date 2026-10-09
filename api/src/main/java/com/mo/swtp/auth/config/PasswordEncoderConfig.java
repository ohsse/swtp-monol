package com.mo.swtp.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCryptPasswordEncoder 빈 등록 설정.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt 기반 비밀번호 인코더를 빈으로 등록한다.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
