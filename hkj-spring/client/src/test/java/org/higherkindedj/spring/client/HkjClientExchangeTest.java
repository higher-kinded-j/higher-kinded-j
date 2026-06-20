// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;

@DisplayName("HkjClientExchange translators")
class HkjClientExchangeTest {

  record UserError(String code, String message) {}

  record UserDto(String id, String name) {}

  private static final UserDto ADA = new UserDto("1", "Ada");

  private static Supplier<ResponseEntity<UserDto>> ok(UserDto body) {
    return () -> ResponseEntity.ok(body);
  }

  private static Supplier<ResponseEntity<UserDto>> fails(HttpStatusCode status, String body) {
    return () -> {
      throw new RestClientResponseException(
          status.toString(),
          status,
          status.toString(),
          new HttpHeaders(),
          body.getBytes(StandardCharsets.UTF_8),
          StandardCharsets.UTF_8);
    };
  }

  private static final ResponseErrorDecoder<UserError> DECODER =
      response -> new UserError(String.valueOf(response.statusValue()), response.body());

  @Nested
  @DisplayName("either")
  class EitherTranslator {

    @Test
    @DisplayName("2xx becomes Right(body)")
    void successRight() {
      EitherPath<UserError, UserDto> path = HkjClientExchange.either(ok(ADA), DECODER);

      assertThatEither(path.run()).isRight().hasRight(ADA);
    }

    @Test
    @DisplayName("4xx is decoded into Left(error)")
    void failureLeft() {
      EitherPath<UserError, UserDto> path =
          HkjClientExchange.either(fails(HttpStatus.NOT_FOUND, "no user"), DECODER);

      assertThatEither(path.run())
          .isLeft()
          .hasLeftSatisfying(e -> assertThat(e.code()).isEqualTo("404"));
    }

    @Test
    @DisplayName("a 2xx response with an empty body becomes Right(null)")
    void emptyBodyRightNull() {
      EitherPath<UserError, UserDto> path =
          HkjClientExchange.either(() -> ResponseEntity.ok(null), DECODER);

      assertThatEither(path.run()).isRight().hasRightNull();
    }

    @Test
    @DisplayName("a decoder that returns null is rejected at the boundary")
    void nullDecoderRejected() {
      ResponseErrorDecoder<UserError> nullDecoder = response -> null;
      Supplier<ResponseEntity<UserDto>> call = fails(HttpStatus.NOT_FOUND, "x");

      assertThatThrownBy(() -> HkjClientExchange.either(call, nullDecoder))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("returned null");
    }
  }

  @Nested
  @DisplayName("eitherVTask")
  class EitherVTaskTranslator {

    @Test
    @DisplayName("defers the call and yields Right(body) when run")
    void successRight() {
      VTaskPath<Either<UserError, UserDto>> path = HkjClientExchange.eitherVTask(ok(ADA), DECODER);

      assertThatEither(path.unsafeRun()).isRight().hasRight(ADA);
    }

    @Test
    @DisplayName("yields Left(error) on a failure response")
    void failureLeft() {
      VTaskPath<Either<UserError, UserDto>> path =
          HkjClientExchange.eitherVTask(fails(HttpStatus.BAD_REQUEST, "bad"), DECODER);

      assertThatEither(path.unsafeRun())
          .isLeft()
          .hasLeftSatisfying(e -> assertThat(e.code()).isEqualTo("400"));
    }
  }

  @Nested
  @DisplayName("maybe")
  class MaybeTranslator {

    @Test
    @DisplayName("2xx with a body becomes Just(body)")
    void successJust() {
      MaybePath<UserDto> path = HkjClientExchange.maybe(ok(ADA));

      assertThatMaybe(path.run()).isJust().hasValue(ADA);
    }

    @Test
    @DisplayName("2xx with an empty body becomes Nothing")
    void emptyBodyNothing() {
      MaybePath<UserDto> path = HkjClientExchange.maybe(() -> ResponseEntity.ok(null));

      assertThatMaybe(path.run()).isNothing();
    }

    @Test
    @DisplayName("404 becomes Nothing")
    void notFoundNothing() {
      MaybePath<UserDto> path = HkjClientExchange.maybe(fails(HttpStatus.NOT_FOUND, "gone"));

      assertThatMaybe(path.run()).isNothing();
    }

    @Test
    @DisplayName("other failures propagate the original exception")
    void otherFailurePropagates() {
      Supplier<ResponseEntity<UserDto>> call = fails(HttpStatus.INTERNAL_SERVER_ERROR, "boom");

      assertThatThrownBy(() -> HkjClientExchange.maybe(call))
          .isInstanceOf(RestClientResponseException.class);
    }
  }

  @Test
  @DisplayName("the utility class cannot be instantiated")
  void cannotInstantiate() throws NoSuchMethodException {
    Constructor<HkjClientExchange> ctor = HkjClientExchange.class.getDeclaredConstructor();
    ctor.setAccessible(true);

    assertThatThrownBy(ctor::newInstance)
        .isInstanceOf(InvocationTargetException.class)
        .hasCauseInstanceOf(UnsupportedOperationException.class);
  }
}
