// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.TypeName;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.optics.annotations.KindSemantics;
import org.higherkindedj.optics.processing.kind.KindFieldInfo;
import org.higherkindedj.optics.processing.kind.KindRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Phase 2 mutation killing tests targeting surviving mutations from the PIT report.
 *
 * <p>This test class targets mutations that survived in:
 *
 * <ul>
 *   <li>NavigatorClassGenerator (widen, getFieldPathKind, isNavigableType, buildViaStatement,
 *       addNavigationMethods, capitalise)
 *   <li>FoldProcessor (isIterableType, getElementType, targetPackage)
 *   <li>IsoProcessor (process, processMethod conditionals)
 *   <li>LensProcessor (targetPackage, capitalise)
 *   <li>PrismProcessor (targetPackage, sealed vs enum)
 *   <li>TraversalProcessor (targetPackage)
 *   <li>FocusProcessor (targetPackage, process return, analyseFieldType)
 *   <li>ForComprehensionProcessor (error diagnostics, validation)
 *   <li>NullableAnnotations (hasNullableAnnotation)
 *   <li>KindFieldInfo (of, parameterised factory methods)
 *   <li>KindRegistry.KindMapping (instance, factory)
 *   <li>TupleGenerator (generateMapAll)
 * </ul>
 */
@DisplayName("Mutation Killing Phase 2 Tests")
class MutationKillingPhase2Test {

  // =============================================================================
  // NavigatorClassGenerator - PathKind.widen() Tests
  // Targets: widen : removed conditional - replaced equality check
  // =============================================================================

  @Nested
  @DisplayName("Navigator PathKind Widen Tests")
  class NavigatorPathKindWidenTests {

    @Test
    @DisplayName("Optional field in navigable record should produce AffinePath navigator")
    void optionalFieldShouldProduceAffinePathNavigator() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Address(String street, String city) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.User",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record User(String name, Optional<Address> address) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.UserFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Optional field should produce AffinePath
      assertThat(code).contains("AffinePath");
    }

