// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.higherkindedj.hkt.free.FreeKindHelper.FREE;
import static org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;

/**
 * Shared jqwik arbitraries for the Free property test (underlying functor = {@code Identity}).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Free<Integer>} generator and
 * the function pools are defined once rather than copy-pasted into {@code FreeMonadPropertyTest}.
 */
final class FreeArbitraries {

  private FreeArbitraries() {}

  /** Wraps a pure value in a {@code Suspend} over the Identity functor. */
  private static Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> suspend(int value) {
    Kind<IdentityKind.Witness, Free<IdentityKind.Witness, Integer>> wrapped =
        IDENTITY.widen(new Identity<>(Free.pure(value)));
    return FREE.widen(Free.suspend(wrapped));
  }

  /**
   * {@code Free<Integer>} programs over ints in {@code [-100, 100]}, cycling completed {@code
   * pure}, deferred {@code suspend} and {@code flatMapped} forms so the property test exercises
   * every interpreted node shape.
   */
  static Arbitrary<Kind<FreeKind.Witness<IdentityKind.Witness>, Integer>> freeKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .map(
            i ->
                switch (Math.floorMod(i, 3)) {
                  case 0 -> FREE.widen(Free.pure(i));
                  case 1 -> suspend(i);
                  default ->
                      FREE.widen(Free.<IdentityKind.Witness, Integer>pure(i).flatMap(Free::pure));
                });
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> Kind<Free, String>} functions, mixing pure/suspend. */
  static Arbitrary<Function<Integer, Kind<FreeKind.Witness<IdentityKind.Witness>, String>>>
      intToFreeString() {
    return Arbitraries.of(
        i -> FREE.widen(Free.pure("v:" + i)),
        i ->
            FREE.widen(
                Free.suspend(IDENTITY.widen(new Identity<>(Free.pure(String.valueOf(i * 2)))))),
        i -> FREE.widen(Free.pure(Integer.toBinaryString(i))));
  }

  /** A small pool of {@code String -> Kind<Free, String>} functions, mixing pure/suspend. */
  static Arbitrary<Function<String, Kind<FreeKind.Witness<IdentityKind.Witness>, String>>>
      stringToFreeString() {
    return Arbitraries.of(
        s -> FREE.widen(Free.pure(s + "!")),
        s -> FREE.widen(Free.suspend(IDENTITY.widen(new Identity<>(Free.pure(s.toUpperCase()))))),
        s -> FREE.widen(Free.pure("x:" + s)));
  }
}
