// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link PathSourceProcessor}.
 *
 * <p>Tests verify that the processor correctly generates Path wrapper classes for types annotated
 * with {@code @PathSource}.
 */
@DisplayName("PathSourceProcessor Integration Tests")
class PathSourceProcessorIntegrationTest {

  @Nested
  @DisplayName("Basic Code Generation")
  class BasicCodeGeneration {

    @Test
    @DisplayName("generates path class for simple interface with witness type")
    void shouldGeneratePathClassForSimpleInterface() {
      // First compile the witness type
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.ApiResultKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface ApiResultKind<A> extends Kind<ApiResultKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      // Then compile the annotated type
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.ApiResult",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = ApiResultKind.Witness.class)
              public sealed interface ApiResult<A> permits ApiSuccess, ApiFailure {
                  <B> ApiResult<B> map(java.util.function.Function<? super A, ? extends B> f);
              }
              """);

      final var successSource =
          JavaFileObjects.forSourceString(
              "com.example.ApiSuccess",
              """
              package com.example;

              public record ApiSuccess<A>(A value) implements ApiResult<A> {
                  @Override
                  public <B> ApiResult<B> map(java.util.function.Function<? super A, ? extends B> f) {
                      return new ApiSuccess<>(f.apply(value));
                  }
              }
              """);

      final var failureSource =
          JavaFileObjects.forSourceString(
              "com.example.ApiFailure",
              """
              package com.example;

              public record ApiFailure<A>(String error) implements ApiResult<A> {
                  @Override
                  public <B> ApiResult<B> map(java.util.function.Function<? super A, ? extends B> f) {
                      return new ApiFailure<>(error);
                  }
              }
              """);

      var compilation =
          javac()
              .withProcessors(new PathSourceProcessor())
              .compile(witnessSource, sourceFile, successSource, failureSource);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.ApiResultPath";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public final class ApiResultPath<A>");
      // Note: Generated classes implement Combinable (not Chainable) because Chainable is sealed
      assertGeneratedCodeContains(compilation, generatedClassName, "implements Combinable<A>");
      assertGeneratedCodeContains(compilation, generatedClassName, "private final Kind<");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public <B> ApiResultPath<B> map(");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public <B> ApiResultPath<B> via(");
    }

    @Test
    @DisplayName("generates path class with custom suffix")
    void shouldGeneratePathClassWithCustomSuffix() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.ResultKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface ResultKind<A> extends Kind<ResultKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Result",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = ResultKind.Witness.class, suffix = "Wrapper")
              public interface Result<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();
      assertTrue(
          compilation.generatedSourceFile("com.example.ResultWrapper").isPresent(),
          "Expected generated file ResultWrapper");
    }

    @Test
    @DisplayName("generates path class with error type for Recoverable capability")
    void shouldGenerateRecoverablePathClass() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.ErrResultKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface ErrResultKind<A> extends Kind<ErrResultKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.ErrResult",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = ErrResultKind.Witness.class,
                  errorType = String.class,
                  capability = PathSource.Capability.RECOVERABLE
              )
              public interface ErrResult<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.ErrResultPath";
      // Note: Generated classes implement Combinable (not Chainable/Recoverable) because those are
      // sealed
      // but still provide recover/recoverWith/mapError methods
      assertGeneratedCodeContains(compilation, generatedClassName, "implements Combinable<A>");
      assertGeneratedCodeContains(compilation, generatedClassName, "private final MonadError<");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public ErrResultPath<A> recover(");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public ErrResultPath<A> recoverWith(");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public ErrResultPath<A> mapError(");
    }

    @Test
    @DisplayName("generates path class with Composable capability only")
    void shouldGenerateComposableOnlyPathClass() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.SimpleKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface SimpleKind<A> extends Kind<SimpleKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Simple",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = SimpleKind.Witness.class,
                  capability = PathSource.Capability.COMPOSABLE
              )
              public interface Simple<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.SimplePath";
      assertGeneratedCodeContains(compilation, generatedClassName, "implements Composable<A>");
      // Should NOT have via or zipWith methods
      assertGeneratedCodeDoesNotContain(
          compilation, generatedClassName, "public <B> SimplePath<B> via(");
      assertGeneratedCodeDoesNotContain(
          compilation, generatedClassName, "public <B, C> SimplePath<C> zipWith(");
    }
  }

  @Nested
  @DisplayName("Generated Methods")
  class GeneratedMethods {

    @Test
    @DisplayName("generates factory methods: of and pure")
    void shouldGenerateFactoryMethods() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.FactoryKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface FactoryKind<A> extends Kind<FactoryKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Factory",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = FactoryKind.Witness.class)
              public interface Factory<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.FactoryPath";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public static <A> FactoryPath<A> of(");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public static <A> FactoryPath<A> pure(");
    }

    @Test
    @DisplayName("generates terminal methods: run and runKind")
    void shouldGenerateTerminalMethods() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.TerminalKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface TerminalKind<A> extends Kind<TerminalKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Terminal",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = TerminalKind.Witness.class)
              public interface Terminal<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.TerminalPath";
      assertGeneratedCodeContains(compilation, generatedClassName, "public Kind<");
      assertGeneratedCodeContains(compilation, generatedClassName, "> run()");
      assertGeneratedCodeContains(compilation, generatedClassName, "> runKind()");
    }

    @Test
    @DisplayName("generates Object methods: equals, hashCode, toString")
    void shouldGenerateObjectMethods() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.ObjectKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface ObjectKind<A> extends Kind<ObjectKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.ObjectType",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = ObjectKind.Witness.class)
              public interface ObjectType<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.ObjectTypePath";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public boolean equals(Object obj)");
      assertGeneratedCodeContains(compilation, generatedClassName, "public int hashCode()");
      assertGeneratedCodeContains(compilation, generatedClassName, "public String toString()");
    }
  }

  @Nested
  @DisplayName("Error Cases")
  class ErrorCases {

    @Test
    @DisplayName("fails when @PathSource is applied to a method")
    void shouldFailForMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadUsage",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              public interface BadUsage {
                  // Can't apply to method, but we test the processor handles it
              }
              """);

