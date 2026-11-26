// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The optics generators over what another annotation processor writes in the same build: an element
 * naming it waits until it resolves, and is then generated as it would be had it been written by
 * hand. One naming what no round brings is reported where it is declared.
 */
@DisplayName("Optics generators across rounds")
class OpticsRoundsTest {

  /** A type the writer brings in its first round: its name and its source. */
  private record Source(String name, String text) {

    JavaFileObject byHand() {
      return JavaFileObjects.forSourceString(name, text);
    }
  }

  /** An interface the writer brings, which a compilation with no records can name too. */
  private static final Source TAG =
      new Source("com.gen.Tag", "package com.gen; public interface Tag {}");

  /** What every row may name, in another package than the elements, as an import names it. */
  private static final List<Source> WRITTEN =
      List.of(
          new Source("com.gen.Profile", "package com.gen; public record Profile(String id) {}"),
          TAG,
          new Source(
              "com.gen.Tagged",
              "package com.gen; @java.lang.annotation.Target(java.lang.annotation.ElementType"
                  + ".TYPE_USE) public @interface Tagged {}"),
          new Source(
              "com.gen.Pkgs",
              "package com.gen; public final class Pkgs { private Pkgs() {} public static final"
                  + " String OPTICS = \"com.example.optics\"; public static final String FIELD ="
                  + " \"address\"; public static final int DEPTH = 2; public static final boolean"
                  + " REMOVE = true; }"));

  /** An interface an element can name a companion in, as a supertype clause's argument. */
  private static final JavaFileObject MARKER =
      JavaFileObjects.forSourceString(
          "com.example.Marker", "package com.example; public interface Marker<T> {}");

  /** A subtype written into the elements' own package, which only a sum type there can permit. */
  private static final Source CIRCLE =
      new Source(
          "com.example.Circle",
          "package com.example; public record Circle(double radius) implements Shape {}");

  /**
   * Writes, in its first round, the types a row names. It runs ahead of the optics processors,
   * which claim their own annotations, so javac offers it the round whatever the row declares.
   */
  @SupportedAnnotationTypes("*")
  private static final class TypeWriter extends AbstractProcessor {

    private final List<Source> sources;
    private boolean written;

    TypeWriter(List<Source> sources) {
      this.sources = sources;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (!written) {
        written = true;
        sources.forEach(this::write);
      }
      return false;
    }

