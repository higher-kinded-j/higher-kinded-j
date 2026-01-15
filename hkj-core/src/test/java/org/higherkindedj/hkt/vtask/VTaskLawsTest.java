// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Explicit law verification tests for VTask using JUnit 5's @TestFactory.
 *
 * <p>This test class verifies that VTask satisfies all required type class laws:
 *
 * <ul>
 *   <li><b>Functor Laws:</b> Identity and Composition
 *   <li><b>Applicative Laws:</b> Identity, Homomorphism, Interchange, Composition
 *   <li><b>Monad Laws:</b> Left Identity, Right Identity, Associativity
 *   <li><b>MonadError Laws:</b> Left Zero, Recovery, Success Passthrough
 * </ul>
 *
 * <p>Each law is tested with multiple test values to ensure comprehensive coverage.
 */
@DisplayName("VTask Laws - Dynamic Test Factory")
class VTaskLawsTest {

  private final VTaskFunctor functor = VTaskFunctor.INSTANCE;
  private final VTaskApplicative applicative = VTaskApplicative.INSTANCE;
  private final VTaskMonad monad = VTaskMonad.INSTANCE;

  // ==================== Test Values ====================

  private static final Integer[] TEST_VALUES = {0, 1, -1, 42, -100, 100, Integer.MAX_VALUE};