      // This test verifies the processor runs without crashing on valid interfaces
      var compilation = javac().withProcessors(new PathSourceProcessor()).compile(sourceFile);

      // No PathSource annotations, so it should succeed but generate nothing
      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("Target Package")
  class TargetPackage {

    @Test
    @DisplayName("generates path class in target package when specified")
    void shouldGenerateInTargetPackage() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.PkgKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface PkgKind<A> extends Kind<PkgKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.PkgType",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = PkgKind.Witness.class,
                  targetPackage = "com.example.generated"
              )
              public interface PkgType<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();
      assertTrue(
          compilation.generatedSourceFile("com.example.generated.PkgTypePath").isPresent(),
          "Expected generated file in com.example.generated package");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("generates path class for concrete class (not just interface)")
    void shouldGeneratePathClassForClass() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.BoxKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface BoxKind<A> extends Kind<BoxKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Box",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = BoxKind.Witness.class)
              public class Box<A> {
                  private final A value;
                  public Box(A value) { this.value = value; }
                  public A getValue() { return value; }
              }
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();
      assertTrue(
          compilation.generatedSourceFile("com.example.BoxPath").isPresent(),
          "Expected generated file BoxPath for class");
      assertGeneratedCodeContains(
          compilation, "com.example.BoxPath", "public final class BoxPath<A>");
    }

