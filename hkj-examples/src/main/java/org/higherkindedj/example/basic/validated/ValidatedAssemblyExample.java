// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.validated;

import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Demonstrates the open-arity accumulating assembly (issue #581): building a record from N
 * independently validated fields, collecting <b>all</b> errors in field-declaration order, with no
 * {@code Semigroup} argument, no arity wall, and no {@code Kind} ceremony.
 *
 * <p>Two flavours:
 *
 * <ul>
 *   <li>{@link Validated#fields()} — the error channel is {@code NonEmptyList<FieldError>}; {@code
 *       field(label, value)} tags each slot so every error carries its path, and nesting a
 *       sub-assembly prepends the outer segment ({@code "address.zip"}).
 *   <li>{@link Validated#accumulate()} — generic in the error payload; fields join with {@code
 *       and(value)}.
 * </ul>
 *
 * <p>The chain is staged, so {@code apply(...)} always matches the accumulated arity (up to 16,
 * matching the shipped {@code FunctionN}); records with more fields nest a sub-record per slot.
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.basic.validated.ValidatedAssemblyExample}
 */
public class ValidatedAssemblyExample {

  // The DTO -> domain scenario: raw request input parsed into validated domain values.
  record SignupRequest(String name, String email, String age) {}

  record User(String name, String email, int age) {}

  record Address(String street, String zip) {}

  record Customer(String name, Address address) {}

  public static void main(String[] args) {
    System.out.println("=== Accumulating Assembly: Validated.fields() / accumulate() ===\n");

    labelledAssembly();
    genericAssembly();
    nestedAssembly();
  }

  // ===== fields(): every bad field reported at once, each with its path =====

  private static void labelledAssembly() {
    System.out.println("--- fields(): located errors, all at once ---");

    SignupRequest good = new SignupRequest("Ada", "ada@example.com", "36");
    SignupRequest bad = new SignupRequest("Ada", "not-an-email", "unknown");

    System.out.println("good request = " + signUp(good));
    // Valid(User[name=Ada, email=ada@example.com, age=36])

    Validated<NonEmptyList<FieldError>, User> failed = signUp(bad);
    System.out.println("bad request  = " + failed);
    // Invalid(NonEmptyList[email: not an email address, age: not a number]) - name was fine
    System.out.println();
  }

  private static Validated<NonEmptyList<FieldError>, User> signUp(SignupRequest dto) {
    return Validated.fields()
        .field("name", parseName(dto.name()))
        .field("email", parseEmail(dto.email()))
        .field("age", parseAge(dto.age()))
        .apply(User::new);
  }

  // ===== accumulate(): any error payload, no labels =====

  private static void genericAssembly() {
    System.out.println("--- accumulate(): generic error payload ---");

    record Settings(String host, int port) {}

    Validated<NonEmptyList<String>, Settings> settings =
        Validated.accumulate()
            .and(Validated.<String, String>validNel("localhost"))
            .and(Validated.<String, Integer>invalidNel("port out of range"))
            .apply(Settings::new);

    System.out.println("settings = " + settings);
    // Invalid(NonEmptyList[port out of range])
    System.out.println();
  }

  // ===== Nesting: the outer label prefixes the inner paths =====

  private static void nestedAssembly() {
    System.out.println("--- Nesting: address.zip ---");

    Validated<NonEmptyList<FieldError>, Address> address =
        Validated.fields()
            .field("street", Validated.<FieldError, String>validNel("Main St"))
            .field("zip", Validated.<FieldError, String>invalidNel(FieldError.of("not a postcode")))
            .apply(Address::new);

    Validated<NonEmptyList<FieldError>, Customer> customer =
        Validated.fields()
            .field("name", parseName("Ada"))
            .field("address", address)
            .apply(Customer::new);

    System.out.println("customer = " + customer);
    // Invalid(NonEmptyList[address.zip: not a postcode])
    System.out.println();
  }

  // ===== Leaf validators: a leaf error is unlabelled; field() attaches the location =====

  private static Validated<NonEmptyList<FieldError>, String> parseName(String raw) {
    return raw == null || raw.isBlank()
        ? Validated.invalidNel(FieldError.of("must not be blank"))
        : Validated.validNel(raw.strip());
  }

  private static Validated<NonEmptyList<FieldError>, String> parseEmail(String raw) {
    return raw != null && raw.contains("@")
        ? Validated.validNel(raw)
        : Validated.invalidNel(FieldError.of("not an email address"));
  }

  private static Validated<NonEmptyList<FieldError>, Integer> parseAge(String raw) {
    try {
      return Validated.validNel(Integer.parseInt(raw));
    } catch (NumberFormatException _) {
      return Validated.invalidNel(FieldError.of("not a number"));
    }
  }
}
