// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryMonad;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.hkt.vtask.VTaskMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Dynamic test factory for MonadError laws using JUnit 6's @TestFactory.
 *
 * <p>This class tests the algebraic laws that all MonadError implementations must satisfy.
 *
 * <p>MonadError laws tested:
 *
 * <ul>
 *   <li><b>Left Zero:</b> {@code flatMap(raiseError(e), f) == raiseError(e)}
 *   <li><b>Recovery:</b> {@code handleErrorWith(raiseError(e), f) == f(e)}
 *   <li><b>Success Passthrough:</b> {@code handleErrorWith(of(a), f) == of(a)}
 * </ul>
 *
 * <p>Benefits of @TestFactory approach:
 *
 * <ul>
 *   <li>Tests are generated at runtime based on actual MonadError implementations
 *   <li>Adding new MonadError implementations automatically adds test coverage
 *   <li>Clear, structured test output showing which implementation/law combination passed/failed
 *   <li>Each test runs independently with proper isolation
 * </ul>
 */
@DisplayName("MonadError Laws - Dynamic Test Factory")
class MonadErrorLawsTestFactory {

  /**
   * Test data record containing all information needed to test a MonadError.
   *
   * @param <F> the monad type constructor
   * @param <E> the error type
   */
  record MonadErrorTestData<F extends WitnessArity<TypeArity.Unary>, E>(
      String name,
      MonadError<F, E> monadError,
      Kind<F, Integer> successValue,
      E errorValue,
      EqualityChecker<F> equalityChecker) {

    static <F extends WitnessArity<TypeArity.Unary>, E> MonadErrorTestData<F, E> of(
        String name,
        MonadError<F, E> monadError,
        Kind<F, Integer> successValue,
        E errorValue,
        EqualityChecker<F> checker) {
      return new MonadErrorTestData<>(name, monadError, successValue, errorValue, checker);
    }
  }

  /** Functional interface for checking equality of Kind values */
  @FunctionalInterface
  interface EqualityChecker<M extends WitnessArity<TypeArity.Unary>> {
    <A> boolean areEqual(Kind<M, A> a, Kind<M, A> b);
  }

