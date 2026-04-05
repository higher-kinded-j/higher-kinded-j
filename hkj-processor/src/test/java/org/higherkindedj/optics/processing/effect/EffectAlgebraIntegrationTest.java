// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link EffectAlgebraProcessor} verifying that generated code compiles
 * successfully and has correct structure across all 5 generated artifacts.
 */
@DisplayName("EffectAlgebra Integration Tests")
class EffectAlgebraIntegrationTest {

  /** A simple effect algebra for testing. */
  private static JavaFileObject simpleAlgebra() {
    return JavaFileObjects.forSourceString(
        "test.pkg.LogOp",
        """
        package test.pkg;

        import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

        @EffectAlgebra
        public sealed interface LogOp<A> permits LogOp.Info, LogOp.Warn, LogOp.Debug {
            record Info<A>(String message) implements LogOp<A> {}
            record Warn<A>(String message, int level) implements LogOp<A> {}
            record Debug<A>(String context, String detail) implements LogOp<A> {}
        }
        """);
  }

  /** Effect algebra with continuation-passing style (mapK method). */
  private static JavaFileObject continuationAlgebra() {
    return JavaFileObjects.forSourceString(
        "test.pkg.TimerOp",
        """
        package test.pkg;

        import java.util.function.Function;
        import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

        @EffectAlgebra
        public sealed interface TimerOp<A> permits TimerOp.StartTimer, TimerOp.StopTimer {
            <B> TimerOp<B> mapK(Function<? super A, ? extends B> f);

            record StartTimer<A>(String name, Function<Long, A> k) implements TimerOp<A> {
                @Override
                public <B> TimerOp<B> mapK(Function<? super A, ? extends B> f) {
                    return new StartTimer<>(name, k.andThen(f));
                }
            }

            record StopTimer<A>(String name, Function<Void, A> k) implements TimerOp<A> {
                @Override
                public <B> TimerOp<B> mapK(Function<? super A, ? extends B> f) {
                    return new StopTimer<>(name, k.andThen(f));
                }
            }
        }
        """);
  }

  private Compilation compile(JavaFileObject source) {
    return javac().withProcessors(new EffectAlgebraProcessor()).compile(source);
  }

  private String getGeneratedSource(Compilation compilation, String className) throws IOException {
    Optional<JavaFileObject> file = compilation.generatedSourceFile(className);
    assertThat(file).as("Generated file should exist: %s", className).isPresent();
    return file.get().getCharContent(true).toString();
  }

  // =========================================================================
  // End-to-end: all 5 files generated and consistent for 3-permit algebra
  // =========================================================================

  @Test
  @DisplayName("All 5 artifacts generated for 3-permit algebra")
  void allArtifactsGeneratedFor3Permits() {
    Compilation compilation = compile(simpleAlgebra());
    assertThat(compilation.errors()).isEmpty();

    assertThat(compilation.generatedSourceFile("test.pkg.LogOpKind")).isPresent();
    assertThat(compilation.generatedSourceFile("test.pkg.LogOpKindHelper")).isPresent();
    assertThat(compilation.generatedSourceFile("test.pkg.LogOpFunctor")).isPresent();
    assertThat(compilation.generatedSourceFile("test.pkg.LogOpOps")).isPresent();
    assertThat(compilation.generatedSourceFile("test.pkg.LogOpInterpreter")).isPresent();
  }

  @Test
  @DisplayName("Kind has correct Witness for LogOp")
  void kindHasCorrectWitness() throws IOException {
    Compilation compilation = compile(simpleAlgebra());
    String source = getGeneratedSource(compilation, "test.pkg.LogOpKind");

    assertThat(source).contains("public interface LogOpKind<A>");
    assertThat(source).contains("extends Kind<LogOpKind.Witness, A>");
    assertThat(source).contains("final class Witness implements WitnessArity<TypeArity.Unary>");
  }

