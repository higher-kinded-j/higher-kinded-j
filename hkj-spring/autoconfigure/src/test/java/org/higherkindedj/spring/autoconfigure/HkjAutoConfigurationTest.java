// Copyright (c) 2025 Magnus Smith
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

            // Verify validation defaults
            assertThat(properties.getValidation().isEnabled()).isTrue();
            assertThat(properties.getValidation().isAccumulateErrors()).isTrue();
            assertThat(properties.getValidation().getMaxErrors()).isEqualTo(0);

            // Verify JSON defaults
            assertThat(properties.getJson().isCustomSerializersEnabled()).isTrue();
            assertThat(properties.getJson().getEitherFormat())
                .isEqualTo(HkjProperties.Jackson.SerializationFormat.TAGGED);

            // Verify async defaults
            assertThat(properties.getAsync().getExecutorCorePoolSize()).isEqualTo(10);
            assertThat(properties.getAsync().getExecutorMaxPoolSize()).isEqualTo(20);
            assertThat(properties.getAsync().getExecutorQueueCapacity()).isEqualTo(100);
            assertThat(properties.getAsync().getExecutorThreadNamePrefix()).isEqualTo("hkj-async-");
            assertThat(properties.getAsync().getDefaultTimeoutMs()).isEqualTo(30000);
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
          .withPropertyValues(
              "hkj.json.custom-serializers-enabled=false", "hkj.json.either-format=SIMPLE")
          .run(
              context -> {
                HkjProperties properties = context.getBean(HkjProperties.class);

                assertThat(properties.getJson().isCustomSerializersEnabled()).isFalse();
                assertThat(properties.getJson().getEitherFormat())
                    .isEqualTo(HkjProperties.Jackson.SerializationFormat.SIMPLE);
              });
    }

    @Test
    @DisplayName("Should load custom validation properties")
    void shouldLoadCustomValidationProperties() {
      contextRunner
          .withPropertyValues(
              "hkj.validation.enabled=false",
              "hkj.validation.accumulate-errors=false",
              "hkj.validation.max-errors=10")
          .run(
              context -> {
                HkjProperties properties = context.getBean(HkjProperties.class);

                assertThat(properties.getValidation().isEnabled()).isFalse();
                assertThat(properties.getValidation().isAccumulateErrors()).isFalse();
                assertThat(properties.getValidation().getMaxErrors()).isEqualTo(10);
              });
    }

    @Test
    @DisplayName("Should load custom async properties")
    void shouldLoadCustomAsyncProperties() {
      contextRunner
          .withPropertyValues(
              "hkj.async.executor-core-pool-size=20",
              "hkj.async.executor-max-pool-size=50",
              "hkj.async.executor-queue-capacity=500",
              "hkj.async.executor-thread-name-prefix=custom-async-",
              "hkj.async.default-timeout-ms=60000")
          .run(
              context -> {
                HkjProperties properties = context.getBean(HkjProperties.class);

                assertThat(properties.getAsync().getExecutorCorePoolSize()).isEqualTo(20);
                assertThat(properties.getAsync().getExecutorMaxPoolSize()).isEqualTo(50);
                assertThat(properties.getAsync().getExecutorQueueCapacity()).isEqualTo(500);
                assertThat(properties.getAsync().getExecutorThreadNamePrefix())
                    .isEqualTo("custom-async-");
                assertThat(properties.getAsync().getDefaultTimeoutMs()).isEqualTo(60000);
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

              // Validation
              "hkj.validation.enabled=true",
              "hkj.validation.accumulate-errors=true",
              "hkj.validation.max-errors=0",

              // JSON
              "hkj.json.custom-serializers-enabled=true",
              "hkj.json.either-format=TAGGED",

              // Async
              "hkj.async.executor-core-pool-size=10",
              "hkj.async.executor-max-pool-size=20",
              "hkj.async.executor-queue-capacity=100",
              "hkj.async.executor-thread-name-prefix=hkj-async-",
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

                assertThat(properties.getValidation().isEnabled()).isTrue();
                assertThat(properties.getValidation().isAccumulateErrors()).isTrue();
                assertThat(properties.getValidation().getMaxErrors()).isEqualTo(0);

                assertThat(properties.getJson().isCustomSerializersEnabled()).isTrue();
                assertThat(properties.getJson().getEitherFormat())
                    .isEqualTo(HkjProperties.Jackson.SerializationFormat.TAGGED);

                assertThat(properties.getAsync().getExecutorCorePoolSize()).isEqualTo(10);
                assertThat(properties.getAsync().getExecutorMaxPoolSize()).isEqualTo(20);
                assertThat(properties.getAsync().getExecutorQueueCapacity()).isEqualTo(100);
                assertThat(properties.getAsync().getExecutorThreadNamePrefix())
                    .isEqualTo("hkj-async-");
                assertThat(properties.getAsync().getDefaultTimeoutMs()).isEqualTo(30000);
              });
    }
  }
}
