// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.validated.ValidatedBook.address;
import static org.higherkindedj.example.book.foundations.validated.ValidatedBook.city;
import static org.higherkindedj.example.book.foundations.validated.ValidatedBook.deliveryAddress;
import static org.higherkindedj.example.book.foundations.validated.ValidatedBook.deliveryAddressPath;
import static org.higherkindedj.example.book.foundations.validated.ValidatedBook.oneAtATime;
import static org.higherkindedj.example.book.foundations.validated.ValidatedBook.postcode;
import static org.higherkindedj.example.book.foundations.validated.ValidatedBook.street;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.NoSuchElementException;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Validated page makes about stopping, accumulating and the bent law. */
@DisplayName("Validated: every error in one reply")
class ValidatedBookTest {

  private static final MonadError<ValidatedKind.Witness<NonEmptyList<String>>, NonEmptyList<String>>
      CHECKS = Instances.validated(NonEmptyList.semigroup());

  private static Validated<NonEmptyList<String>, Address> invalid(String first, String... rest) {
    return Validated.invalid(NonEmptyList.of(first, rest));
  }

  @Nested
  @DisplayName("flatMap stops at the first error")
  class FlatMapStops {

    @Test
    @DisplayName("three mistakes take three submits to find")
    void threeMistakesTakeThreeSubmits() {
      assertThat(oneAtATime("", "", "XYZ")).isEqualTo(invalid("street is required"));
      assertThat(oneAtATime("1 High Street", "", "XYZ")).isEqualTo(invalid("city is required"));
      assertThat(oneAtATime("1 High Street", "Leeds", "XYZ"))
          .isEqualTo(invalid("postcode XYZ is not a UK postcode"));
    }

    @Test
    @DisplayName("gives the address once every check passes")
    void givesTheAddressOnceEveryCheckPasses() {
      assertThat(oneAtATime("1 High Street", "Leeds", "LS1 4AP"))
          .isEqualTo(Validated.valid(new Address("1 High Street", "Leeds", "LS1 4AP")));
    }
  }

  @Nested
  @DisplayName("accumulate() reports every error")
  class AccumulateReportsEveryError {

    @Test
    @DisplayName("three mistakes come back in one reply, in the order of the checks")
    void threeMistakesInOneReply() {
      assertThat(address("", "", "XYZ"))
          .isEqualTo(
              invalid(
                  "street is required", "city is required", "postcode XYZ is not a UK postcode"));
    }

    @Test
    @DisplayName("a valid address comes back built")
    void aValidAddressComesBackBuilt() {
      assertThat(address(" 1 High Street ", "Leeds", "LS1 4AP"))
          .isEqualTo(Validated.valid(new Address("1 High Street", "Leeds", "LS1 4AP")));
    }
  }

  @Nested
  @DisplayName("a check that needs the address")
  class ACheckThatNeedsTheAddress {

    @Test
    @DisplayName("runs once the address is valid")
    void runsOnceTheAddressIsValid() {
      assertThat(deliveryAddress("1 High Street", "York", "YO1 7HH"))
          .isEqualTo(invalid("the courier does not deliver to YO1"));
      assertThat(deliveryAddress("1 High Street", "Leeds", "LS1 4AP").isValid()).isTrue();
    }

    @Test
    @DisplayName("never runs while the address has errors")
    void neverRunsWhileTheAddressHasErrors() {
      assertThat(deliveryAddress("", "", "YO1 7HH"))
          .isEqualTo(invalid("street is required", "city is required"));
    }
  }

  @Nested
  @DisplayName("on a ValidationPath")
  class OnAValidationPath {

    private final ValidationPath<NonEmptyList<String>, String> noStreet =
        Path.validatedNel(street(""));
    private final ValidationPath<NonEmptyList<String>, String> noCity = Path.validatedNel(city(""));
    private final ValidationPath<NonEmptyList<String>, String> badPostcode =
        Path.validatedNel(postcode("XYZ"));

    @Test
    @DisplayName("Path.accumulate() reports every error, as Validated.accumulate() does")
    void pathAccumulateReportsEveryError() {
      assertThat(deliveryAddressPath("", "", "XYZ").run()).isEqualTo(address("", "", "XYZ"));
      assertThat(deliveryAddressPath("1 High Street", "York", "YO1 7HH").run())
          .isEqualTo(invalid("the courier does not deliver to YO1"));
    }

