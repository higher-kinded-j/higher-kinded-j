// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.bifunctor;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.either.EitherKind2;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Bifunctor;
import org.higherkindedj.hkt.tuple.Tuple2Kind2;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedBifunctor;
import org.higherkindedj.hkt.validated.ValidatedKind2;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterBifunctor;
import org.higherkindedj.hkt.writer.WriterKind2;

/**
 * Comprehensive examples demonstrating the Bifunctor type class.
 *
 * <p>A Bifunctor represents types with two type parameters that are both covariant, allowing
 * independent or simultaneous transformation of both "sides" of the type.
 *
 * <p>This class demonstrates:
 *
 * <ul>
 *   <li>Sum Types (Either, Validated): Transform error OR success channels
 *   <li>Product Types (Tuple2, Writer): Transform both components simultaneously
 *   <li>Using bimap(), first(), and second() operations
 *   <li>Real-world scenarios: API responses, validation, logging
 * </ul>
 */
public class BifunctorExample {

  public static void main(String[] args) {
    System.out.println("=== Bifunctor Examples ===\n");

    demonstrateEitherBifunctor();
    demonstrateTuple2Bifunctor();
    demonstrateValidatedBifunctor();
    demonstrateWriterBifunctor();
    demonstrateRealWorldScenarios();
  }

  // ============================================================================
  // Example 1: Either - A Sum Type (Left XOR Right)
  // ============================================================================

  private static void demonstrateEitherBifunctor() {
    System.out.println("--- Example 1: Either Bifunctor (Sum Type) ---");
    System.out.println(
        "Either represents a value that is EITHER Left (error) OR Right (success)\n");

    Bifunctor<EitherKind2.Witness> bifunctor = EitherBifunctor.INSTANCE;

    // Case 1: Transform a successful result (Right)
    Either<String, Integer> successCase = Either.right(42);
    Kind2<EitherKind2.Witness, String, Integer> wrappedSuccess = EITHER.widen2(successCase);

    // Transform the success channel: multiply by 2
    Kind2<EitherKind2.Witness, String, Integer> doubledSuccess =
        bifunctor.second(n -> n * 2, wrappedSuccess);

    System.out.println("Original Right: " + successCase);
    System.out.println("After second(): " + EITHER.narrow2(doubledSuccess));
    // Output: Right(84)

    // Case 2: Transform an error result (Left)
    Either<String, Integer> errorCase = Either.left("INVALID_INPUT");
    Kind2<EitherKind2.Witness, String, Integer> wrappedError = EITHER.widen2(errorCase);

    // Transform the error channel: add context
    Kind2<EitherKind2.Witness, String, Integer> enhancedError =
        bifunctor.first(err -> "Error Code: " + err, wrappedError);

    System.out.println("\nOriginal Left: " + errorCase);
    System.out.println("After first():  " + EITHER.narrow2(enhancedError));
    // Output: Left(Error Code: INVALID_INPUT)

    // Case 3: Transform both channels simultaneously with bimap
    Either<String, Integer> eitherValue = Either.right(100);
    Kind2<EitherKind2.Witness, String, Integer> wrappedEither = EITHER.widen2(eitherValue);

    Kind2<EitherKind2.Witness, Integer, String> transformed =
        bifunctor.bimap(
            String::length, // Transform error: string -> length
            n -> "Result: " + n, // Transform success: number -> formatted string
            wrappedEither);

    System.out.println("\nOriginal Either: " + eitherValue);
    System.out.println("After bimap():   " + EITHER.narrow2(transformed));
    // Output: Right(Result: 100)

    System.out.println("\n");
  }

  // ============================================================================
  // Example 2: Tuple2 - A Product Type (Both First AND Second)
  // ============================================================================

