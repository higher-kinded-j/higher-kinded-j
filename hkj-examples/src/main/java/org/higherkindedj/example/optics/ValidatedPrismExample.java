// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.Optional;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * Demonstrates {@code ValidatedPrism}: the smart-constructor optic for parse-don't-validate
 * boundaries — a {@code Prism} whose match accumulates located reasons instead of answering yes/no.
 *
 * <p>Key concepts demonstrated:
 *
 * <ul>
 *   <li>{@code parse} is fallible and reports <em>why</em>, as located {@code FieldError}s; {@code
 *       build} is total — a valid domain value always renders
 *   <li>Nested composition ({@code andThen}) short-circuits; sibling fields accumulate through the
 *       assembly builders, fed by {@code vp::parse}
 *   <li>{@code fromPrism(prism, reason)} gives a plain prism the reason its empty match cannot
 *       express; {@code toPrism()} forgets reasons on demand
 *   <li>{@code parsePath} lands the boundary directly on the {@code ValidationPath} railway
 * </ul>
 */
public final class ValidatedPrismExample {

  public record EmailAddress(String local, String domain) {
    public String render() {
      return local + "@" + domain;
    }
  }

  public record Contact(String name, EmailAddress email) {}

  /** The wire boundary: {@code String <-> EmailAddress}, parse with reasons, total render. */
  public static final ValidatedPrism<String, EmailAddress> EMAIL =
      ValidatedPrism.of(ValidatedPrismExample::parseEmail, EmailAddress::render);

  private static Validated<NonEmptyList<FieldError>, EmailAddress> parseEmail(String raw) {
    int at = raw.indexOf('@');
    if (at <= 0 || at != raw.lastIndexOf('@') || at == raw.length() - 1) {
      return Validated.invalidNel(FieldError.of("not an email address: " + raw));
    }
    return Validated.validNel(new EmailAddress(raw.substring(0, at), raw.substring(at + 1)));
  }

  public static void main(String[] args) {
    demonstrateParseAndBuild();
    demonstrateSiblingAccumulation();
    demonstrateLiftingAndForgetting();
    demonstrateShortCircuitNesting();
  }

  /** The two directions: fallible located parse, total build, and the railway bridge. */
  private static void demonstrateParseAndBuild() {
    System.out.println("=== Parse and Build Example ===");
    System.out.println("Parsed:   " + EMAIL.parse("ada@corp.example"));
    System.out.println("Rejected: " + EMAIL.parse("not-an-email"));
    System.out.println("Rendered: " + EMAIL.build(new EmailAddress("ada", "corp.example")));
    System.out.println("Railway:  " + EMAIL.parsePath("ada@corp.example").run());
    System.out.println("Expected: Valid, Invalid with a reason, total render, Valid on the path\n");
  }

  /** Siblings accumulate through the assembly builder; the prism supplies each located leaf. */
  private static void demonstrateSiblingAccumulation() {
    System.out.println("=== Sibling Accumulation Example ===");

    Validated<NonEmptyList<FieldError>, Contact> good =
        Validated.fields()
            .field("name", Validated.validNel("Ada"))
            .field("email", EMAIL.parse("ada@corp.example"))
            .apply(Contact::new);
    Validated<NonEmptyList<FieldError>, Contact> bad =
        Validated.fields()
            .field("name", Validated.validNel("Bob"))
            .field("email", EMAIL.parse("nope"))
            .apply(Contact::new);

    System.out.println("Good: " + good);
    System.out.println("Bad:  " + bad);
    System.out.println("Expected: Valid(Contact...), Invalid located at \"email: ...\"\n");
  }

  /** Lift a plain prism by supplying the missing reason; forget reasons when composing onward. */
  private static void demonstrateLiftingAndForgetting() {
    System.out.println("=== Lifting and Forgetting Example ===");

    Prism<String, Integer> parseInt =
        Prism.of(
            s -> {
              try {
                return Optional.of(Integer.parseInt(s));
              } catch (NumberFormatException e) {
                return Optional.empty();
              }
            },
            String::valueOf);

    ValidatedPrism<String, Integer> withReason =
        ValidatedPrism.fromPrism(parseInt, FieldError.of("must be a whole number"));

    System.out.println("Lifted, good:  " + withReason.parse("42"));
    System.out.println("Lifted, bad:   " + withReason.parse("forty-two"));
    System.out.println("Forgotten:     " + EMAIL.toPrism().getOptional("nope"));
    System.out.println("Expected: Valid(42), the supplied reason, then a reason-less empty\n");
  }

  /** Nesting short-circuits: you cannot parse the inner value if the outer parse failed. */
  private static void demonstrateShortCircuitNesting() {
    System.out.println("=== Short-Circuit Nesting Example ===");

    ValidatedPrism<EmailAddress, String> corpOnly =
        ValidatedPrism.of(
            email ->
                "corp.example".equals(email.domain())
                    ? Validated.validNel(email.local())
                    : Validated.invalidNel(FieldError.of("not a corp address")),
            local -> new EmailAddress(local, "corp.example"));

    ValidatedPrism<String, String> corpUser = EMAIL.andThen(corpOnly);

    System.out.println("Nested, good:        " + corpUser.parse("ada@corp.example"));
    System.out.println("Inner refuses:       " + corpUser.parse("ada@other.example"));
    System.out.println("Outer short-circuits: " + corpUser.parse("nope"));
    System.out.println("Built back through both: " + corpUser.build("ada"));
    System.out.println(
        "Expected: Valid(ada); \"not a corp address\"; only the OUTER reason; ada@corp.example");
  }

  private ValidatedPrismExample() {}
}
