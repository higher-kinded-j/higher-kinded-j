// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A component that reaches a generic {@code @GenerateFocus} record through a wildcard or a raw type
 * gets no navigator, and its note says what the component's method returns instead and how to get
 * further. Each claim is held to the code: the path the note names is the method's return type, a
 * lens from the type as declared composes with that path and runs wherever the note says it does,
 * and writing the type the note offers in place of the declared one gets a navigator, under {@code
 * -Xlint:all -Werror}, with nothing more to say.
 */
@DisplayName("Navigator notes - fix lines that can be followed")
class NavigatorNoteFixLineTest {

  private static final JavaFileObject INNER =
      JavaFileObjects.forSourceString(
          "com.myapp.Inner",
          """
          package com.myapp;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Inner<T>(T value, String label) {}
          """);

  private static final JavaFileObject NUM =
      JavaFileObjects.forSourceString(
          "com.myapp.Num",
          """
          package com.myapp;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Num<T extends Number>(T value) {}
          """);

  private static final String OUTER_SETTINGS = "generateNavigators = true";

  /** Each way a component can reach a generic record without naming one type for its parameter. */
  private static final List<String> OUTER_COMPONENTS =
      List.of(
          "Inner<?> any",
          "@SuppressWarnings(\"rawtypes\") Inner raw",
          "Inner<? extends Number> bounded",
          "@Nullable Inner<?> absent",
          "Either<String, Inner<?>> either",
          "Either<String, @Nullable Inner<?>> loose",
          "Num<?> number",
          "Num<? super Integer> whole");

  private static final String WIDE_SETTINGS = "generateNavigators = true, widenCollections = true";

  /** The containers that step into their element only under {@code widenCollections}. */
  private static final List<String> WIDE_COMPONENTS =
      List.of(
          "Map<String, Inner<?>> catalogue",
          "Inner<?>[] shelf",
          "Map<String, @Nullable Inner<?>> gaps");

  /**
   * Reads and writes through a lens written from each declared type, composed with each path the
   * note says it composes with, through the step the note names. It compiles beside the records, so
   * a claim that does not hold fails the shared compilation.
   */
  private static final JavaFileObject PROBE =
      JavaFileObjects.forSourceString(
          "com.myapp.Probe",
          """
          package com.myapp;

          import java.util.HashMap;
          import java.util.List;
          import java.util.Map;
          import org.higherkindedj.hkt.either.Either;
          import org.higherkindedj.optics.Lens;
          import org.higherkindedj.optics.util.Affines;

          public final class Probe {
            private Probe() {}

            private static final Lens<Inner<?>, Object> ANY =
                Lens.of(Inner::value, (inner, value) -> new Inner<>(value, inner.label()));

            @SuppressWarnings({"rawtypes", "unchecked"})
            private static final Lens<Inner, Object> RAW =
                Lens.of(Inner::value, (inner, value) -> new Inner(value, inner.label()));

            private static final Lens<Inner<? extends Number>, Number> BOUNDED =
                Lens.of(Inner::value, (inner, value) -> new Inner<>(value, inner.label()));

            private static final Lens<Num<?>, Number> NUMBER =
                Lens.of(Num::value, (num, value) -> new Num<>(value));

            private static final Lens<Num<? super Integer>, Number> WHOLE =
                Lens.of(Num::value, (num, value) -> new Num<>(value));

            public static List<Object> reads() {
              Outer outer =
                  new Outer(
                      new Inner<>("any", "a"),
                      new Inner<>("raw", "r"),
                      new Inner<>(1, "b"),
                      null,
                      Either.right(new Inner<>("either", "e")),
                      Either.right(new Inner<>("loose", "l")),
                      new Num<>(2),
                      new Num<>(3));
              Map<String, Inner<?>> gaps = new HashMap<>();
              gaps.put("missing", null);
              gaps.put("present", new Inner<>("gap", "g"));
              Wide wide =
                  new Wide(
                      Map.of("k", new Inner<>("mapped", "m")),
                      new Inner<?>[] {new Inner<>("shelved", "s")},
                      gaps);
              return List.of(
                  OuterFocus.any().via(ANY).get(outer),
                  OuterFocus.raw().via(RAW).get(outer),
                  OuterFocus.bounded().via(BOUNDED).set(2.5, outer).bounded().value(),
                  OuterFocus.absent().via(ANY).getOptional(outer),
                  OuterFocus.either().via(ANY).getOptional(outer),
                  OuterFocus.loose().some(Affines.nullable()).via(ANY).getOptional(outer),
                  OuterFocus.number().via(NUMBER).get(outer),
                  OuterFocus.whole().via(WHOLE).get(outer),
                  WideFocus.catalogue().via(ANY).getAll(wide),
                  WideFocus.shelf().via(ANY).modifyAll(value -> value + "!", wide).shelf()[0].value(),
                  WideFocus.gaps().some(Affines.nullable()).via(ANY).getAll(wide));
            }
          }
          """);

