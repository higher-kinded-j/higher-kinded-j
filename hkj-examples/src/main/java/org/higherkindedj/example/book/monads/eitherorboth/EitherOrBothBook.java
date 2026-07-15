// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.monads.eitherorboth;

import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/monads/either_or_both_monad.html">EitherOrBoth</a> page.
 * The page {@code {{#include}}}s the anchored regions, so it cannot drift from the API.
 *
 * <p>One block on that page is not here: it quotes {@code flatMap}'s <em>signature</em> against
 * abstract type variables, which is a shape rather than runnable code. That block keeps a {@code
 * <!-- verify -->} marker instead, so it is still compiled.
 */
public final class EitherOrBothBook {

  // ANCHOR: switch
  static String describe(EitherOrBoth<NonEmptyList<Warning>, Config> r) {
    return switch (r) {
      case EitherOrBoth.Left<NonEmptyList<Warning>, Config>(var w) -> "failed: " + w;
      case EitherOrBoth.Right<NonEmptyList<Warning>, Config>(var cfg) -> "ok: " + cfg;
      case EitherOrBoth.Both<NonEmptyList<Warning>, Config>(var w, var c) ->
          "ok with warnings: " + c;
    };
  }

  // ANCHOR_END: switch

  private EitherOrBothBook() {}

  public static void main(String[] args) {
    RawConfig raw = new RawConfig("8080", "30");

    // ANCHOR: flatmap
    EitherOrBoth<NonEmptyList<Warning>, Config> result =
        parseConfig(raw) // Right(cfg), Both(warnings, cfg), or Left(fatal)
            .flatMap(NonEmptyList.semigroup(), cfg -> validateConfig(cfg)); // warnings accumulate
    // ANCHOR_END: flatmap
    System.out.println(describe(result));

    // ANCHOR: cases
    EitherOrBoth<String, Integer> left = EitherOrBoth.left("fatal");
    EitherOrBoth<String, Integer> right = EitherOrBoth.right(42);
    EitherOrBoth<String, Integer> both = EitherOrBoth.both("deprecated key", 42);
    // ANCHOR_END: cases
    System.out.println(left + " " + right);

    // ANCHOR: fold
    String s =
        both.fold(
            warnings -> "failed: " + warnings,
            value -> "ok: " + value,
            (w, v) -> "ok (" + v + ") with " + w);
    // ANCHOR_END: fold
    System.out.println(s);

    // ANCHOR: accumulate
    EitherOrBoth<NonEmptyList<String>, Config> cfg =
        EitherOrBoth.accumulate()
            .and(parsePortLenient(raw.port())) // Both("port defaulted", 8080)
            .and(parseTimeoutLenient(raw.timeout())) // Right(30)
            .apply(Config::new);
    // Both(NonEmptyList[port defaulted], Config[port=8080, timeout=30])
    // ANCHOR_END: accumulate
    System.out.println(cfg);
  }

  static EitherOrBoth<NonEmptyList<Warning>, Config> parseConfig(RawConfig raw) {
    return EitherOrBoth.both(
        NonEmptyList.single(new Warning("deprecated key")), new Config(8080, 30));
  }

  static EitherOrBoth<NonEmptyList<Warning>, Config> validateConfig(Config cfg) {
    return EitherOrBoth.right(cfg);
  }

  static EitherOrBoth<NonEmptyList<String>, Integer> parsePortLenient(String raw) {
    return EitherOrBoth.both(NonEmptyList.single("port defaulted"), 8080);
  }

  static EitherOrBoth<NonEmptyList<String>, Integer> parseTimeoutLenient(String raw) {
    return EitherOrBoth.right(30);
  }
}

record Warning(String message) {}

record Config(int port, int timeout) {}

record RawConfig(String port, String timeout) {}
