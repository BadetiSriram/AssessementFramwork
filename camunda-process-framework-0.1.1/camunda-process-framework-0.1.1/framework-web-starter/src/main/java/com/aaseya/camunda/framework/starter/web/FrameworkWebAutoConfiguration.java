package com.aaseya.camunda.framework.starter.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the framework web starter.
 *
 * <p>Activated when {@code org.springframework.web.bind.annotation.RestControllerAdvice}
 * is on the classpath (i.e., when {@code spring-boot-starter-web} or
 * {@code spring-webmvc} is a dependency) and the opt-out property is not set.
 *
 * <p>Registers one bean:
 * <ul>
 *   <li>{@link GlobalExceptionHandler} — maps framework and Jakarta Validation exceptions
 *       to RFC 7807 {@link org.springframework.http.ProblemDetail} responses.</li>
 * </ul>
 *
 * <h2>Opt-out</h2>
 * <p>Set {@code framework.web.exception-handler-enabled=false} to suppress the
 * {@link GlobalExceptionHandler} bean entirely and supply your own
 * {@code @RestControllerAdvice} instead.
 *
 * <h2>Override without opt-out</h2>
 * <p>Register a {@link GlobalExceptionHandler} bean in your application context before
 * this auto-configuration fires; the {@code @ConditionalOnMissingBean} guard ensures the
 * framework default is skipped automatically.
 *
 * <h2>Usage — minimal controller</h2>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/orders")
 * public class OrderController {
 *
 *     @GetMapping("/{id}")
 *     public Response<OrderDto> get(@PathVariable String id) {
 *         return Response.ok(orderService.find(id));
 *     }
 * }
 * }</pre>
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
@ConditionalOnProperty(
        prefix = "framework.web",
        name = "exception-handler-enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(FrameworkWebProperties.class)
public class FrameworkWebAutoConfiguration {

    /**
     * Registers the framework global exception handler.
     *
     * <p>Back-off: if the application registers its own {@link GlobalExceptionHandler}
     * bean, this one is skipped entirely.
     *
     * @return the configured exception handler
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler frameworkGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
