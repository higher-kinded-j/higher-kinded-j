// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for GenericPath.
 *
 * <p>Verifies that GenericPath satisfies Functor and Monad laws using {@link MaybeMonad} as the
 * underlying Monad implementation.
 *
 * <h2>Functor Laws</h2>
 *
 * <ul>
 *   <li>Identity: {@code path.map(id) == path}
 *   <li>Composition: {@code path.map(f).map(g) == path.map(g.compose(f))}
 * </ul>
 *
 * <h2>Monad Laws</h2>
 *
 * <ul>
 *   <li>Left Identity: {@code GenericPath.pure(a, monad).via(f) == f(a)}
 *   <li>Right Identity: {@code path.via(x -> GenericPath.pure(x, monad)) == path}
 *   <li>Associativity: {@code path.via(f).via(g) == path.via(x -> f(x).via(g))}
 * </ul>
 */
@DisplayName("GenericPath Law Verification Tests")
class GenericPathLawsTest {

  // Use MaybeMonad as the concrete Monad implementation
  private static final MaybeMonad MONAD = MaybeMonad.INSTANCE;

  // Test values
  private static final int TEST_VALUE = 42;

  // Test functions
  private static final Function<Integer, Integer> ADD_ONE = x -> x + 1;
  private static final Function<Integer, Integer> DOUBLE = x -> x * 2;
  private static final Function<Integer, String> INT_TO_STRING = x -> "value:" + x;
  private static final Function<String, Integer> STRING_LENGTH = String::length;

  // Helper to create a GenericPath from a Just value
  private static <A> GenericPath<MaybeKind.Witness, A> just(A value) {
    return GenericPath.of(MAYBE.just(value), MONAD);
  }

  // Helper to create a GenericPath from Nothing
  private static <A> GenericPath<MaybeKind.Witness, A> nothing() {
    return GenericPath.of(MAYBE.nothing(), MONAD);
  }

