// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.List;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.PathOps;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * Examples demonstrating PathOps utility operations: sequence, traverse, and firstSuccess.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Sequencing lists of paths into paths of lists
 *   <li>Traversing collections with effectful operations
 *   <li>Finding first successful result from multiple attempts
 *   <li>Accumulating validation errors across collections
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.PathOpsExample}
 */
public class PathOpsExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: PathOps Utilities ===\n");

    sequenceMaybeExamples();
    traverseMaybeExamples();
    sequenceEitherExamples();
    traverseEitherExamples();
    sequenceValidatedExamples();
    traverseValidatedExamples();
    firstSuccessExamples();
  }

  // ===== MaybePath Sequence/Traverse =====

  private static void sequenceMaybeExamples() {
    System.out.println("--- MaybePath Sequence ---");

    // All successful
    List<MaybePath<Integer>> allJust = List.of(Path.just(1), Path.just(2), Path.just(3));

    MaybePath<List<Integer>> sequenced = PathOps.sequenceMaybe(allJust);
    System.out.println("All Just: " + sequenced.run());
    // Just[[1, 2, 3]]

    // One Nothing fails the whole sequence
    List<MaybePath<Integer>> withNothing = List.of(Path.just(1), Path.nothing(), Path.just(3));

    MaybePath<List<Integer>> sequencedWithNothing = PathOps.sequenceMaybe(withNothing);
    System.out.println("With Nothing: " + sequencedWithNothing.run());
    // Nothing

    // Empty list
    List<MaybePath<Integer>> empty = List.of();
    MaybePath<List<Integer>> sequencedEmpty = PathOps.sequenceMaybe(empty);
    System.out.println("Empty list: " + sequencedEmpty.run());
    // Just[[]]

    System.out.println();
  }

  private static void traverseMaybeExamples() {
    System.out.println("--- MaybePath Traverse ---");

    // Parse numbers from strings
    List<String> validNumbers = List.of("1", "2", "3", "4", "5");
    MaybePath<List<Integer>> parsed =
        PathOps.traverseMaybe(validNumbers, PathOpsExample::parseIntMaybe);

    System.out.println("Valid numbers: " + parsed.run());
    // Just[[1, 2, 3, 4, 5]]

    // One invalid fails all
    List<String> withInvalid = List.of("1", "two", "3");
    MaybePath<List<Integer>> parsedInvalid =
        PathOps.traverseMaybe(withInvalid, PathOpsExample::parseIntMaybe);

    System.out.println("With invalid: " + parsedInvalid.run());
    // Nothing

    System.out.println();
  }

  private static MaybePath<Integer> parseIntMaybe(String s) {
    try {
      return Path.just(Integer.parseInt(s));
    } catch (NumberFormatException e) {
      return Path.nothing();
    }
  }

  // ===== EitherPath Sequence/Traverse =====

  private static void sequenceEitherExamples() {
    System.out.println("--- EitherPath Sequence ---");

    // All successful
    List<EitherPath<String, Integer>> allRight =
        List.of(Path.right(1), Path.right(2), Path.right(3));

    EitherPath<String, List<Integer>> sequenced = PathOps.sequenceEither(allRight);
    System.out.println("All Right: " + sequenced.run());
    // Right[[1, 2, 3]]

    // First Left short-circuits
    List<EitherPath<String, Integer>> withLeft =
        List.of(Path.right(1), Path.left("Error at position 2"), Path.right(3));

    EitherPath<String, List<Integer>> sequencedWithLeft = PathOps.sequenceEither(withLeft);
    System.out.println("With Left: " + sequencedWithLeft.run());
    // Left[Error at position 2]

    System.out.println();
  }

  private static void traverseEitherExamples() {
    System.out.println("--- EitherPath Traverse ---");

    // Validate and transform user IDs
    List<Integer> userIds = List.of(1, 2, 3);
    EitherPath<String, List<String>> userNames =
        PathOps.traverseEither(userIds, PathOpsExample::lookupUser);

    System.out.println("User lookup: " + userNames.run());
    // Right[[User_1, User_2, User_3]]

    // Include non-existent user
    List<Integer> withInvalid = List.of(1, 999, 3);
    EitherPath<String, List<String>> invalidLookup =
        PathOps.traverseEither(withInvalid, PathOpsExample::lookupUser);

    System.out.println("With invalid ID: " + invalidLookup.run());
    // Left[User 999 not found]

    System.out.println();
  }

  private static EitherPath<String, String> lookupUser(int id) {
    if (id > 0 && id < 100) {
      return Path.right("User_" + id);
    }
    return Path.left("User " + id + " not found");
  }

  // ===== ValidationPath Sequence/Traverse (Accumulating) =====

  private static void sequenceValidatedExamples() {
    System.out.println("--- ValidationPath Sequence (Accumulating) ---");

    // All valid
    List<ValidationPath<NonEmptyList<String>, Integer>> allValid =
        List.of(Path.validNel(1), Path.validNel(2), Path.validNel(3));

    ValidationPath<NonEmptyList<String>, List<Integer>> sequenced =
        PathOps.sequenceValidated(allValid, NonEmptyList.semigroup());

    System.out.println("All valid: " + sequenced.run());
    // Valid[[1, 2, 3]]

    // Multiple invalids - ALL errors accumulated
    List<ValidationPath<NonEmptyList<String>, Integer>> withInvalids =
        List.of(Path.invalidNel("Error 1"), Path.validNel(2), Path.invalidNel("Error 3"));

    ValidationPath<NonEmptyList<String>, List<Integer>> sequencedInvalids =
        PathOps.sequenceValidated(withInvalids, NonEmptyList.semigroup());

    System.out.println("With invalids (all errors): " + sequencedInvalids.run());
    // Invalid[NonEmptyList[Error 1, Error 3]]

    System.out.println();
  }

  private static void traverseValidatedExamples() {
    System.out.println("--- ValidationPath Traverse (Accumulating) ---");

    // Validate all emails
    List<String> emails = List.of("alice@example.com", "bob@company.org", "charlie@domain.net");

    ValidationPath<NonEmptyList<String>, List<String>> validated =
        PathOps.traverseValidated(emails, PathOpsExample::validateEmail, NonEmptyList.semigroup());

    System.out.println("Valid emails: " + validated.run());
    // Valid[[alice@example.com, bob@company.org, charlie@domain.net]]

    // Multiple invalid emails - ALL errors collected
    List<String> mixedEmails =
        List.of("alice@example.com", "invalid-email", "bob.com", "charlie@domain.net");

    ValidationPath<NonEmptyList<String>, List<String>> mixedValidation =
        PathOps.traverseValidated(
            mixedEmails, PathOpsExample::validateEmail, NonEmptyList.semigroup());

    System.out.println("Mixed emails (all errors): " + mixedValidation.run());
    // Invalid[NonEmptyList[Email 'invalid-email' must contain @, Email 'bob.com' must contain @]]

    System.out.println();
  }

  private static ValidationPath<NonEmptyList<String>, String> validateEmail(String email) {
    if (email == null || email.isBlank()) {
      return Path.invalidNel("Email cannot be empty");
    }
    if (!email.contains("@")) {
      return Path.invalidNel("Email '" + email + "' must contain @");
    }
    return Path.validNel(email);
  }

  // ===== TryPath FirstSuccess =====

  private static void firstSuccessExamples() {
    System.out.println("--- TryPath FirstSuccess ---");

    // Try multiple sources, use first that succeeds
    List<TryPath<String>> dataSources =
        List.of(
            Path.failure(new RuntimeException("Primary DB unavailable")),
            Path.failure(new RuntimeException("Cache miss")),
            Path.success("Data from backup source"));

    TryPath<String> result = PathOps.firstSuccess(dataSources);
    System.out.println("First success: " + result.run());
    // Success[Data from backup source]

    // All fail - returns last error
    List<TryPath<String>> allFail =
        List.of(
            Path.failure(new RuntimeException("Error 1")),
            Path.failure(new RuntimeException("Error 2")),
            Path.failure(new RuntimeException("Final error")));

    TryPath<String> allFailResult = PathOps.firstSuccess(allFail);
    System.out.println(
        "All fail: "
            + allFailResult
                .run()
                .foldFailureFirst(t -> "Failure: " + t.getMessage(), s -> "Success: " + s));
    // Failure: Final error

    // First succeeds - others not tried
    List<TryPath<String>> firstSucceeds =
        List.of(
            Path.success("Got it on first try!"),
            Path.failure(new RuntimeException("This won't be tried")));

    TryPath<String> firstSucceedsResult = PathOps.firstSuccess(firstSucceeds);
    System.out.println("First succeeds: " + firstSucceedsResult.run());
    // Success[Got it on first try!]

    // NonEmptyList overload: the sources are statically known, so the call is
    // total. There is no empty case to guard and no IllegalArgumentException.
    NonEmptyList<TryPath<String>> knownSources =
        NonEmptyList.of(
            Path.failure(new RuntimeException("Primary DB unavailable")),
            List.of(Path.success("Data from backup source")));

    TryPath<String> nelResult = PathOps.firstSuccess(knownSources);
    System.out.println("First success (NonEmptyList): " + nelResult.run());
    // Success[Data from backup source]

    System.out.println();
  }
}
