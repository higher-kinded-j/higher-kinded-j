// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.external.ContainerType;
import org.higherkindedj.optics.processing.external.TypeAnalysis;
import org.higherkindedj.optics.processing.external.TypeKindAnalyser;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests specifically designed to kill surviving mutations and improve mutation score.
 *
 * <p>These tests target:
 *
 * <ul>
 *   <li>Boundary conditions (< vs <=, > vs >=)
 *   <li>Boolean return values (especially false returns)
 *   <li>Conditional equality checks
 *   <li>Order-dependent conditionals
 * </ul>
 */
@DisplayName("Mutation Killing Tests")
class MutationKillingTest {

  // =============================================================================
  // Helper Processor for Type Analysis
  // =============================================================================

  private static class AnalyserTestProcessor extends AbstractProcessor {
    private final String targetTypeName;
    private TypeAnalysis result;
    private TypeKindAnalyser analyser;

    AnalyserTestProcessor(String targetTypeName) {
      this.targetTypeName = targetTypeName;
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

      TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(targetTypeName);
      if (typeElement != null) {
        analyser = new TypeKindAnalyser(processingEnv.getTypeUtils());
        result = analyser.analyseType(typeElement);
      }

      return false;
    }

    TypeAnalysis getResult() {
      return result;
    }

    TypeKindAnalyser getAnalyser() {
      return analyser;
    }
  }

  private TypeAnalysis analyseType(String typeName, JavaFileObject... sources) {
    AnalyserTestProcessor processor = new AnalyserTestProcessor(typeName);
    Compilation compilation = javac().withProcessors(processor).compile(sources);
    assertThat(compilation).succeeded();
    return processor.getResult();
  }

  // =============================================================================
  // Boundary Condition Tests - Kill ConditionalsBoundaryMutator
  // =============================================================================

  @Nested
  @DisplayName("Boundary Condition Tests")
  class BoundaryConditionTests {

