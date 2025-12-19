// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for FreePath.
 *
 * <p>Verifies that FreePath satisfies Functor and Monad laws when interpreted.
 */
@DisplayName("FreePath Law Verification Tests")
class FreePathLawsTest {

  private static final int TEST_VALUE = 42;
  private static final Monad<MaybeKind.Witness> MAYBE_MONAD = MaybeMonad.INSTANCE;
  private static final Natural<MaybeKind.Witness, MaybeKind.Witness> IDENTITY_NAT =
      Natural.identity();

  private static final Function<Integer, Integer> ADD_ONE = x -> x + 1;
  private static final Function<Integer, Integer> DOUBLE = x -> x * 2;
  private static final Function<Integer, String> INT_TO_STRING = x -> "value:" + x;
  private static final Function<String, Integer> STRING_LENGTH = String::length;

  // Helper to run a FreePath and get value
  private <A> A run(FreePath<MaybeKind.Witness, A> path) {
    GenericPath<MaybeKind.Witness, A> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);
    return MAYBE.narrow(result.runKind()).get();
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawsTests {

    @TestFactory
    @DisplayName("Functor Identity Law: path.map(id) == path")
    Stream<DynamicTest> functorIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Identity law holds for pure value",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.pure(TEST_VALUE, MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> result = path.map(Function.identity());
                assertThat(run(result)).isEqualTo(run(path));
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for lifted value",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.liftF(MAYBE.just(TEST_VALUE), MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> result = path.map(Function.identity());
                assertThat(run(result)).isEqualTo(run(path));
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for chained value",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.pure(TEST_VALUE, MaybeMonad.INSTANCE)
                        .via(x -> FreePath.pure(x * 2, MaybeMonad.INSTANCE));
                FreePath<MaybeKind.Witness, Integer> result = path.map(Function.identity());
                assertThat(run(result)).isEqualTo(run(path));
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for pure value",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.pure(TEST_VALUE, MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                FreePath<MaybeKind.Witness, Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(run(leftSide)).isEqualTo(run(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.pure(TEST_VALUE, MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> leftSide =
                    path.map(INT_TO_STRING).map(STRING_LENGTH);
                FreePath<MaybeKind.Witness, Integer> rightSide =
                    path.map(INT_TO_STRING.andThen(STRING_LENGTH));
                assertThat(run(leftSide)).isEqualTo(run(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for lifted value",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.liftF(MAYBE.just(TEST_VALUE), MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                FreePath<MaybeKind.Witness, Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(run(leftSide)).isEqualTo(run(rightSide));
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, FreePath<MaybeKind.Witness, String>> intToFreeString =
        x -> FreePath.pure("result:" + x, MaybeMonad.INSTANCE);

    private final Function<String, FreePath<MaybeKind.Witness, Integer>> stringToFreeInt =
        s -> FreePath.pure(s.length(), MaybeMonad.INSTANCE);

    private final Function<Integer, FreePath<MaybeKind.Witness, Integer>> liftedDouble =
        x -> FreePath.liftF(MAYBE.just(x * 2), MaybeMonad.INSTANCE);

    @TestFactory
    @DisplayName("Left Identity Law: FreePath.pure(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with pure function",
              () -> {
                int value = 10;
                FreePath<MaybeKind.Witness, String> leftSide =
                    FreePath.pure(value, MaybeMonad.INSTANCE).via(intToFreeString);
                FreePath<MaybeKind.Witness, String> rightSide = intToFreeString.apply(value);
                assertThat(run(leftSide)).isEqualTo(run(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Left identity with lifted function",
              () -> {
                int value = 10;
                FreePath<MaybeKind.Witness, Integer> leftSide =
                    FreePath.pure(value, MaybeMonad.INSTANCE).via(liftedDouble);
                FreePath<MaybeKind.Witness, Integer> rightSide = liftedDouble.apply(value);
                assertThat(run(leftSide)).isEqualTo(run(rightSide));
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> FreePath.pure(x)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for pure value",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.pure(TEST_VALUE, MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> result =
                    path.via(x -> FreePath.pure(x, MaybeMonad.INSTANCE));
                assertThat(run(result)).isEqualTo(run(path));
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for lifted value",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.liftF(MAYBE.just(TEST_VALUE), MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> result =
                    path.via(x -> FreePath.pure(x, MaybeMonad.INSTANCE));
                assertThat(run(result)).isEqualTo(run(path));
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for pure value",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(10, MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> leftSide =
                    path.via(intToFreeString).via(stringToFreeInt);
                FreePath<MaybeKind.Witness, Integer> rightSide =
                    path.via(x -> intToFreeString.apply(x).via(stringToFreeInt));
                assertThat(run(leftSide)).isEqualTo(run(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for lifted value",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.liftF(MAYBE.just(10), MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> leftSide =
                    path.via(intToFreeString).via(stringToFreeInt);
                FreePath<MaybeKind.Witness, Integer> rightSide =
                    path.via(x -> intToFreeString.apply(x).via(stringToFreeInt));
                assertThat(run(leftSide)).isEqualTo(run(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Associativity with lifted functions",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(5, MaybeMonad.INSTANCE);
                Function<Integer, FreePath<MaybeKind.Witness, Integer>> f =
                    x -> FreePath.liftF(MAYBE.just(x + 1), MaybeMonad.INSTANCE);
                Function<Integer, FreePath<MaybeKind.Witness, Integer>> g =
                    x -> FreePath.liftF(MAYBE.just(x * 2), MaybeMonad.INSTANCE);

                FreePath<MaybeKind.Witness, Integer> leftSide = path.via(f).via(g);
                FreePath<MaybeKind.Witness, Integer> rightSide = path.via(x -> f.apply(x).via(g));
                assertThat(run(leftSide)).isEqualTo(run(rightSide));
              }));
    }
  }

  @Nested
  @DisplayName("Interpretation Laws")
  class InterpretationLawsTests {

    @TestFactory
    @DisplayName("Interpretation preserves structure")
    Stream<DynamicTest> interpretationTests() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "foldMap preserves pure values",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.pure(TEST_VALUE, MaybeMonad.INSTANCE);
                assertThat(run(path)).isEqualTo(TEST_VALUE);
              }),
          DynamicTest.dynamicTest(
              "foldMap preserves lifted values",
              () -> {
                Kind<MaybeKind.Witness, Integer> just = MAYBE.just(TEST_VALUE);
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.liftF(just, MaybeMonad.INSTANCE);
                assertThat(run(path)).isEqualTo(TEST_VALUE);
              }),
          DynamicTest.dynamicTest(
              "foldMap preserves chained operations",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.pure(10, MaybeMonad.INSTANCE)
                        .via(x -> FreePath.pure(x * 2, MaybeMonad.INSTANCE))
                        .via(x -> FreePath.pure(x + 1, MaybeMonad.INSTANCE));
                assertThat(run(path)).isEqualTo(21);
              }),
          DynamicTest.dynamicTest(
              "foldMap preserves map operations",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.pure(10, MaybeMonad.INSTANCE).map(x -> x * 2).map(x -> x + 1);
                assertThat(run(path)).isEqualTo(21);
              }));
    }
  }

  @Nested
  @DisplayName("Combinable Laws")
  class CombinableLawsTests {

    @TestFactory
    @DisplayName("zipWith behaves correctly")
    Stream<DynamicTest> zipWithTests() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "zipWith combines pure values",
              () -> {
                FreePath<MaybeKind.Witness, Integer> a = FreePath.pure(10, MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> b = FreePath.pure(20, MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> result = a.zipWith(b, Integer::sum);
                assertThat(run(result)).isEqualTo(30);
              }),
          DynamicTest.dynamicTest(
              "zipWith combines lifted values",
              () -> {
                FreePath<MaybeKind.Witness, Integer> a =
                    FreePath.liftF(MAYBE.just(10), MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> b =
                    FreePath.liftF(MAYBE.just(20), MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> result = a.zipWith(b, Integer::sum);
                assertThat(run(result)).isEqualTo(30);
              }),
          DynamicTest.dynamicTest(
              "zipWith with type-changing combiner",
              () -> {
                FreePath<MaybeKind.Witness, String> a = FreePath.pure("hello", MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, Integer> b = FreePath.pure(3, MaybeMonad.INSTANCE);
                FreePath<MaybeKind.Witness, String> result = a.zipWith(b, (s, n) -> s.repeat(n));
                assertThat(run(result)).isEqualTo("hellohellohello");
              }));
    }
  }

  @Nested
  @DisplayName("Accessor Methods")
  class AccessorMethodsTests {

    @TestFactory
    @DisplayName("Accessor methods work correctly")
    Stream<DynamicTest> accessorTests() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "toFree returns underlying Free",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.pure(TEST_VALUE, MaybeMonad.INSTANCE);
                var free = path.toFree();
                Kind<MaybeKind.Witness, Integer> result = free.foldMap(IDENTITY_NAT, MAYBE_MONAD);
                assertThat(MAYBE.narrow(result).get()).isEqualTo(TEST_VALUE);
              }),
          DynamicTest.dynamicTest(
              "functor returns the functor instance",
              () -> {
                FreePath<MaybeKind.Witness, Integer> path =
                    FreePath.pure(TEST_VALUE, MaybeMonad.INSTANCE);
                assertThat(path.functor()).isEqualTo(MaybeMonad.INSTANCE);
              }));
    }
  }
}
