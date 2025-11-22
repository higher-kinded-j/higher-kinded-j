// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
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
public class Tutorial03_PrismBasics_Solution {

  @GeneratePrisms
  sealed interface Shape {}

  record Circle(double radius) implements Shape {}

  record Rectangle(double width, double height) implements Shape {}

  record Triangle(double base, double height) implements Shape {}

  // Manual prism implementations (annotation processor will generate these in real projects)
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

  // JsonValue sealed interface for exercises 5-6
  @GeneratePrisms
  sealed interface JsonValue {}

  record JsonString(String value) implements JsonValue {}

  record JsonNumber(double value) implements JsonValue {}

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

    // SOLUTION: Use circlePrism.getOptional() to extract the circle
    Optional<Circle> extracted = circlePrism.getOptional(circle);

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

    // SOLUTION: Use circlePrism.build() to create a Shape from a Circle
    Shape shape = circlePrism.build(circle);

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

    // SOLUTION: Use circlePrism.modify() to double the circle's radius
    Shape doubledCircle = circlePrism.modify(c -> new Circle(c.radius() * 2), circle);

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

    // SOLUTION: Calculate area using prisms
    // Try each prism and calculate the appropriate area
    double area =
        circlePrism
            .getOptional(shape)
            .map(c -> Math.PI * c.radius() * c.radius())
            .or(() -> rectanglePrism.getOptional(shape).map(r -> r.width() * r.height()))
            .or(() -> trianglePrism.getOptional(shape).map(t -> 0.5 * t.base() * t.height()))
            .orElse(0.0);

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

    // SOLUTION: Use the prism to get the JsonString and extract the value field
    Optional<String> value = stringPrism.getOptional(stringValue).map(js -> js.value());

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

    // SOLUTION: Use stringPrism.modify() to uppercase the strings
    JsonValue updated1 =
        stringPrism.modify(js -> new JsonString(js.value().toUpperCase()), string1);
    JsonValue updated2 =
        stringPrism.modify(js -> new JsonString(js.value().toUpperCase()), string2);
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

    // SOLUTION: Use circlePrism.matches() to check if shapes are circles
    boolean isCircle1 = circlePrism.matches(shape1);
    boolean isCircle2 = circlePrism.matches(shape2);
    boolean isCircle3 = circlePrism.matches(shape3);

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
