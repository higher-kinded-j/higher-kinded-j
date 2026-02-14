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
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CopyStrategyCodeGenerator}.
 *
 * <p>These tests verify that the code generator produces correct code blocks for each copy
 * strategy.
 */
@DisplayName("CopyStrategyCodeGenerator")
class CopyStrategyCodeGeneratorTest {

  /**
   * A helper processor that runs the code generator and captures the result. This allows us to test
   * the generator within the annotation processing environment where TypeMirror is available.
   */
  private static class GeneratorTestProcessor extends AbstractProcessor {
    private final String sourceTypeName;
    private final String focusTypeName;
    private final CopyStrategyKind strategy;
    private final CopyStrategyInfo info;
    private final String fieldName;
    private String getterResult;
    private String setterResult;

    GeneratorTestProcessor(
        String sourceTypeName,
        String focusTypeName,
        CopyStrategyKind strategy,
        CopyStrategyInfo info,
        String fieldName) {
      this.sourceTypeName = sourceTypeName;
      this.focusTypeName = focusTypeName;
      this.strategy = strategy;
      this.info = info;
      this.fieldName = fieldName;
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
      TypeElement focusElement = processingEnv.getElementUtils().getTypeElement(focusTypeName);

      if (sourceElement != null && focusElement != null) {
        TypeMirror sourceType = sourceElement.asType();
        TypeMirror focusType = focusElement.asType();

        CopyStrategyCodeGenerator generator = new CopyStrategyCodeGenerator();

        CodeBlock getter = generator.generateGetterLambda(fieldName, info, sourceType);
        getterResult = getter.toString();

        CodeBlock setter =
            generator.generateSetterLambda(strategy, info, fieldName, sourceType, focusType);
        setterResult = setter.toString();
      }

      return false;
    }

    String getGetterResult() {
      return getterResult;
    }

    String getSetterResult() {
      return setterResult;
    }
  }

  private record GeneratedCode(String getter, String setter) {}

  private GeneratedCode generateCode(
      CopyStrategyKind strategy,
      CopyStrategyInfo info,
      String fieldName,
      JavaFileObject... sources) {
    GeneratorTestProcessor processor =
        new GeneratorTestProcessor(
            "com.test.Person", "java.lang.String", strategy, info, fieldName);
    Compilation compilation = javac().withProcessors(processor).compile(sources);
    assertThat(compilation).succeeded();
    return new GeneratedCode(processor.getGetterResult(), processor.getSetterResult());
  }

  private static final JavaFileObject PERSON_SOURCE =
      JavaFileObjects.forSourceString(
          "com.test.Person",
          """
          package com.test;
          public record Person(String name, int age) {
              public static Builder toBuilder() { return new Builder(); }
              public static class Builder {
                  public Builder name(String name) { return this; }
                  public Person build() { return null; }
              }
          }
          """);

  @Nested
  @DisplayName("Getter Lambda Generation")
  class GetterLambdaGeneration {

    @Test
    @DisplayName("should generate record-style getter when getter is empty")
    void shouldGenerateRecordStyleGetter() {
      CopyStrategyInfo info = CopyStrategyInfo.forBuilder("", "toBuilder", "", "build");

      GeneratedCode code = generateCode(CopyStrategyKind.VIA_BUILDER, info, "name", PERSON_SOURCE);

      assertThat(code.getter()).isEqualTo("source -> source.name()");
    }

    @Test
    @DisplayName("should generate explicit getter when specified")
    void shouldGenerateExplicitGetter() {
      CopyStrategyInfo info = CopyStrategyInfo.forBuilder("getName", "toBuilder", "", "build");

      GeneratedCode code = generateCode(CopyStrategyKind.VIA_BUILDER, info, "name", PERSON_SOURCE);

      assertThat(code.getter()).isEqualTo("source -> source.getName()");
    }
  }

  @Nested
  @DisplayName("@ViaBuilder Setter Generation")
  class ViaBuilderSetterGeneration {

