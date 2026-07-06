// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for the package-private helpers of {@link ExternalLensGenerator} and {@link
 * SpecInterfaceGenerator}.
 *
 * <p>These exercise defensive branches that are unreachable through record fixtures because {@code
 * TypeKindAnalyser.detectContainerType} only flags container fields that always have a matching SPI
 * generator with an in-range focus index. A test-controlled generator list makes the guard arms
 * reachable.
 */
@DisplayName("External generators package-private helpers")
class ExternalGeneratorsUnitTest {

  /** A stub generator that supports every type, with a configurable focus type argument index. */
  private static final class StubGenerator implements TraversableGenerator {
    private final int focusIndex;

    StubGenerator(int focusIndex) {
      this.focusIndex = focusIndex;
    }

    @Override
    public boolean supports(TypeMirror type) {
      return true;
    }

    @Override
    public int getFocusTypeArgumentIndex() {
      return focusIndex;
    }

    @Override
    public CodeBlock generateModifyF(
        RecordComponentElement component,
        ClassName recordClassName,
        List<? extends RecordComponentElement> allComponents) {
      return CodeBlock.of("");
    }
  }

  /**
   * Runs the unit-level calls inside a real processing environment so that genuine {@code
   * TypeMirror}s and {@code RecordComponentElement}s are available.
   */
  private static class GeneratorUnitTestProcessor extends AbstractProcessor {
    private boolean invoked = false;

    private MethodSpec traversalForUnknownField;
    private MethodSpec traversalForRawContainerField;
    private TypeName focusTypeForRawContainer;
    private TypeName focusTypeForOutOfRangeIndex;
    private TypeName focusTypeForPrimitive;
    private TypeName parameterisedNameForPrimitive;

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
      if (roundEnv.processingOver() || invoked) {
        return false;
      }

      TypeElement basket = processingEnv.getElementUtils().getTypeElement("com.test.Basket");
      TypeElement mixed = processingEnv.getElementUtils().getTypeElement("com.test.Mixed");
      if (basket == null || mixed == null) {
        return false;
      }
      invoked = true;

      // 1. No generator available at all: the generator-search loop exits without a match and
      // createTraversalMethod returns null, so generateForRecord skips the traversal method.
      ExternalLensGenerator withoutGenerators =
          new ExternalLensGenerator(
              processingEnv.getFiler(), processingEnv.getMessager(), List.of());
      TypeAnalysis basketAnalysis =
          new TypeKindAnalyser(processingEnv.getTypeUtils()).analyseType(basket);
      withoutGenerators.generateForRecord(basketAnalysis, "com.test.gen", basket);

      ExternalLensGenerator withStub =
          new ExternalLensGenerator(
              processingEnv.getFiler(), processingEnv.getMessager(), List.of(new StubGenerator(0)));

      List<? extends RecordComponentElement> components = mixed.getRecordComponents();
      TypeMirror itemsType = components.get(0).asType(); // List<String>
      TypeMirror rawItemsType = components.get(1).asType(); // raw List
      TypeName mixedTypeName = ClassName.get(mixed);

      // 2. Field name matching no record component: the component loop exits without a match.
      traversalForUnknownField =
          withStub.createTraversalMethod(
              FieldInfo.forRecordComponent("phantom", itemsType), mixed, components, mixedTypeName);

      // 3. Raw container field: getFocusType returns null, so the method is skipped.
      traversalForRawContainerField =
          withStub.createTraversalMethod(
              FieldInfo.forRecordComponent("rawItems", rawItemsType),
              mixed,
              components,
              mixedTypeName);

      // 4. getFocusType guard arms.
      focusTypeForRawContainer = withStub.getFocusType(rawItemsType, new StubGenerator(0));
      focusTypeForOutOfRangeIndex = withStub.getFocusType(itemsType, new StubGenerator(1));
      TypeMirror primitiveInt = processingEnv.getTypeUtils().getPrimitiveType(TypeKind.INT);
      focusTypeForPrimitive = withStub.getFocusType(primitiveInt, new StubGenerator(0));

      // 5. SpecInterfaceGenerator.getParameterisedTypeName with a non-declared type.
      SpecInterfaceGenerator specGenerator =
          new SpecInterfaceGenerator(processingEnv.getFiler(), processingEnv.getMessager());
      parameterisedNameForPrimitive = specGenerator.getParameterisedTypeName(primitiveInt);

      return false;
    }
  }

  @Test
  @DisplayName("should take the defensive null arms when generators or components do not match")
  void shouldTakeDefensiveNullArms() {
    var basket =
        JavaFileObjects.forSourceString(
            "com.test.Basket",
            """
            package com.test;
            import java.util.List;
            public record Basket(List<String> items) {}
            """);

    var mixed =
        JavaFileObjects.forSourceString(
            "com.test.Mixed",
            """
            package com.test;
            import java.util.List;
            @SuppressWarnings("rawtypes")
            public record Mixed(List<String> items, List rawItems) {}
            """);

    GeneratorUnitTestProcessor processor = new GeneratorUnitTestProcessor();
    Compilation compilation = javac().withProcessors(processor).compile(basket, mixed);

    assertThat(compilation).succeeded();
    assertThat(processor.invoked).isTrue();
    assertThat(processor.traversalForUnknownField).isNull();
    assertThat(processor.traversalForRawContainerField).isNull();
    assertThat(processor.focusTypeForRawContainer).isNull();
    assertThat(processor.focusTypeForOutOfRangeIndex).isNull();
    assertThat(processor.focusTypeForPrimitive).isNull();
    assertThat(processor.parameterisedNameForPrimitive).isEqualTo(TypeName.INT);
  }
}
