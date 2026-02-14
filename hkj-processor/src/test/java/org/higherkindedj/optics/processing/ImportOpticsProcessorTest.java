// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link ImportOpticsProcessor}.
 *
 * <p>These tests verify that the processor correctly generates optics for various external types
 * including records, sealed interfaces, enums, and classes with wither methods.
 */
@DisplayName("ImportOpticsProcessor")
class ImportOpticsProcessorTest {

  @Nested
  @DisplayName("Record Processing")
  class RecordProcessing {

    @Test
    @DisplayName("should generate lenses for external record via package-info")
    void shouldGenerateLensesForExternalRecord() {
      // External record to import
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Customer",
              """
              package com.external;

              public record Customer(String name, int age) {}
              """);

      // Package-info with @ImportOptics
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Customer.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedNameLens =
          """
          public static Lens<Customer, String> name() {
              return Lens.of(Customer::name, (source, newValue) -> new Customer(newValue, source.age()));
          }
          """;

      final String expectedAgeLens =
          """
          public static Lens<Customer, Integer> age() {
              return Lens.of(Customer::age, (source, newValue) -> new Customer(source.name(), newValue));
          }
          """;

      assertGeneratedCodeContains(compilation, "com.myapp.optics.CustomerLenses", expectedNameLens);
      assertGeneratedCodeContains(compilation, "com.myapp.optics.CustomerLenses", expectedAgeLens);
    }

    @Test
    @DisplayName("should generate lenses with type parameters for generic record")
    void shouldGenerateLensesForGenericRecord() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Pair",
              """
              package com.external;

              public record Pair<A, B>(A first, B second) {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Pair.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedFirstLens =
          """
          public static <A, B> Lens<Pair<A, B>, A> first()
          """;

      assertGeneratedCodeContains(compilation, "com.myapp.optics.PairLenses", expectedFirstLens);
    }

    @Test
    @DisplayName("should generate with methods for record")
    void shouldGenerateWithMethodsForRecord() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Point",
              """
              package com.external;

              public record Point(int x, int y) {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Point.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedWithX =
          """
          public static Point withX(Point source, int newX) {
              return x().set(newX, source);
          }
          """;

