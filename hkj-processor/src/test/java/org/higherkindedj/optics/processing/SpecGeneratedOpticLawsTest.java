// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Verifies that optics generated from spec interfaces satisfy their mathematical laws.
 *
 * <p>This test class uses runtime compilation to generate optics from spec interfaces with copy
 * strategies, then verifies the generated implementations satisfy the required laws.
 *
 * <h3>Lens Laws</h3>
 *
 * <ul>
 *   <li><b>Get-Put</b>: {@code set(get(s), s) == s} - Setting what you get doesn't change anything
 *   <li><b>Put-Get</b>: {@code get(set(a, s)) == a} - Getting what you set returns what you set
 *   <li><b>Put-Put</b>: {@code set(b, set(a, s)) == set(b, s)} - Second set wins
 * </ul>
 *
 * <h3>Prism Laws</h3>
 *
 * <ul>
 *   <li><b>Review-Preview</b>: {@code getOptional(build(a)) == Optional.of(a)}
 *   <li><b>Preview-Review</b>: {@code getOptional(s).map(this::build).orElse(s) == s}
 * </ul>
 */
@DisplayName("Spec Generated Optics Law Verification")
class SpecGeneratedOpticLawsTest {

  // ==================== TEST SOURCES ====================

  // External class with builder pattern for @ViaBuilder testing
  private static final JavaFileObject EXTERNAL_PERSON =
      JavaFileObjects.forSourceString(
          "com.external.Person",
          """
          package com.external;

          public final class Person {
              private final String name;
              private final int age;

              public Person(String name, int age) {
                  this.name = name;
                  this.age = age;
              }

              private Person(Builder builder) {
                  this.name = builder.name;
                  this.age = builder.age;
              }

              public String name() { return name; }
              public int age() { return age; }

              public Builder toBuilder() {
                  return new Builder().name(name).age(age);
              }

              public static Builder builder() { return new Builder(); }

              public static class Builder {
                  private String name;
                  private int age;

                  public Builder name(String name) { this.name = name; return this; }
                  public Builder age(int age) { this.age = age; return this; }
                  public Person build() { return new Person(this); }
              }

              @Override
              public boolean equals(Object o) {
                  if (this == o) return true;
                  if (!(o instanceof Person p)) return false;
                  return age == p.age && name.equals(p.name);
              }

              @Override
              public int hashCode() {
                  return 31 * name.hashCode() + age;
              }
          }
          """);

  // External class with wither methods for @Wither testing
  private static final JavaFileObject EXTERNAL_POINT =
      JavaFileObjects.forSourceString(
          "com.external.Point",
          """
          package com.external;

          public final class Point {
              private final int x;
              private final int y;

              public Point(int x, int y) {
                  this.x = x;
                  this.y = y;
              }

              public int getX() { return x; }
              public int getY() { return y; }

              public Point withX(int x) {
                  return new Point(x, y);
              }

              public Point withY(int y) {
                  return new Point(x, y);
              }

              @Override
              public boolean equals(Object o) {
                  if (this == o) return true;
                  if (!(o instanceof Point p)) return false;
                  return x == p.x && y == p.y;
              }

              @Override
              public int hashCode() {
                  return 31 * x + y;
              }
          }
          """);

  // External sealed interface hierarchy for @InstanceOf prism testing
  private static final JavaFileObject EXTERNAL_SHAPE =
      JavaFileObjects.forSourceString(
          "com.external.Shape",
          """
          package com.external;

          public sealed interface Shape permits Circle, Rectangle {}
          """);

  private static final JavaFileObject EXTERNAL_CIRCLE =
      JavaFileObjects.forSourceString(
          "com.external.Circle",
          """
          package com.external;

          public record Circle(double radius) implements Shape {}
          """);

  private static final JavaFileObject EXTERNAL_RECTANGLE =
      JavaFileObjects.forSourceString(
          "com.external.Rectangle",
          """
          package com.external;

          public record Rectangle(double width, double height) implements Shape {}
          """);

  // External class hierarchy for @MatchWhen prism testing
  // Note: Using non-generic version for simpler reflection testing
  private static final JavaFileObject EXTERNAL_RESULT =
      JavaFileObjects.forSourceString(
          "com.external.Result",
          """
          package com.external;

          public abstract class Result {
              public abstract boolean isSuccess();
              public abstract boolean isFailure();
              public Success asSuccess() { throw new IllegalStateException(); }
              public Failure asFailure() { throw new IllegalStateException(); }
          }
          """);

