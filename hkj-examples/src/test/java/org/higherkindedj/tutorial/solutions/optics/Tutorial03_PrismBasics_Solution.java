// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.util.Prisms;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial03 PrismBasics — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
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
   * Why this is idiomatic: a prism's read operation is partial by design — {@code getOptional}
   * returns {@code Optional<Circle>} so the caller cannot accidentally treat a {@code Rectangle} as
   * a {@code Circle}. The two test branches (present, empty) prove the totality of the result.
   *
   * <p>Alternative: pattern-match the {@code Shape} with {@code if (s instanceof Circle c)}. The
   * code is the same shape — the prism just promotes that pattern into a value you can pass around,
   * store, and compose.
   *
   * <p>Common wrong attempt: cast unconditionally with {@code (Circle) shape} and rely on the
   * {@code ClassCastException} to indicate "not a circle". Exceptions for routine flow are exactly
   * what {@code Optional} replaces.
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
   * Why this is idiomatic: {@code build} is total — given a {@code Circle}, you always get back a
   * {@code Shape}. It is the symmetric partner of {@code getOptional}: read may fail, write cannot.
   *
   * <p>Alternative: {@code Shape s = circle;} — the upcast is implicit because {@code Circle}
   * implements {@code Shape}. {@code build} is preferred when the prism is being passed around as
   * data; the implicit upcast is fine for one-off code.
   *
   * <p>Common wrong attempt: thinking {@code build} can fail and writing {@code
   * circlePrism.build(circle).orElseThrow(...)}. {@code build} returns the outer type directly —
   * there is nothing to unwrap.
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
   * Why this is idiomatic: {@code prism.modify} is "apply this function if the variant matches,
   * otherwise pass through unchanged". One call expresses both the conditional and the
   * transformation; the {@code isSameAs} assertion proves the no-match branch is identity.
   *
   * <p>Alternative: an explicit {@code switch} or {@code instanceof} block returning the modified
   * or original shape. Same outcome; the prism factors the boilerplate into a combinator that
   * composes with other optics.
   *
   * <p>Common wrong attempt: write {@code modify} with side effects (logging the transformation,
   * caching results). Prism modify expects a pure function; surprise side effects fire on every
   * match and become very confusing inside a larger composition.
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
   * Why this is idiomatic: chain {@code Optional.or(...)} across each prism — the first one that
   * matches wins, the rest are skipped. The sum-type dispatch reads as a single expression with one
   * terminal {@code orElse}.
   *
   * <p>Alternative: a Java {@code switch} expression on the sealed interface ({@code switch (shape)
   * { case Circle c -> ... }}). Just as good in modern Java; the prism form is preferable when the
   * dispatch needs to be passed as data or composed with optics elsewhere.
   *
   * <p>Common wrong attempt: nest {@code if (circlePrism.matches(...)) ... else if ...} blocks and
   * re-extract with a cast. Works once, but every cast duplicates the {@code matches} check the
   * prism already performed; let {@code getOptional} carry the typed value through.
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
   * Why this is idiomatic: {@code stringPrism.getOptional(...).map(...)} is the prism-then-lens
   * shape — partial read into the variant, total read of the field. The result stays {@code
   * Optional<String>} so the absent-variant case is impossible to forget.
   *
   * <p>Alternative: a real {@code prism.andThen(lens)} composition that returns an {@code
   * Affine<JsonValue, String>}. Equivalent semantics; reach for the named composition once the same
   * path is needed in two or more places.
   *
   * <p>Common wrong attempt: call {@code stringPrism.getOptional(jv).get().value()} without an
   * {@code ifPresent}/{@code map}. The {@code get()} on an empty optional throws — the {@code map}
   * keeps the absence handling in the type.
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
   * Why this is idiomatic: one prism modifier handles every value; matching variants get
   * transformed, non-matching variants pass through untouched. The {@code isSameAs(number)}
   * assertion on the {@code JsonNumber} input proves the no-op branch is identity.
   *
   * <p>Alternative: a {@code stream().map(jv -> switch (jv) { case JsonString js -> ... default ->
   * jv; })} pipeline. Equally readable; the prism form is mechanical (no manual {@code default}
   * arm) and stays in sync when a new variant is added.
   *
   * <p>Common wrong attempt: forget to wrap the result in a fresh {@code JsonString} and return the
   * bare uppercased {@code String} from the modify lambda. The lambda's required return type is
   * {@code JsonString}, so it fails to compile — but only after the test catches the intent
   * mismatch.
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
   * Why this is idiomatic: {@code matches} is the cheap predicate — yes/no without the {@code
   * Optional} allocation. Useful when you only need the boolean answer (filter, count, branch) and
   * not the extracted value.
   *
   * <p>Alternative: {@code instanceof Circle}. Same answer; reach for {@code matches} when the
   * predicate needs to be passed to a higher-order function (a {@code Stream.filter}, a custom
   * combinator) where the prism reference is what gets stored.
   *
   * <p>Common wrong attempt: writing {@code circlePrism.getOptional(s).isPresent()} when only a
   * boolean is needed. {@code matches} is the named, allocation-free spelling.
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
   * Why this is idiomatic: {@code circlePrism::doesNotMatch} reads as "everything except a circle",
   * and as a method reference it slots straight into {@code Stream.filter}. Negation lives in the
   * prism, not in a stray lambda.
   *
   * <p>Alternative: {@code .filter(s -> !circlePrism.matches(s))}. Equivalent; the named {@code
   * doesNotMatch} is the smaller, more discoverable spelling.
   *
   * <p>Common wrong attempt: writing {@code .filter(s -> !(s instanceof Circle))} and bypassing the
   * prism entirely. Now the filter no longer participates in optic refactors — change the prism's
   * predicate and the manual filter silently goes out of sync.
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

    // SOLUTION: Use doesNotMatch to filter out circles
    List<Shape> nonCircles =
        shapes.stream().filter(circlePrism::doesNotMatch).collect(Collectors.toList());

    assertThat(nonCircles).hasSize(3);
    assertThat(nonCircles).noneMatch(s -> s instanceof Circle);
    assertThat(nonCircles).allMatch(s -> s instanceof Rectangle || s instanceof Triangle);
  }

  /**
   * Why this is idiomatic: {@code Prisms.nearly(default, predicate)} promotes any predicate to a
   * prism — the read side asks "does it satisfy?", the build side returns the supplied default. It
   * is the bridge between a value-level predicate and an optic the rest of the API speaks.
   *
   * <p>Alternative: {@code Prisms.only(value)} when the match is a single specific value. Use
   * {@code nearly} for categories ("positive", "non-empty", "even"), {@code only} for exact
   * sentinels.
   *
   * <p>Common wrong attempt: forget that {@code build(Unit.INSTANCE)} returns the supplied default
   * and assume it can be reverse-engineered from the predicate. The predicate is one-way; the
   * default is what the prism produces when asked to construct.
   */
  @Test
  void exercise9_usingNearlyPrism() {
    // SOLUTION: Create a nearly prism that matches positive integers
    Prism<Integer, Unit> positivePrism = Prisms.nearly(1, n -> n > 0);

    List<Integer> numbers = List.of(-5, 0, 3, -2, 7, 10, -1);

    // SOLUTION: Filter to get only positive numbers using matches
    List<Integer> positives =
        numbers.stream().filter(positivePrism::matches).collect(Collectors.toList());

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
   * <p>Next: Tutorial 04 - Traversal Basics
   */
}
