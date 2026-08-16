// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link ComposeEffectsProcessor} including @ComposeEffects generation and @Handles
 * validation.
 */
@DisplayName("ComposeEffects Processor Tests")
class ComposeEffectsProcessorTest {

  // ---------------------------------------------------------------------------
  // Test fixtures
  // ---------------------------------------------------------------------------

  /**
   * A minimal {@code @EffectAlgebra}. The composition generator reads each field's {@code
   * Class<XOp<?>>} to name {@code XOpKind.Witness} and {@code XOpOps}, so the fixtures have to be
   * real algebras rather than stand-in types.
   */
  private static JavaFileObject algebra(String name) {
    return JavaFileObjects.forSourceString(
        "test.pkg." + name,
        """
        package test.pkg;

        import java.util.function.Function;
        import org.higherkindedj.hkt.Unit;
        import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

        @EffectAlgebra
        public sealed interface %s<A> permits %s.Only {
            <B> %s<B> mapK(Function<? super A, ? extends B> f);

            record Only<A>(String text, Function<Unit, A> k) implements %s<A> {
                @Override
                public <B> %s<B> mapK(Function<? super A, ? extends B> f) {
                    return new Only<>(text, k.andThen(f));
                }
            }
        }
        """
            .formatted(name, name, name, name, name));
  }

  private static JavaFileObject composition(String recordName, String... fields) {
    StringBuilder components = new StringBuilder();
    for (int i = 0; i < fields.length; i++) {
      if (i > 0) components.append(",\n    ");
      components.append("Class<").append(algebraFor(fields[i])).append("<?>> ").append(fields[i]);
    }
    return JavaFileObjects.forSourceString(
        "test.pkg." + recordName,
        """
        package test.pkg;

        import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

        @ComposeEffects
        public record %s(
            %s
        ) {}
        """
            .formatted(recordName, components));
  }

  /** Field {@code console} is served by algebra {@code ConsoleOp}. */
  private static String algebraFor(String field) {
    return Character.toUpperCase(field.charAt(0)) + field.substring(1) + "Op";
  }

  private static JavaFileObject[] twoEffectComposition() {
    return new JavaFileObject[] {
      algebra("ConsoleOp"), algebra("DbOp"), composition("AppEffects", "console", "db")
    };
  }

  private static JavaFileObject[] threeEffectComposition() {
    return new JavaFileObject[] {
      algebra("ConsoleOp"),
      algebra("DbOp"),
      algebra("LoggingOp"),
      composition("TripleEffects", "console", "db", "logging")
    };
  }

  @Test
  @DisplayName("@Handles with an unresolvable value type is rejected")
  void handlesWithUnresolvableValueRejected() {
    JavaFileObject bad =
        JavaFileObjects.forSourceString(
            "test.pkg.BadInterpreter",
            """
            package test.pkg;

            import org.higherkindedj.hkt.effect.annotation.Handles;

            @Handles(int.class)
            public final class BadInterpreter {}
            """);
    Compilation compilation = compile(bad);
    CompilationSubject.assertThat(compilation).failed();
    CompilationSubject.assertThat(compilation)
        .hadErrorContaining("Cannot resolve @Handles value type");
  }

  private Compilation compile(JavaFileObject... sources) {
    return javac()
        .withProcessors(new EffectAlgebraProcessor(), new ComposeEffectsProcessor())
        .compile(sources);
  }

  /**
   * Collapses runs of whitespace so an assertion about a generated type is not at the mercy of
   * where JavaPoet chose to wrap the line.
   */
  private static String flattened(String source) {
    return source.replaceAll("\\s+", " ");
  }

  private String getGeneratedSource(Compilation compilation, String className) throws IOException {
    Optional<JavaFileObject> file = compilation.generatedSourceFile(className);
    assertThat(file).as("Generated file should exist: %s", className).isPresent();
    return file.get().getCharContent(true).toString();
  }

  // ===========================================================================
  // @ComposeEffects Generation Tests
  // ===========================================================================

  @Nested
  @DisplayName("ComposeEffects Generation")
  class ComposeEffectsGenerationTests {

    @Test
    @DisplayName("Should generate Support class for 2-effect composition")
    void generatesSupportFor2Effects() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      assertThat(compilation.errors()).isEmpty();

      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      assertThat(source).contains("public final class AppEffectsSupport");
      assertThat(source).contains("@Generated");
      assertThat(source).contains("private AppEffectsSupport()");
    }

