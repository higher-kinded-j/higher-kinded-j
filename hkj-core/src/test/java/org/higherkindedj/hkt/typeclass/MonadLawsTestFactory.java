// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryMonad;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.hkt.vtask.VTaskMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Dynamic test factory for Monad laws using JUnit 6's @TestFactory.
 *
 * <p>This class demonstrates how to use {@code @TestFactory} to generate tests dynamically at
 * runtime, providing comprehensive law testing across all monad implementations with minimal
 * boilerplate.
 *
 * <p>Monad laws tested:
 *
 * <ul>
 *   <li><b>Left Identity:</b> {@code flatMap(of(a), f) == f(a)}
 *   <li><b>Right Identity:</b> {@code flatMap(m, of) == m}
 *   <li><b>Associativity:</b> {@code flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x),
 *       g))}
 * </ul>
 *
 * <p>Benefits of @TestFactory approach:
 *
 * <ul>
 *   <li>Tests are generated at runtime based on actual monad implementations
 *   <li>Adding new monads automatically adds test coverage
 *   <li>Clear, structured test output showing which monad/law combination passed/failed
 *   <li>Each test runs independently with proper isolation
 * </ul>
 */
@DisplayName("Monad Laws - Dynamic Test Factory")
class MonadLawsTestFactory {

  /**
   * Test data record containing all information needed to test a monad.
   *
   * @param <M> the monad type constructor
   */
  record MonadTestData<M extends WitnessArity<TypeArity.Unary>>(
      String name, Monad<M> monad, Kind<M, Integer> testValue, EqualityChecker<M> equalityChecker) {

    static <M extends WitnessArity<TypeArity.Unary>> MonadTestData<M> of(
        String name, Monad<M> monad, Kind<M, Integer> testValue, EqualityChecker<M> checker) {
      return new MonadTestData<>(name, monad, testValue, checker);
    }
  }

  /** Functional interface for checking equality of Kind values */
  @FunctionalInterface
  interface EqualityChecker<M extends WitnessArity<TypeArity.Unary>> {
    <A> boolean areEqual(Kind<M, A> a, Kind<M, A> b);
  }