    @Test
    @DisplayName("zipWith and zipWith3 stop at the first error")
    void zipWithAndZipWith3StopAtTheFirstError() {
      assertThat(noStreet.zipWith(noCity, String::concat).run())
          .isEqualTo(Validated.invalid(NonEmptyList.of("street is required")));
      assertThat(noStreet.zipWith3(noCity, badPostcode, Address::new).run())
          .isEqualTo(invalid("street is required"));
    }

    @Test
    @DisplayName("zipWithAccum, zipWith3Accum, andAlso and andThen keep every error")
    void theAccumulatingCombinatorsKeepEveryError() {
      NonEmptyList<String> both = NonEmptyList.of("street is required", "city is required");

      assertThat(noStreet.zipWithAccum(noCity, String::concat).run())
          .isEqualTo(Validated.invalid(both));
      assertThat(noStreet.zipWith3Accum(noCity, badPostcode, Address::new).run())
          .isEqualTo(
              invalid(
                  "street is required", "city is required", "postcode XYZ is not a UK postcode"));
      assertThat(noStreet.andAlso(noCity).run()).isEqualTo(Validated.invalid(both));
      assertThat(noStreet.andThen(noCity).run()).isEqualTo(Validated.invalid(both));
    }
  }

  @Nested
  @DisplayName("where the law bends")
  class WhereTheLawBends {

    @Test
    @DisplayName("map2 keeps both errors, and flatMap2 keeps the first")
    void map2AndFlatMap2Disagree() {
      Kind<ValidatedKind.Witness<NonEmptyList<String>>, String> street =
          VALIDATED.widen(street(""));
      Kind<ValidatedKind.Witness<NonEmptyList<String>>, String> city = VALIDATED.widen(city(""));

      assertThat(VALIDATED.narrow(CHECKS.map2(street, city, (s, c) -> s + ", " + c)))
          .isEqualTo(Validated.invalid(NonEmptyList.of("street is required", "city is required")));
      assertThat(VALIDATED.narrow(CHECKS.flatMap2(street, city, (s, c) -> CHECKS.of(s + ", " + c))))
          .isEqualTo(Validated.invalid(NonEmptyList.of("street is required")));
    }

    @Test
    @DisplayName("ap is not the ap that flatMap builds")
    void apIsNotTheApThatFlatMapBuilds() {
      Kind<ValidatedKind.Witness<NonEmptyList<String>>, Function<String, String>> noFunction =
          VALIDATED.widen(Validated.invalidNel("no function"));
      Kind<ValidatedKind.Witness<NonEmptyList<String>>, String> street =
          VALIDATED.widen(street(""));

      Validated<NonEmptyList<String>, String> ap = VALIDATED.narrow(CHECKS.ap(noFunction, street));
      Validated<NonEmptyList<String>, String> fromFlatMap =
          VALIDATED.narrow(
              CHECKS.<Function<String, String>, String>flatMap(
                  f -> CHECKS.map(f, street), noFunction));

      assertThat(ap)
          .isEqualTo(Validated.invalid(NonEmptyList.of("no function", "street is required")));
      assertThat(fromFlatMap).isEqualTo(Validated.invalid(NonEmptyList.of("no function")));
    }
  }

  @Nested
  @DisplayName("the methods")
  class TheMethods {

    private final Validated<NonEmptyList<String>, String> valid = Validated.validNel("LS1 4AP");
    private final Validated<NonEmptyList<String>, String> invalid =
        Validated.invalidNel("postcode is required");

    @Test
    @DisplayName("validateThat gives Valid(Unit) when the condition holds, and the error otherwise")
    void validateThatGivesUnitOrTheError() {
      assertThat(Validated.validateThat(true, "too long").isValid()).isTrue();
      assertThat(Validated.validateThat(false, "too long"))
          .isEqualTo(Validated.invalid("too long"));
    }

