// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.extensions.PrismExtensions.*;

import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Prisms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PrismExtensions Utility Class Tests")
class PrismExtensionsTest {

  // Test data structure
  sealed interface Result permits Success, Failure {}

  record Success(int value) implements Result {}

  record Failure(String error) implements Result {}

  private static final Prism<Result, Success> successPrism =
      Prism.of(r -> r instanceof Success s ? Optional.of(s) : Optional.empty(), success -> success);

  private static final Prism<Result, Failure> failurePrism =
      Prism.of(r -> r instanceof Failure f ? Optional.of(f) : Optional.empty(), failure -> failure);

  @Nested
  @DisplayName("getMaybe() - Get value as Maybe")
  class GetMaybeMethod {
    @Test
    @DisplayName("should return Just when prism matches")
    void shouldReturnJustWhenMatches() {
      Result result = new Success(42);
      Maybe<Success> maybe = getMaybe(successPrism, result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get().value).isEqualTo(42);
    }

    @Test
    @DisplayName("should return Nothing when prism doesn't match")
    void shouldReturnNothingWhenDoesNotMatch() {
      Result result = new Failure("error");
      Maybe<Success> maybe = getMaybe(successPrism, result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("getEither() - Get value as Either")
  class GetEitherMethod {
    @Test
    @DisplayName("should return Right when prism matches")
    void shouldReturnRightWhenMatches() {
      Result result = new Success(100);
      Either<String, Success> either = getEither(successPrism, "Not a success", result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight().value).isEqualTo(100);
    }

    @Test
    @DisplayName("should return Left when prism doesn't match")
    void shouldReturnLeftWhenDoesNotMatch() {
      Result result = new Failure("error");
      Either<String, Success> either = getEither(successPrism, "Not a success", result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("Not a success");
    }
  }

  @Nested
  @DisplayName("getValidated() - Get value as Validated")
  class GetValidatedMethod {
    @Test
    @DisplayName("should return Valid when prism matches")
    void shouldReturnValidWhenMatches() {
      Result result = new Success(200);
      Validated<String, Success> validated = getValidated(successPrism, "Not a success", result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get().value).isEqualTo(200);
    }

    @Test
    @DisplayName("should return Invalid when prism doesn't match")
    void shouldReturnInvalidWhenDoesNotMatch() {
      Result result = new Failure("error");
      Validated<String, Success> validated = getValidated(successPrism, "Not a success", result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("Not a success");
    }
  }

  @Nested
  @DisplayName("modifyMaybe() - Modify with Maybe-returning function")
  class ModifyMaybeMethod {
    @Test
    @DisplayName("should modify when prism matches and function returns Just")
    void shouldModifyWhenMatchesAndJust() {
      Result result = new Success(10);
      Maybe<Result> modified =
          modifyMaybe(
              successPrism,
              success ->
                  success.value > 0 ? Maybe.just(new Success(success.value * 2)) : Maybe.nothing(),
              result);
      assertThat(modified.isJust()).isTrue();
      assertThat(((Success) modified.get()).value).isEqualTo(20);
    }

    @Test
    @DisplayName("should return Nothing when prism doesn't match")
    void shouldReturnNothingWhenDoesNotMatch() {
      Result result = new Failure("error");
      Maybe<Result> modified =
          modifyMaybe(successPrism, success -> Maybe.just(new Success(42)), result);
      assertThat(modified.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should return Nothing when function returns Nothing")
    void shouldReturnNothingWhenFunctionReturnsNothing() {
      Result result = new Success(-5);
      Maybe<Result> modified =
          modifyMaybe(
              successPrism,
              success ->
                  success.value > 0 ? Maybe.just(new Success(success.value * 2)) : Maybe.nothing(),
              result);
      assertThat(modified.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("modifyEither() - Modify with Either-returning function")
  class ModifyEitherMethod {
    @Test
    @DisplayName("should modify when prism matches and function returns Right")
    void shouldModifyWhenMatchesAndRight() {
      Result result = new Success(15);
      Either<String, Result> modified =
          modifyEither(
              successPrism,
              "Not a success",
              success ->
                  success.value > 10
                      ? Either.right(new Success(success.value + 5))
                      : Either.left("Value too small"),
              result);
      assertThat(modified.isRight()).isTrue();
      assertThat(((Success) modified.getRight()).value).isEqualTo(20);
    }

    @Test
    @DisplayName("should return Left when prism doesn't match")
    void shouldReturnLeftWhenDoesNotMatch() {
      Result result = new Failure("error");
      Either<String, Result> modified =
          modifyEither(
              successPrism, "Not a success", success -> Either.right(new Success(42)), result);
      assertThat(modified.isLeft()).isTrue();
      assertThat(modified.getLeft()).isEqualTo("Not a success");
    }

    @Test
    @DisplayName("should return Left when function returns Left")
    void shouldReturnLeftWhenFunctionReturnsLeft() {
      Result result = new Success(5);
      Either<String, Result> modified =
          modifyEither(
              successPrism,
              "Not a success",
              success ->
                  success.value > 10
                      ? Either.right(new Success(success.value + 5))
                      : Either.left("Value too small"),
              result);
      assertThat(modified.isLeft()).isTrue();
      assertThat(modified.getLeft()).isEqualTo("Value too small");
    }
  }

  @Nested
  @DisplayName("modifyValidated() - Modify with Validated-returning function")
  class ModifyValidatedMethod {
    @Test
    @DisplayName("should modify when prism matches and function returns Valid")
    void shouldModifyWhenMatchesAndValid() {
      Result result = new Success(25);
      Validated<String, Result> modified =
          modifyValidated(
              successPrism,
              "Not a success",
              success ->
                  success.value % 5 == 0
                      ? Validated.valid(new Success(success.value / 5))
                      : Validated.invalid("Not divisible by 5"),
              result);
      assertThat(modified.isValid()).isTrue();
      assertThat(((Success) modified.get()).value).isEqualTo(5);
    }

    @Test
    @DisplayName("should return Invalid when prism doesn't match")
    void shouldReturnInvalidWhenDoesNotMatch() {
      Result result = new Failure("error");
      Validated<String, Result> modified =
          modifyValidated(
              successPrism, "Not a success", success -> Validated.valid(new Success(42)), result);
      assertThat(modified.isInvalid()).isTrue();
      assertThat(modified.getError()).isEqualTo("Not a success");
    }

    @Test
    @DisplayName("should return Invalid when function returns Invalid")
    void shouldReturnInvalidWhenFunctionReturnsInvalid() {
      Result result = new Success(13);
      Validated<String, Result> modified =
          modifyValidated(
              successPrism,
              "Not a success",
              success ->
                  success.value % 5 == 0
                      ? Validated.valid(new Success(success.value / 5))
                      : Validated.invalid("Not divisible by 5"),
              result);
      assertThat(modified.isInvalid()).isTrue();
      assertThat(modified.getError()).isEqualTo("Not divisible by 5");
    }
  }

  @Nested
  @DisplayName("Integration Tests with hkj-core Prisms")
  class IntegrationTests {
    @Test
    @DisplayName("should work with Maybe Just prism")
    void shouldWorkWithMaybeJustPrism() {
      Maybe<String> just = Maybe.just("hello");
      Maybe<String> nothing = Maybe.nothing();

      Prism<Maybe<String>, String> justPrism = Prisms.just();

      // Test getMaybe
      Maybe<String> result1 = getMaybe(justPrism, just);
      assertThat(result1.isJust()).isTrue();
      assertThat(result1.get()).isEqualTo("hello");

      Maybe<String> result2 = getMaybe(justPrism, nothing);
      assertThat(result2.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should work with Either Right prism")
    void shouldWorkWithEitherRightPrism() {
      Either<String, Integer> right = Either.right(42);
      Either<String, Integer> left = Either.left("error");

      Prism<Either<String, Integer>, Integer> rightPrism = Prisms.right();

      // Test getEither
      Either<String, Integer> result1 = getEither(rightPrism, "Not right", right);
      assertThat(result1.isRight()).isTrue();
      assertThat(result1.getRight()).isEqualTo(42);

      Either<String, Integer> result2 = getEither(rightPrism, "Not right", left);
      assertThat(result2.isLeft()).isTrue();
      assertThat(result2.getLeft()).isEqualTo("Not right");
    }

    @Test
    @DisplayName("should chain modifyEither for complex transformations")
    void shouldChainModifyEitherForComplexTransformations() {
      Result result = new Success(100);

      // Validate and transform
      Either<String, Result> step1 =
          modifyEither(
              successPrism,
              "Not a success",
              success ->
                  success.value >= 50
                      ? Either.right(new Success(success.value / 2))
                      : Either.left("Value too small"),
              result);

      // Further transformation
      Either<String, Result> step2 =
          step1.flatMap(
              r ->
                  modifyEither(
                      successPrism,
                      "Not a success",
                      success ->
                          success.value > 0
                              ? Either.right(new Success(success.value * 3))
                              : Either.left("Invalid value"),
                      r));

      assertThat(step2.isRight()).isTrue();
      assertThat(((Success) step2.getRight()).value).isEqualTo(150);
    }

    @Test
    @DisplayName("should handle failure case in chain")
    void shouldHandleFailureCaseInChain() {
      Result result = new Success(25);

      Either<String, Result> step1 =
          modifyEither(
              successPrism,
              "Not a success",
              success ->
                  success.value >= 50
                      ? Either.right(new Success(success.value / 2))
                      : Either.left("Value too small"),
              result);

      assertThat(step1.isLeft()).isTrue();
      assertThat(step1.getLeft()).isEqualTo("Value too small");
    }

    @Test
    @DisplayName("should compose getMaybe with Maybe operations")
    void shouldComposeGetMaybeWithMaybeOperations() {
      Result success = new Success(42);
      Result failure = new Failure("error");

      String result1 =
          getMaybe(successPrism, success).map(s -> "Success: " + s.value).orElse("No success");

      String result2 =
          getMaybe(successPrism, failure).map(s -> "Success: " + s.value).orElse("No success");

      assertThat(result1).isEqualTo("Success: 42");
      assertThat(result2).isEqualTo("No success");
    }
  }
}
