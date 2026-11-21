// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("JsonResponseErrorDecoderFactory global status mapping")
class JsonResponseErrorDecoderFactoryTest {

  // Concrete (Jackson-decodable) error hierarchy.
  static class ApiError {
    public String code;
  }

  static class NotFound extends ApiError {}

  static class RateLimited extends ApiError {}

  private final JsonMapper mapper = JsonMapper.builder().build();

  private static ClientErrorResponse response(HttpStatus status) {
    return new ClientErrorResponse(status, "{\"code\":\"x\"}", null);
  }

  @Test
  @DisplayName("dispatches a mapped status to its configured subtype, others to the declared type")
  void dispatchesByStatus() {
    JsonResponseErrorDecoderFactory factory =
        new JsonResponseErrorDecoderFactory(
            mapper, Map.of(404, NotFound.class, 429, RateLimited.class));

    ResponseErrorDecoder<ApiError> decoder = factory.create(ApiError.class);

    assertThat(decoder.decode(response(HttpStatus.NOT_FOUND))).isInstanceOf(NotFound.class);
    assertThat(decoder.decode(response(HttpStatus.TOO_MANY_REQUESTS)))
        .isInstanceOf(RateLimited.class);
    assertThat(decoder.decode(response(HttpStatus.INTERNAL_SERVER_ERROR)))
        .isExactlyInstanceOf(ApiError.class);
  }

  @Test
  @DisplayName("ignores a mapping whose type is not assignable to the declared error type")
  void filtersByAssignability() {
    JsonResponseErrorDecoderFactory factory =
        new JsonResponseErrorDecoderFactory(mapper, Map.of(429, RateLimited.class));

    // Declared type NotFound: RateLimited is not a NotFound, so 429 falls back to NotFound.
    ResponseErrorDecoder<NotFound> decoder = factory.create(NotFound.class);

    assertThat(decoder.decode(response(HttpStatus.TOO_MANY_REQUESTS)))
        .isExactlyInstanceOf(NotFound.class);
  }

  @Test
  @DisplayName("plain() ignores the global mapping so per-method @OnStatus wins")
  void plainIgnoresGlobalMapping() {
    JsonResponseErrorDecoderFactory factory =
        new JsonResponseErrorDecoderFactory(mapper, Map.of(404, NotFound.class));

    ResponseErrorDecoder<ApiError> plain = factory.plain(ApiError.class);

    assertThat(plain.decode(response(HttpStatus.NOT_FOUND))).isExactlyInstanceOf(ApiError.class);
  }

  @Test
  @DisplayName("an empty mapping behaves as a plain decoder")
  void emptyMappingIsPlain() {
    JsonResponseErrorDecoderFactory factory = new JsonResponseErrorDecoderFactory(mapper);

    assertThat(factory.create(ApiError.class).decode(response(HttpStatus.NOT_FOUND)))
        .isExactlyInstanceOf(ApiError.class);
  }

  @Test
  @DisplayName("a per-status @OnStatus override beats the global mapping for the same status")
  void onStatusBeatsGlobalMapping() {
    // Global maps 404 -> RateLimited; the @OnStatus-style override (via plain()) maps 404 ->
    // NotFound.
    JsonResponseErrorDecoderFactory factory =
        new JsonResponseErrorDecoderFactory(mapper, Map.of(404, RateLimited.class));

    ResponseErrorDecoder<ApiError> decoder =
        ResponseErrorDecoders.<ApiError>forDefault(factory, ApiError.class)
            .on(404, NotFound.class)
            .build();

    assertThat(decoder.decode(response(HttpStatus.NOT_FOUND))).isInstanceOf(NotFound.class);
  }

  @Test
  @DisplayName("refuses a Void or void mapping when built, naming the status and Unit")
  void refusesVoidMapping() {
    assertThatThrownBy(() -> new JsonResponseErrorDecoderFactory(mapper, Map.of(404, Void.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "JsonResponseErrorDecoderFactory: status 404 maps to error type Void, which cannot"
                + " hold a value, but a Left always holds one. Map it to org.higherkindedj.hkt.Unit,"
                + " the error type that carries nothing, or remove the mapping.");
    assertThatThrownBy(() -> new JsonResponseErrorDecoderFactory(mapper, Map.of(410, void.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "JsonResponseErrorDecoderFactory: status 410 maps to error type void,");
  }

  @Test
  @DisplayName("refuses a primitive mapping when built, naming the status")
  void refusesPrimitiveMapping() {
    assertThatThrownBy(() -> new JsonResponseErrorDecoderFactory(mapper, Map.of(404, int.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "JsonResponseErrorDecoderFactory: status 404 maps to the primitive int, which no error"
                + " can be. Map it to an error class, or remove the mapping.");
  }
}
