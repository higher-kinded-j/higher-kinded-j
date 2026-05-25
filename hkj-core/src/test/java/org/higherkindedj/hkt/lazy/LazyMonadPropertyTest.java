// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.instances.Witnesses.lazy;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;

import java.util.Objects;
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
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/** Property-based Functor- and Monad-law verification for Lazy. */
class LazyMonadPropertyTest {

  private final Monad<LazyKind.Witness> monad = Instances.monad(lazy());

  /** Forces both sides and compares — Lazy equality is value-based after evaluation. */
  private final BiPredicate<Kind<LazyKind.Witness, ?>, Kind<LazyKind.Witness, ?>> eq =
      (k1, k2) -> {
        try {
          return Objects.equals(LAZY.force(k1), LAZY.force(k2));
        } catch (Throwable e) {
          return false;
        }
      };

  @Provide
  Arbitrary<Kind<LazyKind.Witness, Integer>> lazyKinds() {
    return Arbitraries.integers().between(-100, 100).map(i -> LAZY.widen(Lazy.now(i)));
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
  Arbitrary<Function<Integer, Kind<LazyKind.Witness, String>>> intToLazyString() {
    return Arbitraries.of(
        i -> LAZY.widen(Lazy.now("v:" + i)),
        i -> LAZY.widen(Lazy.defer(() -> String.valueOf(i * 2))),
        i -> LAZY.widen(Lazy.now(Integer.toBinaryString(i))));
  }

  @Provide
  Arbitrary<Function<String, Kind<LazyKind.Witness, String>>> stringToLazyString() {
    return Arbitraries.of(
        s -> LAZY.widen(Lazy.now(s + "!")),
        s -> LAZY.widen(Lazy.defer(s::toUpperCase)),
        s -> LAZY.widen(Lazy.now("x:" + s)));
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("lazyKinds") Kind<LazyKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("lazyKinds") Kind<LazyKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToLazyString") Function<Integer, Kind<LazyKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("lazyKinds") Kind<LazyKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("lazyKinds") Kind<LazyKind.Witness, Integer> m,
      @ForAll("intToLazyString") Function<Integer, Kind<LazyKind.Witness, String>> f,
      @ForAll("stringToLazyString") Function<String, Kind<LazyKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
