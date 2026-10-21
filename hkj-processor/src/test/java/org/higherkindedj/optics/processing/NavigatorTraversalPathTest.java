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
import org.junit.jupiter.api.Test;

/**
 * Tests for NavigatorClassGenerator TraversalPath generation.
 *
 * <p>Covers the TRAVERSAL path kind description and List-of-navigable-type navigators.
 */
@DisplayName("Navigator TraversalPath Tests")
class NavigatorTraversalPathTest {

  @Test
  @DisplayName("should generate TraversalPath navigator for List<navigable> field")
  void shouldGenerateTraversalPathNavigatorForListField() {
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

    // Should generate a TraversalPath navigator for the List<Member> field
    assertGeneratedCodeContains(
        compilation, "com.example.TeamFocus", "MembersNavigator<Team> members()");
    assertGeneratedCodeContains(
        compilation, "com.example.TeamFocus", "TraversalPath<S, String> firstName()");
  }

  @Test
  @DisplayName("should generate navigator with maxDepth limiting recursion")
  void shouldLimitNavigatorDepthAtMaxDepth() {
    final JavaFileObject leaf =
        JavaFileObjects.forSourceString(
            "com.example.Leaf",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 1)
            public record Leaf(String value) {}
            """);

    final JavaFileObject branch =
        JavaFileObjects.forSourceString(
            "com.example.Branch",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 1)
            public record Branch(String name, Leaf leaf) {}
            """);

    Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(leaf, branch);

    assertThat(compilation).succeeded();
  }

  @Test
  @DisplayName("should handle array type widening to TraversalPath")
  void shouldHandleArrayTypeWidening() {
    final JavaFileObject source =
        JavaFileObjects.forSourceString(
            "com.example.Container",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus(widenCollections = true)
            public record Container(String name, int[] values) {}
            """);

    Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

    assertThat(compilation).succeeded();
  }

  @Test
  @DisplayName("should handle Optional<navigable> field widening to AffinePath navigator")
  void shouldHandleOptionalNavigableFieldWidening() {
    final JavaFileObject addressSource =
        JavaFileObjects.forSourceString(
            "com.example.Address",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus(generateNavigators = true)
            public record Address(String street, String city) {}
            """);

    final JavaFileObject personSource =
        JavaFileObjects.forSourceString(
            "com.example.Person",
            """
            package com.example;
            import java.util.Optional;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus(generateNavigators = true, widenCollections = true)
            public record Person(String name, Optional<Address> address) {}
            """);

    Compilation compilation =
        javac().withProcessors(new FocusProcessor()).compile(addressSource, personSource);

    assertThat(compilation).succeeded();

    // Optional<Address> should get a navigator over the AffinePath it widens to
    assertGeneratedCodeContains(
        compilation, "com.example.PersonFocus", "AddressNavigator<Person> address()");
    assertGeneratedCodeContains(
        compilation,
        "com.example.PersonFocus",
        "AddressNavigator<S> extends NavigatorAffinePath<S, Address>");
  }

  @Test
  @DisplayName("should handle nested Optional<List<X>> widening to TraversalPath")
  void shouldHandleNestedOptionalListWidening() {
    final JavaFileObject source =
        JavaFileObjects.forSourceString(
            "com.example.Config",
            """
            package com.example;
            import java.util.List;
            import java.util.Optional;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus(widenCollections = true)
            public record Config(String name, Optional<List<String>> items) {}
            """);

    Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);

    assertThat(compilation).succeeded();
  }
}
