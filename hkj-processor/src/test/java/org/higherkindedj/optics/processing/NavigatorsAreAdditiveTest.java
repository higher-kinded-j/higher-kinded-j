// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.NavigatorAgreesWithStaticFocusTest.Shape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Turning navigators on only adds to what a record's Focus class offers. A navigator is a path of
 * the tier the field's method returns without one, so each method's result, held in the type it has
 * with navigators off, still compiles with them on.
 */
@DisplayName("Navigators are additive")
class NavigatorsAreAdditiveTest {

  private static final Pattern STATIC_METHOD =
      Pattern.compile("public static (.+?) (\\w+)\\(\\) \\{");

  @ParameterizedTest(name = "widenCollections = {0}")
  @ValueSource(booleans = {false, true})
  @DisplayName("each field's method with navigators on is assignable to its type with them off")
  void navigatorsOnlyAdd(boolean widenCollections) throws IOException {
    Map<String, String> off =
        returnTypes(
            compile("generateNavigators = false, widenCollections = " + widenCollections, null));

    Compilation on =
        compile("generateNavigators = true, widenCollections = " + widenCollections, useAsOff(off));

    assertThat(on.errors())
        .as("every method assigns to the type it has with navigators off")
        .isEmpty();
    long navigators =
        returnTypes(on).values().stream().filter(t -> t.contains("Navigator<")).count();
    assertThat(navigators)
        .as("navigators were generated, so the check is not vacuous")
        .isPositive();
  }

  private static Compilation compile(String settings, JavaFileObject use) {
    List<JavaFileObject> sources =
        new ArrayList<>(
            List.of(
                NavigatorAgreesWithStaticFocusTest.shapes(settings),
                NavigatorAgreesWithStaticFocusTest.LEAF,
                NavigatorAgreesWithStaticFocusTest.BOX));
    if (use != null) {
      sources.add(use);
    }
    Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sources);
    if (use == null) {
      assertThat(compilation.errors()).as("the corpus compiles with navigators off").isEmpty();
    }
    return compilation;
  }

  /** Each static Focus method's return type, by field, as the generated source writes it. */
  private static Map<String, String> returnTypes(Compilation compilation) throws IOException {
    String source =
        compilation
            .generatedSourceFile("com.example.ShapesFocus")
            .orElseThrow()
            .getCharContent(true)
            .toString();
    Map<String, String> types = new LinkedHashMap<>();
    Matcher matcher = STATIC_METHOD.matcher(source);
    while (matcher.find()) {
      types.put(matcher.group(2), matcher.group(1));
    }
    assertThat(types.keySet())
        .containsAll(NavigatorAgreesWithStaticFocusTest.SHAPES.stream().map(Shape::field).toList());
    return types;
  }

  /** A use site that holds each method's result in the type the method has with navigators off. */
  private static JavaFileObject useAsOff(Map<String, String> off) {
    String assignments =
        off.entrySet().stream()
            .map(
                e ->
                    "    "
                        + e.getValue()
                        + " "
                        + e.getKey()
                        + " = ShapesFocus."
                        + e.getKey()
                        + "();")
            .collect(Collectors.joining("\n"));
    return JavaFileObjects.forSourceString(
        "com.example.UseAsOff",
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
        import org.higherkindedj.optics.focus.AffinePath;
        import org.higherkindedj.optics.focus.FocusPath;
        import org.higherkindedj.optics.focus.TraversalPath;
        import org.jspecify.annotations.Nullable;

        @SuppressWarnings({"rawtypes", "unused"})
        final class UseAsOff {
          void use() {
        %s
          }
        }
        """
            .formatted(assignments));
  }
}