  private static void demonstrateTuple2Bifunctor() {
    System.out.println("--- Example 2: Tuple2 Bifunctor (Product Type) ---");
    System.out.println("Tuple2 holds two values: BOTH first AND second are present\n");

    Bifunctor<Tuple2Kind2.Witness> bifunctor = Tuple2Bifunctor.INSTANCE;

    // A tuple representing a name and age
    Tuple2<String, Integer> person = new Tuple2<>("Alice", 30);
    Kind2<Tuple2Kind2.Witness, String, Integer> wrappedPerson = TUPLE2.widen2(person);

    // Transform only the first element (name)
    Kind2<Tuple2Kind2.Witness, Integer, Integer> nameLength =
        bifunctor.first(String::length, wrappedPerson);

    System.out.println("Original Tuple: " + person);
    System.out.println("After first():  " + TUPLE2.narrow2(nameLength));
    // Output: Tuple2(5, 30)

    // Transform only the second element (age)
    Kind2<Tuple2Kind2.Witness, String, String> ageFormatted =
        bifunctor.second(age -> age + " years old", wrappedPerson);

    System.out.println("After second(): " + TUPLE2.narrow2(ageFormatted));
    // Output: Tuple2(Alice, 30 years old)

    // Transform both simultaneously
    Kind2<Tuple2Kind2.Witness, String, String> formatted =
        bifunctor.bimap(
            name -> "Name: " + name, // Transform first
            age -> "Age: " + age, // Transform second
            wrappedPerson);

    System.out.println("After bimap():  " + TUPLE2.narrow2(formatted));
    // Output: Tuple2(Name: Alice, Age: 30)

    System.out.println("\n");
  }

  // ============================================================================
  // Example 3: Validated - A Sum Type for Validation (Invalid XOR Valid)
  // ============================================================================

  private static void demonstrateValidatedBifunctor() {
    System.out.println("--- Example 3: Validated Bifunctor (Sum Type) ---");
    System.out.println("Validated represents a validation result: EITHER Invalid OR Valid\n");

    Bifunctor<ValidatedKind2.Witness> bifunctor = ValidatedBifunctor.INSTANCE;

    // Case 1: Valid data
    Validated<List<String>, Integer> validData = Validated.valid(100);
    Kind2<ValidatedKind2.Witness, List<String>, Integer> wrappedValid = VALIDATED.widen2(validData);

    // Transform the valid channel
    Kind2<ValidatedKind2.Witness, List<String>, String> transformedValid =
        bifunctor.second(n -> "Value: " + n, wrappedValid);

    System.out.println("Original Valid: " + validData);
    System.out.println("After second(): " + VALIDATED.narrow2(transformedValid));
    // Output: Valid(Value: 100)

    // Case 2: Invalid data with errors
    Validated<List<String>, Integer> invalidData =
        Validated.invalid(List.of("TOO_SMALL", "OUT_OF_RANGE"));
    Kind2<ValidatedKind2.Witness, List<String>, Integer> wrappedInvalid =
        VALIDATED.widen2(invalidData);

    // Transform the error channel: add prefixes
    Kind2<ValidatedKind2.Witness, List<String>, Integer> enhancedErrors =
        bifunctor.first(errors -> errors.stream().map(e -> "ERROR: " + e).toList(), wrappedInvalid);

    System.out.println("\nOriginal Invalid: " + invalidData);
    System.out.println("After first():    " + VALIDATED.narrow2(enhancedErrors));
    // Output: Invalid([ERROR: TOO_SMALL, ERROR: OUT_OF_RANGE])

    // Case 3: Use bimap to normalise both channels
    Kind2<ValidatedKind2.Witness, String, String> normalised =
        bifunctor.bimap(
            errors -> "Validation failed: " + errors.size() + " errors",
            value -> "Success: " + value,
            wrappedInvalid);

    System.out.println("After bimap():    " + VALIDATED.narrow2(normalised));
    // Output: Invalid(Validation failed: 2 errors)

    System.out.println("\n");
  }

  // ============================================================================
  // Example 4: Writer - A Product Type (Log AND Value)
  // ============================================================================