  private static final Compilation COMPILATION =
      javac()
          .withProcessors(new FocusProcessor())
          .withOptions("-Xlint:all,-processing", "-Werror")
          .compile(
              INNER,
              NUM,
              recordSource("Outer", OUTER_SETTINGS, OUTER_COMPONENTS),
              recordSource("Wide", WIDE_SETTINGS, WIDE_COMPONENTS),
              PROBE);

  /** The offer a note makes, which the reader can paste over the type as declared. */
  private static final Pattern OFFER =
      Pattern.compile(
          "^Navigator for field '(\\w+)' is not generated: .* Write (.+), or another concrete"
              + " instantiation, in place of (.+) to get a navigator\\.$");

  private static JavaFileObject recordSource(
      String name, String settings, List<String> components) {
    return JavaFileObjects.forSourceString(
        "com.myapp." + name,
        """
        package com.myapp;

        import java.util.Map;
        import org.higherkindedj.hkt.either.Either;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import org.jspecify.annotations.Nullable;

        @GenerateFocus(%s)
        public record %s(
            %s) {}
        """
            .formatted(settings, name, String.join(",\n    ", components)));
  }

  /**
   * What a note offers: a type to write, and the type as the note names it, which it replaces.
   *
   * @param type the type the note says to write
   * @param written the declared type as the note names it
   */
  private record Offer(String type, String written) {

    /** The component with the offered type written in place of the declared one. */
    String followedIn(String component) {
      return component.replace(written.replaceFirst("^the raw ", ""), type);
    }

    /**
     * The type argument the offered type gives its record, which a navigator is instantiated with.
     */
    String argument() {
      return type.substring(type.indexOf('<') + 1, type.length() - 1);
    }
  }

  /** Each component's offer in the shared compilation, by the component's name. */
  private static Map<String, Offer> offers() {
    return COMPILATION.notes().stream()
        .map(note -> OFFER.matcher(note.getMessage(Locale.ROOT)))
        .filter(Matcher::matches)
        .collect(
            Collectors.toMap(
                offer -> offer.group(1), offer -> new Offer(offer.group(2), offer.group(3))));
  }

  /** A component declaration's name: its last word. */
  private static String fieldOf(String component) {
    return component.substring(component.lastIndexOf(' ') + 1);
  }

  /** The offer in the note for a component, which every component here draws. */
  private static Offer offerFor(String component, Map<String, Offer> offers) {
    Offer offer = offers.get(fieldOf(component));
    assertThat(offer).as("the offer in the note for '%s'", fieldOf(component)).isNotNull();
    return offer;
  }

  private static String capitalised(String field) {
    return Character.toUpperCase(field.charAt(0)) + field.substring(1);
  }

