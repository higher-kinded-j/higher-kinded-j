// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link SetterProcessor} and {@link GetterProcessor}.
 *
 * <p>These tests provide coverage for previously untested processors, targeting 53 NO_COVERAGE
 * mutations across both processors.
 */
@DisplayName("Setter & Getter Processor Tests")
class SetterGetterProcessorTest {

  // =============================================================================
  // SetterProcessor Tests
  // =============================================================================

  @Nested
  @DisplayName("SetterProcessor Tests")
  class SetterProcessorTests {

    @Test
    @DisplayName("should generate setters for simple record")
    void shouldGenerateSettersForSimpleRecord() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Point",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters
              public record Point(int x, int y) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.test.PointSetters"))
          .isPresent();

      String code =
          compilation
              .generatedSourceFile("com.test.PointSetters")
              .get()
              .getCharContent(true)
              .toString();

      // Setter methods for each component
      Assertions.assertThat(code).contains("Setter<Point, Integer> x()");
      Assertions.assertThat(code).contains("Setter<Point, Integer> y()");
      // With methods
      Assertions.assertThat(code).contains("withX(");
      Assertions.assertThat(code).contains("withY(");
      // Constructor reconstruction
      Assertions.assertThat(code).contains("new Point(");
      // Generated annotation
      Assertions.assertThat(code).contains("@Generated");
    }

    @Test
    @DisplayName("should generate setters for record with String field")
    void shouldGenerateSettersForRecordWithStringField() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Name",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters
              public record Name(String first, String last) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.NameSetters")
              .get()
              .getCharContent(true)
              .toString();