    @Test
    @DisplayName("List field in navigable record should produce TraversalPath navigator")
    void listFieldShouldProduceTraversalPathNavigator() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Address(String street, String city) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Company(String name, List<Address> addresses) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.CompanyFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Collection field should produce TraversalPath
      assertThat(code).contains("TraversalPath");
    }

    @Test
    @DisplayName("Regular navigable field should produce FocusPath navigator")
    void regularFieldShouldProduceFocusPathNavigator() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Address(String street, String city) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Person(String name, Address address) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PersonFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Regular field should use FocusPath in navigator
      assertThat(code).contains("FocusPath");
      assertThat(code).contains("Navigator");
    }

    @Test
    @DisplayName("Set field should produce TraversalPath")
    void setFieldShouldProduceTraversalPath() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Tags",
              """
              package com.example;
              import java.util.Set;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Tags(Set<String> values) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.TagsFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Set<String> should be detected as a collection path
      assertThat(code).contains("TraversalPath");
    }

    @Test
    @DisplayName("Depth limiting should prevent deep navigator nesting")
    void depthLimitingShouldPreventDeepNesting() throws IOException {
      var level2 =
          JavaFileObjects.forSourceString(
              "com.example.Level2",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Level2(String value) {}
              """);
      var level1 =
          JavaFileObjects.forSourceString(
              "com.example.Level1",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 1)
              public record Level1(Level2 nested) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(level2, level1);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.Level1Focus")
              .get()
              .getCharContent(true)
              .toString();

      // With maxNavigatorDepth=1, navigators should be generated but not deeply nested
      assertThat(code).contains("Navigator");
    }
  }

  // =============================================================================
  // FoldProcessor Tests - isIterableType, getElementType, targetPackage
  // Targets: isIterableType conditionals, getElementType conditionals, targetPackage
  // =============================================================================

  @Nested
  @DisplayName("FoldProcessor Mutation Killing Tests")
  class FoldProcessorMutationTests {

    @Test
    @DisplayName("Fold with custom target package should respect targetPackage")
    void foldWithCustomTargetPackage() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Item",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateFolds;
              @GenerateFolds(targetPackage = "com.generated")
              public record Item(String name) {}
              """);

      Compilation compilation = javac().withProcessors(new FoldProcessor()).compile(source);

      assertThat(compilation).succeeded();
      // Must verify that the file is generated in the custom package
      Assertions.assertThat(
              compilation.generatedSourceFile("com.generated.ItemFolds"))
          .isPresent();
    }

    @Test
    @DisplayName("Fold with Set field should detect Iterable and generate correct fold")
    void foldWithSetFieldShouldDetectIterable() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Tags",
              """
              package com.test;
              import java.util.Set;
              import org.higherkindedj.optics.annotations.GenerateFolds;
              @GenerateFolds
              public record Tags(Set<String> values) {}
              """);

      Compilation compilation = javac().withProcessors(new FoldProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.TagsFolds")
              .get()
              .getCharContent(true)
              .toString();

      // Set is Iterable - should generate fold for elements
      assertThat(code).contains("values()");
      assertThat(code).contains("String");
    }

    @Test
    @DisplayName("Fold with raw List field should use Object element type")
    void foldWithRawListShouldUseObject() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.RawData",
              """
              package com.test;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFolds;
              @SuppressWarnings("rawtypes")
              @GenerateFolds
              public record RawData(List items) {}
              """);

      Compilation compilation = javac().withProcessors(new FoldProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.RawDataFolds")
              .get()
              .getCharContent(true)
              .toString();

      // Raw list should fallback to Object element type
      assertThat(code).contains("Object");
    }

    @Test
    @DisplayName("Fold with non-iterable field should generate scalar fold")
    void foldWithNonIterableFieldShouldGenerateScalarFold() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Point",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateFolds;
              @GenerateFolds
              public record Point(int x, int y) {}
              """);

      Compilation compilation = javac().withProcessors(new FoldProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.PointFolds")
              .get()
              .getCharContent(true)
              .toString();

      // Non-iterable int fields should box to Integer
      assertThat(code).contains("Fold<Point, Integer>");
    }

    @Test
    @DisplayName("Fold on non-record should fail")
    void foldOnNonRecordShouldFail() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.BadFold",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateFolds;
              @GenerateFolds
              public class BadFold {
                  private String name;
              }
              """);

      Compilation compilation = javac().withProcessors(new FoldProcessor()).compile(source);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("records");
    }
  }

  // =============================================================================
  // IsoProcessor Tests - process conditionals, processMethod targetPackage
  // Targets: process : replaced equality/boolean, processMethod : targetPackage
  // =============================================================================

  @Nested
  @DisplayName("IsoProcessor Mutation Killing Tests")
  class IsoProcessorMutationTests {

    @Test
    @DisplayName("Iso on method should generate Isos class")
    void isoOnMethodShouldGenerate() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Conversions",
              """
              package com.test;
              import org.higherkindedj.optics.Iso;
              import org.higherkindedj.optics.annotations.GenerateIsos;

              public class Conversions {
                  @GenerateIsos
                  public static Iso<String, Integer> stringToInt() {
                      return Iso.of(Integer::parseInt, String::valueOf);
                  }
              }
              """);

      Compilation compilation = javac().withProcessors(new IsoProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.ConversionsIsos")
              .get()
              .getCharContent(true)
              .toString();

      assertThat(code).contains("stringToInt");
      assertThat(code).contains("Iso<String, Integer>");
    }

    @Test
    @DisplayName("Iso with custom target package should use the target package")
    void isoWithCustomTargetPackage() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Conversions",
              """
              package com.test;
              import org.higherkindedj.optics.Iso;
              import org.higherkindedj.optics.annotations.GenerateIsos;

              public class Conversions {
                  @GenerateIsos(targetPackage = "com.generated")
                  public static Iso<String, Integer> stringToInt() {
                      return Iso.of(Integer::parseInt, String::valueOf);
                  }
              }
              """);

      Compilation compilation = javac().withProcessors(new IsoProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.generated.ConversionsIsos"))
          .isPresent();
    }

    @Test
    @DisplayName("Iso on non-method should be ignored (no error)")
    void isoOnNonMethodShouldBeIgnored() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Empty",
              """
              package com.test;
              public class Empty {}
              """);

      // When there are no annotated methods, compilation should just succeed
      Compilation compilation = javac().withProcessors(new IsoProcessor()).compile(source);

      assertThat(compilation).succeeded();
    }
  }

  // =============================================================================
  // LensProcessor Tests - targetPackage, capitalise conditionals
  // Targets: generateLensesFile : targetPackage, createWithMethod : typeArguments
  // =============================================================================

  @Nested
  @DisplayName("LensProcessor Mutation Killing Tests")
  class LensProcessorMutationTests {

    @Test
    @DisplayName("Lens with custom target package should use targetPackage")
    void lensWithCustomTargetPackage() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Point",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateLenses;
              @GenerateLenses(targetPackage = "com.generated")
              public record Point(int x, int y) {}
              """);

      Compilation compilation = javac().withProcessors(new LensProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.generated.PointLenses"))
          .isPresent();
    }

    @Test
    @DisplayName("Lens on generic record with method should use explicit type args")
    void lensOnGenericRecordWithMethodShouldUseTypeArgs() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Pair",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateLenses;
              @GenerateLenses
              public record Pair<A, B>(A first, B second) {}
              """);

      Compilation compilation = javac().withProcessors(new LensProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.PairLenses")
              .get()
              .getCharContent(true)
              .toString();

      // Generic with methods should use explicit type args: PairLenses.<A, B>first()
      assertThat(code).contains("PairLenses.<A, B>");
      assertThat(code).contains("withFirst(");
      assertThat(code).contains("withSecond(");
    }

    @Test
    @DisplayName("Lens on non-generic record with method should call directly")
    void lensOnNonGenericShouldCallDirectly() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Simple",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateLenses;
              @GenerateLenses
              public record Simple(String value) {}
              """);

      Compilation compilation = javac().withProcessors(new LensProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.SimpleLenses")
              .get()
              .getCharContent(true)
              .toString();

      // Non-generic: with method calls lens directly
      assertThat(code).contains("value().set(");
    }

    @Test
    @DisplayName("Lens parameterized type should use ParameterizedTypeName")
    void lensParameterizedType() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Box",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateLenses;
              @GenerateLenses
              public record Box<T>(T content) {}
              """);

      Compilation compilation = javac().withProcessors(new LensProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.BoxLenses")
              .get()
              .getCharContent(true)
              .toString();

      // Should have type variable
      assertThat(code).contains("<T>");
    }
  }

  // =============================================================================
  // PrismProcessor Tests - targetPackage, sealed vs enum conditionals
  // Targets: generatePrismsFile : targetPackage, sealed modifier check
  // =============================================================================

  @Nested
  @DisplayName("PrismProcessor Mutation Killing Tests")
  class PrismProcessorMutationTests {

    @Test
    @DisplayName("Prism with custom target package should use targetPackage")
    void prismWithCustomTargetPackage() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Color",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GeneratePrisms;
              @GeneratePrisms(targetPackage = "com.generated")
              public enum Color { RED, GREEN, BLUE }
              """);

      Compilation compilation = javac().withProcessors(new PrismProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.generated.ColorPrisms"))
          .isPresent();
    }

    @Test
    @DisplayName("Sealed interface prism methods should use instanceof")
    void sealedInterfacePrismMethodsShouldUseInstanceof() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Animal",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GeneratePrisms;
              @GeneratePrisms
              public sealed interface Animal {
                  record Dog(String breed) implements Animal {}
                  record Cat(boolean indoor) implements Animal {}
              }
              """);

      Compilation compilation = javac().withProcessors(new PrismProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.AnimalPrisms")
              .get()
              .getCharContent(true)
              .toString();

      assertThat(code).contains("dog()");
      assertThat(code).contains("cat()");
      assertThat(code).contains("instanceof");
      assertThat(code).contains("@Generated");
      assertThat(code).contains("private AnimalPrisms()");
    }

    @Test
    @DisplayName("Enum prism methods should use equality comparison")
    void enumPrismMethodsShouldUseEquality() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Direction",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GeneratePrisms;
              @GeneratePrisms
              public enum Direction { NORTH, SOUTH, EAST, WEST }
              """);

      Compilation compilation = javac().withProcessors(new PrismProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.DirectionPrisms")
              .get()
              .getCharContent(true)
              .toString();

      // Enum uses == comparison
      assertThat(code).contains("source == Direction.NORTH");
      assertThat(code).contains("source == Direction.SOUTH");
      assertThat(code).contains("north()");
      assertThat(code).contains("south()");
      assertThat(code).contains("east()");
      assertThat(code).contains("west()");
    }
  }

  // =============================================================================
  // FocusProcessor Tests - targetPackage, analyseFieldType
  // Targets: generateFocusFile : targetPackage, process : boolean return
  // =============================================================================

  @Nested
  @DisplayName("FocusProcessor Mutation Killing Tests")
  class FocusProcessorMutationTests {

    @Test
    @DisplayName("Focus with custom target package should use targetPackage")
    void focusWithCustomTargetPackage() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Point",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(targetPackage = "com.generated")
              public record Point(int x, int y) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.generated.PointFocus"))
          .isPresent();
    }

    @Test
    @DisplayName("Focus on non-record should fail")
    void focusOnNonRecordShouldFail() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.NotRecord",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public class NotRecord {
                  private String name;
              }
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("records");
    }

    @Test
    @DisplayName("Focus on record with Optional field should detect optional type")
    void focusWithOptionalFieldShouldDetect() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Wrapper",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Wrapper(Optional<String> value) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.WrapperFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Optional field should produce AffinePath
      assertThat(code).contains("AffinePath");
    }

    @Test
    @DisplayName("Focus on record with List field should detect collection type")
    void focusWithListFieldShouldDetect() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Playlist",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Playlist(String name, List<String> songs) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PlaylistFocus")
              .get()
              .getCharContent(true)
              .toString();

      // List field should produce TraversalPath
      assertThat(code).contains("TraversalPath");
    }

    @Test
    @DisplayName("Focus generateNavigators=false should not generate navigator classes")
    void focusNavigatorsDisabledShouldNotGenerate() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Address(String street) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = false)
              public record Person(String name, Address address) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PersonFocus")
              .get()
              .getCharContent(true)
              .toString();

      // With generateNavigators=false, no navigator classes should be generated
      assertThat(code).doesNotContain("Navigator");
    }
  }

  // =============================================================================
  // NullableAnnotations Tests
  // Targets: hasNullableAnnotation : replaced boolean return with false
  //          lambda$hasNullableAnnotation$0 : replaced return value with ""
  // =============================================================================

  @Nested
  @DisplayName("NullableAnnotations Mutation Killing Tests")
  class NullableAnnotationsMutationTests {

    @Test
    @DisplayName("@Nullable field should widen to AffinePath")
    void nullableFieldShouldWidenToAffinePath() throws IOException {
      var annotationSource =
          JavaFileObjects.forSourceString(
              "org.jspecify.annotations.Nullable",
              """
              package org.jspecify.annotations;
              import java.lang.annotation.*;
              @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
              @Retention(RetentionPolicy.RUNTIME)
              public @interface Nullable {}
              """);
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;
              import org.jspecify.annotations.Nullable;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Person(String name, @Nullable String nickname) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(annotationSource, source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PersonFocus")
              .get()
              .getCharContent(true)
              .toString();

      // @Nullable should trigger AffinePath for the nullable field
      assertThat(code).contains("AffinePath");
      // The nullable field method should use .nullable() to widen
      assertThat(code).contains("nullable()");
    }

    @Test
    @DisplayName("Non-nullable field should remain as FocusPath")
    void nonNullableFieldShouldRemainFocusPath() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Address(String street, String city) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Person(String name, Address address) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PersonFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Non-nullable should produce FocusPath navigator
      assertThat(code).contains("FocusPath");
    }
  }

  // =============================================================================
  // KindFieldInfo Factory Method Tests
  // Targets: of : replaced return value with null, parameterised : replaced return with null
  // =============================================================================

  @Nested
  @DisplayName("KindFieldInfo Factory Method Tests")
  class KindFieldInfoFactoryTests {

    @Test
    @DisplayName("KindFieldInfo.of should create non-parameterised info")
    void ofShouldCreateNonParameterisedInfo() {
      var info =
          KindFieldInfo.of(
              "org.higherkindedj.hkt.list.ListKind.Witness",
              TypeName.get(String.class),
              "ListTraverse.INSTANCE",
              KindSemantics.ZERO_OR_MORE);

      assertThat(info).isNotNull();
      assertThat(info.witnessType()).isEqualTo("org.higherkindedj.hkt.list.ListKind.Witness");
      assertThat(info.traverseExpression()).isEqualTo("ListTraverse.INSTANCE");
      assertThat(info.isParameterised()).isFalse();
      assertThat(info.witnessTypeArgs()).isEmpty();
      assertThat(info.semantics())
          .isEqualTo(KindSemantics.ZERO_OR_MORE);
    }

    @Test
    @DisplayName("KindFieldInfo.parameterised should create parameterised info")
    void parameterisedShouldCreateParameterisedInfo() {
      var info =
          KindFieldInfo.parameterised(
              "org.higherkindedj.hkt.either.EitherKind.Witness",
              TypeName.get(Integer.class),
              "EitherTraverse.<String>instance()",
              KindSemantics.ZERO_OR_ONE,
              "String");

      assertThat(info).isNotNull();
      assertThat(info.witnessType()).isEqualTo("org.higherkindedj.hkt.either.EitherKind.Witness");
      assertThat(info.traverseExpression()).isEqualTo("EitherTraverse.<String>instance()");
      assertThat(info.isParameterised()).isTrue();
      assertThat(info.witnessTypeArgs()).isEqualTo("String");
      assertThat(info.semantics())
          .isEqualTo(KindSemantics.ZERO_OR_ONE);
    }
  }

  // =============================================================================
  // KindRegistry.KindMapping Tests
  // Targets: instance : replaced return with null, factory : replaced return with null
  // =============================================================================

  @Nested
  @DisplayName("KindRegistry KindMapping Tests")
  class KindRegistryMappingTests {

    @Test
    @DisplayName("lookup for ListKind should return non-null instance mapping")
    void lookupListKindShouldReturnInstanceMapping() {
      Optional<KindRegistry.KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.list.ListKind.Witness");

      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression()).contains("ListTraverse");
      assertThat(mapping.get().traverseExpression()).contains("INSTANCE");
      assertThat(mapping.get().isParameterised()).isFalse();
      assertThat(mapping.get().semantics())
          .isEqualTo(KindSemantics.ZERO_OR_MORE);
    }

    @Test
    @DisplayName("lookup for EitherKind should return non-null factory mapping")
    void lookupEitherKindShouldReturnFactoryMapping() {
      Optional<KindRegistry.KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.either.EitherKind.Witness");

      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression()).contains("EitherTraverse");
      assertThat(mapping.get().traverseExpression()).contains("instance()");
      assertThat(mapping.get().isParameterised()).isTrue();
      assertThat(mapping.get().semantics())
          .isEqualTo(KindSemantics.ZERO_OR_ONE);
    }

    @Test
    @DisplayName("lookup for all known kinds should return non-null")
    void lookupAllKnownKindsShouldReturnNonNull() {
      String[] knownKinds = {
        "org.higherkindedj.hkt.list.ListKind.Witness",
        "org.higherkindedj.hkt.maybe.MaybeKind.Witness",
        "org.higherkindedj.hkt.optional.OptionalKind.Witness",
        "org.higherkindedj.hkt.stream.StreamKind.Witness",
        "org.higherkindedj.hkt.trymonad.TryKind.Witness",
        "org.higherkindedj.hkt.id.IdKind.Witness",
        "org.higherkindedj.hkt.either.EitherKind.Witness",
        "org.higherkindedj.hkt.validated.ValidatedKind.Witness"
      };

      for (String kind : knownKinds) {
        assertThat(KindRegistry.lookup(kind))
            .as("Lookup for %s should be present", kind)
            .isPresent();
      }
    }

    @Test
    @DisplayName("lookup for unknown kind should return empty")
    void lookupUnknownKindShouldReturnEmpty() {
      assertThat(KindRegistry.lookup("com.unknown.SomeKind.Witness")).isEmpty();
    }
  }

  // =============================================================================
  // Navigator buildViaStatement Tests
  // Targets: buildViaStatement conditionals for path widening
  // =============================================================================

  @Nested
  @DisplayName("Navigator buildViaStatement Tests")
  class NavigatorBuildViaStatementTests {

    @Test
    @DisplayName(
        "Navigator with deeply nested navigable types should produce correct via statements")
    void navigatorWithDeeplyNestedShouldUseCorrectVia() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);
      var middle =
          JavaFileObjects.forSourceString(
              "com.example.Office",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Office(String name, Address address) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Company(String name, Office office) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(inner, middle, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.CompanyFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Should have navigator for Office with via delegation
      assertThat(code).contains("Navigator");
      assertThat(code).contains("delegate");
      // CompanyFocus should have an OfficeNavigator inner class
      assertThat(code).contains("OfficeNavigator");
      // The via statement for the delegate's fields should use toLens()
      assertThat(code).contains("delegate.via(OfficeFocus.");
      assertThat(code).contains(".toLens())");
      // Verify the navigator has methods for each field of the target record
      assertThat(code).contains("FocusPath<S, String>");

      // Also verify OfficeFocus generated code has AddressNavigator
      String officeCode =
          compilation
              .generatedSourceFile("com.example.OfficeFocus")
              .get()
              .getCharContent(true)
              .toString();
      assertThat(officeCode).contains("AddressNavigator");
      assertThat(officeCode).contains("delegate.via(AddressFocus.");
    }
  }

  // =============================================================================
  // SetterProcessor additional tests for capitalise edge cases
  // Targets: capitalise : removed conditional - replaced equality check
  // =============================================================================

  @Nested
  @DisplayName("SetterProcessor capitalise Edge Cases")
  class SetterProcessorCapitaliseTests {

    @Test
    @DisplayName("Single character field name should be capitalised correctly")
    void singleCharFieldNameShouldBeCapitalised() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.SingleChar",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters
              public record SingleChar(int x) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.test.SingleCharSetters")
              .get()
              .getCharContent(true)
              .toString();

      // "x" -> "withX"
      assertThat(code).contains("withX(");
    }
  }

  // =============================================================================
  // TupleGenerator mapAll Tests
  // Targets: generateTuple : removed call to generateMapAll
  //          generateMapAll : removed conditional - replaced comparison check
  // =============================================================================

  @Nested
  @DisplayName("TupleGenerator mapAll Tests")
  class TupleGeneratorMapAllTests {

    @Test
    @DisplayName("ForComprehension with arity 2 should generate mapAll method")
    void forComprehensionArity2ShouldGenerateMapAll() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.expression.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 3)
              package org.higherkindedj.hkt.expression;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).succeeded();

      // Check Tuple2 has map method for transforming all fields
      Optional<JavaFileObject> tuple2 =
          compilation.generatedSourceFile("org.higherkindedj.hkt.tuple.Tuple2");
      Assertions.assertThat(tuple2).isPresent();
      String code = tuple2.get().getCharContent(true).toString();
      // map() applies mappers to all tuple components
      assertThat(code).contains("mapFirst(");
      assertThat(code).contains("mapSecond(");
    }
  }

  // =============================================================================
  // TraversalProcessor custom target package
  // Targets: generateTraversalsFile : targetPackage
  // =============================================================================

  @Nested
  @DisplayName("TraversalProcessor targetPackage Tests")
  class TraversalProcessorTargetPackageTests {

    @Test
    @DisplayName("Traversal with default package should generate in same package")
    void traversalWithDefaultPackage() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Items",
              """
              package com.test;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              @GenerateTraversals
              public record Items(List<String> names) {}
              """);

      Compilation compilation = javac().withProcessors(new TraversalProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.test.ItemsTraversals"))
          .isPresent();
    }
  }

  // =============================================================================
  // Navigator with Collection subtype fields
  // Targets: getFieldPathKind : interface checking for Collection subtypes
  // =============================================================================

  @Nested
  @DisplayName("Navigator Collection Subtype Tests")
  class NavigatorCollectionSubtypeTests {

    @Test
    @DisplayName("Focus on record with Collection field should detect collection")
    void focusWithCollectionFieldShouldDetect() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Group",
              """
              package com.example;
              import java.util.Collection;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Group(String name, Collection<String> members) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.GroupFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Collection field should produce TraversalPath
      assertThat(code).contains("TraversalPath");
    }
  }

  // =============================================================================
  // Navigator includeFields / excludeFields tests
  // Targets: shouldGenerateNavigator conditionals
  // =============================================================================

  @Nested
  @DisplayName("Navigator Field Filtering Tests")
  class NavigatorFieldFilteringTests {

    @Test
    @DisplayName("includeFields should only generate for specified fields")
    void includeFieldsShouldFilter() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Address(String street, String city) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, includeFields = {"address"})
              public record Person(String name, Address address) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PersonFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Should include navigator for address
      assertThat(code).contains("AddressNavigator");
    }

    @Test
    @DisplayName("excludeFields should exclude specified fields from navigation")
    void excludeFieldsShouldFilter() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Address(String street, String city) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, excludeFields = {"address"})
              public record Person(String name, Address address) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PersonFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Should NOT have AddressNavigator since address is excluded
      assertThat(code).doesNotContain("AddressNavigator");
    }
  }

  // =============================================================================
  // GetterProcessor additional edge case tests
  // Targets: capitalise, createGetMethod conditionals
  // =============================================================================

  @Nested
  @DisplayName("GetterProcessor Mutation Killing Tests")
  class GetterProcessorMutationTests {

    @Test
    @DisplayName("Getter with custom target package should use targetPackage")
    void getterWithCustomTargetPackageInCode() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Data",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters(targetPackage = "com.custom")
              public record Data(String name) {}
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.custom.DataGetters"))
          .isPresent();

      String code =
          compilation
              .generatedSourceFile("com.custom.DataGetters")
              .get()
              .getCharContent(true)
              .toString();

      // Verify code in custom package
      assertThat(code).contains("package com.custom");
      assertThat(code).contains("@Generated");
    }
  }

  // =============================================================================
  // SetterProcessor additional edge case tests
  // Targets: targetPackage code in custom package
  // =============================================================================

  @Nested
  @DisplayName("SetterProcessor Mutation Killing Tests")
  class SetterProcessorMutationTests {

    @Test
    @DisplayName("Setter with custom target package should generate in correct package")
    void setterWithCustomTargetPackageInCode() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Data",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters(targetPackage = "com.custom")
              public record Data(String name) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.custom.DataSetters"))
          .isPresent();

      String code =
          compilation
              .generatedSourceFile("com.custom.DataSetters")
              .get()
              .getCharContent(true)
              .toString();

      // Verify code in custom package
      assertThat(code).contains("package com.custom");
      assertThat(code).contains("@Generated");
    }
  }

  // =============================================================================
  // FocusProcessor getPathDescription / getPathGetMethod Tests
  // Targets: EmptyObjectReturnValsMutator on lines 498, 507
  // Verifies Javadoc content in generated code reflects correct path descriptions
  // =============================================================================

  @Nested
  @DisplayName("FocusProcessor Javadoc Path Description Tests")
  class FocusProcessorJavadocTests {

    @Test
    @DisplayName("Optional field Javadoc should contain AffinePath description and getOptional")
    void optionalFieldJavadocShouldContainAffinePath() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Config(String name, Optional<String> description) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.ConfigFocus")
              .get()
              .getCharContent(true)
              .toString();

      // getPathDescription returns "AffinePath" for Optional - used in @return Javadoc
      assertThat(code).contains("AffinePath<Config, String>");
      // getPathGetMethod returns "getOptional" for Optional - used in example Javadoc
      assertThat(code).contains("getOptional");
    }

    @Test
    @DisplayName("Collection field Javadoc should contain TraversalPath description and getAll")
    void collectionFieldJavadocShouldContainTraversalPath() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Team",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Team(String name, List<String> members) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.TeamFocus")
              .get()
              .getCharContent(true)
              .toString();

      // getPathDescription returns "TraversalPath" for Collection - used in @return Javadoc
      assertThat(code).contains("TraversalPath<Team, String>");
      // getPathGetMethod returns "getAll" for Collection - used in example Javadoc
      assertThat(code).contains("getAll");
    }

    @Test
    @DisplayName("Regular field Javadoc should contain FocusPath description and get method")
    void regularFieldJavadocShouldContainFocusPath() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Label",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Label(String text) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.LabelFocus")
              .get()
              .getCharContent(true)
              .toString();

      // getPathDescription returns "FocusPath" for regular fields
      assertThat(code).contains("FocusPath<Label, String>");
      // getPathGetMethod returns "get" for regular fields - used in Javadoc examples
      assertThat(code).contains(".get(");
    }
  }

  // =============================================================================
  // Navigator isNavigableType Tests
  // Targets: BooleanTrueReturnValsMutator on line 678 (isNavigableType)
  // Verifies non-navigable types do NOT get navigator inner classes
  // =============================================================================

  @Nested
  @DisplayName("Navigator isNavigableType Tests")
  class NavigatorIsNavigableTypeTests {

    @Test
    @DisplayName("Non-navigable field types should not produce navigator inner classes")
    void nonNavigableFieldShouldNotProduceNavigator() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Address(String street, String city) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Person(String name, Address address) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PersonFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Address IS navigable (has @GenerateFocus) - should have navigator
      assertThat(code).contains("AddressNavigator");
      // String is NOT navigable - should NOT have a NameNavigator
      assertThat(code).doesNotContain("NameNavigator");
      assertThat(code).doesNotContain("StringNavigator");
    }
  }

  // =============================================================================
  // TupleGenerator generateMapAll VoidMethodCall Tests
  // Targets: VoidMethodCallMutator on line 126 (generateMapAll call removal)
  // Verifies the map-all method (mapping all tuple elements) is generated
  // =============================================================================

  @Nested
  @DisplayName("TupleGenerator generateMapAll Tests")
  class TupleGeneratorMapAllMethodTests {

    @Test
    @DisplayName("Tuple2 should have map() method that maps all elements with named mappers")
    void tuple2ShouldHaveMapAllMethod() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.expression.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 2)
              package org.higherkindedj.hkt.expression;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).succeeded();

      Optional<JavaFileObject> tuple2 =
          compilation.generatedSourceFile("org.higherkindedj.hkt.tuple.Tuple2");
      Assertions.assertThat(tuple2).isPresent();
      String code = tuple2.get().getCharContent(true).toString();

      // The map() method generated by generateMapAll uses named mappers: firstMapper, secondMapper
      assertThat(code).contains("firstMapper");
      assertThat(code).contains("secondMapper");
      // The map() method applies all mappers
      assertThat(code).contains("firstMapper.apply(_1)");
      assertThat(code).contains("secondMapper.apply(_2)");
    }

    @Test
    @DisplayName("Tuple3 should have map() method that maps all three elements")
    void tuple3ShouldHaveMapAllMethod() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.expression.package-info",
              """
              @GenerateForComprehensions(minArity = 3, maxArity = 3)
              package org.higherkindedj.hkt.expression;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).succeeded();

      Optional<JavaFileObject> tuple3 =
          compilation.generatedSourceFile("org.higherkindedj.hkt.tuple.Tuple3");
      Assertions.assertThat(tuple3).isPresent();
      String code = tuple3.get().getCharContent(true).toString();

      // The map() method for Tuple3 must include thirdMapper
      assertThat(code).contains("thirdMapper");
      assertThat(code).contains("thirdMapper.apply(_3)");
    }
  }

  // =============================================================================
  // ForComprehensionProcessor error() VoidMethodCall Tests
  // Targets: VoidMethodCallMutator on lines 84, 90, 96
  // Verifies error diagnostics are reported for invalid inputs
  // =============================================================================

  @Nested
  @DisplayName("ForComprehensionProcessor Error Diagnostic Tests")
  class ForComprehensionProcessorErrorTests {

    @Test
    @DisplayName("minArity < 2 should produce error diagnostic")
    void minArityLessThan2ShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.expression.package-info",
              """
              @GenerateForComprehensions(minArity = 1, maxArity = 3)
              package org.higherkindedj.hkt.expression;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("minArity must be >= 2");
    }

    @Test
    @DisplayName("maxArity < minArity should produce error diagnostic")
    void maxArityLessThanMinArityShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.expression.package-info",
              """
              @GenerateForComprehensions(minArity = 5, maxArity = 3)
              package org.higherkindedj.hkt.expression;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("maxArity");
    }

    @Test
    @DisplayName("maxArity > 26 should produce error diagnostic")
    void maxArityGreaterThan26ShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.expression.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 27)
              package org.higherkindedj.hkt.expression;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("maxArity must be <= 26");
    }

    @Test
    @DisplayName("Valid arity range should succeed and generate Tuple classes")
    void validArityRangeShouldSucceed() {
      var source =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.expression.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 3)
              package org.higherkindedj.hkt.expression;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).succeeded();
      // Verify Tuple2 and Tuple3 are generated
      Assertions.assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.tuple.Tuple2"))
          .isPresent();
      Assertions.assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.tuple.Tuple3"))
          .isPresent();
    }
  }

  // =============================================================================
  // Navigator Javadoc path description Tests
  // Targets: NavigatorClassGenerator.addNavigationMethods pathDescription switch
  // Verifies navigator method Javadoc reflects correct path type
  // =============================================================================

  @Nested
  @DisplayName("Navigator Path Description Javadoc Tests")
  class NavigatorPathDescriptionTests {

    @Test
    @DisplayName("Navigator method for regular field should have FocusPath in Javadoc")
    void navigatorRegularFieldJavadocShouldContainFocusPath() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Person(String name, Address address) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PersonFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Navigator methods for regular String fields should mention FocusPath in Javadoc
      assertThat(code).contains("a FocusPath focusing on");
      // The via statement should use toLens()
      assertThat(code).contains("toLens()");
    }
  }

  // =============================================================================
  // TraversalProcessor additional mutation tests
  // Targets: TraversalProcessor conditionals and error paths
  // =============================================================================

  @Nested
  @DisplayName("TraversalProcessor Additional Mutation Tests")
  class TraversalProcessorAdditionalTests {

    @Test
    @DisplayName("Traversal with custom targetPackage should generate in custom package")
    void traversalWithCustomTargetPackage() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Items",
              """
              package com.test;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              @GenerateTraversals(targetPackage = "com.custom")
              public record Items(List<String> names) {}
              """);

      Compilation compilation = javac().withProcessors(new TraversalProcessor()).compile(source);

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              compilation.generatedSourceFile("com.custom.ItemsTraversals"))
          .isPresent();
    }

    @Test
    @DisplayName("Traversal on non-record should fail")
    void traversalOnNonRecordShouldFail() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.BadTraversal",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              @GenerateTraversals
              public class BadTraversal {
                  private String name;
              }
              """);

      Compilation compilation = javac().withProcessors(new TraversalProcessor()).compile(source);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("records");
    }
  }
}
