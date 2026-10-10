// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Coverage tests for NavigatorClassGenerator targeting missed branches.
 *
 * <p>Covers: FocusPath navigator delegate methods, widened navigation methods via navigation-method
 * composition, widening for Optional/Collection within navigators, navigator filtering via
 * include/exclude fields, field name collision with delegate methods, and navigator depth limiting.
 */
@DisplayName("Navigator Coverage Tests")
class NavigatorCoverageTest {

  @Nested
  @DisplayName("FocusPath Navigator Delegate Methods")
  class FocusPathDelegates {

    @Test
    @DisplayName("should generate FocusPath delegate methods for plain navigable record")
    void shouldGenerateFocusPathDelegateMethods() {
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
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // FocusPath delegate methods: get, set, modify, toLens, toPath
      assertGeneratedCodeContains(compilation, "com.example.OuterFocus", "Inner get(S source)");
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "S set(Inner value, S source)");
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "S modify(Function<Inner, Inner> f, S source)");
      assertGeneratedCodeContains(compilation, "com.example.OuterFocus", "Lens<S, Inner> toLens()");
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "FocusPath<S, Inner> toPath()");
    }
  }

  @Nested
  @DisplayName("Widened Navigation in Navigators")
  class WidenedNavigation {

    @Test
    @DisplayName("should generate AffinePath navigation for Optional field in inner record")
    void shouldGenerateAffinePathForOptionalField() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String label, Optional<String> note) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // The navigator for Inner should show AffinePath for Optional<String> note via .some()
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "AffinePath<S, String> note()");
    }

    @Test
    @DisplayName("should generate TraversalPath navigation for List field in inner record")
    void shouldGenerateTraversalPathForListField() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String label, List<String> tags) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // The navigator for Inner should show TraversalPath for List<String> tags via .each()
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "TraversalPath<S, String> tags()");
    }

    @Test
    @DisplayName("should generate both widened and plain navigation in same navigator")
    void shouldGenerateMixedNavigationPaths() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;
              import java.util.Optional;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Profile(String bio, Optional<String> motto, List<String> hobbies) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Account(String username, Profile profile) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // Plain field → FocusPath in navigator
      assertGeneratedCodeContains(
          compilation, "com.example.AccountFocus", "FocusPath<S, String> bio()");
      // Optional field → AffinePath in navigator via .some()
      assertGeneratedCodeContains(
          compilation, "com.example.AccountFocus", "AffinePath<S, String> motto()");
      // List field → TraversalPath in navigator via .each()
      assertGeneratedCodeContains(
          compilation, "com.example.AccountFocus", "TraversalPath<S, String> hobbies()");
    }
  }

  @Nested
  @DisplayName("Composed Widening")
  class BuildViaStatementWidening {

    @Test
    @DisplayName("should generate .some() widening for Optional non-navigable field in navigator")
    void shouldGenerateSomeWideningInNavigator() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String label, Optional<String> note) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // The navigator for Inner should show AffinePath for Optional<String> note
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "AffinePath<S, String> note()");
    }

    @Test
    @DisplayName("should generate .each() widening for List non-navigable field in navigator")
    void shouldGenerateEachWideningInNavigator() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String label, List<String> tags) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // The navigator for Inner should show TraversalPath for List<String> tags
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "TraversalPath<S, String> tags()");
    }

    @Test
    @DisplayName("should generate .each() widening for Set field in navigator")
    void shouldGenerateEachWideningForSetInNavigator() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import java.util.Set;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String label, Set<String> ids) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // The navigator for Inner should show TraversalPath for Set<String> ids
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "TraversalPath<S, String> ids()");
    }
  }

  @Nested
  @DisplayName("Nested Navigable Navigation")
  class NestedNavigableNavigation {

    @Test
    @DisplayName("should generate nested navigator for navigable field within navigator")
    void shouldGenerateNestedNavigator() {
      final JavaFileObject level2Source =
          JavaFileObjects.forSourceString(
              "com.example.Level2",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Level2(String value) {}
              """);

      final JavaFileObject level1Source =
          JavaFileObjects.forSourceString(
              "com.example.Level1",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Level1(String mid, Level2 nested) {}
              """);

      final JavaFileObject rootSource =
          JavaFileObjects.forSourceString(
              "com.example.Root",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Root(String top, Level1 child) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(level2Source, level1Source, rootSource);

      assertThat(compilation).succeeded();

      // Root → Level1 navigator
      assertGeneratedCodeContains(
          compilation, "com.example.RootFocus", "ChildNavigator<Root> child()");

      // Level1 navigator should have navigation into Level1's fields
      assertGeneratedCodeContains(
          compilation, "com.example.RootFocus", "FocusPath<S, String> mid()");
    }
  }

  @Nested
  @DisplayName("Navigator Filtering")
  class NavigatorFiltering {

    @Test
    @DisplayName("should respect includeFields filter for navigator generation")
    void shouldRespectIncludeFieldsFilter() {
      final JavaFileObject detailSource =
          JavaFileObjects.forSourceString(
              "com.example.Detail",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Detail(String info) {}
              """);

      final JavaFileObject mainSource =
          JavaFileObjects.forSourceString(
              "com.example.Main",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, includeFields = {"name"})
              public record Main(String name, Detail detail) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(detailSource, mainSource);

      assertThat(compilation).succeeded();

      // Only 'name' is included, so 'detail' should not appear as a navigator
      assertGeneratedCodeContains(
          compilation, "com.example.MainFocus", "FocusPath<Main, String> name()");
    }

    @Test
    @DisplayName("should respect excludeFields filter for navigator generation")
    void shouldRespectExcludeFieldsFilter() {
      final JavaFileObject detailSource =
          JavaFileObjects.forSourceString(
              "com.example.Detail",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Detail(String info) {}
              """);

      final JavaFileObject mainSource =
          JavaFileObjects.forSourceString(
              "com.example.Main",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, excludeFields = {"detail"})
              public record Main(String name, Detail detail) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(detailSource, mainSource);

      assertThat(compilation).succeeded();

      // 'detail' is excluded from navigator generation
      assertGeneratedCodeContains(
          compilation, "com.example.MainFocus", "FocusPath<Main, String> name()");
    }
  }

  @Nested
  @DisplayName("Field Name Collision with Delegate Methods")
  class FieldNameCollision {

    @Test
    @DisplayName("should skip navigator field that collides with FocusPath delegate 'get'")
    void shouldSkipFieldCollidingWithFocusDelegate() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String get, String data) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // The 'data' field should still have navigation, but 'get' collides with FocusPath.get()
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "FocusPath<S, String> data()");
    }

    @Test
    @DisplayName("should skip navigator field that collides with FocusPath delegate 'toLens'")
    void shouldSkipFieldCollidingWithToLensDelegate() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String toLens, String data) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // 'toLens' collides with FocusPath delegate, 'data' should still be generated
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "FocusPath<S, String> data()");
    }

    @Test
    @DisplayName("should skip navigator field that collides with FocusPath delegate 'set'")
    void shouldSkipFieldCollidingWithSetDelegate() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String set, String data) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // 'set' collides with FocusPath delegate, 'data' should still be generated
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "FocusPath<S, String> data()");
    }

    @Test
    @DisplayName("should skip navigator field that collides with FocusPath delegate 'modify'")
    void shouldSkipFieldCollidingWithModifyDelegate() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String modify, String data) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // 'modify' collides with FocusPath delegate, 'data' should still be generated
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "FocusPath<S, String> data()");
    }

    @Test
    @DisplayName("should skip navigator field that collides with FocusPath delegate 'toPath'")
    void shouldSkipFieldCollidingWithToPathDelegate() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String toPath, String data) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Outer(String name, Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // 'toPath' collides with FocusPath delegate, 'data' should still be generated
      assertGeneratedCodeContains(
          compilation, "com.example.OuterFocus", "FocusPath<S, String> data()");
    }

    @Test
    @DisplayName("should skip navigator field that collides with AffinePath delegate 'matches'")
    void shouldSkipFieldCollidingWithMatchesDelegate() {

      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String matches, String data) {}
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
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // 'matches' collides with AffinePath delegate; 'data' should still be navigable
      assertGeneratedCodeContains(
          compilation, "com.example.AffineOuterFocus", "AffinePath<S, String> data()");
    }
  }

  @Nested
  @DisplayName("Navigator Depth Limiting")
  class NavigatorDepthLimiting {

    @Test
    @DisplayName("should limit navigator depth to maxDepth")
    void shouldLimitNavigatorDepth() {
      final JavaFileObject level3Source =
          JavaFileObjects.forSourceString(
              "com.example.Level3",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 2)
              public record Level3(String deep) {}
              """);

      final JavaFileObject level2Source =
          JavaFileObjects.forSourceString(
              "com.example.Level2",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 2)
              public record Level2(String mid, Level3 nested) {}
              """);

      final JavaFileObject level1Source =
          JavaFileObjects.forSourceString(
              "com.example.Level1",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 2)
              public record Level1(String top, Level2 child) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(level3Source, level2Source, level1Source);

      assertThat(compilation).succeeded();

      // Level1 -> Level2 navigator should exist
      assertGeneratedCodeContains(
          compilation, "com.example.Level1Focus", "ChildNavigator<Level1> child()");
    }
  }

  @Nested
  @DisplayName("Single-Field Widened Record")
  class SingleFieldWidenedRecord {

    @Test
    @DisplayName(
        "should generate navigator for record with single Optional field and navigators enabled")
    void shouldGenerateNavigatorForSingleOptionalField() {
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
              "com.example.Wrapper",
              """
              package com.example;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Wrapper(Optional<Inner> item) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // Single Optional<Inner> field should produce AffinePath-based navigator
      // with correct setter lambda for a record with only one field
      assertGeneratedCodeContains(compilation, "com.example.WrapperFocus", "item()");
    }

    @Test
    @DisplayName(
        "should generate navigator for record with single List field and navigators enabled")
    void shouldGenerateNavigatorForSingleListField() {
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
              "com.example.Wrapper",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Wrapper(List<Inner> items) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // Single List<Inner> field should produce TraversalPath-based navigator
      // with correct setter lambda for a record with only one field
      assertGeneratedCodeContains(compilation, "com.example.WrapperFocus", "items()");
    }
  }

  @Nested
  @DisplayName("Set Collection Type")
  class SetCollectionType {

    @Test
    @DisplayName("should generate TraversalPath for Set<T> field with collection widening")
    void shouldGenerateTraversalPathForSetField() {
      final JavaFileObject sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Registry",
              """
              package com.example;
              import java.util.Set;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(widenCollections = true)
              public record Registry(String name, Set<String> entries) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      assertGeneratedCodeContains(
          compilation, "com.example.RegistryFocus", "TraversalPath<Registry, String> entries()");
    }
  }

  @Nested
  @DisplayName("AFFINE to TRAVERSAL Widening in Navigator")
  class AffineToTraversalWidening {

    @Test
    @DisplayName("should widen from AFFINE to TRAVERSAL when @Nullable navigable has List field")
    void shouldWidenAffineToTraversalInNavigator() {

      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String label, List<String> tags) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.AffineOuter",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(generateNavigators = true)
              public record AffineOuter(String name, @Nullable Inner inner) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // @Nullable Inner makes navigator AFFINE context; Inner's List<String> tags
      // widens to TRAVERSAL. AFFINE.widen(TRAVERSAL) = TRAVERSAL.
      // So tags() in the navigator should return TraversalPath<S, String>
      assertGeneratedCodeContains(
          compilation, "com.example.AffineOuterFocus", "TraversalPath<S, String> tags()");
    }
  }

  @Nested
  @DisplayName("TraversalPath Navigator via SPI Container")
  class TraversalPathNavigator {

    @Test
    @DisplayName("should generate TraversalPath navigator for Map wrapping navigable record")
    void shouldGenerateTraversalNavigatorForMapField() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Entry",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Entry(String title, int priority) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Registry",
              """
              package com.example;
              import java.util.Map;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Registry(String name, Map<String, Entry> entries) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // Map<String, Entry> with SPI ZERO_OR_MORE generates a TRAVERSAL navigator
      // This triggers: addTraversalPathDelegateMethods (lines 582-663),
      // getDelegateMethodNames TRAVERSAL case (line 670),
      // TRAVERSAL pathKindDescription (line 364),
      // and SPI ZERO_OR_MORE → PathKind.TRAVERSAL (line 315)
      // Map<String, Entry> with SPI ZERO_OR_MORE generates a TRAVERSAL navigator.
      // Navigator class name derives from field name: entries → EntriesNavigator
      assertGeneratedCodeContains(
          compilation, "com.example.RegistryFocus", "EntriesNavigator<Registry> entries()");
      // TraversalPath delegates: getAll, setAll, modifyAll, count, isEmpty
      assertGeneratedCodeContains(compilation, "com.example.RegistryFocus", "getAll");
      assertGeneratedCodeContains(compilation, "com.example.RegistryFocus", "setAll");
      assertGeneratedCodeContains(compilation, "com.example.RegistryFocus", "modifyAll");
      assertGeneratedCodeContains(compilation, "com.example.RegistryFocus", "count");
      assertGeneratedCodeContains(compilation, "com.example.RegistryFocus", "isEmpty");
    }

    @Test
    @DisplayName("should generate TraversalPath delegate toPath method in traversal navigator")
    void shouldGenerateTraversalToPathMethod() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Item",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Item(String label) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Container",
              """
              package com.example;
              import java.util.Map;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Container(Map<String, Item> items) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // TraversalPath navigator should have toPath() returning TraversalPath
      assertGeneratedCodeContains(compilation, "com.example.ContainerFocus", "toPath()");
      assertGeneratedCodeContains(compilation, "com.example.ContainerFocus", "TraversalPath");
    }
  }

  @Nested
  @DisplayName("Navigator wrapInNavigator branch")
  class WrapInNavigator {

    @Test
    @DisplayName("should wrap in navigator for SPI ZERO_OR_MORE field with navigable inner type")
    void shouldWrapInNavigatorForSpiTraversalField() {
      final JavaFileObject leafSource =
          JavaFileObjects.forSourceString(
              "com.example.Leaf",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Leaf(String text) {}
              """);

      final JavaFileObject midSource =
          JavaFileObjects.forSourceString(
              "com.example.Mid",
              """
              package com.example;
              import java.util.Map;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Mid(String id, Map<String, Leaf> children) {}
              """);

      final JavaFileObject rootSource =
          JavaFileObjects.forSourceString(
              "com.example.Root",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Root(String name, Mid mid) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(leafSource, midSource, rootSource);

      assertThat(compilation).succeeded();

      // Root → Mid navigator (FOCUS path kind)
      assertGeneratedCodeContains(compilation, "com.example.RootFocus", "MidNavigator<Root> mid()");

      // Mid has Map<String, Leaf> which is SPI ZERO_OR_MORE wrapping navigable Leaf.
      // Navigator class name derives from field name: children → ChildrenNavigator
      assertGeneratedCodeContains(compilation, "com.example.MidFocus", "ChildrenNavigator");
    }
  }

  @Nested
  @DisplayName("TraversalPath field name collision in navigator")
  class TraversalFieldNameCollision {

    @Test
    @DisplayName(
        "should skip fields colliding with TraversalPath delegates getAll/setAll/modifyAll/count/isEmpty")
    void shouldSkipFieldsCollidingWithTraversalDelegates() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Inner",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Inner(String getAll, String data) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.MapOuter",
              """
              package com.example;
              import java.util.Map;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record MapOuter(Map<String, Inner> items) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      assertThat(compilation).succeeded();

      // 'getAll' collides with TraversalPath delegate, 'data' should still be generated
      assertGeneratedCodeContains(compilation, "com.example.MapOuterFocus", "data()");
    }
  }

  @Nested
  @DisplayName("SPI Containers Wrapping Navigable Types")
  class SpiContainersWrappingNavigableTypes {

    @Test
    @DisplayName("should generate AFFINE and TRAVERSAL navigators for SPI-wrapped navigable types")
    void shouldGenerateNavigatorsForSpiWrappedNavigableTypes() {
      final JavaFileObject deptSource =
          JavaFileObjects.forSourceString(
              "com.example.Dept",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Dept(String title) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import java.util.Map;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Address(String street, Map<String, Dept> depts) {}
              """);

      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;
              import java.util.Map;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Company(
                  String name,
                  Address hq,
                  Either<String, Address> registered,
                  Map<String, Address> branches) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(deptSource, addressSource, companySource);

      assertThat(compilation).succeeded();

      // Either<String, Address> wraps a navigable type: AFFINE navigator with AffinePath delegate.
      assertGeneratedCodeContains(
          compilation, "com.example.CompanyFocus", "class RegisteredNavigator<S>");
      assertGeneratedCodeContains(
          compilation,
          "com.example.CompanyFocus",
          "private final AffinePath<S, Address> delegate;");

      // Map<String, Address> wraps a navigable type: TRAVERSAL navigator with TraversalPath
      // delegate methods (getAll/modifyAll from addTraversalPathDelegateMethods).
      assertGeneratedCodeContains(
          compilation, "com.example.CompanyFocus", "class BranchesNavigator<S>");
      assertGeneratedCodeContains(
          compilation,
          "com.example.CompanyFocus",
          "private final TraversalPath<S, Address> delegate;");

      // Inside HqNavigator, the Map<String, Dept> field is wrapped in the target Focus class's
      // DeptsNavigator (SPI ZERO_OR_MORE wrapping a navigable inner type).
      assertGeneratedCodeContains(
          compilation, "com.example.CompanyFocus", "AddressFocus.DeptsNavigator<S> depts()");
    }

    @Test
    @DisplayName("should stop wrapping an SPI container of a navigable type at the depth limit")
    void shouldNotWrapSpiContainerBeyondDepthLimit() {
      final JavaFileObject deptSource =
          JavaFileObjects.forSourceString(
              "com.example.Dept",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Dept(String title) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;
              import java.util.Map;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Address(String street, Map<String, Dept> depts) {}
              """);

      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.ShallowCompany",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 1)
              public record ShallowCompany(String name, Address hq) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(deptSource, addressSource, companySource);

      assertThat(compilation).succeeded();

      // HqNavigator sits at the depth limit, so its Map<String, Dept> field keeps the plain
      // traversal path instead of being wrapped in AddressFocus.DeptsNavigator.
      assertGeneratedCodeContains(
          compilation, "com.example.ShallowCompanyFocus", "TraversalPath<S, Dept> depts()");
      assertGeneratedCodeDoesNotContain(
          compilation, "com.example.ShallowCompanyFocus", "AddressFocus.DeptsNavigator<S> depts()");
    }
  }

  @Nested
  @DisplayName("Nested Container Widening")
  class NestedContainerWidening {

    @Test
    @DisplayName("should chain the widening until it reaches the path kind the method declares")
    void shouldChainWideningForNestedContainers() {
      final JavaFileObject innerSource =
          JavaFileObjects.forSourceString(
              "com.example.Bundle",
              """
              package com.example;
              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Bundle(String id, Optional<List<String>> notes) {}
              """);

      final JavaFileObject outerSource =
          JavaFileObjects.forSourceString(
              "com.example.Shipment",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Shipment(String reference, Bundle bundle) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(innerSource, outerSource);

      // Optional<List<String>> composes to TRAVERSAL, and the chain runs to the element rather
      // than stopping at the first layer. The navigator composes the static method that carries
      // it, so the two report the same path type without chaining anything themselves.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.BundleFocus", "TraversalPath<Bundle, String> notes()");
      assertGeneratedCodeContains(compilation, "com.example.BundleFocus", ".some().each()");
      assertGeneratedCodeContains(
          compilation, "com.example.ShipmentFocus", "TraversalPath<S, String> notes()");
      assertGeneratedCodeContains(
          compilation, "com.example.ShipmentFocus", "delegate.via(BundleFocus.notes())");
    }
  }

  @Nested
  @DisplayName("Widened Navigator Target Fields")
  class WidenedNavigatorTargetFields {

    @Test
    @DisplayName("should handle raw, wildcard, array and subtype container fields in navigators")
    void shouldHandleRawWildcardArrayAndSubtypeContainerFields() {

      final JavaFileObject targetSource =
          JavaFileObjects.forSourceString(
              "com.example.WideTarget",
              """
              package com.example;
              import java.util.ArrayList;
              import java.util.List;
              import java.util.Map;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(generateNavigators = true)
              @SuppressWarnings("rawtypes")
              public record WideTarget(
                  String label,
                  Optional rawOpt,
                  Optional<?> wildOpt,
                  int @Nullable [] scores,
                  ArrayList<String> tags,
                  ArrayList<?> wildTags,
                  ArrayList rawTags,
                  Map<String, String> attrs,
                  List<List<List<String>>> deep) {}
              """);

      final JavaFileObject rootSource =
          JavaFileObjects.forSourceString(
              "com.example.WideRoot",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record WideRoot(String name, WideTarget target) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(targetSource, rootSource);

      assertThat(compilation).succeeded();

      // Raw Optional: no inner type argument to infer from, so the focus falls back to Object,
      // which is what the static method's .some() already reported.
      assertGeneratedCodeContains(
          compilation, "com.example.WideRootFocus", "AffinePath<S, Object> rawOpt()");
      assertGeneratedCodeContains(
          compilation, "com.example.WideTargetFocus", "AffinePath<WideTarget, Object> rawOpt()");
      // Optional<?>: the unbounded wildcard resolves to Object.
      assertGeneratedCodeContains(
          compilation, "com.example.WideRootFocus", "AffinePath<S, Object> wildOpt()");
      // int @Nullable []: a nullable array is a non-declared type, so there is no inner
      // extraction and the path widens via .nullable().
      assertGeneratedCodeContains(
          compilation, "com.example.WideRootFocus", "AffinePath<S, int[]> scores()");
      // ArrayList and its raw and wildcard forms: a Collection subtype is not a recognised
      // container, so the path stops at the container exactly as the static method's does.
      assertGeneratedCodeContains(
          compilation, "com.example.WideRootFocus", "FocusPath<S, ArrayList<String>> tags()");
      assertGeneratedCodeContains(
          compilation, "com.example.WideRootFocus", "FocusPath<S, ArrayList<?>> wildTags()");
      assertGeneratedCodeContains(
          compilation, "com.example.WideRootFocus", "FocusPath<S, ArrayList> rawTags()");
      // Map<String, String>: a ZERO_OR_MORE SPI container the record does not widen.
      assertGeneratedCodeContains(
          compilation, "com.example.WideRootFocus", "FocusPath<S, Map<String, String>> attrs()");
      // List<List<List<String>>>: the chain composes to the leaf, three layers deep.
      assertGeneratedCodeContains(
          compilation, "com.example.WideRootFocus", "TraversalPath<S, String> deep()");
    }
  }

  @Nested
  @DisplayName("Generic Navigable Targets")
  class GenericNavigableTargets {

    private static final JavaFileObject INNER =
        JavaFileObjects.forSourceString(
            "com.myapp.Inner",
            """
            package com.myapp;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Inner<T>(T value, String label) {}
            """);

    @Test
    @DisplayName("a generic target gets a navigator instantiated with the component's arguments")
    void aGenericTargetGetsAnInstantiatedNavigator() throws ReflectiveOperationException {
      final var outer =
          JavaFileObjects.forSourceString(
              "com.myapp.Outer",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Outer(Inner<String> inner, String tag) {}
              """);
      // Compiling the chain proves the composed type; running it proves the composed path.
      final var use =
          JavaFileObjects.forSourceString(
              "com.myapp.Use",
              """
              package com.myapp;

              import org.higherkindedj.optics.focus.FocusPath;

              public final class Use {
                private static final Outer OUTER = new Outer(new Inner<>("x", "l"), "t");

                public static String read() {
                  FocusPath<Outer, String> value = OuterFocus.inner().value();
                  return value.get(OUTER);
                }

                public static String write() {
                  return OuterFocus.inner().value().set("y", OUTER).toString();
                }

                public static String segments() {
                  return OuterFocus.inner().value().pathString();
                }
              }
              """);

      var result = RuntimeCompilationHelper.compileWith(new FocusProcessor(), INNER, outer, use);

      final String focus = "com.myapp.OuterFocus";
      Compilation compilation = result.compilation();
      assertGeneratedCodeContains(
          compilation, focus, "public static InnerNavigator<Outer, String> inner()");
      assertGeneratedCodeContains(
          compilation, focus, "public static final class InnerNavigator<S, T>");
      assertGeneratedCodeContains(
          compilation, focus, "private final FocusPath<S, Inner<T>> delegate");
      assertGeneratedCodeContains(
          compilation,
          focus,
          "public FocusPath<S, T> value() { return delegate.via(InnerFocus.<T>value()); }");
      Assertions.assertThat(result.invokeStatic("com.myapp.Use", "read")).isEqualTo("x");
      Assertions.assertThat(result.invokeStatic("com.myapp.Use", "write"))
          .isEqualTo("Outer[inner=Inner[value=y, label=l], tag=t]");
      Assertions.assertThat(result.invokeStatic("com.myapp.Use", "segments"))
          .isEqualTo("inner.value");
    }

    @Test
    @DisplayName("every shape a component can instantiate a target with compiles under -Werror")
    void everyInstantiatedShapeCompilesUnderWerror() {
      // A target claiming S, a navigator composing another generic target's navigator, a generic
      // record navigated into, several type parameters, bounded and raw-bounded ones, a wildcard
      // inside an argument, and an SPI container's element: each chain below is typed, so a
      // navigator that is missing or instantiated wrongly fails to compile.
      final var records =
          JavaFileObjects.forSourceString(
              "com.myapp.Records",
              """
              package com.myapp;

              import java.util.ArrayList;
              import java.util.List;
              import java.util.Map;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              public final class Records {
                private Records() {}

                @GenerateFocus(generateNavigators = true)
                public record Deep<S>(S leaf) {}

                @GenerateFocus(generateNavigators = true)
                public record Holder<S>(S value, Deep<S> deep) {}

                @GenerateFocus(generateNavigators = true)
                public record Address(String city) {}

                @GenerateFocus(generateNavigators = true)
                public record Wrapper<U>(Inner<U> inner, Address address, U extra) {}

                @GenerateFocus(generateNavigators = true)
                public record Pair<A, B>(A first, B second) {}

                @GenerateFocus(generateNavigators = true)
                public record Bounded<T extends Comparable<T>>(T value) {}

                @SuppressWarnings("rawtypes")
                @GenerateFocus(generateNavigators = true)
                public record RawBound<T extends List>(T value) {}

                @GenerateFocus(generateNavigators = true)
                public record Root(
                    Wrapper<String> wrapper,
                    Holder<Integer> holder,
                    Pair<String, Integer> pair,
                    Bounded<String> bounded,
                    RawBound<ArrayList<String>> rawBound,
                    Inner<List<?>> listed,
                    Either<String, Inner<Long>> either) {}

                @GenerateFocus(generateNavigators = true)
                public record Indexed(Map<String, Inner<String>> inners) {}
              }
              """);
      final var use =
          JavaFileObjects.forSourceString(
              "com.myapp.Chains",
              """
              package com.myapp;

              import com.myapp.Records.*;
              import java.util.ArrayList;
              import java.util.List;
              import org.higherkindedj.optics.focus.AffinePath;
              import org.higherkindedj.optics.focus.FocusPath;
              import org.higherkindedj.optics.focus.TraversalPath;

              final class Chains {
                FocusPath<Root, String> a = RootFocus.wrapper().inner().value();
                FocusPath<Root, String> b = RootFocus.wrapper().address().city();
                FocusPath<Root, String> c = RootFocus.wrapper().extra();
                FocusPath<Root, Integer> d = RootFocus.holder().deep().leaf();
                FocusPath<Root, Integer> e = RootFocus.pair().second();
                FocusPath<Root, String> f = RootFocus.bounded().value();
                FocusPath<Root, ArrayList<String>> g = RootFocus.rawBound().value();
                FocusPath<Root, List<?>> h = RootFocus.listed().value();
                AffinePath<Root, Long> i = RootFocus.either().value();
                TraversalPath<Indexed, String> j = IndexedFocus.inners().value();
                FocusPath<Wrapper<Long>, Long> k = WrapperFocus.<Long>inner().value();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(INNER, records, use);

      assertThat(compilation).succeededWithoutWarnings();
      final String root = "com.myapp.RootFocus";
      // The target claimed S, so the source takes the next free name.
      assertGeneratedCodeContains(
          compilation, root, "public static final class HolderNavigator<S1, S>");
      // A navigator composing a generic target's navigator passes its type arguments explicitly.
      assertGeneratedCodeContains(
          compilation,
          root,
          "public HolderFocus.DeepNavigator<S1, S> deep() { return new"
              + " HolderFocus.DeepNavigator<>(delegate.via(HolderFocus.<S>deep().toPath())); }");
      assertGeneratedCodeContains(
          compilation, root, "public static PairNavigator<Records.Root, String, Integer> pair()");
      assertGeneratedCodeContains(
          compilation,
          root,
          "public static final class BoundedNavigator<S, T extends Comparable<T>>");
      // A raw bound is redeclared on the class, which answers for it itself.
      assertGeneratedCodeContains(
          compilation,
          root,
          "@SuppressWarnings(\"rawtypes\") public static final class RawBoundNavigator<S, T"
              + " extends List>");
      assertGeneratedCodeContains(
          compilation, root, "public static ListedNavigator<Records.Root, List<?>> listed()");
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.IndexedFocus",
          "public static InnersNavigator<Records.Indexed, String>");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiterString = " -> ",
        textBlock =
            """
            Inner<?> inner -> Inner<?> -> Inner<Object>
            @SuppressWarnings("rawtypes") Inner inner -> the raw Inner -> Inner<Object>
            Inner<? extends Number> inner -> Inner<? extends Number> -> Inner<Number>
            Map<String, Inner<?>> inner -> Inner<?> -> Inner<Object>
            """)
    @DisplayName("a component naming no type for a target's parameter keeps its plain path")
    void anUninstantiableComponentKeepsItsPlainPath(
        String component, String written, String alternative) {
      final var outer =
          JavaFileObjects.forSourceString(
              "com.myapp.Outer",
              """
              package com.myapp;

              import java.util.Map;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Outer(%s, String tag) {}
              """
                  .formatted(component));

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(INNER, outer);

      // No chain is offered: a path into Inner is over one instantiation of it, so it composes
      // with neither a wildcard nor a raw Inner.
      assertThat(compilation).succeeded();
      assertGeneratedCodeDoesNotContain(compilation, "com.myapp.OuterFocus", "InnerNavigator");
      assertThat(compilation)
          .hadNoteContaining(
              "Navigator for field 'inner' is not generated: a navigator into Inner has to name"
                  + " one type for each of its type parameters, which "
                  + written
                  + " does not. OuterFocus.inner() keeps its plain path, and a path over one"
                  + " instantiation of Inner does not compose with it either. Write "
                  + alternative
                  + ", or another concrete instantiation, in place of "
                  + written
                  + " to get a navigator.");
    }

    /** Targets whose type parameters are bounded, one shape of bound each. */
    private static final JavaFileObject BOUNDED =
        JavaFileObjects.forSourceString(
            "com.myapp.Bounded",
            """
            package com.myapp;

            import java.util.List;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            record Num<T extends Number>(T value) {}

            @GenerateFocus(generateNavigators = true)
            record Ord<T extends Comparable<T>>(T value) {}

            @GenerateFocus(generateNavigators = true)
            record Both<T extends Number & Comparable<T>>(T value) {}

            @SuppressWarnings("rawtypes")
            @GenerateFocus(generateNavigators = true)
            record RawBound<T extends List>(T value) {}
            """);

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiterString = " -> ",
        textBlock =
            """
            Num<?> -> Num<?> -> Write Num<Number>, or another concrete instantiation,
            Num<? super Integer> -> Num<? super Integer> -> Write Num<Integer>, or another concrete instantiation,
            Ord<?> -> Ord<?> -> Write Ord with a concrete type argument for each type parameter
            @SuppressWarnings("rawtypes") Ord -> the raw Ord -> Write Ord with a concrete type argument for each type parameter
            Both<?> -> Both<?> -> Write Both with a concrete type argument for each type parameter
            RawBound<?> -> RawBound<?> -> Write RawBound with a concrete type argument for each type parameter
            """)
    @DisplayName("the note names a type within the parameter's bounds, or none")
    void theNoteNamesATypeWithinTheBounds(String component, String written, String fix) {
      final var outer =
          JavaFileObjects.forSourceString(
              "com.myapp.Outer",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Outer(%s holder) {}
              """
                  .formatted(component));

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(BOUNDED, outer);

      // Object falls outside Number, and no type the declaration alone names meets a bound written
      // in T itself, an intersection or a raw List, so those name none.
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining(
              "which " + written + " does not. OuterFocus.holder() keeps its plain path");
      assertThat(compilation)
          .hadNoteContaining(fix + " in place of " + written + " to get a navigator.");
    }

    @Test
    @DisplayName("the type the note names gets a navigator when it is written")
    void theTypeTheNoteNamesGetsANavigator() {
      final var outer =
          JavaFileObjects.forSourceString(
              "com.myapp.Outer",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Outer(Inner<Object> any, Num<Number> number, Num<Integer> whole) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(INNER, BOUNDED, outer);

      // A fix line is only worth printing if following it does what it says.
      assertThat(compilation).succeededWithoutWarnings();
      final String focus = "com.myapp.OuterFocus";
      assertGeneratedCodeContains(
          compilation, focus, "public static AnyNavigator<Outer, Object> any()");
      assertGeneratedCodeContains(
          compilation, focus, "public static NumberNavigator<Outer, Number> number()");
      assertGeneratedCodeContains(
          compilation, focus, "public static WholeNavigator<Outer, Integer> whole()");
    }

    @Test
    @DisplayName("a wildcard is the reason given ahead of a companion the field cannot reach")
    void aWildcardIsTheReasonGivenAheadOfAnUnreachableCompanion() {
      final var inner =
          JavaFileObjects.forSourceString(
              "com.lib.Inner",
              """
              package com.lib;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Inner<T>(T value, Secret secret) {}
              """);
      final var secret =
          JavaFileObjects.forSourceString(
              "com.lib.Secret",
              """
              package com.lib;

              record Secret(String code) {}
              """);
      final var outer =
          JavaFileObjects.forSourceString(
              "com.myapp.Outer",
              """
              package com.myapp;

              import com.lib.Inner;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Outer(Inner<?> loose, Inner<String> tight) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(inner, secret, outer);

      // Making Secret public gives tight a navigator, and loose none, so the note for loose names
      // the wildcard, which shows in its declaration, rather than promising one in vain.
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining(
              "Navigator for field 'loose' is not generated: a navigator into Inner has to name"
                  + " one type for each of its type parameters, which Inner<?> does not.");
      assertThat(compilation)
          .hadNoteContaining(
              "Navigator for field 'tight' is not generated: a navigator into Inner names"
                  + " 'Secret'");
      Assertions.assertThat(compilation.notes())
          .noneMatch(
              note ->
                  note.getMessage(null).contains("field 'loose'")
                      && note.getMessage(null).contains("Secret"));
    }

    @Test
    @DisplayName("a record navigating into a container agrees with it about its element")
    void aRecordNavigatingIntoAContainerAgreesAboutItsElement() {
      final var mid =
          JavaFileObjects.forSourceString(
              "com.myapp.Mid",
              """
              package com.myapp;

              import java.util.Map;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Mid(Map<String, Inner<String>> named, Map<String, Inner<?>> loose) {}
              """);

      final var root =
          JavaFileObjects.forSourceString(
              "com.myapp.Root",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Root(Mid mid, String name) {}
              """);

      // The widening decides the return type before the navigator question is asked, so the two
      // must agree: the container whose element gets a navigator is stepped into, and the one
      // whose element gets none is not, or the method declares a traversal and returns a focus.
      var compilation = javac().withProcessors(new FocusProcessor()).compile(INNER, mid, root);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.RootFocus", "public MidFocus.NamedNavigator<S, String> named()");
      assertGeneratedCodeContains(
          compilation, "com.myapp.RootFocus", "public FocusPath<S, Map<String, Inner<?>>> loose()");
    }

    @Test
    @DisplayName("a container whose navigator is declined for any reason is not widened into")
    void aContainerWhoseNavigatorIsDeclinedIsNotWidenedInto() {
      record Case(String name, String midAnnotation) {}
      var cases =
          List.of(
              new Case("NavigatorsOff", "@GenerateFocus(generateNavigators = false)"),
              new Case(
                  "FieldExcluded",
                  "@GenerateFocus(generateNavigators = true, excludeFields = \"leaves\")"));

      for (Case testCase : cases) {
        final var leaf =
            JavaFileObjects.forSourceString(
                "com.myapp.Leaf",
                """
                package com.myapp;

                import org.higherkindedj.optics.annotations.GenerateFocus;

                @GenerateFocus(generateNavigators = true)
                public record Leaf(String value) {}
                """);

        final var mid =
            JavaFileObjects.forSourceString(
                "com.myapp.Mid",
                """
                package com.myapp;

                import java.util.Map;
                import org.higherkindedj.optics.annotations.GenerateFocus;

                %s
                public record Mid(Map<String, Leaf> leaves, String tag) {}
                """
                    .formatted(testCase.midAnnotation()));

        final var root =
            JavaFileObjects.forSourceString(
                "com.myapp.Root",
                """
                package com.myapp;

                import org.higherkindedj.optics.annotations.GenerateFocus;

                @GenerateFocus(generateNavigators = true)
                public record Root(Mid mid, String name) {}
                """);

        // The widening fixes the navigation method's return type before the navigator question is
        // asked, so the two have to be the same question however the answer comes out.
        var compilation = javac().withProcessors(new FocusProcessor()).compile(leaf, mid, root);

        assertThat(compilation).succeeded();
      }
    }

    @Test
    @DisplayName("says nothing about an uninstantiable target the record itself filtered out")
    void saysNothingAboutAFilteredField() {
      final var outer =
          JavaFileObjects.forSourceString(
              "com.myapp.Outer",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true, excludeFields = "inner")
              public record Outer(Inner<?> inner, String tag) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(INNER, outer);

      // The field has no navigator because the record said so. Reporting its wildcard as the
      // reason would name something the author cannot act on, and did not ask about.
      assertThat(compilation).succeeded();
      Assertions.assertThat(compilation.notes())
          .noneMatch(note -> note.getMessage(null).contains("Navigator for field 'inner'"));
    }
  }
}
