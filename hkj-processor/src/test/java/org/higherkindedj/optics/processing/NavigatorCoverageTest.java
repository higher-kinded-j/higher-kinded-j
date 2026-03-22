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
 * Coverage tests for NavigatorClassGenerator targeting missed branches.
 *
 * <p>Covers: FocusPath navigator delegate methods, widened navigation methods via
 * buildViaStatement, buildPathWidening for Optional/Collection within navigators, navigator
 * filtering via include/exclude fields, field name collision with delegate methods, and navigator
 * depth limiting.
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
  @DisplayName("buildViaStatement Widening")
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
          javac()
              .withProcessors(new FocusProcessor())
              .compile(nullableAnnotation, innerSource, outerSource);

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
          javac()
              .withProcessors(new FocusProcessor())
              .compile(nullableAnnotation, innerSource, outerSource);

      assertThat(compilation).succeeded();

      // @Nullable Inner makes navigator AFFINE context; Inner's List<String> tags
      // widens to TRAVERSAL. AFFINE.widen(TRAVERSAL) = TRAVERSAL.
      // So tags() in the navigator should return TraversalPath<S, String>
      assertGeneratedCodeContains(
          compilation, "com.example.AffineOuterFocus", "TraversalPath<S, String> tags()");
    }
  }
}
