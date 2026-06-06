// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.higherkindedj.hkt.free.FreeKindHelper.FREE;
import static org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code Free} type-class tests (underlying functor = {@code
 * Identity}).
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.free.FreeLawFixtures#kinds")}, so the fixture stream
 * is defined once rather than copy-pasted into each test. {@code Free} is a free monad: {@code
 * map}/{@code flatMap} only build {@code Pure}/{@code FlatMapped}/{@code Suspend} nodes, which are
 * interpreted later by {@link #EQ}, so it foldMaps both programs through the same {@code Identity}
 * interpreter and compares results — deferred ({@code Suspend}/{@code FlatMapped}) fixtures are
 * therefore fine.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class FreeLawFixtures {

  private FreeLawFixtures() {}

  /** Identity natural transformation: the suspended Identity instruction is passed through. */
  private static final Natural<IdentityKind.Witness, IdentityKind.Witness> IDENTITY_INTERPRETER =
      Natural.identity();

  /** Interprets a Free program into Identity and extracts the result value. */
  private static <A> A runFree(Kind<FreeKind.Witness<IdentityKind.Witness>, A> kind) {
    Free<IdentityKind.Witness, A> free = FREE.narrow(kind);
    Kind<IdentityKind.Witness, A> result =
        free.foldMap(IDENTITY_INTERPRETER, IdentityMonad.INSTANCE);
    return IDENTITY.narrow(result).value();
  }

  /** Interpret both Free programs through the Identity interpreter and compare their results. */
  static final BiPredicate<
          Kind<FreeKind.Witness<IdentityKind.Witness>, ?>,
          Kind<FreeKind.Witness<IdentityKind.Witness>, ?>>
      EQ = (k1, k2) -> Objects.equals(runFree(k1), runFree(k2));

  /** {@code Suspend} wrapping {@code pure(7)} in the Identity functor. */
  private static Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> suspend7() {
    Kind<IdentityKind.Witness, Free<IdentityKind.Witness, Integer>> wrapped =
        IDENTITY.widen(new Identity<>(Free.pure(7)));
    return FREE.widen(Free.suspend(wrapped));
  }

  /** {@code FlatMapped} program {@code pure(5).flatMap(x -> pure(x + 1))}. */
  private static Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> flatMapped5() {
    Function<Integer, Free<IdentityKind.Witness, Integer>> step = x -> Free.pure(x + 1);
    return FREE.widen(Free.<IdentityKind.Witness, Integer>pure(5).flatMap(step));
  }

  /**
   * Representative Free programs: completed {@code pure(0/42/-1)} plus a deferred {@code suspend}
   * and a {@code flatMapped} chain, so the laws exercise every interpreted node shape.
   */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("pure(0)", FREE.widen(Free.<IdentityKind.Witness, Integer>pure(0))),
        Arguments.of("pure(42)", FREE.widen(Free.<IdentityKind.Witness, Integer>pure(42))),
        Arguments.of("pure(-1)", FREE.widen(Free.<IdentityKind.Witness, Integer>pure(-1))),
        Arguments.of("suspend(pure(7))", suspend7()),
        Arguments.of("pure(5).flatMap(+1)", flatMapped5()));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }

  /** Scalar law strings {@code {"", "a", "free"}}. */
  static Stream<Arguments> strings() {
    return Stream.of(Arguments.of(""), Arguments.of("a"), Arguments.of("free"));
  }
}
