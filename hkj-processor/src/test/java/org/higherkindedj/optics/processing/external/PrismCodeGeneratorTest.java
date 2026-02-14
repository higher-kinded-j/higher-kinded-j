// Copyright (c) 2025 - 2026 Magnus Smith
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
import org.higherkindedj.optics.processing.external.SpecAnalysis.PrismHintInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.PrismHintKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PrismCodeGenerator}.
 *
 * <p>These tests verify that the code generator produces correct code blocks for prism generation.
 */
@DisplayName("PrismCodeGenerator")
class PrismCodeGeneratorTest {

  /**
   * A helper processor that runs the prism code generator and captures the result. This allows us
   * to test the generator within the annotation processing environment where TypeMirror is
   * available.
   */
  private static class GeneratorTestProcessor extends AbstractProcessor {
    private final String sourceTypeName;
    private final String targetTypeName;
    private final PrismHintKind hintKind;
    private final PrismHintInfo hintInfo;
    private String prismResult;
    private TypeMirror capturedTargetType;

    GeneratorTestProcessor(
        String sourceTypeName,
        String targetTypeName,
        PrismHintKind hintKind,
        PrismHintInfo hintInfo) {
      this.sourceTypeName = sourceTypeName;
      this.targetTypeName = targetTypeName;
      this.hintKind = hintKind;
      this.hintInfo = hintInfo;
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
      TypeElement targetElement = processingEnv.getElementUtils().getTypeElement(targetTypeName);

      if (sourceElement != null && targetElement != null) {
        TypeMirror sourceType = sourceElement.asType();
        TypeMirror targetType = targetElement.asType();
        capturedTargetType = targetType;

        PrismCodeGenerator generator = new PrismCodeGenerator();

        // Create hint info with actual type mirror for @InstanceOf
        PrismHintInfo effectiveHintInfo = hintInfo;
        if (hintKind == PrismHintKind.INSTANCE_OF) {
          effectiveHintInfo = PrismHintInfo.forInstanceOf(targetType);
        }

        CodeBlock prism =
            generator.generatePrismReturnStatement(
                hintKind, effectiveHintInfo, sourceType, targetType);
        prismResult = prism.toString();
      }

      return false;
    }

    String getPrismResult() {
      return prismResult;
    }
  }

  private String generatePrism(
      String sourceTypeName,
      String targetTypeName,
      PrismHintKind hintKind,
      PrismHintInfo hintInfo,
      JavaFileObject... sources) {
    GeneratorTestProcessor processor =
        new GeneratorTestProcessor(sourceTypeName, targetTypeName, hintKind, hintInfo);
    Compilation compilation = javac().withProcessors(processor).compile(sources);
    assertThat(compilation).succeeded();
    return processor.getPrismResult();
  }

  @Nested
  @DisplayName("@InstanceOf Prism Generation")
  class InstanceOfPrismGeneration {

    @Test
    @DisplayName("should generate instanceof prism for sealed type hierarchy")
    void shouldGenerateInstanceOfPrism() {
      var paymentMethod =
          JavaFileObjects.forSourceString(
              "com.test.PaymentMethod",
              """
              package com.test;
              public sealed interface PaymentMethod permits CreditCard, BankTransfer {}
              """);

      var creditCard =
          JavaFileObjects.forSourceString(
              "com.test.CreditCard",
              """
              package com.test;
              public record CreditCard(String number, String cvv) implements PaymentMethod {}
              """);

      var bankTransfer =
          JavaFileObjects.forSourceString(
              "com.test.BankTransfer",
              """
              package com.test;
              public record BankTransfer(String iban) implements PaymentMethod {}
              """);

      String result =
          generatePrism(
              "com.test.PaymentMethod",
              "com.test.CreditCard",
              PrismHintKind.INSTANCE_OF,
              PrismHintInfo.empty(),
              paymentMethod,
              creditCard,
              bankTransfer);

      assertThat(result).contains("Prism.of(");
      assertThat(result).contains("source instanceof com.test.CreditCard");
      assertThat(result).contains("Optional.of");
      assertThat(result).contains("Optional.empty()");
    }

