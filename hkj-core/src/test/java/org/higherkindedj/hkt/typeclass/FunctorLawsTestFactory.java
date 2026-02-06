// Copyright (c) 2025 Magnus Smith
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
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherFunctor;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryFunctor;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskFunctor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Dynamic test factory for Functor laws using JUnit 6's @TestFactory.
 *
 * <p>This class demonstrates how to use {@code @TestFactory} to generate tests dynamically at
 * runtime, providing comprehensive law testing across all functor implementations with minimal
 * boilerplate.
 *
 * <p>Functor laws tested:
 *
 * <ul>
 *   <li><b>Identity:</b> {@code map(id, fa) == fa}
 *   <li><b>Composition:</b> {@code map(g ∘ f, fa) == map(g, map(f, fa))}
 * </ul>
 *
 * <p>Benefits of @TestFactory approach:
 *
 * <ul>
 *   <li>Tests are generated at runtime based on actual functor implementations
 *   <li>Adding new functors automatically adds test coverage
 *   <li>Clear, structured test output showing which functor/law combination passed/failed
 *   <li>Each test runs independently with proper isolation
 * </ul>
 */
@DisplayName("Functor Laws - Dynamic Test Factory")
class FunctorLawsTestFactory {

  /**
   * Test data record containing all information needed to test a functor.
   *
   * @param <F> the functor type constructor
   */
  record FunctorTestData<F extends WitnessArity<TypeArity.Unary>>(
      String name,
      Functor<F> functor,
      Kind<F, Integer> testValue,
      Function<Kind<F, Integer>, Integer> extractor) {

    static <F extends WitnessArity<TypeArity.Unary>> FunctorTestData<F> of(
        String name,
        Functor<F> functor,
        Kind<F, Integer> testValue,
        Function<Kind<F, Integer>, Integer> extractor) {
      return new FunctorTestData<>(name, functor, testValue, extractor);
    }
  }

  /**
   * Provides test data for all functor implementations.
   *
   * <p>This is a centralized source of test data. Adding a new functor implementation requires only
   * adding one line here, and all law tests will automatically cover it.
   */
  private static Stream<FunctorTestData<?>> allFunctors() {
    return Stream.of(
        FunctorTestData.of(
            "Maybe",
            MaybeFunctor.INSTANCE,
            MAYBE.widen(Maybe.just(42)),
            kind -> MAYBE.narrow(kind).orElse(-1)),
        FunctorTestData.of(
            "Either",
            EitherFunctor.<String>instance(),
            EITHER.widen(Either.right(42)),
            kind -> EITHER.narrow(kind).fold(err -> -1, val -> val)),
        FunctorTestData.of(
            "Try",
            new TryFunctor(),
            TRY.widen(Try.success(42)),
            kind -> {
              try {
                return TRY.narrow(kind).get();
              } catch (Throwable e) {
                return -1;
              }
            }),
        FunctorTestData.of(
            "List",
            ListMonad.INSTANCE,
            LIST.widen(List.of(42)),
            kind -> LIST.narrow(kind).isEmpty() ? -1 : LIST.narrow(kind).get(0)),
        FunctorTestData.of(
            "Optional",
            OptionalMonad.INSTANCE,
            OPTIONAL.widen(Optional.of(42)),
            kind -> OPTIONAL.narrow(kind).orElse(-1)),
        FunctorTestData.of(
            "VTask",
            VTaskFunctor.INSTANCE,
            VTASK.widen(VTask.succeed(42)),
            kind -> {
              try {
                return VTASK.narrow(kind).run();
              } catch (Throwable e) {
                return -1;
              }
            }));
  }

  /** Helper method to test identity law for a specific functor */
  private <F extends WitnessArity<TypeArity.Unary>> void testIdentityLaw(FunctorTestData<F> data) {
    Functor<F> functor = data.functor();
    Kind<F, Integer> testValue = data.testValue();
    Function<Kind<F, Integer>, Integer> extractor = data.extractor();

    Function<Integer, Integer> identity = x -> x;
    Kind<F, Integer> result = functor.map(identity, testValue);

    // Extract values and compare
    Integer original = extractor.apply(testValue);
    Integer mapped = extractor.apply(result);
    assertThat(mapped).isEqualTo(original);
  }

  /**
   * Dynamically generates tests for the identity law: {@code map(id, fa) == fa}
   *
   * <p>This test factory creates one test per functor implementation, each verifying that mapping
   * the identity function over a functor returns the original functor unchanged.
   */
  @TestFactory
  @DisplayName("Identity Law: map(id, fa) = fa")
  Stream<DynamicTest> identityLaw() {
    return allFunctors()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies identity law", () -> testIdentityLaw(data)));
  }

  /** Helper method to test composition law for a specific functor */
  private <F extends WitnessArity<TypeArity.Unary>> void testCompositionLaw(
      FunctorTestData<F> data) {
    Functor<F> functor = data.functor();
    Kind<F, Integer> testValue = data.testValue();
    Function<Kind<F, Integer>, Integer> extractor = data.extractor();

    Function<Integer, String> f = i -> "value:" + i;
    Function<String, Integer> g = String::length;

    // Left side: map(g ∘ f, fa)
    Function<Integer, Integer> composed = i -> g.apply(f.apply(i));
    Kind<F, Integer> leftSide = functor.map(composed, testValue);

    // Right side: map(g, map(f, fa))
    Kind<F, String> intermediate = functor.map(f, testValue);
    Kind<F, Integer> rightSide = functor.map(g, intermediate);

    // Extract and compare
    Integer leftResult = extractor.apply(leftSide);
    Integer rightResult = extractor.apply(rightSide);
    assertThat(leftResult).isEqualTo(rightResult);
  }

  /**
   * Dynamically generates tests for the composition law: {@code map(g ∘ f, fa) == map(g, map(f,
   * fa))}
   *
   * <p>This test factory creates one test per functor implementation, each verifying that mapping a
   * composed function is equivalent to mapping each function in sequence.
   */
  @TestFactory
  @DisplayName("Composition Law: map(g ∘ f) = map(g) ∘ map(f)")
  Stream<DynamicTest> compositionLaw() {
    return allFunctors()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies composition law", () -> testCompositionLaw(data)));
  }

  /** Helper method to test that map applies function correctly */
  private <F extends WitnessArity<TypeArity.Unary>> void testMapAppliesFunction(
      FunctorTestData<F> data) {
    Functor<F> functor = data.functor();
    Kind<F, Integer> testValue = data.testValue();
    Function<Kind<F, Integer>, Integer> extractor = data.extractor();

    Function<Integer, Integer> addTen = x -> x + 10;
    Kind<F, Integer> result = functor.map(addTen, testValue);

    Integer original = extractor.apply(testValue);
    Integer mapped = extractor.apply(result);
    assertThat(mapped).isEqualTo(original + 10);
  }

  /**
   * Dynamically generates tests verifying that map applies the function to the wrapped value.
   *
   * <p>This is a derived property that helps verify correct functor implementation beyond just law
   * compliance.
   */
  @TestFactory
  @DisplayName("map applies function to wrapped value")
  Stream<DynamicTest> mapAppliesFunction() {
    return allFunctors()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " applies function correctly",
                    () -> testMapAppliesFunction(data)));
  }
}
