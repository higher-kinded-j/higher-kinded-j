// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

/**
 * A runnable example demonstrating all factory methods in the {@link Prisms} utility class.
 *
 * <p>This example showcases the ready-made prisms for standard Java types:
 *
 * <ul>
 *   <li>{@code some()} - Working with Optional values
 *   <li>{@code left()} and {@code right()} - Either case handling
 *   <li>{@code only()} - Matching specific values
 *   <li>{@code notNull()} - Null-safety patterns
 *   <li>{@code instanceOf()} - Type-safe casting for hierarchies
 *   <li>{@code listHead()}, {@code listLast()}, {@code listAt()} - Collection element access
 * </ul>
 */
public class PrismsUtilityExample {

  // Example domain models for demonstrating prisms
  sealed interface Animal permits Dog, Cat, Bird {}

  @GenerateLenses
  record Dog(String name, String breed) implements Animal {}

  record Cat(String name, int lives) implements Animal {}

  record Bird(String name, boolean canFly) implements Animal {}

  record ValidationError(String code, String message) {}

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    System.out.println("=== Prisms Utility Examples ===\n");

    demonstrateSome();
    demonstrateLeftAndRight();
    demonstrateOnly();
    demonstrateNotNull();
    demonstrateInstanceOf();
    demonstrateListHead();
    demonstrateListLast();
    demonstrateListAt();
    demonstrateComposition();
  }

  private static void demonstrateSome() {
    System.out.println("--- Prisms.some(): Working with Optional ---");

    Prism<Optional<String>, String> somePrism = Prisms.some();

    // Extract from present Optional
    Optional<String> present = Optional.of("hello");
    Optional<String> extracted = somePrism.getOptional(present);
    System.out.println("Extract from present: " + extracted.orElse("N/A"));

    // Returns empty for empty Optional
    Optional<String> empty = Optional.empty();
    Optional<String> noMatch = somePrism.getOptional(empty);
    System.out.println("Extract from empty: " + noMatch.orElse("N/A"));

    // Build wraps value in Optional.of()
    Optional<String> built = somePrism.build("world");
    System.out.println("Built Optional: " + built);

    // Practical use: Flattening nested Optionals
    Optional<Optional<String>> nested = Optional.of(Optional.of("nested value"));
    Optional<String> flattened = nested.flatMap(opt -> opt);
    System.out.println("Flattened nested Optional: " + flattened.orElse("N/A"));

    System.out.println();
  }

  private static void demonstrateLeftAndRight() {
    System.out.println("--- Prisms.left() and Prisms.right(): Either Case Handling ---");

    Prism<Either<String, Integer>, String> leftPrism = Prisms.left();
    Prism<Either<String, Integer>, Integer> rightPrism = Prisms.right();

    Either<String, Integer> error = Either.left("Failed");
    Either<String, Integer> success = Either.right(42);

    // Extract from Left
    System.out.println("Left from error: " + leftPrism.getOptional(error).orElse("N/A"));
    System.out.println("Right from error: " + rightPrism.getOptional(error).orElse(-1));

    // Extract from Right
    System.out.println("Left from success: " + leftPrism.getOptional(success).orElse("N/A"));
    System.out.println("Right from success: " + rightPrism.getOptional(success).orElse(-1));

    // Build Left and Right
    Either<String, Integer> builtLeft = leftPrism.build("Error message");
    Either<String, Integer> builtRight = rightPrism.build(100);
    System.out.println("Built Left: " + builtLeft);
    System.out.println("Built Right: " + builtRight);

    // Practical use: Extract error messages from validation results
    Either<ValidationError, String> validationResult =
        Either.left(new ValidationError("ERR001", "Invalid email format"));

    Prism<Either<ValidationError, String>, ValidationError> validationErrorPrism = Prisms.left();
    Optional<String> errorCode =
        validationErrorPrism.mapOptional(ValidationError::code, validationResult);
    System.out.println("Validation error code: " + errorCode.orElse("N/A"));

    System.out.println();
  }

  private static void demonstrateOnly() {
    System.out.println("--- Prisms.only(): Matching Specific Values ---");

    Prism<String, Unit> httpOkPrism = Prisms.only("200 OK");
    Prism<String, Unit> httpErrorPrism = Prisms.only("500 ERROR");

    List<String> statusCodes = List.of("200 OK", "404 NOT FOUND", "500 ERROR", "200 OK");

    // Check for specific status
    System.out.println("First is OK: " + httpOkPrism.matches(statusCodes.get(0)));
    System.out.println("Second is OK: " + httpOkPrism.matches(statusCodes.get(1)));

    // Filter for specific values
    long okCount = statusCodes.stream().filter(httpOkPrism::matches).count();
    long errorCount = statusCodes.stream().filter(httpErrorPrism::matches).count();

    System.out.println("OK count: " + okCount);
    System.out.println("Error count: " + errorCount);

    // Null sentinel handling
    Prism<String, Unit> nullPrism = Prisms.only(null);
    System.out.println("Null matches null: " + nullPrism.matches(null));
    System.out.println("Null matches 'text': " + nullPrism.matches("text"));

    // Practical use: Sentinel value detection in configs
    String shutdownSignal = "SHUTDOWN";
    Prism<String, Unit> shutdownPrism = Prisms.only(shutdownSignal);
    if (shutdownPrism.matches(shutdownSignal)) {
      System.out.println("Shutdown signal detected!");
    }

    System.out.println();
  }

  private static void demonstrateNotNull() {
    System.out.println("--- Prisms.notNull(): Null Safety ---");

    Prism<String, String> notNullPrism = Prisms.notNull();

    // Safe extraction
    String value = "hello";
    String nullValue = null;

    System.out.println(
        "Extract from 'hello': " + notNullPrism.getOptional(value).orElse("was null"));
    System.out.println(
        "Extract from null: " + notNullPrism.getOptional(nullValue).orElse("was null"));

    // Practical use: Filter null values in a list
    List<String> mixedList = Arrays.asList("hello", null, "world", null, "test");

    Traversal<List<String>, String> nonNullStrings =
        Traversals.<String>forList().andThen(Prisms.<String>notNull().asTraversal());

    List<String> filtered = Traversals.getAll(nonNullStrings, mixedList);
    System.out.println("Filtered non-null values: " + filtered);

    // Count non-null values
    long nonNullCount = mixedList.stream().filter(notNullPrism::matches).count();
    System.out.println("Non-null count: " + nonNullCount);

    System.out.println();
  }

  private static void demonstrateInstanceOf() {
    System.out.println("--- Prisms.instanceOf(): Type-Safe Casting ---");

    Prism<Animal, Dog> dogPrism = Prisms.instanceOf(Dog.class);
    Prism<Animal, Cat> catPrism = Prisms.instanceOf(Cat.class);
    Prism<Animal, Bird> birdPrism = Prisms.instanceOf(Bird.class);

    List<Animal> animals =
        List.of(
            new Dog("Rex", "German Shepherd"),
            new Cat("Whiskers", 9),
            new Dog("Max", "Beagle"),
            new Bird("Tweety", true));

    // Extract all dogs
    List<Dog> dogs = animals.stream().flatMap(a -> dogPrism.getOptional(a).stream()).toList();
    System.out.println("Dogs: " + dogs);

    // Count by type
    long dogCount = animals.stream().filter(dogPrism::matches).count();
    long catCount = animals.stream().filter(catPrism::matches).count();
    long birdCount = animals.stream().filter(birdPrism::matches).count();

    System.out.println("Dog count: " + dogCount);
    System.out.println("Cat count: " + catCount);
    System.out.println("Bird count: " + birdCount);

    // Compose with lenses for deep access
    Lens<Dog, String> breedLens = DogLenses.breed();
    Traversal<Animal, String> dogBreed = dogPrism.asTraversal().andThen(breedLens.asTraversal());

    List<String> breeds =
        Traversals.getAll(Traversals.<Animal>forList().andThen(dogBreed), animals);
    System.out.println("All dog breeds: " + breeds);

    // Modify all dogs
    List<Animal> renamedDogs =
        animals.stream()
            .map(a -> dogPrism.modify(dog -> new Dog("Sir " + dog.name(), dog.breed()), a))
            .toList();
    System.out.println("Renamed dogs: " + renamedDogs);

    System.out.println();
  }

  private static void demonstrateListHead() {
    System.out.println("--- Prisms.listHead(): First Element Access ---");

    Prism<List<String>, String> headPrism = Prisms.listHead();

    List<String> names = List.of("Alice", "Bob", "Charlie");
    List<String> empty = List.of();

    // Extract first element
    System.out.println("First of names: " + headPrism.getOptional(names).orElse("N/A"));
    System.out.println("First of empty: " + headPrism.getOptional(empty).orElse("N/A"));

    // Check if list has elements
    System.out.println("Names has head: " + headPrism.matches(names));
    System.out.println("Empty has head: " + headPrism.matches(empty));

    // Get with default
    String firstOrDefault = headPrism.getOrElse("Unknown", names);
    System.out.println("First or default: " + firstOrDefault);

    // Build creates singleton list
    List<String> built = headPrism.build("Solo");
    System.out.println("Built list: " + built);

    // Practical use: Safe access to first element
    List<Integer> numbers = List.of(1, 2, 3, 4, 5);
    Optional<Integer> firstEven =
        headPrism.mapOptional(name -> name.length() % 2 == 0 ? 1 : 0, names);
    System.out.println("First name has even length: " + firstEven.orElse(-1));

    System.out.println();
  }

  private static void demonstrateListLast() {
    System.out.println("--- Prisms.listLast(): Last Element Access ---");

    Prism<List<String>, String> lastPrism = Prisms.listLast();

    List<String> names = List.of("Alice", "Bob", "Charlie");
    List<String> empty = List.of();

    // Extract last element
    System.out.println("Last of names: " + lastPrism.getOptional(names).orElse("N/A"));
    System.out.println("Last of empty: " + lastPrism.getOptional(empty).orElse("N/A"));

    // Check if list has elements
    System.out.println("Names has last: " + lastPrism.matches(names));
    System.out.println("Empty has last: " + lastPrism.matches(empty));

    // Transform last element
    Optional<Integer> lastLength = lastPrism.mapOptional(String::length, names);
    System.out.println("Length of last name: " + lastLength.orElse(0));

    // Practical use: Recent log entry
    List<String> logEntries =
        List.of(
            "2025-01-01 10:00:00 INFO Starting",
            "2025-01-01 10:01:00 INFO Processing",
            "2025-01-01 10:02:00 ERROR Failed");

    String mostRecent = lastPrism.getOrElse("No logs", logEntries);
    System.out.println("Most recent log: " + mostRecent);

    System.out.println();
  }

  private static void demonstrateListAt() {
    System.out.println("--- Prisms.listAt(): Element at Index ---");

    List<String> names = List.of("Alice", "Bob", "Charlie", "David");

    // Access different indices
    Prism<List<String>, String> firstPrism = Prisms.listAt(0);
    Prism<List<String>, String> secondPrism = Prisms.listAt(1);
    Prism<List<String>, String> thirdPrism = Prisms.listAt(2);
    Prism<List<String>, String> outOfBoundsPrism = Prisms.listAt(10);

    System.out.println("Index 0: " + firstPrism.getOptional(names).orElse("N/A"));
    System.out.println("Index 1: " + secondPrism.getOptional(names).orElse("N/A"));
    System.out.println("Index 2: " + thirdPrism.getOptional(names).orElse("N/A"));
    System.out.println("Index 10: " + outOfBoundsPrism.getOptional(names).orElse("N/A"));

    // Check if index exists
    System.out.println("Has index 1: " + secondPrism.matches(names));
    System.out.println("Has index 10: " + outOfBoundsPrism.matches(names));

    // Transform element at index
    Optional<String> upperSecond = secondPrism.mapOptional(String::toUpperCase, names);
    System.out.println("Second name uppercase: " + upperSecond.orElse("N/A"));

    // Negative index always returns empty
    Prism<List<String>, String> negativePrism = Prisms.listAt(-1);
    System.out.println("Negative index: " + negativePrism.getOptional(names).orElse("N/A"));

    // Note: build() throws UnsupportedOperationException
    System.out.println("Note: listAt() build() is not supported for modification");

    System.out.println();
  }

  private static void demonstrateComposition() {
    System.out.println("--- Composing Utility Prisms ---");

    // Complex nested structure
    record DatabaseSettings(String host, int port) {}

    record Config(Optional<Either<String, DatabaseSettings>> database) {}

    // Build a composed path through Optional -> Either -> Settings -> host
    // (This would normally use generated lenses, but we'll simulate it)
    Prism<Optional<Either<String, DatabaseSettings>>, Either<String, DatabaseSettings>> somePrism =
        Prisms.some();
    Prism<Either<String, DatabaseSettings>, DatabaseSettings> rightPrism = Prisms.right();

    // Valid configuration
    Config validConfig =
        new Config(Optional.of(Either.right(new DatabaseSettings("localhost", 5432))));

    // Extract database settings
    Optional<DatabaseSettings> settings =
        somePrism.getOptional(validConfig.database()).flatMap(rightPrism::getOptional);

    System.out.println("Database host: " + settings.map(DatabaseSettings::host).orElse("N/A"));
    System.out.println("Database port: " + settings.map(DatabaseSettings::port).orElse(-1));

    // Invalid configuration (error message)
    Config errorConfig = new Config(Optional.of(Either.left("Database not configured")));

    Prism<Either<String, DatabaseSettings>, String> leftPrism = Prisms.left();
    Optional<String> errorMsg =
        somePrism.getOptional(errorConfig.database()).flatMap(leftPrism::getOptional);

    System.out.println("Error message: " + errorMsg.orElse("N/A"));

    // Missing configuration
    Config missingConfig = new Config(Optional.empty());
    Optional<DatabaseSettings> missingSettings =
        somePrism.getOptional(missingConfig.database()).flatMap(rightPrism::getOptional);

    System.out.println(
        "Missing config host: " + missingSettings.map(DatabaseSettings::host).orElse("N/A"));

    System.out.println();
  }
}
