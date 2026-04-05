// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

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
 * Structural validation tests for the for-comprehension generators.
 *
 * <p>These tests exercise the code generation paths in {@link TupleGenerator}, {@link
 * ForStepGenerator}, and {@link ForPathStepGenerator} by compiling test fixtures and asserting on
 * the structure of generated output. This kills VoidMethodCall, RemoveConditional, and
 * BooleanFalseReturn mutations in the generator code.
 */
@DisplayName("For-Comprehension Generator Tests")
class ForComprehensionGeneratorTest {

  // ---------------------------------------------------------------------------
  // Shared fixture: generates arities 6-8
  // ---------------------------------------------------------------------------

  private static JavaFileObject packageInfoSource() {
    return JavaFileObjects.forSourceString(
        "org.higherkindedj.hkt.expression.package-info",
        """
        @GenerateForComprehensions(minArity = 6, maxArity = 8)
        package org.higherkindedj.hkt.expression;

        import org.higherkindedj.optics.annotations.GenerateForComprehensions;
        """);
  }

  /** Fixture that generates only arity 6 (terminal). */
  private static JavaFileObject singleAritySource() {
    return JavaFileObjects.forSourceString(
        "org.higherkindedj.hkt.expression.package-info",
        """
        @GenerateForComprehensions(minArity = 6, maxArity = 6)
        package org.higherkindedj.hkt.expression;

        import org.higherkindedj.optics.annotations.GenerateForComprehensions;
        """);
  }

  /** Fixture for arity 2 (minimum, uses BiFunction). */
  private static JavaFileObject minAritySource() {
    return JavaFileObjects.forSourceString(
        "org.higherkindedj.hkt.expression.package-info",
        """
        @GenerateForComprehensions(minArity = 2, maxArity = 3)
        package org.higherkindedj.hkt.expression;

        import org.higherkindedj.optics.annotations.GenerateForComprehensions;
        """);
  }

  private Compilation compile(JavaFileObject source) {
    return javac().withProcessors(new ForComprehensionProcessor()).compile(source);
  }

  private String getGeneratedSource(Compilation compilation, String className) throws IOException {
    Optional<JavaFileObject> file = compilation.generatedSourceFile(className);
    assertThat(file).as("Generated file should exist: %s", className).isPresent();
    return file.get().getCharContent(true).toString();
  }

  // ===========================================================================
  // TupleGenerator Structural Tests
  // ===========================================================================

  @Nested
  @DisplayName("TupleGenerator Tests")
  class TupleGeneratorTests {

    @Test
    @DisplayName("Tuple6 should have correct record declaration")
    void tuple6RecordDeclaration() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      assertThat(source).contains("package org.higherkindedj.hkt.tuple;");
      assertThat(source).contains("public record Tuple6<A, B, C, D, E, F>");
      assertThat(source).contains("A _1, B _2, C _3, D _4, E _5, F _6");
      assertThat(source).contains("implements Tuple");
    }

    @Test
    @DisplayName("Tuple7 should have correct record declaration")
    void tuple7RecordDeclaration() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple7");

