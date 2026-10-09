package com.mo.swtp.common.domain;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class BaseEntityQuerydslGenerationTest {

    @Test
    void querydslQClassIsGeneratedForBaseEntity() {
        assertNotNull(QBaseEntity.baseEntity);
    }
}
