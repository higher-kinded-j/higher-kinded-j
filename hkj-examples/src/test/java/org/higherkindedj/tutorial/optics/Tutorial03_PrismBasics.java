// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 03: Prism Basics - Working with Sum Types
 *
 * <p>A Prism is an optic for working with sum types (sealed interfaces, Either, etc.). Unlike a
 * Lens which always succeeds, a Prism might fail because the value might not match the expected
 * case.
 *
 * <p>Key Concepts: - getOptional: tries to extract a value, returns Maybe - build: constructs a
 * value from the inner type - modify: updates the value if it matches the case - Pattern matching:
 * type-safe access to sealed interface variants
 *
 * <p>When to use: - Sealed interfaces with multiple implementations - Either (Left vs Right prisms)
 * - Validated (Valid vs Invalid prisms) - Any sum type where you need to focus on one case
 */
public class Tutorial03_PrismBasics {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /*
   * ========================================================================
   * IMPORTANT: Manual Prism Implementation (For Educational Purposes Only)
   * ========================================================================
   *
   * In this tutorial, we manually create prisms to help you understand sum type optics.
   * This is ONLY for learning - in real projects, NEVER write these manually!
   *
   * What you should do in real projects:
   * ────────────────────────────────────────────────────────────────────────
   * 1. Annotate your sealed interfaces with @GeneratePrisms
   * 2. The annotation processor automatically generates prisms for each case
   * 3. Use the generated prisms from companion classes (e.g., ShapePrisms.circle())
   *
   * Example of real-world usage:
   *
   *   @GeneratePrisms
   *   sealed interface Shape {}
   *   record Circle(double radius) implements Shape {}
   *   record Rectangle(double width, double height) implements Shape {}
   *
   *   // The processor generates:
   *   // - ShapePrisms.circle()     -> Prism<Shape, Circle>
   *   // - ShapePrisms.rectangle()  -> Prism<Shape, Rectangle>
   *
   *   // Usage:
   *   Optional<Circle> maybeCircle = ShapePrisms.circle().getOptional(shape);
   *
   * Why we show manual implementations here:
   * ────────────────────────────────────────────────────────────────────────
   * - Understanding how Prisms work with pattern matching helps demystify sum types
   * - You'll appreciate the annotation processor's type-safe case handling
   * - Helpful for debugging or when you need custom prisms for special cases
   */

  @GeneratePrisms
  sealed interface Shape {}

  record Circle(double radius) implements Shape {}

  record Rectangle(double width, double height) implements Shape {}

  record Triangle(double base, double height) implements Shape {}

  // Manual prism implementations (simulating what @GeneratePrisms creates - FOR LEARNING ONLY)
  static class ShapePrisms {
    public static Prism<Shape, Circle> circle() {
      return Prism.of(s -> s instanceof Circle c ? Optional.of(c) : Optional.empty(), c -> c);
    }

    public static Prism<Shape, Rectangle> rectangle() {
      return Prism.of(s -> s instanceof Rectangle r ? Optional.of(r) : Optional.empty(), r -> r);
    }

    public static Prism<Shape, Triangle> triangle() {
      return Prism.of(s -> s instanceof Triangle t ? Optional.of(t) : Optional.empty(), t -> t);
    }
  }

  // JsonValue sealed interface for exercises 5-6 (also with manual prisms for learning)
  @GeneratePrisms
  sealed interface JsonValue {}

  record JsonString(String value) implements JsonValue {}

  record JsonNumber(double value) implements JsonValue {}

  // Manual prism implementations (simulating what @GeneratePrisms creates - FOR LEARNING ONLY)
  static class JsonValuePrisms {
    public static Prism<JsonValue, JsonString> jsonString() {
      return Prism.of(
          jv -> jv instanceof JsonString js ? Optional.of(js) : Optional.empty(), js -> js);
    }

    public static Prism<JsonValue, JsonNumber> jsonNumber() {
      return Prism.of(
          jv -> jv instanceof JsonNumber jn ? Optional.of(jn) : Optional.empty(), jn -> jn);
    }
  }