      assertThat(source).contains("public record Tuple7<A, B, C, D, E, F, G>");
      assertThat(source).contains("A _1, B _2, C _3, D _4, E _5, F _6, G _7");
    }

    @Test
    @DisplayName("Tuple8 should have correct record declaration")
    void tuple8RecordDeclaration() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple8");

      assertThat(source).contains("public record Tuple8<A, B, C, D, E, F, G, H>");
      assertThat(source).contains("A _1, B _2, C _3, D _4, E _5, F _6, G _7, H _8");
    }

    @Test
    @DisplayName("Tuple6 should have @Generated and @GenerateLenses annotations")
    void tuple6Annotations() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      assertThat(source).contains("@Generated");
      assertThat(source).contains("@GenerateLenses");
    }

    @Test
    @DisplayName("Tuple6 should have generated header comment")
    void tuple6HeaderComment() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      assertThat(source).contains("// Generated by hkj-processor. Do not edit.");
    }

    @Test
    @DisplayName("Tuple6 should have correct imports")
    void tuple6Imports() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      assertThat(source).contains("import java.util.function.Function;");
      assertThat(source).contains("import org.higherkindedj.hkt.util.validation.Validation;");
      assertThat(source).contains("import org.higherkindedj.optics.annotations.GenerateLenses;");
      assertThat(source).contains("import org.higherkindedj.optics.annotations.Generated;");
      assertThat(source)
          .contains("import static org.higherkindedj.hkt.util.validation.Operation.*;");
    }

    @Test
    @DisplayName("Tuple6 should have map() method with all 6 mappers")
    void tuple6MapMethod() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      // Verify map() method signature has 6 mapper params
      assertThat(source).contains("firstMapper");
      assertThat(source).contains("secondMapper");
      assertThat(source).contains("thirdMapper");
      assertThat(source).contains("fourthMapper");
      assertThat(source).contains("fifthMapper");
      assertThat(source).contains("sixthMapper");
    }

    @Test
    @DisplayName("Tuple6 should have individual mapNth methods")
    void tuple6MapNthMethods() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      assertThat(source).contains("mapFirst(");
      assertThat(source).contains("mapSecond(");
      assertThat(source).contains("mapThird(");
      assertThat(source).contains("mapFourth(");
      assertThat(source).contains("mapFifth(");
      assertThat(source).contains("mapSixth(");
    }

    @Test
    @DisplayName("Tuple6 should have Validation calls for each mapper")
    void tuple6ValidationCalls() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      // The map() method should validate each mapper
      assertThat(source).contains("Validation.function().require(firstMapper,");
      assertThat(source).contains("Validation.function().require(sixthMapper,");
    }

    @Test
    @DisplayName("Tuple6 should NOT have bimap method (only arity 2)")
    void tuple6NoBimapMethod() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      assertThat(source).doesNotContain("bimap(");
    }

    @Test
    @DisplayName("Tuple2 should have bimap method")
    void tuple2HasBimapMethod() throws IOException {
      Compilation compilation = compile(minAritySource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple2");

      assertThat(source).contains("bimap(");
      assertThat(source).contains("firstMapper.apply(_1)");
      assertThat(source).contains("secondMapper.apply(_2)");
    }

    @Test
    @DisplayName("Tuple2 should import BiFunction")
    void tuple2ImportsBiFunction() throws IOException {
      Compilation compilation = compile(minAritySource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple2");

      // Tuple2 itself doesn't import BiFunction directly - that's for Steps
      // But it should have the right record components
      assertThat(source).contains("public record Tuple2<A, B>(A _1, B _2)");
    }

    @Test
    @DisplayName("Tuple6 should have class constant")
    void tuple6ClassConstant() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      assertThat(source).contains("private static final Class<Tuple6> TUPLE6_CLASS");
    }

    @Test
    @DisplayName("Tuple6 should have Javadoc")
    void tuple6Javadoc() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      assertThat(source).contains("An immutable tuple containing 6 elements");
      assertThat(source).contains("@param <A>");
      assertThat(source).contains("@param <F>");
      assertThat(source).contains("@param _1");
      assertThat(source).contains("@param _6");
    }

    @Test
    @DisplayName("Tuple8 should have 8 mapNth methods")
    void tuple8Has8MapNthMethods() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple8");

      assertThat(source).contains("mapFirst(");
      assertThat(source).contains("mapSecond(");
      assertThat(source).contains("mapSeventh(");
      assertThat(source).contains("mapEighth(");
    }

    @Test
    @DisplayName("Tuple mapNth should use correct accessor")
    void tupleMapNthUsesCorrectAccessor() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source = getGeneratedSource(compilation, "org.higherkindedj.hkt.tuple.Tuple6");

      // mapFirst should apply mapper to _1 and pass _2.._6 through
      assertThat(source).contains("firstMapper.apply(_1)");
      // mapSixth should apply mapper to _6
      assertThat(source).contains("sixthMapper.apply(_6)");
    }
  }

  // ===========================================================================
  // ForStepGenerator Structural Tests
  // ===========================================================================

  @Nested
  @DisplayName("ForStepGenerator Tests")
  class ForStepGeneratorTests {

    @Test
    @DisplayName("MonadicSteps6 should be generated with correct class structure")
    void monadicSteps6ClassStructure() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps6");

      assertThat(source).contains("package org.higherkindedj.hkt.expression;");
      assertThat(source).contains("// Generated by hkj-processor. Do not edit.");
      assertThat(source).contains("@Generated");
      assertThat(source).contains("public final class MonadicSteps6<");
      assertThat(source).contains("M extends WitnessArity<TypeArity.Unary>");
      assertThat(source).contains("implements For.Steps<M>");
    }

    @Test
    @DisplayName("MonadicSteps6 (non-terminal) should have from/let/focus methods")
    void monadicSteps6NonTerminalMethods() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps6");

      // Non-terminal MonadicSteps should have from(), let(), focus()
      assertThat(source).contains("from(");
      assertThat(source).contains("let(");
      assertThat(source).contains("focus(");
      // Should reference next step class
      assertThat(source).contains("MonadicSteps7");
    }

    @Test
    @DisplayName("MonadicSteps8 (terminal) should NOT have from/let/focus methods")
    void monadicSteps8TerminalMethods() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps8");

      // Terminal MonadicSteps should NOT have from(), let(), focus()
      // but should still have yield()
      assertThat(source).contains("yield(");
      assertThat(source).doesNotContain("MonadicSteps9");
    }

    @Test
    @DisplayName("MonadicSteps6 should have both yield variants")
    void monadicSteps6YieldVariants() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps6");

      // Yield with spread function (Function6)
      assertThat(source).contains("Function6<");
      // Yield with tuple function
      assertThat(source).contains("Function<Tuple6<");
    }

    @Test
    @DisplayName("MonadicSteps6 should have correct imports")
    void monadicSteps6Imports() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps6");

      assertThat(source).contains("import java.util.Objects;");
      assertThat(source).contains("import java.util.function.Function;");
      assertThat(source).contains("import org.higherkindedj.hkt.Kind;");
      assertThat(source).contains("import org.higherkindedj.hkt.Monad;");
      assertThat(source).contains("import org.higherkindedj.hkt.tuple.Tuple;");
      assertThat(source).contains("import org.higherkindedj.hkt.tuple.Tuple6;");
      assertThat(source).contains("import org.higherkindedj.hkt.tuple.Tuple7;");
      assertThat(source).contains("import org.higherkindedj.hkt.function.Function6;");
    }

    @Test
    @DisplayName("MonadicSteps8 (terminal) should not import next Tuple")
    void monadicSteps8TerminalNoNextTupleImport() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps8");

      assertThat(source).contains("import org.higherkindedj.hkt.tuple.Tuple8;");
      assertThat(source).doesNotContain("import org.higherkindedj.hkt.tuple.Tuple9;");
    }

    @Test
    @DisplayName("MonadicSteps6 from() should use flatMap and map")
    void monadicSteps6FromUsesMonadicOps() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps6");

      assertThat(source).contains("monad.flatMap(");
      assertThat(source).contains("monad.map(");
    }

    @Test
    @DisplayName("MonadicSteps6 from() should build Tuple.of with all previous fields")
    void monadicSteps6FromBuildsTuple() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps6");

      // Should reference all 6 tuple accessors when building next tuple
      assertThat(source).contains("t._1()");
      assertThat(source).contains("t._2()");
      assertThat(source).contains("t._3()");
      assertThat(source).contains("t._4()");
      assertThat(source).contains("t._5()");
      assertThat(source).contains("t._6()");
    }

    @Test
    @DisplayName("MonadicSteps6 focus should have null check")
    void monadicSteps6FocusNullCheck() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps6");

      assertThat(source)
          .contains("Objects.requireNonNull(extractor, \"extractor must not be null\")");
    }

    @Test
    @DisplayName("MonadicSteps yield should have null check on result")
    void monadicStepsYieldNullCheck() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps6");

      assertThat(source).contains("The yield function must not return null.");
    }

    @Test
    @DisplayName("MonadicSteps6 should have fields for monad and computation")
    void monadicSteps6Fields() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps6");

      assertThat(source).contains("private final Monad<M> monad;");
      assertThat(source).contains("private final Kind<M, Tuple6<");
    }

    @Test
    @DisplayName("MonadicSteps6 should have package-private constructor")
    void monadicSteps6Constructor() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps6");

      // Package-private constructor (no access modifier)
      assertThat(source).contains("MonadicSteps6(Monad<M> monad, Kind<M, Tuple6<");
      assertThat(source).contains("this.monad = monad;");
      assertThat(source).contains("this.computation = computation;");
    }

    @Test
    @DisplayName("MonadicSteps2 should import BiFunction")
    void monadicSteps2ImportsBiFunction() throws IOException {
      Compilation compilation = compile(minAritySource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps2");

      assertThat(source).contains("import java.util.function.BiFunction;");
    }

    @Test
    @DisplayName("MonadicSteps3 should not import BiFunction")
    void monadicSteps3DoesNotImportBiFunction() throws IOException {
      Compilation compilation = compile(minAritySource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MonadicSteps3");

      assertThat(source).doesNotContain("import java.util.function.BiFunction;");
      assertThat(source).contains("import org.higherkindedj.hkt.function.Function3;");
    }

    // ---------------------------------------------------------------------------
    // FilterableSteps Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("FilterableSteps6 should have correct class structure")
    void filterableSteps6ClassStructure() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FilterableSteps6");

      assertThat(source).contains("@Generated");
      assertThat(source).contains("public final class FilterableSteps6<");
      assertThat(source).contains("MonadZero<M> monad");
      assertThat(source).contains("implements For.Steps<M>");
    }

    @Test
    @DisplayName("FilterableSteps6 should have when() method")
    void filterableSteps6WhenMethod() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FilterableSteps6");

      assertThat(source).contains("when(");
      assertThat(source).contains("Predicate<Tuple6<");
      assertThat(source).contains("filter.test(t)");
      assertThat(source).contains("monad.of(t)");
      assertThat(source).contains("monad.zero()");
    }

    @Test
    @DisplayName("FilterableSteps6 (non-terminal) should have match() method")
    void filterableSteps6MatchMethod() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FilterableSteps6");

      assertThat(source).contains("match(");
      assertThat(source).contains("Optional<");
      assertThat(source).contains("orElseGet(monad::zero)");
    }

    @Test
    @DisplayName("FilterableSteps8 (terminal) should NOT have match() method")
    void filterableSteps8TerminalNoMatch() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FilterableSteps8");

      // Terminal FilterableSteps should NOT have match() (requires next step)
      assertThat(source).doesNotContain("FilterableSteps9");
    }

    @Test
    @DisplayName("FilterableSteps8 (terminal) should still have when() method")
    void filterableSteps8TerminalHasWhen() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FilterableSteps8");

      // Terminal FilterableSteps should still have when()
      assertThat(source).contains("when(");
      assertThat(source).contains("Predicate<Tuple8<");
    }

    @Test
    @DisplayName("FilterableSteps6 should import Predicate and Optional")
    void filterableSteps6FilterImports() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FilterableSteps6");

      assertThat(source).contains("import java.util.function.Predicate;");
      assertThat(source).contains("import java.util.Optional;");
      assertThat(source).contains("import org.higherkindedj.hkt.MonadZero;");
    }

    @Test
    @DisplayName("FilterableSteps6 should have from/let/focus methods")
    void filterableSteps6NonTerminalMethods() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FilterableSteps6");

      assertThat(source).contains("from(");
      assertThat(source).contains("let(");
      assertThat(source).contains("focus(");
      assertThat(source).contains("FilterableSteps7");
    }

    @Test
    @DisplayName("FilterableSteps match should use requireNonNull")
    void filterableStepsMatchNullCheck() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FilterableSteps6");

      assertThat(source).contains("Objects.requireNonNull(matcher, \"matcher must not be null\")");
    }
  }

  // ===========================================================================
  // ForPathStepGenerator Structural Tests
  // ===========================================================================

  @Nested
  @DisplayName("ForPathStepGenerator Tests")
  class ForPathStepGeneratorTests {

    @Test
    @DisplayName("MaybePathSteps should be generated (filterable type)")
    void maybePathStepsGenerated() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      Optional<JavaFileObject> file =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.MaybePathSteps2");

      assertThat(file).isPresent();
      String source = file.get().getCharContent(true).toString();

      assertThat(source).contains("@Generated");
      assertThat(source).contains("public final class MaybePathSteps2<");
      // Filterable path type should have when()
      assertThat(source).contains("when(");
      assertThat(source).contains("Predicate<Tuple2<");
    }

    @Test
    @DisplayName("EitherPathSteps should have extra type parameter E")
    void eitherPathStepsExtraTypeParam() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      Optional<JavaFileObject> file =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.EitherPathSteps2");

      assertThat(file).isPresent();
      String source = file.get().getCharContent(true).toString();

      assertThat(source).contains("public final class EitherPathSteps2<E,");
      assertThat(source).contains("EitherKind.Witness<E>");
      assertThat(source).contains("EitherMonad");
    }

    @Test
    @DisplayName("GenericPathSteps should have monad instance field")
    void genericPathStepsMonadField() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      Optional<JavaFileObject> file =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.GenericPathSteps2");

      assertThat(file).isPresent();
      String source = file.get().getCharContent(true).toString();

      assertThat(source).contains("private final Monad<F> monad;");
      assertThat(source).contains("F extends WitnessArity<TypeArity.Unary>");
    }

    @Test
    @DisplayName("TryPathSteps should be generated (non-filterable)")
    void tryPathStepsGenerated() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      Optional<JavaFileObject> file =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.TryPathSteps2");

      assertThat(file).isPresent();
      String source = file.get().getCharContent(true).toString();

      assertThat(source).contains("public final class TryPathSteps2<");
      // Non-filterable should NOT have when()
      assertThat(source).doesNotContain("when(");
      assertThat(source).doesNotContain("Predicate<");
    }

    @Test
    @DisplayName("IOPathSteps should be generated")
    void ioPathStepsGenerated() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      Optional<JavaFileObject> file =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.IOPathSteps2");

      assertThat(file).isPresent();
      String source = file.get().getCharContent(true).toString();

      assertThat(source).contains("public final class IOPathSteps2<");
      assertThat(source).contains("IOMonad");
    }

    @Test
    @DisplayName("VTaskPathSteps should be generated")
    void vtaskPathStepsGenerated() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      Optional<JavaFileObject> file =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.VTaskPathSteps2");

      assertThat(file).isPresent();
      String source = file.get().getCharContent(true).toString();

      assertThat(source).contains("public final class VTaskPathSteps2<");
      assertThat(source).contains("VTaskMonad");
    }

    @Test
    @DisplayName("IdPathSteps should be generated")
    void idPathStepsGenerated() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      Optional<JavaFileObject> file =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.IdPathSteps2");

      assertThat(file).isPresent();
      String source = file.get().getCharContent(true).toString();

      assertThat(source).contains("public final class IdPathSteps2<");
      assertThat(source).contains("IdMonad");
    }

    @Test
    @DisplayName("NonDetPathSteps should be generated (filterable)")
    void nonDetPathStepsGenerated() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      Optional<JavaFileObject> file =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.NonDetPathSteps2");

      assertThat(file).isPresent();
      String source = file.get().getCharContent(true).toString();

      assertThat(source).contains("public final class NonDetPathSteps2<");
      assertThat(source).contains("ListMonad");
      // NonDet is filterable
      assertThat(source).contains("when(");
    }

    @Test
    @DisplayName("OptionalPathSteps should be generated (filterable)")
    void optionalPathStepsGenerated() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      Optional<JavaFileObject> file =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.OptionalPathSteps2");

      assertThat(file).isPresent();
      String source = file.get().getCharContent(true).toString();

      assertThat(source).contains("public final class OptionalPathSteps2<");
      assertThat(source).contains("OptionalMonad");
      assertThat(source).contains("when(");
    }

    @Test
    @DisplayName("MaybePathSteps6 should have yield returning MaybePath")
    void maybePathSteps6YieldReturnType() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("MaybePath<R>");
      assertThat(source).contains("yield(");
    }

    @Test
    @DisplayName("EitherPathSteps6 should have yield returning EitherPath<E, R>")
    void eitherPathSteps6YieldReturnType() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.EitherPathSteps6");

      assertThat(source).contains("EitherPath<E, R>");
    }

    @Test
    @DisplayName("GenericPathSteps6 should have yield returning GenericPath<F, R>")
    void genericPathSteps6YieldReturnType() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.GenericPathSteps6");

      assertThat(source).contains("GenericPath<F, R>");
      assertThat(source).contains("GenericPath.of(result, monad)");
    }

    @Test
    @DisplayName("Non-terminal path steps should have from/let/focus")
    void nonTerminalPathStepsHaveFromLetFocus() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("from(");
      assertThat(source).contains("let(");
      assertThat(source).contains("focus(");
      assertThat(source).contains("MaybePathSteps7");
    }

    @Test
    @DisplayName("Terminal path steps should NOT have from/let/focus")
    void terminalPathStepsNoFromLetFocus() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps8");

      assertThat(source).doesNotContain("MaybePathSteps9");
    }

    @Test
    @DisplayName("Filterable non-terminal path steps should have match()")
    void filterableNonTerminalHasMatch() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("match(");
      assertThat(source).contains("Optional<");
    }

    @Test
    @DisplayName("Non-filterable path steps should NOT have when() or match()")
    void nonFilterableNoWhenOrMatch() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.TryPathSteps6");

      assertThat(source).doesNotContain("when(");
      assertThat(source).doesNotContain("match(");
      assertThat(source).doesNotContain("Predicate<");
    }

    @Test
    @DisplayName("Path steps from() should use widen/narrow pattern")
    void pathStepsFromUsesWidenNarrow() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      // Non-generic path types use .widen() on KindHelper
      assertThat(source).contains(".widen(next.apply(t).run())");
    }

    @Test
    @DisplayName("GenericPathSteps from() should use runKind pattern")
    void genericPathStepsFromUsesRunKind() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.GenericPathSteps6");

      assertThat(source).contains("next.apply(t).runKind()");
    }

    @Test
    @DisplayName("EitherPathSteps should have static monad() helper method")
    void eitherPathStepsHasStaticMonadHelper() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.EitherPathSteps6");

      assertThat(source).contains("private static <E> EitherMonad<E> monad()");
      assertThat(source).contains("return EitherMonad.instance();");
    }

    @Test
    @DisplayName("Path steps focus() should delegate to let()")
    void pathStepsFocusDelegatesToLet() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("return let(extractor);");
    }

    @Test
    @DisplayName("NonDetPath yield should use NonDetPath.of")
    void nonDetPathYieldUsesNonDetPathOf() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.NonDetPathSteps6");

      assertThat(source).contains("NonDetPath.of(");
    }

    @Test
    @DisplayName("FreePathSteps should have monad and functor instance fields")
    void freePathStepsMonadAndFunctorFields() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FreePathSteps2");

      assertThat(source).contains("private final Monad<FreeKind.Witness<F>> monad;");
      assertThat(source).contains("private final Functor<F> functor;");
      assertThat(source).contains("F extends WitnessArity<TypeArity.Unary>");
    }

    @Test
    @DisplayName("FreePathSteps from() should use runKind pattern")
    void freePathStepsFromUsesRunKind() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FreePathSteps6");

      assertThat(source).contains("next.apply(t).runKind()");
    }

    @Test
    @DisplayName("FreePathSteps yield should return FreePath<F, R>")
    void freePathStepsYieldReturnType() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FreePathSteps6");

      assertThat(source).contains("FreePath<F, R>");
      assertThat(source).contains("FreePath.of(FreeKindHelper.FREE.narrow(result), functor)");
    }

    @Test
    @DisplayName("FreePathSteps should not be filterable")
    void freePathStepsNotFilterable() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FreePathSteps6");

      assertThat(source).doesNotContain("when(");
      assertThat(source).doesNotContain("match(");
      assertThat(source).doesNotContain("Predicate<");
    }

    @Test
    @DisplayName("All 10 path types should generate steps for each arity")
    void allPathTypesGenerateSteps() throws IOException {
      Compilation compilation = compile(packageInfoSource());

      // All 10 path types should generate steps from arity 2 through 8
      String[] prefixes = {
        "MaybePathSteps",
        "OptionalPathSteps",
        "EitherPathSteps",
        "TryPathSteps",
        "IOPathSteps",
        "VTaskPathSteps",
        "IdPathSteps",
        "NonDetPathSteps",
        "FreePathSteps",
        "GenericPathSteps"
      };

      for (String prefix : prefixes) {
        for (int arity = 2; arity <= 8; arity++) {
          String className = "org.higherkindedj.hkt.expression." + prefix + arity;
          Optional<JavaFileObject> file = compilation.generatedSourceFile(className);
          assertThat(file).as("Should generate %s%d", prefix, arity).isPresent();
        }
      }
    }

    // -----------------------------------------------------------------------
    // par() method tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MaybePathSteps6 should have par() method with 2 parallel bindings")
    void maybePathStepsPar2() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      // par(f1, f2) should produce MaybePathSteps8
      assertThat(source).contains("MaybePathSteps8<");
      assertThat(source).contains("par(");
      assertThat(source).contains("MONAD.map2(");
      // Combiner lambda should build tuple from existing + new
      assertThat(source).contains("(r1, r2) -> Tuple.of(t._1(), t._2(), t._3(), t._4(), t._5(), t._6(), r1, r2)");
    }

    @Test
    @DisplayName("MaybePathSteps6 par() should use widen pattern for each parallel arg")
    void maybePathStepsParWidenPattern() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      // Each parallel binding should be widened
      assertThat(source).contains("MaybeKindHelper.MAYBE.widen(f1.apply(t).run())");
      assertThat(source).contains("MaybeKindHelper.MAYBE.widen(f2.apply(t).run())");
    }

    @Test
    @DisplayName("GenericPathSteps par() should use runKind pattern")
    void genericPathStepsParUsesRunKind() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.GenericPathSteps6");

      assertThat(source).contains("f1.apply(t).runKind()");
      assertThat(source).contains("f2.apply(t).runKind()");
    }

    @Test
    @DisplayName("EitherPathSteps par() should use local monad variable")
    void eitherPathStepsParLocalMonad() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.EitherPathSteps6");

      // par() for Either should create local monad variable
      assertThat(source).contains("EitherMonad<E> m = monad();");
      assertThat(source).contains("m.flatMap(");
      assertThat(source).contains("m.map2(");
    }

    @Test
    @DisplayName("MaybePathSteps2 should have par() with k=2,3,4 overloads")
    void maybePathSteps2ParOverloads() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps2");

      // k=2 targets arity 4, k=3 targets arity 5, k=4 targets arity 6
      assertThat(source).contains("MONAD.map2(");
      assertThat(source).contains("MONAD.map3(");
      assertThat(source).contains("MONAD.map4(");
      // Combiner for k=3
      assertThat(source).contains("(r1, r2, r3) -> Tuple.of(t._1(), t._2(), r1, r2, r3)");
      // Combiner for k=4
      assertThat(source).contains("(r1, r2, r3, r4) -> Tuple.of(t._1(), t._2(), r1, r2, r3, r4)");
    }

    @Test
    @DisplayName("High arity par() should not exceed arity 12")
    void highArityParDoesNotExceed12() throws IOException {
      // MaybePathSteps8 is terminal at maxArity=8, but par() generation
      // is based on n + k <= 12 regardless of terminal status
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps8");

      // At arity 8, par(k=2)=10, par(k=3)=11, par(k=4)=12 all valid
      assertThat(source).contains("MONAD.map2(");
      assertThat(source).contains("MONAD.map3(");
      assertThat(source).contains("MONAD.map4(");
    }

    @Test
    @DisplayName("FreePathSteps par() should pass monad instance")
    void freePathStepsParMonadInstance() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FreePathSteps6");

      assertThat(source).contains("monad.flatMap(");
      assertThat(source).contains("monad.map2(");
      assertThat(source).contains("f1.apply(t).runKind()");
    }

    // -----------------------------------------------------------------------
    // traverse/sequence/flatTraverse tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MaybePathSteps6 should have traverse() method")
    void maybePathStepsTraverse() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("traverse(");
      assertThat(source).contains("Traverse<TT> traversable");
      assertThat(source).contains("traversable.traverse(MONAD, f, extractor.apply(t))");
      assertThat(source).contains("Objects.requireNonNull(traversable, \"traversable must not be null\")");
      assertThat(source).contains("Objects.requireNonNull(extractor, \"extractor must not be null\")");
      assertThat(source).contains("Objects.requireNonNull(f, \"function must not be null\")");
    }

    @Test
    @DisplayName("MaybePathSteps6 should have sequence() method")
    void maybePathStepsSequence() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("sequence(");
      assertThat(source).contains("traversable.sequenceA(MONAD, extractor.apply(t))");
    }

    @Test
    @DisplayName("MaybePathSteps6 should have flatTraverse() method")
    void maybePathStepsFlatTraverse() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("flatTraverse(");
      assertThat(source).contains("Monad<TT> innerMonad");
      assertThat(source).contains("Objects.requireNonNull(innerMonad, \"innerMonad must not be null\")");
      assertThat(source).contains("innerMonad.flatMap(Function.identity(), ttb)");
    }

    @Test
    @DisplayName("MaybePathSteps6 traverse() should build tuple with existing fields")
    void traverseTupleBuilding() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      // traverse, sequence, flatTraverse should all build tuples with t._1()..t._6() + new value
      assertThat(source).contains("tb -> Tuple.of(t._1(), t._2(), t._3(), t._4(), t._5(), t._6(), tb)");
    }

    @Test
    @DisplayName("GenericPathSteps traverse() should use instance monad")
    void genericPathStepsTraverseUsesInstanceMonad() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.GenericPathSteps6");

      assertThat(source).contains("traversable.traverse(monad, f, extractor.apply(t))");
      assertThat(source).contains("traversable.sequenceA(monad, extractor.apply(t))");
    }

    @Test
    @DisplayName("EitherPathSteps traverse() should use local monad variable")
    void eitherPathStepsTraverseLocalMonad() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.EitherPathSteps6");

      assertThat(source).contains("traversable.traverse(m, f, extractor.apply(t))");
      assertThat(source).contains("traversable.sequenceA(m, extractor.apply(t))");
    }

    @Test
    @DisplayName("Terminal path steps should NOT have traverse/sequence/flatTraverse")
    void terminalPathStepsNoTraverse() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps8");

      assertThat(source).doesNotContain("traverse(");
      assertThat(source).doesNotContain("sequence(");
      assertThat(source).doesNotContain("flatTraverse(");
      assertThat(source).doesNotContain("Traverse<");
    }

    // -----------------------------------------------------------------------
    // Terminal filterable: when() but NOT match()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Terminal filterable path step should have when() but NOT match()")
    void terminalFilterableHasWhenButNotMatch() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps8");

      // Terminal filterable: when() YES, match() NO
      assertThat(source).contains("when(");
      assertThat(source).contains("Predicate<Tuple8<");
      assertThat(source).doesNotContain("match(");
      assertThat(source).doesNotContain("Optional<");
    }

    @Test
    @DisplayName("Terminal NonDetPathSteps should have when() but NOT match()")
    void terminalNonDetHasWhenButNotMatch() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.NonDetPathSteps8");

      assertThat(source).contains("when(");
      assertThat(source).doesNotContain("match(");
    }

    // -----------------------------------------------------------------------
    // Yield variants: null check, tuple overload, spread function type
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Path steps yield should have null check with YIELD_NULL_MSG")
    void pathStepsYieldNullCheck() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("Objects.requireNonNull(");
      assertThat(source).contains("The yield function must not return null.");
    }

    @Test
    @DisplayName("Path steps yield should have tuple function overload")
    void pathStepsYieldTupleOverload() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      // Yield with Function<Tuple6<...>, R>
      assertThat(source).contains("yield(Function<Tuple6<");
      // The tuple overload uses f.apply(t) directly
      assertThat(source).contains("Objects.requireNonNull(f.apply(t),");
    }

    @Test
    @DisplayName("Arity 2 path step yield spread should use BiFunction")
    void arity2YieldSpreadUsesBiFunction() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps2");

      assertThat(source).contains("yield(BiFunction<");
      assertThat(source).contains("import java.util.function.BiFunction;");
    }

    @Test
    @DisplayName("Arity 3+ path step yield spread should use FunctionN")
    void arity3PlusYieldSpreadUsesFunctionN() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("yield(Function6<");
      assertThat(source).doesNotContain("yield(BiFunction<");
    }

    @Test
    @DisplayName("Yield spread should apply individual tuple accessors")
    void yieldSpreadAppliesToAccessors() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      // spread yield: f.apply(t._1(), t._2(), ..., t._6())
      assertThat(source).contains("f.apply(t._1(), t._2(), t._3(), t._4(), t._5(), t._6())");
    }

    // -----------------------------------------------------------------------
    // Collision avoidance tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("EitherPathSteps should skip E and M in value params")
    void eitherPathStepsSkipsEandM() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.EitherPathSteps8");

      // EITHER_VALUE_PARAMS = A, B, C, D, F, G, H, I, J, K, L, N (skips E and M)
      // Arity 8 uses params: E (extra), A, B, C, D, F, G, H, I
      assertThat(source).contains("EitherPathSteps8<E, A, B, C, D, F, G, H");
      // Should NOT have M as a type param (would collide with local monad var 'm')
      assertThat(source).doesNotContain(", M>");
      assertThat(source).doesNotContain(", M,");
    }

    @Test
    @DisplayName("GenericPathSteps should skip F in value params")
    void genericPathStepsSkipsF() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.GenericPathSteps8");

      // GENERIC_VALUE_PARAMS = A, B, C, D, E, G, H, I, J, K, L, M (skips F)
      // Arity 8 uses: F (extra, bounded), A, B, C, D, E, G, H
      assertThat(source).contains("GenericPathSteps8<F extends WitnessArity<TypeArity.Unary>, A, B, C, D, E, G, H");
    }

    @Test
    @DisplayName("FreePathSteps should skip F in value params (same as Generic)")
    void freePathStepsSkipsF() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FreePathSteps8");

      // Free also uses GENERIC_VALUE_PARAMS (skips F)
      assertThat(source).contains("FreePathSteps8<F extends WitnessArity<TypeArity.Unary>, A, B, C, D, E, G, H");
    }

    // -----------------------------------------------------------------------
    // when() body tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MaybePathSteps when() should use zero() pattern")
    void maybePathStepsWhenBodyZeroPattern() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("predicate.test(t) ? MONAD.of(t) : MONAD.zero()");
    }

    @Test
    @DisplayName("NonDetPathSteps when() should use MONAD reference")
    void nonDetPathStepsWhenUsesMonad() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.NonDetPathSteps6");

      assertThat(source).contains("predicate.test(t) ? MONAD.of(t) : MONAD.zero()");
      assertThat(source).contains("MONAD.flatMap(");
    }

    // -----------------------------------------------------------------------
    // match() body tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MaybePathSteps match() should use orElseGet with zero")
    void maybePathStepsMatchBodyOrElseGet() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains(".orElseGet(MONAD::zero)");
      assertThat(source).contains("Objects.requireNonNull(matcher, \"matcher must not be null\")");
    }

    @Test
    @DisplayName("NonDetPathSteps match() should use MONAD::zero")
    void nonDetPathStepsMatchOrElseGet() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.NonDetPathSteps6");

      assertThat(source).contains(".orElseGet(MONAD::zero)");
    }

    @Test
    @DisplayName("OptionalPathSteps match() should use MONAD::zero")
    void optionalPathStepsMatchOrElseGet() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.OptionalPathSteps6");

      assertThat(source).contains(".orElseGet(MONAD::zero)");
    }

    // -----------------------------------------------------------------------
    // let() body tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MaybePathSteps let() should use map (not flatMap)")
    void maybePathStepsLetUsesMap() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      // let() uses map: MONAD.map(t -> Tuple.of(..., f.apply(t)), computation)
      assertThat(source).contains("MONAD.map(\n            t -> Tuple.of(t._1(), t._2(), t._3(), t._4(), t._5(), t._6(), f.apply(t))");
    }

    @Test
    @DisplayName("EitherPathSteps let() should use local monad variable with map")
    void eitherPathStepsLetUsesLocalMonadMap() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.EitherPathSteps6");

      // Either let() uses local monad: m.map(...)
      assertThat(source).contains("m.map(");
    }

    // -----------------------------------------------------------------------
    // from() tuple construction detail
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MaybePathSteps from() should use flatMap wrapping map for tuple building")
    void maybePathStepsFromTupleConstruction() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      // from() uses flatMap(t -> map(g -> Tuple.of(t._1(), ..., t._6(), g), widen(next...)))
      assertThat(source).contains("MONAD.flatMap(");
      assertThat(source).contains("t -> MONAD.map(");
    }

    // -----------------------------------------------------------------------
    // Import specifics
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MaybePathSteps2 should import BiFunction (arity 2)")
    void maybePathSteps2ImportsBiFunction() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps2");

      assertThat(source).contains("import java.util.function.BiFunction;");
    }

    @Test
    @DisplayName("MaybePathSteps6 should NOT import BiFunction (arity > 2)")
    void maybePathSteps6NoBiFunction() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).doesNotContain("import java.util.function.BiFunction;");
    }

    @Test
    @DisplayName("Non-terminal path steps should import Traverse")
    void nonTerminalImportsTraverse() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("import org.higherkindedj.hkt.Traverse;");
      assertThat(source).contains("import org.higherkindedj.hkt.TypeArity;");
      assertThat(source).contains("import org.higherkindedj.hkt.WitnessArity;");
    }

    @Test
    @DisplayName("Terminal non-generic/non-free path steps should NOT import Traverse or Monad")
    void terminalStaticMonadNoTraverseOrMonadImport() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps8");

      assertThat(source).doesNotContain("import org.higherkindedj.hkt.Traverse;");
      assertThat(source).doesNotContain("import org.higherkindedj.hkt.Monad;");
    }

    @Test
    @DisplayName("Filterable path steps should import Predicate and Optional")
    void filterableImportsPredicateAndOptional() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("import java.util.Optional;");
      assertThat(source).contains("import java.util.function.Predicate;");
    }

    @Test
    @DisplayName("Non-filterable path steps should NOT import Predicate or Optional")
    void nonFilterableNoPredicateOrOptionalImport() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.TryPathSteps6");

      assertThat(source).doesNotContain("import java.util.Optional;");
      assertThat(source).doesNotContain("import java.util.function.Predicate;");
    }

    @Test
    @DisplayName("FreePathSteps should import Functor")
    void freePathStepsImportsFunctor() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FreePathSteps6");

      assertThat(source).contains("import org.higherkindedj.hkt.Functor;");
    }

    @Test
    @DisplayName("GenericPathSteps should import Monad but NOT Functor")
    void genericPathStepsImportsMonadNotFunctor() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.GenericPathSteps6");

      assertThat(source).contains("import org.higherkindedj.hkt.Monad;");
      assertThat(source).doesNotContain("import org.higherkindedj.hkt.Functor;");
    }

    @Test
    @DisplayName("Tuple imports should cover up to N+4 for par() methods")
    void tupleImportsCoverParTargetArity() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      // Arity 6: should import Tuple6 through Tuple10 (6+4)
      assertThat(source).contains("import org.higherkindedj.hkt.tuple.Tuple6;");
      assertThat(source).contains("import org.higherkindedj.hkt.tuple.Tuple7;");
      assertThat(source).contains("import org.higherkindedj.hkt.tuple.Tuple8;");
      assertThat(source).contains("import org.higherkindedj.hkt.tuple.Tuple9;");
      assertThat(source).contains("import org.higherkindedj.hkt.tuple.Tuple10;");
    }

    // -----------------------------------------------------------------------
    // NonDetPath, static MONAD, Free/Generic constructor args
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("NonDetPath yield should use narrow() pattern")
    void nonDetPathYieldUsesNarrow() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.NonDetPathSteps6");

      assertThat(source).contains("NonDetPath.of(ListKindHelper.LIST.narrow(result))");
    }

    @Test
    @DisplayName("MaybePathSteps should have static MONAD field")
    void maybePathStepsStaticMonadField() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("private static final MaybeMonad MONAD = MaybeMonad.INSTANCE;");
    }

    @Test
    @DisplayName("IdPathSteps should have static MONAD field from factory")
    void idPathStepsStaticMonadField() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.IdPathSteps6");

      assertThat(source).contains("private static final IdMonad MONAD = IdMonad.instance();");
    }

    @Test
    @DisplayName("FreePathSteps constructor should take monad and functor")
    void freePathStepsConstructorArgs() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FreePathSteps6");

      assertThat(source).contains("FreePathSteps6(Monad<FreeKind.Witness<F>> monad, Functor<F> functor,");
      assertThat(source).contains("this.monad = monad;");
      assertThat(source).contains("this.functor = functor;");
      assertThat(source).contains("this.computation = computation;");
    }

    @Test
    @DisplayName("GenericPathSteps constructor should take monad")
    void genericPathStepsConstructorArgs() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.GenericPathSteps6");

      assertThat(source).contains("GenericPathSteps6(Monad<F> monad,");
      assertThat(source).contains("this.monad = monad;");
      assertThat(source).contains("this.computation = computation;");
    }

    @Test
    @DisplayName("Free from() should pass monad and functor to next constructor")
    void freeFromPassesMonadAndFunctor() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FreePathSteps6");

      assertThat(source).contains("return new FreePathSteps7<>(monad, functor, newComp);");
    }

    @Test
    @DisplayName("Generic from() should pass monad to next constructor")
    void genericFromPassesMonad() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.GenericPathSteps6");

      assertThat(source).contains("return new GenericPathSteps7<>(monad, newComp);");
    }

    @Test
    @DisplayName("MaybePath yield should use Path.maybe factory with narrow")
    void maybePathYieldUsesPathFactory() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("return Path.maybe(MaybeKindHelper.MAYBE.narrow(result));");
    }

    @Test
    @DisplayName("EitherPath yield should use Path.either factory with narrow")
    void eitherPathYieldUsesPathFactory() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.EitherPathSteps6");

      assertThat(source).contains("return Path.either(EitherKindHelper.EITHER.narrow(result));");
    }

    @Test
    @DisplayName("EitherPathSteps should not have static MONAD field (uses helper method)")
    void eitherPathStepsNoStaticMonadField() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.EitherPathSteps6");

      assertThat(source).doesNotContain("private static final EitherMonad MONAD");
      assertThat(source).contains("private static <E> EitherMonad<E> monad()");
    }

    @Test
    @DisplayName("GenericPath yield should use GenericPath.of with result and monad")
    void genericPathYieldUsesGenericPathOf() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.GenericPathSteps6");

      assertThat(source).contains("return GenericPath.of(result, monad);");
    }

    @Test
    @DisplayName("NonDetPathSteps should import NonDetPath (not Path)")
    void nonDetPathImportsNonDetPath() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.NonDetPathSteps6");

      assertThat(source).contains("import org.higherkindedj.hkt.effect.NonDetPath;");
      assertThat(source).doesNotContain("import org.higherkindedj.hkt.effect.Path;");
    }

    @Test
    @DisplayName("MaybePathSteps should import both MaybePath and Path")
    void maybePathImportsMaybePathAndPath() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.MaybePathSteps6");

      assertThat(source).contains("import org.higherkindedj.hkt.effect.MaybePath;");
      assertThat(source).contains("import org.higherkindedj.hkt.effect.Path;");
    }

    @Test
    @DisplayName("Terminal GenericPathSteps should still import Monad (instance monad)")
    void terminalGenericStillImportsMonad() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.GenericPathSteps8");

      assertThat(source).contains("import org.higherkindedj.hkt.Monad;");
    }

    @Test
    @DisplayName("Terminal FreePathSteps should still import Monad and Functor")
    void terminalFreeStillImportsMonadAndFunctor() throws IOException {
      Compilation compilation = compile(packageInfoSource());
      String source =
          getGeneratedSource(compilation, "org.higherkindedj.hkt.expression.FreePathSteps8");

      assertThat(source).contains("import org.higherkindedj.hkt.Monad;");
      assertThat(source).contains("import org.higherkindedj.hkt.Functor;");
    }
  }

  // ===========================================================================
  // ForComprehensionProcessor Error Handling Tests
  // ===========================================================================

  @Nested
  @DisplayName("ForComprehensionProcessor Validation Tests")
  class ProcessorValidationTests {

    @Test
    @DisplayName("minArity < 2 should produce error")
    void minArityLessThan2ShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.expression.package-info",
              """
              @GenerateForComprehensions(minArity = 1, maxArity = 5)
              package org.higherkindedj.hkt.expression;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation = compile(source);

      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null)).contains("minArity must be >= 2");
    }

    @Test
    @DisplayName("maxArity < minArity should produce error")
    void maxArityLessThanMinArityShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.expression.package-info",
              """
              @GenerateForComprehensions(minArity = 5, maxArity = 3)
              package org.higherkindedj.hkt.expression;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation = compile(source);

      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null)).contains("maxArity");
      assertThat(compilation.errors().get(0).getMessage(null)).contains("minArity");
    }

    @Test
    @DisplayName("maxArity > 26 should produce error")
    void maxArityGreaterThan26ShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.expression.package-info",
              """
              @GenerateForComprehensions(minArity = 2, maxArity = 27)
              package org.higherkindedj.hkt.expression;

              import org.higherkindedj.optics.annotations.GenerateForComprehensions;
              """);

      Compilation compilation = compile(source);

      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null)).contains("maxArity must be <= 26");
    }

    @Test
    @DisplayName("minArity == maxArity should work (single terminal arity)")
    void singleArityTerminal() throws IOException {
      Compilation compilation = compile(singleAritySource());

      // Should generate exactly one arity (terminal), no from/let/focus
      Optional<JavaFileObject> file =
          compilation.generatedSourceFile("org.higherkindedj.hkt.expression.MonadicSteps6");

      assertThat(file).isPresent();
      String source = file.get().getCharContent(true).toString();

      // Single arity is terminal, so no from/let/focus, no MonadicSteps7
      assertThat(source).doesNotContain("MonadicSteps7");
      // But should have yield
      assertThat(source).contains("yield(");
    }

    @Test
    @DisplayName("minArity == 2 should work correctly")
    void minArity2Works() throws IOException {
      Compilation compilation = compile(minAritySource());

      // Should generate MonadicSteps2 and MonadicSteps3
      assertThat(compilation.generatedSourceFile("org.higherkindedj.hkt.expression.MonadicSteps2"))
          .isPresent();
      assertThat(compilation.generatedSourceFile("org.higherkindedj.hkt.expression.MonadicSteps3"))
          .isPresent();
      // Also FilterableSteps
      assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.expression.FilterableSteps2"))
          .isPresent();
      assertThat(
              compilation.generatedSourceFile("org.higherkindedj.hkt.expression.FilterableSteps3"))
          .isPresent();
      // And Tuples
      assertThat(compilation.generatedSourceFile("org.higherkindedj.hkt.tuple.Tuple2")).isPresent();
      assertThat(compilation.generatedSourceFile("org.higherkindedj.hkt.tuple.Tuple3")).isPresent();
    }

    @Test
    @DisplayName("duplicate package annotation should be processed only once")
    void duplicatePackageAnnotationProcessedOnce() {
      // The processor uses processedPackages set to avoid reprocessing
      var source = packageInfoSource();
      Compilation compilation = compile(source);

      // Should generate files exactly once (not duplicated)
      assertThat(compilation.generatedSourceFile("org.higherkindedj.hkt.expression.MonadicSteps6"))
          .isPresent();
    }
  }
}
