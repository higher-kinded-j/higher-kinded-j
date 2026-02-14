// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for incremental compilation behavior.
 *
 * <p>These tests verify that the processor correctly handles changes to source files during
 * development, ensuring:
 *
 * <ul>
 *   <li>Regeneration when source types change
 *   <li>Handling of removed types
 *   <li>Type parameter changes reflected in output
 *   <li>Field additions/removals handled correctly
 * </ul>
 *
 * <p>While these tests use the compile-testing library and don't test true incremental compilation
 * (which requires a real Gradle/IDE build), they simulate the scenarios that would occur during
 * incremental builds.
 */
@DisplayName("Incremental Compilation Tests")
class IncrementalCompilationTest {

  private Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new ImportOpticsProcessor()).compile(sources);
  }

  private JavaFileObject packageInfo(String packageName, String... typeNames) {
    StringBuilder imports = new StringBuilder();
    StringBuilder classes = new StringBuilder();

    for (int i = 0; i < typeNames.length; i++) {
      String typeName = typeNames[i];
      imports.append("import ").append(typeName).append(";\n");
      if (i > 0) classes.append(", ");
      classes.append(typeName.substring(typeName.lastIndexOf('.') + 1)).append(".class");
    }

    return JavaFileObjects.forSourceString(
        packageName + ".package-info",
        String.format(
            """
            @ImportOptics({%s})
            package %s;

            import org.higherkindedj.optics.annotations.ImportOptics;
            %s
            """,
            classes, packageName, imports));
  }

  private String getGeneratedContent(Compilation compilation, String className) {
    Optional<JavaFileObject> generated = compilation.generatedSourceFile(className);
    if (generated.isEmpty()) {
      return null;
    }
    try {
      return generated.get().getCharContent(true).toString();
    } catch (Exception e) {
      return null;
    }
  }

  // =============================================================================
  // Source Change Tests
  // =============================================================================

  @Nested
  @DisplayName("Source Change Tests")
  class SourceChangeTests {

    @Test
    @DisplayName("should regenerate when record adds a new field")
    void shouldRegenerateOnFieldAddition() {
      // First compilation - record with one field
      var recordV1 =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;
              public record Person(String name) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Person");
      var compilation1 = compile(recordV1, pkgInfo);

      assertThat(compilation1.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated1 = getGeneratedContent(compilation1, "com.test.optics.PersonLenses");
      assertThat(generated1).isNotNull();
      assertThat(generated1).contains("name()");
      assertThat(generated1).doesNotContain("age()");

      // Second compilation - record with added field
      var recordV2 =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;
              public record Person(String name, int age) {}
              """);

      var compilation2 = compile(recordV2, pkgInfo);

      assertThat(compilation2.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated2 = getGeneratedContent(compilation2, "com.test.optics.PersonLenses");
      assertThat(generated2).isNotNull();
      assertThat(generated2).contains("name()");
      assertThat(generated2).contains("age()");
    }

    @Test
    @DisplayName("should regenerate when record removes a field")
    void shouldRegenerateOnFieldRemoval() {
      // First compilation - record with two fields
      var recordV1 =
          JavaFileObjects.forSourceString(
              "com.test.Item",
              """
              package com.test;
              public record Item(String id, String name, double price) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Item");
      var compilation1 = compile(recordV1, pkgInfo);

      assertThat(compilation1.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated1 = getGeneratedContent(compilation1, "com.test.optics.ItemLenses");
      assertThat(generated1).isNotNull();
      assertThat(generated1).contains("id()");
      assertThat(generated1).contains("name()");
      assertThat(generated1).contains("price()");

      // Second compilation - record with field removed
      var recordV2 =
          JavaFileObjects.forSourceString(
              "com.test.Item",
              """
              package com.test;
              public record Item(String id, String name) {}
              """);

      var compilation2 = compile(recordV2, pkgInfo);

      assertThat(compilation2.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated2 = getGeneratedContent(compilation2, "com.test.optics.ItemLenses");
      assertThat(generated2).isNotNull();
      assertThat(generated2).contains("id()");
      assertThat(generated2).contains("name()");
      assertThat(generated2).doesNotContain("price()");
    }

    @Test
    @DisplayName("should regenerate when field type changes")
    void shouldRegenerateOnFieldTypeChange() {
      // First compilation - String field
      var recordV1 =
          JavaFileObjects.forSourceString(
              "com.test.Config",
              """
              package com.test;
              public record Config(String timeout) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Config");
      var compilation1 = compile(recordV1, pkgInfo);

      assertThat(compilation1.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated1 = getGeneratedContent(compilation1, "com.test.optics.ConfigLenses");
      assertThat(generated1).isNotNull();
      assertThat(generated1).contains("Lens<Config, String> timeout()");

      // Second compilation - field type changed to int
      var recordV2 =
          JavaFileObjects.forSourceString(
              "com.test.Config",
              """
              package com.test;
              public record Config(int timeout) {}
              """);

      var compilation2 = compile(recordV2, pkgInfo);

      assertThat(compilation2.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated2 = getGeneratedContent(compilation2, "com.test.optics.ConfigLenses");
      assertThat(generated2).isNotNull();
      assertThat(generated2).contains("Lens<Config, Integer> timeout()");
      assertThat(generated2).doesNotContain("Lens<Config, String> timeout()");
    }
  }

  // =============================================================================
  // Type Removal Tests
  // =============================================================================

  @Nested
  @DisplayName("Type Removal Tests")
  class TypeRemovalTests {

    @Test
    @DisplayName("should compile when type is removed from @ImportOptics")
    void shouldCompileWithRemovedType() {
      // Initial compilation with two types
      var record1 =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;
              public record Person(String name) {}
              """);

      var record2 =
          JavaFileObjects.forSourceString(
              "com.test.Address",
              """
              package com.test;
              public record Address(String city) {}
              """);

      var pkgInfoBoth = packageInfo("com.test.optics", "com.test.Person", "com.test.Address");

      var compilation1 = compile(record1, record2, pkgInfoBoth);
      assertThat(compilation1.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation1.generatedSourceFile("com.test.optics.PersonLenses")).isPresent();
      assertThat(compilation1.generatedSourceFile("com.test.optics.AddressLenses")).isPresent();

      // Recompile with only Person
      var pkgInfoOne = packageInfo("com.test.optics", "com.test.Person");

      var compilation2 = compile(record1, pkgInfoOne);
      assertThat(compilation2.status()).isEqualTo(Compilation.Status.SUCCESS);
      assertThat(compilation2.generatedSourceFile("com.test.optics.PersonLenses")).isPresent();
      // AddressLenses is not generated in this compilation (type was removed)
      assertThat(compilation2.generatedSourceFile("com.test.optics.AddressLenses")).isEmpty();
    }
  }

  // =============================================================================
  // Type Parameter Change Tests
  // =============================================================================

  @Nested
  @DisplayName("Type Parameter Change Tests")
  class TypeParameterChangeTests {

    @Test
    @DisplayName("should regenerate when type parameters change")
    void shouldRegenerateOnTypeParameterChange() {
      // First compilation - single type parameter
      var recordV1 =
          JavaFileObjects.forSourceString(
              "com.test.Container",
              """
              package com.test;
              public record Container<T>(T value) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Container");
      var compilation1 = compile(recordV1, pkgInfo);

      assertThat(compilation1.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated1 = getGeneratedContent(compilation1, "com.test.optics.ContainerLenses");
      assertThat(generated1).isNotNull();
      assertThat(generated1).contains("<T>");
      assertThat(generated1).doesNotContain("<T, U>");

      // Second compilation - two type parameters
      var recordV2 =
          JavaFileObjects.forSourceString(
              "com.test.Container",
              """
              package com.test;
              public record Container<T, U>(T first, U second) {}
              """);

      var compilation2 = compile(recordV2, pkgInfo);

      assertThat(compilation2.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated2 = getGeneratedContent(compilation2, "com.test.optics.ContainerLenses");
      assertThat(generated2).isNotNull();
      assertThat(generated2).contains("<T, U>");
      assertThat(generated2).contains("first()");
      assertThat(generated2).contains("second()");
    }

    @Test
    @DisplayName("should regenerate when type bounds change")
    void shouldRegenerateOnTypeBoundChange() {
      // First compilation - unbounded type
      var recordV1 =
          JavaFileObjects.forSourceString(
              "com.test.Bounded",
              """
              package com.test;
              public record Bounded<T>(T value) {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Bounded");
      var compilation1 = compile(recordV1, pkgInfo);

      assertThat(compilation1.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated1 = getGeneratedContent(compilation1, "com.test.optics.BoundedLenses");
      assertThat(generated1).isNotNull();

      // Second compilation - bounded type
      var recordV2 =
          JavaFileObjects.forSourceString(
              "com.test.Bounded",
              """
              package com.test;
              public record Bounded<T extends Number>(T value) {}
              """);

      var compilation2 = compile(recordV2, pkgInfo);

      assertThat(compilation2.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated2 = getGeneratedContent(compilation2, "com.test.optics.BoundedLenses");
      assertThat(generated2).isNotNull();
      assertThat(generated2).contains("extends Number");
    }
  }

  // =============================================================================
  // Sealed Interface Change Tests
  // =============================================================================

  @Nested
  @DisplayName("Sealed Interface Change Tests")
  class SealedInterfaceChangeTests {

    @Test
    @DisplayName("should regenerate when sealed variant is added")
    void shouldRegenerateOnVariantAddition() {
      // First compilation - two variants
      var sealedV1 =
          JavaFileObjects.forSourceString(
              "com.test.Shape",
              """
              package com.test;
              public sealed interface Shape permits Circle, Rectangle {}
              """);

      var circle =
          JavaFileObjects.forSourceString(
              "com.test.Circle",
              """
              package com.test;
              public record Circle(double radius) implements Shape {}
              """);

      var rectangle =
          JavaFileObjects.forSourceString(
              "com.test.Rectangle",
              """
              package com.test;
              public record Rectangle(double width, double height) implements Shape {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Shape");
      var compilation1 = compile(sealedV1, circle, rectangle, pkgInfo);

      assertThat(compilation1.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated1 = getGeneratedContent(compilation1, "com.test.optics.ShapePrisms");
      assertThat(generated1).isNotNull();
      assertThat(generated1).contains("circle()");
      assertThat(generated1).contains("rectangle()");
      assertThat(generated1).doesNotContain("triangle()");

      // Second compilation - added variant
      var sealedV2 =
          JavaFileObjects.forSourceString(
              "com.test.Shape",
              """
              package com.test;
              public sealed interface Shape permits Circle, Rectangle, Triangle {}
              """);

      var triangle =
          JavaFileObjects.forSourceString(
              "com.test.Triangle",
              """
              package com.test;
              public record Triangle(double base, double height) implements Shape {}
              """);

      var compilation2 = compile(sealedV2, circle, rectangle, triangle, pkgInfo);

      assertThat(compilation2.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated2 = getGeneratedContent(compilation2, "com.test.optics.ShapePrisms");
      assertThat(generated2).isNotNull();
      assertThat(generated2).contains("circle()");
      assertThat(generated2).contains("rectangle()");
      assertThat(generated2).contains("triangle()");
    }

    @Test
    @DisplayName("should regenerate when sealed variant is removed")
    void shouldRegenerateOnVariantRemoval() {
      // First compilation - three variants
      var sealedV1 =
          JavaFileObjects.forSourceString(
              "com.test.Status",
              """
              package com.test;
              public sealed interface Status permits Pending, Active, Completed {}
              """);

      var pending =
          JavaFileObjects.forSourceString(
              "com.test.Pending",
              """
              package com.test;
              public record Pending() implements Status {}
              """);

      var active =
          JavaFileObjects.forSourceString(
              "com.test.Active",
              """
              package com.test;
              public record Active(String message) implements Status {}
              """);

      var completed =
          JavaFileObjects.forSourceString(
              "com.test.Completed",
              """
              package com.test;
              public record Completed(java.time.Instant timestamp) implements Status {}
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Status");
      var compilation1 = compile(sealedV1, pending, active, completed, pkgInfo);

      assertThat(compilation1.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated1 = getGeneratedContent(compilation1, "com.test.optics.StatusPrisms");
      assertThat(generated1).isNotNull();
      assertThat(generated1).contains("pending()");
      assertThat(generated1).contains("active()");
      assertThat(generated1).contains("completed()");

      // Second compilation - removed variant
      var sealedV2 =
          JavaFileObjects.forSourceString(
              "com.test.Status",
              """
              package com.test;
              public sealed interface Status permits Pending, Active {}
              """);

      var compilation2 = compile(sealedV2, pending, active, pkgInfo);

      assertThat(compilation2.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated2 = getGeneratedContent(compilation2, "com.test.optics.StatusPrisms");
      assertThat(generated2).isNotNull();
      assertThat(generated2).contains("pending()");
      assertThat(generated2).contains("active()");
      assertThat(generated2).doesNotContain("completed()");
    }
  }

  // =============================================================================
  // Enum Change Tests
  // =============================================================================

  @Nested
  @DisplayName("Enum Change Tests")
  class EnumChangeTests {

    @Test
    @DisplayName("should regenerate when enum constant is added")
    void shouldRegenerateOnEnumConstantAddition() {
      // First compilation - three constants
      var enumV1 =
          JavaFileObjects.forSourceString(
              "com.test.Priority",
              """
              package com.test;
              public enum Priority { LOW, MEDIUM, HIGH }
              """);

      var pkgInfo = packageInfo("com.test.optics", "com.test.Priority");
      var compilation1 = compile(enumV1, pkgInfo);

      assertThat(compilation1.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated1 = getGeneratedContent(compilation1, "com.test.optics.PriorityPrisms");
      assertThat(generated1).isNotNull();
      assertThat(generated1).contains("low()");
      assertThat(generated1).contains("medium()");
      assertThat(generated1).contains("high()");
      assertThat(generated1).doesNotContain("critical()");

      // Second compilation - added constant
      var enumV2 =
          JavaFileObjects.forSourceString(
              "com.test.Priority",
              """
              package com.test;
              public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }
              """);

      var compilation2 = compile(enumV2, pkgInfo);

      assertThat(compilation2.status()).isEqualTo(Compilation.Status.SUCCESS);
      String generated2 = getGeneratedContent(compilation2, "com.test.optics.PriorityPrisms");
      assertThat(generated2).isNotNull();
      assertThat(generated2).contains("low()");
      assertThat(generated2).contains("medium()");
      assertThat(generated2).contains("high()");
      assertThat(generated2).contains("critical()");
    }
  }
}
