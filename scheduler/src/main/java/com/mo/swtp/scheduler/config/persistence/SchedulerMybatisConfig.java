package com.mo.swtp.scheduler.config.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(
        basePackages = "com.mo.swtp",
        annotationClass = SchedulerMybatisMapper.class
)
public class SchedulerMybatisConfig {
}
