// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import org.higherkindedj.hkt.effect.EitherOrBothPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * Demonstrates {@link EitherOrBothPath}: the railway wrapper for {@link
 * org.higherkindedj.hkt.eitherorboth.EitherOrBoth} (success that may carry non-fatal warnings).
 *
 * <p>Shows the two modes of composition: short-circuit sequencing with {@code via} (a {@code Left}
 * stops the chain; a {@code Both} carries its warnings forward and accumulates them) and parallel
 * accumulation with {@code zipWithAccum} (collects every warning, even across a fatal {@code
 * Left}). The {@code rightNel} / {@code leftNel} / {@code bothNel} factories bake in {@code
 * NonEmptyList.semigroup()}.
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.EitherOrBothPathExample}
 */
public class EitherOrBothPathExample {

  public static void main(String[] args) {
    System.out.println("=== EitherOrBothPath ===\n");

    sequentialWithWarnings();
    parallelAccumulation();
    tolerantAssembly();
    recovery();
  }

  /** A pipeline where each stage may add a warning; {@code via} threads them forward. */
  private static void sequentialWithWarnings() {
    System.out.println("--- Sequential (via): warnings accumulate, Left short-circuits ---");

    EitherOrBothPath<NonEmptyList<String>, Integer> result =
        Path.<String, Integer>bothNel("uses deprecated key", 8)
            .via(EitherOrBothPathExample::validate);

    System.out.println("result   = " + result.run());
    System.out.println(
        "warnings = " + result.warnings()); // Just([uses deprecated key, value is low])
    System.out.println("value    = " + result.getOrElse(-1)); // 8
    System.out.println();
  }

  private static EitherOrBothPath<NonEmptyList<String>, Integer> validate(int value) {
    return value < 10 ? Path.bothNel("value is low", value) : Path.rightNel(value);
  }

  /** Two independent validations combined; every warning is collected. */
  private static void parallelAccumulation() {
    System.out.println("--- Parallel (zipWithAccum): collect every warning ---");

    EitherOrBothPath<NonEmptyList<String>, String> name = Path.bothNel("name was trimmed", "Ada");
    EitherOrBothPath<NonEmptyList<String>, Integer> age = Path.bothNel("age defaulted", 30);

    EitherOrBothPath<NonEmptyList<String>, String> registration =
        name.zipWithAccum(age, (n, a) -> n + " (" + a + ")");

    System.out.println("result   = " + registration.run());
    // Both([name was trimmed, age defaulted], Ada (30))
    System.out.println();
  }

  /**
   * The staged assembly over the core type: lenient parses produce warnings, the config still
   * assembles ({@code Both}); a fatal {@code Left} would dominate while keeping every warning.
   */
  private static void tolerantAssembly() {
    System.out.println("--- Tolerant assembly: EitherOrBoth.accumulate() ---");

    record Config(int port, int timeout) {}

    EitherOrBoth<NonEmptyList<String>, Config> cfg =
        EitherOrBoth.accumulate()
            .and(parseLenient("port", "8O80", 8080)) // typo: warns, falls back
            .and(parseLenient("timeout", "30", 60)) // parses cleanly
            .apply(Config::new);

    System.out.println("config = " + cfg);
    // Both(NonEmptyList[port: could not parse '8O80', using 8080], Config[port=8080, timeout=30])
    System.out.println();
  }

  private static EitherOrBoth<NonEmptyList<String>, Integer> parseLenient(
      String key, String raw, int fallback) {
    try {
      return EitherOrBoth.right(Integer.parseInt(raw));
    } catch (NumberFormatException _) {
      return EitherOrBoth.both(
          NonEmptyList.single(key + ": could not parse '" + raw + "', using " + fallback),
          fallback);
    }
  }

  /** A fatal Left can be recovered at the boundary; a Both is already a success. */
  private static void recovery() {
    System.out.println("--- Recovery (recover acts on Left only) ---");

    EitherOrBothPath<NonEmptyList<String>, Integer> fatal = Path.leftNel("config missing");
    System.out.println("recovered = " + fatal.recover(errors -> 0).run()); // Right(0)

    EitherOrBothPath<NonEmptyList<String>, Integer> warned = Path.bothNel("deprecated", 42);
    System.out.println(
        "both kept = " + warned.recover(errors -> 0).run()); // Both([deprecated], 42)
    System.out.println();
  }
}
