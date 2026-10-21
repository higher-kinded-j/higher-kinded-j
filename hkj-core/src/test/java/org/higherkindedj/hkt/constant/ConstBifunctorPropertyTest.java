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
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.laws.BifunctorLaws;

/**
 * Property-based Bifunctor-law verification for {@link ConstBifunctor}, sharing the {@link
 * BifunctorLaws} spec with {@code ConstBifunctorTest}.
 *
 * <p>{@code Const} holds only its first value; the second type is a phantom, so {@code second} is
 * never invoked. The laws are checked structurally over randomly generated short strings via {@link
 * ConstLawFixtures#BIFUNCTOR_EQ}. This complements the example-based checks in {@code
 * ConstBifunctorTest}.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class ConstBifunctorPropertyTest {

  private final ConstBifunctor bifunctor = ConstBifunctor.INSTANCE;

  @Provide
  Arbitrary<Kind2<ConstKind2.Witness, String, Integer>> consts() {
    return Arbitraries.strings()
        .alpha()
        .ofMaxLength(8)
        .map(s -> CONST.widen2(new Const<String, Integer>(s)));
  }

  @Property(tries = 50)
  @Label("Bifunctor identity: bimap(id, id, fab) == fab")
  void identity(@ForAll("consts") Kind2<ConstKind2.Witness, String, Integer> fab) {
    BifunctorLaws.assertIdentity(bifunctor, fab, ConstLawFixtures.BIFUNCTOR_EQ);
  }

  @Property(tries = 50)
  @Label("Bifunctor composition: bimap(f2∘f1, g2∘g1) == bimap(f2, g2) ∘ bimap(f1, g1)")
  void composition(@ForAll("consts") Kind2<ConstKind2.Witness, String, Integer> fab) {
    Function<String, Integer> f1 = String::length;
    Function<Integer, String> f2 = i -> "#" + i;
    Function<Integer, String> g1 = n -> "Value:" + n;
    Function<String, String> g2 = s -> s + "!";
    BifunctorLaws.assertComposition(bifunctor, fab, f1, f2, g1, g2, ConstLawFixtures.BIFUNCTOR_EQ);
  }

  @Property(tries = 50)
  @Label("Bifunctor first-consistency: first(f, fab) == bimap(f, id, fab)")
  void firstConsistency(@ForAll("consts") Kind2<ConstKind2.Witness, String, Integer> fab) {
    BifunctorLaws.assertFirstConsistency(
        bifunctor, fab, String::length, ConstLawFixtures.BIFUNCTOR_EQ);
  }

  @Property(tries = 50)
  @Label("Bifunctor second-consistency: second(g, fab) == bimap(id, g, fab)")
  void secondConsistency(@ForAll("consts") Kind2<ConstKind2.Witness, String, Integer> fab) {
    Function<Integer, String> g = n -> "Value:" + n;
    BifunctorLaws.assertSecondConsistency(bifunctor, fab, g, ConstLawFixtures.BIFUNCTOR_EQ);
  }
}