    @Test
    @DisplayName("should generate correct getter and reverseGet for instanceof prism")
    void shouldGenerateCorrectGetterAndReverseGet() {
      var shape =
          JavaFileObjects.forSourceString(
              "com.test.Shape",
              """
              package com.test;
              public abstract class Shape {}
              """);

      var circle =
          JavaFileObjects.forSourceString(
              "com.test.Circle",
              """
              package com.test;
              public class Circle extends Shape {
                  private double radius;
                  public Circle(double radius) { this.radius = radius; }
                  public double getRadius() { return radius; }
              }
              """);

      String result =
          generatePrism(
              "com.test.Shape",
              "com.test.Circle",
              PrismHintKind.INSTANCE_OF,
              PrismHintInfo.empty(),
              shape,
              circle);

      // Check that the pattern match extracts the subtype
      assertThat(result).contains("instanceof com.test.Circle");
      // Check that reverseGet just returns the subtype
      assertThat(result).contains("subtype -> subtype");
    }
  }

  @Nested
  @DisplayName("@MatchWhen Prism Generation")
  class MatchWhenPrismGeneration {

    @Test
    @DisplayName("should generate predicate-based prism")
    void shouldGenerateMatchWhenPrism() {
      var jsonNode =
          JavaFileObjects.forSourceString(
              "com.test.JsonNode",
              """
              package com.test;
              public abstract class JsonNode {
                  public abstract boolean isArray();
                  public JsonArrayNode asArray() { return null; }
              }
              """);

      var jsonArrayNode =
          JavaFileObjects.forSourceString(
              "com.test.JsonArrayNode",
              """
              package com.test;
              public class JsonArrayNode extends JsonNode {
                  @Override public boolean isArray() { return true; }
                  @Override public JsonArrayNode asArray() { return this; }
              }
              """);

      PrismHintInfo info = PrismHintInfo.forMatchWhen("isArray", "asArray");

      String result =
          generatePrism(
              "com.test.JsonNode",
              "com.test.JsonArrayNode",
              PrismHintKind.MATCH_WHEN,
              info,
              jsonNode,
              jsonArrayNode);

      assertThat(result).contains("Prism.of(");
      assertThat(result).contains("source.isArray()");
      assertThat(result).contains("source.asArray()");
      assertThat(result).contains("Optional.of");
      assertThat(result).contains("Optional.empty()");
    }

    @Test
    @DisplayName("should use predicate and getter methods from annotation")
    void shouldUsePredicateAndGetter() {
      var optional =
          JavaFileObjects.forSourceString(
              "com.test.MyOptional",
              """
              package com.test;
              public class MyOptional<T> {
                  private T value;
                  public boolean isPresent() { return value != null; }
                  public T getValue() { return value; }
              }
              """);

      var string =
          JavaFileObjects.forSourceString(
              "java.lang.String",
              """
              package java.lang;
              public final class String {}
              """);

      PrismHintInfo info = PrismHintInfo.forMatchWhen("isPresent", "getValue");

      String result =
          generatePrism(
              "com.test.MyOptional", "java.lang.String", PrismHintKind.MATCH_WHEN, info, optional);

      assertThat(result).contains("source.isPresent()");
      assertThat(result).contains("source.getValue()");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("should handle nested classes")
    void shouldHandleNestedClasses() {
      var outer =
          JavaFileObjects.forSourceString(
              "com.test.Outer",
              """
              package com.test;
              public class Outer {
                  public sealed interface Event permits Event.Created, Event.Deleted {
                      record Created(String id) implements Event {}
                      record Deleted(String id) implements Event {}
                  }
              }
              """);

      String result =
          generatePrism(
              "com.test.Outer.Event",
              "com.test.Outer.Event.Created",
              PrismHintKind.INSTANCE_OF,
              PrismHintInfo.empty(),
              outer);

      assertThat(result).contains("instanceof com.test.Outer.Event.Created");
    }
  }
}
