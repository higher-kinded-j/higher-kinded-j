// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code OptionalT} type-class tests (inner monad = {@code Optional}).
 *
 * <p>Referenced from the {@code *LawTests} blocks via a fully-qualified {@code @MethodSource}. The
 * kinds span the three transformer states — {@code Some}, inner {@code None}, and an empty outer
 * {@code Optional} — so each law is verified over all of them.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class OptionalTLawFixtures {

  private OptionalTLawFixtures() {}

  private static final MonadError<OptionalKind.Witness, Unit> OUTER =
      Instances.monadError(optional());

  private static <A> Optional<Optional<A>> unwrapKind(
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> kind) {
    OptionalT<OptionalKind.Witness, A> optionalT = OPTIONAL_T.narrow(kind);
    return OPTIONAL.narrow(optionalT.value());
  }

  /** Shared law equality: run/unwrap to {@code Optional<Optional<·>>} and compare. */
  static final BiPredicate<
          Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>,
          Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>>
      EQ = KindEquivalence.byEqualsAfter(OptionalTLawFixtures::unwrapKind);

  /** {@code Some(0/42/-1)}, inner {@code None}, and an empty outer {@code Optional}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("someT(0)", OPTIONAL_T.widen(OptionalT.some(OUTER, 0))),
        Arguments.of("someT(42)", OPTIONAL_T.widen(OptionalT.some(OUTER, 42))),
        Arguments.of("someT(-1)", OPTIONAL_T.widen(OptionalT.some(OUTER, -1))),
        Arguments.of("noneT", OPTIONAL_T.widen(OptionalT.none(OUTER))),
        Arguments.of(
            "outerEmptyT", OPTIONAL_T.widen(OptionalT.fromKind(OPTIONAL.widen(Optional.empty())))));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }

  /** Scalar law strings {@code {"a", "hello"}}. */
  static Stream<Arguments> strings() {
    return Stream.of(Arguments.of("a"), Arguments.of("hello"));
  }
}
