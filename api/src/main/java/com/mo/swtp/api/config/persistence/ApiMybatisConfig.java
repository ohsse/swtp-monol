package com.mo.swtp.api.config.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(
        basePackages = "com.mo.swtp",
        annotationClass = ApiMybatisMapper.class
)
public class ApiMybatisConfig {
}
