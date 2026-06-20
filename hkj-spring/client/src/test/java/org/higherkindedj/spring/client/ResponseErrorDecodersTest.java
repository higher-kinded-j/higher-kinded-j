// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("ResponseErrorDecoders status dispatch")
class ResponseErrorDecodersTest {

  sealed interface ApiErr permits NotFound, Conflict, Generic {}

  record NotFound(String message) implements ApiErr {}

  record Conflict(String message) implements ApiErr {}

  record Generic(String message) implements ApiErr {}

  // Stub factory: returns a decoder yielding a fixed instance for each requested error type, so the
  // test exercises the status-dispatch logic in isolation from Jackson.
  private final Map<Class<?>, ApiErr> instances =
      Map.of(
          ApiErr.class, new Generic("default"),
          NotFound.class, new NotFound("nf"),
          Conflict.class, new Conflict("cf"));

  private final ResponseErrorDecoderFactory factory =
      new ResponseErrorDecoderFactory() {
        @Override
        @SuppressWarnings("unchecked")
        public <X> ResponseErrorDecoder<X> create(Class<X> errorType) {
          return response -> (X) instances.get(errorType);
        }
      };

  private ClientErrorResponse response(HttpStatus status) {
    return new ClientErrorResponse(status, "{}", null);
  }

  @Test
  @DisplayName("routes each status to its mapped error subtype")
  void routesByStatus() {
    ResponseErrorDecoder<ApiErr> decoder =
        ResponseErrorDecoders.<ApiErr>forDefault(factory, ApiErr.class)
            .on(404, NotFound.class)
            .on(409, Conflict.class)
            .build();

    assertThat(decoder.decode(response(HttpStatus.NOT_FOUND))).isInstanceOf(NotFound.class);
    assertThat(decoder.decode(response(HttpStatus.CONFLICT))).isInstanceOf(Conflict.class);
  }

  @Test
  @DisplayName("falls back to the default type for unmapped statuses")
  void fallsBackToDefault() {
    ResponseErrorDecoder<ApiErr> decoder =
        ResponseErrorDecoders.<ApiErr>forDefault(factory, ApiErr.class)
            .on(404, NotFound.class)
            .build();

    assertThat(decoder.decode(response(HttpStatus.INTERNAL_SERVER_ERROR)))
        .isInstanceOf(Generic.class);
  }

  @Test
  @DisplayName("rejects null factory / default type")
  void rejectsNulls() {
    assertThatThrownBy(() -> ResponseErrorDecoders.forDefault(null, ApiErr.class))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> ResponseErrorDecoders.forDefault(factory, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("the utility class cannot be instantiated")
  void cannotInstantiate() throws NoSuchMethodException {
    Constructor<ResponseErrorDecoders> ctor = ResponseErrorDecoders.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    assertThatThrownBy(ctor::newInstance)
        .isInstanceOf(InvocationTargetException.class)
        .hasCauseInstanceOf(UnsupportedOperationException.class);
  }
}