  // Helper to narrow the result for comparison
  private static <A> Maybe<A> narrow(GenericPath<MaybeKind.Witness, A> path) {
    return MAYBE.narrow(path.runKind());
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawsTests {

    @TestFactory
    @DisplayName("Functor Identity Law: path.map(id) == path")
    Stream<DynamicTest> functorIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Identity law holds for Just",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = just(TEST_VALUE);
                GenericPath<MaybeKind.Witness, Integer> result = path.map(Function.identity());
                assertThat(narrow(result)).isEqualTo(narrow(path));
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for Nothing",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = nothing();
                GenericPath<MaybeKind.Witness, Integer> result = path.map(Function.identity());
                assertThat(narrow(result)).isEqualTo(narrow(path));
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for Just",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = just(TEST_VALUE);

                // Left side: path.map(f).map(g)
                GenericPath<MaybeKind.Witness, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);

                // Right side: path.map(g.compose(f))
                GenericPath<MaybeKind.Witness, Integer> rightSide =
                    path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for Nothing",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = nothing();

                GenericPath<MaybeKind.Witness, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                GenericPath<MaybeKind.Witness, Integer> rightSide =
                    path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = just(TEST_VALUE);

                // map Integer -> String -> Integer
                GenericPath<MaybeKind.Witness, Integer> leftSide =
                    path.map(INT_TO_STRING).map(STRING_LENGTH);
                GenericPath<MaybeKind.Witness, Integer> rightSide =
                    path.map(INT_TO_STRING.andThen(STRING_LENGTH));

                assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    // Monadic functions for testing
    private final Function<Integer, GenericPath<MaybeKind.Witness, String>> intToGenericString =
        x -> x > 0 ? just("positive:" + x) : nothing();

    private final Function<String, GenericPath<MaybeKind.Witness, Integer>> stringToGenericInt =
        s -> s.length() > 5 ? just(s.length()) : nothing();

    private final Function<Integer, GenericPath<MaybeKind.Witness, Integer>> safeDouble =
        x -> x < 1000 ? just(x * 2) : nothing();

    @TestFactory
    @DisplayName("Left Identity Law: GenericPath.pure(a, monad).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity when f returns Just",
              () -> {
                int value = 10;

                // Left side: GenericPath.pure(a, monad).via(f)
                GenericPath<MaybeKind.Witness, String> leftSide =
                    GenericPath.pure(value, MONAD).via(intToGenericString);

                // Right side: f(a)
                GenericPath<MaybeKind.Witness, String> rightSide = intToGenericString.apply(value);

                assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Left identity when f returns Nothing",
              () -> {
                int value = -5;

                GenericPath<MaybeKind.Witness, String> leftSide =
                    GenericPath.pure(value, MONAD).via(intToGenericString);
                GenericPath<MaybeKind.Witness, String> rightSide = intToGenericString.apply(value);

                assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> GenericPath.pure(x, monad)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for Just",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = just(TEST_VALUE);

                GenericPath<MaybeKind.Witness, Integer> result =
                    path.via(x -> GenericPath.pure(x, MONAD));

                assertThat(narrow(result)).isEqualTo(narrow(path));
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for Nothing",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = nothing();

                GenericPath<MaybeKind.Witness, Integer> result =
                    path.via(x -> GenericPath.pure(x, MONAD));

                assertThat(narrow(result)).isEqualTo(narrow(path));
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for Just with successful chain",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = just(10);

                // Left side: path.via(f).via(g)
                GenericPath<MaybeKind.Witness, Integer> leftSide =
                    path.via(intToGenericString).via(stringToGenericInt);

                // Right side: path.via(x -> f(x).via(g))
                GenericPath<MaybeKind.Witness, Integer> rightSide =
                    path.via(x -> intToGenericString.apply(x).via(stringToGenericInt));

                assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Associativity holds when first function returns Nothing",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = just(-5);

                GenericPath<MaybeKind.Witness, Integer> leftSide =
                    path.via(intToGenericString).via(stringToGenericInt);
                GenericPath<MaybeKind.Witness, Integer> rightSide =
                    path.via(x -> intToGenericString.apply(x).via(stringToGenericInt));

                assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Associativity holds when second function returns Nothing",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path =
                    just(1); // produces "positive:1" (length 10)

                GenericPath<MaybeKind.Witness, Integer> leftSide =
                    path.via(intToGenericString).via(stringToGenericInt);
                GenericPath<MaybeKind.Witness, Integer> rightSide =
                    path.via(x -> intToGenericString.apply(x).via(stringToGenericInt));

                assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for Nothing",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = nothing();

                GenericPath<MaybeKind.Witness, Integer> leftSide =
                    path.via(intToGenericString).via(stringToGenericInt);
                GenericPath<MaybeKind.Witness, Integer> rightSide =
                    path.via(x -> intToGenericString.apply(x).via(stringToGenericInt));

                assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Associativity with same-type chain",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = just(100);
                Function<Integer, GenericPath<MaybeKind.Witness, Integer>> addTen =
                    x -> just(x + 10);

                GenericPath<MaybeKind.Witness, Integer> leftSide = path.via(safeDouble).via(addTen);
                GenericPath<MaybeKind.Witness, Integer> rightSide =
                    path.via(x -> safeDouble.apply(x).via(addTen));

                assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("map preserves structure")
    Stream<DynamicTest> mapPreservesStructure() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "map over Just produces Just",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = just(TEST_VALUE);
                GenericPath<MaybeKind.Witness, String> result = path.map(Object::toString);
                assertThat(narrow(result).isJust()).isTrue();
              }),
          DynamicTest.dynamicTest(
              "map over Nothing produces Nothing",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = nothing();
                GenericPath<MaybeKind.Witness, String> result = path.map(Object::toString);
                assertThat(narrow(result).isNothing()).isTrue();
              }));
    }

    @TestFactory
    @DisplayName("via is consistent with flatMap")
    @SuppressWarnings("unchecked")
    Stream<DynamicTest> viaConsistentWithFlatMap() {
      Function<Integer, GenericPath<MaybeKind.Witness, String>> f = x -> just("value:" + x);

      return Stream.of(
          DynamicTest.dynamicTest(
              "via and flatMap produce same result for Just",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = just(TEST_VALUE);

                GenericPath<MaybeKind.Witness, String> viaResult = path.via(f);
                // flatMap returns Chainable<B>, cast to GenericPath
                GenericPath<MaybeKind.Witness, String> flatMapResult =
                    (GenericPath<MaybeKind.Witness, String>) path.flatMap(f);

                assertThat(narrow(viaResult)).isEqualTo(narrow(flatMapResult));
              }),
          DynamicTest.dynamicTest(
              "via and flatMap produce same result for Nothing",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = nothing();

                GenericPath<MaybeKind.Witness, String> viaResult = path.via(f);
                // flatMap returns Chainable<B>, cast to GenericPath
                GenericPath<MaybeKind.Witness, String> flatMapResult =
                    (GenericPath<MaybeKind.Witness, String>) path.flatMap(f);

                assertThat(narrow(viaResult)).isEqualTo(narrow(flatMapResult));
              }));
    }

    @TestFactory
    @DisplayName("GenericPath preserves underlying Monad")
    Stream<DynamicTest> genericPathPreservesMonad() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "monad() returns the provided Monad",
              () -> {
                GenericPath<MaybeKind.Witness, Integer> path = just(TEST_VALUE);
                assertThat(path.monad()).isSameAs(MONAD);
              }),
          DynamicTest.dynamicTest(
              "runKind() returns underlying Kind",
              () -> {
                Kind<MaybeKind.Witness, Integer> kind = MAYBE.just(TEST_VALUE);
                GenericPath<MaybeKind.Witness, Integer> path = GenericPath.of(kind, MONAD);
                assertThat(path.runKind()).isEqualTo(kind);
              }));
    }
  }
}
