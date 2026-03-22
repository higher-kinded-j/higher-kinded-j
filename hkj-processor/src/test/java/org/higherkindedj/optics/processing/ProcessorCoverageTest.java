// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Coverage tests for various processors targeting missed branches.
 *
 * <p>Covers FocusProcessor, TraversalProcessor, IsoProcessor, ForComprehensionProcessor,
 * ImportOpticsProcessor, and other processor branch gaps identified by JaCoCo.
 */
@DisplayName("Processor Coverage Tests")
class ProcessorCoverageTest {

  @Nested
  @DisplayName("FocusProcessor edge cases")
  class FocusProcessorEdgeCases {

    @Test
    @DisplayName("should reject @GenerateFocus on non-record types")
    void shouldRejectOnNonRecord() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.NotARecord",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public class NotARecord {
                  private String name;
              }
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("can only be applied to records");
    }

    @Test
    @DisplayName("should use custom target package when specified")
    void shouldUseCustomTargetPackage() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Simple",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(targetPackage = "com.generated")
              public record Simple(String name) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.generated.SimpleFocus", "public final class SimpleFocus");
    }

    @Test
    @DisplayName("should handle includeFields filter")
    void shouldHandleIncludeFieldsFilter() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Filtered",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(includeFields = {"name"})
              public record Filtered(String name, int age, String email) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.FilteredFocus", "FocusPath<Filtered, String> name()");
    }

    @Test
    @DisplayName("should handle excludeFields filter")
    void shouldHandleExcludeFieldsFilter() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Excluded",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(excludeFields = {"email"})
              public record Excluded(String name, int age, String email) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should handle @Nullable annotated field with navigator widening")
    void shouldHandleNullableFieldWidening() {
      // @Nullable widening interacts with navigator generation.
      // When a field is both @Nullable and a @GenerateFocus record, the processor generates
      // a Navigator wrapper class for composition. The navigator internally manages the path.
      // Use custom @Nullable with RECORD_COMPONENT target for test environment detection.
      final JavaFileObject nullableAnnotation =
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

      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String value) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.NullableNav",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(generateNavigators = true)
              public record NullableNav(String name, @Nullable Inner inner) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(nullableAnnotation, innerSource, outerSource);

      assertThat(compilation).succeeded();
      // @Nullable record field with @GenerateFocus produces a Navigator class for composition
      assertGeneratedCodeContains(
          compilation, "com.example.NullableNavFocus", "InnerNavigator<NullableNav> inner()");
    }
  }

  @Nested
  @DisplayName("FocusProcessor nested container widening")
  class FocusProcessorNestedWidening {

    @Test
    @DisplayName("should handle @Nullable field with String type")
    void shouldHandleNullableStringField() {
      // Use custom @Nullable with RECORD_COMPONENT target to ensure detection in test environment
      final var nullableAnnotation =
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

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.NullableStr",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record NullableStr(String name, @Nullable String label) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(nullableAnnotation, sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.NullableStrFocus", "AffinePath<NullableStr, String> label()");
    }

    @Test
    @DisplayName("should handle @Nullable on array field (non-declared type)")
    void shouldHandleNullableArrayField() {
      // Use custom @Nullable with RECORD_COMPONENT target to ensure detection in test environment
      final var nullableAnnotation =
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

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.NullableArr",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record NullableArr(String name, @Nullable int[] values) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(nullableAnnotation, sourceFile);

      assertThat(compilation).succeeded();
      // @Nullable on a non-declared type (array) should produce AffinePath with boxed type
      assertGeneratedCodeContains(
          compilation, "com.example.NullableArrFocus", "AffinePath<NullableArr,");
    }

    @Test
    @DisplayName("should handle @Nullable on primitive int field (non-declared, non-array type)")
    void shouldHandleNullablePrimitiveIntField() {
      // Use custom @Nullable with RECORD_COMPONENT target to ensure detection in test environment
      final var nullableAnnotation =
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

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.NullablePrim",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record NullablePrim(String name, @Nullable int score) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(nullableAnnotation, sourceFile);

      assertThat(compilation).succeeded();
      // @Nullable on a primitive type should produce AffinePath with boxed type
      assertGeneratedCodeContains(
          compilation, "com.example.NullablePrimFocus", "AffinePath<NullablePrim,");
    }

    @Test
    @DisplayName("should handle @Nullable on primitive double field")
    void shouldHandleNullablePrimitiveDoubleField() {
      final var nullableAnnotation =
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

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.NullableDbl",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record NullableDbl(String name, @Nullable double value) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(nullableAnnotation, sourceFile);

      assertThat(compilation).succeeded();
      // @Nullable on a primitive double should produce AffinePath with boxed type
      assertGeneratedCodeContains(
          compilation, "com.example.NullableDblFocus", "AffinePath<NullableDbl,");
    }

    @Test
    @DisplayName("should handle Optional<Optional<String>> nested containers")
    void shouldHandleOptionalOfOptional() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.DoubleOpt",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              @GenerateFocus
              public record DoubleOpt(Optional<Optional<String>> nested) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.DoubleOptFocus", "AffinePath<DoubleOpt, String> nested()");
    }

    @Test
    @DisplayName("should handle List<List<String>> nested containers")
    void shouldHandleListOfList() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.DoubleList",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;
              @GenerateFocus
              public record DoubleList(List<List<String>> matrix) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.DoubleListFocus", "TraversalPath<DoubleList, String> matrix()");
    }

    @Test
    @DisplayName("should handle Set<String> field")
    void shouldHandleSetField() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.SetHolder",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Set;
              @GenerateFocus
              public record SetHolder(String name, Set<String> tags) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.SetHolderFocus", "TraversalPath<SetHolder, String> tags()");
    }

    @Test
    @DisplayName("should handle Optional<List<navigable>> nested container with navigators")
    void shouldHandleOptionalListNavigable() {
      final JavaFileObject itemSource =
          JavaFileObjects.forSourceString(
              "com.example.Item",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Item(String name, int price) {}
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Cart",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              import java.util.List;
              @GenerateFocus(widenCollections = true)
              public record Cart(String id, Optional<List<String>> itemIds) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(itemSource, sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.CartFocus", "TraversalPath<Cart, String> itemIds()");
    }
  }

  @Nested
  @DisplayName("TraversalProcessor edge cases")
  class TraversalProcessorEdgeCases {

    @Test
    @DisplayName("should reject @GenerateTraversals on non-record types")
    void shouldRejectOnNonRecord() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadTraversal",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              @GenerateTraversals
              public class BadTraversal {}
              """);

      Compilation compilation =
          javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
    }

    @Test
    @DisplayName("should skip raw type fields in traversal generation")
    void shouldSkipRawTypeFields() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.RawHolder",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              import java.util.List;
              @GenerateTraversals
              @SuppressWarnings("rawtypes")
              public record RawHolder(String name, List items) {}
              """);

      Compilation compilation =
          javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

      // Should succeed but skip the raw List field (no traversal generated for it)
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should handle record with object array field")
    void shouldHandleObjectArrayField() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.ArrayHolder",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              @GenerateTraversals
              public record ArrayHolder(String name, String[] values) {}
              """);

      Compilation compilation =
          javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.ArrayHolderTraversals", "Traversal<ArrayHolder, String>");
    }
  }

  @Nested
  @DisplayName("IsoProcessor edge cases")
  class IsoProcessorEdgeCases {

    @Test
    @DisplayName("should generate iso for valid single-field record method")
    void shouldGenerateIsoForSingleFieldRecord() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Wrapper",
              """
              package com.example;
              import org.higherkindedj.optics.Iso;
              import org.higherkindedj.optics.annotations.GenerateIsos;
              public record Wrapper(String value) {
                  @GenerateIsos
                  public static Iso<Wrapper, String> iso() { return null; }
              }
              """);

      Compilation compilation = javac().withProcessors(new IsoProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should report error for Iso with wrong number of type arguments")
    void shouldReportErrorForIsoWithWrongTypeArgs() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadIso",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateIsos;
              public class BadIso {
                  @GenerateIsos
                  @SuppressWarnings("rawtypes")
                  public static org.higherkindedj.optics.Iso iso() { return null; }
              }
              """);

      Compilation compilation = javac().withProcessors(new IsoProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("two type arguments");
    }
  }

  @Nested
  @DisplayName("ForComprehensionProcessor edge cases")
  class ForComprehensionEdgeCases {

    @Test
    @DisplayName("should reject @GenerateForComprehensions on non-package elements")
    void shouldRejectOnNonPackage() {
      // @GenerateForComprehensions has @Target(PACKAGE) so applying to a class
      // causes a compile error at the Java level
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadFor",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              @GenerateForComprehensions(minArity = 2, maxArity = 5)
              public class BadFor {}
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
    }

    @Test
    @DisplayName("should reject @GenerateForComprehensions with minArity < 2")
    void shouldRejectInvalidMinArity() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @org.higherkindedj.optics.annotations.GenerateForComprehensions(minArity = 1, maxArity = 5)
              package com.example;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("minArity must be >= 2");
    }

    @Test
    @DisplayName("should reject @GenerateForComprehensions with maxArity > 26")
    void shouldRejectInvalidMaxArity() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @org.higherkindedj.optics.annotations.GenerateForComprehensions(minArity = 2, maxArity = 27)
              package com.example;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("maxArity must be <= 26");
    }

    @Test
    @DisplayName("should reject @GenerateForComprehensions with maxArity < minArity")
    void shouldRejectMaxArityLessThanMinArity() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @org.higherkindedj.optics.annotations.GenerateForComprehensions(minArity = 5, maxArity = 3)
              package com.example;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("maxArity");
    }
  }

  @Nested
  @DisplayName("GetterProcessor edge cases")
  class GetterProcessorEdgeCases {

    @Test
    @DisplayName("should reject @GenerateGetters on non-record types")
    void shouldRejectOnNonRecord() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadGetter",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters
              public class BadGetter {
                  private String name;
              }
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
    }

    @Test
    @DisplayName("should generate getters for record with custom target package")
    void shouldGenerateGettersWithCustomTargetPackage() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.SimpleGetter",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters(targetPackage = "com.generated")
              public record SimpleGetter(String name, int count) {}
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("SetterProcessor edge cases")
  class SetterProcessorEdgeCases {

    @Test
    @DisplayName("should reject @GenerateSetters on non-record types")
    void shouldRejectOnNonRecord() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadSetter",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters
              public class BadSetter {
                  private String name;
              }
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
    }

    @Test
    @DisplayName("should generate setters for record with custom target package")
    void shouldGenerateSettersWithCustomTargetPackage() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.SimpleSetter",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters(targetPackage = "com.generated")
              public record SimpleSetter(String name, int count) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("FoldProcessor edge cases")
  class FoldProcessorEdgeCases {

    @Test
    @DisplayName("should reject @GenerateFolds on non-record types")
    void shouldRejectOnNonRecord() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadFold",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFolds;
              @GenerateFolds
              public class BadFold {
                  private String name;
              }
              """);

      Compilation compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
    }
  }

  @Nested
  @DisplayName("PrismProcessor edge cases")
  class PrismProcessorEdgeCases {

    @Test
    @DisplayName("should generate prisms for enum type")
    void shouldGeneratePrismsForEnumType() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.MyEnum",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GeneratePrisms;
              @GeneratePrisms
              public enum MyEnum { A, B, C }
              """);

      Compilation compilation = javac().withProcessors(new PrismProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.MyEnumPrisms", "Prism<MyEnum, MyEnum> a()");
    }

    @Test
    @DisplayName("should reject @GeneratePrisms on non-sealed types")
    void shouldRejectOnNonSealedType() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadPrism",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GeneratePrisms;
              @GeneratePrisms
              public class BadPrism {}
              """);

      Compilation compilation = javac().withProcessors(new PrismProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
    }
  }

  @Nested
  @DisplayName("ExternalLensGenerator with type parameters")
  class ExternalLensWithTypeParams {

    @Test
    @DisplayName("should generate lenses for generic record")
    void shouldGenerateLensesForGenericRecord() {
      final var source =
          JavaFileObjects.forSourceString(
              "com.example.GenericPair",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateLenses;
              @GenerateLenses
              public record GenericPair<A, B>(A first, B second) {}
              """);

      Compilation compilation = javac().withProcessors(new LensProcessor()).compile(source);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("ImportOpticsProcessor external class with wither and type parameters")
  class ImportOpticsWitherTypeParams {

    @Test
    @DisplayName("should generate lenses for wither class with type parameters")
    void shouldGenerateForWitherClassWithTypeParams() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.GenericBox",
              """
              package com.external;
              public class GenericBox<T> {
                  private final T content;
                  public GenericBox(T content) { this.content = content; }
                  public T getContent() { return content; }
                  public GenericBox<T> withContent(T content) {
                      return new GenericBox<>(content);
                  }
              }
              """);

      final var importAnnotation =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @org.higherkindedj.optics.annotations.ImportOptics({com.external.GenericBox.class})
              package com.myapp;
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalClass, importAnnotation);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should generate lenses for wither class with container field")
    void shouldGenerateForWitherClassWithContainerField() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.ListHolder",
              """
              package com.external;
              import java.util.List;
              public class ListHolder {
                  private final String name;
                  private final List<String> items;
                  public ListHolder(String name, List<String> items) {
                      this.name = name;
                      this.items = items;
                  }
                  public String getName() { return name; }
                  public List<String> getItems() { return items; }
                  public ListHolder withName(String name) {
                      return new ListHolder(name, this.items);
                  }
                  public ListHolder withItems(List<String> items) {
                      return new ListHolder(this.name, items);
                  }
              }
              """);

      final var importAnnotation =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @org.higherkindedj.optics.annotations.ImportOptics({com.external.ListHolder.class})
              package com.myapp;
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalClass, importAnnotation);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("LensProcessor edge cases")
  class LensProcessorEdgeCases {

    @Test
    @DisplayName("should reject @GenerateLenses on non-record types")
    void shouldRejectOnNonRecord() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadLens",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateLenses;
              @GenerateLenses
              public class BadLens {
                  private String name;
              }
              """);

      Compilation compilation = javac().withProcessors(new LensProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
    }

    @Test
    @DisplayName("should handle custom target package for lenses")
    void shouldHandleCustomTargetPackage() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.SimpleLens",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateLenses;
              @GenerateLenses(targetPackage = "com.generated")
              public record SimpleLens(String name, int count) {}
              """);

      Compilation compilation = javac().withProcessors(new LensProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.generated.SimpleLensLenses", "Lens<SimpleLens, String> name()");
    }
  }

  @Nested
  @DisplayName("FoldProcessor edge cases")
  class FoldProcessorEdgeCasesExtended {

    @Test
    @DisplayName("should handle raw Iterable field defaulting to Object element type")
    void shouldHandleRawIterableField() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.RawFold",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFolds;
              import java.util.List;
              @GenerateFolds
              @SuppressWarnings("rawtypes")
              public record RawFold(String name, List items) {}
              """);

      Compilation compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("Navigator with generic records")
  class NavigatorGenericRecords {

    @Test
    @DisplayName("should generate navigator for generic record with type parameters")
    void shouldGenerateNavigatorForGenericRecord() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String label) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.GenericOuter",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record GenericOuter<T>(T tag, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.GenericOuterFocus", "InnerNavigator");
    }
  }

  @Nested
  @DisplayName("FocusProcessor array field widening")
  class FocusProcessorArrayWidening {

    @Test
    @DisplayName("should handle String[] field generating FocusPath")
    void shouldHandleStringArrayField() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.ArrayWiden",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(widenCollections = true)
              public record ArrayWiden(String name, String[] tags) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      // Array fields without SPI generator produce a plain FocusPath with the array type
      assertGeneratedCodeContains(
          compilation, "com.example.ArrayWidenFocus", "FocusPath<ArrayWiden, String[]> tags()");
    }
  }

  @Nested
  @DisplayName("ImportOpticsProcessor edge cases")
  class ImportOpticsEdgeCases {

    @Test
    @DisplayName("should reject @ImportOptics on invalid element type")
    void shouldRejectOnInvalidElementType() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadImport",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.ImportOptics;
              public class BadImport {
                  @ImportOptics({String.class})
                  public String badField;
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(sourceFile);

      // The annotation target is PACKAGE and TYPE so this won't compile at annotation level
      assertThat(compilation).failed();
    }

    @Test
    @DisplayName("should handle interface with ImportOptics that is not a spec")
    void shouldHandleNonSpecInterface() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Simple",
              """
              package com.external;
              public record Simple(String value) {}
              """);

      final var importAnnotation =
          JavaFileObjects.forSourceString(
              "com.myapp.SimpleImporter",
              """
              package com.myapp;
              import org.higherkindedj.optics.annotations.ImportOptics;
              @ImportOptics({com.external.Simple.class})
              public interface SimpleImporter {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importAnnotation);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should handle @ImportOptics on class element")
    void shouldHandleImportOnClass() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Data",
              """
              package com.external;
              public record Data(String info) {}
              """);

      final var importAnnotation =
          JavaFileObjects.forSourceString(
              "com.myapp.DataImporter",
              """
              package com.myapp;
              import org.higherkindedj.optics.annotations.ImportOptics;
              @ImportOptics({com.external.Data.class})
              public class DataImporter {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importAnnotation);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("FocusProcessor with wildcard and raw type fields")
  class FocusProcessorWildcardEdgeCases {

    @Test
    @DisplayName("should handle wildcard type argument in List")
    void shouldHandleWildcardTypeArgument() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.WildcardHolder",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;
              @GenerateFocus(widenCollections = true)
              public record WildcardHolder(String name, List<?> items) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should handle raw List field without type arguments")
    void shouldHandleRawListField() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.RawListHolder",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;
              @GenerateFocus(widenCollections = true)
              @SuppressWarnings("rawtypes")
              public record RawListHolder(String name, List items) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should handle Map field with widenCollections")
    void shouldHandleMapFieldWidening() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.MapHolder",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Map;
              @GenerateFocus(widenCollections = true)
              public record MapHolder(String name, Map<String, Integer> data) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("Navigator with AffinePath delegate collision checks")
  class NavigatorAffinePathDelegates {

    @Test
    @DisplayName("should skip field colliding with AffinePath delegate 'getOptional'")
    void shouldSkipFieldCollidingWithAffineDelegate() {
      final JavaFileObject nullableAnnotation =
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

      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String getOptional, String value) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.AffineOuter",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(generateNavigators = true)
              public record AffineOuter(@Nullable Inner inner, String name) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(nullableAnnotation, innerSource, outerSource);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("TraversalProcessor with custom target package")
  class TraversalProcessorTargetPackage {

    @Test
    @DisplayName("should generate traversals in custom target package")
    void shouldGenerateInCustomTargetPackage() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.CustomPkg",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              import java.util.List;
              @GenerateTraversals(targetPackage = "com.generated")
              public record CustomPkg(String name, List<String> items) {}
              """);

      Compilation compilation =
          javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.generated.CustomPkgTraversals", "Traversal<CustomPkg, String>");
    }
  }
}
