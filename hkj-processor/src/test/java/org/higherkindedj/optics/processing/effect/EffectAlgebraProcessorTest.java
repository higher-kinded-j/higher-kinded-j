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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Structural validation tests for the {@link EffectAlgebraProcessor}.
 *
 * <p>Tests exercise code generation paths by compiling test fixtures and asserting on the structure
 * of generated output.
 */
@DisplayName("EffectAlgebra Processor Tests")
class EffectAlgebraProcessorTest {

  // ---------------------------------------------------------------------------
  // Test fixtures
  // ---------------------------------------------------------------------------

  /** Simple effect algebra with two operations. */
  private static JavaFileObject simpleEffectAlgebra() {
    return JavaFileObjects.forSourceString(
        "test.pkg.ConsoleOp",
        """
        package test.pkg;

        import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

        @EffectAlgebra
        public sealed interface ConsoleOp<A> permits ConsoleOp.ReadLine, ConsoleOp.PrintLine {
            record ReadLine<A>() implements ConsoleOp<A> {}
            record PrintLine<A>(String message) implements ConsoleOp<A> {}
        }
        """);
  }

  /** Effect algebra with mapK (continuation-passing style). */
  private static JavaFileObject mapKEffectAlgebra() {
    return JavaFileObjects.forSourceString(
        "test.pkg.CounterOp",
        """
        package test.pkg;

        import java.util.function.Function;
        import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

        @EffectAlgebra
        public sealed interface CounterOp<A> permits CounterOp.Increment, CounterOp.GetCount {
            <B> CounterOp<B> mapK(Function<? super A, ? extends B> f);

            record Increment<A>(Function<Void, A> k) implements CounterOp<A> {
                @Override
                public <B> CounterOp<B> mapK(Function<? super A, ? extends B> f) {
                    return new Increment<>(k.andThen(f));
                }
            }

            record GetCount<A>(Function<Integer, A> k) implements CounterOp<A> {
                @Override
                public <B> CounterOp<B> mapK(Function<? super A, ? extends B> f) {
                    return new GetCount<>(k.andThen(f));
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

  // ===========================================================================
  // Kind Generation Tests
  // ===========================================================================

  @Nested
  @DisplayName("Kind Generation")
  class KindGenerationTests {

    @Test
    @DisplayName("Should generate Kind interface with Witness class")
    void generatesKindInterface() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      if (!compilation.errors().isEmpty()) {
        compilation.errors().forEach(d -> System.err.println("DIAG: " + d.getMessage(null)));
      }
      assertThat(compilation.errors()).isEmpty();

      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpKind");

      assertThat(source).contains("public interface ConsoleOpKind<A>");
      assertThat(source).contains("extends Kind<ConsoleOpKind.Witness, A>");
      assertThat(source).contains("final class Witness");
      assertThat(source).contains("implements WitnessArity<TypeArity.Unary>");
      assertThat(source).contains("@Generated");
    }

    @Test
    @DisplayName("Witness constructor should throw UnsupportedOperationException")
    void witnessConstructorThrows() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpKind");

      assertThat(source).contains("private Witness()");
      assertThat(source).contains("throw new UnsupportedOperationException");
    }
  }

  // ===========================================================================
  // KindHelper Generation Tests
  // ===========================================================================

  @Nested
  @DisplayName("KindHelper Generation")
  class KindHelperGenerationTests {

    @Test
    @DisplayName("Should generate KindHelper enum with singleton")
    void generatesKindHelperEnum() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpKindHelper");

      assertThat(source).contains("public enum ConsoleOpKindHelper");
      assertThat(source).contains("CONSOLE_OP");
      assertThat(source).contains("@Generated");
    }

    @Test
    @DisplayName("Should generate Holder record")
    void generatesHolderRecord() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpKindHelper");

      assertThat(source).contains("record ConsoleOpHolder<A>(ConsoleOp<A> value)");
      assertThat(source).contains("implements ConsoleOpKind<A>");
    }

    @Test
    @DisplayName("Should generate widen method with validation")
    void generatesWidenMethod() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpKindHelper");

      assertThat(source).contains("public <A> Kind<ConsoleOpKind.Witness, A> widen(");
      assertThat(source).contains("ConsoleOp<A> value");
      assertThat(source).contains("Validation.kind().requireForWiden(value, ConsoleOp.class)");
      assertThat(source).contains("return new ConsoleOpHolder<>(value)");
    }

    @Test
    @DisplayName("Should generate narrow method with validation")
    void generatesNarrowMethod() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpKindHelper");

      assertThat(source).contains("public <A> ConsoleOp<A> narrow(");
      assertThat(source).contains("Kind<ConsoleOpKind.Witness, A> kind");
      assertThat(source).contains("Validation.kind().requireNonNull(kind, Operation.FROM_KIND)");
      assertThat(source).contains("return ((ConsoleOpHolder<A>) kind).value()");
      assertThat(source).contains("@SuppressWarnings(\"unchecked\")");
    }
  }

  // ===========================================================================
  // Functor Generation Tests
  // ===========================================================================

  @Nested
  @DisplayName("Functor Generation")
  class FunctorGenerationTests {

    @Test
    @DisplayName("Should generate Functor with cast-through for simple algebra")
    void generatesFunctorWithCastThrough() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpFunctor");

      assertThat(source).contains("public final class ConsoleOpFunctor");
      assertThat(source).contains("implements Functor<ConsoleOpKind.Witness>");
      assertThat(source).contains("@Generated");
      assertThat(source).contains("private static final ConsoleOpFunctor INSTANCE");
      assertThat(source).contains("public static ConsoleOpFunctor instance()");
      // Cast-through pattern (no mapK)
      assertThat(source).doesNotContain("op.mapK(f)");
    }

    @Test
    @DisplayName("Should generate Functor with mapK delegation for continuation algebra")
    void generatesFunctorWithMapK() throws IOException {
      Compilation compilation = compile(mapKEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.CounterOpFunctor");

      assertThat(source).contains("public final class CounterOpFunctor");
      assertThat(source).contains("implements Functor<CounterOpKind.Witness>");
      // mapK delegation
      assertThat(source).contains("op.mapK(f)");
    }

    @Test
    @DisplayName("Functor map should validate arguments")
    void functorMapValidatesArgs() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpFunctor");

      assertThat(source).contains("Validation.function().require(f, \"f\", Operation.MAP)");
      assertThat(source).contains("Validation.kind().requireNonNull(fa, Operation.MAP)");
    }

    @Test
    @DisplayName("Functor map should narrow and re-widen")
    void functorMapNarrowsAndWidens() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpFunctor");

      assertThat(source).contains("ConsoleOpKindHelper.CONSOLE_OP.narrow(fa)");
    }
  }

  // ===========================================================================
  // Ops Generation Tests
  // ===========================================================================

  @Nested
  @DisplayName("Ops Generation")
  class OpsGenerationTests {

    @Test
    @DisplayName("Should generate Ops class with private constructor")
    void generatesOpsClass() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpOps");

      assertThat(source).contains("public final class ConsoleOpOps");
      assertThat(source).contains("@Generated");
      assertThat(source).contains("private ConsoleOpOps()");
    }

    @Test
    @DisplayName("Should generate static factory methods for each permit")
    void generatesFactoryMethods() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpOps");

      // ReadLine has no parameters
      assertThat(source).contains("public static <A> Free<ConsoleOpKind.Witness, A> readLine(");
      // PrintLine has String message
      assertThat(source).contains("public static <A> Free<ConsoleOpKind.Witness, A> printLine(");
      assertThat(source).contains("String message");
    }

    @Test
    @DisplayName("Factory methods should use Free.liftF")
    void factoryMethodsUseLiftF() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpOps");

      assertThat(source).contains("Free.liftF(ConsoleOpKindHelper.CONSOLE_OP.widen(op), functor())");
    }

    @Test
    @DisplayName("Should generate Bound inner class")
    void generatesBoundInnerClass() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpOps");

      assertThat(source).contains("public static final class Bound<G");
      assertThat(source).contains("Inject<ConsoleOpKind.Witness, G> inject");
      assertThat(source).contains("Functor<G> functorG");
    }

    @Test
    @DisplayName("Bound methods should use Free.translate")
    void boundMethodsUseTranslate() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpOps");

      assertThat(source).contains("Free.translate(standalone, inject::inject, functorG)");
    }

    @Test
    @DisplayName("Should generate boundTo factory method")
    void generatesBoundToMethod() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpOps");

      assertThat(source).contains("public static <G");
      assertThat(source).contains("Bound<G> boundTo(");
      assertThat(source).contains("return new Bound<>(inject, functorG)");
    }
  }

  // ===========================================================================
  // Interpreter Generation Tests
  // ===========================================================================

  @Nested
  @DisplayName("Interpreter Generation")
  class InterpreterGenerationTests {

    @Test
    @DisplayName("Should generate abstract interpreter class")
    void generatesAbstractInterpreter() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpInterpreter");

      assertThat(source).contains("public abstract class ConsoleOpInterpreter<M");
      assertThat(source).contains("implements Natural<ConsoleOpKind.Witness, M>");
      assertThat(source).contains("@Generated");
    }

    @Test
    @DisplayName("Should generate abstract handle methods per permit")
    void generatesAbstractHandleMethods() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpInterpreter");

      assertThat(source).contains("protected abstract <A> Kind<M, A> handleReadLine(");
      assertThat(source).contains("ConsoleOp.ReadLine<A> op");
      assertThat(source).contains("protected abstract <A> Kind<M, A> handlePrintLine(");
      assertThat(source).contains("ConsoleOp.PrintLine<A> op");
    }

    @Test
    @DisplayName("Should generate apply() with switch dispatch")
    void generatesApplyWithSwitch() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpInterpreter");

      assertThat(source).contains("public <A> Kind<M, A> apply(");
      assertThat(source).contains("ConsoleOpKindHelper.CONSOLE_OP.narrow(fa)");
      assertThat(source).contains("switch (op)");
      assertThat(source).contains("handleReadLine(p)");
      assertThat(source).contains("handlePrintLine(p)");
    }

    @Test
    @DisplayName("apply() should validate input")
    void applyValidatesInput() throws IOException {
      Compilation compilation = compile(simpleEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.ConsoleOpInterpreter");

      assertThat(source).contains("Validation.kind().requireNonNull(fa, Operation.FROM_KIND)");
    }
  }

  // ===========================================================================
  // Validation Tests
  // ===========================================================================

  @Nested
  @DisplayName("Validation")
  class ValidationTests {

    @Test
    @DisplayName("Non-sealed interface should produce error")
    void nonSealedInterfaceShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadOp",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

              @EffectAlgebra
              public interface BadOp<A> {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@EffectAlgebra can only annotate sealed interfaces");
    }

    @Test
    @DisplayName("Missing type parameter should produce error")
    void missingTypeParamShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadOp",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

              @EffectAlgebra
              public sealed interface BadOp permits BadOp.Foo {
                  record Foo() implements BadOp {}
              }
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("Sealed interface must have exactly one type parameter");
    }

    @Test
    @DisplayName("Two type parameters should produce error")
    void twoTypeParamsShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadOp",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

              @EffectAlgebra
              public sealed interface BadOp<E, A> permits BadOp.Foo {
                  record Foo<E, A>() implements BadOp<E, A> {}
              }
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("Sealed interface must have exactly one type parameter");
    }

    @Test
    @DisplayName("Non-record permit should produce error")
    void nonRecordPermitShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadOp",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

              @EffectAlgebra
              public sealed interface BadOp<A> permits BadOp.Foo {
                  final class Foo<A> implements BadOp<A> {}
              }
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("Permit must be a record type");
    }

    @Test
    @DisplayName("Annotation on class should produce error")
    void annotationOnClassShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadOp",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

              @EffectAlgebra
              public class BadOp<A> {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@EffectAlgebra can only annotate sealed interfaces");
    }

    @Test
    @DisplayName("All 5 files should be generated for valid algebra")
    void allFilesGenerated() {
      Compilation compilation = compile(simpleEffectAlgebra());
      assertThat(compilation.errors()).isEmpty();

      assertThat(compilation.generatedSourceFile("test.pkg.ConsoleOpKind")).isPresent();
      assertThat(compilation.generatedSourceFile("test.pkg.ConsoleOpKindHelper")).isPresent();
      assertThat(compilation.generatedSourceFile("test.pkg.ConsoleOpFunctor")).isPresent();
      assertThat(compilation.generatedSourceFile("test.pkg.ConsoleOpOps")).isPresent();
      assertThat(compilation.generatedSourceFile("test.pkg.ConsoleOpInterpreter")).isPresent();
    }
  }

  // ===========================================================================
  // mapK-based Algebra Tests
  // ===========================================================================

  @Nested
  @DisplayName("Continuation-Passing Algebra")
  class MapKAlgebraTests {

    @Test
    @DisplayName("All 5 files generated for mapK algebra")
    void allFilesGenerated() {
      Compilation compilation = compile(mapKEffectAlgebra());
      assertThat(compilation.errors()).isEmpty();

      assertThat(compilation.generatedSourceFile("test.pkg.CounterOpKind")).isPresent();
      assertThat(compilation.generatedSourceFile("test.pkg.CounterOpKindHelper")).isPresent();
      assertThat(compilation.generatedSourceFile("test.pkg.CounterOpFunctor")).isPresent();
      assertThat(compilation.generatedSourceFile("test.pkg.CounterOpOps")).isPresent();
      assertThat(compilation.generatedSourceFile("test.pkg.CounterOpInterpreter")).isPresent();
    }

    @Test
    @DisplayName("Ops should have methods for each permit with correct params")
    void opsMethodsHaveCorrectParams() throws IOException {
      Compilation compilation = compile(mapKEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.CounterOpOps");

      // Increment has Function<Void, A> k parameter
      assertThat(source).contains("increment(");
      // GetCount has Function<Integer, A> k parameter
      assertThat(source).contains("getCount(");
    }

    @Test
    @DisplayName("Interpreter should have handle methods for each permit")
    void interpreterHasHandleMethods() throws IOException {
      Compilation compilation = compile(mapKEffectAlgebra());
      String source = getGeneratedSource(compilation, "test.pkg.CounterOpInterpreter");

      assertThat(source).contains("handleIncrement(");
      assertThat(source).contains("handleGetCount(");
    }
  }
}