  /**
   * Exercise 1: Getting a value with a Prism
   *
   * <p>getOptional tries to extract the value if it matches the expected case.
   *
   * <p>Task: Use a prism to extract a Circle from a Shape
   */
  @Test
  void exercise1_gettingWithPrism() {
    Shape circle = new Circle(5.0);
    Shape rectangle = new Rectangle(10.0, 20.0);

    Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

    // TODO: Replace null with code that uses circlePrism.getOptional()
    // to extract the circle
    Optional<Circle> extracted = answerRequired();

    assertThat(extracted.isPresent()).isTrue();
    assertThat(extracted.get().radius()).isEqualTo(5.0);

    // Prism fails when shape doesn't match
    Optional<Circle> notACircle = circlePrism.getOptional(rectangle);
    assertThat(notACircle.isEmpty()).isTrue();
  }

  /**
   * Exercise 2: Building a value with a Prism
   *
   * <p>build (also called reverseGet) constructs the outer type from the inner type.
   *
   * <p>Task: Build a Shape from a Circle
   */
  @Test
  void exercise2_buildingWithPrism() {
    Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

    Circle circle = new Circle(7.5);

    // TODO: Replace null with code that uses circlePrism.build()
    // to create a Shape from a Circle
    // Hint: circlePrism.build(circle)
    Shape shape = answerRequired();

    assertThat(shape).isInstanceOf(Circle.class);
    assertThat(((Circle) shape).radius()).isEqualTo(7.5);
  }

  /**
   * Exercise 3: Modifying with a Prism
   *
   * <p>modify applies a function, but only if the value matches the prism's case.
   *
   * <p>Task: Double the radius of circles, leave other shapes unchanged
   */
  @Test
  void exercise3_modifyingWithPrism() {
    Shape circle = new Circle(5.0);
    Shape rectangle = new Rectangle(10.0, 20.0);

    Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

    // TODO: Replace null with code that uses circlePrism.modify()
    // to double the circle's radius
    // Hint: circlePrism.modify(c -> new Circle(c.radius() * 2), circle)
    Shape doubledCircle = answerRequired();

    assertThat(((Circle) doubledCircle).radius()).isEqualTo(10.0);

    // Modify does nothing if the shape doesn't match
    Shape unchangedRectangle = circlePrism.modify(c -> new Circle(c.radius() * 2), rectangle);
    assertThat(unchangedRectangle).isSameAs(rectangle); // Unchanged
  }

  /**
   * Exercise 4: Pattern matching with multiple prisms
   *
   * <p>Use different prisms to handle different cases of a sum type.
   *
   * <p>Task: Calculate area for different shapes using prisms
   */
  @Test
  void exercise4_patternMatching() {
    Prism<Shape, Circle> circlePrism = ShapePrisms.circle();
    Prism<Shape, Rectangle> rectanglePrism = ShapePrisms.rectangle();
    Prism<Shape, Triangle> trianglePrism = ShapePrisms.triangle();

    Shape shape = new Rectangle(5.0, 10.0);

    // TODO: Replace 0.0 with code that calculates area using prisms
    // Try each prism and calculate the appropriate area
    // Hint: Use if-else with circlePrism.getOptional(shape).isPresent()
    // Or use circlePrism.getOptional(shape).map(...).orElse(value)
    double area = answerRequired();

    assertThat(area).isEqualTo(50.0); // 5 * 10
  }

  /**
   * Exercise 5: Composing Prisms with Lenses
   *
   * <p>You can compose a Prism with a Lens to access nested fields within a sum type variant.
   *
   * <p>Task: Access the radius of a circle through composition
   */
  @Test
  void exercise5_composingPrismWithLens() {
    JsonValue stringValue = new JsonString("Hello");

    Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

    // TODO: Replace null with code that:
    // 1. Uses the prism to get the JsonString
    // 2. Maps over it to extract the value field
    // Hint: stringPrism.getOptional(stringValue).map(js -> js.value())
    Optional<String> value = answerRequired();

    assertThat(value.get()).isEqualTo("Hello");
  }