  private static final JavaFileObject EXTERNAL_SUCCESS =
      JavaFileObjects.forSourceString(
          "com.external.Success",
          """
          package com.external;

          public final class Success extends Result {
              private final String value;

              public Success(String value) { this.value = value; }
              public String getValue() { return value; }

              @Override public boolean isSuccess() { return true; }
              @Override public boolean isFailure() { return false; }
              @Override public Success asSuccess() { return this; }

              @Override
              public boolean equals(Object o) {
                  if (this == o) return true;
                  if (!(o instanceof Success s)) return false;
                  return value.equals(s.value);
              }

              @Override
              public int hashCode() {
                  return value.hashCode();
              }
          }
          """);

  private static final JavaFileObject EXTERNAL_FAILURE =
      JavaFileObjects.forSourceString(
          "com.external.Failure",
          """
          package com.external;

          public final class Failure extends Result {
              private final String error;

              public Failure(String error) { this.error = error; }
              public String getError() { return error; }

              @Override public boolean isSuccess() { return false; }
              @Override public boolean isFailure() { return true; }
              @Override public Failure asFailure() { return this; }

              @Override
              public boolean equals(Object o) {
                  if (this == o) return true;
                  if (!(o instanceof Failure f)) return false;
                  return error.equals(f.error);
              }

              @Override
              public int hashCode() {
                  return error.hashCode();
              }
          }
          """);

  // Spec interface using @ViaBuilder copy strategy
  private static final JavaFileObject PERSON_SPEC =
      JavaFileObjects.forSourceString(
          "com.test.PersonSpec",
          """
          package com.test;

          import org.higherkindedj.optics.Lens;
          import org.higherkindedj.optics.annotations.ImportOptics;
          import org.higherkindedj.optics.annotations.OpticsSpec;
          import org.higherkindedj.optics.annotations.ViaBuilder;
          import com.external.Person;

          @ImportOptics
          public interface PersonSpec extends OpticsSpec<Person> {

              @ViaBuilder
              Lens<Person, String> name();

              @ViaBuilder
              Lens<Person, Integer> age();
          }
          """);

  // Spec interface using @Wither copy strategy
  private static final JavaFileObject POINT_SPEC =
      JavaFileObjects.forSourceString(
          "com.test.PointSpec",
          """
          package com.test;

          import org.higherkindedj.optics.Lens;
          import org.higherkindedj.optics.annotations.ImportOptics;
          import org.higherkindedj.optics.annotations.OpticsSpec;
          import org.higherkindedj.optics.annotations.Wither;
          import com.external.Point;

          @ImportOptics
          public interface PointSpec extends OpticsSpec<Point> {

              @Wither(value = "withX", getter = "getX")
              Lens<Point, Integer> x();

              @Wither(value = "withY", getter = "getY")
              Lens<Point, Integer> y();
          }
          """);

  // Spec interface using @InstanceOf prism hint
  private static final JavaFileObject SHAPE_SPEC =
      JavaFileObjects.forSourceString(
          "com.test.ShapeSpec",
          """
          package com.test;

          import org.higherkindedj.optics.Prism;
          import org.higherkindedj.optics.annotations.ImportOptics;
          import org.higherkindedj.optics.annotations.OpticsSpec;
          import org.higherkindedj.optics.annotations.InstanceOf;
          import com.external.Shape;
          import com.external.Circle;
          import com.external.Rectangle;

          @ImportOptics
          public interface ShapeSpec extends OpticsSpec<Shape> {

              @InstanceOf(Circle.class)
              Prism<Shape, Circle> circle();

              @InstanceOf(Rectangle.class)
              Prism<Shape, Rectangle> rectangle();
          }
          """);

  // Spec interface using @MatchWhen prism hint
  private static final JavaFileObject RESULT_SPEC =
      JavaFileObjects.forSourceString(
          "com.test.ResultSpec",
          """
          package com.test;

          import org.higherkindedj.optics.Prism;
          import org.higherkindedj.optics.annotations.ImportOptics;
          import org.higherkindedj.optics.annotations.OpticsSpec;
          import org.higherkindedj.optics.annotations.MatchWhen;
          import com.external.Result;
          import com.external.Success;
          import com.external.Failure;

          @ImportOptics
          public interface ResultSpec extends OpticsSpec<Result> {

              @MatchWhen(predicate = "isSuccess", getter = "asSuccess")
              Prism<Result, Success> success();

              @MatchWhen(predicate = "isFailure", getter = "asFailure")
              Prism<Result, Failure> failure();
          }
          """);