  @ParameterizedTest(name = "{0}.{1}")
  @CsvSource(
      delimiterString = " | ",
      textBlock =
          """
          Outer | any | FocusPath<Outer, Inner<?>> | Inner<?> | Inner<Object>
          Outer | raw | FocusPath<Outer, Inner> | the raw Inner | Inner<Object>
          Outer | bounded | FocusPath<Outer, Inner<? extends Number>> | Inner<? extends Number> | Inner<Number>
          Outer | absent | AffinePath<Outer, Inner<?>> | Inner<?> | Inner<Object>
          Outer | either | AffinePath<Outer, Inner<?>> | Inner<?> | Inner<Object>
          Outer | loose | AffinePath<Outer, @Nullable Inner<?>> | Inner<?> | Inner<Object>
          Outer | number | FocusPath<Outer, Num<?>> | Num<?> | Num<Number>
          Outer | whole | FocusPath<Outer, Num<? super Integer>> | Num<? super Integer> | Num<Integer>
          Wide | catalogue | TraversalPath<Wide, Inner<?>> | Inner<?> | Inner<Object>
          Wide | shelf | TraversalPath<Wide, Inner<?>> | Inner<?> | Inner<Object>
          Wide | gaps | TraversalPath<Wide, @Nullable Inner<?>> | Inner<?> | Inner<Object>
          """)
  @DisplayName("the note names the method's return type, and the lens that composes with it")
  void theNoteNamesTheMethodsReturnType(
      String record, String field, String signature, String written, String offered) {
    String target = offered.substring(0, offered.indexOf('<'));
    // A lens composes with a focus that may be null once the null is ruled out.
    String through = signature.contains("@Nullable") ? ".some(Affines.nullable()).via()" : ".via()";

    assertThat(COMPILATION).succeededWithoutWarnings();
    String focusClass = "com.myapp." + record + "Focus";
    assertGeneratedCodeContains(
        COMPILATION, focusClass, "public static " + signature + " " + field + "()");
    assertGeneratedCodeDoesNotContain(COMPILATION, focusClass, capitalised(field) + "Navigator");
    assertThat(COMPILATION.notes())
        .extracting(note -> note.getMessage(Locale.ROOT))
        .contains(
            "Navigator for field '"
                + field
                + "' is not generated: a navigator into "
                + target
                + " has to name one type for each of its type parameters, which "
                + written
                + " does not. "
                + record
                + "Focus."
                + field
                + "() returns "
                + signature
                + " instead. A path from one instantiation of "
                + target
                + " does not compose with it, but a lens you write from "
                + written
                + " does, through "
                + through
                + ". Write "
                + offered
                + ", or another concrete instantiation, in place of "
                + written
                + " to get a navigator.");
  }

  @Test
  @DisplayName("a lens from the declared type composes with the path and runs")
  void aLensFromTheDeclaredTypeComposesAndRuns() throws ReflectiveOperationException {
    assertThat(COMPILATION).succeededWithoutWarnings();

    Object reads = new CompiledResult(COMPILATION).invokeStatic("com.myapp.Probe", "reads");

    // A null element is passed over rather than read, once the null is ruled out.
    assertThat(reads)
        .isEqualTo(
            List.of(
                "any",
                "raw",
                2.5,
                Optional.empty(),
                Optional.of("either"),
                Optional.of("loose"),
                2,
                3,
                List.of("mapped"),
                "shelved!",
                List.of("gap")));
  }

  @Test
  @DisplayName("a container the record does not widen has no navigator to miss, and no note")
  void anUnwidenedContainerGetsNoNote() {
    Compilation compilation =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing", "-Werror")
            .compile(
                INNER,
                NUM,
                recordSource(
                    "Outer",
                    OUTER_SETTINGS,
                    List.of("Map<String, Inner<?>> byName", "Inner<?>[] boxes")));

    // A navigator steps into a container only where the record widens it, so neither field is
    // missing one, whatever its element, and the method keeps the container in focus.
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation.notes()).isEmpty();
    assertGeneratedCodeContains(
        compilation,
        "com.myapp.OuterFocus",
        "public static FocusPath<Outer, Map<String, Inner<?>>> byName()");
    assertGeneratedCodeContains(
        compilation, "com.myapp.OuterFocus", "public static FocusPath<Outer, Inner<?>[]> boxes()");
  }

  @Test
  @DisplayName("writing the type a note offers in place of the declared one gets a navigator")
  void writingTheOfferedTypeGetsANavigator() {
    Map<String, Offer> offers = offers();
    Map<String, List<String>> records = Map.of("Outer", OUTER_COMPONENTS, "Wide", WIDE_COMPONENTS);

    Compilation followed =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing", "-Werror")
            .compile(
                INNER,
                NUM,
                recordSource(
                    "Outer",
                    OUTER_SETTINGS,
                    OUTER_COMPONENTS.stream()
                        .map(component -> offerFor(component, offers).followedIn(component))
                        .toList()),
                recordSource(
                    "Wide",
                    WIDE_SETTINGS,
                    WIDE_COMPONENTS.stream()
                        .map(component -> offerFor(component, offers).followedIn(component))
                        .toList()));

    // A fix line is only worth printing if following it does what it says, with nothing more to
    // say about the record.
    assertThat(followed).succeededWithoutWarnings();
    assertThat(followed.notes()).isEmpty();
    records.forEach(
        (record, components) ->
            components.forEach(
                component ->
                    assertGeneratedCodeContains(
                        followed,
                        "com.myapp." + record + "Focus",
                        "public static "
                            + capitalised(fieldOf(component))
                            + "Navigator<"
                            + record
                            + ", "
                            + offerFor(component, offers).argument()
                            + "> "
                            + fieldOf(component)
                            + "()")));
  }
}
