// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.List;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.PathOps;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;

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

  private static final Semigroup<List<String>> LIST_SEMIGROUP = Semigroups.list();

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
    List<ValidationPath<List<String>, Integer>> allValid =
        List.of(
            Path.valid(1, LIST_SEMIGROUP),
            Path.valid(2, LIST_SEMIGROUP),
            Path.valid(3, LIST_SEMIGROUP));

    ValidationPath<List<String>, List<Integer>> sequenced =
        PathOps.sequenceValidated(allValid, LIST_SEMIGROUP);

    System.out.println("All valid: " + sequenced.run());
    // Valid[[1, 2, 3]]

    // Multiple invalids - ALL errors accumulated
    List<ValidationPath<List<String>, Integer>> withInvalids =
        List.of(
            Path.invalid(List.of("Error 1"), LIST_SEMIGROUP),
            Path.valid(2, LIST_SEMIGROUP),
            Path.invalid(List.of("Error 3"), LIST_SEMIGROUP));

    ValidationPath<List<String>, List<Integer>> sequencedInvalids =
        PathOps.sequenceValidated(withInvalids, LIST_SEMIGROUP);

    System.out.println("With invalids (all errors): " + sequencedInvalids.run());
    // Invalid[[Error 1, Error 3]]

    System.out.println();
  }

  private static void traverseValidatedExamples() {
    System.out.println("--- ValidationPath Traverse (Accumulating) ---");

    // Validate all emails
    List<String> emails = List.of("alice@example.com", "bob@company.org", "charlie@domain.net");

    ValidationPath<List<String>, List<String>> validated =
        PathOps.traverseValidated(emails, PathOpsExample::validateEmail, LIST_SEMIGROUP);

    System.out.println("Valid emails: " + validated.run());
    // Valid[[alice@example.com, bob@company.org, charlie@domain.net]]

    // Multiple invalid emails - ALL errors collected
    List<String> mixedEmails =
        List.of("alice@example.com", "invalid-email", "bob.com", "charlie@domain.net");

    ValidationPath<List<String>, List<String>> mixedValidation =
        PathOps.traverseValidated(mixedEmails, PathOpsExample::validateEmail, LIST_SEMIGROUP);

    System.out.println("Mixed emails (all errors): " + mixedValidation.run());
    // Invalid[[Email 'invalid-email' must contain @, Email 'bob.com' must contain @]]

    System.out.println();
  }

  private static ValidationPath<List<String>, String> validateEmail(String email) {
    if (email == null || email.isBlank()) {
      return Path.invalid(List.of("Email cannot be empty"), LIST_SEMIGROUP);
    }
    if (!email.contains("@")) {
      return Path.invalid(List.of("Email '" + email + "' must contain @"), LIST_SEMIGROUP);
    }
    return Path.valid(email, LIST_SEMIGROUP);
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
            + allFailResult.run().fold(s -> "Success: " + s, t -> "Failure: " + t.getMessage()));
    // Failure: Final error

    // First succeeds - others not tried
    List<TryPath<String>> firstSucceeds =
        List.of(
            Path.success("Got it on first try!"),
            Path.failure(new RuntimeException("This won't be tried")));

    TryPath<String> firstSucceedsResult = PathOps.firstSuccess(firstSucceeds);
    System.out.println("First succeeds: " + firstSucceedsResult.run());
    // Success[Got it on first try!]

    System.out.println();
  }
}