  private static void demonstrateWriterBifunctor() {
    System.out.println("--- Example 4: Writer Bifunctor (Product Type) ---");
    System.out.println("Writer holds BOTH a log (first) AND a computed value (second)\n");

    Bifunctor<WriterKind2.Witness> bifunctor = WriterBifunctor.INSTANCE;

    // A Writer with a log and a computation result
    Writer<String, Integer> computation = new Writer<>("Calculated: ", 42);
    Kind2<WriterKind2.Witness, String, Integer> wrappedWriter = WRITER.widen2(computation);

    // Transform only the log channel
    Kind2<WriterKind2.Witness, String, Integer> uppercaseLog =
        bifunctor.first(String::toUpperCase, wrappedWriter);

    System.out.println("Original Writer: " + computation);
    System.out.println("After first():   " + WRITER.narrow2(uppercaseLog));
    // Output: Writer(CALCULATED: , 42)

    // Transform only the value channel
    Kind2<WriterKind2.Witness, String, String> formattedValue =
        bifunctor.second(n -> "Result: " + n, wrappedWriter);

    System.out.println("After second():  " + WRITER.narrow2(formattedValue));
    // Output: Writer(Calculated: , Result: 42)

    // Transform both channels
    Kind2<WriterKind2.Witness, List<String>, String> structured =
        bifunctor.bimap(
            log -> List.of("[LOG]", log), // Wrap log in a list
            value -> "Final: " + value, // Format value
            wrappedWriter);

    System.out.println("After bimap():   " + WRITER.narrow2(structured));
    // Output: Writer([LOG], Calculated: , Final: 42)

    System.out.println("\n");
  }

  // ============================================================================
  // Real-World Scenarios
  // ============================================================================

  private static void demonstrateRealWorldScenarios() {
    System.out.println("--- Real-World Scenarios ---\n");

    // Scenario 1: API Response Transformation
    apiResponseTransformation();

    // Scenario 2: Validation Pipeline
    validationPipeline();

    // Scenario 3: Data Migration
    dataMigration();
  }

  private static void apiResponseTransformation() {
    System.out.println("Scenario 1: API Response Transformation");
    System.out.println("Transform both error messages and success data to match API contract\n");

    Bifunctor<EitherKind2.Witness> bifunctor = EitherBifunctor.INSTANCE;

    // Internal error representation
    Either<String, UserData> internalResult = Either.left("USER_NOT_FOUND");

    // Transform to external API format
    Function<String, ApiError> toApiError = code -> new ApiError(code, "Error occurred", 404);
    Function<UserData, ApiResponse> toApiResponse =
        user -> new ApiResponse(user.name(), user.email(), 200);

    Kind2<EitherKind2.Witness, ApiError, ApiResponse> apiResult =
        bifunctor.bimap(toApiError, toApiResponse, EITHER.widen2(internalResult));

    System.out.println("Internal: " + internalResult);
    System.out.println("External: " + EITHER.narrow2(apiResult));
    System.out.println();
  }

  private static void validationPipeline() {
    System.out.println("Scenario 2: Validation Pipeline");
    System.out.println("Format both validation errors and successful results\n");

    Bifunctor<ValidatedKind2.Witness> bifunctor = ValidatedBifunctor.INSTANCE;

    // Validation result with technical error codes
    Validated<List<String>, String> validationResult =
        Validated.invalid(List.of("ERR_001", "ERR_002"));

    // Transform to user-friendly messages
    Kind2<ValidatedKind2.Witness, String, String> userFriendly =
        bifunctor.bimap(
            errors -> "Please correct the following: " + String.join(", ", errors),
            value -> "✓ Validation passed: " + value,
            VALIDATED.widen2(validationResult));

    System.out.println("Technical: " + validationResult);
    System.out.println("User-friendly: " + VALIDATED.narrow2(userFriendly));
    System.out.println();
  }

  private static void dataMigration() {
    System.out.println("Scenario 3: Data Migration");
    System.out.println("Transform both fields of legacy data to new schema\n");

    Bifunctor<Tuple2Kind2.Witness> bifunctor = Tuple2Bifunctor.INSTANCE;

    // Legacy format: (id_string, timestamp_long)
    Tuple2<String, Long> legacyData = new Tuple2<>("USER_123", 1640000000000L);

    // Migrate to new format: (id_integer, iso_timestamp_string)
    Kind2<Tuple2Kind2.Witness, Integer, String> migratedData =
        bifunctor.bimap(
            id -> Integer.parseInt(id.substring(5)), // Extract number from "USER_123"
            timestamp ->
                java.time.Instant.ofEpochMilli(timestamp).toString(), // Convert to ISO string
            TUPLE2.widen2(legacyData));

    System.out.println("Legacy: " + legacyData);
    System.out.println("Migrated: " + TUPLE2.narrow2(migratedData));
    System.out.println();
  }

  // ============================================================================
  // Helper Classes
  // ============================================================================

  record UserData(String name, String email) {}

  record ApiError(String code, String message, int httpStatus) {}

  record ApiResponse(String name, String email, int httpStatus) {}
}
