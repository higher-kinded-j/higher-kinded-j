// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
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
        public static Fold<User, String> name() {
            return new Fold<>() {
              @Override
              public <M> M foldMap(Monoid<M> monoid, Function<? super String, ? extends M> f, User source) {
                return f.apply(source.name());
              }
            };
        }
        """;

    final String expectedAgeFold =
        """
        public static Fold<User, Integer> age() {
            return new Fold<>() {
              @Override
              public <M> M foldMap(Monoid<M> monoid, Function<? super Integer, ? extends M> f, User source) {
                return f.apply(source.age());
              }
            };
        }
        """;

    var compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.UserFolds";
    assertGeneratedCodeContains(compilation, generatedClassName, expectedNameFold);
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
        public static Fold<Order, String> id() {
            return new Fold<>() {
              @Override
              public <M> M foldMap(Monoid<M> monoid, Function<? super String, ? extends M> f, Order source) {
                return f.apply(source.id());
              }
            };
        }
        """;

    final String expectedItemsFold =
        """
        public static Fold<Order, String> items() {
            return new Fold<>() {
              @Override
              public <M> M foldMap(Monoid<M> monoid, Function<? super String, ? extends M> f, Order source) {
                M result = monoid.empty();
                for (var element : source.items()) {
                  result = monoid.combine(result, f.apply(element));
                }
                return result;
              }
            };
        }
        """;

    var compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.OrderFolds";
    assertGeneratedCodeContains(compilation, generatedClassName, expectedIdFold);
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
        public static <T> Fold<Container<T>, T> value() {
            return new Fold<>() {
              @Override
              public <M> M foldMap(Monoid<M> monoid, Function<? super T, ? extends M> f, Container<T> source) {
                return f.apply(source.value());
              }
            };
        }
        """;

    final String expectedLabelFold =
        """
        public static <T> Fold<Container<T>, String> label() {
            return new Fold<>() {
              @Override
              public <M> M foldMap(Monoid<M> monoid, Function<? super String, ? extends M> f, Container<T> source) {
                return f.apply(source.label());
              }
            };
        }
        """;

    var compilation = javac().withProcessors(new FoldProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.ContainerFolds";
    assertGeneratedCodeContains(compilation, generatedClassName, expectedValueFold);
    assertGeneratedCodeContains(compilation, generatedClassName, expectedLabelFold);
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
}