    private void write(Source source) {
      try (Writer writer = processingEnv.getFiler().createSourceFile(source.name()).openWriter()) {
        writer.write(source.text());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /** Notes the simple names of the types each round brings, round by round. */
  @SupportedAnnotationTypes("*")
  private static final class RoundRoots extends AbstractProcessor {

    private final List<Set<String>> rounds = new ArrayList<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      rounds.add(
          ElementFilter.typesIn(roundEnv.getRootElements()).stream()
              .map(type -> type.getSimpleName().toString())
              .collect(Collectors.toUnmodifiableSet()));
      return false;
    }

    /** The round, counted from one, that first brings a type of this simple name. */
    int first(String simpleName) {
      return IntStream.range(0, rounds.size())
              .filter(round -> rounds.get(round).contains(simpleName))
              .findFirst()
              .orElseThrow()
          + 1;
    }
  }

  /** An optics processor, with the tag its diagnostics carry. */
  enum Generator {
    LENSES("@GenerateLenses", LensProcessor::new),
    FOCUS("@GenerateFocus", FocusProcessor::new),
    GETTERS("@GenerateGetters", GetterProcessor::new),
    SETTERS("@GenerateSetters", SetterProcessor::new),
    FOLDS("@GenerateFolds", FoldProcessor::new),
    TRAVERSALS("@GenerateTraversals", TraversalProcessor::new),
    ISOS("@GenerateIsos", IsoProcessor::new),
    PRISMS("@GeneratePrisms", PrismProcessor::new);

    private final String tag;
    private final Supplier<Processor> processor;

    Generator(String tag, Supplier<Processor> processor) {
      this.tag = tag;
      this.processor = processor;
    }
  }

  /**
   * One shape an element can name what the writer brings in its first round, with the processor
   * that generates for it, and what the last round reports where no round brings it.
   */
  enum LaterRound {
    LENSES_COMPONENT(
        "@GenerateLenses over a component's type",
        Generator.LENSES,
        "Person",
        "Profile",
        record("Person", "@GenerateLenses", "Profile profile, String name")),
    FOCUS_COMPONENT(
        "@GenerateFocus over a component's type",
        Generator.FOCUS,
        "Person",
        "Profile",
        record("Person", "@GenerateFocus", "Profile profile, String name")),
    GETTERS_COMPONENT(
        "@GenerateGetters over a component's type",
        Generator.GETTERS,
        "Person",
        "Profile",
        record("Person", "@GenerateGetters", "Profile profile, String name")),
    SETTERS_COMPONENT(
        "@GenerateSetters over a component's type",
        Generator.SETTERS,
        "Person",
        "Profile",
        record("Person", "@GenerateSetters", "Profile profile, String name")),
    FOLDS_COMPONENT(
        "@GenerateFolds over a component's type, which a fold would take for one to iterate",
        Generator.FOLDS,
        "Person",
        "Profile",
        record("Person", "@GenerateFolds", "Profile profile, String name")),
    TRAVERSALS_ELEMENT(
        "@GenerateTraversals over a list's element type",
        Generator.TRAVERSALS,
        "Person",
        "Profile",
        record("Person", "@GenerateTraversals", "List<Profile> profiles, String name")),
    ISOS_RETURN(
        "@GenerateIsos over the iso a method returns, beside an overload it does not mark",
        Generator.ISOS,
        "profileId",
        "Profile",
        source(
            "com.example.Wrap",
            """
            package com.example;

            import com.gen.Profile;
            import org.higherkindedj.optics.Iso;
            import org.higherkindedj.optics.annotations.GenerateIsos;

            public final class Wrap {
              private Wrap() {}

              @GenerateIsos
              public static Iso<Profile, String> profileId() {
                return Iso.of(Profile::id, Profile::new);
              }

              public static String profileId(Profile profile) {
                return profile.id();
              }

              public static String name() {
                return "wrap";
              }
            }
            """)),
    PRISMS_BOUND(
        "@GeneratePrisms over a sum type's type-parameter bound",
        Generator.PRISMS,
        "Shape",
        "Tag, Tag in Square",
        source(
            "com.example.Shape",
            """
            package com.example;

            import com.gen.Tag;
            import org.higherkindedj.optics.annotations.GeneratePrisms;

            @GeneratePrisms
            public sealed interface Shape<T extends Tag> permits Shape.Square {
              record Square<T extends Tag>(T tag) implements Shape<T> {}
            }
            """)),
    PRISMS_PERMITTED(
        "@GeneratePrisms over a permitted subtype, its prisms written into another package",
        Generator.PRISMS,
        "Shape",
        "Circle",
        List.of(CIRCLE),
        source(
            "com.example.Shape",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GeneratePrisms;

            @GeneratePrisms(targetPackage = "com.example.optics")
            public sealed interface Shape permits Circle, Square {}
            """),
        source(
            "com.example.Square",
            """
            package com.example;

            public record Square(double side) implements Shape {}
            """)),
    TYPE_ARGUMENTS(
        "a type argument, an array component and a wildcard's bounds",
        Generator.LENSES,
        "Person",
        "Profile",
        record(
            "Person",
            "@GenerateLenses",
            "Optional<Profile> profile, Profile[] history, List<? extends Profile> readers,"
                + " List<? super Profile> writers, List<?> others")),
    RECORD_BOUND(
        "a record's type-parameter bound",
        Generator.LENSES,
        "Box",
        "Tag",
        source(
            "com.example.Box",
            """
            package com.example;

            import com.gen.Tag;
            import org.higherkindedj.optics.annotations.GenerateLenses;

            @GenerateLenses
            public record Box<T extends Tag>(T value, String name) {}
            """)),
    TYPE_USE_COMPONENT(
        "a type-use annotation on a component, which would be left off",
        Generator.LENSES,
        "Person",
        "Tagged",
        record("Person", "@GenerateLenses", "@Tagged String name, int age")),
    TYPE_USE_ARGUMENT(
        "a type-use annotation on a type argument, which would be left off",
        Generator.LENSES,
        "Person",
        "Tagged",
        record("Person", "@GenerateLenses", "Optional<@Tagged String> name, int age")),
    TYPE_USE_VALUE(
        "a constant a type-use annotation sets, which would leave the annotation off",
        Generator.LENSES,
        "Person",
        "a value of @Sized's value() on name",
        record("Person", "@GenerateLenses", "@Sized(Pkgs.DEPTH) String name"),
        source(
            "com.example.Sized",
            """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            public @interface Sized {
              int value();
            }
            """)),
    OWN_CONSTANT(
        "a constant the element's own annotation sets, which could not be read",
        Generator.LENSES,
        "Person",
        "a value of @GenerateLenses's targetPackage()",
        record(
            "Person",
            "@SuppressWarnings(\"unused\") @GenerateLenses(targetPackage = Pkgs.OPTICS)",
            "String name")),
    ISOS_CONSTANT(
        "a constant a method's own annotation sets, which could not be read",
        Generator.ISOS,
        "same",
        "a value of @GenerateIsos's targetPackage()",
        source(
            "com.example.Wrap",
            """
            package com.example;

            import com.gen.Pkgs;
            import org.higherkindedj.optics.Iso;
            import org.higherkindedj.optics.annotations.GenerateIsos;

            public final class Wrap {
              private Wrap() {}

              @GenerateIsos(targetPackage = Pkgs.OPTICS)
              public static Iso<String, String> same() {
                return Iso.of(text -> text, text -> text);
              }
            }
            """)),
    FOCUS_SETTINGS(
        "a constant in the list of components a record's navigators are kept to",
        Generator.FOCUS,
        "Person",
        "a value of @GenerateFocus's includeFields()",
        record(
            "Person",
            "@GenerateFocus(includeFields = {Pkgs.FIELD})",
            "Address address, String name"),
        record("Address", "@GenerateFocus", "String street")),
    NAVIGATED_COMPONENT(
        "a component of a record a navigator steps into",
        Generator.FOCUS,
        "Person",
        "Profile in Address",
        record(
            "Person",
            "@GenerateFocus(includeFields = {\"address\"})",
            "Address address, String name"),
        record("Address", "@GenerateFocus", "Profile owner, String street")),
    NAVIGATED_SETTINGS(
        "a constant the settings of a record a navigator steps into set",
        Generator.FOCUS,
        "Person",
        "a value of @GenerateFocus's targetPackage() on Address",
        record("Person", "@GenerateFocus", "Address address, String name"),
        record("Address", "@GenerateFocus(targetPackage = Pkgs.OPTICS)", "String street")),
    NAVIGATED_COMPONENT_VALUE(
        "a constant a type-use annotation sets on a component of a record a navigator steps into",
        Generator.FOCUS,
        "Person",
        "a value of @Sized's value() on street in Address",
        record("Person", "@GenerateFocus", "Address address, String name"),
        record("Address", "@GenerateFocus", "@Sized(Pkgs.DEPTH) String street"),
        source(
            "com.example.Sized",
            """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            public @interface Sized {
              int value();
            }
            """)),
    TYPE_USE_NESTED(
        "a class literal an annotation nested in a type-use annotation sets",
        Generator.LENSES,
        "Holder",
        "a value of @Outer's value() on name",
        record("Holder", "@GenerateLenses", "@Outer(@Inner(Profile.class)) String name, int n"),
        source(
            "com.example.Outer",
            """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            public @interface Outer {
              Inner value();
            }
            """),
        source(
            "com.example.Inner",
            """
            package com.example;

            public @interface Inner {
              Class<?> value();
            }
            """));

    private final String description;
    private final Generator generator;
    private final String element;
    private final String awaited;
    private final List<Source> written;
    private final List<JavaFileObject> sources;

    LaterRound(
        String description,
        Generator generator,
        String element,
        String awaited,
        JavaFileObject... sources) {
      this(description, generator, element, awaited, List.of(), sources);
    }

    LaterRound(
        String description,
        Generator generator,
        String element,
        String awaited,
        List<Source> extra,
        JavaFileObject... sources) {
      this.description = description;
      this.generator = generator;
      this.element = element;
      this.awaited = awaited;
      this.written = Stream.concat(WRITTEN.stream(), extra.stream()).toList();
      this.sources = List.of(sources);
    }

    @Override
    public String toString() {
      return description;
    }
  }

  /**
   * A record in {@code com.example} with the given annotations and components, importing what the
   * writer brings.
   */
  private static JavaFileObject record(String name, String annotations, String components) {
    return source(
        "com.example." + name,
        """
        package com.example;

        import com.gen.Pkgs;
        import com.gen.Profile;
        import com.gen.Tagged;
        import java.util.List;
        import java.util.Optional;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import org.higherkindedj.optics.annotations.GenerateFolds;
        import org.higherkindedj.optics.annotations.GenerateGetters;
        import org.higherkindedj.optics.annotations.GenerateLenses;
        import org.higherkindedj.optics.annotations.GenerateSetters;
        import org.higherkindedj.optics.annotations.GenerateTraversals;

        %s
        public record %s(%s) {}
        """
            .formatted(annotations, name, components));
  }

  private static JavaFileObject source(String name, String body) {
    return JavaFileObjects.forSourceString(name, body);
  }

  /** The optics sources a compilation generated, by file name, leaving out what was written. */
  private static Map<String, String> opticsSourcesOf(
      Compilation compilation, List<Source> written) {
    Set<String> writtenFiles =
        written.stream()
            .map(source -> "/SOURCE_OUTPUT/" + source.name().replace('.', '/') + ".java")
            .collect(Collectors.toSet());
    return compilation.generatedSourceFiles().stream()
        .filter(file -> !writtenFiles.contains(file.getName()))
        .collect(Collectors.toMap(JavaFileObject::getName, OpticsRoundsTest::contents));
  }

  private static String contents(JavaFileObject file) {
    try {
      return file.getCharContent(true).toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Nested
  @DisplayName("Each shape an element can name what a later round brings")
  class LaterRounds {

    @ParameterizedTest(name = "{0}")
    @EnumSource(LaterRound.class)
    @DisplayName("an element waits for the round that brings what it names")
    void anElementWaitsForWhatALaterRoundBrings(LaterRound later) {
      Compilation compilation =
          javac()
              .withProcessors(new TypeWriter(later.written), later.generator.processor.get())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(later.sources);
      Compilation byHand =
          javac()
              .withProcessors(later.generator.processor.get())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(
                  Stream.concat(later.sources.stream(), later.written.stream().map(Source::byHand))
                      .toList());

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(byHand).succeededWithoutWarnings();
      Map<String, String> generated = opticsSourcesOf(compilation, later.written);
      Assertions.assertThat(generated).isNotEmpty().isEqualTo(opticsSourcesOf(byHand, List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LaterRound.class)
    @DisplayName(
        "an element reports what it names that no round brings, not errors in generated code")
    void anElementReportsWhatNoRoundBrings(LaterRound later) {
      Compilation compilation =
          javac().withProcessors(later.generator.processor.get()).compile(later.sources);

      assertThat(compilation).failed();
      String report =
          later.generator.tag
              + ": generated nothing for '"
              + later.element
              + "', as no round of this compilation resolved what it names: "
              + later.awaited
              + ".";
      assertThat(compilation).hadErrorContaining(report).inFile(later.sources.getFirst());
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> error.getMessage(null).contains(report))
          .hasSize(1);
      Assertions.assertThat(compilation.errors())
          .noneMatch(
              error ->
                  error.getSource() != null
                      && error.getSource().getName().startsWith("/SOURCE_OUTPUT/"));
    }
  }

  @Nested
  @DisplayName("A companion is never waited for")
  class Companions {

    @Test
    @DisplayName("a record naming its own companions does not wait for them")
    void aRecordNamingItsOwnCompanionsDoesNotWait() {
      // The companions are written once the record is generated, so waiting for them would never
      // end. Each is named in the package its annotation writes it into, and each processor takes
      // the other's for one the round generates too, as another optics annotation asks for it.
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor(), new FocusProcessor(), new GetterProcessor())
              .compile(
                  source(
                      "com.example.Person",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateFocus;
                      import org.higherkindedj.optics.annotations.GenerateGetters;
                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses
                      @GenerateFocus(targetPackage = "com.example.optics")
                      @GenerateGetters(targetPackage = "")
                      public record Person(
                          String name,
                          com.example.PersonLenses lenses,
                          com.example.optics.PersonFocus focus,
                          com.example.PersonGetters getters) {}
                      """));

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.PersonLenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.example.optics.PersonFocus").isNotNull();
      assertThat(compilation).generatedSourceFile("com.example.PersonGetters").isNotNull();
    }

    @Test
    @DisplayName("a record and an importer naming each other's companions are each generated")
    void aRecordAndAnImporterNamingEachOthersCompanionsAreEachGenerated() {
      // Person names, in full, the AddressLenses the importer writes, and Address names the
      // PersonLenses the lens generator writes. The generator takes what an importer writes for a
      // class it need not wait for, so PersonLenses is written, and the importer then finds it.
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor(), new ImportOpticsProcessor())
              .compile(
                  source(
                      "com.example.Person",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses
                      public record Person(String name)
                          implements Marker<com.example.AddressLenses> {}
                      """),
                  source(
                      "com.example.Address",
                      """
                      package com.example;

                      public record Address(String street) implements Marker<PersonLenses> {}
                      """),
                  source(
                      "com.example.Imports",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.ImportOptics;

                      @ImportOptics({Address.class})
                      public interface Imports {}
                      """),
                  MARKER);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.example.PersonLenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.example.AddressLenses").isNotNull();
    }

