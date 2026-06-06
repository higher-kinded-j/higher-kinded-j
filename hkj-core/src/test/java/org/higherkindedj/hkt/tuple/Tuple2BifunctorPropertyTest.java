// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.laws.BifunctorLaws;

/**
 * Property-based Bifunctor-law verification for {@link Tuple2Bifunctor}, sharing the {@link
 * BifunctorLaws} spec with {@code Tuple2BifunctorTest}.
 *
 * <p>{@code Tuple2} is an eager product, so the laws are checked structurally over randomly
 * generated {@code (first, second)} pairs via {@link Tuple2LawFixtures#BIFUNCTOR_EQ}. This
 * complements {@code TupleMapIdentityPropertyTest}, which exercises the plain record {@code map}
 * helpers on {@code Tuple6}–{@code Tuple12} rather than the {@code Tuple2} bifunctor instance.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class Tuple2BifunctorPropertyTest {

  private final Tuple2Bifunctor bifunctor = Tuple2Bifunctor.INSTANCE;

  @Provide
  Arbitrary<Kind2<Tuple2Kind2.Witness, String, Integer>> tuple2s() {
    return Combinators.combine(
            Arbitraries.strings().alpha().ofMaxLength(8), Arbitraries.integers().between(-100, 100))
        .as((a, b) -> TUPLE2.widen2(new Tuple2<>(a, b)));
  }

  @Property(tries = 50)
  @Label("Bifunctor identity: bimap(id, id, fab) == fab")
  void identity(@ForAll("tuple2s") Kind2<Tuple2Kind2.Witness, String, Integer> fab) {
    BifunctorLaws.assertIdentity(bifunctor, fab, Tuple2LawFixtures.BIFUNCTOR_EQ);
  }

  @Property(tries = 50)
  @Label("Bifunctor composition: bimap(f2∘f1, g2∘g1) == bimap(f2, g2) ∘ bimap(f1, g1)")
  void composition(@ForAll("tuple2s") Kind2<Tuple2Kind2.Witness, String, Integer> fab) {
    Function<String, Integer> f1 = String::length;
    Function<Integer, String> f2 = i -> "#" + i;
    Function<Integer, String> g1 = n -> "Value:" + n;
    Function<String, String> g2 = s -> s + "!";
    BifunctorLaws.assertComposition(bifunctor, fab, f1, f2, g1, g2, Tuple2LawFixtures.BIFUNCTOR_EQ);
  }

  @Property(tries = 50)
  @Label("Bifunctor first-consistency: first(f, fab) == bimap(f, id, fab)")
  void firstConsistency(@ForAll("tuple2s") Kind2<Tuple2Kind2.Witness, String, Integer> fab) {
    BifunctorLaws.assertFirstConsistency(
        bifunctor, fab, String::length, Tuple2LawFixtures.BIFUNCTOR_EQ);
  }

  @Property(tries = 50)
  @Label("Bifunctor second-consistency: second(g, fab) == bimap(id, g, fab)")
  void secondConsistency(@ForAll("tuple2s") Kind2<Tuple2Kind2.Witness, String, Integer> fab) {
    Function<Integer, String> g = n -> "Value:" + n;
    BifunctorLaws.assertSecondConsistency(bifunctor, fab, g, Tuple2LawFixtures.BIFUNCTOR_EQ);
  }
}
