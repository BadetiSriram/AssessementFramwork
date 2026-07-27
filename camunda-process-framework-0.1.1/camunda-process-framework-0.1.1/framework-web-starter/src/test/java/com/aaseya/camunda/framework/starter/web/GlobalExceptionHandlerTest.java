package com.aaseya.camunda.framework.starter.web;

import com.aaseya.camunda.framework.core.exception.BusinessException;
import com.aaseya.camunda.framework.core.exception.NonRetryableException;
import com.aaseya.camunda.framework.core.exception.RetryableException;
import com.aaseya.camunda.framework.core.mdc.MdcKeys;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc standalone tests for {@link GlobalExceptionHandler}.
 *
 * <p>Each test exercises a specific exception mapping through the full Spring MVC
 * dispatch pipeline using {@link MockMvcBuilders#standaloneSetup} so that no
 * Spring application context is required.
 *
 * <p>Spring's {@link org.springframework.http.ProblemDetail} serialises extension
 * properties ({@code setProperty}) at the <em>top level</em> of the JSON object per
 * RFC 7807, not nested under a {@code "properties"} key.  All JSON path assertions
 * therefore use {@code $.<propertyName>} (e.g. {@code $.errorCode}).
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.remove(MdcKeys.CORRELATION_ID);
    }

    // -------------------------------------------------------------------------
    // BusinessException → 422
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("BusinessException returns 422 with errorCode in problem detail")
    void businessException_returns422_withErrorCode() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business rule violation"))
                .andExpect(jsonPath("$.detail").value("Order already shipped"))
                .andExpect(jsonPath("$.errorCode").value("ORDER_ALREADY_SHIPPED"));
    }

    // -------------------------------------------------------------------------
    // RetryableException → 503
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("RetryableException returns 503 with Retry-After header")
    void retryableException_returns503_withRetryAfterHeader() throws Exception {
        mockMvc.perform(get("/test/retryable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.title").value("Service temporarily unavailable"))
                .andExpect(jsonPath("$.errorCode").value("DB_TIMEOUT"));
    }

    // -------------------------------------------------------------------------
    // NonRetryableException → 500
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("NonRetryableException returns 500 with errorCode in problem detail")
    void nonRetryableException_returns500_withErrorCode() throws Exception {
        mockMvc.perform(get("/test/non-retryable"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal error"))
                .andExpect(jsonPath("$.errorCode").value("SCHEMA_MISMATCH"));
    }

    // -------------------------------------------------------------------------
    // MethodArgumentNotValidException → 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MethodArgumentNotValidException returns 400 with fieldErrors array")
    void methodArgumentNotValid_returns400_withFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    // -------------------------------------------------------------------------
    // ConstraintViolationException → 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ConstraintViolationException returns 400 with violations array")
    void constraintViolation_returns400_withViolations() throws Exception {
        mockMvc.perform(get("/test/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    // -------------------------------------------------------------------------
    // Generic Exception → 500 (no leak)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Unexpected exception returns 500 without leaking exception message")
    void unexpectedException_returns500_withoutLeakingMessage() throws Exception {
        mockMvc.perform(get("/test/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Unexpected error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    // -------------------------------------------------------------------------
    // correlationId echoed from MDC
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("correlationId from MDC is echoed in ProblemDetail as top-level property")
    void correlationIdFromMdc_isEchoedInProblemDetail() throws Exception {
        MDC.put(MdcKeys.CORRELATION_ID, "test-corr-777");

        mockMvc.perform(get("/test/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.correlationId").value("test-corr-777"));
    }

    // -------------------------------------------------------------------------
    // Nested test controller
    // -------------------------------------------------------------------------

    /** DTO used by the validation endpoint. */
    static class TestDto {

        @NotBlank(message = "name must not be blank")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * Minimal controller wired into the standalone MockMvc setup.
     * Each endpoint deliberately throws a specific exception to drive the handler tests.
     */
    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business")
        public String throwBusiness() {
            throw new BusinessException("ORDER_ALREADY_SHIPPED", "Order already shipped");
        }

        @GetMapping("/retryable")
        public String throwRetryable() {
            throw new RetryableException("DB_TIMEOUT", "Database connection timed out");
        }

        @GetMapping("/non-retryable")
        public String throwNonRetryable() {
            throw new NonRetryableException("SCHEMA_MISMATCH", "Column type mismatch detected");
        }

        @PostMapping("/validate")
        public String validateBody(@Valid @RequestBody TestDto dto) {
            return dto.getName();
        }

        @GetMapping("/constraint")
        public String throwConstraintViolation() {
            // Throw directly to exercise the ConstraintViolationException handler.
            throw new ConstraintViolationException("manual constraint violation", Set.of());
        }

        @GetMapping("/generic")
        public String throwGeneric() {
            throw new IllegalStateException("secret internal detail that must not leak");
        }
    }
}
