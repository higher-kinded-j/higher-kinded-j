// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("JSON response error decoder")
class HkjClientDecoderTest {

  record UserError(String code, String message) {}

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final HttpStatusCode notFound = HttpStatus.NOT_FOUND;

  private ClientErrorResponse response(String body) {
    return new ClientErrorResponse(notFound, body, null);
  }

  @Nested
  @DisplayName("JsonResponseErrorDecoder")
  class Decoder {

    private final JsonResponseErrorDecoder<UserError> decoder =
        new JsonResponseErrorDecoder<>(mapper, UserError.class);

    @Test
    @DisplayName("decodes the {success,error} envelope into the declared error type")
    void decodesEnvelope() {
      UserError error =
          decoder.decode(
              response(
                  "{\"success\":false,\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"no user\"}}"));

      assertThat(error).isEqualTo(new UserError("NOT_FOUND", "no user"));
    }

    @Test
    @DisplayName("decodes a bare error object when there is no envelope")
    void decodesBareObject() {
      UserError error = decoder.decode(response("{\"code\":\"X\",\"message\":\"m\"}"));

      assertThat(error).isEqualTo(new UserError("X", "m"));
    }

    @Test
    @DisplayName("throws when the body is null")
    void throwsOnNullBody() {
      assertThatThrownBy(() -> decoder.decode(response(null)))
          .isInstanceOf(ResponseErrorDecodeException.class);
    }

    @Test
    @DisplayName("throws when the body is blank")
    void throwsOnBlankBody() {
      assertThatThrownBy(() -> decoder.decode(response("   ")))
          .isInstanceOf(ResponseErrorDecodeException.class);
    }

    @Test
    @DisplayName("throws when the error node is JSON null")
    void throwsOnNullErrorNode() {
      assertThatThrownBy(() -> decoder.decode(response("{\"success\":false,\"error\":null}")))
          .isInstanceOf(ResponseErrorDecodeException.class);
    }

    @Test
    @DisplayName("wraps a parse failure as a decode exception")
    void wrapsParseFailure() {
      assertThatThrownBy(() -> decoder.decode(response("not json")))
          .isInstanceOf(ResponseErrorDecodeException.class)
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("rejects null constructor arguments")
    void rejectsNullArgs() {
      assertThatThrownBy(() -> new JsonResponseErrorDecoder<>(null, UserError.class))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> new JsonResponseErrorDecoder<>(mapper, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("JsonResponseErrorDecoderFactory")
  class Factory {

    @Test
    @DisplayName("creates a working decoder for the requested error type")
    void createsDecoder() {
      ResponseErrorDecoderFactory factory = new JsonResponseErrorDecoderFactory(mapper);

      ResponseErrorDecoder<UserError> decoder = factory.create(UserError.class);

      assertThat(decoder.decode(response("{\"code\":\"X\",\"message\":\"m\"}")))
          .isEqualTo(new UserError("X", "m"));
    }

    @Test
    @DisplayName("rejects a null mapper")
    void rejectsNullMapper() {
      assertThatThrownBy(() -> new JsonResponseErrorDecoderFactory(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("ClientErrorResponse")
  class ErrorResponse {

    @Test
    @DisplayName("exposes the numeric status value")
    void statusValue() {
      assertThat(response("{}").statusValue()).isEqualTo(404);
    }

    @Test
    @DisplayName("carries optional headers")
    void carriesHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.add("Retry-After", "30");

      ClientErrorResponse response = new ClientErrorResponse(notFound, "{}", headers);

      assertThat(response.headers()).isNotNull();
      assertThat(response.headers().getFirst("Retry-After")).isEqualTo("30");
    }

    @Test
    @DisplayName("rejects a null status")
    void rejectsNullStatus() {
      assertThatThrownBy(() -> new ClientErrorResponse(null, "{}", null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("ResponseErrorDecodeException")
  class DecodeException {

    @Test
    @DisplayName("exposes the status and response body")
    void accessors() {
      ResponseErrorDecodeException ex =
          new ResponseErrorDecodeException(notFound, "the body", new RuntimeException("boom"));

      assertThat(ex.status()).isEqualTo(notFound);
      assertThat(ex.responseBody()).isEqualTo("the body");
      assertThat(ex.getMessage()).contains("404");
      assertThat(ex.getCause()).hasMessage("boom");
    }
  }

  @Nested
  @DisplayName("Retry-After")
  class RetryAfter {

    private ClientErrorResponse withRetryAfter(String value) {
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.RETRY_AFTER, value);
      return new ClientErrorResponse(notFound, "{}", headers);
    }

    @Test
    @DisplayName("parses the delta-seconds form")
    void deltaSeconds() {
      assertThat(withRetryAfter("30").retryAfter()).contains(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("is empty when the header is absent")
    void absent() {
      assertThat(new ClientErrorResponse(notFound, "{}", new HttpHeaders()).retryAfter()).isEmpty();
      assertThat(new ClientErrorResponse(notFound, "{}", null).retryAfter()).isEmpty();
    }

    @Test
    @DisplayName("is empty for an unparseable value")
    void unparseable() {
      assertThat(withRetryAfter("soon").retryAfter()).isEmpty();
      assertThat(ClientErrorResponse.parseRetryAfter("  ")).isEmpty();
    }

    @Test
    @DisplayName("parses an HTTP-date: future is positive, past clamps to zero")
    void httpDate() {
      String future =
          DateTimeFormatter.RFC_1123_DATE_TIME.format(
              ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(5));
      assertThat(ClientErrorResponse.parseRetryAfter(future))
          .hasValueSatisfying(d -> assertThat(d).isPositive());

      // 21 Oct 2015 was a Wednesday (the RFC 7231 example) and is in the past → clamps to zero.
      assertThat(ClientErrorResponse.parseRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT"))
          .contains(Duration.ZERO);
    }
  }
}
