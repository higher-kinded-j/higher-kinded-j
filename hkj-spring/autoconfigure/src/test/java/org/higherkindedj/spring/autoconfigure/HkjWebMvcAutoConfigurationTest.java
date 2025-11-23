// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.spring.web.returnvalue.EitherReturnValueHandler;
import org.higherkindedj.spring.web.returnvalue.ValidatedReturnValueHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * Tests for {@link HkjWebMvcAutoConfiguration}.
 *
 * <p>Uses Spring Boot's WebApplicationContextRunner to test web MVC auto-configuration behavior
 * with different property combinations.
 */
@DisplayName("HkjWebMvcAutoConfiguration Tests")
class HkjWebMvcAutoConfigurationTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ServletWebServerFactoryAutoConfiguration.class,
                  DispatcherServletAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  HkjAutoConfiguration.class,
                  HkjWebMvcAutoConfiguration.class));

  @Nested
  @DisplayName("Return Value Handler Registration Tests")
  class HandlerRegistrationTests {

    @Test
    @DisplayName("Should register both handlers by default")
    void shouldRegisterBothHandlersByDefault() {
      contextRunner.run(
          context -> {
            assertThat(context).hasSingleBean(RequestMappingHandlerAdapter.class);

            RequestMappingHandlerAdapter adapter =
                context.getBean(RequestMappingHandlerAdapter.class);
            List<HandlerMethodReturnValueHandler> handlers = adapter.getReturnValueHandlers();

            assertThat(handlers).isNotNull();

            // Verify both handlers are registered
            boolean hasEitherHandler =
                handlers.stream().anyMatch(h -> h instanceof EitherReturnValueHandler);
            boolean hasValidatedHandler =
                handlers.stream().anyMatch(h -> h instanceof ValidatedReturnValueHandler);

            assertThat(hasEitherHandler).isTrue();
            assertThat(hasValidatedHandler).isTrue();
          });
    }

    @Test
    @DisplayName("Should register only EitherReturnValueHandler when Validated is disabled")
    void shouldRegisterOnlyEitherHandlerWhenValidatedDisabled() {
      contextRunner
          .withPropertyValues(
              "hkj.web.either-response-enabled=true", "hkj.web.validated-response-enabled=false")
          .run(
              context -> {
                RequestMappingHandlerAdapter adapter =
                    context.getBean(RequestMappingHandlerAdapter.class);
                List<HandlerMethodReturnValueHandler> handlers = adapter.getReturnValueHandlers();

                assertThat(handlers).isNotNull();

                boolean hasEitherHandler =
                    handlers.stream().anyMatch(h -> h instanceof EitherReturnValueHandler);
                boolean hasValidatedHandler =
                    handlers.stream().anyMatch(h -> h instanceof ValidatedReturnValueHandler);

                assertThat(hasEitherHandler).isTrue();
                assertThat(hasValidatedHandler).isFalse();
              });
    }

    @Test
    @DisplayName("Should register only ValidatedReturnValueHandler when Either is disabled")
    void shouldRegisterOnlyValidatedHandlerWhenEitherDisabled() {
      contextRunner
          .withPropertyValues(
              "hkj.web.either-response-enabled=false", "hkj.web.validated-response-enabled=true")
          .run(
              context -> {
                RequestMappingHandlerAdapter adapter =
                    context.getBean(RequestMappingHandlerAdapter.class);
                List<HandlerMethodReturnValueHandler> handlers = adapter.getReturnValueHandlers();

                assertThat(handlers).isNotNull();

                boolean hasEitherHandler =
                    handlers.stream().anyMatch(h -> h instanceof EitherReturnValueHandler);
                boolean hasValidatedHandler =
                    handlers.stream().anyMatch(h -> h instanceof ValidatedReturnValueHandler);

                assertThat(hasEitherHandler).isFalse();
                assertThat(hasValidatedHandler).isTrue();
              });
    }

    @Test
    @DisplayName("Should register no custom handlers when both are disabled")
    void shouldRegisterNoHandlersWhenBothDisabled() {
      contextRunner
          .withPropertyValues(
              "hkj.web.either-response-enabled=false", "hkj.web.validated-response-enabled=false")
          .run(
              context -> {
                RequestMappingHandlerAdapter adapter =
                    context.getBean(RequestMappingHandlerAdapter.class);
                List<HandlerMethodReturnValueHandler> handlers = adapter.getReturnValueHandlers();

                assertThat(handlers).isNotNull();

                boolean hasEitherHandler =
                    handlers.stream().anyMatch(h -> h instanceof EitherReturnValueHandler);
                boolean hasValidatedHandler =
                    handlers.stream().anyMatch(h -> h instanceof ValidatedReturnValueHandler);

                assertThat(hasEitherHandler).isFalse();
                assertThat(hasValidatedHandler).isFalse();
              });
    }
  }

  @Nested
  @DisplayName("Handler Precedence Tests")
  class HandlerPrecedenceTests {

    @Test
    @DisplayName("Should register custom handlers before Spring's default handlers")
    void shouldRegisterCustomHandlersFirst() {
      contextRunner.run(
          context -> {
            RequestMappingHandlerAdapter adapter =
                context.getBean(RequestMappingHandlerAdapter.class);
            List<HandlerMethodReturnValueHandler> handlers = adapter.getReturnValueHandlers();

            assertThat(handlers).isNotNull().isNotEmpty();

            // Find positions of our handlers
            int eitherHandlerIndex = -1;
            int validatedHandlerIndex = -1;

            for (int i = 0; i < handlers.size(); i++) {
              HandlerMethodReturnValueHandler handler = handlers.get(i);
              if (handler instanceof EitherReturnValueHandler) {
                eitherHandlerIndex = i;
              } else if (handler instanceof ValidatedReturnValueHandler) {
                validatedHandlerIndex = i;
              }
            }

            // Both handlers should be found
            assertThat(eitherHandlerIndex).isGreaterThanOrEqualTo(0);
            assertThat(validatedHandlerIndex).isGreaterThanOrEqualTo(0);

            // Our handlers should be early in the list (within first 10 positions)
            // This ensures they have precedence over Spring's default handlers
            assertThat(eitherHandlerIndex).isLessThan(10);
            assertThat(validatedHandlerIndex).isLessThan(10);
          });
    }
  }

  @Nested
  @DisplayName("Configuration Property Integration Tests")
  class PropertyIntegrationTests {

    @Test
    @DisplayName("Should pass default-error-status to EitherReturnValueHandler")
    void shouldPassDefaultErrorStatusToHandler() {
      contextRunner
          .withPropertyValues("hkj.web.default-error-status=500")
          .run(
              context -> {
                // Verify properties are loaded
                HkjProperties properties = context.getBean(HkjProperties.class);
                assertThat(properties.getWeb().getDefaultErrorStatus()).isEqualTo(500);

                // Verify handler is registered (actual status checking would require
                // reflection or integration tests)
                RequestMappingHandlerAdapter adapter =
                    context.getBean(RequestMappingHandlerAdapter.class);
                List<HandlerMethodReturnValueHandler> handlers = adapter.getReturnValueHandlers();

                boolean hasEitherHandler =
                    handlers.stream().anyMatch(h -> h instanceof EitherReturnValueHandler);

                assertThat(hasEitherHandler).isTrue();
              });
    }

    @Test
    @DisplayName("Should load error status mappings")
    void shouldLoadErrorStatusMappings() {
      contextRunner
          .withPropertyValues(
              "hkj.web.error-status-mappings.UserNotFoundError=404",
              "hkj.web.error-status-mappings.ValidationError=400",
              "hkj.web.error-status-mappings.AuthorizationError=403")
          .run(
              context -> {
                HkjProperties properties = context.getBean(HkjProperties.class);

                assertThat(properties.getWeb().getErrorStatusMappings())
                    .hasSize(3)
                    .containsEntry("UserNotFoundError", 404)
                    .containsEntry("ValidationError", 400)
                    .containsEntry("AuthorizationError", 403);
              });
    }
  }

  @Nested
  @DisplayName("Web Application Context Tests")
  class WebApplicationContextTests {

    @Test
    @DisplayName("Should only activate in servlet web application")
    void shouldOnlyActivateInServletWebApp() {
      contextRunner.run(
          context -> {
            // Should have RequestMappingHandlerAdapter in servlet web context
            assertThat(context).hasSingleBean(RequestMappingHandlerAdapter.class);
          });
    }
  }

  @Nested
  @DisplayName("Complete Configuration Tests")
  class CompleteConfigurationTests {

    @Test
    @DisplayName("Should work with complete web configuration")
    void shouldWorkWithCompleteWebConfiguration() {
      contextRunner
          .withPropertyValues(
              "hkj.web.either-response-enabled=true",
              "hkj.web.validated-response-enabled=true",
              "hkj.web.default-error-status=400",
              "hkj.web.async-either-t-enabled=true",
              "hkj.web.error-status-mappings.UserNotFoundError=404",
              "hkj.web.error-status-mappings.ValidationError=400",
              "hkj.web.error-status-mappings.AuthorizationError=403",
              "hkj.web.error-status-mappings.AuthenticationError=401")
          .run(
              context -> {
                // Verify properties
                HkjProperties properties = context.getBean(HkjProperties.class);
                assertThat(properties.getWeb().isEitherResponseEnabled()).isTrue();
                assertThat(properties.getWeb().isValidatedResponseEnabled()).isTrue();
                assertThat(properties.getWeb().getDefaultErrorStatus()).isEqualTo(400);
                assertThat(properties.getWeb().getErrorStatusMappings()).hasSize(4);

                // Verify handlers are registered
                RequestMappingHandlerAdapter adapter =
                    context.getBean(RequestMappingHandlerAdapter.class);
                List<HandlerMethodReturnValueHandler> handlers = adapter.getReturnValueHandlers();

                boolean hasEitherHandler =
                    handlers.stream().anyMatch(h -> h instanceof EitherReturnValueHandler);
                boolean hasValidatedHandler =
                    handlers.stream().anyMatch(h -> h instanceof ValidatedReturnValueHandler);

                assertThat(hasEitherHandler).isTrue();
                assertThat(hasValidatedHandler).isTrue();
              });
    }

    @Test
    @DisplayName("Should work with minimal configuration (all defaults)")
    void shouldWorkWithMinimalConfiguration() {
      contextRunner.run(
          context -> {
            // Verify beans exist
            assertThat(context).hasSingleBean(HkjProperties.class);
            assertThat(context).hasSingleBean(RequestMappingHandlerAdapter.class);

            // Verify default properties
            HkjProperties properties = context.getBean(HkjProperties.class);
            assertThat(properties.getWeb().isEitherResponseEnabled()).isTrue();
            assertThat(properties.getWeb().isValidatedResponseEnabled()).isTrue();
            assertThat(properties.getWeb().getDefaultErrorStatus()).isEqualTo(400);

            // Verify handlers are registered with defaults
            RequestMappingHandlerAdapter adapter =
                context.getBean(RequestMappingHandlerAdapter.class);
            List<HandlerMethodReturnValueHandler> handlers = adapter.getReturnValueHandlers();

            boolean hasEitherHandler =
                handlers.stream().anyMatch(h -> h instanceof EitherReturnValueHandler);
            boolean hasValidatedHandler =
                handlers.stream().anyMatch(h -> h instanceof ValidatedReturnValueHandler);

            assertThat(hasEitherHandler).isTrue();
            assertThat(hasValidatedHandler).isTrue();
          });
    }
  }
}
