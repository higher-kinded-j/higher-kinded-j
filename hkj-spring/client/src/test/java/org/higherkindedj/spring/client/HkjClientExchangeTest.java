// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Unit;
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
import org.springframework.web.client.RestClientException;
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
    @DisplayName("a 2xx response with an empty body is refused, carrying its status and headers")
    void emptyBodyRefused() {
      Supplier<ResponseEntity<UserDto>> created =
          () -> ResponseEntity.created(URI.create("/users/7")).build();

      assertThatThrownBy(() -> HkjClientExchange.either(created, DECODER))
          .isInstanceOfSatisfying(
              EmptyResponseBodyException.class,
              e -> {
                assertThat(e)
                    .isInstanceOf(RestClientException.class)
                    .hasMessageStartingWith(
                        "The 201 response had no body, but a Right needs a value");
                assertThat(e.statusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(e.headers().getLocation()).isEqualTo(URI.create("/users/7"));
              });
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

    @Test
    @DisplayName("a 2xx response with an empty body fails the task")
    void emptyBodyFailsTheTask() {
      VTaskPath<Either<UserError, UserDto>> path =
          HkjClientExchange.eitherVTask(() -> ResponseEntity.ok(null), DECODER);

      assertThatTry(path.runSafe())
          .isFailure()
          .hasExceptionSatisfying(
              e ->
                  assertThat(e)
                      .isInstanceOf(EmptyResponseBodyException.class)
                      .hasMessageContaining("had no body"));
    }
  }

  @Nested
  @DisplayName("maybeUnit")
  class MaybeUnitTranslator {

    @Test
    @DisplayName("2xx becomes Just(Unit.INSTANCE), whatever the body, and 404 becomes Nothing")
    void justUnitOrNothing() {
      Supplier<ResponseEntity<Void>> noContent = () -> ResponseEntity.noContent().build();

      assertThatMaybe(HkjClientExchange.maybeUnit(noContent).run())
          .isJust()
          .hasValue(Unit.INSTANCE);
      assertThatMaybe(HkjClientExchange.maybeUnit(ok(ADA)).run()).isJust().hasValue(Unit.INSTANCE);
      assertThatMaybe(HkjClientExchange.maybeUnit(fails(HttpStatus.NOT_FOUND, "gone")).run())
          .isNothing();
    }

    @Test
    @DisplayName("other failures propagate as the original exception")
    void otherFailuresPropagate() {
      Supplier<ResponseEntity<UserDto>> serverError = fails(HttpStatus.INTERNAL_SERVER_ERROR, "x");

      assertThatThrownBy(() -> HkjClientExchange.maybeUnit(serverError))
          .isInstanceOf(RestClientResponseException.class);
    }
  }

  @Nested
  @DisplayName("eitherUnit and eitherVTaskUnit")
  class UnitTranslators {

    private final Supplier<ResponseEntity<Void>> noContent =
        () -> ResponseEntity.noContent().build();

    @Test
    @DisplayName("2xx becomes Right(Unit.INSTANCE), whatever the body")
    void successRightUnit() {
      assertThatEither(HkjClientExchange.eitherUnit(noContent, DECODER).run())
          .isRight()
          .hasRight(Unit.INSTANCE);
      assertThatEither(HkjClientExchange.eitherUnit(ok(ADA), DECODER).run())
          .isRight()
          .hasRight(Unit.INSTANCE);
      assertThatEither(HkjClientExchange.eitherVTaskUnit(noContent, DECODER).unsafeRun())
          .isRight()
          .hasRight(Unit.INSTANCE);
    }

    @Test
    @DisplayName("4xx is decoded into Left(error)")
    void failureLeft() {
      assertThatEither(
              HkjClientExchange.eitherUnit(fails(HttpStatus.NOT_FOUND, "gone"), DECODER).run())
          .isLeft()
          .hasLeftSatisfying(e -> assertThat(e.code()).isEqualTo("404"));
      assertThatEither(
              HkjClientExchange.eitherVTaskUnit(fails(HttpStatus.CONFLICT, "busy"), DECODER)
                  .unsafeRun())
          .isLeft()
          .hasLeftSatisfying(e -> assertThat(e.code()).isEqualTo("409"));
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
