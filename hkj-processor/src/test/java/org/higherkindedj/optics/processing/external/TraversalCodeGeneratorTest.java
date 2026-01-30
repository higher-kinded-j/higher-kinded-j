// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.CodeBlock;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.external.SpecAnalysis.TraversalHintInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.TraversalHintKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TraversalCodeGenerator}.
 *
 * <p>These tests verify that the code generator produces correct code blocks for traversal
 * generation.
 */
@DisplayName("TraversalCodeGenerator")
class TraversalCodeGeneratorTest {

  /**
   * A helper processor that runs the traversal code generator and captures the result. This allows
   * us to test the generator within the annotation processing environment where TypeMirror is
   * available.
   */
  private static class GeneratorTestProcessor extends AbstractProcessor {
    private final String sourceTypeName;
    private final String elementTypeName;
    private final TraversalHintKind hintKind;
    private final TraversalHintInfo hintInfo;
    private final String specClassName;
    private String traversalResult;

    GeneratorTestProcessor(
        String sourceTypeName,
        String elementTypeName,
        TraversalHintKind hintKind,
        TraversalHintInfo hintInfo,
        String specClassName) {
      this.sourceTypeName = sourceTypeName;
      this.elementTypeName = elementTypeName;
      this.hintKind = hintKind;
      this.hintInfo = hintInfo;
      this.specClassName = specClassName;
    }

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
      if (roundEnv.processingOver()) {
        return false;
      }

      TypeElement sourceElement = processingEnv.getElementUtils().getTypeElement(sourceTypeName);
      TypeElement elementElement = processingEnv.getElementUtils().getTypeElement(elementTypeName);

      if (sourceElement != null && elementElement != null) {
        TypeMirror sourceType = sourceElement.asType();
        TypeMirror elementType = elementElement.asType();

        TraversalCodeGenerator generator = new TraversalCodeGenerator();

        CodeBlock traversal =
            generator.generateTraversalReturnStatement(
                hintKind, hintInfo, sourceType, elementType, specClassName);
        traversalResult = traversal.toString();
      }

      return false;
    }