  /**
   * Provides test data for all MonadError implementations.
   *
   * <p>This is a centralized source of test data. Adding a new MonadError implementation requires
   * only adding one line here, and all law tests will automatically cover it.
   */
  private static Stream<MonadErrorTestData<?, ?>> allMonadErrors() {
    return Stream.of(
        MonadErrorTestData.of(
            "Maybe",
            MaybeMonad.INSTANCE,
            MAYBE.widen(Maybe.just(42)),
            Unit.INSTANCE,
            new EqualityChecker<MaybeKind.Witness>() {
              @Override
              public <A> boolean areEqual(
                  Kind<MaybeKind.Witness, A> a, Kind<MaybeKind.Witness, A> b) {
                return MAYBE.narrow(a).equals(MAYBE.narrow(b));
              }
            }),
        MonadErrorTestData.of(
            "Either",
            EitherMonad.<String>instance(),
            EITHER.widen(Either.right(42)),
            "test error",
            new EqualityChecker<EitherKind.Witness<String>>() {
              @Override
              public <A> boolean areEqual(
                  Kind<EitherKind.Witness<String>, A> a, Kind<EitherKind.Witness<String>, A> b) {
                return EITHER.narrow(a).equals(EITHER.narrow(b));
              }
            }),
        MonadErrorTestData.of(
            "Try",
            TryMonad.INSTANCE,
            TRY.widen(Try.success(42)),
            new RuntimeException("test error"),
            new EqualityChecker<TryKind.Witness>() {
              @Override
              public <A> boolean areEqual(Kind<TryKind.Witness, A> a, Kind<TryKind.Witness, A> b) {
                Try<A> tryA = TRY.narrow(a);
                Try<A> tryB = TRY.narrow(b);
                if (tryA.isSuccess() != tryB.isSuccess()) {
                  return false;
                }
                if (tryA.isSuccess()) {
                  try {
                    return Objects.equals(tryA.get(), tryB.get());
                  } catch (Throwable e) {
                    return false;
                  }
                } else {
                  Throwable causeA = ((Try.Failure<A>) tryA).cause();
                  Throwable causeB = ((Try.Failure<A>) tryB).cause();
                  return causeA.getClass().equals(causeB.getClass())
                      && Objects.equals(causeA.getMessage(), causeB.getMessage());
                }
              }
            }),
        MonadErrorTestData.of(
            "Optional",
            OptionalMonad.INSTANCE,
            OPTIONAL.widen(Optional.of(42)),
            Unit.INSTANCE,
            new EqualityChecker<OptionalKind.Witness>() {
              @Override
              public <A> boolean areEqual(
                  Kind<OptionalKind.Witness, A> a, Kind<OptionalKind.Witness, A> b) {
                return OPTIONAL.narrow(a).equals(OPTIONAL.narrow(b));
              }
            }),
        MonadErrorTestData.of(
            "Validated",
            ValidatedMonad.instance(listSemigroup()),
            VALIDATED.widen(Validated.valid(42)),
            List.of("test error"),
            new EqualityChecker<ValidatedKind.Witness<List<String>>>() {
              @Override
              public <A> boolean areEqual(
                  Kind<ValidatedKind.Witness<List<String>>, A> a,
                  Kind<ValidatedKind.Witness<List<String>>, A> b) {
                return VALIDATED.narrow(a).equals(VALIDATED.narrow(b));
              }
            }),
        MonadErrorTestData.of(
            "VTask",
            VTaskMonad.INSTANCE,
            VTASK.widen(VTask.succeed(42)),
            new RuntimeException("test error"),
            new EqualityChecker<VTaskKind.Witness>() {
              @Override
              public <A> boolean areEqual(
                  Kind<VTaskKind.Witness, A> a, Kind<VTaskKind.Witness, A> b) {
                VTask<A> taskA = VTASK.narrow(a);
                VTask<A> taskB = VTASK.narrow(b);
                Try<A> resultA = taskA.runSafe();
                Try<A> resultB = taskB.runSafe();
                if (resultA.isSuccess() != resultB.isSuccess()) {
                  return false;
                }
                if (resultA.isSuccess()) {
                  return Objects.equals(resultA.orElse(null), resultB.orElse(null));
                } else {
                  Throwable causeA = ((Try.Failure<A>) resultA).cause();
                  Throwable causeB = ((Try.Failure<A>) resultB).cause();
                  return causeA.getClass().equals(causeB.getClass())
                      && Objects.equals(causeA.getMessage(), causeB.getMessage());
                }
              }
            }));
  }

  /** Creates a Semigroup for List<String> that concatenates lists. */
  private static Semigroup<List<String>> listSemigroup() {
    return (a, b) -> {
      List<String> result = new ArrayList<>(a);
      result.addAll(b);
      return result;
    };
  }

  /**
   * Dynamically generates tests for the left zero law: {@code flatMap(raiseError(e), f) ==
   * raiseError(e)}
   *
   * <p>This law states that raising an error and then flatMapping over it should be equivalent to
   * just raising the error - the function is never applied.
   */
  @TestFactory
  @DisplayName("Left Zero Law: flatMap(raiseError(e), f) = raiseError(e)")
  Stream<DynamicTest> leftZeroLaw() {
    return allMonadErrors()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies left zero law", () -> testLeftZeroLaw(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>, E> void testLeftZeroLaw(
      MonadErrorTestData<F, E> data) {
    MonadError<F, E> monadError = data.monadError();
    E error = data.errorValue();
    EqualityChecker<F> checker = data.equalityChecker();

    // Any function - should never be applied
    Function<Integer, Kind<F, String>> f = i -> monadError.of("result:" + i);

    // Left side: flatMap(raiseError(e), f)
    Kind<F, Integer> errorKind = monadError.raiseError(error);
    Kind<F, String> leftSide = monadError.flatMap(f, errorKind);

    // Right side: raiseError(e)
    Kind<F, String> rightSide = monadError.raiseError(error);

    assertThat(checker.areEqual(leftSide, rightSide))
        .as("flatMap(raiseError(e), f) should equal raiseError(e)")
        .isTrue();
  }