    @Test
    @DisplayName("a type of another package named like a companion, written in full, is waited for")
    void aTypeOfAnotherPackageNamedLikeACompanionIsWaitedFor() {
      // com.gen.ItemGetters is named like Item's companion, but names another package than the
      // one that companion is written into. So Basket waits for it, and folds it as the class it
      // is, rather than as something to iterate.
      Source itemGetters =
          new Source("com.gen.ItemGetters", "package com.gen; public final class ItemGetters {}");
      Compilation compilation =
          javac()
              .withProcessors(
                  new TypeWriter(Stream.concat(WRITTEN.stream(), Stream.of(itemGetters)).toList()),
                  new GetterProcessor(),
                  new FoldProcessor())
              .compile(
                  record("Item", "@GenerateGetters", "String name"),
                  record("Basket", "@GenerateFolds", "com.gen.ItemGetters item"));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .generatedSourceFile("com.example.BasketFolds")
          .contentsAsUtf8String()
          .contains("Fold<Basket, ItemGetters> item()");
    }

    @Test
    @DisplayName("a record naming its own navigator, nested in its companion, does not wait for it")
    void aRecordNamingItsOwnNavigatorDoesNotWait() {
      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(
                  source(
                      "com.example.Person",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateFocus;

                      @GenerateFocus
                      public record Person(Address address, String name)
                          implements Marker<PersonFocus.AddressNavigator<Person>> {}
                      """),
                  source(
                      "com.example.Address",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateFocus;

                      @GenerateFocus
                      public record Address(String street) {}
                      """),
                  MARKER);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.PersonFocus").isNotNull();
    }

