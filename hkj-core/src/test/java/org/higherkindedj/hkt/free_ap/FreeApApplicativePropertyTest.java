// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * Property-based Applicative-law verification for {@code FreeAp} (interpreted over {@code Maybe}),
 * sharing the {@link ApplicativeLaws} spec with {@code FreeApApplicativeTest}.
 *
 * <p>{@code FreeAp} is a <em>free</em> applicative: {@code map}/{@code ap}/{@code map2} build up a
 * {@code Pure}/{@code Lift}/{@code Ap} structure rather than applying their functions, so the laws
 * are checked by interpreting both sides through the identity natural transformation into {@code
 * Maybe} via {@link FreeApLawFixtures#EQ}.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class FreeApApplicativePropertyTest {

  private final Applicative<FreeApKind.Witness<MaybeKind.Witness>> applicative =
      new FreeApApplicative<>();

  @Provide
  Arbitrary<Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer>> freeApKinds() {
    return FreeApArbitraries.freeApKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return FreeApArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return FreeApArbitraries.stringToInt();
  }

  @Property(tries = 50)
  @Label("Applicative identity: ap(of(id), v) == v")
  void identity(@ForAll("freeApKinds") Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> v) {
    ApplicativeLaws.assertIdentity(applicative, v, FreeApLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Applicative homomorphism: ap(of(f), of(x)) == of(f(x))")
  void homomorphism(
      @ForAll @IntRange(min = -50, max = 50) int x,
      @ForAll("intToString") Function<Integer, String> f) {
    ApplicativeLaws.assertHomomorphism(applicative, x, f, FreeApLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Applicative interchange: ap(u, of(y)) == ap(of(f -> f(y)), u)")
  void interchange(
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll @IntRange(min = -50, max = 50) int y) {
    Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<Integer, String>> u = applicative.of(f);
    ApplicativeLaws.assertInterchange(applicative, u, y, FreeApLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Applicative composition: ap(ap(ap(of(compose), u), v), w) == ap(u, ap(v, w))")
  void composition(
      @ForAll("freeApKinds") Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> w,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<String, Integer>> u = applicative.of(g);
    Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<Integer, String>> v = applicative.of(f);
    ApplicativeLaws.assertComposition(applicative, u, v, w, FreeApLawFixtures.EQ);
  }
}
