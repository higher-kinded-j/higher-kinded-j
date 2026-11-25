// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * An optic generated over a deprecated type answers for the deprecation in the file it writes. The
 * deprecation is the author's: they chose the type by declaring the component, where their own
 * {@code @SuppressWarnings} answers for it, and that annotation cannot reach a generated file, so a
 * {@code -Werror} build would fail on a warning no one can silence.
 *
 * <p>Each row compiles one generator over a record whose components are a deprecated type and a
 * list of it, under {@code -Xlint:all}, and the warnings located in generated files must be none.
 * The same row is then compiled with the type left undeprecated, and must draw no warning either,
 * so a row whose fixture reached no deprecated type would be seen to prove nothing: its deprecated
 * form must first warn once the generated suppression is out of the picture, which the {@code
 * forRemoval} rows show for the {@code removal} category that {@code deprecation} does not quiet. A
 * type deprecated by its javadoc tag alone draws the warning too, and is suppressed the same way.
 */
@DisplayName("Optic generators - a deprecated type in a generated file")
class OpticsDeprecationTest {

  private record Row(
      String label, Supplier<Processor> processor, String annotation, String legacy) {
    @Override
    public String toString() {
      return label;
    }
  }

  private static final String DEPRECATED = "@Deprecated";
  private static final String REMOVAL = "@Deprecated(forRemoval = true)";

  /** Deprecated by its javadoc tag alone, which javac reads as it reads {@code @Deprecated}. */
  private static final String JAVADOC =
      """
      /**
       * A legacy record.
       *
       * @deprecated use another
       */
      @SuppressWarnings("dep-ann")""";

  private record Generator(String label, Supplier<Processor> processor, String annotation) {
    @Override
    public String toString() {
      return label;
    }
  }

  /** The generators over a record, each with the annotation that asks for it. */
  private static final List<Generator> GENERATORS =
      List.of(
          new Generator("Lenses", LensProcessor::new, "GenerateLenses"),
          new Generator("Getters", GetterProcessor::new, "GenerateGetters"),
          new Generator("Setters", SetterProcessor::new, "GenerateSetters"),
          new Generator("Folds", FoldProcessor::new, "GenerateFolds"),
          new Generator("Traversals", TraversalProcessor::new, "GenerateTraversals"),
          new Generator("Focus", FocusProcessor::new, "GenerateFocus"));

  /** How a row's name says which legacy mark it carries; {@code @Deprecated} says nothing. */
  private static String legacyLabel(String legacy) {
    return switch (legacy) {
      case REMOVAL -> ", for removal";
      case JAVADOC -> ", by its javadoc tag alone";
      default -> "";
    };
  }

  static Stream<Row> rows() {
    return GENERATORS.stream()
        .flatMap(
            generator ->
                Stream.of(DEPRECATED, REMOVAL, JAVADOC)
                    .map(
                        legacy ->
                            new Row(
                                generator.label() + legacyLabel(legacy),
                                generator.processor(),
                                generator.annotation(),
                                legacy)));
  }

  private static JavaFileObject[] sources(Row row, String legacy) {
    return new JavaFileObject[] {
      JavaFileObjects.forSourceString(
          "com.example.Legacy",
          "package com.example;\n" + legacy + "\npublic record Legacy(String name) {}\n"),
      JavaFileObjects.forSourceString(
          "com.example.Basket",
          """
          package com.example;

          import java.util.List;
          import org.higherkindedj.optics.annotations.*;

          @%s
          @SuppressWarnings({"deprecation", "removal"})
          public record Basket(Legacy item, List<Legacy> items) {}
          """
              .formatted(row.annotation()))
    };
  }