  // ==================== COMPILED RESULTS ====================

  private static SpecCompiledResult compiled;

  @BeforeAll
  static void compileTestTypes() {
    Compilation compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .compile(
                EXTERNAL_PERSON,
                EXTERNAL_POINT,
                EXTERNAL_SHAPE,
                EXTERNAL_CIRCLE,
                EXTERNAL_RECTANGLE,
                EXTERNAL_RESULT,
                EXTERNAL_SUCCESS,
                EXTERNAL_FAILURE,
                PERSON_SPEC,
                POINT_SPEC,
                SHAPE_SPEC,
                RESULT_SPEC);

    assertThat(compilation.status())
        .as("Compilation should succeed")
        .isEqualTo(Compilation.Status.SUCCESS);

    compiled = new SpecCompiledResult(compilation);
  }

  // ==================== @ViaBuilder LENS LAWS ====================

  @Nested
  @DisplayName("@ViaBuilder Generated Lens Laws")
  class ViaBuilderLensLaws {

    @TestFactory
    @DisplayName("@ViaBuilder lenses satisfy Get-Put law")
    Stream<DynamicTest> viaBuilderLensesSatisfyGetPut() {
      return Stream.of(
          lensGetPutTest("Person.name", "com.test.Person", "name", "Alice", 30),
          lensGetPutTest("Person.age", "com.test.Person", "age", "Alice", 30));
    }

    @TestFactory
    @DisplayName("@ViaBuilder lenses satisfy Put-Get law")
    Stream<DynamicTest> viaBuilderLensesSatisfyPutGet() {
      return Stream.of(
          lensPutGetTest("Person.name", "com.test.Person", "name", "Bob", "Alice", 30),
          lensPutGetTest("Person.age", "com.test.Person", "age", 45, "Alice", 30));
    }

    @TestFactory
    @DisplayName("@ViaBuilder lenses satisfy Put-Put law")
    Stream<DynamicTest> viaBuilderLensesSatisfyPutPut() {
      return Stream.of(
          lensPutPutTest("Person.name", "com.test.Person", "name", "Name1", "Name2", "Alice", 30),
          lensPutPutTest("Person.age", "com.test.Person", "age", 35, 40, "Alice", 30));
    }
  }

  // ==================== @Wither LENS LAWS ====================

  @Nested
  @DisplayName("@Wither Generated Lens Laws")
  class WitherLensLaws {

    @TestFactory
    @DisplayName("@Wither lenses satisfy Get-Put law")
    Stream<DynamicTest> witherLensesSatisfyGetPut() {
      return Stream.of(
          lensGetPutTest("Point.x", "com.test.Point", "x", 10, 20),
          lensGetPutTest("Point.y", "com.test.Point", "y", 10, 20));
    }

    @TestFactory
    @DisplayName("@Wither lenses satisfy Put-Get law")
    Stream<DynamicTest> witherLensesSatisfyPutGet() {
      return Stream.of(
          lensPutGetTest("Point.x", "com.test.Point", "x", 50, 10, 20),
          lensPutGetTest("Point.y", "com.test.Point", "y", 75, 10, 20));
    }

    @TestFactory
    @DisplayName("@Wither lenses satisfy Put-Put law")
    Stream<DynamicTest> witherLensesSatisfyPutPut() {
      return Stream.of(
          lensPutPutTest("Point.x", "com.test.Point", "x", 30, 40, 10, 20),
          lensPutPutTest("Point.y", "com.test.Point", "y", 60, 80, 10, 20));
    }
  }

  // ==================== @InstanceOf PRISM LAWS ====================

  @Nested
  @DisplayName("@InstanceOf Generated Prism Laws")
  class InstanceOfPrismLaws {

    @TestFactory
    @DisplayName("@InstanceOf prisms satisfy Review-Preview law")
    Stream<DynamicTest> instanceOfPrismsSatisfyReviewPreview() {
      return Stream.of(
          prismReviewPreviewTest(
              "Shape.circle", "com.test.Shape", "circle", "com.external.Circle", 5.0),
          prismReviewPreviewTest(
              "Shape.rectangle",
              "com.test.Shape",
              "rectangle",
              "com.external.Rectangle",
              10.0,
              20.0));
    }

