// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
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
import org.higherkindedj.hkt.laws.MonadLaws;

/**
 * Property-based Monad-law verification for Optional, sharing the laws spec with OptionalMonadTest.
 */
class OptionalMonadPropertyTest {

  private final MonadError<OptionalKind.Witness, Unit> monad = Instances.monadError(optional());

  private final BiPredicate<Kind<OptionalKind.Witness, ?>, Kind<OptionalKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(OPTIONAL::narrow);

  @Provide
  Arbitrary<Kind<OptionalKind.Witness, Integer>> optionalKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .map(
            i ->
                i == null
                    ? OPTIONAL.<Integer>widen(Optional.empty())
                    : OPTIONAL.widen(Optional.of(i)));
  }

  @Provide
  Arbitrary<Function<Integer, Kind<OptionalKind.Witness, String>>> intToOptionalString() {
    return Arbitraries.of(
        i -> OPTIONAL.widen(i % 2 == 0 ? Optional.of("even:" + i) : Optional.empty()),
        i -> OPTIONAL.widen(i > 0 ? Optional.of("positive:" + i) : Optional.empty()),
        i -> OPTIONAL.widen(Optional.of("value:" + i)));
  }

  @Provide
  Arbitrary<Function<String, Kind<OptionalKind.Witness, String>>> stringToOptionalString() {
    return Arbitraries.of(
        s -> OPTIONAL.widen(s.isEmpty() ? Optional.empty() : Optional.of(s.toUpperCase())),
        s -> OPTIONAL.widen(s.length() > 3 ? Optional.of("long:" + s) : Optional.empty()),
        s -> OPTIONAL.widen(Optional.of("transformed:" + s)));
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToOptionalString") Function<Integer, Kind<OptionalKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("optionalKinds") Kind<OptionalKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("optionalKinds") Kind<OptionalKind.Witness, Integer> m,
      @ForAll("intToOptionalString") Function<Integer, Kind<OptionalKind.Witness, String>> f,
      @ForAll("stringToOptionalString") Function<String, Kind<OptionalKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
