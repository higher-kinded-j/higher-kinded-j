// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TypeKindAnalyser}.
 *
 * <p>These tests verify that the analyser correctly identifies different type kinds and extracts
 * the appropriate information for optics generation.
 */
@DisplayName("TypeKindAnalyser")
class TypeKindAnalyserTest {

  /**
   * A helper processor that runs the TypeKindAnalyser and captures the result. This allows us to
   * test the analyser within the annotation processing environment.
   */
  private static class AnalyserTestProcessor extends AbstractProcessor {
    private final String targetTypeName;
    private TypeAnalysis result;

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
        TypeKindAnalyser analyser = new TypeKindAnalyser(processingEnv.getTypeUtils());
        result = analyser.analyseType(typeElement);
      }

      return false;
    }

    TypeAnalysis getResult() {
      return result;
    }
  }

  private TypeAnalysis analyseType(String typeName, JavaFileObject... sources) {
    AnalyserTestProcessor processor = new AnalyserTestProcessor(typeName);
    Compilation compilation = javac().withProcessors(processor).compile(sources);
    assertThat(compilation).succeeded();
    return processor.getResult();
  }

  @Nested
  @DisplayName("Record Analysis")
  class RecordAnalysis {

    @Test
    @DisplayName("should identify record type")
    void shouldIdentifyRecord() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.TestRecord",
              """
              package com.test;

              public record TestRecord(String name, int age) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.TestRecord", source);

      assertThat(analysis).isNotNull();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.RECORD);
      assertThat(analysis.supportsLenses()).isTrue();
      assertThat(analysis.supportsPrisms()).isFalse();
    }

    @Test
    @DisplayName("should extract field info from record")
    void shouldExtractFieldInfo() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;

              public record Person(String firstName, String lastName, int age) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.Person", source);

      assertThat(analysis.fields()).hasSize(3);
      assertThat(analysis.fields().get(0).name()).isEqualTo("firstName");
      assertThat(analysis.fields().get(1).name()).isEqualTo("lastName");
      assertThat(analysis.fields().get(2).name()).isEqualTo("age");
    }

    @Test
    @DisplayName("should detect container fields in record")
    void shouldDetectContainerFields() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Order",
              """
              package com.test;

              import java.util.List;

              public record Order(String id, List<String> items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.Order", source);

      assertThat(analysis.fields()).hasSize(2);
      assertThat(analysis.fields().get(0).hasTraversal()).isFalse();
      assertThat(analysis.fields().get(1).hasTraversal()).isTrue();
      assertThat(analysis.fields().get(1).containerType()).isPresent();
      assertThat(analysis.fields().get(1).containerType().get().kind())
          .isEqualTo(ContainerType.Kind.LIST);
    }
  }

  @Nested
  @DisplayName("Sealed Interface Analysis")
  class SealedInterfaceAnalysis {

    @Test
    @DisplayName("should identify sealed interface")
    void shouldIdentifySealedInterface() {
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.Result",
              """
              package com.test;

              public sealed interface Result permits Success, Failure {}
              """);

      var success =
          JavaFileObjects.forSourceString(
              "com.test.Success",
              """
              package com.test;

              public record Success(String value) implements Result {}
              """);

      var failure =
          JavaFileObjects.forSourceString(
              "com.test.Failure",
              """
              package com.test;

              public record Failure(String error) implements Result {}
              """);

      TypeAnalysis analysis = analyseType("com.test.Result", sealedInterface, success, failure);

      assertThat(analysis).isNotNull();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.SEALED_INTERFACE);
      assertThat(analysis.supportsLenses()).isFalse();
      assertThat(analysis.supportsPrisms()).isTrue();
    }

    @Test
    @DisplayName("should extract permitted subtypes")
    void shouldExtractPermittedSubtypes() {
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.Shape",
              """
              package com.test;

              public sealed interface Shape permits Circle, Square, Triangle {}
              """);

      var circle =
          JavaFileObjects.forSourceString(
              "com.test.Circle",
              """
              package com.test;

              public record Circle(double radius) implements Shape {}
              """);

      var square =
          JavaFileObjects.forSourceString(
              "com.test.Square",
              """
              package com.test;

              public record Square(double side) implements Shape {}
              """);

      var triangle =
          JavaFileObjects.forSourceString(
              "com.test.Triangle",
              """
              package com.test;

              public record Triangle(double base, double height) implements Shape {}
              """);

      TypeAnalysis analysis =
          analyseType("com.test.Shape", sealedInterface, circle, square, triangle);

      assertThat(analysis.permittedSubtypes()).hasSize(3);
      assertThat(analysis.permittedSubtypes())
          .extracting(te -> te.getSimpleName().toString())
          .containsExactly("Circle", "Square", "Triangle");
    }
  }

  @Nested
  @DisplayName("Enum Analysis")
  class EnumAnalysis {

    @Test
    @DisplayName("should identify enum type")
    void shouldIdentifyEnum() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Colour",
              """
              package com.test;

              public enum Colour { RED, GREEN, BLUE }
              """);

      TypeAnalysis analysis = analyseType("com.test.Colour", source);

      assertThat(analysis).isNotNull();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.ENUM);
      assertThat(analysis.supportsLenses()).isFalse();
      assertThat(analysis.supportsPrisms()).isTrue();
    }

    @Test
    @DisplayName("should extract enum constants")
    void shouldExtractEnumConstants() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Day",
              """
              package com.test;

              public enum Day { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }
              """);

      TypeAnalysis analysis = analyseType("com.test.Day", source);

      assertThat(analysis.enumConstants())
          .containsExactly(
              "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    }
  }

  @Nested
  @DisplayName("Wither Class Analysis")
  class WitherClassAnalysis {

    @Test
    @DisplayName("should identify class with wither methods")
    void shouldIdentifyWitherClass() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.ImmutablePoint",
              """
              package com.test;

              public final class ImmutablePoint {
                  private final int x;
                  private final int y;

                  public ImmutablePoint(int x, int y) {
                      this.x = x;
                      this.y = y;
                  }

                  public int getX() { return x; }
                  public int getY() { return y; }

                  public ImmutablePoint withX(int x) { return new ImmutablePoint(x, this.y); }
                  public ImmutablePoint withY(int y) { return new ImmutablePoint(this.x, y); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.ImmutablePoint", source);

      assertThat(analysis).isNotNull();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.supportsLenses()).isTrue();
      assertThat(analysis.supportsPrisms()).isFalse();
    }

    @Test
    @DisplayName("should extract wither method info")
    void shouldExtractWitherInfo() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Config",
              """
              package com.test;

              public final class Config {
                  private final String host;
                  private final int port;

                  public Config(String host, int port) {
                      this.host = host;
                      this.port = port;
                  }

                  public String getHost() { return host; }
                  public int getPort() { return port; }

                  public Config withHost(String host) { return new Config(host, this.port); }
                  public Config withPort(int port) { return new Config(this.host, port); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.Config", source);

      assertThat(analysis.witherMethods()).hasSize(2);
      assertThat(analysis.witherMethods().get(0).fieldName()).isEqualTo("host");
      assertThat(analysis.witherMethods().get(0).witherMethodName()).isEqualTo("withHost");
      assertThat(analysis.witherMethods().get(0).getterMethodName()).isEqualTo("getHost");
    }

    @Test
    @DisplayName("should detect mutable fields on wither class")
    void shouldDetectMutableFields() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.MutableConfig",
              """
              package com.test;

              public class MutableConfig {
                  private String host;
                  private int port;

                  public String getHost() { return host; }
                  public void setHost(String host) { this.host = host; }

                  public int getPort() { return port; }
                  public void setPort(int port) { this.port = port; }

                  public MutableConfig withHost(String host) {
                      MutableConfig copy = new MutableConfig();
                      copy.host = host;
                      copy.port = this.port;
                      return copy;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.MutableConfig", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.hasMutableFields()).isTrue();
    }
  }

  @Nested
  @DisplayName("Unsupported Type Analysis")
  class UnsupportedTypeAnalysis {

    @Test
    @DisplayName("should identify class without withers as unsupported")
    void shouldIdentifyUnsupportedClass() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.PlainClass",
              """
              package com.test;

              public class PlainClass {
                  private String value;

                  public String getValue() { return value; }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.PlainClass", source);

      assertThat(analysis).isNotNull();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      assertThat(analysis.supportsLenses()).isFalse();
      assertThat(analysis.supportsPrisms()).isFalse();
    }

    @Test
    @DisplayName("should detect mutable class as unsupported with mutable flag")
    void shouldDetectMutableClassAsUnsupported() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.MutablePojo",
              """
              package com.test;

              public class MutablePojo {
                  private String name;

                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.MutablePojo", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      assertThat(analysis.hasMutableFields()).isTrue();
    }
  }

  @Nested
  @DisplayName("Container Type Detection")
  class ContainerTypeDetection {

    @Test
    @DisplayName("should detect List container")
    void shouldDetectListContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithList",
              """
              package com.test;

              import java.util.List;

              public record WithList(List<String> items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithList", source);

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      assertThat(analysis.fields().getFirst().containerType().get().kind())
          .isEqualTo(ContainerType.Kind.LIST);
    }

    @Test
    @DisplayName("should detect Set container")
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

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      assertThat(analysis.fields().getFirst().containerType().get().kind())
          .isEqualTo(ContainerType.Kind.SET);
    }

    @Test
    @DisplayName("should detect Optional container")
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

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      assertThat(analysis.fields().getFirst().containerType().get().kind())
          .isEqualTo(ContainerType.Kind.OPTIONAL);
    }

    @Test
    @DisplayName("should detect array container")
    void shouldDetectArrayContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithArray",
              """
              package com.test;

              public record WithArray(int[] values) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithArray", source);

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      assertThat(analysis.fields().getFirst().containerType().get().kind())
          .isEqualTo(ContainerType.Kind.ARRAY);
    }

    @Test
    @DisplayName("should detect Map container")
    void shouldDetectMapContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithMap",
              """
              package com.test;

              import java.util.Map;

              public record WithMap(Map<String, Integer> mapping) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithMap", source);

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      ContainerType container = analysis.fields().getFirst().containerType().get();
      assertThat(container.kind()).isEqualTo(ContainerType.Kind.MAP);
      assertThat(container.isMap()).isTrue();
    }
  }

  @Nested
  @DisplayName("Container Type Detection With Subtypes")
  class ContainerTypeDetectionWithSubtypes {

    /**
     * A helper processor that runs detectContainerTypeWithSubtypes and captures the result. This
     * allows us to test the method within the annotation processing environment.
     */
    private static class SubtypeDetectionProcessor extends AbstractProcessor {
      private final String targetTypeName;
      private final String fieldName;
      private Optional<ContainerType> result;

      SubtypeDetectionProcessor(String targetTypeName, String fieldName) {
        this.targetTypeName = targetTypeName;
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

        TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(targetTypeName);
        if (typeElement != null) {
          // Find the field's type
          for (var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD
                && enclosed.getSimpleName().contentEquals(fieldName)) {
              TypeKindAnalyser analyser = new TypeKindAnalyser(processingEnv.getTypeUtils());
              result =
                  analyser.detectContainerTypeWithSubtypes(
                      ((VariableElement) enclosed).asType(), processingEnv.getElementUtils());
              break;
            }
          }
          // Also check record components
          if (typeElement.getKind() == ElementKind.RECORD) {
            for (var component : typeElement.getRecordComponents()) {
              if (component.getSimpleName().contentEquals(fieldName)) {
                TypeKindAnalyser analyser = new TypeKindAnalyser(processingEnv.getTypeUtils());
                result =
                    analyser.detectContainerTypeWithSubtypes(
                        component.asType(), processingEnv.getElementUtils());
                break;
              }
            }
          }
        }

        return false;
      }

      Optional<ContainerType> getResult() {
        return result;
      }
    }

    private Optional<ContainerType> detectContainerType(
        String typeName, String fieldName, JavaFileObject... sources) {
      SubtypeDetectionProcessor processor = new SubtypeDetectionProcessor(typeName, fieldName);
      Compilation compilation = javac().withProcessors(processor).compile(sources);
      assertThat(compilation).succeeded();
      return processor.getResult();
    }

    @Test
    @DisplayName("should detect ArrayList as List")
    void shouldDetectArrayListAsList() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithArrayList",
              """
              package com.test;

              import java.util.ArrayList;

              public record WithArrayList(ArrayList<String> items) {}
              """);

      var result = detectContainerType("com.test.WithArrayList", "items", source);

      assertThat(result).isPresent();
      assertThat(result.get().kind()).isEqualTo(ContainerType.Kind.LIST);
    }

    @Test
    @DisplayName("should detect LinkedList as List")
    void shouldDetectLinkedListAsList() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithLinkedList",
              """
              package com.test;

              import java.util.LinkedList;

              public record WithLinkedList(LinkedList<String> items) {}
              """);

      var result = detectContainerType("com.test.WithLinkedList", "items", source);

      assertThat(result).isPresent();
      assertThat(result.get().kind()).isEqualTo(ContainerType.Kind.LIST);
    }

    @Test
    @DisplayName("should detect HashSet as Set")
    void shouldDetectHashSetAsSet() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithHashSet",
              """
              package com.test;

              import java.util.HashSet;

              public record WithHashSet(HashSet<String> items) {}
              """);

      var result = detectContainerType("com.test.WithHashSet", "items", source);

      assertThat(result).isPresent();
      assertThat(result.get().kind()).isEqualTo(ContainerType.Kind.SET);
    }

    @Test
    @DisplayName("should detect TreeSet as Set")
    void shouldDetectTreeSetAsSet() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithTreeSet",
              """
              package com.test;

              import java.util.TreeSet;

              public record WithTreeSet(TreeSet<String> items) {}
              """);

      var result = detectContainerType("com.test.WithTreeSet", "items", source);

      assertThat(result).isPresent();
      assertThat(result.get().kind()).isEqualTo(ContainerType.Kind.SET);
    }

    @Test
    @DisplayName("should detect HashMap as Map")
    void shouldDetectHashMapAsMap() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithHashMap",
              """
              package com.test;

              import java.util.HashMap;

              public record WithHashMap(HashMap<String, Integer> items) {}
              """);

      var result = detectContainerType("com.test.WithHashMap", "items", source);

      assertThat(result).isPresent();
      assertThat(result.get().kind()).isEqualTo(ContainerType.Kind.MAP);
    }

    @Test
    @DisplayName("should return empty for raw List type")
    void shouldReturnEmptyForRawListType() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithRawList",
              """
              package com.test;

              import java.util.List;

              @SuppressWarnings("rawtypes")
              public record WithRawList(List items) {}
              """);

      var result = detectContainerType("com.test.WithRawList", "items", source);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty for non-container type")
    void shouldReturnEmptyForNonContainerType() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithString",
              """
              package com.test;

              public record WithString(String value) {}
              """);

      var result = detectContainerType("com.test.WithString", "value", source);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should detect LinkedHashSet as Set")
    void shouldDetectLinkedHashSetAsSet() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithLinkedHashSet",
              """
              package com.test;

              import java.util.LinkedHashSet;

              public record WithLinkedHashSet(LinkedHashSet<String> items) {}
              """);

      var result = detectContainerType("com.test.WithLinkedHashSet", "items", source);

      assertThat(result).isPresent();
      assertThat(result.get().kind()).isEqualTo(ContainerType.Kind.SET);
    }

    @Test
    @DisplayName("should detect TreeMap as Map")
    void shouldDetectTreeMapAsMap() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithTreeMap",
              """
              package com.test;

              import java.util.TreeMap;

              public record WithTreeMap(TreeMap<String, Integer> items) {}
              """);

      var result = detectContainerType("com.test.WithTreeMap", "items", source);

      assertThat(result).isPresent();
      assertThat(result.get().kind()).isEqualTo(ContainerType.Kind.MAP);
    }

    @Test
    @DisplayName("should return empty for raw Set type")
    void shouldReturnEmptyForRawSetType() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithRawSet",
              """
              package com.test;

              import java.util.Set;

              @SuppressWarnings("rawtypes")
              public record WithRawSet(Set items) {}
              """);

      var result = detectContainerType("com.test.WithRawSet", "items", source);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty for raw Map type")
    void shouldReturnEmptyForRawMapType() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithRawMap",
              """
              package com.test;

              import java.util.Map;

              @SuppressWarnings("rawtypes")
              public record WithRawMap(Map items) {}
              """);

      var result = detectContainerType("com.test.WithRawMap", "items", source);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty for raw Optional type")
    void shouldReturnEmptyForRawOptionalType() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithRawOptional",
              """
              package com.test;

              import java.util.Optional;

              @SuppressWarnings("rawtypes")
              public record WithRawOptional(Optional value) {}
              """);

      var result = detectContainerType("com.test.WithRawOptional", "value", source);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should detect Object array")
    void shouldDetectObjectArray() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithObjectArray",
              """
              package com.test;

              public record WithObjectArray(Object[] items) {}
              """);

      var result = detectContainerType("com.test.WithObjectArray", "items", source);

      assertThat(result).isPresent();
      assertThat(result.get().kind()).isEqualTo(ContainerType.Kind.ARRAY);
    }

    @Test
    @DisplayName("should preserve element type for arrays")
    void shouldPreserveElementTypeForArrays() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithIntegerArray",
              """
              package com.test;

              public record WithIntegerArray(Integer[] values) {}
              """);

      var result = detectContainerType("com.test.WithIntegerArray", "values", source);

      assertThat(result).isPresent();
      assertThat(result.get().kind()).isEqualTo(ContainerType.Kind.ARRAY);
      assertThat(result.get().elementType()).isNotNull();
    }

    @Test
    @DisplayName("should detect CopyOnWriteArrayList as List")
    void shouldDetectCopyOnWriteArrayListAsList() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithCopyOnWriteArrayList",
              """
              package com.test;

              import java.util.concurrent.CopyOnWriteArrayList;

              public record WithCopyOnWriteArrayList(CopyOnWriteArrayList<String> items) {}
              """);

      var result = detectContainerType("com.test.WithCopyOnWriteArrayList", "items", source);

      assertThat(result).isPresent();
      assertThat(result.get().kind()).isEqualTo(ContainerType.Kind.LIST);
    }
  }
}
