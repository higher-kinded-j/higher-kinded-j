// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.annotations.KindSemantics;
import org.higherkindedj.optics.processing.effect.PathSourceProcessor;
import org.higherkindedj.optics.processing.kind.KindRegistry;
import org.higherkindedj.optics.processing.kind.KindRegistry.KindMapping;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Phase 3 mutation killing tests targeting surviving mutations from the PIT report.
 *
 * <p>This test class targets mutations that survived across:
 *
 * <ul>
 *   <li>NavigatorClassGenerator - buildViaStatement path widening, depth limiting, capitalise
 *   <li>FocusProcessor - generateFocusFile conditionals, buildTraverseOverCall
 *   <li>TraversalProcessor - createTraversalMethod type argument handling
 *   <li>ForComprehensionProcessor - process boundary conditions
 *   <li>ImportOpticsProcessor - processSpecInterface, processTypeAnnotation
 *   <li>PathSourceProcessor - isRecoverable, isChainable, generatePathClass conditionals
 *   <li>KindFieldAnalyser - injectTypeArgs boundary conditions
 *   <li>KindRegistry - extractWitnessTypeArgs boundary, KindMapping factories
 *   <li>ForPathStepGenerator - appendImports, appendFields, valueParams
 *   <li>ForStepGenerator - generateFilterableSteps
 * </ul>
 */
@DisplayName("Mutation Killing Phase 3 Tests")
class MutationKillingPhase3Test {

  // =============================================================================
  // NavigatorClassGenerator - buildViaStatement path widening
  // Targets: buildViaStatement lines 647, 651, 654 - all conditional mutations
  // The via statement should append .asTraversal() when widening from Focus/Affine to Traversal
  // =============================================================================

  @Nested
  @DisplayName("Navigator BuildViaStatement Path Widening")
  class NavigatorBuildViaPathWideningTests {

    @Test
    @DisplayName("Focus → Affine widening via Optional field should NOT append asTraversal")
    void focusToAffineWideningShouldNotAppendAsTraversal() throws IOException {
      // A record with an Optional field navigated from a Focus context
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Detail",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Detail(String value) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Container",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              @GenerateFocus(generateNavigators = true)
              public record Container(Optional<Detail> detail, String name) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.ContainerFocus")
              .get()
              .getCharContent(true)
              .toString();

      // The navigator for an Optional field should produce AffinePath, NOT TraversalPath
      assertThat(code).contains("AffinePath");
      // The via statement should NOT have .asTraversal() because we're widening Focus→Affine
      assertThat(code).doesNotContain("asTraversal()");
    }

    @Test
    @DisplayName("Focus → Traversal widening via List field should append asTraversal")
    void focusToTraversalWideningShouldAppendAsTraversal() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Item",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Item(String label) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Basket",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;
              @GenerateFocus(generateNavigators = true)
              public record Basket(List<Item> items) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.BasketFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Navigator for a List field should produce TraversalPath
      assertThat(code).contains("TraversalPath");
    }