  @Test
  @DisplayName("KindHelper enum singleton name follows UPPER_SNAKE convention")
  void kindHelperSingletonName() throws IOException {
    Compilation compilation = compile(simpleAlgebra());
    String source = getGeneratedSource(compilation, "test.pkg.LogOpKindHelper");

    assertThat(source).contains("LOG_OP;");
    assertThat(source).contains("record LogOpHolder<A>");
  }

  @Test
  @DisplayName("Ops has factory methods for all 3 permits")
  void opsHasAllFactoryMethods() throws IOException {
    Compilation compilation = compile(simpleAlgebra());
    String source = getGeneratedSource(compilation, "test.pkg.LogOpOps");

    assertThat(source).contains("public static <A> Free<LogOpKind.Witness, A> info(");
    assertThat(source).contains("String message");
    assertThat(source).contains("public static <A> Free<LogOpKind.Witness, A> warn(");
    assertThat(source).contains("int level");
    assertThat(source).contains("public static <A> Free<LogOpKind.Witness, A> debug(");
    assertThat(source).contains("String context");
    assertThat(source).contains("String detail");
  }

  @Test
  @DisplayName("Ops Bound inner class has methods for all permits")
  void opsBoundHasAllMethods() throws IOException {
    Compilation compilation = compile(simpleAlgebra());
    String source = getGeneratedSource(compilation, "test.pkg.LogOpOps");

    assertThat(source).contains("class Bound<G");
    // Bound should have info, warn, debug methods returning Free<G, A>
    assertThat(source).contains("Free<G, A> info(");
    assertThat(source).contains("Free<G, A> warn(");
    assertThat(source).contains("Free<G, A> debug(");
    assertThat(source).contains("Free.translate(standalone, inject::inject, functorG)");
  }

  @Test
  @DisplayName("Interpreter has handle methods for all 3 permits")
  void interpreterHasAllHandleMethods() throws IOException {
    Compilation compilation = compile(simpleAlgebra());
    String source = getGeneratedSource(compilation, "test.pkg.LogOpInterpreter");

    assertThat(source).contains("protected abstract <A> Kind<M, A> handleInfo(");
    assertThat(source).contains("protected abstract <A> Kind<M, A> handleWarn(");
    assertThat(source).contains("protected abstract <A> Kind<M, A> handleDebug(");
    // Switch dispatch
    assertThat(source).contains("case LogOp.Info<A> p -> handleInfo(p)");
    assertThat(source).contains("case LogOp.Warn<A> p -> handleWarn(p)");
    assertThat(source).contains("case LogOp.Debug<A> p -> handleDebug(p)");
  }

  @Test
  @DisplayName("Functor uses mapK delegation for continuation algebra")
  void functorUsesMapKForContinuationAlgebra() throws IOException {
    Compilation compilation = compile(continuationAlgebra());
    assertThat(compilation.errors()).isEmpty();

    String source = getGeneratedSource(compilation, "test.pkg.TimerOpFunctor");

    assertThat(source).contains("op.mapK(f)");
    assertThat(source).doesNotContain("(TimerOp<B>)"); // No cast-through
  }

  @Test
  @DisplayName("Functor uses cast-through for simple algebra")
  void functorUsesCastThroughForSimpleAlgebra() throws IOException {
    Compilation compilation = compile(simpleAlgebra());
    String source = getGeneratedSource(compilation, "test.pkg.LogOpFunctor");

    assertThat(source).doesNotContain("op.mapK(f)");
    // Should use cast-through pattern: narrow, cast, widen
    assertThat(source).contains("LogOpKindHelper.LOG_OP.narrow(fa)");
    assertThat(source).contains("(LogOp<B>)");
    assertThat(source).contains("LogOpKindHelper.LOG_OP.widen(");
  }

  @Test
  @DisplayName("Multi-param record correctly forwards parameters in Ops")
  void multiParamRecordForwardsParams() throws IOException {
    Compilation compilation = compile(simpleAlgebra());
    String source = getGeneratedSource(compilation, "test.pkg.LogOpOps");

    // Warn has (String message, int level) - both should appear as params
    assertThat(source).contains("warn(String message, int level)");
    // Debug has (String context, String detail)
    assertThat(source).contains("debug(String context, String detail)");
  }
}