  /**
   * Dynamically generates tests for the error recovery law: {@code handleErrorWith(raiseError(e),
   * f) == f(e)}
   *
   * <p>This law states that handling an error with a recovery function should apply that function
   * to the error.
   */
  @TestFactory
  @DisplayName("Recovery Law: handleErrorWith(raiseError(e), f) = f(e)")
  Stream<DynamicTest> recoveryLaw() {
    return allMonadErrors()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies recovery law", () -> testRecoveryLaw(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>, E> void testRecoveryLaw(
      MonadErrorTestData<F, E> data) {
    MonadError<F, E> monadError = data.monadError();
    E error = data.errorValue();
    EqualityChecker<F> checker = data.equalityChecker();

    // Recovery function that returns a success value
    Function<E, Kind<F, Integer>> handler = e -> monadError.of(100);

    // Left side: handleErrorWith(raiseError(e), handler)
    Kind<F, Integer> errorKind = monadError.raiseError(error);
    Kind<F, Integer> leftSide = monadError.handleErrorWith(errorKind, handler);

    // Right side: handler(e)
    Kind<F, Integer> rightSide = handler.apply(error);

    assertThat(checker.areEqual(leftSide, rightSide))
        .as("handleErrorWith(raiseError(e), f) should equal f(e)")
        .isTrue();
  }

  /**
   * Dynamically generates tests for the success passthrough law: {@code handleErrorWith(of(a), f)
   * == of(a)}
   *
   * <p>This law states that handling errors on a successful value should return the success
   * unchanged - the handler is never invoked.
   */
  @TestFactory
  @DisplayName("Success Passthrough Law: handleErrorWith(of(a), f) = of(a)")
  Stream<DynamicTest> successPassthroughLaw() {
    return allMonadErrors()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies success passthrough law",
                    () -> testSuccessPassthroughLaw(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>, E> void testSuccessPassthroughLaw(
      MonadErrorTestData<F, E> data) {
    MonadError<F, E> monadError = data.monadError();
    Kind<F, Integer> successValue = data.successValue();
    EqualityChecker<F> checker = data.equalityChecker();

    // Handler that should never be called
    Function<E, Kind<F, Integer>> handler = e -> monadError.of(-1);

    // Left side: handleErrorWith(successValue, handler)
    Kind<F, Integer> leftSide = monadError.handleErrorWith(successValue, handler);

    // Right side: successValue (unchanged)
    Kind<F, Integer> rightSide = successValue;

    assertThat(checker.areEqual(leftSide, rightSide))
        .as("handleErrorWith(of(a), f) should equal of(a)")
        .isTrue();
  }

  /**
   * Dynamically generates tests verifying that raiseError creates an error state.
   *
   * <p>This is a derived property ensuring raiseError works correctly.
   */
  @TestFactory
  @DisplayName("raiseError creates recoverable error state")
  Stream<DynamicTest> raiseErrorCreatesRecoverableError() {
    return allMonadErrors()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " raiseError creates recoverable error",
                    () -> testRaiseErrorRecoverable(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>, E> void testRaiseErrorRecoverable(
      MonadErrorTestData<F, E> data) {
    MonadError<F, E> monadError = data.monadError();
    E error = data.errorValue();
    EqualityChecker<F> checker = data.equalityChecker();

    int recoveryValue = 999;

    // Create error and recover from it
    Kind<F, Integer> errorKind = monadError.raiseError(error);
    Kind<F, Integer> recovered =
        monadError.handleErrorWith(errorKind, e -> monadError.of(recoveryValue));

    // Verify we got the recovery value
    Kind<F, Integer> expected = monadError.of(recoveryValue);

    assertThat(checker.areEqual(recovered, expected))
        .as("raiseError should create an error that can be recovered from")
        .isTrue();
  }

  /**
   * Dynamically generates tests for the recoverWith convenience method.
   *
   * <p>Tests that recoverWith(raiseError(e), fallback) == fallback
   */
  @TestFactory
  @DisplayName("recoverWith provides fallback for errors")
  Stream<DynamicTest> recoverWithProvidesFallback() {
    return allMonadErrors()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " recoverWith provides fallback", () -> testRecoverWith(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>, E> void testRecoverWith(
      MonadErrorTestData<F, E> data) {
    MonadError<F, E> monadError = data.monadError();
    E error = data.errorValue();
    EqualityChecker<F> checker = data.equalityChecker();

    Kind<F, Integer> fallback = monadError.of(777);

    // Left side: recoverWith(raiseError(e), fallback)
    Kind<F, Integer> errorKind = monadError.raiseError(error);
    Kind<F, Integer> leftSide = monadError.recoverWith(errorKind, fallback);

    // Right side: fallback
    Kind<F, Integer> rightSide = fallback;

    assertThat(checker.areEqual(leftSide, rightSide))
        .as("recoverWith(raiseError(e), fallback) should equal fallback")
        .isTrue();
  }
}