    @Test
    @DisplayName("map, mapError and bimap change only the case they are for")
    void theMapsChangeOnlyTheirCase() {
      assertThat(valid.map(String::length)).isEqualTo(Validated.valid(7));
      assertThat(invalid.map(String::length)).isEqualTo(invalid);
      assertThat(invalid.mapError(NonEmptyList::size)).isEqualTo(Validated.invalid(1));
      assertThat(valid.mapError(NonEmptyList::size)).isEqualTo(Validated.valid("LS1 4AP"));
      assertThat(valid.bimap(NonEmptyList::size, String::length)).isEqualTo(Validated.valid(7));
      assertThat(invalid.bimap(NonEmptyList::size, String::length)).isEqualTo(Validated.invalid(1));
    }

    @Test
    @DisplayName("ap joins both errors with the semigroup when both are invalid")
    void apJoinsBothErrors() {
      Validated<NonEmptyList<String>, Function<? super String, ? extends Integer>> noFunction =
          Validated.invalidNel("no function");
      Validated<NonEmptyList<String>, Function<? super String, ? extends Integer>> length =
          Validated.validNel(String::length);

      assertThat(invalid.ap(noFunction, NonEmptyList.semigroup()))
          .isEqualTo(Validated.invalid(NonEmptyList.of("no function", "postcode is required")));
      assertThat(valid.ap(length, NonEmptyList.semigroup())).isEqualTo(Validated.valid(7));
    }

    @Test
    @DisplayName("fold turns either case into one result")
    void foldTurnsEitherCaseIntoOneResult() {
      String fromValid = valid.fold(errors -> "errors", value -> "valid " + value);
      String fromInvalid = invalid.fold(errors -> "errors " + errors.size(), value -> "valid");

      assertThat(fromValid).isEqualTo("valid LS1 4AP");
      assertThat(fromInvalid).isEqualTo("errors 1");
    }

    @Test
    @DisplayName("orElse, orElseGet and orElseThrow give the value or the fallback")
    void theFallbacksGiveTheValueOrTheFallback() {
      assertThat(valid.orElse("none")).isEqualTo("LS1 4AP");
      assertThat(invalid.orElse("none")).isEqualTo("none");
      assertThat(invalid.orElseGet(() -> "none")).isEqualTo("none");
      assertThat(valid.orElseThrow(IllegalStateException::new)).isEqualTo("LS1 4AP");
      assertThatThrownBy(() -> invalid.orElseThrow(IllegalStateException::new))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("get and getError throw NoSuchElementException on the other case")
    void theGettersThrowOnTheOtherCase() {
      assertThat(valid.get()).isEqualTo("LS1 4AP");
      assertThat(invalid.getError()).isEqualTo(NonEmptyList.of("postcode is required"));
      assertThatThrownBy(invalid::get).isInstanceOf(NoSuchElementException.class);
      assertThatThrownBy(valid::getError).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("toEither keeps the same value or error")
    void toEitherKeepsTheSameValueOrError() {
      assertThat(valid.toEither()).isEqualTo(Either.right("LS1 4AP"));
      assertThat(invalid.toEither())
          .isEqualTo(Either.left(NonEmptyList.of("postcode is required")));
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("map3 accumulates, and handleErrorWith replaces the errors with a value")
    void map3AccumulatesAndHandleErrorWithRecovers() {
      Kind<ValidatedKind.Witness<NonEmptyList<String>>, Address> checked =
          CHECKS.map3(
              VALIDATED.widen(street("")),
              VALIDATED.widen(city("")),
              VALIDATED.widen(postcode("LS1 4AP")),
              Address::new);

      assertThat(VALIDATED.narrow(checked))
          .isEqualTo(invalid("street is required", "city is required"));
      assertThat(
              VALIDATED.narrow(
                  CHECKS.handleErrorWith(checked, errors -> CHECKS.of(ValidatedBook.PICKUP_POINT))))
          .isEqualTo(Validated.valid(ValidatedBook.PICKUP_POINT));
    }

    @Test
    @DisplayName("handleErrorWith never calls the handler on a valid value")
    void handleErrorWithSkipsAValidValue() {
      Kind<ValidatedKind.Witness<NonEmptyList<String>>, String> valid = CHECKS.of("LS1 4AP");

      assertThat(
              VALIDATED.narrow(
                  CHECKS.handleErrorWith(
                      valid,
                      errors -> {
                        throw new AssertionError("the handler ran");
                      })))
          .isEqualTo(Validated.valid("LS1 4AP"));
    }
  }
}