      assertGeneratedCodeContains(compilation, "com.myapp.optics.PointLenses", expectedWithX);
    }
  }

  @Nested
  @DisplayName("Sealed Interface Processing")
  class SealedInterfaceProcessing {

    @Test
    @DisplayName("should generate prisms for sealed interface subtypes")
    void shouldGeneratePrismsForSealedInterface() {
      final var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.external.Shape",
              """
              package com.external;

              public sealed interface Shape permits Circle, Rectangle {}
              """);

      final var circleSubtype =
          JavaFileObjects.forSourceString(
              "com.external.Circle",
              """
              package com.external;

              public record Circle(double radius) implements Shape {}
              """);

      final var rectangleSubtype =
          JavaFileObjects.forSourceString(
              "com.external.Rectangle",
              """
              package com.external;

              public record Rectangle(double width, double height) implements Shape {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Shape.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(sealedInterface, circleSubtype, rectangleSubtype, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedCirclePrism =
          """
          public static Prism<Shape, Circle> circle() {
              return Prism.of(source -> source instanceof Circle ? Optional.of((Circle) source) : Optional.empty(), value -> value);
          }
          """;

      final String expectedRectanglePrism =
          """
          public static Prism<Shape, Rectangle> rectangle() {
              return Prism.of(source -> source instanceof Rectangle ? Optional.of((Rectangle) source) : Optional.empty(), value -> value);
          }
          """;

      assertGeneratedCodeContains(compilation, "com.myapp.optics.ShapePrisms", expectedCirclePrism);
      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.ShapePrisms", expectedRectanglePrism);
    }
  }

  @Nested
  @DisplayName("Enum Processing")
  class EnumProcessing {

    @Test
    @DisplayName("should generate prisms for enum constants")
    void shouldGeneratePrismsForEnum() {
      final var enumType =
          JavaFileObjects.forSourceString(
              "com.external.Status",
              """
              package com.external;

              public enum Status { PENDING, ACTIVE, COMPLETED }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Status.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(enumType, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedPendingPrism =
          """
          public static Prism<Status, Status> pending() {
              return Prism.of(source -> source == Status.PENDING ? Optional.of(source) : Optional.empty(), value -> value);
          }
          """;

      final String expectedActivePrism =
          """
          public static Prism<Status, Status> active() {
              return Prism.of(source -> source == Status.ACTIVE ? Optional.of(source) : Optional.empty(), value -> value);
          }
          """;

      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.StatusPrisms", expectedPendingPrism);
      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.StatusPrisms", expectedActivePrism);
    }

    @Test
    @DisplayName("should convert SNAKE_CASE enum constants to camelCase method names")
    void shouldConvertSnakeCaseToCamelCase() {
      final var enumType =
          JavaFileObjects.forSourceString(
              "com.external.HttpStatus",
              """
              package com.external;

              public enum HttpStatus { OK, NOT_FOUND, INTERNAL_SERVER_ERROR }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.HttpStatus.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(enumType, packageInfo);

      assertThat(compilation).succeeded();

      // NOT_FOUND -> notFound
      final String expectedNotFoundPrism = "public static Prism<HttpStatus, HttpStatus> notFound()";

      // INTERNAL_SERVER_ERROR -> internalServerError
      final String expectedInternalServerErrorPrism =
          "public static Prism<HttpStatus, HttpStatus> internalServerError()";

      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.HttpStatusPrisms", expectedNotFoundPrism);
      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.HttpStatusPrisms", expectedInternalServerErrorPrism);
    }
  }

  @Nested
  @DisplayName("Wither Class Processing")
  class WitherClassProcessing {

    @Test
    @DisplayName("should generate lenses for class with wither methods")
    void shouldGenerateLensesForWitherClass() {
      final var witherClass =
          JavaFileObjects.forSourceString(
              "com.external.ImmutableDate",
              """
              package com.external;

              public final class ImmutableDate {
                  private final int year;
                  private final int month;
                  private final int day;

                  public ImmutableDate(int year, int month, int day) {
                      this.year = year;
                      this.month = month;
                      this.day = day;
                  }

                  public int getYear() { return year; }
                  public int getMonth() { return month; }
                  public int getDay() { return day; }

                  public ImmutableDate withYear(int year) {
                      return new ImmutableDate(year, this.month, this.day);
                  }

                  public ImmutableDate withMonth(int month) {
                      return new ImmutableDate(this.year, month, this.day);
                  }

                  public ImmutableDate withDay(int day) {
                      return new ImmutableDate(this.year, this.month, day);
                  }
              }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.ImmutableDate.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(witherClass, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedYearLens =
          """
          public static Lens<ImmutableDate, Integer> year() {
              return Lens.of(ImmutableDate::getYear, (source, newValue) -> source.withYear(newValue));
          }
          """;

      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.ImmutableDateLenses", expectedYearLens);
    }
  }

  @Nested
  @DisplayName("Error Cases")
  class ErrorCases {

    @Test
    @DisplayName("should reject mutable class without allowMutable flag")
    void shouldRejectMutableClassWithoutFlag() {
      final var mutableClass =
          JavaFileObjects.forSourceString(
              "com.external.MutablePerson",
              """
              package com.external;

              public class MutablePerson {
                  private String name;

                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }

                  public MutablePerson withName(String name) {
                      MutablePerson copy = new MutablePerson();
                      copy.name = name;
                      return copy;
                  }
              }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.MutablePerson.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(mutableClass, packageInfo);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("has mutable fields").inFile(packageInfo);
    }

    @Test
    @DisplayName("should allow mutable class with allowMutable = true")
    void shouldAllowMutableClassWithFlag() {
      final var mutableClass =
          JavaFileObjects.forSourceString(
              "com.external.MutablePerson",
              """
              package com.external;

              public class MutablePerson {
                  private String name;

                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }

                  public MutablePerson withName(String name) {
                      MutablePerson copy = new MutablePerson();
                      copy.name = name;
                      return copy;
                  }
              }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics(value = {com.external.MutablePerson.class}, allowMutable = true)
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(mutableClass, packageInfo);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should reject unsupported type without withers")
    void shouldRejectUnsupportedType() {
      final var plainClass =
          JavaFileObjects.forSourceString(
              "com.external.PlainClass",
              """
              package com.external;

              public class PlainClass {
                  private String value;

                  public String getValue() { return value; }
              }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.PlainClass.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(plainClass, packageInfo);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("not a record, sealed interface, enum, or class with wither methods")
          .inFile(packageInfo);
    }
  }

  @Nested
  @DisplayName("Target Package Configuration")
  class TargetPackageConfiguration {

    @Test
    @DisplayName("should use custom target package when specified")
    void shouldUseCustomTargetPackage() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Order",
              """
              package com.external;

              public record Order(String id, int quantity) {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics(value = {com.external.Order.class}, targetPackage = "com.myapp.generated")
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.generated.OrderLenses").isNotNull();
    }

    @Test
    @DisplayName("should use annotated package when targetPackage is empty")
    void shouldUseAnnotatedPackageByDefault() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Item",
              """
              package com.external;

              public record Item(String name) {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.custom.package-info",
              """
              @ImportOptics({com.external.Item.class})
              package com.myapp.custom;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.custom.ItemLenses").isNotNull();
    }
  }

  @Nested
  @DisplayName("Type-Level Annotation")
  class TypeLevelAnnotation {

    @Test
    @DisplayName("should support @ImportOptics on a class")
    void shouldSupportAnnotationOnClass() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Product",
              """
              package com.external;

              public record Product(String sku, double price) {}
              """);

      final var importerClass =
          JavaFileObjects.forSourceString(
              "com.myapp.ProductOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({com.external.Product.class})
              public class ProductOptics {}
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importerClass);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.ProductLenses").isNotNull();
    }
  }
}