  private static List<String> generatedWarnings(Compilation compilation) {
    return compilation.diagnostics().stream()
        .filter(
            diagnostic ->
                Set.of(Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING)
                    .contains(diagnostic.getKind()))
        .filter(
            diagnostic ->
                diagnostic.getSource() != null
                    && diagnostic.getSource().getName().contains("SOURCE_OUTPUT"))
        .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
        .toList();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  @DisplayName("should compile without a warning in the generated file")
  void shouldAnswerForTheDeprecationInTheGeneratedFile(Row row) {
    Compilation compilation =
        javac()
            .withProcessors(row.processor().get())
            .withOptions("-Xlint:all")
            .compile(sources(row, row.legacy()));

    assertThat(compilation.errors()).isEmpty();
    assertThat(generatedWarnings(compilation)).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  @DisplayName("should draw no suppression for a type that is not deprecated")
  void shouldLeaveAnUndeprecatedTypeUnsuppressed(Row row) {
    Compilation compilation =
        javac()
            .withProcessors(row.processor().get())
            .withOptions("-Xlint:all")
            .compile(sources(row, ""));

    assertThat(compilation.errors()).isEmpty();
    assertThat(compilation.generatedSourceFiles())
        .allSatisfy(
            file -> assertThat(content(file)).doesNotContain("\"deprecation\"", "\"removal\""));
  }

  private static String content(JavaFileObject file) {
    try {
      return file.getCharContent(true).toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * An optic names more than its components' types: a prism names the subtype it focuses on, an
   * imported type's optics name that type and every class it is nested in, and a body calls the
   * accessors, constructor, wither, getter, enum constant or annotated method it is generated
   * around. Each of those, deprecated, is answered for in the generated file as a deprecated
   * component type is.
   *
   * <p>Each shape places the legacy mark on one element in its library sources. An {@code
   * ImportOptics} shape is compiled both from a jar, where the mark is read from a class file, and
   * alongside the code that imports it. The code that names the element itself suppresses both
   * categories, as an author would.
   */
  @Nested
  @DisplayName("A deprecated subtype, owner or called member")
  class SubtypeOwnerOrMember {

    private record Shape(
        String label,
        Supplier<Processor> processor,
        boolean readFromAJar,
        Map<String, String> library,
        Map<String, String> code) {

      @Override
      public String toString() {
        return label;
      }

      Shape alongside() {
        return new Shape(label + ", alongside", processor, false, library, code);
      }

      Shape fromAJar() {
        return new Shape(label + ", from a jar", processor, true, library, code);
      }
    }

    private record Case(Shape shape, String legacy) {
      @Override
      public String toString() {
        return shape.label() + legacyLabel(legacy);
      }
    }

    private static final String RECORD_ACCESSOR =
        """
        package com.example;

        import java.util.List;
        import org.higherkindedj.optics.annotations.*;

        @%s
        public record Basket(String name, List<String> items) {
          LEGACY
          @Override
          public List<String> items() {
            return items;
          }
        }
        """;

    private static final String RECORD_CONSTRUCTOR =
        """
        package com.example;

        import java.util.List;
        import org.higherkindedj.optics.annotations.*;

        @%s
        public record Basket(String name, List<String> items) {
          LEGACY
          public Basket {}
        }
        """;

    private static final String IMPORTED =
        """
        @ImportOptics({%s.class})
        @SuppressWarnings({"deprecation", "removal"})
        package com.myapp;

        import org.higherkindedj.optics.annotations.ImportOptics;
        """;

    /** The generators that read a component and never rebuild the record. */
    private static final Set<String> READ_ONLY = Set.of("Getters", "Folds");

    private static Shape generated(String label, Supplier<Processor> processor, String source) {
      return new Shape(label, processor, false, Map.of("com.example.Basket", source), Map.of());
    }

    private static Stream<Shape> imported(String label, String type, Map<String, String> library) {
      Shape shape =
          new Shape(
              label,
              ImportOpticsProcessor::new,
              false,
              library,
              Map.of("com.myapp.package-info", IMPORTED.formatted(type)));
      return Stream.of(shape.fromAJar(), shape.alongside());
    }

    static Stream<Shape> shapes() {
      return Stream.of(
              Stream.of(
                  new Shape(
                      "Prisms over a deprecated permitted subtype",
                      PrismProcessor::new,
                      false,
                      Map.of(
                          "com.example.Figure",
                          """
                          package com.example;

                          import org.higherkindedj.optics.annotations.GeneratePrisms;

                          @GeneratePrisms
                          @SuppressWarnings({"deprecation", "removal"}) // the permits clause
                          public sealed interface Figure permits Circle, Square {}
                          """,
                          "com.example.Circle",
                          """
                          package com.example;

                          public record Circle(double radius) implements Figure {}
                          """,
                          "com.example.Square",
                          """
                          package com.example;

                          LEGACY
                          public record Square(double side) implements Figure {}
                          """),
                      Map.of()),
                  new Shape(
                      "Prisms over a deprecated enum constant",
                      PrismProcessor::new,
                      false,
                      Map.of(
                          "com.example.Colour",
                          """
                          package com.example;

                          @org.higherkindedj.optics.annotations.GeneratePrisms
                          public enum Colour {
                            RED,
                            LEGACY
                            GREEN
                          }
                          """),
                      Map.of()),
                  new Shape(
                      "Prisms over a deprecated enum",
                      PrismProcessor::new,
                      false,
                      Map.of(
                          "com.example.Colour",
                          """
                          package com.example;

                          LEGACY
                          @org.higherkindedj.optics.annotations.GeneratePrisms
                          public enum Colour {
                            RED
                          }
                          """),
                      Map.of()),
                  new Shape(
                      "Isos from a deprecated method",
                      IsoProcessor::new,
                      false,
                      Map.of(
                          "com.example.Conversions",
                          """
                          package com.example;

                          import org.higherkindedj.optics.Iso;
                          import org.higherkindedj.optics.annotations.GenerateIsos;

                          public final class Conversions {
                            LEGACY
                            @GenerateIsos
                            public static Iso<String, Integer> length() {
                              return Iso.of(Integer::parseInt, String::valueOf);
                            }
                          }
                          """),
                      Map.of()),
                  new Shape(
                      "Isos from a method of a deprecated class",
                      IsoProcessor::new,
                      false,
                      Map.of(
                          "com.example.Conversions",
                          """
                          package com.example;

                          import org.higherkindedj.optics.Iso;
                          import org.higherkindedj.optics.annotations.GenerateIsos;

                          LEGACY
                          public final class Conversions {
                            @GenerateIsos
                            public static Iso<String, Integer> length() {
                              return Iso.of(Integer::parseInt, String::valueOf);
                            }
                          }
                          """),
                      Map.of())),
              GENERATORS.stream()
                  .map(
                      generator ->
                          generated(
                              generator.label() + " calling a deprecated accessor",
                              generator.processor(),
                              RECORD_ACCESSOR.formatted(generator.annotation()))),
              GENERATORS.stream()
                  .filter(generator -> !READ_ONLY.contains(generator.label()))
                  .map(
                      generator ->
                          generated(
                              generator.label() + " calling a deprecated constructor",
                              generator.processor(),
                              RECORD_CONSTRUCTOR.formatted(generator.annotation()))),
              imported(
                  "Imported prisms over a deprecated permitted subtype",
                  "com.external.Figure",
                  Map.of(
                      "com.external.Figure",
                      """
                      package com.external;

                      public sealed interface Figure permits Figure.Circle, Figure.Square {
                        record Circle(double radius) implements Figure {}

                        LEGACY
                        record Square(double side) implements Figure {}
                      }
                      """)),
              imported(
                  "Imported prisms over a deprecated enum constant",
                  "com.external.Colour",
                  Map.of(
                      "com.external.Colour",
                      """
                      package com.external;

                      public enum Colour {
                        RED,
                        LEGACY
                        GREEN
                      }
                      """)),
              imported(
                  "Imported optics of a deprecated record",
                  "com.external.Bag",
                  Map.of(
                      "com.external.Bag",
                      """
                      package com.external;

                      import java.util.List;

                      LEGACY
                      public record Bag(String name, List<String> items) {}
                      """)),
              imported(
                  "Imported optics of a record nested in a deprecated class",
                  "com.external.Holder.Inner",
                  Map.of(
                      "com.external.Holder",
                      """
                      package com.external;

                      LEGACY
                      public class Holder {
                        public record Inner(String name) {}
                      }
                      """)),
              imported(
                  "Imported optics of a deprecated wither class",
                  "com.external.Item",
                  Map.of("com.external.Item", witherClass("LEGACY", "", ""))),
              imported(
                  "Imported optics calling a deprecated accessor",
                  "com.external.Bag",
                  Map.of(
                      "com.external.Bag",
                      """
                      package com.external;

                      import java.util.List;

                      public record Bag(String name, List<String> items) {
                        LEGACY
                        @Override
                        public List<String> items() {
                          return items;
                        }
                      }
                      """)),
              imported(
                  "Imported optics calling a deprecated constructor",
                  "com.external.Bag",
                  Map.of(
                      "com.external.Bag",
                      """
                      package com.external;

                      import java.util.List;

                      public record Bag(String name, List<String> items) {
                        LEGACY
                        public Bag {}
                      }
                      """)),
              imported(
                  "Imported optics calling a deprecated wither",
                  "com.external.Item",
                  Map.of("com.external.Item", witherClass("", "", "LEGACY"))),
              imported(
                  "Imported optics calling a deprecated getter",
                  "com.external.Item",
                  Map.of("com.external.Item", witherClass("", "LEGACY", ""))))
          .flatMap(Function.identity());
    }

    /** A wither class, with the legacy mark on the class, its getter or its wither. */
    private static String witherClass(String onClass, String onGetter, String onWither) {
      return """
          package com.external;

          %s
          public final class Item {
            private final String name;

            public Item(String name) {
              this.name = name;
            }

            %s
            public String getName() {
              return name;
            }

            %s
            public Item withName(String name) {
              return new Item(name);
            }
          }
          """
          .formatted(onClass, onGetter, onWither);
    }

    static Stream<Case> cases() {
      return shapes()
          .flatMap(
              shape ->
                  Stream.of(DEPRECATED, REMOVAL, JAVADOC).map(legacy -> new Case(shape, legacy)));
    }

    private static JavaFileObject[] sourcesOf(Map<String, String> sources, String legacy) {
      return sources.entrySet().stream()
          .map(
              source ->
                  JavaFileObjects.forSourceString(
                      source.getKey(), source.getValue().replace("LEGACY", legacy)))
          .toArray(JavaFileObject[]::new);
    }

    private static Compilation compile(Shape shape, String legacy, Path tmp) throws IOException {
      var compiler =
          javac().withProcessors(shape.processor().get()).withOptions("-Xlint:all,-processing");
      if (!shape.readFromAJar()) {
        return compiler.compile(
            Stream.concat(
                    Stream.of(sourcesOf(shape.library(), legacy)),
                    Stream.of(sourcesOf(shape.code(), legacy)))
                .toArray(JavaFileObject[]::new));
      }
      Compilation library = javac().compile(sourcesOf(shape.library(), legacy));
      assertThat(library.errors()).isEmpty();
      return compiler
          .withClasspath(classpathWith(classDirectory(library, tmp)))
          .compile(sourcesOf(shape.code(), legacy));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("should compile without a warning in the generated file")
    void shouldAnswerForTheDeprecationInTheGeneratedFile(Case row, @TempDir Path tmp)
        throws IOException {
      Compilation compilation = compile(row.shape(), row.legacy(), tmp);

      assertThat(compilation.errors()).isEmpty();
      assertThat(compilation.generatedSourceFiles()).isNotEmpty();
      assertThat(generatedWarnings(compilation)).isEmpty();
    }

    static Stream<Generator> readOnlyGenerators() {
      return GENERATORS.stream().filter(generator -> READ_ONLY.contains(generator.label()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readOnlyGenerators")
    @DisplayName("should suppress only the member that reads the deprecated accessor")
    void shouldSuppressOnlyTheMemberThatReadsTheDeprecatedAccessor(Generator generator) {
      Compilation compilation =
          javac()
              .withProcessors(generator.processor().get())
              .withOptions("-Xlint:all,-processing")
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.example.Basket",
                      RECORD_ACCESSOR
                          .formatted(generator.annotation())
                          .replace("LEGACY", DEPRECATED)));

      assertThat(compilation.errors()).isEmpty();
      // The member over items reads the deprecated items(); the one over name reads only name().
      assertThat(compilation.generatedSourceFiles())
          .singleElement()
          .satisfies(file -> assertThat(content(file)).containsOnlyOnce("\"deprecation\""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    @DisplayName("should draw no suppression when nothing is deprecated")
    void shouldLeaveAnUndeprecatedShapeUnsuppressed(Shape shape, @TempDir Path tmp)
        throws IOException {
      Compilation compilation = compile(shape, "", tmp);

      assertThat(compilation.errors()).isEmpty();
      assertThat(compilation.generatedSourceFiles())
          .isNotEmpty()
          .allSatisfy(
              file -> assertThat(content(file)).doesNotContain("\"deprecation\"", "\"removal\""));
    }
  }
}
