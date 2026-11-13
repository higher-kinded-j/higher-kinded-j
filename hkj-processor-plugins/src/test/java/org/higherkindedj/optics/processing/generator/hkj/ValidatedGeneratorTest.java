// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.hkj;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.generator.GeneratorTestHelper.traversalsJavac;

import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedGenerator")
public class ValidatedGeneratorTest {
  @Test
  @DisplayName("should generate a traversal for Validated fields that draws no lint warning")
  void shouldGenerateCorrectTraversalForValidated() {
    final var sourceFile =
        JavaFileObjects.forSourceString(
            "com.example.Input",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.hkt.Kind;

            @GenerateTraversals
            public record Input(Validated<String, String> value) {}
            """);

    final String expectedBody =
        """
        final Validated<String, String> validated = source.value();
        if (validated.isValid()) {
          final var g_of_b = f.apply(validated.get());
          return applicative.map(newValue -> new Input(Validated.valid(newValue)), g_of_b);
        } else {
          return applicative.of(source);
        }
        """;

    var compilation = traversalsJavac().withOptions("-Xlint:all", "-Werror").compile(sourceFile);

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(compilation, "com.example.InputTraversals", expectedBody);
  }

  @Test
  @DisplayName("getGenericTypeName names a Validated component's valid type, its second argument")
  void genericTypeIsTheValidType() {
    final var checked =
        JavaFileObjects.forSourceString(
            "com.example.Checked",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.Validated;

            public record Checked(
                Validated<String, Integer> plain, Validated<String, ? extends Number> bounded) {}
            """);
    final ValidatedGenerator validated = new ValidatedGenerator();

    assertThat(read(checked, validated::getGenericTypeName))
        .containsOnly(
            entry("plain", ClassName.get(Integer.class)),
            entry("bounded", ClassName.get(Number.class)));
  }

  /** Compiles {@code source} and reads every record component in it through {@code reader}. */
  private static Map<String, TypeName> read(
      final JavaFileObject source, final Function<RecordComponentElement, TypeName> reader) {
    final Map<String, TypeName> names = new HashMap<>();

    final class Reader extends AbstractProcessor {
      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(
          final Set<? extends TypeElement> annotations, final RoundEnvironment round) {
        ElementFilter.typesIn(round.getRootElements()).stream()
            .flatMap(type -> type.getRecordComponents().stream())
            .forEach(
                component ->
                    names.put(component.getSimpleName().toString(), reader.apply(component)));
        return false;
      }
    }

    assertThat(javac().withProcessors(new Reader()).compile(source)).succeeded();
    return Map.copyOf(names);
  }
}