    @TestFactory
    @DisplayName("@InstanceOf prisms satisfy Preview-Review law")
    Stream<DynamicTest> instanceOfPrismsSatisfyPreviewReview() {
      return Stream.of(
          // Matching cases
          prismPreviewReviewTest(
              "Shape.circle (matching)", "com.test.Shape", "circle", "com.external.Circle", 5.0),
          prismPreviewReviewTest(
              "Shape.rectangle (matching)",
              "com.test.Shape",
              "rectangle",
              "com.external.Rectangle",
              10.0,
              20.0),
          // Non-matching cases
          DynamicTest.dynamicTest(
              "Shape.circle (non-matching) satisfies Preview-Review",
              () -> {
                Object prism = compiled.invokeStatic("com.test.Shape", "circle");
                Object rectangle = compiled.newInstance("com.external.Rectangle", 10.0, 20.0);

                Optional<Object> extracted = compiled.invokePrismGetOptional(prism, rectangle);
                Object result =
                    extracted.map(v -> invokePrismBuildSafe(prism, v)).orElse(rectangle);

                assertThat(result)
                    .as("Preview-Review: getOptional(s).map(build).orElse(s) == s")
                    .isEqualTo(rectangle);
              }),
          DynamicTest.dynamicTest(
              "Shape.rectangle (non-matching) satisfies Preview-Review",
              () -> {
                Object prism = compiled.invokeStatic("com.test.Shape", "rectangle");
                Object circle = compiled.newInstance("com.external.Circle", 5.0);

                Optional<Object> extracted = compiled.invokePrismGetOptional(prism, circle);
                Object result = extracted.map(v -> invokePrismBuildSafe(prism, v)).orElse(circle);

                assertThat(result)
                    .as("Preview-Review: getOptional(s).map(build).orElse(s) == s")
                    .isEqualTo(circle);
              }));
    }

