// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code Context} type-class tests.
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource}, e.g.
 * {@code @MethodSource("org.higherkindedj.hkt.context.ContextLawFixtures#kinds")}, and reused by
 * the property test. Centralising the fixture streams and the equality checker keeps every
 * algebra's law verification driven by the same inputs and removes the copy-pasted {@code eq}
 * BiPredicates that previously lived in each test.
 *
 * <p>{@code Context} is lazy — its result only materialises when {@link Context#run()} is called
 * within a scope — so equality runs both sides under the same {@link #STRING_KEY} binding and
 * compares the produced values.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class ContextLawFixtures {

  private ContextLawFixtures() {}

  /** The scoped value bound while comparing two contexts, so {@code ask}-based fixtures resolve. */
  static final ScopedValue<String> STRING_KEY = ScopedValue.newInstance();

  /** Run both contexts under the same {@link #STRING_KEY} binding and compare their results. */
  static final BiPredicate<
          Kind<ContextKind.Witness<String>, ?>, Kind<ContextKind.Witness<String>, ?>>
      EQ =
          (k1, k2) ->
              ScopedValue.where(STRING_KEY, "test")
                  .call(() -> Objects.equals(CONTEXT.narrow(k1).run(), CONTEXT.narrow(k2).run()));

  /** {@code succeed("hello")} and {@code ask(STRING_KEY)} — both inhabit the "test" binding. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("succeed(\"hello\")", CONTEXT.<String, String>succeed("hello")),
        Arguments.of("ask(STRING_KEY)", CONTEXT.ask(STRING_KEY)));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }
}