    @Test
    @DisplayName("Should generate inject methods for 2-effect composition")
    void generatesInjectMethodsFor2Effects() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      assertThat(source).contains("injectConsole(");
      assertThat(source).contains("InjectInstances.injectLeft()");
      assertThat(source).contains("injectDb(");
      assertThat(source).contains("InjectInstances.injectRight()");
    }

    @Test
    @DisplayName("Should generate inject methods for 3-effect composition")
    void generatesInjectMethodsFor3Effects() throws IOException {
      Compilation compilation = compile(threeEffectComposition());
      assertThat(compilation.errors()).isEmpty();

      String source = getGeneratedSource(compilation, "test.pkg.TripleEffectsSupport");

      assertThat(source).contains("injectConsole(");
      assertThat(source).contains("InjectInstances.injectLeft()");
      assertThat(source).contains("injectDb(");
      assertThat(source).contains("injectLogging(");
    }

    @Test
    @DisplayName("Should generate functor() method")
    void generatesFunctorMethod() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      assertThat(source).contains("functor(");
      assertThat(source).contains("EitherFFunctor");
    }

    @Test
    @DisplayName("Should generate BoundSet record")
    void generatesBoundSetRecord() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      assertThat(source).contains("record BoundSet(");
      assertThat(source).doesNotContain("Object console");
      assertThat(source)
          .contains(
              "ConsoleOpOps.Bound<EitherFKind.Witness<ConsoleOpKind.Witness, DbOpKind.Witness>> console");
      assertThat(source)
          .contains(
              "DbOpOps.Bound<EitherFKind.Witness<ConsoleOpKind.Witness, DbOpKind.Witness>> db");
    }

    @Test
    @DisplayName("BoundSet components carry the composed witness at arity 3")
    void boundSetIsTypedAtArityThree() throws IOException {
      Compilation compilation = compile(threeEffectComposition());
      String source = getGeneratedSource(compilation, "test.pkg.TripleEffectsSupport");

      String composed =
          "EitherFKind.Witness<ConsoleOpKind.Witness, EitherFKind.Witness<DbOpKind.Witness,"
              + " LoggingOpKind.Witness>>";
      assertThat(flattened(source)).contains("ConsoleOpOps.Bound<" + composed + "> console");
      assertThat(flattened(source)).contains("DbOpOps.Bound<" + composed + "> db");
      assertThat(flattened(source)).contains("LoggingOpOps.Bound<" + composed + "> logging");
    }

    @Test
    @DisplayName("Inject factories carry the composed witness, not a raw Inject")
    void injectFactoriesAreTyped() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      assertThat(flattened(source))
          .contains(
              "Inject<ConsoleOpKind.Witness, EitherFKind.Witness<ConsoleOpKind.Witness,"
                  + " DbOpKind.Witness>> injectConsole(");
      assertThat(flattened(source))
          .contains(
              "Inject<DbOpKind.Witness, EitherFKind.Witness<ConsoleOpKind.Witness,"
                  + " DbOpKind.Witness>> injectDb(");
    }

    @Test
    @DisplayName("functor() takes and returns typed Functors")
    void functorIsTyped() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      assertThat(flattened(source))
          .contains("EitherFFunctor<ConsoleOpKind.Witness, DbOpKind.Witness> functor(");
      assertThat(source).contains("Functor<ConsoleOpKind.Witness> consoleFunctor");
      assertThat(source).contains("Functor<DbOpKind.Witness> dbFunctor");
    }

    @Test
    @DisplayName("The generated support needs no blanket unchecked/rawtypes suppression")
    void noBlanketSuppression() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      // Declaring the composed witness lets InjectInstances infer, so nothing is cast.
      assertThat(source).doesNotContain("SuppressWarnings");
    }

    @Test
    @DisplayName("The last effect descends exactly as far as the nesting is deep")
    void lastEffectNestingDepth() throws IOException {
      // injectRight() already consumes the final level, so the last effect takes one fewer
      // injectRightThen than its position: at arity 2 it is injectRight() alone.
      String two =
          getGeneratedSource(compile(twoEffectComposition()), "test.pkg.AppEffectsSupport");
      assertThat(two).contains("return InjectInstances.injectRight();");

      String three =
          getGeneratedSource(compile(threeEffectComposition()), "test.pkg.TripleEffectsSupport");
      assertThat(three)
          .contains("return InjectInstances.injectRightThen(InjectInstances.injectRight());");
    }

    @Test
    @DisplayName("Should generate inject methods for 4-effect composition")
    void generatesInjectMethodsFor4Effects() throws IOException {
      var source =
          new JavaFileObject[] {
            algebra("ConsoleOp"),
            algebra("DbOp"),
            algebra("LoggingOp"),
            algebra("MetricsOp"),
            composition("QuadEffects", "console", "db", "logging", "metrics")
          };

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isEmpty();

      String generated = getGeneratedSource(compilation, "test.pkg.QuadEffectsSupport");

      assertThat(generated).contains("injectConsole(");
      assertThat(generated).contains("injectDb(");
      assertThat(generated).contains("injectLogging(");
      assertThat(generated).contains("injectMetrics(");
      // Two injectRightThen for the last effect at arity 4, not three.
      assertThat(flattened(generated))
          .contains(
              "return InjectInstances.injectRightThen(InjectInstances.injectRightThen("
                  + "InjectInstances.injectRight()));");
      // 4-effect functor should nest 3 EitherFFunctors
      assertThat(generated).contains("functor(");
    }
  }

  // ===========================================================================
  // @ComposeEffects Validation Tests
  // ===========================================================================

  @Nested
  @DisplayName("ComposeEffects Validation")
  class ComposeEffectsValidationTests {

    @Test
    @DisplayName("Non-record should produce error")
    void nonRecordShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadEffects",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public class BadEffects {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@ComposeEffects can only annotate record types");
    }

    @Test
    @DisplayName("Single field should produce error")
    void singleFieldShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadEffects",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record BadEffects(String only) {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@ComposeEffects requires 2-4 effect algebra fields");
    }

    @Test
    @DisplayName("Five fields should produce error")
    void fiveFieldsShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadEffects",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record BadEffects(String a, String b, String c, String d, String e) {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@ComposeEffects requires 2-4 effect algebra fields");
    }

    @Test
    @DisplayName("Custom targetPackage should be used for Support class")
    void customTargetPackage() throws IOException {
      var source =
          new JavaFileObject[] {
            algebra("ConsoleOp"),
            algebra("DbOp"),
            JavaFileObjects.forSourceString(
                "test.pkg.MyEffects",
                """
                package test.pkg;

                import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

                @ComposeEffects(targetPackage = "test.gen")
                public record MyEffects(
                    Class<ConsoleOp<?>> console,
                    Class<DbOp<?>> db
                ) {}
                """)
          };

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isEmpty();
      assertThat(compilation.generatedSourceFile("test.gen.MyEffectsSupport")).isPresent();
    }

    @Test
    @DisplayName("A field that is not a Class<XOp<?>> should produce error")
    void nonClassFieldShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.LooseEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record LooseEffects(Integer console, String db) {}
              """);

      Compilation compilation = compile(source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation)
          .hadErrorContaining("must be declared Class<XOp<?>>");
    }

    @Test
    @DisplayName("A Class field naming a type without @EffectAlgebra should produce error")
    void nonAlgebraFieldShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.NotAlgebraEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record NotAlgebraEffects(Class<String> console, Class<Integer> db) {}
              """);

      Compilation compilation = compile(source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation)
          .hadErrorContaining("is not annotated @EffectAlgebra");
    }

    @Test
    @DisplayName("A Class<?> field is rejected by shape, not misreported as a duplicate")
    void wildcardClassFieldShouldError() {
      // Two Class<?> fields are the same type as each other, so resolving the algebras has to
      // happen before the duplicate check or this reports the wrong fault.
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.WildcardEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record WildcardEffects(Class<?> console, Class<?> db) {}
              """);

      Compilation compilation = compile(source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation)
          .hadErrorContaining("must be declared Class<XOp<?>>");
      CompilationSubject.assertThat(compilation).hadErrorContaining("field 'console'");
      CompilationSubject.assertThat(compilation).hadErrorContaining("field 'db'");
    }

    @Test
    @DisplayName("An algebra @EffectAlgebra itself rejects gives one pointed error")
    void nonSealedAlgebraShouldGiveOneError() {
      // Generating a support that names types @EffectAlgebra declined to write would bury the
      // real diagnostic under "package does not exist" from the generated source.
      var plain =
          JavaFileObjects.forSourceString(
              "test.pkg.PlainOp",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

              @EffectAlgebra
              public interface PlainOp<A> {}
              """);
      Compilation compilation =
          compile(plain, algebra("DbOp"), composition("BadEffects", "plain", "db"));

      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation).hadErrorContaining("it is not a sealed interface");
      assertThat(compilation.errors().stream().map(d -> d.getMessage(null)).toList())
          .noneMatch(m -> m.contains("does not exist"));
    }

    @Test
    @DisplayName("An algebra's targetPackage is where its Kind and Ops are looked for")
    void algebraTargetPackageIsHonoured() throws IOException {
      var relocated =
          JavaFileObjects.forSourceString(
              "test.pkg.RelocatedOp",
              """
              package test.pkg;

              import java.util.function.Function;
              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

              @EffectAlgebra(targetPackage = "test.gen")
              public sealed interface RelocatedOp<A> permits RelocatedOp.Only {
                  <B> RelocatedOp<B> mapK(Function<? super A, ? extends B> f);

                  record Only<A>(String text, Function<Unit, A> k) implements RelocatedOp<A> {
                      @Override
                      public <B> RelocatedOp<B> mapK(Function<? super A, ? extends B> f) {
                          return new Only<>(text, k.andThen(f));
                      }
                  }
              }
              """);
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.RelocatedEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record RelocatedEffects(
                  Class<RelocatedOp<?>> relocated,
                  Class<DbOp<?>> db
              ) {}
              """);

      Compilation compilation = compile(relocated, algebra("DbOp"), source);
      assertThat(compilation.errors()).isEmpty();

      String generated = getGeneratedSource(compilation, "test.pkg.RelocatedEffectsSupport");
      assertThat(generated).contains("import test.gen.RelocatedOpKind;");
      assertThat(generated).contains("import test.gen.RelocatedOpOps;");
    }

    @Test
    @DisplayName("A Class of a type variable is rejected by shape")
    void typeVariableClassFieldShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.GenericEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record GenericEffects<T>(Class<T> console, Class<DbOp<?>> db) {}
              """);

      Compilation compilation = compile(algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation)
          .hadErrorContaining("must be declared Class<XOp<?>>");
    }

    @Test
    @DisplayName("An unresolvable algebra is left to javac, not double-reported")
    void unresolvableAlgebraIsLeftToJavac() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.MissingEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record MissingEffects(Class<NoSuchOp<?>> missing, Class<DbOp<?>> db) {}
              """);

      Compilation compilation = compile(algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      // javac already says "cannot find symbol"; a second diagnostic would only mislead.
      assertThat(compilation.errors().stream().map(d -> d.getMessage(null)).toList())
          .noneMatch(m -> m.contains("@EffectAlgebra"));
    }

    @Test
    @DisplayName("A multi-parameter algebra is told why it cannot carry @EffectAlgebra")
    void multiParameterAlgebraExplainsItself() {
      // ErrorOp and StateOp are hand-written for this reason, so this is the likeliest
      // real-world spelling of the mistake.
      var twoParam =
          JavaFileObjects.forSourceString(
              "test.pkg.PairOp",
              """
              package test.pkg;

              public sealed interface PairOp<E, A> permits PairOp.Only {
                  record Only<E, A>(E error) implements PairOp<E, A> {}
              }
              """);
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.PairEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record PairEffects(Class<PairOp<?, ?>> pair, Class<DbOp<?>> db) {}
              """);

      Compilation compilation = compile(twoParam, algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation)
          .hadErrorContaining("2 type parameters cannot carry it");
    }

    @Test
    @DisplayName("A sealed @EffectAlgebra with two type parameters is rejected once")
    void multiParameterAnnotatedAlgebraShouldError() {
      var twoParam =
          JavaFileObjects.forSourceString(
              "test.pkg.WideOp",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

              @EffectAlgebra
              public sealed interface WideOp<E, A> permits WideOp.Only {
                  record Only<E, A>(E error) implements WideOp<E, A> {}
              }
              """);
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.WideEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record WideEffects(Class<WideOp<?, ?>> wide, Class<DbOp<?>> db) {}
              """);

      Compilation compilation = compile(twoParam, algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation)
          .hadErrorContaining("does not have exactly one type parameter");
    }

    @Test
    @DisplayName("A field whose own type is unresolvable is left to javac")
    void unresolvableFieldTypeIsLeftToJavac() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadFieldType",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record BadFieldType(NoSuchThing console, Class<DbOp<?>> db) {}
              """);

      Compilation compilation = compile(algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      assertThat(compilation.errors().stream().map(d -> d.getMessage(null)).toList())
          .noneMatch(m -> m.contains("must be declared Class<XOp<?>>"));
    }

    @Test
    @DisplayName("A primitive field is rejected by shape")
    void primitiveFieldShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.PrimitiveEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record PrimitiveEffects(int console, Class<DbOp<?>> db) {}
              """);

      Compilation compilation = compile(algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation)
          .hadErrorContaining("must be declared Class<XOp<?>>");
    }

    @Test
    @DisplayName("A raw Class field is rejected by shape")
    void rawClassFieldShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.RawEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record RawEffects(Class console, Class<DbOp<?>> db) {}
              """);

      Compilation compilation = compile(algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation)
          .hadErrorContaining("must be declared Class<XOp<?>>");
    }

    @Test
    @DisplayName("A single-parameter type without @EffectAlgebra gets the plain reason")
    void singleParameterNonAlgebraGetsPlainReason() {
      var plain =
          JavaFileObjects.forSourceString(
              "test.pkg.UnannotatedOp",
              """
              package test.pkg;

              public interface UnannotatedOp<A> {}
              """);
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.PlainEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record PlainEffects(Class<UnannotatedOp<?>> plain, Class<DbOp<?>> db) {}
              """);

      Compilation compilation = compile(plain, algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation)
          .hadErrorContaining("is not annotated @EffectAlgebra");
      // One type parameter, so the multi-parameter explanation must not appear.
      assertThat(compilation.errors().stream().map(d -> d.getMessage(null)).toList())
          .noneMatch(m -> m.contains("type parameters cannot carry it"));
    }

    @Test
    @DisplayName("A type argument that fixes the algebra's parameter is rejected by shape")
    void nonWildcardTypeArgumentShouldError() {
      // A class literal cannot be written for a parameterised type, so Class<ConsoleOp<String>>
      // reads as though the composition were fixed to String. It is not: the argument only
      // names the algebra.
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.FixedArgEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record FixedArgEffects(Class<ConsoleOp<String>> a, Class<DbOp<?>> b) {}
              """);

      Compilation compilation = compile(algebra("ConsoleOp"), algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation).hadErrorContaining("write Class<ConsoleOp<?>>");
    }

    @Test
    @DisplayName("A raw Class of the algebra is rejected by shape")
    void rawAlgebraArgumentShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.RawArgEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record RawArgEffects(Class<ConsoleOp> a, Class<DbOp<?>> b) {}
              """);

      Compilation compilation = compile(algebra("ConsoleOp"), algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation).hadErrorContaining("write Class<ConsoleOp<?>>");
    }

    @Test
    @DisplayName("A bounded wildcard is rejected by shape")
    void boundedWildcardArgumentShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BoundedEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record BoundedEffects(
                  Class<ConsoleOp<? extends Number>> a, Class<DbOp<?>> b) {}
              """);

      Compilation compilation = compile(algebra("ConsoleOp"), algebra("DbOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation).hadErrorContaining("write Class<ConsoleOp<?>>");

      var lowerBounded =
          JavaFileObjects.forSourceString(
              "test.pkg.SuperBoundedEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record SuperBoundedEffects(
                  Class<ConsoleOp<? super Number>> a, Class<DbOp<?>> b) {}
              """);

      Compilation lower = compile(algebra("ConsoleOp"), algebra("DbOp"), lowerBounded);
      CompilationSubject.assertThat(lower).failed();
      CompilationSubject.assertThat(lower).hadErrorContaining("write Class<ConsoleOp<?>>");
    }

    @Test
    @DisplayName("Duplicate effect types should produce error with field name")
    void duplicateEffectTypesShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.DupEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record DupEffects(
                  Class<ConsoleOp<?>> first,
                  Class<ConsoleOp<?>> second
              ) {}
              """);

      Compilation compilation = compile(algebra("ConsoleOp"), source);
      CompilationSubject.assertThat(compilation).failed();
      CompilationSubject.assertThat(compilation).hadErrorContaining("Duplicate effect algebra");
      CompilationSubject.assertThat(compilation).hadErrorContaining("second");
    }
  }

  // ===========================================================================
  // @Handles Validation Tests
  // ===========================================================================

  @Nested
  @DisplayName("Handles Validation")
  class HandlesValidationTests {

    @Test
    @DisplayName("@Handles on non-class should produce error")
    void handlesOnNonClassShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadHandler",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(String.class)
              public interface BadHandler {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@Handles can only annotate classes");
    }

    @Test
    @DisplayName("Missing handler should produce error")
    void missingHandlerShouldError() {
      // Need sealed interface + interpreter in same compilation
      var algebra =
          JavaFileObjects.forSourceString(
              "test.pkg.TestOp",
              """
              package test.pkg;

              public sealed interface TestOp<A> permits TestOp.Foo, TestOp.Bar {
                  record Foo<A>() implements TestOp<A> {}
                  record Bar<A>() implements TestOp<A> {}
              }
              """);
      var interpreter =
          JavaFileObjects.forSourceString(
              "test.pkg.TestInterpreter",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(TestOp.class)
              public class TestInterpreter {
                  // Missing handleFoo and handleBar
              }
              """);

      Compilation compilation = compile(algebra, interpreter);
      assertThat(compilation.errors()).isNotEmpty();
      // Should report missing handlers for Foo and Bar
      String errors =
          compilation.errors().stream()
              .map(d -> d.getMessage(null))
              .reduce("", (a, b) -> a + " " + b);
      assertThat(errors).contains("No handler for operation: Foo");
      assertThat(errors).contains("No handler for operation: Bar");
    }

    @Test
    @DisplayName("Complete handlers should pass validation")
    void completeHandlersShouldPass() {
      var algebra =
          JavaFileObjects.forSourceString(
              "test.pkg.TestOp",
              """
              package test.pkg;

              public sealed interface TestOp<A> permits TestOp.Foo {
                  record Foo<A>() implements TestOp<A> {}
              }
              """);
      var interpreter =
          JavaFileObjects.forSourceString(
              "test.pkg.TestInterpreter",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(TestOp.class)
              public class TestInterpreter {
                  public Object handleFoo(TestOp.Foo<?> op) { return null; }
              }
              """);

      Compilation compilation = compile(algebra, interpreter);
      assertThat(compilation.errors()).isEmpty();
    }

    @Test
    @DisplayName("@Handles on non-sealed interface should produce error")
    void handlesOnNonSealedShouldError() {
      var algebra =
          JavaFileObjects.forSourceString(
              "test.pkg.PlainOp",
              """
              package test.pkg;

              public interface PlainOp<A> {}
              """);
      var interpreter =
          JavaFileObjects.forSourceString(
              "test.pkg.PlainInterpreter",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(PlainOp.class)
              public class PlainInterpreter {}
              """);

      Compilation compilation = compile(algebra, interpreter);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@Handles references non-sealed interface");
    }

    @Test
    @DisplayName("Extra handler methods should produce warning")
    void extraHandlerShouldWarn() {
      var algebra =
          JavaFileObjects.forSourceString(
              "test.pkg.SmallOp",
              """
              package test.pkg;

              public sealed interface SmallOp<A> permits SmallOp.Only {
                  record Only<A>() implements SmallOp<A> {}
              }
              """);
      var interpreter =
          JavaFileObjects.forSourceString(
              "test.pkg.SmallInterpreter",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(SmallOp.class)
              public class SmallInterpreter {
                  public Object handleOnly(SmallOp.Only<?> op) { return null; }
                  public Object handleExtra(Object op) { return null; }
              }
              """);

      Compilation compilation = compile(algebra, interpreter);
      // No errors (handleOnly covers the one permit)
      assertThat(compilation.errors()).isEmpty();
      // But should have a warning for handleExtra
      assertThat(compilation.warnings()).isNotEmpty();
      String warnings =
          compilation.warnings().stream()
              .map(d -> d.getMessage(null))
              .reduce("", (a, b) -> a + " " + b);
      assertThat(warnings).contains("handleExtra");
      assertThat(warnings).contains("doesn't match any operation");
    }

    @Test
    @DisplayName("Non-handle methods should be ignored when checking handlers")
    void nonHandleMethodsShouldBeIgnored() {
      var algebra =
          JavaFileObjects.forSourceString(
              "test.pkg.HelperOp",
              """
              package test.pkg;

              public sealed interface HelperOp<A> permits HelperOp.Work {
                  record Work<A>() implements HelperOp<A> {}
              }
              """);
      var interpreter =
          JavaFileObjects.forSourceString(
              "test.pkg.HelperInterpreter",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(HelperOp.class)
              public class HelperInterpreter {
                  public Object handleWork(HelperOp.Work<?> op) { return null; }
                  // Non-handle method should be ignored, not flagged as extra
                  public Object processWork(Object data) { return null; }
              }
              """);

      Compilation compilation = compile(algebra, interpreter);
      // No errors — handleWork covers the one permit
      assertThat(compilation.errors()).isEmpty();
      // processWork should NOT produce a warning since it doesn't start with "handle"
    }
  }
}
