// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.constant;

import module java.base;
import module org.higherkindedj.core;

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;

/**
 * Comprehensive examples demonstrating the Const type and its Bifunctor instance.
 *
 * <p>Const<C, A> is a constant functor that holds a value of type C whilst treating A as a phantom
 * type parameter. The key property is that mapping over the second type parameter (the phantom
 * type) has no effect on the constant value, making Const particularly useful for:
 *
 * <ul>
 *   <li>Efficient fold implementations that accumulate a single value
 *   <li>Getter patterns in lens and traversal libraries
 *   <li>Validation scenarios where you extract data without transformation
 *   <li>Data extraction from complex structures
 * </ul>
 *
 * <p>This class demonstrates:
 *
 * <ul>
 *   <li>Creating Const instances with phantom types
 *   <li>Using Bifunctor operations (first, second, bimap)
 *   <li>The phantom type property: mapSecond has no runtime effect
 *   <li>Real-world applications: folds, getters, validation
 * </ul>
 *
 * @see <a href="https://higher-kinded-j.github.io/const_type.html">Const Type Documentation</a>
 */
public class ConstExample {

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    System.out.println("=== Const Type Examples ===\n");

    demonstrateBasicConstCreation();
    demonstratePhantomTypeProperty();
    demonstrateBifunctorOperations();
    demonstrateFoldImplementation();
    demonstrateGetterPattern();
    demonstrateValidationExtraction();
  }

  // ============================================================================
  // Example 1: Basic Const Creation
  // ============================================================================

  private static void demonstrateBasicConstCreation() {
    System.out.println("--- Example 1: Basic Const Creation ---");
    System.out.println(
        "Const<C, A> holds a value of type C. The type A is phantom (not stored).\n");

    // Create a Const holding a String, with Integer as the phantom type
    Const<String, Integer> stringConst = new Const<>("Hello, Const!");
    System.out.println("Created Const<String, Integer>: " + stringConst);
    System.out.println("Constant value: " + stringConst.value());
    // Output: Hello, Const!

    // Create a Const holding a List, with Double as the phantom type
    Const<List<String>, Double> listConst = new Const<>(List.of("accumulate", "values", "here"));
    System.out.println("\nCreated Const<List<String>, Double>: " + listConst);
    System.out.println("Constant value: " + listConst.value());
    // Output: [accumulate, values, here]

    // Const can hold any value, including null
    Const<String, Boolean> nullConst = new Const<>(null);
    System.out.println("\nConst can hold null: " + nullConst.value());
    // Output: null

    System.out.println("\n");
  }

  // ============================================================================
  // Example 2: The Phantom Type Property
  // ============================================================================

  private static void demonstratePhantomTypeProperty() {
    System.out.println("--- Example 2: The Phantom Type Property ---");
    System.out.println(
        "The second type parameter is 'phantom' - it exists only in the type signature.\n");

    Bifunctor<ConstKind2.Witness> bifunctor = ConstBifunctor.INSTANCE;

    // Start with a Const holding a count
    Const<Integer, String> originalConst = new Const<>(42);
    System.out.println("Original Const<Integer, String>: " + originalConst);
    System.out.println("Constant value: " + originalConst.value());

    // Use second() to change the phantom type from String to Double
    // Notice: The mapper function is required for type safety, but the CONSTANT VALUE is unchanged
    Kind2<ConstKind2.Witness, Integer, Double> afterSecond =
        bifunctor.second(
            s -> 3.14, // This function defines the phantom type transformation
            CONST.widen2(originalConst));

    Const<Integer, Double> transformedConst = CONST.narrow2(afterSecond);
    System.out.println("\nAfter second(): Const<Integer, Double>: " + transformedConst);
    System.out.println("Constant value: " + transformedConst.value());
    // Output: 42 (UNCHANGED!)

    // The phantom type changed from String to Double, but the constant value stayed 42
    System.out.println(
        "\nKey insight: The constant value (42) didn't change, only the phantom type.\n");
  }

  // ============================================================================
  // Example 3: Bifunctor Operations
  // ============================================================================

  private static void demonstrateBifunctorOperations() {
    System.out.println("--- Example 3: Bifunctor Operations ---");
    System.out.println(
        "Const implements Bifunctor: first() transforms the constant, second() only changes the"
            + " phantom type.\n");

    Bifunctor<ConstKind2.Witness> bifunctor = ConstBifunctor.INSTANCE;

    Const<String, Integer> original = new Const<>("hello");
    System.out.println("Original: " + original + " (value: " + original.value() + ")");

    // 1. first() - Transforms the CONSTANT value
    Kind2<ConstKind2.Witness, Integer, Integer> afterFirst =
        bifunctor.first(String::length, CONST.widen2(original));

    Const<Integer, Integer> firstResult = CONST.narrow2(afterFirst);
    System.out.println("\nAfter first(String::length):");
    System.out.println("  Result: " + firstResult + " (value: " + firstResult.value() + ")");
    // Output: Const[5] - the constant value changed from "hello" to 5

    // 2. second() - Changes only the phantom type, constant value unchanged
    // Note: The mapper must be null-safe since phantom type has no value
    Kind2<ConstKind2.Witness, String, Double> afterSecond =
        bifunctor.second(i -> 2.0, CONST.widen2(original));

    Const<String, Double> secondResult = CONST.narrow2(afterSecond);
    System.out.println("\nAfter second(i -> 2.0):");
    System.out.println("  Result: " + secondResult + " (value: " + secondResult.value() + ")");
    // Output: Const[hello] - the constant value is still "hello"

    // 3. bimap() - Combines first and second
    // Note: Second mapper must be null-safe (phantom type has no value)
    Kind2<ConstKind2.Witness, Integer, String> afterBimap =
        bifunctor.bimap(
            String::length, // Transforms the constant: "hello" -> 5
            i -> "Phantom", // Phantom type transformation (null-safe)
            CONST.widen2(original));

    Const<Integer, String> bimapResult = CONST.narrow2(afterBimap);
    System.out.println("\nAfter bimap(String::length, i -> \"Phantom\"):");
    System.out.println("  Result: " + bimapResult + " (value: " + bimapResult.value() + ")");
    // Output: Const[5] - only the first mapper affected the constant value

    System.out.println("\n");
  }

  // ============================================================================
  // Example 4: Fold Implementation
  // ============================================================================

  private static void demonstrateFoldImplementation() {
    System.out.println("--- Example 4: Fold Implementation ---");
    System.out.println(
        "Const is perfect for folds: accumulate a value whilst traversing a structure.\n");

    // Simulate a fold over a list that counts elements
    List<String> items = List.of("apple", "banana", "cherry", "date");

    // Use Const to accumulate a count whilst "traversing" the list
    Const<Integer, String> initialCount = new Const<>(0);

    Const<Integer, String> finalCount =
        items.stream()
            .reduce(
                initialCount,
                // Each step: increment the count using mapFirst
                // The phantom type (String) represents the "shape" we're traversing
                (acc, item) -> acc.mapFirst(count -> count + 1),
                (c1, c2) -> c1.mapFirst(v1 -> v1 + c2.value()));

    System.out.println("Items: " + items);
    System.out.println("Count (using Const): " + finalCount.value());
    // Output: 4

    // Another fold: accumulate total length of strings
    Const<Integer, String> initialLength = new Const<>(0);

    Const<Integer, String> totalLength =
        items.stream()
            .reduce(
                initialLength,
                (acc, item) -> acc.mapFirst(length -> length + item.length()),
                (c1, c2) -> c1.mapFirst(v1 -> v1 + c2.value()));

    System.out.println("\nTotal length of all strings: " + totalLength.value());
    // Output: 23 (5 + 6 + 6 + 4)

    System.out.println("\n");
  }

  // ============================================================================
  // Example 5: Getter Pattern (Lens-style)
  // ============================================================================

  private static void demonstrateGetterPattern() {
    System.out.println("--- Example 5: Getter Pattern (Lens-style) ---");
    System.out.println(
        "Const enables the getter pattern used in lens libraries: extract without transforming.\n");

    // Example: Extract a field from a Person record
    record Person(String name, int age, String city) {}

    Person person = new Person("Alice", 30, "London");

    // Create a "getter" using Const - it extracts the name but the phantom type represents Person
    Function<Person, Const<String, Person>> nameGetter = p -> new Const<>(p.name());

    Const<String, Person> nameConst = nameGetter.apply(person);
    System.out.println("Person: " + person);
    System.out.println("Extracted name: " + nameConst.value());
    // Output: Alice

    // The phantom type (Person) maintains the "context" of what we're getting from
    System.out.println("Phantom type: Person (maintains context)");

    // Chain getters: extract age
    Function<Person, Const<Integer, Person>> ageGetter = p -> new Const<>(p.age());

    Const<Integer, Person> ageConst = ageGetter.apply(person);
    System.out.println("\nExtracted age: " + ageConst.value());
    // Output: 30

    // This pattern is the foundation for van Laarhoven lenses in functional programming
    System.out.println(
        "\nThis pattern underpins van Laarhoven lenses, allowing compositional getters.\n");
  }

  // ============================================================================
  // Example 6: Validation Data Extraction
  // ============================================================================

  private static void demonstrateValidationExtraction() {
    System.out.println("--- Example 6: Validation Data Extraction ---");
    System.out.println("Extract data from validation results without transforming the errors.\n");

    // Simulate validation results
    record ValidationResult(boolean isValid, List<String> errors, Object data) {}

    List<ValidationResult> results =
        List.of(
            new ValidationResult(true, List.of(), "Valid data 1"),
            new ValidationResult(false, List.of("Error A", "Error B"), null),
            new ValidationResult(true, List.of(), "Valid data 2"),
            new ValidationResult(false, List.of("Error C"), null));

    // Use Const to extract all errors whilst traversing validations
    // The phantom type represents the structure we're validating
    List<String> allErrors = new ArrayList<>();

    for (ValidationResult result : results) {
      // Extract errors using Const
      Const<List<String>, ValidationResult> errorConst = new Const<>(result.errors());

      // Accumulate the constant value (errors)
      allErrors.addAll(errorConst.value());
    }

    System.out.println("Validation results processed: " + results.size());
    System.out.println("All accumulated errors: " + allErrors);
    // Output: [Error A, Error B, Error C]

    // Count valid vs invalid
    Const<Integer, ValidationResult> validCount =
        results.stream()
            .reduce(
                new Const<>(0),
                (acc, result) ->
                    new Const<Integer, ValidationResult>(
                        result.isValid() ? acc.value() + 1 : acc.value()),
                (c1, c2) -> new Const<>(c1.value() + c2.value()));

    System.out.println("\nValid results: " + validCount.value());
    // Output: 2

    System.out.println("\nConst enables efficient data extraction during validation traversals.");
  }
}
