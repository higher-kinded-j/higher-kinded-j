// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.profunctor;

import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.func.FunctionKind;
import org.higherkindedj.hkt.func.FunctionProfunctor;

/**
 * A comprehensive example demonstrating the power of Profunctors using {@link FunctionProfunctor}.
 *
 * <p>Profunctors are particularly useful for creating flexible, composable data transformation
 * pipelines where you need to adapt both the input and output of functions. This example shows
 * real-world scenarios where profunctors shine: API adapters, data formatters, and validation
 * pipelines.
 */
public class ProfunctorExample {

  // Example domain models
  public record User(String name, String email, LocalDate birthDate) {}

  public record UserDto(String fullName, String emailAddress, String birthDateString) {}

  public record ValidationResult(boolean isValid, String message) {}

  public record ApiResponse<T>(T data, String status, int code) {}

  public static void main(String[] args) {
    ProfunctorExample example = new ProfunctorExample();

    System.out.println("=== Profunctor Example: Function Composition and Adaptation ===\n");

    example.basicProfunctorOperations();
    example.apiAdapterExample();
    example.validationPipelineExample();
    example.dataTransformationExample();
  }

  /** Demonstrates the basic profunctor operations: lmap, rmap, and dimap. */
  public void basicProfunctorOperations() {
    System.out.println("--- Basic Profunctor Operations ---");

    FunctionProfunctor profunctor = FunctionProfunctor.INSTANCE;

    // Original function: String length calculator
    Function<String, Integer> stringLength = String::length;
    Kind2<FunctionKind.Witness, String, Integer> lengthFunction = FUNCTION.widen(stringLength);

    System.out.println("Original function result: " + stringLength.apply("Hello World")); // 11

    // 1. LMAP (contravariant): Adapt the input
    // Transform Integer -> String -> Integer (pre-process input)
    Kind2<FunctionKind.Witness, Integer, Integer> intToLength =
        profunctor.lmap(Object::toString, lengthFunction);

    Function<Integer, Integer> intLengthFunc = FUNCTION.getFunction(intToLength);
    System.out.println("After lmap (int->string): " + intLengthFunc.apply(12345)); // 5

    // 2. RMAP (covariant): Adapt the output
    // Transform String -> Integer -> String (post-process output)
    Kind2<FunctionKind.Witness, String, String> lengthToString =
        profunctor.rmap(len -> "Length: " + len, lengthFunction);

    Function<String, String> lengthStringFunc = FUNCTION.getFunction(lengthToString);
    System.out.println(
        "After rmap (int->string): " + lengthStringFunc.apply("Profunctor")); // Length: 10

    // 3. DIMAP: Adapt both input and output simultaneously
    // Transform Integer -> String -> String (adapt both ends)
    Kind2<FunctionKind.Witness, Integer, String> fullTransform =
        profunctor.dimap(
            Object::toString, // pre-process: int -> string
            len -> "Result: " + len, // post-process: int -> string
            lengthFunction);

    Function<Integer, String> fullFunc = FUNCTION.getFunction(fullTransform);
    System.out.println("After dimap (int->string): " + fullFunc.apply(42)); // Result: 2

    System.out.println();
  }

