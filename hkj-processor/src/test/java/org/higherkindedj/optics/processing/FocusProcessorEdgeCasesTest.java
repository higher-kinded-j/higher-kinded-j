// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContainsRaw;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Edge case tests for FocusProcessor targeting uncovered branches in analyseFieldType,
 * buildWideningChainExpression, computeComposedPathClass, getPathDescription, and getPathGetMethod.
 */
@DisplayName("FocusProcessor Edge Cases")
class FocusProcessorEdgeCasesTest {

  private static final JavaFileObject NULLABLE_ANNOTATION =
      JavaFileObjects.forSourceString(
          "org.jspecify.annotations.Nullable",
          """
          package org.jspecify.annotations;
          import java.lang.annotation.*;
          @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.FIELD,
                   ElementType.RECORD_COMPONENT})
          @Retention(RetentionPolicy.RUNTIME)
          public @interface Nullable {}
          """);

  @Nested
  @DisplayName("@Nullable Field Widening Precedence")
  class NullableFieldPrecedence {

    @Test
    @DisplayName(
        "@Nullable on Optional field should still produce AffinePath via Optional widening")
    void nullableOptionalShouldUseOptionalWidening() {
      // Optional takes precedence over @Nullable
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Order",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record Order(String id, @Nullable Optional<String> note) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(NULLABLE_ANNOTATION, sourceFile);

      assertThat(compilation).succeeded();

      // Optional widening takes precedence, so it should be AffinePath via .some()
      assertGeneratedCodeContains(
          compilation, "com.example.OrderFocus", "AffinePath<Order, String> note()");
    }

    @Test
    @DisplayName(
        "@Nullable on non-container declared type should produce AffinePath via .nullable()")
    void nullableOnDeclaredTypeShouldProduceAffinePath() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record Config(String name, @Nullable String description) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(NULLABLE_ANNOTATION, sourceFile);

      assertThat(compilation).succeeded();

      assertGeneratedCodeContains(
          compilation, "com.example.ConfigFocus", "AffinePath<Config, String> description()");
      // Verify the .nullable() call is used
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", ".nullable()");
    }
  }

  @Nested
  @DisplayName("Three-Level Nested Container Widening")
  class ThreeLevelNesting {

    @Test
    @DisplayName("should handle Optional<List<Optional<String>>> as TraversalPath")
    void shouldHandleThreeLevelNesting() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Form",
              """
              package com.example;
              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Form(String title, Optional<List<Optional<String>>> data) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Affine + Traversal + Affine = Traversal
      assertGeneratedCodeContains(
          compilation, "com.example.FormFocus", "TraversalPath<Form, String> data()");
    }

    @Test
    @DisplayName("should handle List<List<List<String>>> as TraversalPath")
    void shouldHandleTripleListNesting() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Matrix",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Matrix(String name, List<List<List<String>>> data) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Traversal + Traversal + Traversal = Traversal
      assertGeneratedCodeContains(
          compilation, "com.example.MatrixFocus", "TraversalPath<Matrix, String> data()");
    }
  }

  @Nested
  @DisplayName("Javadoc Path Description for Nested Types")
  class NestedPathJavadoc {

    @Test
    @DisplayName("should use getAll in Javadoc for nested TraversalPath")
    void shouldUseGetAllInJavadocForNestedTraversal() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;
              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Config(String name, Optional<List<String>> items) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Javadoc should reference getAll for TraversalPath
      assertGeneratedCodeContainsRaw(compilation, "com.example.ConfigFocus", ".getAll(instance)");
    }

    @Test
    @DisplayName("should use getOptional in Javadoc for nested AffinePath")
    void shouldUseGetOptionalInJavadocForNestedAffine() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Wrapper",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Wrapper(String name, Optional<Optional<String>> nested) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Javadoc should reference getOptional for AffinePath
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.WrapperFocus", ".getOptional(instance)");
    }
  }

  @Nested
  @DisplayName("Multiple Component Records")
  class MultipleComponentRecords {

    @Test
    @DisplayName("should generate correct methods for record with many different field types")
    void shouldHandleManyFieldTypes() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;
              import java.util.List;
              import java.util.Optional;
              import java.util.Set;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Config(
                  String name,
                  int count,
                  Optional<String> description,
                  List<String> tags,
                  Set<String> categories,
                  boolean active) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Standard fields
      assertGeneratedCodeContains(
          compilation, "com.example.ConfigFocus", "FocusPath<Config, String> name()");
      assertGeneratedCodeContains(
          compilation, "com.example.ConfigFocus", "FocusPath<Config, Integer> count()");
      assertGeneratedCodeContains(
          compilation, "com.example.ConfigFocus", "FocusPath<Config, Boolean> active()");
      // Optional widened
      assertGeneratedCodeContains(
          compilation, "com.example.ConfigFocus", "AffinePath<Config, String> description()");
      // Collection widened
      assertGeneratedCodeContains(
          compilation, "com.example.ConfigFocus", "TraversalPath<Config, String> tags()");
      assertGeneratedCodeContains(
          compilation, "com.example.ConfigFocus", "TraversalPath<Config, String> categories()");
    }
  }

  @Nested
  @DisplayName("Generic Record with Widening")
  class GenericRecordWidening {

    @Test
    @DisplayName("should generate AffinePath for Optional field in generic record")
    void shouldWidenOptionalInGenericRecord() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Wrapper",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Wrapper<T>(T value, Optional<T> optValue) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Standard field preserves generic type
      assertGeneratedCodeContains(
          compilation, "com.example.WrapperFocus", "FocusPath<Wrapper<T>, T> value()");
      // Optional widened for generic field
      assertGeneratedCodeContains(
          compilation, "com.example.WrapperFocus", "AffinePath<Wrapper<T>, T> optValue()");
    }

    @Test
    @DisplayName("should generate TraversalPath for List field in generic record")
    void shouldWidenListInGenericRecord() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Container",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Container<T>(String name, List<T> items) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      assertGeneratedCodeContains(
          compilation, "com.example.ContainerFocus", "TraversalPath<Container<T>, T> items()");
    }
  }

  @Nested
  @DisplayName("Navigator createNavigatorMethod Filtered Out")
  class NavigatorFilteredOut {

    @Test
    @DisplayName("should not generate navigator method when field is excluded by includeFields")
    void shouldNotGenerateNavigatorForExcludedField() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Detail",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Detail(String info) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, includeFields = {"name"})
              public record Outer(String name, Detail detail) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // 'detail' is excluded by includeFields, should get standard FocusPath
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "FocusPath<Outer, Detail> detail()");
    }
  }
}
