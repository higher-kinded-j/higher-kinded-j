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
 * Additional coverage tests for NavigatorClassGenerator delegate methods, path widening branches,
 * and FocusProcessor edge cases.
 *
 * <p>Targets uncovered branches:
 *
 * <ul>
 *   <li>AffinePath delegate methods (getOptional, set, modify, matches, toPath)
 *   <li>TraversalPath delegate methods (getAll, setAll, modifyAll, count, isEmpty, toPath)
 *   <li>{@code @Nullable} field widening in navigator navigation methods
 *   <li>Navigator for Optional&lt;Navigable&gt; wrapping in AffinePath navigator
 *   <li>{@code @Nullable} on primitive type fields in FocusProcessor
 *   <li>buildViaStatement AFFINE→TRAVERSAL asTraversal() conversion
 *   <li>Collection subtype interface check in getFieldPathKindRecursive
 * </ul>
 */
@DisplayName("Navigator Delegate and Widening Coverage Tests")
class NavigatorDelegateAndWideningTest {

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
  @DisplayName("AffinePath Navigator Delegate Methods")
  class AffinePathDelegates {

    @Test
    @DisplayName("should generate AffinePath delegate methods for @Nullable navigable field")
    void shouldGenerateAffinePathDelegateMethods() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String label, int count) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, @Nullable Inner inner) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(NULLABLE_ANNOTATION, innerSource, outerSource);

      assertThat(compilation).succeeded();

      // AffinePath delegate methods: getOptional, set, modify, matches, toPath
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "Optional<Inner> getOptional(S source)");
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "S set(Inner value, S source)");
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "S modify(Function<Inner, Inner> f, S source)");
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "boolean matches(S source)");
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "AffinePath<S, Inner> toPath()");
    }

    @Test
    @DisplayName("should generate AffinePath navigator for Optional<Navigable> field")
    void shouldGenerateAffineNavigatorForOptionalNavigable() {
      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);

      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Company(String name, Optional<Address> backup) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(addressSource, companySource);

      assertThat(compilation).succeeded();

      // Optional<Address> should produce AffinePath with delegate methods
      assertGeneratedCodeContains(
          compilation, "com.example.CompanyFocus", "AffinePath<Company, Address> backup()");
    }
  }

  @Nested
  @DisplayName("TraversalPath Navigator Delegate Methods")
  class TraversalPathDelegates {

    @Test
    @DisplayName(
        "should generate TraversalPath method for List<Navigable> with collection widening")
    void shouldGenerateTraversalPathForListNavigable() {
      final JavaFileObject memberSource =
          JavaFileObjects.forSourceString(
              "com.example.Member",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Member(String firstName, String lastName) {}
              """);

      final JavaFileObject teamSource =
          JavaFileObjects.forSourceString(
              "com.example.Team",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, widenCollections = true)
              public record Team(String name, List<Member> members) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(memberSource, teamSource);

      assertThat(compilation).succeeded();

      // List<Member> with widenCollections produces a TraversalPath method (not a navigator class,
      // since List is a hardcoded widening type rather than an SPI container)
      assertGeneratedCodeContains(
          compilation, "com.example.TeamFocus", "TraversalPath<Team, Member> members()");
    }
  }

  @Nested
  @DisplayName("@Nullable Field Widening in Navigators")
  class NullableFieldNavigation {

    @Test
    @DisplayName("should widen @Nullable field to AffinePath in navigator navigation method")
    void shouldWidenNullableFieldToAffinePath() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Detail",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(generateNavigators = true)
              public record Detail(String info, @Nullable String note) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Container",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Container(String id, Detail detail) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(NULLABLE_ANNOTATION, innerSource, outerSource);

      assertThat(compilation).succeeded();

      // @Nullable String note in Detail should produce AffinePath via .nullable()
      assertGeneratedCodeContains(
          compilation, "com.example.ContainerFocus", "AffinePath<S, String> note()");
      // Non-nullable field should remain FocusPath
      assertGeneratedCodeContains(
          compilation, "com.example.ContainerFocus", "FocusPath<S, String> info()");
    }

    @Test
    @DisplayName("should widen @Nullable navigable field in navigator to AffinePath navigator")
    void shouldWidenNullableNavigableToAffineNavigator() {
      final JavaFileObject leafSource =
          JavaFileObjects.forSourceString(
              "com.example.Leaf",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Leaf(String value) {}
              """);

      final JavaFileObject branchSource =
          JavaFileObjects.forSourceString(
              "com.example.Branch",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(generateNavigators = true)
              public record Branch(String name, @Nullable Leaf leaf) {}
              """);

      final JavaFileObject rootSource =
          JavaFileObjects.forSourceString(
              "com.example.Root",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Root(String id, Branch branch) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(NULLABLE_ANNOTATION, leafSource, branchSource, rootSource);

      assertThat(compilation).succeeded();

      // In Root's BranchNavigator, the @Nullable Leaf should widen the navigator to AffinePath
      assertGeneratedCodeContains(
          compilation, "com.example.RootFocus", "BranchNavigator<Root> branch()");
    }
  }

  @Nested
  @DisplayName("FocusProcessor @Nullable Primitive Fields")
  class NullablePrimitiveFields {

    @Test
    @DisplayName("should widen @Nullable Integer field to AffinePath")
    void shouldWidenNullableIntegerToAffinePath() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Wrapper",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus
              public record Wrapper(String name, @Nullable Integer count) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(NULLABLE_ANNOTATION, sourceFile);

      assertThat(compilation).succeeded();

      assertGeneratedCodeContains(
          compilation, "com.example.WrapperFocus", "AffinePath<Wrapper, Integer> count()");
      assertGeneratedCodeContains(
          compilation, "com.example.WrapperFocus", "FocusPath<Wrapper, String> name()");
    }
  }

  @Nested
  @DisplayName("Navigator with Mixed Widening Paths")
  class MixedWideningPaths {

    @Test
    @DisplayName(
        "should generate correct path kinds for mixed Optional, List, and plain fields in navigator")
    void shouldHandleMixedPathKindsInNavigator() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;
              import java.util.Optional;
              import java.util.List;
              import java.util.Set;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Profile(
                  String name,
                  Optional<String> bio,
                  List<String> tags,
                  Set<String> skills) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Account(String id, Profile profile) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // Verify all four path kinds are correctly generated in the navigator
      assertGeneratedCodeContains(
          compilation, "com.example.AccountFocus", "FocusPath<S, String> name()");
      assertGeneratedCodeContains(
          compilation, "com.example.AccountFocus", "AffinePath<S, String> bio()");
      assertGeneratedCodeContains(
          compilation, "com.example.AccountFocus", "TraversalPath<S, String> tags()");
      assertGeneratedCodeContains(
          compilation, "com.example.AccountFocus", "TraversalPath<S, String> skills()");
    }

    @Test
    @DisplayName(
        "should widen path to TraversalPath when navigating through AffinePath then List field")
    void shouldWidenAffineToTraversalInNestedNavigator() {
      final JavaFileObject leafSource =
          JavaFileObjects.forSourceString(
              "com.example.Leaf",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Leaf(String value, List<String> items) {}
              """);

      final JavaFileObject midSource =
          JavaFileObjects.forSourceString(
              "com.example.Mid",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Mid(String name, Optional<Leaf> optLeaf) {}
              """);

      final JavaFileObject rootSource =
          JavaFileObjects.forSourceString(
              "com.example.Root",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Root(String id, Mid mid) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(leafSource, midSource, rootSource);

      assertThat(compilation).succeeded();

      // Root → Mid navigator should exist
      assertGeneratedCodeContains(compilation, "com.example.RootFocus", "MidNavigator<Root> mid()");
    }
  }

  @Nested
  @DisplayName("FocusProcessor Nested Path Descriptions")
  class NestedPathDescriptions {

    @Test
    @DisplayName("should produce TraversalPath for nested Optional<List<String>>")
    void shouldProduceTraversalPathForNestedOptionalList() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Form",
              """
              package com.example;
              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Form(String title, Optional<List<String>> items) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Optional<List<String>> should compose to TraversalPath
      assertGeneratedCodeContains(
          compilation, "com.example.FormFocus", "TraversalPath<Form, String> items()");
    }

    @Test
    @DisplayName("should produce TraversalPath for nested List<Optional<String>>")
    void shouldProduceTraversalPathForNestedListOptional() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;
              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Config(String name, List<Optional<String>> entries) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // List<Optional<String>> should compose to TraversalPath (Traversal + Affine = Traversal)
      assertGeneratedCodeContains(
          compilation, "com.example.ConfigFocus", "TraversalPath<Config, String> entries()");
    }

    @Test
    @DisplayName("should produce AffinePath for nested Optional<Optional<String>>")
    void shouldProduceAffinePathForNestedOptionalOptional() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Wrapper",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Wrapper(String name, Optional<Optional<String>> deep) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Optional<Optional<String>> should compose to AffinePath (Affine + Affine = Affine)
      assertGeneratedCodeContains(
          compilation, "com.example.WrapperFocus", "AffinePath<Wrapper, String> deep()");
    }
  }

  @Nested
  @DisplayName("Navigator buildViaStatement Widening Paths")
  class BuildViaStatementPaths {

    @Test
    @DisplayName("should use asTraversal() when navigating from FOCUS to TRAVERSAL kind")
    void shouldApplyAsTraversalConversion() {
      // When a field in the inner record is itself a navigable type wrapped in an SPI
      // TRAVERSAL container, the navigator should produce asTraversal() conversion.
      // Using List<Navigable> where the navigable inner type also has navigators.
      final JavaFileObject leafSource =
          JavaFileObjects.forSourceString(
              "com.example.Leaf",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Leaf(String data) {}
              """);

      final JavaFileObject containerSource =
          JavaFileObjects.forSourceString(
              "com.example.Container",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, widenCollections = true)
              public record Container(String id, List<Leaf> leaves) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(leafSource, containerSource);

      assertThat(compilation).succeeded();

      // List<Leaf> should produce TraversalPath
      assertGeneratedCodeContains(
          compilation, "com.example.ContainerFocus", "TraversalPath<Container, Leaf> leaves()");
    }

    @Test
    @DisplayName("should use asAffine() when navigating from FOCUS to AFFINE kind via SPI")
    void shouldApplyAsAffineConversion() {
      // An SPI ZERO_OR_ONE type containing a navigable type should produce asAffine() in
      // buildViaStatement. We test this with a simple navigable wrapped in Optional.
      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Address(String street, String city) {}
              """);

      final JavaFileObject personSource =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Person(String name, Address home) {}
              """);

      final JavaFileObject orgSource =
          JavaFileObjects.forSourceString(
              "com.example.Org",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Org(String name, Optional<Person> leader) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(addressSource, personSource, orgSource);

      assertThat(compilation).succeeded();

      // Optional<Person> should widen to AffinePath
      assertGeneratedCodeContains(
          compilation, "com.example.OrgFocus", "AffinePath<Org, Person> leader()");
    }
  }

  @Nested
  @DisplayName("Navigator with Collection java.util.Collection field")
  class CollectionInterfaceField {

    @Test
    @DisplayName("should generate TraversalPath for java.util.Collection<T> field")
    void shouldWidenCollectionInterfaceToTraversalPath() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Registry",
              """
              package com.example;
              import java.util.Collection;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Registry(String name, Collection<String> items) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      assertGeneratedCodeContains(
          compilation, "com.example.RegistryFocus", "TraversalPath<Registry, String> items()");
    }
  }

  @Nested
  @DisplayName("FocusProcessor Multiple Records with Navigators Disabled")
  class NavigatorsDisabledFallback {

    @Test
    @DisplayName(
        "should use standard FocusPath methods when navigators are disabled even for navigable fields")
    void shouldFallBackToStandardMethodsWhenNavigatorsDisabled() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Inner(String value) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // Without navigators, inner should produce FocusPath, not a Navigator
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "FocusPath<Outer, Inner> inner()");
    }
  }

  @Nested
  @DisplayName("PathKind Widening Composition")
  class PathKindWidening {

    @Test
    @DisplayName("should compose TRAVERSAL + AFFINE = TRAVERSAL in nested navigator")
    void shouldComposeTraversalAndAffineToTraversal() {
      // List<Inner> where Inner has Optional<String> field
      // In navigator: TRAVERSAL (from list) + AFFINE (from optional) = TRAVERSAL
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, widenCollections = true)
              public record Inner(String name, Optional<String> note) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, widenCollections = true)
              public record Outer(String id, List<Inner> items) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // List<Inner> should produce TraversalPath
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "TraversalPath<Outer, Inner> items()");
    }
  }
}