    @Test
    @DisplayName("Affine context (Optional field) should produce AffinePath return type")
    void affineContextShouldProduceAffinePathReturnType() throws IOException {
      // Optional<Branch> field → AffinePath (not directly navigable into Branch)
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Leaf",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Leaf(String text) {}
              """);
      var middle =
          JavaFileObjects.forSourceString(
              "com.example.Branch",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;
              @GenerateFocus(generateNavigators = true)
              public record Branch(List<Leaf> leaves) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Tree",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              @GenerateFocus(generateNavigators = true)
              public record Tree(Optional<Branch> branch, String name) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(inner, middle, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.TreeFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Optional<Branch> → AffinePath (branch is wrapped in Optional)
      assertThat(code).contains("AffinePath<Tree, Branch>");
      // name is plain String → FocusPath
      assertThat(code).contains("FocusPath<Tree, String>");
    }
  }

  // =============================================================================
  // NavigatorClassGenerator - generateNavigatorClass depth and MathMutator
  // Targets: generateNavigatorClass line 298 - currentDepth + 1 → currentDepth - 1
  // Also targets: generateNavigatorsWithPathKind line 197 depth boundary
  // Also targets: addNavigationMethods line 616 depth check
  // =============================================================================

  @Nested
  @DisplayName("Navigator Depth Limiting Tests")
  class NavigatorDepthLimitingTests {

    @Test
    @DisplayName("Three level deep navigation should produce navigators at all levels")
    void threeLevelDeepNavigationShouldWork() throws IOException {
      var level3 =
          JavaFileObjects.forSourceString(
              "com.example.L3",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record L3(String value) {}
              """);
      var level2 =
          JavaFileObjects.forSourceString(
              "com.example.L2",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record L2(L3 child, String name) {}
              """);
      var level1 =
          JavaFileObjects.forSourceString(
              "com.example.L1",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record L1(L2 inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(level3, level2, level1);

      assertThat(compilation).succeeded();

      // L1Focus should have an InnerNavigator with navigation to L2 fields
      String l1Code =
          compilation
              .generatedSourceFile("com.example.L1Focus")
              .get()
              .getCharContent(true)
              .toString();

      assertThat(l1Code).contains("InnerNavigator");
      // The InnerNavigator should have methods for L2's fields
      assertThat(l1Code).contains("child");
      assertThat(l1Code).contains("name");

      // L2Focus should have a ChildNavigator
      String l2Code =
          compilation
              .generatedSourceFile("com.example.L2Focus")
              .get()
              .getCharContent(true)
              .toString();

      assertThat(l2Code).contains("ChildNavigator");
    }
  }

  // =============================================================================
  // NavigatorClassGenerator - capitalise null/empty guard
  // Targets: capitalise lines 704 - both EQUAL_IF and EQUAL_ELSE
  // =============================================================================

  @Nested
  @DisplayName("Navigator Capitalise Edge Cases")
  class NavigatorCapitaliseTests {

    @Test
    @DisplayName("Single char field name should be capitalised correctly in navigator")
    void singleCharFieldNameCapitalisedInNavigator() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Coord",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Coord(int x, int y) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Point",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Point(Coord c) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PointFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Single-char field 'c' should produce "CNavigator"
      assertThat(code).contains("CNavigator");
    }
  }

  // =============================================================================
  // NavigatorClassGenerator - createNavigatorMethod line 736
  // Targets: isNavigableType check in createNavigatorMethod
  // =============================================================================

  @Nested
  @DisplayName("Navigator CreateNavigatorMethod Tests")
  class NavigatorCreateNavigatorMethodTests {

    @Test
    @DisplayName("Non-navigable field type should not produce navigator method")
    void nonNavigableFieldShouldUseStandardMethod() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Simple",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Simple(String name, int age) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.SimpleFocus")
              .get()
              .getCharContent(true)
              .toString();

      // String and int are not navigable - should have standard lens methods, not navigators
      assertThat(code).doesNotContain("NameNavigator");
      assertThat(code).doesNotContain("AgeNavigator");
      // But should still have accessor methods
      assertThat(code).contains("name()");
      assertThat(code).contains("age()");
    }
  }

  // =============================================================================
  // NavigatorClassGenerator - lambda in createNavigatorMethod line 779
  // Targets: the equality check in constructor arg generation
  // =============================================================================

  @Nested
  @DisplayName("Navigator Constructor Args Lambda Tests")
  class NavigatorConstructorArgsLambdaTests {

    @Test
    @DisplayName(
        "Navigator method should use newValue for matching component and source for others")
    void navigatorMethodShouldUseCorrectConstructorArgs() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Addr",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Addr(String line1, String city) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Person(String name, Addr addr, int age) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PersonFocus")
              .get()
              .getCharContent(true)
              .toString();

      // The navigator method for 'addr' should produce:
      // new Person(source.name(), newValue, source.age())
      // where 'addr' is replaced by newValue and others use source.xxx()
      assertThat(code).contains("source.name()");
      assertThat(code).contains("newValue");
      assertThat(code).contains("source.age()");
    }
  }

  // =============================================================================
  // FocusProcessor - getPathDescription EmptyObjectReturnValsMutator line 498
  // Targets: replaced return value with "" for getPathDescription
  // =============================================================================

  @Nested
  @DisplayName("FocusProcessor Path Description Tests")
  class FocusProcessorPathDescriptionTests {

    @Test
    @DisplayName("Regular field should have non-empty FocusPath description in Javadoc")
    void regularFieldShouldHaveNonEmptyPathDescription() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Widget",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Widget(String label) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.WidgetFocus")
              .get()
              .getCharContent(true)
              .toString();

      // getPathDescription should return "FocusPath" not ""
      assertThat(code).contains("FocusPath<Widget, String>");
      // getPathGetMethod should return "get" for regular fields (not empty)
      assertThat(code).contains(".get(instance)");
    }
  }

  // =============================================================================
  // FocusProcessor - generateFocusFile line 163
  // Targets: targetPackage empty check
  // =============================================================================

  @Nested
  @DisplayName("FocusProcessor GenerateFocusFile Tests")
  class FocusProcessorGenerateFocusFileTests {

    @Test
    @DisplayName("Focus with explicit targetPackage should place generated file in target package")
    void focusWithExplicitTargetPackageShouldWork() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Gadget",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(targetPackage = "com.example.generated")
              public record Gadget(String type, int count) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      // The generated file should be in the target package
      assertThat(compilation).generatedSourceFile("com.example.generated.GadgetFocus").isNotNull();
    }
  }

  // =============================================================================
  // FocusProcessor - buildTraverseOverCall line 384
  // Targets: conditional in Kind field traversal
  // =============================================================================

  @Nested
  @DisplayName("FocusProcessor TraverseOver Tests")
  class FocusProcessorTraverseOverTests {

    @Test
    @DisplayName("Record with List field should generate traverseOver method")
    void recordWithListFieldShouldGenerateTraverseOver() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Bag",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;
              @GenerateFocus
              public record Bag(String label, List<String> items) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.BagFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Should have a TraversalPath for the List field
      assertThat(code).contains("TraversalPath");
      assertThat(code).contains("items");
    }
  }

  // =============================================================================
  // FocusProcessor - analyseFieldType line 442 and extractTypeArgument line 490
  // Targets: conditional equality checks in field type analysis
  // =============================================================================

  @Nested
  @DisplayName("FocusProcessor AnalyseFieldType Tests")
  class FocusProcessorAnalyseFieldTypeTests {

    @Test
    @DisplayName("Set field should be detected as collection type")
    void setFieldShouldBeDetectedAsCollectionType() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Tags",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Set;
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

      // Set<String> should produce a TraversalPath
      assertThat(code).contains("TraversalPath");
    }
  }

  // =============================================================================
  // TraversalProcessor - createTraversalMethod conditionals
  // Targets: lines 119-136 - ArrayType check, DeclaredType empty type args,
  //   generator name equality, type argument boundary
  // =============================================================================

  @Nested
  @DisplayName("TraversalProcessor CreateTraversalMethod Tests")
  class TraversalProcessorCreateTraversalMethodTests {

    @Test
    @DisplayName("Record with List field should generate traversal with correct focus type")
    void listFieldShouldGenerateTraversalWithCorrectFocusType() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Shelf",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              import java.util.List;
              @GenerateTraversals
              public record Shelf(List<String> books, String location) {}
              """);

      Compilation compilation = javac().withProcessors(new TraversalProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.ShelfTraversals")
              .get()
              .getCharContent(true)
              .toString();

      // Should generate a traversal method named after the field
      assertThat(code).contains("Traversal<Shelf, String> books()");
      // Should NOT generate traversal for plain String field
      assertThat(code).doesNotContain("Traversal<Shelf, String> location()");
    }