    @TestFactory
    @DisplayName("@InstanceOf prisms correctly match/non-match")
    Stream<DynamicTest> instanceOfPrismsCorrectlyMatch() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Shape.circle matches Circle",
              () -> {
                Object prism = compiled.invokeStatic("com.test.Shape", "circle");
                Object circle = compiled.newInstance("com.external.Circle", 5.0);

                Optional<Object> result = compiled.invokePrismGetOptional(prism, circle);
                assertThat(result).isPresent().contains(circle);
              }),
          DynamicTest.dynamicTest(
              "Shape.circle does not match Rectangle",
              () -> {
                Object prism = compiled.invokeStatic("com.test.Shape", "circle");
                Object rectangle = compiled.newInstance("com.external.Rectangle", 10.0, 20.0);

                Optional<Object> result = compiled.invokePrismGetOptional(prism, rectangle);
                assertThat(result).isEmpty();
              }));
    }
  }

  // ==================== @MatchWhen PRISM LAWS ====================

  @Nested
  @DisplayName("@MatchWhen Generated Prism Laws")
  class MatchWhenPrismLaws {

    @TestFactory
    @DisplayName("@MatchWhen prisms satisfy Review-Preview law")
    Stream<DynamicTest> matchWhenPrismsSatisfyReviewPreview() {
      return Stream.of(
          prismReviewPreviewTest(
              "Result.success", "com.test.Result", "success", "com.external.Success", "test-value"),
          prismReviewPreviewTest(
              "Result.failure",
              "com.test.Result",
              "failure",
              "com.external.Failure",
              "error-message"));
    }

    @TestFactory
    @DisplayName("@MatchWhen prisms satisfy Preview-Review law")
    Stream<DynamicTest> matchWhenPrismsSatisfyPreviewReview() {
      return Stream.of(
          // Matching cases
          prismPreviewReviewTest(
              "Result.success (matching)",
              "com.test.Result",
              "success",
              "com.external.Success",
              "test-value"),
          prismPreviewReviewTest(
              "Result.failure (matching)",
              "com.test.Result",
              "failure",
              "com.external.Failure",
              "error-message"),
          // Non-matching cases
          DynamicTest.dynamicTest(
              "Result.success (non-matching) satisfies Preview-Review",
              () -> {
                Object prism = compiled.invokeStatic("com.test.Result", "success");
                Object failure = compiled.newInstance("com.external.Failure", "error");

                Optional<Object> extracted = compiled.invokePrismGetOptional(prism, failure);
                Object result = extracted.map(v -> invokePrismBuildSafe(prism, v)).orElse(failure);

                assertThat(result)
                    .as("Preview-Review: getOptional(s).map(build).orElse(s) == s")
                    .isEqualTo(failure);
              }));
    }

    @TestFactory
    @DisplayName("@MatchWhen prisms correctly match/non-match")
    Stream<DynamicTest> matchWhenPrismsCorrectlyMatch() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Result.success matches Success",
              () -> {
                Object prism = compiled.invokeStatic("com.test.Result", "success");
                Object success = compiled.newInstance("com.external.Success", "value");

                Optional<Object> result = compiled.invokePrismGetOptional(prism, success);
                assertThat(result).isPresent().contains(success);
              }),
          DynamicTest.dynamicTest(
              "Result.success does not match Failure",
              () -> {
                Object prism = compiled.invokeStatic("com.test.Result", "success");
                Object failure = compiled.newInstance("com.external.Failure", "error");

                Optional<Object> result = compiled.invokePrismGetOptional(prism, failure);
                assertThat(result).isEmpty();
              }));
    }
  }

  // ==================== COMPOSED OPERATIONS ====================

  @Nested
  @DisplayName("Composed Operations")
  class ComposedOperations {

    @TestFactory
    @DisplayName("Multiple lens updates are independent")
    Stream<DynamicTest> multipleLensUpdatesAreIndependent() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Point x and y updates are independent",
              () -> {
                Object xLens = compiled.invokeStatic("com.test.Point", "x");
                Object yLens = compiled.invokeStatic("com.test.Point", "y");
                Object point = compiled.newInstance("com.external.Point", 10, 20);

                // Apply two independent lens updates
                Object updated =
                    compiled.invokeLensSet(yLens, 50, compiled.invokeLensSet(xLens, 100, point));

                // Verify both updates took effect
                assertThat(compiled.invokeLensGet(xLens, updated)).isEqualTo(100);
                assertThat(compiled.invokeLensGet(yLens, updated)).isEqualTo(50);

                // Verify original unchanged
                assertThat(compiled.invokeLensGet(xLens, point)).isEqualTo(10);
                assertThat(compiled.invokeLensGet(yLens, point)).isEqualTo(20);
              }),
          DynamicTest.dynamicTest(
              "Person name and age updates are independent",
              () -> {
                Object nameLens = compiled.invokeStatic("com.test.Person", "name");
                Object ageLens = compiled.invokeStatic("com.test.Person", "age");
                Object person = compiled.newInstance("com.external.Person", "Alice", 30);

                // Apply two independent lens updates
                Object updated =
                    compiled.invokeLensSet(
                        ageLens, 45, compiled.invokeLensSet(nameLens, "Bob", person));

                // Verify both updates took effect
                assertThat(compiled.invokeLensGet(nameLens, updated)).isEqualTo("Bob");
                assertThat(compiled.invokeLensGet(ageLens, updated)).isEqualTo(45);

                // Verify original unchanged
                assertThat(compiled.invokeLensGet(nameLens, person)).isEqualTo("Alice");
                assertThat(compiled.invokeLensGet(ageLens, person)).isEqualTo(30);
              }));
    }

    @TestFactory
    @DisplayName("Lens updates are order-independent for different fields")
    Stream<DynamicTest> lensUpdatesAreOrderIndependent() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Point x/y update order doesn't matter",
              () -> {
                Object xLens = compiled.invokeStatic("com.test.Point", "x");
                Object yLens = compiled.invokeStatic("com.test.Point", "y");
                Object point = compiled.newInstance("com.external.Point", 10, 20);

                // Apply updates in different orders
                Object order1 =
                    compiled.invokeLensSet(yLens, 50, compiled.invokeLensSet(xLens, 100, point));
                Object order2 =
                    compiled.invokeLensSet(xLens, 100, compiled.invokeLensSet(yLens, 50, point));

                assertThat(order1).isEqualTo(order2);
              }));
    }
  }

  // ==================== TEST HELPERS ====================

  private DynamicTest lensGetPutTest(
      String testName, String specClass, String lensMethod, Object... constructorArgs) {
    return DynamicTest.dynamicTest(
        testName + " satisfies Get-Put",
        () -> {
          Object lens = compiled.invokeStatic(specClass, lensMethod);
          String sourceClass = getSourceClassForSpec(specClass);
          Object source = compiled.newInstance(sourceClass, constructorArgs);

          Object currentValue = compiled.invokeLensGet(lens, source);
          Object result = compiled.invokeLensSet(lens, currentValue, source);

          assertThat(result).as("Get-Put: set(get(s), s) == s").isEqualTo(source);
        });
  }

  private DynamicTest lensPutGetTest(
      String testName,
      String specClass,
      String lensMethod,
      Object newValue,
      Object... constructorArgs) {
    return DynamicTest.dynamicTest(
        testName + " satisfies Put-Get",
        () -> {
          Object lens = compiled.invokeStatic(specClass, lensMethod);
          String sourceClass = getSourceClassForSpec(specClass);
          Object source = compiled.newInstance(sourceClass, constructorArgs);

          Object updated = compiled.invokeLensSet(lens, newValue, source);
          Object retrieved = compiled.invokeLensGet(lens, updated);

          assertThat(retrieved).as("Put-Get: get(set(a, s)) == a").isEqualTo(newValue);
        });
  }

  private DynamicTest lensPutPutTest(
      String testName,
      String specClass,
      String lensMethod,
      Object value1,
      Object value2,
      Object... constructorArgs) {
    return DynamicTest.dynamicTest(
        testName + " satisfies Put-Put",
        () -> {
          Object lens = compiled.invokeStatic(specClass, lensMethod);
          String sourceClass = getSourceClassForSpec(specClass);
          Object source = compiled.newInstance(sourceClass, constructorArgs);

          Object doubleSet =
              compiled.invokeLensSet(lens, value2, compiled.invokeLensSet(lens, value1, source));
          Object singleSet = compiled.invokeLensSet(lens, value2, source);

          assertThat(doubleSet).as("Put-Put: set(b, set(a, s)) == set(b, s)").isEqualTo(singleSet);
        });
  }

  private DynamicTest prismReviewPreviewTest(
      String testName,
      String specClass,
      String prismMethod,
      String valueClass,
      Object... constructorArgs) {
    return DynamicTest.dynamicTest(
        testName + " satisfies Review-Preview",
        () -> {
          Object prism = compiled.invokeStatic(specClass, prismMethod);
          Object value = compiled.newInstance(valueClass, constructorArgs);

          Object built = compiled.invokePrismBuild(prism, value);
          Optional<Object> extracted = compiled.invokePrismGetOptional(prism, built);

          assertThat(extracted)
              .as("Review-Preview: getOptional(build(a)) == Optional.of(a)")
              .isPresent()
              .contains(value);
        });
  }

  private DynamicTest prismPreviewReviewTest(
      String testName,
      String specClass,
      String prismMethod,
      String sourceClass,
      Object... constructorArgs) {
    return DynamicTest.dynamicTest(
        testName + " satisfies Preview-Review",
        () -> {
          Object prism = compiled.invokeStatic(specClass, prismMethod);
          Object source = compiled.newInstance(sourceClass, constructorArgs);

          Optional<Object> extracted = compiled.invokePrismGetOptional(prism, source);
          Object result = extracted.map(v -> invokePrismBuildSafe(prism, v)).orElse(source);

          assertThat(result)
              .as("Preview-Review: getOptional(s).map(build).orElse(s) == s")
              .isEqualTo(source);
        });
  }

  private Object invokePrismBuildSafe(Object prism, Object value) {
    try {
      return compiled.invokePrismBuild(prism, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private String getSourceClassForSpec(String specClass) {
    return switch (specClass) {
      case "com.test.Person" -> "com.external.Person";
      case "com.test.Point" -> "com.external.Point";
      case "com.test.Shape" -> "com.external.Shape";
      case "com.test.Result" -> "com.external.Result";
      default -> throw new IllegalArgumentException("Unknown spec class: " + specClass);
    };
  }

  // ==================== COMPILATION HELPER ====================

  /** Result of a successful compilation, providing access to generated classes. */
  static class SpecCompiledResult {
    private final Compilation compilation;
    private final ClassLoader classLoader;

    SpecCompiledResult(Compilation compilation) {
      this.compilation = compilation;
      this.classLoader = new SpecCompiledClassLoader(compilation, getClass().getClassLoader());
    }

    public Class<?> loadClass(String className) throws ClassNotFoundException {
      return classLoader.loadClass(className);
    }

    public Object newInstance(String className, Object... args)
        throws ReflectiveOperationException {
      Class<?> clazz = loadClass(className);
      Class<?>[] argTypes = new Class<?>[args.length];
      for (int i = 0; i < args.length; i++) {
        argTypes[i] = toPrimitiveType(args[i].getClass());
      }
      return clazz.getDeclaredConstructor(argTypes).newInstance(args);
    }

    public Object invokeStatic(String className, String methodName, Object... args)
        throws ReflectiveOperationException {
      Class<?> clazz = loadClass(className);
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
          method.setAccessible(true);
          return method.invoke(null, args);
        }
      }
      throw new NoSuchMethodException(className + "." + methodName);
    }

    public Object invokeLensGet(Object lens, Object source) throws ReflectiveOperationException {
      Method getMethod = findMethod(lens.getClass(), "get", 1);
      getMethod.setAccessible(true);
      return getMethod.invoke(lens, source);
    }

    public Object invokeLensSet(Object lens, Object value, Object source)
        throws ReflectiveOperationException {
      Method setMethod = findMethod(lens.getClass(), "set", 2);
      setMethod.setAccessible(true);
      return setMethod.invoke(lens, value, source);
    }

    @SuppressWarnings("unchecked")
    public Optional<Object> invokePrismGetOptional(Object prism, Object source)
        throws ReflectiveOperationException {
      Method getOptionalMethod = findMethod(prism.getClass(), "getOptional", 1);
      getOptionalMethod.setAccessible(true);
      return (Optional<Object>) getOptionalMethod.invoke(prism, source);
    }

    public Object invokePrismBuild(Object prism, Object value) throws ReflectiveOperationException {
      Method buildMethod = findMethod(prism.getClass(), "build", 1);
      buildMethod.setAccessible(true);
      return buildMethod.invoke(prism, value);
    }

    private Method findMethod(Class<?> clazz, String name, int paramCount)
        throws NoSuchMethodException {
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
          return method;
        }
      }
      for (Method method : clazz.getMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
          return method;
        }
      }
      Class<?> superclass = clazz.getSuperclass();
      if (superclass != null && superclass != Object.class) {
        return findMethod(superclass, name, paramCount);
      }
      throw new NoSuchMethodException(clazz.getName() + "." + name);
    }

    private Class<?> toPrimitiveType(Class<?> wrapper) {
      if (wrapper == Integer.class) return int.class;
      if (wrapper == Long.class) return long.class;
      if (wrapper == Double.class) return double.class;
      if (wrapper == Float.class) return float.class;
      if (wrapper == Boolean.class) return boolean.class;
      if (wrapper == Byte.class) return byte.class;
      if (wrapper == Short.class) return short.class;
      if (wrapper == Character.class) return char.class;
      return wrapper;
    }
  }

  /** ClassLoader that loads classes from compilation output. */
  private static class SpecCompiledClassLoader extends ClassLoader {
    private final Map<String, byte[]> classBytes = new HashMap<>();

    SpecCompiledClassLoader(Compilation compilation, ClassLoader parent) {
      super(parent);
      for (JavaFileObject file : compilation.generatedFiles()) {
        if (file.getKind() == JavaFileObject.Kind.CLASS) {
          String className = inferClassName(file);
          try (InputStream is = file.openInputStream()) {
            classBytes.put(className, readAllBytes(is));
          } catch (IOException e) {
            throw new RuntimeException("Failed to read class file: " + file.getName(), e);
          }
        }
      }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      byte[] bytes = classBytes.get(name);
      if (bytes != null) {
        return defineClass(name, bytes, 0, bytes.length);
      }
      throw new ClassNotFoundException(name);
    }

    private String inferClassName(JavaFileObject file) {
      String name = file.getName();
      int classOutputIndex = name.indexOf(StandardLocation.CLASS_OUTPUT.getName());
      if (classOutputIndex >= 0) {
        name = name.substring(classOutputIndex + StandardLocation.CLASS_OUTPUT.getName().length());
      }
      if (name.startsWith("/")) {
        name = name.substring(1);
      }
      if (name.endsWith(".class")) {
        name = name.substring(0, name.length() - 6);
      }
      return name.replace('/', '.');
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] data = new byte[4096];
      int bytesRead;
      while ((bytesRead = is.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, bytesRead);
      }
      return buffer.toByteArray();
    }
  }
}