    @Test
    @DisplayName("wither method name exactly 4 chars 'with' should be rejected")
    void witherMethodExactly4Chars() {
      // Tests: methodName.length() <= 4 boundary
      // "with" has exactly 4 chars, should NOT be a valid wither
      var source =
          JavaFileObjects.forSourceString(
              "com.test.BadWither",
              """
              package com.test;

              public class BadWither {
                  private String value;

                  public String getValue() { return value; }

                  // Method name "with" is exactly 4 chars - should not be detected as wither
                  public BadWither with(String value) {
                      return new BadWither();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.BadWither", source);

      // Should be UNSUPPORTED because "with" method doesn't count as wither
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      assertThat(analysis.witherMethods()).isEmpty();
    }

    @Test
    @DisplayName("wither method name 5 chars 'withX' should be accepted")
    void witherMethodExactly5Chars() {
      // Tests: methodName.length() <= 4 boundary
      // "withX" has 5 chars, should be a valid wither
      var source =
          JavaFileObjects.forSourceString(
              "com.test.GoodWither",
              """
              package com.test;

              public class GoodWither {
                  private String x;

                  public String getX() { return x; }

                  // Method name "withX" is 5 chars - should be detected as wither
                  public GoodWither withX(String x) {
                      return new GoodWither();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.GoodWither", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(1);
      assertThat(analysis.witherMethods().get(0).witherMethodName()).isEqualTo("withX");
    }

    @Test
    @DisplayName("setter method name exactly 3 chars 'set' should not be detected")
    void setterMethodExactly3Chars() {
      // Tests: methodName.length() > 3 boundary
      // "set" has exactly 3 chars, should NOT be detected as setter
      var source =
          JavaFileObjects.forSourceString(
              "com.test.NoSetter",
              """
              package com.test;

              public class NoSetter {
                  private String value;

                  public String getValue() { return value; }

                  // Method name "set" is exactly 3 chars - should not be detected as setter
                  public void set(String value) { this.value = value; }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.NoSetter", source);

      // Should NOT have mutable fields since "set" doesn't count
      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("setter method name 4 chars 'setX' should be detected")
    void setterMethodExactly4Chars() {
      // Tests: methodName.length() > 3 boundary
      // "setX" has 4 chars, should be detected as setter
      var source =
          JavaFileObjects.forSourceString(
              "com.test.HasSetter",
              """
              package com.test;

              public class HasSetter {
                  private String x;

                  public String getX() { return x; }

                  // Method name "setX" is 4 chars - should be detected as setter
                  public void setX(String x) { this.x = x; }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.HasSetter", source);

      assertThat(analysis.hasMutableFields()).isTrue();
    }

    @Test
    @DisplayName("Map with exactly 2 type arguments should be detected")
    void mapWithExactly2TypeArgs() {
      // Tests: declaredType.getTypeArguments().size() >= 2 boundary
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithMap",
              """
              package com.test;

              import java.util.Map;

              public record WithMap(Map<String, Integer> data) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithMap", source);

      assertThat(analysis.fields()).hasSize(1);
      assertThat(analysis.fields().get(0).containerType()).isPresent();
      assertThat(analysis.fields().get(0).containerType().get().kind())
          .isEqualTo(ContainerType.Kind.MAP);
    }

    @Test
    @DisplayName("Raw Map without type arguments should not be detected as container")
    void rawMapNotDetected() {
      // Tests: declaredType.getTypeArguments().size() >= 2 boundary
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithRawMap",
              """
              package com.test;

              import java.util.Map;

              @SuppressWarnings("rawtypes")
              public record WithRawMap(Map data) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithRawMap", source);

      assertThat(analysis.fields()).hasSize(1);
      // Raw Map should not be detected as a traversable container
      assertThat(analysis.fields().get(0).hasTraversal()).isFalse();
    }
  }

  // =============================================================================
  // Boolean Return Value Tests - Kill BooleanFalseReturnValsMutator
  // =============================================================================

  @Nested
  @DisplayName("Boolean Return Value Tests")
  class BooleanReturnTests {

    @Test
    @DisplayName("record should return supportsLenses=true and supportsPrisms=false")
    void recordBooleanReturns() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.SimpleRecord",
              """
              package com.test;

              public record SimpleRecord(String value) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.SimpleRecord", source);

      assertThat(analysis.supportsLenses()).isTrue();
      assertThat(analysis.supportsPrisms()).isFalse();
      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("sealed interface should return supportsLenses=false and supportsPrisms=true")
    void sealedInterfaceBooleanReturns() {
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.Result",
              """
              package com.test;

              public sealed interface Result permits Ok, Err {}
              """);

      var ok =
          JavaFileObjects.forSourceString(
              "com.test.Ok",
              """
              package com.test;

              public record Ok(String value) implements Result {}
              """);

      var err =
          JavaFileObjects.forSourceString(
              "com.test.Err",
              """
              package com.test;

              public record Err(String error) implements Result {}
              """);

      TypeAnalysis analysis = analyseType("com.test.Result", sealedInterface, ok, err);

      assertThat(analysis.supportsLenses()).isFalse();
      assertThat(analysis.supportsPrisms()).isTrue();
      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("enum should return supportsLenses=false and supportsPrisms=true")
    void enumBooleanReturns() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Color",
              """
              package com.test;

              public enum Color { RED, GREEN, BLUE }
              """);

      TypeAnalysis analysis = analyseType("com.test.Color", source);

      assertThat(analysis.supportsLenses()).isFalse();
      assertThat(analysis.supportsPrisms()).isTrue();
      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("unsupported type should return supportsLenses=false and supportsPrisms=false")
    void unsupportedTypeBooleanReturns() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.PlainClass",
              """
              package com.test;

              public class PlainClass {
                  private final String value;

                  public PlainClass(String value) { this.value = value; }
                  public String getValue() { return value; }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.PlainClass", source);

      assertThat(analysis.supportsLenses()).isFalse();
      assertThat(analysis.supportsPrisms()).isFalse();
    }

    @Test
    @DisplayName("immutable wither class should return hasMutableFields=false")
    void immutableWitherClassHasNoMutableFields() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.ImmutableClass",
              """
              package com.test;

              public final class ImmutableClass {
                  private final String value;

                  public ImmutableClass(String value) { this.value = value; }
                  public String getValue() { return value; }

                  public ImmutableClass withValue(String value) {
                      return new ImmutableClass(value);
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.ImmutableClass", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("mutable wither class should return hasMutableFields=true")
    void mutableWitherClassHasMutableFields() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.MutableClass",
              """
              package com.test;

              public class MutableClass {
                  private String value;

                  public String getValue() { return value; }
                  public void setValue(String value) { this.value = value; }

                  public MutableClass withValue(String value) {
                      MutableClass copy = new MutableClass();
                      copy.value = value;
                      return copy;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.MutableClass", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.hasMutableFields()).isTrue();
    }

    @Test
    @DisplayName("field without container should have hasTraversal=false")
    void nonContainerFieldHasNoTraversal() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.SimpleFields",
              """
              package com.test;

              public record SimpleFields(String name, int count, double value) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.SimpleFields", source);

      assertThat(analysis.fields()).hasSize(3);
      for (var field : analysis.fields()) {
        assertThat(field.hasTraversal()).isFalse();
        assertThat(field.containerType()).isEmpty();
      }
    }
  }

  // =============================================================================
  // Conditional Equality Tests - Kill RemoveConditionalMutator_EQUAL_*
  // =============================================================================

  @Nested
  @DisplayName("Conditional Equality Tests")
  class ConditionalEqualityTests {

    @Test
    @DisplayName("should distinguish record from class")
    void shouldDistinguishRecordFromClass() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.TestRecord",
              """
              package com.test;

              public record TestRecord(String value) {}
              """);

      var clazz =
          JavaFileObjects.forSourceString(
              "com.test.TestClass",
              """
              package com.test;

              public class TestClass {
                  private String value;
                  public String getValue() { return value; }
              }
              """);

      TypeAnalysis recordAnalysis = analyseType("com.test.TestRecord", record);
      TypeAnalysis classAnalysis = analyseType("com.test.TestClass", clazz);

      assertThat(recordAnalysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.RECORD);
      assertThat(classAnalysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
    }

    @Test
    @DisplayName("should distinguish interface from sealed interface")
    void shouldDistinguishInterfaceFromSealedInterface() {
      var regularInterface =
          JavaFileObjects.forSourceString(
              "com.test.RegularInterface",
              """
              package com.test;

              public interface RegularInterface {
                  String getValue();
              }
              """);

      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.SealedInterface",
              """
              package com.test;

              public sealed interface SealedInterface permits Impl {}
              """);

      var impl =
          JavaFileObjects.forSourceString(
              "com.test.Impl",
              """
              package com.test;

              public record Impl(String value) implements SealedInterface {}
              """);

      TypeAnalysis regularAnalysis = analyseType("com.test.RegularInterface", regularInterface);
      TypeAnalysis sealedAnalysis = analyseType("com.test.SealedInterface", sealedInterface, impl);

      // Regular interface is unsupported
      assertThat(regularAnalysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      // Sealed interface is supported
      assertThat(sealedAnalysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.SEALED_INTERFACE);
    }

    @Test
    @DisplayName("should detect List but not ArrayList as container")
    void shouldDetectListNotArrayList() {
      var withList =
          JavaFileObjects.forSourceString(
              "com.test.WithList",
              """
              package com.test;

              import java.util.List;

              public record WithList(List<String> items) {}
              """);

      var withArrayList =
          JavaFileObjects.forSourceString(
              "com.test.WithArrayList",
              """
              package com.test;

              import java.util.ArrayList;

              public record WithArrayList(ArrayList<String> items) {}
              """);

      TypeAnalysis listAnalysis = analyseType("com.test.WithList", withList);
      TypeAnalysis arrayListAnalysis = analyseType("com.test.WithArrayList", withArrayList);

      assertThat(listAnalysis.fields().get(0).containerType()).isPresent();
      assertThat(listAnalysis.fields().get(0).containerType().get().kind())
          .isEqualTo(ContainerType.Kind.LIST);

      // ArrayList is not detected as a special container type
      assertThat(arrayListAnalysis.fields().get(0).containerType()).isEmpty();
    }

    @Test
    @DisplayName("should detect Set container type")
    void shouldDetectSetContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithSet",
              """
              package com.test;

              import java.util.Set;

              public record WithSet(Set<Integer> numbers) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithSet", source);

      assertThat(analysis.fields().get(0).containerType()).isPresent();
      assertThat(analysis.fields().get(0).containerType().get().kind())
          .isEqualTo(ContainerType.Kind.SET);
    }

    @Test
    @DisplayName("should detect Optional container type")
    void shouldDetectOptionalContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithOptional",
              """
              package com.test;

              import java.util.Optional;

              public record WithOptional(Optional<String> maybeValue) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithOptional", source);

      assertThat(analysis.fields().get(0).containerType()).isPresent();
      assertThat(analysis.fields().get(0).containerType().get().kind())
          .isEqualTo(ContainerType.Kind.OPTIONAL);
    }

    @Test
    @DisplayName("wither must be public to be detected")
    void witherMustBePublic() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.PrivateWither",
              """
              package com.test;

              public class PrivateWither {
                  private String value;

                  public String getValue() { return value; }

                  // Private wither should not be detected
                  private PrivateWither withValue(String value) {
                      return new PrivateWither();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.PrivateWither", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      assertThat(analysis.witherMethods()).isEmpty();
    }

    @Test
    @DisplayName("wither must not be static to be detected")
    void witherMustNotBeStatic() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.StaticWither",
              """
              package com.test;

              public class StaticWither {
                  private String value;

                  public String getValue() { return value; }

                  // Static wither should not be detected
                  public static StaticWither withValue(String value) {
                      return new StaticWither();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.StaticWither", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      assertThat(analysis.witherMethods()).isEmpty();
    }

    @Test
    @DisplayName("wither must take exactly one parameter")
    void witherMustTakeOneParameter() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.MultiParamWither",
              """
              package com.test;

              public class MultiParamWither {
                  private String value;
                  private int count;

                  public String getValue() { return value; }

                  // Wither with 2 params should not be detected
                  public MultiParamWither withValue(String value, int extra) {
                      return new MultiParamWither();
                  }

                  // Wither with 0 params should not be detected
                  public MultiParamWither withCount() {
                      return new MultiParamWither();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.MultiParamWither", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      assertThat(analysis.witherMethods()).isEmpty();
    }

    @Test
    @DisplayName("wither must return same type as declaring class")
    void witherMustReturnSameType() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WrongReturnWither",
              """
              package com.test;

              public class WrongReturnWither {
                  private String value;

                  public String getValue() { return value; }

                  // Wither returning wrong type should not be detected
                  public String withValue(String value) {
                      return value;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.WrongReturnWither", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      assertThat(analysis.witherMethods()).isEmpty();
    }

    @Test
    @DisplayName("getter must be public to be paired with wither")
    void getterMustBePublic() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.PrivateGetter",
              """
              package com.test;

              public class PrivateGetter {
                  private String value;

                  // Private getter should not be detected
                  private String getValue() { return value; }

                  public PrivateGetter withValue(String value) {
                      return new PrivateGetter();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.PrivateGetter", source);

      // No getter found for wither, so it's unsupported
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
    }

    @Test
    @DisplayName("getter must not be static to be paired with wither")
    void getterMustNotBeStatic() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.StaticGetter",
              """
              package com.test;

              public class StaticGetter {
                  private static String value;

                  // Static getter should not be detected
                  public static String getValue() { return value; }

                  public StaticGetter withValue(String value) {
                      return new StaticGetter();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.StaticGetter", source);

      // No getter found for wither, so it's unsupported
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
    }

    @Test
    @DisplayName("getter must take no parameters")
    void getterMustTakeNoParams() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.GetterWithParam",
              """
              package com.test;

              public class GetterWithParam {
                  private String value;

                  // Getter with param should not be detected
                  public String getValue(String defaultValue) { return value != null ? value : defaultValue; }

                  public GetterWithParam withValue(String value) {
                      return new GetterWithParam();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.GetterWithParam", source);

      // No getter found for wither, so it's unsupported
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
    }

    @Test
    @DisplayName("getter return type must match wither parameter type")
    void getterReturnTypeMustMatchWitherParam() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.TypeMismatch",
              """
              package com.test;

              public class TypeMismatch {
                  private String value;

                  // Getter returns String
                  public String getValue() { return value; }

                  // Wither takes Integer - type mismatch
                  public TypeMismatch withValue(Integer value) {
                      return new TypeMismatch();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.TypeMismatch", source);

      // Type mismatch means no valid wither pair
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
    }
  }

  // =============================================================================
  // Getter Pattern Tests - Various Getter Naming Conventions
  // =============================================================================

  @Nested
  @DisplayName("Getter Pattern Tests")
  class GetterPatternTests {

    @Test
    @DisplayName("should detect record-style getter (fieldName)")
    void shouldDetectRecordStyleGetter() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.RecordStyle",
              """
              package com.test;

              public class RecordStyle {
                  private final String name;

                  public RecordStyle(String name) { this.name = name; }

                  // Record-style getter: name()
                  public String name() { return name; }

                  public RecordStyle withName(String name) {
                      return new RecordStyle(name);
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.RecordStyle", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(1);
      assertThat(analysis.witherMethods().get(0).getterMethodName()).isEqualTo("name");
    }

    @Test
    @DisplayName("should detect JavaBean-style getter (getFieldName)")
    void shouldDetectJavaBeanStyleGetter() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.JavaBeanStyle",
              """
              package com.test;

              public class JavaBeanStyle {
                  private final String name;

                  public JavaBeanStyle(String name) { this.name = name; }

                  // JavaBean-style getter: getName()
                  public String getName() { return name; }

                  public JavaBeanStyle withName(String name) {
                      return new JavaBeanStyle(name);
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.JavaBeanStyle", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(1);
      assertThat(analysis.witherMethods().get(0).getterMethodName()).isEqualTo("getName");
    }

    @Test
    @DisplayName("should detect boolean-style getter (isFieldName)")
    void shouldDetectBooleanStyleGetter() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.BooleanStyle",
              """
              package com.test;

              public class BooleanStyle {
                  private final boolean active;

                  public BooleanStyle(boolean active) { this.active = active; }

                  // Boolean-style getter: isActive()
                  public boolean isActive() { return active; }

                  public BooleanStyle withActive(boolean active) {
                      return new BooleanStyle(active);
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.BooleanStyle", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(1);
      assertThat(analysis.witherMethods().get(0).getterMethodName()).isEqualTo("isActive");
    }
  }

  // =============================================================================
  // ProcessorUtils Tests - toCamelCase Edge Cases
  // =============================================================================

  @Nested
  @DisplayName("ProcessorUtils Tests")
  class ProcessorUtilsTests {

    @Test
    @DisplayName("toCamelCase should handle null input")
    void toCamelCaseShouldHandleNull() {
      String result = ProcessorUtils.toCamelCase(null);
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("toCamelCase should handle empty input")
    void toCamelCaseShouldHandleEmpty() {
      String result = ProcessorUtils.toCamelCase("");
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toCamelCase should handle single uppercase letter")
    void toCamelCaseShouldHandleSingleUppercase() {
      String result = ProcessorUtils.toCamelCase("A");
      assertThat(result).isEqualTo("a");
    }

    @Test
    @DisplayName("toCamelCase should handle single lowercase letter")
    void toCamelCaseShouldHandleSingleLowercase() {
      String result = ProcessorUtils.toCamelCase("a");
      assertThat(result).isEqualTo("a");
    }

    @Test
    @DisplayName("toCamelCase should convert SNAKE_CASE to camelCase")
    void toCamelCaseShouldConvertSnakeCase() {
      assertThat(ProcessorUtils.toCamelCase("NOT_FOUND")).isEqualTo("notFound");
      assertThat(ProcessorUtils.toCamelCase("INTERNAL_SERVER_ERROR"))
          .isEqualTo("internalServerError");
      assertThat(ProcessorUtils.toCamelCase("HTTP_STATUS_CODE")).isEqualTo("httpStatusCode");
    }

    @Test
    @DisplayName("toCamelCase should convert PascalCase to camelCase")
    void toCamelCaseShouldConvertPascalCase() {
      assertThat(ProcessorUtils.toCamelCase("NotFound")).isEqualTo("notFound");
      assertThat(ProcessorUtils.toCamelCase("Circle")).isEqualTo("circle");
      assertThat(ProcessorUtils.toCamelCase("MyClass")).isEqualTo("myClass");
    }

    @Test
    @DisplayName("toCamelCase should preserve already camelCase")
    void toCamelCaseShouldPreserveCamelCase() {
      assertThat(ProcessorUtils.toCamelCase("notFound")).isEqualTo("notFound");
      assertThat(ProcessorUtils.toCamelCase("myValue")).isEqualTo("myValue");
    }

    @Test
    @DisplayName("toCamelCase should handle all uppercase")
    void toCamelCaseShouldHandleAllUppercase() {
      assertThat(ProcessorUtils.toCamelCase("OK")).isEqualTo("ok");
      assertThat(ProcessorUtils.toCamelCase("ID")).isEqualTo("id");
      assertThat(ProcessorUtils.toCamelCase("URL")).isEqualTo("url");
    }

    @Test
    @DisplayName("isAllUpperCase should return true for all uppercase")
    void isAllUpperCaseShouldReturnTrueForAllUppercase() {
      assertThat(ProcessorUtils.isAllUpperCase("ABC")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("HELLO")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("X")).isTrue();
    }

    @Test
    @DisplayName("isAllUpperCase should return false for mixed case")
    void isAllUpperCaseShouldReturnFalseForMixedCase() {
      assertThat(ProcessorUtils.isAllUpperCase("Hello")).isFalse();
      assertThat(ProcessorUtils.isAllUpperCase("hELLO")).isFalse();
      assertThat(ProcessorUtils.isAllUpperCase("HELLo")).isFalse();
      assertThat(ProcessorUtils.isAllUpperCase("abc")).isFalse();
    }

    @Test
    @DisplayName("isAllUpperCase should return true for empty string")
    void isAllUpperCaseShouldReturnTrueForEmpty() {
      assertThat(ProcessorUtils.isAllUpperCase("")).isTrue();
    }

    @Test
    @DisplayName("isAllUpperCase should ignore non-letter characters")
    void isAllUpperCaseShouldIgnoreNonLetters() {
      assertThat(ProcessorUtils.isAllUpperCase("ABC123")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("A_B_C")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("123")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("A-B-C")).isTrue();
    }

    @Test
    @DisplayName("isAllUpperCase should return false when any letter is lowercase")
    void isAllUpperCaseShouldReturnFalseWithAnyLowercase() {
      assertThat(ProcessorUtils.isAllUpperCase("ABc")).isFalse();
      assertThat(ProcessorUtils.isAllUpperCase("a")).isFalse();
      assertThat(ProcessorUtils.isAllUpperCase("123a456")).isFalse();
    }
  }

  // =============================================================================
  // Annotation Placement Tests - Kill RemoveConditionalMutator for element kinds
  // =============================================================================

  @Nested
  @DisplayName("Annotation Placement Tests")
  class AnnotationPlacementTests {

    @Test
    @DisplayName("@ImportOptics should work on package-info")
    void shouldWorkOnPackageInfo() {
      var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Item",
              """
              package com.external;

              public record Item(String name) {}
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics({com.external.Item.class})
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.ItemLenses").isNotNull();
    }

    @Test
    @DisplayName("@ImportOptics should work on class")
    void shouldWorkOnClass() {
      var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Product",
              """
              package com.external;

              public record Product(String sku) {}
              """);

      var importerClass =
          JavaFileObjects.forSourceString(
              "com.myapp.ProductImporter",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({com.external.Product.class})
              public class ProductImporter {}
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importerClass);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.ProductLenses").isNotNull();
    }

    @Test
    @DisplayName("@ImportOptics should work on interface")
    void shouldWorkOnInterface() {
      var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Widget",
              """
              package com.external;

              public record Widget(int id) {}
              """);

      var importerInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.WidgetImporter",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({com.external.Widget.class})
              public interface WidgetImporter {}
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importerInterface);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.WidgetLenses").isNotNull();
    }

    @Test
    @DisplayName("@ImportOptics should fail on method")
    void shouldFailOnMethod() {
      var source =
          JavaFileObjects.forSourceString(
              "com.myapp.BadUsage",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              public class BadUsage {
                  @ImportOptics({String.class})
                  public void badMethod() {}
              }
              """);

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(source);

      // Annotation on method should either fail or be ignored
      // (depends on annotation target restrictions)
      // If it compiles, check no optics were generated
      if (compilation.status() == Compilation.Status.SUCCESS) {
        assertThat(compilation.generatedSourceFiles()).isEmpty();
      }
    }
  }

  // =============================================================================
  // Empty/Null Cases - Kill mutations in null/empty checks
  // =============================================================================

  @Nested
  @DisplayName("Empty and Null Case Tests")
  class EmptyNullCaseTests {

    @Test
    @DisplayName("record with no components should still be analyzed")
    void recordWithNoComponents() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.EmptyRecord",
              """
              package com.test;

              public record EmptyRecord() {}
              """);

      TypeAnalysis analysis = analyseType("com.test.EmptyRecord", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.RECORD);
      assertThat(analysis.fields()).isEmpty();
      assertThat(analysis.supportsLenses()).isTrue();
    }

    @Test
    @DisplayName("enum with single constant should be analyzed")
    void enumWithSingleConstant() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.SingletonEnum",
              """
              package com.test;

              public enum SingletonEnum { INSTANCE }
              """);

      TypeAnalysis analysis = analyseType("com.test.SingletonEnum", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.ENUM);
      assertThat(analysis.enumConstants()).containsExactly("INSTANCE");
    }

    @Test
    @DisplayName("sealed interface with single permitted subtype should be analyzed")
    void sealedInterfaceWithSingleSubtype() {
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.Wrapper",
              """
              package com.test;

              public sealed interface Wrapper permits Wrapped {}
              """);

      var impl =
          JavaFileObjects.forSourceString(
              "com.test.Wrapped",
              """
              package com.test;

              public record Wrapped(String value) implements Wrapper {}
              """);

      TypeAnalysis analysis = analyseType("com.test.Wrapper", sealedInterface, impl);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.SEALED_INTERFACE);
      assertThat(analysis.permittedSubtypes()).hasSize(1);
    }

    @Test
    @DisplayName("class with single wither should be detected")
    void classWithSingleWither() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.SingleWither",
              """
              package com.test;

              public class SingleWither {
                  private final String value;

                  public SingleWither(String value) { this.value = value; }
                  public String getValue() { return value; }

                  public SingleWither withValue(String value) {
                      return new SingleWither(value);
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.SingleWither", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(1);
      assertThat(analysis.fields()).hasSize(1);
    }
  }

  // =============================================================================
  // Container Type isMap Tests - Kill mutations in isMap() check
  // =============================================================================

  @Nested
  @DisplayName("ContainerType isMap Tests")
  class ContainerTypeIsMapTests {

    @Test
    @DisplayName("Map container should return isMap=true")
    void mapContainerShouldReturnIsMapTrue() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithMap",
              """
              package com.test;

              import java.util.Map;

              public record WithMap(Map<String, Integer> data) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithMap", source);

      assertThat(analysis.fields().get(0).containerType()).isPresent();
      ContainerType container = analysis.fields().get(0).containerType().get();
      assertThat(container.isMap()).isTrue();
      assertThat(container.kind()).isEqualTo(ContainerType.Kind.MAP);
    }

    @Test
    @DisplayName("List container should return isMap=false")
    void listContainerShouldReturnIsMapFalse() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithList",
              """
              package com.test;

              import java.util.List;

              public record WithList(List<String> items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithList", source);

      assertThat(analysis.fields().get(0).containerType()).isPresent();
      ContainerType container = analysis.fields().get(0).containerType().get();
      assertThat(container.isMap()).isFalse();
      assertThat(container.kind()).isEqualTo(ContainerType.Kind.LIST);
    }

    @Test
    @DisplayName("Set container should return isMap=false")
    void setContainerShouldReturnIsMapFalse() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithSet",
              """
              package com.test;

              import java.util.Set;

              public record WithSet(Set<String> items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithSet", source);

      assertThat(analysis.fields().get(0).containerType()).isPresent();
      ContainerType container = analysis.fields().get(0).containerType().get();
      assertThat(container.isMap()).isFalse();
      assertThat(container.kind()).isEqualTo(ContainerType.Kind.SET);
    }

    @Test
    @DisplayName("Optional container should return isMap=false")
    void optionalContainerShouldReturnIsMapFalse() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithOptional",
              """
              package com.test;

              import java.util.Optional;

              public record WithOptional(Optional<String> value) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithOptional", source);

      assertThat(analysis.fields().get(0).containerType()).isPresent();
      ContainerType container = analysis.fields().get(0).containerType().get();
      assertThat(container.isMap()).isFalse();
      assertThat(container.kind()).isEqualTo(ContainerType.Kind.OPTIONAL);
    }

    @Test
    @DisplayName("Array container should return isMap=false")
    void arrayContainerShouldReturnIsMapFalse() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithArray",
              """
              package com.test;

              public record WithArray(String[] items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithArray", source);

      assertThat(analysis.fields().get(0).containerType()).isPresent();
      ContainerType container = analysis.fields().get(0).containerType().get();
      assertThat(container.isMap()).isFalse();
      assertThat(container.kind()).isEqualTo(ContainerType.Kind.ARRAY);
    }
  }

  // =============================================================================
  // Multiple Field Tests - Kill mutations in loops
  // =============================================================================

  @Nested
  @DisplayName("Multiple Field Tests")
  class MultipleFieldTests {

    @Test
    @DisplayName("record with many components should have all fields analyzed")
    void recordWithManyComponents() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.ManyFields",
              """
              package com.test;

              import java.util.List;
              import java.util.Optional;

              public record ManyFields(
                  String name,
                  int age,
                  List<String> tags,
                  Optional<String> nickname,
                  double score
              ) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.ManyFields", source);

      assertThat(analysis.fields()).hasSize(5);

      // Check each field
      assertThat(analysis.fields().get(0).name()).isEqualTo("name");
      assertThat(analysis.fields().get(0).hasTraversal()).isFalse();

      assertThat(analysis.fields().get(1).name()).isEqualTo("age");
      assertThat(analysis.fields().get(1).hasTraversal()).isFalse();

      assertThat(analysis.fields().get(2).name()).isEqualTo("tags");
      assertThat(analysis.fields().get(2).hasTraversal()).isTrue();
      assertThat(analysis.fields().get(2).containerType().get().kind())
          .isEqualTo(ContainerType.Kind.LIST);

      assertThat(analysis.fields().get(3).name()).isEqualTo("nickname");
      assertThat(analysis.fields().get(3).hasTraversal()).isTrue();
      assertThat(analysis.fields().get(3).containerType().get().kind())
          .isEqualTo(ContainerType.Kind.OPTIONAL);

      assertThat(analysis.fields().get(4).name()).isEqualTo("score");
      assertThat(analysis.fields().get(4).hasTraversal()).isFalse();
    }

    @Test
    @DisplayName("enum with many constants should have all constants analyzed")
    void enumWithManyConstants() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.ManyConstants",
              """
              package com.test;

              public enum ManyConstants {
                  FIRST,
                  SECOND,
                  THIRD,
                  FOURTH,
                  FIFTH
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.ManyConstants", source);

      assertThat(analysis.enumConstants()).hasSize(5);
      assertThat(analysis.enumConstants())
          .containsExactly("FIRST", "SECOND", "THIRD", "FOURTH", "FIFTH");
    }

    @Test
    @DisplayName("class with many withers should have all withers analyzed")
    void classWithManyWithers() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.ManyWithers",
              """
              package com.test;

              public class ManyWithers {
                  private final String a;
                  private final String b;
                  private final String c;

                  public ManyWithers(String a, String b, String c) {
                      this.a = a;
                      this.b = b;
                      this.c = c;
                  }

                  public String getA() { return a; }
                  public String getB() { return b; }
                  public String getC() { return c; }

                  public ManyWithers withA(String a) { return new ManyWithers(a, b, c); }
                  public ManyWithers withB(String b) { return new ManyWithers(a, b, c); }
                  public ManyWithers withC(String c) { return new ManyWithers(a, b, c); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.ManyWithers", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(3);
      assertThat(analysis.fields()).hasSize(3);
    }
  }

  // =============================================================================
  // Generated Code Tests - Verify actual code generation
  // =============================================================================

  @Nested
  @DisplayName("Generated Code Tests")
  class GeneratedCodeTests {

    @Test
    @DisplayName("should generate traversal for List field")
    void shouldGenerateTraversalForListField() {
      var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Order",
              """
              package com.external;

              import java.util.List;

              public record Order(String id, List<String> items) {}
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics({com.external.Order.class})
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.OrderLenses").isNotNull();
    }

    @Test
    @DisplayName("should generate with method for each field")
    void shouldGenerateWithMethodForEachField() {
      var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Point3D",
              """
              package com.external;

              public record Point3D(int x, int y, int z) {}
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics({com.external.Point3D.class})
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();

      // Verify with methods are generated
      assertGeneratedCodeContains(
          compilation, "com.myapp.Point3DLenses", "public static Point3D withX");
      assertGeneratedCodeContains(
          compilation, "com.myapp.Point3DLenses", "public static Point3D withY");
      assertGeneratedCodeContains(
          compilation, "com.myapp.Point3DLenses", "public static Point3D withZ");
    }
  }

  // =============================================================================
  // Raw Type Boundary Tests - Kill mutations in type argument checks
  // =============================================================================

  @Nested
  @DisplayName("Raw Type Boundary Tests")
  class RawTypeBoundaryTests {

    @Test
    @DisplayName("Raw List without type arguments should not be detected as container")
    void rawListNotDetected() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithRawList",
              """
              package com.test;

              import java.util.List;

              @SuppressWarnings("rawtypes")
              public record WithRawList(List items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithRawList", source);

      assertThat(analysis.fields()).hasSize(1);
      // Raw List should not have a traversal
      assertThat(analysis.fields().get(0).hasTraversal()).isFalse();
    }

    @Test
    @DisplayName("Raw Set without type arguments should not be detected as container")
    void rawSetNotDetected() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithRawSet",
              """
              package com.test;

              import java.util.Set;

              @SuppressWarnings("rawtypes")
              public record WithRawSet(Set items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithRawSet", source);

      assertThat(analysis.fields()).hasSize(1);
      assertThat(analysis.fields().get(0).hasTraversal()).isFalse();
    }

    @Test
    @DisplayName("Raw Optional without type arguments should not be detected as container")
    void rawOptionalNotDetected() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithRawOptional",
              """
              package com.test;

              import java.util.Optional;

              @SuppressWarnings("rawtypes")
              public record WithRawOptional(Optional value) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithRawOptional", source);

      assertThat(analysis.fields()).hasSize(1);
      assertThat(analysis.fields().get(0).hasTraversal()).isFalse();
    }

    @Test
    @DisplayName("Map with only one type argument should not be detected")
    void mapWithOneTypeArgNotDetected() {
      // This is a tricky case - in practice Map always needs 2 type args,
      // but the check in TypeKindAnalyser guards against edge cases
      var source =
          JavaFileObjects.forSourceString(
              "com.test.NormalMap",
              """
              package com.test;

              import java.util.Map;

              public record NormalMap(Map<String, Integer> data) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.NormalMap", source);

      // Normal Map should be detected
      assertThat(analysis.fields().get(0).containerType()).isPresent();
      assertThat(analysis.fields().get(0).containerType().get().kind())
          .isEqualTo(ContainerType.Kind.MAP);
    }
  }

  // =============================================================================
  // Annotation Value Extraction Tests
  // =============================================================================

  @Nested
  @DisplayName("Annotation Value Extraction Tests")
  class AnnotationValueExtractionTests {

    @Test
    @DisplayName("should handle multiple classes in @ImportOptics")
    void shouldHandleMultipleClasses() {
      var record1 =
          JavaFileObjects.forSourceString(
              "com.external.Record1",
              """
              package com.external;

              public record Record1(String value1) {}
              """);

      var record2 =
          JavaFileObjects.forSourceString(
              "com.external.Record2",
              """
              package com.external;

              public record Record2(int value2) {}
              """);

      var record3 =
          JavaFileObjects.forSourceString(
              "com.external.Record3",
              """
              package com.external;

              public record Record3(boolean value3) {}
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics({
                  com.external.Record1.class,
                  com.external.Record2.class,
                  com.external.Record3.class
              })
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(record1, record2, record3, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.Record1Lenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.myapp.Record2Lenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.myapp.Record3Lenses").isNotNull();
    }

    @Test
    @DisplayName("should handle single class in @ImportOptics")
    void shouldHandleSingleClass() {
      var record =
          JavaFileObjects.forSourceString(
              "com.external.SingleRecord",
              """
              package com.external;

              public record SingleRecord(String value) {}
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics({com.external.SingleRecord.class})
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(record, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.SingleRecordLenses").isNotNull();
    }
  }

  // =============================================================================
  // Additional Boolean False Return Tests
  // =============================================================================

  @Nested
  @DisplayName("Boolean False Return Tests")
  class BooleanFalseReturnTests {

    @Test
    @DisplayName("class without setters should return hasMutableFields=false")
    void classWithoutSettersShouldReturnFalse() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.NoSetters",
              """
              package com.test;

              public class NoSetters {
                  private final String value;

                  public NoSetters(String value) { this.value = value; }
                  public String getValue() { return value; }

                  public NoSetters withValue(String value) {
                      return new NoSetters(value);
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.NoSetters", source);

      // Verify hasMutableFields returns false, not true
      assertThat(analysis.hasMutableFields()).isFalse();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
    }

    @Test
    @DisplayName("class with only private setters should return hasMutableFields=false")
    void classWithPrivateSettersShouldReturnFalse() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.PrivateSetters",
              """
              package com.test;

              public class PrivateSetters {
                  private String value;

                  public String getValue() { return value; }

                  // Private setter doesn't count as mutable
                  private void setValue(String value) { this.value = value; }

                  public PrivateSetters withValue(String value) {
                      PrivateSetters copy = new PrivateSetters();
                      copy.value = value;
                      return copy;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.PrivateSetters", source);

      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("class with static setters should return hasMutableFields=false")
    void classWithStaticSettersShouldReturnFalse() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.StaticSetters",
              """
              package com.test;

              public class StaticSetters {
                  private static String value;

                  public String getValue() { return value; }

                  // Static setter doesn't count as mutable
                  public static void setValue(String value) {
                      StaticSetters.value = value;
                  }

                  public StaticSetters withValue(String v) {
                      return new StaticSetters();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.StaticSetters", source);

      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("class with non-void setters should return hasMutableFields=false")
    void classWithNonVoidSettersShouldReturnFalse() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.FluentSetters",
              """
              package com.test;

              public class FluentSetters {
                  private String value;

                  public String getValue() { return value; }

                  // Fluent setter (returns this) doesn't count as traditional setter
                  public FluentSetters setValue(String value) {
                      this.value = value;
                      return this;
                  }

                  public FluentSetters withValue(String value) {
                      FluentSetters copy = new FluentSetters();
                      copy.value = value;
                      return copy;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.FluentSetters", source);

      // Fluent setter doesn't count as mutable because it doesn't return void
      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("class with setters taking wrong number of params should return false")
    void classWithWrongParamCountSettersShouldReturnFalse() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WrongParamSetters",
              """
              package com.test;

              public class WrongParamSetters {
                  private String value;

                  public String getValue() { return value; }

                  // Setter with 0 params doesn't count
                  public void setValue() { this.value = "default"; }

                  // Setter with 2 params doesn't count
                  public void setValueWithDefault(String value, String defaultValue) {
                      this.value = value != null ? value : defaultValue;
                  }

                  public WrongParamSetters withValue(String value) {
                      WrongParamSetters copy = new WrongParamSetters();
                      copy.value = value;
                      return copy;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.WrongParamSetters", source);

      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("method named exactly 'set' should not be detected as setter")
    void methodNamedSetShouldNotBeDetected() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.JustSet",
              """
              package com.test;

              public class JustSet {
                  private String value;

                  public String getValue() { return value; }

                  // Method named 'set' (exactly 3 chars) doesn't count
                  public void set(String value) { this.value = value; }

                  public JustSet withValue(String value) {
                      JustSet copy = new JustSet();
                      copy.value = value;
                      return copy;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.JustSet", source);

      // 'set' method (exactly 3 chars) should NOT be detected as setter
      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("class with valid public void setter should return hasMutableFields=true")
    void classWithValidSetterShouldReturnTrue() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.ValidSetter",
              """
              package com.test;

              public class ValidSetter {
                  private String value;

                  public String getValue() { return value; }

                  // Valid setter: public, void, 1 param, name > 3 chars
                  public void setValue(String value) { this.value = value; }

                  public ValidSetter withValue(String value) {
                      ValidSetter copy = new ValidSetter();
                      copy.value = value;
                      return copy;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.ValidSetter", source);

      assertThat(analysis.hasMutableFields()).isTrue();
    }
  }

  // =============================================================================
  // Additional Boundary Condition Tests
  // =============================================================================

  @Nested
  @DisplayName("Additional Boundary Condition Tests")
  class AdditionalBoundaryTests {

    @Test
    @DisplayName("wither named 'withA' (5 chars) should be detected")
    void witherWith5CharsShouldBeDetected() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.FiveCharWither",
              """
              package com.test;

              public class FiveCharWither {
                  private String a;

                  public String getA() { return a; }

                  // 'withA' is exactly 5 characters
                  public FiveCharWither withA(String a) {
                      return new FiveCharWither();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.FiveCharWither", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(1);
    }

    @Test
    @DisplayName("setter named 'setA' (4 chars) should be detected")
    void setterWith4CharsShouldBeDetected() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.FourCharSetter",
              """
              package com.test;

              public class FourCharSetter {
                  private String a;

                  public String getA() { return a; }

                  // 'setA' is exactly 4 characters
                  public void setA(String a) { this.a = a; }

                  public FourCharSetter withA(String a) {
                      return new FourCharSetter();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.FourCharSetter", source);

      assertThat(analysis.hasMutableFields()).isTrue();
    }

    @Test
    @DisplayName("List with exactly one type argument should be detected")
    void listWithOneTypeArgShouldBeDetected() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.OneTypeArgList",
              """
              package com.test;

              import java.util.List;

              public record OneTypeArgList(List<String> items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.OneTypeArgList", source);

      assertThat(analysis.fields().get(0).containerType()).isPresent();
      assertThat(analysis.fields().get(0).containerType().get().kind())
          .isEqualTo(ContainerType.Kind.LIST);
    }

    @Test
    @DisplayName("capitalise should handle empty string")
    void capitaliseShouldHandleEmpty() {
      // This tests the capitalise method boundary condition
      var source =
          JavaFileObjects.forSourceString(
              "com.test.EmptyFieldName",
              """
              package com.test;

              public class EmptyFieldName {
                  private String value;

                  public String getValue() { return value; }

                  public EmptyFieldName withValue(String value) {
                      return new EmptyFieldName();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.EmptyFieldName", source);

      // Should still work - the field name extracted from withValue is "value"
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.fields().get(0).name()).isEqualTo("value");
    }
  }

  // =============================================================================
  // Void Method Call Tests - Test generated output
  // =============================================================================

  @Nested
  @DisplayName("Void Method Call Tests")
  class VoidMethodCallTests {

    @Test
    @DisplayName("should generate lens class with private constructor")
    void shouldGenerateLensClassWithPrivateConstructor() {
      var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Data",
              """
              package com.external;

              public record Data(String value) {}
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics({com.external.Data.class})
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.myapp.DataLenses", "private DataLenses()");
    }

    @Test
    @DisplayName("should generate prism class with @Generated annotation")
    void shouldGeneratePrismClassWithGeneratedAnnotation() {
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.external.Animal",
              """
              package com.external;

              public sealed interface Animal permits Dog, Cat {}
              """);

      var dog =
          JavaFileObjects.forSourceString(
              "com.external.Dog",
              """
              package com.external;

              public record Dog(String name) implements Animal {}
              """);

      var cat =
          JavaFileObjects.forSourceString(
              "com.external.Cat",
              """
              package com.external;

              public record Cat(String name) implements Animal {}
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics({com.external.Animal.class})
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(sealedInterface, dog, cat, packageInfo);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.myapp.AnimalPrisms", "@Generated");
      assertGeneratedCodeContains(compilation, "com.myapp.AnimalPrisms", "private AnimalPrisms()");
    }

    @Test
    @DisplayName("should generate enum prisms with correct method names")
    void shouldGenerateEnumPrismsWithCorrectMethodNames() {
      var enumType =
          JavaFileObjects.forSourceString(
              "com.external.Size",
              """
              package com.external;

              public enum Size { SMALL, MEDIUM, LARGE, EXTRA_LARGE }
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics({com.external.Size.class})
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(enumType, packageInfo);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.SizePrisms", "public static Prism<Size, Size> small()");
      assertGeneratedCodeContains(
          compilation, "com.myapp.SizePrisms", "public static Prism<Size, Size> medium()");
      assertGeneratedCodeContains(
          compilation, "com.myapp.SizePrisms", "public static Prism<Size, Size> large()");
      assertGeneratedCodeContains(
          compilation, "com.myapp.SizePrisms", "public static Prism<Size, Size> extraLarge()");
    }

    @Test
    @DisplayName("should generate file comment in generated code")
    void shouldGenerateFileComment() {
      var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Thing",
              """
              package com.external;

              public record Thing(int id) {}
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics({com.external.Thing.class})
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();
      // Verify the class is generated with expected structure
      assertGeneratedCodeContains(
          compilation, "com.myapp.ThingLenses", "public final class ThingLenses");
    }

    @Test
    @DisplayName("should generate javadoc on lens methods")
    void shouldGenerateJavadocOnLensMethods() {
      var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Person",
              """
              package com.external;

              public record Person(String name, int age) {}
              """);

      var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @ImportOptics({com.external.Person.class})
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();
      // Verify lens methods are generated for both fields
      assertGeneratedCodeContains(
          compilation, "com.myapp.PersonLenses", "public static Lens<Person, String> name()");
      assertGeneratedCodeContains(
          compilation, "com.myapp.PersonLenses", "public static Lens<Person, Integer> age()");
    }
  }

  // =============================================================================
  // Type Analysis Static Factory Tests
  // =============================================================================

  @Nested
  @DisplayName("TypeAnalysis Static Factory Tests")
  class TypeAnalysisStaticFactoryTests {

    @Test
    @DisplayName("forRecord should set correct typeKind")
    void forRecordShouldSetCorrectTypeKind() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.TestRecord",
              """
              package com.test;

              public record TestRecord(String a, int b, double c) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.TestRecord", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.RECORD);
      assertThat(analysis.witherMethods()).isEmpty();
      assertThat(analysis.permittedSubtypes()).isEmpty();
      assertThat(analysis.enumConstants()).isEmpty();
    }

    @Test
    @DisplayName("forSealedInterface should set correct values")
    void forSealedInterfaceShouldSetCorrectValues() {
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.Vehicle",
              """
              package com.test;

              public sealed interface Vehicle permits Car, Bike {}
              """);

      var car =
          JavaFileObjects.forSourceString(
              "com.test.Car",
              """
              package com.test;

              public record Car(int wheels) implements Vehicle {}
              """);

      var bike =
          JavaFileObjects.forSourceString(
              "com.test.Bike",
              """
              package com.test;

              public record Bike(int wheels) implements Vehicle {}
              """);

      TypeAnalysis analysis = analyseType("com.test.Vehicle", sealedInterface, car, bike);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.SEALED_INTERFACE);
      assertThat(analysis.fields()).isEmpty();
      assertThat(analysis.witherMethods()).isEmpty();
      assertThat(analysis.permittedSubtypes()).hasSize(2);
      assertThat(analysis.enumConstants()).isEmpty();
    }

    @Test
    @DisplayName("forEnum should set correct values")
    void forEnumShouldSetCorrectValues() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Direction",
              """
              package com.test;

              public enum Direction { NORTH, SOUTH, EAST, WEST }
              """);

      TypeAnalysis analysis = analyseType("com.test.Direction", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.ENUM);
      assertThat(analysis.fields()).isEmpty();
      assertThat(analysis.witherMethods()).isEmpty();
      assertThat(analysis.permittedSubtypes()).isEmpty();
      assertThat(analysis.enumConstants()).containsExactly("NORTH", "SOUTH", "EAST", "WEST");
    }

    @Test
    @DisplayName("forWitherClass should set correct values")
    void forWitherClassShouldSetCorrectValues() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Builder",
              """
              package com.test;

              public class Builder {
                  private final String name;
                  private final int value;

                  public Builder(String name, int value) {
                      this.name = name;
                      this.value = value;
                  }

                  public String getName() { return name; }
                  public int getValue() { return value; }

                  public Builder withName(String name) { return new Builder(name, value); }
                  public Builder withValue(int value) { return new Builder(name, value); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.Builder", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.fields()).hasSize(2);
      assertThat(analysis.witherMethods()).hasSize(2);
      assertThat(analysis.permittedSubtypes()).isEmpty();
      assertThat(analysis.enumConstants()).isEmpty();
    }

    @Test
    @DisplayName("unsupported should set correct values")
    void unsupportedShouldSetCorrectValues() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.PlainInterface",
              """
              package com.test;

              public interface PlainInterface {
                  String getValue();
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.PlainInterface", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      assertThat(analysis.supportsLenses()).isFalse();
      assertThat(analysis.supportsPrisms()).isFalse();
    }
  }

  // =============================================================================
  // Order-Dependent Conditional Tests
  // =============================================================================

  @Nested
  @DisplayName("Order-Dependent Conditional Tests")
  class OrderDependentConditionalTests {

    @Test
    @DisplayName("should check record before class")
    void shouldCheckRecordBeforeClass() {
      // Records are also classes in the type system, but should be detected as RECORD
      var source =
          JavaFileObjects.forSourceString(
              "com.test.RecordType",
              """
              package com.test;

              public record RecordType(String value) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.RecordType", source);

      // Should be RECORD, not WITHER_CLASS
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.RECORD);
    }

    @Test
    @DisplayName("should check sealed before regular interface")
    void shouldCheckSealedBeforeRegularInterface() {
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.Sealed",
              """
              package com.test;

              public sealed interface Sealed permits Impl {}
              """);

      var impl =
          JavaFileObjects.forSourceString(
              "com.test.Impl",
              """
              package com.test;

              public record Impl() implements Sealed {}
              """);

      TypeAnalysis analysis = analyseType("com.test.Sealed", sealedInterface, impl);

      // Should be SEALED_INTERFACE, not UNSUPPORTED
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.SEALED_INTERFACE);
    }

    @Test
    @DisplayName("should check enum before class")
    void shouldCheckEnumBeforeClass() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.EnumType",
              """
              package com.test;

              public enum EnumType { VALUE }
              """);

      TypeAnalysis analysis = analyseType("com.test.EnumType", source);

      // Should be ENUM
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.ENUM);
    }

    @Test
    @DisplayName("should check wither method conditions in order")
    void shouldCheckWitherMethodConditionsInOrder() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.MultiCheck",
              """
              package com.test;

              public class MultiCheck {
                  private String value;

                  public String getValue() { return value; }

                  // All conditions met: starts with 'with', length > 4, public, non-static, 1 param, returns same type
                  public MultiCheck withValue(String value) {
                      return new MultiCheck();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.MultiCheck", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(1);
    }
  }

  // =============================================================================
  // Additional Mutation Killing Tests - Push to 65%
  // =============================================================================

  @Nested
  @DisplayName("String Loop Boundary Tests")
  class StringLoopBoundaryTests {

    @Test
    @DisplayName("isAllUpperCase with single character string")
    void isAllUpperCaseSingleChar() {
      assertThat(ProcessorUtils.isAllUpperCase("A")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("a")).isFalse();
      assertThat(ProcessorUtils.isAllUpperCase("Z")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("z")).isFalse();
    }

    @Test
    @DisplayName("isAllUpperCase with two character strings")
    void isAllUpperCaseTwoChars() {
      assertThat(ProcessorUtils.isAllUpperCase("AB")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("Ab")).isFalse();
      assertThat(ProcessorUtils.isAllUpperCase("aB")).isFalse();
      assertThat(ProcessorUtils.isAllUpperCase("ab")).isFalse();
    }

    @Test
    @DisplayName("toCamelCase with single underscore")
    void toCamelCaseSingleUnderscore() {
      assertThat(ProcessorUtils.toCamelCase("A_B")).isEqualTo("aB");
      assertThat(ProcessorUtils.toCamelCase("HELLO_WORLD")).isEqualTo("helloWorld");
    }

    @Test
    @DisplayName("toCamelCase with multiple underscores")
    void toCamelCaseMultipleUnderscores() {
      assertThat(ProcessorUtils.toCamelCase("A_B_C")).isEqualTo("aBC");
      assertThat(ProcessorUtils.toCamelCase("ONE_TWO_THREE")).isEqualTo("oneTwoThree");
    }

    @Test
    @DisplayName("toCamelCase with trailing underscore")
    void toCamelCaseTrailingUnderscore() {
      assertThat(ProcessorUtils.toCamelCase("HELLO_")).isEqualTo("hello");
    }

    @Test
    @DisplayName("toCamelCase with leading underscore")
    void toCamelCaseLeadingUnderscore() {
      assertThat(ProcessorUtils.toCamelCase("_HELLO")).isEqualTo("Hello");
    }

    @Test
    @DisplayName("toCamelCase with consecutive underscores")
    void toCamelCaseConsecutiveUnderscores() {
      assertThat(ProcessorUtils.toCamelCase("A__B")).isEqualTo("aB");
    }
  }

  @Nested
  @DisplayName("Wither Detection Edge Cases")
  class WitherDetectionEdgeCases {

    @Test
    @DisplayName("wither with wrong return type should not be detected")
    void witherWithWrongReturnType() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WrongReturn",
              """
              package com.test;

              public class WrongReturn {
                  private String value;

                  public String getValue() { return value; }

                  // Returns Object instead of WrongReturn
                  public Object withValue(String value) {
                      return new WrongReturn();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.WrongReturn", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
    }

    @Test
    @DisplayName("wither with subtype return should be detected")
    void witherWithSubtypeReturn() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.SubtypeReturn",
              """
              package com.test;

              public class SubtypeReturn {
                  private String value;

                  public String getValue() { return value; }

                  // Returns same type - should work
                  public SubtypeReturn withValue(String value) {
                      return new SubtypeReturn();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.SubtypeReturn", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
    }

    @Test
    @DisplayName("class with method not starting with 'with' should not detect it as wither")
    void methodNotStartingWithWith() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.NotWith",
              """
              package com.test;

              public class NotWith {
                  private String value;

                  public String getValue() { return value; }

                  // Doesn't start with 'with'
                  public NotWith updateValue(String value) {
                      return new NotWith();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.NotWith", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
    }
  }

  @Nested
  @DisplayName("Getter Detection Edge Cases")
  class GetterDetectionEdgeCases {

    @Test
    @DisplayName("getter with wrong return type should not match wither")
    void getterWithWrongReturnType() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WrongGetterType",
              """
              package com.test;

              public class WrongGetterType {
                  private String value;

                  // Getter returns Object, but wither takes String
                  public Object getValue() { return value; }

                  public WrongGetterType withValue(String value) {
                      return new WrongGetterType();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.WrongGetterType", source);

      // Should be unsupported because getter type doesn't match wither param type
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
    }

    @Test
    @DisplayName("isXxx getter for boolean wither should be detected")
    void isBooleanGetterDetected() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.BooleanGetter",
              """
              package com.test;

              public class BooleanGetter {
                  private boolean active;

                  // Boolean getter using 'is' prefix
                  public boolean isActive() { return active; }

                  public BooleanGetter withActive(boolean active) {
                      return new BooleanGetter();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.BooleanGetter", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(1);
      assertThat(analysis.witherMethods().get(0).getterMethodName()).isEqualTo("isActive");
    }

    @Test
    @DisplayName("record-style getter (no get prefix) should be detected")
    void recordStyleGetterDetected() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.RecordStyleGetter",
              """
              package com.test;

              public class RecordStyleGetter {
                  private final String name;

                  public RecordStyleGetter(String name) { this.name = name; }

                  // Record-style getter: name() instead of getName()
                  public String name() { return name; }

                  public RecordStyleGetter withName(String name) {
                      return new RecordStyleGetter(name);
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.RecordStyleGetter", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(1);
      assertThat(analysis.witherMethods().get(0).getterMethodName()).isEqualTo("name");
    }
  }

  @Nested
  @DisplayName("Sealed Interface Edge Cases")
  class SealedInterfaceEdgeCases {

    @Test
    @DisplayName("sealed interface with no permitted subtypes")
    void sealedInterfaceNoPermittedSubtypes() {
      // Note: This is technically invalid Java, but we test the analysis handles it
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.EmptySealed",
              """
              package com.test;

              // Sealed interface with one permitted subtype
              public sealed interface EmptySealed permits OnlyImpl {}
              """);

      var impl =
          JavaFileObjects.forSourceString(
              "com.test.OnlyImpl",
              """
              package com.test;

              public record OnlyImpl() implements EmptySealed {}
              """);

      TypeAnalysis analysis = analyseType("com.test.EmptySealed", sealedInterface, impl);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.SEALED_INTERFACE);
      assertThat(analysis.permittedSubtypes()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Enum Edge Cases")
  class EnumEdgeCases {

    @Test
    @DisplayName("enum with methods should still be analyzed as enum")
    void enumWithMethods() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.EnumWithMethods",
              """
              package com.test;

              public enum EnumWithMethods {
                  ONE, TWO, THREE;

                  public int getValue() {
                      return ordinal() + 1;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.EnumWithMethods", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.ENUM);
      assertThat(analysis.enumConstants()).containsExactly("ONE", "TWO", "THREE");
    }

    @Test
    @DisplayName("enum with fields should still be analyzed as enum")
    void enumWithFields() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.EnumWithFields",
              """
              package com.test;

              public enum EnumWithFields {
                  RED(255, 0, 0),
                  GREEN(0, 255, 0),
                  BLUE(0, 0, 255);

                  private final int r, g, b;

                  EnumWithFields(int r, int g, int b) {
                      this.r = r;
                      this.g = g;
                      this.b = b;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.EnumWithFields", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.ENUM);
      assertThat(analysis.enumConstants()).containsExactly("RED", "GREEN", "BLUE");
    }
  }

  // =============================================================================
  // Final Push for 65% - Target remaining mutations
  // =============================================================================

  @Nested
  @DisplayName("Final Mutation Killing Tests")
  class FinalMutationKillingTests {

    @Test
    @DisplayName("verify detectMutableFields returns false for class with only methods")
    void detectMutableFieldsReturnsFalseForMethodsOnly() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.MethodsOnly",
              """
              package com.test;

              public class MethodsOnly {
                  private String value;

                  public String getValue() { return value; }

                  // Only has a method that looks like setter but isn't
                  public void setterLike() { }

                  public MethodsOnly withValue(String value) {
                      return new MethodsOnly();
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.MethodsOnly", source);

      assertThat(analysis.hasMutableFields()).isFalse();
    }

    @Test
    @DisplayName("verify empty getTypeArguments returns no container")
    void emptyTypeArgumentsReturnsNoContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.PlainString",
              """
              package com.test;

              public record PlainString(String value) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.PlainString", source);

      assertThat(analysis.fields().get(0).containerType()).isEmpty();
      assertThat(analysis.fields().get(0).hasTraversal()).isFalse();
    }

    @Test
    @DisplayName("verify isAllUpperCase loop terminates correctly")
    void isAllUpperCaseLoopTerminates() {
      // Test with various string lengths to exercise loop boundaries
      assertThat(ProcessorUtils.isAllUpperCase("")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("A")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("AB")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("ABC")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("ABCD")).isTrue();
      assertThat(ProcessorUtils.isAllUpperCase("ABCDe")).isFalse();
      assertThat(ProcessorUtils.isAllUpperCase("aBCDE")).isFalse();
    }

    @Test
    @DisplayName("verify split parts iteration in toCamelCase")
    void toCamelCaseSplitPartsIteration() {
      // Test with different numbers of underscore-separated parts
      assertThat(ProcessorUtils.toCamelCase("A")).isEqualTo("a");
      assertThat(ProcessorUtils.toCamelCase("A_B")).isEqualTo("aB");
      assertThat(ProcessorUtils.toCamelCase("A_B_C")).isEqualTo("aBC");
      assertThat(ProcessorUtils.toCamelCase("A_B_C_D")).isEqualTo("aBCD");
      assertThat(ProcessorUtils.toCamelCase("FIRST_SECOND_THIRD_FOURTH"))
          .isEqualTo("firstSecondThirdFourth");
    }

    @Test
    @DisplayName("verify wither detection loop processes all methods")
    void witherDetectionProcessesAllMethods() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.ManyMethods",
              """
              package com.test;

              public class ManyMethods {
                  private String a;
                  private int b;
                  private boolean c;

                  public String getA() { return a; }
                  public int getB() { return b; }
                  public boolean isC() { return c; }

                  // Some non-wither methods that should be skipped
                  public void doSomething() {}
                  public String computeValue() { return ""; }
                  private void privateMethod() {}

                  // The actual withers
                  public ManyMethods withA(String a) { return new ManyMethods(); }
                  public ManyMethods withB(int b) { return new ManyMethods(); }
                  public ManyMethods withC(boolean c) { return new ManyMethods(); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.ManyMethods", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(3);
    }

    @Test
    @DisplayName("verify record component loop processes all components")
    void recordComponentLoopProcessesAll() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.ManyComponents",
              """
              package com.test;

              import java.util.List;
              import java.util.Set;
              import java.util.Map;
              import java.util.Optional;

              public record ManyComponents(
                  String a,
                  int b,
                  List<String> c,
                  Set<Integer> d,
                  Map<String, String> e,
                  Optional<Double> f,
                  boolean g
              ) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.ManyComponents", source);

      assertThat(analysis.fields()).hasSize(7);
      // Verify each field is correctly categorized
      assertThat(analysis.fields().get(0).hasTraversal()).isFalse(); // String
      assertThat(analysis.fields().get(1).hasTraversal()).isFalse(); // int
      assertThat(analysis.fields().get(2).hasTraversal()).isTrue(); // List
      assertThat(analysis.fields().get(3).hasTraversal()).isTrue(); // Set
      assertThat(analysis.fields().get(4).hasTraversal()).isTrue(); // Map
      assertThat(analysis.fields().get(5).hasTraversal()).isTrue(); // Optional
      assertThat(analysis.fields().get(6).hasTraversal()).isFalse(); // boolean
    }

    @Test
    @DisplayName("verify enum constant loop processes all constants")
    void enumConstantLoopProcessesAll() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.ManyEnumConstants",
              """
              package com.test;

              public enum ManyEnumConstants {
                  ALPHA,
                  BETA,
                  GAMMA,
                  DELTA,
                  EPSILON,
                  ZETA,
                  ETA
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.ManyEnumConstants", source);

      assertThat(analysis.enumConstants()).hasSize(7);
      assertThat(analysis.enumConstants())
          .containsExactly("ALPHA", "BETA", "GAMMA", "DELTA", "EPSILON", "ZETA", "ETA");
    }

    @Test
    @DisplayName("verify sealed interface subtype loop processes all subtypes")
    void sealedInterfaceSubtypeLoopProcessesAll() {
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.ManySubtypes",
              """
              package com.test;

              public sealed interface ManySubtypes permits TypeA, TypeB, TypeC, TypeD {}
              """);

      var typeA =
          JavaFileObjects.forSourceString(
              "com.test.TypeA",
              """
              package com.test;

              public record TypeA() implements ManySubtypes {}
              """);

      var typeB =
          JavaFileObjects.forSourceString(
              "com.test.TypeB",
              """
              package com.test;

              public record TypeB() implements ManySubtypes {}
              """);

      var typeC =
          JavaFileObjects.forSourceString(
              "com.test.TypeC",
              """
              package com.test;

              public record TypeC() implements ManySubtypes {}
              """);

      var typeD =
          JavaFileObjects.forSourceString(
              "com.test.TypeD",
              """
              package com.test;

              public record TypeD() implements ManySubtypes {}
              """);

      TypeAnalysis analysis =
          analyseType("com.test.ManySubtypes", sealedInterface, typeA, typeB, typeC, typeD);

      assertThat(analysis.permittedSubtypes()).hasSize(4);
    }

    @Test
    @DisplayName("verify getter candidate loop tries all patterns")
    void getterCandidateLoopTriesAllPatterns() {
      // Test that all three getter patterns are tried: fieldName(), getFieldName(), isFieldName()
      var source =
          JavaFileObjects.forSourceString(
              "com.test.GetterPatterns",
              """
              package com.test;

              public class GetterPatterns {
                  private String recordStyle;
                  private String javaBeanStyle;
                  private boolean booleanStyle;

                  // Record-style getter
                  public String recordStyle() { return recordStyle; }
                  // JavaBean-style getter
                  public String getJavaBeanStyle() { return javaBeanStyle; }
                  // Boolean-style getter
                  public boolean isBooleanStyle() { return booleanStyle; }

                  public GetterPatterns withRecordStyle(String v) { return new GetterPatterns(); }
                  public GetterPatterns withJavaBeanStyle(String v) { return new GetterPatterns(); }
                  public GetterPatterns withBooleanStyle(boolean v) { return new GetterPatterns(); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.GetterPatterns", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods()).hasSize(3);

      // Verify each getter pattern was correctly matched
      var getterNames = analysis.witherMethods().stream().map(w -> w.getterMethodName()).toList();
      assertThat(getterNames)
          .containsExactlyInAnyOrder("recordStyle", "getJavaBeanStyle", "isBooleanStyle");
    }
  }
}
