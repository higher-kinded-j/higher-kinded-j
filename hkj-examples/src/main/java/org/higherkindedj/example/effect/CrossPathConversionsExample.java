// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;

/**
 * Examples demonstrating conversions between different Path types.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Converting between MaybePath, EitherPath, TryPath, ValidationPath, OptionalPath, and IdPath
 *   <li>Error transformation during conversion
 *   <li>When to use which conversion
 *   <li>Information loss in certain conversions
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.CrossPathConversionsExample}
 */
public class CrossPathConversionsExample {

  private static final Semigroup<List<String>> LIST_SEMIGROUP = Semigroups.list();

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Cross-Path Conversions ===\n");

    maybePathConversions();
    eitherPathConversions();
    tryPathConversions();
    validationPathConversions();
    optionalPathConversions();
    idPathConversions();
    conversionChains();
  }

  // ===== MaybePath Conversions =====

  private static void maybePathConversions() {
    System.out.println("--- MaybePath Conversions ---");

    // From Just
    MaybePath<Integer> justPath = Path.just(42);

    // To EitherPath - provide error for Nothing case
    EitherPath<String, Integer> eitherFromJust = justPath.toEitherPath("No value");
    System.out.println("Just -> Either: " + eitherFromJust.run()); // Right[42]

    // To OptionalPath
    OptionalPath<Integer> optionalFromJust = justPath.toOptionalPath();
    System.out.println("Just -> Optional: " + optionalFromJust.run()); // Optional[42]

    // To ValidationPath
    ValidationPath<List<String>, Integer> validationFromJust =
        justPath.toValidationPath(List.of("No value"), LIST_SEMIGROUP);
    System.out.println("Just -> Validation: " + validationFromJust.run()); // Valid[42]

    // From Nothing
    MaybePath<Integer> nothingPath = Path.nothing();

    EitherPath<String, Integer> eitherFromNothing = nothingPath.toEitherPath("Value was absent");
    System.out.println("Nothing -> Either: " + eitherFromNothing.run()); // Left[Value was absent]

    System.out.println();
  }

  // ===== EitherPath Conversions =====

  private static void eitherPathConversions() {
    System.out.println("--- EitherPath Conversions ---");

    // From Right
    EitherPath<String, Integer> rightPath = Path.right(42);

    // To MaybePath - error information is LOST
    MaybePath<Integer> maybeFromRight = rightPath.toMaybePath();
    System.out.println("Right -> Maybe: " + maybeFromRight.run()); // Just[42]

    // To TryPath
    TryPath<Integer> tryFromRight = rightPath.toTryPath(RuntimeException::new);
    System.out.println("Right -> Try: " + tryFromRight.run()); // Success[42]

    // To ValidationPath - preserves error type
    ValidationPath<String, Integer> validationFromRight =
        rightPath.toValidationPath(Semigroups.first());
    System.out.println("Right -> Validation: " + validationFromRight.run()); // Valid[42]

    // To OptionalPath - error information is LOST
    OptionalPath<Integer> optionalFromRight = rightPath.toOptionalPath();
    System.out.println("Right -> Optional: " + optionalFromRight.run()); // Optional[42]

    // From Left
    EitherPath<String, Integer> leftPath = Path.left("Something went wrong");

    MaybePath<Integer> maybeFromLeft = leftPath.toMaybePath();
    System.out.println("Left -> Maybe (loses error): " + maybeFromLeft.run()); // Nothing

    System.out.println();
  }

  // ===== TryPath Conversions =====

  private static void tryPathConversions() {
    System.out.println("--- TryPath Conversions ---");

    // From Success
    TryPath<Integer> successPath = Path.success(42);

    // To MaybePath
    MaybePath<Integer> maybeFromSuccess = successPath.toMaybePath();
    System.out.println("Success -> Maybe: " + maybeFromSuccess.run()); // Just[42]

    // To EitherPath - transform Throwable to custom error type
    EitherPath<String, Integer> eitherFromSuccess = successPath.toEitherPath(Throwable::getMessage);
    System.out.println("Success -> Either: " + eitherFromSuccess.run()); // Right[42]

    // To OptionalPath
    OptionalPath<Integer> optionalFromSuccess = successPath.toOptionalPath();
    System.out.println("Success -> Optional: " + optionalFromSuccess.run()); // Optional[42]

    // From Failure
    TryPath<Integer> failurePath = Path.failure(new RuntimeException("Database error"));

    MaybePath<Integer> maybeFromFailure = failurePath.toMaybePath();
    System.out.println("Failure -> Maybe: " + maybeFromFailure.run()); // Nothing

    EitherPath<String, Integer> eitherFromFailure = failurePath.toEitherPath(Throwable::getMessage);
    System.out.println("Failure -> Either: " + eitherFromFailure.run()); // Left[Database error]

    System.out.println();
  }

  // ===== ValidationPath Conversions =====

  private static void validationPathConversions() {
    System.out.println("--- ValidationPath Conversions ---");

    // From Valid
    ValidationPath<List<String>, Integer> validPath = Path.valid(42, LIST_SEMIGROUP);

    // To EitherPath
    EitherPath<List<String>, Integer> eitherFromValid = validPath.toEitherPath();
    System.out.println("Valid -> Either: " + eitherFromValid.run()); // Right[42]

    // To MaybePath - error information is LOST
    MaybePath<Integer> maybeFromValid = validPath.toMaybePath();
    System.out.println("Valid -> Maybe: " + maybeFromValid.run()); // Just[42]

    // To TryPath - need to convert error to Throwable
    TryPath<Integer> tryFromValid =
        validPath.toTryPath(errors -> new RuntimeException(String.join(", ", errors)));
    System.out.println("Valid -> Try: " + tryFromValid.run()); // Success[42]

    // To OptionalPath
    OptionalPath<Integer> optionalFromValid = validPath.toOptionalPath();
    System.out.println("Valid -> Optional: " + optionalFromValid.run()); // Optional[42]

    // From Invalid
    ValidationPath<List<String>, Integer> invalidPath =
        Path.invalid(List.of("Error 1", "Error 2"), LIST_SEMIGROUP);

    EitherPath<List<String>, Integer> eitherFromInvalid = invalidPath.toEitherPath();
    System.out.println("Invalid -> Either: " + eitherFromInvalid.run()); // Left[[Error 1, Error 2]]

    System.out.println();
  }

  // ===== OptionalPath Conversions =====

  private static void optionalPathConversions() {
    System.out.println("--- OptionalPath Conversions ---");

    // From Present
    OptionalPath<Integer> presentPath = Path.present(42);

    // To MaybePath
    MaybePath<Integer> maybeFromPresent = presentPath.toMaybePath();
    System.out.println("Present -> Maybe: " + maybeFromPresent.run()); // Just[42]

    // To EitherPath - provide error for empty case
    EitherPath<String, Integer> eitherFromPresent = presentPath.toEitherPath("No value");
    System.out.println("Present -> Either: " + eitherFromPresent.run()); // Right[42]

    // To ValidationPath
    ValidationPath<List<String>, Integer> validationFromPresent =
        presentPath.toValidationPath(List.of("Required value missing"), LIST_SEMIGROUP);
    System.out.println("Present -> Validation: " + validationFromPresent.run()); // Valid[42]

    // From Absent
    OptionalPath<Integer> absentPath = Path.absent();

    MaybePath<Integer> maybeFromAbsent = absentPath.toMaybePath();
    System.out.println("Absent -> Maybe: " + maybeFromAbsent.run()); // Nothing

    EitherPath<String, Integer> eitherFromAbsent = absentPath.toEitherPath("Value is required");
    System.out.println("Absent -> Either: " + eitherFromAbsent.run()); // Left[Value is required]

    System.out.println();
  }

  // ===== IdPath Conversions =====

  private static void idPathConversions() {
    System.out.println("--- IdPath Conversions ---");

    // IdPath always has a value
    IdPath<Integer> idPath = Path.id(42);

    // To MaybePath - always Just
    MaybePath<Integer> maybeFromId = idPath.toMaybePath();
    System.out.println("Id -> Maybe: " + maybeFromId.run()); // Just[42]

    // To EitherPath - always Right
    EitherPath<String, Integer> eitherFromId = idPath.toEitherPath();
    System.out.println("Id -> Either: " + eitherFromId.run()); // Right[42]

    System.out.println();
  }

  // ===== Conversion Chains =====

  private static void conversionChains() {
    System.out.println("--- Conversion Chains ---");

    // Start with Optional from Java stdlib
    Optional<Integer> javaOptional = Optional.of(42);

    // OptionalPath -> EitherPath -> ValidationPath -> TryPath
    String result =
        Path.optional(javaOptional) // OptionalPath
            .toEitherPath("No value") // EitherPath
            .map(x -> x * 2) // Still EitherPath
            .toValidationPath(Semigroups.first()) // ValidationPath
            .map(x -> x + 10) // Still ValidationPath
            .toTryPath(RuntimeException::new) // TryPath
            .run()
            .fold(
                n -> "Final result: " + n, // Success case
                Throwable::getMessage // Error case
                );

    System.out.println("Conversion chain: " + result); // Final result: 94

    // Chain starting from empty Optional
    Optional<Integer> emptyOptional = Optional.empty();

    String emptyResult =
        Path.optional(emptyOptional)
            .toEitherPath("Value was required")
            .toValidationPath(Semigroups.first())
            .toTryPath(RuntimeException::new)
            .run()
            .fold(n -> "Success: " + n, Throwable::getMessage);

    System.out.println("Empty chain: " + emptyResult); // Value was required

    System.out.println();
  }
}
