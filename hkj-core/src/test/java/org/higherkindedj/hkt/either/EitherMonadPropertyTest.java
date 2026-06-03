// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

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

/** Property-based Monad-law verification for Either, sharing the laws spec with EitherMonadTest. */
// jqwik invokes the @Provide methods reflectively via @ForAll(name); IntelliJ cannot see those
// uses.
@SuppressWarnings("unused")
class EitherMonadPropertyTest {

  private final MonadError<EitherKind.Witness<String>, String> monad =
      Instances.monadError(either());

  private final BiPredicate<
          Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
      eq = KindEquivalence.byEqualsAfter(EITHER::narrow);

  @Provide
  Arbitrary<Kind<EitherKind.Witness<String>, Integer>> eitherKinds() {
    return EitherArbitraries.eitherKinds(100);
  }

  @Provide
  Arbitrary<Function<Integer, Kind<EitherKind.Witness<String>, String>>> intToEitherString() {
    return Arbitraries.of(
        i -> EITHER.widen(i % 2 == 0 ? Either.right("even:" + i) : Either.left("err: odd")),
        i -> EITHER.widen(i > 0 ? Either.right("positive:" + i) : Either.left("err: nonpos")),
        i -> EITHER.widen(Either.right("value:" + i)),
        i -> EITHER.widen(i == 0 ? Either.left("err: zero") : Either.right(String.valueOf(i))));
  }

  @Provide
  Arbitrary<Function<String, Kind<EitherKind.Witness<String>, String>>> stringToEitherString() {
    return Arbitraries.of(
        s -> EITHER.widen(s.isEmpty() ? Either.left("err: empty") : Either.right(s.toUpperCase())),
        s -> EITHER.widen(s.length() > 3 ? Either.right("long:" + s) : Either.left("err: short")),
        s -> EITHER.widen(Either.right("transformed:" + s)));
  }

  @Property
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToEitherString") Function<Integer, Kind<EitherKind.Witness<String>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("eitherKinds") Kind<EitherKind.Witness<String>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property
  @Label("Monad associativity")
  void associativity(
      @ForAll("eitherKinds") Kind<EitherKind.Witness<String>, Integer> m,
      @ForAll("intToEitherString") Function<Integer, Kind<EitherKind.Witness<String>, String>> f,
      @ForAll("stringToEitherString")
          Function<String, Kind<EitherKind.Witness<String>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
