// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Explicit Functor and Monad ({@code via}) law verification for {@link EitherOrBothPath} over a
 * fixed set of {@code Left}/{@code Right}/{@code Both} fixtures.
 */
@DisplayName("EitherOrBothPath Law Verification")
class EitherOrBothPathLawsTest {

  private static final Semigroup<String> SG = (a, b) -> a + b;

  private static List<EitherOrBothPath<String, Integer>> fixtures() {
    return List.of(Path.left("e", SG), Path.right(5, SG), Path.both("w", 5, SG));
  }

  private static String label(EitherOrBothPath<String, Integer> p) {
    return p.run().toString();
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {

    @TestFactory
    Stream<DynamicTest> identity() {
      return fixtures().stream()
          .map(
              p ->
                  DynamicTest.dynamicTest(
                      "identity on " + label(p),
                      () -> assertThat(p.map(Function.identity()).run()).isEqualTo(p.run())));
    }

    @TestFactory
    Stream<DynamicTest> composition() {
      Function<Integer, Integer> f = x -> x + 1;
      Function<Integer, Integer> g = x -> x * 2;
      return fixtures().stream()
          .map(
              p ->
                  DynamicTest.dynamicTest(
                      "composition on " + label(p),
                      () ->
                          assertThat(p.map(f).map(g).run()).isEqualTo(p.map(f.andThen(g)).run())));
    }
  }

  @Nested
  @DisplayName("Monad Laws (via)")
  class MonadLaws {

    private final Function<Integer, EitherOrBothPath<String, Integer>> f =
        x -> Path.both("f", x + 1, SG);
    private final Function<Integer, EitherOrBothPath<String, Integer>> g =
        x -> Path.both("g", x * 2, SG);

    @TestFactory
    Stream<DynamicTest> leftIdentity() {
      return Stream.of(0, 5, -3)
          .map(
              a ->
                  DynamicTest.dynamicTest(
                      "left identity on " + a,
                      () ->
                          assertThat(Path.right(a, SG).via(f::apply).run())
                              .isEqualTo(f.apply(a).run())));
    }

    @TestFactory
    Stream<DynamicTest> rightIdentity() {
      return fixtures().stream()
          .map(
              p ->
                  DynamicTest.dynamicTest(
                      "right identity on " + label(p),
                      () -> assertThat(p.via(x -> Path.right(x, SG)).run()).isEqualTo(p.run())));
    }

    @TestFactory
    Stream<DynamicTest> associativity() {
      return fixtures().stream()
          .map(
              p ->
                  DynamicTest.dynamicTest(
                      "associativity on " + label(p),
                      () -> {
                        EitherOrBoth<String, Integer> lhs = p.via(f::apply).via(g::apply).run();
                        EitherOrBoth<String, Integer> rhs =
                            p.via(x -> f.apply(x).via(g::apply)).run();
                        assertThat(lhs).isEqualTo(rhs);
                      }));
    }
  }
}
