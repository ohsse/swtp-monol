package com.mo.swtp.api.config.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mo.swtp.common.exception.ErrorCode;
import com.mo.swtp.common.exception.RestApiException;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class RestApiAdviceTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestExceptionController())
            .setControllerAdvice(new RestApiAdvice())
            .build();

    @Test
    void returnsSuccessResponseFromCommonController() throws Exception {
        mockMvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.message").value("ok"));
    }

    @Test
    void mapsRestApiExceptionToDefinedStatusAndCode() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAMPLE_CONFLICT"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void mapsMethodArgumentTypeMismatchToBadRequest() throws Exception {
        mockMvc.perform(get("/test/type").param("count", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void mapsHttpMessageNotReadableToBadRequest() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType("application/json")
                        .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void mapsUnsupportedMethodToMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/success"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void mapsUnhandledExceptionToInternalServerError() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @RestController
    @RequestMapping("/test")
    static class TestExceptionController extends CommonController {

        @GetMapping("/success")
        ResponseEntity<?> success() {
            return getResponseEntity(Map.of("message", "ok"));
        }

        @GetMapping("/business")
        ResponseEntity<?> business() {
            throw new RestApiException(TestErrorCode.SAMPLE_CONFLICT);
        }

        @GetMapping("/type")
        ResponseEntity<?> type(@RequestParam int count) {
            return getResponseEntity(Map.of("count", count));
        }

        @PostMapping("/body")
        ResponseEntity<?> body(@RequestBody Map<String, Object> payload) {
            return getResponseEntity(payload);
        }

        @GetMapping("/unhandled")
        ResponseEntity<?> unhandled() {
            throw new IllegalStateException("boom");
        }
    }

    enum TestErrorCode implements ErrorCode {
        SAMPLE_CONFLICT(409);

        private final int httpStatus;

        TestErrorCode(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        @Override
        public int getHttpStatus() {
            return httpStatus;
        }
    }
}