  /**
   * Provides test data for all monad implementations.
   *
   * <p>This is a centralized source of test data. Adding a new monad implementation requires only
   * adding one line here, and all law tests will automatically cover it.
   */
  private static Stream<MonadTestData<?>> allMonads() {
    return Stream.of(
        MonadTestData.of(
            "Maybe",
            MaybeMonad.INSTANCE,
            MAYBE.widen(Maybe.just(42)),
            new EqualityChecker<MaybeKind.Witness>() {
              @Override
              public <A> boolean areEqual(
                  Kind<MaybeKind.Witness, A> a, Kind<MaybeKind.Witness, A> b) {
                return MAYBE.narrow(a).equals(MAYBE.narrow(b));
              }
            }),
        MonadTestData.of(
            "Either",
            EitherMonad.<String>instance(),
            EITHER.widen(Either.right(42)),
            new EqualityChecker<EitherKind.Witness<String>>() {
              @Override
              public <A> boolean areEqual(
                  Kind<EitherKind.Witness<String>, A> a, Kind<EitherKind.Witness<String>, A> b) {
                return EITHER.narrow(a).equals(EITHER.narrow(b));
              }
            }),
        MonadTestData.of(
            "Try",
            TryMonad.INSTANCE,
            TRY.widen(Try.success(42)),
            new EqualityChecker<TryKind.Witness>() {
              @Override
              public <A> boolean areEqual(Kind<TryKind.Witness, A> a, Kind<TryKind.Witness, A> b) {
                Try<A> tryA = TRY.narrow(a);
                Try<A> tryB = TRY.narrow(b);
                // For Try, compare success/failure status and values
                if (tryA.isSuccess() != tryB.isSuccess()) {
                  return false;
                }
                if (tryA.isSuccess()) {
                  try {
                    return Objects.equals(tryA.get(), tryB.get());
                  } catch (Throwable e) {
                    return false;
                  }
                } else { // Both are failures, compare them semantically
                  Throwable causeA = ((Try.Failure<A>) tryA).cause();
                  Throwable causeB = ((Try.Failure<A>) tryB).cause();
                  return causeA.getClass().equals(causeB.getClass())
                      && Objects.equals(causeA.getMessage(), causeB.getMessage());
                }
              }
            }),
        MonadTestData.of(
            "List",
            ListMonad.INSTANCE,
            LIST.widen(List.of(42)),
            new EqualityChecker<ListKind.Witness>() {
              @Override
              public <A> boolean areEqual(
                  Kind<ListKind.Witness, A> a, Kind<ListKind.Witness, A> b) {
                return LIST.narrow(a).equals(LIST.narrow(b));
              }
            }),
        MonadTestData.of(
            "Optional",
            OptionalMonad.INSTANCE,
            OPTIONAL.widen(Optional.of(42)),
            new EqualityChecker<OptionalKind.Witness>() {
              @Override
              public <A> boolean areEqual(
                  Kind<OptionalKind.Witness, A> a, Kind<OptionalKind.Witness, A> b) {
                return OPTIONAL.narrow(a).equals(OPTIONAL.narrow(b));
              }
            }),
        MonadTestData.of(
            "VTask",
            VTaskMonad.INSTANCE,
            VTASK.widen(VTask.succeed(42)),
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

  /**
   * Dynamically generates tests for the left identity law: {@code flatMap(of(a), f) == f(a)}
   *
   * <p>This test factory creates one test per monad implementation, each verifying that wrapping a
   * value with {@code of} and then flat-mapping a function is equivalent to just applying the
   * function.
   */
  @TestFactory
  @DisplayName("Left Identity Law: flatMap(of(a), f) = f(a)")
  Stream<DynamicTest> leftIdentityLaw() {
    return allMonads()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies left identity law", () -> runLeftIdentityTest(data)));
  }

  private static <M extends WitnessArity<TypeArity.Unary>> void runLeftIdentityTest(
      MonadTestData<M> data) {
    Monad<M> monad = data.monad();
    EqualityChecker<M> checker = data.equalityChecker();

    int testValue = 5;
    Function<Integer, Kind<M, String>> f = i -> monad.of("result:" + i);

    // Left side: flatMap(of(a), f)
    Kind<M, Integer> ofValue = monad.of(testValue);
    Kind<M, String> leftSide = monad.flatMap(f, ofValue);

    // Right side: f(a)
    Kind<M, String> rightSide = f.apply(testValue);

    assertThat(checker.areEqual(leftSide, rightSide)).isTrue();
  }

  /**
   * Dynamically generates tests for the right identity law: {@code flatMap(m, of) == m}
   *
   * <p>This test factory creates one test per monad implementation, each verifying that
   * flat-mapping with {@code of} returns the original monadic value unchanged.
   */
  @TestFactory
  @DisplayName("Right Identity Law: flatMap(m, of) = m")
  Stream<DynamicTest> rightIdentityLaw() {
    return allMonads()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies right identity law",
                    () -> runRightIdentityTest(data)));
  }

  private static <M extends WitnessArity<TypeArity.Unary>> void runRightIdentityTest(
      MonadTestData<M> data) {
    Monad<M> monad = data.monad();
    Kind<M, Integer> testValue = data.testValue();
    EqualityChecker<M> checker = data.equalityChecker();

    // flatMap(m, of)
    Kind<M, Integer> result = monad.flatMap(monad::of, testValue);

    assertThat(checker.areEqual(result, testValue)).isTrue();
  }

  /**
   * Dynamically generates tests for the associativity law: {@code flatMap(flatMap(m, f), g) ==
   * flatMap(m, x -> flatMap(f(x), g))}
   *
   * <p>This test factory creates one test per monad implementation, each verifying that the order
   * of nested flatMaps doesn't matter.
   */
  @TestFactory
  @DisplayName("Associativity Law: flatMap(flatMap(m, f), g) = flatMap(m, x -> flatMap(f(x), g))")
  Stream<DynamicTest> associativityLaw() {
    return allMonads()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies associativity law",
                    () -> runAssociativityTest(data)));
  }

  private static <M extends WitnessArity<TypeArity.Unary>> void runAssociativityTest(
      MonadTestData<M> data) {
    Monad<M> monad = data.monad();
    Kind<M, Integer> testValue = data.testValue();
    EqualityChecker<M> checker = data.equalityChecker();

    Function<Integer, Kind<M, String>> f = i -> monad.of("step1:" + i);
    Function<String, Kind<M, Integer>> g = s -> monad.of(s.length());

    // Left side: flatMap(flatMap(m, f), g)
    Kind<M, String> innerFlatMap = monad.flatMap(f, testValue);
    Kind<M, Integer> leftSide = monad.flatMap(g, innerFlatMap);

    // Right side: flatMap(m, x -> flatMap(f(x), g))
    Function<Integer, Kind<M, Integer>> composed = x -> monad.flatMap(g, f.apply(x));
    Kind<M, Integer> rightSide = monad.flatMap(composed, testValue);

    assertThat(checker.areEqual(leftSide, rightSide)).isTrue();
  }

  /**
   * Dynamically generates tests verifying that of lifts a value into the monad.
   *
   * <p>This is a derived property that helps verify correct monad implementation.
   */
  @TestFactory
  @DisplayName("of lifts values into the monad")
  Stream<DynamicTest> ofLiftsValue() {
    return allMonads()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " of lifts values correctly", () -> runOfLiftsValueTest(data)));
  }

  private static <M extends WitnessArity<TypeArity.Unary>> void runOfLiftsValueTest(
      MonadTestData<M> data) {
    Monad<M> monad = data.monad();
    EqualityChecker<M> checker = data.equalityChecker();

    int testValue = 100;
    Kind<M, Integer> result = monad.of(testValue);

    // Verify by extracting with flatMap and identity
    Kind<M, Integer> extracted = monad.flatMap(monad::of, result);

    assertThat(checker.areEqual(result, extracted)).isTrue();
  }
}
