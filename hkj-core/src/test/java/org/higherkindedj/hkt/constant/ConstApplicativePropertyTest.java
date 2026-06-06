// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.laws.ApplicativeLaws;

/**
 * Property-based Applicative-law verification for {@code Const} over a {@code sum} monoid, sharing
 * the {@link ApplicativeLaws} spec with {@code ConstApplicativeTest}.
 *
 * <p>{@code Const} is a phantom applicative: {@code map}/{@code ap}/{@code map2} never apply their
 * functions and instead combine the held monoidal values, so the laws are checked structurally by
 * comparing accumulated values via {@link ConstLawFixtures#EQ}.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class ConstApplicativePropertyTest {

  private static final Monoid<Integer> SUM_MONOID =
      new Monoid<>() {
        @Override
        public Integer empty() {
          return 0;
        }

        @Override
        public Integer combine(Integer a, Integer b) {
          return a + b;
        }
      };

  private final Applicative<ConstKind.Witness<Integer>> applicative =
      new ConstApplicative<>(SUM_MONOID);

  @Provide
  Arbitrary<Kind<ConstKind.Witness<Integer>, Integer>> constKinds() {
    return ConstArbitraries.constKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return ConstArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Property(tries = 50)
  @Label("Applicative identity: ap(of(id), v) == v")
  void identity(@ForAll("constKinds") Kind<ConstKind.Witness<Integer>, Integer> v) {
    ApplicativeLaws.assertIdentity(applicative, v, ConstLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Applicative homomorphism: ap(of(f), of(x)) == of(f(x))")
  void homomorphism(
      @ForAll @IntRange(min = -50, max = 50) int x,
      @ForAll("intToString") Function<Integer, String> f) {
    ApplicativeLaws.assertHomomorphism(applicative, x, f, ConstLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Applicative interchange: ap(u, of(y)) == ap(of(f -> f(y)), u)")
  void interchange(
      @ForAll @IntRange(min = -100, max = 100) int u,
      @ForAll @IntRange(min = -50, max = 50) int y) {
    // The function is phantom in Const, so u is just an accumulated value carrying a function type.
    Kind<ConstKind.Witness<Integer>, Function<Integer, String>> uKind = CONST.widen(new Const<>(u));
    ApplicativeLaws.assertInterchange(applicative, uKind, y, ConstLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Applicative composition: ap(ap(ap(of(compose), u), v), w) == ap(u, ap(v, w))")
  void composition(
      @ForAll("constKinds") Kind<ConstKind.Witness<Integer>, Integer> w,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    Kind<ConstKind.Witness<Integer>, Function<String, Integer>> u = applicative.of(g);
    Kind<ConstKind.Witness<Integer>, Function<Integer, String>> v = applicative.of(f);
    ApplicativeLaws.assertComposition(applicative, u, v, w, ConstLawFixtures.EQ);
  }
}
