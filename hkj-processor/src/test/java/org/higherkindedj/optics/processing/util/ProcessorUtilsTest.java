// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.JavaFileObjects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProcessorUtils - shared processor string helpers")
class ProcessorUtilsTest {

  @Test
  @DisplayName("capitalise upper-cases the first character and leaves the rest alone")
  void capitaliseUpperCasesFirstCharacter() {
    assertThat(ProcessorUtils.capitalise("name")).isEqualTo("Name");
    assertThat(ProcessorUtils.capitalise("alreadyUpper")).isEqualTo("AlreadyUpper");
    assertThat(ProcessorUtils.capitalise("x")).isEqualTo("X");
  }

  @Test
  @DisplayName("capitalise passes null and empty inputs through unchanged")
  void capitalisePassesDegenerateInputsThrough() {
    assertThat(ProcessorUtils.capitalise(null)).isNull();
    assertThat(ProcessorUtils.capitalise("")).isEmpty();
  }

  /**
   * Contract tests for {@link ProcessorUtils#mentions}, driven through a real compilation so the
   * type mirrors are javac's own. The subject declares one method per shape; each method's single
   * parameter type is the type to search, and {@code T} the parameter to look for.
   */
  @Nested
  @DisplayName("mentions")
  class Mentions {

    /** Captures each probe method's parameter type, keyed by method name. */
    private static final class CapturingProcessor extends AbstractProcessor {
      private final Map<String, Boolean> mentionsT = new LinkedHashMap<>();
      private final Map<String, Boolean> parameterised = new LinkedHashMap<>();

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        TypeElement subject = processingEnv.getElementUtils().getTypeElement("com.test.Subject");
        if (subject == null) {
          return false;
        }
        TypeParameterElement t = subject.getTypeParameters().getFirst();
        for (ExecutableElement method : ElementFilter.methodsIn(subject.getEnclosedElements())) {
          TypeMirror searched = method.getParameters().getFirst().asType();
          mentionsT.put(method.getSimpleName().toString(), ProcessorUtils.mentions(searched, t));
          parameterised.put(
              method.getSimpleName().toString(), ProcessorUtils.hasTypeArguments(searched));
        }
        return false;
      }
    }

    /** Compiles the subject once and hands back the processor holding both answer maps. */
    private CapturingProcessor probe() {
      var outer =
          JavaFileObjects.forSourceString(
              "com.test.Outer",
              """
              package com.test;
              public class Outer<X> {
                  public class Inner {}
              }
              """);
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Subject",
              """
              package com.test;
              import java.util.List;
              @SuppressWarnings("rawtypes")
              public interface Subject<T, U> {
                  void variable(T p);
                  void otherVariable(U p);
                  void argument(List<T> p);
                  void nestedArgument(List<List<T>> p);
                  void arrayComponent(T[] p);
                  void extendsWildcard(List<? extends T> p);
                  void superWildcard(List<? super T> p);
                  void unboundedWildcard(List<?> p);
                  void enclosingType(Outer<T>.Inner p);
                  void unrelated(String p);
                  void primitive(int p);
                  void rawDeclared(List p);
              }
              """);
      var processor = new CapturingProcessor();
      javac().withProcessors(processor).compile(outer, subject);
      return processor;
    }

    @Test
    @DisplayName("hasTypeArguments holds only for a parameterised declared type")
    void hasTypeArgumentsHoldsOnlyForAParameterisedDeclaredType() {
      assertThat(probe().parameterised)
          .containsEntry("argument", true)
          .containsEntry("nestedArgument", true)
          .containsEntry("enclosingType", false)
          .containsEntry("rawDeclared", false)
          .containsEntry("unrelated", false)
          .containsEntry("primitive", false)
          .containsEntry("arrayComponent", false)
          .containsEntry("variable", false);
    }

    @Test
    @DisplayName("finds the parameter wherever it is written")
    void findsTheParameterWhereverItIsWritten() {
      assertThat(probe().mentionsT)
          .containsEntry("variable", true)
          .containsEntry("argument", true)
          .containsEntry("nestedArgument", true)
          .containsEntry("arrayComponent", true)
          .containsEntry("extendsWildcard", true)
          .containsEntry("superWildcard", true)
          .containsEntry("enclosingType", true);
    }

    @Test
    @DisplayName("answers false for a type that does not name it")
    void answersFalseForATypeThatDoesNotNameIt() {
      assertThat(probe().mentionsT)
          .containsEntry("otherVariable", false)
          .containsEntry("unboundedWildcard", false)
          .containsEntry("unrelated", false)
          .containsEntry("primitive", false);
    }

    @Test
    @DisplayName("searches every arm of an intersection bound")
    void searchesEveryArmOfAnIntersectionBound() {
      // An intersection reaches mentions through a variable's upper bound. javac's intersection
      // implements DeclaredType, so the wrong arm order would answer false here.
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Subject",
              """
              package com.test;
              import java.util.List;
              public interface Subject<T, V extends Runnable & List<T>> {}
              """);

      var processor =
          new AbstractProcessor() {
            Boolean bounded;
            Boolean unbounded;

            @Override
            public Set<String> getSupportedAnnotationTypes() {
              return Set.of("*");
            }

            @Override
            public SourceVersion getSupportedSourceVersion() {
              return SourceVersion.latestSupported();
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
              TypeElement type = processingEnv.getElementUtils().getTypeElement("com.test.Subject");
              if (type == null) {
                return false;
              }
              Element t = type.getTypeParameters().getFirst();
              Element u = type.getTypeParameters().getLast();
              TypeMirror intersection =
                  ((javax.lang.model.type.TypeVariable) u.asType()).getUpperBound();
              bounded = ProcessorUtils.mentions(intersection, t);
              unbounded = ProcessorUtils.mentions(intersection, u);
              return false;
            }
          };
      javac().withProcessors(processor).compile(subject);

      assertThat(processor.bounded).isTrue();
      assertThat(processor.unbounded).isFalse();
    }
  }
}
