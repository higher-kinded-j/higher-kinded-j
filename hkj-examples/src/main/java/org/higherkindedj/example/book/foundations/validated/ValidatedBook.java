// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.validated;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.Set;
import java.util.regex.Pattern;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/validated_monad.html">Validated</a> page:
 * three checks of a delivery address, joined first with {@code flatMap}, which stops at the first
 * error, then with {@code accumulate()}, which reports them all; a check that needs the address
 * first; the same checks on a {@code ValidationPath}; and {@code Validated}'s instance over {@code
 * Kind}. The page {@code {{#include}}}s the anchored regions, {@code ValidatedBookTest} holds the
 * claims the page makes, and the book's output gate runs {@link #main} against the comments in the
 * regions.
 */
public final class ValidatedBook {

  /** A UK postcode's shape: an outward code, a space, then a digit and two letters. */
  private static final Pattern POSTCODE = Pattern.compile("[A-Z]{1,2}[0-9][A-Z0-9]? [0-9][A-Z]{2}");

  /** The outward codes the courier delivers to. */
  static final Set<String> COURIER_AREAS = Set.of("LS1", "LS2", "M1");

  /** Where an order waits when its own address will not do. */
  static final Address PICKUP_POINT = new Address("2 Station Road", "Leeds", "LS1 4DY");

  private ValidatedBook() {}

  // ANCHOR: checks
  // Each check returns a valid value, or its error in a NonEmptyList
  static Validated<NonEmptyList<String>, String> street(String street) {
    return street.isBlank()
        ? Validated.invalidNel("street is required")
        : Validated.validNel(street.strip());
  }

  static Validated<NonEmptyList<String>, String> city(String city) {
    return city.isBlank()
        ? Validated.invalidNel("city is required")
        : Validated.validNel(city.strip());
  }

  static Validated<NonEmptyList<String>, String> postcode(String postcode) {
    return POSTCODE.matcher(postcode).matches()
        ? Validated.validNel(postcode)
        : Validated.invalidNel("postcode " + postcode + " is not a UK postcode");
  }

  // ANCHOR_END: checks

  // ANCHOR: one_at_a_time
  // flatMap runs a check only when the one before it passed
  static Validated<NonEmptyList<String>, Address> oneAtATime(
      String street, String city, String postcode) {
    return street(street)
        .flatMap(s -> city(city).flatMap(c -> postcode(postcode).map(p -> new Address(s, c, p))));
  }

  // ANCHOR_END: one_at_a_time

  // ANCHOR: accumulate
  static Validated<NonEmptyList<String>, Address> address(
      String street, String city, String postcode) {
    return Validated.accumulate()
        .and(street(street))
        .and(city(city))
        .and(postcode(postcode))
        .apply(Address::new);
  }

  // ANCHOR_END: accumulate

  // ANCHOR: dependent
  // This check needs a whole address, so it can only follow the others
  static Validated<NonEmptyList<String>, Address> deliverable(Address address) {
    String area = address.postcode().substring(0, address.postcode().indexOf(' '));
    return COURIER_AREAS.contains(area)
        ? Validated.validNel(address)
        : Validated.invalidNel("the courier does not deliver to " + area);
  }

  static Validated<NonEmptyList<String>, Address> deliveryAddress(
      String street, String city, String postcode) {
    return address(street, city, postcode).flatMap(ValidatedBook::deliverable);
  }

  // ANCHOR_END: dependent

  // ANCHOR: path
  static ValidationPath<NonEmptyList<String>, Address> deliveryAddressPath(
      String street, String city, String postcode) {
    return Path.accumulate()
        .and(Path.validatedNel(street(street)))
        .and(Path.validatedNel(city(city)))
        .and(Path.validatedNel(postcode(postcode)))
        .apply(Address::new)
        .via(address -> Path.validatedNel(deliverable(address)));
  }

  // ANCHOR_END: path

  public static void main(String[] args) {
    // ANCHOR: submits
    Validated<NonEmptyList<String>, Address> first = oneAtATime("", "", "XYZ");
    // Invalid(NonEmptyList[street is required])
    Validated<NonEmptyList<String>, Address> second = oneAtATime("1 High Street", "", "XYZ");
    // Invalid(NonEmptyList[city is required])
    Validated<NonEmptyList<String>, Address> third = oneAtATime("1 High Street", "Leeds", "XYZ");
    // Invalid(NonEmptyList[postcode XYZ is not a UK postcode])
    // ANCHOR_END: submits
    System.out.println(first);
    System.out.println(second);
    System.out.println(third);

    // ANCHOR: one_reply
    Validated<NonEmptyList<String>, Address> everyError = address("", "", "XYZ");
    // Invalid(NonEmptyList[street is required, city is required,
    //   postcode XYZ is not a UK postcode])
    Validated<NonEmptyList<String>, Address> valid = address("1 High Street", "Leeds", "LS1 4AP");
    // Valid(Address[street=1 High Street, city=Leeds, postcode=LS1 4AP])
    // ANCHOR_END: one_reply
    System.out.println(everyError);
    System.out.println(valid);

    // ANCHOR: delivery
    Validated<NonEmptyList<String>, Address> faraway =
        deliveryAddress("1 High Street", "York", "YO1 7HH");
    // Invalid(NonEmptyList[the courier does not deliver to YO1])
    Validated<NonEmptyList<String>, Address> blank = deliveryAddress("", "", "YO1 7HH");
    // Invalid(NonEmptyList[street is required, city is required])
    // ANCHOR_END: delivery
    System.out.println(faraway);
    System.out.println(blank);

    // ANCHOR: path_outcome
    ValidationPath<NonEmptyList<String>, Address> onThePath = deliveryAddressPath("", "", "XYZ");
    // ValidationPath(Invalid(NonEmptyList[street is required, city is required,
    //   postcode XYZ is not a UK postcode]))
    // ANCHOR_END: path_outcome
    System.out.println(onThePath);

    // ANCHOR: kind
    MonadError<ValidatedKind.Witness<NonEmptyList<String>>, NonEmptyList<String>> checks =
        Instances.validated(NonEmptyList.semigroup());

    Kind<ValidatedKind.Witness<NonEmptyList<String>>, Address> checked =
        checks.map3(
            VALIDATED.widen(street("")),
            VALIDATED.widen(city("")),
            VALIDATED.widen(postcode("LS1 4AP")),
            Address::new);
    Kind<ValidatedKind.Witness<NonEmptyList<String>>, Address> orPickup =
        checks.handleErrorWith(checked, errors -> checks.of(PICKUP_POINT));

    Validated<NonEmptyList<String>, Address> bothErrors = VALIDATED.narrow(checked);
    // Invalid(NonEmptyList[street is required, city is required])
    Validated<NonEmptyList<String>, Address> pickup = VALIDATED.narrow(orPickup);
    // Valid(Address[street=2 Station Road, city=Leeds, postcode=LS1 4DY])
    // ANCHOR_END: kind
    System.out.println(bothErrors);
    System.out.println(pickup);
  }
}
