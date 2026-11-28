// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/**
 * Tests for the declarative {@code RenameWriterPathListenToTell} recipe, part of {@code
 * MigrateDeprecationsTo0_7_0}, against the real hkj-core and hkj-api.
 */
@DisplayName("RenameWriterPathListenToTell")
class RenameWriterPathListenToTellTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("org.higherkindedj.openrewrite.RenameWriterPathListenToTell")
        .parser(JavaParser.fromJavaVersion().classpath("hkj-core", "hkj-api", "jspecify"));
  }

  @Test
  @DisplayName("WriterPath.listen(log) becomes tell(log), in a chain and in generic code")
  void renamesListenWithALogToTell() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.hkt.Monoids;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.WriterPath;

            class Usage {
                WriterPath<List<String>, Integer> ship(WriterPath<List<String>, Integer> paid) {
                    return paid.listen(List.of("shipped")).map(n -> n + 1).listen(List.of("billed"));
                }

                <W, A> WriterPath<W, A> append(WriterPath<W, A> path, W log) {
                    return path.listen(log);
                }

                List<String> written() {
                    return Path.writerPure(1, Monoids.<String>list()).listen(List.of("one")).written();
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.hkt.Monoids;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.WriterPath;

            class Usage {
                WriterPath<List<String>, Integer> ship(WriterPath<List<String>, Integer> paid) {
                    return paid.tell(List.of("shipped")).map(n -> n + 1).tell(List.of("billed"));
                }

                <W, A> WriterPath<W, A> append(WriterPath<W, A> path, W log) {
                    return path.tell(log);
                }

                List<String> written() {
                    return Path.writerPure(1, Monoids.<String>list()).tell(List.of("one")).written();
                }
            }
            """));
  }

  @Test
  @DisplayName("A method reference to listen(log) becomes one to tell")
  void renamesAMethodReferenceToListen() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import java.util.function.BiFunction;
            import java.util.function.Function;
            import org.higherkindedj.hkt.effect.WriterPath;

            class Usage {
                BiFunction<WriterPath<List<String>, Integer>, List<String>, WriterPath<List<String>, Integer>>
                        appender() {
                    return WriterPath::listen;
                }

                Function<List<String>, WriterPath<List<String>, Integer>> appenderTo(
                        WriterPath<List<String>, Integer> path) {
                    return path::listen;
                }
            }
            """,
            """
            package com.example;

            import java.util.List;
            import java.util.function.BiFunction;
            import java.util.function.Function;
            import org.higherkindedj.hkt.effect.WriterPath;

            class Usage {
                BiFunction<WriterPath<List<String>, Integer>, List<String>, WriterPath<List<String>, Integer>>
                        appender() {
                    return WriterPath::tell;
                }

                Function<List<String>, WriterPath<List<String>, Integer>> appenderTo(
                        WriterPath<List<String>, Integer> path) {
                    return path::tell;
                }
            }
            """));
  }

  @Test
  @DisplayName("listen() with no argument, and another type's listen(log), are left alone")
  void leavesListenWithNoArgumentAndAnotherTypesListenAlone() {
    rewriteRun(
        java(
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.hkt.Pair;
            import org.higherkindedj.hkt.effect.WriterPath;

            class Recorder {
                Recorder listen(List<String> log) {
                    return this;
                }

                WriterPath<List<String>, Pair<Integer, List<String>>> read(
                        WriterPath<List<String>, Integer> path) {
                    listen(List.of("heard"));
                    return path.listen();
                }
            }
            """));
  }
}
