// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect;

import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherOrBothPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/effect/path_either_or_both.html">EitherOrBothPath</a>
 * page. The page {@code {{#include}}}s the anchored regions, so it cannot drift from the API.
 */
public final class EitherOrBothPathBook {

  private EitherOrBothPathBook() {}

  public static void main(String[] args) {
    Config config = new Config(8080, 30);
    RawConfig raw = new RawConfig("8080", "30");

    // ANCHOR: create
    EitherOrBothPath<NonEmptyList<String>, Config> ok = Path.rightNel(config);
    EitherOrBothPath<NonEmptyList<String>, Config> warned = Path.bothNel("deprecated key", config);
    EitherOrBothPath<NonEmptyList<String>, Config> failed = Path.leftNel("config missing");
    // ANCHOR_END: create
    System.out.println(ok.run() + " / " + warned.run() + " / " + failed.run());

    // ANCHOR: custom_semigroup
    EitherOrBothPath<String, Integer> p = Path.both("warn", 42, Semigroups.string("; "));
    // ANCHOR_END: custom_semigroup
    System.out.println(p.run());

    // ANCHOR: via
    EitherOrBothPath<NonEmptyList<String>, Integer> result =
        Path.<String, Integer>bothNel("uses deprecated key", 8)
            .via(value -> value < 10 ? Path.bothNel("value is low", value) : Path.rightNel(value));

    result.run(); // Both([uses deprecated key, value is low], 8)
    result.warnings(); // Just([uses deprecated key, value is low])
    result.getOrElse(0); // 8
    // ANCHOR_END: via
    System.out.println(result.run() + " / " + result.warnings() + " / " + result.getOrElse(0));

    // ANCHOR: zip_accum
    EitherOrBothPath<NonEmptyList<String>, String> name = Path.bothNel("name was trimmed", "Ada");
    EitherOrBothPath<NonEmptyList<String>, Integer> age = Path.bothNel("age defaulted", 30);

    EitherOrBothPath<NonEmptyList<String>, String> reg =
        name.zipWithAccum(age, (n, a) -> n + " (" + a + ")");
    // Both([name was trimmed, age defaulted], "Ada (30)")
    // ANCHOR_END: zip_accum
    System.out.println(reg.run());

    // ANCHOR: accumulate
    EitherOrBoth<NonEmptyList<String>, Config> cfg =
        EitherOrBoth.accumulate()
            .and(parsePortLenient(raw.port()))
            .and(parseTimeoutLenient(raw.timeout()))
            .apply(Config::new);
    // ANCHOR_END: accumulate
    System.out.println(cfg);

    // ANCHOR: recover
    Path.<String, Integer>leftNel("config missing").recover(errors -> 0).run(); // Right(0)
    Path.<String, Integer>bothNel("deprecated", 42)
        .recover(errors -> 0)
        .run(); // Both([deprecated], 42)
    // ANCHOR_END: recover
    System.out.println(
        Path.<String, Integer>leftNel("config missing").recover(errors -> 0).run()
            + " / "
            + Path.<String, Integer>bothNel("deprecated", 42).recover(errors -> 0).run());
  }

  static EitherOrBoth<NonEmptyList<String>, Integer> parsePortLenient(String raw) {
    return EitherOrBoth.both(NonEmptyList.single("port defaulted"), 8080);
  }

  static EitherOrBoth<NonEmptyList<String>, Integer> parseTimeoutLenient(String raw) {
    return EitherOrBoth.right(30);
  }
}

record Config(int port, int timeout) {}

record RawConfig(String port, String timeout) {}
