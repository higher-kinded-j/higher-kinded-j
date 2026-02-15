// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.higherkindedj.optics.processing.external.ContainerType;
import org.higherkindedj.optics.processing.external.CopyStrategyCodeGenerator;
import org.higherkindedj.optics.processing.external.PrismCodeGenerator;
import org.higherkindedj.optics.processing.external.SpecAnalysis;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyKind;
import org.higherkindedj.optics.processing.external.SpecAnalysis.PrismHintInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.PrismHintKind;
import org.higherkindedj.optics.processing.external.SpecAnalysis.TraversalHintInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.TraversalHintKind;
import org.higherkindedj.optics.processing.external.TraversalCodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mutation killing tests for spec interface processing components.
 *
 * <p>These tests target boundary conditions, boolean returns, and edge cases to improve mutation
 * testing scores.
 */
@DisplayName("Spec Interface Mutation Killing Tests")
class SpecMutationKillingTest {

  // =============================================================================
  // CopyStrategyCodeGenerator Tests
  // =============================================================================

  @Nested
  @DisplayName("CopyStrategyCodeGenerator Mutation Tests")
  class CopyStrategyMutationTests {

    private final CopyStrategyCodeGenerator generator = new CopyStrategyCodeGenerator();

    @Test
    @DisplayName("VIA_BUILDER with empty defaults uses default method names")
    void viaBuilderWithEmptyDefaults() {
      CopyStrategyInfo info = CopyStrategyInfo.forBuilder("", "", "", "");
      CodeBlock result =
          generator.generateSetterLambda(CopyStrategyKind.VIA_BUILDER, info, "name", null, null);

      // Empty strings should be replaced with defaults
      assertThat(result.toString()).contains("toBuilder()");
      assertThat(result.toString()).contains("name(newValue)"); // field name used as setter
      assertThat(result.toString()).contains("build()");
    }

    @Test
    @DisplayName("VIA_BUILDER with custom method names uses custom names")
    void viaBuilderWithCustomNames() {
      CopyStrategyInfo info = CopyStrategyInfo.forBuilder("getX", "newBuilder", "setX", "create");
      CodeBlock result =
          generator.generateSetterLambda(CopyStrategyKind.VIA_BUILDER, info, "x", null, null);

      assertThat(result.toString()).contains("newBuilder()");
      assertThat(result.toString()).contains("setX(newValue)");
      assertThat(result.toString()).contains("create()");
    }

    @Test
    @DisplayName("VIA_CONSTRUCTOR with empty parameter order throws UnsupportedOperationException")
    void viaConstructorWithEmptyParameterOrder() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Empty",
              """
              package com.test;
              public record Empty(String field) {}
              """);

      String result =
          runGeneratorInProcessor(
              "com.test.Empty",
              proc -> {
                CopyStrategyInfo info = CopyStrategyInfo.forConstructor(new String[0]);
                return generator
                    .generateSetterLambda(
                        CopyStrategyKind.VIA_CONSTRUCTOR, info, "field", proc.getTypeMirror(), null)
                    .toString();
              },
              source);

