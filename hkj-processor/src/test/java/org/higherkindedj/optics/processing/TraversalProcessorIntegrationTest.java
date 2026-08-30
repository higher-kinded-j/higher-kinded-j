// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TraversalProcessorIntegrationTest {

  @Test
  void shouldGenerateTraversalForRecordComponent() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Playlist",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.List;

            @GenerateTraversals
            public record Playlist(String name, List<String> songTitles) {}
            """);

    final String expectedTraversal =
        """
        public static Traversal<Playlist, String> songTitles() {
            return new Traversal<Playlist, String>() {
                @Override
                public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Playlist> modifyF(
                    Function<String, Kind<F, String>> f,
                    Playlist source,
                    Applicative<F> applicative
                ) {
                    final var effectOfList = Traversals.traverseList(source.songTitles(), f, applicative);
                    return applicative.map(newList -> new Playlist(source.name(), newList), effectOfList);
                }
            };
        }
        """;

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.PlaylistTraversals";
    assertGeneratedCodeContains(compilation, generatedClassName, expectedTraversal);
  }

  @Test
  @DisplayName("a wildcard type argument is focused as the type it stands for")
  void shouldFocusTheTypeAWildcardStandsFor() {
    // A wildcard cannot be written into the generated optic, so what is named is the type it
    // resolves to: an upper bound where there is one, and Object where the wildcard stands for
    // anything at all.
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Wildcards",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.List;

            @GenerateTraversals
            public record Wildcards(
                List<? extends CharSequence> bounded,
                List<?> unbounded,
                List<? super String> superBounded) {}
            """);

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.WildcardsTraversals";
    assertGeneratedCodeContains(
        compilation,
        generatedClassName,
        "public static Traversal<Wildcards, CharSequence> bounded()");
    assertGeneratedCodeContains(
        compilation, generatedClassName, "public static Traversal<Wildcards, Object> unbounded()");
    assertGeneratedCodeContains(
        compilation,
        generatedClassName,
        "public static Traversal<Wildcards, Object> superBounded()");
  }

  @Test
  @DisplayName("a generic record's type variables reach the method generated for it")
  void shouldCarryTheRecordsTypeVariables() {
    // A traversal over Holder<T> focuses T, so the static method has to declare T itself. The
    // effect is declared alongside it, and takes another name where the record has claimed F.
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Holder",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.List;

            @GenerateTraversals
            public record Holder<T>(String label, List<T> items) {}
            """);
    final var effectNamedSource =
        JavaFileObjects.forSourceString(
            "com.example.Claimed",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.List;

            @GenerateTraversals
            public record Claimed<F>(List<F> items) {}
            """);

    var compilation =
        javac().withProcessors(new TraversalProcessor()).compile(sourceFile, effectNamedSource);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(
        compilation,
        "com.example.HolderTraversals",
        "public static <T> Traversal<Holder<T>, T> items()");
    assertGeneratedCodeContains(
        compilation, "com.example.HolderTraversals", "new Holder<T>(source.label(), newList)");
    assertGeneratedCodeContains(
        compilation,
        "com.example.ClaimedTraversals",
        "public static <F> Traversal<Claimed<F>, F> items()");
    assertGeneratedCodeContains(
        compilation, "com.example.ClaimedTraversals", "F1 extends WitnessArity<TypeArity.Unary>");
  }

  @Test
  @DisplayName("a component whose generator focus index exceeds its type arguments is skipped")
  void shouldSkipComponentWhenGeneratorFocusIndexExceedsTypeArguments() {
    // The test-scope BoxIndexOneGenerator supports com.example.hkjtest.Box but focuses on type
    // argument index 1, which a Box<T> never has, so the traversal method is skipped.
    final var markerSource =
        JavaFileObjects.forSourceString(
            "com.example.hkjtest.Box",
            """
            package com.example.hkjtest;

            public class Box<T> {}
            """);

    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.BoxRecord",
            """
            package com.example;

            import com.example.hkjtest.Box;
            import org.higherkindedj.optics.annotations.GenerateTraversals;

            @GenerateTraversals
            public record BoxRecord(Box<String> boxed) {}
            """);

    var compilation =
        javac().withProcessors(new TraversalProcessor()).compile(markerSource, sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.BoxRecordTraversals").isNotNull();
    assertGeneratedCodeDoesNotContain(compilation, "com.example.BoxRecordTraversals", "boxed");
    assertThat(compilation)
        .hadNoteContaining(
            "@GenerateTraversals: no traversal was generated for component 'BoxRecord.boxed' of"
                + " type Box<String>. The generator"
                + " org.higherkindedj.optics.processing.testspi.TestMarkerGenerators.BoxIndexOneGenerator"
                + " focuses type argument 1, and Box<String> has only 1.");
  }

  @Test
  @DisplayName("a component that is neither an array nor a declared type is skipped")
  void shouldSkipComponentThatIsNeitherArrayNorDeclared() {
    // The test-scope TypeVariableGenerator supports type variables named TRAVMARKER, steering a
    // type-variable component into createTraversalMethod, which cannot handle it and skips it.
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.VarRecord",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;

            @GenerateTraversals
            public record VarRecord<TRAVMARKER>(TRAVMARKER value) {}
            """);

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.VarRecordTraversals").isNotNull();
    assertGeneratedCodeDoesNotContain(compilation, "com.example.VarRecordTraversals", "value");
    assertThat(compilation)
        .hadNoteContaining(
            "no traversal was generated for component 'VarRecord.value' of type TRAVMARKER. The"
                + " generator"
                + " org.higherkindedj.optics.processing.testspi.TestMarkerGenerators.TypeVariableGenerator"
                + " claims the component, but a traversal can only be generated for a declared"
                + " type or an array, and TRAVMARKER is neither.");
  }

  @Test
  @DisplayName("a Collection component is traversed through the helper that rebuilds its shape")
  void shouldGenerateTraversalForCollectionComponent() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Job",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.Collection;
            import java.util.List;

            @GenerateTraversals
            public record Job(Collection<String> tags, List<String> notes) {}
            """);

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation).hadNoteCount(0);
    assertThat(compilation).hadWarningCount(0);
    assertGeneratedCodeContains(
        compilation, "com.example.JobTraversals", "public static Traversal<Job, String> tags()");
    assertGeneratedCodeContains(
        compilation,
        "com.example.JobTraversals",
        "final var effectOfCollection = Traversals.traverseCollection(source.tags(), f,"
            + " applicative);");
    assertGeneratedCodeContains(
        compilation, "com.example.JobTraversals", "public static Traversal<Job, String> notes()");
  }

  @Test
  @DisplayName("a Collection subtype no generator supports is reported where it is declared")
  void shouldWarnWhenNoGeneratorSupportsACollectionSubtype() {
    // A Deque holds elements as plainly as a List does, and no generator claims it. Generating
    // nothing compiles, so the only signal the author gets is this note.
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Job",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.Deque;
            import java.util.List;

            @GenerateTraversals
            public record Job(Deque<String> tasks, List<String> notes) {}
            """);

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadNoteContaining(
            "@GenerateTraversals: no traversal was generated for component 'Job.tasks' of type"
                + " Deque<String>. No TraversableGenerator on the annotation processor path"
                + " supports Deque. Declare the component as a supported container (List, Set,"
                + " Collection, Map, Optional or an array), or put a TraversableGenerator for its"
                + " type on the annotation processor path.")
        .inFile(sourceFile)
        .onLineContaining("Deque<String> tasks");
    assertThat(compilation).hadNoteCount(1);
    assertThat(compilation).hadWarningCount(0);
    assertGeneratedCodeDoesNotContain(
        compilation, "com.example.JobTraversals", "Traversal<Job, String> tasks()");
    assertGeneratedCodeContains(
        compilation, "com.example.JobTraversals", "public static Traversal<Job, String> notes()");
  }

  @Test
  @DisplayName("a Map subtype no generator supports is reported where it is declared")
  void shouldWarnWhenNoGeneratorSupportsAMapSubtype() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Ledger",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.SortedMap;

            @GenerateTraversals
            public record Ledger(SortedMap<String, Integer> balances) {}
            """);

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadNoteContaining(
            "no traversal was generated for component 'Ledger.balances' of type SortedMap<String,"
                + " Integer>. No TraversableGenerator on the annotation processor path supports"
                + " SortedMap.");
    assertThat(compilation).hadNoteCount(1);
    assertThat(compilation).hadWarningCount(0);
  }

  @Test
  @DisplayName("a component that holds no elements is passed over without comment")
  void shouldStaySilentForAComponentThatIsNotAContainer() {
    // Not generating for a String, an int or a Path is the expected outcome, not a gap, so the
    // note is reserved for a Collection or a Map. Path implements Iterable, which is why a
    // bare Iterable is not the bar.
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Person",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.nio.file.Path;
            import java.util.List;
            import java.util.function.Supplier;

            @GenerateTraversals
            public record Person(
                String name, int age, Path home, Supplier<String> greeting, List<String> tags) {}
            """);

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation).hadNoteCount(0);
    assertThat(compilation).hadWarningCount(0);
    assertGeneratedCodeContains(
        compilation,
        "com.example.PersonTraversals",
        "public static Traversal<Person, String> tags()");
  }

  @Test
  @DisplayName("a raw container is reported where it is declared")
  void shouldWarnForARawContainer() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Bag",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.List;

            @GenerateTraversals
            @SuppressWarnings("rawtypes")
            public record Bag(List items) {}
            """);

    var compilation = javac().withProcessors(new TraversalProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadNoteContaining(
            "@GenerateTraversals: no traversal was generated for component 'Bag.items' of type"
                + " List. List is written without a type argument, so there is no element type to"
                + " focus. Give the component its type arguments, as in List<E>.");
    assertGeneratedCodeDoesNotContain(compilation, "com.example.BagTraversals", "items");
  }

  @Test
  @DisplayName("a claimed type that declares no type parameter is reported against its generator")
  void shouldWarnWhenAClaimedTypeDeclaresNoTypeParameter() {
    // The test-scope SoloGenerator claims com.example.hkjtest.Solo, which has no type parameter,
    // so there is nothing the component could be given: the generator's supports() is what needs
    // narrowing, and the note says so.
    final var markerSource =
        JavaFileObjects.forSourceString(
            "com.example.hkjtest.Solo",
            """
            package com.example.hkjtest;

            public class Solo {}
            """);

    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.SoloRecord",
            """
            package com.example;

            import com.example.hkjtest.Solo;
            import org.higherkindedj.optics.annotations.GenerateTraversals;

            @GenerateTraversals
            public record SoloRecord(Solo value) {}
            """);

    var compilation =
        javac().withProcessors(new TraversalProcessor()).compile(markerSource, sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadNoteContaining(
            "no traversal was generated for component 'SoloRecord.value' of type Solo. Solo is"
                + " written without a type argument, so there is no element type to focus. The"
                + " generator"
                + " org.higherkindedj.optics.processing.testspi.TestMarkerGenerators.SoloGenerator"
                + " claims a type that declares no type parameter; narrow its supports().");
    assertGeneratedCodeDoesNotContain(
        compilation, "com.example.SoloRecordTraversals", "Traversal<SoloRecord");
  }
}
