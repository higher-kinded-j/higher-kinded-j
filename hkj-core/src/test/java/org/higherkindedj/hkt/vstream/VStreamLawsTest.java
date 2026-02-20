// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Explicit law verification tests for VStream using JUnit 6's @TestFactory.
 *
 * <p>This test class verifies that VStream satisfies all required type class laws:
 *
 * <ul>
 *   <li><b>Functor Laws:</b> Identity and Composition
 *   <li><b>Applicative Laws:</b> Identity, Homomorphism
 *   <li><b>Monad Laws:</b> Left Identity, Right Identity, Associativity
 *   <li><b>Alternative Laws:</b> Left Identity, Right Identity, Associativity
 * </ul>
 *
 * <p>Each law is tested with multiple test values to ensure comprehensive coverage. VStream
 * equality is determined by materialising both streams and comparing the resulting lists.
 */
@DisplayName("VStream Laws - Dynamic Test Factory")
class VStreamLawsTest {

  private final VStreamFunctor functor = VStreamFunctor.INSTANCE;
  private final VStreamApplicative applicative = VStreamApplicative.INSTANCE;
  private final VStreamMonad monad = VStreamMonad.INSTANCE;
  private final VStreamAlternative alternative = VStreamAlternative.INSTANCE;

  // ==================== Test Values ====================

  private static final Integer[] TEST_VALUES = {0, 1, -1, 42, -100, 100};

