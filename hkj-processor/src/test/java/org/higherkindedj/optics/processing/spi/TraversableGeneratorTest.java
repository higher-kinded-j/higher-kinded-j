// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.spi;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.*;

import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeVariableName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link TraversableGenerator} default methods. */
@DisplayName("TraversableGenerator default methods")
class TraversableGeneratorTest {

  /** Minimal implementation for testing defaults. */
  private static final TraversableGenerator MINIMAL_IMPL =
      new TraversableGenerator() {
        @Override
        public boolean supports(TypeMirror type) {
          return false;
        }

        @Override
        public CodeBlock generateModifyF(
            RecordComponentElement component,
            ClassName recordClassName,
            List<? extends RecordComponentElement> allComponents) {
          return CodeBlock.builder().build();
        }
      };

  @Test
  @DisplayName("getCardinality should default to ZERO_OR_MORE")
  void getCardinalityShouldDefaultToZeroOrMore() {
    assertEquals(Cardinality.ZERO_OR_MORE, MINIMAL_IMPL.getCardinality());
  }

  @Test
  @DisplayName("generateOpticExpression should default to empty string")
  void generateOpticExpressionShouldDefaultToEmpty() {
    assertEquals("", MINIMAL_IMPL.generateOpticExpression());
  }

  @Test
  @DisplayName("getRequiredImports should default to empty set")
  void getRequiredImportsShouldDefaultToEmpty() {
    assertEquals(Set.of(), MINIMAL_IMPL.getRequiredImports());
  }

  @Test
  @DisplayName("getFocusTypeArgumentIndex should default to 0")
  void getFocusTypeArgumentIndexShouldDefaultToZero() {
    assertEquals(0, MINIMAL_IMPL.getFocusTypeArgumentIndex());
  }

  @Test
  @DisplayName("Cardinality enum should have two values")
  void cardinalityEnumShouldHaveTwoValues() {
    Cardinality[] values = Cardinality.values();
    assertEquals(2, values.length);
    assertEquals(Cardinality.ZERO_OR_ONE, values[0]);
    assertEquals(Cardinality.ZERO_OR_MORE, values[1]);
  }

  @Test
  @DisplayName("resolveEffectiveType should delegate to ProcessorUtils.resolveWildcard")
  void resolveEffectiveTypeShouldDelegateToProcessorUtils() {
    // resolveEffectiveType with null returns null (ProcessorUtils handles null gracefully)
    TypeMirror result = MINIMAL_IMPL.resolveEffectiveType(null);
    assertNull(result);
  }

  @Test
  @DisplayName("priority should default to PRIORITY_DEFAULT")
  void priorityShouldDefaultToPriorityDefault() {
    assertEquals(TraversableGenerator.PRIORITY_DEFAULT, MINIMAL_IMPL.priority());
  }

  @Test
  @DisplayName(
      "effectVariable should name the effect F, unless the record declares an F of its own")
  void effectVariableShouldNameAnEffectTheRecordLeavesFree() {
    final var plain =
        JavaFileObjects.forSourceString(
            "com.example.Plain",
            """
            package com.example;

            import java.util.List;

            public record Plain(List<String> items) {}
            """);
    final var shadowing =
        JavaFileObjects.forSourceString(
            "com.example.Shadowing",
            """
            package com.example;

            import java.util.List;

            public record Shadowing<F>(List<F> items) {}
            """);
    final Map<String, TypeVariableName> effects = new HashMap<>();

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
        for (TypeElement type : ElementFilter.typesIn(round.getRootElements())) {
          for (RecordComponentElement component : type.getRecordComponents()) {
            effects.put(type.getSimpleName().toString(), MINIMAL_IMPL.effectVariable(component));
          }
        }
        return false;
      }
    }

    assertThat(javac().withProcessors(new Reader()).compile(plain, shadowing)).succeeded();
    assertEquals(
        Map.of("Plain", TypeVariableName.get("F"), "Shadowing", TypeVariableName.get("F1")),
        effects);
  }
}
