// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.maybe.Maybe;
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
 * <p>When to use: - Sealed interfaces with multiple implementations - Either (Left vs Right
 * prisms) - Validated (Valid vs Invalid prisms) - Any sum type where you need to focus on one case
 */
public class Tutorial03_PrismBasics {

  @GeneratePrisms
  sealed interface Shape {}

  record Circle(double radius) implements Shape {}

  record Rectangle(double width, double height) implements Shape {}

  record Triangle(double base, double height) implements Shape {}

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
    Maybe<Circle> extracted = null;

    assertThat(extracted.isJust()).isTrue();
    assertThat(extracted.get().radius()).isEqualTo(5.0);

    // Prism fails when shape doesn't match
    Maybe<Circle> notACircle = circlePrism.getOptional(rectangle);
    assertThat(notACircle.isNothing()).isTrue();
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
    Shape shape = null;

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
    Shape doubledCircle = null;

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

    // TODO: Replace null with code that calculates area using prisms
    // Try each prism and calculate the appropriate area
    double area =
        circlePrism
            .getOptional(shape)
            .map(c -> Math.PI * c.radius() * c.radius())
            .orElse(() -> rectanglePrism.getOptional(shape).map(r -> r.width() * r.height()))
            .orElse(() -> trianglePrism.getOptional(shape).map(t -> 0.5 * t.base() * t.height()))
            .getOrElse(0.0);

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
    @GeneratePrisms
    sealed interface JsonValue {}

    record JsonString(String value) implements JsonValue {}

    record JsonNumber(double value) implements JsonValue {}

    JsonValue stringValue = new JsonString("Hello");

    Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

    // TODO: Replace null with code that:
    // 1. Uses the prism to get the JsonString
    // 2. Maps over it to extract the value field
    // Hint: stringPrism.getOptional(stringValue).map(js -> js.value())
    Maybe<String> value = null;

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
    @GeneratePrisms
    sealed interface JsonValue {}

    record JsonString(String value) implements JsonValue {}

    record JsonNumber(double value) implements JsonValue {}

    JsonValue string1 = new JsonString("hello");
    JsonValue string2 = new JsonString("world");
    JsonValue number = new JsonNumber(42.0);

    Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

    // TODO: Replace null with code that uppercases the strings
    // Use stringPrism.modify() to transform matching values
    JsonValue updated1 = stringPrism.modify(js -> new JsonString(js.value().toUpperCase()), string1);
    JsonValue updated2 = null;
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

    // TODO: Replace null with code that checks if shapes are circles
    // Hint: circlePrism.matches(shape)
    boolean isCircle1 = null;
    boolean isCircle2 = null;
    boolean isCircle3 = null;

    assertThat(isCircle1).isTrue();
    assertThat(isCircle2).isFalse();
    assertThat(isCircle3).isTrue();
  }

  /**
   * Congratulations! You've completed Tutorial 03: Prism Basics
   *
   * <p>You now understand: ✓ How to use getOptional to safely extract values ✓ How to use build to
   * construct sum type values ✓ How to use modify to conditionally update values ✓ How to pattern
   * match with multiple prisms ✓ How to compose prisms with lenses ✓ How to use matches for type
   * checking ✓ When to use prisms (sum types, sealed interfaces)
   *
   * <p>Next: Tutorial 04 - Traversal Basics
   */
}
