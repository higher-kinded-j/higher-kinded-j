// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import static org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper.COYONEDA;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code Coyoneda} Functor tests (underlying functor = {@code Maybe}).
 *
 * <p>Referenced from the {@code *LawTests} block via a fully-qualified {@code @MethodSource} and
 * reused by the property test. Coyoneda fuses maps, so equality lowers both sides through the
 * underlying {@link MaybeFunctor} and compares by {@code Just}/{@code Nothing} + value.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class CoyonedaLawFixtures {

  private CoyonedaLawFixtures() {}

  private static final MaybeFunctor MAYBE_FUNCTOR = MaybeFunctor.INSTANCE;

  /** Lower both Coyoneda kinds through the underlying functor and compare the resulting Maybes. */
  static final BiPredicate<
          Kind<CoyonedaKind.Witness<MaybeKind.Witness>, ?>,
          Kind<CoyonedaKind.Witness<MaybeKind.Witness>, ?>>
      EQ =
          (k1, k2) -> {
            var r1 = MAYBE.narrow(COYONEDA.narrow(k1).lower(MAYBE_FUNCTOR));
            var r2 = MAYBE.narrow(COYONEDA.narrow(k2).lower(MAYBE_FUNCTOR));
            if (r1.isNothing() && r2.isNothing()) {
              return true;
            }
            if (r1.isJust() && r2.isJust()) {
              return Objects.equals(r1.get(), r2.get());
            }
            return false;
          };

  /** {@code lift(just(0/42/-1))} and {@code lift(nothing())}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("just(0)", COYONEDA.lift(MAYBE.just(0))),
        Arguments.of("just(42)", COYONEDA.lift(MAYBE.just(42))),
        Arguments.of("just(-1)", COYONEDA.lift(MAYBE.just(-1))),
        Arguments.of("nothing", COYONEDA.lift(MAYBE.<Integer>nothing())));
  }
}
