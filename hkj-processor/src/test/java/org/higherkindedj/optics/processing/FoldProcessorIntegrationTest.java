// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class FoldProcessorIntegrationTest {

  @Test
  void shouldGenerateFoldsForRecord() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.User",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFolds;
            import org.higherkindedj.optics.Fold;

            @GenerateFolds
            public record User(String name, int age) {}
            """);

    final String expectedNameFold =
        """
        @Generated
        private static final class NameFold implements Fold<User, String> {
          @Override
          public <M> M foldMap(Monoid<M> monoid, Function<? super String, ? extends M> f, User source) {
            return f.apply(source.name());
          }
        }
        """;

    final String expectedAgeFold =
        """
        @Generated
        private static final class AgeFold implements Fold<User, Integer> {
          @Override
          public <M> M foldMap(Monoid<M> monoid, Function<? super Integer, ? extends M> f, User source) {
            return f.apply(source.age());
          }
        }
        """;

    var compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.UserFolds";
    assertGeneratedCodeContains(
        compilation,
        generatedClassName,
        "public static Fold<User, String> name() { return new NameFold(); }");

    assertGeneratedCodeContains(compilation, generatedClassName, expectedNameFold);
    assertGeneratedCodeContains(
        compilation,
        generatedClassName,
        "public static Fold<User, Integer> age() { return new AgeFold(); }");

    assertGeneratedCodeContains(compilation, generatedClassName, expectedAgeFold);
  }

  @Test
  void shouldGenerateFoldsForRecordWithCollections() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Order",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFolds;
            import org.higherkindedj.optics.Fold;
            import java.util.List;

            @GenerateFolds
            public record Order(String id, List<String> items) {}
            """);

    final String expectedIdFold =
        """
        @Generated
        private static final class IdFold implements Fold<Order, String> {
          @Override
          public <M> M foldMap(Monoid<M> monoid, Function<? super String, ? extends M> f, Order source) {
            return f.apply(source.id());
          }
        }
        """;

    final String expectedItemsFold =
        """
        @Generated
        private static final class ItemsFold implements Fold<Order, String> {
          @Override
          public <M> M foldMap(Monoid<M> monoid, Function<? super String, ? extends M> f, Order source) {
            M result = monoid.empty();
            for (var element : source.items()) {
              result = monoid.combine(result, f.apply(element));
            }
            return result;
          }
        }
        """;

    var compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.OrderFolds";
    assertGeneratedCodeContains(
        compilation,
        generatedClassName,
        "public static Fold<Order, String> id() { return new IdFold(); }");

    assertGeneratedCodeContains(compilation, generatedClassName, expectedIdFold);
    assertGeneratedCodeContains(
        compilation,
        generatedClassName,
        "public static Fold<Order, String> items() { return new ItemsFold(); }");

    assertGeneratedCodeContains(compilation, generatedClassName, expectedItemsFold);
  }

  @Test
  void shouldGenerateFoldsForParameterizedRecord() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Container",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFolds;
            import org.higherkindedj.optics.Fold;

            @GenerateFolds
            public record Container<T>(T value, String label) {}
            """);

    final String expectedValueFold =
        """
        @Generated
        private static final class ValueFold<T> implements Fold<Container<T>, T> {
          @Override
          public <M> M foldMap(Monoid<M> monoid, Function<? super T, ? extends M> f, Container<T> source) {
            return f.apply(source.value());
          }
        }
        """;

    final String expectedLabelFold =
        """
        @Generated
        private static final class LabelFold<T> implements Fold<Container<T>, String> {
          @Override
          public <M> M foldMap(Monoid<M> monoid, Function<? super String, ? extends M> f, Container<T> source) {
            return f.apply(source.label());
          }
        }
        """;

    var compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.ContainerFolds";
    assertGeneratedCodeContains(
        compilation,
        generatedClassName,
        "public static <T> Fold<Container<T>, T> value() { return new ValueFold<>(); }");

    assertGeneratedCodeContains(compilation, generatedClassName, expectedValueFold);
    assertGeneratedCodeContains(
        compilation,
        generatedClassName,
        "public static <T> Fold<Container<T>, String> label() { return new LabelFold<>(); }");

    assertGeneratedCodeContains(compilation, generatedClassName, expectedLabelFold);
  }

  @Test
  @DisplayName("a record whose own type parameter is named M keeps the monoid's variable apart")
  void shouldNameTheMonoidVariablePastOneTheRecordClaimed() {
    // foldMap declares the monoid's type variable inside the record's own, so a record that took
    // M for itself would have the monoid shadow its element type; the variable takes the first
    // free name instead, as a traversal's effect variable does when the record has claimed F.
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Box",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFolds;
            import java.util.List;

            @GenerateFolds
            public record Box<M>(List<M> items, M single) {}
            """);

    var compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    final String generatedClassName = "com.example.BoxFolds";
    assertGeneratedCodeContains(
        compilation, generatedClassName, "public static <M> Fold<Box<M>, M> items()");
    assertGeneratedCodeContains(
        compilation,
        generatedClassName,
        "public <M1> M1 foldMap(Monoid<M1> monoid, Function<? super M, ? extends M1> f, Box<M> source)");
    assertGeneratedCodeContains(compilation, generatedClassName, "M1 result = monoid.empty();");
  }

  @Test
  @DisplayName("a wildcard element is folded as the type it stands for")
  void shouldFoldTheTypeAWildcardStandsFor() {
    // A wildcard cannot be written into the generated fold's signature, so the element type named
    // is the one it resolves to: an upper bound where there is one, and Object where the wildcard
    // stands for anything at all, as the traversal over the same component already does.
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Ranked",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFolds;
            import java.util.List;

            @GenerateFolds
            public record Ranked(
                List<? extends CharSequence> bounded,
                List<?> unbounded,
                List<? super String> superBounded) {}
            """);

    var compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    final String generatedClassName = "com.example.RankedFolds";
    assertGeneratedCodeContains(
        compilation, generatedClassName, "public static Fold<Ranked, CharSequence> bounded()");
    assertGeneratedCodeContains(
        compilation, generatedClassName, "public static Fold<Ranked, Object> unbounded()");
    assertGeneratedCodeContains(
        compilation, generatedClassName, "public static Fold<Ranked, Object> superBounded()");
  }

  @Test
  void shouldFailIfAnnotationIsNotOnRecord() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.NotARecord",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFolds;

            @GenerateFolds
            public class NotARecord {
                private String field;
            }
            """);

    var compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("The @GenerateFolds annotation can only be applied to records.");
  }

  @Test
  @DisplayName("a type-variable component bound to Iterable is a plain fold, not a crash")
  void typeVariableComponentBoundToIterableIsNotTreatedAsIterable() {
    // A TypeVariable never passes isIterableType's DeclaredType gate, so getElementType's
    // documented cast invariant holds even for T extends Iterable<String>.
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.GenericHolder",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFolds;

            @GenerateFolds
            public record GenericHolder<T extends Iterable<String>>(T items) {}
            """);

    var compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.GenericHolderFolds").isNotNull();
  }
}
