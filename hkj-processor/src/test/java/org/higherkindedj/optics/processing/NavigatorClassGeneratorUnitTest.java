// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for {@link NavigatorClassGenerator} internals that are unreachable through
 * compile-testing fixtures.
 *
 * <p>{@code navigableTypeElement}'s annotation fallback is dead in production because the
 * FocusProcessor pre-populates {@code navigableTypes} with every {@code @GenerateFocus} record, so
 * an empty set exercises it.
 */
@DisplayName("NavigatorClassGenerator internals")
class NavigatorClassGeneratorUnitTest {

  @Test
  @DisplayName("should recognise @GenerateFocus types via the annotation fallback")
  void shouldRecogniseGenerateFocusViaAnnotationFallback() {
    final var annotated =
        JavaFileObjects.forSourceString(
            "com.test.Annotated",
            """
            package com.test;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus
            public record Annotated(String value) {}
            """);

    final class NavigabilityProbeProcessor extends AbstractProcessor {
      private Boolean navigable;

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_25;
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver() || navigable != null) {
          return false;
        }

        TypeElement typeElement =
            processingEnv.getElementUtils().getTypeElement("com.test.Annotated");
        if (typeElement != null) {
          NavigatorClassGenerator generator =
              new NavigatorClassGenerator(
                  processingEnv, Set.of(), 1, new WideningAnalysis(processingEnv, List.of()));
          navigable = generator.navigableTypeElement(typeElement.asType()) != null;
        }

        return false;
      }
    }

    NavigabilityProbeProcessor probe = new NavigabilityProbeProcessor();
    Compilation compilation = javac().withProcessors(probe).compile(annotated);

    assertThat(compilation).succeeded();
    assertThat(probe.navigable).isTrue();
  }
}
