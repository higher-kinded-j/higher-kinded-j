// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.eitherorboth;

import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * Demonstrates {@link EitherOrBoth}: an inclusive-or ({@code Ior} / {@code These}) that is a {@code
 * Left}, a {@code Right}, or {@code Both} at once.
 *
 * <p>{@code EitherOrBoth} is the type for "success that also carries non-fatal warnings": config
 * that parses but reports deprecations, an import that yields records and a skipped-rows list. It
 * is right-biased, with total accessors that never throw and an accumulating {@code flatMap}.
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.basic.eitherorboth.EitherOrBothExample}
 */
public class EitherOrBothExample {

  /** The warning channel: a NonEmptyList is non-empty by construction, like a {@code Both}. */
  private static final Semigroup<NonEmptyList<String>> WARNINGS = NonEmptyList.semigroup();

  public static void main(String[] args) {
    System.out.println("=== EitherOrBoth ===\n");

    construction();
    totalAccessors();
    transforms();
    accumulatingFlatMap();
    conversions();
  }

  private static void construction() {
    System.out.println("--- Construction ---");
    EitherOrBoth<String, Integer> left = EitherOrBoth.left("fatal");
    EitherOrBoth<String, Integer> right = EitherOrBoth.right(42);
    EitherOrBoth<String, Integer> both = EitherOrBoth.both("deprecated key", 42);

    System.out.println("left(\"fatal\")           = " + left);
    System.out.println("right(42)               = " + right);
    System.out.println("both(\"deprecated\", 42)  = " + both);
    System.out.println();
  }

  private static void totalAccessors() {
    System.out.println("--- Total accessors (never throw) ---");
    EitherOrBoth<String, Integer> both = EitherOrBoth.both("warn", 42);

    System.out.println("getLeft  = " + both.getLeft()); // Just(warn)
    System.out.println("getRight = " + both.getRight()); // Just(42)
    System.out.println("isBoth   = " + both.isBoth()); // true

    // Pattern matching for the imperative shell.
    String described =
        switch (both) {
          case EitherOrBoth.Left<String, Integer>(var w) -> "failed: " + w;
          case EitherOrBoth.Right<String, Integer>(var v) -> "ok: " + v;
          case EitherOrBoth.Both<String, Integer>(var w, var v) -> "ok (" + v + ") with: " + w;
        };
    System.out.println("switch   = " + described);
    System.out.println();
  }

  private static void transforms() {
    System.out.println("--- Transforms (right-biased) ---");
    EitherOrBoth<String, Integer> both = EitherOrBoth.both("warn", 21);

    System.out.println("map(*2)        = " + both.map(n -> n * 2)); // Both(warn, 42)
    System.out.println("mapLeft(upper) = " + both.mapLeft(String::toUpperCase)); // Both(WARN, 21)
    System.out.println("bimap          = " + both.bimap(String::length, n -> n + 1)); // Both(4, 22)
    System.out.println();
  }

  private static void accumulatingFlatMap() {
    System.out.println("--- Accumulating flatMap (Both carries warnings forward) ---");

    // parseConfig succeeds but flags a deprecation; validateConfig adds another warning.
    EitherOrBoth<NonEmptyList<String>, Integer> result =
        parseConfig().flatMap(WARNINGS, EitherOrBothExample::validateConfig);

    System.out.println("result = " + result);
    // Both([uses deprecated key, value is low], 8): warnings from both stages accumulate.
    var warnings = result.getLeft();
    if (warnings.isJust()) {
      System.out.println("warnings = " + warnings.get().toJavaList());
    }
    System.out.println();
  }

  private static EitherOrBoth<NonEmptyList<String>, Integer> parseConfig() {
    return EitherOrBoth.both(NonEmptyList.single("uses deprecated key"), 8);
  }

  private static EitherOrBoth<NonEmptyList<String>, Integer> validateConfig(int value) {
    return value < 10
        ? EitherOrBoth.both(NonEmptyList.single("value is low"), value)
        : EitherOrBoth.right(value);
  }

  private static void conversions() {
    System.out.println("--- Conversions ---");
    EitherOrBoth<String, Integer> both = EitherOrBoth.both("warn", 42);

    System.out.println(
        "toEitherDroppingWarnings = " + both.toEitherDroppingWarnings()); // Right(42)
    System.out.println(
        "toEitherFailingOnWarnings = " + both.toEitherFailingOnWarnings()); // Left(warn)
    System.out.println("toValidated              = " + both.toValidated()); // Valid(42)
    System.out.println("toMaybe                  = " + both.toMaybe()); // Just(42)
    System.out.println();
  }
}
