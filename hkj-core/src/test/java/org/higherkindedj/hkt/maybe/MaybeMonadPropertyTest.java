// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

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
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;

/** Property-based Monad-law verification for the Maybe monad, sharing the laws spec with POC 1. */
class MaybeMonadPropertyTest {

  private final MonadError<MaybeKind.Witness, Unit> monad = Instances.monadError(maybe());

  private final BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(MAYBE::narrow);

  @Provide
  Arbitrary<Kind<MaybeKind.Witness, Integer>> maybeKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .map(i -> i == null ? MAYBE.<Integer>widen(Maybe.nothing()) : MAYBE.widen(Maybe.just(i)));
  }

  @Provide
  Arbitrary<Function<Integer, Kind<MaybeKind.Witness, String>>> intToMaybeString() {
    return Arbitraries.of(
        i -> MAYBE.widen(i % 2 == 0 ? Maybe.just("even:" + i) : Maybe.nothing()),
        i -> MAYBE.widen(i > 0 ? Maybe.just("positive:" + i) : Maybe.nothing()),
        i -> MAYBE.widen(Maybe.just("value:" + i)),
        i -> MAYBE.widen(i == 0 ? Maybe.nothing() : Maybe.just(String.valueOf(i))));
  }

  @Provide
  Arbitrary<Function<String, Kind<MaybeKind.Witness, String>>> stringToMaybeString() {
    return Arbitraries.of(
        s -> MAYBE.widen(s.isEmpty() ? Maybe.nothing() : Maybe.just(s.toUpperCase())),
        s -> MAYBE.widen(s.length() > 3 ? Maybe.just("long:" + s) : Maybe.nothing()),
        s -> MAYBE.widen(Maybe.just("transformed:" + s)));
  }

  @Property
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToMaybeString") Function<Integer, Kind<MaybeKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("maybeKinds") Kind<MaybeKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property
  @Label("Monad associativity")
  void associativity(
      @ForAll("maybeKinds") Kind<MaybeKind.Witness, Integer> m,
      @ForAll("intToMaybeString") Function<Integer, Kind<MaybeKind.Witness, String>> f,
      @ForAll("stringToMaybeString") Function<String, Kind<MaybeKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