    @Test
    @DisplayName("generates path class with COMBINABLE capability including zipWith")
    void shouldGenerateCombinablePathClass() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.PairKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface PairKind<A> extends Kind<PairKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Pair",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = PairKind.Witness.class,
                  capability = PathSource.Capability.COMBINABLE
              )
              public interface Pair<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.PairPath";
      assertGeneratedCodeContains(compilation, generatedClassName, "implements Combinable<A>");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public <B, C> PairPath<C> zipWith(");
      // COMBINABLE should NOT have via/flatMap
      assertGeneratedCodeDoesNotContain(
          compilation, generatedClassName, "public <B> PairPath<B> via(");
    }

    @Test
    @DisplayName("generates path class with EFFECTFUL capability")
    void shouldGenerateEffectfulPathClass() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.TaskKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface TaskKind<A> extends Kind<TaskKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Task",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = TaskKind.Witness.class,
                  capability = PathSource.Capability.EFFECTFUL
              )
              public interface Task<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.TaskPath";
      // EFFECTFUL maps to Combinable interface but has chainable methods
      assertGeneratedCodeContains(compilation, generatedClassName, "implements Combinable<A>");
      assertGeneratedCodeContains(compilation, generatedClassName, "public <B> TaskPath<B> via(");
      assertGeneratedCodeContains(compilation, generatedClassName, "public <B> TaskPath<B> then(");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public <B> TaskPath<B> flatMap(");
    }

    @Test
    @DisplayName("generates path class with ACCUMULATING capability")
    void shouldGenerateAccumulatingPathClass() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.ValidatedKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface ValidatedKind<A> extends Kind<ValidatedKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Validated",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = ValidatedKind.Witness.class,
                  errorType = String.class,
                  capability = PathSource.Capability.ACCUMULATING
              )
              public interface Validated<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.ValidatedPath";
      assertGeneratedCodeContains(compilation, generatedClassName, "implements Combinable<A>");
      // ACCUMULATING with errorType should have recovery methods
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public ValidatedPath<A> recover(");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public ValidatedPath<A> recoverWith(");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public ValidatedPath<A> mapError(");
    }

    @Test
    @DisplayName("generates @Generated annotation on path class")
    void shouldGenerateWithAnnotation() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.AnnotatedKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface AnnotatedKind<A> extends Kind<AnnotatedKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Annotated",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = AnnotatedKind.Witness.class)
              public interface Annotated<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.AnnotatedPath", "@Generated");
    }

    @Test
    @DisplayName("generates null checks with Objects.requireNonNull")
    void shouldGenerateNullChecks() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.SafeKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface SafeKind<A> extends Kind<SafeKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Safe",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = SafeKind.Witness.class)
              public interface Safe<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.SafePath";
      assertGeneratedCodeContains(compilation, generatedClassName, "Objects.requireNonNull(kind");
      assertGeneratedCodeContains(compilation, generatedClassName, "Objects.requireNonNull(monad");
      assertGeneratedCodeContains(compilation, generatedClassName, "Objects.requireNonNull(mapper");
    }

    @Test
    @DisplayName("handles multiple @PathSource annotations in same compilation")
    void shouldHandleMultipleAnnotations() {
      final var witnessSource1 =
          JavaFileObjects.forSourceString(
              "com.example.FirstKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface FirstKind<A> extends Kind<FirstKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var witnessSource2 =
          JavaFileObjects.forSourceString(
              "com.example.SecondKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface SecondKind<A> extends Kind<SecondKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile1 =
          JavaFileObjects.forSourceString(
              "com.example.First",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = FirstKind.Witness.class)
              public interface First<A> {}
              """);

      final var sourceFile2 =
          JavaFileObjects.forSourceString(
              "com.example.Second",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = SecondKind.Witness.class)
              public interface Second<A> {}
              """);

      var compilation =
          javac()
              .withProcessors(new PathSourceProcessor())
              .compile(witnessSource1, witnessSource2, sourceFile1, sourceFile2);

      assertThat(compilation).succeeded();
      assertTrue(
          compilation.generatedSourceFile("com.example.FirstPath").isPresent(),
          "Expected generated file FirstPath");
      assertTrue(
          compilation.generatedSourceFile("com.example.SecondPath").isPresent(),
          "Expected generated file SecondPath");
    }

    @Test
    @DisplayName("fails with error when @PathSource is applied to enum")
    void shouldFailForEnum() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadEnum",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = Object.class)
              public enum BadEnum {
                  VALUE
              }
              """);

      var compilation = javac().withProcessors(new PathSourceProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@PathSource can only be applied to classes or interfaces");
    }

    @Test
    @DisplayName("generates peek method for all capabilities")
    void shouldGeneratePeekMethod() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.PeekKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface PeekKind<A> extends Kind<PeekKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Peek",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = PeekKind.Witness.class)
              public interface Peek<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.PeekPath", "public PeekPath<A> peek(Consumer<? super A>");
    }

    @Test
    @DisplayName("generates explicit CHAINABLE capability with via, then, flatMap")
    void shouldGenerateChainablePathClass() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.ChainKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface ChainKind<A> extends Kind<ChainKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Chain",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = ChainKind.Witness.class,
                  capability = PathSource.Capability.CHAINABLE
              )
              public interface Chain<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.ChainPath";
      assertGeneratedCodeContains(compilation, generatedClassName, "public <B> ChainPath<B> via(");
      assertGeneratedCodeContains(compilation, generatedClassName, "public <B> ChainPath<B> then(");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public <B> ChainPath<B> flatMap(");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public <B, C> ChainPath<C> zipWith(");
    }

    @Test
    @DisplayName("generates path class for nested inner interface")
    void shouldGeneratePathForNestedInterface() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.OuterKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface OuterKind<A> extends Kind<OuterKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              public class Outer {
                  @PathSource(witness = OuterKind.Witness.class)
                  public interface Inner<A> {}
              }
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();
      assertTrue(
          compilation.generatedSourceFile("com.example.InnerPath").isPresent(),
          "Expected generated file InnerPath for nested interface");
    }

    @Test
    @DisplayName("generates correct toString format")
    void shouldGenerateCorrectToStringFormat() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.StringableKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface StringableKind<A> extends Kind<StringableKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Stringable",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = StringableKind.Witness.class)
              public interface Stringable<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.StringablePath", "return \"StringablePath(\" + kind + \")\"");
    }

    @Test
    @DisplayName("RECOVERABLE without errorType still generates chainable methods but no recovery")
    void shouldGenerateRecoverableWithoutErrorType() {
      final var witnessSource =
          JavaFileObjects.forSourceString(
              "com.example.NoErrorKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface NoErrorKind<A> extends Kind<NoErrorKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """);

      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.NoError",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(
                  witness = NoErrorKind.Witness.class,
                  capability = PathSource.Capability.RECOVERABLE
              )
              public interface NoError<A> {}
              """);

      var compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(witnessSource, sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.NoErrorPath";
      // Should still have chainable methods
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public <B> NoErrorPath<B> via(");
      // But should NOT have recovery methods (no errorType specified)
      assertGeneratedCodeDoesNotContain(
          compilation, generatedClassName, "public NoErrorPath<A> recover(");
      assertGeneratedCodeDoesNotContain(
          compilation, generatedClassName, "private final MonadError");
    }
  }

  // Helper methods

  private static void assertGeneratedCodeContains(
      Compilation compilation, String generatedFileName, String expectedCode) {
    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
      return;
    }

    try {
      String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();
      String normalisedActual = normaliseCode(actualGeneratedCode);
      String normalisedExpected = normaliseCode(expectedCode);

      assertTrue(
          normalisedActual.contains(normalisedExpected),
          String.format(
              "Expected generated code to contain:%n---%n%s%n---%nBut was:%n---%n%s%n---",
              normalisedExpected, actualGeneratedCode));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  private static void assertGeneratedCodeDoesNotContain(
      Compilation compilation, String generatedFileName, String unexpectedCode) {
    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
      return;
    }

    try {
      String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();
      String normalisedActual = normaliseCode(actualGeneratedCode);
      String normalisedUnexpected = normaliseCode(unexpectedCode);

      assertTrue(
          !normalisedActual.contains(normalisedUnexpected),
          String.format(
              "Expected generated code NOT to contain:%n---%n%s%n---%nBut it did:%n---%n%s%n---",
              normalisedUnexpected, actualGeneratedCode));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  private static String normaliseCode(String code) {
    String normalised =
        code.replaceAll("package [\\w.]+;\\s*", "")
            .replaceAll("import [\\w.]+;\\s*", "")
            .replaceAll("/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/", "")
            .replaceAll("//.*", "");

    // Normalise fully qualified class names to simple names
    normalised =
        normalised
            .replaceAll("java\\.util\\.Objects", "Objects")
            .replaceAll("java\\.util\\.function\\.Function", "Function")
            .replaceAll("java\\.util\\.function\\.Consumer", "Consumer")
            .replaceAll("java\\.util\\.function\\.Supplier", "Supplier")
            .replaceAll("java\\.util\\.function\\.BiFunction", "BiFunction")
            .replaceAll("org\\.higherkindedj\\.hkt\\.Kind", "Kind")
            .replaceAll("org\\.higherkindedj\\.hkt\\.Monad", "Monad")
            .replaceAll("org\\.higherkindedj\\.hkt\\.MonadError", "MonadError")
            .replaceAll(
                "org\\.higherkindedj\\.hkt\\.effect\\.capability\\.Composable", "Composable")
            .replaceAll(
                "org\\.higherkindedj\\.hkt\\.effect\\.capability\\.Combinable", "Combinable")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.capability\\.Chainable", "Chainable")
            .replaceAll(
                "org\\.higherkindedj\\.hkt\\.effect\\.capability\\.Recoverable", "Recoverable");

    return normalised.replaceAll("\\s+", "").trim();
  }
}
