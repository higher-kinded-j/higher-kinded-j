// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.higherkindedj.optics.processing.FocusProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Compile-testing integration tests that verify SPI widening produces correct optic expressions in
 * generated Focus classes for third-party container types.
 */
@DisplayName("Third-party SPI widening in Focus generation")
class ThirdPartySpiWideningTest {

  @Nested
  @DisplayName("Eclipse Collections")
  class EclipseCollections {

    @Test
    @DisplayName("ImmutableList field should generate navigator with fromIterableCollecting")
    void immutableListNavigator() {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Item",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Item(String name, int price) {}
              """);

      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Basket",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.eclipse.collections.api.list.ImmutableList;
              @GenerateFocus(generateNavigators = true)
              public record Basket(String id, ImmutableList<Item> items) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      // The navigator should use fromIterableCollecting with Eclipse Lists factory
      assertGeneratedCodeContains(
          compilation,
          "com.example.BasketFocus",
          "EachInstances.fromIterableCollecting(list -> Lists.immutable.ofAll(list))");
    }

    @Test
    @DisplayName("MutableSet field should generate navigator with mutable factory")
    void mutableSetNavigator() {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Tag",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Tag(String label) {}
              """);

      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Article",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.eclipse.collections.api.set.MutableSet;
              @GenerateFocus(generateNavigators = true)
              public record Article(String title, MutableSet<Tag> tags) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      assertGeneratedCodeContains(
          compilation,
          "com.example.ArticleFocus",
          "EachInstances.fromIterableCollecting(list -> Sets.mutable.ofAll(list))");
    }
  }

  @Nested
  @DisplayName("Guava")
  class Guava {

    @Test
    @DisplayName("ImmutableList field should generate navigator with copyOf")
    void immutableListNavigator() {
      var inner =
          JavaFileObjects.forSourceString(
              "com.example.Entry",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus(generateNavigators = true)
              public record Entry(String key, String value) {}
              """);

      var outer =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import com.google.common.collect.ImmutableList;
              @GenerateFocus(generateNavigators = true)
              public record Config(String name, ImmutableList<Entry> entries) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(inner, outer);

      assertThat(compilation).succeeded();

      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", "ImmutableList::copyOf");
    }
  }
}