      Assertions.assertThat(code).contains("Setter<Name, String> first()");
      Assertions.assertThat(code).contains("Setter<Name, String> last()");
      Assertions.assertThat(code).contains("withFirst(");
      Assertions.assertThat(code).contains("withLast(");
      // Setter getter reference
      Assertions.assertThat(code).contains("Name::first");
      Assertions.assertThat(code).contains("Name::last");
    }

    @Test
    @DisplayName("should generate setters for generic record")
    void shouldGenerateSettersForGenericRecord() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Pair",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters
              public record Pair<A, B>(A first, B second) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.PairSetters")
              .get()
              .getCharContent(true)
              .toString();

      // Generic type parameters should be preserved
      Assertions.assertThat(code).contains("<A, B>");
      Assertions.assertThat(code).contains("withFirst(");
      Assertions.assertThat(code).contains("withSecond(");
    }

    @Test
    @DisplayName("should fail on non-record")
    void shouldFailOnNonRecord() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.NotRecord",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters
              public class NotRecord {
                  private String name;
              }
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("records");
    }

    @Test
    @DisplayName("should support custom target package")
    void shouldSupportCustomTargetPackage() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Item",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters(targetPackage = "com.generated")
              public record Item(String value) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.generated.ItemSetters"))
          .isPresent();
    }

    @Test
    @DisplayName("with method for non-generic record should call setter directly")
    void withMethodNonGenericCallsSetterDirectly() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Simple",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters
              public record Simple(String value) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      String code =
          compilation
              .generatedSourceFile("com.test.SimpleSetters")
              .get()
              .getCharContent(true)
              .toString();

      // Non-generic: with method calls setter directly without type args
      Assertions.assertThat(code).contains("value().set(");
    }

    @Test
    @DisplayName("with method for generic record should use explicit type args")
    void withMethodGenericUsesExplicitTypeArgs() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Box",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters
              public record Box<T>(T content) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      String code =
          compilation
              .generatedSourceFile("com.test.BoxSetters")
              .get()
              .getCharContent(true)
              .toString();

      // Generic: with method uses explicit type args: BoxSetters.<T>content()
      Assertions.assertThat(code).contains("BoxSetters.<T>content()");
    }

    @Test
    @DisplayName("setter method has fromGetSet pattern")
    void setterMethodHasFromGetSetPattern() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Data",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters
              public record Data(String name, int count) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      String code =
          compilation
              .generatedSourceFile("com.test.DataSetters")
              .get()
              .getCharContent(true)
              .toString();

      // Should use Setter.fromGetSet pattern
      Assertions.assertThat(code).contains("Setter.fromGetSet(");
      // Multi-field: second field should use newValue in correct position
      Assertions.assertThat(code).contains("source.name()");
      Assertions.assertThat(code).contains("newValue");
    }
  }

  // =============================================================================
  // GetterProcessor Tests
  // =============================================================================

  @Nested
  @DisplayName("GetterProcessor Tests")
  class GetterProcessorTests {

    @Test
    @DisplayName("should generate getters for simple record")
    void shouldGenerateGettersForSimpleRecord() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Point",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters
              public record Point(int x, int y) {}
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.test.PointGetters"))
          .isPresent();

      String code =
          compilation
              .generatedSourceFile("com.test.PointGetters")
              .get()
              .getCharContent(true)
              .toString();

      // Getter methods for each component
      Assertions.assertThat(code).contains("Getter<Point, Integer> x()");
      Assertions.assertThat(code).contains("Getter<Point, Integer> y()");
      // Get convenience methods
      Assertions.assertThat(code).contains("getX(");
      Assertions.assertThat(code).contains("getY(");
      // Generated annotation
      Assertions.assertThat(code).contains("@Generated");
    }

    @Test
    @DisplayName("should generate getters for record with String field")
    void shouldGenerateGettersForRecordWithStringField() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Name",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters
              public record Name(String first, String last) {}
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.NameGetters")
              .get()
              .getCharContent(true)
              .toString();

      Assertions.assertThat(code).contains("Getter<Name, String> first()");
      Assertions.assertThat(code).contains("Getter<Name, String> last()");
      Assertions.assertThat(code).contains("getFirst(");
      Assertions.assertThat(code).contains("getLast(");
      // Getter reference
      Assertions.assertThat(code).contains("Getter.of(");
    }

    @Test
    @DisplayName("should generate getters for generic record")
    void shouldGenerateGettersForGenericRecord() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Pair",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters
              public record Pair<A, B>(A first, B second) {}
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.PairGetters")
              .get()
              .getCharContent(true)
              .toString();

      // Generic type parameters should be preserved
      Assertions.assertThat(code).contains("<A, B>");
      Assertions.assertThat(code).contains("getFirst(");
      Assertions.assertThat(code).contains("getSecond(");
    }

    @Test
    @DisplayName("should fail on non-record")
    void shouldFailOnNonRecord() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.NotRecord",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters
              public class NotRecord {
                  private String name;
              }
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(source);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("records");
    }

    @Test
    @DisplayName("should support custom target package")
    void shouldSupportCustomTargetPackage() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Item",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters(targetPackage = "com.generated")
              public record Item(String value) {}
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.generated.ItemGetters"))
          .isPresent();
    }

    @Test
    @DisplayName("get method for non-generic record calls getter directly")
    void getMethodNonGenericCallsGetterDirectly() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Simple",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters
              public record Simple(String value) {}
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      String code =
          compilation
              .generatedSourceFile("com.test.SimpleGetters")
              .get()
              .getCharContent(true)
              .toString();

      // Non-generic: get method calls getter directly without type args
      Assertions.assertThat(code).contains("value().get(source)");
    }

    @Test
    @DisplayName("get method for generic record uses explicit type args")
    void getMethodGenericUsesExplicitTypeArgs() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Box",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters
              public record Box<T>(T content) {}
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      String code =
          compilation
              .generatedSourceFile("com.test.BoxGetters")
              .get()
              .getCharContent(true)
              .toString();

      // Generic: get method uses explicit type args: BoxGetters.<T>content()
      Assertions.assertThat(code).contains("BoxGetters.<T>content()");
    }
  }

  // =============================================================================
  // PrismProcessor Enum Tests (NO_COVERAGE)
  // =============================================================================

  @Nested
  @DisplayName("PrismProcessor Enum Tests")
  class PrismProcessorEnumTests {

    @Test
    @DisplayName("should generate prisms for enum")
    void shouldGeneratePrismsForEnum() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Color",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GeneratePrisms;
              @GeneratePrisms
              public enum Color {
                  RED, GREEN, BLUE;
              }
              """);

      Compilation compilation = javac().withProcessors(new PrismProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.test.ColorPrisms"))
          .isPresent();

      String code =
          compilation
              .generatedSourceFile("com.test.ColorPrisms")
              .get()
              .getCharContent(true)
              .toString();

      Assertions.assertThat(code).contains("RED");
      Assertions.assertThat(code).contains("GREEN");
      Assertions.assertThat(code).contains("BLUE");
    }

    @Test
    @DisplayName("should fail on non-sealed non-enum type")
    void shouldFailOnNonSealedNonEnumType() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.BadPrism",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GeneratePrisms;
              @GeneratePrisms
              public class BadPrism {}
              """);

      Compilation compilation = javac().withProcessors(new PrismProcessor()).compile(source);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("sealed interfaces or enums");
    }

    @Test
    @DisplayName("should generate prisms for sealed interface with record subtypes")
    void shouldGeneratePrismsForSealedInterface() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Shape",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GeneratePrisms;
              @GeneratePrisms
              public sealed interface Shape {
                  record Circle(double radius) implements Shape {}
                  record Square(double side) implements Shape {}
              }
              """);

      Compilation compilation = javac().withProcessors(new PrismProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.ShapePrisms")
              .get()
              .getCharContent(true)
              .toString();

      Assertions.assertThat(code).contains("circle()");
      Assertions.assertThat(code).contains("square()");
      Assertions.assertThat(code).contains("instanceof");
      Assertions.assertThat(code).contains("@Generated");
    }
  }
}