  /**
   * Demonstrates using profunctors to create flexible API adapters that can handle different input
   * formats and output requirements.
   */
  public void apiAdapterExample() {
    System.out.println("--- API Adapter Pattern ---");

    FunctionProfunctor profunctor = FunctionProfunctor.INSTANCE;

    // Core business logic: validate a user
    Function<User, ValidationResult> validateUser =
        user -> {
          boolean isValid = user.email().contains("@") && !user.name().isEmpty();
          String message = isValid ? "Valid user" : "Invalid user data";
          return new ValidationResult(isValid, message);
        };

    Kind2<FunctionKind.Witness, User, ValidationResult> coreValidator =
        FUNCTION.widen(validateUser);

    // Scenario 1: API expects UserDto input and ApiResponse output
    Kind2<FunctionKind.Witness, UserDto, ApiResponse<ValidationResult>> dtoApiValidator =
        profunctor.dimap(
            // Convert UserDto -> User (contravariant input adaptation)
            dto ->
                new User(
                    dto.fullName(), dto.emailAddress(), LocalDate.parse(dto.birthDateString())),
            // Convert ValidationResult -> ApiResponse (covariant output adaptation)
            result -> new ApiResponse<>(result, "OK", result.isValid() ? 200 : 400),
            coreValidator);

    Function<UserDto, ApiResponse<ValidationResult>> dtoApiFunc =
        FUNCTION.getFunction(dtoApiValidator);

    UserDto validDto = new UserDto("John Doe", "john@example.com", "1990-01-01");
    UserDto invalidDto = new UserDto("", "invalid-email", "1990-01-01");

    System.out.println("Valid DTO result: " + dtoApiFunc.apply(validDto));
    System.out.println("Invalid DTO result: " + dtoApiFunc.apply(invalidDto));

    // Scenario 2: Different output format - just a boolean
    Kind2<FunctionKind.Witness, UserDto, Boolean> dtoBooleanValidator =
        profunctor.dimap(
            dto ->
                new User(
                    dto.fullName(), dto.emailAddress(), LocalDate.parse(dto.birthDateString())),
            ValidationResult::isValid, // Extract just the boolean
            coreValidator);

    Function<UserDto, Boolean> dtoBooleanFunc = FUNCTION.getFunction(dtoBooleanValidator);
    System.out.println("Boolean validation result: " + dtoBooleanFunc.apply(validDto)); // true

    System.out.println();
  }

  /**
   * Shows how profunctors can create reusable validation pipelines that adapt to different input
   * and output requirements.
   */
  public void validationPipelineExample() {
    System.out.println("--- Validation Pipeline with Profunctors ---");

    FunctionProfunctor profunctor = FunctionProfunctor.INSTANCE;

    // Core validation logic: check if a number is positive
    Function<Double, Boolean> isPositive = x -> x > 0;
    Kind2<FunctionKind.Witness, Double, Boolean> positiveValidator = FUNCTION.widen(isPositive);

    // Pipeline 1: Validate string numbers with detailed error messages
    Kind2<FunctionKind.Witness, String, String> stringValidator =
        profunctor.dimap(
            // Parse string to double (with error handling)
            str -> {
              try {
                return Double.parseDouble(str);
              } catch (NumberFormatException e) {
                return -1.0; // Invalid marker
              }
            },
            // Convert boolean to detailed message
            isValid -> isValid ? "✓ Valid positive number" : "✗ Not a positive number",
            positiveValidator);

    Function<String, String> stringValidatorFunc = FUNCTION.getFunction(stringValidator);

    System.out.println("String validation results:");
    System.out.println("  '42.5': " + stringValidatorFunc.apply("42.5"));
    System.out.println("  '-10': " + stringValidatorFunc.apply("-10"));
    System.out.println("  'abc': " + stringValidatorFunc.apply("abc"));

    // Pipeline 2: Validate User objects by checking their age
    Kind2<FunctionKind.Witness, User, ValidationResult> userAgeValidator =
        profunctor.dimap(
            // Extract age from birth date
            user -> {
              long years = java.time.Period.between(user.birthDate(), LocalDate.now()).getYears();
              return (double) years;
            },
            // Convert boolean to ValidationResult
            isValid ->
                new ValidationResult(
                    isValid, isValid ? "User is of valid age" : "User must be older than 0"),
            positiveValidator);

    Function<User, ValidationResult> userValidatorFunc = FUNCTION.getFunction(userAgeValidator);

    User youngUser = new User("Alice", "alice@example.com", LocalDate.of(2010, 5, 15));
    User futureUser = new User("Bob", "bob@example.com", LocalDate.of(2030, 1, 1));

    System.out.println("\nUser validation results:");
    System.out.println("  Young user: " + userValidatorFunc.apply(youngUser));
    System.out.println("  Future user: " + userValidatorFunc.apply(futureUser));

    System.out.println();
  }

