// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the {@code Context} property test.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Context} generator and the
 * function pools are defined once. Generated contexts are pure {@code succeed} values plus the
 * occasional {@code asks} that reads {@link ContextLawFixtures#STRING_KEY}, so they all resolve
 * under the binding used by {@link ContextLawFixtures#EQ}.
 */
final class ContextArbitraries {

  private ContextArbitraries() {}

  /** {@code succeed(i)} over ints in [-100,100], with ~15% {@code asks(STRING_KEY, length)}. */
  static Arbitrary<Kind<ContextKind.Witness<String>, Integer>> contextKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .map(
            i ->
                i == null
                    ? CONTEXT.asks(ContextLawFixtures.STRING_KEY, String::length)
                    : CONTEXT.succeed(i));
  }

  /** A small pool of total {@code Integer -> Context<String, String>} functions. */
  static Arbitrary<Function<Integer, Kind<ContextKind.Witness<String>, String>>>
      intToContextString() {
    return Arbitraries.of(
        i -> CONTEXT.succeed("v:" + i),
        i -> CONTEXT.succeed(String.valueOf(i * 2)),
        i -> CONTEXT.asks(ContextLawFixtures.STRING_KEY, s -> s + ":" + i));
  }

  /** A small pool of total {@code String -> Context<String, String>} functions. */
  static Arbitrary<Function<String, Kind<ContextKind.Witness<String>, String>>>
      stringToContextString() {
    return Arbitraries.of(
        s -> CONTEXT.succeed(s.toUpperCase()),
        s -> CONTEXT.succeed("len:" + s.length()),
        s -> CONTEXT.succeed("[" + s + "]"));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }
}