    @Test
    @DisplayName("should generate builder setter with defaults")
    void shouldGenerateBuilderSetterDefaults() {
      CopyStrategyInfo info = CopyStrategyInfo.forBuilder("", "toBuilder", "", "build");

      GeneratedCode code = generateCode(CopyStrategyKind.VIA_BUILDER, info, "name", PERSON_SOURCE);

      assertThat(code.setter())
          .isEqualTo("(source, newValue) -> source.toBuilder().name(newValue).build()");
    }

    @Test
    @DisplayName("should generate builder setter with custom methods")
    void shouldGenerateBuilderSetterCustom() {
      CopyStrategyInfo info =
          CopyStrategyInfo.forBuilder("getName", "newBuilder", "withName", "create");

      GeneratedCode code = generateCode(CopyStrategyKind.VIA_BUILDER, info, "name", PERSON_SOURCE);

      assertThat(code.setter())
          .isEqualTo("(source, newValue) -> source.newBuilder().withName(newValue).create()");
    }
  }

  @Nested
  @DisplayName("@Wither Setter Generation")
  class WitherSetterGeneration {

    @Test
    @DisplayName("should generate wither setter")
    void shouldGenerateWitherSetter() {
      CopyStrategyInfo info = CopyStrategyInfo.forWither("getYear", "withYear");

      var localDate =
          JavaFileObjects.forSourceString(
              "com.test.Person", // Using Person for simplicity in test setup
              """
              package com.test;
              public class Person {
                  private String name;
                  public String getName() { return name; }
                  public Person withName(String name) { return this; }
              }
              """);

      GeneratorTestProcessor processor =
          new GeneratorTestProcessor(
              "com.test.Person", "java.lang.String", CopyStrategyKind.WITHER, info, "year");

      Compilation compilation = javac().withProcessors(processor).compile(localDate);
      assertThat(compilation).succeeded();

      assertThat(processor.getSetterResult())
          .isEqualTo("(source, newValue) -> source.withYear(newValue)");
    }
  }

  @Nested
  @DisplayName("@ViaConstructor Setter Generation")
  class ViaConstructorSetterGeneration {

    @Test
    @DisplayName("should generate constructor setter with parameter order")
    void shouldGenerateConstructorSetterWithOrder() {
      CopyStrategyInfo info = CopyStrategyInfo.forConstructor(new String[] {"name", "age"});

      GeneratedCode code =
          generateCode(CopyStrategyKind.VIA_CONSTRUCTOR, info, "name", PERSON_SOURCE);

      assertThat(code.setter())
          .isEqualTo("(source, newValue) -> new com.test.Person(newValue, source.age())");
    }

    @Test
    @DisplayName("should throw error when parameter order not specified")
    void shouldRequireParameterOrder() {
      CopyStrategyInfo info = CopyStrategyInfo.forConstructor(new String[] {});

      GeneratedCode code =
          generateCode(CopyStrategyKind.VIA_CONSTRUCTOR, info, "name", PERSON_SOURCE);

      // Should generate a TODO comment indicating parameterOrder is needed
      assertThat(code.setter()).contains("@ViaConstructor requires parameterOrder");
    }
  }

  @Nested
  @DisplayName("@ViaCopyAndSet Setter Generation")
  class ViaCopyAndSetSetterGeneration {

    @Test
    @DisplayName("should generate copy and set setter")
    void shouldGenerateCopyAndSetSetter() {
      CopyStrategyInfo info = CopyStrategyInfo.forCopyAndSet("", "setName");

      GeneratedCode code =
          generateCode(CopyStrategyKind.VIA_COPY_AND_SET, info, "name", PERSON_SOURCE);

      assertThat(code.setter()).contains("com.test.Person copy = new com.test.Person(source)");
      assertThat(code.setter()).contains("copy.setName(newValue)");
      assertThat(code.setter()).contains("return copy");
    }
  }
}
