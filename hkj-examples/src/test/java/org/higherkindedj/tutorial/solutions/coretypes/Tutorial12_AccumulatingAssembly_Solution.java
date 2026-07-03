// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherOrBothAssert.assertThatEitherOrBoth;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Solution for Tutorial 12: Accumulating Assembly — teaching-solution format. */
@DisplayName("Tutorial 12 Solution: Accumulating Assembly")
public class Tutorial12_AccumulatingAssembly_Solution {

  // ─── Shared domain: a signup form and its leaf validators ──────────────────

  record User(String name, String email, int age) {}

  private static Validated<NonEmptyList<FieldError>, String> parseName(String raw) {
    return raw == null || raw.isBlank()
        ? Validated.invalidNel(FieldError.of("must not be blank"))
        : Validated.validNel(raw.strip());
  }

  private static Validated<NonEmptyList<FieldError>, String> parseEmail(String raw) {
    return raw != null && raw.contains("@")
        ? Validated.validNel(raw)
        : Validated.invalidNel(FieldError.of("not an address"));
  }

  private static Validated<NonEmptyList<FieldError>, Integer> parseAge(String raw) {
    try {
      return Validated.validNel(Integer.parseInt(raw));
    } catch (NumberFormatException _) {
      return Validated.invalidNel(FieldError.of("not a number"));
    }
  }