    @Test
    @DisplayName(
        "two records naming each other's companions from two processors wait for nothing else")
    void twoRecordsNamingEachOthersCompanionsDoNotWait() {
      // Each processor takes the companion the other annotation asks for as one it need not wait
      // for, in the first round, where both wait for Profile, and in the second, as each keeps the
      // companions of every element it has met.
      Compilation compilation =
          javac()
              .withProcessors(new TypeWriter(WRITTEN), new LensProcessor(), new FocusProcessor())
              .compile(
                  source(
                      "com.example.A",
                      """
                      package com.example;

                      import com.gen.Profile;
                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses
                      public record A(Profile profile) implements Marker<BFocus> {}
                      """),
                  source(
                      "com.example.B",
                      """
                      package com.example;

                      import com.gen.Profile;
                      import org.higherkindedj.optics.annotations.GenerateFocus;

                      @GenerateFocus
                      public record B(Profile profile) implements Marker<ALenses> {}
                      """),
                  MARKER);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.ALenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.example.BFocus").isNotNull();
    }

    @Test
    @DisplayName(
        "two records naming each other's companions in full, in a later-round package, do not wait")
    void twoRecordsNamingEachOthersCompanionsInALaterRoundPackageDoNotWait() {
      // Neither processor can read where the other's companion is written until Pkgs resolves, and
      // neither meets the other's record again once it does, so each takes any part of a name
      // matching that companion's simple name for it.
      Compilation compilation =
          javac()
              .withProcessors(new TypeWriter(WRITTEN), new LensProcessor(), new FocusProcessor())
              .compile(
                  source(
                      "com.example.A",
                      """
                      package com.example;

                      import com.gen.Pkgs;
                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses(targetPackage = Pkgs.OPTICS)
                      public record A(String name) implements Marker<com.example.optics.BFocus> {}
                      """),
                  source(
                      "com.example.B",
                      """
                      package com.example;

                      import com.gen.Pkgs;
                      import org.higherkindedj.optics.annotations.GenerateFocus;

                      @GenerateFocus(targetPackage = Pkgs.OPTICS)
                      public record B(String name) implements Marker<com.example.optics.ALenses> {}
                      """),
                  MARKER);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.optics.ALenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.example.optics.BFocus").isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Generator.class)
    @DisplayName("an element naming its own companion does not wait for it, whichever generator")
    void anElementNamingItsOwnCompanionDoesNotWait(Generator generator) {
      // Each generator's companion is named as that generator names it, so a name its element
      // writes for it is taken for one this round generates.
      String suffix = generator.tag.substring("@Generate".length());
      JavaFileObject element =
          switch (generator) {
            case ISOS ->
                source(
                    "com.example.Wrap",
                    """
                    package com.example;

                    import org.higherkindedj.optics.Iso;
                    import org.higherkindedj.optics.annotations.GenerateIsos;

                    public final class Wrap {
                      private Wrap() {}

                      @GenerateIsos
                      public static Iso<String, WrapIsos> same() {
                        return Iso.of(text -> null, isos -> "");
                      }
                    }
                    """);
            case PRISMS ->
                source(
                    "com.example.Shape",
                    """
                    package com.example;

                    import org.higherkindedj.optics.annotations.GeneratePrisms;

                    @GeneratePrisms
                    public sealed interface Shape extends Marker<ShapePrisms> permits Shape.Square {
                      record Square(double side) implements Shape {}
                    }
                    """);
            default ->
                source(
                    "com.example.Person",
                    """
                    package com.example;

                    import org.higherkindedj.optics.annotations.Generate%s;

                    @Generate%s
                    public record Person(String name) implements Marker<Person%s> {}
                    """
                        .formatted(suffix, suffix, suffix));
          };
      String owner =
          switch (generator) {
            case ISOS -> "Wrap";
            case PRISMS -> "Shape";
            default -> "Person";
          };

      Compilation compilation =
          javac().withProcessors(generator.processor.get()).compile(element, MARKER);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example." + owner + suffix).isNotNull();
    }

    @Test
    @DisplayName(
        "a record importing its own companion from the package it is written into does not wait")
    void aRecordImportingItsOwnCompanionDoesNotWait() {
      // The simple name is a companion's, however it was imported, so the record does not wait
      // for it.
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor())
              .compile(
                  source(
                      "com.example.Person",
                      """
                      package com.example;

                      import com.example.optics.PersonLenses;
                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses(targetPackage = "com.example.optics")
                      public record Person(String name) implements Marker<PersonLenses> {}
                      """),
                  MARKER);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.optics.PersonLenses").isNotNull();
    }

