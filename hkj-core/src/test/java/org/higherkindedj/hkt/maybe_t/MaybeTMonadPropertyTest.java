// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
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
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Property-based Functor- and Monad-law verification for MaybeT over Optional. Fixtures span the
 * three transformer states: Just, Nothing, and empty-outer.
 */
class MaybeTMonadPropertyTest {

  private final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());
  private final MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Unit> maybeTMonad =
      Instances.maybeT(outerMonad);

  private <A> Optional<Maybe<A>> unwrapKind(
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, A> kind) {
    if (kind == null) return null;
    var maybeT = MAYBE_T.narrow(kind);
    Kind<OptionalKind.Witness, Maybe<A>> outerKind = maybeT.value();
    return OPTIONAL.narrow(outerKind);
  }

  private final BiPredicate<
          Kind<MaybeTKind.Witness<OptionalKind.Witness>, ?>,
          Kind<MaybeTKind.Witness<OptionalKind.Witness>, ?>>
      eq = KindEquivalence.byEqualsAfter(this::unwrapKind);

  private <R> Kind<MaybeTKind.Witness<OptionalKind.Witness>, R> justT(R value) {
    return MAYBE_T.widen(MaybeT.just(outerMonad, value));
  }

  private <R> Kind<MaybeTKind.Witness<OptionalKind.Witness>, R> nothingT() {
    return MAYBE_T.widen(MaybeT.nothing(outerMonad));
  }

  private <R> Kind<MaybeTKind.Witness<OptionalKind.Witness>, R> emptyT() {
    Kind<OptionalKind.Witness, Maybe<R>> emptyOuter = OPTIONAL.widen(Optional.empty());
    return MAYBE_T.widen(MaybeT.fromKind(emptyOuter));
  }

  @Provide
  Arbitrary<Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> maybeTKinds() {
    // Mix of Just (success), inner Nothing, and empty-outer Optional states.
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(this.<Integer>emptyT());
              }
              if (i % 5 == 0) {
                return Arbitraries.just(this.<Integer>nothingT());
              }
              return Arbitraries.just(justT(i));
            });
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
  Arbitrary<Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>>>
      intToTKindString() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? justT("even:" + i) : nothingT(),
        i -> i > 0 ? justT("positive:" + i) : emptyT(),
        i -> justT("value:" + i),
        i -> i == 0 ? nothingT() : justT(String.valueOf(i)));
  }

  @Provide
  Arbitrary<Function<String, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>>>
      stringToTKindString() {
    return Arbitraries.of(
        s -> s.isEmpty() ? nothingT() : justT(s.toUpperCase()),
        s -> s.length() > 3 ? justT("long:" + s) : emptyT(),
        s -> justT("transformed:" + s));
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("maybeTKinds") Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa) {
    FunctorLaws.assertIdentity(maybeTMonad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("maybeTKinds") Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(maybeTMonad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTKindString")
          Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> f) {
    MonadLaws.assertLeftIdentity(maybeTMonad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(
      @ForAll("maybeTKinds") Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> m) {
    MonadLaws.assertRightIdentity(maybeTMonad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("maybeTKinds") Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> m,
      @ForAll("intToTKindString")
          Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> f,
      @ForAll("stringToTKindString")
          Function<String, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> g) {
    MonadLaws.assertAssociativity(maybeTMonad, m, f, g, eq);
  }
}