    String getTraversalResult() {
      return traversalResult;
    }
  }

  private String generateTraversal(
      String sourceTypeName,
      String elementTypeName,
      TraversalHintKind hintKind,
      TraversalHintInfo hintInfo,
      JavaFileObject... sources) {
    // Use a default spec class name for tests
    return generateTraversal(
        sourceTypeName, elementTypeName, hintKind, hintInfo, "com.test.TestSpec", sources);
  }

  private String generateTraversal(
      String sourceTypeName,
      String elementTypeName,
      TraversalHintKind hintKind,
      TraversalHintInfo hintInfo,
      String specClassName,
      JavaFileObject... sources) {
    GeneratorTestProcessor processor =
        new GeneratorTestProcessor(
            sourceTypeName, elementTypeName, hintKind, hintInfo, specClassName);
    Compilation compilation = javac().withProcessors(processor).compile(sources);
    assertThat(compilation).succeeded();
    return processor.getTraversalResult();
  }

  @Nested
  @DisplayName("@TraverseWith Generation")
  class TraverseWithGeneration {

    @Test
    @DisplayName("should generate traversal reference for explicit reference")
    void shouldGenerateTraversalReference() {
      var team =
          JavaFileObjects.forSourceString(
              "com.test.Team",
              """
              package com.test;
              import java.util.List;
              public record Team(String name, List<String> members) {}
              """);

      TraversalHintInfo info =
          TraversalHintInfo.forTraverseWith("org.higherkindedj.optics.Traversals.list()");

      String result =
          generateTraversal(
              "com.test.Team", "java.lang.String", TraversalHintKind.TRAVERSE_WITH, info, team);

      assertThat(result).contains("return org.higherkindedj.optics.Traversals.list()");
    }

    @Test
    @DisplayName("should handle static field references")
    void shouldHandleStaticFieldReferences() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Container",
              """
              package com.test;
              import java.util.Set;
              public record Container(Set<String> items) {}
              """);

      TraversalHintInfo info =
          TraversalHintInfo.forTraverseWith("com.custom.CustomTraversals.SET_TRAVERSAL");

      String result =
          generateTraversal(
              "com.test.Container",
              "java.lang.String",
              TraversalHintKind.TRAVERSE_WITH,
              info,
              source);

      assertThat(result).contains("return com.custom.CustomTraversals.SET_TRAVERSAL");
    }

    @Test
    @DisplayName("should handle parameterised factory methods")
    void shouldHandleParameterisedFactoryMethods() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Tree",
              """
              package com.test;
              public class Tree<T> {
                  private T value;
                  private java.util.List<Tree<T>> children;
              }
              """);

      TraversalHintInfo info =
          TraversalHintInfo.forTraverseWith("com.custom.TreeTraversals.preOrder()");

      String result =
          generateTraversal(
              "com.test.Tree", "java.lang.Object", TraversalHintKind.TRAVERSE_WITH, info, source);

      assertThat(result).contains("return com.custom.TreeTraversals.preOrder()");
    }
  }

  @Nested
  @DisplayName("@ThroughField Generation")
  class ThroughFieldGeneration {

    @Test
    @DisplayName("should generate traversal through list field")
    void shouldGenerateTraversalThroughListField() {
      var team =
          JavaFileObjects.forSourceString(
              "com.test.Team",
              """
              package com.test;
              import java.util.List;
              public record Team(String name, List<String> members) {}
              """);

      TraversalHintInfo info =
          TraversalHintInfo.forThroughField(
              "members", "org.higherkindedj.optics.util.Traversals.forList()");

      String result =
          generateTraversal(
              "com.test.Team", "java.lang.String", TraversalHintKind.THROUGH_FIELD, info, team);

      // Should compose a lens to the field with a list traversal
      assertThat(result).contains("members");
      assertThat(result).contains("org.higherkindedj.optics.util.Traversals.forList()");
    }

    @Test
    @DisplayName("should use explicit traversal when specified")
    void shouldUseExplicitTraversal() {
      var team =
          JavaFileObjects.forSourceString(
              "com.test.Department",
              """
              package com.test;
              import java.util.Set;
              public record Department(String name, Set<String> employees) {}
              """);

      TraversalHintInfo info =
          TraversalHintInfo.forThroughField(
              "employees", "org.higherkindedj.optics.Traversals.set()");

      String result =
          generateTraversal(
              "com.test.Department",
              "java.lang.String",
              TraversalHintKind.THROUGH_FIELD,
              info,
              team);

      assertThat(result).contains("employees");
      assertThat(result).contains("org.higherkindedj.optics.Traversals.set()");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("should handle fully qualified class names in reference")
    void shouldHandleFullyQualifiedNames() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Data",
              """
              package com.test;
              import java.util.List;
              public record Data(List<Integer> numbers) {}
              """);

      TraversalHintInfo info =
          TraversalHintInfo.forTraverseWith(
              "org.higherkindedj.optics.instances.ListTraversal.INSTANCE");

      String result =
          generateTraversal(
              "com.test.Data", "java.lang.Integer", TraversalHintKind.TRAVERSE_WITH, info, source);

      assertThat(result)
          .contains("return org.higherkindedj.optics.instances.ListTraversal.INSTANCE");
    }

    @Test
    @DisplayName("should handle complex nested type traversals")
    void shouldHandleNestedTypes() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Outer",
              """
              package com.test;
              import java.util.List;
              public class Outer {
                  public record Inner(List<String> items) {}
              }
              """);

      TraversalHintInfo info =
          TraversalHintInfo.forTraverseWith("org.higherkindedj.optics.Traversals.list()");

      String result =
          generateTraversal(
              "com.test.Outer.Inner",
              "java.lang.String",
              TraversalHintKind.TRAVERSE_WITH,
              info,
              source);

      assertThat(result).contains("return org.higherkindedj.optics.Traversals.list()");
    }
  }
}