    @Test
    @DisplayName(
        "a companion a reached record imports from the element's target package is not awaited")
    void aCompanionImportedInAReachedRecordIsNotAwaited() {
      // Address names Person's companion by the simple name it imports from the package Person's
      // companion is written into, and Person, reaching Address, does not wait for it there.
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor())
              .compile(
                  source(
                      "com.example.Person",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses(targetPackage = "com.example.optics")
                      public record Person(String name, Address address) {}
                      """),
                  source(
                      "com.example.Address",
                      """
                      package com.example;

                      import com.example.optics.PersonLenses;

                      public record Address(String street) implements Marker<PersonLenses> {}
                      """),
                  MARKER);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.optics.PersonLenses").isNotNull();
    }

    @Test
    @DisplayName("records reaching each other, each importing its own companion, do not wait")
    void recordsReachingEachOtherImportingTheirOwnCompanionsDoNotWait() {
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor())
              .compile(
                  source(
                      "com.example.Person",
                      """
                      package com.example;

                      import com.example.optics.PersonLenses;
                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses(targetPackage = "com.example.optics")
                      public record Person(String name, Team team) implements Marker<PersonLenses> {}
                      """),
                  source(
                      "com.example.Team",
                      """
                      package com.example;

                      import com.example.optics.TeamLenses;
                      import java.util.List;
                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses(targetPackage = "com.example.optics")
                      public record Team(List<Person> members) implements Marker<TeamLenses> {}
                      """),
                  MARKER);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.optics.PersonLenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.example.optics.TeamLenses").isNotNull();
    }

    @Test
    @DisplayName("two records importing each other's companions from a target package do not wait")
    void twoRecordsImportingEachOthersCompanionsDoNotWait() {
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor())
              .compile(
                  source(
                      "com.example.A",
                      """
                      package com.example;

                      import com.example.optics.BLenses;
                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses(targetPackage = "com.example.optics")
                      public record A(String x) implements Marker<BLenses> {}
                      """),
                  source(
                      "com.example.B",
                      """
                      package com.example;

                      import com.example.optics.ALenses;
                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses(targetPackage = "com.example.optics")
                      public record B(String y) implements Marker<ALenses> {}
                      """),
                  MARKER);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.optics.ALenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.example.optics.BLenses").isNotNull();
    }

    @Test
    @DisplayName("a field list naming a component of a companion's type draws no warning")
    void aFieldListNamingACompanionTypedComponentDrawsNoWarning() {
      // The companion is not waited for, so the component's type has not resolved when the field
      // list is checked, and nothing can be said of a navigator into it.
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor(), new FocusProcessor())
              .withOptions("-Xlint:all,-processing", "-Werror")
              .compile(
                  source(
                      "com.example.Person",
                      """
                      package com.example;

                      import java.util.List;
                      import org.higherkindedj.optics.annotations.GenerateFocus;
                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses
                      @GenerateFocus(includeFields = {"address", "lenses"})
                      public record Person(String name, Address address, List<PersonLenses> lenses) {}
                      """),
                  source(
                      "com.example.Address",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateFocus;

                      @GenerateFocus
                      public record Address(String street) {}
                      """));

