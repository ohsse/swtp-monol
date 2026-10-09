package com.mo.swtp.api.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ApiPersistenceConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ApiMybatisConfig.class, ApiQuerydslConfig.class, TestEntityManagerConfig.class);

    @Test
    void registersQuerydslFactoryAndMybatisConfig() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ApiMybatisConfig.class);
            assertThat(context).hasSingleBean(JPAQueryFactory.class);
            assertThat(context).hasSingleBean(EntityManager.class);
        });
    }

    @Configuration
    static class TestEntityManagerConfig {

        @Bean
        EntityManager entityManager() {
            return mock(EntityManager.class);
        }
    }
}
