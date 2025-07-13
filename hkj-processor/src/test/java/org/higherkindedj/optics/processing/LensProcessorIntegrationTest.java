// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

public class LensProcessorIntegrationTest {

  @Test
  void shouldGenerateLensesForRecord() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.User",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateLenses;
            import org.higherkindedj.optics.Lens;

            @GenerateLenses
            public record User(String name, int age) {}
            """);

    final String expectedNameLens =
        """
        public static Lens<User, String> name() {
            return Lens.of(User::name, (source, newValue) -> new User(newValue, source.age()));
        }
        """;

    final String expectedAgeLens =
        """
        public static Lens<User, Integer> age() {
            return Lens.of(User::age, (source, newValue) -> new User(source.name(), newValue));
        }
        """;

    var compilation = javac().withProcessors(new LensProcessor()).compile(sourceFile);

    assertThat(compilation).succeeded();

    final String generatedClassName = "com.example.UserLenses";
    assertGeneratedCodeContains(compilation, generatedClassName, expectedNameLens);
    assertGeneratedCodeContains(compilation, generatedClassName, expectedAgeLens);
  }
}
