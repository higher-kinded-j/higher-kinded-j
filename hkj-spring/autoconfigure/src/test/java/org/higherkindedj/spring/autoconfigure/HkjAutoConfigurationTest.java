// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for {@link HkjAutoConfiguration}.
 *
 * <p>Uses Spring Boot's ApplicationContextRunner to test auto-configuration behavior with different
 * property combinations.
 */
@DisplayName("HkjAutoConfiguration Tests")
class HkjAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  HkjAutoConfiguration.class,
                  HkjJacksonAutoConfiguration.class,
                  JacksonAutoConfiguration.class));

  @Nested
  @DisplayName("Properties Loading Tests")
  class PropertiesLoadingTests {

    @Test
    @DisplayName("Should load default properties when no configuration provided")
    void shouldLoadDefaultProperties() {
      contextRunner.run(
          context -> {
            assertThat(context).hasSingleBean(HkjProperties.class);

            HkjProperties properties = context.getBean(HkjProperties.class);

            // Verify web defaults (using new Path API property names)
            assertThat(properties.getWeb().isEitherPathEnabled()).isTrue();
            assertThat(properties.getWeb().isValidationPathEnabled()).isTrue();
            assertThat(properties.getWeb().getDefaultErrorStatus()).isEqualTo(400);

            // Verify JSON defaults
            assertThat(properties.getJson().isCustomSerializersEnabled()).isTrue();

            // Verify async defaults
            assertThat(properties.getAsync().getDefaultTimeoutMs()).isEqualTo(30000);

            // Verify effect-boundary defaults
            assertThat(properties.getEffectBoundary().isEnabled()).isTrue();
          });
    }

    @Test
    @DisplayName("Should load custom web properties")
    void shouldLoadCustomWebProperties() {
      contextRunner
          .withPropertyValues(
              "hkj.web.either-path-enabled=false",
              "hkj.web.validation-path-enabled=false",
              "hkj.web.default-error-status=500",
              "hkj.web.error-status-mappings.UserNotFoundError=404",
              "hkj.web.error-status-mappings.ValidationError=400")
          .run(
              context -> {
                HkjProperties properties = context.getBean(HkjProperties.class);

                assertThat(properties.getWeb().isEitherPathEnabled()).isFalse();
                assertThat(properties.getWeb().isValidationPathEnabled()).isFalse();
                assertThat(properties.getWeb().getDefaultErrorStatus()).isEqualTo(500);
                assertThat(properties.getWeb().getErrorStatusMappings())
                    .containsEntry("UserNotFoundError", 404)
                    .containsEntry("ValidationError", 400);
              });
    }

    @Test
    @DisplayName("Should load custom JSON properties")
    void shouldLoadCustomJsonProperties() {
      contextRunner
          .withPropertyValues("hkj.json.custom-serializers-enabled=false")
          .run(
              context -> {
                HkjProperties properties = context.getBean(HkjProperties.class);

                assertThat(properties.getJson().isCustomSerializersEnabled()).isFalse();
              });
    }

    @Test
    @DisplayName("Should load custom async properties")
    void shouldLoadCustomAsyncProperties() {
      contextRunner
          .withPropertyValues("hkj.async.default-timeout-ms=60000")
          .run(
              context -> {
                HkjProperties properties = context.getBean(HkjProperties.class);

                assertThat(properties.getAsync().getDefaultTimeoutMs()).isEqualTo(60000);
              });
    }

    @Test
    @DisplayName("Should load custom effect-boundary properties")
    void shouldLoadCustomEffectBoundaryProperties() {
      contextRunner
          .withPropertyValues("hkj.effect-boundary.enabled=false")
          .run(
              context -> {
                HkjProperties properties = context.getBean(HkjProperties.class);

                assertThat(properties.getEffectBoundary().isEnabled()).isFalse();
              });
    }
  }

  @Nested
  @DisplayName("Bean Registration Tests")
  class BeanRegistrationTests {

    @Test
    @DisplayName("Should register HkjProperties bean")
    void shouldRegisterHkjPropertiesBean() {
      contextRunner.run(
          context -> {
            assertThat(context).hasSingleBean(HkjProperties.class);
          });
    }

    @Test
    @DisplayName("Should register JsonMapper when custom serializers enabled")
    void shouldRegisterJsonMapperWithModule() {
      contextRunner
          .withPropertyValues("hkj.json.custom-serializers-enabled=true")
          .run(
              context -> {
                assertThat(context).hasSingleBean(JsonMapper.class);
              });
    }

    @Test
    @DisplayName("Should still have JsonMapper when custom serializers disabled")
    void shouldHaveJsonMapperWhenDisabled() {
      contextRunner
          .withPropertyValues("hkj.json.custom-serializers-enabled=false")
          .run(
              context -> {
                // JsonMapper should still exist from JacksonAutoConfiguration
                assertThat(context).hasSingleBean(JsonMapper.class);
              });
    }
  }

  @Nested
  @DisplayName("Complete Configuration Tests")
  class CompleteConfigurationTests {

    @Test
    @DisplayName("Should load all properties from complete configuration")
    void shouldLoadCompleteConfiguration() {
      contextRunner
          .withPropertyValues(
              // Web (using new Path API property names)
              "hkj.web.either-path-enabled=true",
              "hkj.web.validation-path-enabled=true",
              "hkj.web.default-error-status=400",
              "hkj.web.error-status-mappings.UserNotFoundError=404",
              "hkj.web.error-status-mappings.ValidationError=400",
              "hkj.web.error-status-mappings.AuthorizationError=403",

              // JSON
              "hkj.json.custom-serializers-enabled=true",

              // Async
              "hkj.async.default-timeout-ms=30000")
          .run(
              context -> {
                HkjProperties properties = context.getBean(HkjProperties.class);

                // Verify all properties are loaded correctly
                assertThat(properties.getWeb().isEitherPathEnabled()).isTrue();
                assertThat(properties.getWeb().isValidationPathEnabled()).isTrue();
                assertThat(properties.getWeb().getDefaultErrorStatus()).isEqualTo(400);
                assertThat(properties.getWeb().getErrorStatusMappings())
                    .hasSize(3)
                    .containsEntry("UserNotFoundError", 404);

                assertThat(properties.getJson().isCustomSerializersEnabled()).isTrue();

                assertThat(properties.getAsync().getDefaultTimeoutMs()).isEqualTo(30000);
              });
    }
  }
}