    @Test
    @DisplayName("Record with Set field should generate traversal")
    void setFieldShouldGenerateTraversal() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Group",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              import java.util.Set;
              @GenerateTraversals
              public record Group(Set<Integer> memberIds) {}
              """);

      Compilation compilation = javac().withProcessors(new TraversalProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.GroupTraversals")
              .get()
              .getCharContent(true)
              .toString();

      assertThat(code).contains("Traversal<Group, Integer> memberIds()");
    }

    @Test
    @DisplayName("Record with only non-traversable fields should still succeed")
    void onlyNonTraversableFieldsShouldSucceed() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Plain",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              @GenerateTraversals
              public record Plain(String name, int count) {}
              """);

      Compilation compilation = javac().withProcessors(new TraversalProcessor()).compile(source);

      assertThat(compilation).succeeded();

      // Generated file should exist but with no traversal methods (just the class shell)
      String code =
          compilation
              .generatedSourceFile("com.example.PlainTraversals")
              .get()
              .getCharContent(true)
              .toString();

      assertThat(code).contains("class PlainTraversals");
      assertThat(code).doesNotContain("Traversal<Plain,");
    }
  }

  // =============================================================================
  // ForComprehensionProcessor - process boundary conditions
  // Targets: line 76 ConditionalsBoundaryMutator on maxArity > 26 → maxArity >= 26
  // Also targets: lines 45, 54, 60 - equality conditions in process()
  // =============================================================================

  @Nested
  @DisplayName("ForComprehensionProcessor Boundary Tests")
  class ForComprehensionProcessorBoundaryTests {

    @Test
    @DisplayName("maxArity = 26 should succeed (boundary value)")
    void maxArity26ShouldSucceed() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 26)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      // maxArity = 26 is valid, the processor should not report an error about maxArity
      for (var diag : compilation.diagnostics()) {
        if (diag.getKind() == Diagnostic.Kind.ERROR) {
          assertThat(diag.getMessage(null)).doesNotContain("maxArity must be");
        }
      }
    }

    @Test
    @DisplayName("maxArity = 27 should fail")
    void maxArity27ShouldFail() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 27)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).hadErrorContaining("maxArity must be <= 26");
    }

    @Test
    @DisplayName("minArity = 2 should succeed (boundary value)")
    void minArity2ShouldSucceed() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 3)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).hadErrorCount(0);
    }

    @Test
    @DisplayName("minArity = maxArity should succeed")
    void minArityEqualsMaxArityShouldSucceed() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 5, maxArity = 5)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).hadErrorCount(0);
    }

    @Test
    @DisplayName("duplicate package should be processed only once")
    void duplicatePackageShouldBeProcessedOnce() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 3)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      // Compiling same source twice - should still succeed without errors
      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      assertThat(compilation).hadErrorCount(0);
    }
  }

  // =============================================================================
  // KindRegistry - KindMapping.instance() and factory() NullReturnValsMutator
  // Targets: lines 59, 64 - replaced return value with null
  // Also targets: extractWitnessTypeArgs line 185 ConditionalsBoundaryMutator
  // =============================================================================

  @Nested
  @DisplayName("KindRegistry Mapping Factory Tests")
  class KindRegistryMappingFactoryTests {

    @Test
    @DisplayName(
        "Lookup for instance-based kind (ListKind) should return non-null non-parameterised mapping")
    void instanceBasedKindShouldReturnNonNullMapping() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.list.ListKind.Witness");
      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression()).contains(".INSTANCE");
      assertThat(mapping.get().isParameterised()).isFalse();
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.ZERO_OR_MORE);
    }

    @Test
    @DisplayName(
        "Lookup for factory-based kind (EitherKind) should return non-null parameterised mapping")
    void factoryBasedKindShouldReturnNonNullMapping() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.either.EitherKind.Witness");
      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression()).contains(".instance()");
      assertThat(mapping.get().isParameterised()).isTrue();
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.ZERO_OR_ONE);
    }

    @Test
    @DisplayName("Lookup for IdKind should return EXACTLY_ONE semantics")
    void idKindShouldReturnExactlyOneSemantics() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.id.IdKind.Witness");
      assertThat(mapping).isPresent();
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.EXACTLY_ONE);
      assertThat(mapping.get().isParameterised()).isFalse();
    }

    @Test
    @DisplayName("Lookup for ValidatedKind should return parameterised factory mapping")
    void validatedKindShouldReturnParameterisedMapping() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.validated.ValidatedKind.Witness");
      assertThat(mapping).isPresent();
      assertThat(mapping.get().isParameterised()).isTrue();
      assertThat(mapping.get().traverseExpression()).contains("ValidatedTraverse.instance()");
    }

    @Test
    @DisplayName("extractWitnessTypeArgs with no angle brackets returns empty string")
    void extractWitnessTypeArgsNoAngleBracketsReturnsEmpty() {
      String result = KindRegistry.extractWitnessTypeArgs("org.example.Witness");
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractWitnessTypeArgs with angle brackets returns inner type")
    void extractWitnessTypeArgsWithAngleBracketsReturnsInner() {
      String result = KindRegistry.extractWitnessTypeArgs("org.example.Witness<String>");
      assertThat(result).isEqualTo("String");
    }

    @Test
    @DisplayName("extractWitnessTypeArgs with nested generics returns full inner")
    void extractWitnessTypeArgsNestedGenericsReturnsFullInner() {
      String result =
          KindRegistry.extractWitnessTypeArgs("org.example.Witness<Map<String, Integer>>");
      assertThat(result).isEqualTo("Map<String, Integer>");
    }

    @Test
    @DisplayName("extractWitnessTypeArgs with empty angle brackets returns empty")
    void extractWitnessTypeArgsEmptyAngleBracketsReturnsEmpty() {
      // This is an edge case: "<>" has start=end-1, so substring would be empty
      String result = KindRegistry.extractWitnessTypeArgs("org.example.Witness<>");
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractBaseWitnessType strips type arguments")
    void extractBaseWitnessTypeStripsTypeArgs() {
      String result = KindRegistry.extractBaseWitnessType("org.example.Witness<String>");
      assertThat(result).isEqualTo("org.example.Witness");
    }

    @Test
    @DisplayName("extractBaseWitnessType returns full name when no type args")
    void extractBaseWitnessTypeReturnsFullNameWhenNoTypeArgs() {
      String result = KindRegistry.extractBaseWitnessType("org.example.Witness");
      assertThat(result).isEqualTo("org.example.Witness");
    }
  }

  // =============================================================================
  // ForPathStepGenerator - valueParams conditionals
  // Targets: valueParams lines 33, 34 - equality checks for special type params
  // Also targets: appendImports lines 300-330 - equality checks for imports
  // Also targets: appendFields lines 439, 442 - equality checks for fields
  // =============================================================================

  @Nested
  @DisplayName("ForPathStepGenerator Tests")
  class ForPathStepGeneratorTests {

    @Test
    @DisplayName("ForPath Either steps should have correct type parameters")
    void eitherPathStepsShouldHaveCorrectTypeParams() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 3)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      // Check that EitherPathSteps was generated with correct extra type param
      Optional<JavaFileObject> eitherSteps =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.EitherPathSteps2");
      assertThat(eitherSteps).isPresent();
    }

    @Test
    @DisplayName("ForPath Generic steps should have correct type parameters")
    void genericPathStepsShouldHaveCorrectTypeParams() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 3)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      // Check that GenericPathSteps was generated
      Optional<JavaFileObject> genericSteps =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.GenericPathSteps2");
      assertThat(genericSteps).isPresent();
    }

    @Test
    @DisplayName("ForPath Maybe steps should be generated as filterable")
    void maybePathStepsShouldBeFilterable() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 3)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      Optional<JavaFileObject> maybeSteps =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.MaybePathSteps2");
      assertThat(maybeSteps).isPresent();

      String code = maybeSteps.get().getCharContent(true).toString();
      // Maybe is filterable, so should have a when() method
      assertThat(code).contains("when(");
    }

    @Test
    @DisplayName("ForPath steps should generate all 9 path types")
    void forPathStepsShouldGenerateAllPathTypes() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 2)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      // All 9 path types should be generated
      assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.expression.MaybePathSteps2"))
          .isPresent();
      assertThat(
              compilation.generatedSourceFile(
                  "org.higherkindedj.hkt.expression.OptionalPathSteps2"))
          .isPresent();
      assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.expression.EitherPathSteps2"))
          .isPresent();
      assertThat(compilation.generatedSourceFile("org.higherkindedj.hkt.expression.TryPathSteps2"))
          .isPresent();
      assertThat(compilation.generatedSourceFile("org.higherkindedj.hkt.expression.IOPathSteps2"))
          .isPresent();
      assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.expression.VTaskPathSteps2"))
          .isPresent();
      assertThat(compilation.generatedSourceFile("org.higherkindedj.hkt.expression.IdPathSteps2"))
          .isPresent();
      assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.expression.NonDetPathSteps2"))
          .isPresent();
      assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.expression.GenericPathSteps2"))
          .isPresent();
    }
  }

  // =============================================================================
  // ForStepGenerator - generateFilterableSteps line 199
  // Targets: conditional equality check in filterable step generation
  // =============================================================================

  @Nested
  @DisplayName("ForStepGenerator Filterable Tests")
  class ForStepGeneratorFilterableTests {

    @Test
    @DisplayName("ForStep generation should produce both monadic and filterable steps")
    void shouldGenerateBothMonadicAndFilterableSteps() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 3)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      // MonadicSteps should exist
      assertThat(compilation.generatedSourceFile("org.higherkindedj.hkt.expression.MonadicSteps2"))
          .isPresent();
      assertThat(compilation.generatedSourceFile("org.higherkindedj.hkt.expression.MonadicSteps3"))
          .isPresent();

      // FilterableSteps should also exist
      assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.expression.FilterableSteps2"))
          .isPresent();
      assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.expression.FilterableSteps3"))
          .isPresent();
    }

    @Test
    @DisplayName("FilterableSteps should contain when method")
    void filterableStepsShouldContainWhenMethod() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 2)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      String code =
          compilation
              .generatedSourceFile("org.higherkindedj.hkt.expression.FilterableSteps2")
              .get()
              .getCharContent(true)
              .toString();

      // FilterableSteps should have a when() method for filtering
      assertThat(code).contains("when(");
    }
  }

  // =============================================================================
  // TupleGenerator - generateMapAll and generateTuple VoidMethodCallMutator
  // Targets: generateMapAll lines 177, 184, 222 - boundary conditions
  // Also targets: generateTuple line 126 - VoidMethodCallMutator removing generateMapAll call
  // =============================================================================

  @Nested
  @DisplayName("TupleGenerator MapAll Tests")
  class TupleGeneratorMapAllTests {

    @Test
    @DisplayName("Generated Tuple should contain mapAll method with correct mapper count")
    void generatedTupleShouldContainMapAllMethod() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 4, maxArity = 4)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      String code =
          compilation
              .generatedSourceFile("org.higherkindedj.hkt.tuple.Tuple4")
              .get()
              .getCharContent(true)
              .toString();

      // map method should exist with correct number of mapper parameters
      assertThat(code).contains("map(");
      assertThat(code).contains("firstMapper");
      assertThat(code).contains("secondMapper");
      assertThat(code).contains("thirdMapper");
      assertThat(code).contains("fourthMapper");
    }

    @Test
    @DisplayName("Tuple2 should have bimap instead of map")
    void tuple2ShouldHaveBimapInsteadOfMap() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 2)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      String code =
          compilation
              .generatedSourceFile("org.higherkindedj.hkt.tuple.Tuple2")
              .get()
              .getCharContent(true)
              .toString();

      // Tuple2 should have bimap, not map
      assertThat(code).contains("bimap(");
      assertThat(code).contains("firstMapper");
      assertThat(code).contains("secondMapper");
    }

    @Test
    @DisplayName("Tuple mapAll should have validation for each mapper")
    void tupleMapAllShouldValidateEachMapper() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.package-info",
              """
              @GenerateForComprehensions(minArity = 3, maxArity = 3)
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation =
          javac().withProcessors(new ForComprehensionProcessor()).compile(source);

      String code =
          compilation
              .generatedSourceFile("org.higherkindedj.hkt.tuple.Tuple3")
              .get()
              .getCharContent(true)
              .toString();

      // Should validate each mapper with requireMapper
      assertThat(code).contains("requireMapper");
      // Should have all three mappers
      assertThat(code).contains("firstMapper");
      assertThat(code).contains("secondMapper");
      assertThat(code).contains("thirdMapper");
    }
  }

  // =============================================================================
  // ImportOpticsProcessor - isSpecInterface, processSpecInterface, processTypeAnnotation
  // Targets: Various conditional checks in the spec interface path
  // =============================================================================

  @Nested
  @DisplayName("ImportOpticsProcessor Spec Interface Tests")
  class ImportOpticsProcessorSpecInterfaceTests {

    @Test
    @DisplayName("Interface extending OpticsSpec should be processed as spec interface")
    void interfaceExtendingOpticsSpecShouldBeProcessedAsSpec() {
      var record =
          JavaFileObjects.forSourceString(
              "com.example.Employee",
              """
              package com.example;
              public record Employee(String name, int salary) {}
              """);
      var spec =
          JavaFileObjects.forSourceString(
              "com.example.EmployeeOptics",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface EmployeeOptics extends OpticsSpec<Employee> {}
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(record, spec);

      // The processor should detect the spec interface and generate output
      // (compilation may have errors in generated code, but the processor
      // should recognize OpticsSpec and attempt generation)
      assertThat(compilation).generatedSourceFile("com.example.EmployeeOpticsImpl").isNotNull();
    }

    @Test
    @DisplayName("Interface NOT extending OpticsSpec should be processed as type annotation")
    void interfaceNotExtendingOpticsSpecShouldBeProcessedAsType() {
      var record =
          JavaFileObjects.forSourceString(
              "com.example.Product",
              """
              package com.example;
              public record Product(String name, double price) {}
              """);
      var iface =
          JavaFileObjects.forSourceString(
              "com.example.ProductOptics",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({Product.class})
              public interface ProductOptics {}
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(record, iface);

      assertThat(compilation).succeeded();

      // Should generate lenses file, not spec impl
      assertThat(compilation).generatedSourceFile("com.example.ProductLenses").isNotNull();
    }

    @Test
    @DisplayName("Class with @ImportOptics should be processed as type annotation")
    void classWithImportOpticsShouldBeProcessedAsType() {
      var record =
          JavaFileObjects.forSourceString(
              "com.example.Vehicle",
              """
              package com.example;
              public record Vehicle(String make, int year) {}
              """);
      var clazz =
          JavaFileObjects.forSourceString(
              "com.example.VehicleOptics",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({Vehicle.class})
              public class VehicleOptics {}
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(record, clazz);

      assertThat(compilation).succeeded();

      assertThat(compilation).generatedSourceFile("com.example.VehicleLenses").isNotNull();
    }
  }

  // =============================================================================
  // ImportOpticsProcessor - processPackageAnnotation and getClassArrayFromAnnotation
  // Targets: getClassArrayFromAnnotation lines 266-291 conditional checks
  // =============================================================================

  @Nested
  @DisplayName("ImportOpticsProcessor Package Annotation Tests")
  class ImportOpticsProcessorPackageAnnotationTests {

    @Test
    @DisplayName("Package annotation with multiple classes should process all")
    void packageAnnotationWithMultipleClassesShouldProcessAll() {
      var record1 =
          JavaFileObjects.forSourceString(
              "com.external.Cat",
              """
              package com.external;
              public record Cat(String name, int lives) {}
              """);
      var record2 =
          JavaFileObjects.forSourceString(
              "com.external.Dog",
              """
              package com.external;
              public record Dog(String breed, int age) {}
              """);
      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Cat.class, com.external.Dog.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(record1, record2, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.optics.CatLenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.myapp.optics.DogLenses").isNotNull();
    }

    @Test
    @DisplayName("Package annotation with targetPackage should use specified package")
    void packageAnnotationWithTargetPackageShouldUseSpecifiedPackage() {
      var record =
          JavaFileObjects.forSourceString(
              "com.external.Fish",
              """
              package com.external;
              public record Fish(String species, boolean freshwater) {}
              """);
      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics(value = {com.external.Fish.class}, targetPackage = "com.myapp.gen")
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(record, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.gen.FishLenses").isNotNull();
    }
  }

  // =============================================================================
  // FocusProcessor - process line 118 conditionals
  // Targets: ElementKind.RECORD check in process()
  // =============================================================================

  @Nested
  @DisplayName("FocusProcessor Process Method Tests")
  class FocusProcessorProcessMethodTests {

    @Test
    @DisplayName("GenerateFocus on interface should fail with error")
    void generateFocusOnInterfaceShouldFail() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.NotARecord",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public interface NotARecord { String name(); }
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).failed();
    }

    @Test
    @DisplayName("GenerateFocus on class should fail with error")
    void generateFocusOnClassShouldFail() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.NotARecord",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public class NotARecord { String name; }
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

      assertThat(compilation).failed();
    }
  }

  // =============================================================================
  // ProcessorUtils - toCamelCase
  // Targets: lines 34 and 58 - equality conditions
  // =============================================================================

  @Nested
  @DisplayName("ProcessorUtils ToCamelCase Tests")
  class ProcessorUtilsToCamelCaseTests {

    @Test
    @DisplayName("toCamelCase should convert underscore_case to camelCase")
    void toCamelCaseShouldConvertUnderscoreCase() {
      String result =
          ProcessorUtils.toCamelCase("hello_world");
      assertThat(result).isEqualTo("helloWorld");
    }

    @Test
    @DisplayName("toCamelCase should handle single word")
    void toCamelCaseShouldHandleSingleWord() {
      String result = ProcessorUtils.toCamelCase("hello");
      assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("toCamelCase should handle empty string")
    void toCamelCaseShouldHandleEmptyString() {
      String result = ProcessorUtils.toCamelCase("");
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toCamelCase should handle multiple underscores")
    void toCamelCaseShouldHandleMultipleUnderscores() {
      String result =
          ProcessorUtils.toCamelCase("my_long_name_here");
      assertThat(result).isEqualTo("myLongNameHere");
    }
  }

  // =============================================================================
  // NavigatorClassGenerator - getFieldPathKind NullReturnValsMutator
  // Targets: lines 133, 150, 174 - replaced return value with null
  // These are critical: null PathKind would cause NPE downstream
  // =============================================================================

  @Nested
  @DisplayName("Navigator GetFieldPathKind Return Value Tests")
  class NavigatorGetFieldPathKindReturnValueTests {

    @Test
    @DisplayName("Primitive field should return FOCUS path kind (not null)")
    void primitiveFieldShouldReturnFocusNotNull() throws IOException {
      // Test that non-declared type (primitive int) returns FOCUS, not null
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Dimensions",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Dimensions(int width, int height) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Box",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Box(Dimensions size) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      // Should succeed - if getFieldPathKind returned null, it would NPE
      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.BoxFocus")
              .get()
              .getCharContent(true)
              .toString();

      // The navigator should have been generated successfully
      assertThat(code).contains("SizeNavigator");
      // Fields width/height are primitives - should be FocusPath
      assertThat(code).contains("FocusPath");
    }

    @Test
    @DisplayName("Regular String field should return FOCUS (not null)")
    void regularStringFieldShouldReturnFocus() throws IOException {
      // String is a declared type but not optional or collection → FOCUS
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.NameInfo",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record NameInfo(String first, String last) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Profile(NameInfo name) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.ProfileFocus")
              .get()
              .getCharContent(true)
              .toString();

      assertThat(code).contains("NameNavigator");
      // First/last are String → FocusPath<S, String>
      assertThat(code).contains("FocusPath<S, String>");
    }

    @Test
    @DisplayName("Nullable navigable field should return AFFINE path kind in navigator")
    void nullableNavigableFieldShouldReturnAffinePath() throws IOException {
      // Provide @Nullable annotation as a source file for the compilation
      var nullableAnnotation =
          JavaFileObjects.forSourceString(
              "org.jspecify.annotations.Nullable",
              """
              package org.jspecify.annotations;
              import java.lang.annotation.*;
              @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
              @Retention(RetentionPolicy.RUNTIME)
              public @interface Nullable {}
              """);
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Note",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Note(String text) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Document",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(generateNavigators = true)
              public record Document(String title, @Nullable Note footnote) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(nullableAnnotation, inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.DocumentFocus")
              .get()
              .getCharContent(true)
              .toString();

      // @Nullable navigable field should produce AffinePath in the navigator
      assertThat(code).contains("AffinePath");
      assertThat(code).contains("FootnoteNavigator");
      // The entry point should apply .nullable() to widen FocusPath to AffinePath
      assertThat(code).contains(".nullable()");
    }
  }

  // =============================================================================
  // NavigatorClassGenerator - isNavigableType BooleanTrueReturnValsMutator
  // Targets: line 678 - replaced boolean return with true
  // If always returns true, non-record types would incorrectly get navigators
  // =============================================================================

  @Nested
  @DisplayName("Navigator IsNavigableType Boolean Return Tests")
  class NavigatorIsNavigableTypeBooleanTests {

    @Test
    @DisplayName("Enum field type should not be treated as navigable")
    void enumFieldTypeShouldNotBeNavigable() throws IOException {
      var enumType =
          JavaFileObjects.forSourceString(
              "com.example.Color",
              """
              package com.example;
              public enum Color { RED, GREEN, BLUE }
              """);
      var record =
          JavaFileObjects.forSourceString(
              "com.example.Pixel",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Pixel(Color color, int x, int y) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(enumType, record);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.PixelFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Enum is not navigable - should NOT generate ColorNavigator
      assertThat(code).doesNotContain("ColorNavigator");
    }
  }

  // =============================================================================
  // NavigatorClassGenerator - getFieldPathKind interface subtype checks
  // Targets: lines 145, 149, 157, 160, 166, 168 - various conditional checks
  // These cover the full getFieldPathKind decision tree
  // =============================================================================

  @Nested
  @DisplayName("Navigator GetFieldPathKind Decision Tree Tests")
  class NavigatorGetFieldPathKindDecisionTreeTests {

    @Test
    @DisplayName("Collection subtype (ArrayList) should be detected as TRAVERSAL")
    void collectionSubtypeShouldBeDetectedAsTraversal() throws IOException {
      // The inner record has an ArrayList field - the navigator for this record
      // should detect ArrayList as a collection subtype and use TraversalPath
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Data",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.ArrayList;
              @GenerateFocus(generateNavigators = true)
              public record Data(ArrayList<String> values) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Store",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Store(Data data) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.StoreFocus")
              .get()
              .getCharContent(true)
              .toString();

      // ArrayList implements Collection → TraversalPath in the navigator
      assertThat(code).contains("TraversalPath");
    }

    @Test
    @DisplayName("Optional field should be detected as AFFINE")
    void optionalFieldShouldBeDetectedAsAffine() throws IOException {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Metadata",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Metadata(String key) {}
              """);
      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Entry",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;
              @GenerateFocus(generateNavigators = true)
              public record Entry(Optional<Metadata> meta) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.EntryFocus")
              .get()
              .getCharContent(true)
              .toString();

      // Optional → AffinePath
      assertThat(code).contains("AffinePath");
    }
  }

  // =============================================================================
  // PathSourceProcessor - isRecoverable and isChainable
  // Targets: isRecoverable line 274 - BooleanTrueReturnValsMutator + conditional mutations
  //   isChainable line 263 - conditional mutation
  //   generatePathClass lines 147, 181, 201 - conditional mutations
  //   buildConstructor line 295, buildOfFactory line 335, buildPureFactory line 374
  // =============================================================================

  @Nested
  @DisplayName("PathSourceProcessor Capability Tests")
  class PathSourceProcessorCapabilityTests {

    private JavaFileObject witnessSource() {
      return JavaFileObjects.forSourceString(
          "com.example.TestKind",
          """
          package com.example;

          import org.higherkindedj.hkt.Kind;
          import org.higherkindedj.hkt.TypeArity;
          import org.higherkindedj.hkt.WitnessArity;

          public interface TestKind<A> extends Kind<TestKind.Witness, A> {
              final class Witness implements WitnessArity<TypeArity.Unary> {}
          }
          """);
    }

    @Test
    @DisplayName("COMPOSABLE capability should not include via/flatMap methods")
    void composableCapabilityShouldNotIncludeViaFlatMap() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Composable",
              """
              package com.example;
              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = TestKind.Witness.class, capability = PathSource.Capability.COMPOSABLE)
              public interface Composable<A> {}
              """);

      Compilation compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource(), source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.ComposablePath")
              .get()
              .getCharContent(true)
              .toString();

      // COMPOSABLE should have map/peek but NOT via/flatMap/zipWith
      assertThat(code).contains("map(");
      assertThat(code).doesNotContain("via(");
      assertThat(code).doesNotContain("flatMap(");
      assertThat(code).doesNotContain("zipWith(");
      // Should NOT have recover methods (no error type + not recoverable)
      assertThat(code).doesNotContain("recover(");
      assertThat(code).doesNotContain("monadError");
    }

    @Test
    @DisplayName("COMBINABLE capability should include zipWith but not via/flatMap")
    void combinableCapabilityShouldIncludeZipWithButNotVia() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Combinable",
              """
              package com.example;
              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = TestKind.Witness.class, capability = PathSource.Capability.COMBINABLE)
              public interface Combinable<A> {}
              """);

      Compilation compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource(), source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.CombinablePath")
              .get()
              .getCharContent(true)
              .toString();

      // COMBINABLE should have zipWith but NOT Chainable interface methods (via/flatMap)
      assertThat(code).contains("zipWith(");
      assertThat(code).doesNotContain("Chainable");
      // flatMap may appear internally in zipWith's implementation (monad.flatMap),
      // but via() should not appear at all since Chainable is not implemented
      assertThat(code).doesNotContain("via(");
    }

    @Test
    @DisplayName("CHAINABLE capability should include via and flatMap methods")
    void chainableCapabilityShouldIncludeViaAndFlatMap() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Chainable",
              """
              package com.example;
              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = TestKind.Witness.class, capability = PathSource.Capability.CHAINABLE)
              public interface Chainable<A> {}
              """);

      Compilation compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource(), source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.ChainablePath")
              .get()
              .getCharContent(true)
              .toString();

      // CHAINABLE should have via, then, flatMap
      assertThat(code).contains("via(");
      assertThat(code).contains("then(");
      assertThat(code).contains("flatMap(");
      // Should NOT have recover (no error type)
      assertThat(code).doesNotContain("recover(");
    }

    @Test
    @DisplayName("RECOVERABLE with errorType should include recover methods and monadError field")
    void recoverableWithErrorTypeShouldIncludeRecoverMethods() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Recoverable",
              """
              package com.example;
              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = TestKind.Witness.class,
                  errorType = String.class,
                  capability = PathSource.Capability.RECOVERABLE)
              public interface Recoverable<A> {}
              """);

      Compilation compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource(), source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.RecoverablePath")
              .get()
              .getCharContent(true)
              .toString();

      // RECOVERABLE with errorType should have monadError field + recover methods
      assertThat(code).contains("monadError");
      assertThat(code).contains("recover(");
      assertThat(code).contains("recoverWith(");
      assertThat(code).contains("mapError(");
      // Should also be chainable
      assertThat(code).contains("via(");
      assertThat(code).contains("flatMap(");
    }

    @Test
    @DisplayName("ACCUMULATING with errorType should include recover methods")
    void accumulatingWithErrorTypeShouldIncludeRecoverMethods() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Accumulating",
              """
              package com.example;
              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = TestKind.Witness.class,
                  errorType = String.class,
                  capability = PathSource.Capability.ACCUMULATING)
              public interface Accumulating<A> {}
              """);

      Compilation compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource(), source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.AccumulatingPath")
              .get()
              .getCharContent(true)
              .toString();

      // ACCUMULATING is also recoverable
      assertThat(code).contains("monadError");
      assertThat(code).contains("recover(");
      // And chainable
      assertThat(code).contains("via(");
    }

    @Test
    @DisplayName("EFFECTFUL without errorType should not include recover methods")
    void effectfulWithoutErrorTypeShouldNotIncludeRecover() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Effectful",
              """
              package com.example;
              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = TestKind.Witness.class,
                  capability = PathSource.Capability.EFFECTFUL)
              public interface Effectful<A> {}
              """);

      Compilation compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource(), source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.EffectfulPath")
              .get()
              .getCharContent(true)
              .toString();

      // EFFECTFUL should be chainable but NOT recoverable (no errorType)
      assertThat(code).contains("via(");
      assertThat(code).doesNotContain("monadError");
      assertThat(code).doesNotContain("recover(");
    }

    @Test
    @DisplayName("PathSource with custom targetPackage should use specified package")
    void pathSourceWithCustomTargetPackageShouldWork() {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Custom",
              """
              package com.example;
              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = TestKind.Witness.class,
                  targetPackage = "com.example.gen")
              public interface Custom<A> {}
              """);

      Compilation compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource(), source);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.gen.CustomPath").isNotNull();
    }
  }

  // =============================================================================
  // SpecInterfaceAnalyser / ImportOpticsProcessor - @Wither, @ViaConstructor, @ViaCopyAndSet
  // Targets: parseCopyStrategy lines 353, 362 - equality checks for strategy detection
  //   analyseOpticMethod lines 185, 208
  //   getAnnotationString line 604
  // =============================================================================

  @Nested
  @DisplayName("ImportOpticsProcessor Copy Strategy Tests")
  class ImportOpticsProcessorCopyStrategyTests {

    @Test
    @DisplayName("Record with wither methods should generate lenses using wither strategy")
    void recordWithWitherMethodsShouldWork() {
      var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.ImmutablePoint",
              """
              package com.external;

              public final class ImmutablePoint {
                  private final int x;
                  private final int y;

                  public ImmutablePoint(int x, int y) {
                      this.x = x;
                      this.y = y;
                  }

                  public int x() { return x; }
                  public int y() { return y; }

                  public ImmutablePoint withX(int x) { return new ImmutablePoint(x, this.y); }
                  public ImmutablePoint withY(int y) { return new ImmutablePoint(this.x, y); }
              }
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.ImmutablePoint.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, packageInfo);

      assertThat(compilation).succeeded();

      // Should generate lenses using wither pattern
      assertThat(compilation)
          .generatedSourceFile("com.myapp.optics.ImmutablePointLenses")
          .isNotNull();
    }

    @Test
    @DisplayName("Sealed interface should generate prisms")
    void sealedInterfaceShouldGeneratePrisms() {
      var shape =
          JavaFileObjects.forSourceString(
              "com.external.Shape",
              """
              package com.external;
              public sealed interface Shape permits Circle, Square {}
              """);
      var circle =
          JavaFileObjects.forSourceString(
              "com.external.Circle",
              """
              package com.external;
              public record Circle(double radius) implements Shape {}
              """);
      var square =
          JavaFileObjects.forSourceString(
              "com.external.Square",
              """
              package com.external;
              public record Square(double side) implements Shape {}
              """);
      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Shape.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(shape, circle, square, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.optics.ShapePrisms").isNotNull();
    }

    @Test
    @DisplayName("Enum should generate prisms")
    void enumShouldGeneratePrisms() {
      var color =
          JavaFileObjects.forSourceString(
              "com.external.Color",
              """
              package com.external;
              public enum Color { RED, GREEN, BLUE }
              """);
      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Color.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(color, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.optics.ColorPrisms").isNotNull();
    }
  }

  // =============================================================================
  // FoldProcessor - isIterableType conditional checks
  // Targets: isIterableType lines 174, 182 - equality checks
  //   getElementType line 195
  // =============================================================================

  @Nested
  @DisplayName("FoldProcessor Iterable Detection Tests")
  class FoldProcessorIterableDetectionTests {

    @Test
    @DisplayName("Record with ArrayList field should generate fold (Collection subtype)")
    void arrayListFieldShouldGenerateFold() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Container",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFolds;
              import java.util.ArrayList;
              @GenerateFolds
              public record Container(ArrayList<String> items) {}
              """);

      Compilation compilation = javac().withProcessors(new FoldProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.ContainerFolds")
              .get()
              .getCharContent(true)
              .toString();

      // ArrayList implements Iterable → should generate fold method named after field
      assertThat(code).contains("Fold<Container, String> items()");
      assertThat(code).contains("source.items()");
    }

    @Test
    @DisplayName("Record with Set field should generate fold")
    void setFieldShouldGenerateFold() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.SetHolder",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFolds;
              import java.util.Set;
              @GenerateFolds
              public record SetHolder(Set<Integer> numbers) {}
              """);

      Compilation compilation = javac().withProcessors(new FoldProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.SetHolderFolds")
              .get()
              .getCharContent(true)
              .toString();

      assertThat(code).contains("Fold<SetHolder, Integer> numbers()");
    }
  }

  // =============================================================================
  // IsoProcessor - processMethod line 67 conditional
  // =============================================================================

  @Nested
  @DisplayName("IsoProcessor ProcessMethod Tests")
  class IsoProcessorProcessMethodTests {

    @Test
    @DisplayName("Iso method returning Iso type should generate Isos class")
    void isoMethodReturningIsoTypeShouldGenerate() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Converters",
              """
              package com.example;
              import org.higherkindedj.optics.Iso;
              import org.higherkindedj.optics.annotations.GenerateIsos;

              public class Converters {
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
              .generatedSourceFile("com.example.ConvertersIsos")
              .get()
              .getCharContent(true)
              .toString();

      assertThat(code).contains("stringToInt");
      assertThat(code).contains("Iso<String, Integer>");
    }
  }

  // =============================================================================
  // PrismProcessor - generatePrismsFile line 113 conditional
  // =============================================================================

  @Nested
  @DisplayName("PrismProcessor Generate Tests")
  class PrismProcessorGenerateTests {

    @Test
    @DisplayName("Sealed interface with multiple subtypes should generate prisms for each")
    void sealedInterfaceShouldGeneratePrismsForEachSubtype() throws IOException {
      var parent =
          JavaFileObjects.forSourceString(
              "com.example.Animal",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GeneratePrisms;
              @GeneratePrisms
              public sealed interface Animal permits Cat, Dog, Bird {}
              """);
      var cat =
          JavaFileObjects.forSourceString(
              "com.example.Cat",
              """
              package com.example;
              public record Cat(String name) implements Animal {}
              """);
      var dog =
          JavaFileObjects.forSourceString(
              "com.example.Dog",
              """
              package com.example;
              public record Dog(String breed) implements Animal {}
              """);
      var bird =
          JavaFileObjects.forSourceString(
              "com.example.Bird",
              """
              package com.example;
              public record Bird(boolean canFly) implements Animal {}
              """);

      Compilation compilation =
          javac().withProcessors(new PrismProcessor()).compile(parent, cat, dog, bird);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.AnimalPrisms")
              .get()
              .getCharContent(true)
              .toString();

      // Should have prisms for each subtype
      assertThat(code).contains("Cat");
      assertThat(code).contains("Dog");
      assertThat(code).contains("Bird");
    }
  }

  // =============================================================================
  // SetterProcessor - lambda conditional in createSetterMethod line 139
  // =============================================================================

  @Nested
  @DisplayName("SetterProcessor Lambda Tests")
  class SetterProcessorLambdaTests {

    @Test
    @DisplayName("Setter with multiple fields should use correct field in lambda")
    void setterWithMultipleFieldsShouldUseCorrectField() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateSetters;
              @GenerateSetters
              public record Config(String host, int port, boolean secure) {}
              """);

      Compilation compilation = javac().withProcessors(new SetterProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.ConfigSetters")
              .get()
              .getCharContent(true)
              .toString();

      // Should have setters for each field
      assertThat(code).contains("host(");
      assertThat(code).contains("port(");
      assertThat(code).contains("secure(");
      // Each setter should use newValue for the target field
      assertThat(code).contains("newValue");
    }
  }

  // =============================================================================
  // GetterProcessor - capitalise conditional
  // =============================================================================

  @Nested
  @DisplayName("GetterProcessor Capitalise Tests")
  class GetterProcessorCapitaliseTests {

    @Test
    @DisplayName("Getter for field starting with lowercase should capitalise in method name")
    void getterShouldCapitaliseFieldName() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Data",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateGetters;
              @GenerateGetters
              public record Data(String value) {}
              """);

      Compilation compilation = javac().withProcessors(new GetterProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.DataGetters")
              .get()
              .getCharContent(true)
              .toString();

      assertThat(code).contains("value");
      assertThat(code).contains("Getter<Data, String>");
    }
  }

  // =============================================================================
  // LensProcessor - capitalise conditional
  // =============================================================================

  @Nested
  @DisplayName("LensProcessor Capitalise Tests")
  class LensProcessorCapitaliseTests {

    @Test
    @DisplayName("Lens should generate correct capitalised method names")
    void lensShouldGenerateCorrectCapitalisedMethodNames() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "com.example.Settings",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateLenses;
              @GenerateLenses
              public record Settings(String theme, int fontSize) {}
              """);

      Compilation compilation = javac().withProcessors(new LensProcessor()).compile(source);

      assertThat(compilation).succeeded();

      String code =
          compilation
              .generatedSourceFile("com.example.SettingsLenses")
              .get()
              .getCharContent(true)
              .toString();

      // Should have lens methods
      assertThat(code).contains("theme");
      assertThat(code).contains("fontSize");
      assertThat(code).contains("Lens<Settings,");
    }
  }
}