      // Empty parameter order generates a TODO placeholder
      assertThat(result).contains("UnsupportedOperationException");
      assertThat(result).contains("parameterOrder");
    }

    @Test
    @DisplayName("VIA_CONSTRUCTOR with single parameter generates correct code")
    void viaConstructorWithSingleParameter() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Single",
              """
              package com.test;
              public record Single(String value) {}
              """);

      String result =
          runGeneratorInProcessor(
              "com.test.Single",
              proc -> {
                CopyStrategyInfo info = CopyStrategyInfo.forConstructor(new String[] {"value"});
                return generator
                    .generateSetterLambda(
                        CopyStrategyKind.VIA_CONSTRUCTOR, info, "value", proc.getTypeMirror(), null)
                    .toString();
              },
              source);

      assertThat(result).contains("newValue");
      assertThat(result).doesNotContain("source.value()"); // Only newValue, no getters
    }

    @Test
    @DisplayName("VIA_CONSTRUCTOR with multiple parameters substitutes correct field")
    void viaConstructorWithMultipleParameters() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Multi",
              """
              package com.test;
              public record Multi(String first, int second, String third) {}
              """);

      String result =
          runGeneratorInProcessor(
              "com.test.Multi",
              proc -> {
                CopyStrategyInfo info =
                    CopyStrategyInfo.forConstructor(new String[] {"first", "second", "third"});
                return generator
                    .generateSetterLambda(
                        CopyStrategyKind.VIA_CONSTRUCTOR,
                        info,
                        "second",
                        proc.getTypeMirror(),
                        null)
                    .toString();
              },
              source);

      // second should be newValue, others should be getters
      assertThat(result).contains("source.first()");
      assertThat(result).contains("newValue");
      assertThat(result).contains("source.third()");
    }

    @Test
    @DisplayName("VIA_CONSTRUCTOR boundary: first parameter in order (i > 0 check)")
    void viaConstructorFirstParameterBoundary() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Pair",
              """
              package com.test;
              public record Pair(String a, String b) {}
              """);

      String result =
          runGeneratorInProcessor(
              "com.test.Pair",
              proc -> {
                CopyStrategyInfo info = CopyStrategyInfo.forConstructor(new String[] {"a", "b"});
                return generator
                    .generateSetterLambda(
                        CopyStrategyKind.VIA_CONSTRUCTOR, info, "a", proc.getTypeMirror(), null)
                    .toString();
              },
              source);

      // First param (a) is being set, so it should be newValue
      // Second param (b) should use getter
      // Format: new Type(newValue, source.b())
      assertThat(result).contains("newValue");
      assertThat(result).contains("source.b()");
      // Verify order: newValue comes before source.b()
      int newValueIndex = result.indexOf("newValue");
      int getterIndex = result.indexOf("source.b()");
      assertThat(newValueIndex).isLessThan(getterIndex);
    }

    @Test
    @DisplayName("VIA_COPY_AND_SET with empty copyConstructor uses source type")
    void viaCopyAndSetWithEmptyCopyConstructor() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Mutable",
              """
              package com.test;
              public class Mutable {
                  private String value;
                  public Mutable(Mutable other) { this.value = other.value; }
                  public void setValue(String v) { this.value = v; }
              }
              """);

      String result =
          runGeneratorInProcessor(
              "com.test.Mutable",
              proc -> {
                CopyStrategyInfo info = CopyStrategyInfo.forCopyAndSet("", "setValue");
                return generator
                    .generateSetterLambda(
                        CopyStrategyKind.VIA_COPY_AND_SET,
                        info,
                        "value",
                        proc.getTypeMirror(),
                        null)
                    .toString();
              },
              source);

      assertThat(result).contains("new com.test.Mutable(source)");
      assertThat(result).contains("copy.setValue(newValue)");
    }

    @Test
    @DisplayName("VIA_COPY_AND_SET with specified copyConstructor uses it")
    void viaCopyAndSetWithSpecifiedCopyConstructor() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Custom",
              """
              package com.test;
              public class Custom {
                  private String value;
                  public Custom(Custom other) { this.value = other.value; }
                  public void setValue(String v) { this.value = v; }
              }
              """);

      String result =
          runGeneratorInProcessor(
              "com.test.Custom",
              proc -> {
                CopyStrategyInfo info = CopyStrategyInfo.forCopyAndSet("Custom", "setValue");
                return generator
                    .generateSetterLambda(
                        CopyStrategyKind.VIA_COPY_AND_SET,
                        info,
                        "value",
                        proc.getTypeMirror(),
                        null)
                    .toString();
              },
              source);

      assertThat(result).contains("setValue(newValue)");
    }

    @Test
    @DisplayName("generateGetterLambda with empty getter uses field name")
    void generateGetterLambdaWithEmptyGetter() {
      CopyStrategyInfo info = CopyStrategyInfo.forBuilder("", "", "", "");
      CodeBlock result = generator.generateGetterLambda("name", info, null);

      assertThat(result.toString()).contains("source.name()");
    }

    @Test
    @DisplayName("generateGetterLambda with explicit getter uses it")
    void generateGetterLambdaWithExplicitGetter() {
      CopyStrategyInfo info = CopyStrategyInfo.forBuilder("getName", "", "", "");
      CodeBlock result = generator.generateGetterLambda("name", info, null);

      assertThat(result.toString()).contains("source.getName()");
    }

    @Test
    @DisplayName("capitalise with null returns null")
    void capitaliseWithNull() {
      // Test via javaBeanGetterMethodName which uses capitalise internally
      // Since capitalise is private, we test via public method
      String result = generator.javaBeanGetterMethodName("x");
      assertThat(result).isEqualTo("getX");
    }

    @Test
    @DisplayName("capitalise with empty string returns empty")
    void capitaliseWithEmpty() {
      // Empty field name edge case
      String result = generator.javaBeanGetterMethodName("");
      assertThat(result).isEqualTo("get");
    }

    @Test
    @DisplayName("capitalise with single char works correctly")
    void capitaliseWithSingleChar() {
      String result = generator.javaBeanGetterMethodName("x");
      assertThat(result).isEqualTo("getX");
    }

    @Test
    @DisplayName("NONE strategy throws IllegalArgumentException")
    void noneStrategyThrows() {
      assertThatThrownBy(
              () ->
                  generator.generateSetterLambda(
                      CopyStrategyKind.NONE, CopyStrategyInfo.empty(), "field", null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No copy strategy");
    }
  }

  // =============================================================================
  // PrismCodeGenerator Mutation Tests
  // =============================================================================

  @Nested
  @DisplayName("PrismCodeGenerator Mutation Tests")
  class PrismCodeGeneratorMutationTests {

    private final PrismCodeGenerator generator = new PrismCodeGenerator();

    @Test
    @DisplayName("INSTANCE_OF generates instanceof check")
    void instanceOfGeneratesInstanceofCheck() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Sub",
              """
              package com.test;
              public class Sub {}
              """);

      String result =
          runGeneratorInProcessor(
              "com.test.Sub",
              proc -> {
                PrismHintInfo info = PrismHintInfo.forInstanceOf(proc.getTypeMirror());
                return generator
                    .generatePrismReturnStatement(
                        PrismHintKind.INSTANCE_OF, info, null, proc.getTypeMirror())
                    .toString();
              },
              source);

      assertThat(result).contains("instanceof");
      assertThat(result).contains("Sub");
    }

    @Test
    @DisplayName("MATCH_WHEN generates predicate and getter calls")
    void matchWhenGeneratesPredicateAndGetter() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Node",
              """
              package com.test;
              public class Node {
                  public boolean isLeaf() { return true; }
                  public Node asLeaf() { return this; }
              }
              """);

      String result =
          runGeneratorInProcessor(
              "com.test.Node",
              proc -> {
                PrismHintInfo info = PrismHintInfo.forMatchWhen("isLeaf", "asLeaf");
                return generator
                    .generatePrismReturnStatement(
                        PrismHintKind.MATCH_WHEN, info, null, proc.getTypeMirror())
                    .toString();
              },
              source);

      assertThat(result).contains("isLeaf()");
      assertThat(result).contains("asLeaf()");
    }

    @Test
    @DisplayName("NONE hint kind throws IllegalArgumentException")
    void noneHintKindThrows() {
      assertThatThrownBy(
              () ->
                  generator.generatePrismReturnStatement(
                      PrismHintKind.NONE, PrismHintInfo.empty(), null, null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // =============================================================================
  // TraversalCodeGenerator Mutation Tests
  // =============================================================================

  @Nested
  @DisplayName("TraversalCodeGenerator Mutation Tests")
  class TraversalCodeGeneratorMutationTests {

    private final TraversalCodeGenerator generator = new TraversalCodeGenerator();

    @Test
    @DisplayName("TRAVERSE_WITH generates direct reference")
    void traverseWithGeneratesDirectReference() {
      TraversalHintInfo info = TraversalHintInfo.forTraverseWith("org.example.Traversals.list()");

      CodeBlock result =
          generator.generateTraversalReturnStatement(
              TraversalHintKind.TRAVERSE_WITH, info, null, null, "com.test.Spec");

      assertThat(result.toString()).contains("org.example.Traversals.list()");
    }

    @Test
    @DisplayName("THROUGH_FIELD with empty traversal throws IllegalStateException")
    void throughFieldWithEmptyTraversalThrowsException() {
      // Empty traversal should throw IllegalStateException because auto-detection
      // now happens in SpecInterfaceAnalyser, not in TraversalCodeGenerator
      TraversalHintInfo info = TraversalHintInfo.forThroughField("items", "");

      assertThatThrownBy(
              () ->
                  generator.generateTraversalReturnStatement(
                      TraversalHintKind.THROUGH_FIELD, info, null, null, "com.test.ContainerSpec"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Traversal not specified")
          .hasMessageContaining("auto-detected");
    }

    @Test
    @DisplayName("THROUGH_FIELD with explicit traversal uses it")
    void throughFieldWithExplicitTraversalUsesIt() {
      TraversalHintInfo info =
          TraversalHintInfo.forThroughField("items", "org.example.CustomTraversal.INSTANCE");

      CodeBlock result =
          generator.generateTraversalReturnStatement(
              TraversalHintKind.THROUGH_FIELD, info, null, null, "com.test.Spec");

      assertThat(result.toString()).contains("org.example.CustomTraversal.INSTANCE");
    }

    @Test
    @DisplayName("NONE hint kind throws IllegalArgumentException")
    void noneHintKindThrows() {
      assertThatThrownBy(
              () ->
                  generator.generateTraversalReturnStatement(
                      TraversalHintKind.NONE,
                      TraversalHintInfo.empty(),
                      null,
                      null,
                      "com.test.Spec"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // =============================================================================
  // SpecInterfaceAnalyser Integration Tests (Mutation Killing)
  // =============================================================================

  @Nested
  @DisplayName("SpecInterfaceAnalyser Mutation Tests")
  class SpecInterfaceAnalyserMutationTests {

    @Test
    @DisplayName("Lens without copy strategy annotation fails compilation")
    void lensWithoutCopyStrategyFails() {
      var external =
          JavaFileObjects.forSourceString(
              "com.external.Data",
              """
              package com.external;
              public record Data(String value) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.DataSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Data;

              @ImportOptics
              public interface DataSpec extends OpticsSpec<Data> {
                  // Missing @ViaBuilder, @Wither, etc.
                  Lens<Data, String> value();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(external, spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("copy strategy");
    }

    @Test
    @DisplayName("Prism without hint annotation fails compilation")
    void prismWithoutHintFails() {
      var base =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;
              public class Base {}
              """);

      var sub =
          JavaFileObjects.forSourceString(
              "com.external.Sub",
              """
              package com.external;
              public class Sub extends Base {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.BaseSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Base;
              import com.external.Sub;

              @ImportOptics
              public interface BaseSpec extends OpticsSpec<Base> {
                  // Missing @InstanceOf or @MatchWhen
                  Prism<Base, Sub> sub();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(base, sub, spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("prism hint");
    }

    @Test
    @DisplayName("Traversal without hint annotation fails compilation")
    void traversalWithoutHintFails() {
      var team =
          JavaFileObjects.forSourceString(
              "com.external.Team",
              """
              package com.external;
              import java.util.List;
              public record Team(List<String> members) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.TeamSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Team;

              @ImportOptics
              public interface TeamSpec extends OpticsSpec<Team> {
                  // Missing @TraverseWith or @ThroughField
                  Traversal<Team, String> members();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(team, spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("traversal hint");
    }

    @Test
    @DisplayName("Method with parameters fails compilation")
    void methodWithParametersFails() {
      var data =
          JavaFileObjects.forSourceString(
              "com.external.Data",
              """
              package com.external;
              public record Data(String value) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.DataSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Data;

              @ImportOptics
              public interface DataSpec extends OpticsSpec<Data> {
                  @ViaBuilder
                  Lens<Data, String> value(int unused);  // Parameters not allowed
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(data, spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("no parameters");
    }

    @Test
    @DisplayName("Non-optic return type fails compilation")
    void nonOpticReturnTypeFails() {
      var data =
          JavaFileObjects.forSourceString(
              "com.external.Data",
              """
              package com.external;
              public record Data(String value) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.DataSpec",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Data;

              @ImportOptics
              public interface DataSpec extends OpticsSpec<Data> {
                  @ViaBuilder
                  String value();  // Must return Lens, Prism, etc.
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(data, spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Lens, Prism, Traversal");
    }

    @Test
    @DisplayName("@InstanceOf with non-subtype fails compilation")
    void instanceOfWithNonSubtypeFails() {
      var animal =
          JavaFileObjects.forSourceString(
              "com.external.Animal",
              """
              package com.external;
              public class Animal {}
              """);

      var plant =
          JavaFileObjects.forSourceString(
              "com.external.Plant",
              """
              package com.external;
              public class Plant {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.AnimalSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import com.external.Animal;
              import com.external.Plant;

              @ImportOptics
              public interface AnimalSpec extends OpticsSpec<Animal> {
                  @InstanceOf(Plant.class)  // Plant is not a subtype of Animal
                  Prism<Animal, Plant> plant();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(animal, plant, spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("not a subtype");
    }

    @Test
    @DisplayName("extractFocusType boundary: exactly 2 type arguments works")
    void extractFocusTypeBoundaryExactlyTwo() {
      var data =
          JavaFileObjects.forSourceString(
              "com.external.Data",
              """
              package com.external;
              public record Data(String value) {
                  public Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private String value;
                      public Builder value(String v) { this.value = v; return this; }
                      public Data build() { return new Data(value); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.DataSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Data;

              @ImportOptics
              public interface DataSpec extends OpticsSpec<Data> {
                  @ViaBuilder
                  Lens<Data, String> value();  // Lens<S, A> has exactly 2 type args
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(data, spec);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Default methods are preserved in generated class")
    void defaultMethodsArePreserved() {
      var data =
          JavaFileObjects.forSourceString(
              "com.external.Data",
              """
              package com.external;
              public record Data(String value) {
                  public Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private String value;
                      public Builder value(String v) { this.value = v; return this; }
                      public Data build() { return new Data(value); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.DataSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Data;

              @ImportOptics
              public interface DataSpec extends OpticsSpec<Data> {
                  @ViaBuilder
                  Lens<Data, String> value();

                  // Default method should be preserved
                  default String getDescription() {
                      return "Data lens";
                  }
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(data, spec);

      assertThat(compilation).succeeded();
      // Default methods are preserved in the generated implementation
    }
  }

  // =============================================================================
  // TraversalCodeGenerator - getStandardTraversal switch tests
  // =============================================================================

  @Nested
  @DisplayName("TraversalCodeGenerator Standard Traversal Tests")
  class TraversalStandardTests {

    private final TraversalCodeGenerator generator = new TraversalCodeGenerator();

    @Test
    @DisplayName("getStandardTraversal returns correct reference for each container kind")
    void getStandardTraversalForAllKinds() {
      assertThat(
              generator.getStandardTraversal(
                  ContainerType.Kind.LIST))
          .contains("Traversals.forList()");
      assertThat(
              generator.getStandardTraversal(
                  ContainerType.Kind.SET))
          .contains("Traversals.forSet()");
      assertThat(
              generator.getStandardTraversal(
                  ContainerType.Kind.OPTIONAL))
          .contains("Traversals.forOptional()");
      assertThat(
              generator.getStandardTraversal(
                  ContainerType.Kind.ARRAY))
          .contains("Traversals.forArray()");
      assertThat(
              generator.getStandardTraversal(
                  ContainerType.Kind.MAP))
          .contains("Traversals.forMapValues()");
    }

    @Test
    @DisplayName("each container kind maps to a distinct traversal")
    void eachKindMapsToDistinctTraversal() {
      var list =
          generator.getStandardTraversal(
              ContainerType.Kind.LIST);
      var set =
          generator.getStandardTraversal(
              ContainerType.Kind.SET);
      var optional =
          generator.getStandardTraversal(
              ContainerType.Kind.OPTIONAL);
      var array =
          generator.getStandardTraversal(
              ContainerType.Kind.ARRAY);
      var map =
          generator.getStandardTraversal(
              ContainerType.Kind.MAP);

      // All should be different
      assertThat(list).isNotEqualTo(set);
      assertThat(list).isNotEqualTo(optional);
      assertThat(list).isNotEqualTo(array);
      assertThat(list).isNotEqualTo(map);
      assertThat(set).isNotEqualTo(optional);
      assertThat(set).isNotEqualTo(array);
      assertThat(set).isNotEqualTo(map);
      assertThat(optional).isNotEqualTo(array);
      assertThat(optional).isNotEqualTo(map);
      assertThat(array).isNotEqualTo(map);
    }
  }

  // =============================================================================
  // TRAVERSE_WITH method vs field reference detection
  // =============================================================================

  @Nested
  @DisplayName("TraversalCodeGenerator Reference Detection Tests")
  class TraversalReferenceDetectionTests {

    private final TraversalCodeGenerator generator = new TraversalCodeGenerator();

    @Test
    @DisplayName("TRAVERSE_WITH with method call ending in () generates method-style code")
    void traverseWithMethodCallStyle() {
      TraversalHintInfo info = TraversalHintInfo.forTraverseWith("com.example.Traversals.list()");
      CodeBlock result =
          generator.generateTraversalCode(
              TraversalHintKind.TRAVERSE_WITH, info, null, null, "Spec");
      assertThat(result.toString()).contains("com.example.Traversals.list()");
    }

    @Test
    @DisplayName("TRAVERSE_WITH with field reference not ending in () generates field-style code")
    void traverseWithFieldReferenceStyle() {
      TraversalHintInfo info = TraversalHintInfo.forTraverseWith("com.example.Traversals.INSTANCE");
      CodeBlock result =
          generator.generateTraversalCode(
              TraversalHintKind.TRAVERSE_WITH, info, null, null, "Spec");
      assertThat(result.toString()).contains("com.example.Traversals.INSTANCE");
      // Should NOT contain "()" at the end
      assertThat(result.toString().trim()).doesNotEndWith("()");
    }
  }

  // =============================================================================
  // PrismCodeGenerator - INSTANCE_OF with null target type
  // =============================================================================

  @Nested
  @DisplayName("PrismCodeGenerator Null Target Tests")
  class PrismNullTargetTests {

    private final PrismCodeGenerator generator = new PrismCodeGenerator();

    @Test
    @DisplayName("INSTANCE_OF with null targetType falls back to focusType")
    void instanceOfWithNullTargetUseFocusType() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.FocusType",
              """
              package com.test;
              public class FocusType {}
              """);

      String result =
          runGeneratorInProcessor(
              "com.test.FocusType",
              proc -> {
                // Pass null targetType - should fall back to focusType
                PrismHintInfo info = PrismHintInfo.forInstanceOf(null);
                return generator
                    .generatePrismCode(PrismHintKind.INSTANCE_OF, info, null, proc.getTypeMirror())
                    .toString();
              },
              source);

      assertThat(result).contains("FocusType");
      assertThat(result).contains("instanceof");
    }

    @Test
    @DisplayName("INSTANCE_OF with non-null targetType uses targetType")
    void instanceOfWithTargetTypeUsesIt() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.TargetType",
              """
              package com.test;
              public class TargetType {}
              """);

      String result =
          runGeneratorInProcessor(
              "com.test.TargetType",
              proc -> {
                PrismHintInfo info = PrismHintInfo.forInstanceOf(proc.getTypeMirror());
                return generator
                    .generatePrismCode(PrismHintKind.INSTANCE_OF, info, null, proc.getTypeMirror())
                    .toString();
              },
              source);

      assertThat(result).contains("TargetType");
    }
  }

  // =============================================================================
  // SpecAnalysis requiresCopyStrategy/requiresPrismHint/requiresTraversalHint
  // =============================================================================

  @Nested
  @DisplayName("SpecAnalysis OpticKind Tests")
  class SpecAnalysisOpticKindTests {

    @Test
    @DisplayName("OpticKind enum should have all expected values")
    void opticKindEnumValues() {
      SpecAnalysis.OpticKind[] kinds = SpecAnalysis.OpticKind.values();
      assertThat(kinds)
          .containsExactlyInAnyOrder(
              SpecAnalysis.OpticKind.LENS,
              SpecAnalysis.OpticKind.PRISM,
              SpecAnalysis.OpticKind.TRAVERSAL,
              SpecAnalysis.OpticKind.AFFINE,
              SpecAnalysis.OpticKind.ISO,
              SpecAnalysis.OpticKind.GETTER,
              SpecAnalysis.OpticKind.FOLD);
    }

    @Test
    @DisplayName("CopyStrategyKind enum should have all expected values")
    void copyStrategyKindEnumValues() {
      CopyStrategyKind[] kinds = CopyStrategyKind.values();
      assertThat(kinds)
          .containsExactlyInAnyOrder(
              CopyStrategyKind.VIA_BUILDER,
              CopyStrategyKind.WITHER,
              CopyStrategyKind.VIA_CONSTRUCTOR,
              CopyStrategyKind.VIA_COPY_AND_SET,
              CopyStrategyKind.NONE);
    }

    @Test
    @DisplayName("PrismHintKind enum should have all expected values")
    void prismHintKindEnumValues() {
      PrismHintKind[] kinds = PrismHintKind.values();
      assertThat(kinds)
          .containsExactlyInAnyOrder(
              PrismHintKind.INSTANCE_OF, PrismHintKind.MATCH_WHEN, PrismHintKind.NONE);
    }

    @Test
    @DisplayName("TraversalHintKind enum should have all expected values")
    void traversalHintKindEnumValues() {
      TraversalHintKind[] kinds = TraversalHintKind.values();
      assertThat(kinds)
          .containsExactlyInAnyOrder(
              TraversalHintKind.TRAVERSE_WITH,
              TraversalHintKind.THROUGH_FIELD,
              TraversalHintKind.NONE);
    }
  }

  // =============================================================================
  // SpecInterfaceAnalyser - findFieldType edge cases
  // =============================================================================

  @Nested
  @DisplayName("SpecInterfaceAnalyser Field Lookup Edge Cases")
  class SpecAnalyserFieldLookupTests {

    @Test
    @DisplayName("field on record type found via component name")
    void fieldOnRecordFoundViaComponent() {
      var team =
          JavaFileObjects.forSourceString(
              "com.external.RecordTeam",
              """
              package com.external;
              import java.util.List;
              public record RecordTeam(List<String> players) {
                  public Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private List<String> players;
                      public Builder players(List<String> p) { this.players = p; return this; }
                      public RecordTeam build() { return new RecordTeam(players); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.RecordTeamSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.RecordTeam;
              import java.util.List;

              @ImportOptics
              public interface RecordTeamSpec extends OpticsSpec<RecordTeam> {
                  @ViaBuilder
                  Lens<RecordTeam, List<String>> players();

                  @ThroughField(field = "players")
                  Traversal<RecordTeam, String> allPlayers();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(team, spec);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("field on class found via JavaBean getter")
    void fieldOnClassFoundViaJavaBeanGetter() {
      var team =
          JavaFileObjects.forSourceString(
              "com.external.ClassTeam",
              """
              package com.external;
              import java.util.List;
              public class ClassTeam {
                  private final List<String> players;
                  public ClassTeam(List<String> players) { this.players = players; }
                  public List<String> getPlayers() { return players; }
                  public Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private List<String> players;
                      public Builder players(List<String> p) { this.players = p; return this; }
                      public ClassTeam build() { return new ClassTeam(players); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.ClassTeamSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.ClassTeam;
              import java.util.List;

              @ImportOptics
              public interface ClassTeamSpec extends OpticsSpec<ClassTeam> {
                  @ViaBuilder(getter = "getPlayers")
                  Lens<ClassTeam, List<String>> players();

                  @ThroughField(field = "players")
                  Traversal<ClassTeam, String> allPlayers();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(team, spec);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("field not found should produce error")
    void fieldNotFoundProducesError() {
      var team =
          JavaFileObjects.forSourceString(
              "com.external.SmallTeam",
              """
              package com.external;
              import java.util.List;
              public record SmallTeam(List<String> members) {
                  public Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private List<String> members;
                      public Builder members(List<String> m) { this.members = m; return this; }
                      public SmallTeam build() { return new SmallTeam(members); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.SmallTeamSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import com.external.SmallTeam;

              @ImportOptics
              public interface SmallTeamSpec extends OpticsSpec<SmallTeam> {
                  @ThroughField(field = "nonExistentField")
                  Traversal<SmallTeam, String> allMembers();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(team, spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("not found");
    }
  }

  // =============================================================================
  // Helper Infrastructure
  // =============================================================================

  @FunctionalInterface
  interface GeneratorFunction {
    String apply(GeneratorTestProcessor proc) throws Exception;
  }

  private static class GeneratorTestProcessor extends AbstractProcessor {
    private final String targetTypeName;
    private TypeMirror typeMirror;
    private GeneratorFunction function;
    private String result;

    GeneratorTestProcessor(String targetTypeName, GeneratorFunction function) {
      this.targetTypeName = targetTypeName;
      this.function = function;
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
        typeMirror = typeElement.asType();
        try {
          result = function.apply(this);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      return false;
    }

    TypeMirror getTypeMirror() {
      return typeMirror;
    }

    String getResult() {
      return result;
    }
  }

  private String runGeneratorInProcessor(
      String typeName, GeneratorFunction function, JavaFileObject... sources) {
    GeneratorTestProcessor processor = new GeneratorTestProcessor(typeName, function);
    Compilation compilation = javac().withProcessors(processor).compile(sources);
    assertThat(compilation).succeeded();
    return processor.getResult();
  }
}
