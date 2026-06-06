// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static org.higherkindedj.hkt.eitherf.EitherFKindHelper.EITHERF;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code EitherF} Functor tests (left algebra = {@code Identity}, right
 * algebra = {@code Maybe}).
 *
 * <p>Referenced from the {@code Laws} block via a fully-qualified {@code @MethodSource} and reused
 * by the property test. {@code EitherFFunctor.map} delegates eagerly to the underlying functor on
 * the present side, so equality narrows both sides back to the concrete {@link EitherF} and
 * compares the record values (which structurally compare the wrapped {@code Identity}/{@code Maybe}
 * holders).
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class EitherFLawFixtures {

  private EitherFLawFixtures() {}

  /** Narrow both EitherF kinds and compare by record equality on the concrete value. */
  static final BiPredicate<
          Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, ?>,
          Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, ?>>
      EQ = KindEquivalence.byEqualsAfter(EITHERF::narrow);

  private static Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> left(
      int value) {
    return EITHERF.widen(EitherF.left(IdentityKindHelper.IDENTITY.widen(new Identity<>(value))));
  }

  private static Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> right(
      int value) {
    return EITHERF.widen(EitherF.right(MAYBE.widen(Maybe.just(value))));
  }

  /** {@code Left} and {@code Right} fixtures spanning both effect-algebra sides. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Left(0)", left(0)),
        Arguments.of("Left(42)", left(42)),
        Arguments.of("Right(-1)", right(-1)),
        Arguments.of("Right(42)", right(42)));
  }
}
