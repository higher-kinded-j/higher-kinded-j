// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/** Property-based Functor- and Monad-law verification for Id. */
class IdMonadPropertyTest {

  private final Monad<IdKind.Witness> monad = IdMonad.instance();

  private final BiPredicate<Kind<IdKind.Witness, ?>, Kind<IdKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(ID::narrow);

  @Provide
  Arbitrary<Kind<IdKind.Witness, Integer>> idKinds() {
    return Arbitraries.integers().between(-100, 100).map(i -> ID.widen(Id.of(i)));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Provide
  Arbitrary<Function<Integer, Kind<IdKind.Witness, String>>> intToIdString() {
    return Arbitraries.of(
        i -> ID.widen(Id.of("v:" + i)),
        i -> ID.widen(Id.of(String.valueOf(i * 2))),
        i -> ID.widen(Id.of(Integer.toBinaryString(i))));
  }

  @Provide
  Arbitrary<Function<String, Kind<IdKind.Witness, String>>> stringToIdString() {
    return Arbitraries.of(
        s -> ID.widen(Id.of(s + "!")),
        s -> ID.widen(Id.of(s.toUpperCase())),
        s -> ID.widen(Id.of("x:" + s)));
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("idKinds") Kind<IdKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("idKinds") Kind<IdKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToIdString") Function<Integer, Kind<IdKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("idKinds") Kind<IdKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("idKinds") Kind<IdKind.Witness, Integer> m,
      @ForAll("intToIdString") Function<Integer, Kind<IdKind.Witness, String>> f,
      @ForAll("stringToIdString") Function<String, Kind<IdKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