      assertThat(compilation).succeededWithoutWarnings();
    }
  }

  @Nested
  @DisplayName("What an element's wait reads")
  class Reading {

    @Test
    @DisplayName(
        "a component deprecated for removal by a constant a later round brings is suppressed"
            + " whichever way it resolves")
    void aLaterForRemovalIsSuppressedBothWays() {
      // The generators do not wait for another library's annotation, so the lens reads the
      // component in the round its forRemoval is still javac's mark.
      Compilation compilation =
          javac()
              .withProcessors(new TypeWriter(WRITTEN), new LensProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(
                  record(
                      "Point",
                      "@GenerateLenses",
                      "@Deprecated(forRemoval = Pkgs.REMOVE) int x, int y"));

      assertThat(compilation).succeededWithoutWarnings();
      Assertions.assertThat(
              contents(compilation.generatedSourceFile("com.example.PointLenses").orElseThrow()))
          .contains("@SuppressWarnings({\"deprecation\", \"removal\"})");
    }

    @Test
    @DisplayName("a value of another library's annotation on a declaration does not hold it back")
    void anotherLibrarysDeclarationAnnotationDoesNotHoldARecordBack() {
      // The generators neither read nor copy what @Ref sets, so its value is not read: the record's
      // own companion, which it names, cannot hold it back.
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor())
              .compile(
                  source(
                      "com.example.Person",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses
                      @Ref(PersonLenses.class)
                      public record Person(String name) {}
                      """),
                  source(
                      "com.example.Ref",
                      """
                      package com.example;

                      public @interface Ref {
                        Class<?> value();
                      }
                      """));

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.PersonLenses").isNotNull();
    }

    @Test
    @DisplayName("an iso method that waits is generated alone, apart from an overload of its name")
    void anIsoMethodThatWaitsIsGeneratedApartFromItsOverload() {
      // same() is generated in the first round; same(int) waits for Profile, and is refused for its
      // parameter once it resolves, without same() being generated a second time.
      RoundRoots roots = new RoundRoots();
      Compilation compilation =
          javac()
              .withProcessors(roots, new TypeWriter(WRITTEN), new IsoProcessor())
              .compile(
                  source(
                      "com.example.Wrap",
                      """
                      package com.example;

                      import com.gen.Profile;
                      import org.higherkindedj.optics.Iso;
                      import org.higherkindedj.optics.annotations.GenerateIsos;

                      public final class Wrap {
                        private Wrap() {}

                        @GenerateIsos
                        public static Iso<String, String> same() {
                          return Iso.of(text -> text, text -> text);
                        }

                        @GenerateIsos
                        public static Iso<Profile, String> same(int unused) {
                          return Iso.of(Profile::id, Profile::new);
                        }
                      }
                      """));

      assertThat(compilation).hadErrorContaining("'same' takes parameters");
      assertThat(compilation).hadErrorCount(1);
      // WrapIsos is written, for same(), in the round that writes Profile, so before it resolved.
      Assertions.assertThat(roots.first("WrapIsos")).isEqualTo(roots.first("Profile"));
    }

    @Test
    @DisplayName(
        "an element reaching a long chain of records reads it without overflowing the stack")
    void anElementReachingALongChainOfRecordsReadsIt() {
      // Each record holds the next, so the first reaches every one of them, as a generated schema
      // model can; each is read once, from a queue, rather than from the one before it.
      int length = 3000;
      StringBuilder chain = new StringBuilder("package com.example;\n\nfinal class Chain {}\n");
      chain.append("@org.higherkindedj.optics.annotations.GenerateLenses\n");
      for (int index = 0; index < length - 1; index++) {
        chain.append("record R%d(R%d next, String name) {}%n".formatted(index, index + 1));
      }
      chain.append("record R%d(String name) {}%n".formatted(length - 1));

      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor())
              .compile(source("com.example.Chain", chain.toString()));

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.R0Lenses").isNotNull();
    }

    @Test
    @DisplayName("records reaching one later-round type through each other are each generated")
    void recordsReachingOneLaterRoundTypeThroughEachOtherAreEachGenerated() {
      // Head reaches Middle, which reaches Tail, which names Profile: Head's walk notes each of
      // them as reaching it, so Middle's own walk stops at Tail, and each waits for Profile.
      RoundRoots roots = new RoundRoots();
      Compilation compilation =
          javac()
              .withProcessors(roots, new TypeWriter(WRITTEN), new LensProcessor())
              .compile(
                  record("Head", "@GenerateLenses", "Middle middle"),
                  record("Middle", "@GenerateLenses", "Tail tail"),
                  record("Tail", "@GenerateLenses", "Profile profile"));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.example.HeadLenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.example.MiddleLenses").isNotNull();
      assertThat(compilation)
          .generatedSourceFile("com.example.TailLenses")
          .contentsAsUtf8String()
          .contains("import com.gen.Profile;");
      // Each is written in the round Profile resolves in, so each is brought by the round after.
      Assertions.assertThat(Stream.of("HeadLenses", "MiddleLenses", "TailLenses").map(roots::first))
          .containsOnly(roots.first("Profile") + 1);
    }
  }

  @Nested
  @DisplayName("What the last round reports")
  class Reports {

    @Test
    @DisplayName("an element waiting when an error ends the rounds early is left to that error")
    void anElementWaitingWhenAnErrorEndsTheRoundsIsLeftToThatError() {
      // The misplaced annotation is refused in the first round, so javac makes the next round the
      // last, and the record waiting for Profile is not generated for that error.
      Compilation compilation =
          javac()
              .withProcessors(new TypeWriter(WRITTEN), new LensProcessor())
              .compile(
                  record("Person", "@GenerateLenses", "Profile profile"),
                  source(
                      "com.example.Misplaced",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses
                      public final class Misplaced {}
                      """));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("can only be applied to records");
      Assertions.assertThat(compilation.diagnostics())
          .filteredOn(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
          .noneMatch(diagnostic -> diagnostic.getMessage(null).contains("generated nothing"));
    }

    @Test
    @DisplayName("a report names only what never resolved, not a companion the record names")
    void aReportNamesOnlyWhatNeverResolved() {
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor())
              .compile(
                  source(
                      "com.example.Person",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses
                      public record Person(Missing missing, PersonLenses lenses) {}
                      """));

      assertThat(compilation)
          .hadErrorContaining(
              "@GenerateLenses: generated nothing for 'Person', as no round of this compilation"
                  + " resolved what it names: Missing.");
    }

    @Test
    @DisplayName("a report lists ten of what never resolved, and counts the rest")
    void aReportListsTenNamesAndCountsTheRest() {
      String components =
          IntStream.rangeClosed(1, 12)
              .mapToObj(index -> "Missing" + index + " missing" + index)
              .collect(Collectors.joining(", "));
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor())
              .compile(
                  source(
                      "com.example.Many",
                      "package com.example; @org.higherkindedj.optics.annotations.GenerateLenses"
                          + " public record Many("
                          + components
                          + ") {}"));

      assertThat(compilation)
          .hadErrorContaining(
              "resolved what it names: Missing1, Missing2, Missing3, Missing4, Missing5, Missing6,"
                  + " Missing7, Missing8, Missing9, Missing10, and 2 more.");
    }
  }

  @Nested
  @DisplayName("Gradle's incremental build")
  class IncrementalBuilds {

    @Test
    @DisplayName("an element Gradle's incremental build reads from its class file is generated so")
    void anElementReadFromItsClassFileIsGeneratedAsFromSource(@TempDir Path temp)
        throws IOException {
      // javac does not resolve again, in a later round, a type a class file names, so an element
      // read from one does not wait for what its own class file names: that names the type in full,
      // and the companion is written importing it, as from source.
      List<JavaFileObject> sources =
          List.of(record("Person", "@GenerateLenses", "Profile profile, String name"));

      Reprocessing.Run fromSource =
          Reprocessing.fromSource(
              temp.resolve("source"), sources, List.of(), () -> List.of(new TypeWriter(WRITTEN)));
      Reprocessing.Run fromClassFiles =
          Reprocessing.fromClassNames(
              temp.resolve("class-files"),
              sources,
              List.of(),
              () -> List.of(new TypeWriter(WRITTEN)));

      Assertions.assertThat(fromSource.succeeded()).isTrue();
      Assertions.assertThat(fromSource.generated()).containsKey("com/example/PersonLenses.java");
      Assertions.assertThat(fromClassFiles).isEqualTo(fromSource);
    }

    @Test
    @DisplayName("a record read from its class file waits for a recompiled record it steps into")
    void aClassFileRecordWaitsForARecompiledRecordItStepsInto(@TempDir Path temp)
        throws IOException {
      // Gradle recompiles Address and Street and hands Person and Hub over by name. Address and
      // Street wait for Profile; Person, from its class file, steps into Address, and so waits with
      // it, and Hub steps into Street through Person's class file, and waits too.
      JavaFileObject person = record("Person", "@GenerateFocus", "Address address, String name");
      JavaFileObject address = record("Address", "@GenerateFocus", "Profile owner, String street");
      JavaFileObject hub = record("Hub", "@GenerateFocus", "Desk desk, String name");
      JavaFileObject desk = record("Desk", "@GenerateFocus", "Street street, int floor");
      JavaFileObject street = record("Street", "@GenerateFocus", "Profile owner, String name");
      List<JavaFileObject> sources = List.of(person, address, hub, desk, street);

      Reprocessing.Run fromSource =
          Reprocessing.fromSource(
              temp.resolve("source"), sources, List.of(), () -> List.of(new TypeWriter(WRITTEN)));
      Reprocessing.Run recompiled =
          Reprocessing.fromClassNames(
              temp.resolve("recompiled"),
              sources,
              List.of(address, street),
              List.of(),
              () -> List.of(new TypeWriter(WRITTEN)));

      Assertions.assertThat(fromSource.succeeded()).isTrue();
      Assertions.assertThat(fromSource.generated().get("com/example/PersonFocus.java"))
          .contains("AddressNavigator<Person> address()");
      Assertions.assertThat(recompiled).isEqualTo(fromSource);
    }

    @Test
    @DisplayName("an element read from its class file is named in full where it is reported")
    void anElementReadFromItsClassFileIsReportedInFull(@TempDir Path temp) throws IOException {
      // The second build runs no writer, so the Profile the recompiled Address names never comes.
      // Person and Wrap, read from their class files, reach Address, and wait with it; a class file
      // gives neither a position to be reported at.
      Deque<List<Processor>> writers =
          new ArrayDeque<>(List.of(List.of(new TypeWriter(WRITTEN)), List.of()));
      JavaFileObject address = record("Address", "@GenerateFocus", "Profile owner, String street");
      JavaFileObject wrap =
          source(
              "com.example.Wrap",
              """
              package com.example;

              import org.higherkindedj.optics.Iso;
              import org.higherkindedj.optics.annotations.GenerateIsos;

              public final class Wrap {
                private Wrap() {}

                @GenerateIsos
                public static Iso<Address, String> street() {
                  return Iso.of(Address::street, street -> new Address(null, street));
                }
              }
              """);

      Reprocessing.Run recompiled =
          Reprocessing.fromClassNames(
              temp,
              List.of(
                  record("Person", "@GenerateFocus", "Address address, String name"),
                  address,
                  wrap),
              List.of(address),
              List.of(),
              writers::removeFirst);

      Assertions.assertThat(recompiled.succeeded()).isFalse();
      Assertions.assertThat(recompiled.diagnostics())
          .anyMatch(
              line ->
                  line.contains(
                      "@GenerateFocus: generated nothing for 'com.example.Person', as no round of"
                          + " this compilation resolved what it names: Profile in Address."))
          .anyMatch(
              line ->
                  line.contains(
                      "@GenerateIsos: generated nothing for 'com.example.Wrap.street', as no round"
                          + " of this compilation resolved what it names: Profile in Address."));
    }

    @Test
    @DisplayName(
        "a recompiled record naming a type a class file names too is generated once written")
    void aRecompiledRecordNamingATypeAClassFileNamesIsGenerated(@TempDir Path temp)
        throws IOException {
      // Person's class file names Profile, which Gradle deleted, so javac keeps its failure to find
      // Profile for the whole compilation, and Address, recompiled, never sees Profile resolve,
      // though the first round brings it: Address is generated once a round has brought it.
      JavaFileObject address = record("Address", "@GenerateLenses", "Profile owner, String street");
      List<JavaFileObject> sources =
          List.of(record("Person", "@GenerateLenses", "Profile profile, Address address"), address);

      Reprocessing.Run fromSource =
          Reprocessing.fromSource(
              temp.resolve("source"), sources, List.of(), () -> List.of(new TypeWriter(WRITTEN)));
      Reprocessing.Run recompiled =
          Reprocessing.fromClassNames(
              temp.resolve("recompiled"),
              sources,
              List.of(address),
              List.of(),
              () -> List.of(new TypeWriter(WRITTEN)));

      Assertions.assertThat(fromSource.succeeded()).isTrue();
      Assertions.assertThat(recompiled).isEqualTo(fromSource);
    }

    @Test
    @DisplayName("a recompiled record whose annotation a class file carries too is generated")
    void aRecompiledRecordWhoseAnnotationAClassFileCarriesIsGenerated(@TempDir Path temp)
        throws IOException {
      // Person's class file carries @Tagged, which Gradle deleted, so javac keeps its failure to
      // find Tagged, and Address, recompiled, never sees its own @Tagged resolve: Address is
      // generated once a round has brought Tagged, and the annotation is left off, as from the
      // class file.
      JavaFileObject address = record("Address", "@GenerateLenses", "@Tagged String street");
      List<JavaFileObject> sources =
          List.of(
              record("Person", "@GenerateLenses", "@Tagged String nick, Address address"), address);

      Reprocessing.Run recompiled =
          Reprocessing.fromClassNames(
              temp.resolve("recompiled"),
              sources,
              List.of(address),
              List.of(),
              () -> List.of(new TypeWriter(WRITTEN)));

      Assertions.assertThat(recompiled.succeeded()).isTrue();
      Assertions.assertThat(recompiled.generated()).containsKey("com/example/AddressLenses.java");
    }
  }

  @Nested
  @DisplayName("Compilers that place types otherwise")
  class Compilers {

    @Test
    @DisplayName("an element waits where the compilation has no modules")
    void anElementWaitsWhereTheCompilationHasNoModules() {
      // Under -source 8 javac places a type in no module, and an element is held by its name alone.
      // Records are not Java 8, so the iso is over an interface the writer brings.
      Compilation compilation =
          javac()
              .withProcessors(new TypeWriter(List.of(TAG)), new IsoProcessor())
              .withOptions("-source", "8", "-target", "8", "-Xlint:-options")
              .compile(
                  source(
                      "com.example.Wrap",
                      """
                      package com.example;

                      import com.gen.Tag;
                      import org.higherkindedj.optics.Iso;
                      import org.higherkindedj.optics.annotations.GenerateIsos;

                      public final class Wrap {
                        private Wrap() {}

                        @GenerateIsos
                        public static Iso<Tag, String> tagName() {
                          return Iso.of(tag -> "tag", name -> new Tag() {});
                        }
                      }
                      """));

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.WrapIsos")
          .contentsAsUtf8String()
          .contains("import com.gen.Tag;");
    }

    @Test
    @DisplayName("an element waits where the compiler cannot say which file a type came from")
    void anElementWaitsWhereTheCompilerCannotPlaceATypesFile() {
      // Such a compiler places no type in source or a class file, so nothing the element reaches
      // is read, and the element itself is read as source: it waits for what it names.
      Compilation compilation =
          javac()
              .withProcessors(
                  GeneratorTestHelper.withoutFileObjectLookup(
                      new TypeWriter(WRITTEN), new LensProcessor()))
              .compile(
                  record("Person", "@GenerateLenses", "Profile profile, Address address"),
                  record("Address", "", "String street"));

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.PersonLenses")
          .contentsAsUtf8String()
          .contains("import com.gen.Profile;");
    }
  }
}