  /**
   * Demonstrates using profunctors for complex data transformation pipelines, showing how to chain
   * multiple adaptations.
   */
  public void dataTransformationExample() {
    System.out.println("--- Data Transformation Pipeline ---");

    FunctionProfunctor profunctor = FunctionProfunctor.INSTANCE;

    // Core transformation: User -> UserDto
    Function<User, UserDto> userToDto =
        user ->
            new UserDto(
                user.name(),
                user.email(),
                user.birthDate().format(DateTimeFormatter.ISO_LOCAL_DATE));

    Kind2<FunctionKind.Witness, User, UserDto> coreTransform = FUNCTION.widen(userToDto);

    // Scenario 1: Transform from CSV string to JSON-like string
    Function<String, User> csvParser =
        csvLine -> {
          try {
            if (csvLine == null || csvLine.trim().isEmpty()) {
              return new User("INVALID", "invalid@example.com", LocalDate.of(1900, 1, 1));
            }

            String[] parts = csvLine.split(",");
            if (parts.length < 3) {
              return new User("INVALID", "invalid@example.com", LocalDate.of(1900, 1, 1));
            }

            String name = parts[0].trim();
            String email = parts[1].trim();
            String dateStr = parts[2].trim();

            if (name.isEmpty() || email.isEmpty() || dateStr.isEmpty()) {
              return new User("INVALID", "invalid@example.com", LocalDate.of(1900, 1, 1));
            }

            LocalDate date = LocalDate.parse(dateStr);
            return new User(name, email, date);
          } catch (Exception e) {
            return new User("INVALID", "invalid@example.com", LocalDate.of(1900, 1, 1));
          }
        };

    Function<UserDto, String> dtoToJson =
        dto ->
            String.format(
                "{\"name\":\"%s\",\"email\":\"%s\",\"birthDate\":\"%s\"}",
                dto.fullName(), dto.emailAddress(), dto.birthDateString());

    Kind2<FunctionKind.Witness, String, String> csvToJsonTransform =
        profunctor.dimap(csvParser, dtoToJson, coreTransform);

    Function<String, String> csvToJsonFunc = FUNCTION.getFunction(csvToJsonTransform);

    String csvInput = "Jane Smith, jane@example.com, 1985-03-20";
    System.out.println("CSV input: " + csvInput);
    System.out.println("JSON output: " + csvToJsonFunc.apply(csvInput));

    // Scenario 2: Chain multiple transformations using profunctor composition
    // Add validation + transformation
    Kind2<FunctionKind.Witness, String, ApiResponse<String>> fullPipeline =
        profunctor.rmap(
            // Wrap result in ApiResponse
            jsonString -> new ApiResponse<>(jsonString, "SUCCESS", 200),
            csvToJsonTransform);

    Function<String, ApiResponse<String>> fullPipelineFunc = FUNCTION.getFunction(fullPipeline);

    System.out.println("\nFull pipeline result:");
    System.out.println(fullPipelineFunc.apply(csvInput));

    // Scenario 3: Error handling transformation
    Kind2<FunctionKind.Witness, String, ApiResponse<String>> safeTransform =
        profunctor.dimap(
            Function.identity(), // No input transformation
            // Safe output transformation with error handling
            jsonString -> {
              try {
                // Check for invalid data markers
                if (jsonString.contains("INVALID") || jsonString.isEmpty()) {
                  return new ApiResponse<>("", "ERROR: Invalid input data", 400);
                }
                return new ApiResponse<>(jsonString, "SUCCESS", 200);
              } catch (Exception e) {
                return new ApiResponse<>("", "INTERNAL_ERROR: " + e.getMessage(), 500);
              }
            },
            csvToJsonTransform);

    Function<String, ApiResponse<String>> safeTransformFunc = FUNCTION.getFunction(safeTransform);

    System.out.println("\nSafe transformation results:");
    System.out.println("Valid input: " + safeTransformFunc.apply(csvInput));
    System.out.println("Empty input: " + safeTransformFunc.apply(""));
    System.out.println("Malformed input: " + safeTransformFunc.apply("incomplete,data"));

    System.out.println();
  }
}