  /** Compares two VStream results by materialising and comparing lists. */
  private <A> boolean vstreamsEqual(VStream<A> a, VStream<A> b) {
    List<A> listA = a.toList().run();
    List<A> listB = b.toList().run();
    return Objects.equals(listA, listB);
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
                      VStream<Integer> original = VStream.of(value);
                      Kind<VStreamKind.Witness, Integer> fa = VSTREAM.widen(original);

                      Kind<VStreamKind.Witness, Integer> result =
                          functor.map(Function.identity(), fa);

                      assertThat(vstreamsEqual(VSTREAM.narrow(result), original)).isTrue();
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
                      Kind<VStreamKind.Witness, Integer> fa = VSTREAM.widen(VStream.of(value));

                      // Left side: map(g.f, fa)
                      Function<Integer, Integer> composed = f.andThen(g);
                      Kind<VStreamKind.Witness, Integer> leftSide = functor.map(composed, fa);

                      // Right side: map(g, map(f, fa))
                      Kind<VStreamKind.Witness, String> intermediate = functor.map(f, fa);
                      Kind<VStreamKind.Witness, Integer> rightSide = functor.map(g, intermediate);

                      assertThat(vstreamsEqual(VSTREAM.narrow(leftSide), VSTREAM.narrow(rightSide)))
                          .isTrue();
                    }));
  }

  /**
   * Functor Identity Law with multi-element stream.
   *
   * <p>Verifies identity on streams with multiple elements.
   */
  @TestFactory
  @DisplayName("Functor Identity Law (multi-element): map(id, fa) = fa")
  Stream<DynamicTest> functorIdentityLawMultiElement() {
    return Stream.<List<Integer>>of(
            List.of(1, 2, 3), List.of(), List.of(42), List.of(10, 20, 30, 40, 50))
        .map(
            values ->
                DynamicTest.dynamicTest(
                    "Identity law holds for stream " + values,
                    () -> {
                      VStream<Integer> original = VStream.fromList(values);
                      Kind<VStreamKind.Witness, Integer> fa = VSTREAM.widen(original);

                      Kind<VStreamKind.Witness, Integer> result =
                          functor.map(Function.identity(), fa);

                      assertThat(VSTREAM.narrow(result).toList().run()).isEqualTo(values);
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
                      VStream<Integer> original = VStream.of(value);
                      Kind<VStreamKind.Witness, Integer> fa = VSTREAM.widen(original);
                      Kind<VStreamKind.Witness, Function<Integer, Integer>> identity =
                          applicative.of(Function.identity());

                      Kind<VStreamKind.Witness, Integer> result = applicative.ap(identity, fa);

                      assertThat(vstreamsEqual(VSTREAM.narrow(result), original)).isTrue();
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
                      Kind<VStreamKind.Witness, Function<Integer, String>> ff = applicative.of(f);
                      Kind<VStreamKind.Witness, Integer> fx = applicative.of(value);

                      // Left side: ap(of(f), of(x))
                      Kind<VStreamKind.Witness, String> leftSide = applicative.ap(ff, fx);

                      // Right side: of(f(x))
                      Kind<VStreamKind.Witness, String> rightSide = applicative.of(f.apply(value));

                      assertThat(vstreamsEqual(VSTREAM.narrow(leftSide), VSTREAM.narrow(rightSide)))
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
    Function<Integer, Kind<VStreamKind.Witness, String>> f =
        i -> VSTREAM.widen(VStream.of("result:" + i));

    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Left identity law holds for value " + value,
                    () -> {
                      // Left side: flatMap(of(a), f)
                      Kind<VStreamKind.Witness, Integer> ofValue = monad.of(value);
                      Kind<VStreamKind.Witness, String> leftSide = monad.flatMap(f, ofValue);

                      // Right side: f(a)
                      Kind<VStreamKind.Witness, String> rightSide = f.apply(value);

                      assertThat(vstreamsEqual(VSTREAM.narrow(leftSide), VSTREAM.narrow(rightSide)))
                          .isTrue();
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
                      VStream<Integer> original = VStream.of(value);
                      Kind<VStreamKind.Witness, Integer> m = VSTREAM.widen(original);

                      Kind<VStreamKind.Witness, Integer> result = monad.flatMap(monad::of, m);

                      assertThat(vstreamsEqual(VSTREAM.narrow(result), original)).isTrue();
                    }));
  }

  /**
   * Monad Right Identity Law with multi-element stream.
   *
   * <p>Verifies right identity holds for streams with multiple elements.
   */
  @TestFactory
  @DisplayName("Monad Right Identity Law (multi-element): flatMap(m, of) = m")
  Stream<DynamicTest> monadRightIdentityLawMultiElement() {
    return Stream.<List<Integer>>of(List.of(1, 2, 3), List.of(), List.of(10, 20))
        .map(
            values ->
                DynamicTest.dynamicTest(
                    "Right identity law holds for stream " + values,
                    () -> {
                      VStream<Integer> original = VStream.fromList(values);
                      Kind<VStreamKind.Witness, Integer> m = VSTREAM.widen(original);

                      Kind<VStreamKind.Witness, Integer> result = monad.flatMap(monad::of, m);

                      assertThat(VSTREAM.narrow(result).toList().run()).isEqualTo(values);
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
    Function<Integer, Kind<VStreamKind.Witness, String>> f =
        i -> VSTREAM.widen(VStream.of("step1:" + i));
    Function<String, Kind<VStreamKind.Witness, Integer>> g =
        s -> VSTREAM.widen(VStream.of(s.length()));

    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Associativity law holds for value " + value,
                    () -> {
                      Kind<VStreamKind.Witness, Integer> m = VSTREAM.widen(VStream.of(value));

                      // Left side: flatMap(flatMap(m, f), g)
                      Kind<VStreamKind.Witness, String> innerFlatMap = monad.flatMap(f, m);
                      Kind<VStreamKind.Witness, Integer> leftSide = monad.flatMap(g, innerFlatMap);

                      // Right side: flatMap(m, x -> flatMap(f(x), g))
                      Function<Integer, Kind<VStreamKind.Witness, Integer>> composed =
                          x -> monad.flatMap(g, f.apply(x));
                      Kind<VStreamKind.Witness, Integer> rightSide = monad.flatMap(composed, m);

                      assertThat(vstreamsEqual(VSTREAM.narrow(leftSide), VSTREAM.narrow(rightSide)))
                          .isTrue();
                    }));
  }

  // ==================== Alternative Laws ====================

  /**
   * Alternative Left Identity Law: orElse(empty(), () -> fa) = fa
   *
   * <p>Combining empty with any value using orElse should return that value.
   */
  @TestFactory
  @DisplayName("Alternative Left Identity Law: orElse(empty(), () -> fa) = fa")
  Stream<DynamicTest> alternativeLeftIdentityLaw() {
    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Left identity law holds for value " + value,
                    () -> {
                      Kind<VStreamKind.Witness, Integer> fa = VSTREAM.widen(VStream.of(value));
                      Kind<VStreamKind.Witness, Integer> empty = alternative.empty();

                      Kind<VStreamKind.Witness, Integer> result =
                          alternative.orElse(empty, () -> fa);

                      assertThat(vstreamsEqual(VSTREAM.narrow(result), VSTREAM.narrow(fa)))
                          .isTrue();
                    }));
  }

  /**
   * Alternative Right Identity Law: orElse(fa, () -> empty()) = fa
   *
   * <p>Combining any value with empty using orElse should return that value.
   */
  @TestFactory
  @DisplayName("Alternative Right Identity Law: orElse(fa, () -> empty()) = fa")
  Stream<DynamicTest> alternativeRightIdentityLaw() {
    return Arrays.stream(TEST_VALUES)
        .map(
            value ->
                DynamicTest.dynamicTest(
                    "Right identity law holds for value " + value,
                    () -> {
                      Kind<VStreamKind.Witness, Integer> fa = VSTREAM.widen(VStream.of(value));

                      Kind<VStreamKind.Witness, Integer> result =
                          alternative.orElse(fa, alternative::empty);

                      assertThat(vstreamsEqual(VSTREAM.narrow(result), VSTREAM.narrow(fa)))
                          .isTrue();
                    }));
  }

  /**
   * Alternative Associativity Law for concatenation semantics.
   *
   * <p>{@code orElse(fa, () -> orElse(fb, () -> fc)) == orElse(orElse(fa, () -> fb), () -> fc)}
   */
  @TestFactory
  @DisplayName("Alternative Associativity Law (concatenation semantics)")
  Stream<DynamicTest> alternativeAssociativityLaw() {
    return Stream.of(
            new Object[] {List.of(1), List.of(2), List.of(3)},
            new Object[] {List.of(1, 2), List.of(3), List.of(4, 5)},
            new Object[] {List.of(), List.of(1), List.of(2)})
        .map(
            args -> {
              @SuppressWarnings("unchecked")
              List<Integer> aList = (List<Integer>) args[0];
              @SuppressWarnings("unchecked")
              List<Integer> bList = (List<Integer>) args[1];
              @SuppressWarnings("unchecked")
              List<Integer> cList = (List<Integer>) args[2];

              return DynamicTest.dynamicTest(
                  "Associativity law holds for " + aList + ", " + bList + ", " + cList,
                  () -> {
                    Kind<VStreamKind.Witness, Integer> fa = VSTREAM.widen(VStream.fromList(aList));
                    Kind<VStreamKind.Witness, Integer> fb = VSTREAM.widen(VStream.fromList(bList));
                    Kind<VStreamKind.Witness, Integer> fc = VSTREAM.widen(VStream.fromList(cList));

                    // Left side: orElse(fa, () -> orElse(fb, () -> fc))
                    Kind<VStreamKind.Witness, Integer> leftSide =
                        alternative.orElse(fa, () -> alternative.orElse(fb, () -> fc));

                    // Right side: orElse(orElse(fa, () -> fb), () -> fc)
                    Kind<VStreamKind.Witness, Integer> rightSide =
                        alternative.orElse(alternative.orElse(fa, () -> fb), () -> fc);

                    assertThat(vstreamsEqual(VSTREAM.narrow(leftSide), VSTREAM.narrow(rightSide)))
                        .isTrue();
                  });
            });
  }
}
