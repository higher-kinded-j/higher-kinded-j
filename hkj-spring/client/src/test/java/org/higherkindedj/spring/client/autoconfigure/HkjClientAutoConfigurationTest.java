// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.spring.client.ClientErrorResponse;
import org.higherkindedj.spring.client.JsonResponseErrorDecoderFactory;
import org.higherkindedj.spring.client.ResponseErrorDecoder;
import org.higherkindedj.spring.client.ResponseErrorDecoderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

@DisplayName("HkjClientAutoConfiguration")
class HkjClientAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class, HkjClientAutoConfiguration.class));

  @Test
  @DisplayName("contributes a default JSON decoder factory backed by the application's JsonMapper")
  void contributesDefaultFactory() {
    runner.run(
        context ->
            assertThat(context)
                .getBean(ResponseErrorDecoderFactory.class)
                .isInstanceOf(JsonResponseErrorDecoderFactory.class));
  }

  @Test
  @DisplayName("backs off when the application defines its own decoder factory")
  void backsOffForUserBean() {
    ResponseErrorDecoderFactory custom =
        new ResponseErrorDecoderFactory() {
          @Override
          public <E> ResponseErrorDecoder<E> create(Class<E> errorType) {
            return response -> {
              throw new UnsupportedOperationException("custom");
            };
          }
        };

    runner
        .withBean(ResponseErrorDecoderFactory.class, () -> custom)
        .run(
            context ->
                assertThat(context.getBean(ResponseErrorDecoderFactory.class)).isSameAs(custom));
  }

  // Concrete (Jackson-decodable) error hierarchy for the global-mapping tests.
  public static class ApiError {
    public String code;
  }

  public static class NotFound extends ApiError {}

  @Test
  @DisplayName("applies the global hkj.client.status-error-mappings to the default factory")
  void appliesGlobalStatusMapping() {
    runner
        .withPropertyValues("hkj.client.status-error-mappings.404=" + NotFound.class.getName())
        .run(
            context -> {
              ResponseErrorDecoder<ApiError> decoder =
                  context.getBean(ResponseErrorDecoderFactory.class).create(ApiError.class);
              assertThat(
                      decoder.decode(
                          new ClientErrorResponse(HttpStatus.NOT_FOUND, "{\"code\":\"x\"}", null)))
                  .isInstanceOf(NotFound.class);
            });
  }

  @Test
  @DisplayName("fails fast when a configured error type cannot be resolved")
  void failsFastOnUnresolvableType() {
    runner
        .withPropertyValues("hkj.client.status-error-mappings.404=com.example.DoesNotExist")
        .run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"java.lang.Void", "void"})
  @DisplayName("fails fast when a configured error type is Void, naming Unit")
  void failsFastOnVoid(String className) {
    runner
        .withPropertyValues("hkj.client.status-error-mappings.404=" + className)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "hkj.client.status-error-mappings: error type '"
                            + className
                            + "' for status 404 cannot hold a value, but a Left always holds"
                            + " one. Map the status to org.higherkindedj.hkt.Unit, the error type"
                            + " that carries nothing, or remove the mapping."));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"int", "long", "boolean"})
  @DisplayName("fails fast when a configured error type is a primitive, which no error can be")
  void failsFastOnAPrimitive(String className) {
    runner
        .withPropertyValues("hkj.client.status-error-mappings.404=" + className)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "hkj.client.status-error-mappings: error type '"
                            + className
                            + "' for status 404 is a primitive, which no error can be. Map the"
                            + " status to an error class, or remove the mapping."));
  }
}
