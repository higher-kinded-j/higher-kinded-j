// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.monads.assembly;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.annotations.GenerateAssembly;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/monads/validated_assembly.html">Accumulating Assembly</a>
 * page. The page {@code {{#include}}}s the anchored regions, so it cannot drift from the API.
 */
public final class ValidatedAssemblyBook {

  private ValidatedAssemblyBook() {}

  public static void main(String[] args) {
    // Deliberately bad, so the accumulating result below is the one the page shows.
    UserDto dto = new UserDto("Ada", "not-an-email", "not-a-number");
    RawAddress raw = new RawAddress("Ada", "1 Main St", "SW1A 1AA");
    RawSettings rawSettings = new RawSettings("localhost", "8080");
    RawConfigIn rawConfig = new RawConfigIn("8080", "30");

    Kind<ValidatedKind.Witness<List<String>>, Name> nameKind =
        VALIDATED.widen(Validated.valid(new Name("Ada")));
    Kind<ValidatedKind.Witness<List<String>>, Email> emailKind =
        VALIDATED.widen(Validated.valid(new Email("ada@example.com")));
    Kind<ValidatedKind.Witness<List<String>>, Age> ageKind =
        VALIDATED.widen(Validated.valid(new Age(36)));

    // ANCHOR: hkt_generic
    // The HKT-generic form: capped at map5, and every call passes the Semigroup by hand.
    MonadError<ValidatedKind.Witness<List<String>>, List<String>> validatedMonad =
        Instances.validated(Semigroups.list());
    Kind<ValidatedKind.Witness<List<String>>, User> user =
        validatedMonad.map3(nameKind, emailKind, ageKind, User::new);
    // ANCHOR_END: hkt_generic

    // ANCHOR: fields
    Validated<NonEmptyList<FieldError>, User> assembled =
        Validated.fields()
            .field("name", parseName(dto.name())) // Validated<NonEmptyList<FieldError>, Name>
            .field("email", parseEmail(dto.email()))
            .field("age", parseAge(dto.age()))
            .apply(User::new); // (Name, Email, Age) -> User

    // Invalid(NonEmptyList[email: not an email address, age: not a number]) - name was fine.
    // ANCHOR_END: fields
    System.out.println(assembled);

    // ANCHOR: nesting
    Validated<NonEmptyList<FieldError>, Address> address =
        Validated.fields()
            .field("street", parseStreet(raw.street()))
            .field("zip", parseZip(raw.zip())) // fails: "zip: not a postcode"
            .apply(Address::new);

    Validated<NonEmptyList<FieldError>, Customer> customer =
        Validated.fields()
            .field("name", parseName(raw.name()))
            .field("address", address) // prefixes: "address.zip: not a postcode"
            .apply(Customer::new);
    // ANCHOR_END: nesting
    System.out.println(customer);

    // ANCHOR: accumulate
    Validated<NonEmptyList<ConfigError>, Settings> settings =
        Validated.accumulate()
            .and(parseHost(rawSettings.host()))
            .and(parsePort(rawSettings.port()))
            .apply(Settings::new);
    // ANCHOR_END: accumulate
    System.out.println(settings);

    // ANCHOR: eob_accumulate
    EitherOrBoth<NonEmptyList<String>, Config> cfg =
        EitherOrBoth.accumulate()
            .and(parsePortLenient(rawConfig.port())) // Both("port defaulted", 8080)
            .and(parseTimeoutLenient(rawConfig.timeout())) // Right(30)
            .apply(Config::new);
    // Both(NonEmptyList[port defaulted], Config[port=8080, timeout=30])
    // ANCHOR_END: eob_accumulate
    System.out.println(cfg);

    // ANCHOR: generated_usage
    Validated<NonEmptyList<FieldError>, User> generated =
        UserAssembly.fields()
            .name(parseName(dto.name())) // label "name" attached automatically
            .email(parseEmail(dto.email()))
            .age(parseAge(dto.age()))
            .assemble(); // canonical constructor baked in
    // ANCHOR_END: generated_usage
    System.out.println(generated);
  }

  static Validated<NonEmptyList<FieldError>, Name> parseName(String s) {
    return Validated.validNel(new Name(s));
  }

  static Validated<NonEmptyList<FieldError>, Email> parseEmail(String s) {
    return s.contains("@")
        ? Validated.validNel(new Email(s))
        : Validated.invalidNel(FieldError.of("not an email address"));
  }

  static Validated<NonEmptyList<FieldError>, Age> parseAge(String s) {
    try {
      return Validated.validNel(new Age(Integer.parseInt(s)));
    } catch (NumberFormatException e) {
      // Accumulate the reason; do not throw. Throwing here would defeat the whole page.
      return Validated.invalidNel(FieldError.of("not a number"));
    }
  }

  static Validated<NonEmptyList<FieldError>, String> parseStreet(String s) {
    return Validated.validNel(s);
  }

  static Validated<NonEmptyList<FieldError>, String> parseZip(String s) {
    return Validated.invalidNel(FieldError.of("not a postcode"));
  }

  static Validated<NonEmptyList<ConfigError>, String> parseHost(String s) {
    return Validated.validNel(s);
  }

  static Validated<NonEmptyList<ConfigError>, Integer> parsePort(String s) {
    return Validated.validNel(Integer.parseInt(s));
  }

  static EitherOrBoth<NonEmptyList<String>, Integer> parsePortLenient(String s) {
    return EitherOrBoth.both(NonEmptyList.single("port defaulted"), 8080);
  }

  static EitherOrBoth<NonEmptyList<String>, Integer> parseTimeoutLenient(String s) {
    return EitherOrBoth.right(30);
  }
}

record Name(String value) {}

record Email(String value) {}

record Age(int value) {}

// ANCHOR: generated_spec
@GenerateAssembly
record User(Name name, Email email, Age age) {}

// ANCHOR_END: generated_spec

record Address(String street, String zip) {}

record Customer(Name name, Address address) {}

record Settings(String host, int port) {}

record Config(int port, int timeout) {}

record ConfigError(String message) {}

record UserDto(String name, String email, String age) {}

record RawAddress(String name, String street, String zip) {}

record RawSettings(String host, String port) {}

record RawConfigIn(String port, String timeout) {}