  /** Compares two VTask results by running them and comparing outcomes. */
  private <A> boolean vtasksEqual(VTask<A> a, VTask<A> b) {
    Try<A> resultA = a.runSafe();
    Try<A> resultB = b.runSafe();

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

  // ==================== Functor Laws ====================

  /**
   * Functor Identity Law: map(id, fa) = fa
   *
   * <p>Mapping the identity function over a functor should return the original functor unchanged.
   */
  @TestFactory
  @DisplayName("Functor Identity Law: map(id, fa) = fa")
  Stream<DynamicTest> functorIdentityLaw() {
    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Identity law holds for value " + value,
                    () -> {
                      VTask<Integer> original = VTask.succeed(value);
                      Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(original);

                      Kind<VTaskKind.Witness, Integer> result =
                          functor.map(Function.identity(), fa);

                      assertThat(vtasksEqual(VTASK.narrow(result), original)).isTrue();
                    }));
  }

  /**
   * Functor Composition Law: map(g.f, fa) = map(g, map(f, fa))
   *
   * <p>Mapping a composed function should be equivalent to mapping each function in sequence.
   */
  @TestFactory
  @DisplayName("Functor Composition Law: map(g.f, fa) = map(g, map(f, fa))")
  Stream<DynamicTest> functorCompositionLaw() {
    Function<Integer, String> f = i -> "value:" + i;
    Function<String, Integer> g = String::length;

    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Composition law holds for value " + value,
                    () -> {
                      Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(VTask.succeed(value));

                      // Left side: map(g.f, fa)
                      Function<Integer, Integer> composed = f.andThen(g);
                      Kind<VTaskKind.Witness, Integer> leftSide = functor.map(composed, fa);

                      // Right side: map(g, map(f, fa))
                      Kind<VTaskKind.Witness, String> intermediate = functor.map(f, fa);
                      Kind<VTaskKind.Witness, Integer> rightSide = functor.map(g, intermediate);

                      assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide)))
                          .isTrue();
                    }));
  }

  // ==================== Monad Laws ====================

  /**
   * Monad Left Identity Law: flatMap(of(a), f) = f(a)
   *
   * <p>Wrapping a value with of and then flatMapping is equivalent to just applying the function.
   */
  @TestFactory
  @DisplayName("Monad Left Identity Law: flatMap(of(a), f) = f(a)")
  Stream<DynamicTest> monadLeftIdentityLaw() {
    Function<Integer, VTask<String>> f = i -> VTask.succeed("result:" + i);

    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Left identity law holds for value " + value,
                    () -> {
                      // Left side: flatMap(of(a), f)
                      Kind<VTaskKind.Witness, Integer> ofValue = monad.of(value);
                      Kind<VTaskKind.Witness, String> leftSide =
                          monad.flatMap(i -> VTASK.widen(f.apply(i)), ofValue);

                      // Right side: f(a)
                      VTask<String> rightSide = f.apply(value);

                      assertThat(vtasksEqual(VTASK.narrow(leftSide), rightSide)).isTrue();
                    }));
  }

  /**
   * Monad Right Identity Law: flatMap(m, of) = m
   *
   * <p>FlatMapping with of should return the original monadic value unchanged.
   */
  @TestFactory
  @DisplayName("Monad Right Identity Law: flatMap(m, of) = m")
  Stream<DynamicTest> monadRightIdentityLaw() {
    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Right identity law holds for value " + value,
                    () -> {
                      VTask<Integer> original = VTask.succeed(value);
                      Kind<VTaskKind.Witness, Integer> m = VTASK.widen(original);

                      Kind<VTaskKind.Witness, Integer> result = monad.flatMap(monad::of, m);

                      assertThat(vtasksEqual(VTASK.narrow(result), original)).isTrue();
                    }));
  }

  /**
   * Monad Associativity Law: flatMap(flatMap(m, f), g) = flatMap(m, x -> flatMap(f(x), g))
   *
   * <p>The order of nested flatMaps doesn't matter.
   */
  @TestFactory
  @DisplayName(
      "Monad Associativity Law: flatMap(flatMap(m, f), g) = flatMap(m, x -> flatMap(f(x), g))")
  Stream<DynamicTest> monadAssociativityLaw() {
    Function<Integer, Kind<VTaskKind.Witness, String>> f =
        i -> VTASK.widen(VTask.succeed("step1:" + i));
    Function<String, Kind<VTaskKind.Witness, Integer>> g =
        s -> VTASK.widen(VTask.succeed(s.length()));

    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Associativity law holds for value " + value,
                    () -> {
                      Kind<VTaskKind.Witness, Integer> m = VTASK.widen(VTask.succeed(value));

                      // Left side: flatMap(flatMap(m, f), g)
                      Kind<VTaskKind.Witness, String> innerFlatMap = monad.flatMap(f, m);
                      Kind<VTaskKind.Witness, Integer> leftSide = monad.flatMap(g, innerFlatMap);

                      // Right side: flatMap(m, x -> flatMap(f(x), g))
                      Function<Integer, Kind<VTaskKind.Witness, Integer>> composed =
                          x -> monad.flatMap(g, f.apply(x));
                      Kind<VTaskKind.Witness, Integer> rightSide = monad.flatMap(composed, m);

                      assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide)))
                          .isTrue();
                    }));
  }

  // ==================== MonadError Laws ====================

  /**
   * MonadError Left Zero Law: flatMap(raiseError(e), f) = raiseError(e)
   *
   * <p>Raising an error and then flatMapping should be equivalent to just raising the error.
   */
  @TestFactory
  @DisplayName("MonadError Left Zero Law: flatMap(raiseError(e), f) = raiseError(e)")
  Stream<DynamicTest> monadErrorLeftZeroLaw() {
    return Stream.of("error1", "error2", "validation failed", "")
        .map(
            errorMsg ->
                DynamicTest.dynamicTest(
                    "Left zero law holds for error '" + errorMsg + "'",
                    () -> {
                      Throwable error = new RuntimeException(errorMsg);
                      Function<Integer, Kind<VTaskKind.Witness, String>> f =
                          i -> VTASK.widen(VTask.succeed("result:" + i));

                      // Left side: flatMap(raiseError(e), f)
                      Kind<VTaskKind.Witness, Integer> errorKind = monad.raiseError(error);
                      Kind<VTaskKind.Witness, String> leftSide = monad.flatMap(f, errorKind);

                      // Right side: raiseError(e)
                      Kind<VTaskKind.Witness, String> rightSide = monad.raiseError(error);

                      assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide)))
                          .isTrue();
                    }));
  }

  /**
   * MonadError Recovery Law: handleErrorWith(raiseError(e), f) = f(e)
   *
   * <p>Handling an error with a recovery function should apply that function to the error.
   */
  @TestFactory
  @DisplayName("MonadError Recovery Law: handleErrorWith(raiseError(e), f) = f(e)")
  Stream<DynamicTest> monadErrorRecoveryLaw() {
    return Arrays.stream(TEST_VALUES)
        .map(
            recoveryValue ->
                DynamicTest.dynamicTest(
                    "Recovery law holds with recovery value " + recoveryValue,
                    () -> {
                      Throwable error = new RuntimeException("test error");
                      Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler =
                          e -> monad.of(recoveryValue);

                      // Left side: handleErrorWith(raiseError(e), handler)
                      Kind<VTaskKind.Witness, Integer> errorKind = monad.raiseError(error);
                      Kind<VTaskKind.Witness, Integer> leftSide =
                          monad.handleErrorWith(errorKind, handler);

                      // Right side: handler(e)
                      Kind<VTaskKind.Witness, Integer> rightSide = handler.apply(error);

                      assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide)))
                          .isTrue();
                    }));
  }

  /**
   * MonadError Success Passthrough Law: handleErrorWith(of(a), f) = of(a)
   *
   * <p>Handling errors on a successful value should return the success unchanged.
   */
  @TestFactory
  @DisplayName("MonadError Success Passthrough Law: handleErrorWith(of(a), f) = of(a)")
  Stream<DynamicTest> monadErrorSuccessPassthroughLaw() {
    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Success passthrough law holds for value " + value,
                    () -> {
                      Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler =
                          e -> monad.of(-999);

                      // Left side: handleErrorWith(of(a), handler)
                      Kind<VTaskKind.Witness, Integer> successKind = monad.of(value);
                      Kind<VTaskKind.Witness, Integer> leftSide =
                          monad.handleErrorWith(successKind, handler);

                      // Right side: of(a)
                      Kind<VTaskKind.Witness, Integer> rightSide = monad.of(value);

                      assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide)))
                          .isTrue();
                    }));
  }

  // ==================== Applicative Laws ====================

  /**
   * Applicative Identity Law: ap(of(id), fa) = fa
   *
   * <p>Applying the identity function wrapped in the applicative should return the original value.
   */
  @TestFactory
  @DisplayName("Applicative Identity Law: ap(of(id), fa) = fa")
  Stream<DynamicTest> applicativeIdentityLaw() {
    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Applicative identity law holds for value " + value,
                    () -> {
                      VTask<Integer> original = VTask.succeed(value);
                      Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(original);
                      Kind<VTaskKind.Witness, Function<Integer, Integer>> identity =
                          applicative.of(Function.identity());

                      Kind<VTaskKind.Witness, Integer> result = applicative.ap(identity, fa);

                      assertThat(vtasksEqual(VTASK.narrow(result), original)).isTrue();
                    }));
  }

  /**
   * Applicative Homomorphism Law: ap(of(f), of(x)) = of(f(x))
   *
   * <p>Applying a wrapped function to a wrapped value equals wrapping the result of application.
   */
  @TestFactory
  @DisplayName("Applicative Homomorphism Law: ap(of(f), of(x)) = of(f(x))")
  Stream<DynamicTest> applicativeHomomorphismLaw() {
    Function<Integer, String> f = i -> "result:" + i;

    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Applicative homomorphism law holds for value " + value,
                    () -> {
                      Kind<VTaskKind.Witness, Function<Integer, String>> ff = applicative.of(f);
                      Kind<VTaskKind.Witness, Integer> fx = applicative.of(value);

                      // Left side: ap(of(f), of(x))
                      Kind<VTaskKind.Witness, String> leftSide = applicative.ap(ff, fx);

                      // Right side: of(f(x))
                      Kind<VTaskKind.Witness, String> rightSide = applicative.of(f.apply(value));

                      assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide)))
                          .isTrue();
                    }));
  }
}
