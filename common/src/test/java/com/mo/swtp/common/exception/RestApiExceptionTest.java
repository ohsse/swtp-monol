package com.mo.swtp.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class RestApiExceptionTest {

    @Test
    void keepsErrorCodeAndMessage() {
        RestApiException exception = new RestApiException(CommonErrorCode.INVALID_REQUEST);

        assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("INVALID_REQUEST", exception.getMessage());
    }

    @Test
    void keepsCauseWhenProvided() {
        IllegalArgumentException cause = new IllegalArgumentException("bad input");
        RestApiException exception = new RestApiException(CommonErrorCode.INTERNAL_SERVER_ERROR, cause);

        assertSame(CommonErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
        assertSame(cause, exception.getCause());
    }
}
