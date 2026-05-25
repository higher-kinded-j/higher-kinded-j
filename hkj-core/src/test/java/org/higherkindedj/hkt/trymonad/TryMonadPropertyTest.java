// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

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
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;

/** Property-based Monad-law verification for Try, sharing the laws spec with TryMonadTest. */
class TryMonadPropertyTest {

  private final MonadError<TryKind.Witness, Throwable> monad = Instances.monadError(try_());

  private final BiPredicate<Kind<TryKind.Witness, ?>, Kind<TryKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(TRY::narrow);

  private static final RuntimeException FAIL_A = new IllegalStateException("a");
  private static final RuntimeException FAIL_B = new ArithmeticException("b");

  @Provide
  Arbitrary<Kind<TryKind.Witness, Integer>> tryKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .map(
            i -> {
              if (i == null) return TRY.<Integer>widen(Try.failure(FAIL_A));
              if (i % 5 == 0) return TRY.<Integer>widen(Try.failure(FAIL_B));
              return TRY.widen(Try.success(i));
            });
  }

  @Provide
  Arbitrary<Function<Integer, Kind<TryKind.Witness, String>>> intToTryString() {
    return Arbitraries.of(
        i -> TRY.widen(i % 2 == 0 ? Try.success("even:" + i) : Try.<String>failure(FAIL_A)),
        i -> TRY.widen(i > 0 ? Try.success("positive:" + i) : Try.<String>failure(FAIL_B)),
        i -> TRY.widen(Try.success("value:" + i)));
  }

  @Provide
  Arbitrary<Function<String, Kind<TryKind.Witness, String>>> stringToTryString() {
    return Arbitraries.of(
        s -> TRY.widen(s.isEmpty() ? Try.<String>failure(FAIL_A) : Try.success(s.toUpperCase())),
        s -> TRY.widen(s.length() > 3 ? Try.success("long:" + s) : Try.<String>failure(FAIL_B)),
        s -> TRY.widen(Try.success("transformed:" + s)));
  }

  @Property
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTryString") Function<Integer, Kind<TryKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("tryKinds") Kind<TryKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property
  @Label("Monad associativity")
  void associativity(
      @ForAll("tryKinds") Kind<TryKind.Witness, Integer> m,
      @ForAll("intToTryString") Function<Integer, Kind<TryKind.Witness, String>> f,
      @ForAll("stringToTryString") Function<String, Kind<TryKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
