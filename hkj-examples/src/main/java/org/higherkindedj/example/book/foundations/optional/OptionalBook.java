// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.optional;

import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/optional_monad.html">Optional</a> page: a
 * customer profile's {@code Optional} fields read through an {@code OptionalPath}, and one method
 * written for any container whose error is absence, run with {@code Optional}'s instance and with
 * {@code Maybe}'s. The page {@code {{#include}}}s the anchored regions, {@code OptionalBookTest}
 * holds the claims the page makes, and the book's output gate runs {@link #main} against the
 * comments in the regions.
 */
public final class OptionalBook {

  /** A profile with a nickname and no second email address. */
  static final CustomerProfile ADA =
      new CustomerProfile("Ada", Optional.of("Countess"), Optional.empty());

  /** A profile with a second email address and no nickname. */
  static final CustomerProfile CHARLES =
      new CustomerProfile(
          "Charles", Optional.empty(), Optional.of(new EmailAddress("charles@example.org")));

  private OptionalBook() {}

  // ANCHOR: greeting
  // nickname is an Optional field, as many records' optional fields are
  static String greeting(CustomerProfile profile) {
    return Path.optional(profile.nickname())
        .map(nickname -> "Hello, " + nickname)
        .getOrElse("Hello, " + profile.name());
  }

  static OptionalPath<String> secondAddress(CustomerProfile profile) {
    return Path.optional(profile.altEmail()).map(EmailAddress::value);
  }

  // ANCHOR_END: greeting

  // ANCHOR: generic
  // Written once, for any container whose error is absence
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> contact(
      MonadError<F, Unit> absence, Kind<F, EmailAddress> email, String fallback) {
    return absence.handleError(absence.map(EmailAddress::value, email), unit -> fallback);
  }

  // ANCHOR_END: generic

  public static void main(String[] args) {
    // ANCHOR: greeting_outcomes
    String forAda = greeting(ADA);
    String forCharles = greeting(CHARLES);

    OptionalPath<String> charlesAddress = secondAddress(CHARLES);
    // OptionalPath(charles@example.org)
    Optional<String> adaAddress = secondAddress(ADA).run();
    // Optional.empty
    // ANCHOR_END: greeting_outcomes
    System.out.println(charlesAddress);
    System.out.println(adaAddress);
    System.out.println(forAda + " / " + forCharles);

    // ANCHOR: kind
    MonadError<OptionalKind.Witness, Unit> optionals = Instances.monadError(optional());
    MonadError<MaybeKind.Witness, Unit> maybes = Instances.monadError(maybe());

    Optional<String> fromOptional =
        OPTIONAL.narrow(contact(optionals, OPTIONAL.widen(CHARLES.altEmail()), "no address"));
    // Optional[charles@example.org]
    Maybe<String> fromMaybe =
        MAYBE.narrow(
            contact(maybes, MAYBE.widen(Maybe.fromOptional(ADA.altEmail())), "no address"));
    // Just(no address)
    // ANCHOR_END: kind
    System.out.println(fromOptional);
    System.out.println(fromMaybe);
  }
}
