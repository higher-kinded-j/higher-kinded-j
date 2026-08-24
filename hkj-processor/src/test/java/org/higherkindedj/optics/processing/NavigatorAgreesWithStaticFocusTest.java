// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The same component reports the same path type whichever way it is reached.
 *
 * <p>{@code ShapesFocus.arrayList()} and {@code OuterFocus.shapes().arrayList()} are generated from
 * one declaration by one processor run, and used to disagree about it: two analyses answered the
 * same question and nothing reconciled them (issue #719). The corpus below is one record holding
 * every container shape the processor recognises and several it does not, reached both ways, and
 * the two answers are compared field by field.
 *
 * <p>This is a guard on the agreement rather than on any one shape, so a new container, a new
 * setting, or a second analysis creeping back in fails here without anyone having to predict which
 * shape it would diverge on first.
 */
@DisplayName("Navigator methods agree with static Focus methods")
class NavigatorAgreesWithStaticFocusTest {

  /** A component, and the path type both routes must report for it. */
  private record Shape(String declaration, String field) {}

  private static final List<Shape> SHAPES =
      List.of(
          new Shape("String plain", "plain"),
          new Shape("List<String> list", "list"),
          new Shape("Set<String> stringSet", "stringSet"),
          new Shape("Collection<String> collection", "collection"),
          new Shape("ArrayList<String> arrayList", "arrayList"),
          new Shape("TreeSet<String> treeSet", "treeSet"),
          new Shape("Map<String, String> map", "map"),
          new Shape("Optional<String> option", "option"),
          new Shape("Optional<List<String>> optionOfList", "optionOfList"),
          new Shape("List<Optional<String>> listOfOption", "listOfOption"),
          new Shape("List<List<List<String>>> deep", "deep"),
          new Shape("Either<String, String> either", "either"),
          new Shape("Kind<ListKind.Witness, String> members", "members"),
          new Shape("String[] array", "array"),
          new Shape("@Nullable String nickname", "nickname"),
          new Shape("Leaf leaf", "leaf"),
          new Shape("Optional<Leaf> optionalLeaf", "optionalLeaf"),
          new Shape("List<Leaf> leaves", "leaves"),
          new Shape("Map<String, Leaf> leafMap", "leafMap"),
          new Shape("ArrayList<Leaf> leafArrayList", "leafArrayList"),
          new Shape("Either<String, Leaf> eitherLeaf", "eitherLeaf"));

  private static final JavaFileObject LEAF =
      JavaFileObjects.forSourceString(
          "com.example.Leaf",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Leaf(String name) {}
          """);

  private static final JavaFileObject OUTER =
      JavaFileObjects.forSourceString(
          "com.example.Outer",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Outer(Shapes shapes) {}
          """);

  private static JavaFileObject shapes(boolean widenCollections) {
    String components =
        SHAPES.stream().map(Shape::declaration).reduce((a, b) -> a + ",\n    " + b).orElseThrow();
    return JavaFileObjects.forSourceString(
        "com.example.Shapes",
        """
        package com.example;

        import java.util.ArrayList;
        import java.util.Collection;
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import java.util.Set;
        import java.util.TreeSet;
        import org.higherkindedj.hkt.Kind;
        import org.higherkindedj.hkt.either.Either;
        import org.higherkindedj.hkt.list.ListKind;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import org.jspecify.annotations.Nullable;

        @GenerateFocus(generateNavigators = true, widenCollections = %s)
        public record Shapes(
            %s) {}
        """
            .formatted(widenCollections, components));
  }

  @ParameterizedTest(name = "widenCollections = {0}")
  @ValueSource(booleans = {false, true})
  @DisplayName("should report the same path type whichever way a component is reached")
  void shouldReportTheSamePathTypeWhicheverWayAComponentIsReached(boolean widenCollections)
      throws Exception {

    Compilation compilation =
        javac().withProcessors(new FocusProcessor()).compile(shapes(widenCollections), OUTER, LEAF);

    assertThat(compilation.status())
        .as("the corpus compiles, generated source included")
        .isEqualTo(Compilation.Status.SUCCESS);

    Map<String, String> statics = returnTypes(compilation, "com.example.ShapesFocus", "static ");
    Map<String, String> navigated = returnTypes(compilation, "com.example.OuterFocus", "");

    String[] fields = SHAPES.stream().map(Shape::field).toArray(String[]::new);
    assertThat(statics).as("every component has a static Focus method").containsKeys(fields);
    assertThat(navigated).as("every component has a navigator method").containsKeys(fields);

    for (Shape shape : SHAPES) {
      assertThat(navigated.get(shape.field()))
          .as("%s reached through a navigator", shape.declaration())
          .isEqualTo(statics.get(shape.field()));
    }

    // Spot-checks, so that a normalisation that quietly matched everything would fail here: the
    // shapes the two routes used to disagree on, and the one they agreed on by both giving up.
    assertThat(navigated)
        .containsEntry("arrayList", "FocusPath<_, ArrayList<String>>")
        .containsEntry("treeSet", "FocusPath<_, TreeSet<String>>")
        .containsEntry("listOfOption", "TraversalPath<_, String>")
        .containsEntry("deep", "TraversalPath<_, String>")
        .containsEntry("members", "TraversalPath<_, String>")
        .containsEntry("eitherLeaf", "EitherLeafNavigator<_>")
        .containsEntry(
            "map",
            widenCollections ? "TraversalPath<_, String>" : "FocusPath<_, Map<String, String>>");
  }

  @Test
  @DisplayName("should compose a navigator whose delegate is wider than the target's own")
  void shouldComposeANavigatorWiderThanItsTargets() {
    JavaFileObject outer =
        JavaFileObjects.forSourceString(
            "com.example.Root",
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Root(Map<String, Mid> mids) {}
            """);
    JavaFileObject mid =
        JavaFileObjects.forSourceString(
            "com.example.Mid",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Mid(Leaf leaf) {}
            """);

    Compilation compilation =
        javac().withProcessors(new FocusProcessor()).compile(outer, mid, LEAF);

    // MidFocus.LeafNavigator holds a FocusPath, and composing it with a traversal over the map
    // lands on a TraversalPath, which that navigator cannot hold. Handing back the composed path
    // stops the chain one step early; passing it to the navigator did not compile at all.
    assertThat(compilation.status())
        .as("a navigator over a container of navigable records compiles")
        .isEqualTo(Compilation.Status.SUCCESS);
    assertThat(generated(compilation, "com.example.RootFocus"))
        .contains("public TraversalPath<S, Leaf> leaf() {");
  }

  @Test
  @DisplayName("should compose the target's Focus method from the package it was written to")
  void shouldComposeAcrossARedirectedTargetPackage() {
    JavaFileObject holder =
        JavaFileObjects.forSourceString(
            "com.example.Holder",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Holder(Inner inner) {}
            """);
    JavaFileObject inner =
        JavaFileObjects.forSourceString(
            "com.example.Inner",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, targetPackage = "com.other")
            public record Inner(String name, Leaf leaf) {}
            """);

    Compilation compilation =
        javac().withProcessors(new FocusProcessor()).compile(holder, inner, LEAF);

    // A record can send its companion elsewhere, and the navigation method composes the method
    // where it actually landed rather than where the record is declared.
    assertThat(compilation.status())
        .as("a navigator over a record with a redirected companion compiles")
        .isEqualTo(Compilation.Status.SUCCESS);
    assertThat(generated(compilation, "com.example.HolderFocus"))
        .contains("delegate.via(InnerFocus.name())")
        .contains("import com.other.InnerFocus;");
  }

  @Test
  @DisplayName("should label a navigated path's segments the way a static path labels them")
  void shouldLabelNavigatedPathSegments() {
    Compilation compilation =
        javac().withProcessors(new FocusProcessor()).compile(shapes(false), OUTER, LEAF);

    String outerFocus = generated(compilation, "com.example.OuterFocus");
    // The navigator's own path carries the field name, and via() concatenates the segment the
    // composed static method carries, so a navigated path self-locates like a static one (#592).
    assertThat(outerFocus).contains("newValue)), \"shapes\")");
    assertThat(outerFocus).contains("delegate.via(ShapesFocus.plain())");
  }

  /** The text of a generated file. */
  private static String generated(Compilation compilation, String generatedFile) {
    try {
      return compilation
          .generatedSourceFile(generatedFile)
          .orElseThrow()
          .getCharContent(true)
          .toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The return type of each corpus method in a generated file, normalised to compare across the two
   * routes: a static method's source type is the record, a navigator's is the type variable {@code
   * S}, and a navigator-returning method is qualified by the Focus class it is nested in.
   */
  private static Map<String, String> returnTypes(
      Compilation compilation, String generatedFile, String modifiers) throws Exception {

    String source =
        compilation
            .generatedSourceFile(generatedFile)
            .orElseThrow()
            .getCharContent(true)
            .toString();
    Matcher matcher =
        Pattern.compile("^ +public " + modifiers + "(.+) (\\w+)\\(\\) \\{$", Pattern.MULTILINE)
            .matcher(source);

    Map<String, String> returnTypes = new LinkedHashMap<>();
    while (matcher.find()) {
      returnTypes.put(matcher.group(2), normalise(matcher.group(1)));
    }
    return returnTypes;
  }

  /** Drops the enclosing Focus class from a navigator type, and the source type from a path. */
  private static String normalise(String returnType) {
    return returnType.replaceFirst("^\\w+Focus\\.", "").replaceFirst("<(Shapes|S)(,|>)", "<_$2");
  }
}