  /**
   * Exercise 6: Conditional updates
   *
   * <p>Prisms are great for conditionally updating specific variants.
   *
   * <p>Task: Uppercase all JsonString values in a collection
   */
  @Test
  void exercise6_conditionalUpdates() {
    JsonValue string1 = new JsonString("hello");
    JsonValue string2 = new JsonString("world");
    JsonValue number = new JsonNumber(42.0);

    Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

    // TODO: Replace null with code that uppercases the strings
    // Use stringPrism.modify() to transform matching values
    JsonValue updated1 =
        stringPrism.modify(js -> new JsonString(js.value().toUpperCase()), string1);
    JsonValue updated2 = answerRequired();
    JsonValue updated3 = stringPrism.modify(js -> new JsonString(js.value().toUpperCase()), number);

    assertThat(((JsonString) updated1).value()).isEqualTo("HELLO");
    assertThat(((JsonString) updated2).value()).isEqualTo("WORLD");
    assertThat(updated3).isSameAs(number); // Number unchanged
  }

  /**
   * Exercise 7: Using matches for type checking
   *
   * <p>Prisms provide a matches() method to check if a value is of the expected case.
   *
   * <p>Task: Filter shapes to find all circles
   */
  @Test
  void exercise7_usingMatches() {
    Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

    Shape shape1 = new Circle(5.0);
    Shape shape2 = new Rectangle(10.0, 20.0);
    Shape shape3 = new Circle(3.0);

    // TODO: Replace false with code that checks if shapes are circles
    // Hint: circlePrism.matches(shape)
    boolean isCircle1 = answerRequired();
    boolean isCircle2 = answerRequired();
    boolean isCircle3 = answerRequired();

    assertThat(isCircle1).isTrue();
    assertThat(isCircle2).isFalse();
    assertThat(isCircle3).isTrue();
  }

  /**
   * Exercise 8: Using doesNotMatch for exclusion filtering
   *
   * <p>The doesNotMatch method is the logical negation of matches(). It's useful for filtering out
   * values that don't match a prism.
   *
   * <p>Task: Filter a list to get all non-circle shapes
   */
  @Test
  void exercise8_usingDoesNotMatch() {
    Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

    List<Shape> shapes =
        List.of(
            new Circle(5.0),
            new Rectangle(10.0, 20.0),
            new Circle(3.0),
            new Triangle(6.0, 8.0),
            new Rectangle(2.0, 4.0));

    // TODO: Replace null with code that filters to get non-circle shapes
    // Hint: shapes.stream().filter(circlePrism::doesNotMatch).collect(...)
    List<Shape> nonCircles = answerRequired();

    assertThat(nonCircles).hasSize(3);
    assertThat(nonCircles).noneMatch(s -> s instanceof Circle);
    assertThat(nonCircles).allMatch(s -> s instanceof Rectangle || s instanceof Triangle);
  }

  /**
   * Exercise 9: Using the nearly prism for predicate-based matching
   *
   * <p>The nearly prism matches values that satisfy a predicate. Unlike 'only' which matches exact
   * values, 'nearly' matches categories of values.
   *
   * <p>Task: Create a prism that matches positive numbers and use it to filter a list
   */
  @Test
  void exercise9_usingNearlyPrism() {
    // TODO: Replace null with a nearly prism that matches positive integers
    // Hint: Prisms.nearly(defaultValue, predicate)
    // The default value is used when building - use 1 as the default
    Prism<Integer, Unit> positivePrism = answerRequired();

    List<Integer> numbers = List.of(-5, 0, 3, -2, 7, 10, -1);

    // TODO: Replace null with code that filters to get only positive numbers
    // Hint: numbers.stream().filter(positivePrism::matches).collect(...)
    List<Integer> positives = answerRequired();

    assertThat(positives).containsExactly(3, 7, 10);

    // Verify the build method returns the default value
    Integer builtValue = positivePrism.build(Unit.INSTANCE);
    assertThat(builtValue).isEqualTo(1);
  }

  /**
   * Congratulations! You've completed Tutorial 03: Prism Basics
   *
   * <p>You now understand: ✓ How to use getOptional to safely extract values ✓ How to use build to
   * construct sum type values ✓ How to use modify to conditionally update values ✓ How to pattern
   * match with multiple prisms ✓ How to compose prisms with lenses ✓ How to use matches for type
   * checking ✓ How to use doesNotMatch for exclusion filtering ✓ How to use nearly for
   * predicate-based matching ✓ When to use prisms (sum types, sealed interfaces)
   *
   * <p>Next: Tutorial 04 - Affine Basics
   */
}
