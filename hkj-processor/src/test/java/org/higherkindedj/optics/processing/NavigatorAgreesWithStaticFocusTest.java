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
  record Shape(String declaration, String field) {}

  static final List<Shape> SHAPES =
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
          new Shape("String @Nullable [] nullableArray", "nullableArray"),
          new Shape("int[] counts", "counts"),
          new Shape("@Nullable List<String> nullableList", "nullableList"),
          new Shape("@Nullable String nickname", "nickname"),
          new Shape("Leaf leaf", "leaf"),
          new Shape("Optional<Leaf> optionalLeaf", "optionalLeaf"),
          new Shape("List<Leaf> leaves", "leaves"),
          new Shape("Set<Leaf> leafSet", "leafSet"),
          new Shape("Collection<Leaf> leafCollection", "leafCollection"),
          new Shape("Maybe<Leaf> maybeLeaf", "maybeLeaf"),
          new Shape("List<@Nullable Leaf> nullableLeafList", "nullableLeafList"),
          new Shape("Set<@Nullable Leaf> nullableLeafSet", "nullableLeafSet"),
          new Shape("Collection<@Nullable Leaf> nullableLeafCollection", "nullableLeafCollection"),
          new Shape("Optional<@Nullable Leaf> nullableOptionalLeaf", "nullableOptionalLeaf"),
          new Shape("Maybe<@Nullable Leaf> nullableMaybeLeaf", "nullableMaybeLeaf"),
          new Shape("@Nullable List<Leaf> optionalLeaves", "optionalLeaves"),
          new Shape("List<? extends Leaf> boundedLeaves", "boundedLeaves"),
          new Shape("Set<? extends Leaf> boundedLeafSet", "boundedLeafSet"),
          new Shape("Collection<?> looseCollection", "looseCollection"),
          new Shape("Maybe<? extends Leaf> boundedMaybeLeaf", "boundedMaybeLeaf"),
          new Shape("List<Maybe<? extends Leaf>> boundedMaybeLeaves", "boundedMaybeLeaves"),
          new Shape("@SuppressWarnings(\"rawtypes\") Set rawSet", "rawSet"),
          new Shape("@SuppressWarnings(\"rawtypes\") Maybe rawMaybe", "rawMaybe"),
          new Shape("List<Optional<Leaf>> nestedLeaves", "nestedLeaves"),
          new Shape("Optional<List<@Nullable Leaf>> optionalSparseLeaves", "optionalSparseLeaves"),
          new Shape("Either<String, List<Leaf>> eitherLeaves", "eitherLeaves"),
          new Shape("Map<String, List<Leaf>> leafListMap", "leafListMap"),
          new Shape("Kind<ListKind.Witness, Leaf> leafKind", "leafKind"),
          new Shape("Map<String, Leaf> leafMap", "leafMap"),
          new Shape("Leaf[] leafArray", "leafArray"),
          new Shape("@Nullable Map<String, Leaf> nullableLeafMap", "nullableLeafMap"),
          new Shape("Leaf @Nullable [] nullableLeafArray", "nullableLeafArray"),
          new Shape("@Nullable Leaf[] nullableLeaves", "nullableLeaves"),
          new Shape("ArrayList<Leaf> leafArrayList", "leafArrayList"),
          new Shape("Either<String, Leaf> eitherLeaf", "eitherLeaf"),
          new Shape("Box<String> box", "box"),
          new Shape("Map<String, Box<String>> boxMap", "boxMap"),
          new Shape("List<Box<String>> boxList", "boxList"),
          new Shape("List<Box<?>> looseBoxList", "looseBoxList"),
          new Shape("Box<String>[] boxArray", "boxArray"),
          new Shape("Box<?>[] looseBoxArray", "looseBoxArray"),
          new Shape("@SuppressWarnings(\"rawtypes\") Box[] rawBoxArray", "rawBoxArray"),
          new Shape("Either<String, Box<String>> eitherBox", "eitherBox"),
          new Shape("Box<?> looseBox", "looseBox"),
          new Shape("@SuppressWarnings(\"rawtypes\") Box rawBox", "rawBox"),
          new Shape("Map<String, Box<?>> looseBoxMap", "looseBoxMap"));

  static final JavaFileObject LEAF =
      JavaFileObjects.forSourceString(
          "com.example.Leaf",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Leaf(String name) {}
          """);

  /**
   * A generic navigable record, which a component instantiates, leaves to a wildcard or leaves raw.
   */
  static final JavaFileObject BOX =
      JavaFileObjects.forSourceString(
          "com.example.Box",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Box<T>(T value) {}
          """);

  /**
   * Reaches the corpus three ways: over a FocusPath, whose navigator has a form for each wider
   * tier, and over a TraversalPath and an AffinePath, whose navigators compose every hop at their
   * own.
   */
  private static final JavaFileObject OUTER =
      JavaFileObjects.forSourceString(
          "com.example.Outer",
          """
          package com.example;

          import java.util.List;
          import java.util.Optional;
          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Outer(Shapes shapes, List<Shapes> shapesList, Optional<Shapes> shapesOption) {}
          """);

  private static JavaFileObject shapes(boolean widenCollections) {
    return shapes("generateNavigators = true, widenCollections = " + widenCollections);
  }

  /** The corpus as one record, annotated {@code @GenerateFocus(settings)}. */
  static JavaFileObject shapes(String settings) {
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
        import org.higherkindedj.hkt.maybe.Maybe;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import org.jspecify.annotations.Nullable;

        @GenerateFocus(%s)
        public record Shapes(
            %s) {}
        """
            .formatted(settings, components));
  }

  @ParameterizedTest(name = "widenCollections = {0}")
  @ValueSource(booleans = {false, true})
  @DisplayName("should report the same path type whichever way a component is reached")
  void shouldReportTheSamePathTypeWhicheverWayAComponentIsReached(boolean widenCollections)
      throws Exception {

    Compilation compilation =
        javac()
            .withProcessors(new FocusProcessor())
            .compile(shapes(widenCollections), OUTER, LEAF, BOX);

    assertThat(compilation.status())
        .as("the corpus compiles, generated source included")
        .isEqualTo(Compilation.Status.SUCCESS);

    Map<String, String> statics = returnTypes(compilation, "com.example.ShapesFocus", "static ");
    Map<String, String> navigated =
        returnTypes(compilation, "com.example.OuterFocus", "", "ShapesNavigator");

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
        .containsEntry("leaves", "LeavesNavigator<_>")
        .containsEntry("leafSet", "LeafSetNavigator<_>")
        .containsEntry("leafCollection", "LeafCollectionNavigator<_>")
        .containsEntry("optionalLeaf", "OptionalLeafNavigator<_>")
        .containsEntry("maybeLeaf", "MaybeLeafNavigator<_>")
        .containsEntry("nullableLeafList", "NullableLeafListNavigator<_>")
        .containsEntry("optionalLeaves", "OptionalLeavesNavigator<_>")
        .containsEntry("boundedLeaves", "BoundedLeavesNavigator<_>")
        .containsEntry("nullableLeafSet", "NullableLeafSetNavigator<_>")
        .containsEntry("nullableMaybeLeaf", "NullableMaybeLeafNavigator<_>")
        .containsEntry("nestedLeaves", "NestedLeavesNavigator<_>")
        .containsEntry("optionalSparseLeaves", "OptionalSparseLeavesNavigator<_>")
        .containsEntry("eitherLeaves", "EitherLeavesNavigator<_>")
        .containsEntry(
            "leafListMap",
            widenCollections ? "LeafListMapNavigator<_>" : "FocusPath<_, Map<String, List<Leaf>>>")
        .containsEntry("leafKind", "LeafKindNavigator<_>")
        .containsEntry("boxList", "BoxListNavigator<_, String>")
        .containsEntry("looseBoxList", "TraversalPath<_, Box<?>>")
        .containsEntry("box", "BoxNavigator<_, String>")
        .containsEntry(
            "boxMap",
            widenCollections
                ? "BoxMapNavigator<_, String>"
                : "FocusPath<_, Map<String, Box<String>>>")
        .containsEntry("looseBox", "FocusPath<_, Box<?>>")
        .containsEntry(
            "leafArray", widenCollections ? "LeafArrayNavigator<_>" : "FocusPath<_, Leaf[]>")
        .containsEntry(
            "nullableLeaves",
            widenCollections ? "NullableLeavesNavigator<_>" : "FocusPath<_, @Nullable Leaf[]>")
        .containsEntry("counts", "FocusPath<_, int[]>")
        .containsEntry(
            "boxArray",
            widenCollections ? "BoxArrayNavigator<_, String>" : "FocusPath<_, Box<String>[]>")
        .containsEntry("nullableList", "TraversalPath<_, String>")
        .containsEntry(
            "map",
            widenCollections ? "TraversalPath<_, String>" : "FocusPath<_, Map<String, String>>")
        .containsEntry(
            "array", widenCollections ? "TraversalPath<_, String>" : "FocusPath<_, String[]>")
        .containsEntry(
            "nullableArray",
            widenCollections ? "TraversalPath<_, String>" : "AffinePath<_, String[]>");
  }

  @ParameterizedTest(name = "widenCollections = {0}")
  @ValueSource(booleans = {false, true})
  @DisplayName(
      "should report the static path type widened to the tier of the path it is reached from")
  void shouldReportTheStaticPathTypeWidenedToTheTierItIsReachedFrom(boolean widenCollections)
      throws Exception {

    Compilation compilation =
        javac()
            .withProcessors(new FocusProcessor())
            .compile(shapes(widenCollections), OUTER, LEAF, BOX);
    assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);

    Map<String, String> statics = returnTypes(compilation, "com.example.ShapesFocus", "static ");
    Map<String, String> navigatorTiers = navigatorTiers(compilation, "com.example.ShapesFocus");

    // A form and a navigator of the same tier compose every hop alike, and each hop lands on the
    // field's own path type widened to that tier: a navigator where the field has one, in the form
    // that holds that tier.
    Map<String, Map<String, String>> routes =
        Map.of(
            "Affine",
            returnTypes(compilation, "com.example.OuterFocus", "", "ShapesNavigator", "AffineForm"),
            "Traversal",
            returnTypes(
                compilation, "com.example.OuterFocus", "", "ShapesNavigator", "TraversalForm"));
    assertThat(routes.get("Affine"))
        .isEqualTo(returnTypes(compilation, "com.example.OuterFocus", "", "ShapesOptionNavigator"));
    assertThat(routes.get("Traversal"))
        .isEqualTo(returnTypes(compilation, "com.example.OuterFocus", "", "ShapesListNavigator"));

    for (Map.Entry<String, Map<String, String>> route : routes.entrySet()) {
      for (Shape shape : SHAPES) {
        assertThat(route.getValue().get(shape.field()))
            .as("%s reached from %s", shape.declaration(), route.getKey())
            .isEqualTo(widened(statics.get(shape.field()), route.getKey(), navigatorTiers));
      }
    }

    // Spot-checks, so that a widening that quietly matched everything would fail here.
    assertThat(routes.get("Traversal"))
        .containsEntry("plain", "TraversalPath<_, String>")
        .containsEntry("leaf", "LeafNavigator.TraversalForm<_>")
        .containsEntry("optionalLeaf", "OptionalLeafNavigator.TraversalForm<_>")
        .containsEntry("leaves", "LeavesNavigator<_>")
        .containsEntry("box", "BoxNavigator.TraversalForm<_, String>")
        .containsEntry("nullableMaybeLeaf", "NullableMaybeLeafNavigator.TraversalForm<_>")
        .containsEntry("looseBox", "TraversalPath<_, Box<?>>");
    assertThat(routes.get("Affine"))
        .containsEntry("plain", "AffinePath<_, String>")
        .containsEntry("leaf", "LeafNavigator.AffineForm<_>")
        .containsEntry("optionalLeaf", "OptionalLeafNavigator<_>")
        .containsEntry("leaves", "LeavesNavigator<_>");
  }

  @Test
  @DisplayName("should compose a navigator whose path is wider than the target's own")
  void shouldComposeANavigatorWiderThanItsTargets() {
    JavaFileObject outer =
        JavaFileObjects.forSourceString(
            "com.example.Root",
            """
            package com.example;

            import java.util.Map;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true, widenFields = "mids")
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
    // lands on a TraversalPath, which that navigator cannot hold, so the hop hands on its
    // traversal form. Passing the composed path to the navigator itself would not compile.
    assertThat(compilation.status())
        .as("a navigator over a container of navigable records compiles")
        .isEqualTo(Compilation.Status.SUCCESS);
    assertThat(generated(compilation, "com.example.RootFocus"))
        .contains("public MidFocus.LeafNavigator.TraversalForm<S> leaf() {");
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
        .contains("toPath().via(InnerFocus.name())")
        .contains("import com.other.InnerFocus;");
  }

  @Test
  @DisplayName("should label a navigated path's segments the way a static path labels them")
  void shouldLabelNavigatedPathSegments() {
    Compilation compilation =
        javac().withProcessors(new FocusProcessor()).compile(shapes(false), OUTER, LEAF, BOX);

    String outerFocus = generated(compilation, "com.example.OuterFocus");
    // The navigator's own path carries the field name, and via() concatenates the segment the
    // composed static method carries, so a navigated path self-locates like a static one (#592).
    assertThat(outerFocus).contains("source.shapesOption())), \"shapes\")");
    assertThat(outerFocus).contains("toPath().via(ShapesFocus.plain())");
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
   * The return type of each corpus method in a generated file, or in one class in it, normalised to
   * compare across the routes: a static method's source type is the record, a navigator's is the
   * type variable {@code S}, and a navigator-returning method is qualified by the Focus class it is
   * nested in.
   *
   * @param classes the class to read, as the names of the classes it is nested in and its own, each
   *     found after the one before; none for the whole file
   */
  private static Map<String, String> returnTypes(
      Compilation compilation, String generatedFile, String modifiers, String... classes)
      throws Exception {

    String source = generated(compilation, generatedFile);
    for (String nested : classes) {
      int start = source.indexOf("final class " + nested + "<");
      assertThat(start).as("%s declares %s", generatedFile, nested).isNotNegative();
      source = source.substring(start);
    }
    if (classes.length > 0) {
      // A class's own methods come before any class nested in it, and end at the next class.
      int next = source.indexOf("final class ", 1);
      source = next < 0 ? source : source.substring(0, next);
    }
    Matcher matcher =
        Pattern.compile("^ +public " + modifiers + "(.+) (\\w+)\\(\\) \\{$", Pattern.MULTILINE)
            .matcher(source);

    Map<String, String> returnTypes = new LinkedHashMap<>();
    while (matcher.find()) {
      returnTypes.put(matcher.group(2), normalise(matcher.group(1)));
    }
    return returnTypes;
  }

  /** The tier of each navigator a generated file declares, by name: Focus, Affine or Traversal. */
  private static Map<String, String> navigatorTiers(Compilation compilation, String generatedFile) {
    Matcher matcher =
        Pattern.compile("final class (\\w+Navigator)<.*?> extends Navigator(\\w+)Path<")
            .matcher(generated(compilation, generatedFile));
    Map<String, String> tiers = new LinkedHashMap<>();
    while (matcher.find()) {
      tiers.put(matcher.group(1), matcher.group(2));
    }
    return tiers;
  }

  /**
   * A field's static return type widened to the tier of the path it is reached from: a path of the
   * wider tier, and a navigator of a narrower one in its form of that tier.
   */
  private static String widened(String type, String tier, Map<String, String> navigatorTiers) {
    Matcher path = Pattern.compile("^(Focus|Affine|Traversal)Path<(.*)$").matcher(type);
    if (path.matches()) {
      return wider(path.group(1), tier) + "Path<" + path.group(2);
    }
    Matcher navigator = Pattern.compile("^(\\w+Navigator)<(.*)$").matcher(type);
    assertThat(navigator.matches()).as("%s is a path or a navigator", type).isTrue();
    String own = navigatorTiers.get(navigator.group(1));
    return own.equals(wider(own, tier))
        ? type
        : navigator.group(1) + "." + tier + "Form<" + navigator.group(2);
  }

  /** The wider of two tiers. */
  private static String wider(String tier, String other) {
    List<String> order = List.of("Focus", "Affine", "Traversal");
    return order.get(Math.max(order.indexOf(tier), order.indexOf(other)));
  }

  /** Drops the enclosing Focus class from a navigator type, and the source type from a path. */
  private static String normalise(String returnType) {
    return returnType.replaceFirst("^\\w+Focus\\.", "").replaceFirst("<(Shapes|S)(,|>)", "<_$2");
  }
}