  private static List<String> errorStrings(Validated<NonEmptyList<FieldError>, ?> result) {
    return result.fold(nel -> nel.map(FieldError::toString).toJavaList(), _ -> List.<String>of());
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the staged builder keeps the whole assembly on the concrete type — no
   * {@code Kind}, no typeclass instance, no {@code Semigroup} argument. Each stage knows the field
   * types accumulated so far, so {@code apply(User::new)} is checked against exactly the right
   * constructor arity, and a misordered field is a compile error.
   *
   * <p>Alternative: {@code Instances.validated(...)} + {@code map3} (Tutorial 03). Equivalent
   * semantics, and still the right spelling inside {@code Kind}-generic code; the builder is the
   * front door for everyday record assembly.
   *
   * <p>Common wrong attempt: reaching for {@code flatMap} chains. They compile, but they
   * short-circuit at the first invalid field, which silently abandons the "report everything at
   * once" behaviour this type exists for.
   */
  @Test
  @DisplayName("Exercise 1: fields()...apply(User::new) assembles three valid fields")
  void exercise1_firstAssembly() {
    Validated<NonEmptyList<FieldError>, String> name = parseName("Ada");
    Validated<NonEmptyList<FieldError>, String> email = parseEmail("ada@example.com");
    Validated<NonEmptyList<FieldError>, Integer> age = parseAge("36");

    Validated<NonEmptyList<FieldError>, User> result =
        Validated.fields()
            .field("name", name)
            .field("email", email)
            .field("age", age)
            .apply(User::new);

    assertThatValidated(result).isValid().hasValue(new User("Ada", "ada@example.com", 36));
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the assembly is identical whether the inputs are valid or not; only the
   * result changes. The declaration-order guarantee means the expected list can be read straight
   * off the {@code field(...)} calls: name first, age second, the healthy email contributing
   * nothing.
   *
   * <p>Alternative: asserting on paths only ({@code FieldError::pathString}) when messages are
   * noisy. The rendered {@code "label: message"} form is the most readable for tutorials and for
   * failure output.
   *
   * <p>Common wrong attempt: expecting the errors in severity or alphabetical order. Accumulation
   * is left-to-right concatenation of {@code NonEmptyList}s; the declaration order <em>is</em> the
   * contract.
   */
  @Test
  @DisplayName("Exercise 2: mixed validity reports every bad field in declaration order")
  void exercise2_allErrorsInOrder() {
    Validated<NonEmptyList<FieldError>, String> name = parseName("   ");
    Validated<NonEmptyList<FieldError>, String> email = parseEmail("ada@example.com");
    Validated<NonEmptyList<FieldError>, Integer> age = parseAge("unknown");

    Validated<NonEmptyList<FieldError>, User> result =
        Validated.fields()
            .field("name", name)
            .field("email", email)
            .field("age", age)
            .apply(User::new);
    List<String> expected = List.of("name: must not be blank", "age: not a number");

    assertThatValidated(result).isInvalid();
    assertThat(errorStrings(result)).isEqualTo(expected);
    assertThat(expected).hasSize(2);
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code accumulate()} keeps the exact assembly shape while staying
   * generic in the error payload; the carrier is fixed to {@code NonEmptyList<X>}, so accumulation
   * is concatenation and needs no configuration.
   *
   * <p>Alternative: mapping domain errors into {@code FieldError} at the boundary and using {@code
   * fields()} — worthwhile as soon as callers need to know <em>where</em> the problem is.
   *
   * <p>Common wrong attempt: {@code fields().field("port", ...)} with a {@code
   * NonEmptyList<String>} value. The labelled flavour is deliberately fixed to {@code FieldError};
   * the compiler steers you to {@code accumulate()} for custom payloads.
   */
  @Test
  @DisplayName("Exercise 3: accumulate() assembles with any error payload")
  void exercise3_genericAccumulate() {
    record Settings(String host, int port) {}

    Validated<NonEmptyList<String>, String> host = Validated.validNel("localhost");
    Validated<NonEmptyList<String>, Integer> port = Validated.invalidNel("port out of range");

    Validated<NonEmptyList<String>, Settings> result =
        Validated.accumulate().and(host).and(port).apply(Settings::new);

    assertThatValidated(result).isInvalid();
    List<String> errors = result.fold(NonEmptyList::toJavaList, _ -> List.<String>of());
    assertThat(errors).containsExactly("port out of range");
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: a sub-assembly's result is an ordinary {@code Validated}, so it feeds
   * {@code field("address", ...)} like any leaf. Because {@code FieldError.at} prepends, the outer
   * label wraps around the inner one and the path reads outside-in: {@code address.zip}.
   *
   * <p>Alternative: flattening the domain model to avoid nesting. Twelve fields per level is the
   * ceiling, and a sub-record per aggregate usually improves the model anyway.
   *
   * <p>Common wrong attempt: labelling inside the sub-assembly with the full path ({@code
   * field("address.zip", ...)}). It works until the sub-assembly is reused under a different
   * parent; let composition build the path instead.
   */
  @Test
  @DisplayName("Exercise 4: nesting prefixes inner paths (address.zip)")
  void exercise4_nestedPaths() {
    record Address(String street, String zip) {}
    record Customer(String name, Address address) {}

    Validated<NonEmptyList<FieldError>, Address> address =
        Validated.fields()
            .field("street", Validated.<FieldError, String>validNel("Main St"))
            .field("zip", Validated.<FieldError, String>invalidNel(FieldError.of("not a postcode")))
            .apply(Address::new);

    Validated<NonEmptyList<FieldError>, Customer> result =
        Validated.fields()
            .field("name", parseName("Ada"))
            .field("address", address)
            .apply(Customer::new);

    assertThatValidated(result).isInvalid();
    assertThat(errorStrings(result)).containsExactly("address.zip: not a postcode");
  }

  // ─── Exercise 5 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the tolerant carrier changes the semantics, not the shape. A {@code
   * Both} contributes its warning and its value, so the assembly still produces a {@code Config};
   * only a {@code Left} withholds the value, and even then every warning is kept.
   *
   * <p>Alternative: {@code EitherOrBoth.fields()} when the warnings should carry paths, exactly
   * like the {@code Validated} flavour.
   *
   * <p>Common wrong attempt: using {@code Validated} with lenient parses. {@code Validated} has no
   * "value with warnings" state, so lenient fallbacks silently disappear; {@code EitherOrBoth}'s
   * {@code Both} is precisely that missing state.
   */
  @Test
  @DisplayName("Exercise 5: EitherOrBoth.accumulate() keeps the value flowing")
  void exercise5_tolerantAssembly() {
    record Config(int port, int timeout) {}

    EitherOrBoth<NonEmptyList<String>, Integer> port =
        EitherOrBoth.both(NonEmptyList.single("port defaulted"), 8080);
    EitherOrBoth<NonEmptyList<String>, Integer> timeout = EitherOrBoth.right(30);

    EitherOrBoth<NonEmptyList<String>, Config> result =
        EitherOrBoth.accumulate().and(port).and(timeout).apply(Config::new);

    assertThatEitherOrBoth(result)
        .isBoth()
        .hasBothSatisfying(
            (warnings, config) -> {
              assertThat(warnings.toJavaList()).containsExactly("port defaulted");
              assertThat(config).isEqualTo(new Config(8080, 30));
            });
  }

  // ─── Diagnostic ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: inside {@code fields()}, {@code field(label, value)} is the default; the
   * location costs one string and pays for itself the first time a user has to find the bad input.
   * The unlabelled {@code and} exists for the rare genuinely-unattributable error.
   *
   * <p>Alternative: {@code and(value)} is legitimate for a value whose errors already carry their
   * paths (a pre-labelled sub-assembly that must not be re-prefixed) — and if labels never matter
   * at all, that is what {@code accumulate()} is for.
   *
   * <p>Common wrong attempt: the buggy version in the exercise text. {@code and(parseEmail(...))}
   * compiles and accumulates, but the label is never attached, so the error renders as a bare
   * {@code "not an address"} with an empty path. The types cannot catch it, so the habit must.
   */
  @Test
  @DisplayName("Diagnostic: and() drops the location; field() keeps it")
  void diagnostic_droppedLabel() {
    record Pair(String name, String email) {}

    Validated<NonEmptyList<FieldError>, Pair> corrected =
        Validated.fields()
            .field("name", parseName("Ada"))
            .field("email", parseEmail("oops"))
            .apply(Pair::new);

    assertThatValidated(corrected).isInvalid();
    assertThat(errorStrings(corrected)).containsExactly("email: not an address");
  }
}
