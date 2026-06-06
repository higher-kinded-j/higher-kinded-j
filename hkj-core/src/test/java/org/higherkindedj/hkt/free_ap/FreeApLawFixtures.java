// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code FreeAp} Applicative tests (interpreted over {@code Maybe}).
 *
 * <p>Referenced from the {@code Laws} block via a fully-qualified {@code @MethodSource} and reused
 * by the property test. {@code FreeAp} is a <em>free</em> applicative: {@code map}/{@code
 * ap}/{@code map2} build up a {@code Pure}/{@code Lift}/{@code Ap} structure and never apply their
 * functions — the supplied functions are only run later by a {@code foldMap}/{@code analyse} pass.
 * Equality therefore <em>interprets</em> both sides by folding through the identity natural
 * transformation into the {@link MaybeKind} applicative and compares the resulting {@code
 * Just}/{@code Nothing} + value.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class FreeApLawFixtures {

  private FreeApLawFixtures() {}

  static final Applicative<MaybeKind.Witness> MAYBE_APPLICATIVE = Instances.monadError(maybe());

  static final Natural<MaybeKind.Witness, MaybeKind.Witness> IDENTITY_NAT = Natural.identity();

  /**
   * Interpret both FreeAp kinds by folding them through the identity natural transformation into
   * the {@code Maybe} applicative, then compare the resulting {@code Just}/{@code Nothing} + value.
   */
  static final BiPredicate<
          Kind<FreeApKind.Witness<MaybeKind.Witness>, ?>,
          Kind<FreeApKind.Witness<MaybeKind.Witness>, ?>>
      EQ =
          (k1, k2) -> {
            var r1 = MAYBE.narrow(FREE_AP.narrow(k1).foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE));
            var r2 = MAYBE.narrow(FREE_AP.narrow(k2).foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE));
            if (r1.isNothing() && r2.isNothing()) {
              return true;
            }
            if (r1.isJust() && r2.isJust()) {
              return Objects.equals(r1.get(), r2.get());
            }
            return false;
          };

  /**
   * Representative FreeAp values: {@code pure(0/42/-1)} plus an {@code Ap}-node built over a lifted
   * {@code Maybe} instruction ({@code lift(just(7)).ap(pure(id))}).
   */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("pure(0)", FREE_AP.widen(FreeAp.<MaybeKind.Witness, Integer>pure(0))),
        Arguments.of("pure(42)", FREE_AP.widen(FreeAp.<MaybeKind.Witness, Integer>pure(42))),
        Arguments.of("pure(-1)", FREE_AP.widen(FreeAp.<MaybeKind.Witness, Integer>pure(-1))),
        Arguments.of(
            "ap(lift(just(7)), pure(id))",
            FREE_AP.widen(
                FreeAp.lift(MAYBE.just(7))
                    .ap(FreeAp.<MaybeKind.Witness, Function<Integer, Integer>>pure(x -> x)))));
  }

  /** Scalar law values for the applicative homomorphism/interchange laws. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
