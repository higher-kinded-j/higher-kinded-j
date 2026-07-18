// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ErrorResponseHeaders")
class ErrorResponseHeadersTest {

  @Mock private HttpServletResponse response;

  record ThrottledError(int seconds) implements HttpHeaderCarrier {
    @Override
    public Map<String, String> headers() {
      return Map.of("Retry-After", Integer.toString(seconds));
    }
  }

  record AuthChallenge(String scheme) implements HttpHeaderCarrier {
    @Override
    public Map<String, String> headers() {
      return Map.of("WWW-Authenticate", scheme);
    }
  }

  record NullHeaderError() implements HttpHeaderCarrier {
    @Override
    public Map<String, String> headers() {
      return null;
    }
  }

  record EmptyHeaderError() implements HttpHeaderCarrier {
    @Override
    public Map<String, String> headers() {
      return Map.of();
    }
  }

  record PlainError(String msg) {}

  @Test
  @DisplayName("Skips when error is null")
  void nullErrorSkips() {
    ErrorResponseHeaders.applyTo(null, response);
    verifyNoInteractions(response);
  }

  @Test
  @DisplayName("Skips when error is not a HttpHeaderCarrier or collection")
  void plainErrorSkips() {
    ErrorResponseHeaders.applyTo(new PlainError("x"), response);
    verifyNoInteractions(response);
  }

  @Test
  @DisplayName("Applies headers from a carrier error")
  void appliesHeadersFromCarrier() {
    ErrorResponseHeaders.applyTo(new ThrottledError(30), response);
    verify(response).addHeader("Retry-After", "30");
  }

  @Test
  @DisplayName("Skips silently when carrier returns null map")
  void nullMapSkips() {
    ErrorResponseHeaders.applyTo(new NullHeaderError(), response);
    verify(response, never()).addHeader(anyString(), anyString());
  }

  @Test
  @DisplayName("Skips silently when carrier returns empty map")
  void emptyMapSkips() {
    ErrorResponseHeaders.applyTo(new EmptyHeaderError(), response);
    verify(response, never()).addHeader(anyString(), anyString());
  }

  @Test
  @DisplayName("Skips null keys and null values without failing")
  void skipsNullEntries() {
    HttpHeaderCarrier carrier =
        () -> {
          HashMap<String, String> map = new HashMap<>();
          map.put(null, "value");
          map.put("Skip-Me", null);
          map.put("Keep-Me", "x");
          return map;
        };
    ErrorResponseHeaders.applyTo(carrier, response);
    verify(response).addHeader("Keep-Me", "x");
    verify(response, never()).addHeader(null, "value");
    verify(response, never()).addHeader("Skip-Me", null);
  }

  @Test
  @DisplayName("Applies headers from each carrier element of a NonEmptyList payload")
  void nonEmptyListPayloadAppliesAll() {
    // NonEmptyList is Iterable but not a Collection — the idiomatic accumulation type
    ErrorResponseHeaders.applyTo(
        NonEmptyList.of(new ThrottledError(30), new AuthChallenge("Basic")), response);
    verify(response).addHeader("Retry-After", "30");
    verify(response).addHeader("WWW-Authenticate", "Basic");
  }

  @Test
  @DisplayName("Applies headers from each carrier element of a Collection payload")
  void collectionPayloadAppliesAll() {
    ErrorResponseHeaders.applyTo(
        List.of(new ThrottledError(30), new AuthChallenge("Basic")), response);
    verify(response).addHeader("Retry-After", "30");
    verify(response).addHeader("WWW-Authenticate", "Basic");
  }

  @Test
  @DisplayName("Collection payload accumulates multi-valued headers across carriers")
  void collectionAccumulatesMultiValuedHeaders() {
    // WWW-Authenticate may carry multiple challenges; addHeader keeps both lines.
    ErrorResponseHeaders.applyTo(
        List.of(new AuthChallenge("Basic"), new AuthChallenge("Bearer")), response);
    verify(response).addHeader("WWW-Authenticate", "Basic");
    verify(response).addHeader("WWW-Authenticate", "Bearer");
  }

  @Test
  @DisplayName("Collection payload ignores non-carrier elements")
  void collectionPayloadIgnoresNonCarriers() {
    ErrorResponseHeaders.applyTo(List.of(new PlainError("x"), new ThrottledError(60)), response);
    verify(response).addHeader("Retry-After", "60");
  }

  @Test
  @DisplayName("Array payload applies headers from each carrier element")
  void arrayPayloadAppliesAll() {
    Object[] errors = {new ThrottledError(30), new AuthChallenge("Bearer")};
    ErrorResponseHeaders.applyTo(errors, response);
    verify(response).addHeader("Retry-After", "30");
    verify(response).addHeader("WWW-Authenticate", "Bearer");
  }

  @Test
  @DisplayName("Array payload ignores non-carrier elements")
  void arrayPayloadIgnoresNonCarriers() {
    Object[] errors = {new PlainError("x"), new ThrottledError(99)};
    ErrorResponseHeaders.applyTo(errors, response);
    verify(response).addHeader("Retry-After", "99");
  }

  @Test
  @DisplayName("Reflective construction throws to prevent instantiation")
  void cannotInstantiate() throws NoSuchMethodException {
    Constructor<ErrorResponseHeaders> ctor = ErrorResponseHeaders.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    assertThatThrownBy(ctor::newInstance)
        .isInstanceOf(InvocationTargetException.class)
        .hasCauseInstanceOf(UnsupportedOperationException.class);
  }
}
