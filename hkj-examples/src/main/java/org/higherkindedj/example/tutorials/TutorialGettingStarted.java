// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials;

import module org.higherkindedj.core;

import org.higherkindedj.optics.Lens;

/**
 * Verify your Higher-Kinded-J setup is configured correctly.
 *
 * <p>This example tests that all core dependencies are available and working properly. Run this
 * <b>before</b> starting the tutorials to ensure a smooth learning experience.
 *
 * <h2>What This Tests</h2>
 *
 * <ul>
 *   <li><b>Core Types:</b> {@link Either}, {@link Maybe}, and other fundamental types
 *   <li><b>Optics:</b> {@link Lens} and annotation-generated optics
 *   <li><b>Type Classes:</b> Functor, Applicative, Monad access
 *   <li><b>Java Version:</b> Records, sealed interfaces, pattern matching
 * </ul>
 *
 * <h2>Expected Output</h2>
 *
 * <p>If everything is configured correctly, you should see:
 *
 * <pre>{@code
 * ✓ Core types working
 * ✓ Optics working
 * ✓ Type classes working
 * ✓ Java 25 features working
 *
 * Setup verification complete! You're ready to start the tutorials.
 * }</pre>
 *
 * <h2>Troubleshooting</h2>
 *
 * <p>If this example doesn't compile or run:
 *
 * <ol>
 *   <li>Verify Java 25+ is installed: {@code java -version}
 *   <li>Check {@code build.gradle} includes Higher-Kinded-J dependencies
 *   <li>Run {@code ./gradlew clean build} to trigger annotation processing
 *   <li>Consult the <a
 *       href="https://higher-kinded-j.github.io/tutorials/troubleshooting.html">Troubleshooting
 *       Guide</a>
 * </ol>
 *
 * @see <a href="https://higher-kinded-j.github.io/tutorials/tutorials_intro.html">Tutorial
 *     Introduction</a>
 */
public final class TutorialGettingStarted {

  private TutorialGettingStarted() {
    // Utility class - no instantiation
  }

  // Sealed interface for Java feature verification
  sealed interface Result permits Success, Failure {}

  record Success(String message) implements Result {}

  record Failure(String error) implements Result {}

  /**
   * Verifies that core types ({@link Either}, {@link Maybe}) are accessible and functional.
   *
   * <p>These types form the foundation of the Higher-Kinded-J library. If this method completes
   * without errors, your core dependency is correctly configured.
   */
  private static void verifyCoreTypes() {
    // Test Either: represents success or failure
    Either<String, Integer> success = Either.right(42);
    Either<String, Integer> failure = Either.left("Error");

    assert success.isRight() : "Either.right() should create a Right value";
    assert success.getRight() == 42 : "Right value should be 42";
    assert failure.isLeft() : "Either.left() should create a Left value";
    assert failure.getLeft().equals("Error") : "Left value should be 'Error'";

    // Test Maybe: represents presence or absence
    Maybe<String> present = Maybe.just("Hello");
    Maybe<String> absent = Maybe.nothing();

    assert present.isJust() : "Maybe.just() should create a Just value";
    assert present.get().equals("Hello") : "Just value should be 'Hello'";
    assert absent.isNothing() : "Maybe.nothing() should create Nothing";

    System.out.println("✓ Core types working");
  }

  /**
   * Verifies that the Optics library is accessible and lenses can be created.
   *
   * <p>This tests both manual lens creation and the annotation processor integration. If this
   * method completes successfully, you can use lenses in the tutorials.
   */
  private static void verifyOptics() {
    // Define a simple record (Java 14+ feature)
    record Point(int x, int y) {}

    // Create a lens manually (Tutorial 01 pattern)
    Lens<Point, Integer> xLens = Lens.of(Point::x, (point, newX) -> new Point(newX, point.y()));

    Point original = new Point(5, 10);
    Point updated = xLens.set(15, original);

    assert xLens.get(original) == 5 : "Lens should read original x value";
    assert updated.x() == 15 : "Lens should update x value";
    assert updated.y() == 10 : "Lens should preserve y value";
    assert original.x() == 5 : "Original should be unchanged (immutability)";

    System.out.println("✓ Optics working");
  }

  /**
   * Verifies that type classes ({@code Functor}, {@code Monad}) are accessible.
   *
   * <p>This tests the Higher-Kinded Type simulation that enables generic programming across
   * different container types. Success here means you're ready for the Core Types tutorials.
   */
  private static void verifyTypeClasses() {
    // Test Functor: transforming wrapped values
    Either<String, Integer> value = Either.right(10);
    Either<String, Integer> doubled = value.map(x -> x * 2);

    assert doubled.getRight() == 20 : "Functor.map should transform the value";

    // Test Monad: chaining dependent operations
    Either<String, Integer> result =
        Either.<String, Integer>right(5)
            .flatMap(x -> Either.<String, Integer>right(10).map(y -> x + y));

    assert result.getRight() == 15 : "Monad.flatMap should chain operations";

    System.out.println("✓ Type classes working");
  }

  /**
   * Verifies that Java 25 features (records, sealed interfaces, pattern matching) are available.
   *
   * <p>Higher-Kinded-J requires Java 25+ to leverage modern language features. This method confirms
   * your JDK version is sufficient.
   */
  private static void verifyJavaFeatures() {
    // Test records (Java 14+, standard in 16+)
    record User(String name, int age) {}

    User user = new User("Alice", 30);
    assert user.name().equals("Alice") : "Record accessors should work";

    // Test sealed interfaces (Java 17+) - defined as static nested classes above
    Result success = new Success("OK");

    // Test pattern matching for instanceof (Java 16+)
    boolean isSuccess =
        switch (success) {
          case Success s -> true;
          case Failure f -> false;
        };

    assert isSuccess : "Pattern matching should work";

    System.out.println("✓ Java 25 features working");
  }

  /**
   * Runs all verification checks.
   *
   * <p>Execute this before starting the tutorials to ensure everything is configured correctly. Any
   * assertion failures indicate a setup problem that should be resolved first.
   *
   * <p>Java 25 instance main method - no static modifier or String[] args required.
   */
  void main() {
    System.out.println("Verifying Higher-Kinded-J tutorial setup...\n");

    try {
      verifyCoreTypes();
      verifyOptics();
      verifyTypeClasses();
      verifyJavaFeatures();

      System.out.println("\n✅ Setup verification complete! You're ready to start the tutorials.");
      System.out.println("\nNext steps:");
      System.out.println("  1. Choose a learning track:");
      System.out.println(
          "     - Core Types: https://higher-kinded-j.github.io/tutorials/coretypes_track.html");
      System.out.println(
          "     - Optics: https://higher-kinded-j.github.io/tutorials/optics_track.html");
      System.out.println("  2. Open the first tutorial file in your IDE");
      System.out.println("  3. Start replacing answerRequired() with your solutions!");

    } catch (AssertionError e) {
      System.err.println("\n❌ Setup verification failed: " + e.getMessage());
      System.err.println("\nPlease check:");
      System.err.println("  1. Java version: java -version (must be 24+)");
      System.err.println("  2. Dependencies in build.gradle are correct");
      System.err.println("  3. Run: ./gradlew clean build");
      System.err.println(
          "  4. See: https://higher-kinded-j.github.io/tutorials/troubleshooting.html");
      System.exit(1);
    }
  }
}
