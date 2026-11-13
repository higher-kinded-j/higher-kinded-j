// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

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
import org.higherkindedj.optics.processing.generator.hkj.ValidatedGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ValidatedGenerator} names a {@code Validated} component's valid type, its second argument,
 * read from real record components. The base class's own helpers are tested in hkj-processor, in
 * {@code BaseTraversableGeneratorTest}.
 */
@DisplayName("ValidatedGenerator: the valid type, read from real record components")
class TypeArgumentNameTest {

  /** Reaches the generic-type helper {@link ValidatedGenerator} answers for its focus. */
  private static final class ValidatedFocus extends ValidatedGenerator {
    TypeName of(final RecordComponentElement component) {
      return getGenericTypeName(component);
    }
  }

  @Test
  @DisplayName("getGenericTypeName names a Validated component's valid type")
  void validatedGenericTypeIsItsValidType() {
    final var checked =
        JavaFileObjects.forSourceString(
            "com.example.Checked",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.Validated;

            public record Checked(
                Validated<String, Integer> plain, Validated<String, ? extends Number> bounded) {}
            """);
    final ValidatedFocus validated = new ValidatedFocus();

    assertThat(read(checked, validated::of))
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
